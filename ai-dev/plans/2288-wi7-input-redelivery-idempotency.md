# 2288 WI7 外部输入 redelivery 幂等（refactor roadmap M2）

> Plan Status: completed
> Last Reviewed: 2026-10-02
> Source: `ai-dev/backlog/nop-ai-agent-refactor-roadmap.md` WI7（Deliverable: 代码变更 + 测试；deps: 无）
> Baseline Ruling: `ai-dev/analysis/2026-10/2026-10-02-wi1-dedup-ruling.md`（WI7 本 roadmap 吸收）
> Design Contract: `ai-dev/design/nop-ai-agent/nop-ai-agent-session-and-storage.md`（Owner）；`ai-dev/design/nop-ai-agent/nop-ai-agent-async-tool-execution.md` §三.3（WI7 幂等重投引用）

## Purpose

建立外部输入双层幂等：at-least-once 投递契约下，同一幂等键的消息重发不重复执行——mailbox 层（envelope correlationId 索引去重）+ session 层（持久化前置的已见键去重）。

## Current Baseline

（执行时 HEAD `4e7b191e21`，2026-10-02 考察锚点）

- **无任何输入去重**：`AgentMessageRequest`（engine/AgentMessageRequest.java:9-16）字段 agentName/userMessage/sessionId/metadata/channelKind/principal——**无 requestId/messageId**；`DefaultAgentEngine:771` 无条件 append user 消息；同一请求重发=完整重复执行（:811-814 runningExecutions.putIfAbsent 仅执行中互斥）。
- **channel 侧**：`FeishuInboundMessage`（nop-integration-feishu）无 message_id 提取（原始事件 id 只在 rawPayload 未用）；connector 放 metadata 的 channelType/channelId/senderId 均非消息 id。
- **mailbox 纯内存 at-most-once**：`DeferredAckMailbox`（nack 重投/dead-letter 机制在位但 nack 生产调用为零）；`InMemoryActorRuntime:458-465` ack 在注入 steering 队列后、任何持久化前——crash 即丢；`MailboxEntry` 有 deliveryId（mailbox 内单调）但 `AgentMessageEnvelope` 无独立 message id（correlationId:26 为唯一稳定 id 槽位，但 `SendMessageExecutor:81-84` 默认随机 UUID）。
- **session 持久化**：`AgentSession` 纯 POJO（session/AgentSession.java:19-94）+ `SessionFileWriter.serialize`（:111-133 手工字段清单，metadata:123/tenantId:127 为后期加字段先例——AgentSession 字段+Writer+Reader 三文件模式）+ `SessionFileReader` 读回；crash-safe 写已就绪（tmp+ATOMIC_MOVE :77-108）；`nop_ai_session_message` 表无外部消息 id 列。
- **入站链路**：`DefaultAgentEngine.sendMessage:669-678`/`execute:705-708` → `assembleExecutionSetup:723-779`（**同步段**：session getOrCreate 后、supplyAsync 前——持久化前置的天然判定点）→ `ctx.addMessage(new ChatUserMessage(request.getUserMessage()))`（:771）。`AgentMessageAck(sessionId, status)`（status 默认 "accepted"）。
- mailbox 测试基建：TestDeferredAckMailbox/TestMailboxMessageHandler/TestMailboxEndToEnd；engine 侧 TestSteeringInjection。

## Goals

