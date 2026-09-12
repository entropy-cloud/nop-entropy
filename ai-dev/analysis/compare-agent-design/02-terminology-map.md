# 三方术语与概念对齐表（WI3 交付物）

> Status: resolved
> Date: 2026-09-12
> Scope: nop-ai-agent（Java）/ deepseek-harness（dsh，TypeScript）/ pi（TypeScript）三方 agent 内在设计的术语与概念映射：主循环层级、终止、扩展机制、能力语义、事件、容错、切换、缓存、工具、压缩、会话、checkpoint、多代理等 17 个概念族节；为 WI4–WI27 提供统一翻译口径
> Conclusion: 三方词表结构差异显著——nop 以"枚举点 + 结果对象"（AgentLifecyclePoint/HookResult）与接口族表达扩展，dsh 以统一 waterfall 事件原语覆盖全部扩展面，pi 分三层（配置级单槽 hook / ExtensionAPI 多播事件 / register* 注册）；最容易制造伪差异的词是 session（dsh=事件溯源账本≠HTTP session）、waterfall（洋葱包裹≠广播）、steer（插话≠abort）、continue（约束续跑）、harness（pi 存在 v3/v4 双会话栈）。各维对比（WI8 起）必须按本表翻译后对比，禁止按词面直判等价。
> 基线: nop=c585459f83、dsh=c291e7961a（~/ai/deepseek-harness，WI8-WI12 期重钉；141eb6fef8 为其祖先）、pi=c49906ec7（~/ai/pi），与 `ai-dev/analysis/compare-agent-design/01-code-map.md` 一致；dsh/pi 锚点行号实测于当日 HEAD

## Context

- 为什么需要：roadmap Cross-Cutting 语义对齐约定要求"Java 与 TS 范式差异统一按 WI3 术语表翻译，禁止直译制造伪差异"。三方对同一机制常用不同词（nop `iteration` ≈ dsh `step` ≈ pi 无独立词），也有同词不同义（三方都有 "session/checkpoint/hook"，语义各不相同）。
- 输入：WI2 矩阵 46 个子机制（D1-1..D10-4）的概念族归并；dsh/pi 侧按 HEAD 逐文件实测调研（2026-09-12）；nop 侧经当日代码复核（HookResult 实为 4 态、steering 由 Actor 承担等，见各表注记）。
- 消费方式：WI4–WI27 报告中凡引用三方机制，命名以本表为准；本表未覆盖的新概念在发现 WI 登记并回写本表（后续 WI 可追加节）。

## 翻译口径总则

1. **对位按语义，不按词面**：先判定子机制语义等价（本表映射），再比较设计取舍；词面相同但语义不同（如三方 "checkpoint"）必须在本表"伪差异警示"节核对。
2. **双方皆无 → 裁定"双方均无"**：某子机制一方无对应概念时，报告直接裁定"双方均无"，不得用邻近概念硬充等价物（例如 pi 无内置 subagent，不得拿 example extension 充当内置对位，只能作为"设计取舍证据"引用）。
3. **保留原名 + 首次出现给映射**：报告正文术语保留英文原名，首次出现用"（≈ nop `<对位>`）"标注映射；之后可只用原名。
4. **能力级别词（observe/transform/veto/abort-bail/inject）是本对比的自建度量词**，三方原生词表里没有这个词表；报告引用时标注"（能力分级，见 04 专项）"，不得当作三方原生术语。
5. Java 泛型/接口族 vs TS 判别联合/函数类型的范式差异不构成差异结论（如 dsh `TurnEndReasonMap` 声明合并 ≈ nop 枚举 + 开放扩展点的组合），按"机制等价、形态不同"记述。

## T1 主循环层级（WI2 矩阵 D1-1）

| 语义角色 | nop | dsh | pi |
|---|---|---|---|
| 最外层活动 | execution（`ReActAgentExecutor.execute`，外层 `while(true)` + sustain 逻辑） | run/driver（`kick` 内 `while (await this.turn())`） | run（`agentLoop`/`runAgentLoop` 一次调用；外层 while 只服务 follow-up 排空） |
| 回合 | （无独立回合词；execution 即一次任务执行） | turn（`turn()`：append `turn/start`…`turn/end` + TurnEndReason） | turn（`turn_start`/`turn_end` 事件界定：一次 assistant 响应 + 其工具批） |
| 一步 | iteration（`reactLoop` 内层 `while (ctx.getCurrentIteration() < ctx.getMaxIterations())`，一步 = 一次 LLM 调用 + 工具 fan-out） | step（一次模型调用 + 它请求的全部工具执行；`step/start`/`step/end`） | （无独立词；内层 while 的一轮 = 一次 LLM 响应 + 工具批，不落事件） |
| 循环驱动器 | `ReActAgentExecutor`（另有 `SingleTurnExecutor` 单轮策略） | `ReactLoopAgent`（per-session 状态机）；`AgentLoop` 是 Service/工厂**不是循环体** | `runLoop`（agent-loop.ts 共享循环体）；`Agent` 类是**有状态包装器，包着 runAgentLoop** |

- 锚点：nop `nop-ai/nop-ai-agent/.../engine/ReActAgentExecutor.java:441-444`；dsh `packages/core/agent-loop/src/agent.ts:225-238（kick）、:269-343（turn）、:352-498（step）`、`packages/core/agent-loop/src/index.ts:296（AgentLoop）`；pi `packages/agent/src/agent-loop.ts:155（runLoop）、:170（外层 while）、:174（内层 while）`、`packages/agent/src/agent.ts:167-173`。
- 语义注记：nop 的 `iteration` 是**计数量词**（受 maxIterations 约束），dsh `step` 是**事件括号**（不计数、无上限），pi 内层循环轮次甚至不落事件——对比步数语义时三者不对位，禁止写成"nop iteration = dsh step = pi turn 内层"。
- Java 对照警示：dsh `AgentLoop` ≈ nop `DefaultAgentEngine`（门面/工厂）而非循环体；pi `Agent` ≈ 有状态 session-bound 实例（更接近 nop `AgentExecutionContext` + 引擎的合体）。

## T2 终止语义（D1-2）

| 语义角色 | nop | dsh | pi |
|---|---|---|---|
| 回合结束原因 | （无枚举；由 `ICompletionJudge` 判定 + `AgentExecutionResult` 终态承载） | `TurnEndReasonMap` 6 内置变体：`completed`/`aborted`（+CancelCause user\|parent\|hook\|disposed）/`blocked`/`error`/`max-tokens`/`interrupted` | `stopReason` 7 值：`pending`/`stop`/`length`/`toolUse`/`error`/`aborted`/`deferred` |
| 完成判定器 | `ICompletionJudge`（RuleBased/Llm 两实现）——显式"判定是否完成"组件 | 无判定器组件：无新工具调用即自然收口；`concludesTurn` 工具结果标记可终结 turn | 无判定器组件：无工具调用即停；`shouldStopAfterTurn` hook 可优雅停止；工具结果 `terminate:true` 全批才早停 |
| 收口异议 | （无对位） | `agent/turn-stopping` serial 事件：监听者用 `agent.steer()` 反对即续跑（数据决定，非顺序） | （无对位；`prepareNextTurn` 可替换下一轮状态但不是异议） |

