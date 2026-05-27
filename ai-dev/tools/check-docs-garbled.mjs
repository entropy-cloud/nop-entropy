#!/usr/bin/env node

/**
 * check-docs-garbled.mjs
 *
 * Detect garbled/corrupted characters in documentation files.
 * Scans docs-for-ai/ and ai-dev/ for suspicious Unicode patterns.
 *
 * Usage:
 *   node check-docs-garbled.mjs
 *
 * Adapted from nop-chaos-flux/scripts/check-docs-garbled.mjs
 */

import { execFile } from 'node:child_process';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { promisify } from 'node:util';

const execFileAsync = promisify(execFile);

const PROJECT_ROOT = new URL('../..', import.meta.url).pathname;
const OUTPUT_DIR = new URL('../_tmp/docs-garbled-check', import.meta.url).pathname;
const SCAN_DIRS = ['docs-for-ai', 'ai-dev'];

const textExtensions = new Set([
  '.md', '.mdx', '.txt', '.json', '.yaml', '.yml', '.html', '.xml',
]);

const suspiciousSingles = new Map([
  ['\uFFFD', 'replacement-char'],
  ['\uFEFF', 'bom'],
  ['\u200B', 'zero-width-space'],
  ['\u200C', 'zero-width-non-joiner'],
  ['\u200D', 'zero-width-joiner'],
  ['\u2060', 'word-joiner'],
]);

const mojibakePattern = /(?:Ã.|Â.|â€¦|â€"|â€"|â€|ðŸ|Ð.|Ñ.|æ.|ç.|ä.|å.)/u;
const controlPattern = /^\p{Control}$/u;
const formatPattern = /^\p{Format}$/u;
const privateUsePattern = /^\p{Private_Use}$/u;
const noncharacterPattern = /^\p{Noncharacter_Code_Point}$/u;
const letterPattern = /^\p{Letter}$/u;
const numberPattern = /^\p{Number}$/u;
const punctuationPattern = /^\p{Punctuation}$/u;
const symbolPattern = /^\p{Symbol}$/u;
const separatorPattern = /^\p{Separator}$/u;
const markPattern = /^\p{Mark}$/u;
const hanPattern = /^\p{Script=Han}$/u;
const asciiLetterPattern = /^[A-Za-z]$/u;

function getLineColumn(content, index) {
  let line = 1, column = 1;
  for (let i = 0; i < index; i++) {
    if (content[i] === '\n') { line++; column = 1; } else { column++; }
  }
  return { line, column };
}

function getLineText(content, lineNumber) {
  return content.split(/\r?\n/u)[lineNumber - 1] ?? '';
}

function getContextSnippet(lineText, column) {
  return lineText.slice(Math.max(0, column - 21), Math.min(lineText.length, column + 20));
}

function getCodePointLabel(character) {
  return `U+${character.codePointAt(0).toString(16).toUpperCase().padStart(4, '0')}`;
}

function isAllowedCharacter(character, index) {
  if (character === '\n' || character === '\r' || character === '\t') return true;
  if (character >= ' ' && character <= '~') return true;
  if (hanPattern.test(character)) return true;
  if (index === 0 && character === '\uFEFF') return true;
  if (numberPattern.test(character) || punctuationPattern.test(character) ||
      symbolPattern.test(character) || separatorPattern.test(character) || markPattern.test(character)) return true;
  return false;
}

function classifyCharacter(character, index) {
  if (isAllowedCharacter(character, index)) return null;
  if (suspiciousSingles.has(character)) return suspiciousSingles.get(character);
  if (controlPattern.test(character)) return 'control-char';
  if (formatPattern.test(character)) return 'format-char';
  if (privateUsePattern.test(character)) return 'private-use-char';
  if (noncharacterPattern.test(character)) return 'noncharacter';
  if (letterPattern.test(character)) {
    if (asciiLetterPattern.test(character) || hanPattern.test(character)) return null;
    return 'unexpected-letter';
  }
  return 'unexpected-char';
}

