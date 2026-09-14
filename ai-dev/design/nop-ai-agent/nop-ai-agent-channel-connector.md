# Nop AI Agent 外部信道适配器设计

## 1. 目标

定义 nop-ai-agent 与外部消息信道（飞书、钉钉、企微、Telegram、Webhook、API 等）之间的通用连接抽象。

本篇讨论的是 **Gateway 层**的信道适配器设计，不是引擎层。适配器将外部信道协议转换为引擎层的 `IAgentEngine.sendMessage()` 调用和 `IAgentEventPublisher` 事件订阅，引擎层零改动即可接入新信道。

## 2. 核心问题

1. 外部信道协议差异巨大（飞书 Stream SDK + Protobuf、钉钉 Stream 长连接、企微 HTTP 回调、Telegram Bot API、HTTP Webhook），如何用统一抽象屏蔽差异？
2. 输入（用户消息 → Agent）和输出（Agent 响应 → 用户）是两条不同路径，如何分离？
3. 与引擎层 `IMessageService`（Actor 间内部通信）的关系是什么？
4. 信道能力差异如何声明？（有的信道支持文件传输、有的不支持 Markdown、有的有速率限制）

## 3. 设计决策

| 决策 | 选择 | 理由 |
|------|------|------|
| 架构层级 | Gateway 层适配器模式 | 引擎层（AgentActor / IMessageService）不感知外部信道存在 |
| 信道抽象 | `IChannelConnector` 接口 | 参考 SolonCode Channel（41 行 3 方法）+ Nanobot BaseChannel 的精华 || 输入输出分离 | 接口内双向方法 | 输入侧：webhook 回调 / 长连接消息 → `IAgentEngine.sendMessage()`；输出侧：订阅 `AgentEvent` → 信道原生 API |
| 消息格式转换 | 各适配器内部处理 | 外部格式五花八门，在适配器内转成 `AgentMessageRequest` 即可，不需要额外的消息转换层 |
| 信道能力声明 | 适配器自描述 | 参考 OpenSquilla 通道能力矩阵，每个适配器声明自己的能力集 |
| 会话绑定 | `channelType:channelSessionId` | 参考 Nanobot 的 `channel:chat_id` 格式，引擎 sessionId 由 Gateway 维护映射 |

## 4. 架构定位

```
┌──────────────────────────────────────────────────────────────┐
│                      外部信道                                  │
│   飞书 · 钉钉 · 企微 · Telegram · Discord · Webhook · API    │
└──────────┬──────────┬──────────┬──────────┬──────────────────┘
           │          │          │          │
           ▼          ▼          ▼          ▼
┌──────────────────────────────────────────────────────────────┐
│                 Gateway 层 — 信道适配器                        │
│                                                              │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────┐     │
│  │ Feishu   │ │ DingTalk │ │ WeCom    │ │ Webhook      │     │
│  │Connector │ │Connector │ │Connector │ │Connector     │     │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘ └──────┬───────┘     │
│       │            │            │               │             │
│       └────────────┴──────┬─────┴───────────────┘             │
│                           │                                   │
│                  IChannelConnector                               │
│                  信道会话映射表                                  │
│                  AgentEvent → 信道消息转换                      │
└───────────────────────────┬──────────────────────────────────┘
                            │
                            │  IAgentEngine.sendMessage()
                            │  IAgentEventPublisher.subscribe()
                            │
┌───────────────────────────┴──────────────────────────────────┐
│                    Agent 引擎层（已有）                         │
│                                                              │
│  IAgentEngine · AgentActor · IMessageService                 │
│  IAgentEventPublisher · IPermissionMatrix (channelKind)       │
└──────────────────────────────────────────────────────────────┘
```

**关键边界**：

- **Gateway 层以上**：各信道原生协议，千差万别
- **Gateway 层内部**：`IChannelConnector` 统一抽象，屏蔽协议差异
- **Gateway 层以下**：引擎层只看到 `AgentMessageRequest`（带 `channelKind` 元数据）和 `AgentEvent`

## 5. IChannelConnector 接口

```java
public interface IChannelConnector {
    String getChannelType();

    void start(ChannelConnectorContext context);

    void stop();

    ChannelCapabilities getCapabilities();
}
```

### 5.1 方法说明

