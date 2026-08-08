# 7 nop-ai 外部信道集成 — 飞书信道实现 (W5-2 + W5-3 + W5-4 + W7-1)

> Plan Status: completed
> Mission: nop-ai-channel-integration
> Work Item: W5-2, W5-3, W5-4, W7-1
> Last Reviewed: 2026-08-09
> Source: `ai-dev/backlog/nop-ai-channel-integration-roadmap.md` (W5-2/W5-3/W5-4/W7-1) · `ai-dev/design/nop-ai-agent/nop-ai-agent-channel-connector.md` (§7,§11) · `ai-dev/design/nop-ai-channel-integration-design.md` (§3.4)
> Related: 前置 Plan 6 (W5-1 FeishuClient/FeishuPbCodec — **本 plan 的所有 Phase 在 Plan 6 completed 前不可 start**) · 前置 Plan 2 (W1 IChannelConnector/ChannelSessionStore) · 前置 Plan 4 (W3 IChannelBindProvider) · 前置 Plan 5 (W4 扫码登录端点) · 后续 W6 (E2E)

## Purpose

落地飞书信道的完整功能：扫码绑定（`FeishuBindProvider`）、消息流转连接器（`FeishuConnector`）、IoC 装配。本 plan 完成后，飞书作为第一个信道实现完整接入既有基础设施——扫码绑定/登录经 Plan 4/5 的 `IChannelBindService`/`ChannelLoginApi`，消息会话经 Plan 2 的 `IChannelConnector`/`ChannelConnectorManager`/`IChannelSessionStore` 路由到 `IAgentEngine`。同时收口设计文档 §3.5 表与 `IAgentEventPublisher` 命名同步（W7-1）。

> **关键设计裁定（Phase 0 必做）**：传输层设计 §7.2 假设 connector 经事件订阅获取 Agent 响应文本（`event instanceof AgentResult → getText()`）。但经代码核实，`EXECUTION_COMPLETED` 事件 payload 只含 metrics（`totalIterations`/`totalTokensUsed`/`durationMs`/`guardrailBlocked`），**无响应文本**（`ReActAgentExecutor.java:1015-1027`）。`LLM_RESPONSE_RECEIVED` payload 只含 `iteration`/`hasToolCalls`（`:754-757`），同样无文本。响应文本在 `AgentExecutionResult.getMessages()` 中（`finalMessage` 经 `fromContext` 传 null，`:60-73`）。因此 connector **不能**经事件订阅获取响应文本，必须经 `IAgentEngine.execute()` 的 `CompletableFuture<AgentExecutionResult>` 获取。Phase 0 裁定此出站数据流路径并更新设计文档 §7.2。

## Current Baseline

- **前置依赖状态**：Plan 6（飞书协议层）状态为 `draft`。`nop-integration-feishu` 当前只有 `package-info.java` stubs，**无 FeishuClient/FeishuCredentials/IMessageHandler/FeishuInboundMessage 实现**。**本 plan 的所有 Phase 在 Plan 6 completed 前不可 start**。
- **飞书协议层（Plan 6 将产出）**：`FeishuClient`（Stream 长连接 + `sendMessage(receiveId, msgType, content)` + `tenant_access_token` 认证 + `IMessageHandler.onMessage(FeishuInboundMessage)` 回调）、`FeishuPbCodec`、`FeishuCredentials`。
- **传输层接口（Plan 2 / W1 已落地）**：
  - `IChannelConnector`（`nop-ai-gateway/.../channel/IChannelConnector.java:20-65`）：5 方法。`start(ChannelConnectorContext)` / `stop()` / `getChannelType()` / `getCapabilities()` / `sendOutbound(String channelAddress, ChannelOutboundMessage)`
  - `ChannelConnectorContext`（`:17-48`）：持 `IAgentEngine` + `IAgentEventPublisher` + `ChannelConfig`，构造拒绝 null
  - `IChannelSessionStore`（`:28-62`）：`findByChannel(channelType, channelId) → ChannelSession` / `saveMapping(channelType, channelId, sessionId, agentName)` / `updateLastActive(channelType, channelId)`。**无反向查找（sessionId→channelId）方法**
  - `ChannelConnectorManager`（W1-3）：`<ioc:collect-beans by-type>` 自动收集连接器
  - `ChannelCapabilities`（`:12-105`）：`supportsMarkdown`/`supportsFileUpload`/`supportsStreaming`/`supportsGroupChat`/`maxMessageLength`/`rateLimitPerMinute` 等