function scoreOccurrence(type, context) {
  if (['replacement-char', 'control-char', 'private-use-char', 'noncharacter'].includes(type)) return 5;
  if (['zero-width-space', 'zero-width-non-joiner', 'zero-width-joiner'].includes(type)) return 4;
  if (['format-char', 'word-joiner', 'bom'].includes(type)) return 3;
  if (type === 'unexpected-letter') return mojibakePattern.test(context) ? 4 : 2;
  return 2;
}

function buildVerdict(occurrences) {
  let score = 0;
  let hasHighConfidence = false;
  for (const occ of occurrences) {
    score += scoreOccurrence(occ.type, occ.context);
    if (['replacement-char', 'control-char', 'private-use-char', 'noncharacter'].includes(occ.type) ||
        mojibakePattern.test(occ.context)) hasHighConfidence = true;
  }
  return hasHighConfidence || score >= 6
    ? { status: 'likely-garbled', score }
    : { status: 'needs-review', score };
}

async function getDocFiles() {
  const dirArgs = SCAN_DIRS.flatMap(d => [d]);
  try {
    const { stdout } = await execFileAsync('git', ['ls-files', ...dirArgs], {
      cwd: PROJECT_ROOT, maxBuffer: 20 * 1024 * 1024,
    });
    return stdout.split(/\r?\n/u).filter(Boolean).filter(
      f => textExtensions.has(f.split('.').pop()?.toLowerCase() ? `.${f.split('.').pop()}` : '')
    );
  } catch { return []; }
}

async function scanFile(relativePath) {
  const content = await readFile(new URL(`../../${relativePath}`, import.meta.url), 'utf8');
  const occurrences = [];
  for (let index = 0; index < content.length;) {
    const codePoint = content.codePointAt(index);
    const character = String.fromCodePoint(codePoint);
    const width = codePoint > 0xffff ? 2 : 1;
    const type = classifyCharacter(character, index);
    if (type) {
      const { line, column } = getLineColumn(content, index);
      const lineText = getLineText(content, line);
      occurrences.push({ type, character, codePoint: getCodePointLabel(character), line, column, context: getContextSnippet(lineText, column) });
    }
    index += width;
  }
  return { relativePath, occurrences };
}

async function main() {
  await mkdir(OUTPUT_DIR, { recursive: true });
  const files = await getDocFiles();
  const scans = await Promise.all(files.map(f => scanFile(f)));
  const candidates = scans.filter(s => s.occurrences.length > 0).map(item => ({
    file: item.relativePath,
    occurrenceCount: item.occurrences.length,
    occurrenceTypes: [...new Set(item.occurrences.map(o => o.type))],
    occurrences: item.occurrences,
  })).sort((a, b) => a.file.localeCompare(b.file));

  const verdicts = candidates.map(c => {
    const v = buildVerdict(c.occurrences);
    return { file: c.file, status: v.status, score: v.score, occurrenceCount: c.occurrenceCount, occurrenceTypes: c.occurrenceTypes, examples: c.occurrences.slice(0, 20) };
  });

  await writeFile(require('node:path').join(OUTPUT_DIR, 'verdicts.json'), JSON.stringify(verdicts, null, 2) + '\n');

  const likelyCount = verdicts.filter(v => v.status === 'likely-garbled').length;
  const reviewCount = verdicts.filter(v => v.status === 'needs-review').length;
  console.log(`[check-docs-garbled] Scanned ${files.length} docs files`);
  console.log(`[check-docs-garbled] Candidates: ${candidates.length}, Likely garbled: ${likelyCount}, Needs review: ${reviewCount}`);

  if (verdicts.length > 0) {
    console.log(`[check-docs-garbled] Report: ${OUTPUT_DIR}/verdicts.json`);
  }
  process.exit(likelyCount > 0 ? 1 : 0);
}

main();
