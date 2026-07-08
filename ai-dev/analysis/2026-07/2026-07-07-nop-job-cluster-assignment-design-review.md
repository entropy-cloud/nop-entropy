# nop-job 集群调度分配模型设计评估

> Status: open
> Date: 2026-07-07
> Scope: nop-job-coordinator dispatch/assignment/task-builder; nop-job-api ResourceVector; nop-job-dao NopJobTask model; nop-cluster partition assigner/discovery model
> Conclusion: 未定；当前可确认的是不宜直接做“大一统”调度重构，优先评估 Assignment 强类型扩展与 builder/strategy 分层边界。

## Context

用户提出两个问题：`Assignment` 只有 `workerInstanceId` 和 `ResourceVector cost`，是否缺少 `Map<String,Object> params` 扩展字段；分区执行场景下集群分工应如何实现，当前策略接口是否被充分利用。

本分析覆盖 `nop-job-coordinator` 的 task builder、worker assignment strategy、partition assigner 与 dispatcher 流程，并补充 `ResourceVector` 与 `NopJobTask` 持久化字段事实。结论仍为 open，因为是否扩展公共 SPI / public API 需要后续设计文档或计划接手。

## 现状

### 调度路径：builder 按 dispatchMode / executorKind 动态路由

`JobDispatcherScannerImpl.resolveTaskBuilder()` 不是硬编码枚举，而是按 bean 名动态查找，并且显式 `dispatchMode` 缺 bean 时会 fail fast：

- 非空且非 `single` 的 `dispatchMode` → `nopJobTaskBuilder_<dispatchMode>`；如果找不到，抛 `ERR_JOB_DISPATCH_MODE_NOT_IMPLEMENTED`，不 fallback
- `dispatchMode` 为空、空白或 `single` 时 → 尝试 `nopJobTaskBuilder_<executorKind>`
- `null/blank/single` 路径下仍找不到 executorKind builder 时 → `defaultTaskBuilder`

当前内置 builder 形成以下主要路径：

| 模式 | Builder | Worker选择方式 | 负载感知 | 分片/分区语义 |
|---|---|---|---|---|
| `single` / default | `DefaultJobTaskBuilder` | `workerInstanceId = null`，worker 竞争认领 | worker 侧容量守卫 | 无 |
| `bestFit` | `AdaptiveJobTaskBuilder` | `IWorkerLoadProvider` + `IWorkerAssignmentStrategy` + `LeastLoadedStrategy` | dispatcher 侧 + worker 侧 | 单 task 显式放置 |
| `partition` | `PartitionTaskBuilder` | `IDiscoveryClient` + `WeightedPartitionAssigner` | 不使用 `ResourceVector` 运行时负载；使用 `ServiceInstance.weight` 静态权重 | hash range 分区，写 `partitionRange` |
| `rpcBroadcast` / `broadcast` | `RpcBroadcastTaskBuilder` | `IDiscoveryClient` 获取全部 healthy + enabled 实例 | 不做负载筛选 | 每实例一个 task，写 `targetHost` + sharding 信息 |

### `Assignment` 当前承载信息很窄

`IWorkerAssignmentStrategy` 当前接口：

```java
public interface IWorkerAssignmentStrategy {
    AssignmentPlan assign(ResourceVector taskCost, List<WorkerLoad> workers);
}
```

`Assignment` 当前只有两个字段：

```java
@DataBean
public class Assignment {
    private String workerInstanceId;
    private ResourceVector cost;
}
```

`LeastLoadedStrategy` 会设置 `assignment.cost = taskCost`，但 `AdaptiveJobTaskBuilder` 当前只读取 `assignment.getWorkerInstanceId()`。最终写入 task 的 cost 来自本地 `taskCost`，不是 `assignment.getCost()`。因此当前主路径中 `Assignment.cost` 基本是冗余字段，也不能让策略覆写最终任务成本。

### `PartitionTaskBuilder` 是静态权重分配，不是实时负载分配

`PartitionTaskBuilder` 的关键流程：

