/// <reference types="vitest/config" />
import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

const API_TARGET = process.env.VITE_API_TARGET ?? 'http://localhost:8081';

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    proxy: {
      // The API owns the /api prefix, so the SPA and the API share one origin in dev.
      '/api': { target: API_TARGET, changeOrigin: true },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test-setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
    coverage: {
      provider: 'v8',
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/**/*.test.{ts,tsx}', 'src/main.tsx', 'src/test-support/**'],
    },
  },
});
