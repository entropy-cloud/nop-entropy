# 369 nop-stream R6 波次——Flink 2.3 对比新发现修复与低成本采纳

> Plan Status: completed
> Last Reviewed: 2026-09-30
> Source: `ai-dev/analysis/nop-stream/09a..09e-flink2.3-compare-*.md`（Flink release-2.3.0 对比五章节）+ `10-flink2.3-quality-verdict.md`（总裁定：5 项 P2 + 约 10 项低成本可采纳）
> Related: `ai-dev/plans/368-nop-stream-audit-r5-defects-governance.md`（R5 波次，completed）；裁定总表 successor 项的**部分**提前收口（CC-07/CC-10/CC-15/W-M1）
> Plan Review: 独立子 agent 对抗性审查（含想象性分析）2026-09-30：0 Blocker + 6 Major（F-3/4/6/9/15/16 语义决策未定）+ 14 Minor，全部已折入本版；审查确认全部锚点行号与 live repo 一致

## Purpose

修复 Flink 2.3.0 对比中新发现的 5 项 P2 生命周期端点缺口（C1/C2/C3/G-3/NEW-A），并落地低成本可采纳清单中的高价值项，使 nop-stream 达到"充分质量"判定线；每项修复带聚焦回归测试（行为变更项修复前可复现）。

## Current Baseline

- HEAD = 9f859061cb（plan 368 收口 + Flink 对比分析提交；其后有 docs-only 提交不触 nop-stream，基线声明仍成立）；`cd nop-stream && ../mvnw test -pl <9 模块> -am` 全绿（`_tmp/r5-final-regression.log` EXIT=0）。
- 对比裁定（10-flink2.3-quality-verdict.md）：质量定位内 4/5，差 5 项 P2 可判充分。
- 关键锚点（对比 agent 已核实，实施时以 live code 复核）：
  - C1：`InputGate.checkAlignmentElapsed()`（InputGate.java:828，调用点 :702）背压超时不区分 checkpoint 类型，savepoint 静默降级 unaligned。审查核实：`CheckpointBarrier` 已携带 `checkpointType`（CheckpointBarrier.java:29-46）、`BarrierAlignment` 已持有 `firstBarrier`（InputGate:891）——豁免只需在降级分支读类型，无需 barrier 加字段或 coordinator 查询通道。
  - C2：终止路径撞 in-flight 周期 checkpoint 时 `tryTriggerPendingCheckpoint`（CheckpointCoordinator.java:337）返回 null → 跳过 terminal savepoint 直接完成；且 terminal 路径 catch(Exception) 后仍 stop()。
  - C3：`unregisterTask` 零生产调用方——任务 COMPLETED 后仍留在 checkpoint ACK 集合，多源有界作业每周期撞 600s 超时 abort 循环。
  - G-3：`WindowOperator` cleanup/purge 路径从不调用 `processContext.clear()`（Flink `clearAllState` 有此调用）——per-window `ctx.windowState()` 永久泄漏。
  - NEW-A：`RescaleStateAssembler.buildRescaledTaskState` operator state 1:1 按索引、扩容置空（缩容已由 plan 368 AR-03 fail-fast）。
  - G-2/09e①：迟到 side output 无公共 API（`WindowedStream` 无方法；`WindowOperatorBuilder.lateDataOutputTag()` 为孤儿 setter；线协议已在，缺 getSideOutput 检索）。
  - G-1：缺 `ProcessingTimeSessionWindows`（session 合并仅事件时间）。
  - NEW-B：`restoreBroadcast` 取首个非空快照，弱于 Flink 并集语义（RoundRobinOperatorStateRepartitioner :277-286 对照）。
  - W-M1：`WindowOperator:412` numLateRecordsDropped 无 tag 共享注册（CepOperator 已有 tag 方案可照抄）。
  - CC-15：`JdbcLeaderElector.tryBecomeLeader` 硬编码 epoch=1；CC-10：commitExecutor 单线程无界队列；CC-07：deployTask 传输失败后 liveness 永久豁免。
  - ST-12 部分：memory TTL 仅 checkpoint 时清理、`expiredKeys()` 全表拷贝。

## Goals

- 5 项 P2 全部修复：C1（savepoint 强制 aligned）、C2（终止触发排队 + 失败响亮）、C3（ACK 集合收缩 + 状态继承）、G-3（per-window state 清理）、NEW-A（扩容 operator state fail-fast）。
- 低成本采纳落地：G-2/G-1/T-1/D-2/W-M1、NEW-B、O(1) assignKeyGroupToSubtask、TTL sweep、RocksDB 内存管控预设、CC-15/CC-10/CC-07、C4 校验条目、getSideOutput API。
- 修复后 nop-stream 达到"充分质量"判定线（10 号裁定的解除条件）。

