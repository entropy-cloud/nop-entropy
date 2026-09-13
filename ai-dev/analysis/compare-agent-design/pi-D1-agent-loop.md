# pi-D1 内部 agent loop 对比

> Status: resolved
> Date: 2026-09-12
> 基线: nop=800baf32da（nop-ai 模块与 c585459f83 diff 为空）、pi=c49906ec7（分析当日实测）
> 引用: 00-dimension-matrix.md（WI2，D1 子机制 D1-1..D1-5）、02-terminology-map.md（T1-T5）; 专项 03-flow-agent-loop.md §2.3（pi 调用链权威源 P1-P14，本报告流程级细节引用之只写对比增量）
> Owner: `ai-dev/design/nop-ai-agent/nop-ai-agent-react-engine.md`

## ① 结论摘要

- 总裁定：**对方领先**（与 dsh-D1 同向）。pi 的 loop 在输入注入语义（双队列+QueueMode）与优雅停止设计上领先；nop 在治理终止面与预算护栏上独有。
- 关键差异 1（D1-5）：pi 的 steer/followUp 双队列是循环燃料机制（外层 while 由 follow-up 驱动）+QueueMode 排空粒度可配；nop 仅 Actor opt-in steeringQueue 单机制。
- 关键差异 2（D1-2）：pi 的优雅停止三件套（shouldStopAfterTurn hook / terminate 全批 / 队列排空）全部非错误路径；nop 治理态丰富但无 pi 式"工具结果驱动停机"。
- 关键差异 3（D1-4）：pi 流式默认但 **delta 不持久化**（落盘完整消息）——弱于 dsh 的整流记录持久化，强于 nop 的无流式。
- 可吸收增量建议一句话：nop 吸收 pi 的双队列注入模型与"恢复循环"分层（run 层恢复与 loop 层执行分离，见 ⑥）。

## ② nop 侧机制与锚点

nop 事实基线与 dsh-D1 报告 ② 节共享（同为 03 §2.1 权威结论），按本维子机制要点复述：

- 双层计数循环 sustainLoop×reactLoop（`engine/ReActAgentExecutor.java:441-444`，maxIterations 默认 10）+ ISustainer 扩预算（`reliability/ISustainer.java:11-67`，SisypheanSustainer 默认 3 轮）。
- 治理闸门五连检（:452-532：cancel/denial-pause/WAIT_FOR/force-stop/goal-STUCK）+ 9 态终态机 + ICompletionJudge（RuleBased/Llm，`completion/`）。
- 无流式路径（03 ④-8 勘误：REASONING_CHUNK 未接线，IChatService.call 非流式唯一）。
- 输入注入：Actor steeringQueue（`runtime/AgentActor.java:90`）+ ctx.drainSteering（:924-932）——opt-in、内存、无持久化。

## ③ pi 侧机制与锚点

按 03 §2.3（P1-P14）权威结论，对比增量：

- **主循环**（D1-1）：`runLoop` 双层 while（`packages/agent/src/agent-loop.ts:155-279`）——外层只服务 follow-up 排空（:263-268：本该停止时队列非空则续跑），内层处理 assistant 响应+工具批+steering 注入（:174）。loop 是无状态函数；`Agent` 类是有状态包装器（transcript/queues/listeners，`packages/agent/src/agent.ts:167-173`）。**run 后恢复循环在 AgentSession 层**：`_handlePostAgentRun`→`agent.continue()`（`agent-session.ts:1074-1116`）——retry/overflow 恢复与执行循环分层。
- **终止**（D1-2）：`stopReason` 7 值（`packages/ai/src/types.ts:405`，deferred 为 provider 异步句柄协议）；优雅停止三件套——`shouldStopAfterTurn` hook（types.ts:222，AL:247-257）、terminate 全批（AL:582-584）、队列排空；异常收口 `handleRunFailure` 合成 stopReason=error/aborted 消息保证事件序列完整（`agent.ts:511-527`）——**仅 loop 体未捕获异常可达**（工具钩子异常降级为单工具 error 结果）。
- **上限**（D1-3）：无步数上限（AgentLoopConfig 无 maxTurns 字段；应用层 retry.maxRetries=3 只限重试不限步数，`settings-manager.ts:878-884`）。
- **流式**（D1-4）：流式默认（streamFn→Models.streamSimple）；`message_update` 转发 provider delta（11 变体 assistantMessageEvent，`packages/ai/src/types.ts:535-547`）但 **delta 不持久化**——落盘的是 message_end 后完整消息（v3 JSONL 同步写，`agent-session.ts:651-669`）。
- **注入**（D1-5）：`PendingMessageQueue` ×2（steering/followUp，`agent.ts:125-159`），QueueMode `all|one-at-a-time`（默认 one-at-a-time，`types.ts:44-50`）；steer=当前 turn 后下次 LLM 前注入（AL:182-190）、followUp=停止时注入并续跑（AL:263-268）——双队列是一等循环机制；扩展层还有第三档 nextTurn（只入队不触发）。

## ④ 子机制逐项对照表

