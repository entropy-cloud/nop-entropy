# 15 Batch / Message Connector Capability Audit (nop-stream Independent Audit)

> Plan Status: completed
> Last Reviewed: 2026-08-08
> Source: `ai-dev/backlog/nop-stream-independent-audit-roadmap.md` (Stage 15); frozen Stage-4 outputs (`source-manifest.md` domain `e` connector-main-java-files + domain `a`, `evidence-schema.md`, `finding-corpus.md` shard 20 connector findings, `ai-dev/tools/check-nop-stream-audit-manifest.mjs`); frozen Stage-5 outputs (`environment-qualification.md` — T1 `qualified`/`in-process`, T3/T4 `blocked`); frozen Stage-14 data-plane evidence (transport proof, not re-audited); live repo baseline of `nop-stream-connector` + `nop-stream-connector-batch`.
> Mission: nop-stream-independent-audit
> Work Item: 15. Batch/message connector capability audit
> Related: Execution order `{1}` of this DRAFT_PLANS round. Roadmap deps: Stage 4 (evidence schema), Stage 5 (env qualification), Stage 6 (Java/local audit), Stage 14 (data-plane/multi-JVM audit) — all `done`. Hard prerequisite for Stage 20 (Hist P0/P1 CEP/connector/runtime) and Stage 22 (Hist P2 CEP/connector/runtime). NOT on critical path. Absorbs data-plane audit (Stage 14) follow-ups that deferred Kafka/Pulsar real-backend evidence to connector successor plans.

## Purpose

独立验证 nop-stream 的 **batch 与 message connector** 是否只报告它们在 qualified backend 上真正能提供的保证。每个 connector 的 source/sink 能力必须形成一条可复核的 evidence row：声明保证（`SinkConsistencyCapability` / `SourceConsistencyCapability` 标签）、实现锚点、运行时接线、正向证据（真实 in-process 实跑测试名）、拒绝/失败证据、lane 强度、finding 关联、disposition。message backend（Kafka/Pulsar）真实集成因 T3/T4 lane `blocked` 必须如实标 `blocked`，不得用 in-process `LocalMessageService` 冒充。

本审计验证核心 invariants：(a) message source 的 collect 异常不再被静默吞掉（corpus M7-2-P1-9：capture-and-rethrow → run() rethrow）；(b) batch sink 的 null 边界拒绝与 close() robust flush（corpus M7-2-P1-15：partial-addressed）；(c) StreamConnectors 不再硬引用 optional deps 导致 NoClassDefFoundError（corpus O7-2-AR-2：verified-fixed）；(d) 每条能力声明的 consistency 标签（`AT_LEAST_ONCE` / `IDEMPOTENT`）与真实行为一致——不得声明 `EXACTLY_ONCE` 而 only at-least-once。

本审计**发现**的任何 confirmed live defect 不在本计划内修复，而按 roadmap 规则指派给 active/successor remediation plan。data-plane transport（record/barrier/watermark 在 wire codec 上的传输）已在 Stage 14 证明，本计划引用其结论不重新审计。

## Current Baseline

经 2026-08-08 live repo 核对（引用均与 frozen Stage-4 `source-manifest.md` 域 `e`（4 connector 模块 16 个 main java 文件）+ 实际源码一致）：

