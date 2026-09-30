import { act, fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it } from 'vitest';
import { copy } from '../copy/es';
import { interpolate } from '../copy/interpolate';
import { activeEventSource } from '../test-support/fakeEventSource';
import {
  installFetchMock,
  jsonResponse,
  problemResponse,
  type FetchMock,
} from '../test-support/http';
import { renderWithProviders } from '../test-support/renderWithProviders';
import { UploadPage } from './UploadPage';
import { MAX_FILE_SIZE_BYTES, MAX_FILE_SIZE_MB } from './limits';

const EXISTING_ID = '99999999-9999-9999-9999-999999999999';
const ACCEPTED_ID = '11111111-1111-1111-1111-111111111111';

function textFile(name: string, content = 'contenido tecnico'): File {
  return new File([content], name, { type: 'text/plain' });
}

function oversizeFile(name: string): File {
  const file = new File(['x'], name, { type: 'application/pdf' });
  // Declaring the size avoids allocating the megabytes the check is about.
  Object.defineProperty(file, 'size', { value: MAX_FILE_SIZE_BYTES + 1 });
  return file;
}

/**
 * The dropzone input carries an accept attribute, and userEvent honours it by dropping files that
 * do not match; a change event is what lets the rejection path be exercised at all.
 */
async function selectFiles(files: readonly File[]): Promise<void> {
  const input = screen.getByLabelText(copy.upload.dropzone.inputLabel);
  await act(async () => {
    fireEvent.change(input, { target: { files } });
  });
}

function rowFor(filename: string): HTMLElement {
  return screen.getByRole('row', { name: new RegExp(filename) });
}

async function fillRow(filename: string, values: { author: string; version: string }) {
  const user = userEvent.setup();
  await user.type(
    screen.getByLabelText(`${copy.upload.fields.author}: ${filename}`),
    values.author,
  );
  await user.type(
    screen.getByLabelText(`${copy.upload.fields.version}: ${filename}`),
    values.version,
  );
}

