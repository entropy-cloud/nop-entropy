#!/usr/bin/env node

/**
 * check-orm-unique-key-constraint.mjs
 *
 * Invariant INV-UK guard (plan 2026-08-13-1930-2, Workstream A2).
 *
 * Detects <unique-key> elements in ORM model XML that are missing the
 * `constraint=` attribute (and/or `columns=` attribute). Without `constraint=`,
 * the DDL emission gate (ddl.xlib) silently skips the UNIQUE constraint,
 * losing data-integrity protection at the deployment layer (Lesson 09).
 *
 * Detection rule (from plan, algorithm-spec layer):
 *   A <unique-key> element is a HIT iff it is missing `constraint=` OR
 *   the constraint value is empty, OR it is missing `columns=`.
 *   XML-aware parsing: <unique-key> elements may span multiple lines
 *   (e.g. constraint and displayName on separate lines).
 *
 * Usage:
 *   node ai-dev/tools/check-orm-unique-key-constraint.mjs --module nop-metadata
 *   node ai-dev/tools/check-orm-unique-key-constraint.mjs --fixture
 *   node ai-dev/tools/check-orm-unique-key-constraint.mjs --module nop-metadata --format json
 *
 * Options:
 *   --module <name>   Scan only the specified module's model/*.orm.xml
 *   --format <fmt>    Output format: summary (default), json
 *   --fixture         Run self-verification fixture instead of scanning codebase
 *   --help            Show this help
 *
 * Exit code:
 *   0 = zero hits (all unique-keys have constraint + columns)
 *   1 = hits found (missing constraint or columns)
 *   2 = internal error
 */

import fs from 'fs';
import path from 'path';

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

function findOrmFiles(repoRoot, module) {
    const results = [];
    if (module) {
        const modelDir = path.join(repoRoot, module, 'model');
        if (fs.existsSync(modelDir)) {
            const entries = fs.readdirSync(modelDir);
            for (const e of entries) {
                if (e.endsWith('.orm.xml')) {
                    results.push(path.join(modelDir, e));
                }
            }
        }
    } else {
        // Scan all modules for model/*.orm.xml
        const modulesDir = repoRoot;
        const entries = fs.readdirSync(modulesDir, { withFileTypes: true });
        for (const entry of entries) {
            if (entry.isDirectory() && entry.name.startsWith('nop-')) {
                const modelDir = path.join(modulesDir, entry.name, 'model');
                if (fs.existsSync(modelDir)) {
                    for (const f of fs.readdirSync(modelDir)) {
                        if (f.endsWith('.orm.xml')) {
                            results.push(path.join(modelDir, f));
                        }
                    }
                }
            }
        }
    }
    return results;
}

/**
 * Extract all <unique-key> elements from ORM XML, handling:
 * - Self-closing: <unique-key name="..." ... />
 * - Multi-line: attributes spread across lines until />
 * - Block: <unique-key name="..."> ... </unique-key>
 *
 * Returns array of { name, hasConstraint, constraintValue, hasColumns, columnsValue, line, rawText }
 */
function extractUniqueKeys(content) {
    const lines = content.split('\n');
    const uniqueKeys = [];
    const regex = /<unique-key\b/g;
    let m;

    while ((m = regex.exec(content)) !== null) {
        const startOffset = m.index;
        const startLine = offsetToLine(content, startOffset);

        // Find the end of this element: either /> or </unique-key>
        const selfCloseEnd = findSelfClose(content, startOffset);
        const blockEnd = findBlockClose(content, startOffset);

        let endOffset;
        if (selfCloseEnd !== -1 && (blockEnd === -1 || selfCloseEnd < blockEnd)) {
            endOffset = selfCloseEnd;
        } else if (blockEnd !== -1) {
            endOffset = blockEnd;
        } else {
            continue; // malformed, skip
        }

        const elementText = content.substring(startOffset, endOffset);

        // Extract attributes
        const nameMatch = elementText.match(/name="([^"]*)"/);
        const constraintMatch = elementText.match(/constraint="([^"]*)"/);
        const columnsMatch = elementText.match(/columns="([^"]*)"/);
        const hasConstraintAttr = /constraint="/.test(elementText);
        const hasColumnsAttr = /columns="/.test(elementText);

        uniqueKeys.push({
            name: nameMatch ? nameMatch[1] : '(unnamed)',
            hasConstraint: hasConstraintAttr && constraintMatch && constraintMatch[1].length > 0,
            constraintValue: constraintMatch ? constraintMatch[1] : null,
            hasColumns: hasColumnsAttr && columnsMatch && columnsMatch[1].length > 0,
            columnsValue: columnsMatch ? columnsMatch[1] : null,
            line: startLine,
            rawText: elementText.split('\n')[0].trim(),
        });
    }

    return uniqueKeys;
}

function offsetToLine(content, offset) {
    let line = 1;
    for (let i = 0; i < offset && i < content.length; i++) {
        if (content[i] === '\n') line++;
    }
    return line;
}

