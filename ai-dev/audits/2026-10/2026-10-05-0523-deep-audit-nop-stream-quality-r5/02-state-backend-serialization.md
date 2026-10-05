# 深度审计 R5 报告 02：nop-stream 状态后端与序列化子系统

- **审计日期**: 2026-10-05
- **HEAD SHA**: `bd089444f5c2b1fcd2c9a964ce909bac774a83d9`（2026-10-05 00:07:55 +0800）
- **范围**: nop-stream-core 的 `io.nop.stream.core.common.state`（memory backend、keyed state、TTL、shard/key-group）、`io.nop.stream.core.checkpoint`（participant/storage/incremental）、`io.nop.stream.core.common.typeinfo`/`typeutils`、`io.nop.stream.core.execution.materialization`；nop-stream-rocksdb 全模块；nop-stream-runtime 的 `io.nop.stream.runtime.checkpoint`（协调器、storage、reshard、metrics）；以及所有 SerDe / savepoint restore / rescale / reshard 路径。附带核查 connector-jdbc 的 2PC 台账（审计要点 8）。
- **方法**: 只读源码审计。先读 AGENTS.md、`docs-for-ai/03-modules/nop-stream.md`、`ai-dev/design/nop-stream/01-architecture-baseline.md`；随后逐文件通读范围内约 12.2k 行主代码（shard/key-group 公式链 8 个调用点逐一核对；memory SerDe 全文；CheckpointSerDe 全文；Coordinator 生命周期段；PendingCheckpoint 全文；RocksDB backend/encoder/快照/增量全文；JDBC/File storage 全文；reshard/rescale 装配器全文），并交叉核对 restore/重材料化/reshard 每个入口的 key→key-group→subtask 公式链与 keyType 还原路径；E2E 测试保护力按"是否真实作业产出快照"口径抽查。禁止修改产品代码；未运行 maven/测试。

## 总体结论

key→key-group→subtask 公式链本身已高度收敛：记录路由（`DataStreamImpl.KeySelectorPartitioner`）、memory 属主（`MemoryKeyedStateBackend.routeKey`）、RocksDB 属主（`computeKeyGroupId`）、restore 过滤（`KeyGroupRangeRestoreFilter`）、reshard（`KeyGroupReshard`）全部经 `KeyGroupAssignment` 单点公式，AR-01 parity 成立；Enum identity-hash 缺陷的修复（按 `name()` 哈希）完整且未发现残留入口；JSON 反序列化后的 key 类型还原（ST-02 rematerializer）在 WindowOperator/CepOperator 主路径上闭环。checkpoint 生命周期（PendingCheckpoint 双 CAS、N1 status guard、forceFail/abort 的 future 完成语义、增量段回滚）经 plan 369 修复后未发现新的端点缺陷。剩余问题集中在四个结构面：**异步持久化与 memory 快照浅引用的竞争（P0）**、**Object.class 声明的 keyed 后端绕过 ST-02 重材料化（P1）**、**rescale 重建丢弃 keyType 信封头（P2）**、**增量快照本地保留窗口在 persist 失败后的可恢复性（P2）**。

---

### [ST-01] 异步 checkpoint 持久化直接序列化 memory 后端的活对象图——barrier 后变异可泄入快照并撕裂 JSON
- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/checkpoint/CheckpointConfig.java:36`；`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:640-661, 692-708`；`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryStateSerDe.java:239-249, 650-652, 690-713`；`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/CheckpointSerDe.java:756-763`
- **证据片段**:
  ```java
  // MemoryStateSerDe.snapshotKeyedState — entry 持有活对象引用（无 copy-on-write）
  Map.Entry<TypedNamespaceAndKey, ?> e = (Map.Entry<TypedNamespaceAndKey, ?>) eObj;
  ...
  entry.put("namespace", serializeNamespace(e.getKey().namespace));
  entry.put("key", serializeKey(unwrapStorageKey(e.getKey().key)));   // serializeKey 原样返回活 key
  entryWriter.write(entry, e.getValue(), valueSer);                    // ser==null 时 value 原样放入
  ```
  ```java
  // CheckpointCoordinator.completePendingCheckpoint — 默认异步，快照图交持久化线程
  // async path: hand storage I/O + completion side effects to the dedicated persist executor.
  executor.submit(() -> executePersistAsync(completed, pending, manifest));
  // CheckpointSerDe.serializeTaskStateSnapshot:762 — keyedStates 活 map 直接入序列化图
  map.put("keyedStates", snapshot.getKeyedStates());
  ```
- **严重程度**: P0
- **现状**: `DEFAULT_ASYNC_SNAPSHOT_ENABLED = true`（CheckpointConfig.java:36）。对齐完成后任务线程 ACK 即返回并恢复消费；持久化线程随后对整个 keyedStates 图做 `JsonTool.serialize`。MemoryStateSerDe 快照的 entry 对 value/key 只持有**活对象引用**（`MemoryValueState.update` 直接 `storage.put(k, value)`，`value()` 返回存储内同一实例）；`CheckpointCoordinator.java:617-621` 注释断言的"manifest 与 completed checkpoint 是 immutable"前提对 memory 后端**不成立**。window 聚累器（如 `long[]` ACC 原地 `add`）在 ACK 后继续变异快照引用的同一数组。
- **风险**: ① exactly-once 契约破坏——barrier 之后的同 key 原地变异泄入快照，恢复后状态与 source 位点不一致（重复聚累/错误聚合），静默；② 物理撕裂——序列化期间 HashMap 扩容/链表重组可致 `ConcurrentModificationException`（持久化失败，checkpoint 反复失败）或产出结构损坏但可解析的 JSON（静默坏数据）。Flink heap 后端为此强制 copy-on-write 或同步快照，此处两者皆无。
- **建议**: 三选一并留痕：(a) `snapshotKeyedState` 对无自定义 serializer 的值做深拷贝（JSON round-trip 或 descriptor 声明类型感知拷贝）；(b) memory 后端在 async 模式下退回同步持久化（`isAsyncSnapshotEnabled=false` 联动）；(c) 短期缓解——在文档与 `CheckpointConfig` javadoc 显式声明"async + memory + 可变值对象"为不支持组合并加 tripwire 日志。最小正确修复是 (a)。
- **信心水平**: 确定（结构性竞争成立；具体撕裂表现为概率性）
- **误报排除**: 这不是"看起来不优雅"：`completePendingCheckpoint` 的注释自证其正确性前提是"immutable snapshot data"（697 行"operating on immutable snapshot data"），而 memory SerDe 交付的是活引用——设计前提与实现直接矛盾，且 async 默认开启（非边缘配置）。RocksDB 路径不受影响（快照值是从 SST 反序列化的新对象），故本条限定 memory 后端。
- **复核状态**: 未复核

### [ST-02] ProcessOperator / EquiJoinOperator / OverWindowOperator 以 Object.class 声明 keyed 后端，ST-02 key 重材料化退化为恒等——Long/Date/bean key 恢复后静默不可达
- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/ProcessOperator.java:57-62`；`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/join/EquiJoinOperator.java:123-127`；`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/OverWindowOperator.java:105-110`；`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/shard/StateKeyRematerializer.java:60-66`；`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/TypedNamespaceAndKey.java:33-38`
- **证据片段**:
  ```java
  // ProcessOperator.open() — 声明 keyType = Object.class
  // Key type Object.class mirrors the CepOperator precedent —
  // key classes are JSON-serializable values (e.g. String keys).
  if (keyedStateBackend == null && stateBackend != null) {
      keyedStateBackend = stateBackend.createKeyedStateBackend(Object.class);
  // StateKeyRematerializer.rematerializeKey — Object.class 恒等返回
  if (keyType == null || keyType == Object.class || keyType.isInstance(key)) {
      return key;
  ```
