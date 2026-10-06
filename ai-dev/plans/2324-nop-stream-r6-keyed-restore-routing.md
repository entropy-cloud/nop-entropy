# 2324 nop-stream R6 P1：键控恢复/路由族（KR-06/KR-07/KR-08/KR-09）

> Plan Status: completed
> Last Reviewed: 2026-10-06
> Source: ai-dev/audits/2026-10/2026-10-06-0522-deep-audit-nop-stream-quality-r6/（05-keyed-routing-window.md 复核保留 3×P1、04 CC 组联动、00 REG-05）
> Related: ai-dev/plans/2313-nop-stream-r5-routing-ownership-consistency.md、2322-nop-stream-r6-p0-e2e-red-baseline.md（已修 restore 侧 M 回落链）

## Adjudication（计划审查后的关键裁定）

- **KR-06 走方案 (b)（快照存在性驱动）**：restore 端不复活 keyed 标记——`oldParallelism != newParallelism` 且任一旧快照 `getKeyedStates()` 非空即走 `buildRescaledTaskState`（KeyGroupRange 过滤在 map 层已存在，RescaleStateAssembler:266-281）。理由：零持久化格式变化、零标记耦合（不需要 legacy 裸键回退提前）、ProcessOperator 无条件自建后端造成的"一切 process vertex 变 keyed"副作用消失。remote provision 字段错位（stateBackend vs keyedStateBackend）因此不再影响行为——只修正 RemoteTaskDeploySupport:105-107/:44-49 的虚假 javadoc；`setTargetKeyGroupRange` 接线从 Exit Criteria 撤下（代理指标，map 层过滤已覆盖），backend 层 range 传递留待后续按需。
- **KR-09 必须加前缀感知匹配**：生产唯一 operator-state 写入者 CheckpointBarrierTracker:250 一律写 `operator-{N}-<裸名>` 前缀键，守卫的裸名 equals 对生产键恒 false（TestScaleDownPerKeyStateGuard:53 塞裸键构造快照——测试绿但空心）。修复范围含前缀剥离/后缀匹配 + **生产形状（带前缀）fixture 的变异探针**。
- **KR-07 改述为链内 operator 级前缀**：TaskStateSnapshot 本就按 (vertexId, taskIndex) 一份，无跨 vertex 碰撞；真实碰撞是链内 per-operator。修复 = tracker 侧对 `getKeyedStates()` 非空的算子无条件派生 `operator-{i}-keyed`（与 operator-state 命名空间化 :247-251 对称），恢复侧先试新前缀、miss 回退裸键（旧 epoch 兼容）。因 KR-06 走 (b)，格式变化完全封装在本 Phase。
- **KR-08 顺序敏感**：必须同时移除 TimestampsAndWatermarksOperator:98 的直接 `idle=false` 翻转（改走 markActive）——否则 :183-188 的 `if (idle)` 转换守卫看不到 true、不发 ACTIVE。恢复路径**不**加发射点（open():74 重置 idle、下游 InputGate 新建）；缺陷纯粹是活体 idle→resume 转换。`markAsTemporarilyIdle`（StreamSourceOperator:266-268，零生产调用者）out of scope。
- **E2E 落点钉死**：`nop-stream-runtime` integration 包（TestChannelStateRescaleE2E harness 先例），真跑 `executeWithCheckpoint` 在 P=2 产 checkpoint、以 P=1 重建图恢复；fraud-example 不适合（S2 有 2PC sink，P 变更被 D1 typed-reject）。

## Purpose

闭合键控状态恢复的三个 P1 缺陷与一个 P2 守卫缺口：KR-06（rescale 恢复路由生产不可达：键控缩容恢复静默 1:1 错置/扩容报通用错误，R5 KR-2/WIN-2/4 修复休眠）、KR-07（链内多 keyed 算子 keyed 快照裸键后写覆盖）、KR-08（idle 解除永不发 WatermarkStatus.ACTIVE，事件时间永久停摆）、KR-09（缩容守卫名单漏 3 键且对生产前缀键恒不匹配）。

## Current Baseline

