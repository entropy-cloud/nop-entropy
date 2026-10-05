# 深度审计 R5 报告 01：nop-stream-cep 引擎正确性 + nop-stream-flow XDSL StreamModel 编排正确性

- **审计日期**: 2026-10-05
- **HEAD SHA**: `bd089444f5c2b1fcd2c9a964ce909bac774a83d9`（2026-10-05 00:07:55 +0800）
- **范围**: `nop-stream/nop-stream-cep`（NFA/NFACompiler/SharedBuffer/CepOperator/Pattern DSL/model builder）、`nop-stream/nop-stream-flow`（StreamModelDslBuilder/AdvancedTransforms/StreamSchemaRegistry/xdef 契约面/Delta）、交叉边界（`nop-kernel/nop-xdefs` stream.xdef/pattern.xdef、`nop-stream-rocksdb` 序列化契约、`nop-stream-core` 关键 API 消费面）。
- **方法**: 只读审计。以 live code 为准逐文件通读 CEP 引擎核心（NFA 1153 行、CepOperator 1453 行、SharedBuffer/Accessor/Lockable/DeweyNumber 全量、NFACompiler 全量、Pattern/Quantifier 声明面）与 flow builder 全量（StreamModelDslBuilder 1110 行、AdvancedTransforms 949 行、StreamSchemaRegistry），xdef 契约逐属性对账 builder 消费点；对每个候选发现以 grep 全仓定位消费面/守卫面后判定。与 Flink CEP 上游实现逐算法比对（computeNextStates Dewey 预算、OutgoingEdges 分支计数、self-ignore 排除、start-state 重加、stop-state 丢弃、processMatchesAccordingToSkipStrategy 补偿序），区分「忠实移植」与「本地改动」，只审计本地改动与契约面。未读 ai-dev/audits、plans、bugs、lessons；未运行构建/测试。

---

## 发现总览

| 严重程度 | 数量 | 条目 |
|---|---|---|
| P0 | 0 | — |
| P1 | 1 | CEP-1 |
| P2 | 4 | CEP-2, CEP-3, FLOW-1, FLOW-2 |
| P3 | 4 | CEP-4, CEP-5, CEP-6, FLOW-3 |

---

### [CEP-1] CEP×RocksDB 已知不可用边界在声明面/注入面零守卫，RocksDB 后端静默忽略 CEP 状态描述符声明的 JavaStreamSerializer 序列化契约，且配置提示文本反向宣称 RocksDB 支持
- **文件**:
  - `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:470-487`（initStateAccess 给 NFA/SharedBuffer 描述符设置 JavaStreamSerializer）
  - `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/sharedbuffer/SharedBuffer.java:173-189`（MapStateDescriptor.setSerializer(JavaStreamSerializer.of())）
  - `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/RocksDBMapState.java:290-307`（readValue/serializeValue 只走 JSON，从不读 descriptor.getSerializer()）
  - `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/deploy/RemoteTaskDeploySupport.java:177`、`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/TaskCheckpointWiring.java:175`（向包括 CepOperator 在内的任意算子注入任意 IStateBackend，无类型守卫）
  - `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/NopCepConfigs.java:21-26`
- **证据片段**:
```java
// RocksDBMapState —— descriptor 上的 JavaStreamSerializer 从未被 consult：
private UV readValue(byte[] bytes) {
    if (ContainerValueCodec.isContainerType(descriptor.getValueType())) {
        return (UV) RocksDBValueSerDe.deserializeContainer(
                bytes, descriptor.getValueType(), "RocksDBMapState '" + descriptor.getName() + "'");
    }
    return RocksDBValueSerDe.deserialize(bytes, descriptor.getValueType());
}
private byte[] serializeValue(UV value) {
    return RocksDBValueSerDe.serialize(ContainerValueCodec.encode(value));
}
// NopCepConfigs（对用户的配置提示）：
String COMMON_HINT =
        "And it could accelerate the CEP operate process "
                + "speed and limit the capacity of cache in pure memory. Note: It's only effective to "
                + "limit usage of memory when 'state.backend' was set as 'rocksdb', which would "
                + "transport the elements exceeded the number of the cache into the rocksdb state "
                + "storage instead of memory state storage.";
```
- **严重程度**: P1
- **现状**: SharedBuffer 的两个 MapState（eventsBuffer/entries）与 CepOperator 的 NFAState ValueState 显式声明 `JavaStreamSerializer`（`Lockable` 持 `AtomicInteger` refCounter + 无默认构造，JSON 往返必坏——且 refCounter 无 public getter，序列化方向即丢锁计数）。Memory 后端（MemoryStateSerDe:746-753）兑现该契约；RocksDB 后端的全部 State 实现对 `descriptor.getSerializer()` 零 consult（rocksdb 模块 grep `getSerializer` 零命中），值一律走 `RocksDBValueSerDe.serialize`（JSON）。同时 runtime 侧 `RemoteTaskDeploySupport`/`TaskCheckpointWiring` 把 env 级配置的任意 backend 注入任意算子，`CepOperator.resolveKeyedBackend` 只对「未配置 backend」有 fallback+WARN，对「配置了 RocksDB」无任何 typed 拒绝。`NopCepConfigs.COMMON_HINT` 更是直接告诉用户 rocksdb 下缓存溢出元素会"transport into the rocksdb state storage"——与实际（JSON 序列化损坏/首次读即 typed 反序列化失败）相反。
- **风险**: 用户按提示文本配置 `state.backend=rocksdb` + CEP：写路径看似成功（JSON 化 Lockable，refCount 丢失/结构残缺），读路径在首个 `getEntry`/`materializeMatch` 即抛反序列化错误，或更坏——部分形状 JSON 化"成功"后 refcount 语义全毁（over-release StreamException / 静默泄漏）。故障发生在运行中段而非部署期，违背本仓库「非法组合部署前 typed fail-fast」的一贯裁定。该边界本身是已知历史结论，但当前没有任何一处代码或文档把守它——属于"边界已被破坏而声明面未把守"。
- **建议**: ① `CepOperator.resolveKeyedBackend`（或 `IStateBackend.createKeyedStateBackend` 消费点）对不支持 JavaStreamSerializer 契约的 backend 抛 typed `StreamException`（携带 descriptor 名），把已知不可用组合钉死为部署期错误；② 修正 `NopCepConfigs.COMMON_HINT` 文案，移除对 rocksdb 生效的错误承诺（或在实现兑现前显式声明 rocksdb 不支持 CEP）；③ 长期方案：让 RocksDB State 实现要么兑现 `TypeSerializer` 契约要么对设置了自定义 serializer 的 descriptor 显式 fail-fast。
- **信心水平**: 确定（serializer 契约被忽略、无守卫、文案失实均为代码事实）；具体故障形态（首读即错 vs 静默 refcount 损坏）很可能
- **误报排除**: 这不是"看起来不优雅"——它是跨模块序列化契约的单侧静默违反（声明方设置、消费方忽略），叠加 runtime 注入面无类型守卫与配置文案反向误导三个独立事实；且任务简报明确把"该边界已被破坏或声明面未把守"定义为发现。
- **复核状态**: 未复核

---

### [CEP-2] DSL `<cep>` 时间行为被钉死 EventTime 且构建期不校验上游 watermark 供给；比较器/迟到侧输出/处理时间均不可声明——无水印管线部署即进入"静默永不匹配 + 状态无界增长"
- **文件**:
  - `nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/AdvancedTransforms.java:746-784`（buildCep 只调 `CEP.pattern(keyed, pattern)`，默认 EventTime）
  - `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/PatternStreamBuilder.java:197-201`（forStreamAndPattern 默认 `TimeBehaviour.EventTime`；`inProcessingTime()/withComparator()/withLateDataOutputTag()` 为 API-only）
  - `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:867-879`（ET 分支：`timestamp > currentWatermark` 才 buffer；watermark 永为 MIN_VALUE 时事件全部入队，无排水驱动）
  - `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/stream.xdef:213`（`<cep patternRef="!string">` 无任何时间行为属性）
