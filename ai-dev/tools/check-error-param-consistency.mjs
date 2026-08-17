#!/usr/bin/env node

/**
 * check-error-param-consistency.mjs
 *
 * Invariant INV-ERROR-PARAM guard (plan 2026-08-15-1913-3, Cycle 3 / P1-6 + P1-7).
 *
 * Detects "identifiable placeholder rendered literally" defects: a
 * `new NopMetadataException(...)` throw site whose ErrorCode description declares
 * an identity placeholder `{xxx}` that has no matching `.param("xxx", ...)` /
 * `.param(ARG_XXX, ...)` key in the same statement chain. At runtime
 * (ErrorMessageManager) such a placeholder renders as the literal text
 * `{xxx}` — the failing object's identity is lost to the end user.
 *
 * Detection pipeline:
 *   1. Registry build — parse `NopMetadataArgs.java` (ARG_* -> key string) and
 *      the 10 `*Errors.java` sub-interfaces composed by `NopMetadataErrors`
 *      (Aggregation/DataSource/Field/Join/Lineage/Misc/Module/Quality/Recon/Sql).
 *      Each `ErrorCode ERR_X = ErrorCode.define("code", "desc " + "...", ARG_..)`
 *      entry yields the set of `{placeholder}` tokens extracted from the
 *      concatenated description string literals, plus the tail ARG_* names
 *      declared after the description literal (define-face rule, P2-10).
 *   1b. Define-face consistency (P2-10) — for every registry define, the set of
 *      declared ARG_* VALUES (resolved through the args registry; constant
 *      NAMES are UPPER_SNAKE, values are the camelCase keys) must equal the
 *      set of `{placeholder}` tokens in the description (symmetric difference
 *      must be empty). A declared-but-unrendered key and a rendered-but-
 *      undeclared placeholder are both violations: at render time the first
 *      silently drops an identity the throw site passed, the second renders a
 *      literal `{xxx}` unless the throw site happens to pass the key (which
 *      only the throw-site scan would notice). Unresolvable ARG_* tail names
 *      and non-ARG ALL_CAPS tail params are violations too.
 *   1c. Dead-define detection (P2-10) — a registry define with zero
 *      references in the module's src/main (comment-stripped corpus,
 *      excluding the *Errors.java definition files themselves) is dead.
 *      Two sub-kinds: `dead` (zero references anywhere incl. tests and the
 *      rest of the repo) and `test-only` (kept alive only by test mirrors —
 *      production has no consumer). Both are violations. A define referenced
 *      from another module's code stays alive (NopMetadataErrors is public).
 *      Known blind spot, documented rather than hidden: a test-mirror entry
 *      that references the constant only inside a STRING (not as an identifier)
 *      would evade the corpus regex; the mirrors in this module reference the
 *      constants directly, so the blind spot is empty as of 2026-08-16.
 *   2. Throw-site scan — for every `new NopMetadataException(` in src/main
 *      (comment-stripped text, string literals preserved):
 *        - resolve the first constructor argument (error code reference):
 *          `NopMetadataErrors.ERR_X` (qualified), bare `ERR_X` (classes
 *          implementing NopMetadataErrors), inline `ErrorCode.define(...)`
 *          (locally parsed), anything else (method parameter / local variable)
 *          => UNRESOLVED;
 *        - resolve every `.param(key, value)` key on the statement chain
 *          (up to the terminating `;`): string literal, qualified constant
 *          `NopMetadataErrors.ARG_X`, or bare constant `ARG_X`;
 *        - HIT iff any non-exempt description placeholder has no matching key.
 *
 * False-positive / blind-spot adjudications (hard requirements from the plan;
 * verified live 2026-08-15):
 *   (a) catch-block `e.param(...)` re-throw augmentation (5 live sites, e.g.
 *       AggregationHelper.java:170, ExternalAggregationProcessor.java:52) can
 *       never hit: this scanner only inspects `new NopMetadataException(`
 *       sites — parameter augmentation of an existing exception is out of
 *       scope (the upstream throw site owns the placeholder coverage).
 *   (b) `{error}` placeholders are NO LONGER EXEMPT (P2-09 closed 2026-08-16,
 *       plan 2026-08-16-0226-2 Phase 2): all 7 annotation-family throw sites
 *       pass `.param(ARG_ERROR, NopMetadataHelper.toErrorMessage(e))` (or the
 *       file-local messageOf equivalent), so the `-- {error}` suffix family is
 *       under full hard-gate coverage — a missing error param renders the
 *       literal `{error}` and is a violation like any identity placeholder.
 *   (c) dead-code throw sites are exempted via `// invariant-ok:` annotation
 *       (P2-23 dead-code deletion will remove the exemption with the code).
 *       Define-face violations (1b/1c) use the same `// invariant-ok:`
 *       annotation mechanism on the `ErrorCode ERR_X =` line (exemption list
 *       form, mirroring the baseline precedent of check-silent-wrong-result:
 *       exempted entries stay visible under "Allowed (adjudicated)").
 *   (d) variable-form error codes (error code is a method parameter / local
 *       variable — live: MetaTableFieldResolver.java:214/223/234
 *       `errOnInvalid`, NopMetaLineageEdgeBizModel.java:115 `errorCode`) are
 *       statically unresolvable. They are NOT silently skipped: the scanner
 *       emits an UNRESOLVED list, and every UNRESOLVED site must either be
 *       fixed to a resolvable constant reference or carry a
 *       `// invariant-ok: <adjudication reference>` annotation documenting the
 *       manual call-site -> error-code mapping verification. An unannotated
 *       UNRESOLVED site is a violation (forces adjudication).
 *
 * Allowed-comment mechanism (same as check-silent-wrong-result.mjs):
 *   a raw source line containing `// invariant-ok: <reference>` suppresses
 *   the violation on a throw site starting on that same line. Suppressed
 *   entries are still printed under "Allowed (adjudicated)" so no silent
 *   blind spot is created.
 *
 * Exit mode (adjudicated in plan Phase 1): ZERO-HIT HARD GATE —
 *   0 = no violations AND no unannotated UNRESOLVED sites AND no
 *       unexempted define-face / dead-define findings
 *   1 = violations found (missing placeholders, unresolvable .param keys,
 *       unannotated variable-form throw sites, define-face drift, dead or
 *       test-only defines)
 *   2 = internal error
 *
 * Usage:
 *   node ai-dev/tools/check-error-param-consistency.mjs --module nop-metadata
 *   node ai-dev/tools/check-error-param-consistency.mjs --module nop-metadata --format json
 *   node ai-dev/tools/check-error-param-consistency.mjs --fixture
 */

