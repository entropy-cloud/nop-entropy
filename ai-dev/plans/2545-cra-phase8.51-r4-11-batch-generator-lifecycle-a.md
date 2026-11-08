# 2545 CRA Phase 8.51 — R4-11 1.7 generator/生命周期束 a（G23 前半，11 工单）

> Plan Status: active
> Last Reviewed: 2026-11-08
> Source: roadmap 工作项 8.51（`ai-dev/backlog/concurrency-resource-audit-remediation-roadmap.md` live :222，Status=**todo**；Owner Doc=`docs-for-ai/03-modules/nop-batch.md`；Deps=「batch-proposal.md §二 R4-11（11 项）；硬依赖 8.22——已 done（plan 2516），硬依赖解除」）；批次定义 = `ai-dev/audits/2026-10/2026-10-23-1400-cra-phase7-cross-module-adjudication/batch-proposal.md` §二 :126 R4-11 行（11 finding ID / 模块 nop-batch / 验证命令 `./mvnw test -pl nop-batch -am` / PA=无）+ §二.5 :178 G23 组行「22 | R4-11 + R4-12 | 拆分 11/11」；成员发现详情 = `ai-dev/audits/2026-10/2026-10-16-1258-deep-audit-nop-batch-concurrency/`（C1-shared-mutable-state.md C1-001/C1-002/C1-003；C1-round2.md C1-006；C5-memory-bounds.md C5-003/C5-004；C6-resource-lifecycle.md C6-003/C6-004；C6-round2.md C6-007/C6-008；C7-long-running.md C7-004）
> Related: plan 2516（R2-13，completed）为本批同模块前批；C5-004 与 5.4-C5-004 双轨联动（R3-04）不双计；C10-001/002 为验收前置

## Purpose

关闭 R4-11 批 11 项 P2 工单（generator 游标 / 生命周期回调 / 资源窗 / 读侧包络 / 重试语义）。

## Current Baseline

- **判级**：11 项全 P2（merge-adjudication 单元内裁定）。
- **依赖**：硬依赖 8.22（plan 2516）**已 done，解除**。
- **PA 判定（重要）**：本批站点绝大多数在 `nop-batch/*`（非 Protected Area）。**唯一例外**：C1-003 的 F3 同族清单包含 `nop-kernel/nop-core` 的 `ExecutionContextImpl.onBeforeComplete/onAfterComplete`（平台框架核心 = **Protected Area plan-first**）。本批**只修 nop-batch 侧载体**（`BatchTaskContextImpl`），平台侧同型两面**登记为 successor 并显式不做**（见 Deferred 节）；不在本批静默改 nop-core。
- **基线测试签名**（起草期前）：`./mvnw test -pl nop-batch` 全绿；本批结束时逐模块记录。

## Goals

- 11 工单全部收口（10 项 nop-batch 侧 Fix + 1 项 C1-003 的 nop-batch 侧半边，平台半边显式 successor）。
- 每条工单 ≥1 可辨识验收载体；关键项先红后绿或变异论证。
- E5 owner doc 落账（`nop-batch.md`）。

## Non-Goals

- **不改 `nop-kernel/nop-core` 的 `ExecutionContextImpl`**（Protected Area plan-first，无授权）→ C1-003 平台半边登记 successor。
- 不做 R4-12（8.52）成员（C7-005..C7-012、C8-004..C8-010）。
- 不改 orm 模型 / `_` 生成物 / xdef schema 结构（C7-004 只做装配期校验，不加 schema 约束）。
- 不做 C5-004 的双轨对端（5.4-C5-004，归 R3-04 已收口面）。

## Scope

### In Scope

- `nop-batch/nop-batch-core`：`BatchTaskGlobals`、`impl/BatchTask`、`impl/BatchTaskContextImpl`、`gen` 侧在 `nop-batch-gen`（`BatchGenState`、`SequentialBatchRequestGenerator`）、`consumer/BatchProcessorConsumer`、`consumer/AbstractRetryBatchConsumer`、`consumer/RetryConsumeHelper`、`loader/ListBatchLoader`、`loader/AsyncFetchPartitionDispatchLoaderProvider`、`BatchConfigs`。
- `nop-batch/nop-batch-exp`：`ExportDbTool`、`ImportDbTool`。
- `nop-batch/nop-batch-dsl`：`BatchRetryPolicyModel`。
- 测试树 + `docs-for-ai/03-modules/nop-batch.md`。

