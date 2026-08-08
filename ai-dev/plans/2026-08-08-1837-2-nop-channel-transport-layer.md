# 2 nop-ai 外部信道集成 — 传输层 IChannelConnector 抽象与会话映射 (W1)

> Plan Status: active
> Mission: nop-ai-channel-integration
> Work Item: W1
> Last Reviewed: 2026-08-08
> Source: `ai-dev/backlog/nop-ai-channel-integration-roadmap.md` (W1) · `ai-dev/design/nop-ai-agent/nop-ai-agent-channel-connector.md` (§5–§8) · `ai-dev/design/nop-ai-channel-integration-design.md` (§3.5 模块裁定)
> Related: 前置 Plan 1 (W0) · 后续 Plan 3 (W2 业务消息层)、W5 (飞书首信道)

## Purpose

落地传输层抽象 `IChannelConnector`（连接器生命周期、信道能力声明、消息双向流转契约）+ 信道会话映射表 `ai_channel_session` + `ChannelConnectorManager`（注册/查找/统一生命周期）。这是把外部信道（飞书/钉钉/企微/Webhook）接入 Agent 引擎的统一适配层，使新增信道零改引擎。本 plan 完成后，后续 W5 的 `FeishuConnector` 只需实现 `IChannelConnector` 即可接入。

## Current Baseline

- `nop-ai-gateway` 已存在（deps: `nop-gateway`/`nop-ai-api`/`nop-ai-core`），有 `ai-gateway-defaults.beans.xml`。**当前不依赖 `nop-ai-agent`**（传输层上下文 `ChannelConnectorContext` 需持 `IAgentEngine`/`IAgentEventPublisher`，故须新增 `nop-ai-agent` 依赖；经核 `nop-ai-agent` 不依赖 `nop-ai-gateway`，无环）。
- `IAgentEngine.sendMessage(AgentMessageRequest) → AgentMessageAck` 存在于 `nop-ai-agent`（`io.nop.ai.agent.engine`）。
- `IAgentEventPublisher`（`publish`/`addSubscriber`/`removeSubscriber`）存在于 `nop-ai-agent`。
- `AgentMessageRequest`（`nop-ai-agent`）已有字段：`agentName`、`userMessage`、`sessionId`、`metadata`(Map)、`channelKind`(ChannelKind 枚举，`io.nop.ai.agent.security.ChannelKind`)、`principal`。**信道连接器无需扩展引擎层请求模型**——`channelKind`/`metadata` 已就绪。
- `nop-ai/model/nop-ai.orm.xml` 是 AI ORM 权威源（实体在 `io.nop.ai.dao.entity.*`，生成到 `nop-ai-dao`）。`nop-ai-gateway` **当前不依赖 `nop-ai-dao`**——`ai_channel_session` 表的 orm 源位置与实体可达性需裁定（见 Phase 2 Decision）。
- channel-connector 设计文档（`nop-ai-agent-channel-connector.md` §5）定义了 `IChannelConnector` 接口形状（`getChannelType`/`start(ctx)`/`stop()`/`getCapabilities()`）、`ChannelConnectorContext`（`IAgentEngine` + `IAgentEventPublisher` + `ChannelConfig`）、`ChannelCapabilities`（supportsMarkdown/supportsFileUpload/supportsStreaming/supportsGroupChat/maxMessageLength/rateLimitPerMinute 等）、信道会话映射表字段（`channelType`/`channelId`/`sessionId`/`agentName`/`createdAt`/`lastActiveAt`）。本 plan 把这些设计**解析为代码**。
- ORM 变更纪律同 Plan 1：编辑 `model/*.orm.xml` 源 → 重新生成 → 迁移 DDL；**禁止手编 `_gen/`**。

## Goals

- `IChannelConnector`（含**业务主动通知出站发送方法** `sendOutbound`，区别于 Agent 会话响应经 `IAgentEventPublisher` 订阅回推的路径）+ `ChannelConnectorContext` + `ChannelCapabilities` + `ChannelConfig` + 传输层出站消息载体类型落 `nop-ai-gateway`（`nop-ai-gateway` 新增 `nop-ai-agent` 依赖）
- `ai_channel_session` 表 + `IChannelSessionStore`（`findByChannel`/`saveMapping`/`updateLastActive`；**sessionId 来自引擎 ack，store 不自行生成**），ORM 源经重新生成
- `ChannelConnectorManager`（注册/按 channelType 查找/统一 start/stop 生命周期）+ Nop IoC `beans.xml` 装配
- 连接器管理器在运行时真实驱动已注册连接器的生命周期（Anti-Hollow：stub 连接器测试证明 `start`/`stop` 被调用）

