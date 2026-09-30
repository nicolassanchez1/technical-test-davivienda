import { QueryClientProvider } from '@tanstack/react-query';
import { act, render, waitFor } from '@testing-library/react';
import { toast } from 'sonner';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { documentKeys } from '../api/queryKeys';
import type { DocumentPageResponse } from '../api/types';
import { createQueryClient } from '../app/queryClient';
import { copy } from '../copy/es';
import { FakeEventSource, activeEventSource } from '../test-support/fakeEventSource';
import { installFetchMock, jsonResponse, requestedUrl, type FetchMock } from '../test-support/http';
import { DocumentStatusProvider } from './DocumentStatusProvider';

vi.mock('sonner', () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
    info: vi.fn(),
    warning: vi.fn(),
  },
}));

const DOCUMENT_ID = '11111111-1111-1111-1111-111111111111';
const LIST_KEY = documentKeys.list({ status: null, page: 1, pageSize: 10 });

function processingPage(): DocumentPageResponse {
  return {
    items: [
      {
        id: DOCUMENT_ID,
        title: 'Guia de despliegue',
        author: 'Equipo',
        category: 'MANUAL',
        tags: ['infra'],
        version: '1.0',
        status: 'PROCESANDO',
      },
    ],
    total: 1,
    page: 1,
    pageSize: 10,
  };
}

function renderProvider(seed?: DocumentPageResponse) {
  const queryClient = createQueryClient();
  if (seed) {
    queryClient.setQueryData(LIST_KEY, seed);
  }
  const result = render(
    <QueryClientProvider client={queryClient}>
      <DocumentStatusProvider>
        <span />
      </DocumentStatusProvider>
    </QueryClientProvider>,
  );
  return { ...result, queryClient };
}

function cachedStatus(queryClient: ReturnType<typeof createQueryClient>): string | undefined {
  return queryClient.getQueryData<DocumentPageResponse>(LIST_KEY)?.items?.[0]?.status;
}

function emitStatus(payload: unknown): void {
  act(() => {
    activeEventSource().emit('document.status', JSON.stringify(payload));
  });
}

