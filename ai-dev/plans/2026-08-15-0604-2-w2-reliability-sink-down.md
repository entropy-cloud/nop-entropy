# W2 LLM 可靠性子集下沉 nop-ai-core

> Plan Status: active
> Last Reviewed: 2026-08-15
> Source: `ai-dev/backlog/nop-ai-gateway-failover-roadmap.md`（W2）、`ai-dev/design/nop-ai-gateway/02-account-failover-requirement.md`（§4.1/§五 Q11）
> Related: `ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md`、`ai-dev/design/nop-ai-agent/nop-ai-llm-error-normalization-design.md`
> Mission: nop-ai-gateway-failover
> Work Item: W2

## Purpose

把 LLM 可靠性子集（账号链/熔断/错误分类/重试策略/provider 链，共 18 个类）从 nop-ai-agent 迁移到 nop-ai-core，使网关能力级（W5/W6/W7）不依赖 nop-ai-agent。同步迁移 `NopAiAgentException`/`NopAiAgentErrors` → nop-ai-core 等价物与 `buildModelKey`，适配 nop-ai-agent 既有调用方，迁移既有测试，更新 `nop-ai-agent-reliability.md` 模块归属。

## Current Baseline

- 18 个待迁类全部位于 `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/reliability/`：`ThresholdBreaker` / `ICircuitBreaker` / `CircuitState` / `AccountChain` / `IAccountChainResolver` / `ProviderFailoverChain` / `ProviderFailoverQueue` / `IProviderFailoverQueue` / `IProviderFailoverChainResolver` / `LlmErrorClassifier` / `IRetryPolicy` / `StandardRetryPolicy` / `NoRetryPolicy` / `RetryContext` / `RetryDecision` / `RetryOutcome` / `AlwaysClosed` / `NoOpProviderFailoverQueue`（live ls 核实）。
- 其中 5 类 import `io.nop.ai.agent.NopAiAgentErrors` / `io.nop.ai.agent.engine.NopAiAgentException`：`ThresholdBreaker`、`ProviderFailoverQueue`、`StandardRetryPolicy`、`RetryContext`、`RetryOutcome`（live grep 核实，仅此 5 类）。18 类整体 import 面干净：仅 `io.nop.ai.core.model.*`、`io.nop.ai.api.chat.*`、`io.nop.api.core.*`、java.util + 上述 agent 异常——**无 agent 引擎类型依赖，迁移可行**（reviewer 实证）。
- `NopAiAgentErrors` 位于 `io.nop.ai.agent.NopAiAgentErrors`，共 **31 个错误码 + 8 个 ARG_ 常量**；被迁移类使用的只有 `ERR_AI_AGENT_INVALID_ARG` + `ARG_MSG`；main 代码提及该类的文件 ~44（import ~41，使用 `NopAiAgentErrors.` 常量 43）。`NopAiAgentException` 位于 `io.nop.ai.agent.engine`，`extends NopException`，构造器覆盖 (String)/(String, Throwable)/(ErrorCode)/(ErrorCode, Throwable)；main 代码提及 ~151 文件、显式 import ~115 文件。
- `buildModelKey` 是 `LlmCallCoordinator.java:583` 的 `public static String buildModelKey(ChatOptions)`（`provider + ":" + model`）；调用方：`LlmCallCoordinator`（~10 处）、`ReActAgentExecutor.java:613`（实例限定）、`ThresholdBreaker`/`ICircuitBreaker`（javadoc 引用）、**留守测试 `TestEngineExtractedCoordinators.java:182-187`（2 处静态断言调用）**。`LlmCallCoordinator.buildModelKey` 原方法去留需裁定（删除 or 委托新位置）。
- **留驻类反向引用已迁类（8 个 main 类）**：`IGoalTracker`/`ISustainer`→`ICircuitBreaker`；`IterationSnapshot`/`SustainContext`→`RetryContext`；`NoOpGoalTracker`/`NoOpSustainer`→`AlwaysClosed`+`NoRetryPolicy`；`SessionGoalTracker`→`ThresholdBreaker`；`SisypheanSustainer`→`StandardRetryPolicy`+`ThresholdBreaker`——同包引用现无 import，迁移后**必须补 import**（live grep 实证）。
- **跨模块反射契约**：`nop-task/nop-task-dao/.../TaskExceptionRegistry.java:69` 以字符串注册 `"io.nop.ai.agent.engine.NopAiAgentException"`（分层约束下反射注册，javadoc 明确记录意图），`nop-task-ext` 测试断言 FQCN——若删除该类将导致注册串失效（任务异常精确重建能力静默降级）。本 plan 的"异常类保留"裁定（见 Phase 1）使该契约不受影响。
- nop-ai-core 已有 `NopAiCoreErrors`（`io.nop.ai.core.NopAiCoreErrors`，40 码 + ARG 常量），**无模块内 exception 类**（`find *Exception.java` 零结果）；`LlmErrorClassifier` 已使用 `NopAiCoreErrors`（`NopException` 直接抛出）——先例成立。
- `AccountChain` 已依赖 `io.nop.ai.core.model.LlmAccountModel`（nop-ai-core 类型）；`LlmErrorClassifier` 依赖 `io.nop.ai.api.chat.ErrorClassification`。
- 装配面无 beans.xml 注册：nop-ai-agent `_vfs` 下无 reliability 类 bean 注册（grep 仅命中 app.orm.xml 无关项）；装配为构造器/字段注入（`DefaultAgentEngineConfig.java:128-129`、`ReActAgentExecutorBuilder.java:655-656`、`ReActAgentExecutor.java:288-289` `new StandardRetryPolicy()` / `new ThresholdBreaker()`）。
- 测试现状：nop-ai-agent 测试包 `io.nop.ai.agent.reliability` 含 31 个测试类，其中纯单元测试（`TestThresholdBreaker` / `TestStandardRetryPolicy` / `TestNoRetryPolicy` / `TestAlwaysClosed` / `TestLlmErrorClassifier` / `TestProviderFailoverQueue` 等）与被迁移类耦合（随迁 nop-ai-core）；装配/端到端测试（`TestRetryPolicyWiring` / `TestCircuitBreakerWiring` / `TestCircuitAwareRouting` / `TestLlmCallCoordinatorErrorResponse` / `TestThresholdBreakerEndToEnd` / `TestStandardRetryPolicyEndToEnd` 等）留在 nop-ai-agent 但需 import 更新。**随迁测试断言含异常类型**（`TestThresholdBreaker` 8 处 `assertThrows(NopAiAgentException.class)` + `ERR_AI_AGENT_INVALID_ARG`、`TestProviderFailoverQueue` 5 处、`TestStandardRetryPolicy` 3 处、`TestNoRetryPolicy.java:51` `new NopAiAgentException(...)` 输入）——迁移后必须改为 `NopAiCoreException` + core 码（此为预期改动；grep 实证 agent 引擎无 `catch (NopAiAgentException)` 依赖已迁类抛出的异常，适配安全）。
- **reliability 包外引用已迁类的测试仅 5 个**：`TestAccountFallbackChain`（断言的是留守 `LlmCallCoordinator` fail-loud 异常，异常由留守代码抛出，迁移后仍成立，仅补 import）、`TestEngineExtractedCoordinators`（含 `buildModelKey` 静态调用，见上）、`TestExecutionMiddlewareLlmRetry`、`TestProviderFailoverChain`、`TestSmartModelRouterFallback`（均补 import）。`TestCheckpointDispatchPathWiring` 仅 import 留守类，**不需要改动**。
- nop-ai-agent pom 依赖 nop-ai-core（既有方向，下沉不改变模块依赖方向）；nop-ai-core 不依赖 nop-ai-agent。

