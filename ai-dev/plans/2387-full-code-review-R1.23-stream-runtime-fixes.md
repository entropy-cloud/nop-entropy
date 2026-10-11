# 2387 full-code-review R1.23 — nop-stream core/runtime 修复批（P1-6.7-007/008 + P1-7.2-001）

> Plan Status: completed
> Last Reviewed: 2026-10-10
> Completed: 2026-10-10
> Source: roadmap `ai-dev/backlog/full-code-review-roadmap.md` R1.23；findings `P1-6.7-007`/`P1-6.7-008`（`ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/6.7-check-p1-batch-c.md`）+ `P1-7.2-001`（`7.2-nop-stream-incremental-audit.md`）
> Related: R1.22（同族 stream，独立 plan）；执行顺序裁定：本 plan 提前于 R1.21/R1.22 执行——P1-7.2-001 使 nop-stream-runtime 测试门禁恒红，阻断所有 `-am` 全链 gate（R1.20 已实测中断于该模块），先修解锁后续工作项验证链
> Owner Doc: `docs-for-ai/03-modules/nop-stream.md`

## Purpose

收口 nop-stream 三条 P1：①TimerServiceManager 吞用户定时器异常（事件时间窗口触发失败静默丢发射且作业无感知）；②SourceReaderOperator.restoreState 在 restore-先于-open 时序（SupervisionLoop.rebuildTask 新建链恢复路径）静默丢弃 split 位点；③TestStreamConnectorRegistryDiscovery 断言与 WI4 已裁决的 PARALLEL 工厂语义失配（模块测试门禁恒红）。

## Current Baseline

- **P1-6.7-007**：`nop-stream-core/.../TimerServiceManager.java:45-53`（advanceWatermark）与 `:55-63`（fireProcessingTimeTimers）逐 service `catch(Exception){LOG.error}` 零漂移——首个 service 失败后其余 service 照常推进、watermark 照常转发，异常不上抛。`TestTimerServiceManagerRobustness` 2/2 绿固化的是缺陷契约本身（`assertDoesNotThrow`）。**上游二次吞点（审查 F1 裁定纳入 scope）**：`ProcessingTimeServiceDriver.fireDueTimersOnTaskThread:139-151` 对 manager 调用包 `catch(Exception){LOG.error}`，其注释引用 manager「established robustness contract」将随本修失效；:141 的直接 `processingTimeService.fireDueTimers(now)` 无 catch 可传播（mail action 异常经 TaskMailbox.drainAndRun:149-155 无捕获上抛至任务循环）——删除该 catch 即与直接路径对齐 fail-fast。
- **P1-6.7-008**：`SourceReaderOperator.java:395-410` restoreState 守卫 `reader != null && snapshotResult != null`——reader 仅 `open():236` 创建；恢复路径 `RescaleStateAssembler.restoreOperatorsFromState:454`（SupervisionLoop.rebuildTask 复用）作用于**新建链**（open 未发生），恒 null → READER_SPLITS_KEY 静默跳过 → 恢复后 per-split 位点全丢。同类先例：preOpenSplits 缓冲（:98/:202-215/:255-267）与 AbstractStreamOperator 自带 pendingRestoreState（:68/:230 + applyPendingRestoreState:260-269）——暂存字段为模块既有模式。**恢复语义已裁定（审查 F3）**：`SourceReader.restoreState` javadoc（:69-77）明确 "Called before start() on recovery"，FileSourceReader.restoreState:406-419 为替换语义——**flush 顺序钉死：reader 创建→bridge channel→flushPreOpenSplits→restore flush→reader.start()**（restore flush 若在 enumerator 交付 flush 之前，后到 addSplits 会追加重复 split）。
- **P1-7.2-001**：`TestStreamConnectorRegistryDiscovery.java:130-133` 断言 debezium-cdc SINGLE_INSTANCE；WI4（1dad44a0c6）工厂 `DebeziumCdcSourceConnectorFactory.java:38` 已裁决 PARALLEL（B 层表路由，cdc-design.md §3.3），connectors 文档 `nop-stream-connectors.md:29/:87` 已同步——唯测试孤立过期（窗内 05e81257e6 二次触碰仍漏改）。类内耦合核查（审查 F9）：probe :197-198 instanceAligned 断言走 consistency 对齐与 parallelism 无关；listing :274-285 仅钉 2PC 行；aliases :110-114 无涉——无其他失配。
- **测试基建**：SourceReaderOperator 最小测试**不可依赖 null proxy**（审查 F4——`wireCoordinatorChannel:171-193` 经 SourceCoordinatorRegistry.registerIfAbsent 无条件创建 coordinator 并 boot 真实 enumerator，open():231-232 注释描述的 null 分支不可达）。须全套 Source stub（照 `TestSourceApiRouting.java:32-170` NoOpSource/NoOpEnumerator/NoOpReader 先例，SourceReader 必须 override isFinished()）；SourceCoordinatorRegistry 为静态 map 须独立 vertexId + 测试清理（先例 :281/:299）；stub 无位点时 isFinished()=true 使 run() 立即退出（快败非挂死）。OperatorSnapshotResult 公有无参构造 + putOperatorState 直构（先例 TestOperatorStateWiring:232）。