| 方法 | 职责 | 说明 |
|------|------|------|
| `getChannelType()` | 返回信道类型标识 | 如 `"feishu"`, `"dingtalk"`, `"wecom"`, `"webhook"`, `"api"` |
| `start(context)` | 启动信道连接 | 建立 webhook 监听 / 长连接 / 轮询等。context 提供引擎层依赖 |
| `stop()` | 停止信道连接 | 优雅关闭，释放资源 |
| `getCapabilities()` | 声明信道能力 | 适配器自描述。**RESERVED（P2-CHANNEL，2026-09-14）**：当前无生产消费者，为后续权限矩阵和消息格式化适配保留；每信道自身的降级（长文本分段、附件链接化）在连接器内部用自身常量实现，不经此 SPI（见 §8 裁定） |

### 5.2 ChannelConnectorContext

```java
public class ChannelConnectorContext {
    IAgentEngine agentEngine;
    IAgentEventPublisher eventPublisher;
    ChannelConfig config;
}
```

适配器通过 `agentEngine.sendMessage()` 发送用户消息，通过订阅 `eventPublisher` 接收 Agent 响应。

### 5.3 设计原则

- **引擎零改动**：新增信道只需实现 `IChannelConnector`，通过 Nop IoC 注册，引擎代码不变
- **接口最小化**：参考 SolonCode Channel 的 41 行 3 方法哲学，接口只有 4 个方法
- **适配器自治**：每个适配器自行管理连接生命周期、凭证、重连、速率限制
- **输入输出在适配器内闭环**：输入（webhook/长连接 → sendMessage）和输出（AgentEvent → 信道 API）都在适配器内部处理

## 6. 信道会话映射

外部信道的会话标识与引擎 sessionId 之间的映射由 Gateway 层维护：

```
外部信道会话                    Gateway 映射                  引擎 sessionId
─────────────                  ────────────                 ──────────────
飞书 chat_id: "oc_xxx"    →   ChannelSession{               →  "sess-abc123"
                                channelType="feishu",
                                channelId="oc_xxx",
                                sessionId="sess-abc123"
                              }
钉钉 conversationId: "cid" →  ChannelSession{               →  "sess-def456"
                                channelType="dingtalk",
                                channelId="cid",
                                sessionId="sess-def456"
                              }
```

**映射表存储**：使用引擎的持久化接口（IOrmSession），存储 `ai_channel_session` 表。字段：`channelType`, `channelId`, `sessionId`, `agentName`, `createdAt`, `lastActiveAt`。

**映射逻辑**：

1. 收到外部消息 → 按 `channelType:channelId` 查找映射
2. 找到映射 → 复用已有 `sessionId` 调用 `agentEngine.sendMessage()`
3. 未找到映射 → 创建新 session，`agentEngine.sendMessage(request)` 返回 ack 中的 `sessionId` 写入映射表

## 7. 消息流转

### 7.1 输入路径（用户 → Agent）

```
飞书用户发消息
  │
  ▼
FeishuConnector（IMessageHandler.onMessage 回调，由 FeishuClient 投递）
  │  1. 提取消息文本、发送者信息、chat_id（receiveId）
  │  2. 群聊 @bot 过滤（群聊未 @bot → 不处理）
  │  3. IChannelSessionStore.findByChannel 查映射 → 命中复用 sessionId / 未命中新建
  ▼
agentEngine.execute(AgentMessageRequest{
    sessionId = mappedSessionId,     // 复用或 null（新建）
    agentName = config.agentName,
    userMessage = extractedText,
    metadata = {
        "channelType": "feishu",
        "channelId": "oc_xxx",
        "senderId": "user_id"
    },
    channelKind = DM | GROUP          // 供 IPermissionMatrix 使用
})
  │
  ▼
CompletableFuture<AgentExecutionResult>   // 出站路径经此 future 回调，见 §7.2
```

> **为何用 `execute()` 而非 `sendMessage()`**：`sendMessage()` 是 fire-and-forget（立即返回 `AgentMessageAck`，不返回执行结果），无法获取响应文本。`execute()` 返回 `CompletableFuture<AgentExecutionResult>`，connector 经 future callback 取响应文本回复用户。

### 7.2 输出路径（Agent → 用户）

