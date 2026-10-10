# Plan 2377: U4 tableId XDSL 声明面（`<source tableId>` → record 元数据直达）

- Status: completed
- Item Type: Feature
- Roadmap: `ai-dev/backlog/nop-stream-deferred-unlock-roadmap.md` U4
- Related: multi-engine WI7/R4（`ChangeSemanticView` 桥 + `StreamRecord` 元数据，plan 2336）；WI20（`TestWi20TableTagRoutingPatternE2E` 表标识字段 filter 路由降级形态，plan 2366）；U3/plan 2376（builder 后置装饰 + xdef 再生成 + registeredStream 接线钉先例）
- Owner Direction: 2026-10-10「容易实现的部分都写计划自动完成」

## Purpose

把 multi-engine WI7/R4 交付的引擎 record 元数据面（`StreamRecord.withChangeMetadata`）接到**作业 DSL**：`<source tableId="...">` 声明使该源的全部记录在发射点被盖 `withChangeMetadata(INSERT, tableId)`。与 WI20 的「单表流 + 表标识字段 filter 路由」降级形态互补——声明面可用时表身份直达 record 元数据，不再依赖数据字段路由。

## Current Baseline（2026-10-10 代码实证）

1. `StreamRecord.withChangeMetadata(RowKind, tableId)`（`nop-stream/nop-stream-core/.../streamrecord/StreamRecord.java:190`）：引擎 record 元数据；`copy()` 保留两字段（:205-211）；`StreamElementCodec:167` 线格式往返保留（`TestStreamElementCodecChangeMetadata`）；checkpoint channel state 携带（`TestChannelStateChangeMetadata`）。
2. 源发射点盖章钩子：`StreamSourceOperator.applyChangeMetadata(record, value)`（:323，static，**3 调用点**：:247 collect / :257 collectWithTimestamp + FLIP-27 路径 `SourceReaderOperator:363`）——**仅当 value 实现 `io.nop.api.core.stream.ChangeSemanticView`** 时盖章（WI7，plan 2336）；普通 value 零元数据（历史默认）。`TestChangeMetadataBridgeAndGuard` 静态调用钉住桥与 sink 守卫（不构造 operator，新构造参数不破坏它）。**FLIP-27 路径维持不盖 declared 声明（Non-Goal/successor 已登记），两参重载必须保持 static**。
3. Sink 守卫：`StreamSinkOperator.processElement` ——非 INSERT 元数据 + 非 `CdcChangeAwareSink` = typed fail-fast（`ERR_STREAM_SINK_NOT_CHANGE_AWARE`）。**INSERT 盖章对该守卫透明**（isInsertOnly 直通）。
4. 用户函数不可见 record 元数据：`ProcessFunction.Context` 仅 timestamp+timerService；`SinkFunction.consume(value)` 仅值；map/filter 重建 StreamRecord 丢元数据。**`<custom customType>` 解析完整 `OneInputStreamOperator` bean（AdvancedTransforms.buildCustom）——其 `processElement(StreamRecord)` 是唯一用户级元数据观察缝**。
5. DSL `<source>` 仅路由 `env.addSource(SourceFunction)`（builder :2039）；FLIP-27 `SourceApiTransformation` 路径对 DSL 不可达。
6. 声明载体缺口：`SourceTransformation`（持 SourceFunction，Serializable）→ `StreamGraphGenerator.transformSource`（:253，graph 包）→ 私有内部类 `SourceOperatorFactory`（:526）→ `new StreamSourceOperator<>(fn)`——全链无 tableId 载体。**拷贝路径（对抗审查 F1 Blocker）**：本地执行计划对每个 chain 深拷贝（`GraphExecutionPlan:452-456` → `OperatorChain:256/:277`），凡有出边的源（任何真实作业）都走 `StreamSourceOperator.copyForSubtask()`/`copyForSubtask(int)`（:205-223，两变体均 1 参重建）——**新字段若不随拷贝传递则运行时空转而全部图级钉子照绿**。`DataStreamImpl.getTransformation()` **已存在**（:404-406，public；`DataStream` 接口未暴露，builder 需 cast）；`applyDeclaredParallelism`（:2372-2391）原地 setParallelism 返回同实例，union 包装同一 SourceTransformation 实例——post-addSource 回设属性必然到达 transformSource。
7. xdef `<source>`（stream.xdef:163-172）现有属性：maxParallelism/consistencyCapability + U3 的 rateLimit*。可选字符串属性惯例：`lateData="string"`（无 `!` 前缀，:210）。
8. `StreamUnionOperator.processElement` 原样转发 record（:46-48），chaining 直传——元数据在 union/chaining 链保留。
9. U3 先例（plan 2376，已合入 master 339c4528da）：xdef 改动 → 再生成恰 1 处 `_gen` 变更；builder 后置装饰 + registeredStream 接线钉 + runtime E2E beans fixture 模式（`RateLimitE2EBeans` + `test-rate-limit-e2e.beans.xml`）。

