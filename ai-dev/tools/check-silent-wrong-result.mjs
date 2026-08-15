#!/usr/bin/env node

/**
 * check-silent-wrong-result.mjs
 *
 * Invariant gate for the silent-wrong-result failure family
 * (plan 2026-08-15-0820-1, Cycle 2 / I1').
 *
 * One scanner x 5 rules, each rule tagged with its sub-family:
 *
 *   locale            INV-LOCALE            no-arg .toLowerCase()/.toUpperCase()
 *                                             (default-locale case mapping; AR-12 family)
 *   narrowing-cast    INV-NARROW            (long|int|short) cast on a floating-typed
 *                                             operand that is a sub-operand of arithmetic
 *                                             (truncation-before-arithmetic; AR-01 family)
 *   contains-classify INV-CONTAINS-CLASSIFY .contains( on a String-typed receiver
 *                                             (substring classification; AR-05 family)
 *   delim-key         INV-DELIM-KEY         separator-concatenated composite key used in
 *                                             collection key position or assigned to a
 *                                             *Key variable (AR-03 family)
 *   bigdec-precision  INV-BIGDEC            BigDecimal.valueOf(...)/new BigDecimal(...)
 *                                             argument containing .doubleValue() inside a
 *                                             method that has no .longValue() integer
 *                                             routing (AR-10 family; the routed form in
 *                                             AggregationHelper.toBigDecimal is the
 *                                             protected correct shape and is exempt)
 *
 * Detection runs on comment-stripped text so tokens inside javadoc/block comments
 * are never counted (e.g. `catch (SQLException)` or `{@code .toLowerCase()}` in
 * javadoc). String literal handling: locale/narrowing/contains/bigdec run on
 * fully masked text (strings blanked); delim-key needs separator literals and runs
 * on comments-only-stripped text.
 *
 * Allowed-comment mechanism (for I3' adjudication outcome (b)):
 *   A raw source line containing `// invariant-ok: <reference>` suppresses the
 *   hits on that same line: they move out of the violation set AND out of the
 *   baseline reconciliation input. They are still listed in the output under
 *   "Allowed (adjudicated)" so no silent blind spot is created. An allowed hit
 *   must NOT live in the baseline as well (terminal state is one of the two).
 *
 * Baseline reconciliation (mode b, snapshot reconciliation):
 *   --baseline <file> loads a JSON baseline {entries:[{file,family,text,count}]}.
 *   Key = file path + family + normalized (trimmed) hit-line text.
 *   Semantics = count-aware subset: for every key, current count <= baseline
 *   count => exit 0 (green). Any key exceeding its baseline count, or any key
 *   not present in the baseline => exit 1 (red). Fewer hits than baseline is
 *   GREEN (progressive fix period keeps CI green; baseline shrinks only via
 *   adjudication/fix bookkeeping).
 *
 * Usage:
 *   node ai-dev/tools/check-silent-wrong-result.mjs --module nop-metadata
 *   node ai-dev/tools/check-silent-wrong-result.mjs --module nop-metadata --rule locale
 *   node ai-dev/tools/check-silent-wrong-result.mjs --module nop-metadata --format json
 *   node ai-dev/tools/check-silent-wrong-result.mjs --module nop-metadata \
 *          --baseline ai-dev/audits/nop-metadata-invariants/baseline-cycle2/silent-wrong-result.json
 *   node ai-dev/tools/check-silent-wrong-result.mjs --module nop-metadata --emit-baseline
 *   node ai-dev/tools/check-silent-wrong-result.mjs --fixture
 *
 * Exit code:
 *   0 = zero hits, or (with --baseline) all hits within baseline
 *   1 = hits found outside baseline / without baseline
 *   2 = internal error (missing/unparseable baseline, unknown rule, ...)
 */

import fs from 'fs';
import path from 'path';

const RULES = ['locale', 'narrowing-cast', 'contains-classify', 'delim-key', 'bigdec-precision'];

const INV_BY_RULE = {
    'locale': 'INV-LOCALE',
    'narrowing-cast': 'INV-NARROW',
    'contains-classify': 'INV-CONTAINS-CLASSIFY',
    'delim-key': 'INV-DELIM-KEY',
    'bigdec-precision': 'INV-BIGDEC',
};

const KEY_METHODS = [
    'get', 'put', 'add', 'contains', 'computeIfAbsent', 'putIfAbsent',
    'getOrDefault', 'merge', 'remove', 'containsKey', 'containsValue',
];

const DELIM_CLASS = '[|:;#@$%^&,]';

// String-transform call tails: receiver of .contains() produced by these calls
// is String-typed (used for chained receivers like x.toUpperCase().contains(..)).
const STRING_TRANSFORM_CALLS = [
    'toLowerCase', 'toUpperCase', 'trim', 'strip', 'toString', 'intern', 'valueOf', 'substring', 'replace',
];