- **幂等键载体**：`AgentMessageRequest.idempotencyKey`（String，nullable——null/blank = 不去重，完全向后兼容）；调用方（channel connector/编排方）负责提供稳定键。
- **session 层去重（持久化前置，主机制；审查 B1/M3/M8 修正判定点）**：`AgentSession.seenInputKeys`（LinkedHashMap<String,Long> key→首见时间戳，**有界 FIFO 驱逐**默认 1024）+ SessionFileWriter/Reader 各一行（tenantId 三文件先例）。**判定时序钉定**：①读检查在 `assembleExecutionSetup` 内（:731 getOrCreate 之后、:742 SESSION_CREATED/LOADED 事件之前——duplicate 不发噪音事件、:771 `ctx.addMessage` 之前——重复消息不入 ctx）：键已存在 → 抛 `NopAiAgentException(ERR_AI_AGENT_DUPLICATE_INPUT)`（携带 sessionId）中断 dispatch；②**记录 + `sessionStore.save(session)` 在 `registerExecutionSlot` 成功之后、dispatch 之前**（:712-:713 锁持有窗口内的同步段）——先前置到 assemble 会让"session busy 拒绝/锁失败/异步拒绝"的失败路径永久毒化幂等键（重投被吞，B1）；锁内 save 同时消除与执行终态 save（:934-938"终态先落盘再放锁"不变量）的并发序列化竞态（M3：assemble 段 save 会与 worker 线程并发序列化非线程安全 POJO + running 中途快照可能晚于终态落盘造成 restore 重跑）。save 失败同步抛错=拒绝该输入（at-least-once 下由投递方重试）。
- **duplicate 回传契约（审查 B2 钉定）**：`sendMessage` 路径捕获 ERR_AI_AGENT_DUPLICATE_INPUT → 返回 `AgentMessageAck(sessionId, "duplicate")`；`execute` 路径 future 异常完成（同错误码）——**FeishuConnector 对该错误码静默忽略**（LOG.info + 不回复错误文本；其他异常照常）。错误码 `ERR_AI_AGENT_DUPLICATE_INPUT` 入 NopAiAgentErrors。
- **mailbox 层去重（辅机制；审查 M4 修正清理点）**：`DeferredAckMailbox` 增加 correlationId 索引（`Map<String, Long>`，与 pending/inFlight/deadLetter 同锁）；清理点 = **ack、dead-letter、nack(requeue=false) 三处**（requeue=true 重投**保留**索引）；**duplicate 判定先于容量检查**（满箱时 duplicate 仍返回 true 的 no-op 去重）；`IMailbox.offer` javadoc 契约限定"缓冲型实现：稳定键 duplicate offer == single entry"（NoOpMailbox 恒 false 语义不违约）；blank correlationId 不入索引（`SendMessageExecutor` 随机默认值语义不变——文档钉定"稳定键才可去重"）。
- **at-least-once 契约钉定（含边界登记）**：session-and-storage.md 明确双层分工与四条边界——①mailbox 纯内存 crash 丢消息（根治=mailbox 持久化，范围外）；②出厂默认 InMemorySessionStore 下"持久化前置"退化为内存级（m9）；③seenInputKeys FIFO 驱逐后最老键重投按新消息执行（m6，1024 上限可接受裁定：LRU 不解决重投窗口只增加复杂度）；④**Feishu 首消息重投残留窗口**（M5：channel→session 映射在执行完成后才 save，执行期间重投命中新 sessionId 绕过 session 级去重——登记为已知窗口，connector 映射前置为后续改造）。
- **gateway 接线（最小；审查 m7 钉定规格）**：`FeishuConnector` 从 rawPayload（byte[]，UTF-8 解码）提取 `event.message.message_id`（沿用 extractJsonTextField 极简提取器惯例，不引运行时 JSON 依赖；message_id 为消息级重投稳定 id，优于 event_id）填入 `AgentMessageRequest.idempotencyKey`（提取失败回退 null=不去重，不阻断）。

## Non-Goals

- mailbox 持久化/跨进程重投源（纯内存 crash 丢失的根治——范围外，契约文档登记）。
- `nop_ai_session_message` 加列 / ORM 变更（AgentSession POJO 三文件模式更轻）。
- SendMessageExecutor 的 correlationId 默认值改造（agent 间消息幂等属调用方契约）。
- WI8 注入语义三分（本项只做去重，不改注入语义）。

## Scope

### In Scope

- `AgentMessageRequest.idempotencyKey`；`DefaultAgentEngine` 判定（读检查/记录+save 分置两段）+ duplicate 回传（sendMessage ack / execute 异常）；`NopAiAgentErrors.ERR_AI_AGENT_DUPLICATE_INPUT`。
- `AgentSession.seenInputKeys` + Writer/Reader；FIFO 有界驱逐。
- `DeferredAckMailbox` correlationId 索引 + `IMailbox` javadoc 契约。
- `FeishuConnector` message_id 提取（rawPayload JSON 解析，失败回退 null）。
- 测试：mailbox 重复 offer；session 去重（重复 sendMessage 单条 user 消息 + duplicate ack）；持久化前置（键随 save 落盘、restore 后重投被去重）；FIFO 驱逐；null 键兼容；Writer/Reader 往返。
- owner doc session-and-storage.md（双层幂等节 + at-least-once 契约与边界）+ log。

### Out Of Scope

- nop-ai-api 变更（AgentMessageRequest 属 nop-ai-agent engine 包，非公共 api 模块）。
- 其他 connector（仅 Feishu 接线示范）。