- 锚点：nop `engine/ICompletionJudge.java`、`completion/RuleBasedCompletionJudge.java`；dsh `packages/core/session/src/types.ts:200-224（TurnEndReasonMap）、:187-195（CancelCause）`、`packages/core/tools/src/index.ts:420（concludesTurn）`、`packages/core/agent/src/runtime-types.ts:377-381（"Data decides" Javadoc）`；pi `packages/ai/src/types.ts:405（stopReason）`、`packages/agent/src/types.ts:222（shouldStopAfterTurn）、:61-69（BeforeToolCallResult.terminate）`。
- 语义注记：dsh `max-tokens` 是**粘性**的（一旦触顶，turn 结果不能降级回 completed）；dsh `interrupted` **只由崩溃修复合成**（≠ 用户取消）；pi `length` 触发整批工具调用判废回填错误结果（不执行）；pi `deferred` 是 provider 异步句柄协议（`DeferredHandle`，无 nop/dsh 对位）。
- 词同义异警示：三方 "stop" 粒度不同——nop `FORCED_STOP`（系统强制）、dsh `turn-stopping`（收口前异议点）、pi `stopReason:"stop"`（自然停）。不可互译。

## T3 迭代上限与延长（D1-3）

| 语义角色 | nop | dsh | pi |
|---|---|---|---|
| 步数上限 | `AgentConstraintsModel.maxIterations`（`AgentLoopGuard` 守护） | 无（grep maxTurns/maxSteps 零命中；inbox 排空即停） | 无（AgentLoopConfig 无上限字段；应用层只有重试预算 retry.maxRetries=3，不限制正常步数） |
| 超限延长 | `ISustainer.onStop`（唯一 sustainable 出口 `SustainStopReason.MAX_ITERATIONS`）：`SustainDecision.CONTINUE` 强制续跑一个 sustain round（重新计满 maxIterations）| （无上限故无延长） | （无上限故无延长） |

- 锚点：nop `engine/AgentLoopGuard.java`、`reliability/ISustainer.java:11-67`、`reliability/SisypheanSustainer.java`、`engine/ReActAgentExecutor.java:420-430（sustain round 预算注释）`；pi `packages/coding-agent/src/core/settings-manager.ts:878-884（retry.maxRetries）`。
- 翻译口径：三方的"循环何时停"根本机制不同（nop 计数器判定、dsh/pi 队列排空判定）。roadmap 初步假设"三者都无硬性步数上限"**不成立**：nop 有 maxIterations（可被 sustainer 延长），WI8/WI18 报告须按此修正表述。

## T4 流式输出（D1-4）

| 语义角色 | nop | dsh | pi |
|---|---|---|---|
| 流式原语 | REASONING_CHUNK lifecycle point + `LLM_RESPONSE_RECEIVED` 事件（hook 粒度，无 token 级事件类型） | `StreamChunk` 7 变体（block-start/text-delta/reasoning-delta/tool-call-delta/block-end/usage/finish） | `AgentEvent.message_update`（内嵌 pi-ai `assistantMessageEvent` 11 变体 delta） |
| chunk 持久化 | （不持久化 token 级流） | **持久化**：流式经 AssistantStreamAttempt 累积+`agent/assistant-stream` 瞬时帧，结算一次性持久化 `assistant/attempt` 或含完整 stream 记录的 `assistant/message`（c291e7961a 结构迁移，log-only） | 不持久化 delta；持久化的是 message_start/end 后的完整消息 |
| 流容器 | （经 IChatService 流式接口） | `BlockAssembler`（chunk→内容块；中断落 `interrupted:true` 消息） | `EventStream<AgentEvent,R>`（push/异步迭代/promise 三消费形态）；`AgentEventSink` 顺序 await |

- 锚点：nop `hook/AgentLifecyclePoint.java:11（REASONING_CHUNK）`、`engine/AgentEventType.java（LLM_RESPONSE_RECEIVED）`；dsh `packages/llm/llm/src/types.ts:390-403（StreamChunk）、:130-139（FinishReasonMap）`、`packages/core/session/src/types.ts:335（assistant/attempt）`、`packages/core/agent-loop/src/agent.ts:389-431`；pi `packages/agent/src/types.ts:428-443（AgentEvent 10 变体）、:437-438（message_update）`、`packages/ai/src/types.ts:535-547`、`packages/ai/src/utils/event-stream.ts:4`。
- 语义注记：dsh 是三方中唯一把模型流整流记录持久化的（结算一次性落 `assistant/attempt` 或含完整 stream 记录的 `assistant/message`，token-level replay fidelity；c291e7961a 结构迁移）；nop 流式暴露面在 hook 层（REASONING_CHUNK），事件枚举无 chunk 值——WI10 对比事件词表时按此口径，不得把 nop `LLM_RESPONSE_RECEIVED` 译成"chunk 事件"。
- **勘误（WI4 实测回写，2026-09-12）**：nop `REASONING_CHUNK` lifecycle point 与 `ITERATION_STARTED` 事件当前**已声明但无任何触发/发布点**；LLM 调用唯一路径是同步非流式 `IChatService.call`（`callStream` 不被 agent 引擎消费）——**nop 当前无流式执行路径**（详见 03-flow-agent-loop.md ④-8）。WI8/WI18 对比 D1-4 时 nop 侧按"无"处理。

## T5 输入注入 / steering（D1-5）

| 语义角色 | nop | dsh | pi |
|---|---|---|---|
| 运行中插话 | Actor 级 `steeringQueue`（`Queue<ChatMessage>`，经 `InMemoryActorRuntime` steering-injection consumption loop 注入执行上下文） | `steer(msg)` → inbox `next-step` 列表（最近 step 边界消费并唤醒） | `steer(msg)` → steering 队列（当前 assistant turn 结束后、下次 LLM 调用前注入，不打断当前工具批） |
| 停机后排队消息 | （无对位；Actor 消息走 messenger/mailbox） | `followup(msg)` → inbox `next-turn`（成为新独立 turn 的唯一普通消息） | `followUp(msg)` → follow-up 队列（agent 本该停止时注入并续跑——外层循环燃料） |
| 静默上下文注入 | （无对位） | `inject(msg)` → inbox `next-step` 不唤醒（文件变更/AGENTS.md/skill 内容） | （无对位；扩展 `sendMessage` 有第三档 `nextTurn` 只入队不触发） |
| 队列模式 | （无对位） | （无对位；splice 事件溯源 + `next-turn`/`next-step` 双边界） | `PendingMessageQueue` + `QueueMode`：`all`（一次排空）|`one-at-a-time`（默认，每次 drain 取最旧一条） |

- 锚点：nop `runtime/AgentActor.java:90,218-235（steeringQueue）`、`runtime/InMemoryActorRuntime.java:52-64,440-441`；dsh `packages/core/agent-loop/src/inbox.ts:111-115（claim）、:238（spliced 事件）`（三语义契约声明于 `packages/core/agent/src/runtime-types.ts:215-241`）、`packages/core/agent-loop/src/agent.ts:137-147（followup/steer/inject）`；pi `packages/agent/src/agent.ts:125-159（PendingMessageQueue）、:231-232（默认 one-at-a-time）、:283（steer）、:288（followUp）`、`packages/agent/src/types.ts:44-50（QueueMode）`。
- 语义注记：nop steering 是 **opt-in 的 Actor 附加能力**（engine 绑定 ctx steering queue 后才生效），dsh/pi 是**一等循环机制**；dsh `inject` 与 pi `steer` 都不唤醒/不打断，但 dsh 多出"不唤醒注入"独立词。词同义异警示：`steer` 三方都不是 abort——不打断当前执行，只在边界消费。

## T6 扩展机制词表（D2-1/D2-2）

