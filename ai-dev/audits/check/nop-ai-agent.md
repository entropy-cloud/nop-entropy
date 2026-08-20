# nop-ai-agent 实现代码检查报告

- 检查日期: 2026-08-20
- 模块路径: nop-ai/nop-ai-agent
- 文件数: 实测 src/main/java 共 536 个 Java 文件（其中 `model/_gen`、`plan/model/_gen` 等生成代码 38 个，手写 498 个；另有 resources 25 个，任务描述的 963 为含测试等全模块口径，本报告仅审计 src/main/java）
- 覆盖范围声明:
  - **深读**（逐行）: ReAct 执行循环全链路（ReActAgentExecutor、LlmCallCoordinator、AgentToolDispatcher、AgentPromptAssembly、AgentToolPlanResolver、AgentExecutionContext、AgentExecutionResult、AgentHookInvoker、AgentLoopGuard、AgentCompactionCoordinator、SingleTurnExecutor、DefaultAgentEventPublisher）、引擎与会话生命周期（DefaultAgentEngine、AgentSessionLifecycle、AgentSessionSupport、AgentExecutorResolver、AgentCallDelegate、ReActAgentExecutorBuilder）、会话存储（InMemorySessionStore、DBSessionStore、FileBackedSessionStore）、记忆（InMemoryAiMemoryStore、AdapterBackedAiMemoryStore、InMemoryMemoryStoreProvider）、上下文压缩（Layer2TurnPruningStrategy、CompactConfig、InMemorySpillStore）、消息（LocalAgentMessenger、DeferredAckMailbox）、工具执行器（CallAgentExecutor、WriteMemoryExecutor）、Actor（AgentActor）、安全抽读（DefaultPathAccessChecker、NoOpSandboxBackend、DockerSandboxBackend、DefaultDenialLedger、Slf4jAuditLogger、AgentSecurityConsultation 的 deny 响应注入点）、guardrail（PromptInjectionGuardrail）、跨模块核对（nop-ai-api ChatResponse、nop-ai-core ChatServiceImpl 流式汇聚/ModelKeys/OpenAiDialect max_tokens 语义）、beans.xml 装配
  - **扫描**（grep + 抽读验证）: 全部 536 文件的空 catch / `new RuntimeException` / `printStackTrace` / `catch (Throwable)` / 可变 static 集合 / `synchronized` / `@Inject private` / `@Value` / `@Component` / `ProcessBuilder`·`Runtime.exec` / `apiKey`·`password`·`secret` / `sessionStore.remove`·`evict`
  - **未覆盖**: team/（28 文件，仅抽读 MemberAgentTaskStep、TaskDispatchCoordinator）、plan/（44 文件）、reliability/（30 文件，仅经执行循环调用面理解）、security/ 其余约 69 文件（fencing、approval 细节等）、guardrail/test 与 rule 加载器、skill/（14 文件）、compact 其余策略（Layer3FullSummary、ReferenceCompaction、MicroCompression、PipelineCompactor、ToolResultTruncator）、session 的 SessionFileReader/Writer 与消息表、middleware/、hook/、conflict/、contribution/、repair/、usage/、quota/、budget/、fencing/、router/、talent/、recipe/ 逐行细节
  - 结论性印象: 该模块工程质量显著高于平均水平（注释中带设计裁定与防线编号、fail-loud 文化、超时/重试/熔断/配对不变式均有显式处理、路径与审计有防注入设计）；grep 层面零违例（无空 catch、无 bare RuntimeException、无 printStackTrace、无 Spring 注解误用、无私有 @Inject）。以下发现集中在：一个新特性（BAIL）遗漏既有不变式、一个配置语义错位（maxTokens）、以及会话生命周期结束后内存结构不清理这一族资源问题

## 发现统计

| 严重程度 | 数量 |
|---------|------|
| P0 | 0 |
| P1 | 4 |
| P2 | 5 |
| P3 | 2 |

## 发现列表

