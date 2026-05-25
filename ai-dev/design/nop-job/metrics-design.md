# nop-job Metrics 设计

> Status: implemented
> Created: 2026-05-17
> Last Updated: 2026-05-18
> Related: `ai-dev/plans/17-nop-job-block-strategy-metrics.md`
> Convention: `ai-dev/lessons/02-metrics-design-convention.md`

## 1. 设计规范

严格遵循 `../../../lessons/02-metrics-design-convention.md` 的三件套模式：接口（业务语义方法名）+ 真实实现（`GlobalMeterRegistry` + 构造函数预创建）+ 空实现（no-op fallback）。

业务组件持有接口类型字段，默认值为空实现，通过 setter 注入真实实现。

## 2. Meter 命名契约

Meter 名称是使用者在 Grafana/Prometheus 中直接看到的契约，属于对外接口。

命名规范：`nop.job.{component}.{metric-name}`

| 层级 | 前缀 | Meter 示例 |
|------|------|-----------|
| Planner | `nop.job.planner.` | `due-count`, `lock-conflict` |
| Dispatcher | `nop.job.dispatcher.` | `waiting-fires`, `dispatch-conflict`, `fires-dispatched` |
| Worker | `nop.job.worker.` | `tasks-claimed`, `task-success`(Timer), `task-failure`, `task-timeout` |
| Completion | `nop.job.completion.` | `fires-completed`, `fire-success`(Timer), `fire-failure`, `fire-timeout` |

## 3. Planner Metrics（已实现）

`IJobPlannerMetrics` / `JobPlannerMetricsImpl` / `EmptyJobPlannerMetrics`

- `nop.job.planner.due-count` — Counter
- `nop.job.planner.lock-conflict` — Counter

注入：`JobPlannerScannerImpl.plannerMetrics`

## 4. Dispatcher Metrics（已实现）

`IJobDispatcherMetrics` / `JobDispatcherMetricsImpl` / `EmptyJobDispatcherMetrics`

- `nop.job.dispatcher.waiting-fires` — Counter：扫描到的 WAITING fire 数
- `nop.job.dispatcher.dispatch-conflict` — Counter：锁冲突次数
- `nop.job.dispatcher.fires-dispatched` — Counter：成功分发次数

注入：`JobDispatcherScannerImpl.dispatcherMetrics`

## 5. Worker Metrics（已实现）

`IJobWorkerMetrics` / `JobWorkerMetricsImpl` / `EmptyJobWorkerMetrics`

- `nop.job.worker.tasks-claimed` — Counter：认领的 task 数
- `nop.job.worker.task-success` — Timer：成功执行耗时
- `nop.job.worker.task-failure` — Counter：失败次数
- `nop.job.worker.task-timeout` — Counter：超时次数
- `nop.job.worker.task-rejected` — Counter：因 maxConcurrency 超限被跳过的次数

注入：`JobWorkerScannerImpl.workerMetrics`

## 6. Completion Metrics（已实现）

`IJobCompletionMetrics` / `JobCompletionMetricsImpl` / `EmptyJobCompletionMetrics`

- `nop.job.completion.fires-completed` — Counter：扫描中完成的 fire 数
- `nop.job.completion.fire-success` — Timer：成功 fire 耗时
- `nop.job.completion.fire-failure` — Counter：失败 fire 数
- `nop.job.completion.fire-timeout` — Counter：超时 fire 数

注入：`JobCompletionProcessorImpl.completionMetrics`

## 7. Schedule 聚合统计字段

`NopJobSchedule` 新增列：

| 字段 | 类型 | 说明 |
|------|------|------|
| `lastDurationMs` | BIGINT | 上次执行耗时（毫秒） |
| `totalFireCount` | BIGINT | 总触发次数 |
| `successFireCount` | BIGINT | 成功触发次数 |
| `failFireCount` | BIGINT | 失败触发次数 |

更新时机：`JobCompletionProcessorImpl` 处理 fire 完成时递增。可通过 GraphQL `NopJobSchedule__findPage` 查询。

## 8. 不做

- 不做独立的 `job_summary` 表（schedule 聚合字段够用）
- 不做 Dashboard UI（聚合字段已可 GraphQL 查询）
