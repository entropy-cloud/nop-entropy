# 维度 02：状态后端与序列化

## 审计范围与方法

- **范围**：nop-stream-core 状态后端（`MemoryKeyedStateBackend`/`MemoryStateSerDe`/各 Memory*State、KeyedState/OperatorState API、serializer 契约、`io.nop.stream.core.checkpoint` 包、`common/typeinfo`/`typeutils` 序列化工具）、nop-stream-rocksdb（`RocksDBKeyedStateBackend` 全文、增量 snapshot/restore、key encoder、两个 SerDe）、runtime 侧 checkpoint SerDe/checksum/savepoint/restore 管道（`CheckpointSerDe`、`LocalFileCheckpointStorage`、`CheckpointCoordinator` 持久化链路）、以及 operator-state/broadcast/rescale 汇聚点（`MemoryOperatorStateBackend`、`RescaleStateAssembler`、`KeyGroupRangeRestoreFilter`）。
- **方法**：以 HEAD live code 为准，逐文件全文阅读上述核心类；对每条候选发现沿"快照点（task 线程）→ ACK → 持久化线程 → 恢复"全链路验证调用时序（`CheckpointCoordinator` 持久化专用 executor，`CheckpointCoordinator.java:208` 注释及 `storeCheckPoint` 调用点 673/702/750）；未读 ai-dev/ 下任何文件；未运行构建/测试。
- **前置事实**：HEAD 已含 R5 修复批次（ST-01 value 族冻结、ST-02 key 重材料化、ST-06 响亮失败、P1-21-01 容器编解码、F-02/F-03 增量保留与校验等均已落地），本报告只报告 live code 中仍然成立的缺口。
- **严重程度判级**：P0/P1/P2/P3 按任务书口径。本次未发现 P0。

---

## 第 1 轮（初审）

### [ST-01] Memory 后端 ListState/MapState 快照载荷元素为活引用，异步持久化期间任务线程变异可泄漏进持久化 JSON
- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryStateSerDe.java:230-259`（`writeListPayload`/`writeMapPayload`）、`818-829`（`serializeWithSerializer` 空序列化器分支）
- **证据片段**:
  ```java
  // MemoryStateSerDe.java:230-237
  private static void writeListPayload(Map<String, Object> entry, Object storedValue,
                                       IStreamSerializer<Object> valueSer) {
      List<Object> serializedList = new ArrayList<>();
      for (Object v : (List<?>) storedValue) {
          serializedList.add(serializeWithSerializer(v, valueSer));
      }
      entry.put("listValue", serializedList);
  }
  // MemoryStateSerDe.java:822-828（无自定义序列化器时原样返回活引用）
  if (serializer == null) {
      // R5 ST-01: raw-value writers freeze via freezeRawValue instead.
      // This method is also the per-element path for list/map payloads, ...
      return value;
  }
  ```
  `writeMapPayload`（240-259 行）同样：`pair.add(SnapshotCopyUtil.defensiveCopy(me.getKey()))` 只冻结了 map key，value 走 `serializeWithSerializer(me.getValue(), valueSer)` 后仅在 `valueSer == null` 时包一层 `ContainerValueCodec.encode(value)`——而 `encode` 对非容器值是恒等透传（`ContainerValueCodec.java:83-121`），bean/标量 value 仍是活引用。对照同文件 "value" 族的 `freezeRawValue`（223-228 行）在 R5 ST-01 中明确做了 `SnapshotCopyUtil.defensiveCopy`。
- **严重程度**: P1
- **现状**: 快照点冻结（ST-01）覆盖了 Value/Reducing/Appending 载荷与 map key，但 ListState 的 `listValue` 元素与 MapState 的 `mapValue` value 在默认配置（无自定义 `IStreamSerializer`）下仍以活引用进入 `StateSnapshot`。
- **风险**: 持久化由 coordinator 专用 executor 异步执行（`CheckpointCoordinator.java:208`，`storeCheckPoint` 调用点 673/702/750），barrier 之后任务线程继续变异这些元素/值：持久化 JSON 捕获 post-barrier 状态（与冻结的 keyed lineage 不一致，恢复后状态偏差）；并发变异与 `JsonTool.serialize` 遍历竞态可产生 CME 或撕裂文档 → checkpoint 持久化失败。另外非 DataBean 元素的失败暴露点从快照点（可归因到 state）后移到持久化线程，违背 ST-01 建立的"首故障在快照点暴露"契约。
- **建议**: 在 `writeListPayload`/`writeMapPayload` 中，对 `valueSer == null` 且 `ContainerValueCodec.encode` 不产生 wrapper 的元素/值执行 `SnapshotCopyUtil.defensiveCopy`；容器元素可先记录元素类再拷贝内容（wrapper 字段来自 live 元素类，内容来自拷贝），保住 P1-21-01 的元素类型恢复能力。
- **信心水平**: 确定（无自定义序列化器路径的引用链完整验证：storage map → entry → 异步 JSON 序列化）
- **误报排除**: 表面上这像"R5 ST-01 已修"的同类误报，但 ST-01 只冻结了 value 族、appending 载荷与 map key；823-827 行注释给出的"拷贝会抹掉元素类型"理由仅对容器元素成立（`ContainerValueCodec.encode` 需要 live runtime class 记录 wrapper 类型），对 bean/标量元素 `encode` 是恒等、无需 live 类型——该路径下没有任何拷贝发生，且与同文件 219-222 行对 `freezeRawValue` 的使用边界自相矛盾。
- **复核状态**: 未复核

### [ST-02] EquiJoin/OverWindow 快照把活 `PerKeyOrderedBuffer` 交给 operator-state 通道，持久化时才序列化 → post-barrier 记录泄漏进 durable 工作视图
- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/join/EquiJoinOperator.java:148-154`；`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/OverWindowOperator.java:131-138`；根因面 `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryOperatorStateBackend.java:37-42`
- **证据片段**:
  ```java
  // EquiJoinOperator.java:148-153
  public OperatorSnapshotResult snapshotState(...StateSnapshotContext context) throws Exception {
      OperatorSnapshotResult result = super.snapshotState(context);
      result.putOperatorState(BUFFER_STATE, buffer);   // buffer 是活的 PerKeyOrderedBuffer
      return result;
  }
  // MemoryOperatorStateBackend.java:37-42 —— 仅浅拷贝 map，值仍活引用
  public OperatorSnapshotResult snapshotState(long checkpointId) throws Exception {
      return new OperatorSnapshotResult(
              new HashMap<>(operatorStates), Collections.emptyMap(), Collections.emptyMap());
  }
  // PerKeyOrderedBuffer.toSerializableForm()（持久化时才遍历活 buffers map）
  ```
