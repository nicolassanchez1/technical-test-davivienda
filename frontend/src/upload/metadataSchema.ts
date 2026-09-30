import { z } from 'zod';
import type { DocumentCategory } from '../api/types';
import { copy } from '../copy/es';
import { interpolate } from '../copy/interpolate';
import { documentCategories } from '../documents/presentation';

/** The same bounds as the Bean Validation constraints on the backend's DocumentMetadata record. */
export const TITLE_MAX_LENGTH = 300;
export const AUTHOR_MAX_LENGTH = 200;
export const VERSION_MAX_LENGTH = 50;
export const TAG_MAX_LENGTH = 50;
export const TAGS_MAX_COUNT = 20;

const TAG_SEPARATOR = ',';

const categoryValues = documentCategories as [DocumentCategory, ...DocumentCategory[]];

const requiredText = (max: number, missing: string, tooLong: string) =>
  z
    .string()
    .trim()
    .min(1, { message: missing })
    .max(max, { message: interpolate(tooLong, { max }) });

export const documentMetadataSchema = z.object({
  title: requiredText(
    TITLE_MAX_LENGTH,
    copy.upload.validation.titleRequired,
    copy.upload.validation.titleTooLong,
  ),
  author: requiredText(
    AUTHOR_MAX_LENGTH,
    copy.upload.validation.authorRequired,
    copy.upload.validation.authorTooLong,
  ),
  category: z.enum(categoryValues, { message: copy.upload.validation.categoryRequired }),
  version: requiredText(
    VERSION_MAX_LENGTH,
    copy.upload.validation.versionRequired,
    copy.upload.validation.versionTooLong,
  ),
  tags: z
    .array(
      z.string().max(TAG_MAX_LENGTH, {
        message: interpolate(copy.upload.validation.tagTooLong, { max: TAG_MAX_LENGTH }),
      }),
    )
    .max(TAGS_MAX_COUNT, {
      message: interpolate(copy.upload.validation.tooManyTags, { max: TAGS_MAX_COUNT }),
    }),
});

export type DocumentMetadataInput = z.infer<typeof documentMetadataSchema>;

export type MetadataDraft = {
  readonly title: string;
  readonly author: string;
  readonly category: DocumentCategory;
  readonly version: string;
  readonly tags: string;
};

export type MetadataFieldErrors = Readonly<Partial<Record<keyof MetadataDraft, string>>>;

export type MetadataValidation =
  | { readonly ok: true; readonly metadata: DocumentMetadataInput }
  | { readonly ok: false; readonly errors: MetadataFieldErrors };

/**
 * Tags are canonicalised the way the backend does it, so what the reader sees in the row is what
 * ends up indexed: trimmed, lowercased and de-duplicated.
 */
export function parseTags(raw: string): string[] {
  const canonical = raw
    .split(TAG_SEPARATOR)
    .map((tag) => tag.trim().toLowerCase())
    .filter((tag) => tag.length > 0);
  return [...new Set(canonical)];
}

export function validateMetadata(draft: MetadataDraft): MetadataValidation {
  const result = documentMetadataSchema.safeParse({
    title: draft.title,
    author: draft.author,
    category: draft.category,
    version: draft.version,
    tags: parseTags(draft.tags),
  });

  if (result.success) {
    return { ok: true, metadata: result.data };
  }

  const errors: Partial<Record<keyof MetadataDraft, string>> = {};
  for (const issue of result.error.issues) {
    const field = issue.path[0];
    if (typeof field === 'string' && isDraftField(field) && errors[field] === undefined) {
      errors[field] = issue.message;
    }
  }
  return { ok: false, errors };
}

const draftFields: readonly (keyof MetadataDraft)[] = [
  'title',
  'author',
  'category',
  'version',
  'tags',
];

function isDraftField(value: string): value is keyof MetadataDraft {
  return (draftFields as readonly string[]).includes(value);
}
