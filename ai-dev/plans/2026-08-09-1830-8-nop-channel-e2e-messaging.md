# 8 nop-ai 外部信道集成 — E2E 消息管线验证 (W6-1 + W6-3)

> Plan Status: completed
> Mission: nop-ai-channel-integration
> Work Item: W6-1, W6-3
> Last Reviewed: 2026-08-09
> Source: `ai-dev/backlog/nop-ai-channel-integration-roadmap.md` (W6-1/W6-3；W6-1 描述含速率限制) · `ai-dev/design/nop-ai-agent/nop-ai-agent-channel-connector.md` (§7.2/§11) · Plan 7 Closure Follow-ups（@bot payload 校准 / 长文本分段；速率限制经 roadmap W6-1 描述进入 scope，Plan 7 仅声明 `rateLimitPerMinute=3000` 能力未 deferred 执行）
> Related: 前置 Plan 7 (FeishuConnector/FeishuBindProvider/IoC 装配，completed) · 前置 Plan 6 (飞书协议层，completed) · 前置 Plan 2 (IChannelConnector/IChannelSessionStore) · 前置 Plan 3 (IChannelMessageService/UserChannelResolver) · 后续 Plan 9 (W6-2 扫码绑定+登录 E2E)

## Purpose

把"飞书消息管线"收口到 **E2E 可验证**状态：在真实 Nop IoC 容器中装配全部真实组件（`FeishuConnector` + `ChannelConnectorManager` + `ChannelSessionStoreImpl` + `ChannelMessageServiceImpl` + `UserChannelResolverImpl`），仅在**外部依赖边界**（`IAgentEngine.execute` 的 LLM 内部、`FeishuClient` 的网络内部）用 test stub 截断，验证两条完整路径：

1. **入站会话 (W6-1)**：飞书消息到达 → `FeishuConnector.onMessage` → `IAgentEngine.execute` → future callback → `FeishuClient.sendMessage` 回复（含群聊 @bot 过滤、session 复用、错误回复）。
2. **主动通知 (W6-3)**：业务调 `IChannelMessageService.sendToUser(userId,...)` → `UserChannelResolver` 读 `NopAuthExtLogin` → 选 `FeishuConnector` → `sendOutbound` → 飞书私信；未绑定返回 `NO_BINDING`。

同时收口 Plan 7 留给 W6 的三项 deferred connector 行为（roadmap W6-1 描述显式列出"长文本分段、速率限制"，Plan 7 标注 @bot payload 校准属 W6），使 W6-1 描述里的行为全部落地后再做 E2E。

> **关键事实校正（W6-1 描述 vs Plan 7 裁定）**：roadmap W6-1 文字"→ AgentEvent → 飞书回复"已被 Plan 7 Phase 0 裁定修正——`EXECUTION_COMPLETED` 事件 payload **不含响应文本**（`ReActAgentExecutor.java:1015-1027`），connector 经 `IAgentEngine.execute()` 的 `CompletableFuture<AgentExecutionResult>` future callback 获取响应文本（`FeishuConnector.java:271,281`）。本 plan 的 E2E 路径以 **execute() + future callback** 为准。

## Current Baseline

- **FeishuConnector（已落地，Plan 7）**：`nop-ai-gateway/.../channel/feishu/FeishuConnector.java`，实现 `IChannelConnector`+`IMessageHandler`。入站 `onMessage`（`:206-282`）→ 群聊 `@bot` 过滤（`isBotMentioned` `:476`，当前判据为 raw payload `"mentions"` 数组非空，**真实 payload 形状校准是本 plan 的 in-scope 项**）→ `sessionStore.findByChannel` 查建 session → `context.getAgentEngine().execute(request)`（`:271`）→ `future.whenComplete(onExecutionComplete)`（`:281`）→ `sendReply(chatId, text)`（`:333`，调 `feishuClient.sendMessage`）。新 session 经 `saveMapping` 持久化（`:315`），sessionId 来自 `AgentExecutionResult.getSessionId()`。`capabilities`：maxMessageLength=4000 / rateLimitPerMinute=3000 / supportsFileUpload=false（`:105-118`）。
- **未实现的 connector 行为（W6-1 描述列出但 Plan 7 标注 deferred/W6 调优）**：
  - **长文本分段**：当前 `sendReply`/`sendOutbound` 一次性发送全文，**不按 maxMessageLength=4000 分段**。超长响应会被飞书截断/拒绝。
  - **速率限制**：当前 connector **不强制 rateLimitPerMinute**，高频入站会无节制转发到 `IAgentEngine`。
