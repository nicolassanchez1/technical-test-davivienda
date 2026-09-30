import { buildQuery, requestJson } from './client';
import type { DocumentListParams } from './queryKeys';
import type {
  DocumentPageResponse,
  DocumentResponse,
  UploadAcceptedResponse,
  DocumentCategory,
} from './types';

export type UploadMetadata = {
  readonly title: string;
  readonly author: string;
  readonly category: DocumentCategory;
  readonly tags: readonly string[];
  readonly version: string;
};

export type UploadBatch = {
  readonly files: readonly File[];
  readonly metadata: readonly UploadMetadata[];
};

export function listDocuments(
  params: DocumentListParams,
  signal?: AbortSignal,
): Promise<DocumentPageResponse> {
  const query = buildQuery({
    status: params.status,
    page: params.page,
    pageSize: params.pageSize,
  });
  return requestJson<DocumentPageResponse>(`/documents${query}`, { signal });
}

export function getDocument(id: string, signal?: AbortSignal): Promise<DocumentResponse> {
  return requestJson<DocumentResponse>(`/documents/${id}`, { signal });
}

/**
 * One multipart request for the whole batch: the API validates every file before storing any, so
 * splitting it per file would trade the all-or-nothing guarantee for nothing.
 */
export function uploadDocuments(
  batch: UploadBatch,
  signal?: AbortSignal,
): Promise<UploadAcceptedResponse> {
  const form = new FormData();
  for (const file of batch.files) {
    form.append('files', file, file.name);
  }
  // Sent as a part with an explicit charset rather than a bare field: a bare field carries no
  // Content-Type, and the converter that reads it server side would then guess the encoding and
  // mangle an accented title.
  form.append(
    'metadata',
    new Blob([JSON.stringify(batch.metadata)], { type: 'text/plain;charset=utf-8' }),
  );

  return requestJson<UploadAcceptedResponse>('/documents', {
    method: 'POST',
    body: form,
    signal,
  });
}
