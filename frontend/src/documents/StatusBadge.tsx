import type { DocumentErrorCode, DocumentStatus } from '../api/types';
import { DOCUMENT_STATUS } from '../api/types';
import { errorCodeReason, statusDescription, statusLabel } from './presentation';

const toneByStatus: Record<DocumentStatus, string> = {
  PROCESANDO: 'bg-amber-100 text-amber-900 ring-amber-300',
  INDEXADO: 'bg-emerald-100 text-emerald-900 ring-emerald-300',
  ERROR: 'bg-rose-100 text-rose-900 ring-rose-300',
};

export type StatusBadgeProps = {
  readonly status: DocumentStatus;
  readonly errorCode?: DocumentErrorCode;
};

/**
 * A live region, because its text is replaced by a pushed event rather than by a navigation, and a
 * reader who is not looking at the row would otherwise never learn the document finished.
 */
export function StatusBadge({ status, errorCode }: StatusBadgeProps) {
  return (
    <span role="status" className="inline-flex flex-col items-start gap-1">
      <span
        title={statusDescription(status)}
        className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold ring-1 ring-inset ${toneByStatus[status]}`}
      >
        {statusLabel(status)}
      </span>
      {status === DOCUMENT_STATUS.failed ? (
        <span className="text-xs text-rose-800">{errorCodeReason(errorCode)}</span>
      ) : null}
    </span>
  );
}