> **关键裁定（W5-3 Phase 0）**：经代码核实，Agent 事件 payload **不含响应文本**。`EXECUTION_COMPLETED` 事件 payload 只含 metrics（`totalIterations`/`totalTokensUsed`/`durationMs`/`guardrailBlocked`，见 `ReActAgentExecutor.java:1333-1340`）；`LLM_RESPONSE_RECEIVED` payload 只含 `iteration`/`hasToolCalls`（`ReActAgentExecutor.java:971-974`，`hasToolCalls` 计算点 `:973`）。响应文本在 `AgentExecutionResult.getMessages()` 最后一条 assistant 消息中。因此 connector **不能**经事件订阅获取响应文本，必须经 `IAgentEngine.execute()` 的 `CompletableFuture<AgentExecutionResult>` 获取。
>
> **拒绝方案：事件订阅**（原设计假设）。事件 payload 无文本，connector 无法经事件获取响应。事件仍可用于状态追踪（如记录 `EXECUTION_STARTED`），但**不用于获取响应文本**。
>
> **优势**：`execute()` 的 future callback 闭包直接捕获 `chatId`，无需内存反向映射（sessionId→chatId）；无需事件订阅/取消订阅的生命周期管理；无需 TextChunk delta 合并（`execute` 返回完整结果，飞书不支持流式）。
>
> **约束**：单体部署假定（`execute` 的 future 在同 JVM 完成）。多实例部署需 future 的跨进程传递（后续部署演进）。

```
// 入站处理 lambda 内（chatId 已在闭包中捕获）：
CompletableFuture<AgentExecutionResult> future = agentEngine.execute(request);
future.whenComplete((result, error) -> {
    if (error != null || result.getStatus() == failed) {
        feishuClient.sendMessage(chatId, "执行出错: " + describeError(error, result));
    } else {
        String text = extractLastAssistantText(result.getMessages());
        if (text != null && !text.isEmpty()) {
            feishuClient.sendMessage(chatId, text);
        }
        // 新 session 保存映射
        if (isNewSession && result.getSessionId() != null) {
            sessionStore.saveMapping("feishu", chatId, result.getSessionId(), agentName);
        }
    }
});
```

#### 7.2.1 长文本分段（W6-1 裁定）

> **裁定（W6-1 Phase 0）**：飞书消息长度上限 4000 字符（`ChannelCapabilities.maxMessageLength`）。当 `sendReply`/`sendOutbound` 待发文本长度 > `maxMessageLength` 时，按以下策略切片**顺序**经 `feishuClient.sendMessage` 发送多段：
>
> 1. 优先在换行符 `\n` 边界切（避免割断语义单元）；
> 2. 无换行符或换行符间距 > `maxMessageLength` 时，按 `maxMessageLength` 硬切；
> 3. 切片**不丢字符**——所有分段拼接后等同原文。
>
> **不静默截断**：超长响应不丢尾部、不静默丢弃，必须分段发完整。此行为在 `sendReply`（agent 回复）与 `sendOutbound`（主动通知）出站路径统一生效。

#### 7.2.2 入站速率限制守卫（W6-1 裁定）

> **裁定（W6-1 Phase 0）**：connector 维护 rolling 60s 滑动窗口的入站消息计数；窗口内计数超 `ChannelCapabilities.rateLimitPerMinute`（飞书=3000）时，`onMessage` **显式回复**"请求过于频繁，请稍后再试"并**不转发到 `IAgentEngine.execute`**（防刷引擎），不静默丢消息。窗口内未超限则正常转发。
>
> **拒绝方案：静默丢弃**。超速场景必须有可观察的显式回复，而非 `return`/`continue` 静默吞掉（Minimum Rules #24）。速率限制是单 connector 实例内的近似计量，多实例共享配额属 optimization candidate（Non-Blocking Follow-up）。

#### 7.2.3 群聊 @bot payload 形状（W6-1 裁定）

