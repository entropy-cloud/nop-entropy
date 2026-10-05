# nop-stream 开放式对抗审查（2026-10-05 批次）

> 审核日期: 2026-10-05（0523 批次）
> 定位: 开放式、发现导向；重点对象是 R4 后三个修复批次（plan 368/369、plan 2306 44 项批量修复）新写入的代码，以及 stream-sql/CDC 分片/join 等新特性与既有机制（AR-01 路由 parity、JSON checkpoint 通道、keyed 当前键 scoping）的组合缝。
> 去重基线: `ai-dev/audits/2026-09/2026-09-30-0530-deep-audit-nop-stream-quality-r4/summary.md` + `ai-dev/audits/2026-09/2026-09-30-0530-adversarial-review-nop-stream/01-open-findings.md`（仅读标题/总评/发现编号）。
> 编号说明: 本轮新发现从 **AR-12** 起编（AR-01..AR-11 已被 2026-09-30 对抗审查占用）。旧项现状见文末「既有发现现状核查」。

## 发现清单

### [AR-12] remote-deploy 对 TM 侧算子强制注入默认 MemoryStateBackend——AR-01 路由/属主 parity 在 DISTRIBUTED 模式被重新打开
- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/deploy/RemoteTaskDeploySupport.java:158-181`；`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/datastream/DataStreamImpl.java:431-447,477`；`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/rpc/TaskDeploymentDescriptor.java:54-105`
- **证据片段**: ```java
// RemoteTaskDeploySupport.provisionStateBackends（javadoc 自认的分歧）
 * the LOCAL wiring first honors checkpointConfig.getStateBackend() and only
 * falls back to MemoryStateBackend when unset, while this remote path ALWAYS
 * provisions a MemoryStateBackend — the TaskManager-side deploy path has no
 * access to the job's CheckpointConfig.
    if (abstractOp.getStateBackend() == null) {
        IStateBackend backend = new MemoryStateBackend();   // maxParallelism 恒为默认 128
        abstractOp.setStateBackend(backend);
    }

