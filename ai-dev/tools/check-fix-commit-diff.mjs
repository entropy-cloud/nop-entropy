#!/usr/bin/env node
/**
 * Gate ⑤ (INV-5, plan 2026-08-12-1120-2 Phase 4): fix-commit real-diff 验证门禁.
 *
 * 扫描 git 历史中 `fix(nop-ai)` 模式 commit（范围 = 2026-07-31 审计关闭后全部，含 PR 内
 * commit），断言实质 diff > 0。实质 diff = 增删行数（排除纯版权头 / 纯空白行 / 生成文件路径）
 * 之和 > 0。零实质变更的 fix commit = overclaimed closure（Lesson 05 判定规则），报错退出 1。
 *
 * 门禁⑤为 commit 卫生门禁，输出 = 违规 commit 清单（不适用 known-gaps 清单，由 I2/I3 消费）。
 *
 * 用法:
 *   node ai-dev/tools/check-fix-commit-diff.mjs                     # 本地模式：--since 2026-07-31
 *   node ai-dev/tools/check-fix-commit-diff.mjs --base-ref <sha>    # PR/CI 模式：<base>..HEAD
 *   node ai-dev/tools/check-fix-commit-diff.mjs --repo <dir> [--since ...]  # 指定仓库（self-test 用）
 *   node ai-dev/tools/check-fix-commit-diff.mjs --self-test         # _tmp 构造正反例仓库自测
 */

import { execSync } from 'node:child_process';
import { existsSync, mkdtempSync, writeFileSync, rmSync, mkdirSync } from 'node:fs';
import { resolve, join } from 'node:path';
import { tmpdir } from 'node:os';

const ROOT = resolve(import.meta.dirname, '../..');
const SINCE_DEFAULT = '2026-07-31 00:00';

/** 生成/非产品路径：target、_gen、下划线前缀文件（codegen 产物）。测试文件计入（回归测试是实质内容）。 */
function isExcludedPath(path) {
    if (!path) return true;
    if (path.includes('/target/') || path.includes('/_gen/')) return true;
    const base = path.split('/').pop() || '';
    return base.startsWith('_');
}

/** 单行是否为非实质变更（版权头 / 纯空白）。 */
function isNonSubstantiveLine(text) {
    const stripped = text.trim();
    return stripped.length === 0 || /copyright|©|版权所有/i.test(stripped);
}

function git(repo, args, opts = {}) {
    const cmd = ['git', '-C', repo, ...args].map((a) => `"${a}"`).join(' ');
    try {
        return execSync(cmd, { encoding: 'utf8', ...opts });
    } catch (e) {
        if (opts.allowFail) return '';
        throw new Error(`git command failed: ${cmd}\n${e.stderr || e.message}`);
    }
}

/** 计算一个 commit 的实质 diff 行数（hunk 内按行配对，纯空白差异 = 非实质）。 */
function substantiveDiff(repo, commit) {
    const files = git(repo, ['diff-tree', '--no-commit-id', '--name-only', '-r', commit])
        .split('\n').filter(Boolean);
    let substantive = 0;
    for (const file of files) {
        if (isExcludedPath(file)) continue;
        const patch = git(repo, ['diff', `${commit}^`, commit, '--', file], { allowFail: true });
        if (!patch) continue;
        // hunk 内配对：removed[i].trim() === added[j].trim() 视为纯空白差异（跳过），
        // 其余新增/删除行计入实质 diff（版权头/纯空白行除外）。
        const pendingRemoved = [];
        const flushRemoved = () => {
            for (const r of pendingRemoved) {
                if (!isNonSubstantiveLine(r)) substantive++;
            }
            pendingRemoved.length = 0;
        };
        for (const line of patch.split('\n')) {
            if (line.startsWith('+++') || line.startsWith('---')) continue;
            if (line.startsWith('@@')) {
                flushRemoved();
                continue;
            }
            if (line.startsWith('+')) {
                const added = line.slice(1);
                const idx = pendingRemoved.findIndex((r) => r.trim() === added.trim());
                if (idx >= 0) {
                    pendingRemoved.splice(idx, 1);
                } else if (!isNonSubstantiveLine(added)) {
                    substantive++;
                }
                continue;
            }
            if (line.startsWith('-')) {
                pendingRemoved.push(line.slice(1));
                continue;
            }
            // context line — 之前的 removed 行无法与新行配对，全部计为实质
            flushRemoved();
        }
        flushRemoved();
    }
    return substantive;
}

