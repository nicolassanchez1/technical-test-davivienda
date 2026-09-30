import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link, useParams, useSearchParams } from 'react-router';
import { getDocument } from '../api/documents';
import { documentKeys } from '../api/queryKeys';
import { copy } from '../copy/es';
import { termsOf } from '../search/queryTerms';
import { BodyError, BodyLoading } from './BodyState';
import { DocumentBody } from './DocumentBody';
import { DocumentMetadata } from './DocumentMetadata';

const QUERY_PARAM = 'q';

/**
 * One document, read in place. The status shown here is the one the event stream last announced,
 * so a document that finishes indexing while it is open fills in without a reload and without the
 * page ever asking for it.
 */
export function DocumentViewerPage() {
  const { id = '' } = useParams<{ id: string }>();
  const [searchParams] = useSearchParams();
  const query = searchParams.get(QUERY_PARAM) ?? '';
  const terms = useMemo(() => termsOf(query), [query]);

  const detail = useQuery({
    queryKey: documentKeys.detail(id),
    queryFn: ({ signal }) => getDocument(id, signal),
    enabled: id.length > 0,
  });

  return (
    <section className="flex flex-col gap-6">
      <header className="flex flex-col gap-2">
        <Link to={searchPath(query)} className="text-sm text-sky-800 underline">
          {copy.viewer.backToSearch}
        </Link>
        <h2 className="text-2xl font-semibold text-slate-900">
          {detail.data?.title ?? copy.viewer.title}
        </h2>
      </header>

      {detail.isPending ? <BodyLoading /> : null}

      {detail.isError ? (
        <BodyError error={detail.error} onRetry={() => void detail.refetch()} />
      ) : null}

      {detail.isSuccess ? (
        <div className="grid items-start gap-6 lg:grid-cols-[20rem_minmax(0,1fr)]">
          <DocumentMetadata entry={detail.data} />
          <div className="flex min-w-0 flex-col gap-3">
            <h3 className="text-base font-semibold text-slate-900">{copy.viewer.body.legend}</h3>
            <DocumentBody documentId={id} entry={detail.data} terms={terms} />
          </div>
        </div>
      ) : null}
    </section>
  );
}

/** The search the reader came from, terms and all, so going back does not retype the query. */
function searchPath(query: string): string {
  return query.length > 0 ? `/?${new URLSearchParams({ q: query }).toString()}` : '/';
}
