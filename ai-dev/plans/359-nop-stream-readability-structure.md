# 359 nop-stream 可读性与结构整改（行为保持）

> Plan Status: active
> Last Reviewed: 2026-09-26
> Source: 审计 `ai-dev/audits/2026-09/2026-09-26-0546-deep-audit-nop-stream-quality/`（01-readability.md + 02-structure-maintainability.md；复核修正已并入）
> Related: 358（正确性/资源缺陷修复，先行）、360（JMH+JFR 性能迭代）

## Purpose

在不改变任何产品行为语义的前提下，收口 nop-stream 的可读性与工程卫生债：超长方法拆分、逐字重复块合并、死代码清除、生产不可达的重复触发循环收敛、变更考古型注释改写为当前不变式、命名/魔法数字/注释语言卫生、横切工具统一、错误处理规范补全。目标读者是下一个维护者：读完核心路径代码即可理解当前行为，不需要考古 git 历史与内部审计编号。

## Current Baseline

- 10 个待拆分方法（经对抗性复核校正：8 个 ≥100 行 + 2 个 86-91 行临界方法）：GraphExecutionPlan.build(204)、CepOperator.open(140)、NFA.computeNextStates(136)、StreamExecutionEnvironment.execute(132)、InputGate.readMultiChannel(114)、CheckpointBarrierTracker.acknowledgeOperator(105)、StreamTaskInvokable.processInputGate(101)、ResultPartition.write(101)、StreamModelDslBuilder.buildTransforms(91，282-372)、NFA.advanceTime(86，262-347，timeout 分支所在——审计 01 曾把 16 行的 process 薄包装与其误并为 111 行，以本条为准)。
- WindowOperator.onEventTime(:810-889)/onProcessingTime(:892-962) ~80 行近逐字复制（仅 3 处差异，复核确认）。
- 死代码：InputGate.java:211 `emptyRounds`（全仓唯一出现处）；FileSplitEnumerator.java:60,153,168 `nextSubtaskIndex`（仅在 snapshot/restore 往返，分配实际用 `i % parallelism`:122）。
- 重复块：GraphExecutionPlan.java:390-408 vs 412-428（~19 行 writer 创建复制）；CheckpointBarrierTracker.java:231-235 vs 238-242（完成逻辑逐字相同）；StreamModelDslBuilder.java:548-605（buildMap/Filter/FlatMap/KeyBy 四连同构 14 行模板）；SharedBuffer.java:334-349 vs 357-372（getEntry/getEvent 同构）。
- 触发循环三套并存（复核修正）：CheckpointCoordinator.startCheckpointScheduler(:348-414，"checkpoint-coordinator-*") 生产不可达仅 3 处测试调用；活跃两套为 JobCoordinator:1048-1080 与 GraphModelCheckpointExecutor:792-821。
- 14 处 Executors.new* 手写命名 daemon ThreadFactory（清单见审计 02）。
- 变更考古型注释：core+runtime 宽口径 ~847 行（仅 `//` 窄口径 ~305 行），前 5 集中：JobCoordinator(88)/StreamTaskInvokable(53)/InputGate(52)/CheckpointCoordinator(43)/GraphModelCheckpointExecutor(40)。
- 卫生杂项：中英混排注释（ResultPartition:239-246、MemoryStateSerDe:578）；命名（CepOperator timerService vs cepTimerService，字段约在 :154/:206；KeyGroupRange s/e :99-100）；魔法数字（InputGate 50ms 轮询 :493,618、10ms park :680）；PrintSink/PrintSinkFunction System.out 逐记录；CepOperator:304-306 缩进异常、:1131-1159 测试钩子区；WindowedStreamImpl:215,230,248,263 与 PendingCheckpoint:61,138,141、EmbeddedDistributedExecutor:401 的 StreamException 无 ErrorCode（公共 API 档规范）。
- 基线编译绿；测试基线以 358 Phase 1 记录为准（358 先行）。

## Goals

- 10 个超长方法全部拆为具名私有方法（每方法 ≤150 行，行为保持），两处 80 行逐字复制合并为模板。
- 死代码清零（2 处），4 处明显重复块去重，生产不可达触发循环删除并迁移其测试。
- top-5 文件的 change-log 注释改写为"当前不变式"注释（叙事外迁 commit/审计，编号清除）；全模块注释统一英文。
- 命名/魔法数字/缩进/测试钩子标注等卫生项收口；ThreadFactory 工具统一 14 处；公共 API 层错误补 ErrorCode。

## Non-Goals

- 不做巨型类抽离（JobCoordinator/GraphModelCheckpointExecutor 等的职责切面已在审计 02 登记，属后续结构演进，本计划仅做行为保持的文件内整改）。
- 不改任何运行时行为、协议、序列化格式、既有公开方法签名。显式例外（均为新增而非修改）：Phase 2 删除生产不可达的 public 触发循环 API（startCheckpointScheduler 及连带）；Phase 3 新增 NopStreamThreadFactory 工具类与 PrintSink/PrintSinkFunction 的注入构造器。
- 不做性能优化（→360）；不改 checkpoint 存储格式。
- FileSplitEnumerator 分配策略不重构（仅处理死字段）。

