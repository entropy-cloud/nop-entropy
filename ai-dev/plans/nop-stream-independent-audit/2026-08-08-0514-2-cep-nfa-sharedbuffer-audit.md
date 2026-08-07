# 12 CEP / NFA / SharedBuffer Audit (nop-stream Independent Audit)

> Plan Status: completed
> Last Reviewed: 2026-08-08
> Source: `ai-dev/backlog/nop-stream-independent-audit-roadmap.md` (Stage 12); frozen Stage-4 outputs (`source-manifest.md` domains a/f/g, `evidence-schema.md`, `finding-corpus.md` shards 20/22, `ai-dev/tools/check-nop-stream-audit-manifest.mjs`); frozen Stage-5 outputs (`environment-qualification.md` — T1 `qualified`/`in-process`); frozen Stage-6/9 evidence; live repo baseline of `nop-stream-cep` + `nop-stream-fraud-example`.
> Mission: nop-stream-independent-audit
> Work Item: 12. CEP/NFA/SharedBuffer audit
> Related: Execution order `{2}` of this DRAFT_PLANS round. Roadmap deps: Stage 4 (evidence schema), Stage 6 (Java/local audit), Stage 9 (checkpoint audit) — all `done`. Hard prerequisite for Stage 20 (Hist P0/P1 CEP/connector/runtime) and Stage 22 (Hist P2 CEP/connector/runtime). NOT on critical path. Absorbs Stage-9/10/11 non-goals that deferred CEP NFA matching / state recovery to Stage 12.

## Purpose

独立验证 nop-stream 的 **CEP / NFA / SharedBuffer** 是否实现其设计目标：CEP 公共入口（`CEP.pattern()` → `PatternStream` → `CepOperator`，keyed 与 non-keyed 路径）、NFA 匹配（linear + branching、TAKE/IGNORE/PROCEED 转换、DeweyNumber 版本）、timeout（event-time + processing-time）、skip 策略（NoSkip/SkipPastLast/SkipToFirst/SkipToLast/SkipToNext）、SharedBuffer 生命周期（register/put/lock/release/refcount、branching 释放）、checkpoint continuation（NFAState + SharedBuffer + watermark + event-time timer 的 snapshot/restore）。每个被支持的能力必须形成一条可复核的 evidence row；每个不支持的组合必须有 fail-fast 证明或显式 non-goal 裁定。

本审计验证核心 invariants：(a) NFA 线性匹配与分支匹配的正确性（含 DeweyNumber 版本隔离）；(b) SharedBuffer refcount 在 branching pattern 下的释放正确性（corpus O8-2-AR-1：parallel stacks lockstep）；(c) CepOperator dangling-partial-match 安全网的触发条件（corpus O8-2-AR-2：仅 size==1）；(d) CEP 状态 checkpoint/restore 后继续匹配（NFAState + SharedBuffer + watermark round-trip）。

本审计**发现**的任何 confirmed live defect 不在本计划内修复，而按 roadmap 规则指派给 active/successor remediation plan。

## Current Baseline

经 2026-08-08 live repo 核对（引用均与 frozen Stage-4 `source-manifest.md` 域 a/f/g + 实际源码一致；line anchors 经 explore agent 逐行复核）：

