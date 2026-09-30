import { ApiError } from '../api/problem';
import { copy } from '../copy/es';
import { describeError } from '../shared/errors';

/** The problem types the search endpoint answers with, which is what tells the two 400s apart. */
const INVALID_QUERY_PROBLEM = 'urn:problem-type:invalid-search-query';
const TIMEOUT_PROBLEM = 'urn:problem-type:search-timeout';

/**
 * A failed search is worth its own wording: a query with nothing to look for and a search that ran
 * out of time are both things the reader can fix, and a generic failure tells them neither.
 */
export function describeSearchError(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return describeError(error);
  }
  if (error.status === 503 || error.problemType === TIMEOUT_PROBLEM) {
    return copy.search.errors.timeout;
  }
  if (error.problemType === INVALID_QUERY_PROBLEM) {
    return copy.search.errors.noSearchableTerm;
  }
  if (error.status === 400) {
    return copy.search.errors.pageOutOfRange;
  }
  return describeError(error);
}