import fs from 'fs';
import path from 'path';

const EXEMPT_PLACEHOLDERS = new Set(); // (b) P2-09 {error} exemption CLOSED 2026-08-16:
// every `-- {error}` annotation family throw site now passes
// .param(ARG_ERROR, ...) — missing-error-param renders literally and is a HIT.
const EXCEPTION_CTORS = ['NopMetadataException'];

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
 * Strip comments only (line + block), keep string/char literals verbatim
 * (same state machine as check-silent-wrong-result.mjs).
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
    // Same-line trailing annotation, or a standalone `// invariant-ok:` comment
    // within the contiguous `//` comment block directly above the throw
    // statement (allows wrapped multi-line adjudication references while
    // keeping the 80-column checkstyle limit).
    if (lines[idx] !== undefined && lines[idx].includes('// invariant-ok:')) {
        return true;
    }
    for (let i = idx - 1; i >= 0; i--) {
        const trimmed = lines[i].trim();
        if (!trimmed.startsWith('//')) {
            return false; // comment block ended
        }
        if (trimmed.startsWith('// invariant-ok:')) {
            return true;
        }
    }
    return false;
}

function extractStringLiterals(spanText) {
    const literals = [];
    let i = 0;
    while (i < spanText.length) {
        if (spanText[i] === '"') {
            let j = i + 1;
            let value = '';
            while (j < spanText.length && spanText[j] !== '"') {
                if (spanText[j] === '\\' && j + 1 < spanText.length) {
                    value += spanText[j] + spanText[j + 1];
                    j += 2;
                } else {
                    value += spanText[j];
                    j++;
                }
            }
            if (j < spanText.length) {
                literals.push(value);
                i = j + 1;
            } else {
                break;
            }
        } else {
            i++;
        }
    }
    return literals;
}

/**
 * Walk a statement from an opening paren until the terminating `;` at
 * paren depth 0 (string-aware). Returns { spanEnd, firstArgEnd }.
 * firstArgEnd = offset of the comma at depth 1 (or closing paren) that ends
 * the first constructor argument.
 */
function walkStatement(text, openParen) {
    let depth = 1; // we are inside the ctor's opening paren
    let i = openParen + 1;
    let firstArgEnd = -1;
    let state = 'code';
    const len = text.length;
    let guard = 0;
    while (i < len && guard++ < 20000) {
        const ch = text[i];
        if (state === 'string' || state === 'char') {
            const quote = state === 'string' ? '"' : "'";
            if (ch === '\\') {
                i += 2;
                continue;
            }
            if (ch === quote) state = 'code';
            i++;
            continue;
        }
        if (ch === '"') { state = 'string'; i++; continue; }
        if (ch === "'") { state = 'char'; i++; continue; }
        if (ch === '(' || ch === '[') {
            depth++;
        } else if (ch === ')' || ch === ']') {
            depth--;
            if (depth === 0) {
                if (firstArgEnd === -1) firstArgEnd = i;
                // ctor closing paren reached; keep walking for chained .param(...)
            }
        } else if (ch === ',' && depth === 1 && firstArgEnd === -1) {
            firstArgEnd = i;
        } else if (ch === ';' && depth === 0) {
            return { spanEnd: i, firstArgEnd: firstArgEnd === -1 ? i : firstArgEnd };
        }
        i++;
    }
    return null; // malformed / unterminated — caller must not silently pass
}

// ============================================================
// Registry: ARG_* constants + ErrorCode defines
// ============================================================

function parseArgsRegistry(fileContent) {
    const map = new Map();
    const re = /String\s+(ARG_[A-Z0-9_]+)\s*=\s*"((?:[^"\\]|\\.)*)"\s*;/g;
    let m;
    while ((m = re.exec(fileContent)) !== null) {
        map.set(m[1], m[2]);
    }
    return map;
}

function placeholdersOf(description) {
    const set = new Set();
    const re = /\{([A-Za-z_][A-Za-z0-9_]*)\}/g;
    let m;
    while ((m = re.exec(description)) !== null) {
        set.add(m[1]);
    }
    return set;
}

/**
 * Identifiers between the last description string literal and the define's
 * closing paren — the declared tail params (live form: `ARG_X, ARG_Y`).
 */
function parseDefineTailIds(spanText) {
    const lastQuote = spanText.lastIndexOf('"');
    const closeParen = spanText.lastIndexOf(')');
    if (lastQuote === -1 || closeParen === -1 || closeParen < lastQuote) return [];
    const tail = spanText.substring(lastQuote + 1, closeParen);
    const ids = [];
    const re = /[A-Za-z_$][\w$]*/g;
    let m;
    while ((m = re.exec(tail)) !== null) ids.push(m[0]);
    return ids;
}

/**
 * Parse `ErrorCode ERR_X = ErrorCode.define("code", "desc" + "...", ARG_..);`
 * entries from an *Errors.java file. Description = concatenation of all string
 * literals after the first (the code string). Tail ARG_* names are recorded
 * for the define-face symmetric-difference rule (P2-10).
 */
