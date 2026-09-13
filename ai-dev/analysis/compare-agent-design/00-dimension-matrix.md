# 对比维度矩阵 D1–D10（WI2 交付物）

> Status: resolved
> Date: 2026-09-12
> Scope: nop-ai-agent / deepseek-harness / pi 三方 agent 内在设计对比的维度权威定义：D1–D10 每维的子机制拆解、三方代码锚点候选、报告固定 6 节模板与裁定格式；S1–S4 专项文档章节结构登记与权威源约定
> Conclusion: 维度矩阵已落定，构成 WI4–WI27 的报告契约：每份维度报告固定 6 节 + 每子机制逐项裁定（nop 领先 / 对方领先 / 等价 / 双方均无 / 不可比）+ 每维度总裁定；专项文档 03–06 是执行流程/扩展点能力/同点顺序/跨扩展协同四个主题的权威源，维度报告冲突时以专项文档为准并由 WI29 收敛
> 基线: 三方 HEAD 与 `ai-dev/analysis/compare-agent-design/01-code-map.md` 一致（nop=c585459f83、dsh=141eb6fef8、pi=c49906ec7）；锚点候选全部取自该代码地图

## Context

- 为什么需要：`nop-ai-agent-design-comparison` roadmap（WI2）需要一份维度权威定义，供 WI4–WI7（专项深挖 S1–S4）与 WI8–WI27（20 份逐维对比报告）作为统一契约引用；本矩阵只定义"怎么比、比哪些子机制、按什么模板产出"，不产出任何对比结论（结论是 WI8–WI27 的工作）。
- 输入：`ai-dev/analysis/compare-agent-design/01-code-map.md`（WI1 交付物，三方代码锚点基础）、`ai-dev/analysis/00-analysis-writing-guide.md`（写作规范）、`ai-dev/analysis/agent-survey/agentscope-harness-vs-nop-ai-agent-comparison.md`（对比格式先例：结论先行 + 勘误 + 对照表结构）、roadmap 中 D1–D10 维度定义与 S1–S4 专题要求。
- 约束：纯分析任务，无代码变更；本矩阵是 owner 契约，roadmap 与本矩阵冲突时以本矩阵为准（roadmap Rules）。

## 报告模板契约（每份维度报告固定 6 节）

每份维度报告（dsh-Dx 或 pi-Dx）都遵循固定 6 节模板。`dsh-Dx` 的"对方侧"是 deepseek-harness，`pi-Dx` 的"对方侧"是 pi；两套报告共用同一模板。

```md
# <dsh|pi>-D<x> <维度名> 对比

> Status: open
> Date: <分析日期>
> 基线: nop=<HEAD>、<dsh|pi>=<HEAD>（分析当日实测）
> 引用: 00-dimension-matrix.md（WI2）、02-terminology-map.md（WI3）; 相关专项 03/04/05/06

## ① 结论摘要

- ≤10 行：本维度总裁定 + 关键差异 1–3 条（各带一个锚点佐证）+ 可吸收增量建议一句话

## ② nop 侧机制与锚点

- 按子机制逐项：机制说明 + 代码锚点（仓库相对路径 + 类/函数名，必要时 `:line`）
- 该主题已有专项深挖（03–06）时，本节约引专项结论 + 只写对比增量，不重复展开

## ③ 对方侧机制与锚点

- 同上，对方（dsh / pi）侧

## ④ 子机制逐项对照表

| 子机制 | nop 机制 | 对方机制 | 裁定 | 证据 |
|--------|----------|----------|------|------|

- 每行一个子机制（沿用本矩阵 D 节的子机制编号），裁定用 5 值格式（见下），证据填关键锚点

## ⑤ 语义差异与取舍

- Java 与 TS 范式差异按 WI3 术语表翻译后对比，禁止直译制造伪差异
- 同一子机制双方皆无时裁定"双方均无"，不得硬比
- 记录设计取舍与语义不等价之处（词同义异 / 义同词异）

## ⑥ 裁定 + 可吸收增量建议

- 本维度总裁定（5 值之一）+ 一句话依据
- 可吸收增量建议：仅记录，不实施，不给排期；每条建议注明来源侧与针对的 nop 现状
```

