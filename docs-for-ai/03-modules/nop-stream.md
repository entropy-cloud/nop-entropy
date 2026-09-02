# nop-stream 流处理引擎 Owner Doc

> 定位：nop-stream 的运维与使用契约 owner doc（item 16 交付的落点）。
> 本文只记录**已实现**的用户可见契约；架构决策记录属于平台内部设计文档，不在本文展开。

## 模块路由

| 子模块 | 职责 |
|---|---|
| `nop-stream-core` | 核心 API（DataStream、算子、状态后端契约、checkpoint 协议类型） |
| `nop-stream-runtime` | 分布式运行时（JobCoordinator / TaskManager / 控制面 RPC / 运维面） |
| `nop-stream-cep` | CEP 模式匹配 |
| `nop-stream-rocksdb` | RocksDB 状态后端 |
| `nop-stream-connector{,-batch,-jdbc,-debezium}` | 连接器族 |
| `nop-stream-flow` | XDSL 流程集成 |
| `nop-stream-fraud-example` | 复合场景示例（S1/S2 + 分布式演练） |

## 可观测性与运维面

### 分层指标标准集（P-REQ-1 / P-REQ-8）

命名规范：`nop.stream.<layer>.<name>`（snake_case；Prometheus 输出时 `.` → `_`）。指标总是注册进 nop-stream 进程级组合注册表（`io.nop.stream.core.metrics.StreamMetricsRegistries`）；是否暴露由运维 HTTP 端点配置决定（见下）。**本表是指标名与语义的唯一权威名表。**

**engine 层（作业/集群/协调器）** — 更新点：JobCoordinator / CheckpointCoordinator 真实生命周期路径

| 指标名 | 类型 | 标签 | 语义 |
|---|---|---|---|
| `nop.stream.engine.nodes.active` | gauge | —（cluster 级） | 集群注册表当前活跃节点数 |
| `nop.stream.engine.checkpoints.completed` | counter | jobId | durable checkpoint 完成数 |
| `nop.stream.engine.checkpoints.failed` | counter | jobId | checkpoint 持久化失败数 |
| `nop.stream.engine.checkpoints.aborted` | counter | jobId | checkpoint 中止数（含超时/恢复中止） |
| `nop.stream.engine.checkpoint.duration` | timer | jobId | checkpoint 触发→durable 完成时长 |
| `nop.stream.engine.checkpoint.size.bytes` | gauge | jobId | 最新完成 checkpoint 大小（字节） |
| `nop.stream.engine.recoveries.total` | counter | jobId | 全局恢复（globalRecovery）次数 |

**task 层（任务）** — 更新点：TaskManager 部署/取消/失败/终态上报路径

| 指标名 | 类型 | 标签 | 语义 |
|---|---|---|---|
| `nop.stream.task.deployed.total` | counter | nodeId | 该节点已部署任务数（assignment + deployTask 两路径） |
| `nop.stream.task.cancelled.total` | counter | nodeId | 该节点被取消任务数 |
| `nop.stream.task.failures.total` | counter | nodeId | 该节点任务失败数（部署失败 + 运行失败终态） |
| `nop.stream.task.running` | gauge | nodeId | 该节点当前在跑任务数（不含已完成） |

**operator 层（算子）** — 更新点：StreamTaskInvokable 数据面热路径（LOCAL 与 REMOTE 两条执行路径均注入）

| 指标名 | 类型 | 标签 | 语义 |
|---|---|---|---|
| `nop.stream.operator.records.in.total` | counter | jobId/vertexId/subtask | 从输入通道分发进算子链的记录数（MIDDLE/SINK 角色） |
| `nop.stream.operator.records.out.total` | counter | jobId/vertexId/subtask | 经本任务 RecordWriter 发射的记录数 |
| `nop.stream.operator.processing.time` | timer | jobId/vertexId/subtask | 单记录算子链处理时长（输入分发计时） |

**io 层（输入输出）** — 更新点：source 发射点 + 跨任务 writer 发射点

| 指标名 | 类型 | 标签 | 语义 |
|---|---|---|---|
| `nop.stream.io.records.consumed.total` | counter | jobId/vertexId/subtask | source 侧喂入管线的记录数（SOURCE/SELF_CONTAINED 角色） |
| `nop.stream.io.records.emitted.total` | counter | jobId/vertexId/subtask | 发往下游任务的记录数（跨任务发射） |

**state 层（状态后端，P-REQ-8）** — 更新点：RocksDBKeyedStateBackend 打开路径（RocksDB aggregated properties 直读；个别属性在特定 RocksDB 构建下不可用时该 gauge 值为 NaN 并告警一次）

