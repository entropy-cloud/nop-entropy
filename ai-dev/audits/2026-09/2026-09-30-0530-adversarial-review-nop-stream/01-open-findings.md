# nop-stream 对抗性审查（开放式、发现导向）— 2026-09-30

> Scope: nop-stream 全部子模块（core / runtime / cep / flow / rocksdb / connector*），HEAD = 0e67dba845（plan 366 收口后）
> 基线事实：plan 366（N1/N2/N3/N4/N5/A2'/B6'/S1/S2 + 可读性治理 + 性能留舍）已落地；A3-A6/B2/B7/G1-G3/D4/A11/N6 维持 Deferred；follow-up（回放窗口先存缝隙、file source 行累积、withLateDataOutputTag 孤儿、processWatermark1/2 测试驱动）已登记
> 方法：先通读 R2/R3/93 项清单完成去重，然后以四个视角切入——(1) 最新修复代码的 fix-of-fix 审计（plan 366 三提交逐 diff），(2) xdef 声明面 vs builder 消费面的系统对照，(3) 审计覆盖最薄的模块（reshard 迁移、connector-batch/debezium、XDSL 高级变换），(4) 组合爆炸测试者（两个独立正确机制的组合处：路由哈希 × 属主哈希、pendingReplay × unaligned capture）
> 去重声明：本轮无一条与 R2/R3/93 项清单中的既有编号重复；与历史问题同族的（Debezium 恒真条件、withLateDataOutputTag）只报告**现状变化**。

---

## 发现清单

### [AR-01] keyBy 路由哈希与 keyed-state 属主哈希是两套独立公式——rescale/reshard 后 keyed state 被"正确地"放到永远不会收到对应记录的子任务上

