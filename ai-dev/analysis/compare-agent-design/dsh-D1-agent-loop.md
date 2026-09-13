# dsh-D1 内部 agent loop 对比

> Status: resolved
> Date: 2026-09-12
> 基线: nop=800baf32da（nop-ai 模块与 c585459f83 diff 为空）、dsh=c291e7961a（分析当日实测）
> 引用: 00-dimension-matrix.md（WI2，D1 子机制 D1-1..D1-5）、02-terminology-map.md（T1-T5）; 专项 03-flow-agent-loop.md（执行流程权威源，本报告流程级细节全部引用之，只写对比增量）
> Owner: `ai-dev/design/nop-ai-agent/nop-ai-agent-react-engine.md`

## ① 结论摘要

- 总裁定：**对方领先**。dsh 的 loop 在流式工程化、输入注入语义、事件括号完整性上系统性领先；nop 在治理终止面与预算护栏上独有。
- 关键差异 1（D1-4 流式）：dsh 全链路流式 + 整流记录持久化（结算一次性落 `assistant/attempt` 或含完整 stream 记录的 `assistant/message`，token 级重放保真）；nop **无流式路径**（REASONING_CHUNK/ITERATION_STARTED 已声明未接线，非流式 IChatService.call 唯一路径）——03 ④-8 勘误结论。
- 关键差异 2（D1-3 上限）：roadmap 初步假设"三者都无硬性步数上限"**被推翻**——nop 有 maxIterations（默认 10）+ ISustainer 扩预算机制；dsh 设计上无上限（inbox 排空即停）。
- 关键差异 3（D1-5 注入）：dsh followup/steer/inject 三语义 × next-turn/next-step 双边界 × 持久化 inbox 是一等循环机制；nop 仅 Actor opt-in 的 steeringQueue 单机制。
- nop 独有价值：治理闸门五连检（cancel/pause/wait/force-stop/goal-STUCK）与 9 态终态机（dsh 仅 6 值 TurnEndReason 且无治理暂停态）。
- 可吸收增量建议一句话：为 nop 补齐流式执行路径并接线 REASONING_CHUNK，同时为 steering 消息建立持久化队列（见 ⑥）。

## ② nop 侧机制与锚点

主循环层级与治理面按 03 §2.1（P1-P16）权威结论，此处只列对比相关增量：

- **双层计数循环**：外层 sustainLoop `while(true)`（`engine/ReActAgentExecutor.java:441-442`）× 内层 reactLoop `while (getCurrentIteration() < getMaxIterations())`（:443-444，默认 maxIterations=10，`engine/AgentExecutionContext.java:85-94`）。每轮 sustain 经 `ISustainer.onStop(MAX_ITERATIONS)`（:958-982）咨询：CONTINUE → `maxIterations += originalMax`（计数不重置，预算扩展语义）→ 重入；STOP → truncated。默认 NoOpSustainer 恒 STOP；SisypheanSustainer 默认最多 3 轮（`reliability/SisypheanSustainer.java:74`）。
- **治理闸门**（每 iteration 顶部五连检）：cancel（:452）→ denial-pause（:466）→ WAIT_FOR suspend（:478-509）→ force-stop（:511，预调用估算 >0.9×maxContextTokens）→ goal-STUCK（:528）——终态机 9 态：completed/failed/truncated/escalated/paused/waiting/cancelled/forced_stopped/vetoed。
- **流式**：无（03 ④-8 勘误：`hook/AgentLifecyclePoint.java:11` REASONING_CHUNK 与 `engine/AgentEventType.java:6` ITERATION_STARTED 均无触发点；LLM 调用唯一路径 `IChatService.call` 同步非流式，`nop-ai-api/.../chat/IChatService.java:16-19,33`）。
- **输入注入**：Actor steeringQueue（`runtime/AgentActor.java:90`，volatile Queue<ChatMessage>）经 InMemoryActorRuntime 消费循环注入（`runtime/InMemoryActorRuntime.java:390-493`），执行器在每 iteration 工具后、下一轮 LLM 前 `ctx.drainSteering()`（:924-932，`engine/AgentExecutionContext.java:318-325`）——opt-in（engine 绑定后生效）、追加式不唤醒语义、不持久化（内存 Queue）。
- Owner doc 交叉核对：`ai-dev/design/nop-ai-agent/nop-ai-agent-react-engine.md` 的 ReAct 循环与双层结构描述与代码一致；其"Sustainer 无限续跑"表述需按 SisypheanSustainer 默认上限 3 轮修正（登记 daily log，非本报告范围）。

