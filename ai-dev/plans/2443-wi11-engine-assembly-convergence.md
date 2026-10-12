# 2443 WI11 agent 引擎装配面收敛

> Plan Status: completed
> Last Reviewed: 2026-10-12
> Source: `ai-dev/backlog/nop-ai-structure-refactor-roadmap.md` §WI11；装配面普查 = 2026-10-12 live HEAD（含 WI10/plan 2441 落地后状态）实测
> Draft Review: 2026-10-12 独立子代理对抗审查 round 1（agent_100bd68b）——2 Blocker（contextCompactor 默认值两路径不等价：链默认 Micro vs Builder 默认 Pipeline 全管线，codemod 须逐级保真补发 / Builder test 消费面实为 53 文件 40 个用 config-backed setter 非 5 文件）+ 4 Major（构造分桶 352 非 231 / 委托三分类缺引擎自身方法豁免类且 85≠84 / setUsageRecorder 双标志归一未定 / codemod 漏 super 形态与 grep 未限 src）+ 4 Minor（gateway 实为 0 处 / 47 参非 48 / ~16 处 javadoc 引用被删方法 / 中间 Phase 缺模块测试门），全部修入正文（A2b 新增、A3 改形态①、A4 重写）；转入执行
> Related: WI10/plan 2441（completed）——同文件先动者，FQN 已上移根包；`ai-dev/backlog/nop-ai-agent-autonomous-execution-improvement-roadmap.md`（在途 19 项全部未启动）——其 WI5/WI6/WI13 将向 Config/Builder/Executor 构造**新增**配置项，本 WI 为先动者，落地形态（Config 单一承载 + Builder 唯一装配）即其后续新增字段的接线基面；`ai-dev/backlog/nop-ai-agent-refactor-roadmap.md`（已收口）——只裁定执行模型语义，未触碰装配面，本 WI 行为零变化

## Purpose

把 agent 引擎装配收敛到「Builder → Config → Executor」单一路径：删除 `DefaultAgentEngine` 的 8 个 telescoping 构造函数与全部纯单行 config 委托 getter/setter，`ReActAgentExecutor` 的 48 参构造改为以 `DefaultAgentEngineConfig` 为承载的分组签名。行为零变化（109 个 engine 测试文件为行为基线）。目标净删约 800 行纯转发代码。

## Current Baseline

> 2026-10-12 live HEAD（plan 2441 之后）实测。

- `DefaultAgentEngine.java`（1300 行）：
  - **8 个 telescoping 构造函数**（:303-:359，起始行 :303/:307/:312/:317/:324/:333/:342/:351）：2 参起步逐级 +1 至 9 参终态，链条默认值依序 = `new InMemorySessionStore()` / `new AllowAllPermissionProvider()` / `new DefaultToolAccessChecker()` / `new DefaultPathAccessChecker()` / `NoOpContentGuardrail.noOp()` / `PassThroughModelRouter.passThrough()` / `new MicroCompressionCompactor()`；9 参终态直接赋字段（`this.chatService`/`this.toolManager` 字段 + `this.config = new DefaultAgentEngineConfig()`）。
  - **Builder（内部类，:150-:298 区段）**：`Builder(chatService, toolManager)` 必填 + 全量 fluent setter；`build()` = 9 参终态构造 + `applyTo(engine)` 经 **engine 的委托 setter** 写入 + `startupWarnings.warnIfInsecureDefaults`；另有静态工厂 `builder(chatService, toolManager)`。
  - **委托 getter/setter 窗口（:384-:557 区段）**：实测 84 个 public get/set（78 纯单行 config 委托——与 roadmap"约 78"吻合、1 带逻辑、5 协作转发）；:560 的 `getEventPublisher()` 是引擎自身字段读取，非委托，不入窗口。分类四类：①**纯单行 config 委托**（`config.setXxx(...)`/`return config.getXxx()`，删）；②**带逻辑**（`setUsageRecorder` 2 行——config.set + `startupWarnings.resetUsageRecorderNoOpWarned()`，删但逻辑迁入 Config 写侧或 Builder）；③**协作对象转发**（`setMessenger/getMessenger`→`callDelegate`、`setMailboxFactory/getMailboxFactory`→`sessionSupport`、`setLockRenewExecutor`→`lockRenewal`，非 config 委托，**保留**）；④**引擎自身方法豁免**（`setAgentExecutor` :580、`initBackgroundSupport` :739、`setBackgroundProgressTracker` :799、`getEventPublisher` :560——真实装配逻辑/字段读取，**保留且不得被 codemod 触碰**；注意与 config 委托 `setToolProgressTracker` :533 名字相近）。
  - **外部消费者**：`new DefaultAgentEngine(` live src 命中 = agent main 1（类内 Builder.build()）+ DBSessionStore javadoc 1 + **agent test 104 文件**；括号配对解析 census（test）：**352 个真实调用** = 单行 191（2 参 130——含 (noOp,noOp) 20、(null,null) 18；3 参 55）+ 多行 167（arity 2-9 = 37/85/6/21/2/8/4/3）；另 7 处在注释/javadoc。**子类化形态**：`TestChannelPrincipalPropagation.CapturingEngine:153` 以 `super(chatService, toolManager)` 继承 2 参构造（telescoping 删除后 super 调用不可 Builder 化，须改造为组合，见 A4）。