- 从 jobParams 提取 `serviceName`
- 通过 `IDiscoveryClient.getInstances(serviceName)` 获取服务实例
- 过滤 `instance.isHealthy() && instance.isEnabled()`
- 读取 schedule 的 `partitionCount`，限制参与实例数
- 使用 `WeightedPartitionAssigner` 将 `[0, 32767]` hash 范围按 `ServiceInstance.weight` 加权切分
- 每个 task 写入 `workerInstanceId`、`shardingIndex`、`shardingTotal`、`partitionRange`

这不是简单等分；只有实例权重相同时才近似等分。但它也没有读取 `DefaultWorkerLoadProvider` 的 `reserved/available/capacity`，所以不是实时负载感知。

### `NopJobTask` 已有强类型列与 JSON 扩展

`NopJobTask` 当前已有以下 task 分派相关列：

- `workerInstanceId`
- `targetHost`
- `shardingIndex`
- `shardingTotal`
- `costCpu`
- `costMemory`
- `partitionRange`
- `priority`
- `taskPayload` JSON component

这说明很多框架内置调度参数已经有强类型持久化位置，不应优先塞进无约束 `Map<String,Object>`。

### dispatcher 会统一覆盖 cost / priority

`JobDispatcherScannerImpl` 在 builder 返回 tasks 后，会统一根据 schedule 覆盖每个 task 的 `costCpu`、`costMemory`、`priority`。因此即使 `Assignment` 后续支持 params，若想通过 strategy/builder 覆写 cost 或 priority，还必须同步调整 dispatcher 的覆盖逻辑。

## 问题分析

### P1：`Assignment` 扩展能力不足，但不宜直接以 `Map params` 作为主方案

事实问题：

- 当前 `Assignment` 无法承载 `targetHost`、`shardingIndex`、`shardingTotal`、`partitionRange` 等分派上下文
- `Assignment.cost` 当前由 strategy 设置，但没有被 `AdaptiveJobTaskBuilder` 消费
- 自定义 strategy 无法影响最终 task 的分片/路由字段

风险判断：这是扩展性与模型一致性问题，不是当前生产功能不可用问题，因此不应定为 P0。

推荐方向：优先给 `Assignment` 增加强类型字段，例如 `targetHost`、`shardingIndex`、`shardingTotal`、`partitionRange`。如果确需开放扩展，再增加 `attributes` / `params` 作为兜底，并规定框架自有字段不得放入 map。

### Design Boundary：`PartitionTaskBuilder` 不做实时负载感知，是否需要改取决于语义

当前 partition 是静态权重分配：`ServiceInstance.weight` 可表达不同节点能力，但不会按瞬时负载调整 hash range。

这未必是错误。实时负载感知 partition 可能引入新的问题：

- 分区范围随负载波动，业务侧扫描范围不稳定
- 同一个 fire 的分片计划对负载快照敏感，结果可重复性变差
- 频繁重分片可能影响幂等、补偿、问题排查

更稳妥的优化不是“默认实时负载感知”，而是：允许可选的容量过滤或低频带阻尼的权重调整，并保持当前静态权重路径作为默认。

### Design Boundary：不应强行统一所有 dispatchMode 到旧 `IWorkerAssignmentStrategy`

`IJobTaskBuilder` 和 `IWorkerAssignmentStrategy` 目前职责不同：

- `IJobTaskBuilder`：一个 fire 生成几个 task、task 形态是什么
- `IWorkerAssignmentStrategy`：在候选 worker 中选择显式放置目标

partition 还需要 hash range 切分，broadcast 的语义是“发给所有 healthy + enabled 实例”。旧 `IWorkerAssignmentStrategy` 只有 `taskCost + workers`，缺少 `dispatchMode`、`serviceName`、`partitionCount`、hash range、broadcast 语义上下文。强行复用会把语义塞进 map，降低可维护性。

更合理的方向：保持 builder/strategy 分层，必要时新增带 context 的策略 SPI，或抽出共享的 worker candidate provider。

### Future Extensibility：`ResourceVector` 维度固化是未来扩展限制

`ResourceVector` 当前是 final、不可变、只有 `cpu` 与 `memory`。如果未来需要 GPU、磁盘 IO、网络带宽等资源，现有模型不足。

