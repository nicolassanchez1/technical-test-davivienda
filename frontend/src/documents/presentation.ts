import { copy } from '../copy/es';
import type { DocumentCategory, DocumentErrorCode, DocumentStatus } from '../api/types';

/**
 * Typing the copy maps against the contract unions is what makes a status, category or error code
 * the backend can send but the UI has no wording for a compile error instead of a blank cell.
 */
const statusLabels: Record<DocumentStatus, string> = copy.statuses;
const statusDescriptions: Record<DocumentStatus, string> = copy.statusDescriptions;
const categoryLabels: Record<DocumentCategory, string> = copy.categories;
const errorCodeReasons: Record<DocumentErrorCode, string> = copy.errorCodes;
const uploadRuleReasons: Readonly<Record<string, string>> = copy.uploadRules;

/** The one cast the compiler cannot avoid, kept to a single line whose contract is obvious. */
function keysOf<T extends string>(record: Record<T, unknown>): readonly T[] {
  return Object.keys(record) as T[];
}

export const documentStatuses = keysOf(statusLabels);
export const documentCategories = keysOf(categoryLabels);

export function isDocumentStatus(value: unknown): value is DocumentStatus {
  return typeof value === 'string' && documentStatuses.includes(value as DocumentStatus);
}

export function isDocumentCategory(value: unknown): value is DocumentCategory {
  return typeof value === 'string' && documentCategories.includes(value as DocumentCategory);
}

export function statusLabel(status: DocumentStatus): string {
  return statusLabels[status];
}

export function statusDescription(status: DocumentStatus): string {
  return statusDescriptions[status];
}

export function categoryLabel(category: DocumentCategory): string {
  return categoryLabels[category];
}

export function errorCodeReason(errorCode: DocumentErrorCode | undefined): string {
  return errorCode ? errorCodeReasons[errorCode] : copy.unknownErrorCode;
}

export function uploadRuleReason(rule: string | undefined): string {
  return (rule && uploadRuleReasons[rule]) ?? copy.unknownUploadRule;
}