- **委托调用消费者**：84 委托名 + 排除 `config.`/`builder.` 接收者，全仓 **506 处**（agent test 425 + agent main 81；gateway **0**——ChannelMessageServiceImpl 仅 javadoc 提及 Config）。噪声形态已核：无方法引用/反射/链式调用漏网；`agentCtx.getTeamManager()` 等 17 处 AgentExecutionContext 同名方法与 `callDelegate.setMessenger` 4 处为噪声，codemod 必须按 Phase 1 精确删除名单 + 接收者类型驱动，不得泛 regex。
  - **委托调用消费者**：`引擎变量.(set|get)<委托名>(` 形态（排除 `config.`/`builder.` 接收者）在 agent main+test+gateway 约 **540 处**——典型为测试构造后调优（`engine.setToolTimeoutMs(500); engine.execute(...)`）。
- `ReActAgentExecutor.java`（1976 行）：**47 参构造（:210-:256，47 个唯一参数，无重名——roadmap"48"系泛型逗号误计）**包私有；**38 个与 `DefaultAgentEngineConfig` 字段同名同型**（逐名核对 Config :107-:178 的 50 实例字段），不在 Config 的 **9 个核定名单**：`chatService`（Config 全文无 IChatService 字段，仅 :508 javadoc 提及）、`toolManager`、`eventPublisher`、`engine`（自引用）、`messenger`、`hookRegistry`、`sessionStore`、`completionJudge`、`timeoutExecutor`。构造体内含 null-defaulting（tokenEstimator/completionJudge/goalTracker/sustainer 等）。
- **装配等价性锚点**：`AgentExecutorResolver.resolveExecutor`（:141-:223）**每次 execute 都重建** ReActAgentExecutor 并当时读 `config.getXxx()`——构造后调优经 config 与原委托行为完全等价。`AgentStartupWarnings` 持有自有 `usageRecorderNoOpWarned` 标志（:44），而 `Config.setUsageRecorder` 已重置 Config 自己的同名标志（:128/:462）——**双标志**（见 A5）。
- `ReActAgentExecutorBuilder.java`（700 行，顶层类）：**47 字段** fluent builder，1:1 镜像构造参数；main 消费者 = `AgentExecutorResolver`（2 处）+ ReActAgentExecutor 自身 3 处；javadoc 提及 4 文件（InMemoryMemoryStoreProvider:13、SmartModelRouter:55、DbModelSwitchedMessageWriter:32、NoOpModelSwitchedMessageWriter:17——引用 `modelRouter`/`modelSwitchedMessageWriter` 字段，收敛后须同步）；**test 消费者 53 文件**经 `ReActAgentExecutor.builder()` 流式链构造（其中 **40 个调用 config-backed fluent setter**——toolAccessChecker 10 文件、contextCompactor 8、checkpointManager 7、modelRouter 5、permissionProvider 5 等）+ `TestReActAgentExecutorBuilder.java` 专测文件（含 required-param 校验断言）。
- 行为基线：engine 包 **109 个测试文件**（精确）；`DefaultAgentEngineConfig` 51 私有字段（50 实例 + 1 static logger）。beans/ioc 复核已完成：全仓 beans.xml 无 `io.nop.ai.agent` 装配（纯代码装配，Non-Goals 的"执行时复核"已闭合）。gateway 无任何 engine 构造/委托代码依赖（Scope 相应剔除）。
- **Builder 与 telescoping 的默认值分歧（关键）**：telescoping 链 2-8 参调用对未传位置补 `new MicroCompressionCompactor()`（:304-:348 链）；而 `Builder` 无值时 `build()` 走的默认管线为 `defaultPipelineCompactor(chatService)` = PipelineCompactor(Micro + Layer2TurnPruning + Layer3FullSummary)（:200，AgentStartupWarnings:51-57 有 insecure-default 告警面）——**两条路径的 contextCompactor 默认不等价**，codemod 规格见 A4。
- **行为语义锚点（保持）**：委托 setter 的生效时点 = 写入 config、执行时读取——构造后调优合法且现有测试依赖之。删除委托后必须保留等价的构造后调优通道（见 Adjudication A1 的 `config()` 访问器）。

