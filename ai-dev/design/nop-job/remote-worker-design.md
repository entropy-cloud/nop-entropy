# nop-job 远程调用 Worker 模式（REST Worker）设计

**日期**：2026-08-19（更新于 2026-08-28——客户端全异步化 + 集中式轮询管理器落地，见 §3.4/§3.5/§3.11/§四；主方案定为"coordinator 复用 worker 执行链 + `executorKind=rpcPoll` 三段式 invoker"）
**范围**：`nop-job-api`、`nop-job-coordinator`、`nop-job-dao`、`nop-job-service`、`nop-job-meta`；不新增模块
**状态**：定稿（Phase 2-4 已实现并全量测试通过；2026-08-28 追加：客户端异步化 + `RpcPollTaskManager` 集中式轮询；Phase 4 日志上报 client 为可选增强，Deferred 项见计划 2254）
**灵感来源**：PowerJob v5.1.2 worker 心跳注册 + server push；snail-job v2.0.2 client 内置 RPC server + server push（详见 §二，已浓缩为设计决策）

---

## 一、设计结论

1. **新增第三种 worker 执行模式（REST 模式）**：worker 不扫描、不读取、不写入任何数据库，只提供 REST 服务（三个短 RPC 方法）。coordinator 照常把 schedule/fire/task 状态写入数据库；**coordinator 进程内嵌既有 worker 执行链**（`JobWorkerScannerImpl` + `DefaultJobInvokerResolver`），通过新增的 `RemoteJobInvoker`（`executorKind=rpcPoll`，`implements IJobInvoker`）以**三段式**远程驱动 worker 执行：`startJob` 启动并立即返回（taskId 即 DB 任务号，零映射）→ 周期轮询 `getJobStatus` 直至终态 → 取消时 `cancelJob`。**对外就是一个普通 invoker**——scanner 不感知任何远程细节。
2. **必须落库（两阶段流水线，与现有模式同构）**：dispatcher 阶段按 `fire.dispatchMode` 路由到对应 `IJobTaskBuilder` 生成 task 并落库（`fireStore.insertTasksAndMarkFireDispatching`），scanner 阶段从数据库读取 WAITING task 认领并执行。两阶段解耦——dispatcher 提交后 scanner 任何时间扫描都得到一致视图；崩溃/重启通过数据库状态恢复；与 DB worker 模式共享同一套 `nop_job_task` 表与状态机语义。**未来若需消除落库延迟（dispatcher 直接同步调用 worker），属后续优化，不在本计划范围内**。
3. **worker 是一个普通 Nop 服务，零 nop-job 依赖**：只需暴露三个 BizModel 方法——`invokeJob`（异步启动）、`getJobStatus`（状态查询）、`cancelJob`（取消），返回值契约复用平台 api-core 的 `TaskStatusBean`。任务的"调度语义"全部由 coordinator 侧持有，worker 只响应"启动一个后台任务、报告其状态、取消它"。甚至可非 Java 实现。
4. **`executorKind=rpcPoll` 标识三段式执行**：与既有 `executorKind=rpc`（单次同步 RPC 调用业务服务）并列——`rpc` 一次 RPC 完成，`rpcPoll` 由 `startJob`/`getJobStatus`/`cancelJob` 三段分离 RPC 完成（**每段都是短调用、必须在一定时间内返回**，长任务在 worker 侧后台线程执行）。**不新增 dispatchMode 值、不做 SQL 层模式隔离**：`fire.dispatchMode` 仍用既有值（single/broadcast 等）路由 task builder；task 落库后 scanner 对所有 WAITING task 统一认领，按 `fire.executorKind` 经 `DefaultJobInvokerResolver` 解析 invoker 执行。部署拓扑为"仅 coordinator 派发执行，无独立 DB worker 进程"，REST worker 不读 DB。
5. **不新增模块**：`IRpcPollTaskClient` + `DefaultRpcPollTaskClient`（三段式客户端）+ `RemoteJobInvoker`（`nopJobInvoker_rpcPoll`，内部启动 + 轮询 + 取消）放入 `nop-job-coordinator`；coordinator 装配既有 `JobWorkerScannerImpl`/`DefaultJobInvokerResolver`/`IJobExecutionContextBuilder`（来自 `nop-job-worker`）作为执行链。远程调用通道复用平台既有 `IRpcServiceInvoker`（注册中心路由或 urlMap 静态路由）+ `ApiRequest`/`TaskStatusBean` 契约。现有 `RpcJobInvoker`（`executorKind=rpc`，DB 模式 worker 同步调用业务服务）保持原位不动。
6. **执行日志上报为可选行为**：coordinator 开放 `NopJobTaskLogBizModel.reportTaskLog` 批量接口，日志按 `taskId` 关联；worker 侧通过**普通 REST/RPC 配置方式**指定上报地址（受信配置，不随请求头下发），未配置则不启用；上报失败不影响任务执行与状态机。

---

## 二、背景与动机

### 2.1 现状痛点

nop-job 的分布式模式（`nop-job-worker`）是纯 DB 轮询架构：`JobWorkerScannerImpl` 每 5 秒扫描 `nop_job_task` 表认领 WAITING 任务并本地执行。该模式对 worker 的硬性要求是**必须能访问共享数据库**，这带来三类部署约束：

1. **worker 与 coordinator 必须共库**：worker 节点必须能直连调度数据库，跨机房/跨网络分区部署困难。
2. **worker 需要完整 ORM/DAO 依赖**：即使只执行 `IJobInvoker`，也会拉入 nop-orm、JDBC 驱动等重依赖。
3. **DB 是轮询瓶颈**：worker 规模扩大时对 `nop_job_task` 表的竞争性扫描成为吞吐上限（既有分析已指出这是 DB-as-bus 哲学的固有代价）。

### 2.2 参考框架的处理模式（浓缩）

对 PowerJob v5.1.2 与 snail-job v2.0.2 源码的审查结论（浓缩为设计决策，不重复过程）：