- **证据片段**:
```java
// PatternStreamBuilder —— DSL 不可达的三个维度：
PatternStreamBuilder<IN> withComparator(final EventComparator<IN> comparator) { ... }
PatternStreamBuilder<IN> withLateDataOutputTag(final OutputTag<IN> lateDataOutputTag) { ... }
PatternStreamBuilder<IN> inProcessingTime() { ... }
static <IN> PatternStreamBuilder<IN> forStreamAndPattern(
        final DataStream<IN> inputStream, final Pattern<IN, ?> pattern) {
    return new PatternStreamBuilder<>(
            inputStream, pattern, TimeBehaviour.EventTime, null, null);   // DSL 唯一入口
}
// AdvancedTransforms.buildCep —— 只有这一条通道：
PatternStream<T> patternStream = CEP.pattern((DataStream) keyed, (Pattern) pattern);
```
- **严重程度**: P2
- **现状**: watermark 只能由显式 `<timestampsAndWatermarks>` 或显式发射的 source 产生（core 无默认水位发射）。`<cep>` 构建点对「上游整条路径不存在 watermark 源」零校验：这样的声明可部署、可运行，但 ET 分支下 `currentWatermark()` 恒为 `Long.MIN_VALUE`，`onEventTime` 永不被 `processWatermark` 触发（ledger 永无 due），全部事件沉入 `elementQueueState` + SharedBuffer——匹配永不输出、状态无界增长、无任何 WARN。同时 Java API 的 `inProcessingTime()/withComparator()/withLateDataOutputTag()` 三个语义维度在 xdef/DSL 面完全不可表达（patternRef 是唯一参数），DSL 用户无法选择 PT 或提供迟到侧输出，也无法声明事件同秒排序比较器。
- **风险**: 「声明可部署但语义必死」的静默失败类：生产环境把 CEP 编排进无 watermark 的管线（例如处理时间语义的 CDC 流）时，故障表现为"没有告警产出 + 内存缓慢上涨"，定位成本高；且 PT 模式明明是引擎已实现并修复过 R5-CEP-01 的能力，DSL 面却选不了。
- **建议**: ① `buildCep` 做一次轻量图分析（沿上游边回溯是否存在 `StreamTimestampsAndWatermarksModel` 或已知发射 watermark 的 source bean 能力声明），缺失时 build 期 WARN 起步、 typed 拒绝为终态；② 给 stream.xdef `<cep>` 增加 `timeBehaviour="enum(EVENT_TIME|PROCESSING_TIME)"` 与可选 `lateDataSideOutputTag`/`comparatorBean` 属性并接线到 PatternStreamBuilder 已有通道。
- **信心水平**: 确定（行为与缺口为代码事实）
- **误报排除**: 不是把 Flink 通病当发现——差异点在：本仓库 flow 模块对其他同类"声明了但引擎做不到"的形态（sideOutput、HASH-into-union、triggerId）全部 build 期 fail-fast，唯独「CEP 需要上游 watermark」这一同等致命的前提没有任何守卫或文档化警告，属于该模块自身契约标准的执行遗漏。
- **复核状态**: 未复核

---

### [CEP-3] 处理时间模式下 wall-clock timer 注册无去重、无取消路径——window timer 按每个新起始部分匹配逐条注册，`deleteProcessingTimeTimer` 为 no-op，高匹配率 key 下 pending 定时器无界累积
- **文件**:
  - `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:523-542`（registerProcessingTimeTimer 每次新建回调+注册；deleteProcessingTimeTimer 空实现）
  - `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:882-899`（registerTimer：PT 分支墙钟 timer + ledger 双写）
  - `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:1246-1258`（processEvent：每个 `isNewStartPartialMatch` 事件注册 1+N 条 window timer）
  - `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/TaskProcessingTimeService.java:52`（`TreeMap<Long, List<TimerRegistration>>`——同刻重复注册以 List 追加，不合并）
- **证据片段**:
```java
// processEvent —— 每个新起始匹配都注册，TreeSet ledger 去重了台账但去重不了墙钟调度：
if (nfa.getWindowTime() > 0 && nfaState.isNewStartPartialMatch()) {
    registerTimer(timestamp + nfa.getWindowTime());
}
// createTimerService —— 每条注册一个独立回调闭包，delete 为 no-op：
@Override
public void registerProcessingTimeTimer(VoidNamespace namespace, long time) {
    Object registeringKey = currentRegistrationKey();
    getProcessingTimeService().registerTimer(time, t -> { ... onProcessingTime(t); ... });
}
@Override
public void deleteProcessingTimeTimer(VoidNamespace namespace, long time) {
}
```
- **严重程度**: P2
- **现状**: PT 模式（含 PT+comparator）下，`registerTimer` 的 ledger 侧由 `TreeSet` 去重，但墙钟侧每次调用都在 `TaskProcessingTimeService` 的 `TreeMap<Long, List<TimerRegistration>>` 中追加一条独立注册（同刻也不合并），且没有任何取消/删除路径（`deleteProcessingTimeTimer` 空实现；回调 fire 前一直驻留）。ET 模式无此问题（排水由 watermark 驱动，`registerEventTimeTimer` 只写 ledger）。
- **风险**: 量化：窗口 W、某 key 每秒产生 S 个新起始匹配，则稳态 pending 注册数 ≈ S×W（如 S=100/s、W=1h → 36 万条闭包 + TreeMap 条目；每条含捕获 key 的 lambda + ScheduledFuture，百字节级 → 数十 MB/ key，且每条 fire 都跑一遍全量 drain，CPU 随注册数而非去重后时间点数增长）。与 Flink 的 keying state timer service（per key/namespace/timestamp 去重）存在结构性差距。
- **建议**: PT 分支的墙钟注册复用 ledger 的 `TreeSet.add` 返回值做去重（新 time 才注册墙钟），或给 `InternalTimerService` 增加按 (key,time) 去重的注册语义；fire 后的回调（当前即弃）与 `close()` 均无需改动。
- **信心水平**: 确定（无去重/无取消为代码事实）；内存量级风险取决于负载形态（很可能）
- **误报排除**: 不是重复报告 ledger 相关历史缺陷——R5-CEP-01 修的是「ledger 可见性」，本条是独立的「墙钟调度面无去重」：ledger 去重正确并不能阻止 TreeMap 侧按注册次数线性累积，两套结构行为不同步正是问题本身。
- **复核状态**: 未复核

---

