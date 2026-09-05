# 1 flow DSL 编译器产品化收敛（xpl source 取消语义 / 错误源位置锚点 / per-transform parallelism 消费）

> Plan Status: completed
> Mission: nop-stream-productization
> Work Item: item 29
> Last Reviewed: 2026-09-04
> Source: roadmap `ai-dev/backlog/nop-stream-productization-roadmap.md` item 29（来源 item 11 审计报告 `ai-dev/analysis/2026-09/2026-09-01-nop-stream-rocksdb-flow-fraud-example-audit.md` §2.5/§3.2）；审计记录 `ai-dev/audits/2026-08-13-0805-open-audit-nop-stream-invariant-loop.md` AR-14
> Related: `ai-dev/plans/nop-stream-productization/2026-09-01-1457-3-rocksdb-flow-fraud-example-audit.md`（FL-1/FL-2 过渡修复的来源 plan）

## Purpose

把 item 11 审计遗留的 flow DSL 编译器三项结构性缺口收口：内联 xpl source 的取消语义可观察、flow 构建/校验错误携带源位置锚点（file:line）、`transforms/@parallelism` 声明值被引擎真实消费（替代 FL-2 过渡 fail-fast）。

## Current Baseline

（live 核对于 2026-09-04，行号为当日基线）

**xpl source 取消语义**：
- `XplSourceFunction`（`nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/functions/XplSourceFunction.java:26-54`）：`volatile boolean running` 标志（:32）；`run()` 调 `body.call1(null, ctx, scope)`（:41-45）**从不读取 running**；`cancel()`（:47-50）是标志的唯一非测试写入者，但引擎只在 `StreamSourceOperator.close()`（core `operators/StreamSourceOperator.java:308-316`）中调用 cancel——即 run() 已退出之后，标志对运行中的 xpl body 无任何可观察效果。
- `SourceFunction.SourceContext`（core `common/functions/source/SourceFunction.java:55-90`）只有 `collect/collectWithTimestamp/emitWatermark/markAsTemporarilyIdle/getProcessingTime`，**无取消访问器**。
- 引擎取消的真实机制：mailbox cancel 标志在源下一次 `collect()` 时协作生效（`StreamSourceOperator.drainControlMails` :276-285 → `exec.isCancelled()` 抛 `ERR_STREAM_CHECKPOINT_ABORTED`）或线程中断（`SubtaskTask.cancel()` :166 `t.interrupt()`）。不调用 collect 的 xpl 循环体既读不到标志也收不到协作异常，只能依赖中断。
- **LOCAL env 入口无取消通道**：core `StreamExecutionEnvironment.execute()`（:254-377）阻塞等待完成，无 cancel API；`MailboxExecutor.signalCancel()` 的生产调用者全部在 nop-stream-runtime（`SupervisionLoop:507`、`GraphModelCheckpointExecutor:972`、`TaskManager:1093`、`JobCoordinator`），flow 模块不依赖 runtime——**取消 e2e 必须落在可触达 runtime 取消面的宿主**（见 Phase 1 第 6 项裁定）。
- `SourceContext` 实现者清点（接口演进爆炸半径）：分布于 **7 个模块、约 16-17 个文件、30+ 个匿名实现**——core（1 生产匿名 + 测试）、flow 测试 ×1、connector 测试 ×4、connector-batch ×1、debezium 测试 ×4（单文件最多 7 个）、runtime 测试 ×2（单文件 8 个）、fraud-example ×3。abstract 形态将迫使全部测试源码机械迁移。
- 既有 SourceFunction 取消契约模式（volatile 标志 **被 run 循环读取**）：`DirectoryFileSourceFunction`（cancel :230-233，run 内 :119/:124/:169/:173 检查）、`DebeziumCdcSourceFunction`（running :85，循环 :199-203）、`MessageSourceFunction`（:59/:178-180）、core `CollectionSourceFunction`。XplSourceFunction 是唯一无法满足该模式的实现。
- 现有测试：`TestXplFunctionWrappers.sourceFunctionRunsBodyWithSourceContext`（flow test :116-155）只断言包装器自身 `isRunning()` 标志翻转，不证明 body 可观察取消。

