# 3 nop-ai 外部信道集成 — 业务消息层 IChannelMessageService (W2)

> Plan Status: active
> Mission: nop-ai-channel-integration
> Work Item: W2
> Last Reviewed: 2026-08-08
> Source: `ai-dev/backlog/nop-ai-channel-integration-roadmap.md` (W2) · `ai-dev/design/nop-ai-channel-integration-design.md` (§3.2, §3.3, §3.5) · `ai-dev/design/nop-ai-agent/nop-ai-agent-channel-connector.md` (§5)
> Related: 前置 Plan 1 (W0) · 前置 Plan 2 (W1 传输层 `IChannelConnector`/`ChannelConnectorManager`) · 后续 W3 (扫码绑定) / W5 (飞书首信道) / W6 (E2E)

## Purpose

落地业务消息层 `IChannelMessageService`——纯使用层抽象，方法**只以平台身份 userId 为锚**，对调用方屏蔽一切信道协议细节。包含：统一消息模型 `ChannelMessage`（出/入站）、`SendResult` 枚举（`SENT`/`NO_BINDING`/`UNSUPPORTED`）、`UserChannelResolver`（userId → 已绑定信道 + 信道地址）、接口与实现。实现 `ChannelMessageServiceImpl` 经 `UserChannelResolver` 解析 → 选连接器 → 调原生发送。本 plan 完成后，业务调用方可经 `sendToUser(userId, msg)` 推送主动通知（不触发 Agent 推理）。

## Current Baseline

- `nop-integration-api` 已存在，接口（`ISmsSender`/`IEmailSender`/`IQrcodeService`）在 `io.nop.integration.api.{sms,email,qrcode}`，pom **仅依赖 `nop-api-core`**。无任何信道消息相关类型。
- `nop-auth-service` 现有 `NopAuthExtLoginBizModel`（`CrudBizModel`，`nop-auth-service/src/main/java/io/nop/auth/service/entity/`）操作 `NopAuthExtLogin`。pom **不含 `nop-integration-api` 依赖**。
- `NopAuthExtLogin` 实体（`nop-auth/model/nop-auth.orm.xml:179-232`）含 `userId`/`loginType`(int)/`extId`(string)/`verified`(boolean)/`delFlag`/`lastLoginTime` 列。Plan 1 (W0) 将为其加 `(loginType, extId)` 唯一约束 + 扩展 `login-type` 字典（20=飞书 等）。**本 plan 依赖 Plan 1 已落地**。
- `nop-ai-gateway` 已含 `nop-ai-agent` 依赖（Plan 2 / W1 将新增）及 `IChannelConnector`/`ChannelConnectorManager`/`IChannelSessionStore`（Plan 2 产出）。**本 plan 依赖 Plan 2 已落地**。
- `nop-ai-gateway` pom **不含 `nop-integration-api` 依赖**。
- 设计（§3.2）明确：业务消息层方法只以 userId 为锚，**禁止暴露信道协议字段**（拒绝 `sendToChannel(channelAddress,...)`）；未绑定返回 `NO_BINDING` 不抛异常；跨信道降级（飞书失败→短信）是 v1 显式 non-goal，由调用方凭 `SendResult` 自决。

## Goals

- `ChannelMessage` 模型（`OutboundChannelMessage`: text/markdown/attachments/businessRef；`InboundChannelMessage`: userId/channelType/channelAddress/text/rawAttachments/receivedAt）+ `SendResult` 枚举（`SENT`/`NO_BINDING`/`UNSUPPORTED`）落 `nop-integration-api`
- `IChannelMessageService` 接口（`sendToUser(userId, OutboundChannelMessage)→SendResult`、`subscribeInbound(listener)`）+ `UserChannelResolver` 接口（`resolve(userId)→List<ChannelBinding>`、`resolve(userId, channelType)→ChannelBinding`）落 `nop-integration-api`
- `UserChannelResolver` 实现落 `nop-auth-service`（读 `NopAuthExtLogin`，`loginType`↔channelType，`extId`↔信道地址，多绑定默认"最近活跃"=`lastLoginTime`，可指定 channelType 覆盖）
- `ChannelMessageServiceImpl` 实现落 `nop-ai-gateway`（出站经 resolver → 选连接器 → 发送；入站经连接器回调 `dispatchInbound` → 通知监听器）
- 新增 Maven 依赖边无环：`nop-ai-gateway`→`nop-integration-api`、`nop-auth-service`→`nop-integration-api`

