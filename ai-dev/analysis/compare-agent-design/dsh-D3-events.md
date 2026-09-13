# dsh-D3 事件类型与触发方式对比

> Status: resolved
> Date: 2026-09-12
> 基线: nop=800baf32da（nop-ai 模块与 c585459f83 diff 为空）、dsh=c291e7961a（分析当日实测）
> 引用: 00-dimension-matrix.md（WI2，D3 子机制 D3-1..D3-4）、02-terminology-map.md（T8）; 专项发射点位置引用 03-flow-agent-loop.md 事件表（03 §2.1/§2.2 各"事件发布点全表"节），只写对比增量
> Owner: `ai-dev/design/nop-ai-agent/02-execution-model.md`

## ① 结论摘要

- 总裁定：**对方领先**。dsh 的事件体系在词表可扩展性、分发原语、持久化/重放能力三个子机制上系统性领先；nop 的事件是轻量进程内通知面。
- 关键差异 1（D3-1 词表）：nop 固定枚举 21 值（编译期封闭）；dsh SessionEvent 声明合并可扩展（本 build 56 类 + `ignorable` 信封实现"词汇增长不 bump 版本"）。
- 关键差异 2（D3-3 订阅）：nop 单一同步扇出（CopyOnWriteArrayList）；dsh 五种分发 mode（emit/serial/waterfall/parallel/bail）按事件语义各就各位。
- 关键差异 3（D3-4 持久化）：nop 事件全瞬时（无事件溯源；持久化=snapshot+journal 分录，重放单位是"消息历史+checkpoint"而非事件流）；dsh 二元分明（SessionEvent 持久化可重放 + log-only/surface 分层）。
- nop 独有价值：治理语义事件（SESSION_PAUSED/ESCALATED/RESTORED/WOKE）的 Javadoc 级语义区分纪律；DeferredAckMailbox 的 3-phase reservation（dsh inbox claim 只有单相）。
- 可吸收增量建议一句话：nop 事件面可吸收 dsh 的类型化事件体与 ignorable 版本演进机制（见 ⑥）。

## ② nop 侧机制与锚点

事件词表、信箱、持久化模型按 2026-09-12 nop 侧补充调研（D3 任务）与 03 §2.1 事件表，对比增量：

- **词表**（D3-1）：`AgentEventType` 21 值平面枚举（`engine/AgentEventType.java:3-128`）——EXECUTION_STARTED/ITERATION_STARTED（未接线）/LLM_RESPONSE_RECEIVED/TOOL_CALL_*/EXECUTION_*/SESSION_*（CREATED/LOADED/CANCEL_*/FORKED/PAUSED/RESUMED/ESCALATED/RESTORED/WOKE）/FORCED_STOP/COMPACTION。治理四态语义互斥有 Javadoc 级纪律（:34-41：paused=ledger 治理、failed=错误、forced_stop=溢出、cancelled=用户）。payload 为扁平 POJO `AgentEvent`（eventType+sessionId+Map payload+error，`engine/AgentEvent.java:5-57`）——**无类型化事件体、无 schema**。
- **发射点**（D3-2）：20 个发布点全表见 03 §2.1 事件表（P1-P16 各阶段）； ITERATION_STARTED/REASONING_CHUNK 无发布点（死点勘误）。
- **订阅模型**（D3-3）：单一同步扇出——CopyOnWriteArrayList 逐订阅者调用、单订阅者异常隔离（`engine/DefaultAgentEventPublisher.java:15-27`）；无 mode 区分（所有事件同一分发语义）。
- **持久化 vs 瞬时**（D3-4）：**全部瞬时**——AgentEvent 无落盘路径（全仓 grep 无持久化代码）；持久化责任分离：消息历史=FileBackedSessionStore 全量快照（`session/SessionFileWriter.java:30-46`"session 是可变聚合状态、全量重写"）+ 恢复点=checkpoint journal（Markdown 分录，`reliability/CheckpointJournalWriter.java:86-104`）。**无事件溯源**：恢复是"整段消息历史重放+checkpoint 校验"，非事件重放。
- **信箱**（跨 agent 消息，非事件但同面）：IMailbox 3-phase reservation（offer/poll/ack-nack，PENDING→IN_FLIGHT→DEAD_LETTERED，`message/IMailbox.java:52-121`）+ DeferredAckMailbox（maxDeliveryAttempts=5，`message/DeferredAckMailbox.java:47-216`）+ DBMessageService（DB 落地 at-least-once，50ms 轮询+stale claim 5min，`message/DBMessageService.java:67-660`）。
- **UI 桥接**：无内置 TUI/RPC 桥；唯一接缝是 nop-ai-gateway 的 ChannelConnectorContext（`nop-ai-gateway/.../channel/ChannelConnectorContext.java:20-41`），当前无生产 addSubscriber 调用。

