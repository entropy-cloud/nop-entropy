# Nop AI Agent 外部信道适配器设计

## 1. 目标

定义 nop-ai-agent 与外部消息信道（飞书、钉钉、企微、Telegram、Webhook、API 等）之间的通用连接抽象。

本篇讨论的是 **Gateway 层**的信道适配器设计，不是引擎层。适配器将外部信道协议转换为引擎层的 `IAgentEngine.sendMessage()` 调用和 `AgentEventPublisher` 事件订阅，引擎层零改动即可接入新信道。

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
| 消息格式转换 | 各适配器内部处理 | 外部格式五花八门，在适配器内转成 `AgentMessageRequest` 即可，不需要额外的 CanonicalMessage 层 |
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
                            │  AgentEventPublisher.subscribe()
                            │
┌───────────────────────────┴──────────────────────────────────┐
│                    Agent 引擎层（已有）                         │
│                                                              │
│  IAgentEngine · AgentActor · IMessageService                 │
│  AgentEventPublisher · IPermissionMatrix (channelKind)       │
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
| `getCapabilities()` | 声明信道能力 | 适配器自描述，供权限矩阵和消息格式化参考 |

### 5.2 ChannelConnectorContext

```java
public class ChannelConnectorContext {
    IAgentEngine agentEngine;
    AgentEventPublisher eventPublisher;
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
FeishuConnector（收到飞书 Event callback）
  │  1. 解密/验签
  │  2. 提取消息文本、发送者信息、chat_id
  │  3. 查找或创建 ChannelSession 映射
  ▼
agentEngine.sendMessage(AgentMessageRequest{
    sessionId = mappedSessionId,     // 复用或新建
    agentName = config.agentName,
    userMessage = extractedText,
    metadata = {
        "channelType": "feishu",
        "channelId": "oc_xxx",
        "senderId": "user_id",
        "channelKind": "dm"          // 供 IPermissionMatrix 使用
    }
})
  │
  ▼
AgentMessageAck{ sessionId, status="accepted" }
```

### 7.2 输出路径（Agent → 用户）

```
适配器在 start() 时订阅 eventPublisher：
eventPublisher.subscribe("agent.{sessionId}.events", event -> {
    if (event instanceof AgentResult) {
        // 提取文本响应
        String text = ((AgentResult) event).getText();
        // 调用飞书 API 发送消息
        feishuClient.sendMessage(channelId, text);
    } else if (event instanceof AgentError) {
        feishuClient.sendMessage(channelId, "执行出错: " + error.getMessage());
    }
    // TextChunk/ThinkingChunk 等中间事件 — 根据信道能力决定是否转发
});
```

### 7.3 中间事件处理策略

Agent 执行过程中会产生中间事件（TextChunk, ThinkingChunk, ToolCallStart, ToolCallComplete）。适配器根据信道能力决定如何处理：

| 策略 | 适用信道 | 行为 |
|------|---------|------|
| **全部等待** | Webhook, API | 只发送 `AgentResult`，中间事件全部忽略 |
| **文本增量** | 飞书, 钉钉, 企微 | 累积 TextChunk，在 ThinkingChunk 或 AgentResult 时合并发送 |
| **实时流式** | WebSocket, SSE | 逐事件转发，客户端自行渲染 |

参考 Nanobot 的 `_coalesce_stream_deltas` delta 合并优化：当 LLM 产出速度超过信道发送速率时，合并同一会话的连续文本增量，避免触发速率限制。

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

- **消息格式化**：Agent 响应包含 Markdown 时，`supportsMarkdown=false` 的信道需要转为纯文本
- **权限矩阵**：`IPermissionMatrix` 可结合 `channelKind` 和能力集决定允许的工具层级
- **速率限制**：适配器自行遵守，引擎不感知

## 9. 与 IMessageService 的关系

```
┌─────────────────────────────────────────────────┐
│                   Gateway 层                      │
│                                                  │
│  IChannelConnector                                 │
│    │ 输入: agentEngine.sendMessage()              │
│    │ 输出: eventPublisher.subscribe()              │
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
| **Gateway → Agent** | `IAgentEngine.sendMessage()` 方法调用 | 外部用户消息投递到 Agent |
| **Agent → Gateway** | `AgentEventPublisher` 事件订阅 | Agent 执行结果/事件推送到外部 |
| **Agent ↔ Agent** | `IMessageService` 消息传递 | Actor 间内部通信（call-agent / send-message） |

**IMessageService 是引擎内部通信**，信道适配器不使用它。适配器直接调用 `IAgentEngine` 和订阅 `AgentEventPublisher`，是同一 JVM 内的方法调用/事件观察，不是消息传递。

**多实例部署时**：Gateway 和 Agent 可能不在同一 JVM。此时 Gateway 通过 REST/GraphQL 调用 Agent 服务，`IChannelConnector` 的实现改为远程调用。但接口不变——适配器内部封装远程调用细节。

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

凭证通过 Nop 标准的配置加密机制（`nop-config-encrypt`）保护，不使用明文文件。

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

信道适配器是**应用层集成代码**，不是引擎核心。它的存在不改变引擎层的任何设计——引擎通过 `IAgentEngine` 和 `AgentEventPublisher` 与外部交互，不关心消息来自飞书还是 API。

## 13. 参考来源

| 项目 | 参考点 | 价值 |
|------|--------|------|
| SolonCode | Channel 接口（41 行 3 方法）+ WebStreamBuilder.bind() 路由 | 接口最小化哲学、sessionId 绑定模式 |
| Nanobot | BaseChannel + MessageBus 双总线 + ChannelManager 优化 | 输入/输出分离、delta 合并、重复抑制 |
| OpenSquilla | 通道能力矩阵（27 标签 + GREEN/YELLOW/RED）| 声明式能力模型、通道→工具权限矩阵 |
| PilotDeck | Gateway 接口（387 行）+ CanonicalMessage | Gateway 功能完备性的参考上限 |
| Hermes | 30 通道 + Profile 隔离 | 大规模通道管理的工程实践 |

## 14. Open Questions

- [ ] 飞书 Stream SDK 的 Java 版本选型——官方 SDK (`oapi-sdk-java`) 还是独立实现？
- [ ] 群聊场景下 @机器人 的消息过滤策略——是否只处理 @当前机器人 的消息？
- [ ] 长文本响应的分段发送策略——飞书消息有长度限制，超长响应如何分段？
- [ ] 多媒体消息（图片、文件）的支持范围——是否通过工具（file-upload / file-download）实现？
- [ ] 适配器的 Nop IoC 注册方式——`beans.xml` + `@Inject`，还是 `@Configuration`？
