import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { App } from './App';
import { copy } from './copy/es';

describe('App', () => {
  it('renders the application title from the Spanish copy file', () => {
    render(<App />);

    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent(copy.appTitle);
  });

  it('renders no hard-coded text outside the copy file', () => {
    render(<App />);

    expect(screen.getByText(copy.appTagline)).toBeInTheDocument();
  });
});