> **裁定（W6-1 Phase 0 fork (c)）**：飞书 `im.message.receive_v1` event 中 `mentions` 数组形状（基于飞书开放平台文档）：
> ```json
> "mentions": [
>   {"key":"@_user_1","id":{"open_id":"ou_xxx","union_id":"on_xxx","name":"..."}}
> ]
> ```
> 要精确判定"mentions 含 bot 自己"需知 bot 的 `open_id`，而当前 `FeishuCredentials`（appId/appSecret/verificationToken/encryptKey）与 `FeishuClient.start` 均不提供 bot 身份。
>
> **Fork 裁定**：选 (c) — 仅文档化 payload 形状 + 保留启发式判据（解析 `mentions` 数组、按文档化形状识别 `key`/`id.open_id` 字段是否存在），精确 bot `open_id` 匹配 defer 到真实飞书 E2E（watch-only residual）。**拒绝 (a)/(b)**（给 `FeishuCredentials`/`ChannelConfig` 加 bot-id 字段或 `FeishuClient.start` 调飞书 API 取 bot 身份）：触及 `nop-integration-feishu`，而本信道集成保持飞书协议层不依赖 AI 的边界（roadmap 完成定义）；bot 身份在真实部署期配置即可，当前 E2E 用文档化形状驱动解析器 focused test。
>
> **解析语义**：`mentions` 缺失/空/字段缺失 → 按"未 @"处理（正确语义：群聊未 @bot 不回复）；`mentions` 非空且含文档化形状 → 按"已 @"处理（触发 execute）。缺字段按"未 @"而非静默跳过应处理逻辑。

### 7.3 中间事件处理策略

> **裁定（W5-3 Phase 0）**：飞书信道改用 `execute()` + future callback（§7.2）后，中间事件 delta 合并/文本增量策略**不再适用于飞书**——`execute()` 返回完整结果后一次性回复，无中间增量。下表的"文本增量"策略仅适用于 SSE/WebSocket 等流式信道（需经 `IAgentEventPublisher` 订阅中间事件实现）。

Agent 执行过程中会产生中间事件（TextChunk, ThinkingChunk, ToolCallStart, ToolCallComplete）。**流式信道**（WebSocket, SSE）的适配器根据信道能力决定如何处理：

| 策略 | 适用信道 | 行为 |
|------|---------|------|
| **全部等待** | Webhook, API, 飞书, 钉钉, 企微 | 经 `execute()` future 一次性取完整结果，中间事件全部忽略 |
| **实时流式** | WebSocket, SSE | 经 `IAgentEventPublisher` 订阅，逐事件转发，客户端自行渲染 |

参考 Nanobot 的 `_coalesce_stream_deltas` delta 合并优化：当 LLM 产出速度超过信道发送速率时，合并同一会话的连续文本增量，避免触发速率限制。**此优化仅对流式信道有效**；飞书等非流式信道经 `execute()` 取完整结果，无需 delta 合并。

## 8. 信道能力声明

参考 OpenSquilla 的 27 能力标签体系，每个适配器声明自己的能力：

```java
public class ChannelCapabilities {
    boolean supportsMarkdown;
    boolean supportsFileUpload;
    boolean supportsFileDownload;
    boolean supportsStreaming;
    boolean supportsGroupChat;
    boolean supportsMentions;
    boolean supportsTypingIndicator;
    int maxMessageLength;
    int maxFileSize;
    int rateLimitPerMinute;
}
```

**用途**：

- **消息格式化**（RESERVED，P2-CHANNEL 2026-09-14）：Agent 响应包含 Markdown 时，`supportsMarkdown=false` 的信道需要转为纯文本——此能力消费尚未接线，登记为 reserved（见下方裁定），不作为当前承诺
- **权限矩阵**：`IPermissionMatrix` 可结合 `channelKind` 和能力集决定允许的工具层级（同样 reserved，当前 `channelKind` 已供权限矩阵使用，能力集字段尚未被消费）
- **速率限制**：适配器自行遵守，引擎不感知

