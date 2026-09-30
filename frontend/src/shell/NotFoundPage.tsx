import { Link } from 'react-router';
import { copy } from '../copy/es';

export function NotFoundPage() {
  return (
    <section className="flex flex-col items-start gap-3">
      <h2 className="text-2xl font-semibold">{copy.notFound.title}</h2>
      <p className="text-slate-600">{copy.notFound.description}</p>
      <Link to="/" className="text-sky-800 underline">
        {copy.actions.goToSearch}
      </Link>
    </section>
  );
}
