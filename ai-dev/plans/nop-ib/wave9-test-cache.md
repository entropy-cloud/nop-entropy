# Wave 9 — 测试结果缓存（opt-in `--cache`）

> Plan Status: completed
> Last Reviewed: 2026-11-17
> Source: [nop-ib-roadmap.md](../../backlog/nop-ib-roadmap.md) Wave 9（owner 指令 2026-11-16）
> Related: [wave8-mvnd-gradle-comparison.md](wave8-mvnd-gradle-comparison.md)（content-addressable 裁定的定向翻案：opt-in + 显式 cached 标记消解假绿顾虑）

## Purpose

owner 需求：增加命令行参数 `--cache`，启用后"代码与全部依赖都没变且上次跑绿"的模块跳过 JUnit 执行，直接复用上次的绿结果——但输出必须显式区分 cached 与 executed（零假绿）。

## Current Baseline（2026-11-16 live repo）

- 默认 affected 模式：git 无变更 → `no affected tests selected` exit 0（已存在的跳过）。
- `-pl <module>` 显式执行请求：无条件全量跑（对齐 mvn 语义）。
- 单元索引已持有每类 contentHash + ABI digest（`index/UnitIndex`），可作指纹原料，无需重扫文件。
- 无任何 up-to-date/lastGreen 机制（grep 核实）；Deferred 26 曾裁定 content-addressable cache 不吸收——本 plan 以 **opt-in + 显式 cached 标记 + 只缓存绿** 三重限定定向翻案。
- 模型过期闸门（Wave 6 已集中）：staleness → 所有分支回退 Maven。

## Goals

- `--cache` 开关：`test -pl` 在指纹命中且上次 exit=0 时跳过执行，输出 `kind=cached-up-to-date` + `cached:true`。
- `--force` 逃逸开关。
- 缓存内容只存绿结果（红结果永不入缓存——预存红模块天然每次真跑）。
- liteflow 实测：二跑 cached，首跑 executed，JSON 核验。

## Non-Goals

- 类级部分缓存（只跑变化的类）——本轮只做模块级绿缓存，类级粒度登记 follow-up。
- 默认启用——不改变不带 `--cache` 的任何行为。

## Scope

### In Scope

- `nop-tools/nop-ib`：`TestCacheStore`（新）、`TestCommand` 接线、`NopIbMain` 参数。
- 单元测试钉 + liteflow 实测 + 文档收口。

### Out Of Scope

- Maven fallback 路径的缓存（mvn 语义不可代理）。
- `--rerun-failed`/`--class` 显式执行路径的缓存（执行请求语义）。

## Execution Plan

### Phase 1 — 缓存存储与指纹

Status: completed
Targets: `model/../cli/TestCacheStore.java`（新，放 cli 或独立包）

Item Types: `Feature`

- [x] `TestCacheStore`：`.nop-ib/test-cache.json` 读写；条目 = {artifactId, fingerprint, exitCode, summary{tests,failed,errors,skipped}, cachedAt}
- [x] 指纹 = sha256(模块单元索引全部 (className, contentHash) 排序表 + ABI digest 表 + externalArtifacts + surefire 全部 facts(argLine/skip/includes/excludes/groups/excludedGroups/sysprops/env) + 相关依赖模块的 ABI digest 表)；索引缺失/模型过期 → 指纹 null（永不命中）
- [x] 单元钉：指纹稳定性（同输入同指纹/任一成分变化换指纹）、存储往返、红结果拒存

Exit Criteria:

- [x] 指纹/存储钉全绿
- [x] 无静默跳过：指纹不可得 → null → 不命中（显式）

### Phase 2 — 命令接线

Status: completed
Targets: `NopIbMain`、`TestCommand`

Item Types: `Feature`

- [x] `--cache` / `--force` 参数解析并传入 test/check
- [x] `-pl` 分支接线：命中 → TestRun(kind="cached-up-to-date", cached=true, exit=OK) 直接 report，不 fork；未命中 → 正常执行，exit=0 时写缓存
- [x] plan scope 分支同样接线（selected/full 模块）；`--class`/`--rerun-failed`/maven fallback 不缓存
- [x] 单元钉：cached TestRun 的 JSON 含 cached 字段、--force 绕过

