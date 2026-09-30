import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { HighlightedText } from './HighlightedText';
import { containsTerm, markTerms, termsOf } from './queryTerms';

// Accented text is written with escapes on purpose: the copy-discipline test keeps every Spanish
// character inside the copy file, and this fixture is a document's words, not the interface's.
const ACCENTED_WORD = '\u00cdndice';
const ACCENTED_TITLE = `${ACCENTED_WORD} invertido`;

describe('termsOf', () => {
  it('keeps a quoted phrase as one term', () => {
    expect(termsOf('"indice invertido" postgres')).toEqual(['indice invertido', 'postgres']);
  });

  it('drops an excluded word and the alternation operator', () => {
    expect(termsOf('despliegue or rollback -borrador')).toEqual(['despliegue', 'rollback']);
  });

  it('drops the punctuation around a word and the words too short to be useful', () => {
    expect(termsOf('(postgres), y tsvector.')).toEqual(['postgres', 'tsvector']);
  });
});

describe('markTerms', () => {
  it('wraps a match in the sentinels the renderer splits on', () => {
    expect(markTerms('El indice del manual', ['indice'])).toBe('El ⟦indice⟧ del manual');
  });

  it('matches the way the engine does, ignoring accents and case', () => {
    render(<HighlightedText text={markTerms(ACCENTED_TITLE, ['indice'])} />);

    expect(screen.getByText(ACCENTED_WORD).tagName).toBe('MARK');
  });

  it('marks the rest of the word, so a term matches the form the document uses', () => {
    expect(markTerms('los indices del manual', ['indice'])).toBe('los ⟦indices⟧ del manual');
  });

  it('never lights up the middle of a word', () => {
    expect(markTerms('el indice', ['dice'])).toBe('el indice');
    expect(containsTerm('el indice', ['dice'])).toBe(false);
  });

  it('doubles a sentinel the document itself contains, so it renders as that character', () => {
    render(<HighlightedText text={markTerms('a ⟦b⟧ indice', ['indice'])} />);

    expect(screen.getByText(/a/)).toHaveTextContent('a ⟦b⟧ indice');
    expect(screen.getByText('indice').tagName).toBe('MARK');
  });

  it('leaves the text alone when the query carries no term to look for', () => {
    expect(markTerms('sin resaltado', termsOf('  '))).toBe('sin resaltado');
  });
});