**错误源位置锚点**：
- flow 构建错误全部只带 element/edge id，**零** `getLocation()` 调用（flow 模块 grep 无命中）。抛错点：`StreamModelDslBuilder`（FL-2 per-transform parallelism :350-366、缺 id :273-276、重复 id :277-280、边端点未知 :295-304、DAG 环 :331-348、边声明矩阵 :381-423、`requireSingleInput` :451-473、bean 未找到/类型不匹配 :713-723、FL-1 :504-528/:603-627）+ `AdvancedTransforms`（实测 29 处 `throw new StreamException`，仅 `resolveWindowAssigner` :218 附近需把 transform 模型穿参）。
- **模型对象已携带 SourceLocation**：flow 模型类继承 `AbstractComponentModel`（实现 `ISourceLocationSetter`），`DslBeanModelParser.parseObject`（nop-xlang `xdsl/json/DslBeanModelParser.java:70-71`）对每个 xdef bean 节点回填 `setLocation(node.getLocation())`——数据已在，只是错误路径未消费。
- 平台既有错误定位模式：`NopException.loc(SourceLocation)` / `.source(ISourceLocationGetter)`（nop-api-core `exceptions/NopException.java:262-271`，`param()` 自动捕获 :393-398，渲染为 `@_loc=` 后缀 :339-340）；stream 家族先例 `ICepPatternGroupModel.java:27`。`StreamException` 继承链最终继承 `NopException`。
- pre-submit validate 包（`flow/validate/`）：`ValidationIssue` 无 location 字段（:25-94）；`StreamConfValidator.toIssue()`（:311-315）**丢弃**了 layer-1 解析错误携带的 loc；`describe()` 输出无 file/line。设计文档 `pre-submit-validation-design.md` 的输出契约（D6/D7）未定义位置字段。

**per-transform parallelism**：
- 声明面已存在：`stream.xdef`（nop-xdefs `_vfs/nop/schema/stream/stream.xdef:104`）`transforms/*@parallelism`（可选 int）；模型字段 `_StreamTransformModel` `Integer parallelism`。
- core `Transformation.parallelism` 为 **final**（core `transformation/Transformation.java:31`，仅构造器赋值 :54-59）；不存在 `DataStream.setParallelism`（仅 `forceNonParallel()` → `lockParallelismToOne()`）；所有 `*Transformation` 在 DataStream 调用点被盖 `environment.getParallelism()` 章。
- flow builder 把 model 级 parallelism 映射为 `env.setParallelism`（`StreamModelDslBuilder.build` :153-156），随后 FL-2 fail-fast（:350-366，`ERR_STREAM_NOT_IMPLEMENTED`）拒绝「声明值 ≠ stream 级生效值」；测试 `TestStreamFlowAuditFixes.perTransformParallelismMismatchFailFast`（:158-171）+ 正控制用例。
- **下游 per-vertex 机制已完备**（引擎侧无需新建）：`StreamGraphGenerator.resolveParallelism`（:428-433）逐节点；`JobGraphGenerator.canChain` 并行度不等即断链（:328-332）、`createJobVertex` 逐顶点（:419-466）；`GraphExecutionPlan.build` srcP×tgtP 分区矩阵 + 逐 subtask 拆分（:296-496）；`KeyGroupAssignment` 按 (maxParallelism, parallelism) 分配。混合并行度已有程序化证据：`TestStreamGraphGenerator.testDifferentParallelism`（:308-324）、`TestJobGraph.testDifferentParallelismVertices`（:203-220）。2PC sink 门禁读生效并行度（`StreamGraphGenerator.transformSink` :318-328）。

**真正剩余的 gap**：① SourceContext 无取消访问器，xpl body 无法实现文档声称的 `while(running)` 模式；② 错误不携带源位置（数据已有、未消费）；③ core 无 per-operator 并行度入口，flow 声明值无消费路径（FL-2 以拒绝兜底）。

## Goals

