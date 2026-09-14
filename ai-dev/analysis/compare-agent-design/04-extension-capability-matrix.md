# S2 三方扩展点全量清单与能力语义（WI5 交付物）

> Status: resolved
> Date: 2026-09-12
> Scope: nop-ai-agent / deepseek-harness（dsh）/ pi 三方全部扩展点的能力语义矩阵：五级能力定义（observe/transform/veto/abort-bail/inject）、逐点触发时机/可影响内容/能力级别/同步异步/异常传播；S2 主题权威深挖
> Conclusion: 三方能力面结构迥异——nop 能力最强也最不对称：12 个 lifecycle point 中 6 个的 veto/bail 返回值实际无消费者（PRE_ACTING/POST_ACTING/POST_CALL/PRE_COMPACT/POST_COMPACT=observe、REASONING_CHUNK 是死点），但策略对象面（judge/sustainer/wait/denial/breaker/retry/compactor）全部可改变循环走向；dsh 扩展点**无 abort-bail**（轮次中断只有 agent.cancel() 命令通道，veto 只在 4 处有明确决策类型）；pi 双轨面能力分明（配置级 hook 抛异常=run 终止，扩展事件抛异常=被包含降级）。关键否定性结论：nop 执行级 middleware 无 transform 通道（AttemptContext 只读）；nop HookContext.executionContext 活引用构成合同外越权通道；dsh tools/change 裸 emit 无异常包含；pi AgentLoopConfig.beforeToolCall 类型上不能改参数而扩展层 tool_call 可就地 mutate（无再校验）。
> 基线: nop=800baf32da（nop-ai 模块与 c585459f83 diff 为空）、dsh=c291e7961a（2026-09-10，重钉；141eb6fef8 为其祖先）、pi=c49906ec7；全部锚点行号当日实测；能力级别以代码实际行为为准（返回值被谁消费、消费后走什么分支）
> 锚点重钉: 2026-09-14，HEAD 4582e780dad4（nop，plan 355 重构+M5/M6 修复后逐锚点核对；仅行号更新，结论不变）
> 引用: 00-dimension-matrix.md（S2 章节契约）、02-terminology-map.md T7（词面映射）、03-flow-agent-loop.md（挂载位置）；本文档是扩展点能力语义主题权威源

## ① 结论摘要

- 能力分布总况：nop 监听面合同能力偏弱（6/12 lifecycle point 返回值无消费）但策略对象面强（9 个可替换组件全部影响控制流）；dsh 全部 waterfall 统一具备 transform，veto 仅 4 处（pre-step reject / pre-execute deny / post-execute block / request-error retry），无 abort-bail；pi veto 面 8 个事件 + beforeToolCall，transform 面 6 事件 + 4 hook，注册面 inject 丰富。
- 异常传播三分：nop PRE_/BEFORE_ 前缀 hook 抛→重抛→顶层 catch→status=failed（normally-completed future）；dsh waterfall 异常→turn 结构化 error→driver 存活；pi 配置级 hook 抛→handleRunFailure 合成 error 消息终止 run，扩展 handler 抛→逐 handler 包含（唯一例外 tool_call 无 catch→降级为 veto）。
- 同步异步：三方关键路径分发全部 await（串行）；nop 全部扩展点在单一 ReAct 工作线程相互串行（fan-out 仅工具体）；dsh emit 同步 fire-and-forget（contained）；pi handler async 顺序 await。
- owner doc 勘误线索 6 项已登记（⑤ 节）：IContentGuardrail 已 ship 但 doc 称半闭合、接口计数 65/67/61/68 vs live 约 73、REASONING_CHUNK 死点、HookToMiddlewareAdapter 死代码、消费点缩写失真、53/52 扩展点自相矛盾。
- 能力级别全部以返回值消费点代码为证（③ 节每行带锚点）；"以注释/文档/类型名为准"的裁定为零。

## ② 能力级别定义与判定标准

### 五级判定标准（代码证据形态）

| 级别 | 判定标准 | 代码证据形态 |
|---|---|---|
| observe | 监听者/扩展返回值与副作用不影响主流程 | 返回值无接收变量，或消费点显式丢弃；异常被包含 |
| transform | 可修改继续流转的内容（消息/参数/结果/配置/prompt） | 返回值或就地修改被后续流程实际使用（替换源对象/进入下游调用） |
| veto | 可否决/跳过当前步骤，但不中断整个轮次 | 返回决策值使本步骤不执行/被替换，循环继续（continue/跳过该批内其他不受影响） |
| abort-bail | 可中断当前轮次或整个执行（含强制改变循环走向：强制续跑/挂起/优雅停止） | 触发 break/终止分支/状态机终态/整 run 退出 |
| inject | 可向上下文注入新内容 | 新消息/新上下文进入模型可见输入或 prompt |

