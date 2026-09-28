# R3 正确性发现（2026-09-29）

> 审计方法：diff 回归子代理（plan-01 三提交逐 hunk）+ 正确性子代理（浅覆盖面新发现）；✅ = 主审人工核实（live 代码逐行）

## R4-N1（P0，作业静默永久悬挂）✅

**位置**：`SupervisionLoop.java:789,801`（调用点）+ `ResultPartition.java:490`（`queue.put`）+ `InputChannel.activateMaterializationReplay`

- `ResultPartition` 队列为有界 `LinkedBlockingQueue<>(capacity)`（默认 1024，`GraphExecutionPlan.resolvePartitionCapacity`）。
- `restartRegion` Phase 3 在 `rebuildTask` 内**同步**执行回放注入（`buildConsumerInvokableWithReplay` → `tempChannel.activateMaterializationReplay(epoch)` → `partition.injectFront(list)`），此刻旧 consumer 已终态（Phase 2 等待）、新 consumer 尚未 `executor.submitTask`——**没有任何线程在消费队列**。
- `injectFront` 对回放列表逐条阻塞 `queue.put`；回放列表来自物化 store 的 `point.replay(epoch)`，停机期以生产端全速无界增长（A5 已记录 store 无界）。回放量 ≥ 队列容量时第 1025 次 `put` 永久阻塞监督线程。
- 对照组：unaligned-restore 同族注入（`InputGate.restoreChannelState`）安全，因其喂入的是 capture 截断的 ≤ 容量在途快照；materialization 回放复用了为有界快照设计的 API。
- 既有测试只回放小批量，未暴露。
- **修复方向**：回放改为惰性/分批——如分区挂 pendingReplay 缓冲由 consumer 的 `read()` 优先排空（O(1) 注入、不依赖队列容量），保持"回放先于新队列数据"顺序。

## R4-N2（P0，作业静默永久悬挂）✅

**位置**：`SupervisionLoop.java:794-817`（else 分支）+ `:849-854`（`rebuildProducerInvokableReusingWriters`）

- `RegionDecomposer` 契约：region 只在 materialization 边切开 → **同 region 内部边必然无 matPoint**。
- consumer 侧重建：`matPoint == null` 走 else 分支 → `new ResultPartition()`（空、不 seal）。
- producer 侧重建：复用**旧** writer → 写**旧**分区。
- 结果：内部边 A→B 的 region（≥2 顶点，最常见拓扑如 region={source,window}）失败重启后，B 读空分区永久 `take()` 阻塞，A 写旧分区满 1024 后阻塞；作业既不完成也不报错。
- `:448-452` 的 `hasProducerRole` 检查只打日志不拒绝。
- **修复方向**：`matPoint == null` 的通道一律复用旧分区（与 producer 侧旧 writer 天然对账；producer 已 finished 时 consumer 自然读到 EOS）；或对含内部边的 region 恢复 fail-fast 拒绝。前者恢复设计意图（reconnect-to-live-queue），为优选。

## R4-N3（P1，假阳性恢复 → 恰好摧毁尾段提交保护）✅

**位置**：`TaskManager.java:309-323`（心跳循环无 isFinished 过滤）+ `RunningTask.java:161-163`（success 保留条目）+ `JobCoordinator.java:924-928`（COMPLETED 删 liveness 键 + 失实注释）、`:1002`（merge 回插）、`:1233`（stall 判定）

- `RunningTask.run()` finally 对 success==true **故意保留** runningTasks 条目（bounded-run 尾段 2PC 提交依赖它接收 `notifyCheckpointComplete`）。
- coordinator 在 COMPLETED 时 `subtaskLiveness.remove(livenessKey)`，注释声称"The task has already left the TaskManager's runningTasks set … no further heartbeats will re-add it"——对 success 路径为假。
- `TaskManager.heartbeat()` 遍历 `runningTasks.values()`（仅 null-invokable 过滤），把**冻结**的 `inv.getLastActivityTime()` 持续上报；coordinator `merge(key, frozen, Math::max)` 重新插入冻结值。
- `taskTimeoutMs`（默认 60s）后该任务必然被判 TASK_STALL → `requestRecovery` 全局恢复 → 取消仍在排水/提交尾段的健康任务；尾段事务无后续恢复可重提交时即丢数据。
- **修复方向**：心跳循环对 finished 任务跳过 liveness 上报（一个 isFinished 过滤），恢复 coordinator 注释钉死的契约。

