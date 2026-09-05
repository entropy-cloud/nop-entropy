# nop-stream 分布式运行手册（初稿）

**日期**：2026-09-02（item 16 深化：2026-09-03）
**范围**：nop-stream DISTRIBUTED 模式的部署、启动、checkpoint/恢复操作、kill/rescale/backpressure 演练、运维观测面
**状态**：item 16 已深化（§4 维护工具、§6 运维观测面）；运维契约的权威落点 = `docs-for-ai/03-modules/nop-stream.md`（指标名表 / REST 契约 / 健康语义 / 告警与治理配置键 / 运维手册速查），本手册保留分布式专有细节（拓扑、演练矩阵、已知边界）并与其互链
**基线**：`MiniStreamCluster` 真实多 JVM（ProcessBuilder spawn；D-GAP §3.2 约束 ⑤：不引入 K8s/容器编排）

---

## 1. 部署拓扑

```
+-------------------+       control-plane RPC (JDBC 消息表轮询)        +----------------+
| JobCoordinator(JVM)| ---- deployTask / triggerCheckpoint /            | TaskManager ×N |
|  JobCoordinatorMain|      notifyCheckpointComplete / cancelTask ---> | TaskManagerMain|
 +-------------------+      (全部 mutating 入口携带 fencing epoch；    +----------------+
                             stale epoch 在 TM RPC 边界 typed 拒绝)
                            <---- ACK / 状态报告 / 心跳 --------------
         |                                                                    |
         +----------- 共享存储：H2 AUTO_SERVER 库（注册表/消息表） ------------+
         +----------- 共享存储：LocalFileCheckpointStorage 目录（checkpoint/manifest） +
```

- **JobCoordinator（JC）**：1 个主协调进程（HA 模式可加备用 JC，经共享库租约选主）。职责：assignment 生成、remote-deploy `deployTask` RPC、周期 checkpoint 触发 + barrier 扇出、durable epoch 持久化、提交通知扇出（2PC sink 提交跨 JVM）、失败检测 + global recovery + fencing epoch 轮转。
- **TaskManager（TM）**：N 个工作进程（场景矩阵基线 N=2/3）。职责：本地重建管线（XDSL spec 或携带的 JobGraph）、数据面执行、barrier 对齐 + 状态快照、ACK 回传、2PC sink 本地提交。
- **共享 H2 库**：`jdbc:h2:file:<dir>/cluster.db;AUTO_SERVER=TRUE;MODE=MySQL`——节点注册表（`nop_stream_node`）、task_assignment（fencing 可观察面）、控制面消息表（`nop_stream_msg_queue`）。
- **共享 checkpoint 目录**：`LocalFileCheckpointStorage` 布局按 `<jobId>/` 组织，恢复身份 = (jobId, pipelineId)。默认基目录为 `${java.io.tmpdir}/nop-stream-checkpoints`（复数；embedded/RPC 执行器与本地路径同约定，可经系统属性 `nop-stream.checkpoint.storage.dir` 覆写）——存储 jobId 为消毒后的作业名（`StorageJobIds.sanitizeJobId`：非 `[a-zA-Z0-9_-]` 字符替换 + 稳定 hash 后缀保证单射，见 `checkpoint-design.md` §8.1.4）。kill/restart 等恢复演练必须显式传 `checkpointBaseDir`（默认目录禁用自动恢复——AR-1 隔离语义）。
- **数据面 topic 命名（AR-12，2026-09-04 收口）**：跨 TM 数据通道 topic 由 `StreamTopicNaming.buildTopic` 单点生成并**消毒**——格式 `nop-stream.{jobId}.{edgeId}.{srcSubtask}.{tgtSubtask}`，其中：各段非法字符（Kafka topic 合法集 `[a-zA-Z0-9._-]` 之外，如 edgeKey 的 `>`、CJK/空格/`:` 作业名）映射为 `-` 并附原段 SHA-256 前 8 hex 的 `.h<hash>` 后缀消歧；总长超 249 字符时确定性截断 + 全串 hash 后缀；**各段已合法时产物与旧格式逐字一致**（既有字面量 topic 不变）。生产者/消费者在任意 JVM 对同输入收敛到同一 topic（确定性）。注意：edge config 查表 key（`"A->B"` 原始形态）不是 topic、不消毒。控制面 topic（`nop-stream.control.*` / `nop-stream.rpc.task.*`）不经该消毒层——jobId/nodeId 非法时控制面 topic 在真 broker 部署仍会挂，登记为 Non-Blocking Follow-up（消毒机制落地后复用收口）。证明：`TestStreamTopicNaming`（单元：恒合法/确定性/消歧/恒等）+ `TestRemotePlanTopicLegality`（plan 级：真实 builder + adapter，CJK 作业名 + `A->B` edge 下 8 通道 topic/subscribeName 全合法、基数相符、edge config 查表零破坏）。

