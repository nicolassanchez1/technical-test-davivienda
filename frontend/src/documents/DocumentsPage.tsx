import { useQuery } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router';
import { listDocuments } from '../api/documents';
import { documentKeys } from '../api/queryKeys';
import type { DocumentResponse, DocumentStatus } from '../api/types';
import { copy } from '../copy/es';
import { interpolate } from '../copy/interpolate';
import { Pagination } from '../shared/Pagination';
import { describeError } from '../shared/errors';
import { formatBytes, formatDateTime } from '../shared/format';
import { StatusBadge } from './StatusBadge';
import { categoryLabel, documentStatuses, isDocumentStatus, statusLabel } from './presentation';

const PAGE_SIZE = 10;
const STATUS_PARAM = 'status';
const PAGE_PARAM = 'page';

export function DocumentsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const status = readStatus(searchParams);
  const page = readPage(searchParams);

  const documents = useQuery({
    queryKey: documentKeys.list({ status, page, pageSize: PAGE_SIZE }),
    queryFn: ({ signal }) => listDocuments({ status, page, pageSize: PAGE_SIZE }, signal),
  });

  const total = documents.data?.total ?? 0;
  const pageCount = Math.max(1, Math.ceil(total / PAGE_SIZE));

  const changeStatus = (value: string) => {
    const next = new URLSearchParams(searchParams);
    if (isDocumentStatus(value)) {
      next.set(STATUS_PARAM, value);
    } else {
      next.delete(STATUS_PARAM);
    }
    next.delete(PAGE_PARAM);
    setSearchParams(next);
  };

  const changePage = (value: number) => {
    const next = new URLSearchParams(searchParams);
    next.set(PAGE_PARAM, String(value));
    setSearchParams(next);
  };

  return (
    <section className="flex flex-col gap-6">
      <header className="flex flex-col gap-2">
        <h2 className="text-2xl font-semibold text-slate-900">{copy.documents.title}</h2>
        <p className="text-slate-600">{copy.documents.intro}</p>
      </header>

      <div className="flex flex-wrap items-end justify-between gap-4">
        <div className="flex flex-col gap-1">
          <label htmlFor="status-filter" className="text-sm font-medium text-slate-700">
            {copy.documents.filter.label}
          </label>
          <select
            id="status-filter"
            className="rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900"
            value={status ?? ''}
            onChange={(event) => changeStatus(event.target.value)}
          >
            <option value="">{copy.documents.filter.all}</option>
            {documentStatuses.map((candidate) => (
              <option key={candidate} value={candidate}>
                {statusLabel(candidate)}
              </option>
            ))}
          </select>
        </div>
        {documents.isSuccess ? (
          <p className="text-sm text-slate-600">
            {total === 1 ? copy.documents.totalOne : interpolate(copy.documents.total, { total })}
          </p>
        ) : null}
      </div>

      {documents.isPending ? (
        <p role="status" className="text-slate-600">
          {copy.states.loading}
        </p>
      ) : null}

      {documents.isError ? (
        <div role="alert" className="flex flex-col items-start gap-3 rounded-md bg-rose-50 p-4">
          <p className="font-medium text-rose-900">{copy.states.errorTitle}</p>
          <p className="text-sm text-rose-800">{describeError(documents.error)}</p>
          <button
            type="button"
            onClick={() => void documents.refetch()}
            className="rounded-md bg-rose-700 px-3 py-1.5 text-sm font-medium text-white"
          >
            {copy.actions.retry}
          </button>
        </div>
      ) : null}

      {documents.isSuccess && (documents.data.items ?? []).length === 0 ? (
        <p className="rounded-md bg-slate-50 p-4 text-slate-600">
          {status ? copy.documents.emptyFiltered : copy.documents.empty}
        </p>
      ) : null}

      {documents.isSuccess && (documents.data.items ?? []).length > 0 ? (
        <>
          <div className="overflow-x-auto rounded-lg ring-1 ring-slate-200">
            <table className="min-w-full divide-y divide-slate-200 text-sm">
              <caption className="sr-only">{copy.documents.table.caption}</caption>
              <thead className="bg-slate-50 text-left text-slate-700">
                <tr>
                  <th scope="col" className="px-4 py-3 font-semibold">
                    {copy.documents.table.title}
                  </th>
                  <th scope="col" className="px-4 py-3 font-semibold">
                    {copy.documents.table.author}
                  </th>
                  <th scope="col" className="px-4 py-3 font-semibold">
                    {copy.documents.table.category}
                  </th>
                  <th scope="col" className="px-4 py-3 font-semibold">
                    {copy.documents.table.version}
                  </th>
                  <th scope="col" className="px-4 py-3 font-semibold">
                    {copy.documents.table.tags}
                  </th>
                  <th scope="col" className="px-4 py-3 font-semibold">
                    {copy.documents.table.size}
                  </th>
                  <th scope="col" className="px-4 py-3 font-semibold">
                    {copy.documents.table.created}
                  </th>
                  <th scope="col" className="px-4 py-3 font-semibold">
                    {copy.documents.table.status}
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 bg-white">
                {(documents.data.items ?? []).map((entry) => (
                  <DocumentRow key={entry.id} entry={entry} />
                ))}
              </tbody>
            </table>
          </div>

          <Pagination
            label={copy.documents.title}
            indicator={interpolate(copy.documents.pageIndicator, { page, pages: pageCount })}
            page={page}
            pageCount={pageCount}
            onChange={changePage}
          />
        </>
      ) : null}
    </section>
  );
}

function DocumentRow({ entry }: { readonly entry: DocumentResponse }) {
  const tags = entry.tags ?? [];
  return (
    <tr>
      <th scope="row" className="px-4 py-3 text-left font-medium text-slate-900">
        {entry.id ? (
          <Link to={`/documents/${entry.id}`} className="text-sky-800 underline">
            {entry.title}
          </Link>
        ) : (
          entry.title
        )}
      </th>
      <td className="px-4 py-3 text-slate-700">{entry.author}</td>
      <td className="px-4 py-3 text-slate-700">
        {entry.category ? categoryLabel(entry.category) : null}
      </td>
      <td className="px-4 py-3 text-slate-700">{entry.version}</td>
      <td className="px-4 py-3 text-slate-700">
        {tags.length > 0 ? (
          <span className="flex flex-wrap gap-1">
            {tags.map((tag) => (
              <span key={tag} className="rounded bg-slate-100 px-1.5 py-0.5 text-xs text-slate-700">
                {tag}
              </span>
            ))}
          </span>
        ) : (
          <span className="text-slate-400">{copy.documents.noTags}</span>
        )}
      </td>
      <td className="px-4 py-3 text-slate-700">
        {entry.sizeBytes === undefined ? null : formatBytes(entry.sizeBytes)}
      </td>
      <td className="px-4 py-3 text-slate-700">{formatDateTime(entry.createdAt)}</td>
      <td className="px-4 py-3">
        {entry.status ? <StatusBadge status={entry.status} errorCode={entry.errorCode} /> : null}
      </td>
    </tr>
  );
}

function readStatus(params: URLSearchParams): DocumentStatus | null {
  const raw = params.get(STATUS_PARAM);
  return isDocumentStatus(raw) ? raw : null;
}

function readPage(params: URLSearchParams): number {
  const parsed = Number.parseInt(params.get(PAGE_PARAM) ?? '', 10);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : 1;
}
