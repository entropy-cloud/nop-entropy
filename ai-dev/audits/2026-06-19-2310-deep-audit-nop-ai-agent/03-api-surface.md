# 维度 03：API 表面积与契约一致性（Java 公共接口）

## 检查范围

本模块无 BizModel/GraphQL/xmeta，本维度转为 Java 公共接口表面积与契约一致性。核验 IAgentEngine/IAgentExecutor/ISessionStore/ITeamManager/ICheckpointManager/IToolExecutor/IApprovalGate 等 78 个接口；beans.xml↔IToolExecutor 一致性；register-model↔xdef 一致性。

## 第 1 轮（初审）发现

### [维度03-01] IAiMemoryStore 缺 findByKey/size/clear，工具用 instanceof InMemoryAiMemoryStore 绕开接口

- **文件**: `memory/IAiMemoryStore.java:6-30`；`tool/ReadMemoryExecutor.java:129-137`；`tool/WriteMemoryExecutor.java:152-167`
- **证据片段**:
  ```java
  // ReadMemoryExecutor.java:129
  private static AiMemoryItem findByKey(IAiMemoryStore store, String key) {
      if (store instanceof io.nop.ai.agent.memory.InMemoryAiMemoryStore) {
          return ((InMemoryAiMemoryStore) store).findByKey(key);
      }
      return store.getAll(null).stream().filter(i -> key.equals(i.getKey())).findFirst().orElse(null); // O(n)
  }
  ```
- **严重程度**: P1 → **复核降级 P2**
- **现状**: 接口未暴露 findByKey/size/clear；工具执行器 instanceof + 强转访问；非 InMemory 实现（AdapterBackedAiMemoryStore）走 O(n) fallback。
- **风险**: 接口契约与工具需求脱钩；新增 store 实现易漏方法；AdapterBacked 走 fallback 性能差。
- **建议**: 提升三方法到接口（或 IAiMemoryStoreExtended），删除 instanceof 分支。
- **信心水平**: 高
- **误报排除**: beans.xml 注册的 read-memory/write-memory 是 IoC 实际加载；AdapterBackedAiMemoryStore 是 main 真实实现。
- **复核状态**: **已复核——降级 P1→P2**。fallback 功能完全正确（只是 O(n) 性能），非正确性 bug；AdapterBackedAiMemoryStore 自身 size/clear 内部也调 loadAll 即 O(n)，真正优化空间在 storage adapter 层。

### [维度03-02] ISessionStore 通过 7 个 default-throw-UOE 方法把实现细节泄漏进接口（ISP 违反）

- **文件**: `session/ISessionStore.java:62-143`
- **证据片段**:
  ```java
  default Collection<AgentSession> listAllSessions() { throw new UnsupportedOperationException("listAllSessions requires ..."); }  // 62
  default void save(AgentSession session) { throw new UnsupportedOperationException("save requires a persistent ..."); }           // 86
  default String forkSession(String parentSessionId, ...) { throw new UnsupportedOperationException("forkSession requires VfsSessionStore"); }  // 125
  // 另有 appendEvent/compact/loadSnapshot/setPlanRef 同模式
  ```
- **严重程度**: P2
- **现状**: 在 5 个核心方法之上叠 7 个默认抛异常的方法，反复出现 "requires VfsSessionStore/Phase 2/persistent"。
- **风险**: 接口膨胀到 12 方法，无实现能"完成整个接口"；新调用者读不到 UOE 契约意图，写出在某 store 上崩溃的代码。
- **建议**: 拆 IPersistentSessionStore/ISessionSnapshotStore 子接口，或统一 Javadoc 约束。
- **信心水平**: 高
- **误报排除**: InMemorySessionStore 把这些方法分化为 no-op/真实现，证明语义本应分化。
- **复核状态**: 未复核

### [维度03-03] lookup 接口在 Optional vs 原生 null 之间不一致，ISessionStore.get 完全无 Javadoc

- **文件**: `session/ISessionStore.java:11`；`team/ITeamManager.java:70,79,145`；`runtime/IActorRuntime.java:94,102`
- **证据片段**:
  ```java
  AgentSession get(String sessionId);                  // ISessionStore:11 无 Javadoc，返回原生 null
  Optional<Team> getTeam(String teamId);               // ITeamManager:70
  Optional<AgentActor> getActor(String actorId);       // IActorRuntime:94
  ```
- **严重程度**: P2
- **现状**: 同语义"按id查找可能不存在"，三种约定并存；FileBackedSessionStore 不存在时 return null；DefaultAgentEngine 6 处调用后立即 `if(session==null)throw`。
- **风险**: 调用者无契约指引，按 Optional 习惯写会 NPE；IDE 静态分析无法告警。
- **建议**: ISessionStore.get 加 @Nullable+Javadoc，长期统一 Optional。
- **信心水平**: 高
- **误报排除**: 同模块 ITeamManager/IActorRuntime 已用 Optional，证明模块内存在不一致。
- **复核状态**: 未复核

