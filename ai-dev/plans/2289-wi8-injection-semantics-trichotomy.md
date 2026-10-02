# 2289 WI8 注入持久化 + 语义三分 + 队列事件化（refactor roadmap M2）

> Plan Status: completed
> Last Reviewed: 2026-10-02
> Source: `ai-dev/backlog/nop-ai-agent-refactor-roadmap.md` WI8（Deliverable: 代码变更 + 测试；deps: WI5, WI7）
> Baseline Ruling: `ai-dev/analysis/2026-10/2026-10-02-wi1-dedup-ruling.md`（WI8 本 roadmap 吸收；autonomous 定位排除 steering 持久化）
> Design Contract: Owner `ai-dev/design/nop-ai-agent/nop-ai-agent-react-engine.md` + `nop-ai-agent-session-and-storage.md`

## Purpose

注入语义三分落地：followup（空闲追加，round 边界消费）/ steer（打断合流，等待期唤醒消费）/ inject（背景注入不触发唤醒，静默入史）；注入队列事件化（入队/排空对消费者可见）；drain-commit 后持久化消除"注入后崩溃丢失"窗口。

## Current Baseline

（执行时 HEAD `81a3b253f7`，2026-10-02 考察锚点）

- **无语义概念**：注入全走裸 `ChatUserMessage`（`InMemoryActorRuntime.toSteeringMessage:504-515`），历史中与真实用户消息不可区分；followup/inject 不存在。
- **消费时机**：steer 语义现状 = `runSteeringWakeupTurn:1284-1359`（awaitWakeup probe `:1213-1214` 对任意排队消息唤醒）；followup 现状 = round 边界 drain `:1664-1672`；inject 通道不存在；heartbeat 关闭模式只有边界 drain。
- **持久化缺口**：两处 drain 均只写 ctx——落盘依赖下一次 LLM_TURN checkpoint（`:1072-1097`）或终态 finally（`DefaultAgentEngine:1001-1005`）；drain 后、checkpoint 前崩溃注入丢失。
- **队列**：`steeringQueue`（ConcurrentLinkedQueue，`AgentExecutionContext:84`）无计数无事件；mailbox 有 pending/inFlight 计数（`DeferredAckMailbox:212-224`）；`AgentEventType` 21 值无队列/注入类事件（`AgentEventType.java:3-128`）；main 源码零事件订阅者（DefaultAgentEventPublisher 同步 fan-out :18-27）。
- **ChatMessage 面**：6 值封闭 `@JsonSubTypes`（`ChatMessage.java:25-33`）——新消息子类会使旧 reader fail-fast（`SessionFileReader` corrupt-file 契约）；基类有 `providerHints` Map（:37-42，已序列化）。enqueueSteering 生产代码无调用方（Actor 走裸队列 `InMemoryActorRuntime:442-445`）。
- **WI7 复用件**：idempotencyKey 两段去重、单消费者纪律、awaitWakeup probe（调用点 lambda 可按语义过滤）。

## Goals

