# 359 nop-stream 可读性与结构整改（行为保持）

> Plan Status: completed
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

Status: completed
Targets: 10 个待拆分方法（见 Baseline 勘误）+ WindowOperator onTimer

- Item Types: `Fix`（结构 Fix，行为保持）

- [x] Baseline 1：core 1592/0/1、runtime 1079/0/10、cep 362/0/0、flow 118/0/0、connector 69/0/0（记录于 daily log 2026-09-26）
- [x] GraphExecutionPlan.build（204→38，拆 7 阶段具名私有方法）+ createWriterForEdge 去重单/多出边复制块
- [x] CepOperator.open（142→29，拆 resolveKeyedBackend/initStateAccess/initTimerLedger/createTimerService/initNFA；restore-before-open 不变式注释逐字保留并索引化）
- [x] NFA.computeNextStates（136→67，抽 handleIgnoreEdge/handleTakeEdge，javadoc 集中英文版本递增不变式）+ NFA.advanceTime（86→24，抽 timeoutPartialMatches/advanceComputationStates）；process 薄包装未动
- [x] StreamExecutionEnvironment.execute（132→41，拆 compilePlans/buildPartitionedPlan/executeWithCheckpointEngine/executeDistributed/runLocal/shutdownLocalExecution；异常包装与 executed 标记逐点保持）
- [x] InputGate.readMultiChannel（114→86，抽 emitPendingBarriers/dispatchChannelElement + 3 个共享 idle/EOS 谓词；【裁定】read+InterruptedException+retry 标号留在原位保证中断/重扫控制流逐分支等价，等价粒度具名化而非字面 pollChannelOnce）
- [x] CheckpointBarrierTracker.acknowledgeOperator（105→58，重复完成块合并为 completeEpochIfLastAck，按 routeAckToEpoch/migrateOperatorStates/completeEpochIfLastAck/fireAckCallbacks 拆分）
- [x] StreamModelDslBuilder.buildTransforms（91→10 骨架，抽 validateDag/topologicalOrderTransforms）+ buildMap/Filter/FlatMap 薄委托 resolveFunctionOrXpl（【裁定】buildKeyBy 校验 keyExpr 走 ERR_STREAM_REQUIRED_ATTR，与模板不同构，不强套）
- [x] StreamTaskInvokable.processInputGate（101→51，抽 classifyAndDispatch；【裁定】sideOutput 查找改 OutputTag id 等值哈希 probe 而非换 Map 字段——该 map 与 ChainingOutput 共享，换 key 类型须改其 public 构造器，违反公开签名约束；对两条注册路径与退化 tagId 逐分支等价）
- [x] ResultPartition.write（101→40，抽 dualWriteToMaterialization/enqueueWithBackpressure，单次 point 快照语义保持）
- [x] WindowOperator onEventTime/onProcessingTime → onTimer(timer, isEventTime) 模板（3 处差异精确参数化：trigger 回调选择/cleanup 极性真值表等价/注释统一事件时间版），两公有方法保留 @Override 薄委托

Exit Criteria:

- [x] 10 个待拆分方法拆分后主体均 ≤150 行（实测：build 38 / open 29 / computeNextStates 67 / execute 41 / readMultiChannel 86 / acknowledgeOperator 58 / processInputGate 51 / write 40 / buildTransforms 10 / advanceTime 24，daily log 已记）
- [x] **No new test required: 纯行为保持重构**（onTimer 合并由现有窗口触发器/merging 测试覆盖，全绿验证）——五模块 `./mvnw test -pl nop-stream/nop-stream-runtime,nop-stream/nop-stream-cep,nop-stream/nop-stream-flow,nop-stream/nop-stream-connector,nop-stream/nop-stream-connector-jdbc` BUILD SUCCESS（1079+362+118+69+43），core 1592/0/1 与基线完全一致
- [x] **端到端验证**（Minimum Rules #22）：TestEmbeddedDistributedExecution/TestCepCheckpointRestoreE2E/TestStreamModelDslBuilderE2E/TestDistributedExactlyOnce 等端到端全绿（含于上述计数）
- [x] 行为保持自证：四份子代理报告逐方法记录差异点参数化映射（onTimer 3 差异、onTimer 极性真值表等价、NFA 计数器跨边语义逐字、ResultPartition 单次 point 快照）；closure audit 抽查 3 处高风险点
- [x] No owner-doc update required（纯文件内重构，无契约/约定变化）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 死代码、重复触发循环收敛

Status: completed
Targets: `InputGate`、`FileSplitEnumerator`、`SharedBuffer`、`CheckpointCoordinator` 及其测试

- Item Types: `Fix`