### 非 hook 表面映射规则（本矩阵裁定约定）

1. **策略对象**（可替换组件、非监听者）：按"对主循环的影响面"标注能力级别，行尾加注记〔策略对象〕。
2. **注册面**（运行时注入能力/资源的机制）：标注"注册面"，其注入效果可标 inject，但无运行时否决语义。
3. **调度配置枚举**（如 pi toolExecution）：不参与五级分级，单独注明。

### 灰区预裁定（本矩阵固化）

- nop DENY_AND_BREAK（中断本迭代 dispatch + 阈值必伴 status=paused → 下轮 break reactLoop）→ **abort-bail**。
- nop 执行级 veto（合成 NON_TRANSIENT 失败进 retry 决策，cap 3 后 fail-loud）→ **veto**（间接 abort-bail 效果记入异常/传播栏）。
- pi terminate:true（全批都 true 才停）→ **abort-bail**（优雅非错误）。
- pi hook 抛异常降级为 error 工具结果 → 按该点主能力定级，异常传播栏说明"降级为 veto"。
- dsh waterfall 不调 next() → 技术性跳过内层与内建默认，语义由消费端类型裁定（pre-step=干净 veto；request=无 veto 通道，缺 config 结构化报错）。

## ③ 三方逐点能力矩阵

### 3.1 nop-ai-agent

#### 3.1.1 AgentLifecyclePoint × HookResult 四态（合同能力 = 返回值消费点）

| 点 | 触发时机 | Veto 消费 | Bail 消费 | Reenter 消费 | 能力级别 | 异常传播 | 锚点 |
|---|---|---|---|---|---|---|---|
| PRE_CALL | execute 入口一次 | →setStatus(completed)+vetoedAt 事件，**整执行立即返回** | 非法点抛 | 非法点抛 | **abort-bail** | 重抛→顶层 catch→failed | `engine/ReActAgentExecutor.java:614-623` |
| PRE_REASONING | 每迭代（压缩后） | →iteration++ continue 跳过本轮 | 非法点抛 | 非法点抛 | **veto** | 重抛→failed | `:825-829` |
| POST_REASONING | 响应落账后 | **丢弃（无消费）** | →丢弃响应+re-prompt，cap 3（MAX_POST_REASONING_BAILS）超限抛 | 非法点抛 | **abort-bail** | 告警继续 | `:1061-1079`、`:152` |
| PRE_ACTING | 每工具结果处理前 | **丢弃** | 非法点抛 | 非法点抛 | **observe** | 告警继续 | `engine/AgentToolDispatcher.java:371` |
| POST_ACTING | 每工具 commit 后 | **丢弃** | 非法点抛 | 非法点抛 | **observe** | 告警继续 | `:460` |
| POST_CALL | 正常出口且 completed | **丢弃** | →仅标记 bailReason/guardrailBlocked，不阻断 | 非法点抛 | **observe** | 告警继续 | `engine/ReActAgentExecutor.java:1304-1335` |
| ON_ERROR | 两处失败终点 | 无 | 非法点抛（被兜底） | 非法点抛（同） | **observe** | 双层 try/catch 永不外溢 | `engine/AgentHookInvoker.java:179-188,192-198` |
| REASONING_CHUNK | **从未触发（死点）** | — | — | — | **无能力** | — | 全库唯一非注释引用 `hook/DefaultHookRegistry.java:169` |
| PRE_COMPACT | 压缩归档前 | **丢弃**（压缩照常） | 非法点抛 | 非法点抛 | **observe** | 重抛→failed | `engine/AgentCompactionCoordinator.java:85` |
| POST_COMPACT | compact 返回后（含异常路径） | **丢弃** | 非法点抛 | 非法点抛 | **observe** | 告警继续 | `:121,125` |
| BEFORE_TOOL_RESULT_PROCESSED | 每工具结果 commit 前 | 非 Reenter 结果丢弃 | 非法点抛 | **Reenter→hook 文本替换真实工具结果**（tool_call_id 保持配对）+marker，cap 3/迭代 | **veto+transform+inject** | BEFORE_ 前缀重抛→failed | `engine/AgentToolDispatcher.java:390-414` |
| AFTER_TOOL_RESULT_PROCESSED | commit+持久化后 | 丢弃 | 非法点抛 | **Reenter→真实结果保留，批后注入 1 条 user marker** | **inject** | 告警继续 | `:462-480,209-211` |

