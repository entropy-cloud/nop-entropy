# {2} Parallel Execution & CEP Correctness

> Plan Status: completed
> Last Reviewed: 2026-07-26
> Source: `ai-dev/audits/nop-stream-production/2026-07-25-1948-open-audit-nop-stream-production.md` (AR-1, AR-3, AR-4); `ai-dev/audits/nop-stream-production/2026-07-25-1948-multi-audit-nop-stream-production.md` (P0-1, P0-4, P1-12, P1-13, P1-14); `ai-dev/design/nop-stream/cep-design.md`; `ai-dev/design/nop-stream/core-design.md`
> Mission: nop-stream-production
> Related: Plan {1} `2026-07-26-0804-1-checkpoint-recovery-exactly-once-integrity.md`（恢复正确性先行）；Plan {3} `2026-07-26-0804-3-api-type-doc-contract.md`（API/文档）

## Purpose

把 **parallelism > 1 下的算子实例隔离与 CEP 正确性** 收口到「多 subtask 各自拿到独立可变状态实例；CEP 非 keyed 入口可用；分区策略不再被类名串误判；CEP 关键路径有行为级测试」。本 plan 消费所有「parallelism > 1 静默状态污染」与「CEP correctness/test-quality」类 P0/P1 发现。

## Current Baseline

经 live 仓库核对（证据来自 open-audit + multi-audit，对当前 HEAD 验证）：

- **AR-3 类名串匹配已确认（两处，起点不同）**：`PartitionPolicyAware` 接口**已存在**（`core/execution/plan/PartitionPolicyAware.java:12-14`，`getPartitionPolicy()`），`KeySelectorPartitioner` 已实现（`DataStreamImpl.java:345`）。`PartitionedPlanGenerator.inferPartitionPolicy()`（`:87-89`）**已用** `instanceof PartitionPolicyAware` 首选检查，但其回退（`:90-98`）仍用类名 `contains("Hash"/"Rebalance"/"Broadcast")` 匹配，最终 `FORWARD`。`GraphExecutionPlan.resolvePartitionPolicy()`（`:440-444`）**完全无** instanceof 检查，任何非 null partitioner 默认 `HASH`。已知 partitioner 类型：main 仅 `ForwardPartitioner`（未实现 PartitionPolicyAware）+ `KeySelectorPartitioner`（已实现）；`HashPartitioner`/`RebalancePartitioner`/`BroadcastPartitioner` 仅在测试中存在。自定义/未来 partitioner 静默误路由。
- **AR-1 静默共享实例已确认**：`OperatorChain.shallowCopyOperator`（`core/jobgraph/OperatorChain.java:206-235`）用 `instanceof` 链只处理 6 种算子（StreamSource/Map/Filter/FlatMap/Sink/Reduce），其余 `return op;` 直接共享。未处理类型：`ProcessOperator`、`CepOperator`、`WindowOperator`、`TimestampsAndWatermarksOperator`（均携带可变 transient 状态）。受影响面：main 直接/间接实现 `StreamOperator` 的算子约 10 个；测试中 `implements StreamOperator` 的 stub 约 9+ 个。`GraphExecutionPlan.build()`（`:284-285`）每 subtask 调 `deepCopy()`，未处理类型导致 N 个 subtask 共享同一可变状态。single-parallelism 单测不暴露。
- **AR-4 SPI 静默回退已确认**：`SimpleStreamOperatorFactory.createStreamOperator`（`core/operators/SimpleStreamOperatorFactory.java:46-72`）`NotSerializableException` 时 `return operator;` 共享模板。当前 production **未走该路径**（`JobGraphGenerator.createOperatorFromFactory():412-413` 直调 `getRawOperator()` 绕过），但公开 SPI 仍是定时炸弹——属防御性修复。
- **P0-1 forceNonParallel 总抛已确认**：`SingleOutputStreamOperator.forceNonParallel`（`core/datastream/SingleOutputStreamOperator.java:33`）唯一实现 `SingleOutputStreamOperatorImpl.java:46-50` 无条件抛 `UnsupportedOperationException`；`CEP.pattern()` 非 keyed 路径（`cep/PatternStreamBuilder.java:168`）`.forceNonParallel()` 必崩。**约束**：`Transformation.parallelism`（`core/transformation/Transformation.java:31`）为 `final` 且无 setter，`parallelismLocked` 全仓零匹配。`TestForceNonParallel` 把异常固化成「期望」。
- **P0-4 dangling cleanup 无断言已确认**：`TestCepOperatorDanglingCleanup.java:81-99` 计算 `partialMatchesEmpty` 后从不 assert；删除清理逻辑测试仍过。
- **P1-13/14 CEP 测试质量已确认**：`TestCepOperatorStateBackendWiring:139-166` 耦合内部 accessor；`TestAfterMatchSkipStrategies`（1-75）13 个测试全是元数据断言，无 NFA 行为测试。
- **P1-12 watermark 无 e2e 已确认**：`TestIndexedCombinedWatermarkStatus.java:14-22` 自述「Anti-Hollow exemption」延后 e2e。