## ③ 对方侧机制与锚点

按 03 §2.2 事件表与 02 T8，对比增量：

- **词表**（D3-1）：双层——持久层 `SessionEventMap` 声明合并扩展（核心 13 类 + 扩展，本 build KNOWN_SESSION_EVENT_TYPES 共 56 类，`packages/core/session/src/types.ts:269-401`、`known-event-types.ts:22-79`）；瞬时层 agent/* 12 事件 + tools/* 6 + llm/stream 等（cordis 事件）。`ignorable` 信封标记（`types.ts:465-488`）= 未知类型可跳过，词汇增长不 bump 格式版本。
- **发射点**（D3-2）：03 §2.2 事件表（P1-P21）；典型 turn 序列 `agent/status(running)→turn/start→…→turn/end`。
- **订阅模型**（D3-3）：五种 DispatchMode 按事件各就各位（`vendor/cordis/src/events.ts:32`）——session/event=emit（post-commit 馈送）、agent/turn-stopping=serial、agent/pre-step 等=waterfall、session/flush=parallel（allSettled）；dsh contained emit 逐监听者异常包含（`dispatch.ts:120-137`）。
- **持久化 vs 瞬时**（D3-4）：二元分明——SessionEvent append 即真相（append-only log，事件可重放：inbox 投影、runtime-context、llm-retry 计数都从事件重放恢复）；log-only 事件（assistant/attempt、compaction/start）不投影 surface；surface 事件才进模型可见历史。UI 桥接：session/event post-commit 馈送 → TUI/RPC/headless 消费（headless 从 session log 汇总输出，`packages/bundle/headless/src/index.ts:64,207`）。

## ④ 子机制逐项对照表

| 子机制 | nop 机制 | dsh 机制 | 裁定 | 证据 |
|---|---|---|---|---|
| D3-1 事件词表与分类 | 平面枚举 21 值，编译期封闭；payload=Map 无类型；治理四态 Javadoc 语义纪律 | 双层词表（持久 SessionEvent 56 类声明合并可扩展 + 瞬时 cordis 事件）；ignorable 信封版本演进 | 对方领先 | nop `AgentEventType.java:3-128` + `AgentEvent.java:5-57`；dsh `types.ts:269-401,465-488`——声明合并+ignorable 使 dsh 词表可三方生态扩展而 nop 需改枚举编译 |
| D3-2 发射点 | 20 个发布点（03 表），覆盖执行/工具/会话生命周期与治理 | 30+ 派发点（03 表），另含 inbox 变更/request header 变化/压缩事务等细粒度事件 | 等价 | 03 §2.1 vs §2.2 事件表——覆盖面相当；dsh 多出的 header/change 类事件服务于 KV-cache 记账（D6 交叉），nop 无对位需求 |
| D3-3 订阅模型 | 单一同步扇出+异常隔离（全事件同一语义） | 五 mode 按事件语义各就各位（emit/serial/waterfall/parallel）+ contained 封装 | 对方领先 | nop `DefaultAgentEventPublisher.java:15-27`；dsh `events.ts:32,183-243`——dsh 的 mode 化让"通知/裁决/等待屏障"语义显式，nop 一刀切广播 |
| D3-4 持久化事件 vs 瞬时事件 | 全瞬时无事件溯源；持久化=snapshot+journal 双轨（恢复=消息重放+checkpoint 校验，非事件重放） | 持久 SessionEvent append 即真相（可重放：inbox/llm-retry 计数均从事件恢复）+ log-only/surface 分层 | 对方领先 | nop `SessionFileWriter.java:30-46`、`CheckpointJournalWriter.java:86-104`；dsh `session/src/index.ts:446,747-753`——dsh 事件日志同时服务审计、重放、UI 三消费方；nop 事件丢失即丢失 |
| （附加）跨 agent 消息信箱 | IMailbox 3-phase reservation（offer/poll/ack-nack+dead-letter）+ DBMessageService at-least-once | durable Inbox（spliced 溯源+claim 一次性）——面向输入注入而非通用消息 | 等价 | nop `IMailbox.java:52-121`、`DBMessageService.java:67-660`；dsh `inbox.ts:26-78`——机制面不同（通用消息 vs 输入队列），各自自洽；dsh inbox 单相 claim vs nop 3-phase 显式 ack 语义更强 |

## ⑤ 语义差异与取舍

- **"事件"词同义异**（02 T8）：nop AgentEvent 是进程内一次性通知（发完即逝）；dsh SessionEvent 是持久账本条目（append 即真相）。对比任何一方"事件数"时口径完全不同——nop 21 值枚举 vs dsh 56 类持久+20 瞬时。
- **重放语义不互译**：dsh "重放"= 从事件日志重建任意投影（inbox/surface/计数）；nop "重放"= 恢复时整段消息历史重放进 context（`AgentSessionLifecycle.java:164-166`）。nop 的 checkpoint journal 是 Markdown 人读格式（非机器 schema），消费方式是恢复校验而非投影重建。
- **订阅者的能力预期**：dsh 的 mode 化让订阅者签约时就知道自己能否否决（waterfall）还是纯观察（emit）；nop 订阅者一律纯观察——nop 想要裁决语义必须走 hook 通道，两通道界限清晰但事件通道表达能力受限。
- **UI 桥接取舍**：dsh 事件日志天然支持多 UI 重放（headless/TUI/RPC 同源）；nop 事件不持久导致 UI 必须实时在线——离线审计只能靠 session 快照+journal（人读 Markdown）。

## ⑥ 裁定 + 可吸收增量建议

**维度总裁定：对方领先**——词表可扩展性（D3-1）、分发原语（D3-3）、持久化/重放（D3-4）三项 dsh 系统性领先；nop 仅在信箱 ack 语义与治理事件纪律上有局部优势，不足以翻转。

可吸收增量建议（仅记录，不实施）：

1. 【来源 dsh；针对 nop Map payload】为 AgentEvent 引入类型化 payload（判别联合或 sealed interface），并给 SESSION_* 治理事件建立 schema 化 payload 契约（当前 Map<String,Object> 无契约，消费方靠字符串约定）。
2. 【来源 dsh；针对 nop 枚举封闭】评估事件词表的可扩展机制（ignorable 对位：未知事件类型跳过而非反序列化失败），支撑 nop-ai 生态插件在不改核心枚举的前提下新增事件。
3. 【来源 dsh；针对 nop 事件瞬时】评估"关键治理事件持久化"窄方案：仅 SESSION_PAUSED/ESCALATED/FORCED_STOP 写 journal 分录（新增 EVENT 分录类型），获得离线审计能力而不必引入全量事件溯源。
4. 【来源 dsh；针对 nop 分发单一】若未来 nop 事件需要裁决语义，优先复用现有 hook 通道而非给事件加 mode（保持两通道界限——记录该取舍理由供设计参考）。

## References

- `ai-dev/analysis/compare-agent-design/00-dimension-matrix.md`（D3 定义）、`02-terminology-map.md`（T8）、`03-flow-agent-loop.md`（事件发射点表）
- `ai-dev/design/nop-ai-agent/02-execution-model.md`（Owner doc）
- nop：`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/AgentEventType.java`、`message/`、`session/`
- dsh：`packages/core/session/src/{types,index,known-event-types}.ts`、`vendor/cordis/src/events.ts`（`~/ai/deepseek-harness` @ c291e7961a）