describe('DocumentStatusProvider', () => {
  let fetchMock: FetchMock;

  beforeEach(() => {
    vi.mocked(toast.success).mockClear();
    vi.mocked(toast.error).mockClear();
    vi.mocked(toast.info).mockClear();
    vi.mocked(toast.warning).mockClear();
    fetchMock = installFetchMock();
  });

  it('opens exactly one stream for the whole application and closes it on unmount', () => {
    const { unmount } = renderProvider();

    expect(FakeEventSource.instances).toHaveLength(1);
    expect(activeEventSource().url).toBe('/api/events');

    unmount();

    expect(FakeEventSource.instances[0]?.closed).toBe(true);
  });

  it('never installs an interval, because a status change is pushed rather than asked for', () => {
    const setInterval = vi.spyOn(globalThis, 'setInterval');

    renderProvider(processingPage());
    emitStatus({ documentId: DOCUMENT_ID, status: 'INDEXADO' });

    expect(setInterval).not.toHaveBeenCalled();
    setInterval.mockRestore();
  });

  it('writes the indexed status into the cached list and announces it', () => {
    const { queryClient } = renderProvider(processingPage());

    emitStatus({ documentId: DOCUMENT_ID, status: 'INDEXADO', occurredAt: '2026-01-01T10:00:00Z' });

    expect(cachedStatus(queryClient)).toBe('INDEXADO');
    expect(fetchMock).not.toHaveBeenCalled();
    expect(vi.mocked(toast.success)).toHaveBeenCalledWith(
      copy.notifications.indexed.title,
      expect.objectContaining({ description: expect.stringContaining('Guia de despliegue') }),
    );
  });

  it('writes the failure into the cache and announces the Spanish reason for its error code', () => {
    const { queryClient } = renderProvider(processingPage());

    emitStatus({ documentId: DOCUMENT_ID, status: 'ERROR', errorCode: 'PDF_NO_TEXT_LAYER' });

    const cached = queryClient.getQueryData<DocumentPageResponse>(LIST_KEY);
    expect(cached?.items?.[0]?.status).toBe('ERROR');
    expect(cached?.items?.[0]?.errorCode).toBe('PDF_NO_TEXT_LAYER');
    expect(vi.mocked(toast.error)).toHaveBeenCalledWith(
      copy.notifications.failed.title,
      expect.objectContaining({
        description: expect.stringContaining(copy.errorCodes.PDF_NO_TEXT_LAYER),
      }),
    );
  });

  it('also patches the cached detail of the document the event names', () => {
    const { queryClient } = renderProvider();
    queryClient.setQueryData(documentKeys.detail(DOCUMENT_ID), {
      id: DOCUMENT_ID,
      title: 'Guia',
      status: 'PROCESANDO',
    });

    emitStatus({ documentId: DOCUMENT_ID, status: 'INDEXADO' });

    expect(
      queryClient.getQueryData<{ status?: string }>(documentKeys.detail(DOCUMENT_ID))?.status,
    ).toBe('INDEXADO');
  });

  it('ignores a frame that is not a usable status change', () => {
    const { queryClient } = renderProvider(processingPage());

    act(() => {
      activeEventSource().emit('document.status', 'not json');
      activeEventSource().emit('document.status', JSON.stringify({ status: 'INDEXADO' }));
      activeEventSource().emit(
        'document.status',
        JSON.stringify({ documentId: DOCUMENT_ID, status: 'FINISHED' }),
      );
    });

    expect(cachedStatus(queryClient)).toBe('PROCESANDO');
    expect(vi.mocked(toast.success)).not.toHaveBeenCalled();
  });

  it('reconciles with a single request per connection for documents still processing', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse({
        items: [{ id: DOCUMENT_ID, title: 'Guia de despliegue', status: 'INDEXADO' }],
        total: 1,
        page: 1,
        pageSize: 50,
      }),
    );
    const { queryClient } = renderProvider(processingPage());

    act(() => {
      activeEventSource().open();
    });

    await waitFor(() => {
      expect(cachedStatus(queryClient)).toBe('INDEXADO');
    });
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(requestedUrl(fetchMock)).toContain('/api/documents');
    // Catching up is not news: the reader was not watching, so nothing is announced.
    expect(vi.mocked(toast.success)).not.toHaveBeenCalled();
  });

  it('does not reconcile when nothing is shown as processing', async () => {
    renderProvider({
      items: [{ id: DOCUMENT_ID, title: 'Guia', status: 'INDEXADO' }],
      total: 1,
      page: 1,
      pageSize: 10,
    });

    act(() => {
      activeEventSource().open();
    });
    await Promise.resolve();

    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('reconciles again on the next connection and never in between', async () => {
    fetchMock.mockResolvedValue(jsonResponse({ items: [], total: 0, page: 1, pageSize: 50 }));
    renderProvider(processingPage());

    act(() => {
      activeEventSource().open();
    });
    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledTimes(1);
    });

    act(() => {
      activeEventSource().fail();
    });
    expect(fetchMock).toHaveBeenCalledTimes(1);

    act(() => {
      activeEventSource().open();
    });
    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledTimes(2);
    });
  });

  it('reports a dropped connection once and confirms when it comes back', () => {
    fetchMock.mockResolvedValue(jsonResponse({ items: [], total: 0, page: 1, pageSize: 50 }));
    renderProvider();

    act(() => {
      activeEventSource().fail();
      activeEventSource().fail();
    });

    expect(vi.mocked(toast.warning)).toHaveBeenCalledTimes(1);
    expect(vi.mocked(toast.warning)).toHaveBeenCalledWith(copy.notifications.streamLost);

    act(() => {
      activeEventSource().open();
    });

    expect(vi.mocked(toast.success)).toHaveBeenCalledWith(copy.notifications.streamRestored);
  });
});
