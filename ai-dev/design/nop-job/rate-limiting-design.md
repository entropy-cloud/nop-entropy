# nop-job 限流设计

> Status: draft
> Created: 2026-05-17
> Last Updated: 2026-05-18

## 1. Nop 平台已有限流体系

**不需要引入 resilience4j**。Nop 平台已有完整的限流基础设施。

### 1.1 核心接口与实现

| 组件 | 模块 | 说明 |
|------|------|------|
| `IRateLimiter` | `nop-commons` | 令牌桶限流器接口，`tryAcquire(permits, timeout)` |
| `DefaultRateLimiter` | `nop-commons` | 基于 Guava `RateLimiter` 的实现 |
| `IFlowControlRunner` | `nop-rpc-api` | 限流执行器抽象，`runAsync(FlowControlEntry, task)` / `run(entry, task)` |
| `DefaultFlowControlRunner` | `nop-rpc-core` | 基于 `IRateLimiter` 的默认实现，令牌桶 + 最大等待时间 |
| `SentinelFlowControlRunner` | `nop-cluster-sentinel` | 集成 Alibaba Sentinel（流控+熔断降级+系统保护+热点参数限流+集群流控） |
| `FlowControlRpcServiceInterceptor` | `nop-rpc-api` | RPC 拦截器，对 RPC 调用自动限流 |
| `FlowControlEntry` | `nop-rpc-api` | 限流上下文（resource/resourceType/bizKey/origin/inBound） |

### 1.2 已接入的限流点

| 层级 | 接入方式 | 状态 |
|------|---------|------|
| **RPC Client** | `nopRpcServiceClientInterceptors` 列表 | **未启用** — 当前只有 stat/log/clientContext/tcc 拦截器，`FlowControlRpcServiceInterceptor` 未加入 |
| **GraphQL** | `GraphQLEngine.setFlowControlRunner()` | **已启用** — 自动注入 `nopFlowControlRunner`，对 GraphQL 请求做全局限流 |
| **nop-task** | `RateLimitTaskStepWrapper` / `ThrottleTaskStepWrapper` | **已启用** — Step 级别限流，支持 global 和 per-instance 两种模式 |
| **nop-batch** | `RateLimitConsumer` | **已启用** — 批处理消费限流 |

### 1.3 Bean 注册机制

引入 `nop-cluster-sentinel` 模块后自动注册：

```xml
<!-- sentinel-defaults.beans.xml -->
<bean id="nopFlowControlRunner" ioc:default="true"
      class="io.nop.cluster.sentinel.SentinelFlowControlRunner"/>
```

- `ioc:default="true"` 表示其他模块可以覆盖
- `GraphQLEngine` 通过 setter 自动按类型注入 `IFlowControlRunner`
- 不引入 sentinel 时，需手动注册 `DefaultFlowControlRunner`

### 1.4 Sentinel 规则配置

`SentinelRuleConfig` 通过 `@cfg:` 前缀读取配置，支持动态刷新：

| 配置项 | 说明 |
|--------|------|
| `nop.cluster.sentinel.flow-rules` | 流控规则（JSON） |
| `nop.cluster.sentinel.degrade-rules` | 熔断降级规则 |
| `nop.cluster.sentinel.sys-rules` | 系统保护规则 |
| `nop.cluster.sentinel.auth-rules` | 授权规则 |

### 1.5 snail-job 限流机制对比

snail-job 不提供显式的限流能力。其并发控制通过以下机制间接实现：

| 机制 | 说明 |
|------|------|
| 阻塞策略 | DISCARD / OVERLAY / PARALLEL，控制同一 Job 的并发 |
| Worker 线程池 | 每个 Worker 内置线程池，线程数 = 并发上限 |
| Netty 连接数 | Client/Server 间 Netty 连接天然限制吞吐 |

## 2. nop-job 限流现状与 Gap

### 2.1 已覆盖的