- **Agent 引擎接口（已存在，本 plan 消费）**：
  - `IAgentEngine.sendMessage(AgentMessageRequest) → AgentMessageAck`（`:10`）—— **fire-and-forget**（`DefaultAgentEngine.java:665-674`：调 `doExecute()` 后立即返回 ack，**不经 future 返回结果**）。connector **不能用此方法获取响应文本**。
  - `IAgentEngine.execute(AgentMessageRequest) → CompletableFuture<AgentExecutionResult>`（`:12`, impl `DefaultAgentEngine.java:702-705`）—— **返回完整执行结果**。`AgentExecutionResult`（`engine/AgentExecutionResult.java:10-58`）含 `status`/`messages(List<ChatMessage>)`/`sessionId`/`error`/`bailReason`。**响应文本在 `messages` 中最后一条 assistant 消息**（`finalMessage` 经 `fromContext` 传 null，`:64`）。
  - `AgentMessageRequest`（`engine/AgentMessageRequest.java:9-26`）：构造 `(agentName, userMessage, sessionId, metadata, channelKind, principal)`
  - `IAgentEventPublisher`（`:3-9`）：`addSubscriber`/`removeSubscriber`/`publish`。**事件 payload 不含响应文本**（见 Phase 0 裁定）。事件仍可用于状态追踪（EXECUTION_STARTED 等），但**不用于获取响应文本**。
  - `ChannelKind`（`security/ChannelKind.java:22-27`）：`WEBUI`/`API`/`DM`/`GROUP`
- **绑定接口（Plan 4 / W3 已落地）**：
  - `IChannelBindProvider`（`nop-integration-api/.../bind/IChannelBindProvider.java:31-72`）：`getChannelType()` / `createBindTicket(channelType, platformUserId) → BindTicket` / `onChannelScanCallback(ChannelScanCallback) → ChannelBindResult`
  - `BindTicket` / `ChannelScanCallback`（`rawPayload` 是 `Map<String,Object>`，provider 解析飞书字段）/ `ChannelBindResult`
- **扫码登录端点（Plan 5 / W4 已落地）**：`ChannelLoginApiBizModel.loginByScanAsync` 经 `IChannelBindProvider.onChannelScanCallback` 编排。`FeishuBindProvider` 注册后即可被扫码登录链消费。
- **模块依赖**：`nop-ai-gateway` 当前依赖 `nop-ai-agent`/`nop-integration-api`/`nop-auth-api`/`nop-biz-auth-core`/`nop-ai-dao`。本 plan 新增 `nop-ai-gateway` → `nop-integration-feishu` 依赖（无环：`nop-integration-feishu` 仅依赖 `nop-integration-api` → `nop-api-core`）。
- **NopIoC 注入约束**：`@Inject` 字段必须是 `protected`/package-private（不支持 `private`）。`IChannelSessionStore` Javadoc（`:24-26`）明确 "injected into connectors via Nop IoC (@Inject on a non-private field); they are NOT part of ChannelConnectorContext"。
- **Open Questions 待收口**：(1) W5-2 QR payload 选型（设计 §六）；(2) 群聊 @机器人过滤策略（设计 §14）；(3) 出站数据流路径（事件 vs execute()——Phase 0 裁定）。
- **设计文档 §3.5 已知 drift**（W7-1）：§3.5 表 `IChannelConnector` 行标注 `nop-ai-agent`（在 `nop-ai-channel-integration-design.md:238`），实际落 `nop-ai-gateway`；设计/roadmap 中 `AgentEventPublisher` 应为 `IAgentEventPublisher`。

## Goals

