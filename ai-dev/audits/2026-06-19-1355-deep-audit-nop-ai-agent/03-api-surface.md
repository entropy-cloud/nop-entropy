# 维度 03（API 表面积）+ 维度 05（生成管线完整性）— nop-ai-agent

## 维度 03 发现

### [维度03-1] IAgentEngine 多个 public 方法在全仓库无任何 production 调用方

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/IAgentEngine.java:9,41,185` 以及 `DefaultAgentEngine.java:1784,1647`
- **证据片段**:
  ```java
  // IAgentEngine.java
  AgentMessageAck sendMessage(AgentMessageRequest request);              // line 9
  default AgentExecStatus getSessionStatus(String sessionId) {           // line 41
      throw new UnsupportedOperationException("getSessionStatus requires Phase 2");
  }
  default SessionRestoreSummary restorePendingSessions(String approver, String reason) {  // line 185
      throw new UnsupportedOperationException(...);
  }
  // DefaultAgentEngine.java line 1784
  @Override public AgentMessageAck sendMessage(AgentMessageRequest request) {
      String sessionId = resolveSessionId(request.getSessionId());
      CompletableFuture<AgentExecutionResult> future = doExecute(request, sessionId);
      future.exceptionally(ex -> { LOG.error(...); return null; });
      return new AgentMessageAck(sessionId);
  }
  ```
- **严重程度**: P3
- **现状**: 这三个 public 方法虽然在 `IAgentEngine` 上声明、在 `DefaultAgentEngine` 中有完整实现，但全仓库 `grep` 验证：唯一调用方都在 `src/test/java` 下。`sendMessage` 实际只是 `execute()` 的同步 fire-and-forget 包装；`restorePendingSessions` 在 javadoc 中明确说"opt-in, not lifecycle-coupled"，但没有任何 main code 路径调用它。
- **风险**: 这是"看起来是契约、实际无消费方"的 API 表面积。维护者修改 `IAgentEngine` 时会把这些方法当作有人用而保留，但实际上它们的语义未被任何 production 路径验证。
- **建议**: 在 IAgentEngine 上为这三个方法添加契约说明；或将 `sendMessage` 标记为 convenience 方法。`restorePendingSessions` 的协议描述应当与某个实际调用方配对。
- **信心水平**: 确定（grep 验证完毕，主代码无调用方）
- **误报排除**: 这不是"未被引用的私有方法"。这些是 public interface 上的方法，对外契约的一部分。
- **复核状态**: 未复核

---

### [维度03-2] `AgentExecutionResult.finalMessage` 字段在生产路径恒为 null，但暴露在公共 API 上

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/AgentExecutionResult.java:13,21,61`
- **证据片段**:
  ```java
  // line 13
  private final String finalMessage;
  // line 43-55: production 构造入口
  public static AgentExecutionResult fromContext(AgentExecutionContext ctx) {
      long durationMs = System.currentTimeMillis() - ctx.getStartTimeMs();
      return new AgentExecutionResult(
              ctx.getStatus(),
              null,                        // <-- finalMessage 恒为 null
              ctx.getMessages(),
              ctx.getCurrentIteration(),
              ctx.getTokensUsed(),
              durationMs,
              ctx.getLastError(),
              ctx.getSessionId()
      );
  }
  // line 61: 公共 getter 暴露
  public String getFinalMessage() { return finalMessage; }
  ```
- **严重程度**: P3
- **现状**: `AgentExecutionResult` 公开 8 个 getter，其中 `getFinalMessage()` 在生产路径下永远返回 null，因为唯一的 production 构造入口 `fromContext` 硬编码 `null`。生产消费者（如 `CallAgentExecutor.extractFinalMessage`）不得不自己从 `getMessages()` 倒序找最后一个 `ChatAssistantMessage`。
- **风险**: 公共 API 上暴露了一个"看起来有用、实际总是 null"的字段。集成方读取 `result.getFinalMessage()` 会拿到 null，需要降级到 `getMessages()` 自己抽取。
- **建议**: 要么在 `fromContext` 中实现 finalMessage 的提取逻辑，要么 deprecate `getFinalMessage()` 并在 javadoc 中明确。
- **信心水平**: 确定
- **误报排除**: 问题在 contract 层：production 路径下公共 getter 永远返回 null。
- **复核状态**: 未复核