## Non-Goals

- 不实现具体信道连接器（`FeishuConnector` 属 W5）；本 plan 的出站发送经 `IChannelConnector` 抽象（Plan 2 产出），用 stub/mock 连接器验证
- 不实现扫码绑定协议（`IChannelBindProvider`/`IChannelBindService` 属 W3）
- 不实现扫码登录（属 W4）
- 不实现跨信道降级编排（飞书失败→短信）—— v1 显式 non-goal
- 不实现 Agent 会话闭环响应（Agent 响应由传输层连接器订阅 `IAgentEventPublisher` 原路回推，不经业务消息层——见设计 §3.2）
- 不改动 `IMessageService`（topic 总线）任何契约
- 不修改 `IAgentEngine`/`AgentMessageRequest` 契约

## Scope

### In Scope

- `nop-integration-api` 新包（如 `io.nop.integration.api.channel`）— `ChannelMessage`/`OutboundChannelMessage`/`InboundChannelMessage`/`SendResult`/`ChannelBinding`/`IChannelMessageService`/`UserChannelResolver`/`IInboundMessageListener`
- `nop-auth-service` — `UserChannelResolver` 实现 + beans.xml 注册 + 新增 `nop-integration-api` 依赖
- `nop-ai-gateway` — `ChannelMessageServiceImpl` + beans.xml 注册 + 新增 `nop-integration-api` 依赖
- 测试（resolver 读绑定 + message service 出站路由 + 未绑定返回 `NO_BINDING`）

### Out Of Scope

- 具体信道连接器实现（W5）
- 扫码绑定/登录（W3/W4）
- `IMessageService` 骨干接入（设计 §3.3 问题 B 的方式二，多消费者部署属 W6-4）
- Agent 会话响应路径（传输层直连，不经业务消息层）

## Execution Plan

### Phase 1 — 业务消息层接口与模型 (nop-integration-api)

Status: planned
Targets: `nop-integration/nop-integration-api/src/main/java/io/nop/integration/api/channel/` (新)

- Item Types: `Fix`

- [ ] 定义 `OutboundChannelMessage`（text/markdown/attachments/businessRef）
- [ ] 定义 `InboundChannelMessage`（userId/channelType/channelAddress/text/rawAttachments/receivedAt）
- [ ] 定义 `SendResult` 枚举（`SENT`/`NO_BINDING`/`UNSUPPORTED`）
- [ ] 定义 `ChannelBinding`（userId/channelType/channelAddress，承载 resolver 解析结果）
- [ ] 定义 `IChannelMessageService` 接口（`sendToUser(userId, OutboundChannelMessage)→SendResult`、`subscribeInbound(IInboundMessageListener)`）
- [ ] 定义 `UserChannelResolver` 接口（`resolve(userId)→List<ChannelBinding>`、`resolve(userId, channelType)→ChannelBinding`）
- [ ] 定义 `IInboundMessageListener` 接口（`onInbound(InboundChannelMessage)`）
- [ ] **依赖纯净度**：确认新类型仅依赖 `nop-api-core`（无 `nop-ai-*`/`nop-auth-*`）

Exit Criteria:

- [ ] 上述 7 个类型存在于 `nop-integration-api` 且可编译
- [ ] `./mvnw compile -pl nop-integration-api -am` 成功
- [ ] **无静默跳过**：`SendResult` 三值语义清晰（`NO_BINDING`/`UNSUPPORTED` 是显式结果非异常）
- [ ] **No new test required**: 纯接口/模型定义，无可验证行为；测试在 Phase 2/3 实现时编写（见 Minimum Rules #25）
- [ ] No owner-doc update required（纯接口新增，尚无使用契约文档）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — UserChannelResolver 实现 (nop-auth-service)

