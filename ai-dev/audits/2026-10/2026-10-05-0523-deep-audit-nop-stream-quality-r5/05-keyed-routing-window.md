# 05 深度审计：nop-stream 键控路由/属主哈希一致性 + 窗口语义（R5 报告 05 号）

- **审计日期**: 2026-10-05
- **HEAD SHA**: `bd089444f5c2b1fcd2c9a964ce909bac774a83d9`
- **范围**: `nop-stream/nop-stream-core`（KeyGroupAssignment/shard 族、状态后端 memory、RecordWriter/PartitionRouter、windowing assigners/triggers/evictors）、`nop-stream/nop-stream-runtime`（WindowOperator/MergingWindowSet、GraphModelCheckpointExecutor/RescaleStateAssembler、RemoteGraphExecutionPlanBuilder、RemoteTaskDeploySupport、CheckpointSerDe、MaxParallelismReshardMigration）、`nop-stream/nop-stream-rocksdb`（RocksDBKeyedStateBackend/RocksDBSnapshotSerDe）、`nop-stream/nop-stream-flow`（StreamModelDslBuilder/AdvancedTransforms 装配与 fail-fast）、`nop-stream/nop-stream-cep`（仅键控入口核对）、相关 E2E/单测的保护力评估、`ai-dev/design/nop-stream/window-failfast-decisions.md` 一致性核对。
- **方法**: 只读静态审计。以 live code 为准，逐一枚举 key→subtask 与状态→subtask 的全部决定点并核对其公式链是否汇聚于 `KeyGroupAssignment.assignToKeyGroup / assignKeyGroupToSubtask`；追踪 maxParallelism 的全部来源与钉定时机；核对 checkpoint 元数据的生产/消费闭环；窗口部分逐文件核对 assigner 边界、timer 生命周期、merge 正确性与设计裁定（D9-D12）的落地。`_` 前缀生成产物不在审计对象内（`nop-stream-flow/model/_gen/_StreamSinkModel.java` 等仅确认为生成物后跳过）。未运行任何 maven/测试，未修改任何产品代码。
- **结论概要**: R4 P0 的「路由 `(hash % parallelism)` vs 属主 `stableHash % maxParallelism`」公式分裂在 live code 的内建建图/状态归置链路上**已消除**——所有 12 个决定点全部汇聚于同一公式链（见附录清单）。但该缺陷族仍有 3 个**残留入口**（remote 部署后端供给分歧、checkpoint 元数据 write-only、build 期无 M/P 一致性校验），均可在特定配置下重建 R4 型「记录落在不持有其状态的子任务」行为；窗口侧发现 2 个 P1（session+evictor 时间戳错位、rescale 下 timer 与 keyed state 分裂）。

---

## 发现清单