但直接添加 `Map<String,Integer> extra` 会牵动：

- `fits/add/subtract/loadScore/MAX_VALUE` 语义
- JSON 序列化与 DataBean 兼容
- worker capacity metadata
- reserved cost 聚合
- 若落库则涉及 ORM protected area

因此这是 future extensibility，不应作为本轮最小改动。

## 改进建议

### 建议 1：先修正 `Assignment` 语义，而不是立即引入任意 `params`

候选最小改动：

```java
@DataBean
public class Assignment {
    private String workerInstanceId;
    private ResourceVector cost;
    private String targetHost;
    private Integer shardingIndex;
    private Integer shardingTotal;
    private String partitionRange;
    private Map<String, Object> attributes; // optional, only for extension
}
```

其中框架已有强类型列的字段优先用强类型属性，`attributes` 仅作为业务或未来扩展兜底。

同时需要明确：

- `AdaptiveJobTaskBuilder` 是否应消费 `assignment.getCost()`
- dispatcher 是否允许 builder / assignment 覆写 cost / priority
- 未知 attributes 是否应落入 `taskPayload`，还是只允许白名单映射

### 建议 2：保持 builder/strategy 分层，抽共享 candidate 逻辑

不建议把 partition/broadcast 直接改成共用旧 `IWorkerAssignmentStrategy`。可以考虑抽取：

```java
interface IWorkerCandidateProvider {
    List<WorkerLoad> getCandidates(String serviceName);
}
```

用途：

- `AdaptiveJobTaskBuilder` 获取可用 worker load
- `PartitionTaskBuilder` 可选使用 worker load 进行容量过滤或派生 weight
- `RpcBroadcastTaskBuilder` 仍保留全员广播语义，但可复用 healthy/enabled 过滤逻辑

### 建议 3：partition 只做可选容量过滤 / 稳定权重调整

可选演进路径：

1. 保持当前 `WeightedPartitionAssigner` 默认行为
2. 增加可选配置：启用 loadProvider 后，先过滤无可用容量 worker
3. 将 `WorkerLoad.capacity` 或低频平滑后的 score 转换为 `ServiceInstance.weight`
4. 保证同一 fire 生成的 range 覆盖 `[0, 32767]` 且不重叠

不建议用瞬时 `loadScore` 每轮直接重分片作为默认策略。

### 建议 4：新增 context-aware SPI 前先不要改旧接口签名

如果后续确实需要统一 bestFit/partition/broadcast 的 worker 分配策略，应新增接口而不是破坏旧接口：

```java
interface IWorkerAssignmentPlanner {
    AssignmentPlan assign(WorkerAssignmentContext context);
}
```

`WorkerAssignmentContext` 可包含：

- `dispatchMode`
- `serviceName`
- `taskCost`
- `partitionCount`
- `hashRange`
- `workers`
- `jobParams`

旧 `IWorkerAssignmentStrategy` 保留给 `bestFit`，避免破坏已有自定义 strategy。

## 最小可行实施切片

### Slice 1：coordinator 内部强类型模型修正

- 给 `Assignment` 增加强类型字段，例如 `targetHost`、`shardingIndex`、`shardingTotal`、`partitionRange`
- 调整 `AdaptiveJobTaskBuilder` 白名单消费字段
- 暂不修改 `ResourceVector`
- 暂不修改 ORM 模型
- 暂不修改 `IWorkerAssignmentStrategy` 签名
- 暂不引入无约束 `attributes`；是否需要兜底 map 放到后续设计判断

测试建议：

- 自定义 strategy 返回带白名单字段的 `Assignment`，断言 `targetHost/shardingIndex/shardingTotal/partitionRange` 正确映射到 `NopJobTask`
- `Assignment.cost` 缺失时保持现有行为，或按最终语义断言 strategy cost 是否被消费
- dispatcher 端到端确认 `costCpu/costMemory/priority` 覆盖语义未被意外改变

### Slice 2：partition 可选候选过滤

