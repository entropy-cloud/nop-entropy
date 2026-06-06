# nop-job 设计文档

> Status: active
> Created: 2026-04-04
> Updated: 2026-06-07（按 AGE owner-doc 模式重组）

本目录按 AGE（Attractor-Guided Engineering）owner-doc 模式组织，从高层设计原则到分项设计逐层展开：

1. **愿景层** — 产品定位、成功标准、约束、non-goals、设计收敛路径、设计不变量
2. **架构基线层** — 三层模型、模块划分、数据模型、核心流程、API 层
3. **执行策略层** — 阻塞策略、Invoker 路由、限流
4. **可观测层** — Metrics 命名和埋点规范
5. **集群层** — 集群 HA 与动态分区、retry 桥接

---

## 愿景层

- `00-vision.md`
  - 产品定位（schedule/fire/task 三层模型）、成功标准、不可违反的约束、显式 non-goals、设计收敛路径、必须由人决策的决策点、核心取舍、设计不变量、拒绝了什么

## 架构基线层

- `01-architecture-baseline.md`
  - 运行时分层、模块划分、核心对象职责契约（JobSchedule/JobFire/JobTask）、数据模型设计（三张表）、trigger 复用方案、核心流程设计（planner/dispatch/complete/manual/pause/resume/timeout/cancel）、API 与 Service 层

## 执行策略层

- `invoker-design.md`
  - Invoker 路由体系：`IJobInvoker`/`IJobTaskBuilder`/`IJobWorker`，`executorKind` 统一路由，广播/分片 Task 构建机制
  - 状态：草案 v5，待确认

- `block-strategy-design.md`
  - 四种阻塞策略（DISCARD/OVERLAY/PARALLEL/RECOVERY）的语义和实现
  - 状态：implemented

- `rate-limiting-design.md`
  - 限流设计与 nop 平台限流体系集成（Worker 级并发控制、RPC 限流启用）
  - 状态：draft

## 可观测层

- `metrics-design.md`
  - Micrometer Metrics 命名和埋点规范（Planner/Dispatcher/Worker/Completion 四套 Metrics）
  - 状态：implemented

## 集群层

- `cluster-ha-design.md`
  - 集群 HA 与动态分区设计：`IDiscoveryClient` + `PartitionAssignHelper`、抖动防护、节点生命周期
  - 状态：active

- `retry-integration-design.md`
  - `retryPolicyId` 对接 `nop-retry`：`IJobRetryBridge` 接口、调用时机、优先级策略、回调约定
  - 状态：设计完成

---

## 阅读顺序

**必读路径**（理解定位 → 架构 → 核心流程）：

1. `00-vision.md` — 设计原则、约束、non-goals
2. `01-architecture-baseline.md` — 架构基线、数据模型、核心流程

**按需深入**：

3. `invoker-design.md` — Invoker 路由体系
4. `block-strategy-design.md` — 阻塞策略
5. `cluster-ha-design.md` — 集群 HA 与动态分区
6. `retry-integration-design.md` — retry 桥接
7. `metrics-design.md` — Metrics 命名和埋点规范
8. `rate-limiting-design.md` — 限流设计

**扩展方向**：

- `ai-dev/lessons/02-metrics-design-convention.md` — Metrics 命名规范