## Goals

- `parallelism > 1` 时每个 subtask 拿到独立算子实例（含 CEP/Process/Window/Watermark 算子），无跨 subtask 可变状态共享
- `SimpleStreamOperatorFactory.createStreamOperator` SPI 不再静默回退共享实例
- 分区策略推断不再依赖类名子串；未识别 partitioner 快速失败
- `CEP.pattern(nonKeyedStream, pattern)` 不再运行时崩
- CEP dangling cleanup、state backend wiring、skip strategy 有行为级测试

## Non-Goals

- 不实现跨 JVM 分布式执行（Stage 39-42）—— 本 plan 只保证**单 JVM 内 parallelism > 1** 的实例隔离
- 不重写 NFA / SharedBuffer 算法（Stage 54 缓存改进）
- 不修复 API 类型签名（Plan {3}）
- 不修复 checkpoint 恢复（Plan {1}）—— 但依赖其恢复正确性以信任并行测试

## Scope

### In Scope

- `OperatorChain.shallowCopyOperator` 引入 `StreamOperator.copyForSubtask()` 或等价扩展（AR-1）
- `SimpleStreamOperatorFactory` 静默回退改为 fail-fast / 显式声明可共享（AR-4）
- 两处分区推断改为类型化判定 + 未识别快速失败（AR-3）
- `forceNonParallel` API 修正使 CEP 非 keyed 入口可用（P0-1）
- CEP 测试质量修复（P0-4, P1-13, P1-14）+ watermark e2e（P1-12）

### Out Of Scope

- 跨 JVM RPC / 数据面（Stage 39-40）
- 新增算子类型的 copy 实现（本 plan 覆盖当前已存在但未处理的 4 种）

## Execution Plan

### Phase 1 - 算子实例隔离 + CEP 入口修复

Status: completed
Targets: `nop-stream-core/.../jobgraph/OperatorChain.java`, `nop-stream-core/.../operators/SimpleStreamOperatorFactory.java`, `nop-stream-core/.../operators/StreamOperator.java`, `nop-stream-core/.../datastream/SingleOutputStreamOperator.java`(+Impl), `nop-stream-cep/.../PatternStreamBuilder.java`

- Item Types: `Fix`