// DataStreamImpl.resolveKeyGroupMaxParallelism（客户端 keyBy 时烤入）
IStateBackend backend = checkpointConfig != null ? checkpointConfig.getStateBackend() : null;
int maxParallelism = backend != null ? backend.getMaxParallelism() : KeyGroup.DEFAULT_MAX_PARALLELISM;
...
return KeyGroupAssignment.assignToSubtask(key, maxParallelism, numPartitions);
```
- **严重程度**: P1
- **现状**: R4 P0（AR-01）的修复把 keyBy 路由统一到 `KeyGroupAssignment.assignToSubtask(key, maxParallelism, parallelism)`，`maxParallelism` 在客户端建图时从 `checkpointConfig.getStateBackend()` 烤入 `KeySelectorPartitioner`。但 `TaskDeploymentDescriptor` 不携带 CheckpointConfig/IStateBackend，TM 侧 `provisionStateBackends` 无条件注入 `new MemoryStateBackend()`（maxParallelism=128）。该分歧本身已在 javadoc 登记「pending owner adjudication」，但登记文本只讨论了**后端类型**（RocksDB→内存）分歧，未讨论它对刚修复的 AR-01 parity 的后果。
- **风险**: 用户以 `env.setStateBackend(new MemoryStateBackend(256))`（或任何 maxParallelism≠128 的后端）配置并以 DISTRIBUTED 模式运行 keyed 作业时：路由按 256 算 key-group/属主，keyed 状态属主按 128 算——正是 AR-01 的双公式错位在分布式形态复活，产生幻影状态 + 恢复后 MISSING，且只在 DISTRIBUTED 出现（LOCAL 一致），极难定位。次级缝：程序化 API 用户在 `keyBy()` **之后**才 `setStateBackend(...)`，烤入值同样过期（LOCAL 也中）。
- **建议**: 短期——在 `resolveKeyGroupMaxParallelism` 旁对 DISTRIBUTED 部署加启动期 fail-fast（客户端后端非默认即拒绝 remote deploy，错误信息指向该分歧）；中期——把 `maxParallelism`（最低限度）与后端描述一起穿入 TaskDeploymentDescriptor，统一 LOCAL/remote 注入路径；并给 RemoteTaskDeploySupport 的登记补上 parity 后果一行。
- **信心水平**: 很可能（静态链路五点闭合：烤入点、descriptor 字段清单、TM 注入点、ownership 公式、分歧登记文本均核实；未运行时复现）
- **发现来源视角**: 修复代码的 fix-of-fix（R4 P0 修复自身的部署形态缝）+ 契约考古学家（javadoc 承认的分歧里藏着未登记的第二后果）

### [AR-13] PerKeyOrderedBuffer 工作视图经真实 checkpoint 存储往返后静默丢失——EquiJoin/OverWindow 恢复后空转，durable keyed 副本结构性救不回来
- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/join/EquiJoinOperator.java:140-167`；`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/OverWindowOperator.java:130-148,252-280`；`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/CheckpointSerDe.java:900-921`；测试 `nop-stream-runtime/src/test/java/io/nop/stream/runtime/operators/join/TestEquiJoinWithCheckpoint.java:91`
- **证据片段**: ```java
// EquiJoinOperator.restoreState —— 存储恢复路径的值是 JSON 反序列化的 LinkedHashMap
Object restored = snapshotResult.getOperatorState(BUFFER_STATE);
if (restored instanceof PerKeyOrderedBuffer) {          // 存储往返后恒 false → 静默跳过
    buffer.putAll((PerKeyOrderedBuffer<String, JoinSideRecord<Object>>) restored);
}

// CheckpointSerDe.deserializeOperatorState —— 只有三种 @type 再水合分支
if ("TimerSnapshot".equals(type)) { ... }
if ("PaneTrackingSnapshot".equals(type)) { ... }
if (isAccumulatorFormMap(form)) { ... }
return value;                                            // PerKeyOrderedBuffer 无分支，原样 Map 返回
```
- **严重程度**: P1
- **现状**: WI12/WI13 的 join/over-window 采用「工作视图（PerKeyOrderedBuffer，operator-state 传输）+ 逐条 keyed MapState 副本」双层设计，且 javadoc 明言 keyed 侧因当前键 scoping 无法重建整视图、**operator-state 传输就是计算状态的恢复载体**。但该载体走 `CheckpointSerDe` 的 JSON 通道（`LocalFileCheckpointStorage`/`JdbcCheckpointStorage` 都经此），`deserializeOperatorState` 仅有 TimerSnapshot/PaneTrackingSnapshot/accumulator 三个再水合分支（WindowOperator 的三个状态形态都配了 `@type` 自描述），`PerKeyOrderedBuffer` 没有；反序列化产物是 `LinkedHashMap`，两个算子 restoreState 的 `instanceof` 守卫静默跳过（无日志）。全恢复链路核实：`restoreDeployedSubtaskFromStorage → CheckpointSerDe.deserializeCheckpoint → RescaleStateAssembler.restoreOperatorsFromState → buildSnapshotFromTaskState（原样透传 Map）→ op.restoreState`。现有测试 `TestEquiJoinWithCheckpoint` 直接把内存中的活对象传给 `restoreState`，从未穿过存储序列化。
- **风险**: 崩溃/kill 后从 durable checkpoint 恢复的作业：join 工作视图为空 → 崩溃前缓冲未匹配的双侧记录全部静默丢失（INNER 少输出、OUTER 少补全）、`matched` 标志丢失；OverWindow 同病。更糟的是 OverWindowOperator 的「durable copy 兜底」`rebuildViewFromKeyedState()` **生产代码零调用**，且实现走 `MemoryMapState.entries()`（当前键 scoping，`getMap()`），fresh 实例上根本遍历不到全量条目——类注释「the view is rebuilt from these entries on restore」是不成立的声明。恢复语义对用户完全不可见（恰好违反本项目 No-Silent-No-Op 准则）。
- **建议**: (1) 给 `PerKeyOrderedBuffer` 配 `@type` 自描述 form + `CheckpointSerDe` 再水合分支（对齐 WindowOperator 三形态先例），或恢复后从 Map 形态响亮重建；(2) `instanceof` 失配分支至少 LOG.warn/error（fail-loud）；(3) 补一条「checkpoint 写盘 → CheckpointSerDe 往返 → restoreState」的钉子测试（现有测试全部绕过序列化）；(4) 删除或修正 OverWindowOperator 两处「rebuilt from keyed entries」的失实注释与死方法。
- **信心水平**: 确定（序列化通道、反序列化分支清单、守卫静默路径、测试盲区四点全部代码核实；唯一未做的是运行时复现）
- **发现来源视角**: 组合爆炸（新 join 特性 × JSON checkpoint 通道 × 当前键 scoping）+ 异常路径侦探

### [AR-14] keyBy 路由热路径每记录做 JSON 序列化 + murmur3——plan 2306 stableHash 统一化把 O(1) 哈希变成 O(key 大小) 的反射序列化，路由侧无缓存
- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/shard/KeyGroupAssignment.java:62-92`；`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/datastream/DataStreamImpl.java:439-447`；对照组（有缓存）`MemoryKeyedStateBackend.java:527-552`、`RocksDBKeyedStateBackend.java:505-539`
- **证据片段**: ```java
// KeyGroupAssignment.stableHash —— plan 2306 Phase 4：所有值类型统一 canonical JSON + murmur3
try {
    String json = JsonTool.serialize(key, false);
    return HashHelper.murmur3_32(json);
} catch (RuntimeException notSerializable) {
    LOG.warn("Key of type {} is not JSON-serializable; falling back to identity hashCode ...",
            key.getClass().getName(), notSerializable);      // 每次调用都打 WARN+堆栈
    return key.hashCode();
}

