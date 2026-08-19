# nop-job REST Worker：方案 A（自建远程投递）vs 方案 B（coordinator 内嵌执行 + 既有 RPC 通道）对比分析

> Status: open
> Date: 2026-08-19
> Scope: nop-job 远程调用 Worker 模式（REST Worker）的投递通道选型
> Conclusion: **方案 B 更优**——用现有 `RpcJobInvoker` + `IRpcServiceInvoker` 在 coordinator 侧执行"worker 职责"，worker 退化为普通 Nop 服务（BizModel 方法），新增面只有 coordinator 侧一个执行器组件；方案 A 重新发明了一条并行的任务投递协议（JobTaskRequest + dispatchTask 端点 + 自定义 HTTP 客户端），概念面与代码面都更大且无额外收益。

## Context

- 用户需求：nop-job 增加一种 worker 模式——worker 只提供 REST 服务、直接通过远程调用触发、不依赖任何数据库；与现有 DB 扫描模式并存。
- 用户提出方案 B 的原始描述："dispatcher 中负责执行 worker，worker 中的 invoker 实际会调用 rpc，此时会调用到远程，直接通过这个就可以处理，也不需要远程 dispatch worker。"
- 本分析基于三套代码事实：nop-job 现有 `RpcJobInvoker`（nop-job-service，`nopJobInvoker_rpc`）、平台 RPC 抽象 `IRpcServiceInvoker` 及其两个实现（`ClusterRpcServiceInvoker` 注册中心路由 / `HttpRpcServiceInvoker` urlMap 静态路由）、以及方案 A 草稿 `ai-dev/design/nop-job/remote-worker-design.md`。

## 一、两个方案的形态

### 方案 A：自建远程投递通道（原设计草案）

```
Coordinator                                   Worker（nop-job-worker-rest 模块）
Planner → fire → Dispatcher → task(归因)
RemoteDispatchScanner                          NopJobWorkerRpc BizModel
  ├─ fetchRemoteWaitingTasks                     ├─ dispatchTask(JobTaskRequest)
  ├─ tryLock → RUNNING                           │    ├─ 按 executorKind 解析 nopJobInvoker_xxx
  ├─ RemoteWorkerClient（IHttpClient 直调）──HTTP→│    ├─ 构建 IJobExecutionContext
  ├─ 写回 SUCCESS/FAILED                         │    └─ invokeAsync → JobFireResult
  └─ 超时由 TimeoutChecker 兜底                  └─ cancelTask
```

新增面：`JobTaskRequest` DTO（nop-job-api）、`nop-job-worker-rest` 新模块（BizModel + 轻量 context builder + invoker 解析）、coordinator 侧 `RemoteJobTaskBuilder` + `RemoteDispatchScanner` + `RemoteWorkerClient`（nop-job-coordinator，新增 nop-http-api 依赖）。

### 方案 B：coordinator 内嵌执行 + 既有 RPC 通道

```
Coordinator（执行器内嵌）                        Worker（普通 Nop 服务，零 nop-job 依赖）
Planner → fire → Dispatcher → task(归因)
RemoteDispatchScanner                            任意 BizModel（默认方法名 invokeJob）
  ├─ fetchRemoteWaitingTasks                        ├─ 业务逻辑（普通 BizModel 方法）
  ├─ tryLock → RUNNING                              └─ 返回 ApiResponse
  ├─ resolve invoker: nopJobInvoker_rpc
  │    └─ RpcJobInvoker（现有组件）
  │         └─ IRpcServiceInvoker（现有抽象）
  │              ├─ ClusterRpcServiceInvoker（注册中心发现+负载均衡+重试）
  │              └─ HttpRpcServiceInvoker（urlMap 静态路由）──HTTP→
  ├─ ApiResponse → JobFireResult → 写回
  └─ 超时由 TimeoutChecker 兜底
```

新增面：coordinator 侧一个执行器组件（RemoteDispatchScanner + 执行逻辑，同方案 A 的位置），无新 DTO、无新 worker 模块、无自定义 HTTP 客户端。`RpcJobInvoker` 与 `IRpcServiceInvoker` 全部是既有组件。

## 二、逐维度对比

### 2.1 概念面与代码面

