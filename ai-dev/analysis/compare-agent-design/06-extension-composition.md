# S4 三方跨扩展机制协同（WI7 交付物）

> Status: resolved
> Date: 2026-09-12
> Scope: nop-ai-agent / deepseek-harness（dsh）/ pi 三方在同一行为穿过多种扩展机制时的叠加顺序、冲突裁决、veto/bail 传播边界与终止范围；S4 主题权威深挖
> Conclusion: 三方的跨机制组织模式根本不同——nop 是**同心圆包裹**（middleware 洋葱的核是 hook 顺序遍历，middleware veto 结构性优先于全部 hooks；机制面在代码位置上静态串联），dsh 是**扁平线性管线**（各 waterfall 派发点严格顺序 await 零交错，跨机制协同靠"同一事件多 listener 委托链"与"成对/三联 scoped 监听防撕裂"），pi 是**槽位-事件桥接**（配置级 hook 就是扩展事件的装配外壳，两者不能共存；跨机制协同靠 AgentSession 层的恢复循环）。终止范围：nop 扩展拥有四方最宽的终止权（hook 可达整个执行级 abort-bail），dsh 扩展点零 run 级终止权（唯一通道 agent.cancel() 命令），pi 居中（shouldStopAfterTurn/terminate 全批可达 run 级但均优雅）。
> 基线: nop=800baf32da（nop-ai 模块与 c585459f83 diff 为空）、dsh=c291e7961a、pi=c49906ec7；全部锚点行号当日实测
> 引用: 00-dimension-matrix.md（S4 契约）、03-flow-agent-loop.md（流程段序基准）、04（单点能力）、05（同点顺序）；本文档是跨扩展协同主题权威源

## ① 结论摘要

- 组织模式：nop 同心圆（middleware 洋葱 ⊃ hook 遍历；9 个 chain 点，POST_COMPACT/ON_ERROR/REASONING_CHUNK 为非 chain 直调）；dsh 扁平管线（4+4+2 个 waterfall/emit 派发点严格串行）；pi 槽位-事件桥（6 个槽位就是事件桥：transformContext←context、beforeToolCall←tool_call、afterToolCall←tool_result、transformHeaders/onPayload/onResponse←before_provider_*/after_provider_response）。
- 冲突裁决通则：同面冲突=顺序裁决（05 章）；跨面冲突=流程位置裁决（nop middleware veto 断链即 hooks 不执行；dsh listener 内委托 next() 决定归属；pi 单槽覆盖无链）。三方都无"打分制"优先级仲裁。
- 最具信息量的三个跨机制设计：dsh installModelSelection **三联监听**（assemble/request/pre-step 共享 assembled 快照，防 prompt 与路由撕裂）；dsh request-error 上 llm-retry×compaction-basic **出厂容序委托链**（retryableCodes 不含溢出码，两套计数独立）；pi overflow×retry **分类器硬互斥**（_isRetryableError 对溢出显式返回 false，retry 优先判定）。
- 发现的下游可见缺口（nop 两项）：PRE_CALL veto 在结构化 AgentExecutionResult 中无标记（仅事件 payload vetoedAt）；POST_COMPACT hook 注入的消息会被历史替换 clear 丢弃。
- veto/bail 终止范围：nop 覆盖四级全部（唯一拥有"整个执行"级扩展终止权）；dsh 扩展点封顶于"单 turn"（pre-step reject→blocked），run 级仅 agent.cancel() 命令；pi 封顶于"整个 run"但两级均优雅（shouldStopAfterTurn / terminate 全批）。

## ② 三方机制面清单（census 归类 + 契约映射）

按 WI5 ④4.1 census 全集归类，映射到 S4 契约四类列举：