## 2. 启动顺序

1. **预置共享存储**：创建共享 H2 库（首次自动建表）；若场景含 JDBC 2PC sink，先在共享库上预建数据表 + per-chain ledger 表（DDL 来自 sink 的 `getLedgerTableDDL()`/`initializeLedgerTable()`）。
2. **先启 TM，后启 JC**：JC 启动时会等待 `expectedNodeIds` 内全部 TM 注册（超时 fail-fast）。每 TM：`TaskManagerMain nodeId=<id> jdbcUrl=<url> topicNamespace=<ns> ...`。
3. **JC 启动**：`JobCoordinatorMain jobId=<id> jdbcUrl=<url> topicNamespace=<ns> checkpointBaseDir=<dir> expectedNodeIds=tm-0,tm-1[,tm-2] [pipelineFactoryClass=<fqcn>] [fencingEpoch=<n>]`。
   - `pipelineFactoryClass`（item 14 起支持）：场景管线工厂——供给真实管线（XDSL 声明 + 可序列化 bean + checkpoint 调优 + `RemotePipelineSpec`）。**不设置时保持 Stage 42 trivial 空管线基线**（能力测试用途）。工厂构建失败即 fail-fast，**不会回落到 trivial 管线**（防止静默部署错误管线）。
   - 恢复语义：JC 启动时若 checkpoint 目录已有 durable checkpoint，自动恢复最新视图并把 checkpoint id counter 推进到其后（防止重启后发放低位 epoch id——shadow-window 问题 P0-03）。
   - 周期 checkpoint：工厂给了正 interval 时，launch 路径启动 `JobCoordinator.startPeriodicCheckpoints(interval)`（触发 + barrier 全节点扇出一体；`CheckpointCoordinator.startCheckpointScheduler()` 单独不投递 barrier）。
4. **HA 模式（可选）**：`leaderElectorEnabled=true` 时 JC 以 STANDBY 启动，经共享库租约选主后才 activation + assignment（`spawnJobCoordinator(index)` 可加备用）。

## 3. Checkpoint 与恢复操作

- **触发**：周期触发（interval 配置）或手动 `triggerCheckpoint`（JC RPC）。barrier 扇出到**所有承载 subtask 的节点**（非仅 source 节点——各 TM 的 tracker 需先注册 in-flight epoch，否则 barrier ACK 被 drop）。
- **持久化**：全部 subtask ACK → 协调器持久化 CompletedCheckpoint + EpochManifest（durable）；durable 后经 `registerDistributedCommitForwarder` 向全节点扇出 `notifyCheckpointComplete` → 各 TM 驱动本地 2PC sink `finishCommit`（提交通知丢失由 subsuming commit + ledger/manifest 幂等 guard 兜底）。
- **恢复（kill/restart）**：TM 进程死亡 → FAILED 报告或租约到期（failure detector，默认 5s tick / 15s lease）→ global recovery：fencing epoch 轮转（严格递增）→ pending checkpoint 全部 abort（timeout 类 abort 只丢弃 epoch 不取消任务）→ 重新 assignment + `deployTask`（descriptor 携带 `checkpointRestorePath`）→ TM 侧 `RemoteTaskDeploySupport` 在运行前从共享存储恢复该 subtask 状态（manifest 优先；keyed 状态按 KeyGroupRange 路由，支持 restore-time parallelism rescale）。
- **跨集群恢复（stop-the-world 重启/rescale 演练）**：停掉整个集群（SIGTERM）→ 以**相同 jobId + 相同 checkpoint 目录**重新拉起（TM 数可变；新 JC generation 用严格更大的 `fencingEpoch`）→ JC 恢复最新 durable checkpoint、TM 侧逐 subtask 恢复。文件源按 per-file 字节 cursor 续读，不重复消费。

