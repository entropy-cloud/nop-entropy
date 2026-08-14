# nop-job retryPolicyId 对接 nop-retry 设计

**日期**：2026-05-18
**范围**：`nop-job` ↔ `nop-retry` 集成
**状态**：设计完成

---

## 1. 设计结论

nop-job 的 `retryPolicyId` 是一个**声明式外键桥接点**，指向 `nop_retry_policy.sid`。当 job fire 失败且 schedule 上配置了 `retryPolicyId` 时，nop-job 通过 `IJobRetryBridge` 接口把失败事件交给 nop-retry 处理。nop-job 自身不实现任何重试逻辑。

---

## 2. 背景

### 2.1 nop-retry 提供的能力

`IRetryEngine` + `IRetryTask` 是 nop-retry 的核心 API：

- `IRetryEngine.newRetryTask(serviceName, serviceMethod)` 创建重试任务
- `IRetryTask.withPolicyId(policyId)` 指定策略
- `IRetryTask.withIdempotentId(id)` 幂等控制
- `IRetryTask.callAsync(request, cancelToken)` 提交执行

`NopRetryPolicy` 实体包含完整重试控制参数：

| 字段 | 用途 |
|------|------|
| `immediateRetryCount` | 立即重试次数 |
| `immediateRetryIntervalMs` | 立即重试间隔 |
| `maxRetryCount` | 最大重试次数 |
| `backoffStrategy` | 退避策略（固定间隔/指数退避） |
| `initialIntervalMs` | 初始间隔 |
| `maxIntervalMs` | 最大间隔 |
| `jitterRatio` | 抖动比例 |
| `deadlineTimeoutMs` | 总截止超时 |
| `blockStrategy` | 阻塞策略（丢弃/覆盖/并行） |
| `callbackEnabled` | 是否启用回调 |
| `callbackTriggerType` | 回调触发类型 |
| `callbackPolicyId` | 回调任务使用的策略 |

### 2.2 nop-retry 的执行模型

1. `RetryEngineImpl.executeTask()` 创建 `NopRetryRecord`，加载 `NopRetryPolicy`
2. 立即重试阶段：在 `immediateRetryCount` 次内内联重试
3. 延迟重试阶段：`RetryScannerImpl` 定期扫描 PENDING 记录，乐观锁 claim 后重新执行
4. 超限后进入死信 `NopRetryDeadLetter`

### 2.3 nop-job 当前状态

- `NopJobSchedule` 和 `NopJobFire` 均有 `retryPolicyId` 字段（VARCHAR，可空）
- nop-job-coordinator **没有**任何对 `IRetryEngine` / `IRetryTask` 的引用
- 失败的 fire 被标记为 `FIRE_STATUS_FAILED(40)` 后无后续处理
- `JobCompletionProcessorImpl` 在 fire 失败时只更新 schedule 统计，不触发重试

---

## 3. 集成方案

### 3.1 核心接口

```java
public interface IJobRetryBridge {
    /**
     * Fire 失败时调用。如果 fire 关联了 retryPolicyId，
     * 桥接实现应将其提交给 nop-retry（异步投递，不返回 retry 记录 ID）。
     */
    void onFireFailed(JobFireFailedEvent event);
}
```

默认实现 `NoOpJobRetryBridge` 不做任何事。

> 2026-08-13 更新（plan 341）：`onFireFailed` 返回类型从 `String`（retry_record_id）改为 `void`。
> 实际实现 `NopRetryJobRetryBridge` 自始使用 `task.callAsync(...)` 异步提交，从未回填 `fire.retry_record_id`；
> 同步回填能力废弃后 `NopJobFire.retry_record_id` 列被移除（见 §4.1），桥接不再有返回值。

### 3.2 nop-job-retry-adapter 模块

新增可选模块 `nop-job-retry-adapter`，提供 `NopRetryJobRetryBridge` 实现：

1. 从 `fire` 读取 `retryPolicyId`，为空则直接返回 null
2. 从 `schedule` 读取 `namespaceId`、`groupId` 用于 retry record 分组
3. 通过 `IRetryEngine.newRetryTask(serviceName, serviceMethod)` 创建 retry task：
   - `serviceName` = fire 的 `executorKind` 或固定约定值（如 `"nopJobExecutor"`）
   - `serviceMethod` = fire 的 `jobName` 或固定约定值（如 `"execute"`）
