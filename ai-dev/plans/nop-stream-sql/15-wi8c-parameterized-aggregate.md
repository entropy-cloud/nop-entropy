# 15 WI8c 步骤2 参数化聚合面

> Plan Status: completed
> Last Reviewed: 2026-10-02
> Source: `ai-dev/backlog/nop-stream-sql-roadmap.md`（WI8c 行、A4、Cross-Cuting 4）、`ai-dev/design/nop-stream/sql-compiler-contract.md` §3.2（D13 回改条款）、`ai-dev/design/nop-stream/sql-landing-decision.md` §5
> Related: `ai-dev/plans/nop-stream-sql/11-wi8b-schemas-consumer.md`、`ai-dev/plans/nop-stream-sql/14-wi9-aggregate-eval.md`
> Owner: 仓库 owner（2026-10-02 执行指令委托）
>
> 审查修订记录：经独立子 agent 对抗性审查一轮修订（实测核验 10 项）——B-1 类型校验通路钉死（entry 增可选 schemaId、buildAggregate 经 StreamSchemaRegistry 解析列类型经 SPI 传入、类型不符=聚合函数不接受该参数类型：sum/avg 要数值、min/max 要 Comparable、count 任意、Unknown 跳过）；B-2 beans 注册按实测机制重写（src/main/resources/_vfs + 模块标记 + app-beans 惯例 + 自动装配证明测试）；M-1 端到端判据改两段式（flow 接线证明假 resolver 被调用 + sql 语义单测）；M-2 BeanContainer 三态钉死（isInitialized 护栏 + tryGetBeanByType + null 转 ERR_STREAM_INVALID_ARG）；M-3 补五 id 与 BaseRule.g4 对齐钉子；m-1 A4 bean 前置在 aggregatorRef 分支内真实执行；m-2 aggregateWithoutBeanFailsFast 改写为恰一双缺；m-3 恰一测试需 keyBy+window 上游的顺序前提；m-4 行号/日志项/签名移除。Status 由 draft 转 active。

## Purpose

新增 `aggregators` 注册表与 `<aggregate aggregatorRef>` 参数化聚合声明面（与 windowingStrategies 同构：纯参数描述符 + 稳定 ID），聚合语义复用 WI9 求值器；构建期逐项 fail-fast（未知 aggregatorId/未知 fnId/表达式编译失败/参数个数不符/参数类型不符）。同时裁定 D13 遗留的「新模块 schema」声明面机制。

## Current Baseline

