# pi-D3 事件类型与触发方式对比

> Status: resolved
> Date: 2026-09-12
> 基线: nop=800baf32da（nop-ai 模块与 c585459f83 diff 为空）、pi=c49906ec7（分析当日实测）
> 引用: 00-dimension-matrix.md（WI2，D3 子机制 D3-1..D3-4）、02-terminology-map.md（T8）; 发射点引用 03 §2.3 事件表，只写对比增量
> Owner: `ai-dev/design/nop-ai-agent/02-execution-model.md`

## ① 结论摘要

- 总裁定：**对方领先**。pi 的事件面在类型化（判别联合）、消费原语（顺序 await sink + EventStream 双消费形态）、UI 桥接（interactive/print/rpc 三模式同源）上系统性领先于 nop 的进程内通知面。
- 关键差异 1（D3-1）：pi AgentEvent 10 变体判别联合 + AgentSessionEvent 会话级扩展（auto_retry_*/compaction_*/queue_update 等应用事件）——双层词表与 dsh 类似但粒度更应用化。
- 关键差异 2（D3-3）：pi 双消费路径并存——SDK/扩展用顺序 await sink（严格串行、可背压、持久化依赖其同步性），应用用 EventStream 异步迭代——nop 单一扇出。
- 关键差异 3（D3-4）：pi 事件不持久化但**持久化与事件同点触发**（message_end 分支内先扩展替换后落盘，06 ④.1）——事件流与账本强一致；nop 事件与快照两轨。
- 可吸收增量建议一句话：nop 吸收 pi 的"事件即持久化触发点"模式与队列状态事件（见 ⑥）。

## ② nop 侧机制与锚点

nop 事实基线与 dsh-D3 报告 ② 节共享：AgentEventType 21 值平面枚举 + AgentEvent 扁平 POJO（Map payload）+ DefaultAgentEventPublisher 同步扇出（异常隔离）+ 全瞬时无事件溯源 + 无内置 UI 桥（`engine/AgentEventType.java`、`engine/DefaultAgentEventPublisher.java:15-27`）。

## ③ pi 侧机制与锚点

按 03 §2.3 事件表与 02 T8，对比增量：

- **词表**（D3-1）：框架层 `AgentEvent` 判别联合 10 变体（agent_start/end、turn_start/end、message_start/update/end、tool_execution_start/update/end，`packages/agent/src/types.ts:428-443`）；应用层 `AgentSessionEvent` 会话级扩展（agent_end 带 willRetry、compaction_start/end、auto_retry_start/end、summarization_retry_*、queue_update、bash_execution_update，`agent-session.ts:144-185`）——框架事件与应用事件分层，应用层可自治扩展词表。
- **发射点**（D3-2）：03 §2.3 表（P1-P14）；每 turn 序列 agent_start→turn_start→message_*→tool_execution_*→turn_end→agent_end；retry 的 continue 重发 agent_start/turn_start（`agent-loop.ts:138-139`）。
- **订阅模型**（D3-3）：`AgentEventSink` 顺序 await（每事件全量重放消息副本，`agent-loop.ts:25`）；`Agent.subscribe()` 监听器按订阅序 await 且纳入 run settlement（`agent.ts:240-253,537-543`——agent_end 后等全部监听器 settle 才算 idle）；`EventStream<T,R>` push/异步迭代/promise 三消费形态（`packages/ai/src/utils/event-stream.ts:4`）。
- **持久化与桥接**（D3-4）：AgentEvent 瞬时不落盘；持久化在 message_end 分支与事件同点（`_handleAgentEvent`：emitExtensionEvent→_emit UI→appendMessage 落盘，`agent-session.ts:651-669`）；UI 桥接三模式（interactive TUI/print/rpc JSON-RPC，`modes/index.ts:7-9`）共享同一事件流；queue_update 等队列状态事件使 UI 实时反映注入队列。

## ④ 子机制逐项对照表