## Scope

### In Scope

- `nop-stream/nop-stream-core`、`nop-stream-runtime`、`nop-stream-cep`、`nop-stream-flow`、`nop-stream-connector`（限死字段）

### Out Of Scope

- `nop-stream-rocksdb`、`nop-stream-fraud-example`、`quickstart`、全部 `src/test`（测试仅随生产代码同步修改）
- 巨型类抽离、包结构调整、InputGate 七构造器收拢（P3，登记 follow-up）

## Execution Plan

### Phase 1 - 超长方法拆分与逐字重复合并（行为保持）

Status: planned
Targets: 10 个待拆分方法（见 Baseline 勘误）+ WindowOperator onTimer

- Item Types: `Fix`（结构 Fix，行为保持）

- [ ] Baseline 1：执行前记录受影响 4 模块测试基线（通过/失败数，写入 daily log）
- [ ] GraphExecutionPlan.build（204→≤150，拆 7 阶段具名私有方法）并顺带去重 :390-428 writer 创建复制块
- [ ] CepOperator.open（140→≤150 拆 createTimerService/initStateAccess/resolveKeyedBackend；restore-before-open 不变式注释保留）
- [ ] NFA.computeNextStates（136 行，抽 handleIgnoreEdge/handleTakeEdge，集中版本递增不变式注释）与 NFA.advanceTime（86 行 262-347，抽 timeoutPartialMatches/advanceComputationStates；注意 process 是 16 行薄包装，不改）
- [ ] StreamExecutionEnvironment.execute（132 行，拆 compilePlans/runLocal/shutdownLocalExecution）
- [ ] InputGate.readMultiChannel（114 行，抽 pollChannelOnce/emitPendingBarriers；readSingleChannel/readMultiChannel 共享 idle/EOS 谓词防漂移）
- [ ] CheckpointBarrierTracker.acknowledgeOperator（105 行，合并 :231-242 逐字重复完成块，按路由→校验→迁移→回调拆分）
- [ ] StreamModelDslBuilder.buildTransforms（91 行 282-372，抽 validateDag/topologicalOrderTransforms）+ 四连同构抽 resolveFunctionOrXpl
- [ ] StreamTaskInvokable.processInputGate（101 行，抽分发私有方法；sideOutputConsumers 线性扫改 Map 直查——纯数据结构替换，语义不变）
- [ ] ResultPartition.write（101 行，抽 dualWriteToMaterialization/enqueueWithBackpressure）
- [ ] WindowOperator onEventTime/onProcessingTime → onTimer(boolean isEventTime) 模板方法（3 处差异参数化；两者保留为 @Override 薄委托，接口签名不变）

Exit Criteria:

- [ ] 10 个待拆分方法拆分后主体均 ≤150 行（`awk`/读码复核，结果记入 daily log）
- [ ] **No new test required: 纯行为保持重构**；但现有覆盖必须保持——`./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime,nop-stream/nop-stream-cep,nop-stream/nop-stream-flow -am` 全绿且不少于 Baseline 1（WindowOperator onTimer 合并由现有窗口触发器/merging 测试覆盖，若覆盖不足则补一条 focused 测试）
- [ ] **端到端验证**（Minimum Rules #22）：现有 e2e（本地执行 source→算子→sink）保持通过
- [ ] 行为保持自证：diff 审查记录——所有拆分为 extract-method/参数化，无逻辑改动（closure audit 抽查 3 处高风险点：InputGate retry 标号控制流、NFA 版本算术、ResultPartition 四象限写入）
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 死代码、重复触发循环收敛

Status: planned
Targets: `InputGate`、`FileSplitEnumerator`、`SharedBuffer`、`CheckpointCoordinator` 及其测试

- Item Types: `Fix`

- [ ] 删除 InputGate.emptyRounds 死字段
- [ ] 删除 FileSplitEnumerator.nextSubtaskIndex 死字段（注意：该字段参与 snapshot/restore 往返——若删除会改快照格式，则保留反序列化兼容但不再写出，执行时按兼容性裁定并记录）
- [ ] SharedBuffer getEntry/getEvent 同构抽 getWithCache
- [ ] 删除生产不可达的 CheckpointCoordinator.startCheckpointScheduler 触发循环（:348-414），并做连带处置清单：stopCheckpointScheduler(:416-431，生产调用点 CheckpointCoordinator≈:1544)、isSchedulerStarted/scheduler 字段、3 处测试调用迁移——逐项裁定删除/保留并记录（stop 路径不得留下永远 early-return 的死方法）

Exit Criteria:

- [ ] 全模块 grep `emptyRounds|nextSubtaskIndex|checkpoint-coordinator-` 零命中（生产+测试）
- [ ] nextSubtaskIndex 删除的快照兼容性裁定已记录（删/保留读兼容，二选一有据）
- [ ] 相关模块测试全绿且不少于 Baseline 1（CheckpointCoordinator 测试迁移后覆盖等价）
- [ ] **无静默跳过**：触发循环删除后生产路径触发行为不变（现有周期 checkpoint 测试保持通过）
- [ ] No owner-doc update required（若无 docs 引用被删循环；发现引用则同步更新）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 注释、命名与规范卫生

Status: planned
Targets: top-5 注释文件 + 卫生杂项清单

- Item Types: `Fix`（规范补全）+ `Follow-up` 落地

- [ ] top-5 文件（JobCoordinator/StreamTaskInvokable/InputGate/CheckpointCoordinator/GraphModelCheckpointExecutor）change-log 注释改写：删除 AR-n/Stage n/P1-INV-n/HG-n/review Bn 等内部编号叙事，原地保留/改写为当前不变式说明；叙事结论已在审计档案中的可直接删除
- [ ] 全模块注释统一英文：ResultPartition:239-246、MemoryStateSerDe:578 中英混排改英文
- [ ] 命名：CepOperator timerService→internalTimerService、cepTimerService→userTimerService；KeyGroupRange s/e→start/end
- [ ] 魔法数字：InputGate 50ms 轮询/10ms park 提为命名常量
- [ ] NopStreamThreadFactory 工具类统一 14 处手写 daemon ThreadFactory（新工具类为纯委托包装；测试要求：一条单测断言线程命名与 daemon 标记，成本极低）
- [ ] PrintSink/PrintSinkFunction 改可注入 PrintStream（新增注入构造器；默认 System.out，默认行为不变；focused 测试：注入自定义流断言输出落在注入流——Minimum Rules #25）
- [ ] 公共 API 层错误码补全：WindowedStreamImpl 4 处、PendingCheckpoint 3 处、EmbeddedDistributedExecutor 1 处 StreamException 补 ErrorCode + .param()（StreamException 已支持 ErrorCode 构造器，消息英文保持）
- [ ] CepOperator:304-306 缩进对齐；:1131-1159 测试钩子区加"test-only"注释标注（不改可见性，避免跨包测试破坏）

Exit Criteria:

- [ ] grep 复核：top-5 文件中 AR-n/Stage n/P1-INV-n/HG-n 编号注释清零；全模块中英混排注释清零（抽查 CJK 扫描注释行）
- [ ] `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime,nop-stream/nop-stream-cep -am` 全绿且不少于 Baseline 1
- [ ] ErrorCode 补全后错误路径测试保持通过（无消息语义破坏）
- [ ] Phase 3 新增能力测试：PrintStream 注入 focused 测试通过；NopStreamThreadFactory 命名/daemon 单测通过（Minimum Rules #25）
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] 死代码与生产不可达触发循环清零（grep 证据）
- [ ] top-5 注释改写与卫生杂项全部落地
- [ ] 不存在被静默降级的 in-scope live defect（本计划全部为行为保持重构+规范补全，无 live defect 延期）
- [ ] No owner-doc update required（三 Phase 各有显式裁定）
- [ ] 独立子 agent closure-audit 已完成并记录证据（含行为保持抽查）
- [ ] **Anti-Hollow Check**：closure audit 抽查拆分后方法的调用链连通（无拆出来的私有方法变成无调用死代码）
- [ ] 10 个待拆分方法与 onTimer 合并全部落地且测试基线不回退
- [ ] `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime,nop-stream/nop-stream-cep,nop-stream/nop-stream-flow,nop-stream/nop-stream-connector -am` 全绿
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0

## Deferred But Adjudicated

### 巨型类职责抽离（JobCoordinator FencingEpochManager / GraphModelCheckpointExecutor RestoreEngine 等）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 属结构演进而非缺陷；审计 02 已登记完整切面与风险，需独立计划逐切面做行为保持抽离+characterization 测试，与本次可读性整改分开评审
- Successor Required: `no`（审计档案即交接物，后续按需立计划）

### InputGate 七构造器收拢 / WindowOperator 字符串键状态通道重构

- Classification: `watch-only residual`
- Why Not Blocking Closure: 前者纯 API 形态问题（P3）；后者 `\u0000` 键格式已进 checkpoint 数据，重构需格式版本兼容设计，风险收益比不适于本计划
- Successor Required: `no`

## Non-Blocking Follow-ups

- AdvancedTransforms 内建 window 测试目录迁出主代码（optimization candidate）
- SharedBuffer 翻译腔 Javadoc 与拼写修正在 Phase 3 顺带处理，若遗漏则入 follow-up

## Closure

Status Note: <<完成时填写>>
Completed: <<YYYY-MM-DD>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立子 agent>>
- Evidence: <<每条 Exit Criterion / Closure Gate 的验证结果>>

Follow-up:

- <<Deferred But Adjudicated 两项>>
