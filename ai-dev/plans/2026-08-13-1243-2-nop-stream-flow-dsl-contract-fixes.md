# 2 nop-stream-flow DSL 契约修复：edge 分区属性静默忽略（P1）+ checkpoint/窗口策略属性静默忽略（P1）+ 模块错误码接入（P1）

> Plan Status: completed
> Last Reviewed: 2026-08-13
> Source: `ai-dev/audits/2026-08-13-0805-multi-audit-nop-stream-invariant-loop.md`（P1-XDSL-5 / P1-XDSL-6 / P1-09-02）
> Related: `2026-08-13-1243-1`（运行时引擎）、`2026-08-13-1243-3`（跨模块契约）

## Purpose

把 nop-stream-flow 模块的 DSL 声明式入口三处"静默忽略/静默降级"收口：`<edge>` 的 partition/keyExpr/流控属性声明后零消费（P1-XDSL-5）；checkpoint 配置 6 字段与窗口策略 trigger/allowedLateness/accumulationMode 声明后零消费（P1-XDSL-6）；整个模块 58 处裸异常、零错误码/模块异常类使用（P1-09-02）。核心判定：**已确认的 contract drift 只能以 `Fix` 落地，不得降级为 follow-up**；未实现的功能必须 fail-fast（No-Silent-No-Op Rule），实现的功能必须语义落地并测试。

## Current Baseline

（live repo 2026-08-13 复核）

- `StreamModelDslBuilder.resolveEdgePartition`（`:414-422`）定义后全仓 0 调用者；`buildTransforms` 中 edges 仅用于拓扑排序；edge 的 `partition`/`keyExpr`/`flowControlPolicy`/`queueCapacity`/`receiveWindow`/`packetSize` 六个属性在 main 代码零消费（grep 复核确认）。`test-reduce-pipeline.stream.xml:20` 的 `partition="HASH"` 恰好依赖此静默降级才通过测试。
- `applyCheckpointConfig` 仅消费 enabled/interval/processingGuarantee/timeout/maxConcurrentCheckpoints/minPause/maxRetainedCheckpoints/jobTerminationMode；`barrierAlignmentTimeout`/`maxConsecutiveCheckpointFailures`/`storageType`/`jobId`/`pipelineId`/`<storageConfig>` 六个 getter 在 main 代码 0 引用（`StreamModelDslBuilder.java:124-150`）。**core `CheckpointConfig` 存在全部六个对应 setter**（`CheckpointConfig.java:132,233,271,279,295,303`），checkpoint 字段可完整实现。
- `buildWindow` 只读 strategyRef + strategy 的 windowFnId；`triggerId`/`allowedLateness`/`accumulationMode` getter 0 引用（`AdvancedTransforms.java:108-158`）；`TestAdvancedTransforms` 中 `triggerId="t"` 也从未被消费。**core `WindowedStream` 无 `allowedLateness()`/`accumulationMode()` API**（仅 `trigger()`/`evictor()`，`WindowedStream.java:34-88`）；`DataStream` 无 `rebalance`/`broadcast`/`partitionCustom` API（仅 `keyBy`）——实现路径受限，需按 Non-Goals 裁定 fail-fast。
- flow 模块 grep `NopStreamErrors|ERR_STREAM_|StreamException` 全部 0 命中；58 处抛裸 IAE/UOE/ISE（`StreamModelDslBuilder.java` 24 处、`AdvancedTransforms.java` 23 处、`builder/functions/*.java` 6 处、resolver 族 5 处——执行时以 grep 实测为准）。
- 全量测试基线 2895 tests / 0 failures（2026-08-13 I5）。既有异常类型断言依赖裸异常类型：`TestAdvancedTransforms`（4× IAE + 2× UOE）、`TestStreamModelDslBuilderFailFast`（7× UOE）、`TestStreamModelDeltaFailFast`（1× UOE）、`TestBeanFunctionResolver`（2× IAE）——Phase 3 迁移时必须同步更新这些断言（`TestAdvancedTransforms` 另有 2 处已用 `StreamException` 的断言无需改动）。

## Goals

