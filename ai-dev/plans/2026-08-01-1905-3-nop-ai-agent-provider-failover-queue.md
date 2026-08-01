# nop-ai-agent ProviderFailoverQueue 跨 provider 有序故障转移（W2-4）

> Plan Status: active
> Mission: nop-ai-agent-harness-evolution
> Work Item: W2-4（ProviderFailoverQueue：跨 provider 有序故障转移 P1→P2→P3 + failover_switch 去重；每 provider 独立熔断状态）
> Last Reviewed: 2026-08-01
> Draft Review: round-1 独立子 agent 审查发现 1 Blocker（ProviderFailoverQueue 状态模型矛盾：per-execution 游标 vs 跨调用去重）+ 3 Major（去重可测试性、provider 切换状态重置嵌套循环、Decision C 范围不可控）+ 5 Minor，全部已修。round-2 独立子 agent 审查 verdict READY FOR ACTIVE（Blocker + 3 Major 全部 RESOLVED，Live 行号精确，无新 Blocker/Major，残留 2 Minor 已顺手修）。共识达成。
> Source: `ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md` §13.4（`:729-738`，方向 only）；`ai-dev/backlog/nop-ai-agent-harness-evolution-roadmap.md` W2-4
> Related: 前置 plan `2026-08-01-1505-1`（W2e-5 账号回退链，已 completed，本计划是其显式 Deferred But Adjudicated successor——消费 W2e 产出的分类信号 + 同 provider 账号链做跨 provider 切换）；`ThresholdBreaker`（per `provider:model` 熔断，§13.4 欲升为 provider 维度）；cc-switch provider circuit breaker 调研

## Purpose

补齐跨 provider 故障转移能力。今日 `LlmCallCoordinator` 的 FALLBACK 有两通道——**同 provider 账号链**（QUOTA/AUTH→`AccountChain`，W2e-5）与**模型 tier 链**（TRANSIENT→`IModelRouter.getFallback`）——**无跨 provider 维度**。当某 provider 整体不可用（所有账号耗尽 / 该 provider 模型熔断全开），今日只能 fail-loud。本计划增加**第三通道**：跨 provider 有序故障转移 P1→P2→P3（每 provider 独立熔断状态 + `failover_switch` 去重防震荡），使 provider 级故障可恢复而非终止（design §13.4）。

## Current Baseline

> 已逐条核对 live repo（独立 explore 子 agent 报告 ses_04301709fffecDP52Z12gehqp8）。

**已落地（核对属实）**：

- `ThresholdBreaker`（`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/reliability/ThresholdBreaker.java`，233 行）：三态（CLOSED/OPEN/HALF_OPEN），**per `provider:model` 复合键**（`buildModelKey` 在 `LlmCallCoordinator.java:386-390`），`ConcurrentHashMap<String, BreakerEntry>`（`:71`）。默认阈值 3（`:65`）/冷却 60s（`:67`）。**仅内存、单实例**（持久化是 Non-Goal，`:59-60`）。implement `ICircuitBreaker`（`:110` 行接口）。
  - **§13.4 称"单 provider 维度"但 live 是 per `provider:model` 复合——比 provider 级更细**。provider 级健康状态今日**无直接来源**，需 roll-up 或新 tracker。
- 账号链（W2e-5，刚落地）：`AccountChain`（`.../reliability/AccountChain.java`，65 行，stateful per-execution walker）+ `IAccountChainResolver`（`:23`，`@FunctionalInterface`）+ `LlmConfigHelper.resolveAccountChain(provider)`（`nop-ai-core/.../service/LlmConfigHelper.java:158-168`）。**同 provider 内**多账号，`<accounts>` 排除主账号。
- `LlmCallCoordinator`（`.../engine/LlmCallCoordinator.java`，589 行）FALLBACK 路由（**两通道，无跨 provider**）：
  - 响应级（错误 ChatResponse）：`:165-198`——`QUOTA_EXCEEDED||AUTH_INVALID`→`doAccountSwitch`（账号链，`:174-176`）；else→`doModelTierFallback`（`:188-189`）。链耗尽 fail-loud（账号链 `:177-182`；模型 tier `:190-194`，`buildFallbackExhaustedError` `:343-357`，循环外抛 `:249-252`）。
  - 传输级（异常）：`:226-239`——恒模型 tier（QUOTA/AUTH 此路径不可达，分类器启发式从不产）。
