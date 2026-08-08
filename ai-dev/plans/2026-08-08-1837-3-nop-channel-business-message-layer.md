# 3 nop-ai 外部信道集成 — 业务消息层 IChannelMessageService (W2)

> Plan Status: completed
> Mission: nop-ai-channel-integration
> Work Item: W2
> Last Reviewed: 2026-08-09
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

Status: completed
Targets: `nop-integration/nop-integration-api/src/main/java/io/nop/integration/api/channel/` (新)

- Item Types: `Fix`

- [x] 定义 `OutboundChannelMessage`（text/markdown/attachments/businessRef）
- [x] 定义 `InboundChannelMessage`（userId/channelType/channelAddress/text/rawAttachments/receivedAt）
- [x] 定义 `SendResult` 枚举（`SENT`/`NO_BINDING`/`UNSUPPORTED`）
- [x] 定义 `ChannelBinding`（userId/channelType/channelAddress，承载 resolver 解析结果）
- [x] 定义 `IChannelMessageService` 接口（`sendToUser(userId, OutboundChannelMessage)→SendResult`、`subscribeInbound(IInboundMessageListener)`）
- [x] 定义 `UserChannelResolver` 接口（`resolve(userId)→List<ChannelBinding>`、`resolve(userId, channelType)→ChannelBinding`）
- [x] 定义 `IInboundMessageListener` 接口（`onInbound(InboundChannelMessage)`）
- [x] **依赖纯净度**：确认新类型仅依赖 `nop-api-core`（无 `nop-ai-*`/`nop-auth-*`）

Exit Criteria:

- [x] 上述 7 个类型存在于 `nop-integration-api` 且可编译（+ `ChannelTypeCodes` 收口 Phase 2 Decision 的映射定义点）
- [x] `./mvnw compile -pl nop-integration-api -am` 成功
- [x] **无静默跳过**：`SendResult` 三值语义清晰（`NO_BINDING`/`UNSUPPORTED` 是显式结果非异常）
- [x] **No new test required**: 纯接口/模型定义，无可验证行为；测试在 Phase 2/3 实现时编写（见 Minimum Rules #25）
- [x] No owner-doc update required（纯接口新增，尚无使用契约文档）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — UserChannelResolver 实现 (nop-auth-service)

Status: completed
Targets: `nop-auth/nop-auth-service/pom.xml` · `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/channel/` (新) · `nop-auth-service` beans.xml

- Item Types: `Fix`

- [x] `nop-auth-service/pom.xml` 新增 `nop-integration-api` 依赖（无环验证：`nop-integration-api` 仅依赖 `nop-api-core`）
- [x] 实现 `UserChannelResolver`：经注入的 `IEntityDao`（或 `IBizObjectManager`）查 `NopAuthExtLogin` by `userId`（过滤 `verified=true AND delFlag=0`），`loginType`↔channelType 映射，`extId`↔channelAddress；`resolve(userId)` 多绑定按 `lastLoginTime` 降序（"最近活跃"默认）；`resolve(userId, channelType)` 指定信道覆盖
- [x] **Decision：`loginType`(int) ↔ `channelType`(string) 映射定义点**。Plan 1 分配了字典码（`20`=飞书、`21`=钉钉、`22`=企微、`23`=Webhook），但 int→string 映射（如 `20`→`"feishu"`）需一个权威定义点。裁定一种：① 常量类/enum（如 `ChannelTypeCodes`，`nop-integration-api` 或 `nop-auth-service`），或 ② 配置表。裁定须确保 resolver 与连接器 `getChannelType()` 返回值一致。记录裁定于 daily log
- [x] beans.xml 注册 `UserChannelResolver` bean（参照 `nop-auth-service` 既有 beans.xml 注册模式）
- [x] 单元测试：mock `NopAuthExtLogin` 数据 → resolver 返回正确 `ChannelBinding` 列表；多绑定按 `lastLoginTime` 排序；指定 channelType 过滤

Exit Criteria:

- [x] `UserChannelResolver` 实现存在于 `nop-auth-service`，beans.xml 注册
- [x] **接线验证**：`UserChannelResolver` bean 在 IoC 容器可解析（测试中容器启动 + `getBean` 成功，见 Minimum Rules #23）
- [x] 测试覆盖：单绑定返回 / 多绑定按 `lastLoginTime` 排序 / 指定 channelType 过滤 / 无绑定返回空列表
- [x] **无静默跳过**：无绑定返回空列表（非 null），`verified=false`/`delFlag!=0` 行被过滤（非静默包含）
- [x] `./mvnw test -pl nop-auth-service -am` 通过
- [x] `nop-auth-service` pom 含 `nop-integration-api` 且无环
- [x] 若改变 live baseline：相关 design/docs-for-ai 已更新；否则写 `No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 — ChannelMessageServiceImpl 实现 (nop-ai-gateway)

Status: completed
Targets: `nop-ai/nop-ai-gateway/pom.xml` · `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/channel/` · `ai-gateway-defaults.beans.xml`

- Item Types: `Fix | Proof`

- [x] `nop-ai-gateway/pom.xml` 新增 `nop-integration-api` 依赖（无环验证）
- [x] 实现 `ChannelMessageServiceImpl`：
  - 出站 `sendToUser(userId, msg)`：经 `UserChannelResolver.resolve(userId)` → 取绑定（空则返回 `SendResult.NO_BINDING`）→ 经 `ChannelConnectorManager`（Plan 2 产出）按 channelType 选连接器 → **把 `OutboundChannelMessage` 转为传输层出站消息载体（Plan 2 定义于 `nop-ai-gateway`）→ 调 `IChannelConnector.sendOutbound(channelAddress, outboundMessage)`** → 返回 `SENT`（或目标信道不支持返回 `UNSUPPORTED`）
  - 入站 `dispatchInbound(InboundChannelMessage)`：通知所有 `IInboundMessageListener`
- [x] beans.xml 注册 `ChannelMessageServiceImpl` bean（注入 `UserChannelResolver` + `ChannelConnectorManager`）
- [x] 编写 **stub 连接器 + mock resolver 测试**：
  - `sendToUser` 有绑定 → resolver 返回绑定 → manager 选 stub 连接器 → **stub 的 `sendOutbound` 被调用并收到转换后的出站消息** → 返回 `SENT`
  - `sendToUser` 无绑定 → resolver 返回空 → 返回 `NO_BINDING`（**stub 连接器的 `sendOutbound` 从未被调用**）
  - `dispatchInbound` → 注册的 listener 收到 `InboundChannelMessage`

Exit Criteria:

- [x] `ChannelMessageServiceImpl` 实现存在于 `nop-ai-gateway`，beans.xml 注册
- [x] **接线验证**：`ChannelMessageServiceImpl` bean 在 IoC 容器可解析，`UserChannelResolver` + `ChannelConnectorManager` 被注入（见 Minimum Rules #23）
- [x] **端到端验证**（resolver→manager→connector 链）：测试证明 `sendToUser(有绑定)` 真实路由到 stub 连接器的 `sendOutbound`（调用计数 > 0）且返回 `SENT`；`sendToUser(无绑定)` 返回 `NO_BINDING` 且连接器 `sendOutbound` 从未被调用（见 Minimum Rules #22，用 stub 连接器替代真实信道）
- [x] **无静默跳过**：无绑定返回 `NO_BINDING` 不抛异常也不静默丢弃；目标信道不支持返回 `UNSUPPORTED` 不静默跳过
- [x] `./mvnw test -pl nop-ai-gateway -am` 通过
- [x] `nop-ai-gateway` pom 含 `nop-integration-api` 且无环
- [x] 若改变 live baseline：相关 design/docs-for-ai 已更新；否则写 `No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] `IChannelMessageService`/`UserChannelResolver` 接口 + `ChannelMessage` 模型落 `nop-integration-api`（仅依赖 `nop-api-core`，不泄漏信道协议字段）
- [x] `UserChannelResolver` 实现落 `nop-auth-service`（读 `NopAuthExtLogin`，多绑定"最近活跃"默认），bean 可解析
- [x] `ChannelMessageServiceImpl` 实现落 `nop-ai-gateway`，出站路由（resolver→manager→connector）+ 未绑定 `NO_BINDING` + 入站分发可验证
- [x] 新增 Maven 依赖边无环（`nop-ai-gateway`→`nop-integration-api`、`nop-auth-service`→`nop-integration-api`，后者仅依赖 `nop-api-core`）
- [x] 业务层接口不依赖 AI 模块（`IChannelMessageService`/`ChannelMessage` 仅依赖 `nop-api-core`）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] 受影响 owner docs 已同步到 live baseline，或明确写明 No owner-doc update required
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）`sendToUser` 有绑定时真实调用连接器 `sendOutbound`（不只是返回 SENT 常量），（b）无绑定时连接器 `sendOutbound` 从未被调用（返回 NO_BINDING），（c）`dispatchInbound` 真实通知 listener
- [x] `./mvnw compile -pl nop-integration-api,nop-auth-service,nop-ai-gateway -am`
- [x] `./mvnw test -pl nop-auth-service,nop-ai-gateway -am`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- 跨信道降级编排（飞书失败→短信）—— v1 显式 non-goal，由调用方凭 `SendResult` 自决（设计 §3.2 拒绝方案表已裁定）
- `IMessageService` 骨干接入（多消费者部署）属 W6-4
- 具体信道连接器（`FeishuConnector`）的出站发送实现属 W5

## Closure

