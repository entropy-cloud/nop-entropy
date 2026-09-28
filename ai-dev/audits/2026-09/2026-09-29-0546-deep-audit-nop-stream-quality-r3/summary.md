# nop-stream 深度审计 R3（2026-09-29）

> Scope: nop-stream（core / runtime / cep / flow / rocksdb / connector*），HEAD = 43392902b3（plan 01 收口后）
> 基线：`./mvnw test -pl nop-stream-core,nop-stream-flow,nop-stream-runtime,nop-stream-cep,nop-stream-rocksdb -am` 全绿（`_tmp/r4-baseline-test.log` EXIT=0）
> 方法：4 个并行只读子代理（① plan-01 三提交 diff 回归审计、② 正确性新发现、③ 可读性/结构、④ 性能候选与基准缺口）+ 高严重度发现逐条人工核实（标注 ✅）
> 前置基线文档：`ai-dev/analysis/2026-09/2026-09-28-nop-stream-quality-perf-audit.md`（R3 前轮 93 项）、plans 358-360 / 2277-2279 / nop-stream-quality-perf-01、收敛裁定书 `ai-dev/audits/evidence/nop-stream-perf-{360,2279}/convergence*.md`

## 结论

共记录 24 项新发现（P0×2、P1×4、P2×18）。**plan-01 三提交自身无 P0/P1 回归**（diff 审计逐 hunk 核对，仅 2 条 P2 注释/异常树形态偏差 + 2 条既有问题备注）。本轮 P0 集中在三轮深审覆盖最薄的**region 重启/物化回放**特性线（SupervisionLoop），两项均可造成作业静默永久悬挂。既有性能裁定（360/2279/plan-01 的 8 个抽查项）在 HEAD 全部维持，未破坏。

## P0（已人工核实 ✅，均在 region 重启特性线）

| # | 位置 | 问题 | 验证 |
|---|------|------|------|
| R4-N1 ✅ | `SupervisionLoop.java:789,801` + `ResultPartition.java:490` + `InputChannel.activateMaterializationReplay` | region 重启的 materialization 回放把**无界**回放列表经 `injectFront` 的阻塞 `queue.put` 灌入**有界**（默认 1024）队列；此刻新 consumer 尚未提交、旧 consumer 已终态，无人消费——回放量 ≥ 容量时监督线程永久阻塞，作业悬挂。回放列表来自物化 store（A5 已证其在停机期全速增长），中等吞吐 1 秒即可超限 | ✅ 代码逐行核实：`LinkedBlockingQueue<>(capacity)`、`queue.put`、Phase 3 rebuild 先于 submitTask |
| R4-N2 ✅ | `SupervisionLoop.java:794-817`（else 分支）+ `:849-854`（producer 复用旧 writer） | region 只在 materialization 边切开（RegionDecomposer 契约）；region 重启时，**同 region 内部边**（无 matPoint）的 consumer 侧重建为**全新空分区**（不 replay 也不 seal），而同 region 的 producer 侧复用**旧** writer 写**旧**分区 → 边被切断：consumer 永久阻塞在空分区 take()，producer 写满旧分区后阻塞，作业静默悬挂。任何 ≥2 顶点 region 失败即触发（最常见拓扑） | ✅ RegionDecomposer 契约（materialization 边=唯一切点）+ 两处重建代码核实 |

## P1

| # | 位置 | 问题 | 验证 |
|---|------|------|------|
| R4-N3 ✅ | `TaskManager.java:309-323`（心跳无 isFinished 过滤）+ `RunningTask.java:161-163`（成功保留条目）+ `JobCoordinator.java:924-928,1002,1233` | 三组件契约矛盾：RunningTask 成功后**故意保留**注册表条目（bounded-run 尾段 2PC 提交），coordinator 在 COMPLETED 时删除 liveness 键并注释声称"任务已离开 runningTasks，心跳不会再加回"——该假设对 success 路径为假；心跳把**冻结的** activity 时间持续 merge 回 subtaskLiveness，60s 后必然误判 TASK_STALL → 触发全局恢复、取消正在排水/提交尾段的健康任务（恰好摧毁保留条目要保护的窗口；尾段事务无后续恢复可重提交时丢数据） | ✅ 三处代码核实 |
| R4-N4 ✅ | `JdbcCheckpointStorage.java:400-407,567-574` | `tableExists()`/`epochTableExists()` 把**任何**异常降级为"表不存在"（LOG.debug + return false）；restore 路径遇瞬时 JDBC 故障（连接抖动/权限）→ manifest 视为空 → 有状态作业以空状态静默冷启动，与"真没有 checkpoint"不可区分（写路径同故障却响亮抛错，读写语义不对称） | ✅ 代码核实 |
| R4-A2' | `CheckpointCoordinator.java:870`（fail 路径） | A2 修复不对称：`onCompletePersistFailure` 调 `notifyParticipantsFinishCommit(id,false)` 后无 A2 式 `checkpointSuccessMap` 清理——无失败参与者时条目永久滞留（与已修 abort 路径同族，pre-existing） | 子代理核实 |
| R4-B6' | `FileSource.java:163` | B6 修复不完整：`directoryPath` 写出仍无保留字符校验（splitById 已校验），含 `\n` 的目录路径腐蚀整个 payload → checkpoint 成功但无法恢复（pre-existing） | 子代理核实 |