| 设计点 | PowerJob | snail-job | 本设计吸收 |
|--------|----------|-----------|------------|
| worker 保活 | 心跳驱动 server 内存注册表（60s 超时判离线） | 心跳 + server 内存节点表（30s 剔除） | **不引入心跳**：worker 存活判定复用 nop-job 现有 `INamingService`/`IDiscoveryClient`，超时检查器已有 worker-liveness 链 |
| 任务投递 | server 选 TaskTracker 后单向 tell，可靠性靠内部状态机重派 | server 预建 gRPC 通道 + 动态代理 push，失败 failover | **归因 + DB 兜底**：任务状态权威在 DB，调用失败标记 FAILED 或由超时检查器回收 |
| 执行结果回传 | task 终态 ask 确认 + 重试队列 | client 主动 POST `/report/dispatch/result` 回调 | **三段式（start/poll/cancel）**：`startJob` 立即返回句柄，coordinator 侧 invoker 轮询 `getJobStatus` 直至终态，无回调端点 |
| worker 本地状态 | TaskTracker 写本地 H2（自治） | 无（状态全在 server DB） | **无任何本地持久状态**：worker 以 in-memory 任务表登记异步任务，状态全在 coordinator DB |
| 执行日志 | OmsLogHandler 批量上报 server | client 批量 POST `/report/log/task` 落库 | **可选日志上报**：coordinator 开放 `reportTaskLog`，按 taskId 归组 |
| 认证 | 无显式 token | 双向 token（`SNAIL_JOB_AUTH_TOKEN`） | **headers 透传**：平台 RPC 的 header 传播机制，可配置 token header |

### 2.3 为什么必须分段（同步单次 RPC 的局限）

HTTP RPC 的读超时是硬约束，coordinator 不能为单个任务占用一个 HTTP 连接分钟/小时级；`HEADER_TIMEOUT` 的语义是"目标端执行超时"，不是"连接无限等待"。而**长任务（分钟/小时级）是调度系统的常态**（类似 longTask 场景）。因此远程执行必须**分段**——每次 RPC 都是短调用、必须在一定时间内返回：

- worker 的 `invokeJob` 只负责"登记本地异步任务并立即返回"，实际执行在 worker 后台线程进行——执行时长完全不受 RPC 超时限制。
- coordinator 的轮询查询（`getJobStatus`）是短请求，超时可控。
- 取消（`cancelJob`）也是短请求，经协调链路主动中断 worker 侧执行。
- 与 PowerJob 的 TaskTracker 周期上报、snail-job 的异步执行+结果回传是同一问题的不同解法；本设计取"轮询"而非"回调"，worker 不需要知道 coordinator 地址。

### 2.4 投递通道选型（方案 A vs 方案 B）

远程"最后一公里"有两个实现路径，经对比后采用方案 B（对比过程见记录，此处保留结论）：

| | 方案 A：自建远程投递 | 方案 B：coordinator 内嵌执行 + 既有 RPC 通道（**采用**） |
|---|---|---|
| 投递机制 | coordinator 新增 `RemoteWorkerClient` 用 `IHttpClient` 直调 worker 的 nop-job 专用端点 | coordinator 侧 `IRpcPollTaskClient`（startJob/getJobStatus/cancelJob 三方法）经平台 `IRpcServiceInvoker`（注册中心路由或 urlMap 静态路由）调用 worker 的普通 BizModel 方法 |
| 新传输契约 | `JobTaskRequest` DTO + `dispatchTask` 端点 | 无——复用 `ApiRequest`/`TaskStatusBean` 平台契约 |
| worker 模块 | 需新增 `nop-job-worker-rest`（依赖 nop-job-api + nop-biz） | 无——worker 是普通 Nop 服务，零 nop-job 依赖，可跨语言 |
| 可靠性构件 | 服务选择/超时/错误映射需自建 | 服务发现、负载均衡、重试、白名单、拦截器全部来自平台 RPC 栈 |
| 静态实例列表 | jobParams.instances（新约定） | `HttpRpcServiceInvoker.urlMap`（平台既有机制） |
| 取消 | 需实现 `cancelTask` 端点 | `IRpcPollTaskClient.cancelJob` 同通道调用（见 §3.4/§3.5） |

**采纳方案 B 的决定性理由**：零新协议、零新 worker 模块、可靠性构件免费获得；同一 schedule 在 DB 模式与 REST 模式间切换只改 `executorKind`（rpc ↔ rpcPoll），无需改参数结构。代价仅是 coordinator 承担执行代理（两方案相同）。

**方案 A 的保留价值**：若未来需要 REST 模式 worker 仍以 `IJobInvoker` 编程（把现有 DB 模式 worker 的 invoker bean 原样搬到无 DB 环境），可后续新增 `nop-job-worker-rest` 作为扩展，不进 V1。

### 2.5 目标

1. 支持"worker 只提供 REST 服务、零数据库依赖"的部署形态——worker 可部署在无法访问调度数据库的网络区域。
2. 与现有 DB 扫描模式并存，同一 coordinator 集群可同时服务两种模式的 schedule，调度管理面（GraphQL BizModel、阻塞策略、日历、分片）完全复用。
3. 不引入新的协调协议（无心跳协议、无 gRPC、无消息队列），只在现有"DB 权威状态 + 确定性分区 + 乐观锁"模型上增加一个 `executorKind`（`rpcPoll`）与对应 invoker，且投递复用平台既有 RPC 抽象。

---

## 三、核心设计

### 3.1 总体架构与数据流

**两阶段流水线（与 DB worker 模式同构）**：

```
阶段 1：Dispatcher（落库）
  PlannerScanner → fire(WAITING)
    └─ DispatcherScanner.scanBatch
            ├─ fire.dispatchMode 路由到 IJobTaskBuilder（single/partition/bestFit/broadcast，既有值）
            └─ builder.buildTasks(fire) → fireStore.insertTasksAndMarkFireDispatching(fire, tasks)
                                                                                    ↓
阶段 2：Scanner（落库后认领执行，coordinator 进程内嵌既有 worker 执行链）
  JobWorkerScannerImpl.scanBatch（复用，不限制 workerId）
    ├─ fetchWaitingTasks(WAITING) → 容量/并发过滤 → tryLockTasksForExecute(WAITING→CLAIMED, CAS)
    └─ executeTask → invokerResolver.resolveInvoker(schedule, fire)
            ├─ executorKind=rpcPoll → nopJobInvoker_rpcPoll = RemoteJobInvoker
            │     └─ invokeAsync(ctx): IRpcPollTaskClient.startJob（异步）→ RpcPollTaskManager 注册
            │                         （集中式轮询注册表：定期批量 getJobStatus → 终态 resolve）
            │                         → cancelAsync(ctx): IRpcPollTaskClient.cancelJob（异步）
            └─ 其他 executorKind（rpc/test）→ 既有 invoker（不变）
    promise.whenComplete → 写回 DB task（SUCCESS/FAILED/...）
```

