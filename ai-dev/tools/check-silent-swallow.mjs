#!/usr/bin/env node

/**
 * check-silent-swallow.mjs
 *
 * Invariant INV-SILENT-SWALLOW guard (plan 2026-08-13-1930-2, Workstream A1).
 *
 * Detects catch blocks in service-tier Java code that silently swallow exceptions:
 * neither rethrow nor wrap/propagate with an ErrorCode-bearing exception.
 *
 * Detection rule (from plan, algorithm-spec layer):
 *   A catch clause is a HIT iff within the catch block's brace span, NONE of the
 *   following "good" signals appear:
 *     - throw                 (rethrow)
 *     - NopMetadataException( (module exception with ErrorCode)
 *     - .errorCode(           (ErrorCode-bearing exception)
 *     - ErrorCode.            (ErrorCode reference)
 *     - BizException          (framework biz exception with ErrorCode)
 *     - Biz.fatal(            (framework biz helper)
 *
 *   i.e. "catch 后既不 rethrow 也不附加 ErrorCode 传播" = silent swallow.
 *
 *   This is the semantic superset of the existing ast-grep rules:
 *     - java-lint-empty-catch.yml  (empty catch body)
 *     - java-lint-getmessage-only.yml (catch only e.getMessage())
 *   It also covers "LOG.warn then continue" and other non-empty but
 *   ErrorCode-not-propagating patterns.
 *
 * Usage:
 *   node ai-dev/tools/check-silent-swallow.mjs --module nop-metadata
 *   node ai-dev/tools/check-silent-swallow.mjs --fixture
 *   node ai-dev/tools/check-silent-swallow.mjs --module nop-metadata --format json
 *
 * Options:
 *   --module <name>   Scan only the specified module (e.g. nop-metadata)
 *   --format <fmt>    Output format: summary (default), json
 *   --fixture         Run self-verification fixture instead of scanning codebase
 *   --help            Show this help
 *
 * Exit code:
 *   0 = zero hits (all catch blocks propagate/rethrow)
 *   1 = hits found (silent swallow detected)
 *   2 = internal error
 */

import fs from 'fs';
import path from 'path';

const GOOD_SIGNALS = [
    'throw',
    'NopMetadataException(',
    '.errorCode(',
    'ErrorCode.',
    'Errors.',
    'BizException',
    'Biz.fatal(',
];

function parseArgs(argv) {
    const args = { module: null, format: 'summary', fixture: false, help: false };
    for (let i = 2; i < argv.length; i++) {
        const a = argv[i];
        if (a === '--module' && i + 1 < argv.length) args.module = argv[++i];
        else if (a === '--format' && i + 1 < argv.length) args.format = argv[++i];
        else if (a === '--fixture') args.fixture = true;
        else if (a === '--help' || a === '-h') args.help = true;
    }
    return args;
}

function findRepoRoot() {
    let dir = process.cwd();
    while (dir !== path.dirname(dir)) {
        if (fs.existsSync(path.join(dir, 'pom.xml')) && fs.existsSync(path.join(dir, 'AGENTS.md'))) {
            return dir;
        }
        dir = path.dirname(dir);
    }
    return process.cwd();
}

function relativePath(fullPath) {
    const repoRoot = findRepoRoot();
    if (fullPath.startsWith(repoRoot)) {
        return fullPath.slice(repoRoot.length + 1);
    }
    return fullPath;
}

function walkJavaFiles(dir, results) {
    if (!fs.existsSync(dir)) return results;
    const entries = fs.readdirSync(dir, { withFileTypes: true });
    for (const entry of entries) {
        const fullPath = path.join(dir, entry.name);
        if (entry.isDirectory()) {
            if (['node_modules', '.git', 'target', '_gen'].includes(entry.name)) continue;
            // Only scan src/main for service-tier code (not tests)
            if (entry.name === 'test' && dir.endsWith('/src')) continue;
            walkJavaFiles(fullPath, results);
        } else if (entry.isFile() && entry.name.endsWith('.java')) {
            results.push(fullPath);
        }
    }
    return results;
}

/**
 * Find all catch clauses in the source and extract their brace spans.
 * Returns array of { catchLine, blockStart, blockEnd (exclusive), content }
 *
 * Algorithm:
 * 1. Find `catch` keyword followed by `(` ... `)` ... `{`
 * 2. From the opening `{`, track brace depth to find matching `}`
 * 3. Extract span content between { and }
 */
