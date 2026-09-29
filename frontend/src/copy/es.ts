/**
 * Every user-facing string lives here. Components reference keys so the Spanish copy the
 * evaluators read can change without touching component code.
 */
export const copy = {
  appTitle: 'Buscador y Visor de Documentos Técnicos',
  appTagline: 'Carga, indexa y busca tu documentación técnica.',
} as const;

export type CopyKey = keyof typeof copy;