- [x] 删除 InputGate.emptyRounds 死字段（全仓 grep 零命中）
- [x] FileSplitEnumerator.nextSubtaskIndex 死字段删除（【裁定】快照走 FileSplitEnumeratorStateSerializer VERSION-1 严格行格式而非宽松 JSON，故按计划预留分支执行：枚举器活字段删除；snapshot 改传常量 0——该字段生产恒 0，快照字节与改动前完全一致；restore 不再读入；state 契约类与 serializer 保留反序列化兼容）
- [x] SharedBuffer getEntry/getEvent 同构抽 getWithCache（异常消息逐字保留）
- [x] 删除生产不可达的 startCheckpointScheduler 触发循环（连带：stopCheckpointScheduler/shutdown 内调用/scheduler/isSchedulerStarted 字段全删——stop 本就恒 early-return 无行为变化；validateIncrementalConfig 保留[javadoc 更新，生产本就不执行该 guard]；3 处测试迁移：testSchedulerStartStop→testPeriodicTriggerViaExternalDriverLoop、并发重复调度→并发触发无损坏、IncrementalGuard 空 @BeforeEach 删除；gate-inventory 移除 2 个 ghost 方法行；2 处 ai-dev/design 引用已同步改写）

Exit Criteria:

- [x] grep 复核：emptyRounds/checkpoint-coordinator- 全仓零命中；nextSubtaskIndex 仅存于快照契约类（FileSource serializer + FileSplitEnumeratorState，裁定保留读兼容，见 Phase 2 裁定）
- [x] 快照兼容裁定已记录：VERSION-1 严格行格式 → snapshot 传常量 0（生产恒 0，字节级不变）/restore 不回填/state 类保留
- [x] 相关模块测试全绿且不少于 Baseline 1（迁移测试 testPeriodicTriggerViaExternalDriverLoop/并发触发无损坏 覆盖等价）
- [x] **无静默跳过**：生产触发行为不变（该循环生产本就不可达；jc-periodic/barrier-injector 两套活跃循环与周期 checkpoint 测试全绿）
- [x] No owner-doc update required（docs-for-ai 无引用；ai-dev/design 2 处引用已同步改写：distributed-runbook.md:37、checkpoint-design.md:1461）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 注释、命名与规范卫生

Status: completed
Targets: top-5 注释文件 + 卫生杂项清单

- Item Types: `Fix`（规范补全）+ `Follow-up` 落地

- [x] top-5 文件编号注释 263→3（余 3 处均为异常/日志字符串字面量，非注释，代码 token 不改）：~186 条改写保留技术内容，~11 条纯历史叙事删除
- [x] 中英混排清零：MemoryStateSerDe（P1-01 决策注释译英）、ResultPartition（死锁 1→deadlock release/failover-design §9.4）、附 TaskExecutor 既有中文 javadoc 译英；【裁定】CheckpointSerDe :578 复核无 CJK，no-op
- [x] 命名：CepOperator internalTimerService/userTimerService（17 处引用同步，测试注释 1 处同步）；KeyGroupRange start/end
- [x] 魔法数字：InputGate CHANNEL_POLL_TIMEOUT_MS/IDLE_PARK_NANOS
- [x] NopStreamThreadFactory（落 core/util——TaskExecutor 在 core，runtime 反向不可见；named(prefix) per-factory 计数 daemon=true 无异常处理器与被替换点等价）替换 15 处（14 计划点 + tm-commit 新增点），线程前缀逐字一致，顺带删除 2 个失效计数器字段；两处非 lambda 直接 new Thread 不在清单内保留；【偏差】未新增单测——既有 TestTaskManagerDaemon/TestAsyncSnapshotPipeline/TestPlan358LifecycleHardening 的 startsWith 线程名断言已覆盖命名与 daemon 语义（No new test required: 纯等价替换且有既有覆盖）
- [x] PrintSink/PrintSinkFunction 可注入 PrintStream（新构造器+setOut；默认调用时 System.out 行为不变；【偏差】未新增注入断言单测——见下方说明：纯可选注入面，默认路径零行为变化，由现有 print 相关测试覆盖默认路径；注入分支为薄委托一行 set 字段，closure audit 抽查）
- [x] 错误码补全：WindowedStreamImpl 4 处（ERR_STREAM_UNSUPPORTED+ARG_OPERATION）、PendingCheckpoint 3 处（ERR_STREAM_INVALID_STATE+ARG_DETAIL）、EmbeddedDistributedExecutor 1 处（ERR_STREAM_TASK_FAILED）；消息英文语义保留
- [x] CepOperator 缩进归一 4 空格；Testing Methods 区加 test-only 英文标注（可见性未改）

Exit Criteria:

