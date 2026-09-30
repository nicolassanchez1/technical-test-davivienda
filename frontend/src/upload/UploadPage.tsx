import { useRef, useState, type FormEvent } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useDropzone, type FileRejection } from 'react-dropzone';
import { Link } from 'react-router';
import { toast } from 'sonner';
import { uploadDocuments, type UploadMetadata } from '../api/documents';
import { ApiError, type UploadFileProblem } from '../api/problem';
import { documentKeys } from '../api/queryKeys';
import {
  DOCUMENT_CATEGORY,
  type DocumentCategory,
  type UploadedDocumentResponse,
} from '../api/types';
import { copy } from '../copy/es';
import { interpolate } from '../copy/interpolate';
import { StatusBadge } from '../documents/StatusBadge';
import { categoryLabel, documentCategories, uploadRuleReason } from '../documents/presentation';
import { useDocumentStatusTracker } from '../events/DocumentStatusContext';
import { describeError } from '../shared/errors';
import { formatBytes, formatMegabytes } from '../shared/format';
import { rejectionReason, TOO_MANY_FILES_REASON, type RejectedFile } from './fileRejections';
import {
  ACCEPTED_FILE_TYPES,
  MAX_FILES_PER_UPLOAD,
  MAX_FILE_SIZE_BYTES,
  MAX_FILE_SIZE_MB,
  titleFromFilename,
} from './limits';
import { validateMetadata, type MetadataDraft, type MetadataFieldErrors } from './metadataSchema';

type UploadRow = {
  readonly key: string;
  readonly file: File;
  readonly metadata: MetadataDraft;
};

type CommonMetadata = Omit<MetadataDraft, 'title'>;

type SubmitBatch = {
  readonly files: readonly File[];
  readonly metadata: readonly UploadMetadata[];
  readonly rowKeys: readonly string[];
};

const emptyCommonMetadata: CommonMetadata = {
  author: '',
  category: DOCUMENT_CATEGORY.manual,
  version: '',
  tags: '',
};

const inputClassName =
  'w-full rounded-md border border-slate-300 px-2 py-1.5 text-sm text-slate-900 aria-invalid:border-rose-500';