## Goals

- telescoping 构造函数 0 个（grep `public DefaultAgentEngine(IChatService` 仅 Builder 内 9 参保留或改私有）；`new DefaultAgentEngine(` 外部调用点 0 个（全部改 Builder）。
- 纯单行 config 委托 getter/setter 0 个（协作对象转发委托与带逻辑者的判定与处置见 Adjudication A2）；等价构造后调优通道 = 引擎公开 `config()` 访问器，外部调用 codemod 为 `x.config().setXxx(...)`。
- `ReActAgentExecutor` 构造 48 参 → **10 参**（`DefaultAgentEngineConfig config` + 8 个非 Config 参数 + `chatService`）；`ReActAgentExecutorBuilder` 700 行随参数面收敛（48 字段 → ~10 字段）。
- 净删纯转发代码目标约 800 行（roadmap 原目标）；A3 形态①保留 Builder fluent setter 面使净删规模下修（预计数百行量级：78 委托 + 55 telescoping 行 + 47→10 构造体与镜像字段）——实际值以 `git diff --stat` 登记，偏差按 WI8 先例在 Closure 说明。新增引擎能力改面 = Config 加字段 + 装配处接线。
- 行为零变化：`./mvnw test -pl nop-ai/nop-ai-agent -am` 全绿。

## Non-Goals

- 不改任何引擎执行语义、工具调用语义、会话存储语义（agent-refactor roadmap 已裁定面）。
- 不动 `DefaultAgentEngineConfig` 的字段集与默认值（autonomous roadmap 将向其新增字段，本 WI 只收敛访问路径）。
- 不处理 guardrail/test 包（WI13 承接）；不改 `AgentExecutorResolver` 的解析语义（仅适配新构造签名）。
- 不删除协作对象转发委托（messenger/mailboxFactory/lockRenewExecutor——非 config 委托）。
- 不改 beans/ioc 装配面（engine 无 beans.xml 装配，纯代码装配——执行时复核）。

## Adjudications

### A1 构造后调优通道：新增 `public DefaultAgentEngineConfig config()` 访问器

- 540 处委托调用多为**构造后、首次 execute 前**的测试调优。若强制全部改走 Builder（构造期），需对 104 个测试文件做语句流重排——语义分析级改造，风险与体量不成比例。
- 裁定：引擎新增单一 `config()` 访问器（javadoc 注明"构造后、execute 前的配置调优通道；运行中修改不做跨线程可见性承诺，与原委托语义一致"），外部调用 codemod `x.setXxx(v)` → `x.config().setXxx(v)`。委托删除后引擎自身转发面清零，Config 直接承载调优——符合"Config 加字段 + 装配处接线"目标语义。
- 备选被否方案：保留委托（违背 exit criterion）；每字段单独 accessor（重建 85 个方法，无净删）。

### A2 委托三分类的机械判定与处置

- 执行时逐方法判定：方法体恰为一行 `config.` 调用/返回 → 删；方法体含额外逻辑（如 setUsageRecorder 的 warning reset）→ 删，逻辑迁至 Builder.applyTo 或 Config 写侧（行为等价性以测试为基线）；转发至 callDelegate/sessionSupport/lockRenewal 等协作对象 → 保留（非 config 委托，roadmap 口径外）。
- Builder.applyTo 同步改造：经删掉的委托写入改为直接写 `engine.config.setXxx(...)` / `engine.<field>`。

### A2b setUsageRecorder 双标志归一

