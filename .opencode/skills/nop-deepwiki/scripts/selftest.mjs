#!/usr/bin/env node
// nop-deepwiki skill 自检（零业务依赖；mermaid 校验为可选增强）
// 对 selftest-fixture 跑 gen-wiki-meta + check-wiki，断言六类缺陷的预期行为：
//   1) 越界行号引用 → gen 降级为文件级链接 + stdout 汇报
//   2) 空括号死链   → gen 不动（解析失败），check ERROR
//   3) 坏 mermaid   → check ERROR（正则白名单；装了 mermaid 时解析校验再报一层）
//   4) 纯文本 Sources → check WARN
//   5) 无图题 mermaid（--check-diagram-titles）→ check WARN；正常页图题块不触发
//   6) 正常页       → 零检出（另断言 index 分组编号目录）
// 运行：node .opencode/skills/nop-deepwiki/scripts/selftest.mjs
// 退出码：0 全部断言通过；1 有断言失败。

import { spawnSync } from 'node:child_process';
import { rmSync, cpSync, mkdirSync, readFileSync } from 'node:fs';
import { join, dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { execSync } from 'node:child_process';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const fixture = join(scriptDir, 'selftest-fixture');
const genScript = join(scriptDir, 'gen-wiki-meta.mjs');
const checkScript = join(scriptDir, 'check-wiki.mjs');
const work = resolve(process.cwd(), '_tmp', 'deepwiki-selftest');
const wikiDir = join(work, 'wiki');
const repoDir = join(work, 'repo');

const failures = [];
const assert = (cond, msg) => { if (cond) console.log('  PASS ' + msg); else { failures.push(msg); console.log('  FAIL ' + msg); } };

// ---------- 0. 准备工作区：fixture 拷入 _tmp，迷你目标仓库 git init（无 origin → 相对链接模式） ----------
rmSync(work, { recursive: true, force: true });
mkdirSync(join(work, 'wiki'), { recursive: true });
cpSync(join(fixture, 'PLAN.md'), join(wikiDir, 'PLAN.md'));
cpSync(join(fixture, 'good.md'), join(wikiDir, 'good.md'));
cpSync(join(fixture, 'bad.md'), join(wikiDir, 'bad.md'));
cpSync(join(fixture, 'target'), repoDir, { recursive: true });
try {
  execSync('git init -q', { cwd: repoDir });
} catch (e) {
  console.error('git init 失败（selftest 需要可用 git）：' + e.message);
  process.exit(1);
}

// ---------- 1. gen ----------
console.log('[1] gen-wiki-meta');
const gen = spawnSync(process.execPath, [genScript, wikiDir, '--repo', repoDir], { encoding: 'utf8' });
const genOut = gen.stdout + gen.stderr;
assert(gen.status === 0, 'gen 退出码 0');
assert(/行号越界降级：1 条/.test(genOut), '越界行号降级计数 = 1');
const goodText = readFileSync(join(wikiDir, 'good.md'), 'utf8');
const badText = readFileSync(join(wikiDir, 'bad.md'), 'utf8');
assert(goodText.includes('../repo/pom.xml#L1-L8'), '正常页界内引用带锚重写（#L1-L8）');
assert(/\[pom\.xml:99999-100001\]\(\.\.\/repo\/pom\.xml\)/.test(badText), '越界引用降级为文件级链接（无锚）');
assert(badText.includes('[幽灵页面](missing-page.md)'), '非空括号链接不被 gen 触碰');
assert(badText.includes('[bad-anchor-target]()'), '无法解析的空括号引用保留给 check 报残余');
const indexText = readFileSync(join(wikiDir, 'index.md'), 'utf8');
assert(/## 1\. 指南/.test(indexText), 'index 分组编号目录（## 1. 指南）');
assert(/- 1\.1 \[正常页\]\(good\.md\)/.test(indexText), 'index 页面条目组内编号（1.1 正常页）');
assert(/## 2\. 缺陷域/.test(indexText), 'index 章轴取 PLAN 所属章列（## 2. 缺陷域）');
assert(/- 2\.1 \[缺陷页\]\(bad\.md\)/.test(indexText), '所属章列页面条目编号（2.1 缺陷页）');

// ---------- 2. check ----------
console.log('[2] check-wiki --strict');
const check = spawnSync(process.execPath, [checkScript, wikiDir, '--strict', '--check-diagram-titles', '--repo', repoDir], { encoding: 'utf8' });
const checkOut = check.stdout + check.stderr;
assert(check.status === 1, 'check --strict 退出码 1（缺陷被检出）');
assert(/空括号死链/.test(checkOut), '空括号死链 ERROR');
assert(/Mermaid 块首行无法识别/.test(checkOut), '坏 mermaid 块 ERROR');
assert(/Sources 仅有纯文本文件名/.test(checkOut), '纯文本 Sources WARN');
assert(/图缺 front-matter 图题/.test(checkOut), '无图题 mermaid 块 WARN（--check-diagram-titles）');
assert(/断链：\(missing-page\.md\)/.test(checkOut), '缺失目标断链 ERROR');
assert(/渲染校验跳过|Mermaid 解析失败/.test(checkOut), 'mermaid 校验三态之一被显式输出');
// 正常页零检出：good.md 不应出现在任何 ERROR/WARN 行
const findingLines = checkOut.split('\n').filter((l) => /^(ERROR|WARN) /.test(l));
assert(!findingLines.some((l) => l.includes('good.md')), '正常页零检出（good.md 无 ERROR/WARN）');

// ---------- 汇总 ----------
console.log(`\nselftest：${failures.length ? failures.length + ' 项失败' : '全部断言通过'}（工作区保留于 ${work}）`);
process.exit(failures.length ? 1 : 0);