### [P1] POST_REASONING BAIL 丢弃响应时未移除已入上下文的 assistant/tool_call 消息，破坏 tool_call_id 配对不变式

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/ReActAgentExecutor.java:779`（bail 分支），对照 `:692-696`（消息入上下文点）
- **维度**: D1、D8
- **证据**:
```java
// 第 692-696 行：响应消息已先加入 ctx
ctx.addMessage(assistantMsg);
appendToolCallMessages(ctx, llmResult.response);
...
// 第 779-792 行：BAIL 分支声称"discard this round's response"，但不移除任何消息
if (postReasoningResult.isBail()) {
    String bailReason = ((HookResult.BailResult) postReasoningResult).getReason();
    bailCount++;
    ...
    ctx.setCurrentIteration(ctx.getCurrentIteration() + 1);
    continue;   // assistantMsg 与全部 ChatToolCallMessage 留在 ctx 中
}
```
- **现状**: LLM 响应中的 assistant 文本与 ChatToolCallMessage 在 POST_REASONING 钩子触发之前就已 append 到 `ctx.getMessages()`。W5-3 BAIL 路径的语义是"丢弃本轮响应并重新提问"，但实现只跳过了 guardrail 与工具派发，已入上下文的消息（含工具调用请求）未回滚。若被 bail 的响应携带 tool_calls，下一轮 `new ChatRequest(ctx.getMessages())` 将包含没有对应 tool response 的 assistant tool_call。
- **风险**: 对强制校验 tool_call/tool_response 配对的 provider（OpenAI 兼容系），下一轮 LLM 调用直接 HTTP 400 → 被归类为 NON_TRANSIENT → 整个执行失败。这正是本模块他处反复防御的不变式（AR-11 注释、Layer2 分组裁剪、guardrail-block 与安全 deny 路径都专门补配对响应），唯独 BAIL 路径遗漏。触发条件：配置了返回 BailResult 的 POST_REASONING 中间件（BAIL 是该特性的设计用法）且被 bail 的响应含 tool_calls——即该特性一经实际使用即可复现。
- **建议**: bail 分支在 `continue` 前移除本轮 append 的 assistant/tool_call 消息；或为每个 toolCall 补 `ChatToolResponseMessage.error(callId, "response discarded by POST_REASONING bail")`（与 guardrail-block 路径同构）。
- **误报排除**: 已核对 `AgentPromptAssembly.checkOutputGuardrail`（:310-314）对 BLOCK 路径按 callId 补 error 响应、`AgentSecurityConsultation`（:134-262）7 个 deny 路径均补 `ChatToolResponseMessage.error`，证明"丢弃响应必须保配对"是本模块自己的既定实现约定，BAIL 路径为遗漏而非设计。

### [P1] 上下文窗口上限误用 chatOptions.maxTokens（输出 token 上限），声明 maxTokens 的 agent 会被过早 forced_stopped

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/AgentCompactionCoordinator.java:65`（主），对照 `AgentLoopGuard.java:89-99`、`AgentExecutionContext.java:109-112`、`AgentPromptAssembly.java:251-252`
- **维度**: D8、D1
- **证据**:
```java
// AgentCompactionCoordinator
public long resolveMaxContextTokens(AgentExecutionContext ctx) {
    ChatOptions chatOpts = ctx.getChatOptions();
    if (chatOpts != null && chatOpts.getMaxTokens() != null) {
        return chatOpts.getMaxTokens();     // ← 输出上限被当作上下文窗口
    }
    return ReActAgentExecutor.DEFAULT_MAX_CONTEXT_TOKENS; // 128000
}
// AgentLoopGuard.shouldForceStop
long estimate = tokenEstimator.estimateTokens(ctx.getMessages());
if (estimate > maxContextTokens * forcedStopPercent) { // 0.9
    return true;   // forced_stopped
}
```
- **现状**: `ctx.chatOptions` 来自 agent 模型声明的 `chatOptions.maxTokens`（AgentExecutionContext.create:109-112），而同一个值同时经 `buildChatOptions` 填入请求 options、由 OpenAiDialect 以 `max_tokens`（补全输出上限）发给 provider（nop-ai-core OpenAiDialect:155）。压缩协调器却把它当作"上下文窗口"用于 forced-stop（0.9x）与压缩阈值判定的分母。`ChatOptionsModel` 中不存在独立的 contextWindow/maxContext 字段（已核对生成模型 `_ChatOptionsModel.java`）。
- **风险**: agent.xml 里声明 `maxTokens: 512`（限定回答长度的常规配置）后，第一轮迭代 `shouldForceStop` 在估计上下文超过 ~460 token 时即触发——含系统提示的正常对话几乎必然超限，agent 在做出任何 LLM 调用前就被 `forced_stopped`，且每次迭代重复触发。同理压缩阈值判定也被压低。触发路径现实（声明 maxTokens 是 DSL 常规用法），失败确定且表现为"agent 完全不可用"，但状态可见（FORCED_STOP 事件）、无静默数据损坏，故定 P1 而非 P0。
- **建议**: `resolveMaxContextTokens` 不应读 `chatOptions.maxTokens`；引入独立的 `maxContextTokens` 配置（模型声明或 provider 能力表），缺省回落 128000。
- **误报排除**: 已核对 dialect 层 `max_tokens` 语义（输出上限）与 `ChatOptionsModel` 字段全集，确认无独立上下文字段；确认 `DEFAULT_MAX_CONTEXT_TOKENS=128000` 的命名意图即上下文窗口，证明这是取值源接错而非语义另有定义。

