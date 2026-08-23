# ai-core-api 实现代码检查报告

- 检查日期: 2026-08-20
- 模块路径: nop-ai/{nop-ai-core,nop-ai-api}
- 文件数: 约 326（src/main/java，不含 `_` 前缀生成文件与 target/；实际非生成源文件 nop-ai-core 158 + nop-ai-api 94 ≈ 252，其余为 `_gen` 生成物，按约定排除）
- 覆盖范围声明:
  - **全文精读**: ChatServiceImpl、LlmConfigHelper、AbstractLlmDialect、ILlmDialect、LlmDialectFactory、全部 5 个方言实现（OpenAi/Anthropic/Gemini/Ollama/Responses）、DefaultAiChatService（deprecated 旧链路）、DefaultChatLogger、ChatLogHelper、StandardRetryPolicy、ThresholdBreaker、LlmErrorClassifier、ConcurrencyRegistry、DefaultAiChatResponseCache、DefaultAiChatExchangePersister、FileSystemResponseProvider、MockChatService、MockAiChatService、AiCommand、AbstractAiChatSession、AiCoreConfigs、NopAiCoreException、ai-defaults.beans.xml；nop-ai-api 侧 IChatService、ChatRequest、ChatOptions、ChatResponse、ChatMessage、ChatToolCallMessage、ChatReasoningMessage、ChatStreamChunk、ChatStreamAccumulator、ErrorClassification、SecureDefault、NopAiException。
  - **grep 全量扫描**（两模块全部非生成源文件）: 空 catch、`new RuntimeException`、`printStackTrace`、HTTP 客户端创建/关闭、apiKey/token 字段与日志、静态可变状态、deprecated 引用。
  - **外围契约验证**（用于判定 nop-ai 侧行为，非审计对象本身）: nop-http-api 的 IHttpClient/IHttpResponse/HttpRequest/AbstractServerEventSubscription（SSE 多行 data 聚合与事件边界）、nop-http-client-jdk 的 ServerEventPublisher、nop-api-core 的 JSON.parse（严格模式，坏输入抛异常）、nop-commons 的 AESTextCipher 默认密钥。
  - **抽查未逐行**: response/*Parser、file/* 大部分、commons/splitter、prompt/node、api/beans 生成风格 DTO、api/crud 接口——经定向阅读与 grep 未见超出下述模式的新问题。
  - 测试代码不在范围内。SSE 底层行解析（AbstractServerEventSubscription）经验证无跨事件/多行 data 边界问题，且属 nop-http 模块，其问题不计入本报告。

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 1 |
| P1 | 3 |
| P2 | 9 |
| P3 | 7 |

## 发现列表

### [P0] Gemini/Ollama 流式工具调用的 arguments 完全丢失（默认调用路径即触发）

- **文件**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/dialect/GeminiDialect.java:479`、`nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/dialect/OllamaDialect.java:334`、`nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/ChatServiceImpl.java:113`
- **维度**: D1 正确性 / D8 契约一致性
- **证据**（GeminiDialect.parseStreamChunk，Ollama 同型）:
```java
if (functionCall instanceof Map) {
    Map<String, Object> fc = (Map<String, Object>) functionCall;
    chunk.setItemType(StreamItemType.tool_call);
    chunk.setItemIndex(order);
    chunk.setPhase(StreamItemPhase.ADDED);
    chunk.setDelta((String) fc.get("name"));
    // Gemini 的 args 是结构化对象，作为 tool_call 增量。
    // 单 chunk 边界：args 完整时无法与 name 同载，按 name 优先
    return chunk;
}
```
- **现状**: Gemini 与 Ollama 的流式 `functionCall`/`tool_calls` 在一个事件中**完整**给出 name + args（args 为结构化 Map）。`parseStreamChunk` 只把 `name` 放进 ADDED delta，`args` 无任何承载通道（已核对 `ChatStreamChunk` 全部字段：无 arguments 字段），且 Ollama 只取 `tool_calls.get(0)`。下游聚合器（`ChatServiceImpl.StreamAggregator.ToolCallAccumulator`：ADDED 阶段 delta 只赋给 name，argumentsBuilder 永远为空）产出的 `ChatToolCall.arguments` 恒为空 Map。注释自认"args 完整时无法与 name 同载"，但没有任何后续机制补发。而非流式路径 `parseResponse`（Gemini 不解析 functionCall——见下、Ollama line 257-269 解析）行为不一致。
- **风险**: `ChatServiceImpl.callAsync` 中 `boolean stream = true` 为缺省（options.stream 为 null 时走流式聚合），即**不显式 setStream(false) 的普通调用**在 Gemini/Ollama + 工具场景下，模型返回的工具调用参数全部丢失 → 工具以空参数执行或调用失败，属于默认路径上的数据错误。Gemini 非流式 parseResponse 也未解析 `functionCall` part（line 353-392 只处理 text/thought），同一 provider 的工具调用在流式与非流式两条路径上均不完整。
- **建议**: 扩展 `ChatStreamChunk`（或 ADDED delta 约定）承载完整 arguments；Gemini/Ollama 方言在 functionCall 首现时把 args JSON 序列化进 arguments 通道；补 Gemini 非流式 functionCall 解析；增加"Gemini 流式工具调用 → 聚合 arguments 与原始一致"的回归测试。
- **误报排除**: 已核对 ChatStreamChunk 无 arguments 字段、聚合器无补发逻辑、Gemini 官方流式 functionCall 一次完整下发（name+args 同 chunk），丢参路径完整成立。

---

> **处置（fix-ai-check 分支，2026-08-22）**: 缺陷成立，已修复。`ChatStreamChunk` 新增可选 `arguments` 通道（完整 args JSON 文本，`@JsonInclude(NON_NULL)` 序列化兼容）；Gemini/Ollama `parseStreamChunk` 将单事件完整下发的 functionCall args / tool_calls arguments（Map 或 JSON 字符串）写入该通道；`ChatServiceImpl.StreamAggregator` 与 nop-ai-api `ChatStreamAccumulator` 的 ToolCallAccumulator 以与 DELTA 片段相同的拼装语义消费该通道；Gemini/Ollama/`ILlmDialect` 默认 `buildStreamChunk` 回填 args（网关反向转换与 parse/build 闭环不丢参）；并补齐 Gemini 非流式 `parseResponse` 的 functionCall part 解析（条目建议的另一半）。测试：`nop-ai-core` `TestStreamAggregator#testAggregator_geminiStreamToolCallArgsPreserved` / `#testAggregator_ollamaStreamToolCallArgsPreserved`（修复前默认流式路径聚合出的 `ChatToolCall.arguments` 恒为空 Map，`location` 断言得 null）；另 `TestGeminiDialect#testParseResponseFunctionCall`、`TestGeminiDialect#testParseStreamChunkFunctionCallPreservesArguments`、`TestOllamaDialect#testParseStreamChunkToolCallsPreserveArguments`、nop-ai-api `TestChatStreamAccumulator#toolCallCompleteArgumentsAssembled` 等补齐 chunk 级/非流式/api 聚合器回归。红→绿验证：8 个新测试修复前全红，修复后 `./mvnw test -pl nop-ai/nop-ai-core -am` 全绿（379 tests / 0 failures / 3 skipped 为 @Disabled 环境性），gateway 模块 178/0 防回归通过。

### [P1] AnthropicDialect 将 assistant 角色映射为 "model"，多轮对话请求必被 Anthropic API 拒绝

- **文件**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/dialect/AnthropicDialect.java:626`
- **维度**: D1 正确性（Provider 适配）
- **证据**:
```java
@Override
public String getRole(ChatMessage message) {
    String role = getBaseRole(message);
    // Claude 使用 model 而不是 assistant
    return "assistant".equals(role) ? "model" : role;
}
```
- **现状**: Anthropic Messages API 的 role 取值只允许 `user`/`assistant`（`model` 是 Gemini 的角色名，疑似从 GeminiDialect 抄写时连注释一起带错）。`buildBody` → `convertMessage` 会把历史中所有 assistant 侧消息（assistant 文本、tool_call 消息经 `getBaseRole` 归并为 assistant）序列化为 `role:"model"` 发出。
- **风险**: 任何包含 assistant 历史的多轮对话/工具调用回放请求都会收到 Anthropic 400 (`unexpected role`)，Agent ReAct 循环第二轮起即失败。附带风险：Extended Thinking 回放时 `convertMessage` 生成的 thinking block 无 `signature` 字段（line 577-582），开启 thinking 的多轮回放同样可能被 Anthropic 拒绝。`parseRequestBody` 侧同时接受 `"assistant"`/`"model"`（line 86）说明作者误解了协议，不是笔误一处。
- **建议**: role 映射改回 `assistant`；thinking block 回放补 signature（或在请求方向丢弃 thinking block 仅回放 text/tool_use，Anthropic 对无 signature 的 thinking block 会报错）。
- **误报排除**: 已确认 `buildBody` 不做任何 role 纠正（无配置面可覆盖 role），Anthropic 官方协议 role 枚举为 {user, assistant}。

### [P1] 四方言 convertMessage 误用 getContent()：工具参数 JSON 与推理内容以文本形式混入请求体（伪影/重复）

- **文件**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/dialect/OpenAiDialect.java:339`、`.../AnthropicDialect.java:569`、`.../GeminiDialect.java:511`、`.../OllamaDialect.java:371`
- **维度**: D1 正确性 / D8 契约一致性
- **证据**（GeminiDialect.convertMessage，其余三方言同型）:
```java
String textContent = message.getContent();          // ChatToolCallMessage.getContent() = arguments JSON!
if (textContent != null && !textContent.isEmpty()) {
    Map<String, Object> textPart = new LinkedHashMap<>();
    textPart.put("text", textContent);
    parts.add(textPart);                            // 伪影 text part
}
...
if (message instanceof ChatToolCallMessage) { ... parts.add(toolPart); }  // functionCall part
```
- **现状**: `ChatToolCallMessage.getContent()` 返回 arguments 的 JSON 文本（api 模块 line 66-68），`ChatReasoningMessage.getContent()` 返回 summary。OpenAI/Anthropic/Gemini/Ollama 四个方言的 `convertMessage` 开头无条件把 `getContent()` 当普通文本内容写入 content 字段/text part/text block，然后再附加真正的 tool_call/thinking 载荷。GeminiDialect 自己的注释（line 139-141、228-231 `isToolPartArtifact`"convertMessage 伪影检测"）和 OllamaDialect.parseRequestBody 的注释（line 76"content 为 arguments 序列化伪影（convertMessage 副作用）"）都自证了该伪影存在——但只在网关**反向解析**侧做补偿，**正向发送**侧未修复。ChatReasoningMessage 在 Gemini/Anthropic/OpenAI/Ollama 中还会同时以"裸文本"+"thinking 载荷"双份发出（如 Gemini line 518-522 再加一个 `<thinking>` 包装 part）。
- **风险**: 多轮工具循环回放历史时，发给真实 provider 的请求里混入 arguments JSON 文本（模型会把它当成自己"说过的话"）或重复的推理文本，污染上下文、干扰模型行为、增大 token 消耗；对严格校验的 provider（Anthropic tool_use 与 text 混排语义）影响更直接。ResponsesDialect 是唯一按 type 正确分派的实现，可作修复范本。
- **建议**: 四方言 `convertMessage` 对 ChatToolCallMessage/ChatReasoningMessage（以及 ChatToolResponseMessage 的 content 与 functionResponse 双写）按消息类型分支处理，不落入通用 `getContent()` 路径；用"回放请求经 parseRequestBody roundtrip 后 messages 等价"测试锁定。
- **误报排除**: 已读 ChatToolCallMessage/ChatReasoningMessage 的 getContent 定义与四方言 convertMessage 全文，Gemini 的 `isToolPartArtifact` 注释直接承认伪影来源是 convertMessage。

### [P1] callStream 在下游订阅前就开始向上游拉数据，SubmissionPublisher 无订阅者时 submit 丢弃（丢 chunk 竞争）

- **文件**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/ChatServiceImpl.java:199`、`nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/mock/MockChatService.java:61`
- **维度**: D3 并发与线程安全
- **证据**:
```java
SubmissionPublisher<ChatStreamChunk> publisher = new SubmissionPublisher<>();

Flow.Publisher<IServerEventResponse> eventPublisher = httpClient.fetchServerEventFlow(httpRequest, cancelToken);
eventPublisher.subscribe(new Flow.Subscriber<>() {
    public void onSubscribe(Flow.Subscription subscription) {
        subscription.request(Long.MAX_VALUE);   // subscribe() 内同步触发，先于本方法 return
        ...
    }
    public void onNext(IServerEventResponse item) {
        ChatStreamChunk chunk = dialect.parseStreamChunk(item.getData());
        if (chunk != null) publisher.submit(chunk);  // 此时下游可能尚未订阅 callStream 返回的 publisher
    }
});
return publisher;
```
- **现状**: JDK `SubmissionPublisher.submit()` 在无订阅者时不缓冲不阻塞、直接丢弃。`eventPublisher.subscribe(...)` 会同步回调 `onSubscribe → request(MAX_VALUE)`（已核对 AbstractServerEventSubscription：request 内同步 startRequest 发起 HTTP），上游数据可能在 `callStream` 返回、调用方拿到 publisher 并 subscribe 之前就开始流入，`publisher.submit` 的早期 chunk 被静默丢弃。MockChatService.callStream 同模式且更明显（`awaitResponse(...).whenComplete(...)` 回调里 submit/close 完全不等待订阅）。另外：下游消费慢时 `submit` 在缓冲（默认 256）满后阻塞，阻塞的是 http 客户端 executor 线程（request(MAX_VALUE) 已绕过上游背压），并发慢消费者会占住 GlobalExecutors 全局 worker 线程。
- **风险**: 低延迟 provider（本地 Ollama、mock、网关缓存命中）+ 响应快的场景下开头 chunk 丢失，聚合结果残缺（如丢掉首个 text/reasoning/tool_call ADDED 声明导致 name 丢失）；慢消费者场景存在线程池占用隐患。窗口小但真实存在，且是结构性的（无订阅即丢，不是纯时序巧合）。
- **建议**: 延迟上游订阅——返回一个在 `subscribe()` 回调里才向 `eventPublisher.subscribe(...)` 的代理 publisher（或在 SubmissionPublisher 首个订阅注册后再 request 上游）；MockChatService 同样修复。
- **误报排除**: 已核对 JDK SubmissionPublisher 语义与 AbstractServerEventSubscription.request 的同步 startRequest 行为，竞争窗口成立。

### [P2] ChatServiceImpl.callAsync 对未设置 options 的请求直接 NPE

- **文件**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/ChatServiceImpl.java:114`
- **维度**: D1 正确性
- **证据**:
```java
public CompletionStage<ChatResponse> callAsync(ChatRequest request, ICancelToken cancelToken) {
    boolean stream = true;
    if (request.getOptions().getStream() != null)   // options == null 时 NPE
        stream = request.getOptions().getStream();
```
- **现状**: `ChatRequest` 契约允许 options 为 null（`new ChatRequest(messages)` 构造器、`makeOptions()` 惰性创建均支持），`LlmConfigHelper.getProvider` 等 follow-up 调用也都有 null 保护，唯独这一处直接解引用。
- **风险**: 只传 messages 不设 options 的合法请求在入口 NPE，而不是得到带错误码的 NopException。
- **建议**: `request.getOptions() != null ? request.getOptions().getStream() : null`，或入口 `request.makeOptions()`。
- **误报排除**: ChatRequest.getOptions 可返回 null（无 @NonNull 约束，字段默认 null），构造器 `ChatRequest(List)` 不设 options。

### [P2] 单条畸形 SSE data 行以异常终止整条流（JSON.parse 严格模式无容错）

- **文件**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/ChatServiceImpl.java:212`、`nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/dialect/OpenAiDialect.java:229`（Anthropic:420 / Gemini:427 / Ollama:313 / Responses:486 同型）
- **维度**: D1 SSE 解析边界 / D4 错误处理
- **证据**:
```java
public void onNext(IServerEventResponse item) {
    ChatStreamChunk chunk = dialect.parseStreamChunk(item.getData());  // 无 try/catch
    ...
}
// OpenAiDialect.parseStreamChunk:
Map<String, Object> dataMap = (Map<String, Object>) JSON.parse(data); // 严格模式，坏 JSON 抛 NopException
```
- **现状**: 已核对 `io.nop.api.core.json.JSON.parse` 为 strictMode，解析失败抛异常；异常从订阅者 onNext 抛回 http 客户端 executor 的解析循环，被 ServerEventPublisher 捕获后转 `onError` → `publisher.closeExceptionally`，整条流终止。任何一条非 JSON 的 data 行（部分 provider 的 keep-alive 提示、截断的心跳、兼容网关的警告文本）都会杀死整个流，已收到的增量全部作废。
- **风险**: 流式聚合（默认路径）对 provider 发送的杂散非 JSON 行零容错，一次杂线即前功尽弃。
- **建议**: `parseStreamChunk` 对单行解析失败 catch 后 LOG.warn 并返回 null（现有 null 分支已天然跳过），保持"坏块跳过、流继续"语义。
- **误报排除**: 已核对 JSON.parse 严格模式实现与 ServerEventPublisher 的异常传导路径（executor 块 catch → onError）。

### [P2] Anthropic 流式聚合丢失 promptTokens（message_start 的 usage 未提取）

- **文件**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/dialect/AnthropicDialect.java:426`
- **维度**: D1 正确性（token 计数）
- **证据**:
```java
case "message_start":
    Object message = dataMap.get("message");
    if (message instanceof Map) {
        Map<String, Object> messageMap = (Map<String, Object>) message;
        chunk.setModel((String) messageMap.get("model"));   // 只取 model，忽略 message.usage
    }
    break;
...
case "message_delta":                                      // 只在这里取 usage（input_tokens 此处恒为极小值或缺失）
    Object usage = dataMap.get("usage");
```
- **现状**: Anthropic SSE 的 `message_start` 事件携带 `message.usage.input_tokens`（完整 prompt token 数）；`message_delta` 只携带最终 `output_tokens`。当前实现只从 message_delta 取 usage，聚合出的 ChatUsage.promptTokens 为 null（或缺失）。
- **风险**: 流式路径（含默认聚合路径）的 token 计量/成本统计缺 promptTokens，上层计费、上下文预算判断失真。
- **建议**: message_start 分支提取 `message.usage`（input_tokens/cache 相关字段）并写入 chunk.usage（聚合器已支持 usage 字段非首个覆盖语义——注意 ChatStreamAccumulator 只在非 null 时覆盖，OpenAI 侧首 chunk usage 与末 chunk 合并需确认）。
- **误报排除**: Anthropic 官方流式协议 usage 分布（message_start 含 input_tokens，message_delta 只含 output_tokens）与代码只取 message_delta 的事实已双重核对。

### [P2] Anthropic 流式 error 事件被伪装成正文文本注入，流不报错

- **文件**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/dialect/AnthropicDialect.java:534`
- **维度**: D4 错误处理
- **证据**:
```java
case "error":
    // 错误消息：作为 text item 的错误文本（快速暴露，不静默）
    ...
    chunk.setItemType(StreamItemType.text);
    chunk.setPhase(StreamItemPhase.DELTA);
    chunk.setDelta("[ERROR] " + errorMsg);
    break;
```
- **现状**: SSE `error` 事件（provider 中途报错，如过载）被转换为普通 text delta。聚合后的 ChatResponse `isSuccess()==true`、finishReason 可能为 null，错误信息以 `"[ERROR] ..."` 混入 assistant 正文。注释声称"快速暴露"，实际既未暴露为错误也未中断流，下游把错误文本当模型输出。
- **风险**: Agent 循环把 provider 错误文本当成模型回答继续处理，错误被静默吞掉（与设计的 errorClassification 通路相悖）。
- **建议**: error 事件应抛异常或产生携带 errorClassification 的终止信号（如 chunk 标记 DONE + 错误注入 ChatResponse.error）。
- **误报排除**: 已核对聚合器对 text delta 的处理（追加进 content，无错误语义），路径成立。

### [P2] Responses 流式 response.failed 被当作正常完成，错误信息丢弃

- **文件**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/dialect/ResponsesDialect.java:552`
- **维度**: D4 错误处理
- **证据**:
```java
case EVENT_RESPONSE_COMPLETED:
case EVENT_RESPONSE_FAILED:
case EVENT_RESPONSE_INCOMPLETE: {
    Object respObj = eventMap.get("response");
    if (respObj instanceof Map) {
        ...
        chunk.setFinishReason(normalizeFinishReason((String) respMap.get("status")));
        // respMap.get("error") 从未读取
    }
    chunk.setPhase(StreamItemPhase.DONE);
```
- **现状**: `response.failed` 事件的 `response.error`（code/message）被忽略；`normalizeFinishReason("failed")` 不在映射表内原样透传 `"failed"`，chunk 以 DONE 正常收尾，聚合结果是 isSuccess==true 的"成功"响应（可能无内容）。
- **风险**: 上游失败（如内容策略拒绝）被当成正常空响应，上层无法感知失败原因，重试/降级决策全部失效。
- **建议**: failed 分支读取 `response.error` 并产出错误 ChatResponse 或抛携带分类的异常。
- **误报排除**: 已核对 normalizeFinishReason 映射表无 "failed" 项、聚合器无 error 通道。

### [P2] OpenAI 流式单事件多 tool_call 增量只取首个；content 与 tool_calls 同发时 tool_calls 被丢弃

- **文件**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/dialect/OpenAiDialect.java:251`
- **维度**: D1 正确性
- **证据**:
```java
if (!hasContent) {   // line 264：同一 delta 里 content 与 tool_calls 互斥假设
    Object toolCallsObj = deltaMap.get("tool_calls");
    ...
}
...
private void parseToolCallDelta(ChatStreamChunk chunk, List<?> toolCalls) {
    for (Object tc : toolCalls) {
        ...
        return; // 单 chunk 返回，取首个 entry   ← 同一 SSE 事件内第二个 index 的增量丢弃
    }
}
```
- **现状**: 两个限制叠加：(1) 同一事件中 `tool_calls` 数组有多个 entry（部分兼容网关/代理会合并并行调用的增量）时只取第一个，其余 index 的 arguments 片段永久丢失（聚合器按 index 拼接，缺片即 JSON 损坏——进而触发 P2 末条"空 arguments"静默降级）；(2) content 非空时同事件的 tool_calls/reasoning 被忽略（注释承认假设 OpenAI 互斥，兼容实现不一定遵守）。
- **风险**: 并行工具调用在部分 provider 网关下参数损坏或调用丢失。
- **建议**: parseStreamChunk 返回后允许一次提交多个 chunk（ onNext 内循环解析），或 dialect 提供返回 List<ChatStreamChunk> 的内部方法。
- **误报排除**: 代码注释自认"单 chunk 边界/取首个"，丢增量事实明确；是否所有 OpenAI 兼容端都单 entry 不确定，故定 P2 而非 P1。

### [P2] ChatOptions 契约字段在主链路被静默忽略（requestTimeout / frequencyPenalty / presencePenalty / toolChoice），forceTool 产出非法格式

- **文件**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/ChatServiceImpl.java:269`、`nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/dialect/OpenAiDialect.java:152`、`nop-ai/nop-ai-api/src/main/java/io/nop/ai/api/chat/ChatOptions.java:331`
- **维度**: D8 API/契约一致性
- **证据**:
```java
// ChatServiceImpl.buildHttpRequest —— options.requestTimeout 未消费：
httpRequest.setTimeout(CFG_AI_SERVICE_READ_TIMEOUT.get());

// OpenAiDialect.buildBody —— 只放行 temperature/max_tokens/top_p/top_k/stop：
addOptionIfNotNull(body, "temperature", options.getTemperature());
...   // frequency_penalty / presence_penalty / tool_choice 无一写入

// ChatOptions.forceTool 产出私有前缀格式，未在任何 dialect 转换：
public void forceTool(String toolName) { this.toolChoice = "tool:" + toolName; }
```
- **现状**: (1) `ChatOptions.requestTimeout` 在新链路（ChatServiceImpl）完全被忽略，只有 deprecated 的 DefaultAiChatService 消费它——旧链路迁移后 per-request 超时失效；(2) `frequencyPenalty`/`presencePenalty` 字段无任何方言消费；(3) `toolChoice` 只有 ResponsesDialect 写入 body 且原样透传——OpenAI/Anthropic/Gemini/Ollama 均忽略，`disableTools()`/`requireTool()`/`forceTool()` 便捷方法在这些 provider 上全部无效，且 `forceTool` 的 `"tool:xxx"` 私有格式即使透传给 Responses API 也是非法值（合法形态是结构化 JSON）。附带：`CFG_AI_SERVICE_CONNECT_TIMEOUT` 配置项定义后从未被使用（连接超时不可配置）。
- **风险**: 用户按公开契约（nop-ai-api ChatOptions/ChatRequest 便捷方法）设置的选项被静默丢弃，行为与文档/字段语义不符；调试成本高。
- **建议**: 各方言 buildBody 补齐 frequency_penalty/presence_penalty/tool_choice 映射（forceTool 的私有格式在方言层转换为 provider 结构）；ChatServiceImpl 采纳 options.requestTimeout（非空时覆盖全局 read timeout）；删除或接通 connect-timeout 配置。
- **误报排除**: 已 grep 全部方言 buildBody 与 ChatServiceImpl 全文确认上述字段零消费；deprecated 旧实现对照（DefaultAiChatService.initHeaders/setOptions）确认旧链路曾支持。

### [P2] llm.xml `<request>` 请求路径配置（LlmRequestModel）在新链路整体失效

- **文件**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/model/LlmRequestModel.java`（消费方仅 `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/DefaultAiChatService.java:466`）
- **维度**: D8 契约漂移
- **证据**:
```java
// DefaultAiChatService.setOptions（deprecated 链路）按配置路径写参数：
setIfNotNull(body, requestModel.getMaxTokensPath(), maxTokens);
setIfNotNull(body, requestModel.getTemperaturePath(), options.getTemperature());
...
// 新链路五方言 buildBody 全部硬编码路径（"max_tokens"/"temperature"/...），grep 确认 dialect/ 包零引用 LlmRequestModel
```
- **现状**: llm.xml 的 `<request>` 元素（temperaturePath/maxTokensPath/topPPath/stopPath/seedPath/contextLengthPath 等）以及 options 的 seed/contextLength 只被 @Deprecated 的 DefaultAiChatService 消费；替代者 ChatServiceImpl + dialect 的 buildBody 全部硬编码字段路径，配置面静默失效。响应侧（`<response>` contentPath 等）仍被 dialect 消费，请求/响应两侧配置能力不对称。
- **风险**: 运维在 llm.xml 配置请求路径定制（如 Azure 风格字段、温度字段改写）后不生效且无告警；provider 特殊参数（seed 等）无法下发。
- **建议**: 要么在 dialect buildBody 中接通 config.getRequest() 路径覆盖，要么在配置加载时对未被新链路消费的元素告警/移除文档。
- **误报排除**: grep 确认 LlmRequestModel 引用仅存在于模型类自身与 DefaultAiChatService。

### [P2] AiCommand 缓存加载失败时丢弃异常对象（无堆栈、无 error 级别）

- **文件**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/command/AiCommand.java:307`
- **维度**: D4 错误处理
- **证据**:
```java
try {
    AiChatExchange exchange = chatCache.loadCachedResponse(prompt, options);
    ...
} catch (Exception e) {
    LOG.info("nop.ai.load-cache-fail:promptName={},requestHash={}", prompt.getName(), prompt.getRequestHash());
    // e 未记录
}
```
- **现状**: 缓存加载异常被吞且日志不含异常对象（无堆栈）、级别 info。缓存文件损坏、反序列化失败、IO 错误不可区分，排障时无线索。
- **风险**: 静默降级为重新调用 LLM（成本上升）且无诊断信息；违反平台"不静默吞异常"的错误处理约定精神。
- **建议**: `LOG.info("...:promptName={},requestHash={}", ..., e)` 至少保留堆栈，或区分 IO/解析错误。
- **误报排除**: SLF4J 参数列表确认未传 e（传入也不会打印堆栈，因为占位符数量与参数不匹配 e）。

### [P2] 流式工具参数 JSON 解析失败时 core 聚合器静默置空（与 api 模块聚合器行为漂移）

- **文件**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/ChatServiceImpl.java:596`、`nop-ai/nop-ai-api/src/main/java/io/nop/ai/api/chat/stream/ChatStreamAccumulator.java:226`
- **维度**: D4 / D8 双实现漂移
- **证据**:
```java
// ChatServiceImpl.StreamAggregator（core）：
try {
    Map<String, Object> args = (Map<String, Object>) JSON.parse(argsStr);
    toolCall.setArguments(args);
} catch (Exception e) {
    toolCall.setArguments(new LinkedHashMap<>());   // 原始 argsStr 丢弃
}

// api 模块 ChatStreamAccumulator（对照）：
} catch (Exception e) {
    toolCall.setArguments(Collections.singletonMap("_raw", argsStr));  // 保留原始文本
}
```
- **现状**: 同一语义（流式工具参数聚合）存在两份实现且失败行为不一致：core 侧丢原始数据置空 Map（空 Map 与"模型确实传了空参数"不可区分，且与 P0/Gemini 丢参叠加后完全无法诊断），api 侧保留 `_raw`。core 侧还多一个差异：argsStr 为空时 core 强制设空 Map，api 侧保持 null。
- **风险**: 参数解析失败被伪装成合法空参数，工具静默误执行；两份聚合器行为漂移导致流式/直接订阅两条路径结果不一致。
- **建议**: core 侧对齐 api 侧保留 `_raw`（或统一收敛为单一实现）；解析失败至少 LOG.warn。
- **误报排除**: 两段代码已并排核对。

### [P3] DefaultChatLogger.logRequest 对空消息请求 NPE

- **文件**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/DefaultChatLogger.java:43`
- **维度**: D1
- **证据**:
```java
ChatMessage message = request.getLastMessage();   // messages 为空时返回 null
String content = message.getContent();            // NPE
```
- **现状**: `ChatRequest.getLastMessage()` 在 messages 为 null/空时返回 null；logMessage 开启时空消息请求在日志处 NPE。
- **风险**: 低（空消息请求本身异常），但把本应干净的参数校验错误变成 NPE。
- **建议**: 判空跳过日志。
- **误报排除**: ChatRequest.getLastMessage 实现已核对（返回 null）。

### [P3] secret 文件读取结果缓存空串后不再刷新；secret 写回全局配置树

- **文件**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/LlmConfigHelper.java:139`
- **维度**: D5 / D6
- **证据**:
```java
apiKey = secretCache.computeIfAbsent(provider, k -> {
    File secretFile = new File(secretDir, provider + ".txt");
    if (secretFile.exists()) { ... return secret; }
    return "";    // 不存在 → 缓存空串
});
...
AppConfig.getConfigProvider().assignConfigValue(apiKeyName, secret);  // secret 写回全局配置
```
- **现状**: (1) secret 文件缺失时缓存 `""`，此后文件创建也不重读（MapCache 无 TTL），需手动 `clearSecretCache()`/`reset()`；(2) 读到的明文 key 经 `assignConfigValue` 写入全局配置树，任何遍历/导出配置的路径（诊断端点、配置 dump）都可能带出明文 key。
- **风险**: 运维给运行中实例补 key 不生效（需重启或调内部 API）；全局配置树的明文扩大泄漏面（DefaultChatLogger 的脱敏只覆盖日志路径）。
- **建议**: 空结果不缓存（或短 TTL）；优先仅返回值而不回写全局配置。
- **误报排除**: MapCache.computeIfAbsent 会缓存空串值（非 null-sentinel 跳过）已核对；存在 clearSecretCache 机制但不自动触发。

### [P3] rateLimiter 按 provider 首建后不感知 llm.xml rateLimit 变更

- **文件**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/ChatServiceImpl.java:349`（同型 `DefaultAiChatService.java:111`）
- **维度**: D6 / 配置漂移
- **证据**:
```java
IRateLimiter rateLimiter = rateLimiters.computeIfAbsent(provider, k -> {
    LOG.debug("nop.ai.create-rate-limiter: provider={}, rate={}", provider, config.getRateLimit());
    return createRateLimiter(config.getRateLimit());
});
```
- **现状**: limiter 一旦创建，llm.xml 修改 rateLimit（或热更新组件模型）后仍用旧速率，直至重启；DefaultAiChatService 还有二次问题：rateLimit 改为 null（取消限流）后已存在的 limiter 继续生效。
- **风险**: 限流配置调整不生效，方向不可控（可能过严或过松）。
- **建议**: 以 (provider, rateLimit) 为缓存键或在配置版本变化时重建。
- **误报排除**: computeIfAbsent 语义与 map 生命周期已核对（实例级、无失效）。

### [P3] DefaultAiChatService.getApiKey 缺 secretDir 判空（对比 LlmConfigHelper 有保护）

- **文件**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/DefaultAiChatService.java:281`
- **维度**: D1 / D7（deprecated 类）
- **证据**:
```java
if (StringHelper.isEmpty(apiKey)) {
    return secretCache.computeIfAbsent(llmName, k -> {
        File secretFile = new File(secretDir, llmName + ".txt");   // secretDir==null → NPE
```
- **现状**: IoC 装配时 @InjectValue 缺省 "/nop/ai/secret" 保证非空，但该类被手工 new（或测试）时 secretDir 为 null 直接 NPE。同文件逻辑在 LlmConfigHelper 中有 `secretDir != null` 保护，两份实现不一致。
- **风险**: 低（deprecated 类 + 非常规构造路径），属重复实现间的防御不一致。
- **建议**: 对齐 LlmConfigHelper 的判空；长期收敛重复的 secret 解析实现。
- **误报排除**: 两处代码已并排核对。

### [P3] mock 路径使用 bare IllegalStateException（违反错误处理两档策略）

- **文件**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/mock/FileSystemResponseProvider.java:87,208`、`nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/mock/MockChatService.java:51`
- **维度**: D7 平台规范
- **证据**:
```java
throw new IllegalStateException("Mock response timeout after " + timeoutHours + " hours");
throw new IllegalStateException("Failed to create mock directory", e);
throw new IllegalStateException("ResponseProvider is not set");
```
- **现状**: 模块内已有 NopAiCoreException/NopException + ErrorCode 体系（StandardRetryPolicy 等均遵守），mock 包三处用裸 IllegalStateException。
- **风险**: 违反"禁止 bare RuntimeException 家族"约定；错误消息无法经 ErrorCode 体系归一。
- **建议**: 换用 NopAiCoreException（或 NopException + ERR_AI_AGENT_INVALID_ARG）。
- **误报排除**: 全模块 grep 无其他 bare RuntimeException/printStackTrace，仅此三处（外加 AiCommand NopException.adapt 等合规用法）。

### [P3] DefaultAiChatExchangePersister 开启加密时默认回落平台硬编码密钥

- **文件**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/persist/DefaultAiChatExchangePersister.java:35`
- **维度**: D5
- **证据**:
```java
private ITextCipher textCipher = new AESTextCipher();
```
- **现状**: 未注入 textCipher 时 AESTextCipher 使用 `CFG_CRYPT_DEFAULT_ENC_KEY`，该配置为空时回落到 nop-commons 内置硬编码默认密钥（已核对 AESTextCipher：`hashWithDefault(CFG_CRYPT_DEFAULT_ENC_KEY.get(), "*(<K:00a9mf8ia7Nn3^y34%FER{3/")`）。加密开关（`nop.ai.persist.exchange-encrypt`）默认 false，配置说明已提示应配 enc-key 或注入 cipher。
- **风险**: 运维开启 encrypt 但未配密钥时，交换持久化（含对话内容）以人人可知的平台默认密钥加密，形同明文。
- **建议**: encryptEnabled=true 且未配置 enc-key/未注入 cipher 时 fail-fast。
- **误报排除**: AESTextCipher 默认密钥回落链已核对；默认关闭 + 已文档化故定 P3。

### [P3] AbstractLlmDialect.buildFullContentWithThinking 默认 think 标记为无意义字面量（疑笔误），新旧实现默认值不一致

- **文件**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/dialect/AbstractLlmDialect.java:464`
- **维度**: D1 / D8
- **证据**:
```java
String startMarker = modelConfig != null && modelConfig.getThinkStartMarker() != null
        ? modelConfig.getThinkStartMarker() : "ery\n";
String endMarker = modelConfig != null && modelConfig.getThinkEndMarker() != null
        ? modelConfig.getThinkEndMarker() : "module-info>\n";
// 对照 deprecated DefaultAiChatService.checkThink 的默认值："<think>\n" / "\n</think>\n"
```
- **现状**: 新基类的默认思考标记是 `"ery\n"`/`"module-info>\n"`（不像任何标记语法，疑为裁剪事故残留），而旧实现（以及消费侧 checkThink 的 content.startsWith(startMarker) 剥离逻辑）用 `<think>` 标记。该方法当前无生产调用者（grep 仅定义处），属公开 protected API 的休眠缺陷。
- **风险**: 一旦被调用，产生的"思考包裹"与任何消费方的剥离标记不匹配；新旧默认值不一致加剧漂移。
- **建议**: 修正为 `<think>\n`/`\n</think>\n` 或删除方法；接通时补 roundtrip 测试。
- **误报排除**: grep 确认无调用者（因此 P3 而非 P1）；默认值对照已核对。

### [P3] ThresholdBreaker HALF_OPEN 探针丢失时该 model-key 永久拒绝

- **文件**: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/reliability/ThresholdBreaker.java:128`
- **维度**: D3 / D4
- **证据**:
```java
case HALF_OPEN:
    if (!entry.probeInFlight) {
        entry.probeInFlight = true;
        return true;          // 探针名额给出后，若调用方异常终止未回调 record*，probeInFlight 永远 true
    }
    return false;
```
- **现状**: 探针调用者拿到名额后如果因编排缺陷（取消、未走 finally）从不调用 recordSuccess/recordFailure，该 key 停在 HALF_OPEN 且 probeInFlight=true，后续所有 allowCall 永久 false（比 OPEN 更糟——OPEN 至少有 cooldown 探针）。类内无超时自愈。
- **风险**: 依赖调用方严格配对；一旦失配形成永久熔断。
- **建议**: 记录探针发放时间戳，超过一个 cooldown 周期未回报则重置 probeInFlight。
- **误报排除**: 状态机全文已核对，无超时重置路径；触发需调用方失误，故 P3。

## 附注（非缺陷的边界说明）

- **HTTP 响应体资源管理**：非流式路径经 `IHttpClient.fetchAsync`，`IHttpResponse` 为已物化模型（byte[] 驻留），连接释放由客户端实现负责，`ChatServiceImpl` 无需也未做显式关闭——该设计下未见 nop-ai 侧泄漏。流式路径的 InputStream 生命周期由 nop-http 的 ServerEventPublisher 管理（属 nop-http-client-jdk/apache 模块，超出本审计单元；观察到的"onError 路径 reader 未显式 close"位于该外部模块，不计入本报告）。
- **SSRF**：`ChatOptions.accountBaseUrl/accountKey` 均标注 `@JsonIgnore`，经 Jackson 反序列化无法从外部注入，baseUrl 主路径来自 llm.xml/AppConfig 配置——nop-ai 侧未发现用户可控 URL 直连面。
- **Nop IoC 约定（D7）**：注入全部为 setter 注入（@Inject/@InjectValue），无 private 字段注入；bean 均在 `_vfs/nop/ai/beans/ai-defaults.beans.xml` 显式定义。唯一注记：`MockChatService` 带 @Inject setter 但无 bean 定义、无生产引用，只能手工构造（构造后未 set provider 会抛 IllegalStateException，见 P3 bare RuntimeException 条目）。
- **密钥日志（D5）**：DefaultChatLogger 有三级凭据脱敏正则（api-key/Authorization/sk- 前缀）且默认开启，apiKey 走 header 不落 URL（Gemini 已改为 x-api-key header）；`ChatStreamChunk.toString`/各 ChatResponse 字段均不含密钥。未见密钥泄漏入日志的直接路径。
