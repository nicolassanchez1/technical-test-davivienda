import { useParams } from 'react-router';
import { copy } from '../copy/es';
import { interpolate } from '../copy/interpolate';

export function DocumentViewerPage() {
  const { id } = useParams<{ id: string }>();

  return (
    <section className="flex flex-col gap-4">
      <header className="flex flex-col gap-2">
        <h2 className="text-2xl font-semibold text-slate-900">{copy.viewer.title}</h2>
        <p className="text-slate-600">{interpolate(copy.viewer.documentId, { id: id ?? '' })}</p>
      </header>
      <p className="rounded-md bg-slate-50 p-4 text-slate-600">{copy.viewer.pending}</p>
    </section>
  );
}