## Non-Goals

- 不做 NEW-C（barrier 优先队列，正确性无损、中成本）、C-A3（channel-state rescale descriptor，中成本）、C-A5（CheckpointFailureReason 枚举，纯风格）——维持裁定表/新登记 follow-up。
- 不做 stableHash murmur 加扰（需 hashPolicy 版本绑定，owner 决策）、Java API 默认 guarantee 降档（owner 决策）、xdef 声明面 9 项（独立波次）、file sink 滚动/2PC 重试协议/CON-11 spill（连接器管理面专项）。
- 不重开 plan 368 Deferred 裁定（除 CC-07/CC-10/CC-15/W-M1 四项本波次提前收口）。

## Scope

### In Scope

- `nop-stream-runtime`：CheckpointCoordinator、GraphModelCheckpointExecutor、SupervisionLoop（CC-07 宽限期）、cluster/JdbcLeaderElector、checkpoint storage、WindowOperator 及其 windowing 测试。
- `nop-stream-core`：InputGate（对齐超时豁免）、CheckpointBarrier（类型携带）、assignKeyGroupToSubtask、memory state backend TTL、KeyedStateStore/WindowedStream API 面、TimestampsAndWatermarksOperator。
- `nop-stream-rocksdb`：内存管控预设（可配）。
- 文档：checkpoint-design.md（C1-C4 语义段）、failover-design.md（CC-07）、state-management-design.md（NEW-A 裁定）、connector 冻结政策（user-guide 或 connectors.md）。

### Out Of Scope

- Flink 对比裁定的定位外能力（SQL、双流 join、Netty 级网络等）。
- 09e 连接器管理面专项与 xdef 声明面波次。

## Execution Plan

### Phase 1 - Checkpoint 生命周期端点闭环（C1/C2/C3/C4）

Status: completed
Targets: `InputGate.checkAlignmentElapsed`、`CheckpointCoordinator`（触发/ACK 集合/状态继承）、terminal savepoint 路径（GraphModelCheckpointExecutor/JobCoordinator 终止段）、capability 校验

- Item Types: `Fix`

