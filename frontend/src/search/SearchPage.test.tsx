import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useLocation } from 'react-router';
import { beforeEach, describe, expect, it } from 'vitest';
import type { SearchResponse } from '../api/types';
import { copy } from '../copy/es';
import { interpolate } from '../copy/interpolate';
import {
  installFetchMock,
  jsonResponse,
  problemResponse,
  type FetchMock,
} from '../test-support/http';
import { renderWithProviders } from '../test-support/renderWithProviders';
import { SearchPage } from './SearchPage';

const DOCUMENT_ID = '11111111-1111-1111-1111-111111111111';

const results: SearchResponse = {
  items: [
    {
      id: DOCUMENT_ID,
      title: 'Guia de despliegue',
      titleHighlight: 'Guia de ⟦despliegue⟧',
      author: 'Equipo de plataforma',
      category: 'MANUAL',
      tags: ['infra'],
      version: '1.0',
      indexedAt: '2026-01-02T10:00:00Z',
      chunkIndex: 3,
      heading: 'Rollback',
      snippet: 'El ⟦despliegue⟧ se revierte con un comando.',
      rank: 0.8,
    },
  ],
  total: 1,
  page: 1,
  pageSize: 10,
  tookMs: 42,
};

/** Renders the query string, which is the only way a test can see where a navigation landed. */
function CurrentLocation() {
  return <output>{useLocation().search}</output>;
}

function searchRequests(mock: FetchMock): string[] {
  return mock.mock.calls.map((call) => String(call[0])).filter((url) => url.includes('/search'));
}