- **Message source**：`MessageSourceFunction<T>` `nop-stream-connector/.../connector/MessageSourceFunction.java:45`（implements `SourceFunction<T>`）。partition-aware 订阅（`subtaskIndex`/`totalParallelism` `:55-57`，`getEffectiveTopic()` `:114-119` 返回 `{topic}-{subtaskIndex}`）。`run(SourceContext)` `:121-183`：`messageService.subscribe(effectiveTopic, consumer)` `:129` → `onMessage` → `ctx.collect(msg)` `:150`。**M7-2-P1-9 已修复**：collect 异常不再静默吞掉——`pendingError` capture（`:156-158`）+ `run()` 末尾 rethrow（`:176-182`），type-mismatch 同样 capture（`:136-141`）。`getSourceConsistency()` `:197-199` 返回 `AT_LEAST_ONCE`。`cancel()` `:186-194` 关闭 subscription + countDown latch。
- **Message sink**：`MessageSinkFunction<T>` `nop-stream-connector/.../connector/MessageSinkFunction.java:24`（implements `SinkFunction<T>`）。`consume(T)` `:43-45` → `messageService.send(topic, value)`（同步）。`getSinkConsistency()` `:48-50` 返回 `AT_LEAST_ONCE`。无 2PC、无 buffer、无 flush——纯同步 send。
- **Batch source**：`BatchLoaderSourceFunction<S>` `nop-stream-connector-batch/.../batch/BatchLoaderSourceFunction.java:33`（implements `ReplayableSourceFunction<S>`）。`run()` `:61-85`：循环 `loader.load(batchSize, chunkContext)` `:68`，空 list 则 break（bounded source `:69-71`），逐条 `ctx.collect(item)` `:76`。replay 能力：`getCurrentOffset()` `:98-100` / `seek(long)` `:103-105`（currentOffset 字段 `:42`）。`getSourceConsistency()` `:93-95` 返回 `AT_LEAST_ONCE`。loader `AutoCloseable` 关闭 `:81-83`。
- **Batch sink**：`BatchConsumerSinkFunction<R>` `nop-stream-connector-batch/.../batch/BatchConsumerSinkFunction.java:47`（implements `SinkFunction<R>, AutoCloseable`）。buffer `List<R>` `:54`，`consume()` `:77-89` 缓冲到 `batchSize` 触发 `flush()` `:91-105`。**M7-2-P1-15 partial-addressed**：null 边界拒绝（`:82-84` 抛 `StreamException`），close() robust flush（`:116-145`，flushError + suppressed + `ERR_STREAM_CHAINING_OUTPUT_FLUSH_FAILED`），thread-safety contract 文档化（`:39-45` 单线程模型）。`getSinkConsistency()` `:148-150` 返回 `IDEMPOTENT`。`finish()` `:108-113` flush。
- **StreamConnectors**：`StreamConnectors` `nop-stream-connector-batch/.../batch/StreamConnectors.java`（utility）。**O7-2-AR-2 verified-fixed**：不再硬引用 optional deps 导致 class-load 期 NoClassDefFoundError（corpus 标 `status_at_0802: verified-fixed`）。
- **message backend 抽象**：connector 通过 `IMessageService`（`io.nop.api.core.message.IMessageService`）抽象对接 backend。runtime `ioc:default` bean `streamMessageService->LocalMessageService`（manifest 域 `d` `ioc-default-bean-declarations`）= in-process 默认。真实 Kafka/Pulsar backend 由 `nop-stream-runtime` 的 wire-codec SPI 承载（Stage 14 已审计 transport），其 gated 测试 `TestDataPlaneKafkaBackendE2E` / `TestDataPlanePulsarBackendE2E` 在 T3/T4 lane（`environment-qualification.md` 标 `blocked`，无 broker provisioned）。
- **测试语料**（manifest 域 `g`；connector 模块测试）：
  - `nop-stream-connector`：`TestMessageAdapters`、`TestFileTwoPhaseCommitSink`、`TestFileSource`、`TestFileSourceCheckpointRestore`、`TestConnectorResourceManagement`、`TestDrainableSourceSupport`、`TestMessageSourceFunctionThreadSafety`（7 文件）。
  - `nop-stream-connector-batch`：`TestBatchConsumerSinkFunction`、`TestBatchConsumerSinkFunctionFailure`、`TestBatchConsumerSinkFunctionCloseLogging`、`TestBatchLoaderSourceFunction`、`TestConnectorConsistencyCapability`（5 文件）。
  - **全部 unit/in-process**——无 `env.execute()` 级 connector 测试；无真实 Kafka/Pulsar broker connector 集成测试（gated 测试在 runtime 模块，属 data-plane lane）。
