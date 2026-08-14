> Audit Status: closed
> Audit Type: open-ended
> Mission: nop-stream-invariant-loop
> Processed: 2026-08-13 — AR-01 (P0)/AR-02 (P1) → plan `ai-dev/plans/2026-08-13-1243-1-nop-stream-engine-state-recovery-and-timer-fixes.md`；AR-03 (P1) → plan `2026-08-13-1243-3-nop-stream-cross-module-contract-remediation.md`；AR-04~21 (P2) → roadmap `## Follow-up Backlog`（`ai-dev/backlog/nop-stream-invariant-loop-roadmap.md` 2026-08-13 0805 open-audit 批次）

# nop-stream 开放式对抗审查报告（2026-08-13 0805 批次）

## 基本信息

- **审核模块**: `nop-stream/` 全模块组（core / runtime / cep / flow / rocksdb / connector / connector-batch / connector-jdbc / connector-debezium / fraud-example）+ 跨模块 nop-message-debezium（offset store）
- **审核日期**: 2026-08-13（live 基线；cycle 3 稳态暂停后，无未提交代码变更）
- **方法**: 3 个并行子 agent 深读（connector 族 / flow+XDSL+错误码 / rocksdb+restore 族）+ 主 agent 逐条对 live 源码独立复核（本文所有发现均经实际代码文本验证；TextScanner/JsonSerializer 的 JSON 数值解析行为已读 nop-kernel 源码确认）

## 去重声明

- 前次 open-audit（2026-08-12-1217）的 P0-01/P0-02/P0-03/P1-01~05 已由 plan 0132-1/2/3 修复——本次对修复代码（TaskProcessingTimeService / ProcessingTimeServiceDriver / SupervisionLoop rewire / CheckpointSerDe JSON forms / markFinishedChannel 竞态 / CEP timer 注册表对称 / fan-out close）逐行复核，**仅报告修复的残余缺口**（见 AR-02），修复本身完整性无其他问题。
- 已知 backlog 项（P1-XDSL-5/6、P1-INV-1/2、P2-INV-4/5/6、P2-03-01/02、P2-09-01~10 等）**不重报**；但发现其中两项的**新变体/新实例**（AR-04 的 Long 键变体、AR-20 的算子级 toString 键变体）按去重规则报告并标注同族关系。
- 门禁复核：本次未重跑全量测试（只读审计）；前次 I5 基线 2895/0 视为有效。

---

## 发现清单（按优先级）

---

### [AR-01] [P0] Memory 后端 Long 键状态在 JSON checkpoint 恢复后 100% 静默丢失（默认配置路径；RocksDB 后端同 checkpoint 恢复正确——跨后端分叉）

**P0 判定理由**: 默认配置（MemoryStateBackend + storageType="local" JSON 持久化）下，任何 Long 键（常见：事件 id / 用户 id）有状态作业在 checkpoint 恢复后**全部键控状态静默从空开始**，无任何报错；同一 checkpoint 在 RocksDB 后端恢复正确——是 P2-INV-4（POJO 键）的数值键新变体，影响面大且机制确定。

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryStateSerDe.java:814-820`；`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/TypedNamespaceAndKey.java:29-39`；`nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/core/common/state/backend/rocksdb/RocksDBKeyEncoder.java:258-267`
- **证据片段**:
  ```java
  // MemoryStateSerDe.deserializeKey — 键不按 keyType 重物化
  private Object deserializeKey(Object obj) { return obj; }
  // TypedNamespaceAndKey.equals — 类敏感
  if (key != null && that.key != null
          && !key.getClass().equals(that.key.getClass())) { return false; }
  // RocksDBKeyEncoder.jsonToKey — 按 keyType 重物化（免疫）
  if (keyType != null && keyType != Object.class) {
      return JsonTool.parseBeanFromText(json, keyType);
  }
  ```
- **严重程度**: P0
- **现状**: 快照路径 `serializeKey` 原样存 Long(123)；JSON 持久化（默认 `storageType="local"`，`CheckpointConfig.java:47`；`CheckpointSerDe.serializeTaskStateSnapshot:330` 原样入 JSON）经 `JsonSerializer` 写 `value.toString()` → `"123"`；恢复时 `TextScanner.parseInteger`（`nop-kernel/nop-commons/.../TextScanner.java:665-678`，已读源码确认）**先试 `Integer.parseInt` 成功** → Integer(123)；`deserializeKey` 原样 → `TypedNamespaceAndKey(ns, Integer(123))`；live 查找用 Long(123) → `equals` 类检查失败 → 全部 miss。键值 > 2^31 时 Long 回退解析恰好幸存。RocksDB 路径键经字节编码 + keyType 重物化，同 checkpoint 恢复正确。
- **风险**: 默认配置 + Long 键 + 崩溃/重启恢复 = 聚合、窗口、键控状态全部静默归零，产出错误结果且无任何诊断；跨后端同一 checkpoint 行为不一致使问题更难排查。
- **建议**: `deserializeKey` 按 `backend.getKeyType()` 重物化（与 RocksDB `jsonToKey` 对齐）；或 `TypedNamespaceAndKey.equals/hashCode` 对 Number 类型宽容；至少对非 String 数值键做 round-trip 断言测试（先红后绿）。
- **信心水平**: 确定（序列化→解析→equals 三段机制全部源码验证）
- **发现来源视角**: 跨后端一致性侦探（异常路径侦探）

---

### [AR-02] [P1] Processing-time 定时器只在数据面推进点被触发：空闲任务（无数据流动）永不 fire —— plan 0132-1 生产接线修复的残余缺口

**P1 判定理由**: 上一轮 P0-01/P1-02 的修复（生产接线 ProcessingTimeService）只对"数据持续流动"的任务生效；空闲期 PT 窗口不发射、cleanup 定时器不触发（状态增长复燃）、CEP PT 超时不判定——静默行为缺陷，测试恰好只覆盖了数据流和 EOS 后 drain。

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/ProcessingTimeServiceDriver.java:112-128`；`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/StreamTaskInvokable.java:730-773`；`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/InputGate.java:417-474,505-540`
- **证据片段**:
  ```java
  // Driver：fire mail 投递到 mailbox（非阻塞 put）
  if (due) { mailbox.put(Mail.control(this::fireDueTimersOnTaskThread, "processing-time-timer-fire")); }
  // StreamTaskInvokable.processInputGate：mail 只在 read() 返回后的循环顶 drain
  if (mailboxExecutor.processAvailableMails()) { break; }
  Optional<StreamElement> elementOpt = inputGate.read();   // 空闲时永不返回
  // InputGate.readMultiChannel：无元素 → parkNanos(10ms) → continue retry（忙等，永不返回 null）
  LockSupport.parkNanos(10_000_000L);
  ```
