import type { QueryClient } from '@tanstack/react-query';
import { documentKeys } from '../api/queryKeys';
import type {
  DocumentErrorCode,
  DocumentPageResponse,
  DocumentResponse,
  DocumentStatus,
} from '../api/types';
import { DOCUMENT_STATUS } from '../api/types';
import { isDocumentStatus } from '../documents/presentation';

/** A status change with everything the UI needs already narrowed to non-optional. */
export type DocumentStatusEvent = {
  readonly documentId: string;
  readonly status: DocumentStatus;
  readonly errorCode?: DocumentErrorCode;
  readonly occurredAt?: string;
};

export type LiveStatuses = Readonly<Record<string, DocumentStatusEvent>>;

export const NO_LIVE_STATUSES: LiveStatuses = {};

/** Null for anything that is not a usable status change, so a malformed frame is ignored. */
export function parseStatusEvent(payload: string): DocumentStatusEvent | null {
  let parsed: unknown;
  try {
    parsed = JSON.parse(payload);
  } catch {
    return null;
  }
  if (typeof parsed !== 'object' || parsed === null) {
    return null;
  }
  const message = parsed as Record<string, unknown>;
  if (typeof message.documentId !== 'string' || !isDocumentStatus(message.status)) {
    return null;
  }
  return {
    documentId: message.documentId,
    status: message.status,
    errorCode:
      typeof message.errorCode === 'string' ? (message.errorCode as DocumentErrorCode) : undefined,
    occurredAt: typeof message.occurredAt === 'string' ? message.occurredAt : undefined,
  };
}

export function statusEventOf(entry: DocumentResponse): DocumentStatusEvent | null {
  if (!entry.id || !isDocumentStatus(entry.status)) {
    return null;
  }
  return {
    documentId: entry.id,
    status: entry.status,
    errorCode: entry.errorCode,
  };
}

/**
 * Writes the change straight into the cache instead of invalidating it: the event already carries
 * the new state, so asking the server again would be the refetch this design exists to avoid.
 */
export function applyStatusToCache(queryClient: QueryClient, event: DocumentStatusEvent): void {
  queryClient.setQueriesData<DocumentPageResponse>({ queryKey: documentKeys.lists() }, (previous) =>
    patchPage(previous, event),
  );
  queryClient.setQueryData<DocumentResponse>(documentKeys.detail(event.documentId), (previous) =>
    previous ? patchDocument(previous, event) : previous,
  );
}

/**
 * The row keeps its place even when a status filter is active. A document that silently vanished
 * from the page the reader is looking at would hide the very transition this view exists to show.
 */
function patchPage(
  previous: DocumentPageResponse | undefined,
  event: DocumentStatusEvent,
): DocumentPageResponse | undefined {
  if (!previous?.items?.some((entry) => entry.id === event.documentId)) {
    return previous;
  }
  return {
    ...previous,
    items: previous.items.map((entry) =>
      entry.id === event.documentId ? patchDocument(entry, event) : entry,
    ),
  };
}

function patchDocument(entry: DocumentResponse, event: DocumentStatusEvent): DocumentResponse {
  return { ...entry, status: event.status, errorCode: event.errorCode };
}

/** Every document the client currently shows as still processing, from the cache and the feed. */
export function stillProcessingIds(
  queryClient: QueryClient,
  liveStatuses: LiveStatuses,
): ReadonlySet<string> {
  const pending = new Set<string>();
  for (const [, page] of queryClient.getQueriesData<DocumentPageResponse>({
    queryKey: documentKeys.lists(),
  })) {
    for (const entry of page?.items ?? []) {
      if (entry.id && entry.status === DOCUMENT_STATUS.processing) {
        pending.add(entry.id);
      }
    }
  }
  for (const [documentId, event] of Object.entries(liveStatuses)) {
    if (event.status === DOCUMENT_STATUS.processing) {
      pending.add(documentId);
    }
  }
  return pending;
}

export function cachedDocumentTitle(
  queryClient: QueryClient,
  documentId: string,
): string | undefined {
  const detail = queryClient.getQueryData<DocumentResponse>(documentKeys.detail(documentId));
  if (detail?.title) {
    return detail.title;
  }
  for (const [, page] of queryClient.getQueriesData<DocumentPageResponse>({
    queryKey: documentKeys.lists(),
  })) {
    const match = page?.items?.find((entry) => entry.id === documentId);
    if (match?.title) {
      return match.title;
    }
  }
  return undefined;
}
