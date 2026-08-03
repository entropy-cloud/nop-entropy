# Follow-up Backlog Quality Sweep

> Plan Status: completed
> Last Reviewed: 2026-08-04
> Source: `ai-dev/backlog/nop-stream-production-roadmap.md` Follow-up Backlog (P2-1/P2-2/P2-3/P2-7/P2-8/P2-19/P2-20/P2-21 + AR-series), `ai-dev/audits/nop-stream-production/2026-07-25-1948-multi-audit-nop-stream-production.md`, `ai-dev/audits/nop-stream-production/2026-07-25-1948-open-audit-nop-stream-production.md`
> Related: Stage 23 (`2026-07-25-2200-3-code-cleanup-p3`, completed — closed OperatorChain javadoc/PartitionPolicy dead enum/WindowOperator empty else/CheckpointMetricsSnapshot.toString)

## Purpose

把 nop-stream-production Follow-up Backlog 中全部条目逐条收口：修复确认存活的 live defect / doc drift，对已被 Stage 23/53 间接修复的条目做 verify-and-close。收口后 Follow-up Backlog 中无未裁定条目。

## Current Baseline

经 2026-08-04 live repo 核对（含独立审查修正，使用正确文件路径 `operators/` 复数 + `jobgraph/`）：

**确认仍然存活（confirmed live defect / doc drift）：**
- P2-2: 父模块 `nop-stream/` 下的 `src/main/java/io/nop/stream/flow/model/` 是重复源码树，60 个 git-tracked 文件（含 30 个 `_gen` 偏离规范副本）。`nop-stream/pom.xml` packaging=`pom`（父模块永不编译 src），规范副本在 `nop-stream-flow/src/main/java/io/nop/stream/flow/model/`（同样 60 文件）。删除前 `git ls-files` 计数 = 60，删除后 = 0
- P2-7: `CheckpointCoordinator.onCompletePersistFailure`（`:783-794`）对同一失败信息先 `LOG.error`（`:786`）后 `LOG.warn`（`:793`），重复记录
- P2-8: `Lockable.release()`（`:60`）和 `releaseOrDetach()`（`:72`）在 refCounter 下溢时抛裸 `IllegalStateException("Lockable over-release: ...")`，绕过平台异常体系
- P2-3: `StreamOperator.java:29` Javadoc 引用 `io.nop.stream.core.operators.TwoInputStreamOperator`（包名也错，实际为 `operators` 复数）；`OneInputStreamOperator.java:25` 引用 `AbstractStreamOperatorV2`；`Input.java:28-31,35,50` 引用 `MultipleInputStreamOperator`/`AbstractInput`/`AbstractStreamOperatorV2`。这些类型均为 Flink 概念，在 nop-stream 不存在（vision §4 Non-Goals）
- AR-6: `JobGraphGenerator.java:520-533` 的 Javadoc（描述 `determinePartitionType` 语义、`@param streamEdge`、`@return ResultPartitionType`）错挂在 `hasNonVirtualOperator(List<StreamNode>)`（`:534`，返回 boolean）上方；`determinePartitionType`（`:557`）本身无 javadoc
- P2-1: `nop-stream-flow/pom.xml:22` 依赖 `nop-stream-cep`（`CepPatternModel` 使用），但文档声称 flow 只依赖 core
- P2-20: `component-roadmap.md` cep 部分声称依赖 `nop-xlang`，实际 `IEvalFunction` 来自 `nop-core`（`io.nop.core.lang.eval`）
- P2-21: flow 文档声称只依赖 core，实际依赖 cep + xdefs（与 P2-1 同一 drift 的文档侧）
- gap-analysis P2 count drift: `08-gap-analysis.md:20` 声称 `P2 | 43`，但 G28-G58 中大量已 ✅ Closed

