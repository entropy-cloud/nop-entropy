# 286 nop-job Assignment 分派元数据重构

> Plan Status: completed
> Last Reviewed: 2026-07-07
> Source: `ai-dev/analysis/2026-07/2026-07-07-nop-job-cluster-assignment-design-review.md`; `ai-dev/design/nop-job/worker-assignment-design.md`
> Related: `ai-dev/plans/213-nop-job-partition-mode.md`, `ai-dev/plans/215-nop-job-best-fit-dispatch.md`, `ai-dev/plans/269-nop-job-dispatch-config-index-quality.md`

## Purpose

收敛 `Assignment` 作为 worker 分派结果的内部契约，使 bestFit/assignment 路径能以强类型方式表达已有 `NopJobTask` 分派列可承载的路由/分片元数据。同时修正 `Assignment.cost` 当前由策略设置但未进入 task 的语义漂移。

本计划只做 coordinator 内部小切片：不改 ORM 模型、不改 `ResourceVector` public API、不改旧 `IWorkerAssignmentStrategy` 签名、不重写 partition/broadcast 调度语义。

## Current Baseline

- `Assignment` 位于 `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/Assignment.java`，当前只有 `workerInstanceId` 与 `ResourceVector cost`。
- `AssignmentPlan` 位于同包，已是 `@DataBean`，持有 `List<Assignment>` 并提供 `empty()` 与 `isEmpty()`。
- `IWorkerAssignmentStrategy.assign(ResourceVector taskCost, List<WorkerLoad> workers)` 当前只被 `AdaptiveJobTaskBuilder` / `LeastLoadedStrategy` 的 bestFit 路径使用。
- `LeastLoadedStrategy` 会设置 `Assignment.cost = taskCost`，但 `AdaptiveJobTaskBuilder` 当前只读取 `assignment.getWorkerInstanceId()`，最终 task cost 使用本地 `taskCost`。
- `AdaptiveJobTaskBuilder` 当前只消费 `plan.getAssignments().get(0)` 并生成一个 task；bestFit 路径没有定义多 assignment 语义。
- `JobDispatcherScannerImpl.scanOnce()` 在 builder 返回 tasks 后，当前会统一用 schedule 的 `taskCostCpu`、`taskCostMemory`、`priority` 覆盖每个 task 的对应值；代码注释称 builder 已设置值“不受影响”，但 live behavior 与注释不一致。
- `NopJobTask` 字段定义源自 `nop-job/model/nop-job.orm.xml`，生成 getter/setter 在 `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/entity/_gen/_NopJobTask.java`，保留类 `NopJobTask.java` 本身为空；禁止手改生成物。
- `NopJobTask` 已有强类型分派列：`workerInstanceId`、`partitionIndex`、`targetHost`、`shardingIndex`、`shardingTotal`、`partitionRange`、`costCpu`、`costMemory`、`priority`，以及 `taskPayload` JSON component。
- `PartitionTaskBuilder` 当前直接使用 `IDiscoveryClient` + `WeightedPartitionAssigner`，按 `ServiceInstance.weight` 静态加权切 hash range，不使用 `IWorkerAssignmentStrategy`。
- `RpcBroadcastTaskBuilder` 当前保持全员广播语义，为每个 healthy + enabled 实例生成 task，不使用 `IWorkerAssignmentStrategy`。
- `ai-dev/design/nop-job/worker-assignment-design.md` 仍是 draft，并包含早期接口/字段描述；需要与 live repo 和本计划的保守重构边界对齐。

## Goals

- `Assignment` 能强类型表达已有 `NopJobTask` 分派列对应的路由/分片元数据，不以无约束 `Map<String,Object>` 承载框架字段。
- `AdaptiveJobTaskBuilder` 将 `Assignment` 中的白名单分派元数据映射到 `NopJobTask` 已有列。
- `Assignment.cost` 的语义被明确并接入 task cost 写入路径，避免策略输出字段成为死数据。
- `JobDispatcherScannerImpl` 的 cost/priority 归一逻辑保留 builder-set 值，只为未设置值做 schedule 填充和 null→0 归一。
- bestFit 路径的 `AssignmentPlan` 多 assignment 情况被显式裁定：本计划保持单 task bestFit 语义，非单 assignment 必须 fail fast，不得静默忽略。
- 保持 partition 与 broadcast 的现有语义不变；本计划只补齐 bestFit/assignment 路径的内部契约。