- **严重程度**: P1
- **现状**: `readMultiChannel`/`readSingleChannel` 在无数据时忙等（50ms bounded read + 10ms park，`continue retry`），**从不返回**；mail drain 只发生在（a）`read()` 返回后循环顶（b）SOURCE `collect()`（`StreamSourceOperator.java:253-255`）。任务空闲时 fire mail 在 mailbox 中滞留，直到下一条数据到达。`TestProcessingTimeWindowProductionE2E` 用有界源 + 持续发射 + 源结束后的末次 drain 掩盖了该缺口（fire mail 在 `run()` 返回后 `drainControlMails()` 补 drain）；无任何"空闲期 wall-clock 触发"测试。
- **风险**: 无界源 + 突发流量（Kafka 式源空闲数小时）→ PT 窗口在窗口边界后很久（甚至永不）发射；PT cleanup timer 空闲期不触发 → 窗口状态无限增长（P1-02 的原缺陷在空闲期完整复燃）；CEP PT 模式 `within()` 超时仅在数据到达时判定。
- **建议**: 空闲期也需要周期性 drain——最低成本方案：`readSingleChannel`/`readMultiChannel` 全 channel 空转 N 次（约 100ms）后返回空 Optional 让循环顶 drain（与 `barrierAlignmentTimeout` 的 50ms 轮询同构），或驱动 mail 在忙等循环内检查 mailbox；补"发射结束后空闲期窗口仍按墙钟触发"的回归测试。
- **信心水平**: 确定（代码路径逐行验证；测试掩蔽机制已确认）
- **发现来源视角**: 组合爆炸测试者 / 异常路径侦探

---

### [AR-03] [P1] Debezium offset 静态注册表永不清洗：同 JVM 复用连接器名即从陈旧 offset 续跑；未命名连接器共享 `_default_` 桶互相污染

**P1 判定理由**: Nop 服务端多 pipeline 共享 JVM 是常态；新作业（无 checkpoint）首跑即静默从上次运行的 offset 续跑（跳过数据），且无任何日志/报错——静默事件丢失。

- **文件**: `nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/engine/NopStreamOffsetBackingStore.java:41-48,78-96`；`nop-stream/nop-stream-connector-debezium/src/main/java/io/nop/stream/connector/debezium/DebeziumCdcSourceFunction.java:232-236,260-266`
- **证据片段**:
  ```java
  private static final ConcurrentHashMap<String, ConcurrentHashMap<ByteBuffer, ByteBuffer>> REGISTRY = ...;
  public static NopStreamOffsetBackingStore forConnector(String connectorName) {
      ConcurrentHashMap<ByteBuffer, ByteBuffer> shared =
          REGISTRY.computeIfAbsent(connectorName, k -> new ConcurrentHashMap<>());   // 永不移除
  public static void clearConnector(String connectorName) { REGISTRY.remove(connectorName); }  // 仅测试调用
  // DebeziumCdcSourceFunction.initializeState state==null（首跑）→ forConnector(resolveConnectorName())
  // resolveConnectorName() 未配置 name → "_default_"
  ```