| 机制面 | nop | dsh | pi |
|---|---|---|---|
| 主扩展原语 | `AgentLifecyclePoint` 枚举 12 点（PRE_CALL/PRE_REASONING/POST_REASONING/PRE_ACTING/POST_ACTING/ON_ERROR/POST_CALL/REASONING_CHUNK/PRE_COMPACT/POST_COMPACT/BEFORE_TOOL_RESULT_PROCESSED/AFTER_TOOL_RESULT_PROCESSED）+ `IAgentLifecycleHook`/`IHookRegistry` | Cordis 事件五种 `DispatchMode`：`emit`/`parallel`/`serial`/`bail`/`waterfall`；`agent/*` 12 事件、`tools/*` 6 事件、`llm/stream`、`system-prompt/assemble` 等 | 第一层：`AgentLoopConfig` 配置级 hook（convertToLlm/transformContext/getApiKey/shouldStopAfterTurn/prepareNextTurn/getSteeringMessages/getFollowUpMessages/toolExecution/beforeToolCall/afterToolCall，**每类单槽无链**） |
| 尝试级拦截 | `ExecutionPoint` 4 点（PRE/POST_LLM_ATTEMPT、PRE/POST_TOOL_ATTEMPT）+ `IAgentMiddleware`/`MiddlewareChain`（EXECUTION scope，随重试重跑） | （无独立层；`agent/request` waterfall 兼职改配置，`tools/execute` waterfall 是 around 包裹） | 第二层：`ExtensionAPI` 的 `pi.on(<event>)` 多播 hook（25 联合成员/34 type 标签/48 具体事件接口） |
| 安全过滤链 | `SecurityCheckpointChain` 7-checkpoint（security 包 filter chain） | `tools/pre-execute`（审批 allow/deny/ask）+ sandbox confine | （核心无权限层；`tool_call` 事件 block 充当审批，官方 example confirm-destructive.ts） |
| 资源注册 | `IContributionRegistry`（7 类 ContributionType）+ `tool.xdef` DSL 声明 | Service 注入（`static inject`）+ scope 层叠注册（`ctx.systemPrompt.section()`） | 第三层：`registerTool/registerCommand/registerShortcut/registerFlag/registerProvider` 等（热注册，加载期入 pending 队列） |

- 锚点：nop `hook/AgentLifecyclePoint.java:4-14`、`middleware/ExecutionPoint.java`、`middleware/IAgentMiddleware.java`、`hook/IHookRegistry.java`、`security/SecurityCheckpointChain.java`、`contribution/IContributionRegistry.java`；dsh `vendor/cordis/src/events.ts:32（DispatchMode）、:194-242（三 mode 实现）`、`packages/core/agent/src/runtime-types.ts:245-404（agent/* 事件全表）、packages/core/tools/src/index.ts:143-207（tools/*）`、`packages/llm/llm/src/index.ts:64（llm/stream）`；pi `packages/agent/src/types.ts:149-293（AgentLoopConfig）`、`packages/coding-agent/src/core/extensions/types.ts:1050-1075（ExtensionEvent）、:1219-1261（pi.on 34 重载）、:1268-1456（register*）`、`packages/coding-agent/src/core/extensions/runner.ts`。
- 语义注记：dsh 扩展机制**没有统称词**（vocabulary = 事件 mode + Service + scope），"插件框架"是 Cordis 基础设施不是 agent 概念；pi 三层中配置级 hook 是**单槽**（多扩展靠 coding-agent 组合函数串链，runner.ts 实现），ExtensionAPI 是多播。nop 双层（session 级 12 点 vs attempt 级 4 点）显式分 scope，是三方中唯一在类型上区分会话/尝试粒度的。
- 翻译口径：nop "hook"（生命周期点回调）≈ dsh "waterfall 事件监听者" ≈ pi ExtensionAPI "事件 handler"——**注册形态与分发语义完全不同**（见 T7、S3/S4 专项），报告只能写"扩展点对位"，禁止写"等价于"。

## T7 能力语义与结果对象（D2-3/D2-4；S2 能力分级的词面基础）

| 能力（自建五级） | nop 原生词 | dsh 原生词 | pi 原生词 |
|---|---|---|---|
| observe（仅监听） | lifecycle hook 返回 `HookResult.pass()`；`AgentEventType` 事件订阅 | `emit` 类事件（agent/status、tool/result 等，void 不可改写） | `pi.on` 通知类事件（agent_start/turn_start 等，无返回值消费） |
| transform（改写内容） | middleware `AttemptContext` 携带改写；`IOutputGuardrail` BLOCK/MODIFY 链 | waterfall 返回值即权威（`agent/request` 替换 LlmCallConfig；`tools/post-execute` accept 替换渲染内容；`system-prompt/assemble` 返回 PromptAssembly） | `afterToolCall` 返回 `AfterToolCallResult`（content/details/isError/usage 浅覆盖）；扩展 `message_end` 同 role 替换；`before_provider_request` 整体替换 payload |
| veto（否决跳过） | `HookResult.veto()`；ExecutionPoint Veto→进重试决策（PRE_LLM_Veto 视为失败，tool Veto→该工具 error 结果不影响同批其他） | `agent/pre-step` 返回 `{kind:'reject'}`；`tools/pre-execute` deny(reason)（**禁改参数**）；不调 `next()` 即否决链上其余 | `beforeToolCall` 返回 `{block:true, reason}`→工具不执行回填 error 结果；扩展 `tool_call` block |
| abort-bail（中断轮次） | `HookResult.bail()`（W5-3 hard-block）；middleware veto cap 防无限循环 | `serial` mode 的 bail 值（非 null/false/undefined 即停）；`agent/request-error` 返回 undefined=失败终局 | hook 异常→变 error 工具结果不中止 run；`terminate:true` 全批才早停（软终止） |
| inject（注入内容） | （REASONING_CHUNK 仅观察；无 hook 级注入词） | `agent/pre-step` enter 可重写进入消息；`agent/inbox/spliced` 注入机制见 T5 | 扩展 `before_agent_start` 附加消息/整条替换 systemPrompt；`context` 事件替换 messages；`transformContext` 改写送 LLM 前消息数组 |

- 锚点：nop `hook/HookResult.java（Pass/Veto/Reenter/Bail 四态内部类）`、`middleware/ExecutionPoint.java（veto 语义 Javadoc）`、`guardrail.rule/RuleGraphGuardrail.java（BLOCK/MODIFY）`；dsh `packages/core/agent/src/runtime-types.ts:53-58（PreStepDecision/RequestErrorAction）、:232-244（agent/request）、packages/core/tools/src/index.ts:588-604（Pre/PostToolDecision）`、`vendor/cordis/src/events.ts:8-13（isBailed）、:204-211（serial）`；pi `packages/agent/src/types.ts:61-69（BeforeToolCallResult）、:84-95（AfterToolCallResult）、:277（beforeToolCall）、:292（afterToolCall）`、`packages/agent/src/agent-loop.ts:600-668（block 分支）、:724-751（afterToolCall 异常处理）`、`packages/coding-agent/src/core/extensions/types.ts:914-928（tool_call 就地改 event.input）、:1085-1096`。
- 语义注记（WI5 以代码为准逐点核证，本表只给词面映射）：nop 是三方中唯一有**显式 Bail 硬阻断态**的（WI2 矩阵锚点候选漏记 Bail，已随本表勘误）；dsh 否决是**结构化返回值**（Decision 判别联合）+ waterfall 不调 next；pi 配置级 hook 与扩展事件的否决/改写能力面**不同**（配置级 beforeToolCall 不能改参数，扩展层 tool_call 可就地 mutate event.input）。
- 重要勘误（对 WI2 矩阵）：`HookResult` 实为 **Pass/Veto/Reenter/Bail 四态**，矩阵 D2 锚点候选"HookResult（Pass/Veto/Reenter）"应读作四态；Reenter（重入当前 hook 点，重入次数 per-iteration 计数封顶）是 nop 独有态，dsh/pi 无直接对位。

## T8 事件体系：持久化 vs 瞬时（D3）

