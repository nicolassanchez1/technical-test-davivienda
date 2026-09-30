// Declares only the build-time settings this SPA reads, so a typo in one is a compile error
// rather than a silent fallback.
interface ImportMetaEnv {
  readonly VITE_MAX_FILE_SIZE_MB?: string;
  readonly VITE_MAX_FILES_PER_UPLOAD?: string;
}
