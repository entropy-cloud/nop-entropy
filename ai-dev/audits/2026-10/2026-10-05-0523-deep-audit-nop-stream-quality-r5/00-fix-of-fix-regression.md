# nop-stream 修复批次回归审计（fix-of-fix）— R5 轮报告 00 号

- **审计日期**: 2026-10-05
- **HEAD SHA**: `bd089444f5c2b1fcd2c9a964ce909bac774a83d9`
- **审计人**: 资深 Java 平台审计员（fix-of-fix 专项）
- **只读声明**: 本审计全程只读（git log/show/diff + 源码阅读），未运行 maven/测试，未修改任何产品代码。

## 覆盖的提交清单

以 `git log --since=2026-09-28 -- nop-stream/ nop-message/` 为入口，逐 hunk 深查以下行为性修复提交（其余为 docs/test/plan 记账或新增特性，不在 fix-of-fix 范围）：

**plan 368（2026-09-30，R4 缺陷修复）**

| 提交 | 内容 |
|---|---|
| `0dc22b8fab` | Phase 1: AR-01 keyBy 路由/属主公式统一 + ST-03 enum 稳定哈希 |
| `bcadd20c72` | Phase 2: ST-01 PendingCheckpoint.abort future 补全 / ST-02 restore 键重材料化 / ST-06 checkpoint 记录损坏响亮失败 |
| `a6cb2a5731` | Phase 3: CC-01 空工作集哨兵 + CC-03 stop 级联 cancel + CC-04 轮换代 liveness 清理 + CC-05 僵尸心跳拒绝 + REG-02 COMPLETED 墓碑 + CC-02 重放 gap 收口（SupervisionLoop） |
| `e20b7cef1b` | Phase 4: CON-01 ledger 命名空间 + CON-02/03 Debezium 错误传播 + CON-05 并行度门 + AR-10 volatile + 任务身份管线（deepCopy(TaskLocation)） |
| `4cb16e87db` | Phase 5: CEP-01 PT bucket 排水 ledger 断链 + REG-01 不可回放内部边 fail-fast + CEP-02 until bypass + CEP-08 times 校验 |
| `2bb7c013a7` | Phase 7: P2 批次（CEP-03/W-M1 指标分域、AR-02 custom source fail-fast、ST-04/07、CON-07/08/10、TE-05/09/16） |
| `5f9c6989bc` | Phase 8: 治理批次 13 项（行为保持声明） |
| `598e52e71f` | plan 368 补提交交付物 |

**plan 369（2026-09-30，R6 Flink 对照修复）**

| 提交 | 内容 |
|---|---|
| `cb01757ef5` + `821e322d24` | Phase 1: C1 savepoint 免降级 + C2 终点 checkpoint 有界重试 + C3 完成任务契约收缩/状态继承 + C4 transport persistence 声明 |
| `b738d61212` | Phase 2: G-3 per-window 用户状态清理 + G-2 side output + G-1 迟到数据 side output + W-M1 指标分域 |
| `765ada3386` | Phase 3: ST-12 TTL 周期清扫 + NEW-B operator state union + NEW-A scale-up fail-fast + assignKeyGroupToSubtask O(1) |
| `24b935140d` | Phase 4: CC-15 JDBC 选举 epoch 单调计数器 + CC-07 部署宽限期 + CC-10 + RPC 契约 |

**plan 2306（2026-10-03，44 项嫌疑批量修复）**

| 提交 | 内容 |
|---|---|
| `bc9c444cf3` | nop-stream 面: DmlOp @DataBean + normalizeBatch 回水合 + stableHash 统一 murmur3 |
| `05e81257e6` | 全仓收口: normalizeBatch 遗留裸行形态兼容 + 测试裁定 |

**2026-10-02/03/04 其他行为性提交**

- `794e68a5a0`（B 层实例隔离: schema history 实例化 + serverName/JMX + server id 混名）
- `3c542f7046`、`75a3a368a9`、`1dad44a0c6`、`706a5b8a4c`（CDC 真库修复、Debezium 3.7 换代、分片契约、jdbc dmlMode）
- `754556b002`（EquiJoin watermark 跨 key 清理 currentKey 重设，MAJ-1）
- `6e097ee235` merge add-stream-sql（新特性面，抽查其修 MAJ-1 行为性 hunk）
- nop-message WI4–WI18（`71d7d69dbb`…`7342b6325a`，重点深查 WI9/WI11/WI12/WI14）

## 方法

1. 每个行为性提交做 `git show <sha>` 全量 hunk 审查，先读 diff 再回读 HEAD live code 验证调用方与不变量（KeyGroupAssignment、PendingCheckpoint、CheckpointCoordinator、SupervisionLoop、JdbcLeaderElector、KafkaConsumeTask、DebeziumCdcSourceFunction 等均为 live code 复核）。
2. 对照且仅对照三个计划文件（368/369/2306）的修复声明与提交信息；未读 ai-dev/audits、ai-dev/bugs 正文及其他 plans。
3. 对每个行为性 hunk 做语义推演（边界/null/并发/异常路径）与变异式推演（配套测试在核心逻辑回退时能否变红）。
4. 治理批次抽查 5 项删除/改名：全仓 grep 验证 0 引用（见「检查范围」）。

---

## 发现条目

### [REG-1] 远程部署路径 subtask-0 未注入 ledger 命名空间——CON-01 修复在 remote 面不完整，且 region restart 翻转 subtask-0 的 ledger 身份

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/transport/RemoteGraphExecutionPlanBuilder.java:286-288`；`nop-stream/nop-stream-connector-jdbc/src/main/java/io/nop/stream/connector/jdbc/JdbcTwoPhaseCommitSink.java:418-419,643`；`nop-stream/nop-stream-connector-jdbc/src/main/java/io/nop/stream/connector/jdbc/JdbcTwoPhaseCommitSinkBuilder.java:138-139`
- **证据片段**:
```java
// RemoteGraphExecutionPlanBuilder（subtask 0 共享 ORIGINAL 链，未经 copyForSubtask(TaskLocation)）:
OperatorChain chain = taskIndex == 0
        ? original.getOperatorChains().get(0)
        : original.getOperatorChains().get(0).deepCopy(taskLocation);

// JdbcTwoPhaseCommitSinkBuilder.build() —— 原始实例 namespace=null:
return new JdbcTwoPhaseCommitSink<>(jdbcTemplate, querySpace, tableName,
        ledgerTableName, columnNames, recordMapper, 0, maxBatchSize, null, ...);