---

### [维度03-3] IAgentEngine 同一接口内 sync/async 失败语义不一致

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/IAgentEngine.java:9,11,37,45,75,114,185`
- **证据片段**:
  ```java
  AgentMessageAck sendMessage(AgentMessageRequest request);                       // sync, fire-and-forget
  CompletableFuture<AgentExecutionResult> execute(AgentMessageRequest request);   // async
  default CompletableFuture<String> forkSession(...) { throw new UnsupportedOperationException(...); }
  default CompletableFuture<Void> cancelSession(...) { throw new UnsupportedOperationException(...); }
  default CompletableFuture<AgentExecutionResult> resumeSession(...) { throw new UnsupportedOperationException(...); }
  default CompletableFuture<AgentExecutionResult> restoreSession(...) { throw new UnsupportedOperationException(...); }
  default SessionRestoreSummary restorePendingSessions(String approver, String reason) { ... }  // sync!
  ```
- **严重程度**: P3
- **现状**: 同一个 `IAgentEngine` 接口上 7 个方法混用了三种失败语义：(1) 5 个方法返回 `CompletableFuture`，失败通过 future 异常传播；(2) `sendMessage` 同步返回 ack，执行失败在内部吞掉（永远不抛）；(3) `restorePendingSessions` 同步阻塞返回。
- **风险**: 集成方难以建立稳定的心智模型——同一个接口的调用有的要 `.get()` 等待 future、有的要同步捕获异常、有的 failure 永远不抛。
- **建议**: 在 IAgentEngine 的 javadoc 中添加"Method Categories"段落，明确划分三类；或把 `restorePendingSessions` 也改成返回 `CompletableFuture`。
- **信心水平**: 很可能
- **误报排除**: 问题是同一个接口内 failure 传播机制有三种且没有统一文档。
- **复核状态**: 未复核

---

### [维度03-4] IAgentEngine.execute 与 IAgentExecutor.execute 的 future 类型不对称

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/IAgentEngine.java:11` 与 `IAgentExecutor.java:23`
- **证据片段**:
  ```java
  // IAgentEngine.java line 11
  CompletableFuture<AgentExecutionResult> execute(AgentMessageRequest request);

  // IAgentExecutor.java line 23 (engine 委托给 executor)
  CompletionStage<AgentExecutionResult> execute(AgentExecutionContext ctx);
  ```
- **严重程度**: P3
- **现状**: `IAgentEngine.execute` 返回具体的 `CompletableFuture<>`，而它委托的 `IAgentExecutor.execute` 返回更抽象的 `CompletionStage<>`。
- **风险**: 实现方实际返回 `CompletableFuture`，赋值兼容。但接口契约不对称——engine 想保证 `.orTimeout()` 能力，executor 不保证。
- **建议**: 统一到 `CompletableFuture`，让所有实现方都保证可超时能力。
- **信心水平**: 有趣的猜测
- **误报排除**: `CompletionStage` 缺少 `orTimeout` / `join` / `complete` 等方法，engine 用了这些方法，而委托契约却没有保证。
- **复核状态**: 未复核

---