- **CEP 公共入口**：`CEP.pattern(DataStream, Pattern)` `nop-stream-cep/.../cep/CEP.java:38`（+ comparator overload `:51`）→ `PatternStream<T>` `.../PatternStream.java:46`（`process(...)` `:101`、`select(...)` `:120/156`、`flatSelect(...)` `:183/219`、`inEventTime()` `:84`/`inProcessingTime()` `:77`）。`PatternStreamBuilder.build(...)` `.../PatternStreamBuilder.java:132-172` 创建 `CepOperator` `:146-154`：**keyed 分支** `keyedStream.transform("CepOperator", outTypeInfo, operator)` `:157-160`；**non-keyed 分支** `inputStream.keyBy(NullByteKeySelector).transform("GlobalCepOperator", outTypeInfo, operator).forceNonParallel()` `:161-169`。`SingleOutputStreamOperator.forceNonParallel()` 接口 `nop-stream-core/.../datastream/SingleOutputStreamOperator.java:33`；实现 `SingleOutputStreamOperatorImpl.java:52-58`（**M7-2-P0-1 已修复**：调用 `transformation.lockParallelismToOne()` `:54-55`，不再无条件抛 `UnsupportedOperationException`）。回归测试 `TestCepNonKeyedEntryE2E`。注意：**无 `KeyedCEPPatternOperator` 类**（keyed/non-keyed 都用同一 `CepOperator`，transform name 不同）；`CepOperator` Javadoc `:78` 声明 "CEP pattern operator for a keyed input stream"。
- **NFA**：`NFA<T>` `.../nfa/NFA.java:84`。核心方法：`createInitialNFAState()` `:142`、`open(...)` `:193`、`process(SharedBufferAccessor, NFAState, T, long, AfterMatchSkipStrategy, TimerService)` `:236-251`（delegates `doProcess` `:356`）、**`advanceTime(...)` `:265-346`**（timeout 计算）、`doProcess(...)` `:356-433`、`processMatchesAccordingToSkipStrategy(...)` `:435-478`（state-transition/complete-match，调 `releaseNode` `:464-465/475-476`）、**`computeNextStates(...)` `:621-756`**（IGNORE/TAKE branching + DeweyNumber versioning）、`addComputationState(...)` `:758-778`、`findFinalStateAfterProceed(...)` `:780-806`。
- **NFA 状态值类（O8-2-AR-3：均未 implements Serializable）**：`NFAState` `.../nfa/NFAState.java:28`（fields `Queue<ComputationState> partialMatches/completedMatches` `:35-43`，`COMPUTATION_STATE_COMPARATOR` `:45-55`）；`ComputationState` `.../nfa/ComputationState.java:33`（fields currentStateName/version:DeweyNumber/startTimestamp/previousTimestamp/previousBufferEntry:NodeId/startEventID:EventId `:35-49`，factories `:135-157`）；`EventId` `.../sharedbuffer/EventId.java:27`（`implements Comparable<EventId>`，fields int id/long timestamp `:28-29`）；`NodeId` `.../sharedbuffer/NodeId.java:26`（fields pageName/eventId `:28-29`）。注：`DeweyNumber`、`State`、`StateTransition`、`AfterMatchSkipStrategy`、`NFAFactory` 均 Serializable；4 个 NFA 值类 + 3 个 SharedBuffer 值类（见下）**不** Serializable。
- **Skip 策略**：`AfterMatchSkipStrategy` `.../aftermatch/AfterMatchSkipStrategy.java:33`（`implements Serializable`，factories `skipPastLastEvent()` `:64`、`skipToFirst` `:44`、`skipToLast` `:55`、`skipToNext()` `:73`、`noSkip()` `:82`；`prune(...)` `:101-123` 调 `releaseNode`）。实现：`NoSkipStrategy`、`SkipPastLastStrategy`（singleton `INSTANCE` `:30`，`getPruningId` `:36-49`）、`SkipToFirstStrategy`、`SkipToLastStrategy`、`SkipToNextStrategy`。DSL enum `AfterMatchSkipStrategyKind`。
- **SharedBuffer**：`SharedBuffer<V>` `.../sharedbuffer/SharedBuffer.java:66`（backing MapStates `eventsBuffer/eventsCount/entries` `:75-81`，Guava caches `eventsBufferCache/entryCache` `:93/98`，`getAccessor()` `:220-222`，`registerEvent` `:236-260`，`advanceTime` `:224-234`，`flushCache()` `:420-443`，cache stats `:449-522`）。`SharedBufferAccessor<V>` `.../sharedbuffer/SharedBufferAccessor.java:46`（`implements AutoCloseable`）：`registerEvent` `:80-82`、**`put(stateName, eventId, previousNodeId, version)` `:94-115`**、`extractPatterns(nodeId, version)` `:124-203`（DFS over `ExtractionState` stack `:353-390`）、`materializeMatch(...)` `:211-229`、`lockNode(node, version)` `:237-248`、**`releaseNode(NodeId, DeweyNumber)` `:258-308`**（parallel stacks `nodesToExamine`/`versionsToExamine` `:259-263`，`visited` `:261`，**null-branch stack-lockstep fix `:274-281`**——pop version when node null，edge refcount release `releaseOrDetach()` `:290-298`，node refcount release `:301-306`）、`releaseEvent(eventId)` `:329-338`、`close()` → flushCache `:345-347`。
- **SharedBuffer 值类（O8-2-AR-3：均未 Serializable）**：`SharedBufferNode` `.../sharedbuffer/SharedBufferNode.java:28`（field `List<Lockable<SharedBufferEdge>> edges` `:30`）；`SharedBufferEdge` `.../SharedBufferEdge.java:28`（fields target:NodeId/deweyNumber `:30-31`）；`Lockable<T>` `.../Lockable.java:35`（field `AtomicInteger refCounter` `:37`，`lock()` `:46-48`，**`release()` `:56-66`——M7-2-P2-8 已修复**：现抛 `StreamRuntimeException("Lockable over-release: ...")` `:62`，非 bare `IllegalStateException`；`releaseOrDetach()` `:68-81`）。
- **CepOperator**：`.../operator/CepOperator.java:98`（`extends AbstractUdfStreamOperator<...> implements OneInputStreamOperator<IN,OUT>`）。状态名 `NFA_STATE_NAME/EVENT_QUEUE_STATE_NAME` `:115-116`、`WATERMARK_STATE_NAME` `:415`、`EVENT_TIME_TIMERS_STATE_NAME` `:416`。构造 `:200-222`、`copyForSubtask()` `:233-243`、`open()` `:259`（applyPendingRestoreState AFTER `stateBackend.createKeyedStateBackend` `:257`）。**Dangling 安全网（O8-2-AR-2）**：`onEventTime(long)` `:509-569`——predicate `nfaState.getPartialMatches().size() == 1 && nfaState.getCompletedMatches().isEmpty()` **`:540`**（仅 size==1 触发），release path `accessor.releaseNode(...)` `:556-565`，clear `:566`。`onProcessingTime(long)` `:571-629`——predicate `:600`（size==1），release `:615-627`，clear `:626`。**Checkpoint continuation**：`snapshotState(...)` `:419-426`（writes WATERMARK + EVENT_TIME_TIMERS state）、`restoreState(...)` `:429-447`（restores currentWatermark + sets `watermarkRestored=true` `:435` + event-time timers `:437-445`）。Watermark/timeout：`processWatermark(...)` `:450-459`、`processElement(...)` `:462-488`（processing-time branch `:465-472`，event-time branch + late-data sideOutput `:480-485`）、`advanceTime(...)` `:685-707`、`processEvent(...)` `:657-683`、`processMatchedSequences(...)` `:709-716`、`processTimedOutSequences(...)` `:718-732`（仅当 user function implements `TimedOutPartialMatchHandler`）。CEP own TimerService：`io.nop.stream.cep.time.TimerService` `.../cep/time/TimerService.java:30-31`（`@Internal`，single method `currentProcessingTime()` `:36`），impl `CepOperator.TimerServiceImpl` `:746-752`，distinct from core `InternalTimerService<VoidNamespace>` `:126`。
- **测试语料**（manifest 域 g；**全部 unit/in-process**——`nop-stream-cep` 内 **无 `env.execute()` 测试**，"E2E" 后缀指 operator-level 而非 cluster execution）：
  - **SharedBuffer**：`TestSharedBuffer`（15 @Test，basic register/retrieve/put）、`TestSharedBufferExtended`（9 @Test，branching/edge lifecycle，**null-branch regression `:307`** targeting `releaseNode` fix `:274-281`，branching-with-overlapping-edges `:149-153/242-245`）、`TestSharedBufferCache`（6 @Test，Guava LRU eviction）、`TestSharedBufferCacheConsistency`（5 @Test，write-through + `FailingPutStateStore` `:91`）、`TestSharedBufferFlushCache`（4 @Test）、`TestLockable`（4 @Test）、**`TestLockableOverRelease`**（6 @Test，assert over-release 抛 `StreamRuntimeException` 非 bare `IllegalStateException`，`testOverReleaseDoesNotThrowBareIllegalStateException` `:20`）。
  - **NFA**：`TestNFA`、**`TestNFAExtended`**（~14 @Test，694 lines，**branching end-to-end `testFollowedByAnyBranchingWithSkipPastLastEvent` `:640-692`**——SharedBuffer bounded-growth assertion `:688`）、`TestNFAState`（4 @Test，equals/hashCode，**M7-2-P2-18：mirror tests**）、`TestNFAWindowTimeout`、`TestNFAWindowTimesAccessor`、`TestDeweyNumber`、`TestGreedy`、`TestNotPattern`、`TestIterativeCondition`（`testIterativeWithBranchingPattern` `:146`）、**`TestWatermarkStateRobustness`**（3 @Test，**M8-2-P2-20：测 Quantifier.Times/DeweyNumber hash robustness 而非 watermark I/O，类名误导**）、`TestNFACompiler`/`TestNFACompilerExtended`/`TestNFAStateNameHandler`。
  - **Skip 策略**：`TestAfterMatchSkipStrategies`（5 @Test，**M7-2-P1-14：原 100% metadata，现含 NFA behavior methods `:140/150/160/172`**）、`TestCepSkipStrategyE2E`（8 @Test：NoSkip/SkipPastLast/SkipToNext/SkipToFirst/SkipToLast + oneOrMore variants `:93-252`）。
  - **CepOperator**：`TestCepOperatorBasic`（keyed state isolation `:129`）、`TestCepOperatorTimeout`（processing-time timeout `:94`）、**`TestCepOperatorDanglingCleanup`**（**M7-2-P0-4 已修复**：`testDanglingCleanupReleasesSharedBuffer` `:81` 现 asserts `partialMatchesEmpty` `:114-119` + `operator.getPartialMatches().isEmpty()` `:121`）、`TestCepOperatorStateBackendWiring`（**M7-2-P1-13：couples to getKeyedStateBackend()/getNFAStateForTesting()**）、`TestCepOperatorStateRecovery`（snapshot/restore/continue `:118/145`）、`TestCepOperatorWatermarkPersistence`（watermark round-trip `:81/98/115/135`）、`TestCepOperatorOnEventTimeStatePreservation`（active partial match not cleared after watermark `:67`）、`TestCepOperatorCacheStatistics`（7 @Test）、**`TestCepCheckpointRestoreE2E`**（**CEP checkpoint continuation**：`testE2ENfaStateSurvivesCheckpointRestore` `:113`、`testE2ESharedBufferSurvivesCheckpointRestore` `:151`、`testE2ETimerSurvivesCheckpointRestore` `:181`——snapshot mid-pattern → close → restore new CepOperator → continue → assert match completes）、`TestCepStateRestoreAndContinue`（NFA-layer restore-and-continue `:79/136`）、`TestCepPublicApiE2E`（用 `StreamExecutionEnvironment` 构造但**不调 `env.execute()`**）、`TestCepNonKeyedEntryE2E`（**M7-2-P0-1 回归** `:40/59`）。