- [x] C1：savepoint 强制 aligned——对齐超时降级 unaligned 前读 `BarrierAlignment.firstBarrier.getCheckpointType()`；**全部非 CHECKPOINT 类型**（SAVEPOINT/TERMINAL_SAVEPOINT/EXPORTED_SAVEPOINT 家族，用 `isCheckpoint()` 判据）不降级，超时按 checkpoint 超时响亮失败（`ERR_STREAM_BARRIER_ALIGNMENT_TIMEOUT` 已是 typed）。类型字段已存在，不动 barrier 序列化面。
- [x] C2：终止/保存点触发排队（**4 处调用点全部覆盖**：JobCoordinator:2201 DISTRIBUTED 终止、GraphModelCheckpointExecutor:403 LOCAL triggerSavepoint、:502 LOCAL handleJobTermination DRAIN/SUSPEND、:721 COMPLETED final checkpoint——LOCAL 面是嵌入式主用法，与 DISTRIBUTED 同型修复）。用 `tryTriggerCheckpointWithReason`(:354) 的 typed reason 分类处置：in-flight/minPause → 在剩余 checkpoint timeout 内有界轮询重试（等待当前 in-flight 完成）；**NO_TASKS_TO_ACK → 短路为成功**（C3 落地后有界作业全完成时终止 savepoint 的空 ACK 集合法，防止 C2×C3 交互把正常停机变成 job fail）；最终失败 → TERMINAL_SAVEPOINT/COMPLETED 场景 job fail（响亮），EXPORT_SAVEPOINT（KEEPS_JOB_RUNNING）场景响亮记录并保持作业运行（选择记录于 log）。"catch 后仍 stop()"问题一并消除，但 **InterruptedException 路径维持 plan 368 CC-11 有记录的中断→stop 语义不变**。
- [x] C3：ACK 集合收缩——checkpoint 参与集合排除 terminal 态任务（DISTRIBUTED 挂点：`reportTaskStatus` 的 completedSubtaskKeys tombstone，JobCoordinator:1003-1017；LOCAL：SupervisionLoop 终态扫描新增到 coordinator 的通知接线）；其状态自 `latestCompletedCheckpoint`（CheckpointCoordinator:167，`getTaskState(location)` 按 TaskLocation 可查）继承。**继承语义（审查 F-6 裁定）**：快照对象**引用计数迁移**到新 epoch——增量 RocksDB 的 SST 段 handle 必须经 SharedStateRegistry 等价机制把旧段引用转移到新 checkpoint（Flink DefaultCheckpointPlanCalculator + SharedStateRegistry 做法），禁止裸引用共享（旧 checkpoint 被 retention 裁剪 → 恢复读已删段）；非增量 JSON 状态引用共享可接受。恢复侧（RescaleStateAssembler 读新 checkpoint 的 taskStates）天然兼容。新 checkpoint 仍含全量任务状态，恢复语义不变。**本项落地即关闭 10 号裁定 Open Question 3 的"显式降档"分支（operator state 重分布接线仍归 backlog successor，扩容 fail-fast 见 Phase 3 NEW-A）——state-management-design.md 的登记即裁定记录。**
- [x] C4：capability 校验补"输出侧在途数据 × transport 持久性"条目。**落点裁定（审查 F-9）**：全仓无 transport 持久性能力声明且该事实仅 runtime 可知（IMessageService 属上游平台模块）——选 core 声明档位：`CheckpointConfig` 新增 transport 持久性声明（默认 durable，保守），runtime 部署接线（TaskManager 构建 checkpoint 配置处）按实际 transport 填充；校验在 core `validateUnalignedConfig` 启动链执行；**不改 nop-api-core**。非持久 transport × 声明 unaligned 输出 → 启动期 typed fail-fast。
- [x] 测试（各修复前可复现，存 `_tmp/r6-p1-*.log`）：savepoint 背压场景不降级（含 EXPORTED_SAVEPOINT）；terminal savepoint in-flight 撞车后仍完成/响亮失败（LOCAL 与 DISTRIBUTED 两个形态各一）；NO_TASKS_TO_ACK 终止短路成功；有界作业完成后周期 checkpoint 正常完成（不再超时循环）——**E2E 用 LOCAL（嵌入）形态**执行以同时暴露 LOCAL 接线面（审查 F-7）；capability 条目拒绝组合；**完成 2PC sink × 继承状态 × 恢复重提交幂等**一例（依赖 CON-01 账本 guard，审查 F-8）；增量 RocksDB 段引用迁移（旧 checkpoint retention 裁剪后新 checkpoint 恢复仍可读——F-6 核心测试）。

Exit Criteria:

- [x] C1-C4 各有聚焦测试且通过；行为变更项有修复前复现记录。
- [x] 有界作业 + 完成后周期 checkpoint 场景 E2E 不再出现 600s 超时循环（测试断言）。
- [x] `nop-stream-runtime`、`nop-stream-core` 测试全绿。
- [x] owner doc：`checkpoint-design.md` 新增"生命周期端点契约"段（savepoint 对齐豁免/终止触发/ACK 收缩/transport 条目）。
- [x] `ai-dev/logs/2026/09-30.md`（或 10-01.md，按提交日）已更新。

### Phase 2 - 窗口/CEP 收口（G-3/G-2/G-1/T-1+D-2/W-M1/getSideOutput）

Status: completed
Targets: `WindowOperator`、`WindowedStream`/`WindowOperatorBuilder`、`TimestampsAndWatermarksOperator`、`WindowedStreamImpl`（或等价）

- Item Types: `Fix`

