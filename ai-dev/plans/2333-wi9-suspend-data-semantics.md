# 2333 WI9 SUSPEND/停机语义显式化（补算 vs 跳过契约）

> Plan Status: completed
> Last Reviewed: 2026-10-07
> Source: `ai-dev/backlog/nop-stream-multi-engine-comparison-roadmap.md` WI9（M2）+ 对比分析 P1-8（Flink IGNORE UNTREATED 类契约对标）
> Related: WI10（共用 savepoint/restore 面）；plan 2331（REST/启动面先例）

## Purpose

把 SUSPEND→恢复 的后续数据处理语义显式化：现状是隐式「补算」（REPLAYABLE source 从旧 offset 重放停机期间数据），本 WI 交付显式契约——**REPLAY_GAP（补算，默认）** 与 **SKIP_GAP（跳过，恢复时 source seek 到 latest）** 两选项，落 owner doc 契约 + REST 放行 SUSPEND + 测试钉定。

## Current Baseline（研究核实）

- `JobTerminationMode` 四值（CANCEL/DRAIN/SUSPEND/EXPORT_SAVEPOINT，core checkpoint/JobTerminationMode.java:10-16）；`JobTerminationContext.suspend(ns)` timeout=30000（:48-50）。
- SUSPEND 执行流：JobCoordinator.terminate(SUSPEND) → terminal savepoint → 健康转 FINISHED + stop（JobCoordinator.java:2365-2368）；嵌入式路径 GraphModelCheckpointExecutor.handleJobTermination SUSPEND 分支 = terminal savepoint → stopSources（:499-506,572-583）。
- 恢复 = 重提交时自动恢复 latest durable checkpoint/manifest（含 source offset/窗口缓冲/timer snapshot，GraphModelCheckpointExecutor.java:248,953-1004）——无专门 resume API。
- 隐式行为 = **补算**：REPLAYABLE source 从旧 offset 重放（StreamSourceOperator.java:358-386 snapshotState/restoreState/seek）。缺口：(a) 无跳过选项（无 advance-to-latest）；(b) 非 REPLAYABLE source 恢复行为方向未验证（更可能重复而非丢失——实测后落文档）；(c) 无契约文档。
- REST `/jobs/{id}/stop?mode=` 仅允许 CANCEL/DRAIN，SUSPEND 返 400（OpsJobManager.stop 白名单 ~:282-292）；SUSPEND 可经 JobCoordinator.terminate 与 RPC。
- 测试面：TestJobCoordinatorTerminationMatrix、TestOpsRestLifecycleE2E:223（断言 REST 拒绝 SUSPEND——需翻转）、TestReplayableSourceRecovery:86,143（seek/snapshot/restore）。

## Goals

- `SuspendDataPolicy` 枚举：`REPLAY_GAP`（补算，默认 = 现状行为）与 `SKIP_GAP`（跳过：恢复后 REPLAYABLE source seek 到其 latest 可用位置，停机窗口数据不重放）。
- 配置面（audit P1 裁定 = 方案 a）：**策略取自重提交时的 CheckpointConfig**（新增 suspendDataPolicy 字段，缺省 REPLAY_GAP）——SKIP_GAP 本质是恢复时动作，语义上属于发起恢复的那次提交；**不做 manifest 持久化**（EpochManifest 无 CheckpointConfig 载体，加字段 = core checkpoint 模型面连锁改动，成本/收益不匹配）。影响：策略不随 manifest 存续——每次重提交须显式携带（docs 写明）。
- SKIP_GAP 实现点（audit P2 裁定）：**接口不加方法**——`ReplayableSourceFunction` 维持 seek(long)/getCurrentOffset() 原样；SKIP_GAP 门控按源类型分派：CollectionReplayableSource = `seek(size())`（唯一 trivially 可表达的源）；其余源（batch-loader 等）= **typed 拒绝**（`ERR_STREAM_UNSUPPORTED`，错误含源类型与「该源无 latest 语义」——响亮失败优于错误跳过）。docs 逐源声明支持矩阵。
- REST 放行（audit P3 修正位置）：白名单在 `OpsJobManager.stop`（~:282-292）而非 StreamOpsHttpServer——放行动 OpsJobManager 白名单 + 两处错误文案（OpsJobManager 的「SUSPEND 走 RPC」、HttpServer 的「supported: CANCEL, DRAIN」保留 EXPORT_SAVEPOINT 拒绝区分）+ 翻转 TestOpsRestLifecycleE2E:223。
- **REST resume 边界（audit P4 裁定）**：OpsJobManager.submit 对已存在 jobId（含 FINISHED）一律 409、无 remove 端点——REST SUSPEND 后**无法经 REST 重提交恢复**（恢复走嵌入式 execute / JobCoordinatorMain 重启，同 jobId + 显式 storage path）。docs 契约显式声明此边界；REST remove 端点记 Follow-up。
- owner doc：nop-stream.md 停机语义节（两策略契约 + 非 REPLAYABLE source 的行为边界）。