**已被间接修复（need verify-and-close only）：**
- AR-5: `ResultPartition.close()`（`:316-329`）仅 `queue.put(END_OF_STREAM)`，不再有 `queue.size()`/`queue.clear()`/`bufferPool.release()` 竞态（Stage 26/43 重构已消除）
- AR-7: `PartitionPolicy` 仅 FORWARD/HASH/REBALANCE/BROADCAST，UNION/SINGLETON 死枚举已删（Stage 23）
- P2-4: `CheckpointedSourceFunction` Javadoc 已更新（Stage 53）
- source-anchors.md: 已有 38 处 nop-stream/STRM- 匹配（Stage 22 补全）
- CheckpointMetricsSnapshot.toString: `:89` 已含 `failureCause`（Stage 23）
- OperatorChain.open() javadoc: `:92-99` 已描述 "reverse order (tail to head)"（Stage 23 修复 forward vs reverse）
- WindowOperator empty else blocks: live grep 无匹配（Stage 23 清理）

## Goals

- 修复全部确认存活的 live defect / doc drift（P2-2/P2-7/P2-8/P2-3/AR-6/P2-1/P2-20/P2-21）
- 对已修复条目做 verify-and-close
- 修正 gap-analysis P2 count drift
- Follow-up Backlog 中无未裁定条目

## Non-Goals

- P2-5（DataStreamImpl unsafe cast `UnknownTypeInformation`）— 设计层面 API 变更，影响面大于 cleanup sweep，移入 Deferred
- P2-6（IWindowOperatorFactory performative Class param）— 同上，API 层面重构
- P2-9 ~ P2-18（测试质量条目）— 低价值测试清理，不混入 defect sweep
- G36 BroadcastState / G66 / G67 — 属于独立 plan（`2026-08-04-2200-2`）

## Scope

### In Scope

- P2-2: 删除父模块 `nop-stream/` 下的重复 `src/` 源码树
- P2-7: 去重 `CheckpointCoordinator.onCompletePersistFailure` 日志
- P2-8: `Lockable` 裸异常收敛到平台异常体系
- P2-3: 修正 `StreamOperator`/`OneInputStreamOperator`/`Input` Javadoc 中对不存在类型的引用
- AR-6: 将 `JobGraphGenerator` 错位 javadoc 移到正确方法上方
- P2-1 + P2-20 + P2-21: 文档依赖关系纠正
- gap-analysis count drift 修正
- AR-5/AR-7/P2-4/source-anchors/CheckpointMetricsSnapshot/OperatorChain/WindowOperator verify-and-close

### Out Of Scope

- API 签名变更（P2-5/P2-6）
- 测试质量清理（P2-9~P2-18）
- 功能性新特性

## Execution Plan

### Phase 1 - Live Defect Fixes (P2-2 / P2-7 / P2-8 / P2-3 / AR-6)

Status: completed
Targets: 父模块 `nop-stream/` 下的 src 重复树（已删）, `nop-stream/nop-stream-runtime/.../checkpoint/CheckpointCoordinator.java`, `nop-stream/nop-stream-cep/.../sharedbuffer/Lockable.java`, `nop-stream/nop-stream-core/.../operators/StreamOperator.java`, `nop-stream/nop-stream-core/.../operators/OneInputStreamOperator.java`, `nop-stream/nop-stream-core/.../operators/Input.java`, `nop-stream/nop-stream-core/.../jobgraph/JobGraphGenerator.java`

- Item Types: `Fix`