> **getCapabilities 归宿裁定（P2-CHANNEL，2026-09-14，plan 2026-09-14-1937-2 Option B = reserved）**：
> 1. **现状**：`IChannelConnector.getCapabilities()`/`ChannelCapabilities` 无生产消费者；FeishuConnector 的降级（`deliverSegmented` 长文本分段、附件→链接/提示）用自身常量（`MAX_MESSAGE_LENGTH` 等）在连接器内部实现，不经 SPI。
> 2. **拒绝方案 A（在 `ChannelMessageServiceImpl.sendToUser` 消费做通用降级）**：(a) 会在 FeishuConnector 自身降级之外形成第二个降级边界，同一能力双份处理（双重转换风险）；(b) 通用截断语义与 §7.2.1"不静默截断、必须分段发完整"的既有裁定冲突（截断会丢内容，分段是连接器职责）；(c) 当前无真实消费者，提前接线属投机 API。
> 3. **落地**：`IChannelConnector.getCapabilities()` 与 `ChannelCapabilities` javadoc 标注 RESERVED + 裁定说明；本 doc §5.1 同步。将来若新增真实消费者（如纯文本 webhook 信道的 markdown→text 适配），需移除 reserved 标记并在消费边界补回归测试。

## 9. 与 IMessageService 的关系

```
┌─────────────────────────────────────────────────┐
│                   Gateway 层                      │
│                                                  │
│  IChannelConnector                                 │
│    │ 输入: agentEngine.execute()                  │
│    │ 输出: execute() future callback              │
│    │   (流式信道经 IAgentEventPublisher 订阅)      │
│    │                                              │
│    │    ↕ 同一 JVM 内的方法调用，不是消息传递        │
│    │                                              │
│  IAgentEngine  ────→  AgentActor                  │
│                         │                         │
└─────────────────────────┼─────────────────────────┘
                          │
                          │  IMessageService
                          │  (Actor 间内部通信)
                          │
                    ┌─────┴──────┐
                    │ AgentActor │ ←──→ │ AgentActor │
                    │   (Lead)   │       │  (Worker)  │
                    └────────────┘       └────────────┘
```

| 层级 | 通信机制 | 用途 |
|------|---------|------|
| **Gateway → Agent** | `IAgentEngine.execute()` 方法调用（返回 `CompletableFuture`） | 外部用户消息投递到 Agent + 取回执行结果 |
| **Agent → Gateway** | `execute()` future callback（非流式）/ `IAgentEventPublisher` 事件订阅（流式信道） | Agent 执行结果/事件推送到外部 |
| **Agent ↔ Agent** | `IMessageService` 消息传递 | Actor 间内部通信（call-agent / send-message） |

**IMessageService 是引擎内部通信**，信道适配器不使用它。适配器直接调用 `IAgentEngine.execute()` 经 future callback 取响应文本（非流式信道），或订阅 `IAgentEventPublisher` 取中间增量（流式信道），是同一 JVM 内的方法调用/事件观察，不是消息传递。

**多实例部署时**：Gateway 和 Agent 可能不在同一 JVM。此时 `execute()` 的 `CompletableFuture` 需跨进程传递（或改用远程回调），Gateway 通过 REST/GraphQL 调用 Agent 服务。但接口不变——适配器内部封装远程调用细节。

## 10. 凭证管理

每个适配器自行管理凭证，通过 Nop IoC 注入配置：

```xml
<!-- feishu-connector.beans.xml -->
<bean id="feishuConnector" class="io.nop.ai.agent.channel.FeishuConnector">
    <property name="appId" value="${nop.ai.channel.feishu.appId}" />
    <property name="appSecret" value="${nop.ai.channel.feishu.appSecret}" />
    <property name="verificationToken" value="${nop.ai.channel.feishu.verificationToken}" />
    <property name="encryptKey" value="${nop.ai.channel.feishu.encryptKey}" />
</bean>
```

凭证通过 Nop 标准的配置加密机制保护，不使用明文文件。实际机制为 `DefaultConfigValueEnhancer`（`nop-core-framework/nop-config`，实现 `IConfigValueEnhancer`）识别配置值的 `@sec:` 前缀（常量 `CommonConstants.SEC_VALUE_PREFIX` / `ConfigConstants.CFG_SEC_PREFIX`），匹配后经 `AESTextCipher`（`io.nop.commons.crypto.impl`，实现 `ITextCipher`）解密。即 `@InjectValue("${nop.integration.feishu.appSecret}")` 注入的 `@sec:...` 形式值会被自动解密。（术语修正：原文档此处写的 `nop-config-encrypt` 不是实际模块名，实际走 `DefaultConfigValueEnhancer` + `@sec:` 前缀 + `AESTextCipher` 路径。）