- **D13 回改条款已触发（本 plan 内实测裁定，2026-10-02）**：「WI8c 声明面落新模块 schema」经 delta 机制实验证实不可行——实验证据（probe 实测，两轮）：(1) nop-stream-sql 提供 `x:extends` 基座 stream.xdef 的 delta xdef 后，新增顶层子节点（`<aggregators>`）可过 schema 校验，但同名子节点不显式重声明时既有元素不接受新属性（`aggregatorRef` 报 `nop.err.xlang.xdsl.attr-not-allowed`）；(2) 显式重声明 `<aggregate aggregatorRef="string"/>` 后 parse 通过，**但 flow 生成的 typed 模型不携带新字段**（`StreamModel.getAggregators()`/`StreamAggregateModel.aggregatorRef` 不存在）——flow 的模型类由其构建期 generate-sources 阶段执行 `precompile/gen-stream-xdsl.xgen`（渲染 BASE `/nop/schema/stream/stream.xdef`）产出，flow 构建看不到下游模块的 delta schema，xdefs jar 是全仓唯一 schema 来源。结论：**typed 消费面只能落 base xdef**（同 windowingStrategies 先例）；按 D13 §3.2 回改条款回改为「声明面落 base stream.xdef，消费面经 SPI 由新模块提供」，roadmap 与 D13 记录同步。
- `<aggregate>` 现状（stream.xdef:157-158，bean 属性在共享 define :105-108 且本就可选）：`AdvancedTransforms.buildAggregate`（:341-362）顺序=requireSingleInput → WindowedStream 类型检查（:345-351）→ bean 缺失检查（:352-357，`ERR_STREAM_REQUIRED_ATTR`）。恰一分支插在 bean 检查位置；**上游 WindowedStream 检查先于恰一检查——恰一/双缺测试必须带 keyBy+window 上游**。
- **windowingStrategies 同构先例**（stream.xdef:48-55）：顶层 `xdef:key-attr` 注册表 + entry unique-attr + description；`getStrategy(String)` 由 key-attr 生成，`getAggregator(String)` 同理可得。
- WI9 交付（nop-stream-sql，已 audit）：`StreamSqlAggregations.resolve(fnId)` 五 id 目录（未知名 null）、`StreamSqlAggregation.create(evaluator)` 直接实现 core `AggregateFunction`（WindowedStream.aggregate 零适配）、`allowsNoArg()`（count 0..1 其余恰 1）、`withDistinct(true)` fail-fast、`StreamSqlExprCompiler.compileScalar(SqlExpr)/resolveType(SqlExpr, Function<String,BasicTypeInfo<?>>)`（**列类型函数由调用方供给**）、`EqlExprSupport.parseExpr`（空文本返回 null 需调用方 fail-fast）。
- WI8b 交付（nop-stream-flow）：`StreamSchemaRegistry.resolveSchemas(StreamModel)` per-model 静态构建、`FieldSpec{name,type:BasicTypeInfo,nullable,defaultValue}`——buildAggregate 持有 owner model 可解析列类型，经 SPI 传入 resolver。
- **依赖方向**：nop-stream-sql → nop-stream-flow（D13），flow 不可依赖 WI9 目录/编译器；构建期解析走 BeanContainer SPI 注入。**BeanContainer 三态**：`BeanContainer.isInitialized()` 为 false 时 `instance()` 抛平台 `ERR_IOC_BEAN_CONTAINER_NOT_INITIALIZED`——须先护栏；按 type 可选查找用 `tryGetBeanByType(Class)`（缺 bean 返回 null；`getBeanByType` 缺失即抛，语义相反）。
- **beans 自动装配机制（实测）**：普通 `_vfs/**.beans.xml` **不会**被自动装载。两条自动装配路：(a) `_vfs/nop/autoconfig/*.beans` 列表文件；(b) 模块 `{moduleId}/beans/` 下 `app.beans.xml`/`app-*.beans.xml` + 模块经 `_module` 标记发现（`AppBeanContainerLoader.getModuleAppResources`）。nop-stream-sql 的 `_vfs` 当前在模块根（非 src/main/resources，**Maven 不打包**，其中 nop/schema/.gitkeep 在 D13 回改后已成死资源）——本 plan 一并纠正模块资源布局。
- 模型重生成管线（WI10 先例）：改 base xdef → `./mvnw install -pl nop-kernel/nop-xdefs -DskipTests` 刷 xdefs jar → flow 构建重生成 `_gen` 模型类（git 跟踪）；新类的非下划线 wrapper（如 `StreamAggregatorModel extends _StreamAggregatorModel`）是手写保留文件，不违 _gen 纪律。
- 既有测试：`TestAdvancedTransforms.aggregateWithoutBeanFailsFast`（:132-148）断言 bean 缺失 `ERR_STREAM_REQUIRED_ATTR`——恰一裁定后必改写（双缺 → `ERR_STREAM_INVALID_ARG`）；flow 测试类路径无 nop-stream-runtime → window→aggregate build 到算子工厂处必抛 runtime-gap（既有先例 `aggregateDispatchesToWindowedAggregatePath`）。
- 错误码惯例：复用 `ERR_STREAM_INVALID_ARG`/`ERR_STREAM_REF_UNKNOWN`（零 core 新码）。

## Goals