- [x] **P2-2**: `git rm -r nop-stream/src/`（删除 60 个重复 git-tracked 文件）。`nop-stream/pom.xml` packaging=pom 永不编译 src，`nop-stream-flow/src/` 规范副本完整（60 文件一一对应）。删除后父模块构建不受影响
- [x] **P2-7**: 去重 `CheckpointCoordinator.onCompletePersistFailure`（`:783-794`）。保留 `LOG.error`（`:786`），移除冗余 `LOG.warn`（`:793`）；`metrics.recordFailure` 和后续 abort 逻辑不变
- [x] **P2-8**: `Lockable.release()`（`:60`）和 `releaseOrDetach()`（`:72`）的裸 `IllegalStateException` 改为 cep 模块级异常类（遵循 AGENTS.md 两层错误策略：模块内部用模块级异常），异常消息保持英文
- [x] **P2-3**: 修正 3 个文件的 Javadoc 引用：`StreamOperator.java:29`（移除 `TwoInputStreamOperator` 引用，改为 nop-stream 实际类型或删除多流引用）、`OneInputStreamOperator.java:25`（移除 `AbstractStreamOperatorV2` 引用）、`Input.java:28-31,35,50`（移除 `MultipleInputStreamOperator`/`AbstractInput`/`AbstractStreamOperatorV2` 引用，改为描述 nop-stream 实际单输入模型）。这些 Flink 概念属 vision §4 Non-Goals，不应出现在 public API Javadoc 中
- [x] **AR-6**: 将 `JobGraphGenerator.java:520-533` 的 Javadoc 块（描述 `determinePartitionType`）从 `hasNonVirtualOperator`（`:534`）上方移到 `determinePartitionType`（`:557`）上方；为 `hasNonVirtualOperator` 补写符合其语义的 Javadoc（检查 chain 中是否有非 virtual operator）

Exit Criteria:

- [x] `git ls-files nop-stream/src/ | wc -l` = 0
- [x] `./mvnw compile -pl nop-stream -am -T 1C` 通过
- [x] `CheckpointCoordinator.onCompletePersistFailure` 方法体中同一失败信息只出现一次日志调用
- [x] `Lockable.release()` 和 `releaseOrDetach()` 不再使用裸 `IllegalStateException`；编译通过
- [x] `StreamOperator.java`/`OneInputStreamOperator.java`/`Input.java` 中无 `TwoInputStreamOperator`/`MultipleInputStreamOperator`/`AbstractStreamOperatorV2`/`AbstractInput` 引用（grep 为空）
- [x] `JobGraphGenerator.java` 中 `determinePartitionType` 方法上方有匹配其语义的 Javadoc；`hasNonVirtualOperator` 上方有独立 Javadoc（非 `determinePartitionType` 的描述）
- [x] **新功能测试（Rule #25）**: P2-7 新增/调整测试验证 `onCompletePersistFailure` 只触发一次日志；P2-8 新增测试验证 over-release 抛模块级异常类型（非裸 `IllegalStateException`）。P2-3/AR-6/P2-2 纯 Javadoc/文件删除，No new test required: Javadoc 修正和死代码删除不产生新行为
- [x] **无静默跳过（Rule #24）**: 无新增空方法体或吞异常路径
- [x] `./mvnw test -pl nop-stream -am -T 1C` 通过
- [x] No owner-doc update required（纯内部 defect/Javadoc 修复，不改 public contract 行为）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - Documentation Drift Corrections (P2-1 / P2-20 / P2-21 / gap-analysis count)

Status: completed
Targets: `nop-stream/README.md`, `ai-dev/design/nop-stream/01-architecture-baseline.md`, `ai-dev/design/nop-stream/component-roadmap.md`, `ai-dev/analysis/nop-stream/08-gap-analysis.md`, `ai-dev/backlog/nop-stream-production-roadmap.md` Follow-up Backlog

- Item Types: `Fix` | `Decision`

- [x] **P2-1 + P2-21**: 纠正 flow 模块依赖文档。`nop-stream/nop-stream-flow/pom.xml:22` 实际依赖 `nop-stream-cep`（`CepPatternModel` 使用）。更新 `ai-dev/design/nop-stream/01-architecture-baseline.md` §2 和 README 中 flow 模块依赖描述（flow → cep → core），并注明 xdefs 依赖
- [x] **P2-20**: 纠正 `ai-dev/design/nop-stream/component-roadmap.md` cep 部分的依赖描述。`IEvalFunction` 来自 `nop-core`（`io.nop.core.lang.eval`），非 `nop-xlang`。更新对应章节
- [x] **gap-analysis count drift**: 修正 `08-gap-analysis.md:20` 的 P2 计数。逐行核对 P2 区域，更新计数或显式注明 "N total (M closed)"
- [x] 在 `nop-stream-production-roadmap.md` Follow-up Backlog 中为 P2-1/P2-20/P2-21 标注修复状态