export function UploadPage() {
  const queryClient = useQueryClient();
  const { liveStatuses, track } = useDocumentStatusTracker();

  const [rows, setRows] = useState<readonly UploadRow[]>([]);
  const [common, setCommon] = useState<CommonMetadata>(emptyCommonMetadata);
  const [rejected, setRejected] = useState<readonly RejectedFile[]>([]);
  const [fieldErrors, setFieldErrors] = useState<Readonly<Record<string, MetadataFieldErrors>>>({});
  const [serverErrors, setServerErrors] = useState<Readonly<Record<string, UploadFileProblem>>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [accepted, setAccepted] = useState<readonly UploadedDocumentResponse[]>([]);
  const rowSequence = useRef(0);

  const upload = useMutation({
    mutationFn: (batch: SubmitBatch) =>
      uploadDocuments({ files: batch.files, metadata: batch.metadata }),
    onSuccess: (response) => {
      const items = response.items ?? [];
      track(items);
      setAccepted(items);
      setRows([]);
      setRejected([]);
      setFieldErrors({});
      setServerErrors({});
      setFormError(null);
      void queryClient.invalidateQueries({ queryKey: documentKeys.lists() });
      toast.success(copy.notifications.uploadAccepted.title, {
        description:
          items.length === 1
            ? copy.notifications.uploadAccepted.descriptionOne
            : interpolate(copy.notifications.uploadAccepted.description, {
                count: items.length,
              }),
      });
    },
    onError: (error, batch) => {
      setServerErrors(problemsByRow(error, batch.rowKeys));
      setFormError(describeError(error));
      toast.error(copy.notifications.uploadRejected);
    },
  });

  const addFiles = (incoming: readonly File[], dropRejections: readonly FileRejection[]) => {
    const nextRejected: RejectedFile[] = dropRejections.map((rejection, index) => ({
      key: `rejected-${rejection.file.name}-${index}`,
      filename: rejection.file.name,
      reason: rejectionReason(rejection.errors),
    }));

    const signatures = new Set(rows.map(signatureOf));
    const added: UploadRow[] = [];

    incoming.forEach((file, index) => {
      const signature = `${file.name}:${file.size}`;
      if (signatures.has(signature)) {
        nextRejected.push({
          key: `duplicate-${file.name}-${index}`,
          filename: file.name,
          reason: copy.upload.rejections.duplicateSelection,
        });
        return;
      }
      if (rows.length + added.length >= MAX_FILES_PER_UPLOAD) {
        nextRejected.push({
          key: `surplus-${file.name}-${index}`,
          filename: file.name,
          reason: TOO_MANY_FILES_REASON,
        });
        return;
      }
      signatures.add(signature);
      rowSequence.current += 1;
      added.push({
        key: `row-${rowSequence.current}`,
        file,
        metadata: { ...common, title: titleFromFilename(file.name) },
      });
    });

    setRows([...rows, ...added]);
    setRejected(nextRejected);
    setFieldErrors({});
    setServerErrors({});
    setFormError(null);
  };

  const dropzone = useDropzone({
    accept: ACCEPTED_FILE_TYPES,
    maxSize: MAX_FILE_SIZE_BYTES,
    minSize: 1,
    maxFiles: MAX_FILES_PER_UPLOAD,
    multiple: true,
    onDrop: addFiles,
  });

  const updateRow = (key: string, field: keyof MetadataDraft, value: string) => {
    setRows((current) =>
      current.map((row) =>
        row.key === key ? { ...row, metadata: withField(row.metadata, field, value) } : row,
      ),
    );
  };

  const removeRow = (key: string) => {
    setRows((current) => current.filter((row) => row.key !== key));
  };

  const applyCommonToAll = () => {
    setRows((current) =>
      current.map((row) => ({
        ...row,
        metadata: {
          title: row.metadata.title,
          author: common.author || row.metadata.author,
          category: common.category,
          version: common.version || row.metadata.version,
          tags: common.tags || row.metadata.tags,
        },
      })),
    );
  };

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setServerErrors({});

    if (rows.length === 0) {
      setFieldErrors({});
      setFormError(copy.upload.validation.noFiles);
      return;
    }

    const errors: Record<string, MetadataFieldErrors> = {};
    const metadata: UploadMetadata[] = [];
    for (const row of rows) {
      const validation = validateMetadata(row.metadata);
      if (validation.ok) {
        metadata.push(validation.metadata);
      } else {
        errors[row.key] = validation.errors;
      }
    }

    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      setFormError(copy.upload.validation.formInvalid);
      return;
    }

    setFormError(null);
    upload.mutate({
      files: rows.map((row) => row.file),
      metadata,
      rowKeys: rows.map((row) => row.key),
    });
  };

  return (
    <section className="flex flex-col gap-6">
      <header className="flex flex-col gap-2">
        <h2 className="text-2xl font-semibold text-slate-900">{copy.upload.title}</h2>
        <p className="text-slate-600">{copy.upload.intro}</p>
      </header>

      <div
        {...dropzone.getRootProps({
          className: `flex cursor-pointer flex-col items-center gap-2 rounded-lg border-2 border-dashed p-8 text-center ${
            dropzone.isDragActive ? 'border-sky-500 bg-sky-50' : 'border-slate-300 bg-slate-50'
          }`,
        })}
      >
        <input {...dropzone.getInputProps({ 'aria-label': copy.upload.dropzone.inputLabel })} />
        <p className="font-medium text-slate-800">
          {dropzone.isDragActive ? copy.upload.dropzone.active : copy.upload.dropzone.idle}
        </p>
        <p className="text-sm text-slate-600">
          {interpolate(copy.upload.dropzone.hint, {
            maxFiles: MAX_FILES_PER_UPLOAD,
            maxSize: formatMegabytes(MAX_FILE_SIZE_MB),
          })}
        </p>
      </div>

      {rejected.length > 0 ? (
        <div role="alert" className="rounded-md bg-amber-50 p-4">
          <p className="font-medium text-amber-900">{copy.upload.rejectedTitle}</p>
          <ul className="mt-2 flex flex-col gap-1 text-sm text-amber-900">
            {rejected.map((file) => (
              <li key={file.key}>
                <span className="font-medium">{file.filename}</span>
                <span>{`: ${file.reason}`}</span>
              </li>
            ))}
          </ul>
        </div>
      ) : null}

      <form onSubmit={submit} className="flex flex-col gap-6">
        <fieldset className="flex flex-col gap-3 rounded-lg ring-1 ring-slate-200 p-4">
          <legend className="px-1 text-sm font-semibold text-slate-800">
            {copy.upload.defaults.legend}
          </legend>
          <p className="text-sm text-slate-600">{copy.upload.defaults.hint}</p>
          <div className="grid gap-3 sm:grid-cols-4">
            <label className="flex flex-col gap-1 text-sm text-slate-700">
              {copy.upload.fields.author}
              <input
                className={inputClassName}
                value={common.author}
                onChange={(event) => setCommon({ ...common, author: event.target.value })}
              />
            </label>
            <label className="flex flex-col gap-1 text-sm text-slate-700">
              {copy.upload.fields.category}
              <select
                className={inputClassName}
                value={common.category}
                onChange={(event) =>
                  setCommon({ ...common, category: event.target.value as DocumentCategory })
                }
              >
                {documentCategories.map((category) => (
                  <option key={category} value={category}>
                    {categoryLabel(category)}
                  </option>
                ))}
              </select>
            </label>
            <label className="flex flex-col gap-1 text-sm text-slate-700">
              {copy.upload.fields.version}
              <input
                className={inputClassName}
                value={common.version}
                onChange={(event) => setCommon({ ...common, version: event.target.value })}
              />
            </label>
            <label className="flex flex-col gap-1 text-sm text-slate-700">
              {copy.upload.fields.tags}
              <input
                className={inputClassName}
                value={common.tags}
                onChange={(event) => setCommon({ ...common, tags: event.target.value })}
              />
              <span className="text-xs text-slate-500">{copy.upload.fields.tagsHint}</span>
            </label>
          </div>
          <div>
            <button
              type="button"
              onClick={applyCommonToAll}
              className="rounded-md border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-800"
            >
              {copy.actions.applyToAll}
            </button>
          </div>
        </fieldset>

        {rows.length === 0 ? (
          <p className="rounded-md bg-slate-50 p-4 text-slate-600">{copy.upload.table.empty}</p>
        ) : (
          <div className="overflow-x-auto rounded-lg ring-1 ring-slate-200">
            <table className="min-w-full divide-y divide-slate-200 text-sm">
              <caption className="sr-only">{copy.upload.table.caption}</caption>
              <thead className="bg-slate-50 text-left text-slate-700">
                <tr>
                  <th scope="col" className="px-3 py-2 font-semibold">
                    {copy.upload.fields.filename}
                  </th>
                  <th scope="col" className="px-3 py-2 font-semibold">
                    {copy.upload.fields.title}
                  </th>
                  <th scope="col" className="px-3 py-2 font-semibold">
                    {copy.upload.fields.author}
                  </th>
                  <th scope="col" className="px-3 py-2 font-semibold">
                    {copy.upload.fields.category}
                  </th>
                  <th scope="col" className="px-3 py-2 font-semibold">
                    {copy.upload.fields.version}
                  </th>
                  <th scope="col" className="px-3 py-2 font-semibold">
                    {copy.upload.fields.tags}
                  </th>
                  <th scope="col" className="px-3 py-2 font-semibold">
                    {copy.upload.fields.actions}
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 bg-white">
                {rows.map((row) => (
                  <MetadataRow
                    key={row.key}
                    row={row}
                    errors={fieldErrors[row.key]}
                    serverError={serverErrors[row.key]}
                    onChange={updateRow}
                    onRemove={removeRow}
                  />
                ))}
              </tbody>
            </table>
          </div>
        )}

        {formError !== null ? (
          <p role="alert" className="rounded-md bg-rose-50 p-3 text-sm text-rose-900">
            {formError}
          </p>
        ) : null}

        <div>
          <button
            type="submit"
            disabled={upload.isPending}
            className="rounded-md bg-sky-800 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50"
          >
            {upload.isPending ? copy.actions.submittingUpload : copy.actions.submitUpload}
          </button>
        </div>
      </form>

      {accepted.length > 0 ? (
        <div className="flex flex-col gap-3 rounded-lg ring-1 ring-emerald-200 bg-emerald-50 p-4">
          <h3 className="font-semibold text-emerald-900">{copy.upload.accepted.title}</h3>
          <p className="text-sm text-emerald-900">{copy.upload.accepted.hint}</p>
          <ul className="flex flex-col gap-2">
            {accepted.map((item) => {
              const live = item.id ? liveStatuses[item.id] : undefined;
              const status = live?.status ?? item.status;
              return (
                <li key={item.id ?? item.filename} className="flex items-center gap-3">
                  <span className="text-sm font-medium text-slate-900">{item.filename}</span>
                  {status ? <StatusBadge status={status} errorCode={live?.errorCode} /> : null}
                  {item.id ? (
                    <Link to={`/documents/${item.id}`} className="text-sm text-sky-900 underline">
                      {copy.upload.accepted.viewLink}
                    </Link>
                  ) : null}
                </li>
              );
            })}
          </ul>
        </div>
      ) : null}
    </section>
  );
}