function parseArgs(argv) {
    const args = {
        module: null, format: 'summary', fixture: false, help: false,
        baseline: null, rule: null, emitBaseline: false,
    };
    for (let i = 2; i < argv.length; i++) {
        const a = argv[i];
        if (a === '--module' && i + 1 < argv.length) args.module = argv[++i];
        else if (a === '--format' && i + 1 < argv.length) args.format = argv[++i];
        else if (a === '--fixture') args.fixture = true;
        else if (a === '--baseline' && i + 1 < argv.length) args.baseline = argv[++i];
        else if (a === '--rule' && i + 1 < argv.length) args.rule = argv[++i];
        else if (a === '--emit-baseline') args.emitBaseline = true;
        else if (a === '--help' || a === '-h') args.help = true;
    }
    if (args.rule && !RULES.includes(args.rule)) {
        console.error(`Error: unknown rule '${args.rule}'. Valid rules: ${RULES.join(', ')}`);
        process.exit(2);
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
 * Same-length mask of the source where block/line comment bodies and
 * string/char literal contents are replaced with spaces (newlines and quote
 * delimiters preserved). Line/offset structure is identical to the original,
 * so hit positions computed on the mask map 1:1 to the original text.
 */
function maskCommentsAndStrings(text) {
    const chars = text.split('');
    let state = 'code';
    let i = 0;
    const len = text.length;
    while (i < len) {
        const ch = text[i];
        const next = i + 1 < len ? text[i + 1] : '';
        if (state === 'code') {
            if (ch === '/' && next === '/') {
                state = 'lineComment';
                chars[i] = ' ';
                chars[i + 1] = ' ';
                i += 2;
            } else if (ch === '/' && next === '*') {
                state = 'blockComment';
                chars[i] = ' ';
                chars[i + 1] = ' ';
                i += 2;
            } else if (ch === '"') {
                state = 'string';
                i++;
            } else if (ch === "'") {
                state = 'char';
                i++;
            } else {
                i++;
            }
        } else if (state === 'string' || state === 'char') {
            const quote = state === 'string' ? '"' : "'";
            if (ch === '\\') {
                chars[i] = ' ';
                if (i + 1 < len) chars[i + 1] = ' ';
                i += 2;
            } else if (ch === quote) {
                state = 'code';
                i++;
            } else {
                if (ch !== '\n') chars[i] = ' ';
                i++;
            }
        } else if (state === 'lineComment') {
            if (ch === '\n') {
                state = 'code';
                i++;
            } else {
                chars[i] = ' ';
                i++;
            }
        } else if (state === 'blockComment') {
            if (ch === '*' && next === '/') {
                chars[i] = ' ';
                chars[i + 1] = ' ';
                state = 'code';
                i += 2;
            } else {
                if (ch !== '\n') chars[i] = ' ';
                i++;
            }
        }
    }
    return chars.join('');
}

/**
 * Strip comments only (line + block), keep string/char literals verbatim.
 * Needed by delim-key whose pattern matches separator literals ("#" etc.).
 */
function stripCommentsOnly(text) {
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
                result += ch;
                i++;
            } else if (ch === "'") {
                state = 'char';
                result += ch;
                i++;
            } else {
                result += ch;
                i++;
            }
        } else if (state === 'string') {
            if (ch === '\\') {
                result += ch + (next !== undefined ? next : '');
                i += 2;
            } else if (ch === '"') {
                state = 'code';
                result += ch;
                i++;
            } else {
                result += ch;
                i++;
            }
        } else if (state === 'char') {
            if (ch === '\\') {
                result += ch + (next !== undefined ? next : '');
                i += 2;
            } else if (ch === "'") {
                state = 'code';
                result += ch;
                i++;
            } else {
                result += ch;
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
                if (ch === '\n') result += ch;
                i++;
            }
        }
    }
    return result;
}

function lineOfOffset(text, offset) {
    let line = 1;
    for (let i = 0; i < offset && i < text.length; i++) {
        if (text[i] === '\n') line++;
    }
    return line;
}

function snippetAt(rawText, lineNum) {
    const lines = rawText.split('\n');
    const idx = lineNum - 1;
    return lines[idx] !== undefined ? lines[idx].trim() : '';
}

function hasInvariantOk(rawText, lineNum) {
    const lines = rawText.split('\n');
    const idx = lineNum - 1;
    return lines[idx] !== undefined && lines[idx].includes('// invariant-ok:');
}

// ============================================================
// Rule 1: locale — no-arg .toLowerCase() / .toUpperCase()
// ============================================================

function scanLocale(masked, rawText, relPath) {
    const hits = [];
    const lines = masked.split('\n');
    for (let i = 0; i < lines.length; i++) {
        const re = /\.(?:toLowerCase|toUpperCase)\(\)/g;
        let m;
        while ((m = re.exec(lines[i])) !== null) {
            hits.push({
                file: relPath, line: i + 1, family: 'locale',
                snippet: snippetAt(rawText, i + 1),
                rule: 'no-arg .toLowerCase()/.toUpperCase() (default-locale case mapping; INV-LOCALE)',
            });
        }
    }
    return hits;
}

