import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router';
import { App } from './App';
import { AppProviders } from './app/AppProviders';
import './index.css';

const container = document.getElementById('root');
if (!container) {
  throw new Error('Root container is missing from index.html');
}

createRoot(container).render(
  <StrictMode>
    <BrowserRouter>
      <AppProviders>
        <App />
      </AppProviders>
    </BrowserRouter>
  </StrictMode>,
);