## Goals

- 18 个可靠性类迁移至 nop-ai-core（包名 `io.nop.ai.core.reliability`，Phase 1 裁定确认），nop-ai-core 对 nop-ai-agent 零依赖保持。
- 新增 `NopAiCoreException`（nop-ai-core 等价异常）；可靠性子集使用的错误码（`ERR_AI_AGENT_INVALID_ARG` + `ARG_MSG`）并入 `NopAiCoreErrors`。**裁定（见 Phase 1）：`NopAiAgentErrors`/`NopAiAgentException` 保留在 nop-ai-agent**（31 码中 30 码为 agent 专用；agent 内部 115+ 调用方继续使用）——迁移后 5 个已迁类不再引用任何 nop-ai-agent 类型。
- `buildModelKey` 移入 nop-ai-core（落点 Phase 1 裁定），`LlmCallCoordinator`/`ReActAgentExecutor` 调用方改为调用新位置。
- 既有测试迁移 + 双模块（nop-ai-core / nop-ai-agent）全量回归零失败；行为零变更（迁移是纯机械移动 + import 更新 + 异常类替换，不修改语义）。
- `ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md` 模块归属同步；agent 引擎专用部分（`Checkpoint*` / `GoalTracker` / `Sustainer` / `WaitCoordinator` / `CompactionAwareTruncation` / `LlmCallCoordinator`）明确标注留在 nop-ai-agent。