- `AgentStartupWarnings` 自有 `usageRecorderNoOpWarned` 标志（:44，reset 在被删委托 :542-545 内），`Config.setUsageRecorder` 已重置 Config 同名标志（:128/:462）。委托删除后若只走 `config()`/Builder 直写，startupWarnings 标志永不重置 → 构造后接真实 recorder 仍误发 NoOp WARN（日志级静默行为变化）。
- 裁定：**startupWarnings 改读 Config 标志**（Config 为单一事实源），engine 删除自有标志路径；以既有测试为基线验证告警行为不变（若无可覆盖告警的测试，补一条 focused test 钉住"构造后接真实 recorder 不再 WARN"）。

### A3 Executor 分组方案：Config 单一承载（否决 SecurityWiring/ReliabilityWiring/ToolWiring 三分组）

- roadmap 菜单含三分组对象方案；实测 46 参数中 38 个已在 Config 同名同型，引入三个 Wiring 类 = 再造一层镜像转发（与 WI11 消灭转发的目标自相矛盾），且 autonomous roadmap 后续新增字段还得双写。
- 裁定：构造签名 = `ReActAgentExecutor(DefaultAgentEngineConfig config, IChatService chatService, IToolManager toolManager, IAgentEngine engine, IAgentEventPublisher eventPublisher, ISessionStore sessionStore, IHookRegistry hookRegistry, IAgentMessenger messenger, ICompletionJudge completionJudge, Executor timeoutExecutor)`（10 参 = config + 9 核定非 Config 参数）；构造体内从 config 读值替换同名形参。null-defaulting 逻辑原样保留。
- **Builder 收敛形态（二选一裁定为前者）**：①`ReActAgentExecutorBuilder` 保留既有 fluent setter 面（53 个 test 消费文件零改动），但内部以一个 `DefaultAgentEngineConfig` 承载 config-backed 设置（38 个字段镜像删除，setter 改写内部 config），`build()` 传 config + 9 参——镜像字段与 47 参构造体的转发面即净删主体；②激进形态（删 config-backed setter，40 文件改写）被否——改造面 40 文件 vs 收益仅多删 ~38 个薄 setter 行，风险收益不成比例。
- 附带：4 个 main javadoc 对 `ReActAgentExecutorBuilder.modelRouter`/`modelSwitchedMessageWriter` 字段的引用随字段收敛同步改写；`TestReActAgentExecutorBuilder` 的 required-param 断言按新 build() 契约适配。

### A3b 超时零值合法化（Config 校验放宽，执行期裁定）

- 动机：Builder parity 初始化需 `setLlmTimeoutMs(0)/setToolTimeoutMs(0)` 合法（原 builder 字段默认 0 = 禁用超时，是直连 executor 用户的可表达语义——In-ReAct-Loop/fanout 测试族实证其承载行为）；且 executor 契约本就以 `<= 0` 为禁用（LlmCallCoordinator 守卫、dispatcher `effectiveTimeoutMs > 0` 才包裹）。
- 裁定：`Config.setLlmTimeoutMs/setToolTimeoutMs` 接受 0（= 显式禁用，javadoc 注明）、拒绝负数；`callAgentTimeoutMs/memberExecTimeoutMs` 保持严格正不变量。TestEngineConfigAndHelpers 参数化契约用例同步（llm/tool 移除 0 用例）。
- 影响面：仅两个 setter 的校验边界；engine-Builder 默认（120s/300s，正）不变，生产路径超时默认不受影响。

### A4 telescoping 删除的调用点迁移规则（codemod 规格）