function parseErrorRegistry(fileContentRaw, filePath) {
    const map = new Map();
    const fileContent = stripCommentsOnly(fileContentRaw);
    const relPath = relativePath(filePath);
    const startRe = /ErrorCode\s+(ERR_[A-Z0-9_]+)\s*=/g;
    let m;
    while ((m = startRe.exec(fileContent)) !== null) {
        const name = m[1];
        const walk = walkStatement(fileContent, fileContent.indexOf('(', m.index));
        if (walk === null) continue;
        // walkStatement walked from the define( open paren; rebuild span text
        const spanText = fileContent.substring(m.index, walk.spanEnd + 1);
        if (!spanText.includes('ErrorCode.define')) continue;
        const literals = extractStringLiterals(spanText);
        if (literals.length < 2) continue;
        const description = literals.slice(1).join('');
        map.set(name, {
            code: literals[0],
            description,
            placeholders: placeholdersOf(description),
            tailIds: parseDefineTailIds(spanText),
            file: relPath,
            line: lineOfOffset(fileContent, m.index),
        });
    }
    return map;
}

function buildRegistry(javaFiles) {
    const argsMap = new Map();
    const errorMap = new Map();
    for (const f of javaFiles) {
        const base = path.basename(f);
        const content = fs.readFileSync(f, 'utf-8');
        if (base === 'NopMetadataArgs.java') {
            for (const [k, v] of parseArgsRegistry(content)) argsMap.set(k, v);
        } else if (/^[A-Za-z]+Errors\.java$/.test(base)) {
            // local ARG_* declarations inside Errors files resolve first
            // (they extend NopMetadataArgs but may add their own constants)
            for (const [k, v] of parseArgsRegistry(content)) argsMap.set(k, v);
            for (const [k, v] of parseErrorRegistry(content, f)) errorMap.set(k, v);
        }
    }
    return { argsMap, errorMap };
}

// ============================================================
// Throw-site scan
// ============================================================

function resolveKeyExpr(keyExpr, argsMap) {
    const expr = keyExpr.trim();
    if (expr.startsWith('"')) {
        const m = expr.match(/^"((?:[^"\\]|\\.)*)"/);
        return m ? { key: m[1] } : { key: null, reason: 'unparseable string literal key' };
    }
    if (/^[A-Za-z_$][\w.$]*$/.test(expr)) {
        const simple = expr.split('.').pop();
        if (argsMap.has(simple)) {
            return { key: argsMap.get(simple) };
        }
        return { key: null, reason: `non-ARG constant/expression key '${expr}'` };
    }
    return { key: null, reason: `unresolvable .param key '${expr}'` };
}

function resolveErrorCodeRef(firstArg, errorMap) {
    const expr = firstArg.trim();
    if (expr.startsWith('"')) {
        return { kind: 'skip', reason: 'string-message constructor (no ErrorCode)' };
    }
    if (expr.includes('ErrorCode.define(')) {
        const openParen = firstArg.indexOf('ErrorCode.define(');
        const walk = walkStatement(firstArg, firstArg.indexOf('(', openParen + 'ErrorCode.define'.length));
        const spanText = walk ? firstArg.substring(openParen, (walk.firstArgEnd === -1 ? firstArg.length : walk.firstArgEnd)) : firstArg;
        const literals = extractStringLiterals(spanText);
        if (literals.length >= 2) {
            const description = literals.slice(1).join('');
            return { kind: 'inline', placeholders: placeholdersOf(description), detail: literals[0] };
        }
        return { kind: 'unresolved', detail: 'inline ErrorCode.define not parseable' };
    }
    let m = expr.match(/^NopMetadataErrors\.([A-Za-z_$][\w$]*)$/);
    if (m && errorMap.has(m[1])) {
        const def = errorMap.get(m[1]);
        return { kind: 'constant', placeholders: def.placeholders, detail: m[1] };
    }
    m = expr.match(/^([A-Z][A-Z0-9_]*)$/);
    if (m && errorMap.has(m[1])) {
        const def = errorMap.get(m[1]);
        return { kind: 'constant', placeholders: def.placeholders, detail: m[1] + ' (bare)' };
    }
    return { kind: 'unresolved', detail: expr };
}

/**
 * Builder-form handling: `NopMetadataException e = new NopMetadataException(ERR);`
 * followed by separate `e.param(k, v)` statements (e.g.
 * MetaDataSourceConnectionProcessor.blocked()). When the ctor is a local
 * variable assignment, collect `.param(...)` keys applied to that variable
 * within the remainder of the enclosing method (approximated as the text up to
 * the next 4-space closing brace). Without this, the split builder pattern
 * would be a false positive (params are passed, just not chained inline).
 */
function collectBuilderVarParams(stripped, siteStart, spanEnd, registry) {
    const assignMatch = stripped.substring(Math.max(0, siteStart - 200), siteStart)
        .match(/([A-Za-z_$][\w$]*)\s*=\s*$/);
    if (!assignMatch) return [];
    const varName = assignMatch[1];
    const after = stripped.substring(spanEnd + 1);
    const methodEnd = after.search(/\n    \}/);
    const window = after.substring(0, methodEnd === -1 ? 2000 : methodEnd);
    const keyExprs = [];
    const re = new RegExp(`\\b${varName}\\.param\\s*\\(`, 'g');
    let m;
    while ((m = re.exec(window)) !== null) {
        const openParen = window.indexOf('(', m.index);
        const walk = walkStatement(window, openParen);
        if (walk === null) continue;
        const stmtText = window.substring(m.index, walk.spanEnd + 1);
        const keyRe = /\.param\s*\(\s*("(?:[^"\\]|\\.)*"|[\w.$]+)/g;
        let km;
        while ((km = keyRe.exec(stmtText)) !== null) {
            keyExprs.push(km[1]);
        }
    }
    return keyExprs;
}