- [x] G-3：窗口 per-window 状态清理——**三处路径全部接线** `processContext.clear()`：purge 分支（:885-891）、cleanup 分支（:897-907）、**merge-clear 路径（:1721，MergingWindowSet 合并退休的 sourceWindow）**（对齐 Flink clearAllState；实施注意：onTimer 的 cleanup 分支调用前需先 `processContext.window = triggerContext.window`——processContext.window 仅在 emit 路径被赋值，审查 F-10）。测试：带 `ctx.windowState()` 的 ProcessWindowFunction，cleanup/purge/merge-retire 三路径后状态条目均消失（checkpoint 快照不含残留）。
- [x] G-2+09e①：`WindowedStream.sideOutputLateData(OutputTag)` 公共 API（直通既有 builder 孤儿 setter）+ 迟到记录检索 API——**宿主裁定（审查 F-11）**：检索面落 `SingleOutputStreamOperator`（与 Flink 同形，`getSideOutput(OutputTag<X>)`），实施时确认 operator 引用从 WindowedStream 可达，不可达则降级落 WindowedStream 并记录选择。测试：迟到记录进 side output 可检索。
- [x] G-1：`ProcessingTimeSessionWindows`（处理时间 session 合并，复用 MergingWindowSet 管线；gap 语义与事件时间版一致）。测试：PT session 窗口按处理时间间隔合并与触发。
- [x] T-1+D-2：`TimestampsAndWatermarksOperator` 空 if/else-if 死分支与 `elementsSinceLastEmit` 写而不读清理；`processWatermark` 上游 watermark 转发——**决策（审查 F-12，Item Type: Fix+Decision）**：与 Flink 对齐（忽略上游 watermark，只透传 MAX_WATERMARK），理由：上游 watermark 与本算子周期发射双源会产生乱序 watermark 下发，Flink 语义为准；行为变更（上游 watermark 不再透传）以既有 watermark 测试族 + 新增对照测试钉住。
- [x] W-M1：WindowOperator `numLateRecordsDropped` 照抄 CepOperator 的带 tag 作用域注册方案。测试：两并行实例指标互不串数。

Exit Criteria:

- [x] 六个 checklist 项（G-2+09e① 与 T-1+D-2 为合并项）各有聚焦测试且通过；G-3 有"三路径 cleanup 后状态不残留"断言（修复前泄漏复现记录）。
- [x] `nop-stream-runtime`（windowing 测试族）、`nop-stream-cep` 测试全绿。
- [x] owner doc：`window-design.md` 补 PT session 与 late-data side output 公共 API 说明；无则 `No owner-doc update required` 需写明理由。
- [x] `ai-dev/logs/` 已更新。

### Phase 3 - 状态后端低成本项（NEW-A/NEW-B/O(1)/TTL/RocksDB 管控）

Status: completed
Targets: `RescaleStateAssembler`、operator state 重分布（restoreBroadcast）、`KeyGroupAssignment.assignKeyGroupToSubtask`、memory TTL（StateTtlConfig/后端）、RocksDB 后端配置

- Item Types: `Fix`

- [x] NEW-A：`RescaleStateAssembler` 扩容（newParallelism>old）遇旧子任务携带非空 operator state → fail-fast（与 AR-03 缩容对称的响亮失败；1:1 同并行度保持现状）；本项落地即显式裁定（关闭 10 号 Open Question 3 的降档分支）：operator state 重分布生产接线属 successor（backlog 已有），本波次消除静默置空——登记即裁定记录。
- [x] NEW-B：`restoreBroadcast` 改并集语义（全部非空快照并集发所有实例，对照 Flink RoundRobinOperatorStateRepartitioner）。测试：两快照并集恢复。**注明（审查 F-13）**：该路径在 4 参 restoreState(mode,...) 内、生产当前零调用（与 NEW-A 不接线裁定一致）——本项是后端级语义修正，接线前不改变生产行为。
- [x] `assignKeyGroupToSubtask` O(1) 闭式改写——**位精确等价**（当前 start=idx*base+min(idx,rem) 区间的属主映射），全网格属性测试（maxParallelism×parallelism×keyGroup 全组合旧新相等）。
- [x] ST-12 部分：memory 后端 TTL 增加定期清理入口（checkpoint 之外，如注册到现有周期调度点）+ `expiredKeys()` 免拷贝迭代——**实现约束（审查 F-14）**：免拷贝返回 live view 后必须以**迭代器 remove**（或先收集后删除）实现 sweep，防 HashMap 迭代中删除的 CME；改签名需同步唯一消费者 RocksDB sweep（RocksDBKeyedStateBackend:716 边迭代边删除）。RocksDB sidecar 无界增长仍属 backlog 专项（裁定不变）。
- [x] RocksDB 内存管控预设：block cache / WriteBufferManager 总上限 / bloom filter 可配（默认值保守，不破坏既有测试）。

Exit Criteria:

- [x] 各项有聚焦测试；O(1) 改写有全网格等价属性测试；NEW-A 有扩容 fail-fast 测试（修复前静默置空复现记录）。
- [x] `nop-stream-core`、`nop-stream-rocksdb`、`nop-stream-runtime` 测试全绿。
- [x] owner doc：`state-management-design.md` 登记扩容 operator state 裁定与 RocksDB 管控配置面。
- [x] `ai-dev/logs/` 已更新。

### Phase 4 - 并发长尾低成本项（CC-15/CC-10/CC-07/RPC 契约）

Status: completed
Targets: `JdbcLeaderElector`、JobCoordinator commit/部署路径、RPC 接口 javadoc