```mermaid
flowchart LR
    subgraph Coordinator
        P[PlannerScanner] --> F[(nop_job_fire)]
        D[DispatcherScanner<br/>按 dispatchMode 路由] --> T[(nop_job_task)]
        W[JobWorkerScannerImpl<br/>coordinator 内嵌，复用] -->|认领 + 执行| T
        I[RemoteJobInvoker<br/>nopJobInvoker_rpcPoll] -->|startJob / cancelJob| W
        M[RpcPollTaskManager<br/>集中式轮询注册表] -->|定期批量 getJobStatus| W
        I -->|register + future| M
        C[CompletionProcessor] --> F
        TC[TimeoutChecker] --> T
        L["NopJobTaskLogBizModel.reportTaskLog"]
    end
    subgraph DB[(调度数据库)]
        S[(nop_job_schedule)]
        F[(nop_job_fire)]
        T[(nop_job_task)]
        TL[(nop_job_task_log)]
    end
    subgraph Worker["Worker（普通 Nop 服务）"]
        W1["invokeJob（异步启动）"]
        W2["getJobStatus（状态查询）"]
        W3["cancelJob（取消）"]
    end
    P --> S
    D -->|按 dispatchMode 选 builder，落库| T
    I -->|IRpcPollTaskClient → IRpcServiceInvoker| W1
    I -->|轮询 TaskStatusBean| W2
    I --> W3
    W1 --> W2
    W2 -.->|可选：日志上报 client/appender| L
    L --> TL
```

流水线（REST 模式 schedule，`executorKind=rpcPoll`）：

1. **Planner**（不变）：扫描到期 schedule → 创建 fire → 推进 `next_fire_time`。
2. **Dispatcher**（不变）：扫描 WAITING fire → 按 `fire.dispatchMode`（既有值）路由到对应 builder → 创建 task 落库 → fire 置 DISPATCHING。
3. **JobWorkerScannerImpl**（复用，coordinator 内嵌装配）：扫描 WAITING task（不限制 workerId）→ CAS 认领 → `resolveInvoker` → `invokeAsync`。
4. **RemoteJobInvoker**（新增，`executorKind=rpcPoll`）：
   - `invokeAsync`：`startJob(taskId, 参数)` **异步**远程启动（worker 立即返回）→ 成功后注册到 **`RpcPollTaskManager`**（集中式轮询注册表）→ 管理器单一调度循环定期批量 `getJobStatus`（客户端异步，单表并发在途），终态（SUCCESS/FAILURE/CANCELLED/TIMEOUT/NOT_FOUND）resolve promise → scanner 写回 DB task。
   - `cancelAsync`：`cancelJob` **异步**远程中断（best-effort）。
5. **CompletionProcessor**（不变）：聚合 task 结果 → 更新 fire/schedule。
6. **TimeoutChecker**（不变）：认领者 `workerInstanceId=coordinator hostId`，liveness 链按 coordinator 正常判定；墙钟超时兜底（invoker 崩溃/轮询异常时回收）。

### 3.2 模块划分（零新增模块）

| 模块 | 变更 | 内容 |
|------|------|------|
| `nop-job-coordinator` | 扩展 | `IRpcPollTaskClient` + `DefaultRpcPollTaskClient`（**startJob/getJobStatus/cancelJob 三方法**，底层 `IRpcServiceInvoker`，**不绑定 HTTP**）、`RemoteJobInvoker`（`nopJobInvoker_rpcPoll`，`implements IJobInvoker`，内部启动 + 轮询 + 取消） |
| `nop-job-coordinator` | 依赖 | 增加 **`nop-job-worker`**（复用 `JobWorkerScannerImpl`/`DefaultJobInvokerResolver`/`DefaultJobExecutionContextBuilder`/capacity）、**`nop-rpc-cluster`**（`nopRpcServiceInvoker` bean 装配所在模块；`IRpcServiceInvoker` 接口本体在 `nop-api-core`，已核实无循环依赖） |
| `nop-job-meta` | 扩展 | `executor-kind.dict.yaml` 增加 `rpcPoll` 选项（**不增加 dispatchMode 值**） |
| `nop-job-service` | 扩展 | `NopJobTaskLogBizModel.reportTaskLog`（日志上报端点） |
| `nop-job-dao` | 扩展（ORM） | 新表 `nop_job_task_log`（plan-first 保护区实施前需独立 plan） |

**现有组件保持不动**：`RpcJobInvoker`（`executorKind=rpc`，DB 模式 worker 同步调用业务服务）、`DefaultJobInvokerResolver`、`JobWorkerScannerImpl`、`IJobTaskBuilder`/各 builder（single/broadcast/partition/bestFit）、`IJobTaskStore` 既有方法签名、`IJobTaskStore.fetchWaitingTasks` 查询等全部原位。

**coordinator 依赖**：`nop-job-coordinator` 增加 **`nop-job-worker`**（worker 执行链组件所在模块）+ **`nop-rpc-cluster`**（`nopRpcServiceInvoker` bean 装配所在模块，`ClusterRpcServiceInvoker`/`HttpRpcServiceInvoker` 按部署配置；`nop-http-api` 经其传递可达；已核实无循环依赖）。

### 3.3 任务归因与派发（无 SQL 层模式隔离）

**核心机制**：REST 模式任务由 dispatcher 按既有 dispatchMode 生成（`single` → 1 个 task；`broadcast` → N 个 task 各归因一个实例）。**不做查询级隔离**——`IJobTaskStore` 不增加任何 `fetchRemote*` 方法，`fetchWaitingTasks`/`fetchRunningTasks` 查询零改动（避免 EQL 跨实体关联陷阱与既有 DB worker 路径回归）。

- **部署拓扑**（plan 2254 修订，用户裁定）：**不存在 DB worker 与 coordinator dispatcher 同时运行**的情况——REST worker 不读 DB，coordinator 内嵌的 `JobWorkerScannerImpl` 是 WAITING task 的唯一认领者，无需"排除某类任务"的守卫。多 coordinator（脑裂）场景由 `tryLockTasksForExecute` 乐观锁统一处理。
- **执行方式选择在 invoker 链**：scanner 不判断 executorKind——`DefaultJobInvokerResolver` 按 `fire.executorKind`（fallback `schedule.executorKind`）解析 `nopJobInvoker_<executorKind>` bean。`rpcPoll` 任务由 `RemoteJobInvoker` 执行，`rpc`/`test` 任务由既有 invoker 执行。**scanner 与执行方式完全解耦：invoker 内部自行决定如何执行**（一次 RPC 或三段式轮询）。
- **workerInstanceId 语义**：创建时可为空（single）或归因目标实例（broadcast）；被 scanner 认领时由 `tryLockTasksForExecute` 既有语义覆盖为认领节点 id（coordinator hostId）——**路由不依赖 workerInstanceId**，精确路由靠 `task.targetHost`（§3.7）。
- **worker 存活判定**：无需特殊处理——认领者 `workerInstanceId=coordinator hostId`，TimeoutChecker 的 liveness 链按 coordinator 的 `AppConfig.appName()` 解析，coordinator 在集合内，判定正常；墙钟超时（`schedule.timeoutSeconds` → `executionTimeoutMs`）兜底回收异常任务。与 DB 模式 worker 完全一致。