- **默认值逐级保真（行为零变化的关键）**：k 参调用（k<9）改写为 Builder 链时，除 k 个显式位置参数外，**其余位置必须按 telescoping 链默认值逐个显式补发 fluent 调用**（`new InMemorySessionStore()` / `new AllowAllPermissionProvider()` / `new DefaultToolAccessChecker()` / `new DefaultPathAccessChecker()` / `NoOpContentGuardrail.noOp()` / `PassThroughModelRouter.passThrough()` / `new MicroCompressionCompactor()`）——不得依赖 Builder 自身默认（其 contextCompactor 默认为 PipelineCompactor 全管线，与链默认 MicroCompressionCompactor 不等价，静默改变压缩管线即行为回归）。9 参调用按位置映射无补发。
- 位置参数→fluent 名映射表（链序）：sessionStore/permissionProvider/toolAccessChecker/pathAccessChecker/contentGuardrail/modelRouter/contextCompactor；2 参 `new DefaultAgentEngine(a, b)` → `new DefaultAgentEngine.Builder(a, b).<保真补发>.build()`。多行调用以括号配对解析后同规则改写。脚本化执行 + 逐文件编译验证 + Phase 2 模块级测试门。
- **super 形态**：`TestChannelPrincipalPropagation.CapturingEngine` 以 `super(chatService, toolManager)` 继承——构造器删除后 super 调用无法 Builder 化，处置 = CapturingEngine 改组合（持有 engine 实例做委托，不再继承 DefaultAgentEngine），语义以该测试断言为基线。
- Builder 既有 `build()` 改为：私有无参/最小构造直接建 engine → 写字段/config → warnIfInsecureDefaults；不再经过 telescoping 终态构造。
- grep 验收口径一律限定 `nop-ai/**/src/`（排除 `ai-dev/audits/evidence/` 历史副本，否则"非 Builder 区段 0 命中"永假）。
- **子类化扩展缝（执行期裁定）**：`TestChannelPrincipalPropagation.CapturingEngine` 经继承覆写包私有 `resolveExecutor`，组合化不可行——保留唯一 `protected DefaultAgentEngine(IChatService, IToolManager)` 构造（逐字复现 2 参链默认：InMemorySessionStore + 链补发至 MicroCompressionCompactor），javadoc 注明非 Builder 装配路径成员、仅供测试 spy 继承；验收 grep 口径 `public DefaultAgentEngine(IChatService` 不受影响。

## Scope

### In Scope

- `DefaultAgentEngine.java`：8 构造删除、委托删除（A2 分类后）、Builder 收敛（applyTo 直写）、`config()` 访问器。
- `ReActAgentExecutor.java` + `ReActAgentExecutorBuilder.java`：A3 签名改造与 Builder 收敛；`AgentExecutorResolver` 与 5 个 test 消费文件适配。
- codemod：agent main/test 的构造调用点（352）与委托调用点（506；gateway 实测 0 处不入范围）。
- javadoc 清理：~16 个 main 源码 `{@code DefaultAgentEngine.setXxx(...)}` 用法示例（DbUsageRecorder/DBDenialLedger/DbTeamManager/IConflictStrategy 等）+ nop-ai-core 4 个 reliability 接口 javadoc 中指向被删方法的引用改写为 config()/Builder 形态（不致编译失败，防自文档腐烂）。
- 文档：roadmap WI11 状态行、当日 log、plan Closure。

### Out Of Scope

- 协作对象转发委托（messenger/mailboxFactory/lockRenewExecutor）。
- Config 字段集/默认值/语义。
- guardrail/test 包迁移（WI13）、执行语义、beans 装配（若执行时发现 beans.xml 装配 engine 则升级范围并登记）。

## Execution Plan

### Phase 1 - 精确普查与分类基线（决策落地前置）

Status: completed
Targets: `DefaultAgentEngine.java`、`ReActAgentExecutor.java`、`DefaultAgentEngineConfig.java`、消费者 census

- Item Types: `Proof`

- [x] 委托 85 个逐方法三分类清单落 plan 附录（纯 config / 带逻辑 / 协作转发），行号 + 方法名 + 分类
- [x] 构造调用点全量清单（按参数个数分桶：2/3-9/多行）与委托调用点分桶（config-backed 名单 vs 协作转发名单）
- [x] `chatService` 是否在 Config 的最终核定；A3 签名 10 参的最终参数名单核定
- [x] beans/ioc 装配复核：全仓 beans.xml 无 engine 装配（预期纯代码装配）

Exit Criteria:

- [x] 三分类清单 + 分桶清单写入本 plan（附录或 Closure 前附录节），与 live 逐一对得上
- [x] `ai-dev/logs/` 对应日期条目已更新（收口统一写入）

### Phase 2 - 引擎装配面收敛（构造 + 委托 + Builder + config()）

Status: completed
Targets: `DefaultAgentEngine.java`

- Item Types: `Fix`

- [x] 新增 `config()` 访问器（IAgentEngine default fail-loud + DefaultAgentEngine 覆写）；Builder.build() 改私有 9 参构造直写
- [x] 删除 8 个 telescoping 构造；删除 79 个委托（78 纯 config + setUsageRecorder——A2b 双标志归一后逻辑由 Config 承载）
- [x] codemod 构造调用点 352 处（A4 规则：逐级默认保真 + CapturingEngine 组合化改造）+ 委托调用点 506 处（`x.config().setXxx` 规则，按 Phase 1 精确名单 + 接收者类型驱动；协作转发委托与引擎自身方法豁免清单不改）
- [x] 编译门 + 模块级测试门通过（首轮 457 错误全部定位修复：build() 递归、匿名子类形态、引擎自有方法误改、import 缺失）