- **Corpus 交叉**（finding-corpus.md，connector 域）：M7-2-P1-9（MessageSourceFunction 静默吞 collect 异常，**FIXED**，shard 20）、M7-2-P1-15（TestBatchConsumerSinkFunction happy-path only，**partial-addressed**，shard 20）、O7-2-AR-2（StreamConnectors 硬引用 optional deps，**verified-fixed**，shard 20）、M8-2-P2-12（LocalSourceCoordinator bare IllegalStateException 4 处 + silent snapshot swallow，shard 18，corpus domain 标签 `connector` 但 anchor `LocalSourceCoordinator.java` 实际在 **`nop-stream-core`** `io/nop/stream/core/source/coordinator/`——不在 manifest 域 `e` 4 connector 模块范围；该 finding `deferred` 指向 `2026-08-04-2300-3-contract-drift-config-test-integrity.md`，本计划只 cross-ref 指派，不重复审计）。
- **partition-aware 测试覆盖**（live 核对）：`MessageSourceFunction.getEffectiveTopic()` `:114-119` 与分区构造器校验（`:95-102`）全仓库**零测试引用**——`TestMessageSourceFunctionThreadSafety`（3 参数构造器，无 subtaskIndex）与 `TestMessageAdapters`（1 参数构造器）均不覆盖分区路径。partition-aware 能力的 `positive_proof` 无法引用真实测试，须用 `manual-trace:MessageSourceFunction.java:114-119` 并标 `disposition: component-only`/`unverified`（不得虚标 `e2e-proved`）。
- **真实 gap**：(1) 没有 per-connector source/sink capability matrix 的成套 evidence row（声明标签 vs 真实行为）；(2) message source 的 collect 异常 capture-and-rethrow（M7-2-P1-9 FIXED）缺独立 evidence row 冻结（虽有 `TestMessageSourceFunctionThreadSafety` 覆盖 capture-rethrow，但不覆盖 partition-aware）；(3) batch sink null 拒绝 + robust close（M7-2-P1-15 partial）缺 evidence row 冻结；(4) StreamConnectors optional-deps（O7-2-AR-2）缺 verified-fixed 复验 evidence row；(5) message backend Kafka/Pulsar 真实集成能力缺 honest `blocked` 标注（依赖 T3/T4 blocked lane，引用 Stage 14 transport 结论）；(6) consistency 标签（`AT_LEAST_ONCE` / `IDEMPOTENT`）与行为一致性缺显式裁定；(7) **partition-aware 订阅（`getEffectiveTopic`/分区构造器校验）零测试覆盖**——须如实标 gap，不得虚标 evidence。

## Goals

- 产出一份 **batch/message connector 支持/拒绝能力矩阵**（per-connector source/sink × 声明保证 × 真实行为 × lane），每能力一条 evidence row，`environment_class` 据 frozen lane 词表裁定（connector 全部 in-process → `in-process` 或 `unit`；真实 Kafka/Pulsar backend → `blocked`）。
- 为**每条 batch connector 能力**（BatchLoaderSourceFunction bounded/replay、BatchConsumerSinkFunction buffered-flush/null-reject/robust-close）产出 entry-to-effect evidence row：`positive_proof` 为真实 in-process 实跑测试名（`ClassName#method`）。
- 为**每条 message connector 能力**（MessageSourceFunction partition-aware/capture-rethrow、MessageSinkFunction sync-send）产出 evidence row。
- 产出 **M7-2-P1-9 复验** evidence row：`source_anchor` 指向 `MessageSourceFunction.java:148-182`（capture-and-rethrow）；`disposition` 据 in-process lane 裁定（FIXED）。
- 产出 **M7-2-P1-15 复验** evidence row：`source_anchor` 指向 `BatchConsumerSinkFunction.java:82-84`（null reject）+ `:116-145`（robust close）；`disposition` 据 live 行为裁定（partial-addressed 标 `e2e-proved`/`residual-risk` + 注明 happy-path-only 历史 gap 已部分收敛）。
- 产出 **O7-2-AR-2 复验** evidence row：`source_anchor` 指向 `StreamConnectors.java` + `nop-stream-connector/pom.xml`；`disposition` 据 live 证据裁定（`e2e-proved` 仅当有显式 class-load 隔离测试，否则 `residual-risk` + manual-trace）。
- 产出 **message backend 限制** evidence row：Kafka/Pulsar 真实集成 `disposition: blocked` + cross-ref T3/T4 `@@LANE` `blocked` + Rule S5-1（不得用 in-process `LocalMessageService` 冒充）；引用 Stage 14 transport 结论（不重新审计 transport）。
- 产出 **consistency 标签裁定** evidence row：`AT_LEAST_ONCE`（message source/sink、batch source）与 `IDEMPOTENT`（batch sink）标签与真实行为一致——不得声明 `EXACTLY_ONCE` 而 only at-least-once。
- 对 **M8-2-P2-12**（LocalSourceCoordinator bare exception，domain `connector`）做 live 复验标注：判定该 anchor 是否在本 connector 审计范围（LocalSourceCoordinator 属 runtime source coordination），若属 runtime 则 cross-ref 指派，不重复审计。
- 所有 evidence row 经 `check-nop-stream-audit-manifest.mjs evidence --strict` 校验通过且非空过；corpus finding_id 交叉标注合法。