- 内联 xpl source 的取消语义可观察：xpl body 能通过 `SourceContext` 观察取消状态并退出循环，接线到引擎 mailbox 取消标志（与既有协作取消机制同一真值源）。
- flow 构建/校验错误携带源位置：`StreamModelDslBuilder`/`AdvancedTransforms` 抛出的构建错误与 `conf-validate` 输出包含 file:line 锚点。
- `transforms/@parallelism` 声明值被真实消费：每个 transform 可独立生效并行度，FL-2 过渡 fail-fast 退役；行为从 xdef 声明 → builder → StreamNode → JobVertex → 执行全程钉定。

## Non-Goals

- 不改分区/rescale 语义（per-vertex 机制已存在，本计划只接线声明面）。
- 不改 bean/xpl 函数解析语义与 Xpl*Function 家族的参数绑定行为。
- 不为 map/filter/sink 等非 source 包装器添加 cancel（`cancel()` 只定义在 `SourceFunction` 上）。
- 不做 per-transform parallelism 的分布式专项演练（多 JVM per-vertex 机制已有 C2 等覆盖；本计划以 LOCAL e2e + plan 级断言钉定接线）。
- 不改 `stream.xdef` 结构（`transforms/@parallelism` 声明已存在）。

## Scope

### In Scope

- `nop-stream-core`：`SourceContext` 取消访问器（含 `StreamSourceOperator` 上下文接线）；per-operator 并行度 API 入口（形态由 Phase 2 Decision 裁定）。
- `nop-stream-flow`：`XplSourceFunction` 语义修正 + javadoc；`StreamModelDslBuilder`/`AdvancedTransforms` 错误位置锚点；FL-2 退役与并行度接线；`ValidationIssue`/`StreamConfValidator` 位置传播。
- 测试：core/flow 单测 + LOCAL e2e。**e2e 宿主**：Phase 1 取消语义 e2e 与 Phase 3 conf-validate 集成测试落位 fraud-example 测试代码（其测试 scope 依赖 runtime 取消面与既有 `TestConfValidatePreSubmitE2E` 先例）——**仅测试代码触碰 fraud-example**，不改其产品代码。
- 如 Phase 1 Decision 选 abstract 接口形态：6 模块 ~15 文件测试源码的机械式 implementer 迁移纳入本 plan scope（默认倾向 default 形态规避，见 Decision）。
- owner docs：`ai-dev/design/nop-stream/stream-dsl-design.md`、`docs-for-ai/03-modules/nop-stream.md`（用户指南相关段落）、`ai-dev/design/nop-stream/pre-submit-validation-design.md`。

### Out Of Scope

- `nop-stream-runtime`/分布式部署路径改动。
- fraud-example 场景拓扑变更（仅允许新增测试场景文件）。
- 既有 2PC sink P>1 门禁的任何放宽（`ERR_STREAM_2PC_SINK_PARALLELISM_NOT_SUPPORTED` 保持）。

## Execution Plan

### Phase 1 - xpl source 取消语义（SourceContext 取消访问器 + 包装器修正）

Status: completed
Targets: `nop-stream-core/.../functions/source/SourceFunction.java`、`nop-stream-core/.../operators/StreamSourceOperator.java`、`nop-stream-flow/.../builder/functions/XplSourceFunction.java`、相关测试

- Item Types: `Fix | Decision | Proof`

