import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useSearchParams } from 'react-router';
import { searchKeys, type SearchParams } from '../api/queryKeys';
import { searchDocuments } from '../api/search';
import type { DocumentCategory } from '../api/types';
import { copy } from '../copy/es';
import { interpolate } from '../copy/interpolate';
import { categoryLabel, documentCategories, isDocumentCategory } from '../documents/presentation';
import { Pagination } from '../shared/Pagination';
import { useDebouncedValue } from '../shared/useDebouncedValue';
import { SearchResultItem } from './SearchResultItem';
import { describeSearchError } from './searchErrors';

const PAGE_SIZE = 10;
const DEBOUNCE_MS = 300;

/** The engine refuses a longer query anyway, so the field never lets one be typed. */
const MAX_QUERY_LENGTH = 256;

const QUERY_PARAM = 'q';
const PAGE_PARAM = 'page';
const CATEGORY_PARAM = 'category';
const AUTHOR_PARAM = 'author';
const TAGS_PARAM = 'tags';

const fieldClassName =
  'w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900';

/** The fields that are typed into, debounced together before they reach the URL. */
type TypedFilters = {
  readonly query: string;
  readonly author: string;
  readonly tags: string;
};

export function SearchPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const committed = typedFiltersOf(searchParams);
  const [typed, setTyped] = useState<TypedFilters>(committed);
  const settled = useDebouncedValue(typed, DEBOUNCE_MS);

  // The URL is the source of truth, so a search can be shared and survives a reload. Typing only
  // reaches it once the reader has stopped, which is what makes one request per search instead of
  // one per keystroke. Nothing is written while an edit is still pending: otherwise clearing the
  // filters would be undone by the debounced value that has not caught up yet.
  useEffect(() => {
    if (!sameTypedFilters(settled, typed)) {
      return;
    }
    const next = new URLSearchParams(searchParams);
    applyTypedFilters(next, settled);
    if (next.toString() === searchParams.toString()) {
      return;
    }
    next.delete(PAGE_PARAM);
    setSearchParams(next, { replace: true });
  }, [settled, typed, searchParams, setSearchParams]);

  const criteria: SearchParams = {
    query: committed.query.trim(),
    page: readPage(searchParams),
    pageSize: PAGE_SIZE,
    category: readCategory(searchParams),
    author: committed.author.trim(),
    tags: tagsOf(committed.tags),
  };
  const hasQuery = criteria.query.length > 0;

  const results = useQuery({
    queryKey: searchKeys.results(criteria),
    queryFn: ({ signal }) => searchDocuments(criteria, signal),
    enabled: hasQuery,
  });

  const items = results.data?.items ?? [];
  const total = results.data?.total ?? 0;
  const pageCount = Math.max(1, Math.ceil(total / PAGE_SIZE));

  const changeCategory = (value: string) => {
    const next = new URLSearchParams(searchParams);
    if (isDocumentCategory(value)) {
      next.set(CATEGORY_PARAM, value);
    } else {
      next.delete(CATEGORY_PARAM);
    }
    next.delete(PAGE_PARAM);
    setSearchParams(next, { replace: true });
  };

  const changePage = (value: number) => {
    const next = new URLSearchParams(searchParams);
    next.set(PAGE_PARAM, String(value));
    setSearchParams(next);
  };

  const clearFilters = () => {
    setTyped((previous) => ({ ...previous, author: '', tags: '' }));
    const next = new URLSearchParams(searchParams);
    for (const name of [CATEGORY_PARAM, AUTHOR_PARAM, TAGS_PARAM, PAGE_PARAM]) {
      next.delete(name);
    }
    setSearchParams(next, { replace: true });
  };

  const hasFilters =
    criteria.category !== null || criteria.author.length > 0 || criteria.tags.length > 0;

  return (
    <section className="flex flex-col gap-6">
      <header className="flex flex-col gap-2">
        <h2 className="text-2xl font-semibold text-slate-900">{copy.search.title}</h2>
        <p className="text-slate-600">{copy.search.intro}</p>
      </header>

      <search className="flex flex-col gap-4">
        <div className="flex flex-col gap-1">
          <label htmlFor="search-query" className="text-sm font-medium text-slate-700">
            {copy.search.inputLabel}
          </label>
          <input
            id="search-query"
            type="search"
            autoComplete="off"
            maxLength={MAX_QUERY_LENGTH}
            aria-describedby="search-query-hint"
            className={fieldClassName}
            value={typed.query}
            onChange={(event) => setTyped({ ...typed, query: event.target.value })}
          />
          <p id="search-query-hint" className="text-xs text-slate-500">
            {copy.search.inputHint}
          </p>
        </div>

        <fieldset className="grid gap-4 sm:grid-cols-3">
          <legend className="sr-only">{copy.search.filters.legend}</legend>
          <div className="flex flex-col gap-1">
            <label htmlFor="search-category" className="text-sm font-medium text-slate-700">
              {copy.search.filters.category}
            </label>
            <select
              id="search-category"
              className={fieldClassName}
              value={criteria.category ?? ''}
              onChange={(event) => changeCategory(event.target.value)}
            >
              <option value="">{copy.search.filters.anyCategory}</option>
              {documentCategories.map((candidate) => (
                <option key={candidate} value={candidate}>
                  {categoryLabel(candidate)}
                </option>
              ))}
            </select>
          </div>

          <div className="flex flex-col gap-1">
            <label htmlFor="search-author" className="text-sm font-medium text-slate-700">
              {copy.search.filters.author}
            </label>
            <input
              id="search-author"
              type="text"
              autoComplete="off"
              className={fieldClassName}
              value={typed.author}
              onChange={(event) => setTyped({ ...typed, author: event.target.value })}
            />
          </div>

          <div className="flex flex-col gap-1">
            <label htmlFor="search-tags" className="text-sm font-medium text-slate-700">
              {copy.search.filters.tags}
            </label>
            <input
              id="search-tags"
              type="text"
              autoComplete="off"
              aria-describedby="search-tags-hint"
              className={fieldClassName}
              value={typed.tags}
              onChange={(event) => setTyped({ ...typed, tags: event.target.value })}
            />
            <p id="search-tags-hint" className="text-xs text-slate-500">
              {copy.search.filters.tagsHint}
            </p>
          </div>
        </fieldset>

        {hasFilters ? (
          <div>
            <button
              type="button"
              onClick={clearFilters}
              className="rounded-md border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-800"
            >
              {copy.search.filters.clear}
            </button>
          </div>
        ) : null}
      </search>

      {!hasQuery ? (
        <p className="rounded-md bg-slate-50 p-4 text-slate-600">{copy.search.idle}</p>
      ) : null}

      {hasQuery && results.isPending ? (
        <p role="status" className="text-slate-600">
          {copy.states.loading}
        </p>
      ) : null}

      {hasQuery && results.isError ? (
        <div role="alert" className="flex flex-col items-start gap-3 rounded-md bg-rose-50 p-4">
          <p className="font-medium text-rose-900">{copy.states.errorTitle}</p>
          <p className="text-sm text-rose-800">{describeSearchError(results.error)}</p>
          <button
            type="button"
            onClick={() => void results.refetch()}
            className="rounded-md bg-rose-700 px-3 py-1.5 text-sm font-medium text-white"
          >
            {copy.actions.retry}
          </button>
        </div>
      ) : null}

      {hasQuery && results.isSuccess && items.length === 0 ? (
        <div className="flex flex-col gap-1 rounded-md bg-slate-50 p-4 text-slate-600">
          <p>{copy.search.empty}</p>
          <p className="text-sm">{copy.search.emptyHint}</p>
        </div>
      ) : null}

      {hasQuery && results.isSuccess && items.length > 0 ? (
        <div className="flex flex-col gap-4">
          <p className="text-sm text-slate-600">
            {interpolate(total === 1 ? copy.search.summaryOne : copy.search.summary, {
              total,
              took: results.data.tookMs ?? 0,
            })}
          </p>

          <ol aria-label={copy.search.resultsLabel} className="flex flex-col gap-3">
            {items.map((hit) => (
              <li key={hit.id}>
                <SearchResultItem hit={hit} query={criteria.query} />
              </li>
            ))}
          </ol>

          <Pagination
            label={copy.search.paginationLabel}
            indicator={interpolate(copy.search.pageIndicator, {
              page: criteria.page,
              pages: pageCount,
            })}
            page={criteria.page}
            pageCount={pageCount}
            onChange={changePage}
          />
        </div>
      ) : null}
    </section>
  );
}