Exit Criteria:

- [x] 全部钉绿；`./mvnw test -pl nop-tools/nop-ib` 全绿
- [x] cached 输出显式（人类行 + JSON 均含 cached 标记）

### Phase 3 — 实测 + 文档收口 + closure

Status: completed
Targets: `~/sources/liteflow`、design 05、roadmap

Item Types: `Proof | Follow-up`

- [x] liteflow nospring 双跑实测：首跑 executed、二跑 cached、`--force` 重执行；JSON kind 字段核验；结果表入 analysis
- [x] design 05 增补缓存契约段（命中条件 + 不可见输入披露：外部 DB/容器/环境变量不在指纹内）
- [x] roadmap Wave 9 勾选 + Status；daily log
- [x] 独立子 agent closure audit APPROVED，Evidence 落本 plan；提交

Exit Criteria:

- [x] 实测表入库（cached/executed/force 三行）
- [x] `--cache` 缺省时行为与现版本逐字节一致（无行为漂移）
- [x] check-plan-checklist --strict 退出 0
- [x] closure audit APPROVED

## Closure Gates

- [x] 零假绿：cached 显式标记、红不入缓存、--force 逃逸、stale/缺索引不命中
- [x] 钉全绿 + `./mvnw test -pl nop-tools/nop-ib` 全绿
- [x] liteflow 实测三态（executed/cached/force）在档
- [x] design 05 + roadmap 同步
- [x] 独立 closure audit APPROVED（evidence 落 plan）
- [x] 提交

## Deferred But Adjudicated

### 类级部分缓存（只跑指纹变化的类）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 模块级绿缓存已覆盖 owner 诉求（"全没变→跳过"）；类级粒度涉及部分执行合并语义，复杂度高
- Successor Required: no
- Successor Path: 需要时再立项

## Closure

Status Note: 收口。实现事实经独立 audit 核实为真实落地（指纹成分齐全、红拒存、cached 不 fork、--force 逃逸、testCompile 目录修复、liteflow 三态实测在档）；audit 发现的 5 项条件（M1 Phase2 钉缺失、M2 design 05 契约段、m1 JSON cached 字段、m2 证据路径、m3 依赖索引披露）已全部修复；79/79 全绿。
Completed: 2026-11-17

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（closure audit task，2026-11-17）
- Evidence:
  - Phase 1 Exit Criteria：指纹/存储钉 PASS（TestTestCacheStore 4/4 实跑）；无静默跳过 PASS（TestCacheStore.java:87-96,124-132 fail-safe null）
  - Phase 2 Exit Criteria：--cache/--force 参数 PASS（NopIbMain.java:98-103）；-pl 接线 PASS（TestCommand.java:246-249,425-441）；audit M1 修复后补钉 TestRunModuleMaybeCached 3 tests（hit→cached-up-to-date 不 fork / --force 绕过 / cache=null 原路径）；m1 修复后 report() 含 cached:true
  - Phase 3 Exit Criteria：liteflow 三态实测在档（~/sources/liteflow/_tmp/nop-ib/test-20261116-*.log，cached 42.6s→~2s）；--cache 缺省零漂移 PASS；--strict 退出 0
  - Closure Gates：零假绿 PASS；79/79 全绿（./mvnw test -pl nop-tools/nop-ib）；design 05 §八 契约段已落地（M2 修复）；附带缺陷（testCompile 输出目录）修复核实 PASS（IncrementalCompileExecutor.java:163-182）
  - Anti-Hollow：runModuleMaybeCached 调用链从 test/check 入口到 cache.hit/record 追踪连通；cached 分支无 runModuleTests 调用（审计核实 TestCommand.java:425-441）
  - Deferred 分类检查：类级部分缓存=optimization candidate，non-blocking 成立
  - scan-hollow-implementations: 退出 0
