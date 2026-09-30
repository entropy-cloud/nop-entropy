# 深度审计 R4 轮 — nop-stream 状态后端与序列化（02）

- 审计日期：2026-09-30
- 审计对象：nop-stream 状态管理子系统（live code，工作副本根 `/Users/abc/app/nop-entropy-wt/nop-entropy-master`）
- 审计编号前缀：R5-ST
- 审计方式：全量人工精读 + 定向 grep；不修改任何代码
- 注：任务描述中的模块路径 `nop-stream-core/...` 实际为 `nop-stream/nop-stream-core/...`（多模块父目录 `nop-stream/` 下），行号均以仓库内实际文件为准。

## 范围

| 区域 | 文件 |
|---|---|
| memory 后端 | `nop-stream/nop-stream-core/.../state/backend/memory/`（MemoryStateSerDe 783 行、MemoryKeyedStateBackend、AbstractMemoryState、MemoryMapState、TypedNamespaceAndKey） |
| shard/keyGroup | `nop-stream/nop-stream-core/.../state/shard/`（KeyGroupAssignment、KeyGroupRangeRestoreFilter、KeyGroupReshard、KeyGroupRange） |
| TTL | TtlContext、StateTtlConfig、RocksDbTtlAware、RocksDBKeyedStateBackend.cleanupExpiredEntries |
| RocksDB 后端 | `nop-stream/nop-stream-rocksdb/...`（RocksDBKeyedStateBackend 951 行、RocksDBSnapshotSerDe 679 行、RocksDBKeyEncoder、RocksDBValueSerDe、各 state 类、incremental/） |
| checkpoint 协调 | `nop-stream/nop-stream-runtime/.../checkpoint/`（CheckpointCoordinator 1567 行、PendingCheckpoint、RetentionCleaner） |
| checkpoint 执行 | `.../execution/GraphModelCheckpointExecutor.java` 1467 行、RescaleStateAssembler、TaskCheckpointWiring |
| 存储 | `.../checkpoint/storage/`（JdbcCheckpointStorage 929 行、CheckpointSerDe 945 行、LocalFileCheckpointStorage） |
| barrier | `nop-stream/nop-stream-core/.../execution/CheckpointBarrierTracker.java` |
| wire codec | `.../runtime/transport/SysDaoWireCodec.java` |

## 一、历史已修复项验证（勿重复立项，验证完整性）

| 历史项 | 验证结论 |
|---|---|
| R4-N4（tableExists 不再吞异常） | **修复完整**。`JdbcCheckpointStorage.tableExists/epochTableExists`（409-417 / 577-585 行）对 catalog 查询失败一律抛 `CheckpointStorageException`，javadoc 明确区分"查询成功且表不存在"。grep 同族模式后残留两处降级见 R5-ST-06（serde 层 null 折叠）与 R5-ST-17（方言解析降级仅 debug 日志）。`RocksDBKeyedStateBackend.openDB` 的 `listColumnFamilies` 失败降级为空 DB（215-219 行）保留 WARN+继续——与 R4-N4 的"表不存在"不同，这是 open 路径的恢复语义且有响亮日志 + 精确后果说明（"prior state will be invisible"），**判定为已裁决接受，不立项**。 |
| R4-A2'（onCompletePersistFailure 清理 checkpointSuccessMap） | **修复完整且对称**。grep `checkpointSuccessMap` 全量位点：唯一 put 在 `notifyParticipantsFinishCommit`（1212 行）；remove 位点覆盖 success（828）、persist-failure（876-877，条件=无 failedCommitParticipants）、abort（934-935，同条件）、retry 收敛（1250）、shutdown（1326）。failure/abort 路径在 `failedCommitParticipants` 仍持有该 epoch 时保留 marker，`retryFailedCommits` 重放时 `getOrDefault(failedEpoch, true)` 能读到真实 success 标志——语义自洽，未发现其他滞留 map/list（`failedCommitParticipants` 生命周期同样有界：retry 成功或 participant 被移除时收敛）。 |
| manifest checksum JSON 文本往返（369699be2d） | 机制健全。`CheckpointSerDe.computeCanonicalChecksumHex`（351-365 行）：写读双方共用 canonicalizeFieldOrder + `serialize→parseMap→serialize` 文本往返 + `normalizeNumbersDeep` 数值固定点；`normalizeValue` 对整值用原始 `num.longValue()`（非 double 截断），Long>2^53 不丢精度。 | 
| bean 型 keyed state 恢复回归 / AR-01 | memory 侧 `MemoryStateSerDe.deserializeKey`（667-689 行）按 keyType 重材料化 + fail-fast，有专项测试（`TestMemoryStateSerDeNumericKeyRestore`，覆盖 Long<2^31→Integer 场景）。**但修复存在系统级不完整之处，见 R5-ST-02**（RocksDB restore 无对应重材料化；且 memory 的 range filter 发生在重材料化之前）。 |
| incremental restore fail-fast / shared SST ref leak | `RocksDBKeyedStateBackend.restoreIncremental` 无 segmentStore 时 fail-fast（855-861 行）；persist 失败回滚 `releaseIncrementalSegments`（683-701 行）退 ref-count 并按零引用丢弃文件。**验证通过**。残留的共享 SST 本地路径生命周期时序问题见 R5-ST-18。 |
| B6'（FileSource directoryPath 保留字符） | 不在本轮范围（source 连接器），未检查。 |

## 二、发现（按严重程度排序）

---

### [R5-ST-01] PendingCheckpoint.abort 双重 CAS 导致 abort 路径 future 永不异常完成

**文件**：`/Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/PendingCheckpoint.java`（168-179 行）；配合 `CheckpointCoordinator.java`（897-911 行）

**证据片段**（PendingCheckpoint.abort）：
```java
public synchronized void abort(String reason, Throwable cause) {
    checkValidTransition(Status.ABORTED);
    if (status.compareAndSet(Status.RUNNING, Status.ABORTED)) {   // ← 永远为 false
        isDisposed = true;
        if (!completableFuture.isDone()) {
            ... completableFuture.completeExceptionally(error);
```
**证据片段**（CheckpointCoordinator.abortPendingCheckpoint，897-911 行）：
```java
if (!pending.getStatus().compareAndSet(PendingCheckpoint.Status.RUNNING, PendingCheckpoint.Status.ABORTED)) {
    LOG.debug("Skip aborting checkpoint {} ...");
    return;
}
PendingCheckpoint removed = pendingCheckpoints.remove(checkpointId);
if (removed == null) { ... return; }
removed.abort(reason);   // 此刻 status 已是 ABORTED，内部 CAS 必然失败
```

**严重程度**：P1（契约漂移 + 真实错误面被掩盖）

**现状**：协调器先做 RUNNING→ABORTED 的 CAS，成功后才调用 `removed.abort(reason)`；而 `PendingCheckpoint.abort` 内部再次 CAS RUNNING→ABORTED。第二次 CAS 恒为 false（`checkValidTransition` 因 from==to 放行），于是 `isDisposed=true` 与 `completableFuture.completeExceptionally(...)` 成为死代码。

