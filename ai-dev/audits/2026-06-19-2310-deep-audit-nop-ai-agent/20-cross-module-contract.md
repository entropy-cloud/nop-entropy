# 维度 20：跨模块契约一致性

## 检查范围

对外暴露接口（IAgentEngine 等）稳定性；消费上游（IMessageService/IToolExecutor/AiToolCall/ChatMessage/ILlmDialect/ITaskFlowManager）方式；DTO；事件 publish/subscribe；配置项；xdef 模型稳定性。

## 第 1 轮（初审）发现

### [维度20-01] IAgentEngine default 方法抛 UOE 与其自身 javadoc 承诺的 NopAiAgentException 契约矛盾（跨模块角度）

- **文件**: `engine/IAgentEngine.java:37-47,75-77,114-117,185-188`
- **证据片段**:
  ```java
  // javadoc(:64-66) 承诺: fails fast with a NopAiAgentException rather than silently no-op'ing
  default CompletableFuture<AgentExecutionResult> resumeSession(String sessionId, String approver, String reason) {
      throw new UnsupportedOperationException("resumeSession requires a registered denial ledger and a paused session");  // :75-77
  }
  default CompletableFuture<AgentExecutionResult> restoreSession(String sessionId, String approver, String reason) {
      throw new UnsupportedOperationException("restoreSession requires a FileBackedSessionStore-backed engine");  // :114-117
  }
  ```
- **严重程度**: P1
- **现状**: 维度09-04 已报 default 方法抛 UOE。本维度补跨模块角度：IAgentEngine javadoc（被未来上层 nop-ai-service/web/app 调用方依赖）对每个可失败操作承诺失败类型是 NopAiAgentException（extends NopException），但 default 实现抛 UnsupportedOperationException（extends RuntimeException），二者无父子关系。
- **风险**: 上层调用方按 javadoc 写 `catch(NopAiAgentException)` 无法捕获 default 抛的 UOE。任何使用非 DefaultAgentEngine（测试桩/Mock/变体）或面向接口编程的调用方会被 UOE 穿透 catch 块。UOE 表达"未实现"与 javadoc"结构化失败"语义不同。
- **建议**: default 方法抛 NopAiAgentException 使失败类型契约自洽；或 javadoc 明确"功能未启用时抛 UOE，调用方需同时捕获"。
- **信心水平**: 高
- **误报排除**: DefaultAgentEngine 中这些方法都 override 为抛 NopAiAgentException，问题只在"调用方按接口编程"时；两异常类型层次互不相干是客观契约矛盾。
- **复核状态**: 未复核（与维度09-04 互补，此处为跨模块契约稳定性角度）

### [维度20-02] SingleTurnExecutor/ReActAgentExecutor 假设 ChatResponse.getMessage() 在 isSuccess() 时非空，上游契约不保证

- **文件**: `engine/SingleTurnExecutor.java:41-52`；`engine/ReActAgentExecutor.java:1491-1492`；上游 `nop-ai-api/.../ChatResponse.java:169-171`
- **证据片段**:
  ```java
  // SingleTurnExecutor:41-52
  if (!response.isSuccess()) { ... return ...; }
  ChatAssistantMessage assistantMsg = response.getMessage();   // :51 未 null 检查
  ctx.addMessage(assistantMsg);                                 // :52
  // ReActAgentExecutor:1491-1492 同模式
  // ChatResponse:169-171 isSuccess() 仅检查 error
  public boolean isSuccess() { return error == null; }
  ```