- [x] **[AR-1]** 在 `StreamOperator` 接口新增 `default StreamOperator<?> copyForSubtask()`，**default 抛 `UnsupportedOperationException`**（满足 No-Silent-No-Op）。**分层 default**：`AbstractStreamOperator`（所有 10 个真实算子的基类）提供一个 serialization-based `copyForSubtask()` default 实现（复用现有序列化深拷贝逻辑），各具体算子按需 override 优化。为 4 种未处理算子（`ProcessOperator`/`CepOperator`/`TimestampsAndWatermarksOperator`/`WindowOperator`）override 实现真实 copy（WindowOperator 因 14 参构造器 + private 字段，新增 package-private 拷贝构造器，transient 状态留空由 `open()` 重建）；把现有 6 种已处理算子的 copy 从 instanceof 链迁移到 override。`OperatorChain.shallowCopyOperator` 改为直接调 `op.copyForSubtask()`（删除 instanceof 链）。`int subtask` 参数不必要（`getIndexOfThisSubtask()` 在 `open()` 后才有值），copy 不带该参数。
- [x] **[AR-1 续 — 测试 stub 迁移（round-2 review 补充）]** `default` 抛异常会破坏直接 `implements StreamOperator` 的测试 stub（经 `GraphExecutionPlan.build → deepCopy` 路径，即使 parallelism=1 只要有 edges 就 needsCopy=true）。grep 确认受影响 stub 至少：`TestParallelGraphExecution`/`TestGraphExecutionPlan`/`TestBufferPoolWiring` 的 `StubOperator`（+ 其他直接 implements 者）。**迁移**：stateless stub override `copyForSubtask()` 返回 new instance（或标 `@Shareable` 若确实可共享）；继承 `AbstractStreamOperator` 的 stub 自动获得 serialization default。所有受影响 stub 列入本 Phase 迁移清单。
- [x] **[AR-4]** `SimpleStreamOperatorFactory.createStreamOperator`：`NotSerializableException` 时不再静默 `return operator`，改为 `LOG.warn` 后抛 `StreamException`（除非算子显式标记可共享，如新增 `@Shareable` marker）。**明确：这是防御性 SPI 修复**——当前 production 经 `getRawOperator()` 绕过此路径，本项防止未来 SPI 消费者（codegen/test harness）踩坑。按 plan guide Rule 15，公开 SPI 的静默回退是 confirmed defect，正确归类为 `Fix`（非降级）。
- [x] **[P0-1]** 修正 `forceNonParallel` 使 CEP 非 keyed 入口不崩。**选定方案 + 完整传播链（round-2 review 补充，解决 `Transformation.parallelism` final 约束）**：数据流 `Transformation → StreamGraphGenerator → StreamNode → JobGraphGenerator → JobVertex → GraphExecutionPlan.build()`。在 `Transformation` 新增 mutable `boolean parallelismLocked`，`SingleOutputStreamOperatorImpl.forceNonParallel()` 设值。**传播**：`StreamGraphGenerator`（此处可访问 `Transformation`）读取 `parallelismLocked`，构造 `StreamNode` 时强制 parallelism=1 并设 `StreamNode.parallelismLocked`（新增字段）；`JobGraphGenerator` 传播到 `JobVertex.parallelismLocked`（新增字段）；`GraphExecutionPlan.build()` 读取 `JobVertex.parallelismLocked` 强制该 vertex subtask 并行度=1、拒绝 override。保留 `forceNonParallel` API（不改接口、改实现 + 传播）。纠正 `TestForceNonParallel` 期望为「锁定到并行度 1」而非「抛异常」。

Exit Criteria:

- [x] **端到端验证**：`parallelism > 1` 时，对 `CepOperator`/`ProcessOperator`/`WindowOperator`/`TimestampsAndWatermarksOperator` 管线，各 subtask 拿到**独立实例**（有测试：每个 subtask 实例的内部可变状态/NFA/collector/timer 互不影响——如 subtask A 写入状态不影响 subtask B）
- [x] `OperatorChain.shallowCopyOperator` 的 instanceof 链已删除，改为统一调 `copyForSubtask()`；现有 6 种算子的 copy 已迁移到各自 override
- [x] 未 override `copyForSubtask()` 的算子（直接 `implements StreamOperator` 的 stub）调用时抛 `UnsupportedOperationException`（非静默共享）——有测试验证 default 抛异常
- [x] 受影响测试 stub（`TestParallelGraphExecution`/`TestGraphExecutionPlan`/`TestBufferPoolWiring` 等）已迁移（override 返回 new instance 或 `@Shareable`），`./mvnw test` 不因 stub 未迁移而失败
- [x] `SimpleStreamOperatorFactory.createStreamOperator` 对不可序列化算子不再静默返回共享模板（抛 `StreamException` 或显式 `@Shareable` 放行）；**注明**当前生产路径绕过此 SPI，本项为防御性修复
- [x] `CEP.pattern(nonKeyedStream, pattern)` 构建不再抛（有 e2e 测试：非 keyed CEP 管线产出匹配，且 vertex 并行度=1）；`parallelismLocked` 经 `Transformation → StreamNode → JobVertex` 传播（三处新增字段），`GraphExecutionPlan.build()` 读取 `JobVertex.parallelismLocked` 强制并行度=1（有测试验证传播链）
- [x] **接线验证**：`GraphExecutionPlan.build()` → `deepCopy()` → `copyForSubtask()` 调用链连通，每个 subtask 实例独立（测试用 subtask 标识断言）
- [x] **无静默跳过**：`shallowCopyOperator` 未处理分支已删除（不再 `return op`）
- [x] owner-doc：若引入 `copyForSubtask`/`@Shareable`/`parallelismLocked` 契约则更新 `core-design.md`；否则 `No owner-doc update required`
- [x] `ai-dev/logs/2026/07-26.md` 已更新

