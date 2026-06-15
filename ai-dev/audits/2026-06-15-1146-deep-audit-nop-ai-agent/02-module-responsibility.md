# 维度 02：模块职责与文件边界（nop-ai-agent）

## 第 1 轮（初审）

### [维度02-1] ReActAgentExecutor.java（2042 行）承担 ≥10 个不相关职责

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/ReActAgentExecutor.java:115-2042`
- **证据片段**:
  ```java
  // 职责 1：Builder（27 个依赖，305 行）
  private ReActAgentExecutor(IChatService chatService, IToolManager toolManager,
          IAgentEventPublisher eventPublisher, IPermissionProvider permissionProvider,
          IToolAccessChecker toolAccessChecker, IPathAccessChecker pathAccessChecker,
          ... 21 more dependencies ...
  // 职责 2：ReAct 主循环
  while (ctx.getCurrentIteration() < ctx.getMaxIterations()) {
      if (ctx.isCancelRequested()) { handleCancellation(...); break; }
      if (denialLedger.isPaused(sessionId)) { handleSessionPaused(...); break reactLoop; }
      if (shouldForceStop(ctx)) { handleForcedStop(...); break; }
      if (shouldTriggerCompaction(ctx)) { performCompaction(...); }
  // 职责 3：六路 security dispatch
  // 职责 4：tool 并发调度 CompletableFuture.allOf(futuresArray).join();
  // 职责 5/6/7/8：compaction、checkpoint(3处)、sessionStore持久化(3处)、talent/skill咨询
  ```
- **严重程度**: P2
- **现状**: 单一类混合至少 10 类职责：Builder、ReAct 主循环、6 路 Security dispatch、tool 并发调度、token 估算、上下文压缩、hook lifecycle、input/output guardrail、CompletionDecision、effective 权限/path 计算。110 个 import、34 个 private 方法。
- **风险**: 改任何一项逻辑（security deny 顺序、checkpoint 触发点、session 持久化、compaction 触发、tool 调度并发模型、effective 权限计算、hook lifecycle）都必须修改这个 2042 行巨型类，回归面极大。三类触发点的 checkpoint recording 以三处分散重复的形式出现，任一处遗漏即破坏 crash/restart 恢复不变式。
- **建议**: 抽取多个独立协作者：SecurityDispatcher（~400 行）、CheckpointRecorder（~120 行）、EffectivePermissionResolver（~200 行）、ToolDispatcher（~150 行）、SystemPromptAssembler（~120 行）。这些函数都已经以 private 方法存在，只需提取到独立类。
- **信心水平**: 高
- **误报排除**: 类被 14 个测试覆盖，说明现状可测试，但每条新逻辑分支都会让测试集膨胀。确认不是"不优雅"问题——单一类承担 security + checkpoint + compaction + 调度是真实耦合。
- **复核状态**: 未复核

### [维度02-2] DefaultAgentEngine.java（1246 行）三处 supplyAsync 后 sync 块近似复制粘贴

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java:661-692, 774-798, 888-910`
- **证据片段**:
  ```java
  // doExecute (661-692)
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
  // resumeSession (774-798) 和 restoreSession (888-910) 几乎逐行相同
  ```
- **严重程度**: P2
- **现状**: 三个公开入口 `doExecute` / `resumeSession` / `restoreSession` 各自内联了一段相同的"注册 CancelHandle → 执行 → finally 注销 → 全量消息 sync → token/iteration 累加 → save"流程（约 30 行 ×3 = 90 行重复代码）。
- **风险**: 未来需要在"执行后 sync"链上插入任何步骤，必须三处同步修改，遗漏任一处即产生行为漂移。
- **建议**: 抽取 `private CompletableFuture<AgentExecutionResult> runWithSync(sessionId, ctx, executor, session)`，三处入口统一调用。
- **信心水平**: 高
- **误报排除**: 不算"不优雅"——三处确实存在可证实的逐行重复。
- **复核状态**: 未复核

### [维度02-3] `io.nop.ai.agent.plan.model` 包（42 个文件）是完全未被使用的死代码

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/plan/model/` 整个目录（21 个手写 stub + 21 个 `_gen/_*.java`）
- **证据片段**:
  ```java
  // plan/model/AgentPlanModel.java
  package io.nop.ai.agent.plan.model;
  import io.nop.ai.agent.plan.model._gen._AgentPlanModel;
  public class AgentPlanModel extends _AgentPlanModel{ public AgentPlanModel(){ } }
  ```
  ```
  $ grep -rn "io.nop.ai.agent.plan" --include="*.java" --include="*.xml" --include="*.xdef" \
      /nop-entropy-master/ | grep -v "nop-ai-agent/src/main/java/io/nop/ai/agent/plan/"
  nop-kernel/nop-xdefs/.../agent-plan.xdef:13:  xdef:bean-package="io.nop.ai.agent.plan.model"
  ```
- **严重程度**: P2
- **现状**: `agent-plan.xdef` 把 `xdef:bean-package` 声明为 `io.nop.ai.agent.plan.model`，但**整个包零生产代码引用、零测试引用**——没有任何手写 Java 类 import 此包。该 schema 声明了 21 个元素，全部生成完毕但无消费方。
- **风险**: 未完成的迁移——schema 已切到新位置但生产代码仍依赖旧包（见 [维度02-4]）。42 个文件扩大模块代码体积、混淆"plan model 在哪里"。每次跑 codegen 都会被重新生成。
- **建议**: 二选一：① 完成 migration（切 LlmCompletionJudge/AgentExecutionContext 的 import）；② 删除 agent-plan.xdef、register-model、record-mappings 与整个 plan/model/ 包。
- **信心水平**: 高
- **误报排除**: 已通过全仓库扫描确认无外部消费方（仅 xdef 自身的 bean-package 声明）。
- **复核状态**: 未复核

### [维度02-4] `io.nop.ai.agent.model` 包内残留 7 个 `AgentPlan*` 旧生成文件

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/model/`（AgentPlanDecision/Error/Model/Note/PhaseModel/Question/TaskModel.java 共 7 个手写 stub + 7 个 `_gen/_*.java`）
- **证据片段**:
  ```java
  // model/_gen/_AgentPlanModel.java（旧，475 行）
  public abstract class _AgentPlanModel extends io.nop.core.resource.component.AbstractComponentModel {
      // generate from /nop/schema/ai/agent-plan.xdef
  // 对比：plan/model/_gen/_AgentPlanModel.java（新，277 行）—— 同一 xdef 两份不同生成产物
  ```
- **严重程度**: P2
- **现状**: 同一个 schema 在两个 bean-package 各生成了一份。`LlmCompletionJudge.java:5` 与 `AgentExecutionContext.java:6,21,81,85` 仍 import 旧位置的 `io.nop.ai.agent.model.AgentPlanModel`。engine 从不调用 `ctx.setPlan(...)`，所以 `LlmCompletionJudge` 中读 plan 的分支实际上是死代码。
- **风险**: 同名类在两个包下并存，开发者很容易 import 错——`AgentExecutionContext.plan` 字段类型是旧的，而 codegen 生成的 `agent-plan.xml` 加载结果是新的，类型不兼容。
- **建议**: 配合 [维度02-3] 完成 migration，删除旧文件；或在 xdef 修复 `xdef:bean-package` 让生成器只产生一份。
- **信心水平**: 高
- **误报排除**: 通过对比两份 `_gen/_AgentPlanModel.java` 的 Javadoc 和行数（475 vs 277）确认它们不是同一次生成的副本。
- **复核状态**: 未复核

### [维度02-5] DefaultLevelHintsProducer（security 包）越界进入"文件系统路径解析"职责

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/security/DefaultLevelHintsProducer.java:89, 116-129, 131-134`
- **证据片段**:
  ```java
  private boolean evaluateWritesOutside(Map<String, Object> arguments, File workDir) {
      File base = workDir != null ? workDir : new File(".").getAbsoluteFile();  // line 89
      ...
  }
  private boolean isOutsideBase(String pathValue, File base, ...) {
      File resolved = new File(pathValue);                  // line 116
      if (!resolved.isAbsolute()) {
          resolved = new File(base, pathValue);             // line 118
      }
      ...
  }
  ```
- **严重程度**: P3
- **现状**: `DefaultLevelHintsProducer` 位于 `security/` 包，但其 `evaluateWritesOutside` 实际承担了"path 解析 + 工作目录基准 + 路径归一化"——本应属于 `DefaultPathAccessChecker` 或独立 `PathResolver` 的职责。与 `DefaultPathAccessChecker.normalizePathStatic` 形成重复。`new File(".")` 还让 hint 评估依赖 JVM CWD。
- **风险**: 路径归一化规则若变更，三处独立的"under workspace"判断逻辑可能漂移。当前无回归，属"职责越权"型耦合。
- **建议**: 把路径解析抽到 `security/DefaultPathAccessChecker` 或新建 `security/PathBoundary` 工具类。
- **信心水平**: 中
- **误报排除**: `security/` 包同时存在 `DefaultPathAccessChecker` 和 `DefaultLevelHintsProducer`，两者都在做路径解析，存在真实职责重叠。
- **复核状态**: 未复核

## 维度复核结论

| 发现 | 复核结论 | 理由 |
|------|---------|------|
| [维度02-1] ReActAgentExecutor 巨型类 | **保留 P2** | 行数、import 数、private 方法数、职责清单均可证实。三处分散的 checkpoint recording 是真实耦合。 |
| [维度02-2] 三处重复 sync 块 | **保留 P2** | 行号区间可对比，逐行重复可证实。 |
| [维度02-3] plan/model 死代码包 | **保留 P2** | 全仓库 grep 确认零消费方。 |
| [维度02-4] model 下残留旧 AgentPlan* | **保留 P2** | 两份 _gen 对比（475 vs 277 行）可证实是不同时间点产物。 |
| [维度02-5] security 越权做路径解析 | **保留 P3** | DefaultLevelHintsProducer 确实做路径 IO 概念，与 DefaultPathAccessChecker 职责重叠。 |

## 最终保留项

| 编号 | 严重程度 | 文件 | 一句话摘要 |
|------|---------|------|-----------|
| 02-1 | P2 | engine/ReActAgentExecutor.java | 2042 行巨型类承担 ≥10 类职责 |
| 02-2 | P2 | engine/DefaultAgentEngine.java | 三处 supplyAsync sync 块近似复制粘贴 |
| 02-3 | P2 | plan/model/ 整个包 | 42 个文件完全未被使用的死代码 |
| 02-4 | P2 | model/AgentPlan*.java | 7 个旧生成残留，与 plan/model 新位置冲突 |
| 02-5 | P3 | security/DefaultLevelHintsProducer.java | security 包越界做文件路径解析 |