## Non-Goals

- 不新增或修改 `nop-job/model/nop-job.orm.xml` 字段、索引或生成物。
- 不修改 `nop-job/nop-job-api/src/main/java/io/nop/job/api/resource/ResourceVector.java`。
- 不修改 `IWorkerAssignmentStrategy` 方法签名。
- 不新增 `Map<String,Object> params/attributes` 作为本轮框架字段承载方式。
- 不把 `PartitionTaskBuilder`、`RpcBroadcastTaskBuilder` 强制迁移到旧 `IWorkerAssignmentStrategy`。
- 不实现 context-aware 新 SPI（如 `IWorkerAssignmentPlanner`）。
- 不支持 bestFit 一次 fire 生成多个 task；`AssignmentPlan` 多 assignment 语义留给后续 context-aware planner 或新 dispatchMode。
- 不做实时负载感知 partition 或按瞬时 loadScore 动态重分片。

## Scope

### In Scope

- `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/Assignment.java`
- `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/AssignmentPlan.java`（如需保持 DataBean 构造/序列化一致性）
- `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/LeastLoadedStrategy.java`（只允许保持行为并适配 assignment 字段）
- `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/AdaptiveJobTaskBuilder.java`
- `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobDispatcherScannerImpl.java`
- `nop-job/nop-job-coordinator/src/test/java/io/nop/job/coordinator/engine/` 下的 focused tests
- `ai-dev/design/nop-job/worker-assignment-design.md`
- `docs-for-ai/03-modules/nop-job.md`
- `ai-dev/logs/2026/07-07.md`

### Out Of Scope

- `nop-job/model/nop-job.orm.xml` 与任何 `_gen/`、`_*.xml`、`_*.java` 生成物。
- `ResourceVector` 维度扩展。
- partition/broadcast worker 选择策略重构。
- 新增 worker candidate provider 或 context-aware planner。
- 修改 worker scan/claim 逻辑。

## Execution Plan

### Phase 1 - 设计契约同步与裁定

Status: completed
Targets: `ai-dev/design/nop-job/worker-assignment-design.md`, `docs-for-ai/03-modules/nop-job.md`

- Item Types: `Decision`, `Fix`, `Proof`

- [x] 更新 worker assignment 设计文档，将 live baseline 对齐到当前实现：`IWorkerAssignmentStrategy` 仍是 `taskCost + workers` 形式；partition/broadcast 不走旧 strategy；`Assignment` 的强类型分派元数据仅用于 bestFit/assignment 路径。
- [x] 清理或重写 worker assignment 设计文档中与 live repo 冲突的历史草案内容，包括：`DefaultJobTaskBuilder` 已不再写 coordinator hostId、`NopJobTask` 已有 cost/partitionRange 等字段、旧 `IWorkerAssignmentStrategy.assign(NopJobFire, workers)` 签名、`Assignment.partitionRange` 类型描述。
- [x] 裁定 `Assignment.cost` 语义：strategy 返回的 cost 是本次 assignment 的 task cost；若非空，应进入 `NopJobTask.costCpu/costMemory` 写入路径；若为空，回退到 schedule task cost。
- [x] 裁定 dispatcher cost/priority 归一语义：dispatcher 只填充 builder 未设置的 cost/priority，并继续把 null 归一为 0；不再无条件覆盖 builder 已明确设置的值。
- [x] 裁定 `docs-for-ai/03-modules/nop-job.md` 的待更新内容，但不在本 Phase 修改 owner doc 中尚未由 live code 支撑的新行为；具体 owner-doc 更新放到 Phase 3 代码落地后完成。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `ai-dev/design/nop-job/worker-assignment-design.md` 不再描述与 live repo 冲突的旧 `IWorkerAssignmentStrategy` 签名、旧字段状态或过度统一 dispatchMode 的方案。
- [x] `ai-dev/design/nop-job/worker-assignment-design.md` 已被收敛为当前设计基线，不再保留会误导执行的历史 Phase / Proposed-vs-current / 旧 baseline 叙事；如仍保留 draft 状态，需明确哪些部分仍是 draft、哪些是 live baseline。
- [x] `Assignment.cost` 与 dispatcher cost/priority 保留规则的决策已写入 `ai-dev/design/nop-job/worker-assignment-design.md`。
- [x] `docs-for-ai/03-modules/nop-job.md` 的待更新点已在本 plan Phase 3 中列为必做，且本 Phase 未提前发布未落地 live behavior。
- [x] No production code change in this phase; no new test required because this phase only synchronizes design/owner-doc contract.
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码为 0。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - Assignment 强类型分派元数据落地