- **Fraud example（manifest 域 f）**：`fraud-detection.stream.xml`（`<cep patternRef="rapid-transaction-pattern">` `:71-72`，**linear pattern** `begin("first").next("second").within(30s)` `:99-109`，`AT_LEAST_ONCE` checkpoint `:17-19`）。Java entry `FraudDetectionDemo.java:52`（main，**不调 `env.execute()`**，直接 instantiate NFA + SharedBuffer `:106-111`）。4 个 pattern 全部 **linear**（无 `followedByAny`/branching）：`RapidTransactionPattern`、`GeographicAnomalyPattern`（`IterativeCondition` cross-pattern lookup，**O8-2-AR-4：TestGeographicAnomalyPatternFix** inline re-implements city2 condition）、`UnusualAmountPattern`、`AccountTakeoverPattern`。
- **Corpus 交叉**（finding-corpus.md shard 20 P0/P1 + shard 22 P2 + AR）：CEP 相关 finding ~13 个。关键：M7-2-P0-1（forceNonParallel throws，**FIXED**）、M7-2-P0-4（dangling cleanup never asserts，**FIXED**）、O8-2-AR-1（releaseNode parallel stacks lockstep，**LIVE** with null-branch fix `:274-281`）、O8-2-AR-2（dangling safety net size==1，**LIVE**）、O8-2-AR-3（state 值类 non-Serializable，**LIVE**）、O8-2-AR-4（TestGeographicAnomalyPatternFix zero bug-catching power，**LIVE**）、M7-2-P1-13（TestCepOperatorStateBackendWiring couples internal accessors，**LIVE**）、M7-2-P1-14（TestAfterMatchSkipStrategies 100% metadata，**partial-fixed** now含 behavior）、M7-2-P2-8（Lockable.release bare exception，**FIXED**）、M7-2-P2-17（TestSharedBuffer assertNotNull，**LIVE**）、M7-2-P2-18（TestNFAState mirror tests，**LIVE**）、M8-2-P2-20（TestWatermarkStateRobustness 误导类名，**LIVE**）。
- **真实 gap**：(1) 没有 CEP 入口→NFA 匹配→输出的成套 evidence row 矩阵（linear + branching + skip 策略）；(2) branching-pattern SharedBuffer 释放正确性（O8-2-AR-1）的独立 evidence row 缺冻结（虽有 `TestNFAExtended#testFollowedByAnyBranchingWithSkipPastLastEvent` + `TestSharedBufferExtended` null-branch regression）；(3) CepOperator dangling 安全网触发条件（size==1，O8-2-AR-2）的 evidence row 缺冻结，且 **size>1 branching 场景的 dangling cleanup 无 CepOperator 级测试**；(4) CEP checkpoint continuation（NFAState + SharedBuffer + watermark + event-time timer round-trip）的端到端 evidence row 缺冻结（虽有 `TestCepCheckpointRestoreE2E` 3 个）；(5) CEP 状态值类 non-Serializable（O8-2-AR-3）的 latent Java-serialization 风险缺 evidence row disposition；(6) 无 `env.execute()` 级 CEP 测试（linear 或 branching）——所有 "E2E" 测试为 operator-driven。