function typedFiltersOf(params: URLSearchParams): TypedFilters {
  return {
    query: params.get(QUERY_PARAM) ?? '',
    author: params.get(AUTHOR_PARAM) ?? '',
    tags: params.getAll(TAGS_PARAM).join(', '),
  };
}

function sameTypedFilters(left: TypedFilters, right: TypedFilters): boolean {
  return left.query === right.query && left.author === right.author && left.tags === right.tags;
}

function applyTypedFilters(params: URLSearchParams, typed: TypedFilters): void {
  setOrDelete(params, QUERY_PARAM, typed.query);
  setOrDelete(params, AUTHOR_PARAM, typed.author);
  params.delete(TAGS_PARAM);
  for (const tag of tagsOf(typed.tags)) {
    params.append(TAGS_PARAM, tag);
  }
}

function setOrDelete(params: URLSearchParams, name: string, value: string): void {
  if (value.trim().length === 0) {
    params.delete(name);
    return;
  }
  params.set(name, value);
}

function tagsOf(value: string): readonly string[] {
  return value
    .split(',')
    .map((tag) => tag.trim())
    .filter((tag) => tag.length > 0);
}

function readCategory(params: URLSearchParams): DocumentCategory | null {
  const raw = params.get(CATEGORY_PARAM);
  return isDocumentCategory(raw) ? raw : null;
}

function readPage(params: URLSearchParams): number {
  const parsed = Number.parseInt(params.get(PAGE_PARAM) ?? '', 10);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : 1;
}
