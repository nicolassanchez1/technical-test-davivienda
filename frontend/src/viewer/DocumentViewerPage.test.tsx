import { screen, waitFor, within } from '@testing-library/react';
import { Route, Routes } from 'react-router';
import { VirtuosoMockContext } from 'react-virtuoso';
import { beforeEach, describe, expect, it } from 'vitest';
import type { DocumentChunkResponse, DocumentResponse } from '../api/types';
import { copy } from '../copy/es';
import { interpolate } from '../copy/interpolate';
import { installFetchMock, jsonResponse, textResponse, type FetchMock } from '../test-support/http';
import { renderWithProviders } from '../test-support/renderWithProviders';
import { firstMatchingChunkIndex } from './ChunkedBody';
import { DocumentViewerPage } from './DocumentViewerPage';

const DOCUMENT_ID = '11111111-1111-1111-1111-111111111111';

/** Enough of a viewport for a handful of chunks, so a long body has to virtualise to fit. */
const VIEWPORT_HEIGHT = 300;
const ITEM_HEIGHT = 50;

const markdownDocument: DocumentResponse = {
  id: DOCUMENT_ID,
  title: 'Guia de despliegue',
  author: 'Equipo de plataforma',
  category: 'MANUAL',
  tags: ['infra', 'despliegue'],
  version: '1.0',
  originalFilename: 'despliegue.md',
  mimeType: 'text/markdown',
  sizeBytes: 2048,
  status: 'INDEXADO',
  chunkCount: 12,
  processingMs: 340,
  createdAt: '2026-01-02T10:00:00Z',
  updatedAt: '2026-01-02T10:00:05Z',
  indexedAt: '2026-01-02T10:00:05Z',
};

const MARKDOWN_SOURCE = [
  '# Guia de despliegue',
  '',
  'Texto de introduccion sobre el despliegue.',
  '',
  '## Rollback',
  '',
  'El despliegue se revierte con un comando.',
  '',
  '| Entorno | Estado |',
  '| --- | --- |',
  '| Produccion | Activo |',
  '',
].join('\n');

function chunk(index: number, content: string, page?: number): DocumentChunkResponse {
  return { chunkIndex: index, content, page };
}

type Bodies = {
  readonly document: DocumentResponse;
  readonly chunks?: readonly DocumentChunkResponse[];
  readonly nextChunkIndex?: number;
  readonly markdown?: string;
};

function respondWith(mock: FetchMock, bodies: Bodies): void {
  mock.mockImplementation((input) => {
    const url = String(input);
    if (url.includes('/content')) {
      return Promise.resolve(
        jsonResponse({ chunks: bodies.chunks ?? [], nextChunkIndex: bodies.nextChunkIndex }),
      );
    }
    if (url.includes('/file')) {
      return Promise.resolve(textResponse(bodies.markdown ?? ''));
    }
    return Promise.resolve(jsonResponse(bodies.document));
  });
}

function renderViewer(route = `/documents/${DOCUMENT_ID}`) {
  return renderWithProviders(
    <VirtuosoMockContext.Provider
      value={{ viewportHeight: VIEWPORT_HEIGHT, itemHeight: ITEM_HEIGHT }}
    >
      <Routes>
        <Route path="/documents/:id" element={<DocumentViewerPage />} />
      </Routes>
    </VirtuosoMockContext.Provider>,
    { route },
  );
}

function contentRequests(mock: FetchMock): string[] {
  return mock.mock.calls.map((call) => String(call[0])).filter((url) => url.includes('/content'));
}