- **严重程度**: P1
- **现状**: AR-01/ST-02 修复链的前提是后端声明**真实 key 类**：`MemoryStateSerDe.deserializeKey` 用 `backend.getKeyType()` 重材料化。WindowOperator（`keyClass`，:492）与 CepOperator（`resolveEffectiveKeyClass`）满足；但 ProcessOperator/EquiJoinOperator/OverWindowOperator 三处自装配用 `Object.class`。JSON 持久化把 `Long(123)` 收窄为 `Integer(123)`；恢复时 `rematerializeKey(raw, Object.class)` 恒等放行 Integer；而 `TypedNamespaceAndKey.equals` 对 key **类敏感**（33-36 行显式 `!key.getClass().equals(...)` 即 false），活路径 `setCurrentKey(Long)` 的查找全部 miss——恢复的 keyed state 静默孤儿化。Date/bean key 在 Object.class 下退化为 LinkedHashMap 存活，同样 miss。
- **风险**: keyed ProcessFunction / equi-join / over-window 在非 String key 下每次 checkpoint 恢复都从空状态重启（无任何报错）；与 RocksDB 后端行为不一致（RocksDB 存储键是 canonical JSON 字节，`"123"` 两边字节相同，天然自愈），同一作业换后端结论反转。
- **建议**: 三处 `createKeyedStateBackend(Object.class)` 改为真实 key 类：ProcessOperator/EquiJoinOperator 可仿照 CepOperator 的 `restoredKeyClass` 机制从恢复快照头或 KeySelector 推导；短期至少在快照头写真实 key 类而非 `java.lang.Object`，并让 memory 恢复路径消费它（见 ST-04）。另在 `ProcessOperator` 的注释处去掉"e.g. String keys"这一未强制的前提。
- **信心水平**: 确定（代码路径完整可推演；恢复后 miss 是类敏感 equals 的直接后果）
- **误报排除**: 这是历史缺陷族（AR-01"恢复路径用 JSON 原生 key 计算"）的**残留入口**而非已修复点的误报：修复点本身（rematerializer、WindowOperator/CepOperator 声明、restore-filter 的 keyType 感知）验证为完整；本条指出的是修复链的三个未覆盖调用点——`Object.class` 分支（StateKeyRematerializer:64）是重材料化中唯一不设防的恒等通道。
- **复核状态**: 未复核

### [ST-03] rescale 重建丢弃快照信封的 keyType 头——对重 scale 产物再跑 reshard/rescale 时 ST-02 重材料化静默失效
- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/RescaleStateAssembler.java:207-234`；`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/reshard/MaxParallelismReshardMigration.java:351-365`；`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/RescaleStateAssembler.java:218-223`
- **证据片段**:
  ```java
  // RescaleStateAssembler.buildRescaledTaskState — 只回填 "states"，keyType 头未拷贝
  if (mergedStates.isEmpty()) continue;
  Map<String, Object> data = new LinkedHashMap<>();
  data.put("states", mergedStates);
  merged.putKeyedState(entry.getKey(), new StateSnapshot(data));
  // MaxParallelismReshardMigration.redistributePools — keyType 缺失则 fallback null
  Map<Integer, Map<String, Object>> redistributed = KeyGroupReshard.redistributeStates(
          pool.globalStates, newMaxParallelism, newParallelism,
          StateKeyRematerializer.resolveSnapshotKeyType(pool.envelope, null));
  ```
- **严重程度**: P2
- **现状**: `MemoryStateSerDe.snapshotState` 总是写信封头 `keyType`（:109），`StateKeyReshard/KeyGroupRangeRestoreFilter` 的 ST-02 语义依赖它。但 rescale 装配器重建的 keyed 信封只含 `states`——`keyType` 头在产物中丢失。首次 rescale 的**输入**仍有头（过滤正确）；但产物作为下一次离线 reshard（或再次 rescale）的输入时，`resolveSnapshotKeyType(envelope, null)` 解析为 null → `rematerializeKey` 恒等 → 用 JSON 原生形态哈希分桶。
- **风险**: Date/bean key 的离线 reshard 在"上一轮产物"上会把条目分到错误 owner；错误条目随后在恢复时被 `KeyGroupRangeRestoreFilter` 按 range **静默过滤掉**（非 fail-fast）——两次变换复合后表现为无害的单次迁移，实际丢数据。String/数值主键作业不受影响。
- **建议**: `buildRescaledTaskState` 重建信封时保留原 envelope 的顶层 `keyType`（与 `MaxParallelismReshardMigration.redistributePools:362` 的 `new LinkedHashMap<>(pool.envelope)` 对齐——后者已正确保留）；并在 `resolveSnapshotKeyType(envelope, null)` 的两处调用点对"信封缺 keyType 且存在非 String key 条目"打 WARN 或拒绝。
- **信心水平**: 确定（信封头丢失可从代码直接推演；触发需要非原始类型 key + 变换链）
- **误报排除**: 不是同类误报：ST-02 的修复验证了"过滤前重材料化"，本条指出修复后的**产物不满足修复的输入契约**——丢失发生在写侧（rescale 装配器），与读侧修复正交，属于修复完备性缺口。
- **复核状态**: 未复核

### [ST-04] memory 与 RocksDB 恢复路径对 keyType 的解析规则分叉——memory 忽略快照头，RocksDB 消费它
- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryStateSerDe.java:278-284, 675-677`；`nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/RocksDBSnapshotSerDe.java:352-357, 407-421`
- **证据片段**:
  ```java
  // MemoryStateSerDe.restoreState — 只用后端声明 keyType
  KeyGroupRange range = backend.getTargetKeyGroupRange();
  if (range != null) {
      statesMap = KeyGroupRangeRestoreFilter.filterKeyedStates(statesMap, range,
              backend.getMaxParallelism(), keyType);        // keyType = backend.getKeyType()
  // RocksDBSnapshotSerDe.resolveRestoreKeyType — 声明为 Object 时回落快照头
  Class<?> declared = backend.getKeyType();
  if (declared != null && declared != Object.class) {
      return declared;
  }
  return StateKeyRematerializer.resolveSnapshotKeyType(stateData, declared);
  ```
- **严重程度**: P2
- **现状**: 同一 ST-02 契约有两套实现：RocksDB 在后端声明为 Object.class 时回落到快照头的 `keyType` 字段（`resolveRestoreKeyType`），memory 侧的过滤（:280）与 `deserializeKey`（:676）都只看后端声明、不消费快照头。声明为真实类的主路径两者一致；分叉只在 Object.class 声明（ST-02 的三个算子）+ 头中带真实类名的快照上显形。`StateKeyRematerializer.resolveSnapshotKeyType` 在 memory 主代码中零调用。
- **风险**: 同一快照跨后端恢复行为不一致：RocksDB 能借助头重材料化，memory 不能——问题被掩盖为"换后端才复现"；另使 ST-02/ST-03 的修复验证口径分裂（修 RocksDB 不修 memory）。
- **建议**: `MemoryStateSerDe` 复用 `resolveRestoreKeyType` 同款规则（声明为 Object.class 时回落 `resolveSnapshotKeyType(stateData, keyType)`），把该解析收敛为 `StateKeyRematerializer` 上的单一静态方法，两侧调用同一点。
- **信心水平**: 确定
- **误报排除**: 不是风格问题：两侧对同一持久化格式承担同一恢复契约，`MemoryStateSerDe` 顶部注释自称与 RocksDB "byte-compatible"，但 keyType 解析规则已分叉——这是跨后端一致性缺陷而非实现偏好。
- **复核状态**: 未复核

### [ST-05] 增量快照本地保留（F-02）在 persist 失败后会删掉最后一个可恢复 checkpoint 的 non-sst 目录——作业变为不可恢复（响亮失败）
- **文件**: `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/incremental/RocksDBIncrementalSnapshotStrategy.java:113-124, 154-185`；`nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/incremental/RocksDBIncrementalRestore.java:102-148`；`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:749-776`
- **证据片段**:
  ```java
  // doSnapshot — 保留窗口 K=2 的前提假设写在注释里：
  // K=2 covers "last durable + one in-flight" under maxConcurrentCheckpoints=1.
  pruneOldCheckpoints(checkpointBaseDir, checkpointId, localRetention());
  // pruneOldCheckpoints — 按 id 排序删最旧的
  for (int i = 0; i < ids.size() - retention; i++) { ... deleteIfExists(victim); }
  ```
- **严重程度**: P2
- **现状**: task 侧 `cp-{id}` 目录的 non-sst 副本（MANIFEST/CURRENT/WAL——`reconstructRocksdbDir` 重建可打开 DB 的唯一来源）是**任务本地唯一拷贝**，段存储只共享 SST。保留窗口按"每个快照最终都会 durable"假设取 2。若 checkpoint N 的持久化失败（`onCompletePersistFailure`，仅回滚段引用计数，不回滚本地保留决策），其 cp-N 目录不会被恢复语义引用；下一个快照 N+1 的 prune 会把 cp-(N-1)（= 最后一个 durable 且可恢复的目录）挤出窗口。
- **风险**: 之后进程重启恢复"最新 durable checkpoint"时，`result.getNonSstDir()` 指向已被删除的目录 → `reconstructRocksdbDir` 空 SstNameMap → `openReadOnly` 失败（typed `ERR_STREAM_STATE_ERROR`）。不产生静默数据丢失（fail-loud），但作业在存储抖动一次 + 再跑一次快照后即不可恢复——增量模式的可用性回归，且与"K=2 覆盖 last durable"的注释承诺矛盾。
- **建议**: 保留决策由 coordinator 驱动（durable 完成回调后释放旧目录），或 task 侧保留窗口对"最后 N 个**已确认 durable**"的 id 做 ACK 驱动清理；短期方案：persist 失败路径通知 backend 暂停 prune 一轮。
- **信心水平**: 很可能（时序由三段代码共同决定，未运行时验证；但窗口组合在代码层面成立）
- **误报排除**: 不是"磁盘泄漏"类美观问题：后果是恢复路径响亮失败、作业无法拉起，属于真实的可恢复性缺陷；F-02 修复本身（消除线性磁盘增长）是对的，本条指出其安全性论证漏了 persist 失败分支。
- **复核状态**: 未复核