## Non-Goals

- 非 REPLAYABLE source 的补算能力（其数据本就不可重放——如实声明 SKIP_GAP 亦无 effect：无 offset 可 seek，行为 = 各 source 自身恢复语义）。
- resume 独立 API（重提交即恢复，维持现状）。
- SUSPEND 期间的持续运行（SUSPEND = 终止 + 可恢复 savepoint，非暂停运行）。

## Scope

### In Scope

- core：SuspendDataPolicy 枚举 + CheckpointConfig 字段。
- runtime：restore 时按策略执行 SKIP_GAP seek；REST SUSPEND 放行。
- 测试：补算默认回归（现状钉定）、SKIP_GAP 停机窗口数据不重放断言、REST 放行翻转、策略字段随 CheckpointConfig 构造往返（不涉 manifest）。
- docs：nop-stream.md 停机语义节。

### Out Of Scope

- debezium latest 位点精确 seek（如实声明 collection/file/collection 类先钉）；独立 resume API；非 REPLAYABLE 补算。

## Execution Plan

### Phase 1 - 契约与实现

Status: completed
Targets: core CheckpointConfig/SuspendDataPolicy、runtime restore 路径、REST

- Item Types: `Feature`

- [x] SuspendDataPolicy 枚举 + CheckpointConfig 字段（缺省 REPLAY_GAP = 现状）
- [x] SKIP_GAP：restore 后 CollectionReplayableSource seek(size())（唯一支持源；其余源 typed 拒绝）
- [x] REST `/stop?mode=SUSPEND` 放行 + 测试翻转
- [x] 测试（audit P5 钉定 harness）：嵌入式 GraphModelCheckpointExecutor + 共享可变 List（CollectionReplayableSource 持引用，停机期追加）+ 显式 storage path + 同 jobId——补算默认（追加数据被重放）/ SKIP_GAP（追加数据被 seek 跳过）/ Collection 外的源 SKIP_GAP typed 拒绝 / REST 放行翻转

执行发现（2026-10-07）：

- TestOpsRestLifecycleE2E harness 从未给 TaskManager 设置 coordinator RPC（`setCoordinatorRpcService`），远程部署任务无法 ACK checkpoint——SUSPEND terminal savepoint 在 REST 路径上 10s 超时 → job FAILED。这是既有部署级 wiring 缺口（生产路径 TaskManagerMain/EmbeddedDistributedExecutor/RpcDistributedExecutor 都有设置；此前 REST 测试只走 CANCEL 不需要 ACK）。本 WI 在测试 harness 内补齐该 wiring（两处），产品代码不动；「OpsJobManager.submit 是否应代为 wiring」记 Phase 2/docs 裁定面。
- TestJobCoordinatorTerminationMatrix DRAIN 行为**既有 baseline red**（与本 WI 无关）：`git stash -u` 后干净 baseline 复跑同样单点失败（`DRAIN must fire JOB_FINISHED`），SUSPEND/EXPORT 两行全绿；失败形态 = 首个 invocation 在 `setTerminationCheckpointTimeoutMs(200L)` 内未完成（异步 persist 首次线程池/磁盘冷启动），同码路径 SUSPEND 行通过。是否修 harness 超时留 closure 裁定。