| 维度 | 方案 A | 方案 B |
|------|--------|--------|
| 新传输契约 | `JobTaskRequest`（一套新 DTO + 序列化 + 版本约定） | 无——复用 `ApiRequest`/`ApiResponse` 平台 RPC 契约（已有文档、序列化、拦截器生态） |
| 新 worker 模块 | `nop-job-worker-rest`（BizModel、context builder、invoker 解析、token 校验） | 无——worker 是任意 Nop 服务 |
| 新客户端 | `RemoteWorkerClient`（IHttpClient 直调，需自己处理服务选择/超时/错误映射） | 无——`IRpcServiceInvoker` 现成（服务发现/负载均衡/重试/白名单/拦截器） |
| coordinator 侧 | RemoteDispatchScanner + builder | RemoteDispatchScanner（含执行逻辑），同量级 |
| 文档与测试 | 新契约需全量文档化+测试 | 复用现有 RPC 契约的测试资产 |

### 2.2 worker 侧依赖与编程模型

| 维度 | 方案 A | 方案 B |
|------|--------|--------|
| worker 依赖 | nop-job-api + nop-biz（nop-job-worker-rest） | 仅 Nop 平台标准（BizModel），可跨语言 |
| worker 编程模型 | nop-job `IJobInvoker` 生态（可注册任意 nopJobInvoker_xxx bean，含 BeanMethodJobInvoker） | 普通 BizModel 方法（`@BizMutation invokeJob`），worker 不需要知道 nop-job 存在 |
| executorKind 语义 | worker 侧 invoker 路由键 | coordinator 侧 invoker 路由键（remote 模式下实际只用 rpc，或用户自定义 coordinator 侧 invoker） |
| 执行上下文 | 完整 `IJobExecutionContext`（execCount/scheduledFireTime 等全字段） | `ApiRequest` + RpcJobInvoker 注入的 job headers（jobName/jobGroup/fireId/taskId/sharding/超时；execCount 等需扩展注入） |

### 2.3 可靠性构件

| 维度 | 方案 A | 方案 B |
|------|--------|--------|
| 服务发现 | 复用 `IDiscoveryClient`（与 broadcast/partition 相同） | `ClusterRpcServiceInvoker`（`IServerChooser` 负载均衡）——平台级机制，含健康过滤 |
| 调用重试 | 无（失败即 FAILED，靠 nop-retry 桥接） | `ClusterRpcClient.retryCount`（可配），与 nop-retry 桥接叠加 |
| 调用超时 | 需自行配置 HTTP read timeout（`http-timeout-ms`） | `RpcJobInvoker` 已把 timeoutSeconds 注入 `HEADER_TIMEOUT`，目标端按 header 处理；HTTP 层超时仍可配 |
| 取消 | 新增 `cancelTask` 端点（用户已确认要支持） | `RpcJobInvoker.cancelAsync` 现成（jobParams.cancelMethod 指定远程取消方法） |
| 认证 | 自定义 token 配置 | `ApiRequest.headers` 透传（用户配置 headers 或平台认证头自动传播） |
| 静态实例列表 | jobParams.instances（新约定，牺牲 TimeoutChecker 存活判定） | `HttpRpcServiceInvoker.urlMap`（平台既有静态路由机制，serviceName → baseUrl 映射，无注册中心场景现成可用） |

### 2.4 归因/共存/超时回收（两方案相同，不构成差异）

- 任务归因 `workerInstanceId` + `fire.dispatchMode=remote` 双条件隔离 DB 模式 worker 与 bestFit 任务——两方案一致。
- TimeoutChecker 的 worker-liveness 链（`workerInstanceId` 不在 `INamingService` 存活集合 → SUSPICIOUS → TIMEOUT）对两方案同样生效。

### 2.5 性能

两方案同为 coordinator → worker 一跳 HTTP，调用路径长度等价。区别仅在于方案 B 的 HTTP 客户端是 `HttpRpcService`（平台默认）而方案 A 是自定义 IHttpClient 直调——两者底层同一 `IHttpClient`，无实质差异。

### 2.6 演进性

| 演进方向 | 方案 A | 方案 B |
|----------|--------|--------|
| 跨语言 worker | 需实现 JobTaskRequest 协议 | 任意语言实现 HTTP `/r/` 契约（ApiRequest 是 JSON）即可 |
| worker 复用 nop-job invoker 生态 | ✅ | ❌（但可混布：需要 invoker 生态的节点用 DB 模式 worker 或方案 A 模块） |
| 新调度语义接入 | 全部依赖新契约扩展 | 直接在 RpcJobInvoker/jobParams 上扩展 |
| coordinator 集群扩展 | 多节点分区分摊（乐观锁） | 同左，且 ClusterRpcServiceInvoker 天然支持多 coordinator 调用同一 worker 服务 |

### 2.7 风险与代价