合同白名单（fail-loud 校验）：Bail 仅 POST_REASONING/POST_CALL；Reenter 仅 BEFORE/AFTER_TOOL_RESULT_PROCESSED（`engine/AgentHookInvoker.java:156-162,213-219`）。

#### 3.1.2 ExecutionPoint（执行级 middleware）

| 点 | 触发时机 | Veto 后分支 | 能力 | 异常传播 | 锚点 |
|---|---|---|---|---|---|
| PRE_LLM_ATTEMPT | 重试循环每 attempt 前 | 跳过真实调用，合成 NON_TRANSIENT 错误进 retry 决策（RETRY/FALLBACK/STOP）；不计 circuit failure；cap 3 超限 fail-loud | **veto** | middleware 抛→位于 try 外→直接终止执行 | `engine/LlmCallCoordinator.java:213-232,850` |
| POST_LLM_ATTEMPT | 每 attempt 返回后 | 拒绝该响应→同一 retry 决策；cap 3 | **veto** | middleware 抛→位于 try 内→当作传输错误进 retry（可能被重试掩盖） | `:240-259,296-311` |
| PRE_TOOL_ATTEMPT | 每工具提交前（调用线程） | 该工具产 error result，同批其他不受影响 | **veto** | 抛→取消已启动 futures→重抛→failed | `engine/AgentToolDispatcher.java:243-256,287-294` |
| POST_TOOL_ATTEMPT | 每工具 join 后 | 替换真实结果为 error result | **veto**（退化 transform：只能换成错误） | 抛→上浮→failed | `:357-369` |

**transform 能力裁定：无（合同级）。** `executeExecutionMiddleware` core 是纯 pass-through，AttemptContext 只读，middleware 拿不到 ChatRequest/ChatResponse/AiToolCallResult 对象（`engine/AgentHookInvoker.java:113-141`、`middleware/AttemptContext.java`）。越权通道：HookContext.executionContext 活引用可非合同改写（`hook/HookContext.java:30-32`）。

#### 3.1.3 SecurityCheckpointChain 7-checkpoint

链序：postDenialGuard→toolAccess→permission→pathAccess→layer2(securityLevel+matrix)→layer3(approvalGate)→conflict(writeIntent)；首个非 ALLOW 短路（`engine/AgentSecurityConsultation.java:277-285`、`security/SecurityCheckpointChain.java:16-24`）。

| 结果 | 行为 | 能力级别 | 锚点 |
|---|---|---|---|
| DENY | 跳过该工具 + 注入 error tool response + 记 ledger；**同批后续工具继续逐个过链** | **veto+inject** | `engine/ReActAgentExecutor.java:1180-1182` |
| DENY_AND_BREAK | break dispatchLoop（本批剩余全不执行）+ 阈值超限时置 paused → 下一迭代 break reactLoop | **abort-bail** | `:1177-1179,1196-1199,745-748`、`engine/AgentSecurityConsultation.java:336-350` |

7 个 checkpoint 策略（IToolAccessChecker/IPermissionProvider/IPathAccessChecker/IPermissionMatrix/IApprovalGate/IPostDenialGuard/IConflictStrategy）能力同构 = veto(+inject error response)〔策略对象〕。

#### 3.1.4 护栏 / 修复器 / 事件

| 面 | 触发时机 | 能力级别 | 异常/失败语义 | 锚点 |
|---|---|---|---|---|
| IContentGuardrail 输入 | PRE_REASONING 后每迭代 | **transform+veto+inject**（MODIFY 改写 user 消息放行；BLOCK 注入阻断消息+continue） | 抛→重抛→failed | `engine/AgentPromptAssembly.java:67-81`、`engine/ReActAgentExecutor.java:831-847` |
| IContentGuardrail 输出 | POST_REASONING 后 | **transform+veto+inject**（MODIFY setContent；BLOCK 注 error responses 保配对/改写 assistant+continue） | 同上 | `engine/AgentPromptAssembly.java:293-325`、`:1081-1084` |
| IToolCallRepairer | dispatchLoop 每 call | **transform**（返回值直接替换后续使用的 toolCall）；无失败概念（修不动原样返回交 7-checkpoint 拒绝） | 不抛不 veto | `engine/ReActAgentExecutor.java:1163`、`repair/ChainRepairer.java:42-80` |
| IAgentEventPublisher 订阅 | 各发布点 | **observe**（同步扇出、单订阅者异常隔离、返回 void 无消费） | 双 contained | `engine/DefaultAgentEventPublisher.java:16-27`、`engine/AgentHookInvoker.java:220-232` |

#### 3.1.5 策略对象面〔策略对象〕