**不变量**：REST 模式任务的执行由 `RemoteJobInvoker` 通过三段式 RPC 驱动；builder 在无法确定目标实例（broadcast 归因）时显式失败（复用 `ERR_JOB_NO_AVAILABLE_INSTANCE` 语义），不静默退化为 single。

#### 3.3.1 dispatcher 阶段：按 dispatchMode 路由（保留既有机制）

dispatcher（`JobDispatcherScannerImpl.scanBatch` → `resolveTaskBuilder(fire)` → `insertTasksAndMarkFireDispatching`）把 `fire.dispatchMode` 映射到对应 `IJobTaskBuilder`：

| dispatchMode | builder | 产物 |
|--------------|---------|------|
| `single` | DefaultJobTaskBuilder | 1 个 task，`workerInstanceId`=NULL（competing-consumer） |
| `partition` | PartitionTaskBuilder | N 个 task，各带 `partitionRange`（hash 切片） |
| `bestFit` | AdaptiveJobTaskBuilder | N 个 task，各 `workerInstanceId`=least-loaded worker |
| `broadcast` | RpcBroadcastTaskBuilder | N 个 task，各 `workerInstanceId`=健康实例 |

**不新增 `remote` 值**（plan 2254 修订，用户裁定）：REST 模式 schedule 复用既有 dispatchMode（`single` 最常用，1 个 task → 远程执行），执行方式由 `executorKind=rpcPoll` 决定，dispatchMode 保持"dispatcher 侧如何建 task"的既有语义。

**必须落库**（plan 2254 修订，用户裁定）：builder 生成的 task 先经 `insertTasksAndMarkFireDispatching` 写入 `nop_job_task`，scanner 阶段再从库中扫描认领——与 DB 模式同构的两阶段流水线。**不在 dispatcher 阶段直接同步调用 worker**（消除落库延迟的"直连优化"明确列为后续优化项，非本计划范围）。

### 3.4 Worker 侧契约（普通 BizModel，三个短方法）

worker 是任意 Nop 服务，暴露三个 BizModel 方法（方法名可配，默认如下；**每个方法都是短 RPC**，必须在一定时间内返回）：

| 方法 | 语义 | 参数 → 返回 |
|------|------|-------------|
| `invokeJob` | **异步启动**：登记本地后台任务并立即返回；实际执行在 worker 侧线程 | `ApiRequest<Object>`（业务参数 + job headers）→ `String`（taskId，= DB jobTaskId） |
| `getJobStatus` | 查询进度/结果（短请求，快速返回） | `ApiRequest`（data.instanceId）→ `TaskStatusBean` |
| `cancelJob` | 取消执行中的任务（尽力而为） | `ApiRequest`（data.instanceId）→ `boolean` |

**载荷键统一约定**：状态查询与取消的请求体统一使用 `data.instanceId`（= DB jobTaskId）——worker 侧按此键读取，不区分来源。

**客户端异步契约（2026-08-28 更新）**：coordinator 侧 `IRpcPollTaskClient` 三方法（startJob/getJobStatus/cancelJob）**全部为异步**（返回 `CompletionStage`）——RPC 调用不阻塞任何 coordinator 线程，单个挂起的 getJobStatus 不再独占轮询线程（check2 P1-2 语义从"线程池兜底"升级为"异步接口天然不占线程"）；同步校验类失败（如 serviceName 缺失）也经 future 失败透传，调用方只有一条错误通道。异步化的直接受益者是 §3.5 的集中式轮询管理器。三个方法同时接收 **`ICancelToken`**（来自 `jobCtx`/轮询条目，与 DB 模式 `RpcJobInvoker` 一致）：实现透传底层 `IRpcServiceInvoker.invokeAsync(..., cancelToken)`——token 在 RPC 在途时被取消则框架中止该调用（无需等待读超时）；可传 `null`（无取消令牌）。

**worker 侧执行模型**：`invokeJob` 收到后以 `taskId` 为键登记到本地 in-memory 任务表（`ConcurrentHashMap<taskId, 执行句柄>`），后台线程执行，执行中更新状态（RUNNING + details 进度），结束写入终态与结果。`getJobStatus` 查表返回 `TaskStatusBean`；`NOT_FOUND` 表示任务不在表内（worker 重启/从未收到）。

**`TaskStatusBean` 映射契约**（`io.nop.api.core.beans.task.TaskStatusBean`，api-core 既有类）：

| TaskStatusBean 状态 | nop-job task 处理 |
|---------------------|-------------------|
| `RUNNING(1)` | 保持 RUNNING，继续轮询；details 进度可透传 resultPayload |
| `SUCCESS(2)` | 写回 SUCCESS |
| `FAILURE(3)` | 写回 FAILED（`error` 透传错误码/消息） |
| `CANCELLED(4)` | 写回 CANCELED |
| `TIMEOUT(5)` | 写回 TIMEOUT |
| `NOT_FOUND(6)` | 写回 FAILED（`nop.err.job.remote-task-lost`） |

`details`（Map）承载**中间进度**（约定 key 如 `progress` 及业务自定义指标）；`taskState` 承载业务状态文本（如"步骤 3/5：清洗数据"）。

**框架 header 注入**（`DefaultRpcPollTaskClient.startJob` 请求携带，沿用 `NopJobApiConstants`，其中 `execCount`/`scheduledFireTime` 为本次新增常量）：`jobName`/`jobGroup`、`jobFireId`/`jobTaskId`、`shardingIndex`/`shardingTotal`、`execCount`/`scheduledFireTime`、`timeoutSeconds`（`HEADER_TIMEOUT`）、`nop-svc-target-host`（§3.7 精确路由）。

