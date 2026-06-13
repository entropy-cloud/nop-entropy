# 维度 02：模块职责与文件边界 — nop-ai-agent

**审计目标**: `nop-ai/nop-ai-agent`
**模块性质**: 纯 Java 框架库（AI Agent 引擎），非标准业务模块，无 api/dao/meta/service/web 子模块拆分
**审计口径**: 以 live code 为准；生成产物本身不计为发现，但**陈旧/孤儿生成文件**（不再匹配当前 xdef 配置）属于有效发现
**关键基线**: 手写 Java 104 文件 / 生成 `_gen` 32 文件 / 仅 1 个手写文件 >500 行（ReActAgentExecutor 767 行）

## 第 1 轮（初审）

### [维度02-01] 陈旧的 plan 模型生成物残留于错误包，并被错误接入 AgentExecutionContext

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/model/_gen/_AgentPlanModel.java:1-19`（及其同目录 6 个 `_AgentPlan*.java`）、`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/AgentExecutionContext.java:6,19`、对照 `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/ai/agent-plan.xdef:13`
- **证据片段**:
  ```java
  // AgentExecutionContext.java —— 引用的是 io.nop.ai.agent.model 包下的 AgentPlanModel
  import io.nop.ai.agent.model.AgentPlanModel;   // line 6
  ...
  private AgentPlanModel plan;                    // line 19

  // agent-plan.xdef —— 权威 xdef 声明的 bean-package 是另一个包
  xdef:name="AgentPlan" xdef:bean-package="io.nop.ai.agent.plan.model"

  // model/_gen/_AgentPlanModel.java 头部却声称生成自同一 xdef
  // generate from /nop/schema/ai/agent-plan.xdef
  package io.nop.ai.agent.model._gen;
  ```
- **严重程度**: P1
- **现状**: `agent-plan.xdef` 的 `xdef:bean-package` 已迁移至 `io.nop.ai.agent.plan.model`（根类 `AgentPlan`），但 `io.nop.ai.agent.model._gen` 下仍残留 7 个声称"generate from /nop/schema/ai/agent-plan.xdef"的孤儿文件（`_AgentPlanModel/_AgentPlanTaskModel/_AgentPlanPhaseModel/_AgentPlanDecision/_AgentPlanError/_AgentPlanNote/_AgentPlanQuestion`），连同 `io.nop.ai.agent.model` 下 7 个对应的空 retention 类。它们已无法被 xdef 重新生成覆盖（包路径不匹配），且与权威包 `io.nop.ai.agent.plan.model` 内的同名类并存，造成双胞胎模型。
- **风险**: (1) `AgentExecutionContext.plan` 字段被定型为陈旧的 `io.nop.ai.agent.model.AgentPlanModel`，而 `agent-plan.register-model.xml` 运行时加载 `agent-plan.xml` 实际产出的是 `io.nop.ai.agent.plan.model.AgentPlan` —— 两类无继承关系，`plan` 字段事实上是死代码（grep 确认引擎代码从不调用 `setPlan`，仅一个测试 `assertNull(ctx.getPlan())`）。(2) 未来实现 plan 执行模式（`DefaultAgentEngine.resolveExecutor` 现抛 `UnsupportedOperationException`）时，开发者会误接入陈旧类型，导致 `ClassCastException` 或字段永远为 null 的隐性回归。(3) 5 个同名 simple name 在同模块内并存，IDE 自动 import 极易选错包。
- **建议**: 删除 `io.nop.ai.agent.model` 下 7 个 `_AgentPlan*` 残留文件及对应 retention 类；将 `AgentExecutionContext.plan` 的类型改为权威的 `io.nop.ai.agent.plan.model.AgentPlan`（或暂时移除该死字段，待 plan 执行模式落地时再加）。重新跑 `mvn install` 校验生成产物不再回写旧包。
- **信心水平**: 确定
- **误报排除**: 这不是"对生成产物本身挑刺"。审计口径明确把"陈旧/孤儿生成文件（不再匹配当前 xdef/template 配置）"列为有效发现。此处 xdef 的 `bean-package` 已是 `io.nop.ai.agent.plan.model`，但旧包路径下仍存在声称生成自同一 xdef 的文件，二者只能一方为权威——旧包那方即为陈旧残留。证据来自 xdef 源文件直接读取，非推测。
- **复核状态**: 未复核

### [维度02-02] ReActAgentExecutor 单文件 767 行，execute() 方法 293 行混合 7+ 生命周期职责

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/ReActAgentExecutor.java:226-519`（execute 主体）；另含 107 行内嵌 Builder（`117-224`）和硬编码 `PATH_ARG_KEYS` 集合（`645-648`）
- **证据片段**:
  ```java
  public CompletionStage<AgentExecutionResult> execute(AgentExecutionContext ctx) {  // line 227
      ...
      while (ctx.getCurrentIteration() < ctx.getMaxIterations()) {                  // 252
          if (ctx.isCancelRequested()) { handleCancellation(...); break; }          // 253 取消
          if (shouldTriggerCompaction(ctx)) { performCompaction(...); }             // 258 压缩
          HookResult preReasoningResult = invokeHooks(PRE_REASONING, ...);          // 262 钩子
          GuardrailResult inputGuardrailResult = checkInputGuardrail(ctx);          // 268 输入护栏
          RoutingResult routingResult = modelRouter.route(...);                     // 278 路由
          ChatResponse response = chatService.call(request, null);                  // 285 LLM 调用
          ...
          for (ChatToolCall chatToolCall : assistantMsg.getToolCalls()) {           // 343 工具循环
              ... toolAccessChecker / permissionProvider / checkPathAccess ...       // 安全三连
          }
      }
  }
  ```