### [P1] 会话邮箱与消息订阅只在创建时登记，会话终态后永不清理（mailboxFactory 接线时随会话数无界增长）

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/AgentSessionSupport.java:48`
- **维度**: D2
- **证据**:
```java
public void ensureSessionMailbox(String sessionId) {
    if (mailboxFactory == null) { return; }
    sessionMailboxes.computeIfAbsent(sessionId, sid -> {
        IMailbox mailbox = mailboxFactory.apply(sid);
        ...
        IMessageSubscription subscription = callDelegate.getMessenger().registerHandler(inboxTopic, handler);
        if (subscription != null) {
            sessionMailboxSubscriptions.put(sid, subscription);   // 只增不减
        }
        ...
    });
}
```
- **现状**: `ensureSessionMailbox` 在 doExecute/resumeSession/wakeSession/restoreSession 四个入口都会调用；而 `sessionMailboxes` / `sessionMailboxSubscriptions` 两个 ConcurrentHashMap 全模块只有 `computeIfAbsent`/`put`/`get`，没有任何 remove（已 grep 全量验证）。终态清理 finally（AgentSessionLifecycle:342-361、457-470、654-674）清理了 CancelHandle、checkpoint、actor、writeIntent、接管锁，唯独不清邮箱与订阅。
- **风险**: 每个会话（含 `sendMessage` 未带 sessionId 时每次请求新生成的 UUID 会话）永久占用一个 map 条目 + 一个 messenger topic 订阅（`{inbox-topic}` 消费者注册随会话数线性增长，且随引擎生命周期存活）。长期运行的服务内存与消息路由表无界增长。触发条件：接线了 `mailboxFactory`（Actor/steering 能力所需；默认 Builder 为 null 时本路径为 no-op），故定 P1。
- **建议**: 在终态清理 finally 中对称调用 `sessionMailboxSubscriptions.remove(sid).cancel()` + `sessionMailboxes.remove(sid)`；或为邮箱提供 TTL/容量上限。
- **误报排除**: 已 grep `sessionMailboxes|sessionMailboxSubscriptions` 全部 8 处使用点确认无任何 remove；已核对三个生命周期入口的 finally 块清单确认邮箱不在其中。

### [P1] 会话作用域内存结构无任何淘汰：每会话 memory store 默认必建且永不释放，DB/File 会话存储的写穿缓存同样无界

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/memory/InMemoryMemoryStoreProvider.java:21`（主），对照 `AgentSessionLifecycle.java:180`、`session/DBSessionStore.java:72`、`session/FileBackedSessionStore.java:89`
- **维度**: D2、D6
- **证据**:
```java
// InMemoryMemoryStoreProvider —— 无任何移除/淘汰方法
private final ConcurrentHashMap<String, InMemoryAiMemoryStore> stores = new ConcurrentHashMap<>();
public IAiMemoryStore getOrCreate(String sessionId) {
    ...
    return stores.computeIfAbsent(sessionId, k -> new InMemoryAiMemoryStore());
}
// AgentSessionLifecycle.buildBudgetedMemorySection —— 每次执行都会实例化一个 store
io.nop.ai.agent.memory.IAiMemoryStore store = config.getMemoryStoreProvider().getOrCreate(sessionId);
```
- **现状**: 引擎默认 provider 即 `InMemoryMemoryStoreProvider`（DefaultAgentEngine.Builder:147），且 `buildBudgetedMemorySection` 在 `memoryInjectionBudgetTokens > 0`（默认 1024）时对**每次执行**调用 `getOrCreate`——即使会话从未使用记忆工具也会留下一个永久 store 条目；写过记忆的会话则连同内容一起永久驻留。同类问题：`DBSessionStore`/`FileBackedSessionStore` 的写穿缓存 `sessions` map 无淘汰（引擎也从不调用 `sessionStore.remove`，已 grep 验证 engine 包无此调用）；`DefaultDenialLedger.counts` 仅在 resumeSession 时 reset，终态会话的计数永久残留（单条很小，顺带记录）。
- **风险**: 长生命周期引擎进程的内存随会话总数单调增长（默认配置即生效，无需特殊接线）；DB/File 后端的缓存还会把全量消息历史钉在内存里，削弱"换 DB 存储省内存"的预期。属于慢性资源耗尽，定 P1。
- **建议**: 为 provider/缓存提供终态清理钩子（引擎终态 finally 已有 `checkpointManager.remove` 先例，可对称处理）或基于容量的 LRU/TTL；`DefaultDenialLedger` 在终态清理时一并 reset。
- **误报排除**: 已确认 provider 与两个 store 类均无 remove/evict 方法且引擎侧无调用点；已确认默认装配路径（Builder 默认值 + memoryInjectionBudgetTokens 默认 1024）即触发 getOrCreate。