## ③ 对方侧机制与锚点

主循环层级按 03 §2.2（P1-P21）权威结论，对比增量：

- **三层队列循环**：driver `while (await this.turn())`（`packages/core/agent-loop/src/agent.ts:225-238`）× turn（:269-343，事件括号 turn/start…turn/end + TurnEndReason）× step（:352-498，step/start…step/end）。**无步数上限**（grep maxTurns/maxSteps 零命中）：turn 结束条件 = turnEnds 置位且 inbox.nextStep 排空（:315-319）——数据决定，非计数。
- **TurnEndReason 6 值**（`packages/core/session/src/types.ts:200-224`）：completed/aborted（+CancelCause user|parent|hook|disposed）/blocked/error/max-tokens（粘性，agent.ts:307-310）/interrupted（只由崩溃修复合成）。
- **流式**：全链路流式——`StreamChunk` 7 变体（`packages/llm/llm/src/types.ts:390-403`）；流式期间经 AssistantStreamAttempt 累积 + `agent/assistant-stream` 瞬时帧（agent.ts:386），结算时一次性持久化 `assistant/attempt`（agent.ts:421-447）或含完整 stream 记录的 `assistant/message`（:474-483）——整流记录持久化实现 token 级重放保真（原逐 chunk `assistant/chunk` 事件已删除，替代为 assistant/attempt，`session/src/types.ts:335`）+ BlockAssembler 装配；abort 时 interruptedBlocks 落 `interrupted:true` 消息（:402-419）。
- **输入注入**：durable Inbox 双边界（`packages/core/agent-loop/src/inbox.ts`，三语义契约声明于 `packages/core/agent/src/runtime-types.ts:215-241`）：followup→next-turn（新独立 turn）/ steer→next-step（最近 step 边界消费并唤醒）/ inject→next-step 不唤醒；`agent/inbox/spliced` 持久事件溯源（:238）+ claim 一次性取走（:111-115）——被拒绝 step 的消息"消费即消失"（06 ④）。

## ④ 子机制逐项对照表

| 子机制 | nop 机制 | dsh 机制 | 裁定 | 证据 |
|---|---|---|---|---|
| D1-1 主循环形态与层级 | 双层计数循环（sustainLoop×reactLoop），每 iteration = 一次 LLM+工具 fan-out；无 turn/step 事件括号（ITERATION_STARTED 未接线） | 三层队列循环（driver×turn×step），turn/step 均有持久化事件括号；step = 一次模型调用+其工具批 | 等价 | nop `ReActAgentExecutor.java:441-444`；dsh `agent.ts:225-343`——骨架同为"治理→调用→工具→回填"，层级划分与事件粒度取舍不同（02 T1：三者不对位，禁止直译） |
| D1-2 终止条件判定 | 显式判定器 `ICompletionJudge`（RuleBased/Llm）+ 9 态终态机（含治理态 paused/escalated/waiting） | 无判定器组件：无工具调用自然收口 + 6 值 TurnEndReason；`concludesTurn` 工具结果标记可终结 turn | nop 领先 | nop 治理终止面（pause/escalated/waiting/forced_stop）为 dsh 全无（nop `AgentEventType.java:34-107` Javadoc 显式四态区分 vs dsh `types.ts:200-224`）；nop judge 支持 LLM 语义判定完成（`completion/LlmCompletionJudge.java:69-110`），dsh 只靠"无工具调用" |
| D1-3 迭代上限与延长 | maxIterations（默认 10）+ ISustainer 扩预算（SisypheanSustainer 默认 3 轮）；超限→truncated | 无上限（inbox 排空即停）；max-tokens 粘性是唯一内建护栏 | nop 领先 | nop `ReActAgentExecutor.java:958-995`、`reliability/ISustainer.java:11-67`；dsh grep maxTurns/maxSteps 零命中——预算护栏是自主执行安全的显式机制，dsh 缺失（设计立场：数据决定，但无兜底上限） |
| D1-4 流式输出 | **无流式路径**（REASONING_CHUNK 死点；非流式 call 唯一路径；事件枚举无 chunk 值） | 全链路流式 + StreamChunk 7 变体 + 整流记录持久化（token 级重放）+ 中断固化 interrupted:true | 对方领先 | 03 ④-8 勘误（nop）；dsh `llm/src/types.ts:390-403`、`agent.ts:389-431`、`session/src/types.ts:335`——显著差距 |
| D1-5 输入注入/steering | Actor steeringQueue（opt-in、内存、无持久化、单机制、消费点固定 iteration 末） | durable Inbox 三语义（followup/steer/inject）×双边界（next-turn/next-step）×唤醒控制×spliced 事件溯源×QueueMode 对位无 | 对方领先 | nop `runtime/AgentActor.java:90`、`ReActAgentExecutor.java:924-932`；dsh `agent-loop/src/inbox.ts:111-115,238`、`agent.ts:137-147`——dsh 注入是一等持久机制，nop 是附加能力且无 followup/inject 区分 |