| 子机制 | nop 机制 | pi 机制 | 裁定 | 证据 |
|---|---|---|---|---|
| D1-1 主循环形态与层级 | 双层计数循环（引擎内闭环；run 即执行） | 双层队列循环 + **AgentSession 层恢复循环**（retry/overflow 在 loop 外以 continue 重入）——执行与恢复分层 | 等价 | nop `ReActAgentExecutor.java:441-444`；pi `agent-loop.ts:155-279`、`agent-session.ts:1074-1116`——骨架同；pi 的恢复分层 vs nop 的 sustainer 内嵌是两种重入位置学（06 ③ 已裁定三分） |
| D1-2 终止条件判定 | ICompletionJudge + 9 态治理终态机 | stopReason 7 值 + shouldStopAfterTurn/terminate 优雅停止 + handleRunFailure 事件序列保全 | nop 领先 | nop 治理终态（paused/escalated/waiting）pi 全无；pi 的优雅停止三件套与合成消息机制精细但无治理语义（`types.ts:222` vs nop `AgentEventType.java:34-107`） |
| D1-3 迭代上限与延长 | maxIterations+ISustainer 扩预算 | 无上限（队列排空即停；retry.maxRetries 只限重试） | nop 领先 | 与 dsh-D1 D1-3 同向：预算护栏 nop 独有；pi 设计立场与 dsh 一致 |
| D1-4 流式输出 | 无流式路径（勘误） | 流式默认+delta 事件转发，但 delta 不持久化（落盘完整消息） | 对方领先 | pi `agent-loop.ts:308-361`、`agent-session.ts:651-669`——流式 UX nop 全缺；pi 的持久化保真弱于 dsh 整流记录（三方梯度：dsh 最强/pi 中/nop 无） |
| D1-5 输入注入/steering | Actor steeringQueue 单机制（opt-in/内存/无模式） | steer/followUp 双队列+QueueMode 可配+扩展 nextTurn 三档——一等循环燃料 | 对方领先 | pi `agent.ts:125-159,283-290`、`types.ts:44-50`；nop `AgentActor.java:90`——pi 队列模式粒度（一次排空 vs 逐条）nop 无对位 |

## ⑤ 语义差异与取舍

- **恢复循环的位置学**（06 ③ 核心结论的三方完整版）：nop 在引擎内（sustainer 出口咨询）、dsh 在 step 内 while 重入（buildRequest 重走）、pi 在 Session 层（run 后 continue，摘除 error 消息）。pi 的分层使 loop 本体保持纯净（无重试概念），代价是恢复逻辑要跨层协调（摘消息+门闩+重建）。
- **"停机"的数据驱动谱系**：nop 计数器/judge 判定 → pi 队列排空+显式 hook → dsh inbox 排空+concludesTurn 数据标记。pi 的 shouldStopAfterTurn/terminate 是"协商式停机"（工具与 hook 都能提议），nop 的 FORCED_STOP 是"命令式停机"。
- **continue 词义陷阱**（02 伪差异警示）：pi `continue()` 是约束续跑（末消息必须可续），且 retry 的 continue 会重发 agent_start/turn_start 事件（扩展会看到多个 agent_start）——不能直译为 nop 的"恢复执行"。
- **事件重复语义**：pi retry 重发 agent_start（`agent-loop.ts:138-139`），nop 单次执行单发 EXECUTION_STARTED——消费方按事件计数做统计时两方语义不同。
- **deferred stopReason**（pi 独有）：provider 异步任务句柄协议（`types.ts:409-419`）在 nop/dsh 无对位——登记不硬比。

## ⑥ 裁定 + 可吸收增量建议

**维度总裁定：对方领先**——D1-4/D1-5 pi 领先（流式默认+双队列注入），D1-2/D1-3 nop 领先（治理终态+预算护栏），D1-1 等价；与 dsh-D1 结论同向但 pi 的领先幅度小于 dsh（流式持久化弱于 dsh）。

可吸收增量建议（仅记录，不实施）：

1. 【来源 pi；针对 nop 注入单机制】将 steering 升级为双队列模型（steering=本轮边界消费 / followup=停止后续跑）并支持排空模式配置（对位 QueueMode），与建议 2（dsh 三语义）合并设计。
2. 【来源 pi；针对 nop 恢复内嵌】评估把 LLM 重试的"摘除失败响应+重入"模式抽象为独立的 run 恢复层（当前 nop 在 Coordinator 内部循环重试，失败升级与主循环耦合；pi 分层使 loop 纯净可测）。
3. 【来源 pi；针对 nop 优雅停机】工具结果 terminate 提议式停机（全批一致才停）可作为 nop completion judge 的 RuleBased 补充信号——judge 已有 CONTINUE 注入机制，terminate 是其反向。
4. 【来源 nop；反向记录】pi 无预算护栏与治理暂停，无人值守部署需外层守护（与 dsh-D1 建议 3 同旨）。

## References

- `ai-dev/analysis/compare-agent-design/00-dimension-matrix.md`（D1 定义）、`02-terminology-map.md`（T1-T5）、`03-flow-agent-loop.md`（§2.3）、`dsh-D1-agent-loop.md`（nop 基线共享）
- `ai-dev/design/nop-ai-agent/nop-ai-agent-react-engine.md`（Owner doc）
- nop：`nop-ai/nop-ai-agent/.../engine/ReActAgentExecutor.java`、`reliability/`
- pi：`packages/agent/src/agent-loop.ts`、`agent.ts`、`types.ts`、`packages/coding-agent/src/core/agent-session.ts`（`~/ai/pi` @ c49906ec7）