**风险**：
1. 超时 abort 后 pending 的 future **永远不完成**。三个生产等待方全部受影响：`GraphModelCheckpointExecutor.triggerSavepoint`（408-409 行）、`triggerTerminalSavepoint`（507-508 行）、`JobCoordinator`（1981-1982 行）。savepoint 触发后若 checkpoint 超时被 abort，等待方拿不到 `ERR_STREAM_CHECKPOINT_ABORTED`，而是空等满 `checkpointTimeout` 后收到 `TimeoutException`——故障语义被静默替换，DRAIN/SUSPEND 终止路径表现为"莫名超时"而非"checkpoint 被中止"。
2. 与 `forceFail`（211-229 行，已用 `status.get()` 判断而非 CAS，注释还专门解释了为什么不能用 fail()）形成对照——abort 路径的同等问题被遗漏。

**建议**：协调器侧取消预 CAS（交给 `PendingCheckpoint.abort` 完成 CAS+future 完成），或 `abort()` 改为与 `forceFail` 相同的"读-判断-补完 future"模式：当 current==ABORTED 且 future 未完成时补 `completeExceptionally`。补一条回归测试：触发 timeout abort 后断言 `future` 在短时间内异常完成且原因带 abort reason。

**信心水平**：高（调用链与 CAS 语义均为确定性推理；`checkValidTransition(ABORTED→ABORTED)` 经 `isValidTransition` 的 from==to 分支放行已逐行核对）。

**误报排除**：`pending.dispose()` 仅在 coordinator.shutdown 中调用（cancel future），不覆盖运行期超时 abort；scheduleTimeout 的重入 abort 因 status 已 ABORTED 直接 early-return，也不会补完 future。

---

### [R5-ST-02] restore 路径的 key-group 以 JSON 原生 key 计算：RocksDB restore 完全没有 keyType 重材料化，memory 的 range filter 先于重材料化

**文件**：
- `/Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/RocksDBSnapshotSerDe.java`（408-418、476-482 行）
- `/Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/shard/KeyGroupRangeRestoreFilter.java`（95-99 行）
- `/Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/shard/KeyGroupReshard.java`（114-122 行）
- 对照：`MemoryStateSerDe.java`（364-377、667-689 行，AR-01 修复点）

**证据片段**（RocksDBSnapshotSerDe.putEntry — 恢复写入用原生 key 算 group 并烧进二进制 key）：
```java
private static void putEntry(RocksDBKeyedStateBackend<?> backend, ColumnFamilyHandle cf,
                             Object namespace, Object rawKey, byte[] valueBytes) {
    int keyGroupId = backend.computeKeyGroupId(rawKey);      // rawKey = e.get("key")，JSON 原生形态
    byte[] key = RocksDBKeyEncoder.encode(
            RocksDBKeyEncoder.deserializeNamespace(namespace), rawKey, keyGroupId);
```
（`restoreMapState` 476-482 行同样用 `Object rawKey = e.get("key"); int keyGroupId = backend.computeKeyGroupId(rawKey);`）

**证据片段**（KeyGroupRangeRestoreFilter — rescale 过滤同样用原生 key）：
```java
for (Map<String, Object> e : entries) {
    Object rawKey = e.get("key");
    if (keyOwnedByRange(rawKey, range, maxParallelism)) {    // JSON 原生 key 的 hash
```

**对照证据**（live 写路径用的是 typed key）：`RocksDBKeyedStateBackend.buildStorageKey`（420-422 行）`computeKeyGroupId(rawKey)` 的入参是 `currentKey`（类型化 K）；`MemoryKeyedStateBackend.routeKey`（458-468 行）入参是 `currentKey`。而 memory 恢复链路是先 filter（原生 key）→ `restoreKeyedEntries` 内 `backend.routeKey(deserializeKey(...))`（类型化 key，MemoryStateSerDe 370-372 行）。

**严重程度**：P1（特定 key 类型下恢复失败/状态不可达 + rescale 误路由）

**现状**：AR-01 已证明"JSON 持久层往返会改变 key 运行时类型"（Long→Integer）并修复了 memory serde 的 key 重材料化；但：
1. RocksDB 全量 JSON restore 的三个入口（`restoreValueEntries`/`restoreMapState`/`restoreListEntries`）直接使用 `e.get("key")` 原生对象计算 key-group 并编码字节，从不按 `keyType`（快照头里明明写有 `"keyType"`，`snapshotState` 90 行，restore 却不读）重材料化。对任何 JSON 往返改变类型/hash 的 key（Date、UUID、BigDecimal、bean），恢复写入的 key-group 前缀与 live 读取计算的 group 不同 → **恢复出来的条目永远查不到**（读路径 miss，无任何报错）。
2. memory 后端 rescale（`KeyGroupRangeRestoreFilter`，被 `MemoryStateSerDe.restoreState` 273-279 行与 `RescaleStateAssembler.mergeAndFilterKeyedStates` 183 行调用）以及 maxParallelism 迁移（`KeyGroupReshard.redistributeStates`）都在重材料化**之前**用原生 key 计算 group：对上述 key 类型，过滤决策与恢复后 `routeKey(typedKey)` 的真实 group 不一致 → 条目被分给错误的 subtask（跨 subtask 既不复制也不纠正 → 一侧丢失）。

**风险**：使用非 String/整型 key 的有状态作业（Date 时间 key、enum key、bean key 在流处理中常见）在 (a) RocksDB 后端 checkpoint 恢复、(b) memory 后端 rescale/maxParallelism 迁移 两个场景下静默丢状态或状态不可达。`TestMemoryStateSerDeNumericKeyRestore` 只覆盖 Long/Integer；`TestKeyGroupRangeBackendRestore`/`TestKeyGroupReshard` 均未测 Date/enum/bean key。

**建议**：
1. RocksDB restore 三入口在 `putEntry`/`restoreMapState` 前按快照头 `keyType`（或 backend.keyType）做与 `MemoryStateSerDe.deserializeKey` 等价的重材料化，并让 group 计算发生在重材料化之后；
2. memory 的 `KeyGroupRangeRestoreFilter.filterKeyedStates` / `KeyGroupReshard.redistributeStates` 增加 keyType 参数，先重材料化再算 group（或退而求其次：filter 之后按 typed key 的真实 group 二次校验并 fail-fast 不一致）；
3. 补 Date/enum/bean key 的 rescale-restore 回归测试。

**信心水平**：结构性不一致为高（代码路径确定）；具体 key 类型触发面为中（取决于 JsonTool 对各类型的往返类型；Long/String/Integer 已证安全，Date/enum 结构上必然改变 hash 输入，bean 依赖字段序保持）。未实际运行验证 Date 的 JSON 往返形态，故整体置信"高（不一致存在）/ 中（触发广度）"。

**误报排除**：RocksDB 增量路径不受影响（SST 字节里的 group 前缀是快照时用 typed key 算好的，`copyColumnFamilyRange` 只按字节范围拷贝）；`keyLen<=0`/null key 分支不受影响（group=hash(null)=0，两侧一致）；Long/Integer 键因 `Integer.hashCode()==Long.hashCode()`（同值）而安全，已有测试佐证。

