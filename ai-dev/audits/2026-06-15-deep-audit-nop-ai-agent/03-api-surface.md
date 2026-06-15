# 维度03：API 表面积与契约一致性

## 审计范围

审计 `nop-ai/nop-ai-agent/` 完整公共 API 表面：所有 33 个 public 接口（`engine`、`session`、`reliability`、`security`、`skill`、`completion`、`guardrail`、`hook`、`compact`、`memory`、`message`、`router`、`repair`、`talent` 包内 `I*` 类），关键公共值/模型类，并交叉比对 `agent.xdef` + `agent-plan.xdef` schema 与生成类及运行时消费方。

## 第 1 轮（初审）

### [维度03-01] `AgentExecutionContext` 直接暴露可变内部状态（messages/metadata），与同模块 `AgentSession` 的防御性拷贝约定不一致
- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/AgentExecutionContext.java:73-75,137-143`
- **证据片段**:
  ```java
  public List<ChatMessage> getMessages() {
      return messages;             // 直接返回内部 ArrayList 引用
  }
  ...
  public Map<String, Object> getMetadata() {
      return metadata;             // 直接返回内部 HashMap 引用
  }
  public void setMetadata(Map<String, Object> metadata) {
      this.metadata = metadata;    // 直接存入外部引用，未拷贝
  }
  ```
- **严重程度**: P1
- **现状**: 公共 API 把内部可变集合字段直接交给调用方；setMetadata 也不做防御性拷贝。
- **风险**: 任何外部调用方（hook、talent、tool、subscriber）拿到 `ctx.getMessages()` / `ctx.getMetadata()` 后写入或清空，都会绕过 `addMessage`/`setLastError` 等受控入口，破坏 executor 内部状态。同一个 repo 里的 `AgentSession.getMessages()`（行 75-77）正是用 `Collections.unmodifiableList(new ArrayList<>(messages))` 做防御性拷贝——模块内自我不一致。`DefaultAgentEngine` 在 forkSession 路径用 `child.setMetadata(parent.getMetadata())`（InMemorySessionStore.java:92）拿到的是同一个 map 引用，存在共享可变状态。
- **建议**: 与 `AgentSession` 保持一致，返回 `Collections.unmodifiableList(...)` / `unmodifiableMap(...)`；`setMetadata` 内做 `new HashMap<>(metadata)`。注意：现有 `ctx.getMetadata().putAll(...)` 调用点（DefaultAgentEngine:625、LlmCompletionJudge:236-237、ReActAgentExecutor:724）依赖可变性，应改为 `ctx.putMetadata(key, value)` 显式 API。
- **信心水平**: 确定
- **误报排除**: 不是 Nop "BizModel 返回 entity" 或 "@Inject protected 字段" 已知误报；这是同一模块内可对照的不一致约定（AgentSession 已采用防御性拷贝）。
- **复核状态**: 未复核

### [维度03-02] `AgentSession.getMetadata()` 直接返回可变 HashMap，与同类 `getMessages()` 的防御性拷贝在同一文件内自相矛盾
- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/session/AgentSession.java:75-77,146-148`
- **证据片段**:
  ```java
  public List<ChatMessage> getMessages() {
      return Collections.unmodifiableList(new ArrayList<>(messages));   // 防御性拷贝
  }
  ...
  public Map<String, Object> getMetadata() {
      return metadata;       // 直接返回内部 HashMap
  }
  ```
- **严重程度**: P2
- **现状**: 同一个类的两个集合 getter 一个做防御性拷贝、一个直接返回引用。
- **风险**: `InMemorySessionStore.forkSession` 路径中 `child.setMetadata(parent.getMetadata())`（行 92）导致父子 session 共享同一个 map；任何一方调用 `setMetadata` 之外的修改（如 `getMetadata().put(k,v)`）都会泄漏到另一方。同时 `setMetadata`（行 150-152）做了 `new HashMap<>(metadata)` 拷贝，但 getter 又把内部 map 暴露出去，使 setter 的拷贝失去意义。
- **建议**: getter 改为 `Collections.unmodifiableMap(metadata)` 或返回 `new HashMap<>(metadata)`，与 `getMessages()` 对齐。
- **信心水平**: 确定
- **误报排除**: 不是 dynamic DSL 上下文场景的合法 `Object` 用法；这是同文件内的 API 一致性缺陷，有结构性证据。
- **复核状态**: 未复核