function scanFileText(relPath, stripped, raw, registry) {
    const hits = [];
    const unresolved = [];
    const ctorAlt = EXCEPTION_CTORS.join('|');
    const siteRe = new RegExp(`new\\s+(?:${ctorAlt})\\s*\\(`, 'g');
    let m;
    while ((m = siteRe.exec(stripped)) !== null) {
        const openParen = stripped.indexOf('(', m.index);
        const walk = walkStatement(stripped, openParen);
        const line = lineOfOffset(stripped, m.index);
        if (walk === null) {
            hits.push({
                file: relPath, line, kind: 'internal', missing: [], keyProblems: [],
                snippet: snippetAt(raw, line),
                rule: 'INTERNAL: unbalanced parens — could not extract throw statement span (INV-ERROR-PARAM)',
            });
            continue;
        }
        const firstArg = stripped.substring(openParen + 1, walk.firstArgEnd);
        const statementText = stripped.substring(m.index, walk.spanEnd + 1);
        const ref = resolveErrorCodeRef(firstArg, registry.errorMap);

        if (ref.kind === 'skip') continue;

        // Resolve every .param() key on the statement chain.
        const keyRe = /\.param\s*\(\s*("(?:[^"\\]|\\.)*"|[\w.$]+)/g;
        const keys = new Set();
        const keyProblems = [];
        let km;
        while ((km = keyRe.exec(statementText)) !== null) {
            const resolved = resolveKeyExpr(km[1], registry.argsMap);
            if (resolved.key === null) {
                keyProblems.push(resolved.reason);
            } else {
                keys.add(resolved.key);
            }
        }

        if (ref.kind === 'unresolved') {
            unresolved.push({
                file: relPath, line, kind: 'unresolved',
                detail: ref.detail, keys: [...keys],
                snippet: snippetAt(raw, line),
            });
            continue;
        }

        let missing = [...ref.placeholders].filter(
            (p) => !EXEMPT_PLACEHOLDERS.has(p) && !keys.has(p));
        if (missing.length > 0) {
            // Builder-form: params may be attached to the assigned variable in
            // subsequent statements — collect and re-check before reporting.
            const builderKeys = collectBuilderVarParams(stripped, m.index, walk.spanEnd, registry);
            for (const keyExpr of builderKeys) {
                const resolved = resolveKeyExpr(keyExpr, registry.argsMap);
                if (resolved.key !== null) keys.add(resolved.key);
            }
            missing = missing.filter((p) => !EXEMPT_PLACEHOLDERS.has(p) && !keys.has(p));
        }
        if (missing.length > 0 || keyProblems.length > 0) {
            hits.push({
                file: relPath, line, kind: 'hit',
                detail: ref.detail, missing, keyProblems,
                snippet: snippetAt(raw, line),
                rule: missing.length > 0
                    ? `placeholder(s) {${missing.join('}, {')}} declared by ${ref.detail} have no .param key — renders literally (INV-ERROR-PARAM)`
                    : keyProblems.join('; '),
            });
        }
    }
    return { hits, unresolved };
}

function scanFile(filePath, registry) {
    const relPath = relativePath(filePath);
    const raw = fs.readFileSync(filePath, 'utf-8');
    const stripped = stripCommentsOnly(raw);
    return scanFileText(relPath, stripped, raw, registry);
}

// ============================================================
// Define-face consistency + dead-define detection (P2-10)
// ============================================================

function isDefinitionFile(base) {
    return base === 'NopMetadataErrors.java' || base === 'NopMetadataArgs.java'
        || /^[A-Za-z]+Errors\.java$/.test(base);
}

function concatStrippedCorpus(files) {
    let s = '';
    for (const f of files) {
        s += stripCommentsOnly(fs.readFileSync(f, 'utf-8'));
        s += '\n';
    }
    return s;
}

function walkAllJavaFiles(dir, results, excludeDirs) {
    if (!fs.existsSync(dir)) return results;
    const entries = fs.readdirSync(dir, { withFileTypes: true });
    for (const entry of entries) {
        const fullPath = path.join(dir, entry.name);
        if (entry.isDirectory()) {
            if (['node_modules', '.git', 'target', '_gen'].includes(entry.name)) continue;
            if (excludeDirs && excludeDirs.some((d) => fullPath === d || fullPath.startsWith(d + path.sep))) continue;
            walkAllJavaFiles(fullPath, results, excludeDirs);
        } else if (entry.isFile() && entry.name.endsWith('.java')) {
            results.push(fullPath);
        }
    }
    return results;
}

/**
 * Symmetric difference: declared ARG_* VALUES vs description placeholders.
 * Constant NAMES are UPPER_SNAKE (`ARG_META_TABLE_ID`), VALUES are the
 * camelCase keys (`metaTableId`) — comparison happens on values.
 */
function checkDefineFace(registry) {
    const violations = [];
    for (const [name, def] of registry.errorMap) {
        if (!def.tailIds) continue; // legacy fixture entries without tail info
        const declaredValues = new Set();
        const argProblems = [];
        for (const id of def.tailIds) {
            if (registry.argsMap.has(id)) {
                declaredValues.add(registry.argsMap.get(id));
            } else if (id.startsWith('ARG_')) {
                argProblems.push(`unresolvable ARG constant ${id} (no String value declaration)`);
            } else if (/^[A-Z][A-Z0-9_]*$/.test(id)) {
                argProblems.push(`non-ARG ALL_CAPS tail param ${id}`);
            }
        }
        const missingDecl = [...def.placeholders].filter((p) => !declaredValues.has(p));
        const missingPh = [...declaredValues].filter((v) => !def.placeholders.has(v));
        if (missingDecl.length > 0 || missingPh.length > 0 || argProblems.length > 0) {
            const parts = [];
            if (missingDecl.length > 0) {
                parts.push(`desc placeholder(s) {${missingDecl.join('}, {')}} not declared by any tail ARG`);
            }
            if (missingPh.length > 0) {
                parts.push(`declared key(s) {${missingPh.join('}, {')}} have no desc placeholder (param passed at throw site is never rendered)`);
            }
            parts.push(...argProblems);
            violations.push({
                file: def.file, line: def.line, kind: 'define-face',
                name, missing: [...missingDecl, ...missingPh], keyProblems: argProblems,
                snippet: def.description,
                rule: `define-face drift on ${name}: ${parts.join('; ')} (INV-ERROR-PARAM/define)`,
            });
        }
    }
    return violations;
}