- 用户声明的 edge 分区/流控属性要么语义落地（仅 HASH+keyExpr 可经 `keyBy` 落地），要么构建期 fail-fast 且错误信息可定位到 DSL 声明位置（edge id + 属性名）。**本 plan 裁定：REBALANCE/BROADCAST/flowControl 四属性（`flowControlPolicy`/`queueCapacity`/`receiveWindow`/`packetSize`）与无 keyExpr 的 HASH 一律 fail-fast**（core `DataStream` 无对应算子 API，见 Non-Goals）。
- checkpoint 配置 6 字段**完整实现**（core setter 存在）并经 `env.getCheckpointConfig()` 语义断言验证。
- 窗口策略 3 属性裁定：`triggerId`/`allowedLateness`/`accumulationMode` 非默认值即 fail-fast（`WindowedStream` 无 allowedLateness/accumulationMode API；trigger 语义需额外注册表，见 Non-Goals）。
- flow 模块全部裸异常迁移为模块异常类（`StreamException`）带错误码；高频错误（duplicate id、not-yet-implemented、类型校验）定义专用 `ERR_STREAM_*` 码。
- 本 plan 涉及属性在参考示例 DSL（`test-reduce-pipeline.stream.xml`）中的声明与修复后行为一致（HASH → FORWARD 或 keyBy 落地）。

## Non-Goals

- 不实现分布式分区执行的数据面语义（REBALANCE/BROADCAST/流控四属性 fail-fast 即收口；分区运行时不入本 plan）。
- 不新增 core 公共 API（不给 `WindowedStream` 加 `allowedLateness()`、不给 `DataStream` 加 partition 算子——跨模块公共契约变更属 plan-first + 人工确认，不在自动修复信封内）。
- 不触碰 `nop-kernel/nop-xdefs` 的 stream.xdef/pattern.xdef schema 结构（属性定义已存在，本 plan 只消费或 fail-fast）。
- 不处理 source/sink 的 maxParallelism/consistencyCapability 静默忽略（AR-11 P2，backlog；`test-smoke.stream.xml:8,16` 演示的 `consistencyCapability` 维持 watch-only，不在本 plan 修正范围）。
- 不处理 window 节点级 allowedLateness/triggerId 子元素（AR-12 P2，backlog；若 fail-fast 机制天然覆盖则执行时标注关闭）。
- 不处理 CEP pattern 的 SKIP_TO 校验（AR-16 P2，backlog）。

## Scope

### In Scope

- P1-XDSL-5：edge 六属性消费（HASH+keyExpr 经 keyBy）或 fail-fast（其余）；`resolveEdgePartition` 接入或删除；`test-reduce-pipeline.stream.xml` 对齐。
- P1-XDSL-6：checkpoint 六字段实现 + 窗口策略三属性 fail-fast。
- P1-09-02：flow 模块 58 处裸异常 → `StreamException` + 错误码；既有异常类型断言迁移。
- 上述行为的 focused 测试（fail-fast 断言 + checkpoint 字段语义断言）。

### Out Of Scope

- 平台级错误处理体系改造（`NopStreamErrors` 死码清理属 AR-10 P2，backlog；本 plan 只新增使用，不删码）。
- RocksDB/runtime 等其他模块的裸异常（P2-09-02b，backlog）。

## Execution Plan

### Phase 1 - edge 分区/流控属性契约（P1-XDSL-5）

Status: completed
Targets: `nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java`、`test-reduce-pipeline.stream.xml`

- Item Types: `Decision | Fix | Fix | Fix`

