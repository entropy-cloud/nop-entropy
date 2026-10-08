# 2334 WI10 范围重算与回补（TimeRange 重放 + FILL_HISTORY 作业形态）

> Plan Status: completed
> Last Reviewed: 2026-10-07
> Source: `ai-dev/backlog/nop-stream-multi-engine-comparison-roadmap.md` WI10（M2）+ 对比分析 P1-8（TDengine RECALCULATE/FILL_HISTORY 对标）
> Related: WI9（共用 savepoint/restore 面；其 ReplayableSourceFunction 接口裁定 = 不加方法，本 WI wrapper 无接口耦合）；plan 2328（WI1 launch 面）

## Purpose

交付 TDengine 对标的两个重算作业形态：**RECALCULATE**（时间范围重放：从 REPLAYABLE source 按事件时间范围选择重放，结果经 upsert sink 幂等回写）与 **FILL_HISTORY**（回补：独立 jobId 的历史批量装载作业，完成即终态）。复用 savepoint/restore 与 upsert sink 幂等，不动窗口引擎。

## Current Baseline（研究核实 + 2026-10-07 独立审查修订）

- REPLAYABLE source：CollectionReplayableSource（seek/offset，runtime/source/CollectionReplayableSource.java:22-72）、BatchLoaderSourceFunction（connector-batch；类声明 :63 起，client-side skip 语义 :109-149、seek :212-220）。**注意**：`DirectoryFileSourceFunction`（nop-stream-fraud-example，demo 模块）实现 SourceFunction+CheckpointedSourceFunction 但**不是** ReplayableSourceFunction（无 seek/getCurrentOffset）——不列入 REPLAYABLE 清单，wrapper 将对其 typed 拒绝。
- 时间范围过滤：**无现成原语**（StreamFilterModel 是通用谓词）；`seek(long)` 是 offset 语义无时间含义。
- `TimestampAssigner` 已存在：`io.nop.stream.core.common.eventtime.TimestampAssigner`（core，extractTimestamp(element, recordTimestamp)），wrapper 注入即可，无需新建。
- 作业提交面：env.execute（嵌入式）/ OpsJobManager.submit（REST）/ JobCoordinatorMain launch（无 fromSavepoint 参数）；REST stop 支持 CANCEL/DRAIN/SUSPEND（SUSPEND 已随 WI9/plan 2333 落地）。
- 同 jobId 第二次运行会 auto-restore durable checkpoint（OpsJobManager:163-171）——RECALCULATE 必须用新 jobId/新存储目录，否则恢复 seek 过的 offset 重放为空。
- upsert 幂等：jdbc-2pc dmlMode=UPSERT/UPSERT_DELETE（JdbcTwoPhaseCommitSink.java:107-133，H2 经 MERGE INTO 原生支持，TestJdbcTwoPhaseCommitSinkDml 先例）——同 key 重放=覆盖，幂等成立。**注意**：泛型（非 ChangeEvent）记录走 keyed 模式必须提供 opMapper（非 null 即可，纯重算回写用 "c"）。
- 窗口侧重算无需新机制：恢复窗口状态即重算（研究确认）。
- 测试面：TestSavepointEndToEnd、TestReplayableSourceRecovery:86,143；E2E 三块拼图齐备——TestE2EWindowAggregateRestore（env harness）、TestE2EJdbcTwoPhaseCommitSink（图内 2pc sink）、TestJdbcTwoPhaseCommitSinkDml（H2 UPSERT）。runtime pom 已有 connector-jdbc/connector-batch 测试依赖。

## Goals