type MetadataRowProps = {
  readonly row: UploadRow;
  readonly errors: MetadataFieldErrors | undefined;
  readonly serverError: UploadFileProblem | undefined;
  readonly onChange: (key: string, field: keyof MetadataDraft, value: string) => void;
  readonly onRemove: (key: string) => void;
};

function MetadataRow({ row, errors, serverError, onChange, onRemove }: MetadataRowProps) {
  const filename = row.file.name;
  const fieldId = (field: keyof MetadataDraft) => `${row.key}-${field}`;
  const errorId = (field: keyof MetadataDraft) => `${fieldId(field)}-error`;

  const textField = (field: 'title' | 'author' | 'version' | 'tags', label: string) => {
    const message = errors?.[field];
    return (
      <td className="px-3 py-2 align-top">
        <input
          id={fieldId(field)}
          aria-label={fieldLabel(label, filename)}
          aria-invalid={message !== undefined}
          aria-describedby={message === undefined ? undefined : errorId(field)}
          className={inputClassName}
          value={row.metadata[field]}
          onChange={(event) => onChange(row.key, field, event.target.value)}
        />
        {message === undefined ? null : (
          <p id={errorId(field)} className="mt-1 text-xs text-rose-800">
            {message}
          </p>
        )}
      </td>
    );
  };

  return (
    <>
      <tr>
        <th scope="row" className="px-3 py-2 text-left align-top font-medium text-slate-900">
          <span className="block">{filename}</span>
          <span className="block text-xs font-normal text-slate-500">
            {formatBytes(row.file.size)}
          </span>
        </th>
        {textField('title', copy.upload.fields.title)}
        {textField('author', copy.upload.fields.author)}
        <td className="px-3 py-2 align-top">
          <select
            id={fieldId('category')}
            aria-label={fieldLabel(copy.upload.fields.category, filename)}
            className={inputClassName}
            value={row.metadata.category}
            onChange={(event) => onChange(row.key, 'category', event.target.value)}
          >
            {documentCategories.map((category) => (
              <option key={category} value={category}>
                {categoryLabel(category)}
              </option>
            ))}
          </select>
        </td>
        {textField('version', copy.upload.fields.version)}
        {textField('tags', copy.upload.fields.tags)}
        <td className="px-3 py-2 align-top">
          <button
            type="button"
            onClick={() => onRemove(row.key)}
            className="rounded-md border border-slate-300 px-2 py-1 text-xs font-medium text-slate-800"
          >
            {copy.actions.remove}
          </button>
        </td>
      </tr>
      {serverError ? (
        <tr>
          <td colSpan={7} className="bg-rose-50 px-3 py-2">
            <p role="alert" className="text-sm text-rose-900">
              <span className="font-semibold">{`${copy.upload.rowErrorLabel}: `}</span>
              <span>{uploadRuleReason(serverError.rule)}</span>
            </p>
            {serverError.existingDocumentId ? (
              <Link
                to={`/documents/${serverError.existingDocumentId}`}
                className="text-sm text-sky-900 underline"
              >
                {copy.upload.duplicateLink}
              </Link>
            ) : null}
          </td>
        </tr>
      ) : null}
    </>
  );
}

function fieldLabel(label: string, filename: string): string {
  return `${label}: ${filename}`;
}

function withField(
  metadata: MetadataDraft,
  field: keyof MetadataDraft,
  value: string,
): MetadataDraft {
  if (field === 'category') {
    return { ...metadata, category: value as DocumentCategory };
  }
  return { ...metadata, [field]: value };
}

function signatureOf(row: UploadRow): string {
  return `${row.file.name}:${row.file.size}`;
}

function problemsByRow(
  error: unknown,
  rowKeys: readonly string[],
): Readonly<Record<string, UploadFileProblem>> {
  if (!(error instanceof ApiError)) {
    return {};
  }
  const byRow: Record<string, UploadFileProblem> = {};
  for (const problem of error.fileErrors) {
    const key = rowKeys[problem.index];
    if (key !== undefined) {
      byRow[key] = problem;
    }
  }
  return byRow;
}