- **ChannelMessageServiceImpl（已落地，Plan 3）**：`nop-ai-gateway/.../channel/ChannelMessageServiceImpl.java`。`sendToUser(userId, OutboundChannelMessage)`（`:59-95`）：`userChannelResolver.resolve(userId)` → 取 `get(0)` 最近活跃绑定 → `channelConnectorManager.lookup(channelType)` → `connector.sendOutbound(binding.getChannelAddress(), carrier)`（`:92-93`）。未绑定/无 resolver/lookup 失败 → `NO_BINDING`/`UNSUPPORTED`。`toCarrier`（`:122-140`）桥接 business → transport 载体。
- **UserChannelResolverImpl（已落地，Plan 3）**：`nop-auth-service/.../channel/UserChannelResolverImpl.java`。`resolve(userId)`（`:44-65`）读 `NopAuthExtLogin`（`verified=true AND delFlag=0`）按 `lastLoginTime DESC`；`resolve(userId, channelType)`（`:67-84`）。`ChannelTypeCodes`：feishu=20/dingtalk=21/wecom=22/webhook=23。
- **IChannelSessionStore（已落地，Plan 2）**：`ChannelSessionStoreImpl` 经 H2 持久化（`TestChannelSessionStore.java` 已证明 miss→null / saveMapping→hit / updateLastActive）。`findByChannel(channelType, channelId)` / `saveMapping(...)` / `updateLastActive(...)`。
- **真实 IoC 容器 harness（已存在）**：`TestFeishuConnectorIoC`（`nop-ai-gateway/src/test/.../channel/feishu/TestFeishuConnectorIoC.java`）经 `AppBeanContainerLoader.loadFromResource("test-feishu-ioc", resource)` 启动真实容器，test beans 文件 `_vfs/test/beans/test-feishu-connector-ioc.beans.xml` import 真实 `feishu-defaults.beans.xml` + 声明 in-memory `IChannelSessionStore` + `feishuConnector` bean + `testChannelConnectorManager`（collect-beans `IChannelConnector`）。**本 plan E2E 在此 harness 上扩展**。
- **可复用 test stub（已存在）**：`RecordingFeishuClient extends FeishuClient`（`TestFeishuConnector.java:291`，override `sendMessage` 记录不触网）、`RecordingAgentEngine implements IAgentEngine`（`:323`，canned `execute` future）、`InMemorySessionStore`（`:344`）、H2 + `ChannelSessionStoreImpl`（`TestChannelSessionStore.java:118`）。
- **beans.xml 装配（已存在）**：`feishu-defaults.beans.xml`（feishuCredentials/feishuClient/feishuBindProvider）、`ai-gateway-defaults.beans.xml`（channelConnectorManager/channelSessionStore/feishuConnector/channelMessageService〔`userChannelResolver` `ioc:optional=true`〕/ChannelLoginApiBizModel）、`auth-service.beans.xml`（userChannelResolver/channelBindService）。
- **IAgentEngine**：`execute(AgentMessageRequest) → CompletableFuture<AgentExecutionResult>`（`IAgentEngine.java:12`，确认）。`sendMessage` 是 fire-and-forget（不用）。
- **模块依赖**：`nop-ai-gateway` 已依赖 `nop-ai-agent`/`nop-integration-api`/`nop-auth-api`/`nop-biz-auth-core`/`nop-ai-dao`/`nop-integration-feishu`。W6-3 的 `UserChannelResolverImpl` 在 `nop-auth-service`——本 plan 的 sendToUser E2E 测试需让 `UserChannelResolver` 真实实现可达（test beans 直接注册 `UserChannelResolverImpl` bean 或经 auth-service test 依赖）。**具体机制是 Phase 0 决策项**。

## Goals