## 4. 状态重置与维护工具（item 16）

reset 与离线 reshard 收敛为同一维护工具入口族（`StreamMaintenanceMain`），共享「先校验、后动作、结果报告」语义；拒绝语义显式报错（非静默清空）：

- **reset-state（全新重跑）**：清理指定 jobId 的本地 checkpoint 状态（durable checkpoint + epoch manifest + source cursor 随之重置），同 jobId 重新拉起即从起点重读：
  ```bash
  java -cp <classpath> io.nop.stream.runtime.maintain.StreamMaintenanceMain \
      reset-state jobId=<id> checkpointBaseDir=<dir> sourceReplayable=true
  ```
  - 前置校验：作业仍在运行（注册表活跃 coordinator）→ 报错要求先 stop；`sourceReplayable=false`（不可重放 source）→ 报错拒绝；目录不存在 → 报错（防拼错路径静默成功）。
  - e2e 证据：`TestStreamStateResetTool#resetThenReplayFromStart`（run 1 全量输出 + durable 状态 → reset → run 2 同 jobId 从起点完整重放）。
- **reshard（离线 max-parallelism 迁移）**：
  ```bash
  java -cp <classpath> io.nop.stream.runtime.maintain.StreamMaintenanceMain \
      reshard oldSavepointPath=<path> oldMaxParallelism=<n> newMaxParallelism=<m> outputBaseDir=<dir>
  ```

## 5. 演练步骤（与 gated 测试命令一一对应）

全部演练 gated：`-Dnop.stream.test.multi-jvm.enabled=true`。通用命令形如：

```bash
./mvnw test -pl nop-stream/nop-stream-fraud-example -am -T 1C \
  -Dtest=<TestClass> -Dnop.stream.test.multi-jvm.enabled=true \
  -Dsurefire.failIfNoSpecifiedTests=false
# 调试时保留产物（日志/checkpoint/输出目录，位于 <repo>/_tmp/mini-stream-cluster/<runId>/）：
#   -Dnop.stream.test.multi-jvm.preserve-artifacts=true
```