---

### [R5-ST-03] KeyGroupAssignment.stableHash 把 Enum 标为"稳定值 hash"，但 Enum.hashCode 是 identity hash（G38 契约失效）

**文件**：`/Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/shard/KeyGroupAssignment.java`（61-82、187-207 行）

**证据片段**：
```java
private static boolean isStableValueHashType(Object key) {
    if (key instanceof String
            || key instanceof Integer
            ...
            || key instanceof Date) {
        return true;
    }
    if (key instanceof Enum<?>) {
        return true;      // ← Enum.hashCode() 默认是 Object.hashCode()（identity）
    }
    return false;
}
```
类 javadoc（G38）声称："The hash returned for a key depends only on the key's *value*, never on JVM identity"。

**严重程度**：P1（跨 JVM / 跨重启路由不可复现）

**现状**：`java.lang.Enum` 不覆写 `hashCode()`（JDK 15+ 明确放开 final 限制但默认实现仍是 `super.hashCode()` 的 identity hash）。enum key 的 key-group 在两个 JVM 中（coordinator 进程与 task 进程、或作业重启前后）几乎必然不同。

**风险**：enum key 作业的 checkpoint 在 (a) 分布式部署（RPC 路径，keyBy 路由按 hash）下数据被分到与恢复后不同的 subtask；(b) 结合 R5-ST-02，restore 侧 range filter 用快照中的 enum 名字符串（String hash，稳定）计算 group，live 写入用 enum identity hash —— 过滤归属与实际存储归属系统性错位。`stableHash` 的 JSON/murmur3 兜底分支本可救场，但 `isStableValueHashType` 短路在前面。

**建议**：enum 分支改为 `((Enum<?>) key).name()` 的 String hash（或直接落到 murmur3(JSON) 分支），并补一条"跨虚拟 enum key group 一致性"测试；若有意保留旧行为需在 javadoc 修正 G38 声明并禁止 enum key。

**信心水平**：高（Enum.hashCode 语义为 JDK 规范行为；`isStableValueHashType` 无任何 enum 特判补偿）。

**误报排除**：单 JVM 内 identity hash 一致，纯内存单进程作业的运行期不受影响——问题只在跨进程/跨重启的 checkpoint 路由与恢复。

---

### [R5-ST-04] RocksDB MapState 快照按 `namespace + "|" + rawKey` 分组：分隔符碰撞会合并两个不同 base key 的 map 行

**文件**：`/Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/RocksDBSnapshotSerDe.java`（177-233 行）

**证据片段**（198-206 行）：
```java
int baseLen = RocksDBKeyEncoder.baseKeyLength(fullKey);
RocksDBKeyEncoder.DecodedKey dk = RocksDBKeyEncoder.decode(fullKey, backend.getKeyType());
String groupKey = dk.namespace + "|" + dk.rawKey;      // 字符串拼接，无转义
Map<String, Object> entry = grouped.get(groupKey);
if (entry == null) {
    entry = new LinkedHashMap<>();
    entry.put("namespace", RocksDBKeyEncoder.serializeNamespace(dk.namespace));
    entry.put("key", dk.rawKey);
```

**严重程度**：P2（真实数据损坏，触发需要 namespace/key 含 "|"）

**现状**：MapState 快照把同一 (namespace, key) 的所有 map 行聚成一个 snapshot entry；分组键是 `namespace.toString() + "|" + rawKey.toString()`。`(ns="x", key="a|b")` 与 `(ns="x|a", key="b")` 产生同一 groupKey：第二组的行被追加进第一组的 `mapValue` 列表，entry 的 namespace/key 只保留第一组的——恢复时全部行写到第一个 base key 下，第二个 base key 的 map 数据被静默合并/吞并。二进制 key 编码本身是 TLV 无歧义的（`[group][nsLen][ns][keyLen][key]`），只有这个 Java 层分组键退化成了裸拼接。

**风险**：namespace 或 key 含 "|" 的 MapState 作业在 checkpoint→restore 后 map 数据错乱（多出来的键值对/丢失的 map）。memory 后端无此分组逻辑，不受影响。

**建议**：分组键改用已解码的结构对（`AbstractMap.SimpleEntry<DecodedKey,?>` 或 `nsBytes + 0x00 + keyBytes`，或直接用 `baseLen` 前缀字节 `Arrays.copyOf(fullKey, baseLen)` 作为分组键——后者零歧义且免解码）。

**信心水平**：高（碰撞构造直接、路径无补偿校验）。

**误报排除**：namespace 为默认 String 常量、key 为简单标量时不触发；`mapValue` pair 内部无歧义（`appendMapKey` 是长度前缀编码）。

---

### [R5-ST-05] 容器类型 ValueState 的元素类型在整个 P1-21-01 链路上仍会丢失（encode/decode 只接在 MapState）

**文件**：
- `/Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/RocksDBValueState.java`（91 行）
- `/Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/RocksDBSnapshotSerDe.java`（299-303、437-446 行）
- `/Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryStateSerDe.java`（126、384-390 行）

**证据片段**（RocksDBValueState.update — 直接 JSON 序列化，无 wrap）：
```java
backend.getDb().put(cfHandle, key, RocksDBValueSerDe.serialize(value));   // 对照 RocksDBMapState.serializeValue: serialize(ContainerValueCodec.encode(value))
```
**证据片段**（RocksDBSnapshotSerDe.restoreValueEntries — 无 decode）：
```java
Object value = RocksDBValueSerDe.deserializeObject(e.get("value"), valueClass);
putEntry(backend, cf, e.get("namespace"), e.get("key"), RocksDBValueSerDe.serialize(value));
```

**严重程度**：P2（P1-21-01 修复不完整的同族残留）

**现状**：`ContainerValueCodec.encode` 只被 `RocksDBMapState.serializeValue`（306-307 行）与 `MemoryStateSerDe.writeMapPayload`（209 行）调用；`decode` 只被 RocksDB/Memory 的 MapState restore 调用。声明类型为 `List<T>`/`Map<K,V>` 的 **ValueState** 在两个后端都是裸 JSON 往返：
- 写入：`serialize(List<Event>)` → 纯 JSON 数组，无元素类型包装；
- RocksDB 快照（`writeValuePayload`）→ `deserialize(bytes, List.class)` → JSON-native `List<LinkedHashMap>`；
- memory restore（`deserializeValue`）走 `ContainerValueCodec.decode` 的 **unwrapped 分支**：仅 `LOG.warn` 后原样返回 JSON-native 容器（ContainerValueCodec 142-151 行）；RocksDB restore（`deserializeObject` → `parseBeanFromText(json, List.class)`）连 warn 都没有，元素退化为 LinkedHashMap。

**风险**：`ValueState<List<Event>>` 形态的作业（在 value 里攒批次是常见写法）restore 后首次按元素类型访问即 ClassCastException / 条件判断静默失真。memory 侧有 warn（可观测），RocksDB 侧完全静默。对比：`ListState<Event>`（顶层 list 状态）因 `writeListPayload`/`deserializeList` 逐元素按声明类型重材料化而是安全的——即同一容器类型在 ListState 与 ValueState 两个家族里保护不对称。