## Non-Goals

- 不迁移 agent 引擎专用可靠性类（`Checkpoint*` / `IGoalTracker` / `SessionGoalTracker` / `NoOpGoalTracker` / `GoalAssessment` / `IterationSnapshot` / `ISustainer` / `Sustainer*` / `SustainContext` / `SustainDecision` / `SustainStopReason` / `IWaitCoordinator` / `Wait*` / `CompactionAwareTruncation` / checkpoint journal/snapshot 系列 / `ICheckpointManager` / `NoOpCheckpoint` / `ToolExecutionCheckpoint` / `AiAgentCheckpointTable` 等）。
- 不迁移 `LlmCallCoordinator`（强耦合 agent 引擎，语义沿用）。
- 不实现网关新能力（W5-W7）。
- 不改变任何可靠性类的行为语义（含错误码文本、阈值默认值、重试策略细节）。

## Scope

### In Scope

- SINK-01: 18 类迁移（含伴生 import 修正、包名裁定）。
- SINK-02: `NopAiAgentException`/`NopAiAgentErrors` → nop-ai-core 等价物（core exception 类 + 错误码归属裁定与落地）；`buildModelKey` 随迁。
- SINK-03: nop-ai-agent 调用方适配（18 个已迁类 import 更新 + 被迁移 2 项错误码在 agent 侧的使用点更新——main 约 29-30 文件，Phase 1 精确清单）+ 既有测试迁移（纯单元测试随迁 nop-ai-core，装配/E2E 测试留原处并更新引用）+ 双模块全量回归。
- SINK-04: `nop-ai-agent-reliability.md` 模块归属同步（agent 引擎专用部分留原处的明确边界标注）+ roadmap W2 状态同步。

### Out Of Scope

- W1/W3-W8 工作。
- 可靠性类语义调整（阈值/冷却/重试细节/错误码文本的**内容**不变，仅位置与命名空间变化）。
- nop-ai-gateway 依赖改动（既有 channel 依赖维持）。

## Execution Plan

### Phase 1 - 影响面盘点与裁定（SINK-02 前置裁决）