### [维度03-03] `AgentEvent.payload` 共享同一个可变 Map（构造与 getter 均未拷贝）
- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/AgentEvent.java:14-22,50-52`
- **证据片段**:
  ```java
  public AgentEvent(AgentEventType eventType, String sessionId, String agentName,
                    Map<String, Object> payload, String error) {
      ...
      this.payload = payload;          // 直接存入外部引用
      ...
  }
  ...
  public Map<String, Object> getPayload() {
      return payload;                  // 直接返回内部引用
  }
  ```
- **严重程度**: P2
- **现状**: 事件 payload 既不在构造时拷贝、也不在 getter 处包不可变视图。
- **风险**: `DefaultAgentEventPublisher.publish`（DefaultAgentEventPublisher.java:16-25）按 CopyOnWriteArrayList 顺序调用每个 subscriber，subscriber A 修改 `event.getPayload().put(...)` 后 subscriber B 看到的就是被污染的 payload；同样审计 logger 也会看到被篡改的审计字段。`DefaultAgentEngine.publishCancelRequested`（行 527-533）、`publishCancelled`（行 535-540）等公共路径都受影响。
- **建议**: 构造时 `this.payload = payload != null ? Collections.unmodifiableMap(new HashMap<>(payload)) : null`；getter 维持返回不可变视图。
- **信心水平**: 确定
- **误报排除**: 不是"事件 payload 必须是 Map<String,Object>"的设计必要——拷贝/不可变视图不损失任何使用语义，只切断共享可变状态。
- **复核状态**: 未复核

### [维度03-04] 整个 `memory` 包（`IAiMemoryStore` / `AiMemoryItem` / `AiMemoryConfig`）是孤立的 dead public API
- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/memory/IAiMemoryStore.java:1-30`
- **证据片段**:
  ```java
  public interface IAiMemoryStore {
      List<AiMemoryItem> getAll(Map<String, Object> filters);
      List<AiMemoryItem> getLastN(int n);
      List<AiMemoryItem> search(String query);
      void add(AiMemoryItem item);
      default List<AiMemoryItem> readBudgeted(int maxTokens, Map<String, Object> context) {
          throw new UnsupportedOperationException("readBudgeted requires Phase 2");
      }
      ...
  }
  ```
- **严重程度**: P1
- **现状**: 搜索整个 `src/main`，`IAiMemoryStore`/`AiMemoryItem`/`AiMemoryConfig` 仅被自身和测试代码引用。引擎、executor、任何 SPI 注入路径都不引用本接口；模块内无任何 production 实现。
- **风险**: 公共 SPI 形同虚设——下游模块若按此接口实现 store 并期望引擎使用，发现根本没被接线。同时 4 个 default 方法都抛 `UnsupportedOperationException("... requires Phase 2")`（占位 SPI），调用方依赖一个永不工作的契约。
- **建议**: 二选一：(a) 在 `DefaultAgentEngine` 增设 `setMemoryStore(IAiMemoryStore)` 并在 ReAct loop 中实际消费，使 SPI 闭合；(b) 将 `memory` 包整体下沉到独立"future/experimental"模块或暂从 public API 移除，避免误用。
- **信心水平**: 确定
- **误报排除**: 已通过全模块 grep 确认 `src/main` 内除 `memory` 包自身外无引用；与"延期实现"不同——延期实现至少会被引擎按可选依赖注入，这里完全无注入点。
- **复核状态**: 未复核

### [维度03-05] 存在两套互不一致的 `_AgentPlanModel` 生成类，且运行时使用的类型与当前 `agent-plan.xdef` 完全不匹配
- **文件**:
  - `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/model/_gen/_AgentPlanModel.java:11-21`（旧）
  - `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/plan/model/_gen/_AgentPlanModel.java:11-19`（较新但仍不匹配）
  - schema: `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/ai/agent-plan.xdef:12-17`
  - 消费方: `engine/AgentExecutionContext.java:6,21,81-87`、`completion/LlmCompletionJudge.java:5,167-170`