Status: completed
Targets: `Assignment.java`, `AssignmentPlan.java`, `LeastLoadedStrategy.java`, focused unit tests

- Item Types: `Fix`, `Proof`

- [x] 扩展 `Assignment`，用强类型属性表达已有 `NopJobTask` 分派列可承载的元数据：`targetHost`、`shardingIndex`、`shardingTotal`、`partitionRange`。
- [x] 明确新增属性的 Java 类型必须与 task 列语义一致：`targetHost` 与 `partitionRange` 为字符串；`shardingIndex` 与 `shardingTotal` 为整数；`partitionRange` 不使用 `IntRangeBean`。
- [x] 保持 `Assignment` 为 `@DataBean`，并确保新增属性满足 DataBean/JSON 反序列化约定。
- [x] 不添加无约束 `Map<String,Object> params/attributes`。
- [x] 保持 `LeastLoadedStrategy` 现有 worker 选择行为不变，只让它继续返回最小必要 assignment 数据。
- [x] 为 `Assignment` DataBean 序列化/反序列化或 BeanTool 构造路径补 focused test，覆盖新增属性。

Exit Criteria:

- [x] `Assignment` 能表达 target host、sharding index、sharding total、partition range，且字段名/类型与 `NopJobTask` 现有列语义一致。
- [x] `Assignment` 新增属性不要求 ORM 模型变更，不触碰生成文件。
- [x] `LeastLoadedStrategy` 现有最少负载选择测试仍通过，行为未改变。
- [x] **新增功能测试**：新增属性可通过 JSON/DataBean 或等价 BeanTool 路径构造和读取。
- [x] **无静默跳过**：新增属性若类型非法，走现有反序列化/类型转换错误路径，而非被吞掉。
- [x] No owner-doc update required in this phase; design裁定已在 Phase 1 记录，用户可见 owner-doc 行为更新在 Phase 3 代码落地后完成。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - AdaptiveJobTaskBuilder 映射与 dispatcher 保留规则

Status: completed
Targets: `AdaptiveJobTaskBuilder.java`, `JobDispatcherScannerImpl.java`, focused tests

- Item Types: `Fix`, `Proof`

- [x] `AdaptiveJobTaskBuilder` 从 `Assignment` 中读取白名单分派元数据，并映射到 `NopJobTask` 已有列。
- [x] `AdaptiveJobTaskBuilder` 根据 Phase 1 裁定处理 `Assignment.cost`：非空 assignment cost 写入 task cost，空值回退到 schedule task cost。
- [x] `AdaptiveJobTaskBuilder` 保持 bestFit 单 task 语义：`AssignmentPlan` 必须恰好包含一个 assignment；0 个走 no fitting worker 既有错误语义，多于 1 个显式失败。
- [x] `AdaptiveJobTaskBuilder` 校验单个 assignment 的有效性：assignment 本身不能为空，`workerInstanceId` 不能为空或空白；非法 assignment 显式失败。
- [x] `JobDispatcherScannerImpl` 的 cost/priority 归一逻辑调整为“保留 builder 已设置的值，只填充 null 并归一为 0”。
- [x] 同步修正 `JobDispatcherScannerImpl` 中关于 builder 已设置值不受影响的现有注释，使注释与新行为一致。
- [x] 保持 `DefaultJobTaskBuilder`、`PartitionTaskBuilder`、`RpcBroadcastTaskBuilder` 行为不变；它们未设置 cost/priority 时仍由 dispatcher 填充 schedule 默认值。
- [x] 不改变 `dispatchMode=partition` 与 `rpcBroadcast/broadcast` 的 worker 选择语义。
- [x] 同步 `docs-for-ai/03-modules/nop-job.md`：记录 dispatcher 对 builder-set cost/priority 的保留规则，以及未设置时从 schedule 填充并 null→0 归一。