- **严重程度**: P1
- **现状**: 快照点放入 result 的是 live buffer 引用；真正的内容序列化（`CheckpointSerDe.serializeOperatorStates` → `PerKeyOrderedBuffer.toSerializableForm()`）发生在 coordinator 持久化线程（`LocalFileCheckpointStorage.storeCheckPoint` → `CheckpointSerDe.serializeCheckpoint:851-883`），晚于 barrier、与任务线程并发。
- **风险**: barrier 后任务线程继续 `buffer.add(...)`：durable 快照携带 post-barrier 记录；恢复时 `restoreState` 从该工作视图 `putAll` 重建 buffer，而上游 source 按快照 offset 重放同一批记录 → join/over 窗口缓冲重复元素、输出重复；且与同一快照中已冻结的 keyed-state lineage（`bufferState` MapState）不一致。内存 region-restart 直传路径（in-memory ACK 对象）同样共享活对象。
- **建议**: 快照点对 buffer 做 `toSerializableForm()`（或等价冻结拷贝）再放入 result——与 `WindowOperator`（631-637 行 clone accumulator）、`StreamReduceOperator`（ST-13 typedCopy）已建立的纪律对齐；或在 `MemoryOperatorStateBackend.snapshotState` 对值统一做类型保持冻结。
- **信心水平**: 确定
- **误报排除**: 不是"persist 前任务已暂停"的误报场景：ACK 排水后 coordinator 才完成 checkpoint，任务 mailbox 在 barrier 处理后立即继续消费记录；`WindowOperator`/`ProcessOperator`/`StreamReduceOperator` 都在快照点各自冻结，恰证这两个操作符是遗漏的入口而非全局设计如此。
- **复核状态**: 未复核

### [ST-03] 恢复侧类名白名单拒绝写侧已接受的用户类：默认 JSON 快照成功、恢复必失败，失败暴露在恢复时刻
- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/util/ClassNameValidator.java:16-63`；消费点 `MemoryStateSerDe.java:475-484,512,546-548,590-593`；`RocksDBSnapshotSerDe.java:666-680`
- **证据片段**:
  ```java
  // ClassNameValidator.java:56-63 —— accumulator 比 keyType 更严（仅 io.nop.stream.*）
  public static void validateAccumulatorClass(String className) {
      ...
      if (!className.startsWith("io.nop.stream.")) {
          throw new StreamException(ERR_STREAM_CLASS_NOT_ALLOWED).param(ARG_CLASS_NAME, className);
      }
  }
  // MemoryStateSerDe.java:512（恢复时才加载 valueType）
  Class<Object> valueClass = loadClass(resolveTypeName(stateInfo, "valueTypeName", "valueType"));
  ```
- **严重程度**: P1
- **现状**: `validateClassName` 允许 `io.nop.*` 与 JDK 前缀，`validateAccumulatorClass` 仅允许 `io.nop.stream.*`；它们只作用于**恢复侧**的 `valueType`/`mapKeyType`/`keyType`/`accumulatorType`/`aggregateFunctionType` 头字段加载。写侧（`snapshotKeyedState` 写 `descriptor.getValueType().getName()`、`getAccumulatorType().getName()`）不设限，用户应用包（如 `com.acme.Event`）的状态值可正常快照。
- **风险**: 默认（无自定义序列化器）配置下，用户自定义包的 state bean / ReducingState accumulator（ReducingState 无 live-function 注册通道，只能走类名反射）在运行期一切正常，恢复时抛 `ERR_STREAM_CLASS_NOT_ALLOWED`——checkpoint 变成不可恢复资产，故障暴露在 recovery 这个最坏时点。同族字节码门 `StreamDeserializationFilter`（24-39、145-155 行）承认"用户状态类在基线之外"是合法需求并提供了系统属性逃生口 + 迁移提示，两个门行为与错误信息契约不一致。
- **建议**: 三选一：(a) 快照/`getState` 注册时校验 descriptor 类型，把失败前移到写侧对称点；(b) 给 `ClassNameValidator` 提供与 `nop.stream.state.deserialize.allowed-prefixes` 对齐的逃生口并在错误信息中附迁移提示；(c) 至少让 `validateAccumulatorClass` 与 `validateClassName` 同口径（`io.nop.*`）。
- **信心水平**: 确定（代码路径无歧义；缓冲因素是错误本身 fail-fast 且信息可定位，但恢复失败即 checkpoint 报废）
- **误报排除**: 不是"安全加固本意如此"的可豁免项——本审计不质疑白名单本身，指出的是写/恢复两侧不对称导致的延迟故障与两个门（JEP 290 filter vs ClassNameValidator）契约漂移；这是"安全加固引入的恢复面回归"，属于核心契约漂移而非设计品味。
- **复核状态**: 未复核

### [ST-04] 增量 checkpoint 任务本地保留清理未防 id 回绕：重启/重建 backend 后首快照会删掉自己的 cp-{id} 目录
- **文件**: `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/incremental/RocksDBIncrementalSnapshotStrategy.java:120,154-185`；`RocksDBKeyedStateBackend.java:127,850-853`
- **证据片段**:
  ```java
  // RocksDBIncrementalSnapshotStrategy.java:173-184
  ids.sort(Long::compareTo);
  for (int i = 0; i < ids.size() - retention; i++) {
      Path victim = checkpointBaseDir.resolve("cp-" + ids.get(i));
      ... // 未排除 currentId，且隐含“id 单调递增”假设
  }
  // RocksDBKeyedStateBackend.java:127 —— 每个 backend 实例从 0 起计
  private final transient AtomicLong incrementalSnapshotIdCounter = new AtomicLong(0);
  // :851  baseDir 缺省 = dbPath + "-checkpoints"（跨重启稳定存在）
  ```
- **严重程度**: P1
- **现状**: `checkpointBaseDir`（缺省 `dbPath+"-checkpoints"`）跨进程重启/region 重启持久存在，而 `incrementalSnapshotIdCounter` 是 backend 实例字段、每次重建从 0 起。进程重启或 region restart（supervision loop 重建 task → 重建 backend）后，旧的高 id 目录（如 cp-5、cp-6）仍在；新首快照 id=1 写完 `cp-1` 后，`pruneOldCheckpoints` 按"保留数值最大 2 个"清理，sorted 列表 `[1..6]` 会删除 `cp-1`（刚写完的当前快照目录）与 `cp-2`。
- **风险**: 返回的 `IncrementalSnapshotResult` 随即指向已删除的 `nonSstDir`/`native` SST 路径：coordinator `buildAndMaterializeSegments` 的 `storeSegment` 读不到文件 → checkpoint 持久化失败（连续失败可拖垮作业）；若 SST 恰好已在共享 store（去重短路）则持久化"成功"但 manifest 记录的 `nonSstDir` 已失效 → 下次恢复 `reconstructRocksdbDir` 打不开目录，恢复即失败。增量 checkpoint 恰是为此场景存在的。
- **建议**: `pruneOldCheckpoints` 排除 `currentId`（以及持久化 manifest 仍引用的最新成功 id）；或把 id 计数器持久化/从既有目录推导（`max(existing)+1`），保证重启后 id 严格单调。
- **信心水平**: 很可能（清理与计数代码路径确定；未运行实测，但算术成立：retention=2 且存在任一 id>新 id 的旧目录即触发）
- **误报排除**: F-02 注释"K=2 covers last durable + one in-flight under maxConcurrentCheckpoints=1"只论证了**单进程内 id 单调**的安全性；id 回绕发生在 backend 重建（重启/region restart），这正是 checkpoint 目录必须存活的场景，注释未覆盖。
- **复核状态**: 未复核

### [ST-05] `deserializeEpochManifest` 对不可解析 JSON 返回 null（与 `.checkpoint` body 的 ST-06 响亮失败不对称）
- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/CheckpointSerDe.java:479-483`（对照 `127-134`）
- **证据片段**:
  ```java
  // deserializeEpochManifest —— 同文件内两种行为
  Map<String, Object> map = JsonTool.parseMap(json);
  if (map == null) {
      return null;                       // manifest：折叠为“无 manifest”
  }
  // deserializeCheckpoint —— ST-06 已修复为响亮失败
  Map<String, Object> map = JsonTool.parseMap(json);
  if (map == null) {
      throw corruptCheckpointRecord(null, ..., "checkpoint record is not a JSON object");
  }
  ```