- [x] Decision：`SourceContext` 取消访问器的接口形态——**裁定：default 方法**（`isCancelled()`，default 返回 false + javadoc "该上下文无法观察取消"）。裁定记录（2026-09-04）：全仓 implementers 清单与 Current Baseline 一致——core 生产匿名实现 ×1（`StreamSourceOperator.run`，本 plan override）+ core 测试匿名实现若干 + flow 测试 ×1 + connector 测试 ×4 + connector-batch ×1 + debezium 测试 ×4 + runtime 测试 ×2 + fraud-example ×3，约 30+ 个匿名实现分布在 7 模块；abstract 形态将迫使全部测试源码机械迁移，收益仅为形式纯洁，故取 default 形态。硬约束落实：① 生产路径 `StreamSourceOperator` 的匿名 SourceContext **已 override** 并读 `MailboxExecutor.isCancelled()`（与 `drainControlMails` 同一真值源）；② flow 测试所用上下文 override 为可控值；③ 无生产路径依赖 default false。
- [x] `StreamSourceOperator` 的 SourceContext 实现取消访问器（读 mailbox cancelled 标志；与既有 `drainControlMails` 抛错路径共存：循环条件轮询优雅退出、collect 时协作异常两条路径都合法）。
- [x] `XplSourceFunction` 修正：javadoc 中"body 自行检查 cancel 标志"的不可实现模式改为可实现的 `ctx` 轮询模式说明；`cancel()`/`isRunning()` 保留为 close 路径与测试面；`run()` 语义不变（body 同步执行）。
- [x] core 单测：signalCancel 后 `ctx` 取消访问器返回 true（**接线验证**：访问器确实反映 mailbox 标志，不是恒定值）；未取消时返回 false。注意进程级静态 registry/共享状态无涉，直接构造 mailbox harness（沿 `TestMailboxWiring` 先例）。（`TestSourceContextCancelAccessor`：同一 ctx 实例 signalCancel 前后取值翻转 + 无 mailbox 上下文返回 false + default 语义）
- [x] flow 单测：xpl body 以 `while(!ctx 取消)` 模式循环，取消后 body 退出、`run()` 正常返回（TestXplFunctionWrappers 扩展，用真实/仿真 SourceContext 而非仅包装器自身标志）。（`sourceFunctionBodyExitsLoopViaContextCancelPoll`：可控 override 上下文 3 轮后翻转）
- [x] LOCAL e2e（**端到端验证**，宿主裁定：落位 fraud-example 测试代码——core env 入口无取消通道、signalCancel 生产调用者全在 runtime，fraud-example 测试 scope 已依赖 runtime）：构建带内联 xpl source 循环体（轮询取消访问器 + 周期 collect）的作业，经可触达的 runtime 取消面（如 LOCAL checkpoint executor 的 abort 路径或等价 signalCancel 入口）触发取消，断言源任务在超时上限内优雅退出（非仅依赖线程中断/collect 协作异常）、任务达终态。备选（如 runtime 取消面在 LOCAL 测试中不可达）：flow 内 harness 级测试（XDSL build → 手工驱动 `StreamTaskInvokable` → mailbox `signalCancel` → 断言源退出），如实命名为 harness 级取消链验证、不称 env 入口端到端——两形态择一，选择与理由写入执行日志。【**形态选择：主形态（runtime 取消面）**——`TestXplSourceCancelE2E`（fraud-example 测试代码 + 测试场景文件 `fraud-cancel-xpl.stream.xml`）：tiny interval/timeout 使 pending checkpoint 必然超时 → `GraphModelCheckpointExecutor.registerLocalAbortHandler` → `signalCancel()`（生产取消配方）；xpl body 忙轮询 `ctx.isCancelled()`（无阻塞调用，中断无法结束循环——唯一出口是访问器观察到取消）；断言 execute() 有界终止（~1.7s）、cause chain 含 `ERR_STREAM_CHECKPOINT_ABORTED`（非 supervision-task-failed 即非中断驱动退出）、数据面元素已达 sink。理由与备选比较见 `ai-dev/logs/2026/09-04.md`】

Exit Criteria:

- [x] xpl body 经 `SourceContext` 观察取消的模式有真实可执行实现与测试证明（非仅 javadoc 声明）
- [x] 生产 SourceContext 的取消访问器与 mailbox cancelled 标志同源（测试断言 signalCancel 前后取值翻转）
- [x] 端到端：从 XDSL/env 入口提交带内联 xpl source 的作业 → 取消 → 任务终态，全路径测试绿
- [x] **无静默跳过**：接口形态 Decision 落档（abstract 或 documented default），不存在"恒 false 但当作正常"的生产路径
- [x] `stream-dsl-design.md` 增补内联 xpl source 取消语义段（§5.1）；`docs-for-ai/03-modules/nop-stream.md` 用户指南 xpl source 模式同步（落位用户文档族 `nop-stream-user-guide.md`「bean / xpl 双函数形态」节——`nop-stream.md` 自身头部声明用法内容归属用户文档族，取消模式属"怎么用起来"）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - per-transform parallelism 消费（core API 入口 + flow 接线 + FL-2 退役）