- **xdef 声明面（base stream.xdef；protected area plan-first 证据=本 plan+回归+迁移注记）**：顶层 `<aggregators xdef:key-attr="aggregatorId" xdef:body-type="list">` 注册表（entry：`aggregatorId="!string"`、`fnId="!string"`、`expr="string"`、可选 `schemaId="string"`，形状对齐 windowingStrategies）；`<aggregate>` 增 `aggregatorRef="string"`。
- **模型重生成**：flow `_gen` 重生成（`_StreamAggregatorModel`/`StreamAggregatorModel` wrapper + `StreamAggregateModel.aggregatorRef` + `StreamModel.aggregators` + key-attr 查找器）。
- **恰一裁定**：`buildAggregate` 的 `bean` 与 `aggregatorRef` 恰好其一——并存或双缺均 fail-fast `ERR_STREAM_INVALID_ARG`（取代现行 bean 缺失报错）；bean 分支行为零变化。
- **解析 SPI（flow 侧新接口，语义契约非实现）**：按 fnId+表达式+列类型函数解析聚合函数——实现方需完成：fnId 查 WI9 目录（未命中报未知 fnId）、表达式编译（失败 fail-fast）、参数个数校验（`allowsNoArg` 语义：count 0..1、其余恰 1）、参数类型校验（**裁定**：类型不符=聚合函数不接受该参数类型——sum/avg 要求数值型、min/max 要求 Comparable 型、count 任意；列类型未知（Unknown）时跳过校验不误杀）、产出 `AggregateFunction`。flow 侧 `buildAggregate` 的 aggregatorRef 分支全序：registry entry 查找（未知 aggregatorId `ERR_STREAM_REF_UNKNOWN`）→ **A4 bean 前置**（`beanResolver.contains(fnId)` 命中即 bean 解析，忠实 A4 全序）→ BeanContainer 护栏 + `tryGetBeanByType(SPI)` → 命中委托、null（含未初始化容器）fail-fast `ERR_STREAM_INVALID_ARG`（ARG_DETAIL 点名 classpath 缺 nop-stream-sql）。
- **sql 侧实现**：SPI 实现类承接上述语义契约（WI9 全套 API）+ 列类型函数由 SPI 入参供给。**模块资源布局纠正与注册**：`_vfs` 移至 `src/main/resources/_vfs`；按 Nop 模块发现机制补模块标记并把 resolver 注册进 `{moduleId}/beans/` 下的 app-beans 文件（对齐 nop-stream-runtime 既有惯例）；**自动装配证明测试**：sql 模块内全量 CoreInitialization 后按 type 取 SPI 非空。
- **对齐钉子**：`builtinIds()` 闭集 = {sum,count,avg,min,max} 且与 BaseRule.g4 五聚合关键字（:273 `MAX|MIN|SUM|COUNT|AVG`）一致，逐 id 经 aggregatorRef 声明 build 到达聚合分支（对齐与声明面一处钉住）。
- **fail-fast 清单（逐项测试钉码）**：未知 aggregatorId（`ERR_STREAM_REF_UNKNOWN`）；fnId 未知（`ERR_STREAM_INVALID_ARG`）；expr 编译失败（同）；参数个数不符（同）；**参数类型不符（同，schemaId+非数值列 sum 场景）**；bean 与 aggregatorRef 并存/双缺（同）；无 resolver provider（同，点名依赖）。

## Non-Goals

- 不实现 `<custom>`/`<process>` 参数化（params fail-fast 维持）；不接 WindowOperator 新语义（既有 windowed.aggregate 链路）；不做 coders/sideInputs（FU-3）；WI8d join 面独立 plan。

## Scope

### In Scope

- `nop-kernel/nop-xdefs`：stream.xdef 增量（protected，plan-first 证据齐备）
- `nop-stream/nop-stream-flow`：模型重生成、SPI 接口、buildAggregate 恰一分支与全序、测试改写
- `nop-stream/nop-stream-sql`：SPI 实现、模块资源布局纠正（_vfs 入 resources + 模块标记 + app-beans）、自动装配证明测试、resolver 语义测试
- roadmap 三处同步（WI8c 行、D13 行回改注记、Cross-Cuting 4 注记）；`sql-compiler-contract.md` §3.2 回改记录；`sql-landing-decision.md` §4.1 注记；当日日志

### Out Of Scope

- `<join>`/`joinRef`（WI8d）；运行时窗口算子变更；schemas/coders 消费者扩展。

## Execution Plan

### Phase 1 - 机制裁定与 xdef 声明面

Status: completed
Targets: `nop-kernel/nop-xdefs`、roadmap 与两份 design doc

- Item Types: `Decision`

