import { Suspense, lazy } from 'react';
import type { DocumentResponse } from '../api/types';
import { DOCUMENT_STATUS } from '../api/types';
import { copy } from '../copy/es';
import { errorCodeReason } from '../documents/presentation';
import { BodyLoading, BodyNotice } from './BodyState';
import { ChunkedBody } from './ChunkedBody';

/**
 * The Markdown renderer and its parsers are the heaviest thing the SPA can load, and only a
 * Markdown document needs them, so they arrive when one is opened rather than at startup.
 */
const MarkdownBody = lazy(() =>
  import('./MarkdownBody').then((module) => ({ default: module.MarkdownBody })),
);

const MARKDOWN_MIME_TYPE = 'text/markdown';
const PDF_MIME_TYPE = 'application/pdf';

export type DocumentBodyProps = {
  /** The id the page was opened with, so a body is asked for even if the projection omits it. */
  readonly documentId: string;
  readonly entry: DocumentResponse;
  readonly terms: readonly string[];
};

/**
 * The body a document deserves: its Markdown source rendered, its pages labelled, or its text as
 * it was written. A document that is not indexed yet says so instead of showing an empty page.
 */
export function DocumentBody({ documentId, entry, terms }: DocumentBodyProps) {
  if (entry.status === DOCUMENT_STATUS.processing) {
    return <BodyNotice text={copy.viewer.body.processing} />;
  }

  if (entry.status === DOCUMENT_STATUS.failed) {
    return (
      <div className="flex flex-col gap-2">
        <BodyNotice text={copy.viewer.body.failed} />
        <p className="text-sm text-rose-800">{errorCodeReason(entry.errorCode)}</p>
      </div>
    );
  }

  if (entry.mimeType === MARKDOWN_MIME_TYPE) {
    return (
      <Suspense fallback={<BodyLoading />}>
        <MarkdownBody documentId={documentId} terms={terms} />
      </Suspense>
    );
  }

  return (
    <ChunkedBody
      documentId={documentId}
      terms={terms}
      showPages={entry.mimeType === PDF_MIME_TYPE}
    />
  );
}