### 裁定格式

- 5 值裁定枚举：`nop 领先` / `对方领先` / `等价` / `双方均无` / `不可比`。
- 每份报告两级裁定：子机制逐项裁定（对照表每行）+ 维度总裁定（⑥ 节首行）。
- 裁定必须带证据：锚点 + 代码行为描述；能力级别（observe/transform/veto/abort-bail/inject）以代码实际行为为准，不以注释、文档或类型名为准。
- `不可比` 用于范式无法对位（如跨语言 AOP 语义、平台基础设施）且翻译后仍无共同语义面的子机制，须写明不可比原因。

### 证据纪律（roadmap Cross-Cutting 固化）

- 每条结论带代码锚点（仓库相对路径 + 类/函数名）；对方仓库设计文档只作导航，结论必须核对代码。
- 每份报告头部记录被分析仓库 HEAD commit 与分析日期。
- 外部仓库路径书写为 `~/ai/deepseek-harness` / `~/ai/pi` 或对方仓库相对路径并注明仓库。

## 权威源约定（S1–S4 专项文档登记）

专项深挖主题 S1–S4 是四个机制级专题的权威文档，章节结构在本矩阵登记作为权威源契约。交付物路径：`ai-dev/analysis/compare-agent-design/03-flow-agent-loop.md`、`04-extension-capability-matrix.md`、`05-extension-ordering.md`、`06-extension-composition.md`。

| 编号 | 主题 | 交付物 | 章节结构 |
|------|------|--------|----------|
| S1 | agent loop 具体执行流程 | 03-flow-agent-loop.md | ① 结论摘要（≤10 行）② 三方各自一条完整调用链：从输入进入到最终响应的逐步分解（阶段划分、每步职责、流式路径），流程图上逐点标注该阶段挂载的扩展点 ③ 扩展点挂载位置汇总表（阶段 × 扩展点）④ 三方流程结构差异点 ⑤ 权威源引用关系（供 dsh-D1 / pi-D1 引用） |
| S2 | 扩展点全量清单与能力语义 | 04-extension-capability-matrix.md | ① 结论摘要 ② 能力级别五级定义（observe 仅监听 / transform 修改内容 / veto 否决跳过 / abort-bail 中断轮次 / inject 注入内容）③ 三方每个扩展点一行：触发时机、可扩展/可影响内容、能力级别、同步异步、异常如何传播（能力级别以代码实际行为为准）④ 能力级别汇总对比表 ⑤ 权威源引用关系（供 dsh-D2 / pi-D2 引用） |
| S3 | 同一扩展点多触发顺序 | 05-extension-ordering.md | ① 结论摘要 ② 排序来源（注册顺序 / 优先级字段 / DSL 声明顺序 / 订阅顺序）③ 分发语义（waterfall 逐层包裹 / serial 顺序 await / emit 广播 / 链式）④ 顺序对调用方的可预测性与可配置性 ⑤ 权威源引用关系（供 dsh-D2 / pi-D2 引用） |
| S4 | 不同扩展机制之间的协同 | 06-extension-composition.md | ① 结论摘要 ② 三方各自扩展机制面（nop：lifecycle hook × execution middleware × filter chain × DSL 声明扩展；dsh：agent waterfall × tools waterfall × llm/stream × system-prompt；pi：AgentLoopConfig hook × ExtensionAPI event × registerProvider）③ 同一行为穿过多种扩展机制时的叠加顺序 ④ 冲突裁决、veto/bail 的传播边界与终止范围 ⑤ 权威源引用关系（供 dsh-D2 / pi-D2 引用） |

### 冲突收敛规则（权威源契约）

- 03–06 专项文档是执行流程、扩展点能力语义、同点顺序、跨扩展协同四个主题的权威深挖；dsh-D1/D2、pi-D1/D2 等维度报告引用专项结论，只写对比增量，不重复展开。
- 维度报告与专项文档结论冲突时，**以专项文档为准**，并由 WI29 交叉一致性校对收敛记录到总报告附录。
- S1–S4 专项文档自身冲突（同一代码行为在不同主题文档中描述不一致）由 WI29 发现并修正。