- **严重程度**: P2
- **现状**: 单个 `execute()` 方法跨越 293 行（`227-519`），在 while 主循环内串行编排：取消检查、上下文压缩触发、6 个生命周期点的 hook 调用、输入/输出 guardrail、模型路由、LLM 调用与 token 统计、工具访问/权限/路径访问三道安全闸、并行工具执行、reentry 计数（`BEFORE_/AFTER_TOOL_RESULT_PROCESSED` 两段几乎重复的逻辑 `444-475`）、事件发布、异常兜底。叠加 107 行内嵌 `Builder`，整文件 767 行，是模块内唯一超过 500 行的手写文件（次大文件仅 326 行）。
- **风险**: (1) 单方法过长且嵌套深（while → for → for → if），分支组合爆炸，难以针对单个生命周期点写聚焦单测；现有测试只能通过完整 ReAct 回环间接覆盖（见 `TestHookInReActLoop` 624 行、`TestContentGuardrailInReActLoop` 503 行等超大测试文件，正是被测对象过大的反噬）。(2) `BEFORE_/AFTER_TOOL_RESULT_PROCESSED` 两段 reentry 计数逻辑近乎复制粘贴（`444-458` vs `464-475`），后续新增 re-entrant hook 点时极易漏改一处。(3) 内嵌 Builder 占 14% 行数，与编排逻辑物理混排，阅读主循环时需频繁跳过。
- **建议**: 将 Builder 抽为独立顶层 `ReActAgentExecutorBuilder`（与 `DefaultAgentEngine` 共用依赖集，可考虑统一工厂）；将 `execute()` 拆为 `runIteration(ctx)` / `processToolCalls(ctx, assistantMsg)` / `enforceToolSecurity(...)` / `handleReenter(...)` 等私有方法，使每个生命周期点可独立断言；reentry 计数抽成 `ReentryGuard` 小工具消除重复。
- **信心水平**: 很可能
- **误报排除**: 这不是"看起来不优雅"。维度正文明确要求"检查是否有超大类（>500 行）承担了过多职责"。证据是可量化的：767 行（唯一超 500）、execute() 293 行单方法、7+ 职责混合、两段重复 reentry 逻辑，且衍生出多个 500+ 行的测试文件作为可观测的反作用。结构性原因成立。
- **复核状态**: 未复核