## Execution Plan

### Phase 1 - 双层去重实现

Status: completed
Targets: engine/session/mailbox 三包

- Item Types: `Feature`

- [x] AgentMessageRequest.idempotencyKey + 两段判定（读检查在 assembleExecutionSetup；记录+save 在执行槽注册成功后锁内）
- [x] AgentSession.seenInputKeys（有界 FIFO）+ SessionFileWriter/Reader 往返
- [x] DeferredAckMailbox correlationId 索引 + IMailbox javadoc 契约
- [x] FeishuConnector message_id 提取

Exit Criteria:

- [x] mailbox 单测：稳定键重复 offer → 单 entry + offer 返回 true；ack 后同键可再次入队；**nack(requeue=true) 后同键仍去重（索引保留）；nack(requeue=false) 清理索引**；blank 键不去重；满箱 duplicate 仍 true；索引随 ack/dead-letter 清理
- [x] engine 集成测试：同键两次 sendMessage → 单条 user 消息 + 第二次 ack status="duplicate"；null 键两次 → 两条消息（兼容）；**execute() 路径同键重投 → future 异常完成（ERR_AI_AGENT_DUPLICATE_INPUT）**
- [x] **B1 回归**：session busy 拒绝（键未记录）→ 前一执行完成后同键重投 → 必须执行；**M3**：记录+save 发生在执行槽注册之后（锁内）
- [x] 持久化前置测试：首输入后（save 已发生）重建 engine/store → 同键重投被去重；save 失败注入 → 同步抛错且消息未入 ctx
- [x] Writer/Reader 往返测试（含 FIFO 驱逐边界 + **旧格式文件无该字段读回**）；FeishuConnector 提取单测（rawPayload 含/不含 message_id + execute duplicate 静默处理）
- [x] `./mvnw test -pl nop-ai/nop-ai-agent,nop-ai/nop-ai-gateway -am` 全绿
- [x] log 条目

### Phase 2 - owner doc 与收口检查

Status: completed
Targets: session-and-storage.md、log、验证

- Item Types: `Proof`

- [x] session-and-storage.md 双层幂等节（机制/契约/四条边界：mailbox 内存丢失/默认 store 退化/FIFO 驱逐窗口/Feishu 首消息重投窗口）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

Exit Criteria:

- [x] owner doc 在位
- [x] 全量测试通过 + doc-links 0 error

## Closure Gates

> 收口顺序：独立 closure audit 通过 → 勾选 refactor roadmap WI7 checkbox → git commit → 本 plan 标 completed。

- [x] 双层幂等端到端连通（engine 入口→session 键去重→mailbox 信封去重）
- [x] at-least-once 契约与边界在 owner doc 显式登记
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `./mvnw test -pl nop-ai/nop-ai-agent,nop-ai/nop-ai-gateway -am` 通过
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2288-wi7-input-redelivery-idempotency.md --strict` 退出码 0
- [x] 受影响 owner docs 已同步
- [x] git commit 完成（含全部 In Scope 文件）

## Closure

Status Note: 双层幂等全部落地（session 两段判定+mailbox 索引+Feishu 接线+四条边界登记）；closure audit 首轮 FAIL 1 blocker（读检查误用变异方法，B1 毒化实证复现）已修复（只读 hasSeenInput + 行为化 B1 测试）并复核 PASS——WI7 交付全部落地。
Completed: 2026-10-02

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 agent_26fd0c8f-9512-412d-a26c-e44e47022246（fresh session，两轮）
- Evidence:
  - 首轮审计 7 验证点：4 PASS + 1 blocker（读检查误用 markInputSeen，B1 毒化实证复现）+ 2 部分 FAIL（IMailbox javadoc 未交付、B1 测试空心）
  - 修复：只读 hasSeenInput + 行为化 B1 测试（GatedChatService 真实占槽 + busy 拒绝不记录 + 重投执行）+ IMailbox offer dedup 契约 javadoc + Feishu javadoc 附带修复——复核全 PASS
  - surefire agent+gateway 3657 / 0F / 0E；hollow scan exit 0；doc-links exit 0
  - owner doc 四条边界登记核对 PASS
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2288-wi7-input-redelivery-idempotency.md --strict` exit 0（completed 后 0 failed）

Follow-up:

- mailbox 持久化/跨进程重投源（范围外登记；根治 crash 丢消息）