- 2322 已修 restore-M 回落链（KR-06 的一个面孔）；本计划修其余面。
- KR-06：isVertexKeyed（GraphModelCheckpointExecutor:1534-1543）读 `m.hasKeyedState()`；CheckpointPlanBuilder:163-168 读 `getKeyedStateBackend()`（open() 前恒 null）；缩容现走 :1377-1379 裸 1:1 `stateLookup.lookup`；`buildRescaledTaskState`（RescaleStateAssembler:266-281）已具备按 KeyGroupRange 的 map 层过滤。
- KR-07：裸键源头 AbstractStreamOperator:307-312；tracker migrateOperatorStates:253-262（prefix 分支因 hasKeyedState 门死）；恢复侧 buildSnapshotFromTaskState:557-583 双算子同读裸键；canChain 8 规则无 keyed 约束——process×2/cep→process/join→process 可达。
- KR-08：OperatorWatermarkOutput.markActive:183-188 存在但零调用者；WatermarksWithIdleness.onEvent:63-67 无 markActive；InputGate channelIdle 复位仅 :616/:1237 两处且由 ACTIVE 触发。
- KR-09：守卫名单 5 键（RescaleStateAssembler:137-141）；漏 3 键：process-internal-timers（TimerStateKeys:26 → ProcessOperator:122）、equi-join-buffer（EquiJoinOperator:80 private）、over-window-buffer（OverWindowOperator:57 private）；javadoc :119-124 与行为矛盾。
- 2322 遗留：restoreDeployedSubtaskFromStorage:1187/:1219 传 null configuredBackend（注释归属本计划）；TaskDeploymentDescriptor.getMaxParallelism()（:108-123）在调用点可用。
- 现有 E2E 覆盖缺口：TestMaxParallelismReshardMigrationE2E 是离线迁移（不走活恢复路径）；TestS2OfflineReshardE2E 作业 P 恒 1；TestS2RecoveryAndRescaleE2E javadoc 明示不含 restore-time P 变更——真并行度变更恢复零覆盖。

## Goals

- 键控 vertex 并行度变更恢复真实可用：缩容按 KeyGroupRange 重分布（每 key 恰恢复一次、落在属主 subtask）；扩容按算子形态分 case（带 operator states → 既有 typed fail-fast 保持；纯 keyed → 正确重分布）。
- 链内多 keyed 算子的 keyed 快照互不覆盖；旧裸键格式恢复兼容。
- idle→resume 转换发出 WatermarkStatus.ACTIVE，下游解除逐出。
- 缩容守卫对生产形状键（带 `operator-{N}-` 前缀）真实生效，名单补齐 3 键且引用公共常量。
- remote TM restore 的 M 回落穿参遗留闭合。

## Non-Goals

- 不改 key-group 属主公式（R5 KR-1/2/3 已收敛）。
- 不复活 keyed 标记/isVertexKeyed（方案 (b) 裁定）；不迁移 checkpoint 持久化格式（KR-07 新键带裸键回退）。
- WIN-05/WIN-06/KR-10 窗口长尾归治理批次。
- 不给 StreamSourceOperator.markAsTemporarilyIdle 接生产调用者（零调用现状）。

## Scope

### In Scope

- `nop-stream-runtime` GraphModelCheckpointExecutor（rescale 检测改快照存在性驱动；remote 穿参遗留）、RescaleStateAssembler（守卫前缀感知 + 名单 + javadoc）、RemoteTaskDeploySupport（javadoc 修正）。
- `nop-stream-core` CheckpointBarrierTracker（keyed 快照 operator 级前缀）、恢复侧 buildSnapshotFromTaskState（前缀优先 + 裸键回退）、AbstractStreamOperator（如需辅助）、TimestampsAndWatermarksOperator/OperatorWatermarkOutput（KR-08）、EquiJoinOperator/OverWindowOperator（BUFFER_STATE 常量改 public 或公共归宿）、TimerStateKeys。
- 新增 runtime integration E2E：真并行度变更（缩容 + 扩容）恢复。
- 各修复面钉子（生产形状 fixture）。

### Out Of Scope

- WindowOperator 窗口合并语义、SourceCoordinatorRegistry 静态键（治理批次）。
- fraud-example fixture 手术（2PC sink D1 约束）。

## Execution Plan

### Phase 1 - KR-06 快照存在性驱动 rescale + KR-09 守卫修复 + remote 穿参遗留

