# nop-ai 外部信道集成 Roadmap

> Status: active
> 设计：`ai-dev/design/nop-ai-channel-integration-design.md`（业务层 + QR 绑定/登录）+ `ai-dev/design/nop-ai-agent/nop-ai-agent-channel-connector.md`（传输层 `IChannelConnector`）。两篇合起来是"外部信道集成"的完整设计——本 roadmap 彻底落地两者。
> 立场：业务层接口放 `nop-integration-api`（不依赖 AI），实现与传输连接器胶水放 `nop-ai-gateway`（装配点），厂商 SDK 协议放新模块 `nop-integration-feishu`（不依赖 AI），绑定记录复用 `NopAuthExtLogin`。飞书是第一个落地信道，信道抽象保证钉钉/企微/Webhook 零改引擎与登录主流程即可接入。
>
> **模块裁定（roadmap 对设计 §3.5 的细化）**：设计 §3.5 表中 `IChannelConnector` 标注 `nop-ai-agent`，那是 channel-connector 设计文档 §12 所说的"Gateway 层（应用层）"的概念归属。本 roadmap 把这个概念层**解析到具体 Maven 模块 `nop-ai-gateway`**（因 `ChannelConnectorContext` 持 `IAgentEngine`/`IAgentEventPublisher`，这些类型在 `nop-ai-agent`；`nop-ai-gateway` 经核不依赖、也不被 `nop-ai-agent` 依赖，新增 `nop-ai-agent` 依赖无环）。W7-1 含同步更新设计 §3.5 该行。

## 模块归属速查

| 关注点 | 接口 | 实现 |
|---|---|---|
| 业务消息门面 | `IChannelMessageService` + `ChannelMessage` 模型 + `UserChannelResolver` → `nop-integration-api` | `nop-ai-gateway` |
| 传输连接器 | `IChannelConnector` + `ChannelConnectorContext` + `ChannelCapabilities` → `nop-ai-gateway`（依赖 `nop-ai-agent` 的 `IAgentEngine`/`IAgentEventPublisher`） | `nop-ai-gateway` |
| 扫码绑定协议 | `IChannelBindProvider` + `BindTicket` → `nop-integration-api` | 厂商在 `nop-integration-feishu` |
| 绑定记录门面 | `IChannelBindService` → `nop-auth-api` | `nop-auth-service`（写 `NopAuthExtLogin`） |
| 扫码登录 accessCode | 经既有 `IAuthTokenProvider.generateAccessCode(IUserContext, ttl)` 签发 + `ILoginSpi.getLoginResultAsync(AccessCodeRequest)` 兑换 | `nop-ai-gateway` 编排（**登录主流程零改动**） |
| 飞书 SDK 协议 | — | 新模块 `nop-integration-feishu`（`FeishuClient`/`FeishuPbCodec`/`FeishuBindProvider`，不依赖 AI） |
| 二维码渲染 | `IQrcodeService`（已存在） | 复用 `nop-integration-zxing` |

## Work Items

### W0. 基础与 Schema（前置，最高优先）✅

- [x] W0-1 `NopAuthExtLogin` 加 `(loginType, extId)` 唯一索引（设计 §3.4 强制要求；当前 `nop-auth/model/nop-auth.orm.xml` 的 NopAuthExtLogin 实体无 `<unique-keys>`，对照 NopAuthUser 有）。有效绑定条件 `verified=1 AND delFlag=0`，选条件唯一索引或在 `completeBinding` 应用层先查后写 + DB 兜底唯一约束。**ORM 变更纪律**：编辑 `model/*.orm.xml` 源 → `mvn clean install -DskipTests` 触发增量重新生成 → 迁移 DDL；**禁止手编 `_gen/` 与 `_` 前缀生成产物**。Proof：`_gen/` 未被手改 + DDL 迁移可执行。**裁定（已落地）**：采用普通唯一约束（方案 B）+ 应用层兜底；`UK_NOP_AUTH_EXT_LOGIN_TYPE_EXTID` 已写入源 + 三方言生成 DDL + 三方言迁移 `_add_ext_login_unique.sql`。
- [x] W0-2 扩展 `auth/login-type` 字典（当前整数码 `1`=密码/`10`=单点，`stdDataType=int`）：分配 `20`=飞书、`21`=钉钉、`22`=企微、`23`=Webhook（建议码段，避开已有），加中文 label。**先定位权威源**（非 `_dump/` 生成产物；nop 中 dict 权威源通常在 `_vfs/.../dict/` 或 app 模型源），编辑源后重新生成。**已落地**：权威源 `nop-service-framework/nop-biz-auth-core/src/main/resources/_vfs/dict/auth/login-type.dict.yaml` 已含 6 项。
- [x] W0-3 新建模块 `nop-integration/nop-integration-feishu`：`pom.xml`（`<parent>`=nop-integration）**并在 `nop-integration/pom.xml` 的 `<modules>` 增加 `<module>nop-integration-feishu</module>`**（当前 9 模块无 feishu，不加则 `-pl nop-integration -am` 不构建本模块）；依赖 `nop-integration-api`（**不依赖任何 `nop-ai/*` 与 `nop-auth/*`**，保证厂商协议可被非 AI 场景复用）；包结构 `io.nop.integration.feishu.{client,codec,bind}`。Proof：`./mvnw compile -pl nop-integration-feishu -am` 成功 + `grep nop-ai nop-integration-feishu/pom.xml` 为空。**已落地**：BUILD SUCCESS + 依赖纯净（`rg "nop-ai|nop-auth"` 无匹配）+ 0 checkstyle violations。