| 风险 | 方案 A | 方案 B |
|------|--------|--------|
| coordinator 成为执行代理的负载 | 同 | 同（执行编排在 coordinator，两侧等价） |
| 契约漂移 | 新协议无历史包袱，但需从零验证 | 复用成熟契约，风险低 |
| 语义混淆 | dispatchTask 语义清晰（"执行一个 nop-job 任务"） | coordinator 侧执行 `nopJobInvoker_rpc` 与 DB 模式下 worker 执行同一 bean，语义一致性好——"同一执行器，不同执行位置" |
| RpcJobInvoker 位置 | 不变 | **需下沉**：RpcJobInvoker 目前在 nop-job-service，coordinator 单独部署（不带 service）时不可用——需迁移到 nop-job-coordinator（nop-job-service 继续 re-export 保持兼容），或要求 coordinator 部署必带 nop-job-service |

## 三、异步执行模型（长任务问题的修正）

### 3.1 为什么同步 RPC 不行

初版方案 B 假设"coordinator 同步等 `ApiResponse` 返回后写回"——对秒级任务成立，但对长时间 job（分钟/小时级）不成立：

1. HTTP RPC 的读超时是硬约束（平台 HTTP client 有连接/读超时配置），coordinator 不能为单个任务占用一个 HTTP 连接小时级。
2. `HEADER_TIMEOUT` 语义是"目标端执行超时"，不是"连接可以无限等"。
3. 同步占用下，coordinator 的调用线程/连接池被长任务耗尽，短任务排队饿死。

**结论**：远程执行必须是**异步模型——启动即返回句柄，轮询查询进度**。这与 PowerJob 的 TaskTracker 周期上报、snail-job 的异步执行+结果回传是同一问题的两种解法；本设计取"轮询"而非"回调"（worker 不需要知道 coordinator 地址，与同步模型的反向依赖一致）。

### 3.2 异步执行 + 进度查询模型

```
RemoteDispatchScanner（两段式，无 in-memory 状态）      Worker（普通 BizModel，零 nop-job 依赖）
┌─ 认领段 ─────────────────────────────────────────┐
│ fetchRemoteWaitingTasks(partitions)               │
│ → tryLockTasksForExecute（WAITING→CLAIMED→RUNNING）│
│ → startJob(taskId, 参数, 日志上报地址) ──HTTP──→   │ invokeJob：登记本地异步任务（键=taskId）
│   返回 taskId（= DB jobTaskId，零映射）             │ 立即返回，后台执行
└──────────────────────────────────────────────────┘
┌─ 轮询段（每扫描周期） ───────────────────────────┐
│ fetchRemoteRunningTasks(partitions, 游标分页)      │
│ → getJobStatus(taskId) ──HTTP──→                  │ getJobStatus：查本地任务 → TaskStatusBean
│   RUNNING → 忽略（可写进度 details 到 resultPayload）│
│   终态   → 写回 DB task（SUCCESS/FAILED/...）      │
│   NOT_FOUND → FAILED(remote-task-lost)            │
└──────────────────────────────────────────────────┘
```

关键决策：

| 决策 | 理由 |
|------|------|
| **远程任务句柄 = DB task 的 `jobTaskId`** | 取消/查询/日志归组全部零映射；worker 本地以 jobTaskId 为键登记异步任务 |
| **轮询责任按分区隔离，无 in-memory 集合** | 复用 `fetchRunningTasks(limit, partitions, cursor)` 游标分页（现有方法），每个 coordinator 只轮询自己分区内的 RUNNING remote 任务；认领者崩溃后内存态自然丢失，由 TimeoutChecker worker-liveness 链兜底——状态权威始终在 DB |
| **轮询周期** | 与 scanner 周期一致（默认 5s），批量查询控制 RPC 数量；不需要独立轮询线程 |
| **startJob 失败（连接级）** | task → FAILED（remote-invoke-failed），重试交给 nop-retry 桥接 |
| **执行超时** | 完全复用三层语义：`schedule.timeoutSeconds` → `executionTimeoutMs` → worker-liveness（SUSPICIOUS→TIMEOUT）。worker 迟到的终态查询结果发现 DB task 已 concurrently-finalized → 丢弃 |
| **取消** | `cancelFire` → 乐观锁更新状态 + 调 `cancelJob(taskId)` 主动中断 worker（best-effort） |

### 3.3 进度查询契约：采用 api-core 的 `TaskStatusBean`

`io.nop.api.core.beans.task.TaskStatusBean`（`nop-kernel/nop-api-core`）是平台级通用异步任务状态契约，直接采用，不新造 DTO：

