# 维度 02：模块职责与文件边界 — nop-ai-agent

## 第 1 轮（初审）

### [维度02-1] DefaultAgentEngine 承担 7+ 跨领域不相关职责，混杂 team / message / security / skill / memory / hook 多包业务逻辑

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java:142-3021`
- **证据片段**:
  ```java
  // [职责 A] 30+ setter/getter 对作为 DI 容器（606-1640 行）
  public void setTalents(List<ITalent> talents) { ... }      // line 616
  public void setMessenger(IAgentMessenger messenger) { ... } // line 699
  public void setApprovalGate(IApprovalGate approvalGate) { ... } // line 942
  public void setCheckpointManager(ICheckpointManager checkpointManager) { ... } // line 1061
  public void setTeamManager(ITeamManager teamManager) { ... } // line 1343

  // [职责 B] team 业务逻辑（应在 team/ 包）
  private void precheckTeamDeclarations(AgentModel agentModel) { ... } // line 2882
  private void autoBindTeam(AgentModel agentModel, String sessionId, String agentName) { ... } // line 2901
  private void autoBindLead(TeamModel teamDecl, String sessionId, String agentName) { ... } // line 2922
  private void autoBindMember(TeamMemberRefModel memberDecl, String sessionId) { ... } // line 2956

  // [职责 C] call-agent 消息处理（应在 message/ 包）
  private Object handleCallAgentRequest(AgentMessageEnvelope envelope) { ... } // line 747
  private void registerCallAgentHandler(IAgentMessenger messenger) { ... } // line 712

  // [职责 D] security checker 组合（应在 security/ 包）
  IToolAccessChecker resolveEffectiveToolAccessChecker(AgentMessageRequest request) { ... } // line 2549
  IPathAccessChecker resolvePerAgentPathChecker(AgentModel agentModel) { ... } // line 2581
  IPathAccessChecker resolveEffectivePathAccessChecker(AgentMessageRequest request, IPathAccessChecker perAgentBase) { ... } // line 2620

  // [职责 E] skill curation
  public SkillCurationResult curateSkills() { ... } // line 658

  // [职责 F] mailbox 生命周期管理（应在 message/ 包）
  private void ensureSessionMailbox(String sessionId) { ... } // line 2772

  // [职责 G] hook contribution 解析 + memory section 构造
  private void resolveHookContributions(IHookRegistry hookRegistry) { ... } // line 2742
  private String buildBudgetedMemorySection(String sessionId) { ... } // line 2043
  ```
- **严重程度**: P1
- **现状**: `DefaultAgentEngine` 名义上是 `IAgentEngine` 的实现，实际承担了 7 类不相关职责。其中至少 4 类（team 自动绑定、call-agent 消息处理、security checker 组合、mailbox 生命周期）是来自 `team/`、`message/`、`security/` 包的业务逻辑被搬进 `engine/` 包，破坏了包级别的职责边界。文件因这些职责堆叠达 3021 行。
- **风险**:
  - 团队/消息/安全逻辑的修改需要打开 `engine/DefaultAgentEngine.java`，违反"修改一个关注点只需改一处"的内聚原则；
  - 包间依赖倒置：`engine/` 包反向依赖 `team/`、`message/`、`security/` 的具体类型而非抽象，增加耦合；
  - 后续维护者难以判断"team 自动绑定 bug"应去 `team/` 还是 `engine/` 找；
  - 文件过大（3021 行）使 review 与冲突合并成本陡增。
- **建议**: 提取协作类：
  - `team/TeamAutoBinder`（封装 `precheckTeamDeclarations` / `autoBindTeam` / `autoBindLead` / `autoBindMember` / `resolveActorId`）
  - `message/CallAgentMessageHandler`（封装 `handleCallAgentRequest` / `registerCallAgentHandler`）
  - `security/SecurityCheckerComposer`（封装 3 个 `resolveEffective*Checker` 方法）
  - `message/SessionMailboxLifecycle`（封装 `ensureSessionMailbox`）
  - `DefaultAgentEngine` 退化为：注入上述协作类 + 执行入口调度 + 纯引擎级 setter
- **信心水平**: 确定
- **误报排除**: 这不是"Nop CrudBizModel 大文件平台模式"——`DefaultAgentEngine` 不继承 `CrudBizModel`、不是 GraphQL 端点、不存在 xbiz 生成映射；它是手写的运行时引擎实现类，应受单一职责约束。也不是"必要的 DI 容器"——DI 容器只需 setter/getter，不需要把 team 绑定、消息处理、security 组合、skill curation、mailbox 生命周期、hook 解析、memory 注入一并塞入。
- **复核状态**: 未复核

---

### [维度02-2] DefaultAgentEngine 三入口方法 doExecute / resumeSession / restoreSession 存在 ~200 行设置代码三重复制

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java:1815-1985`（doExecute）, `2102-2241`（resumeSession）, `2244-2434`（restoreSession）
- **证据片段**:
  ```java
  // doExecute (line 1824, 1853-1856, 1873-1878, 1896, 1914, 1921, 1948)
  precheckTeamDeclarations(agentModel);
  IPathAccessChecker perAgentBase = resolvePerAgentPathChecker(agentModel);
  IPathAccessChecker effectivePathAccessChecker = resolveEffectivePathAccessChecker(request, perAgentBase);
  ensureSessionMailbox(sessionId);
  IAgentExecutor executor = resolveExecutor(agentModel, effectiveToolAccessChecker, effectivePathAccessChecker);
  ...
  if (!sessionTakeoverLock.tryAcquire(sessionId, instanceId, lockLeaseMs)) { ... }
  CancelHandle existing = runningExecutions.putIfAbsent(sessionId, handle);
  ...
  ThreadLocalTenantResolver.set(tenantId);
  ...
  if (actorRuntime.isEnabled()) { AgentActor actor = actorRuntime.createActor(...); ... }
  autoBindTeam(agentModel, sessionId, request.getAgentName());
  ...
  releaseLockQuietly(sessionId, instanceId);

  // resumeSession (line 2139, 2147-2149, 2159-2164, 2182, 2189, 2196, 2215) — 完全同构
  precheckTeamDeclarations(agentModel);
  IPathAccessChecker effectivePathAccessChecker = resolvePerAgentPathChecker(agentModel);
  ensureSessionMailbox(sessionId);
  IAgentExecutor executor = resolveExecutor(agentModel, effectiveToolAccessChecker, effectivePathAccessChecker);
  ...
  if (!sessionTakeoverLock.tryAcquire(sessionId, instanceId, lockLeaseMs)) { ... }

  // restoreSession (line 2322, 2326-2328, 2338-2343, 2359, 2366, 2373, 2392) — 完全同构
  precheckTeamDeclarations(agentModel);
  IPathAccessChecker effectivePathAccessChecker = resolvePerAgentPathChecker(agentModel);
  ensureSessionMailbox(sessionId);
  IAgentExecutor executor = resolveExecutor(agentModel, effectiveToolAccessChecker, effectivePathAccessChecker);
  ```