| 演练 | 测试（`io.nop.stream.fraud.scenario`） | 步骤要点 | 验收断言 |
|------|------|------|------|
| **C0 基线部署**（JC + 2 TM 无故障） | `TestS1MultiJvmE2E#s1MultiJvmBaselineDeploysFullSemanticsWithCheckpoints`、`TestS2MultiJvmE2E#s2MultiJvmBaselineBaseAndDeltaProduceExactlyOnceOutput` | 部署 S1（CDC→CEP→窗口→2PC JDBC sink，共享 H2）/ S2（file→keyBy 窗口→2PC 文件 sink，base+Delta） | sink 端 == 精确期望集（A1-1..5 / A2-1,A2-2,A2-7）；ledger 非空；durable manifest 存在 |
| **C1 kill TM + 恢复 + fencing** | `TestS1MultiJvmE2E#s1MultiJvmKillRecoverFencingExactlyOnce`、`TestS2MultiJvmE2E#s2MultiJvmKillRecoverFencingExactlyOnce` | 前置：durable manifest 已落盘 + 已有部分提交输出 → 真实 SIGTERM kill `tm-1` → restart（S2 变体：kill 后不 restart，由租约到期触发恢复，再 restart） | fencing epoch 严格递增 + 重发行 assignment；旧 epoch 控制面 mutation 在 TM RPC 边界被拒（日志可观察，R-14 模式）；恢复后终态 == 全量期望集（无重复无丢失） |
| **C2 restore-rescale（TM 2→3）** | `TestS2RestoreRescaleMultiJvmE2E` | run 1（2 TM）至 durable checkpoint + 部分输出 → 整集群优雅停止 → run 2 以相同 jobId/checkpoint 目录 + 3 TM + 更大 fencingEpoch 重启 | 3 TM 全注册；fencing 严格递增；cursor/keyed 状态/manifest 跨 JVM 恢复；终态 == 全量期望集；manifest keys == epoch 文件集 |
| **C3 backpressure 触发（限速 sink）** | `TestScenarioBackpressureMultiJvmE2E`（S1/S2 两方法） | sink bean 内节流（`ThrottledScenarioSinks`，release marker 文件控制）→ 节流期间观察 durable epoch 严格推进 → 释放节流 | 节流期间 checkpoint 无死锁持续前进；释放后终态 == 精确期望集（A1-5 / A2-7） |
| **EX 稳定性演练矩阵（item 15：soak/chaos/backpressure；item 32：observation-surface）** | `TestStabilityExerciseMultiJvm`（`io.nop.stream.fraud.scenario`，参数化联合入口：`soakS2`/`soakS1`/`soakHighRateItem28Observation`/`chaosKillLoop`/`chaosJcHaFailover`/`backpressureSteppedThrottle`/`backpressureObservationSurface`/`soakObservationSurfaceShort`） | 三装置驱动（`ExerciseLoadGenerator` 负载+期望生成 / `ChaosKillPlanner` 种子化随机 kill（含 SIGSTOP/SIGCONT 分区等价轮）/ `ExerciseSampler` 周期采样 JC 指标+**各 TM 面指标（item 32）**+通道 gauge 深度+队列 COUNT 对照+epoch+存活）；参数 `-Dexercise.*` 覆写，非法值 fail-fast；**必须** `-Dnop.stream.test.multi-jvm.preserve-artifacts=true`（入口显式校验）；产物含 `samples/`（samples.jsonl 含 tmMetrics/channelQueueDepth + chaos-events.jsonl + run-summary.json）。矩阵定义与判据 = `ai-dev/analysis/2026-09/2026-09-03-distributed-stability-exercise-report.md`（OBS 格 = item 32 plan） | 逐格判据见演练报告与 item 32 plan（无重复无丢失 / checkpoint 持续推进或饱和后恢复 / 资源泄漏信号 / fencing 严格递增 / **OBS：TM 面 wire 指标序列 + gauge 水位抬升-回落 + 节流/释放吞吐差**）；test green = pass，red = 按结局分类（hang 先核验 item 28 归属） |

**既有能力基线演练**（runtime 模块，trivial/heartbeat 管线）：`TestMiniStreamClusterProcessSpawn`（进程 spawn/健康检查）、`TestMultiJvmExactlyOnceRecovery`（heartbeat 源 kill/恢复 + fencing epoch 严格递增）、`TestMultiJvmCoordinatorFailover`（HA JC failover）。

## 6. 运维观测面（item 16 交付）

契约细节（指标名表 / REST 端点 / 健康语义 / 告警与治理配置键）的唯一权威位置 = `docs-for-ai/03-modules/nop-stream.md`；本节只列分布式模式的操作步骤与 gated 验证命令。

### 6.1 指标暴露

```bash
# JC 启动参数启用运维 HTTP 端点（默认关闭；未启用时无任何 HTTP 监听）：
JobCoordinatorMain ... opsHttpPort=8901 [opsHttpBind=127.0.0.1]
# 采集：
curl http://127.0.0.1:8901/metrics                       # TextFormat 0.0.4
curl -H 'Accept: application/openmetrics-text' \
      http://127.0.0.1:8901/metrics                       # OpenMetrics
```