## Goals

- `<source tableId="...">`（可选属性，空/缺省 = 未声明）声明 → 该源全部记录在两个发射点（collect/collectWithTimestamp）盖 `(INSERT, tableId)`。
- 优先级：value 自带 `ChangeSemanticView`（Debezium ChangeEvent 等）**胜过**声明——逐记录 CDC 身份比静态声明更富，静态覆盖会污染真实 CDC 流。
- 多表路由场景断言：两个不同 tableId 的源 → union → `<custom>` 观察算子 bean（读 `element.getTableId()`）→ sink，逐记录断言表身份归属。

## Non-Goals

- 不改任何用户 API（`ProcessFunction.Context`/`SinkFunction`/`ChangeSemanticView` 不动）。
- FLIP-27 `SourceApiTransformation` 路径不接线（DSL `<source>` 不可达该路径，登记为 successor 触发条件：FLIP-27 源进入 DSL 声明面时）。
- 不声明 RowKind（声明 tableId 恒 INSERT；DELETE/UPDATE 语义只来自值自带 `ChangeSemanticView`，守卫面不放宽）。
- 连接器侧元数据驱动 sink 路由不做（今日消费方=引擎守卫/线格式/channel state/`<custom>` 算子；用户可见路由仍走 WI20 字段形态直至元数据感知 sink 出现）。

## Scope

### In Scope

1. **core**：`SourceTransformation` 增可空 `declaredTableId`（String，getter/setter，Serializable，serialVersionUID 不动——新增 String 字段对 Java 序列化兼容）；`StreamGraphGenerator.transformSource` 传递到 `SourceOperatorFactory`（增字段+构造参数）→ `createStreamOperator` 传入 `StreamSourceOperator` 新构造参数（旧构造保留委托 null）；**`copyForSubtask()` 与 `copyForSubtask(int)`（含 SubtaskShardedSourceFunction 分支）两变体传递 declaredTableId（审查 F1：否则执行拷贝路径空转）**；`applyChangeMetadata` 扩展为三参（view 胜出 → declared INSERT 盖章 → 双无则原样），:247/:257 两发射点换用；**旧两参重载保持 static** 委托 null（`SourceReaderOperator:363` 与 `TestChangeMetadataBridgeAndGuard` 静态调用依赖），FLIP-27 路径不盖 declared（successor 已登记）。`StreamSourceOperator` 增 package-private 只读访问器供钉子观察。
2. **flow**：xdef `<source tableId="string">` + 再生成（恰 1 处 `_StreamSourceModel` 变更；机制=root pom exec-maven-plugin CodeGenTask，`-am` 重建 nop-xdefs）；builder `buildSource` 在 `env.addSource` 返回的 DataStream 上**复用既有** `DataStreamImpl.getTransformation()`（cast 后回设 `declaredTableId`，null/blank → 未声明，零静默路径）。
3. **测试**：
   - core：`TestDeclaredTableIdStamping` 6 钉（declared 双发射点盖 INSERT+tableId；value view 胜过 declared；旧构造默认无盖章；copyForSubtask 拷贝后仍盖章；图级 generate→factory→operator 接线；blank 归一）。
   - flow：`TestSourceTableIdDeclaration` 3 钉（`registeredStream` 取源 DataStream → SourceTransformation.getDeclaredTableId 断言声明直达；空/缺省 → null；与 rateLimit 属性共存）。
   - runtime：`TestSourceTableIdE2E`（观察算子 bean 收集 `(value, tableId)`；断言逐记录表身份归属正确 + sink 全量送达 + INSERT 对非 change-aware sink 透明即守卫不触发）。