Status: completed
Targets: `nop-stream-core`（DataStream/Transformation 并行度入口）、`nop-stream-flow/.../builder/StreamModelDslBuilder.java:350-366`、`AdvancedTransforms.java`、相关测试

- Item Types: `Decision | Fix | Proof`

- [x] Decision：core per-operator 并行度 API 入口形态。**裁定（2026-09-04）**：① `DataStream.setParallelism(int)`（接口面；`SingleOutputStreamOperator`/`KeyedStream` 协变覆盖，source 的 `DataStreamSource` 经 `SingleOutputStreamOperator` 面覆盖——覆盖全部 transform 构建点返回类型）+ `DataStream.sink(fn, parallelism)` 注册重载（sink transformation 终端无下游流对象，声明值随注册传入）；② `Transformation.parallelism` 改为**受控可变**（final → guarded setter）：算子构造发生在 `map()`/`filter()` 内部、调用方拿到流对象之前，逐构造器传参需复制全部 builder 方法重载——受控 setter（Flink DataStream API 同款取舍）是裁定形态；守卫 `>=1` typed 拒绝、`parallelismLocked` 且非 1 值 typed 拒绝（锁语义不削弱，`resolveParallelism` 仍强制锁顶点为 1，论证见 `Transformation.setParallelism` javadoc）；③ `forceNonParallel()`/lock 不削弱（单测覆盖 locked 下 setParallelism(1) 放行、setParallelism(2) typed 拒绝、锁顶点 StreamNode 解析为 1）；④ flow builder 每个 transform 构建点（base builders + `AdvancedTransforms` 全系）用声明值覆盖 env 级盖章，未声明继承 stream 级；`<window>` 虚拟元素无消费者 → 声明即 fail-fast（沿 FL-1 政策）；HASH 边目标声明值同步到隐式 partition 顶点（防 FORWARD modulo 伪分布）。
- [x] core API 实现 + 单测（含与 `parallelismLocked`/`forceNonParallel` 交互、与 `canChain` 断链交互的用例）。（`TestDataStreamParallelism` 7 用例：map/keyBy/source 盖章、sink 重载到 JobVertex、<1 typed 拒绝、锁交互×2、混合并行度到 StreamNode+断链 JobVertex；`TestStreamGraphGenerator.testTwoPhaseCommitSinkRaisedToParallelism2ViaSetParallelismIsRejected`）
- [x] flow builder 接线：`transforms/@parallelism` 声明值 → per-operator 生效；FL-2 fail-fast（`StreamModelDslBuilder.java:350-366`）退役删除。
- [x] 重写/调整 FL-2 测试：`perTransformParallelismMismatchFailFast` 改为消费语义断言（声明 4 ≠ stream 级 1 时 map 顶点并行度 = 4）；保留"声明值 = 生效值放行"正控制；新增"未声明继承 stream 级"用例。（`TestStreamFlowAuditFixes`：`perTransformParallelismMismatchIsConsumed`（JobVertex 级断言 sink=4/source=2）+ `perTransformParallelismUndeclaredInheritsStreamLevel` + 保留正控制）
- [x] flow→core 行为钉定测试：混合并行度 XDSL 模型 build 后逐 StreamNode/JobVertex 并行度断言（对齐 `TestStreamGraphGenerator.testDifferentParallelism` 的程序化先例）；并行度相等的相邻 transform 仍可 chain。（`TestPerTransformParallelismWiring` 3 用例：HASH 边进宽目标 partition 顶点对齐、`<keyBy>` 声明值到 partition 顶点、等并行度 source→map→sink 单顶点 chain）
- [x] LOCAL e2e（**端到端验证**）：XDSL 场景（source P=1 → **keyBy(HASH 边)** → map/aggregate P=N(>1) → 非 2PC sink）经 `env.execute()` 到 sink 输出完整、结果精确。**拓扑必须使用 HASH 分区边**：FORWARD 边在 srcP≠tgtP 时按 modulo 语义把数据集中到 target subtask 0（`GraphExecutionPlan.java:65` 文档语义），会产出"N 个 subtask 只有 1 个有数据"的结构性绿灯伪证明；HASH 边 + 断言数据分布到 >1 个 subtask（per-key/subtask 计数）才构成 per-transform parallelism 真实消费的行为级证据。plan/执行侧同时断言 per-vertex subtask 数与声明一致。（`TestPerTransformParallelismE2E`：HASH 边 40 keys 均匀分布 4 subtask、共享 map bean 按线程观察 ≥2 subtask 且 latch 强制重叠、40 元素逐元素精确 once、同声明重建 JobGraph→GraphExecutionPlan 逐顶点 subtask 数==声明值）
- [x] 2PC sink 门禁回归：声明并行度路径不放宽 `ERR_STREAM_2PC_SINK_PARALLELISM_NOT_SUPPORTED`（既有测试保持绿）。（新增 `setParallelism` 抬升 2PC sink 到 2 仍被拒 + 既有 gate 测试族全绿）

