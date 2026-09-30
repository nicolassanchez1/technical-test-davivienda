import type { components } from '@technical-test-davivienda/shared';

type Schemas = components['schemas'];

export type DocumentResponse = Schemas['DocumentResponse'];
export type DocumentPageResponse = Schemas['DocumentPageResponse'];
export type DocumentContentResponse = Schemas['DocumentContentResponse'];
export type DocumentChunkResponse = Schemas['DocumentChunkResponse'];
export type UploadAcceptedResponse = Schemas['UploadAcceptedResponse'];
export type UploadedDocumentResponse = Schemas['UploadedDocumentResponse'];
export type DocumentStatusMessage = Schemas['DocumentStatusMessage'];
export type SearchResponse = Schemas['SearchResponse'];
export type SearchHitResponse = Schemas['SearchHitResponse'];

export type DocumentStatus = NonNullable<DocumentResponse['status']>;
export type DocumentCategory = NonNullable<DocumentResponse['category']>;
export type DocumentErrorCode = NonNullable<DocumentResponse['errorCode']>;

export const DOCUMENT_STATUS = {
  processing: 'PROCESANDO',
  indexed: 'INDEXADO',
  failed: 'ERROR',
} as const satisfies Record<string, DocumentStatus>;

export const DOCUMENT_CATEGORY = {
  manual: 'MANUAL',
  specification: 'SPECIFICATION',
  architectureGuide: 'ARCHITECTURE_GUIDE',
  other: 'OTHER',
} as const satisfies Record<string, DocumentCategory>;