- **严重程度**: P2
- **现状**: 存在但不可解析（截断/外力损坏/JDBC 行损坏）的最新 `.epoch` 文件被折叠为"无 manifest"；`GraphModelCheckpointExecutor.restoreFromCheckpoint`（957-995 行）随即静默回退 `.checkpoint` 旧 epoch 或冷启动。
- **风险**: 正是 ST-06 在 `.checkpoint` 路径修掉的模式（"existing but unparseable record is corruption, not an empty store"）：状态ful 作业静默回滚到旧 epoch 或静默冷启动；manifest checksum 机制对"不可解析"这一损坏形态完全失效（checksum 只保护可解析文档）。`LocalFileCheckpointStorage` 与 `JdbcCheckpointStorage`（737-738 行）共用此入口。
- **建议**: 与 `deserializeCheckpoint` 对齐：`map == null` 时抛 `ERR_STREAM_CHECKPOINT_DATA_CORRUPT`（`data.length == 0` 的"空记录"可保留 null 语义）。
- **信心水平**: 确定
- **误报排除**: 不是"存储层 temp+ATOMIC_MOVE 使截断不可达"的误报——manifest checksum 机制（507-510 行）本身承认"present-but-corrupt"是需要防的威胁面；且 JDBC 存储无原子移动语义。ST-06 注释在同一文件中给出了明确的裁决理由，manifest 路径未同步。
- **复核状态**: 未复核

### [ST-06] 增量 checkpoint 无生产装配路径：coordinator 侧可开启且校验通过，backend 侧仍走全量扫描——特性静默不生效
- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:1732-1744`；`nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/RocksDBKeyedStateBackend.java:867-881`
- **证据片段**:
  ```java
  // CheckpointCoordinator.validateIncrementalConfig —— 只校验 coordinator 自身两面
  if (incrementalCheckpointEnabled) {
      if (segmentStore == null) { throw ... }        // 有 segmentStore 即通过
      if (!config.isAsyncSnapshotEnabled()) { throw ... }
  }
  // backend.incrementalCheckpointEnabled 默认 false；全仓库 main 代码无
  // backend.setIncrementalCheckpointEnabled(true) / setCheckpointBaseDir(...) 调用
  ```
- **严重程度**: P2
- **现状**: `grep` 全 main 源码：`RocksDBKeyedStateBackend.setIncrementalCheckpointEnabled/setSegmentStore/setCheckpointBaseDir` 仅测试代码调用；coordinator 级 flag 与 backend 级 flag 之间没有任何装配（`RocksDBStateBackend.createKeyedStateBackend` 不传递该配置）。用户开启 coordinator 增量 + segmentStore 后，`buildAndMaterializeSegments` 找不到任何增量 marker → segments 恒为空，无任何告警。
- **风险**: 特性静默 no-op：作业"看起来"配置了增量 checkpoint，实际每轮全量 JSON 扫描（大状态作业的 checkpoint 时长/体积无改善）；运维与开发被误导（No-Silent-No-Op 规则明确禁止的形态）。同时使 ST-04 的触发面（增量目录管理）在生产不可达，但特性修复后该问题立即转为可达。
- **建议**: 在 `validateIncrementalConfig` 中追加"coordinator 开启增量时必须存在至少一个声明增量的 keyed backend"校验（任务装配期 fail-fast），或把 flag 贯通到 `IStateBackend.createKeyedStateBackend` 的工厂参数。
- **信心水平**: 确定（负向 grep + 校验逻辑直读）
- **误报排除**: 不是"flag 由用户代码自行接线所以不算平台缺陷"的误报——`validateIncrementalConfig` 的存在（"misconfigured job fails fast ... No-Silent-No-Op rule"注释）恰恰宣示平台承诺防止这种半配置状态，但校验只覆盖了 coordinator 一侧。
- **复核状态**: 未复核

### [ST-07] `MemoryStateSerDe.resolveHeaderKeyType` 对快照头 keyType 裸调 `Class.forName`，绕过全部兄弟路径都在用的 `ClassNameValidator`
- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryStateSerDe.java:423-438`
- **证据片段**:
  ```java
  private static Class<?> resolveHeaderKeyType(Object headerType) {
      ...
      try {
          return Class.forName(name);            // 无 ClassNameValidator.validateClassName
      } catch (ClassNotFoundException e) {
          LOG.warn("nop.stream.restore-keytype-unknown: ...");
          return null;
      }
  }
  ```
- **严重程度**: P2
- **现状**: checkpoint 字节跨越信任边界（共享存储/备份），头字段 `keyType` 是外部可控字符串；同文件 `loadClass`（475-478 行）与共享的 `StateKeyRematerializer.loadKeyType`（107-115 行）对同类字符串都强制 `validateClassName`，唯此处遗漏。`Class.forName` 会执行目标类 static initializer。
- **风险**: 伪造/损坏 checkpoint 可触发任意classpath 上类的初始化（RCE 加固链条上的缺口）；同时加载结果仅用于重材料化，与 `RocksDBSnapshotSerDe.resolveRestoreKeyType` → `StateKeyRematerializer.resolveSnapshotKeyType`（校验路径）行为分叉。
- **建议**: 换用 `ClassNameValidator.validateClassName(name)` + `Class.forName`（对齐 `loadKeyType`）；未知类保持现有 warn+null 降级。
- **信心水平**: 确定
- **误报排除**: 不是"Class.forName 不初始化"的误报——三参 false 才跳过初始化，此处单参默认初始化；也不是"该字段只由本平台写入"的误报——checkpoint 文件正是全链路 checksum 校验要防的篡改面。
- **复核状态**: 未复核