## Non-Goals

- 不实现任何具体信道连接器（`FeishuConnector` 属 W5）
- 不实现业务消息层 `IChannelMessageService`（属 W2/Plan 3）
- 不修改 `IAgentEngine`/`IAgentEventPublisher`/`AgentMessageRequest` 任何契约（引擎零改动）
- 不实现消息格式转换、凭证管理、速率限制的厂商具体逻辑（属 W5 各连接器内部）
- 不实现扫码绑定/登录（属 W3/W4）
- 不修改 `nop-ai-gateway` 现有的 `AiDialectBackendMessageConverter` 及其 beans

## Scope

### In Scope

- `nop-ai-gateway/pom.xml` — 新增 `nop-ai-agent` 依赖
- `nop-ai-gateway` 新包（如 `io.nop.ai.gateway.channel`）— `IChannelConnector`/`ChannelConnectorContext`/`ChannelCapabilities`/`ChannelConfig`/`ChannelConnectorManager`/`IChannelSessionStore`/`ChannelSession`
- `nop-ai/model/nop-ai.orm.xml`（或裁定后的 orm 源）— `ai_channel_session` 实体
- `ai-gateway-defaults.beans.xml`（或新增 channel beans）— `ChannelConnectorManager` 注册
- 迁移 DDL（针对 `ai_channel_session` 表）
- 测试（stub 连接器 + 会话映射 + manager 生命周期）

### Out Of Scope

- 具体信道连接器实现（W5）
- 业务消息层接口与实现（W2）
- 扫码绑定/登录（W3/W4）
- `nop-ai-agent` 引擎层任何改动
- 凭证加密（`nop-config-encrypt`）的厂商接线（W5）

## Execution Plan

### Phase 1 — IChannelConnector 抽象与 nop-ai-agent 依赖接线

Status: planned
Targets: `nop-ai-gateway/pom.xml` · `nop-ai-gateway/src/main/java/io/nop/ai/gateway/channel/` (新)

- Item Types: `Fix`

- [ ] `nop-ai-gateway/pom.xml` 新增 `nop-ai-agent` 依赖（无环验证：`nop-ai-agent` 不依赖 `nop-ai-gateway`）
- [ ] 定义 `IChannelConnector` 接口，落 `io.nop.ai.gateway.channel`，方法包括：
  - `getChannelType()` — 信道类型标识（如 `"feishu"`）
  - `start(ChannelConnectorContext)` — 启动连接（webhook 监听/长连接/轮询）
  - `stop()` — 优雅关闭
  - `getCapabilities()` — 声明信道能力
  - **`sendOutbound(channelAddress, outboundMessage)` — 业务主动通知出站发送**（设计 §3.2 业务消息层 `sendToUser` 经此方法推送非 Agent 通知；区别于 Agent 会话响应经 `IAgentEventPublisher` 订阅回推的路径——设计 §7.2）
- [ ] 定义传输层出站消息载体类型（text/markdown/attachments），落 `nop-ai-gateway`（**不依赖 `nop-integration-api`**——业务消息层 `ChannelMessageServiceImpl` 在 Plan 3 负责把 `OutboundChannelMessage` 转为此类型；此设计决策收口了两篇设计文档之间的接口缺口：传输设计 §5 接口无 send 方法，业务设计 §3.2 假设连接器可发送）
- [ ] 定义 `ChannelConnectorContext`（持 `IAgentEngine` + `IAgentEventPublisher` + `ChannelConfig`）
- [ ] 定义 `ChannelCapabilities`（supportsMarkdown/supportsFileUpload/supportsStreaming/supportsGroupChat/maxMessageLength/rateLimitPerMinute 等，参照设计 §8）
- [ ] 定义 `ChannelConfig`（信道配置载体，可持 agentName 等）
- [ ] 单元测试：stub `IChannelConnector` 实现经 context 可访问 `IAgentEngine`/`IAgentEventPublisher`；`sendOutbound` 可被调用并收到参数

Exit Criteria:

