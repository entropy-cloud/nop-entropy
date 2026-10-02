#!/usr/bin/env node
// coverage-baseline.mjs — 解析逐模块 jacoco 报告，产出 unit-test-coverage-roadmap 的分模块基线快照。
//
// 数据源：各模块 target/site/jacoco/jacoco.xml（由 coverage-baseline.sh 的逐模块
// jacoco:report 步骤生成，exec 排除口径 = root pom coverage profile）。
// 分层与目标：roadmap M1 内核 ≥55% / M2 业务引擎 ≥45% / M3 外围 ≥30% 行覆盖；
// 数据驱动模块（nop-jq / nop-xlang）标记 semantic，不按行覆盖强判。
//
// 用法：
//   node ai-dev/tools/coverage-baseline.mjs [--skip-maven] [--out <dir>] [--label <YYYY-MM-DD>]
//     --out <dir>    输出目录（默认 ai-dev/analysis/2026-10）
//     --label <d>    快照名后缀（默认当天日期）
//     --help

import fs from 'node:fs';
import path from 'node:path';

const ROOT = path.resolve(new URL('..', import.meta.url).pathname, '..');

function parseArgs(argv) {
    const opts = { out: 'ai-dev/analysis/2026-10', label: new Date().toISOString().slice(0, 10) };
    for (let i = 0; i < argv.length; i++) {
        const a = argv[i];
        if (a === '--out') opts.out = argv[++i];
        else if (a === '--label') opts.label = argv[++i];
        else if (a === '--help') {
            console.log('usage: coverage-baseline.mjs [--skip-maven] [--out <dir>] [--label <YYYY-MM-DD>]');
            process.exit(0);
        }
    }
    return opts;
}

// ---------- 模块发现 ----------

const SKIP_DIRS = new Set(['.git', '.m2-repo', 'node_modules', '_tmp', '_sql', 'docs', 'docs-for-ai', 'ai-dev', '.idea', '.mvn']);

function walkModules(dir, out) {
    for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
        if (e.isFile() || SKIP_DIRS.has(e.name) || e.name.startsWith('.')) continue;
        const p = path.join(dir, e.name);
        if (e.isDirectory()) walkModules(p, out);
    }
    if (fs.existsSync(path.join(dir, 'pom.xml'))) out.push(dir);
}

function artifactIdOf(moduleDir) {
    const pom = fs.readFileSync(path.join(moduleDir, 'pom.xml'), 'utf8');
    if (pom.includes('@artifactId@')) return null; // archetype 模板，不可构建
    const noParent = pom.replace(/<parent>[\s\S]*?<\/parent>/, '');
    const m = noParent.match(/<artifactId>([^<]+)<\/artifactId>/);
    return m ? m[1] : path.basename(moduleDir);
}

// roadmap 分层：按 root pom 聚合组路径（M1 内核 / M2 业务引擎 / M3 外围）
// kernel = nop-kernel + nop-persistence + nop-core-framework（module-groups.md 的框架主干三层）
function layerOf(relDir) {
    const top = relDir.split(path.sep)[0];
    if (top === 'nop-kernel' || top === 'nop-persistence' || top === 'nop-core-framework') return 'kernel';
    if (['nop-wf', 'nop-batch', 'nop-sys', 'nop-rule', 'nop-dyn', 'nop-service-framework'].includes(top)) return 'engine';
    return 'periphery';
}

const LAYER_TARGET = { kernel: 55, engine: 45, periphery: 30 };
// 数据驱动模块：以语义基线为准（roadmap Rules），不按行覆盖强判
const SEMANTIC = new Set(['nop-jq', 'nop-xlang']);

// ---------- jacoco XML 解析（零依赖） ----------

function parseCounters(text) {
    const counters = {};
    const re = /<counter type="(\w+)" missed="(\d+)" covered="(\d+)"\/>/g;
    let m;
    while ((m = re.exec(text))) counters[m[1]] = { missed: +m[2], covered: +m[3] };
    return counters;
}

function pct(c) {
    if (!c || (c.covered + c.missed) === 0) return null;
    return Math.round((10000 * c.covered) / (c.covered + c.missed)) / 100;
}

