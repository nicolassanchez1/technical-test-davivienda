import { ErrorCode, type FileError } from 'react-dropzone';
import { copy } from '../copy/es';
import { interpolate } from '../copy/interpolate';
import { formatMegabytes } from '../shared/format';
import { MAX_FILES_PER_UPLOAD, MAX_FILE_SIZE_MB } from './limits';

export type RejectedFile = {
  readonly key: string;
  readonly filename: string;
  readonly reason: string;
};

export const TOO_MANY_FILES_REASON = interpolate(copy.upload.rejections.tooMany, {
  maxFiles: MAX_FILES_PER_UPLOAD,
});

const reasonByCode: Readonly<Record<string, string>> = {
  [ErrorCode.FileInvalidType]: copy.upload.rejections.extension,
  [ErrorCode.FileTooLarge]: interpolate(copy.upload.rejections.tooLarge, {
    maxSize: formatMegabytes(MAX_FILE_SIZE_MB),
  }),
  // The dropzone is configured with a minimum of one byte, so this is only ever an empty file.
  [ErrorCode.FileTooSmall]: copy.upload.rejections.empty,
  [ErrorCode.TooManyFiles]: TOO_MANY_FILES_REASON,
};

export function rejectionReason(errors: readonly FileError[]): string {
  for (const error of errors) {
    const reason = reasonByCode[error.code];
    if (reason !== undefined) {
      return reason;
    }
  }
  return copy.upload.rejections.unknown;
}