## Goals

- TimerServiceManager 两方法 fail-fast：首个异常抛出、其余 addSuppressed、全部 service 均被尝试；per-service LOG.error 保留为诊断。ProcessingTimeServiceDriver 删除对 manager 调用的二次吞 catch（含失效注释）——与直接 service 路径对齐，任务线程 mail action 异常传播致任务失败。
- SourceReaderOperator：restore-先于-open 时位点滴入暂存字段，open() 按「reader 创建→bridge→flushPreOpenSplits→restore flush→reader.start()」顺序 flush（对齐 preOpenSplits 模式）；恢复→重开→续读 **reader 侧位点**不丢。
- 测试门禁恢复绿：debezium-cdc 断言同步 PARALLEL（禁止回退工厂语义）。
- 判别测试三腿 stash 实证；nop-stream-core/runtime 模块测试全绿。

## Non-Goals

- StreamTaskInvokable/SupervisionLoop 的恢复编排重构；checkpoint 存储格式变更；legacy StreamSourceOperator（SourceFunction 代）同型面；P2-7.2-001 keyTuple（台账通道）；enumerator 重投 split 与 restore 位点重叠的去重语义裁定（见 Non-Blocking Follow-ups）。

## Scope

### In Scope

- `TimerServiceManager.java` 两方法异常聚合；`TestTimerServiceManagerRobustness.java` 重写（缺陷契约→fail-fast 契约）
- `ProcessingTimeServiceDriver.java:139-151` 删除 manager 调用的二次吞 catch + 失效注释（F1 裁定）
- `SourceReaderOperator.java` restoreState 暂存+open flush（顺序钉死）；`SourceReader.java:22-23` 生命周期 javadoc 自相矛盾修正（restore 在 start 前）；新增恢复时序回归测试
- `TestStreamConnectorRegistryDiscovery.java` debezium-cdc PARALLEL 断言同步
- owner doc：nop-stream.md 简注（定时器异常 fail-fast 语义〔manager+driver 两级〕；source reader 恢复时序契约）

### Out Of Scope

- Non-Goals 全部；nop-stream-sql（R1.22）；connectors 文档（已同步，仅核对）

## Execution Plan

### Phase 1 - 三修 + 判别测试

Status: completed
Targets: 上列文件

- Item Types: `Fix`

