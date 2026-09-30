import type { ReactNode } from 'react';
import { QueryClientProvider, type QueryClient } from '@tanstack/react-query';
import { render, type RenderResult } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { createQueryClient } from '../app/queryClient';
import { DocumentStatusProvider } from '../events/DocumentStatusProvider';

export type ProviderOptions = {
  readonly route?: string;
  readonly queryClient?: QueryClient;
};

export function renderWithProviders(
  ui: ReactNode,
  options: ProviderOptions = {},
): RenderResult & { readonly queryClient: QueryClient } {
  const queryClient = options.queryClient ?? createQueryClient();
  const result = render(
    <MemoryRouter initialEntries={[options.route ?? '/']}>
      <QueryClientProvider client={queryClient}>
        <DocumentStatusProvider>{ui}</DocumentStatusProvider>
      </QueryClientProvider>
    </MemoryRouter>,
  );
  return { ...result, queryClient };
}
