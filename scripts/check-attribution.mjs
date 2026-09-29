#!/usr/bin/env node
/**
 * Rejects AI attribution in commit metadata. The repository is delivered as the sole
 * work of its author, so generated co-author trailers, tool signatures and assistant
 * session links must never reach history.
 *
 * Usage:
 *   node scripts/check-attribution.mjs --message-file <path>   (commit-msg hook)
 *   node scripts/check-attribution.mjs --range <base>..<head>  (CI over a PR range)
 */
import { readFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';

const FORBIDDEN_PATTERNS = [
  { name: 'assistant co-author trailer', regex: /^\s*co-authored-by:.*(claude|anthropic)/im },
  { name: 'tool signature', regex: /generated\s+with\s+\[?\s*claude\s+code/i },
  { name: 'session trailer', regex: /^\s*claude-session\s*:/im },
  { name: 'assistant session link', regex: /https?:\/\/(?:[\w-]+\.)*claude\.ai\//i },
  { name: 'assistant identity', regex: /noreply@anthropic\.com|claude\[bot\]/i },
];

function findViolations(text) {
  return FORBIDDEN_PATTERNS.filter((pattern) => pattern.regex.test(text)).map(
    (pattern) => pattern.name,
  );
}

function git(args) {
  return execFileSync('git', args, { encoding: 'utf8', maxBuffer: 32 * 1024 * 1024 });
}

function readCommitsInRange(range) {
  const shas = git(['rev-list', '--no-merges', range]).split('\n').filter(Boolean);

  return shas.map((sha) => {
    const metadata = git(['show', '--no-patch', '--format=%an%n%ae%n%cn%n%ce%n%B', sha]);
    const subject = git(['show', '--no-patch', '--format=%s', sha]).trim();
    return { label: `${sha.slice(0, 8)} ${subject}`, text: metadata };
  });
}

function parseArguments(argv) {
  const [flag, value] = argv;
  if (flag === '--message-file' && value) return { mode: 'message-file', value };
  if (flag === '--range' && value) return { mode: 'range', value };
  return null;
}

function main() {
  const options = parseArguments(process.argv.slice(2));
  if (!options) {
    console.error('Usage: check-attribution.mjs --message-file <path> | --range <base>..<head>');
    process.exit(2);
  }

  const subjects =
    options.mode === 'message-file'
      ? [{ label: options.value, text: readFileSync(options.value, 'utf8') }]
      : readCommitsInRange(options.value);

  const failures = subjects
    .map((subject) => ({ label: subject.label, violations: findViolations(subject.text) }))
    .filter((result) => result.violations.length > 0);

  if (failures.length === 0) {
    console.log(`Attribution check passed (${subjects.length} commit message(s)).`);
    return;
  }

  console.error('Attribution check failed. Remove the following from commit metadata:');
  for (const failure of failures) {
    console.error(`  ${failure.label}: ${failure.violations.join(', ')}`);
  }
  process.exit(1);
}

main();