| 指标名 | 类型 | 语义 |
|---|---|---|
| `nop.stream.state.rocksdb.block.cache.usage` | gauge | block cache 已用字节 |
| `nop.stream.state.rocksdb.block.cache.pinned` | gauge | block cache pinned 字节 |
| `nop.stream.state.rocksdb.memtable.usage.total` | gauge | 全部 memtable 大小 |
| `nop.stream.state.rocksdb.estimate.num.keys` | gauge | 估算键数 |
| `nop.stream.state.rocksdb.compaction.pending.bytes` | gauge | 待 compaction 字节 |
| `nop.stream.state.rocksdb.compactions.running` | gauge | 进行中 compaction 数 |

job/cluster/node 指标族视图映射：job 族 = 任一 `jobId` 标签维度聚合；node 族 = `nodeId` 标签维度（task 层全部 + operator/io 层按部署节点归并）；cluster 族 = engine 层无 job 维度指标（`nodes.active` 等）。

### 作业进度事件监听（P-REQ-2）

- 监听接口：`io.nop.stream.runtime.event.StreamJobEventListener`（经 `JobCoordinator.addJobEventListener` 注册；监听器异常被捕获记录，不影响主路径）。
- 事件类型（`StreamJobEvent.EventType`）：`JOB_STARTED` / `CHECKPOINT_COMPLETED` / `CHECKPOINT_FAILED` / `CHECKPOINT_ABORTED` / `RECOVERY_STARTED` / `RECOVERY_COMPLETED` / `JOB_FAILED` / `JOB_CANCELED` / `JOB_FINISHED` / `JOB_DEGRADED`。
- 事件负载：jobId、类型、时间戳、checkpointId（如适用）、durationMs、sizeBytes、cause（失败原因）。
- 内建 `LoggingJobEventListener`：每事件一行 `nop-stream job event:` 前缀日志（多 JVM 集群子进程日志可按此前缀检索）。
- 「进度」等价物：连续流模型下无批次边界，CHECKPOINT_* 与 RECOVERY_* 事件流即进度锚点。

### `/metrics` 暴露与 metrics 配置（P-REQ-3 / P-REQ-4）

- **运维 HTTP 端点**（`io.nop.stream.runtime.ops.StreamOpsHttpServer`，JDK 内建 HttpServer，宿主于 coordinator 进程）：
  - `GET /metrics`：全部 `nop.stream.*` meter 的 Prometheus 抓取端点。默认输出 **TextFormat 0.0.4**（`text/plain; version=0.0.4`）；请求头 `Accept: application/openmetrics-text` 协商输出 **OpenMetrics** 格式（`application/openmetrics-text; version=1.0.0`，以 `# EOF` 结尾）。
  - `GET /jobs/{jobId}/checkpoints`：checkpoint 观测查询（见下节）。
  - 指标族覆盖 job（`jobId` 标签维度）/ cluster（`nop.stream.engine.nodes.active` 等）/ node（`nodeId` 标签维度）三级。
- **配置键**（默认关闭，未启用时无任何 HTTP 监听——显式关闭语义）：
  - `nop.stream.ops.http.enabled`（默认 `false`）
  - `nop.stream.ops.http.port`（默认 `8901`；`0` = 临时端口，测试用）
  - `nop.stream.ops.http.bind`（默认 `127.0.0.1`；跨机采集改为 `0.0.0.0` 并受控访问）
  - `nop.stream.ops.metrics.enabled`（默认 `true`；单独关闭指标暴露）
- **Prometheus 挂载语义**：端点启用时 PrometheusMeterRegistry 挂为进程组合注册表成员；挂载前累计的计数不回放（Prometheus 成员标准语义）——生产部署应在启动作业前启用端点。采集侧掉线重连不丢增量（组合注册表 base 成员始终累计）。
- **周期 sink**（`io.nop.stream.runtime.ops.StreamMetricsReporter`）：
  - `nop.stream.metrics.log.enabled`（默认 `false`）+ `nop.stream.metrics.log.interval-ms` + `nop.stream.metrics.log.target=stdout|file` + `nop.stream.metrics.log.file`（target=file 时必填；原子重写 `.tmp` → `ATOMIC_MOVE`）。
- **配置模板**：`nop-stream-runtime/src/main/resources/_vfs/nop/stream/conf/metrics.properties.template`（含 prometheus-pull / log / file 三类引擎内建 sink 注释样例 + JMX/PushGateway 外部接入模式备忘）。

### checkpoint 运维观测（P-REQ-6）