- **语义三分**：新增 `InjectionSemantics`（FOLLOWUP/STEER/INJECT）+ `InjectionEntry`（semantics + ChatMessage 包裹，engine 包）；`AgentExecutionContext.enqueueSteering(ChatMessage, InjectionSemantics)` 重载 + 语义感知 probe（`hasQueuedInjection(InjectionSemantics...)`）。
- **消费规则**：steer → 等待期唤醒轮（快照/commit 均只取 STEER 语义）；followup → 仅 round 边界 drain 消费；inject → 仅 round 边界静默入史（不进唤醒请求、不触发唤醒）。原 Actor 路径默认 STEER（行为保持）。
- **注入持久化（审查 P3 修正：三处提交点）**：session 同步放 `drainCommitSteering` 内部（覆盖唤醒轮正常 + guardrail 分支两处）+ round 边界 drain 之后——三个提交点全覆盖。**单写者论证**：所有 save 均在同一 supplyAsync worker 线程（awaitWakeup 阻塞 worker、wakeup 轮 inline 执行；终态 save 在同 lambda finally），同 JVM 单执行由 runningExecutions 互斥保证（review P9 确认安全）。
- **终态残留处置（审查 P4）**：执行终态同步前 drain 全部残留注入入 ctx——最后边界 drain 之后到终态 finally 之间入队的消息不再丢失。
- **离线注入 staging（审查 P1 Blocker 修正）**：ctx 与其队列是 per-execution 实例（执行结束即销毁）——"会话不在执行时入队"落 session 级 staging：`AgentSession.pendingInjections`（沿 seenInputKeys 三文件先例持久化）；`injectInput` 在会话非 running 时写 staging + save；下次执行的 `buildBaseExecutionContext` 将 staged 注入合并进新 ctx 队列并清空 staging + save。重启语义：staging 随 session 持久化。
- **队列事件化（审查 P2 修正 chokepoint）**：新增 `AgentEventType.INJECTION_QUEUE_UPDATED`（payload: semantics/action=enqueued|drained/pendingCount）。发布点：①ctx 入队（`AgentExecutionContext` 桥接 nullable `IAgentEventPublisher`，`buildBaseExecutionContext` 注入；null 静默）；②executor 排空（hookInvoker.publishEvent 先例）；③actor 入队（`InMemoryActorRuntime` 增 optional publisher，NoOp 默认）。类型波及（Queue<ChatMessage>→Queue<InjectionEntry>）：AgentActor:91/222/235、DefaultAgentEngine:954、AgentSessionLifecycle:357/527/922、TestSteeringInjection:241/300；`AgentToolDispatcher.drainSteering`（死代码，零调用方）删除。
- **engine 注入 API**：`IAgentEngine.injectInput(sessionId, ChatMessage, InjectionSemantics)`（default 抛 NOT_SUPPORTED，照抄 forkSession 先例；实现于 DefaultAgentEngine——会话 running→入队按语义路由 + 事件；非 running→staging 落盘；**幂等键接线为 follow-up**，登记裁定）。

## Non-Goals

- 新 ChatMessage 子类 / `@JsonSubTypes` 扩展（旧 reader fail-fast 不兼容）。
- mailbox 持久化（WI7 已登记边界）。
- 在飞 LLM 打断（Phase B 已裁定不立项）。
- gateway connector 接线（API 面就绪即可，channel 路由属后续）。

## Scope

### In Scope

- engine 包：`InjectionSemantics`/`InjectionEntry`/`AgentExecutionContext` 队列升级与重载/`ReActAgentExecutor` 消费规则与持久化/`AgentEventType.INJECTION_QUEUE_UPDATED`/`DefaultAgentEngine.injectInput` + `IAgentEngine` 声明。
- `InMemoryActorRuntime` 入队点适配 entry 包裹（默认 STEER）。
- 测试：三语义消费路由（steer 唤醒/followup 仅边界/inject 静默入史不唤醒）、drain 后持久化、事件发布（入队/排空 + pendingCount）、null 语义兼容（旧单参重载默认值）。
- owner docs（react-engine.md 注入语义三分节 + session-and-storage.md 注入持久化）+ log。

### Out Of Scope

- nop-ai-api 变更；gateway/connector 路由；mailbox 持久化。
- 长等待延迟语义登记（审查 P5）：heartbeat 模式下长工具等待期间 FOLLOWUP/INJECT 不被消费——延迟上界=工具策略硬上限，登记 owner doc；等待期注入仅在内存队列、drain 前崩溃丢失（pre-drain 残留窗口如实登记为 residual）。
- injectInput 幂等键（审查 P7）：follow-up（WI7 去重挂 AgentMessageRequest 面，injectInput 兼容重载后补）。

## Execution Plan

### Phase 1 - 语义三分与事件化实现

Status: completed
Targets: engine 包、runtime 入队点

- Item Types: `Feature`