- `IModelRouter.getFallback(ChatOptions)`（`.../router/IModelRouter.java:36`，default 返回 null）；`SmartModelRouter.getFallback`（`:122-152`）按 tier 序列降级。**模型 tier 内**回退，非跨 provider 健康感知队列。
- `ErrorClassification`（`nop-ai/nop-ai-api/src/main/java/io/nop/ai/api/chat/ErrorClassification.java:32-39`）6 值。**QUOTA/AUTH/CACHE_STATE_LOST 默认启发式不产**——需配置 `<errorMappings>` 才可达（`:28-30` 不变量）。

**架构事实（阻断项，已核实）**：

- **llm.xdef 是单 provider 作用域**（`nop-kernel/nop-xdefs/.../_vfs/nop/schema/ai/llm.xdef`，163 行）：根 `<llm>` 元素（`xdef:name="LlmModel"`，`:7-14`）一个 per `{provider}.llm.xml`。`<accounts>`（`:46-53`）是同 provider 账号链。**无 provider 列表、无 priority 属性、无 failover-chain 声明、无 manifest 聚合**。声明 P1→P2→P3 **无落点**——须新增 schema 概念。
- **无 ProviderFailoverQueue 类、无 provider priority/order 字段、无 failover_switch 去重**（全 `nop-ai/` grep 确认；唯一 `failover` 命中是 daemon lease failover，不相关）。
- **provider 切换的状态重置复杂度**（集成难点）：今日账号切换 `doAccountSwitch`（`LlmCallCoordinator.java:283-307`）只改 `accountKey`/`accountBaseUrl`——provider/model/config/dialect 不变。跨 provider 切换**根本不同**：切 provider 须改 `ChatOptions.provider`/`model`（`ChatServiceImpl.java:101` 从 `ChatOptions` 读 provider 并按 provider 重载 config/dialect，已支持）+ **重置 `accountChain`**（`:132` 单变量，新 provider 有自己的 `<accounts>`）+ **新 circuit-breaker 键**（`buildModelKey` `:386-390` 改变，`circuitBreaker.allowCall` `:111` 评估不同条目）+ 重置 attempt 计数。重试循环（`:133-247` while(true)）内含单 `accountChain` 变量（`:132`）——跨 provider 是**嵌套循环**（provider 循环含 account 链循环），plan 须明确此结构。
- **`resolveCircuitAware`（`:512-555`）已隐式提供部分跨 provider 能力**：它在重试前扫描 `IModelRouter.getFallback()` 链找 circuit-cleared 模型；若 SmartModelRouter tier 序列含跨 provider 条目（不同 `provider:model`），已隐式跨 provider。但这是 router 链内静态配置，非健康感知的 P1→P2→P3 优先级队列——W2-4 补的是显式有序 provider failover + failover_switch 去重。
- §13.4（`:729-738`）**方向 only**：三性质（有序 P1→P2→P3 + 每 provider 独立熔断 + failover_switch 去重 + 与 LlmErrorClassifier 组合），**无 schema、无集成点、无健康 roll-up 方案、无去重算法、无状态模型**。

**真正剩余的 gap**：

1. 无 provider 优先级声明落点（schema 缺失）。
2. 无 provider 维度健康状态（ThresholdBreaker 是 per model 复合，无 provider roll-up）。
3. 无 ProviderFailoverQueue（有序游走 + failover_switch 去重防震荡）。
4. **ProviderFailoverQueue 状态模型未定义**（阻断项）：游走器类比 `AccountChain`（per-execution walker，每次 `doLlmCallWithRetry` 新建），但去重/防震荡只在**跨调用**有意义（单次调用内 P1→P3 线性不回退，无震荡；P1↔P2 震荡只发生在跨独立调用间）。故须**混合模式**：per-execution 游标 + 跨调用共享的 provider 健康状态（类比注入式 `ThresholdBreaker` 单例，`LlmCallCoordinator.java:49`）。
5. **provider 切换嵌套循环未定义**：跨 provider 是 provider 循环含 account 链循环（须重置 accountChain + 新 circuit key + 重置 attempt），plan 须明确嵌套结构。
6. 无第三通道集成：跨 provider 切换在 LlmCallCoordinator 的 FALLBACK 路径中无落点（账号链耗尽今日 fail-loud，不升级到跨 provider）。
7. §13.4 design 无算法/集成/schema/状态模型细节——须先 design elaboration。

## Goals

