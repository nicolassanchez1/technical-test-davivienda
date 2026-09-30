import { copy } from '../copy/es';

const BYTES_PER_KILOBYTE = 1024;
const BYTES_PER_MEGABYTE = BYTES_PER_KILOBYTE * BYTES_PER_KILOBYTE;

const integer = new Intl.NumberFormat(copy.locale, { maximumFractionDigits: 0 });
const oneDecimal = new Intl.NumberFormat(copy.locale, { maximumFractionDigits: 1 });
const dateTime = new Intl.DateTimeFormat(copy.locale, {
  dateStyle: 'medium',
  timeStyle: 'short',
});

export function formatBytes(bytes: number): string {
  if (bytes >= BYTES_PER_MEGABYTE) {
    return `${oneDecimal.format(bytes / BYTES_PER_MEGABYTE)} ${copy.units.megabytes}`;
  }
  if (bytes >= BYTES_PER_KILOBYTE) {
    return `${integer.format(bytes / BYTES_PER_KILOBYTE)} ${copy.units.kilobytes}`;
  }
  return `${integer.format(bytes)} ${copy.units.bytes}`;
}

export function formatMegabytes(megabytes: number): string {
  return `${integer.format(megabytes)} ${copy.units.megabytes}`;
}

/** Empty for a missing or unparseable timestamp, so a partial row still renders. */
export function formatDateTime(isoTimestamp: string | undefined): string {
  if (!isoTimestamp) {
    return '';
  }
  const parsed = new Date(isoTimestamp);
  return Number.isNaN(parsed.getTime()) ? '' : dateTime.format(parsed);
}