**建议**：把 `ContainerValueCodec.encode/decode` 对称接入 ValueState 家族（memory `snapshotOneState` 的 ValueState 分支 + RocksDB `RocksDBValueState.update/read` 与 `writeValuePayload`/`restoreValueEntries`），或至少让 RocksDB restore 与 memory 一样走 `ContainerValueCodec.decode` 获得 warn 级可观测。

**信心水平**：高（调用点穷举：`grep -rn "ContainerValueCodec.encode\|ContainerValueCodec.decode" nop-stream --include=*.java` 仅 MapState 相关 6 处）。

**误报排除**：`ListState`/`MapState` 不受影响（各自有元素级或包装级保护）；自定义 `IStreamSerializer` 存在时 memory 走 `__java_bytes__` 标记路径不受影响（RocksDB 无自定义 serializer 通道，见 R5-ST-09 备注）。

---

### [R5-ST-06] CheckpointSerDe.deserializeCheckpoint 对缺字段行返回 null：主恢复路径把"损坏"折叠为"无 checkpoint"→ 静默冷启动（R4-N4 同族残余）

**文件**：
- `/Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/CheckpointSerDe.java`（166-175 行）
- `JdbcCheckpointStorage.java`（127-147 行）、`LocalFileCheckpointStorage.java`（116-146 行）、`GraphModelCheckpointExecutor.java`（932-936 行）

**证据片段**：
```java
if (jobId == null || pipelineId == null || checkpointId == null
        || triggerTimestamp == null || completedTimestamp == null) {
    LOG.warn("Checkpoint data missing required fields, skipping deserialization");
    return null;                      // 损坏 → null → 上层当作"没有 checkpoint"
}
```
**证据片段**（GraphModelCheckpointExecutor.restoreFromCheckpoint）：
```java
CompletedCheckpoint latestCheckpoint = coordinator.restoreFromCheckpoint();
if (latestCheckpoint == null) {
    LOG.info("No recoverable checkpoint found, starting fresh");   // 仅 info
    return;
}
```

**严重程度**：P2（R4-N4 同族：异常/损坏 → "空"）

**现状**：R4-N4 把"catalog 查询失败→表不存在"的折叠修掉了，但 serde 层仍有一条同族路径：JSON 可解析但必填字段缺失（截断写入的 legacy 行、字段被改名的 forward-compat 数据、手工编辑）时返回 null。`getLatestCheckpoint`（JDBC 与 LocalFile 均是）把 null 直接上抛为"无 checkpoint"，主恢复路径以 **LOG.info** 冷启动有状态作业；而同模块的 `getAllCheckpoints`/`getLatestCheckpoints` 对 null 行是 skip-and-continue（165-169 / 195-199 行），manifest 侧 checksum 损坏则是 typed fail-fast（`ERR_STREAM_CHECKPOINT_CHECKSUM_MISMATCH`）——同一类损坏数据在四个读路径上有三种降级行为。

**风险**：最新 checkpoint 行/文件损坏时，作业不是响亮失败（让运维介入）而是以空状态启动继续产出（2PC sink 除外），下游拿到与新空状态一致但业务上错误的输出。checksum 存在的行会 fail-fast（好），仅 v1 legacy 无 checksum 行或缺字段的行走这条静默路径。

**建议**：区分两种 null 语义：(a) "存储中确实没有任何 checkpoint"（合法冷启动）与 (b) "存在 checkpoint 但不可解读"（应抛 typed `ERR_STREAM_CHECKPOINT_*` 并附文件/行标识）。最小改法：`deserializeCheckpoint` 增加抛错变体，`getLatestCheckpoint` 用之，list 类方法保留 skip。

**信心水平**：高（代码路径确定）；触发频率低-中（原子写使截断少见，legacy/手工场景为主）。

**误报排除**：带 checksum 且 checksum 不匹配的损坏体会 fail-fast（149-164 行），不入此路径；`data == null || length == 0`（空结果集）是合法"无 checkpoint"，不在本发现范围。

---

### [R5-ST-07] savepoint 恢复路径三级查找全 miss 时静默 fresh start

**文件**：`/Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/GraphModelCheckpointExecutor.java`（1001-1035 行）

**证据片段**：
```java
if (savepointCheckpoint == null) {
    LOG.info("No recoverable savepoint found at path {}, starting fresh", savepointPath);
    return;
}
```

**严重程度**：P2

**现状**：`executeWithSavepoint(jobGraph, jobName, config, savepointPath)` 是用户显式要求"从这个 savepoint 恢复"的入口。查找链为：`savepointStorage.getLatestCheckpoint(jobId, pipelineId)` → `loadSavepoint(path)` → 父目录 `getLatestCheckpoint`。前两级都以 (jobId, pipelineId) 为键——若 savepoint 是以不同 jobId（如改名后重放）触发存储的，或路径打错，三级全 miss 后只打 info 即以空状态启动。与同文件 restore 路径的 fail-fast 风格（fingerprint fail-fast、reverse-vertex differential reject、`ERR_STREAM_CHECKPOINT_EXECUTOR_RESTORE_FAILED`）不一致。

**风险**：运维误操作（错路径/错 jobId）不会被拒绝，作业直接从空状态运行：窗口/CEP 状态清零、2PC sink pendingCommits 丢失（2PC sink 的 restoreFromEpoch 找不到 epoch 时行为取决于 participant 实现）。发现越晚损失越大。

**建议**：显式 savepoint 恢复入口在"路径存在（目录或 .checkpoint 文件可解析）但内容不可用/键不匹配"时 fail-fast（`ERR_STREAM_SAVEPOINT_*`），仅"路径完全不存在"保留 fresh start 并升级为 WARN。

**信心水平**：高（逻辑直接）。

**误报排除**：`restoreFromCheckpoint`（自动恢复路径）的 fresh-start 由 AR-1 显式裁决过（默认目录不自动恢复），不在本发现范围；本条仅针对显式 savepointPath 入口。

---

### [R5-ST-08] 自定义序列化 payload 标记与用户 Map 值存在判别歧义（`__java_bytes__` / `nopContainerType`）

**文件**：
- `/Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryStateSerDe.java`（728、753-761 行）
- `/Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/ContainerValueCodec.java`（53-58、175-182 行）

**证据片段**：
```java
if (obj instanceof Map && ((Map<?, ?>) obj).containsKey(JAVA_BYTES_MARKER)) {
    // custom-serializer payload: base64-decode and Java-deserialize
    byte[] payload = java.util.Base64.getDecoder()
            .decode(String.valueOf(((Map<?, ?>) obj).get(JAVA_BYTES_MARKER)));
```

**严重程度**：P3（低概率但后果为数据损坏/恢复失败）