### [P2] extractAssistantMessage 可返回 null：ReAct 路径在 null 上解引用 NPE，SingleTurn 路径把 null 消息塞进上下文

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/ReActAgentExecutor.java:687-692` 与 `:733`；`SingleTurnExecutor.java:78-79`
- **维度**: D1
- **证据**:
```java
// ReActAgentExecutor:687-692 —— isSuccess 后不判空
ChatAssistantMessage assistantMsg = extractAssistantMessage(llmResult.response);  // 可返回 null
...
ctx.addMessage(assistantMsg);      // ArrayList 允许 null，静默污染消息列表
// :733
String llmOutputSummary = assistantMsg.getContent() != null ...   // ← assistantMsg 为 null 时 NPE
// SingleTurnExecutor:78-79 —— 无任何解引用，null 直接入列表，状态仍置 completed
ChatAssistantMessage assistantMsg = extractAssistantMessage(response);
ctx.addMessage(assistantMsg);
```
- **现状**: `ChatResponse.isSuccess()` 仅等于 `error == null`；`success(...)` 工厂允许构造 messages 为 null 或不含 assistant 消息的成功响应（javadoc 自己承认"defensive: a pre-326 response with only the legacy message field"这一形态）。`extractAssistantMessage` 对该形态返回 null。随附 ChatServiceImpl（流式与非流式）恒定追加 assistant 消息，故默认栈不可达；仅当接入不符合该约定的自定义/旧版 IChatService 时触发。ReAct 路径在 :733 NPE（被外层 catch 转为 failed，且 null 已留在消息列表随结果返回）；SingleTurn 路径更糟——不抛异常，状态 completed，但 `ctx.messages` 含 null 元素，后续序列化/遍历（如下一轮 buildBaseExecutionContext 复制历史、ChatRequest 序列化）才爆炸。
- **风险**: 自定义 chat service 集成下的执行失败/延迟 NPE，且错误信息（"java.lang.NullPointerException"）无助于定位。
- **建议**: `extractAssistantMessage` 返回 null 时构造空 content 的 `ChatAssistantMessage` 兜底（或 fail-loud 抛带说明的 NopAiAgentException）；`AgentExecutionContext.addMessage` 增加 null 参数校验。
- **误报排除**: 已核对 `ChatResponse` 全部工厂方法与 `isSuccess` 定义、`AgentExecutionContext.addMessage` 无 null 校验、随附 ChatServiceImpl 两条路径均恒定含 assistant 消息——故为条件触发（非默认栈），定 P2。

### [P2] 匿名执行（sessionId=null）时 `sessionStore.get(null)` 触发 ConcurrentHashMap NPE——同文件其他位置均做了 null 防护，唯两处遗漏

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/ReActAgentExecutor.java:758-759`；`engine/AgentToolDispatcher.java:385-386`
- **维度**: D1
- **证据**:
```java
// ReActAgentExecutor:758 —— 仅判 store 非空，未判 sessionId（对照 :365、:498 均为 store!=null && sessionId!=null）
if (sessionStore != null) {
    AgentSession persistedLlm = sessionStore.get(sessionId);   // sessionId=null → CHM.get(null) NPE
// AgentToolDispatcher:385 —— 同样遗漏（对照同类 :145 prepareDispatchContext 有防护）
if (sessionStore != null) {
    AgentSession persisted = sessionStore.get(sessionId);
```
- **现状**: 执行器明确支持匿名执行（checkpoint watermark 专门生成 `anon:llm:...` 前缀，:484-485、:742），但 LLM_TURN 后与工具结果提交后的会话同步两处直接 `get(sessionId)`。`InMemorySessionStore`/`DBSessionStore`/`FileBackedSessionStore` 的 `get` 第一行都是 `ConcurrentHashMap.get`，null key 抛 NPE。引擎主路径（DefaultAgentEngine）经 `resolveSessionId` 恒返回非空 id，故仅直接使用 `ReActAgentExecutor.builder().sessionStore(...)` 且 ctx 未设 sessionId 时可达；NPE 被外层 catch 转为 failed。同族小问题：`DefaultAgentEngine.cancelSession(null,...)` 走 else 分支同样 `get(null)` NPE（未捕获，直接抛给调用方）。
- **风险**: standalone 执行器用法（public API）下匿名执行在首次 LLM 成功后即失败；错误为裸 NPE。
- **建议**: 两处补 `sessionId != null` 条件（与 :498 一致）；`cancelSession` 入口判空。
- **误报排除**: 已确认三个 store 的 `get` 实现首行均为 CHM 访问（null key 必抛）；已确认 `resolveSessionId` 有 UUID 兜底，故引擎路径不可达、降级为 P2。