// ============================================================
// Rule 2: narrowing-cast — (long|int|short) on floating-typed
//         operand that is a sub-operand of arithmetic
// ============================================================

function scanNarrowingCast(masked, rawText, relPath) {
    const hits = [];
    const re = /\((?:long|int|short)\)\s*([A-Za-z_$][\w.$]*)\s*(?=[+\-*/%])/g;
    let m;
    while ((m = re.exec(masked)) !== null) {
        const operand = m[1];
        const simple = operand.split('.').pop();
        // Operand must be floating-point typed: a same-file double/float declaration.
        const floatDecl = new RegExp(`\\b(?:double|Double|float|Float)\\s+(?:final\\s+)?${simple}\\b`);
        if (floatDecl.test(masked)) {
            const line = lineOfOffset(masked, m.index);
            hits.push({
                file: relPath, line, family: 'narrowing-cast',
                snippet: snippetAt(rawText, line),
                rule: `(long|int|short) cast on floating-typed operand '${operand}' followed by arithmetic (truncation before arithmetic; INV-NARROW)`,
            });
        }
    }
    return hits;
}

// ============================================================
// Rule 3: contains-classify — .contains( with String-typed receiver
// ============================================================

function scanContainsClassify(masked, rawText, relPath) {
    const hits = [];
    const lines = masked.split('\n');
    for (let i = 0; i < lines.length; i++) {
        const re = /\.contains\s*\(/g;
        let m;
        while ((m = re.exec(lines[i])) !== null) {
            const before = lines[i].substring(0, m.index);
            let hit = false;
            let receiverDesc = '';

            // Case A: chained call tail — x.toUpperCase().contains(
            const callTail = before.match(/([A-Za-z_$][\w.$]*)\s*\(\s*\)\s*$/);
            // Case B: plain identifier tail — upper.contains(
            const idTail = callTail ? null : before.match(/([A-Za-z_$][\w.$]*)\s*$/);

            if (callTail) {
                const method = callTail[1].split('.').pop();
                if (STRING_TRANSFORM_CALLS.includes(method)) {
                    hit = true;
                    receiverDesc = `chain tail ${method}() (String-typed)`;
                }
            } else if (idTail) {
                const id = idTail[1];
                const simple = id.split('.').pop();
                const stringDecl = new RegExp(`\\bString\\s+(?:final\\s+)?${simple}\\b`);
                if (stringDecl.test(masked)) {
                    hit = true;
                    receiverDesc = `identifier '${id}' (String-declared in file)`;
                }
            }
            if (hit) {
                hits.push({
                    file: relPath, line: i + 1, family: 'contains-classify',
                    snippet: snippetAt(rawText, i + 1),
                    rule: `String.contains on receiver ${receiverDesc} (substring membership; classification use to be adjudicated; INV-CONTAINS-CLASSIFY)`,
                });
            }
        }
    }
    return hits;
}

// ============================================================
// Rule 4: delim-key — separator-concatenated composite key
// ============================================================

function balancedParenSpan(text, openOffset) {
    let depth = 0;
    for (let i = openOffset; i < text.length; i++) {
        if (text[i] === '(') depth++;
        else if (text[i] === ')') {
            depth--;
            if (depth === 0) return { start: openOffset, end: i };
        }
    }
    return null; // unbalanced (malformed) — caller must not silently pass
}

function scanDelimKey(stripped, rawText, relPath) {
    const hits = [];
    const sepConcat = new RegExp(`\\+\\s*["'](${DELIM_CLASS})["']\\s*\\+`);

    // (a) separator concat inside collection key-position argument span
    const keyCall = new RegExp(`\\.(?:${KEY_METHODS.join('|')})\\s*\\(`, 'g');
    let m;
    while ((m = keyCall.exec(stripped)) !== null) {
        const openParen = stripped.indexOf('(', m.index + m[0].length - 1);
        const span = balancedParenSpan(stripped, openParen);
        if (span === null) {
            // Malformed/unbalanced parens: report explicitly, never silently skip.
            const line = lineOfOffset(stripped, m.index);
            hits.push({
                file: relPath, line, family: 'delim-key',
                snippet: snippetAt(rawText, line),
                rule: `INTERNAL: unbalanced parens after .${m[0].replace(/\s*\($/, '')}( — could not extract argument span (INV-DELIM-KEY)`,
            });
            continue;
        }
        const argText = stripped.substring(span.start + 1, span.end);
        if (sepConcat.test(argText)) {
            const line = lineOfOffset(stripped, m.index);
            hits.push({
                file: relPath, line, family: 'delim-key',
                snippet: snippetAt(rawText, line),
                rule: `separator-concatenated key inside .${m[0].replace(/\s*\($/, '')}(...) argument (composite key collision risk; INV-DELIM-KEY)`,
            });
        }
    }

    // (b) String <name containing Key> = <separator concat or String.join("sep", ...)>
    const keyVar = /\bString\s+(\w*[Kk]ey\w*)\s*=/g;
    while ((m = keyVar.exec(stripped)) !== null) {
        const semi = stripped.indexOf(';', m.index);
        if (semi === -1) continue;
        const rhs = stripped.substring(m.index, semi);
        if (sepConcat.test(rhs) || new RegExp(`String\\.join\\s*\\(\\s*["']${DELIM_CLASS}["']`).test(rhs)) {
            const line = lineOfOffset(stripped, m.index);
            hits.push({
                file: relPath, line, family: 'delim-key',
                snippet: snippetAt(rawText, line),
                rule: `composite key variable '${m[1]}' built by separator concatenation (INV-DELIM-KEY)`,
            });
        }
    }
    return hits;
}

// ============================================================
// Rule 5: bigdec-precision — Number->BigDecimal via doubleValue
//         without integer (longValue) routing in the method
// ============================================================

function findEnclosingMethodSpan(maskedLines, matchLineIdx) {
    const CONTROL = /^\s*(?:if|for|while|switch|catch|try|do|else|synchronized|return|throw|new)\b/;
    // Method signature line: optional modifiers, optional generics, a return type,
    // a method name, then '(' — covers public/protected/private AND package-private
    // methods (e.g. `java.math.BigDecimal toBigDecimal(Object v) {`).
    const SIG = /^\s*(?:(?:public|private|protected|static|final|synchronized|abstract|default|native|strictfp)\s+)*(?:<[^>]+>\s+)?[\w.$]+(?:<[^>]*>)?(?:\[\])*\s+[A-Za-z_$][\w$]*\s*\(/;
    for (let i = matchLineIdx - 1; i >= 0; i--) {
        const line = maskedLines[i];
        if (!SIG.test(line) || CONTROL.test(line)) continue;
        // Find the opening '{' at/after this signature line (allow wrapped params).
        let braceLine = -1, braceCol = -1;
        for (let j = i; j < Math.min(i + 6, maskedLines.length); j++) {
            const col = maskedLines[j].indexOf('{');
            if (col !== -1) {
                braceLine = j;
                braceCol = col;
                break;
            }
        }
        if (braceLine === -1) continue;
        // Brace-match forward on the joined masked text.
        const masked = maskedLines.join('\n');
        const openOffset = maskedLines.slice(0, braceLine).join('\n').length + (braceLine > 0 ? 1 : 0) + braceCol;
        let depth = 0;
        for (let k = openOffset; k < masked.length; k++) {
            if (masked[k] === '{') depth++;
            else if (masked[k] === '}') {
                depth--;
                if (depth === 0) {
                    const closeLine = lineOfOffset(masked, k);
                    if (matchLineIdx + 1 >= i + 1 && matchLineIdx + 1 <= closeLine) {
                        return { startLine: i + 1, endLine: closeLine, text: masked.substring(openOffset, k) };
                    }
                    break; // matched block does not enclose the match — keep scanning up
                }
            }
        }
    }
    return null;
}

function scanBigdec(masked, rawText, relPath) {
    const hits = [];
    const maskedLines = masked.split('\n');
    const callRe = /(?:(?:java\.math\.)?BigDecimal\.valueOf\s*\(|new\s+(?:java\.math\.)?BigDecimal\s*\()/g;
    let m;
    while ((m = callRe.exec(masked)) !== null) {
        const openParen = masked.indexOf('(', m.index);
        const span = balancedParenSpan(masked, openParen);
        if (span === null) {
            // Malformed/unbalanced parens: report explicitly, never silently skip
            // (aligned with the delim-key INTERNAL reporting).
            const lineIdx = lineOfOffset(masked, m.index);
            hits.push({
                file: relPath, line: lineIdx + 1, family: 'bigdec-precision',
                snippet: snippetAt(rawText, lineIdx + 1),
                rule: 'INTERNAL: unbalanced parens after BigDecimal.valueOf/new BigDecimal( — could not extract argument span (INV-BIGDEC)',
            });
            continue;
        }
        const argText = masked.substring(span.start + 1, span.end);
        if (!argText.includes('.doubleValue()')) continue;

        const lineIdx = lineOfOffset(masked, m.index) - 1;
        const method = findEnclosingMethodSpan(maskedLines, lineIdx);
        const routed = method !== null && method.text.includes('.longValue()');
        if (!routed) {
            hits.push({
                file: relPath, line: lineIdx + 1, family: 'bigdec-precision',
                snippet: snippetAt(rawText, lineIdx + 1),
                rule: method === null
                    ? 'BigDecimal from .doubleValue() with no enclosing method located (conservative hit; INV-BIGDEC)'
                    : 'BigDecimal from .doubleValue() in a method without .longValue() integer routing (lossy for Long>2^53; INV-BIGDEC)',
            });
        }
    }
    return hits;
}

// ============================================================
// Scan orchestration
// ============================================================

function scanFile(filePath) {
    const relPath = relativePath(filePath);
    const raw = fs.readFileSync(filePath, 'utf-8');
    const masked = maskCommentsAndStrings(raw);
    const stripped = stripCommentsOnly(raw);
    const hits = [];
    hits.push(...scanLocale(masked, raw, relPath));
    hits.push(...scanNarrowingCast(masked, raw, relPath));
    hits.push(...scanContainsClassify(masked, raw, relPath));
    hits.push(...scanDelimKey(stripped, raw, relPath));
    hits.push(...scanBigdec(masked, raw, relPath));
    return hits;
}

function scanAll(scanPaths, ruleFilter) {
    let all = [];
    for (const scanPath of scanPaths) {
        const javaFiles = walkJavaFiles(scanPath, []);
        for (const f of javaFiles) {
            all.push(...scanFile(f));
        }
    }
    if (ruleFilter) all = all.filter(h => h.family === ruleFilter);
    all.sort((a, b) => a.file.localeCompare(b.file) || a.line - b.line || a.family.localeCompare(b.family));
    return all;
}

function splitAllowed(hits, rawTextByFile) {
    const violations = [];
    const allowed = [];
    for (const h of hits) {
        const rawText = rawTextByFile.get(h.file);
        if (rawText !== undefined && hasInvariantOk(rawText, h.line)) {
            allowed.push({ ...h, allowed: true });
        } else {
            violations.push(h);
        }
    }
    return { violations, allowed };
}

// ============================================================
// Baseline reconciliation (count-aware subset semantics)
// ============================================================

function hitKey(h) {
    return `${h.file} | ${h.family} | ${h.snippet}`;
}

function loadBaseline(baselinePath) {
    if (!fs.existsSync(baselinePath)) {
        console.error(`Error: baseline file not found: ${baselinePath}`);
        process.exit(2);
    }
    let parsed;
    try {
        parsed = JSON.parse(fs.readFileSync(baselinePath, 'utf-8'));
    } catch (e) {
        console.error(`Error: baseline file is not valid JSON: ${baselinePath} (${e.message})`);
        process.exit(2);
    }
    if (!parsed || !Array.isArray(parsed.entries)) {
        console.error(`Error: baseline file must contain an 'entries' array: ${baselinePath}`);
        process.exit(2);
    }
    return parsed;
}

function reconcile(violations, baseline) {
    const baselineCounts = new Map();
    for (const e of baseline.entries) {
        const key = `${e.file} | ${e.family} | ${e.text}`;
        baselineCounts.set(key, e.count);
    }
    const currentCounts = new Map();
    for (const h of violations) {
        const key = hitKey(h);
        currentCounts.set(key, (currentCounts.get(key) || 0) + 1);
    }
    const excess = [];
    for (const [key, count] of currentCounts) {
        const base = baselineCounts.get(key);
        if (base === undefined) {
            excess.push({ key, current: count, baseline: 0, reason: 'new key not in baseline' });
        } else if (count > base) {
            excess.push({ key, current: count, baseline: base, reason: 'count exceeds baseline' });
        }
    }
    const within = [];
    for (const [key, count] of currentCounts) {
        const base = baselineCounts.get(key);
        if (base !== undefined && count <= base) {
            within.push({ key, current: count, baseline: base });
        }
    }
    return { excess, within };
}

function buildBaselineJson(violations) {
    const counts = new Map();
    for (const h of violations) {
        const key = hitKey(h);
        counts.set(key, (counts.get(key) || 0) + 1);
    }
    const entries = [...counts.entries()]
        .map(([key, count]) => {
            const [file, family, ...rest] = key.split(' | ');
            return { file, family, text: rest.join(' | '), count };
        })
        .sort((a, b) => a.file.localeCompare(b.file) || a.family.localeCompare(b.family) || a.text.localeCompare(b.text));
    return {
        invariant: 'INV-SILENT-WRONG-RESULT',
        note: 'Ratchet baseline (mode b). Shrinks only via I3\' adjudication terminal states or I4\' fixes; never expands without traceable justification.',
        entries,
    };
}

// ============================================================
// Output
// ============================================================

function generateSummary(violations, allowed, perFamily, baselineResult) {
    const lines = [];
    lines.push('# Silent Wrong Result Scan Report (INV-LOCALE / INV-NARROW / INV-CONTAINS-CLASSIFY / INV-DELIM-KEY / INV-BIGDEC)');
    lines.push(`Generated: ${new Date().toISOString().split('T')[0]}`);
    lines.push('');
    lines.push('Hits per family:');
    for (const rule of RULES) {
        lines.push(`  ${rule.padEnd(20)} ${String(perFamily[rule] || 0).padStart(3)}  (${INV_BY_RULE[rule]})`);
    }
    lines.push(`  ${'TOTAL'.padEnd(20)} ${String(violations.length).padStart(3)}`);
    if (allowed.length > 0) {
        lines.push(`  allowed (invariant-ok)  ${String(allowed.length).padStart(3)}  (adjudicated, excluded from violations & baseline input)`);
    }
    lines.push('');
    if (violations.length === 0) {
        lines.push('No violations detected.');
    } else {
        lines.push('## Violations');
        lines.push('');
        lines.push('| # | Family | File:Line | Snippet |');
        lines.push('|---|--------|-----------|---------|');
        violations.forEach((h, i) => {
            lines.push(`| ${i + 1} | ${h.family} | \`${h.file}:${h.line}\` | \`${h.snippet}\` |`);
        });
    }
    if (allowed.length > 0) {
        lines.push('');
        lines.push('## Allowed (adjudicated via // invariant-ok — NOT counted, listed to avoid silent blind spots)');
        lines.push('');
        for (const a of allowed) {
            lines.push(`- \`${a.file}:${a.line}\` [${a.family}] \`${a.snippet}\``);
        }
    }
    if (baselineResult) {
        lines.push('');
        lines.push('## Baseline reconciliation (mode b)');
        lines.push('');
        lines.push(`Keys within baseline: ${baselineResult.within.length}`);
        if (baselineResult.excess.length > 0) {
            lines.push(`Keys outside baseline: ${baselineResult.excess.length}`);
            for (const e of baselineResult.excess) {
                lines.push(`- EXCESS: ${e.key} (current=${e.current}, baseline=${e.baseline}, ${e.reason})`);
            }
        } else {
            lines.push('Keys outside baseline: 0 — all hits within baseline (current <= baseline per key).');
        }
    }
    return lines.join('\n');
}

// ============================================================
// Self-verification fixture
// ============================================================

const FX = {};

FX.localeViolation = `
class FxLocaleViolation {
    void m(String x) {
        String k = x.toLowerCase();
    }
}
`;
FX.localeCompliant = `
class FxLocaleCompliant {
    void m(String x) {
        String k = x.toLowerCase(Locale.ROOT);
    }
}
`;
FX.localeJavadocPseudo = `
class FxLocaleJavadoc {
    /**
     * AR-12 note: {@code "I".toLowerCase()} changes under tr-TR.
     */
    void m(String x) {
        String k = x.toLowerCase(Locale.ROOT);
    }
}
`;

FX.narrowViolation = `
class FxNarrowViolation {
    void m(double amount, long unitMillis) {
        long ms = (long) amount * unitMillis;
    }
}
`;
FX.narrowCompliantParen = `
class FxNarrowCompliantParen {
    void m(double amount, long unitMillis) {
        long ms = (long) (amount * unitMillis);
    }
}
`;
FX.narrowWidening = `
class FxNarrowWidening {
    void m(int from, long limit) {
        long to = (long) from + limit;
    }
}
`;

FX.containsViolation = `
class FxContainsViolation {
    void m(String typeName) {
        String upper = typeName.toUpperCase(Locale.ROOT);
        if (upper.contains("INT")) { return; }
    }
}
`;
FX.containsSetOk = `
class FxContainsSetOk {
    void m(java.util.Set<String> names, String upper) {
        if (names.contains(upper)) { return; }
    }
}
`;
FX.containsUnknownReceiver = `
class FxContainsUnknown {
    void m() {
        if (getNames().contains("INT")) { return; }
    }
}
`;

FX.delimInlineKey = `
class FxDelimInline {
    void m(String entityType, String entityId, java.util.Set<String> visited) {
        visited.add(entityType + "#" + entityId);
    }
}
`;
FX.delimKeyVar = `
class FxDelimKeyVar {
    void m(String a, String b, java.util.Set<String> visited) {
        String visitKey = "nop-meta-table" + "|" + b;
        visited.add(visitKey);
    }
}
`;
FX.delimMessageOk = `
class FxDelimMessage {
    void m(long minRows, long maxRows, StringBuilder sb) {
        sb.append("volume fail: rowCount outside [" + minRows + "," + maxRows + "]");
    }
}
`;
FX.delimStructuralOk = `
class FxDelimStructural {
    void m(String a, String b, java.util.Map<java.util.List<String>, String> m) {
        m.put(java.util.List.of(a, b), "v");
    }
}
`;

FX.bigdecViolation = `
class FxBigdecViolation {
    java.math.BigDecimal toBigDecimal(Object v) {
        if (v instanceof Number) {
            return BigDecimal.valueOf(((Number) v).doubleValue());
        }
        return null;
    }
}
`;
FX.bigdecProtected = `
class FxBigdecProtected {
    java.math.BigDecimal toBigDecimal(Object v) {
        if (v instanceof Number) {
            Number n = (Number) v;
            if (n instanceof Long || n instanceof Integer) {
                return java.math.BigDecimal.valueOf(n.longValue());
            }
            return java.math.BigDecimal.valueOf(n.doubleValue());
        }
        return null;
    }
}
`;
FX.bigdecNewViolation = `
class FxBigdecNew {
    java.math.BigDecimal toBigDecimal(Object v) {
        if (v instanceof Number) {
            return new BigDecimal(((Number) v).doubleValue());
        }
        return null;
    }
}
`;

function fixtureDetect(label, sample, expectedFamilies) {
    const relPath = `fixture/${label}.java`;
    const masked = maskCommentsAndStrings(sample);
    const stripped = stripCommentsOnly(sample);
    const hits = [];
    hits.push(...scanLocale(masked, sample, relPath));
    hits.push(...scanNarrowingCast(masked, sample, relPath));
    hits.push(...scanContainsClassify(masked, sample, relPath));
    hits.push(...scanDelimKey(stripped, sample, relPath));
    hits.push(...scanBigdec(masked, sample, relPath));
    const famCount = {};
    for (const h of hits) famCount[h.family] = (famCount[h.family] || 0) + 1;
    let pass = true;
    for (const [fam, expected] of Object.entries(expectedFamilies)) {
        const actual = famCount[fam] || 0;
        if (actual !== expected) pass = false;
    }
    // No unexpected families
    for (const fam of Object.keys(famCount)) {
        if (!(fam in expectedFamilies)) pass = false;
    }
    return { sample: label, famCount, expectedFamilies, pass };
}

function runFixture() {
    const results = [];
    results.push(fixtureDetect('locale-violation', FX.localeViolation, { locale: 1 }));
    results.push(fixtureDetect('locale-compliant', FX.localeCompliant, {}));
    results.push(fixtureDetect('locale-javadoc-pseudo', FX.localeJavadocPseudo, {}));
    results.push(fixtureDetect('narrow-violation', FX.narrowViolation, { 'narrowing-cast': 1 }));
    results.push(fixtureDetect('narrow-compliant-paren', FX.narrowCompliantParen, {}));
    results.push(fixtureDetect('narrow-widening-int', FX.narrowWidening, {}));
    results.push(fixtureDetect('contains-violation', FX.containsViolation, { 'contains-classify': 1 }));
    results.push(fixtureDetect('contains-set-ok', FX.containsSetOk, {}));
    results.push(fixtureDetect('contains-unknown-receiver', FX.containsUnknownReceiver, {}));
    results.push(fixtureDetect('delim-inline-key', FX.delimInlineKey, { 'delim-key': 1 }));
    results.push(fixtureDetect('delim-key-var', FX.delimKeyVar, { 'delim-key': 1 }));
    results.push(fixtureDetect('delim-message-ok', FX.delimMessageOk, {}));
    results.push(fixtureDetect('delim-structural-ok', FX.delimStructuralOk, {}));
    results.push(fixtureDetect('bigdec-violation', FX.bigdecViolation, { 'bigdec-precision': 1 }));
    results.push(fixtureDetect('bigdec-protected-routed', FX.bigdecProtected, {}));
    results.push(fixtureDetect('bigdec-new-violation', FX.bigdecNewViolation, { 'bigdec-precision': 1 }));

    // Allowed-comment mechanism: a hit on a line with `// invariant-ok:` moves
    // out of the violation set and out of baseline input, but stays visible.
    const allowedSample = FX.localeViolation.replace(
        'String k = x.toLowerCase();',
        'String k = x.toLowerCase(); // invariant-ok: adjudication-table-cycle2#L7'
    );
    const rawByFile = new Map([['fixture/allowed.java', allowedSample]]);
    const allowedHits = scanLocale(maskCommentsAndStrings(allowedSample), allowedSample, 'fixture/allowed.java');
    const { violations, allowed } = splitAllowed(allowedHits, rawByFile);
    const allowedPass = violations.length === 0 && allowed.length === 1;

    // Baseline reconciliation fixtures:
    // subset (current <= baseline per key) => green; excess/new key => red.
    const h1 = { file: 'a/A.java', family: 'locale', snippet: 'String k = x.toLowerCase();' };
    const h2 = { file: 'a/A.java', family: 'locale', snippet: 'String k = x.toLowerCase();' };
    const h3 = { file: 'a/B.java', family: 'delim-key', snippet: 'visited.add(a + "#" + b);' };
    const baseline = { entries: [
        { file: 'a/A.java', family: 'locale', text: 'String k = x.toLowerCase();', count: 2 },
        { file: 'a/B.java', family: 'delim-key', text: 'visited.add(a + "#" + b);', count: 1 },
    ] };
    const subsetRes = reconcile([h1, h2, h3], baseline);           // exactly at baseline counts
    const shrinkRes = reconcile([h1], baseline);                    // fewer than baseline (fix period)
    const excessRes = reconcile([h1, h2, h1, h3], baseline);        // same key 3x > baseline 2
    const newKeyRes = reconcile([h1, { file: 'a/C.java', family: 'locale', snippet: 'new site' }], baseline);
    const baselinePass = subsetRes.excess.length === 0
        && shrinkRes.excess.length === 0
        && excessRes.excess.length === 1
        && newKeyRes.excess.length === 1;

    const allPass = results.every(r => r.pass) && allowedPass && baselinePass;

    console.log('Self-verification fixture: ' + (allPass ? 'PASS' : 'FAIL'));
    console.log('');
    for (const r of results) {
        const status = r.pass ? 'OK' : 'FAIL';
        const detail = Object.entries(r.expectedFamilies).map(([f, c]) => `${f}=${c}`).join(',') || 'no-hit';
        const actual = Object.entries(r.famCount).map(([f, c]) => `${f}=${c}`).join(',') || 'none';
        console.log(`  [${status}] ${r.sample}: expected {${detail}}, actual {${actual}}`);
    }
    console.log(`  [${allowedPass ? 'OK' : 'FAIL'}] allowed-comment: violation=${violations.length}, allowed=${allowed.length} (expect 0/1, allowed listed not silent)`);
    console.log(`  [${baselinePass ? 'OK' : 'FAIL'}] baseline-reconcile: subset=${subsetRes.excess.length===0}, shrink=${shrinkRes.excess.length===0}, excess=${excessRes.excess.length===1}, new-key=${newKeyRes.excess.length===1}`);
    console.log('');
    if (!allPass) {
        console.log('FIXTURE FAILED: scanner cannot distinguish violation from compliant sample, or reconciliation semantics wrong.');
    }
    return allPass ? 0 : 1;
}

// ============================================================
// Main
// ============================================================

function main() {
    const args = parseArgs(process.argv);

    if (args.help) {
        console.log('Usage: node ai-dev/tools/check-silent-wrong-result.mjs --module nop-metadata');
        console.log('       node ai-dev/tools/check-silent-wrong-result.mjs --module nop-metadata --rule <family>');
        console.log('       node ai-dev/tools/check-silent-wrong-result.mjs --module nop-metadata --baseline <file>');
        console.log('       node ai-dev/tools/check-silent-wrong-result.mjs --module nop-metadata --emit-baseline');
        console.log('       node ai-dev/tools/check-silent-wrong-result.mjs --fixture');
        console.log('');
        console.log(`Rules (families): ${RULES.join(', ')}`);
        console.log('Options:');
        console.log('  --module <name>     Scan only the specified module (service src/main)');
        console.log('  --rule <family>     Only run the specified rule');
        console.log('  --format <fmt>      Output format: summary (default), json');
        console.log('  --baseline <file>   Reconcile hits against a snapshot baseline (mode b)');
        console.log('  --emit-baseline     Print the baseline JSON for the current hits and exit 0');
        console.log('  --fixture           Run self-verification fixture');
        process.exit(0);
    }

    if (args.fixture) {
        process.exit(runFixture());
    }

    const repoRoot = findRepoRoot();
    let scanPaths;
    if (args.module) {
        const moduleServicePath = path.join(repoRoot, args.module, args.module + '-service', 'src', 'main', 'java');
        if (fs.existsSync(moduleServicePath)) {
            scanPaths = [moduleServicePath];
        } else {
            scanPaths = [path.join(repoRoot, args.module)];
        }
    } else {
        scanPaths = [repoRoot];
    }

    const hits = scanAll(scanPaths, args.rule);

    const rawTextByFile = new Map();
    for (const scanPath of scanPaths) {
        for (const f of walkJavaFiles(scanPath, [])) {
            rawTextByFile.set(relativePath(f), fs.readFileSync(f, 'utf-8'));
        }
    }
    const { violations, allowed } = splitAllowed(hits, rawTextByFile);

    if (args.emitBaseline) {
        console.log(JSON.stringify(buildBaselineJson(violations), null, 2));
        process.exit(0);
    }

    const perFamily = {};
    for (const h of violations) perFamily[h.family] = (perFamily[h.family] || 0) + 1;

    let baselineResult = null;
    if (args.baseline) {
        const baseline = loadBaseline(args.baseline);
        baselineResult = reconcile(violations, baseline);
    }

    switch (args.format) {
        case 'json':
            console.log(JSON.stringify({
                invariant: 'INV-SILENT-WRONG-RESULT',
                generated: new Date().toISOString(),
                rules: RULES,
                hitCount: violations.length,
                perFamily,
                hits: violations,
                allowed,
                baseline: baselineResult,
            }, null, 2));
            break;
        default:
            console.log(generateSummary(violations, allowed, perFamily, baselineResult));
    }

    if (baselineResult) {
        process.exit(baselineResult.excess.length > 0 ? 1 : 0);
    }
    process.exit(violations.length > 0 ? 1 : 0);
}

main();
