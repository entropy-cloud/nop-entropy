# 维度02：模块职责与文件边界

## 已检查范围（无问题项）
`_gen/` 目录无手写代码、`_` 前缀文件无手编辑、`app.orm.xml` 是手写源（合规）、小包内聚良好、`plan/` 作命名空间容器合理。

## 第 1 轮（初审）

### [维度02-01] ReActAgentExecutor 单类聚合 13+ 项横切职责（2005 行）
- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/ReActAgentExecutor.java:112-2005`
- **证据片段**:
  ```java
  // 构造器注入 26 个协作者（行 149-173），覆盖完全不相关的 13 个关注点：
  private final IChatService chatService;            // ReAct 主循环
  private final IToolManager toolManager;            // 工具调度
  private final IPermissionProvider permissionProvider;   // L1 权限
  private final IToolAccessChecker toolAccessChecker;     // L1 工具访问
  private final IPathAccessChecker pathAccessChecker;     // L1 路径访问
  private final IAuditLogger auditLogger;                 // 审计
  private final IHookRegistry hookRegistry;               // 生命周期钩子
  private final IContextCompactor contextCompactor;       // 压缩
  private final IContentGuardrail contentGuardrail;       // 内容护栏
  private final IApprovalGate approvalGate;               // L3 审批门
  private final IDenialLedger denialLedger;               // L3 拒绝账本
  private final IPostDenialGuard postDenialGuard;         // L3 盲重试守卫
  private final ICheckpointManager checkpointManager;     // 检查点
  private final ISessionStore sessionStore;               // 执行内持久化
  ```
  以及行 1617-1816 的父权限钳制逻辑（~200 行纯函数）。
- **严重程度**: P1
- **现状**: 单个 2005 行类同时承担 ReAct 主循环、工具并行调度、L1/L2/L3 六条安全门控、压缩触发与执行、三类检查点记录、执行内会话持久化、输入/输出护栏、9 点生命周期钩子、completion judge 集成、事件发布、错误恢复、talent/skill 咨询、父权限钳制等 13+ 项横切职责。
- **风险**: 任何对安全门控、检查点策略或压缩逻辑的修改都会触及与 ReAct 核心循环相同的文件/方法。`dispatchLoop`（行 759-919）单段就内联了 6 条 deny 路径的 audit+event+error+threshold 处理；测试覆盖任一分支都需要实例化 26 个依赖。维护与回归风险随安全层叠加持续放大。
- **建议**: 至少抽出两块内聚、无 executor 状态依赖的逻辑：(a) `EffectivePermissionResolver`（封装行 1617-1816 的父权限钳制）；(b) `DispatchSecurityGate`（封装行 777-916 的 L1/L2/L3 门控）。检查点记录与执行内持久化亦可抽 `ExecutionProgressRecorder`。
- **信心水平**: 确定
- **误报排除**: 非生成文件（手写）；非"大文件即问题"——已逐项列举 13 项职责并指出可抽出的 ~500 行内聚块。
- **复核状态**: 未复核

### [维度02-02] `model/` 包残留重复且已孤立的 AgentPlan* 模型层级
- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/model/AgentPlanModel.java:1-9`（及同包 7 个 9 行存根 + `model/_gen/_AgentPlan*.java` 共 8 个生成文件）
- **证据片段**:
  ```java
  // model/AgentPlanModel.java —— 9 行空存根
  package io.nop.ai.agent.model;
  import io.nop.ai.agent.model._gen._AgentPlanModel;
  public class AgentPlanModel extends _AgentPlanModel{
      public AgentPlanModel(){
      }
  }
  ```
  ```java
  // engine/AgentExecutionContext.java:21 —— plan 字段在生产中从不被填充
  private AgentPlanModel plan;
  ```
  全工程 `.setPlan(` 仅 1 处调用：`src/test/.../TestLlmCompletionJudge.java:57`（测试）；`LlmCompletionJudge.resolveGoal:167-170` 读取 `ctx.getPlan()` 但生产中恒为 null，为死代码。
- **严重程度**: P2
- **现状**: 存在两套并行的 plan 模型层级——`model/`（旧）与 `plan/model/`（新，绑定当前 xdef）。`model/` 那套是 xdef 的 `bean-package` 迁移后遗留的孤儿：再生成不会更新它们，且唯一引用方（`AgentExecutionContext.plan`）在生产中从不被赋值。
- **风险**: 误导后续开发者——同名类在两个包各有一份，import 时极易选错。`LlmCompletionJudge` 读取死字段掩盖了"plan goal 从未被使用"的真实功能缺口。~1600 行孤儿生成代码 + 7 个存根增加模块认知负担。
- **建议**: 删除 `model/AgentPlan*.java`（7 个存根）+ `model/_gen/_AgentPlan*.java`（8 个生成文件）；移除 `AgentExecutionContext.plan` 字段及其 getter/setter；移除 `LlmCompletionJudge.resolveGoal` 的死分支。若 plan goal 功能计划接入，应改用规范化的 `plan/model/AgentPlan`。
- **信心水平**: 确定
- **误报排除**: 手写的 7 个 9 行存根是合法审计目标（非 `_` 前缀）。已全工程搜索 `setPlan` 调用确认生产代码零引用。
- **复核状态**: 未复核

