#!/usr/bin/env node
// check-test-time-waits.mjs — 测试源时间等待守卫（roadmap WI-3，plan 2509）。
//
// 扫描全仓 `src/test/**.java` 中的 `Thread.sleep` / `TimeUnit.*.sleep` 等时间等待，
// 按 roadmap Evidence Ledger 的四分类启发式归类（SLEEP_THEN_ASSERT / POLL_LOOP /
// SIMULATED_WORK / OTHER），与提交基线 `test-time-waits-baseline.json` 对账：
//   - current > baseline（新文件 / 新类别 / 计数上升）→ exit 1（增，禁止）
//   - baseline > current（修复未消号 / 文件消失）→ exit 1（stale，需 --update 同批消号）
//   - 相等 → exit 0
// 「存量基线只减不增」由两向共同保证；消号 = 修复 + 同批 `--update`。
//
// 启发式与 `_tmp/classify_test_sleeps.py`（盘点脚本，git 忽略）逐条同口径平移；
// 差异面：本脚本剥离注释与字符串字面量（Python 侧对账时同口径适配）、排除 `_gen/`。
//
// 用法：
//   node check-test-time-waits.mjs                # 全仓对账（check）
//   node check-test-time-waits.mjs self-test      # 正向控制自测（内置 fixture）
//   node check-test-time-waits.mjs --update       # 重生成基线（消号 = 修复 + 同批）
//   node check-test-time-waits.mjs --module <pfx> # 子集扫描（check 只对子集内报错）
//   node check-test-time-waits.mjs --json         # 机器可读报告
//   node check-test-time-waits.mjs --help

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const TOOLS_DIR = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(TOOLS_DIR, '..', '..');
const BASELINE_PATH = path.join(TOOLS_DIR, 'test-time-waits-baseline.json');

const CATEGORIES = ['SLEEP_THEN_ASSERT', 'POLL_LOOP', 'SIMULATED_WORK', 'OTHER'];