### [维度03-5] Tool executor 公共错误处理 helper 在 5-7 个类中逐字复制

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/tool/{CallAgentExecutor,SendMessageExecutor,TeamSendMessageExecutor,TeamTaskCreateExecutor,TeamTaskUpdateExecutor,TeamStatusExecutor,TeamExecuteFlowExecutor}.java`
- **证据片段**:
  ```java
  // TeamSendMessageExecutor.java:243-262
  private static CompletableFuture<AiToolCallResult> honestDenied(int callId,
                                                                  String toolName,
                                                                  String action,
                                                                  TeamAclDecision decision) {
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("allowed", false);
      body.put("toolName", toolName);
      body.put("action", action);
      body.put("resolvedRole", decision.getResolvedRole() != null
              ? decision.getResolvedRole().name() : null);
      body.put("reason", decision.getReason());
      AiToolCallResult result = new AiToolCallResult();
      result.setId(callId);
      result.setStatus("success");
      AiToolOutput output = new AiToolOutput();
      output.setBody(JSON.stringify(body));
      result.setOutput(output);
      return CompletableFuture.completedFuture(result);
  }
  // 同一段代码逐字出现在 TeamTaskCreateExecutor.java:213 / TeamTaskUpdateExecutor.java:282
  // / TeamStatusExecutor.java:169 / TeamExecuteFlowExecutor.java:306
  ```
- **严重程度**: P3
- **现状**: `honestDenied(...)` 在 5 个 team tool executor 中逐字复制。`honestNotEnabled(...)` 在同样 5 个文件中逐字复制。`fail(int callId, String message)` 在 7 个 executor 中各自重定义。只有 3 个 memory tool 通过 `AbstractMemoryToolExecutor` 正确共享了 scaffolding。
- **风险**: 未来任何对"honest result"格式的修改需要同步改 5-7 处，漏一处就会出现工具间不一致——这恰恰是 tool/ 层最容易被 LLM 注意到的不一致。
- **建议**: 抽出一个 `TeamToolResults`（或 `ToolResults`）工具类，集中 `honestDenied`、`honestNotEnabled`、`fail`、`honestCasResult` 等 static factory。参照 `AbstractMemoryToolExecutor`。
- **信心水平**: 确定
- **误报排除**: 这里的重复是公共 API 表面的实现层面复制，且有 5-7 处——足够触发"修改时易遗漏"的真实维护风险。3 个 memory tool 已经证明可以抽出基类。
- **复核状态**: 未复核

---

### 维度 03 检查项合规确认

1. **`@BizModel`/`@BizQuery`/`@BizMutation`**: **0**（符合非标准 AI 模块定位）。✓
2. **10 个 tool executor 的 `IToolExecutor` 契约一致性**: 全部 `implements IToolExecutor`，签名完全一致。✓
3. **10 个 tool executor 的错误返回模式**: 全部使用 `AiToolCallResult.errorResult` + `status="success"` + `AiToolOutput`。✓
4. **`IAgentEngine` 5 个 async 方法的 `CompletableFuture` 泛型**: 全部正确。✓
5. **`Object` 返回值的合理性**: 5 处 Object 返回值都是异构 payload 容器或平台契约，合理。✓
6. **`Map<String, Object>` 滥用检查**: tool executor 的 `resolveArguments` 是 LLM 提供的 JSON 工具参数，合理。✓

---

## 维度 05 发现

### [维度05-1] codegen 脚本与 pom 配置链路正确（合规确认）

- **文件**: `nop-ai/nop-ai-agent/precompile/gen-agent-xdsl.xgen:1-4`、根 `pom.xml:323-412`、`nop-ai/nop-ai-agent/pom.xml:53-58`
- **证据片段**:
  ```xml
  <!-- precompile/gen-agent-xdsl.xgen -->
  <c:script>
      codeGenerator.renderModel('/nop/schema/ai/agent-plan.xdef','/nop/templates/xdsl', '/',$scope);
      codeGenerator.renderModel('/nop/schema/ai/agent.xdef','/nop/templates/xdsl', '/',$scope);
  </c:script>
  ```
- **严重程度**: P3（合规确认）
- **现状**: codegen 管线**配置正确**：(1) `gen-agent-xdsl.xgen` 正确调用 `renderModel` 渲染两个 xdef；(2) exec-maven-plugin 通过根 pom 的 pluginManagement 继承获得 `precompile` execution；(3) 本模块 pom 仅声明插件、不重写。本模块**没有** codegen Maven phase 配置缺失问题。
- **复核状态**: 未复核

---

### [维度05-2] `model/_gen/` 下 7 个 plan-命名的"冻结孤儿"生成类，被引擎主代码使用但不再被任何 xdef 重新生成

- **文件**:
  - `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/model/_gen/_AgentPlanModel.java`（475 行，git 最后修改 2026-03-13）
  - 同目录下 `_AgentPlanDecision.java`、`_AgentPlanError.java`、`_AgentPlanNote.java`、`_AgentPlanPhaseModel.java`、`_AgentPlanQuestion.java`、`_AgentPlanTaskModel.java`（共 7 个）
- **证据片段**:
  ```java
  // _AgentPlanModel.java line 11-21（声称从 agent-plan.xdef 生成）
  public abstract class _AgentPlanModel extends io.nop.core.resource.component.AbstractComponentModel {
      private java.time.LocalDateTime _createdAt ;
      private KeyedList<io.nop.ai.agent.model.AgentPlanDecision> _decisions = ...;
      private KeyedList<io.nop.ai.agent.model.AgentPlanError> _errors = ...;
      // ... 当前 agent-plan.xdef 不存在 AgentPlanModel 这个 xdef:name 声明
  }
  // AgentExecutionContext.java:7
  import io.nop.ai.agent.model.AgentPlanModel;          // 引擎运行时持有的 plan 数据结构
  // LlmCompletionJudge.java:6 + line 168-170
  AgentPlanModel plan = ctx.getPlan();
  if (plan != null && plan.getGoal() != null && !plan.getGoal().trim().isEmpty()) { ... }
  ```
- **严重程度**: P1
- **现状**: 这 7 个文件标称"generate from agent-plan.xdef"，但当前 `agent-plan.xdef` 的 schema 中**没有** `AgentPlanModel` 这个 bean 定义（root 是 `AgentPlan`，bean-package 是 `io.nop.ai.agent.plan.model`）。git 历史显示这些文件来自 2026-03-13 的重构 commit，那时 xdef 的 root 曾经短暂叫 `AgentPlanModel`。之后所有 xdef 重构都没有触及这 7 个文件。关键问题：**这 7 个"生成文件"实际上是引擎运行时的核心数据结构**——`AgentExecutionContext.plan: AgentPlanModel`、`LlmCompletionJudge.resolveGoal()` 都直接使用。这些类既不在当前 schema 的生成范围内，又受 AGENTS.md "Hard Stop: Generated Files" 规则保护，形成死锁。
- **风险**: 核心契约漂移：(1) xdef 是 source of truth，但被主代码使用的 `AgentPlanModel` 与 xdef 已经脱节 3 个月以上；(2) `io.nop.ai.agent.plan.model.AgentPlan`（当前 xdef 的合法产物）和 `io.nop.ai.agent.model.AgentPlanModel`（运行时实际使用）是**两个并行存在的 plan 模型**；(3) 任何修改 `agent-plan.xdef` 的开发者都不会意识到他们的修改无法传播到运行时实际使用的 `model.AgentPlanModel`。
- **建议**: 二选一：(A) 把所有消费者从 `io.nop.ai.agent.model.AgentPlanModel` 迁移到 `io.nop.ai.agent.plan.model.AgentPlan`，并删除 `model/_gen/_AgentPlan*.java` 及其手写保留层；(B) 把 `agent.xdef` 加回这些 bean 定义（让它们重新被 codegen 管理），或者把这些文件从 `_gen/` 移出手写区并明确标注"非生成"。
- **信心水平**: 确定（git 时间线、grep 验证、主代码消费者三重证据）
- **误报排除**: AGENTS.md 明确说生成文件不应作为审计对象"除非生成模板/模型本身有误"——这里正是模型层面的问题：xdef 缺少这些 bean 定义。
- **复核状态**: 未复核

---

### [维度05-3] `plan/model/_gen/` 下 5 个 plan-命名的孤儿生成类，无任何 main 代码消费者（纯死代码）

- **文件**:
  - `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/plan/model/_gen/_AgentPlanModel.java`（277 行）
  - 同目录下 `_AgentPlanNote.java`、`_AgentPlanNoteModel.java`、`_AgentPlanQuestion.java`、`_AgentPlanDecision.java`（共 5 个）
  - 以及对应的手写保留层：`plan/model/AgentPlanModel.java`、`AgentPlanNote.java`、`AgentPlanNoteModel.java`、`AgentPlanQuestion.java`、`AgentPlanDecision.java`（共 5 个 9-行空壳）
- **证据片段**:
  ```java
  // plan/model/_gen/_AgentPlanModel.java line 11-26（声称从 agent-plan.xdef 生成，但是 SIMPLER 字段集）
  public abstract class _AgentPlanModel extends io.nop.core.resource.component.AbstractComponentModel {
      private KeyedList<io.nop.ai.agent.plan.model.AgentPlanNoteModel> _notes = KeyedList.emptyList();
      private java.lang.String _overview ;
      private java.lang.String _path ;
      private AgentExecStatus _planStatus ;
      private KeyedList<io.nop.ai.agent.plan.model.AgentPlanTaskModel> _tasks = KeyedList.emptyList();
      private java.lang.String _title ;
  }
  // 手写保留层 plan/model/AgentPlanModel.java（9 行空壳）
  public class AgentPlanModel extends _AgentPlanModel{ public AgentPlanModel(){ } }
  ```
- **严重程度**: P2
- **现状**: `plan/model/_gen/` 下 21 个 `_*.java` 文件中，16 个对应 `agent-plan.xdef` 当前 schema（合法生成产物）；**5 个**来自 `agent-plan.xdef` 最早的版本，无任何 main 业务消费者（连 record-mapping 都不实例化它们）。
- **风险**: 5+5=10 个文件纯死代码，占用代码搜索注意力、误导维护者。
- **建议**: 删除这 10 个文件（5 个 `_gen/_*.java` + 5 个空壳子类），清理 codegen 输出的死代码。删除前先跑全模块测试确认无测试引用。
- **信心水平**: 确定
- **误报排除**: 与 05-2 不同——05-2 的 7 个孤儿有主代码消费者（运行时核心数据结构），是 P1 漂移；本条的 5 个孤儿完全无消费者，是 P2 死代码清理。
- **复核状态**: 未复核

---

### [维度05-4] record-mapping 加载器的 `optional="true"` + pom test-scope 依赖构成"开发时有效、生产时静默禁用"的隐式契约

- **文件**:
  - `nop-ai/nop-ai-agent/pom.xml:45-49`（`nop-record-mapping` 声明为 `<scope>test</scope>`）
  - `nop-ai/nop-ai-agent/src/main/resources/_vfs/nop/core/registry/agent-plan.register-model.xml:7-8`（main 资源）
- **证据片段**:
  ```xml
  <!-- pom.xml line 45-49 -->
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-record-mapping</artifactId>
      <scope>test</scope>
  </dependency>
  <!-- agent-plan.register-model.xml line 7-8（main 资源） -->
  <loader fileType="agent-plan.md" mappingName="agent-plan.Markdown_to_AgentPlanModel" optional="true"
          class="io.nop.record_mapping.md.MarkdownDslResourceLoaderFactory"/>
  ```
- **严重程度**: P3
- **现状**: `agent-plan.register-model.xml` 是 main 资源，引用类 `MarkdownDslResourceLoaderFactory`（来自 `nop-record-mapping`）。但该模块在本 pom 中声明为 `<scope>test</scope>`——只在测试 classpath 可见。`optional="true"` 让平台在类加载失败时 WARN + 跳过，所以生产启动时会 log 一条 WARN 然后跳过——`.md` plan 文件类型在生产中实际**无加载器**。
- **风险**: (1) 部署层的隐式失效——markdown 驱动的 plan 作者路径在生产中静默不可用；(2) 维护耦合——任何人把 scope 改成默认会引入隐式行为变化。
- **建议**: 在 `agent-plan.register-model.xml` 上方加注释明确 scope 约束；或如果 `.md` 加载在生产中确实是预期特性，则把 `nop-record-mapping` 提升为 compile scope。
- **信心水平**: 确定
- **误报排除**: 这不是 10-01（hyphen bug）的重复——本条从 pom scope 与 register-model optional 的跨文件耦合角度独立确认。
- **复核状态**: 未复核

---

### 维度 05 检查项合规确认

1. **`app.orm.xml`**: 由维度 04-01 处理。✓
2. **手写子类继承正确性**: 全部为 9-行空壳，符合平台"retention stub"模式。✓
3. **`_gen/` 文件未被手写修改**: 36 个文件全部包含生成器标记。✓
4. **生成产物时间戳一致性（合法生成部分）**: codegen pipeline 对合法产物正常工作。✓
5. **Maven codegen phase 配置完整性**: 无缺失。✓
6. **`record-mapping` 的 `optional="true"` 平台处理**: 平台行为符合预期。✓

## 维度复核结论

待独立复核子 agent 输出。

## 最终保留项

待复核完成后填写。

---

注：本文件合并了维度 03 和维度 05，因 nop-ai-agent 是非标准 AI 子系统模块，两者检查对象高度重叠。