### Out Of Scope

Non-Goals 所列全部。

## Execution Plan

### Phase 1 - 设计裁定

Status: completed
Targets: 本 plan 的裁定表（模块内小批次，无跨模块契约变更，不单建设计文档）

- Item Types: `Decision`

- [x] 裁定表落 Plan（见下「裁定 A」）——本批无跨模块契约变更，且 11 项各只有一种合理形态，按 roadmap「轻量通道」不单建 design 文档
- [x] `No owner-doc update required`（本 Phase；owner doc 归 Phase 3）
- [x] `ai-dev/logs/` 已更新

**裁定 A（本批唯一形态）**

| 工单 | 唯一形态 |
|---|---|
| C1-001 | `BatchTaskGlobals` 增 `saveTaskContext()/restoreTaskContext(prev)`；`BatchTask` 两处 provide 站点改 save/restore（不再裸 provide + remove） |
| C1-002 | `BatchGenState` 的 `hasNext/getGenCount/getSubIndex/next` 全部 `synchronized`（锁粒度=游标载体） |
| C1-003 | `BatchTaskContextImpl` 增 `taskBeginFired` 标记；`onTaskBegin` 在 fired 后显式抛错（不再静默接受后丢弃）。平台侧 `ExecutionContextImpl` 同型两面 = successor（PA 未授权） |
| C1-006 | `SequentialBatchRequestGenerator.nextRequest/onResponse` 实例级 `synchronized` |
| C5-003 | `BatchProcessorConsumer` 增 `maxOutputItemsPerChunk`（缺省 0=不限制），add 前超限抛 `ERR_BATCH_TOO_MANY_PROCESSING_ITEMS` |
| C5-004 | `ListBatchLoader` 构造期超阈值 WARN（键 `nop.batch.list-loader.warn-size`，缺省 1e6，<=0 关闭）+ owner doc 声明包络；**不拒绝**（拒绝会破坏既有合法大表导出） |
| C6-003 | `ExportDbTool`/`ImportDbTool` 的 executor 创建 + beforeExport/beforeImport + `syncConfigWithDb` 全部移入 try；finally 判 null |
| C6-004 | 两工具 finally 增 `waitTerminated`（stats 活跃/队列双零即视为已停，上限 30s，超时 WARN）；不引入平台设施改动 |
| C6-007 | 同 C6-003（Import 侧 `syncConfigWithDb` 移入 try，使 `needClose` finally 覆盖池创建后的全部失败路径） |
| C6-008 | `AsyncFetchPartitionDispatchLoaderProvider` 的 fetch 线程在每次 load 后 `ctx.complete()`；异常路径 `ctx.completeExceptionally(e)` |
| C7-004 | `BatchRetryPolicyModel` 负 `maxRetryCount` 装配期显式失败（语义反转陷阱）；`AbstractRetryBatchConsumer` 删入口 `checkRetry(e,0)` 预检查（消除 +2 计数与双链不一致） |

Exit Criteria:

- [x] 裁定表在案且每项唯一形态
- [x] `ai-dev/logs/` 已更新

### Phase 2 - 11 工单实现 + 回归测试

Status: completed
Targets: nop-batch-core / -gen / -exp / -dsl + 测试树

- Item Types: `Fix`（11）、`Proof`