- [x] 落档 D13 回改裁定（probe 两轮实验证据）：`sql-compiler-contract.md` §3.2 注记、`sql-landing-decision.md` §4.1 注记、roadmap D13 行与 Cross-Cuting 4 注记
- [x] stream.xdef：aggregators 注册表 + `<aggregate aggregatorRef>` + entry 可选 schemaId
- [x] xdefs install + flow 重生成模型 + 新类 wrapper 手写保留文件
- [x] ai-dev/logs/ 当日条目更新

Exit Criteria:

- [x] 三文档回改注记一致（D13 行、contract §3.2、landing-decision §4.1）
- [x] 重生成后 `./mvnw install -pl nop-stream/nop-stream-flow -DskipTests` 绿（typed 模型携带新面）
- [x] `parseRoadmapMarkdown` 复核（WI8c 翻转时执行，roadmap 本 Phase 仅改 D13/CC4 注记不改状态行——31+7 复核随 Phase 3）

### Phase 2 - 构建期消费、SPI 与注册

Status: completed
Targets: `nop-stream/nop-stream-flow`、`nop-stream/nop-stream-sql`

- Item Types: `Feature`

- [x] flow：SPI 接口（语义契约 javadoc）；`buildAggregate` 恰一裁定 + aggregatorRef 全序（entry 查找 → A4 bean 前置 → BeanContainer 护栏+tryGetBeanByType → 委托或 fail-fast 点名依赖）；schemaId 列类型函数经 StreamSchemaRegistry 构建传入 SPI
- [x] sql：SPI 实现（WI9 全套语义契约）+ 模块资源布局纠正 + 模块标记 + app-beans 注册（执行期实测：模块发现需两段 `*/*/_module` 且 moduleId 段无连字符——最终双 _module 布局见日志；_vfs 自模块根迁 src/main/resources）
- [x] flow 测试：改写 `aggregateWithoutBeanFailsFast` 为恰一双缺断言（带 keyBy+window 上游）；恰一矩阵（双缺/并存/未知 ref）——**接线证明**：测试 beans.xml 注入假 SPI 实现，counter 断言 build 时真实调用，随后 runtime-gap 如实断言
- [x] sql 测试：自动装配证明（全量初始化后按 type 取 SPI 非空）；resolver 语义测试（fnId 未知/expr 失败/个数/类型不符/Unknown 跳过/成功路径 sum 语义直测）；对齐钉子（builtinIds 闭集）
- [x] flow 补充（audit M-1 补救）：无 provider 分支具名测试——flow 类路径无 sql 模块时合法 aggregatorRef fail-fast `invalid-arg` 且 message 含 nop-stream-sql（aggregateRefWithoutProviderFailsFastNamingDependency）
- [x] ai-dev/logs/ 当日条目更新

Exit Criteria:

- [x] 恰一裁定：并存与双缺 fail-fast 码串钉住；bean-only 路径既有测试改写后绿
- [x] **接线验证（规则 #23）**：aggregatorRef 合法声明经 BeanContainer 真实调到 SPI 实现（假实现 counter 断言）
- [x] 逐项 fail-fast（未知 aggregatorId/未知 fnId/expr 失败/参数个数/参数类型/无 provider）各有具名测试
- [x] 自动装配证明：sql 模块全量初始化后按 type 取 SPI 非空（B-2 的 hollow 防护）
- [x] 对齐钉子：builtinIds 闭集 = BaseRule.g4 五聚合
- [x] `./mvnw test -pl nop-stream/nop-stream-flow` 绿（139）；`./mvnw test -pl nop-stream/nop-stream-sql` 绿（34）；-am 全量随 Phase 3 复核
- [x] ai-dev/logs/ 当日条目已更新

### Phase 3 - 收口

Status: completed
Targets: plan 与 roadmap

- Item Types: `Proof`

- [x] 独立子 agent closure audit（不同 task_id）：首轮判定 FAIL——唯一阻塞项 M-1（无 provider 分支缺具名测试）；补 aggregateRefWithoutProviderFailsFastNamingDependency 用例（flow 23/23）后达成 PASS 条件；证据落 ai-dev/audits/nop-stream-sql/wi8c-closure-audit.md
- [x] audit 通过后 roadmap WI8c `todo` → `done`（括注单层无嵌套）；`parseRoadmapMarkdown` 复核 31 工作项 + 7 里程碑、15 done、无静默丢弃
- [x] scan-hollow（flow + sql）高危零发现；invariants sync OK
- [x] Plan Status → `completed`；check-plan-checklist --strict 退出码 0；check-doc-links --strict 退出码 0