- [x] Decision: 逐属性裁定落地——**partition + keyExpr 组合矩阵**：(1) `partition="HASH"` + keyExpr → 经 `keyBy(EvalActionKeySelector(edge.getKeyExpr()))` 落地（下游非 keyBy 时应用；下游本身是 keyBy 时视为冗余 HASH → fail-fast 提示）；(2) `partition="HASH"` 无 keyExpr → fail-fast；(3) **keyExpr 声明于非 HASH 边（FORWARD 或未设置 partition）→ fail-fast**（edge id + 属性名；否则 keyExpr 仍被静默忽略，违反本 plan 目的与"无静默跳过"exit criterion）；(4) REBALANCE / BROADCAST / 流控四属性（flowControlPolicy/queueCapacity/receiveWindow/packetSize）非默认值 → fail-fast（core 无对应算子 API）；(5) FORWARD（无 keyExpr）维持现状
- [x] Fix: 在 `buildTransforms`/`buildTransform` 消费 edge 属性（HASH+keyExpr 应用 keyBy；其余非默认值抛 `StreamException` 含 edge id + 属性名）；`resolveEdgePartition` 接入实际调用链作为消费入口或删除死代码
- [x] Fix: `test-reduce-pipeline.stream.xml:20` 的 `partition="HASH"` 与修复后行为一致（HASH+keyExpr 且下游非 keyBy → 语义落地；若下游是 keyBy → 示例改为 FORWARD）；示例 DSL 测试回归先红后绿
- [x] Fix: 新增 DSL 构建期测试——未实现边属性非默认值声明抛错（fail-fast 断言含 edge id + 属性名，参数化覆盖：HASH-无keyExpr / HASH+keyExpr 下游是 keyBy / 非 HASH 边声明 keyExpr / REBALANCE / BROADCAST / 四流控属性）；已落地 HASH 的语义断言（**repo-observable 形式**：HASH+keyExpr 且下游是 `<window>` 时 `registeredStream("w") instanceof WindowedStream`——flow 测试无法检查中间 transformation DAG，`TestStreamModelDslBuilderE2E.java:87-90` 已注明不可检查且示例 DSL 多为 parallelism=1，分区运行时语义不可观测，见 Phase 1 exit criteria 说明）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 已落地属性（HASH+keyExpr 非 keyBy 下游）有构建成功断言（WindowedStream 形式）；未实现属性非默认值 fail-fast 有断言测试（参数化覆盖全部组合矩阵）
- [x] `resolveEdgePartition` 有 live 调用者或已删除（grep 复核）
- [x] `test-reduce-pipeline.stream.xml` 全量 DSL 测试回归绿；示例文件与 builder 行为一致（HASH 语义落地或改 FORWARD）
- [x] **无静默跳过**：无任何被忽略的已声明非默认属性（grep 复核六属性全部消费或 fail-fast；含 keyExpr 单独声明场景）
- [x] 说明：本 Phase 的落地验证为**构建期断言**（flow 测试无法观测运行时分区——`TestStreamModelDslBuilderE2E.java:87-90` 明确 transformation 不可检查且示例 DSL 均为低 parallelism；流控/分区运行时语义验证不适用，属 Non-Goals 的分区运行时）；**连带影响**：`fraud-detection.stream.xml:94/:95`（REBALANCE + HASH 边）在 fail-fast 后不可构建——该文件本身是破损死文件（P2-03-02/XDSL-1~4，backlog），本 plan 执行时顺手将这两条边改为 FORWARD 或随 backlog 修复，留痕即可
- [x] `docs-for-ai/` 相关 DSL 文档（若存在 stream DSL 用法章节）同步 fail-fast 行为；否则 `No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - checkpoint 配置与窗口策略属性契约（P1-XDSL-6）

Status: completed
Targets: `nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java`、`AdvancedTransforms.java`、`TestAdvancedTransforms.java`（内联策略 fixture）

- Item Types: `Fix | Fix | Fix | Fix`

- [x] Fix: `applyCheckpointConfig` 消费六个未消费字段（barrierAlignmentTimeout/maxConsecutiveCheckpointFailures/storageType/jobId/pipelineId/storageConfig）→ 映射到 core `CheckpointConfig` 对应 setter（setter 已存在，逐字段映射含 storageConfig 结构：DSL `KeyedList<StorageConfigEntry(key,value)>` → core `Map<String,String>`）；**非默认值判定钉死为 `!= xdef 默认值才 set`**（xdef 默认：barrierAlignmentTimeout=30000、maxConsecutiveCheckpointFailures=3、storageType="local"；jobId/pipelineId 为 null 检查——不得用 `> 0` 守卫，否则声明 0 被静默忽略）
- [x] Fix: `buildWindow` 对 `triggerId`/`allowedLateness`/`accumulationMode` 非默认值（xdef 默认：allowedLateness=0、accumulationMode=DISCARDING、triggerId 未设置）即 fail-fast（`StreamException` 含 transform id + 属性名）；默认值静默忽略合法（与默认行为一致）。**执行时裁定（基线事实修正）**：live `stream.xdef:47` 的 `triggerId="!string"` 是 mandatory，与 plan 假定的"triggerId 未设置"默认路径矛盾——minimal relaxation `!string → string`（仅去除 mandatory 标记，属性定义/生成类零变更）；不放松则 triggerId 必声明 → fail-fast 必触发 → window DSL 整体不可构建，违反 Phase 1 exit criteria。`window` 节点级 `<allowedLateness>`/`<triggerId>` 子元素（AR-12 watch-only）经同一校验天然覆盖，AR-12 标注关闭（roadmap 已更新）
- [x] Fix: `TestAdvancedTransforms` 内联策略中 `triggerId="t"` 的 5 处 fixture（:76/:93/:115/:138/:180）**全部改为删除 triggerId（回默认路径）**——注意 :115/:180 的断言消息 "nop-stream-runtime" 依赖 `buildWindow` 成功后才在 aggregate/reduce 边界失败，:138 依赖 buildWindow 的 IAE 先于 bean 检查触发，这些用例只有"回默认路径"才能保留其断言目的，不能改 fail-fast 分支；`test-smoke.stream.xml` 无 checkpoint/窗口属性，无需修改（其 `consistencyCapability` 属 AR-11 watch-only，不动）
- [x] Fix: 新增测试——checkpoint 六字段语义断言（DSL 构建后 `env.getCheckpointConfig()` 各字段值与声明一致，参数化，含 0/默认值边界）；窗口三属性非默认值 fail-fast 断言（参数化）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 六字段全部实现映射（grep 复核 setter 调用点）；窗口三属性全部 fail-fast（grep 复核 getter 调用点 + fail-fast 断言测试）
- [x] checkpoint 六字段语义断言全绿：`env.getCheckpointConfig()` 反映声明值（含 storageConfig 结构映射）
- [x] `TestAdvancedTransforms` 中 `triggerId="t"` 用例与修复后行为一致（先红后绿）
- [x] flow 模块全量测试回归绿（`TestAdvancedTransforms`、`TestStreamModelDslBuilderE2E`/`TestStreamModelDslBuilderFailFast`、示例 DSL 测试等）
- [x] **无静默跳过**：fail-fast 错误信息含 DSL 节点 id 与属性名，可定位到声明位置
- [x] 说明：无 pipeline-execution E2E 适用——flow 测试 classpath 无 `nop-stream-runtime`（`TestAdvancedTransforms.java:41-51` 记录），窗口/checkpoint 执行验证由 `env.getCheckpointConfig()` 断言与构建期 fail-fast 断言承担；运行时语义由运行时模块测试覆盖
- [x] `docs-for-ai/` 相关 DSL 文档（若存在 checkpoint/窗口用法章节）同步；否则 `No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - flow 模块错误码体系接入（P1-09-02）