**取消接线**：远程执行需配 `executorKind=rpcPoll`（`RemoteJobInvoker.cancelAsync` → 远程 `cancelJob`）。取消链路（本计划接通）：`cancelFire`（BizModel）→ **在 `fireStore.cancelFire` 之前**用 `IJobTaskStore.findTasksByFireId` 捕获 in-flight task 快照（cancel 事务会把活动 task 全部置 CANCELED，事后加载为空）→ 取消成功后对快照中的活动任务调用 `IJobCancelHandler.cancelRunningTask`（既有组件，超时路径已在用）→ 按 executorKind 解析 invoker → `cancelAsync`（`data.instanceId`=DB taskId）→ 远程 `cancelJob`。该接线同时惠及 DB 模式（手动取消也会通知 worker 调 invoker.cancelAsync）。

**实现注记（踩坑）**：`NopJobFireBizModel` 的 taskStore 依赖**不能用 @Inject 或 beans.xml property 装配**——BizModel bean（`ioc:type="@bean:id"`）经 `BizProxyFactoryBean` 注册时，backing 实例上的 @Inject/property 注入会破坏 `BizObjectManager` 的 biz model 注册（表现为全部 `biz_*` proxy 转换失败 `nop.err.api.convert-to-type-fail`，`MODEL BEANS: []`）。因此 taskStore 与 cancelHandler 均按类型懒获取（`BeanContainer.getBeanByType(IJobTaskStore.class)` / `BeanContainer.instance().tryGetBeanByType(IJobCancelHandler.class)`，调用时取；cancelHandler 保留普通 setter 供测试反射注入 mock 覆盖）；生产环境中 coordinator 部署（app-engine.beans.xml 装配 `IJobCancelHandler`=DefaultJobCancelHandler）时取消链自动生效，未装配时取消仅置 DB 状态（best-effort 语义不变）。

### 3.5 RemoteJobInvoker 职责（三段式 start/poll/cancel，executorKind=rpcPoll）

**不新增第 5 个扫描器**。coordinator 内嵌 worker 执行链（`JobWorkerScannerImpl` + `DefaultJobInvokerResolver` + `IJobExecutionContextBuilder` + `IWorkerCapacityProvider`，来自 nop-job-worker），`JobCoordinator` 以可选注入方式挂载 `IJobWorkerScanner`（未装配 bean 则不启动 worker 扫描，行为与现状一致）。

`RemoteJobInvoker implements IJobInvoker`（bean 名 `nopJobInvoker_rpcPoll`），由 `DefaultJobInvokerResolver` 按 `fire.executorKind`（fallback `schedule.executorKind`）= `rpcPoll` 解析命中。对外就是一个普通 invoker：scanner 零感知远程细节，只做"认领 → `invokeAsync` → promise 写回"。

**poll 段委托集中式 `RpcPollTaskManager`（bean 名 `nopRpcPollTaskManager`，2026-08-28 落地）**：所有 rpcPoll 任务共用一个轮询注册表（`taskId → PollEntry`）与**单一调度循环**，不再每个任务各自持有 `scheduleWithFixedDelay` 周期任务——调度句柄/空转项不再随在途任务数线性堆积。管理器每 `poll-interval-ms` 扫一遍注册表，只处理"promise 未终结、未取消（cancelToken）、未超时（墙钟 deadline）"的活跃条目，异步批量发起 `getJobStatus`，终态 resolve 后经 `future.whenComplete` 自动移出注册表。

```
invokeAsync(jobCtx):
  # 从 ctx.attributes 取 jobTaskId/jobFireId → load task/fire/schedule
  # 段 1 start：rpcPollTaskClient.startJob(schedule, fire, task, jobCtx.cancelToken)（异步）
  #           —— worker 立即返回 taskId=DB jobTaskId；token 透传底层 RPC（在途取消即中止）
  # 段 2 poll：委托 RpcPollTaskManager.register(schedule, fire, task, cancelToken, future)
  #   管理器单一调度循环（nop.job.remote.poll-interval-ms / poll-threads）：
  #     reload task → isConcurrentlyFinalized? → 停止轮询（写回会被 scanner 丢弃）
  #     墙钟超时（schedule.timeoutSeconds）或 cancelToken 取消 → cancelJob + resolve ERROR(timeout/canceled)
  #     getJobStatus(schedule, fire, fresh, entry.cancelToken)（异步，不阻塞）→ RUNNING/UNKNOWN 继续；
  #     终态 → resolve；条目移出注册表
  # 段 3 cancel（超时/取消路径或 cancelAsync）：rpcPollTaskClient.cancelJob(..., cancelToken)（异步）
  #          —— best-effort，token 透传底层 RPC

cancelAsync(jobCtx): load task/fire/schedule → cancelJob(..., jobCtx.cancelToken)（DB 状态以乐观锁为准）
```

关键语义：

| 决策 | 理由 |
|------|------|
| **远程任务句柄 = DB task 的 `jobTaskId`** | 取消/查询/日志归组全部零映射；worker 本地以 jobTaskId 为键登记异步任务 |
| **复用 worker 执行链，不新增 scanner** | `JobWorkerScannerImpl.executeTask` 已完整实现"拉 WAITING → tryLock → loadFire/loadSchedule → invokerResolver → invokeAsync → promise 写回"，且 `fetchWaitingTasks` 默认 `enforceAttribution=false` 不限制 workerId；多 coordinator 脑裂由 `tryLockTasksForExecute` 乐观锁处理（用户裁定：不存在 DB worker 与 coordinator 并存场景，无需 SQL 隔离） |
| **分段执行：单次 RPC 必须短时返回** | 长任务（longTask 语义）在 worker 侧后台线程执行；三段式让每个 RPC 都在读超时窗口内返回；**客户端全异步**（`CompletionStage`），coordinator 不阻塞等待任一 RPC |
| **集中式轮询管理器（`RpcPollTaskManager`）** | 单一注册表管理全部在途 rpcPoll call（**未被取消/未超时/未终结的条目**）；单一调度循环定期批量异步发起 `getJobStatus`；终态/取消/超时自动移出——不再每任务一个 `scheduleWithFixedDelay`，调度项不随任务数线性堆积；check2 P1-2（单 RPC 挂起不阻塞其他轮询）从"线程池兜底"升级为"异步接口天然不占线程" |
| **cancelToken 透传底层 RPC** | 三方法与 DB 模式 `RpcJobInvoker` 一致接收 `ICancelToken` 并透传 `IRpcServiceInvoker.invokeAsync(..., cancelToken)`：token 在 RPC 在途时被取消 → 框架中止该调用（无需等待读超时）；startJob 传 `jobCtx` token、getJobStatus/cancelJob 传轮询条目 token |
| **状态权威始终在 DB** | 认领者（coordinator）崩溃后轮询自然停止，由 TimeoutChecker 墙钟超时兜底回收；worker 迟到的终态查询结果发现 DB task 已 concurrently-finalized → 丢弃 |
| **超时/取消也走三段式** | 墙钟超时与 `cancelToken.isCancelled()` 都先 `cancelJob` 远程中断（best-effort）再 resolve ERROR，错误码 `ERR_JOB_TIMEOUT`/`ERR_JOB_CANCELED` 由 `DefaultJobExecutionContextBuilder.buildResultUpdate` 识别并写回 `TASK_STATUS_TIMEOUT(50)`/`TASK_STATUS_CANCELED(60)`（DB 模式 `RpcJobInvoker` 的错误码不命中，行为不变） |
| **getJobStatus 瞬态失败** | 本轮忽略、下轮重查（短请求失败成本低）；连续失败由 TimeoutChecker 墙钟兜底 |