- [x] `InjectionSemantics`/`InjectionEntry` + steeringQueue 升级（enqueue 重载/语义感知 probe/drain 适配；Actor 默认 STEER；类型波及 6 处同步；删除 dispatcher drainSteering 死代码）
- [x] 消费规则：steering 唤醒轮仅消费 STEER；边界 drain 消费 FOLLOWUP+INJECT+残留 STEER；inject 不进唤醒请求快照
- [x] 注入持久化：drainCommitSteering 内部 + 边界 drain 后（三提交点全覆盖）+ 终态残留 drain
- [x] 离线 staging：AgentSession.pendingInjections + buildBaseExecutionContext 合并 + injectInput 两分支（running 入队/非 running staging）
- [x] `INJECTION_QUEUE_UPDATED` 事件 + 三发布点（ctx 入队与排空/actor 入队经 optional publisher）+ IAgentEngine.injectInput default NOT_SUPPORTED

Exit Criteria:

- [x] 集成测试：①steer 等待期唤醒（既有 4 场景保持绿）；②followup 不触发唤醒、round 边界入史；③inject 静默入史不触发唤醒；④drain-commit 后（重建 engine）注入消息在 session 历史中可读；⑤事件断言（入队/排空 + payload pendingCount）；⑥离线 injectInput（非 running）→ staging 持久化 → 下次执行消费；⑦终态残留 drain（最后一轮 LLM 期间入队的消息在终态快照中）
- [x] 旧单参 enqueueSteering 兼容（默认语义 STEER）+ 全部既有 steering 测试零回归
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 全绿
- [x] log 条目

### Phase 2 - owner docs 与收口检查

Status: completed
Targets: react-engine.md、session-and-storage.md、log、验证

- Item Types: `Proof`

- [x] react-engine.md 注入语义三分节；session-and-storage.md 注入持久化节
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

Exit Criteria:

- [x] owner docs 在位
- [x] 全量测试通过 + doc-links 0 error

## Closure Gates

> 收口顺序：独立 closure audit 通过 → 勾选 refactor roadmap WI8 checkbox → git commit → 本 plan 标 completed。

- [x] 三语义消费路由端到端连通（入队→语义路由→消费→入史→持久化→事件）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2289-wi8-injection-semantics-trichotomy.md --strict` 退出码 0
- [x] 受影响 owner docs 已同步
- [x] git commit 完成（含全部 In Scope 文件）

## Closure

Status Note: 三语义消费路由/离线 staging/三提交点持久化/终态残留 drain/事件化三发布点全部落地；closure audit 两轮——首轮 FAIL 1 blocker（actor 入队发布点未实现）已修复，复核轮 3 处登记落盘后 PASS——WI8 交付全部落地。
Completed: 2026-10-02

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 agent_07c83051-da1f-4223-a6b0-a677d3072dcb（fresh session，三轮）
- Evidence:
  - 首轮审计 8 验证点 7 PASS + 1 blocker（actor 入队发布点未实现，三处记录与 live 矛盾）
  - 修复：InMemoryActorRuntime.setInjectionEventPublisher + 入队点发布；复核轮 3 处登记落盘（session-and-storage residual 三条/log 第二轮 FAIL 记录/plan Follow-up 两条）后 PASS
  - 附带项全修：drainSteering 实际首语义、injectInput semantics null 防御、staging-vs-终态 save 窄窗 residual 登记、gate-gaps.yaml + gate table injectInput not-applicable 注册、invariant-catalog 文档表滞后 follow-up
  - surefire agent 3466 / 0F / 0E；hollow scan exit 0；doc-links exit 0
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2289-wi8-injection-semantics-trichotomy.md --strict` exit 0（completed 后 0 failed）

Follow-up:

- channel connector 路由到 injectInput（API 面就绪，channel 路由属后续）
- injectInput 幂等键接线（WI7 两段去重挂 AgentMessageRequest 面，兼容重载后补）
- invariant-catalog.md §3.2 文档表滞后（IAgentEngine 10→11 方法缺 injectInput 行）——操作性 gate（测试 TABLE + gate-gaps.yaml）已更新，文档目录表补齐属后续治理
- injectInput 幂等键接线（WI7 去重挂 AgentMessageRequest 面，兼容重载后补）