- **overview**（`GET /jobs/{jobId}/checkpoints` 响应的 `overview` 段）：完成/失败/中止计数、最新 checkpoint 大小与时长、最近时间戳、最新失败原因（`failureCause`）——数据来自 `CheckpointMetrics` 快照。
- **history**（同端点 `history` 段）：有界观测历史（新est 在前；默认上限 100 条，`CheckpointCoordinator.setCheckpointHistoryMaxEntries` 可调）。每条含 `checkpointId` / `status`（COMPLETED|FAILED|ABORTED）/ `triggerTimestamp` / `durationMs` / `sizeBytes` / `failureCause`（FAILED/ABORTED 必带）/ `recordedAt`。
- 历史由真实完成/失败/中止路径记录（与 durable checkpoint 存储保留策略解耦——观测面记录 vs 存储面保留）。

### REST 运维 API（P-REQ-5）

生命周期端点宿主于同一运维 HTTP 端点（`StreamOpsHttpServer`；单作业 launch 模式经 `JobCoordinatorMain` 的 `opsHttpPort` 启用，多作业模式由 `OpsJobManager` 提供 submit/stop）。错误返回结构化 body（`{"error": <CODE>, "message": ..., "status": <http>}`），未知作业 404 / 非法参数 400 / 重复提交 409——无静默空响应。

| 方法+路径 | 语义 | 成功响应 |
|---|---|---|
| `GET /jobs` | 运行中作业列表（本进程受理的全部作业 + 状态） | `{jobs: [{jobId, jobStatus, running, restartCount, fencingEpoch}]}` |
| `POST /jobs` | 提交作业（body = `JobSubmissionSpec` JSON：`{jobId, pipelineFactoryClass, params?}`）。工厂引用语义：coordinator 进程受理，经与 launch 完全一致的路径构建并启动（工厂失败 fail-fast，无 trivial 管线回落） | 201 + 作业摘要 |
| `POST /jobs/{jobId}/stop?mode=CANCEL\|DRAIN` | 停止作业（四态终止模式的 CANCEL/DRAIN 入口；SUSPEND/EXPORT_SAVEPOINT 走 coordinator RPC，REST 传参即 400；`RECOVERING` 窗口内返回 409 `JOB_STATE_CONFLICT`，恢复完成后重试） | 200 + 作业摘要 |
| `GET /jobs/{jobId}` | 作业详情：逻辑健康状态（`health`，见下节）、jobStatus、failureCause、restartCount、checkpointOverview（计数/最新时长） | 200 + 详情 JSON |
| `GET /jobs/{jobId}/checkpoints` | checkpoint 观测（见上节） | 200 + overview/history |
| `GET /jobs/{jobId}/threaddump` | 线程诊断：coordinator 进程全线程栈文本 | 200 `text/plain` |

提交语义细节：重复 jobId → 409；`pipelineFactoryClass` 缺失/不存在/不实现 `ClusterPipelineFactory` → 400（显式错误，无静默回落）；body 非 JSON → 400。

### 状态重置工具（P-REQ-10）

- 工具类：`io.nop.stream.runtime.maintain.StreamStateResetTool.reset(jobId, checkpointBaseDir, sourceReplayable, clusterRegistry?)`——清理 `LocalFileCheckpointStorage` 布局 `<base>/<jobId>/`（durable checkpoint + epoch manifest + 其携带的 source cursor——位点随 durable 状态一并重置），同 jobId 重新拉起即全新重跑（可重放 source 从起点重读）。
- **拒绝语义（无静默清空）**：`sourceReplayable=false` → 显式报错（不可重放 source 重置即丢数据）；注册表存在活跃 coordinator → 显式报错（先 stop）；状态目录不存在 → 显式报错（防拼错路径静默成功）。
- 入口收敛（`StreamMaintenanceMain`，与离线 reshard 同一工具族，共享「先校验、后动作、结果报告」语义）：
  ```bash
  # reset-state：重置作业本地状态
  <java> io.nop.stream.runtime.maintain.StreamMaintenanceMain reset-state \
      jobId=<id> checkpointBaseDir=<dir> sourceReplayable=true
  # reshard：离线 max-parallelism 迁移（MaxParallelismReshardMigration）
  <java> io.nop.stream.runtime.maintain.StreamMaintenanceMain reshard \
      oldSavepointPath=<path> oldMaxParallelism=<n> newMaxParallelism=<m> outputBaseDir=<dir>
  ```

### 历史与日志生命周期治理（P-REQ-11）

治理配置（`StreamGovernanceConfig`，coordinator 侧周期扫描裁剪观测历史 + 终态作业记录；durable checkpoint 存储保留沿用 `CheckpointConfig.maxRetainedCheckpoints`，与观测面解耦）：