Status: planned
Targets: `nop-auth/nop-auth-service/pom.xml` · `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/channel/` (新) · `nop-auth-service` beans.xml

- Item Types: `Fix`

- [ ] `nop-auth-service/pom.xml` 新增 `nop-integration-api` 依赖（无环验证：`nop-integration-api` 仅依赖 `nop-api-core`）
- [ ] 实现 `UserChannelResolver`：经注入的 `IEntityDao`（或 `IBizObjectManager`）查 `NopAuthExtLogin` by `userId`（过滤 `verified=true AND delFlag=0`），`loginType`↔channelType 映射，`extId`↔channelAddress；`resolve(userId)` 多绑定按 `lastLoginTime` 降序（"最近活跃"默认）；`resolve(userId, channelType)` 指定信道覆盖
- [ ] **Decision：`loginType`(int) ↔ `channelType`(string) 映射定义点**。Plan 1 分配了字典码（`20`=飞书、`21`=钉钉、`22`=企微、`23`=Webhook），但 int→string 映射（如 `20`→`"feishu"`）需一个权威定义点。裁定一种：① 常量类/enum（如 `ChannelTypeCodes`，`nop-integration-api` 或 `nop-auth-service`），或 ② 配置表。裁定须确保 resolver 与连接器 `getChannelType()` 返回值一致。记录裁定于 daily log
- [ ] beans.xml 注册 `UserChannelResolver` bean（参照 `nop-auth-service` 既有 beans.xml 注册模式）
- [ ] 单元测试：mock `NopAuthExtLogin` 数据 → resolver 返回正确 `ChannelBinding` 列表；多绑定按 `lastLoginTime` 排序；指定 channelType 过滤

Exit Criteria:

- [ ] `UserChannelResolver` 实现存在于 `nop-auth-service`，beans.xml 注册
- [ ] **接线验证**：`UserChannelResolver` bean 在 IoC 容器可解析（测试中容器启动 + `getBean` 成功，见 Minimum Rules #23）
- [ ] 测试覆盖：单绑定返回 / 多绑定按 `lastLoginTime` 排序 / 指定 channelType 过滤 / 无绑定返回空列表
- [ ] **无静默跳过**：无绑定返回空列表（非 null），`verified=false`/`delFlag!=0` 行被过滤（非静默包含）
- [ ] `./mvnw test -pl nop-auth-service -am` 通过
- [ ] `nop-auth-service` pom 含 `nop-integration-api` 且无环
- [ ] 若改变 live baseline：相关 design/docs-for-ai 已更新；否则写 `No owner-doc update required`
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 — ChannelMessageServiceImpl 实现 (nop-ai-gateway)

Status: planned
Targets: `nop-ai/nop-ai-gateway/pom.xml` · `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/channel/` · `ai-gateway-defaults.beans.xml`

- Item Types: `Fix | Proof`

- [ ] `nop-ai-gateway/pom.xml` 新增 `nop-integration-api` 依赖（无环验证）
- [ ] 实现 `ChannelMessageServiceImpl`：
  - 出站 `sendToUser(userId, msg)`：经 `UserChannelResolver.resolve(userId)` → 取绑定（空则返回 `SendResult.NO_BINDING`）→ 经 `ChannelConnectorManager`（Plan 2 产出）按 channelType 选连接器 → **把 `OutboundChannelMessage` 转为传输层出站消息载体（Plan 2 定义于 `nop-ai-gateway`）→ 调 `IChannelConnector.sendOutbound(channelAddress, outboundMessage)`** → 返回 `SENT`（或目标信道不支持返回 `UNSUPPORTED`）
  - 入站 `dispatchInbound(InboundChannelMessage)`：通知所有 `IInboundMessageListener`