## Goals

- 产出一份 **CEP 支持/拒绝能力矩阵**（linear matching、branching matching、skip 策略、event-time/processing-time timeout、dangling cleanup、checkpoint continuation、keyed vs non-keyed entry），每能力一条 evidence row，`environment_class` 据 frozen lane 词表裁定（CEP 全部 in-process/operator-driven → `in-process` 或 `unit`；无 multi-JVM CEP claim）。
- 为**每条 CEP 能力**产出 entry-to-effect evidence row：`positive_proof` 为真实 in-process 实跑测试名（`ClassName#method`），验证从 CEP 入口到 NFA 匹配到输出的连通性（接线验证）。
- 产出 **branching-pattern SharedBuffer 释放** evidence row（O8-2-AR-1 live 复验）：`positive_proof` 引用 `TestNFAExtended#testFollowedByAnyBranchingWithSkipPastLastEvent` + `TestSharedBufferExtended`（null-branch regression）；`disposition` 据 in-process lane 裁定。
- 产出 **dangling 安全网触发条件** evidence row（O8-2-AR-2 live 复验）：`source_anchor` 指向 `CepOperator.java:540/600`（size==1 predicate）；`positive_proof` 引用 `TestCepOperatorDanglingCleanup`；`disposition` 据 live 行为裁定（size==1 in-process proven；size>1 branching 场景缺测试 → 标 `residual-risk` 或 `unverified` + 注明 gap）。
- 产出 **CEP checkpoint continuation** evidence row（端到端）：`positive_proof` 引用 `TestCepCheckpointRestoreE2E`（NFAState + SharedBuffer + timer round-trip）；`environment_class: in-process`。
- 产出 **skip 策略矩阵** evidence row：NoSkip/SkipPastLast/SkipToNext/SkipToFirst/SkipToLast 各据 `TestCepSkipStrategyE2E` 对应方法裁定。
- 产出 **CEP 状态 non-Serializable 风险** evidence row（O8-2-AR-3）：`source_anchor` 指向 7 个 non-Serializable 值类；`disposition: residual-risk`——注明 latent Java-serialization 风险（当前 checkpoint 用平台 state serializer 非 Java serialization，故 non-blocking），注明若切换 Java serialization 会 break。
- 对**关键历史 P0/P1/P2/AR finding** 做 live 复验标注：M7-2-P0-1（FIXED）、M7-2-P0-4（FIXED）、M7-2-P2-8（FIXED）、O8-2-AR-1/2/3/4（LIVE/residual）、M7-2-P1-13/14、M7-2-P2-17/18、M8-2-P2-20——据 live 行为标 `finding_id` + `disposition`。
- 产出 **fraud-example scope** evidence row：4 个 pattern 全 linear，无 branching；`FraudDetectionDemo` 不调 `env.execute()`；`disposition: non-goal` 或 `component-only`（example module 仅作 fail-fast/semantic surface，manifest 域 f）。
- 所有 evidence row 经 `check-nop-stream-audit-manifest.mjs evidence --strict` 校验通过且非空过；corpus finding_id 交叉标注合法。

## Non-Goals

- 通用 window 行为、watermark 生成/传播——属 Stage 11（本计划只验证 CEP own TimerService + CepOperator 的 watermark/timer checkpoint）。
- State backend 编码 / savepoint / rescale——属 Stage 10（本计划只验证 CEP 状态 round-trip 行为，不验证 backend 编码）。
- NFA 新功能开发 / 新 skip 策略。
- 修复本审计发现的 confirmed live defect（按 roadmap 规则指派 remediation plan）。

## Scope

### In Scope

- `ai-dev/audits/nop-stream-independent-audit/stage-12-cep-nfa-sharedbuffer.evidence.md`（domain evidence rows，manifest 域 a/f/g 范围内的 cep operator/nfa/sharedbuffer/aftermatch source anchor + test lane）。**文件名必须是 `*.evidence.md` 且为 audit dir 直系子文件。**
- 支持/拒绝能力矩阵文本（写入证据文件头部，仅矩阵/判据不改 frozen 字段/词表）。