function collectFixCommits(repo, baseRef, since) {
    // R-5-1 (I3): subject-only matching. `git log --grep=fix(nop-ai)` matches
    // the subject AND every body line (a chore/feat commit whose body merely
    // mentions the prefix is wrongly scanned). The subject is already in the
    // output via `--format=%H %s`, so the JS-side startsWith filter is the
    // ONLY candidate filter — body mentions are excluded by construction.
    const args = ['log', '--no-merges', '--format=%H %s'];
    if (baseRef) {
        args.push(`${baseRef}..HEAD`);
    } else if (since) {
        args.push(`--since=${since}`);
    } else {
        args.push(`--since=${SINCE_DEFAULT}`);
    }
    const out = git(repo, args);
    return out.split('\n').filter(Boolean).map((line) => {
        const sp = line.indexOf(' ');
        return { hash: line.slice(0, sp), subject: line.slice(sp + 1) };
    }).filter((c) => c.subject.startsWith('fix(nop-ai)'));
}

function runReal(repo, baseRef, since) {
    const commits = collectFixCommits(repo, baseRef, since);
    const violations = [];
    console.log(`\n=== 门禁⑤ fix-commit real-diff 验证 ===`);
    console.log(`范围: ${baseRef ? `PR 区间 ${baseRef}..HEAD` : `since ${since || SINCE_DEFAULT}`}`);
    console.log(`扫描到 fix(nop-ai) commit: ${commits.length}`);
    for (const c of commits) {
        const n = substantiveDiff(repo, c.hash);
        const status = n > 0 ? 'OK' : 'VIOLATION';
        console.log(`  ${status}  ${c.hash}  diff=${n}  ${c.subject}`);
        if (n <= 0) {
            violations.push(`${c.hash} ${c.subject}: zero substantive diff (overclaimed closure, Lesson 05)`);
        }
    }
    if (violations.length > 0) {
        console.log(`\n违规 fix commit（交 I2/I3 消费）:`);
        for (const v of violations) console.log(`  - ${v}`);
        return 1;
    }
    console.log('结果: PASS — 全部 fix(nop-ai) commit 含实质 diff，零违规');
    return 0;
}