describe('SearchPage', () => {
  let fetchMock: FetchMock;

  beforeEach(() => {
    fetchMock = installFetchMock();
  });

  it('starts idle and asks the engine for nothing until something is typed', () => {
    renderWithProviders(<SearchPage />);

    expect(screen.getByText(copy.search.idle)).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('shows the loading state while the first page is on its way', () => {
    fetchMock.mockResolvedValue(jsonResponse(results));
    renderWithProviders(<SearchPage />, { route: '/?q=despliegue' });

    expect(screen.getByRole('status')).toHaveTextContent(copy.states.loading);
  });

  it('renders each result with its highlighted title and fragment', async () => {
    fetchMock.mockResolvedValue(jsonResponse(results));
    const { container } = renderWithProviders(<SearchPage />, { route: '/?q=despliegue' });

    const list = await screen.findByRole('list', { name: copy.search.resultsLabel });
    expect(within(list).getByRole('link', { name: 'Guia de despliegue' })).toBeInTheDocument();
    expect(within(list).getByText(/se revierte con un comando/)).toHaveTextContent(
      'El despliegue se revierte con un comando.',
    );
    expect(
      within(list).getByText(interpolate(copy.search.matchInHeading, { heading: 'Rollback' })),
    ).toBeInTheDocument();

    // The sentinels became mark nodes: the title and the fragment, never raw HTML.
    const marks = container.querySelectorAll('mark');
    expect([...marks].map((mark) => mark.textContent)).toEqual(['despliegue', 'despliegue']);
  });

  it('reports how many documents matched and how long the engine took', async () => {
    fetchMock.mockResolvedValue(jsonResponse(results));
    renderWithProviders(<SearchPage />, { route: '/?q=despliegue' });

    expect(
      await screen.findByText(interpolate(copy.search.summaryOne, { took: 42 })),
    ).toBeInTheDocument();
  });

  it('shows the empty state when the query matched nothing', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse({ items: [], total: 0, page: 1, pageSize: 10, tookMs: 7 }),
    );
    renderWithProviders(<SearchPage />, { route: '/?q=inexistente' });

    expect(await screen.findByText(copy.search.empty)).toBeInTheDocument();
    expect(screen.getByText(copy.search.emptyHint)).toBeInTheDocument();
  });

  it('explains a query with nothing to search for instead of failing generically', async () => {
    fetchMock.mockResolvedValue(
      problemResponse(
        {
          type: 'urn:problem-type:invalid-search-query',
          detail: 'The query must contain at least one searchable word.',
        },
        400,
      ),
    );
    renderWithProviders(<SearchPage />, { route: '/?q=de+la' });

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent(copy.search.errors.noSearchableTerm);
    expect(alert).not.toHaveTextContent(copy.states.invalidRequest);
    expect(within(alert).getByRole('button', { name: copy.actions.retry })).toBeInTheDocument();
  });

  it('explains a page beyond the last one apart from an unsearchable query', async () => {
    fetchMock.mockResolvedValue(problemResponse({ type: 'urn:problem-type:invalid-request' }, 400));
    renderWithProviders(<SearchPage />, { route: '/?q=despliegue&page=9000' });

    expect(await screen.findByRole('alert')).toHaveTextContent(copy.search.errors.pageOutOfRange);
  });

  it('invites narrowing the search when it ran out of its time budget', async () => {
    fetchMock.mockResolvedValue(problemResponse({ type: 'urn:problem-type:search-timeout' }, 503));
    renderWithProviders(<SearchPage />, { route: '/?q=despliegue' });

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent(copy.search.errors.timeout);
    expect(alert).not.toHaveTextContent(copy.states.serviceUnavailable);
  });

  it('asks once for a settled query instead of once per keystroke', async () => {
    const user = userEvent.setup();
    fetchMock.mockResolvedValue(jsonResponse(results));
    renderWithProviders(<SearchPage />);

    await user.type(screen.getByRole('searchbox', { name: copy.search.inputLabel }), 'postgres');

    await waitFor(() => {
      expect(searchRequests(fetchMock)).toHaveLength(1);
    });
    expect(searchRequests(fetchMock)[0]).toContain('q=postgres');
  });

  it('sends the metadata filters, repeating a tag parameter per tag', async () => {
    const user = userEvent.setup();
    fetchMock.mockResolvedValue(jsonResponse(results));
    renderWithProviders(<SearchPage />, { route: '/?q=despliegue&tags=infra&tags=api' });

    await screen.findByRole('link', { name: 'Guia de despliegue' });
    expect(searchRequests(fetchMock)[0]).toContain('tags=infra&tags=api');

    await user.selectOptions(
      screen.getByRole('combobox', { name: copy.search.filters.category }),
      'MANUAL',
    );

    await waitFor(() => {
      expect(searchRequests(fetchMock).length).toBeGreaterThan(1);
    });
    expect(searchRequests(fetchMock).at(-1)).toContain('category=MANUAL');
  });

  it('clears the filters without the debounced input putting them back', async () => {
    const user = userEvent.setup();
    fetchMock.mockResolvedValue(jsonResponse(results));
    const { container } = renderWithProviders(
      <>
        <SearchPage />
        <CurrentLocation />
      </>,
      { route: '/?q=despliegue&author=Equipo+de+plataforma&tags=infra' },
    );

    await screen.findByRole('link', { name: 'Guia de despliegue' });
    await user.click(screen.getByRole('button', { name: copy.search.filters.clear }));

    // Read right after the click: a debounced value that has not caught up would put the filters
    // back into the URL here, and only take them out again 300 ms later.
    expect(container.querySelector('output')?.textContent).toBe('?q=despliegue');
    expect(screen.getByRole('textbox', { name: copy.search.filters.author })).toHaveValue('');

    await waitFor(() => {
      expect(searchRequests(fetchMock).length).toBeGreaterThan(1);
    });
    const last = searchRequests(fetchMock).at(-1) ?? '';
    expect(last).toContain('q=despliegue');
    expect(last).not.toContain('author=');
    expect(last).not.toContain('tags=');
  });

  it('pages forward without losing the query', async () => {
    const user = userEvent.setup();
    fetchMock.mockResolvedValue(jsonResponse({ ...results, total: 24 }));
    renderWithProviders(<SearchPage />, { route: '/?q=despliegue' });

    await screen.findByRole('link', { name: 'Guia de despliegue' });
    expect(screen.getByRole('button', { name: copy.actions.previousPage })).toBeDisabled();

    await user.click(screen.getByRole('button', { name: copy.actions.nextPage }));

    await waitFor(() => {
      expect(searchRequests(fetchMock).at(-1)).toContain('page=2');
    });
    expect(searchRequests(fetchMock).at(-1)).toContain('q=despliegue');
  });

  it('carries the query to the viewer so the body highlights the same words', async () => {
    fetchMock.mockResolvedValue(jsonResponse(results));
    renderWithProviders(<SearchPage />, { route: '/?q=despliegue' });

    expect(await screen.findByRole('link', { name: 'Guia de despliegue' })).toHaveAttribute(
      'href',
      `/documents/${DOCUMENT_ID}?q=despliegue`,
    );
  });
});