// JdbcTwoPhaseCommitSink —— namespace 缺省回退到目标表名:
private String effectiveLedgerNamespace() {
    return ledgerNamespace != null ? ledgerNamespace : tableName;
}
```
- **严重程度**: P1
- **现状**: plan 368 Phase 4（R5-CON-01）把 v2 ledger 主键改为 `(sink_namespace, epoch_id, subtask_id)`，namespace 由 `copyForSubtask(TaskLocation)` 注入 `jobId|vertexId`；但 remote 计划构建器对 `taskIndex == 0` 复用**原始** OperatorChain，原始 sink 由 builder 构造、namespace 恒为 null，回退为 `tableName`。后果一：两个不同 job（共享同一数据库、同名目标表与同名 ledger 表，即 CON-01 明示保护的"不同 job 共享一个数据库"场景）的 subtask-0 在相同 epoch 序列下 ledger 键 `(tableName, epoch, 0)` 相互碰撞，后提交方被幂等守卫 WARN 后**静默跳过数据写**（数据丢失，仅日志可见）；同 job 双分支写同一目标表同理。后果二：remote 模式 subtask-0 发生 region restart 时，`SupervisionLoop` 用 `deepCopy(oldSubtask.getTaskLocation())` 重建链，sink 经 `copyForSubtask(TaskLocation)` 获得 namespace=`jobId|vertexId`——subtask-0 的 ledger 身份在重启前后从 `tableName` **翻转为** `job|vertex`，对已 prepare 未 commit 的 epoch，恢复后守卫必然 miss，重复提交同一批数据（duplicate commit，恰好击穿守卫存在的目的）。embedded 构建器（`GraphExecutionPlan`）因 `needsCopy` 恒为真而不受影响，两个构建器行为不对称。
- **风险**: remote 生产部署（本引擎的主部署形态）下，CON-01 声称已修复的跨 job ledger 守卫互踩在 subtask-0 上依旧可静默丢数据；region restart 后 subtask-0 的 ledger 键漂移引入新的重复提交窗口。配套测试 `TestJdbcTwoPhaseCommitLedgerNamespace` 只经 `copyForSubtask(TaskLocation)` 注入身份，从未覆盖 remote builder 的 taskIndex==0 原始链路径，回退该路径不会变红。
- **建议**: remote builder 对 taskIndex==0 也走 `deepCopy(taskLocation)`（或在 builder 原始构造处直接携带 namespace），使两构建器与 restart 路径三者身份一致；补一条 remote 形态的守卫测试（双 job 同表同 epoch 断言互不 skip）+ restart 前后 namespace 不变断言。
- **信心水平**: 确定（代码路径确定；触发需要"跨 job 共库同名表"或"remote subtask-0 restart 且有 in-flight pending epoch"的部署形态）
- **误报排除**: 这不是"多配置不可能"式臆测——两条触发路径都是修复声明（"different jobs sharing one database never collide"与 restart 期 ledger 幂等保持）自己点名的场景；也不是对回退命名空间风格的洁癖——`effectiveLedgerNamespace()` 的回退分支正是使守卫键在 subtask-0 与其他 subtask 间不一致、并在 restart 时翻转的结构性原因。
- **复核状态**: 未复核

### [REG-2] REG-01 不可回放内部边 fail-fast 无 epoch 对照，安全重启场景被误杀为 job 失败

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/SupervisionLoop.java:605-646`
- **证据片段**:
```java
private static void failFastOnUnreplayableInternalEdge(...) {
    if (coordinator == null || coordinator.getLatestCheckpoint() == null) {
        return;
    }
    long checkpointEpoch = coordinator.getLatestCheckpoint().getCheckpointId();
    ...
            ResultPartition partition = channel.getPartition();
            if (!partition.isFinished()) {
                // Producer not completed: it is rebuilt and re-emits
                continue;
            }
            throw new StreamException(ERR_STREAM_RESTART_UNREPLAYABLE_INTERNAL_EDGE)
```
- **严重程度**: P2
- **现状**: 该 fail-fast（plan 368 Phase 5，防"COMPLETED 生产者 + 非物化内部边 + 消费者状态回滚"的静默丢数）只判断"存在任一已完成 checkpoint + 分区已 sealed + 消费者待重建"，**不比较 checkpoint epoch 是否已覆盖分区全部数据**。有限流拓扑（生产者发完 EOS 并 seal 分区，消费者 checkpoint 在 EOS 数据全部消费之后完成，随后消费者在处理 EOS 前因用户函数异常失败）满足全部触发条件，但此时回滚到该 checkpoint 后残余队列+EOS 交付是**零丢失**的正确恢复——修复前可干净重启，修复后抛 `ERR_STREAM_RESTART_UNREPLAYABLE_INTERNAL_EDGE`，经监督循环重试仍复现同一条件，直至 restart 预算耗尽、job FAILED。
- **风险**: 有界/批式流（项目官方支持的 BatchLoaderSourceFunction 有限数据集形态）中"生产者先完成、消费者后失败"本来可恢复的故障变成不可恢复；且检测条件在重试间不变，预算必然烧尽。
- **建议**: 引入可区分信号再收紧——例如消费侧记录"已完整消费 sealed 分区（收到 EOS 前）的最后水位/barrier epoch"，或将 sealed 分区的末记录 epoch 与 checkpoint epoch 对照（非物化边缺 epoch 时，可退而在 checkpoint ACK 里记录各输入通道 consumedUpTo）；至少应在错误信息中区分"确认有损窗口"与"无法判定"两种形态，前者才 fail-fast。
- **信心水平**: 很可能（误杀路径在代码层面确定存在；实际发生频率取决于有限流拓扑在生产中的占比）
- **误报排除**: 这不是对"fail-fast 方向"本身的否定——用 fail-fast 换掉静默丢数在信息不足时是正确取舍；问题是检测条件**过宽**（任意历史 checkpoint 即触发，而非确认 checkpoint 未覆盖分区数据），把"无法判定"与"确认有损"混为一谈，属于可收敛的检测精度缺陷而非风格异议。配套测试 `TestRegionRestartUnreplayableInternalEdge` 只钉住有损路径（`internalEdge_completedProducer_consumerRebuild_withCheckpoint_failsLoud`），安全路径无守卫，回退/过宽都不会被测试暴露。
- **复核状态**: 未复核

### [REG-3] Debezium B 层 schema history JDBC 表名后缀仅取 subtask 序号——多连接器共享一库时互相碰撞，recovery 互锁修复不完整

- **文件**: `nop-stream/nop-stream-connector-debezium/src/main/java/io/nop/stream/connector/debezium/DebeziumCdcSourceFunction.java:661-679`
- **证据片段**:
```java
String table = config.getSchemaHistoryJdbcTable();
config.setSchemaHistoryJdbcTable(
        (table == null || table.isEmpty() ? "nop_cdc_schema_history" : table)
                + "_" + lastSegment(instance));
...
private static String lastSegment(String instance) {
    int dash = instance.lastIndexOf('-');
    return dash >= 0 ? instance.substring(dash + 1) : instance;
}
```
- **严重程度**: P2
- **现状**: `794e68a5a0` 修复"B 层 schema history 未按实例隔离致 recovery 互锁"。文件路径变体用**完整**实例名（`{instance}-{fileName}`），隔离正确；JDBC 表名变体却用 `lastSegment(instance)`——而 `instance = baseName + "-" + subtaskIndex`，`lastIndexOf('-')` 之后只剩 subtask 序号。连接器 `cdcA` 的 subtask-0 与连接器 `cdcB` 的 subtask-0 共享同一数据库时，二者都落到 `nop_cdc_schema_history_0`，恢复了对不同连接器（乃至不同 job）的历史互读/互锁——与被修复的缺陷同构，只是从"同 job 双实例"换位到"跨连接器同序号"。baseName 含 `-` 时同样被截断。
- **风险**: 同库部署多个 CDC 连接器（不同 base name）时，第二个连接器 recovery 读到第一个的 DDL 历史，恢复互锁/陈旧 schema 钉住；该缺陷正是本修复声称消除的类别。
- **建议**: JDBC 表名后缀改用完整实例名的表安全形式（sanitize 后的 baseName + subtask 序号，或对 instance 做稳定 hash 后缀），与文件路径变体对齐；补一条"两个不同 baseName 的函数实例不产生同名 history 表"的断言。
- **信心水平**: 确定（命名推导逻辑确定；触发需共享数据库的多连接器部署）
- **误报排除**: 不是对"实例隔离"方向的重复挑刺——文件变体已正确隔离，唯独 JDBC 变体的后缀提取函数选择了丢失 baseName 信息的实现；这是同一修复内部两条路径不一致的结构性缺陷，不是同类审计中"共享存储必然互踩"的高频泛化。
- **复核状态**: 未复核