Status: planned
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/reliability/`、`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/NopAiAgentErrors.java`、`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/NopAiAgentException.java`、`nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/`（只读盘点）

- Item Types: `Decision | Proof`

- [ ] 逐类盘点：对 18 个待迁类逐一核对 (a) 包内互依赖、(b) 对外部模块/agent 包依赖（除 5 类已知 agent 异常引用外有无其他 agent 引擎依赖）、(c) 留驻类（Checkpoint*/GoalTracker/Sustainer/WaitCoordinator/CompactionAwareTruncation 等）反向依赖待迁类清单（已预知 8 个 main 类，见 Baseline）——裁定处理方式（补 import 即可，方向合法）。
- [ ] 包名裁定：`io.nop.ai.core.reliability`（与 `io.nop.ai.agent.reliability` 同构），记录裁定理由。
- [ ] **异常/错误码迁移策略裁定（预裁定，执行时复核确认）**：新增 `io.nop.ai.core.NopAiCoreException extends NopException`（构造器对齐 `NopAiAgentException` 四件套）；`NopAiAgentErrors` 中被迁移类使用的**仅 2 项**（`ERR_AI_AGENT_INVALID_ARG` + `ARG_MSG`）并入 `NopAiCoreErrors`；**`NopAiAgentErrors`（其余 30 码 + 7 ARG 常量）与 `NopAiAgentException` 保留在 nop-ai-agent**（30 码为 agent 专用，agent 内部 115+ 调用方继续使用）。理由：a) 可靠性子集依赖消除是硬约束，全量删除 agent 异常类不是；b) 全部 31 码迁入 core 造成 agent 专用码污染核心层；c) 保留 `NopAiAgentException` 同时保住 `nop-task-dao:69` 反射注册串契约。此裁定与需求文档 SINK-02 字面措辞的关系在 Phase 4 记录（等价物已建立，agent 类保留的偏差说明）。
- [ ] 错误码迁移映射表（含 **ARG_*** 常量）：被迁移 2 项 → core 等价码；**错误码 ID 保持原样**（`nop.err.ai.agent.invalid-arg` 是持久化/日志错误码 ID，重命名即行为变更——Phase 1 直接裁定 ID 保留，仅换持有类）；`NopAiAgentErrors` 剩余码清单确认（30 码 + 7 ARG 常量）；grep 实证被迁移 2 项在 agent 侧的使用点清单（main ~34 文件 + 留驻类 `SisypheanSustainer`/`SessionGoalTracker`/`SustainContext`/`IterationSnapshot` 各含 2-4 处，精确计数）。
- [ ] `buildModelKey` 落点裁定：新增 `io.nop.ai.core.reliability.ModelKeys`（或同类小工具类，final + private 构造器 + static 方法，语义与 `LlmCallCoordinator.buildModelKey` 逐字一致）；**`LlmCallCoordinator.buildModelKey` 原方法去留裁定（推荐：删除，调用方含留守测试 `TestEngineExtractedCoordinators:182-187` 全部改引用新位置；或保留委托——二选一落档）**；`LlmCallCoordinator`/`ReActAgentExecutor` 调用方与 `ICircuitBreaker`/`ThresholdBreaker` javadoc 引用同步。
- [ ] 跨模块反射契约核实（Proof）：`nop-task-dao TaskExceptionRegistry.java:69` 注册串 `"io.nop.ai.agent.engine.NopAiAgentException"` 在"异常类保留"裁定下持续有效（无需改动）；`nop-task-ext TestTaskExceptionRegistry` 断言不变——结论记录。
- [ ] 测试迁移裁定：逐测试类裁定"随迁 nop-ai-core / 留 nop-ai-agent（更新 import）"，依据 = 是否直接测试被迁移类语义（随迁）vs 测试 agent 装配/端到端链路（留守）；reliability 包外引用已迁类的 ~16 个测试文件纳入"留 nop-ai-agent + 补 import"清单。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 盘点结果落档：18 类依赖矩阵 + 留驻类反向依赖结论（8 个 main 类清单核对，无隐藏依赖）。
- [ ] 包名/异常策略（含保留裁定）/错误码映射（含 ARG_*）/buildModelKey 落点/测试迁移五项裁定已写入 plan 或 design 文档，无未决项。
- [ ] 错误码映射表完整覆盖被迁移错误码（含 ARG_* 常量），grep 实证使用点清单精确。
- [ ] nop-task 反射契约核实结论记录（注册串持续有效）。
- [ ] 测试迁移清单完整（reliability 包内 31 类 + 包外 ~16 类逐项归类）。
- [ ] 本 Phase 为纯文档裁定，`No owner-doc update required`（design 文档同步在 Phase 4）。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 代码迁移（SINK-01 + SINK-02）

Status: planned
Targets: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/reliability/**`（新建）、`nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/NopAiCoreException.java`（新建）、`nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/NopAiCoreErrors.java`（扩展）

- Item Types: `Fix`