- `TimeRangeReplayableSource`（**runtime source 包**——core 无具体 source 实现且可测 batch-loader 包装）：包装既有 `ReplayableSourceFunction`（构造器 `instanceof` 校验，非 REPLAYABLE typed 拒绝），按事件时间范围 [from, to) 过滤重放。**时间提取**：SourceContext.collect 传裸值无时间戳——wrapper 构造器注入 `TimestampAssigner<T>`，拦截 collect **与 collectWithTimestamp 双分支**。**再发射语义（审查钉定）**：collect(e) 分支内范围命中后经 `collectWithTimestamp(e, extractedTs)` 携带提取时间戳再发射（下游事件时间窗口开箱可用）；collectWithTimestamp(e, ts) 分支范围内原样透传。offset 语义保留内层 offset；转发面 = seek/getCurrentOffset/snapshotState/initializeState 全套 + **capability faces（审查钉定）**：`getSourceConsistency()` 转发内层（wrapper 自身不做能力升格声明）、实现 ParallelismCheckable/ConnectivityCheckable 并在内层实现相应接口时转发（防包装后部署期 parallelism=1 防线与连通性探针被静默丢失）。
- RECALCULATE 作业形态：**独立提交（新 jobId——同 jobId 第二次运行会 auto-restore seek 过的 offset 导致重放为空，必须新存储目录）** + TimeRangeReplayableSource 包原 source + 下游 jdbc-2pc dmlMode=UPSERT 幂等回写（泛型结果行需 opMapper="c"）；文档化为 cookbook 形态（复用既有提交面，无新 API）。
- FILL_HISTORY 作业形态：BatchLoaderSourceFunction（批量装载）+ UPSERT sink 的独立作业——loader 空批自然 EOS 终态（DRAIN 是中途手动停止路径，测试按自然 EOS 钉）。
- REST stop 面现状（WI9 已交付，非本 WI 执行项）：支持 CANCEL/DRAIN/SUSPEND；回补作业到 loader 空批自然 EOS 终态。
- docs：user-guide 重算与回补 cookbook 节（两种形态 + 幂等前提 + 边界，含 wrapper × SKIP_GAP 交互声明：WI9 SKIP_GAP 按具体 source 类型分派，wrapper 包装后 typed 拒绝——重算作业为全新有界作业，正常不触发）。

## Non-Goals

- 窗口引擎改动（恢复即重算，无需新机制）；增量 checkpoint 的范围重算（savepoint/重提交面已够）；非 REPLAYABLE source 的历史重放（BatchLoader 是批装载不算重放——如实区分；DirectoryFileSourceFunction 非 REPLAYABLE，wrapper typed 拒绝）。

## Scope

### In Scope

- core/runtime：TimeRangeReplayableSource（包装 + 事件时间过滤 + offset 兼容 + capability 转发）。
- 测试：范围过滤正确性（boundary from/to）/offset checkpoint 往返/RECALCULATE E2E（collection source → 窗口聚合 → upsert sink 幂等回写：同 key 重放两次结果一致）/FILL_HISTORY 形态测试（batch loader → 自然 EOS 终态）/capability 转发钉（包装 batch-loader 时部署期 validateParallelism 仍触发）。
- docs：cookbook 节。

### Out Of Scope

- debezium 按时间位点 seek（其 offset 是 binlog 位点无时间语义——cookbook 如实声明）；窗口引擎改动。

## Execution Plan

### Phase 1 - source 与测试

Status: completed
Targets: core/runtime source、测试、docs

- Item Types: `Feature`

- [x] TimeRangeReplayableSource（wrapper：TimestampAssigner 注入 + collect/collectWithTimestamp 双分支过滤 + 非 REPLAYABLE typed 拒绝 + 全套转发 seek/getCurrentOffset/snapshotState/initializeState + capability faces 转发（consistency/ParallelismCheckable/ConnectivityCheckable））
- [x] 测试（E2E 用新 jobId/新存储目录防 auto-restore 空重放）：边界过滤/offset 往返/RECALCULATE E2E 幂等（同 key 重放两次结果一致）/FILL_HISTORY 自然 EOS 终态/capability 转发钉
- [x] docs cookbook 节（落 nop-stream-user-guide.md「窗口生命周期事件与 maxDelay」同级的重算章节；含 debezium 无时间语义边界、DirectoryFileSourceFunction 非 REPLAYABLE typed 拒绝、wrapper × SKIP_GAP 交互声明）

