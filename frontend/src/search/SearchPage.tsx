import { Link } from 'react-router';
import { copy } from '../copy/es';

export function SearchPage() {
  return (
    <section className="flex flex-col gap-4">
      <header className="flex flex-col gap-2">
        <h2 className="text-2xl font-semibold text-slate-900">{copy.search.title}</h2>
        <p className="text-slate-600">{copy.search.intro}</p>
      </header>
      <p className="rounded-md bg-slate-50 p-4 text-slate-600">{copy.search.pending}</p>
      <div className="flex gap-4">
        <Link to="/upload" className="text-sky-800 underline">
          {copy.actions.goToUpload}
        </Link>
        <Link to="/documents" className="text-sky-800 underline">
          {copy.actions.goToDocuments}
        </Link>
      </div>
    </section>
  );
}