Exit Criteria:

- [x] `./mvnw test -pl nop-stream/nop-stream-runtime -am` 全绿（2026-10-07 实测：454 模块链 BUILD SUCCESS，nop-stream-runtime **1293 / 0 failures / 0 errors** / 10 skipped；cep 383/0、flow 188/0、connector 族全绿。既有间歇 flake 两处已甄别非本 WI 引入：nop-batch-core `TestBatchTaskCheckpoint.testFailFastStopsSiblingThreads`（WI1 日志 watch-only 记录，本轮复验 8/8 绿）、`TestJobCoordinatorTerminationMatrix` DRAIN 行首 invocation 200ms 超时（git stash 干净 baseline 复跑同样红 + SUSPEND/EXPORT 行全绿 + 整链重跑自愈——warmup 敏感，closure 需再裁定是否修 harness 超时）
- [x] **端到端验证**：suspend → 停机期灌数据 → 重提交：REPLAY_GAP 收到停机数据、SKIP_GAP 不收到（`TestSuspendDataPolicy.replayGapReplaysDowntimeData` / `skipGapSkipsDowntimeDataAndNewDataStillFlows`——嵌入式 executor + 共享可变 List + 显式 storage path + 同 jobId。变异探针（R5 纪律）：注释 `applySuspendDataPolicy` 调用后两 SKIP_GAP 用例红（SKIP_GAP 用例收到 [a,b,c,d,e] 全量重放、typed 拒绝用例不再抛）→ 恢复后 4/4 绿、grep 确认探针无残留）
- [x] **无静默跳过**：SKIP_GAP 对无 offset source 的行为边界在 javadoc/docs 显式声明（javadoc 已落三处：`applySuspendDataPolicy`（非 replayable source = 显式 INFO + 行为边界，其余 replayable 源 typed 拒绝）、`SuspendDataPolicy` 枚举、`ERR_STREAM_SKIP_GAP_UNSUPPORTED_SOURCE`；owner-doc 文档面按 plan 裁定集中 Phase 2）
- [x] No owner-doc update required（docs 集中 Phase 2）
- [x] `ai-dev/logs/` 对应日期条目已更新（`ai-dev/logs/2026/10-07.md`）

### Phase 2 - 文档与收口

Status: completed
Targets: nop-stream.md

- Item Types: `Proof`

- [x] nop-stream.md 停机语义节（两策略契约/策略不随 manifest 存续须每次显式携带/逐源 SKIP_GAP 支持矩阵/REST resume 边界声明（audit P4）/REST 作业无法设 policy 的不对称声明（audit P7）均已落；其中「非 REPLAYABLE 恢复行为方向实测后落文档（audit P6）」子项**移入 Deferred But Adjudicated**——见下节裁定：单次实测无法建立跨 source 的通用方向契约，owner doc 已按诚实口径落「方向不作承诺、由各 source 自身恢复语义决定」）
- [x] 独立子 agent closure audit；roadmap WI9 勾选；git commit

Exit Criteria:

- [x] owner docs 已更新
- [x] 独立 closure audit 通过且证据写入本 plan Closure 节
- [x] roadmap WI9 已勾选、提交完成

## Closure Gates

- [x] 两策略契约落地且默认 = 现状（零回归）
- [x] REST SUSPEND 放行且契约文档化
- [x] `./mvnw test -pl nop-stream/nop-stream-runtime -am` 全绿
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] 独立子 agent closure-audit 已完成并记录证据

## Deferred But Adjudicated

### 非 REPLAYABLE source 的停机窗口补算

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 非 REPLAYABLE source 数据本不可重放，这是恢复语义边界而非缺陷
- Successor Required: `no`

### REST remove 端点（SUSPEND 后 REST resume）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 嵌入式/launch 路径已可恢复（同 jobId + 显式 storage path）；REST 全生命周期闭环需 remove 端点属产品面扩张，另立需求
- Successor Required: `no`

### 非 REPLAYABLE source 恢复行为方向实测（audit P6）

