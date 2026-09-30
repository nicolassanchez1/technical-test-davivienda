import type { ReactNode } from 'react';
import { documentFileUrl } from '../api/documents';
import type { DocumentResponse } from '../api/types';
import { copy } from '../copy/es';
import { interpolate } from '../copy/interpolate';
import { StatusBadge } from '../documents/StatusBadge';
import { categoryLabel } from '../documents/presentation';
import { formatBytes, formatDateTime } from '../shared/format';

export type DocumentMetadataProps = {
  readonly entry: DocumentResponse;
};

/** Everything the catalog knows about the document, so reading it needs no download. */
export function DocumentMetadata({ entry }: DocumentMetadataProps) {
  const tags = entry.tags ?? [];

  return (
    <aside className="flex flex-col gap-3 self-start rounded-lg bg-slate-50 p-4 ring-1 ring-slate-200">
      <h3 className="text-base font-semibold text-slate-900">{copy.viewer.metadata.legend}</h3>

      <dl className="flex flex-col gap-2 text-sm">
        <MetadataRow
          label={copy.viewer.metadata.status}
          value={
            entry.status ? <StatusBadge status={entry.status} errorCode={entry.errorCode} /> : null
          }
        />
        <MetadataRow label={copy.viewer.metadata.author} value={entry.author} />
        <MetadataRow
          label={copy.viewer.metadata.category}
          value={entry.category ? categoryLabel(entry.category) : null}
        />
        <MetadataRow label={copy.viewer.metadata.version} value={entry.version} />
        <MetadataRow
          label={copy.viewer.metadata.tags}
          value={
            tags.length > 0 ? (
              <span className="flex flex-wrap gap-1">
                {tags.map((tag) => (
                  <span
                    key={tag}
                    className="rounded bg-white px-1.5 py-0.5 text-xs text-slate-700 ring-1 ring-slate-200"
                  >
                    {tag}
                  </span>
                ))}
              </span>
            ) : (
              copy.documents.noTags
            )
          }
        />
        <MetadataRow label={copy.viewer.metadata.filename} value={entry.originalFilename} />
        <MetadataRow label={copy.viewer.metadata.mimeType} value={entry.mimeType} />
        <MetadataRow
          label={copy.viewer.metadata.size}
          value={entry.sizeBytes === undefined ? null : formatBytes(entry.sizeBytes)}
        />
        <MetadataRow label={copy.viewer.metadata.pageCount} value={entry.pageCount} />
        <MetadataRow label={copy.viewer.metadata.chunkCount} value={entry.chunkCount} />
        <MetadataRow
          label={copy.viewer.metadata.processingTime}
          value={
            entry.processingMs === undefined
              ? null
              : interpolate(copy.viewer.metadata.milliseconds, { value: entry.processingMs })
          }
        />
        <MetadataRow label={copy.viewer.metadata.created} value={formatDateTime(entry.createdAt)} />
        <MetadataRow label={copy.viewer.metadata.updated} value={formatDateTime(entry.updatedAt)} />
        <MetadataRow label={copy.viewer.metadata.indexed} value={formatDateTime(entry.indexedAt)} />
      </dl>

      {entry.id ? (
        <a
          href={documentFileUrl(entry.id)}
          target="_blank"
          rel="noreferrer"
          className="text-sm text-sky-800 underline"
        >
          {copy.viewer.openOriginal}
        </a>
      ) : null}
    </aside>
  );
}

/** A field the API did not send is left out rather than shown as an empty row. */
function MetadataRow({ label, value }: { readonly label: string; readonly value: ReactNode }) {
  if (value === null || value === undefined || value === '') {
    return null;
  }
  return (
    <div className="grid grid-cols-[8rem_1fr] items-start gap-2">
      <dt className="text-slate-500">{label}</dt>
      <dd className="text-slate-900">{value}</dd>
    </div>
  );
}