4. 设置 `.withPolicyId(fire.getRetryPolicyId())`
5. 设置 `.withIdempotentId("job-fire:" + fire.getJobFireId())` — 确保同一 fire 不重复创建 retry record
6. 设置 `.withNamespaceId(schedule.getNamespaceId())`
7. 设置 `.withGroupId(schedule.getGroupId())`
8. 构建 `ApiRequest`：request body 包含 fire 的 `jobFireId`、schedule 的关键信息
9. 调用 `task.callAsync(request, null)`（异步投递，不等待执行结果，也不回填 fire）

> 2026-08-13 更新（plan 341）：bridge 为异步 fire-and-forget 投递，删除原本"返回 retry record ID"的描述（§3.1 签名已改 void）。

### 3.3 调用时机

在 `JobCompletionProcessorImpl.tryCompleteFireAndGetStatus()` 中：

```
fire 失败 (FIRE_STATUS_FAILED)
  ↓
检查 fire.getRetryPolicyId() 或 schedule.getRetryPolicyId()
  ↓ (非空)
调用 IJobRetryBridge.onFireFailed(event)
  ↓
fireStore.completeFireAndUpdateSchedule(fire, schedule)  → boolean
  ↓
返回 true → 继续触发指标/告警
返回 false → 版本冲突，跳过指标/告警（Fire 将在下次扫描中重新处理）
```

> 2026-07-28 更新：`completeFireAndUpdateSchedule` 的返回类型从 `void` 改为 `boolean`。
> 调用方必须在返回 false 时跳过 retry/alarm/metrics 触发，避免双 Coordinator 部署下的误触发。

### 3.4 retryPolicyId 的优先级

```
fire.retryPolicyId 优先（允许单次 fire 覆盖）
  ↓ (为空)
schedule.retryPolicyId 兜底
  ↓ (为空)
不触发重试
```

### 3.5 Retry 执行时的回调

回调由 **policy 配置驱动**（不是 `IRetryTask` 上的回调字段——`withCallback` 属死 API，已随 plan 342 移除）：

- `NopRetryPolicy.callbackEnabled` 启用回调
- `callbackTriggerType`：`ON_SUCCESS`（重试成功时回调）/ `ON_FAILURE`（重试最终失败时回调）/ `ALWAYS`（两种情况都回调）
- `callbackPolicyId` 指向回调目标策略（回调本身也走 retry 链路）

回调目标 = 原任务的 serviceName/serviceMethod（回调执行原业务逻辑），而非独立的"完成服务"。

回调 payload（`RetryEngineImpl.triggerCallback/buildCallbackData`）包含：`recordId`、`idempotentId`、`success`、`retryCount`、`errorCode`、`errorMessage`。

回调任务使用新 `NopRetryRecord`，其 `idempotentId` = 原 idempotentId + `"_callback"` 后缀，避免与主任务幂等键冲突。

> 2026-08-13 更新（plan 342）：删除原"回调经 `withCallback` 到 `nopJobCompletionService.onRetryComplete`"描述——该设计从未实现，实际为 policy 驱动回调原服务。bridge 保持 fire-and-forget（§3.2），回调不回写 fire 状态。

---

## 4. 数据模型变更

### 4.1 NopJobFire 已有字段

| 字段 | 用途 |
|------|------|
| `retry_policy_id` | 桥接到 `nop_retry_policy.sid` |

> 2026-08-13 更新（plan 341）：`retry_record_id` 列已移除——
> 桥接为异步 fire-and-forget 投递（§3.1 签名 void），不存在回填方；同步回填若未来实现，需以独立设计引入新列。

### 4.2 NopJobSchedule 已有字段（无需变更）

| 字段 | 用途 |
|------|------|
| `retry_policy_id` | 默认重试策略 |

### 4.3 无需新增数据库字段

桥接所需的 `retry_policy_id` 已存在于现有实体中；`retry_record_id` 因异步投递不需要（plan 341 移除）。

---

## 5. 模块依赖

```
nop-job-coordinator
  ├── 依赖 nop-job-api（IJobRetryBridge 定义在此）
  └── 不直接依赖 nop-retry-api

nop-job-retry-adapter（可选模块）
  ├── 依赖 nop-job-api（IJobRetryBridge 接口）
  ├── 依赖 nop-retry-api（IRetryEngine、IRetryTask）
  └── 通过 IoC 注册为 IJobRetryBridge 实现
```

nop-job-coordinator 只通过 `IJobRetryBridge` 接口与重试系统交互。`nop-job-retry-adapter` 是可选的，运行时不引入则使用 no-op 默认实现。

---

## 6. 被拒绝的替代方案

### 6.1 job 内建重试字段

在 `NopJobSchedule` 中增加 `maxRetryTimes`、`retryInterval`、`backoffStrategy` 等字段。

