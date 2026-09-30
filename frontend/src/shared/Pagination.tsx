import { copy } from '../copy/es';

export type PaginationProps = {
  readonly label: string;
  readonly indicator: string;
  readonly page: number;
  readonly pageCount: number;
  readonly onChange: (page: number) => void;
};

export function Pagination({ label, indicator, page, pageCount, onChange }: PaginationProps) {
  return (
    <nav className="flex items-center justify-between gap-4" aria-label={label}>
      <button
        type="button"
        disabled={page <= 1}
        onClick={() => onChange(page - 1)}
        className="rounded-md border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-800 disabled:opacity-40"
      >
        {copy.actions.previousPage}
      </button>
      <p className="text-sm text-slate-600">{indicator}</p>
      <button
        type="button"
        disabled={page >= pageCount}
        onClick={() => onChange(page + 1)}
        className="rounded-md border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-800 disabled:opacity-40"
      >
        {copy.actions.nextPage}
      </button>
    </nav>
  );
}