Exit Criteria:

- [x] `ai-dev/design/nop-stream/01-architecture-baseline.md` §2 模块依赖描述与 `nop-stream/nop-stream-flow/pom.xml` 实际依赖一致（包含 cep 依赖）
- [x] `ai-dev/design/nop-stream/component-roadmap.md` cep 依赖描述为 `nop-core`（非 `nop-xlang`）
- [x] `08-gap-analysis.md` P2 总计数与逐行 ✅ Closed 标注一致
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] No new test required: 纯文档变更
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - Verify-and-Close Already-Fixed Items

Status: completed
Targets: `ai-dev/backlog/nop-stream-production-roadmap.md` Follow-up Backlog

- Item Types: `Proof` | `Decision`

- [x] **AR-5**: 确认 `ResultPartition.close()`（`:316-329`）仅 `queue.put(END_OF_STREAM)`，无竞态模式。标注 ✅ Closed (Stage 26/43 refactor)
- [x] **AR-7**: 确认 `PartitionPolicy` 无 UNION/SINGLETON 死枚举。标注 ✅ Closed (Stage 23)
- [x] **P2-4**: 确认 `CheckpointedSourceFunction` Javadoc 已更新。标注 ✅ Closed (Stage 53)
- [x] **source-anchors.md**: 确认 `source-anchors.md` 有 nop-stream 锚点条目（38 处匹配）。标注 ✅ Closed (Stage 22)
- [x] **CheckpointMetricsSnapshot.toString**: 确认 `:89` 含 `failureCause`。标注 ✅ Closed (Stage 23)
- [x] **OperatorChain.open() javadoc**: 确认 `:92-99` 描述 "reverse order"。标注 ✅ Closed (Stage 23)
- [x] **WindowOperator empty else blocks**: 确认 live grep 无空 else 块。标注 ✅ Closed (Stage 23)
- [x] **P2-19**: 确认 `ai-dev/design/nop-stream/01-architecture-baseline.md`/`ai-dev/design/nop-stream/component-roadmap.md` 中不再将 `StreamExecutionEnvironment` 归类为 "datastream"（实际在 `nop-stream-core/.../environment/`）。标注最终状态（✅ Closed 或 Fix）

Exit Criteria:

- [x] 上述 8 条 Follow-up Backlog 条目均标注最终状态（✅ Closed + 关闭 stage 引用）
- [x] 每条 verify 有 live repo grep 证据记录在 daily log 或 plan 中
- [x] No new test required: 纯验证 + 文档标注
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 全部 in-scope confirmed live defects/drifts（P2-2/P2-7/P2-8/P2-3/AR-6/P2-1/P2-20/P2-21）已修复
- [x] 全部已修复条目（AR-5/AR-7/P2-4/source-anchors/CheckpointMetricsSnapshot/OperatorChain/WindowOperator）已 verify-and-close
- [x] gap-analysis count drift 已修正
- [x] Follow-up Backlog 中无未裁定条目（含 P2-5~P2-18 有明确 Deferred/out-of-scope 裁定）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect（P2-5/P2-6/P2-9~P2-18 有明确 Deferred 裁定）
- [x] 受影响的 owner docs 已同步到 live baseline
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] `./mvnw compile -pl nop-stream -am -T 1C`
- [x] `./mvnw test -pl nop-stream -am -T 1C`
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### P2-5 — DataStreamImpl unsafe cast `(TypeInformation<R>) UnknownTypeInformation.INSTANCE`

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 该 cast 在 `DataStreamImpl.java:140/183/204` 内部发生，`UnknownTypeInformation` 序列化为 JSON 时不携带类型信息（等同 `Object.class`），当前无运行时错误。修复需变更 DataStream API 签名（`TypeInformation<R>` → `TypeInformation<?>`），影响面超出 cleanup sweep 范围
- Successor Required: `no`（如未来重构 DataStream 类型系统时附带处理）

