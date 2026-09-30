import { createContext, useContext } from 'react';
import type { UploadedDocumentResponse } from '../api/types';
import type { LiveStatuses } from './statusFeed';
import { NO_LIVE_STATUSES } from './statusFeed';

export type DocumentStatusTracker = {
  /** Latest status per document id, as announced by the stream or resolved on reconnect. */
  readonly liveStatuses: LiveStatuses;
  /** Registers freshly accepted uploads so their badges go live before any list is fetched. */
  readonly track: (documents: readonly UploadedDocumentResponse[]) => void;
};

const NO_TRACKING: DocumentStatusTracker = {
  liveStatuses: NO_LIVE_STATUSES,
  track: () => {},
};

export const DocumentStatusContext = createContext<DocumentStatusTracker>(NO_TRACKING);

export function useDocumentStatusTracker(): DocumentStatusTracker {
  return useContext(DocumentStatusContext);
}