- **证据片段**:
  ```java
  // 旧 _gen（io.nop.ai.agent.model._gen）注释自称从 agent-plan.xdef 生成：
  public abstract class _AgentPlanModel extends AbstractComponentModel {
      private java.time.LocalDateTime _createdAt ;
      private java.lang.String _currentPhase ;
      private KeyedList<io.nop.ai.agent.model.AgentPlanDecision> _decisions = ...;  // xdef 里没有
      private KeyedList<io.nop.ai.agent.model.AgentPlanError> _errors = ...;
      private java.lang.String _goal ;
      private KeyedList<io.nop.ai.agent.model.AgentPlanQuestion> _keyQuestions = ...;  // xdef 里没有
      private KeyedList<io.nop.ai.agent.model.AgentPlanNote> _notes = ...;
      private KeyedList<io.nop.ai.agent.model.AgentPlanPhaseModel> _phases = ...;
      ...
  }
  ```
  而当前 xdef 顶层名为 `AgentPlan`（不是 `AgentPlanModel`），bean-package 为 `io.nop.ai.agent.plan.model`，字段集合为 `title / currentPhase / currentTaskNo / purpose / goal / currentBaseline / sources / relatedPlans / successCriteria / nonGoals / scope / readFiles / writtenFiles / phases / errors / validationChecklist / closure`——两份 `_AgentPlanModel` 都不匹配。
- **严重程度**: P0
- **现状**: 同名（`_AgentPlanModel`）生成类在两个包内并存，字段集合互不相同；二者都自称从 `/nop/schema/ai/agent-plan.xdef` 生成。`agent-plan.register-model.xml` 把 `.xml/.yaml` loader 指向 xdef（生成 `AgentPlan`），`.md` loader 通过 `agent-plan.record-mappings.xml` 映射到 `plan.model.AgentPlanModel`（旧字段集合）。运行时引擎在 `AgentExecutionContext` 暴露的 `plan` 字段类型是 `io.nop.ai.agent.model.AgentPlanModel`（更旧），但全 `src/main` 中**没有任何代码调用 `ctx.setPlan(...)`** —— `LlmCompletionJudge.resolveGoal`（行 167-170）读取的 `plan.getGoal()` 在生产路径永远是 null。
- **风险**: (1) 三套并存的 `AgentPlanModel` 类字段集合都不一致，任何下游基于其中之一写的代码都会在重新生成时被覆盖；(2) 引擎对外暴露 `AgentExecutionContext.getPlan()`，让插件/hook 作者以为可以读到执行计划，实际只能拿到 null；(3) `LlmCompletionJudge.resolveGoal` 有一段死代码处理"plan.getGoal()"分支，永远不生效；(4) 维护者改 xdef 时难以预测哪些类会被重新生成、哪些是手写覆盖。
- **建议**: 一次清理：(a) 删除整个 `io.nop.ai.agent.model.AgentPlan*`（旧）与 `io.nop.ai.agent.plan.model.AgentPlanModel`（中间）及其 `_gen` 子目录，仅保留与当前 xdef 对齐的 `AgentPlan`；(b) 把 `AgentExecutionContext.plan` 字段类型改为 `AgentPlan`（或暂时移除该字段及 `LlmCompletionJudge.resolveGoal` 中的 plan 分支）；(c) 跑一次 `mvn install` 重新生成 `_gen`，确认无残留分歧。
- **信心水平**: 确定
- **误报排除**: 不是"生成代码不应改动"的误报——本发现的对象是 schema/源模型/dsl 之间的一致性问题，正是 source-of-truth 层面的契约缺口。AGENTS.md 明确要求"生成物以 source model 为准"，这里 source model（xdef）和保留的生成类已经分裂。
- **复核状态**: 未复核

### [维度03-06] `Permission` / `PathAccessResult` / `ToolAccessResult` 是三份结构几乎相同的决策值对象，无公共基类
- **文件**:
  - `security/Permission.java:5-15`
  - `security/PathAccessResult.java:5-15`
  - `security/ToolAccessResult.java:5-15`
- **证据片段**（三处对齐展示）:
  ```java
  // Permission
  private final boolean allowed;
  private final String reason;
  private final String matchedRuleId;
  // 工厂：allow() / deny(reason) / deny(reason, ruleId)

  // PathAccessResult
  private final boolean allowed;
  private final String reason;
  private final String matchedRule;
  // 工厂：allow() / deny(reason) / denyByRule(ruleName, path) / deny(reason, matchedRule)

  // ToolAccessResult
  private final boolean allowed;
  private final String reason;
  private final String matchedRule;
  // 工厂：allow() / deny(reason) / denyByRule(ruleName, toolName)
  ```