- [x] W-01 C1-001：TL save/restore（`BatchTaskGlobals` + `BatchTask` 两站点）
- [x] W-02 C1-002：`BatchGenState` 游标同步
- [x] W-03 C1-003：`taskBeginFired` 守卫（batch 侧）
- [x] W-04 C5-004：`ListBatchLoader` 常驻规模 WARN + 配置键
- [x] W-05 C5-003：`BatchProcessorConsumer` 输出集上界（**closure audit B-2 后重做**：配置键 `nop.batch.max-output-items-per-chunk` + `BatchTaskBuilder` 生产接线 + 计数改 AtomicInteger 避免异步 O(n²)）
- [x] W-06 C1-006：`SequentialBatchRequestGenerator` 实例同步
- [x] W-07 C6-003/C6-008：exp 工具 try 窗重构（含 `syncConfigWithDb` 入 try）+ C6-008 **closure audit B-1 后改形**为装配期 fail-fast（`ERR_BATCH_SAVE_STATE_WITH_DISPATCHER_UNSUPPORTED`），移除空转的 fetch-ctx `complete()`
- [x] W-08 C6-007：ImportDbTool `syncConfigWithDb` 移入 try
- [x] W-09 C6-004：两工具 `waitTerminated` 有界等待
- [x] W-10 C7-004：负 maxRetryCount fail-fast + 移除入口预检查（**closure audit M-3 后改形**：裸 `IllegalArgumentException` → `BatchDslErrors.ERR_BATCH_RETRY_MAX_COUNT_NEGATIVE` + `NopException`；入口预检查移除后死方法一并删除）
- [x] W-11 C1-004 死字段 `onTaskEnds` 删除（C1-003 F3 附项，声明面钉随宿主同 diff）
- [x] 回归测试：既有 nop-batch 全量测试 | 新增钉见下
- [x] **无静默跳过**（rule 24）：WARN/抛错全部显式，无空 catch

Exit Criteria:

- [x] 11 工单全部 landed
- [x] `./mvnw test -pl nop-batch` BUILD SUCCESS（逐模块签名见 Phase 3）
- [x] 零触碰核对：`nop-kernel`/`nop-tcc`/`nop-retry` 零改动；orm/`_` 生成物零改动
- [x] `ai-dev/logs/` 已更新

**测试载体清单（closure audit M-2 后补全，逐工单 ≥1）**

| 工单 | 载体 | 性质 |
|---|---|---|
| C1-001 | `pin01`/`pin01b`（TL save/restore 两腿） | 行为钉 |
| C1-002 | `BatchGenState` 四方法 synchronized（结构面）+ 既有 `TestBatchGenModel` 回归 | 结构钉（诚实登记：并发红不可确定性构造） |
| C1-003 | `pin03`（fire 后注册 fail-fast） | 行为钉 |
| C1-006 | `SequentialBatchRequestGenerator` 两方法 synchronized（结构面）+ DSL 全量回归 | 结构钉（诚实登记） |
| C5-003 | `pin05`/`pin05b`（超限 fail-fast / 不限制）+ **`pin06` 配置键经 builder 真实接线**（closure audit B-2） | 行为钉 |
| C5-004 | `pin04`（超大 List 只告警不拒绝） | 契约钉 |
| C6-003 | `TestDbToolExecutorContract`（executor 契约）+ owner doc 声明面 | 契约钉 |
| C6-004 | `TestDbToolExecutorContract`（waitTerminated 有界等待） | 契约钉 |
| C6-007 | `TestImportDbTool`（Import 全链）+ `syncConfigWithDb` 入 try 的代码面 | 回归钉 |
| C6-008 | **`pin07`（saveState + 异步派发 fail-fast）**（closure audit B-1） | 行为钉（变异红证） |
| C7-004 | 负值 fail-fast（`ERR_BATCH_RETRY_MAX_COUNT_NEGATIVE`）+ 计数修正由 `TestBatchBugVerification` 既有重试面承载 | 类型化钉 + 回归 |

### Phase 3 - 全量验证 + owner doc 落账

Status: completed
Targets: nop-batch 全量 + owner doc

- Item Types: `Proof`

- [x] E3 分解通道：`./mvnw test -pl nop-batch` BUILD SUCCESS + 逐模块签名记录
- [x] owner doc 落账：`nop-batch.md`（TL 嵌套语义 / 生成器并发语义 / 输出集上界键 / List 常驻包络 / exp 工具优雅停机 / 重试语义边界）
- [x] 工具门禁：check-doc-links --strict EXIT=0；scan-hollow `--module nop-batch --severity high` EXIT=0
- [x] `ai-dev/logs/` 已更新

