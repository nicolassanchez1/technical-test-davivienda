import type { components } from '@technical-test-davivienda/shared';
import type { DocumentErrorCode } from './types';

/**
 * One rejected file of an upload batch, read from the `errors` member of the 422 problem+json
 * body. `index` is the position in the `files` part, which is what lines the error up with the
 * row the form rendered for that file.
 */
export type UploadFileProblem = components['schemas']['UploadFileProblem'];

/** Every failed response reaches the UI as this, so a caller never has to read a status code twice. */
export class ApiError extends Error {
  readonly status: number;
  readonly detail: string | undefined;
  readonly problemType: string | undefined;
  readonly requestId: string | undefined;
  readonly fileErrors: readonly UploadFileProblem[];

  constructor(init: {
    status: number;
    detail?: string;
    problemType?: string;
    requestId?: string;
    fileErrors?: readonly UploadFileProblem[];
  }) {
    super(init.detail ?? `Request failed with status ${init.status}`);
    this.name = 'ApiError';
    this.status = init.status;
    this.detail = init.detail;
    this.problemType = init.problemType;
    this.requestId = init.requestId;
    this.fileErrors = init.fileErrors ?? [];
  }
}

export async function apiErrorFrom(response: Response): Promise<ApiError> {
  const body = await readProblemBody(response);
  if (!body) {
    return new ApiError({ status: response.status });
  }
  return new ApiError({
    status: response.status,
    detail: optionalString(body.detail),
    problemType: optionalString(body.type),
    requestId: optionalString(body.requestId),
    fileErrors: fileProblemsOf(body.errors),
  });
}

async function readProblemBody(response: Response): Promise<Record<string, unknown> | null> {
  const contentType = response.headers.get('content-type') ?? '';
  if (!contentType.includes('json')) {
    return null;
  }
  try {
    const parsed: unknown = await response.json();
    return isRecord(parsed) ? parsed : null;
  } catch {
    // A truncated or non-JSON body still has to surface as the status it came with.
    return null;
  }
}

function fileProblemsOf(value: unknown): readonly UploadFileProblem[] {
  if (!Array.isArray(value)) {
    return [];
  }
  return value.filter(isRecord).flatMap((entry) => {
    if (typeof entry.index !== 'number') {
      return [];
    }
    return [
      {
        index: entry.index,
        filename: optionalString(entry.filename),
        rule: optionalString(entry.rule),
        errorCode: optionalString(entry.errorCode) as DocumentErrorCode | undefined,
        existingDocumentId: optionalString(entry.existingDocumentId),
      },
    ];
  });
}

function optionalString(value: unknown): string | undefined {
  return typeof value === 'string' && value.length > 0 ? value : undefined;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}