- **Phase 0 出站路径裁定**：裁定 connector 经 `IAgentEngine.execute()` 获取响应文本（而非事件订阅），更新设计文档 §7.2
- **W5-2 FeishuBindProvider**：实现 `IChannelBindProvider`，生成扫码 QR payload + 处理飞书扫码回调 + 返回 `open_id`
- **W5-3 FeishuConnector**：实现 `IChannelConnector`，入站消息 → `IAgentEngine.execute()`，结果 → 飞书回复（含群聊 @bot 过滤、附件降级）
- **W5-4 IoC 装配**：beans.xml 注册 + `ChannelConnectorManager` 自动收集 + SPI 注册
- **W7-1 设计文档同步**：§3.5 表修正 + `IAgentEventPublisher` 命名统一 + §7.2 出站路径更新

## Non-Goals

- 不重新实现飞书协议层（FeishuClient/FeishuPbCodec）——属 Plan 6
- 不做真实飞书服务器 E2E 测试——属 W6
- 不实现文件上传/下载（设计 §11 "暂不实现"）——附件仅做降级
- 不改动 `IChannelConnector`/`IChannelBindProvider`/`IAgentEngine` 接口契约
- 不实现流式增量推送（飞书不支持流式；`execute()` 返回完整结果后一次性回复）
- 不实现跨信道降级——设计 §四显式拒绝

## Scope

### In Scope

- `nop-integration/nop-integration-feishu/src/main/java/io/nop/integration/feishu/bind/FeishuBindProvider.java`
- `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/channel/feishu/FeishuConnector.java`
- `nop-ai/nop-ai-gateway/pom.xml` — 新增 `nop-integration-feishu` 依赖
- IoC beans.xml 装配（`feishu-connector.beans.xml` 或 gateway defaults）
- 设计文档 §7.2（出站路径）、§3.5（模块归属）、`IAgentEventPublisher` 命名同步

### Out Of Scope

- FeishuClient/FeishuPbCodec 实现（Plan 6）
- 真实飞书连接 E2E（W6）
- 文件上传/下载（设计 §11 deferred）

## Execution Plan

### Phase 0 — 决策：出站数据流路径裁定

> **前置**：Plan 6 completed。本 Phase 裁定 connector 如何获取 Agent 响应文本——这是 Phase 2 实现的前置设计决策。

Status: completed
Targets: `ai-dev/design/nop-ai-agent/nop-ai-agent-channel-connector.md` §7.2 · daily log

- Item Types: `Decision | Proof`

- [x] **Decision：出站数据流路径裁定**。经代码核实，事件 payload 不含响应文本（`EXECUTION_COMPLETED` payload 只有 metrics，`ReActAgentExecutor.java:1015-1027`）。裁定 connector 出站路径：
  - **选定方案：`IAgentEngine.execute()` + future callback**。connector 在收到入站消息后调 `execute(AgentMessageRequest)` → 获得 `CompletableFuture<AgentExecutionResult>` → future 完成时从 `result.getMessages()` 提取最后一条 assistant 消息的文本 → `FeishuClient.sendMessage(chatId, text)` 回复。错误时 `result.getStatus()==failed` 或 future exceptionally completed → 发送错误提示。
  - **拒绝方案：事件订阅**（设计 §7.2 原假设）。事件 payload 不含文本，connector 无法经事件获取响应。事件仍可用于状态追踪（如记录 EXECUTION_STARTED），但不用于获取响应文本。
  - **优势**：execute() 的 future callback 闭包直接捕获 chatId，无需内存反向映射（sessionId→chatId）；无需事件订阅/取消订阅的生命周期管理；无需 TextChunk delta 合并（execute 返回完整结果，飞书不支持流式）。
  - **约束**：单体部署假定（execute 的 future 在同 JVM 完成）。多实例部署需 future 的跨进程传递（后续 plan）。
- [x] 裁定记录写入设计文档 `nop-ai-agent-channel-connector.md` §7.2（更新伪代码为 execute() 路径，注明事件 payload 不含文本的事实 + 拒绝事件订阅方案的理由）

Exit Criteria:

- [x] 出站路径已裁定（`execute()` + future callback），经代码验证的事实（事件 payload 无文本、execute 返回 messages）记录在设计文档 §7.2
- [x] 设计文档 §7.2 原伪代码（`event instanceof AgentResult`）更新为实际可行的 execute() 路径
- [x] **No new test required**: Phase 0 是设计裁定，事实经代码核实（`ReActAgentExecutor.java:1015-1027` + `AgentExecutionResult.java:60-73`），非 throwaway
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 1 — FeishuBindProvider：飞书扫码绑定协议 (W5-2)

> **前置**：Plan 6 completed（FeishuClient 可用，若 QR 选型需要 bot 推送）。

Status: completed
Targets: `nop-integration/nop-integration-feishu/src/main/java/io/nop/integration/feishu/bind/FeishuBindProvider.java` · `nop-integration/nop-integration-feishu/src/test/java/.../bind/TestFeishuBindProvider.java`

- Item Types: `Decision | Fix`

- [x] **Decision：QR payload 选型**（设计 §六 Open Question）。评估：
  - 选项 A：飞书扫码登录二维码（构造 OAuth 授权 URL，不需 bot 推送）
  - 选项 B：自建券 + 机器人推送（需 FeishuClient.sendMessage）
  - 选定后记录到设计文档 §六收口
- [x] 实现 `FeishuBindProvider`（`IChannelBindProvider` 实现，落 `nop-integration-feishu/.../bind/`）：
  - `getChannelType()` → `"feishu"`
  - `createBindTicket("feishu", platformUserId) → BindTicket`：按 QR 选型生成 payload。ticketId 内部维护（内存 Map，有 expiresAt 过期清理）。`status = PENDING`
  - `onChannelScanCallback(ChannelScanCallback) → ChannelBindResult`：从 `callback.rawPayload` 解析飞书回调体，提取 `open_id`，用 ticketId 回查 platformUserId。返回 `ChannelBindResult(extId=open_id, platformUserId, status=BINDING_COMPLETED)`。缺关键字段时抛异常（No Silent No-Op）
- [x] **`FeishuBindProvider` 不依赖 `nop-ai-*`**——只实现 `nop-integration-api` 接口 + 同模块 FeishuClient

Exit Criteria:

- [x] QR payload 选型已做出，记录在设计文档 §六收口
- [x] `FeishuBindProvider` 存在于 `nop-integration-feishu/.../bind/`，实现 3 个接口方法
- [x] **新增功能测试覆盖**（Test-Mandated Feature Rule，用 stub FeishuClient）：
  - [x] `createBindTicketReturnsPendingTicketWithQrPayload`：BindTicket status=PENDING、qrPayload 非空非占位
  - [x] `onCallbackReturnsCompletedWithOpenId`：模拟飞书回调 → ChannelBindResult status=BINDING_COMPLETED、extId 非空
  - [x] `onCallbackWithUnknownTicketFailsExplicitly`：未知 ticketId → 显式失败
  - [x] `onCallbackWithMissingPayloadFailsExplicitly`：rawPayload 缺关键字段 → 显式抛异常（No Silent No-Op）
- [x] **无静默跳过**：缺字段 / 未知 ticket / 过期 ticket 均显式失败
- [x] `grep nop-ai nop-integration-feishu/pom.xml` 为空（依赖纯净）
- [x] `./mvnw test -pl nop-integration-feishu -am` 通过
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — FeishuConnector：飞书消息流转连接器 (W5-3)

> **前置**：Phase 0 出站路径已裁定 + Plan 6 FeishuClient 可用。

Status: completed
Targets: `nop-ai/nop-ai-gateway/pom.xml` · `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/channel/feishu/FeishuConnector.java` · `nop-ai/nop-ai-gateway/src/test/java/.../channel/feishu/TestFeishuConnector.java`

- Item Types: `Fix`