/** 自测：_tmp 构造正反例仓库。 */
function runSelfTest() {
    const tmp = mkdtempSync(join(ROOT, '_tmp', 'gate5-selftest-'));
    try {
        git(tmp, ['init', '-q']);
        git(tmp, ['config', 'user.email', 'gate5-selftest@local']);
        git(tmp, ['config', 'user.name', 'gate5-selftest']);
        mkdirSync(join(tmp, 'nop-ai', 'nop-ai-core', 'src', 'main', 'java'), { recursive: true });

        const src = join(tmp, 'nop-ai', 'nop-ai-core', 'src', 'main', 'java', 'A.java');
        // base commit（feat，不匹配 fix(nop-ai)）
        writeFileSync(src, 'public class A {\n}\n');
        git(tmp, ['add', '.']);
        git(tmp, ['commit', '-q', '-m', 'feat(nop-ai): base']);

        // 正例：fix(nop-ai) 实质 diff
        writeFileSync(src, 'public class A {\n    public int x = 1;\n}\n');
        git(tmp, ['add', '.']);
        git(tmp, ['commit', '-q', '-m', 'fix(nop-ai): real change']);

        // 反例 1：fix(nop-ai) 仅空白
        writeFileSync(src, 'public class A {\n    public int x = 1;    \n}\n');
        git(tmp, ['add', '.']);
        git(tmp, ['commit', '-q', '-m', 'fix(nop-ai): whitespace only']);

        // 反例 2：fix(nop-ai) 仅版权头
        writeFileSync(src, '// Copyright 2026 example\npublic class A {\n    public int x = 1;    \n}\n');
        git(tmp, ['add', '.']);
        git(tmp, ['commit', '-q', '-m', 'fix(nop-ai): copyright header only']);

        // 反例 3：fix(nop-ai) 仅生成文件（_gen）
        mkdirSync(join(tmp, 'nop-ai', '_gen'), { recursive: true });
        writeFileSync(join(tmp, 'nop-ai', '_gen', 'Gen.java'), 'public class Gen {}\n');
        git(tmp, ['add', '.']);
        git(tmp, ['commit', '-q', '-m', 'fix(nop-ai): generated file only']);

        // R-5-1 反例：subject 非 fix(nop-ai) 但 body 首行以 'fix(nop-ai)' 开头。
        // `--grep` 会误匹配（逐行匹配 body），subject-only 过滤必须排除它。
        writeFileSync(src, 'public class A {\n    public int x = 2;\n}\n');
        git(tmp, ['add', '.']);
        git(tmp, ['commit', '-q', '-m', 'chore(ci): add invariant gate job\n\nfix(nop-ai): mentioned only in the body, not a fix commit']);

        let failed = 0;
        const check = (cond, label) => {
            console.log(`  ${label}: ${cond ? 'PASS' : 'FAIL'}`);
            if (!cond) failed++;
        };
        console.log('\n=== 门禁⑤ self-test（_tmp 正反例仓库）===');
        const commits = collectFixCommits(tmp, null, '2020-01-01');
        const bySubject = {};
        for (const c of commits) bySubject[c.subject] = c;
        check(commits.length === 4, `4 个 fix(nop-ai) commit 被扫描到（实际 ${commits.length}）`);
        check(!bySubject['chore(ci): add invariant gate job'],
            'R-5-1: subject 非 fix(nop-ai) 但 body 首行含该字样的 commit 必须被排除出候选集');
        check(substantiveDiff(tmp, bySubject['fix(nop-ai): real change'].hash) > 0,
            '正例（实质 diff）通过');
        check(substantiveDiff(tmp, bySubject['fix(nop-ai): whitespace only'].hash) === 0,
            '反例（仅空白）判零');
        check(substantiveDiff(tmp, bySubject['fix(nop-ai): copyright header only'].hash) === 0,
            '反例（仅版权头）判零');
        check(substantiveDiff(tmp, bySubject['fix(nop-ai): generated file only'].hash) === 0,
            '反例（仅生成文件）判零');

        // PR 模式（base-ref）：base = 首个 commit → 区间内 5 个 commit，
        // subject-only 过滤后 4 个 fix commit
        const base = git(tmp, ['rev-list', '--max-parents=0', 'HEAD']).trim();
        const prCommits = collectFixCommits(tmp, base, null);
        check(prCommits.length === 4, `PR 模式（--base-ref）扫描到 4 个（实际 ${prCommits.length}）`);

        // 全流程：4 个 commit 中 1 正 3 反 → exit 1
        const code = runReal(tmp, null, '2020-01-01');
        check(code === 1, '整体门禁在存在违规 commit 时退出 1（实际 exit ' + code + '）');
        console.log(failed === 0 ? 'self-test: all PASS' : `self-test: ${failed} FAILED`);
        return failed === 0 ? 0 : 1;
    } finally {
        rmSync(tmp, { recursive: true, force: true });
    }
}

const args = process.argv.slice(2);
if (args.includes('--self-test')) {
    process.exit(runSelfTest());
}
const repo = args.includes('--repo') ? args[args.indexOf('--repo') + 1] : ROOT;
const baseRef = args.includes('--base-ref') ? args[args.indexOf('--base-ref') + 1] : null;
const since = args.includes('--since') ? args[args.indexOf('--since') + 1] : null;
process.exit(runReal(repo, baseRef, since));