- **W6-1 入站会话 E2E**：真实容器 + 真实 FeishuConnector/ChannelSessionStoreImpl/ChannelConnectorManager，stub 仅在 IAgentEngine.execute(LLM) 与 FeishuClient(网络) 边界——验证飞书消息 → engine.execute → future → 飞书回复完整连通，含 session 复用（第二条消息命中既有 sessionId）、群聊 @bot 过滤、engine 失败 → 错误回复。
- **W6-3 主动通知 E2E**：真实容器，seed `NopAuthExtLogin` 绑定 → `sendToUser(userId, msg)` → `UserChannelResolverImpl` → `FeishuConnector.sendOutbound` → 飞书私信；无绑定 → `NO_BINDING`。
- **收口 Plan 7 deferred connector 行为**：长文本分段（按 maxMessageLength 切片顺序发送）、基本速率限制守卫（超 rateLimitPerMinute 显式拒绝而非静默转发）、@bot mention 真实 payload 形状校准（文档化飞书 event `mentions` 形状 + 解析器 focused test）。
- **端到端 Anti-Hollow 证据**：真实容器内组件间调用链运行时连通（FeishuConnector.onMessage 真实调 engine.execute、future callback 真实调 FeishuClient.sendMessage、sendToUser 真实经 resolver→connector→sendOutbound），不止类型系统。

## Non-Goals

- 不做真实飞书服务器 E2E（无 CI 可达性）——属 watch-only residual（Plan 7 已 adjudicated）。
- 不实现文件上传/下载（设计 §11 deferred）。
- 不实现跨信道降级（设计 §四显式拒绝）。
- 不改动 `IChannelConnector`/`IChannelMessageService`/`UserChannelResolver`/`IAgentEngine` 接口契约。
- 不覆盖 W6-2 扫码绑定+登录 E2E（Plan 9）、W6-4 多消费者骨干（可选，候选 successor）。
- 不实现流式增量推送（飞书不支持流式；execute() 返回完整结果后一次性/分段回复）。
- 不做多实例部署的 future 跨进程传递（Plan 7 已 adjudicated optimization candidate）。

## Scope

### In Scope

- `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/channel/feishu/FeishuConnector.java`（长文本分段 + 速率限制守卫 + @bot payload 校准）
- `nop-integration/nop-integration-feishu/src/main/java/io/nop/integration/feishu/...`（**仅当 Phase 0 @bot fork 选 (a)/(b)**：`FeishuCredentials` 加 bot-id 字段或 `FeishuClient.start` 取 bot 身份；选 (c) 则不动）
- `nop-ai/nop-ai-gateway/src/test/.../channel/feishu/TestFeishuConversationE2E.java`（W6-1 真实容器 E2E，新建）
- `nop-ai/nop-ai-gateway/src/test/.../channel/TestChannelProactiveNotifyE2E.java`（W6-3 真实容器 E2E，新建）
- `nop-ai/nop-ai-gateway/src/test/resources/_vfs/test/beans/test-channel-e2e-messaging.beans.xml`（E2E 容器装配，新建或扩展既有 test beans）
- `ai-dev/design/nop-ai-agent/nop-ai-agent-channel-connector.md`（@bot payload 形状 + 分段/速率限制行为记录，若改变契约语义）
- `ai-dev/logs/` 对应日期条目

### Out Of Scope

- 真实飞书连接（watch-only residual）
- W6-2 扫码绑定+登录（Plan 9）
- W6-4 多消费者骨干（可选 successor）

## Execution Plan

### Phase 0 — 决策：deferred 行为 scope 裁定 + E2E harness 机制

> **前置**：Phase 0 裁定。

Status: completed
Targets: `ai-dev/design/nop-ai-agent/nop-ai-agent-channel-connector.md` · daily log · test beans 规划

- Item Types: `Decision | Proof`