### W1. 传输层 IChannelConnector（channel-connector 设计落地）✅

> 接口与上下文落 `nop-ai-gateway`（因 `ChannelConnectorContext` 持 `IAgentEngine`/`IAgentEventPublisher`，需给 `nop-ai-gateway` 加 `nop-ai-agent` 依赖；无环）。

- [x] W1-1 `IChannelConnector` 接口（`getChannelType()`/`start(ctx)`/`stop()`/`getCapabilities()`）+ `ChannelConnectorContext`（`IAgentEngine` + `IAgentEventPublisher` + `ChannelConfig`）+ `ChannelCapabilities`（supportsMarkdown/supportsFileUpload/supportsStreaming/supportsGroupChat/maxMessageLength/rateLimitPerMinute 等）+ `ChannelConfig`。落 `nop-ai-gateway`。**已落地**：`nop-ai-gateway` 新增 `io.nop.ai.gateway.channel` 包（`IChannelConnector` 含 `sendOutbound`、`ChannelConnectorContext`、`ChannelCapabilities`、`ChannelConfig`、传输层出站载体 `ChannelOutboundMessage`）；pom 加 `nop-ai-agent` 依赖（无环）。Plan 2 (`2026-08-08-1837-2-nop-channel-transport-layer.md`) Phase 1 完成。
- [x] W1-2 信道会话映射：新增 `ai_channel_session` 表（`channelType`/`channelId`/`sessionId`/`agentName`/`createdAt`/`lastActiveAt`）+ `IChannelSessionStore`。**ORM 变更纪律同 W0-1**：编辑 `nop-ai` 下源 orm.xml → 重新生成 → 迁移 DDL；Proof：`_gen/` 未手改。映射逻辑：收外部消息按 `channelType:channelId` 查 → 命中复用 `sessionId`，未命中建 session 写映射。**已落地**（裁定方案 A）：`nop-ai/model/nop-ai.orm.xml` 新增 `NopAiChannelSession`（`(channelType,channelId)` 唯一键）→ codegen 生成实体 + `_app.orm.xml` + 三方言 `_create_nop-ai.sql`；`IChannelSessionStore` + `ChannelSessionStoreImpl`（sessionId 来自引擎 ack，store 不生成）落 `nop-ai-gateway`（新增 `nop-ai-dao` 依赖，无环）。Phase 2 完成。
- [x] W1-3 `ChannelConnectorManager`（注册/按 channelType 查找/统一 start/stop 生命周期）+ Nop IoC `beans.xml` 装配。连接器自洽管理凭证、重连、速率限制。**已落地**：`ChannelConnectorManager`（`<ioc:collect-beans by-type>` 自动收集 + 程序化注册；lookup 未命中抛异常不静默）注册于 `ai-gateway-defaults.beans.xml`，并新增 `_vfs/nop/ai/gateway/_module` 标记使 beans 可发现。Phase 3 完成。
- [x] W1-4 beans.xml 装配验证：`ChannelConnectorManager` 在运行时实际注入到消费方（Anti-Hollow exit criterion：容器启动 + `getBean` 解析成功）。**已落地**：`TestChannelConnectorManager` stub 连接器证明 `startAll`/`stopAll` 真实调用连接器（计数 > 0）+ 经 context 持有 `IAgentEngine`/`IAgentEventPublisher`；`TestChannelSessionStore` H2 持久化证明 miss→null / saveMapping→hit / updateLastActive→刷新。Gateway 21 tests green + 全 `nop-ai` reactor install 成功。