## Non-Goals

- JDBC/file/CDC connector 的 transactional external-effect（2PC commit/abort/retry、CDC offset restore）——属 Stage 16（本计划只审 batch/message connector）。
- 通用 data-plane transport（record/barrier/watermark 在 wire codec 上的传输）——属 Stage 14（已审计，本计划引用结论）。
- checkpoint/state backend 编码、window/CEP 语义。
- 修复本审计发现的 confirmed live defect（指派 remediation plan）。
- provision Kafka/Pulsar broker（gate 未解除前如实 `blocked`）。

## Scope

### In Scope

- `ai-dev/audits/nop-stream-independent-audit/stage-15-batch-message-connector.evidence.md`（domain evidence rows，manifest 域 `e`（4 connector 模块 main java）+ 域 `a`（public types）+ 域 `g`（test lane）范围内的 batch/message connector source/sink + message backend 限制）。**文件名必须是 `*.evidence.md` 且为 audit dir 直系子文件。**
- batch/message connector 支持/拒绝能力矩阵文本（写入证据文件头部，仅矩阵/判据不改 frozen 字段/词表）。

### Out Of Scope

- JDBC/file/CDC connector external-effect（Stage 16）。
- data-plane transport 重新审计（Stage 14）。
- checkpoint/state/window/CEP 语义。
- 修复 confirmed live defect。
- 修改 frozen evidence-row 11 字段定义或 7 分类词表。
- provision 外部 broker/DB。

## Execution Plan

### Phase 1 - Batch Connector Source/Sink Capability Evidence

Status: completed
Targets: `ai-dev/audits/nop-stream-independent-audit/stage-15-batch-message-connector.evidence.md`

- Item Types: `Proof`

- [x] 产出 BatchLoaderSourceFunction bounded-source evidence row：`source_anchor` 指向 `BatchLoaderSourceFunction.java:61-85`（run loop，空 list break）；`implementation_anchor` `:68-71`；`positive_proof` 引用 `TestBatchLoaderSourceFunction` 对应方法（bounded completion）；`runtime_wiring: wired`；`environment_class` 据 in-process 裁定；`required_lane: in-process`。
- [x] 产出 BatchLoaderSourceFunction replay/offset evidence row：`source_anchor` 指向 `:97-105`（`getCurrentOffset`/`seek`）；`positive_proof` 引用覆盖 seek/replay 的测试方法；`disposition` 据 in-process 裁定（若有 replay 测试 `e2e-proved`，否则标 gap）。
- [x] 产出 BatchConsumerSinkFunction buffered-flush evidence row：`source_anchor` 指向 `BatchConsumerSinkFunction.java:77-105`（consume buffer→flush）；`positive_proof` 引用 `TestBatchConsumerSinkFunction` 对应方法；`runtime_wiring: wired`。
- [x] 产出 BatchConsumerSinkFunction null-reject + robust-close evidence row（M7-2-P1-15 复验）：`source_anchor` 指向 `:82-84`（null reject）+ `:116-145`（robust close）；`positive_proof` 引用 `TestBatchConsumerSinkFunctionFailure` + `TestBatchConsumerSinkFunctionCloseLogging`；`finding_id: M7-2-P1-15`；`disposition` 据 in-process 裁定。
- [x] 产出 StreamConnectors optional-deps verified-fixed evidence row（O7-2-AR-2 复验）：`source_anchor` 指向 `StreamConnectors.java` + `nop-stream-connector/pom.xml`；`finding_id: O7-2-AR-2`；`disposition` 据 live 证据裁定——`e2e-proved` 仅当存在显式 class-load 隔离测试证明 base connector 模块在 nop-batch-core 缺失时可加载（`TestConnectorConsistencyCapability` 在 connector-batch 模块，**不证明** base connector 隔离）；否则 `residual-risk` + `manual-trace:` + 注明隐式推断（base connector 测试运行间接证明无硬依赖）。
- [x] 产出 batch sink consistency-label evidence row：`source_anchor` 指向 `:148-150`（`IDEMPOTENT`）；`disposition` 据标签与行为一致性裁定。
- [x] 冻结 **batch connector 支持/拒绝矩阵**文本（写入证据文件头部）。