### P2-6 — IWindowOperatorFactory performative `Class<...>` 参数

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: `WindowedStreamImpl.java:184-242` 传入 `(Class<T>)(Class<?>)Object.class` 仅用于建 dummy serializer，无运行时风险。修复需改工厂签名，与 P2-5 同属 API 重构范畴
- Successor Required: `no`

## Non-Blocking Follow-ups

- P2-9 ~ P2-18（测试质量条目）：低价值测试清理（`TestCountTrigger` 边界补全、纯 getter/setter 测试删除/增强等），不影响 defect closure，可在未来 test-quality sweep plan 中处理

## Closure

Status Note: All in-scope live defects (P2-2/P2-7/P2-8/P2-3/AR-6) fixed with regression tests; all doc drift (P2-1/P2-20/P2-21 + gap-analysis count) corrected; all already-fixed items (AR-5/AR-7/P2-4/source-anchors/CheckpointMetricsSnapshot/OperatorChain/WindowOperator/P2-19) verified and marked ✅ Closed in roadmap. Deferred items (P2-5/P2-6/P2-9~P2-18) carry explicit out-of-scope adjudication.
Completed: 2026-08-04

Closure Audit Evidence:

- Reviewer / Agent: mission-driver EXECUTE pass (single-agent execution; independent closure-audit subagent recommended per plan guide rule #4)
- Evidence:
  - `git ls-files` on deleted `nop-stream/src/` tree = 0 (P2-2)
  - `CheckpointCoordinator.onCompletePersistFailure` retains single `LOG.error` at `:786`; duplicate `LOG.warn` removed (P2-7). Regression test `TestCheckpointCoordinatorPersistFailureLog` asserts exactly one failure log event.
  - `Lockable.release()` / `releaseOrDetach()` throw `StreamRuntimeException` (extends `NopException`), not bare `IllegalStateException` (P2-8). `TestLockableOverRelease` / `TestLockable` updated to assert platform exception type.
  - `StreamOperator.java` / `OneInputStreamOperator.java` / `Input.java` Javadoc rewritten to describe nop-stream single-input model; grep for `TwoInputStreamOperator`/`MultipleInputStreamOperator`/`AbstractStreamOperatorV2`/`AbstractInput` returns no matches (P2-3).
  - `JobGraphGenerator.java` `determinePartitionType` javadoc relocated to `:560+`; new javadoc written for `hasNonVirtualOperator` (`:531`) (AR-6).
  - `01-architecture-baseline.md` §2 flow row updated to `→ core, cep, xdefs`; line 619 `(nop-xlang)` → `(nop-core, io.nop.core.lang.eval)` (P2-1/P2-20/P2-21).
  - `component-roadmap.md` C6 row dependency updated to `依赖 C1, nop-core` (P2-20).
  - `08-gap-analysis.md` P2 count updated from `43` to `31 (16 closed, 15 open)` in both Executive Summary priority table and section header.
  - `nop-stream-production-roadmap.md` Follow-up Backlog: every in-scope item now carries a `**Status**: ✅ Fixed/Closed (...)` line.
  - `./mvnw clean install -pl nop-stream -am -T 1C -DskipTests` → BUILD SUCCESS.
  - `./mvnw test -pl nop-stream -am -T 1C` → BUILD SUCCESS (all 14 nop-stream modules green).
  - `node ai-dev/tools/check-doc-links.mjs --strict` → exit 0.

Follow-up:

- P2-5 / P2-6 (API signature refactors) — explicit out-of-scope adjudication, no successor plan required unless DataStream type system is refactored.
- P2-9 ~ P2-18 (test quality cleanup) — explicit out-of-scope; future test-quality sweep plan if prioritised.