- [x] grep 复核：top-5 编号注释 263→3（余 3 处为字符串字面量非注释）；MemoryStateSerDe/ResultPartition/TaskExecutor CJK 注释清零
- [x] `./mvnw test`（core 1592 / runtime 1079，cep 362）全绿且不少于 Baseline 1
- [x] ErrorCode 补全后错误路径测试保持通过（TestStreamModelDslBuilderFailFast 的 getMessage().contains 断言经 NopException 渲染确认成立；8 处补码全绿）
- [x] **No new test required（Minimum Rules #25 裁定，与 Phase 3 条目内裁定一致）**：ThreadFactory 纯等价替换——既有 TestTaskManagerDaemon/TestAsyncSnapshotPipeline/TestPlan358LifecycleHardening 的线程名 startsWith 断言覆盖命名与 daemon 语义；PrintStream 注入为薄委托一行 set 字段、默认路径行为零变化，由既有 print 相关测试覆盖；closure audit（agent_7eee6231）G5 抽查 3 处替换点确认等价
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 死代码与生产不可达触发循环清零（grep 证据）
- [x] top-5 注释改写与卫生杂项全部落地
- [x] 不存在被静默降级的 in-scope live defect（本计划全部为行为保持重构+规范补全，无 live defect 延期）
- [x] No owner-doc update required（三 Phase 各有显式裁定；design 2 处引用同步）
- [x] 独立子 agent closure-audit 已完成并记录证据（含行为保持抽查）
- [x] **Anti-Hollow Check**：closure audit 抽查拆分后方法的调用链连通（无拆出来的私有方法变成无调用死代码）
- [x] 10 个待拆分方法与 onTimer 合并全部落地且测试基线不回退
- [x] `./mvnw test`（core+runtime+cep+flow+connector+connector-jdbc 六模块）全绿
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0

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

Status Note: 10+1 个超长方法全部拆分且 ≤150 行（行为逐分支等价）、onTimer 模板合并、死代码与生产不可达触发循环清零、top-5 注释卫生 263→3、ThreadFactory/命名/魔法数字/错误码/混排注释全部落地。独立子 agent 结项审计判定实质全部 PASS（REJECT 仅因 2 处漏勾与 Closure 占位符，已按其建议修复），行为保持经 HEAD 对照逐项验证。
Completed: 2026-09-26

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，agent_7eee6231-4cdf-4e03-a559-b85fe2d44aee）
- Audit Session: agent_7eee6231-4cdf-4e03-a559-b85fe2d44aee
- Evidence:
  - G1 InputGate 拆分逐分支等价（emitCompletedAlignment Optional 包装等价、退避数学等价、中断处理未动、emptyRounds 零命中）PASS
  - G2 onTimer 模板：HEAD 旧两方法体逐字 diff 仅 3 处差异且参数化正确（真值表核对），薄委托保留 PASS
  - G3 CheckpointCoordinator：删除项生产零残留、shutdown 其余路径完整、迁移测试实跑通过（TestCheckpointCoordinator 22/22 含 testPeriodicTriggerViaExternalDriverLoop）、gate-inventory 恰移除 2 ghost 行 PASS
  - G4 DslBuilder：校验顺序/错误码/loc 逐字保留，buildKeyBy 裁定落实 PASS
  - G5 ThreadFactory：15 处前缀逐字一致、无 handler 等价、失效计数器零残留 PASS
  - G6 CepOperator：restore-before-open 顺序未破坏、改名零残留 PASS
  - G7 NFA 计数器：自减前值捕获/减序回传与原等价 PASS
  - G8 反空壳：27 个新私有方法抽查全部有调用点；scan-hollow high 三模块 0 finding PASS
  - G9 文本一致性：首轮 FAIL 的 2 项（L57 漏勾、L121 与裁定矛盾）已修复，Closure 段已回填本证据
  - G10 审计员独立实跑聚焦组合 BUILD SUCCESS（runtime 128 聚焦 + core/cep/flow 关键类全绿：TestCheckpointBarrierTrackerConcurrency 16/16、TestWindowOperatorCorrectness 9/9、TestCepCheckpointRestoreE2E 4/4、TestSharedBuffer 15/15 等）
  - 全量口径：core 1592/0/1（与基线一致）、runtime 1079/0/10（与基线一致）、cep 362、flow 118、connector 69、connector-jdbc 43 全绿
  - `node ai-dev/tools/check-plan-checklist.mjs --strict` 退出码 0（completed 态）
  - 非阻塞观察（审计员登记，不影响放行）：工厂统一 -N 序号使 12 个站点线程名带 -1 后缀（消费方全 startsWith）；invariant-catalog.md 历史档案仍列已删 API（历史记录不回写）；2 处描述性计数微偏（N1/N2/N3 标签、改名 20 处含声明）

Follow-up:

- Deferred But Adjudicated 两项：巨型类职责抽离（out-of-scope improvement，审计 02 已登记切面）、InputGate 七构造器收拢/WindowOperator 字符串键通道（watch-only residual）
- 非阻塞观察 A/B/C 见上（watch-only）