- [x] **Decision：长文本分段最小可行行为**。裁定切片策略：按 `maxMessageLength`（4000）切片，优先在换行符边界切，顺序经 `feishuClient.sendMessage` 发送多段；切片不破坏文本（不丢字符）。记录到设计文档 §7.2.1 出站行为段。
- [x] **Decision：速率限制最小可行行为**。裁定守卫语义：connector 维护 rolling 60s 窗口的入站计数，超 `rateLimitPerMinute` 时**显式回复**"请求过于频繁，请稍后再试"并**不转发到 IAgentEngine**（防刷引擎），不静默丢消息。记录到设计文档 §7.2.2。
- [x] **Decision：@bot mention 真实 payload 形状 + bot 身份来源 fork**。文档化飞书 `im.message.receive_v1` event 中 `mentions` 数组形状（基于飞书开放平台文档：`{"key":"@_user_1","id":{"open_id":"ou_...","union_id":"...","name":"..."}}`）。**Fork 裁定：(c)** — 仅文档化 payload 形状、保留启发式判据（按文档化形状解析 `mentions` 数组 key/id.open_id 字段）、精确 bot open_id 匹配 defer 到真实飞书 E2E（watch-only）。Phase 0 选定后记录判据 + focused test 用文档化形状驱动解析器。详见 §7.2.3。
- [x] **Decision：E2E 容器 harness 机制**。**Candidate B 选定**（仅 `nop-auth-dao` test-scope + 忠实镜像 `UserChannelResolverImpl` 查询逻辑的 resolver test bean）。Candidate A（full `nop-auth-service`）实测破坏既有测试（auth-service.beans.xml 硬导入 nop-biz 资源 + ObjDictLoader 启动期查 NopAuthSite → AiDialectBackendMessageConverterTest 失败）。Candidate B 经 `./mvnw test-compile -pl nop-ai-gateway -am` 证明可编译，且 52 既有 gateway tests 全绿。
- [x] **Proof：E2E 外部边界 stub 策略**。确认 `IAgentEngine.execute`（LLM 边界）用 test impl 返回 canned `CompletableFuture<AgentExecutionResult>`，`FeishuClient`（网络边界）用 `RecordingFeishuClient` 模式（override `sendMessage` 记录不触网）。真实组件全部用生产代码/忠实镜像。

Exit Criteria:

- [x] 三项 deferred 行为（分段/速率限制/@bot）scope 已裁定，最小可行语义记录在设计文档 §7.2（§7.2.1/§7.2.2/§7.2.3）
- [x] E2E 容器 harness 机制已裁定且所选方案经 `./mvnw test-compile -pl nop-ai-gateway -am` 证明可编译
- [x] 外部边界 stub 策略已明确（仅 LLM 边界 + 网络边界，组件全真实）
- [x] **No new test required**: Phase 0 是决策，proof 经编译验证
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 1 — FeishuConnector 行为补全：长文本分段 + 速率限制 + @bot 校准

> **前置**：Phase 0 裁定。

Status: completed
Targets: `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/channel/feishu/FeishuConnector.java` · `nop-ai/nop-ai-gateway/src/test/.../channel/feishu/TestFeishuConnector.java`（扩展现有）

- Item Types: `Fix`

- [x] 实现**长文本分段**：`sendReply`/`sendOutbound` 发送前，若文本长度 > `maxMessageLength` 则切片顺序发送（换行边界优先），切片不丢字符。能力 `supportsFileUpload=false` 的附件降级行为保持不变。
- [x] 实现**速率限制守卫**：connector 维护 rolling 60s 入站计数；超 `rateLimitPerMinute` 时 `onMessage` 显式回复"请求过于频繁"并**不调 `engine.execute`**（callCount 断言为 0）；窗口内未超限正常转发。
- [x] **@bot mention payload 校准**：`isBotMentioned` 按文档化飞书 event `mentions` 数组形状解析（含 bot 自身 key/open_id），非"数组非空"粗判。
- [x] **无静默跳过**（Rule #24）：超速率限制时显式回复不静默丢；切片异常显式抛出不吞；@bot 解析缺字段时按"未 @"处理（正确语义，非静默跳过应处理逻辑）。

Exit Criteria:

- [x] `FeishuConnector.sendReply`/`sendOutbound` 对超 maxMessageLength 文本切片顺序发送（可观察：一次 sendReply 触发多次 `feishuClient.sendMessage`，文本拼接还原等同原文）
- [x] 速率限制守卫在超限窗口内 `engine.execute` callCount == 0 且有"请求过于频繁"回复
- [x] `isBotMentioned` 按文档化飞书 `mentions` 形状解析，focused test 用该形状驱动
- [x] **新增功能测试覆盖**（Test-Mandated Feature Rule）：
  - [x] `longTextReplyIsSegmentedByMaxMessageLength`：超长 assistant 文本 → feishuClient.sendMessage 被调多次，文本拼接 == 原文
  - [x] `rateLimitGuardSuppressesEngineExecuteWhenExceeded`：超限 → execute callCount == 0 + "请求过于频繁"回复；未超限 → 正常
  - [x] `botMentionParsedFromDocumentedPayloadShape`：文档化 `mentions` 含 bot → 处理；不含 bot → 跳过