- [x] `nop-ai/nop-ai-gateway/pom.xml` 新增 `nop-integration-feishu` 依赖（取 FeishuClient/FeishuCredentials/FeishuBindProvider，无环）
- [x] 实现 `FeishuConnector`（`IChannelConnector` 实现，落 `nop-ai-gateway/.../channel/feishu/`）：
  - `getChannelType()` → `"feishu"`
  - `getCapabilities()` → 飞书能力声明（supportsMarkdown=true / supportsGroupChat=true / supportsFileUpload=false（v1 deferred）/ rateLimitPerMinute≈3000 / maxMessageLength≈4000）
  - `start(ctx)`：保留 ctx（持 IAgentEngine + IAgentEventPublisher + ChannelConfig）；经 `@Inject`（protected 字段，NopIoC 约束）注入 FeishuClient + IChannelSessionStore（store 不在 context 中，经 IoC 注入）；用 ChannelConfig 凭证初始化 FeishuClient（start 连接）；注册 IMessageHandler 接收入站消息
  - **入站处理**（FeishuClient 回调）：提取 chatId/senderId/文本/chatType；**群聊 @bot 过滤**（群聊未 @bot → 不处理，这是正确语义——不回复无关消息，非"静默跳过应处理逻辑"）；IChannelSessionStore.findByChannel 查映射 → 命中复用 sessionId / 未命中新建；构造 AgentMessageRequest（含 channelKind=DM/GROUP + metadata channelType/channelId/senderId）；调 **`IAgentEngine.execute(request)`**（Phase 0 裁定路径）→ `CompletableFuture<AgentExecutionResult>`
  - **出站处理**（future callback，Phase 0 裁定路径）：future 完成时从 `AgentExecutionResult.getMessages()` 提取最后一条 assistant 消息文本 → `FeishuClient.sendMessage(chatId, text)`；新 session 保存映射（saveMapping + sessionId 来自 result.getSessionId()）；error 时发送错误提示。**chatId 在 future lambda 闭包中直接捕获，无需内存反向映射**
  - `sendOutbound(addr, msg)`：主动通知发送——FeishuClient.sendMessage(addr, text)；附件 + supportsFileUpload=false → 降级为链接或提示文本
  - `stop()`：FeishuClient.stop() + 清理资源
- [x] **单体部署约束声明**：execute() 的 future 在同 JVM 完成。多实例部署需 future 跨进程传递（Deferred）

Exit Criteria:

- [x] `FeishuConnector` 存在于 `nop-ai-gateway/.../channel/feishu/`，实现 `IChannelConnector` 全部方法
- [x] `nop-ai/nop-ai-gateway/pom.xml` 含 `nop-integration-feishu` 依赖且无环
- [x] **新增功能测试覆盖**（Test-Mandated Feature Rule，用 stub FeishuClient + stub IAgentEngine）：
  - [x] `inboundMessageTriggersEngineExecuteAndSessionMapping`：模拟飞书消息 → `IAgentEngine.execute` 被调用（callCount > 0）→ 新 session 写入 IChannelSessionStore
  - [x] `executeResultTriggersFeishuReply`：stub execute future 完成 → FeishuClient.sendMessage 被调用（callCount > 0），回复内容含 result.messages 中的文本
  - [x] `executeFailureTriggersErrorReply`：stub execute future 异常完成 → FeishuClient.sendMessage 被调用，内容含 error
  - [x] `groupMessageWithoutBotMentionIsIgnored`：群聊无 @bot → execute callCount == 0
  - [x] `dmMessageIsProcessed`：私聊 → execute callCount > 0
  - [x] `sendOutboundWithAttachmentDegradesToTextLink`：附件 + supportsFileUpload=false → 降级
- [x] **端到端验证**（Anti-Hollow Rule #22）：`fullInboundToOutboundRoundTrip`——模拟飞书消息到达 → engine.execute → future 完成 → FeishuClient.sendMessage 回复。**从用户入口（飞书消息）到最终输出（飞书回复）完整跑通**（经 stub，调用链真实连通）
- [x] **接线验证**（Wiring Verification Rule #23）：start 后 IMessageHandler 注册到 FeishuClient（handler 注册计数 > 0）；inbound 真实调 engine.execute（callCount 断言）；future callback 真实调 FeishuClient.sendMessage（callCount 断言）
- [x] **无静默跳过**（Rule #24）：stop 后 sendOutbound 显式失败；附件降级有明确行为；future 异常不静默吞掉
- [x] `./mvnw test -pl nop-ai-gateway -am` 通过
- [x] No owner-doc update required（设计文档 §7.2 更新已在 Phase 0 完成）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 — IoC 装配 (W5-4) + 设计文档同步 (W7-1)