Exit Criteria:

- [x] `transforms/@parallelism` 声明值从 xdef → builder → StreamNode → JobVertex → 执行全程生效（逐级断言测试存在且绿）
- [x] FL-2 fail-fast 代码删除，其测试改写为消费语义（无遗留死测试）
- [x] 端到端：XDSL 声明混合并行度的作业从入口执行到 sink 输出，输出精确性断言绿
- [x] **接线验证**：per-vertex 并行度断言基于下游消费点（StreamNode/JobVertex/GraphExecutionPlan），不只验证 builder 字段传递
- [x] `stream-dsl-design.md` 并行度解析顺序（transform 级 > stream 级 > 默认 1）落档（§5.2）；`docs-for-ai/03-modules/nop-stream.md` 用户指南 per-transform parallelism 用法同步（落位用户文档族 `nop-stream-user-guide.md`「bean / xpl 双函数形态」节）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - build 期错误源位置锚点（builder 抛错 + validate 输出携带 file:line）

Status: completed
Targets: `nop-stream-flow/.../builder/StreamModelDslBuilder.java`、`AdvancedTransforms.java`、`nop-stream-flow/.../flow/validate/ValidationIssue.java`、`nop-stream-flow/.../flow/validate/StreamConfValidator.java`、相关测试

- Item Types: `Fix | Proof`

- [x] `StreamModelDslBuilder` 全部抛错点（Current Baseline 所列清单）附加模型对象位置：`.loc(t.getLocation())` / `.source(t)`（沿 `ICepPatternGroupModel.java:27` 先例与 `NopException` 自动捕获机制）；`StreamEdgeModel` 错误同样带边节点位置。（36 处 `.loc(...)`：transform 19 + edge 8 + model 根 9；bean 解析错误经新增 `resolveBean` 包装锚定到声明元素）
- [x] `AdvancedTransforms` 抛错点同样处理（实测 29 处 + Phase 2 新增 window-parallelism fail-fast 1 处 = 30 处全部携带；`resolveWindowAssigner` 已把 `t` 穿参）。
- [x] `ValidationIssue` 增加 sourceLocation 字段（file/line）；`StreamConfValidator.toIssue()` 从 `NopException.getErrorLocation()` 填充（不再丢弃 layer-1 解析位置）；`describe()` 渲染位置锚点。（`@ <file>:<line>` 后缀，null 不渲染）
- [x] 单测：每错误族至少一条断言异常携带位置（`@_loc` 或 loc 参数，file:line 与 fixture 模型实际位置一致）；validate 报告/`conf-validate` 输出含位置（含 layer-1 与 layer-2 两层来源）。`conf-validate` CLI 集成测试落位：扩展 fraud-example 既有 `TestConfValidatePreSubmitE2E` 或 runtime 既有 `TestStreamConfValidateCommand`（**仅测试代码触碰**，CLI 入口本体在 runtime 不改）。（flow `TestErrorSourceLocationAnchors` 8 用例：2 个 XDSL fixture 精确行号 + 5 族程序式代表覆盖 + 无位置不伪造；`TestStreamConfValidator` 增层 1/层 2 位置断言 + 连接器模式 null 锚点断言；fraud-example `TestConfValidatePreSubmitE2E.badEdgeReportCarriesFileLineAnchor` CLI 集成：exit 1 + 报告含 `file:line`）
- [x] 位置缺失路径显式裁定：无位置的合成/编程式模型错误输出不含伪位置（不允许出现伪造 file:line）。（`syntheticModelWithoutLocationProducesNoFakeAnchor` + connector-mode null 锚点断言）