- [ ] beans.xml 注册 `ChannelMessageServiceImpl` bean（注入 `UserChannelResolver` + `ChannelConnectorManager`）
- [ ] 编写 **stub 连接器 + mock resolver 测试**：
  - `sendToUser` 有绑定 → resolver 返回绑定 → manager 选 stub 连接器 → **stub 的 `sendOutbound` 被调用并收到转换后的出站消息** → 返回 `SENT`
  - `sendToUser` 无绑定 → resolver 返回空 → 返回 `NO_BINDING`（**stub 连接器的 `sendOutbound` 从未被调用**）
  - `dispatchInbound` → 注册的 listener 收到 `InboundChannelMessage`

Exit Criteria:

- [ ] `ChannelMessageServiceImpl` 实现存在于 `nop-ai-gateway`，beans.xml 注册
- [ ] **接线验证**：`ChannelMessageServiceImpl` bean 在 IoC 容器可解析，`UserChannelResolver` + `ChannelConnectorManager` 被注入（见 Minimum Rules #23）
- [ ] **端到端验证**（resolver→manager→connector 链）：测试证明 `sendToUser(有绑定)` 真实路由到 stub 连接器的 `sendOutbound`（调用计数 > 0）且返回 `SENT`；`sendToUser(无绑定)` 返回 `NO_BINDING` 且连接器 `sendOutbound` 从未被调用（见 Minimum Rules #22，用 stub 连接器替代真实信道）
- [ ] **无静默跳过**：无绑定返回 `NO_BINDING` 不抛异常也不静默丢弃；目标信道不支持返回 `UNSUPPORTED` 不静默跳过
- [ ] `./mvnw test -pl nop-ai-gateway -am` 通过
- [ ] `nop-ai-gateway` pom 含 `nop-integration-api` 且无环
- [ ] 若改变 live baseline：相关 design/docs-for-ai 已更新；否则写 `No owner-doc update required`
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] `IChannelMessageService`/`UserChannelResolver` 接口 + `ChannelMessage` 模型落 `nop-integration-api`（仅依赖 `nop-api-core`，不泄漏信道协议字段）
- [ ] `UserChannelResolver` 实现落 `nop-auth-service`（读 `NopAuthExtLogin`，多绑定"最近活跃"默认），bean 可解析
- [ ] `ChannelMessageServiceImpl` 实现落 `nop-ai-gateway`，出站路由（resolver→manager→connector）+ 未绑定 `NO_BINDING` + 入站分发可验证
- [ ] 新增 Maven 依赖边无环（`nop-ai-gateway`→`nop-integration-api`、`nop-auth-service`→`nop-integration-api`，后者仅依赖 `nop-api-core`）
- [ ] 业务层接口不依赖 AI 模块（`IChannelMessageService`/`ChannelMessage` 仅依赖 `nop-api-core`）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [ ] 受影响 owner docs 已同步到 live baseline，或明确写明 No owner-doc update required
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）`sendToUser` 有绑定时真实调用连接器 `sendOutbound`（不只是返回 SENT 常量），（b）无绑定时连接器 `sendOutbound` 从未被调用（返回 NO_BINDING），（c）`dispatchInbound` 真实通知 listener
- [ ] `./mvnw compile -pl nop-integration-api,nop-auth-service,nop-ai-gateway -am`
- [ ] `./mvnw test -pl nop-auth-service,nop-ai-gateway -am`
- [ ] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- 跨信道降级编排（飞书失败→短信）—— v1 显式 non-goal，由调用方凭 `SendResult` 自决（设计 §3.2 拒绝方案表已裁定）
- `IMessageService` 骨干接入（多消费者部署）属 W6-4
- 具体信道连接器（`FeishuConnector`）的出站发送实现属 W5

## Closure

Status Note: (待完成时填写)
Completed: (待定)

Closure Audit Evidence:

- (待 closure audit 时填写)

Follow-up:

- (待 closure audit 时填写)