| TaskStatusBean | 语义 | nop-job task 映射 |
|----------------|------|-------------------|
| `STATUS_RUNNING(1)` | 执行中 | RUNNING（忽略，继续轮询） |
| `STATUS_SUCCESS(2)` | 完成 | SUCCESS |
| `STATUS_FAILURE(3)` | 失败（`error` 承载错误） | FAILED（错误信息透传） |
| `STATUS_CANCELLED(4)` | 被取消 | CANCELED |
| `STATUS_TIMEOUT(5)` | worker 侧判定超时 | TIMEOUT |
| `STATUS_NOT_FOUND(6)` | 任务不存在（worker 重启丢失） | FAILED（remote-task-lost） |

- `details`（Map）承载**中间进度**（约定 key 如 `progress`、业务自定义指标），coordinator 可周期性透传进 `resultPayload`。
- `taskState` 承载业务状态文本（如"步骤 3/5：清洗数据"）。
- 优点：契约在 `api-core` 已存在（任何 Nop 服务都有此依赖）、JSON 序列化现成、跨语言实现契约简单（6 个状态码 + map）。
- 局限：无专用 progress 数字字段（用 details 约定补齐）；与 nop-job 的 task-status dict 是两个 enum，需要固定映射表（上表即映射契约）。

### 3.4 对方案 B 的修正：新建 `IRemoteTaskClient`，不改造 `RpcJobInvoker`

异步需求暴露后，初版方案 B 的"coordinator 内嵌执行 `RpcJobInvoker`"需要修正：

- `RpcJobInvoker.invokeAsync` 的契约是**同步等待业务结果**（返回 `JobFireResult`），与"启动即返回 + 轮询"不兼容；强行改造会破坏 DB 模式下 `executorKind=rpc`（worker 同步调用业务服务）的既有语义与测试。
- **修正**：新建 `IRemoteTaskClient`（`startJob`/`getJobStatus`/`cancelJob` 三方法），**底层通道仍复用 `IRpcServiceInvoker`**（服务发现/urlMap/重试/白名单/header 透传全部保留）——方案 B 的"复用平台 RPC 通道"结论不变，只是复用对象从 `RpcJobInvoker` 调整为"`IRpcServiceInvoker` + `ApiRequest`/`TaskStatusBean` 契约"。
- `RpcJobInvoker` **保持原位不动**（不再下沉），DB 模式继续使用；remote 模式走 `IRemoteTaskClient`。两者是不同形态下的不同客户端，不共享执行器语义。
- 同理，`RpcJobInvoker` 的上下文 header 扩展（execCount 等）移到 `IRemoteTaskClient.startJob` 的参数注入。

### 3.5 执行日志上报

**需求**：worker 执行过程中的日志需要集中收集，供调度管理面（控制台）按任务查看——对标 snail-job 的日志上报（client 批量 POST 到 server，落库可查）与 PowerJob 的 OmsLogHandler。

**平台现状**：`nop-sys` 仅有 `NopSysChangeLog`（变更日志），无任务执行日志实体；无既有日志上报服务。需要新增：

| 项 | 设计 |
|----|------|
| 上报端点 | coordinator（调度服务）开放 `NopJobTaskLogBizModel.reportTaskLog`（REST `/r/` 入口），批量接收 `[{taskId, logTime, level, message, ...}]` |
| 日志落点 | 新表 `nop_job_task_log`（taskId 索引，按 task 查询展示；ORM 变更为 plan-first 保护区，实施前需 plan） |
| worker 上报地址 | **普通 REST/RPC 配置方式**（worker 侧部署时受信配置，如 `nop.job.log.report-url`），**不随请求头下发**——避免外部攻击（恶意请求可伪造上报地址导致日志泄露/诱导 SSRF） |
| 可选性 | **日志上报与任务状态查询完全分离**：`getJobStatus` 是任务状态机必需路径；日志上报是可选增强——worker 未配置上报地址则不启用，上报失败 best-effort（本地缓冲/丢弃），**不影响任务执行与状态机** |
| taskId 关联 | 日志行以 `taskId`（= DB jobTaskId）归组，控制台按 taskId 查询展示；同一 taskId 下包含多次 startJob（重试/恢复）产生的日志 |
| worker 侧接入 | 轻量上报 client（平台提供，走普通 RPC 通道）+ 可选 SLF4J/logback appender（按 taskId 归组）；业务代码也可显式调用 |
| 失败语义 | 日志上报失败不影响任务执行（best-effort，本地缓冲重试或丢弃） |
| 备选（拒绝） | 请求头下发上报地址（SSRF/日志泄露风险）；复用 nop-sys 日志（语义不匹配）；引入消息队列（过度设计）；worker 本地日志（无法集中查看） |