Status: completed
Targets: `nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/`（含 `builder/functions/` 子目录全部文件）、`nop-stream-core/.../exceptions/NopStreamErrors.java`（仅新增码）、`TestAdvancedTransforms.java` / `TestStreamModelDslBuilderFailFast.java` / `TestStreamModelDeltaFailFast.java` / `TestBeanFunctionResolver.java`（异常断言迁移）

- Item Types: `Fix | Fix | Fix | Proof | Proof`

- [x] Fix: 新增高频错误码（duplicate transform id、not-yet-implemented registry、upstream 校验失败、类型校验失败等）到 `NopStreamErrors`（遵循 AGENTS.md 两档错误处理：模块内用 `StreamException` + ErrorCode + `.param(...)`）
- [x] Fix: 58 处裸异常批量替换为 `StreamException(ERR_STREAM_...)`（保留英文消息、cause、fail-fast 意图）；错误码语义与场景匹配（不得盲取死码）；**范围含 `builder/functions/*.java` 的 6 处**（Phase 1/2 新增的 fail-fast 抛出点同步用新体系）
- [x] Fix: **迁移既有异常类型断言**——按 live grep 实测计数：`TestAdvancedTransforms`（4× IAE :97/:142/:314/:332 + 2× UOE :350/:364）、`TestStreamModelDslBuilderFailFast`（7× UOE）、`TestStreamModelDeltaFailFast`（1× UOE）、`TestBeanFunctionResolver`（2× IAE）改为断言 `StreamException` + 错误码 + 保留消息断言（不得放宽为 `Exception.class`）；**`TestAdvancedTransforms` 已有的 2 处 `StreamException` 断言（:125/:188）无需改动**；`:268` 的 `assertThrows(Exception.class)` 是解析器拒绝测试（builder 外作用域），维持不变并在执行日志注明
- [x] Proof: 新增/扩展测试——至少高频路径（duplicate id、not-yet-implemented）断言 `getErrorCode()` 非空且消息保留上下文
- [x] Proof: 类别清扫——flow 模块（含 `builder/functions/`）grep 复核零裸 `RuntimeException` 子类残留（`throw new (IllegalArgumentException|IllegalStateException|UnsupportedOperationException)` 在 flow main 代码 0 命中，或全部命中处经裁定豁免登记）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] flow 模块（含 `builder/functions/`）裸异常清零（grep 复核）
- [x] 新增错误码有抛出点 + 测试断言 `getErrorCode()` 非空
- [x] 既有异常类型断言迁移完成且回归全绿（4 个测试文件；保留消息断言）
- [x] flow 模块全量测试回归绿
- [x] **端到端验证**：DSL 构建错误经 `StreamException` 冒泡到调用方（错误码可程序化处理）已验证
- [x] `docs-for-ai/02-core-guides/error-handling.md` 若需登记模块异常类用法则同步；否则 `No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] P1-XDSL-5 已修复：edge 六属性消费或 fail-fast + 测试证据在案
- [x] P1-XDSL-6 已修复：checkpoint 六字段 + 窗口策略三属性消费或 fail-fast + 测试证据在案
- [x] P1-09-02 已修复：flow 模块裸异常清零 + 错误码测试证据在案
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] 受影响的 owner docs 已同步到 live baseline，或明确写明 No owner-doc update required
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）fail-fast 分支在运行时确实可达（测试断言），（b）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw test -pl nop-stream/nop-stream-flow -am -T 1C`
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream-flow --severity high` exit 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs 2026-08-13-1243-2-nop-stream-flow-dsl-contract-fixes.md --strict` exit 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0（若修改了 docs-for-ai）