/**
 * Dead defines: zero references in module src/main (stripped corpus, excluding
 * the definition files). `dead` = zero refs anywhere (when the repo corpus is
 * provided); `test-only` = kept alive only by test mirrors. Runs in two
 * phases: without the repo corpus the findings are provisional (module-local
 * view); the caller re-runs with the repo corpus to confirm — a candidate
 * referenced anywhere else in the repo is production-alive and dropped.
 * Exemption: `// invariant-ok:` on the define line.
 */
function checkDeadDefines(registry, corpora) {
    const violations = [];
    for (const [name, def] of registry.errorMap) {
        if (!def.file) continue; // legacy fixture entries without source info
        const re = new RegExp(`\\b${name}\\b`);
        if (re.test(corpora.moduleMain)) continue; // production-alive
        if (corpora.repoAll !== '' && re.test(corpora.repoAll)) continue; // alive elsewhere
        const testOnly = corpora.moduleTest !== '' && re.test(corpora.moduleTest);
        violations.push({
            file: def.file, line: def.line,
            kind: testOnly ? 'test-only-define' : 'dead-define',
            name, missing: [], keyProblems: [],
            snippet: def.description,
            rule: testOnly
                ? `test-only define ${name}: referenced from tests only, no src/main consumer (INV-ERROR-PARAM/define)`
                : `dead define ${name}: zero references in src/main, tests, and the rest of the repo (INV-ERROR-PARAM/define)`,
        });
    }
    return violations;
}

function buildDeadCheckCorpora(repoRoot, scanPaths, module) {
    const moduleRoot = module
        ? (fs.existsSync(path.join(repoRoot, module, module + '-service'))
            ? path.join(repoRoot, module, module + '-service')
            : path.join(repoRoot, module))
        : null;
    const corpusFiles = (dir) => walkAllJavaFiles(dir, [])
        .filter((f) => !isDefinitionFile(path.basename(f)));
    const moduleMainFiles = moduleRoot ? corpusFiles(path.join(moduleRoot, 'src', 'main', 'java')) : scanPaths.flatMap((p) => corpusFiles(p));
    const moduleTestDir = moduleRoot ? path.join(moduleRoot, 'src', 'test', 'java') : null;
    const moduleTestFiles = moduleTestDir ? corpusFiles(moduleTestDir) : [];
    return {
        moduleMain: concatStrippedCorpus(moduleMainFiles),
        moduleTest: concatStrippedCorpus(moduleTestFiles),
        // lazy repo corpus: the repo-wide confirmation must NOT re-count the
        // module's own test references (they are already classified as
        // test-only), so the module test dir is excluded from the walk
        repoAll: '',
        _buildRepoCorpus: () => {
            const excludeDirs = moduleTestDir ? [moduleTestDir] : [];
            const all = walkAllJavaFiles(repoRoot, [], excludeDirs)
                .filter((f) => !isDefinitionFile(path.basename(f)));
            return concatStrippedCorpus(all);
        },
    };
}

// ============================================================
// Allowed-comment split + summary
// ============================================================

function splitAllowed(entries, rawTextByFile) {
    const violations = [];
    const allowed = [];
    for (const e of entries) {
        const rawText = rawTextByFile.get(e.file);
        if (rawText !== undefined && hasInvariantOk(rawText, e.line)) {
            allowed.push({ ...e, allowed: true });
        } else {
            violations.push(e);
        }
    }
    return { violations, allowed };
}

function generateSummary(hits, hitAllowed, unresolvedViolations, unresolvedAllowed,
    defineViolations, defineAllowed) {
    const lines = [];
    lines.push('# Error Param Consistency Scan Report (INV-ERROR-PARAM)');
    lines.push(`Generated: ${new Date().toISOString().split('T')[0]}`);
    lines.push('');
    lines.push(`Missing-placeholder violations: ${hits.length}`);
    lines.push(`Unannotated UNRESOLVED (variable-form) throw sites: ${unresolvedViolations.length}`);
    lines.push(`Define-face violations (P2-10): ${defineViolations.length}`);
    lines.push(`Allowed via // invariant-ok: hits=${hitAllowed.length}, unresolved=${unresolvedAllowed.length}, define-face=${defineAllowed.length}`);
    lines.push('');
    if (hits.length > 0) {
        lines.push('## Violations — identity placeholders without .param key');
        lines.push('');
        lines.push('| # | File:Line | Missing | Snippet |');
        lines.push('|---|-----------|---------|---------|');
        hits.forEach((h, i) => {
            const missingText = h.missing && h.missing.length
                ? h.missing.map((p) => `{${p}}`).join(' ')
                : (h.keyProblems || []).join('; ');
            lines.push(`| ${i + 1} | \`${h.file}:${h.line}\` | ${missingText} | \`${h.snippet}\` |`);
        });
        lines.push('');
    }
    if (defineViolations.length > 0) {
        lines.push('## Define-face violations — declared ARG values XOR description placeholders / dead defines');
        lines.push('');
        lines.push('| # | File:Line | Kind | Finding |');
        lines.push('|---|-----------|------|---------|');
        defineViolations.forEach((d, i) => {
            lines.push(`| ${i + 1} | \`${d.file}:${d.line}\` | ${d.kind} | ${d.rule} |`);
        });
        lines.push('');
    }
    if (unresolvedViolations.length > 0) {
        lines.push('## UNRESOLVED throw sites requiring adjudication (variable-form error code)');
        lines.push('');
        lines.push('Every site must be verified against its call-site error-code mapping and either');
        lines.push('fixed to a resolvable constant, or annotated `// invariant-ok: <ref>`.');
        lines.push('');
        for (const u of unresolvedViolations) {
            lines.push(`- \`${u.file}:${u.line}\` error-code expr: \`${u.detail}\` | params: [${u.keys.join(', ')}] | \`${u.snippet}\``);
        }
        lines.push('');
    }
    if (hitAllowed.length > 0 || unresolvedAllowed.length > 0 || defineAllowed.length > 0) {
        lines.push('## Allowed (adjudicated via // invariant-ok — NOT counted, listed to avoid silent blind spots)');
        lines.push('');
        for (const a of hitAllowed) {
            lines.push(`- \`${a.file}:${a.line}\` missing {${(a.missing || []).join('}, {')}} — \`${a.snippet}\``);
        }
        for (const a of unresolvedAllowed) {
            lines.push(`- \`${a.file}:${a.line}\` variable-form \`${a.detail}\` — \`${a.snippet}\``);
        }
        for (const a of defineAllowed) {
            lines.push(`- \`${a.file}:${a.line}\` ${a.kind} \`${a.name}\` — \`${a.rule}\``);
        }
        lines.push('');
    }
    if (hits.length === 0 && unresolvedViolations.length === 0 && defineViolations.length === 0) {
        lines.push('No violations: every declared identity placeholder has a .param key; every variable-form site adjudicated;');
        lines.push('every define declares exactly its description placeholders and has a production consumer.');
    }
    return lines.join('\n');
}

