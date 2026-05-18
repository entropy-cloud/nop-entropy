# nop-job 集群 HA 与动态分区设计

> Status: active
> Created: 2026-05-18
> Last Verified: 2026-05-18
> Related: `ai-dev/design/nop-job/rewrite-design.md` §12, `ai-dev/analysis/2026-05-18-fault-tolerance-deep-dive.md`, `ai-dev/analysis/2026-05-18b-powerjob-vs-nop-job-fault-tolerance.md`

## 1. 决策：基于服务发现 + 纯函数分区计算，不需要选主

nop-job 当前通过 `@InjectValue("@cfg:nop.job.coordinator.assigned-partitions|")` 静态配置 partition 范围。节点失效后其 partition 上的任务不会被自动接管。

**决策**：集成 nop-cluster 的 `IDiscoveryClient` + `PartitionAssignHelper`，每个 Scanner 启动时动态计算自己的 partition 范围，集群成员变更时自动重新分配。**不需要 Leader Election**。

**不需要选主的理由**：
- `PartitionAssignHelper.getMyRange(sortedServers, myInstanceId)` 是纯函数、确定性函数
- 给定相同的排序实例列表，所有节点独立计算出一致的 partition 分配
- 没有需要单点协调的工作（如"由 Leader 统一分配 partition"）
- 去掉 Leader Election 减少组件依赖、减少抖动风险

**拒绝了**：
- 从零实现分区分配（nop-cluster 已有 `PartitionAssignHelper`）
- 用 Leader 节点统一分配 partition（增加了 Leader 职责和单点风险，且不必要）
- 依赖 Leader Election 触发分区重算（选主抖动会引发分区抖动）

## 2. nop-cluster 已用到的组件

| 组件 | 位置 | 角色 |
|------|------|------|
| `IDiscoveryClient` | `nop-cluster-core` | 服务发现：返回 `List<ServiceInstance>`，感知集群成员变化 |
| `PartitionAssignHelper` | `nop-cluster-core` | 纯函数：`getMyRange(sortedServers, myInstanceId)` → 当前实例负责的 `IntRangeBean` |
| `WeightedPartitionAssigner` | `nop-cluster-core` | 按 ServiceInstance 权重分配 partition 范围 |
| `ServiceInstance` | `nop-cluster-core` | 实现 `Comparable`（按 instanceId 排序），含 weight/healthy/enabled 属性 |

**不使用的组件**：
- `ILeaderElector` / `SysDaoLeaderElector` — 选主与分区计算无关，nop-job 的 Scanner 不需要知道谁是 Leader

## 3. 集成设计

### 3.1 核心原则：复用服务发现，不新建心跳机制

`IDiscoveryClient` 的注册机制**本身就是心跳**：
- Nacos：实例注册后定期发心跳，超时标记 unhealthy
- DB 注册表：实例定期更新 refresh_time，超期视为下线

nop-job 只需注册为 `ServiceInstance`，服务发现的基础设施负责心跳和健康检查。nop-job 层面不需要新建心跳表或额外的健康检查机制。

### 3.2 Scanner 分区刷新流程

每个 Scanner（Planner/Dispatcher/TimeoutChecker/CompletionProcessor/Worker）：

```
启动:
  1. 注册到 IDiscoveryClient（服务名如 "nop-job-coordinator"）
     → 注册动作本身就是心跳的起点，服务发现基础设施接管后续续期
  2. 首次计算分区:
     a. instances = discoveryClient.getInstances("nop-job-coordinator")
     b. healthyInstances = filter(instances, i -> i.healthy && i.enabled)
     c. sortedInstances = sort(healthyInstances)  // ServiceInstance 已实现 Comparable
     d. myRange = PartitionAssignHelper.getMyRange(sortedInstances, myInstanceId)
     e. assignedPartitions = IntRangeSet.from(myRange)
     f. lastSeenInstances = sortedInstances
  3. 启动扫描循环

运行:
  每次 scan 前:
    如果 距上次分区计算 > stableWindowMs 且 收到了变更事件:
      重新执行步骤 2a-2f
```

### 3.2 关键约束

1. **partition_index 在创建时固定**：Schedule 创建时 `partition_index = hash(key) % partition_count`，之后不变。Fire/Task 继承 Schedule 的 partition_index。partition_count 变更时需要迁移数据，这是独立的运维操作。
2. **所有 Scanner 使用同一服务名**：Planner/Dispatcher/Worker 都注册为同一服务，保证看到相同的实例列表。
3. **分区变更不中断当前 batch**：Scanner 在一次 scan 循环中使用的 assignedPartitions 保持不变，下次循环开始时再切换。

## 4. 实例列表抖动防护

核心风险：服务发现返回的实例列表短暂不一致（网络闪断、GC 停顿、注册中心延迟），导致不同节点计算出不同的 partition 范围。

### 4.1 风险分析

| 风险 | 严重度 | 已有防护 | 需要额外防护 |
|------|--------|---------|-------------|
| Partition 重叠（两个节点处理同一 partition） | 低（浪费 DB 查询） | `tryUpdateManyWithVersionCheck` 乐观锁保证只有一个成功 | 不需要 |
| Partition 空洞（某些 partition 无人处理） | 中（延迟处理） | 下次分区重算后自然恢复 | 需要 stabilization window |
| 频繁 rebalance 导致效率下降 | 中 | — | 需要 stabilization window |
| 服务发现视图不一致 | 中 | — | 需要确认最终一致性够用 |

### 4.2 乐观锁是最终安全网

nop-job 的所有关键 DB 操作都有乐观锁保护：

