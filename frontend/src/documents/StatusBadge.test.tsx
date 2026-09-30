import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { copy } from '../copy/es';
import { StatusBadge } from './StatusBadge';

describe('StatusBadge', () => {
  it('renders the processing status with its Spanish label', () => {
    render(<StatusBadge status="PROCESANDO" />);

    expect(screen.getByRole('status')).toHaveTextContent(copy.statuses.PROCESANDO);
  });

  it('renders the indexed status with its Spanish label', () => {
    render(<StatusBadge status="INDEXADO" />);

    expect(screen.getByRole('status')).toHaveTextContent(copy.statuses.INDEXADO);
  });

  it('renders the failed status with the Spanish reason for its error code', () => {
    render(<StatusBadge status="ERROR" errorCode="PDF_NO_TEXT_LAYER" />);

    const badge = screen.getByRole('status');
    expect(badge).toHaveTextContent(copy.statuses.ERROR);
    expect(badge).toHaveTextContent(copy.errorCodes.PDF_NO_TEXT_LAYER);
  });

  it('falls back to generic wording when a failure carries no error code', () => {
    render(<StatusBadge status="ERROR" />);

    expect(screen.getByRole('status')).toHaveTextContent(copy.unknownErrorCode);
  });

  it('does not show a reason for a healthy document', () => {
    render(<StatusBadge status="INDEXADO" />);

    expect(screen.getByRole('status')).not.toHaveTextContent(copy.unknownErrorCode);
  });
});