Exit Criteria:

- [x] 独立 audit 证据落档两处
- [x] roadmap WI8c = done + 解析器 31 + 7 复核通过
- [x] check-plan-checklist --strict 退出码 0；check-doc-links --strict 退出码 0；scan-hollow 高危零发现

## Closure Gates

- [x] aggregators 注册表 + aggregatorRef 声明面经 base xdef 落地且 flow typed 模型携带（重生成管线产出，零手改 _gen；wrapper 为手写保留文件——audit 实读确认重生成特征）
- [x] bean/aggregatorRef 恰一裁定 fail-fast 双向钉住；bean 路径零退化
- [x] resolveAggregator 六项 fail-fast 各有具名测试（未知 aggregatorId/未知 fnId/expr 失败/参数个数/参数类型/无 provider——M-1 补救后齐备）
- [x] 聚合语义不复制：运行期累加 100% 来自 WI9 求值器（audit grep 实证 SPI 零自行累加）
- [x] **自动装配真实生效**：app-beans 注册经 Nop 模块机制装载（TestStreamAggregatorFunctionResolver 全量初始化按 type 查找，非手工容器）
- [x] 对齐钉子：内置五 id 与 BaseRule.g4 五聚合一致
- [x] D13 回改三文档注记一致且 roadmap 行同步（audit 四处口径一致核验）
- [x] `./mvnw test -pl nop-stream/nop-stream-flow` 绿（140）；`./mvnw test -pl nop-stream/nop-stream-sql` 绿（34）
- [x] 独立子 agent closure-audit 已完成并记录证据（不同 task_id，M-1 补救后 PASS）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql/15-wi8c-parameterized-aggregate.md --strict` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

### delta xdef 声明面路径

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: probe 实测证实 typed codegen 绑定 base xdef（两轮证据），delta 路径只余 schema 校验 + XNode 旁路消费的降级形态，与 typed 消费面要求不符；回改条款已执行
- Successor Required: `no`
- Successor Path: 若日后 XDSL 支持 delta 驱动跨模块 codegen 再评估

## Non-Blocking Follow-ups

- FU-3（既有）：coders/sideInputs/requirements/checkpointParticipants 注册表消费者——本 plan 只落 aggregators。

## Closure

Status Note: 参数化聚合面落地——aggregators 注册表与 aggregatorRef 落 base xdef（D13 回改裁定执行，三文档注记一致），恰一裁定取代 bean 强制，六项 fail-fast 各有具名测试，SPI 消费面经模块 app-beans 自动装配真实生效（模块发现机制经实测：两段 _module 发现入口 + moduleId 往返命中布局），累加语义 100% 复用 WI9。独立 closure audit 首轮 FAIL（M-1 无 provider 用例缺失）补齐后 PASS。
Completed: 2026-10-02

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，与起草审查、执行均不同 task）
- Audit Session: 证据落档 ai-dev/audits/nop-stream-sql/wi8c-closure-audit.md（首轮 FAIL 单项 M-1，补救后 PASS）
- Evidence:
  - xdef 同构 + _gen 重生成特征实读确认；恰一三用例码串钉住；bean-only 零退化
  - 全序逐环实读（entry/bean 前置/护栏/委托）；SPI 全序与 Unknown 跳过语义核验
  - 模块发现 Anti-Hollow：resolverAutoAssemblesThroughModuleMechanism 全量初始化实跑绿
  - 实跑 flow 140 / sql 34 全绿；门禁（doc-links/scan-hollow/invariants/roadmap 解析 31+7）全过
  - M-1 补救：aggregateRefWithoutProviderFailsFastNamingDependency（flow 23/23）
- `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql/15-wi8c-parameterized-aggregate.md --strict` 退出码 0

Follow-up:

- no remaining plan-owned work；M-2（schemaId→SPI 端到端桥接单用例）与 M-3（expr 二次 parse 效率）为非阻塞 Minor，随 WI17 编译器接线时自然覆盖