> **凭证解析优先级（P2-CHANNEL，2026-09-14，plan 2026-09-14-1937-2）**：live `FeishuConnector.resolveCredentials` 的落地形态为四级（与上文示例的 per-property 注入不同——连接器统一经 `FeishuClient.start(credentials, handler)` 传递凭证对象）：
> 1. ChannelConfig options 面——`feishu.appId` + `feishu.appSecret` 成对字面值；
> 2. ChannelConfig options 面——`feishu.credentials` 对象（字面语义；options 面**不**引入 `credentialId` 引用语义）；
> 3. 注入的 `nopFeishuCredentials` bean（`nop.integration.feishu.*` 配置，含 `nop.integration.feishu.credentialId` 凭证库引用，由 `FeishuClient.start` 解析）；
> 4. 空凭证兜底——由 `FeishuClient.start` fail-fast（"appId not configured"）。
> 回归测试：`TestFeishuConnector` 3 例（注入 bean 透传 / options 优先 / 双缺空凭证）+ `TestFeishuConnectorIoC` 注入断言。

## 11. 飞书适配器参考设计

作为第一个实现的信道适配器，飞书的特殊性：

| 维度 | 飞书特性 | 适配器处理 |
|------|---------|-----------|
| **连接方式** | Stream SDK 长连接（推荐）或 HTTP 回调 | 优先使用 Stream SDK，无需公网 IP |
| **消息格式** | Protobuf 编解码 | 适配器内部转成纯文本 |
| **消息类型** | text / rich_text / interactive | 提取 text 内容作为 userMessage |
| **回复方式** | Open API `im/v1/messages` | `AgentResult` → 飞书消息 API |
| **事件订阅** | `im.message.receive_v1` | 收到消息事件 → `sendMessage()` |
| **速率限制** | 50 msg/s (app level) | 适配器内做速率控制 + delta 合并 |
| **群聊/私聊** | chat_id 区分 p2p / group | 映射到 channelKind=dm / group |
| **文件传输** | 飞书 API upload/download | 暂不实现，后续通过工具支持 |

**飞书适配器最小实现范围**：

1. Stream SDK 长连接建立
2. 收到 im.message.receive_v1 事件 → 提取文本 → sendMessage
3. 订阅 AgentResult → 飞书消息 API 回复
4. 信道会话映射（飞书 chat_id ↔ 引擎 sessionId）
5. 错误处理（凭证失效重连、速率限制重试）

## 12. 分层归属

| 接口 | 层级 | 说明 |
|------|------|------|
| `IChannelConnector` | Gateway 层（应用层） | 不在引擎 Layer 1~4 中 |
| `ChannelCapabilities` | Gateway 层 | 适配器自描述 |
| `ChannelSession` 映射表 | Gateway 层 | 使用引擎持久化接口，但逻辑在 Gateway |
| `channelKind` 元数据 | 引擎 Layer 1 | `IPermissionMatrix` 按 channelKind 分级（已有设计） |

信道适配器是**应用层集成代码**，不是引擎核心。它的存在不改变引擎层的任何设计——引擎通过 `IAgentEngine` 和 `IAgentEventPublisher` 与外部交互，不关心消息来自飞书还是 API。

## 13. 参考来源

| 项目 | 参考点 | 价值 |
|------|--------|------|
| SolonCode | Channel 接口（41 行 3 方法）+ WebStreamBuilder.bind() 路由 | 接口最小化哲学、sessionId 绑定模式 |
| Nanobot | BaseChannel + MessageBus 双总线 + ChannelManager 优化 | 输入/输出分离、delta 合并、重复抑制 |
| OpenSquilla | 通道能力矩阵（27 标签 + GREEN/YELLOW/RED）| 声明式能力模型、通道→工具权限矩阵 |
| PilotDeck | Gateway 接口（387 行）+ CanonicalMessage | Gateway 功能完备性的参考上限 |
| Hermes | 30 通道 + Profile 隔离 | 大规模通道管理的工程实践 |

## 14. Open Questions