### [维度02-03] DefaultAgentEngine 用 8 个伸缩构造函数自兼 Builder，与同模块 ReActAgentExecutor 的 Builder 模式不一致

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/DefaultAgentEngine.java:57-121`
- **证据片段**:
  ```java
  public DefaultAgentEngine(IChatService chatService, IToolManager toolManager) {           // 57
      this(chatService, toolManager, new InMemorySessionStore());
  }
  public DefaultAgentEngine(IChatService chatService, IToolManager toolManager,
                            ISessionStore sessionStore) {                                    // 61
      this(chatService, toolManager, sessionStore, new AllowAllPermissionProvider());
  }
  public DefaultAgentEngine(IChatService chatService, IToolManager toolStore,
                            ISessionStore sessionStore, IPermissionProvider permissionProvider) { // 66
      this(chatService, toolManager, sessionStore, permissionProvider, new AllowAllToolAccessChecker());
  }
  // ... 共 8 个重载，层层 this(...) 委托，直到第 8 个才真正赋值（105-121）
  ```
- **严重程度**: P2
- **现状**: `DefaultAgentEngine` 用 8 个层层委托的构造函数（`57-121`，共 65 行，占文件 20%）来为 9 个依赖逐个提供"缺省值注入"。而**同一模块、同一依赖集**的 `ReActAgentExecutor` 采用的是 `Builder` 模式（`ReActAgentExecutor.builder()...build()`，且 `DefaultAgentEngine.resolveExecutor` 第 `281-293` 行正是这样调用）。两种构造范式在同一引擎链条里并存。
- **风险**: (1) 经典伸缩构造器反模式：每新增一个可选依赖，需新增一个构造器并重排委托链，调用方还需辨认该用哪个重载。(2) 范式不一致增加认知成本——`DefaultAgentEngine` 自己用伸缩构造器，却用 Builder 去构造它内部的 `ReActAgentExecutor`，对维护者释放矛盾信号。(3) `resolveExecutor`（`277-302`）混合了"执行模式分派"和"ReAct 执行器组装"两件事，后者是把 Builder 调用硬塞进分派方法，进一步说明引擎类承担了本应由独立工厂承担的组装职责。
- **建议**: 为 `DefaultAgentEngine` 提供与 `ReActAgentExecutor` 一致的 `Builder`（或一个统一的 `AgentEngineFactory` 同时产出 engine 与 executor），删除 8 个伸缩构造器只保留一个全参构造；将 `resolveExecutor` 中的 ReAct 组装逻辑移入工厂方法。
- **信心水平**: 很可能
- **误报排除**: 这不是 checkstyle 能覆盖的机械问题（伸缩构造器合法编译通过），而是设计模式 / 职责混放问题，属于维度 02"模块职责"范畴。可量化证据：8 个重载、65 行纯委托样板、与同模块 `ReActAgentExecutor.Builder` 范式冲突。
- **复核状态**: 未复核

### [维度02-04] 路径参数识别策略（PATH_ARG_KEYS）硬编码在 ReAct 引擎主循环内，泄露至 security 包边界之外

- **文件**: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/ReActAgentExecutor.java:645-683`
- **证据片段**:
  ```java
  private static final Set<String> PATH_ARG_KEYS = Set.of(                       // 645
          "path", "file", "filePath", "filename", "directory", "dir",
          "destination", "output", "input", "source", "target", "cwd"
  );

  private String checkPathAccess(ChatToolCall chatToolCall, AgentExecutionContext ctx,
                                 String sessionId, String agentName) {            // 650
      Map<String, Object> arguments = chatToolCall.getArguments();
      ...
      for (Map.Entry<String, Object> entry : arguments.entrySet()) {
          if (!PATH_ARG_KEYS.contains(entry.getKey())) {                          // 658 按键名启发式判定
              continue;
          }
          ...
          PathAccessResult pathResult = pathAccessChecker.checkAccess(pathValue, ctx); // 670
  ```
- **严重程度**: P2
- **现状**: "哪些工具参数键应被视为路径"这一**安全相关策略**，以一个 12 元素的硬编码 `Set` 嵌在 ReAct 主循环所在的引擎类里。`security` 包提供了 `IPathAccessChecker`/`DefaultPathAccessChecker`（负责"给定路径是否允许访问"），但"识别某个参数是不是路径"的前置判定却被留在了引擎层，且不可按工具/按部署覆盖。
- **风险**: (1) 安全策略与安全实现分家：路径访问的"判定+检查"完整职责被横跨 engine 与 security 两个包拆开，修改启发式需改引擎热路径而非安全模块。(2) 仅靠键名白名单识别会漏判非约定键名（如 `"url"`、`"repo"`、自定义工具的本地化参数名），造成**安全检查被静默绕过**——这是真实的可量化风险，不是风格问题。(3) 启发式无法按工具 schema 精确化（schema 本可声明某参数为 `path`/`file-path` 类型）。
- **建议**: 将路径参数识别下沉到 `security` 包（如新增 `IPathArgumentExtractor`/`PathArgumentExtractor`，由 `DefaultPathAccessChecker` 持有），或改为 schema 驱动——从 `AiToolModel.getSchema()` 中类型为 path 的参数直接获取。引擎仅保留"对识别出的路径调用 `pathAccessChecker`"的编排，不持有安全判定策略。
- **信心水平**: 很可能
- **误报排除**: 这不是"Nop 约定大于配置"的误报（校准清单第 5/7 条）。`PATH_ARG_KEYS` 不是平台约定的标准模式，而是模块自定义的安全启发式；它与 `security` 包已存在的 `IPathAccessChecker` 抽象分裂了同一安全职责。维度 02 明确要求检查"本应属于另一层的功能被错误放置"——路径安全策略属于 security 层。
- **复核状态**: 未复核

## 合规性说明（已检查但未列为发现的项目）

- **生成产物本身（32 个 `_gen` 文件）**: 抽查 `_AgentModel.java`、`_AgentConstraintsModel.java`、`_AgentPlan.java` 等，均含标准头部，无手写修改痕迹。除 [维度02-01] 指出的陈旧残留外，生成产物合规。
- **标准业务子模块拆分（dao/service/meta/web/app）**: 本模块是框架库，基线已确认无此拆分需求，按 concern 分包，职责划分清晰。
- **`_` 前缀资源文件**: register-model / record-mappings 为手写注册/映射配置，位置符合 Nop 平台约定。
- **其余手写文件**（均 <200 行）: AgentExecutionContext(172)、AgentSession(138)、DefaultPathAccessChecker(118) 等，文件大小与职责匹配。
- **retention 类**: 标准 Nop 代码生成保留文件，合规。

**统计**: 共 4 项发现 —— P1×1、P2×3。
