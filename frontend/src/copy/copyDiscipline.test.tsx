import { readFileSync, readdirSync } from 'node:fs';
import { join, relative, sep } from 'node:path';
import { screen } from '@testing-library/react';
import { beforeEach, describe, expect, it } from 'vitest';
import { copy } from './es';
import { App } from '../App';
import { DocumentsPage } from '../documents/DocumentsPage';
import { SearchPage } from '../search/SearchPage';
import { AppLayout } from '../shell/AppLayout';
import { NotFoundPage } from '../shell/NotFoundPage';
import { installFetchMock, jsonResponse } from '../test-support/http';
import { renderWithProviders } from '../test-support/renderWithProviders';
import { UploadPage } from '../upload/UploadPage';

const SOURCE_ROOT = join(process.cwd(), 'src');
const COPY_DIRECTORY = join(SOURCE_ROOT, 'copy');

const SPANISH_ONLY_CHARACTERS = /[áéíóúüñÁÉÍÓÚÜÑ¿¡]/;
const LITERAL_ACCESSIBLE_TEXT = /\b(aria-label|aria-description|placeholder|title|alt)\s*=\s*["']/;

function sourceFiles(directory: string): string[] {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const path = join(directory, entry.name);
    if (entry.isDirectory()) {
      return sourceFiles(path);
    }
    return /\.tsx?$/.test(entry.name) ? [path] : [];
  });
}

const filesOutsideCopy = sourceFiles(SOURCE_ROOT)
  .filter((path) => !path.startsWith(COPY_DIRECTORY + sep))
  .map((path) => relative(SOURCE_ROOT, path));

/** The copy values, plus a pattern per templated value so an interpolated string still matches. */
const copyValues = collectStrings(copy);
const exactCopy = new Set(copyValues);
const templatedCopy = copyValues
  .filter((value) => value.includes('{'))
  .map((value) => new RegExp(`^${escapeRegExp(value).replace(/\\\{\w+\\\}/g, '.*')}$`));

function collectStrings(value: unknown): string[] {
  if (typeof value === 'string') {
    return [value];
  }
  if (typeof value === 'object' && value !== null) {
    return Object.values(value).flatMap(collectStrings);
  }
  return [];
}

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function comesFromCopy(text: string): boolean {
  return exactCopy.has(text) || templatedCopy.some((pattern) => pattern.test(text));
}

function textsNotInCopy(container: HTMLElement): string[] {
  const walker = document.createTreeWalker(container, NodeFilter.SHOW_TEXT);
  const unexpected: string[] = [];
  for (let node = walker.nextNode(); node !== null; node = walker.nextNode()) {
    const text = node.textContent?.trim() ?? '';
    if (text.length > 0 && !comesFromCopy(text)) {
      unexpected.push(text);
    }
  }
  return unexpected;
}

describe('copy discipline', () => {
  beforeEach(() => {
    installFetchMock().mockResolvedValue(
      jsonResponse({ items: [], total: 0, page: 1, pageSize: 10 }),
    );
  });

  it('keeps every Spanish-only character inside the copy file', () => {
    const offenders = filesOutsideCopy.filter((path) =>
      SPANISH_ONLY_CHARACTERS.test(readFileSync(join(SOURCE_ROOT, path), 'utf8')),
    );

    expect(offenders).toEqual([]);
  });

  it('never gives an accessible name as a literal instead of a copy reference', () => {
    const offenders = filesOutsideCopy.filter((path) =>
      LITERAL_ACCESSIBLE_TEXT.test(readFileSync(join(SOURCE_ROOT, path), 'utf8')),
    );

    expect(offenders).toEqual([]);
  });

  it('renders the shell with no text of its own', () => {
    const { container } = renderWithProviders(<AppLayout />);

    expect(textsNotInCopy(container)).toEqual([]);
  });

  it('renders the search page with no text of its own', () => {
    const { container } = renderWithProviders(<SearchPage />);

    expect(textsNotInCopy(container)).toEqual([]);
  });

  it('renders the upload page with no text of its own', () => {
    const { container } = renderWithProviders(<UploadPage />);

    expect(textsNotInCopy(container)).toEqual([]);
  });

  it('renders the documents page with no text of its own', async () => {
    const { container } = renderWithProviders(<DocumentsPage />);
    await screen.findByText(copy.documents.empty);

    expect(textsNotInCopy(container)).toEqual([]);
  });

  it('renders the viewer with no text of its own', async () => {
    // One response per call: the viewer reads the document and then its body, and a Response can
    // only be consumed once.
    installFetchMock().mockImplementation(() =>
      Promise.resolve(jsonResponse({ items: [], total: 0, page: 1, pageSize: 10 })),
    );
    const { container } = renderWithProviders(<App />, {
      route: '/documents/11111111-1111-1111-1111-111111111111',
    });
    await screen.findByText(copy.viewer.body.empty);

    expect(textsNotInCopy(container)).toEqual([]);
  });

  it('renders the not-found page with no text of its own', () => {
    const { container } = renderWithProviders(<NotFoundPage />);

    expect(textsNotInCopy(container)).toEqual([]);
  });
});