| 对象 | 消费点与分支 | 能力级别 | 锚点 |
|---|---|---|---|
| IModelRouter | route 定本轮模型；getFallback 供 FALLBACK/熔断扫描换模 | **transform** | `engine/ReActAgentExecutor.java:878-879`、`engine/LlmCallCoordinator.java:581,802` |
| ICompletionJudge | COMPLETE→completed；CONTINUE→注入续跑消息（cap 3）；ESCALATE→escalated | **abort-bail+inject** | `engine/ReActAgentExecutor.java:1101-1135` |
| ISustainer | CONTINUE→maxIterations+=originalMax 强制续跑（反向 abort-bail）；STOP→放行终止；null→fail-loud | **abort-bail（反向）** | `:455-478` |
| IWaitCoordinator | SUSPEND→WAIT_FOR checkpoint+waiting+break reactLoop | **abort-bail** | `:757-788` |
| IDenialLedger | 阈值→DENY_AND_BREAK+paused；isPaused→break reactLoop | **abort-bail** | `engine/AgentSecurityConsultation.java:336-350` |
| ICircuitBreaker | allowCall=false→外层 fail-fast 抛 / 触发 fallback 换模 | **abort-bail(+transform)** | `engine/LlmCallCoordinator.java:183-195,784-826` |
| IRetryPolicy | RETRY 改走向/STOP 重抛终止/FALLBACK 三级切换；null→fail-loud | **abort-bail+transform** | `:280-418` |
| IBudgetProvider | 快照入 ctx；引擎自身零分支（唯一消费者是功能性 router） | **inject（数据面）/observe** | `engine/ReActAgentExecutor.java:872-876` |
| IContextCompactor | compact 结果 clear+addAll **整列表替换**；抛异常**被吞**保留原文（全引擎唯一吞异常的 transform 点） | **transform（最强）** | `engine/AgentCompactionCoordinator.java:116-133` |

#### 3.1.6 注册面与 DSL

| 面 | 能力 | 锚点 |
|---|---|---|
| ITalent | 注册面 inject（准入后注入 system prompt 指令+工具定义） | `engine/AgentPromptAssembly.java:101-136` |
| ISkillProvider | 注册面 inject；缺 requiredSkill → fail-fast（abort 装配） | `:168-197`、`skill/SkillResolver.java:73-81` |
| IContributionRegistry PROMPT/HOOK | PROMPT=inject（拼 system prompt）；HOOK=装配期注册 hook（能力=所映射点） | `:216-239`、`engine/AgentExecutorResolver.java:220-237` |
| IContributionRegistry 其余 5 类（TOOL/COMMAND/MCP_SERVER/PERMISSION_RULE/ROUTER） | 注册面 observe（存储可查询、零引擎消费） | `contribution/ContributionType.java` javadoc 自认 successor |
| `<hook event>` DSL | 能力 = 所映射 lifecycle point 的完整合同能力（EvalFunctionHookAdapter 采纳 HookResult） | `hook/DefaultHookRegistry.java:97-115,203-222` |
| `<middlewares>`/`<filter-chain>` DSL | 会话级=同点 veto/bail 能力；执行级=veto | `engine/AgentExecutorResolver.java:238-295` |

线程模型：全部扩展点在单一 ReAct 工作线程（supplyAsync lambda）相互串行；工具体 fan-out 在 IToolManager 异步线程但 PRE/POST_TOOL_ATTEMPT 在调用线程；SingleTurnExecutor 模式不接 hookRegistry（整个 hook 面不生效，`engine/AgentExecutorResolver.java:211-212`）。

### 3.2 deepseek-harness