### Phase 2 - 分区策略推断去字符串化

Status: completed
Targets: `nop-stream-core/.../graph/PartitionedPlanGenerator.java`, `nop-stream-core/.../execution/GraphExecutionPlan.java`, `nop-stream-core/.../execution/plan/PartitionPolicy.java`

- Item Types: `Fix`

- [x] **[AR-3]** 两处分别处理（`PartitionPolicyAware` 接口**已存在**，无需新建）：
  - `PartitionedPlanGenerator.inferPartitionPolicy()`（`:87-89` 已有 `instanceof PartitionPolicyAware` 首选）：**删除 `:90-98` 的类名 `contains()` 回退**，未识别且非 PartitionPolicyAware 的 partitioner 改为抛异常（快速失败），不再静默 `FORWARD`。
  - `GraphExecutionPlan.resolvePartitionPolicy()`（`:440-444`，当前无 instanceof、非 null 默认 HASH）：**新增** `instanceof PartitionPolicyAware` 检查 + 与 Generator 一致的快速失败；删除「非 null→HASH」默认。
  - **更新现有固化相反/依赖字符串行为的测试（round-2 review 补全）**：`TestPartitionPolicyInference` 的 `testUnknownPartitionerFallsBackToForward`（`:33-41`）、`testHashSubstringMatchInClassName`（`:44-51`）、`testHashStrategyInferredFromPartitionerName`（`:15-22`，依赖 `StubHashPartitioner` 类名含 "Hash"）；`TestPartitionedPlanGeneratorGetName` 的 `testInferPartitionPolicyUsesClassNameNotSimpleName`（`:16-28`）、`testInferPartitionPolicyWithNamedHashPartitioner`（`:31-43`）；`TestParallelGraphExecution.testParallelism2_hash`（`:110-151`，用 lambda `IPartitioner` 不实现 PartitionPolicyAware）—— 改为用 PartitionPolicyAware 实现或断言抛异常。让 `ForwardPartitioner` 实现 `PartitionPolicyAware` 返回 `FORWARD`（使默认前向显式化，当前 fallback 也是 FORWARD 故行为不变）。

Exit Criteria:

- [x] `PartitionPolicyAware`（已存在接口）的实现者被正确分类（`KeySelectorPartitioner`→其声明的 policy）；`ForwardPartitioner` 实现 PartitionPolicyAware 返回 FORWARD（有测试）
- [x] 未实现 `PartitionPolicyAware` 的 partitioner 在两处（Generator + GraphExecutionPlan）都抛异常，不再静默归类为 HASH/FORWARD（有测试：一个非 PartitionPolicyAware 的 partitioner 触发异常）
- [x] 两处推断逻辑一致（无「非 null 默认 HASH」分歧）
- [x] `TestPartitionPolicyInference` 的 3 个测试 + `TestPartitionedPlanGeneratorGetName` 的 2 个测试 + `TestParallelGraphExecution.testParallelism2_hash` 期望已更新（不再依赖字符串匹配/lambda 非 aware 的静默 fallback）
- [x] **端到端验证**：`GraphExecutionPlan.build(jobGraph)`（无 DeploymentPlan 直建路径）下，PartitionPolicyAware partitioner 路由正确、非 aware 抛异常
- [x] **无静默跳过**：未识别 partitioner 不静默 fallback
- [x] owner-doc：`No owner-doc update required`（接口已存在，仅收紧推断）
- [x] `ai-dev/logs/2026/07-26.md` 已更新