// KeySelectorPartitioner.partition —— 每条记录一路调到这里，无任何记忆化
return KeyGroupAssignment.assignToSubtask(key, maxParallelism, numPartitions);
```
- **严重程度**: P2
- **现状**: plan 2306 Phase 4 为修「顺序 key 族塌缩单 subtask」把 stableHash 从 `Object#hashCode()` 统一改为 JSON+murmur3（键→key-group 映射变更已裁定接受）。状态访问侧同步获得了单槽 (key,namespace) 缓存（`cachedNamespaceAndKey`/`cachedStorageKey`，注释明言「removes the dominant per-access CPU/alloc cost」），但 **keyBy 路由路径没有对等物**：每条记录经 `partition → assignToSubtask → assignToKeyGroup → stableHash` 做一次完整 JSON 序列化（反射遍历 + 字符串构建 + murmur3）。String 键尚可（引号转义扫描），POJO 键是全量反射树重建。R4 性能轮「无 ≥2% 低风险可收割项」的结论早于本次变更。
- **风险**: 10x 吞吐/键宽场景下路由成为每记录的显著 CPU+分配热点（估算：POJO 键每记录数百 ns 至 μs 级，百万级 QPS 下占单核 10%+，且全是短命对象加重 GC）；非 JSON 可序列化键的回退分支在生产热路径上会**每记录打一条 WARN 带完整堆栈**（日志洪水 + 本身就是身份哈希路由，AR-01 parity 静默失效但只留日志痕迹）。
- **建议**: 给 `KeySelectorPartitioner` 加与后端同款的单槽 equals 缓存（key→subtask），数值上与 `assignToSubtask` 位精确一致、不破坏 AR-01 parity 与既有 parity 测试；回退 WARN 降频（每键一次或采样）；中期为 String/数值键加免 JSON 的快速路径（须与 JSON 路径的输出值逐一钉死等价）。
- **信心水平**: 确定（代码事实）；性能影响幅度为估算档
- **发现来源视角**: 修复代码的 fix-of-fix（plan 2306 批量修复的负优化面）+ 10x 规模运维者

### [AR-15] TTL 周期清理以「数据面同步全量扫描」实现——每 60s 一条记录替全作业支付 stop-the-world
- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryKeyedStateBackend.java:171-232`
- **证据片段**: ```java
@Override
public void setCurrentKey(K key) {
    this.currentKey = key;
    maybeSweepExpiredEntries();              // 记录处理热路径上的钩子
}

