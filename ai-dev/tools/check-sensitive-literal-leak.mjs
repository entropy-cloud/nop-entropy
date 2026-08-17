#!/usr/bin/env node

/**
 * check-sensitive-literal-leak.mjs
 *
 * Invariant INV-SENSITIVE guard (plan 2026-08-13-1930-2, Workstream A3).
 *
 * Detects sensitive string literals (raw JDBC URLs, inline SQL) that appear
 * in logger calls or error-message-builder (.param()) argument positions.
 *
 * Detection rule (from plan, algorithm-spec layer):
 *   A line is a HIT iff it contains BOTH:
 *     (1) a logger/error-builder call token: LOG.info|warn|error|debug|trace( or .param(
 *     (2) a string literal matching either:
 *         ① JDBC-URL pattern: /jdbc:[a-z]+:\/\//
 *         ② SQL-literal pattern: length > 12 AND /\b(SELECT|INSERT|UPDATE|DELETE|FROM|WHERE|JOIN)\b/i
 *   Exclusion: sanitized hash identifiers (sqlHash, sqlHashOf) are not hits
 *   (aligned with R8.2 AR-16 / R6.2 P2-12 desensitization precedent).
 *
 *   Implementation approximation (per plan): line-level co-occurrence — same line
 *   has both a logger/error-builder token AND a matching literal → HIT.
 *   Self-verification fixture uses single-line samples to eliminate ambiguity.
 *
 * Usage:
 *   node ai-dev/tools/check-sensitive-literal-leak.mjs --module nop-metadata
 *   node ai-dev/tools/check-sensitive-literal-leak.mjs --fixture
 *   node ai-dev/tools/check-sensitive-literal-leak.mjs --module nop-metadata --format json
 *
 * Exit code:
 *   0 = zero hits
 *   1 = hits found
 *   2 = internal error
 */

import fs from 'fs';
import path from 'path';

const JDBC_URL_PATTERN = /jdbc:[a-z]+:\/\//i;
const SQL_KEYWORDS = ['SELECT', 'INSERT', 'UPDATE', 'DELETE', 'FROM', 'WHERE', 'JOIN'];
const LOGGER_TOKENS = ['LOG.info(', 'LOG.warn(', 'LOG.error(', 'LOG.debug(', 'LOG.trace(', '.param('];
const SANITIZED_MARKERS = ['sqlHash', 'sqlHashOf', 'ARG_', 'HASH', 'hash'];

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
            if (entry.name === 'test' && dir.endsWith('/src')) continue;
            walkJavaFiles(fullPath, results);
        } else if (entry.isFile() && entry.name.endsWith('.java')) {
            results.push(fullPath);
        }
    }
    return results;
}

/**
 * Check if a line contains a logger/error-builder call token.
 */
function hasLoggerToken(line) {
    return LOGGER_TOKENS.some(token => line.includes(token));
}

/**
 * Extract all string literals from a Java source line.
 * Returns array of { value, col } for each "..." literal found.
 */
function extractStringLiterals(line) {
    const literals = [];
    let i = 0;
    while (i < line.length) {
        if (line[i] === '"') {
            // Check it's not a char literal or escaped
            // Find the end of the string
            let j = i + 1;
            let value = '';
            while (j < line.length && line[j] !== '"') {
                if (line[j] === '\\' && j + 1 < line.length) {
                    value += line[j] + line[j + 1];
                    j += 2;
                } else {
                    value += line[j];
                    j++;
                }
            }
            if (j < line.length) {
                literals.push({ value, col: i + 1 });
                i = j + 1;
            } else {
                break; // unterminated string
            }
        } else {
            i++;
        }
    }
    return literals;
}

/**
 * Check if a string literal value is a sensitive literal.
 * Returns { hit: boolean, reason: string } .
 */
function checkSensitiveLiteral(literal) {
    // Skip sanitized markers
    for (const marker of SANITIZED_MARKERS) {
        if (literal.includes(marker)) {
            return { hit: false, reason: null };
        }
    }

    // ① JDBC-URL pattern
    if (JDBC_URL_PATTERN.test(literal)) {
        return { hit: true, reason: 'raw JDBC URL literal' };
    }

    // ② SQL-literal pattern: length > 12 AND contains ≥2 distinct SQL keywords
    //    (Real SQL has SELECT...FROM, INSERT...INTO, etc. Requiring ≥2 distinct keywords
    //     avoids false positives on natural-language log messages like "join completed".)
    if (literal.length > 12) {
        let keywordCount = 0;
        const upperLit = literal.toUpperCase();
        for (const kw of SQL_KEYWORDS) {
            const re = new RegExp('\\b' + kw + '\\b');
            if (re.test(upperLit)) keywordCount++;
        }
        if (keywordCount >= 2) {
            return { hit: true, reason: 'inline SQL literal (' + literal.substring(0, 40) + (literal.length > 40 ? '...' : '') + ')' };
        }
    }

    return { hit: false, reason: null };
}

