import js from '@eslint/js';
import globals from 'globals';
import prettier from 'eslint-config-prettier';
import reactHooks from 'eslint-plugin-react-hooks';
import tseslint from 'typescript-eslint';

export default tseslint.config(
  {
    ignores: [
      '**/dist/**',
      '**/coverage/**',
      '**/node_modules/**',
      '**/target/**',
      'packages/shared/src/api.d.ts',
    ],
  },
  js.configs.recommended,
  tseslint.configs.recommended,
  {
    rules: {
      eqeqeq: ['error', 'always'],
      'no-console': ['error', { allow: ['warn', 'error'] }],
      '@typescript-eslint/no-unused-vars': [
        'error',
        { argsIgnorePattern: '^_', varsIgnorePattern: '^_' },
      ],
    },
  },
  {
    files: ['**/*.ts', '**/*.tsx'],
    languageOptions: {
      parserOptions: {
        projectService: true,
        tsconfigRootDir: import.meta.dirname,
      },
    },
    rules: {
      // Type-aware rules worth their cost: an unawaited promise silently drops work in the
      // upload pipeline, the worker and the SSE fan-out.
      '@typescript-eslint/no-floating-promises': 'error',
      '@typescript-eslint/no-misused-promises': 'error',
      '@typescript-eslint/await-thenable': 'error',
      '@typescript-eslint/consistent-type-imports': 'error',
    },
  },
  {
    files: ['frontend/src/**/*.{ts,tsx}'],
    extends: [reactHooks.configs.flat['recommended-latest']],
    languageOptions: {
      globals: globals.browser,
    },
  },
  {
    files: ['**/*.{mjs,cjs,js}'],
    languageOptions: {
      globals: globals.node,
    },
    rules: {
      'no-console': 'off',
    },
  },
  prettier,
);