## D1–D10 维度定义与子机制拆解

维度定义来自 roadmap（WI2 的输入定义），以下子机制拆解为本矩阵落定内容，是 WI8–WI27 逐项对照的统一口径。三方锚点候选全部取自 WI1 代码地图，执行时须按各仓库当前 HEAD 复核。每维度的报告模板引用统一指向"报告模板契约"节。

### D1 内部 agent loop

**定义**：主循环形态（turn/step/iteration 分层）、终止条件、迭代上限与延长、流式输出、输入注入/steering。

**子机制拆解**：

| 编号 | 子机制 | 说明 |
|------|--------|------|
| D1-1 | 主循环形态与层级 | 循环是单层还是多层（iteration/turn/step）；循环体职责划分；每轮产出什么 |
| D1-2 | 终止条件判定 | 正常终止（无工具调用 / 用户指令 / 判定器）与异常终止的判定来源 |
| D1-3 | 迭代上限与延长 | 是否硬性步数上限；超限后的行为（强制停止 / 延长 / 升级）；延长机制 |
| D1-4 | 流式输出 | LLM 输出是否流式；token/消息级事件；chunk 是否持久化 |
| D1-5 | 输入注入 / steering | 循环进行中是否可注入输入（follow-up / steer / 队列）；注入点与优先级 |

**三方代码锚点候选**：

| 侧 | 锚点候选 |
|----|----------|
| nop | `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/ReActAgentExecutor.java`（双层 while 主循环）、`engine/SingleTurnExecutor.java`（单轮策略）、`engine/AgentLoopGuard.java`（maxIterations）、`reliability/ISustainer.java` + `SisypheanSustainer`（超限延长）、`completion/ICompletionJudge.java` + `RuleBased`/`Llm` 实现、`reliability/IGoalTracker.java` + `SessionGoalTracker`（卡死检测）、`engine/AgentExecutionContext.java` / `AgentExecutionResult.java` |
| dsh | packages/core/agent-loop/src/agent.ts（ReactLoopAgent，:64）、packages/core/agent-loop/src/index.ts（AgentLoop，:296）、packages/core/session/src/types.ts（TurnEndReason）、packages/core/agent/src/inbox.ts（followup/steer/inject）、chunk 持久化（session/surface.ts SurfaceManager :398） |
| pi | packages/agent/src/agent-loop.ts（agentLoop/agentLoopContinue，796 行）、packages/agent/src/agent.ts（Agent :592，steer/followUp + PendingMessageQueue QueueMode）、packages/agent/src/types.ts（stopReason / AgentLoopConfig） |

**报告模板引用**：dsh-D1（WI8）、pi-D1（WI18）；流程级细节引用 03 专项文档，只写对比增量。

### D2 扩展点

**定义**：hook/中间件/waterfall/拦截器的类型与触发阶段、注册方式（声明式 DSL vs 代码）、veto/改写语义。

**子机制拆解**：

| 编号 | 子机制 | 说明 |
|------|--------|------|
| D2-1 | 扩展点类型与触发阶段 | 有哪些扩展机制（hook/middleware/waterfall/事件拦截）；各自触发阶段与粒度 |
| D2-2 | 注册方式 | 声明式（DSL/配置）vs 编程式（代码/接口）；注册入口与发现机制 |
| D2-3 | veto / 改写语义 | 扩展能否否决本轮/中止执行、改写内容；结果模型（pass/veto/重入/链式改写） |
| D2-4 | 能力级别与错误传播 | 扩展失败（异常/拒绝）对主循环的影响；同步异步 |

**三方代码锚点候选**：