- [ ] `IChannelConnector`（含 `sendOutbound`）/`ChannelConnectorContext`/`ChannelCapabilities`/`ChannelConfig`/传输层出站消息载体 类型存在于 `nop-ai-gateway` 且可编译
- [ ] `nop-ai-gateway` pom 含 `nop-ai-agent` 依赖；`./mvnw compile -pl nop-ai-gateway -am` 成功
- [ ] **无静默跳过**：`sendOutbound` 未实现的 stub 抛 `UnsupportedOperationException` 而非空方法体（本 phase 的 stub 仅用于接线测试）
- [ ] 新增功能测试覆盖：stub 连接器测试验证 context 持有 `IAgentEngine`/`IAgentEventPublisher` + `sendOutbound` 可被调用（见 Minimum Rules #25）
- [ ] No owner-doc update required（传输层抽象尚无对外使用契约；若设计文档需更新则同步）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — ai_channel_session 表与 IChannelSessionStore

Status: planned
Targets: `nop-ai` orm 源 · 迁移 DDL · `nop-ai-gateway/src/main/java/io/nop/ai/gateway/channel/`

- Item Types: `Decision | Fix`

- [ ] **Decision：orm 源位置与实体可达性**。`ai_channel_session` 是 gateway 层持久化，但 `nop-ai-gateway` 不依赖 `nop-ai-dao`。裁定一种：
  - 方案 A：表定义加到 `nop-ai/model/nop-ai.orm.xml`（实体 `io.nop.ai.dao.entity.NopAiChannelSession`，生成到 `nop-ai-dao`），`nop-ai-gateway` 新增 `nop-ai-dao` 依赖以访问实体（**注：此新增依赖边须同步更新 roadmap 完成定义的依赖图证明**）
  - 方案 B：gateway 层自建轻量 orm（不走 nop-ai-dao codegen），`IChannelSessionStore` 实现直接用原生 SQL/DAO
  - 裁定须确保 `nop-ai-gateway` 能读写 `ai_channel_session`，且 ORM 纪律（源 → 生成）不被破坏
- [ ] 按裁定编辑 orm 源，声明 `ai_channel_session` 实体（字段：`channelType`/`channelId`/`sessionId`/`agentName`/`createdAt`/`lastActiveAt` + 通用字段），`(channelType, channelId)` 唯一键
- [ ] `mvn clean install -DskipTests -pl nop-ai -am` 触发重新生成，确认 `_gen/`/`_app.orm.xml` 由源驱动，未被手编
- [ ] 定义 `IChannelSessionStore` 接口（**sessionId 来自引擎 ack，store 不自行生成**——设计 §6 映射逻辑）：
  - `findByChannel(channelType, channelId) → ChannelSession|null`（命中复用 + 刷新 `lastActiveAt`）
  - `saveMapping(channelType, channelId, sessionId, agentName)`（未命中时由**连接器**经 `IAgentEngine.sendMessage()` 取得 `AgentMessageAck.sessionId` 后写映射——store 不调引擎）
  - `updateLastActive(channelType, channelId)`
- [ ] 实现 `IChannelSessionStore`（用裁定方案 A/B 的实体/DAO）。**注：`IChannelSessionStore` 由具体连接器经 Nop IoC `@Inject` 注入（不在 `ChannelConnectorContext` 中），连接器经 context 的 `IAgentEngine` 取得 sessionId 后调 `saveMapping`**
- [ ] 编写 **会话映射测试**（本 Phase 内，见 Rule #25）：`findByChannel` 首次 miss 返回 null；`saveMapping` 后 `findByChannel` hit 返回同 sessionId；`updateLastActive` 刷新时间
- [ ] 生成/更新迁移 DDL（目标路径：`nop-ai/deploy/sql/{mysql,postgresql,oracle}/_create_nop-ai.sql` 更新或新建 `_add_channel_session.sql`）

Exit Criteria:

- [ ] `ai_channel_session` 表在 orm 源中声明，`(channelType, channelId)` 唯一键存在
- [ ] 重新生成产物（`_app.orm.xml`/`_gen/`）同步含该实体，未被手编
- [ ] `IChannelSessionStore` 接口（`findByChannel`/`saveMapping`/`updateLastActive`）+ 实现存在于 `nop-ai-gateway`；**sessionId 不由 store 生成**（来自引擎 ack，store 仅持久化）
- [ ] 会话映射测试覆盖：miss→null / saveMapping→hit 复用同 sessionId / updateLastActive 刷新
- [ ] DDL 建表语句存在于 `nop-ai/deploy/sql/` 下且语法正确
- [ ] **无静默跳过**：`findByChannel` 未命中返回 null（明确状态，非抛异常也非静默建空 session）；`saveMapping` 真实持久化映射（非空操作）
- [ ] 若裁定改变 live baseline：相关 design/log 已记录
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 — ChannelConnectorManager + IoC 装配 + 接线验证