### W2. 业务消息层 IChannelMessageService（业务设计 §3.2 落地）✅

- [x] W2-1 `ChannelMessage` 模型（`OutboundChannelMessage`: text/markdown/attachments/businessRef；`InboundChannelMessage`: userId/channelType/channelAddress/text/rawAttachments/receivedAt）+ `SendResult` 枚举（`SENT`/`NO_BINDING`/`UNSUPPORTED`）。落 `nop-integration-api`（依赖 `nop-api-core`，无 AI）。
- [x] W2-2 `IChannelMessageService` 接口（`sendToUser(userId, OutboundChannelMessage)→SendResult`、`subscribeInbound(listener)`）+ `UserChannelResolver` 接口（`resolve(userId)→List<ChannelBinding>`、`resolve(userId, channelType)→ChannelBinding`）。落 `nop-integration-api`。**只以 userId 为锚，禁止暴露信道协议字段**（设计拒绝 sendToChannel）。
- [x] W2-3 `UserChannelResolver` 实现：读 `NopAuthExtLogin`（`loginType`↔channelType，`extId`↔信道地址），多绑定默认"最近活跃"（复用既有 `LAST_LOGIN_TIME` 列 `lastLoginTime` 作为活跃度判据），调用方可指定 channelType 覆盖。落 `nop-auth-service`（**新增依赖 `nop-integration-api`，无环**——后者只依赖 `nop-api-core`）。含 beans.xml 注册 + 运行时注入验证。
- [x] W2-4 `IChannelMessageService` 实现 `ChannelMessageServiceImpl`：出站经 `UserChannelResolver` 解析 → 选连接器 → 调原生发送；入站由连接器回调 `dispatchInbound(InboundChannelMessage)` → 通知监听器。落 `nop-ai-gateway`。**未绑定返回 `NO_BINDING` 不抛异常；跨信道降级（飞书失败→短信）v1 non-goal，由调用方凭 SendResult 自决**。含 beans.xml 注册 + 运行时注入验证（Anti-Hollow：容器解析 `ChannelMessageServiceImpl` bean 成功）。

### W3. 扫码绑定（业务设计 §3.4 ② 落地）✅

- [x] W3-1 `IChannelBindProvider` 接口（`createBindTicket(channelType, platformUserId)→BindTicket`、`onChannelScanCallback(ChannelScanCallback)→ChannelBindResult`、返回信道侧用户标识）+ `BindTicket` 模型（ticketId/qrPayload/expiresAt/status，**券状态由 provider 自持，无需独立 store**）+ `ChannelScanCallback`/`ChannelBindResult` 载体。落 `nop-integration-api`。命名遵循 `…Provider`（多步有状态协议，区别于 `ISmsSender`/`IEmailSender` 的单向推送）。**Plan**：`2026-08-08-1837-4-nop-channel-scan-binding.md`（active）Phase 1。**已落地**：`io.nop.integration.api.bind` 包新增 6 类型（接口 + 4 DataBean + 2 枚举）；依赖纯净（仅 `nop-api-core`）；`./mvnw compile -pl nop-integration-api -am` BUILD SUCCESS。
- [x] W3-2 `IChannelBindService` 业务门面（`startBinding`/`completeBinding`/`findBinding(channelType,extId)`/`listBindings(userId)`/`unbind(bindingId)`）。接口落 `nop-auth-api`，实现落 `nop-auth-service`：`completeBinding` 写 `NopAuthExtLogin`（`loginType`=信道整数码、`extId`=channelUserId、`userId`=platformUserId、`verified=true`），依赖 W0-1 唯一索引保证 extId→userId 唯一。实现依赖 `nop-integration-api`（调 `IChannelBindProvider`，无环）。含 beans.xml 注册 + 注入验证。**Plan**：`2026-08-08-1837-4-nop-channel-scan-binding.md`（active）Phase 2-3。**已落地**：`IChannelBindService` 接口（含 `findBinding` 反查 + 自有 DataBean 类型 `BindStartResult`/`ChannelBindingInfo`）落 `nop-auth-api`（接口签名不引用 `nop-integration-api` 类型，类型翻译由 impl 负责）；`ChannelBindServiceImpl` 落 `nop-auth-service`（`completeBinding` 显式裁定三种重复绑定情况：同用户幂等 / 同用户 unbind 后重绑物理删除+新建 / 跨用户重绑物理删除+新建，软删行查询用 `orm_disableLogicalDelete(true)`，物理删除用 `deleteEntityDirectly`）；`auth-service.beans.xml` 注册 bean + `<ioc:collect-beans by-type>` 收集 provider；14 plan-scoped tests green（`TestChannelBindServiceImpl` 12 + `TestChannelBindServiceIoC` 2）+ 既有 `TestUserChannelResolver` 8 无回归。`nop-auth-service` pom 新增 `nop-auth-api` 依赖；`nop-bom` 新增 `nop-auth-api` 到 dependencyManagement。