- **严重程度**: P2
- **现状**: 三个公共值类型字段同形（`boolean allowed / String reason / String matchedRule(Id)`），但既无公共基类、也无公共接口，工厂方法名也不完全对齐。
- **风险**: (1) 维护成本：未来要加字段必须改 3 处；(2) audit 代码需要为三种来源分别适配，没有任何多态/接口可统一；(3) 一致性漂移已经发生——`PathAccessResult` 有 `deny(reason, matchedRule)`，`ToolAccessResult` 没有；`Permission` 用 `matchedRuleId` 而另两个用 `matchedRule`。
- **建议**: 引入 `AbstractAccessDecision`（或 interface `AccessDecision`）承载 `allowed/reason/matchedRule` 三字段及统一工厂，三个子类型如需携带额外字段再扩展。统一字段命名为 `matchedRule`。
- **信心水平**: 很可能
- **误报排除**: 不是"看起来不优雅"——可量化（三份 80+ 行同构代码、字段名不一致、工厂方法集不一致），有结构性复制证据。
- **复核状态**: 未复核

### [维度03-07] `AgentMessageAck.status` 是字符串字面量 `"accepted"`，缺少枚举或常量
- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/AgentMessageAck.java:8-23`
- **证据片段**:
  ```java
  public class AgentMessageAck {
      private final String sessionId;
      private final String status;
      public AgentMessageAck(String sessionId, String status) {
          this.sessionId = sessionId;
          this.status = status;
      }
      public AgentMessageAck(String sessionId) {
          this(sessionId, "accepted");        // 字面量
      }
      ...
  }
  ```
- **严重程度**: P3
- **现状**: 公共 ack 类型用裸字符串表达状态，唯一可能值 `"accepted"` 仅以字面量形式出现在单参构造器中。
- **风险**: 调用方写拼写错或 `"ok"` 编译期无法捕获；下游 `if ("accepted".equals(ack.getStatus()))` 也依赖拼写。模块其它决策类全部用枚举，本类不一致。
- **建议**: 引入 `MessageAckStatus` 枚举（至少 `ACCEPTED`、`REJECTED`、`DUPLICATE` 等占位），单参构造器改为 `this(sessionId, MessageAckStatus.ACCEPTED)`。
- **信心水平**: 很可能
- **误报排除**: 同模块其它类已采用枚举，对照可知此为遗漏。
- **复核状态**: 未复核

### [维度03-08] `IAgentMessenger.request(...)` 返回 `CompletableFuture<Object>`，请求方必须强转
- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/message/IAgentMessenger.java:60`、实现 `message/LocalAgentMessenger.java:67-112,198-218`
- **证据片段**:
  ```java
  CompletableFuture<Object> request(AgentMessageEnvelope requestEnvelope, Duration timeout);
  ```
  ```java
  AgentMessageEnvelope env = (AgentMessageEnvelope) message;
  ...
  CompletableFuture<Object> future = pendingRequests.remove(correlationId);
  if (future != null) {
      future.complete(env.getPayload());     // payload 类型为 Object
  }
  ```
- **严重程度**: P3
- **现状**: 请求-响应契约对响应体不做任何类型约束；把弱类型直接外推给调用方。
- **风险**: 调用方需 `(MyResponse) future.join()`，运行时 ClassCastException 才能发现协议不匹配；缺乏泛型签名会让静态分析失效。
- **建议**: 引入 `IAgentMessenger.request(envelope, timeout, Class<T> responseType)` 重载，或泛型化 `AgentMessageEnvelope<T>` 让 `request` 返回 `CompletableFuture<T>`。
- **信心水平**: 有趣的猜测
- **误报排除**: `IAgentMessageHandler` 是用户实现的强类型业务接口（不是 DSL 解析上下文），`request` 是面向业务调用方的 API；这两个端点都应当能给定预期类型。
- **复核状态**: 未复核