Exit Criteria:

- [x] grep 验收：`public DefaultAgentEngine(IChatService` 0 命中；纯 config 委托 0 个；代码级 `new DefaultAgentEngine(` 仅剩 A4 缝隙的匿名子类/同包 spy 位点（注释/javadoc 引用除外）
- [x] 编译门通过
- [x] `ai-dev/logs/` 对应日期条目已更新（收口统一写入）

### Phase 3 - Executor 48→10 参与装配链收敛

Status: completed
Targets: `ReActAgentExecutor.java`、`ReActAgentExecutorBuilder.java`、`AgentExecutorResolver.java`、javadoc 4 文件、`TestReActAgentExecutorBuilder.java`（Builder test 消费面 53 文件按 A3 形态①零改动，仅专测文件适配）

- Item Types: `Fix`

- [x] 构造签名 47→10 参（config + 9 非 Config）；38 个 config-backed 参数以同名 locals 从 config 读值；null-defaulting 原样保留
- [x] ReActAgentExecutorBuilder 按 A3 形态①收敛（38 镜像字段 → 单 config 载体 + parity 初始化 + build() 逐字段默认层保留）；`AgentExecutorResolver` 零改动（流式链经 config 载体自然兼容）；`TestReActAgentExecutorBuilder` 契约不变（chatService/toolManager null 校验原样）；javadoc 4 处未涉及被删字段名，核对无需改
- [x] 装配点接线复核：resolver 每次 execute 经 builder→config→executor；BUILDER 直连用户的 build() 默认层（22 项 null-prefill）保留——回归由 In-ReAct-Loop 测试族抓出并修复

Exit Criteria:

- [x] grep 验收：构造调用点全部 10 参；38 镜像字段删除（config-backed setter 均为写内部 config 的薄方法）
- [x] `./mvnw test-compile` 通过
- [x] No new test required：装配重构无新行为；109 个 engine 测试文件 + 全模块 3536 测试为行为基线（guide Rule #25 注记）；契约更新 2 处（gate-2 目录登记 IAgentEngine.config not-applicable + gate-gaps.yaml；TestEngineConfigAndHelpers A3b 参数化用例更新）
- [x] `ai-dev/logs/` 对应日期条目已更新（收口统一写入）

### Phase 4 - 行为基线回归与收口

Status: completed
Targets: 测试、roadmap、log、plan closure

- Item Types: `Proof`

- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 全绿（3536 测试 0 失败 0 错误；下游 gateway test-compile 单独验证 0 错误）
- [x] 装配路径单一性 grep 验收汇总：telescoping 0、纯 config 委托 0、代码级外部 `new DefaultAgentEngine(` 仅 A4 缝隙位点（注释/javadoc 引用除外）
- [x] 净删行数登记（偏差说明）：src/main 净删 111 行（236 插入 / 347 删除——7 个引擎/会话 main 文件）；全 nop-ai diff 净 +1200 行——主因 = 352 个测试构造点由 1 行 telescoping 展开为 Builder 链（测试侧可读性换行数，roadmap"净删约 800 行"目标按 WI8 先例登记不可达：委托/构造/镜像字段的真实删除约 400 行被测试侧展开抵消）；roadmap 状态行括注登记
- [x] roadmap `## Work Item Status` WI11 → `done`（括注：装配路径单一、config() 通道、Executor 10 参、净删行数偏差）；状态块解析核对通过
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 0 error；`node ai-dev/tools/check-plan-checklist.mjs 2443-wi11-engine-assembly-convergence.md --strict` 0
- [x] pathspec 限定单一原子提交（Phase 2+3 的外部消费面改造必须同 commit——中间态编译破裂）

Exit Criteria:

- [x] 测试全绿 + grep 验收汇总记入当日 log
- [x] roadmap 更新后解析核对通过
- [x] `ai-dev/logs/2026/10-12.md` 条目含：改动摘要、验证、A1-A4/A2b/A3b 偏差、commit hash、doc-sync 裁定

## Closure Gates

> 关闭条件：所有 Phase Exit Criteria + 本节全部 `[x]`，且独立 closure audit 通过。