// ============================================================
// Self-verification fixture
// ============================================================

const FX_REGISTRY = {
    argsMap: new Map([
        ['ARG_TABLE_ID', 'tableId'],
        ['ARG_NAME', 'name'],
        ['ARG_ERROR', 'error'],
        ['ARG_OP', 'op'],
    ]),
    errorMap: new Map([
        ['ERR_FX_TABLE_NOT_FOUND', {
            code: 'nop.err.metadata.fx-table-not-found',
            description: 'Table not found: {tableId}',
            placeholders: new Set(['tableId']),
        }],
        ['ERR_FX_OP_UNSUPPORTED', {
            code: 'nop.err.metadata.fx-op-unsupported',
            description: 'op {op} name={name} -- {error}',
            placeholders: new Set(['op', 'name', 'error']),
        }],
        ['ERR_FX_PLAIN', {
            code: 'nop.err.metadata.fx-plain',
            description: 'no placeholders here',
            placeholders: new Set(),
        }],
    ]),
};

const FX_SAMPLES = [
    {
        label: 'literal-key-complete',
        source: `class FxA { void m(String id) {
    throw new NopMetadataException(NopMetadataErrors.ERR_FX_TABLE_NOT_FOUND)
            .param("tableId", id);
} }`,
        expectHits: 0, expectUnresolved: 0,
    },
    {
        label: 'qualified-arg-key-complete',
        source: `class FxB { void m(String id) {
    throw new NopMetadataException(NopMetadataErrors.ERR_FX_TABLE_NOT_FOUND)
            .param(NopMetadataErrors.ARG_TABLE_ID, id);
} }`,
        expectHits: 0, expectUnresolved: 0,
    },
    {
        label: 'bare-arg-key-complete',
        source: `class FxC { void m(String id) {
    throw new NopMetadataException(NopMetadataErrors.ERR_FX_TABLE_NOT_FOUND)
            .param(ARG_TABLE_ID, id);
} }`,
        expectHits: 0, expectUnresolved: 0,
    },
    {
        label: 'missing-identity-placeholder (poison form)',
        source: `class FxD { void m(String id) {
    throw new NopMetadataException(NopMetadataErrors.ERR_FX_TABLE_NOT_FOUND);
} }`,
        expectHits: 1, expectUnresolved: 0,
    },
    {
        label: 'key-mismatch (P1-7 form: wrong key name)',
        source: `class FxE { void m(String name) {
    throw new NopMetadataException(NopMetadataErrors.ERR_FX_TABLE_NOT_FOUND)
            .param("name", name);
} }`,
        expectHits: 1, expectUnresolved: 0,
    },
    {
        // P2-09 exemption closed: a missing {error} param now hits like any
        // identity placeholder (renders literally at runtime)
        label: 'error-placeholder-missing-param (P2-09 closed)',
        source: `class FxF { void m(String op, String name) {
    throw new NopMetadataException(NopMetadataErrors.ERR_FX_OP_UNSUPPORTED)
            .param("op", op).param("name", name);
} }`,
        expectHits: 1, expectUnresolved: 0,
    },
    {
        label: 'string-ctor-skipped',
        source: `class FxG { void m() {
    throw new NopMetadataException("plain message");
} }`,
        expectHits: 0, expectUnresolved: 0,
    },
    {
        label: 'variable-form-error-code-unresolved',
        source: `class FxH { void m(ErrorCode errOnInvalid) {
    throw new NopMetadataException(errOnInvalid).param("tableId", "t1");
} }`,
        expectHits: 0, expectUnresolved: 1,
    },
    {
        label: 'inline-define-resolved',
        source: `class FxI { void m(String id) {
    throw new NopMetadataException(ErrorCode.define("nop.err.x", "bad table {tableId}", ARG_TABLE_ID))
            .param("tableId", id);
} }`,
        expectHits: 0, expectUnresolved: 0,
    },
    {
        label: 'bare-err-constant-resolved',
        source: `class FxJ implements NopMetadataErrors { void m(String id) {
    throw new NopMetadataException(ERR_FX_TABLE_NOT_FOUND).param(ARG_TABLE_ID, id);
} }`,
        expectHits: 0, expectUnresolved: 0,
    },
    {
        label: 'builder-var-form-complete (split e.param statements)',
        source: `class FxK { void m(String jdbcUrl, String reason) {
    NopMetadataException e = new NopMetadataException(
            NopMetadataErrors.ERR_FX_TABLE_NOT_FOUND);
    return e.param("tableId", jdbcUrl).param("name", reason);
} }`,
        expectHits: 0, expectUnresolved: 0,
    },
    {
        label: 'builder-var-form-without-any-param (real miss)',
        source: `class FxL { void m() {
    NopMetadataException e = new NopMetadataException(
            NopMetadataErrors.ERR_FX_TABLE_NOT_FOUND);
    throw e;
} }`,
        expectHits: 1, expectUnresolved: 0,
    },
];

function runFixtureOnSample(sample, registry) {
    return scanFileText('fixture/' + sample.label + '.java',
        stripCommentsOnly(sample.source), sample.source, registry);
}