## ⑤ 语义差异与取舍

- **计数 vs 队列**（T1/T3 口径）：nop 的 iteration 是受预算约束的计数量词，"何时停"由计数器+判定器决定；dsh 的 turn/step 是事件括号，"何时停"由 inbox 数据决定（"Data decides"，`runtime-types.ts:377-381`）。这是两种停止哲学：nop 适合不可信环境下的预算护栏，dsh 适合交互式会话的自然收口——对比 D1-3 裁定时不得把 dsh 的"无上限"直译为"缺失"，它是与 durable inbox 配套的自洽设计（但确实无兜底）。
- **治理态词同义异**（02 伪差异警示）：nop paused（governance 可恢复）≠ dsh blocked（pre-step 拒绝）；nop escalated（terminal）≠ dsh error（可重试）。四方词表不互译。
- **chunk 持久化的代价取舍**：dsh 为 token 级重放保真付出存储与写入放大（整条流记录随结算一次性写入）；nop 非流式则完全放弃流式 UX 与中断内容保真——dsh 的 `interruptedBlocks` 机制（中断固化部分内容）在 nop 无任何对位。
- **steering 语义不互译**（02 T5）：dsh steer ≠ abort（边界消费）；nop steering 同样不打断——两者语义一致但机制位阶不同（一等 vs opt-in）。

## ⑥ 裁定 + 可吸收增量建议

**维度总裁定：对方领先**——流式（D1-4）与输入注入（D1-5）两项 dsh 显著领先，主循环形态与终止判定各有取舍（等价/nop 领先），但 dsh 的 loop 在工程完成度（事件括号完整性、中断保真、队列持久化）上整体领先。

可吸收增量建议（仅记录，不实施，不排期）：

1. 【来源 dsh；针对 nop 无流式】为 ReAct 路径接通流式：消费 `IChatService.callStream` 并接线 REASONING_CHUNK lifecycle point 与 chunk 级事件，评估整流记录持久化（assistant/attempt / 含完整 stream 记录的 assistant/message 对位，c291e7961a 结构迁移）以获得中断内容保真（interrupted:true 对位）。
2. 【来源 dsh；针对 nop steering 非持久】将 Actor steeringQueue 升级为持久化队列（session 关联 + spliced 事件溯源对位），并区分 followup（新执行）/steer（本轮边界消费）/inject（静默上下文）三语义。
3. 【来源 nop 自身经验；针对 dsh 无上限——反向记录】dsh 可参考 nop 的 sustainer 模式为无人值守场景增加可选预算护栏（记录供 dsh 侧参考，非 nop 行动项）。
4. 【来源 dsh；针对 nop 终态粒度】max-tokens 粘性语义（已触顶的 turn 不能被后续成功降级为 completed）可吸收进 nop 的终态机，防止截断结果被误标完成。

## References

- `ai-dev/analysis/compare-agent-design/00-dimension-matrix.md`（D1 子机制定义）、`02-terminology-map.md`（T1-T5）、`03-flow-agent-loop.md`（流程权威源）
- `ai-dev/design/nop-ai-agent/nop-ai-agent-react-engine.md`（Owner doc）
- nop：`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/ReActAgentExecutor.java`、`reliability/`、`runtime/`
- dsh：`packages/core/agent-loop/src/agent.ts`、`packages/core/session/src/types.ts`、`packages/llm/llm/src/types.ts`、`packages/core/agent-loop/src/inbox.ts`（`~/ai/deepseek-harness` @ c291e7961a）