Exit Criteria:

- [x] E3 验收且逐条登记
- [x] owner doc 落账核对
- [x] 双门禁 EXIT=0 登记
- [x] `ai-dev/logs/` 已更新

### Phase 4 - closure audit + 收口

Status: planned
Targets: 本 plan、roadmap 8.51 行、cra-index P2-G23 束行

- Item Types: `Proof`

- [x] 独立子 agent closure audit（fresh session，agent_5b478355）：逐条核对 Exit Criteria / Closure Gates / Anti-Hollow / C1-003 平台半边 successor 边界 → **R1 REJECTED（2B+5M+6m）**，Blocker/Major 全部随收口闭环
- [x] R1 Blocker/Major 修复：B-1 C6-008 改形装配期 fail-fast（移除空转 fetch-ctx complete）；B-2 C5-003 配置键 + builder 真实接线；M-1 owner doc 配置键订正；M-2 逐工单载体补全（见上表）；M-3 类型化错误码；M-4 提交溯源说明；M-5 Phase 4 收口
- [x] Minor 修复：m-1 测试数订正（core 82）；m-2 验证命令改子模块枚举；m-3 死方法 `checkRetry` 删除；m-4 `ExportDbTool.syncConfigWithDb` 移入 try；m-5 `pin04` 保护力诚实登记；m-6 异步 `size()` → `AtomicInteger`
- [ ] cra-index 台账回填：P2-G23 束行（R4-11 半边收敛注）+ roadmap 8.51 → done
- [ ] 双门禁 + Closure Evidence 写入本 plan

Exit Criteria:

- [ ] closure audit 证据写入 Closure 段（R2 复审仍需一轮 fresh session）
- [ ] cra-index + roadmap 在案
- [ ] 双门禁 EXIT=0

## Closure Gates

- [ ] 11 工单全部收口（10 项 nop-batch Fix + C1-003 batch 侧半边）
- [ ] 每工单 ≥1 条验收载体
- [ ] `./mvnw test -pl nop-batch` BUILD SUCCESS
- [ ] owner doc 落账完成
- [ ] scan-hollow nop-batch `--severity high` EXIT=0
- [ ] 不存在被静默降级的 in-scope live defect（C1-003 平台半边为 PA 未授权 successor，非降级）
- [ ] 独立子 agent closure-audit 完成并记录证据
- [ ] **Anti-Hollow Check**
- [ ] `check-plan-checklist --strict` EXIT=0；`check-doc-links --strict` EXIT=0
- [ ] cra-index P2-G23 收敛注 + roadmap 8.51 → done

## Deferred But Adjudicated

### C1-003 平台半边（`ExecutionContextImpl.onBeforeComplete` / `onAfterComplete` 的 swap-on-fire 同型缺口）

- Classification: `out-of-scope improvement`（Protected Area 未授权）
- Why Not Blocking Closure: `nop-kernel/nop-core` 属 AGENTS.md「框架核心引擎（nop-core/nop-xlang/nop-xdef 内部）」= **plan-first** Protected Area；本批 PA=无，无授权改平台核心。另：审计原文已核实仓内全部注册点均在 fire 之前，平台半边当前不可触发（潜在面）。
- Successor Required: `yes`
- Successor Path: R-G 治理批（roadmap 8.74）中登记，或独立 plan-first 批次。

## Non-Blocking Follow-ups

- `ListBatchLoader` 的超大 List 目前只 WARN 不拒绝；若后续出现 OOM 实例，考虑加 fail-fast 开关（需 owner doc 契约变更）。

## Closure

Status Note: （收口时填写）
Completed: （YYYY-MM-DD）

Closure Audit Evidence:

- Reviewer / Agent: （独立子 agent fresh session）
- Audit Session: （session ID）
- Evidence:
  - （每条 Exit Criterion / Closure Gate 的 PASS/FAIL）
  - `check-plan-checklist --strict` 退出码
  - Anti-Hollow 结果 + scan-hollow 退出码
  - Deferred 分类检查

Follow-up:

- （仅 non-blocking）
