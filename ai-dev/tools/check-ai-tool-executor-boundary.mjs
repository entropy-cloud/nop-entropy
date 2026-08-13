#!/usr/bin/env node
/**
 * Gate ④ (INV-4, plan 2026-08-12-1120-2 Phase 4): ToolExecutor 安全边界声明门禁.
 *
 * 静态扫描 I0 目标集表 §3.3（30 个实例：27 直接具体 + 3 间接子类；抽象基类
 * AbstractMemoryToolExecutor 显式排除），断言每个实现「安全边界声明存在 + 校验调用点出现」
 * （机械可判）。清单外新执行器未登记即 fail（表完备性）。运行时接线验证不属本门禁
 * （归 I2 接线抽查——声明 ≠ 接线，Lesson 08）。
 *
 * 声明判定（I1 Phase 1 Decision D7，按 §3.3 安全敏感面列）：
 *   - network（类内含 URL/HTTP 处理）：必须引用 SSRF 校验入口
 *     （SsrfAddressGuard / validateUrl / validateHost）；纯委托注入后端
 *     （ISearchEngine）视为有界抽象声明。
 *   - command：必须引用 validateCommand。
 *   - file：必须使用有界抽象（IToolFileSystem / VirtualFileSystem / IResource /
 *     ICompactionArchiveReader），且不得构造裸 java.io.File / java.nio.file 路径
 *     （BashExecutor 归 command 面例外）；边界实现 LocalToolFileSystem 的
 *     isPathAllowed→resolveFile 接线单独检查。
 *   - memory：无外部 IO 面，声明成立（且不得引入裸文件 IO）。
 *   - not-applicable（AskOracleExecutor：oracle client 未实现、fail-fast 无实际
 *     网络 I/O）：必须登记 gate-gaps.yaml（family gate-4-tool-boundary）。
 *
 * 用法:
 *   node ai-dev/tools/check-ai-tool-executor-boundary.mjs            # 真实扫描
 *   node ai-dev/tools/check-ai-tool-executor-boundary.mjs --self-test  # fixture 正反例
 */

import { readFileSync, existsSync } from 'node:fs';
import { resolve, join, sep } from 'node:path';
import { execSync } from 'node:child_process';

const ROOT = resolve(import.meta.dirname, '../..');
const GAP_FILE = join(ROOT, 'ai-dev', 'audits', 'nop-ai-invariants', 'gate-gaps.yaml');
const GAP_FAMILY = 'gate-4-tool-boundary';
const FIXTURES = join(import.meta.dirname, 'fixtures', 'gate4');

const ABSTRACT_EXCLUDED = 'io.nop.ai.agent.tool.AbstractMemoryToolExecutor';