| 配置键 | 默认值 | 语义 |
|---|---|---|
| `nop.stream.ops.checkpoint-history.max-entries` | `100` | checkpoint 观测历史最大条数 |
| `nop.stream.ops.checkpoint-history.retention-minutes` | `1440` | 观测历史保留时长（与条数双约束） |
| `nop.stream.ops.job-record.retention-minutes` | `1440` | 终态作业记录保留时长 |
| `nop.stream.ops.governance.cleanup-interval-ms` | `300000` | 治理扫描周期（5 分钟） |

### 逻辑健康状态机（P-REQ-7）

`io.nop.stream.runtime.health.StreamJobHealth` 七态（KS 七态参照按 nop-stream 连续流 + 全局恢复语义裁剪），由 `JobCoordinator` 真实生命周期事件驱动（start / globalRecovery / failJob / terminate / durable checkpoint 完成回调），非定时推断：

| 状态 | 语义（KS 参照） | 进入事件 |
|---|---|---|
| `CREATED` | 已构造未启动 | 初始态 |
| `RUNNING` | 正常服务 | `start()`；`DEGRADED` 经下一 durable checkpoint 完成回愈 |
| `RECOVERING` | 拓扑重建中（≈ REBALANCING） | `globalRecovery()` 开始 |
| `DEGRADED` | 仍在服务但存在未收敛故障痕迹（≈ PENDING_ERROR，重启计数 > 0） | 恢复完成 |
| `FAILED` | 终态：failJob（含恢复 cap 耗尽） | `failJob()` |
| `CANCELED` | 终态：terminate(CANCEL) | `terminate(CANCEL)` |
| `FINISHED` | 终态：terminate(DRAIN/SUSPEND) 完成 | `terminate(DRAIN/SUSPEND)` |

- **迁移合法性表**（唯一合法迁移；非法迁移 fail-fast 抛 `IllegalStateException`，非静默忽略）：`CREATED→RUNNING`；`RUNNING→{RECOVERING, FAILED, CANCELED, FINISHED}`；`RECOVERING→{DEGRADED, FAILED}`；`DEGRADED→{RUNNING, RECOVERING, CANCELED, FINISHED}`；终态无出边。
- **监听器**：`JobCoordinator.addHealthListener(JobHealthListener)`（每次合法迁移回调；监听器异常被捕获记录，不影响主路径）。进入 `DEGRADED` 同时经事件总线派发 `JOB_DEGRADED` 事件（告警路由输入）。
- **观测面**：每次迁移输出日志 `job health transition: job=<id> <from> -> <to> (cause=...)`（多 JVM 进程日志可按此前缀检索）；REST `GET /jobs/{jobId}` 响应含 `health` 字段。
- **stop 与恢复窗口**：`RECOVERING` 窗口内 stop 无合法迁移（毫秒级窗口），REST `POST /jobs/{jobId}/stop` 返回 `409 JOB_STATE_CONFLICT`（显式冲突，恢复完成后重试即可）；`failJob` 对已终态作业为可观察 no-op（WARN 日志）。

### 告警与事件外发（P-REQ-12）

- `IAlertChannel` 抽象 + `AlertService`（作业事件监听器）：按路由表把故障语义事件外发到全部配置渠道——`JOB_FAILED`→ERROR、`RECOVERY_STARTED`→WARN、`JOB_DEGRADED`→WARN（进度类事件不路由）。渠道异常按渠道记录 WARN，不影响作业控制路径。
- 内建渠道：`LoggingAlertChannel`（结构化日志，`nop-stream alert:` 前缀，默认启用）；`WebhookAlertChannel`（HTTP POST JSON，JDK HttpClient；异步有界队列投递——慢/不可达 webhook 不阻塞控制路径，队列满丢弃并 WARN）。
- 接线：`OpsJobManager`（多作业运维面，submit 时注册到每个作业）；`JobCoordinatorMain`（launch 路径，`alertWebhookUrl` 参数启用 webhook 渠道）。
- 配置键（`AlertService.fromProperties`）：

| 配置键 | 默认值 | 语义 |
|---|---|---|
| `nop.stream.alert.logging.enabled` | `true` | 日志渠道开关 |
| `nop.stream.alert.webhook.enabled` | `false` | webhook 渠道开关 |
| `nop.stream.alert.webhook.url` | —（启用时必填，缺失 fail-fast） | webhook 接收端点（http/https） |
| `nop.stream.alert.webhook.timeout-ms` | `5000` | 单次投递超时 |
| `nop.stream.alert.webhook.retries` | `2` | 失败重试次数（固定 200ms 退避） |