### 3.6 错误与超时语义（复用现有三层模型）

| 场景 | 处理 | 依据 |
|------|------|------|
| `startJob` 连接失败（拒绝/不可达/服务未注册） | task → FAILED（`nop.err.job.remote-invoke-failed`），fire 由 Completion 聚合为 FAILED | 显式失败可观测；重试经 `IJobRetryBridge` 交给 nop-retry |
| `startJob` 已发出但 worker 实际未收到（网络抖动/worker 崩溃） | task 保持 RUNNING，轮询返回 NOT_FOUND → FAILED（remote-task-lost）；或由 TimeoutChecker 墙钟超时回收 | 双保险：轮询主动发现 + 墙钟兜底 |
| `getJobStatus` 查询失败（瞬态） | 本轮忽略，下轮重查；连续失败由 TimeoutChecker 兜底 | 轮询是短请求，失败成本低 |
| 执行超时（worker 一直在跑但超过时限） | RpcPollTaskManager 墙钟判定（条目 deadline）→ `cancelJob` + ERROR(`ERR_JOB_TIMEOUT`) → 写回 `TASK_STATUS_TIMEOUT`；TimeoutChecker 的 `timeoutSeconds`/`executionTimeoutMs` 墙钟分支同时兜底 | 远程任务**不**单独豁免 liveness 链——认领者 workerInstanceId=coordinator hostId，liveness 按 coordinator appName 正常判定，与普通任务一致 |
| coordinator 在调用中重启 | task 停留在 RUNNING/CLAIMED，TimeoutChecker 按墙钟回收；worker 侧执行不受影响 | 与 DB 模式 worker 崩溃的恢复路径同构 |
| 取消（`cancelFire`） | coordinator 侧原子更新状态（既有）；同时经本计划接通的取消链主动远程中断：`cancelFire` BizModel → `IJobCancelHandler.cancelRunningTask` → 按 `executorKind` 解析 invoker（`executorKind=rpcPoll` → `RemoteJobInvoker.cancelAsync` → `cancelJob`，`data.instanceId`=DB taskId）。远程中断为 best-effort，DB 状态以乐观锁为准 | 支持取消是明确需求；远程执行与 DB 模式共用同一条取消链 |

### 3.7 目标服务选择与服务发现（targetHost header 精确路由）

复用平台 `IRpcServiceInvoker` 的两种既有实现，按部署形态选择：

| 场景 | 使用 | 配置 |
|------|------|------|
| 有注册中心（Nacos/Consul 等） | `ClusterRpcServiceInvoker`（`nopRpcServiceInvoker`） | `jobParams.serviceName` = 注册中心服务名；`allowedServiceNames` 白名单放行 |
| 无注册中心（静态实例列表） | `HttpRpcServiceInvoker` | `urlMap[serviceName] = http://host:port` 静态映射（平台既有机制），`jobParams.serviceName` 作为 key |

**目标实例精确路由（关键机制，与现有 broadcast 模式同构）**：

```
builder（single/partition/bestFit）：把目标实例 host 写入 task.targetHost（partition/bestFit 各按其归因语义）
DefaultRpcPollTaskClient（startJob/getJobStatus/cancelJob）：请求 header 注入 nop-svc-target-host = task.targetHost
平台 chooser：LoadBalanceServerChooser 过滤器链中的 SpecificServiceInstanceFilter
                按 nop-svc-target-host 只保留匹配实例 → 精确路由到该实例
```

- `nop-svc-target-host`（`ApiConstants.HEADER_SVC_TARGET_HOST`）是平台既有机制：`SpecificServiceInstanceFilter`（nop-cluster-core）默认装配于 `nopServerChooser_*` 过滤器链（rpc-cluster-defaults.beans.xml），请求带该 header 时只保留 `host.equals(instance.getHost())` 的实例，不带则不过滤。
- 与现有 `RpcBroadcastTaskBuilder` 注入 `HEADER_SVC_TARGET_HOST = task.targetHost` 的用法完全一致（broadcast 模式的 `nopJobInvoker_rpc` 精确路由即依赖此机制）。
- **选择时机分离**：builder 选实例（discovery 快照）→ 记录 targetHost；client 调用时经注册中心重新发现 + header 精确过滤——实例列表变化（worker 下线）时该次调用自然失败（无匹配实例），由调用失败路径处理，不持有过期实例引用。
- 注册中心场景 `serviceName` 必填（缺失抛 `ERR_JOB_SERVICE_NAME_REQUIRED`，显式失败）。

### 3.8 执行日志上报（可选行为）

**需求**：worker 执行过程中的日志集中收集，供调度管理面按任务查看——对标 snail-job 的日志上报与 PowerJob 的 OmsLogHandler。

| 项 | 设计 |
|----|------|
| 上报端点 | coordinator（调度服务）开放 `NopJobTaskLogBizModel.reportTaskLog`（REST `/r/` 入口），批量接收日志行（`TaskLogEntry` 列表，校验 jobTaskId/logTime/logLevel；logLevel 按 `job/log-level` dict 编码 10/20/30/40/50 落库） |
| **taskId 关联** | 日志行以 `taskId`（= DB jobTaskId）归组，控制台按 taskId 查询；同一 taskId 下可包含多次 startJob（重试/恢复）产生的日志 |
| **上报地址（worker 侧配置，防攻击）** | 普通 REST/RPC 配置方式：worker 侧部署时受信配置上报地址（如 `nop.job.log.report-url`），**不随请求头下发**——避免恶意请求伪造上报地址导致日志泄露或诱导 SSRF |
| **与状态查询分离（可选）** | `getJobStatus` 是任务状态机必需路径；日志上报是可选增强——worker 未配置上报地址则不启用；上报失败 best-effort（本地缓冲/丢弃），**不影响任务执行与状态机** |
| worker 侧接入 | 轻量上报 client `JobLogReporter`（`io.nop.job.api.log` 包，nop-job-api +`nop-http-api` 依赖；未配置 report-url/无 IHttpClient 时 `isEnabled()=false` 显式关闭，`report()` 抛 `ERR_JOB_LOG_REPORT_DISABLED`；异步上报 handle 吞错 + WARN 日志）+ 可选 SLF4J/logback appender（按 taskId 归组）；业务代码也可显式调用 |

