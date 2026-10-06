# 2324 nop-stream R6 P1：键控恢复/路由族（KR-06/KR-07/KR-08/KR-09）

> Plan Status: draft
> Last Reviewed: 2026-10-06
> Source: ai-dev/audits/2026-10/2026-10-06-0522-deep-audit-nop-stream-quality-r6/（05-keyed-routing-window.md 复核保留 3×P1、04 CC 组联动、00 REG-05）
> Related: ai-dev/plans/2313-nop-stream-r5-routing-ownership-consistency.md、2322-nop-stream-r6-p0-e2e-red-baseline.md（Phase 2 已修 restore 侧 M 回落链的 KR-06 面孔之一）

## Purpose

闭合键控状态恢复的三个 P1 缺陷与一个 P2 守卫缺口：KR-06（rescale 恢复路由生产不可达——isVertexKeyed 依赖 open() 才创建的 keyedStateBackend + remote provision 字段错位，setTargetKeyGroupRange 零调用者，R5 KR-2/WIN-2/4 修复全部休眠）、KR-07（keyed 快照裸键 `"keyed-state"` 后写覆盖：链内 ≥2 keyed 算子每次 checkpoint 丢状态）、KR-08（idle 恢复永不发 WatermarkStatus.ACTIVE，事件时间永久停摆）、KR-09（RescaleStateAssembler 缩容守卫名单漏 process-internal-timers/equi-join-buffer/over-window-buffer 三键，含 REG-05）。

## Current Baseline

- 2322 Phase 2 已修 `resolveMaxParallelism` 回落链读配置后端 M（KR-06 的 restore-M 面孔），但 isVertexKeyed/字段错位/setTargetKeyGroupRange 零调用等问题原样存在（R6 复核独立推导成立：三个 plan 构建点全部早于 restore 早于 open()；provisionStateBackends set `stateBackend` 而 builder 读 `keyedStateBackend`）。
- KR-07：CheckpointBarrierTracker:307-312 裸键写入、:258-262 裸键分支、恢复侧 :577-580 双算子同读；`.process(f1).process(f2)`、cep→process、join→process 普通可达（window 因 ChainingStrategy.NEVER 不可达——复核修正）。
- KR-08：markActive 全仓 main 零调用者；T&WO 内部类与 processElement 共享 idle 旗标；InputGate 侧永久逐出机制成立。
- KR-09：守卫名单恰 5 键（RescaleStateAssembler:137-141），漏 3 键（TimerStateKeys:26、EquiJoinOperator:80、OverWindowOperator:57）；守卫现随 KR-06 休眠，**修复必须先于/随 KR-06 落地**否则当周复现 WIN-2。
- fraud-example S2 家族从未测过真并行度变更恢复（2322 复核确认）；KR-06 是未来任何真 rescale E2E 的先决阻塞项。

## Goals

- restore 入口的 keyed 元数据与路由目标在全部部署形态（LOCAL/REMOTE/spec 重建）真实可达：isVertexKeyed 判定不再依赖 open() 时序；remote provision 字段与 builder 读取字段对齐；setTargetKeyGroupRange 被真实调用。
- keyed 快照键含 vertex 级命名空间（消除后写覆盖）；恢复侧按键匹配。
- idle 解除路径补 WatermarkStatus.ACTIVE（markActive 接线）。
- 缩容守卫名单补齐 3 键并改引常量；javadoc 与行为一致；钉子覆盖三键。
- 新增真并行度变更恢复 E2E（此前 fraud 家族零覆盖）。

## Non-Goals

- 不改 key-group 属主公式本身（R5 KR-1/2/3 已收敛）。
- WIN-05（evictor 侧存储泄漏）等窗口长尾不在本计划（治理批次）。
- 不做 keyGroupOwnership 元数据的 schema 演进（KR-10 P3 观察项）。

## Scope

### In Scope