## R4-N4（P1，静默状态丢失）✅

**位置**：`JdbcCheckpointStorage.java:400-407`（tableExists）、`:567-574`（epochTableExists）；消费点 `:123/:152/:183/:214/:264/:286/:352/:541/:595/:647`

- `catch (Exception e) { LOG.debug(...); return false; }`：基础设施故障（连接抖动/权限）被降级为"表不存在"。
- restore 路径：`loadLatestEpochManifest`/`getLatestCheckpoint` 返回 null → `GraphModelCheckpointExecutor.restoreFromCheckpoint:890-893` INFO "No recoverable checkpoint found, starting fresh" → **有状态作业以空状态静默起跑**。
- 写路径 `ensureTable` 同故障响亮抛错——读写失败语义不对称。
- **修复方向**：区分"表不存在"（查询成功返回 false）与"查询失败"（抛 typed 异常）；或至少把读路径的降级改为 WARN + 可配置 fail-fast。

## R4-N5（P2，背压许可缓慢泄漏）✅

**位置**：`ResultPartition.java:475-479`（injectFront drain 侧见哨兵 `bufferPool.release()`）vs `:356`（close 入队不 acquire）、`:305-307`（read 出队不 release）、`:496-497`（重 put 不 acquire）

- 哨兵从不持有许可；唯独 injectFront 对它多 release 一次 → 每次"已 finished 分区"的注入净 +1 permit。
- **修复方向**：drain 侧见哨兵 break 不 release（对齐 `drainBufferedElements`）。

## R4-N6（P2，静默失败，A3 同族独立 API 面）

`GraphModelCheckpointExecutor.triggerSavepoint:329-346`：pending 无法触发、future 返回 null 或 completed==null 时静默返回 `savepointPath=null`，调用方无法区分已保存/失败。并入 A3 的 successor 修复裁定。

## R4-A2'（P2，既有缺陷，A2 同族不对称）

`CheckpointCoordinator.onCompletePersistFailure`（:870 fail 路径）调 `notifyParticipantsFinishCommit(id,false)` 后无 `checkpointSuccessMap` 清理；无失败参与者时条目永久滞留。abort 路径（:926-928）已修，fail 路径遗漏。

## R4-B6'（P2，既有缺陷，B6 不完整）

`FileSource.java:163`：`directoryPath` 写出仍无保留字符校验（splitById 已校验），含 `\n`/`\` 的目录路径腐蚀 payload。

## diff 回归审计结论（plan-01 三提交）

**无 P0/P1 新引入回归。** 逐 hunk 核对清单：
- E3 记忆化：IdentityHashMap 不依赖 equals/hashCode；方法局部无跨 key/并发泄漏；ConditionContext.matchedEvents 惰性冻结使缓存不失真；filter 异常不缓存；3 个调用点全覆盖。hunk OK。
- F1/F2 缓存：双组件 equals 失效完备；共享 byte[] 只读无腐蚀；namespace 双轨语义保持。hunk OK。
- JobCoordinator A1 重构：预算提前返回/锁内异常全经外层 finally 清 flag；锁配对完整；CAS 唯一生产入口。hunk OK（仅 R4-S2 注释失实）。
- StreamTaskInvokable 重构：4 处 CloseSupport 替换逐字等价；**例外**：closeChainAndGate 三重失败时 suppressed 树扁平→嵌套（R4-S1）。
- A2/A7/A8/A12/B1/B3-B6/G4/G7/G8/G9/G13/G15/G17：hunk OK。

## 自我否决候选（审计过程排除，留档防重复调查）

- `deployTask` 换槽不等旧线程退出（跨 JVM 僵尸窗口由 fencing epoch 兜底）
- `RemoteResultPartition.close()` 并发双 EOS（单 producer 契约不可达）
- `TaskManager.cancelTask` 无条件 remove 竞态（coordinator 取消总伴随 epoch rotate/作业停止）
- `SubtaskTask.run` 早期 return 跳过 closeOperatorChains（链未 open）
- JDBC SELECT LIMIT 方言（失败响亮，支持矩阵均支持）
- region 重启丢弃在途 watermark（TimerSnapshot 携带恢复 currentWatermark）
