import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { apiUrl } from '../api/client';
import { listDocuments } from '../api/documents';
import type { DocumentStatus, UploadedDocumentResponse } from '../api/types';
import { copy } from '../copy/es';
import { interpolate } from '../copy/interpolate';
import { errorCodeReason, isDocumentStatus } from '../documents/presentation';
import { DocumentStatusContext, type DocumentStatusTracker } from './DocumentStatusContext';
import {
  NO_LIVE_STATUSES,
  applyStatusToCache,
  cachedDocumentTitle,
  parseStatusEvent,
  statusEventOf,
  stillProcessingIds,
  type DocumentStatusEvent,
  type LiveStatuses,
} from './statusFeed';

const STATUS_EVENT = 'document.status';

/** Newest first, so the documents a reader just uploaded are the ones this window covers. */
const RECONCILIATION_PAGE_SIZE = 50;

type Announcement = (title: string, reason: string) => void;

const announcements: Record<DocumentStatus, Announcement> = {
  PROCESANDO: (title) =>
    toast.info(copy.notifications.processing.title, {
      description: interpolate(copy.notifications.processing.description, { title }),
    }),
  INDEXADO: (title) =>
    toast.success(copy.notifications.indexed.title, {
      description: interpolate(copy.notifications.indexed.description, { title }),
    }),
  ERROR: (title, reason) =>
    toast.error(copy.notifications.failed.title, {
      description: interpolate(copy.notifications.failed.description, { title, reason }),
    }),
};

/**
 * Owns the single event stream for the whole application. It is opened once when the provider
 * mounts and closed when it unmounts: never one stream per document, and never a timer anywhere,
 * because a status change is pushed rather than asked for.
 */
export function DocumentStatusProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [liveStatuses, setLiveStatuses] = useState<LiveStatuses>(NO_LIVE_STATUSES);

  // The stream is created in an effect that must not depend on the statuses it collects, so the
  // latest snapshot is read through a ref rather than through the closure.
  const liveStatusesRef = useRef<LiveStatuses>(NO_LIVE_STATUSES);
  const connectionLostRef = useRef(false);

  const remember = useCallback((events: readonly DocumentStatusEvent[]) => {
    if (events.length === 0) {
      return;
    }
    const merged = { ...liveStatusesRef.current };
    for (const event of events) {
      merged[event.documentId] = event;
    }
    liveStatusesRef.current = merged;
    setLiveStatuses(merged);
  }, []);

  const track = useCallback(
    (documents: readonly UploadedDocumentResponse[]) => {
      remember(
        documents.flatMap((entry) =>
          entry.id && isDocumentStatus(entry.status)
            ? [{ documentId: entry.id, status: entry.status }]
            : [],
        ),
      );
    },
    [remember],
  );

  useEffect(() => {
    const source = new EventSource(apiUrl('/events'));

    /**
     * Run once per successful connection, and only for documents still shown as processing: a
     * change announced while the tab was offline is never replayed, so it has to be asked for
     * exactly once, here. One request resolves the whole set.
     */
    const reconcile = async () => {
      const pending = stillProcessingIds(queryClient, liveStatusesRef.current);
      if (pending.size === 0) {
        return;
      }
      try {
        const page = await listDocuments({
          status: null,
          page: 1,
          pageSize: RECONCILIATION_PAGE_SIZE,
        });
        const resolved = (page.items ?? []).flatMap((entry) => {
          const event = entry.id && pending.has(entry.id) ? statusEventOf(entry) : null;
          return event ? [event] : [];
        });
        for (const event of resolved) {
          applyStatusToCache(queryClient, event);
        }
        // Deliberately silent: catching up on a change is not news worth a toast per document.
        remember(resolved);
      } catch {
        // Nothing is retried here; the next connection reconciles again.
      }
    };

    const handleStatus = (event: Event) => {
      const payload = (event as MessageEvent<unknown>).data;
      if (typeof payload !== 'string') {
        return;
      }
      const status = parseStatusEvent(payload);
      if (!status) {
        return;
      }
      remember([status]);
      applyStatusToCache(queryClient, status);
      announcements[status.status](
        cachedDocumentTitle(queryClient, status.documentId) ?? copy.notifications.unknownDocument,
        errorCodeReason(status.errorCode),
      );
    };

    const handleOpen = () => {
      if (connectionLostRef.current) {
        connectionLostRef.current = false;
        toast.success(copy.notifications.streamRestored);
      }
      void reconcile();
    };

    const handleError = () => {
      // EventSource reconnects by itself; this only reports the gap once, never schedules a retry.
      if (connectionLostRef.current) {
        return;
      }
      connectionLostRef.current = true;
      toast.warning(copy.notifications.streamLost);
    };

    source.addEventListener(STATUS_EVENT, handleStatus);
    source.addEventListener('open', handleOpen);
    source.addEventListener('error', handleError);

    return () => {
      source.removeEventListener(STATUS_EVENT, handleStatus);
      source.removeEventListener('open', handleOpen);
      source.removeEventListener('error', handleError);
      source.close();
    };
  }, [queryClient, remember]);

  const tracker: DocumentStatusTracker = { liveStatuses, track };

  return (
    <DocumentStatusContext.Provider value={tracker}>{children}</DocumentStatusContext.Provider>
  );
}