- `PartitionTaskBuilder` 可选注入候选/负载 provider
- provider 存在时过滤不可用 worker；不存在时保持现有 discovery + `WeightedPartitionAssigner`
- 不改变 broadcast 全员语义

测试建议：

- 无 provider 时现有 partition 行为不变
- provider 过滤后 range 仍覆盖 `[0,32767]`
- high-load / no-capacity worker 被跳过或获得更低权重（取决于最终设计）

### Slice 3：context-aware 新 SPI（可选，需设计先行）

- 新增 `IWorkerAssignmentPlanner` 与 `WorkerAssignmentContext`
- 不破坏旧 `IWorkerAssignmentStrategy`
- 先仅让新 builder 试用，不批量改写所有现有 dispatchMode

## Protected / Plan-First 判断

- 修改 `nop-job/model/nop-job.orm.xml` 属于 ORM 模型结构变更，必须 plan-first；禁止手改 `_gen/` 或 `_*.xml` 生成物
- 修改 `nop-job-api` 的 `ResourceVector` 属于跨模块公共 API 变更，必须 plan-first，并需要迁移/兼容说明
- 修改 `IWorkerAssignmentStrategy` 方法签名虽在 `nop-job-coordinator`，但属于 Java SPI / IoC 扩展点，建议 plan-first，并提供迁移方案
- 统一改写所有 dispatchMode 属于调度语义重构，应先进入 `ai-dev/design/` 或 `ai-dev/plans/`

## 被否决或暂缓的方案

- 直接给 `Assignment` 增加无约束 `Map<String,Object> params` 并承载所有框架字段：暂缓。原因是 `NopJobTask` 已有强类型列，map 会降低类型安全。
- 强制所有 dispatchMode 共用旧 `IWorkerAssignmentStrategy`：否决。原因是旧接口上下文不足，partition/broadcast 语义不同。
- 默认让 partition 按瞬时负载动态重分片：暂缓。原因是可能破坏分区稳定性。
- 本轮扩展 `ResourceVector.extra`：暂缓。原因是涉及 public API、序列化、资源聚合和可能的 ORM 变更。

## Conclusion

- 当前可确认的问题不是“必须给 `Assignment` 加 `Map params`”，而是 `Assignment` 模型与 task 分派上下文不匹配，且 `Assignment.cost` 未被主路径消费。
- 推荐保留 `IJobTaskBuilder` 与 `IWorkerAssignmentStrategy` 分层，先做 coordinator 内部强类型字段扩展和候选获取复用，不做大一统调度重构。
- 后续若要实施，应从 Slice 1 开始；涉及 `ResourceVector`、ORM 或 SPI 签名时必须另起 design/plan。

## Open Questions

- [ ] `Assignment.cost` 是否允许策略覆写最终 task cost，还是应删除/弱化其语义？
- [ ] dispatcher 对 `costCpu/costMemory/priority` 的统一覆盖是否应保留？
- [ ] `Assignment.attributes` 是否需要 `AssignmentParamKeys` 常量或白名单映射？
- [ ] partition 的容量过滤是否应默认开启，还是只作为配置项？
- [ ] 若新增 context-aware SPI，是否应替代还是并存旧 `IWorkerAssignmentStrategy`？

## References

- `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/Assignment.java`
- `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/AssignmentPlan.java`
- `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/IWorkerAssignmentStrategy.java`
- `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/LeastLoadedStrategy.java`
- `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/AdaptiveJobTaskBuilder.java`
- `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/PartitionTaskBuilder.java`
- `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/RpcBroadcastTaskBuilder.java`
- `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/DefaultJobTaskBuilder.java`
- `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobDispatcherScannerImpl.java`
- `nop-job/nop-job-api/src/main/java/io/nop/job/api/resource/ResourceVector.java`
- `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/entity/NopJobTask.java`
- `nop-job/model/nop-job.orm.xml`
- `nop-cluster/nop-cluster-core/src/main/java/io/nop/cluster/assigner/WeightedPartitionAssigner.java`
- `nop-cluster/nop-cluster-core/src/main/java/io/nop/cluster/discovery/ServiceInstance.java`