| 子机制 | nop 机制 | pi 机制 | 裁定 | 证据 |
|---|---|---|---|---|
| D3-1 事件词表与分类 | 平面枚举 21 值编译期封闭；Map payload | 判别联合 10 变体（框架）+ 会话级扩展事件（应用自治：auto_retry/queue_update 等）；类型化 payload | 对方领先 | nop `AgentEventType.java`；pi `types.ts:428-443`、`agent-session.ts:144-185`——pi 应用层扩展词表无需改框架（与 dsh ignorable 异曲同工但走继承分层路线）；类型化 payload 优于 Map |
| D3-2 发射点 | 20 个发布点（03 表），治理事件丰富 | 03 §2.3 表（P1-P14），生命周期+流式+队列+恢复事件；retry 重发 agent_start 语义 | 等价 | 03 §2.1 vs §2.3——覆盖面相当；pi 的 willRetry/queue_update 等前瞻事件（恢复前通知 UI）是 nop 无的细节，nop 的治理事件 pi 无 |
| D3-3 订阅模型 | 单一同步扇出+异常隔离 | 顺序 await sink（背压）+EventStream 三消费形态+settlement 语义（agent_end 等监听器） | 对方领先 | nop `DefaultAgentEventPublisher.java:15-27`；pi `agent-loop.ts:25`、`agent.ts:240-253,537-543`、`event-stream.ts:4`——pi 的 settlement 语义（waitForIdle 依赖监听器完成）是持久化正确性的隐式保障，nop 无对位 |
| D3-4 持久化事件 vs 瞬时事件 | 全瞬时；持久化=快照+journal 两轨 | 事件瞬时但与持久化同点触发（先替换后落盘）；三模式 UI 同源消费 | 对方领先 | nop `SessionFileWriter.java:30-46`；pi `agent-session.ts:651-669`（06 ④.1：替换后消息才是落盘消息）——pi 的事件流与账本强一致；nop 事件丢失即丢失（同 dsh-D3 D3-4 结论） |
| （附加）队列状态事件 | 无对位（steeringQueue 无事件） | queue_update 事件实时反映注入队列（`agent-session.ts` _queueSteer/_queueFollowUp） | 对方领先 | pi `agent-session.ts:1390-1419`——注入队列的可观测性 nop 缺失 |

## ⑤ 语义差异与取舍

- **事件的"层"归属**：pi 框架事件（AgentEvent）与应用事件（AgentSessionEvent）显式分层——框架不感知应用语义（auto_retry 是 Session 概念）；nop 的 AgentEventType 把治理语义（SESSION_PAUSED）直接放框架枚举。取舍：pi 分层可移植性好，nop 治理事件一等化表达直接。
- **同步性的隐性契约**：pi 持久化正确性依赖 processEvents 同步 await（监听器不完成 loop 不前进）；nop 的 save 同步完成（另一方向）。两方都选择了"简单但阻塞"的一致性，dsh 选择了"异步+barrier"（D9 交叉）。
- **message_update 的双层嵌套**（02 T4 注记）：pi 应用事件内嵌 provider delta（assistantMessageEvent）——应用事件包 provider 事件；Java 侧常拆两层 listener，对比粒度时注意。
- **不可比项**：pi 的 rpc/print 模式事件序列化协议属 UI/RPC 层（roadmap Non-Goal），只登记不对比。

## ⑥ 裁定 + 可吸收增量建议

**维度总裁定：对方领先**——D3-1/D3-3/D3-4 与附加的队列事件 pi 全面领先（与 dsh-D3 同向）；nop 仅治理事件语义纪律保持局部优势。

可吸收增量建议（仅记录，不实施）：

1. 【来源 pi；针对 nop payload】AgentEvent payload 判别联合化（与 dsh-D3 建议 1 合并设计——dsh 走 ignorable 兼容、pi 走类型化，两者可组合：类型化+可跳过）。
2. 【来源 pi；针对 nop 注入可观测】steering 队列引入 queue_update 式事件（配合 D1 建议 1 的双队列改造），使注入队列对 UI/审计可见。
3. 【来源 pi；针对 nop run 语义】settlement 语义（事件消费完成才算 idle）若 nop 未来引入异步事件消费则必须同步引入（当前同步扇出天然满足，登记为设计约束）。
4. 【来源 nop；反向记录】治理事件四态 Javadoc 纪律（paused/escalated/failed/forced_stop 互斥区分）值得 pi/dsh 生态参考——三方中 nop 的治理语义表达最强。

## References

- `ai-dev/analysis/compare-agent-design/00-dimension-matrix.md`（D3 定义）、`02-terminology-map.md`（T8）、`03-flow-agent-loop.md`（§2.3）、`dsh-D3-events.md`（nop 基线共享）
- `ai-dev/design/nop-ai-agent/02-execution-model.md`（Owner doc）
- nop：`nop-ai/nop-ai-agent/.../engine/AgentEventType.java`、`engine/DefaultAgentEventPublisher.java`
- pi：`packages/agent/src/types.ts`、`packages/coding-agent/src/core/agent-session.ts`、`packages/ai/src/utils/event-stream.ts`（`~/ai/pi` @ c49906ec7）