- `tryLockSchedulesForPlan()` → `tryUpdateManyWithVersionCheck` — Schedule 只被一个 Planner 锁定
- `tryLockFiresForDispatch()` → `tryUpdateManyWithVersionCheck` — Fire 只被一个 Dispatcher 锁定
- `tryLockTasksForExecute()` → `tryUpdateManyWithVersionCheck` — Task 只被一个 Worker 锁定

**即使 partition 完全重叠，两个节点扫描到相同的 Schedule/Fire/Task，也只有一个能成功 claim。另一个拿到空列表，空跑一轮。**

这意味着：**partition 分配错误不会导致数据不一致或重复执行，只会导致效率降低（空跑）或延迟（空洞）。**

### 4.3 防抖策略：Stabilization Window

分区计算不是每次 scan 都执行，也不是实例列表一变就执行：

```
分区刷新条件（同时满足）:
  1. 距上次分区计算超过 stableWindowMs（默认 30s）
  2. 上次计算后收到了 ServiceInstance 变更事件
  
不满足条件时:
  继续使用上次计算的 assignedPartitions（即使实例列表已变）
```

**为什么 stableWindowMs=30s 是安全的**：
- 服务发现的心跳机制（Nacos instance heartbeat / DB 注册表 refresh_time）通常 10-30s 检测到宕机
- 如果节点真正宕机，服务发现最多 ~30s 后标记为 unhealthy
- 再等 30s stabilization window，最多 ~60s 后 partition 重新分配
- 对于调度系统，60s 的接管延迟是可接受的（Schedule 本身有 lockTimeoutMs=60s）

### 4.4 为什么不需要 Leader 参与

`PartitionAssignHelper.getMyRange()` 的性质：
1. **纯函数**：无副作用，不依赖外部状态
2. **确定性**：给定相同的 `(sortedServers, myInstanceId, range)`，所有节点计算出相同结果
3. **对称**：没有"谁先算谁说了算"的问题

只要所有节点：
1. 从同一个 `IDiscoveryClient` 获取实例列表
2. 按 `instanceId` 排序（`ServiceInstance` 实现了 `Comparable`）
3. 传入相同的 range（`IntRangeBean.shortRange()`）

就能独立计算出一致的 partition 分配。即使短暂不一致（服务发现延迟），乐观锁也保证不丢不重。

### 4.5 服务发现一致性要求

`IDiscoveryClient.getInstances()` 需要满足：
1. **最终一致**：不同节点在同一时刻可能看到略微不同的列表，但几秒后收敛
2. **不需要强一致**：乐观锁是最终安全网
3. **healthy/enabled 过滤**： unhealthy 的实例不应参与分区计算

已满足此要求的实现：
- **Nacos** — AP 模式，实例注册 + 心跳续期，最终一致
- **DB 注册表** — 实例定期更新 refresh_time，扫描时过滤超期行，最终一致

## 5. 节点生命周期

### 5.1 节点上线

```
1. 注册到 IDiscoveryClient
2. 首次分区计算 → 得到 assignedPartitions
3. 启动 Scanner 循环
```

### 5.2 节点下线（正常）

```
1. 从 IDiscoveryClient 注销
2. 其他节点通过服务发现感知 → 标记 partitionRefreshNeeded
3. stableWindowMs 后 → partition 重新分配 → 原节点 partition 被其他节点接管
```

### 5.3 节点宕机（异常）

```
1. 无注销通知
2. 服务发现的心跳超时（Nacos ~30s / DB 注册表 ~30s）→ 实例标记为 unhealthy
3. 其他节点 stableWindowMs(30s) 后重新计算分区时过滤掉 unhealthy 实例
4. 总接管时间 ≈ 心跳超时 + stableWindowMs ≈ 60s
```

### 5.4 网络分区

```
1. 分区内的节点各自看到不同的实例列表
2. partition 可能重叠
3. 乐观锁保证不重复执行
4. 网络恢复后 partition 自然收敛
```

## 6. 配置项

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `nop.job.cluster.service-name` | `nop-job-coordinator` | 注册到服务发现的服务名 |
| `nop.job.cluster.partition-stable-window-ms` | `30000` | 分区计算防抖窗口 |
| `nop.job.cluster.partition-count` | `32767` | 总分区数（对应 `IntRangeBean.shortRange()`） |

## 7. 与 PowerJob / Snail-Job HA 方案对比

| 维度 | nop-job（本设计） | PowerJob | Snail-Job |
|------|------------------|----------|-----------|
| 是否需要选主 | ❌ 不需要 | ✅ appId 级 DB 锁选举 | ✅ Server 节点注册 |
| 分区分配方式 | `PartitionAssignHelper`（纯函数，每节点独立计算） | 无分区，appId 级单点调度 | Bucket 平均分配 |
| 分区粒度 | partition_index（~32767 个） | appId | bucket（可配置数量） |
| 动态 rebalance | ✅ 服务发现变更 + stabilization window 触发 | ✅ 选举触发 | ✅ 心跳触发 |
| 抖动防护 | stabilization window + 乐观锁 | DB 锁重试 + ping 检测 | rebalance 初始延迟 |
| 水平扩展 | ✅ 加节点自动感知 | ⚠️ 受限于 appId 级单点 | ✅ 加节点自动 rebalance |
| 数据安全 | 乐观锁（partition 重叠不丢不重） | DB 锁 + CAS | DB 锁 |

**nop-job 方案的优势**：
1. **不需要选主**：减少了组件依赖和选主抖动的风险
2. **无单点**：partition 分配不依赖任何协调者，每个节点独立计算
3. **乐观锁兜底**：即使分区计算错误，也不会导致数据不一致
4. **天然水平扩展**：加节点后所有节点自动感知并重新分配

**nop-job 方案的劣势**：
1. 接管延迟较长（~60s）：服务发现心跳超时 + stableWindowMs
2. 依赖服务发现基础设施（Nacos / DB 注册表）的正确性