- [x] TimerServiceManager：advanceWatermark/fireProcessingTimeTimers 改异常聚合（首个抛出+addSuppressed；循环不中断——全部 service 尝试）；javadoc 声明 fail-fast 契约
- [x] ProcessingTimeServiceDriver：fireDueTimersOnTaskThread 删除 manager 调用的 try/catch 与失效注释——manager 异常与 :141 直接路径同语义传播（mail action 异常经 drainAndRun 无捕获上抛）
- [x] TestTimerServiceManagerRobustness 重写：failing+good 双 service → 断言异常抛出（首个原因保留）**且** second service 仍被推进（addSuppressed 含第二异常时也断言）；no-services 空转用例保持；补 driver 级传播测试（fireDueTimersOnTaskThread 经 mailbox drain 异常上抛——不经 scheduler 线程，直测 mail action）
- [x] SourceReaderOperator：restoreState 在 reader==null 时将 splits 暂存 `pendingRestoreSplits` 字段；open() 按「reader 创建→bridge channel→flushPreOpenSplits→restore flush→reader.start()」顺序 flush；reader!=null 时行为不变
- [x] SourceReader.java:22-23 生命周期 javadoc 修正（restore 在 start 前，与方法级 javadoc 对齐）
- [x] 新增 TestSourceReaderOperatorRestoreBeforeOpen：全套 Source stub（照 TestSourceApiRouting NoOpSource 先例，含独立 vertexId+registry 清理；无恢复位点时 isFinished()=true 快败）；restore(带位点快照)→open()→run→断言 reader 收到恢复位点；含 reader 先开时序的既有行为回归
- [x] TestStreamConnectorRegistryDiscovery:132 SINGLE_INSTANCE→PARALLEL；类内一致性断言与 connectors 文档 §:87 核对无失配（审查 F9 已预核）
- [x] **判别验证（三腿 stash）**：①stash TimerServiceManager+Driver 改动→重写后的 robustness 测试 FAIL（异常未抛出）；②stash SourceReaderOperator 改动→restore-before-open 测试 FAIL（位点丢失）；③stash 测试断言改动→discovery 测试恢复红（证明 WI4 语义在先）——各腿恢复后绿
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [x] 三修全落地（异常聚合/恢复暂存/断言同步）
- [x] 判别测试三腿绿 + 既有测试零回归
- [x] nop-stream-core/nop-stream-runtime 测试全绿（模块门禁恢复）
- [x] owner doc 处置归 Phase 2

### Phase 2 - owner doc + 收口

Status: completed
Targets: `docs-for-ai/03-modules/nop-stream.md`

- Item Types: `Fix`

- [x] nop-stream.md：定时器异常 fail-fast 语义 + source reader 恢复时序契约（restore-before-open 位点暂存）简注
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] roadmap R1.23 翻 done；index P1-6.7-007/008、P1-7.2-001 → fixed；daily log 条目

Exit Criteria:

- [x] owner doc 条目在位且与 live 行为一致
- [x] doc-links 检查退出码 0
- [x] 收口动作落盘

## Closure Gates