### [维度03-09] `IAgentEngine` 接口 8 个方法中 6 个是 `default throw UnsupportedOperationException`，接口契约包含未实现能力
- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/IAgentEngine.java:37-188`
- **证据片段**:
  ```java
  default CompletableFuture<String> forkSession(AgentMessageRequest request, boolean inheritContext) {
      throw new UnsupportedOperationException("forkSession requires Phase 2 ISessionStore");
  }
  default AgentExecStatus getSessionStatus(String sessionId) {
      throw new UnsupportedOperationException("getSessionStatus requires Phase 2");
  }
  ...
  ```
- **严重程度**: P3
- **现状**: 唯一生产实现 `DefaultAgentEngine` 重写了全部 6 个，但接口默认行为是抛 UOE，错误消息明文写出 "requires Phase 2" 等内部计划用语。
- **风险**: (1) SPI 实现者若忘记重写某个方法，运行时才以 UOE 形式失败，错误消息把"Phase 2"内部术语暴露给调用方；(2) 接口"承诺"的能力比 default 行为大很多，从签名上无法分辨哪些是核心契约、哪些是可选；(3) `restorePendingSessions` 同步、其它 `CompletableFuture`，混合同步/异步契约。
- **建议**: 拆分为 `IAgentEngine`（核心：`sendMessage/execute`）+ `ISessionLifecycleEngine`（fork/resume/restore/cancel/...），让能力发现变成接口 instanceof 检查；同时错误消息去掉"Phase 2"等内部术语。
- **信心水平**: 很可能
- **误报排除**: 本接口 6/8 默认抛异常（75%），已不是"个别可选方法"，且唯一生产实现全量重写，等于接口默认值在生产中从不会被使用。
- **复核状态**: 未复核

### [维度03-10] `InMemorySessionStore.getAll()` / `listAllSessions()` 直接返回 `ConcurrentHashMap.values()` 活动视图
- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/session/InMemorySessionStore.java:32-35,50-53`
- **证据片段**:
  ```java
  @Override
  public Collection<AgentSession> getAll() {
      return sessions.values();
  }
  ...
  @Override
  public Collection<AgentSession> listAllSessions() {
      return getAll();
  }
  ```
- **严重程度**: P3
- **现状**: 公共 store 接口的两个枚举方法返回底层 map 的活动视图，调用方迭代时可调用 `iterator.remove()` 反向删除 session。
- **风险**: `DefaultAgentEngine.restorePendingSessions`（行 938）做 `new ArrayList<>(discovered)` 拍快照规避了该问题，但 ISessionStore 是公共 SPI，第三方实现/调用方未必做快照；与 `FileBackedSessionStore.listAllSessions()`（需扫描磁盘）的语义不对齐——后者返回的是新建列表，前者返回活动视图。
- **建议**: 两处都 `return new ArrayList<>(sessions.values());` 或 `Collections.unmodifiableCollection(...)`；同时把"返回快照"写入 ISessionStore javadoc。
- **信心水平**: 很可能
- **误报排除**: 与"返回 entity 集合"无关；这是 store SPI 自身语义不一致。
- **复核状态**: 未复核

### [维度03-11] `RuleBasedPathAccessChecker.rules` 用 `Collections.unmodifiableList(rules)` 而非 `List.copyOf`，对调用方后续修改仍是透传
- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/RuleBasedPathAccessChecker.java:76-87`
- **证据片段**:
  ```java
  public RuleBasedPathAccessChecker(List<PathRuleModel> rules, IPathAccessChecker delegate) {
      ...
      this.rules = Collections.unmodifiableList(rules);   // 仅包装，不复制
      this.delegate = delegate;
  }
  ```
- **严重程度**: P3
- **现状**: 构造器仅把传入 `rules` 包了一层不可变视图，没有 `new ArrayList<>(rules)` 拷贝；`DefaultAgentEngine.resolvePerAgentPathChecker`（行 1047-1053）传入的是 `agentModel.getPathRules()` —— 该 list 来自 `KeyedList.fromList(...)` 的视图，本身可能被 AgentModel 后续变更。
- **风险**: 同模块 `ParentPermissionConstraint`（行 115-117）使用 `Set.copyOf`/`List.copyOf`（真正不可变拷贝），两处安全相关 checker 对传入集合的持有策略不一致；若 agent 模型在 freeze 之前被修改，RuleBasedPathAccessChecker 看到的 rules 会跟着变。安全相关代码不应留这种窗口。
- **建议**: 改为 `this.rules = List.copyOf(rules);`（JDK 10+），与 `ParentPermissionConstraint` 对齐。
- **信心水平**: 很可能
- **误报排除**: 同包同上下文已有正确写法（`ParentPermissionConstraint`），可证明这是 inconsistency 而非设计选择。
- **复核状态**: 未复核

### [维度03-12] `SkillModel` 的所有集合 getter 直接返回内部可变字段
- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/skill/SkillModel.java:71-73,97-99,105-107,118-120`
- **证据片段**:
  ```java
  public List<String> getIntentSignature() {
      return intentSignature;          // 直接返回内部 List
  }
  ...
  public List<String> getDependencies() {
      return dependencies;
  }
  public Set<String> getTags() {
      return tags;
  }
  public Set<SkillResourceScope> getResourceScope() {
      return resourceScope;
  }
  ```
