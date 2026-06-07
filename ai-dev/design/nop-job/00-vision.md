# nop-job 高层设计原则

**日期**：2026-04-04（更新于 2026-06-07）
**范围**：`nop-job` 子系统
**状态**：active

---

## 一、产品定位

nop-job 是 Nop 平台的分布式任务调度系统，采用**数据库驱动的 schedule/fire/task 三层模型**：

1. `JobSchedule` 负责保存调度定义和下一次触发游标。
2. `JobFire` 负责表示一次具体的触发批次。
3. `JobTask` 负责表示一次具体执行投递，即使单任务模式也保留这一层。

两套可并存的调度方案：
- **LocalJobScheduler**（`nop-job-core`）— 轻量内存调度器，适用于单机嵌入式场景。
- **Coordinator/Worker 架构**（`nop-job-coordinator` + `nop-job-worker` + `nop-job-dao`）— 数据库驱动的分布式调度系统。

核心设计决策：

1. **trigger 只负责"算时间"，不负责"保存状态"** — `CronTrigger`、`PeriodicTrigger` 等被降格为纯计算组件。
2. **`nop-job` 不内建重试编排逻辑** — 失败重试交由 `nop-retry`，`nop-job` 通过 `IJobRetryBridge` 保留桥接点。
3. **不复用任何外部项目的代码实现**，只吸收领域拆分思路：命名空间/分组、任务定义与执行批次分离、执行器与调度记录分离。
4. **旧模型**（`definition + instance + instance_his + assignment`）不再演进，由新 `schedule + fire + task` 模型取代。

## 二、成功标准

1. 用户可以通过 ORM 创建 `NopJobSchedule`，配置 cron/fixed-rate/fixed-delay 触发规则，系统自动按时生成 fire/task 并正确执行
2. schedule/fire/task 三层模型是唯一运行时模型——旧 `definition + instance + instance_his` 完全退场
3. 集群模式下，多节点通过 `IDiscoveryClient` + `PartitionAssignHelper` 动态分区，无单点，partition 重叠时乐观锁保证不丢不重
4. 失败重试通过 `IJobRetryBridge` 桥接到 `nop-retry`，`nop-job` 自身不内建任何重试逻辑
5. 四种阻塞策略（DISCARD/OVERLAY/PARALLEL/RECOVERY）可独立验证，Metrics 可在 Grafana/Prometheus 中观察

## 三、不可违反的约束

| # | 约束 | 含义 |
|---|------|------|
| 1 | **数据库是权威状态源** | 运行时任何关键状态都必须能从数据库恢复：哪些 schedule 处于启用状态、每个 schedule 的下一次触发时间、哪些 fire 正在等待或执行、哪些 task 正在运行或失败 |
| 2 | **trigger 只算时间** | `CronTrigger`、`PeriodicTrigger` 等是纯计算组件，不持有运行态。`TriggerContextImpl`、`TriggerExecutorImpl`、`DefaultJobScheduler` 等运行时状态机不再复用 |
| 3 | **job 和 retry 必须分层** | `nop-job` 只负责调度（什么时候触发、触发后生成什么 fire/task、执行结果如何落库、如何暂停/恢复/取消/超时回收）。`nop-retry` 负责重试（退避与再次执行、重试记录、死信、回调）。不得把 `maxRetryTimes`、`retryInterval` 一类字段塞进 job 定义表 |
| 4 | **先最小正确架构** | V1 先把 SINGLE 执行模式做好，数据模型预留 task 层为后续广播/分片扩展留口。V1 不引入 workflow，不引入多语言执行器体系 |
| 5 | **不复用外部代码** | 只借鉴领域拆分思路（命名空间/分组、定义与批次分离、fire/batch 与 task 分离、执行器独立于调度定义、管理面和运行面分层），不照搬任何外部项目的代码实现、mapper/handler/actor 组织方式 |

## 四、显式 Non-Goals

本系统**不做**以下事情：

| Non-Goal | 理由 |
|----------|------|
| workflow 编排 | job 是原子执行单元，编排属于更高层系统。V1 不引入 |
| 多语言执行器体系 | 增加复杂度，Nop 平台已有 `IJobInvoker` SPI，通过 `executorKind` 扩展即可 |
| 复制外部调度系统的状态机与中间对象 | 学习领域拆分思路，但实现结构自主，不引入外部项目的高复杂度 |
| job 内建 retry 字段 | 会与 `nop-retry` 形成重复系统，通过 `retryPolicyId` 桥接即可 |
| 静态分区分配表 | 不保留 `nop_job_assignment`，改用动态分区（`IDiscoveryClient` + `PartitionAssignHelper`） |

## 五、设计收敛路径

设计按以下顺序收敛，不可逆序：

1. **先定义三层领域模型**（JobSchedule/JobFire/JobTask 及其职责边界）
2. **再定义数据模型**（三张表、字段、索引）
3. **再定义核心流程**（planner/dispatch/complete/manual/pause/resume/timeout/cancel）
4. **再补集群与分区**（动态分区、HA、抖动防护）
5. **最后实现可观测**（Metrics 命名、埋点规范、管理页面）

