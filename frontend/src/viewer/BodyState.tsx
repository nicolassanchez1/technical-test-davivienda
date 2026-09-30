import { copy } from '../copy/es';
import { describeError } from '../shared/errors';

export function BodyLoading() {
  return (
    <p role="status" className="text-slate-600">
      {copy.states.loading}
    </p>
  );
}

export function BodyNotice({ text }: { readonly text: string }) {
  return <p className="rounded-md bg-slate-50 p-4 text-slate-600">{text}</p>;
}

export function BodyError({
  error,
  onRetry,
}: {
  readonly error: unknown;
  readonly onRetry: () => void;
}) {
  return (
    <div role="alert" className="flex flex-col items-start gap-3 rounded-md bg-rose-50 p-4">
      <p className="font-medium text-rose-900">{copy.states.errorTitle}</p>
      <p className="text-sm text-rose-800">{describeError(error)}</p>
      <button
        type="button"
        onClick={onRetry}
        className="rounded-md bg-rose-700 px-3 py-1.5 text-sm font-medium text-white"
      >
        {copy.actions.retry}
      </button>
    </div>
  );
}
