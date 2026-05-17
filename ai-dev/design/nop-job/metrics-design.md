# nop-job Metrics 设计

> Status: draft
> Created: 2026-05-17
> Related: `ai-dev/plans/17-nop-job-block-strategy-metrics.md`
> Convention: `ai-dev/lessons/02-metrics-design-convention.md`

## 1. 设计规范

严格遵循 `lessons/02-metrics-design-convention.md` 的三件套模式：接口（业务语义方法名）+ 真实实现（`GlobalMeterRegistry` + 构造函数预创建）+ 空实现（no-op fallback）。

## 2. Meter 命名契约

Meter 名称是使用者在 Grafana/Prometheus 中直接看到的契约，属于对外接口。

命名规范：`nop.job.{component}.{metric-name}`

| 层级 | 前缀 | Meter 示例 |
|------|------|-----------|
| Planner | `nop.job.planner.` | `due-count`, `lock-conflict` |
| Dispatcher | `nop.job.dispatcher.` | `dispatch-count`, `dispatch-failed`, `timeout-count`, `overlay-cancelled` |
| Worker | `nop.job.worker.` | `invoke-count`, `invoke-failed`, `invoke-duration`(Timer), `invoke-rejected` |
| Completion | `nop.job.completion.` | `fire-count`(tagged), `fire-failed`(tagged), `fire-duration`(Timer, tagged) |

Tag 维度：`executorKind`（test/rpc/rpcBroadcast）、`jobGroup`。

## 3. 已有：IJobPlannerMetrics（coordinator/metrics/）

- `nop.job.planner.due-count` — Counter
- `nop.job.planner.lock-conflict` — Counter

## 4. 需新增的 Metrics 接口

| 接口 | 层级 | 注入目标 |
|------|------|----------|
| `IJobDispatcherMetrics` | Coordinator | `JobDispatcherScannerImpl` |
| `IJobWorkerMetrics` | Worker | `IJobInvoker` 实现 |
| `IJobCompletionMetrics` | Coordinator | `JobCompletionProcessorImpl` |

每个接口的具体 Meter 见第 2 节表格。

## 4. 决策：Schedule 聚合 vs 独立 summary 表

**选了**：在 `NopJobSchedule` 实体上增加聚合统计字段（`lastFireTime`、`lastFireStatus`、`lastDurationMs`、`totalFireCount`、`successFireCount`、`failFireCount`）。

**为什么**：snail-job 用独立的 `job_summary` 表，但 nop-job 的 schedule 表已经是查询入口，聚合字段直接挂在上面 GraphQL 自动可查，不需要额外的表和 JOIN。如果未来统计维度超出 schedule 粒度再考虑独立表。

**更新时机**：`JobCompletionProcessorImpl` 处理 fire 完成时递增。

## 5. 不做

- 不做独立的 `job_summary` 表
- 不做 Dashboard UI