| # | 扩展点 | 触发时机 | 可影响内容 | 能力级别 | 同步异步 | 异常传播 | 锚点 |
|---|---|---|---|---|---|---|---|
| 1 | agent/pre-step（waterfall） | 每提议 step 前（claim+组装后） | 整个进入 step 的 messages 数组 | **veto+transform+inject**（reject→turnEnds blocked 干净收尾无 abort；enter 重写；默认 next 注入快照消息） | await | →turn catch→turn error→driver 存活 | 声明 `packages/core/agent/src/runtime-types.ts:330`；分发 `packages/core/agent-loop/src/agent.ts:249-255,290-293` |
| 2 | agent/request（waterfall） | 每 buildRequest | LlmCallConfig（provider/model/…）；不能改 messages | **transform**（无 veto：缺 provider/model→结构化报错） | await | 同上 | `runtime-types.ts:347`；`agent.ts:530-537` |
| 3 | agent/request-error（waterfall） | 流终结 error/aborted 后 | 恢复决策 retry/undefined | **veto**（retry=否决失败终局接管恢复；undefined=默认终局 observe） | await | 同上 | `runtime-types.ts:363`；`agent.ts:448-463` |
| 4 | agent/turn-stopping（serial） | turn 关闭前 | 无返回值消费 | **observe+inject**（否决靠 agent.steer() 数据+inbox 重查，"Data decides"） | serial await | 异常→turn error | `runtime-types.ts:391`；`agent.ts:315-318` |
| 5 | tools/pre-execute（waterfall） | 参数快照冻结后 | 仅 allow/deny/ask；**不能改参数**（"already logged and presented"） | **veto**（deny→isError result 不执行；ask 无审批服务降级 deny） | await | catch→该调用 error result（contained） | `packages/core/tools/src/index.ts:144,581-584,1465-1495` |
| 6 | tools/execute（waterfall） | 工具体外圈 around | 可换 exec.signal；自拟结果过 schema 重校验 | **transform**（技术含 veto-over-body：不调 next 跳过工具体；caller abort 不可剥离） | await | catch→error result | `:155,384-386,1559-1586,1816-1834,1879-1906` |
| 7 | tools/post-execute（waterfall） | dispatch 结果后、materialize 前 | content/value 替换、additionalContexts、block feedback | **transform+veto+inject**（block→isError+feedback） | await | catch→error result；同时 content+value 是 TypeError | `:167,590-593,1732-1771` |
| 8 | tools/result（emit） | 结果物化后 | 冻结快照 | **observe** | 同步 fire-and-forget | 双 contained（throw+rejection log） | `:189,1647-1666,1837-1850` |
| 9 | tools/change（emit） | 工具注册表变化 | 无 | **observe** | raw ctx.emit | **无包含**（唯一裸通知之一） | `:199,806` |
| 10 | tools/code-dispatch-log（waterfall） | code-mode 子派发落账前 | 仅 tool/code-dispatch 的日志副本 content | **transform**（仅呈现/持久化层） | await | fallback 记录原文（contained） | `:181,1286-1296` |
| 11 | llm/stream（waterfall） | 每 LlmRuntime.stream | 整个 chunk 流（可包裹/过滤/整流替换） | **transform+inject**（+技术性 veto-over-adapter-call） | 分发同步、迭代异步 | 保持 thrown→step try/catch→rethrow→turn error；adapter 失败走 request-error | `packages/llm/llm/src/index.ts:72,1113-1121` |
| 12 | system-prompt/assemble（waterfall） | 每 assemble（sections 汇出排序后） | 整个 PromptAssembly；complete section 恢复收回 section 改写权 | **transform+inject**（无真 veto：空 assembly 只渲染空 prompt） | await | 无包含→preStep→turn error | `packages/core/system-prompt/src/index.ts:31,552-626` |
| 13 | session/event（emit） | 每次 append 后（post-commit） | 无 | **observe** | fire-and-forget | 双 contained | `packages/core/session/src/index.ts:72,747-752,403-420` |
| 14 | session/flush（parallel） | 显式 flush | 无（等待屏障） | **observe** | Promise.allSettled | 全 settle 后第一个 rejection 抛给 caller | `:81,1144-1161` |
| 15 | session/created / disposed（emit） | 会话发布/销毁 | 无 | **observe**（created 附同步 throw veto 边：同步抛→否决 publication 并回滚；async rejection 仅 log） | 同步边界 | created 同步传播；disposed contained | `:50,60,1101-1119,1121-1131` |
| 16 | agent/status·error·inbox/*·created·disposed·session-start（emit） | 各状态点 | 无 | **observe**（dispatch.emit 逐监听者 try/catch，互不影响） | fire-and-forget | contained | `packages/core/agent/src/dispatch.ts:120-137`、`agent.ts:119-126,218-223` |

跨切面：waterfall 不调 next()=返回值即整体返回、内建默认被跳过（`vendor/cordis/src/events.ts:234-243`）；**abort-bail 在 dsh 扩展点不存在**（轮次中断唯一通道是 agent.cancel() 命令）；scope 注册（systemPrompt.section/context、tools）=每 pre-step 稳定注入的声明式 inject（waterfall 前汇入）。

### 3.3 pi

#### 3.3.1 AgentLoopConfig 配置级 hook（9 函数；toolExecution 为调度配置）

| Hook | 触发时机 | 可影响内容 | 能力级别 | 同步异步 | 异常传播 | 锚点 |
|---|---|---|---|---|---|---|
| convertToLlm（必填） | 每次 LLM 调用前 | AgentMessage[]→LLM Message[] 完整转换 | **transform** | await | 调用点无 catch→Agent.runWithLifecycle catch→handleRunFailure 合成 error 消息终止 run（裸 agentLoop 路径=unhandled rejection） | `packages/agent/src/types.ts:178`、`agent-loop.ts:295`、`agent.ts:502-527` |
| transformContext | 每 LLM 调用前（convertToLlm 前） | 送 LLM 消息数组整体替换 | **transform** | await | 同上（扩展 context 事件侧已被 runner 包含） | `types.ts:200`、`agent-loop.ts:289-292` |
| getApiKey | 每 LLM 调用前 | 本次请求 key | **inject**（undefined 回退 config.apiKey） | await | 抛→同上 | `types.ts:210`、`agent-loop.ts:305-306` |
| shouldStopAfterTurn | turn_end 后 | true→agent_end 优雅退出整 run（不再拉 steering/follow-up） | **abort-bail**（优雅非错误） | await | 抛→同上 | `types.ts:222`、`agent-loop.ts:247-257` |
| prepareNextTurn | turn_end 后 | 下轮 context/model/thinkingLevel 整体替换 | **transform+inject** | await | 抛→同上 | `types.ts:229-245`、`agent-loop.ts:226-245` |
| getSteeringMessages | runLoop 开始+每轮工具批后 | 注入消息（下轮 LLM 前） | **inject** | async await | 抛→同上 | `types.ts:244`、`agent-loop.ts:167,259,182-190` |
| getFollowUpMessages | 将停止时 | 非空→作为 pending 续跑（阻止 run 结束） | **inject** | async await | 抛→同上 | `types.ts:257`、`agent-loop.ts:263-268` |
| beforeToolCall | 参数校验后、执行前 | block→error result（reason 可自定义）；terminate（全批 true 才停）；**类型上不能改参数**（BeforeToolCallResult 无 args 字段） | **veto**（+可选 abort-bail：全批 terminate） | async await | **hook 抛错→降级为 veto**（catch→immediate error 工具结果，run 继续） | `types.ts:277,61-69`、`agent-loop.ts:619-667` |
| afterToolCall | 执行完成后 | 浅覆盖 content/details/isError/usage/terminate | **transform** | async await | **hook 抛错→整个结果替换为 error**（工具已真实执行——事后污染非 veto） | `types.ts:292,84-95`、`agent-loop.ts:724-751` |
| toolExecution | — | 调度方式 sequential/parallel | 调度配置（不分级） | — | — | `types.ts:268`、`agent-loop.ts:422-425` |

#### 3.3.2 ExtensionAPI 34 个 type 标签逐个定级

**Veto 组（8）**：

| 事件 | 能力 | 证据 |
|---|---|---|
| tool_call | **veto+transform** | block 短路 `runner.ts:943-947`；**input 就地 mutate 直达执行参数、无再校验**（`ext/types:914-928`、`AS:496`→`AL:618,655-660` 共享引用） |
| session_before_switch | veto | cancel 短路 `ASRT:142-147`（通用机制 `R:813-817`） |
| session_before_fork | veto | `ASRT:159-164` |
| session_before_compact | **veto+transform** | cancel→"Compaction cancelled"（`AS:1903-1905`）；compaction 字段整体替换压缩结果（`:1907-1925`） |
| session_before_tree | **veto+transform** | cancel（`AS:3093-3095`）；summary/instructions/label 覆写（`:3097-3111`） |
| project_trust | veto | 首个 yes/no 裁决胜出（`R:209-232`），生效+可持久化（`PT:44-63`） |
| input | **veto+transform** | handled 短路（`AS:1160-1163`）；transform 改写（`:1164-1167`） |
| user_bash | **veto+transform** | result 直接替代真实 shell 执行（`IM:6596-6631`）；operations 覆写；首个结果短路（`R:962-967`） |

**Transform 组（6 纯 transform）**：

| 事件 | 能力 | 证据 |
|---|---|---|
| tool_result | transform | content/details/isError/usage 链式合并 `R:877-930` → `AL:724-751` |
| message_end | transform | 同 role 替换 `R:835-875`；异 role 拒绝并报 error（`:850-857`）；原地落地同步持久化 `AS:721-791` |
| context | transform | messages 替换 `R:984-1014`（=transformContext 接线，每 LLM 调用前） |
| before_provider_request | transform | 出线 payload 整体替换 `R:1016-1048` |
| before_provider_headers | transform | 就地改写/null 删头 `R:1050-1079` |
| before_agent_start | **transform+inject** | systemPrompt 链式替换 `AS:1265-1272`；message 附加进 prompt `:1251-1263` |

**Inject 组（1 纯 inject）**：resources_discover（纯追加 skill/prompt/theme 路径，`R:1147-1193`→`AS:2389-2400`）。

**Observe 组（19）**：session_start、session_info_changed、session_compact、session_compact_failed、session_shutdown、session_tree、agent_start、agent_end、agent_settled、turn_start、turn_end、message_start、message_update、tool_execution_start、tool_execution_update、tool_execution_end、model_select、thinking_level_select、after_provider_response（响应体不可及、返回值丢弃，`ext/types:708-712`、`sdk:348-358`）。通用 emit 丢弃返回值（`R:801-833`）。

#### 3.3.3 register* 注册面

| 注册面 | 能力 | 锚点 |
|---|---|---|
| registerTool | 注册面 inject（新工具进 LLM tools 面+系统提示） | `ext/types:1268-1270`、`loader:267-272`、`AS:2588-2633` |
| registerCommand/Shortcut/Flag | 注册面 inject（新增用户动作/CLI 面） | `:1277-1305`、`AS:1289-1313` |
| registerMessageRenderer/MarkdownTransformer/EntryRenderer | 注册面 observe（纯 TUI 显示层，不入 LLM 上下文） | `:1312-1318`、`R:579-601` |
| registerProvider/unregisterProvider | 注册面 **transform+inject**（全局路由改写：models 替换/baseUrl 覆写/自定义 streamSimple） | `:1440-1456`、`R:353-411`、`MR:131-143` |

异常传播总结：扩展 handler 逐 handler try/catch（`R:809-828`），异常→errorListeners 上报（`R:563-567`），不打断其他 handler 与主流程；唯一穿透点 tool_call（`R:941` 无 catch→`AS:498-503` 包装→`AL:661-667` 接住→**降级为 veto**）。所有 handler async 顺序 await（按扩展加载序串行）。

## ④ 能力级别汇总对比

### 4.1 census（计数单位 = 可注册监听面/事件类型）

| 方 | hook/waterfall 类 | serial | emit/事件 | 注册面 | 策略对象 | 合计 |
|---|---|---|---|---|---|---|
| nop | 12 lifecycle + 4 execution + guardrail×2 + repairer + DSL 3 形态 | —（middleware 链非独立 mode） | 事件订阅 1 面（20 事件值） | talent/skill/contribution×7/`<hook>` DSL | 9 | ≈38 面 |
| dsh | waterfall 8（agent 3 + tools 4 + llm/stream）+ assemble | 1（turn-stopping） | emit 12（agent 8 + tools 2 + session 4 中 2 emit + created/disposed）+ raw 2 | scope 注册（systemPrompt/tools） | —（策略=per-provider retryPolicy 配置） | ≈25 面 |
| pi | AgentLoopConfig hook 9 + 配置 1 | — | ExtensionAPI 34 type 标签 | register×9 | —（strategy=settings 配置） | ≈53 面 |

### 4.2 五级分布对比

| 能力级 | nop | dsh | pi |
|---|---|---|---|
| observe | 事件订阅；PRE/POST_ACTING、POST_CALL、PRE/POST_COMPACT、ON_ERROR（返回值丢弃）；REASONING_CHUNK（死点）；contribution 5 类 | 全部 emit 系（14）；request-error 默认侧；turn-stopping 可见侧 | 19 个事件；3 个渲染注册面 |
| transform | guardrail×2（MODIFY）；repairer；IContextCompactor（最强，整历史替换）；IModelRouter；IContextCompactor 抛异常被吞 | 全部 8 waterfall 统一具备；code-dispatch-log（仅日志副本） | convertToLlm/transformContext/afterToolCall/prepareNextTurn + tool_call 就地 mutate + tool_result/message_end/context/before_provider_* + session_before_* 的结果替换侧 + user_bash |
| veto | PRE_REASONING；执行级 4 点（veto cap 3）；7-checkpoint DENY；BEFORE_TOOL_RESULT Reenter（内容替换）；guardrail BLOCK；执行级无 transform | pre-step reject；tools/pre-execute deny；tools/post-execute block；agent/request-error retry | beforeToolCall block；tool_call block；session_before_switch/fork/compact/tree cancel；project_trust；input handled；user_bash |
| abort-bail | PRE_CALL veto（整执行 completed）；POST_REASONING bail（cap 3）；DENY_AND_BREAK（经 pause）；策略对象 6 个（judge/sustainer/wait/denial/breaker/retry） | **无**（唯一通道 agent.cancel() 命令） | shouldStopAfterTurn；beforeToolCall terminate 全批 |
| inject | BEFORE/AFTER_TOOL_RESULT Reenter marker；guardrail BLOCK 注入消息；DENY error response；talent/skill/PROMPT contribution；IBudgetProvider 快照 | pre-step enter 附加/快照消息；post-execute additionalContexts；llm/stream 整流替换；scope 注册 | prepareNextTurn 换 context；getSteering/getFollowUp；before_agent_start message；resources_discover；registerTool/Command/Provider |

### 4.3 关键结构性差异（供 WI9/WI19 引用）

1. **能力不对称性**：nop 的 hook 合同能力与点位强相关（同为 lifecycle point，PRE_CALL=abort-bail 而 PRE_ACTING=observe）；dsh/pi 的同 mode 点能力相对均匀。
2. **abort-bail 有无**：nop 策略对象面 6 个 + hook 2 点可中断轮次；dsh 扩展点零 abort-bail（设计立场：中断是用户命令不是扩展权）；pi 2 处（shouldStopAfterTurn / terminate 全批）。
3. **参数改写权**：nop repairer（transform 工具调用）与 pi 扩展 tool_call（就地 mutate 无再校验）都可改参数；dsh 明确禁止（pre-execute "already logged"，execute 只换 signal）。
4. **异常传播哲学**：nop 前缀 hook fail-fast（重抛→failed）+后缀容错（告警继续）+ 执行级不对称（PRE 在 try 外、POST 在 try 内进 retry）；dsh 结构化收口（turn error + driver 存活）；pi 双轨（配置级 hook 抛→run 终止，扩展事件抛→包含降级）。
5. **越权/灰通道**：nop HookContext.executionContext 活引用（合同外可变 ctx）；pi tool_call mutate 无再校验；dsh 无已知越权通道（请求冻结 deep-freeze）。

## ⑤ 权威源引用关系与勘误登记

- 本文档是 **S2（扩展点能力语义）主题的权威深挖**。**dsh-D2（WI9）**/ **pi-D2（WI19）** 引用本文档 ③/④ 结论，只写对比增量（取舍评价），不重复逐点分级；冲突时以本文档为准，由 WI29 收敛。
- 同点顺序（排序来源/分发顺序）归 WI6（S3）；跨机制叠加/冲突裁决归 WI7（S4）；本文档仅记录判定能力所必需的分发行为后果。
- nop 侧接口普查：owner doc `ai-dev/design/nop-ai-agent/03-extension-matrix.md` 自称 67/65/63/61 接口互相矛盾，live 主源码 public interface 约 73 个；本矩阵对 loop 挂载面全量分级（12+4+7+9+注册面+护栏+修复器），非 loop 挂载的 Layer 4 平台扩展（约 40 个：team/message/memory/recovery/usage 等）登记于本节不逐点分级（理由：不挂载在 agent 主循环，其能力语义属平台集成面，D2 对比不受影响）。
- **owner doc 勘误线索（docs bug，登记待后续处理）**：① IContentGuardrail 实现已 ship（PromptInjectionGuardrail + DefaultAgentEngine.setContentGuardrail 接线点），doc 称"无实现/半闭合"过期；② 接口计数矛盾（65/67/61/68 vs live ~73）；③ "扩展点 53 vs 52"自相矛盾；④ AgentHookInvoker:58-59 注释称 REASONING_CHUNK 直调 invokeHooks——实际全库零触发点；⑤ DefaultHookRegistry.HookToMiddlewareAdapter 为零引用死代码但注释称 register() 已包裹 middleware；⑥ "主要消费者 DAE/RAE"缩写系统性失真（消费点已散入 6+ 个协作者类）。按 AGENTS.md docs-bug 规则登记于 daily log，owner doc 修订不在本分析计划内。

## References

- `ai-dev/analysis/compare-agent-design/00-dimension-matrix.md`（S2 契约与裁定格式）
- `ai-dev/analysis/compare-agent-design/02-terminology-map.md`（T7 词面映射）
- `ai-dev/analysis/compare-agent-design/03-flow-agent-loop.md`（挂载位置）
- `ai-dev/design/nop-ai-agent/03-extension-matrix.md`（owner doc，普查对照对象）
- nop：`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/`（engine/hook/middleware/security/guardrail/repair/contribution/skill）
- dsh：`packages/core/agent/src/runtime-types.ts`、`dispatch.ts`、`packages/core/agent-loop/src/agent.ts`、`packages/core/tools/src/index.ts`、`packages/llm/llm/src/index.ts`、`packages/core/system-prompt/src/index.ts`、`vendor/cordis/src/events.ts`（`~/ai/deepseek-harness`）
- pi：`packages/agent/src/types.ts`、`agent-loop.ts`、`agent.ts`、`packages/coding-agent/src/core/extensions/{types,runner,loader}.ts`、`core/agent-session.ts`、`core/sdk.ts`（`~/ai/pi`）