function parseModuleReport(xmlPath) {
    const xml = fs.readFileSync(xmlPath, 'utf8');
    const lastPkg = xml.lastIndexOf('</package>');
    const totals = parseCounters(lastPkg >= 0 ? xml.slice(lastPkg) : xml);
    const classes = [];
    const classRe = /<class name="([^"]+)"[^>]*>([\s\S]*?)<\/class>/g;
    let cm;
    while ((cm = classRe.exec(xml))) {
        const name = cm[1];
        if (name.includes('._gen') || /\/_gen\//.test(name)) continue;
        const body = cm[2].replace(/<method[\s\S]*?<\/method>/g, '');
        const cc = parseCounters(body);
        const line = pct(cc.LINE);
        if (line === null) continue;
        const lines = (cc.LINE.covered || 0) + (cc.LINE.missed || 0);
        classes.push({ class: name.replace(/\//g, '.'), linePct: line, lines });
    }
    classes.sort((a, b) => a.linePct - b.linePct);
    return {
        line: pct(totals.LINE), lineCovered: totals.LINE?.covered ?? 0, lineTotal: (totals.LINE?.covered ?? 0) + (totals.LINE?.missed ?? 0),
        branch: pct(totals.BRANCH), branchCovered: totals.BRANCH?.covered ?? 0, branchTotal: (totals.BRANCH?.covered ?? 0) + (totals.BRANCH?.missed ?? 0),
        classes,
    };
}

// ---------- 主流程 ----------

const opts = parseArgs(process.argv.slice(2));
const moduleDirs = [];
walkModules(ROOT, moduleDirs);

const rows = [];
let xmlCount = 0;
for (const dir of moduleDirs) {
    const rel = path.relative(ROOT, dir);
    const aid = artifactIdOf(dir);
    if (!aid) continue; // archetype 模板
    const hasMain = fs.existsSync(path.join(dir, 'src', 'main', 'java'));
    if (!hasMain) continue; // 纯 pom 聚合/资源模块不进基线
    const layer = layerOf(rel);
    const semantic = SEMANTIC.has(aid);
    const xmlPath = path.join(dir, 'target', 'site', 'jacoco', 'jacoco.xml');
    if (fs.existsSync(xmlPath)) {
        xmlCount++;
        const r = parseModuleReport(xmlPath);
        rows.push({
            dir: rel, artifactId: aid, layer, targetPct: LAYER_TARGET[layer], semantic,
            linePct: r.line, lineCovered: r.lineCovered, lineTotal: r.lineTotal,
            branchPct: r.branch, branchCovered: r.branchCovered, branchTotal: r.branchTotal,
            classCount: r.classes.length,
            lowCoverageClasses: r.classes.filter(c => c.linePct < 40 && c.lines >= 30).slice(0, 60),
        });
    } else {
        rows.push({
            dir: rel, artifactId: aid, layer, targetPct: LAYER_TARGET[layer], semantic,
            linePct: null, lineCovered: 0, lineTotal: null, branchPct: null, branchCovered: 0, branchTotal: null,
            classCount: null, lowCoverageClasses: [],
        });
    }
}

rows.sort((a, b) => (a.layer === b.layer ? (a.linePct ?? -1) - (b.linePct ?? -1) : a.layer.localeCompare(b.layer)));

let latestMtime = 0;
for (const r of rows) {
    const p = path.join(ROOT, r.dir, 'target', 'site', 'jacoco', 'jacoco.xml');
    if (fs.existsSync(p)) latestMtime = Math.max(latestMtime, fs.statSync(p).mtimeMs);
}

const snapshot = {
    label: opts.label,
    generatedAt: latestMtime ? new Date(latestMtime).toISOString() : null,
    source: 'per-module target/site/jacoco/jacoco.xml (jacoco 0.8.14, root pom coverage profile excludes)',
    layerTargets: LAYER_TARGET,
    totals: {
        modulesWithReport: xmlCount,
        modulesNoReport: rows.filter(r => r.linePct === null).length,
        overallLinePct: pct({ covered: rows.reduce((s, r) => s + r.lineCovered, 0), missed: rows.reduce((s, r) => s + ((r.lineTotal ?? 0) - r.lineCovered), 0) }),
    },
    modules: rows,
};

// ---------- 输出 ----------

fs.mkdirSync(opts.out, { recursive: true });
const jsonPath = path.join(opts.out, `coverage-baseline-${opts.label}.json`);
fs.writeFileSync(jsonPath, JSON.stringify(snapshot, null, 2) + '\n');

const md = [];
md.push(`# 覆盖率基线快照 ${opts.label}`);
md.push('');
md.push(`- 数据源：逐模块 jacoco.xml × ${xmlCount}；无报告模块 × ${snapshot.totals.modulesNoReport}（有 main 无 exec，按 0 处理）`);
md.push(`- 全仓加权行覆盖：${snapshot.totals.overallLinePct}%`);
md.push(`- 生成时间（以最新 jacoco.xml mtime 为准，保证幂等）：${snapshot.generatedAt}`);
md.push('');
md.push('| 模块 | 分层 | 目标% | 行覆盖% | 分支覆盖% | 行数 | 达标 |');
md.push('|---|---|---:|---:|---:|---:|---|');
for (const r of rows) {
    const target = r.semantic ? 'semantic' : r.targetPct;
    const meet = r.semantic ? 'semantic' : (r.linePct === null ? 'NO-EXEC' : (r.linePct >= r.targetPct ? 'YES' : 'no'));
    md.push(`| ${r.artifactId} | ${r.layer} | ${target} | ${r.linePct ?? '—'} | ${r.branchPct ?? '—'} | ${r.lineTotal ?? '—'} | ${meet} |`);
}
md.push('');
const mdPath = path.join(opts.out, `coverage-baseline-${opts.label}.md`);
fs.writeFileSync(mdPath, md.join('\n') + '\n');

console.log(`modules=${rows.length} withReport=${xmlCount} overallLinePct=${snapshot.totals.overallLinePct}`);
console.log(`written: ${jsonPath}`);
console.log(`written: ${mdPath}`);