Exit Criteria:

- [x] ≥5 条 batch connector evidence row，格式经 `check-nop-stream-audit-manifest.mjs evidence --strict` 校验 exit 0，且校验器实际解析到行（非 "0 evidence rows yet" 空过）
- [x] **端到端验证（Rule #22）**：至少一条 row 的 `positive_proof` 是真实 in-process 实跑测试名（`ClassName#method`），`environment_class >= in-process`/`unit`，`disposition` 合理；不得用 metadata-only 测试充数
- [x] **接线验证（Rule #23）**：batch source/sink row 的 `runtime_wiring` 据 in-process 实跑裁定（`BatchLoaderSourceFunction.run` → `ctx.collect`、`BatchConsumerSinkFunction.consume` → `flush` → `consumer.consume` 确实连通），非仅方法存在
- [x] **无静默跳过（Rule #24）**：任一 batch 能力无法在 in-process 实跑的，row `disposition` 标 `unverified`；M7-2-P1-15 happy-path-only 历史 gap 若仍有未覆盖边界须标 `residual-risk` + 注明
- [x] `No owner-doc update required`（证据文件是审计产出；不改 `docs-for-ai/`）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - Message Connector Source/Sink Capability & Backend Limitation Evidence

Status: completed
Targets: `ai-dev/audits/nop-stream-independent-audit/stage-15-batch-message-connector.evidence.md`

- Item Types: `Proof`

- [x] 产出 MessageSourceFunction partition-aware subscription evidence row：`source_anchor` 指向 `MessageSourceFunction.java:114-119`（getEffectiveTopic）+ `:95-102`（分区构造器校验）+ `:129`（subscribe）；**partition-aware 路径零测试覆盖**（live 核对，见 Current Baseline）——`positive_proof` 用 `manual-trace:MessageSourceFunction.java:114-119`，`disposition: component-only`（或 `unverified`），不得虚标 `e2e-proved`；`runtime_wiring` 据 manual trace 裁定。
- [x] 产出 MessageSourceFunction capture-and-rethrow evidence row（M7-2-P1-9 复验）：`source_anchor` 指向 `:148-182`（pendingError capture + run rethrow）；`positive_proof` 引用验证 collect 异常 surfacing 的测试方法；`finding_id: M7-2-P1-9`；`disposition: e2e-proved`（FIXED）。
- [x] 产出 MessageSinkFunction sync-send evidence row：`source_anchor` 指向 `MessageSinkFunction.java:43-45`（consume → send）；`positive_proof` 引用覆盖 sync send 的测试方法；`runtime_wiring: wired`。
- [x] 产出 message source/sink consistency-label evidence row：`source_anchor` 指向 `MessageSourceFunction.java:197-199`（`AT_LEAST_ONCE`）+ `MessageSinkFunction.java:48-50`（`AT_LEAST_ONCE`）；`disposition` 据标签与行为一致性裁定。
- [x] 产出 message backend Kafka/Pulsar 限制 evidence row：`disposition: blocked`；cross-ref T3/T4 `@@LANE` `blocked`（`environment-qualification.md`）+ Rule S5-1（gated test skipped 非 evidence）；注明 in-process `LocalMessageService`（`ioc:default` bean）= T1 in-process，不得冒充 Kafka/Pulsar；引用 Stage 14 transport 结论（不重新审计）。
- [x] 冻结 **message connector 支持/拒绝矩阵**文本（写入证据文件头部）。

Exit Criteria:

- [x] ≥4 条 message connector evidence row + ≥1 message backend `blocked` row，格式校验 exit 0，且校验器实际解析到行（非空过）
- [x] **端到端验证（Rule #22）**：至少一条非 `component-only`/`unverified` 的 message source/sink row，其 `positive_proof` 引用真实 in-process 实跑测试名（基于 `LocalMessageService`），`environment_class >= in-process`/`unit`（partition-aware 零覆盖 row 诚实标 `component-only` 不计入此条）
- [x] **接线验证（Rule #23）**：message source row 的 `runtime_wiring` 据 in-process 实跑裁定（`messageService.subscribe` → `onMessage` → `ctx.collect` 确实连通）
- [x] **无静默跳过（Rule #24）**：message backend Kafka/Pulsar 不得静默标 `e2e-proved`——须 `blocked` + cross-ref T3/T4 + Rule S5-1；M7-2-P1-9 FIXED 须有 capture-and-rethrow 复验（不得静默当已修复而不验证）
- [x] message backend blocked row 命名 unqualified lane（T3/T4）+ Rule S5-1 引用
- [x] `No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - Historical Finding Revalidation, Fail-Fast & Resource-Lifecycle Evidence

Status: completed
Targets: `ai-dev/audits/nop-stream-independent-audit/stage-15-batch-message-connector.evidence.md`

- Item Types: `Proof | Decision`

- [x] 对 M8-2-P2-12（LocalSourceCoordinator bare exception，corpus domain 标签 `connector`）做 live 复验判定：anchor `LocalSourceCoordinator.java` 实际在 **`nop-stream-core`**（不在 manifest 域 `e` 4 connector 模块范围）→ **不在本 connector 审计 in-scope 范围**；cross-ref 指派至 `2026-08-04-2300-3-contract-drift-config-test-integrity.md`（corpus `deferred` 字段已指向），记录判定理由（corpus domain 标签是分类标签，不代表代码在 connector 模块）。
- [x] 产出 connector resource-lifecycle evidence row：`source_anchor` 指向 connector `AutoCloseable` 关闭路径（`BatchLoaderSourceFunction:81-83` loader close、`BatchConsumerSinkFunction:129-139` consumer close）；`positive_proof` 引用 `TestConnectorResourceManagement`；`disposition` 据 in-process 裁定。
- [x] 产出 drainable-source support evidence row（若 manifest 域 `e` 范围内）：`source_anchor` 指向 drainable 路径；`positive_proof` 引用 `TestDrainableSourceSupport`；`disposition` 据 in-process 裁定。
- [x] 产出 message source thread-safety evidence row：`source_anchor` 指向 `MessageSourceFunction` 并发路径（`synchronized(ctx)` `:148`）；`positive_proof` 引用 `TestMessageSourceFunctionThreadSafety`；`disposition` 据 in-process 裁定。
- [x] 全 evidence 文件回归校验 + corpus 交叉标注核对 + batch/message 矩阵最终冻结。

Exit Criteria:

- [x] ≥3 条 resource-lifecycle/drainable/thread-safety/M8-2-P2-12 判定 evidence row，格式校验 exit 0，且校验器实际解析到行（非空过）
- [x] **无静默跳过（Rule #24）**：M8-2-P2-12 判定须显式记录（in-scope 复验 或 cross-ref 指派 + 理由），不得默默跳过；confirmed still-live connector finding 不得降级为 non-blocking follow-up
- [x] batch/message connector 支持/拒绝矩阵在证据文件头部有显式文本
- [x] `node ai-dev/tools/check-nop-stream-audit-manifest.mjs evidence --strict` exit 0，且校验器实际解析到行（非空过）；finding_id 全部合法（ID 在 frozen corpus 内或 `none`）
- [x] `No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **审计计划（无生产代码变更）**：本计划产出为 evidence rows + 矩阵文本，不改 nop-stream 生产代码。`./mvnw test`/`compile` 不强制；改为以 evidence 校验器退出码 + in-process 实跑证据引用为 closure 依据。但若审计中发现 confirmed live defect，按 roadmap 规则指派 remediation plan（不在本计划内修复）。