Status: planned
Targets: `nop-ai-gateway/src/main/java/io/nop/ai/gateway/channel/` · `ai-gateway-defaults.beans.xml`

- Item Types: `Fix | Proof`

- [ ] 实现 `ChannelConnectorManager`：注册/按 channelType 查找连接器；`startAll(ctx)` 逐个调 `connector.start(ctx)`；`stopAll()` 逐个调 `connector.stop()`
- [ ] 在 `ai-gateway-defaults.beans.xml`（或新增 `channel-connector.beans.xml`）注册 `ChannelConnectorManager` bean
- [ ] 编写 **stub 连接器测试**：注册一个 stub `IChannelConnector`（记录 `start`/`stop` 调用计数）→ `manager.startAll(ctx)` → 断言 stub 的 `start()` 被调用且收到非 null context → `manager.stopAll()` → 断言 `stop()` 被调用

Exit Criteria:

- [ ] `ChannelConnectorManager` bean 在 `ai-gateway-defaults.beans.xml` 注册
- [ ] **接线验证**：测试证明 `manager.startAll(ctx)` 真实调用已注册连接器的 `start()`（stub 计数 > 0），`stopAll()` 真实调用 `stop()`（见 Minimum Rules #23）
- [ ] **stub 集成验证**（manager→connector 链，见 Minimum Rules #22——本 plan 无真实信道，用 stub 连接器替代；真实端到端从外部消息到引擎到回复在 W5/W6 验证）：测试证明 manager 启动 stub 连接器后，连接器经 context 持有 `IAgentEngine`/`IAgentEventPublisher`
- [ ] **无静默跳过**：manager 对未注册/查找失败的 channelType 抛异常或返回明确错误，不静默 `continue`
- [ ] `./mvnw test -pl nop-ai-gateway -am` 通过
- [ ] 若改变了 live baseline：相关 design/docs-for-ai 已更新；否则写 `No owner-doc update required`
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] `IChannelConnector`（含 `sendOutbound` 出站方法）+ `ChannelConnectorContext` + `ChannelCapabilities` + `ChannelConfig` + 传输层出站消息载体 落 `nop-ai-gateway`，`nop-ai-gateway` 已含 `nop-ai-agent` 依赖（无环）
- [ ] `ai_channel_session` 表经 ORM 源声明并重新生成（`_gen/` 未手编），`(channelType, channelId)` 唯一键存在
- [ ] `ChannelConnectorManager` 在 IoC 注册且运行时真实驱动连接器生命周期（stub 测试证明）
- [ ] `IChannelSessionStore` 的 sessionId 来自引擎 ack（store 不自行生成）
- [ ] 引擎层（`nop-ai-agent`）零改动（`IAgentEngine`/`IAgentEventPublisher`/`AgentMessageRequest` 契约未被修改）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [ ] 受影响 owner docs 已同步到 live baseline，或明确写明 No owner-doc update required
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）`ChannelConnectorManager.startAll` 在运行时确实调用连接器（不只是类型注册），（b）`IChannelSessionStore.saveMapping` 真实持久化（非空操作），（c）无空方法体/静默 no-op
- [ ] `./mvnw compile -pl nop-ai-gateway -am`
- [ ] `./mvnw test -pl nop-ai-gateway -am`
- [ ] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- 具体信道连接器（`FeishuConnector`）实现属 W5，本 plan 仅建抽象 + manager + session store
- 凭证加密（`@InjectValue` + `nop-config-encrypt`）的厂商接线属 W5
- 设计文档同步（W7-1）：`nop-ai-agent-channel-connector.md` §5 `IChannelConnector` 接口应补 `sendOutbound` 方法；`nop-ai-channel-integration-design.md` §3.5 模块裁定表 `IChannelConnector` 行由 `nop-ai-agent` 改为 `nop-ai-gateway`；`AgentEventPublisher` 统一为 `IAgentEventPublisher`（传输设计文档全部出现处）

## Closure

Status Note: (待完成时填写)
Completed: (待定)

Closure Audit Evidence:

- (待 closure audit 时填写)

Follow-up:

- (待 closure audit 时填写)