- **严重程度**: P1
- **现状**: 上游 ChatResponse.isSuccess()=error==null，不要求 message!=null；new ChatResponse() 即 success=true+message=null。下游在 success 分支无条件 getMessage() 并把结果（可能 null）放入 ctx.messages（ArrayList.add(null) 不抛异常，null 被静默吞入）。
- **风险**: (1)nop-ai-api 是公开契约，未来任何 IChatService 实现按契约返回 error=null,message=null 都合法；一旦发生，null 消息混入上下文，在后续迭代 ChatRequest 序列化、跨进程持久化（JsonTool.stringify）、ChatRequest.copy() 处爆炸；(2)上层消费 AgentExecutionResult.getMessages() 拿到含 null 列表违反"消息元素非 null"隐含契约；(3)典型脆弱耦合。
- **建议**: 两 executor 显式 null 检查；或在 ChatResponse.isSuccess() javadoc 明确"success 隐含 message!=null"并由 nop-ai-core 实现保证。
- **信心水平**: 高
- **误报排除**: ChatResponse.isSuccess()=error==null 无 message 校验属实；success(message) 工厂无 null 校验；new ChatResponse() 允许 message=null。但补充：测试中 NullMessageChatService 构造 new ChatResponse() 验证 null 处理，但未覆盖两 executor success 分支——确认该路径无防御也无测试。
- **复核状态**: **已复核——成立（维持 P1）**。isSuccess 仅判 error==null、两 executor 无 null 检查、addMessage 静默接受 null 全部属实；契约漏洞+缺防御+测试缺口确认。

### [维度20-03] NoOpTeamManager/NoOpTeamTaskStore 写/状态转移方法抛 UOE，违反接口 Optional/boolean 返回契约

- **文件**: `team/ITeamTaskStore.java:39-44,96-137`；`team/NoOpTeamTaskStore.java:67-85`；`team/ITeamManager.java:122-136`；`team/NoOpTeamManager.java:78-86`
- **证据片段**:
  ```java
  // ITeamTaskStore:39-44 契约: An illegal transition... returns Optional.empty() — non-exception control flow
  // NoOpTeamTaskStore:67-69
  public Optional<TeamTask> claimTask(String taskId, String claimedBy) { throw notEnabled(); }
  // ITeamManager:132-135 bindMemberSession 返回 boolean (true成功/false失败)
  // NoOpTeamManager:82-86
  public boolean bindMemberSession(...) { throw notEnabled(); }
  ```
- **严重程度**: P2
- **现状**: ITeamTaskStore.claimTask/completeTask/abandonTask/reclaimTask javadoc 明确"非异常控制流返回 Optional.empty()"；ITeamManager.bindMemberSession 返回 boolean。但出厂默认 NoOp 实现在所有这些方法抛 UOE（虽是 Minimum Rules #24 intentional 设计，但与接口承诺的返回类型契约冲突）。
- **风险**: 调用方按接口契约写 `Optional<TeamTask> r=store.claimTask(...); if(r.isEmpty())handleDenied();`，NoOp 配置下到不了 isEmpty 就抛 UOE。team tools 已知 NoOp 行为做了 try/catch，但未来任何直接消费接口的上层模块（如 nop-ai-service 团队管理 BizModel）按 javadoc 编程会在 NoOp 配置下崩溃。
- **建议**: 修接口 javadoc 明确"未启用时抛 UOE 调用方必须捕获"；或让 NoOp 返回 empty/false（但这与 Minimum Rules #24 No Silent No-Op 冲突，故更推荐前者）。
- **信心水平**: 高
- **误报排除**: NoOp javadoc 明确"intentional fast failure per Minimum Rules #24"——已知设计选择，但与接口 javadoc 仍构成不一致契约。
- **复核状态**: 未复核

### [维度20-04] AgentMessageEnvelope.payload 为 Object，缺乏形式化 payload 契约

- **文件**: `message/AgentMessageEnvelope.java:25,28-44,62-64`；消费点 DefaultAgentEngine:813-821、CallAgentExecutor:257-261
- **证据片段**:
  ```java
  private final Object payload;   // :25 opaque payload
  // DefaultAgentEngine:813-821 消费侧必须 instanceof
  Object payload = envelope.getPayload();
  if (!(payload instanceof CallAgentRequestPayload)) { LOG.warn("...unexpected-payload..."); return new CallAgentResponsePayload("failure",...); }
  ```