- **严重程度**: P3
- **现状**: hand-written POJO 的所有集合 getter 都返回内部字段，无不可变视图、无拷贝。
- **风险**: `SkillResolver.addSkill(skill)` 调用 `skill.collectToolDependencies(...)` 写入共享 sink，逻辑上只读，但若任何调用方在中间通过 `getDependencies().add(...)` 修改原列表，会影响下一次 resolver 调用。Skill 文档说"skills do not change at runtime per design"，但当前 API 形态并不强制这一点。
- **建议**: getter 改为返回 `Collections.unmodifiableList(...)`（首次访问惰性包装），或在首次访问后冻结；setter 内做拷贝。
- **信心水平**: 很可能
- **误报排除**: `SkillModel` javadoc 自己说"skills don't change at runtime"，当前实现并不能保证此契约——属于"声明与代码不匹配"。
- **复核状态**: 未复核

### [维度03-13] `CompactionResult.getCompactedMessages()` 返回内部 List，未做防御性拷贝
- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/session/CompactionResult.java:22-31,53-55`
- **证据片段**:
  ```java
  public CompactionResult(String sessionId, long tokensBefore, long tokensAfter,
                          int retainedMessageCount, String snapshotId,
                          List<ChatMessage> compactedMessages) {
      ...
      this.compactedMessages = compactedMessages;     // 直接存入外部引用
  }
  ...
  public List<ChatMessage> getCompactedMessages() {
      return compactedMessages;                       // 直接返回
  }
  ```
- **严重程度**: P3
- **现状**: 构造器与 getter 都不做防御性拷贝；同模块 `AgentExecutionResult`（行 27-41）对同样字段做了 `Collections.unmodifiableList(new ArrayList<>(messages))`。
- **风险**: 压缩结果理论上应当是不可变值对象（已实现 equals/hashCode），但当前实现允许调用方通过保留的 list 引用在事后修改，使 equals/hashCode 出现时间相关漂移；`CompactionResult` 还被 `equals/hashCode` 用于快照对比场景。
- **建议**: 与 `AgentExecutionResult` 对齐：`this.compactedMessages = compactedMessages != null ? Collections.unmodifiableList(new ArrayList<>(compactedMessages)) : null`。
- **信心水平**: 很可能
- **误报排除**: 不是"返回 List 就是合法"——同模块同类（AgentExecutionResult）已采用防御性拷贝，存在可对照的内部标准。
- **复核状态**: 未复核

### [维度03-14] `HookContext.getData()` 返回内部 HashMap 活动引用，使 hook 之间可通过 data map 互相污染
- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/hook/HookContext.java:8-32`
- **证据片段**:
  ```java
  private final Map<String, Object> data;
  ...
  public HookContext(AgentLifecyclePoint lifecyclePoint, AgentExecutionContext executionContext) {
      ...
      this.data = new HashMap<>();
  }
  ...
  public Map<String, Object> getData() {
      return data;            // 直接返回内部 HashMap
  }
  ```
- **严重程度**: P3
- **现状**: 同一 `HookContext` 在同一生命周期点按注册顺序传给多个 hook，每个 hook 都拿到同一个 `data` map 引用，且 `data` 没有 put/set API，唯一写入途径就是通过 `getData().put(...)`。
- **风险**: hook 之间无契约边界：先注册的 hook 可覆盖后注册 hook 写入的数据，且没有任何命名空间隔离；这与 IAgentLifecycleHook javadoc 描述的"hook 是独立扩展点"不符。
- **建议**: 提供 `putData(key, value)` / `getData(key)` 显式 API，getter 返回 `Collections.unmodifiableMap(data)`；或在 javadoc 明确"data map 是 hook 间共享可变状态，约定 key 命名空间前缀"。
- **信心水平**: 有趣的猜测
- **误报排除**: 与"hook 共享上下文"的合理设计不冲突——只是要求显式 API 而非裸 Map 暴露。
- **复核状态**: 未复核

## 维度复核结论

待复核。

## 子项复核结论

待复核。

## 最终保留项

待复核后填写。
