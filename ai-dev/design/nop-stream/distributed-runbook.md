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
- **共享 checkpoint 目录**：`LocalFileCheckpointStorage` 布局按 `<jobId>/` 组织，恢复身份 = (jobId, pipelineId)。

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
| **EX 稳定性演练矩阵（item 15：soak/chaos/backpressure）** | `TestStabilityExerciseMultiJvm`（`io.nop.stream.fraud.scenario`，参数化联合入口：`soakS2`/`soakS1`/`soakHighRateItem28Observation`/`chaosKillLoop`/`chaosJcHaFailover`/`backpressureSteppedThrottle`） | 三装置驱动（`ExerciseLoadGenerator` 负载+期望生成 / `ChaosKillPlanner` 种子化随机 kill（含 SIGSTOP/SIGCONT 分区等价轮）/ `ExerciseSampler` 周期采样 JC 指标+队列深度+epoch+存活）；参数 `-Dexercise.*` 覆写，非法值 fail-fast；**必须** `-Dnop.stream.test.multi-jvm.preserve-artifacts=true`（入口显式校验）；产物含 `samples/`（samples.jsonl + chaos-events.jsonl + run-summary.json）。矩阵定义与判据 = `ai-dev/analysis/2026-09/2026-09-03-distributed-stability-exercise-report.md` | 逐格判据见演练报告（无重复无丢失 / checkpoint 持续推进 / 资源泄漏信号 / fencing 严格递增）；test green = pass，red = 按结局分类（hang 先核验 item 28 归属） |

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

- **remote-deploy 数据面全对订阅 + 队列满阻塞泄漏**（Follow-up item 28；item 15 演练已定量触发，证据锐化见 Follow-up 31）：`SubtaskPlanBuilder` 在 remote-deploy 下构建全对通道，1024 槽队列满后 dispatch 线程可能永久阻塞。**item 15 演练（2026-09-03）全参数触发并定量**：跨 TM 通道累计 ~800—1000 条记录后数据面永久停摆（200 行/s@5s、20 行/s@40s、10 ev/s@60s 三速率点）；停摆签名 = durable epoch/输出冻结 + 进程全活 + `nop_stream_msg_queue` 单调增长（最高 41k+）+ TM 日志 `RemoteInputChannel -- Interrupted while enqueueing decoded element`；且 JDBC transport 无回压传导（源全速写完 12000 行而消费侧死亡）。**继发控制面失效**：jam 诱发 `taskStall=true` 自动 global recovery ×3 耗尽 recovery cap(=3) 后，真实节点 kill/租约到期永久不可恢复（演练 CHAOS-1 轮 3—8 直接证据）——修复须区分 stall 恢复预算与真实故障恢复。复现：`TestStabilityExerciseMultiJvm`（§5 EX 行）；报告 = `ai-dev/analysis/2026-09/2026-09-03-distributed-stability-exercise-report.md`。**演练若出现 gated 测试 hang（而非 fail），优先核验此项归属，勿误判为新死锁**（本轮未出现 hang——停摆以冻结+超时形态出现，判定路径同此归属）。
- **JDBC 后端 Stage-31 重启恢复降级**（Follow-up item 28）：`JdbcCheckpointStorage.loadRetainedEpochManifests` override 缺失。当前场景/演练 checkpoint 存储均为 `LocalFileCheckpointStorage`，该路径未触发、未被静默绕过（item 15 演练 2026-09-03 再次核验：全部 6 格均为 LocalFile 路径）；JDBC 后端恢复语义待 item 28。
- **2PC sink 并行度 >1 引擎硬门禁**：`ERR_STREAM_2PC_SINK_PARALLELISM_NOT_SUPPORTED`（CONN-01 P1 有意 defer，checkpoint-design §6.4.1）。分布式场景管线一律有效并行度 1；keyed-parallelism rescale（P>1 + 2PC sink 组合）待 CONN-01 successor + per-transform parallelism 消费（roadmap item 29 家族）。keyed 状态跨 key-group 再路由语义已由 executor 级测试 + 离线 reshard 恢复覆盖。
- **checkpoint manifest 无 checksum/版本字段**（Follow-up item 25）：恢复断言以「最新 durable epoch manifest + 结果无重复无丢失」为准，不断言 manifest 校验行为。
- **背压无直接量化指标**（item 16 收口裁定 2026-09-03；item 15 演练量化补记）：item 16 交付的指标面（五层名表）未含背压直测指标（如输出队列水位 gauge）——背压量化仍以**代理观察**为准：`nop.stream.io.emit.time`（发射阻塞耗时——生产侧背压代理；**多 JVM 演练下 TM 侧 io 指标不经 JC 暴露，见下**）+ `nop.stream.operator.processing.time`（单记录处理时长抬升）+ `nop.stream.io.records.emitted.total` 增速回落 + checkpoint 存储 epoch 推进 + 解除后结果完整性。**指标缺口记 Follow-up 候选**（队列水位直测 gauge 归属 item 15 稳定性演练的采样装置裁定，已路由 Follow-up 32；TM 侧指标上报通道缺口同属 32）。**item 15 BP-1 演练量化结果（2026-09-03）**：sink 内节流 50ms/记录档位下 checkpoint 60s 窗口推进 ×18 无死锁（C3 语义成立）；200/500ms 档位的推进停止归因 item 28 累计流量 jam（非背压死锁——50ms 档同为持续背压却健康），背压行为本身未见独立缺陷。
- **fencing epoch 缺省值**：非 HA launch 缺省 `deriveHaFencingEpoch(0,1)=1`；跨集群重启演练必须显式传更大的 `fencingEpoch`（新 generation 严格递增）。