/** I0 §3.3 目标集表（30 实例）。markers：任一出现即「声明成立」；memory 面 = 无外部 IO。 */
const EXECUTORS = [
    // ---- agent 模块：内存/会话面（8 直接 + 3 间接） ----
    { fqcn: 'io.nop.ai.agent.tool.CallAgentExecutor', surface: 'memory',
        file: 'nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/tool/CallAgentExecutor.java' },
    { fqcn: 'io.nop.ai.agent.tool.SendMessageExecutor', surface: 'memory',
        file: 'nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/tool/SendMessageExecutor.java' },
    { fqcn: 'io.nop.ai.agent.tool.SetActiveTagsExecutor', surface: 'memory',
        file: 'nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/tool/SetActiveTagsExecutor.java' },
    { fqcn: 'io.nop.ai.agent.tool.TeamExecuteFlowExecutor', surface: 'memory',
        file: 'nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/tool/TeamExecuteFlowExecutor.java' },
    { fqcn: 'io.nop.ai.agent.tool.TeamSendMessageExecutor', surface: 'memory',
        file: 'nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/tool/TeamSendMessageExecutor.java' },
    { fqcn: 'io.nop.ai.agent.tool.TeamStatusExecutor', surface: 'memory',
        file: 'nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/tool/TeamStatusExecutor.java' },
    { fqcn: 'io.nop.ai.agent.tool.TeamTaskCreateExecutor', surface: 'memory',
        file: 'nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/tool/TeamTaskCreateExecutor.java' },
    { fqcn: 'io.nop.ai.agent.tool.TeamTaskUpdateExecutor', surface: 'memory',
        file: 'nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/tool/TeamTaskUpdateExecutor.java' },
    { fqcn: 'io.nop.ai.agent.tool.ReadMemoryExecutor', surface: 'memory',
        file: 'nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/tool/ReadMemoryExecutor.java' },
    { fqcn: 'io.nop.ai.agent.tool.WriteMemoryExecutor', surface: 'memory',
        file: 'nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/tool/WriteMemoryExecutor.java' },
    { fqcn: 'io.nop.ai.agent.tool.SearchMemoryExecutor', surface: 'memory',
        file: 'nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/tool/SearchMemoryExecutor.java' },
    // ---- toolkit 模块：文件/网络/命令面 ----
    { fqcn: 'io.nop.ai.toolkit.tools.ApplyDeltaExecutor', surface: 'file',
        file: 'nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/ApplyDeltaExecutor.java' },
    { fqcn: 'io.nop.ai.toolkit.tools.AskOracleExecutor', surface: 'network',
        verdict: 'not-applicable',
        file: 'nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/AskOracleExecutor.java' },
    { fqcn: 'io.nop.ai.toolkit.tools.BashExecutor', surface: 'command',
        file: 'nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/BashExecutor.java' },
    { fqcn: 'io.nop.ai.toolkit.tools.CopyFileExecutor', surface: 'file',
        file: 'nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/CopyFileExecutor.java' },
    { fqcn: 'io.nop.ai.toolkit.tools.CreateDirectoryExecutor', surface: 'file',
        file: 'nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/CreateDirectoryExecutor.java' },
    { fqcn: 'io.nop.ai.toolkit.tools.DeleteFileExecutor', surface: 'file',
        file: 'nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/DeleteFileExecutor.java' },
    { fqcn: 'io.nop.ai.toolkit.tools.GraphqlQueryExecutor', surface: 'network',
        file: 'nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/GraphqlQueryExecutor.java' },
    { fqcn: 'io.nop.ai.toolkit.tools.HttpRequestExecutor', surface: 'network',
        file: 'nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/HttpRequestExecutor.java' },
    { fqcn: 'io.nop.ai.toolkit.tools.ListDirectoryExecutor', surface: 'file',
        file: 'nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/ListDirectoryExecutor.java' },
    { fqcn: 'io.nop.ai.toolkit.tools.MoveFileExecutor', surface: 'file',
        file: 'nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/MoveFileExecutor.java' },
    { fqcn: 'io.nop.ai.toolkit.tools.PatchFileExecutor', surface: 'file',
        file: 'nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/PatchFileExecutor.java' },
    { fqcn: 'io.nop.ai.toolkit.tools.ReadFileExecutor', surface: 'file',
        file: 'nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/ReadFileExecutor.java' },
    { fqcn: 'io.nop.ai.toolkit.tools.ReadRefExecutor', surface: 'file',
        file: 'nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/ReadRefExecutor.java' },
    { fqcn: 'io.nop.ai.toolkit.tools.SearchContentExecutor', surface: 'file',
        file: 'nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/SearchContentExecutor.java' },
    { fqcn: 'io.nop.ai.toolkit.tools.SearchEngineExecutor', surface: 'network',
        file: 'nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/SearchEngineExecutor.java' },
    { fqcn: 'io.nop.ai.toolkit.tools.SearchFilesExecutor', surface: 'file',
        file: 'nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/SearchFilesExecutor.java' },
    { fqcn: 'io.nop.ai.toolkit.tools.SkillExecutor', surface: 'file',
        file: 'nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/SkillExecutor.java' },
    { fqcn: 'io.nop.ai.toolkit.tools.UpdateTodosExecutor', surface: 'memory', note: 'I0 表标注文件面，live 为内存 ConcurrentHashMap 实现（无文件 IO），按 live 面判声明成立；偏差记录供 I2 核对',
        file: 'nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/UpdateTodosExecutor.java' },
    { fqcn: 'io.nop.ai.toolkit.tools.WriteFileExecutor', surface: 'file',
        file: 'nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/WriteFileExecutor.java' },
];

/** 面 → 校验入口标记（任一出现即「校验调用点出现」）。 */
const SURFACE_MARKERS = {
    network: ['SsrfAddressGuard', 'validateUrl', 'validateHost', 'ISearchEngine'],
    command: ['validateCommand'],
    file: ['IToolFileSystem', 'VirtualFileSystem', 'IResource', 'ICompactionArchiveReader'],
    memory: [],
};