### [维度03-04] 17 处 instanceof NoOpXxx 用具体类探测"未启用"状态

- **文件**: tool/TeamExecuteFlowExecutor.java:172,178；tool/CallAgentExecutor.java:202；engine/DefaultAgentEngine.java:619,638,654,787,3299 等（共 17 处）
- **证据片段**:
  ```java
  if (teamManager == null || teamManager instanceof NoOpTeamManager) {   // TeamExecuteFlowExecutor:172
      return honestNotEnabled(call.getId(), "Team functionality is not enabled ...");
  }
  if ((hasTeamDecl || hasMemberDecl) && teamManager instanceof NoOpTeamManager) {  // DefaultAgentEngine:3299
      throw new NopAiAgentException("Agent declares <team>/<team-member> but no functional ITeamManager ...");
  }
  ```
- **严重程度**: P2
- **现状**: ITeamManager/ITeamTaskStore/IAgentMessenger/IAuditLogger/ISecurityLevelResolver/IDenialLedger 都没有 isEnabled()，消费者用 instanceof 具体类判断；而 IActorRuntime 已有 isEnabled()（:66）。
- **风险**: 装饰器/代理包装 NoOp 不被识别；测试 stub 必须继承 NoOp 而非直接 implements；违反面向行为接口设计。
- **建议**: 受影响接口加 `default boolean isEnabled(){return true;}`（NoOp 覆盖为 false），替换所有 instanceof。
- **信心水平**: 高
- **误报排除**: IActorRuntime.isEnabled() 的存在证明模式已被意识到但只对 1 个接口做了。
- **复核状态**: 未复核

### [维度03-05] IAgentMessenger.request 返回 CompletableFuture<Object>，响应载荷完全无类型

- **文件**: `message/IAgentMessenger.java:60`；`tool/CallAgentExecutor.java:250-263`；`message/IAgentMessageHandler.java:28`
- **证据片段**:
  ```java
  CompletableFuture<Object> request(AgentMessageEnvelope requestEnvelope, Duration timeout);  // :60
  // CallAgentExecutor:257
  if (!(response instanceof CallAgentResponsePayload)) { ... errorResult(...); }
  ```
- **严重程度**: P2
- **现状**: 请求-响应契约只承诺 Object；两调用点都需 instanceof+强转；LocalAgentMessenger 多处 return null 混合"响应payload"与"无响应"语义。
- **风险**: 编译期无法捕获响应类型不匹配；新增 request/response 对无 schema 校验。
- **建议**: 引入泛型/类型化 envelope 或在 envelope 上声明 expectedResponseType。
- **信心水平**: 中-高
- **误报排除**: 这是接口固定响应类型契约（非 LLM 动态参数），应类型安全。
- **复核状态**: 未复核

### [维度03-06] ISessionStore.forkSession 用 Map<String,Object> + magic key "agentName" 传结构化 override

- **文件**: `session/ISessionStore.java:91-127`
- **证据片段**:
  ```java
  // 契约 Javadoc: key "agentName" (if String) overrides child agent name
  default String forkSession(String parentSessionId, boolean inheritContext, Map<String, Object> props) { throw UOE; }  // 125
  // InMemorySessionStore:103
  Object agentNameValue = props.get(PROPS_KEY_AGENT_NAME);   // "agentName"
  ```
- **严重程度**: P3
- **现状**: 强类型意图（一个 String）+ 弱类型 map 塞同一 Map，靠 magic key 区分；拼错无声退化为继承 parent。
- **风险**: 类型不安全；三实现重复 magic key 解码；新 override 让 magic key 无限增长。
- **建议**: 拆签名为 `forkSession(..., String overrideAgentName, Map metadata)`。
- **信心水平**: 高
- **误报排除**: agentName override 是宿主代码强类型调用，非动态参数。
- **复核状态**: 未复核

### [维度03-07] AgentToolExecuteContext 7 个 telescoping constructor / 14 字段

- **文件**: `engine/AgentToolExecuteContext.java:56-287`
- **证据片段**:
  ```java
  public AgentToolExecuteContext(... 10 参数) { }   // :56
  public AgentToolExecuteContext(..., Set<String> allowedTools) { }   // :84  11参数
  // ... 递增到 17 参数（:271）；类持 14 final 字段
  ```