**现状**：还原路径用"值是 Map 且含特定 key"来判别自定义序列化 payload（`__java_bytes__`）与容器包装（`nopContainerType`）。若用户 MapState/ValueState 的值本身是含 `__java_bytes__` 键的 Map（例如业务数据原样透传 JSON），且该值未走 encode 包装（JSON 路径下的裸 Map 值），restore 会把它误判为字节 payload：base64 解码失败抛 `IllegalArgumentException`（恢复失败），或侥幸解码成功后 Java 反序列化出无关对象（数据损坏）。`nopContainerType=list|map` 的同构问题因 encode 先行而被大部分屏蔽（仅 legacy 快照暴露）。

**风险**：触发面窄（需要用户值恰好携带 magic key），但一旦触发是恢复失败或静默错值，且用户极难归因。

**建议**：marker 值加结构校验（`__java_bytes__` 要求值为合法 base64 字符串且 Java 反序列化成功，失败则按普通 Map 继续解码并 WARN）；或在 encode 侧对含 magic key 的用户 Map 主动加一层防混淆包装。

**信心水平**：高（歧义确定性存在）/ 触发概率低。

**误报排除**：走自定义 `IStreamSerializer` 的值在快照里必然是 `{__java_bytes__: <base64>}` 形态（写入侧先序列化），不会与用户数据混淆；`nopContainerType` 碰撞路径已由 encode-before-decode 的顺序基本排除。

---

### [R5-ST-09] RocksDB 状态子系统 17 处无 ErrorCode 的 `new StreamException("...")`，JDBC 存储 1 处 bare RuntimeException——同模块双轨错误风格

**文件**：
- `/Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/`（RocksDBSnapshotSerDe.java:416、RocksDBMapState.java:77/155/168/184/194/207、RocksDBValueState.java:110、RocksDBListState.java:78/170、RocksDBReducingState.java:124、RocksDBAggregatingState.java:195、RocksDBInternalAppendingState.java:185、AbstractRocksDBState.java:89、incremental/RocksDBIncrementalSnapshotStrategy.java:170/195）
- `/Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/JdbcCheckpointStorage.java`（895-911 行）

**证据片段**：
```java
// RocksDBSnapshotSerDe.putEntry
} catch (Exception e) {
    throw new StreamException("Failed to restore entry", e);        // 无 ErrorCode
}
// JdbcCheckpointStorage.runInsertOrUpdateSeparateTxns
throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);   // bare RuntimeException
```

**严重程度**：P3（规范允许模块内部英文字符串消息，但同一子系统双轨制是真实维护成本）

**现状**：`nop-stream-core` 状态层统一用 `ERR_STREAM_STATE_ERROR`/`ERR_STREAM_TYPE_MISMATCH` + `.param(...)`；`nop-stream-rocksdb` 状态类 17 处用裸字符串 `StreamException`（同文件内两种风格并存，如 RocksDBSnapshotSerDe 既有 `ERR_STREAM_STATE_ERROR` 也有裸字符串）。AGENTS.md 的两级策略允许"模块内部用模块异常类+英文字符串"，故不判违规，但：错误码监控（按 code 聚合告警）对 RocksDB 状态读写失败不可用；`JdbcCheckpointStorage` 的 `new RuntimeException(e)` 则明确违反"Never use bare RuntimeException"。

**建议**：JDBC 的 bare RuntimeException 至少改为 `CheckpointStorageException(ERR_STREAM_CHECKPOINT_ERROR, e)`；RocksDB 侧统一到 `ERR_STREAM_STATE_ERROR` + `ARG_DETAIL`（机械替换成本低）。