const RAW_IO_PATTERNS = [/new\s+java\.io\.File\s*\(/, /new\s+File\s*\(/, /java\.nio\.file\.Paths?\s*\./, /Files\.readString\s*\(/, /Files\.write\s*\(/];

/** 边界实现：LocalToolFileSystem 必须把 isPathAllowed 接线进 resolveFile（P1-MA6.2-003 修复面）。 */
const BOUNDARY_IMPL = {
    file: 'nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/fs/LocalToolFileSystem.java',
    markers: ['isPathAllowed', 'resolveFile'],
};

function loadGapInstances(family) {
    if (!existsSync(GAP_FILE)) {
        throw new Error(`known-gaps file missing: ${GAP_FILE}`);
    }
    const lines = readFileSync(GAP_FILE, 'utf8').split('\n');
    let current = null;
    const out = new Set();
    for (const raw of lines) {
        if (raw.trim().startsWith('#')) continue;
        const fam = raw.match(/^([a-z0-9-]+):\s*$/);
        if (fam) { current = fam[1]; continue; }
        if (current === family) {
            const m = raw.match(/^\s*- instance:\s*(.+)$/);
            if (m) out.add(m[1].trim());
        }
    }
    return out;
}

function readSource(root, relPath) {
    const file = join(root, relPath);
    if (!existsSync(file)) throw new Error(`source file missing: ${file}`);
    return readFileSync(file, 'utf8');
}

/** 核心判定：返回违规列表。 */
function checkExecutors(executors, root, gapped) {
    const violations = [];
    const notes = [];
    for (const ex of executors) {
        if (ex.verdict === 'not-applicable') {
            if (!gapped.has(ex.fqcn)) {
                violations.push(`${ex.fqcn}: verdict not-applicable but not registered in gate-gaps.yaml (${GAP_FAMILY})`);
            }
            continue;
        }
        if (gapped.has(ex.fqcn)) continue;
        const source = readSource(root, ex.file);
        const markers = SURFACE_MARKERS[ex.surface] || [];
        const declared = markers.length === 0 || markers.some((m) => source.includes(m));
        if (!declared) {
            violations.push(`${ex.fqcn} (${ex.file}): surface=${ex.surface} but no boundary marker found (${markers.join(' / ')})`);
        }
        if (ex.surface === 'memory' || ex.surface === 'file') {
            for (const p of RAW_IO_PATTERNS) {
                if (p.test(source)) {
                    violations.push(`${ex.fqcn} (${ex.file}): raw filesystem IO construct found (${p}) — must use bounded abstraction (${markers.join(' / ')} or none)`);
                }
            }
        }
        if (ex.note) notes.push(`[gate-4] note: ${ex.fqcn} — ${ex.note}`);
    }
    return { violations, notes };
}

/** 表完备性：§3.3 复现命令反查（implements IToolExecutor + extends AbstractMemoryToolExecutor）。 */
function completenessViolations(root) {
    const violations = [];
    const tableSet = new Set(EXECUTORS.map((e) => e.fqcn));
    const direct = execSync(
        `grep -rn "implements\\s\\+IToolExecutor\\b" nop-ai --include="*.java" | grep -v target || true`,
        { cwd: root, encoding: 'utf8' });
    const indirect = execSync(
        `grep -rln "extends AbstractMemoryToolExecutor" nop-ai --include="*.java" | grep -v target || true`,
        { cwd: root, encoding: 'utf8' });
    const found = new Set();
    for (const line of direct.split('\n').filter(Boolean)) {
        const m = line.match(/(nop-ai\/[^:]+\.java)/);
        if (!m) continue;
        const path = m[1].replaceAll(sep, '/');
        if (path.includes('/src/test/') || path.includes('/_gen/')) continue;
        const fqcn = pathToFqcn(path);
        if (!fqcn) continue;
        found.add(fqcn);
    }
    for (const line of indirect.split('\n').filter(Boolean)) {
        const m = line.match(/(nop-ai\/[^:]+\.java)/);
        if (!m) continue;
        const path = m[1].replaceAll(sep, '/');
        if (path.includes('/src/test/') || path.includes('/_gen/')) continue;
        const fqcn = pathToFqcn(path);
        if (fqcn) found.add(fqcn);
    }
    found.delete(ABSTRACT_EXCLUDED);
    for (const fqcn of [...found].sort()) {
        if (!tableSet.has(fqcn)) {
            violations.push(`${fqcn}: IToolExecutor implementor NOT in the gate table — add to invariant-catalog §3.3 (Loop Rule)`);
        }
    }
    for (const fqcn of tableSet) {
        if (!found.has(fqcn) && EXECUTORS.some((e) => e.fqcn === fqcn && !e.verdict)) {
            violations.push(`${fqcn}: in the gate table but no longer found by the §3.3 reproduction grep`);
        }
    }
    return violations;
}

function pathToFqcn(relPath) {
    const m = relPath.match(/nop-ai\/nop-ai-[^/]+\/src\/main\/java\/(.+)\.java$/);
    if (!m) return null;
    return m[1].replaceAll('/', '.');
}

function runReal() {
    const gapped = loadGapInstances(GAP_FAMILY);
    const { violations, notes } = checkExecutors(EXECUTORS, ROOT, gapped);
    for (const n of notes) console.log(n);
    const comp = completenessViolations(ROOT);
    const boundary = BOUNDARY_IMPL;
    const bSrc = readSource(ROOT, boundary.file);
    const boundaryOk = boundary.markers.every((m) => bSrc.includes(m));
    console.log(`\n=== 门禁④ ToolExecutor 安全边界声明检查 ===`);
    console.log(`目标集: ${EXECUTORS.length} 实例（§3.3，抽象基类排除）`);
    console.log(`边界实现 ${boundary.file}: ${boundaryOk ? 'OK (isPathAllowed 接线 resolveFile)' : 'MISSING'}`);
    if (violations.length === 0 && comp.length === 0 && boundaryOk) {
        console.log('结果: PASS — 全部实例声明成立，表完备性一致，零清单外缺口');
        return 0;
    }
    for (const v of violations) console.log(`  [FAIL] ${v}`);
    for (const v of comp) console.log(`  [FAIL] ${v}`);
    if (!boundaryOk) console.log('  [FAIL] LocalToolFileSystem 边界接线缺失');
    return 1;
}

/** fixture 正反例自测：证明门禁真的在拦截（非空转），并验证判定逻辑。 */
function runSelfTest() {
    const fixtureRoot = FIXTURES;
    if (!existsSync(fixtureRoot)) {
        throw new Error(`fixture dir missing: ${fixtureRoot}`);
    }
    const fakeTable = [
        { fqcn: 'fixture.GoodFileExecutor', surface: 'file', file: 'good/FileExecutor.java' },
        { fqcn: 'fixture.GoodMemoryExecutor', surface: 'memory', file: 'good/MemoryExecutor.java' },
        { fqcn: 'fixture.BadNetworkExecutor', surface: 'network', file: 'bad/NetworkExecutor.java' },
        { fqcn: 'fixture.BadRawFileExecutor', surface: 'file', file: 'bad/RawFileExecutor.java' },
        { fqcn: 'fixture.BadCommandExecutor', surface: 'command', file: 'bad/CommandExecutor.java' },
        { fqcn: 'fixture.NotApplicableExecutor', surface: 'network', verdict: 'not-applicable',
            file: 'gapped/NotApplicableExecutor.java' },
    ];
    let failed = 0;
    const check = (cond, label) => {
        console.log(`  ${label}: ${cond ? 'PASS' : 'FAIL'}`);
        if (!cond) failed++;
    };
    const run = (table, gapped) => checkExecutors(table, fixtureRoot, gapped).violations;

    console.log('\n=== 门禁④ self-test（fixture 正反例）===');
    // 空清单下运行：正例绿、反例红、not-applicable 未登记红
    const violations = run(fakeTable, new Set());
    check(!violations.some((v) => v.includes('GoodFileExecutor')), 'good-file-executor（有界抽象）green');
    check(!violations.some((v) => v.includes('GoodMemoryExecutor')), 'good-memory-executor（无外部 IO）green');
    check(violations.some((v) => v.includes('BadNetworkExecutor')), 'bad-network-executor（无 SSRF 标记）red');
    check(violations.some((v) => v.includes('BadRawFileExecutor')), 'bad-raw-file-executor（裸 File 构造）red');
    check(violations.some((v) => v.includes('BadCommandExecutor')), 'bad-command-executor（无 validateCommand）red');
    check(violations.some((v) => v.includes('NotApplicableExecutor')), 'not-applicable 未登记 red');
    // 登记后：not-applicable 实例绿（known-gaps 机制生效）
    const violationsGapped = run(fakeTable, new Set(['fixture.NotApplicableExecutor']));
    check(!violationsGapped.some((v) => v.includes('NotApplicableExecutor')),
        'not-applicable 登记后 green（known-gaps 机制）');
    console.log(failed === 0 ? 'self-test: all PASS' : `self-test: ${failed} FAILED`);
    return failed === 0 ? 0 : 1;
}

const args = process.argv.slice(2);
if (args.includes('--self-test')) {
    process.exit(runSelfTest());
}
process.exit(runReal());