private void maybeSweepExpiredEntries() {
    if (!anyTtlBound || ttlSweepIntervalMillis <= 0) return;
    long now = System.nanoTime();
    if (lastTtlSweepNanos != 0 && now - lastTtlSweepNanos < ttlSweepIntervalMillis * 1_000_000L) return;
    lastTtlSweepNanos = now;
    cleanupExpiredEntries();                 // 遍历每个 TTL state 的整个 storage map
}
```
- **严重程度**: P2
- **现状**: plan 369 Phase 3（ST-12 部分）把周期 TTL 清理挂在 `setCurrentKey`（nop-stream 无独立周期调度面，注释已说明这一取舍），默认 60s 一次、对每个绑定了 TTL 的 state 的整个 storage 做一次同步迭代删除。TTL-free 作业零成本（单布尔+单 long 比较）——这个设计对默认场景是对的；但对**启用了任一 TTL** 的作业，扫描成本与状态总量成正比且在数据面内联支付。
- **风险**: 10x 状态规模（千万级条目）下每次 sweep 是百毫秒~秒级的数据面停顿：期间 watermark 停止推进、checkpoint barrier 对齐计时器照跑——若 sweep 时长逼近 barrierAlignmentTimeout/checkpointTimeout 预算，会出现「每 60s 一次对齐超时→checkpoint abort→重试」的自节奏风暴；延迟敏感作业的 P99 呈 60s 锯齿。清理本身无预算/分批/可观测打点（sweep 耗时无 metric）。
- **建议**: 给 sweep 加时长预算（如单次上限 N 条或 T 毫秒，剩余下轮继续）+ sweep 耗时/回收条数打点；TTL 大状态作业文档标注该停顿语义；中期把触发点迁到 mailbox 空闲档或 checkpoint 间期。
- **信心水平**: 确定（机制）；量级为推测档（未运行基准）
- **发现来源视角**: 10x 规模运维者 + 异常路径侦探（sweep×barrier 对齐的组合时序）

### [AR-16] `resolveEdgePartition`/`findEdge` 对重复 from→to 边取首条且无去重校验——二条边声明的 partition 静默失效（旧 AR-08 现状复核，仍未修复）
- **文件**: `nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:1013-1026`
- **证据片段**: ```java
private StreamEdgeModel findEdge(String fromId, String toId) {
    for (StreamEdgeModel e : model.getEdges()) {
        if (fromId.equals(e.getFrom()) && toId.equals(e.getTo())) {
            return e;                        // 首条命中即返回，重复边无告警
        }
    }
    return null;
}
```
- **严重程度**: P3
- **现状**: **已知未修复**（2026-09-30 对抗审查 AR-08 原样保留）。本轮复核：builder 侧无去重校验，xdef 侧亦未见 uniqueness 约束；重复边第二条的 `partition`/`keyExpr` 声明依赖模型内边顺序地失效。
- **风险**: 与原判一致——低概率、真实存在；DSL 用户复制粘贴边声明时可触发，属「声明面承诺 > 消费面实现」的缝。
- **建议**: 建图入口对重复 (from,to) 边 fail-fast（一条错误码即可），或在 stream.xdef 加 uniqueness 断言。
- **信心水平**: 很可能（未逐条核对 xdef 约束，builder 消费面确定取首条）
- **发现来源视角**: 契约考古学家（旧项现状核查）

### [AR-17] `<checkpoint enabled="true" interval="0">` 仍静默禁用 checkpoint（旧 AR-09 现状复核，仍未修复）
- **文件**: `nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:331`
- **证据片段**: ```java
if (cfg.isEnabled() && cfg.getInterval() > 0) {
    env.enableCheckpointing(cfg.getInterval());
}
```
- **严重程度**: P3
- **现状**: **已知未修复**（旧 AR-09）。同文件 `applyCheckpointConfig` 其余字段已按 P1-XDSL-6 修成「显式 0 必须生效/非默认才写」，唯独 interval 守卫保留 `> 0`——enabled=true 与 interval=0 的组合被静默吞掉，与同文件确立的 fail-fast 哲学相悖。
- **风险**: 用户显式声明了 checkpoint 意图却得到 AT_MOST_ONCE 语义的静默降级；其余六个字段的修正反而让这行更刺眼（邻近代码的不一致）。
- **建议**: `enabled=true && interval<=0` → 类型化错误（ERR_STREAM_INVALID_ARG + 定位），与 F-04b/项 18 的「显式 0 必须生效或响亮失败」裁定对齐。
- **信心水平**: 确定
- **发现来源视角**: 契约考古学家（旧项现状核查）

### [AR-18] HashPartitionRouter 的 null-partitioner 回退仍按整条记录值哈希路由（旧 AR-11 现状复核，影响面收窄但未修）
- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/HashPartitionRouter.java:33-41`
- **证据片段**: ```java
public int selectChannel(StreamRecord<?> record) {
    if (partitioner != null) { ... }
    return Math.floorMod(record.getValue().hashCode(), numPartitions);   // 整记录身份/值哈希
}
```
- **严重程度**: P3
- **现状**: **已知未修复**（旧 AR-11）。影响面已收窄：DSL 路径现在要求 HASH 边必须带 keyExpr 且统一走 `DataStream#keyBy`（`StreamModelDslBuilder` HASH-without-keyExpr fail-fast），该回退主要暴露给程序化图构建。另注意该回退用 `Object#hashCode()`，对 POJO 是身份哈希——与 ST-03 同族的跨 JVM 不稳定路由。
- **风险**: 程序化 API 用户建 HASH 边而不注入 partitioner 时，同 key 不同字段值的记录分流、跨重启路由漂移；无任何告警。
- **建议**: null-partitioner 时响亮失败或按 record key 域哈希；至少 WARN 一次。
- **信心水平**: 确定
- **发现来源视角**: 契约考古学家（旧项现状核查）

## 既有发现现状核查（去重，不占新编号）