- **严重程度**: P1
- **现状**: 首跑路径（state==null）绑定到静态共享 map，**不清理已有 offset**；`clearConnector` 仅测试调用（`TestDebeziumCdcCheckpoint.java:54`）。同 JVM 内先前跑过同连接器名（含崩溃未 checkpoint 的运行、redeploy、不同 pipeline 复用名）→ Debezium 引擎从陈旧 offset 续跑；两连接器都未配 `name` → 共享 `_default_` 桶互相覆盖 offset。
- **风险**: 新作业静默跳过源数据（snapshot 阶段被跳过）；无法诊断（offset 不落任何持久化）。
- **建议**: 首跑路径（state==null）先 `clearConnector`/替换为新 map 再绑定；未命名连接器 fail-fast 或要求唯一 name；补充"首跑不继承上一运行 offset"的测试。
- **信心水平**: 确定（机制）；很可能（影响场景）
- **发现来源视角**: 10x 规模运维者 / 模型攻击者

---

### [AR-04] [P2] BatchLoaderSourceFunction 伪造 replay：seek() 仅设字段、run() 不消费 offset——checkpoint 恢复后全量重发

- **文件**: `nop-stream/nop-stream-connector-batch/src/main/java/io/nop/stream/connector/batch/BatchLoaderSourceFunction.java:60-85,95-105`
- **证据片段**:
  ```java
  @Override public void run(SourceContext<S> ctx) throws Exception {
      ... while (running) { List<S> batch = loader.load(batchSize, chunkContext); ... }
  }   // 从不 consult currentOffset
  @Override public void seek(long offset) { this.currentOffset = offset; }
  ```
- **严重程度**: P2
- **现状**: 实现 `ReplayableSourceFunction`（契约："restore 后经 seek(long) 从正确位置重发"），`StreamSourceOperator` 恢复路径确实调 seek（:321-331），但 `run()` 永远从 loader 起点读；checkpointed offset 只是计数记录。测试仅断言 counter 被设置（`TestBatchLoaderSourceFunction.testSeekSetsOffset`），未验证位置恢复。
- **风险**: 恢复后重复发射全部批次（重复计算/重复写入），契约宣称的 no-duplicate 失效。
- **建议**: 在 `run()` 中跳过前 `currentOffset` 条，或去掉 `ReplayableSourceFunction` 改诚实声明 AT_LEAST_ONCE；补恢复位置测试。
- **信心水平**: 确定

---

### [AR-05] [P2] FileSourceReader 游标按 1 字节换行计账：CRLF 文件恢复时每行滞后 1 字节，恢复点产生幽灵空记录

- **文件**: `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileSourceReader.java:131-137,158-166`
- **证据片段**:
  ```java
  activeBytesConsumed += line.getBytes(StandardCharsets.UTF_8).length + 1; // +1 for newline
  // openSplit 恢复：fis.skip(split.getCurrentOffset())
  ```
- **严重程度**: P2
- **现状**: `readLine()` 剥离 `\r\n`/`\n`/`\r`，CRLF 实际 2 字节终止符只计 1 → 游标逐行滞后 1 字节；恢复时 skip 落在上一行 `\n` 位置，`readLine()` 返回 `""` → 每次恢复点发射一条幽灵空记录（崩溃循环叠加）。
- **风险**: 恢复后数据流掺入空记录；对 Windows 换行输入"从 checkpoint 恢复、无重复无污染"承诺失效。
- **建议**: 用 counting InputStream 追踪真实位置或显式计 `\r\n`；补 CRLF 恢复测试。
- **信心水平**: 确定（CRLF 输入时）

---

### [AR-06] [P2] DebeziumMessageSource.dispatchEvent 吞掉 collect() 失败——与 MessageSourceFunction 的 P1-9 修复同族反例

- **文件**: `nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/DebeziumMessageSource.java:155-163`；`nop-stream/nop-stream-connector-debezium/src/main/java/io/nop/stream/connector/debezium/DebeziumCdcSourceFunction.java:137`
- **证据片段**:
  ```java
  private void dispatchEvent(ChangeEvent event) {
      for (Consumer<ChangeEvent> consumer : subscriptions.keySet()) {
          try { consumer.accept(event); }
          catch (Exception e) { LOG.error("Error processing CDC event: {}", event, e); }
      }
  }
  ```