### [ST-06] reshard 迁移的 E2E 仍以手工构造、公式对称的快照为输入——R4"真实作业快照"缺陷族在该工具面未闭合
- **文件**: `nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/checkpoint/reshard/TestMaxParallelismReshardMigrationE2E.java:63-87, 92-120`
- **证据片段**:
  ```java
  /** Build a keyed ValueState stateData map for the given (key -> value) entries. */
  private static Map<String, Object> buildKeyedStateData(Map<String, Long> entries) {
      ...
      en.put("namespace", "_default_");
      en.put("key", e.getKey());
      en.put("value", e.getValue());
  ...
  // stageSavepoint 用被测同款公式分桶：
  int gid = KeyGroupAssignment.assignToKeyGroup(e.getKey(), OLD_MAX_P);
  int sub = KeyGroupAssignment.assignKeyGroupToSubtask(gid, OLD_MAX_P, PARALLELISM);
  ```
- **严重程度**: P2
- **现状**: 测试输入的 keyed 条目是手工拼装的 map，且分桶使用与被测迁移**同一** `KeyGroupAssignment` 公式；key 仅 String。公式若发生"绝对正确性"回归（如 stableHash 改变、keyType 还原失效），staging 与 verification 同侧同变，断言仍绿。真实作业产出快照的 E2E 存在于 restore 面（`TestE2EWindowAggregateRestore` 经 `env.execute` 两阶段真跑，验证为真实链路），但 **reshard 工具面没有**对应输入。
- **风险**: reshard 迁移对非 String key（Long 收窄、Enum、Date/bean——恰是 ST-02/ST-03 的触发面）的绝对路由正确性无测试保护；ST-03 类型的信封头丢失回归在该测试上不可见（手工信封恰好带 keyType 头）。
- **建议**: 增加一条"真实作业 savepoint → reshard → 恢复"测试：用 `env.execute` + `EXPORT_SAVEPOINT` 产出含非 String key 的 savepoint，喂给 `MaxParallelismReshardMigration.migrate`，对恢复后的逐 key 值做绝对断言（不依赖 staging 公式）。
- **信心水平**: 确定
- **误报排除**: 为什么这次不是 R4 轮同类误报：R4 轮误报是"把单测命名含 E2E 当缺陷、而真实链路 E2E 已存在"；本条逐一定位了测试文件与构造函数，确认 reshard 面的 E2E 输入**确实**全部来自 `buildKeyedStateData`/`stageSavepoint` 手工装配（63-120 行），且与 restore 面 `TestE2EWindowAggregateRestore` 的真跑形态形成对照——缺口是局部的、有明确定位的。
- **复核状态**: 未复核

### [ST-07] MaxParallelismReshardMigration 的 oldMaxParallelism 参数自称"consistency check"但从未被校验
- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/reshard/MaxParallelismReshardMigration.java:79-81, 102-114, 453-472`
- **证据片段**:
  ```java
  * @param oldMaxParallelism maxParallelism in effect when the old savepoint
  *                          was written (consistency check)
  ...
  private static void validateArgs(int oldMaxParallelism, int newMaxParallelism, int newParallelismOverride) {
      if (oldMaxParallelism < 1) { throw ... }          // 只查 >= 1
      if (newMaxParallelism == oldMaxParallelism) { throw ... }
  }                                                      // 无任何与 checkpoint 记录值的比对
  ```
- **严重程度**: P3
- **现状**: javadoc 声明该参数用于一致性校验；实际 `validateArgs` 只做范围检查，`reshardCheckpoint` 全程未把 `oldMaxParallelism` 与快照内持久化的 `TaskEpochSnapshot.maxParallelism`（`materializeKeyGroupOwnership` 已写入并经 CheckpointSerDe 持久化）比对。`StreamMaintenanceMain reshard oldMaxParallelism=<n>` 的 CLI 值填错时静默通过。
- **风险**: 运维以错误 old 值调用时得不到任何警告；由于重分组只用 new 值重哈希，错值不影响迁移结果本身——危害限于"校验承诺落空"与排障误导。CLI 契约（docs-for-ai nop-stream.md reshard 命令示例）暗示该值有意义。
- **建议**: `reshardCheckpoint` 读快照 taskStates 中已物化的 `maxParallelism`，与参数不符即 typed 拒绝；或删除参数与文档中的"consistency check"措辞，二选一，不保留假接口。
- **信心水平**: 确定
- **误报排除**: 不是文档洁癖：这是"声称存在的防线不存在"的契约缺口（No-Silent-No-Op 的反例——静默接受本应校验的输入），属于真实维护成本。
- **复核状态**: 未复核

### [ST-08] 持久化的 key-group ownership 元数据（parallelism/maxParallelism/range）无任何 restore 侧消费者，javadoc 承诺落空
- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/checkpoint/TaskEpochSnapshot.java:33-51, 116-163`；`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/CheckpointSerDe.java:764-774, 811-825`；`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/execution/RescaleStateAssembler.java:296-328`
- **证据片段**:
  ```java
  // TaskEpochSnapshot javadoc
  * taken. Together with {@link #maxParallelism} it lets the restore path
  * ...
  // CheckpointSerDe — 写入并读回：
  map.put("parallelism", epoch.getParallelism());
  map.put("maxParallelism", epoch.getMaxParallelism());
  ...
  // restore 侧实际：resolveMaxParallelism(execPlan, checkpointPlan) 从活 plan 重推导，
  // 全仓库主代码无 getKeyGroupOwnership/getMaxParallelism()（快照对象）的消费点
  ```
- **严重程度**: P3
- **现状**: `materializeKeyGroupOwnership` 把每个 subtask 的 ownership 物化进快照，CheckpointSerDe 双向持久化；但恢复/rescale 全部经 `resolveMaxParallelism` 从活执行计划重推导，快照记录值在主代码零消费（grep 仅 CheckpointSerDe 读写自身）。字段 javadoc 声称"lets the restore path ..."与实现不符。
- **风险**: 误导后续维护者以为存在"快照 maxParallelism 与活 plan 不一致即拒绝"的防线（实际没有，见 ST-07 关联）；死元数据长期演进漂移。
- **建议**: 要么补上消费点（restore 时快照 recorded maxParallelism ≠ 活 plan → typed 提示走 reshard 工具），要么修正 javadoc 并把字段标注为观测面元数据。
- **信心水平**: 确定（零消费点经全量 grep 验证）
- **误报排除**: 不是"未使用的字段该删"式洁癖：javadoc 对行为作出了错误承诺，属于文档-实现漂移；本次以 live code 为准裁定的口径下，承诺不成立即是缺陷。
- **复核状态**: 未复核