### [KR-1] Remote 部署路径恒定供给 `new MemoryStateBackend()`，maxParallelism 与作业配置解耦，可重建 R4 型路由/属主分裂
- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/deploy/RemoteTaskDeploySupport.java:158-181`
- **证据片段**:
```java
 * Known divergence: the LOCAL wiring first honors
 * {@code checkpointConfig.getStateBackend()} and only falls back to
 * {@link MemoryStateBackend} when unset, while this remote path ALWAYS
 * provisions a {@link MemoryStateBackend} — the TaskManager-side deploy
 * path has no access to the job's CheckpointConfig. ...
    private static void provisionStateBackends(StreamTaskInvokable invokable) {
        ...
                if (abstractOp.getStateBackend() == null) {
                    IStateBackend backend = new MemoryStateBackend();
                    abstractOp.setStateBackend(backend);
```
- **严重程度**: P1
- **现状**: 本地路径 `TaskCheckpointWiring.wireTaskCheckpointPipeline`（TaskCheckpointWiring.java:166-178）从 `checkpointConfig.getStateBackend()` 供给后端，与 `DataStreamImpl.resolveKeyGroupMaxParallelism`（DataStreamImpl.java:434-443）同源，路由与属主一致。Remote 路径（TaskManager 侧 deploy）后端不可达，一律供给默认 `MemoryStateBackend()`（maxParallelism=128）；而 keyBy 路由分区器 `KeySelectorPartitioner` 是在作业侧建图时按当时配置后端的 M 序列化进 JobGraph 的（DataStreamImpl.java:302）。该分歧注释只承认了"后端类型被替换"（RocksDB 作业拿到 memory 后端），**没有承认 maxParallelism 耦合**：当作业显式配置 `new MemoryStateBackend(64)` 或 `new RocksDBStateBackend(path, 256)` 并经 remote 部署时，记录按 M=64/256 路由，keyed state 却按 128 归置与过滤（`resolveMaxParallelism` 读 live 后端=128，GraphModelCheckpointExecutor.java:1419-1438），rescale restore 的 `mergeAndFilterKeyedStates` 按 128 归属重分配——这正是 R4 P0 的「记录落在不持有其键组的子任务」模式。
- **风险**: remote 部署 + 非默认 maxParallelism 的作业，在并行度 rescale（甚至同并行度跨 maxParallelism 变更重启，见 KR-2）后出现静默的键控状态不可达/错账。窗口/CEP 等 keyed 聚合输出错误，无任何 fail-fast。
- **建议**: 把后端（至少其 `maxParallelism` 标量）通过 TaskDeploymentDescriptor/DeploymentPlan 带到 TM 侧（`DeploymentPlan.stateBackendBinding` 字段已存在但只是字符串，未承载该值）；短期最小修：TM 侧供给后端时校验 `KeySelectorPartitioner.maxParallelism == provisionedBackend.getMaxParallelism()`，不一致 fail-fast，禁止该形态静默运行。
- **信心水平**: 确定（代码路径与两处 M 来源均已逐行核实；分歧注释原文在案）
- **误报排除**: 该分歧在代码中被标注为 known divergence / plan 2278 Deferred，看似属"已裁定勿动"类。但裁定注释的论证范围仅覆盖"后端类型替换"这一可观察后果，未覆盖 maxParallelism 是路由公式链输入这一事实——AR-01 parity（KeyGroupAssignment.java:34-38 类注释明示的契约）在该路径上是断的。这不是"把已裁定分歧当新发现"的误报：裁定文件没有裁定过 routing/ownership 在 remote 路径的解耦，且本审计的授权任务恰是"找出该缺陷族的全部残留入口"。
- **复核状态**: 未复核

### [KR-2] checkpoint 落盘的 keyGroupOwnership 元数据（parallelism/maxParallelism/range）是 write-only：restore 路径从不读取，maxParallelism 变更后恢复静默错置
- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/checkpoint/TaskEpochSnapshot.java:140-200`；`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java:1292-1296,1412-1438`
- **证据片段**:
```java
// CheckpointSerDe.java:764-772（写入侧注释明示消费意图）
        // Persist key-group ownership metadata so a rescale restore
        // can read the recorded range instead of re-deriving it.
        if (snapshot instanceof TaskEpochSnapshot) {
            TaskEpochSnapshot epoch = (TaskEpochSnapshot) snapshot;
            if (epoch.isKeyGroupOwnershipMaterialized()) {
                map.put("parallelism", epoch.getParallelism());
                map.put("maxParallelism", epoch.getMaxParallelism());
// GraphModelCheckpointExecutor.java:1419（读取侧：checkpointPlan 参数被忽略）
    static int resolveMaxParallelism(GraphExecutionPlan execPlan, CheckpointPlan checkpointPlan) {
        for (String vertexId : execPlan.getSortedVertexIds()) { ... keyed.getMaxParallelism() ... }
        return KeyGroup.DEFAULT_MAX_PARALLELISM;
```
- **严重程度**: P1
- **现状**: checkpoint 侧 `RescaleStateAssembler.materializeKeyGroupOwnership`（RescaleStateAssembler.java:308-328）给每个子任务快照盖上 `setKeyGroupOwnership(parallelism, maxParallelism, range)`，CheckpointSerDe 序列化/反序列化它（CheckpointSerDe.java:769-773, 815-821）。但对主代码 grep 证实：`getMaxParallelism()/getKeyGroupRange()/isKeyGroupOwnershipMaterialized()` 除 SerDe 外**零生产消费者**。restore/rescale 决策用的是：oldParallelism = checkpoint 里 location 计数（GraphModelCheckpointExecutor.java:1302），maxParallelism = 当前 live 后端（1419-1438）。后果：(a) 用户在两次运行之间改了 maxParallelism 配置但未跑 `MaxParallelismReshardMigration`：若并行度同时变化，merge+filter 恰好按新 M 重新分布（侥幸正确）；若并行度不变，走 1:1 restore、不过滤，而新图路由按新 M 计算——记录落在与新路由不一致的旧归置上，keyed state 静默不可达。(b) 本应精确 fail-fast（"checkpoint M=128 vs live M=64，请先跑 reshard 迁移"）的守卫完全缺失，尽管承载该守卫所需的数据已被持久化。
- **风险**: 配置漂移路径上的静默数据错置；`MaxParallelismReshardMigration`（offline 工具）的正确使用无法被运行时强制，错用 savepoint 无告警。
- **建议**: 在 `restoreTaskStatesFromSource` 入口对每个 checkpoint 子任务快照增加一致性断言：`snapshot.maxParallelism == resolveMaxParallelism(execPlan, null)`（及 materialized range 与按 stamped (P, M) 重算值的自洽性），不一致抛 typed 异常并指引导向 `MaxParallelismReshardMigration`。元数据已就位，这是纯增量守卫。
- **信心水平**: 确定（grep 全库证明 write-only；两处决策输入已逐行核实）
- **误报排除**: "元数据没人读"类发现容易沦为风格抱怨。这次不是：CheckpointSerDe 写入侧注释明文承诺 "so a rescale restore can read the recorded range instead of re-deriving it"，实现与承诺不符属于契约未闭环；且本审计要点 4（maxParallelism 钉定时机一致性）直接命中。同时已排除"rescale 分支其实会用 stamped M"的可能——`buildRescaledTaskState` 的 maxParallelism 形参来自 `resolveMaxParallelism`（live 后端），与 stamped 值无任何交集。
- **复核状态**: 未复核

### [KR-3] keyBy 的 maxParallelism 在建图期一次性快照，运行期无任何 equality 校验；配置顺序或默认值不对称均可静默/晚败地破坏公式链
- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/datastream/DataStreamImpl.java:429-318,434-443`；`nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/RocksDBStateBackend.java:68-76`
- **证据片段**:
```java
// DataStreamImpl.java:302（建图期把 M 烘进分区器，此后不再复核）
        IPartitioner<T> partitioner = new KeySelectorPartitioner<>(key, resolveKeyGroupMaxParallelism());
// DataStreamImpl.java:434-437（M 来源 = keyBy 调用时刻的 CheckpointConfig）
    private int resolveKeyGroupMaxParallelism() {
        CheckpointConfig checkpointConfig = environment.getCheckpointConfig();
        IStateBackend backend = checkpointConfig != null ? checkpointConfig.getStateBackend() : null;
        int maxParallelism = backend != null ? backend.getMaxParallelism() : KeyGroup.DEFAULT_MAX_PARALLELISM;
// RocksDBStateBackend.java:74-76（默认构造 M=1，与 MemoryStateBackend 默认 128 不对称）
    public RocksDBStateBackend(String dbPath) {
        this(dbPath, 1, null);
```
- **严重程度**: P1
- **现状**: 全库 grep 证实主代码中不存在任何 `KeySelectorPartitioner.maxParallelism == backend.getMaxParallelism()` 校验点。三个具体缺口：(1) **顺序脆弱**——`env.setStateBackend(b)` 若在 `keyBy()` 之后调用，分区器保持旧 M（默认 128），运行期后端是新 M，静默分裂（本地路径即中招，无需 remote）；(2) **默认值不对称**——`new RocksDBStateBackend(path)` 的 M=1，`resolveKeyGroupMaxParallelism` 读到 1 后，任何 keyBy+P>1 的作业在**第一条记录路由时**才在 `assignKeyGroupToSubtask` 守卫（KeyGroupAssignment.java:201-203）抛 StreamException，错误语义是运行期路由失败而非配置期"parallelism ≤ maxParallelism"校验失败；(3) 该守卫每条记录都会重新触发，无一次性 build 期等价物。
- **风险**: (1) 是 R4 P0 的又一个复活入口（静默）；(2)(3) 是晚败与诊断成本（报错出现在数据面而非建图面，用户难以定位到 backend 构造参数）。
- **建议**: (a) 在 `execute()/buildJobGraph()` 收口处遍历 Transformation 边，对每个 `KeySelectorPartitioner` 断言其 M 等于当时解析的后端 M，不等则 fail-fast（同时覆盖顺序脆弱与 remote 分歧的本地可测子集）；(b) keyBy 时刻若解析出 `parallelism > M` 直接在建图期抛错（P 可随后变化，故 execute 期再校一次）；(c) 统一 `RocksDBStateBackend(dbPath)` 默认 M 为 `KeyGroup.DEFAULT_MAX_PARALLELISM` 或在 javadoc/文档中把 M=1 标注为 keyBy 不兼容。
- **信心水平**: 确定（守卫缺失经全库 grep 证实；三个缺口均有直接代码证据）
- **误报排除**: "建议加校验"类发现容易是防御式编程偏好。这次的结构性理由是：AR-01 契约的正确性完全依赖"M 同源"这一隐式前提，而该前提没有任何机器检查，且已存在至少两个现实入口（setStateBackend 顺序、RocksDB 默认构造）使其断裂。CR 与测试均未覆盖 M 不一致形态（现有测试全部硬编码 M=128/16 两侧一致）。
- **复核状态**: 未复核

### [KR-4] `stableHash` 契约头声明与实现存在缺口：非 JSON-serializable 键回落 identity hashCode（仅 WARN），Enum 走 `name().hashCode()` 与值类型走 murmur3 两条公式
- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/shard/KeyGroupAssignment.java:22-92`
- **证据片段**:
```java
        try {
            String json = JsonTool.serialize(key, false);
            return HashHelper.murmur3_32(json);
        } catch (RuntimeException notSerializable) {
            LOG.warn("Key of type {} is not JSON-serializable; falling back to identity hashCode for key-group routing (JVM-variable — not checkpoint-stable)",
                    key.getClass().getName(), notSerializable);
            return key.hashCode();
        }
```
- **严重程度**: P3
- **现状**: G38 契约头（第 22-32 行）宣称"For every other type the hash is Murmur3 over the canonical JSON bytes"，但方法体保留了 identity hashCode 回落分支。Enum 已正确特判（`name().hashCode()`，JDK Enum.hashCode 是 identity hash）。回落分支触发即意味着该键的 key→group 映射跨 JVM/重启不稳定，checkpoint 的 keyed state 恢复后不可达。回落有 WARN 日志可观测，且注释论证了"真实 keyed 键必须可 JSON 序列化才能 checkpoint"。
- **风险**: 仅当生产代码在 checkpoint 关闭或首次 checkpoint 前长期使用不可序列化键时才实际发生；届时 WARN 是唯一信号，无 fail-fast。
- **建议**: 将回落分支的 WARN 升级为一次性去重告警或在 checkpoint 开启时直接 fail-fast（与"必须可序列化才能 checkpoint"的论断对齐）；同时把 G38 契约头补上回落分支的例外描述，消除文档-实现差额。
- **信心水平**: 确定（分支存在性确定；实际触发面取决于用户键类型）
- **误报排除**: 未把"Enum 与 String 公式不同"当缺陷——TestKeyRoutingOwnershipParity（enumKeyHashesByNameNotByIdentity）已裁定两条公式各自值稳定即可，parity 只要求路由与属主经同一函数，二者确实同函数。
- **复核状态**: 未复核

### [KR-5] `KeySelectorPartitioner` 对 null 键静默路由到 channel 0（Flink 抛 NPE）；当前与属主公式巧合一致，但属隐式耦合
- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/datastream/DataStreamImpl.java:470-483`
- **证据片段**:
```java
        public int partition(T value, int numPartitions) {
            try {
                Object key = keySelector.getKey(value);
                if (key == null) {
                    return 0;
                }
                return KeyGroupAssignment.assignToSubtask(key, maxParallelism, numPartitions);
```
- **严重程度**: P3
- **现状**: null 键全部进 subtask 0。核验：`stableHash(null)=0` → group 0 → `assignKeyGroupToSubtask(0, M, P)` 在 base/rem 闭式下对任意合法 M/P 都返回 0（g=0 < firstRegionEnd 或 0/base=0），所以**当前 parity 成立**。但这是一致性靠巧合而非靠公式路径的残留入口：若 `assignKeyGroupToSubtask` 的分桶闭式未来改为 Flink 式 `g*P/M`（其注释明确讨论过两者的边界差异），g=0→0 仍成立，但任何对 group 0 特殊处理的改动都会同时破坏 null 键 parity，而该特判不会经过任何公式链测试。
- **风险**: 低。行为差异（vs Flink 的 NPE fail-fast）+ 一致性依赖隐式数值巧合。
- **建议**: null 键显式抛 `ERR_STREAM_PARTITION_KEY_FAILED`（对齐 Flink），或在注释中写明 "channel 0 == group 0 owner" 的推导并加一条 pinned 测试。
- **信心水平**: 确定（代码事实）；"巧合一致"的判定经过闭式逐例验证
- **误报排除**: 未作为 P0/P1 上报，因为当前无任何错误行为；列为残留入口是因为审计要点 1 要求逐一核验 key→subtask 决定点，此处的 early-return 恰好绕开了 `assignToSubtask` 公式路径。
- **复核状态**: 未复核

### [KR-6] `HashPartitionRouter` 的 `record.getValue().hashCode()` 回落分支：对记录**值**取 JVM 哈希，绕开 key-group 公式——当前内建建图不可达的残留入口
- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/HashPartitionRouter.java:28-36`
- **证据片段**:
```java
    public int selectChannel(StreamRecord<?> record) {
        if (partitioner != null) {
            int channel = ((IPartitioner<Object>) partitioner).partition(record.getValue(), numPartitions);
            return Math.floorMod(channel, numPartitions);
        }
        return Math.floorMod(record.getValue().hashCode(), numPartitions);
    }
```
- **严重程度**: P3
- **现状**: 若 HASH policy 与 null partitioner 组合出现，该分支对记录值做 `hashCode() % n`（POJO 为 identity hash，且哈希对象是值不是键）。当前不可达性已核验：本地 `PartitionedPlanGenerator.inferPartitionPolicy`（PartitionedPlanGenerator.java:92-116）null partitioner → FORWARD、非 aware partitioner → fail-fast；remote `resolvePartitionPolicy`（RemoteGraphExecutionPlanBuilder.java:512-526）仅 partitioner != null 才回 HASH；`PartitionRouter.create` 的唯一生产调用点是 `EdgeAssembly.createWriterForEdge`（EdgeAssembly.java:75-76），partitioner 来自 JobEdge。因此 HASH+null-partitioner 在仓库内无生产构造路径。另外该类的 javadoc 引用 "routed via {@link StateShard#stableHash}" 已过时（keyBy 实际经 `KeySelectorPartitioner`→`assignToSubtask`）。
- **风险**: 作为公开静态工厂 `PartitionRouter.create` 的可组合面存在；未来新增 plan 构建器若以 (HASH, null) 调用即复活 R4 族缺陷，且键控场景下语义完全错误（对 value 而非 key 哈希）。
- **建议**: 把 null-partitioner 的 HASH 回落改为 fail-fast（`ERR_STREAM_INVALID_STATE`），或复用 `KeySelectorPartitioner` 所在公式链；同步修正 javadoc 引用。
- **信心水平**: 确定（分支存在；不可达性经三个构建器逐一核验）
- **误报排除**: 明确不把它当 P0/P1——审计要求"以 live code 为准，必须有结构性原因或可量化风险"；其风险是"可达性依赖当前构建器的约定而非类型系统强制"，属于缺陷族残留入口盘点义务，不是现行错误行为。
- **复核状态**: 未复核

### [WIN-1] session/merging 窗口 + evictor：merge 时 element-timestamps 侧存储不对齐、被合并窗口的侧存储条目泄漏，TimeEvictor 基于伪时间戳做错误驱逐
- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java:1686-1795（mergeWindowContents）,1055-1068（伪时间戳回落）,1540-1563（侧存储读写）`；`WindowOperatorBuilder.java:127-135`（evictor 与任意 assigner 可自由组合）
- **证据片段**:
```java
// mergeWindowContents 的 ListState 分支：只合并元素列表，完全不触碰 elementTimestampsState
        if (newListWindowState != null) {
            ...
            newListWindowState.setCurrentNamespace(targetWindow);
            List<IN> targetElements = new ArrayList<>();
            ... for (IN e : existing) targetElements.add(e); ...
            for (W sourceWindow : sourceWindows) {
                ... targetElements.add(e); ...
                newListWindowState.clear();          // 只清元素，不清该 namespace 的时间戳
            }
            newListWindowState.update(targetElements);
            return;                                   // 无任何 elementTimestampsState 合并/清理
// emitWindowContents：索引超出 storedTimestamps 时用当前 watermark 冒充元素时间戳
                if (storedTimestamps != null && idx < storedTimestamps.size()) { ... } else {
                    elementTimestamp = internalTimerService.currentWatermark();
                }
```
- **严重程度**: P1
- **现状**: evictor 存在时 `open()` 创建 `elementTimestampsState`（WindowOperator.java:537-543），`addWindowElement` 对 ListState 路径按 stateWindow namespace 索引对齐地写入时间戳（1451-1457）。session 窗口合并时 `mergeWindowContents` 把 source 窗口的元素追加进 target 列表，但 (a) target 的 `storedTimestamps` 仍是 target-only 长度，合并后 idx ≥ size 的元素在 emit 时被盖上 `currentWatermark` 伪时间戳，`TimeEvictor` 的 `record.getTimestamp() <= evictCutoff` 判定从此基于错误时间——迟到的真实旧元素可能被保留、新元素可能被误驱逐（驱逐还回写状态，污染永久化）；(b) source 窗口 namespace 下的时间戳条目永不清理（该分支用 `newListWindowState.clear()` 而非 `clearWindowContents`，后者才会同时清 `ELEMENT_TIMESTAMPS_KEY`，见 1633-1635），长跑 session+evictor 作业中随每次 merge 无界累积。注：DSL 层（AdvancedTransforms/flow）未暴露 evictor，但 `WindowedStream.evictor()`（WindowedStream.java:88）与 `WindowOperatorBuilder.evictor()` 是公开 API，session+evictor 是合法组合。
- **风险**: 合法 API 组合下的静默错误输出 + 无界状态增长。evictor 驱逐决策错误直接改变窗口计算结果。
- **建议**: `mergeWindowContents` 的 ListState 分支按索引对齐合并两侧 `elementTimestampsState` 并 clear source namespace 的时间戳条目；或至少在 evictor + MergingWindowAssigner 组合时 build 期 fail-fast（明确裁定为不支持组合）。
- **信心水平**: 确定（代码路径完整核实；未运行测试验证现象，故不标"确定级现象"）
- **误报排除**: 未把"evictor+session 少见"当豁免——Flink 本身支持该组合，且 `WindowOperator.java:530-536` 注释自证侧存储就是为 evictor 的正确时间戳而建（"without it ... TimeEvictor never evicts"），merge 路径破坏的正是该注释要保证的契约。也没有把正常路径（非 merging 窗口 + evictor）误伤：tumble/slide 的 window==stateWindow，无 merge，侧存储对齐。
- **复核状态**: 未复核

### [WIN-2] 窗口作业 rescale：timer/pane-tracking/trigger-accumulators 走 operator state 1:1 拷贝，与按 KeyGroupRange 重分布的窗口内容分裂——scale-down 静默丢窗口输出
- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java:628-656`（operator state 落盘）、`945-952`（contents null → 静默不发）；`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/RescaleStateAssembler.java:140-159,190-200`
- **证据片段**:
```java
// WindowOperator.snapshotState：timers 是 OPERATOR state
        if (internalTimerService != null) {
            result.putOperatorState(TimerStateKeys.WINDOW_INTERNAL_TIMERS, internalTimerService.snapshotTimers());
        }
// RescaleStateAssembler.buildRescaledTaskState：operator state 1:1 by index；scale-up 才有守卫
    if (taskIndex < oldParallelism) {
        TaskLocation oldLoc = oldSubtasks.get(taskIndex);
        TaskStateSnapshot oldState = stateLookup.lookup(oldLoc);
        if (oldState != null && oldState.getOperatorStates() != null) {
            for (...) merged.putOperatorState(e.getKey(), e.getValue());
// WindowOperator.onTimer fire 路径：内容不在本子任务时静默跳过
            ACC contents = getWindowContents(triggerContext.key, stateWindow);
            if (contents != null) { emitWindowContents(...); }
```
- **严重程度**: P1
- **现状**: rescale restore 把 keyed state（窗口内容）按 KeyGroupRange 全量重分布（buildRescaledTaskState → mergeAndFilterKeyedStates），但窗口的 event-time/processing-time timer 快照、`pane-tracking`、`trigger-accumulators` 都在 operator state 里，按 oldIndex==newIndex 1:1 拷贝。并行度 4→2 时：(a) old subtask 2/3 的 timer 整体丢弃（无人认领）；(b) 存活的 timer 到新子任务上触发时，其 key 的窗口内容已被 range 重分布到（可能）另一个子任务，`getWindowContents` 返回 null，fire 分支静默不 emit——窗口结果丢失且无任何日志/计数。scale-up 方向 `assertNoOperatorStateOnScaleUp`（140-159）会因 operator state 非空而 fail-fast，2PC sink 也有 typed 守卫（GraphModelCheckpointExecutor.java:1317-1322），唯独 window/带 timer 的普通 keyed 作业在 scale-down 方向无守卫、静默错。同并行度恢复不受影响（1:1 且 range 不变）。
- **风险**: 对窗口作业做 scale-down 恢复 = 部分窗口永不发射/pane 分类错乱，静默。这是 rescale 能力（Stage 35）对窗口语义的未闭环面。
- **建议**: 短期：把 `assertNoChannelStateOnRescale` 式的 typed fail-fast 扩展到"携带 WINDOW_INTERNAL_TIMERS/pane-tracking 的 vertex 的任何跨并行度 restore"（与 2PC sink 守卫同型）；长期：timer 重分布——timer 本质是 (key, window, ts) 三元组，可按 `assignKeyGroupToSubtask(assignToKeyGroup(key))` 随 keyed state 一起重分布。
- **信心水平**: 很可能（静态推理链完整：timer 注册子任务=key 属主子任务、operator state 1:1、fire 路径 null 静默；未构造复现测试）
- **误报排除**: 未与既有裁定冲突——`assertNoOperatorStateOnScaleUp` 的注释只声称覆盖"scale-up 时 added subtasks 空启动"，scale-down 被 1:1 掩盖为"kept subtask index still maps to an old subtask"，该论证对非 keyed operator state（如 source split 表）成立，对 timer 这种**以 key 为粒度、必须跟随 keyed ownership 走**的状态不成立。也没有把 `mergingWindows.getStateWindow(...)==null return`（onTimer 930-936）当作同一现象——那是显式文档化的 trigger 未清理形态。
- **复核状态**: 未复核

### [WIN-3] processing-time 窗口的 PaneInfo timing 恒为 EARLY：`computePaneInfo` 只用 watermark 判定，无视 processing time
- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java:1156-1182`
- **证据片段**:
```java
        long watermark = internalTimerService.currentWatermark();
        PaneInfo.PaneTiming timing;
        boolean isOnTimeOrLate = watermark >= window.maxTimestamp();
        if (isOnTimeOrLate && !tracking.onTimeEmitted) {
            timing = PaneInfo.PaneTiming.ON_TIME;
            tracking.onTimeEmitted = true;
        } else if (isOnTimeOrLate && tracking.onTimeEmitted) {
            timing = PaneInfo.PaneTiming.LATE;
        } else {
            timing = PaneInfo.PaneTiming.EARLY;
        }
```
- **严重程度**: P2
- **现状**: processing-time assigner（TumblingProcessingTimeWindows 等，`isEventTime()==false`）作业的 watermark 通常恒为 `Long.MIN_VALUE`，`isOnTimeOrLate` 永假，经 `ctx.getPaneInfo()` 暴露给 `ProcessWindowFunction` 的 timing 对所有 firing（包括到期触发的 ON_TIME 性质发射）都报 EARLY。event-time 路径正确。`window-failfast-decisions.md` D9 行引用的 "PaneInfo EARLY/ON_TIME/LATE :1147-1165" 即此实现，裁定文档未对 processing-time 形态做约束。
- **风险**: 用户在 processing-time 窗口的 ProcessWindowFunction 里按 pane timing 分流（如只对 ON_TIME 结果入账）会得到全 EARLY 的错误分类；不丢数据、不改聚合值，属用户可见语义缺陷。
- **建议**: `computePaneInfo` 按 `windowAssigner.isEventTime()` 分派时钟：event-time 用 watermark，processing-time 用 `internalTimerService.currentProcessingTime()` 与 `window.maxTimestamp()` 比较。
- **信心水平**: 确定（代码事实；processing-time 下 watermark 恒 MIN_VALUE 由 WatermarkStatus/idle 机制与 `processWatermark` 转发路径支持）
- **误报排除**: 未当成 P1——影响面仅 `ctx.getPaneInfo()` 的读数，不触发错误发射/丢失；也排除"EARLY 是有意保守"的可能：`tracking.onTimeEmitted` 的去重逻辑明确定义了 ON_TIME 语义，只是取错了时钟源。
- **复核状态**: 未复核

---

## 检查范围

**路由/属主一致性（要点 1-5）**：
- `KeyGroupAssignment`（stableHash/assignToKeyGroup/assignToSubtask/computeKeyGroupRangeForSubtaskIndex/assignKeyGroupToSubtask 全函数）、`KeyGroup`、`KeyGroupRange`、`ShardPrefixedKey`、`StateShard`、`StateKeyRematerializer`、`KeyGroupRangeRestoreFilter`、`KeyGroupReshard`（nop-stream-core shard 包全部 live 文件）
- `MemoryKeyedStateBackend`/`MemoryStateBackend`/`MemoryStateSerDe`、`RocksDBKeyedStateBackend`/`RocksDBStateBackend`/`RocksDBSnapshotSerDe`（routeKey/snapshot/restore/keyType 头）
- `DataStreamImpl.keyBy/KeySelectorPartitioner/resolveKeyGroupMaxParallelism`、`RecordWriter`、`PartitionRouter`+四个 Router、`EdgeAssembly`、`GraphExecutionPlan.resolvePartitionPolicy/resolveParallelism`、`PartitionedPlanGenerator.inferPartitionPolicy`、`RemoteGraphExecutionPlanBuilder`（三分歧点）、`RemoteTaskDeploySupport`、`TaskCheckpointWiring`
- `GraphModelCheckpointExecutor`（restore/rescale 主路径、resolveMaxParallelism、channel-state 守卫）、`RescaleStateAssembler` 全文件、`MaxParallelismReshardMigration`/`ReshardMigrationResult`、`TaskEpochSnapshot`、`CheckpointSerDe`（ownership 段）、`CheckpointConfig`（Builder）
- `AssignmentPlanner`（确认为 task→node round-robin，非 key 路由）、`EquiJoinOperator`（equiKey 经 keyBy 路由）、`CepOperator`/`ProcessOperator`（setCurrentKey 路径抽查）
- `StreamModelDslBuilder`（HASH edge/keyBy/join 校验与 maxParallelism fail-fast）、`AdvancedTransforms`（join 装配 union→keyBy(equiKey)）
- 测试保护力：`TestKeyRoutingOwnershipParity`、`TestRealRoutingRescaleE2E`、`TestKeyGroupRescaleDispatchE2E`、`TestChannelStateRescaleE2E`、`TestS2RecoveryAndRescaleE2E`、`TestS2OfflineReshardE2E`、`TestMaxParallelismReshardMigrationE2E`、`TestKeyGroupRoutingAcrossBackendsDeterministic`（nop-stream-rocksdb）
- `HashHelper.murmur3_32`（nop-kernel/nop-commons，Guava murmur3_32/UTF-8，确认确定性）

**窗口语义（要点 6-9）**：
- assigners：`WindowAssigner`、`MergingWindowAssigner`、`WindowAssignerSupport`、`Tumbling/Sliding × Event/ProcessingTimeWindows`、`EventTime/ProcessingTimeSessionWindows`、`GlobalWindows`、`WindowStagger`
- 窗口与时间：`TimeWindow`（intersects/cover/mergeWindows/getWindowStartWithOffset）、`EventTimeTrigger`、`ProcessingTimeTrigger`、`CountTrigger`、`PurgingTrigger`、`Continuous*Trigger`（签名抽查）
- 运行时：`WindowOperator`（全文 2074 行）、`MergingWindowSet`（全文）、`WindowOperatorBuilder`（全文件）、`WindowedStreamImpl`/`WindowedStream`（装配与 evictor/allowedLateness 面）、`TimeEvictor`/`CountEvictor`/`DeltaEvictor`/`Evictor`、`Internal*WindowFunction`、`PaneInfo`/`PaneState`
- 设计一致性：`ai-dev/design/nop-stream/window-failfast-decisions.md`（D9-D12）对照 `AdvancedTransforms.buildWindow/failFastOnUnsupportedWindowStrategy/failFastOnUnsupportedWindowNodeAttrs` 与 `WindowOperator.open()/emitWindowContents`
- 抽查：`TestE2EWindowAllowedLatenessFullAggregate` 存在性（D9 全量聚合回归）、`TestWindowOverflow`/assigner 边界测试族存在性

**未覆盖/边界**：`OverWindowOperator`、`nop-stream-sql` 编译器窗口方言（另属 SQL 审计横切面）、`nop-stream-cep` 内部 NFA、RocksDB 增量 checkpoint 段存储细节、运行期物理传输（RemoteResultPartition 流控）——均不属本次两个横切面的问题定义。

---

## 附录：路由/属主决定点清单（可复现）

统一公式链（canonical）：`stableHash(key)`（KeyGroupAssignment.java:62-92）→ `(h & 0x7FFFFFFF) % maxParallelism`（:101-106）→ 分桶闭式 `g < rem*(base+1) ? g/(base+1) : rem + (g-rem*(base+1))/base`（:194-214）；range 闭式 `start=i*base+min(i,rem), size=base+(i<rem)`（:150-174）。

| # | 决定点 | 文件:行号 | 公式 | 一致性结论 |
|---|---|---|---|---|
| 1 | keyBy 路由（生产唯一入口） | DataStreamImpl.java:297-318, 453-489（`partition`→`assignToSubtask` 于 :477） | `assignToSubtask(key, M_keyBy, P_target)` | 一致（前提 M_keyBy==M_backend，见 KR-1/KR-3） |
| 2 | M_keyBy 来源 | DataStreamImpl.java:434-443 | `checkpointConfig.getStateBackend().getMaxParallelism()`，无后端→128 | 与运行期后端同源（local）；remote 分裂（KR-1）；无 equality 校验（KR-3） |
| 3 | DSL HASH edge / join 装配 | StreamModelDslBuilder.java:1042-1058；AdvancedTransforms.java:546 | 复用 `DataStream.keyBy`（即 #1） | 一致 |
| 4 | RecordWriter 通道选择 | RecordWriter.java:306-317；EdgeAssembly.java:56-80 | router 优先（HASH→#1 经 KeySelectorPartitioner），legacy `floorMod(partition(v,n),n)` | 一致（n=目标并行度，matrix 行） |
| 5 | HashPartitionRouter 回落 | HashPartitionRouter.java:30-36 | `floorMod(value.hashCode(), n)` | **不属公式链**；内建建图不可达（PartitionedPlanGenerator.java:92-116；RemoteGraphExecutionPlanBuilder.java:512-526）——残留入口 KR-6 |
| 6 | memory 后端状态归置 | MemoryKeyedStateBackend.java:554-564（routeKey） | `assignToKeyGroup(key, M_backend)` | 一致（M 同源时） |
| 7 | RocksDB 后端状态归置 | RocksDBKeyedStateBackend.java:493-499 | `assignToKeyGroup(key, M_backend)` | 一致（M 同源时） |
| 8 | restore 范围过滤（serde 级） | KeyGroupRangeRestoreFilter.java:59-70；MemoryStateSerDe.java:280；RocksDBSnapshotSerDe.java:363-367 | `assignToKeyGroup(rematerialized, M)` + `range.contains` | 公式一致；`setTargetKeyGroupRange` 主代码无生产调用（仅测试），生产过滤在 #9 |
| 9 | rescale restore 过滤/重分布 | GraphModelCheckpointExecutor.java:1296,1354-1357；RescaleStateAssembler.java:181-283 | newRange=`computeKeyGroupRangeForSubtaskIndex(M_live, P_new, i)`；filter=`assignToKeyGroup` | 公式一致；M 取 live 后端，checkpoint stamped M 未消费（KR-2） |
| 10 | checkpoint 所有权落盘 | RescaleStateAssembler.java:308-328；CheckpointSerDe.java:769-773,815-821 | `computeKeyGroupRangeForSubtaskIndex(M_live, P, i)` 写入 TaskEpochSnapshot | 写侧一致；读侧缺失（KR-2，write-only） |
| 11 | maxParallelism reshard（离线） | KeyGroupReshard.java:92-153；MaxParallelismReshardMigration.java:345-431 | `assignToKeyGroup(newM)`+`assignKeyGroupToSubtask`+`computeKeyGroupRangeForSubtaskIndex`，keyType 先重物化 | 一致；含守卫（P≤M、old≠new、守恒断言） |
| 12 | coordinator 指派 | AssignmentPlanner.java:148 | `runtimeNodeIndex % activeNodeCount`（task→node） | 不适用（非 key→subtask 路由） |
| 13 | stableHash 副本 | StateShard.java:118-131（`stableHash` 委托、`computeShardId` 遗留） | 委托 `KeyGroupAssignment.stableHash`；`computeShardId` 用 stateShardCount 取模但主代码无路由调用 | 一致（遗留 API 已收口到单一函数） |
| 14 | 后端供给点 | TaskCheckpointWiring.java:166-178（local）；RemoteTaskDeploySupport.java:171-181（remote） | local=配置后端；remote=恒 `new MemoryStateBackend()`(128) | local 一致；remote 即 KR-1 |

**窗口 fail-fast 设计裁定一致性**（window-failfast-decisions.md ↔ 实现）：
- D9（allowedLateness 放行）：AdvancedTransforms.java:199-236（strategy/node 双层合并消费）+ WindowedStreamImpl.java:154 + WindowOperator.java:1109-1115（`skipPurgeForLateness` 把 DISCARDING 清空推迟到 cleanup，与裁定"清空点必须推迟到 window end + lateness"一致）+ cleanup timer `maxTimestamp+allowedLateness`（:1410-1417）。**一致**；`TestE2EWindowAllowedLatenessFullAggregate` 提供回归。
- D10（ACCUMULATING_AND_RETRACTING fail-fast）：WindowOperator.java:450-456 open 期门禁 + AdvancedTransforms.java:229-233 build 期非 DISCARDING 拒绝。**一致**。
- D11（triggerId fail-fast）：AdvancedTransforms.java:219-222（strategy 级）、245-247（节点级）。**一致**。
- D12（窗口级 parallelism fail-fast）：AdvancedTransforms.java:176-186。**一致**。

**R4 修复完备性总裁定**：公式统一已完成且内建链路（#1-#4、#6-#11、#13）全部汇聚单一公式链，`TestRealRoutingRescaleE2E.realDispatchRoutesEachKeyToItsOwnershipSubtask` 以真实分发验证 parity、`TestKeyRoutingOwnershipParity.testDataActuallyDistinguishesLegacyRoutingFormula` 防空洞；但 KR-1/KR-2/KR-3 三个残留入口均可在配置组合下使 M 或归属源分裂，重新引入 R4 型错误行为，应作为本缺陷族的收口清单。

**测试保护力评估（要点 5）**：
- 真实作业产出快照：`TestS2RecoveryAndRescaleE2E`（真实 DSL 作业 + `registerCheckpointExecutorFactory` + 周期 checkpoint + 从 durable manifest 恢复，窗口跨恢复边界，memory/RocksDB 双后端，TestS2RecoveryAndRescaleE2E.java:40-68,194-197）；`TestS2OfflineReshardE2E`（真实 RocksDB 两轮 run + 离线 reshard + 恢复，:126-164）。
- 真实分发 + 半手工 savepoint：`TestRealRoutingRescaleE2E.rescaleRestoreKeepsStateOnTheSubtaskRecordsRouteTo`——phase A 用真实 `executeWithCheckpoint` 观测实际路由落位，再按观测落位**手工**构造 savepoint（:278-317），phase B 用真实 restore+分发恢复（:321-329）。公式链回归保护强，但 savepoint 载荷非后端 `snapshotState` 产出。
- 手工 savepoint（明确坦白）：`TestKeyGroupRescaleDispatchE2E.java:52-58` 注释自述"savepoint is hand-staged by the OWNERSHIP formula ... this test could not detect the routing/ownership mismatch"；`TestChannelStateRescaleE2E.java:53-55`（"a savepoint staged with channel state"）亦为构造快照。
- 覆盖缺口（支撑 KR-2/KR-3/WIN-2）：无任何测试覆盖 M 配置不一致（跨 maxParallelism 恢复未走迁移工具）、remote 部署 + 非默认 M、窗口作业 scale-down 恢复；现有测试两侧 M 均硬编码相等（128/16）。

---

## 深挖第 2 轮追加

- **轮次日期**: 2026-10-05
- **HEAD SHA**: `bd089444f5c2b1fcd2c9a964ce909bac774a83d9`（与第 1 轮同基线）
- **范围**: 在第 1 轮发现暴露的面上继续深挖四个盲区：(1) maxParallelism/state backend 配置从 DSL/客户端到 TM 的全部传播点；(2) checkpoint 信封其余字段的读写闭环；(3) 窗口/CEP/join/OVER 各算子 operator-state vs keyed-state 分类与传输协议（对照 AR-13 缺陷族：恢复只认活对象实例、JSON 往返后 instanceof 静默跳过）；(4) session merge 精度、watermark idle 场景、custom assigner 声明面、trigger onMerge、窗口×CEP keyGroup 一致性。只读静态审计，未运行 maven/测试，未修改产品代码。
- **结论概要**: 新增 3 条（KR-7 P1、WIN-4 P1、KR-8 P3）。最有价值的是 KR-7：join/OVER 算子把非 @DataBean 的 `PerKeyOrderedBuffer` 放进 operator state，durable checkpoint 默认配置下**每次序列化必抛** `ERR_JSON_ONLY_DATA_BEAN_IS_SERIALIZABLE`（join×durable 恢复组合不可用），该故障机制在本仓库已有两处在案先例（CEP NFAState、FilePendingCommit）而此处漏修。核过未立项的点见本节末尾清单。

### [KR-7] join/OVER 算子的 PerKeyOrderedBuffer 工作视图走 operator state 传输但无 @DataBean/@type 往返形式：durable checkpoint 默认配置下序列化必抛 ERR_JSON_ONLY_DATA_BEAN_IS_SERIALIZABLE；关闭 only-data-bean 后退化为 `{}` 静默截断 + restore instanceof 静默跳过（AR-13 同族）
- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/join/EquiJoinOperator.java:140-146,150-166`；`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/OverWindowOperator.java:120-148,257`；`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/buffer/PerKeyOrderedBuffer.java:32`；`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/CheckpointSerDe.java:848-876,384,119,254`
- **证据片段**:
```java
// EquiJoinOperator.java:144 — open() 后恒非空，快照恒携带该对象
        result.putOperatorState(BUFFER_STATE, buffer);
// EquiJoinOperator.java:163 — 非活对象即静默跳过（无日志/无异常）
            if (restored instanceof PerKeyOrderedBuffer) {
// CheckpointSerDe.java:848 — 三个已知类型之外全部原样透传
        * Currently handled: TimerSnapshot, PaneTrackingSnapshot, and
        * SimpleAccumulator values ... Everything else passes through unchanged.
// CheckpointSerDe.java:384 — checksum 与落盘都对嵌套 map 做全深度 JsonTool.serialize
        Map<String, Object> reparsed = JsonTool.parseMap(JsonTool.serialize(canonical, false));
// JsonSerializer.java:157-158（nop-core，默认 nop.core.json.serialize-only-data-bean=true）
        if (!checker.isAllowSerialize(onlyForDataBean, o, beanModel))
            throw new NopException(ERR_JSON_ONLY_DATA_BEAN_IS_SERIALIZABLE)...
```
- **严重程度**: P1
- **现状**: `PerKeyOrderedBuffer`（:32）是普通 `Serializable` POJO——无 @DataBean、无 getter 属性、不在 JsonWhitelist。durable 路径（epoch manifest 与 `.checkpoint` body 均经 `serializeTaskStateSnapshot`）把它原样透传进 canonical map，`computeCanonicalChecksumHex`（:384）在写任何字节之前就对全嵌套 map 做 `JsonTool.serialize` → `ERR_JSON_ONLY_DATA_BEAN_IS_SERIALIZABLE`。即：**AdvancedTransforms 生产装配的 join 作业（EquiJoinOperator 经 union→keyBy(equiKey) 进图）每开一次 durable checkpoint 必败**，无任何 build 期守卫，仅运行期暴露。该故障机制在本仓库已有两处在案先例：(a) `CepOperator.initStateAccess` 注释自证 NFAState 曾触发同一异常（"every checkpoint persist of a CEP operator failed with only-data-bean-is-serializable"，CepOperator.java:473-478，改走 JavaStreamSerializer→byte[] 修复）；(b) `FilePendingCommit` 显式 @DataBean 并注释明文契约 "the pendingCommits map is snapshotted into operator state and must round-trip the local-storage JSON persist path"（FilePendingCommit.java:26-39）。契约已知，PerKeyOrderedBuffer 是唯一漏网。第二张面孔：若用户以关闭 `nop.core.json.serialize-only-data-bean` 来"修复"，无 getter 的 bean 序列化为 `{}`，restore 的 `instanceof PerKeyOrderedBuffer`（EquiJoinOperator.java:163、OverWindowOperator.java:144）静默跳过——工作视图静默清空：join 丢 checkpoint 前缓冲记录（INNER 漏配对、LEFT 漏补发，静默），OVER 窗口丢历史帧。keyed 镜像在（`equi-join-buffer`/`over-window-buffer` MapState），但重建入口 `rebuildViewFromKeyedState`（OverWindowOperator.java:257）**全库无生产调用方**——恢复后无人从 keyed 镜像回填工作视图。测试保护力：`TestEquiJoinWithCheckpoint` 与 `TestE2EOverWindowWithCheckpoint`（后者甚至由测试手工遍历 operator state 做 instanceof+`mergeRestoredBuffer`，:91-99）全程内存活对象往返，从未触达 `serializeTaskStateSnapshot`/JsonTool 持久化路径——缺口与 KR-2/WIN-2 的测试缺口同型。
- **风险**: join + durable 恢复（S2 主打能力）两个已交付特性组合在默认配置下完全不可用（fail-loud 但仅运行期暴露）；配置降级路径转为静默数据丢失。OverWindowOperator 当前仅测试/示例可构造，生产面主要是 EquiJoinOperator。
- **建议**: (a) 最小修：给 PerKeyOrderedBuffer 增加自描述 form（对齐 PaneTrackingSnapshot 的 toSerializableForm/fromSerializableForm + CheckpointSerDe 两个分支），或删除 operator-state 传输、restore 统一调用 `rebuildViewFromKeyedState()`（keyed 镜像已是 §十 durable channel 的权威源，两个 E2E 的注释自认"both channels agree by construction"）；(b) 补一条 join + durable storage 的真实 JSON 往返 E2E；(c) 长期：把"operator state 值必须可 JSON 往返"从惯例升级为机器检查（snapshot 后试序列化），将该故障类从运行期移到 build 期。
- **信心水平**: 确定（JsonSerializer 白名单门禁与默认配置、写入链路、无生产调用方、测试路径均逐一核实；机制有 CEP/FilePendingCommit 两处在案先例）
- **误报排除**: 未把"operator state 必须 JSON 安全"当风格问题——FilePendingCommit 的注释证明该往返契约是本仓库明文设计约束；也未因"部署可能关闭 only-data-bean"而弱化默认分支——repo 内无任何 yaml/properties 覆盖该配置（全库 grep）。核对了 CEP 注释的确切含义：它修复的是 keyed ValueState 的 NFAState，与本条 operator-state 传输是同一异常的两个入口，不构成"已修复"。
- **复核状态**: 未复核

### [WIN-4] CepOperator 的 per-key event-time timer ledger 是 operator state：rescale 下与按 KeyGroupRange 重分布的 NFA/队列 keyed state 分裂（WIN-2 同族），scale-down 恢复后 drain 消费空状态并销账，静默丢失 pending 模式推进
- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:663-695,697-751,825-833,1024-1062`；`RescaleStateAssembler.java:190-200`（第 1 轮已核的 1:1 拷贝）
- **证据片段**:
```java
// snapshotState：per-key ledger 整体进 operator state
            result.putOperatorState(EVENT_TIME_TIMERS_STATE_NAME, timersForm);   // :692
// scale-down restore 后触发：setCurrentKey(key); onEventTime(currentWatermark);  // :829-833
// onEventTime：对（可能已被重分布走的）keyed 状态 drain，随后无条件销账
        NFAState nfaState = getNFAState();                                        // :1027
        ...
            timers.removeIf(timer -> timer <= time);                              // :1056
            if (timers.isEmpty()) { registeredEventTimeTimersByKey.remove(key); } // :1058
```
- **严重程度**: P1
- **现状**: CepOperator.snapshotState 把 `cep-current-watermark`（:666）、`cep-key-class`（:674）、per-key event-time timer ledger（:692）放 operator state；NFA computation state（`ValueState<NFAState>`，:478-481）、event 队列（:486）、SharedBuffer 是 keyed state。rescale restore：operator state 按 oldIndex==newIndex 1:1 拷贝，keyed state 按新 range 过滤重分布。并行度 4→2 时两支失败：(a) old subtask 2/3 的 ledger 整体丢弃——这些 key 的 pending timer 从此不存在，模式永不超时推进、状态滞留；(b) 存活 ledger 在新子任务上触发时，该 key 的 NFA/队列状态可能已被重分布走——`getNFAState()`/`elementQueueState.get()` 对空 keyed 状态 drain（不产生任何匹配）后，:1052-1060 把 ledger 条目**消费性删除**——pending timer 被永久销账而未产生结果，静默。scale-up 方向 `assertNoOperatorStateOnScaleUp` 因 operator states 恒非空（watermark :666 无条件 put）而 fail-fast：跨并行度恢复 CEP 作业 = scale-up 显式拒绝 / scale-down 静默错，方向不对称与 WIN-2 完全同型。注：`cep-current-watermark`/`cep-key-class` 是 subtask 粒度标量，1:1 拷贝语义正确；缺陷仅在 per-key 的 ledger 与 per-key 的 keyed 状态分属两类 state。
- **风险**: CEP 作业 scale-down 恢复后部分 key 永不推进（timer 丢失）、另一部分触发即销账（消费丢失），均静默。本条确认 WIN-2 缺陷族不是 WindowOperator 局部问题，而是"per-key 辅助状态放 operator state"这一装配惯例的系统性缺口。
- **建议**: 与 WIN-2 同收口：ledger 的 (key, timers) 按 `assignKeyGroupToSubtask(assignToKeyGroup(key))` 随 keyed state 重分布（timer 本质是 keyed 事件）；或短期把"携带 CEP_EVENT_TIME_TIMERS 的 vertex 的跨并行度 restore"纳入 typed fail-fast（与 2PC sink 守卫同型）；WIN-2 的收口清单显式扩展到 CepOperator。
- **信心水平**: 很可能（状态分类、1:1/range 重分布路径、drain+销账代码逐行核实；未构造复现测试）
- **误报排除**: 未与 AR-11 修复混淆——`rematerializeLedgerKey`（:763-784）解决的是恢复后 key 类型漂移，与本条"同一 key 的 timer 与 NFA 状态落在不同子任务"正交；也未把 scale-up 的 fail-fast 当作覆盖本条——它恰恰证明系统知道 operator state 不能跨并行度语义拷贝，却只拦了单向（与 WIN-2 对 2PC/timer 的论证一致）。
- **复核状态**: 未复核

### [KR-8] checkpoint body 信封的 `restored` 字段是死元数据：写入、反序列化回填，但主代码零消费者
- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/CheckpointSerDe.java:107,201,235-237`；`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/checkpoint/CompletedCheckpoint.java:85-90`
- **证据片段**:
```java
// 写侧：序列化时带出
        serializable.put("restored", checkpoint.isRestored());        // :107
// 读侧：回填后无任何主代码读取 isRestored() 做决策
        if (restored != null) { checkpoint.setRestored(restored); }   // :235-237
```
- **严重程度**: P3
- **现状**: 全库 grep 证实 `CompletedCheckpoint.isRestored()` 的唯一主代码调用者是写入侧自身（:107 读取刚 set 的值）；运行期置位点（CheckpointCoordinator.java:1120、MaxParallelismReshardMigration.java:449）只写不读。字段语义（"该 checkpoint 是否已被用作恢复源"）本可承载一次性恢复/防重复恢复语义，但没有任何决策消费它。与 KR-2 的 keyGroupOwnership 同属"信封元数据生产/消费未闭环"，但本字段连承诺性注释都没有。
- **风险**: 低。误导运维把该字段当"savepoint 已消费"标志；给未来实现 savepoint 一次性语义留下"字段已存在、无需迁移"的错觉。
- **建议**: 删除该字段（随 formatVersion 升位），或给出真实消费者（如恢复入口断言/防同 checkpoint 双恢复）并写明语义。
- **信心水平**: 确定（grep 全库零消费者）
- **误报排除**: KR-2 已把有具体错置路径的 keyGroupOwnership 定为 P1；本条无任何行为后果，单列 P3 以免与 KR-2 的修复混叠——KR-2 建议的一致性断言不应被解读为覆盖本字段。
- **复核状态**: 未复核

### 深挖第 2 轮核过未立项清单

- **KR-1 传播面补全**：maxParallelism 触点全库清点后，第 1 轮 14 决定点之外无新的丢弃/覆盖点。`EmbeddedDistributedExecutor.java:389-393` 的 maxParallelism 仅用于协调节点数 sizing，不是 key 路由公式输入；`StreamMaintenanceMain.java:104-114` 的 reshard CLI 要求人工提供 oldMaxParallelism（而 checkpoint 信封已 stamp 该值）——是 KR-2 write-only 的旁证，不单独立项。
- **窗口三字段传输协议（AR-13 对照）**：WindowOperator 的 trigger-accumulators（per-value `@type` 启发式，CheckpointSerDe.java:912-941）、WINDOW_INTERNAL_TIMERS（顶层 `@type:"TimerSnapshot"`，:906-907）、pane-tracking（顶层 `@type:"PaneTrackingSnapshot"`，:909-910）在 JSON 持久化路径均已闭环——AR-13 缺陷族在这三个字段上不成立。jdbc 2PC（DmlOp @DataBean）与 file 2PC（FilePendingCommit @DataBean）同样闭合；唯一缺口即 KR-7。
- **trigger onMerge**：`Continuous*Trigger.onMerge`（重注册 fire timer）与 merge 回调的被并窗口 accumulator/pane/user-state 清理顺序（WindowOperator.java:785-812，G-3）与 Flink 语义一致；merge 后 `onElement`（:848）重算并注册下一 fire timestamp，无 stale-timer 面。
- **merge 迟到守卫可达性**（WindowOperator.java:762-777 的两个 `ERR_STREAM_WINDOW_MERGE_INVALID_*` throw）：推演后未立项——event-time 分支被 cleanup timer（maxTimestamp+allowedLateness）与 watermark 推进的 mailbox 顺序封死（窗口过期即已 retire，不在 merging set 内可合并）；processing-time 分支因 `ProcessingTimeSessionWindows.assignWindows` 用当前时钟构造新窗口（:50-58，合并结果 end 必在未来）而不可达。属防御性不变式，非缺陷。
- **watermark idle/active 场景**：`AbstractStreamOperator.processWatermarkStatus`（:455-457）默认转发，WindowOperator/CepOperator/EquiJoinOperator 均继承或显式转发，无 idle-status 吞没导致的对齐 stall 面。
- **custom window assigner 声明面**：pane-tracking 仅持久化 TimeWindow-scoped 条目（WindowOperator.java:1194-1212，非 TW namespace 不可逆故跳过）为代码内明文裁定，静默降级已文档化——已知限制，P3 级未立项。
- **信封其余字段闭环**：`channelState`（executor:1384/assembler:104 消费）、`StorageJobIds`（两处消费）、`SavepointMetadata`（savepoint 流程读写）、keyed-state 信封载体 `StateSnapshot`（@DataBean）均闭环；唯一死字段即 KR-8。

---

## 维度复核结论

- **复核日期**: 2026-10-05（R5 独立维度复核轮）
- **复核基线**: 审计基线 `bd089444`，live HEAD 已前进至 `c14a7cfb`；逐条重读 live code 后，全部引用行号与代码内容仍与发现一致（漂移未触及本审计面），所有裁决以 live code 为准。
- **复核方法**: 12 条发现全部独立重走代码路径；对「零调用/恒」类论断自行全仓 grep 复现——(a) `new MemoryStateBackend()` 主代码全部供给点（4 处：RemoteTaskDeploySupport:176、TaskCheckpointWiring:174、WindowOperator:490 open 兜底、javadoc 示例）；(b) keyGroupOwnership 字段全部访问者（生产代码仅 CheckpointSerDe 写侧 :770-773 与反序列化回填 :815-821，消费仅测试）；(c) `KeySelectorPartitioner` M 与后端 M 的 equality 校验（全仓零命中，唯一 `maxParallelism ==` 在 StateShard.equals）；(d) `rebuildViewFromKeyedState` 调用方（仅定义 + 自身 javadoc 引用，零生产/测试调用）；(e) `isRestored()` 主代码消费者（仅 CheckpointSerDe:107 写侧自身）；(f) `nop.core.json.serialize-only-data-bean` 覆盖（全仓 yaml/properties 零覆盖，缺省 true 见 CoreConfigs:172-173）；(g) `deployTask` 生产 RPC 链（AssignmentPlanner:240 → TaskManager.deployTask:673 → wireDeployedSubtask）。

### 逐条裁决

- **KR-1 → 保留 P1**。remote 侧后端供给唯一入口核实（`provisionStateBackends` :171-181 恒 `new MemoryStateBackend()`=128，known-divergence 注释 :158-170 原文在案且论证范围仅覆盖"后端类型替换"）；local 侧同源（TaskCheckpointWiring:169-175 读 checkpointConfig）；keyBy 在建图期把作业侧 M 烘进 `KeySelectorPartitioner`（DataStreamImpl:302）并随图序列化到 TM；`resolveMaxParallelism` 读 live 后端（GraphModelCheckpointExecutor:1419-1438，checkpointPlan 形参确实未用）；`DeploymentPlan.stateBackendBinding` 确为 String 不承载 M。配置 M=64/256 的作业经 remote 部署时"路由 M ≠ 归置 M"链路完整成立，且该路径是生产 RPC 面，无任何校验。P1 恰当。
- **KR-2 → 保留 P1（机制表述微修，结论不变）**。write-only 经全仓 grep 复现证实；写入侧承诺性注释原文在案（CheckpointSerDe:764-766）；restore 决策输入 = location 计数 + live 后端；旁证加强：连离线迁移工具都要求人工提供 oldMaxParallelism（MaxParallelismReshardMigration 入参、StreamMaintenanceMain:104）而不消费信封 stamp。微修一点：M 漂移 + 并行度不变的 1:1 restore 中，放置并非保持"旧归置"——`restoreKeyedEntries`（MemoryStateSerDe:367-374）经 `backend.routeKey` 按新 M 重推导每个条目的归置；真实错位形态是"每个子任务只重放置自己旧分区的键 → 新归属下跨旧分区的键整体缺失 + 落在非本子任务新组的条目滞留不可达"，可观察后果（keyed state 静默不可达/缺失、无 fail-fast 守卫）与原结论一致。修复建议（入口一致性断言）仍以 stamped 元数据为唯一可行数据源，成立。
- **KR-3 → 保留 P1**。三缺口逐一核实：(1) `CheckpointConfig.setStateBackend`（:399）为普通 setter，keyBy 后调用即分区器 M（默认 128）与运行期后端 M 静默分裂，且无任何收口校验；(2) `RocksDBStateBackend(path)` M=1（:68-76，javadoc 自述陷阱）导致 keyBy+P>1 作业在 `assignKeyGroupToSubtask` 守卫（KeyGroupAssignment:200-203）首条记录抛 StreamException，错误面在数据面非建图面；(3) 该守卫确为每记录重入，无一次性 build 期等价物。
- **KR-4 → 保留 P3**。G38 契约头（:22-32）宣称非特判类型一律 murmur3(JSON)，方法体保留 identity hashCode 回落（:87-92，仅 WARN）——契约头与实现确有差额（方法内注释已自述回落，属文档内不一致而非无文档）；触发面窄（键不可 JSON 序列化则 checkpoint 本身也不可用），P3 恰当。
- **KR-5 → 保留 P3**。null 键 early-return :470-476 核实；parity 闭式逐例复验：`stableHash(null)=0` → g=0，`assignKeyGroupToSubtask(0,M,P)` 在 rem>0（0<firstRegionEnd → 0/(base+1)=0）与 rem=0（0<0 假 → rem+0/base=0）两分支均返回 0 = group 0 属主，巧合一致成立。当前无错误行为，作为公式链外残留入口记录，P3 恰当。
- **KR-6 → 保留 P3（两处微修，实质不变）**。HASH+null-partitioner 在内建建图不可达经三个构建器复现核实（PartitionedPlanGenerator.inferPartitionPolicy:92-99 null→FORWARD、非 aware fail-fast；RemoteGraphExecutionPlanBuilder.resolvePartitionPolicy:512-526 仅 partitioner!=null 回 HASH；`PartitionRouter.create` 唯一生产调用 EdgeAssembly:75-76）。微修：该类实际位于 `runtime/transport/` 而非发现头所引 `runtime/execution/`（行号一致）；现 javadoc 文为 "stable hash of the record key ... otherwise hashes the record value"，与发现引述的 `StateShard#stableHash` 字样有出入，但"文档称哈希键、回落实际哈希值"的 doc-code 失配实质成立。
- **KR-7 → 保留 P1**。durable 序列化链全步重走：`serializeCheckpoint`（CheckpointSerDe:98-121，body 与 checksum 均 JsonTool.serialize）与 `serializeEpochManifest`（:250-281）都经 `serializeTaskStateSnapshot`→`serializeOperatorStates`（:849-876 白名单三分支，其余原样透传，javadoc 明言 not JSON-safe 需转换）→`computeCanonicalChecksumHex`（:384 写字节前即全深 serialize）；门禁核实：`JsonWhitelistChecker.isAllowSerialize`（:24-29）onlyDataBean=true 时仅 @DataBean/白名单放行，`JsonSerializer`（:157-158）抛 ERR_JSON_ONLY_DATA_BEAN_IS_SERIALIZABLE；缺省 true（CoreConfigs:172-173）且全仓无覆盖；`PerKeyOrderedBuffer`（:32）无 @DataBean、无白名单、无 IJsonSerializable——**join×durable 默认配置每次序列化必抛**成立。两处在案先例核实（CepOperator:473-478 注释原文、FilePendingCommit:26-39 @DataBean 契约注释原文）；`rebuildViewFromKeyedState` 零生产调用核实；测试缺口核实（两 E2E 均内存活对象往返，TestE2EOverWindowWithCheckpoint:90-99 手工 instanceof 遍历——JSON 往返后恰是该 instanceof 静默跳过）。第二张面孔（关闭 only-data-bean → 无 getter 序列化退化 + instanceof 静默跳过）方向成立。
- **KR-8 → 保留 P3**。`isRestored` 全仓 grep 复现：主代码唯一消费者是写侧自身（CheckpointSerDe:107）；FunctionInitializationContext.isRestored 是无关的 UDF 面 API。无行为后果，P3 恰当，与 KR-2 不混叠的划界合理。
- **WIN-1 → 保留 P1（附缓解语境）**。merge ListState 分支（WindowOperator:1750-1790）核实：只并元素列表、source 仅 `newListWindowState.clear()`，全程不触碰 `elementTimestampsState`；对照同函数 fallback 分支 :1828 对 source 调 `clearWindowContents`（会清 ELEMENT_TIMESTAMPS_KEY，:1632-1634），证明 ListState 分支的遗漏非设计意图。emit 伪时间戳回落（:1053-1064）、驱逐回写污染（:1147-1153）、侧存储契约注释（:531-536）逐行核实；session assigner 确 extends MergingWindowAssigner，evictor 为公开 API（WindowedStream:88、WindowOperatorBuilder:71），session+evictor 用户可构造。缓解语境（复核补充）：`TimeEvictor` javadoc 自述"API 预留，当前未被使用"，DSL 未暴露 evictor，内建唯一组合（KeyedStreamImpl:193 countWindow→GlobalWindows+CountEvictor）不经 merge——维持 P1（公开 API 合法组合 + 静默错账 + 无界泄漏 + 破坏代码自证契约），若团队正式裁定 evictor 为预留面则降 P2 亦可辩护。
- **WIN-2 → 保留 P1**。状态分类核实（WINDOW_INTERNAL_TIMERS/trigger-accumulators/pane-tracking 均为 operator state，:626-656；窗口内容为 keyed state）；scale-down 4→2（M=128）逐例推演成立：(a) old s2/s3 的 operator state 无人认领（assembler :190-200 仅 taskIndex<oldParallelism 按同 index 取），其键组 [64,128) 的 timer 整体丢失；(b) old s1 存活为新 s1，但其键组 [32,64) 在新 P=2 下属主为 s0，触发时 `getWindowContents` 返回 null → :945-950 静默不 emit。scale-up 方向 `assertNoOperatorStateOnScaleUp`（assembler:135-145）显式 fail-fast，方向不对称与注释自述论证（"1:1 by index serves every kept subtask"）恰好只对非 keyed 耦合状态成立。P1 恰当。
- **WIN-3 → 保留 P2**。`computePaneInfo`（:1157-1182）仅用 `currentWatermark()` 判定，无 isEventTime 分派、无 processing-time 时钟；processing-time 作业 watermark 恒 MIN_VALUE → 恒 EARLY。仅影响 `ctx.getPaneInfo()` 读数、不丢数据，P2 恰当。
- **WIN-4 → 保留 P1**。状态分类核实（snapshotState :662-695：`cep-current-watermark` :666 无条件 put、`cep-key-class` 条件、per-key timer ledger :692 均 operator state；NFA `ValueState` / 事件队列 `MapState` / SharedBuffer 为 keyed）；scale-down 两支失败均成立：old 越界子任务 ledger 整体丢弃；存活 ledger 触发时对空 keyed 状态 drain 后 :1051-1060 `removeIf` 消费性销账（processWatermark :823-833 逐 key `setCurrentKey`→`onEventTime`）。scale-up 因 :666 无条件 put 必被 `assertNoOperatorStateOnScaleUp` 拦截，方向不对称成立。`rematerializeLedgerKey`（:763-784）与本条正交的划界核实无误。P1 恰当。

### 复核总裁定

12 条发现（KR-1..8、WIN-1..4）全部保留，无驳回、无降级。P1 七条（KR-1/2/3/7、WIN-1/2/4）全部通过深验证：代码路径、配置默认值、零消费论断、方向不对称性均在 live code 上逐行复现；两条存在机制表述微修（KR-2 的 1:1 restore 放置语义、KR-6 的文件路径与 javadoc 引文），均不影响结论与等级。KR-1/KR-2/KR-3 作为 R4 P0 缺陷族的三个残留入口、KR-7 作为 AR-13 同族第三例漏修、WIN-2/WIN-4 作为"per-key 辅助状态放 operator state"装配惯例的系统性缺口，构成最终收口清单。

## 最终保留项

| 编号 | 严重程度 | 裁决 | 一句话裁定 |
|---|---|---|---|
| KR-1 | P1 | 保留 | remote 部署恒供给 MemoryStateBackend(128) 且 M 不透传，生产 RPC 面无校验，路由/属主分裂链完整成立 |
| KR-2 | P1 | 保留（机制微修） | ownership 元数据 write-only 经全仓 grep 复现；1:1 restore 实为按新 M 重放置旧分区（非保留旧归置），静默不可达/缺失结论不变 |
| KR-3 | P1 | 保留 | M equality 校验全仓零命中；setStateBackend 顺序脆弱（静默）+ RocksDB 默认 M=1（首记录晚败）两入口真实 |
| KR-4 | P3 | 保留 | G38 契约头与 identity-hashCode 回落分支确有差额，触发面窄 |
| KR-5 | P3 | 保留 | null→channel 0 与 group 0 属主闭式巧合一致（逐例复验），公式链外残留入口 |
| KR-6 | P3 | 保留（引文微修） | HASH+null-partitioner 内建不可达经三构建器复现；实际包路径 transport/、javadoc 引文有出入但 doc-code 失配实质成立 |
| KR-7 | P1 | 保留 | durable 默认配置下 PerKeyOrderedBuffer 透传 JsonTool 必抛 only-data-bean，链路/门禁缺省/两先例/零 rebuild 调用方/测试缺口全部复现 |
| KR-8 | P3 | 保留 | `restored` 字段写+回填、零消费者经 grep 复现，无行为后果 |
| WIN-1 | P1 | 保留（附缓解语境） | ListState merge 分支不并/不清时间戳侧存储（对照 fallback 分支证明非设计意图），伪时间戳驱逐 + 无界泄漏成立；TimeEvictor 自述"API 预留"为缓解语境 |
| WIN-2 | P1 | 保留 | 4→2 逐例推演：越界子任务 timer 丢弃 + 存活 timer 跨属主触发 null 静默跳过，scale-up 有守卫方向不对称 |
| WIN-3 | P2 | 保留 | computePaneInfo 仅用 watermark，processing-time 恒 EARLY，用户可见语义缺陷不丢数据 |
| WIN-4 | P1 | 保留 | CEP per-key timer ledger 为 operator state，scale-down drain 空状态后消费性销账，scale-up 必然 fail-fast，方向不对称与 WIN-2 同型 |