- **严重程度**: P2
- **现状**: `ctx::collect` 失败（算子错误、输出已关闭）被 catch+log，`DebeziumCdcSourceFunction.run()` 永不感知 → Debezium 引擎继续消费、offset 继续推进 → 事件静默丢弃且作业"健康"运行。与 `MessageSourceFunction.java:61-67` 的 P1-9 修复（同家族：collect 失败 → pendingError → 失败退出）语义完全相反——同族类别清扫未达。
- **风险**: CDC 链路下游失败时静默丢数，且 offset 前移使重跑也无法补救。
- **建议**: 镜像 P1-9：collect 消费者捕获首个错误 → 置 failed → 中止 run 并重抛；或 dispatchEvent 上抛。
- **信心水平**: 确定（机制）

---

### [AR-07] [P2] 同族失败模式残留：RapidTransactionPattern / AccountTakeoverPattern 仍"首元素即返回"（GeographicAnomalyPattern 已修复的同款缺陷）

- **文件**: `nop-stream/nop-stream-fraud-example/src/main/java/io/nop/stream/fraud/pattern/RapidTransactionPattern.java:95-97`；`.../pattern/AccountTakeoverPattern.java:94-96,116-118`
- **证据片段**:
  ```java
  for (TransactionEvent firstEvent : ctx.getEventsForPattern("first")) {
      return value.getUserId().equals(firstEvent.getUserId());   // 只看第一个候选
  }
  return false;
  // 对照已修复的 GeographicAnomalyPattern.java:90-97 —— continue 遍历全部候选
  ```
- **严重程度**: P2
- **现状**: 项目的修复（`TestGeographicAnomalyPatternFix`）证明 `getEventsForPattern` 可能返回多个候选且必须全部检查（mock 含错用户+对用户两个事件）；两个兄弟 pattern 仍是首元素即返回——若首候选属于他人，正确候选被跳过 → 漏报欺诈告警。
- **风险**: 多候选上下文出现时（quantifiers / followedByAny / 分支共享）静默漏报；与已修复 siblings 行为不一致。
- **建议**: 应用 GeographicAnomalyPattern 的遍历模式到两兄弟；补多候选测试。
- **信心水平**: 确定（缺陷模式存在）；影响时机 = 多候选场景出现（猜测）

---

### [AR-08] [P2] TestGeographicAnomalyPatternFix 测的是手抄副本而非生产 pattern——修复测试自身无法防回归

- **文件**: `nop-stream/nop-stream-fraud-example/src/test/java/io/nop/stream/fraud/pattern/TestGeographicAnomalyPatternFix.java:20-33`
- **证据片段**:
  ```java
  IterativeCondition<TransactionEvent> condition = new IterativeCondition<TransactionEvent>() {
      // 匿名复制品——GeographicAnomalyPattern 从未被 import 或实例化
  };
  ```
- **严重程度**: P2
- **现状**: 测试类的匿名 condition 是生产 `GeographicAnomalyPattern` 逻辑的手抄副本；生产 pattern 若回退到首元素版，测试仍全绿——测试给的是虚假信心。
- **风险**: 同族缺陷可无感回归。
- **建议**: 把 condition 提取为 pattern 内的静态方法（或暴露实例），让生产与测试引用同一对象。
- **信心水平**: 确定

---

### [AR-09] [P2] JdbcTwoPhaseCommitSink.commit() 幂等命中路径提前 return，不清理 pendingCommits（清理契约不对称）

- **文件**: `nop-stream/nop-stream-connector-jdbc/src/main/java/io/nop/stream/connector/jdbc/JdbcTwoPhaseCommitSink.java:219-225,265`
- **证据片段**:
  ```java
  if (ledgerExists(connection, checkpointId)) {
      ...; committed = true;
      return;                                  // ← 提前返回
  }
  ...writeDataRows...writeLedgerEntry...;
  connection.commit();
  committed = true;
  // :265 —— getPendingCommits().remove(checkpointId);  仅数据写入路径执行
  ```
- **严重程度**: P2
- **现状**: 基类 `finishCommit`/`restoreFromEpoch` 会在 commit() 返回后删条目，故当前运行时被掩盖；任何直接调 commit() 的路径（测试/子类/未来运行时）在幂等命中时泄漏 pending 条目（无界增长 + 重复 ledger 查询）。
- **风险**: 潜在无界增长；行为不对称难调试。
- **建议**: 幂等分支同样 remove 后 return；幂等测试补 pendingCommits 断言。
- **信心水平**: 确定（代码不对称）

---