function findCatchBlocks(content) {
    const blocks = [];
    const lines = content.split('\n');

    // Work on the full content with character offsets for brace matching
    for (let lineIdx = 0; lineIdx < lines.length; lineIdx++) {
        const line = lines[lineIdx];
        // Find catch keyword on this line (word boundary, not in a string/comment — heuristic)
        const catchRegex = /\bcatch\b\s*\(/g;
        let m;
        while ((m = catchRegex.exec(line)) !== null) {
            const catchCol = m.index;
            // Skip if inside a comment or string on this line (simple heuristic)
            const beforeCatch = line.substring(0, catchCol);
            if (isInCommentOrString(beforeCatch)) continue;

            // Find the opening brace '{' after the catch clause
            // The catch clause is: catch ( Type name ) {  — possibly spanning multiple lines
            const braceResult = findOpeningBrace(lines, lineIdx, catchCol + m[0].length);
            if (!braceResult) continue; // not a valid catch block (e.g. in a comment)

            const { braceLine, braceCol } = braceResult;

            // Find the matching closing brace
            const spanResult = findMatchingBrace(lines, braceLine, braceCol);
            if (!spanResult) continue;

            const { closeLine, closeCol } = spanResult;

            // Extract span content between { and }
            const spanLines = [];
            for (let i = braceLine; i <= closeLine; i++) {
                if (i === braceLine && i === closeLine) {
                    spanLines.push(lines[i].substring(braceCol + 1, closeCol));
                } else if (i === braceLine) {
                    spanLines.push(lines[i].substring(braceCol + 1));
                } else if (i === closeLine) {
                    spanLines.push(lines[i].substring(0, closeCol));
                } else {
                    spanLines.push(lines[i]);
                }
            }
            const spanContent = spanLines.join('\n');

            blocks.push({
                catchLine: lineIdx + 1,
                blockStartLine: braceLine + 1,
                blockEndLine: closeLine + 1,
                content: spanContent,
            });
        }
    }
    return blocks;
}

function isInCommentOrString(text) {
    // Simple heuristic: if there's an odd number of unescaped double quotes or // before the position
    let inString = false;
    for (let i = 0; i < text.length; i++) {
        if (text[i] === '"' && (i === 0 || text[i - 1] !== '\\')) {
            inString = !inString;
        }
        if (!inString && text[i] === '/' && i + 1 < text.length && text[i + 1] === '/') {
            return true;
        }
    }
    return inString;
}

function findOpeningBrace(lines, startLine, startCol) {
    // From startCol on startLine, find the next '{' that opens the catch block body
    // Skip past the catch parameter declaration: catch ( ... ) {
    // depth starts at 1 because the regex consumed the opening '(' of 'catch (',
    // so we are already inside the parameter list.
    let depth = 1;
    for (let i = startLine; i < lines.length; i++) {
        const line = lines[i];
        const start = (i === startLine) ? startCol : 0;
        for (let j = start; j < line.length; j++) {
            const ch = line[j];
            if (ch === '(') depth++;
            else if (ch === ')') depth--;
            else if (ch === '{' && depth === 0) {
                return { braceLine: i, braceCol: j };
            }
        }
        // If we've exhausted the line and depth is back to 0, the { might be on the next line
    }
    return null;
}

function findMatchingBrace(lines, openLine, openCol) {
    let depth = 1;
    for (let i = openLine; i < lines.length; i++) {
        const line = lines[i];
        const start = (i === openLine) ? openCol + 1 : 0;
        for (let j = start; j < line.length; j++) {
            const ch = line[j];
            // Skip string literals and char literals (simple heuristic)
            if (ch === '"') {
                // skip to end of string
                j++;
                while (j < line.length && line[j] !== '"') {
                    if (line[j] === '\\') j++;
                    j++;
                }
                continue;
            }
            if (ch === "'") {
                j++;
                while (j < line.length && line[j] !== "'") {
                    if (line[j] === '\\') j++;
                    j++;
                }
                continue;
            }
            if (ch === '/' && j + 1 < line.length && line[j + 1] === '/') {
                break; // rest of line is comment
            }
            if (ch === '{') depth++;
            else if (ch === '}') {
                depth--;
                if (depth === 0) {
                    return { closeLine: i, closeCol: j };
                }
            }
        }
    }
    return null;
}

/**
 * Strip comments and string/char literals from Java source text using a
 * single-pass state machine. Prevents false-positive signal detection from
 * tokens that appear only inside comments or string literals.
 *
 * Replaces stripped ranges with a space (not empty) to avoid accidentally
 * merging adjacent tokens. If the input ends mid-string/mid-comment
 * (unterminated), the remainder is stripped — this is conservative (strips
 * any signal inside = potential hit, never a false pass).
 */
function stripCommentsAndStrings(text) {
    let result = '';
    let i = 0;
    const len = text.length;
    let state = 'code';
    while (i < len) {
        const ch = text[i];
        const next = i + 1 < len ? text[i + 1] : '';
        if (state === 'code') {
            if (ch === '/' && next === '/') {
                state = 'lineComment';
                i += 2;
            } else if (ch === '/' && next === '*') {
                state = 'blockComment';
                i += 2;
            } else if (ch === '"') {
                state = 'string';
                i++;
            } else if (ch === "'") {
                state = 'char';
                i++;
            } else {
                result += ch;
                i++;
            }
        } else if (state === 'string') {
            if (ch === '\\') {
                i += 2;
            } else if (ch === '"') {
                state = 'code';
                result += ' ';
                i++;
            } else {
                i++;
            }
        } else if (state === 'char') {
            if (ch === '\\') {
                i += 2;
            } else if (ch === "'") {
                state = 'code';
                result += ' ';
                i++;
            } else {
                i++;
            }
        } else if (state === 'lineComment') {
            if (ch === '\n') {
                state = 'code';
                result += ch;
                i++;
            } else {
                i++;
            }
        } else if (state === 'blockComment') {
            if (ch === '*' && next === '/') {
                state = 'code';
                result += ' ';
                i += 2;
            } else {
                i++;
            }
        }
    }
    return result;
}

/**
 * Check if the catch block content contains any good signal.
 * Signals are matched against code only (comments and string literals stripped).
 * Returns true if it has at least one good signal (NOT a hit).
 */
function hasGoodSignal(blockContent) {
    const codeOnly = stripCommentsAndStrings(blockContent);
    for (const signal of GOOD_SIGNALS) {
        if (codeOnly.includes(signal)) {
            return true;
        }
    }
    return false;
}

function scanFile(filePath) {
    const relPath = relativePath(filePath);
    const content = fs.readFileSync(filePath, 'utf-8');
    const catchBlocks = findCatchBlocks(content);
    const hits = [];
    for (const block of catchBlocks) {
        if (!hasGoodSignal(block.content)) {
            hits.push({
                file: relPath,
                line: block.catchLine,
                blockStartLine: block.blockStartLine,
                blockEndLine: block.blockEndLine,
                snippet: getSnippet(content, block.catchLine),
                rule: 'catch block has none of: ' + GOOD_SIGNALS.join(', '),
            });
        }
    }
    return hits;
}

function getSnippet(content, lineNum) {
    const lines = content.split('\n');
    const idx = lineNum - 1;
    return lines[idx] ? lines[idx].trim() : '';
}

function generateSummary(hits) {
    const lines = [];
    lines.push('# Silent Swallow Scan Report (INV-SILENT-SWALLOW)');
    lines.push(`Generated: ${new Date().toISOString().split('T')[0]}`);
    lines.push('');
    lines.push(`Total catch blocks scanned: ${totalCatchScanned}`);
    lines.push(`Hits (silent swallow): ${hits.length}`);
    lines.push('');
    if (hits.length === 0) {
        lines.push('No silent-swallow catch blocks detected.');
    } else {
        lines.push('## Hits');
        lines.push('');
        lines.push('| # | File:Line | Catch Block (start-end) | Snippet |');
        lines.push('|---|-----------|------------------------|---------|');
        hits.forEach((h, i) => {
            lines.push(`| ${i + 1} | \`${h.file}:${h.line}\` | L${h.blockStartLine}-L${h.blockEndLine} | \`${h.snippet}\` |`);
        });
        lines.push('');
        lines.push('## Rule');
        lines.push('');
        lines.push('A catch clause is a HIT iff within the catch block brace span, NONE of these signals appear:');
        lines.push('- `' + GOOD_SIGNALS.join('`\n- `') + '`');
    }
    return lines.join('\n');
}

function generateJson(hits) {
    return JSON.stringify({
        invariant: 'INV-SILENT-SWALLOW',
        generated: new Date().toISOString(),
        totalCatchScanned,
        hitCount: hits.length,
        hits,
    }, null, 2);
}

let totalCatchScanned = 0;

function scanAll(scanPaths) {
    const allHits = [];
    totalCatchScanned = 0;
    for (const scanPath of scanPaths) {
        const javaFiles = walkJavaFiles(scanPath, []);
        for (const f of javaFiles) {
            const content = fs.readFileSync(f, 'utf-8');
            const catchBlocks = findCatchBlocks(content);
            totalCatchScanned += catchBlocks.length;
            const hits = scanFile(f);
            allHits.push(...hits);
        }
    }
    allHits.sort((a, b) => a.file.localeCompare(b.file) || a.line - b.line);
    return allHits;
}

// ============================================================
// Self-verification fixture
// ============================================================

const FIXTURE_VIOLATION = `
class FixtureViolation {
    void example() {
        try {
            doSomething();
        } catch (Exception e) {
            LOG.warn("ignored", e);
            return null;
        }
    }
}
`;

const FIXTURE_COMPLIANT = `
class FixtureCompliant {
    void example() {
        try {
            doSomething();
        } catch (Exception e) {
            throw new NopMetadataException(NopMetadataErrors.ERR_TEST, e);
        }
    }
}
`;

// (a) catch references *Errors. ErrorCode enum → must PASS (not a hit)
const FIXTURE_ERRORS_ENUM = `
class FixtureErrorsEnum {
    void example() {
        try {
            doSomething();
        } catch (Exception e) {
            result.setErrors(List.of(NopMetadataErrors.ERR_INDEX_BUILD_FAILED + ": " + e.getMessage()));
        }
    }
}
`;

// (b) catch only has ErrorCode. inside a comment → must be HIT (comment bypass blocked)
const FIXTURE_COMMENT_BYPASS = `
class FixtureCommentBypass {
    void example() {
        try {
            doSomething();
        } catch (Exception e) {
            // ErrorCode. in comment should NOT count
            LOG.warn("ignored", e);
        }
    }
}
`;

// (c) catch has throw → must PASS (regression)
const FIXTURE_THROW = `
class FixtureThrow {
    void example() {
        try {
            doSomething();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
`;

// (d) catch has string containing "//" followed by throw → must PASS (no false strip of // inside string)
const FIXTURE_STRING_URL = `
class FixtureStringUrl {
    void example() {
        try {
            doSomething();
        } catch (Exception e) {
            LOG.warn("see http://example.com for details");
            throw new RuntimeException(e);
        }
    }
}
`;

function runFixture() {
    const results = [];

    const check = (label, fixture, expectedHit) => {
        const blocks = findCatchBlocks(fixture);
        for (const b of blocks) {
            const hit = !hasGoodSignal(b.content);
            results.push({ sample: label, expectedHit, actualHit: hit, pass: hit === expectedHit });
        }
    };

    check('violation', FIXTURE_VIOLATION, true);
    check('compliant', FIXTURE_COMPLIANT, false);
    check('errors-enum', FIXTURE_ERRORS_ENUM, false);
    check('comment-bypass', FIXTURE_COMMENT_BYPASS, true);
    check('throw', FIXTURE_THROW, false);
    check('string-url', FIXTURE_STRING_URL, false);

    const allPass = results.every(r => r.pass);

    console.log('Self-verification fixture: ' + (allPass ? 'PASS' : 'FAIL'));
    console.log('');
    for (const r of results) {
        const status = r.pass ? 'OK' : 'FAIL';
        console.log(`  [${status}] ${r.sample}: expected hit=${r.expectedHit}, actual hit=${r.actualHit}`);
    }
    console.log('');
    if (!allPass) {
        console.log('FIXTURE FAILED: scanner cannot distinguish violation from compliant sample.');
    }
    return allPass ? 0 : 1;
}

// ============================================================
// Main
// ============================================================

function main() {
    const args = parseArgs(process.argv);

    if (args.help) {
        console.log('Usage: node ai-dev/tools/check-silent-swallow.mjs --module nop-metadata');
        console.log('       node ai-dev/tools/check-silent-swallow.mjs --fixture');
        console.log('');
        console.log('Options:');
        console.log('  --module <name>   Scan only the specified module');
        console.log('  --format <fmt>    Output format: summary (default), json');
        console.log('  --fixture         Run self-verification fixture');
        process.exit(0);
    }

    if (args.fixture) {
        process.exit(runFixture());
    }

    const repoRoot = findRepoRoot();
    let scanPaths;
    if (args.module) {
        // Scan the service-tier source of the specified module
        const moduleServicePath = path.join(repoRoot, args.module, args.module + '-service', 'src', 'main', 'java');
        if (fs.existsSync(moduleServicePath)) {
            scanPaths = [moduleServicePath];
        } else {
            // Fallback: scan the whole module
            scanPaths = [path.join(repoRoot, args.module)];
        }
    } else {
        scanPaths = [repoRoot];
    }

    const hits = scanAll(scanPaths);

    switch (args.format) {
        case 'json':
            console.log(generateJson(hits));
            break;
        default:
            console.log(generateSummary(hits));
    }

    process.exit(hits.length > 0 ? 1 : 0);
}

main();