| 侧 | 锚点候选 |
|----|----------|
| nop | `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/hook/AgentLifecyclePoint.java`（12 点）+ `IAgentLifecycleHook`/`IHookRegistry`、`middleware/IAgentMiddleware.java` + `MiddlewareChain.java` + `ExecutionPoint`（4 点）、`HookResult`（Pass/Veto/Reenter/Bail 四态，勘误见 02-terminology-map.md T7）、filter chain（security 包）、66 接口扩展矩阵（owner doc `ai-dev/design/nop-ai-agent/03-extension-matrix.md`）、`engine/AgentHookInvoker.java` |
| dsh | agent waterfall（agent/pre-step / agent/request / agent/request-error / agent/turn-stopping，packages/core/agent 事件）、tools/* waterfall、packages/llm/llm/src/index.ts（llm/stream）、capability seams |
| pi | packages/agent/src/types.ts（AgentLoopConfig hooks：transformContext/beforeToolCall/afterToolCall/prepareNextTurn 等）、packages/coding-agent/src/core/extensions/types.ts（ExtensionAPI，25 联合成员 / 34 type 标签 / 48 具体事件）、registerTool/registerCommand/registerProvider（extensions/runner.ts） |

**报告模板引用**：dsh-D2（WI9）、pi-D2（WI19）；能力级别与顺序语义引用 04/05/06 专项文档，只写对比增量。

### D3 事件类型与触发方式

**定义**：事件词表与分类、发射点、订阅模型（emit/serial/waterfall、同步/异步）、持久化事件 vs 瞬时事件、UI 桥接。

**子机制拆解**：

| 编号 | 子机制 | 说明 |
|------|--------|------|
| D3-1 | 事件词表与分类 | 事件命名与类型系统；分类维度（生命周期/错误/工具/持久化） |
| D3-2 | 发射点 | 事件在流程哪些位置被发射；发射者与载荷 |
| D3-3 | 订阅模型 | 分发语义（emit 广播 / serial 顺序 / waterfall 链）；同步异步；订阅注册方式 |
| D3-4 | 持久化事件 vs 瞬时事件 | 事件是否写日志/存储；重放能力；UI/桥接消费方 |

**三方代码锚点候选**：

| 侧 | 锚点候选 |
|----|----------|
| nop | `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/AgentEventType.java` + `IAgentEventPublisher` + `DefaultAgentEventPublisher`、`message/IMailbox.java` + `DeferredAckMailbox`（3-phase reservation）、`message/DBMessageService.java`、`message/IAgentMessenger.java` |
| dsh | packages/core/session/src/types.ts（SessionEventMap :236，48 类持久化 SessionEvent）、packages/core/agent/src/types.ts（inbox/spliced :19）、Cordis 瞬时事件 emit/serial/waterfall（vendor/cordis/src/events.ts） |
| pi | packages/agent/src/types.ts（AgentEvent 判别联合 :428）、packages/agent/src/harness/events.ts（HarnessEvent run_start/run_end）、AgentSessionEvent 扩展（coding-agent，auto_retry_* 等）、JSONL/TUI 桥接 |

**报告模板引用**：dsh-D3（WI10）、pi-D3（WI20）。

### D4 容错性

**定义**：错误分类体系、重试策略与退避、部分失败与中断语义、abort/cancel、降级与恢复路径。

**子机制拆解**：

| 编号 | 子机制 | 说明 |
|------|--------|------|
| D4-1 | 错误分类体系 | 错误如何分类（可重试/不可重试/限流/配额/鉴权）；分类依据 |
| D4-2 | 重试策略与退避 | 重试条件、次数、退避算法、Retry-After 尊重 |
| D4-3 | 部分失败与中断语义 | 工具部分失败 / 轮次中断的结果语义；合成结果 / 错误回填 |
| D4-4 | abort/cancel | 取消的入口、传播路径、清理语义 |
| D4-5 | 降级与恢复路径 | 失败升级（三级）、会话暂停/恢复、续跑语义 |

**三方代码锚点候选**：

| 侧 | 锚点候选 |
|----|----------|
| nop | `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/reliability/LlmErrorClassifier.java`（ErrorClassification：TRANSIENT/RATE_LIMITED/NON_TRANSIENT/QUOTA_EXCEEDED/AUTH_INVALID/CACHE_STATE_LOST）、`reliability/StandardRetryPolicy.java`（指数退避全抖动 + Retry-After floor）、`reliability/IRetryPolicy.java`、`security/IDenialLedger.java` + `DefaultDenialLedger`/`DBDenialLedger`（阈值 3 → session paused）、`engine/LlmCallCoordinator.java`（熔断→重试→超时）、`reliability/ISustainer.java`（at-least-once 续跑） |
| dsh | packages/core/session/src/index.ts（HarnessError 稳定错误码）、packages/llm/llm-retry/src/index.ts（agent/request-error 重试 waterfall :210 + 退避 + provider retry-after）、持久化 llm/retry 事件、工具中止合成结果 |
| pi | 可重试/不可重试正则分类（coding-agent settings）、传输层（SDK 镜像策略）与应用层（AgentSession 自动重试）双重重试、上下文溢出独立分类走压缩、错误工具结果回填 |

**报告模板引用**：dsh-D4（WI11）、pi-D4（WI21）。

### D5 自动切换

**定义**：provider/model/账号故障转移、熔断与冷却、配额感知、模型分级路由、切换语义与 fail-loud。

**子机制拆解**：

| 编号 | 子机制 | 说明 |
|------|--------|------|
| D5-1 | provider/model/账号故障转移 | 失败后切换通道（换账号/换 provider/换 model）；切换链顺序 |
| D5-2 | 熔断与冷却 | 熔断状态机（CLOSED/OPEN/HALF_OPEN）、阈值、冷却、懒探针 |
| D5-3 | 配额感知 | 配额/预算对切换的影响；配额失败是否触发切换 |
| D5-4 | 模型分级路由 | 复杂度分级路由、预算降级、fallback 链 |
| D5-5 | 切换语义与 fail-loud | 切换是自动还是留白给扩展；无可用通道时的失败语义 |

**三方代码锚点候选**：

| 侧 | 锚点候选 |
|----|----------|
| nop | `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/LlmCallCoordinator.java`（重试→熔断→账号链→ProviderFailoverChain→分级路由）、`nop-ai-core/.../reliability/ThresholdBreaker.java`（阈值 3 / 60s 冷却）、`reliability/IAccountChainResolver.java` + `AccountChain`、`reliability/IProviderFailoverChainResolver.java` + `ProviderFailoverChain`/`ProviderFailoverQueue`、`router/SmartModelRouter.java`（Complexity 分级 + 预算降级 + fallback 链）、`budget/IBudgetProvider.java`、`quota/IResourceGuard.java` + `QuotaConfig` |
| dsh | 无内置 failover——agent/request 逐步重写 provider/model（packages/core/agent 事件）、per-provider 重试策略、配额仅分类不转移 |
| pi | 无内置——model_select（用户/扩展驱动）+ registerProvider 热注册 + per-call getApiKey（packages/ai） + 配额不可重试 |

**报告模板引用**：dsh-D5（WI12，deps WI11）、pi-D5（WI22，deps WI21）。

### D6 前缀缓存利用

**定义**：缓存断点控制、前缀稳定性构造、压缩与缓存的交互、一次性调用是否写缓存、缓存命中观测。

**子机制拆解**：

| 编号 | 子机制 | 说明 |
|------|--------|------|
| D6-1 | 缓存断点控制 | 显式断点（cache_control / 断点指令）的放置位置与 TTL |
| D6-2 | 前缀稳定性构造 | system prompt / 工具定义的稳定化设计；动态内容如何不破坏前缀 |
| D6-3 | 压缩与缓存的交互 | 压缩（spill/总结/截断）对 KV cache 复用是促进还是破坏 |
| D6-4 | 一次性调用是否写缓存 | 一次性/工具类调用是否禁用缓存写入 |
| D6-5 | 缓存命中观测 | 命中/未命中统计能力；观测接口 |

**三方代码锚点候选**：

| 侧 | 锚点候选 |
|----|----------|
| nop | 现状待 D6 报告逐项核查（显式缓存断点 / 前缀稳定性约定是否存在）；锚点候选：`compact/PipelineCompactor.java`、`engine/ChatOptionsHelper.java`（ChatOptions 构造）、`engine/ITokenEstimator.java` |
| dsh | 构造性前缀稳定：section 化静态 system prompt（packages/core/system-prompt/src/index.ts）、动态上下文走 user 快照消息、压缩请求字节级重放复用 KV cache（packages/compaction/compaction-basic/src/index.ts）、purpose 标记辅助调用 |
| pi | packages/agent/src/harness/compaction/compaction.ts（Anthropic cache_control 断点：system / 最后工具定义 / 最后 user 消息 + cacheRetention long/short/none + 工具集变化才重建 system prompt + 稳定工具序 + 一次性调用禁写缓存 + cache-stats 观测） |

**报告模板引用**：dsh-D6（WI13）、pi-D6（WI23）。

### D7 工具系统

**定义**：声明与 schema、执行调度（并行/串行/屏障）、结果回填、工具调用修复、权限/沙箱。

**子机制拆解**：

| 编号 | 子机制 | 说明 |
|------|--------|------|
| D7-1 | 工具声明与 schema | 工具如何声明（DSL/代码）；参数 schema 来源与格式 |
| D7-2 | 执行调度 | 工具并行/串行/屏障；并发安全标注；调度队列 |
| D7-3 | 结果回填 | 工具结果如何回填上下文；大小限制/截断 |
| D7-4 | 工具调用修复 | 模型产出非法调用时的修复链（名称/参数/类型/schema） |
| D7-5 | 权限与沙箱 | 工具访问控制、审批、沙箱隔离 |

**三方代码锚点候选**：

| 侧 | 锚点候选 |
|----|----------|
| nop | tool.xdef DSL（`ai-dev/design/nop-ai-agent/04-tool-invocation.md`）、`engine/AgentToolDispatcher.java`（activeTags/denyTags/denyTools 标签过滤）、`repair/ChainRepairer.java`（4 阶段：名称规范化/参数结构/类型强转/schema 清理）、`security/SecurityCheckpointChain.java`（7-checkpoint，含 approval gate + path checker + sandbox `ISandboxBackend`）、`tool/` 13 工具（memory 三件套 / call-agent / send-message / team 五件套） |
| dsh | packages/core/tools/src/index.ts（defineTool：输出 schema / render / isConcurrencySafe）、tools/pre-execute 审批（packages/guard）、Landlock/Seatbelt 沙箱（packages/sandbox）、code-mode（packages/core/agent-tool-presentation） |
| pi | packages/agent/src/types.ts（AgentTool :386，typebox schema）、默认并行 / sequential 覆盖、deferred tools（transcript 中途引入）、文件变更串行队列（harness/tools/file-mutation-queue）、权限留白给扩展 |

**报告模板引用**：dsh-D7（WI14）、pi-D7（WI24）。

### D8 上下文工程与压缩

**定义**：token 预算与压力检测、触发条件、策略分层、spill/引用式保真、产物形态（破坏性 vs 追加式）。

**子机制拆解**：

| 编号 | 子机制 | 说明 |
|------|--------|------|
| D8-1 | token 预算与压力检测 | 预算估算（校准/EMA）；超限压力如何检测 |
| D8-2 | 触发条件 | 压缩触发时机（阈值 / 溢出 / 手动 / 强制） |
| D8-3 | 策略分层 | 压缩策略分级（截断→微压缩→turn 剪枝→全文总结）；升级条件 |
| D8-4 | spill / 引用式保真 | 内容溢出存储（spill）；引用式/内容寻址压缩的保真语义 |
| D8-5 | 产物形态 | 压缩是破坏性（删除历史）还是追加式（追加 CompactionEntry）；可恢复性 |

**三方代码锚点候选**：

| 侧 | 锚点候选 |
|----|----------|
| nop | `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/compact/PipelineCompactor.java`（Layer 1→2→3 升级）、`compact/ToolResultTruncator.java`、`compact/MicroCompressionCompactor.java`、`compact/Layer2TurnPruningStrategy.java`、`compact/Layer3FullSummaryStrategy.java`（7 段 prompt）、`compact/ReferenceCompactionStrategy.java`（内容寻址引用式）、`compact/ISpillStore.java` + `InMemorySpillStore`、`engine/AgentCompactionCoordinator.java`、`engine/ITokenEstimator.java` + `CalibratedTokenEstimator`（EMA 校准） |
| dsh | packages/compaction/compaction/src/index.ts（CompactionEngine seam，compactIfNeeded/compactNow）、packages/compaction/compaction-basic/src/index.ts（region 区间选择 / summarizer / 压力与 context-overflow 触发 / token 预算保留）、tool-pairing 平衡、spill policy（packages/spill/spill-policy）、ToolResultPruner（compaction-tool-result-pruner） |
| pi | packages/agent/src/harness/compaction/compaction.ts（threshold/overflow/manual 触发 + reserveTokens/keepRecentTokens 预算 + 追加式 CompactionEntry 非破坏重建）、coding-agent compaction.ts + branch-summarization.ts |

**报告模板引用**：dsh-D8（WI15）、pi-D8（WI25）。

### D9 会话持久化与恢复

**定义**：session 数据模型、存储格式与后端、resume/fork/branch、checkpoint 与崩溃恢复。

**子机制拆解**：

| 编号 | 子机制 | 说明 |
|------|--------|------|
| D9-1 | session 数据模型 | 会话状态的数据结构（消息/事件/状态快照）；版本化 |
| D9-2 | 存储格式与后端 | 序列化格式（JSONL/JSON/SQLite/DB）与写策略（write-behind / 原子写） |
| D9-3 | resume / fork / branch | 会话恢复、分支/会话树语义 |
| D9-4 | checkpoint 与崩溃恢复 | 检查点持久化、崩溃尾部修复、恢复扫描、幂等与发散检测 |

**三方代码锚点候选**：

| 侧 | 锚点候选 |
|----|----------|
| nop | `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/session/AgentSession.java` + `ISessionStore`（`InMemorySessionStore`/`FileBackedSessionStore`/`DBSessionStore`）、`reliability/ICheckpointManager.java` + `DBCheckpointManager`/`FileBackedCheckpointManager`、`reliability/CheckpointJournalWriter.java`/`Reader`（LLM_TURN/TOOL_EXECUTION/COMPACTION/WAIT_FOR，幂等键/发散检测）、`runtime/lock/ISessionTakeoverLock.java` + `DbSessionTakeoverLock`（CAS + lease）、`runtime/recovery/IRecoveryManager.java` + `ScheduledRecoveryManager`（60s 扫描）、`runtime/recovery/DefaultOrphanRecoveryHandler.java` |
| dsh | packages/core/session/src/index.ts（追加式类型化事件日志 + fork 边界 + write-behind）、session-persistence（coordinator.ts :903，interruptedTurnClosers 崩溃尾部修复）、JSONL(zstd)/SQLite 双后端（packages/storage）、SurfaceManager（surface.ts :398） |
| pi | packages/agent/src/harness/session/jsonl/（codec.ts / storage.ts 原子重命名写 / repo.ts JsonlSessionRepo：id/parentId 会话树 + 类型化条目 + 版本迁移 + labels + SQLite 后端 + 上下文重建）、packages/coding-agent/src/core/session-manager.ts（CURRENT_SESSION_VERSION=3，fork/switch/tree） |

**报告模板引用**：dsh-D9（WI16）、pi-D9（WI26）。

### D10 多代理与子代理

**定义**：派生模型、深度预算、团队/编排、与主循环的隔离边界。

**子机制拆解**：

| 编号 | 子机制 | 说明 |
|------|--------|------|
| D10-1 | 派生模型 | 子代理/成员如何声明与派生（工厂/DSL/运行时） |
| D10-2 | 深度预算 | 子代理层级深度、数量、迭代预算控制 |
| D10-3 | 团队 / 编排 | 团队生命周期、任务编排（fan-out / 归约 / DAG）、调度 |
| D10-4 | 与主循环的隔离边界 | 子代理执行与主循环的隔离（进程/线程/上下文）；消息通道；跨实例协调 |

**三方代码锚点候选**：

| 侧 | 锚点候选 |
|----|----------|
| nop | `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/team/ITeamManager.java` + `InMemoryTeamManager`/`DbTeamManager`、`team/ACL`、`team.flow/TeamTaskFlowOrchestrator.java` + `MemberFanOutDispatcher` + `AllMustSucceedReduction`、`team.scheduler/`、`runtime/AgentActor.java` + `IActorRuntime`、`plan/runtime/PlanExecutor.java`（计划状态机）、`tool/CallAgentExecutor.java` + team 五工具、`runtime/lock/ISessionTakeoverLock.java`（跨进程接管） |
| dsh | packages/subagent/subagent/src/index.ts（SubagentRuntime :171）+ continuation.ts（SubagentContinuationManager :355）；6 provider：spawn/fork（in-process）/acp/claude-code/codex/dsh-sdk + 深度预算 + 实验性 agent-team |
| pi | 无内置（官方 example extension 形态）及其设计取舍 |

**报告模板引用**：dsh-D10（WI17）、pi-D10（WI27）。

## 报告模板与专项文档的消费关系

- WI4（S1）→ 03-flow-agent-loop.md；WI5（S2）→ 04-extension-capability-matrix.md；WI6（S3）→ 05-extension-ordering.md；WI7（S4）→ 06-extension-composition.md。
- WI8–WI17（dsh-D1..D10）与 WI18–WI27（pi-D1..D10）→ 各 D 节"报告模板引用"标注的 6 节模板；其中 D1/D2 报告引用 03–06 专项结论，只写对比增量。
- WI28 总报告汇总 20 份维度报告 + 4 份专项文档，引用本矩阵的裁定格式；WI29 交叉校对按本矩阵的维度定义与权威源约定收敛。
- 本矩阵（00-dimension-matrix.md）与 roadmap 工作项行描述冲突时，roadmap Rules 明确以本矩阵为准（先更新矩阵，再同步回写 roadmap）。

## Conclusion

- D1–D10 每维已给出子机制拆解（共 46 个子机制项：D1/D4/D5/D6/D7/D8 各 5 项，D2/D3/D9/D10 各 4 项）与三方代码锚点候选（基于 WI1 代码地图），维度报告可据此逐项对照。
- 报告固定 6 节模板与 5 值裁定格式已固化，WI8–WI27 可直接按此产出。
- S1–S4 章节结构与权威源约定（以专项文档为准 + WI29 收敛）已登记，WI4–WI7 可直接按此产出。
- 本矩阵不产出任何对比结论；对比结论是 WI8–WI27 的工作（Non-Goal 兑现）。
- 验证：`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0。

## Open Questions

- D6（前缀缓存利用）的 nop 侧现状尚未核查，矩阵只登记锚点候选；nop 侧是否存在显式缓存断点/前缀稳定性设计由 WI13（dsh-D6）与 WI23（pi-D6）在对比中逐项核查并写入报告。
- 三方 agent 内在设计之外的项目（插件热重载 / UI-TUI / RPC 协议 / 评测）明确排除在对比范围外，不产生 Open Question。

## References

- `ai-dev/analysis/compare-agent-design/01-code-map.md`（WI1，本矩阵锚点来源）
- `ai-dev/analysis/00-analysis-writing-guide.md`（写作规范）
- `ai-dev/analysis/agent-survey/agentscope-harness-vs-nop-ai-agent-comparison.md`（对比格式先例）
- `ai-dev/backlog/nop-ai-agent-design-comparison-roadmap.md`（WI2 编排与 D1–D10/S1–S4 定义）
- `ai-dev/design/nop-ai-agent/03-extension-matrix.md`（nop 扩展接口矩阵，66 接口）、`01-architecture-baseline.md`、`nop-ai-agent-react-engine.md`、`nop-ai-agent-reliability.md`、`nop-ai-agent-context-compaction-economics.md`、`nop-ai-agent-context-model.md`、`04-tool-invocation.md`、`nop-ai-agent-session-and-storage.md`、`nop-ai-agent-multi-agent.md`（nop 侧 owner docs）
- `~/ai/deepseek-harness`（HEAD 141eb6fef8；docs/architecture.md、docs/subsystems/*.md 只作导航）
- `~/ai/pi`（HEAD c49906ec7；packages/coding-agent/docs/*.md 只作导航）