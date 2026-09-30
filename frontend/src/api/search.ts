import { buildQuery, requestJson } from './client';
import type { SearchParams } from './queryKeys';
import type { SearchResponse } from './types';

export function searchDocuments(
  params: SearchParams,
  signal?: AbortSignal,
): Promise<SearchResponse> {
  const query = buildQuery({
    q: params.query,
    page: params.page,
    pageSize: params.pageSize,
    category: params.category,
    author: params.author,
    tags: params.tags,
  });
  return requestJson<SearchResponse>(`/search${query}`, { signal });
}