### W4. 扫码登录（业务设计 §3.4 ③ 落地，零改登录主流程）

> **关键裁定（accessCode 机制）**：登录用 accessCode 是**提供方签名令牌**，由既有 `IAuthTokenProvider.generateAccessCode(IUserContext, ttl)` 签发、`parseAccessCode` 解码（`JwtAuthTokenProvider` 实现）。**不是**自建的不透明 store key——自建 key 无法被既有 `parseAccessCode` 解码，会破坏"登录主流程零改动"。扫码回调解析出 platformUserId 后构造 `IUserContext` → 调 `generateAccessCode` 签发 → 前端换取 `LoginResult`。

- [x] W4-1 扫码回调端点 + accessCode 编排落 `nop-ai-gateway`：暴露为 `@BizModel("ChannelLoginApi")` 的 `@BizMutation("loginByScan")`（`@Auth(publicAccess=true)`，经 GraphQL `ChannelLoginApi__loginByScan` 或 REST `/r/ChannelLoginApi__loginByScan` 访问）；收到信道扫码回调 → 经 `IChannelBindProvider` + `IChannelBindService.findBinding` 查绑定得 platformUserId → **创建 session**（Phase 0 裁定路径 B2：新增 `ISessionBootstrap.createSessionForUserAsync(userId)` 接口落 `nop-biz-auth-core`，impl 落 `LoginServiceImpl` 复用 `buildUserContext`+`saveSession`+`saveUserContextAsync`，session 创建与消费同进程同缓存）→ `IAuthTokenProvider.generateAccessCode(ctx, ttl)` 签发一次性 code → 返回前端。**`nop-ai-gateway` 新增依赖 `nop-biz-auth-core`（取 `IAuthTokenProvider`/`IUserContextCache`/`ISessionBootstrap`，无环）+ `nop-auth-api`（取 `IChannelBindService`，无环）**。Exit criterion：端点真实可达（W6-2 端到端命中）。**Plan**：`2026-08-08-1837-5-nop-channel-scan-login.md`（completed）。**已落地**：`ChannelLoginApiBizModel` + `ScanLoginResult` 落 `nop-ai-gateway`；`ISessionBootstrap` 接口落 `nop-biz-auth-core`；`LoginServiceImpl.createSessionForUserAsync` impl 复用既有 session 创建逻辑；`ai-gateway-defaults.beans.xml` 注册 BizModel（FQCN id + collect-beans）；`TestChannelLoginApi` 4 tests（happy path bootstrap callCount==1+code 非占位 / 未绑定显式失败 / 无 provider 显式失败 / null sessionBootstrap 显式失败）。
- [x] W4-2 验证既有 `ILoginSpi.getLoginResultAsync(AccessCodeRequest)` + `ILoginService.parseAccessCode`（→ `IAuthTokenProvider.parseAccessCode`）路径**无需改动**即可消费 W4-1 用 `generateAccessCode` 签发的 code；前端轮询/换取 `LoginResult`。新增信道扫码登录只实现 `IChannelBindProvider`，登录主流程既有契约不被破坏（`TestChannelLoginAccessCode` 4 tests 证明 accessCode 往返命中缓存 + 用途隔离 + 伪造拒绝；`git hash-object` 对比证明 `ILoginService`/`ILoginSpi`/`IAuthTokenProvider`/`LoginApiBizModel` 四文件零修改；roadmap"零改动"完成定义经 Phase 0 诚实修正——session 创建路径 B2 用新接口 `ISessionBootstrap` 而非改 `ILoginService`，三既有接口字面零改）。**完整 `getLoginResultAsync`→`LoginResult` 全链验证按 Phase 2 裁定 A 留待 W6-2 E2E**（归入 plan 的 Deferred But Adjudicated）。**Plan**：`2026-08-08-1837-5-nop-channel-scan-login.md`（completed）Phase 0-2。