### [REG-4] CC-02 重放 gap 收口仍留有残余双投递窗口，"no record is double-delivered" 的 javadoc 断言过强

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/SupervisionLoop.java:1060-1110`（T1→T3→T3'→T4 序列与 `assembleReplaySegment` 身份去重）
- **证据片段**:
```java
// T3 —— store snapshot via the channel's raw replay (does not touch the queue).
List<MaterializedElement> snapshot =
        tempChannel.replayMaterialized(consistentCutEpoch);
List<StreamElement> gap = oldPartition.drainBufferedElements();
List<StreamElement> replaySegment =
        assembleReplaySegment(snapshot, residual, gap, consistentCutEpoch);
```
- **严重程度**: P3
- **现状**: 四步序列把双投递窗口从修复前的 `(T1 排水, T3 快照)`（含 store 读取 I/O，毫秒级）压缩到 `(T3 快照, T3' 二次排水)`（同线程相邻调用，亚微秒级）。但依据"store 双写先于入队"的逐条不变量，记录 R 若在 T3 前完成 store 写、又因调度停顿在 T3' 之后才入队，则 R 同时存在于快照（→重放段）与存活队列（→活读）中——T3' 的身份去重只作用于**被排水的**元素，对 T3' 之后入队者无机会去重。窗口概率极低但非零，而 `rebuildConsumerInputGateWithReplay` 的 javadoc 仍断言 "no record is double-delivered on materialization edges"（exactly-once 表述）。
- **风险**: 极低概率的物化边重复投递；更重要的是文档契约（exactly-once 断言）超出实现可保证的范围，后续维护者可能据此做出依赖该断言的决定。
- **建议**: 二选一：把 javadoc 措辞收敛为"窗口压缩到 (T3, T3')，残余依赖 at-least-once 下游容忍"；或在 T4 attach 时对快照集合做代际标记、活读路径对带标记记录做二次去重（代价较大，需权衡）。
- **信心水平**: 确定（时序推演确定；实际触发需要生产者在 store 写与入队之间停顿超过 T3→T3' 间隔）
- **误报排除**: 不是重提 CC-02 已修复的原始缺陷（那是一个宽得多、含控制事件丢失的窗口，已收敛）；本条针对的是修复后**残余**窗口与文档断言不匹配这一点，属于修复自身引入的契约表述风险。
- **复核状态**: 未复核

### [REG-5] ST-12 自动 TTL 清扫在 checkpoint 恢复后永久失效——`anyTtlBound` 门旗不会被重新武装

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryKeyedStateBackend.java:188-192,477-481`
- **证据片段**:
```java
private void maybeSweepExpiredEntries() {
    if (!anyTtlBound || ttlSweepIntervalMillis <= 0) {
        return;
    }
    ...
private void applyTtl(Object stateObj, StateDescriptor<?> descriptor) {
    ...
    TtlContext<TypedNamespaceAndKey> existing = aware.ttlContext();
    if (existing != null && existing.getConfig().equals(cfg)) {
        return;                      // 恢复后既有 ctx 命中此分支，anyTtlBound 不置位
    }
    aware.bindTtl(new TtlContext<>(cfg, ttlClock));
    anyTtlBound = true;
}
```
- **严重程度**: P3
- **现状**: `anyTtlBound` 是 `transient`，恢复后为 false；而恢复态的 TTL state 经 `applyTtl` 重新访问时因"已有等值 TtlContext"提前返回，永不把门旗置回 true。结果：恢复后的作业代际里 `setCurrentKey` 驱动的 60s 周期清扫静默停摆，过期条目退回"仅访问时惰性淘汰/快照时排除"——正是 ST-12 声称要补的"再也不被访问的条目常驻 JVM"问题在恢复代际重现。`cleanupExpiredEntries()` 显式入口不受影响。
- **风险**: 长生命周期、多代恢复的内存后端作业状态内存缓慢膨胀（无正确性损失：过期条目仍被快照排除与惰性淘汰）；ST-12 的守卫测试用全新 backend，恢复代际回退不可见。
- **建议**: `applyTtl` 的等值提前返回分支同样置 `anyTtlBound = true`；或把门旗改为在 `restoreState`/反序列化完成后按 `states` 中是否存在 TTL state 重算。
- **信心水平**: 确定（代码路径确定）
- **误报排除**: 不是要求 TTL 语义变更——过期判定本身正确；这是修复（fix）自身的接线缺口（transient 门旗 × 等值早退分支），属于典型的 fix-of-fix 面。
- **复核状态**: 未复核

### [REG-6] plan 2306 stableHash 统一 murmur3：裁定兼容性成立，但 StateShard javadoc 的旧哈希平价承诺未同步，代码注释的缺陷归因对 String 键不成立

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/shard/KeyGroupAssignment.java:68-76`；`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/shard/StateShard.java:95-115`
- **证据片段**:
```java
// KeyGroupAssignment.stableHash（plan 2306 后）:
// 回归覆盖 stream-2pc 单 subtask 归集（plan 2306 Phase 4）：
// 所有值类型统一走 canonical JSON + murmur3_32。此前 String/Integer 等直接用
// Object#hashCode()（算术递增），顺序 key 族（key-0/key-1/...）会全部落到相邻
// key-group，P>1 时 100% 塌缩到单一 subtask——并行度静默退化为 1。

// StateShard.computeShardId javadoc（未同步）:
// For built-in value types (String/Long/Integer/...) this is identical to the
// legacy {@code key.hashCode()}, preserving routing parity;
```
- **严重程度**: P3
- **现状**: 移除 `isStableValueHashType` 快路径、全类型走 canonical JSON + murmur3_32，路由与属主仍经同一函数（AR-01 parity 保持），旧 checkpoint 布局不兼容已在 bug 记录中按 2.0.0-SNAPSHOT 预发布裁定接受，`TestKeyGroupAssignment.sequentialKeysSpreadAcrossSubtasks` 钉住新行为——**改动本身判定为无回归**。两处残留：① `StateShard.computeShardId` javadoc 仍声称"与 legacy hashCode 完全一致、保持路由平价"，与实现相反（文档契约漂移）；② 代码注释把塌缩归因于"key-0/key-1/...（String）hashCode 算术递增"——String.hashCode 对顺序命名串是良好扩散的，该归因仅对小宇宙 Integer 键（hashCode==值）成立，注释会误导后续排查。
- **风险**: 文档/注释与实现的契约漂移会误导下一个依赖"legacy 平价"假设的改动（例如有人按 StateShard javadoc 恢复快路径即破坏 murmur 分布守卫测试所钉的布局）。
- **建议**: 同步 StateShard javadoc（删除平价承诺、指向 murmur 合同）；修正 KeyGroupAssignment 注释归因（Integer 小宇宙键 / 大 maxParallelism 下的分布退化），保留裁定引用。
- **信心水平**: 确定
- **误报排除**: 不是重审已裁定的兼容性破坏（裁定与守卫测试均在）；本条仅针对修复未同步的**周边契约文档**与不准确的根因叙述，是可复现的 grep 级漂移（`identical to the legacy` 于 StateShard.java:113）。
- **复核状态**: 未复核
  → 已修复：plan 2317（ai-dev/plans/2317-nop-stream-r5-doc-contract-batch.md Phase 1，commit 待填，2026-10-05）

### [REG-7] keyBy 路由的 maxParallelism 在建图期一次性钉定——后配/换用 state backend 时路由与属主静默失配；parallelism>maxParallelism 的失败点深藏在首条记录

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/datastream/DataStreamImpl.java:302,434-446,465-487`
- **证据片段**:
```java
public <K> KeyedStream<T, K> keyBy(KeySelector<T, K> key) {
    IPartitioner<T> partitioner = new KeySelectorPartitioner<>(key, resolveKeyGroupMaxParallelism());
    ...
private int resolveKeyGroupMaxParallelism() {
    CheckpointConfig checkpointConfig = environment.getCheckpointConfig();
    IStateBackend backend = checkpointConfig != null ? checkpointConfig.getStateBackend() : null;
    int maxParallelism = backend != null ? backend.getMaxParallelism() : KeyGroup.DEFAULT_MAX_PARALLELISM;
```
- **严重程度**: P3
- **现状**: AR-01 修复（plan 368 Phase 1）把路由公式换为 `assignToSubtask(key, maxParallelism, numPartitions)`，maxParallelism 在 `keyBy()` 调用瞬间从 env 的 checkpoint-config backend 读取并钉死。若用户先 `keyBy` 后 `env.setStateBackend(custom)`（CheckpointConfig 支持该顺序），或运行时装接的 backend 与建图期可见的 backend 不同（其 `getMaxParallelism()` 可配置），路由用的 maxParallelism 与 keyed-state 属主用的 maxParallelism 静默分叉——记录被路由到不持有其状态的 subtask，AR-01 parity 被绕过且无任何告警。另：`assignKeyGroupToSubtask` 对 `parallelism > maxParallelism` 抛类型化异常，该检查只在**首条记录路由时**触发（默认 maxParallelism=128 下配置并行度>128 的作业可部署成功、跑起来即炸），失败点深于部署期。
- **风险**: 特定配置顺序下 AR-01 修复的核心承诺（路由/属主一致）静默失效，症状是恢复后 keyed state 不可达/重复——排查面远高于失败点前移的成本；parallelism>128 作业的失败延迟到运行期首条记录。
- **建议**: ① 部署期（plan build / env.execute）对"建图期钉定的 maxParallelism == 运行期 backend 解析值"做一致性断言，不一致即类型化失败；② 在 `setParallelism`/部署校验处前置 `parallelism <= maxParallelism` 检查（复用 `KeyGroupAssignment` 的校验），把失败点从数据面提到部署面。
- **信心水平**: 很可能（分叉路径代码确定；依赖用户先 keyBy 后换 backend 的配置顺序，API 完全允许）
- **误报排除**: 不是否定 AR-01 修复本身（旧公式与属主公式 100% 不一致，新公式仅特定顺序下失配，属严格改进）；本条针对修复**新增的捕获时机**这一实现选择留下的缺口，修复前不存在"看似一致实则失配"的假象。
- **复核状态**: 未复核

---

## 检查范围

**逐 hunk 深查的提交**（生产代码 hunks 全读，live code 交叉验证）：
plan 368 全部 8 个 Phase 提交 + 补提交（`0dc22b8fab`、`bcadd20c72`、`a6cb2a5731`、`e20b7cef1b`、`4cb16e87db`、`2bb7c013a7`、`5f9c6989bc`、`598e52e71f`）；plan 369 全部 4 个 Phase 提交 + 补提交（`cb01757ef5`、`b738d61212`、`765ada3386`、`24b935140d`、`821e322d24`）；plan 2306 两个 nop-stream 触点提交（`bc9c444cf3`、`05e81257e6`）；CDC/B 层（`794e68a5a0`、`3c542f7046`、`75a3a368a9`、`1dad44a0c6`、`706a5b8a4c` 摘要级 + 关键 hunk）；stream-sql 修 MAJ-1（`754556b002`）；nop-message WI9/WI11/WI12/WI14 深查（`175d0955dd`、`a94def93a5`、`e81391dd01`、`8a166066df`），WI6/WI7/WI8/WI10/WI13/WI15-WI18 为提交信息 + diffstat + 抽样 hunk 级覆盖。

**治理批次（plan 368 Phase 8「行为保持」）抽查 5 项**：
1. `StreamMessageEnvelope.CONTROL_HEARTBEAT` 删除 → 全仓 grep 0 引用（仅 TaskManager 保留无关的 TM 心跳间隔常量）；
2. 8 个 `ERR_STREAM_*` 错误码删除（CHECKPOINT_EXECUTOR_NOT_INITIALIZED/ALREADY_STARTED/SNAPSHOT_FAILED、CHAINING_OUTPUT_SNAPSHOT/RESTORE_FAILED、WINDOW_AGGREGATOR_*×3）→ 全仓 grep 0 引用；
3. `IStateBackend` javadoc 摘除 `RedisStateBackend` 链接（该类不在 -core，链接悬空修复）→ 纯文档；
4. `TestRemoteInputChannelHeartbeat...java → TestRemoteInputChannelLiveness.java` 改名 → git show 确认 0 内容变化；
5. `nop-stream/pom.xml` surefire `forkedProcessTimeoutInSeconds=300` → 带 42s 基线校准注释，无行为面。
抽查结论：**治理批次「行为保持」声明成立**，未发现伪装成治理的语义变更。

**配套测试保护力抽验（变异式推演）**：`TestKeyRoutingOwnershipParity`/`TestKeyGroupAssignment`（钉 murmur 公式与 enum 分离——回退 stableHash 会红）、`TestPendingCheckpointAbortFuture`（钉 ST-01——回退 CAS 门控会红）、`TestCepOperatorProcessingTimeBucketDrain`（钉 CEP-01——摘除 ledger 镜像会红）、`TestJdbcTwoPhaseCommitLedgerNamespace`（钉 CON-01 但**只覆盖 copy 注入路径**，remote taskIndex-0 原始链路径无守卫 → REG-1 的保护力缺口）、`TestRegionRestartUnreplayableInternalEdge`（只钉有损路径，安全路径无守卫 → REG-2 的保护力缺口）、`TestJobCoordinatorRecoveryRobustness`/`TestReplayGapClosure`（钉 REG-02/CC-02 序列）。

**已知未深查面**（声明边界）：stream-sql merge 分支的新特性代码（非修复面）、nop-message WI 系列的测试重写细节、`fraud-example`、`_` 前缀生成产物（按口径排除）。

## 修复批次总评

三个修复批次（368/369/2306）整体质量高：修复方向与声明一致，错误分层（typed ErrorCode + fail-fast）执行彻底，绝大多数修复带正向断言的回归测试而非"改测试凑绿"；治理批次的「行为保持」声明经抽查属实；plan 369 的 C2/C3 终点 checkpoint 生命周期与 plan 368 的 CC-02 重放序列是本批次中推理最严密的两处，其注释链可复算。发现的问题集中在三类：**修复覆盖面的部署形态缺口**（REG-1 remote subtask-0、REG-3 跨连接器共库——两处都是"修复了 A 形态、B 形态同构缺陷残留"）、**fail-fast 检测条件过宽**（REG-2 把"无法判定"当"确认有损"）、**修复自身的接线/文档尾巴**（REG-4/5/6/7）。无 P0；唯一 P1（REG-1）触发面明确（remote 部署 + 并行 sink / region restart），建议优先修复并补 remote 形态守卫测试。

**工具基线**：无缺失。git log/show/diff 与全仓 grep 均可用，未发现需要 maven/测试基线才能裁定的悬点；所有定级依据均来自 live code 的可复现 grep/show 证据。

---

## 深挖第 2 轮追加

- **深挖日期**: 2026-10-05
- **审计人**: fix-of-fix 深挖第 2 轮子代理（独立 fresh session）
- **只读声明**: 本轮全程只读（git log/show/diff + grep + 源码阅读），未运行 maven/测试，未修改任何产品代码；仅在本文件末尾追加本节。

### 第 2 轮覆盖面（第 1 轮清单的盲区补齐）

1. **遗漏的修复批次（第 1 轮未逐 hunk 覆盖）**：
   - plan 366 Phase 2 `5e33afea72`（9 项修复：N1 attachPendingReplay / N2 内部边复用 / F1 COMPLETED 跳过 / N3 心跳过滤 / N4 JDBC 目录响亮失败 / A2' / B6' / S1 / S2）——全 hunk 审查 + HEAD live 复核；
   - plan 01 Phase 2 `adb1682afb`（11 项修复：A1/A2/A7/A8/A9/A12/B1/B3/B4/B5/B6）——全 hunk 审查 + A8 全调用点收口验证 + A2/A2' 重试循环有界性验证；
   - plan 01 Phase 3 `7a706eebbf`（E3/F1/F2 perf 留舍）——F2 复用键别名风险专项核查（不可变新实例 + 单槽缓存，无别名）；plan 366 Phase 4 `3051805cc6`（P2 缓冲化/P4 惰性分配）——diff 审查无行为漂移；
   - stream-sql 分支（经 merge `6e097ee235` 引入）的行为修复链：`66ef34f979`（B-1 keyed rework）、`5ed2b57750`（R-1/R-2）、`ec9b6af5eb`（回归守卫）、`754556b002`（MAJ-1）——含 `5ed2b57750`→`ec9b6af5eb` 的批次内自愈链核查（seqFor 首匹配缺陷 20 分钟内被同批次修复并钉同 ts 守卫测试，HEAD 无残留缺陷）。
2. **nop-message WI 系列补深查**（第 1 轮仅抽样）：WI13 `8f421b0593`、WI15 `68d5024932`、WI16 `540a32d56c`、WI17 `f16ba9b0e7`——全 hunk 审查。
3. **已有发现模式横向排查**：attachPendingReplay 双重 attach 孤儿化（已被后续 REG-07/AR-05 fail-fast 收口，非缺陷）；transient 门旗 × 等值早退模式（仅 memory backend 一处，REG-5 已覆盖）；身份推导族（WI13 组身份、WI8 Pulsar seek、server id 混名——残余碰撞面需同名连接器或构造性 hashCode 相等，判定为低价值不报）；B 层 JDBC checkpoint storage 同型静默冷启动（N4 已统一 typed 异常，无残留）。
4. **治理批次再抽查 4 项**（叠加第 1 轮 5 项）：plan 368 Phase 8 的 RD-02 `ERR_STREAM_INIT_ERROR` 接线（裸串→typed ErrorCode，方向良性且提交信息明示）、REG-05 `FileSourceReader.readBuf` 初始化器迁移（openSplit 先于一切读路径赋值，行为保持成立）、RemoteResultPartition 心跳删除 + CONTROL_HEARTBEAT 全删零残留（仅剩 kafka consumer 无关域配置项）；plan 366 Phase 3 的 `processWatermarkStatus1/2` 删除（全仓 grep 零残留、单输入转发路径完好、IndexedCombinedWatermarkStatus 保留在用）与 `TtlCleanupStrategy` 构造器单参化（2.0.0-SNAPSHOT 预发布裁定同口径）。**两批次「行为保持」声明经再抽查成立**。

### 发现条目（第 2 轮新增）

### [REG-8] WI16 ConsumeFailure 派发上下文三缺陷：stale topic 违反同提交写下的契约、Kafka/Pulsar 批量失败上下文语义相反、topic 字段跨派发滞留

- **文件**: `nop-message/nop-message-kafka/src/main/java/io/nop/message/kafka/KafkaConsumeTask.java:452-465`（dispatch）、`:426-436`（批量路径填上下文）；`nop-message/nop-message-pulsar/src/main/java/io/nop/message/pulsar/PulsarConsumeTask.java:236-249`（dispatch）、`:162-166`（批量路径清空）、`:270-274`（fail 填充）；`nop-kernel/nop-api-core/src/main/java/io/nop/api/core/message/ConsumeFailure.java:30`（契约 javadoc）
- **证据片段**:
```java
// 两个 dispatch（Kafka :452-465 与 Pulsar :236-249 同构）——只清 location/message，不清 topic：
String location = lastFailureLocation;
Object message = lastFailureMessage;
lastFailureLocation = null;
lastFailureMessage = null;          // lastFailureTopic 从未置 null
...
config.getConsumer().onConsumeFailure(new ConsumeFailure(
        lastFailureTopic, location, message, attempt, e));

// ConsumeFailure.getTopic javadoc（同提交写下）：
/** Source topic; null for framework-level failures (poll/commit). */

// Kafka 批量失败路径（:426-436）——按进度指针填消息级上下文（attempt 计入）：
if (progress < recordList.size()) {
    ConsumerRecord<String, String> failed = recordList.get(progress);
    lastFailureTopic = failed.topic();
    lastFailureLocation = failed.topic() + "-" + failed.partition() + "@" + failed.offset();
    ...
}
// Pulsar 批量失败路径（:164-166）——显式剥为"框架级"形态（attempt=0）：
lastFailureLocation = null;
lastFailureMessage = null;
```
- **严重程度**: P3
- **现状**: WI16（`540a32d56c`，D6 第一层）引入的失败上下文通道有三处修复自身缺陷。① `lastFailureTopic` 在两个后端的 `dispatchConsumeFailure` 中均只读不清：一旦某条记录失败过一次（topic 被填），此后任何框架级失败（poll/commit 异常，location==null）派发的 `ConsumeFailure.topic` 都是该陈旧 topic——直接违反同一提交在 `ConsumeFailure.getTopic` 上写下的 "null for framework-level failures" 契约，且两个后端还不对称（Kafka 批量 catch 只清 location/message 留 topic；Pulsar 批量 catch 显式清 location/message 同样留 topic）。② 同一事件类别（批量消费失败）在两个后端派发出相反的契约形态：Kafka 按进度指针填 location/message 并计入 attempt（消息级），Pulsar 显式置空派发为 attempt=0（框架级）——可移植的 onConsumeFailure 处理器无法对批量路径写出一套语义。③ 结合 ①：Pulsar 批量路径清空后若此前有消息级失败残留 topic，框架级形态还携带陈旧 topic，与 ① 的"应为 null"叠加为双重失真。
- **风险**: 新特性（F-20）的回调契约在交付当天即与自身文档漂移；依赖 `attempt`/`topic` 做毒丸升级或死信路由决策的用户代码在 Pulsar 批量路径与框架级失败路径上得到错误输入（诊断误导优先，WI21 规划的死信转复用 `getMessage`/`topic` 后会升级为行为面）。无数据丢失。
- **建议**: dispatch 中将 `lastFailureTopic` 与 location/message 一并清空（三处：Kafka dispatch、Pulsar dispatch、Pulsar 批量 catch 补 `lastFailureTopic = null`）；对 Kafka/Pulsar 批量失败路径的上下文形态做一次显式裁定（建议对齐 Kafka 的进度指针形态，Pulsar 可从 batch 内首条未确认消息取位置），并在 ConsumeFailure javadoc 或 backend-semantics-design 的 D6 条目记录裁定。
- **信心水平**: 确定（代码路径与契约文本均为可 grep 复现的同提交内矛盾）
- **误报排除**: 不是质疑 D6 事件机制本身或 attempt 计数公式（计数与成功清零逻辑正确，测试钉住 [1,2]）；三条都是派发前的**上下文状态管理**缺陷——契约文本（同一提交产物）与实现互斥，且双后端对同一场景语义相反，属修复自身的接线尾巴而非设计异议。
- **复核状态**: 未复核

### [REG-9] PerKeyOrderedBuffer.seqFor 修复后保留为公开陷阱 API——首匹配语义正是同批次刚修掉并钉了守卫测试的 keyed 镜像碰撞缺陷形态，且零调用方

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/buffer/PerKeyOrderedBuffer.java:201-211`（HEAD 位置；缺陷引入于 wi12 `5ed2b57750` 时的 runtime/operators/windowing/ 同名文件）
- **证据片段**:
```java
// seqFor —— 首匹配 + -1 哨兵（HEAD，零调用方）:
/** the insertion-seq recorded for a (key, ts) entry — keyed-state key suffix. */
public long seqFor(K key, long ts) {
    TreeMap<long[], V> tree = buffers.get(key);
    if (tree != null) {
        for (long[] tsKey : tree.keySet()) {
            if (tsKey[0] == ts) {
                return tsKey[1];     // 首匹配：同 ts 多条目时返回最旧 seq，非调用方刚 add 的那条
            }
        }
    }
    return -1L;                      // 不可达哨兵 → 键 "key\0ts\0-1" 永不被 trim 清理
}

// 修复链（ec9b6af5eb）已把唯一调用方改为 add() 返回的精确 tsKey：
long[] tsKey = buffer.add(key, ts, value);
bufferState.put(key + "\u0000" + ts + "\u0000" + tsKey[1], value);
```
- **严重程度**: P3
- **现状**: wi12 R-1 修复（`5ed2b57750`）用 `seqFor(key, ts)` 推导 keyed-state 条目键后缀，但其首匹配语义在同 (key, ts) 重复记录（事件时间流中完全现实）下让第二条记录的 durable 写覆盖第一条——该缺陷在 20 分钟后由 `ec9b6af5eb` 以"add 返回精确 tsKey"修复，并落地 `addReturnsDistinctTsSeqForSameTimestamp` 守卫测试。修复只改写了调用方，`seqFor` 本身保留为 public 方法（全仓 grep 零调用方）且 javadoc 仍宣称它是 "keyed-state key suffix" 的推导入口。这正是同批次审计自己在 R-2 条目里定性的"零调用方潜伏陷阱"形态的复刻。
- **风险**: wi12 审计与 roadmap 明确登记 WI13 join 缓冲"同规则复用"（join-operator.md 绑定句）——下一个复用者按 javadoc 把 `seqFor` 当键推导入口即原样复活刚修复并钉了测试的碰撞缺陷；`-1` 哨兵分支还会产生永不清理的 `key\0ts\0-1` 残留条目。
- **建议**: 删除 `seqFor`（零调用方、语义已被 `add` 返回值取代）；若因 API 面保留，至少改写 javadoc 明示"勿用作 keyed-state 键推导（首匹配会碰撞），用 `add` 返回值"并在方法体断言禁止 -1 路径外泄。
- **信心水平**: 确定（零调用方为可复现 grep；缺陷形态与守卫测试为同批次提交链可证）
- **误报排除**: 不是重审已修复的 R-1 缺陷（HEAD 主路径正确且被测试钉住）；本条针对修复收尾时**遗留的陷阱 API**——保留一个语义与刚修复缺陷完全同形、javadoc 仍诱导误用的 public 方法，属于与 R-2（rebuildViewFromKeyedState 防重入前同样是零调用方陷阱）同簇的治理遗漏。
- **复核状态**: 未复核

### 第 2 轮核查后无新发现的领域（避免后续重复排查）

- plan 366 Phase 2 的 N1/N2/F1/N3/N4/A2' 本体、与 plan 369 C3（COMPLETED 契约收缩）及 plan 368 REG-01（内部边 fail-fast）的跨批次语义交互——未发现矛盾（区域拓扑下"下游 COMPLETED ⇒ 上游已完成"的自洽性成立）；
- `attachPendingReplay` 双重 attach 孤儿化窗口——已被后续 REG-07/AR-05 fail-fast（live `ResultPartition.attachPendingReplay` 二次 attach 抛 typed 异常）收口；
- A2/A2' 的 `checkpointSuccessMap`/`failedCommitParticipants` 生命周期——重试循环逐轮摘除、shutdown 兜底 clear，无泄漏路径；
- F2 复用键（`cachedNamespaceAndKey`）——不可变新实例 + 单槽 (key,ns) 缓存，无 Map 别名风险，且有 `TestMemoryInternalStateCachedKeyIsolation` 隔离测试；
- WI13 组身份前缀化、WI15 sendTimeout/cancelToken 接线（无独立同步 send 路径，接线完整）、WI17 maxPollIntervalMs/F-39 同步抛包装——均闭环；
- WI5 dmlMode 列集首记录钉定——per-subtask 实例生命周期内钉定 + 漂移 typed 拒绝是声明契约（非 REG-7 式静默失配），`copyForSubtask` 两变体均完整传递 DML 契约与 ledger namespace；
- 治理批次再抽查 4 项（见覆盖面第 4 条）——「行为保持」声明成立。

---

## 维度复核结论

- **复核日期**: 2026-10-05
- **复核人**: R5 修复批次回归审计·独立维度复核子代理（fresh session，与第 1/2 轮审计人无会话共享）
- **只读声明**: 本轮全程只读（git show/log + 全仓 grep + live code 重走），未运行 maven/测试，未修改任何产品代码；仅在本文件末尾追加本节与最终保留项表。所有行号引用以 HEAD `bd089444` live code 复核为准。

### [REG-1] 裁决：**保留（P1）**——核心缺陷全链路确证，但"后果二"（region restart 翻转身份）不可达，需从发现中剥离

重走完整代码路径逐环确认：(a) `RemoteGraphExecutionPlanBuilder.assembleSubtasks` :286-288 对 `taskIndex == 0` 复用原始 OperatorChain、仅其余 subtask 走 `deepCopy(taskLocation)`——证据行号准确；(b) 生产构造链 `JdbcTwoPhaseCommitSinkConnectorFactory:96 → JdbcTwoPhaseCommitSinkBuilder.build():137-141` 恒传 `ledgerNamespace=null`；(c) `JdbcTwoPhaseCommitSink.effectiveLedgerNamespace():418-420` 回退 `tableName`；(d) `commit():643-658` 守卫命中仅 WARN 后 `connection.commit()` 并 return——**数据写被静默跳过，确认是数据丢失**；(e) remote 部署三入口（`RpcDistributedExecutor.buildDataPlanePlan:354`、`EmbeddedDistributedExecutor:295`、`TaskManager.deployTask → SubtaskPlanBuilder.buildSubtaskPlan:135`）全部经 `buildRemoteOnly`，taskIndex-0 原始链路径在生产主形态真实可达；f) 跨 job 共库同名表与同 job 双分支同表两场景的碰撞键 `(tableName, epoch, 0)` 推演成立（v2 主键 `(sink_namespace, epoch_id, subtask_id)` 已核实）；g) `TestJdbcTwoPhaseCommitLedgerNamespace` 确认只覆盖 `copyForSubtask` 注入路径，原始链路径零守卫。**但后果二不成立**：`SupervisionLoop`（唯一 `rebuildTask` 调用方，经 `GraphModelCheckpointExecutor:850`）所跑计划全部由内嵌构建器 `GraphExecutionPlan.build` 构建（`GraphModelCheckpointExecutor:583-611`），内嵌路径对含边顶点的 `needsCopy` 恒真、subtask-0 建图期已携带 `jobId|vertexId`，restart 前后身份稳定不翻转；remote 侧无任何计划进入 SupervisionLoop，其 recovery 重部署重走 `buildRemoteOnly`，身份保持 `tableName`（稳定但仍在碰撞，无翻转）。P1 定级由后果一独立支撑（CON-01 明示保护的"不同 job 共库"场景在主部署形态静默丢数），维持不变；修复建议同 adopt，但验收断言只需覆盖 remote taskIndex-0 路径，restart 翻转断言撤消。

### [REG-2] 裁决：**保留（P2）**——检测条件过宽确证；一处机制描述修正（非预算烧尽，而是首次重启即中止）

逐行复核 `failFastOnUnreplayableInternalEdge`（:605-656）：触发条件确为"存在任一已完成 checkpoint + 消费者非 COMPLETED 待重建 + 非物化通道 + 分区已 sealed"，不对照 checkpoint epoch 是否覆盖分区数据——过宽判定成立。安全场景结构性真实：有界流生产者发完 EOS 封闭分区后，消费者 checkpoint N 可在全部记录消费完毕之后完成（此后仅在 EOS/终态刷出处理中失败），回滚到 N 后残余+EOS 重交付为零丢失的正确恢复，修复前可干净重启、修复后即 fail-fast；`TestRegionRestartUnreplayableInternalEdge` 确认只有单条有损路径测试（`internalEdge_completedProducer_consumerRebuild_withCheckpoint_failsLoud`），安全路径无守卫。**机制修正**：`failFastOnUnreplayableInternalEdge` 在 `restartRegion` Phase 3 之前同步抛出（:542），该调用处于 `run()` 主循环 :349 且无任何 try/catch 包裹——异常直接穿透监督循环终止运行，**不存在"重试复现同一条件直至 restart 预算耗尽"的中间过程**（第一次重启尝试即 job 失败）。此修正不减轻发现：对可恢复故障的结局同样是不可恢复的 job 失败，且更即时。P2 维持。

### [REG-3] 裁决：**保留（P2）**

`qualifySchemaHistoryPerInstance`（:661-673）与 `lastSegment`（:675-678）逐行确认：文件变体用完整 `instance`（:667），JDBC 变体用 `lastSegment(instance)`（:672）只剩 subtask 序号；`instanceName():535-536` 确认为 `baseName + "-" + subtaskIndex`，故 `cdcA-0` 与 `cdcB-0` 均截断为 `_0`——跨连接器同库同序号碰撞成立，baseName 含 `-` 同样被截断。同修复内 javadoc 自称 "instance-suffixed table name" 而实现是 index-suffix，自我契约矛盾成立。文件变体正确、JDBC 变体同构缺陷残留的"修复内部不一致"定性准确。P2 恰当（多连接器共库是现实部署形态，后果为 recovery 互锁/陈旧 schema，非静默丢数）。

### [REG-4] 裁决：**保留（P3）**

时序不变量复核确认：`ResultPartition.write():296-297` 中 `dualWriteToMaterialization` 先于 `enqueueWithBackpressure`，两调用间无原子性；生产者线程在 store 写后、入队前被调度停顿且停顿跨越 T3 快照与 T3' 二次排水，则该记录同时存在于快照（→重放段）与存活队列——T3' 身份去重只作用于被排水的元素，结构性窗口成立。文档断言确证：类级 "Exactly-once safety argument" javadoc :131-132 明文 "no record is double-delivered on materialization edges"，另有 :1083 "every record is delivered exactly once" 与 :1130 "exactly-once holds"——契约表述超出实现可保证范围成立（原文将该断言归于 `rebuildConsumerInputGateWithReplay` javadoc，实际在类级 javadoc 与内联注释，归属小误不影响实质）。P3 恰当（窗口亚微秒级概率极低 + 文档契约漂移）。

### [REG-5] 裁决：**驳回**——恢复路径重建全新 state 对象，TtlContext 恒为 null，门旗必然重新武装

反证链：region restart 的 keyed state 恢复路径为 `SupervisionLoop.rebuildTask → resolveConsistentCutEpochAndRestoreOperators:934 → GraphModelCheckpointExecutor.restoreOperatorsFromState:1526 → RescaleStateAssembler.restoreOperatorsFromState:454 → AbstractStreamOperator.restoreState:275/279 → keyedStateBackend.restoreState(StateSnapshot) → MemoryStateSerDe.restoreState`。`MemoryStateSerDe.restoreValueState:389-394`（及 map/list/reducing/aggregating 各族同构）对每个 state **新建对象**（`new MemoryValueState<>(backend, descriptor)` 等），`AbstractMemoryState` 构造后 `ttl` 字段为 null（构造函数不绑 TTL）；快照格式（`snapshotKeyedState:222-252`）也从不携带 TtlContext。因此恢复后首次带 TTL 描述符的 `getState` → `applyTtl:467-482` 时 `existing == null`，**等值早退分支（:477-478）不可能命中**，必然走 `bindTtl` 并置 `anyTtlBound = true`（:480-481）——门旗被重新武装。早退分支要求 state 对象已有等值 TtlContext，而那只能在同一 backend 实例内此前 bindTtl 过（那一刻门旗已置 true，且 `anyTtlBound` 在存活实例上从不清回 false）；发现声称的"transient 门旗 × 恢复态既有 ctx 早退"组合要求 state 对象携 TtlContext 跨恢复存活，全仓无此路径（backend 对象本身不经 Java 序列化参与恢复，序列化面是 StateSnapshot Map）。发现的证据片段与行号准确，但其断言的代码路径在当前恢复管线下不可达，整体判误报。

### [REG-6] 裁决：**保留（P3，修正后）**——javadoc 漂移成立；"注释归因错误"子项驳回

子项①成立：`StateShard.stableHash` javadoc（:111-117）仍声称 "For built-in value types (String/Long/Integer/...) this is identical to the legacy key.hashCode(), preserving routing parity"，而 `KeyGroupAssignment.stableHash`（:78-80）已全类型走 canonical JSON + murmur3_32（仅 Enum 与不可 JSON 化回退例外），文档契约漂移确证；且漂移面比发现所引更大——`KeyGroupAssignment` 类级 javadoc（:26-28 "hashCode() is reused directly"）同样未同步。子项②驳回：审计断言 "String.hashCode 对顺序命名串是良好扩散的"不成立——`"key-0"` 与 `"key-1"` 仅末字符不同，末字符权重为 31^0=1，两者 hashCode 恰好相差 1；`key-0`..`key-9` 的哈希是连续整数，`key-10`..`key-99` 聚集在约 288 的连续区间内，在 maxParallelism=128、P≤12 的常见配置下确实 100% 塌缩到单一 subtask。**被质疑的代码注释归因是正确的**，"修正注释归因"的建议撤消；保留项仅为 javadoc 同步（StateShard :113-116 与 KeyGroupAssignment 类 javadoc :26-28 两处，删除 legacy 平价承诺、指向 murmur 合同）。P3（纯文档）维持。

### [REG-7] 裁决：**保留（P3）**

逐环确认：`keyBy():302` 在建图期调用 `resolveKeyGroupMaxParallelism():434-443` 并钉入 `KeySelectorPartitioner` 的 final 字段（:463）；`CheckpointConfig.setStateBackend:399` 存在且无顺序约束，先 keyBy 后换 backend 的分叉路径 API 层面完全合法；运行期属主侧 `TaskCheckpointWiring:170-175` 从 checkpointConfig 的 backend 现场解析（`IStateBackend.getMaxParallelism()` 可配置，`MemoryStateBackend(maxParallelism)` 构造器存在）——两侧 maxParallelism 可静默分叉确证。`assignKeyGroupToSubtask:201-202` 对 `parallelism > maxParallelism` 抛 typed 异常且全仓无部署期前置校验，该异常在 `KeySelectorPartitioner.partition:477` 内触发——失败点确在首条记录路由时。P3 恰当（依赖非常规配置顺序，非默认路径）。

### [REG-8] 裁决：**保留（P3）**

三条子缺陷全部独立确证：① `lastFailureTopic` 在两个后端均为写入后永不清空（Kafka :78/:300/:432/:463、Pulsar :48/:271/:247，全文件 grep 确认无任何置 null 点），而 `dispatchConsumeFailure` 只清 location/message——框架级失败（location==null）派发携带陈旧 topic，与同提交写下的 `ConsumeFailure.getTopic` javadoc（nop-api-core `ConsumeFailure.java:30` "Source topic; null for framework-level failures"）直接互斥；② Kafka 批量 catch（:428-436）按进度指针填满消息级上下文（location 非空 → attempt 计入），Pulsar 批量 catch（:163-166）显式置空 location/message 派发框架级形态（attempt=0）——同一事件类别双后端语义相反确证；③ ①×② 叠加：Pulsar 批量路径清空后仍残留此前消息级失败的 topic，与"应为 null"双重失真。三处均为可 grep 复现的同提交内矛盾。P3 恰当（回调契约漂移 + 诊断误导，无数据丢失）。

### [REG-9] 裁决：**保留（P3）**

独立全仓 grep（`*.java/*.xml/*.xpl/*.js`）确认 `seqFor` 在 `PerKeyOrderedBuffer.java` 之外零引用（唯一命中的测试文件 `TestPerKeyOrderedBuffer` 匹配的是 `addReturnsDistinctTsSeqForSameTimestamp`，不调用 seqFor）——"零调用方"论断成立。首匹配 + `-1` 哨兵语义（HEAD `nop-stream-core/common/buffer/PerKeyOrderedBuffer.java:200-211`）与 javadoc "keyed-state key suffix" 诱导性表述逐行确认。缺陷谱系经 git 复核：`5ed2b57750` 以 `seqFor` 作 keyed 写键（首匹配碰撞），`ec9b6af5eb` 改用 `add` 返回的 `tsKey[1]` 修复并落地守卫测试，且该修复提交自身的收尾注记已明示 "seqFor 现为零调用方死代码——建议删除或挪作诊断"——遗留为治理尾巴属实。P3 恰当（无现役调用方，风险面是未来复用复活已修复缺陷）。

### 复核方法附注

- 全仓 grep 均含 XML/XPL/JS 反射与配置引用面；REG-1 的"remote 计划是否可能进入 SupervisionLoop"与 REG-5 的"恢复路径是否可能保留 TtlContext"两个关键反驳点均按调用链逐跳走查至叶子实现。
- 对声称零引用的论断（REG-9 seqFor、REG-8 lastFailureTopic 清空点）均以独立 grep 复跑验证，与原文结论一致。
- 两处机制级修正（REG-1 后果二不可达、REG-2 首次重启即中止而非预算烧尽）不改变对应发现的保留定级，理由见各条裁决。

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|---|---|---|---|
| REG-1 | P1 | `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/transport/RemoteGraphExecutionPlanBuilder.java`（:286-288；关联 `nop-stream-connector-jdbc/.../JdbcTwoPhaseCommitSinkBuilder.java:137-141`、`JdbcTwoPhaseCommitSink.java:418-420,643-658`） | remote 建图 taskIndex-0 复用原始 OperatorChain，sink ledger namespace 恒为 null 回退目标表名，跨 job/双分支共库同名表同 epoch 时幂等守卫互踩静默跳过数据写（CON-01 声称保护的场景在主部署形态残留）；复核修正：restart 身份翻转后果不可达（SupervisionLoop 仅跑内嵌构建器计划），P1 由碰撞丢数独立支撑 |
| REG-2 | P2 | `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/SupervisionLoop.java`（:605-656） | 内部边 fail-fast 不对照 checkpoint 是否已覆盖分区数据，有限流"checkpoint 先于 EOS 处理完成、终态处理中失败"的可恢复场景被误杀为 job 失败；复核修正：异常穿透监督循环首次重启即中止，非预算烧尽 |
| REG-3 | P2 | `nop-stream/nop-stream-connector-debezium/src/main/java/io/nop/stream/connector/debezium/DebeziumCdcSourceFunction.java`（:661-678） | schema history JDBC 表名后缀 `lastSegment` 只剩 subtask 序号，多连接器共库同序号互读 DDL 历史恢复互锁，与同修复内文件变体（完整实例名）不一致 |
| REG-4 | P3 | `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/SupervisionLoop.java`（:1085-1091 及 :131-132 javadoc） | CC-02 重放序列在"store 写后、入队前停顿跨越 T3 与 T3'"时残余双投递窗口非零，而类级 javadoc 仍断言 "no record is double-delivered"，契约超出实现可保证范围 |
| REG-6 | P3 | `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/shard/StateShard.java`（:111-117；关联 `KeyGroupAssignment.java:26-28`） | stableHash 全类型改 murmur3 后 StateShard/KeyGroupAssignment javadoc 仍承诺 legacy hashCode 平价，契约漂移误导后续改动；复核修正：KeyGroupAssignment 代码注释的 String 顺序键塌缩归因是正确的，该子项撤消 |
| REG-7 | P3 | `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/datastream/DataStreamImpl.java`（:302,434-443,445-489） | keyBy 建图期一次性钉定 maxParallelism，先 keyBy 后换 backend 时与运行期属主 maxParallelism 静默分叉；parallelism>maxParallelism 的 typed 失败深藏在首条记录路由，无部署期校验 |
| REG-8 | P3 | `nop-message/nop-message-kafka/src/main/java/io/nop/message/kafka/KafkaConsumeTask.java`（:452-467）、`nop-message/nop-message-pulsar/src/main/java/io/nop/message/pulsar/PulsarConsumeTask.java`（:236-251,163-166）、`nop-kernel/nop-api-core/src/main/java/io/nop/api/core/message/ConsumeFailure.java`（:30） | WI16 失败上下文三缺陷：lastFailureTopic 永不清空致框架级失败携带陈旧 topic 违反同提交 javadoc 契约、Kafka/Pulsar 批量失败语义相反、Pulsar 批量路径叠加陈旧 topic |
| REG-9 | P3 | `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/buffer/PerKeyOrderedBuffer.java`（:200-211） | seqFor 修复后保留为公开陷阱 API：首匹配语义与刚修复并钉了守卫测试的 keyed 写键碰撞缺陷同形、javadoc 仍诱导作键推导、全仓零调用方（修复提交自身已建议删除） |

被驳回项：[REG-5]（恢复路径经 MemoryStateSerDe 重建全新 state 对象、TtlContext 恒 null，首次 getState 必走 bindTtl 分支重新武装 anyTtlBound，声称的等值早退场景不可达——见复核结论该条反证链）。