- 指标族三级：job（`jobId` 标签）/ cluster（`nop_stream_engine_nodes_active` 等）/ node（`nodeId` 标签）。
- 引擎内建周期 sink（日志/文件）与配置模板：`_vfs/nop/stream/conf/metrics.properties.template`。
- gated 验证：`TestMetricsExposureE2E` / `TestObservabilityWiringE2E`（runtime 模块，默认跑）。

**TM 进程本地端点（item 32，多 JVM 观察面上收）**：

```bash
# 每 TM 启动参数启用同一运维 HTTP 端点（默认关闭；端口避开 JC 8901 与演练 JC 8931）：
TaskManagerMain ... opsHttpPort=8941          # 第 i 个 TM 取 base+i（8941/8942/...）
# 采集（task/operator/io 层指标 + 通道队列水位 gauge 直接来自 TM 进程）：
curl http://127.0.0.1:8941/metrics
```

- 形态裁定（item 32 D1）：每 TM 进程本地 pull 端点（与 Prometheus 每进程一 target 架构一致）；拒绝 TM→JC push transport（新增 RPC 契约 + JC 聚合/基数管理，收益不抵复杂度；跨机 TM 用部署侧 Prometheus federation/remote write）。裁定与契约细节的权威落点 = `docs-for-ai/03-modules/nop-stream.md`。
- TM 进程不承载作业注册表：`/jobs` 族维持既有结构化错误（list/submit/stop → 503，detail/checkpoints → 404），非统一 404。
- 默认态验证：`TestTaskManagerOpsEndpoint`（runtime 模块，默认跑：默认关闭 / 200+Prometheus 文本 / 结构化错误 / Accept 协商 / shutdown 停端点 / 真实任务执行后 wire 形态指标出现在 `/metrics`）。

**演练观察面（item 32 后的双源口径）**：

- **通道水位直测**：TM 面 `nop_stream_io_channel_queue_size{jobId,edgeId,sourceSubtask,targetSubtask}`（`RemoteInputChannel` 本地队列深度 gauge；仅激活订阅的通道注册；恢复重建经可变 holder 重绑无冻结值）。聚合关注口径 = **per-edge max**（最差通道即背压信号；sum 随通道矩阵规模缩放）——演练装置 `ExerciseSampler` 按"全部 TM 面该 gauge 的 max"采样记入 samples.jsonl 的 `channelQueueDepth` 字段。
- **COUNT 代理降级为对照/后备**：`nop_stream_msg_queue` 为 send 侧 INSERT-only 表（消费不删行），其 COUNT 只能近似；保留双记（既有 `queueDepth` 字段与泄漏启发式口径不变——jam 签名 = 深度增长 + epoch 冻结的耦合判定），gauge 为背压量化的直接证据源（OBS-2 留档对照：gauge 0 vs COUNT 3735）。
- TM 面指标快照记入 samples.jsonl 的 `tmMetrics` 字段（per-TM map，key 用 Prometheus wire 形态名，如 `nop_stream_io_emit_time_seconds_sum`）。
- **OBS 格节流档位落点裁定（live 证据 runIds `1788468752196-1`/`1788469038532-1`/`1788469782853-1`）**：S2 形态下 sink 节流不饱和通道（window 吸收输入，积压落窗口状态，gauge 恒 0/max 6）、与 source 同链的 map 节流只节流生产者——OBS-1 把档位文件接到 **window assigner**（消费顶点逐记录 `assignWindows` 路径，`SteppedThrottleWindowAssigner` 透传包装，窗口/聚合/期望输出语义不变），消费吞吐 1.96/s vs 源 ~20/s，通道真实填满（gauge → 1024 满容量）。注意：故意饱和通道期间 aligned barrier 合法地排在积压之后（不能超越数据），checkpoint 推进断言形态为"释放后恢复"（OBS-1 留档：136 次推进、节流窗 gap 45s、释放后吞吐 82/s）。
- gated 验证：`TestStabilityExerciseMultiJvm#backpressureObservationSurface`（节流档位下 gauge 水位抬升至满容量 + 释放后回落 + TM 面吞吐可量化差（1.96/s vs 82.07/s）+ TM 面 wire 指标序列 + 释放后 checkpoint 恢复 + 整程既有判据）与 `#soakObservationSurfaceShort`（短 soak：TM 面指标序列 + gauge 序列在场 + 既有 soak 判据）。