describe('UploadPage', () => {
  let fetchMock: FetchMock;

  beforeEach(() => {
    fetchMock = installFetchMock();
  });

  it('adds a row per accepted file and prefills the title from the filename', async () => {
    renderWithProviders(<UploadPage />);

    await selectFiles([textFile('guia-de-despliegue.txt')]);

    expect(await screen.findByRole('row', { name: /guia-de-despliegue\.txt/ })).toBeInTheDocument();
    expect(
      screen.getByLabelText(`${copy.upload.fields.title}: guia-de-despliegue.txt`),
    ).toHaveValue('guia de despliegue');
  });

  it('rejects a file whose extension is not on the allowlist', async () => {
    renderWithProviders(<UploadPage />);

    await selectFiles([
      new File(['binario'], 'instalador.exe', { type: 'application/octet-stream' }),
    ]);

    const rejected = await screen.findByRole('alert');
    expect(rejected).toHaveTextContent(copy.upload.rejectedTitle);
    expect(rejected).toHaveTextContent(copy.upload.rejections.extension);
    expect(screen.getByText(copy.upload.table.empty)).toBeInTheDocument();
  });

  it('rejects a file over the maximum size before sending anything', async () => {
    renderWithProviders(<UploadPage />);

    await selectFiles([oversizeFile('manual-enorme.pdf')]);

    const rejected = await screen.findByRole('alert');
    expect(rejected).toHaveTextContent(
      interpolate(copy.upload.rejections.tooLarge, { maxSize: `${MAX_FILE_SIZE_MB} MB` }),
    );
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('rejects an empty file', async () => {
    renderWithProviders(<UploadPage />);

    await selectFiles([textFile('vacio.txt', '')]);

    expect(await screen.findByRole('alert')).toHaveTextContent(copy.upload.rejections.empty);
  });

  it('refuses to submit while a required metadata field is blank', async () => {
    const user = userEvent.setup();
    renderWithProviders(<UploadPage />);
    await selectFiles([textFile('guia.md')]);

    await user.click(screen.getByRole('button', { name: copy.actions.submitUpload }));

    const row = rowFor('guia.md');
    expect(within(row).getByText(copy.upload.validation.authorRequired)).toBeInTheDocument();
    expect(within(row).getByText(copy.upload.validation.versionRequired)).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('refuses a title longer than the limit the backend enforces', async () => {
    const user = userEvent.setup();
    renderWithProviders(<UploadPage />);
    await selectFiles([textFile('guia.md')]);

    const title = screen.getByLabelText(`${copy.upload.fields.title}: guia.md`);
    fireEvent.change(title, { target: { value: 'a'.repeat(301) } });
    await fillRow('guia.md', { author: 'Equipo', version: '1.0' });
    await user.click(screen.getByRole('button', { name: copy.actions.submitUpload }));

    expect(
      within(rowFor('guia.md')).getByText(
        interpolate(copy.upload.validation.titleTooLong, { max: 300 }),
      ),
    ).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('applies the common values to every row at once', async () => {
    const user = userEvent.setup();
    renderWithProviders(<UploadPage />);
    await selectFiles([textFile('uno.txt'), textFile('dos.md')]);

    await user.type(screen.getByLabelText(copy.upload.fields.author), 'Equipo de plataforma');
    await user.click(screen.getByRole('button', { name: copy.actions.applyToAll }));

    expect(screen.getByLabelText(`${copy.upload.fields.author}: uno.txt`)).toHaveValue(
      'Equipo de plataforma',
    );
    expect(screen.getByLabelText(`${copy.upload.fields.author}: dos.md`)).toHaveValue(
      'Equipo de plataforma',
    );
  });

  it('sends one multipart request with the files and the metadata aligned by index', async () => {
    const user = userEvent.setup();
    fetchMock.mockResolvedValue(
      jsonResponse(
        { items: [{ id: ACCEPTED_ID, filename: 'guia.md', status: 'PROCESANDO' }] },
        202,
      ),
    );
    renderWithProviders(<UploadPage />);
    await selectFiles([textFile('guia.md')]);
    await fillRow('guia.md', { author: 'Equipo', version: '1.0' });
    await user.type(screen.getByLabelText(`${copy.upload.fields.tags}: guia.md`), 'Infra, INFRA');

    await user.click(screen.getByRole('button', { name: copy.actions.submitUpload }));

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledTimes(1);
    });
    const call = fetchMock.mock.calls[0];
    expect(String(call?.[0])).toBe('/api/documents');
    const body = call?.[1]?.body as FormData;
    expect(body.getAll('files')).toHaveLength(1);
    const metadata = JSON.parse(await (body.get('metadata') as Blob).text()) as unknown[];
    expect(metadata).toEqual([
      {
        title: 'guia',
        author: 'Equipo',
        category: 'MANUAL',
        version: '1.0',
        tags: ['infra'],
      },
    ]);
  });

  it('tracks an accepted document and moves its badge when the stream announces the change', async () => {
    const user = userEvent.setup();
    fetchMock.mockResolvedValue(
      jsonResponse(
        { items: [{ id: ACCEPTED_ID, filename: 'guia.md', status: 'PROCESANDO' }] },
        202,
      ),
    );
    renderWithProviders(<UploadPage />);
    await selectFiles([textFile('guia.md')]);
    await fillRow('guia.md', { author: 'Equipo', version: '1.0' });
    await user.click(screen.getByRole('button', { name: copy.actions.submitUpload }));

    expect(await screen.findByText(copy.upload.accepted.title)).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent(copy.statuses.PROCESANDO);

    act(() => {
      activeEventSource().emit(
        'document.status',
        JSON.stringify({ documentId: ACCEPTED_ID, status: 'INDEXADO' }),
      );
    });

    await waitFor(() => {
      expect(screen.getByRole('status')).toHaveTextContent(copy.statuses.INDEXADO);
    });
  });

  it('renders every per-file error of a rejected batch against its own row', async () => {
    const user = userEvent.setup();
    fetchMock.mockResolvedValue(
      problemResponse(
        {
          type: 'urn:problem-type:invalid-upload',
          detail: 'The batch was rejected.',
          errors: [
            { index: 0, filename: 'roto.pdf', rule: 'PDF_HEADER', errorCode: 'CORRUPT_FILE' },
            {
              index: 1,
              filename: 'copia.md',
              rule: 'UNIQUE_CHECKSUM',
              existingDocumentId: EXISTING_ID,
            },
          ],
        },
        422,
      ),
    );
    renderWithProviders(<UploadPage />);
    await selectFiles([textFile('roto.pdf'), textFile('copia.md')]);
    await fillRow('roto.pdf', { author: 'Equipo', version: '1.0' });
    await fillRow('copia.md', { author: 'Equipo', version: '1.0' });

    await user.click(screen.getByRole('button', { name: copy.actions.submitUpload }));

    const alerts = await screen.findAllByRole('alert');
    const messages = alerts.map((alert) => alert.textContent ?? '');
    expect(messages.some((text) => text.includes(copy.uploadRules.PDF_HEADER))).toBe(true);
    expect(messages.some((text) => text.includes(copy.uploadRules.UNIQUE_CHECKSUM))).toBe(true);
    expect(screen.getByRole('link', { name: copy.upload.duplicateLink })).toHaveAttribute(
      'href',
      `/documents/${EXISTING_ID}`,
    );
    // Nothing was stored, so the rows stay on screen for the reader to fix.
    expect(rowFor('roto.pdf')).toBeInTheDocument();
    expect(rowFor('copia.md')).toBeInTheDocument();
  });

  it('reports an oversize batch the server refused without marking any row', async () => {
    const user = userEvent.setup();
    fetchMock.mockResolvedValue(
      problemResponse({ detail: 'The uploaded file exceeds the configured maximum size.' }, 413),
    );
    renderWithProviders(<UploadPage />);
    await selectFiles([textFile('guia.md')]);
    await fillRow('guia.md', { author: 'Equipo', version: '1.0' });

    await user.click(screen.getByRole('button', { name: copy.actions.submitUpload }));

    expect(await screen.findByText(copy.states.payloadTooLarge)).toBeInTheDocument();
  });

  it('asks for at least one file before submitting an empty form', async () => {
    const user = userEvent.setup();
    renderWithProviders(<UploadPage />);

    await user.click(screen.getByRole('button', { name: copy.actions.submitUpload }));

    expect(screen.getByRole('alert')).toHaveTextContent(copy.upload.validation.noFiles);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