| 旧项 | 现状（2026-10-05 复核） |
|---|---|
| AR-01(P0, 路由/属主双公式) | 已修（plan 368 Phase 1，`assignToSubtask` 统一 + parity 测试）；但其修复在 DISTRIBUTED 形态被 AR-12 重新打开 |
| AR-02/AR-03/AR-07/AR-10 | 已修（plan 368 Phase 4/7） |
| AR-04(residual drain 丢控制事件) | 已修（SupervisionLoop T1-T4 组装保留 controls，`assembleReplaySegment`） |
| AR-05(attachPendingReplay 替换语义) | 已修（二次 attach 对未消费段 fail-fast，SupervisionLoop:1092 注释钉住） |
| AR-06(XplWatermarkGenerator.onPeriodicEmit 恒空) | **仍未修复**：`AdvancedTransforms.java:946-947` 空体原样；xpl 水位生成器只有 onEvent 通道，`watermarkInterval>0` 组合依旧静默失效（周期发射无 xpl 消费路径）。维持原判 P2，建议在本体补「声明 onPeriodicEmit body 或构造期拒绝 watermarkInterval>0+generator 组合」 |
| AR-08/AR-09/AR-11 | 仍未修复，本轮以 AR-16/17/18 重新登记并复核现状 |
| R4-ST-01/02/03、CC-01/02/03、CON-01/02/03、CEP-01、REG-01、DC-01..04 | 均已在 plan 368/369 各 Phase 落地修复（commit 0dc22b8f..24b93514 逐条对应，本轮未发现 fix-of-fix 回归——除 AR-12/AR-14/AR-15 三项修复自身的次生问题如上） |

## 总评

1. **最值得关注的方向是「operator-state 传输层是恢复语义的单点，却没有类型契约」。** WindowOperator 为三个状态形态逐一配了 `@type` 自描述 + 再水合分支，而 WI12/WI13 的新算子（join/over-window）把整份计算状态放在一个裸 `Serializable` POJO 里过 JSON 通道，`instanceof` 守卫把失败变成静默——这不是两个孤立 bug，是一个尚未成文的约定（「凡是 putOperatorState 的值必须有 @type form 或响亮失败」）漏掉了最新成员。建议把该约定写进 checkpoint-design 并由测试模板强制（AR-13）。
2. **其次是「修复正确但部署/性能面有缝」的 plan 368/369/2306 新代码**：AR-01 的统一公式只统一了 LOCAL 形态（remote-deploy 的后端注入是登记过的分歧，但其 parity 后果没被登记）；stableHash 的 JSON 化在状态侧配了缓存、在路由侧没配（AR-12/14）。这类缝的共同根因是「同一不变式有多个消费点时，修复只覆盖了当时测试经过的那条路径」。
3. **第三是规模语义**：TTL 全量 sweep 挂在数据面（AR-15）与路由每记录 JSON 化（AR-14）在 10x 规模下会最先暴露——两者都是「正确性优先的修复」未经性能收口，建议在下一个性能轮一起收割。

## 盲区自评

- AR-12/13/14 均为静态链路闭合，未运行任何复现（硬性纪律禁止跑测试）；AR-13 的「存储往返后必为 Map」依赖对 Nop JsonTool 反序列化行为的推断 + plan 2306 DmlOp 注释的旁证（「Nop JSON 还原不回水合 @DataBean 类型」），未单独验证 JsonTool 对 `Serializable` POJO 是否可能带 `@type` 回水合——若实际带，AR-13 降级为「仅跨进程恢复路径」。
- stream-sql 编译器（SQL→模型语义：TUMBLE 边界、聚合终态、多输入 union）本轮只查了接线与路由，未做 SQL 语义对抗（wi6-wi23 已有多轮专项 audit，刻意去重）。
- DISTRIBUTED 多 JVM 故障注入、unaligned checkpoint × replay 组合、RocksDB 增量段引用迁移（plan 369 C3 新代码）未做运行时推演；CEP-01 修复的 ledger 双时间域镜像已核查 ET 侧有 `!isProcessingTime` 守卫，但 restore 后 PT timer 重注册路径（reconcile 一次性）未深追。
- fraud-example、quickstart、connector-batch 本轮未入范围。

## 按严重程度分布

| 严重程度 | 数量 | 编号 |
|---|---|---|
| P0 | 0 | — |
| P1 | 2 | AR-12（DISTRIBUTED 路由/属主 parity 重新打开）、AR-13（join/over-window 工作视图存储往返后静默丢失） |
| P2 | 2 | AR-14（路由热路径每记录 JSON 序列化）、AR-15（TTL 同步全量 sweep 停顿） |
| P3 | 3 | AR-16（重复边取首，旧 AR-08）、AR-17（interval=0 静默禁用，旧 AR-09）、AR-18（null-partitioner 整记录哈希回退，旧 AR-11） |
| 合计 | 7 | 新发现 4 + 旧未修复新编号 3；另有 AR-06 维持原判未重编 |

## 维度复核结论