### 6.2 REST 运维 API

- 生命周期：`POST /jobs`（工厂引用提交）、`POST /jobs/{jobId}/stop?mode=CANCEL|DRAIN`、`GET /jobs`、`GET /jobs/{jobId}`（含 health/failureCause/checkpointOverview）。
- 观测诊断：`GET /jobs/{jobId}/checkpoints`（overview+history 含 failureCause）、`GET /jobs/{jobId}/threaddump`。
- gated/单测验证：`TestOpsRestLifecycleE2E`（REST 提交→运行→stop→列表/详情端到端 + 404/400/409 错误语义）。

### 6.3 健康状态与告警

- 健康七态 + 迁移表 + 监听器：见 owner doc「逻辑健康状态机」；coordinator 进程日志锚点 `job health transition:`。
- 告警外发：logging 渠道默认（`nop-stream alert:` 前缀）；webhook 渠道 launch 参数 `alertWebhookUrl=<url>`。
- gated 验证（kill TM → coordinator 进程日志断言健康迁移 + JOB_DEGRADED 事件 + RECOVERY_STARTED 告警）：

```bash
./mvnw test -pl nop-stream/nop-stream-runtime -am -T 1C \
  -Dtest=TestMultiJvmHealthStateAndAlerts \
  -Dnop.stream.test.multi-jvm.enabled=true \
  -Dsurefire.failIfNoSpecifiedTests=false
```

### 6.4 治理

- checkpoint 观测历史（条数+时长双约束）与终态作业记录的周期裁剪：`nop.stream.ops.checkpoint-history.*` / `nop.stream.ops.job-record.retention-minutes` / `nop.stream.ops.governance.cleanup-interval-ms`（默认 100 条 / 1440 分钟 / 5 分钟扫描；`OpsJobManager.startGovernance()` 启动扫描）。
- durable checkpoint 存储保留沿用 `CheckpointConfig.maxRetainedCheckpoints`（存储面，与观测面解耦）。
- 验证：`TestOpsRestLifecycleE2E#governanceSweepPrunesExpiredHistoryAndTerminalRecords`。

## 7. 已知边界（诚实披露）