Exit Criteria:

- [x] Current Baseline 清单中的 builder/AdvancedTransforms 抛错点全部携带真实模型位置（逐点测试或代表性全覆盖断言）
- [x] `conf-validate` 对坏模型的报错输出包含 file:line（集成测试断言 exit code 与输出内容）
- [x] `pre-submit-validation-design.md` 输出契约更新（ValidationIssue 位置字段 + describe 渲染格式，D7 行）；`stream-dsl-design.md` 错误报告段落同步（§5.3）
- [x] 纯位置传播不改变既有错误码/参数集合（既有错误断言测试不因位置附加而失败，必要时按新增字段最小调整）（flow 115 + fraud-example 113 全绿，无既有断言调整）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 三项结构缺口（取消语义/源位置锚点/parallelism 消费）全部落地且各有 focused 测试与 e2e 证明
- [x] FL-2 过渡 fail-fast 退役，无残留死代码/死测试
- [x] 行为契约变更已同步 owner docs（stream-dsl-design.md §5.1-5.3 / pre-submit-validation-design.md D7 / docs-for-ai nop-stream 页 + 用户文档族 nop-stream-user-guide.md）
- [x] `./mvnw test -pl nop-stream -am -T 1C` 全绿（BUILD SUCCESS，30 模块 reactor，2026-09-04）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream/nop-stream-flow --severity high` exit 0（core 有改动则加跑 `--module nop-stream/nop-stream-core`；模块路径必须是 `nop-stream/nop-stream-<m>` 相对形式，短名会被工具静默解析为不存在的目录导致假绿）（flow 与 core 双跑均 exit 0，0 findings）
- [x] `node ai-dev/tools/check-nop-stream-invariants.mjs` exit 0（新增/迁移类如触发注册表 pin 更新须同步）（exit 0，无 pin 更新触发）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0（No errors found）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` exit 0（见 Closure 节证据）
- [x] 独立子 agent closure-audit 完成并写入证据（含 Anti-Hollow：三链路从入口到出口运行时连通）（见 Closure 节证据）

## Deferred But Adjudicated

（起草时无；执行中如出现按Allowed classifications 分类并附理由）

## Non-Blocking Follow-ups

（起草时无）

## Closure

Status Note: 三项结构缺口全部收口且经独立 closure audit 复核——①内联 xpl source 取消语义可观察（SourceContext.isCancelled() default + 生产 override 读 mailbox 标志，e2e 经真实 runtime 取消面（checkpoint abort → signalCancel）证明 body 忙轮询（中断免疫）有界优雅退出；②build 期错误全量携带 file:line（builder 36 + AdvancedTransforms 30 处 .loc；ValidationIssue 位置字段 + conf-validate CLI 渲染；无位置不伪造）；③transforms/@parallelism 声明值全程消费（core setParallelism/sink 重载 + flow 全构建点接线，FL-2 fail-fast 退役，HASH 边 partition 顶点对齐，e2e 行为级分布证明 + 2PC 门禁不放宽）。全仓计划内测试与工具门禁绿。
Completed: 2026-09-04

Closure Audit Evidence:

- Reviewer / Agent: 独立 general subagent（fresh session，未参与实现）`ses_f97182870ffeDj4KEqJwkQfnRM`
- Evidence:
  - **CLOSURE-AUDIT: APPROVED**（0 Blocker / 0 Major / 1 Minor（= Closure 占位待填，本节即其完成）/ 2 Info（full reactor 未复跑——audit 独立复跑全部 plan 命名测试套件替代；Deferred/Follow-up 占位确认无隐瞒））
  - Phase 1 Exit Criteria 6/6 PASS：`SourceFunction.java:111-113` default isCancelled + javadoc；`XplSourceFunction.java:22-37` ctx 轮询模式；`StreamSourceOperator.java:264-266` 生产 override 读 `MailboxExecutor` volatile 标志（:38/:89-90）；`TestSourceContextCancelAccessor` 3/3（signalCancel 前后翻转断言）；`TestXplFunctionWrappers.sourceFunctionBodyExitsLoopViaContextCancelPoll` 绿；e2e `TestXplSourceCancelE2E` 绿（有界 execute + `ERR_STREAM_CHECKPOINT_ABORTED` cause chain + sink 数据面证明；fixture `fraud-cancel-xpl.stream.xml:30` `while (!ctx.isCancelled())`）；docs §5.1 + 用户指南
  - Phase 2 Exit Criteria 6/6 PASS：`stream.xdef:104` 声明 → `applyDeclaredParallelism`（builder :754 + 全构建点）→ `Transformation.setParallelism`（guarded :132-144）→ `resolveParallelism`（StreamGraphGenerator:428-433）→ JobVertex（:436 + canChain:328-332）→ GraphExecutionPlan N subtasks（:472-489）；FL-2 grep 0 残留；`TestDataStreamParallelism` 7/7、`TestPerTransformParallelismWiring` 3/3、`TestStreamFlowAuditFixes` 8/8、e2e `TestPerTransformParallelismE2E` 绿（40 keys 精确 once + ≥2 subtask latch + plan 侧 subtask 数）；2PC gate 回归绿；docs §5.2 + 用户指南
  - Phase 3 Exit Criteria 5/5 PASS：builder 36 + AdvancedTransforms 30 处 `.loc` 计数核实；`resolveWindowAssigner(owner, t, strategy)` 穿参（:184/:242）；`ValidationIssue.java:34,116-117` 位置字段 + 渲染；`StreamConfValidator.toIssue:317-320` 填充；`TestErrorSourceLocationAnchors` 8/8（含 no-fake-anchor :228）；`TestConfValidatePreSubmitE2E.badEdgeReportCarriesFileLineAnchor` 绿（6/6，exit 1 + file:line）；docs D7 + §5.3
  - **Anti-Hollow 三链路运行时连通**（audit 独立追踪）：a. 取消链 CONNECTED（default → 生产 override → mailbox volatile ← signalCancel ← `GraphModelCheckpointExecutor.registerLocalAbortHandler:931→:972` + `SupervisionLoop:507`，e2e 走生产 abort 路径且中断免疫 by construction）；b. 并行度链 CONNECTED（逐链接核实 + 行为级分布断言）；c. 位置链 CONNECTED（`DslBeanModelParser.java:71` setLocation → .loc → `NopException.loc():268` → toIssue → describe 渲染 → CLI 输出断言）
  - **诚实性**：Deferred/Follow-up 均为起草占位（"起草时无"），无 in-scope live defect 被静默降级
  - 工具门禁（audit 复跑）：scan-hollow flow/core exit 0（0 findings）；invariants exit 0；doc-links --strict exit 0；check-plan-checklist --strict exit 0（1/1 passed）
  - 实现侧验证：`./mvnw test -pl nop-stream -am -T 1C` 30 模块 reactor BUILD SUCCESS（2026-09-04）；`./mvnw clean install -pl nop-stream -am -T 1C -DskipTests` + typecheck/lint 占位命令通过

Follow-up:

- no remaining plan-owned work（`<window>@parallelism` fail-fast 与 union/sideOutput runtime-API-gap 为既有显式拒绝面，非本 plan 遗留；follow-up 归属不变）