### W5. 飞书首信道落地

- [x] W5-0 **Decision：飞书 Stream SDK 选型**（channel-connector 设计 §14 Open Question）：官方 `oapi-sdk-java` vs 独立实现。声明外部 Maven 依赖后果（与本项目"按需引入"依赖立场的关系）；选定后写决策记录到设计文档 Open Questions 收口。**已落地（裁定 B 独立实现）**：修正 baseline 误判（Java 11+ `java.net.http.WebSocket`/`HttpClient` 系 JDK stdlib 非外部库）；`oapi-sdk-java` 为全平台 SDK + shaded protobuf（体积失称，与 `sms-tencent→qcloudsms` 聚焦小库先例不类比）；FeishuPbCodec 手写 protobuf wire format（无 `protobuf-java`，`protobuf` 未被 nop-bom 管理）；pom 维持单一依赖 `nop-integration-api`（零外部依赖）。设计 §14 已收口、§10 凭证加密术语修正（`nop-config-encrypt` → `DefaultConfigValueEnhancer` + `@sec:` + `AESTextCipher`）。Plan 6 Phase 0 完成。
- [x] W5-1a 飞书连接/重连生命周期（`FeishuClient`：Stream SDK 长连接建立、断线重连、心跳），`nop-integration-feishu`。**已落地**：`FeishuClient`（start/stop/isConnected/sendMessage + 指数退避重连 + CONTROL 心跳调度）经 JDK WebSocket（`JdkStreamTransport`）+ JDK HttpClient（`JdkFeishuHttpApi`）；`tenant_access_token` 缓存刷新；测试经 `IStreamTransport`/`IFeishuHttpApi` seam 注入 fake（10 tests：生命周期状态转换 + DATA 帧投递 handler + token 缓存复用 + null/double-start 显式行为）。Plan 6 Phase 2 完成。
- [x] W5-1b `FeishuPbCodec`：Pbbp2 Protobuf 二进制协议（method=0 CONTROL / method=1 DATA / method=2 ACK 解编码），`nop-integration-feishu`。独立可测。**已落地**：`FeishuPbCodec`（encode/decode 手写 protobuf wire format，proto3 default 语义）+ `FeishuStreamFrame`/`FeishuFrameType`；8 tests（CONTROL/DATA/ACK round-trip + 二进制 payload 逐字节 + 畸形输入显式抛 `NopFeishuException` + null 显式失败）。Plan 6 Phase 1 完成。
- [x] W5-1c 飞书凭证管理：appId/appSecret/verificationToken/encryptKey，经 `nop-config-encrypt` 加密保护，`nop-integration-feishu`。**已落地**：`FeishuCredentials`（DataBean，4 字段经 `@InjectValue("@cfg:nop.integration.feishu.*|")` 注入，`@sec:` 前缀配置值经 `DefaultConfigValueEnhancer`+`AESTextCipher` 解密）。Plan 6 Phase 2 完成。
- [ ] W5-2 `FeishuBindProvider`（`IChannelBindProvider` 实现，`nop-integration-feishu`）：生成扫码 QR payload（Open Question：飞书扫码登录二维码 vs 自建券 + 机器人推送，W5-2 选型）、处理飞书扫码回调、返回 open_id 作为 extId。
- [ ] W5-3 `FeishuConnector`（`IChannelConnector` 实现，落 `nop-ai-gateway`，依赖 W5-1）：Stream 收 `im.message.receive_v1` → 解密验签 → 提取文本/chat_id/sender → 查建 `ChannelSession` → `IAgentEngine.sendMessage(AgentMessageRequest)`（metadata 带 channelType/channelId/senderId/channelKind=dm|group）；订阅 `IAgentEventPublisher` 的 `AgentResult`/`AgentError` → 飞书消息 API 回复；TextChunk 按 `ChannelCapabilities` 累积合并（delta 合并，防速率限制 50msg/s）。附件/多媒体与能力协商降级（转链接/拒绝）在此收口。
- [ ] W5-4 IoC 装配：`feishu-connector.beans.xml`（凭证 `@InjectValue` 占位 + 加密）、`ChannelConnectorManager` 注册 `FeishuConnector`、`IChannelBindProvider` SPI 注册 `FeishuBindProvider`。