**信心水平**：高（grep 全量清点，模式 `new StreamException("` 于 nop-stream/*/src/main/java，17 处命中 rocksdb 状态子系统，1 处 cep/DeweyNumber 不属状态子系统已排除）。

**误报排除**：`StreamException(String)` 构造是模块刻意提供的兼容入口（存在即合法 API），本发现仅针对使用侧不一致，不要求删除构造器。

---

### [R5-ST-10] RocksDB 全量 restore 先清空 DB 再逐条写入：中途失败留下半恢复的持久 DB

**文件**：`/Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/RocksDBSnapshotSerDe.java`（343-344、394-406 行）

**证据片段**：
```java
clearAllStates(backend);          // 先删光所有 CF 的存量数据（含 default 之外的每个 CF）
backend.getStates().clear();
...
for (Map.Entry<String, Object> entry : effectiveStatesMap.entrySet()) {
    ...
    switch (stateType) {
        ...
        default:
            throw new StreamException(ERR_STREAM_STATE_ERROR)   // 中途失败：DB 已被清空
```

**严重程度**：P3（fail-fast 语义正确，破坏可从 checkpoint 存储重放；但不可逆清空值得说明）

**现状**：恢复先 `clearAllStates`（逐 key 删除，iterator + delete，非原子）再写快照条目。任何一条 entry 损坏（如 R5-ST-06 的缺字段、R5-ST-08 的 marker 误判、未知 stateType）都会在 DB 已清空后抛出。任务启动失败、作业不运行（fail-fast 目标达成），本地 DB 处于部分恢复状态；因 checkpoint 数据仍在存储层，修复后重新 restore 可收敛。`clearAllStates` 只清 `cfHandles` 中的 CF，`defaultCF` 不清——当前无状态写 default CF，语义恰好正确但依赖隐含约定。

**建议**：恢复写入前先写临记录或改用"新 DB 目录 + 原子换名"（同 RocksDBIncrementalRestore 的 reconstruct 模式）；最低成本是 在 fail-fast 抛出前对已写入状态做二次 `clearAllStates`（保证不留混合态），并给 defaultCF 不参与状态写入的约定补注释/断言。

**信心水平**：高（顺序确定）；实际影响受限（restore 失败即作业启动失败）。

**误报排除**：memory 后端的 `states.clear()`（MemoryStateSerDe 281 行）同理但内存态、进程退出即消失，不单独立项。

---

### [R5-ST-11] restoreRangeInto 的 native handle 关闭顺序与后端自定纪律相反（先 db 后 CF handle）

**文件**：`/Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/incremental/RocksDBIncrementalRestore.java`（209-230 行）

**证据片段**：
```java
try (RocksDB src = openReadOnlyTyped(dbOptions, reconstructed, descriptors, handles)) {
    ...
} finally {
    for (ColumnFamilyHandle h : handles) {
        if (h != null) h.close();      // RocksDB src 已先被 try-with-resources 关闭
    }
}
```

**严重程度**：P3

**现状**：`RocksDBKeyedStateBackend.close()`（889-950 行）刻意按"metrics → CF handles → defaultCF → db → options"顺序关闭并以此为注释纪律；`restoreRangeInto` 的 try-with-resources 使 `src.close()` 先于 finally 中的 handle.close() 执行——顺序相反。rocksdbjni 官方契约要求 ColumnFamilyHandle 先于 DB 关闭；先关 DB 再关 handle 在部分平台/版本上会触发 native 层警告乃至未定义行为（读只打开的短生命周期 DB 概率低，但与项目自身纪律不一致）。

**风险**：恢复路径上的低概率 native 崩溃/警告；与既有 close 纪律漂移。

**建议**：将 handle 关闭移入 try-with-resources 之前显式执行，或用嵌套 try（内层关 handles、外层关 db）。

**信心水平**：中（顺序事实确定；实际崩溃面依赖 JNI 版本，未实测）。

**误报排除**：`reconstructRocksdbDir` 无 native 资源；`copyColumnFamilyRange` 内 iterator 已用 try-with-resources 正确关闭。

---

### [R5-ST-12] TTL 清理仅在 checkpoint 时执行且无后台 sweep：RocksDB sidecar 无界增长，memory 过期条目可永生

**文件**：
- `/Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/RocksDBKeyedStateBackend.java`（705-746 行）
- `/Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/TtlContext.java`（60、162-174 行）

**证据片段**：
```java
public StateSnapshot snapshotState() throws Exception {
    if (incrementalCheckpointEnabled) {
        cleanupExpiredEntries();          // 只在这里 + 全量路径入口清理
        return snapshotIncremental();
    }
    cleanupExpiredEntries();
    return RocksDBSnapshotSerDe.snapshotState(this);
}
```

**严重程度**：P3（正确性有保障，是性能/内存问题）

**现状**：(1) 过期条目的物理清理只发生在 `snapshotState()`；两次 checkpoint 之间，过期状态仍可被读到吗——不会（读路径 `readEviction`/`isExpired` 每次判断），语义正确；但 TTL sidecar（`timestamps` HashMap / RocksDB 的 ByteBuffer 键 HashMap）只为"过期并清理"的键删除时间戳，长 checkpoint 间隔 + 高键基数时 sidecar 线性增长。`TtlContext.expiredKeys()` 每次全表拷贝（168 行 `new HashMap<>(timestamps)`），O(n) per sweep。内存后端没有任何 sweep 入口——不再被访问的过期条目既不会被清理也不参与快照（快照过滤跳过），在 JVM 内永生（直到作业重启）。(2) `cleanupExpiredEntries` 对每个 TTL 状态全 CF iterator 扫 sidecar 的 expired 集合再逐条 delete——纯 Java 替代 compaction filter，TTL 状态大时每 epoch 的 sweep 成本与状态量线性。

**风险**：长时间运行 + 大键基数 + 低 checkpoint 频率的作业内存缓慢膨胀（memory 后端尤其）；checkpoint 周期性引入 O(状态量) 的清理停顿。

**建议**：memory 后端在 `snapshotState` 之外提供定期 sweep（复用 `expiredKeys()`）；`expiredKeys()` 免拷贝迭代（对象复用问题不大，单线程模型下可直接迭代）。RocksDB 侧可按 `maxParallelism`/状态量设置 sweep 采样或增量游标。

**信心水平**：高（行为确定）；危害等级低（TTL 语义正确性不受影响，恢复语义——"恢复条目无时间戳则首访问给新窗口"——已在 TtlContext javadoc 明确并正确实现）。

**误报排除**：过期条目不会被快照带走（memory `isExpiredForSnapshot` 240-242 行、RocksDB `expiredForSnapshot` 70-76 行均过滤）；`MapState.remove` 不删 sidecar 是 per-map 粒度时间戳的正确行为。

---

### [R5-ST-13] MemoryKeyedStateBackend 的 targetKeyGroupRange 与 restoreAggregateFunctions 在 restore 后不复位

**文件**：`/Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryKeyedStateBackend.java`（417-423、476-479、482-488 行）；RocksDB 侧对称（397-403、794-829 行）

**证据片段**：
```java
@Override
public void restoreState(StateSnapshot snapshot) throws Exception {
    new MemoryStateSerDe(this).restoreState(states, snapshot);   // 使用 targetKeyGroupRange 后不清空
    rebindStateBackends();
}
```

**严重程度**：P3（当前调用时序下无实际触发；属防御性缺失）

**现状**：`setTargetKeyGroupRange` 是"下一次 restore 的过滤器"，restore 完成后字段保留。同一 backend 实例若发生第二次 restore（SupervisionLoop 的 region rebuild 复用 invokable 时理论上可能重入 restore 链），旧 range 仍会过滤——若两次 restore 间并行度已变，条目被静默丢弃（memory）或错写（RocksDB）。`restoreAggregateFunctions` 同样跨 restore 累积（旧 job 的 function 残留到复用实例）。

**风险**：目前所有生产调用点（部署期一次性 restore、rebuildTask 同 range）恰好不受影响；任何未来的"运行期重恢复"特性会踩中。

**建议**：restore 结束（成功或失败）后将 `targetKeyGroupRange` 置 null（单次语义），aggregate functions 在 `close()` 清空。

**信心水平**：高（字段生命周期确定）；触发路径当前不存在，故仅 P3。

**误报排除**：`RocksDBKeyedStateBackend.targetKeyGroupRange` 的增量 restore 路径同样使用该字段，同一建议适用。

---

### [R5-ST-14] TaskLocation 序列化的 "|" 分隔符对 pipelineId 无 sanitize；反序列化 fallback 会用占位 Location 覆盖语义

**文件**：`/Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/CheckpointSerDe.java`（186-191、571-595、702-714 行）

**证据片段**：
```java
public static String taskLocationToString(TaskLocation loc) {
    return loc.getJobId() + "|" + loc.getPipelineId() + "|" + loc.getVertexId() + "|" + loc.getTaskIndex();
}
...
} catch (Exception e) {
    LOG.warn("Failed to parse TaskLocation from key '{}', using fallback", entry.getKey(), e);
    taskLocation = new TaskLocation(jobId, pipelineId, entry.getKey(), 0);   // 占位 Location，taskIndex 恒 0
```

**严重程度**：P3（jobId 已被 `StorageJobIds.sanitizeJobId` 保护；pipelineId 未 sanitize）

**现状**：`resolvePipelineId`（GraphModelCheckpointExecutor 580-582 行）原样返回 `config.getPipelineId()`。pipelineId 含 "|" 时：LocalFile 路径在 `validateId` 处写失败（fail-fast，可接受）；JDBC 路径写入成功、读回 `split("\\|")` 得到 >4 段 → 抛 `ERR_STREAM_INVALID_ARG` → 被 catch 后构造占位 TaskLocation（vertexId=整个原始串、index=0）放入结果 map —— 与其他占位 key 冲突时 `put` 互相覆盖，恢复期 lookup 报出的 "Available keys" 也不再反映真实 checkpoint 内容，误导排障。

**建议**：对 pipelineId 施加与 jobId 相同的 sanitize（或在 `taskLocationToString` 处对含 "|" 的分量 fail-fast）；fallback 分支至少保留原始可解析信息在 detail 中。

**信心水平**：高（拼接/切分逻辑确定）；触发需要非常规 pipelineId。

**误报排除**：`stringToTaskLocation` 对严格 4 段的合法 key 无影响；vertexId 由 `vertex-<n>` 生成器产出，天然无 "|"。

---

### [R5-ST-15] operator-state 反序列化的 accumulator 启发式可能误判用户状态

**文件**：`/Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/CheckpointSerDe.java`（860-901 行）

**证据片段**：
```java
private static boolean isAccumulatorFormMap(Map<String, Object> form) {
    if (form.isEmpty()) { return false; }
    for (Object v : form.values()) {
        if (!(v instanceof Map)) { return false; }
        Object vt = ((Map<String, Object>) v).get("@type");
        if (!(vt instanceof String) || ((String) vt).isEmpty()) { return false; }
    }
    return true;      // "每个值都是带 @type 的 Map" ⇒ 判定为 accumulator form
}
```

**严重程度**：P3

**现状**：operator state 值若恰好是"所有元素都是带 `@type` 字符串键的 Map"的用户 Map（例如透传的带类型标注 JSON 文档），restore 会被误判为 accumulator map，逐值走 `accumulatorFromForm` → `ClassNameValidator.validateAccumulatorClass`（要求 `io.nop.stream.` 前缀 + SimpleAccumulator）→ 大概率 fail-fast 抛 `ERR_STREAM_STATE_ERROR`（恢复失败），小概率（值恰为合法 accumulator 类名）错误重建。写侧对称启发式 `isAccumulatorMap`（要求全部值 instanceof SimpleAccumulator）误判概率低得多——两侧判定强度不对称。

**风险**：特定形状的用户 operator state 无法恢复，报错指向"Failed to recreate accumulator"，归因困难。

**建议**：写侧在 accumulator form 上增加防混淆标记（如 `{"@nopAccumulatorForm":true, items:{...}}` 一层显式包装），读侧以标记而非结构启发式判定。

**信心水平**：高（判定逻辑确定）；触发面窄。

**误报排除**：managed keyed state 不经过该路径（走 MemoryStateSerDe/RocksDBSnapshotSerDe）；空 Map 已排除。

---

### [R5-ST-16] retention 双平面界限不一致：checkpoint 平面按 jobId 全局计数，manifest 平面按 (jobId, pipelineId) 计数

**文件**：`/Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/RetentionCleaner.java`（168-227 行）

**证据片段**：
```java
List<CompletedCheckpoint> allCheckpoints = checkpointStorage.getAllCheckpoints(jobId);   // 跨 pipeline 联合
if (allCheckpoints.size() > maxRetained) {
    for (int i = maxRetained; i < allCheckpoints.size(); i++) {
        ... checkpointStorage.deleteCheckpoint(jobId, old.getPipelineId(), old.getCheckpointId());
```
与 `pruneEpochManifestsForObservedPipelines`（216 行，逐 pipelineId 调 `pruneEpochManifests(jobId, pid, maxRetained)`）。

**严重程度**：P3（当前单 pipeline 部署不受影响）

**现状**：同一 jobId 下多个 pipeline（或多实例复用 jobId）共享 checkpoint 存储时，retention 以 job 全局排序保 N 个 checkpoint，活跃度低的 pipeline 的恢复点可能被另一个 pipeline 的新 checkpoint 挤掉（低于其自身的 maxRetained 保障）；manifest 平面却按 per-pipeline 保 N。两个平面的保留承诺不一致，恢复点与 manifest 可能错位（checkpoint 已删而 manifest 还在，或反之）。

**风险**：多 pipeline jobId 的低频 pipeline 在崩溃后可用的恢复点少于配置值。单 pipeline（当前主要形态）不受影响。

**建议**：checkpoint 平面按 (jobId, pipelineId) 分组各保 N（与 manifest 平面对齐），或两者统一为 job 级并在文档明确。

**信心水平**：高（两个平面的计数口径均直接可读）。

**误报排除**：单 pipeline 作业下两个口径等价；`deleteCheckpoint` 带上了 old.getPipelineId()，删除本身不会误删他 pipeline 的目标行——问题只在"计数与排序口径"。

---

### [R5-ST-17] JdbcCheckpointStorage 两处次要降级/一致性问题

**文件**：`/Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/JdbcCheckpointStorage.java`

**证据片段 1**（810-826 行，方言解析失败降级）：
```java
} catch (Exception e) {
    LOG.debug("Failed to resolve dialect for upsert strategy, using generic fallback", e);
}
return UpsertDialect.GENERIC;
```
**证据片段 2**（233-259 行，deleteAllCheckpoints 双表两段式）：
```java
jdbcTemplate.executeUpdate(sql);          // 先删 stream_checkpoint（独立执行）
...
jdbcTemplate.executeUpdate(epochSql);     // 再删 stream_epoch_manifest（无共同事务）
```

**严重程度**：P3

**现状**：(1) `getDialectForQuerySpace` 抛错时静默降到 GENERIC（仅 debug 日志）。GENERIC 在 PostgreSQL 上功能正确（注释解释过两段式事务的必要性），故为可接受的降级，但 infra 层故障的可观测性只有 debug 级。(2) `deleteAllCheckpoints` 两条 DELETE 不在共同事务中：第一条成功第二条失败时，checkpoint 行已清而 manifest 残留——恰好是该 javadoc 自己警告的"stale manifest 会被 loadLatestEpochManifest 选中 → stale restore"状态（该路径仅用于清理/重置场景，风险受限）。SQL 注入面检查：表名为常量、全部参数占位符化、`pruneEpochManifests` 的 IN 列表为 `?` 占位拼接——**未发现注入**（grep `" + TABLE_NAME + "` 均为常量拼接；无用户输入进 SQL 文本）。

**建议**：方言解析失败升级为 WARN；deleteAllCheckpoints 包进单个 `runInTransaction`（PostgreSQL DDL 无关，两条 DELETE 可同事务）。

**信心水平**：高。

**误报排除**：GENERIC 两段式在 PG 的事务中止问题已被 `runInsertOrUpdateSeparateTxns` 的独立事务设计规避（895-911 行），不重复立项；`isDuplicateKeyException` 的启发式覆盖 H2 23505 / MySQL 1062 文案 / PG unique violation 文案，漏判后果是 INSERT 异常上抛 fail-fast（安全方向）。

---

### [R5-ST-18] 增量快照共享 SST 依赖 task-local 绝对路径与 cp-N 目录留存，重启后 id 计数器复位可能删除仍被引用的本地文件

**文件**：
- `/Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/incremental/RocksDBIncrementalSnapshotStrategy.java`（63-124、154-185 行）
- `/Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java`（633-665 行）

**证据片段**（strategy doSnapshot，98 行 + 120 行）：
```java
sstHandles.add(new SharedStateHandle(hash, entry.toAbsolutePath().toString(), size));   // task-local 绝对路径
...
pruneOldCheckpoints(checkpointBaseDir, checkpointId, localRetention());   // 只保留最新 K=2 个 cp-N 目录
```
**证据片段**（coordinator buildAndMaterializeSegments，652-654 行）：
```java
if (!segmentStore.segmentExists(hash)) {
    segmentStore.storeSegment(java.nio.file.Path.of(handle.getFilePath()), hash);   // 从 task-local 路径拷入共享 store
}
```

**严重程度**：P3（单机嵌入式模式下时序成立；跨重启/计数器复位时存在删除竞态窗口）

**现状**：SST 的持久副本靠 coordinator 在 persist 阶段从 `handle.getFilePath()`（task-local `cp-N/native/` 绝对路径）拷入 segment store；task 侧 F-02 retention 只保最新 K=2 个 cp-N 目录。正常时序（maxConcurrent=1，epoch N durable 后才触发 N+2 的 pruning）是安全的——注释亦给出该论证。两个未覆盖的窗口：(1) coordinator 拷贝发生在**异步 persist 线程**，若同一 epoch 内 persist 排队很久而任务侧连续完成了两个新快照（maxConcurrent>1 配置下允许），K=2 的本地目录可能先于拷贝被删，`storeSegment` 抛 IOException → 该 epoch persist 失败（fail-fast，安全但损失一个 checkpoint）；(2) 作业在同一机器、同一 dbPath 重启后，`incrementalSnapshotIdCounter`（AtomicLong，从 0 起）复位，新一轮 `cp-1` 会 `deleteIfExists` 掉上一轮的 `cp-1`（72-73 行）——上一轮 checkpoint 的 handle 若仍指向这些文件且尚未拷入共享 store（上一轮在拷贝前崩溃），重启后的 restore 找不到 SST → `reconstructRocksdbDir` 抛 "Shared SST segment missing"（fail-fast，无静默）。两处最终都是响亮失败而非数据损坏，故 P3。

**风险**：增量 checkpoint 在多并发/崩溃-重启场景下的恢复成功率下降；错误信息可定位但需要人工干预。

**建议**：coordinator 在 register 时同步拷贝（或将 task-local 目录的删除条件改为"该 hash 已存在于 segment store"）；重启后首轮快照的 `cp-{id}` 目录名加入 JVM 实例前缀避免与旧目录冲突。

**信心水平**：中（时序推演完整但未复现；两窗口最终都以 typed 错误收场）。

**误报排除**：`SharedStateRegistryImpl` 的 ref-count 配对由单 coordinator 驱动（javadoc 已裁决 stale-unregister 竞态不成立）；restore 侧对 segment 缺失/内容损坏均 fail-fast（F-03），无静默路径。

---

## 三、已检查、判定无问题的重点项（误报排除汇总）

| 检查项 | 结论 |
|---|---|
| SQL 注入（JdbcCheckpointStorage 表名/列名/值） | 全常量表名 + 参数占位符，`pruneEpochManifests` IN 列表占位符化——干净。 |
| JDBC 连接泄漏 | 全部经 `IJdbcTemplate` 托管；无手工 Connection/ResultSet。 |
| iterator/Options 泄漏（RocksDB） | `RocksIterator` 均在 try-with-resources；`openDB` 的 `Options`（208 行）、`restoreRangeInto` 的 `Options/listOpts`（183 行）均已关（后者 CF handle 顺序问题单列 R5-ST-11）。`RocksDBIncrementalSnapshotStrategy` 的 `Checkpoint` 与 `Files.list/walk` Stream 均关闭。 |
| barrier 对齐 / partial checkpoint | tracker 按 epoch 独立 ACK（duplicate ACK 按 operator 去重，182-186 行注释明确防"N-1 真实 ACK + 1 重复完成 epoch"的 exactly-once 破坏）；snapshot 错误经 abortCallback 路由到正确 epoch 并阻止该 ACK 计为成功（189-196 行）。Tracker 本身不做通道对齐（对齐在 InputGate），abort 时 epoch-precise 释放（TaskCheckpointWiring.registerLocalAbortHandler 323-372 行），D3 条件取消逻辑自洽。 |
| savepoint vs checkpoint 差异 | trigger 侧 savepoint 不受 minPause 节流（Coordinator 374-384 行显式只限 `CheckpointType.CHECKPOINT`），maxConcurrent 仍生效——与 Flink 语义一致；savepoint 后 `materializeKeyGroupOwnership` 落盘 range 归属。差异处理主体正确（savepoint 恢复 miss 的静默 fresh start 已单列 R5-ST-07）。 |
| ACK null-state 丢状态 | tracker 的完成回调恒传非 null `state.snapshot`（trigger 时构造），`PendingCheckpoint.acknowledgeTask` 的 `state != null` 分支生产路径不可达——防御性代码，无发现。 |
| checkpointSuccessMap / failedCommitParticipants 滞留 | R4-A2' 修复完整（见"历史修复验证"），grep 全位点核对。 |
| TTL 恢复语义 | 恢复条目无时间戳 → 首访问授予新窗口（OnCreateAndWrite），快照过滤与读路径判定一致；TTL 配置不变时重复 getState 不重置 sidecar（memory 381-386 行、rocksdb 645-652 行对称实现）。 |
| MapState 二进制 key 前缀扫描歧义 | TLV 编码下不同 base key 互不为前缀（keyLen 字段隔离），`collectMap`/`deleteByPrefix` 的 startsWith 扫描安全；快照分组键的歧义是 Java 字符串层问题（R5-ST-04）。 |
| `copyColumnFamilyRange` 范围扫描 | KeyGroupRange 为半开 `[start,end)`（javadoc 39-40 行），`compareBytes(key, endPrefix) >= 0 break` 边界正确；big-endian 前缀保证字典序=数值序。 |
| SysDaoWireCodec 类型往返 | send 侧先 `DataPlaneWireSupport.toWireMap` 扁平化再包 ApiRequest，receive 侧 Map→bean 重建；未知类型返回 null（P3 级，仅理论路径，未立项——消息丢失会表现为 barrier 超时而非静默错值）。 |
| CheckpointIDCounter 单调性 | restore 后 `advanceCheckpointIdCounterAfterRestore` 单调推进（983-990 行），manifest 路径与 checkpoint 路径共用，防"shadow window"回滚。 |
| `normalizeNumbersDeep` 精度 | 整值固定点用原始 `num.longValue()` 而非 double，>2^53 Long 不失真；两侧同管道，checksum 结构性收敛。 |

## 四、计数结论

- **发现总数：18**（P1×3、P2×4、P3×11）。
- **grep 模式与排除项**：
  - 错误处理一致性：`grep -rn "new StreamException(\"" nop-stream/*/src/main/java --include=*.java` → 18 处命中，其中 17 处属 nop-stream-rocksdb 状态子系统（立项 R5-ST-09），1 处 `nop-stream-cep/.../DeweyNumber.java:115` 为 CEP NFA 内部、不属状态子系统（排除）；`grep -rn "catch (Exception" <state dirs>` → 24 处，逐一归类为"包装重抛 / warn+降级（Codec/migration 的 best-effort 已有裁决注释）/ fail-fast"，未发现新的 swallow-return-empty 模式（R4-N4 同族仅剩 R5-ST-06 serde 层一处）。
  - A2' 同族滞留检查：`grep -n "checkpointSuccessMap\\|failedCommitParticipants" CheckpointCoordinator.java` → put×1（1212）、remove×5、clear×2，全部路径闭环。
  - 容器 codec 覆盖面：`grep -rn "ContainerValueCodec.encode\\|ContainerValueCodec.decode" nop-stream --include=*.java` → 6 处全部 MapState 相关（R5-ST-05 的依据）。
  - 历史已修复项（R4-N4 / R4-A2' / checksum 往返 / incremental fail-fast）验证通过，未重复立项；Deferred 项（A3-A6/B2/B7/G1-G3/D4/A11）未重开。