**文件:行号**
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/datastream/DataStreamImpl.java:403`（KeySelectorPartitioner）
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryKeyedStateBackend.java:466`（routeKey）
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/shard/KeyGroupAssignment.java:41-49,175-182`（G38 契约与公式）
- `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/RescaleStateAssembler.java:160,183`（rescale 按 ownership 过滤）
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/shard/KeyGroupReshard.java:121`（reshard 同一公式）
- `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/TaskCheckpointWiring.java:174`（生产默认后端）
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/shard/KeyGroup.java:41`（默认 128）

**证据片段**

路由侧（记录去哪个子任务）——`DataStreamImpl.java:398-403`：
```java
public int partition(T value, int numPartitions) {
    try {
        Object key = keySelector.getKey(value);
        if (key == null) {
            return 0;
        }
        return (key.hashCode() & Integer.MAX_VALUE) % numPartitions;
```

状态属主侧（key 的状态属于哪个子任务）——`MemoryKeyedStateBackend.java:466` + `KeyGroupAssignment`：
```java
int keyGroupId = KeyGroupAssignment.assignToKeyGroup(key, maxParallelism);  // (stableHash & 0x7FFFFFFF) % maxParallelism
```
`stableHash` 对 JDK 值类型等于 `hashCode()`，对其余一切类型是 **murmur3(canonical JSON)**（G38 契约，`KeyGroupAssignment.java:175-182`）。属主子任务 = `assignKeyGroupToSubtask(keyGroupId, maxParallelism, parallelism)`，即连续区间切分。

rescale 恢复按**属主公式**重新归置状态——`RescaleStateAssembler.java:159-165`：
```java
for (Map.Entry<String, List<Map<String, Object>>> entry : mergedKeyedByName.entrySet()) {
    Map<String, Object> mergedStates = mergeAndFilterKeyedStates(entry.getValue(), newRange, maxParallelism);
```
其中 `mergeAndFilterKeyedStates` → `KeyGroupRangeRestoreFilter.filterKeyedStates(src, range, maxParallelism)` → `assignToKeyGroup(rawKey, maxParallelism) ∈ range`。

生产默认后端——`TaskCheckpointWiring.java:172-175`：
```java
IStateBackend stateBackend = configuredBackend != null
        ? configuredBackend
        : new MemoryStateBackend();   // 默认 maxParallelism = DEFAULT_MAX_PARALLELISM = 128
```

**严重程度**：P0（错误行为：受支持的 rescale/reshard 操作之后 keyed 作业静默产出错误结果 + 幻影状态；非崩溃、非数据丢失，故不阻断作业）

**现状**：新发现。三轮深审（93 项清单、R2、R3）均未覆盖此交叉面；Stage 34/35/37 特性线的全部 E2E 测试（`TestKeyGroupRescaleDispatchE2E.stageSavepoint`、`TestMaxParallelismReshardMigrationE2E`、`TestChannelStateRescaleE2E`）都用 **ownership 公式手动构造 savepoint**（`int sub = KeyGroupAssignment.assignKeyGroupToSubtask(gid, MAX_P, pOld)`），而不是用真实作业运行产出的（按路由公式放置的）快照——测试与生产各用一半公式，互相永不相遇，全家族绿灯无法暴露此缺陷。

**风险推导**（为什么这是错的）：
1. 运行期：记录 K 被路由到子任务 `h % p`；K 的状态存在该子任务（后端不拒绝 group 越界的 key，`routeKey` 只是本地打标）。单次运行内自洽。
2. **同 parallelism 重启/region restart：安全**——1:1 own-snapshot restore，`setTargetKeyGroupRange` 在 main 源码零调用（仅测试调用），恢复不过滤，状态回到原子任务，路由继续送 K 到同一子任务。爆炸半径不含日常恢复。
3. **rescale（pOld ≠ pNew）：错位**——`buildRescaledTaskState` 先合并所有旧子任务再按 **ownership**（`(h%128) → range(s')`）过滤归置；而运行期路由按 `h % pNew` 送记录。仅当 `maxParallelism == parallelism` 时两公式才对全部 key 重合；默认 128 vs 任意 p>1 时，绝大多数 key 的恢复状态落在 `owner(h%128)`，而记录会持续送到 `h%pNew`——接收子任务把 K 当全新 key 从零重算（窗口聚合回零、CEP 历史模式静默清空、keyed process 状态丢失），owner 子任务持有永不清理的幻影状态（TTL 也只清理被访问的 key）。
4. **MaxParallelismReshardMigration 离线迁移：同一错位**——`KeyGroupReshard.redistributeStates:121` 按 `assignKeyGroupToSubtask(groupId, newMaxParallelism, newParallelism)` 落盘，运行期仍按 `h%pNew` 路由，迁移产物与路由面天然错开。
5. **POJO key 的额外漂移**：`key.hashCode()` 对未覆写 hashCode 的 POJO 是身份哈希——**同 parallelism 重启后**（如 kill/recover 用新 JVM 恢复），同一 key 的路由也会改变，状态孤儿化。G38 契约注释声称 stableHash "preserves routing parity with the legacy formula"——但路由侧从未切换到 stableHash，parity 只对 JDK 值类型成立，注释描述的是一种不存在的对齐。

**附带损害（未来破坏者视角）**：`RescaleStateAssembler.materializeKeyGroupOwnership:227-247` 把按 ownership 公式计算的 per-subtask KeyGroupRange **持久化进 checkpoint**，而真实数据放置遵循路由公式——任何未来消费该元数据的特性（按 group 的增量 checkpoint、group 语义的 exactly-once handoff、group 级监控）都继承一个系统性谎言。

**建议**：
1. 短期：`KeySelectorPartitioner.partition` 改为经 `KeyGroupAssignment.assignToKeyGroup(key, backendMaxParallelism)` + `assignKeyGroupToSubtask(group, maxParallelism, numPartitions)` 路由（Flink 的 KeyGroupRangeAssignment 即此形态），使路由面与属主面共用一套公式。注意需把 job 级 maxParallelism 传递到 writer 构造处。
2. 短期：G38 契约注释改为如实描述（"state 侧稳定、routing 侧未对齐"），或在 `stableHash` 与 `key.hashCode()` 分歧的类型上打 WARN。
3. 测试补口：新增一个**真实运行**的 E2E——keyBy 作业 parallelism=2、maxParallelism=128、真实 checkpoint → rescale 到 3 → 断言每个 key 的状态跟随其记录（而不是按 ownership 公式手工构造快照）。这一个测试会同时钉死 AR-01 的全部三个表现。
4. `HashPartitionRouter:35` 的 `record.getValue().hashCode()` 回退（按**整条记录值**而非 key 路由）在 HASH+null partitioner 时可达即错——改为 fail-fast 或复用 stableHash（见 AR-11）。

**信心水平**：很可能（全链路代码推导闭环：路由公式、属主公式、生产默认 128、rescale 过滤路径、测试构造方式五点均已逐行核实；未运行时复现——建议按建议 3 先落一个真实路由 E2E 再定修复方案）

**发现来源视角**：组合爆炸测试者（两个各自"正确"的机制——数据面 hash 路由 × 状态面 key-group 属主——的组合缝）

---

### [AR-02] `<custom><source>` xpl body 被 xdef 声明、被模型解析、被 builder 静默忽略——违反同文件 Anti-Hollow 自我声明

**文件:行号**
- `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/stream.xdef:188-193`
- `nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/AdvancedTransforms.java:375-408`
- `nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/model/_gen/_StreamCustomModel.java:35-38,113-125`
- `nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:108-119`

**证据片段**

xdef 声明（`stream.xdef:188-193`）：
```xml
<custom customType="!xml-name" xdef:name="StreamCustomModel" xdef:ref="StreamTransformModel">
    <source>xpl-fn:(event:any)=>any</source>
    <params xdef:key-attr="name" xdef:body-type="list">
        <param xdef:ref="StreamParamModel"/>
    </params>
</custom>
```

builder 消费（`AdvancedTransforms.buildCustom`）——只读 `customType`，对 `<params>` fail-fast，**从不读取 `m.getSource()`**：
```java
if (m.getCustomType() == null) { throw ... ERR_STREAM_REQUIRED_ATTR ... }
if (m.hasParams()) { throw ... ERR_STREAM_NOT_IMPLEMENTED ... "<params> on <custom> has no execution consumer" ... }
...
OneInputStreamOperator<T, R> operator = owner.resolveBean(t, m.getCustomType(), OneInputStreamOperator.class);
```

同文件 javadoc（`StreamModelDslBuilder.java:109-111`）：
```java
 * <li>Every {@code xdef}-declared transform element is either implemented here or
 *       fails fast with a {@link StreamException} carrying an {@code ERR_STREAM_*} code.</li>
```

**严重程度**：P2

**现状**：新发现。全仓 grep 确认 `StreamCustomModel.getSource()` 在 main 源码零消费（仅 `_gen` 的序列化自省引用）。同类的 `<source>`/`<sink>`/`<map>`/`<filter>`/`<flatMap>`/`<reduce>` 的 xpl body 全部有消费或 fail-fast；`<custom>` 是唯一一个 body 被解析进模型后无声蒸发的地方。

**风险**：用户在 `<custom>` 里写了转换逻辑（xdef 合法、解析通过、IDE 有补全），构建成功、作业运行——但逻辑从未执行，自定义算子 bean 收到的输入与用户书写的心智模型完全无关。这是"静默接受不支持的配置"类别中最隐蔽的形态：同一节点的 `<params>` 会响亮拒绝，`<source>` 却沉默，用户从报错模式无法推断该节点哪些子元素真正生效。

**建议**：`buildCustom` 对 `m.getSource() != null` 走与 `<params>` 相同的 `ERR_STREAM_NOT_IMPLEMENTED` fail-fast（一行），或在 xdef 中删除 `<custom><source>` 声明（二选一，与维护者语义确认）。同时建议把"xdef 声明面 × builder 消费面"的对照固化为一个 lint 工具（`StreamModel` 每个字段 → grep 消费者），防第三处同型缺口。

**信心水平**：确定（零消费已 grep 证实）

**发现来源视角**：文档描述一种理想但代码实现另一种（Anti-Hollow 自我声明 vs 实际残留缺口）

---

### [AR-03] MaxParallelismReshardMigration：缩容时被裁撤子任务的 operator state 静默消失，且"守恒校验"结构上不可能失败

**文件:行号**
- `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/reshard/MaxParallelismReshardMigration.java:303-318`（scale-down 丢 operator state）
- `MaxParallelismReshardMigration.java:128-135`（空转守恒校验）
- `MaxParallelismReshardMigration.java:57-59`（类 javadoc 的 no-silent-drop 声明）

**证据片段**

```java
// Operator (non-keyed) state: copy 1:1 by index where an old
// subtask exists; scale-up subtasks start empty, operator-state
// rescale redistribution is out of scope and orthogonal to reshard.
if (s < oldParallelism) {
    ... copy operator states ...
}
```
Phase 4 循环只遍历 `s ∈ [0, newParallelism)`——`newParallelismOverride < oldParallelism`（缩容）时，`s ≥ newParallelism` 的旧子任务的 operator state（对 file/batch source 即 **split 分配表**）没有拷贝、没有警告、没有拒绝。

守恒校验（:128-135）：
```java
// Sanity check: per-state conservation is structural (we only move
// entries), but assert explicitly to fail-fast on any logic bug.
for (Map.Entry<String, Integer> stateCount : result.getKeyCountByState().entrySet()) {
    if (stateCount.getValue() == null || stateCount.getValue() < 0) {
        throw new StreamException(ERR_STREAM_STATE_ERROR)...
```
`recordKeyCounts` 只记录一次计数，随后仅断言"非负"——注释宣称的 conservation invariant（迁移前后计数一致）从未被对比，该检查除负数外不可能失败。

**严重程度**：P2

**现状**：新发现。R2 曾记录该类"126 行 7 参方法"的结构问题（plan 366 已拆四阶段），但缩容数据面与校验空转未见记录。类 javadoc 明确承诺 "any structural anomaly ... fails fast — no silent drop"，与缩容路径行为相悖（同 AR-02 的自我违约模式，该模式在本模块已复现两次）。

**风险**：对有算子状态的顶点（典型：bounded file source 的 split 枚举状态），离线缩容迁移产出**看似成功**的 savepoint（报告 exit 0、reshard-report.json 就位），恢复后作业静默丢失部分输入 split → 数据缺失且无从追溯。keyed state 不受影响（走 pool 重分配），故只咬 operator-state 顶点——恰好是最不设防的一类。

**建议**：(1) 缩容（`newParallelism < oldParallelism`）且顶点带非空 operator state 时 fail-fast 或按 key-group 同样方式重分配（与 2PC sink 的 parallelism-change 拒绝语义对齐）；(2) 把守恒校验补成真实的 before==after 对比（`recordKeyCounts` 的 baseline 已在手，Phase 3 后再计一次即可）；(3) 缩容 + operator state 场景至少 `result.addWarning(...)`。

**信心水平**：确定

**发现来源视角**：异常路径侦探（迁移工具的输入维度穷举：scale-up 有注释、scale-down 无行为）

---

### [AR-04] Region 重启的 residual drain 连控制事件一起丢弃——事件时间窗口在新 gate 上停摆到 producer 下一个 watermark

**文件:行号**
- `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/SupervisionLoop.java:808-826`（drain 后只 attach 数据回放）
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/ResultPartition.java:514-536`（drainBufferedElements 丢弃非哨兵的一切，含 Watermark）
- `ResultPartition.java:294-308`（dualWriteToMaterialization 只写 `isRecord()`，watermark/barrier 从不入 store）

**证据片段**

```java
// Drain residual queue content before attaching replay: ...
List<StreamElement> drained = oldPartition.drainBufferedElements();
InputChannel tempChannel = new InputChannel(consumerPartition);
int injected = tempChannel.activateMaterializationReplay(consistentCutEpoch);
LOG.info("Reconnect-to-live-queue: drained {} stale element(s), attached {} post-checkpoint"
        + " replay element(s) ...", drained.size(), injected, ...);
```
`drained` 列表此后被丢弃。其中不仅有数据记录（在 store 中，回放覆盖），还有 **Watermark/WatermarkStatus**（物化 store 永不接收控制事件，`dualWriteToMaterialization` 的 `!element.isRecord()` 过滤）——这部分**无处回放**。

**严重程度**：P2

**现状**：新发现（N1/N2 修复 `5e33afea72` 引入的行为形态；修复前的 `injectFront` 路径同样只回放 store 内容，故属该特性线的既有盲区而非回归）。登记在案的"回放窗口先存缝隙"follow-up 指数据缝隙，不含控制事件丢失。

**风险**：重启后新 InputGate 的 watermark 阀从 MIN 重建；若 producer 是低吞吐/间歇源（CDC、批量推送），队列中残留的 watermark 被丢弃后，下游事件时间窗口在下一条记录到达（并携带新 watermark）前不触发——告警延迟可观测但自愈。对 bounded 作业更糟：若 producer 在 drain 前已发出**最终 watermark** 且其分区未 finished（理论上 finished 分区才安全，见下），窗口可能永不触发。实际可达性受限于"drain 时 producer 仍 RUNNING 但 watermark 已是最终值"的窄窗口（waitForTerminal 保证 producer 已终态，而终态前 source 会发 MAX_WATERMARK 并 close——watermark 在 close 前 put 进队列的情形真实存在），故为低频但真实的告警延迟/不触发窗口。

**建议**：drain 结果按元素类型分流——数据记录弃（store 有），Watermark/WatermarkStatus 重注入（`attachPendingReplay` 或 gate 级 pending 队列；与数据回放的相对顺序：watermark 应排在数据回放**之后**还是之前需按 consistent-cut 语义裁定，建议 owner 确认后随"回放窗口先存缝隙"follow-up 一并处理）。

**信心水平**：确定（机制）/ 很可能（生产触发频率）

**发现来源视角**：异常路径侦探（被丢弃的 `drained` 列表的成分分析）

---

### [AR-05] `attachPendingReplay` 是"替换"而非"追加"语义——二次 attach 会孤儿化未读完的前一批回放元素

**文件:行号**
- `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/ResultPartition.java:91-96`
- `InputChannel.java:127-137`（injectElements，unaligned restore 路径）
- `InputChannel.java:217-240`（activateMaterializationReplay，region restart 路径）

**证据片段**

```java
public void attachPendingReplay(List<StreamElement> elements) {
    if (elements == null || elements.isEmpty()) {
        return;
    }
    this.pendingReplay = new java.util.concurrent.ConcurrentLinkedQueue<>(elements);
}
```
字段被整体替换。若上一个队列尚有未消费元素，它们被直接孤儿化（无日志、无计数）。

**严重程度**：P2（防御性——当前调用序列恰好安全，见现状）

**现状**：当前 main 源码的两个 attach 入口（region restart rebuild、unaligned channel-state restore）在时序上互斥：restore 前 `captureInFlightData → drainBufferedElements` 总是先清空 pendingReplay（`drainBufferedElements` 先 poll 干 replay 再 poll 队列，`ResultPartition.java:519-522`），所以"未读完就被替换"的窗口今天打不开。但这一安全性依赖一条**未写在任何契约里的隐式前提**：attach 的调用方必须保证"旧 replay 已耗尽或已 capture"。该前提既不在 javadoc（javadoc 反而描述了并发 attach 场景），也没有 assert。

**风险**：下一个合理需求（如"回放中途追加补发段"、双通道恢复路径复用同一分区）会自然地连续 attach 两次，静默丢数据。这是典型的"机制正确 + 契约未声明"型未来破坏者。

**建议**：三选一并落注释/断言：(a) attach 改为追加到既有队列尾（语义上"后续回放段"合理）；(b) 检测 `pendingReplay != null && !isEmpty()` 时 fail-fast；(c) 至少 javadoc 显式声明单次活跃契约 + debug 日志。

**信心水平**：确定（机制）/ 有趣的猜测（未来触发场景）

**发现来源视角**：未来破坏者（下一个合理需求会迫使什么 hack）

---

### [AR-06] XDSL watermark 生成器适配器的 `onPeriodicEmit` 恒为空——`watermarkInterval>0` + xpl 生成器组合下周期发射静默失效

**文件:行号**
- `nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/AdvancedTransforms.java:600-617`
- `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/stream.xdef:124-129`

**证据片段**

```java
private static final class XplWatermarkGenerator<T> implements WatermarkGenerator<T> {
    ...
    @Override
    public void onEvent(T event, long eventTimestamp, WatermarkOutput output) {
        body.call3(null, event, eventTimestamp, output, ...);
    }

    @Override
    public void onPeriodicEmit(WatermarkOutput output) {
    }
}
```
xdef 只声明 `(event, eventTimestamp, output) => void` 形状的 body——xpl 用户**无法表达**周期发射逻辑；`F-04a` 修复刚把 node-level `watermarkInterval` 接线进算子（注释强调"declared value ... reaches the operator"），但该间隔只对 bean 策略的 `onPeriodicEmit` 生效，xpl 生成器路径上间隔声明无效且无提示。

**严重程度**：P3

**现状**：新发现。与 F-04a（plan 1326-2，已修的"声明 interval 被静默忽略"）同族——同一属性在另一生成器类型上仍是静默失效。

**风险**：从 Flink/其他引擎迁移的用户按 `WatermarkGenerator` 心智模型写 xpl 生成器并声明 `watermarkInterval="200"`，得到的是纯 per-event 发射：无界乱序源上水位推进语义静默改变（偏保守，多数场景表现为窗口延迟而非错误），配置与行为不一致无任何报告。`StreamConfValidator` 也不覆盖该组合。

**建议**：xdef `<watermarkGenerator>` 的签名注释明确"per-event only；周期发射请用 bean 策略"；或在 `buildTimestampsAndWatermarks` 检测"xpl 生成器 + 非 200 显式 interval"时输出 WARN。

**信心水平**：确定

**发现来源视角**：新人开发者困惑点（Flink 心智模型迁移）

---

### [AR-07] DebeziumCdcSourceFunction `if (!draining)` 恒真死条件——2026-09-01 记录后仍未清理（现状核查）

**文件:行号**：`nop-stream/nop-stream-connector-debezium/src/main/java/io/nop/stream/connector/debezium/DebeziumCdcSourceFunction.java:185-197`

**证据片段**：
```java
this.running = true;
this.draining = false;          // :185 刚置 false
initCompletionLatch();

try {
    if (!draining) {            // :189 恒真
        source = createMessageSource(...);
```

**严重程度**：P3
**现状**：2026-09-01 connectors 审计 H 组记录的"恒真死条件"原样保留（行号从 189 → 189），无新代码依赖、无恶化。真正的 drain 路径（`truncateForDrain`）在 `run` 内部不可达，`draining=true` 时的 run 行为（跳过 subscribe、直接进 latch 等待）是**从未被走到的分支**。
**风险**：读者误以为 run 支持 mid-flight drain 语义；死分支掩盖了"drain 后重入 run 会重建 source"这一实际行为。
**建议**：随下次 connector 触碰顺手删除条件或改为显式断言。
**信心水平**：确定
**发现来源视角**：死代码清道夫（现状复核）

---

### [AR-08] 重复的 from→to 边对不校验，`findEdge` 取首条——第二条边声明的 partition 顺序依赖地失效

**文件:行号**
- `nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:321-343`（validateDag 只查 edge id 重复）
- `StreamModelDslBuilder.java:719-726`（findEdge 线性取首条匹配）

**证据片段**：
```java
Set<String> edgeIds = new HashSet<>();
for (StreamEdgeModel e : edges) {
    if (!edgeIds.add(e.getId())) { throw ... }      // 只查 id 重复
    ...
    upstreams.get(e.getTo()).add(e.getFrom());      // Set —— 重复 from->to 被合并
}
...
private StreamEdgeModel findEdge(String fromId, String toId) {
    for (StreamEdgeModel e : model.getEdges()) {
        if (fromId.equals(e.getFrom()) && toId.equals(e.getTo())) {
            return e;                               // 首条胜出
```
两条 `A→B` 边（如 `e1 partition="FORWARD"`、`e2 partition="HASH" keyExpr="..."`——后者能通过 `validateEdgeDeclarations`）都会通过构建；`applyEdgePartition` 经 `findEdge` 只看到第一条 → 若首条是 FORWARD，第二条声明的 HASH **静默忽略**；调换声明顺序则行为翻转。

**严重程度**：P3
**现状**：新发现。同文件对重复 id、HASH 冗余、keyExpr-without-HASH 都有逐属性 fail-fast，唯独 from→to 对重复无守卫——与该 builder 的 fail-fast 密度不成比例。
**风险**：低（需要用户写出畸形声明）；但一旦写出，错误是顺序依赖的、两次构建结果不同，极难归因。
**建议**：`validateDag` 中对 `(from,to)` 对做 Set 去重，重复即 `ERR_STREAM_DUPLICATE_ID`（或专用错误码）fail-fast。
**信心水平**：确定
**发现来源视角**：异常路径侦探（builder 的输入维度穷举）

---

### [AR-09] `<checkpoint enabled="true" interval="0">` 静默禁用 checkpoint——与同文件 F-04b 确立的"显式 0 必须生效"哲学相悖

**文件:行号**：`nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:179-181`

**证据片段**：
```java
if (cfg.isEnabled() && cfg.getInterval() > 0) {
    env.enableCheckpointing(cfg.getInterval());
}
```
xdef `interval` 默认 60000；显式声明 `interval="0"` 且 `enabled="true"` 时两个条件不同时成立，checkpointingDeclared 不置位 → 作业按非 checkpoint 路径运行，无任何报告。对照同文件 `:157-163` 的 F-04b 注释——团队刚为 `watermarkInterval="0"` 修过一模一样的问题（"the `> 0` guard silently dropped a root-level declaration"）。

**严重程度**：P3
**现状**：新发现（F-04b 只修了 watermark 侧）。
**风险**：低频；一旦命中，用户以为开着 checkpoint 的作业实际上裸奔，故障后无恢复点——静默语义反转。
**建议**：`enabled=true && interval<=0` 时 fail-fast（`ERR_STREAM_INVALID_ARG` + 属性名），与同 builder 对其他非法声明的处理一致。
**信心水平**：确定
**发现来源视角**：未来破坏者/一致性（同一文件内 0-语义的双标）

---

### [AR-10] BatchLoaderSourceFunction.currentOffset 跨线程读写无同步——checkpoint 线程可能读到撕裂/过期偏移

**文件:行号**：`nop-stream/nop-stream-connector-batch/src/main/java/io/nop/stream/connector/batch/BatchLoaderSourceFunction.java:60,121-122,163-165`

**证据片段**：
```java
private long currentOffset = 0;          // 非 volatile，任务线程写
...
ctx.collect(item);
currentOffset++;                          // 任务线程
...
public long getCurrentOffset() {          // checkpoint 线程读（snapshotState 路径）
    return currentOffset;
}
```

**严重程度**：P3
**现状**：新发现。`run`/`seek` 在任务线程，`getCurrentOffset` 由 checkpoint ACK 线程调用（ReplayableSourceFunction 契约）。该 source 自报 `AT_LEAST_ONCE`，偏移略旧只导致重放 多几条——在自报语义内。但 long 的非同步跨线程读在规范上允许读到中间字面量（64 位 JVM 实践上原子，规范上不保证）。
**风险**：实际影响≈0（偏移保守即可）；正确性卫生问题 + 违反项目自身的并发审查基线（R3 曾为同类可见性问题立 P1）。
**建议**：`volatile` 或改 `AtomicLong`，一行修复。
**信心水平**：确定
**发现来源视角**：异常路径侦探（谁在哪个线程读这个字段）

---

### [AR-11] HashPartitionRouter 的 null-partitioner 回退按**整条记录值**哈希路由——同 key 不同字段的记录会分流到不同子任务

**文件:行号**：`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/HashPartitionRouter.java:30-36`

**证据片段**：
```java
public int selectChannel(StreamRecord<?> record) {
    if (partitioner != null) { ... key-based ... }
    return Math.floorMod(record.getValue().hashCode(), numPartitions);
}
```

**严重程度**：P3（当前主路径不可达：keyBy 经 `KeySelectorPartitioner` 恒非 null；XDSL HASH 边也物化为 keyBy。但"policy=HASH 而 partitioner 缺失"一旦因未来重构出现——例如某新入口只设 partitionType 不设 partitioner——该回退会让 keyed 语义静默崩坏且极难归因）
**现状**：新发现；与 AR-01 同根（路由面哈希纪律缺失的第三处表现：KeySelectorPartitioner 用 JVM hashCode、stableHash 注释声称 parity、此处用 record value hashCode——三处三个公式）。
**建议**：回退分支改为 fail-fast（"HASH policy requires a key partitioner"），或在 selectChannel 中对 policy==HASH && partitioner==null 的组合在构造期拒绝。
**信心水平**：确定（机制）/ 有趣的猜测（可达性）
**发现来源视角**：未来破坏者

---

## 与既有裁定的交叉核查（现状变化，无新编号）

| 既有项 | 本轮核查结果 |
|---|---|
| plan 366 Phase 2 九项修复 | 逐 diff 复核未发现 fix-of-fix 缺陷：N1/N2 的 writer 复用与失败路径"不关 writer"契约（`StreamTaskInvokable.java:699-706`）自洽；`waitForTerminal` 预算耗尽 typed fail-fast（`ERR_STREAM_SUPERVISION_ZOMBIE_TASK_TIMEOUT`）关闭了僵尸线程与 shared-writer 并发写风险；A2' 的 `failedCommitParticipants` 守卫语义正确（`notifyParticipantsFinishCommit` 仅在 participant 回调抛异常时写入，:1211-1223）；N3 的 `isFinished` 覆盖 COMPLETED/FAILED/CANCELED，CANCELING（卡死取消）保持心跳属预期检测行为 |
| 回放窗口先存缝隙（follow-up） | 仍开放；本轮 AR-04（控制事件丢失）应与其合并处理——同一段 drain 代码的两个互补缺口 |
| withLateDataOutputTag 孤儿（follow-up） | 仍开放：`PatternStreamBuilder.java:107` 的 `withLateDataOutputTag` 在 main 源码仍零消费 |
| file source 行累积重构（follow-up） | Phase 4 只做了字节缓冲化（-33%，`FileSourceReader.readNextLine` 仍是整行 BAOS 累积 + `readBuf` 为 transient 且无 readObject 重建——当前无序列化路径，构造期初始化，若未来跨 JVM 传 reader 会 NPE，留意）；行级重构维持 Deferred |
| Debezium 恒真条件（09-01 H 组） | 见 AR-07，未恶化 |
| R2 C4（幂等 re-commit pendingCommits 残留） | plan 366 Phase 3 仅清理了注释（`JdbcTwoPhaseCommitSink.java:303-307`），修复本体（B4）早在 plan 01 落地，行为面无变化 |

---

## 总评

**最值得关注的 1-3 个方向：**

1. **AR-01（路由×属主双轨）是本轮唯一可能造成生产事故的发现，也是 16+ 轮审计的结构性盲区的范例**：数据面（RecordWriter/Router）与状态面（KeyGroupAssignment/后端）分属两个特性线演化，各自内部测试充分（Stage 34 的 routing E2E 是单子任务的；Stage 35/37 的 rescale E2E 用公式构造输入），没有一条测试让真实路由的产物流经真实恢复的过滤。修复本身不难（路由改走 key-group 公式），难在先落一条"真实运行 + 真实快照 + rescale + 断言状态跟随记录"的 E2E 把缺陷钉在纸上。
2. **"fail-fast 哲学的自我违约"正在成为模式**：AR-02（`<custom><source>`）、AR-03（reshard 缩容 + 空转守恒校验）与 AR-08/AR-09 共享同一根因——fail-fast 规则靠人肉枚举维护（每个属性一个 if），声明面（xdef/模型字段）与消费面的映射没有机械化保障。建议把"StreamModel 每个 getter 至少一个 main 消费者，否则必须出现在显式 fail-fast 清单"固化为 `ai-dev/tools/` 下的 lint（与既有 hollow-implementation scanner 同族），一次治理、长期防复发。
3. **plan 366 修复质量总体良好**：N1/N2/N3/N4/A2' 的修复逻辑经逐行复核无 fix-of-fix 回归，且多处（writer 保留契约、zombie fail-fast、A2' 守卫）与既有机制形成了正确闭环。这印证了 R3 的收敛判断——该特性线的低垂果实已摘完，剩余风险集中在**跨机制组合缝**（AR-01/04/05 全部是这一类），单文件深审的边际收益已低于跨层组合审查。

**盲区自评：**
- 未运行任何测试或最小复现（本轮全程静态审查）——AR-01 的 P0 定级基于五点代码事实的推导链而非运行时证据，置信"很可能"，建议按其建议 3 先落钉子测试。
- Distributed（RPC 数据面/控制面）仅在路由公式传导（JobEdge partitioner 传播）层面触及，`RemoteResultPartition`/`RemoteInputChannel` 的传输语义未重新深审（R2/R3 已覆盖其大部分，且 Phase 3 刚删除了其心跳面）。
- RocksDB 后端只核对了 restore 过滤路径（`RocksDBSnapshotSerDe:350-353`）与 AR-01 的关联，SST/WriteBatch/快照恢复内部未重审（360 系列已实测裁定）。
- fraud-example / quickstart 仅抽查了其与 xdef 消费面的一致性（均一致），未审业务逻辑。
- CEP NFA/SharedBuffer 内部本轮仅走了 XDSL 构建入口（`CepPatternBuilder`）与 within 机制，匹配引擎本体因 09-01 轮 + E3/F1/F2 已实测而未重审。

**严重程度分布**

| 严重程度 | 数量 | 编号 |
|---|---|---|
| P0 | 1 | AR-01（很可能，未运行验证） |
| P1 | 0 | — |
| P2 | 4 | AR-02、AR-03、AR-04、AR-05 |
| P3 | 6 | AR-06、AR-07、AR-08、AR-09、AR-10、AR-11 |
| 合计 | 11 | |