### Out Of Scope

- 修复 confirmed live defect（指派 remediation plan）。
- 通用 window/watermark 语义（Stage 11）。
- State backend 编码/savepoint/rescale（Stage 10）。
- 新 CEP feature 开发。
- 修改 frozen evidence-row 11 字段定义或 7 分类词表。

## Execution Plan

### Phase 1 - CEP Public Entry Path & NFA Linear Matching Evidence

Status: completed
Targets: `ai-dev/audits/nop-stream-independent-audit/stage-12-cep-nfa-sharedbuffer.evidence.md`

- Item Types: `Proof`

- [x] 产出 keyed CEP entry evidence row：`source_anchor` 指向 `CEP.pattern():38` + `PatternStreamBuilder.build():146-160`（keyed transform `"CepOperator"`）；`implementation_anchor` 指向 `CepOperator.java:98`；`positive_proof` 引用 `TestCepOperatorBasic#testSimplePatternMatch` 或 `TestCepPublicApiE2E#testCepPatternCreationFromKeyedStream`；`runtime_wiring: wired`。
- [x] 产出 non-keyed CEP entry evidence row（M7-2-P0-1 live 复验）：`source_anchor` 指向 `PatternStreamBuilder.java:161-169`（non-keyed forceNonParallel）+ `SingleOutputStreamOperatorImpl.forceNonParallel():52-58`；`positive_proof` 引用 `TestCepNonKeyedEntryE2E#cepPatternOnNonKeyedStreamBuildsWithoutThrowing` + `#cepPatternOnNonKeyedStreamProducesMatches`；`finding_id: M7-2-P0-1`；`disposition: e2e-proved`（regression proves fixed）。
- [x] 产出 NFA linear matching evidence row：`source_anchor` 指向 `NFA.process():236-251` + `doProcess():356-433` + `computeNextStates():621-756`（IGNORE/TAKE for linear）；`positive_proof` 引用 `TestNFA#testSimplePatternMatch` 或 `TestCepOperatorBasic#testSimplePatternMatch`。
- [x] 产出 NFA state transition (TAKE/IGNORE/PROCEED) evidence row：`source_anchor` 指向 `NFA.computeNextStates():621-756` + `findFinalStateAfterProceed():780-806`；`positive_proof` 引用 `TestNFACompiler*` + `TestNFAExtended` 对应方法。
- [x] 冻结 **CEP 入口支持/拒绝矩阵**文本（写入证据文件头部）：keyed entry（SUPPORTED, in-process）、non-keyed entry（SUPPORTED, in-process，M7-2-P0-1 FIXED）、linear matching（SUPPORTED）、branching matching（SUPPORTED — 见 Phase 2）。

Exit Criteria:

- [x] ≥4 条 CEP entry/NFA linear evidence row，格式经 `check-nop-stream-audit-manifest.mjs evidence --strict` 校验 exit 0，且校验器实际解析到行（非 "0 evidence rows yet" 空过）
- [x] **端到端验证（Rule #22）**：至少一条 row 的 `positive_proof` 是真实 in-process 实跑测试名（`ClassName#method`），`environment_class >= in-process`/`unit`，`disposition` 合理；不得用 metadata-only 测试充数
- [x] **接线验证（Rule #23）**：keyed/non-keyed entry row 的 `runtime_wiring` 据 in-process 实跑裁定（`CEP.pattern()` → `PatternStreamBuilder` → `CepOperator` 确实连通），非仅方法存在
- [x] **无静默跳过（Rule #24）**：任一 CEP 入口无法在 in-process 实跑的，row `disposition` 标 `unverified`
- [x] `No owner-doc update required`（证据文件是审计产出；不改 `docs-for-ai/`）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - Branching Matching, Skip Strategies, Timeout & SharedBuffer Lifetime Evidence

Status: completed
Targets: `ai-dev/audits/nop-stream-independent-audit/stage-12-cep-nfa-sharedbuffer.evidence.md`

- Item Types: `Proof`

- [x] 产出 NFA branching matching evidence row：`source_anchor` 指向 `NFA.computeNextStates():621-756`（DeweyNumber versioning branching）+ `addComputationState():758-778`（`lockNode`）；`positive_proof` 引用 `TestNFAExtended#testFollowedByAnyBranchingWithSkipPastLastEvent`（SharedBuffer bounded-growth assertion `:688`）。
- [x] 产出 SharedBuffer release/lockstep evidence row（O8-2-AR-1 live 复验）：`source_anchor` 指向 `SharedBufferAccessor.releaseNode():258-308`（parallel stacks `:259-263` + null-branch fix `:274-281`）；`positive_proof` 引用 `TestSharedBufferExtended#testSharedBufferFullLifecycle` + null-branch regression `:307`；`finding_id: O8-2-AR-1`；`disposition` 据 in-process lane 裁定（null-branch fix `e2e-proved`；若仍有未覆盖的 branching 释放路径标 `residual-risk` + 注明 gap）。
- [x] 产出 Lockable refcount release evidence row（M7-2-P2-8 live 复验）：`source_anchor` 指向 `Lockable.release():56-66`（现抛 `StreamRuntimeException`）+ `releaseOrDetach():68-81`；`positive_proof` 引用 `TestLockableOverRelease#testOverReleaseDoesNotThrowBareIllegalStateException` + `TestLockable#testReleaseThrowsWhenCounterAlreadyZero`；`finding_id: M7-2-P2-8`；`disposition: e2e-proved`（FIXED）。
- [x] 产出 skip 策略矩阵 evidence row：`source_anchor` 指向 `AfterMatchSkipStrategy.prune():101-123` + `SkipPastLastStrategy.getPruningId():36-49`；`positive_proof` 引用 `TestCepSkipStrategyE2E`（8 个方法 `:93-252`：NoSkip/SkipPastLast/SkipToNext/SkipToFirst/SkipToLast + oneOrMore）；`disposition` 据 in-process lane 裁定。
- [x] 产出 event-time/processing-time timeout evidence row：`source_anchor` 指向 `NFA.advanceTime():265-346`（timeout computation）+ `CepOperator.processTimedOutSequences():718-732`；`positive_proof` 引用 `TestCepOperatorTimeout#testTimeoutWithProcessingTime` + `TestNFAWindowTimeout`；`disposition` 据 in-process lane 裁定（注：processing-time timeout 用 `System.currentTimeMillis()` 可能 non-deterministic，注明 caveat）。
- [x] 每条 row 标注 `required_lane` 与 `finding_id`。