**实现注记（Phase 4 落地）**：`reportTaskLog` 冗余展示列（jobFireId/jobScheduleId/jobName/groupId）从 task → fire 两级快照填充（`IJobTaskStore.loadTask` + `IJobFireStore.loadFire`，经 `BeanContainer` 懒取——BizModel backing 注入坑见 §3.4 注记）；task 缺失时日志行仍落库（仅 taskId，展示列留空），端点不触碰任何 task/fire/schedule 状态——与状态机完全解耦。

#### 3.8.1 日志表 `nop_job_task_log`

实体 `NopJobTaskLog`，表 `nop_job_task_log`（ORM 变更为 plan-first 保护区，实施前需独立 plan）：

| 字段 | 说明 |
|------|------|
| `job_task_log_id` | 主键（seq 生成） |
| `job_task_id` | **必填**，关联 `nop_job_task.job_task_id`，日志归组键 |
| `job_fire_id` / `job_schedule_id` / `job_name` / `job_group` | 冗余展示列（日志量大时避免 join，写入时由 coordinator 侧从 task 快照填充） |
| `log_time` | 日志产生时间（worker 侧时间戳） |
| `log_level` | 日志级别（TRACE/DEBUG/INFO/WARN/ERROR，dict `job/log-level`） |
| `log_message` | 日志文本（VARCHAR 4000） |
| `log_payload` | JSON 扩展信息（可空，如结构化业务数据） |
| 审计字段 | `version`/`created_by`/`create_time`/`updated_by`/`update_time`/`remark`（平台标准） |

索引：

| 索引 | 说明 |
|------|------|
| `ix_nop_job_task_log_task_time` | `job_task_id + log_time`，按任务按时间查日志（主查询路径） |
| `ix_nop_job_task_log_time` | `log_time`，供容量清理（保留期删除） |

写入策略：批量插入（批量上报接口攒批），低频不敏感；容量清理（按保留天数滚动删除）留作后续（见计划 Deferred 项）。

### 3.9 rerun 追溯（`source_fire_id`）

**需求**：`rerunFire` 产生的新 fire 需要精确关联到源 fire（重跑追溯、日志按"rerun 链"归组、审计"谁重跑了哪次执行"）。现状：新 fire 仅 `triggerSource=RECOVERY` + 同一 schedule，无法精确对应源 fire。

| 项 | 设计 |
|----|------|
| 新增列 | `nop_job_fire.source_fire_id`（自关联 `job_fire_id`，可空；仅 `triggerSource=RECOVERY` 的 fire 填充） |
| 填充时机 | `rerunFire` 创建恢复 fire 时，`source_fire_id = 源 fire 的 job_fire_id`（`buildRecoveryFire` 一处填充，单一写入点） |
| 索引 | `ix_nop_job_fire_source`（`source_fire_id`），支持按源 fire 查询其全部 rerun |
| 历史数据 | 不迁移（历史 RECOVERY fire 该列留空），仅新产生 |
| 使用契约 | 控制台/日志按 `source_fire_id` 聚合 rerun 链；`triggerNow`（MANUAL）不填充（无源 fire） |

### 3.10 认证与安全

- RPC header 透传：平台 RPC 栈自动转发 `authorization` 等请求头（见平台 RPC 文档的 header 传播机制），worker 侧按平台标准鉴权即可。
- 轻量 token：`jobParams.headers` 可配置自定义 token header，worker 侧校验（未配置则不校验，内网部署默认）。
- `allowedServiceNames` 白名单：`ClusterRpcServiceInvoker` 按服务名白名单放行，防止任意服务名被调用。
- 日志上报地址为受信配置（见 §3.8），不响应任何运行时下发的地址。

### 3.11 配置项

**coordinator 侧（`nop.job.remote.*`，RpcPollTaskManager / DefaultRpcPollTaskClient）**：

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `poll-interval-ms` | 5000 | getJobStatus 轮询间隔（>= 1000）；集中式管理器单一调度循环节拍 |
| `poll-threads` | 2 | 轮询调度线程池大小（[1,32]）；pollOnce 派发至此，防慢 store/阻塞实现拖累调度循环 |
| `poll-timeout-ms` | 10000 | getJobStatus/cancelJob 单次 RPC 超时（HEADER_TIMEOUT，毫秒；<=0 不注入），与 startJob 的任务级超时解耦 |

worker 执行链节拍沿用 worker 既有配置（`nop.job.worker.scan-interval-ms`/`batch-size`/`lock-timeout-ms`/`assigned-partitions`/`max-concurrency`，见 nop-job-worker）。

HTTP/RPC 调用超时与重试沿用平台 RPC 栈配置（`nop.cluster.client-retry-count` 等）与 schedule 的 `timeoutSeconds`（经 `HEADER_TIMEOUT` 传递），不新增专属超时维度。

**worker 侧（日志上报可选）**：

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `nop.job.log.report-url` | （空，不启用） | coordinator 日志上报端点地址；未配置则日志上报功能整体关闭 |
| `nop.job.log.report-interval-ms` | 5000 | 批量上报周期（缓冲攒批） |

---

## 四、拒绝了什么