- Item Types: `Fix`

- [x] CC-15：`JdbcLeaderElector.tryBecomeLeader` 消除硬编码 epoch=1。**机制裁定（审查 F-15：行丢失时"读租约行递增"无值可读，不可行）**：为 epoch 引入**独立于租约行的单调源**——同库持久化 epoch 计数行（独立表/键，永不清理，事务内 read-and-increment 取值后再写租约行）；租约行存在时维持既有 :255 UPDATE 乐观递增。测试：租约行被外部删除后重选 → 新 epoch > 历史最大值（fencing 不回卷）。
- [x] CC-10：commitExecutor 队列设上界（可配）+ 积压打点。**满队拒绝语义（审查 F-16，候选列定 + 选择记录）**：(a) CallerRuns——提交回退调用方线程执行（对 exactly-once 提交链最安全，自然背压）；(b) Abort + typed 异常由调用方转 checkpoint 失败。倾向 (a)（提交通知丢失即丢 commit 语义，CallerRuns 的阻塞代价在中小规模定位内可接受），实施时确认调用方线程非关键路径后定并记录。慢提交不再无界积压。
- [x] CC-07：部署宽限期——deployTask 传输失败的任务在可配宽限期内进入"待重部署"而非永久 benefit-of-the-doubt；宽限期满无 liveness 即触发恢复（消除静默半死盲区）。
- [x] RPC 超时契约文档化：IStreamTaskRpcService 等 RPC 面的超时/重试语义 javadoc（文档项，无行为变更；No new test required: doc-only）。

Exit Criteria:

- [x] CC-15/CC-10/CC-07 各有聚焦测试（租约行删除后 epoch 单调不回卷、队列上界+满队语义、宽限期后触发恢复）。
- [x] `ai-dev/backlog/nop-stream-r5-successors.md` 的 CC-07/CC-10/CC-15/W-M1 对应行标注"已由 plan 369 收口"（防 owner-doc 漂移，审查 F-17）。
- [x] `nop-stream-runtime` 测试全绿。
- [x] owner doc：`failover-design.md` CC-07 宽限期语义；裁定总表中 CC-07/CC-10/CC-15 在 plan 368 表行保持（历史），本 plan 记录提前收口。
- [x] `ai-dev/logs/` 已更新。

### Phase 5 - 文档收口与 Closure

Status: completed
Targets: 本 plan、docs-for-ai、design docs

- Item Types: `Proof`