// 与 _tmp/classify_test_sleeps.py 逐条同口径的正则（不得凭"等价语义"改写）。
const SLEEP = /\b(Thread\.sleep|TimeUnit\.\w+\.sleep|SECONDS\.sleep|MILLISECONDS\.sleep)\s*\(/;
const ASSERT = /\b(assert\w*|verify|fail)\s*\(|assertThat\(/;
const SYNC = /\.(await|join|get|acquire|take|poll)\s*\(|awaitUntil|Awaitility|await\(\)/;
const LOOP_HEAD = /^\s*(while|for|do)\b/;
const LAMBDA_HEAD = /->\s*\{|\bpublic void run\(\)|\bpublic \w+ call\(\)|\bvoid run\(\)/;

const SKIP_DIRS = new Set(['target', 'node_modules', '.git', '_tmp', '_gen']);

function parseArgs(argv) {
    const opts = { mode: 'check', update: false, json: false, module: null };
    for (let i = 0; i < argv.length; i++) {
        const a = argv[i];
        if (a === 'self-test') opts.mode = 'self-test';
        else if (a === '--update') opts.update = true;
        else if (a === '--json') opts.json = true;
        else if (a === '--module') {
            opts.module = argv[++i];
            if (!opts.module) return usageError('--module requires a path prefix');
        } else if (a === '--help') {
            console.log('usage: check-test-time-waits.mjs [self-test] [--update] [--module <prefix>] [--json] [--help]');
            process.exit(0);
        } else {
            return usageError('unknown argument: ' + a);
        }
    }
    return opts;
}

function usageError(msg) {
    console.error('ERROR: ' + msg);
    process.exit(2);
}

// ---------- 文件遍历 ----------

function walkJavaTestFiles(dir, out) {
    let entries;
    try {
        entries = fs.readdirSync(dir, { withFileTypes: true });
    } catch {
        return;
    }
    for (const e of entries) {
        const p = path.join(dir, e.name);
        if (e.isDirectory()) {
            if (SKIP_DIRS.has(e.name) || e.name.startsWith('.')) continue;
            walkJavaTestFiles(p, out);
        } else if (e.isFile() && e.name.endsWith('.java') && isTestSource(p)) {
            out.push(p);
        }
    }
}

function isTestSource(absPath) {
    const rel = path.relative(ROOT, absPath).split(path.sep).join('/');
    return rel.includes('/src/test/');
}

// ---------- 注释/字符串剥离（字符状态机，只可能少计不可能多计） ----------

const ST_CODE = 0, ST_BLOCK_COMMENT = 1, ST_LINE_COMMENT = 2, ST_STRING = 3, ST_CHAR = 4;

function maskCommentsAndStrings(text) {
    const out = [];
    let state = ST_CODE;
    for (let i = 0; i < text.length; i++) {
        const c = text[i];
        const next = i + 1 < text.length ? text[i + 1] : '';
        if (state === ST_CODE) {
            if (c === '/' && next === '*') { out.push(' ', ' '); state = ST_BLOCK_COMMENT; i++; continue; }
            if (c === '/' && next === '/') { out.push(' ', ' '); state = ST_LINE_COMMENT; i++; continue; }
            if (c === '"') { out.push(' '); state = ST_STRING; continue; }
            if (c === '\'') { out.push(' '); state = ST_CHAR; continue; }
            out.push(c);
        } else if (state === ST_BLOCK_COMMENT) {
            if (c === '*' && next === '/') { out.push(' ', ' '); state = ST_CODE; i++; continue; }
            out.push(c === '\n' ? '\n' : ' ');
        } else if (state === ST_LINE_COMMENT) {
            if (c === '\n') { out.push('\n'); state = ST_CODE; continue; }
            out.push(' ');
        } else if (state === ST_STRING) {
            if (c === '\\' && next !== '') { out.push(' ', ' '); i++; continue; }
            if (c === '"') { out.push(' '); state = ST_CODE; continue; }
            out.push(c === '\n' ? '\n' : ' ');
        } else { // ST_CHAR
            if (c === '\\' && next !== '') { out.push(' ', ' '); i++; continue; }
            if (c === '\'') { out.push(' '); state = ST_CODE; continue; }
            out.push(c === '\n' ? '\n' : ' ');
        }
    }
    return out.join('');
}

// ---------- 分类（_tmp/classify_test_sleeps.py 逐条平移） ----------

function classify(lines, idx) {
    const depthLine = lines[idx];
    const indent = trimStartLen(depthLine);
    for (let j = idx - 1; j >= Math.max(-1, idx - 40) + 1; j--) {
        const line = lines[j];
        if (line === undefined) break;
        const stripped = line.trim();
        if (!stripped) continue;
        const lineIndent = trimStartLen(line);
        if (lineIndent < indent && stripped.endsWith('{')) {
            if (LOOP_HEAD.test(line)) return 'POLL_LOOP';
            if (LAMBDA_HEAD.test(line)) return 'SIMULATED_WORK';
            break;
        }
    }
    const limit = Math.min(lines.length, idx + 8);
    for (let k = idx + 1; k < limit; k++) {
        if (SYNC.test(lines[k])) return 'OTHER';
        if (ASSERT.test(lines[k])) return 'SLEEP_THEN_ASSERT';
    }
    return 'OTHER';
}

function trimStartLen(line) {
    const m = line.match(/^\s*/);
    return m ? m[0].length : 0;
}

function scanFile(absPath) {
    const text = fs.readFileSync(absPath, 'utf8');
    const masked = maskCommentsAndStrings(text);
    const lines = masked.split(/\r?\n/);
    const counts = {};
    for (let idx = 0; idx < lines.length; idx++) {
        if (!SLEEP.test(lines[idx])) continue;
        const cat = classify(lines, idx);
        counts[cat] = (counts[cat] || 0) + 1;
    }
    return counts;
}

function scanRepo(modulePrefix) {
    const files = [];
    walkJavaTestFiles(ROOT, files);
    const relFiles = files
        .map((p) => path.relative(ROOT, p).split(path.sep).join('/'))
        .sort((a, b) => (a < b ? -1 : a > b ? 1 : 0));
    const result = { scannedFiles: 0, entries: {}, totals: {} };
    for (const rel of relFiles) {
        if (modulePrefix && !rel.startsWith(modulePrefix)) continue;
        result.scannedFiles++;
        const counts = scanFile(path.join(ROOT, ...rel.split('/')));
        if (Object.keys(counts).length === 0) continue;
        result.entries[rel] = counts;
        for (const cat of Object.keys(counts)) {
            result.totals[cat] = (result.totals[cat] || 0) + counts[cat];
        }
    }
    return result;
}

// ---------- 基线对账 ----------

function loadBaseline() {
    if (!fs.existsSync(BASELINE_PATH)) return null;
    return JSON.parse(fs.readFileSync(BASELINE_PATH, 'utf8'));
}

function compareWithBaseline(current, baseline, modulePrefix) {
    const errors = [];
    const baseEntries = baseline ? baseline.entries : {};
    const inScope = (file) => !modulePrefix || file.startsWith(modulePrefix);
    // 增：current > baseline（含基线缺失的新文件 / 新类别）
    for (const [file, counts] of Object.entries(current.entries)) {
        if (!inScope(file)) continue;
        const baseCounts = baseEntries[file] || {};
        for (const [cat, n] of Object.entries(counts)) {
            const base = baseCounts[cat] || 0;
            if (n > base) {
                errors.push(`INCREASE ${file} ${cat}: current=${n} baseline=${base} — 新增时间等待违规（禁止）`);
            }
        }
    }
    // stale：baseline > current（修复未消号 / 文件消失），需 --update 同批消号
    for (const [file, baseCounts] of Object.entries(baseEntries)) {
        if (!inScope(file)) continue;
        const curCounts = current.entries[file] || {};
        for (const [cat, base] of Object.entries(baseCounts)) {
            const cur = curCounts[cat] || 0;
            if (base > cur) {
                errors.push(`STALE ${file} ${cat}: current=${cur} baseline=${base} — 修复后基线未消号，运行 --update 同批消号`);
            }
        }
    }
    return errors.sort((a, b) => (a < b ? -1 : a > b ? 1 : 0));
}

function buildBaselineJson(current) {
    const doc = {
        formatVersion: 1,
        generator: 'ai-dev/tools/check-test-time-waits.mjs --update',
        updatedAt: new Date().toISOString().slice(0, 10),
        totals: {},
        entries: {},
    };
    const cats = [...CATEGORIES].sort((a, b) => (a < b ? -1 : a > b ? 1 : 0));
    for (const cat of cats) doc.totals[cat] = current.totals[cat] || 0;
    const files = Object.keys(current.entries).sort((a, b) => (a < b ? -1 : a > b ? 1 : 0));
    for (const file of files) {
        const counts = {};
        for (const cat of Object.keys(current.entries[file]).sort((a, b) => (a < b ? -1 : a > b ? 1 : 0))) {
            counts[cat] = current.entries[file][cat];
        }
        doc.entries[file] = counts;
    }
    return JSON.stringify(doc, null, 2) + '\n';
}

// ---------- self-test（正向控制：扫描器必须证明自己能变红） ----------

function selfTest() {
    const cases = [];
    const add = (name, source, expect) => cases.push({ name, source, expect });

    add('no-sleep-file-all-zero', [
        'public class Foo {',
        '    @Test',
        '    public void testOk() {',
        '        assertEquals(1, 1);',
        '    }',
        '}',
    ].join('\n'), {});

    add('sleep-then-assert', [
        'public class Foo {',
        '    @Test',
        '    public void testA() throws Exception {',
        '        startThing();',
        '        Thread.sleep(100);',
        '        assertEquals("done", state);',
        '    }',
        '}',
    ].join('\n'), { SLEEP_THEN_ASSERT: 1 });

    add('poll-loop', [
        'public class Foo {',
        '    @Test',
        '    public void testB() throws Exception {',
        '        while (!done.get()) {',
        '            Thread.sleep(10);',
        '        }',
        '    }',
        '}',
    ].join('\n'), { POLL_LOOP: 1 });

    add('simulated-work-in-run', [
        'public class Foo {',
        '    @Test',
        '    public void testC() {',
        '        new Thread(new Runnable() {',
        '            public void run() {',
        '                Thread.sleep(5000);',
        '            }',
        '        }).start();',
        '    }',
        '}',
    ].join('\n'), { SIMULATED_WORK: 1 });

    add('other-step-spacing', [
        'public class Foo {',
        '    @Test',
        '    public void testD() throws Exception {',
        '        stepOne();',
        '        Thread.sleep(50);',
        '        stepTwo();',
        '    }',
        '}',
    ].join('\n'), { OTHER: 1 });

    add('comment-and-string-sleeps-not-counted', [
        'public class Foo {',
        '    // Thread.sleep(1) inside comment',
        '    /* Thread.sleep(2) inside block comment */',
        '    private static final String MSG = "Thread.sleep(3) inside string";',
        '    @Test',
        '    public void testE() {',
        '        assertEquals(MSG, MSG);',
        '    }',
        '}',
    ].join('\n'), {});

    add('malformed-input-stable-not-crash', [
        'public class Foo {',
        '    /* unclosed block comment',
        '    Thread.sleep(1)',
        '',
        '    public void broken() {',
        '        Thread.sleep(2);',
        '    }',
        '}',
    ].join('\n'), null); // 只断言不崩溃且结果确定（无 sleep 命中——其余被注释吞掉）

    add('string-with-url-slash-not-comment', [
        'public class Foo {',
        '    private static final String URL = "http://example.com/a";',
        '    @Test',
        '    public void testF() throws Exception {',
        '        Thread.sleep(10);',
        '        assertEquals(URL, URL);',
        '    }',
        '}',
    ].join('\n'), { SLEEP_THEN_ASSERT: 1 });

    let failed = 0;
    for (const c of cases) {
        let counts;
        try {
            const lines = maskCommentsAndStrings(c.source).split(/\r?\n/);
            counts = {};
            for (let idx = 0; idx < lines.length; idx++) {
                if (!SLEEP.test(lines[idx])) continue;
                const cat = classify(lines, idx);
                counts[cat] = (counts[cat] || 0) + 1;
            }
        } catch (e) {
            console.error(`self-test FAIL ${c.name}: crashed: ${e && e.message}`);
            failed++;
            continue;
        }
        if (c.expect === null) {
            console.log(`self-test ok ${c.name} (no crash, counts=${JSON.stringify(counts)})`);
            continue;
        }
        if (JSON.stringify(counts) !== JSON.stringify(c.expect)) {
            console.error(`self-test FAIL ${c.name}: expect=${JSON.stringify(c.expect)} got=${JSON.stringify(counts)}`);
            failed++;
        } else {
            console.log(`self-test ok ${c.name}`);
        }
    }

    // 对账三态：增 / stale / 相等
    const cur = { scannedFiles: 2, entries: { 'a.java': { POLL_LOOP: 1 }, 'b.java': { OTHER: 2 } }, totals: { POLL_LOOP: 1, OTHER: 2 } };
    const stateCases = [
        { name: 'compare-equal', base: { entries: { 'a.java': { POLL_LOOP: 1 }, 'b.java': { OTHER: 2 } } }, expect: 0 },
        { name: 'compare-increase', base: { entries: { 'a.java': { POLL_LOOP: 1 } } }, expect: 1 },
        { name: 'compare-increase-new-file', base: { entries: {} }, expect: 1 },
        { name: 'compare-stale', base: { entries: { 'a.java': { POLL_LOOP: 2 }, 'b.java': { OTHER: 2 } } }, expect: 1 },
        { name: 'compare-stale-file-gone', base: { entries: { 'a.java': { POLL_LOOP: 1 }, 'b.java': { OTHER: 2 }, 'c.java': { OTHER: 1 } } }, expect: 1 },
        { name: 'compare-flip', base: { entries: { 'a.java': { OTHER: 1 }, 'b.java': { OTHER: 2 } } }, expect: 1 },
    ];
    for (const sc of stateCases) {
        const errors = compareWithBaseline(cur, sc.base);
        if ((errors.length > 0 ? 1 : 0) !== sc.expect) {
            console.error(`self-test FAIL ${sc.name}: expect errors=${sc.expect} got=[${errors.join(' | ')}]`);
            failed++;
        } else {
            console.log(`self-test ok ${sc.name}`);
        }
    }

    if (failed > 0) {
        console.error(`self-test FAILED: ${failed} case(s)`);
        process.exit(1);
    }
    console.log('self-test PASSED');
}

// ---------- main ----------

const opts = parseArgs(process.argv.slice(2));

if (opts.mode === 'self-test') {
    selfTest();
    process.exit(0);
}

const current = scanRepo(opts.module);
const baseline = loadBaseline();
const errors = compareWithBaseline(current, baseline, opts.module);

if (opts.json) {
    console.log(JSON.stringify({ mode: 'check', module: opts.module, scannedFiles: current.scannedFiles, totals: current.totals, entryCount: Object.keys(current.entries).length, entries: current.entries, errors }, null, 2));
} else {
    console.log(`scanned ${current.scannedFiles} test java file(s)` + (opts.module ? ` under ${opts.module}` : ' (whole repo)'));
    console.log(`totals: ` + CATEGORIES.map((c) => `${c}=${current.totals[c] || 0}`).join(', '));
    if (baseline) {
        console.log(`baseline (${BASELINE_PATH}): ` + CATEGORIES.map((c) => `${c}=${baseline.totals[c] || 0}`).join(', '));
    }
    if (errors.length > 0) {
        for (const e of errors) console.error('VIOLATION ' + e);
        console.error(`check FAILED: ${errors.length} problem(s)`);
    }
}

if (opts.update) {
    // --module 子集更新必须与既有基线合并（只改子集内条目），不得整仓覆盖。
    let merged = current;
    if (opts.module) {
        const prev = loadBaseline();
        const prefix = opts.module;
        const entries = {};
        for (const [file, counts] of Object.entries(prev ? prev.entries : {})) {
            if (!file.startsWith(prefix) && !current.entries[file]) {
                entries[file] = counts;
            }
        }
        for (const [file, counts] of Object.entries(current.entries)) {
            if (file.startsWith(prefix) || !entries[file]) {
                entries[file] = counts;
            }
        }
        const totals = {};
        for (const counts of Object.values(entries)) {
            for (const [cat, n] of Object.entries(counts)) {
                totals[cat] = (totals[cat] || 0) + n;
            }
        }
        merged = { scannedFiles: current.scannedFiles, entries, totals };
    }
    fs.writeFileSync(BASELINE_PATH, buildBaselineJson(merged), 'utf8');
    console.log(`baseline updated${opts.module ? ` (module ${opts.module}, merged)` : ''}: ${BASELINE_PATH}（消号请与修复同批提交）`);
    process.exit(0);
}

process.exit(errors.length > 0 ? 1 : 0);