### [AR-10] [P2] NopStreamErrors 再发现 7 个死错误码（backlog 之外）：CHECKPOINT_EXECUTOR 三兄弟 + CHAINING_OUTPUT 双 + SERIALIZATION + INIT_ERROR

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/exceptions/NopStreamErrors.java:53-54,70-77,88-89,114-118`
- **证据片段**:
  ```java
  ErrorCode ERR_STREAM_SERIALIZATION = define("nop.err.stream.serialization", ...);              // L53
  ErrorCode ERR_STREAM_INIT_ERROR = define("nop.err.stream.init-error", ...);                    // L70
  ErrorCode ERR_STREAM_CHECKPOINT_EXECUTOR_NOT_INITIALIZED = define(...);                        // L73
  ErrorCode ERR_STREAM_CHECKPOINT_EXECUTOR_ALREADY_STARTED = define(...);                        // L76
  ErrorCode ERR_STREAM_CHECKPOINT_EXECUTOR_SNAPSHOT_FAILED = define(...);                        // L88
  ErrorCode ERR_STREAM_CHAINING_OUTPUT_SNAPSHOT_FAILED = define(...);                            // L114
  ErrorCode ERR_STREAM_CHAINING_OUTPUT_RESTORE_FAILED = define(...);                             // L117
  ```
- **严重程度**: P2
- **现状**: 全仓 0 抛出点（grep 验证，仅 ai-dev 文档命中）；`CHECKPOINT_EXECUTOR_*` 三兄弟对应场景抛裸异常；`CHAINING_OUTPUT_*` 双码对应的 RL-7 快照/恢复失败路径抛裸异常（P1-09-02 族）。backlog 的 P2-09-02b 批量替换如盲取这些码，语义可能错配。
- **风险**: 错误码面漂移 + 批量替换误用。
- **建议**: 接入对应错误路径或删除；补"死错误码"反射测试防回归。
- **信心水平**: 确定

---

### [AR-11] [P2] DSL source/sink 的 maxParallelism / consistencyCapability 属性静默忽略，且官方示例 DSL 正在演示它们

- **文件**: `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/stream.xdef:112-113,170-171`；`nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:313-326,384-396`；`nop-stream/nop-stream-flow/src/test/resources/_vfs/nop/stream/test/test-smoke.stream.xml:8,13`
- **证据片段**:
  ```xml
  <source maxParallelism="!int=0" consistencyCapability="!enum:...=AT_LEAST_ONCE" ...>
  <!-- 示例 DSL： -->
  <source ... consistencyCapability="REPLAYABLE"/>
  <sink   ... consistencyCapability="IDEMPOTENT"/>
  ```
- **严重程度**: P2
- **现状**: `buildSource`/`buildSink` 只读 bean/source/name；`getMaxParallelism()`/`getConsistencyCapability()` 非 `_gen` main 代码 0 消费者（grep 验证）——P1-XDSL-5/6 家族之外的一对未登记静默忽略属性，且被参考示例当作有效契约使用。
- **风险**: 用户声明 REPLAYABLE/IDEMPOTENT 得到 AT_LEAST_ONCE 语义且无诊断。
- **建议**: 消费或非默认值 fail-fast（对齐 failFastOnUnsupportedRegistries）；修正示例 DSL。
- **信心水平**: 确定

---

### [AR-12] [P2] `<window>` 节点级 allowedLateness / triggerId 子元素静默忽略（P1-XDSL-6 家族第二声明点）

- **文件**: `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/stream.xdef:150-153`；`nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/AdvancedTransforms.java:109-127`
- **证据片段**:
  ```xml
  <window strategyRef="!string" xdef:name="StreamWindowModel" xdef:ref="StreamTransformModel">
      <allowedLateness>long</allowedLateness>
      <triggerId>string</triggerId>
  </window>
  ```
- **严重程度**: P2
- **现状**: `buildWindow` 只读 strategyRef + strategy 的 windowFnId；节点自身子元素解析进 `_StreamWindowModel`（getter :53/:91）但 0 消费、0 fail-fast——P1-XDSL-6 覆盖的是 strategy 级属性，这是同一家族的第二声明点。
- **风险**: 用户认为声明了 allowedLateness/自定义 trigger，实际默认行为；迟到事件被丢无诊断。
- **建议**: 映射到 `WindowedStream.allowedLateness()`/trigger 选择，或子元素非空即 fail-fast；并入 P1-XDSL-6 修复范围。
- **信心水平**: 很可能

---

### [AR-13] [P2] EvalFunctionCondition 以 null IEvalScope 执行 xpl `where`/`until` 体——与 flow 全部 xpl 包装器语义分叉，引用全局即 NPE

- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/model/builder/EvalFunctionCondition.java:28`
- **证据片段**:
  ```java
  public boolean filter(Object value, Context ctx) {
      return ConvertHelper.toTruthy(action.call2(null, value, ctx, null));   // scope == null
  }
  // 对照 XplMapFunction/XplFilterFunction 等全部包装器：body.call1(null, value, XplFunctionSupport.newCallScope())
  ```
- **严重程度**: P2
- **现状**: `ExecutableFunction.call2` 以 null scope 构建 EvalRuntime；xpl 体引用全局（`log`/`$import`/`$scope`）时经 `rt.getScope()` 解析 → NPE。CEP 测试全部用 scope 无关 lambda 设置 where，该路径零覆盖。
- **风险**: 真实 XDSL CEP 作业的 where/until 引用全局即匹配期 NPE；与 flow 语义不一致难排查。
- **建议**: 传 `XplFunctionSupport.newCallScope()`（把 helper 移到共享位置）；补真实 xpl 体引用全局的测试。
- **信心水平**: 很可能

