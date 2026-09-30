import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, beforeEach } from 'vitest';
import { App } from './App';
import { copy } from './copy/es';
import { installFetchMock, jsonResponse } from './test-support/http';
import { renderWithProviders } from './test-support/renderWithProviders';

describe('App shell', () => {
  beforeEach(() => {
    installFetchMock().mockResolvedValue(
      jsonResponse({ items: [], total: 0, page: 1, pageSize: 10 }),
    );
  });

  it('renders the application title from the Spanish copy file', () => {
    renderWithProviders(<App />);

    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent(copy.appTitle);
    expect(screen.getByText(copy.appTagline)).toBeInTheDocument();
  });

  it('exposes the three sections as named navigation links', () => {
    renderWithProviders(<App />);

    const navigation = screen.getByRole('navigation', { name: copy.nav.label });
    for (const label of [copy.nav.search, copy.nav.upload, copy.nav.documents]) {
      expect(screen.getByRole('link', { name: label })).toBeInTheDocument();
    }
    expect(navigation).toBeInTheDocument();
  });

  it('renders the search route at the root', () => {
    renderWithProviders(<App />, { route: '/' });

    expect(screen.getByRole('heading', { level: 2, name: copy.search.title })).toBeInTheDocument();
  });

  it('renders the upload route', () => {
    renderWithProviders(<App />, { route: '/upload' });

    expect(screen.getByRole('heading', { level: 2, name: copy.upload.title })).toBeInTheDocument();
  });

  it('renders the viewer route for one document', () => {
    renderWithProviders(<App />, { route: '/documents/11111111-1111-1111-1111-111111111111' });

    expect(screen.getByRole('heading', { level: 2, name: copy.viewer.title })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: copy.viewer.backToSearch })).toBeInTheDocument();
  });

  it('renders the not-found page for an unknown route', () => {
    renderWithProviders(<App />, { route: '/unknown' });

    expect(
      screen.getByRole('heading', { level: 2, name: copy.notFound.title }),
    ).toBeInTheDocument();
  });

  it('navigates from the header without a full page load', async () => {
    const user = userEvent.setup();
    renderWithProviders(<App />, { route: '/' });

    await user.click(screen.getByRole('link', { name: copy.nav.documents }));

    expect(
      await screen.findByRole('heading', { level: 2, name: copy.documents.title }),
    ).toBeInTheDocument();
  });
});