### [ST-09] 原生反序列化白名单对 `java.*`/`javax.*` 整前缀放行——含 javax.naming/javax.script 等敏感包，信任边界路径的攻击面偏宽
- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/typeutils/StreamDeserializationFilter.java:54-59, 103-118`
- **证据片段**:
  ```java
  private static final List<String> BASELINE_PREFIXES = List.of(
          "io.nop.",
          "java.",
          "javax.",
          "jakarta.",
          "[");
  ...
  for (String prefix : allowed) {
      if (name.startsWith(prefix)) { return ObjectInputFilter.Status.ALLOWED; }
  }
  ```
- **严重程度**: P3
- **现状**: F-10a 的 JEP 290 过滤器对 `javax.*` 无差别放行（javax.naming、javax.script、javax.management 等历史上被反序列化 gadget 利用的包均在基线内），`java.*` 同理宽于"核心值类型"所需。检查点字节被明确定位为跨信任边界（共享 JDBC 存储/备份恢复）。
- **风险**: 若 classpath 上存在仅由基线前缀类构成的 gadget 链，过滤器不设防。可利用性依赖具体 classpath 与链构造，属纵深防御缺口而非已证可利用。
- **建议**: 把 `java.*`/`javax.*` 收窄为按需枚举（java.lang/java.util/java.math/java.time/...），或对 javax.* 子前缀排除 naming/script/management/rmi；与 ClassNameValidator 的前缀表做同源收敛，避免两份白名单漂移。
- **信心水平**: 有趣的猜测（缺口确定存在；实际可利用性未验证）
- **误报排除**: 不是"白名单本来就是粗粒度"的常识豁免：该文件自身把检查点字节定义为信任边界输入（22-30 行 javadoc），前缀集的可收窄空间是可量化的攻击面差异，属于安全纵深的真实短板，而非风格。
- **复核状态**: 未复核

### [ST-10] RocksDB 全量恢复 clearAllStates 全键物化 + 逐键 delete——大状态下的恢复期堆尖峰与慢恢复
- **文件**: `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/RocksDBSnapshotSerDe.java:423-435`
- **证据片段**:
  ```java
  private static void clearAllStates(RocksDBKeyedStateBackend<?> backend) throws Exception {
      for (ColumnFamilyHandle cf : backend.getCfHandles().values()) {
          try (RocksIterator it = backend.getDb().newIterator(cf)) {
              List<byte[]> keys = new ArrayList<>();
              for (it.seekToFirst(); it.isValid(); it.next()) {
                  keys.add(it.key());                    // 全部 key 物化进堆
              }
              for (byte[] key : keys) {
                  backend.getDb().delete(cf, key);       // 逐键 delete，无 WriteBatch
              }
  ```
- **严重程度**: P3
- **现状**: 恢复前清空 DB 的路径把每个列族的全部复合键（含 namespace JSON + key JSON 字节）一次性物化为 Java `List<byte[]>`，再逐键原生 delete。同文件 `deleteByPrefix`（backend :746-767）同样逐键。一个 GB 级 keyed 状态恢复时，堆占用与键数成正比，恢复时长为 O(N) 次原生 JNI 调用。
- **风险**: 大状态 + 多列族下恢复期 OOM 风险与分钟级恢复窗口（恢复路径在任务启动关键路径上）。正确性无损（fail-fast 语义完整），为可量化但局部的性能/资源缺陷。
- **建议**: 改用 `WriteBatch` 分批（如每 1 万键一批 commit），或对"恢复前整库清空"语义改用 drop-and-recreate column family（`dropColumnFamily` + `createColumnFamily`，一次原生操作替代 N 次删除）；`deleteByPrefix` 同样适用。
- **信心水平**: 确定
- **误报排除**: 不是微优化：键物化是 O(状态大小) 的堆分配，与批处理改写不同，这一形态在大状态下改变的是"能否恢复成功"（OOM 与否），量化标准为键数 × 平均键字节。
- **复核状态**: 未复核

---

## 已验证为健康的重点面（负面结论记录）

1. **公式链单点收敛（审计要点 1/2）**: 记录路由 `DataStreamImpl:477`、memory 属主 `MemoryKeyedStateBackend:562`、RocksDB 属主 `RocksDBKeyedStateBackend:499`、restore 过滤 `KeyGroupRangeRestoreFilter:68`、reshard `KeyGroupReshard:141-142`、rescale 装配 `GraphModelCheckpointExecutor:1354` / `RescaleStateAssembler:322` / `MaxParallelismReshardMigration:414` 全部走 `KeyGroupAssignment` 单点；`assignKeyGroupToSubtask` 闭式解与 legacy 扫描等价性有全网格性质测试钉住（类注释）。未发现第二条哈希公式入口。Enum 按 `name()` 哈希的修复（`KeyGroupAssignment:66-70`）完整，无 identity-hash 残留调用点（`stableHash` 的非 JSON 可序列化 fallback 有 WARN 可观测，且 JSON 不可序列化 key 本就不可 checkpoint）。
2. **PendingCheckpoint 生命周期（审计要点 3）**: RUNNING→{COMPLETED,ABORTED,FAILED} 的 CAS 端点、N1 status guard（`CheckpointCoordinator:587-591`）、ST-01 abort future 完成、F-01 forceFail（COMPLETED→FAILED 收缩语义）、persist 失败/abort 的 checkpointSuccessMap 清理（A2'）一致；未发现 fix-of-fix 端点。增量段两阶段回滚（`executeIncrementalPersistAsync` 的 storeCheckPoint/storeEpochManifest 失败分支 `releaseIncrementalSegments`）与协调器"不直接操作 RocksDB 实例"的守卫（`setIncrementalCheckpointEnabled` 校验 segmentStore/async）齐备。
3. **SerDe 边界（审计要点 4）**: canonical checksum（先验后析、field-order 规范化、数值定点归一 `normalizeNumbersDeep`）、formatVersion 双面校验、`__java_bytes__` 标记的 base64 载体、`MapValuePairValidator`/TimeWindow namespace 防御、`ContainerValueCodec` 逐层元素类型、legacy 容忍（checksum/版本缺失 debug 放行）均验证一致；`JavaStreamSerializer` 在 readObject 前设 `StreamDeserializationFilter`（唯一原生反序列化咽喉点）。
4. **TTL（审计要点 5）**: 惰性逐出 + 快照前排除（`isExpiredForSnapshot`）+ 60s 门控清扫（ST-12）+ RocksDB 前缀删除（MapState 整 map 单元）一致；恢复后侧车为空、首次访问授予新窗口为已文档化的 OnCreateAndWrite 语义（有意取舍，不计缺陷）。TTL 时间戳本身不持久化——跨重启 TTL 窗口重置是文档化限制。
5. **RocksDB 列族/实例生命周期（审计要点 6）**: `listColumnFamilies` 失败响亮（防静默空列族）、close 顺序（metrics→CF→defaultCF→db→WBM→cache→filter→options，suppressed 聚合）、memory-control 参数前置校验、增量恢复的 SST 哈希重校验（F-03a）、key layout v2 fail-fast、CF 名读取失败拒绝跳过（RK-6）均验证到位。namespace TLV 编码注入性成立（R5-ST-04 修复后按 base-key 前缀分组）。
6. **JDBC checkpoint storage（审计要点 8）**: 目录查询失败响亮（plan 366）、观测式保留的 read-observed-then-delete-by-id 方向、方言化原子 upsert（PG/MySQL/H2 + GENERIC 分事务）、结构化 duplicate-key 判定（R5-CON-08）均验证；`deleteAllCheckpoints` 补删 epoch 表避免陈旧恢复。
7. **connector-jdbc 2PC 台账**: 台账主键 `(sink_namespace, epoch_id, subtask_id)`（plan 368 R5-CON-01）、数据 + 台账行同一 `connection.commit()`、namespace 缺省回落 tableName、`ledgerExists` 幂等守卫在先——事务边界与命名空间隔离符合契约（本审计仅抽样核对其与 checkpoint 交互面）。

## 检查范围

- **通读（全文）**: shard 包全部 7 文件（KeyGroupAssignment/KeyGroup/KeyGroupRange/KeyGroupReshard/KeyGroupRangeRestoreFilter/StateKeyRematerializer/StateShard + ShardPrefixedKey/StatePath）；memory backend 12 文件（含 MemoryStateSerDe/MemoryKeyedStateBackend/TtlContext/TypedNamespaceAndKey/各 state 家族）；typeinfo/typeutils 8 文件（TypeSerializer/JavaStreamSerializer/StreamDeserializationFilter/JsonToolSerializer 抽查）；checkpoint core 关键文件（CompletedCheckpoint/EpochManifest/TaskEpochSnapshot/ChannelState/CheckpointConfig/SharedStateRegistryImpl/SstFileChecksum/IncrementalSnapshotResult/SharedStateHandle/CheckpointParticipant 抽查）；materialization 2 文件；runtime checkpoint（CheckpointCoordinator 生命周期段 400-850/1000-1200/1400-1750、PendingCheckpoint 全文、CheckpointSerDe 全文、LocalFileCheckpointStorage 全文、JdbcCheckpointStorage 全文、MaxParallelismReshardMigration 全文、RescaleStateAssembler 全文、GraphModelCheckpointExecutor restore 段 1280-1500）；rocksdb 全模块主代码（backend/encoder/value serde/snapshot serde/各 state 家族抽查/incremental 2 文件/OptionConfig 抽查）；connector-jdbc 2PC 台账交互面。
- **定点核对**: `stableHash/assignToKeyGroup/assignToSubtask/assignKeyGroupToSubtask/computeKeyGroupRangeForSubtaskIndex` 全部 12 个主代码调用点；`rematerializeKey/resolveSnapshotKeyType` 全部 6 个主代码调用点；`createKeyedStateBackend` 全部 4 个生产调用点及其 keyType 声明；async persist 线程时序（tracker wiring → acknowledgeTask → persist executor）。
- **测试保护力抽查**: TestMaxParallelismReshardMigrationE2E（手工构造确认）、TestStateMigrationEndToEnd（真实 backend 快照链）、TestE2EWindowAggregateRestore（真实 env.execute 两阶段）、TestRestoreKeyRematerialization（ST-02 覆盖确认）、TestAsyncSnapshotPipeline（时序注释交叉印证）。
- **未覆盖（超出本轮范围或时间盒）**: metrics 包逐文件、RocksDB 各 state 家族逐行（抽查为主）、WindowOperator 全文（仅 key 声明与 setCurrentKey 调用面）、cep SharedBuffer/NFA 内部、HeapInternalTimerService 快照往返逐字段、nop-stream-runtime taskmanager/RPC 面（属报告 01/03 同族审计范围的可能性更高）。

---

## 深挖第 2 轮追加

- **深挖日期**: 2026-10-05（同一 HEAD `bd089444`）
- **范围**: 第 1 轮"未覆盖"清单 + ST-01/02/03/04 暴露链路的延伸面：HeapInternalTimerService 快照往返逐字段及全部 timer service 实例化/接线点；operator state（MemoryOperatorStateBackend 全文、WindowOperator trigger-accumulators/pane-tracking、OverWindowOperator、EquiJoinOperator、StreamUnionOperator）；checkpoint/participant 包（CheckpointParticipant 契约 + TwoPhaseCommitSinkFunction 全文 + StreamSinkOperator 接线）；checkpoint/incremental 包（SharedStateRegistryImpl 全文、孤儿对账承诺核对）；ChannelState 全文；core checkpoint 其余快照类型（SavepointMetadata/EpochState/SourceEnumeratorSnapshot/SerializerFingerprint 及其双后端消费核对）；runtime/checkpoint/metrics 包；ProcessingTimeServiceDriver/timer 触发线程边界；TTL×checkpoint（RocksDB 快照前清扫）复核；keyType 生产/消费链补查（RocksDBKeyedStateBackend.snapshotIncremental、StreamSourceOperator、CheckpointBarrierTracker 信封改名、RescaleStateAssembler operator-state 守卫）。
- **方法**: 只读源码审计，逐文件通读上列主代码，配以全仓 grep 验证零调用点/零消费点断言（rebuildViewFromKeyedState、mergeRestoredBuffer、setKeyType、snapshotTimers、restoreFromEpoch 调用面）。禁止修改产品代码；未运行 maven/测试。

### [ST-11] ProcessOperator 的 timer service 从不进入 checkpoint——keyed ProcessFunction 注册的全部 event-time/processing-time timer 在恢复后静默丢失
- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/ProcessOperator.java:41-47, 59-62`；对照 `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java:552, 576-579, 645-647`；`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/TimerStateKeys.java:11-16`；`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/HeapInternalTimerService.java:48-54, 318-322`
- **证据片段**:
  ```java
  // ProcessOperator.open() — 创建并注册 timer service，但全类（189 行）无
  // snapshotState/restoreState/processBarrier override，无 setKeyType 调用
  internalTimerService = new HeapInternalTimerService<>(
          (Triggerable<Object, VoidNamespace>) this, () -> getCurrentKey());
  if (timeServiceManager != null) {
      timeServiceManager.registerTimerService(internalTimerService);
  }
  // 对照 WindowOperator.snapshotState（timer 唯一的两处持久化方之一）:
  if (internalTimerService != null) {
      result.putOperatorState(TimerStateKeys.WINDOW_INTERNAL_TIMERS,
              internalTimerService.snapshotTimers());
  // HeapInternalTimerService.rematerializeEntry — keyType==null 恒等放行
  if (key == null || keyType == null || keyType == Object.class
          || keyType.isInstance(key)) { return entry; }
  ```
- **严重程度**: P1
- **现状**: timer 快照在主代码中只有两个实现方：WindowOperator（`WINDOW_INTERNAL_TIMERS` operator state + open() 延迟回放，WindowOperator.java:645-647/576-579）与 CepOperator（自有 per-key 台账 `CEP_EVENT_TIME_TIMERS`，带 keyClass 信封）。ProcessOperator 同样向 `TimerServiceManager` 注册 service（:45-47）并向用户代码暴露 `Context.timerService()`（可 registerEventTimeTimer/registerProcessingTimeTimer），但其 keyed 后端经 `applyPendingRestoreState()` 恢复（:61）而 timer 完全没有对应通道——`copyForSubtask`/open() 重建的 service 恒为空。`TimerStateKeys` 的 WI21 契约（"Timer state is REQUIRED state for window and CEP exactly-once"）只登记了两个 consumer，ProcessOperator 不在列且无任何文档声明该排除。次级面：即便补上快照，`setKeyType` 从未被调用（keyType==null → `rematerializeEntry` 恒等），Long/bean key 的 JSON 漂移将复现 AR-22 问题。
- **风险**: 任何依赖 keyed ProcessFunction timer 的作业（超时/间隔触发、time-service 轮询、基于 onTimer 的窗口模拟）在 checkpoint 恢复后 timer 全部消失且无任何告警——恢复点之后不再产生本应触发的输出（静默输出丢失），且与 WindowOperator/CepOperator 的恢复语义不一致。触发条件仅为"使用 timer 的 KeyedProcessFunction + 一次恢复"，无配置门槛。
- **建议**: 仿照 WindowOperator 模式补齐三件套：(1) ProcessOperator.snapshotState 将 `internalTimerService.snapshotTimers()` 放入 operator state（新增 `TimerStateKeys.PROCESS_INTERNAL_TIMERS`）；(2) restoreState 捕获并在 open() 延迟回放；(3) open() 中按可得的 key 类（恢复头/KeySelector，同 ST-02 建议的 `restoredKeyClass` 机制）调用 `setKeyType`。短期缓解：在 ProcessOperator.open() 对"已注册 timer service 且无快照通道"打 WARN。
- **信心水平**: 确定（全类通读 + `snapshotTimers`/`setKeyType`/`restoreTimers` 全仓调用点 grep 验证：ProcessOperator 零命中；测试仅覆盖 timer 触发行为，无恢复行为断言，也无文档声明这是有意限制）
- **误报排除**: 不是"基类已兜底"的误判——AbstractStreamOperator/AbstractUdfStreamOperator 的 snapshotState 只覆盖 keyed/operator 后端与 CheckpointParticipant，TimerServiceManager 无任何快照职责（全类 89 行通读确认）；也不是 ST-02 的重复——ST-02 指向恢复后 keyed state 查找 miss，本条指向 timer 数据本身在快照侧就不存在，两者可独立成立。
- **复核状态**: 未复核

### [ST-12] EquiJoinOperator/OverWindowOperator 的 working-view 恢复传输只认活对象实例——JSON 持久化路径恢复后缓冲视图静默为空，"keyed mirror 重建"承诺零接线
- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/join/EquiJoinOperator.java:140-145, 161-166, 349-355, 426-453`；`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/OverWindowOperator.java:47, 121-128, 142-147, 159-162, 252-280`；`nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/operators/windowing/TestE2EOverWindowWithCheckpoint.java:79-97`；生产装配 `nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/AdvancedTransforms.java:547`
- **证据片段**:
  ```java
  // EquiJoinOperator.snapshotState — 活 buffer 对象直接入快照（"restore transport"）
  result.putOperatorState(BUFFER_STATE, buffer);                       // :144
  // restoreState — 只认活类型实例，JSON 往返后的 LinkedHashMap 静默跳过（无 else/WARN）
  if (restored instanceof PerKeyOrderedBuffer) {                       // :163
      buffer.putAll((PerKeyOrderedBuffer<String, JoinSideRecord<Object>>) restored);
  // OverWindowOperator — 兜底机制存在但全仓库零调用：
  /** Rebuilds the working view from the durable keyed state (call after
   *  {@code restoreState} on a fresh instance). */                    // :252-253
  public void rebuildViewFromKeyedState() { if (viewRebuilt) return; ... } // :257
  ```
- **严重程度**: P1
- **现状**: 两算子的恢复设计是双通道：keyed mirror（`persistCurrent` 逐元素写 keyed MapState，EquiJoinOperator:349-355 / OverWindowOperator:159-171）+ operator-state 活对象传输。传输通道只在**同 JVM 内存恢复**下成立（快照结果持有原 buffer 实例）；经 JSON 持久化（storageType=local/jdbc，唯一跨进程路径）后恢复值是 `LinkedHashMap`，`instanceof PerKeyOrderedBuffer` 为 false 且无 else 分支——工作视图静默为空。号称的兜底 `rebuildViewFromKeyedState()`（OverWindowOperator 类 javadoc :47 与 :159-162 注释"the view is rebuilt from these entries on restore"均指向它）在 main+test 全仓库**零调用**（grep 验证）；EquiJoinOperator 无任何 keyed-mirror 重建代码（仅 :426-453 的 test/diagnostic 只读迭代器）。生产影响面：EquiJoinOperator 由 flow builder 装配（AdvancedTransforms:547）；OverWindowOperator 目前仅测试引用，但其 E2E（TestE2EOverWindowWithCheckpoint:87-97）恰以**未经序列化的内存快照**冒充恢复产物并手工调 `mergeRestoredBuffer`，把缺口钉死在测试盲区（同 ST-06 家族）。
- **风险**: equi-join 作业重启（故障恢复/发布重启）后，恢复点之前缓冲的全部 join 侧记录静默丢失——后续水印推进不再对旧记录产出匹配（漏 join），无报错；OVER 窗口同理（缓冲行丢失，行号/聚累从恢复点重排）。keyed mirror 数据完整存活却永不消费，形成"写-only 血统"。
- **建议**: 在两算子的 open()（applyPendingRestoreState 之后）无条件调用 keyed-mirror 重建（OverWindowOperator 复用 `rebuildViewFromKeyedState()` 并把它接进 `restoreState`/open；EquiJoin 新增等价方法），将 operator-state 活对象传输降级为内存直跑优化而非恢复正确性的依赖；同时给 `instanceof` 失败分支补 WARN。为 E2E 补一条经 `CheckpointSerDe` 序列化往返的真实持久化恢复用例。
- **信心水平**: 确定（传输失效与零调用均为代码可直接推演 + grep 证实；JSON 往返产物类型由 CheckpointSerDe:871-873 pass-through + JsonTool 行为决定）
- **误报排除**: 不是"测试已证明恢复闭环"——E2E 断言的连续性来自未经序列化的活对象合并（测试注释自称"rebuilt from the durable keyed entries"但代码走的是 operator-state 活实例，注释与实现不符）；也不是 ST-02 的重复——ST-02 是 keyed 条目的 key 类漂移，本条是 operator-state 传输通道的类型退化 + 兜底未接线，恢复的数据面不同。
- **复核状态**: 未复核

### [ST-13] operator-state 通道的异步持久化序列化活对象——CheckpointSerDe 对非特判值"原样透传"，EquiJoin/OverWindow 的活 buffer 在 ACK 后被持久化线程遍历
- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/CheckpointSerDe.java:851-876`；`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/join/EquiJoinOperator.java:144`；`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/OverWindowOperator.java:126`；`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/checkpoint/CheckpointConfig.java:36`（async 默认开）
- **证据片段**:
  ```java
  // CheckpointSerDe.serializeOperatorStates — 仅特判 TimerSnapshot/PaneTracking/
  // SimpleAccumulator 三类，其余"Everything else passes through unchanged"：
  } else {
      result.put(entry.getKey(), value);      // :871-873 活对象原样进入序列化图
  // 两算子 snapshotState 放入的是任务线程持续变异的活 buffer：
  result.putOperatorState(BUFFER_STATE, buffer);   // EquiJoin:144 / OverWindow:126
  ```
- **严重程度**: P2
- **现状**: ST-01 的竞争面限定在 keyed 通道（MemoryStateSerDe 浅引用 + `keyedStates` 活 map）。operator-state 通道存在同构问题：默认异步（`DEFAULT_ASYNC_SNAPSHOT_ENABLED = true`）下任务线程 ACK 即恢复消费，`processElement` 继续 `buffer.add(...)`（内部结构变异）；持久化线程随后对整个 task-state 图做 `JsonTool.serialize`，透传的活 PerKeyOrderedBuffer（HashMap/ArrayList 组合）在遍历中被并发变异。两算子未做任何防御性拷贝（对照：TwoPhaseCommitSinkFunction.saveState 在锁内 `new TreeMap<>(pendingCommits)`，WindowOperator 对 trigger-accumulators 逐项 `clone()`——同文件族内的正确写法已存在）。
- **风险**: ① checkpoint 周期性持久化失败（CME 中止整个 checkpoint 文档的序列化，numFailedCheckpoints 抬升、恢复点滞后拉长）——响亮但难定位（表现为"checkpoint 偶发失败"而非指向本通道）；② 产出结构损坏但可解析的 buffer JSON——因 ST-12（该值在 durable 恢复路径不被消费）暂不致数据损坏，但一旦按 ST-12 建议接通 operator-state 恢复，本条即升级为 P0 级静默坏数据。两个缓冲型算子高吞吐时必然触发。
- **建议**: 与 ST-12 一并修复：snapshotState 时对 buffer 做快照隔离拷贝（`buffer` 提供不可变视图 DTO，或锁内复制），参照同族 TwoPhaseCommitSinkFunction/WindowOperator 的既有模式；若采纳 ST-12 的"keyed mirror 为唯一恢复真相"方案，则 operator-state 传输可整体退场，本条随之消解。短期：CheckpointConfig javadoc 与 ST-01 的 (c) 缓解一并声明"async + 活 operator-state 值"为不支持组合。
- **信心水平**: 确定（结构性竞争成立；具体撕裂为概率性，与 ST-01 同口径）
- **误报排除**: 不是 ST-01 的机械重复：ST-01 的证据链（MemoryStateSerDe.snapshotKeyedState/CheckpointSerDe:762）只覆盖 keyed 通道，其"限定 memory 后端"的裁定不含 operator-state 透传路径；本条给出的是另一条独立代码通道（serializeOperatorStates）+ 两个具体生产点 + 与 ST-01 不同的后果形态（持久化失败而非快照内容错误），修复点也不同（快照侧拷贝 vs SerDe 深拷贝）。
- **复核状态**: 未复核

### 深挖第 2 轮验证为健康的面（负面结论记录）

1. **timer 触发线程边界**: ProcessingTimeServiceDriver 严格遵循 mailbox 设计——调度线程只做 volatile due-check 并投递 control mail，`fireProcessingTimeTimers` 在任务线程执行（ProcessingTimeServiceDriver.java:115-151），与 `processElement` 串行，timer map 无跨线程变异；`nextProcessingTimeTimer` 的 volatile 单读契约自洽。
2. **VoidNamespace / TimerSnapshot / PaneTrackingSnapshot 的 JSON 往返**: VoidNamespace 带 `@JsonValue`/`@JsonCreator` 单例往返闭合（恢复后仍为 INSTANCE，`deserializeNamespace` 兼容）；WindowOperator 的两份 operator-state DTO 均为值语义自描述表单，CheckpointSerDe 双向特判对称。
3. **CepOperator timer 台账**: per-key 信封携带 `keyClass` + 规范值，`rematerializeLedgerKey` 镜像 MemoryStateSerDe 语义且对 legacy 形态 WARN 降级（CepOperator.java:676-692, 763-786）——AR-22/ST-02 修复链在 CEP 面闭环。
4. **SerializerFingerprint 承诺兑现**: javadoc 声称"compared at getState() time"属实——Memory（MemoryKeyedStateBackend:427-440）与 RocksDB（RocksDBKeyedStateBackend:566-567）双后端均有 current/restored checksum 比对 + StateMigrationFunction 迁移通道 + typed fail-fast，与 ST-08 的落空承诺形成对照。
5. **SharedStateRegistryImpl 孤儿对账承诺兑现**: javadoc 声称"Orphan files ... reconciled at coordinator (re)start"——CheckpointCoordinator:1788-1815 的 restart orphan-segment cleanup 实现存在（按注册表存在性扫描删除）。
6. **rescale 的 operator-state 守卫**: `assertNoOperatorStateOnScaleUp` 对 scale-up 带 operator state 的快照 typed fail-fast，scale-down 1:1 按索引（RescaleStateAssembler.java:137-159, 186-200）——MemoryOperatorStateBackend javadoc 自述的"redistribution 重载无生产调用方"为已裁定项（NEW-A），不计新发现。
7. **CheckpointParticipant 2PC 基类**: TwoPhaseCommitSinkFunction 的 saveState 锁内防御拷贝（RL-4）、setPendingCommits 键归一化 + 同步包装（RL-5）、restoreFromEpoch 的 durable/in-flight 二分与 per-entry 隔离、copyForSubtask 的 No-Silent-No-Op 均到位；StreamSinkOperator 的 `participant-` 前缀写读对称（P0-3 修复完好）。
8. **ChannelState**: per-element 隔离 skip 均带 WARN（P1-09-01 规则落实）、channel index 解析防御、side-output tagId 持久化（HG-01）在位；`mapToEnvelope` 的裸 cast 由 per-element try/catch 包裹。
9. **keyType 链补查**: RocksDB 增量快照信封写 `keyType` 头（RocksDBKeyedStateBackend:861-863）；`applyPendingRestoreState` 在全部四个自装配 keyed 算子（Process/EquiJoin/OverWindow/Cep）中均被调用，无"pendingRestoreState 永不消费"的漏接线；RocksDB 增量快照前 `cleanupExpiredEntries()`（TTL×checkpoint 交互）语义正确。
10. **runtime/checkpoint/metrics 包**: 纯 AtomicLong 计数 + synchronized snapshot，`totalStateSize` 为单调累计值但唯一消费点是日志打印（GraphModelCheckpointExecutor:889-902），无决策依赖，不构成缺陷。

---

## 维度复核结论

- **复核日期**: 2026-10-05（同一 HEAD `bd089444`）
- **复核方法**: 只读重走 live code。对每条发现独立核验证据行号、现状描述与风险推演；对全部"零调用/零消费"论断重新执行全仓 grep（含 main/test 源集与 XML/model 资源面）；ST-01/ST-11/ST-12 按指令重走了完整代码路径。唯一例外按指令读取了 04 报告的 CC-11 条目做重合度比对。本节为追加内容，原发现正文未做任何修改；条目内"复核状态: 未复核"字样由本节裁决取代。
- **总裁决**: 13 条全部**保留**，无一驳回、无一降级；其中 ST-01、ST-05 各附一条精度澄清（不改变等级），ST-12 附与 04 报告 CC-11 的重合/差异裁定。

### 逐条裁决

**ST-01 — 保留，P0。** 全链路独立复验成立：`DEFAULT_ASYNC_SNAPSHOT_ENABLED = true`（CheckpointConfig.java:36）；`completePendingCheckpoint` 在 `!config.isAsyncSnapshotEnabled()` 分支之外把整个 `CompletedCheckpoint` 交给 persist executor（CheckpointCoordinator.java:658-661），而 617-621 行注释与 692-697 行"operating on immutable snapshot data"确以 immutable 为前提；MemoryStateSerDe 快照路径对值/键零拷贝（`serializeKey` 恒等返回 :650-652；`serializeWithSerializer` 在无自定义 serializer 时原样返回活值 :690-693；`snapshotKeyedState` entry 循环 :239-249），`StateSnapshot` 构造器直接持有传入 map；CheckpointSerDe:762 将活 keyedStates map 放入序列化图，`serializeCheckpoint` :119 以 `JsonTool.serialize` 单次遍历活图写串。原地变异语义证实：`MemoryAggregatingState.add` 经 `aggFn.add(value, accumulator)` 变异存储内同一 ACC（mutable ACC 为 AggregateFunction 的规范用法），`MemoryListState.add` 向存储内同一 ArrayList 追加；memory 后端是缺省后端（TaskCheckpointWiring 兜底 `new MemoryStateBackend()`）、storageType 缺省 "local"（JSON 持久化）——默认配置即暴露。**精度澄清（不降级）**：快照自身的 LinkedHashMap/ArrayList 容器在快照构建后不再被结构化修改，故 ② 中"CME/链表重组"子论断的适用面窄于原文表述——仅当活引用值本身是 Map/List 且被原地结构化变异（如 ValueState 持有可变 Map）时成立；对典型的 long[]/POJO ACC，失效形态是**撕裂内容**（可解析但数值错误），即风险 ①。① 本身完全成立且是主导失效路径，exactly-once 契约破坏 + 默认开启，P0 维持。

**ST-02 — 保留，P1。** 三个 `createKeyedStateBackend(Object.class)` 调用点逐一定位核实（ProcessOperator:60、EquiJoinOperator:126、OverWindowOperator:107）；`createKeyedStateBackend` 生产调用点全集为 5 处，另两处 WindowOperator:492（`keyClass`）与 CepOperator:450（`effectiveKeyClass`）确用真实类，与发现描述一致。`rematerializeKey` 的 `keyType == Object.class` 恒等分支（StateKeyRematerializer:64-67）与 `TypedNamespaceAndKey` 的类敏感 equals/hash（`!key.getClass().equals(...)` 即 false + `31 * value.getClass().hashCode() + value.hashCode()`）均逐字核实。Long→Integer 收窄有 MemoryStateSerDe javadoc（AR-01 注记）与 deserializeKey 注释自证。恢复后查找全 miss 的推演成立，silent orphan 成立，P1 恰当。

**ST-03 — 保留，P2。** `buildRescaledTaskState` 重建信封仅写 `"states"`（RescaleStateAssembler:229-233）核实；对照点 `redistributePools` 确以 `new LinkedHashMap<>(pool.envelope)` 保留信封头且用 `resolveSnapshotKeyType(pool.envelope, null)`（MaxParallelismReshardMigration:351-365）。写侧丢头、读侧恒等的复合失效链成立；"两次变换复合后静默过滤"的推演与 KeyGroupRangeRestoreFilter 的 range 过滤语义一致。触发需非原始 key + 变换链，P2 恰当。

**ST-04 — 保留，P2。** memory 侧 `restoreState`:280 与 `deserializeKey`:676 均只消费后端声明 keyType（实读核实）；RocksDB 侧 `resolveRestoreKeyType`（RocksDBSnapshotSerDe:407-421）在声明为 Object.class 时回落快照头，逐字与引文一致。全仓 grep：`resolveSnapshotKeyType` 主代码共 4 处（定义 + RocksDB:420 + reshard:359 + rescale:222），memory 主代码零调用，论断成立。**微瑕（不改级）**："byte-compatible" 自述实际位于 RocksDBSnapshotSerDe:55 与 RocksDBStateBackend:28（描述与 MemoryKeyedStateBackend 的兼容契约），原文归到 MemoryStateSerDe 顶部——归属有偏差但"存在跨后端兼容契约、且 keyType 解析规则已分叉"的实质成立。

**ST-05 — 保留，P2（附时序精化）。** 结构面全部核实：`pruneOldCheckpoints` 按 id 升序删除窗口外最旧目录（实读 ：154-185）；K=2 的"last durable + one in-flight"假设注释在位（:113-118）；`onCompletePersistFailure` 仅做段引用回滚/状态清理，不回收任务本地保留决策（实读方法体）；恢复路径 `reconstructRocksdbDir` 确依赖任务本地 nonSstDir（readSstNameMap + 逐文件 copy）。**时序精化（不降级）**：单次 persist 失败（N 失败）且下一快照 N+1 **成功持久化**时，恢复走 N+1、其目录存在，作业仍可恢复；不可恢复需 N+1 也未 durable——即失败重复发生，或（async persist 默认开）进程在 N+1 持久化完成前崩溃。原文"存储抖动一次 + 再跑一次快照后即不可恢复"应读作"抖动延续到下一次快照的持久化、或其间崩溃"；窗口放大机制与 K=2 注释承诺落空均成立，fail-loud 后果定性准确，P2 维持。

**ST-06 — 保留，P2。** 实读 TestMaxParallelismReshardMigrationE2E:63-120：`buildKeyedStateData` 手工拼装条目（且手工信封恰好带 `keyType: java.lang.String` 头——原文括注属实，ST-03 类回归在该测试上确实不可见）；`stageSavepoint` 用与被测迁移同源的 `KeyGroupAssignment` 公式分桶，staging 与 verification 同侧同变成立；key 仅 String。reshard 工具面无真实作业快照输入的缺口成立。

**ST-07 — 保留，P3。** `migrate` javadoc "（consistency check）"（:79-81）与 `validateArgs` 仅范围检查（:453-472）逐字核实；`reshardCheckpoint` 全程无与快照记录 `maxParallelism` 的比对（结合 ST-08 的消费面 grep 交叉证实）。危害限于校验承诺落空，P3 恰当。

**ST-08 — 保留，P3。** 全仓 grep `getKeyGroupOwnership` 主代码零命中；`TaskEpochSnapshot.getMaxParallelism` 的消费点仅 CheckpointSerDe 写侧（:771）与读侧回填（:817-824）；restore 实际经 `resolveMaxParallelism`（GraphModelCheckpointExecutor:1418-1436）从活 keyed 后端重推导（:1431 的 `keyed.getMaxParallelism()` 是 `IKeyedStateBackend`，非快照对象）。两处 javadoc 承诺原文核实（"lets the restore path recompute..."、"re-read on restore"）。文档-实现漂移成立，P3 恰当。

**ST-09 — 保留，P3。** `BASELINE_PREFIXES`（io.nop./java./javax./jakarta./[）与 startsWith 即 ALLOWED 的判定循环逐字核实（:54-59, :103-118）。javax.* 整前缀放行的事实成立；可利用性未证、定位为纵深防御缺口的口径与"有趣的猜测"信心水平自洽，P3 恰当。

**ST-10 — 保留，P3。** `clearAllStates` 全键物化进 `List<byte[]>` + 逐键原生 delete 逐行核实（RocksDBSnapshotSerDe:423-435）；`deleteByPrefix` 同形态（RocksDBKeyedStateBackend:746-767）核实。正确性无损、恢复期堆尖峰与 O(N) JNI 的量化口径成立，P3 恰当。

**ST-11 — 保留，P1。** 两条路径重走完毕。（A）快照组装路径：timer 持久化在主代码仅两个实现方——WindowOperator（`snapshotTimers()` → `WINDOW_INTERNAL_TIMERS` operator state，:645-647；open() 延迟回放 ：576-579）与 CepOperator（自有 `snapshotTimersByKey` 私有台账）；ProcessOperator 全文 189 行通读无 snapshotState/restoreState/processBarrier override，timer service 注册（:45-47）与用户面暴露（`Context.timerService()` :118-120/:141-143）均在，而 timer 数据无任何快照通道。全仓 grep：`snapshotTimers` 主代码仅 HeapInternalTimerService:247（定义）与 WindowOperator:646（消费）；`restoreTimers` 主代码仅 WindowOperator:577；TimerServiceManager（89 行全文）无任何快照职责；`TimerStateKeys` 仅登记 WINDOW/CEP 两个 consumer，与 WI21 契约注释一致。（B）恢复装配路径：ProcessOperator 的 service 由 open() 全新 `new HeapInternalTimerService<>` 重建，恒为空；`setKeyType` 主代码唯一调用点是 WindowOperator:552，ProcessOperator 零接线属实，`rematerializeEntry` 的 `keyType == null` 恒等分支（:318-322）逐字核实。"使用 timer 的 KeyedProcessFunction + 一次恢复即静默丢 timer、无配置门槛"成立，P1 恰当。

**ST-12 — 保留，P1（附与 CC-11 的重合裁定，见下）。** 代码面全部核实：EquiJoin `snapshotState`:144 活 buffer 直入 operator state，`restoreState`:163 `instanceof PerKeyOrderedBuffer` 无 else/WARN，`persistCurrent` keyed mirror（:349-355）在，EquiJoin 无任何 keyed-mirror 重建代码（:426-453 确为 test/diagnostic 只读面）；OverWindow :126/:142-147 同构，`rebuildViewFromKeyedState`（:257）**全仓零调用**（grep 仅定义 + 类 javadoc :47 引用），`mergeRestoredBuffer` 仅 TestE2EOverWindowWithCheckpoint:97 一次测试调用；processElement 经 `buffer.add` 持续结构化变异活 buffer。生产面核实：EquiJoinOperator 由 AdvancedTransforms:547 装配；OverWindowOperator 无任何生产引用（Java 引用面 + XML/model 资源面均查，仅测试）。E2E（:87-97）确以未经序列化的活对象 `mergeRestoredBuffer` 冒充恢复产物，测试盲区定性成立。JSON 路径下 `instanceof` 失败 → 工作视图为空 → keyed mirror 写-only，推演完整，P1 恰当。

**ST-13 — 保留，P2。** `serializeOperatorStates` 三类特判 + `else { result.put(entry.getKey(), value); }` 透传（:851-876，透传点 ：871-873）逐字核实；两算子放入的是任务线程持续 `buffer.add` 变异的活 PerKeyOrderedBuffer（HashMap<K, TreeMap<long[],V>>，结构化变异实读核实）——与 ST-01 的 keyed 通道不同，**活容器本身**在序列化图内，CME 论断在此直接成立；async 默认开（CheckpointConfig:36）。同族正确写法对照核实：TwoPhaseCommitSinkFunction.saveState 锁内 `new TreeMap<>(pendingCommits)`（:161-162）、WindowOperator trigger-accumulators 逐项 `clone()`（:634）。"因 ST-12 该值在 durable 恢复路径暂不被消费故暂不致数据损坏、一旦接通即升级 P0 级"的分级推演自洽，P2 恰当。

### ST-12 与 04 报告 CC-11 / AR-13 的重合与差异

已按指令读取 04 报告 CC-11（`PerKeyOrderedBuffer` 恢复不合并 `insertionSeq`：(ts,seq) 碰撞静默覆盖已恢复条目，P1）。裁定如下：

1. **相邻而非重复**。两者位于**同一条恢复管道**（`open()` new 归零 buffer → `restoreState` → `putAll`），但根因与失效层不同：ST-12 是**传输通道缺陷**——JSON 持久化路径下恢复值退化为 LinkedHashMap，`instanceof` 失败后什么都没恢复（工作视图整体为空）；CC-11 是**合并语义缺陷**——条目确实到达（同 JVM 内存路径），但 `putAll` 不推进 `insertionSeq`，此后同 (key, ts) 新插入按等键 `tree.put` 覆盖已恢复条目。一为"恢复量为零"，一为"恢复后部分条目可被覆盖"，可同时独立成立，不可互相抵扣。
2. **修复联动**：ST-12 建议的修复路线（`rebuildViewFromKeyedState()` 从 keyed mirror 经 `buffer.add()` 重建，add 按计数器重新分配 seq）**天然绕开** CC-11 的旧 seq 碰撞——durable 路径上修 ST-12 即顺带消解 CC-11 的主要暴露面；CC-11 在同 JVM `putAll` 路径与任何保留 putAll 的修复方案下仍独立成立。建议汇总时两条**分别保留、显式交叉引用、合并修复**，避免同一管道缺陷被重复计数或被单一修复误标"已闭环"。
3. **AR-13**：在本复核允许的读取范围（本文件 + 04 文件）内 grep 未见 AR-13 条目（04 中零命中），无法就其文本做直接比对；若对抗审查报告的 AR-13 指向同一 PerKeyOrderedBuffer 恢复族（从 CC-11 的表述推断可能相邻），最终去重应由汇总方在读取 AR-13 原文后按第 1 点的"根因/失效层"口径裁定。

### 复核中发现的原报告微瑕（不影响任何裁决等级）

- ST-04："byte-compatible" 自述归属应为 RocksDBSnapshotSerDe:55 / RocksDBStateBackend:28（RocksDB 侧声明与 memory 后端兼容），非 MemoryStateSerDe 顶部注释。
- ST-01：CME 子论断适用面窄于原文（见逐条裁决中的精度澄清）；撕裂内容（风险 ①）为对典型 ACC 的实际失效形态。
- ST-05：不可恢复时序需"失败重复或下次持久化完成前崩溃"，单次 persist 失败 + 下次成功持久化可自愈（见逐条裁决中的时序精化）。

## 最终保留项

| 编号 | 严重程度 | 裁决 | 一句话理由 |
|---|---|---|---|
| ST-01 | P0 | 保留 | async 默认开 + memory 快照浅引用 + JSON 全图遍历活对象，默认配置下 exactly-once 破坏链完整；CME 子论断收窄为"撕裂内容"后 P0 仍成立 |
| ST-02 | P1 | 保留 | 三处 Object.class 声明 + 恒等重材料化 + 类敏感 equals，非 String key 恢复后 keyed state 静默孤儿，全链路 live code 证实 |
| ST-03 | P2 | 保留 | rescale 重建信封丢 keyType 头（写侧），下游 reshard/再 rescale 恒等分桶（读侧），静默过滤丢数据链成立 |
| ST-04 | P2 | 保留 | 同一恢复契约 memory 只看后端声明、RocksDB 回落快照头，分叉经双侧实读 + 零调用 grep 证实（byte-compatible 归属微瑕不改级） |
| ST-05 | P2 | 保留 | 本地保留窗口与 durable 语义脱钩的结构成立；时序精化为"失败重复或崩溃才不可恢复"，fail-loud 定性准确，P2 维持 |
| ST-06 | P2 | 保留 | reshard E2E 输入确为手工构造 + 同源公式 + 手工信封带 keyType 头，ST-02/03/05 触发面无测试保护 |
| ST-07 | P3 | 保留 | "consistency check" javadoc 承诺与 validateArgs 实际范围检查不符，假接口成立 |
| ST-08 | P3 | 保留 | ownership 元数据写入+读回但零决策消费（getKeyGroupOwnership 全仓零命中），javadoc 承诺落空成立 |
| ST-09 | P3 | 保留 | javax.*/java.* 整前缀放行事实成立，纵深防御缺口定位与信心水平自洽 |
| ST-10 | P3 | 保留 | clearAllStates/deleteByPrefix 全键物化 + 逐键 JNI delete 逐行证实，可量化的恢复期资源缺陷 |
| ST-11 | P1 | 保留 | timer 快照通道在 ProcessOperator 完全不存在（双路径重走 + setKeyType/snapshotTimers/restoreTimers 全仓 grep），恢复后 timer 静默全丢 |
| ST-12 | P1 | 保留 | JSON 路径 instanceof 静默跳过 + rebuildViewFromKeyedState 全仓零调用 + EquiJoin 生产装配，恢复后缓冲视图为空；与 CC-11 为同管道相邻缺陷，分别保留、交叉引用、合并修复 |
| ST-13 | P2 | 保留 | operator-state 透传活容器在序列化图内被并发结构化变异，同族防御拷贝对照在位，分级推演自洽 |