只要这条顺序不乱，设计就不会滑入"先写 runtime 再补模型"的陷阱。

## 六、必须由人决策的决策点

以下决策不可由 AI 自行发明，必须经过显式确认：

1. 三层模型（schedule/fire/task）的职责边界变更（如合并或拆分某层）
2. 旧模型（`definition + instance + instance_his + assignment`）的完全退场时机
3. 定位变更（从"数据库驱动的分布式调度"改为其他定位）
4. `executorKind` dict 的扩展（新增执行器类型需确认路由策略）
5. 集群分区策略的变更（从动态分区改为 Leader 分配或其他）
6. V1 功能范围的扩大（如引入 workflow、广播/分片执行模式）

## 七、核心取舍

- **保留**：schedule/fire/task 三层分离、trigger 纯计算器、retry 通过桥接点外委、动态分区 + 乐观锁、四种阻塞策略
- **保留（非核心路径）**：`LocalJobScheduler`（最小化嵌入式部署选项）
- **去除**：旧模型（`definition + instance + instance_his + assignment`）、trigger 运行态（`TriggerContextImpl`/`TriggerExecutorImpl`/`DefaultJobScheduler`）、job 内建 retry 字段、静态分区分配表
- **聚焦**：数据库驱动的分布式调度 + 可恢复 + 可观察 + 边界清晰

## 八、设计不变量

以下不变量不可违反：

1. `next_fire_time` 是调度扫描的核心索引字段，必须由 trigger 纯计算器得出
2. `schedule -> fire` 的转换必须放在单事务中
3. 插入 fire 时要依赖唯一索引防重
4. schedule 的 `version` 必须参与更新，防止多节点重复计划
5. `retry_policy_id` 只是声明式桥接点，`nop-job` 自己不解释 policy 细节
6. 集群分区不需要选主——`PartitionAssignHelper` 是纯函数，每节点独立计算
7. 乐观锁是数据安全的最终兜底——partition 重叠不丢不重
8. `FIXED_DELAY` 的 `next_fire_time` 在 fire 完成后才重新计算
9. 手工触发不推进定时 trigger 的 `next_fire_time`

## 九、核心隐喻

nop-job 的运行方式：

1. **控制面**：`JobSchedule`（调度定义，持久化）
2. **批次层**：`JobFire`（触发批次，schedule 与执行之间的边界对象）
3. **投递层**：`JobTask`（具体执行投递，可独立表达分发失败/执行失败/超时/取消）
4. **执行层**：`IJobInvoker`（执行器 SPI，通过 `executorKind` 路由）
5. **重试桥接层**：`IJobRetryBridge`（默认 no-op，可选模块桥接 `nop-retry`）

即使 V1 只有单任务模式，fire/task 分层仍然保留，因为：
- 后续做广播/分片时不需要再改主模型
- `JobFire` 可以只表达批次最终结果，不被执行细节污染
- `JobTask` 可以单独表达分发失败、执行失败、超时、取消

## 十、拒绝了什么

| 方案 | 拒绝理由 |
|------|---------|
| 在旧模型（`definition + instance + instance_his + assignment`）上增量演化 | 旧模型语义不清晰：`JobDefinition` 和 `JobInstance` 没有控制面/运行面分层，`JobInstance` 同时承载待调度状态和执行状态，`NopJobInstanceHis` 只是复制历史表 |
| 把 `maxRetryTimes`、`retryInterval`、`backoffStrategy` 等字段继续放进 job 表 | 与 `nop-retry` 形成重复系统。`nop-retry` 已有完整的退避算法、死信、阻塞策略、回调机制 |
| trigger 持有运行态 | `TriggerContextImpl`/`TriggerExecutorImpl`/`DefaultJobScheduler` 等运行时状态机不复用。trigger 只做时间计算 |
| 照搬外部项目的代码实现、mapper/handler/actor 组织方式 | 只借鉴领域拆分思路，实现结构自主，避免引入外部项目的高复杂度 |
| 静态分区分配表（`nop_job_assignment`） | 改用 `IDiscoveryClient` + `PartitionAssignHelper` 动态计算分区，不需要 Leader 分配 |
| Leader Election | `PartitionAssignHelper.getMyRange()` 是纯函数、确定性函数。去掉 Leader Election 减少组件依赖、减少抖动风险 |

---

## 与其他文档的关系

- `01-architecture-baseline.md` — 架构基线：模块划分、数据模型、核心流程、API 层
- `invoker-design.md` — Invoker 路由体系
- `block-strategy-design.md` — 阻塞策略（DISCARD/OVERLAY/PARALLEL/RECOVERY）
- `cluster-ha-design.md` — 集群 HA 与动态分区
- `retry-integration-design.md` — retry 桥接
- `metrics-design.md` — Metrics 命名和埋点规范
- `rate-limiting-design.md` — 限流设计