- **严重程度**: P2
- **现状**: 三个公共/包级入口方法（`doExecute`、`resumeSession`、`restoreSession`）每个都包含 ~200 行几乎逐行相同的设置 + 清理序列。grep 验证每条标志线均出现在三处（行号 1824/2139/2322、1853/2147/2326、1873/2159/2338 等）。
- **风险**:
  - 同一并发-清理协议在三处独立维护，任一处修改（如新增 actor 生命周期点、调整锁顺序）需三处同步修改，否则协议漂移；
  - 已发生过的演化（plan 197 / 214 / 218 / 221 / 231）每轮都需要在三个方法分别应用补丁，源代码注释中可见大量"see doExecute for full rationale"反向指向，说明维护者已意识到重复但未抽取；
  - 后续若新增第 4 个入口（例如 `interruptSession` / `migrateSession`），复制-粘贴会进一步扩散。
- **建议**: 提取协作方法或模板方法，例如：
  - `<T> CompletableFuture<T> withSessionExecutionLock(String sessionId, AgentModel model, AgentMessageRequest request, Supplier<CompletionStage<T>> body)`，封装 takeover lock + putIfAbsent + supplyAsync + tenant + actor + team bind + cleanup；
  - 或提取 `ExecutionContextPrime.primeForEntry(...)`，封装 `precheckTeamDeclarations` + checker 解析 + mailbox + executor resolve，返回一个值对象，三入口仅注入 user message 差异；
  - 三入口最终留下各自特有逻辑（doExecute 追加 user message；resumeSession 重置 denialLedger；restoreSession 消费 checkpoint 一致性）。
- **信心水平**: 确定
- **误报排除**: 这不是"三处方法长得像但语义不同所以不能合并"——三处的设置/清理序列语义完全一致（同一并发协议、同一 actor 生命周期、同一 team 自动绑定），仅中间的业务步骤不同。模板方法/帮助方法能精确抽取共用部分，保留各自的业务差异。
- **复核状态**: 未复核

---

### [维度02-3] Plan 模型类层级在两个包内平行存在，model.AgentPlanModel 为生产代码从不填充的孤儿类型

- **文件**:
  - `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/model/AgentPlanModel.java:1-9`（手写空壳）
  - `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/model/_gen/_AgentPlanModel.java:1-475`（生成基类，与当前 xdef 不一致）
  - `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/plan/model/AgentPlanModel.java:1-9`（另一份同名类）
  - `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/plan/model/_gen/_AgentPlanModel.java:1-277`（另一份同名生成基类）
  - `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/plan/model/AgentPlan.java:1-9`（实际 DSL 根 bean）
  - `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/AgentExecutionContext.java:7,24,103,107`（消费 `model.AgentPlanModel`）