- [x] 所有 in-scope confirmed live defects 已修复（P1-6.7-007/008 + P1-7.2-001）
- [x] 行为/契约结果已达成（判别三腿 + 零回归）
- [x] 必要 focused verification 已完成（nop-stream-core/runtime 全绿）
- [x] 不存在被静默降级的 in-scope live defect
- [x] 受影响 owner docs 已同步
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime` 全绿（含 -am 链恢复验证：file/tcc/retry gate 链通过 stream-runtime）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- legacy StreamSourceOperator（SourceFunction 代）同型恢复面核查（属冻结代维护线）
- P2-7.2-001 keyTuple（台账通道，勿在本 plan 双立）
- runtime rebuild 路径 enumerator 全新 boot 重投 split 与 restore 位点重叠的去重语义（FileSourceReader restore=替换/后到 addSplits=追加；单元测试不可达，需编排级裁定——审查 F10）

## Closure

Status Note: 三修全部落地且与 plan/审查裁定逐点一致（含 F1 checked 包装机制约束、F3 钉死 flush 顺序）；三个判别腿失败特征精确（timer 3 FAIL / restore 2 FAIL 且 after-open 直路径腿绿 / assertion 恢复红证明 WI4 工厂语义在先）；重写与新增测试共 17 用例全绿且带真实失败注入；core 1676 + runtime 1233 全量零失败——nop-stream-runtime 测试门禁恢复（此前 TestStreamConnectorRegistryDiscovery 恒红阻断所有 -am 全链 gate）；无静默跳过残留；roadmap/index/daily log/owner doc 四处收口在位。**执行顺序裁定**：本 plan 提前于 R1.21/R1.22 执行（gate 解锁），roadmap 行内已登记。

Completed: 2026-10-10

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session）agent_5bafd76c-4697-4208-a05d-f9582d71c8e0
- Evidence:
  - P1-6.7-007 PASS：TimerServiceManager.java:53-68/:76-91 异常聚合（首因抛出+全尝试+addSuppressed）；ProcessingTimeServiceDriver.java:146-159 RuntimeException 原样重抛/checked 按 ERR_STREAM_OPERATOR_ERROR 包装（Mail.control=Runnable 机制约束在 javadoc :138-144 声明）；owner doc「定时器异常语义」与 live 行为逐点一致
  - P1-6.7-008 PASS：SourceReaderOperator.java:108 pendingRestoreSplits 声明、restoreState:412-434 暂存、open():232-263 五步钉死顺序（createReader:245→bridge:249→flushPreOpenSplits:252→flushPendingRestoreSplits:258→start:260）、flushPendingRestoreSplits:444-452 替换语义；SourceReader.java:22-29 类头 javadoc 矛盾消除
  - P1-7.2-001 PASS：TestStreamConnectorRegistryDiscovery.java:130-134 PARALLEL 断言 + 工厂:38 未回退
  - 测试证据（surefire）：TestTimerServiceManagerRobustness 4/4、TestProcessingTimeServiceDriver 10/10（含传播测试 :233）、TestSourceReaderOperatorRestoreBeforeOpen 3/3、TestStreamConnectorRegistryDiscovery 8/8；模块全量 _tmp/stream-tests.log：core 1676/runtime 1233 全零失败 BUILD SUCCESS
  - 判别证据：_tmp/disc-timer.log（3 FAIL——"Expected StreamException to be thrown, but nothing was thrown"）、_tmp/disc-restore.log（2 FAIL：before-open 腿 :71+run 腿 :113 位点空；**after-open 直路径腿保持绿**=精确判别）、_tmp/disc-assertion.log（1 FAIL 恢复红——debezium-cdc expected SINGLE_INSTANCE but was PARALLEL，证明工厂语义在先）
  - Anti-Hollow：pendingRestoreSplits 唯一写点 restoreState:431/唯一消费点 flushPendingRestoreSplits:447/唯一调用点 open():258（grep 核实）；异常聚含有真实失败注入非死代码；TaskMailbox.drainAndRun:149-155 无 catch、manager 其余生产调用方（AbstractStreamOperator:402/StreamSinkOperator:103）均 throws 无吞点
  - 收口完整性：roadmap:154 done；index :158/:159/:171 fixed；daily log 10-10.md R1.23 条目；owner doc nop-stream.md:19-25 两节
  - 工具门禁：doc-links --strict 0；checklist --strict 0（completed 态复跑确认）；hollow 扫描（nop-stream/core/runtime）退出码均 0
  - Follow-ups 诚实性：legacy StreamSourceOperator 的 sourceFunction 构造器 :97 赋值（restore-before-open 形态不适用）；P2-7.2-001 台账通道未双立；enumerator 重投去重为编排级真实问题但单元不可达且恢复位点已是最终权威——三条均真 non-blocking

Follow-up:

- legacy StreamSourceOperator 同型恢复面核查（冻结代维护线）；P2-7.2-001 keyTuple（台账通道）；enumerator 重投 split 与 restore 位点重叠的去重语义（编排级，审查 F10 登记）