## 四、结论

**方案 B 更好**，理由按重要性排序：

1. **零新协议、零新 worker 模块**：远程调用的"最后一公里"由平台既有 `IRpcServiceInvoker`（注册中心路由或 urlMap 静态路由）+ `ApiRequest`/`TaskStatusBean` 契约承担，这两个组件已被 nop-rpc/nop-auth/nop-file 等平台模块生产验证。方案 A 需要发明 `JobTaskRequest` + `dispatchTask` 端点 + 自定义客户端三条新东西，换来的只是"worker 侧保留 IJobInvoker 生态"——而这一能力在 DB 模式下已存在，REST 模式 worker 是普通服务反而更符合"worker 只提供 rest 服务"的定位。
2. **worker 零 nop-job 依赖（甚至可跨语言）**：方案 B 的 worker 就是一个普通 Nop 服务（三个 BizModel 方法），方案 A 的 worker 必须携带 nop-job-worker-rest 模块。
3. **可靠性构件免费获得**：服务发现/负载均衡/重试/白名单/拦截器/header 透传全部来自平台 RPC 栈，方案 A 需要自行实现或降级。
4. **长任务天然支持**：异步模型（启动即返回 + `TaskStatusBean` 轮询）下，worker 执行时长不受 RPC 超时限制；进度可观察（details 透传）；超时/取消复用现有三层语义。
5. **调度定义可迁移**：`dispatchMode` 决定投递通道（single/remote），同一 schedule 在两种模式间切换只改一个字段，无需改参数结构。

**对初版方案 B 的两处修正**（异步需求暴露后）：

- 不改造/下沉 `RpcJobInvoker`（其同步语义服务 DB 模式），改新建 `IRemoteTaskClient`（start/status/cancel），复用 `IRpcServiceInvoker` 通道。
- 增加日志上报接口（`NopJobTaskLogBizModel.reportTaskLog` + `nop_job_task_log` 表），上报地址为 worker 侧普通 REST/RPC 配置（受信配置，不随请求头下发），日志上报与状态查询分离、为可选行为。

**方案 A 的保留价值**：当业务方希望 REST 模式 worker 仍以 `IJobInvoker` 编程（例如把现有 DB 模式 worker 的 invoker bean 原样搬到无 DB 环境）时，方案 A 的 `nop-job-worker-rest` 是自然延伸。作为后续可选扩展，不进 V1。

**V1 落地清单（方案 B 修订版）**：

| 项 | 位置 |
|----|------|
| `RemoteDispatchScanner`（两段式：认领+startJob / 分区轮询 getJobStatus+写回） | nop-job-coordinator（第 5 个 scanner，复用 AbstractBatchScanner/store/fetchRunningTasks 游标分页） |
| `RemoteJobTaskBuilder`（dispatchMode=remote，归因 workerInstanceId/targetHost） | nop-job-coordinator（继承 AbstractServiceTaskBuilder） |
| `IRemoteTaskClient` + 实现（start/status/cancel 三方法，底层 IRpcServiceInvoker） | nop-job-coordinator |
| `NopJobTaskLogBizModel.reportTaskLog` + `nop_job_task_log` 表（上报地址 worker 侧配置，可选启用） | nop-job-service / nop-job-dao（ORM 变更，plan-first） |
| `fetchRemoteWaitingTasks` / `fetchRemoteRunningTasks`（fire.dispatchMode=remote 过滤） | nop-job-dao（IJobTaskStore 扩展） |
| `dispatch-mode.dict.yaml` 增加 remote 选项 | nop-job-meta |
| worker 侧契约文档 + 示例 BizModel（invokeJob/getJobStatus/cancelJob）+ 日志上报 client/appender（可选） | 文档 + 测试（不新增模块） |

## References

- 方案 A 草稿：`ai-dev/design/nop-job/remote-worker-design.md`
- 现有组件：`RpcJobInvoker`（`nop-job/nop-job-service/src/main/java/io/nop/job/service/executor/RpcJobInvoker.java`）、`IRpcServiceInvoker`（`nop-kernel/nop-api-core/src/main/java/io/nop/api/core/rpc/IRpcServiceInvoker.java`）、`ClusterRpcServiceInvoker` / `HttpRpcServiceInvoker`（`nop-cluster/nop-rpc-cluster/.../`）
- 平台 RPC 机制文档：`docs-for-ai/02-core-guides/rpc-and-distributed-rpc.md`
