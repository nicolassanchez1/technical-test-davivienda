import { Link } from 'react-router';
import type { SearchHitResponse } from '../api/types';
import { copy } from '../copy/es';
import { interpolate } from '../copy/interpolate';
import { categoryLabel } from '../documents/presentation';
import { formatDateTime } from '../shared/format';
import { HighlightedText } from './HighlightedText';

export type SearchResultItemProps = {
  readonly hit: SearchHitResponse;
  readonly query: string;
};

/**
 * One ranked document. The title and the fragment arrive already marked with the engine's
 * sentinels, so both go through the same renderer and no HTML is ever built from a response.
 */
export function SearchResultItem({ hit, query }: SearchResultItemProps) {
  const tags = hit.tags ?? [];
  const location = locationOf(hit);

  return (
    <article className="flex flex-col gap-2 rounded-lg p-4 ring-1 ring-slate-200">
      <h3 className="text-lg font-semibold">
        <Link to={viewerPath(hit.id, query)} className="text-sky-800 underline">
          <HighlightedText text={hit.titleHighlight ?? hit.title ?? ''} />
        </Link>
      </h3>

      <p className="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-slate-600">
        <span>{hit.author}</span>
        {hit.category ? <span>{categoryLabel(hit.category)}</span> : null}
        <span>{hit.version}</span>
        <span>{formatDateTime(hit.indexedAt)}</span>
        {location ? <span className="font-medium text-slate-700">{location}</span> : null}
      </p>

      {hit.snippet ? (
        <p className="text-slate-800">
          <HighlightedText text={hit.snippet} />
        </p>
      ) : null}

      {tags.length > 0 ? (
        <p className="flex flex-wrap gap-1">
          {tags.map((tag) => (
            <span key={tag} className="rounded bg-slate-100 px-1.5 py-0.5 text-xs text-slate-700">
              {tag}
            </span>
          ))}
        </p>
      ) : null}
    </article>
  );
}

/** Where in the document the fragment came from: a Markdown section, or a page for a PDF. */
function locationOf(hit: SearchHitResponse): string | null {
  if (hit.heading) {
    return interpolate(copy.search.matchInHeading, { heading: hit.heading });
  }
  if (hit.page !== undefined) {
    return interpolate(copy.search.matchInPage, { page: hit.page });
  }
  return null;
}

/** The query travels to the viewer so the body it opens highlights the same words. */
function viewerPath(id: string | undefined, query: string): string {
  return `/documents/${id ?? ''}?${new URLSearchParams({ q: query }).toString()}`;
}
