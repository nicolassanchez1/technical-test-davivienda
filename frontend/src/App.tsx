import { copy } from './copy/es';

export function App() {
  return (
    <main>
      <h1>{copy.appTitle}</h1>
      <p>{copy.appTagline}</p>
    </main>
  );
}