---

### [AR-14] [P2] XplSourceFunction.cancel() 置的 running 标志无任何消费者——XDSL 源无法履行取消契约

- **文件**: `nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/functions/XplSourceFunction.java:28,37-50`
- **证据片段**:
  ```java
  private volatile boolean running = true;
  @Override public void run(SourceFunction.SourceContext<T> ctx) {
      body.call1(null, ctx, XplFunctionSupport.newCallScope());   // body 只拿到 ctx，拿不到 this/running
  }
  @Override public void cancel() { running = false; }   // isRunning() 0 运行时消费者
  ```
- **严重程度**: P2
- **现状**: `SourceFunction` 契约"run 应阻塞至 cancel"在 XDSL 体下无法实现（体签名 `xpl-fn:(ctx)=>void` 无取消句柄）；`cancel()` 是静默 no-op。
- **风险**: XDSL 循环源取消时挂起或需线程中断副作用；未来运行时接线将静默误导。
- **建议**: 向体传可取消 context（或 SourceContext 包装暴露 running）；至少文档化限制。
- **信心水平**: 确定（死标志）；很可能（影响）

---

### [AR-15] [P2] 声明指向 `<source>` 变换的边被静默丢弃——唯一没有上游校验的变换类型（anti-hollow 保证破洞）

- **文件**: `nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/StreamModelDslBuilder.java:269-271,292-306`
- **证据片段**:
  ```java
  if (t instanceof StreamSourceModel) {
      return buildSource(env, (StreamSourceModel) t);   // upstreamIds 完全忽略
  }
  // 其余全部类型：requireSingleInput(upstreamIds)（≠1 即抛）
  ```
- **严重程度**: P2
- **现状**: `buildTransforms` 校验了边两端存在（:219-228），因此 `from="src" to="src2"`（src2 是 source）通过拓扑校验后边被静默丢弃——声明拓扑与实际执行拓扑不一致，违反类自身 javadoc 的 "Anti-Hollow guarantees"。
- **风险**: 非法 DSL 拓扑静默降级执行；生产难诊断。
- **建议**: source 分支要求 `upstreamIds.isEmpty()`（镜像 requireSingleInput）；补 fail-fast 测试。
- **信心水平**: 确定

---

### [AR-16] [P2] SKIP_TO_FIRST/SKIP_TO_LAST 未配 afterMatchSkipTo 时违反 pattern.xdef 文档规则，且以无上下文的 Guard 错误失败

- **文件**: `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/pattern.xdef:8`；`nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/model/builder/CepPatternBuilder.java:224-230`；`nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/aftermatch/SkipToElementStrategy.java:31-32`
- **证据片段**:
  ```java
  case SKIP_TO_FIRST: strategy = AfterMatchSkipStrategy.skipToFirst(partModel.getAfterMatchSkipTo());
  // SkipToElementStrategy 构造：this.patternName = notNull(patternName, "patternName");
  ```
- **严重程度**: P2
- **现状**: xdef 文档明示"SKIP_TO_FIRST/SKIP_TO_LAST 需要设置 afterMatchSkipTo（对应子 pattern 名）"，但属性可选、builder 直传 null → 失败在 `Guard.notNull("patternName")` 裸 IAE，无任何 DSL 上下文。
- **风险**: 文档规则与构建期校验漂移；用户得到难懂的构建失败。
- **建议**: builder 内校验并用 `StreamRuntimeException(ERR_CEP_MALFORMED_PATTERN)` 报"缺少 afterMatchSkipTo"；补 builder 测试。
- **信心水平**: 确定（行为）；中（是否够格报——它确实 fail-fast，属文档契约 + 错误质量）

---

### [AR-17] [P2] RocksDB 增量恢复：try-with-resources 先关 RocksDB 再关 ColumnFamilyHandle（JNI 生命周期顺序反了）

- **文件**: `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/core/common/state/backend/rocksdb/incremental/RocksDBIncrementalRestore.java:160-183`
- **证据片段**:
  ```java
  try (RocksDB src = RocksDB.openReadOnly(...)) {
      ...复制 range...
  } finally {
      for (ColumnFamilyHandle h : handles) { if (h != null) h.close(); }   // 先于 DB？不——DB 已被 try 关闭
  }
  ```
