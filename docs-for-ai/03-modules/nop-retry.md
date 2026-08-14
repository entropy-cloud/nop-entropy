# nop-retry — 分布式重试引擎

## 功能概览

分布式重试引擎，支持可配置的退避策略。

- 可配置退避策略（固定间隔、指数退避）
- 重试策略管理
- 幂等键支持（死信后同幂等键可重新提交）
- 命名空间/分组隔离
- 回调触发（policy 驱动）
- 重试尝试追踪（每次执行尝试一条 NopRetryAttempt）

## 核心实体

| 实体 | 表名 | 用途 |
|------|------|------|
| NopRetryPolicy | `nop_retry_policy` | 重试策略 |
| NopRetryRecord | `nop_retry_record` | 重试记录 |
| NopRetryAttempt | `nop_retry_attempt` | 重试尝试 |
| NopRetryDeadLetter | `nop_retry_dead_letter` | 重试死信 |

## 退避策略

- **固定间隔**：每次重试间隔固定毫秒数
- **指数退避**：间隔随重试次数指数增长（`jitterRatio` 抖动）

## 阻塞策略

- **丢弃**（DISCARD）：同幂等键已有记录时丢弃新任务
- **覆盖**（OVERWRITE）：覆盖已有记录
- **并行**（PARALLEL）：允许并行执行

## 回调

由 policy 驱动（`callbackEnabled` + `callbackTriggerType` + `callbackPolicyId`），回调执行原任务 service/method，payload 含 `recordId`/`idempotentId`/`success`/`retryCount`/`errorCode`/`errorMessage`；回调任务 idempotentId 追加 `_callback` 后缀。

## 关键字段

**NopRetryPolicy**:
- `backoffStrategy`：退避策略（固定间隔/指数退避）
- `maxRetryCount`：最大重试次数
- `initialIntervalMs` / `maxIntervalMs`：初始/最大间隔毫秒数
- `jitterRatio`：抖动比例
- `immediateRetryCount` / `immediateRetryIntervalMs`：立即重试次数/间隔
- `deadlineTimeoutMs`：总截止超时
- `retryingTimeoutMs`：执行中锁定超时
- `blockStrategy`：阻塞策略
- `callbackEnabled` / `callbackTriggerType` / `callbackPolicyId`：回调配置

**NopRetryRecord**:
- `namespaceId`：命名空间
- `groupId`：分组
- `executorName`：执行器名称
- `idempotentId`：幂等键
- `status`：状态（PENDING/RETRYING/COMPLETED/MAX_RETRIES/SUSPENDED）
- `nextTriggerTime`：下次触发时间
- `partitionIndex`：分区索引（集群扫描分区）
- `retryCount` / `maxRetryCount`：重试计数

**NopRetryAttempt**:
- `recordId` + `attemptNo`：归属记录与尝试序号（唯一）
- `status`：WAITING/RUNNING/SUCCESS/FAILED/STOPPED/CANCELED
- `startTime` / `endTime` / `durationMs`：耗时
- `errorCode` / `errorMessage` / `errorStack`：失败信息
- `requestPayloadSnapshot`：请求快照

**NopRetryDeadLetter**:
- `recordId`：原记录 ID（原记录在死信化时删除，死信保留全量快照）
- `idempotentId`：幂等键（非唯一，多次失败周期可产生多条死信）
- `requestPayload` / `failureCode` / `failureMessage` / `errorStack`：失败快照

## 执行语义

- **尝试追踪**：每次执行尝试（含立即/延迟重试）写一条 `NopRetryAttempt`，attemptNo = retryCount + 1。
- **死信化**：maxRetry 耗尽/bizFatal/deadline 超时 → 死信全量快照 + 删除原 record 行；此后同 idempotentId 可重新提交。
- **死信重放**：`retryFromDeadLetter` = 手动单次重放（fire-and-forget，不新建 record、不改死信状态）。
- **分区**：`partitionIndex = floorMod(hash(namespaceId:groupId:idempotentId), 16)`，集群扫描按分区取数。
- **回调**：`callbackEnabled` + `callbackTriggerType`（ON_SUCCESS/ON_FAILURE/ALWAYS）+ `callbackPolicyId`；回调执行原 service/method，新任务 idempotentId 追加 `_callback` 后缀。

## 子模块

| 子模块 | 职责 |
|--------|------|
| `nop-retry-engine` | 重试引擎核心 |
| `nop-retry-dao` | ORM 实体与 DAO |
| `nop-retry-service` | 业务逻辑 |
| `nop-retry-web` | Web 层与 AMIS 页面 |

## 关系

- `nop-job` 可通过 `nop-job-retry-adapter` 集成 nop-retry 实现任务失败重试

## 源码锚点

| 组件 | 路径 |
|------|------|
| ORM 模型 | `nop-retry/model/nop-retry.orm.xml` |

## 相关文档

- `../nop-job.md`
- `../reusable-modules-overview.md`