- `nop-stream-runtime` GraphModelCheckpointExecutor（isVertexKeyed 时序、provision 字段对齐、setTargetKeyGroupRange 接线）。
- `nop-stream-core` CheckpointBarrierTracker（快照键命名空间）+ 恢复侧匹配。
- `nop-stream-core` TimestampsAndWatermarksOperator/WatermarksWithIdleness（ACTIVE 接线）。
- `nop-stream-runtime` RescaleStateAssembler（守卫名单 + 常量引用 + javadoc + 钉子）。
- 新增并行度变更恢复 E2E（fraud-example 或 runtime integration）。
- 2322 留下的 remote TM restore 穿参遗留（restoreDeployedSubtaskFromStorage null 参数）在本计划内闭合。

### Out Of Scope

- WindowOperator 窗口合并语义（WIN-05 归治理）。
- SourceCoordinatorRegistry 静态键（CC-04 归治理批次）。

## Execution Plan

### Phase 1 - KR-09 守卫名单 + KR-06 恢复路由接线

Status: planned
Targets: RescaleStateAssembler、GraphModelCheckpointExecutor、RemoteTaskDeploySupport（字段对齐面）

- Item Types: `Fix`、`Proof`

- [ ] KR-09：守卫名单补 3 键并改引常量；javadoc 修正；TestScaleDownPerKeyStateGuard 扩展三键覆盖
- [ ] KR-06：isVertexKeyed 判定与 restore 目标重接线（keyed 标记来源改为不依赖 open() 时序的 plan 级信息）；remote provision 字段对齐（stateBackend vs keyedStateBackend）
- [ ] setTargetKeyGroupRange 接线至真实恢复路径（grep 0→非 0 main 调用者）
- [ ] remote TM restore 穿参遗留闭合（2322 null 注释处）
- [ ] focused 测试：键控 vertex 缩容恢复走 KeyGroupRange 重分布（非 1:1）；扩容恢复 fail-fast 或正确重分布

Exit Criteria:

- [ ] **端到端验证**：真并行度变更恢复 E2E 绿（keyed 状态按 key-group 正确重分布，输出 exactly-once）
- [ ] **接线验证**：setTargetKeyGroupRange 在恢复链路被运行时调用（计数器/断言证据）
- [ ] 缩容守卫三键钉子变异探针回退必红
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - KR-07 快照键命名空间 + KR-08 ACTIVE 接线

Status: planned
Targets: CheckpointBarrierTracker、恢复侧反序列化匹配、TimestampsAndWatermarksOperator

- Item Types: `Fix`、`Proof`

- [ ] KR-07：keyed 快照键引入 vertex 级命名空间（对齐 JDBC ledger sink_namespace 先例）；恢复侧按键定位；旧裸键快照兼容或显式迁移说明
- [ ] KR-07 钉子：链内双 keyed 算子 checkpoint→恢复双方状态完好
- [ ] KR-08：markActive 在事件路径接线（WatermarksWithIdleness.onEvent → ACTIVE 下行）+ idle→active 转换恢复路径钉子
- [ ] `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime,nop-stream/nop-stream-fraud-example` 全绿

Exit Criteria:

- [ ] 双 keyed 算子链 checkpoint/恢复钉子变异探针回退必红
- [ ] idle 解除后事件时间恢复推进的端到端断言存在且通过
- [ ] owner doc：user-guide 状态/checkpoint 相关描述与 live 行为核对（新增键命名空间如属公开契约须记录）
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] KR-06/KR-07/KR-08/KR-09 四缺陷闭合（变异探针证据）
- [ ] 真并行度变更恢复 E2E 绿（端到端）
- [ ] R5 休眠修复（KR-2/WIN-2/4 守卫）恢复活性确认
- [ ] `./mvnw test -pl <受影响模块>` 全绿
- [ ] R6 summary 对应条目补记修复归属
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：恢复路由接线在 E2E 中真实走过（非仅单测）
- [ ] owner doc 同步（或显式 No owner-doc update required + 理由）
- [ ] 代码规范核对通过

## Deferred But Adjudicated

### WIN-05/WIN-06/KR-10 等窗口长尾

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 不影响本计划四缺陷闭合
- Successor Required: `yes`
- Successor Path: 后续治理批次

## Closure

Status Note: （收口时填写）
Completed: （收口时填写）

Closure Audit Evidence: （收口时由独立子 agent 填写）

Follow-up:

- （收口时填写）