> **前置**：Phase 1 + Phase 2 已实现。

Status: completed
Targets: beans.xml · 设计文档 §3.5/§7.2/§14

- Item Types: `Fix | Follow-up`

- [x] 创建 beans.xml 装配：FeishuConnector bean（`@Inject` FeishuClient + IChannelSessionStore）+ FeishuClient bean（凭证 `@InjectValue`）+ FeishuBindProvider bean（`IChannelBindProvider` SPI 经 collect-beans 自动收集）+ FeishuCredentials bean。`@Inject` 字段用 `protected`（NopIoC 约束）
- [x] **运行时装配验证**（Anti-Hollow）：容器启动 → ChannelConnectorManager.getConnector("feishu") 解析成功 + FeishuBindProvider 经 collect-beans 被扫码登录端点发现
- [x] **W7-1 设计文档同步**：
  - [x] `nop-ai-channel-integration-design.md` §3.5 表（`:238`）`IChannelConnector` 行由 `nop-ai-agent` 改为 `nop-ai-gateway`
  - [x] `nop-ai-channel-integration-design.md:257` 散文中 `FeishuConnector` 实现归属由 `nop-integration-feishu` 改为 `nop-ai-gateway`（connector 依赖 `IAgentEngine`/`IChannelSessionStore`，不能下沉到不依赖 AI 的厂商模块）
  - [x] 设计文档 + roadmap 中 `AgentEventPublisher` 统一为 `IAgentEventPublisher`
  - [x] `nop-ai-agent-channel-connector.md` §7.2 已在 Phase 0 更新（execute() 出站路径）；§7.3 中间事件策略（delta 合并/文本增量）因改用 execute() 而不再适用于飞书，标注为 SSE/WebSocket 专用策略
  - [x] 运行 `node ai-dev/tools/check-doc-links.mjs --strict`（修改设计文档后必须）

Exit Criteria:

- [x] beans.xml 注册 FeishuConnector + FeishuClient + FeishuBindProvider + FeishuCredentials
- [x] **新增功能测试覆盖**：IoC 装配测试——容器启动后 getConnector("feishu") 返回非 null + FeishuBindProvider 经 collect-beans 注册
- [x] **接线验证**：beans.xml bean 经容器解析成功（getBean 非 null），FeishuConnector 真实注入 FeishuClient + IChannelSessionStore（字段非 null）
- [x] W7-1 设计文档同步完成：§3.5 表 + IAgentEventPublisher 命名 + §7.2
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `./mvnw test -pl nop-integration-feishu,nop-ai-gateway -am` 通过
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] Phase 0 出站路径裁定（execute() + future callback），设计文档 §7.2 已更新
- [x] W5-2 FeishuBindProvider 实现 + 测试（QR 决策 + 扫码回调解析 open_id + 单测）
- [x] W5-3 FeishuConnector 实现 + 测试（入站→execute() + future→飞书回复 + @bot 过滤 + 附件降级 + 端到端 round-trip）
- [x] W5-4 IoC 装配（beans.xml + 容器解析验证）
- [x] W7-1 设计文档同步（§3.5 表 + IAgentEventPublisher 命名 + §7.2）
- [x] `nop-ai-gateway` → `nop-integration-feishu` 依赖无环
- [x] `nop-integration-feishu` 仍不依赖 `nop-ai-*`
- [x] **Anti-Hollow Check**：FeishuConnector 非空壳（inbound 真实调 execute、future callback 真实调 FeishuClient.sendMessage），FeishuBindProvider 非空壳（回调真实解析 rawPayload）
- [x] 不存在被静默降级的 in-scope live defect
- [x] `./mvnw test -pl nop-integration-feishu,nop-ai-gateway -am` 全绿
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] 独立子 agent closure-audit 已完成并记录证据

## Deferred But Adjudicated

### 真实飞书服务器 E2E 验证

- Classification: `watch-only residual`
- Why Not Blocking Closure: 单测用 stub 验证消息流转路径和接线连通性。真实飞书连接属 W6 E2E 范围。
- Successor Required: `yes`
- Successor Path: `ai-dev/plans/`（W6 E2E plan）