## P2（择要）

- **R4-N5 ✅** `ResultPartition.injectFront:475-479` 对 EOS 哨兵 release 了一个从不存在的许可（close() 入队不 acquire、read()/drain 出队不 release、injectFront 重 put 不 acquire——唯独 injectFront drain 侧多 release）→ 每次"已 finished 分区"的 unaligned 恢复注入净 +1 permit，背压上界缓慢侵蚀。已逐行核实。
- **R4-N6** `GraphModelCheckpointExecutor.triggerSavepoint:329-346` 失败时静默返回 null（A3 同族、独立 API 面）。
- **R4-S1（diff 审计）** plan-01 重写 `closeChainAndGate` 后三重失败场景 suppressed 树从扁平变嵌套（`firstError.suppressed=[X]`、`X.suppressed=[Y]`），异常不丢但与提交声明的"逐字保持"不符。
- **R4-S2（diff 审计）** `JobCoordinator.java:1497-1511,1281-1283` 注释描述的 recoveryPending 清除时点已失实（实际在外层 finally、锁外、health/事件之后）——并发关键路径上的失实注释。
- **可读性 P1 组**：3 个"声明但未接线"死特性面（RemoteResultPartition 心跳 ~60 行、TtlContext.sweepExpired/backgroundCleanup、AbstractStreamOperator.processWatermarkStatus1/2——已核实双输入算子不存在 ✅）；10 个零引用 public 顶层类（5 个滞留三轮整改）；TestAwait 5 模块逐字副本；windowing 测试家族互抄（plan-01 新增 A12 测试为第 5 份）；21 份 IStreamTaskRpcService stub。
- **可读性 P2 组**：deployTask 141 行 7×守卫重复 + reportDeploy/Assignment 20 行双胞胎；MaxParallelismReshardMigration 126 行 7 参方法；TaskCheckpointWiring 全文件零缩进 + wire/unwire 4 组 instanceof 镜像；FileSource 校验第 3 份复制；4 处零引用常量（含 numLateRecordsDropped 指标从未注册——观测缺口）；15 处 plan-01 编号注释 + JobCoordinator:80/TaskManager:470 说谎 javadoc；若干单方法死 API。
- **性能候选（新）**：file sink invoke 每记录 `toString()` 物化（任务内 10-40%，aliasing 契约）、FileSourceReader 逐字节读（H 组既有项，源任务内 5-30%，行为保持可修）、JDBC sink invoke 每记录 Map 拷贝（3-10%，门控风险）、E3 记忆化每状态 IdentityHashMap 分配（d20 1-2.5%，无风险）、双槽 storage-key 缓存（evictor 档 ~1%，watch）。**既有 8 项裁定抽查全部维持 ✅**。
- **基准缺口**：connector 数据面（file source 行读、2PC sink invoke）全无口径；TaskDispatchLoop 中间层无隔离口径；嵌入态 checkpoint 循环、extractPatterns、Memory shard 路由无口径。

## 与既有裁定的关系

- plan-01 Deferred 的行为变更类缺陷（A3/A4/A5/A6/B2/B7）维持"owner 语义确认后单独立项"归属，本轮不实施（N6 并入 A3 successor）。
- 性能 Deferred 项（D2/D3/C2/F3 等）维持原裁定，本轮抽查确认未被后续改动破坏。
- 修复归属：R4-N1/N2/N3/N4/A2'/B6'/N5/S1/S2 + 可读性组 + 性能候选实测 → `ai-dev/plans/366-nop-stream-audit-r4-quality-perf.md`。

## 详细报告

- `01-correctness-findings.md`（正确性：N1-N6、A2'、B6'、含自我否决候选）
- `02-readability-structure.md`（可读性/结构）
- `03-performance.md`（性能候选与基准缺口）