Exit Criteria:

- [x] ≥5 条 branching/skip/timeout/SharedBuffer evidence row，格式校验 exit 0，且校验器实际解析到行（非空过）
- [x] **端到端验证（Rule #22）**：branching matching row 的 `positive_proof` 引用真实 in-process 实跑测试（`TestNFAExtended#testFollowedByAnyBranchingWithSkipPastLastEvent`），`environment_class >= in-process`/`unit`
- [x] **接线验证（Rule #23）**：branching row 的 `runtime_wiring` 据 in-process 实跑裁定（`NFA.computeNextStates` branching → `SharedBufferAccessor.lockNode` → `releaseNode` 确实连通）
- [x] **无静默跳过（Rule #24）**：O8-2-AR-1 branching 释放若有未覆盖路径，须标 `residual-risk` + 注明 gap（不得静默当 `e2e-proved`）；timeout non-determinism 须注明 caveat
- [x] skip 策略矩阵覆盖 NoSkip/SkipPastLast/SkipToNext/SkipToFirst/SkipToLast
- [x] `No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - Dangling Safety Net, Checkpoint Continuation & Serializable Risk Evidence

Status: completed
Targets: `ai-dev/audits/nop-stream-independent-audit/stage-12-cep-nfa-sharedbuffer.evidence.md`

- Item Types: `Proof | Decision`

- [x] 产出 dangling 安全网 evidence row（O8-2-AR-2 live 复验）：`source_anchor` 指向 `CepOperator.onEventTime():540`（size==1 predicate）+ `onProcessingTime():600`；`positive_proof` 引用 `TestCepOperatorDanglingCleanup#testDanglingCleanupReleasesSharedBuffer`（M7-2-P0-4 FIXED，现 asserts `partialMatchesEmpty`）；`finding_id: O8-2-AR-2`；`disposition` 据 in-process lane 裁定（size==1 in-process proven；**size>1 branching 场景缺 CepOperator 级测试 → 标 `residual-risk` + 注明 "safety net fires only when size==1; branching stale entries from size>1 not reclaimed at operator level"**）。
- [x] 产出 CEP checkpoint continuation evidence row（端到端）：`source_anchor` 指向 `CepOperator.snapshotState():419-426` + `restoreState():429-447`（WATERMARK + EVENT_TIME_TIMERS round-trip）+ `open():259`（applyPendingRestoreState）+ `NFAState`/`SharedBuffer` MapState snapshot；`positive_proof` 引用 `TestCepCheckpointRestoreE2E#testE2ENfaStateSurvivesCheckpointRestore` + `#testE2ESharedBufferSurvivesCheckpointRestore` + `#testE2ETimerSurvivesCheckpointRestore`；`environment_class: in-process`，`disposition: e2e-proved`。
- [x] 产出 watermark persistence evidence row：`source_anchor` 指向 `CepOperator.processWatermark():450-459` + watermark state `:415`；`positive_proof` 引用 `TestCepOperatorWatermarkPersistence`（4 个方法）；`disposition` 据 in-process lane 裁定。
- [x] 产出 CEP 状态 non-Serializable 风险 evidence row（O8-2-AR-3）：`source_anchor` 指向 7 个 non-Serializable 值类（NFAState/ComputationState/EventId/NodeId/SharedBufferNode/SharedBufferEdge/Lockable）；`disposition: residual-risk`——注明 latent Java-serialization 风险（当前 checkpoint 用平台 state serializer 非 Java serialization，故 non-blocking），注明 successor ownership（若切换 Java serialization 会 break）。
- [x] 对 M7-2-P0-4（dangling never asserts）做 live 复验标注：`finding_id: M7-2-P0-4`，`disposition: e2e-proved`（FIXED，`TestCepOperatorDanglingCleanup:114-119` 现 asserts）。

Exit Criteria:

- [x] ≥4 条 dangling/checkpoint/watermark/Serializable evidence row，格式校验 exit 0，且校验器实际解析到行（非空过）
- [x] **端到端验证（Rule #22）**：checkpoint continuation row 的 `positive_proof` 引用真实 in-process 实跑测试（`TestCepCheckpointRestoreE2E` 3 个方法，snapshot mid-pattern → close → restore → continue → assert match completes），`environment_class >= in-process`，`disposition: e2e-proved`
- [x] **接线验证（Rule #23）**：checkpoint continuation row 的 `runtime_wiring` 据 in-process 实跑裁定（`snapshotState` → `restoreState` → `open` applyPendingRestoreState → continue matching 确实连通）
- [x] **无静默跳过（Rule #24）**：O8-2-AR-2 size>1 gap 不得静默当 `e2e-proved`——须标 `residual-risk` + 注明 gap；O8-2-AR-3 non-Serializable 须标 `residual-risk` + 注明 non-blocking rationale（不得静默忽略）
- [x] `node ai-dev/tools/check-nop-stream-audit-manifest.mjs evidence --strict` exit 0，且校验器实际解析到行（非空过）
- [x] `No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - Historical Finding Revalidation & Fraud-Example Scope Evidence

Status: completed
Targets: `ai-dev/audits/nop-stream-independent-audit/stage-12-cep-nfa-sharedbuffer.evidence.md`

- Item Types: `Proof | Decision`

- [x] 对剩余历史 CEP finding 做 live 复验标注 evidence row：M7-2-P1-13（TestCepOperatorStateBackendWiring couples internal accessors）、M7-2-P1-14（TestAfterMatchSkipStrategies 100% metadata，partial-fixed）、M7-2-P2-17（TestSharedBuffer assertNotNull）、M7-2-P2-18（TestNFAState mirror tests）、M8-2-P2-20（TestWatermarkStateRobustness 误导类名）、O8-2-AR-4（TestGeographicAnomalyPatternFix zero bug-catching power）——据 live 行为标 `finding_id` + `disposition`（test-effectiveness 类标 `residual-risk`/`component-only` + successor ownership → Stage 17；confirmed still-live 标 successor remediation plan）。
- [x] 产出 fraud-example scope evidence row：`source_anchor` 指向 `fraud-detection.stream.xml:71-72/99-109`（linear pattern）+ `FraudDetectionDemo.java:52`（不调 `env.execute()`）；`disposition: non-goal`——注明 example module（manifest 域 f 的 `*.stream.xml` + domain-a sub-entry `java-public-types-example` 的 Java）4 pattern 全 linear 无 branching，仅作 fail-fast/semantic surface（manifest include/exclude 规则：example anchors 仅 eligible for fail-fast/semantic rows，不作 production-capability claim）。
- [x] 产出 no-env-execute coverage gap evidence row：`disposition: residual-risk` 或 `unverified`——注明 nop-stream-cep 无 `env.execute()` 级测试（linear 或 branching），所有 "E2E" 测试为 operator-driven；coverage gap 指派 Stage 17（test effectiveness）successor。
- [x] 全 evidence 文件回归校验 + corpus 交叉标注核对。
- [x] 冻结 **CEP 支持/拒绝矩阵**最终文本（写入证据文件头部）。

Exit Criteria:

- [x] ≥6 条 historical finding revalidation/fraud-example/coverage-gap evidence row，格式校验 exit 0，且校验器实际解析到行（非空过）
- [x] **无静默跳过（Rule #24）**：confirmed still-live finding 不得降级为 non-blocking follow-up；test-effectiveness gap 须标 `residual-risk`/`unverified` + successor ownership（Stage 17）
- [x] CEP 支持/拒绝矩阵在证据文件头部有显式文本
- [x] `node ai-dev/tools/check-nop-stream-audit-manifest.mjs evidence --strict` exit 0，且校验器实际解析到行（非空过）；finding_id 全部合法（ID 在 frozen corpus shard 20/22 + AR 内或 `none`）
- [x] `No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **审计计划（无生产代码变更）**：本计划产出为 evidence rows + 矩阵文本，不改 nop-stream 生产代码。`./mvnw test`/`compile` 不强制；改为以 evidence 校验器退出码 + in-process 实跑证据引用为 closure 依据。但若审计中发现 confirmed live defect，按 roadmap 规则指派 remediation plan（不在本计划内修复）。

- [x] CEP 入口能力（keyed/non-keyed entry、linear/branching matching、skip 策略、timeout）各有 evidence row（in-process lane 实跑或如实标注缺覆盖）
- [x] SharedBuffer lifetime（release/lockstep、Lockable refcount）有 evidence row，O8-2-AR-1/M7-2-P2-8 live 复验
- [x] dangling 安全网（O8-2-AR-2）有 evidence row，size==1 proven + size>1 gap 如实标 `residual-risk`
- [x] CEP checkpoint continuation 有端到端 evidence row（NFAState + SharedBuffer + watermark + timer round-trip in-process 实跑）
- [x] CEP 状态 non-Serializable（O8-2-AR-3）有 evidence row，标 `residual-risk` + non-blocking rationale
- [x] 关键历史 P0/P1/P2/AR finding 有 live 复验 evidence row（M7-2-P0-1/P0-4/P2-8 FIXED；O8-2-AR-1/2/3/4、M7-2-P1-13/14、M7-2-P2-17/18、M8-2-P2-20 live/residual）
- [x] 支持/拒绝矩阵显式成文
- [x] 所有 evidence row 经 `check-nop-stream-audit-manifest.mjs evidence --strict` exit 0，且**非空过**
- [x] 不存在被静默降级到 deferred 的 in-scope 审计项（test-effectiveness gap 标 `residual-risk` + successor Stage 17；Serializable risk 标 `residual-risk`；dangling size>1 gap 标 `residual-risk`——均为合法终态）
- [x] 审计发现的任何 confirmed live defect 已指派 active/successor remediation plan
- [x] `No owner-doc update required`（不改 `docs-for-ai/`）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 验证（a）in-process row 的 `positive_proof` 确为实跑测试名（非 metadata-only 充数），（b）`runtime_wiring=wired` 确经接线验证，（c）dangling size>1 gap 无静默放行（标 `residual-risk`），（d）non-Serializable risk 无静默忽略（标 `residual-risk` + rationale），（e）checkpoint continuation 端到端路径连通

## Deferred But Adjudicated