**拒绝理由**：与 nop-retry 形成重复系统。nop-retry 已有完整的退避算法、死信、阻塞策略、回调机制。重复建模增加维护负担且功能不完整。

### 6.2 直接在 coordinator 中注入 IRetryEngine

让 `JobCompletionProcessorImpl` 直接依赖 `IRetryEngine`。

**拒绝理由**：违反分层原则。coordinator 不应直接依赖 retry 引擎，应通过桥接接口解耦。这使得 nop-job 可以在没有 nop-retry 的环境下独立运行。

### 6.3 由 Worker 端负责重试提交

Task 执行失败时由 Worker 提交重试。

**拒绝理由**：Worker 可能不可达（网络分区、进程崩溃）。重试提交应由 Coordinator 侧完成，Coordinator 有完整的 fire/schedule 上下文。

---

## 7. 与架构基线的关系

本文档是 `01-architecture-baseline.md` §七.4（执行完成流程）和 `00-vision.md` §三.3（job 和 retry 必须分层）的详细展开。基线文档定义了分层原则和桥接方向，本文档补充了：

1. 具体的 `IJobRetryBridge` 接口签名和调用时机
2. `retryPolicyId` 的优先级策略（fire > schedule）
3. adapter 模块的依赖边界
4. 回调机制的约定
5. 被拒绝的替代方案

---

## 8. 执行语义裁定（plan 342，2026-08-13）

> 以下裁定将 nop-retry 执行语义与 live 实现收敛，为 retry-integration-design 之外的运行时行为提供契约。

### 8.1 执行尝试追踪（NopRetryAttempt）

- 每次执行尝试（含立即重试与延迟重试）写一条 `NopRetryAttempt`：attemptNo = 当前 retryCount + 1，status 迁移 WAITING→RUNNING→SUCCESS/FAILED，记录 startTime/endTime/durationMs、errorCode/errorMessage/errorStack、requestPayloadSnapshot。
- `UK_RETRY_ATTEMPT_RECORD_NO(recordId, attemptNo)` 保证编号唯一；retryCount 单调递增，编号可能因 handleExecutionFailure 的 +1 出现间隙（如 1,2,4），不构成冲突。
- **已知限制（watch-only）**：PARALLEL 阻塞策略下同一 record 并发执行时，attemptNo 均由 retryCount+1 派生，理论上可能撞唯一约束；PARALLEL 语义本身不保证并发安全，视为既有设计限制。

### 8.2 分区赋值（partitionIndex）

- `newRecord` 计算 `partitionIndex = Math.floorMod(hash(namespaceId + ":" + groupId + ":" + idempotentId), DEFAULT_PARTITION_COUNT)`（hash 采用 `String.hashCode()`），同幂等键稳定落同分区，集群模式下 scanner 按分区取数可用。

### 8.3 死信后幂等键可复用（Decision）

- `moveToDeadLetter` 保存死信全量快照（含 recordId/requestPayload/失败信息）后**删除原 record 行**，使同一 idempotentId 可重新提交（`findPendingRecordByIdempotentId` 只查 PENDING/RETRYING，且原行已删除，无唯一约束冲突）。
- **副作用（已裁定）**：
  (a) `deadLetter→record` to-one relation 悬空（两表无 FK/cascade，`deleteEntityDirectly` 不触发级联，仅关系导航为 null）；
  (b) attempt 行成为孤儿（recordId 指向已删 record，历史明细不再可经 record 导航）；
  (c) 管理侧行为变更：`loadRecord`/`pause`/`resume` 对死信后的 recordId 返回 not-found，`NopRetryRecord` 页面上 MAX_RETRIES 历史行消失——终态视图以死信页为准（死信行保留 recordId 字段可查）。
- **多次失败周期**：`UK_RETRY_DL_IDEMPOTENT_ID` 已收窄为普通索引 `IDX_RETRY_DL_IDEMPOTENT_ID`（orm.xml，plan 342），同一幂等键多次失败周期可产生多条死信，二次失败不会撞约束。

### 8.4 retryFromDeadLetter 语义（Decision）

- `retryFromDeadLetter` = **手动单次重放**：读取死信快照，单次 `invokeAsync`（fire-and-forget，不新建 record、不改死信状态、失败无痕迹）。
- 错误路径快速失败：死信不存在 / 缺 serviceName/serviceMethod / 缺 requestPayload，分别返回 `ERR_RETRY_DEAD_LETTER_NOT_FOUND` / `ERR_RETRY_DEAD_LETTER_INVALID_EXECUTOR` / `ERR_RETRY_DEAD_LETTER_INVALID_REQUEST`。