### [ST-08] RocksDB 非映射族（Value/Reducing/Aggregating）的容器声明类型在运行期读写与快照恢复两侧都绕过 `ContainerValueCodec`
- **文件**: `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/RocksDBValueState.java:72,91`；`RocksDBSnapshotSerDe.java:305-309,475-485`（对照容器感知的 `RocksDBMapState.java:291-307`）
- **证据片段**:
  ```java
  // RocksDBValueState.update —— 直_serialize，无 wrapper
  backend.getDb().put(cfHandle, key, RocksDBValueSerDe.serialize(value));
  // RocksDBValueState.value —— 直读，无 decode
  T result = RocksDBValueSerDe.deserialize(bytes, descriptor.getValueType());
  // 对照 RocksDBMapState（P1-21-01 落地面）：
  private byte[] serializeValue(UV value) {
      return RocksDBValueSerDe.serialize(ContainerValueCodec.encode(value));
  }
  ```
- **严重程度**: P2
- **现状**: `ValueStateDescriptor(name, List.class)` 型状态在 RocksDB 上：每次 `value()` 读回 JSON 原生元素（`LinkedHashMap`），写回也不带元素类型 wrapper；全量快照 `writeValuePayload` 与恢复 `restoreValueEntries` 同样不解码容器。Memory 后端恢复路径对容器类型有 `ContainerValueCodec.decode` + 降级告警（`MemoryStateSerDe.deserializeValue:897-900`）。
- **风险**: 容器声明类型的标量状态在 RocksDB 上元素类型永久降级（CEP/用户 typed 访问 CCE 或静默错判）；跨后端恢复 Memory→RocksDB 行为分歧；与 P1-21-01 已为 MapState 建立的"类型感知存储"契约不一致。
- **建议**: 把 `RocksDBValueState`/`RocksDBReducingState`/聚合族的读写路径接到 `ContainerValueCodec.encode/decode`（与 `RocksDBMapState.readValue/serializeValue` 同构），或在 descriptor 层显式拒绝容器声明类型（fail-fast 优于静默降级）。
- **信心水平**: 很可能（读路径直读确定；`JsonTool.parseBeanFromText(json, List.class)` 的具体行为属平台 JSON 层，可能抛错也可能返回降级容器——两种结果都非正确形态）
- **误报排除**: 不是"JSON 是单一序列化格式所以降级可接受"的误报——P1-21-01 的裁决（ContainerValueCodec 的存在）已确立"容器值必须带元素类型 wrapper"为契约，MapState 落地、同后端的兄弟族未落地是收敛缺口，不是设计分歧。
- **复核状态**: 未复核

### [ST-09] RocksDB 后端缺少 Memory 侧 R5 ST-02 的 effective-key 捕获/头覆盖/外来键告警（对称性缺口，影响面被 canonical-JSON 哈希收窄）
- **文件**: `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/RocksDBSnapshotSerDe.java:91,414-421`；`RocksDBKeyedStateBackend.java:442-445`
- **证据片段**:
  ```java
  // RocksDBSnapshotSerDe.snapshotState —— 写“声明类型”，无 first-key 捕获
  stateData.put("keyType", backend.getKeyType().getName());
  // RocksDBKeyedStateBackend.setCurrentKey —— 无 captureEffectiveKeyType
  public void setCurrentKey(K key) { this.currentKey = key; }
  ```
- **严重程度**: P3
- **现状**: `ProcessOperator`/`EquiJoinOperator`/`OverWindowOperator` 以 `Object.class` 声明创建 backend；RocksDB 后端无 `MemoryKeyedStateBackend.captureEffectiveKeyType`/`noteRestoredKeyType` 等价物，Object 声明下快照头永远是 `java.lang.Object`，恢复侧重材料化恒等、也无 `warnForeignNativeKeys` 级别的告警。
- **风险**: 实际读路径正确性大部分被 `KeyGroupAssignment.stableHash`（canonical JSON murmur3）与二进制 key 字节（同为 canonical JSON）吸收——Long/Integer、bean/LinkedHashMap 的 canonical JSON 相同，key-group 与字节查找不漂移；残余风险集中在"序列化文本非恒定的类型"（如带派生字段/transient 的 bean、Date 格式配置变化）静默 miss 且无任何观测信号。与 Memory 侧修复形成文档化行为不对称，误导后续开发以为 ST-02 已全线闭合。
- **建议**: 为 RocksDB 后端补齐 first-key 捕获 + Object 声明时头覆盖 + 外来键一次性告警（可复用 `StateKeyRematerializer`），或至少在恢复侧补同款 warn。
- **信心水平**: 很可能（代码不对称确定；残余错误场景依赖 JsonTool 序列化非恒定性，属窄触发面）
- **误报排除**: 不是"R5 ST-02 已全局修复"的误报——grep 确认 `noteRestoredKeyType`/`captureEffectiveKeyType` 仅存在于 memory 包；也不是"RocksDB 按字节查找所以无需 key 类"的误报——putEntry 恢复路径确实用该 keyType 计算写入 prefix，类型不恒定时 prefix 与 live 读不一致且无声。
- **复核状态**: 未复核

