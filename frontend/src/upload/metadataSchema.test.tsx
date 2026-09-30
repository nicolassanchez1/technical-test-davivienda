import { describe, expect, it } from 'vitest';
import { copy } from '../copy/es';
import { interpolate } from '../copy/interpolate';
import {
  TAGS_MAX_COUNT,
  TAG_MAX_LENGTH,
  TITLE_MAX_LENGTH,
  parseTags,
  validateMetadata,
  type MetadataDraft,
} from './metadataSchema';

const valid: MetadataDraft = {
  title: 'Guia de despliegue',
  author: 'Equipo de plataforma',
  category: 'MANUAL',
  version: '1.0',
  tags: 'infra, despliegue',
};

describe('parseTags', () => {
  it('trims, lowercases and de-duplicates the way the backend canonicalises tags', () => {
    expect(parseTags(' Infra , INFRA,  despliegue ,,')).toEqual(['infra', 'despliegue']);
  });

  it('accepts an empty field as no tags', () => {
    expect(parseTags('   ')).toEqual([]);
  });
});

describe('validateMetadata', () => {
  it('accepts a complete row and hands over the canonical tags', () => {
    const result = validateMetadata(valid);

    expect(result).toEqual({
      ok: true,
      metadata: {
        title: 'Guia de despliegue',
        author: 'Equipo de plataforma',
        category: 'MANUAL',
        version: '1.0',
        tags: ['infra', 'despliegue'],
      },
    });
  });

  it('reports every blank required field at once', () => {
    const result = validateMetadata({ ...valid, title: '   ', author: '', version: '' });

    expect(result.ok).toBe(false);
    expect(result.ok ? undefined : result.errors).toEqual({
      title: copy.upload.validation.titleRequired,
      author: copy.upload.validation.authorRequired,
      version: copy.upload.validation.versionRequired,
    });
  });

  it('mirrors the maximum title length the backend enforces', () => {
    const result = validateMetadata({ ...valid, title: 'a'.repeat(TITLE_MAX_LENGTH + 1) });

    expect(result.ok ? undefined : result.errors.title).toBe(
      interpolate(copy.upload.validation.titleTooLong, { max: TITLE_MAX_LENGTH }),
    );
  });

  it('mirrors the maximum number of tags', () => {
    const tags = Array.from({ length: TAGS_MAX_COUNT + 1 }, (_, index) => `tag${index}`).join(',');

    const result = validateMetadata({ ...valid, tags });

    expect(result.ok ? undefined : result.errors.tags).toBe(
      interpolate(copy.upload.validation.tooManyTags, { max: TAGS_MAX_COUNT }),
    );
  });

  it('mirrors the maximum length of a single tag', () => {
    const result = validateMetadata({ ...valid, tags: 'a'.repeat(TAG_MAX_LENGTH + 1) });

    expect(result.ok ? undefined : result.errors.tags).toBe(
      interpolate(copy.upload.validation.tagTooLong, { max: TAG_MAX_LENGTH }),
    );
  });

  it('accepts a row with no tags at all', () => {
    const result = validateMetadata({ ...valid, tags: '' });

    expect(result.ok && result.metadata.tags).toEqual([]);
  });
});