## 运维手册（分布式模式操作）

> 本节是 nop-stream 运维操作的权威速查（与 live 行为一致性由 gated 测试矩阵背书：REST 生命周期/指标暴露/重置重放/健康告警 e2e）。

### 启动作业

1. 预置共享存储（H2 AUTO_SERVER 库或等价 JDBC；JDBC 2PC sink 需预建数据表 + ledger 表）。
2. 先启 TM 后启 JC：`TaskManagerMain nodeId=<id> jdbcUrl=<url> topicNamespace=<ns>`；`JobCoordinatorMain jobId=<id> jdbcUrl=<url> topicNamespace=<ns> checkpointBaseDir=<dir> expectedNodeIds=tm-0,tm-1 [pipelineFactoryClass=<fqcn>] [opsHttpPort=<port>] [alertWebhookUrl=<url>]`。
3. 恢复语义：JC 启动时自动恢复最新 durable checkpoint 并推进 id counter（防 shadow-window）；`pipelineFactoryClass` 构建失败 fail-fast 不回落 trivial 管线。
4. 多作业模式（可选）：coordinator 进程内 `OpsJobManager` + REST `POST /jobs` 提交（工厂引用语义，见 REST 契约）。

### 停止作业

- REST：`POST /jobs/{jobId}/stop?mode=CANCEL`（立即取消）或 `mode=DRAIN`（终态 checkpoint 后停止）。`RECOVERING` 窗口内返回 409，恢复完成后重试。
- coordinator RPC 直连形态：`terminate(SUSPEND)`（savepoint 后挂起，可恢复）/ `terminate(EXPORT_SAVEPOINT)`（导出 savepoint，作业继续）。
- 终态：`CANCELED` / `FINISHED`（健康状态机终态，无出边）。

### 状态重置（全新重跑）

`StreamMaintenanceMain reset-state jobId=<id> checkpointBaseDir=<dir> sourceReplayable=true`（前置校验与拒绝语义见「状态重置工具」节；e2e：重置后同 jobId 从起点完整重放）。

### 恢复操作（savepoint 等价）

- **自动**：TM 失败 → FAILED 报告/租约到期 → global recovery（fencing epoch 轮转 + 重新 assignment + TM 侧从最近 durable checkpoint 恢复）；健康状态 `RUNNING → RECOVERING → DEGRADED`，下一 durable checkpoint 完成回愈 `RUNNING`。
- **跨集群恢复（stop-the-world 重启/rescale）**：停集群 → 同 jobId + 同 checkpoint 目录重拉（TM 数可变，新 generation 用更大 `fencingEpoch`）。
- **SUSPEND 恢复**：`terminate(SUSPEND)` 产生终态 savepoint；以同 jobId + 同 checkpointBaseDir 重启即从 savepoint 恢复（等价 savepoint-恢复入口）。
- **离线 reshard**：`StreamMaintenanceMain reshard oldSavepointPath=<path> oldMaxParallelism=<n> newMaxParallelism=<m> outputBaseDir=<dir>`。

### 指标采集与告警配置速查

- Prometheus：`nop.stream.ops.http.enabled=true` + `port`（默认 8901）→ `GET /metrics`（TextFormat 0.0.4 / OpenMetrics 协商）；配置模板 `nop-stream-runtime/src/main/resources/_vfs/nop/stream/conf/metrics.properties.template`。
- 周期 sink：`nop.stream.metrics.log.*`（stdout/file）。
- 告警：`nop.stream.alert.*`（见「告警与事件外发」节）；coordinator 进程日志检索锚点：`nop-stream job event:`（事件）、`job health transition:`（健康迁移）、`nop-stream alert:`（告警外发）。
- 治理：`nop.stream.ops.checkpoint-history.*` / `nop.stream.ops.job-record.*` / `nop.stream.ops.governance.cleanup-interval-ms`（见「历史与日志生命周期治理」节）。

### 故障排查速查

- `GET /jobs/{jobId}`：health/jobStatus/failureCause/restartCount/checkpointOverview 一屏定位。
- `GET /jobs/{jobId}/checkpoints`：checkpoint overview + history（失败记录含 `failureCause`）。
- `GET /jobs/{jobId}/threaddump`：coordinator 进程全线程栈。
- 指标族：engine 层（checkpoint 计数/时长/恢复数）→ task 层（部署/失败/在跑）→ operator/io 层（记录吞吐）→ state 层（RocksDB 统计），名表见「分层指标标准集」节。