| 语义角色 | nop | dsh | pi |
|---|---|---|---|
| 进程内瞬时事件 | `AgentEventType` 枚举（EXECUTION_STARTED/ITERATION_STARTED/LLM_RESPONSE_RECEIVED/TOOL_CALL_*/SESSION_* 等 19+ 值）+ `IAgentEventPublisher`/`DefaultAgentEventPublisher` | Cordis `emit` 事件（agent/status、tool/result、session/event post-commit 馈送等） | `AgentEvent` 判别联合 10 变体（agent_start..tool_execution_end）+ `AgentSessionEvent` 扩展（auto_retry_start/end、compaction_start/end、queue_update 等） |
| 持久化事件 | （无事件溯源；状态经 `ISessionStore` 快照 + `CheckpointJournalWriter` 分录持久化） | `SessionEvent`：核心声明 13 类 + 声明合并扩展，本 build `KNOWN_SESSION_EVENT_TYPES` 共 **56 类**；append-only 日志即真相 | 会话条目即持久化层：v3 `SessionEntry` 9 成员 / v4 `Entry` 7 成员 + `LaneRecord` 9 成员（见 T13） |
| 持久化 vs 瞬时分界 | 瞬时事件不落盘；持久面是**快照 + journal 分录**（LLM_TURN/TOOL_EXECUTION/COMPACTION/WAIT_FOR 四类） | 二元分明：`log-only` 事件不投影到 surface（chunk、compaction/start 等），surface 事件才进模型可见历史 | AgentEvent 瞬时不落盘；落盘的是 SessionEntry/LaneRecord（事件与条目不同层） |
| UI 桥接 | （经事件订阅者，无内置 TUI） | `session/event` emit → UI 订阅；SurfaceManager 投影 | AgentEvent sink → TUI/RPC/print 三模式；`EventStream` 异步迭代 |

- 锚点：nop `engine/AgentEventType.java`、`engine/IAgentEventPublisher.java`、`reliability/CheckpointJournalWriter.java`；dsh `packages/core/session/src/types.ts:269-401（SessionEventMap）、:465-488（SessionEvent 信封 + ignorable 标记）、packages/core/session/src/known-event-types.ts:22-79`、`packages/core/session/src/index.ts:76,85（session/event、session/flush）`；pi `packages/agent/src/types.ts:428-443`、`packages/coding-agent/src/core/agent-session.ts:144-185（AgentSessionEvent）`、`packages/agent/src/harness/session/types.ts:203-212（LaneRecord）`。
- 语义注记：dsh `ignorable` 信封标记 = 未知事件类型可跳过（词汇增长不 bump 格式版本的兼容机制），nop/pi 无对位；nop 持久化单位是 checkpoint journal 分录而非事件（WI16 注意口径：nop"事件溯源"不成立，是"快照 + 分录账本"）。
- **勘误（dsh c291e7961a 结构迁移，2026-09-12 WI8-WI12 audit 发现并同步）**：assistant/chunk→assistant/attempt 整流记录、inbox 迁至 agent-loop/src、session-persistence coordinator 删除（合成收尾上移 agent-loop）、KNOWN_SESSION_EVENT_TYPES 48→56。

## T9 错误分类与重试（D4-1/D4-2）

| 语义角色 | nop | dsh | pi |
|---|---|---|---|
| 错误基类/错误码 | `NopException` + `ErrorCode`（框架两级错误策略；agent 模块 `NopAiAgentErrors`） | `HarnessError`（稳定 `code` 机器路由码 ≠ message；"route on code, never parse message"） | （无统一错误基类；正则分类器直接匹配 provider 文案） |
| 错误分类 | `ErrorClassification` 6 值：TRANSIENT/RATE_LIMITED/NON_TRANSIENT/QUOTA_EXCEEDED/AUTH_INVALID/CACHE_STATE_LOST（cause 链解包） | 规范码：CONTEXT_WINDOW_EXCEEDED/QUOTA/EMPTY_RESPONSE/INVALID_CREDENTIAL/NO_ADAPTER…；默认可重试集 EMPTY_RESPONSE/RATE_LIMIT/SERVER/TIMEOUT/TRANSPORT | 双正则表：`NON_RETRYABLE_PROVIDER_LIMIT_ERROR_PATTERN`（配额/账单，先查）+ `RETRYABLE_PROVIDER_ERROR_PATTERN`（~40 条）；**溢出独立第三类** `isContextOverflow`（27 条正则 + 静默溢出检测） |
| 重试策略 | `StandardRetryPolicy`（指数退避全抖动 + Retry-After floor）+ `IRetryPolicy` | `RetryPolicyConfig`：`normal`（有界，默认 5 次）|`always`（对一切失败无限重试）；策略归 provider 所有（PreparedLlmCall 冻结）、执行归 llm-retry 插件 | 双层：传输层 `retryProviderRequest`（SDK 镜像：x-should-retry 头/408/409/429/5xx/retry-after 上限）+ 应用层 auto-retry（`_prepareRetry` 摘除 error 消息 + 指数退避 + `agent.continue()`；`retryAssistantCall` 仅存在于未接线的 v4 harness 路径——WI4 勘误） |
| 退避细节 | 全抖动（uniform jitter），Retry-After 为下限 floor | `initialDelayMs * 2^min(retry-1,1024)` 封顶 maxDelayMs × 对称抖动；providerRetryAfterMs 有效则优先、超 maxDelay 则 normal 模式放弃 | `baseDelayMs*2^(attempt-1)`；退避中被 abort 归一为 aborted |

- 锚点：nop `nop-ai/nop-ai-core/.../reliability/LlmErrorClassifier.java`、`reliability/StandardRetryPolicy.java`；dsh `packages/llm/llm/src/error.ts:17-27,29,32,38,45,70-115`、`packages/llm/llm/src/retry-policy.ts:19-56`、`packages/llm/llm-retry/src/index.ts:59-64,126-137,188-190,194-205,210-219`；pi `packages/ai/src/utils/retry.ts:7,26,98,163-212,223-228`、`packages/ai/src/utils/provider-retry.ts:22-35,105-125`、`packages/ai/src/utils/overflow.ts:37,74,134-163`。
- 语义注记：dsh 重试状态**持久化在事件日志**（`llm/retry` 等待前先写 + 重启经 sessionProjections 事件折叠恢复计数），三方唯一；dsh `always` 无限重试模式是 Java 框架罕见的激进设计；pi 重试对象是"整条 assistant 消息重生成"不是 HTTP 请求；pi 溢出先于重试判断且独立走压缩恢复（一次性门闩 `_overflowRecoveryAttempted`）。

## T10 中断、取消与部分失败（D4-3/D4-4/D4-5）

| 语义角色 | nop | dsh | pi |
|---|---|---|---|
| 取消 | `SESSION_CANCEL_REQUESTED`/`SESSION_CANCELLED` 事件（IWaitCoordinator/挂起语义） | `aborted` TurnEndReason + `TurnEndCancelCause`（user/parent/hook/disposed） | `abort()` + stopReason `aborted`；退避中 abort 归一为 aborted AssistantMessage |
| 工具中止合成 | （工具无重试；veto 产出 error 结果） | 未派发 call 合成 error result："aborted before dispatch"（码 ABORTED_BEFORE_DISPATCH/ABORTED）——**中止调用必须有结果对**（provider 拒 dangling tool-call） | 工具 throw → `createErrorToolResult` + `isError:true` 正常进 transcript |
| 失败升级 | 三级失败升级 + `IDenialLedger`（阈值 3 → `SESSION_PAUSED` governance pause）+ `IGoalTracker` 卡死检测 → `SESSION_ESCALATED`（terminal） | （无对位；错误走 turn `error` 收口） | （无对位；重试耗尽即 finalError，`auto_retry_end` 带 success/finalError） |
| 崩溃恢复合成 | checkpoint journal 消费 + `IRecoveryManager` 60s 扫描 + `SESSION_RESTORED` | `interrupted` reason 只由崩溃修复写入；`interruptedTurnClosers` 确定性合成收尾（补 error result → step/end → turn/end），码 TOOL_NOT_STARTED/TOOL_OUTCOME_UNKNOWN | v4 `findOpenOperations` 崩溃恢复 + SuspendedOperation（lane 级） |