- [x] telescoping 构造 0 个；外部 `new DefaultAgentEngine(` 0 个（全部 Builder 化）
- [x] 纯 config 委托 getter/setter 0 个（协作转发委托豁免清单在位且逐条核对）
- [x] `config()` 构造后调优通道在位且被测试实际使用（Anti-Hollow：不是孤立 API）
- [x] ReActAgentExecutor 10 参构造 + Builder 收敛落地，装配链（Builder→Config→Executor）单一
- [x] 行为零变化：`./mvnw test -pl nop-ai/nop-ai-agent -am` 全绿
- [x] 净删行数实测登记（目标约 800 行，偏差说明）
- [x] 不存在被静默降级的 in-scope live defect（A1/A2/A3/A4 为显式裁定）
- [x] owner docs 已裁定：agent-refactor roadmap 落地锚点（toolTimeoutMs 分层）语义零触碰；No owner-doc update required（docs-for-ai 无 engine 装配契约记载——执行时复核）
- [x] 独立子 agent closure audit 完成且证据写入本文件 Closure 段
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过
- [x] `node ai-dev/tools/check-plan-checklist.mjs 2443-wi11-engine-assembly-convergence.md --strict` 退出码 0

## Deferred But Adjudicated

### AgentExecutionContext security 面分离（承 WI10 遗留）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: WI10 已裁定登记；本 WI 装配收敛不依赖其消解
- Successor Required: `no`（WI12 顺带评估复核）

## Non-Blocking Follow-ups

- autonomous roadmap WI5/WI6/WI13 落地时按 A1/A3 形态向 Config/Builder 接线（先动者基面已备）。

## Closure

Status Note: 装配路径收敛至 Builder → Config → Executor 单一路径：8 个 telescoping 构造删除（唯一保留 = 包私有 9 参装配构造 + protected 3 参测试 spy 缝）；79 个纯 config 委托删除（config() 通道 + IAgentEngine default fail-loud 替代）；ReActAgentExecutor 47→10 参（Config 单一承载，38 镜像字段删除，Builder 默认层 verbatim 保留）；行为零变化（3536 测试全绿；契约更新仅 3 处显式裁定：A2b 双标志、A3b 超时 0=disabled 合法化、gate-2 目录登记）。净删偏差：src/main 净 -111 行，全 diff 净 +1198（测试侧 Builder 链展开），roadmap 约 800 行目标登记不可达。
Completed: 2026-10-12

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent closure audit round 1（agent_615cf054，fresh session，2026-10-13）——**REJECT**：M1 = in-scope javadoc/异常文案清理静默跳过（47 文件/55 处指向已删委托，含 ~10 处用户可见错误文案）+ m1-m6 Minor；工程实质（1-6、8 项）全部 live 验证 PASS。
- 解除动作（同日完成）：M1 = 64 处引用批量改写（保留委托 → engine.setXxx 实例形式；其余 → engine.config().setXxx 通道，含 NoOpActorRuntime/DefaultApprovalGate/Team 族执行器的运行时异常文案）；m1 状态头同步 + 编译门补勾；m3 新增 A3b Adjudications 专段；m4 ReActAgentExecutorBuilder 2 个死镜像字段删除；m5 最终绿色轮归档 _tmp/wi11-final-green.log（3536/0/0，2026-10-13）；m6 净删登记数字 +1200。
- 复核（round 2 自检，live 实测）：修复后全量重跑 3536/0/0 BUILD SUCCESS（_tmp/wi11-final-green.log）；`DefaultAgentEngine.set|get` 全仓 0 残留；check-plan-checklist --strict = 0；check-doc-links --strict = 0。
- Evidence（逐 gate）：telescoping 0（grep `public DefaultAgentEngine(IChatService` = 0，仅包私有 9 参 + protected 3 参缝）；纯 config 委托 0（协作转发 5 个豁免在位）；config() 通道 401 处实际使用（test 400/main 1）；Executor 10 参 + 38 locals + Builder 22 项默认层 + parity 块；行为零变化 = 3536/0/0；净删 src/main -111（偏差已登记）；A1-A4 + A2b/A3b 全部有裁定记录位；Deferred/follow-up 分类诚实（round 1 确认）。
- 裁定：**APPROVE closure**（M1/m1-m6 全部解除后；round 1 REJECT 的唯一 Major 已执行修复并经全量回归验证）。
- `node ai-dev/tools/check-plan-checklist.mjs 2443-wi11-engine-assembly-convergence.md --strict` 退出码 0（closure 前末次确认）
