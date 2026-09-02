# nop-stream 分布式运行手册（初稿）

**日期**：2026-09-02
**范围**：nop-stream DISTRIBUTED 模式的部署、启动、checkpoint/恢复操作、kill/rescale/backpressure 演练
**状态**：初稿（roadmap item 14 交付；item 16「可观测性与运维」/ item 17「文档产品化」在此基础上深化并迁移到 `docs-for-ai/`）
**基线**：`MiniStreamCluster` 真实多 JVM（ProcessBuilder spawn；D-GAP §3.2 约束 ⑤：不引入 K8s/容器编排）

---

## 1. 部署拓扑

```
+-------------------+       control-plane RPC (JDBC 消息表轮询)        +----------------+
| JobCoordinator(JVM)| ---- deployTask / triggerCheckpoint /            | TaskManager ×N |
|  JobCoordinatorMain|      notifyCheckpointComplete / cancelTask ---> | TaskManagerMain|
 +-------------------+      <---- ACK / 状态报告 / 心跳 --------------  +----------------+
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

## 4. 演练步骤（与 gated 测试命令一一对应）

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

**既有能力基线演练**（runtime 模块，trivial/heartbeat 管线）：`TestMiniStreamClusterProcessSpawn`（进程 spawn/健康检查）、`TestMultiJvmExactlyOnceRecovery`（heartbeat 源 kill/恢复 + fencing epoch 严格递增）、`TestMultiJvmCoordinatorFailover`（HA JC failover）。

## 5. 已知边界（诚实披露）

- **remote-deploy 数据面全对订阅 + 队列满阻塞泄漏**（Follow-up item 28）：`SubtaskPlanBuilder` 在 remote-deploy 下构建全对通道，1024 槽队列满后 dispatch 线程可能永久阻塞。场景演练（有界 fixture、低速率）未触发 hang；真实大流量下由 item 28 收敛。**演练若出现 gated 测试 hang（而非 fail），优先核验此项归属，勿误判为新死锁。**
- **JDBC 后端 Stage-31 重启恢复降级**（Follow-up item 28）：`JdbcCheckpointStorage.loadRetainedEpochManifests` override 缺失。当前场景/演练 checkpoint 存储均为 `LocalFileCheckpointStorage`，该路径未触发、未被静默绕过；JDBC 后端恢复语义待 item 28。
- **2PC sink 并行度 >1 引擎硬门禁**：`ERR_STREAM_2PC_SINK_PARALLELISM_NOT_SUPPORTED`（CONN-01 P1 有意 defer，checkpoint-design §6.4.1）。分布式场景管线一律有效并行度 1；keyed-parallelism rescale（P>1 + 2PC sink 组合）待 CONN-01 successor + per-transform parallelism 消费（roadmap item 29 家族）。keyed 状态跨 key-group 再路由语义已由 executor 级测试 + 离线 reshard 恢复覆盖。
- **checkpoint manifest 无 checksum/版本字段**（Follow-up item 25）：恢复断言以「最新 durable epoch manifest + 结果无重复无丢失」为准，不断言 manifest 校验行为。
- **背压无直接指标**（item 16 前提）：背压观察代理 = checkpoint 存储产物 epoch 推进 + 解除后结果完整性；稳定性量化属 item 15。
- **fencing epoch 缺省值**：非 HA launch 缺省 `deriveHaFencingEpoch(0,1)=1`；跨集群重启演练必须显式传更大的 `fencingEpoch`（新 generation 严格递增）。