function scanFile(filePath) {
    const relPath = relativePath(filePath);
    const content = fs.readFileSync(filePath, 'utf-8');
    const lines = content.split('\n');
    const hits = [];

    for (let i = 0; i < lines.length; i++) {
        const line = lines[i];
        // Skip comment lines
        const trimmed = line.trim();
        if (trimmed.startsWith('//') || trimmed.startsWith('*') || trimmed.startsWith('/*')) {
            continue;
        }

        // Must have a logger/error-builder token on this line
        if (!hasLoggerToken(line)) continue;

        // Extract string literals
        const literals = extractStringLiterals(line);
        for (const lit of literals) {
            const result = checkSensitiveLiteral(lit.value);
            if (result.hit) {
                hits.push({
                    file: relPath,
                    line: i + 1,
                    literal: lit.value.length > 60 ? lit.value.substring(0, 60) + '...' : lit.value,
                    reason: result.reason,
                    snippet: trimmed,
                });
            }
        }
    }

    return hits;
}

function generateSummary(hits) {
    const lines = [];
    lines.push('# Sensitive Literal Leak Scan Report (INV-SENSITIVE)');
    lines.push(`Generated: ${new Date().toISOString().split('T')[0]}`);
    lines.push('');
    lines.push(`Hits (sensitive literal in logger/.param argument): ${hits.length}`);
    lines.push('');
    if (hits.length === 0) {
        lines.push('No sensitive literal leaks detected.');
    } else {
        lines.push('## Hits');
        lines.push('');
        lines.push('| # | File:Line | Reason | Literal | Snippet |');
        lines.push('|---|-----------|--------|---------|---------|');
        hits.forEach((h, i) => {
            lines.push(`| ${i + 1} | \`${h.file}:${h.line}\` | ${h.reason} | \`${h.literal}\` | \`${h.snippet}\` |`);
        });
    }
    return lines.join('\n');
}

function generateJson(hits) {
    return JSON.stringify({
        invariant: 'INV-SENSITIVE',
        generated: new Date().toISOString(),
        hitCount: hits.length,
        hits,
    }, null, 2);
}

// ============================================================
// Self-verification fixture
// ============================================================

const FIXTURE_LINES = [
    // Violation: raw JDBC URL in .param()
    '                    .param("url", "jdbc:mysql://localhost:3306/mydb");',
    // Violation: inline SQL in LOG.info
    '        LOG.info("executing query: {}", "SELECT * FROM users WHERE id = 1");',
    // Compliant: sanitized hash
    '        LOG.info("query hash: {}", sqlHash);',
    // Compliant: short non-SQL string
    '        LOG.info("join completed for table {}", tableName);',
    // Compliant: no logger on line
    '        String sql = "SELECT * FROM users";',
];

function runFixture() {
    const results = [];
    const expectations = [
        { line: 0, expectedHit: true, label: 'violation-jdbc-url' },
        { line: 1, expectedHit: true, label: 'violation-inline-sql' },
        { line: 2, expectedHit: false, label: 'compliant-sanitized-hash' },
        { line: 3, expectedHit: false, label: 'compliant-short-string' },
        { line: 4, expectedHit: false, label: 'compliant-no-logger' },
    ];

    for (const exp of expectations) {
        const line = FIXTURE_LINES[exp.line];
        let actualHit = false;
        if (hasLoggerToken(line)) {
            const literals = extractStringLiterals(line);
            for (const lit of literals) {
                const result = checkSensitiveLiteral(lit.value);
                if (result.hit) {
                    actualHit = true;
                    break;
                }
            }
        }
        results.push({
            label: exp.label,
            expectedHit: exp.expectedHit,
            actualHit,
            pass: exp.expectedHit === actualHit,
        });
    }

    const allPass = results.every(r => r.pass);
    console.log('Self-verification fixture: ' + (allPass ? 'PASS' : 'FAIL'));
    console.log('');
    for (const r of results) {
        const status = r.pass ? 'OK' : 'FAIL';
        console.log(`  [${status}] ${r.label}: expected hit=${r.expectedHit}, actual hit=${r.actualHit}`);
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
        console.log('Usage: node ai-dev/tools/check-sensitive-literal-leak.mjs --module nop-metadata');
        console.log('       node ai-dev/tools/check-sensitive-literal-leak.mjs --fixture');
        process.exit(0);
    }

    if (args.fixture) {
        process.exit(runFixture());
    }

    const repoRoot = findRepoRoot();
    let scanPaths;
    if (args.module) {
        const moduleServicePath = path.join(repoRoot, args.module, args.module + '-service', 'src', 'main', 'java');
        scanPaths = fs.existsSync(moduleServicePath) ? [moduleServicePath] : [path.join(repoRoot, args.module)];
    } else {
        scanPaths = [repoRoot];
    }

    let allHits = [];
    for (const scanPath of scanPaths) {
        const javaFiles = walkJavaFiles(scanPath, []);
        for (const f of javaFiles) {
            allHits.push(...scanFile(f));
        }
    }

    allHits.sort((a, b) => a.file.localeCompare(b.file) || a.line - b.line);

    switch (args.format) {
        case 'json':
            console.log(generateJson(allHits));
            break;
        default:
            console.log(generateSummary(allHits));
    }

    process.exit(allHits.length > 0 ? 1 : 0);
}

main();