- **remote-deploy 数据面全对订阅 + 队列满阻塞泄漏**（原 Follow-up item 28 + 31）：**已修复（2026-09-04，plan `2026-09-03-1951-3`）**。修复三要素 = per-subtask 订阅收敛（订阅范围三态：TM 单 deploy 只订本 subtask 输入 topics；remoteDeployMode=true 协调器零订阅；单 JVM 执行器全订阅不变）+ 队列满有界等待（默认 10s，满窗零进展转 `ERR_STREAM_CHANNEL_OVERFLOW` typed 失败，dispatch 线程不再永久阻塞）+ stall 恢复预算分池（stall 独立 `maxStallRestarts` + 30s 冷却，真实故障预算不被消耗）。设计 = `dataplane-transport-design.md`。**复验（2026-09-04，全参数留档）**：SOAK-3 36000 行（旧 jam 阈值 ~36 倍）完整收敛 exactly-once + epoch 55 次推进 max gap 5s（runId `1788456304950-1`）；CHAOS-1 kill TM ×2 轮持续流量下 fencing 1→2→3 严格递增 + 终态期望集（`1788456616196-1`）；BP-1 三档节流 50/200/500ms 全部 checkpoint 持续推进（29/30/30 次/窗口，`1788457179765-1`，原 200/500ms 档 jam 已消除）。复现命令 = §5 EX 行（`TestStabilityExerciseMultiJvm`）。**队列水位代理再校准（随修复落档）**：`nop_stream_msg_queue` 为 send 侧 INSERT-only 表（消费后不删除），健康运行的表计数随发送量单调增长属后端设计属性非泄漏——SOAK 判据的「活跃尾部队列增长」信号已与「epoch 无推进」耦合（jam 签名 = 增长 + 冻结；增长 + 推进 = 正常流量，`ExerciseSampler` 2026-09-04 校准）。
- **HA failover 新 leader 接管在 task_assignment 唯一键冲突处中止**（原 Follow-up item 34，**Phase 4 复验发现并确认 pre-existing**）：**已修复（2026-09-04，plan `2026-09-04-0829-1`）**。修复要素 = leadership 激活路径 attempt 计数种子化——`activateAsLeader` 在物化 assignment 前按 deployment plan 枚举逐 `(vertexId, subtaskIndex)` 读 `ClusterRegistry.getAttemptHistory`，把进程内计数抬到 max(内存, 持久化历史最大)（无历史 = fresh-job 行为不变；每次激活全量读，warm gating / 读失败 de-active 后 rethrow / 批量读不需要三项裁定见 `01-architecture-baseline.md`「跨 leader 接管的 attempt 连续性」节）。原缺陷留档：新 leader `activateAsLeader` 完成租约获取 + fencing 轮转（epoch=2000000）后，重发 assignment 的 `INSERT INTO nop_stream_task_assignment`（attempt_number=1）与旧 leader 的存留行唯一键冲突（`nop.err.dao.sql.duplicate-key`）→ become-leader listener 异常中止 → 接管半途而废（无重部署、无后续 checkpoint，输出冻结）；item 15 报告「jam 抑制重部署触发」归因已修正为本缺陷（同签名产物 `1788385016277-1`）。**复验（2026-09-04，全参数留档）**：CHAOS-2 两轮 JC kill 终态收敛（期望集 43 行完整 + checkpoint 恢复推进 94 次 max gap 5s + 输出不冻结）+ 租约/双 epoch 严格递增（lease 1→2→3、fencing 1000000→2000000→3000000）+ 全部 coordinator 日志无 duplicate-key / 无 invoke-become-leader-listener-error（runId `1788487981277-1`，pass）；多 JVM 接管单测断言面同步升级为「接管完成」（`TestMultiJvmCoordinatorFailover`，红证 = 选择性 stash 后必红 `1788483771855-1`）；focused 测试 = `TestJobCoordinatorAttemptSeedOnTakeover`（JDBC 预置行接续 / fresh-job 与同 JVM 回归 / InMemory 对等 / 读失败 de-active）。复现命令 = §5 EX 行 `chaosJcHaFailover`。
- **EpochManifest 文件不随 checkpoint retention 清理**（原 Follow-up item 33，Phase 4 复验发现）：**已修复（2026-09-04，plan `2026-09-04-0233-3`）**。新语义一句话：retention 一轮内**双平面同裁**——`cleanupOldCheckpoints` 删除超限旧 checkpoint 行之后同步调 `ICheckpointStorage.pruneEpochManifests`（keep-newest-N per `(jobId, pipelineId)`，按 epochId 降序，与 `loadRetainedEpochManifests` 同口径；LocalFile 删超限 `.epoch` 文件、JDBC 按读取观察到的超限旧 epochId 删 `stream_epoch_manifest` 行），使每个 `(jobId, pipelineId)` 的 manifest 数与 `maxRetainedCheckpoints` 同界；裁剪失败 WARN 留痕、下一轮完成自愈；async 路径只运行于 `checkpoint-retention-<jobId>` executor（段 3a monitor 内零新增 I/O），sync-fallback 内联于 ACK 线程。原缺陷留档：`cleanupOldCheckpoints` 只删 CompletedCheckpoint 平面，健康 SOAK-3（280s，interval 2s）留档 137 个 manifest 文件（maxRetained=5 不生效于 manifest 面）；item 15 报告「retained manifests 全程有界」的正观察系 jam 早停所致（epoch 冻结 ⇒ manifest 自然不再增长），非 retention 生效证据。设计与裁定 = `checkpoint-design.md` §9.2；接线/有界性验证 = `TestCheckpointRetentionAsync`（coordinator 完成路径→retention executor→裁剪面）+ `TestEpochManifestRetention`（双存储 focused）+ `TestCheckpointDualPlaneRetentionE2E`（LocalFile 端到端有界）+ `TestCheckpointCoordinatorJdbcRetainedManifests`（JDBC 行有界 + ref-count 不回退）+ gated C2 `TestS2RestoreRescaleMultiJvmE2E`（分布式终态有界断言）。
- **JDBC 后端 Stage-31 重启恢复降级**（原 Follow-up item 28）：**已修复（2026-09-04）**——`JdbcCheckpointStorage.loadRetainedEpochManifests` override 落地（`stream_epoch_manifest` per-epoch 行，最新优先 count 截断），接线验证 = `TestCheckpointCoordinatorJdbcRetainedManifests`（真实 `restoreSharedStateRegistry` 消费点，多 epoch ref-count 重建）；双存储契约见 `dataplane-transport-design.md` §六。
- **2PC sink 并行度 >1 引擎硬门禁**（原边界，**已解除** 2026-09-04，CONN-01 successor plan `2026-09-04-1043-1`）：2PC sink（jdbc-2pc/file）现支持任意有效并行度，exactly-once 在 P=N 成立（per-subtask 隔离 + 台账复合键/文件 `.sK` 后缀；LOCAL e2e `TestParallel2PcJdbcE2E`/`TestParallel2PcFileE2E` + 真实多 JVM kill TM 恢复 `TestParallel2PcMultiJvmE2E` 证明）。原 `ERR_STREAM_2PC_SINK_PARALLELISM_NOT_SUPPORTED` 门禁随移除删除；**新边界**：2PC sink 顶点跨并行度**恢复**被 typed 拒绝（`ERR_STREAM_2PC_SINK_PARALLELISM_CHANGE_UNSUPPORTED`，D1 裁定 checkpoint-design §8.5.2）——分布式重启须保持与快照相同的并行度。keyed 状态跨 key-group 再路由语义仍由 executor 级测试 + 离线 reshard 恢复覆盖。
- **checkpoint manifest 无 checksum/版本字段**（Follow-up item 25）：恢复断言以「最新 durable epoch manifest + 结果无重复无丢失」为准，不断言 manifest 校验行为。
- **背压无直接量化指标**（item 16 收口裁定 2026-09-03；item 15 演练量化补记；item 28 修复后 BP-1 全档复验通过）：背压量化仍以**代理观察**为准（TM 侧 io 指标不经 JC 暴露、队列水位直测 gauge 缺——均归 Follow-up 32）。**BP-1 复验（2026-09-04，items 28+31 修复后）**：三档全部推进（50/200/500ms 各 29/30/30 次/窗口），修复前的「200/500ms 档 jam」确认为 item 28 累计流量缺陷而非背压死锁；500ms 档 = 有界等待不误伤健康慢消费者的实证。
- **fencing epoch 缺省值**：非 HA launch 缺省 `deriveHaFencingEpoch(0,1)=1`；跨集群重启演练必须显式传更大的 `fencingEpoch`（新 generation 严格递增）。
- **多 JVM gated 测试偶发恢复检测超时**（2026-09-04 记录）：`TestMultiJvmExactlyOnceRecovery#multiJvmDeployKillRecoverFencing` 在高机器负载下偶现 `initial=1 recovered=1`（恢复检测窗口内未完成轮转）；同机复跑即绿（2.1s），git-stash A/B 判定 pre-existing 非回归（plan 1951-2 留档）。运行多 JVM 套件时避免并行负载。
