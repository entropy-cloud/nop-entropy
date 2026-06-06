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
| `executionTimeoutSeconds` | 单次执行超时 |
| `deadlineTimeoutMs` | 总截止超时 |
| `blockStrategy` | 阻塞策略（丢弃/覆盖/并行） |
| `saveRecordStrategy` | 保存记录策略 |
| `callbackEnabled` | 是否启用回调 |
| `callbackTriggerType` | 回调触发类型 |

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
     * 桥接实现应将其提交给 nop-retry。
     *
     * @return retry_record_id，null 表示未提交重试
     */
    String onFireFailed(JobFireFailedEvent event);
}
```

默认实现 `NoOpJobRetryBridge` 返回 null，不做任何事。

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
9. 调用 `task.callAsync(request, null)`
10. 返回 retry record ID

### 3.3 调用时机

在 `JobCompletionProcessorImpl.tryCompleteFireAndGetStatus()` 中：

```
fire 失败 (FIRE_STATUS_FAILED)
  ↓
检查 fire.getRetryPolicyId() 或 schedule.getRetryPolicyId()
  ↓ (非空)
调用 IJobRetryBridge.onFireFailed(event)
  ↓
将返回的 retry_record_id 回填到 fire.setRetryRecordId(...)
  ↓
fireStore.completeFireAndUpdateSchedule(fire, schedule)
```

### 3.4 retryPolicyId 的优先级

```
fire.retryPolicyId 优先（允许单次 fire 覆盖）
  ↓ (为空)
schedule.retryPolicyId 兜底
  ↓ (为空)
不触发重试
```

### 3.5 Retry 执行时的回调

`NopRetryPolicy` 支持 `callbackEnabled` + `callbackTriggerType`：

- `ON_SUCCESS`：重试成功时回调 nop-job 标记 fire 成功
- `ON_FAILURE`：重试最终失败时回调 nop-job 标记 fire 最终失败
- `ALWAYS`：两种情况都回调

回调通过 `IRetryTask.withCallback(serviceName, serviceMethod)` 设置：

- `callbackService` = `"nopJobCompletionService"`（由 adapter 注册）
- `callbackMethod` = `"onRetryComplete"`

回调 payload 包含：`jobFireId`、`retrySuccess`、`attemptCount`、`errorCode`、`errorMessage`。

---

## 4. 数据模型变更

### 4.1 NopJobFire 已有字段（无需变更）

| 字段 | 用途 |
|------|------|
| `retry_policy_id` | 桥接到 `nop_retry_policy.sid` |
| `retry_record_id` | 回填的 retry 记录 ID（已有） |

### 4.2 NopJobSchedule 已有字段（无需变更）

| 字段 | 用途 |
|------|------|
| `retry_policy_id` | 默认重试策略 |

### 4.3 无需新增数据库字段

所有桥接所需的字段（`retry_policy_id`、`retry_record_id`）已存在于现有实体中。

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