- **严重程度**: P2
- **现状**: payload 是 Object 无 schema/xdef 约束。每种 (kind,topic) 合法 payload 类型只能从源码反推：(REQUEST,"agent.call-agent")↔CallAgentRequestPayload、(RESPONSE,"{senderId}.reply")↔CallAgentResponsePayload、(ASYNC,"{sessionId}.inbox")↔String。所有消费点需 instanceof + 容忍 unexpected type。
- **风险**: 跨模块（未来 publisher/subscriber）必须依赖源码/文档约定判断合法 payload 类型，无编译期校验。生产方与消费方类型约定漂移时，消费方运行时只得"unexpected payload type"warn + fail-closed，无法编译/装载期发现。
- **建议**: (a)envelope 加 String payloadType 字段配对；(b)或为 AgentMessageKind 各值定义预期 payload 基类型（如标记接口 IAgentRequestPayload）。
- **信心水平**: 中
- **误报排除**: 已检查 3 生产点+3 消费点均 instanceof 防御；非 bug，是契约 discoverability 问题影响未来跨模块扩展安全。
- **复核状态**: 未复核

### [维度20-05] CallAgentResponsePayload.status 自由 String 字面量，未定义为枚举/常量

- 与维度15-E 同一发现。详见 `15-type-safety.md` [维度15-E]。跨进程消息角度补强：DBMessageService 路径序列化为 String 传输，生产方误传"ok"/"completed"/"Success"时消费方 equals 静默走 failure 分支无编译期提示。严重程度 P2。

### [维度20-06] AgentMessageAck.status 自由 String 且无状态词汇表

- **文件**: `engine/AgentMessageAck.java:3-24`
- **证据片段**:
  ```java
  public class AgentMessageAck {
      private final String status;
      public AgentMessageAck(String sessionId) { this(sessionId, "accepted"); }   // :14 唯一生产点硬编码
  }
  ```
- **严重程度**: P3
- **现状**: status 是 String，全仓唯一生产点硬编码"accepted"，无 javadoc 描述可能值集合/状态机。全仓搜索 getStatus() 无任何条件分支调用方。
- **风险**: 跨模块上层（未来 HTTP/GraphQL）按字面量判断，未来引入新状态时上层无文档可循可能 silently 误处理。
- **建议**: 删除 status（YAGNI，1 值无消费分支）；或定义枚举 AckStatus 或 javadoc 列已知值。
- **信心水平**: 高
- **误报排除**: 全仓搜索无 getStatus() 条件分支调用方；字段实际冗余但暴露 public API 即构成跨模块契约表面。
- **复核状态**: 未复核

### [维度20-07] IAgentMessenger.request 契约未声明 correlationId 唯一性约束，LocalAgentMessenger 冲突时静默 abort 先前在飞请求

- **文件**: `message/IAgentMessenger.java:44-60`；`message/LocalAgentMessenger.java:92-104`
- **证据片段**:
  ```java
  // IAgentMessenger.request javadoc 只要求 correlationId 非空，未要求唯一
  // LocalAgentMessenger:95-104
  CompletableFuture<Object> previous = pendingRequests.put(correlationId, future);
  if (previous != null && !previous.isDone()) {
      LOG.warn("...correlationId collision; previous in-flight request aborted...");   // 静默 abort
      previous.completeExceptionally(new IllegalStateException("correlationId reused while request still in-flight: " + correlationId));
  }
  ```