- [x] 飞书 Stream SDK 的 Java 版本选型——官方 SDK (`oapi-sdk-java`) 还是独立实现？
  - **裁定（W5-0 / 2026-08-09）：独立实现（选项 B）**。
  - **决定性理由**：
    1. **baseline 前提修正**：选项 B 的"无外部依赖"前提在 Java 11+ 项目中成立——`java.net.http.HttpClient` 与 `java.net.http.WebSocket` 是 JDK 标准库（自 Java 11 起）。本项目 `<maven.compiler.release>11</maven.compiler.release>`，故 WebSocket 传输经 JDK 零外部依赖获得。baseline 原将"Java 11 HttpClient"与 OkHttp/Netty 并列为"须引入的 WebSocket 客户端库"系误判。
    2. **`oapi-sdk-java` 体积失称**：官方 SDK 是全平台客户端（im/contact/doc/drive/… 全部服务模型）并自带 shaded protobuf（`larksuite-oapi-shaded-protobuf`）——SDK 自身 shade protobuf 正说明 protobuf 版本冲突是真实痛点。把整套 SDK 引入"厂商协议层"模块（roadmap 要求该模块可被非 AI 场景复用）会让所有消费方耦合飞书全量 SDK。仓库先例 `sms-tencent→qcloudsms` 不具类比性：`qcloudsms` 是聚焦 SMS 的小库，非全平台 SDK。
    3. **roadmap 硬性要求最大化满足**：roadmap 要求 `FeishuPbCodec` "独立可测（纯字节进出，不依赖网络或 SDK 连接）"。选项 B 下 codec 覆盖完整 Pbbp2 帧编解码（method=0 CONTROL / 1 DATA / 2 ACK），作为一等组件被完整单测；选项 A 下 SDK 内部自有帧编解码，codec 沦为边缘。
    4. **避开 protobuf 依赖**：`protobuf` 未被 `nop-bom` 管理。为单一 Frame 消息（method/headers/payload 三字段）手写 protobuf wire format（发布标准 https://protobuf.dev/programming-guides/encoding/）约 80 行自洽代码，规避引入 `protobuf-java` 及其版本管理负担。
  - **外部依赖后果：无**。`nop-integration-feishu` 模块的 pom 维持单一依赖 `nop-integration-api`。WebSocket 传输 = `java.net.http.WebSocket`（JDK stdlib）；Open API 调用 = `java.net.http.HttpClient`（JDK stdlib）；帧编解码 = 手写 protobuf wire format（无 `protobuf-java`）。
  - **拒绝的替代（选项 A）理由**：(a) 依赖足迹失称（全平台 SDK + shaded protobuf）；(b) 边缘化 roadmap 要求的独立可测 `FeishuPbCodec`；(c) protobuf 版本冲突风险（SDK 自身 shading 即证据）；(d) 飞书 SDK 版本锁定与升级风险。
  - **wire-format 来源**：protobuf 编码规则为发布标准；Frame 消息结构（method/headers/payload 字段）由官方飞书/Lark SDK 包结构（`com.lark.oapi.core.ws`）及跨语言一致性（Go/Java/Node SDK）佐证。字段号级 wire 兼容性于 W6 E2E 对真实飞书 Stream 服务器验证（归入 plan 的 Deferred But Adjudicated，非静默跳过）。
- [x] 群聊场景下 @机器人 的消息过滤策略——是否只处理 @当前机器人 的消息？
  - **裁定（W6-1 / 2026-08-09）：fork (c) — 文档化 payload 形状 + 启发式判据，精确 bot open_id 匹配 defer 到真实飞书 E2E**。详见 §7.2.3。`mentions` 数组按飞书开放平台文档形状（`{"key":"@_user_1","id":{"open_id":"ou_xxx",...}}`）解析；缺字段按"未 @"处理（正确语义）。拒绝给 `FeishuCredentials` 加 bot-id 字段（fork a）/ `FeishuClient.start` 调 API 取身份（fork b）：保持飞书协议层不依赖 AI 的边界。
- [x] 长文本响应的分段发送策略——飞书消息有长度限制，超长响应如何分段？
  - **裁定（W6-1 / 2026-08-09）**：按 `maxMessageLength`（4000）切片，换行边界优先，硬切兜底，顺序多段发送，不丢字符。详见 §7.2.1。
- [ ] 多媒体消息（图片、文件）的支持范围——是否通过工具（file-upload / file-download）实现？
- [ ] 适配器的 Nop IoC 注册方式——`beans.xml` + `@Inject`，还是 `@Configuration`？