Status: completed
Targets: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java`、RescaleStateAssembler.java、RemoteTaskDeploySupport.java、`nop-stream/nop-stream-core` TimerStateKeys、EquiJoinOperator、OverWindowOperator（常量归宿）

- Item Types: `Fix`、`Proof`

- [x] KR-06：restoreTaskStatesFromSource 缩容分支改为快照存在性驱动——`oldP != newP` 且旧快照任一 `getKeyedStates()` 非空 → buildRescaledTaskState（KeyGroupRange 重分布）；否则保持既有 1:1/报错路径
- [x] KR-06 扩容 case 钉死：带 operator states 的 keyed vertex 扩容 → 既有 `assertNoOperatorStateOnScaleUp`（KR-09 修复后对生产键生效）typed fail-fast；纯 keyed vertex 扩容 → 按新范围正确重分布（测试断言）
- [x] KR-09：常量归宿裁定并落地（EquiJoinOperator/OverWindowOperator 的 BUFFER_STATE 改 public 或集中到公共常量类；守卫引用 TimerStateKeys.PROCESS_INTERNAL_TIMERS + 两个 BUFFER 常量）；守卫匹配改前缀感知（剥离 `operator-{i}-` 前缀后比对，或按 mapping opStateKey 派生）；javadoc :119-124 修正
- [x] KR-09 钉子：**生产形状 fixture（`operator-2-internal-timers` 等带前缀键）** 的缩容拒绝/放行双向断言；变异探针（回退名单/回退前缀匹配必红）；TestScaleDownPerKeyStateGuard 既有裸键用例保留（向后兼容面）
- [x] remote 穿参遗留：restoreDeployedSubtaskFromStorage 两个 null 处改传 `TaskDeploymentDescriptor.getMaxParallelism()`（resolveMaxParallelism 增加 int 回落重载）
- [x] RemoteTaskDeploySupport javadoc 修正（provisionStateBackends 的虚假声明）
- [x] 新增 E2E（runtime integration 包，复用 TestChannelStateRescaleE2E harness 模式）：真 `executeWithCheckpoint` P=2 产 checkpoint → P=1 重建图恢复 → 每 key 恰恢复一次且落在 key-group 属主 subtask；扩容 case 按算子形态分断言
- [x] `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime,nop-stream/nop-stream-rocksdb` 全绿

Exit Criteria:

- [x] **端到端验证**：缩容恢复 E2E 绿——P=2 checkpoint 经 P=1 恢复后每 key 数据恰好一次、输出正确
- [x] **接线验证**：E2E 断言 key 落在 KeyGroupRange 属主 subtask（结果导向，非 setTargetKeyGroupRange 代理指标）
- [x] KR-09 生产形状钉子变异探针回退必红；裸键 legacy 用例保持
- [x] 扩容双 case 断言存在（typed fail-fast / 纯 keyed 重分布）
- [x] remote 穿参遗留闭合（grep 两处 null 已消）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - KR-07 链内 operator 级前缀 + KR-08 ACTIVE 接线

Status: completed
Targets: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/CheckpointBarrierTracker.java`、buildSnapshotFromTaskState 所在恢复侧、AbstractStreamOperator、TimestampsAndWatermarksOperator、OperatorWatermarkOutput

- Item Types: `Fix`、`Proof`

- [x] KR-07：tracker migrateOperatorStates——`getKeyedStates()` 非空时无条件派生 `getOperatorStateKey(i) + "-keyed"` 写入（与 operator-state 命名空间化对称，去掉 hasKeyedState 死门）
- [x] KR-07 恢复侧：buildSnapshotFromTaskState 先按 `operator-{i}-keyed` 前缀匹配，miss 回退裸键（旧 epoch 兼容）；mixed-format（前缀+裸键并存）合并行为写明（升级路径：新格式优先）
- [x] KR-07 钉子：链内双 keyed 算子（process→process）checkpoint→恢复双方 keyed 状态完好（变异探针：回退前缀派生必红——后写覆盖前写）
- [x] KR-08：processElement:98 直接翻转改走 markActive（保留转换守卫语义）；WatermarksWithIdleness.onEvent 接 markActive；恢复路径不加发射点（open 重置语义不变）
- [x] KR-08 钉子：idle→resume 端到端——idle 后事件到达 → 下游收到 WatermarkStatus.ACTIVE、事件时间恢复推进（变异探针：回退 ：98 移除/onEvent 接线必红）
- [x] `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime,nop-stream/nop-stream-fraud-example` 全绿

Exit Criteria:

- [x] 双 keyed 算子链 checkpoint/恢复钉子变异探针回退必红
- [x] idle→resume 端到端断言存在且通过（ACTIVE 下行 + 事件时间恢复）
- [x] 旧裸键格式恢复回退路径有测试覆盖（legacy epoch 兼容）
- [x] owner doc：user-guide checkpoint/idle 相关描述与 live 行为核对（键前缀如属公开契约须记录；否则显式 No owner-doc update required + 理由）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] KR-06/KR-07/KR-08/KR-09 四缺陷闭合（变异探针证据，生产形状 fixture）
- [x] 缩容恢复 E2E 绿（端到端 + 属主断言）；扩容双 case 断言
- [x] R5 休眠修复（WIN-2/4 守卫族）经 KR-09 前缀感知恢复活性确认
- [x] `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime,nop-stream/nop-stream-rocksdb,nop-stream/nop-stream-fraud-example` 全绿
- [x] R6 summary 对应条目补记修复归属
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：rescale 检测在真实 executeWithCheckpoint 管线中被验证（E2E 非 mock）；KR-09 守卫在生产形状键上被验证
- [x] owner doc 同步裁定：**No owner-doc update required**——keyed 快照键前缀为持久化格式内部细节（带旧格式回退，非公开 API）；idle→resume 行为向 AR-9 既有「idleness 必须跨任务边界」契约收敛；user-guide 无相应声明段落需改
- [x] 代码规范核对通过