- **复核日期**: 2026-10-05
- **复核方法**: 只读重走 live code。对 AR-12..15 逐条独立核验证据行号、现状描述、风险推演与严重程度；对 AR-16/17/18 按 fast-path 逐点 grep 确认旧缺陷仍在。跨报告重合判定仅读取 05 维度（KR-1/KR-7）与 02 维度（ST-12）原文，未读取其他审计文件。本节为追加内容，上文发现正文未做任何修改。
- **总裁决**: 7 条全部**保留**，无一驳回、无一升级/降级。两条 P1 均为跨报告重复/同族发现：AR-12 与 05 报告 KR-1 为**同一缺陷**；AR-13 与 02 报告 ST-12 为**同一缺陷**、与 05 报告 KR-7 为**同族相邻**——汇总时须按下方合并口径去重，避免同一缺陷被重复计数。

### 逐条裁决

**AR-12 — 保留，P1。与 05 报告 KR-1 为同一缺陷（判定依据见下节）。** 独立复验五点全部成立：(1) `RemoteTaskDeploySupport.provisionStateBackends`（:171-181）对后端为 null 的算子无条件 `new MemoryStateBackend()`，而该默认构造取 `KeyGroup.DEFAULT_MAX_PARALLELISM = 128`（MemoryStateBackend.java:56、KeyGroup.java:41）；(2) `TaskDeploymentDescriptor`（:52-105）字段全集为 identity 五元组/nodeId/attemptId/jobGraph/deploymentPlan/checkpointRestorePath/pipelineSpec——**不携带 CheckpointConfig 或 IStateBackend**，TM 侧确实无法取到作业后端；(3) 客户端烤入点核实：`keyBy()` 在建图期一次性把 `resolveKeyGroupMaxParallelism()`（DataStreamImpl:434-443，读 `checkpointConfig.getStateBackend().getMaxParallelism()`）烤进 `KeySelectorPartitioner`（:302），运行期路由走 `assignToSubtask(key, M_keyBy, P)`（:477）；(4) 属主侧核实：TM 注入的后端经 `MemoryStateBackend.createKeyedStateBackend`（:99）生成 `MemoryKeyedStateBackend(keyType, 128)`，`routeKey` → `assignToKeyGroup(key, M_backend)`——M_keyBy≠M_backend 时路由/属主双公式错位，AR-01 parity 在该形态断裂；(5) 分歧登记文本（RemoteTaskDeploySupport javadoc :158-170）实读确认只讨论「后端类型被替换」（RocksDB→memory），未讨论 maxParallelism 耦合与 parity 后果。风险推演成立：DISTRIBUTED + 非默认 maxParallelism（如 `new MemoryStateBackend(256)`）的 keyed 作业静默错置。次级缝（`keyBy()` 之后才 `setStateBackend` 导致烤入值过期、LOCAL 也中）核实为真，且恰为 05 报告 KR-3 缺口(1)（建图期一次性快照、无 equality 校验）。P1 恰当（与 KR-1 同级，静默、无 fail-fast）。

**AR-13 — 保留，P1。与 02 报告 ST-12 为同一缺陷、与 05 报告 KR-7 为同族相邻（判定依据见下节）。** 引用代码逐点核实：`EquiJoinOperator.snapshotState` 活 buffer 直入 operator state（:144）、`restoreState` 的 `instanceof PerKeyOrderedBuffer` 守卫无 else/WARN（:163-165）；`OverWindowOperator` 同构（:126、:144-146）；`CheckpointSerDe.deserializeOperatorState`（:900-921）仅有 TimerSnapshot/PaneTrackingSnapshot/accumulator 三个再水合分支，PerKeyOrderedBuffer 原样透传为 Map，且 `deserializeTaskStateSnapshot` 确将 operator states 逐个经该函数（:798-802）——durable 恢复链上 `op.restoreState` 拿到的必为 Map，守卫恒 false、静默跳过；`rebuildViewFromKeyedState` 全仓 grep 仅定义（OverWindowOperator:257）与类 javadoc 引用（:47），**零调用**属实；且该兜底即便被调用也无效——`MemoryMapState.entries()`（:137-140）经 `getMap()` 收敛到 `currentKey()`（:73）单键 scoping，fresh 实例遍历不到全量条目，「durable copy 兜底」双重失效属实；`TestEquiJoinWithCheckpoint:91` 实读确认 `restoreState` 的入参是 `op1.getLastSnapshotResult()` 的**内存活对象**，从未穿过存储序列化，测试盲区定性成立；OverWindowOperator:161-162「the view is rebuilt from these entries on restore」失实注释在案。**精度澄清（不降级）**：盲区自评中悬置的 JsonTool 行为本复核已闭环，结论比原文的两种假设都更利于 KR-7 口径——默认配置 `nop.core.json.serialize-only-data-bean=true`（CoreConfigs.java:172-173，nop-stream 无任何覆盖）下，非 @DataBean 的 PerKeyOrderedBuffer（实读：普通 Serializable POJO、无 getter 属性）在 `computeCanonicalChecksumHex`/`serialize`（CheckpointSerDe:384/:119）的 `JsonTool.serialize` 处**直接抛** `ERR_JSON_ONLY_DATA_BEAN_IS_SERIALIZABLE`（JsonSerializer:157-158 + JsonWhitelistChecker:24-29）——即**默认配置下的真实失效面是「durable checkpoint 每写必败」（响亮，KR-7 的主面孔），而非恢复后静默丢数据**；AR-13 的静默丢失面孔仅在用户关闭 only-data-bean 后出现，且彼时无 getter 的 bean 序列化为 `{}`（空 map，而非原文所述「LinkedHashMap」——类型退化结论不变，instanceof 恒 false）。两种形态任一成立均为 P1（默认配置 = join/OVER × durable checkpoint 完全不可用；降级配置 = 恢复后工作视图静默清空），P1 维持。