- **provider 优先级声明落点**：裁定 P1→P2→P3 有序 provider 链的声明形态（新 manifest schema vs llm.xdef 扩展 vs 路由配置），回写 design §13.4。
- **provider 维度健康状态**：裁定 provider 级健康来源（ThresholdBreaker per-model roll-up vs 独立 provider tracker），每 provider 独立熔断状态。
- **ProviderFailoverQueue**：有序 provider 游走 P1→P2→P3 + `failover_switch` 去重（防震荡，避免 P1↔P2 反复弹跳）。
- **第三通道集成**：LlmCallCoordinator FALLBACK 路径新增跨 provider 通道——当同 provider 账号链耗尽 / provider 健康降级时，切换到下一 provider 重试（而非 fail-loud）。
- **端到端**：provider P1 整体故障 → failover 到 P2 → 重试成功（或全部 provider 耗尽 fail-loud）。

## Non-Goals

- **改造同 provider 账号链**（W2e-5）——保持 `AccountChain` 现有行为；本计划在其耗尽时升级到跨 provider。
- **改造模型 tier 回退**（`IModelRouter.getFallback`）——保持现有行为；跨 provider 是其上的 provider 维度。
- **ThresholdBreaker 持久化 / 跨进程**——今日仅内存单实例（Non-Goal），本计划不引入持久化。
- **主动 provider 健康探测 / 预测性切换**——被动故障驱动（失败后切换），非主动探测。
- **W2-1/W2-2 checkpoint / W2-3 三级失败升级**——属 W2 其他 work item。
- W1 / W3+ 全部。

## Scope

### In Scope

- provider 优先级声明落点 Decision（schema 形态）+ design §13.4 elaboration。
- provider 维度健康状态 Decision（roll-up vs 独立 tracker）。
- `ProviderFailoverQueue`（有序游走 + failover_switch 去重）。
- LlmCallCoordinator 第三通道集成（跨 provider 切换，账号链耗尽/provider 降级时触发）。
- 端到端验证 + 零回归（两通道行为不变；无 provider 链配置时退回今日 fail-loud）。

### Out Of Scope

- ThresholdBreaker 持久化 / 跨进程共享。
- 主动健康探测。
- W2-1/W2-2/W2-3 / W1 / W3+ 全部。

## Risks And Rollback

- **failover 震荡**：P1 故障切 P2，P1 恢复后又切回 P1 再故障——`failover_switch` 去重须有效防弹跳。缓解：去重窗口 + 每 provider 独立冷却（不切回未过半开期的 provider）。
- **跨 provider 切换改变 usage 归属 / 成本**：切到 P2 可能更贵。缓解：声明层可标 provider tier/cost，failover 优先同级（Non-Goal 主动成本优化，但声明层留位）。
- **零回归红线**：无 provider 链配置时行为须与今日一致（账号链耗尽 fail-loud）。

## Execution Plan

### Phase 1 - design elaboration + schema/健康/集成裁定（Decision）

Status: planned
Targets: `ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md` §13.4（`:729-738` 方向 → 含 schema/算法/集成点的 elaboration）；若引入新 schema 则含 `nop-kernel/nop-xdefs` 下 xdef

- Item Types: `Decision`