### [P2] 工具调用超时用 `orTimeout` 只完成 future，不取消底层工具执行——超时后工具作为僵尸继续运行并完成副作用

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/AgentToolDispatcher.java:227-243`
- **维度**: D2、D3
- **证据**:
```java
CompletableFuture<ToolCallOutput> toolFuture = toolManager.callTool(...)
        .thenApply(result -> new ToolCallOutput(chatToolCall, result));
if (toolTimeoutMs > 0) {
    toolFuture = toolFuture.orTimeout(toolTimeoutMs, TimeUnit.MILLISECONDS)
            .exceptionally(ex -> { ... return new ToolCallOutput(timedCall,
                    AiToolCallResult.errorResult(resultId, errMsg)); });
}
```
- **现状**: `orTimeout` 超时只让派生 future 异常完成，`exceptionally` 把超时转成错误工具结果返回给 LLM（会话不被阻塞，这达到注释声明的目标），但 `toolManager.callTool` 返回的原 future 未被 cancel——底层工具线程继续执行。对比同模块的另两条超时路径：LLM 调用超时会 `f.cancel(true)`（LlmCallCoordinator.callChatWithTimeout:616），call-agent 超时会显式 `engine.cancelSession(childSessionId, ..., true)`（CallAgentExecutor:471-479）——唯独通用工具路径无取消动作。
- **风险**: 慢工具超时后其副作用（文件写入、外部 API 调用、子 agent 执行）仍会完成且结果被丢弃；配合 300s 默认超时与无界线程场景，可造成线程占用与"LLM 已被告知失败、系统却实际执行成功"的状态分裂。
- **建议**: 保留 `toolManager.callTool` 的原 future 引用，超时分支对原 future `cancel(true)`；或为 IToolManager 提供取消协议。
- **误报排除**: 已确认代码未持有原 future 引用（链式直接派生）；已确认 LLM 与 call-agent 路径均有取消语义，属本路径遗漏而非平台约定。

### [P2] 执行失败仅保留 `e.toString()`，任何地方都不记录堆栈——生产排障信息丢失

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/ReActAgentExecutor.java:1042-1053`；`SingleTurnExecutor.java:95-100`
- **维度**: D4
- **证据**:
```java
} catch (Exception e) {
    if (ctx.isCancelRequested()) { ... } else {
        ctx.setStatus(AgentExecStatus.failed);
        ctx.setLastError(e.toString());          // ← 无 LOG.error(e)，无 cause 链
        hookInvoker.invokeOnError(ctx, agentName);
        hookInvoker.publishErrorEvent(AgentEventType.EXECUTION_FAILED, sessionId, agentName, e.toString());
    }
}
```
- **现状**: 执行器把所有异常折叠为 `AgentExecutionResult(status=failed, error=e.toString())` 的正常返回（future 永不异常完成，因此 `DefaultAgentEngine.sendMessage` 里带堆栈的 `future.exceptionally` 日志分支实际不可达）。全链路没有任何一处以 WARN/ERROR 级别打印该异常的堆栈（事件负载与 lastError 均为 toString）。cause 链同样丢失（NopAiAgentException 包装的底层异常只剩首行）。
- **风险**: 生产环境 agent 失败时只能看到一行异常签名，无法定位来源（对比：LlmCallCoordinator 内部的重试日志倒是有完整信息）。
- **建议**: catch 块补 `LOG.error("agent execution failed: session={}", sessionId, e)`（事件照发）；或 lastError 记录 `e.toString() + cause`。
- **误报排除**: 已核对 `DefaultAgentEventPublisher.publish` 仅在订阅者抛错时打日志、`sendMessage` 的 exceptionally 分支因 future 恒正常完成而不可达——确认全链路无堆栈落盘。