## Deferred But Adjudicated

### 分布式分区执行的数据面落地（REBALANCE/BROADCAST/流控属性）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 本 plan 裁定这些属性构建期 fail-fast（core `DataStream` 无 rebalance/broadcast/partitionCustom API，新增公共 API 属跨模块契约变更需人工确认）；运行时多节点分区执行属分布式执行面，backlog 触发条件 = 分布式执行需求出现或 core 分区 API 落地
- Successor Required: `no`

### AR-11（source/sink 的 maxParallelism/consistencyCapability）与 AR-12（window 节点级 allowedLateness/triggerId）

- Classification: `watch-only residual`（P2，backlog 条目）
- Why Not Blocking Closure: P2 不驱动本 plan；本 plan 的 checkpoint 六字段/窗口三属性 fail-fast 机制与 AR-11/AR-12 同族，若执行时天然覆盖（如窗口节点级属性经同一校验路径拒绝）则标注关闭并留痕；`test-smoke.stream.xml` 的 `consistencyCapability` 演示明确维持 watch-only 不动
- Successor Required: `no`

### 窗口策略三属性（triggerId/allowedLateness/accumulationMode）的完整实现

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: `WindowedStream` 无 allowedLateness/accumulationMode API（仅 trigger/evictor）；trigger 语义落地需新增 trigger 注册表（跨模块公共契约变更 → plan-first + 人工确认）。本 plan 以非默认值 fail-fast 保证"声明不静默丢失"（No-Silent-No-Op 收口），语义实现留待未来公共 API 落地
- Successor Required: `no`

## Non-Blocking Follow-ups

- 错误码接入完成后复核 AR-10（7 个死错误码）与 P2-09-02b（core/runtime 裸异常批次）的 backlog 触发条件
- 示例 DSL 全套（fraud-detection.stream.xml 等 P2-03-02/XDSL-1~4）维持 backlog

## Closure