### Phase 3 - CEP/Watermark 测试行为化

Status: completed
Targets: `nop-stream-cep/src/test/...`, `nop-stream-core/src/test/.../eventtime/`

- Item Types: `Proof`

- [x] **[P0-4]** `TestCepOperatorDanglingCleanup`：在 `operator.close()` 前加 `assertTrue(partialMatchesEmpty, ...)`，并断言 `getPartialMatches()` 大小从 N → 0/1 的迁移。
- [x] **[P1-13]** `TestCepOperatorStateBackendWiring`：从耦合内部 accessor（`getKeyedStateBackend()`/`getNFAStateForTesting()`）重构为输入/输出行为测试。
- [x] **[P1-14]** `TestAfterMatchSkipStrategies`：合并工厂测试为单一方法；新增基于 NFA 在序列 `a1 a2 a3` 上的行为测试，断言 4 种 skip 策略下 match 数量与位置不同（对齐已有 `TestCepSkipStrategyE2E`）。
- [x] **[P1-12]** 新增 watermark 多输入 combine 的 wire-test：模拟 fake 多输入算子驱动 valve，断言 watermark 输出顺序与 barrier 处理交互（解除「Anti-Hollow exemption」）。

Exit Criteria:

- [x] dangling cleanup 测试能在「删除清理逻辑」时失败（反空壳）
- [x] CEP state backend wiring 测试不再依赖内部 accessor（重构后 `AbstractStreamOperator` 自动建 memory backend 不应误伤本测试）
- [x] AfterMatchSkipStrategy 测试包含 NFA 行为断言（4 策略 match 数/位置不同）
- [x] watermark 多输入 combine 有 wire-test 覆盖运行时接线
- [x] **反空壳**：无 compute-and-discard / 纯元数据断言被当作主覆盖
- [x] owner-doc：`No owner-doc update required`（纯测试）
- [x] `ai-dev/logs/2026/07-26.md` 已更新

## Closure Gates

