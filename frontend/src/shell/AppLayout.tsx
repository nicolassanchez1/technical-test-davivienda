import { NavLink, Outlet } from 'react-router';
import { copy } from '../copy/es';

const links = [
  { to: '/', label: copy.nav.search },
  { to: '/upload', label: copy.nav.upload },
  { to: '/documents', label: copy.nav.documents },
];

export function AppLayout() {
  return (
    <div className="min-h-screen bg-white text-slate-900">
      <header className="border-b border-slate-200 bg-slate-50">
        <div className="mx-auto flex max-w-6xl flex-col gap-3 px-6 py-5">
          <div className="flex flex-col gap-1">
            <h1 className="text-xl font-bold tracking-tight">{copy.appTitle}</h1>
            <p className="text-sm text-slate-600">{copy.appTagline}</p>
          </div>
          <nav aria-label={copy.nav.label}>
            <ul className="flex gap-2">
              {links.map((link) => (
                <li key={link.to}>
                  <NavLink
                    to={link.to}
                    end={link.to === '/'}
                    className={({ isActive }) =>
                      `inline-block rounded-md px-3 py-1.5 text-sm font-medium ${
                        isActive ? 'bg-sky-800 text-white' : 'text-slate-700 hover:bg-slate-200'
                      }`
                    }
                  >
                    {link.label}
                  </NavLink>
                </li>
              ))}
            </ul>
          </nav>
        </div>
      </header>
      <main className="mx-auto max-w-6xl px-6 py-8">
        <Outlet />
      </main>
    </div>
  );
}