function findSelfClose(content, startOffset) {
    // Find the next /> after startOffset (within the same element)
    const rest = content.substring(startOffset);
    const match = rest.match(/\/>/);
    return match ? startOffset + match.index + 2 : -1;
}

function findBlockClose(content, startOffset) {
    const rest = content.substring(startOffset);
    const match = rest.match(/<\/unique-key>/);
    return match ? startOffset + match.index + '</unique-key>'.length : -1;
}

function scanFile(filePath) {
    const relPath = relativePath(filePath);
    const content = fs.readFileSync(filePath, 'utf-8');
    const uniqueKeys = extractUniqueKeys(content);
    const hits = [];

    for (const uk of uniqueKeys) {
        const issues = [];
        if (!uk.hasConstraint) {
            issues.push('missing or empty constraint=');
        }
        if (!uk.hasColumns) {
            issues.push('missing or empty columns=');
        }
        if (issues.length > 0) {
            hits.push({
                file: relPath,
                line: uk.line,
                uniqueKeyName: uk.name,
                issue: issues.join('; '),
                snippet: uk.rawText,
            });
        }
    }

    return { hits, totalScanned: uniqueKeys.length };
}

function generateSummary(hits, totalScanned) {
    const lines = [];
    lines.push('# Unique-Key Constraint Scan Report (INV-UK)');
    lines.push(`Generated: ${new Date().toISOString().split('T')[0]}`);
    lines.push('');
    lines.push(`Total <unique-key> elements scanned: ${totalScanned}`);
    lines.push(`Hits (missing constraint or columns): ${hits.length}`);
    lines.push('');
    if (hits.length === 0) {
        lines.push('All unique-key elements have constraint= and columns= attributes.');
    } else {
        lines.push('## Hits');
        lines.push('');
        lines.push('| # | File:Line | Unique-Key Name | Issue | Snippet |');
        lines.push('|---|-----------|-----------------|-------|---------|');
        hits.forEach((h, i) => {
            lines.push(`| ${i + 1} | \`${h.file}:${h.line}\` | ${h.uniqueKeyName} | ${h.issue} | \`${h.snippet}\` |`);
        });
    }
    return lines.join('\n');
}

function generateJson(hits, totalScanned) {
    return JSON.stringify({
        invariant: 'INV-UK',
        generated: new Date().toISOString(),
        totalUniqueKeysScanned: totalScanned,
        hitCount: hits.length,
        hits,
    }, null, 2);
}

// ============================================================
// Self-verification fixture
// ============================================================

const FIXTURE_XML = `
<orm>
    <entity name="GoodEntity">
        <unique-keys>
            <unique-key name="UK_GOOD" columns="col1,col2" constraint="UK_GOOD"/>
        </unique-keys>
    </entity>
    <entity name="BadEntity">
        <unique-keys>
            <unique-key name="UK_BAD" columns="col1"/>
        </unique-keys>
    </entity>
    <entity name="BadEntity2">
        <unique-keys>
            <unique-key name="UK_BAD2" constraint="UK_BAD2"/>
        </unique-keys>
    </entity>
</orm>
`;

function runFixture() {
    const uniqueKeys = extractUniqueKeys(FIXTURE_XML);
    const results = [];

    for (const uk of uniqueKeys) {
        const isHit = !uk.hasConstraint || !uk.hasColumns;
        const expectedHit = uk.name === 'UK_GOOD' ? false : true;
        const pass = isHit === expectedHit;
        results.push({
            name: uk.name,
            hasConstraint: uk.hasConstraint,
            hasColumns: uk.hasColumns,
            expectedHit,
            actualHit: isHit,
            pass,
        });
    }

    const allPass = results.every(r => r.pass);

    console.log('Self-verification fixture: ' + (allPass ? 'PASS' : 'FAIL'));
    console.log('');
    for (const r of results) {
        const status = r.pass ? 'OK' : 'FAIL';
        console.log(`  [${status}] ${r.name}: constraint=${r.hasConstraint}, columns=${r.hasColumns}, expected hit=${r.expectedHit}, actual hit=${r.actualHit}`);
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
        console.log('Usage: node ai-dev/tools/check-orm-unique-key-constraint.mjs --module nop-metadata');
        console.log('       node ai-dev/tools/check-orm-unique-key-constraint.mjs --fixture');
        process.exit(0);
    }

    if (args.fixture) {
        process.exit(runFixture());
    }

    const repoRoot = findRepoRoot();
    const ormFiles = findOrmFiles(repoRoot, args.module);

    let allHits = [];
    let totalScanned = 0;
    for (const f of ormFiles) {
        const { hits, totalScanned: ts } = scanFile(f);
        allHits.push(...hits);
        totalScanned += ts;
    }

    allHits.sort((a, b) => a.file.localeCompare(b.file) || a.line - b.line);

    switch (args.format) {
        case 'json':
            console.log(generateJson(allHits, totalScanned));
            break;
        default:
            console.log(generateSummary(allHits, totalScanned));
    }

    process.exit(allHits.length > 0 ? 1 : 0);
}

main();