### [ST-10] `LocalFileSegmentStore.pathFor` 未校验 contentHash 为十六进制，manifest 可控 hash 可越出 store 目录寻址
- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/checkpoint/storage/LocalFileSegmentStore.java:108-111`
- **证据片段**:
  ```java
  private Path pathFor(String contentHash) {
      String prefix = contentHash.substring(0, 2);
      return baseDir.resolve(SHARED_STATE_DIR).resolve(prefix).resolve(contentHash + SST_SUFFIX);
  }
  ```
- **严重程度**: P3
- **现状**: `getSegmentPath`/`discardSegment` 直接以 manifest/registry 中的字符串拼路径；正常写入路径的 hash 恒为 sha256 hex，但恢复重建（`SharedStateRegistryImpl` 从 `StateSegmentDescriptor.path` 重注册，`CheckpointCoordinator.java:1772`）将外部字符串引入。`LocalFileCheckpointStorage` 对自身路径有 `validatePath` 防穿越，segment store 没有对应防线。
- **风险**: 伪造 manifest（checksum 可被攻击者一并重算）可使 `exists/read` 越出 `shared-state/`（存在性 oracle、任意文件读后因 re-hash 不匹配而失败）；`gcSegmentsForCheckpoint → discardSegment` 理论上可删除 baseDir 附近的非 segment 文件。本地信任模型下风险有限。
- **建议**: `pathFor` 前置 `^[0-9a-f]{16,64}$` 校验（对齐 `SstFileChecksum` 产出的形态），不符即 typed fail-fast。
- **信心水平**: 确定（代码事实）；风险评级为低（需本地篡改 checkpoint）
- **误报排除**: 不是"hash 总是平台计算所以无需校验"的误报——`restoreSharedStateRegistry` 是从持久化 manifest 反向注冊 hash 的既有入口，输入等价于文件内容。
- **复核状态**: 未复核

### [ST-11] `RocksDBKeyedStateBackend.openDB` 失败路径泄漏 `dbOptions`/`cfOptions`（及内存控制预设的原生句柄）
- **文件**: `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/RocksDBKeyedStateBackend.java:233-263`（对照 `close():1046-1059` 的成对关闭纪律）
- **证据片段**:
  ```java
  dbOptions = new DBOptions()...;
  cfOptions = new ColumnFamilyOptions()...;
  if (optionConfig.isMemoryControlEnabled()) { applyMemoryControlPreset(); }  // blockCache 等
  ...
  try {
      db = RocksDB.open(dbOptions, dbPath, cfDescriptors, handles);
  } catch (RocksDBException e) {
      throw new StreamException(ERR_STREAM_STATE_ERROR, e)...   // options 不关闭直接抛出
  }
  ```
- **严重程度**: P3
- **现状**: 构造器内 `RocksDB.open` 抛出时（目录权限/锁占用/磁盘满），已分配的 `DBOptions`/`ColumnFamilyOptions` 与 memory-control 预设的 `LRUCache`/`WriteBufferManager`/`BloomFilter` 无人关闭（`close()` 只在实例存活后可达）。同文件 237-240 行对 `Options` 已示范 try-with-resources 纪律，构造失败路径未对齐。
- **风险**: 任务反复重启重试的场景下原生内存/文件句柄累积；错误路径资源泄漏属低频但真实。
- **建议**: `openDB` 的 open 失败分支按 `close()` 的逆序释放 `dbOptions/cfOptions/writeBufferManager/blockCache/bloomFilter`（或整个构造改用 owner 模式，失败时调 `close()`）。
- **信心水平**: 确定
- **误报排除**: 不是"进程随即退出所以无影响"的误报——region restart/supervision loop 正是进程内重建 backend 的常驻路径。
- **复核状态**: 未复核

---

## 深挖第 2 轮追加

（对第 1 轮候选模式在其余状态访问面与 checkpoint 元数据面上的复检结论；无新增高价值问题，以下为排除记录。）

1. **ListState/MapState/Reducing/Aggregating 恢复路径的类型一致性**：`MemoryStateSerDe.restoreKeyedEntries` 与 `RocksDBSnapshotSerDe` 八个 restore 分支统一走 `MapValuePairValidator`（逐对校验、fail-fast、带 stateName）、`resolveTypeName` 新旧双拼写 fallback、`AccumulatorTypeInference`/live-function 优先——未发现绕过点。第 1 轮 ST-01 的活引用缺口仅存在于**快照侧** list/map 载荷，不涉及恢复侧。
2. **KeyGroupRangeRestoreFilter / RescaleStateAssembler**：partial restore 与 rescale 汇聚在 ST-02 感知 keyType 的重材料化上（`filterKeyedStates(..., keyType)`、R5 ST-03 的 keyType 头透传），且 `stableHash` 采用 canonical-JSON murmur3，Long→Integer 类 JSON 降级不改变 key-group——未发现错分片入口。
3. **epoch manifest/handle**：`SharedStateRegistryImpl` 的 register/unregister 全程 `ConcurrentHashMap.compute` 单段锁、零计数不可见、失败持久化回滚（`releaseIncrementalSegments`）齐全；`SstFileChecksum` 流式 SHA-256、恢复侧 re-verify（F-03a）闭环。`IncrementalSnapshotResult` 的 JSON 往返（typed marker / Map 重建双分支）在 backend restore 与 coordinator 两侧对称，且都强制 `verifyKeyLayoutVersion(strict)`。唯一缺口是 ST-04 的本地目录时序（已列第 1 轮）与 ST-05 的不可解析 manifest（已列第 1 轮）。
4. **broadcast/union/split 分配模式**（`MemoryOperatorStateBackend.restoreUnion/restoreBroadcast/restoreSplitDistribute`）：S-9 已对非法 taskIndex fail-fast；UNION/BROADCAST 合并语义有明确注释且生产未接线（`restoreState(4参)` 无 main 调用方），不构成现行缺陷。
5. **Timer snapshot**（`HeapInternalTimerService.TimerSnapshot`）：快照点生成本身即冻结形态（eager `snapshotTimers()`），key 重材料化走声明 keyType（`rematerializeIfNeeded` 等价逻辑，320-340 行），与 keyed-state ST-02 修复同构——未发现新缺口。
6. **savepoint 与 checkpoint 行为差异**：`storeSavepoint`/`loadSavepoint` 与 checkpoint 共用同一 `CheckpointSerDe`（含 body checksum），差异仅在目录/元数据文件；`loadSavepointMetadata` 对缺失 metadata 文件返回 null 属调用方契约。未发现跨后端/跨类型恢复语义缺口超出 ST-08 所列范围。

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 摘要 |
|---|---|---|---|
| ST-01 | P1 | nop-stream-core/.../memory/MemoryStateSerDe.java:230-259,818-829 | List/Map 快照载荷元素/值在默认配置下为活引用，异步持久化竞态可泄漏/撕裂 |
| ST-02 | P1 | nop-stream-core/.../join/EquiJoinOperator.java:148-154 等 | EquiJoin/OverWindow 快照持有活 PerKeyOrderedBuffer，持久化时才序列化 → 恢复后重复元素 |
| ST-03 | P1 | nop-stream-core/.../util/ClassNameValidator.java:16-63 等 | 恢复侧类名白名单拒绝写侧已接受的用户类，checkpoint 快照成功但恢复必失败 |
| ST-04 | P1 | nop-stream-rocksdb/.../incremental/RocksDBIncrementalSnapshotStrategy.java:154-185 | 增量本地保留清理未防 id 回绕，重启后首快照可删掉自身目录，恢复链断裂 |
| ST-05 | P2 | nop-stream-runtime/.../storage/CheckpointSerDe.java:479-483 | 不可解析 epoch manifest 折叠为 null，静默回退旧 epoch/冷启动（ST-06 不对称） |
| ST-06 | P2 | nop-stream-runtime/.../checkpoint/CheckpointCoordinator.java:1732-1744 | 增量 checkpoint 无生产装配路径，coordinator 开启后 backend 静默走全量 |
| ST-07 | P2 | nop-stream-core/.../memory/MemoryStateSerDe.java:423-438 | 快照头 keyType 裸 Class.forName 绕过 ClassNameValidator（加固链缺口） |
| ST-08 | P2 | nop-stream-rocksdb/.../RocksDBValueState.java:72,91 等 | RocksDB 容器声明类型的非映射族绕过 ContainerValueCodec，元素类型降级无告警 |
| ST-09 | P3 | nop-stream-rocksdb/.../RocksDBSnapshotSerDe.java:91,414-421 | RocksDB 缺少 Memory 侧 ST-02 的 effective-key 捕获/告警（对称性缺口） |
| ST-10 | P3 | nop-stream-core/.../storage/LocalFileSegmentStore.java:108-111 | contentHash 未校验 hex，可越出 store 目录寻址（本地信任模型下低危） |
| ST-11 | P3 | nop-stream-rocksdb/.../RocksDBKeyedStateBackend.java:233-263 | openDB 失败路径泄漏 dbOptions/cfOptions 及内存控制原生句柄 |

---

## 维度复核结论

> 复核人：独立复核员（与初审者无关）。方法：逐条实读被引文件与行号核对证据；ST-01..ST-04 四条 P1 从代码独立重推完整触发链（谁调用、什么时序、什么配置门控），并反向搜寻初审者可能遗漏的保护机制（调用方前置校验、快照时深拷贝、测试钉住的契约）；现状归属仅引 R5 summary 与本批 00 报告 REG-08。未修改初审正文；未运行构建/测试。复核新增的关键事实：**REG-08（同批 00 报告）与 ST-01 为同一缺陷的重复登记**，且 REG-08 的 P3 watch 理据（"当前生产算子无原地变异用法"）被本复核推翻——详见 ST-01 详情。

| 发现 | 判定 | 现状归属 | 复核要点 |
|---|---|---|---|
| ST-01 | 保留 P1 | 已知未修复（同批 00 报告 REG-08 已登记同一缺陷、判 P3 watch；plan 2311 Non-Blocking Follow-ups 已登记 List 侧） | 逐行核实成立；复核新增：EquiJoinOperator.bufferState（MapState）载荷叶 JoinSideRecord 在 `emitMatches` 中 barrier 后原地 `setMatched(true)`（EquiJoinOperator.java:296-297，对象经 persistCurrent:372 以同一引用存入 MemoryMapState，MemoryMapState.put 无拷贝），REG-08"无生产变异用法"的降级理据不成立，P1 维持 |
| ST-02 | 保留 P1 | R5 已修复（KR-7 族，plan 2312 toSerializableForm/fromSerializableForm）后暴露的新残余：修复前 durable 默认下 persist 必抛 ERR_JSON_ONLY_DATA_BEAN、竞态不可达；修复后持久化竞态成为主缺陷 | 触发链独立重推成立（详见 P1 详情）；无任何遗漏保护 |
| ST-03 | 保留 P1 | 新发现 | 代码事实成立；一处收窄修正：默认 `nop.core.json.serialize-only-data-bean=true`（CoreConfigs.java:182-184）使**非 DataBean** 用户类在快照点 defensiveCopy 即响亮失败（该形态属 R5 KR-7 族已知面）；静默"写成功/恢复必败"面限于 DataBean 用户类与用户 accumulator 类（accumulator 面无 JSON gate 缓解，ReducingState 无 live-function 通道已核实）——P1（checkpoint 变不可恢复资产 + 与 JEP290 filter 逃生口契约漂移）仍成立 |
| ST-04 | 降级 P1→P2 | 已知未修复（R5 ST-05：同函数 pruneOldCheckpoints 的 persist-失败保留窗口缺陷，P2；本条为其 id-回绕兄弟触发面） | 事实链完整（计数器实例字段从 0 起、baseDir 跨重启持久、prune 无 currentId 排除均逐行确认），但判级下调：触发前置=增量特性启用，而本报告 ST-06 自证 `setIncrementalCheckpointEnabled` 无任何 main 调用方（负向 grep 独立复核）；损坏形态为响亮失败（storeSegment 读不到文件 / restore openReadOnly 失败），非静默数据丢失；与 R5 对同函数、同特性门控缺陷的 P2 判级一致。特性接线后应升回 P1 |
| ST-05 | 保留 P2 | 新发现（.checkpoint body 响亮失败修复的未同步残余） | CheckpointSerDe:479-483（manifest null 折叠）对照 127-134（body 抛 corruptCheckpointRecord）逐行确认；消费面 LocalFile:533 + Jdbc:524/737 共用此入口确认；GraphModelCheckpointExecutor:957-995 静默回退链确认 |
| ST-06 | 保留 P2 | 新发现 | 负向 grep 独立确认 `setIncrementalCheckpointEnabled/setCheckpointBaseDir` 仅定义与测试调用；validateIncrementalConfig（CheckpointCoordinator:1732-1744）只验 coordinator 侧两面；buildAndMaterializeSegments（:798-828）marker 缺失时 `continue` 恒空、无告警确认 |
| ST-07 | 保留 P2 | 新发现 | resolveHeaderKeyType（423-438）单参 `Class.forName`（执行 static initializer）无校验确认；同文件 loadClass:476 与 StateKeyRematerializer:108 均走 validateClassName 的对照确认 |
| ST-08 | 保留 P2 | 新发现（P1-21-01 仅落地 RocksDBMapState 后的同后端收敛缺口） | RocksDBValueState value()/update() 直读直写（无 encode/decode）确认；RocksDBMapState.readValue/serializeValue 容器感知对照确认；writeValuePayload 快照面同样旁路确认；deserialize 对 List.class 的确切行为未钉死但两种结果均非正确形态，判级恰当 |
| ST-09 | 保留 P3 | R5 ST-02 修复未覆盖 RocksDB 侧的对称性残余（新登记） | setCurrentKey（RocksDBKeyedStateBackend:442-444）无捕获、rocksdb 包 grep 无 captureEffectiveKeyType/noteRestoredKeyType 确认；快照头恒写声明类型（RocksDBSnapshotSerDe:91）使恢复侧 resolveRestoreKeyType 的头覆盖机制（:414-421）被饿死；canonical-JSON stableHash 吸收大部分风险的收窄论证成立，P3 恰当 |
| ST-10 | 保留 P3 | 新发现 | pathFor（108-111）无 hex 校验确认；恢复侧 manifest 反向注册入口（CheckpointCoordinator:1770-1776，`seg.getPath()` 直接作 hash 注册）确认；LocalFileCheckpointStorage validatePath（5 处）对照确认；本地信任模型下低危评级恰当 |
| ST-11 | 保留 P3 | 新发现 | open 失败分支（261-264）不释放 dbOptions/cfOptions 及 memory-control 原生句柄确认；close()（1040-1059）成对关闭纪律与同函数内 Options try-with-resources（245-247）对照确认；region restart 进程内重建场景使泄漏真实可达 |

复核统计：11 条 = 保留 10 + 降级 1（ST-04 P1→P2）+ 驳回 0。无新发现补录。

### P1 重点复核详情

**ST-01（List/Map 载荷活引用）— 独立推导链，成立**

1. 谁产生：barrier 处任务线程同步调用 `AbstractStreamOperator.snapshotState`（AbstractStreamOperator.java:284）→ `keyedStateBackend.snapshotState()`（:308）→ `MemoryStateSerDe.snapshotState`。Keyed 快照的 entries/载荷容器是新建的，但：ListState 元素经 `writeListPayload`（:230-237）→ `serializeWithSerializer(v, null)` → 原样返回活引用（:823-828）；MapState value 经 `writeMapPayload`（:254-255）→ `ContainerValueCodec.encode`，对 bean/标量 value 是恒等透传（ContainerValueCodec.java:121），叶子仍活引用。对照同文件 value 族 `freezeRawValue`（:223-228）做了 `SnapshotCopyUtil.defensiveCopy`（JSON 往返深拷贝）——冻结协议在 List/Map 叶子侧确实未闭合，是 R5 ST-01 修复（plan 2311）的残余而非已修。
2. 什么时序：持久化在 coordinator 专用 `persistExecutor`（CheckpointCoordinator.java:208 区域注释，executePersistAsync → `checkpointStorage.storeCheckPoint`）异步执行；`LocalFileCheckpointStorage.storeCheckPoint:97` 才调 `CheckpointSerDe.serializeCheckpoint` 做 JSON 序列化——晚于 barrier，与任务线程并发。
3. 什么配置：默认（descriptor 无自定义 IStreamSerializer，`getSerializerIfAvailable:809-816` 对 JsonToolSerializer 亦返回 null）即触发；自定义 serializer 走 byte[] marker 快照安全路径。
4. 触发面非纯假设：EquiJoinOperator 的 keyed `bufferState`（MapState<String, JoinSideRecord>）值对象在 `emitMatches` 中 barrier 后被 `other.setMatched(true)`（:296-297）原地变异；该对象与 bufferState 存储中的是同一引用（persistCurrent:372 直接 put，MemoryMapState.put 存引用不拷贝）。同批 00 报告 REG-08 已登记同一代码缺陷但判 P3 watch，其"当前生产算子核查暂无原地变异用法"的枚举（window acc clone、CEP ledger TreeSet）漏了本触发面——本复核据此维持 02 报告的 P1，并建议修正 REG-08 的 watch 理据（两处登记应合并，以 P1 为准）。
5. 无遗漏保护：outer 容器（serializedList、pair、wrapper）确为快照点新建（post-barrier 对容器结构本身的 add/remove 不泄漏），泄漏面精确到叶子级；恢复侧 `deserializeValue` 对深拷贝产生的 JSON-native 叶子能经 `parseBeanFromText` 正确重 materialize，修复建议不破坏恢复契约。

**ST-02（EquiJoin/OverWindow 活 PerKeyOrderedBuffer）— 独立推导链，成立**

1. 谁产生：`EquiJoinOperator.snapshotState:148-154` / `OverWindowOperator.snapshotState:131-136` 在 `super.snapshotState(context)`（keyed lineage 已冻结）之后 `result.putOperatorState(BUFFER_STATE, buffer)` 放入**活** buffer；`MemoryOperatorStateBackend.snapshotState:37-42` 仅 `new HashMap<>(operatorStates)` 浅拷贝，OperatorSnapshotResult 全程引用传递（AbstractStreamOperator:315-321 原样并入）。
2. 什么时序：序列化发生在持久化线程——`CheckpointSerDe.serializeOperatorStates:851-883` 对 `PerKeyOrderedBuffer` 实例调 `toSerializableForm()`（遍历活 buffers map，PerKeyOrderedBuffer.java:82-105），由 `LocalFileCheckpointStorage.storeCheckPoint:97` → `serializeCheckpoint:356` 触发；barrier 后任务 mailbox 立即继续消费（`buffer.add:196-201`、`trimToWatermarkWithKeys:286-304` 均原地变异）。
3. 损坏推导：durable 快照携带 post-barrier 记录 → 恢复时 `restoreState`（EquiJoin:158-184）`putAll` 重建工作视图 + keyed lineage 同源污染，而 source 按快照 offset 重放同一批记录 → join/over 缓冲重复元素、重复输出；与同一快照中已冻结的 keyed lineage 不一致（EquiJoin 的 bufferState 通道经 ST-01 叶面泄漏，buffer 通道经本条整体泄漏，两通道一起破坏 exactly-once）。
4. 无遗漏保护：CheckpointCoordinator 交给 persist executor 的注释（:620 区域"the completed checkpoint are immutable"）所依赖的不可变性对本通道不成立；对照 `WindowOperator.snapshotState:629-637`（accumulator clone + snapshotTimers eager + snapshotPaneTracking）与 `StreamReduceOperator`（typedCopy 冻结，:126-134），freeze-at-snapshot-point 是既有纪律，这两个操作符是遗漏入口。无测试钉住该时序契约。
5. 现状归属：R5 KR-7 修复（2312）引入 toSerializableForm 通道前，durable 默认下 persist 必抛（ERR_JSON_ONLY_DATA_BEAN），竞态不可达；修复使 persist 成功的同时让本竞态成为主缺陷——属 R5 修复后的新残余，非 R5 已知未修复项。

**ST-03（ClassNameValidator 写读不对称）— 独立推导链，成立（触发面收窄一处）**

1. 写侧无校验：`snapshotKeyedState` 直接写 `descriptor.getValueType().getName()`（MemoryStateSerDe:273）与 accumulatorType（:160/:176）；`getOrCreateState`/`verifySchemaCompatibility` 均不做类名白名单校验。grep 全量确认 validateClassName/validateAccumulatorClass 的全部消费点均在恢复/反序列化侧（MemoryStateSerDe:476/482/685、RocksDBSnapshotSerDe:672/678/700、StateKeyRematerializer:108、StreamElementCodec:60、ContainerValueCodec:276、SimpleTypeSerializer resolveClass）。
2. 恢复侧必校验：`loadClass:475-478`（validateClassName，io.nop.{stream,commons,core,dao}+JDK 前缀）、`loadAccumulatorClass:481-484`（仅 io.nop.stream.*）。
3. 触发链：用户 DataBean 状态类（com.acme.*，@DataBean 使默认 JSON gate 放行）或用户 SimpleAccumulator 类（local value 可为 JDK 类型，无 gate 阻拦）→ 运行期一切正常、checkpoint 写盘成功 → 恢复时 `restoreState` 即抛 ERR_STREAM_CLASS_NOT_ALLOWED → checkpoint 成为不可恢复资产，故障暴露在最坏时点。
4. 契约漂移对照确认：同族字节码门 StreamDeserializationFilter（typeutils 包）基线白名单之外显式提供系统属性逃生口 + 错误信息迁移提示（:22-28、:143-155），ClassNameValidator 两者皆无，且 accumulator 门（io.nop.stream.*）比主门（io.nop.*+JDK）更严。
5. 收窄修正：非 DataBean 用户类在默认 `nop.core.json.serialize-only-data-bean=true`（CoreConfigs.java:182-184）下快照点即响亮失败，不构成"写成功恢复败"静默面（该形态已被 R5 KR-7 族覆盖）。ReducingState 无 live-function 注册通道（registerRestoreAggregateFunction 仅被 AggregatingState 恢复路径 :681 消费）确认。P1 维持。

**ST-04（增量 checkpoint id 回绕删自身目录）— 事实链成立，判级降为 P2**

1. 事实链逐行独立确认：`incrementalSnapshotIdCounter` 为 backend 实例 transient 字段从 0 起计（RocksDBKeyedStateBackend.java:127），`snapshotIncremental:852` 每次 `incrementAndGet`；`checkpointBaseDir` 缺省 `dbPath+"-checkpoints"`（:851）跨进程重启/region restart 持久存在；`pruneOldCheckpoints`（RocksDBIncrementalSnapshotStrategy.java:154-185）对目录名 id 全集升序排序后删除 `ids[0 .. size-retention)`，无 currentId 排除、无 manifest 引用保护——重启后首快照 id=1 与遗留 cp-5/cp-6 共存时，`[1,5,6]` retention=2 确删 cp-1（刚写完的当前目录），`IncrementalSnapshotResult.nonSstDir` 与 sstHandles 路径（strategy:98/123）随即失效。
2. 无遗漏保护：grep 确认无 id 持久化/从既有目录推导机制，backend open 无 baseDir 清理或迁移，`pruneOldCheckpoints` 无其他调用方。
3. 降级依据：(a) 触发前置是增量特性启用，而 `setIncrementalCheckpointEnabled` 全仓库无 main 调用方（本报告 ST-06 自证，本复核独立 grep 确认）——当前生产不可达，仅测试可达；(b) 同一函数的兄弟缺陷（R5 ST-05：persist 失败后保留窗口删最后可恢复目录）在 R5 判 P2，同特性门控、同损坏形态（响亮失败：storeSegment 读不到文件 / restore `reconstructRocksdbDir` openReadOnly typed error），本条无理由更高；(c) 静默损坏分支（SST 去重短路后 manifest 引用失效）最终仍在恢复点响亮失败，非静默数据丢失。修复 ST-06 装配时必须同步修本条，届时升回 P1。

## 最终保留项（复核后）

| 编号 | 复核后严重程度 | 文件路径 | 一句话摘要 | 现状归属 |
|---|---|---|---|---|
| ST-01 | P1 | nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryStateSerDe.java:230-259,818-829 | List/Map 快照载荷叶子在默认配置下为活引用，异步持久化捕获 barrier 后叶面状态（EquiJoinOperator matched-flag 即现成生产触发面） | 已知未修复：同批 00 报告 REG-08 重复登记（其 P3 watch 理据被本复核推翻，两处应合并以 P1 为准） |
| ST-02 | P1 | nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/join/EquiJoinOperator.java:148-154；nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/OverWindowOperator.java:131-138；根因面 MemoryOperatorStateBackend.java:37-42 | EquiJoin/OverWindow 快照持活 PerKeyOrderedBuffer，持久化线程才序列化 → post-barrier 记录入 durable 工作视图，恢复后重复 | R5 已修复（KR-7 族，2312）后暴露的新残余 |
| ST-03 | P1 | nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/util/ClassNameValidator.java:16-63；消费点 MemoryStateSerDe.java:475-484,512,546-548,590-593 | 恢复侧白名单拒绝写侧已接受的 DataBean 用户类/用户 accumulator，checkpoint 写成功恢复必败，且与 JEP290 filter 逃生口契约漂移 | 新发现（非 DataBean 形态的快照点响亮失败已由 R5 KR-7 族覆盖） |
| ST-04 | P2（降级；增量特性接线后升 P1） | nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/incremental/RocksDBIncrementalSnapshotStrategy.java:120,154-185；RocksDBKeyedStateBackend.java:127,851-852 | 增量本地保留清理未防 id 回绕，重启后首快照可删自身 cp-{id} 目录（触发前置=增量特性启用，当前无生产装配） | 已知未修复：R5 ST-05（同函数保留窗口缺陷）的兄弟触发面 |
| ST-05 | P2 | nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/CheckpointSerDe.java:479-483 | 不可解析 epoch manifest 折叠为 null，静默回退旧 epoch/冷启动（与 .checkpoint body 的响亮失败不对称） | 新发现（body 侧修复的未同步残余） |
| ST-06 | P2 | nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/CheckpointCoordinator.java:1732-1744；nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/RocksDBKeyedStateBackend.java:867-881 | 增量 checkpoint 无生产装配路径，coordinator 校验通过后 backend 静默走全量（No-Silent-No-Op 半配置态） | 新发现 |
| ST-07 | P2 | nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryStateSerDe.java:423-438 | 快照头 keyType 裸单参 Class.forName（执行 static initializer）绕过全部兄弟路径在用的 ClassNameValidator | 新发现 |
| ST-08 | P2 | nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/RocksDBValueState.java:72,91；RocksDBSnapshotSerDe.java:305-309,475-485 | RocksDB 非映射族（Value/Reducing/Aggregating）容器声明类型读写与快照恢复均绕过 ContainerValueCodec，元素类型永久降级 | 新发现（P1-21-01 MapState 落地后的同后端收敛缺口） |
| ST-09 | P3 | nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/RocksDBSnapshotSerDe.java:91,414-421；RocksDBKeyedStateBackend.java:442-445 | RocksDB 缺少 Memory 侧 effective-key 捕获/头覆盖/外来键告警，恢复侧头覆盖机制被"头恒为声明类型"饿死（canonical-JSON 吸收大部分风险） | R5 ST-02 修复未覆盖 RocksDB 侧的对称性残余（新登记） |
| ST-10 | P3 | nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/checkpoint/storage/LocalFileSegmentStore.java:108-111 | contentHash 未校验十六进制，manifest 反向注册的字符串可越出 store 目录寻址（本地信任模型下低危） | 新发现 |
| ST-11 | P3 | nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/RocksDBKeyedStateBackend.java:233-263 | openDB 失败路径泄漏 dbOptions/cfOptions 及 memory-control 原生句柄（region restart 进程内重建场景真实可达） | 新发现 |