- **严重程度**: P2
- **现状**: JLS 14.20.3：resource 关闭嵌套在 finally 之前——`src.close()` 先删除 C++ DB，随后 `h.close()` 触碰已释放的原生内存。rocksdbjni 约定要求 handle 先于 DB 关闭。对照 `RocksDBKeyedStateBackend.close()`（:868-891）顺序正确。每次增量恢复都执行此序列。
- **风险**: 原生 use-after-free，潜在 JVM 崩溃/静默原生损坏（非确定性，测试可能间歇通过）。
- **建议**: 在 try 块内先关全部 handle 再让 DB 关闭（或嵌套 try-with-resources，handles 先声明后关闭顺序即 handle→DB）。
- **信心水平**: 确定（顺序语义）；很可能（可观察影响）

---

### [AR-18] [P2] 增量快照不清 non-sst 目录 + checkpoint id 计数器重启归零：陈旧 WAL/MANIFEST 混入重建 DB

- **文件**: `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/core/common/state/backend/rocksdb/RocksDBKeyedStateBackend.java:125`；`.../incremental/RocksDBIncrementalSnapshotStrategy.java:66-76`；`.../incremental/RocksDBIncrementalRestore.java:102-114`
- **证据片段**:
  ```java
  private final transient AtomicLong incrementalSnapshotIdCounter = new AtomicLong(0);   // 每次启动归零
  // doSnapshot：deleteIfExists(nativeDir); ... Files.createDirectories(nonSstDir);   // 只清 native
  // restore：遍历 nonSstDir 全部文件 copy 进重建 DB
  ```
- **严重程度**: P2
- **现状**: 计数器重启归零 → `cp-{id}` 目录跨运行复用；`doSnapshot` 只删 `native/` 不删 `non-sst/`；restore 复制 non-sst 下**所有**文件。上一运行的陈旧 WAL（log number 新于新 MANIFEST 引用）被复制并重放 → 上一运行未 checkpoint 的写入混入恢复状态。
- **风险**: 增量 checkpoint（opt-in）开启时，每次重启后恢复状态可能含外来数据或打开失败。
- **建议**: doSnapshot 同样 `deleteIfExists(nonSstDir)`；或持久化计数器。
- **信心水平**: 很可能

---

### [AR-19] [P2] RocksDBSnapshotSerDe.snapshotMapState 用 `namespace + "|" + key` 字符串拼接分组：分隔符碰撞即跨 (key,namespace) 合并

- **文件**: `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/core/common/state/backend/rocksdb/RocksDBSnapshotSerDe.java:187-200`
- **证据片段**:
  ```java
  String groupKey = dk.namespace + "|" + dk.rawKey;
  ```
- **严重程度**: P2
- **现状**: `(ns="default", key="a|b")` 与 `(ns="default|a", key="b")` 产生同一 groupKey → 合并为一个快照条目，第二条的 map 条目静默恢复到第一条的键下。Memory 后端按类型化 TypedNamespaceAndKey 分组无此碰撞——跨后端分组逻辑分叉。
- **风险**: 键/命名空间含 `|`（URL、管道符）时恢复数据错位（当前窗口命名空间为 TimeWindow/GlobalWindow，不可达 → 潜伏）。
- **建议**: 长度前缀元组或 TypedNamespaceAndKey 式复合键分组。
- **信心水平**: 确定（机制）；猜测（当前可达性）

---