**AR-14 — 保留，P2。** `stableHash` 实读核实：除 Enum 特判（:66-70）外，所有键类型统一走 `JsonTool.serialize + murmur3_32`（:78-80，plan 2306 Phase 4 注释在案）；回退分支每次调用打一条带完整堆栈的 WARN（:88-90）。路由热路径无记忆化核实：`KeySelectorPartitioner.partition`（DataStreamImpl:470-483）每记录直通 `assignToSubtask → assignToKeyGroup → stableHash`，分区器仅持有 keySelector/maxParallelism 两个字段、无任何缓存。对照组缓存属实：MemoryKeyedStateBackend.cachedNamespaceAndKey（:541-552，含单槽复用论证）、RocksDBKeyedStateBackend cachedStorageKeyFor（:512-539，注释「removes the dominant per-access CPU/alloc」逐字在案）——状态侧有缓存、路由侧无对等物的不对称成立。**复核增益（不改级）**：G38 契约头（KeyGroupAssignment:26-28）仍宣称「String/primitive wrappers 等 hashCode() 直接复用」，与 plan 2306 后的实际代码不符（全部 JSON 化）——该陈旧声明意味着连 String 键也在路由热路径支付 JSON 序列化，较原文「String 键尚可」更强地支持本条；同时构成 KR-4 之外的契约头-实现漂移点。性能影响为估算档与原文自评一致；parity 正确性不受影响（两侧同函数），P2 恰当。

**AR-15 — 保留，P2。** 实读核实：`setCurrentKey` 挂 `maybeSweepExpiredEntries()`（:171-175，每记录热路径）；门控（:188-199）默认 `DEFAULT_TTL_SWEEP_INTERVAL_MILLIS = 60_000L`（:84/:127）；`cleanupExpiredEntries`（:212-226）对每个 TTL-enabled state 的整个 `ttlStorage()` 做同步迭代删除，无预算/分批/耗时打点。TTL-free 作业仅付一布尔一 long 比较（`anyTtlBound` :140）——「默认场景零成本、TTL 作业全量扫」的双面定性准确。数据面 stop-the-world × watermark/barrier 对齐的组合时序推演成立（sweep 在任务线程 mailbox 内联）。量级为推测档与原文自评一致，P2 恰当。

**AR-16 — 保留，P3。** fast-path 复核：`findEdge` 首条命中即返回、无去重告警（StreamModelDslBuilder ~1014-1024），builder 侧无重复边校验。旧 AR-08 原样存在，维持 P3。

**AR-17 — 保留，P3。** fast-path 复核：`applyCheckpointConfig` 的 `if (cfg.isEnabled() && cfg.getInterval() > 0)`（~:331）原样——enabled=true + interval=0 被静默吞掉，同文件其余字段的显式 0 语义修正反衬该守卫。旧 AR-09 原样存在，维持 P3。

**AR-18 — 保留，P3。** fast-path 复核：`HashPartitionRouter.selectChannel` 的 null-partitioner 回退 `Math.floorMod(record.getValue().hashCode(), numPartitions)`（~:33-40）原样——整记录（非键）identity/值哈希。旧 AR-11 原样存在（影响面收窄的描述仍准确），维持 P3。

### 跨报告重合判定（供汇总合并）

