/**
 * Mirrors of the backend limits, so a file that cannot be accepted is refused before it is sent.
 * The API re-validates everything and stays the authority; these only spare the reader a round
 * trip. Overridable at build time for a deployment that raises `APP_MAX_FILE_SIZE_MB` or
 * `APP_MAX_FILES_PER_UPLOAD`.
 */
function positiveNumber(raw: string | undefined, fallback: number): number {
  const parsed = Number.parseInt(raw ?? '', 10);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
}

export const MAX_FILE_SIZE_MB = positiveNumber(import.meta.env.VITE_MAX_FILE_SIZE_MB, 20);
export const MAX_FILES_PER_UPLOAD = positiveNumber(import.meta.env.VITE_MAX_FILES_PER_UPLOAD, 10);
export const MAX_FILE_SIZE_BYTES = MAX_FILE_SIZE_MB * 1024 * 1024;

/** The same allowlist the API enforces, `.markdown` included. */
export const ACCEPTED_EXTENSIONS = ['.txt', '.md', '.markdown', '.pdf'] as const;

/** Keyed by MIME type for the file picker; react-dropzone also matches on the extensions. */
export const ACCEPTED_FILE_TYPES: Readonly<Record<string, readonly string[]>> = {
  'text/plain': ['.txt'],
  'text/markdown': ['.md', '.markdown'],
  'application/pdf': ['.pdf'],
};

export function hasAcceptedExtension(filename: string): boolean {
  const lowercased = filename.toLowerCase();
  return ACCEPTED_EXTENSIONS.some((extension) => lowercased.endsWith(extension));
}

/** Everything before the last dot, which is what prefills the title for an uploaded file. */
export function titleFromFilename(filename: string): string {
  const lastDot = filename.lastIndexOf('.');
  const base = lastDot > 0 ? filename.slice(0, lastDot) : filename;
  return base.replace(/[_-]+/g, ' ').trim();
}