### [AR-20] [P2] triggerAccumulators / paneTracking 用 `key.toString()` 建键：POJO 键（无 toString 覆写）恢复后全部 miss，触发/窗格状态静默重置

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java:2050,993-994`
- **证据片段**:
  ```java
  String stateKey = "trigger_" + key + STATE_KEY_SEPARATOR + window + ...;
  ```
- **严重程度**: P2
- **现状**: 算子级（非后端）String 键 map；快照键是既有字符串，恢复后 live 键的 `toString()` 对 POJO（identity hash）每次不同 → `getSimpleAccumulator`/`computePaneInfo` 全部 miss → 触发计数从零、ON_TIME/LATE 分类重置。**该面不经后端键编码，任何后端修复都无法覆盖**——与 P2-INV-4（后端键 round-trip）是独立失败面。
- **风险**: POJO 键窗口作业恢复后触发语义错乱（CountTrigger 重新计数、early/late 误标）。
- **建议**: 用确定性键编码（JSON 或 keySerializer）替代 `String.valueOf`。
- **信心水平**: 很可能（机制）；猜测（POJO 键窗口作业使用面）

---

### [AR-21] [P2] 恢复清空顺序跨后端分叉：RocksDB 先清后过滤，Memory 先过滤后早退——同快照不同后端终态不同

- **文件**: `nop-stream/nop-stream-rocksdb/src/main/java/io/nop/stream/core/common/state/backend/rocksdb/RocksDBSnapshotSerDe.java:424-436`；`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/state/backend/memory/MemoryStateSerDe.java:122-130`
- **证据片段**:
  ```java
  // RocksDB：clearAllStates(backend); ... effectiveStatesMap = filterKeyedStates(...);   // 过滤结果为空也已清
  // Memory：filter 先行；statesMap.isEmpty() → return（不清）→ 旧状态保留
  ```
- **严重程度**: P2
- **现状**: KeyGroupRange 恢复 + 过滤结果为空时：RocksDB 后端被清空，Memory 后端保留既有条目——向非空后端恢复（retry/区域重启路径）两后端终态不同。
- **风险**: 陈旧状态保留（Memory）vs 全清（RocksDB）语义不一致，rescale/恢复测试难以覆盖。
- **建议**: 统一"过滤 → 清 → 写"顺序。
- **信心水平**: 确定（顺序）；影响条件性

---

## 总评

门禁体系（不变式 #1-#7）覆盖的结构性契约（参数传递、输出转发、同步集合、ID 单调、wiring 存在性）与 live 代码依然双向吻合；本轮的发现集中在门禁体系外的三个方向：

1. **修复残余缺口（AR-02）**：plan 0132-1 的 PT 生产接线解决了"完全零接线"，但 fire 路径仍挂在数据面推进点（mailbox 只在数据到达时 drain，而 `InputGate.read()` 空闲期忙等永不返回）。E2E 用有界源 + 源末 drain 恰好掩盖了空闲期。这是"修实例不修类别"的又一次实例——**PT timer 触发机制尚未真正 wall-clock 化**，建议作为不变式 #7 wiring 族的扩展候选（"空闲期定时器仍按墙钟触发"门禁）或至少补空闲期回归测试。
2. **恢复路径跨后端一致性（AR-01/AR-19/AR-21）**：Memory 与 RocksDB 两个后端在键物化（Long 键 100% 丢失 vs 正确）、分组逻辑、清空顺序上三处分叉。AR-01 是其中唯一 P0——默认配置 + 常见键类型 + 静默全丢，且正是 P2-INV-4 家族里"数值键 + 跨后端分叉"的新变体（旧的 POJO 变体已在 backlog，本轮的新变体影响面大得多）。
3. **connector 族的"同族未清扫"**（AR-03/04/06/07/08）：Debezium offset 注册表陈旧续跑、collect 失败吞异常（P1-9 修复的反面）、batch replay 伪造、fraud pattern 首元素缺陷——这些与 mission 一贯强调的"类别清扫"直接相关：每次修一个实例时，grep 全族兄弟的纪律在这几个新模块里没有完全贯彻。

## 优先修复建议（按序）

1. AR-01（P0）：Memory 后端恢复键按 keyType 重物化（与 RocksDB 对齐）+ Long 键 round-trip 测试。
2. AR-02（P1）：空闲期 mailbox drain（read 空转超时返回 或 driver 直接驱动）+ 空闲期 PT 触发测试。
3. AR-03（P1）：首跑清理 offset 注册表 + 未命名连接器 fail-fast。
4. AR-04~09（P2 connector 族）：batch seek 诚实化、CRLF 游标、collect 失败上抛（P1-9 同款）、pattern 族清扫 + 测试引用生产对象。
5. AR-10~16（P2 flow/错误码族）：死码接入或删除、maxParallelism/consistencyCapability 与 window 节点级属性 fail-fast、EvalFunctionCondition scope、source 上游校验。
6. AR-17~21（P2 rocksdb/restore 族）：handle 关闭顺序、non-sst 清理、分组键、toString 键、清空顺序。

## 本次审核盲区自评

1. **未重跑全量测试/门禁**：以 live 源码 + I5 基线为准；AR-01/AR-02 未做运行实证（机制已源码闭环）。
2. **CEP NFA/SharedBuffer 族本轮未深挖**（前 21 轮已多轮覆盖，且 C3-RL-8 已 backlog）；`NFAState` PriorityQueue JSON 物化（P2-INV-6）未实测。
3. **执行层 RPC/transport（JobCoordinator/RemoteResultPartition 心跳）仍浅扫**：跨 JVM 路径未逐行复核。
4. **性能量化缺失**：AR-18 的 WAL 混入概率、AR-02 空闲期状态增长速率未做压力实证。
5. **fraud-example 的其余 pattern 正确性**仅抽查了三个，未全量语义核验。

## 按严重程度分布

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0 | 1 | Memory 后端恢复键类型丢失（默认路径静默数据丢失，跨后端分叉） |
| P1 | 2 | PT 定时器空闲期不触发（修复残余）、Debezium offset 静态注册表陈旧续跑/`_default_` 碰撞 |
| P2 | 18 | connector 同族未清扫（AR-04/05/06/09）、fraud pattern 兄弟残留与假测试（AR-07/08）、flow/XDSL/错误码（AR-10~16）、rocksdb/restore 族（AR-17~21） |

（P2 统计口径：AR-04~21 共 18 条；合计 21 条发现。）

<AI_STEP_RESULT>issues</AI_STEP_RESULT>
