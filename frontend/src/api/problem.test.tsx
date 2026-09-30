import { describe, expect, it } from 'vitest';
import { problemResponse } from '../test-support/http';
import { apiErrorFrom } from './problem';

describe('apiErrorFrom', () => {
  it('reads the per-file errors a rejected batch reports', async () => {
    const error = await apiErrorFrom(
      problemResponse(
        {
          type: 'urn:problem-type:invalid-upload',
          detail: 'The batch was rejected.',
          requestId: 'req-1',
          errors: [
            { index: 0, filename: 'roto.pdf', rule: 'PDF_HEADER', errorCode: 'CORRUPT_FILE' },
            {
              index: 2,
              filename: 'copia.md',
              rule: 'UNIQUE_CHECKSUM',
              existingDocumentId: '11111111-1111-1111-1111-111111111111',
            },
          ],
        },
        422,
      ),
    );

    expect(error.status).toBe(422);
    expect(error.detail).toBe('The batch was rejected.');
    expect(error.problemType).toBe('urn:problem-type:invalid-upload');
    expect(error.requestId).toBe('req-1');
    expect(error.fileErrors).toEqual([
      {
        index: 0,
        filename: 'roto.pdf',
        rule: 'PDF_HEADER',
        errorCode: 'CORRUPT_FILE',
        existingDocumentId: undefined,
      },
      {
        index: 2,
        filename: 'copia.md',
        rule: 'UNIQUE_CHECKSUM',
        errorCode: undefined,
        existingDocumentId: '11111111-1111-1111-1111-111111111111',
      },
    ]);
  });

  it('drops an error entry that carries no index, because it cannot mark a row', async () => {
    const error = await apiErrorFrom(
      problemResponse({ detail: 'Rejected.', errors: [{ filename: 'x.md' }, 'nonsense'] }, 422),
    );

    expect(error.fileErrors).toEqual([]);
  });

  it('keeps the status when the body is not JSON at all', async () => {
    const error = await apiErrorFrom(
      new Response('<html>gateway</html>', {
        status: 502,
        headers: { 'content-type': 'text/html' },
      }),
    );

    expect(error.status).toBe(502);
    expect(error.detail).toBeUndefined();
    expect(error.fileErrors).toEqual([]);
  });

  it('keeps the status when the body claims to be JSON but is truncated', async () => {
    const error = await apiErrorFrom(
      new Response('{"detail":', { status: 500, headers: { 'content-type': 'application/json' } }),
    );

    expect(error.status).toBe(500);
    expect(error.detail).toBeUndefined();
  });
});
