# Adversarial Review Summary: nop-job (2026-06-03)

## 基本信息

- **审核模块**: nop-job
- **审核日期**: 2026-06-03
- **审核类型**: 对抗性审查（开放式发现导向）
- **审查方法**: 从代码异常信号出发，重点验证 2026-05-18 审计修复质量

## 修复验证结果

### 已修复 (11/15)

先前审计 (R1+R2) 的 15 项主要发现中，11 项已确认修复。修复质量总体良好，关键 P0/P1 问题（dispatch timeout 死代码、overlay 统计丢失、SUSPICIOUS 状态处理、PARALLEL 策略、RECOVERY 调度空转）均已解决。

### 仍未修复 (2/15)

- **F9 (P1)**: retryRecordId 返回 jobFireId 而非实际重试记录 ID — 异步设计限制
- **F14 (P3)**: JobFireResult.CONTINUE 字段/方法名冲突 — API 设计气味

### 部分修复 (1/15)

- **F4 (P2)**: toTriggerSpec 已合并，pauseCalendars 已修复，但 maxFailedCount 仍硬编码为 0

## 新发现 (7 项)

| ID | 严重程度 | 一句话摘要 |
|----|---------|-----------|
| AR-1 | **P1** | F1 修复残留：startTime = 锁过期时间（未来时刻）→ 取消防送时长始终为 0ms；调度超时比配置多 60s |
| AR-2 | **P1** | R2-12 修复残留：恢复 fire 缺失 triggerSource/retryPolicyId/jobParamsSnapshot → worker 收到空参数 |
| AR-3 | P2 | insertManualFire overlay 取消循环缺少 try-catch（与 planner 路径不一致） |
| AR-4 | P2 | retryRecordId 死写 — 在 persist 之后设置，永远不会刷新到 DB |
| AR-5 | P2 | CLAIMED→RUNNING 之间的 worker 崩溃导致任务永远孤儿 — 超时检查器不会发现 CLAIMED 任务 |
| AR-6 | P3 | Planner parallel 路径 setActiveFireCount(0) 是无效代码 — 已在 store 内持久化 |
| AR-7 | P3 | maxFailedCount 硬编码为 0 — 无 ORM 列提供值 |

## 总评

nop-job 模块的 5 月审计修复工作覆盖了大部分发现，代码质量有明显改善。**但两个修复本身引入了新的语义错误**：

1. **AR-1** (F1 残留): 修复 dispatch timeout 死代码时，将 `startTime` 设为锁过期时间（`now + lockTimeoutMs`）而非实际开始时间（`now`）。这导致 `startTime` 语义变为"锁过期"而非"开始执行"，级联影响 cancel duration 计算和 dispatch timeout 截止时间。

2. **AR-2** (R2-12 残留): 修复 RECOVERY 调度空转时，新建 fire 的路径遗漏了 `buildFire` 中的 4 个关键字段。其中 `jobParamsSnapshot` 缺失意味着 worker 执行时收到空参数，任务很可能立即失败。

这两个 P1 问题建议优先修复。

## 去重信息

- 2026-05-18-adversarial-review-nop-job (R1+R2): 31 项发现，已验证状态
- 2026-05-18-deep-audit-nop-job-full: 154 项发现（系统审计），本报告仅覆盖对抗性审查范围
