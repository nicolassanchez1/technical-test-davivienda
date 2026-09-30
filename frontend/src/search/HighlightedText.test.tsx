import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { HighlightedText, segmentsOf } from './HighlightedText';

describe('HighlightedText', () => {
  it('wraps every run between the sentinels in a mark node', () => {
    render(
      <p>
        <HighlightedText text="Guia de ⟦despliegue⟧ en ⟦produccion⟧" />
      </p>,
    );

    const marks = screen.getAllByText(/despliegue|produccion/);
    expect(marks.map((mark) => mark.tagName)).toEqual(['MARK', 'MARK']);
    expect(screen.getByText(/Guia de/)).toHaveTextContent('Guia de despliegue en produccion');
  });

  it('renders text without sentinels as a single plain segment', () => {
    expect(segmentsOf('sin resaltado')).toEqual([{ value: 'sin resaltado', highlighted: false }]);
  });

  it('renders a doubled sentinel as the literal character the document contains', () => {
    expect(segmentsOf('a ⟦⟦b⟧⟧ c')).toEqual([{ value: 'a ⟦b⟧ c', highlighted: false }]);
  });

  it('treats an empty run between sentinels as empty output rather than a broken match', () => {
    expect(segmentsOf('antes ⟦⟧ despues')).toEqual([
      { value: 'antes ', highlighted: false },
      { value: ' despues', highlighted: false },
    ]);

    const { container } = render(<HighlightedText text="antes ⟦⟧ despues" />);
    expect(container.querySelectorAll('mark')).toHaveLength(0);
  });

  it('closes an unterminated highlight at the end of the fragment', () => {
    render(<HighlightedText text="fragmento ⟦cortado" />);

    expect(screen.getByText('cortado').tagName).toBe('MARK');
  });
});