Status Note: 业务消息层 `IChannelMessageService` 全部落地——Phase 1 接口/模型（nop-integration-api，仅依赖 nop-api-core）、Phase 2 `UserChannelResolver` 实现（nop-auth-service，读 NopAuthExtLogin，最近活跃默认）、Phase 3 `ChannelMessageServiceImpl` 实现（nop-ai-gateway，resolver→manager→connector 出站路由 + 入站分发）。新增两条 Maven 依赖边无环。所有 Phase Exit Criteria + Closure Gates 经独立子 agent closure-audit 逐条验证 PASS，Anti-Hollow 调用链在测试中以调用计数断言（非类型级）。W2 四项 roadmap 翻转 ✅。
Completed: 2026-08-09

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session `ses_01daeab5effe737oKw0EXqplgf`，read-only，read every live source/test/pom/beans file end-to-end）
- Evidence:
  - Phase 1 Exit Criteria — PASS：8 类型存在于 `nop-integration-api/.../channel/`；`SendResult` 三值语义清晰；import 纯净度 grep `io.nop.(ai|auth).` 返回 0 匹配；pom 仅 `nop-api-core`。`./mvnw compile -pl nop-integration-api -am` SUCCESS。
  - Phase 2 Exit Criteria — PASS：`UserChannelResolverImpl.java:33` 过滤 verified=true+delFlag=0（`:94-100`），loginType→channelType 经 ChannelTypeCodes（`:112`），非信道行显式过滤（`:113-116`），lastLoginTime DESC（`:50-51`）；beans.xml `:43-46` 注册；pom `:49-52` 依赖；`TestUserChannelResolver` 8 tests 全 PASS，覆盖单绑定/多绑定排序/channelType 过滤/无绑定空列表/非信道过滤/example 条件断言。
  - Phase 3 Exit Criteria — PASS：`ChannelMessageServiceImpl.java:41` sendToUser 经 resolver（`:71`）→ 空/null 返回 NO_BINDING（`:67-69,72-75`）→ `manager.lookup`（`:83`，未命中 catch 返回 UNSUPPORTED `:81-90`）→ `toCarrier` 桥接全字段（`:122-140`）→ `connector.sendOutbound`（`:93`）→ SENT（`:94`）；dispatchInbound 扇出（`:108-112`）。beans.xml `:37-47` 注册（ioc:optional resolver）；pom `:43-46` 依赖；`TestChannelMessageService` 7 tests 全 PASS。
  - 端到端验证（Minimum Rules #22）— PASS：`sendToUserBoundRoutesToConnectorAndReturnsSent` 断言 `sendOutboundCount==1` + 桥接载体字段；`sendToUserUnboundReturnsNoBindingAndNeverCallsConnector` 断言 `sendOutboundCount==0`（从未调用）。
  - 接线验证（Minimum Rules #23）— PASS：测试经 setter 注入 resolver+manager，方法真实到达 connector.sendOutbound（计数>0）。
  - 无静默跳过（Minimum Rules #24）— PASS：NO_BINDING/UNSUPPORTED 是显式 SendResult 值非异常非丢弃；无空方法体/continue/吞异常/TODO-as-done。
  - Anti-Hollow Check — PASS：调用链 sendToUser→resolver.resolve→bindings.get(0)→manager.lookup→toCarrier→connector.sendOutbound 每环存在且测试以计数断言；toCarrier 映射 text/markdown/businessRef + 每 Attachment 的 name/mimeType/url/content 全字段（逐字段核对 `ChannelOutboundMessage.java:35-117`）。
  - 无环 — PASS：`nop-integration-api` 仅依赖 `nop-api-core`；新边 nop-auth-service→nop-integration-api、nop-ai-gateway→nop-integration-api 无环。
  - `scan-hollow-implementations.mjs --module nop-ai-gateway/nop-auth-service --severity high` = 0 findings（Critical/High/Medium/Low 全 0）。
  - Deferred 项分类检查：`Deferred But Adjudicated` 为空；Non-Goals 明确划出（具体连接器/扫码绑定/扫码登录/跨信道降级/IMessageService 骨干/Agent 会话响应），无 in-scope live defect 被降级。
  - 已知环境项（非本 plan 引入）：`nop-auth-service`/`nop-sys-dao` broad-reactor DB 测试报 `Table "NOP_AUTH_SITE"/"NOP_SYS_SEQUENCE" not found (this database is empty)`，AutoTest H2 schema-init 既有问题，stash 本 plan 改动后 baseline 同样复现（08-08 日志 line 39/67 已确认）；plan-scoped 单元测试（TestUserChannelResolver 8 + TestChannelMessageService 7）+ Plan 2 测试（13）全 green。
  - `check-plan-checklist.mjs <plan-file> --strict` 退出码 0（见下）。

Follow-up:

- No remaining plan-owned work. W3（扫码绑定 IChannelBindProvider/IChannelBindService）/ W4（扫码登录）/ W5（飞书首信道 FeishuConnector）/ W6（E2E）/ W7（owner-doc 同步）为独立后续 work item，非本 plan scope。
- 设计文档同步（`channel-connector.md` §5 sendOutbound / `nop-ai-channel-integration-design.md` §3.5 模块裁定等）随 W7-1 owner-doc 同步收口。