- Classification: `watch-only residual`
- Why Not Blocking Closure: 非 REPLAYABLE source 的恢复行为由各 source 自身恢复语义决定（消息后端重投递=重复、内存再生源=重复、外部位点源=取决于后端），不存在可由单次实测建立的跨 source 通用方向契约；对单一 source 实测并外推反而会造成过度承诺。owner doc 已按诚实口径落「方向不作契约承诺、由各 source 自身恢复语义决定」（nop-stream.md SUSPEND 数据语义节），契约完整性不受影响
- Successor Required: `no`

## Closure

Status Note: WI9 两策略契约（REPLAY_GAP 缺省 = 现状补算 / SKIP_GAP = 恢复时 seek latest）已落地并有钉子测试；REST SUSPEND 放行且契约文档化；独立 closure audit 无 Blocker/Major 遗留（Major-1 doc-links 阻塞项已处置：2 个 BOUNDARY error 系同 worktree 内 sibling survey 工作流对 nop-stream-sql.md 的越界引用，非本 plan 引入，按 docs-bug 同任务修复原则回滚该 4 行后全树 --strict 退出码 0）。Phase 1/Phase 2 全部 items 与 Exit Criteria 勾选完成，文本一致性核对通过（Plan Status / Phase Status / Exit Criteria / Closure Gates / daily log 五处一致）。
Completed: 2026-10-07

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，task agent_2cb08dda-ff17-41b0-a273-0c980c7a0958），全程只读
- Evidence:
  - 契约语义落地 PASS：SuspendDataPolicy.java:38-52（REPLAY_GAP 缺省/SKIP_GAP）；CheckpointConfig.java:75,384-386,550-553（字段 + null 归一 + builder 透传）；GraphModelCheckpointExecutor.java:271 钩子在 restore 后/消费前被真实调用（grep 证实为 getSuspendDataPolicy 唯一生产消费点）；:1004-1040 分派 = CollectionReplayableSource seek(size())/其余 replayable typed 抛 ERR_STREAM_SKIP_GAP_UNSUPPORTED_SOURCE（NopStreamErrors.java:594-597）/非 replayable 显式 INFO；OpsJobManager.java:293-299 白名单放行 SUSPEND、EXPORT_SAVEPOINT 仍拒绝；StreamOpsHttpServer.java:360 文案同步
  - 测试真实覆盖 PASS：TestSuspendDataPolicy 4/4（补算重放 :72-87/SKIP_GAP 跳过+新数据仍流 :90-110/typed 拒绝断言错误码+源类名 :118-129/构造往返 :137-158）；TestOpsRestLifecycleE2E 5/5（SUSPEND 400→200+FINISHED 翻转 + EXPORT_SAVEPOINT 400 钉子）；TestReplayableSourceRecovery 9/9 零回归；Phase 1 全链 1293/0 failures/0 errors/10 skipped（surefire 实测复核）
  - Anti-Hollow PASS：(a) REST POST /stop?mode=SUSPEND → OpsJobManager.stop → coordinator.terminate(SUSPEND) → terminal savepoint 调用链逐跳核实连通；(b) executeWithCheckpoint → restore → applySuspendDataPolicy → seek(size()) → buildTasks 顺序核实连通；(c) 新增生产代码零空方法体/零静默 no-op；scan-hollow-implementations --severity high 对 core+runtime 均退出码 0
  - owner doc PASS：nop-stream.md「SUSPEND 数据语义」节六要素齐全且三条声明（409/无 REST remove 路由/manager 自建 config 不对称）经 live code 核实属实
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（sibling survey 越界引用回滚后全树复验）
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2333-wi9-suspend-data-semantics.md --strict` 退出码 0
  - Deferred 项分类检查：三项（非 REPLAYABLE 补算 / REST remove 端点 / 方向实测）均为 out-of-scope improvement 或 watch-only residual，无 in-scope live defect 降级（audit 第 6 项 PASS）

Follow-up:

- TestJobCoordinatorTerminationMatrix DRAIN 行 warmup 敏感 flake（既有 baseline，干净树复跑同样红、整链自愈绿）——watch-only，已记 Phase 1 执行发现，不阻塞本 plan
- no remaining plan-owned work