### [CEP-4] NFA `LazyVerdictCache` 按条件实例身份缓存单事件判定——对有状态/非确定性的 `IterativeCondition` 改变可观察求值次数，且该缓存无专项回归钉子
- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/NFA.java:712-731, 1016-1052`
- **证据片段**:
```java
// Conditions are deterministic per (event, context), so the per-call
// verdict cache is semantics-preserving: ... (代码自述的前提)
final LazyVerdictCache<T> conditionVerdicts = new LazyVerdictCache<>();
...
private boolean checkFilterCondition(
        ConditionContext context,
        IterativeCondition<T> condition,
        T event,
        LazyVerdictCache<T> conditionVerdicts) throws Exception {
    if (condition == null) {
        return true;
    }
    Boolean cached = conditionVerdicts.get(condition);
    if (cached != null) {
        return cached;   // 同一条件实例在同一次 computeNextStates 内只求值一次
    }
```
- **严重程度**: P3
- **现状**: `createDecisionGraph` 沿 PROCEED 链行走会对同一 `IterativeCondition` 实例多次到达（例如同一条件同时挂在 TAKE 与经 PROCEED 可达的边、以及 `findFinalStateAfterProceed` 复走）。Flink 上游每次到达都重新调用 `condition.filter(event, ctx)`；本地改动引入按身份的每调用缓存，把求值次数折叠为一次。条件契约本身允许有状态（`RichIterativeCondition` 有 open/close 生命周期，NFA.open 会逐一 open），纯函数前提只是代码注释里的自述约定，无任何声明面强制。
- **风险**: 有状态条件（计数、采样、随机丢弃、依赖 open 参数的缓存）在此 NFA 上与上游 Flink 语义分叉：同事件第二次"应该"看到的求值被吞掉；`ctx.getEventsForPattern` 有副作用的实现同样被折叠。常规纯函数条件无影响——故仅 P3。`grep` 测试目录未见以该缓存为对象的专项测试（其正确性目前只被通用 NFA 测试间接覆盖）。
- **建议**: 在 `IterativeCondition` 的 javadoc/声明面把「同事件同上下文内 filter 必须纯函数且幂等」升格为显式契约（fail-fast 不可行，但至少文档化 + 在测试指南中给出反例），或为 `LazyVerdictCache` 补一条"同实例多转移只求值一次"的行为钉子测试，把优化前提固化。
- **信心水平**: 确定（缓存存在且折叠求值为代码事实）；实际踩中有状态条件的概率低
- **误报排除**: 不是性能优化一律报——问题在于该优化以一条**未在任何契约面声明**的前提（条件确定性）为正确性支柱，而同一 API 面恰好在别处（open/close、RuntimeContext 注入）鼓励有状态条件，前提与契约面互相矛盾。
- **复核状态**: 未复核

---

### [CEP-5] pattern 模型 part 级 `windowTime` 在未声明 oneOrMore/times/timesOrMore 时被静默丢弃——声明面消费不完整，违反本模块自身 no-silent-drop 规则
- **文件**:
  - `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/model/builder/CepPatternBuilder.java:166-204`（windowTime 只在三个数值器分支内被读取）
  - `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/pattern.xdef`（`CepPatternPartModel` define 将 `windowTime="duration"` 声明为任意 part 皆可写）
- **证据片段**:
```java
private Pattern addQualifier(Pattern pattern, CepPatternPartModel partModel) {
    ...
    if (partModel.isOneOrMore()) {
        pattern = pattern.oneOrMore(partModel.getWindowTime());   // 唯一消费点 1
    }
    if (partModel.getTimes() != null) {
        pattern = pattern.times(..., partModel.getWindowTime());  // 消费点 2
    }
    if (partModel.getTimesOrMore() != null) {
        pattern = pattern.timesOrMore(partModel.getTimesOrMore(), partModel.getWindowTime()); // 消费点 3
    }
    ... // 无任何分支校验「声明了 windowTime 但三个数值器全空」
```
- **严重程度**: P3
- **现状**: `<single name="a" windowTime="5s" optional="true"/>`（或任何不带数值器的 part）通过 xdef 校验、通过 builder、编译为 NFA 时该窗口被静默丢弃——用户以为声明了 per-iteration 超时，实际什么都没发生。同文件对 `until` 有"仅适用 oneOrMore"的注释，但对 windowTime 的静默丢弃没有任何处理。
- **风险**: 声明面语义静默降级；与 flow 侧 FL-1/P1-XDSL-6「声明即消费、消费不了即 fail-fast」的既定标准不一致。触发面窄（需要用户误用 part 级 windowTime 且不带数值器），故 P3。
- **建议**: `CepPatternBuilder.addQualifier` 增加终检：`windowTime != null && 三个数值器全空` → typed `MalformedPatternException`（锚定 part 名与位置），与 `until` 的量化器前置校验同款。
- **信心水平**: 确定
- **误报排除**: 不是吹毛求疵——同一 builder 对 `afterMatchSkipStrategy` 的每个枚举值、`subType` 加载失败、`next` 成环都做了 typed 报错，说明"声明即必须消费"是该 builder 自己执行的标准，此为漏网的一个属性。
- **复核状态**: 未复核

---

### [CEP-6] `SKIP_TO_FIRST`/`SKIP_TO_LAST` 未声明 `afterMatchSkipTo` 时抛裸 `IllegalArgumentException`（Guard）——错误分层与定位双双缺席
- **文件**:
  - `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/model/builder/CepPatternBuilder.java:224-230`
  - `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/aftermatch/SkipToElementStrategy.java:34-37`
  - `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/util/Guard.java:82-86`
- **证据片段**:
```java
// SkipToElementStrategy 构造器：
SkipToElementStrategy(String patternName, boolean shouldThrowException) {
    this.patternName = notNull(patternName, "patternName");   // → IllegalArgumentException("IsNull:patternName")
    ...
}
// CepPatternBuilder —— afterMatchSkipTo 为 null 时直接透传：
case SKIP_TO_FIRST: {
    strategy = AfterMatchSkipStrategy.skipToFirst(partModel.getAfterMatchSkipTo());
    break;
}
```
- **严重程度**: P3
- **现状**: pattern.xdef 注释声明 SKIP_TO_FIRST/LAST 需要设置 `afterMatchSkipTo`，但 xdef 与 builder 均未强制；缺失时失败发生在 `Guard.notNull` → 裸 `IllegalArgumentException("IsNull:patternName")`，无错误码、无 part 名、无 source 位置——而同一 builder 对成环（`ERR_CEP_PATTERN_PART_NOT_ALLOW_LOOP` + `.source()`）、subtype 加载失败（`MalformedPatternException`）都有 typed 定位错误。仓库已裁定同类形态归 `MalformedPatternException`（Pattern.java CEP-08 注释：times(<=0) 从 Guard 改 Malformed 正是为了这个分层），此路径是漏改。
- **风险**: 用户从堆栈只能看到 "IsNull:patternName"，无法定位到哪个 pattern part 缺参；错误监控按错误码聚合时落入未分类桶。
- **建议**: `getAfterMatchSkipStrategy` 内先判 `getAfterMatchSkipTo() == null` → `MalformedPatternException(ERR_CEP_MALFORMED_PATTERN).param(ARG_PATTERN_DETAIL, ...).source(partModel)`；同步在 pattern.xdef 或 builder 为 SKIP_TO_FIRST/LAST 补充声明面提示。
- **信心水平**: 确定
- **误报排除**: 不是"裸异常也 fail-fast 了就没事"——同文件近邻代码（CEP-08 系列）已经把完全相同的形态（times 非法值）从 Guard 迁移到 typed 异常，说明仓库裁定标准明确把这类列为应修缺陷，此为同类未迁移点。
- **复核状态**: 未复核

---

### [FLOW-1] stream.xdef 声明面存在一族「解析进模型但无任何消费者、也无 fail-fast」的属性：transform 级 `name`（`<source>` 除外）、`<edge><name>` 子元素、`<schema><field>` 的 `nullable`/`defaultValue`
- **文件**:
  - `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/stream.xdef:144`（共享 define `name="string"`）、`:252`（edge `<name>string</name>`）、`:94`（field `nullable="boolean" defaultValue="any"`）
  - `nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:805`（`t.getName()` 全 builder 唯一消费点，仅 source）
  - `nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/model/_gen/_StreamEdgeModel.java:171-176`（`getName()/setName` 生成后零调用）
  - `nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamSchemaRegistry.java:44-72, 131-132`（nullable/defaultValue 装入 FieldSpec 后无任何读取方）
- **证据片段**:
```java
// StreamModelDslBuilder —— name 只在 source 分支被消费：
private <T> DataStream<T> buildSource(StreamExecutionEnvironment env, StreamSourceModel t) {
    ...
    String name = t.getName() == null ? "Source:" + t.getId() : t.getName();
    return applyDeclaredParallelism((DataStream<T>) env.addSource(fn, name), t);
}
// buildMap/buildFilter/buildFlatMap/buildKeyBy/... 全部不读 t.getName()
// StreamSchemaRegistry.FieldSpec —— 携带但无人消费：
public Boolean getNullable() { return nullable; }
public Object getDefaultValue() { return defaultValue; }
// （全仓 grep：getNullable/getDefaultValue 在 flow/sql 生产代码零调用）
```
- **严重程度**: P2
- **现状**: fraud 示例大量声明 `name="ParseTransactionLines"` 等（`fraud-s2-file.stream.xml:28,31,39,42`、delta `fraud-s2-file-delta.stream.xml` 的 `name="BlacklistFilter"`），除 source 外全部静默无效：算子在 plan/metrics/日志中的名字仍是 `Source:xxx`/`map` 式默认名。`<edge><name>` 与 schema 字段 `nullable`/`defaultValue` 同样零消费（columnTypeLookup 只用 name+type）。这与 StreamModelDslBuilder 类头自述的 Anti-Hollow 契约（"Every xdef-declared ... either implemented or fails fast"）以及 FL-1/P1-XDSL-5/6 系列对 edge 属性执行的"消费或拒绝"标准不一致——edge 的 partition/keyExpr/flowControl 四属性逐条 fail-fast，而 name 类属性静默吞掉。
- **风险**: 用户按 xdef 合法声明却拿不到语义（operator name 影响指标标签、`stableKey`/fingerprint 可读性、日志定位；defaultValue 影响聚合列缺省值预期），缺陷只在读运维输出时才暴露；声明面信任度受损。
- **建议**: 三选一并保持一致：① 让 builder 全 transform 通用地消费 `name`（默认回退 `elementDesc(t)`）传给 map/filter 等的 operator 命名通道；② xdef 收紧（删除不可消费属性）；③ 沿用 FL-1 模式对无消费者属性 fail-fast。schema 的 nullable/defaultValue 至少要么在 `StreamSchemaRegistry.resolveSchemas` 做 fail-fast，要么在文档标注"声明保留、当前无语义"。
- **信心水平**: 确定
- **误报排除**: `<description>` 属文档性元数据不在此列；本条限定为**语义性**属性（operator 命名、字段缺省/可空语义）且生产代码零消费经全仓 grep 双向验证（消费面 grep + 生成类 getter 引用 grep）。
- **复核状态**: 未复核

---

### [FLOW-2] 构建期无 sink-可达性校验：与全部 sink 断连的已声明变换被静默排除出编译图——"少声明一条边"的管线照常部署、照常运行、零输出
- **文件**:
  - `nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:434-453`（buildTransforms 只做拓扑排序与逐点装配，无可达性检查）
  - `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/environment/StreamExecutionEnvironment.java:424-445`（execute 只 fail-fast「一个 sink 都没有」，编译仅取 sink-可达子图）
- **证据片段**:
```java
// StreamExecutionEnvironment —— 只有全空 sink 才报错；可达子图外的声明静默消失：
private List<SinkTransformation<?>> requireSinkTransformations() {
    List<SinkTransformation<?>> sinks = findSinkTransformations();
    if (sinks.isEmpty()) {
        throw new StreamException(ERR_STREAM_INVALID_STATE).param(ARG_DETAIL, "No sinks found in the streaming job");
    }
    return sinks;
}
/// compilePlans javadoc：
 * Compiles the sink-reachable transformation DAG into the executable JobGraph.
```
- **严重程度**: P2
- **现状**: builder 在 `validateDag` 已持有完整 id 图与边表（端点存在性、重复 id、环全部校验），唯独不校验「每个声明的 transform 都能到达某个 `<sink>`」。用户漏写 `tx-agg → file-sink` 这类一条边时：sink 仍在（别的上游或直接挂在更上游），图编译成功，被断连的子树（及其全部计算声明）从 JobGraph 中静默蒸发，作业以"成功运行、永远没有输出"收场，无 WARN。
- **风险**: 声明面最危险的静默失败类——数据完整性事故（以为在聚合入库，实际什么都没进）而非报错；conf-validate 层 2 同样不报（复用同一 builder）。
- **建议**: `validateDag` 末尾加一遍反向可达性检查（从 sink 集合沿边回溯），未覆盖的 transform id 以 typed `ERR_STREAM_*` + `loc` 列出（"transform 'x' is not reachable from any sink"），与环检测的 unprocessed-ids 报错同款形态。
- **信心水平**: 确定
- **误报排除**: 不是把 core 的「DAG 编译只取 sink 可达子图」这一 Flink 同款设计当缺陷——Flink 同样如此；缺陷在于 flow 的**声明式**面在已拥有完整模型图的前提下放弃了部署期拦截机会，与模块自述 Anti-Hollow 承诺直接冲突（Java API 用户"没 sink 到的变换"是代码可见的死代码，DSL 用户的断连是纯声明拼写事故）。
- **复核状态**: 未复核

---

### [FLOW-3] `x:extends`/`_delta` 定制语义经核查保持（fraud delta 正确），但 delta 只能复用 builder 层 fail-fast，无 delta 专属的"合并后指纹/可达性"再校验缺失项——低优先级记录
- **文件**:
  - `nop-stream/nop-stream-fraud-example/src/main/resources/_vfs/nop/stream/demo/fraud-s2-file-delta.stream.xml:12-27`（e2 重接 + blacklist-filter 注入）
  - `ai-dev/design/nop-stream/stream-dsl-design.md:204-219`（keyed-list 合并规则与 fingerprint 敏感性契约）
- **证据片段**:
```xml
<edge id="e2" from="tx-watermarks" to="blacklist-filter" partition="FORWARD"/>
<edge id="e6" from="blacklist-filter" to="kb-user" partition="FORWARD"/>
```
- **严重程度**: P3
- **现状**: 同 id 边 e2 整体覆盖 base 的 from/to，graph 语义保持（wm→filter→kb-user）；transform id 集合变化使 fingerprint 敏感（by-design）。合并后模型走完整 `validateDag`（delta 不绕过 fail-fast 已由结构保证）。记录性发现：合并语义本身未发现缺陷，但 FLOW-2 的可达性缺口在 delta 场景被放大——delta 改接边时漏写一条边即静默断连，且 diff 审查更难发现。
- **风险**: 见 FLOW-2；delta 组合使其触发面变宽。
- **建议**: 随 FLOW-2 一并解决（合并后模型统一做可达性校验即可覆盖 delta）；无需 delta 专属机制。
- **信心水平**: 确定（delta 语义保持为代码/示例事实）
- **误报排除**: 本条不是把"delta 有测试覆盖"当缺陷，而是确认简报要求的核查点（覆盖后图语义保持）通过，并记录其与 FLOW-2 的耦合放大关系。
- **复核状态**: 未复核

---

## 已验证为正确的重点面（负面清单，防止后续轮次重复排查）

1. **PT 分支 bucket 排水驱动源（R5-CEP-01 修复态）**: `registerTimer`（CepOperator.java:882-899）PT 分支同时写墙钟 timer 与 ledger；`processWatermark` 的 per-key ET 排水被 `if (!isProcessingTime)`（:795）正确隔离；`drainDueBuckets` 的 ledger 超集不变式由「bucket 首事件注册 + window timer 注册 + reconcile-once 回填」三路共同维持，`onProcessingTime` STEP 6（:1088-1097）与 `onEventTime`（:1052-1061）对称清理。历史缺陷无回归迹象。
2. **NFA 转移核心**: Dewey 版本预算（takeBranchesToVisit/ignoreBranchesToVisit/totalTakeToSkip）、OutgoingEdges 分支计数与 self-ignore 排除、start-state 无条件重加（保证"size==1 必为 start"）、stop-state 丢弃路径的 null 守卫、completedMatches 按 startEventID 排序的补偿序——与 Flink 上游逐算法一致，本地注释文档化了不变式。
3. **SharedBuffer/Lockable**: over-release 双路径（`release`/`releaseOrDetach`）均 fail-fast（StreamRuntimeException）；`releaseNode` 的空节点 pop-lockstep 修复在位；scoped accessor（`getAccessor(getCurrentKey())`）的 cache key 隔离 + write-through + close-no-op 契约自洽；`registerEvent` 的 EventId 冲突环 + 双溢出守卫（:324-339）闭环。
4. **checkpoint 三态原子性**: watermark/key-class/timer-ledger 以 operator state 与 keyed state 同 checkpoint 单元持久化；`restoreState` 先于 `open()` 的时序由 resolveEffectiveKeyClass 消费链钉死；键类三通道（显式 Byte.class / checkpoint 携带 / 首 key 捕获）与 AR-10/AR-11 裁定一致。
5. **Pattern DSL 声明面**: times/from-to/timesOrMore 的非法值全部 typed MalformedPatternException（CEP-08 系列）；`within` 非正值、inner window 非正值、notNext/notFollowedBy-after-optional、贪婪后追加 optional、名字含 `:` 均构建期拒绝；编译产物（createTimesState/createLooping/createSingletonState/greedy-until 副本）与 Flink 一致且带 CEP-02 null-target 守卫。
6. **flow builder fail-fast 矩阵**: 缺 id/重复 id/未知边端点/环（含 unprocessed-ids 列表）/edge 声明矩阵（HASH+keyExpr、HASH-into-keyBy/union/join、REBALANCE/BROADCAST、四 flow-control 属性）/join 八查/source-sink-custom 的 params/maxParallelism/type/consistencyCapability/window 的 virtual-parallelism、triggerId、accumulationMode 全部 typed + `loc` 锚定；`<sql>` 唯一内容约束与双 provider 缺失 fail-fast 在位。

## 检查范围

**设计/文档**（先读）: AGENTS.md、`docs-for-ai/03-modules/nop-stream.md`、`ai-dev/design/nop-stream/cep-design.md`、`ai-dev/design/nop-stream/stream-dsl-design.md`、`ai-dev/design/nop-stream/state-management-design.md`（CEP 相关节检索）。

**CEP（全部通读）**: `nfa/NFA.java`、`nfa/NFAState.java`、`nfa/ComputationState.java`、`nfa/DeweyNumber.java`、`nfa/State.java`、`nfa/compiler/NFACompiler.java`、`nfa/compiler/NFAStateNameHandler.java`（关键节）、`nfa/sharedbuffer/{SharedBuffer,SharedBufferAccessor,Lockable}.java`、`operator/CepOperator.java`、`pattern/Pattern.java`（主要部分）、`CEP.java`、`PatternStream.java`、`PatternStreamBuilder.java`、`NopCepConfigs.java`、`model/builder/{CepPatternBuilder,EvalFunctionCondition}.java`、`nfa/aftermatch/{AfterMatchSkipStrategy,SkipToElementStrategy,SkipToFirstStrategy,SkipRelativeToWholeMatchStrategy}.java`。

**flow（全部通读）**: `builder/StreamModelDslBuilder.java`、`builder/AdvancedTransforms.java`、`builder/StreamSchemaRegistry.java`、`builder/functions/XplSourceFunction.java`（取消语义抽查）、`validate/StreamConfValidator.java`（结构概览）、`model/_gen/_StreamEdgeModel.java`（name getter 核查）。

**契约/交叉**: `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/{stream.xdef,pattern.xdef}`（全文）、`nop-stream-rocksdb/.../RocksDBMapState.java`（全文）+ rocksdb 模块 serializer 契约 grep、`nop-stream-core/.../{StreamExecutionEnvironment(执行/编译节),DataStream(union/命名),KeyedStream(setParallelism),ProcessingTimeService,TaskProcessingTimeService,MemoryStateSerDe(735-775)}`、`nop-stream-runtime/.../{RemoteTaskDeploySupport:177,TaskCheckpointWiring:175}`、`nop-stream-fraud-example/.../fraud-s2-file{,-delta}.stream.xml`（全文）。

**检索手段**: 全仓 grep（serializer 消费面、getName/getNullable/getDefaultValue 消费面、forEachEventTimeTimer 调用面、setStateBackend 注入面、cep×rocksdb 守卫面）；只读 git（rev-parse/log）。未运行任何构建/测试，未修改任何产品代码。

---

## 深挖第 2 轮追加

- **轮次日期**: 2026-10-05（同 HEAD `bd089444`）
- **深挖范围**: 第 1 轮发现暴露的注入面/时间面/Anti-Hollow 面的延伸；SharedBuffer Dewey 溢出与 EventId 守卫复核、NFAState/ComputationState 相等语义消费面、afterMatchSkip 家族 prune 对 start-state(null EventId) 的处理、CepOperator×keyBy/sideOutput 组合、flow 并行度声明传播、stream.xdef/pattern.xdef 全属性 vs builder 消费点系统对照、RocksDB 后端类型支持面穷尽（`setSerializer` 全仓仅 CEP 三处声明、rocksdb 后端支持 aggregating/reducing state，CEP-1 之外无新的 serializer 契约消费方）。
- **本轮结论**: 新增 3 条（P1×1、P2×2）。以下第 1 轮负面清单之外的面经核查**无高价值问题**：DeweyNumber.increase 带 `StreamException` 溢出守卫、addStage 仅增长数组长度（无回绕）；ComputationState equals/hashCode 六字段完备且与 Flink 一致；NFAState.equals/hashCode 为测试专用 oracle（生产代码零消费，其排序比较器忽略 buffer-entry 身份属弱 oracle，不构成产品缺陷，不单列）；AfterMatchSkipStrategy.prune 对 `startEventID == null`（start state）显式跳过，`resetNfaStateIfFullyTimedOut` 的不变式注释成立；Quantifier 的 consecutive/allowCombinations/greedy/optional 非法组合全部走 `checkPattern` → typed `MalformedPatternException`；xdef 全属性对照中 window/aggregate/reduce/process/union/custom/timestampsAndWatermarks/checkpoint/aggregators/joins 的其余属性均已消费或 fail-fast（triggerId/accumulationMode/parallelism-on-window/params/custom-source 等）。

---

### [FLOW-5] keyed 阶段的并行度声明传播无守卫，与 FORWARD 路由「恒发 channel 0」及 rescale 的 KeyGroup 重分布互相矛盾——声明的 `keyBy(P)→cep/window/join(Q≠P)` 照常编译部署，稳态下并行度结构性失效，rescale 恢复后 keyed state 与流量永久分离（静默丢匹配）
- **文件**:
  - `nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:1068-1073`（`applyDeclaredParallelism` 对任意 transform 生效，含 keyed 消费者）、`:566-615`（`validateEdgeDeclarations` 校验 partition/keyExpr/flowControl，唯独无任何 FORWARD 边两端的并行度一致性检查）、`:1035-1057`（`applyEdgePartition` javadoc 自认「FORWARD's modulo semantics would concentrate all traffic on target subtask 0 (a structural false-green)」，其 Item-29 修复只对齐隐式 HASH 分区点的声明值，不触及路由行为）
  - `nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/AdvancedTransforms.java:781-783`（buildCep 末尾 `applyDeclaredParallelism`）、`:900`（buildKeyBy 对 keyBy 顶点应用自身 parallelism）
  - `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/ForwardPartitionRouter.java:29-34`（`selectChannel` 无条件 `return 0`，但同类头注释宣称「maps source subtask index into the target partition space via modulo」）、`ForwardPartitioner.java:36-39`（`partition` 恒 0）；`TestPartitionRouter.testForwardPartitionRouter_alwaysReturns0`（测试钉死恒 0 为「预期」）
  - `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/GraphExecutionPlan.java:69`（类 javadoc 宣称「FORWARD: 1:1 mapping, source subtask i → target subtask i (modulo target parallelism)」——与实现直接矛盾）
  - `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/jobgraph/JobGraphGenerator.java:343-`（`canChain` 准则 1：并行度不等即断链、走 ResultPartition 路由路径）
  - `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java:1354-1356`（rescale 恢复按 `computeKeyGroupRangeForSubtaskIndex` 把 keyed state 按 stableHash key-group 重分布到全部新 subtask）
- **证据片段**:
```java
// ForwardPartitionRouter —— 实现恒 0，头注释宣称 modulo：
// When parallelism differs, maps source subtask index into the target partition space via modulo.
@Override
public int selectChannel(StreamRecord<?> record) {
    return 0;   // 测试 testForwardPartitionRouter_alwaysReturns0 钉死
}
// GraphExecutionPlan javadoc —— 与上直接矛盾：
//  <li>FORWARD: 1:1 mapping, source subtask i → target subtask i (modulo target parallelism)</li>
// StreamModelDslBuilder —— builder 对 keyed 消费者声明并行度零守卫：
static <S extends DataStream<?>> S applyDeclaredParallelism(S stream, StreamTransformModel t) {
    if (t.getParallelism() != null && t.getParallelism() > 0) { stream.setParallelism(t.getParallelism()); }
    return stream;
}
// rescale 恢复按 key-group 重分布（GraphModelCheckpointExecutor:1354）：
KeyGroupRange newRange = KeyGroupAssignment.computeKeyGroupRangeForSubtaskIndex(
        maxParallelism, newParallelism, taskIndex);
taskState = buildRescaledTaskState(vertexId, taskIndex, newRange, ...);
```
- **严重程度**: P1
- **现状**: 三层事实叠加。① builder 层：`<cep>`/`<window>` 后继的 aggregate/reduce/process/join 均接受任意 `parallelism` 声明；显式 `<keyBy parallelism="P">` 与其 keyed 下游之间的边只能是 FORWARD（HASH-into-keyBy 被 `ERR_STREAM_EDGE_HASH_REDUNDANT` 拒绝、REBALANCE/BROADCAST 被拒绝），builder 对「FORWARD 边两端并行度不等」无任何校验。② 运行层：并行度不等 ⇒ `canChain` 断链 ⇒ 走 ResultPartition+Router 路径 ⇒ `ForwardPartitionRouter` 把每个上游 subtask 的全部记录发往该行 channel 0 ⇒ 下游 subtask 1..Q-1 永久空转——声明并行度纯装饰（同类注释自称 structural false-green；且与 GraphExecutionPlan javadoc 的 i→i modulo 契约直接矛盾）。③ 恢复层：kill/同并行度恢复走 1:1 shard 查找、无 key-group 过滤（`KeyGroupRangeRestoreFilter` 仅测试引用），state 仍全在 subtask 0、与恒-0 路由意外自洽；但 **rescale 恢复**按 stableHash key-group 把 keyed state（CEP 的 nfaState/eventQueues/SharedBuffer、window state）重分布到全部 Q 个 subtask，而新流量依旧只到 subtask 0——key-group 归属非 0 号 subtask 的 key 永远等不到新事件，其 NFA 部分匹配只被 watermark 广播触发的 timeout 排空后从此静默，0 号 subtask 则从零重建这些 key 的状态：匹配丢失/重复，无任何报错。
- **风险**: 与 FLOW-2 同类「声明可部署、静默错误」但后果更重：数据正确性（匹配结果丢失/分裂）而非仅无输出。触发面为「受支持且各层全放行」的声明组合：任意 keyed 消费者声明与上游 keyBy 顶点不同的并行度 + 一次 rescale 恢复；即使不恢复，声明的并行度扩展也是假的（全部负载压在 subtask 0，吞吐扩容无效且无告警）。Item-29 的既有修复（对齐隐式分区点并行度）证明该问题域已被认知，但显式 `<keyBy>` 变换路径与 rescale 路径未被同一标准覆盖。
- **建议**: ① `validateEdgeDeclarations` 增加一条：FORWARD 边两端顶点的有效并行度（声明值>环境值）不等即 typed 拒绝（或强制退化声明方为上游值），错误锚定边 id 与两端并行度——把「隐式 HASH 边 Item-29 对齐」推广为显式边的通用约束；② 若要支持不等并行度，须让 ForwardPartitionRouter 真正实现其注释承诺（`sourceSubtaskIndex % numPartitions`）或对 keyed 顶点间的 FORWARD 边改用 key-group 路由，并同步修正 GraphExecutionPlan:69 的 javadoc 与测试名所钉死的行为；③ rescale 恢复路径在 `buildRescaledTaskState` 之外，应校验目标顶点的实际入流路由策略与 key-group 归属一致，不一致时 typed 拒绝 rescale（比静默分裂好）。
- **信心水平**: 确定（三层行为——builder 无守卫、router 恒 0 且被测试钉死、rescale 按 key-group 重分布——均为代码事实且相互咬合成完整故障链）；具体生产踩中概率取决于 keyed 阶段不等并行度声明 + rescale 的组合使用频率（很可能，DSL 未提示任何限制）
- **误报排除**: 不是重复 FLOW-2（断连子图被静默排除）——本条管线完整在图内、每条记录都被处理，坏在「发到哪个 subtask」；也不是把 Flink 通病当发现——Flink 的 FORWARD 在并行度不等时是部署期校验错误（`forward` 要求上游并行度等于下游或显式 rescale），而本仓库三层全放行；Item-29 javadoc 的自认文字（concentrate all traffic on target subtask 0）是仓库自身承认该行为存在的直接证据，本条指出的是其修复不完整且与 rescale 语义正面冲突。
- **复核状态**: 未复核

---

### [CEP-7] 处理时间模式的墙钟 timer 在 checkpoint 恢复后不重建：ledger 恢复了、调度丢了，且 `forEachProcessingTimeTimer` 恒空——静默 key 的窗口超时永不触发，in-flight 匹配丢失、SharedBuffer/elementQueue 泄漏
- **文件**:
  - `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:510-567`（`createTimerService`：PT 注册走 `getProcessingTimeService().registerTimer` 纯内存回调；`:563-565` `forEachProcessingTimeTimer` 空实现——恢复侧即使想重挂也无计时器可枚举）
  - `CepOperator.java:663-695`（`snapshotState` 只持久化 watermark/key-class/timer-ledger 三个 operator state，不持久化任何墙钟调度）、`:697-751`（`restoreState` 恢复 ledger 内容）、`:389-426`（`open` 序列 resolveKeyedBackend→initStateAccess→initTimerLedger→createTimerService→initNfa，无任何「按恢复的 ledger 重新挂墙钟」步骤）
  - `CepOperator.java:790-838`（`processWatermark` 的 ledger 驱动排水被 `if (!isProcessingTime)` 限定在 ET——PT 模式无 ledger 自愈通道）
  - 对照组：`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java:676-684, 254, 573-574`（同仓库窗口算子恢复时捕获 `HeapInternalTimerService.TimerSnapshot` 并在 `open()` 延迟重挂——正确做法在本仓库已有先例）
- **证据片段**:
```java
// 恢复侧没有任何重挂入口：
@Override
public void forEachProcessingTimeTimer(BiConsumer<VoidNamespace, Long> consumer) {
}   // 恒空：恢复逻辑无法枚举应重挂的 PT 计时器
// PT 的真实调度只在 processElement→registerTimer 时新建：
internalTimerService.registerProcessingTimeTimer(VoidNamespace.INSTANCE, timestamp + 1);
// getProcessingTimeService().registerTimer(time, t -> {...onProcessingTime(t)...}); —— 纯内存 ScheduledFuture，不随 checkpoint 存活
// 而 ET 模式的恢复自愈通道在 PT 被显式关死：
if (newWatermark > currentWatermark) { ... if (!isProcessingTime) { /* ledger 驱动 per-key 排水 */ } }
```
- **严重程度**: P2
- **现状**: ledger（`cep-event-time-timers` operator state）在 PT 模式下也被双写（R5-CEP-01 修复）并随 checkpoint 持久化，恢复后 `drainDueBuckets(dueOnly=false)` 确实能读到这些时间戳——但触发器没了：PT 模式唯一的排水驱动是墙钟回调，恢复后没有任何回调被重挂（`open()` 不做、`forEachProcessingTimeTimer` 恒空、`processWatermark` 排水被 `!isProcessingTime` 关死）。某 key 恢复后若再有事件，`registerTimer` 新挂的回调 fire 时会把该 key 的 ledger 全量排水（迟到的自愈）；若该 key 从此静默（恰恰是窗口超时语义最重要的场景——欺诈检测中用户停止交易），其 pending 窗口计时器永不 fire：应发的超时匹配不发、`elementQueueState` bucket 与 SharedBuffer 条目（refCount>0）对泄漏，且这些死条目随每次 snapshotState 全量持久化，checkpoint 无限背债。
- **风险**: 每一次例行重启/故障恢复（恢复是生产常态）都让 PT 模式 CEP 进入「老计时器失联」状态：超时语义延迟到该 key 下一个事件、或永不；表现为「恢复后部分告警不产出 + 状态缓慢上涨」，与 CEP-2/CEP-3 同族但独立（那两条是首次运行的调度行为，本条是恢复面的调度重建缺口）。同仓库 WindowOperator 已用 TimerSnapshot 机制正确解决同构问题，CEP 未跟进。
- **建议**: ① 在 `open()`（initTimerLedger 之后）为 PT 模式按恢复的 ledger 重挂墙钟：对每个 key 的每个 pending 时间戳调用既有 `registerProcessingTimeTimer` 通道（回调已自带 key 捕获语义，或按 key 分组只挂每 key 首个到期时间戳以避免重复回调——到期回调本就全量排水）；② 或最小改动：`forEachProcessingTimeTimer` 改为遍历 ledger（把 ledger 升级为「唯一事实源」），并在恢复后由任务层调用一次 fire-now；③ 与 CEP-3 的去重修复协同：ledger 的 `TreeSet.add` 返回值同时作为「是否重挂墙钟」的判据。
- **信心水平**: 确定（无重挂路径、forEach 恒空、PT 无 ledger 自愈均为代码事实）；静默 key 场景的泄漏后果很可能
- **误报排除**: 不是重复 CEP-3（运行期注册无去重/无取消）——本条是**恢复期**调度不重建，两者机制正交（CEP-3 修好后本条依旧存在：恢复后的重建无人做）；也不是 ledger 本身的缺陷——ledger 恢复正确，缺的是把 ledger 重新变成调度。ET 模式不受影响（watermark+ledger 双恢复构成自愈闭环，负面清单第 4 条已验证），故限 PT。
- **复核状态**: 未复核

---

### [FLOW-4] `<timestampsAndWatermarks>` 三个策略来源全缺时静默装配成 NoOp 算子——声明了「时间源」却永不发 watermark，无 WARN、无 fail-fast，直接制造 CEP-2 的「无水印死管线」陷阱
- **文件**:
  - `nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/AdvancedTransforms.java:818-823`（`watermarkStrategyBean` 为空即无条件走 `new XplWatermarkStrategy<>(getTimestampAssigner(), getWatermarkGenerator())`，两个 body 均可为 null，无「至少声明其一」校验）、`:885-905`（`XplWatermarkStrategy.createTimestampAssigner/createWatermarkGenerator` 对 null body 分别回退 NoOp）、`:908-929`（`NoOpWatermarkGenerator.onPeriodicEmit` 空实现 = 永不发水印；`NoOpTimestampAssigner` 透传原 timestamp）
  - `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/stream.xdef:164-169`（三个来源全部可选声明，xdef 不强制）
  - 对照组：`nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:856-867`（同文件族对 `<source>` 的 bean/source 双缺抛 `ERR_STREAM_REQUIRED_BODY`——同一「声明必须存在执行体」标准的既有执行）
- **证据片段**:
```java
// buildTimestampsAndWatermarks —— bean 缺失即进入可为全空的分支，无任何兜底校验：
WatermarkStrategy<T> strategy;
if (m.getWatermarkStrategyBean() != null) {
    strategy = owner.resolveBean(t, m.getWatermarkStrategyBean(), WatermarkStrategy.class);
} else {
    strategy = new XplWatermarkStrategy<>(m.getTimestampAssigner(), m.getWatermarkGenerator()); // 两者都可空
}
// NoOpWatermarkGenerator —— onEvent/onPeriodicEmit 全空：该算子成为纯透传：
@Override public void onPeriodicEmit(WatermarkOutput output) { }
```
- **严重程度**: P2
- **现状**: `<timestampsAndWatermarks id="wm" watermarkInterval="200"/>`（漏写 `watermarkStrategyBean` 或两个 xpl body）通过 xdef 校验、通过 builder、编译出一个**名称叫 watermark 源、实际零水印输出**的透传算子。各 NoOp 回退单独看都有正当理由（FL-3：单边缺失不应 NPE），但「组合态=全空」没有任何一处守卫——与同 builder 对 `<source>`/`<sink>`/`<map>`/`<filter>`/`<reduce>` 的 bean/source 双缺 fail-fast 标准不一致。该算子位于图内且被下游（CEP-2 已证明）当作唯一 watermark 供给点引用时，故障形态正是 CEP-2 描述的最恶性场景：ET 模式 watermark 恒 MIN_VALUE、匹配永不输出、状态无界——但这次连「上游根本没有 watermark 源」的图分析都救不了，因为图里**看起来有**一个 watermark 源。
- **风险**: 声明拼装事故（漏复制一个属性）直接复现 CEP-2 的部署后死管线，且更具迷惑性（拓扑上存在 timestampsAndWatermarks 节点，排查者首先排除「无水印源」假设）；conf-validate 层同样不报（复用同一 builder）。
- **建议**: `buildTimestampsAndWatermarks` 装配后终检：`watermarkStrategyBean == null && timestampAssigner == null && watermarkGenerator == null` → typed `StreamException(ERR_STREAM_REQUIRED_BODY)` 锚定该节点 id/loc，文案说明三者必须至少其一；若确需「仅透传打时间戳」的合法形态，要求显式声明（如新增 `passThrough="true"`）而非靠全空表达。
- **信心水平**: 确定（装配逻辑与 NoOp 行为为代码事实；全仓 grep 确认 `watermarkStrategyBean/getTimestampAssigner/getWatermarkGenerator` 在 flow 生产代码唯一消费点即此，validator 无补充校验）
- **误报排除**: 不是重复 CEP-2——CEP-2 是 `<cep>` 构建点不校验上游 watermark 供给；本条是供给点自身可以合法地是空壳，两个守卫缺口叠加才完整闭合故障链，本条补的是其中尚未被报告的一半；也不是反对 NoOp 回退本身（单边缺省的 FL-3 语义保持），限定的正是三边全缺的组合态。
- **复核状态**: 未复核

---

## 维度复核结论

复核方式：以 live code 逐条独立重验（文件通读 + 全仓 grep），HEAD 与原报告一致（`bd089444`）。只读，未运行构建/测试，未修改任何产品代码。

### CEP-1 — 保留 P1

逐项重验均成立：① `NopCepConfigs.COMMON_HINT`（:21-26）逐字确认宣称 rocksdb 下溢出元素"transport into the rocksdb state storage"；② `SharedBuffer`（:173-189）与 `CepOperator.initStateAccess`（:477-481）确以 `JavaStreamSerializer.of()` 设置三处 descriptor；`Lockable`（Lockable.java:37-48,91-93）无默认构造、`getRefCounter` 包私有、`AtomicInteger` 字段——JSON 往返必丢锁计数的前提成立；③ `RocksDBMapState.readValue/serializeValue`（:291-307）只走 `RocksDBValueSerDe` JSON 路径，rocksdb 模块全量 grep `getSerializer` 零命中；对照 `MemoryStateSerDe`（:238,680-682,729）确实兑现 serializer 契约；④ `CepOperator.resolveKeyedBackend`（:436-463）对已配置 backend 直接 `createKeyedStateBackend` 无任何类型拒绝；`TaskCheckpointWiring`（:169-175）把 env 级 `checkpointConfig.getStateBackend()` 无守卫注入含 CepOperator 在内的任意算子；cep 模块与 docs-for-ai 均无该边界的守卫或文档。**证据勘误一处**：`RemoteTaskDeploySupport:175-177` 并非"注入任意 IStateBackend"——它在 backend 未设时固定注入 `MemoryStateBackend`，其自带注释明确记录远端部署**不**透传 RocksDB 配置（即该路径反而碰不到此坑）；破坏链由 TaskCheckpointWiring 单独完整成立，不影响 P1 定级。裁决：保留。

### CEP-2 — 保留 P2

重验成立：`buildCep`（AdvancedTransforms:746-784）唯一通道 `CEP.pattern(...)`；`PatternStreamBuilder.forStreamAndPattern`（:197-204）硬编码 `TimeBehaviour.EventTime`，`withComparator/withLateDataOutputTag/inProcessingTime`（:102-112）经全仓 grep 确认仅 Java API/测试消费（CEP.java:54、PatternStream.java:58-66、TestCepProductionExecutionE2E:132），flow DSL 无任何接线；stream.xdef:213 `<cep patternRef="!string">` 为唯一节点属性（另有共享 define 的 id/name/bean/parallelism）；CepOperator ET 分支（:867-879）`timestamp > currentWatermark` 才 buffer。前提"无默认水位发射"经 grep 验证：core 的水位发射点仅 `TimestampsAndWatermarksOperator`（用户 generator 驱动）与传输层对已有水明的转发，`XplSourceFunction` 零水位发射。裁决：保留。

### CEP-3 — 保留 P2

重验成立：`registerProcessingTimeTimer`（CepOperator:523-538）每次调用新建闭包并注册、无去重；`deleteProcessingTimeTimer`（:540-542）空实现；`processEvent`（:1245-1260）每个新起始部分匹配注册 1 条全局窗口 + N 条 per-state 窗口 timer（实际注册面比报告所述还多一层）；`TaskProcessingTimeService`（:52,:73-79）`TreeMap<Long, List<TimerRegistration>>` 同刻以 List 追加不合并，fire 时 `pollFirstEntry` 移除已 fire 条目——故"稳态 pending ≈ S×W、无取消路径"的量化与定性均准确。ledger（TreeSet）去重与墙钟调度两套结构行为不同步确认为本条核心。裁决：保留。

### CEP-4 — 保留 P3

重验成立：`LazyVerdictCache`（NFA:1029-1048）IdentityHashMap 按条件实例身份缓存、每次 `computeNextStates` 新建（:723-731）；`checkFilterCondition`（:1016-1026）命中即吞掉求值；正确性前提仅存在于代码注释自述（:724-728）；cep 测试目录 grep `LazyVerdictCache` 零命中，无专项行为钉子。P3（改变可观察求值次数、仅在有状态条件时语义分叉）定级恰当。裁决：保留。

### CEP-5 — 保留 P3

重验成立：`addQualifier`（CepPatternBuilder:161-204）`windowTime` 仅在 oneOrMore/times/timesOrMore 三分支内被读取，三分支全空时无任何终检；pattern.xdef:29 在 part 级 define 声明 `windowTime="duration"` 无数值器前置约束；同 builder 对 subtype 加载失败（:167-171 typed MalformedPatternException）与成环均 typed 报错，"声明即消费"为其自执行标准的论证成立。裁决：保留。

### CEP-6 — 保留 P3

重验成立：`getAfterMatchSkipStrategy`（CepPatternBuilder:224-230）SKIP_TO_FIRST/LAST 直接透传 `getAfterMatchSkipTo()`；`SkipToElementStrategy` 构造器（:35-37）`Guard.notNull` → 裸 `IllegalArgumentException("IsNull:patternName")`（Guard.java:82-86）；pattern.xdef:8 注释声明必填但 xdef（`afterMatchSkipTo="xml-name"` 无 `!`）与 builder 均不强制；Pattern.java:487-491,529-533,578-582 的 CEP-08 注释证实同类形态（times 非法值）已被本模块裁定应走 `MalformedPatternException`，此为漏改点。裁决：保留。

### FLOW-1 — 保留 P2

重验成立：`StreamModelDslBuilder` 全文 `t.getName()` 唯一消费点 :805（buildSource）；`StreamEdgeModel` 的 `getName` 在 flow 生产代码零调用（_StreamEdgeModel.java:171-180 仅生成类自身）；`FieldSpec.getNullable/getDefaultValue`（StreamSchemaRegistry:44-72）经全仓 grep 确认零生产消费方；stream.xdef:94（field nullable/defaultValue）、:141-145（共享 define name）、:250-256（edge `<name>`）声明面确认。fraud 示例 map/aggregate/sink 均声明 `name` 而除 source 外全部静默无效。与同类 edge 属性逐条 fail-fast（validateEdgeDeclarations javadoc 自述）的标准不一致成立。P2 定级恰当（可观测性/缺省语义受损，非计算正确性）。裁决：保留。

### FLOW-2 — 保留 P2

重验成立：`buildTransforms`（:434-446）仅 validateDag（端点/重复 id/属性矩阵/环）+ 拓扑序 + 逐点装配；`validateDag` 无任何 sink-可达性检查（"unreachable node" 仅指环检测的卡死节点）；`StreamExecutionEnvironment.requireSinkTransformations`（:424-431）仅"零 sink"报错，`compilePlans` javadoc 自述只编译 sink-可达子图。断连子树静默蒸发的推演成立。裁决：保留。

### FLOW-3 — 保留 P3

重验成立：fraud-s2-file-delta.stream.xml 与引用一致（e2 重接 wm→blacklist-filter + 新增 e6→kb-user，同 id 覆盖），base 图语义保持；作为记录性条目及其与 FLOW-2 的耦合放大论证成立。裁决：保留。

### FLOW-4 — 保留 P2

重验成立：stream.xdef:164-169 三来源（watermarkStrategyBean/timestampAssigner/watermarkGenerator）全部可选；`buildTimestampsAndWatermarks`（AdvancedTransforms:817-823）bean 为空即进入两 body 均可为空的 `XplWatermarkStrategy` 分支，无至少其一校验；`NoOpWatermarkGenerator.onEvent/onPeriodicEmit`（:908-916）与 `NoOpTimestampAssigner`（:918-927）全空/透传确认；全仓 grep 确认三 getter 在 flow 生产代码唯一消费点即 :819-822；StreamConfValidator 零 watermark 相关校验；对照组 `resolveFunctionOrXpl`（StreamModelDslBuilder:856-867，buildSource:802-803 消费）的双缺 fail-fast 标准确认。裁决：保留。

### CEP-7 — 保留 P2

重验成立：`snapshotState`（:663-695）仅持久化 watermark/key-class/timer-ledger 三个 operator state；`restoreState`（:697-751）仅恢复 ledger 内容；`open()`（:389-426）序列 resolveKeyedBackend→initStateAccess→initTimerLedger→createTimerService→initNfa 无任何 PT 重挂步骤；`forEachProcessingTimeTimer`（:563-565）恒空；`processWatermark` 的 per-key 排水被 `!isProcessingTime`（:795）关死——PT 模式恢复后触发链三路全断确认。对照组成立：WindowOperator.restoreState 捕获 `HeapInternalTimerService.TimerSnapshot`（:679-684）、open() 延迟重挂（:573-577），同仓库已有正确先例。与 CEP-3 的正交性论证成立（运行期注册 vs 恢复期重建）。裁决：保留。

### FLOW-5 — 保留 P1

三层故障链逐环重验：① builder 层——`applyDeclaredParallelism`（StreamModelDslBuilder:1066-1071）对任意 transform 无条件生效，`buildKeyBy`（StreamModelDslBuilder:891-900）与 `buildCep` 末尾（AdvancedTransforms:781-783）均应用声明并行度；`validateEdgeDeclarations`（:566-615）校验 HASH/keyExpr/REBALANCE/BROADCAST/flow-control 五类却无 FORWARD 两端并行度一致性检查，HASH-into-keyBy（ERR_STREAM_EDGE_HASH_REDUNDANT）与 REBALANCE/BROADCAST 拒绝确认；`applyEdgePartition` javadoc（:1030-1040）自认 "FORWARD's modulo semantics would concentrate all traffic on target subtask 0 (a structural false-green)"。② 运行层——`canChain`（JobGraphGenerator:343-352）准则 1 并行度不等即断链；`PartitionRouter.create`（:43-55）FORWARD → `ForwardPartitionRouter.selectChannel` 无条件 return 0（ForwardPartitionRouter.java:29-34），类头注释宣称 modulo（:18）与实现矛盾；`GraphExecutionPlan` javadoc（:69）"source subtask i → target subtask i (modulo target parallelism)" 与实现直接矛盾；`TestPartitionRouter.testForwardPartitionRouter_alwaysReturns0` 钉死恒 0。③ 恢复层——`GraphModelCheckpointExecutor`（:1354-1356）rescale 走 `computeKeyGroupRangeForSubtaskIndex` + `RescaleStateAssembler`（:264,:322）按 `KeyGroupRangeRestoreFilter.filterKeyedStates` 把 keyed state 重分布到全部新 subtask；新流量仍恒发 channel 0，非 0 号 key-group 的状态永无新事件。**证据勘误两处（不动摇结论）**：(a) `ForwardPartitioner` 实际位于 `io/nop/stream/core/graph/`（非 execution/），`partition()` 恒 0 在 :36-39 属实；(b) `buildKeyBy` 在 StreamModelDslBuilder.java:891-900，非 AdvancedTransforms.java:900。**一处表述修正**："KeyGroupRangeRestoreFilter 仅测试引用"不准确——该类在主代码被 `MemoryStateSerDe:280` 与 `RescaleStateAssembler:264` 消费；但其**激活入口** `setTargetKeyGroupRange` 生产运行面确无调用方（仅测试调用），故所支撑的"非 rescale 恢复走 1:1 查找、无 key-group 过滤"结论在 production 路径成立。另一处轻微过表述："keyBy 与其 keyed 下游之间的边只能是 FORWARD"——HASH+keyExpr 边指向 cep/window 等目标并未被拒绝，但那会叠加第二个 keyBy（改变语义的非惯用形），FORWARD 默认路径无守卫的结论不变。P1 定级维持：声明可部署、吞吐扩容为假、rescale 后匹配丢失/分裂且无报错。裁决：保留。

### 复核汇总

12 条发现全部保留，原严重程度无一变动（P1×2：CEP-1、FLOW-5；P2×6：CEP-2、CEP-3、CEP-7、FLOW-1、FLOW-2、FLOW-4；P3×4：CEP-4、CEP-5、CEP-6、FLOW-3）。勘误均为证据引用层面（文件路径 2 处、注入点定性 1 处、消费面表述 1 处），无一动摇发现本体；所有"零调用/恒空/恒为"类论断经全仓 grep 独立复验为真。

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|---|---|---|---|
| CEP-1 | P1 | nop-stream/nop-stream-cep/.../operator/CepOperator.java; .../nfa/sharedbuffer/SharedBuffer.java; nop-stream-rocksdb/.../RocksDBMapState.java; nop-stream-runtime/.../TaskCheckpointWiring.java; .../NopCepConfigs.java | CEP×RocksDB 边界零守卫：RocksDB 后端静默忽略 CEP 状态 descriptor 声明的 JavaStreamSerializer 契约（Lockable JSON 往返必坏），runtime 注入面无类型拒绝，配置提示文本反向宣称 rocksdb 支持 |
| CEP-2 | P2 | nop-stream-flow/.../builder/AdvancedTransforms.java; nop-stream-cep/.../PatternStreamBuilder.java; nop-kernel/nop-xdefs/.../stream/stream.xdef | `<cep>` 时间行为钉死 EventTime 且构建期不校验上游 watermark 供给，PT/比较器/迟到侧输出 DSL 不可表达——无水印管线部署即静默永不匹配 + 状态无界增长 |
| CEP-3 | P2 | nop-stream-cep/.../operator/CepOperator.java; nop-stream-core/.../TaskProcessingTimeService.java | PT 模式墙钟 timer 注册无去重、无取消路径，高匹配率 key 下 pending 定时器按注册次数线性累积（稳态 ≈ S×W），与 ledger 去重行为脱节 |
| CEP-4 | P3 | nop-stream-cep/.../nfa/NFA.java | LazyVerdictCache 按条件实例身份折叠单事件判定，对有状态/非确定性 IterativeCondition 改变可观察求值次数，且无专项回归钉子 |
| CEP-5 | P3 | nop-stream-cep/.../model/builder/CepPatternBuilder.java; nop-kernel/nop-xdefs/.../stream/pattern.xdef | part 级 windowTime 在未声明数值器时被静默丢弃，违反该 builder 自身"声明即消费或 typed 拒绝"标准 |
| CEP-6 | P3 | nop-stream-cep/.../model/builder/CepPatternBuilder.java; .../nfa/aftermatch/SkipToElementStrategy.java | SKIP_TO_FIRST/LAST 缺 afterMatchSkipTo 时抛裸 IllegalArgumentException(Guard)，缺错误码/part 定位，与同文件 CEP-08 已裁定的错误分层不一致 |
| CEP-7 | P2 | nop-stream-cep/.../operator/CepOperator.java（对照 nop-stream-runtime/.../WindowOperator.java） | PT 模式墙钟 timer 恢复后不重建：ledger 恢复而调度丢失（forEachProcessingTimeTimer 恒空、processWatermark 自愈被关死），静默 key 的窗口超时永不触发并泄漏状态——同仓库 WindowOperator 已有 TimerSnapshot 正确先例 |
| FLOW-1 | P2 | nop-stream-flow/.../builder/StreamModelDslBuilder.java; .../StreamSchemaRegistry.java; nop-kernel/nop-xdefs/.../stream/stream.xdef | 一族 xdef 属性解析进模型但零消费且无 fail-fast：transform 级 name（source 除外）、edge `<name>`、schema 字段 nullable/defaultValue |
| FLOW-2 | P2 | nop-stream-flow/.../builder/StreamModelDslBuilder.java; nop-stream-core/.../StreamExecutionEnvironment.java | 构建期无 sink-可达性校验，与全部 sink 断连的已声明变换被静默排除出编译图——管线照常部署、零输出、无告警 |
| FLOW-3 | P3 | nop-stream-fraud-example/.../fraud-s2-file-delta.stream.xml | delta 定制语义经核查保持（e2 重接正确），但 FLOW-2 可达性缺口在 delta 场景被放大（漏写一条边即静默断连且 diff 更难察觉），随 FLOW-2 一并解决 |
| FLOW-4 | P2 | nop-stream-flow/.../builder/AdvancedTransforms.java; nop-kernel/nop-xdefs/.../stream/stream.xdef | `<timestampsAndWatermarks>` 三策略来源全缺时静默装配成零水印 NoOp 算子，拓扑上"看起来有"水印源而实际制造 CEP-2 死管线，且更具迷惑性 |
| FLOW-5 | P1 | nop-stream-flow/.../builder/StreamModelDslBuilder.java; nop-stream-core/.../execution/ForwardPartitionRouter.java; .../jobgraph/JobGraphGenerator.java; nop-stream-runtime/.../GraphModelCheckpointExecutor.java | keyed 阶段 `keyBy(P)→下游(Q≠P)` 的 FORWARD 边三层全放行：路由恒发 channel 0（声明并行度为假、与 javadoc/测试矛盾），rescale 按 key-group 重分布状态后流量与状态永久分离——静默丢匹配 |