- **严重程度**: P2
- **现状**: 7 构造器层层委托；注释自承"5 个测试 caller 需兼容"。
- **风险**: 新字段需改 7 构造器；Set<String> allowedTools 与 Set<String> allowedPathRoots 类型相同，互换不报编译错。
- **建议**: 引入 Builder（参考 ReActAgentExecutor.Builder），构造器降级 package-private。
- **信心水平**: 高
- **误报排除**: 14 字段可扩展配置载体，符合 Builder 重构经典场景。
- **复核状态**: 未复核

### [维度03-08] ITeamTaskStore.getTasksByCreator 仅被测试调用，无生产 caller

- **文件**: `team/ITeamTaskStore.java:88-94`；实现 InMemoryTeamTaskStore:117 / DbTeamTaskStore:225
- **证据片段**:
  ```java
  List<TeamTask> getTasksByCreator(String createdBy);   // 接口契约
  // grep 全模块 main：仅接口声明 + 3 实现；caller 全在 src/test
  ```
- **严重程度**: P3
- **现状**: 接口契约承诺外部 never-used 查询；3 实现（含 DB SQL）需永久维护。
- **建议**: 删除或标 "@since TODO reserved"。
- **信心水平**: 高
- **误报排除**: grep 确认 main 无 caller，非反射/beans 调用。
- **复核状态**: 未复核

### [维度03-09] Team.getMembers() 声明 ConcurrentMap 但实现静默依赖 ConcurrentHashMap 原子性

- **文件**: `team/Team.java:99-101`；`team/InMemoryTeamManager.java:218-220,245-247,271-273,297`
- **严重程度**: P2（与 15-A 同一问题，类型安全角度）
- **现状**: 公开类型 ConcurrentMap，实现 @SuppressWarnings 强转 ConcurrentHashMap 并用 compute 原子性。
- **复核状态**: 见维度15-A 复核结论（降级为 P2：强转无必要、fail-fast 非静默）。

### [维度03-10] IPostDenialGuard.checkBeforeDispatch 用 null 表示 allow，与同模块 7 个 decision-style 接口不一致

- **文件**: `security/IPostDenialGuard.java:53-73`
- **证据片段**:
  ```java
  * @return a DenialResult if ... already-denied, or null to allow the call to proceed   // :68-73
  DenialResult checkBeforeDispatch(String sessionId, String toolName, Map<String,Object> arguments, String workDir);
  // 对比：IApprovalGate/IPermissionMatrix/IToolAccessChecker/IPathAccessChecker/ITeamAclChecker/IResourceGuard/IMemberSpawner 全部返回非 null result
  ```
- **严重程度**: P3
- **现状**: 同包 7 个兄弟决策接口都返回非 null result 对象，唯独此接口用 null=allow。
- **风险**: 跨接口写防御代码易误判 NPE；决策日志统一封装需特例分支。
- **建议**: 引入 DenialResult.allowed()/denied() 工厂，永不返回 null。
- **信心水平**: 高
- **误报排除**: 非 lookup-style（如 getLatestCheckpoint 返回 null 合理）；是 decision-style。
- **复核状态**: 未复核

## 维度复核结论

[维度03-01] 独立复核：**降级 P1→P2**（fallback 功能正确，纯接口设计/性能问题）。[维度03-09] 与 15-A 同源，按 P2 处理。其余（03-02~08, 10）复核未发现反证，保留原级。

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|------|---------|---------|-----------|
| 03-01 | P2 | memory/IAiMemoryStore.java | 接口缺方法，工具 instanceof 绕开（已降级） |
| 03-02 | P2 | session/ISessionStore.java | 7 个 default-UOE 方法污染接口（ISP） |
| 03-03 | P2 | session/ISessionStore.java | lookup 接口 Optional vs null 不一致 |
| 03-04 | P2 | tool/*+engine | 17 处 instanceof NoOpXxx 探测未启用 |
| 03-05 | P2 | message/IAgentMessenger.java | request 返回 Object 无类型 |
| 03-06 | P3 | session/ISessionStore.java | forkSession 用 magic key 传 override |
| 03-07 | P2 | engine/AgentToolExecuteContext.java | 7 telescoping constructor/14字段 |
| 03-08 | P3 | team/ITeamTaskStore.java | getTasksByCreator 无生产 caller |
| 03-09 | P2 | team/Team.java | ConcurrentMap 声明依赖 ConcurrentHashMap（同15-A） |
| 03-10 | P3 | security/IPostDenialGuard.java | null=allow 与 7 兄弟接口不一致 |

**beans.xml↔IToolExecutor 一致性**：10 个 bean 全部指向真实存在的 IToolExecutor 实现，TestAiAgentToolsIoC 实测验证，无悬空。**register-model↔xdef**：agent/agent-plan 的 schemaPath 与 nop-xdefs 中实际 xdef 一致。