- [ ] 18 类机械迁移至 `io.nop.ai.core.reliability`（package 声明 + import 更新；git mv 保历史）。
- [ ] `NopAiCoreException` 新建（构造器对齐 `NopAiAgentException` 四件套）；`NopAiCoreErrors` 并入 `ERR_AI_AGENT_INVALID_ARG` + `ARG_MSG` 的 core 等价码（按 Phase 1 映射表）。
- [ ] 迁移类内 5 类共 16 处 `NopAiAgentException`/`NopAiAgentErrors` 使用点（ThresholdBreaker 6 / ProviderFailoverQueue 3 / StandardRetryPolicy 3 / RetryContext 2 / RetryOutcome 2）替换为 `NopAiCoreException` + core 错误码。
- [ ] **留驻类同步**：8 个反向引用已迁类的 main 留驻类补 `io.nop.ai.core.reliability.*` import（实测 `{@link}` javadoc 引用 7 处——`IGoalTracker`/`ISustainer`×2/`IterationSnapshot`/`SustainContext`/`NoOpSustainer`——必须补以保链接；`{@code}` 文本引用 6 处——`NoOpGoalTracker`×2/`SessionGoalTracker`/`SisypheanSustainer`×3——建议同步）；其中 4 个留驻类（`SisypheanSustainer`/`SessionGoalTracker`/`SustainContext`/`IterationSnapshot`）的 `ERR_AI_AGENT_INVALID_ARG`+`ARG_MSG` 使用点改为 core 码（异常类可继续用 `NopAiAgentException`）。
- [ ] `buildModelKey` 移入 `io.nop.ai.core.reliability.ModelKeys`（Phase 1 裁定落点），`LlmCallCoordinator`/`ReActAgentExecutor` 调用方**按 Phase 1 裁定执行**（裁定删除时：调用方 + 留守测试 `TestEngineExtractedCoordinators:182-187` 改引用新位置；裁定委托时：调用方零改动，仅新位置就位）；`ICircuitBreaker`/`ThresholdBreaker` javadoc 引用同步。
- [ ] `NopAiAgentErrors` 中被迁移的 2 项移除（其余 30 码 + 7 ARG 常量保留），agent 侧使用点（~35 文件精确清单）更新为 core 等价码；`NopAiAgentException` 保留不动（agent 内部继续使用）。
- [ ] nop-ai-agent 对 18 个已迁类的 import 全量更新（`DefaultAgentEngineConfig` / `ReActAgentExecutorBuilder` / `ReActAgentExecutor` / `LlmCallCoordinator` 等 + 其他引用方）。

Exit Criteria:

- [ ] `./mvnw compile -pl :nop-ai-core,:nop-ai-agent -am` 通过（先于测试运行）。
- [ ] grep 实证：nop-ai-agent 源码对 `io.nop.ai.agent.reliability` 已迁类的引用零残留（排除测试，测试在 Phase 3 处理）；`NopAiAgentErrors` 中被迁移 2 项零残留。
- [ ] nop-ai-core 对 `io.nop.ai.agent` 的 import 零残留（模块方向纪律，grep 实证）。
- [ ] 迁移类行为语义零变更（git diff 仅 package/import/异常类型行，抽查代表类：`ThresholdBreaker`/`AccountChain`/`StandardRetryPolicy`）。
- [ ] **无静默跳过**（Minimum Rules #24）：迁移后无新空方法体/无 swallow；`buildModelKey` 新位置被真实调用（grep 调用点）。
- [ ] `No owner-doc update required`（文档同步在 Phase 4）。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - 测试迁移 + 全量回归（SINK-03）

Status: planned
Targets: `nop-ai/nop-ai-core/src/test/java/io/nop/ai/core/reliability/**`（随迁测试）、`nop-ai/nop-ai-agent/src/test/java/**`（留守测试：reliability 包内装配/E2E + 包外引用已迁类的 ~16 个文件）

- Item Types: `Fix | Proof`