## Deferred But Adjudicated

### WIN-05/WIN-06/KR-10 等窗口长尾

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 不影响本计划四缺陷闭合
- Successor Required: `yes`
- Successor Path: 后续治理批次

### backend 层 setTargetKeyGroupRange 冗余保险接线

- Classification: `optimization candidate`
- Why Not Blocking Closure: map 层 KeyGroupRangeRestoreFilter 已承担重分布过滤（RescaleStateAssembler:266-281）；backend 层传递属纵深防御，无行为差异
- Successor Required: `no`

## Closure

Status Note: KR-06（快照存在性驱动 rescale：缩容 KeyGroupRange 重分布 E2E 每 key 恰一次、扩容纯 keyed 正确重分布/带辅助状态 typed fail-fast）、KR-07（链内 keyed 快照 operator 级前缀 + legacy 裸键回退，真实 tracker 驱动钉子）、KR-08（idle→resume ACTIVE 双侧接线 + 转换守卫防 spam）、KR-09（前缀感知守卫使 R5 WIN-2/4 休眠守卫在生产形状键上恢复活性）四缺陷全部闭合。core 1691/0、runtime 1246/0、rocksdb 137/0、fraud-example 120/0（skipped 均为门控）。独立审计 APPROVE WITH CONDITIONS 的两项整改已完成：(1) KR-06 检测行变异敏感化——E2E fixture 改生产形状（backend 移至 open()、staging 改 KR-07 tracker 前缀格式、config 显式后端 M=16），实测探针（注释检测行→3 测试全红且失败形态与预测一致：缩容静默丢 subtask-1 键、扩容 1:1 lookup 报缺位；恢复→全绿）；(2) scale-up 属主断言补齐（每 key 按 KeyGroupAssignment 落属主 subtask 逐 key 断言，替换死代码循环）。

Completed: 2026-10-06

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，agent_32edcdb0-cc30-4d69-9db6-59b06056a2c9）
- Audit Session: agent_32edcdb0-cc30-4d69-9db6-59b06056a2c9
- Evidence:
  - Phase 1 Exit Criteria 6 条：5 PASS + 1 PARTIAL（属主断言死代码）→已修（逐 key 属主断言）
  - Phase 2 Exit Criteria 5 条：全 PASS（KR-08"端到端"实为 operator/generator 级，下游 ACTIVE 复活为既有已测行为——偏差已记录）
  - Closure Gates 9 条：8 PASS + Gate 1 KR-06 探针缺口→已修（生产形状 fixture + 实测探针红/绿闭环，见 _tmp/probe-red3.log 与恢复后运行）
  - 变异探针：KR-07 tracker/恢复双侧、KR-08 双接线、KR-09 生产形状 3 钉均独立推演红；KR-06 检测行经 fixture 生产形状化后实测红
  - Anti-Hollow：executeWithSavepoint→restoreFromSavepointPath→restoreTaskStatesFromSource→buildRescaledTaskState 全链无 mock；KR-09 守卫链 buildRescaledTaskState:242→assertNoOperatorStateOnScaleUp:165 成立
  - `node ai-dev/tools/check-plan-checklist.mjs --strict` 审计时代复跑 exit 0；回填后主 agent 复跑 exit 0
  - Deferred 项分类检查：无 in-scope live defect 被降级；backend 层 range 接线为 optimization candidate
  - 条件整改：Major-1/Major-2 完成；m1 注释归属修正（aux E2E 拒绝源=通用 scale-up 分支，KR-09 前缀匹配钉在 TestScaleDownPerKeyStateGuard）；m2 javadoc 残留与 m5 记录为非阻塞
  - 提交卫生：按文件范围切分，剔除 Debezium WI9 无关改动（审计 m3）

Follow-up:

- backend 层 setTargetKeyGroupRange 冗余保险接线（Deferred 已裁定 optimization candidate）
- R5 WIN-2/4 的 Phase 2（key-group 重分布落地后解除缩容 fail-fast）归后续治理批次
- RescaleStateAssembler 被内插方法孤立的 R5 javadoc 块归位（审计 m2，非阻塞）