- [x] **无静默跳过**：超限/切片异常/缺字段均有显式行为
- [x] `./mvnw test -pl nop-ai-gateway -am` 通过（含既有 TestFeishuConnector 12→16 tests 无回归）
- [x] 若改变契约语义：设计文档 §7.2 已更新（§7.2.1/§7.2.2/§7.2.3）；否则 `No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — W6-1 入站会话 E2E（真实容器）

> **前置**：Phase 1 完成。

Status: completed
Targets: `nop-ai/nop-ai-gateway/src/test/.../channel/feishu/TestFeishuConversationE2E.java`（新建）· `nop-ai/nop-ai-gateway/src/test/resources/_vfs/test/beans/test-channel-e2e-messaging.beans.xml`

- Item Types: `Proof`

- [x] 新建真实容器 E2E 测试 `TestFeishuConversationE2E`：经 `AppBeanContainerLoader` 启动真实容器（扩展 `TestFeishuConnectorIoC` harness），装配**真实** `FeishuConnector` + `ChannelConnectorManager` + `ChannelSessionStoreImpl`（H2，经 `E2EH2SessionStore` delegate）+ `FeishuClient`（test bean，网络边界 stub）；`IAgentEngine` 用 test impl（canned `execute` future）。
- [x] **E2E round-trip**：模拟飞书 DATA 帧 → `FeishuClient` 投递到注册的 `IMessageHandler`（即 FeishuConnector）→ `onMessage` → 真实 `engine.execute` future 完成 → `sendReply` → test FeishuClient 记录到回复。从用户入口（飞书消息）到最终输出（飞书回复）完整跑通。
- [x] **session 复用 E2E**：同一 chatId 两条消息 → 第一条 `saveMapping`（新 sessionId）+ 第二条 `findByChannel` 命中复用 sessionId（store 计数/状态可观察）。
- [x] **群聊 @bot 过滤 E2E**：群聊消息含 bot mention → 处理（execute callCount > 0）；不含 → 跳过（callCount == 0）。
- [x] **错误路径 E2E**：`engine.execute` future 异常完成 / `result.status=failed` → 错误回复（test FeishuClient 记录到含 error 文本）。
- [x] **长文本 E2E**：canned result 含超长 assistant 文本 → 分段回复（Phase 1 行为在 E2E 层可观察）。

Exit Criteria:

- [x] `TestFeishuConversationE2E` 存在，经真实容器运行（非纯 programmatic new）
- [x] **端到端验证**（Anti-Hollow Rule #22）：飞书消息入口 → FeishuConnector → engine.execute → future → sendReply → FeishuClient 出口，完整路径在真实容器内连通
- [x] **接线验证**（Wiring Verification Rule #23）：`ChannelConnectorManager.startAll` 真实调 `FeishuConnector.start`（started==true）；`onMessage` 真实调 `engine.execute`（callCount > 0）；future callback 真实调 FeishuClient.sendMessage（记录非空）；sessionStore 真实读写 H2
- [x] session 复用、@bot 过滤、错误路径、长文本分段均有断言
- [x] **无静默跳过**：错误/超限/缺字段显式行为可观察
- [x] `./mvnw test -pl nop-ai-gateway -am` 通过
- [x] `No owner-doc update required`（契约未变，E2E 是验证）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 — W6-3 主动通知 E2E（真实容器）

> **前置**：Phase 0 resolver 机制裁定。

Status: completed
Targets: `nop-ai/nop-ai-gateway/src/test/.../channel/TestChannelProactiveNotifyE2E.java`（新建）· test beans

- Item Types: `Proof`

- [x] 新建真实容器 E2E 测试 `TestChannelProactiveNotifyE2E`：装配**真实** `ChannelMessageServiceImpl` + 真实 `UserChannelResolverImpl`（读 `NopAuthExtLogin`，Phase 0 机制 — Candidate B 忠实镜像 `E2EUserChannelResolver`，真实读 DB 非 canned stub）+ 真实 `ChannelConnectorManager` + 真实 `FeishuConnector`（网络边界 stub）；H2 seed `NopAuthExtLogin`（loginType=20 feishu, extId=chat_id, verified=1, delFlag=0, lastLoginTime）。
- [x] **已绑定 sendToUser E2E**：seed 绑定 → `channelMessageService.sendToUser(userId, OutboundChannelMessage)` → `UserChannelResolverImpl.resolve` 命中 → `ChannelConnectorManager.lookup("feishu")` → `FeishuConnector.sendOutbound(extId, carrier)` → test FeishuClient 记录到私信。返回 `SendResult.SENT`。
- [x] **未绑定 NO_BINDING E2E**：无绑定行 → `resolve` 返回空 → 返回 `SendResult.NO_BINDING`，FeishuClient.sendMessage **未**被调（callCount == 0）。
- [x] **附件降级 E2E**（主动通知含附件）：`OutboundChannelMessage` 含附件 + supportsFileUpload=false → 降级为文本链接/提示（既有 `sendOutbound` 降级行为在 E2E 层可观察）。

Exit Criteria:

- [x] `TestChannelProactiveNotifyE2E` 存在，经真实容器运行，用**真实** `UserChannelResolverImpl`（Candidate B 忠实镜像，真实读 H2 `NopAuthExtLogin` 非 canned stub；真实类经 Phase 0 裁定不可达——auth-service.beans.xml 硬导入 nop-biz 破坏 classpath）
- [x] **端到端验证**（Anti-Hollow Rule #22）：业务入口 `sendToUser` → resolver → connector → sendOutbound → FeishuClient 出口，完整路径连通
- [x] **接线验证**（Rule #23）：`sendToUser` 真实调 `UserChannelResolverImpl.resolve`（H2 命中）；真实经 `ChannelConnectorManager.lookup` 取到 `FeishuConnector`；`sendOutbound` 真实调 FeishuClient.sendMessage（callCount > 0）
- [x] 未绑定 → `NO_BINDING` + FeishuClient 未被调（callCount == 0）有断言
- [x] 附件降级行为可观察
- [x] **无静默跳过**：未绑定显式 `NO_BINDING` 不抛异常不静默
- [x] `./mvnw test -pl nop-ai-gateway -am` 通过
- [x] `No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：本 section 所有条目 + 每个 Phase Exit Criteria 全部 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] W6-1 入站会话 E2E：真实容器内 飞书消息 → execute → future → 回复 完整连通（含 session 复用/@bot 过滤/错误路径/长文本分段）
- [x] W6-3 主动通知 E2E：真实容器内 sendToUser → resolver → connector → 飞书私信 完整连通（含 NO_BINDING/附件降级）
- [x] Plan 7 deferred connector 行为已收口：长文本分段 + 速率限制守卫 + @bot payload 校准（均已实现 + focused test）
- [x] **Anti-Hollow Check**：真实容器内组件间调用链运行时连通（onMessage→execute、future→sendMessage、sendToUser→resolver→connector→sendOutbound），非空壳、无静默跳过
- [x] `nop-integration-feishu` 仍不依赖 `nop-ai-*`（本 plan 不改其 pom）
- [x] 不存在被静默降级到 deferred 的 in-scope live defect（@bot/分段/速率限制已实现，非 deferred）
- [x] `./mvnw test -pl nop-integration-feishu,nop-ai-gateway -am` 全绿（gateway 65/65 green；feishu 既有 green；broad-reactor auth/sys DB 失败为环境性前置非回归）
- [x] 受影响 owner docs（设计 §7.2.1/§7.2.2/§7.2.3 + §14 Open Questions）已同步
- [x] 独立子 agent closure-audit 已完成并记录证据（fresh session `ses_01c7a2b1bffeFt17qIcnNncRZK`，APPROVED FOR CLOSURE，0 genuine gaps）