- [ ] 按 Phase 1 清单迁移纯单元测试至 nop-ai-core（package/import 更新；**断言异常类型适配为预期改动**：`assertThrows(NopAiAgentException.class)` → `NopAiCoreException`、`ERR_AI_AGENT_INVALID_ARG` → core 码、`TestNoRetryPolicy.java:51` 的异常输入换类——**语义断言零改动**；随迁测试不得 import `io.nop.ai.agent.*`，违反则编译失败）。
- [ ] 留守 nop-ai-agent 的装配/E2E 测试更新 import/引用。
- [ ] reliability 包外引用已迁类的 5 个测试文件（`TestAccountFallbackChain` / `TestEngineExtractedCoordinators` / `TestExecutionMiddlewareLlmRetry` / `TestProviderFailoverChain` / `TestSmartModelRouterFallback`）补 import（`TestCheckpointDispatchPathWiring` 无需改动——仅 import 留守类）。
- [ ] 留守类单测（`TestSessionGoalTracker` / `TestSisypheanSustainer` / `TestDBCheckpointManager` / `TestFileBackedCheckpointManager` / `TestCheckpointJournalSnapshotFormat` 等）若引用已迁类则补 import（引用 `NopAiAgentException` 的无需改——异常类保留）。
- [ ] 双模块全量回归：`./mvnw test -pl :nop-ai-core,:nop-ai-agent -am -T 1C` 零失败；记录迁移前后测试计数（迁移前 nop-ai-agent 测试总数 vs 迁移后 core+agent 合计，证明无测试丢失）。
- [ ] 新公共类型测试（Minimum Rules #25）：`ModelKeys.buildModelKey` 随迁后必须有其语义测试（将 `TestEngineExtractedCoordinators:182-187` 的断言随迁/复制至 nop-ai-core 新位置，或等价的 `ModelKeys` 单元测试）；`NopAiCoreException` 构造器四件套由随迁测试断言覆盖（`assertThrows(NopAiCoreException.class)` 即验证）。
- [ ] 端到端链路保持：`TestThresholdBreakerEndToEnd`/`TestStandardRetryPolicyEndToEnd`（留守 agent）验证迁移后熔断/重试在 ReAct 执行链仍生效（Anti-Hollow：下沉类被 agent 引擎运行时消费，非仅类型存在；`TestThresholdBreakerEndToEnd` 仅断言 `getError()` 文本与 CircuitState，不依赖异常类型——兼容确认）。

Exit Criteria:

- [ ] `./mvnw test -pl :nop-ai-core,:nop-ai-agent -am -T 1C` BUILD SUCCESS，0 failures / 0 errors。
- [ ] 测试计数核对：迁移前（agent）+ 迁移后（core + agent）测试总数一致（或差异已逐项说明）。
- [ ] 端到端测试（留守 agent 的 EndToEnd 用例）全绿——证明下沉类在 agent 引擎链路仍被运行时调用。
- [ ] `No owner-doc update required`（文档同步在 Phase 4）。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 4 - 文档同步（SINK-04）

Status: planned
Targets: `ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md`、`ai-dev/design/nop-ai-agent/nop-ai-llm-error-normalization-design.md`（如涉）、`ai-dev/design/nop-ai-gateway/02-account-failover-requirement.md`（§四.1 归属表 + SINK-02 偏差说明）、`docs-for-ai/02-core-guides/error-handling.md`（核查）、`ai-dev/backlog/nop-ai-gateway-failover-roadmap.md`（W2 状态）

- Item Types: `Follow-up`

