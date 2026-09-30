import { Fragment, type ReactNode } from 'react';

/** The sentinels ts_headline is configured with, so no HTML ever crosses the wire. */
export const HIGHLIGHT_START = '⟦';
export const HIGHLIGHT_STOP = '⟧';

/**
 * Renders a highlighted fragment as React nodes. A doubled sentinel is a sentinel the document
 * itself contains and renders as that literal character; an empty run between two sentinels
 * renders nothing rather than an empty mark.
 */
export function HighlightedText({ text }: { readonly text: string }) {
  return (
    <>
      {segmentsOf(text).map((segment, index) => (
        <Fragment key={index}>{renderSegment(segment)}</Fragment>
      ))}
    </>
  );
}

type Segment = { readonly value: string; readonly highlighted: boolean };

function renderSegment(segment: Segment): ReactNode {
  if (segment.value.length === 0) {
    return null;
  }
  return segment.highlighted ? <mark>{segment.value}</mark> : segment.value;
}

export function segmentsOf(text: string): readonly Segment[] {
  const segments: Segment[] = [];
  let buffer = '';
  let highlighted = false;

  const flush = () => {
    if (buffer.length > 0) {
      segments.push({ value: buffer, highlighted });
      buffer = '';
    }
  };

  for (let index = 0; index < text.length; index += 1) {
    const character = text[index];
    if (character !== HIGHLIGHT_START && character !== HIGHLIGHT_STOP) {
      buffer += character;
      continue;
    }
    if (text[index + 1] === character) {
      buffer += character;
      index += 1;
      continue;
    }
    flush();
    highlighted = character === HIGHLIGHT_START;
  }
  flush();

  return segments;
}