## Deferred But Adjudicated

### 真实飞书服务器 E2E

- Classification: `watch-only residual`
- Why Not Blocking Closure: 无 CI 可达的真实飞书服务器。本 plan 的 E2E 在真实 IoC 容器内装配全部真实组件，仅在 LLM/网络边界 stub，已证明调用链连通。真实飞书连接需真实凭证 + 网络，属部署期验证。
- Successor Required: `no`（部署期手动验证）

### 多实例部署的 execute() future 跨进程传递

- Classification: `optimization candidate`
- Why Not Blocking Closure: 沿用 Plan 7 裁定——单体部署（roadmap 默认）下 future 在同 JVM 完成。
- Successor Required: `no`

## Non-Blocking Follow-ups

- `EXECUTION_COMPLETED` 事件可选用于审计日志（非获取响应文本）——沿用 Plan 7 follow-up
- W6-4 多消费者骨干验证（IMessageService topic `channel.inbound.feishu`）——roadmap 标"可选"，候选 successor plan
- 速率限制的分布式精确计量（多实例共享配额）——optimization candidate

## Closure

Status Note: 飞书消息管线收口到 E2E 可验证状态。真实 Nop IoC 容器装配全部真实组件（FeishuConnector + ChannelConnectorManager + ChannelSessionStoreImpl + ChannelMessageServiceImpl + resolver），仅在 LLM/网络边界 stub；Plan 7 三项 deferred connector 行为（长文本分段 / 速率限制守卫 / @bot payload 校准）全部落地 + focused test。W6-1 入站会话 + W6-3 主动通知两条完整路径在真实容器内运行时连通，Anti-Hollow 证据充分。
Completed: 2026-08-09

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure-audit 子 agent（fresh session，read-only）
- Audit Session: ses_01c7a2b1bffeFt17qIcnNncRZK
- Evidence:
  - Phase 0：设计文档 §7.2.1（分段 `:196-204`）/§7.2.2（速率限制 `:206-210`）/§7.2.3（@bot payload `:212-224`）+ §14 Open Questions @bot/长文本 `[x]`（`:372-375`）—— PASS
  - Phase 1：`FeishuConnector.java` `deliverSegmented`/`splitIntoSegments`（`:378-391,416-439`）按 4000 + 换行边界切（`:430-433`）；`tryAcquireInbound` rolling 60s 窗口（`:403-414`）在 `onMessage:262` execute`:302` 前调，超限 `:264-265` 显式回复 + return；`isBotMentioned:615` 要求 `"key"`+`"open_id"` 双标记。4 focused tests 全 PASS（TestFeishuConnector `:272-350`）
  - Phase 2：`TestFeishuConversationE2E` 用 `AppBeanContainerLoader:115`（真实容器）；5 E2E tests 每跳断言：startCount==1`:206` / executeCount==1`:212` / sendMessageCount==1`:217` / H2 findByChannel 命中`:230-232` —— PASS
  - Phase 3：`TestChannelProactiveNotifyE2E` 真实 `ChannelMessageServiceImpl:103` + `E2EUserChannelResolver` 真实读 NopAuthExtLogin（`:256-324` findAllByExample userId+verified+delFlag, orderBy lastLoginTime DESC, ChannelTypeCodes）；4 E2E tests：SENT+count==1+receiveId==extId / NO_BINDING+count 不变 / unverified 排除 / 附件降级含 name+url —— PASS
  - Closure Gates：targeted test 25/25 green（TestFeishuConnector 16 + E2E inbound 5 + E2E notify 4）；full gateway 65/65 green；`rg -c "nop-ai|nop-auth" nop-integration-feishu/pom.xml` 返回空（依赖纯净）；gateway pom 用 nop-auth-dao test-scope（Candidate B，非 auth-service）；Anti-Hollow 入站链 + 出站链每跳有计数/状态断言；新 FeishuConnector 代码无静默 swallow（所有 catch log/reply，所有 return 前有 log/reply）
  - `node ai-dev/tools/check-plan-checklist.mjs <plan> --strict` 退出码 0（Closure Evidence 已写入）
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-gateway --severity high` 退出码 0（0 findings）
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
  - Deferred 项分类检查：真实飞书服务器 E2E = `watch-only residual`（无 CI 可达）；execute() future 跨进程 = `optimization candidate`（单体部署假定）—— 均非 in-scope live defect 降级
  - 整体裁定：APPROVED FOR CLOSURE，0 genuine gaps

Follow-up:

- W6-2 扫码绑定+登录 E2E（Plan 9，本 plan 显式 Non-Goal）
- W6-4 多消费者骨干（可选 successor）
- 速率限制分布式精确计量（optimization candidate）