### [维度02-03] `NopAiAgentException`（模块级异常）错置于 `engine` 子包
- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/NopAiAgentException.java:1-12`
- **证据片段**:
  ```java
  package io.nop.ai.agent.engine;   // 仅 12 行，模块级异常
  public class NopAiAgentException extends RuntimeException {
      public NopAiAgentException(String message) { super(message); }
      public NopAiAgentException(String message, Throwable cause) { super(message, cause); }
  }
  ```
  被 16 个类跨 6 个包导入：`session`(5) / `reliability`(5) / `security`(1) / `message`(2) / `skill`(2) / `engine`(1)。
- **严重程度**: P3
- **现状**: 按 `AGENTS.md` 约定，模块级异常应置于模块根包；此处放在 `engine` 功能性子包内，迫使所有其它子包反向 import `engine`。
- **风险**: 轻度包依赖扭曲（`security`/`session`/`reliability` 等本不应依赖 `engine`），新子包误以为必须依赖 engine 才能抛异常。功能无影响。
- **建议**: 迁至 `io.nop.ai.agent.NopAiAgentException`（模块根包），更新 16 处 import。属低风险机械重构。
- **信心水平**: 确定
- **误报排除**: 非 `_` 生成文件；非 Nop 框架强制约束，但与 AGENTS.md 明示的"模块级异常类"约定一致。
- **复核状态**: 未复核

### [维度02-04] `security` 单包（56 文件）混装 ~14 个子关注点
- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/`（目录，56 个 .java）
- **证据片段**（按子关注点归类）:
  ```
  路径访问(7) / 权限提供(4) / 工具访问(5) / 审计(5) / 审批门(4) /
  拒绝账本(9) / 盲重试守卫(3) / 内容信任(3) / 安全级别/矩阵(7) /
  身份/通道(3) / 指纹(1) / 父约束(1) / DB 表常量(1)
  ```
- **严重程度**: P3
- **现状**: 单个 `security` 包容纳约 14 个子领域，56 个文件。
- **风险**: 包内查找性下降；不同子领域的 NoOp/默认实现命名拥挤（`NoOp*`/`AllowAll*`/`PassThrough*`/`Auto*` 共 11 个）；DB 表常量类与策略接口混在一起，职责边界模糊。
- **建议**: 拆为子包 `security.path` / `security.tool` / `security.permission` / `security.approval` / `security.denial` / `security.audit` / `security.matrix` / `security.fingerprint`。
- **信心水平**: 很可能
- **误报排除**: 56 文件已远超单包可舒适浏览规模，且子领域确有清晰边界。
- **复核状态**: 未复核

### [维度02-05] 4 个 DB* 类重复 JDBC `initSchema` / `SQLException` 包装样板
- **文件**: `session/DBSessionStore.java:78-87`、`reliability/DBCheckpointManager.java:93-102`、`security/DBDenialLedger.java:98-107`、`message/DBMessageService.java:149-157`
- **证据片段**:
  ```java
  // DBSessionStore.java:78-87
  private void initSchema() {
      try (Connection conn = dataSource.getConnection();
           Statement stmt = conn.createStatement()) {
          stmt.execute(AiAgentSessionTable.DDL_CREATE_TABLE);
          stmt.execute(AiAgentSessionTable.DDL_CREATE_INDEX);
      } catch (SQLException e) {
          throw new NopAiAgentException(
                  "DBSessionStore: failed to initialize schema: " + e.getMessage(), e);
      }
  }
  ```
- **严重程度**: P3
- **现状**: 4 个 DB 实现各自携带逐字相同的 10 行 `initSchema()` 与重复的 SQLException→NopAiAgentException 包装。
- **风险**: 样板重复约 40 行 initSchema + ~20 处 catch 包装；新增 DB 实现需复制同款样板。
- **建议**: 抽 `JdbcHelper.executeDdl(DataSource, String...)` 与 `JdbcHelper.wrapSql(...)`（或 `AbstractAgentDbStore` 基类）。
- **信心水平**: 很可能
- **误报排除**: 各类的表常量类已正确外置，样板剩余量较小，故定 P3。
- **复核状态**: 未复核

### [维度02-06] `DefaultAgentEngine` 三条执行路径尾部 ~20 行块逐字三连重复
- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java`（doExecute 行 642-673 / resumeSession 行 755-779 / restoreSession 行 869-891）
- **证据片段**:
  ```java
  // DefaultAgentEngine.java:642-673（resumeSession:755-779 / restoreSession:869-891 同构）
  return CompletableFuture.supplyAsync(() -> {
      session.setStatus(AgentExecStatus.running);
      CancelHandle handle = new CancelHandle(ctx, Thread.currentThread());
      runningExecutions.put(sessionId, handle);
      AgentExecutionResult result;
      try {
          result = executor.execute(ctx).toCompletableFuture().join();
      } finally {
          runningExecutions.remove(sessionId);
          session.setStatus(ctx.getStatus());
      }
      session.replaceMessages(ctx.getMessages());
      session.addTokensUsed(ctx.getTokensUsed());
      session.addIterations(ctx.getCurrentIteration());
      session.touch();
      sessionStore.save(session);
      return result;
  });
  ```
- **严重程度**: P3
- **现状**: `doExecute` / `resumeSession` / `restoreSession` 三条路径共用"登记 CancelHandle → 执行 → finally 注销 → 同步 messages/tokens/iterations → save"的 ~20 行收尾块，三处逐字重复。
- **风险**: 未来在收尾块加入新步骤需改三处，易遗漏其中一条路径导致行为漂移（恢复路径与正常路径行为不一致是典型生产 bug 源）。
- **建议**: 抽私有方法 `runExecutionAndSync(sessionId, ctx, session, executor)`，三处复用。
- **信心水平**: 确定
- **误报排除**: 非 `_` 生成文件；仅针对三连重复的 20 行块。
- **复核状态**: 未复核

## 维度复核结论

待复核。

## 子项复核结论

待复核。

## 最终保留项

待复核后填写。