4. **文档**：user guide 支持矩阵表 A 增行 + U4 声明形态段（与 WI20 降级形态/值级 ChangeSemanticView 三分关系、INSERT-only 语义、观察缝=`<custom>`）；deferred-unlock roadmap U4 勾选注记；daily log。

### Out Of Scope

- `ChangeSemanticView` 接口与 `RowKind` 语义（nop-api-core 不动）。
- FLIP-27 源路径、连接器元数据路由、`ProcessFunction.Context` 元数据暴露。
- linter/nop-lint 新规则（声明面为纯放行，无 fail-fast 面变化——tableId 此前不在拒绝清单，无需删钉）。

## Execution Plan

### Phase 1: core 盖章面（载体 + 优先级 + 钉）

Status: completed
Targets: `nop-stream/nop-stream-core`（SourceTransformation / StreamGraphGenerator / StreamSourceOperator / TestDeclaredTableIdStamping）

- Item Types: `Feature` + `Proof`

- [x] `SourceTransformation.declaredTableId` + `StreamGraphGenerator`/`SourceOperatorFactory`/`StreamSourceOperator` 全链传递（旧构造委托保留；**`copyForSubtask()`/`copyForSubtask(int)` 两变体同传**）
- [x] `applyChangeMetadata` 三参优先级（view > declared > 无）+ 两发射点接线 + 旧两参重载保持 static 委托
- [x] `TestDeclaredTableIdStamping` 6/0 绿（declared 双发射点盖章 / view 胜出 / 旧构造默认无盖章 / **copyForSubtask 拷贝后仍盖章** / 图级 generate→factory→operator 接线钉 / blank 归一钉）；WI7 既有钉 TestChangeMetadataBridgeAndGuard 5/0 无回归

Exit Criteria:

- [x] 声明链 live：`declaredTableId` 从 Transformation 经 factory 到 operator 字段——`graphGeneratorCarriesDeclarationFromTransformationToOperator`（generate→getOperatorFactory→createStreamOperator→访问器断言）+ 行为级盖章断言并列；测试断言盖章产物 `(INSERT, tableId)`
- [x] 优先级钉：value 携带 view 时 declared 不覆盖（防 CDC 污染）
- [x] **拷贝路径钉（审查 F1）**：`copyForSubtask` 后的 operator 仍携带声明——真实执行作业全部走该路径
- [x] **接线验证**：`StreamGraphGenerator.transformSource` 真实传参（图级钉直证），非仅类型存在
- [x] **无静默跳过**：双无路径维持 WI7 no-op 原样（TestChangeMetadataBridgeAndGuard 5/0 保持绿）
- [x] `./mvnw test -pl nop-stream/nop-stream-core`（bridge+stamping 定向）绿；全量回归归 Phase 3 后统一跑
- [x] No owner-doc update required（纯 core 内部载体，无声明面语义变化——flow 阶段统一落文档）

### Phase 2: flow 声明面（xdef + builder + 接线钉）

Status: completed
Targets: `stream.xdef`、`_StreamSourceModel`（再生成）、`StreamModelDslBuilder`、`TestSourceTableIdDeclaration`（`DataStreamImpl` 无需改动——`getTransformation()` 已存在）

- Item Types: `Feature` + `Proof`

- [x] xdef `<source tableId="string">` + 再生成恰 1 处 `_gen` 变更（_StreamSourceModel +32/-1）+ flow 编译过
- [x] builder 回设（复用既有 `getTransformation()`，cast `DataStreamImpl`；blank 归一在 transformation setter；非 SourceTransformation 载体 typed fail-fast 防御分支）
- [x] `TestSourceTableIdDeclaration` 3/0 绿（声明直达 SourceTransformation / 空缺省 null / 与 rateLimit 属性共存——bean 面经 InMemoryBeanFunctionResolver，registeredStream cast 断言）

Exit Criteria:

- [x] 声明→载体 wiring：`registeredStream` 取源 DataStream，`SourceTransformation.getDeclaredTableId()` 等于声明值（含空缺省负钉）
- [x] **端到端前置**：builder 无忽略分支——声明非空必达载体（负钉断言）
- [x] 再生成产物核对：`git diff` 中 `_gen` 恰 1 文件
- [x] flow 定向测试绿（全量回归归 Phase 3 后统一跑）
- [x] No owner-doc update required（文档统一 Phase 4）

### Phase 3: runtime E2E（多表路由场景）

Status: completed
Targets: `nop-stream/nop-stream-runtime`（`TestSourceTableIdE2E` + beans fixture）

- Item Types: `Proof`

- [x] fixture：双源（不同 tableId）→ union → `<custom>` 观察算子 bean → sink；beans fixture 注册观察 bean（`TableIdE2EBeans`：OrdersSource/UsersSource/TableIdObserver/CollectingSink；**观察 bean 原样转发同一 StreamRecord 实例**）
- [x] `TestSourceTableIdE2E` 2/0 绿：逐记录表身份归属（order:1-3→db.orders、user:1-2→db.users 零交叉）+ sink 全量 5 记录 + 守卫透明 + **未声明负控**（同观察缝读 null）

Exit Criteria:

- [x] **端到端验证**：从 `<source tableId>` 声明到 `<custom>` 算子读到 `getTableId()` 到 sink 输出完整跑通；**变异探针红绿闭环**：断开 `applyDeclaredTableId` 调用→重装 flow→E2E 红（"got null — declaration was not wired to the executed copy"，25ms 内失败）→恢复（grep 确认真实行在位）→重装→2/0 绿
- [x] 多表归属断言：每记录 observed tableId == 其源声明（无交叉）
- [x] 透明性断言：INSERT 盖章记录通过非 change-aware sink（守卫不触发）
- [x] 全量回归：core 1765/0（+6，1 skip 存量）+ flow 315/0（+3）+ runtime/sql 全量（本 Phase 后统一记录）
- [x] No owner-doc update required（文档统一 Phase 4）

### Phase 4: 文档与收口

Status: completed
Targets: `docs-for-ai/03-modules/nop-stream-user-guide.md`、roadmap、daily log、plan

- Item Types: `Proof`

- [x] user guide 支持矩阵行 + U4 声明形态段（三分关系：值级 view / 声明 tableId / WI20 字段降级；INSERT-only；`<custom>` 观察缝）
- [x] daily log 条目 + deferred-unlock roadmap U4 勾选（独立 audit APPROVE 后）
- [x] 独立 closure audit + 单次提交

Exit Criteria:

- [x] 文档与 live 行为一致（矩阵行锚点真实存在——audit 逐锚点核实）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（audit 复跑）
- [x] `node ai-dev/tools/check-plan-checklist.mjs` + `scan-hollow-implementations --severity high` 退出码 0（audit 复跑，U4 改动文件零发现）
- [x] 独立 closure audit APPROVE 证据写入本 plan Closure 段

## Closure Gates

- [x] 所有 in-scope confirmed live defects 已修复（无——本 plan 纯新增声明面）
- [x] 所有 in-scope confirmed contract drifts 已收敛（无）
- [x] 声明面行为达成：`<source tableId>` → `(INSERT, tableId)` 盖章 + view 优先级 + 多表路由场景断言
- [x] 必要 focused verification 已完成（core 6 钉 + flow 3 钉 + runtime E2E + 变异探针红绿闭环）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift（FLIP-27/用户级 API 两 deferred 项 audit 核实为 out-of-scope improvement，非隐藏缺陷）
- [x] 受影响 owner docs 已同步（user guide 矩阵+形态段）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：声明→载体→盖章→观察全链运行时连通（E2E 直证 + copyForSubtask 双钉）；无空方法体/静默跳过
- [x] `./mvnw test`（core/flow/runtime/sql 全量）绿——runtime 1 失败为白名单时序抖动族（WI15 条目在案），隔离 3/0 绿核实
- [x] checkstyle / 代码规范检查通过（pre-commit nop-lint 门禁，nop-stream 2566 文件 0 门禁诊断）

