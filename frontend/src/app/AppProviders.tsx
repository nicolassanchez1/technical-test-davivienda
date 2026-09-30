import { useState, type ReactNode } from 'react';
import { QueryClientProvider } from '@tanstack/react-query';
import { Toaster } from 'sonner';
import { DocumentStatusProvider } from '../events/DocumentStatusProvider';
import { createQueryClient } from './queryClient';

export function AppProviders({ children }: { readonly children: ReactNode }) {
  const [queryClient] = useState(createQueryClient);

  return (
    <QueryClientProvider client={queryClient}>
      <DocumentStatusProvider>
        {children}
        <Toaster position="top-right" richColors closeButton />
      </DocumentStatusProvider>
    </QueryClientProvider>
  );
}
