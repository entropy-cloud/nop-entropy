# nop-job 限流设计

> Status: draft
> Created: 2026-05-17
> Related: `ai-dev/plans/17-nop-job-block-strategy-metrics.md`

## 1. 调研：nop-cluster 限流能力

### 1.1 nop-cluster 搜索结果

在 `nop-cluster` 模块中搜索 `RateLimit` / `Throttle` / `IRateLimiter` / `IThrottle`，**未找到任何限流相关接口**。

nop-cluster 提供的能力：
- `IDiscoveryClient` — 服务发现
- `ServiceInstance` — 实例信息
- `ILoadBalance` — 负载均衡（含 `LeastActiveLoadBalance`）
- `WeightedPartitionAssigner` — 加权分区分配

### 1.2 snail-job 限流机制

（待补充：需分析 snail-job 的限流实现细节）

## 2. nop-task 已有限流能力

nop-task 提供的限流 Step：
- `RateLimitTaskStepWrapper` — 速率限制
- `ThrottleTaskStepWrapper` — 节流控制

但这是 **Step 级别**的限流，不是 **Job 级别**或 **Worker 节点级别**的并发控制。

## 3. 设计方案

### 3.1 需要限流的场景

| 场景 | 说明 | 建议方案 |
|------|------|----------|
| Worker 节点级并发控制 | 限制单个 Worker 同时执行的 Job 数量 | `Semaphore` + `IJobWorkerMetrics.onRejected()` |
| Job 级并发控制 | 限制某个 Job 的全局并发数 | 基于 DB 行锁或 Redis 分布式信号量 |
| 集群级总并发控制 | 限制整个集群的总并发执行数 | 需要分布式协调（Redis / DB） |

### 3.2 是否需要 nop-cluster 新增接口

**结论：暂不需要**。

原因：
1. nop-job 的限流需求是 Job 领域特定的，不属于通用集群能力
2. Worker 级并发用 JDK `Semaphore` 即可
3. 分布式限流如需要，可作为独立的 `nop-commons` 工具类实现

### 3.3 实现方案（待细化）

1. `NopJobSchedule` 增加 `maxConcurrency` 字段（int, 默认 1）
2. Worker 层 `IJobInvoker` 执行前检查并发限制
3. 超过限制时返回 `REJECTED` 状态，Coordinator 记录 metrics

## 4. 待决策

- snail-job 的限流是按 Worker 粒度还是按 Job 粒度？→ 需要先调研 snail-job 源码
- 是否需要分布式限流（跨 Worker 节点的全局并发控制）？
- 限流被拒后是否自动排队重试？
