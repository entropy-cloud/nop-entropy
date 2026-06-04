# 112 nop-stream R12/R13 Residual Audit Remediation

> Plan Status: in progress
> Last Reviewed: 2026-06-04
> Source: `ai-dev/audits/2026-05-30-adversarial-review-nop-stream-r13/01-open-findings.md` + `ai-dev/audits/2026-05-30-adversarial-review-nop-stream-r12/01-open-findings.md`, 经 live repo baseline 验证 (ses_16d5ca866ffe1218IES5DTYzsS)
> Related: 103-nop-stream-2026-06-02-audits-remediation (completed), 102-nop-stream-remaining-audit-findings-remediation (completed)

## Purpose

修复 R12/R13 对抗性审查中经 live repo 验证仍然存在的 2 个 P2 发现，将 nop-stream 审计发现收口到"所有已知发现已处理"状态。

## Current Baseline

经独立子 agent live repo 验证（ses_16d5ca866ffe1218IES5DTYzsS），R12 的 8 个发现和 R13 的 17 个发现中，23 个已修复（Plan 102 修复 3 个，Plan 103 修复 20 个），仅剩 2 个未修复：

| 编号 | 严重程度 | 文件 | 摘要 |
|------|---------|------|------|
| R12-AR-7 | P2 | `nop-stream-cep/.../operator/CepOperator.java:161` | `currentWatermark` 为 transient，checkpoint 恢复后丢失 watermark 进度。`open()` 行 191 已将初始值设为 `Long.MIN_VALUE`（消除了反序列化后为 0L 的问题），但 watermark 推进后的进度在恢复时仍然丢失。`WindowAggregationOperator` 已通过 `WindowAggregationState` 正确持久化此字段，但 `CepOperator` 没有 |
| R13-AR-15 | P2 | `nop-stream-cep/.../sharedbuffer/SharedBuffer.java:172-182` | `advanceTime()` 已清理 `eventsCount`（行 177）和 `eventsBufferCache`（行 180-181），但**不**清理 `eventsBuffer`（持久化 `MapState<EventId, Lockable<V>>`）。时间戳复用后 `registerEvent()` 从 0 开始计数，生成与旧条目碰撞的 `EventId`，`eventsBuffer.put()` 静默覆盖 |

## Goals

- 修复 R12-AR-7：CepOperator 通过覆写 `snapshotState()`/`restoreState()` 直接持久化和恢复 `currentWatermark`（参照 `WindowAggregationOperator` 的直接覆写路径，不使用 `CheckpointParticipant` 接口）
- 修复 R13-AR-15：SharedBuffer `advanceTime()` 同步清理 `eventsBuffer` 中时间戳小于阈值的条目（通过 `eventsBuffer.entries()` 迭代过滤）
- 每个修复有对应的单元测试
- `./mvnw test -pl nop-stream -am` 通过

## Non-Goals

- 不重构 CepOperator 的整个状态管理机制（仅修复 currentWatermark 持久化）
- 不实现 ClosureCleaner（R13-AR-17 的替代方案之一，已由 Plan 102 用具名静态内部类修复）
- 不重构 SharedBuffer 为完全基于时间戳分区的存储架构

## Scope

### In Scope

- CepOperator 的 currentWatermark 持久化修复
- SharedBuffer 的 advanceTime EventId 碰撞修复
- 对应修复的单元测试补充

### Out Of Scope

- 其他 nop-* 模块
- CepOperator 其他 transient 字段审计
- SharedBuffer 性能优化

## Execution Plan

### Phase 1 — [P2] CepOperator currentWatermark 持久化修复（R12-AR-7）

Status: completed
Targets: `nop-stream/nop-stream-cep/.../operator/CepOperator.java`

- Item Types: `Fix`

- [x] 在 CepOperator 中覆写 `snapshotState()` 方法：将 `currentWatermark` 作为 operator state 写入 `OperatorSnapshotResult`
- [x] 在 CepOperator 中覆写 `restoreState()` 方法：从快照中读取并恢复 `currentWatermark`（参照 `WindowAggregationOperator` 的直接覆写路径；不使用 `CheckpointParticipant` 接口）
- [x] 添加单元测试：验证 CepOperator 快照包含 currentWatermark 值，恢复后 watermark 与快照时一致
- [x] 添加测试：验证恢复后的 watermark 推进行为正确（新 watermark > 恢复值时正常推进）

Exit Criteria:

- [x] CepOperator checkpoint/restore 路径正确持久化和恢复 currentWatermark
- [x] 新增快照恢复测试（端到端：从 watermark 推进到快照到恢复到验证恢复值）
- [x] 恢复后 watermark 推进行为不退化
- [x] `./mvnw test -pl nop-stream -am` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — [P2] SharedBuffer advanceTime EventId 碰撞修复（R13-AR-15）

Status: completed
Targets: `nop-stream/nop-stream-cep/.../nfa/sharedbuffer/SharedBuffer.java`

- Item Types: `Fix`

- [x] 在 `registerEvent()` 中生成 EventId 时检查 eventsBuffer 和 eventsBufferCache 是否已存在同 ID，若碰撞则递增 id 直到找到未使用的值（实际实现方案偏离了计划中的 advanceTime 清理方案，因为在 advanceTime 中清理 eventsBuffer 会导致仍在被 NFA 引用的事件丢失，改为在 registerEvent 端防止碰撞）
- [x] 添加单元测试：验证 `advanceTime()` 后 `registerEvent()` 不生成与已有 `eventsBuffer` 条目碰撞的 `EventId`
- [x] 添加测试：验证 `advanceTime()` → `registerEvent()` 不生成与已有 `eventsBuffer` 条目碰撞的 `EventId`（端到端：从 advanceTime 到 registerEvent 到验证 eventsBuffer 无碰撞）
- [x] 添加测试：验证 advanceTime → registerEvent → flushCache 完整链路

Exit Criteria:

- [x] `advanceTime()` 后 `registerEvent()` 不生成与已有 `eventsBuffer` 条目碰撞的 `EventId`
- [x] 新增 EventId 唯一性测试（端到端：从 advanceTime 到 registerEvent 到验证 eventsBuffer 无碰撞）
- [x] 现有 SharedBuffer 测试不退化
- [x] `./mvnw test -pl nop-stream -am` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 全部 P0 发现已修复（无 P0 项，R12/R13 所有 P0 已由 Plan 103 修复）
- [x] 全部 P1 发现已修复（无 P1 项，R12/R13 所有 P1 已由 Plan 102/103 修复）
- [x] R12-AR-7 和 R13-AR-15 已修复
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] `./mvnw test -pl nop-stream -am` 通过
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- CepOperator 其他 transient 字段系统性审计（当前仅有 currentWatermark 被报告）
- SharedBuffer 存储架构优化（按时间戳分区降低 advanceTime 开销）

## Closure

Status Note: Pending execution.

Closure Audit Evidence:

- Reviewer / Agent: Pending independent closure audit
- Evidence: Pending