### [P2] 压缩触发条件用累计 token 用量（跨迭代累加的 prompt+completion）对比上下文阈值——语义错位导致过早压缩

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/AgentCompactionCoordinator.java:57-63`，对照 `ReActAgentExecutor.java:703`
- **维度**: D6、D8
- **证据**:
```java
public boolean shouldTriggerCompaction(AgentExecutionContext ctx) {
    long maxContextTokens = resolveMaxContextTokens(ctx);
    if (ctx.getTokensUsed() > maxContextTokens * ReActAgentExecutor.DEFAULT_TRIGGER_TOKEN_PERCENT) {
        return true;   // tokensUsed 是每轮 prompt+completion 的累计和（:703）
    }
    return ctx.getMessages().size() > ReActAgentExecutor.DEFAULT_TRIGGER_MAX_MESSAGES;
}
```
- **现状**: `ctx.getTokensUsed()` 在每轮 LLM 响应后累加该轮的 prompt+completion（ReAct 全历史重发模式下近似随轮数平方增长），并非当前上下文大小。用它对比 `0.8 × maxContextTokens` 会在实际上下文远未接近窗口时触发压缩（例：10 轮、每轮 5k 上下文的会话累计 ~50k，超过 128000×0.8 的阈值之前就会在更长会话中提前触发；若同时声明了小 maxTokens 则见 P1 第 2 条，双重放大）。同文件的 forced-stop 用的是正确口径（`tokenEstimator.estimateTokens(ctx.getMessages())`），两者口径不一致。
- **风险**: 过早触发 Layer1-3 压缩 → 中间轮次被裁剪/摘要，回答质量下降；与配置阈值表达的语义（"上下文接近窗口 80%"）不符。
- **建议**: 与 shouldForceStop 统一使用 pre-call 估算值；tokensUsed 仅留给用量统计。
- **误报排除**: 已核对 :698-703 的累加逻辑与 forced-stop 的估算口径差异，确认非同义指标。

### [P3] 默认沙箱后端名为 NoOp 却在宿主机直接执行命令，且启动告警未覆盖该默认值

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/NoOpSandboxBackend.java:63-95`；装配默认见 `ReActAgentExecutorBuilder.java:516-518`
- **维度**: D5
- **证据**:
```java
/** Shipped default ... used as the DefaultAgentEngine default ... Executes the
  command directly on the host JVM via ProcessBuilder — there is no isolation */
public final class NoOpSandboxBackend implements ISandboxBackend {
    ...
    ProcessBuilder pb = new ProcessBuilder(request.getCommand());
```
- **现状**: 引擎/执行器缺省装配的 `NoOpSandboxBackend` 并非 no-op，而是宿主机直接执行（仅墙钟与输出上限）。这是设计文档明示的"Layer 1 designable baseline"（有免责注释），执行链上还有权限矩阵、审批门、路径检查等多层前置。但 `AgentStartupWarnings.warnIfInsecureDefaults` 覆盖了 AllowAll 权限、NoOp usage recorder 等，却不覆盖沙箱缺省；且类名与 Nop 生态中"NoOp=直通空实现"的惯例相反，运维扫一眼装配容易误判为"没有执行任何东西"。
- **风险**: 部署未显式接 `DockerSandboxBackend` 且 agent 暴露 shell/code 执行类工具时，LLM 驱动的命令直接跑在宿主机；命名与告警缺口放大误配概率。
- **建议**: 在 startup warnings 中增加沙箱默认值告警；考虑改名（如 `HostSandboxBackend`）保留 NoOp 语义占位。
- **误报排除**: 已确认这是文档化的设计基线而非隐蔽行为（javadoc 明示），故仅定 P3 的可运维性问题。