- [ ] **Decision A：provider 优先级声明落点**。裁定 P1→P2→P3 有序链声明形态——候选：(i) 新 manifest xdef（`{tenant}.llm-failover.xml` 列 provider + priority + per-provider 健康配置）；(ii) llm.xdef 扩展（加 provider-group/failover-chain 元素）；(iii) 路由配置（SmartModelRouter 扩展）。裁定须给出理由 + 与现有单 provider `{provider}.llm.xml` 的关系（引用 vs 内联）。回写 design §13.4。
- [ ] **Decision B：provider 维度健康来源**。ThresholdBreaker 今日 per `provider:model` 复合。裁定 provider 级健康——候选：(i) roll-up（聚合该 provider 所有 model 的 BreakerEntry 状态）；(ii) 独立 provider tracker（新 provider 维度熔断器）。裁定须正视 §13.4 "每 provider 独立熔断状态"与 live per-model 事实的 reconcile。回写 design §13.4。
- [ ] **Decision C：第三通道集成点 + 触发条件 + 嵌套循环结构**。裁定跨 provider 何时触发——候选：(i) 同 provider 账号链耗尽时升级（被动，故障驱动，单一集成点 `:177-182`）；(ii) provider 健康降级（roll-up OPEN）时主动切（新集成点 `:136` 调用前）；(iii) 两者。**约束：优先 (i)（被动、范围有限、单计划可关闭）；选 (iii) 须将 Phase 2 拆为 2a（被动）+ 2b（主动）**。裁定须明确**嵌套循环结构**：provider 切换须重置 `accountChain`（`:132`）+ 新 circuit-breaker 键（`buildModelKey`）+ 重置 attempt——provider 循环含 account 链循环。回写 design §13.4。
- [ ] **Decision D：failover_switch 去重算法 + 状态模型 + 可测试性**。裁定防震荡机制 + **状态模型**（阻断项）：去重须**跨调用共享状态**（类比注入式 `ThresholdBreaker` 单例），per-execution 游标（类比 `AccountChain`）只管单次调用内 P1→P3 线性游走。**混合模式裁定**：per-execution 游标 + 跨调用共享 provider 健康状态。去重算法候选：去重窗口（N 秒内不切回刚失败 provider）+ 每 provider 独立冷却（半开期探测）。**可测试性约束**：时间依赖逻辑须确定可测试（可注入 Clock/时间源 或零窗口测试模式），**不复制 `ThresholdBreaker` 直接调 `System.currentTimeMillis()` 的反模式**（`:122`）。回写 design §13.4。
- [ ] **Decision E：跨 provider 切换的选项下沉**。**已由代码预定**：`ChatServiceImpl.java:101` 每次 `call()` 从 `request.getOptions().getProvider()` 读 provider 并按 provider 重载 config/dialect——**复用 `ChatOptions` 现有 provider/model 字段即可**（与 W2e-5 账号链经 `ChatOptions.accountKey` 下沉同模式），无需新字段。确认此结论并回写 design §13.4。
- [ ] 回写 design §13.4 从"方向 only"补齐为含 schema/算法/集成/状态模型/嵌套循环的 elaboration；reconcile §13.4 "单 provider 维度"措辞 vs live per-model 事实；补充 §13.5 推荐顺序跳过理由（W2-4 的硬前置是 W2e 错误分类 + 账号链，均已满足；与 W2-1/W2-2/W2-3 无硬依赖）。

Exit Criteria:

- [ ] design §13.4 含 5 项裁定（A-E）结论 + 理由，从"方向"升级为可执行规格
- [ ] **裁定 A schema 落点与 live 一致**：引用 `{provider}.llm.xml` 单 provider 文件结构（llm.xdef 根 `<llm>` 元素 `:7-14`）；裁定 B 与 ThresholdBreaker per-model 事实一致（`:71`）；裁定 E 与 `ChatOptions` 现有 provider/model 字段 + `ChatServiceImpl.buildHttpRequest` 一致
- [ ] 裁定已为 Phase 2 设界：所选 schema/集成须使 Phase 2 单计划可关闭（若需超范围执行层则先拆 predecessor）
- [ ] No owner-doc update beyond design（跨 provider failover 尚未成平台用户可见 API）
- [ ] No new test required: design-only phase（Rule #25）
- [ ] `ai-dev/logs/2026/08-01.md` 已追加本 phase 裁定

### Phase 2 - ProviderFailoverQueue + 第三通道集成（design-gated）（Fix | Proof）

Status: planned
Targets: 依 Phase 1 裁定落点（`.../reliability/ProviderFailoverQueue.java` 新增；`.../engine/LlmCallCoordinator.java` 第三通道；Phase 1 裁定 A 的 schema 落点 + codegen）；`ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md`（记录落地决策子集）

- Item Types: `Fix | Proof`

> **design-gated**：本 phase 落点由 Phase 1 裁定决定。下列项以"无论裁定如何都必须成立"的可观测结果表述。

- [ ] 落地 Phase 1 裁定 A 的 schema（provider 优先级声明）+ codegen 模型；改 xdef 后**必须先 `./mvnw install -pl nop-kernel/nop-xdefs -am -DskipTests`** 重打包。
- [ ] 落地 Phase 1 裁定 B 的 provider 维度健康（roll-up 或独立 tracker），每 provider 独立熔断状态。
- [ ] **ProviderFailoverQueue（混合状态模型）**：per-execution 有序游标（P1→P2→P3，类比 `AccountChain`）+ **跨调用共享 provider 健康状态**（注入式单例，类比 `ThresholdBreaker`，承载 failover_switch 去重窗口）。按 Phase 1 裁定 D 的可测试时间源（可注入 Clock，非直接 `System.currentTimeMillis()`）。
- [ ] **第三通道集成 + 嵌套循环** in `LlmCallCoordinator`：按 Phase 1 裁定 C 的触发条件——同 provider 账号链耗尽 / provider 健康降级时，切到下一 provider。**嵌套循环结构**：provider 切换须重置 `accountChain`（`:132`，新 provider 有自己的 `<accounts>`）+ 新 circuit-breaker 键（`buildModelKey`）+ 重置 attempt；全部 provider 耗尽 fail-loud（design §6.9 模式，不静默降级）。
- [ ] 单测：N provider 链按声明顺序解析；failover_switch 去重生效（窗口内不切回）；provider 健康降级触发切换；全部耗尽 fail-loud。