| 场景 | 覆盖方式 |
|------|---------|
| Job 级并发控制 | 4 种阻塞策略（DISCARD/OVERLAY/PARALLEL/RECOVERY） |
| GraphQL 请求限流 | GraphQLEngine 已注入 FlowControlRunner |
| Step 级限流 | nop-task 的 RateLimitTaskStepWrapper |
| Job 触发 RPC 时的调用 | 如果 RPC interceptor 链启用 FlowControl，自动覆盖 |

### 2.2 未覆盖的

| 场景 | 说明 |
|------|------|
| Worker 节点级并发控制 | 限制单个 Worker 同时执行的 task 数量 |
| RPC 限流拦截器未启用 | `FlowControlRpcServiceInterceptor` 未加入 `nopRpcServiceClientInterceptors` |

## 3. 设计方案

### 3.1 Worker 级并发控制

**方案：查询当前 Worker 在途 task 数 + 超阈值跳过拉取**

`JobWorkerScannerImpl.scanOnce()` 流程：
1. 调用 `IJobTaskStore.countRunningTasks(workerInstanceId)` 查询本 Worker 当前 RUNNING 状态的 task 数
2. 如果 >= `maxConcurrency`，跳过本轮扫描（不发 SQL 拉取新 task）
3. 否则正常拉取，但限制 `remaining = maxConcurrency - runningCount` 作为 batch 上限

**为什么不用 Semaphore**：
- `invoker.invokeAsync()` 是异步的，scanner 不等待完成
- Semaphore 需要 `acquire` 在执行前、`release` 在回调里，如果回调永远不触发（极端异常）会泄漏
- "count + skip" 模式与现有的 `activeFireCount` 思路一致，无生命周期管理风险
- count 查询走 DB，天然准确（task 状态是持久化的）

**配置项**：`nop.job.worker.max-concurrency`（默认 0 = 不限制）

**代码改动**：
- `IJobTaskStore` 增加 `countRunningTasks(String workerInstanceId)`
- `JobWorkerScannerImpl` 增加 `maxConcurrency` 字段，`scanOnce` 开头检查
- `IJobWorkerMetrics` 增加 `onRejected()`

### 3.2 RPC 限流启用

**方案：在 nop-job 的 beans.xml 中配置 `FlowControlRpcServiceInterceptor`**

nop-job 的 Worker 执行 RPC 调用时（通过 `IJobInvoker`），底层走的是 `IRpcServiceInvoker`。启用 RPC 限流：

1. 在 nop-job-worker 的 beans.xml 中将 `FlowControlRpcServiceInterceptor` 加入 RPC interceptor 链
2. 配置 Sentinel 流控规则，resource 设为 `job:{executorKind}:{jobName}`
3. 不需要改 nop-job 代码，纯配置启用

### 3.3 Job 级并发控制

**已通过阻塞策略完全覆盖**。`blockStrategy` + `activeFireCount` 实现 4 种策略。

### 3.4 集群级限流

后续按需。方案：利用 Sentinel 集群流控模式（需要 Token Server），或基于 DB 行锁的分布式信号量。

### 3.5 是否需要引入 resilience4j

**不需要**：
1. Nop 已集成 Sentinel（比 resilience4j 功能更全）
2. `nop-commons` 自带 `IRateLimiter`（Guava RateLimiter）
3. Worker 级并发是 Semaphore 语义，JDK 原生即可

## 4. 与 snail-job 的对比

| 维度 | snail-job | nop-job 现状 | nop-job 规划 |
|------|-----------|-------------|-------------|
| Worker 并发控制 | 线程池 | 无显式控制 | Semaphore（后续 plan） |
| Job 并发控制 | 阻塞策略 | 4 种阻塞策略（已实现） | 已满足 |
| RPC 限流 | 无 | Sentinel（已集成，未启用） | 配置启用即可 |
| 分布式限流 | 无 | Sentinel 集群流控（已集成） | 按需启用 |

## 5. 实现优先级

1. **RPC 限流启用**（配置即可，无需代码改动）：加入 `FlowControlRpcServiceInterceptor` + 配置 Sentinel 规则
2. **Worker 级 Semaphore**（代码改动）：`JobWorkerScannerImpl` 加 Semaphore
3. **集群级限流**（后续按需）：利用 Sentinel 集群流控