### W6. 端到端集成与验证

- [ ] W6-1 E2E 飞书会话：用户消息 → FeishuConnector → IAgentEngine → AgentEvent → 飞书回复（含群聊 @机器人过滤、长文本分段、速率限制）。
- [ ] W6-2 E2E 扫码绑定 + 扫码登录：createBindTicket → 扫码 → completeBinding 写 NopAuthExtLogin → `generateAccessCode` → `getLoginResultAsync` 换 LoginResult；二次扫码直接登录；端点 W4-1 真实命中。
- [ ] W6-3 E2E 主动通知：业务调用 `IChannelMessageService.sendToUser(userId,...)` → UserChannelResolver → FeishuConnector → 飞书私信；未绑定返回 `NO_BINDING`。
- [ ] W6-4 可选骨干验证：入站经 `IMessageService` topic `channel.inbound.feishu` 多消费者分发（业务监听器 + 审计），证明单体直连与多消费者两种部署接口不变（设计 §3.3 问题 B）。

### W7. Owner-doc 同步

- [ ] W7-1 设计 §3.5 表 `IChannelConnector` 行由 `nop-ai-agent` 改为 `nop-ai-gateway`（与本 roadmap 模块裁定一致）；设计/roadmap 中 `AgentEventPublisher` 统一为 `IAgentEventPublisher`（实际类型名，含传输设计文档 `ai-dev/design/nop-ai-agent/nop-ai-agent-channel-connector.md` 全部出现处）。实现完成后关键结论同步到 `docs-for-ai/`（若有对外使用契约）。

## 完成定义

- W0–W7 全部 todo → planned（有 execution plan）→ done（每项经独立 closure audit 通过）
- **依赖图证明（新增边全部无环）**：
  - `nop-ai-gateway` → 新增 `nop-ai-agent` + `nop-auth-api` + `nop-integration-api` + `nop-integration-feishu` + `nop-biz-auth-core`，无环
  - `nop-auth-service` → 新增 `nop-integration-api`（W2-3/W3-2 实现），无环（`nop-integration-api` 只依赖 `nop-api-core`）
  - `nop-integration-api`/`nop-auth-api` 只向下依赖 `nop-api-core`，不反向依赖任何 `nop-ai-*`/`nop-service-framework`
- 业务层接口（`IChannelMessageService`/`IChannelBindProvider`/`UserChannelResolver`/`ChannelMessage`）**仅依赖 `nop-api-core`**
- 飞书 SDK 协议层（`nop-integration-feishu`）**不依赖任何 `nop-ai-*`**（`grep nop-ai nop-integration-feishu/pom.xml` 为空）
- 扫码登录复用既有 `IAuthTokenProvider.generateAccessCode`/`ILoginSpi.getLoginResultAsync(AccessCodeRequest)`，**登录主流程零改动**（用集成测试证明未改 `ILoginService`/`ILoginSpi`/`IAuthTokenProvider` 契约）
- `NopAuthExtLogin` 的 `(loginType, extId)` 唯一索引落地，扫码登录 extId→userId 唯一性有 DB 级保证
- 所有 ORM 变更（W0-1/W1-2）遵守纪律：编辑 `model/*.orm.xml` 源 → 重新生成 → 迁移；`_gen/` 未手编
- `./mvnw test -pl nop-integration,nop-auth,nop-ai -am -T 1C` 全绿；新增信道（钉钉/企微）只需实现 `IChannelConnector` + `IChannelBindProvider`，不改引擎与登录主流程

## 设计 Open Questions（实现期收口）

- 出站多绑定信道选择默认值（最近活跃 vs 优先级表）→ W2-3 收口（已倾向最近活跃=lastLoginTime）
- 入站骨干 topic 命名约定 → W6-4 收口
- 飞书扫码 qrPayload 选型（飞书扫码登录二维码 vs 自建券 + 机器人推送）→ W5-2 收口
- 附件/多媒体与 `ChannelCapabilities` 协商降级（转链接/拒绝）→ W5-3 收口
- 飞书 Stream SDK 选型（官方 vs 独立实现，外部依赖后果）→ W5-0 收口