- **严重程度**: P2
- **现状**: 接口 javadoc 只要求 correlationId 非空未要求"在所有在飞请求中唯一"，但 LocalAgentMessenger 隐式假设唯一——冲突时 silently abort 先前在飞请求（IllegalStateException 完成 previous future）仅 WARN。
- **风险**: 上层按接口编程不知"correlationId 必须在飞唯一"。若用确定性 correlationId（如业务主键 sessionId+taskNo），同 senderId 两在飞请求触发 silent abort，造成难诊断的"莫名其妙请求失败"。当前唯一调用方 CallAgentExecutor:232 用 UUID 恰好规避，但接口未上升为契约，未来跨模块调用方（按业务键发起 request-response）易踩坑。
- **建议**: javadoc 明确"correlationId 必须在所有未完成 request 中唯一，否则实现可能 abort 旧在飞请求"；或 LocalAgentMessenger 改抛异常而非 silent abort。
- **信心水平**: 中-高
- **误报排除**: LocalAgentMessenger:100-103 确实 silently complete previous 异常+WARN 未抛；注释"caller should use unique ids"是私有实现层文档未上升契约。
- **复核状态**: 未复核

### [维度20-08] AgentEvent.payload 为 Map<String,Object>，每个 AgentEventType 键集合散落 publish 点无集中契约

- **文件**: `engine/AgentEvent.java:11`；`engine/AgentEventType.java:3-79`；publish 点 DefaultAgentEngine:2026-2030/2033-2037/2069-2072/2503-2508/2701-2706
- **证据片段**:
  ```java
  private final Map<String, Object> payload;   // AgentEvent:11
  // DefaultAgentEngine:2503-2508 一种事件的键
  Map<String,Object> resumePayload = new HashMap<>();
  resumePayload.put("approver", approver != null ? approver : "");
  resumePayload.put("reason", reason != null ? reason : "");
  resumePayload.put("preResetDenialCount", preResetDenialCount);
  ```
- **严重程度**: P2
- **现状**: payload 无类型 Map，每个 AgentEventType 应携带哪些键/值类型仅散落 5+ publish 点内联 HashMap，AgentEventType 枚举 javadoc 只对部分事件键做文字描述，无机器可读 schema。
- **风险**: 未来上层模块（审计 logger/监控 subscriber/HTTP SSE）订阅 IAgentEventPublisher 后必须按事件类型硬编码字符串键读 payload。publish 点重命名键/新增必填键/改值类型，上层静默拿 null 或 ClassCastException。事件契约（语义稳定 pub/sub）应作稳定 API 维护。
- **建议**: 为每个 AgentEventType 定义 payload 常量类或 typed record；至少在枚举 javadoc 用 @payloadKey 集中列出键名/类型。
- **信心水平**: 中
- **误报排除**: 抽样比对 SESSION_RESUMED javadoc 与 publish 点当前一致；但多个事件键未在 javadoc 列出。是契约 discoverability 退化非当前 bug。
- **复核状态**: 未复核

## 维度复核结论

[维度20-02] 独立复核：**成立 P1**（契约漏洞+缺防御+测试缺口）。[维度20-05] 与 15-E 同源去重。其余（20-01/03/04/06/07/08）复核未发现反证，保留。xdef 模型（agent.xdef/agent-plan.xdef）核验稳定，跨模块消费方契约表面稳定，无 xdef↔model 字段漂移。

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|------|---------|---------|-----------|
| 20-01 | P1 | engine/IAgentEngine.java | default 方法抛 UOE 与 javadoc 承诺 NopAiAgentException 矛盾 |
| 20-02 | P1 | engine/SingleTurnExecutor.java | 假设 getMessage() 非空，上游 isSuccess() 不保证 |
| 20-03 | P2 | team/NoOpTeamManager.java 等 | NoOp 写方法抛 UOE 违反 Optional/boolean 契约 |
| 20-04 | P2 | message/AgentMessageEnvelope.java | payload 为 Object 无形式化契约 |
| 20-05 | P2 | message/CallAgentResponsePayload.java | status 自由 String 字面量（同15-E） |
| 20-06 | P3 | engine/AgentMessageAck.java | status 无状态词汇表 |
| 20-07 | P2 | message/LocalAgentMessenger.java | correlationId 唯一性约束未声明，冲突静默 abort |
| 20-08 | P2 | engine/AgentEvent.java | payload 键集合散落无集中契约 |