执行发现（2026-10-07）：

- **有界 source 的 2PC 提交契约（引擎语义，E2E 过程中实证钉定）**：只经「作业终结 MAX_WATERMARK 触发窗口出数」进入 2PC sink 的记录不会被提交——终结出数后无 checkpoint barrier，开放 epoch 永不 preCommit（`pendingCommits` 保持空、表空；**对任意 source 形态成立**，与 wrapper 无关——plain 与 wrapped 源在该形态下同样不提交）。RECALCULATE 有界 source 因此必须：(a) 显式 emitWatermark(≥ 最末窗口 end) 让窗口在运行中出数；(b) 驻留 ≥ 一个 checkpoint interval 让周期 barrier 完成 preCommit+commit。wrapper 对 watermark 仅透传，终结 watermark 是内层 source 的责任（`forwardsTerminalWatermarkFromInnerSource` 单元钉 + `BoundedHistorySource` E2E 载体）。该契约已写入 user-guide cookbook 与 E2E javadoc。
- 泛型（非 ChangeEvent）记录走 jdbc-2pc keyed 模式必须提供 `opMapper`（否则 invoke 期 typed 拒绝）——重算回写用 `"c"`。已落 cookbook。
- wrapper capability 转发的 non-checkable 分支裁定：`ParallelismCheckable` 无约束内层 = no-op（与未包装行为等价）；`ConnectivityCheckable` 无探针契约内层 = typed 拒绝 `ERR_STREAM_CONNECTOR_CAPABILITY_MISMATCH`（假通过违反 No-Silent-No-Op）——`nonCheckableDelegateHasNoFakeProbe` 钉定。
- REST harness 无关；本次 E2E 走嵌入式 env.execute 路径（`CheckpointExecutorFactoryImpl` 注册 + checkpointing enabled 是 2PC sink 的 submit 期 fail-fast 前提——guard 文案「no checkpointing is configured」）。

Exit Criteria:

- [x] `./mvnw test -pl nop-stream/nop-stream-runtime,nop-stream/nop-stream-connector-jdbc -am` 全绿（2026-10-07 实测 EXIT=0；首轮 nop-batch-core TestBatchTaskCheckpoint 单点 flake 复跑自愈，WI1/WI9 日志既有 watch-only 记录一致）
- [x] **端到端验证**：RECALCULATE E2E（同 key 重放两次 → sink 结果一致幂等）（`TestRecalculateBackfillE2E.recalculateReplayIsIdempotentThroughUpsert`——新 jobId/新存储目录、全表逐字节一致；子范围用例钉范围内物化）
- [x] **接线验证**：包装 batch-loader 时 ParallelismCheckable/ConnectivityCheckable 转发在部署/探测路径被真实消费（断言或调用链证据）（`forwardsCapabilityFacesOfCheckableDelegate`——validateParallelism(2) 经 wrapper 触发内层守卫、checkConnection 到达内层；`nonCheckableDelegateHasNoFakeProbe` 钉反向分支；部署期消费面 = GraphExecutionPlan/RemoteGraphExecutionPlanBuilder/StreamConnectivityProber 既有 instanceof 分派，wrapper 实现接口即被消费）
- [x] **无静默跳过**：包装非 REPLAYABLE source = typed 拒绝；to <= from = typed 拒绝（`rejectsNonReplayableDelegateTyped` 错误码+源类名断言、`rejectsEmptyOrInvertedRangeTyped`）
- [x] owner docs 已更新（user-guide「范围重算与回补作业形态」节）
- [x] `ai-dev/logs/` 对应日期条目已更新（`ai-dev/logs/2026/10-07.md` plan 2334 条目）