| 方案 | 拒绝理由 |
|------|---------|
| **worker 心跳注册表**（PowerJob/snail-job 式：心跳驱动 server 内存节点表） | nop-job 已有 `IDiscoveryClient`/`INamingService` 服务发现体系且 TimeoutChecker 已有 worker-liveness 判定，再引入心跳协议是重复造轮子 |
| **异步回调**（worker 执行完 POST 结果回 coordinator，snail-job `/report/dispatch/result` 式） | worker 需要知道 coordinator 地址（反向发现）+ 新增回调端点 + 回调幂等处理；轮询模型下结果写回天然幂等（乐观锁），且 coordinator 重启时超时链已兜底 |
| **同步 RPC 等待执行结果** | HTTP 读超时硬约束，长任务（分钟/小时级）无法占用连接；`HEADER_TIMEOUT` 语义是目标端执行超时而非连接无限等待 |
| **消息队列做派发总线**（Kafka/RocketMQ push 任务） | 引入新基础设施，违背"最简基础设施"哲学 |
| **gRPC 协议**（snail-job 式双向流） | Nop 平台无既有 gRPC 栈，HTTP/REST 是平台一等公民，跨语言消费也更容易 |
| **REST 模式任务不留 task 行**（dispatcher 直接远程调用、不落库） | 破坏 schedule/fire/task 三层模型与 Completion/Timeout 的全部既有语义；审计与可观测性丢失 |
| **方案 A：自建远程投递通道**（`JobTaskRequest` DTO + `nop-job-worker-rest` 模块 + `RemoteWorkerClient` 直调） | 重新发明一条与平台 RPC 平行的投递协议；仅"worker 侧保留 IJobInvoker 生态"一项收益，而该生态在 DB 模式下已存在；作为后续可选扩展保留（见 §2.4） |
| **`RemoteDispatchScanner` + `dispatchMode=remote` + `fetchRemote*` SQL 隔离**（最初方案） | 用户裁定（plan 2254）：不存在 DB worker 与 coordinator dispatcher 并存场景，多 coordinator 脑裂由 `tryLockTasksForExecute` 乐观锁处理；不新增 dispatchMode 值、无 SQL 守卫——执行方式选择收敛到 invoker 链（`executorKind=rpcPoll`），scanner 层零感知 |
| **两段式 `IRemoteTaskClient`（startJob/getJobStatus）** | 无法表达取消（worker 侧长任务需要远程中断）；定名为三段式 `IRpcPollTaskClient`（startJob/getJobStatus/cancelJob），取消链与 DB 模式共用 |
| **每个 rpcPoll 任务各自持有周期调度**（RemoteJobInvoker 内 `scheduleWithFixedDelay`，V1 形态） | 调度句柄/空转项随在途任务数线性堆积、轮询节拍分散在各调用点、同步客户端下单个挂起 RPC 独占轮询线程；收敛为**集中式 `RpcPollTaskManager`**（单一注册表 + 单一调度循环 + 异步客户端单表并发在途，2026-08-28 落地） |
| **改造 `RpcJobInvoker` 以承载异步语义** | `RpcJobInvoker.invokeAsync` 的同步契约（返回 `JobFireResult`）服务 DB 模式 `executorKind=rpc`，强行改造破坏既有语义与测试；异步远程客户端独立为 `IRpcPollTaskClient`，共享的是底层 RPC 通道而非执行器语义 |
| **日志上报地址随 `startJob` 请求头下发** | 恶意请求可伪造上报地址导致日志泄露或诱导 worker SSRF；上报地址必须是 worker 部署时的受信配置 |
| **把 REST 端点放进 `nop-job-worker`** | 该模块编译依赖 `nop-job-dao`，会让 worker 部署带上 ORM/DAO 依赖链，违背"零数据库依赖"目标 |
| **coordinator 侧独立新模块** | 三段式 invoker 与现有 scanner/worker 执行链共享全部基础设施（AbstractBatchScanner/store/分区/状态机），独立成模块只会复制依赖与装配样板 |

---

## 五、与已有设计的关系

- `00-vision.md`：不违反任何约束（数据库仍为权威状态源；job/retry 仍分层；不引入外部代码；V1 聚焦 single 语义，remote 是 single 的投递通道变体）。
- `01-architecture-baseline.md`：三层模型与五层运行时不变；coordinator 内嵌 worker 执行链（nop-job-worker 依赖），执行器解析统一收敛到 `DefaultJobInvokerResolver`（按 `executorKind`）。
- `invoker-design.md`：`dispatchMode` 仍是 coordinator 侧唯一路由键（不新增值）；`executorKind` 新增 `rpcPoll` 值（dict `executor-kind.dict.yaml`）——远程模式执行器由 coordinator 侧 `RemoteJobInvoker`（`nopJobInvoker_rpcPoll`）承担，与 DB 模式 `nopJobInvoker_rpc`/`nopJobInvoker_test` 正交共存。
- `worker-assignment-design.md`：bestFit 归因语义与远程执行共享 `workerInstanceId`/`targetHost` 列；远程任务认领者 workerInstanceId=coordinator hostId，纳入统一 liveness 链。
- `timeout-and-recovery-design.md`：三层超时语义全部复用，不新增超时维度；远程任务不豁免 liveness 链（认领者是 coordinator，其 liveness 正常）。
- `cluster-ha-design.md`：无选主协调模型不变；内嵌 worker 执行链与其他 scanner 一样依赖确定性分区 + 乐观锁。
- `local-config-scheduling.md`：本地模式（无 DB）与 REST 模式（无 DB）是两条不同路径——本地模式是"调度器也内嵌"，REST 模式是"调度器在 coordinator、worker 只执行"。二者不冲突、不合并。

## 六、Open Questions

- [x] `NopJobTaskLogBizModel.reportTaskLog` 的鉴权：**已定案**——平台标准认证（REST `/r/` 入口走 Nop 标准鉴权链）+ worker 侧受信地址；可选 token 增强留作后续（见计划 Deferred）。
- [x] worker 侧 in-memory 任务表的生命周期：**已定案**——worker 重启后任务丢失（NOT_FOUND → FAILED）是设计既定语义（由超时链/轮询发现）；worker 侧磁盘持久化（PowerJob 本地 H2 式）列为 Deferred（见计划 2254）。
- [x] `IRpcPollTaskClient` 对 `IRpcServiceInvoker` 的具体调用形态：**已定案**——直接 `invokeAsync(serviceName, method, ApiRequest)`（`DefaultRpcPollTaskClient` 已实现）；预生成类型化接口列为 Deferred（见计划 2254）。
- [x] RemoteJobInvoker 轮询失败连续次数上限：**已定案**——无限重试 + TimeoutChecker 墙钟兜底（既有 `timeoutSeconds`/`executionTimeoutMs` 语义），不新增连续失败上限维度；失败可见性由墙钟超时保证。
- [x] 轮询循环归属与客户端形态：**已定案（2026-08-28）**——`IRpcPollTaskClient` 三方法全异步（`CompletionStage`，同步校验失败也经 future 透传，单错误通道）；poll 段收敛到集中式 `RpcPollTaskManager`（单一注册表管理全部在途 call——未被取消/未超时/未终结，单一调度循环定期批量异步 `getJobStatus`）；`RemoteJobInvoker` 只负责 start 注册 + cancel，不再持有每任务周期任务。