Exit Criteria:

- [x] focused test：自定义 strategy 返回带 target host、sharding index、sharding total、partition range 的 assignment，`AdaptiveJobTaskBuilder` 生成的 `NopJobTask` 对应列正确。
- [x] focused test：strategy 返回非空 assignment cost 时，task cost 使用 assignment cost；assignment cost 为空时，task cost 回退到 schedule cost。
- [x] focused test：strategy 返回多于一个 assignment 时，`AdaptiveJobTaskBuilder` 显式失败，不静默忽略后续 assignment。
- [x] focused test：strategy 返回 null assignment、或 assignment 的 `workerInstanceId` 为 null/blank 时，`AdaptiveJobTaskBuilder` 显式失败。
- [x] focused test：dispatcher 不覆盖 builder 已设置的 cost/priority；builder 未设置时，dispatcher 仍按 schedule 值填充并把 null 归一为 0。
- [x] focused test：priority 保留规则作为 dispatcher-level invariant 用 custom/stub builder 验证；不要求 `Assignment` 增加 priority 字段。
- [x] regression test：default/single 仍满足 `workerInstanceId == null`，保留 task payload 与 `partitionIndex`；partition 仍设置 `workerInstanceId/shardingIndex/shardingTotal/partitionRange/partitionIndex` 并覆盖 `[0,32767]`；rpcBroadcast/broadcast 仍设置 `workerInstanceId/targetHost(addr:port)/shardingIndex/shardingTotal/partitionIndex`。
- [x] **接线验证**：`JobDispatcherScannerImpl.scanOnce()` 实际解析 `nopJobTaskBuilder_bestFit`，调用自定义 strategy，经 `AdaptiveJobTaskBuilder` 映射并经 insert tasks 流程落到 `NopJobTask`。
- [x] **端到端验证**：至少一个 `dispatchMode=bestFit` 场景从 waiting fire → dispatcher → inserted task 完整验证 assignment 元数据和 cost 结果。
- [x] **无静默跳过**：no fitting worker 仍抛 `ERR_JOB_NO_FITTING_WORKER`；多 assignment 显式失败；`loadProvider == null` 仍抛 `ERR_JOB_WORKER_CAPACITY_PROVIDER_REQUIRED`。
- [x] `docs-for-ai/03-modules/nop-job.md` 已更新并与实现一致。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 4 - 验证、文档链接与收口审计

Status: completed
Targets: `nop-job`, `docs-for-ai/03-modules/nop-job.md`, `ai-dev/design/nop-job/worker-assignment-design.md`, this plan

- Item Types: `Proof`, `Decision`

- [x] 运行 nop-job 受影响模块测试，至少覆盖 coordinator 及其依赖。
- [x] 运行文档链接检查，必须退出码为 0。
- [x] 启动独立 closure audit 子 agent，验证 plan exit criteria、调用链、测试证据与 deferred 分类。
- [x] 根据 closure audit 结果修复发现的问题，或将非阻塞项移入 `Deferred But Adjudicated` 并说明理由。
- [x] closure audit evidence 写入 `Closure` 段落后，运行 final plan checklist 检查。

Exit Criteria:

- [x] `./mvnw test -pl nop-job/nop-job-coordinator -am` 通过。
- [x] 如跨模块影响超出 coordinator，`./mvnw test -pl nop-job -am` 通过；否则记录不运行全 nop-job 的理由。
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码为 0。
- [x] 独立 closure audit evidence 写入 `Closure` 段落。
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/286-nop-job-assignment-metadata-refactor.md --strict` 退出码为 0（关闭前，且必须在 closure evidence 写入后运行）。
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-job --severity high` 退出码为 0。
- [x] 编译期代码规范检查通过：`./mvnw test -pl nop-job/nop-job-coordinator -am` 获得 BUILD SUCCESS，并记录命令和结果。
- [x] `ai-dev/logs/` 对应日期收口条目已更新。

## Closure Gates