1. **AR-12 ↔ 05 报告 KR-1：同一缺陷。** 根因同一（remote-deploy 的 TM 侧供给点拿不到作业 CheckpointConfig，恒注入默认 MemoryStateBackend(128)）、失效层同一（keyBy 路由 M=客户端配置 vs keyed 属主 M=TM 注入默认的解耦，remote 部署形态下 AR-01 parity 断裂）、证据同一（同文件同段 RemoteTaskDeploySupport.java:158-181、同一段 javadoc 引文）、严重程度同一（双 P1）。AR-12 的独立增量仅两点：AR-01-parity 复活的知识框架 + 「登记文本未覆盖 parity 后果」的观察（KR-1 误报排除段已含同义论证）；其次级缝（keyBy 后 setStateBackend 的顺序脆弱）与 KR-3 缺口(1) 重合。**合并口径：以 KR-1 为主条目，AR-12 并入不单独计数；AR-12 建议中的启动期 fail-fast 与 descriptor 携带 maxParallelism 两个修复方向与 KR-1 建议一致，可直接归并；顺序脆弱子情形归 KR-3。**
2. **AR-13 ↔ 02 报告 ST-12：同一缺陷。** 同一根因（operator-state 恢复传输只认活 PerKeyOrderedBuffer 实例、无 @type 往返形式；keyed-mirror 重建兜底零接线）、同一失效层（恢复时工作视图整体为空、keyed 镜像 write-only）、同一文件集、同一零调用 grep 结论（rebuildViewFromKeyedState）、同一测试盲区（E2E 用内存活对象冒充恢复产物）、同一严重程度（双 P1）。**合并口径：AR-13 与 ST-12 二选一保留为主条目（建议随 KR-7 编号族归并，见下），另一条以交叉引用方式挂靠，不重复计数。**
3. **AR-13/ST-12 ↔ 05 报告 KR-7：同族相邻（同一根因、不同主失效层）。** 三者同源于「PerKeyOrderedBuffer 无 JSON 往返形式」这一未成文契约缺口，但主失效面不同：KR-7 的主面孔在**写侧**——默认配置（only-data-bean=true）下 durable checkpoint 序列化必抛 `ERR_JSON_ONLY_DATA_BEAN_IS_SERIALIZABLE`，join×durable 恢复组合完全不可用（fail-loud）；AR-13/ST-12 的面孔在**恢复侧**——序列化成功（关 only-data-bean）后 restore 的 instanceof 守卫静默跳过、工作视图清空（silent）。本复核已实证：默认配置下 AR-13 所述「从 durable checkpoint 恢复后静默丢失」**不可达**（checkpoint 根本写不出去），该面孔须降级配置才显形；AR-13 盲区自评预判的正是这一点，按裁决口径以 live code 为准采纳 KR-7 的两面孔框架。**合并口径：三条合并为单一缺陷族、单一修复工单（@type 自描述 form + CheckpointSerDe 再水合分支，或接通 keyed-mirror 重建并让 operator-state 传输退化为内存优化）；汇总登记建议以 KR-7 为主条目（默认配置必抛，覆盖面与影响最宽），AR-13/ST-12 作为恢复侧面孔显式交叉引用，修复验证须同时覆盖写侧（durable checkpoint 能写出）与恢复侧（经 CheckpointSerDe 真实往返后工作视图非空）两个断言。**
4. **AR-14/AR-15 无跨报告重合**：05 报告 KR-4 覆盖的是 stableHash 回退分支的契约头缺口（P3），与 AR-14 的路由热路径性能面相邻但根因与裁决层不同（KR-4 汇总时可与 AR-14 互相引用 G38 契约头陈旧问题，不合并）；AR-15 与 02 报告对 TTL 面的「已验证健康」负面结论（惰性逐出 + 60s 门控清扫验证一致）不冲突——02 未评估 sweep 的规模停顿语义，AR-15 是该面上的独立增量。

### 最终保留项

| 编号 | 严重程度 | 裁决 | 一句话理由 |
|---|---|---|---|
| AR-12 | P1 | 保留（并入 KR-1） | 五点静态链路（烤入点/descriptor 字段/TM 注入/ownership 公式/登记文本）逐点复核成立，与 KR-1 同根因同失效层，属同一缺陷；次级缝归 KR-3 |
| AR-13 | P1 | 保留（并入 KR-7 族） | 恢复链 instanceof 静默跳过 + rebuildViewFromKeyedState 零调用 + 双重失效 + 测试盲区全部成立；默认配置下真实失效面为写侧必抛（KR-7 主面孔），静默丢失面孔限于降级配置；与 ST-12 同一、与 KR-7 同族相邻，三合一登记 |
| AR-14 | P2 | 保留 | 路由热路径每记录 JSON+murmur3 无缓存、状态侧同款缓存在位的不对称属实；G38 契约头陈旧声明反而加重该条（String 键亦付 JSON 成本） |
| AR-15 | P2 | 保留 | setCurrentKey 挂钩 + 60s 门控 + 全量同步 sweep + 无预算/打点逐行核实，TTL 作业数据面停顿语义成立 |
| AR-16 | P3 | 保留 | findEdge 首条命中无去重校验原样在案（旧 AR-08 未修复） |
| AR-17 | P3 | 保留 | enabled=true + interval=0 静默吞掉原样在案（旧 AR-09 未修复） |
| AR-18 | P3 | 保留 | null-partitioner 整记录哈希回退原样在案（旧 AR-11 未修复） |