Exit Criteria:

- [ ] provider 优先级声明 schema 存在，codegen 模型已生成（`./mvnw clean compile` 通过）
- [ ] ProviderFailoverQueue 有序游走 + failover_switch 去重有单测断言（去重窗口内不切回刚失败 provider）
- [ ] **第三通道集成真实生效**：同 provider 账号链耗尽 → 切下一 provider（非 fail-loud），有测试断言 provider 切换（`ChatOptions.provider` 变化）
- [ ] **端到端验证**：provider P1 整体故障（账号链耗尽）→ failover 到 P2 → 重试成功；全部 provider 耗尽 → fail-loud。`TestProviderFailoverQueue` 从 `LlmCallCoordinator.doLlmCallWithRetry` 入口到跨 provider 成功/fail-loud 完整跑通（Minimum Rules #22）
- [ ] **接线验证**：ProviderFailoverQueue 在运行时确实被 FALLBACK+账号链耗尽分支调用（计数器/标志位 verify，Minimum Rules #23）；账号链/模型 tier 两通道行为不变
- [ ] **无静默跳过**：全部 provider 耗尽显式抛异常（非返回 null/STOP 当正常）；无 provider 链配置时退回今日 fail-loud（显式，明示零回归）
- [ ] **零回归**：无 provider 链配置时账号链耗尽仍 fail-loud（今日行为）；未配置 `<errorMappings>` 的 provider QUOTA/AUTH 不可达（不变量）
- [ ] design §13.4 已记录落地决策子集
- [ ] `ai-dev/logs/2026/08-01.md` 已追加本 phase

## Closure Gates

> 本计划涉及代码 + design + 可能的 xdef 变更，构建验证条目保留。

- [ ] 跨 provider 有序故障转移端到端成立：P1 故障 → P2 → 成功 / 全部耗尽 fail-loud，测试覆盖
- [ ] failover_switch 去重生效（防震荡），有测试断言
- [ ] 三通道区分成立：同 provider 账号链（QUOTA/AUTH）/ 模型 tier（TRANSIENT）/ 跨 provider（provider 级故障），无错误降级
- [ ] 零回归：无 provider 链配置时行为不变（账号链耗尽 fail-loud）
- [ ] 无静默跳过：provider 耗尽 fail-loud
- [ ] design §13.4 从"方向"升级为含 schema/算法/集成点的 elaboration
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 验证（a）ProviderFailoverQueue 在运行时被调用，（b）跨 provider 切换确实改变 `ChatOptions.provider`，（c）端到端从 provider 故障到 failover 成功/fail-loud 完整连通
- [ ] 若改 xdef：`./mvnw install -pl nop-kernel/nop-xdefs -am -DskipTests` 后 `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过
- [ ] `./mvnw compile` 通过
- [ ] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### ThresholdBreaker 持久化 / 跨进程共享

- Classification: `optimization candidate`
- Why Not Blocking Closure: 今日仅内存单实例（既有 Non-Goal）；跨 provider failover 在单实例内成立。持久化/跨进程是独立优化（多实例 provider 健康共享）。
- Successor Required: no

### 主动 provider 健康探测 / 预测性切换

- Classification: `optimization candidate`
- Why Not Blocking Closure: 本计划是被动故障驱动（失败后切换）；主动探测需探测调度子系统，属独立优化。
- Successor Required: no

## Non-Blocking Follow-ups

- provider tier/cost 声明 + failover 优先同级（声明层留位，非阻塞）。
- 跨 provider failover 可观测性指标（切换频率、provider 健康分布）。

## Closure

Status Note: <<完成时填写>>
Completed: <<YYYY-MM-DD>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立子 agent>>
- Evidence: <<完成时填写>>

Follow-up:

- <<完成时填写>>