- 锚点：nop `engine/AgentEventType.java（SESSION_PAUSED/ESCALATED/RESTORED Javadoc）`、`security/IDenialLedger.java`、`reliability/IGoalTracker.java`、`runtime/recovery/ScheduledRecoveryManager.java`；dsh `packages/core/agent-loop/src/tool-calls.ts:250-260`、`packages/core/tools/src/index.ts:469,472`、`packages/core/session/src/repair.ts:14-18,105-107`、合成收尾接线 `packages/core/agent-loop/src/index.ts:892-893`（合成器本体 `packages/core/session/src/repair.ts:29`；session-persistence coordinator.ts 已删除）；pi `packages/agent/src/agent-loop.ts:701-707,760-765`、`packages/coding-agent/src/core/agent-session.ts:168-169,2776-2835`、`packages/agent/src/harness/session/jsonl/repo.ts（findOpenOperations）`。
- 语义注记：nop 的 paused（governance 可恢复）与 escalated（terminal 需人工）是**语义不同的两个态**（事件 Javadoc 明示），翻译成英文 "paused/stopped" 会丢掉这个区分——WI11 报告保留原名。dsh 的崩溃修复合成事件时间戳复用最后真实事件（确定性重放）。

## T11 故障切换（D5）

| 语义角色 | nop | dsh | pi |
|---|---|---|---|
| 内置 failover | **有**：`LlmCallCoordinator` 重试→熔断→账号链→`ProviderFailoverChain`→`SmartModelRouter` 分级路由 | **无**（全仓 grep failover 零命中）；表达方式 = `agent/request` waterfall 返回替换 LlmCallConfig（改 provider/model 字段） | **无**（设计立场）；`model_select` 事件（set/cycle/restore）由用户/扩展驱动 + `registerProvider` 热注册换端点 |
| 熔断 | `ThresholdBreaker`（CLOSED/OPEN/HALF_OPEN，阈值 3/60s 冷却，懒探针） | （无对位） | （无对位） |
| 账号/配额 | `IAccountChainResolver`/`AccountChain`（QUOTA/AUTH 同模型换 key）+ `IBudgetProvider`/`IResourceGuard` | 配额仅分类（QUOTA 码）不转移 | `getApiKey` per-call 解析（支持过期 OAuth）；配额错误不可重试；`Model.compat.allowedFallbackModels`→Anthropic 原生 `params.fallbacks`（API 协议特性非 agent 层） |
| 模型路由 | `SmartModelRouter`（Complexity 分级 + 预算降级 + fallback 链） | `installModelSelection`（system-prompt/assemble + request 两 scoped 监听成对切换，防 prompt/路由撕裂） | `prepareNextTurn` hook 可换 model/context/thinkingLevel（单点替换非故障转移） |

- 锚点：nop `engine/LlmCallCoordinator.java`、`nop-ai-core/.../reliability/ThresholdBreaker.java`、`reliability/IAccountChainResolver.java`、`reliability/IProviderFailoverChainResolver.java`、`router/SmartModelRouter.java`；dsh `packages/core/agent/src/runtime-types.ts:232-244`、`packages/core/agent/src/model-selection.ts:33 起`、`packages/llm/llm-deepseek/src/index.ts:86（retryPolicy 配置）`；pi `packages/coding-agent/src/core/agent-session.ts:1573-1600（model_select/setModel）`、`packages/coding-agent/src/core/extensions/types.ts:1388-1456（registerProvider）`、`packages/ai/src/api/anthropic-messages.ts:1107-1109（fallbacks）`。
- 语义注记：roadmap 初步假设"nop 是三者中唯一内置多通道切换"**成立**（当日复核确认）。dsh/pi 的"无"都是显式设计立场而非缺失——报告按"留白给扩展/用户"表述，不写"落后"。

## T12 前缀缓存利用（D6）

| 语义角色 | nop | dsh | pi |
|---|---|---|---|
| 显式断点 | （已核查，详见 dsh-D6 ②：断点通道空置——providerHints 可透传 cache_control 但无生产写入方；无显式 cache_control 对位词） | （无显式断点 API；构造性稳定替代） | `cache_control {type:"ephemeral"}` 三断点：system 块 / 最后一个工具定义 / 最后一条 user 消息最后块 |
| 前缀稳定构造 | （已核查，详见 dsh-D6 ②：前缀两不稳定源——记忆进 system+工具 HashSet 序） | `PromptSection` 数值 order 约定（-100 身份/0 persona/100-199 工具）；动态上下文**不进 system prompt**——渲染成 user-role 快照消息（"This snapshot supersedes earlier…"）且只保留 surface 上最后一份 | 工具集变化才重建 system prompt（`setActiveToolsByName`→`_rebuildSystemPrompt`）；工具注册表 Map 插入序稳定 |
| 压缩与缓存交互 | （已核查，详见 dsh-D6 ②：压缩交互不存在） | 压缩摘要调用**字节级重放对话自己的前缀**（同 system+tools+消息前缀，压缩指令作最后 user 消息）→ KV cache 保持温热；`EpochHeader`（request/header）记账 config+system+tools 快照 | 摘要请求强制 `cacheRetention:"none"` + 全新 sessionId（**禁写缓存**防污染）；`cacheRetention` 三档 none/short/long（long→ttl 1h）+ sessionId 缓存路由 |
| 命中观测 | （已核查，详见 dsh-D6 ②：观测解析未消费） | （无显式 stats；`headerEquals` reason:change 间接记账） | `CacheMiss`（missedTokens/missedCost/idleMs/modelChanged，1024 token 噪声地板 + 5min TTL）+ `computeCacheWaste` |
| 辅助调用标记 | （已核查，详见 dsh-D6 ②：一次性标记不存在） | `GenerateOptions.purpose: 'compaction'|'session-title'` | （无 purpose 词；用 retention+sessionId 组合表达） |

- 锚点：nop `compact/PipelineCompactor.java`、`engine/ChatOptionsHelper.java`（现状核查起点）；dsh `packages/core/system-prompt/src/index.ts:53-75,77-85,164-178,236-240`、`packages/core/agent-loop/src/runtime-context.ts:23,64`、`packages/core/session/src/types.ts:232-261（EpochHeader）`、`packages/compaction/compaction-basic/src/index.ts:226-246`、`packages/llm/llm/src/types.ts:371-377（purpose）`；pi `packages/ai/src/api/anthropic-messages.ts:1295-1320,1343-1361,1002-1022,50-71`、`packages/coding-agent/src/core/agent-session.ts:938-955,1034-1067`、`packages/coding-agent/src/core/cache-stats.ts:7-11,56-71,138`、`packages/agent/src/harness/compaction/compaction.ts:110-115`。
- 语义注记：dsh 与 pi 是**两种正交策略**——dsh 靠结构约定让前缀天然稳定（无 cache API），pi 显式管理断点/TTL/路由/观测。roadmap 初步假设"pi 拥有最显式的 prefix-cache 工程化设计"**成立**。nop 侧待核查项已由 WI13 兑现（dsh-D6 ② 五项核查，已回写本表 nop 列）。

