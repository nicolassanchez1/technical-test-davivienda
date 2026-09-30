import { HIGHLIGHT_START, HIGHLIGHT_STOP } from './HighlightedText';

/**
 * Client-side highlighting for the viewer. The search endpoint highlights the fragments it
 * returns, but the body arrives as plain chunks, so the terms of the query have to be found here.
 *
 * It mirrors the engine where it matters and stops short of it where it cannot follow: matching
 * ignores accents and case exactly as `es_unaccent` does, and a match has to start a word so a
 * two-letter term does not light up the middle of every word. It does not stem, so a highlight is
 * a good approximation of the match, never a claim about what the index actually matched.
 */

const TERM = /"([^"]*)"|(\S+)/g;
const EDGE_PUNCTUATION = /^[^\p{L}\p{N}]+|[^\p{L}\p{N}]+$/gu;
const DIACRITIC = /\p{Diacritic}/gu;
const WORD_CHARACTER = /[\p{L}\p{N}]/u;

/** A single letter highlights half the page without telling the reader anything. */
const MINIMUM_TERM_LENGTH = 2;

/** `websearch_to_tsquery` reads this as the alternation operator, not as a word to look for. */
const ALTERNATION_OPERATOR = 'or';

export type HighlightRange = { readonly start: number; readonly end: number };

/** The words and quoted phrases worth highlighting; an excluded `-word` is not one of them. */
export function termsOf(query: string): readonly string[] {
  const terms = new Set<string>();

  for (const match of query.matchAll(TERM)) {
    const phrase = match[1];
    const word = match[2] ?? '';
    if (phrase === undefined && word.startsWith('-')) {
      continue;
    }
    const term = (phrase ?? word).replace(EDGE_PUNCTUATION, '').trim();
    if (term.length < MINIMUM_TERM_LENGTH || term.toLowerCase() === ALTERNATION_OPERATOR) {
      continue;
    }
    terms.add(term);
  }

  return [...terms];
}

export function containsTerm(text: string, terms: readonly string[]): boolean {
  return matchRanges(text, terms).length > 0;
}

/** Every stretch of {@link text} a term covers, in order and never overlapping. */
export function matchRanges(text: string, terms: readonly string[]): readonly HighlightRange[] {
  if (terms.length === 0 || text.length === 0) {
    return [];
  }

  const haystack = fold(text);
  const found: HighlightRange[] = [];

  for (const term of terms) {
    const needle = fold(term).text;
    if (needle.length === 0) {
      continue;
    }
    for (
      let at = haystack.text.indexOf(needle);
      at >= 0;
      at = haystack.text.indexOf(needle, at + needle.length)
    ) {
      if (startsAWord(haystack.text, at)) {
        found.push(rangeOf(haystack, at, endOfWord(haystack.text, at + needle.length)));
      }
    }
  }

  return merged(found);
}

/**
 * The text with every match wrapped in the same sentinels the engine uses, so one renderer draws
 * both a server-highlighted snippet and a client-highlighted chunk. A sentinel the document itself
 * contains is doubled, which is what `ts_headline` does to text that was already wrapped.
 */
export function markTerms(text: string, terms: readonly string[]): string {
  const ranges = matchRanges(text, terms);
  let marked = '';
  let cursor = 0;

  for (const range of ranges) {
    marked += escapeSentinels(text.slice(cursor, range.start));
    marked +=
      HIGHLIGHT_START + escapeSentinels(text.slice(range.start, range.end)) + HIGHLIGHT_STOP;
    cursor = range.end;
  }

  return marked + escapeSentinels(text.slice(cursor));
}

function escapeSentinels(text: string): string {
  return text
    .split(HIGHLIGHT_START)
    .join(HIGHLIGHT_START.repeat(2))
    .split(HIGHLIGHT_STOP)
    .join(HIGHLIGHT_STOP.repeat(2));
}

type FoldedText = {
  /** Lowercased and stripped of accents, so a word written with one is the same as without. */
  readonly text: string;
  /** Where each folded character started in the original, plus its end, so offsets map back. */
  readonly origins: readonly number[];
};

function fold(source: string): FoldedText {
  let text = '';
  const origins: number[] = [];
  let origin = 0;

  for (const character of source) {
    const folded = character.normalize('NFD').replace(DIACRITIC, '').toLowerCase();
    for (let position = 0; position < folded.length; position += 1) {
      origins.push(origin);
    }
    text += folded;
    origin += character.length;
  }
  origins.push(origin);

  return { text, origins };
}

function rangeOf(folded: FoldedText, start: number, end: number): HighlightRange {
  return {
    start: folded.origins[start] ?? 0,
    end: folded.origins[end] ?? folded.origins[folded.origins.length - 1] ?? 0,
  };
}

function startsAWord(text: string, at: number): boolean {
  return at === 0 || !WORD_CHARACTER.test(text[at - 1] ?? '');
}

/** The rest of the word is marked too, so searching `indice` lights up `indices` whole. */
function endOfWord(text: string, from: number): number {
  let end = from;
  while (end < text.length && WORD_CHARACTER.test(text[end] ?? '')) {
    end += 1;
  }
  return end;
}

function merged(ranges: readonly HighlightRange[]): readonly HighlightRange[] {
  const ordered = [...ranges].sort((left, right) => left.start - right.start);
  const result: HighlightRange[] = [];

  for (const range of ordered) {
    const previous = result[result.length - 1];
    if (previous && range.start <= previous.end) {
      result[result.length - 1] = { start: previous.start, end: Math.max(previous.end, range.end) };
      continue;
    }
    result.push(range);
  }

  return result;
}
