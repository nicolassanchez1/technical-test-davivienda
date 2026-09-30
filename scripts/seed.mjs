#!/usr/bin/env node
/**
 * Uploads the sample documents through the API, so a fresh stack has something to search.
 *
 * Usage: pnpm seed [--api http://localhost:8081]
 */
import { readFile } from 'node:fs/promises';
import { basename, join } from 'node:path';

const SAMPLES_DIRECTORY = 'samples';

/** Metadata per sample, chosen so the search demo has something to filter and rank by. */
const SAMPLES = [
  {
    file: 'guia-arquitectura-buscador.md',
    title: 'Guía de arquitectura del buscador',
    author: 'Equipo de plataforma',
    category: 'ARCHITECTURE_GUIDE',
    tags: ['postgresql', 'busqueda', 'indices'],
    version: '2.1',
  },
  {
    file: 'manual-operacion-cluster.md',
    title: 'Manual de operación del clúster',
    author: 'Área de infraestructura',
    category: 'MANUAL',
    tags: ['operacion', 'respaldo'],
    version: '1.4',
  },
  {
    file: 'especificacion-carga-documentos.md',
    title: 'Especificación de carga de documentos',
    author: 'Equipo de plataforma',
    category: 'SPECIFICATION',
    tags: ['carga', 'validacion'],
    version: '3.0',
  },
  {
    file: 'notas-despliegue.txt',
    title: 'Notas de despliegue',
    author: 'Área de infraestructura',
    category: 'OTHER',
    tags: ['despliegue', 'nginx'],
    version: '1.0',
  },
  {
    file: 'politica-retencion-latin1.txt',
    title: 'Política de retención de documentos',
    author: 'Comité de arquitectura',
    category: 'SPECIFICATION',
    tags: ['retencion', 'gobierno'],
    version: '1.2',
  },
  {
    file: 'informe-rendimiento.pdf',
    title: 'Informe técnico de rendimiento',
    author: 'Equipo de plataforma',
    category: 'SPECIFICATION',
    tags: ['rendimiento', 'postgresql'],
    version: '1.1',
  },
  {
    file: 'plano-escaneado-sin-texto.pdf',
    title: 'Plano escaneado sin capa de texto',
    author: 'Área de infraestructura',
    category: 'OTHER',
    tags: ['plano'],
    version: '1.0',
  },
];

const MIME_TYPES = { md: 'text/markdown', txt: 'text/plain', pdf: 'application/pdf' };

function apiBaseUrl() {
  const flag = process.argv.indexOf('--api');
  return flag === -1
    ? (process.env.SEED_API_URL ?? 'http://localhost:8081')
    : process.argv[flag + 1];
}

async function upload(baseUrl, sample) {
  const path = join(SAMPLES_DIRECTORY, sample.file);
  const content = await readFile(path);
  const extension = sample.file.split('.').pop();

  const body = new FormData();
  body.append('files', new Blob([content], { type: MIME_TYPES[extension] }), basename(path));
  const { file: _file, ...metadata } = sample;
  body.append('metadata', new Blob([JSON.stringify([metadata])], { type: 'application/json' }));

  const response = await fetch(`${baseUrl}/api/documents`, { method: 'POST', body });
  const text = await response.text();
  return { ok: response.ok, status: response.status, body: text };
}

async function main() {
  const baseUrl = apiBaseUrl();

  try {
    const health = await fetch(`${baseUrl}/api/health`);
    if (!health.ok) throw new Error(`health responded ${health.status}`);
  } catch {
    console.error(`No API at ${baseUrl}. Start it with: docker compose up -d`);
    process.exitCode = 1;
    return;
  }

  let accepted = 0;
  for (const sample of SAMPLES) {
    const result = await upload(baseUrl, sample);
    if (result.ok) {
      accepted += 1;
      console.log(`  ✓ ${sample.file}`);
    } else if (result.status === 422 && result.body.includes('UNIQUE_CHECKSUM')) {
      console.log(`  · ${sample.file} (ya estaba cargado)`);
    } else {
      console.error(`  ✗ ${sample.file} → ${result.status} ${result.body.slice(0, 160)}`);
      process.exitCode = 1;
    }
  }

  console.log(
    `\n${accepted} documento(s) aceptado(s). El worker los indexa en segundo plano; ` +
      `"plano-escaneado-sin-texto.pdf" debe terminar en ERROR, porque no tiene capa de texto.`,
  );
}

await main();