- [x] connector 冻结政策登记（SourceFunction vs Source 两代政策：legacy 冻结、新连接器一律 Source API——写入 connectors.md）。
- [x] 全模块回归：`cd nop-stream && ../mvnw test -pl nop-stream-core,nop-stream-flow,nop-stream-runtime,nop-stream-cep,nop-stream-rocksdb,nop-stream-connector,nop-stream-connector-jdbc,nop-stream-connector-batch,nop-stream-connector-debezium -am`。
- [x] 独立子 agent closure audit（fresh session）：逐 Phase Exit Criteria + Closure Gates 对照 live repo；evidence 写入 Closure 段。
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出码 0；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` 退出码 0。
- [x] Anti-Hollow：抽查 C3 状态继承链（完成任务的 checkpoint 状态在恢复路径可读）与 G-3 清理链（cleanup 后快照无残留）为运行时连通实现。

Exit Criteria:

- [x] 全模块回归 EXIT=0（`_tmp/r6-final-regression.log`）。
- [x] closure audit evidence 写入 Closure 段（每 Gate PASS/FAIL）。
- [x] 两工具退出码 0。
- [x] `ai-dev/logs/` 收口条目已更新。

## Closure Gates

- [x] 5 项 P2（C1/C2/C3/G-3/NEW-A）+ 本 plan In Scope 低成本项全部修复并带聚焦测试
- [x] 行为变更项修复前可复现（或注明为何不可复现）
- [x] 不存在被静默降级的 in-scope live defect（未采纳项全部归入裁定/follow-up：NEW-C、C-A3、C-A5、连接器管理面、xdef 声明面、owner 决策两项）
- [x] 受影响 owner docs 已同步
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：C3 继承链与 G-3 清理链运行时连通；无空方法体/静默跳过
- [x] `./mvnw compile`（nop-stream 全模块）
- [x] `./mvnw test`（nop-stream 全模块）
- [x] check-doc-links --strict 退出码 0

## Deferred But Adjudicated

### NEW-C remote 边 barrier 无插队（aligned 恒降级，正确性无损）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 需 InputChannel 双队列结构改动（中成本），配置语义名存实亡但正确性无损（降级 unaligned 仍是一致快照）。
- Successor Required: `yes`
- Successor Path: `ai-dev/backlog/nop-stream-r5-successors.md` 追加登记

### C-A3 channel-state rescale descriptor / C-A5 CheckpointFailureReason 枚举

- Classification: `optimization candidate`
- Why Not Blocking Closure: C-A3 中成本（借 Flink InflightDataRescalingDescriptor 思路，等真实跨并行度 unaligned 恢复需求）；C-A5 纯风格。
- Successor Required: `yes`（C-A3）/ `no`（C-A5）
- Successor Path: 同上 backlog

### 09e 连接器管理面（commit 重试协议/文件滚动/CON-11 spill）与 xdef 声明面 9 项

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 独立专项波次（连接器框架演进 / flow 声明面收口），与本波次生命周期端点修复无依赖。
- Successor Required: `yes`
- Successor Path: backlog（既有登记）

### owner 决策两项：stableHash murmur 加扰、Java API 默认 guarantee 降档

- Classification: `watch-only residual`
- Why Not Blocking Closure: 均需 owner 拍板（hash 版本迁移成本 vs 收益；语义声明哲学 vs 易用性），10 号裁定 Open Questions 已列。
- Successor Required: `no`

## Non-Blocking Follow-ups

- ProcessingTimeSessionWindows 若实施中发现需扩 timer 服务结构（超出"复用 MergingWindowSet 管线"），降级回 backlog 并在本 plan 执行记录说明。
- 09b Open Question：RocksDB 侧 IStreamSerializer 通道（与 memory serde 对称）。

## Closure

Status Note: 2026-09-30 收口。Phase 1-5 全部完成并提交（d07811a944 计划 / cb01757ef5 P1 / b738d61212 P2 / 765ada3386 P3 / 24b935140d P4 / 821e322d24 补提交 P1 生产代码 / 收口提交）。5 项 P2（C1/C2/C3/G-3/NEW-A）+ 低成本可采纳 10 项落地；剩余项全部有裁定归属（Deferred 四组）。Flink 2.3 对比裁定的"充分质量"解除条件已满足。
Completed: 2026-09-30

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent closure audit（fresh session，task id agent_5190a5de-58d1-4598-bd7c-c0794710ef0e）
- Audit Session: agent_5190a5de-58d1-4598-bd7c-c0794710ef0e
- Evidence:
  - 逐 Phase Exit Criteria 抽查：Phase 1-4 全部 PASS（C1 豁免/C2 四调用点+短路语义/C3 继承+引用迁移/C4 声明校验/G-3 四路径/G-2 API/G-1/O(1) 全网格/NEW-A/NEW-B/CC-15/CC-10/CC-07/backlog 标注——逐项 live 代码行号在案）
  - 首轮 audit FAIL 4 项（收口工程面）已全部修复：①Phase 1 生产代码补提交 821e322d24（git add pathspec 静默失败，教训已记日志）②checkpoint-design.md §15 ③window-design.md §18 ④daily log plan 369 条目（4 处 logs 勾选随之转真）
  - Anti-Hollow：(a) C3 继承链读码贯通（markTaskCompleted→acknowledgeTask→toCompletedCheckpoint→buildAndMaterializeSegments 段引用迁移→恢复读取）；(b) G-3 清理链贯通（三路径→clearUserWindowState→processContext.clear→userFunction.clear）；(c) scan-hollow-implementations --module nop-stream --severity high = 0 findings EXIT=0
  - 全量回归：`cd nop-stream && ../mvnw test -pl nop-stream-core,nop-stream-flow,nop-stream-runtime,nop-stream-cep,nop-stream-rocksdb,nop-stream-connector,nop-stream-connector-jdbc,nop-stream-connector-batch,nop-stream-connector-debezium -am -q` EXIT=0（`_tmp/r6-final-regression.log`）
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/369-nop-stream-r6-flink-compare-fixes.md --strict` 退出码 0；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
  - Deferred 项分类检查：四组 Deferred 均非 in-scope confirmed P2 live defect（audit 逐条对照 09x 报告原文核实）

Follow-up:

- NEW-C/C-A3 归 backlog；C-A5/owner 决策两项 watch；连接器管理面与 xdef 声明面归既有专项波次——见 Deferred But Adjudicated 与 `ai-dev/backlog/nop-stream-r5-successors.md`