function runFixture() {
    const results = [];
    for (const sample of FX_SAMPLES) {
        const { hits, unresolved } = runFixtureOnSample(sample, FX_REGISTRY);
        results.push({
            sample: sample.label,
            expectedHits: sample.expectHits,
            expectedUnresolved: sample.expectUnresolved,
            actualHits: hits.length,
            actualUnresolved: unresolved.length,
            pass: hits.length === sample.expectHits && unresolved.length === sample.expectUnresolved,
        });
    }
    // Allowed-comment mechanism: a hit on a line annotated `// invariant-ok:`
    // (same-line trailing, or a standalone annotation comment line directly
    // above the throw) moves out of the violation set but stays visible.
    const annotated = `class FxAllowed { void m(String id) {
    throw new NopMetadataException(NopMetadataErrors.ERR_FX_TABLE_NOT_FOUND); // invariant-ok: fixture-adjudication
} }`;
    const rawByFile = new Map([['fixture/annotated.java', annotated]]);
    const scanRes = scanFileText('fixture/annotated.java', stripCommentsOnly(annotated), annotated, FX_REGISTRY);
    const split = splitAllowed(scanRes.hits, rawByFile);
    const allowedPass = split.violations.length === 0 && split.allowed.length === 1;

    // Preceding-line annotation form (standalone comment line above the throw)
    // also suppresses, staying visible — needed for long adjudication
    // references that would break the 80-column checkstyle limit.
    const annotatedAbove = `class FxAllowedAbove { void m(String id) {
    // invariant-ok: preceding-line adjudication reference (fixture)
    throw new NopMetadataException(NopMetadataErrors.ERR_FX_TABLE_NOT_FOUND);
} }`;
    const rawByFile2 = new Map([['fixture/annotated-above.java', annotatedAbove]]);
    const scanRes2 = scanFileText('fixture/annotated-above.java', stripCommentsOnly(annotatedAbove), annotatedAbove, FX_REGISTRY);
    const split2 = splitAllowed(scanRes2.hits, rawByFile2);
    const allowedAbovePass = split2.violations.length === 0 && split2.allowed.length === 1;

    // A preceding-line annotation does NOT leak to a following, separate throw.
    const twoThrows = `class FxTwoThrows { void m(String id) {
    // invariant-ok: preceding-line adjudication reference (fixture)
    throw new NopMetadataException(NopMetadataErrors.ERR_FX_TABLE_NOT_FOUND);
    throw new NopMetadataException(NopMetadataErrors.ERR_FX_TABLE_NOT_FOUND);
} }`;
    const rawByFile3 = new Map([['fixture/two-throws.java', twoThrows]]);
    const scanRes3 = scanFileText('fixture/two-throws.java', stripCommentsOnly(twoThrows), twoThrows, FX_REGISTRY);
    const split3 = splitAllowed(scanRes3.hits, rawByFile3);
    const noLeakPass = split3.violations.length === 1 && split3.allowed.length === 1;

    // Multi-line wrapped annotation inside the contiguous comment block above
    // the throw is accepted (long adjudication references, 80-col safe).
    const annotatedWrapped = `class FxAllowedWrapped { void m(String id) {
    // explanatory adjudication comment (fixture)
    // invariant-ok: wrapped adjudication reference line one —
    // continued on line two with detail
    throw new NopMetadataException(NopMetadataErrors.ERR_FX_TABLE_NOT_FOUND);
} }`;
    const rawByFile4 = new Map([['fixture/annotated-wrapped.java', annotatedWrapped]]);
    const scanRes4 = scanFileText('fixture/annotated-wrapped.java', stripCommentsOnly(annotatedWrapped), annotatedWrapped, FX_REGISTRY);
    const split4 = splitAllowed(scanRes4.hits, rawByFile4);
    const allowedWrappedPass = split4.violations.length === 0 && split4.allowed.length === 1;

    // Define-face rule (P2-10): declared ARG values must equal desc placeholders.
    const FX_DEFINE_REGISTRY = {
        argsMap: FX_REGISTRY.argsMap,
        errorMap: new Map([
            ['ERR_FX_DEF_OK', {
                code: 'nop.err.metadata.fx-def-ok', description: 'ok {tableId}',
                placeholders: new Set(['tableId']), tailIds: ['ARG_TABLE_ID'],
                file: 'fixture/DefineFx.java', line: 3,
            }],
            ['ERR_FX_DEF_MISSING_DECL', {
                code: 'nop.err.metadata.fx-def-missing-decl', description: 'needs {tableId} {error}',
                placeholders: new Set(['tableId', 'error']), tailIds: [],
                file: 'fixture/DefineFx.java', line: 4,
            }],
            ['ERR_FX_DEF_MISSING_PH', {
                code: 'nop.err.metadata.fx-def-missing-ph', description: 'none here',
                placeholders: new Set(), tailIds: ['ARG_TABLE_ID'],
                file: 'fixture/DefineFx.java', line: 5,
            }],
            ['ERR_FX_DEF_UNRESOLVED_ARG', {
                code: 'nop.err.metadata.fx-def-unresolved-arg', description: 'x {tableId}',
                placeholders: new Set(['tableId']), tailIds: ['ARG_TABLE_ID', 'ARG_NOPE'],
                file: 'fixture/DefineFx.java', line: 6,
            }],
        ]),
    };
    const defineFaceRes = checkDefineFace(FX_DEFINE_REGISTRY);
    const defineFacePass = defineFaceRes.length === 3
        && defineFaceRes.some((v) => v.name === 'ERR_FX_DEF_MISSING_DECL' && v.missing.includes('tableId'))
        && defineFaceRes.some((v) => v.name === 'ERR_FX_DEF_MISSING_PH' && v.missing.includes('tableId'))
        && defineFaceRes.some((v) => v.name === 'ERR_FX_DEF_UNRESOLVED_ARG');

    // Dead-define rule (P2-10): dead / test-only / alive classification.
    const deadCorpora = {
        moduleMain: 'class C { Object a = NopMetadataErrors.ERR_FX_DEF_OK;'
            + ' Object c = NopMetadataErrors.ERR_FX_DEF_UNRESOLVED_ARG; }',
        moduleTest: 'class T { Object b = NopMetadataErrors.ERR_FX_DEF_MISSING_PH; }',
        repoAll: '',
    };
    const deadRes = checkDeadDefines(FX_DEFINE_REGISTRY, deadCorpora);
    const deadPass = deadRes.length === 2
        && deadRes.some((v) => v.name === 'ERR_FX_DEF_MISSING_DECL' && v.kind === 'dead-define')
        && deadRes.some((v) => v.name === 'ERR_FX_DEF_MISSING_PH' && v.kind === 'test-only-define')
        && !deadRes.some((v) => v.name === 'ERR_FX_DEF_OK');

    const allPass = results.every(r => r.pass) && allowedPass && allowedAbovePass && noLeakPass
        && allowedWrappedPass && defineFacePass && deadPass;
    console.log('Self-verification fixture: ' + (allPass ? 'PASS' : 'FAIL'));
    console.log('');
    for (const r of results) {
        const status = r.pass ? 'OK' : 'FAIL';
        console.log(`  [${status}] ${r.sample}: expected hits=${r.expectedHits} unresolved=${r.expectedUnresolved}, actual hits=${r.actualHits} unresolved=${r.actualUnresolved}`);
    }
    console.log(`  [${allowedPass ? 'OK' : 'FAIL'}] allowed-comment same-line: violation=${split.violations.length}, allowed=${split.allowed.length} (expect 0/1, allowed listed not silent)`);
    console.log(`  [${allowedAbovePass ? 'OK' : 'FAIL'}] allowed-comment preceding-line: violation=${split2.violations.length}, allowed=${split2.allowed.length} (expect 0/1)`);
    console.log(`  [${noLeakPass ? 'OK' : 'FAIL'}] preceding-line no-leak: violation=${split3.violations.length}, allowed=${split3.allowed.length} (expect 1/1 — second throw NOT exempted)`);
    console.log(`  [${allowedWrappedPass ? 'OK' : 'FAIL'}] allowed-comment wrapped-block: violation=${split4.violations.length}, allowed=${split4.allowed.length} (expect 0/1)`);
    console.log(`  [${defineFacePass ? 'OK' : 'FAIL'}] define-face symmetric diff: violations=${defineFaceRes.length} (expect 3: missing-decl, missing-ph, unresolved-arg)`);
    console.log(`  [${deadPass ? 'OK' : 'FAIL'}] dead-define classification: findings=${deadRes.length} (expect 2: dead + test-only; alive skipped)`);
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
        console.log('Usage: node ai-dev/tools/check-error-param-consistency.mjs --module nop-metadata');
        console.log('       node ai-dev/tools/check-error-param-consistency.mjs --module nop-metadata --format json');
        console.log('       node ai-dev/tools/check-error-param-consistency.mjs --fixture');
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

    const javaFiles = [];
    for (const p of scanPaths) walkJavaFiles(p, javaFiles);
    const registry = buildRegistry(javaFiles);
    if (registry.errorMap.size === 0 || registry.argsMap.size === 0) {
        console.error('Error: registry empty — NopMetadataArgs.java / *Errors.java not found under scan path.');
        process.exit(2);
    }

    let allHits = [];
    let allUnresolved = [];
    const rawTextByFile = new Map();
    for (const f of javaFiles) {
        const rel = relativePath(f);
        rawTextByFile.set(rel, fs.readFileSync(f, 'utf-8'));
        const r = scanFile(f, registry);
        allHits.push(...r.hits);
        allUnresolved.push(...r.unresolved);
    }

    // Define-face + dead-define rules (P2-10). Errors files' raw text is
    // already in rawTextByFile (they are part of the walked module files).
    const corpora = buildDeadCheckCorpora(repoRoot, scanPaths, args.module);
    let defineViolations = checkDefineFace(registry);
    let deadFindings = checkDeadDefines(registry, corpora);
    // provisional findings need repo-wide confirmation (a candidate with no
    // module-main refs could still be referenced by another module's code);
    // only build the repo corpus when candidates exist (keeps it fast)
    if (deadFindings.length > 0) {
        corpora.repoAll = corpora._buildRepoCorpus();
        deadFindings = checkDeadDefines(registry, corpora);
    }
    defineViolations = defineViolations.concat(deadFindings);
    const defineSplit = splitAllowed(defineViolations, rawTextByFile);
    defineViolations = defineSplit.violations;
    const defineAllowedEntries = defineSplit.allowed;
    defineViolations.sort((a, b) => a.file.localeCompare(b.file) || a.line - b.line);

    const hitSplit = splitAllowed(allHits, rawTextByFile);
    const unresolvedSplit = splitAllowed(allUnresolved, rawTextByFile);
    allHits = hitSplit.violations;
    allUnresolved = unresolvedSplit.violations;

    allHits.sort((a, b) => a.file.localeCompare(b.file) || a.line - b.line);
    allUnresolved.sort((a, b) => a.file.localeCompare(b.file) || a.line - b.line);

    switch (args.format) {
        case 'json':
            console.log(JSON.stringify({
                invariant: 'INV-ERROR-PARAM',
                generated: new Date().toISOString(),
                registrySize: { errorCodes: registry.errorMap.size, args: registry.argsMap.size },
                violationCount: allHits.length,
                unresolvedCount: allUnresolved.length,
                defineFaceViolationCount: defineViolations.length,
                violations: allHits,
                unresolved: allUnresolved,
                defineFace: defineViolations,
                allowed: [...hitSplit.allowed, ...unresolvedSplit.allowed, ...defineAllowedEntries],
            }, null, 2));
            break;
        default:
            console.log(generateSummary(allHits, hitSplit.allowed, allUnresolved, unresolvedSplit.allowed,
                defineViolations, defineAllowedEntries));
    }

    process.exit((allHits.length + allUnresolved.length + defineViolations.length) > 0 ? 1 : 0);
}

main();