> 只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] `Assignment` 强类型分派元数据已落地，且未引入无约束 params 作为框架字段承载方式。
- [x] `Assignment.cost` 不再是死数据：非空 assignment cost 已进入 task cost 写入路径，空值回退 schedule cost。
- [x] bestFit 多 assignment 情况显式失败，不静默忽略。
- [x] dispatcher cost/priority 归一逻辑与 Phase 1 裁定一致，并有 focused tests。
- [x] partition/broadcast 现有语义保持不变，并有 regression 证据。
- [x] 不涉及 ORM 模型变更、`ResourceVector` public API 变更、旧 `IWorkerAssignmentStrategy` 签名变更。
- [x] 受影响 design/owner docs 与 live repo 一致。
- [x] 必要 focused verification 与端到端/接线验证已完成。
- [x] 不存在被静默降级到 deferred/follow-up 的 in-scope live defect 或 contract drift。
- [x] 独立子 agent closure audit 已完成并记录 evidence。
- [x] **Anti-Hollow Check**：closure audit 已验证 assignment 元数据从 strategy 返回、经 builder 映射、经 dispatcher insert 落到 task 的运行时调用链连通。
- [x] `./mvnw test -pl nop-job/nop-job-coordinator -am` 通过。
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码为 0。
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/286-nop-job-assignment-metadata-refactor.md --strict` 退出码为 0。
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-job --severity high` 退出码为 0。
- [x] 编译期代码规范检查通过：`./mvnw test -pl nop-job/nop-job-coordinator -am` 获得 BUILD SUCCESS，并记录命令和结果。

## Deferred But Adjudicated

### 无约束 `Assignment.params/attributes`

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 本计划目标是先补齐框架已有强类型 task 列的 assignment 表达能力。无约束 map 会降低类型安全，且需要额外 key 白名单/常量治理。
- Successor Required: no

### context-aware assignment planner 新 SPI

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 旧 `IWorkerAssignmentStrategy` 上下文不足，统一 bestFit/partition/broadcast 需要新的设计文档与迁移方案；本计划只处理现有 bestFit/assignment 路径的契约漂移。
- Successor Required: no
- Successor Path: 未来如实施，再创建独立 design/plan；本计划 closure 不依赖 successor。

### partition 实时负载感知或候选过滤

- Classification: `optimization candidate`
- Why Not Blocking Closure: 当前 partition 已按 `ServiceInstance.weight` 静态加权，实时负载感知可能破坏分区稳定性；需单独设计和测试，不影响本计划的 assignment 元数据重构。
- Successor Required: no

### `ResourceVector` 自定义维度

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 涉及 `nop-job-api` public API、序列化、资源聚合与可能的 ORM 变更，必须独立 plan-first。
- Successor Required: no

## Non-Blocking Follow-ups

- 若业务需要自定义 assignment 扩展字段，另行设计 key 白名单与 taskPayload 映射规则。
- 若未来需要让 partition 复用候选 worker 获取逻辑，优先设计 `IWorkerCandidateProvider` 或 context-aware planner，不回改本计划。

## Closure

Status Note: `Assignment` typed metadata、assignment cost 语义、dispatcher preserve 规则与 bestFit fail-fast 已全部落地；focused tests、end-to-end wiring proof、owner docs、全量 `nop-job -am` 验证与独立 closure audit 均已完成，plan scope 可以关闭。
Completed: 2026-07-07

Closure Audit Evidence:

- Reviewer / Agent: general subagent
- Audit Session: `ses_0c2f325f8ffedB6VyKvhz9fJUy`
- Evidence:
  - Exit Criteria verification: PASS；re-audit 确认 `TestAdaptiveJobTaskBuilder` 现已覆盖 `JsonTool.parseBeanFromText(..., Assignment.class)` 与 `BeanTool.buildBean(..., Assignment.class)` 的 typed metadata/cost 构造路径，补齐此前 closure blocker。
  - Closure Gates verification: PASS；独立审计确认 typed assignment metadata 映射、assignment cost override/fallback、multi-assignment fail-fast、dispatcher preserve-only normalization、partition/broadcast regression、以及无 ORM/`ResourceVector`/旧 strategy 签名漂移均与 live code 一致。
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/286-nop-job-assignment-metadata-refactor.md --strict`: PASS。
  - Anti-Hollow scan: PASS；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-job --severity high` 返回 High severity findings: 0。
  - Deferred classification check: PASS；deferred 项仍为 out-of-scope improvement / optimization candidate，无 in-scope defect 被降级。

Follow-up:

- no remaining plan-owned work.