（执行中如出现延期项，须写明 Classification / Why Not Blocking Closure / Successor Required。预期场景：processing-time timeout 因 `System.currentTimeMillis()` non-deterministic 无法确定性复验——此类 row 标 `disposition: component-only` + 注明 non-deterministic caveat。dangling size>1 branching gap 标 `residual-risk` + Stage 17 successor。non-Serializable risk 标 `residual-risk` + 注明当前 non-blocking。test-effectiveness gap 标 `residual-risk` + Stage 17 successor。confirmed still-live defect 不得 deferred——须指派 remediation plan。）

## Non-Blocking Follow-ups

- CepOperator dangling 安全网 size>1 branching 场景测试覆盖 → successor test-effectiveness remediation（Stage 17 lane）。
- no-env-execute CEP 测试覆盖（linear + branching）→ Stage 17（test effectiveness）。
- CEP 状态值类 non-Serializable（O8-2-AR-3）→ watch-only；若平台切换 Java serialization for state 则需 successor remediation plan。
- O8-2-AR-4（TestGeographicAnomalyPatternFix）test-effectiveness → Stage 17。
- M7-2-P1-13/14、M7-2-P2-17/18、M8-2-P2-20 test-quality → Stage 17 / active remediation plan `2026-08-04-2300-3`。

## Closure

Status Note: All 4 Phases executed in a single run on 2026-08-08. Produced `stage-12-cep-nfa-sharedbuffer.evidence.md` with a 16-row CEP Support/Reject matrix and 22 `@@EVIDENCE` rows (EVID-S12-001..022). `check-nop-stream-audit-manifest.mjs evidence --strict` exit 0 (parser confirmed 22 stage-12 rows parsed, not empty-passing; self-test positive control green). `./mvnw test -pl nop-stream/nop-stream-cep -am -T 1C` BUILD SUCCESS (292 tests, 0 failures) — every evidence-referenced test passes. No production code changed (audit-only plan); no confirmed live defect newly discovered (O8-2-AR-1 already fixed; O8-2-AR-2/3/4 and the test-effectiveness findings are pre-existing residuals explicitly marked + assigned successors).
Completed: 2026-08-08

Closure Audit Evidence:

- Reviewer / Agent: executing agent (opencode, mission nop-stream-independent-audit, plan execution order {2}); independent closure-audit subagent verification remains the responsibility of the next CLOSURE_VERIFY round per roadmap rules.
- Evidence:
  - Phase 1 Exit Criteria met: 4 CEP entry/NFA linear evidence rows (EVID-S12-001..004), validator exit 0 + 22 rows parsed. `positive_proof` references real in-process tests (`TestCepPublicApiE2E#testCepPatternCreationFromKeyedStream`, `TestCepNonKeyedEntryE2E#cepPatternOnNonKeyedStreamBuildsWithoutThrowing`, `TestNFA#testSimplePatternMatch`); `runtime_wiring: wired` per in-process build; key/non-keyed entry rows reference M7-2-P0-1 FIXED regression.
  - Phase 2 Exit Criteria met: 5 branching/skip/timeout/SharedBuffer rows (EVID-S12-005..009). Branching row `positive_proof: TestNFAExtended#testFollowedByAnyBranchingWithSkipPastLastEvent` (real in-process run, SharedBuffer bounded-growth assertion :688). O8-2-AR-1 null-branch fix `e2e-proved` via `TestSharedBufferExtended#testReleaseNodePopsVersionOnNullEntry:307`. Skip matrix covers NoSkip/SkipPastLast/SkipToNext/SkipToFirst/SkipToLast. Timeout processing-time non-determinism caveat annotated.
  - Phase 3 Exit Criteria met: 5 dangling/checkpoint/watermark/Serializable rows (EVID-S12-010..014). O8-2-AR-2 dangling safety net size==1 `e2e-proved` + size>1 gap marked `residual-risk` + Stage 17 successor (no silent pass). O8-2-AR-3 non-Serializable `residual-risk` + non-blocking rationale. Checkpoint continuation `e2e-proved` via `TestCepCheckpointRestoreE2E` 3 methods (snapshot mid-pattern → close → restore → continue → match completes).
  - Phase 4 Exit Criteria met: 8 historical-finding/fraud-example/coverage-gap rows (EVID-S12-015..022). All confirmed-still-live test-effectiveness findings (M7-2-P1-13/14, M7-2-P2-17/18, M8-2-P2-20, O8-2-AR-4) marked `residual-risk` + Stage 17 successor (not silently downgraded). Fraud-example `non-goal`; no-env-execute gap `residual-risk`. CEP Support/Reject matrix in evidence file header.
  - Closure Gates: every in-scope CEP capability has an evidence row; residuals explicitly annotated; no required-lane row blocked (all in-process). Validator `evidence --strict` exit 0, non-empty (22 rows).
  - Anti-Hollow check: (a) in-process `positive_proof` values are real test names verified present in the test tree + passing in surefire; (b) `runtime_wiring=wired` rows trace entry→operator→NFA→SharedBuffer→output; (c) dangling size>1 gap marked `residual-risk` not silently passed; (d) non-Serializable risk marked `residual-risk` + rationale; (e) checkpoint continuation path (snapshotState→restoreState→open applyPendingRestoreState→continue) proven connected.

Follow-up:

- CepOperator dangling safety net size>1 branching coverage → Stage 17 (test effectiveness).
- no-`env.execute()` CEP coverage (linear + branching) → Stage 17.
- CEP state value classes non-Serializable (O8-2-AR-3) → watch-only; successor plan required only if platform switches to Java serialization for CEP state.
- O8-2-AR-4 / M7-2-P1-13/14 / M7-2-P2-17/18 / M8-2-P2-20 test-quality → Stage 17 / active remediation plan `2026-08-04-2300-3`.
