import type { DocumentCategory, DocumentStatus } from './types';

export type DocumentListParams = {
  readonly status: DocumentStatus | null;
  readonly page: number;
  readonly pageSize: number;
};

export type SearchParams = {
  readonly query: string;
  readonly page: number;
  readonly pageSize: number;
  readonly category: DocumentCategory | null;
  readonly author: string;
  readonly tags: readonly string[];
};

export const documentKeys = {
  all: ['documents'] as const,
  lists: () => ['documents', 'list'] as const,
  list: (params: DocumentListParams) => ['documents', 'list', params] as const,
  detail: (id: string) => ['documents', 'detail', id] as const,
  content: (id: string) => ['documents', 'content', id] as const,
  file: (id: string) => ['documents', 'file', id] as const,
};

export const searchKeys = {
  all: ['search'] as const,
  results: (params: SearchParams) => ['search', 'results', params] as const,
};