- **证据片段**:
  ```java
  // model/AgentPlanModel.java（第一份）
  package io.nop.ai.agent.model;
  import io.nop.ai.agent.model._gen._AgentPlanModel;
  public class AgentPlanModel extends _AgentPlanModel { ... }

  // plan/model/AgentPlanModel.java（第二份，完全同名！不同包）
  package io.nop.ai.agent.plan.model;
  import io.nop.ai.agent.plan.model._gen._AgentPlanModel;
  public class AgentPlanModel extends _AgentPlanModel { ... }

  // plan/model/AgentPlan.java（实际由 xdef 生成，bean-package=io.nop.ai.agent.plan.model）
  package io.nop.ai.agent.plan.model;
  import io.nop.ai.agent.plan.model._gen._AgentPlan;
  public class AgentPlan extends _AgentPlan { ... }

  // AgentExecutionContext.java（消费第一份孤儿类）
  import io.nop.ai.agent.model.AgentPlanModel;
  private AgentPlanModel plan;        // line 24
  public AgentPlanModel getPlan() { ... }   // line 103
  public void setPlan(AgentPlanModel plan) { ... }  // line 107
  ```
- **严重程度**: P2
- **现状**: 同一概念"Agent Plan 模型"在模块内存在三个不同的类层级：
  1. **`io.nop.ai.agent.plan.model.AgentPlan`** —— `agent-plan.xdef` 当前 schema 的根 `<plan>` 元素所生成，是 XML DSL 实际加载得到的类。
  2. **`io.nop.ai.agent.plan.model.AgentPlanModel`** —— record-mapping `Markdown_to_AgentPlanModel` 的目标类，是 markdown 视角的简化模型。
  3. **`io.nop.ai.agent.model.AgentPlanModel`** —— 被 `AgentExecutionContext.plan` 字段和 `LlmCompletionJudge` 引用，但生产代码中 **`setPlan` 从未被调用**（grep 验证仅测试代码 `TestLlmCompletionJudge.java:58` 调用），即运行时 `plan` 字段恒为 null，且 `resolveExecutor` 中 `"plan".equals(mode)` 直接抛 `UnsupportedOperationException`。这是 plan 执行模式未实现遗留的孤儿类层级。
- **风险**:
  - 模块边界混乱：plan 概念的模型分散在 `model/` 和 `plan/model/` 两个包，新增维护者难以判断该改哪里；
  - `model.AgentPlanModel` 是死代码（生产中从不被实例化、不被填充），但仍被 `AgentExecutionContext` 作为字段类型引用，造成"运行时有 plan 字段但永远 null"的错觉；
  - `_gen/` 中存在两套同名 stale 生成类（均非当前 xdef 严格产物），违反"`_gen/` 是当前 schema 投影"的隐含约定；
  - record-mapping 中 `Markdown_to_AgentPlanTaskModel` 使用 `overview` / `notes` 字段，但当前 `_AgentPlanTaskModel` 生成类并无这两个字段（schema 与 mapping 漂移）。
- **建议**:
  - 决策单一权威 plan 模型：保留 `plan.model.AgentPlan`（xdef 投影）+ `plan.model.AgentPlanModel`（markdown 投影），删除 `model.AgentPlanModel` 及其 7 个 `_gen` 兄弟；
  - `AgentExecutionContext.plan` 字段类型迁移到 `plan.model.AgentPlan`（或暂时移除该字段，待 plan 模式实现时重新引入）；
  - `LlmCompletionJudge` 同步迁移；
  - 校准 record-mapping 与 `_gen` 的字段一致性。
- **信心水平**: 很可能
- **误报排除**: 这不是"xdef 与代码生成预期不一致的 _gen 问题，不能审计"——审计对象是**手写**的 `AgentPlanModel.java` 空壳、**手写**的 `AgentExecutionContext.plan` 字段、**手写**的 `setPlan` 调用缺失，以及 plan 模型被分裂到两个包这一**架构层面**的边界问题；生成代码本身只是症状，不是发现对象。
- **复核状态**: 未复核

---