Status Note: 三 Phase 全部落地（edge 属性消费/fail-fast、checkpoint 六字段实现 + 窗口属性 fail-fast、flow 错误码体系接入），Phase 1/2/3 Exit Criteria 与 Closure Gates 全部勾选，独立 closure audit（fresh session）PASS 16/16。
Completed: 2026-08-13

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，read-only）
- Audit Session: `ses_0061177d2ffefKhB6Sj3RueLxJ`
- Evidence:
  - Phase 1 exit criteria 验证（PASS）：`validateEdgeDeclarations`（`StreamModelDslBuilder.java:349`，buildTransforms:304 调用）覆盖六属性；`applyEdgePartition`（:568）经 `keyBy` 落地 HASH+keyExpr（:576）；`resolveEdgePartition`（:545）live 调用者在 :569（main）；`requireSingleInput` :434 消费边属性；`AdvancedTransforms.java:469` 同链。`TestStreamModelEdgeContract` 11 用例 0 失败（8 参数化 fail-fast + redundant + WindowedStream 落地断言 + sink 构建）。`test-reduce-pipeline.stream.xml:24` e0=FORWARD、`fraud-detection.stream.xml:97-98` e3/e4=FORWARD。
  - Phase 2 exit criteria 验证（PASS）：`applyCheckpointConfig`（:167-221）六字段 `!= xdef 默认` 映射（:198-220，常量 :120-122 对齐 stream.xdef:31-35 默认）；`buildWindow` → `failFastOnUnsupportedWindowStrategy`（:177-197）+ `failFastOnUnsupportedWindowNodeAttrs`（:204-216）ERR_STREAM_WINDOW_ATTR_UNSUPPORTED 含 transform id + 属性名；`stream.xdef` git diff 唯一变更 = `triggerId="!string"` → `"string"`（:47，plan 基线事实修正，见 Phase 2 item 2 记录）；`TestAdvancedTransforms` triggerId 零残留；`TestStreamCheckpointAndWindowContract` 8 用例 0 失败（六字段语义 + 0 边界 + core 默认对齐）。
  - Phase 3 exit criteria 验证（PASS）：flow main grep `throw new (IAE|ISE|UOE)` 0 命中；16 个新错误码（NopStreamErrors.java:462-539）各有 ≥1 flow main 抛出点；四个测试文件断言迁移完成全绿；`TestStreamErrorCodeContract` 4 用例（getErrorCode() 非空 + 端到端程序化错误码分支）。
  - Closure Gate 工具验证（PASS）：`./mvnw test -pl nop-stream/nop-stream-flow -am -T 1C` BUILD SUCCESS（flow 74 tests / 0 failures；nop-stream 组全量 0 失败：core 1473 / runtime 829 / cep 327 / rocksdb 86 / connector 35 / connector-jdbc 32 / connector-batch 35 / connector-debezium 19 / flow 74 / fraud-example 25）；`scan-hollow-implementations.mjs --module nop-stream-flow --severity high` exit 0（0 findings）；`check-nop-stream-invariants.mjs all` exit 0；`check-plan-checklist.mjs --strict` exit 0；`check-doc-links.mjs --strict` exit 0。
  - Anti-Hollow 检查：keyBy 调用链 buildTransform → requireSingleInput → applyEdgePartition → keyBy（:419-435→:568-576）运行时连通（测试断言 WindowedStream 产物）；checkpoint 六 setter 经 build() → applyCheckpointConfig 调用（:157）可达（语义断言验证）；无空方法体/静默跳过/no-op。
  - Deferred 项分类检查：Non-Goals/Deferred 项全部维持 out-of-scope（分区运行时数据面、WindowedStream 公共 API、AR-11 watch-only、AR-16）——无 in-scope live defect 被降级；AR-12（window 节点级属性）由 fail-fast 机制天然覆盖，roadmap 标 done。

Follow-up:

- no remaining plan-owned work
- P2 批次维持 backlog 触发条件不变：fraud-detection.stream.xml 死文件（P2-03-02/XDSL-1~4）、AR-10（7 个死错误码 + NopStreamErrors 死码清理）、AR-11（source/sink maxParallelism/consistencyCapability watch-only）、P2-09-02b（core/runtime 裸异常批次）、AR-16（CEP SKIP_TO 校验）