| S4 契约面 | nop（census 归类） | dsh（census 归类） | pi（census 归类） |
|---|---|---|---|
| lifecycle hook × … | 12 lifecycle point hooks〔契约"lifecycle hook"〕 | agent/* 3 waterfall + 1 serial + 8 emit〔契约"agent waterfall"及 emit 系〕 | ExtensionAPI 34 事件〔契约"ExtensionAPI event"〕 |
| execution middleware × … | 4 ExecutionPoint middleware〔契约"execution middleware"〕；filter chain = `<filter-chain>` DSL 声明式 middleware（W3-2，居洋葱最外层） | tools/* 4 waterfall + 2 emit + session/* 4 + scope 注册面〔契约"tools waterfall"及外围〕 | AgentLoopConfig 9 hook 槽〔契约"AgentLoopConfig hook"〕 |
| DSL 声明扩展 × … | `<hook>`/`<middlewares>`/`<filter-chain>` DSL + talent/skill/contribution 注册面 | —（DSL 形态即 cordis 组成 patch，行序无装载语义） | register* 注册面（tool/command/provider 等） |
| llm/stream / system-prompt × … | IContextCompactor/IModelRouter 等策略对象面（非 hook，契约外补充登记） | llm/stream + system-prompt/assemble waterfall〔契约"llm/stream × system-prompt"〕 | registerProvider〔契约"registerProvider"〕+ stream-options 三子槽 |

未被 ③ 典型行为链穿过的 census 面（显式登记）：nop 事件订阅（observe 扇出，不参与控制流协同）、IBudgetProvider（数据面）；dsh raw change 事件（tools/change、system-prompt/change）；pi 渲染类注册面（TUI 层）。

## ③ 典型行为链的机制面叠加图

### 3.1 nop：三条行为链

**链 1：一次 LLM 调用**（段序与 03 §2.1 P5-P11 一致，标注机制面归属）：

| 段 | 行为 | 机制面 |
|---|---|---|
| 1 | PRE_CALL | session middleware 洋葱（core=hooks 顺序遍历）——veto→整个执行 completed |
| 2 | 治理闸门 cancel→pause→wait→force-stop→goal | 策略对象面（非 hook） |
| 3 | 压缩：PRE_COMPACT **洋葱** → 快照归档 → compact → POST_COMPACT **非 chain 直调 hooks**（先于历史替换执行，其注入会被 clear 丢弃） | middleware+hooks / IContextCompactor |
| 4 | PRE_REASONING 洋葱（veto→跳过本轮）→ 输入护栏 → 预算快照 → route → 熔断感知换模 | hooks / guardrail / 策略对象 |
| 5 | 每次 attempt：PRE_LLM_ATTEMPT 执行级（pass-through，非包裹）→ call 120s 超时 → POST_LLM_ATTEMPT（仅拿到响应对象时触发）→ retryPolicy 裁决 | execution middleware / 策略对象 |
| 6 | 响应落账（checkpoint+事件）→ POST_REASONING 洋葱（bail→re-prompt cap 3）→ 输出护栏 → judge | hooks / guardrail / ICompletionJudge |

锚点：`engine/AgentHookInvoker.java:58-79（洋葱构造：core=invokeHooks）`、`middleware/MiddlewareChain.java:55-61`、`engine/AgentHookInvoker.java:52-56（9 chain 点清单）`、`engine/AgentCompactionCoordinator.java:84,120-132（PRE_COMPACT 洋葱/POST_COMPACT 直调+先于替换）`、`engine/LlmCallCoordinator.java:196-242`。

**链 2：一次工具执行**（03 §2.1 P12 细化，**顺序勘误**：BEFORE 在回填与 checkpoint **之前**）：

```
dispatchLoop（逐工具）：repairer（transform）→ TOOL_CALL_STARTED 事件 → 7-checkpoint evaluate
  ├ DENY → 注入 error response → continue（同批后续工具继续）
  └ DENY_AND_BREAK → break（⟺ 阈值达标 ⟺ 即时 paused）
→ paused 检查（跳过整批 executeAllowedCalls）
→ 建批循环（逐 allowed call）：PRE_TOOL_ATTEMPT 执行级（veto→该工具 error result，不提交）
   → 提交 future + orTimeout(300s)          ← 全部先提交
→ allOf join
→ 逐结果提交循环（按 allowedCalls 序）：
   POST_TOOL_ATTEMPT（veto→结果替换为 error）
   → PRE_ACTING 洋葱（返回值丢弃=无终止力）
   → spill（超限入 store）
   → BEFORE_TOOL_RESULT_PROCESSED 洋葱（Reenter→hook 文本顶替真实结果，跳过回填，cap 3/迭代/点）
   → ctx.addMessage 回填 → TOOL_EXECUTION checkpoint → 会话重同步
   → POST_ACTING 洋葱（丢弃）→ AFTER_TOOL_RESULT_PROCESSED 洋葱（Reenter→仅置标记）
   → TOOL_CALL_COMPLETED 事件
→ 批后：任一 Reenter 请求 → 注入 1 条 user marker
```

锚点：`engine/ReActAgentExecutor.java:862-905`、`engine/AgentToolDispatcher.java:203-215,284-418,430-432`。

**链 3：轮次收口**：judge（Complete/Continue/Escalate，策略对象）→ POST_CALL 洋葱（仅 completed 触发；bail 仅标记）→ EXECUTION_COMPLETED → sustainer（MAX_ITERATIONS 出口，策略对象）→ 引擎 finally 会话回写。

### 3.2 dsh：三条行为链

**链 1：一次模型请求**（**勘误**：agent/request 在 step/start 之后、user/message 落账**之前**——事件文档明示；四个 waterfall 严格顺序 await 零交错）：

| 段 | 行为 | 机制面 |
|---|---|---|
| 1 | inbox.claim（持久 splice + claimed emit） | session 事件 + agent emit |
| 2 | system-prompt/assemble waterfall（**complete-section 内存恢复在 waterfall 后立刻**；durable 物化 system/message 在 step/start+agent/request 之后） | waterfall |
| 3 | runtime-context 快照候选 → step/start → **agent/request** waterfall → prepareCall | waterfall |
| 4 | system/message + user/message 落账（仅首 attempt）→ request/header、request/context 落账 | session 事件 |
| 5 | llm/stream waterfall → adapter → 逐 chunk（每 chunk 后 throwIfAborted）→ assistant/message 落账 | waterfall + session 事件 |

嵌套例外：compaction 的 summarizer 走 `ctx.llm.stream()`（purpose: 'compaction'）——**llm/stream 会嵌套出现在 agent/pre-step（压力压缩）与 agent/request-error（溢出压缩）listener 内部**，但绕过 agent/request。retry（continue）只重入段 3-5；assemble/pre-step 属 step 提议案层不随 attempt 重跑。锚点：`packages/core/agent-loop/src/agent.ts:244-256,302,364-377,530-618`、`packages/core/system-prompt/src/index.ts:617-627`、`packages/core/agent/src/runtime-types.ts:334-336`。

**链 2：一次工具执行**（**勘误**：deny 不是 final-result——跳过 body 但**不跳过 post-execute**）：

```
tool/call 落账 → prepareExecution:
  tools/pre-execute waterfall（allow/deny/ask；ask→approval seam 机制面内映射 allow-once/deny，无服务降级 deny）
  ├ deny → {kind:'post-result', error result}   ← 仍流经 post-execute/finalize 再落账
  └ allow → guards → dispatch
→ tools/execute around waterfall → dispatchToolBody → 归一化（失败也 post-result）
→ finalize: tools/post-execute waterfall（accept/block）
→ materialize → finalizeContent（definition-owned）→ tools/result emit（冻结）
→ tool/result 模型序提交（sourceEventSeqs 回链）
```

真正跳过 post-execute 的 final-result 家族：PTC collapsed 调用（collapse 在策略管线**之前**，pre-execute 看不到）、参数 JSON 物化失败、caller 已取消、pre-execute listener 抛错、tools/execute waterfall 抛错。锚点：`packages/core/tools/src/index.ts:1413-1497,1553-1611,1638-1658`、`packages/core/agent-loop/src/tool-calls.ts:147-161`。

**链 3：错误恢复**：finish error/aborted → assistant/attempt 落账 → agent/request-error waterfall（多 listener 委托链）→ retry 则 continue 重入段 1 链 1 的 request 段（同一 step 不重开；llm-retry 计数按 step/start 清零）。**压缩发生在 request-error listener 内、step 仍开着**（compactSurfaceRegion owner:'current-turn' 要求 openTurn 非空否则抛"no open turn"；回归测试钉死 compaction 三事件严格介于同一 step 的 start/end 之间）；surface replace 推进 replaceGeneration → 重试请求从替换后 surface 派生。锚点：`packages/core/agent-loop/src/agent.ts:443-463`、`packages/compaction/compaction-basic/src/index.ts:180-224`、`packages/compaction/compaction-basic/src/region.ts:197-202`。

### 3.3 pi：三条行为链

**链 1：一次 LLM 调用**（**勘误**：transformHeaders 与 onPayload 不同层；getApiKey 为休眠槽）：

```
session.prompt: /cmd 拦截 → input 事件（handled→不发送）→ skill/模板展开 → auth 预检
  → before_agent_start 事件（只在新 prompt 发，retry/continue 不重发）
→ agent.prompt → runAgentLoop: agent_start/turn_start → 用户消息落盘（message_end）
→ 每 turn streamAssistantResponse:
  ① transformContext 槽 = context 事件桥（链式 pipeline，structuredClone 一次；每 turn 含 retry continue 都触发）
  ② convertToLlm 槽（sdk 装 blockImages 包装，无扩展事件）
  ③ getApiKey 槽（pi 默认未装配=休眠；实际 key 解析在 streamFn 内 applyAuth）
  ④ streamFn → Models.applyAuth（key/认证头）→ transformHeaders 槽（attribution merge + before_provider_headers 事件，models 层）
     → onPayload 槽（before_provider_request 事件，provider api 层）→ HTTP → onResponse（after_provider_response）
     → SSE → message_start/update/end
```

**链 2：一次工具执行**（**勘误**：tool_result 事件在 tool_execution_end **之前**）：

```
assistant message_end（先落盘）→ 逐 toolCall: tool_execution_start
  → prepareToolCall{prepareArguments→validate→beforeToolCall 槽 = tool_call 事件桥（block/mutate，抛错降级 veto）}
  → executePreparedToolCall（tool_execution_update）
  → finalizeExecutedToolCall{afterToolCall 槽 = tool_result 事件桥（改写 content/isError/usage）+ 图片归一}
  → tool_execution_end → toolResult message_start/end（end 落盘）
```

并行路径：end 随各自执行完发，全部完成后再按源序统一发 toolResult 消息事件。锚点：`packages/agent/src/agent-loop.ts:433-487,616-667,713-758`、`packages/coding-agent/src/core/agent-session.ts:485-537`。

**链 3：run 收口恢复循环**：agent_end（session 附 willRetry）→ _handlePostAgentRun{① _isRetryableError（**对溢出显式返回 false——retry 优先于 compaction 且分类器硬互斥**）→ _prepareRetry（摘消息在退避 sleep 之前）→ continue；② retry 耗尽→auto_retry_end(finalError)；③ _checkCompaction（overflow 一次性门闩/threshold）；④ hasQueuedMessages}→ true 则 agent.continue()（重发 agent_start/turn_start，不重发 input/before_agent_start）。锚点：`packages/coding-agent/src/core/agent-session.ts:1074-1116,2770-2861`。

## ④ 冲突裁决与 veto/bail 传播边界

### 4.1 跨机制冲突裁决规则

| 场景 | 裁决规则 | 锚点 |
|---|---|---|
| nop middleware veto vs hooks | **位置性裁决**：middleware 不调 proceed → core（全部 hooks）不执行——middleware veto 结构性优先于该点全部 hooks；middleware 放行后 hook 循环内首个 Veto/Bail/Reenter 短路其余 hooks 并向外传播；最外层 middleware 的最终返回值即聚合结果 | `engine/AgentHookInvoker.java:58-79`、`middleware/MiddlewareChain.java:55-61`、`middleware/IAgentMiddleware.java:25-27` |
| nop 三组 cap | **完全独立**：执行级 veto 3/每次 LLM 调用（方法内局部变量）；POST_REASONING bail 3/per-execute（跨 sustain 不重置）；Reenter 3/迭代/点。谁先到线谁先抛，统一落 execute catch→failed | `engine/LlmCallCoordinator.java:190,765`、`engine/ReActAgentExecutor.java:130,148,428-433` |
| nop 治理闸门竞争 | **固定优先序**：cancel > pause > wait > force-stop > goal（reactLoop 顶部检查序；cancel 注释明示 user-initiated 最高优先）；cancel 与 pause 同时 pending → 终态 cancelled | `engine/ReActAgentExecutor.java:452-532` |
| nop DENY × DENY_AND_BREAK 组合 | DENY_AND_BREAK **当且仅当** denial 阈值达标（此时已即时 paused）；其后的未评估工具既无响应也无审计（配对缺口）；已 ALLOW 工具被 paused 检查跳过整批 | `engine/AgentSecurityConsultation.java:137-143,330-349`、`engine/ReActAgentExecutor.java:878-900` |
| nop PRE_CALL veto 的可辨性 | **缺口**：仅 EXECUTION_COMPLETED 事件 payload 带 vetoedAt；结构化 AgentExecutionResult 无 veto 标记字段（下游只能靠 completed+totalIterations=0 间接推断） | `engine/ReActAgentExecutor.java:410-416,1027-1039`、`engine/AgentExecutionResult.java:60-73` |
| nop 压缩 vs 先前注入 | PRE_COMPACT 阶段注入的消息**进入**压缩输入（能否存活取决于策略）；**POST_COMPACT hook 注入的消息被历史替换 clear 丢弃**（缺口）；veto 本身是一次性控制流，不受压缩影响 | `engine/AgentCompactionCoordinator.java:93-132` |
| dsh 同事件多 listener 异类决策 | waterfall 洋葱：外层不 next()=否决全部内层；外层委托后内层决策作为返回值流回外层可再覆盖——**外层有最终裁决权**；出厂插件以"透传+窄分类"实现容序（llm-retry retryableCodes 不含溢出码；always 模式先委托下游） | `vendor/cordis/src/events.ts:234-247`、`packages/llm/llm-retry/src/index.ts:194-241` |
| dsh 模型切换撕裂防护 | **三联 scoped 监听**（非双）：assemble（around，快照 selection.current→assembled 并改写 variables）+ agent/request（around，读 **assembled 快照**而非 current——step 中途切换不影响本次请求）+ agent/pre-step（prepend 最外层，比对 assembled 与最新 header，不同则追加 durable model-switch notice）——prompt 与 route 读同一快照，切换只在下一 step 生效 | `packages/core/agent/src/model-selection.ts:76-127` |
| dsh pre-step reject 与已认领消息 | **消费即消失**：claimed 消息既不 discarded 也无 canceled 标记、不重排入 pending——下一 turn 拿不到（测试钉死）；rejecter 自己拿到 payload.messages 可自行回队（loop 不代劳）；claim 之后 staged 的消息与后续 next-turn 消息存活 | `packages/core/agent/src/inbox.ts:111-116`、`packages/core/agent/src/runtime-types.ts:287-290`、interception.spec.ts:236-367 |
| dsh deny × block 叠加 | **可叠加且良构**：deny（post-result）仍流经 post-execute——一个 listener pre-execute deny + 另一个 post-execute block 完全合法；deny 决定"没跑"，block 决定"模型最终读到什么"；block 丢弃 body deferred contexts 仅保留 block 决议自带 contexts | `packages/core/tools/src/index.ts:1479-1489,1726-1750` |
| dsh overflow×retry 计数 | **出厂默认互不重复计账**：CONTEXT_WINDOW_EXCEEDED 不在 llm-retry 默认 retryableCodes → 溢出归 compaction（WeakMap per-agent 计数，maxOverflowRetries 默认 1，idle/成功消息清零）；瞬态错归 llm-retry（持久 llm/retry 事件，per-step 预算默认 5）。**配置告警**：勿把 CONTEXT_WINDOW_EXCEEDED 加进 normal retryableCodes——会 shadow 压缩（重试 maxRetries 次全失败才轮到压缩） | `packages/llm/llm/src/retry-policy.ts:14-24`、`packages/compaction/compaction-basic/src/index.ts:124,168-178`、`packages/llm/llm-retry/src/index.ts:130-137` |
| dsh abort vs waterfall | signal 检查点在 loop 代码不在 dispatch：已开始的 listener 不会被放弃，loop await 完成后在下一检查点丢弃；显式检查点覆盖 pre-step 前后/每 chunk 后/request-error 后等；流中 abort→interrupted:true 消息或 assistant/attempt | `packages/core/agent-loop/src/agent.ts:128-135,246,256,391-398,443-461` |
| pi 槽位-事件双通道 | **不能共存**：AgentSession 构造时无条件 `agent.beforeToolCall = emitToolCall`（单槽覆盖，不保留前值）；createAgentSession options 不暴露 beforeToolCall——SDK 用户函数与扩展链无组合点（手工赋值会杀死扩展桥）。对比：prepareNextTurnWithContext 是显式链式（refresh 包装） | `packages/coding-agent/src/core/agent-session.ts:485,540-561`、`packages/coding-agent/src/core/sdk.ts:38-87` |
| pi abort vs auto-retry | 分两层：session.abort() 先 abortRetry 再 agent.abort()——退避 sleep 抛弃、_prepareRetry 返回 false、不会 continue；绕过 session 直接 agent.abort() 是 no-op（activeRun 已结束）且 continue 开新 AbortController 新 run——**"continue 时 agent 已 abort"在正常路径不可达**；pi-ai retry.ts 的"退避中 abort 归一 aborted"只用于摘要调用（retryAssistantCall），不用于主 turn | `packages/coding-agent/src/core/agent-session.ts:1561-1565,2842-2855`、`packages/agent/src/agent.ts:491`、`packages/ai/src/utils/retry.ts:199-209` |
| pi compact cancel 与溢出恢复 | cancel → compaction_end(aborted)+session_compact_failed、返回 false → run 正常结束（无其他恢复路径：溢出被 retry 分类排除、门闩保持直到下一条 user message）；副作用：失败 assistant 消息在 compact 调用**前**已被弹出 agent state（session 文件有、内存没有） | `packages/coding-agent/src/core/agent-session.ts:2114-2119,2202-2217` |
| pi message_end 替换与持久化 | **替换发生在持久化之前**：emitExtensionEvent（emitMessageEnd→_replaceMessageInPlace 原地覆写同一对象）→ _emit UI → appendMessage 落盘——替换后的消息才是落盘消息，后续 turn_end/agent_end 引用一致 | `packages/coding-agent/src/core/agent-session.ts:651-667,721-791` |
| pi terminate × shouldStopAfterTurn | 同一 turn 都触发时 **shouldStopAfterTurn 生效**（先于 steering 轮询与内层 while 结算）；terminate 需全批都 true，且只停工具循环——队列有 steering/followUp 时 run 仍续 | `packages/agent/src/agent-loop.ts:216,247-257,582-584` |

### 4.2 veto/bail 终止范围表（四级粒度：单工具 / 单步 / 单轮 turn / 整个 run）

| 粒度 | nop | dsh | pi |
|---|---|---|---|
| 单工具 | PRE_TOOL_ATTEMPT veto（error result）；POST_TOOL_ATTEMPT veto（结果替换）；BEFORE Reenter（结果顶替）；7-checkpoint DENY（跳过+error response） | tools/pre-execute deny（error result，仍过 post-execute）；tools/post-execute block（结果改写） | tool_call block（error result）；tool_call handler 抛错（降级 veto） |
| 单步（iteration/step/attempt） | PRE_REASONING veto（跳过本轮，计预算）；执行级 veto→retry 决策（单 attempt 重试/换模）；BEFORE Reenter 后 marker 注入影响下一轮 | request-error retry（重入 buildRequest，同一 step 不重开）；request-error 无 retry→step error | ——（pi 无独立"步"粒度；afterToolCall 抛错→单工具 error 属工具级） |
| 单轮 turn | PRE_REASONING 累计可致 truncated；DENY_AND_BREAK 经 pause 断轮；POST_REASONING bail cap 3 触顶→整个执行 failed（跨级） | **pre-step reject→turn blocked（dsh 扩展点最大终止范围）**；request-error 终局→turn error；turn-stopping steer 反对→turn 续跑（反向） | ——（pi turn 不是终止单位；turn_end 后由 run 级机制接管） |
| 整个 run/执行 | **PRE_CALL veto→整个执行 completed**（nop 独有的扩展 run 级终止权）；执行级 veto cap→failed；bail cap→failed；DENY_AND_BREAK→paused（可 resume）；ISustainer 反向（强制续跑）；IWaitCoordinator SUSPEND→waiting 挂起 | **扩展点不可达**（无 abort-bail）；唯一 run 级中断 = agent.cancel() 命令（非扩展点；keepInbox 可保队列；disposed cause 特殊） | shouldStopAfterTurn true→run 优雅退出；beforeToolCall terminate 全批→停工具循环（队列可续）；配置级 hook 抛异常→handleRunFailure 合成 error 收场（**仅 loop 体未捕获异常可达**：convertToLlm/getSteeringMessages 等槽位；工具钩子异常降级单工具级） |

附加粒度行（一方特有）：dsh 单次压缩（compaction 内部 maxOverflowRetries）；pi 单条输入（input handled）、单次压缩（session_before_compact cancel）、项目信任状态（project_trust，remember=true 才持久化）。

### 4.3 结构性对照结论

1. **终止权分布**：nop 把 run 级终止权交给扩展（PRE_CALL veto/bail cap/.threshold pause），dsh 完全收回（命令通道唯一），pi 折中（优雅 run 级 + 错误 run 级分属不同机制）。
2. **跨机制协同的"粘合剂"**：nop=代码位置（静态洋葱嵌套）；dsh=事件委托链+scoped 快照（运行时组合）；pi=槽位桥接+Session 层恢复循环（框架层与应用层分工）。
3. **防撕裂/一致性设计**：dsh 三联监听快照传递 vs pi message_end 先替换后落盘 vs nop checkpoint 分录+会话重同步——三方都显式处理了"多机制共同作用于同一持久状态"的一致性，但 nop 存在两个缺口（PRE_CALL veto 无结构化标记、POST_COMPACT 注入丢弃）。

## ⑤ 权威源引用关系

- 本文档是 **S4（跨扩展机制协同）主题的权威深挖**。**dsh-D2（WI9）/ pi-D2（WI19）** 引用本文档 ③/④ 结论，只写对比增量；冲突时以本文档为准，由 WI29 收敛。
- 与前序专项的分工：03 管流程段序（本文档段序与其一致，超出部分为机制面归属标注）、04 管单点能力、05 管同点顺序、本文档管跨机制叠加与冲突裁决。
- 对 03-flow-agent-loop.md 的两处顺序精化（不构成冲突，属粒度细化）：nop 工具链 BEFORE_TOOL_RESULT_PROCESSED 位于回填与 TOOL_EXECUTION checkpoint 之前（03 P12 段内顺序按本文档链 2 为准）；dsh 链 1 的 agent/request 与 user/message 相对位置、complete-section 内存/持久两层时点按本文档为准。
- 基线注记：dsh=c291e7961a（WI6 重钉延续）；后续 WI 实测 dsh 时须再确认 HEAD。

## References

- `ai-dev/analysis/compare-agent-design/00-dimension-matrix.md`（S4 契约）
- `ai-dev/analysis/compare-agent-design/03-flow-agent-loop.md`、`04-extension-capability-matrix.md`、`05-extension-ordering.md`（前序专项）
- nop：`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/`（AgentHookInvoker/ReActAgentExecutor/LlmCallCoordinator/AgentToolDispatcher/AgentCompactionCoordinator/AgentSecurityConsultation/AgentExecutionResult）、`middleware/`
- dsh：`vendor/cordis/src/events.ts`、`packages/core/agent-loop/src/agent.ts`、`packages/core/agent/src/model-selection.ts`、`packages/core/tools/src/index.ts`、`packages/llm/llm-retry/src/index.ts`、`packages/llm/llm/src/retry-policy.ts`、`packages/compaction/compaction-basic/`（`~/ai/deepseek-harness` @ c291e7961a）
- pi：`packages/agent/src/agent-loop.ts`、`packages/agent/src/agent.ts`、`packages/coding-agent/src/core/agent-session.ts`、`core/extensions/runner.ts`、`core/sdk.ts`、`packages/ai/src/utils/retry.ts`（`~/ai/pi` @ c49906ec7）
