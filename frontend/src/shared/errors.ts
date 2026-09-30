import { ApiError } from '../api/problem';
import { copy } from '../copy/es';

/**
 * Turns any failure into Spanish copy. The API answers in English by contract, so its `detail` is
 * never shown: the status is mapped to wording the reader of this UI can act on.
 */
export function describeError(error: unknown): string {
  if (error instanceof ApiError) {
    return describeStatus(error.status);
  }
  // A rejected fetch, i.e. the request never reached the server.
  if (error instanceof TypeError) {
    return copy.states.networkError;
  }
  return copy.states.unknownError;
}

function describeStatus(status: number): string {
  switch (status) {
    case 400:
    case 415:
    case 422:
      return copy.states.invalidRequest;
    case 404:
      return copy.states.notFound;
    case 413:
      return copy.states.payloadTooLarge;
    case 503:
      return copy.states.serviceUnavailable;
    default:
      return copy.states.unknownError;
  }
}