### Phase 2 - closure audit 与提交

Status: completed
Targets: roadmap WI10、plan 2334

- Item Types: `Proof`

- [x] 独立子 agent closure audit；roadmap WI10 勾选；git commit

Exit Criteria:

- [x] 独立 closure audit 通过且证据写入本 plan Closure 节
- [x] roadmap WI10 已勾选、提交完成

## Closure Gates

- [x] RECALCULATE 幂等 E2E 通过（重放两次结果一致）
- [x] `./mvnw test -pl nop-stream/nop-stream-runtime,nop-stream/nop-stream-connector-jdbc -am` 全绿
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] 独立子 agent closure-audit 已完成并记录证据

## Deferred But Adjudicated

### debezium 按时间位点 seek

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: debezium offset 是 binlog 位点无时间语义，时间位点 seek 需位点和时间戳的映射表（DB 历史表），成本远超首版需求；cookbook 声明用 collection/batch-loader 路径做范围重放
- Successor Required: `no`

## Closure

Status Note: WI10 两作业形态（RECALCULATE 时间范围重放 + FILL_HISTORY 回补）落地并有钉子测试；wrapper 契约（双分支过滤/typed 拒绝/全套转发/capability faces）与三条作业配方硬契约经独立审计七项全 PASS（0 Blocker/0 Major）；「终结出数无 barrier 不提交」声明为 dev-time 对照实验证实的引擎语义（正向配方有 E2E 永久锚点，反向分支记录于执行发现/日志，未强求补测——audit Minor-1 处置）。
Completed: 2026-10-07

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，task agent_a67751dc-984a-4f65-a85f-11203f4854b6），全程只读
- Evidence:
  - 实现语义落地 PASS：TimeRangeReplayableSource.java:79-96（null/非 REPLAYABLE/空 range 三路 typed 拒绝）、:114-136（consistency+四件套转发）、:144-166（capability faces 转发与无探针 typed 拒绝）、:168-192（双分支过滤语义）、:195-212（watermark 等透传）
  - 测试真实覆盖 PASS：TestTimeRangeReplayableSource 9/9 + TestRecalculateBackfillE2E 3/3（surefire Oct 7 23:20 批次 > 源 mtime，报告测的就是当前代码）；connector-jdbc 45 用例全绿（模块零改动）
  - E2E 全链 PASS：env.execute → 真窗口算子 → jdbc-2pc UPSERT → H2 回读断言（2.9s 物化 3 行）；部署面消费核实（GraphExecutionPlan.java:544-550 / RemoteGraphExecutionPlanBuilder.java:365-374 / StreamConnectivityProber.java:45-56 三处 instanceof 分派必命中 wrapper 实现的接口）
  - owner doc PASS：user-guide「范围重算与回补作业形态」节八要素与实现逐条一致
  - 提交契约声明如实标注引擎语义（非 wrapper 行为）：E2E javadoc/plan 执行发现/cookbook 三方同口径
  - `./mvnw test -pl nop-stream/nop-stream-runtime,nop-stream/nop-stream-connector-jdbc -am` EXIT=0（首轮 batch-core flake 复跑自愈）
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2334-wi10-range-recalc-backfill.md --strict` 退出码 0
  - scan-hollow-implementations --severity high（runtime + core）退出码 0
  - Deferred 项分类检查：debezium 时间位点 seek 为 out-of-scope improvement（位点无时间语义，映射表成本超首版需求），非 live defect 降级
  - 提交圈定（audit Minor-4）：仅 WI10 文件（实现 + 两测试 + user-guide 节 + plan + roadmap 勾选 + 10-07.md 的 2334 条目 hunk），sibling 调研流文件不混入

Follow-up:

- null 参数拒绝（TRS:79-84）无用例覆盖——audit Minor-2 登记为观察项（非 plan 钉定清单内）；后续触碰该类时可顺手补
- no remaining plan-owned work