## Deferred But Adjudicated

### FLIP-27 SourceApiTransformation 路径的 tableId 载体

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: DSL `<source>` 仅路由 SourceFunction 路径（代码实证 builder :2039），FLIP-27 路径今日无 DSL 入口，无用户可达缺口。
- Successor Required: `yes`
- Successor Path: FLIP-27 源进入 DSL 声明面时补 `SourceApiTransformation` 同型载体（roadmap 维持延期表登记）

### 用户级元数据访问 API（ProcessFunction.Context 暴露 getTableId）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: `<custom>` OneInputStreamOperator 已是用户级观察缝；Context 扩展属用户 API 设计（涉及全 process 面兼容性），owner 裁定通道未开。
- Successor Required: `no`
- Successor Path: 出现「process bean 内需按表身份分支」真实诉求时另立项

## Closure

Status Note: U4 交付完成——`<source tableId>` 声明面（xdef+再生成恰 1 处 `_gen`）、builder `applyDeclaredTableId`（复用既有 `getTransformation()` 载体回设，非 SourceTransformation typed fail-fast）、core 盖章链（`SourceTransformation.declaredTableId` → `SourceOperatorFactory` → `StreamSourceOperator`，`copyForSubtask` 双变体同传，`applyChangeMetadata` 三参优先级 view>declared>无，两参重载保持 static 供 FLIP-27 路径与 WI7 钉）、core 6/0 + flow 3/0 + runtime E2E 2/0（多表归属零交叉经 `<custom>` 观察缝 + 守卫透明 + 未声明负控）、变异探针红绿闭环、文档两处同步。独立 audit APPROVE 后收口。审查首轮 1 Blocker（copyForSubtask 剥离字段）在本 plan 执行前已修复并被双钉（单元钉+E2E 执行拷贝路径钉）证实。
Completed: 2026-10-10

Closure Audit Evidence:

- Reviewer / Agent: agent_c1e05297-4d55-4374-b43f-1aa4d2decbfb（独立 fresh-session Explore 审计）
- Audit Session: agent_c1e05297-4d55-4374-b43f-1aa4d2decbfb
- Evidence:
  - Phase 1 PASS：载体链 `StreamGraphGenerator.java:253-258/:536-554` + `StreamSourceOperator.java:94/:106-118/:227-245`；**copyForSubtask 双变体同传核实（:227-231 与 sharded 分支 :240-245，执行路径 OperatorChain.java:256/:277 必经拷贝）**；优先级 `:347-372`（两发射点 :271/:281）；`SourceReaderOperator.java:363` 两参静态调用保持编译。
  - Phase 2 PASS：xdef U4 hunk；`git diff --numstat` 恰 1 处 `_gen`（32/1）；builder `:2037-2041`；`TestSourceTableIdDeclaration` 3/0。
  - Phase 3 PASS：`TestSourceTableIdE2E` 2/0；E2E 断言结构上保证断线必红（非 null 等值断言）；观察 bean 原样转发同实例（TableIdE2EBeans.java:75）。
  - 回归 PASS：core 1765/0（1 skip 存量）/flow 315/0/sql 165/0/runtime 1372（1=白名单时序抖动族，audit 独立隔离复跑 TestCheckpointEndToEnd 3/0 + TestE2ETwoPhaseCommitSink 3/0）。
  - 工具 PASS：doc-links/check-plan-checklist/scan-hollow 三工具 audit 复跑退出码均 0（hollow 扫描 U4 改动文件零发现）。
  - Deferred 诚实性 PASS：FLIP-27 不可达（builder 唯一源路由 `addSource(SourceFunction)`）+ 用户级 API 有真实触发条件，均非隐藏缺陷。
  - 审计发现处置：Minor-1「5 钉→6 钉」已在本收口编辑中修正（Scope item 3 + Closure Gates）；commit 卫生（排除无关工作树改动）由显式路径暂存执行。
- `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2377-u4-tableid-declaration.md --strict` 退出码 0（关闭前复跑确认）

Follow-up:

- no remaining plan-owned work（FLIP-27 tableId 载体与用户级元数据 API 两项 Deferred But Adjudicated 已带触发条件登记于本 plan）
