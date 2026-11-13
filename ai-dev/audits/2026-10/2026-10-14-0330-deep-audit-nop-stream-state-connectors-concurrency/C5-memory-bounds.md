# C5 内存有界性与大数据量审计 — nop-stream-state-connectors

- **审计维度**: C5（内存有界性与大数据量——声明规模包络内任何执行路径的内存占用有上界）
- **审计对象**: nop-stream 8 个模块的 `src/main/java`（140 files / 25,458 lines）
  - `nop-stream/nop-stream-cep`（74 files / 11,750 lines）
  - `nop-stream/nop-stream-rocksdb`（19 files / 4,523 lines）
  - `nop-stream/nop-stream-connector`（19 files / 3,416 lines）
  - `nop-stream/nop-stream-connector-batch`（9 files / 1,238 lines）
  - `nop-stream/nop-stream-connector-jdbc`（4 files / 1,483 lines）
  - `nop-stream/nop-stream-connector-debezium`（8 files / 1,824 lines）
  - `nop-stream/nop-stream-connector-kafka`（4 files / 943 lines）
  - `nop-stream/nop-stream-format-avro`（3 files / 281 lines）
- **基线**: `_tmp/cra-baseline-nop-stream-state-connectors.txt`（2026-10-14 05:29:10）
- **Owner doc 规模包络对照**: `docs-for-ai/03-modules/nop-stream.md` + `docs-for-ai/03-modules/nop-stream-connectors.md`。
  - CEP：**只支持 Memory 状态后端**（`NopCepConfigs.COMMON_HINT` + `CepOperator.resolveKeyedBackend` 对 RocksDB 的 fail-fast，R5 CEP-1）——CEP 的规模包络 = 单堆内存；`nop.cep.pipeline.sharedbuffer.cache.*` 缺省 1024 slots。
  - RocksDB：owner doc 未声明数值包络；后端的存在目的即"状态超出堆"（磁盘/off-heap 承载），memtable/写缓冲/block cache 上限经 `RocksDBOptionConfig` 可配（**缺省全 0 = 不启用**，见声明边界注记）。
  - 连接器族：各 source/sink 的交付语义与恢复语义逐行声明（能力矩阵）；**未声明**每 epoch 缓冲上限、ledger/manifest 保留策略。
- **定向缺陷族**: F7（OOM 同族——无界集合/缓存/物化、一次载入全量 vs 流式、序列化中间体积）与 F11（序列化断链/中间体积）逐点核对。
- **盲审纪律**: 本初审未读取 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何文件；仅审计当前 live code。
- **方法**: 逐文件精读核心数据面（CepOperator 1510 行、SharedBuffer/Accessor、RocksDBKeyedStateBackend 1068 行、RocksDBSnapshotSerDe 746 行、JdbcTwoPhaseCommitSink 1161 行、FileTwoPhaseCommitSink、BatchLoader 双代、Message/Debezium/Kafka reader、Avro 双类）+ 基线 grep 圈定全部集合/缓存/队列/物化点，对每一项判定上界与保证者。

---

## 无界风险清单（路径 × 增长源 × 声明上界 × 判定）

| # | 路径 | 增长源 | 声明上界 / 保证者 | 判定 |
|---|---|---|---|---|
| 1 | `CepOperator.reconciledTimerKeys`（cep/operator/CepOperator.java:190,1267-1291） | 每个 drain 过的 distinct stream key 追加 1 条，**无任何删除路径** | 无 | **发现 001（P2）** |
| 2 | `CepOperator.registeredEventTimeTimersByKey`（:181） | 待触发 timer（key × 时间戳） | drain 时 `removeIf(<=time)` + 空则删 key（:1087-1107,1134-1154）；文档声明"bounded by keys with genuinely pending work" | 有界，OK |
| 3 | `CepOperator.elementQueueState`（per-key MapState<Long,List<IN>>） | watermark 滞后量内的乱序事件 | 每 bucket 建 timer（`bufferEvent`→`registerTimer`，CEP-01 修复后 ledger 超集不变式）；watermark/timer 驱动 drain | 有界（watermark 模型声明语义），OK |
| 4 | `SharedBuffer.eventsBufferCache/entryCache`（Guava） | 缓存键 | `maximumSize(1024)`（`nop.cep.pipeline.sharedbuffer.cache.*` 可配）+ LRU 淘汰 | 有界，OK |
| 5 | `SharedBuffer` backing state（eventsBuffer/entries/eventsCount，Memory 后端=堆内） | 每 key 的 partial match 事件/节点 | 引用计数释放（`releaseNode/releaseEvent`）+ `within` 窗口超时释放 + `advanceTime` 清 eventsCount；**无 window 的 pattern 不超时是 CEP 语义**（声明包络内=堆容量） | 声明包络内，OK（用法学区） |
| 6 | `NFA.LazyVerdictCache`（NFA.java:1039） | per-event 条件判定 | 每次 `doProcess` 事件级作用域，不跨事件 | 有界，OK |
| 7 | `RocksDBSnapshotSerDe.snapshotState` 全量路径（默认；`incrementalCheckpointEnabled=false`） | **整个 RocksDB 状态**物化为 Java 对象图 | 无 | **发现 002（P1，F7/F11）** |
| 8 | `RocksDBMapState.collectMap`（entries/keys/values/iterator，:211-261） | 当前 key 的全部 map 子项一次性物化 | 无（Flink 同接口为 iterator 直通） | **发现 003（P2，F7）** |
| 9 | `RocksDBMapState.applyMigration`（:57-79） | 整个 CF 的 keys+values 双列表物化 | 无（schema 迁移冷路径） | **发现 004（P2，F7）** |
| 10 | `RocksDBSnapshotSerDe.clearAllStates`（:446-458）/ `RocksDBKeyedStateBackend.deleteByPrefix`（:746-767） | 每 CF/每 map 的 key 列表物化 | 仅 key 字节（无 value），单 CF/map 范围 | P3 同族注记（并入 002/004 失效面） |
| 11 | RocksDB off-heap：memtable 64MB/CF（`DEFAULT_WRITE_BUFFER_SIZE`），block cache/WriteBufferManager **缺省 0 = 不启用** | 状态数 × 64MB，无全局 off-heap cap | `RocksDBOptionConfig` javadoc 明文声明缺省保留 pre-preset 行为（"Conservative defaults: all knobs at 0"）+ `nop.stream.state.rocksdb.*` 观测面 | **声明边界**，不立项（观测面在，运维可配） |
| 12 | `RocksDBIncrementalSnapshotStrategy` cp-N 目录 | 每 checkpoint 一目录 | F-02 滚动保留（默认 2，`local-retention` 可配） | 有界，OK |
| 13 | `JdbcTwoPhaseCommitSink.currentBuffer`（:233）+ `FileTwoPhaseCommitSink.currentBuffer`（:136） | **本 epoch 全部记录**驻留堆内直到 barrier | 无配置上限；checkpoint 间隔 × 吞吐 | **发现 005（P2）** |
| 14 | JDBC ledger `stream_epoch_ledger_v2` 行（writeLedgerEntry :852-861） | 每 epoch × 每 subtask 1 行，**只增不删** | 无清理/TTL 路径（grep 无 DELETE） | **发现 006（P2，F4）** |
| 15 | File sink `manifest.properties` 键（manifestKey=epochId[.sK]）+ 每 commit 全量 load/rewrite（loadManifest :532-543 / updateManifestAtomically :546-596） | 每 epoch 1 键，**只增不删**；每 commit O(全部 epoch) 读+写 | 无清理路径 | **发现 007（P2，F4 同族）** |
| 16 | `FileTwoPhaseCommitSink.MANIFEST_JVM_LOCKS`（:98）/ `recoveredEntryKeys`（:118） | 输出目录串 / manifest 条目 | 目录数 / 单 manifest 条目数 | 有界，OK |
| 17 | `FileSplitEnumerator.discovered/assigned/finishedFiles`、`splitById`；`FileSourceReader.finishedSplits` | 文件数 | file source **BOUNDED**（枚举完即终态），生命期有界 | 有界，OK |
| 18 | `FileSourceReader` 行聚合（readNextLine :340-379） | 单条无终止符行 | `maxLineBytes`（缺省 16MB）typed fail-fast（R5-CON-07）+ 8KB chunk buffer | 有界，OK |
| 19 | `MessageSourceReader`/`DebeziumCdcSourceReader` 桥接队列 | 引擎 push→pull 转换 | `ArrayBlockingQueue(256)`，put 阻塞=背压 | 有界，OK |
| 20 | `DebeziumCdcSourceReader` offset map（snapshotState 每 split 一份实例级拷贝，plan M-2） | 表数 × offset map | 表 include-list 启动期钉定，实例级（非行级） | 有界（表数级），OK |
| 21 | `KafkaSourceFunction` poll 循环（:270-292） | poll 批 | 立即逐条 `collectWithTimestamp`（引擎背压）+ consumer `max.poll.records` 内建上限；限速可叠 | 有界，OK |
| 22 | `KafkaTwoPhaseCommitSink.invoke` | producer 内部缓冲 | Kafka producer `buffer.memory`（缺省 32MB）承载，连接器自身无堆内累积 | 有界，OK |
| 23 | `BatchLoaderSourceFunction/SourceReader`（run/pollNext） | 批数据集 | 严格流式：`loader.load(batchSize)` 逐批消费，仅 batchCursor 持单批；skip 不物化 | 有界，OK |
| 24 | `BatchConsumerSinkFunction.buffer` | 待 flush 记录 | `batchSize`（缺省 100）即 flush；flush 失败 typed 上抛任务失败（保留缓冲语义不产生无界堆积） | 有界，OK |
| 25 | `AvroSchemaRegistryClient.schemaByIdCache/latestBySubject`（:36-37） | schema id / subject | 键空间=registry id 空间与 subject 集（坏 id fetch 失败不入缓存，不可投毒）；owner doc 声明"双缓存 + 换版不感知已知边界" | 键空间本质可枚举且小，OK（已知边界声明） |
| 26 | `AvroWireFormat.encode/decode` 每记录 `ByteArrayOutputStream`/`GenericDatumWriter|Reader`/encoder | 每记录临时对象 | 生命周期单记录，无驻留 | 无驻留（每记录分配浪费属 C9 热路径维度，非 C5，不立项） |
| 27 | `FileSource.sample`（FileSource.java:394-420） | 采样记录 | `limit` 硬上限 + 行级 maxLineBytes | 有界，OK |

---

## 发现条目

### [nop-stream-state-connectors-C5-001] CepOperator.reconciledTimerKeys 每 key 单调增长、永不清理

- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:190,1267-1291`
- **证据片段**:
  ```java
  private void reconcileTimerLedgerIfNeeded(Object key) throws Exception {
      if (reconciledTimerKeys != null && reconciledTimerKeys.contains(key)) {
          return;
      }
      if (reconciledTimerKeys == null) {
          reconciledTimerKeys = new HashSet<>();
      }
      reconciledTimerKeys.add(key);   // 只 add；全文件无 remove/clear（close 也不清）
  ```
- **严重程度**: P2
- **并发场景**: 单任务线程（mailbox 模型）——非竞态缺陷，纯长时运行单调增长；与 F4"无界账本"同型的**内存版**。
- **触发时序**: 确定性增长，无需竞态时序——每个 distinct stream key 首次进入 `drainDueBuckets`（:1226）即 `reconciledTimerKeys.add(key)`；此后该 key 的 state 即使被 `resetNfaStateIfFullyTimedOut`（:1170-1184）完全清空、timer ledger 条目被 `onEventTime`（:1104）删除，`reconciledTimerKeys` 中的条目与其持有的强 key 引用仍然存留，直到 operator 实例关闭。
- **现状**: timer ledger 的"legacy checkpoint 对账"目的只需"每 key 一次"，但实现用永久 Set 承载；`registeredEventTimeTimersByKey` 同文件内有完整清理语义（空则 remove），此 Set 没有。
- **风险**: key 基数无界的负载（如 user_id 流）下每 subtask 每生命周期累积 ≈32-64B/键（HashSet 条目 + key 强引用，阻止 key 对象回收）。1 亿 distinct key ≈ 数 GB 级稳态增长；且与"CEP 只支持 Memory 后端"（堆内包络）叠加，直接侵蚀业务状态可用的堆预算。`nop.stream.operator.*` 指标面无该结构的水位观测。
- **建议**: 对账完成后该集合的用途即终结——改为按 key 计数的弱引用结构（如 Guava `CacheBuilder.weakKeys().maximumSize(N)`）或在 key 的 timer ledger 条目为空且 NFA state 已重置时同步 `remove(key)`；复用平台 `io.nop.commons.cache.CacheConfig` 有界缓存语义。同时补一个 `getLedgerForTesting` 风格的测试可见水位。
- **失效面枚举**（F3 同族清扫——"对账一次性集合/只增登记表"全查）:
  - `CepOperator.registeredEventTimeTimersByKey` — 有清理（drain removeIf + 空 key 删除）✓
  - `CepOperator.capturedKeyClass` — 单槽 write-once，O(1) ✓
  - `SharedBuffer` eventsCount — `advanceTime` 迭代删除过期时间戳 ✓
  - `DebeziumCdcSourceReader.restoredOffsetByTable/adoptedSplits` — 以 split（表）数为界，表集启动钉定 ✓
  - `FileSourceReader.finishedSplits` / `FileSplitEnumerator.finishedFiles` — BOUNDED source，生命期有界 ✓
  - `FileTwoPhaseCommitSink.MANIFEST_JVM_LOCKS`（static）— 以输出目录数为界，JVM 级小基数 ✓
- **信心水平**: 确定
- **误报排除**: 不是"低频路径粗粒度结构"豁免——drain 是每 watermark/每记录热路径；也不是有界声明——owner doc 与代码注释均未声明 key 基数上界；`registeredEventTimeTimersByKey` 的 javadoc 明确以"bounded"为目标设计，本字段是同文件的语义孤儿。
- **复核状态**: 已保留（P2 维持，2026-10-14 复核）——live 复核证实全文件仅 :190 声明、:1268 contains、:1271-1274 add，无任何 remove/clear；同文件 `registeredEventTimeTimersByKey` 具备完整空集清理语义，对照成立；transient 字段 + key 强引用 + Memory-only 堆内包络叠加论证成立。现状归属：同 campaign C7-005 为同缺陷重复确证（C7 复核已裁定归并本条、修复并轨）；r4 审计（2026-09-30 `01-cep-correctness.md`）曾从 PT+comparator 正确性角度触及同结构，未从内存角度立案，历史无重复登记；live code 未修。；二轮复核：维持保留 P2（2026-10-14 独立二轮复核）——重验全文件仅 ：190 声明/:1268 contains/:1271-1274 add、无任何 remove/clear，与 `registeredEventTimeTimersByKey` 的 removeIf+空键删除对照成立；按「长时运行必然无界增长」字面可争 P1，但单键 ~64B 增量、与 006/007（每单位小增量确定性增长族）判级一致性支持 P2 维持。

### [nop-stream-state-connectors-C5-002] RocksDB 全量 JSON checkpoint 路径把整个状态库物化为堆内对象图（默认路径，F7/F11）

- **文件**: `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/RocksDBSnapshotSerDe.java:84-115,199-251,309-349`（入口 `RocksDBKeyedStateBackend.snapshotState`:827-836）
- **证据片段**:
  ```java
  List<Map<String, Object>> entries = new ArrayList<>();
  TtlContext<ByteBuffer> ttl = state.ttlContext();
  try (RocksIterator it = backend.getDb().newIterator(cfHandle)) {
      for (it.seekToFirst(); it.isValid(); it.next()) {
          ...
          RocksDBKeyEncoder.DecodedKey dk = RocksDBKeyEncoder.decode(it.key(), backend.getKeyType());
          Map<String, Object> entry = new LinkedHashMap<>();
          entry.put("namespace", RocksDBKeyEncoder.serializeNamespace(dk.namespace));
          entry.put("key", dk.rawKey);
          entryWriter.write(entry, dk, it.key(), it.value(), descriptor);  // 值先反序列化为对象
          entries.add(entry);
      }
  }
  info.put("entries", entries);   // 整个 CF 全量驻留，snapshotState 返回前不释放
  ```
- **严重程度**: P1
- **并发场景**: 任务线程在 checkpoint barrier 处执行（与记录处理互斥）；非竞态，规模触发。
- **触发时序**: 状态规模 S（RocksDB 承载、可远超堆）→ barrier 触发 `snapshotState` → `incrementalCheckpointEnabled` 缺省 false（`RocksDBKeyedStateBackend.java:119`）走 `RocksDBSnapshotSerDe.snapshotState` → 全 CF 迭代中**每个值先 `RocksDBValueSerDe.deserialize` 为 Java 对象**、连同 key/namespace 逐一装进 per-entry `LinkedHashMap` 并累积于 `entries`/`grouped`+`groupedMapValues`（MapState 路径连 base key 的 ByteBuffer 切片也复制驻留）→ 返回的 `StateSnapshot`（byte 兼容 MemoryStateSerDe 的 JSON 形态）在下游 checkpoint 持久化时再整体序列化。峰值堆占用 ≈ 状态对象图（序列化字节数的典型 2-5 倍：对象头/装箱/每 entry LinkedHashMap）+ 下游 JSON 中间体；S 超过"堆/放大倍数"即 OOM。
- **现状**: 增量 SST 路径（`snapshotIncremental`，native checkpoint + content-addressed SST，不物化堆内）已实现，但**缺省关闭**；全量 JSON 路径是默认 checkpoint 形态。增量恢复也要求 `ISegmentStore`（缺失即 typed 拒绝，不静默回落）。
- **风险**: 规模口径——RocksDB 后端的存在目的即"状态超出堆"（owner doc 无数值包络，功能包络=GB 级状态）；在该包络内，一旦状态 > 堆/2，每个 checkpoint 周期必然 OOM 或 Full GC 风暴（checkpoint 超时→作业失败→恢复再 snapshot 再 OOM 的循环）。这是声明用途包络内的默认路径缺陷，非越界使用。
- **建议**: ① 将 `snapshotKeyedState`/`snapshotMapState` 改为流式写出（迭代器逐 entry 序列化到 SegmentStore 分段，`StateSnapshot` 携带分段句柄而非全量 entries）；② 或将 `incrementalCheckpointEnabled` 对"状态规模超过阈值"的场景设为缺省/文档强制（owner doc 声明包络与该开关的取舍）；③ 短期至少在 snapshot 前用 `nop.stream.state.rocksdb.estimate.num.keys`+memtable 体量预估峰值并在超阈值时 typed 拒绝（显式失败优于 OOM）。
- **失效面枚举**（F3——"全量物化"同族清扫，rocksdb 模块内全部迭代面）:
  - `snapshotKeyedState`（本体，P1）✗
  - `snapshotMapState`（:199-251 grouped 双 Map，同 P1 面）✗
  - `clearAllStates`（:446-458，仅 key 列表物化，P3）△
  - `RocksDBKeyedStateBackend.deleteByPrefix`（:746-767，单 map key 列表，P3）△
  - `copyColumnFamilyRange`（:383-411）— 迭代内逐条 put，流式 ✓
  - `RocksDBIncrementalSnapshotStrategy.doSnapshot` — 文件枚举+哈希流式，不物化数据 ✓
  - `RocksDBMapState.applyMigration` — 独立立项 004
  - core 侧 `MemoryStateSerDe` 同形态——内存后端状态本就全在堆内，物化仅 2 倍瞬态，不在本模块范围（注记）。
- **信心水平**: 确定（物化行为）；很可能（OOM 阈值取决于实际状态规模与堆配比）
- **误报排除**: 不是"有界声明存在的缓存/队列"——无任何上限声明；不是越界使用——全量路径是缺省 checkpoint 形态，增量路径才是 opt-in；owner doc 对该取舍无声明（包络缺口本身随本发现登记）。
- **复核状态**: 已保留（P1 维持，2026-10-14 复核）——全量路径逐值反序列化 + entries/grouped/groupedMapValues 全量驻留（`RocksDBSnapshotSerDe.java` map 段 :199-260、keyed 段 :309-349）与 `incrementalCheckpointEnabled` 缺省 false（`RocksDBKeyedStateBackend.java:119`）、backend 缺省分派（:826-836）逐项证实；owner doc 无状态规模数值包络，「状态超出堆」功能包络内默认路径缺陷成立。现状归属：同 campaign C8-005（P2）为同点位重复，C8 复核已裁定归并本条（C5 P1 主立案，C8 的 barrier 同步自背压角度由 C8-010 分立承接）；r6 审计 ST-06 曾以「增量无生产装配」登记同面，现 incremental 装配已端到端存在（coordinator + strategy + restore）但仍 opt-in 缺省关，与本条「默认=全量物化」表述一致；live code 未修。；二轮复核：维持保留 P1（2026-10-14 独立二轮复核）——map 段 grouped/groupedMapValues、keyed 段 entries 逐值 deserialize 全量驻留、增量缺省 false（:119 javadoc 自认「full-scan remains the default」）、缺省分派（:826-836）逐项重验成立；「状态超出堆」功能包络内默认路径必然周期性整库过堆，无可用的反驳证据，P1 维持。

### [nop-stream-state-connectors-C5-003] RocksDBMapState 的 entries()/keys()/values()/iterator() 每次调用全量物化当前 key 的整张 map

- **文件**: `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/RocksDBMapState.java:211-261`
- **证据片段**:
  ```java
  private Map<UK, UV> collectMap() {
      ...
      Map<UK, UV> result = new LinkedHashMap<>();
      try (RocksIterator it = backend.getDb().newIterator(cfHandle)) {
          it.seek(baseKey);
          while (it.isValid()) {
              ...
              UK mapKey = extractMapKey(fullKey, baseKey.length);   // 每 key JSON 反序列化
              UV value = readValue(it.value());                     // 每值 JSON 反序列化
              result.put(mapKey, value);
              it.next();
          }
      }
      ...
      return result;
  }
  @Override public Iterable<Map.Entry<UK, UV>> entries() { return collectMap().entrySet(); }
  @Override public Iterable<UK> keys() { return collectMap().keySet(); }
  @Override public Iterable<UV> values() { return collectMap().values(); }
  @Override public Iterator<Map.Entry<UK, UV>> iterator() { return collectMap().entrySet().iterator(); }
  ```
- **严重程度**: P2
- **并发场景**: 任务线程每记录路径调用（非竞态；规模触发）。`Iterable` 返回值契约邀请调用方按流式消费，实现却一次性物化。
- **触发时序**: 确定性——任何调用 `entries()/keys()/values()/iterator()` 的算子（如对 per-key MapState 的全量遍历、TTL/迁移/快照辅助逻辑）在单个 key 的 map 有 N 个子项时，堆内瞬时物化 N 个 (key,value) 反序列化对象；N 与 key 数无关、与单 key map 大小成正比，调用频率与记录速率成正比。
- **现状**: RocksDB 后端其他读路径（`get/contains/isEmpty`）是精确点查；`isEmpty`（:264-273）已经是迭代器直通的首项判断——同为遍历语义却走了流式，说明物化非必要。
- **风险**: 规模口径——单 key map 数十万子项（如 per-user 明细列表）时，单次调用 = 数十万对象图的瞬时峰值；热路径反复调用造成 GC 压力与延迟毛刺。这是"RocksDB 承载大状态"功能包络内的放大器： RocksDB 让状态放得下（磁盘），本路径让"读一遍"必须整块过堆。F7"一次载入全量 vs 流式"的教科书形态。
- **建议**: 返回惰性迭代器（`RocksIterator` 包装，`hasNext/next` 直推 seek 前缀范围 + 逐项反序列化），与 `isEmpty` 的首项判断同构；`collectMap` 仅保留给确需整 map 的内部点（如物化返回值需 snapshot 的场景）。参照 Flink `RocksDBMapState` 的 lazy-iterator 实现。
- **失效面枚举**（F3——RocksDB 状态类的遍历/聚合读面）:
  - `RocksDBMapState.entries/keys/values/iterator`（本体）✗
  - `RocksDBMapState.isEmpty` — 迭代器首项直通 ✓
  - `RocksDBListState/RocksDBValueState/RocksDBReducingState/RocksDBAggregatingState` — 单 key 单值/单 list 点查语义，无遍历面（list 值整体存取为声明语义）✓
  - `MemoryKeyedStateBackend`（core 模块，范围外）同接口为堆内 Map 直通，天然流式 ✓
- **信心水平**: 确定
- **误报排除**: 不是"原语使用"误报——`Iterable` 契约 vs 全量物化的错配是复合行为问题；不是低频路径——MapState 遍历是算子数据处理路径；`isEmpty` 已证明流式实现可行且更廉价。
- **复核状态**: 已保留，降级 P2→P3（2026-10-14 复核）——物化行为本身证实（entries/keys/values/iterator 全部经 `collectMap()`，`isEmpty` 已是首项直通），但「调用频率与记录速率成正比」的热路径前提与 live 调用方普查不符：生产侧唯一迭代调用面是 `OverWindowOperator.rebuildView`（:294，`viewRebuilt` 门控的一次性重建）+ test/diagnostic（`EquiJoinOperator.keyedBufferEntries/keyedAnyMatched` :524/:539、`OverWindowOperator` :343）；初审判定为遍历面的 TTL/迁移/快照实际均**不经** collectMap（各自直迭代 CF），CEP 侧 MapState 迭代因 R5 CEP-1 fail-fast 不可达 RocksDB。真实影响 = 恢复后一次性 per-key 全图物化 + Iterable 契约陷阱（Flink 同接口为惰性迭代器），无每记录放大，降级 P3 保留。；二轮复核：维持降级 P3（2026-10-14 独立二轮复核）——调用方普查独立复现：生产迭代面仅 `OverWindowOperator.rebuildViewFromKeyedState`（:283-294，viewRebuilt 门控一次性 restore 兜底），`keyedBufferEntries`/`keyedAnyMatched` 调用方经 grep 全部位于 test 源码树，TTL 清扫/迁移/快照各自直迭代 CF 不经 collectMap；「调用频率与记录速率成正比」前提证伪结论成立，物化行为与 Iterable 契约陷阱维持，P3 维持。

### [nop-stream-state-connectors-C5-004] RocksDBMapState.applyMigration 把整个 column family 的 keys+values 双列表物化进堆

- **文件**: `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/RocksDBMapState.java:57-79`
- **证据片段**:
  ```java
  public void applyMigration(StateMigrationFunction<?, ?> migration) {
      StateMigrationFunction<Object, Object> fn = ...;
      List<byte[]> keys = new ArrayList<>();
      List<byte[]> values = new ArrayList<>();
      try (RocksIterator it = backend.getDb().newIterator(cfHandle)) {
          for (it.seekToFirst(); it.isValid(); it.next()) {
              keys.add(it.key());      // 整个 CF 的全部 key
              values.add(it.value());  // 整个 CF 的全部 value —— 先全部驻留
          }
      }
      try {
          for (int i = 0; i < keys.size(); i++) {
              Object old = readValue(values.get(i));
              ...
              backend.getDb().put(cfHandle, keys.get(i), serializeValue((UV) migrated));
          }
  ```
- **严重程度**: P2
- **并发场景**: restore 期 schema 迁移路径（`verifySchemaCompatibility`→`applyMigration`，冷路径、单线程）；非竞态，规模触发。
- **触发时序**: 恢复时某状态 schema 指纹失配且注册了迁移函数 → `applyMigration` 先把**全 CF**（全部 key、全部用户的全部行）keys+values 原始字节收集进两个 ArrayList，再逐行迁移写回。大状态库恢复 = 峰值堆 ≈ 全库字节 × 2（keys+values 列表）+ 单行反序列化对象。
- **现状**: 冷路径（仅 schema 演进时执行一次），但恰在**恢复**这一最不能 OOM 的时刻发生——恢复期 OOM 直接导致作业无法拉起（比运行期失败更难处置，无最近 checkpoint 可回退）。
- **风险**: 规模口径——全库状态接近堆容量（RocksDB 的正常使用形态）时，恢复迁移必 OOM；且失败发生在 restore 阶段，作业死循环于"恢复→OOM"。收集-再写回的两段式无正确性必要（迭代器内逐行 read-modify-write 即可，RocksDB iterator 快照语义保证安全）。
- **建议**: 改为单遍迭代 + 逐行 `readValue→migrate→put`（iterator 基于隐式快照，写回不影响本迭代）；或分批（如每 1 万行 flush 一批）。与发现 002 的流式改造共享同一 `RocksIterator` 包装模式。
- **失效面枚举**（F3——applyMigration 族的兄弟实现）:
  - `RocksDBMapState.applyMigration`（本体，全 CF 物化）✗
  - `AbstractRocksDBState.applyValueMigration`（:68 起，单值状态迁移）— 单值粒度，无收集列表 ✓
  - core 侧各 `Memory*State.applyMigration` — 内存后端状态本就在堆内，无放大 ✓
  - `RocksDBSnapshotSerDe.restoreState` — 消费方输入已物化（checkpoint 载体形态决定），随 002 的流式改造一并评估 △
- **信心水平**: 确定
- **误报排除**: 冷路径不豁免——恢复路径 OOM 的后果（作业无法拉起）重于运行期；"先收集后处理"在本场景无正确性收益（迭代器快照语义已保证一致视图）。
- **复核状态**: 已保留（P2 维持，2026-10-14 复核）——keys+values 双 ArrayList 全 CF 收集（`RocksDBMapState.java:66-79`）逐行证实；触发链确定（`verifySchemaCompatibility`→`applyMigration`，`RocksDBKeyedStateBackend.java:559-572`，schema 指纹失配 + 已注册迁移函数），restore 期 OOM 后果（作业无法拉起、无更早 checkpoint 可回退）成立；冷路径不豁免的裁定维持。现状归属：live code 未修；与 008/009 为同根因三实例，修复归并为同一流式迁移 helper。；二轮复核：维持保留 P2（2026-10-14 独立二轮复核）——双列表全 CF 收集（:57-79）与触发链（`RocksDBKeyedStateBackend.java:559-572`：指纹失配且已注册迁移函数才触发）重验成立；触发条件性（需 schema 演进）不支持升 P1，restore 期 OOM 后果（作业无法拉起、无更早 checkpoint 可回退）支持不降级，P2 恰当。

### [nop-stream-state-connectors-C5-005] 2PC sink 的 in-epoch 内存缓冲随 epoch 体量无界（无每 epoch 记录数上限配置）

- **文件**: `nop-stream/nop-stream-connector-jdbc/src/main/java/io/nop/stream/connector/jdbc/JdbcTwoPhaseCommitSink.java:233,484-494,640-650`（同族：`nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileTwoPhaseCommitSink.java:136,250-268`）
- **证据片段**:
  ```java
  // ---- In-memory buffer for the current epoch (not yet in pendingCommits) ----
  private final List<DmlOp> currentBuffer = new ArrayList<>();
  ...
  @Override
  public void invoke(IN value) throws Exception {
      ...
      synchronized (currentBuffer) {
          currentBuffer.add(op);        // 本 epoch 全部记录驻留堆内，直到下一个 barrier
      }
  }
  ```
- **严重程度**: P2
- **并发场景**: 任务线程 invoke 追加、barrier 时 saveState 转 pendingCommits（`synchronized(currentBuffer)` 双线程协调已在）；非竞态，规模触发。
- **触发时序**: checkpoint 间隔 T 内吞吐 R 条记录/秒 → `currentBuffer` 单调增长至 R×T 条 `DmlOp`（每条含整行 `LinkedHashMap<String,Object>` 拷贝）；barrier 到达时整体转入 `pendingCommits[epochId]` 并随 checkpoint JSON 序列化（第二份中间体）。T=5min、R=10k/s、行宽 1KB ≈ 3GB 堆内 + 等量 checkpoint 序列化体。
- **现状**: JDBC 执行侧已做 `maxBatchSize`（缺省 1000）分段（javadoc 明言修复"whole epoch buffered into a single executeBatch OOM"），但**缓冲侧**无对应上限旋钮；file sink 形态相同（缓冲 `List<String>` 行）。Kafka 2PC sink 不受影响（producer 内建 buffer.memory 有界）。
- **风险**: 规模口径——内存上界 = epoch 体量（checkpoint 间隔 × 吞吐 × 行宽），无声明、无配置、无 fail-fast；checkpoint 间隔拉长或 barrier 延迟（上游反压）时静默逼近堆上限。与 002 叠加（RocksDB 作业的 sink 侧缓冲 + 状态侧快照同周期叠加峰值）。
- **建议**: 增加 `maxEpochRecords`/`maxEpochBytes` 配置：超限即 typed fail-fast（`ERR_STREAM_STATE_ERROR` + 当前缓冲水位），提示调小 checkpoint 间隔；与平台 `HighWatermarkSemaphore`（io.nop.commons.concurrent.semaphore）的水位语义对齐。owner doc 能力矩阵同步声明该边界。
- **失效面枚举**（F3——全部 sink 缓冲面）:
  - `JdbcTwoPhaseCommitSink.currentBuffer`（本体）✗
  - `FileTwoPhaseCommitSink.currentBuffer`（同型 List<String> 行缓冲）✗
  - `KafkaTwoPhaseCommitSink` — producer.send 直推，buffer.memory 有界 ✓
  - `BatchConsumerSinkFunction.buffer` — batchSize 即 flush，失败 typed 上抛任务失败（有界）✓
  - `TwoPhaseCommitSinkFunction.pendingCommits`（core 基类，范围外）— 以未提交 epoch 数为界（正常 1-2 个）✓
- **信心水平**: 确定（无界性）；很可能（实际 OOM 取决于 T×R 配置）
- **误报排除**: 2PC"缓冲整个 epoch"是 exactly-once 的结构性需求（buffer-then-commit），**不是缺陷本身**；缺陷是"无上限声明与无 fail-fast"——引擎有 `nop.stream.data.db.backlog.limit` 背压门（WI6，DB 数据面通道）但 sink 缓冲侧无对应保护，超出声明包络的 OOM 与包络内缺陷按提示词规则不混排，本条按"边界配置缺陷"立项 P2。
- **复核状态**: 已保留（P2 维持，2026-10-14 复核）——Jdbc `currentBuffer`（:233/:484-494）与 File `currentBuffer`（:136/:250-268）双面无上限证实；`maxBatchSize` 仅约束执行侧分段（构造校验 :324-325），缓冲侧 grep 全模块无 `maxEpoch`/`maxPending` 类旋钮；owner doc 能力矩阵声明 buffer-then-commit 语义但未声明每 epoch 上限，包络缺口随本条登记成立。现状归属：同 campaign C8-003（P2）为同本体重复，C8 复核已裁定归并本条（B7/B8 过载行为视角与 spill 建议并入本条执行）；live code 未修。；二轮复核：维持保留 P2（2026-10-14 独立二轮复核）——Jdbc currentBuffer（:233/:491-492 invoke/:642-646 saveState 转存）与 File currentBuffer（:136/:238-239 invoke/:250-262 saveState 落盘 temp）缓冲期同型无上限重验成立；maxBatchSize 仅执行侧 executeBatch 分段（:324-325/:934/:972/:982），缓冲侧 grep 确无 maxEpoch/maxPending 旋钮，OOM 依赖 T×R 配比故 P2 维持。

### [nop-stream-state-connectors-C5-006] JDBC 2PC ledger（stream_epoch_ledger_v2）每 epoch 追加、无任何清理/归档路径（F4）

- **文件**: `nop-stream/nop-stream-connector-jdbc/src/main/java/io/nop/stream/connector/jdbc/JdbcTwoPhaseCommitSink.java:852-861,796-811`（DDL :796-811；全文件无 ledger DELETE/保留策略）
- **证据片段**:
  ```java
  private void writeLedgerEntry(Connection connection, String namespace, long epochId,
                                int subtaskIndex) throws SQLException {
      try (PreparedStatement ps = connection.prepareStatement(insertLedgerSql)) {
          ps.setString(1, namespace);
          ps.setLong(2, epochId);
          ps.setInt(3, subtaskIndex);
          ps.setTimestamp(4, CoreMetrics.currentTimestamp());
          ps.executeUpdate();      // 每 epoch × 每 subtask 一行，永久驻留
      }
  }
  ```
- **严重程度**: P2
- **并发场景**: 每 epoch commit 时一行（commit 线程/通知路径）；与 F4"无界账本只增不删"直接对应。
- **触发时序**: 确定性——checkpoint 间隔 T 的长驻作业每 T 追加 `subtask 数` 行；无 TTL、无压缩、无归档（grep 全模块仅数据表 keyed DELETE，无 ledger 清理）。
- **现状**: 台账行是幂等提交守卫的真相源（`(sink_namespace, epoch_id, subtask_id)` 主键探测）；语义上仅"可能被重提交的 epoch"需要行存留，已 durable 且已 commit 的 epoch 行在恢复重放窗口（`finishCommit` 只回放到恢复 epoch）过后即无消费方。
- **风险**: 行数 = epoch 数 × subtask 数线性增长（1 分钟间隔 × 单 subtask ≈ 52 万行/年）。运行期影响温和（主键探测 O(log n)），但账本无界违反 owner doc「历史与日志生命周期治理」（P-REQ-11）对观测历史的双约束治理先例；共享库多作业部署下账本膨胀还会拖累同库运维（备份、DDL、全表扫描）。C5 视角：每 commit 的台账探测随索引深度缓增，非内存峰值问题，定为 P2（F4 家族 + 长时运行必然触发）。
- **建议**: 提供带 watermark 的保留策略——恢复窗口 = 恢复点 epoch 起点之下界，低于 `min(全部活跃 sink 支路的恢复下界)` 的行可安全删除；在 commit 路径低频搭车清理（每 N epoch 一次 `DELETE ... WHERE epoch_id < ? AND namespace = ?`），owner doc P-REQ-11 治理键族扩展（如 `nop.stream.ledger.retention-epochs`）。清理必须以"namespace 维度"隔离（R5-CON-01 语义）。
- **失效面枚举**（F3——append-only 持久化账本全查）:
  - JDBC `stream_epoch_ledger_v2` 行（本体）✗
  - File sink `manifest.properties` epoch 键 — 同族，独立立项 007 ✗
  - RocksDB 增量 cp-N 目录 — F-02 滚动保留（默认 2）✓（仓库内已有正确范本）
  - `LocalFileCheckpointStorage` 持久 checkpoint 保留 — `maxRetainedCheckpoints`（core，范围外）✓
  - checkpoint 观测 history — 100 条 + retention 双约束（core，范围外）✓
- **信心水平**: 确定
- **误报排除**: 不是"平台基础设施不重审"——ledger 行为本模块写入路径；不是低频冷数据——每 checkpoint 周期必写；F4 家族为提示词指定定向检查项且本仓库有 AR-06 同族实证背景。
- **复核状态**: 已保留（P2 维持，2026-10-14 复核）——`writeLedgerEntry` 仅 INSERT（:852-861）、DDL 无保留/TTL 面（:796-811）、全文件无台账 DELETE 证实（grep 命中均为 CDC 数据面语义）；行数 = epoch × subtask 确定性增长成立。现状归属：本缺陷为 campaign 四向登记（C3-002、C7-001、C8-007 均已裁定归并；其中 C3/C7 复核按各自判级口径维持 P1，终裁归 7.1 合并裁定）；历史审计（2026-08-13 invariant-loop 两轮、r5/r6）均未就台账清理立案（C8 复核已证）；live code 未修。；二轮复核：维持保留 P2（2026-10-14 独立二轮复核）——全文件 delete 命中经逐条核对均为 CDC 数据面语义（KIND_DELETE/buildDeleteSql），无台账 DELETE/保留路径，DDL 无 TTL 重验成立；历史核对补充：r5 `03-connectors-security.md` CON-08 仅以 namespace 漂移致台账行无法归因积累为次生效应提及，未就「无保留策略」立案，「历史首次登记」标注维持。

### [nop-stream-state-connectors-C5-007] File 2PC manifest 每 epoch 追加一键且每次 commit 全量 load+rewrite（O(全部 epoch) 放大，F4 同族）

- **文件**: `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileTwoPhaseCommitSink.java:532-543,546-596,648-655`
- **证据片段**:
  ```java
  private Properties loadManifest() throws IOException {
      Properties props = new Properties();
      Path manifestPath = outputDirPath.resolve(MANIFEST_FILE);
      if (Files.exists(manifestPath)) {
          try (InputStream in = ...getResource().getInputStream()) {
              props.load(in);            // 每 commit 全量读入（键数 = 历史 epoch 总数）
          }
      }
      return props;
  }
  ...
  static String manifestKey(int subtaskIndex, long epochId) {
      return epochId + subtaskSuffix(subtaskIndex);   // 每 epoch 追加，无清理
  }
  ```
- **严重程度**: P2
- **并发场景**: 每 epoch commit 一次（`withManifestLock` 串行化，AR-14）；非竞态（manifest 一致性已由锁修复），纯规模增长。
- **触发时序**: 确定性——长驻作业每 checkpoint：`loadManifest`（O(E) 读+解析）→ 追加 1 键 → `updateManifestAtomically`（O(E) 序列化 + fsync + rename）。E = 输出目录生命周期内的 epoch 总数，跨作业重启持续累积（manifest 是恢复真相源，不可重置）。
- **现状**: manifest 键无删除路径（grep 无 remove/prune）；无保留/分段/滚动声明。幂等守卫只需要"可能被重提交的 epoch"的键（与 006 相同的窗口论证）。
- **风险**: 规模口径——1 分钟间隔跑 1 年 ≈ 52 万键 ≈ 20-30MB manifest；每 commit 的读+解析+全量重写+fsync 成本随 E 线性放大，commit 时延（持跨 JVM FileLock 期间）同步劣化并拉长 manifest 锁持有窗口（反压 checkpoint 周期）；P3 级磁盘占用 + P2 级 commit 放大。堆内瞬时物化 O(E)（Properties + TreeMap 排序拷贝 :565-570）。
- **建议**: 与 006 同一保留策略：恢复窗口下界以下的键安全裁剪（commit 路径低频搭车或独立 compaction 步骤——读旧 manifest、写裁剪版、原子 rename，复用现有 `updateManifestAtomically` 机制）；或 manifest 分段（按 epoch 区间分文件，commit 只触达当前段 + 守卫段）。owner doc 能力矩阵同步声明。
- **失效面枚举**（F3——manifest/账本同族）:
  - File manifest epoch 键（本体）✗
  - JDBC ledger（同族，006 立项）✗
  - `FileTwoPhaseCommitSink.recoveredEntryKeys` — 以 manifest 条目为界（与 manifest 同生，随裁剪策略同步受益）△
  - `.manifest.lock` / per-subtask tmp 名 — 固定文件名集 ✓
  - `tempPath/finalPath` 每 epoch 数据文件 — 用户可见输出物（交付语义本体，非账本；目录内文件数随 epoch 增长属产品语义，不立项）✓
- **信心水平**: 确定
- **误报排除**: manifest 是 exactly-once 提交记录、结构必需——缺陷不在其存在而在"无界 + 每 commit O(E) 全量重做"；不是低频路径——每 checkpoint 周期必执行且持锁。
- **复核状态**: 已保留（P2 维持，2026-10-14 复核）——`manifestKey` 每 epoch 追加、`loadManifest` 全量读（:532-543）、`updateManifestAtomically` 全量重写 + fsync + 原子 rename（:546-596）逐项证实；grep remove 命中均为 `pendingCommits` 内存面，manifest 键无裁剪路径成立。现状归属：C7-007（P3）为两构成面（本条 manifest 面 + C6-009 锁面）的合并复述，C7 复核已裁定归并；live code 未修。；二轮复核：维持保留 P2（2026-10-14 独立二轮复核）——manifestKey 每 epoch 追加（实际位置 :623-628，条目引用 ：648-655 有 ~25 行漂移，该段为 isEpochCommitted/finalPath 同族 manifest 面，实质无碍）、loadManifest 全量读（:540-555）、updateManifestAtomically 全量重写+fsync+ATOMIC_MOVE（:560-596）、remove 命中均为 pendingCommits 内存面，逐项重验成立，P2 维持。

---

## 覆盖度说明

- **精读文件**（数据面全量）: CepOperator、SharedBuffer、SharedBufferAccessor、NFA（process/advanceTime/extractPaths/LazyVerdictCache）、NopCepConfigs、SharedBufferCacheConfig；RocksDBKeyedStateBackend、RocksDBSnapshotSerDe、RocksDBMapState、RocksDBOptionConfig、RocksDBIncrementalSnapshotStrategy、RocksDBIncrementalRestore（部分）、RocksDBStateBackend；JdbcTwoPhaseCommitSink（全量）；FileTwoPhaseCommitSink（全量）、FileSourceReader（全量）、FileSource（sample/序列化面）、FileSplitEnumerator（集合面）、MessageSourceReader/MessageSourceFunction（全量）、MessageTopicSplitEnumerator（集合面）；BatchLoaderSource/SourceReader/SourceFunction/BatchConsumerSinkFunction（全量）；DebeziumCdcSourceReader（全量）、DebeziumCdcSourceFunction（状态/集合面）；KafkaSourceFunction/KafkaTwoPhaseCommitSink（全量）；AvroWireFormat/AvroSchemaRegistryClient（全量）。
- **范围外注记**（不做发现，供复核参考）: `nop-stream-core` 的 MemoryKeyedStateBackend/TwoPhaseCommitSinkFunction 基类/CheckpointSerDe（共享前缀"平台基础设施不重审"，但 002/005 的失效面已标注交界）；`RocksDBValueSerDe`/`RocksDBKeyEncoder` 每访问 JSON 分配属 C9 热路径维度；`_` 前缀生成文件未作为对象。
- **声明边界（不立项）汇总**: ① CEP 仅 Memory 后端（fail-fast 守卫在 `resolveKeyedBackend`，R5 CEP-1）；② RocksDB 内存控制旋钮缺省 0（`RocksDBOptionConfig` javadoc 显式声明 + 观测面齐备）；③ Avro 双缓存的换版不感知（owner doc 已知边界）；④ 无 window CEP pattern 的 partial match 累积（CEP 语义，堆内包络承载）。

## 深挖第 2 轮追加 — C5 内存有界性（nop-stream-state-connectors）

- **轮次**: 第 2 轮深挖追加（接续 `C5-memory-bounds.md` 第 1 轮 27 项无界风险清单 + 7 项发现 C5-001..007 + 4 项声明边界）
- **盲审边界**: 本轮仅读取本维度第 1 轮文件自身；未读取 `ai-dev/audits/` 其他目录、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`。owner doc（`docs-for-ai/03-modules/nop-stream.md` / `nop-stream-connectors.md`）按共享前缀要求读取用于包络对照。
- **深挖方法**: ①对第 1 轮未盘点的文件逐个精读容器字段（8 模块实例集合字段全量普查 + 未读文件清单比对）；②对第 1 轮 004 号发现做 F3 同族清扫复核（发现其失效面枚举存在漏网与误标）；③对"并发 subtask × 缓冲"做峰值叠加专项验算；④序列化中间体放大复查。
- **本轮 OOM 结论包络对照**: RocksDB 侧沿用第 1 轮包络——owner doc 未声明数值包络，功能包络 = "状态超出堆"（磁盘/off-heap 承载）；连接器侧沿用第 1 轮口径——owner doc 能力矩阵声明 pending 事务入 checkpoint 的语义（`nop-stream-connectors.md` 128/125 行）但**未声明**每 epoch 缓冲上限、pendingCommits 堆叠上限、ledger/manifest 保留策略。

---

### 本轮新增盘点面（第 1 轮未盘点文件的容器排查）

| 文件（第 1 轮未精读/部分精读） | 容器面 | 判定 |
|---|---|---|
| `RocksDBListState`（applyMigration/add） | 全 CF keys + **全反序列化 list 对象图**双收集（:60-67）；`add`/`addAll` 全列表 read-modify-write | **发现 008（P2）**；`add` 的 O(N) RMW 属声明 list 语义 + C9 热路径，不立项 |
| `AbstractRocksDBState.applyValueMigration`（共享 helper，4 个状态类调用） | 全 CF keys+values **原始字节**双列表（:70-78）——与 004 同型，第 1 轮误标"✓" | **发现 009（P2）** |
| `RocksDBAggregatingState` / `RocksDBReducingState` | 单槽 accumulator 缓存（key 相等才复用）+ 单值 RMW；`get`/`add` 单 key 单值 | 有界（声明语义），OK |
| `RocksDBInternalListState` / `InternalAppendingState` / `InternalAggregatingState` | 纯命名空间 key 代理，存储实现继承公共类 | 随公共类判定 |
| `RocksDBKeyedStateBackend.cachedStorageKeyFor`（:509-542） | **单槽**（last key/namespace 对）缓存，equals 失配即重编码 | O(1)，OK |
| `RocksDBValueSerDe`（deserializeList/Map/Object） | 每调用瞬态：bytes→String→JSON 树→typed 对象三段中间体；无跨调用驻留 | 无驻留；放大倍数计入 002/008 的峰值口径，独立分配浪费属 C9 |
| `RocksDBMetricsRecorder` | 固定 6 个 gauge，AtomicReference 单 db 句柄 | 有界，OK |
| `RocksDBIncrementalRestore`（全量精读，第 1 轮仅部分） | `sst-name-map` 侧车 readAllLines（SST 数为界）；`reconstructRocksdbDir` 文件级流式拷贝；`restoreRangeInto` 经 `copyColumnFamilyRange` 迭代内逐条 put（第 1 轮已判流式 ✓） | 有界，OK |
| `DebeziumCdcSource`（全量精读，第 1 轮未盘点） | split/enumerator 状态 JSON 序列化每次调用瞬态；表数为界 | 有界，OK |
| `DebeziumTableSplitEnumerator` / `DebeziumCdcEnumeratorState` | `shareBySubtask`/`unassignedTables`——表名字符串，include-list 启动钉定 | 有界，OK |
| `BatchLoaderSplitEnumerator` / `BatchLoaderEnumeratorState` / `BatchLoaderSplit` | 无集合字段（单游标语义） | OK |
| `MessageSinkFunction` | 无状态，同步 `messageService.send`，无缓冲 | OK |
| `CdcEventOps` | 纯静态 facade，无状态 | OK |
| `DebeziumSourceSupport` | config 拷贝（序列化 round-trip 瞬态），表数级 | OK |
| `FilePendingCommit` | 纯描述符（tempPath/recordCount/subtaskIndex），**不携带行数据** | OK（file sink 不受 010 影响） |
| `FileSourceEnumeratorState`/`MessageSourceEnumeratorState` 等 | split/offset 描述符级 | 有界，OK |
| CEP 未盘点面（NFAState/ComputationState/DeweyNumber/aftermatch 7 策略/4 个 adapter/conditions/NFACompiler/KeyEncoder 等） | `AfterMatchSkipStrategy` discardStates、match Map 均为**每事件/每匹配作用域**，无跨事件驻留；NFAState 队列 = per-key partial matches（CEP within 语义承载，第 1 轮清单 #5） | 有界，OK |

### 峰值叠加验算补遗（并发 subtask × 缓冲）——第 1 轮遗漏项

| 叠加面 | 单 subtask 稳态峰值 | 全局峰值（P = 并行度） | 判定 |
|---|---|---|---|
| JDBC 2PC sink：currentBuffer + pendingCommits + 快照 JSON 中间体 | **≈2× epoch 堆内 + 1× epoch JSON 瞬态**（第 1 轮 005 仅验算 1×） | ×P，且 pendingCommits 堆叠数无机制上界（见 010） | **发现 010（P2）** |
| File 2PC sink：currentBuffer + pendingCommits | 行落盘 temp 文件，pending 只存描述符 | ×P 有界（描述符级） | OK |
| Kafka 2PC sink | producer `buffer.memory` 32MB | ×P（producer 内存，非堆内累积） | OK |
| CEP：elementQueueState + SharedBuffer state + reconciledTimerKeys + 每 barrier 全量状态 JSON | per-key 有界（第 1 轮 #3/#5）+ 001 单调项 | ×P（key-group 路由分摊 key 基数）；快照 JSON 放大 = 全 subtask 状态 2 倍瞬态（Memory 后端声明包络内） | 包络内，OK（001 已立项） |
| Debezium/Message reader：桥接队列 256/split | 256 × 表数（include-list 钉定） | ×P 有界 | OK |
| RocksDB：sink 缓冲（010/005）+ 状态快照（002）同 barrier 叠加 | sink 侧 2× epoch + 状态侧全库物化同周期叠加 | ×P | 已在 002/005 风险栏声明，本轮验算确认叠加方向（checkpoint 体积增大 → durable 变慢 → 通知窗口拉长 → pendingCommits 堆叠加深的正反馈） |

### 序列化中间体放大复查（第 1 轮遗漏项）

- **checkpoint 载体**: JDBC sink 每 barrier 把全部未确认 epoch 的 DmlOp 行图重新序列化进 checkpoint JSON（快照逐 epoch 累积时体积正反馈）——并入 010。
- `RocksDBSnapshotSerDe` 序列化路径、`RocksDBKeyEncoder.serializeNamespace` 每 entry 中间体：第 1 轮 002 已覆盖，不重复。
- `RocksDBValueSerDe.deserializeList/Map` 每访问三段中间体（String→JSON 树→typed 对象）：单次调用瞬态、生命周期到调用结束，跨调用无驻留；其峰值放大已计入 008 的全 CF 收集口径。逐记录分配浪费属 C9 热路径维度（第 1 轮范围外注记维持）。
- `DebeziumSourceSupport.serializationRoundTripCopy` / 各 split serializer：config/split 粒度瞬态，有界。

---

## 发现条目

### [nop-stream-state-connectors-C5-008] RocksDBListState.applyMigration 全 CF 反序列化对象图驻留——004 失效面漏网的最重兄弟

- **文件**: `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/RocksDBListState.java:56-80`
- **证据片段**:
  ```java
  List<byte[]> keys = new ArrayList<>();
  List<List<Object>> lists = new ArrayList<>();
  try (RocksIterator it = backend.getDb().newIterator(cfHandle)) {
      for (it.seekToFirst(); it.isValid(); it.next()) {
          keys.add(it.key());
          lists.add(RocksDBValueSerDe.deserializeList(it.value(), descriptor.getValueType()));
          //                                             ^^^^^^^^^^^^ 每个值先完整反序列化为 Java 对象列表
      }
  }
  ```
- **严重程度**: P2
- **并发场景**: restore 期 schema 迁移路径（`verifySchemaCompatibility`→`applyMigration`，冷路径、单任务线程）；非竞态，规模触发。
- **触发时序**: 恢复时某 ListState 的 schema 指纹失配且注册了迁移函数 → 迭代**全 CF**，把每行的 list 值经 `deserializeList`（bytes→String→JSON 树→typed `List<Object>` 三段中间体）完整反序列化，与全部 key 一同驻留两个 ArrayList，直到第二遍迁移循环结束——峰值堆 ≈ 全 CF 字节数的 3-5 倍（对象头/装箱/每行 JSON 树瞬态叠加），远超 004 的 2×（原始字节）。
- **现状**: 第 1 轮发现 004 的失效面枚举将该迁移同族清扫为"`RocksDBListState/RocksDBValueState/RocksDBReducingState/RocksDBAggregatingState` — 单 key 单值/单 list 点查语义，无遍历面（list 值整体存取为声明语义）✓"——只核对了遍历读面，**漏核对了 applyMigration 写面**；本类实际持有全 CF 反序列化对象图，是同族中最重的物化形态。
- **风险**: 规模口径——RocksDB 功能包络 = "状态超出堆"（owner doc 无数值包络）；ListState 存储大列表（如 per-user 事件序列）时全库状态接近堆容量属正常使用形态，此时恢复迁移必 OOM，且发生在 restore 阶段（作业无法拉起、无更早 checkpoint 可回退，同 004 论证）。列表行值经 JSON 反序列化后放大倍数高于 004 的原始字节收集，OOM 阈值更低。
- **建议**: 与 004/009 同一修复方向：单遍迭代 + 逐行 `read→migrate→write`（iterator 隐式快照保证一致视图），不持有跨行收集列表；若担心迁移函数重入读自身 CF，可分批（每 1 万行 flush）。与 004 的修复共享同一 `RocksIterator` 流式包装模式，可一并落 `AbstractRocksDBState` 层的公共迭代 helper。
- **失效面枚举**（F3——迁移写面全族补全，修正第 1 轮枚举）:
  - `RocksDBListState.applyMigration`（本体，反序列化对象图全 CF 驻留，最重）✗
  - `RocksDBMapState.applyMigration` — 第 1 轮 004 已立项（原始字节双列表）✗
  - `AbstractRocksDBState.applyValueMigration` — 同型漏网，独立立项 009 ✗
  - core 侧 `Memory*State.applyMigration` — 内存后端状态本就在堆内，无放大 ✓
  - `RocksDBIncrementalRestore.reconstructRocksdbDir` — 文件级流式拷贝 ✓
- **信心水平**: 确定
- **误报排除**: 冷路径不豁免（restore 期 OOM 后果重于运行期，同 004 裁定）；"list 值整体存取为声明语义"豁免的是**点查读面**，不豁免迁移写面的跨行收集——声明语义只约束单 key 存取粒度，不约束"把全 CF 一次搬进堆"；不是生成文件。
- **复核状态**: 已保留（P2 维持，2026-10-14 复核）——keys + `deserializeList` 反序列化对象图双收集（`RocksDBListState.java:56-80`）逐行证实，为迁移族最重物化形态；第 1 轮失效面「✓」误标的修正成立（该枚举只核对了点查读面、漏核对迁移写面）；restore 期触发链与 004 相同。现状归属：live code 未修；与 004/009 同根因，修复归并同一流式迁移 helper。；二轮复核：维持保留 P2（2026-10-14 独立二轮复核）——keys + `deserializeList` 反序列化对象图双收集（:60-67）重验成立，为迁移族最重物化形态（3-5× 对 004 的 2× 口径合理）；第 1 轮失效面「单 list 点查语义 ✓」确只核对了读面，误标修正维持，P2 维持。

### [nop-stream-state-connectors-C5-009] AbstractRocksDBState.applyValueMigration 与 004 同型的全 CF 原始字节双列表物化——第 1 轮失效面误标"✓"的共享 helper

- **文件**: `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/AbstractRocksDBState.java:66-91`（调用方: `RocksDBValueState.java:45`、`RocksDBReducingState.java:55`、`RocksDBAggregatingState.java:95`、`RocksDBInternalAppendingState.java:73`）
- **证据片段**:
  ```java
  static void applyValueMigration(RocksDBKeyedStateBackend<?> backend, ColumnFamilyHandle cfHandle, ...) {
      List<byte[]> keys = new ArrayList<>();
      List<byte[]> values = new ArrayList<>();
      try (RocksIterator it = backend.getDb().newIterator(cfHandle)) {
          for (it.seekToFirst(); it.isValid(); it.next()) {
              keys.add(it.key());
              values.add(it.value());   // 整个 CF 的 keys+values 原始字节先全部驻留
          }
      }
  ```
- **严重程度**: P2
- **并发场景**: restore 期 schema 迁移冷路径（单任务线程）；非竞态，规模触发。
- **触发时序**: ValueState/ReducingState/AggregatingState/InternalAppendingState 任一状态的 schema 指纹失配且注册了迁移函数 → 该共享 helper 把**整个状态列族**的 keys+values 原始字节收集进两个 ArrayList（峰值堆 ≈ 全 CF 字节 × 2 + 单行反序列化瞬态），再逐行迁移写回。第 1 轮 004 的失效面枚举称其"单值状态迁移——单值粒度，无收集列表 ✓"，与代码事实不符（:71-77 正是跨行收集列表）。
- **现状**: 这是"collect-then-write-back"迁移同族的第 3 个实例（004=MapState、008=ListState、本条=共享 helper 覆盖其余 4 个状态类）；因是共享 helper，**一处修复即可同时覆盖 4 个状态类**——但也意味着第 1 轮若按其枚举结论"无需修"会留下 4 个状态类的恢复期 OOM 面。
- **风险**: 规模口径同 004/008——全库状态接近堆容量（RocksDB 正常使用形态）时恢复迁移必 OOM，作业死循环于"恢复→OOM"；accumulator 型状态（AggregatingState）通常单行较大（窗口聚合器对象图），2× 全 CF 字节的收集峰值在大 accumulator 场景同样敏感。
- **建议**: 与 004/008 同一修复：改单遍迭代逐行 read-migrate-write（iterator 隐式快照）；因三处同根因，建议在 `AbstractRocksDBState` 提供一个"流式迁移迭代"公共方法，三个入口（MapState/ListState/helper）收敛到同一实现——F3 修类别而非修实例。
- **失效面枚举**（F3——本 helper 的全部调用方逐一登记）:
  - `RocksDBValueState.applyMigration`（:44-46，经 helper）✗
  - `RocksDBReducingState.applyMigration`（:54-56，经 helper）✗
  - `RocksDBAggregatingState.applyMigration`（:94-97，经 helper；迁移后正确 invalidate accumulator 缓存 ✓）✗（仅物化面）
  - `RocksDBInternalAppendingState.applyMigration`（:71-74，经 helper，ListState 内部孪生亦走此面）✗
  - `RocksDBMapState.applyMigration` / `RocksDBListState.applyMigration` — 各自独立实现，004/008 已立项 ✗
- **信心水平**: 确定
- **误报排除**: 不是对第 1 轮已立项项的重复——004 立项对象是 `RocksDBMapState.applyMigration` 本体，本条是第 1 轮**明确判为无风险**（"✓"）的独立代码路径，属 F3 要求的漏网兄弟补登记；冷路径不豁免（同 004/008 裁定）；helper 的字节级收集无任何上限声明。
- **复核状态**: 已保留（P2 维持，2026-10-14 复核）——共享 helper 全 CF keys+values 双列表（`AbstractRocksDBState.java:66-91`）逐行证实；四个调用方核对无误（`RocksDBValueState` :44、`RocksDBReducingState` :54、`RocksDBAggregatingState` :94、`RocksDBInternalAppendingState` :72）；第 1 轮「单值粒度，无收集列表 ✓」确与代码事实不符，误标修正成立；一处修复覆盖 4 个状态类的杠杆论证成立。现状归属：live code 未修；与 004/008 同根因，修复归并。；二轮复核：维持保留 P2（2026-10-14 独立二轮复核）——共享 helper keys+values 双列表（:68-91）与四调用方（ValueState:45/ReducingState:55/AggregatingState:95/InternalAppendingState:73，其中 AggregatingState 迁移后 invalidateAccumulatorCache 正确）重验成立；第 1 轮「单值粒度，无收集列表 ✓」确与代码事实不符，误标修正与一处修复覆盖 4 状态类的杠杆论证维持，P2 维持。

### [nop-stream-state-connectors-C5-010] JDBC 2PC sink 的 pendingCommits 堆叠无上界守卫，且每 barrier 全量重序列化进 checkpoint 快照（第 1 轮"以未提交 epoch 数为界 ✓"的判定缺乏支撑机制）

- **文件**: `nop-stream/nop-stream-connector-jdbc/src/main/java/io/nop/stream/connector/jdbc/JdbcTwoPhaseCommitSink.java:631-650`（快照携带机制: `nop-stream/nop-stream-core/.../sink/TwoPhaseCommitSinkFunction.java:156-167`（范围外注记）+ `:250-258`（commit 失败 retain 路径））
- **证据片段**:
  ```java
  // DmlOp javadoc（:154-158）: "Buffered per epoch and persisted in
  //  pendingCommits; Serializable because the pending batch rides the checkpoint state."
  @Override
  public TaskStateSnapshot saveState(long epochId) throws Exception {
      ...
      synchronized (currentBuffer) {
          if (!currentBuffer.isEmpty()) {
              List<DmlOp> batch = new ArrayList<>(currentBuffer);
              getPendingCommits().put(epochId, batch);   // 行数据不落盘，整 epoch 堆内驻留
              currentBuffer.clear();
          }
      }
      return super.saveState(epochId);   // 基类把【全部】未确认 epoch 拷入快照 → checkpoint JSON
  ```
- **严重程度**: P2
- **并发场景**: 任务线程 saveState（barrier 处）与 runtime CheckpointCoordinator 的 finishCommit 通知线程（`nop-stream-runtime/.../CheckpointCoordinator.java:1523`）跨组件协调；规模与延迟双重触发，非竞态缺陷。
- **触发时序**: ①稳态——barrier N 的 saveState 把 R×T 条 DmlOp 移入 pendingCommits[N]，通知晚于 barrier N+1 的 saveState 到达时，单 subtask 同时持有 pendingCommits[N]（满额）+ 新 currentBuffer（增长中）+ 快照 JSON 序列化瞬态 ≈ **2-3× epoch 字节**（第 1 轮 005 仅验算了 currentBuffer 单项 1×）。②恢复堆叠——restore 期目标库不可用时，`restoreFromEpoch` 对 commit 失败的 epoch **显式 retain**（基类 :254-257 "retaining in pending for subsuming commit"）；任务起跑后每个新 barrier 追加新 epoch，而全部 retained epoch 在**每个**后续 checkpoint 快照中被反复整体 JSON 序列化——checkpoint 体积随未提交 epoch 数正反馈增长（体积↑→durable 变慢→通知窗口↑→堆叠↑）。全程无 `maxUnackedEpochs`/字节上限/typed fail-fast（grep 无 maxPending/maxEpoch/retention 配置项）。
- **现状**: 第 1 轮 005 的失效面枚举将 `pendingCommits` 判为"以未提交 epoch 数为界（正常 1-2 个）✓"——"正常 1-2 个"是经验值而非机制保证：无任何代码或配置强制该界，通知延迟（durable 慢、coordinator 排队）与恢复期 commit 失败 retain 是可具体化的超界触发链；且 sink 自身注释（:692-694）承认"recovery-heavy runs 下 pendingCommits 累积陈旧条目"是实际发生过的模式（ledger-guard 分支为此补了 remove）。
- **风险**: 规模口径——owner doc（`nop-stream-connectors.md` 128 行）声明"pending 事务入 checkpoint"为语义本体，但**未声明**堆叠上限；稳态 2-3× 叠加使 005 的 OOM 估算（T=5min、R=10k/s、行宽 1KB ≈ 3GB）实际变为 6-9GB/subtask，×P subtask 后远超第 1 轮给出的单 subtask 口径；恢复堆叠场景在共享库慢盘/目标库故障演练中可复现。与 002 叠加（RocksDB 作业 sink 侧 + 状态侧同 barrier 峰值）方向已在第 1 轮声明，本轮验算确认其正反馈结构。
- **建议**: ①在 JDBC sink（或基类）暴露 `maxPendingEpochs`/`maxPendingBytes` 配置：saveState 时若 pendingCommits 堆叠超限即 typed fail-fast（`ERR_STREAM_STATE_ERROR` + 堆叠水位），提示排查 commit 通知链路；②在 `nop.stream.*` 指标面暴露 pendingCommits 深度/字节数 gauge（当前无观测）；③恢复期 retain 的 epoch 数上限与 owner doc 能力矩阵同步声明（语义保留，边界显式化）。
- **失效面枚举**（F3——全部 2PC sink 的 pendingCommits 载荷形态）:
  - `JdbcTwoPhaseCommitSink.pendingCommits` — **行数据堆内驻留**，堆叠无上界（本体）✗
  - `FileTwoPhaseCommitSink.pendingCommits` — 仅 `FilePendingCommit` 描述符（tempPath/recordCount/subtaskIndex），行已落盘 temp 文件，堆叠=描述符级有界 ✓
  - `KafkaTwoPhaseCommitSink.pendingCommits` — 仅标记（数据在 producer/broker），高水位幂等重放有界 ✓
  - `BatchConsumerSinkFunction` — 非 CheckpointParticipant，batchSize 即 flush，失败 typed 上抛 ✓
  - `TwoPhaseCommitSinkFunction`（core 基类快照拷贝 :162-165 / retain :250-258）— 范围外注记，机制证据
- **信心水平**: 确定（无守卫 + 快照携带事实）；很可能（稳态 2-3× 峰值的实际 OOM 取决于 T×R×P 配比与通知时延）
- **误报排除**: pending 事务入 checkpoint 是 exactly-once 结构性需求，**语义本身不立项**（owner doc 已声明）；立项对象是"堆叠数无机制上界 + 无 fail-fast + 无观测"的边界配置缺陷——与第 1 轮 005 对 currentBuffer 的立项口径一致且不重叠（005=缓冲单项，本条=跨 epoch 堆叠 + 每 barrier 全量重序列化中间体）；"正常 1-2 个"的经验界不构成提示词认可的"有界声明"（无配置、无守卫、无文档声明）。
- **复核状态**: 已保留（P2 维持，2026-10-14 复核）——`saveState` 整 buffer 无界转存 `pendingCommits[epochId]`（:639-650）、基类每 barrier 把全部未确认 epoch 拷入快照（core `TwoPhaseCommitSinkFunction.java:156-167`）、restore commit 失败显式 retain（:250-258）、成功路径与 guard 命中分支 `remove(checkpointId)` 逐项证实；grep 全模块确无 `maxPending`/`maxEpoch` 类旋钮；sink 自身 WARN 注释自认 recovery-heavy 堆叠模式成立。现状归属：本条为 campaign 内该面唯一登记锚点（C8 复核将时长预算/固定超时耦合面交 C8-010 分立承接，不归并）；live code 未修。；二轮复核：维持保留 P2（2026-10-14 独立二轮复核）——saveState 整 buffer 转存 pendingCommits（:639-646）、core 基类 saveState 每 barrier 全量拷贝（`common/functions/sink/TwoPhaseCommitSinkFunction.java:155-168`）、commit 失败 retain（:256）、guard 命中 remove+WARN 注释自认 recovery-heavy 堆叠（:694-695）逐项重验成立；全模块 grep 确无 maxPending/maxEpoch 旋钮，「正常 1-2 个」无机制保证结论维持，P2 维持。

---

## 覆盖度说明

- **本轮新增精读**（第 1 轮未盘点或部分盘点）: `RocksDBListState`、`AbstractRocksDBState`、`RocksDBInternalListState`、`RocksDBAggregatingState`（+`resolveStorageValueType`）、`RocksDBReducingState`（add/get 面）、`RocksDBValueSerDe`、`RocksDBMetricsRecorder`、`RocksDBIncrementalRestore`（全量）、`RocksDBKeyedStateBackend.cachedStorageKeyFor` 段、`DebeziumCdcSource`（全量）、`DebeziumSourceSupport`、`DebeziumTableSplitEnumerator`、`MessageSinkFunction`、`CdcEventOps`、`FilePendingCommit`、`NFAState`、aftermatch 策略族与 4 个 adapter（每匹配作用域核验）、batch/debezium/message 各 EnumeratorState 值对象。
- **峰值叠加验算**: 上表补遗覆盖全部"subtask × 缓冲"叠加面；全局峰值结论——除 010（JDBC sink 堆叠）与 002/005 的已立项叠加外，其余叠加面均为 ×P 的有界放大。
- **明确无新发现的方向**（防止第 3 轮重复排查）: 消息/file/batch/debezium source 族容器（第 1 轮 #17-24 维持）；Avro 双缓存（#25 维持）；CEP per-key 结构（#1-6 维持，001 单调项除外）；RocksDB 单槽 key/accumulator 缓存；`sst-name-map` 侧车；`RocksDBMetricsRecorder`。
- **范围外维持**: nop-stream-core 的 MemoryKeyedStateBackend/TwoPhaseCommitSinkFunction 基类/CheckpointSerDe（共享前缀"平台基础设施不重审"；010 的基类机制仅作证据引用，立项锚点在 connector 模块）；`RocksDBValueSerDe` 逐记录分配属 C9。

---

# 第 2 轮续作（合并续写段：先前中断轮次产物完整保留于上方，编号连续不重叠）

> 本段为第 2 轮的独立续作深挖（第二次完整遍历）。上方中断轮次产物（盘点面表 + C5-008/009/010 + 覆盖度说明）**原文保留**；本续作按提示词规定的四段结构（本轮发现 → 排除项/定向核查 → 终止声明 → 覆盖声明）补充独立验证结果。盲审纪律同前：仅读取本维度第 1 轮文件（`C5-memory-bounds.md`）与 owner docs/基线，未读取 `ai-dev/audits/` 其他维度文件、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`。

## 1. 本轮续作发现

**无新增编号发现（C5-011 起：无）。** 续作独立重推结论：上方中断轮次的 C5-008 与 C5-009 被本续作**独立重推导完全复现**（`RocksDBListState.applyMigration` 的全 CF 反序列化对象图双收集、`AbstractRocksDBState.applyValueMigration` 的全 CF 原始字节双列表及第 1 轮失效面"✓"误标）——两个独立遍历收敛于同一证据（`RocksDBListState.java:60-67`、`AbstractRocksDBState.java:70-78`），条目可信度增强，正式复核状态维持"未复核"（独立审计属闭案流程）。C5-010 的模块内锚点由本续作补验：`saveState`（`JdbcTwoPhaseCommitSink.java:639-650`）确以 `new ArrayList<>(currentBuffer)` 拷贝转存且 `pendingCommits` 无任何上限守卫、成功提交路径 `remove(checkpointId)`（:695）；基类 retain 机制证据属 core 范围外，维持原注记形态。

## 2. 排除项/定向核查（续作新增，防第 3 轮重复排查）

| # | 定向面（第 2 轮深挖指令方向） | 核查结论 |
|---|---|---|
| 1 | **restore 侧 JSON 树中间体与 `KeyGroupRangeRestoreFilter` 过滤副本**（rescale 恢复路径，`RocksDBSnapshotSerDe.restoreState:386-391` 调用点） | `filterStateInfo`（core，`KeyGroupRangeRestoreFilter.java:126-144`）对每个保留 entry 做 `new LinkedHashMap<>(e)`——**叶级值共享引用的浅拷贝**，仅条目 map 元数据开销 ≈ (1/P)×N×~100B 瞬态；原 statesMap 在 restore 循环全程强可达（局部变量），峰值 = 全 JSON 树（002 已立项的输入本体）+ 元数据级过滤包装（<5% 叠加）。**不独立立项**（无独立触发链，P3 优化候选注记）；同路径的 key 双重重物化（filter 内 `rematerializeKey` + `putEntry` 再一次）为 restore 冷路径瞬态浪费，C9 维度。restore 主循环本身逐条 put（`restoreValueEntries:501-507` 等），无第二次全量物化 |
| 2 | **Kafka 反序列化输入侧批——超大批次/单条巨大消息是否绕过批上限** | 批记录数受 consumer `max.poll.records`（缺省 500）、批字节受 `fetch.max.bytes`/`max.partition.fetch.bytes`（Kafka 内建上限，经 `consumerProps` 透传可调）双重约束；单条超过 fetch 上限的消息会被整体取回（Kafka 语义）→ 单条原始 byte[] + Avro/UTF-8 解码对象图瞬态驻留，但 poll 循环逐条 `collectWithTimestamp` 引擎背压、**同时仅一条解码驻留**，无连接器侧累积结构。边界由 consumer 配置旋钮承载（平台基础设施缺省），连接器自身无缺陷。排除 |
| 3 | **Avro decode 单记录放大**（`AvroWireFormat.decode:85-104`） | `BinaryDecoder` 直接包装输入 byte[] 区间流式解码，`GenericRecord` 单记录生命周期；schema 双缓存键空间可枚举（第 1 轮 #25 / 声明边界 ③ 维持）。每记录新建 `GenericDatumWriter`/`ByteArrayOutputStream` 属 C9。排除 |
| 4 | **Debezium 事件批** | 嵌入引擎事件经 `ctx.collect` → `ArrayBlockingQueue(256)` 阻塞 put 背压（`DebeziumCdcSourceReader.java:130,324-335`）；引擎内部缓冲属 debezium/平台基础设施范围外。failure listener 注册表 register/unregister 在 `run()`/finally 配对（`DebeziumCdcSourceFunction.java:248,298`），以活跃 connector 实例数为界。`NopStreamOffsetBackingStore` 静态注册表以 connector 名为界（AR-03 首跑 clear）。排除 |
| 5 | **BatchLoaderSourceReader 流式声明复核** | `nextRecord()`（:160-181）逐批 `loader.load(batchSize)`，`batchCursor` 仅持单批迭代器；skip 路径消费即弃、不物化。第 1 轮 #23 判定维持 |
| 6 | **`FileTwoPhaseCommitSink.recoveredEntryKeys`（:118）** | 仅 `setPendingCommits`（:443-445）从恢复的 pendingCommits 灌入（每 subtask 恢复窗口 1-2 条），无运行期增长路径。第 1 轮 #16 判定维持 |
| 7 | **CEP 缓存统计 ledger**（深挖指令点名方向） | `SharedBuffer.logCacheStatistics`（:385-389）为 Guava 统计计数器周期日志（`CEP_CACHE_STATISTICS_INTERVAL` 缺省 30min，`CepOperator.onCacheStatisticsTimer` 专用回调），**无任何增长集合**。排除 |
| 8 | **CEP timer ledger checkpoint 形态**（`CepOperator.snapshotState:709-728`） | `timersForm` 列表 = 全部待触发 timer（key×时间戳）的 JSON-safe 拷贝，barrier 处瞬态 2×，受第 1 轮 #2 的 pending-timers 界约束；restore 侧 `rematerializeLedgerKey` 单 key JSON 重物化瞬态。有界，维持 |
| 9 | **`FileSource.sample` 全量文件列表**（`FileSource.java:407`） | `Files.walk(...).collect(toList())` 在记录 limit 生效前先物化全部 Path——但与 `FileSplitEnumerator`（:147-149）同一发现语义（BOUNDED source 全量枚举为声明边界，第 1 轮 #17），且 sample 为 CLI 单次运维工具路径（非长驻）。维持排除（P3 注记） |
| 10 | **JDBC keyed 折叠 map**（`writeKeyedOps:952-956`） | commit 期瞬态 `LinkedHashMap<String,DmlOp>`（epoch 同界，键串+引用开销）；flush 分段 `maxBatchSize`（:934,972,982）驱动侧有界。缓冲侧无界根因已由 005/010 立项，本面为其 commit 期放大注记，不独立立项 |
| 11 | **DebeziumTableSplitEnumerator / DebeziumCdcSource FLIP-27 面复核** | `shareBySubtask` 表集启动钉定、`deliverShare` 交付即 remove（:89）；split/枚举器状态 serializer 瞬态。排除 |
| 12 | **RocksDB 迁移族其余面** | `RocksDBValueState/ReducingState/AggregatingState/InternalAppendingState/InternalAggregatingState` 的 `get/add` 均单 key 点查（TTL 旁路驱逐正确）；`cachedStorageKeyFor` 与 accumulator 前缓存均单槽 O(1)；`RocksDBIncrementalRestore.readSstNameMap` readAllLines 以 SST 文件数（文件名字符串）为界、`reconstructRocksdbDir`/`copyColumnFamilyRange` 流式。排除 |

## 3. 终止声明

**累计发现清单**（第 1 轮 C5-001..007 + 第 2 轮 C5-008..010，共 10 项）：

| ID | 标题 | 严重度 |
|---|---|---|
| C5-001 | CepOperator.reconciledTimerKeys 每 key 单调增长永不清理 | P2 |
| C5-002 | RocksDB 全量 JSON checkpoint 整库物化堆内对象图（默认路径） | P1 |
| C5-003 | RocksDBMapState entries/keys/values/iterator 全量物化单 key map | P2 |
| C5-004 | RocksDBMapState.applyMigration 双列表物化整 CF | P2 |
| C5-005 | 2PC sink in-epoch 缓冲无每 epoch 记录数上限 | P2 |
| C5-006 | JDBC ledger 只增不删（F4） | P2 |
| C5-007 | File manifest 每 epoch 追加 + 全量重写 O(n) 放大（F4 同族） | P2 |
| C5-008 | RocksDBListState.applyMigration 全 CF 反序列化对象图驻留（004 失效面漏网） | P2 |
| C5-009 | AbstractRocksDBState.applyValueMigration 全 CF 原始字节双列表（第 1 轮"✓"误标修正） | P2 |
| C5-010 | JDBC pendingCommits 堆叠无上界守卫 + 每 barrier 全量重序列化 | P2 |

**终止判定：C5 维度深挖终止。** 依据：①第 1 轮 27 项无界风险清单 + 第 2 轮两次独立遍历（中断轮次 + 本续作）的并集已覆盖 8 模块全部"随输入增长"容器面——基线 grep（C1 static/C2 locks/C2 compute）圈定的每个集合/缓存/队列、第 1 轮"部分"标注文件的未读段（RocksDBIncrementalRestore 全量、DebeziumCdcSourceFunction 全量、FileSource 序列化面全量）、深挖指令的全部追加方向（反序列化输入侧批、序列化中间体 2× 叠加、峰值叠加验算、去重/清理辅助集合）均已核查并归档于两轮排除表；②第 2 轮两次遍历的**新增发现收敛**：中断轮次 3 项（008/009/010），本续作独立遍历 0 项新增且与 008/009 完全复现——边际收益已归零；③剩余未立项面均为：声明边界（CEP Memory-only、RocksDB 旋钮缺省 0、无 window partial match、Avro 换版不感知）、P3 优化候选（KeyGroupRangeRestoreFilter 元数据副本、sample 文件列表、每记录序列化分配→C9）、或范围外（core 基类/RocksDBValueSerDe 逐记录分配）。10 项发现的修复建议中 002/004/008/009 共享同一流式迭代改造模式、005/010 共享同一 pending 上限旋钮模式、006/007 共享同一保留策略模式——修复宜按三个族归并执行。

## 4. 覆盖声明（续作段）

- **本续作精读**（全文件）: `RocksDBIncrementalRestore`、`DebeziumCdcSourceFunction`、`FileSource`（含双 serializer + sample）、`RocksDBListState`、`RocksDBInternalListState`、`RocksDBInternalAppendingState`、`RocksDBReducingState`、`RocksDBAggregatingState`、`RocksDBValueState`（migration/get 面）、`AbstractRocksDBState`、`AvroWireFormat`、`DebeziumCdcSourceReader`、`DebeziumTableSplitEnumerator`、`MessageSource`、`BatchLoaderSourceReader`、`KeyGroupRangeRestoreFilter`（core，机制证据）。
- **本续作定点核查段**: `CepOperator`（:690-869 snapshot/restore/watermark + statistics timer grep）、`RocksDBSnapshotSerDe.restoreState`（:356-560）、`FileTwoPhaseCommitSink`（:280-460 commit/recoveredEntryKeys/setPendingCommits）、`JdbcTwoPhaseCommitSink`（:600-720 saveState/commit + :895-1024 flush/fold）、`KafkaSourceFunction`（全量）、`NFACompiler`（集合面 grep）、`SharedBuffer`/`CepOperator`（statistics 面全查）。
- **全模块清扫**: `readAllLines|readAllBytes|Collectors.toList|Files.walk|Files.list` 全量 grep——命中 11 处全部归类（增量恢复侧车/流式拷贝、FileSource 枚举声明语义、sample 工具路径）。
- **与中断轮次的关系**: 中断轮次覆盖度说明（上方）+ 本段合并构成第 2 轮完整覆盖；两段无矛盾——中断轮次的盘点面表、峰值叠加验算表、序列化复查结论经本续作独立重推均维持成立。

---

## 维度复核结论

- **复核人**: 独立复核子代理（2026-10-14，plan 2462 Phase 4；本阶段盲审解除）
- **复核输入**: 本维度全文（第 1 轮 27 项清单 + C5-001..007 + 第 2 轮两次遍历 C5-008..010）；同 campaign 已复核维度的交叉裁定（C3-async-chains、C7-long-running、C8-backpressure 的复核节）；`ai-dev/audits/cra-index.md`；历史审计（r4 2026-09-30、r6 2026-10-06、2026-08-13 invariant-loop）；git log。
- **复核方法**: 逐条重读 live code 并普查调用方——`CepOperator`（reconciledTimerKeys 全文件读写点、resolveKeyedBackend R5 CEP-1 守卫）、`RocksDBSnapshotSerDe`（map/keyed 快照段）、`RocksDBKeyedStateBackend`（:119 缺省开关、:826-836 分派、:559-572 迁移触发链）、`RocksDBMapState`/`RocksDBListState`/`AbstractRocksDBState`（迁移三实例逐行）、`JdbcTwoPhaseCommitSink`/`FileTwoPhaseCommitSink`/core `TwoPhaseCommitSinkFunction`（缓冲/快照携带/retain/台账 DDL+INSERT）；`entries()|keys()|values()` 生产调用方全 nop-stream 普查；`maxPending|maxEpoch` 类旋钮全模块 grep；owner doc（`nop-stream.md`/`nop-stream-connectors.md`）包络声明逐条对照。
- **总判定**: **保留 10、降级 1（C5-003，P2→P3）、驳回 0**。无一条适用「已声明边界+守卫」豁免——owner doc 对每 epoch 缓冲上限、pendingCommits 堆叠上限、ledger/manifest 保留策略、全量 checkpoint 物化取舍均无声明，与初审判定一致；live code 10/10 未修。

### 逐条判定

**C5-001 — 保留，P2 维持**。全文件仅 ：190 声明、:1268 contains、:1271-1274 add，无任何移除点；同文件 `registeredEventTimeTimersByKey` 的完整空集清理语义对照成立。现状：C7-005 同缺陷重复确证（已裁定归并本条）；r4 曾从正确性角度触及同结构、未从内存角度立案。

**C5-002 — 保留，P1 维持**。全量路径逐值反序列化 + entries/grouped 全量驻留、增量开关缺省 false、backend 缺省分派逐项证实；owner doc 无数值包络，「状态超出堆」功能包络内默认路径缺陷成立，P1 恰当。现状：C8-005 同点位重复（已裁定归并本条，C5 为 P1 主立案）；r6 ST-06 登记的「增量无生产装配」现已端到端存在但仍 opt-in 缺省关，与本条表述一致。

**C5-003 — 保留，降级 P2→P3**。物化行为证实，但「热路径反复调用」前提与调用方普查不符：生产侧唯一调用面是 `OverWindowOperator.rebuildView`（viewRebuilt 门控的一次性重建）+ test/diagnostic；初审判为遍历面的 TTL/迁移/快照均不经 collectMap（各自直迭代 CF），CEP 侧迭代因 R5 CEP-1 fail-fast 不可达 RocksDB。真实影响 = 恢复后一次性 per-key 全图物化 + Iterable 契约陷阱，无每记录放大，降 P3。

**C5-004 — 保留，P2 维持**。双列表全 CF 收集逐行证实；触发链确定（schema 指纹失配 + 已注册迁移函数）；restore 期 OOM 后果（作业无法拉起）成立，冷路径不豁免裁定维持。现状：与 008/009 同根因三实例，修复归并。

**C5-005 — 保留，P2 维持**。Jdbc/File 双面缓冲无上限、`maxBatchSize` 仅执行侧分段、缓冲侧无旋钮逐项证实；owner doc 声明 buffer-then-commit 语义但未声明每 epoch 上限，包络缺口随本条登记成立。现状：C8-003 同本体重复（已裁定归并本条）。

**C5-006 — 保留，P2 维持**。台账仅 INSERT、DDL 无保留面、全文件无台账 DELETE 证实；行数确定性增长成立。现状：campaign 四向登记（C3-002、C7-001、C8-007 均裁定归并；C3/C7 复核按各自判级口径维持 P1，本维度维持 P2，终裁归 7.1 合并裁定）；历史审计未立案。

**C5-007 — 保留，P2 维持**。manifest 每 epoch 追加 + 每 commit 全量读/重写/fsync 逐项证实；manifest 键无裁剪路径成立。现状：C7-007（P3）为构成面合并复述（本条 manifest 面 + C6-009 锁面），已裁定归并。

**C5-008 — 保留，P2 维持**。反序列化对象图双收集逐行证实，迁移族最重物化形态；第 1 轮失效面「✓」误标修正成立。现状：与 004/009 同根因，修复归并。

**C5-009 — 保留，P2 维持**。共享 helper 双列表逐行证实；四个调用方（Value/Reducing/Aggregating/InternalAppending）核对无误；第 1 轮「单值粒度无收集列表 ✓」确与代码事实不符，误标修正成立。现状：与 004/008 同根因，修复归并；一处修复覆盖 4 个状态类。

**C5-010 — 保留，P2 维持**。saveState 无界转存、基类每 barrier 全量快照携带、restore commit 失败 retain、无任何上限旋钮逐项证实；「正常 1-2 个」确无机制保证，sink 注释自认 recovery-heavy 堆叠模式成立。现状：campaign 内该面唯一登记锚点（C8 的时长预算面由 C8-010 分立承接，不归并）。

### 现状标注统计

- **live code 状态**: 10/10 未修（git log 近期对涉事文件的提交均为 SerDe 收敛/CDC 硬化/守卫族，无内存面修复）。
- **同 campaign 跨维重复（对方已裁定归并至本维度，7.1 修复并轨）**: 5 处——C7-005→C5-001、C8-005→C5-002、C8-003→C5-005、C3-002/C7-001/C8-007→C5-006（四向归一，其中 C3/C7 判级 P1）、C7-007→C5-007。
- **本维度首次立项新锚点**: C5-003、C5-004/008/009（迁移族三实例）、C5-010（C5-001/002/005/006/007 虽有跨维归并对象，主立案锚点均在本维度）。
- **历史审计交叉**: r4（2026-09-30）从正确性角度触及 reconciledTimerKeys 结构（非内存立案）；r6 ST-06 从「增量装配缺失」角度登记 checkpoint 面（现已修，与 C5-002 的「默认全量」表述兼容）；ledger/manifest 保留策略在历史各轮均未立案——C5-006/007 为首次登记。

## 最终保留项

| ID | 初审级 | 复核判定 | 现状归属 |
|---|---|---|---|
| C5-001 | P2 | 保留 P2 | live 未修；C7-005 归并本条；r4 有正确性面先例、内存面首次立案 |
| C5-002 | P1 | 保留 P1 | live 未修；C8-005 归并本条（P1 主立案）；r6 ST-06 相邻面已修、本面维持 |
| C5-003 | P2 | **降级 P3** | live 未修；本维度唯一登记；热路径前提经调用方普查证伪、物化行为与契约陷阱维持 |
| C5-004 | P2 | 保留 P2 | live 未修；与 008/009 同根因三实例，修复归并流式迁移 helper |
| C5-005 | P2 | 保留 P2 | live 未修；C8-003 归并本条（含 spill 建议并入执行） |
| C5-006 | P2 | 保留 P2 | live 未修；campaign 四向登记（C3-002/C7-001/C8-007 归并；C3/C7 维持 P1，终裁归 7.1） |
| C5-007 | P2 | 保留 P2 | live 未修；C7-007 构成面归并本条 |
| C5-008 | P2 | 保留 P2 | live 未修；迁移族最重实例，与 004/009 归并 |
| C5-009 | P2 | 保留 P2 | live 未修；共享 helper 一修盖 4 状态类，与 004/008 归并 |
| C5-010 | P2 | 保留 P2 | live 未修；campaign 唯一锚点；C8-010 分立承接时长预算面 |

**修复排批提示**: 三个修复族——①流式迭代族 {C5-002, C5-004, C5-008, C5-009}（同一 RocksIterator 流式包装模式，P1 建议与 002 同批评估）；②pending/epoch 上限旋钮族 {C5-005, C5-010}；③持久化账本保留策略族 {C5-006, C5-007}（与 C3-002/C7-001 的 P1 判级同批裁定）。C5-001 独立小改（清理点同步 remove）；C5-003（P3）随流式迭代族顺带收敛。

---

## 维度复核结论（独立复核子代理）【二轮复核】

- **复核人**: 独立复核子代理·二轮（2026-10-14；与初审者及首轮复核均为不同角色，独立对 live code 重验后裁定，不承袭首轮结论）
- **复核输入**: 本文件全文（第 1 轮 27 项清单 + C5-001..010 + 首轮复核节）；owner doc（`docs-for-ai/03-modules/nop-stream.md`、`nop-stream-connectors.md`）包络声明逐条对照；历史审计现状核对仅限 `ai-dev/audits/` 下 nop-stream 相关历史目录（r4 `2026-09-30-0530`/01-cep-correctness.md、r5 `2026-10-05-0523`/01+03、r6 `2026-10-06-0522`/02-state-backend-serialization.md + summary）与 git log；未读取 `ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`。
- **复核方法**: 逐条重读 live code——`CepOperator`（reconciledTimerKeys 全文件读写点 grep + reconcile 方法全文）、`RocksDBSnapshotSerDe`（map 段 grouped/groupedMapValues + keyed 段 entries/entryWriter 逐值 deserialize）、`RocksDBKeyedStateBackend`（:119 缺省开关与 javadoc、:826-836 分派、:559-572 迁移触发链、cleanupExpiredEntries 面）、`RocksDBMapState`（collectMap/entries 族/isEmpty/applyMigration 逐行）、`RocksDBListState`/`AbstractRocksDBState`（迁移两实例 + 四调用方 grep）、`JdbcTwoPhaseCommitSink`（currentBuffer/saveState/commit guard/ledger DDL+INSERT/全文件 delete 语义逐条归类）、`FileTwoPhaseCommitSink`（currentBuffer/manifestKey/loadManifest/updateManifestAtomically/remove 命中归类）、core `TwoPhaseCommitSinkFunction`（快照全量拷贝/retain）；`MapState.entries()|keys()|values()` 生产调用方全仓普查（含 EquiJoinOperator/OverWindowOperator/PerWindowKeyedStateStore/NamespaceAwareMapState 逐一消歧）；`maxPending|maxEpoch` 全模块 grep；基线后 `nop-stream/` 提交逐条核对（均为 test/runtime 修复，无涉事文件改动，工作区 diff 为空）。
- **总判定**: **保留 9、维持首轮降级 1（C5-003，P3）、驳回 0**。二轮独立验证未能推翻任何一条发现的证据链与增长/物化路径；首轮对 C5-003 的降级前提（调用方普查）经独立复现成立，维持其 P3。live code 10/10 未修。

### 逐条判定表（发现编号 × 原判级 × 二轮判定 × 理由 × 现状标注）

| 编号 | 原判级 | 二轮判定 | 一句话理由 | 现状标注 |
|---|---|---|---|---|
| C5-001 | P2 | 保留 P2 | 全文件仅 ：190/:1268/:1271-1274 三个读写点、无 remove/clear 重验成立；单键 ~64B 小增量，与 006/007 族判级一致性支持 P2（字面「必然无界增长」的 P1 读法经权衡否决） | 已知未修复（campaign 内新发现，本维度主立案锚点；同缺陷 C7-005 已裁定归并本条；r4 曾从 PT+comparator 正确性角度触及同结构、未从内存角度立案——内存面为本 campaign 首次登记） |
| C5-002 | P1 | 保留 P1 | 全量物化面、增量缺省 false（javadoc 自认 full-scan 为默认）、缺省分派逐项重验成立；「状态超出堆」功能包络内默认路径周期性整库过堆无可反驳，包络内高概率 OOM 判据满足 | 已知未修复（campaign 内新发现主锚点；同点位 C8-005 已裁定归并本条；r6 ST-06「增量装配缺失」相邻面现已修复——装配端到端存在但仍 opt-in 缺省关，与本条「默认=全量物化」表述兼容） |
| C5-003 | P2（首轮降 P3） | 维持降级 P3 | 调用方普查独立复现：生产迭代面仅 viewRebuilt 门控的一次性 restore 兜底 + test 源码树，TTL/迁移/快照均直迭代 CF，「每记录放大」前提证伪；物化行为与 Iterable 契约陷阱维持，P3（优化候选+潜在契约陷阱）恰当 | 已知未修复（本维度唯一登记锚点，历史无先例） |
| C5-004 | P2 | 保留 P2 | 双列表全 CF 收集与触发链（指纹失配+已注册迁移函数）重验成立；触发条件性不支持升 P1，restore 期后果支持不降级 | 已知未修复（本维度新发现；与 008/009 同根因三实例，修复归并流式迁移 helper） |
| C5-005 | P2 | 保留 P2 | Jdbc/File 双面缓冲期同型无上限、maxBatchSize 仅执行侧重验成立；owner doc 未声明每 epoch 上限，包络缺口登记成立；OOM 依赖 T×R 配比，P2 恰当 | 已知未修复（campaign 内新发现主锚点；C8-003 已裁定归并本条） |
| C5-006 | P2 | 保留 P2 | 全文件无台账 DELETE（delete 命中均为 CDC 数据面）、DDL 无保留面重验成立；确定性增长成立，运行期影响温和，P2 与族内一致性相符 | 已知未修复（campaign 四向登记：C3-002/C7-001/C8-007 均裁定归并本条，其中 C3/C7 按各自维度判级维持 P1，终裁归 7.1 合并裁定；历史各轮未就「无保留策略」立案——r5 CON-08 仅以 namespace 漂移的行积累为次生效应提及，本面为首次登记） |
| C5-007 | P2 | 保留 P2 | manifestKey 每 epoch 追加 + 每 commit 全量读/重写/fsync + 无裁剪路径重验成立；条目引用 ：648-655 有 ~25 行漂移（实为 :623-628），实质无碍 | 已知未修复（campaign 内新发现主锚点；C7-007 构成面已裁定归并本条） |
| C5-008 | P2 | 保留 P2 | keys+反序列化对象图双收集重验成立，迁移族最重物化形态；第 1 轮失效面「✓」误标修正维持 | 已知未修复（本维度新发现；与 004/009 同根因，修复归并） |
| C5-009 | P2 | 保留 P2 | 共享 helper 双列表与四调用方逐行重验成立；第 1 轮「单值粒度无收集列表 ✓」确与代码事实不符，一修盖 4 状态类的杠杆论证维持 | 已知未修复（本维度新发现；与 004/008 同根因，修复归并） |
| C5-010 | P2 | 保留 P2 | saveState 整 buffer 转存 + 基类每 barrier 全量快照携带 + retain 路径 + 无任何上限旋钮重验成立；「正常 1-2 个」确无机制保证，sink 注释自认 recovery-heavy 堆叠 | 已知未修复（campaign 内该面唯一登记锚点；C8-010 分立承接时长预算面，不归并） |

### 二轮不确定项（显式列出）

1. **C5-002 的 OOM 阈值口径**: 物化行为为代码级确定，但「峰值堆 ≈ 序列化字节数 2-5 倍」为经验估计（对象头/装箱/每 entry LinkedHashMap），未做实测；P1 判级不依赖该倍数的精确值（只要放大系数 >1，包络内大状态必触顶），风险表述与判级解耦。
2. **C5-005/C5-010 的实际 OOM 条件**: 依赖 checkpoint 间隔 × 吞吐 × 行宽 × 并行度 × commit 通知时延的配置配比，二轮未做压测复现；按口径归 P2（边界配置缺陷）而非 P1（包络内高概率），与首轮/首轮复核口径一致。
3. **C5-006 判级的 7.1 终裁依赖**: 本维度维持 P2；C3/C7 维度对同本体维持 P1（各自维度判级口径不同），最终判级以 7.1 合并裁定为准，二轮不预设结果。
4. **C5-003 的附带观察（不构成新发现，仅记录）**: `OverWindowOperator.rebuildViewFromKeyedState` 在未显式设置 current key 的上下文调用 `bufferState.entries()` 时，RocksDBMapState.collectMap 按「当前 key」前缀扫描的实际语义值得实现方留意——属正确性/语义面，超出本维度复核职责，不予立案。
5. **历史现状核对边界**: r4/r5/r6 之外的更早历史轮次（2026-05/06 族）未逐目录核对，依据本 campaign 首轮复核对历史各轮「ledger/manifest 保留策略未立案」的核查结论采信；该采信不影响二轮判定的证据链独立性（所有 10 条的成立性均以 live code 独立重验为准）。

---

## 最终保留项【二轮复核后】

| 编号 | 最终严重程度 | 文件路径 | 一句话摘要 | 现状 |
|---|---|---|---|---|
| C5-001 | P2 | `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java` | reconciledTimerKeys 每 distinct key 永久追加、无任何清理路径，key 强引用阻止回收，单调侵蚀 Memory-only 堆内包络 | live 未修；C7-005 归并本条；内存面首次登记（r4 有正确性面先例） |
| C5-002 | P1 | `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/RocksDBSnapshotSerDe.java`（+`RocksDBKeyedStateBackend.java`） | 默认全量 JSON checkpoint 把整个 RocksDB 状态逐值反序列化物化为堆内对象图，增量路径 opt-in 缺省关；包络内高概率周期性 OOM | live 未修；C8-005 归并本条（P1 主立案）；r6 ST-06 相邻面已修、本面维持 |
| C5-003 | P3 | `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/RocksDBMapState.java` | entries/keys/values/iterator 每调用全量物化当前 key 的整张 map，Iterable 契约与实现错配；生产热路径无调用方放大 | live 未修；本维度唯一登记锚点（首轮降 P3，二轮维持） |
| C5-004 | P2 | `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/RocksDBMapState.java` | applyMigration 把整个 CF 的 keys+values 双列表物化进堆，restore 期 schema 迁移大库必触顶、作业无法拉起 | live 未修；与 008/009 同根因三实例，修复归并流式迁移 helper |
| C5-005 | P2 | `nop-stream/nop-stream-connector-jdbc/src/main/java/io/nop/stream/connector/jdbc/JdbcTwoPhaseCommitSink.java`（同族 `FileTwoPhaseCommitSink.java`） | 2PC sink 的 in-epoch 缓冲随 epoch 体量无界（无每 epoch 记录数/字节上限配置、无 fail-fast） | live 未修；C8-003 归并本条（spill 建议并入执行） |
| C5-006 | P2 | `nop-stream/nop-stream-connector-jdbc/src/main/java/io/nop/stream/connector/jdbc/JdbcTwoPhaseCommitSink.java` | JDBC 台账 stream_epoch_ledger_v2 每 epoch×subtask 追加、无任何清理/TTL/归档路径（F4） | live 未修；campaign 四向登记归并锚点（C3/C7 面 P1，终裁归 7.1） |
| C5-007 | P2 | `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileTwoPhaseCommitSink.java` | manifest 每 epoch 追加一键且每次 commit 全量 load+rewrite（O(全部 epoch) 放大 + fsync 持锁窗口拉长） | live 未修；C7-007 构成面归并本条 |
| C5-008 | P2 | `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/RocksDBListState.java` | applyMigration 全 CF 反序列化对象图驻留（迁移族最重物化形态，峰值 ≈ 全 CF 字节 3-5 倍） | live 未修；与 004/009 同根因，修复归并 |
| C5-009 | P2 | `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/rocksdb/AbstractRocksDBState.java` | 共享 helper applyValueMigration 全 CF 原始字节双列表物化，一处修复覆盖 4 个状态类 | live 未修；与 004/008 同根因，修复归并 |
| C5-010 | P2 | `nop-stream/nop-stream-connector-jdbc/src/main/java/io/nop/stream/connector/jdbc/JdbcTwoPhaseCommitSink.java`（机制证据 core `TwoPhaseCommitSinkFunction.java`） | pendingCommits 跨 epoch 堆叠无上界守卫 + 每 barrier 全部未确认 epoch 重序列化进 checkpoint，恢复期正反馈增长 | live 未修；campaign 唯一锚点；C8-010 分立承接时长预算面 |

**二轮修复排批提示**（与首轮一致，经独立验证维持）: ①流式迭代族 {C5-002, C5-004, C5-008, C5-009}；②pending/epoch 上限旋钮族 {C5-005, C5-010}；③持久化账本保留策略族 {C5-006, C5-007}（与 C3-002/C7-001 同批裁定）；C5-001 独立小改；C5-003（P3）随流式迭代族顺带收敛。