describe('DocumentViewerPage', () => {
  let fetchMock: FetchMock;

  beforeEach(() => {
    fetchMock = installFetchMock();
  });

  it('shows every metadata field the catalog knows, with a link to the original file', async () => {
    respondWith(fetchMock, { document: markdownDocument, markdown: MARKDOWN_SOURCE });
    renderViewer();

    expect(
      await screen.findByRole('heading', { level: 2, name: 'Guia de despliegue' }),
    ).toBeInTheDocument();

    const metadata = screen.getByRole('complementary');
    expect(within(metadata).getByRole('status')).toHaveTextContent(copy.statuses.INDEXADO);
    expect(within(metadata).getByText('Equipo de plataforma')).toBeInTheDocument();
    expect(within(metadata).getByText(copy.categories.MANUAL)).toBeInTheDocument();
    expect(within(metadata).getByText('1.0')).toBeInTheDocument();
    expect(within(metadata).getByText('infra')).toBeInTheDocument();
    expect(within(metadata).getByText('despliegue.md')).toBeInTheDocument();
    expect(within(metadata).getByText('text/markdown')).toBeInTheDocument();
    expect(within(metadata).getByText('2 KB')).toBeInTheDocument();
    expect(within(metadata).getByText('12')).toBeInTheDocument();
    expect(
      within(metadata).getByText(interpolate(copy.viewer.metadata.milliseconds, { value: 340 })),
    ).toBeInTheDocument();
    expect(within(metadata).getByRole('link', { name: copy.viewer.openOriginal })).toHaveAttribute(
      'href',
      `/api/documents/${DOCUMENT_ID}/file`,
    );
  });

  it('renders Markdown from the stored file, with GitHub tables and a table of contents', async () => {
    respondWith(fetchMock, { document: markdownDocument, markdown: MARKDOWN_SOURCE });
    renderViewer();

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Guia de despliegue' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 2, name: 'Rollback' })).toBeInTheDocument();
    expect(screen.getByRole('table')).toBeInTheDocument();

    const contents = screen.getByRole('navigation', { name: copy.viewer.toc.title });
    expect(within(contents).getByRole('link', { name: 'Rollback' })).toHaveAttribute(
      'href',
      '#rollback',
    );
    // The body never asks for the indexed chunks: Markdown is read as it was written.
    expect(contentRequests(fetchMock)).toHaveLength(0);
  });

  it('highlights the terms of the query it was opened with', async () => {
    respondWith(fetchMock, { document: markdownDocument, markdown: MARKDOWN_SOURCE });
    const { container } = renderViewer(`/documents/${DOCUMENT_ID}?q=despliegue`);

    await screen.findByRole('heading', { level: 1, name: /Guia de/ });

    const marks = [...container.querySelectorAll('mark')];
    expect(marks.length).toBeGreaterThan(0);
    expect(new Set(marks.map((mark) => mark.textContent))).toEqual(new Set(['despliegue']));
  });

  it('renders plain text chunks with their whitespace preserved', async () => {
    respondWith(fetchMock, {
      document: { ...markdownDocument, mimeType: 'text/plain', originalFilename: 'notas.txt' },
      chunks: [chunk(0, 'linea uno\n  linea dos')],
    });
    renderViewer();

    const paragraph = await screen.findByText(/linea uno/);
    expect(paragraph.textContent).toBe('linea uno\n  linea dos');
    expect(paragraph).toHaveClass('whitespace-pre-wrap');
    expect(contentRequests(fetchMock)[0]).toContain('fromChunkIndex=0');
  });

  it('labels every block of a PDF with the page it was extracted from', async () => {
    respondWith(fetchMock, {
      document: { ...markdownDocument, mimeType: 'application/pdf', pageCount: 2 },
      chunks: [chunk(0, 'Texto de la primera pagina', 1), chunk(1, 'Texto de la segunda', 2)],
    });
    renderViewer();

    expect(
      await screen.findByText(interpolate(copy.viewer.body.pageLabel, { page: 1 })),
    ).toBeInTheDocument();
    expect(
      screen.getByText(interpolate(copy.viewer.body.pageLabel, { page: 2 })),
    ).toBeInTheDocument();
  });

  it('says a document is still being indexed instead of showing an empty body', async () => {
    respondWith(fetchMock, {
      document: { ...markdownDocument, status: 'PROCESANDO', indexedAt: undefined },
    });
    renderViewer();

    expect(await screen.findByText(copy.viewer.body.processing)).toBeInTheDocument();
    expect(contentRequests(fetchMock)).toHaveLength(0);
  });

  it('explains in Spanish why a failed document has nothing to read', async () => {
    respondWith(fetchMock, {
      document: {
        ...markdownDocument,
        mimeType: 'application/pdf',
        status: 'ERROR',
        errorCode: 'PDF_NO_TEXT_LAYER',
      },
    });
    renderViewer();

    expect(await screen.findByText(copy.viewer.body.failed)).toBeInTheDocument();
    expect(screen.getAllByText(copy.errorCodes.PDF_NO_TEXT_LAYER).length).toBeGreaterThan(0);
    expect(contentRequests(fetchMock)).toHaveLength(0);
  });

  it('mounts only the chunks on screen, never the whole body', async () => {
    const chunks = Array.from({ length: 60 }, (_, index) =>
      chunk(index, `Fragmento numero ${index}`),
    );
    respondWith(fetchMock, {
      document: { ...markdownDocument, mimeType: 'text/plain', chunkCount: 60 },
      chunks,
    });
    renderViewer();

    await screen.findByText('Fragmento numero 0');
    await waitFor(() => {
      expect(screen.getAllByRole('article').length).toBeLessThan(chunks.length / 2);
    });
    expect(screen.queryByText('Fragmento numero 59')).not.toBeInTheDocument();
  });

  it('highlights the query terms inside a text chunk as well', async () => {
    respondWith(fetchMock, {
      document: { ...markdownDocument, mimeType: 'text/plain' },
      chunks: [chunk(0, 'El despliegue se revierte con un comando.')],
    });
    const { container } = renderViewer(`/documents/${DOCUMENT_ID}?q=despliegue`);

    await screen.findByText(/se revierte/);
    expect([...container.querySelectorAll('mark')].map((mark) => mark.textContent)).toEqual([
      'despliegue',
    ]);
  });

  it('opens a long body on the first chunk that matched, and at the top when none did', () => {
    const chunks = Array.from({ length: 60 }, (_, index) =>
      chunk(index, index === 40 ? 'El despliegue se revierte' : `Fragmento numero ${index}`),
    );

    expect(firstMatchingChunkIndex(chunks, ['despliegue'])).toBe(40);
    expect(firstMatchingChunkIndex(chunks, ['inexistente'])).toBe(0);
    expect(firstMatchingChunkIndex(chunks, [])).toBe(0);
  });

  it('shows the empty state when an indexed document has no chunk to show', async () => {
    respondWith(fetchMock, {
      document: { ...markdownDocument, mimeType: 'text/plain' },
      chunks: [],
    });
    renderViewer();

    expect(await screen.findByText(copy.viewer.body.empty)).toBeInTheDocument();
  });
});