## T13 工具系统（D7）

| 语义角色 | nop | dsh | pi |
|---|---|---|---|
| 声明与 schema | `tool.xdef` DSL 声明（模型驱动） | `defineTool`：name/description/parameters（隐式根对象 schema）/`output{schema,render}`（canonical lossless-JSON 值与模型可见投影分离）/timeoutMs（不下发模型）/execute/finalizeContent/presentCall/presentResult | `AgentTool`：继承 pi-ai `Tool`（name/description/**TypeBox schema**）+ label/`prepareArguments`（校验前垫片）/execute(toolCallId,params,signal,onUpdate)/`executionMode?` |
| 调度 | `AgentToolDispatcher`（activeTags/denyTags/denyTools 标签过滤；批内 fan-out） | `ToolExecutionMode` parallel|exclusive：仅 `isConcurrencySafe(args)`===true 入 parallel；有界滚动池 `maxParallelToolCalls`（默认 10，实现在 tool-calls.ts:199-214——WI4 锚点更正，agent-loop/index.ts:236-252 是 settings schema）；**exclusive 调用是屏障**；结果按模型顺序提交 | config `toolExecution` 默认 `"parallel"`、单工具 `"sequential"` 覆盖；并行=准备串行+执行并发；end 事件按完成序、结果消息按源序 |
| 结果回填 | （工具结果经 hook `BEFORE/AFTER_TOOL_RESULT_PROCESSED` 处理后回填） | `tools/result` emit（冻结快照）；surface replace 只允许改 content | `AgentToolResult{content[],details,usage?,addedToolNames?,terminate?}`；约定 throw 表失败，不在 content 编码错误 |
| 修复链 | `ChainRepairer` 4 阶段（名称规范化/参数结构/类型强转/schema 清理）+ `IToolCallRepairer` | （无修复链；`INVALID_PREPARED_CALL` 码） | `prepareArguments` 垫片（校验前兼容转换，非修复链） |
| 审批/沙箱 | `SecurityCheckpointChain` 7-checkpoint（含 approval gate + `IPathAccessChecker` + `ISandboxBackend` fail-closed） | `tools/pre-execute` allow/deny/ask 瀑布（ask 无审批服务时降级 deny）；`SandboxProvider.confine(argv, policy)` 返回禁闭 argv + denial 方言签名；SANDBOX_UNAVAILABLE fail-closed | **核心无权限层**（留白给扩展 `tool_call` block）；`withFileMutationQueue` 按 (env,canonical path) 串行化同文件变更 |
| 特有机制 | call-agent/send-message/team 五工具（工具化多代理入口） | code-mode（`run_code` 子派发，`tools/code-dispatch-log` 只改日志副本） | deferred tools：结果带 `addedToolNames` → 中途引入新工具定义（Anthropic `defer_loading:true`） |

- 锚点：nop `engine/AgentToolDispatcher.java`、`repair/ChainRepairer.java`、`security/SecurityCheckpointChain.java`、`security/ISandboxBackend.java`；dsh `packages/core/tools/src/schema.ts:483-545（DefineToolOptions/defineTool）、packages/core/tools/src/index.ts:222-288,344-347,404-421,469-472,588-604`、`packages/core/agent-loop/src/tool-calls.ts:84-101,199-214`、`packages/core/agent-loop/src/index.ts:236-252`、`packages/sandbox/sandbox/src/index.ts:29,62-72,90-116,124-144,158-176`、`packages/guard/timeout-policy/src/index.ts:20-56`；pi `packages/agent/src/types.ts:386-409（AgentTool）、:361-375（AgentToolResult）、:34-42`、`packages/agent/src/agent-loop.ts:411-426,489-554,600-668,787`、`packages/ai/src/utils/deferred-tools.ts:8-39`、`packages/agent/src/harness/tools/file-mutation-queue.ts:29-56`、`packages/coding-agent/examples/extensions/confirm-destructive.ts`。
- 语义注记：dsh `output.render`（canonical value→模型投影分离）与 pi `deferred tools` 是 Java 框架普遍缺失的概念，WI14/WI24 作为重点差异；dsh "参数已落账不可改"（pre-execute 禁改参数）与 pi 扩展层"可就地 mutate input"是**相反取舍**，禁止调和表述。

## T14 上下文压缩与 spill（D8）

| 语义角色 | nop | dsh | pi |
|---|---|---|---|
| 预算/压力 | `ITokenEstimator` + `CalibratedTokenEstimator`（EMA 校准） | `TokenMeter`（启发式估算 + provider usage 锚点 header 匹配复用）；pressure 判据 `totalTokens >= thresholdTokens` | `CompactionSettings{enabled,reserveTokens=16384,keepRecentTokens=20000}`；`shouldCompact = tokens > contextWindow - reserveTokens` |
| 触发 | `AgentCompactionCoordinator`（何时压缩内聚在引擎） | `CompactionTrigger`：`pressure`（pre-step 自动检查）|`context-overflow`（request-error 码触发，压缩后 retry，有 maxOverflowRetries 上限）+ 手动 `compactNow`（要求真空闲，runMaintenance） | 三档 `reason`：`threshold`/`overflow`（一次性门闩）/`manual`（/compact） |
| 策略分层 | `PipelineCompactor` Layer1（ToolResultTruncator 截断/微压缩）→Layer2（TurnPruning）→Layer3（FullSummary 7 段 prompt）升级 | region 区间选择（`selectCompactableRange`，切点过 tool-pairing balance 不切在 tool 对中间）+ 免费 prune（可选 ToolResultPruner）+ 摘要；`retainTokens`/`retainRatio` 尾部保留 | `findCutPoint` 按 keepRecentTokens 从尾累计（切点限合法边界）；迭代式摘要（旧 retainedTail 虚拟展开参与累计，UPDATE_SUMMARIZATION_PROMPT 合并旧摘要） |
| 引用式保真 | `ReferenceCompactionStrategy`（内容寻址引用式）+ `ISpillStore`/`InMemorySpillStore`（read-spill 工具取回） | `SpillStore.saveText`（verbatim 全量）→`SpillRef{locator,bytes,retrievalHint}`；spill-policy 挂 tools/post-execute（有界头尾预览 + 取回指引）；Claim-Check 模式 | （无 spill 对位；保真靠 CompactionEntry 携带 retainedTail 原文尾段） |
| 产物形态 | （Pipeline 就地改写上下文 + COMPACTION checkpoint 记录） | **追加式遮蔽**：append-only 日志不动；`SurfaceOp{op:'replace',start,end}` 新节点遮蔽旧 surface 区间（只许改 content）；影子价协议（replace 紧邻计量事件定价） | **追加式**：会话树追加 `CompactionEntry{summary,retainedTail,tokensBefore,...}`，原始条目不删除；重建 = 最近一条 compaction + 其后全部 entry（此前丢弃，不递归拼接） |

- 锚点：nop `compact/PipelineCompactor.java`、`compact/ReferenceCompactionStrategy.java`、`compact/ISpillStore.java`、`engine/AgentCompactionCoordinator.java`、`engine/CalibratedTokenEstimator.java`；dsh `packages/compaction/compaction/src/index.ts:25,96-169`、`packages/compaction/compaction-basic/src/index.ts:95-103,147-165,179-223,306-326,368-420`、`packages/compaction/compaction/src/tool-pairing.ts:110,122`、`packages/compaction/compaction/src/types.ts:23-88`、`packages/core/session/src/types.ts:368-378（SurfaceOp）`、`packages/core/session/src/surface.ts:117-134,287-318`、`packages/spill/spill/src/index.ts:45-60`、`packages/spill/spill-policy/src/index.ts:94-107,190`；pi `packages/agent/src/harness/compaction/compaction.ts:148-162,247-250,312-422,624-646,689-702`、`packages/agent/src/harness/session/types.ts:44-51（CompactionEntry）`、`packages/agent/src/harness/session/context.ts:45-57,65-100（buildSessionContext）`、`packages/coding-agent/src/core/compaction/branch-summarization.ts:34-58,96-120`。
- 语义注记：dsh 的 **Surface（模型可见投影）与 append-origin 日志双层**是理解其压缩的关键——"非破坏"指日志，模型可见历史确实被 replace 遮蔽；pi 的"非破坏"指会话树条目不删但**重建算法只取最近一条 compaction 之后**（多次压缩自然级联，旧历史不参与上下文）。两方"追加式"词同义异，WI15/WI25 必须分开表述。

## T15 会话持久化与恢复（D9-1/D9-2/D9-3）

| 语义角色 | nop | dsh | pi |
|---|---|---|---|
| 会话数据模型 | `AgentSession`（可恢复执行状态容器：消息历史 + 状态） | `Session` 事件溯源聚合（append-only log + 深冻结；消息历史是派生） | **双会话栈**：生产 v3（coding-agent SessionManager，`SessionEntry` 9 成员 + `SessionHeader{version:3}`）与新引擎 v4（harness，`Entry` 7 成员 + `LaneRecord` 9 成员 + lanes） |
| 存储后端 | `ISessionStore` 三实现：InMemory/FileBacked/DB | 双后端：JSONL（`.jsonl.zstd`）+ SQLite，共用后端无关 coordinator + torn-tail 修复 token | v3 单 JSONL 文件（`.tmp`+rename 原子发布）；v4 `JsonlSessionRepo` + SQLite 后端（writer lease + migrations + branch-cache） |
| 写策略 | （快照式保存，非逐事件追加） | `SessionWriteBehind` 有界批量写（deadline/active write/barrier/失败保留）；`flush()` 返回持久化完成 barrier；turn 边界不 await flush | 追加未确认时按有效前缀原子发布（`publishFileAtomically`）；SQLite writer lease |
| fork/branch | `SESSION_FORKED` 事件 + fork 语义 | `SessionStore.fork`（index.ts:1203）+ `session/end-seed{inherited}` 标记（types.ts:400）+ 内存 `firstLiveSeq`（index.ts:497,584；types.ts:382 注记）——seedLength 头字段已在 c291e7961a 移除（index.ts:97-98 显式拒绝） | v3 `forkFrom` 复制内容到新文件新 id（`parentSession` 记来源）；`navigateTree` 同文件树内移动（可带 branch_summary）；labels 是追加型 LabelEntry（latest-wins） |
| 版本化 | （无版本迁移机制） | `ignorable` 信封标记（词汇增长不 bump 版本） | `CURRENT_SESSION_VERSION=3` + `migrateV1ToV2`/`migrateV2ToV3`（v3 栈）；v4 `JsonlV4Header.version===4` |

- 锚点：nop `session/AgentSession.java`、`session/ISessionStore.java`、`session/FileBackedSessionStore.java`、`session/DBSessionStore.java`；dsh `packages/core/session/src/index.ts:425,559,604,1022,1203,497,584,97-98`、`packages/core/session/src/types.ts:78-98,382,400,408-440`、`packages/session/session-persistence/src/write-behind.ts:22,41-60`、`packages/session/session-persistence-jsonl/src/format.ts:17,24`、`packages/core/session/src/surface.ts:398`；pi `packages/coding-agent/src/core/session-manager.ts:30-42,100-154,283-288,938,1232-1250,1284-1313,1580-1603`、`packages/agent/src/harness/session/jsonl/codec.ts:70-100`、`packages/agent/src/harness/session/jsonl/storage.ts:24-47,86`、`packages/agent/src/harness/session/jsonl/repo.ts`、`packages/session-backends/sqlite-node/src/sqlite/repo.ts:669`。
- 语义注记（重要勘误，对既有调研）：pi **存在两套并存的会话栈**——生产用 v3（coding-agent）与 harness v4（lane/operation 日志，可恢复 operation 记录 run/compaction/navigation）。**WI4 精确化（2026-09-12）**：v4 `AgentHarness` 当前未接入生产循环——除 getModel/setModel/getTools 等少数方法外全部 `unavailable()` 抛 HarnessNotImplemented（agent-harness.ts:355-441），coding-agent 内唯一引用 create-harness.ts 仅被测试引用；生产持久化只走 v3。旧调研与新报告只写 "CURRENT_SESSION_VERSION=3" 会漏掉 v4 栈的存在，但对比 D9 时须写明"v3 生产 + v4 未接线骨架"，不得把 v4 当作生产等价栈。
- 词同义异警示："session" 三方都不是 HTTP session——nop 是可恢复状态容器、dsh 是事件账本（与 agent 1:1 同 id）、pi 是文件+内存索引两层。

## T16 checkpoint（D9-4）

| 语义角色 | nop | dsh | pi |
|---|---|---|---|
| checkpoint 主词 | `ICheckpointManager`（DB/FileBacked 两实现）+ `CheckpointJournalWriter/Reader`（append-only journal：LLM_TURN/TOOL_EXECUTION/COMPACTION/WAIT_FOR 四类分录，幂等键 + 发散检测） | **无 checkpoint 名词**：durability = `session/flush` parallel barrier + `session-checkpoint-policy` 插件（模型请求先于 adapter 派发 checkpoint）+ write-behind | **无 checkpoint 名词**：v4 lane 的 `LaneRecord`（operation_started/finished/step_attempt/…）即操作级恢复点 + `findOpenOperations` 崩溃扫描 |
| 接管/ fencing | `ISessionTakeoverLock`（CAS + lease，Db/NoOp 实现）+ `IFencingTokenService`（单调计数器防并发回退） | （无对位；单进程模型 + SessionStore 所有权） | SQLite writer lease（后端级互斥，非会话接管语义） |
| 恢复守护 | `IRecoveryManager`/`ScheduledRecoveryManager`（60s 扫描）+ 三类 handler（orphan/session-timeout/team-task） | 载入时 coordinator 采纳 `interruptedTurnClosers`（确定性合成，非后台扫描） | v4 `findOpenOperations`（打开时发现，非定时扫描） |

- 锚点：nop `reliability/ICheckpointManager.java`、`reliability/CheckpointJournalWriter.java`、`runtime/lock/ISessionTakeoverLock.java`、`fencing/IFencingTokenService.java`、`runtime/recovery/ScheduledRecoveryManager.java`；dsh `packages/session/session-checkpoint-policy/src/index.ts:2,53-55`、`packages/core/session/src/index.ts:85,1022`；pi `packages/agent/src/harness/session/types.ts:203-212`、`packages/agent/src/harness/session/jsonl/repo.ts（findOpenOperations :290-326）`。
- 语义注记：**"checkpoint" 是三方同词异义最重的词之一**——nop 是显式持久化检查点子系统（journal + 幂等键 + 发散检测），dsh 是 flush barrier（ durability 语义），pi 是 lane 操作日志（恢复点语义）。对比 D9-4 子机制时以"崩溃恢复能力面"对齐（journal 消费 vs interruptedTurnClosers vs findOpenOperations），不以名词对齐。

## T17 多代理与子代理（D10）

| 语义角色 | nop | dsh | pi |
|---|---|---|---|
| 派生/编排 | 全栈内置：`ITeamManager`（InMemory/Db）+ `TeamTaskFlowOrchestrator`/`MemberFanOutDispatcher`/`AllMustSucceedReduction` + `PlanExecutor` 计划状态机 + `AgentActor`/`IActorRuntime` | `SubagentRuntime`（ctx.subagents，具名 provider 注册表）+ `SubagentContinuationManager` + `Activation`（子代理驻留纪元 running\|waiting\|settled） | **核心无内置**（grep 仅命中 examples）；官方形态 = example extension：每 subagent spawn 独立 pi 进程（JSON/print 模式捕获结构化输出），Single/Parallel(≤8)/Chain 三模式 |
| provider/传输 | `CallAgentExecutor`/`SendMessageExecutor`（工具化入口）+ `IAgentMessenger`（Local/NoOp）+ `IMailbox`/`DeferredAckMailbox`（3-phase reservation） | 6 provider：spawn/fork（in-process，fork 种子继承父历史）/acp/claude-code/codex/dsh-sdk；`inheritsParentContext` 声明式 + `SubagentCapabilities`（含 depthLimit） | （扩展 `registerProvider` 是 LLM provider 不是 subagent provider——勿混同） |
| 深度预算 | （team/plan 层约束；`AgentConstraintsModel`） | `SessionHeader.delegationDepth` 持久化 + `AgentOptions.subagentDepth` 运行时，`delegationDepthOf = max(header, runtime)` 单调（恢复不能清零）+ 请求侧 maxDepth | （无内置故无对位） |
| 隔离边界 | Actor 容器（进程内 opt-in）+ 跨进程：`IDaemonCoordinator` + 接管锁 | 子代理本质 = 另一个普通 session+agent（同 ReactLoopAgent）；隔离来自独立 session 日志 + fork 种子；out-of-process provider 走适配 | 进程级隔离（独立 context/会话/进程）；编排逻辑全在扩展层 |
| 跨实例协调 | `ISessionTakeoverLock` 跨进程接管 + `DBMessageService` 平台消息桥 | `subagent/start`/`end` 事件（按委派父 scope 过滤）+ `reportFrom`（子→父内容投递，不结束子 turn） | （无对位） |

- 锚点：nop `team/ITeamManager.java`、`team.flow/TeamTaskFlowOrchestrator.java`、`plan/runtime/PlanExecutor.java`、`runtime/AgentActor.java`、`tool/CallAgentExecutor.java`、`message/IMailbox.java`；dsh `packages/subagent/subagent/src/index.ts:171,212,231,255,270,304`、`packages/subagent/subagent/src/continuation.ts:8-9,165,197,355`、`packages/subagent/subagent/src/types.ts:83-89,127-129,292-331`、`packages/subagent/subagent/src/depth.ts:11-14,28-35,39`、`packages/core/session/src/types.ts:83-98`；pi `packages/coding-agent/examples/extensions/subagent/index.ts:1-14,240`。
- 语义注记：roadmap 初步假设"pi 无内置 subagent"**成立**。dsh `Activation` 与 `reportFrom` 是 Java 框架无对应词的概念（驻留纪元句柄 / 结构化子→父通道）；nop 是三者中唯一带团队编排（fan-out/归约/计划状态机/调度）与跨进程接管的全栈实现。

## 伪差异警示清单（词同义异 / 义同词异速查）

| 词 | 陷阱 | 正确口径 |
|---|---|---|
| session | 三方语义完全不同 | nop=可恢复状态容器；dsh=事件溯源账本（1:1 agent）；pi=文件+索引（双栈 v3/v4） |
| checkpoint | nop 有名词、dsh/pi 无 | 按"崩溃恢复能力面"对齐（T16），不以名词对齐 |
| hook | 注册形态与分发语义三方不同 | nop=枚举点回调；dsh=waterfall 事件监听者；pi=单槽配置 hook 或多播事件 handler |
| waterfall | ≠并行广播、≠责任链短路 | Cordis 洋葱式包裹：监听者外层先跑，不调 `next()` 即否决一切内层 |
| steer | ≠abort/interrupt | 三方 steer 都不打断当前执行，只在边界消费（nop 在 Actor 消费循环、dsh 在 step 边界、pi 在 turn 后） |
| continue | ≠任意 resume | pi `continue()` 要求末尾是可续消息（约束续跑）；nop `SustainDecision.CONTINUE` 是超限强制续跑；dsh 无 continue 词 |
| step | 三方存在性不同 | dsh=事件括号的一步；nop=计数的 iteration；pi=无独立词 |
| interrupted（dsh） | ≠用户取消 | 只由崩溃修复合成（TurnEndReason），循环本体从不产生 |
| paused vs escalated（nop） | 英文直译丢语义 | paused=governance 可恢复；escalated=terminal 需人工重评（AgentEventType Javadoc 明示） |
| 追加式压缩 | dsh 与 pi "非破坏"对象不同 | dsh=日志不破坏但 surface 被遮蔽；pi=树条目不删但重建只取最近 compaction 之后 |
| AgentLoop（dsh） | 名字含 Loop 但不是循环 | Service/AgentFactory（≈ nop DefaultAgentEngine 门面）；循环体是 ReactLoopAgent |
| harness（pi） | 单数词指两个东西 | 新引擎栈（packages/agent harness/，v4 lane 格式）≠ coding-agent 进程形态（interactive/print/rpc mode） |
| provider | dsh/pi 各指不同层 | pi `registerProvider`=LLM provider；dsh `SubagentProvider`=子代理传输；nop 无统一词（分 IChatService/failover chain/subagent 工具） |
| surface（dsh） | ≠UI surface | 模型可见有序投影（user/assistant/tool-result 三类），append-only 日志之上的 CQRS 读模型 |

## Conclusion

- 17 个概念族节、约 120 个术语完成三方映射，全部锚点实测于当日 HEAD（nop=c58545f83 前缀省略为仓库相对路径、dsh=c291e7961a、pi=c49906ec7）。
- 翻译口径五条总则 + 14 条伪差异警示固化；WI4–WI27 引用术语时以本表为准，发现未覆盖概念在发现 WI 登记并回写本表。
- 对 roadmap 初步假设的三点裁定输入：①"三者都无硬性步数上限"**不成立**（nop 有 maxIterations+ISustainer）；②"nop 唯一内置 failover"**成立**；③"pi 最显式 prefix-cache 工程"**成立**（dsh 靠构造性稳定，nop 五项待核查已由 dsh-D6 ② 兑现并回写 T12）。
- 对 WI2 矩阵的一处勘误：`HookResult` 为 Pass/Veto/Reenter/**Bail** 四态（矩阵锚点候选漏 Bail）；Reenter 为 nop 独有态。

## Open Questions

- [x] nop 侧 D6（前缀缓存）五项（显式断点/前缀稳定/压缩交互/命中观测/一次性标记）现状已核查并回写 T12 表（WI13 已兑现，见 dsh-D6 ②）。
- [ ] WI5（S2 能力矩阵）落定时，若发现本表 T7 词面映射与代码行为不符，以代码为准修正本表。

## References

- `ai-dev/analysis/compare-agent-design/00-dimension-matrix.md`（WI2，本表概念族划分来源）
- `ai-dev/analysis/compare-agent-design/01-code-map.md`（WI1，三方 HEAD 与包结构基线）
- `ai-dev/analysis/00-analysis-writing-guide.md`（写作规范）
- `ai-dev/backlog/nop-ai-agent-design-comparison-roadmap.md`（WI3 编排与语义对齐约定）
- `ai-dev/design/nop-ai-agent/02-execution-model.md`、`03-extension-matrix.md`（nop 侧术语交叉参考）
- `~/ai/deepseek-harness`（HEAD c291e7961a，WI8-WI12 期重钉；141eb6fef8 为其祖先）、`~/ai/pi`（HEAD c49906ec7）（外部仓库，锚点为仓库相对路径）
