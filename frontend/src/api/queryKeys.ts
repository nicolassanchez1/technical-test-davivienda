import type { DocumentStatus } from './types';

export type DocumentListParams = {
  readonly status: DocumentStatus | null;
  readonly page: number;
  readonly pageSize: number;
};

export const documentKeys = {
  all: ['documents'] as const,
  lists: () => ['documents', 'list'] as const,
  list: (params: DocumentListParams) => ['documents', 'list', params] as const,
  detail: (id: string) => ['documents', 'detail', id] as const,
};