### [维度02-4] ReActAgentExecutor 路径作用域解析方法簇（~200 行）与 ReAct 循环无共享状态，可独立为 PathScopeResolver

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/ReActAgentExecutor.java:2857-3095`
- **证据片段**:
  ```java
  // 7 个无状态路径解析方法构成一个内聚簇
  private List<ChatToolDefinition> buildToolDefinitions(AgentModel agentModel) { ... } // 2857
  private Set<String> computeEffectiveAllowedTools(AgentModel agentModel, AgentExecutionContext ctx) { ... } // 2896
  private File resolveWorkDir(AgentModel agentModel) { ... } // 2928
  private Set<String> computeEffectivePathRoots(AgentModel agentModel, AgentExecutionContext ctx) { ... } // 2966
  private Set<String> computeOwnDeclaredPathRoots(AgentModel agentModel) { ... } // 3006
  private List<PathRuleModel> computeEffectivePathRules(AgentModel agentModel, AgentExecutionContext ctx) { ... } // 3038
  private boolean isUnderAnyRoot(String normalizedPath, Set<String> roots) { ... } // 3076
  private static String resolveAbsolute(String rawPath, File baseDir) { ... } // 2823
  ```
- **严重程度**: P3
- **现状**: `ReActAgentExecutor` 共 3327 行，其中 `execute()` 主循环及其 30+ 直接辅助方法确实围绕 ReAct 循环内聚，不计为问题。但路径作用域解析方法簇共 8 个方法 ~200 行，形成一个独立的纯计算子域，与 ReAct 循环的执行状态完全无耦合（无读写 `this.*` 字段）。
- **风险**:
  - 较轻：增加了 `ReActAgentExecutor` 的视认负担；
  - 路径策略变更需在大文件中搜索分散的方法；
  - 单元测试路径策略时必须实例化整个 `ReActAgentExecutor.Builder`（50+ 参数），成本远高于测试一个独立的纯函数类。
- **建议**: 提取为 `engine/PathScopeResolver`（或 `security/AgentPathScopeResolver`，因路径作用域本质是安全概念），仅暴露 `resolve(AgentModel, AgentExecutionContext) -> PathScope`；`ReActAgentExecutor` 通过依赖注入持有该 resolver。
- **信心水平**: 有趣的猜测
- **误报排除**: 这不是"大文件就是问题"——已经明确说明 `execute()` 主循环及其辅助方法不计为问题（内聚于 ReAct 执行）。本发现明确指出的是其中可分离的、与主循环无状态耦合的路径解析簇。
- **复核状态**: 未复核

---

## 未计为发现的检查结论

| 文件 | 行数 | 评估结论 |
|------|------|---------|
| `team/scheduler/TeamTaskSchedulerDaemon.java` | 1097 | 单一职责：周期扫描 + CAS 认领 + 派发团队任务 |
| `team/DbTeamManager.java` | 829 | 单一职责：DB 实现的 `ITeamManager` |
| `team/flow/TeamTaskFlowOrchestrator.java` | 792 | 单一职责：编排团队任务流的图构建与执行 |
| `runtime/recovery/ScheduledRecoveryManager.java` | 552 | 单一职责：周期 sweep 守护 |
| `runtime/InMemoryActorRuntime.java` | 520 | 单一职责：Actor 运行时容器实现 |
| `message/DBMessageService.java` | 497 | 单一职责：DB 实现的 `IMessageService` |
| `tool/CallAgentExecutor.java` | 477 | 单一职责：`call-agent` 工具执行器 |
| `team/DbTeamTaskStore.java` | 466 | 单一职责：DB 实现的 `ITeamTaskStore` |
| `reliability/DBCheckpointManager.java` | 458 | 单一职责：DB 实现的 `ICheckpointManager` |
| `engine/AgentToolExecuteContext.java` | 441 | 数据载体类 |
| `session/DBSessionStore.java` | 420 | 单一职责：DB 实现的 `ISessionStore` |
| `security/DockerSandboxBackend.java` | 411 | 单一职责：Docker CLI 后端实现 |

**其他检查项结论**：
- **`engine/` 包是否混入持久化逻辑**：否。grep `java.sql.*`/`javax.sql.*`/`PreparedStatement`/`DataSource`/`INSERT INTO`/`SELECT ... FROM` 在 `engine/` 包零命中。
- **`security/` 包是否混入业务逻辑**：否。`security/` 包 73 个文件全部围绕审批/拒绝/审计/沙箱/路径访问/工具访问/租户隔离/权限矩阵展开。
- **`_gen/` 边界**：手写子类正确继承 `_gen._*` 基类且只添加业务方法，未在 `_gen/` 内手写代码。
- **`precompile/` 误放手写代码**：`precompile/` 仅含 `gen-agent-xdsl.xgen` 一个 codegen 脚本（4 行），无手写 Java 误放。
- **`app.orm.xml` 是否被手改**：不带 `_` 前缀，是源 ORM 模型，无被生成覆盖风险。
- **跨职责 DAO/SQL 检查**：14 个 `Db*` 类均与其实现的接口同包，符合 Nop 平台"接口与实现同包"模式。

## 维度复核结论

待独立复核子 agent 输出。

## 最终保留项

待复核完成后填写。