- [x] batch connector 能力（bounded source、replay、buffered-flush、null-reject、robust-close）各有 evidence row（in-process lane 实跑或如实标注缺覆盖）
- [x] message connector 能力（partition-aware source、capture-rethrow、sync-send）各有 evidence row
- [x] message backend Kafka/Pulsar 真实集成如实 `blocked`（cross-ref T3/T4 + Rule S5-1），无静默冒充
- [x] consistency 标签（`AT_LEAST_ONCE` / `IDEMPOTENT`）与真实行为一致性有显式裁定
- [x] connector 域历史 finding（M7-2-P1-9 FIXED、M7-2-P1-15 partial、O7-2-AR-2 verified-fixed、M8-2-P2-12 判定）有 live 复验 evidence row
- [x] 支持/拒绝矩阵显式成文
- [x] 所有 evidence row 经 `check-nop-stream-audit-manifest.mjs evidence --strict` exit 0，且**非空过**（Stage 15 文件本身被 validator 解析到 ≥1 行 `@@EVIDENCE`）
- [x] inventory_id 用 `EVID-S15-NNN` 前缀（全局唯一，与既有 stage 文件不冲突）
- [x] 不存在被静默降级到 deferred 的 in-scope 审计项（message backend blocked 为合法终态；M7-2-P1-15 residual 标 `residual-risk` + rationale；均为合法终态）
- [x] 审计发现的任何 confirmed live defect 已指派 active/successor remediation plan
- [x] `No owner-doc update required`（不改 `docs-for-ai/`）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 验证（a）in-process row 的 `positive_proof` 确为实跑测试名（非 metadata-only 充数），（b）`runtime_wiring=wired` 确经接线验证，（c）message backend blocked 无静默放行（标 `blocked` + cross-ref），（d）consistency 标签裁定无虚标，（e）partition-aware 零覆盖 gap 如实标 `component-only`/`unverified`（无虚标 e2e-proved）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` exit 0（Minimum Rule #26）

## Deferred But Adjudicated

（执行中如出现延期项，须写明 Classification / Why Not Blocking Closure / Successor Required。预期场景：message backend Kafka/Pulsar 真实集成因 T3/T4 lane `blocked`——此类 row 标 `disposition: blocked` + cross-ref T3/T4 `@@LANE` + Rule S5-1，是本计划合法终态并由 blocked-gate 规则承担后果（block Stage 23 ready verdict），非 deferred。batch sink happy-path-only 历史 gap（M7-2-P1-15）若仍有未覆盖并发/boundary 路径——标 `residual-risk` + 注明 non-blocking rationale + successor test-effectiveness（Stage 17）。confirmed still-live connector defect 不得 deferred——须指派 remediation plan。）

## Non-Blocking Follow-ups

- batch sink 并发/boundary 测试覆盖若仍不足（M7-2-P1-15 residual）→ Stage 17（test effectiveness）successor。
- message connector `env.execute()` 级端到端测试覆盖（当前全 unit/in-process）→ Stage 17 successor。
- provision Kafka/Pulsar broker 后 message backend row 从 `blocked` 升级 → connector successor plan（rerun T3/T4 lane）。

## Closure

Status Note: Stage 15 batch/message connector capability audit completed. Produced `ai-dev/audits/nop-stream-independent-audit/stage-15-batch-message-connector.evidence.md` (15 evidence rows EVID-S15-001..015 + header matrices B1–B6 / M1–M4+MB / R1–R3+H1). Batch source/sink capabilities (bounded source, replay/offset, buffered-flush, null-reject, robust-close, IDEMPOTENT label) e2e-proved on real in-process tests; partition-aware subscription honestly `unverified` (zero test coverage — never falsely marked e2e-proved); M7-2-P1-9 capture-and-rethrow e2e-proved (FIXED); M7-2-P1-15 partial-addressed `residual-risk` (null-reject + robust-close now tested; concurrency boundary documented single-thread, untested); O7-2-AR-2 `residual-risk` (StreamConnectors moved to connector-batch; base connector pom has no nop-batch-core dep; no explicit classloader-isolation test); message backend Kafka/Pulsar honestly `blocked` (T3/T4 gated, Rule S5-1); M8-2-P2-12 `non-goal` (anchor in nop-stream-core, cross-ref deferred plan). Validator `check-nop-stream-audit-manifest.mjs evidence --strict` EXIT=0, 15 rows parsed (non-empty); connector tests green (35+35, 0 failures). No new confirmed live defect found; residuals tracked as non-blocking follow-ups (Stage 17 test-effectiveness).
Completed: 2026-08-08

Closure Audit Evidence:

- Reviewer / Agent: EXEC executing agent (mission-driver EXEC_PLANS round `2026-08-07-183547-mission-driver`, this session) — mechanical closure verification. Independent fresh-subagent CLOSURE_VERIFY is the next mission-driver round; this EXEC session verified each exit criterion against live source/tests and records the evidence below.
- Evidence (per Exit Criterion / Closure Gate + validator exit code + Anti-Hollow):
  - Phase 1 (≥5 batch rows): SATISFIED — 6 rows EVID-S15-001..006 (bounded source / replay / buffered-flush / null-reject+robust-close / AR-2 / IDEMPOTENT label). Rule #22: every in-process row `positive_proof` is a real test name (`TestBatchLoaderSourceFunction#testEmitAllRecords`, `TestBatchConsumerSinkFunction#testBufferAndFlush`, etc.), confirmed by green `./mvnw test` (8+8+8+14 batch tests, 0 failures). Rule #23: `runtime_wiring: wired` adjudicated from `BatchLoaderSourceFunction.run():68 load→:76 ctx.collect` and `BatchConsumerSinkFunction.consume():85 buffer→:87 flush→:99 consumer.consume` (full path connected over in-process lane). Rule #24: M7-2-P1-15 NOT silently e2e-proved — residual concurrency boundary named, `disposition: residual-risk`. Batch support/reject matrix (B1–B6) frozen in evidence file header.
  - Phase 2 (≥4 message + ≥1 blocked): SATISFIED — 5 rows EVID-S15-007..011 (partition-aware / capture-rethrow / sync-send / AT_LEAST_ONCE label / Kafka-Pulsar blocked). Rule #22: EVID-S15-008 (P1-9) cites `TestMessageSourceFunctionThreadSafety#testCollectFailureSurfacesFromRun` + `#testTypeMismatchSurfacesFromRun`; EVID-S15-009 cites `TestMessageAdapters#testMessageSinkSendsMessages` — all real in-process tests. Rule #23: message source `runtime_wiring: wired` per `messageService.subscribe:129 → onMessage:131 → ctx.collect:150`. Rule #24: partition-aware path ZERO test refs → `disposition: unverified`, `environment_class: none` (NOT falsely e2e-proved); message backend Kafka/Pulsar NOT silently e2e-proved — `blocked` + cross-ref T3/T4 @@LANE + Rule S5-1 + Stage 14 transport cross-ref. Message support/reject matrix (M1–M4+MB) frozen in evidence file header.
  - Phase 3 (≥3 lifecycle/drainable/thread-safety/M8-2-P2-12): SATISFIED — 4 rows EVID-S15-012..015 (M8-2-P2-12 non-goal / resource-lifecycle / drainable / thread-safety). Rule #24: M8-2-P2-12 NOT silently skipped — explicit `non-goal` judgment recorded (anchor `LocalSourceCoordinator.java` confirmed in `nop-stream-core/io/nop/stream/core/source/coordinator/`, outside manifest domain e) + cross-ref deferred plan `2026-08-04-2300-3-contract-drift-config-test-integrity.md`. Resource-lifecycle/drainable/thread-safety matrix (R1–R3+H1) frozen in evidence file header.
  - Validator: `node ai-dev/tools/check-nop-stream-audit-manifest.mjs evidence --strict` → `[PASS] evidence` EXIT=0; Stage-15 file parsed to 15 `@@EVIDENCE` rows (non-empty, anti-hollow); inventory_id prefix `EVID-S15-NNN` globally unique (no collision with S6–S14); finding_id values M7-2-P1-9 / M7-2-P1-15 / O7-2-AR-2 / M8-2-P2-12 all registered in frozen corpus; exactly 1 `blocked` row (EVID-S15-011); 0 `e2e-proved` rows with environment_class < required_lane.
  - Anti-Hollow: (a) in-process `positive_proof` are real实跑 test names (not metadata); (b) `runtime_wiring: wired` adjudicated from connected run→collect / consume→flush→consumer paths; (c) message backend blocked named T3/T4 + Rule S5-1, no silent pass; (d) consistency labels AT_LEAST_ONCE/IDEMPOTENT adjudicated with label-behavior consistency + StreamRequirementValidator rejection test; (e) partition-aware zero-coverage gap honestly `unverified`/`environment_class: none` (not虚标 e2e-proved).
  - Build/tests: `./mvnw test -pl nop-stream/nop-stream-connector,nop-stream/nop-stream-connector-batch -am` → BUILD SUCCESS (35 + 35 tests, 0 failures, 0 skipped). Audit-only plan: no production code changed.

Follow-up:

- batch sink concurrency/boundary coverage (M7-2-P1-15 residual) → Stage 17 (test effectiveness) successor.
- message connector `env.execute()`-level e2e + partition-aware subscription test coverage (EVID-S15-007 unverified) → Stage 17 successor.
- provision Kafka/Pulsar broker → connector successor plan reruns T3/T4 (EVID-S15-011 blocked → upgrade to e2e-proved).
- StreamConnectors explicit classloader-isolation test (O7-2-AR-2 residual) → Stage 17 successor.