- [ ] `nop-ai-agent-reliability.md` 模块归属同步：可靠性子集（18 类 + `NopAiCoreException`/core 错误码 + `ModelKeys.buildModelKey`）标注归属 nop-ai-core；agent 引擎专用部分（Checkpoint*/GoalTracker/Sustainer/WaitCoordinator/CompactionAwareTruncation）明确标注留在 nop-ai-agent；`LlmCallCoordinator` 不迁移标注。
- [ ] 需求文档 §4.1 归属表核对；**SINK-02 偏差说明**：记录"`NopAiAgentErrors`/`NopAiAgentException` 保留在 agent（30 码 agent 专用 + nop-task 反射契约），core 等价物 = `NopAiCoreException` + 并入的 2 项错误码"裁定（与 roadmap 字面的偏差 + 理由）。
- [ ] `nop-ai-llm-error-normalization-design.md` 归属同步：`LlmErrorClassifier`/`IRetryPolicy` 与账号链消费的模块归属描述（文档范围声明行 + Layer 3 归属节）→ nop-ai-core。
- [ ] `docs-for-ai/02-core-guides/error-handling.md` 核查：`NopAiAgentErrors`（nop-ai-agent）与 `NopAiCoreErrors`（nop-ai-core）条目在异常类保留裁定下仍然准确；**被迁移英文码 `ERR_AI_AGENT_INVALID_ARG`（"invalid argument: {msg}"）并入 `NopAiCoreErrors` 后，该文档"英文转换码例外以模块为粒度"的分界表述需复核**——给出明确结论（更新例外归属表述或记录无需改动及理由）。
- [ ] roadmap W2 状态：`todo` → `planned`（draft review 通过时）→ `done`（独立 closure audit 通过后）。

Exit Criteria:

- [ ] 上述文档同步全部完成，grep 抽查无"reliability 子集属 nop-ai-agent"残留表述。
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] 18 类 + `NopAiCoreException`/core 错误码 + `ModelKeys.buildModelKey` 迁移完成，nop-ai-core 对 nop-ai-agent 零依赖保持（grep 实证）。
- [ ] 已迁类对 nop-ai-agent 零引用；`NopAiAgentErrors` 被迁移 2 项在 agent 侧零残留（grep 实证，含测试）。
- [ ] nop-task 反射契约核实：注册串 `"io.nop.ai.agent.engine.NopAiAgentException"` 持续有效（类保留），`nop-task-ext` 测试零改动零回归。
- [ ] 双模块全量回归零失败，测试计数无丢失。
- [ ] 行为语义零变更（git diff 抽查代表类仅机械差异）。
- [ ] `nop-ai-agent-reliability.md` 等 owner docs 已同步到 live baseline（含 SINK-02 偏差说明）。
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope 迁移残留项。
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据（audit 验证：迁移完整性 grep、模块依赖方向、行为零变更抽查、端到端链路运行时消费、nop-task 契约）。
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）下沉类被 nop-ai-agent 引擎在运行时确实调用（EndToEnd 测试绿 + 代码追踪），（b）无空方法体/静默跳过/no-op 作为正常实现。
- [ ] `./mvnw compile -pl :nop-ai-core,:nop-ai-agent -am`
- [ ] `./mvnw test -pl :nop-ai-core,:nop-ai-agent -am -T 1C`
- [ ] checkstyle：仓库根 pom 的 checkstyle 插件配置整体注释、`-Pqa` profile `failOnViolation=false`（违规不失败）——**无有效 checkstyle 门禁**，本 plan 以 compile/test 门禁 + grep 零残留作为代码规范验证（记录此裁定，不虚构门禁）。
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（关闭时执行）
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-core --severity high` 退出码 0（关闭时执行）

## Deferred But Adjudicated

### `NopAiAgentErrors`/`NopAiAgentException` 保留在 nop-ai-agent（非全量迁移）

- Classification: `watch-only residual`（已裁定：保留是迁移策略的一部分，非未完成项）
- Why Not Blocking Closure: 31 个错误码中 30 个为 agent 专用（filter/recipe/session/memory/hook 系列），迁入 core 是设计污染；`NopAiAgentException` 保留同时保住 `nop-task-dao:69` 反射注册串契约（若删除，任务异常精确重建能力将静默降级为 generic `NopException`）。可靠性子集"不依赖 nop-ai-agent"的硬约束通过"已迁类零 agent 引用"满足，与保留裁定不冲突。此裁定已在 Phase 1 落档并将在 Phase 4 写入需求文档偏差说明。
- Successor Required: `no`

## Non-Blocking Follow-ups

- 无（迁移类语义增强、性能优化等不属本 plan）。

## Closure

Status Note: （关闭时填写）
Completed: （关闭时填写）

Closure Audit Evidence:

- Reviewer / Agent: （关闭时填写）
- Evidence: （关闭时填写）

Follow-up:

- （关闭时填写）