### 文件上传/下载

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 设计 §11 显式标注"暂不实现"。附件降级（转链接/拒绝）在本 plan 实现。
- Successor Required: `no`

### 多实例部署的 execute() future 跨进程传递

- Classification: `optimization candidate`
- Why Not Blocking Closure: execute() 的 CompletableFuture 在同 JVM 完成。单体部署（roadmap 完成定义的默认部署模式）下功能完整。多实例部署需 future 的跨进程传递或改用消息队列回调，属后续部署演进。
- Successor Required: `no`

## Non-Blocking Follow-ups

- 群聊 @bot 过滤的具体解析方式（飞书 event payload 中的 mention 字段）——W6 E2E 时按真实 payload 校准
- 长文本分段发送（设计 §14 Open Question）——超长响应分段策略可在 W6 调优
- `EXECUTION_COMPLETED` 事件 payload 可选用于状态追踪/审计日志（非获取响应文本）

## Closure

Status Note: 飞书信道完整功能落地——FeishuBindProvider（扫码绑定 + open_id 回调解析）+ FeishuConnector（消息流转连接器，入站→execute()→future callback→飞书回复）+ IoC 装配（beans.xml + collect-beans 自动收集）+ 设计文档同步（§7.2 execute() 裁定 + §3.5 模块归属 + IAgentEventPublisher 命名）。Phase 0 裁定 connector 出站路径用 execute() + future callback（事件 payload 无响应文本）。所有 Phase Exit Criteria + Closure Gates 经独立子 agent 审计 PASS。
Completed: 2026-08-09

Closure Audit Evidence:

- Reviewer / Agent: independent subagent (task_id: ses_01d0198a3ffexAdjUzpG76ei7B, explore mode)
- Audit Session: ses_01d0198a3ffexAdjUzpG76ei7B
- Evidence:
  - Phase 0 §7.2/§7.3 update: PASS — `nop-ai-agent-channel-connector.md` §7.2 (L167–194) pseudo-code = execute() + future callback; §7.3 (L196–207) delta-merge 标注 SSE/WebSocket 专用.
  - Phase 1 FeishuBindProvider: PASS — `FeishuBindProvider.java` implements IChannelBindProvider (3 methods); 7 tests (4 required + 3 extra); `grep -c nop-ai pom.xml` = 0.
  - Phase 2 FeishuConnector: PASS — `FeishuConnector.java` implements IChannelConnector (5 methods) + IMessageHandler; 12 tests incl. `fullInboundToOutboundRoundTrip`; Anti-Hollow: onMessage calls execute() (L271, test asserts executeCount==1 sendMessageCount==0); future callback calls feishuClient.sendMessage (L330); no empty bodies / silent catches.
  - Phase 3 IoC: PASS — `feishu-defaults.beans.xml` registers FeishuCredentials + FeishuClient + FeishuBindProvider; `ai-gateway-defaults.beans.xml` has feishuConnector bean; 4 IoC tests (real container start, collect-beans, @Inject fields non-null).
  - W7-1: PASS — §3.5 table IChannelConnector row = `nop-ai-gateway` (L238); no bare `AgentEventPublisher` in channel-connector doc (0 matches for non-I-prefixed pattern).
  - `./mvnw test -pl nop-integration/nop-integration-feishu,nop-ai/nop-ai-gateway -am` = 77 tests green (25 feishu + 52 gateway).
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0 (0 errors).
  - Deferred 项分类检查：真实飞书 E2E (W6)、文件上传 (设计 §11 deferred)、多实例 future 跨进程 — 均为 watch-only/optimization/out-of-scope，无 in-scope live defect 被降级.
  - `node ai-dev/tools/check-plan-checklist.mjs` 退出码 0 (所有 checklist 已勾选).

Follow-up:

- 真实飞书服务器 E2E 验证 (W6 plan)
- 群聊 @bot mention 解析的具体 payload 校准 (W6 E2E)
- 长文本分段发送策略 (W6 调优)
- 设计文档 §1/§11 残留的旧 phrasing (sendMessage/事件订阅概述) — 非 W7-1 scope，留 doc-hygiene pass