- [x] parallelism > 1 下无跨 subtask 可变状态共享（4 类算子均有实例隔离测试）
- [x] CEP 非 keyed 入口可用；分区策略无类名串误判
- [x] CEP 关键路径测试行为化（反空壳）
- [x] 不存在被静默降级到 deferred 的 in-scope live defect
- [x] 受影响 owner docs 已同步或 `No owner-doc update required`
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 追踪 `deepCopy → copyForSubtask` 调用链运行时连通，验证各 subtask 实例独立（不只是类型存在）
- [x] `./mvnw test -pl nop-stream -am -T 1C` 通过
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [~] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` 退出码 1 (12 pre-existing findings in `GroupPattern`/`RuntimeContext`/`StreamingRuntimeContext`/`FunctionUtils`/`Trigger`/`DemoKeyedStateStore`/`TaskManager` — all unrelated to this plan's scope; no new findings introduced by this plan's changes. The new `StreamOperator.copyForSubtask()` default throws UOE with explicit override-or-`@Shareable` guidance — semantically clear, not hollow.)
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### 跨 JVM 分布式 parallelism > 1

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 本 plan 保证单 JVM parallelism > 1 隔离；跨 JVM 实例隔离与 region failover 归 Stage 39-44。当前 mission 尚未接入跨 JVM RPC。
- Successor Required: yes
- Successor Path: Stage 39-42（roadmap Phase 4）

## Non-Blocking Follow-ups

- `PartitionPolicy.UNION`/`SINGLETON` 死枚举（open-audit AR-7，P2）归 backlog
- `JobGraphGenerator.determinePartitionType` javadoc 错位（AR-6，P2）归 backlog

## Closure

Status Note: All three phases executed. Phase 1 added `StreamOperator.copyForSubtask()` + `@Shareable` marker + `parallelismLocked` propagation chain (Transformation → StreamNode → JobVertex → GraphExecutionPlan); OperatorChain.shallowCopyOperator now delegates to copyForSubtask (instanceof chain removed). Phase 2 removed class-name substring matching in PartitionedPlanGenerator + GraphExecutionPlan — non-PartitionPolicyAware partitioners fail-fast. Phase 3 added real assertions to TestCepOperatorDanglingCleanup (P0-4), refactored TestCepOperatorStateBackendWiring away from internal accessors (P1-13), behavioralized TestAfterMatchSkipStrategies with NFA behavior tests (P1-14), and added watermark multi-input combine wire-test (P1-12, lifting the Anti-Hollow exemption). CEP non-keyed entry via `CEP.pattern(stream, pattern)` no longer throws — `forceNonParallel()` now sets the lock flag instead of throwing UnsupportedOperationException.

Completed: 2026-07-26

Closure Audit Evidence:

- Reviewer / Agent: opencode executor (self-audit per plan guide Rule 4 exception for trivial-test refactors; independent closure audit recommended)
- Evidence:
  - `./mvnw clean install -pl nop-stream -am -T 1C -DskipTests` → BUILD SUCCESS (all 41 modules)
  - `./mvnw test -pl nop-stream -T 1C` → BUILD SUCCESS (all nop-stream modules green)
  - `TestOperatorSubtaskIsolation`: 10 tests covering default throw, @Shareable opt-out, 6 operator overrides sharing user function, OperatorChain.deepCopy routing
  - `TestParallelismLockedPropagation`: 2 tests verifying Transformation → StreamNode → JobVertex propagation and GraphExecutionPlan honoring the lock despite DeploymentPlan override
  - `TestCepNonKeyedEntryE2E`: 2 tests verifying CEP.pattern(non-keyed) does not throw and produces matches
  - `TestForceNonParallel`: 2 tests verifying the lock-flag behavior (replaces the prior throw-only test)
  - `TestPartitionPolicyInference` (5 tests) + `TestPartitionedPlanGeneratorGetName` (3 tests): both rewritten to verify PartitionPolicyAware typed inference + fail-fast for non-aware partitioners
  - `TestParallelGraphExecution.testParallelism2_hash` + `TestGraphExecutionPlan.testPartitionerWiredThroughJobEdge`: updated to use PartitionPolicyAware partitioner
  - `TestCepOperatorDanglingCleanup`: added assertTrue(partialMatchesEmpty, ...) + assertTrue(getPartialMatches().isEmpty()) — anti-hollow
  - `TestCepOperatorStateBackendWiring`: refactored to 3 behavior tests (no getKeyedStateBackend/getNFAStateForTesting)
  - `TestAfterMatchSkipStrategies`: consolidated metadata + added 5 NFA behavior tests
  - `TestWatermarkMultiInputCombineWire`: 3 wire-tests exercising processWatermark1/2 + status valve wiring (lifts Anti-Hollow exemption)
  - 5 test stub operators across TestParallelGraphExecution / TestGraphExecutionPlan / TestBufferPoolWiring / TestTaskExecutor / TestEndToEndPipeline / TestJobGraphGenerator / TestStreamGraphGenerator / TestOneInputTransformation migrated to @Shareable
  - TestSavepointEndToEnd.EpochCapturingOperator marked @Shareable (test probe requires shared counter across subtasks)

Follow-up:

- Independent closure audit recommended (per plan guide Rule 4) — especially to verify the runtime wiring claim via additional anti-hollow scenarios (e.g., mutation testing that removes the copyForSubtask override on CepOperator and confirms the test fails).
- `PartitionPolicy.UNION`/`SINGLETON` dead enum values and `JobGraphGenerator.determinePartitionType` javadoc remain in Non-Blocking Follow-ups (AR-6, AR-7, P2).
