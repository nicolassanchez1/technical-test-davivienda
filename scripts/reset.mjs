#!/usr/bin/env node
/**
 * Empties the application so a demo can start from nothing: documents and their chunks, the
 * pending jobs, and the stored files. Leaves the schema and the containers alone, so it takes
 * seconds instead of a rebuild.
 *
 * Usage: pnpm reset
 */
import { execFileSync } from 'node:child_process';

const QUEUES = ['documents.process', 'documents.process.dlq'];

function compose(args, { quiet = false } = {}) {
  return execFileSync('docker', ['compose', ...args], {
    encoding: 'utf8',
    stdio: quiet ? ['ignore', 'pipe', 'ignore'] : ['ignore', 'pipe', 'inherit'],
  });
}

function running(service) {
  try {
    return compose(['ps', '--status', 'running', '--format', '{{.Service}}'], { quiet: true })
      .split('\n')
      .includes(service);
  } catch {
    return false;
  }
}

function main() {
  const missing = ['postgres', 'rabbitmq', 'api'].filter((service) => !running(service));
  if (missing.length > 0) {
    console.error(`Faltan servicios: ${missing.join(', ')}. Arranca con: docker compose up -d`);
    process.exitCode = 1;
    return;
  }

  // Chunks go with their document through the foreign key, so one statement empties both.
  compose([
    'exec',
    '-T',
    'postgres',
    'psql',
    '-U',
    process.env.POSTGRES_USER ?? 'documents',
    '-d',
    process.env.POSTGRES_DB ?? 'documents',
    '-q',
    '-c',
    'TRUNCATE documents CASCADE;',
  ]);
  console.log('  ✓ documentos y fragmentos borrados');

  for (const queue of QUEUES) {
    try {
      compose(['exec', '-T', 'rabbitmq', 'rabbitmqctl', 'purge_queue', queue], { quiet: true });
      console.log(`  ✓ cola ${queue} vaciada`);
    } catch {
      // A queue only exists once something has been published to it.
      console.log(`  · cola ${queue} no existía`);
    }
  }

  // The API and the worker share this volume; orphaned files would otherwise pile up.
  compose(['exec', '-T', 'api', 'sh', '-c', 'rm -rf /app/storage/* 2>/dev/null || true'], {
    quiet: true,
  });
  console.log('  ✓ archivos almacenados eliminados');

  console.log('\nTodo vacío. Carga los documentos de ejemplo con: pnpm seed');
}

main();
