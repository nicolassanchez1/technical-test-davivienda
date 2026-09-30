import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it } from 'vitest';
import type { DocumentPageResponse } from '../api/types';
import { copy } from '../copy/es';
import { activeEventSource } from '../test-support/fakeEventSource';
import {
  installFetchMock,
  jsonResponse,
  problemResponse,
  requestedUrl,
  type FetchMock,
} from '../test-support/http';
import { renderWithProviders } from '../test-support/renderWithProviders';
import { DocumentsPage } from './DocumentsPage';

const INDEXED_ID = '11111111-1111-1111-1111-111111111111';
const PROCESSING_ID = '22222222-2222-2222-2222-222222222222';

const page: DocumentPageResponse = {
  items: [
    {
      id: INDEXED_ID,
      title: 'Guia de despliegue',
      author: 'Equipo de plataforma',
      category: 'MANUAL',
      tags: ['infra', 'despliegue'],
      version: '1.0',
      sizeBytes: 2048,
      status: 'INDEXADO',
      createdAt: '2026-01-02T10:00:00Z',
    },
    {
      id: PROCESSING_ID,
      title: 'Especificacion de API',
      author: 'Equipo de backend',
      category: 'SPECIFICATION',
      tags: [],
      version: '2.1',
      sizeBytes: 4096,
      status: 'PROCESANDO',
      createdAt: '2026-01-03T10:00:00Z',
    },
  ],
  total: 2,
  page: 1,
  pageSize: 10,
};

function rowFor(title: string): HTMLElement {
  return screen.getByRole('row', { name: new RegExp(title) });
}

describe('DocumentsPage', () => {
  let fetchMock: FetchMock;

  beforeEach(() => {
    fetchMock = installFetchMock();
  });

  it('shows the loading state before the first page arrives', () => {
    fetchMock.mockResolvedValue(jsonResponse(page));
    renderWithProviders(<DocumentsPage />);

    expect(screen.getByRole('status')).toHaveTextContent(copy.states.loading);
  });

  it('renders one row per document with its metadata and status badge', async () => {
    fetchMock.mockResolvedValue(jsonResponse(page));
    renderWithProviders(<DocumentsPage />);

    expect(await screen.findByRole('link', { name: 'Guia de despliegue' })).toBeInTheDocument();

    const indexed = rowFor('Guia de despliegue');
    expect(within(indexed).getByRole('status')).toHaveTextContent(copy.statuses.INDEXADO);
    expect(indexed).toHaveTextContent(copy.categories.MANUAL);
    expect(within(rowFor('Especificacion de API')).getByRole('status')).toHaveTextContent(
      copy.statuses.PROCESANDO,
    );
    expect(screen.getByText(copy.documents.noTags)).toBeInTheDocument();
  });

  it('shows the empty state when no document has been uploaded', async () => {
    fetchMock.mockResolvedValue(jsonResponse({ items: [], total: 0, page: 1, pageSize: 10 }));
    renderWithProviders(<DocumentsPage />);

    expect(await screen.findByText(copy.documents.empty)).toBeInTheDocument();
  });

  it('shows the filtered empty state when a status matches nothing', async () => {
    fetchMock.mockResolvedValue(jsonResponse({ items: [], total: 0, page: 1, pageSize: 10 }));
    renderWithProviders(<DocumentsPage />, { route: '/documents?status=ERROR' });

    expect(await screen.findByText(copy.documents.emptyFiltered)).toBeInTheDocument();
  });

  it('shows a Spanish error and an explicit retry when the request fails', async () => {
    fetchMock.mockResolvedValue(
      problemResponse({ title: 'Service Unavailable', detail: 'Dependency is down.' }, 503),
    );
    renderWithProviders(<DocumentsPage />);

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent(copy.states.errorTitle);
    expect(alert).toHaveTextContent(copy.states.serviceUnavailable);
    expect(alert).not.toHaveTextContent('Dependency is down.');
    expect(screen.getByRole('button', { name: copy.actions.retry })).toBeInTheDocument();
  });

  it('asks the API for the chosen status and resets to the first page', async () => {
    const user = userEvent.setup();
    fetchMock.mockResolvedValue(jsonResponse(page));
    renderWithProviders(<DocumentsPage />, { route: '/documents?page=3' });

    await screen.findByRole('link', { name: 'Guia de despliegue' });
    await user.selectOptions(
      screen.getByRole('combobox', { name: copy.documents.filter.label }),
      'PROCESANDO',
    );

    await waitFor(() => {
      expect(fetchMock.mock.calls.length).toBeGreaterThan(1);
    });
    const lastUrl = requestedUrl(fetchMock, fetchMock.mock.calls.length - 1);
    expect(lastUrl).toContain('status=PROCESANDO');
    expect(lastUrl).toContain('page=1');
  });

  it('moves the status badge to its new state when the stream announces a change', async () => {
    fetchMock.mockResolvedValue(jsonResponse(page));
    renderWithProviders(<DocumentsPage />);

    await screen.findByRole('link', { name: 'Especificacion de API' });
    expect(within(rowFor('Especificacion de API')).getByRole('status')).toHaveTextContent(
      copy.statuses.PROCESANDO,
    );

    act(() => {
      activeEventSource().emit(
        'document.status',
        JSON.stringify({
          documentId: PROCESSING_ID,
          status: 'ERROR',
          errorCode: 'PDF_NO_TEXT_LAYER',
        }),
      );
    });

    await waitFor(() => {
      expect(within(rowFor('Especificacion de API')).getByRole('status')).toHaveTextContent(
        copy.statuses.ERROR,
      );
    });
    expect(within(rowFor('Especificacion de API')).getByRole('status')).toHaveTextContent(
      copy.errorCodes.PDF_NO_TEXT_LAYER,
    );
    // The badge changed without a second request: the event carried the new state.
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('pages forward without asking for a page past the last one', async () => {
    const user = userEvent.setup();
    fetchMock.mockResolvedValue(jsonResponse({ ...page, total: 24 }));
    renderWithProviders(<DocumentsPage />);

    await screen.findByRole('link', { name: 'Guia de despliegue' });
    expect(screen.getByRole('button', { name: copy.actions.previousPage })).toBeDisabled();

    await user.click(screen.getByRole('button', { name: copy.actions.nextPage }));

    await waitFor(() => {
      expect(requestedUrl(fetchMock, fetchMock.mock.calls.length - 1)).toContain('page=2');
    });
  });
});