### [P3] 工具结果内容不经任何 guardrail 检查即进入下一轮 prompt——聚合场景下最大的注入面无内置防线

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/AgentToolDispatcher.java:308-321`（结果直接构造响应消息），对照 `AgentPromptAssembly.java:67-91`（input guardrail 只查最后一条 user 消息）与 `:293-325`（output guardrail 只查 assistant 输出）
- **维度**: D5
- **证据**:
```java
// 工具结果 → spill/截断 → 直接成为工具响应消息，无 guardrail 调用
String resultText = toolResult.getOutput() != null ? toolResult.getOutput().getBody() : "";
resultText = spillIfOversized(sessionId, resultText, toolName);
toolResponse = ChatToolResponseMessage.fromToolCall(chatToolCall, resultText);
```
- **现状**: 引擎对 INPUT（最后一条 user 消息）与 OUTPUT（assistant 文本）都有 guardrail 挂点，但工具结果（网页抓取、文件读取等外部内容的主要入口）在提交进上下文前没有任何内容检查挂点被调用。注入内容可借工具结果进入上下文影响后续推理；PromptInjectionGuardrail 的 INPUT/OUTPUT 双向设计也未覆盖该通道。缓解因素：架构提供了 `POST_TOOL_ATTEMPT` / `BEFORE_TOOL_RESULT_PROCESSED` 执行级/会话级中间件可自行补检；记忆注入采用了独立 system 消息的权限边界设计（AgentSessionLifecycle:153-162 注释）。
- **风险**: 未自配中间件的部署中，工具结果是最容易承载提示注入的内容源且无默认检查。
- **建议**: 在工具结果提交点增加可选的 guardrail 挂点（Tool 方向），或默认串联 PromptInjectionGuardrail 的 REPORT 模式。
- **误报排除**: 已通读 dispatcher 提交路径与 promptAssembly 两个 guardrail 挂点的调用位置，确认工具结果通道确无调用；已确认中间件扩展点存在（属可补而非不可补）。

## 其他核查结论（未单列发现）

- **D7 平台规范**: 未发现违例。模块无 `@Inject`/`@Value`/`@Component` 等 Spring 系注解（纯 Builder 装配 + `_vfs` beans.xml 显式定义工具执行器 bean，符合 Nop IoC 约定）；错误处理采用模块异常 `NopAiAgentException`（英文消息、多处带 cause），个别公共入口使用 `NopAiCoreErrors` 错误码 + `.param(...)`，符合两档策略；无 bare RuntimeException、无空 catch、无 printStackTrace（全量 grep 为零）。
- **D1 循环终止**: reactLoop 全部 `continue` 路径均消耗迭代（:540/:558/:790/:795/:833/:898/:934），sustain 轮受 `sustainer.onStop` 契约（null 即 fail-loud）约束，completion-judge Continue 有 3 次死循环保护，POST_REASONING bail 有 3 次上限，LLM 重试有 veto 上限与 fallback 扫描硬上限（MAX_FALLBACK_SCAN=64）——未发现死循环或提前退出缺陷。
- **D3 并发**: 单会话单执行由 takeover 锁 + `runningExecutions.putIfAbsent` 去重（含 TOCTOU 修复注释）；steering 队列为 ConcurrentLinkedQueue、CancelHandle.thread/renewHandle 为 volatile、事件订阅者列表为 CopyOnWriteArrayList、邮箱三段协议单锁守护——未发现共享状态竞争。
- **密钥处理**: `doAccountSwitch` 将账号 API key 写入 `ChatOptions.accountKey` 下沉，日志仅输出账号 id/baseUrl 覆盖标志；`ModelKeys.buildModelKey` 只含 provider:model，不含 key——未发现密钥泄漏到日志/prompt 的路径。
