# Nop 平台前缀缓存设计构想

> Status: analysis
> Date: 2026-06-10
> Scope: nop-ai-api / nop-ai-core — 前缀缓存如何在 Nop 体系下落地
> 依据: `agent-survey/2026-06-10-reasonix-prefix-cache-immutability.md` 源码级调研

---

## 1. 核心原则

前缀缓存只看一件事：**应用每次发送给模型的字节前缀是否一样**。

模型返回什么、怎么解析、怎么存储，都不影响缓存。应用层全权控制发送内容，只需保证：

1. **system prompt 不变**
2. **tools 不变**
3. **历史消息序列化是确定性的** — 同一个 `ChatToolCall{id, name, arguments}` 每次序列化为相同 JSON

第 3 点当前已满足：所有 `ILlmDialect` 实现使用 `LinkedHashMap` 保证键序，`JSON.stringify()` 保证格式一致。

---

## 2. 不需要做的事

| 方案 | 结论 | 理由 |
|------|------|------|
| ChatMessage 加 `frozen` 标记 | ❌ | 缓存是字节序列匹配，不是消息属性 |
| ChatRequest 加 `fixedMessages` 字段 | ❌ | 拆两个列表增加负担，API 层不需要感知前缀 |
| 新增 `PrefixState` 类 | ❌ | 重复表达 ChatRequest 里已有的东西（system prompt、tools），多余 |
| 新增 `PrefixFingerprint` 类 | ❌ | 过度设计，`SHA-256(system + tools JSON)` 一个静态方法即可 |

---

## 3. 需要做的事

### 3.1 ChatUsage 补 cacheMissTokens

```java
// ChatUsage 新增
private Integer cacheMissTokens;

@JsonIgnore
public double getCacheHitRate() {
    if (promptTokens == null || promptTokens == 0) return 0;
    return (cacheHitTokens != null ? cacheHitTokens : 0) / (double) promptTokens;
}
```

DeepSeek API 直接返回 `prompt_cache_miss_tokens`。当前通过 `promptTokens - cacheHitTokens` 推算不够准确（Anthropic 的 cache_creation_tokens 会导致差值不为 miss）。

### 3.2 ILlmDialect.convertMessage() 移除 isLast 参数

当前 `convertMessage(msg, modelConfig, isLast, options)` 中 `isLast` 参数会在最后一条消息上注入 thinking prompt。这意味着**同一条消息在不同位置可能序列化为不同字节**。

修正：移除 `isLast` 参数，thinking prompt 注入由调用方在构建 ChatRequest 时完成。

```java
// 修正前
Map<String, Object> convertMessage(ChatMessage message, LlmModelModel modelConfig,
                                    boolean isLast, ChatOptions options);

// 修正后
Map<String, Object> convertMessage(ChatMessage message, LlmModelModel modelConfig,
                                    ChatOptions options);
```

**契约**：相同的 `ChatMessage` + 相同的 `ChatOptions` → 永远序列化为相同字节。

### 3.3 引擎层：prefixLength + prefixHash

不需要新的类。`AgentExecutionContext` 里加两个字段：

```java
public class AgentExecutionContext {
    AgentModel agentModel;
    List<ChatMessage> messages;       // 完整消息列表
    ChatOptions options;              // 含 tools

    // 前缀缓存支持
    int prefixLength;                 // messages[0..prefixLength) 为冻结前缀
    String prefixHash;                // SHA-256(system + tools JSON)，跨 turn 校验
}
```

ReAct 循环中的约定：

```java
// 会话初始化时
String systemPrompt = buildSystemPrompt(agentModel);
List<ChatToolDefinition> tools = buildToolSpecs(agentModel);
context.getMessages().add(new ChatSystemMessage(systemPrompt));
// ... 添加 fewShots
context.setPrefixLength(context.getMessages().size());
context.setPrefixHash(sha256(systemPrompt + JSON.stringify(tools)));

// 每 turn 组装 ChatRequest
ChatRequest request = new ChatRequest(context.getMessages(), context.getOptions());
// 引擎层自律：不修改 messages[0..prefixLength)，只在后面追加

// turn 结束后校验
ChatUsage usage = response.getUsage();
if (usage != null && usage.getCacheHitTokens() != null && usage.getCacheHitTokens() == 0) {
    // cache miss — 对比 prefixHash 定位原因
}
```

### 3.4 Anthropic cache_control（可选增强）

Anthropic 的 prompt caching 需要显式 `cache_control` 标记。在 `ChatMessage` 上加一个透传字段：

```java
// ChatMessage 新增
private Map<String, Object> providerHints;  // Anthropic: {"cache_control": {"type": "ephemeral"}}
```

`AnthropicDialect` 在序列化时读取 `providerHints` 并注入对应字段。引擎层在构建 system 消息时设置 `providerHints`。Dialect 不需要理解"前缀"语义。

---

## 4. 工作项

| # | 工作项 | 层级 | 依赖 |
|---|--------|------|------|
| 1 | `ChatUsage` 加 `cacheMissTokens` + `getCacheHitRate()` | nop-ai-api | 无 |
| 2 | `ChatMessage` 加 `providerHints` | nop-ai-api | 无 |
| 3 | `ILlmDialect.convertMessage()` 移除 `isLast` 参数 | nop-ai-core | 无 |
| 4 | `AnthropicDialect` 从 `providerHints` 读取并注入 `cache_control` | nop-ai-core | #2 |
| 5 | ReAct 循环：会话初始化时记录 `prefixLength` + `prefixHash`，每 turn 校验 | nop-ai-agent | 无 |

---

## 5. 结论

前缀缓存不需要新的数据结构或抽象层。应用层保证每次发给模型的消息前缀一样就行——这在引擎层通过简单约定（`prefixLength` + `prefixHash`）实现。唯一需要修改代码的是确保 `ILlmDialect` 序列化的确定性（移除 `isLast` 参数）。
