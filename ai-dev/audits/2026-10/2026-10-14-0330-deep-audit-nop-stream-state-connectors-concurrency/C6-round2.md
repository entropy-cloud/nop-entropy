# C6 资源生命周期与泄漏（深挖第 2 轮·续）— nop-stream-state-connectors

- **审计维度**: C6 第 2 轮续（释放路径中间异常专项 + 第 1 轮"配对健全面"抽查复核 + C6-001/002/003 失效面扩展 + native 句柄中途失败归属 + 临时文件其余形态 + 未点名注册点对称性）
- **文件状态说明（合并续写）**: 目标文件 `C6-round2.md` 本轮创建前不存在。先前一次中断的第 2 轮尝试已将其发现（C6-010、C6-011）追加写入 `C6-resource-lifecycle.md` 的「深挖第 2 轮追加」节——该内容与本文件合并视为同一第 2 轮，编号予以保留（C6-010 RocksDB gauge 注册无摘除；C6-011 Files.walk Stream 未关闭），本续篇编号自 **C6-012** 接续。前轮文件未做任何修改。
- **盲审纪律**: 本轮仅读取本维度文件（`C6-resource-lifecycle.md`，作为前序轮次必要输入）与 live code；未读取 `ai-dev/audits/` 下其他审计目录/其他维度文件、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`。只审计当前 live code；`_` 前缀生成文件不作为发现对象。
- **依据文档**: `docs-for-ai/03-modules/nop-stream.md`、`docs-for-ai/03-modules/nop-stream-connectors.md`（完整读取）；基线 `_tmp/cra-baseline-nop-stream-state-connectors.txt`（仅用于圈定）。
- **日期**: 2026-10-14；**复核状态**: 全部条目已完成二轮独立复核（C6-012/013/014 均保留 P3，见各条目复核状态行与 C6-resource-lifecycle.md 文末二轮复核节）

---

## 1. 本轮发现

### [nop-stream-state-connectors-C6-012] CepOperator 迟到丢弃 counter 注册进进程级 StreamMetricsRegistries 后无任何摘除——meter 按 (operator, subtask) 标签基数随作业生命周期只增不减，无位置实例按 identityHashCode 每实例一枚

- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:438-441`（注册点，open() 内）、`:379-387`（标签回退）、`:683-691`（close() 无 registry.remove）；注册表本体 `nop-stream/nop-stream-core/.../metrics/StreamMetricsRegistries.java`（进程级 static composite，模块外设施，本轮只审 cep 侧使用方式）
- **证据片段**:
  ```java
  // CepOperator.open() —— 每个算子实例 open 都执行：
  this.numLateRecordsDropped = StreamMetricsRegistries.registry()
          .counter(LATE_ELEMENTS_DROPPED_METRIC_NAME,
                  "operator", effectiveLateDropOperatorTag(),
                  "subtask", effectiveLateDropSubtaskTag());      // L438-441
  ...
  private String effectiveLateDropOperatorTag() {
      return lateDropMetricOperator != null
              ? lateDropMetricOperator
              : "cep@" + Integer.toHexString(System.identityHashCode(this));  // L382：无位置实例 = 每实例唯一 meter id
  }
  // close()（L683-691）仅 super.close() + 定时器取消 + nfa.close() —— 全文无 registry.remove/removeByTag
  ```
- **严重程度**: P3（注册表 register/remove 不对称的慢性增长面：一条 meter 的代价为百字节级；上界 = 进程生命周期内出现过的不同 (operator 标签, subtask) 组合数。非资源耗尽级、非指标静默失效级——故 P3 而非 C6-010 的 P2）
- **并发场景**: 多作业/多子任务任务线程向同一进程级 composite registry 注册同名 meter；与抓取线程（scrape）并发读。
- **触发时序/泄漏链**: 作业 A（jobId=j1）CEP 顶点 open → 注册 `numLateRecordsDropped{operator="j1.p.v", subtask="0"}` → 作业 A 终止，close() 不摘除 → 作业 B（jobId=j2）open → 新标签组合 → 第 2 枚 meter 进 registry → 长驻 TM 上每新增一个 (job, vertex, subtask) 组合永久累积一枚 counter，无任何删除路径（模块内 grep `\.remove(` 对 meter 为零命中；CepOperator 内的 remove 全部是 timer ledger 的 map remove，与 meter 无关）。次生观测面：同身份重启（同 jobId/vertex/subtask）时 Micrometer first-wins 返回**既有** counter——新代算子的丢弃计数累加到旧代计数上，跨代计数合并且无代际 reset（观测口径伪影，非数据损坏）。回退路径放大：未经 `copyForSubtask(TaskLocation)` 装配的位置无关实例（程序化 API/单测形态）按 `cep@<identityHashCode>` 每实例注册唯一 id，此类实例反复 open 时 meter 按实例数累积。
- **现状**: register 与 remove 完全不对称（F8 注册表族）；与 C6-009（MANIFEST_JVM_LOCKS，P3）、C6-010（rocksdb gauge，P2——因其 first-wins 空 tags 导致指标**静默失效**）同族。第 2 轮前篇的静态注册表复扫以「static 容器」为口径，meter 注册**调用点**未在其清单内；本条补齐 cep 模块的 meter 注册点。
- **风险**: 长驻 TM 承载多短命作业（jobId 每次提交唯一）时 registry 缓慢单调增长（Micrometer meter map 基数膨胀的经典形态）；同身份重启的跨代计数合并使 `numLateRecordsDropped` 失去代际可比性。量级均无害，属优化候选。
- **建议**: `CepOperator.close()` 内对持有的 `Counter` 按 `Meter.Id` 调 `registry.remove(id)`（同 C6-010 建议①的形状）；或接受现状并在 owner doc 指标表中声明「进程生命周期内按标签基数有界」。回退标签 `cep@identityHashCode` 建议改为稳定名（如 pipeline 名），避免位置无关实例的每实例 meter。
- **失效面枚举**（F3——8 模块全部 MeterRegistry 注册调用点逐一核对）: ① `CepOperator` late-drop counter（本条，未修）；② `RocksDBMetricsRecorder.register` 6 gauge（C6-010 已登记，未修）；③ 其余 7 模块 grep `registry.(gauge|counter|timer)` / `StreamMetricsRegistries` 零命中（connector 族/batch/jdbc/debezium/kafka/avro 均无直接 meter 注册，✓）；④ nop-stream-core 自身的 `MicrometerStreamTaskMetrics` 等注册点为模块外平台设施（第 2 轮前篇③已登记为相邻疑点，维持不立案）。
- **信心水平**: 确定（注册点唯一且无摘除为静态事实；first-wins 复用语义经 C6-010 对 micrometer-core 1.12.8 的字节码核实结论可直接引用）
- **误报排除**: 不是「有界声明存在的缓存」——owner doc 指标表未声明 meter 生命周期；不是 C6-010 的重复计数——010 锚定 rocksdb gauge 的空 tags 静默失效，本条锚定 cep counter 的标签基数增长与跨代合并，机制与修复点不同；也不属「平台基础设施自身不重审」——缺陷锚点在 cep 模块的注册/不摘除使用方式。
- **复核状态**: 保留 P3（二轮独立复核：open() 每 open 注册带 (operator,subtask) tags 的 counter、close() 无任何 registry.remove、无位置实例回退 `cep@identityHashCode` 均经 live code 复证；量级=百字节级 meter 按 (job,vertex,subtask) 基数与实例数缓增，P3 恰当。现状归属：同机制面 core-runtime 轮（2026-10-14-0130）C7-012（P2）/C5-008（P3）已登记 core 侧三算子（EquiJoin/OverWindow/WindowOperator）；本条为 R4 R5-CEP-03（P3，JVM 级同名 counter 串数）经 commit 2bb7c013a7（plan 368 Phase 7）修复打 tags 后的残留面——cep 锚点（注册无摘除+跨代合并+identityHashCode 回退）首次立案，live code 未修）

### [nop-stream-state-connectors-C6-013] CepOperator.close() 释放链中间异常无 suppressed 收集——super.close()（keyedStateBackend.close 可重抛 native 关闭错误）先于定时器取消与 nfa.close()，异常时 NFA 的 Rich 条件函数关闭被跳过；NFA.close() 自身同样首抛中断后续条件关闭

- **文件**: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java:683-691`（close 顺序）、`:206-214` 同族点见 `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/NFA.java:206-214`；上游可抛点 `nop-stream/nop-stream-core/.../operators/AbstractStreamOperator.java:133-144`、`nop-stream/nop-stream-rocksdb/.../RocksDBKeyedStateBackend.java:1060-1066`
- **证据片段**:
  ```java
  // CepOperator.close() —— L683-691：
  public void close() throws Exception {
      super.close();                    // L684：= AbstractStreamOperator.close() → keyedStateBackend.close()
      releaseCacheStatisticsTimer();    // L687：super.close() 抛出时不可达
      if (nfa != null) {
          nfa.close();                  // L689：同上不可达
      }
  }
  // AbstractStreamOperator.close()（core，L141-143）：if (keyedStateBackend != null) { keyedStateBackend.close(); }
  // RocksDBKeyedStateBackend.close()（L1060-1066）：收集全部 native 关闭错误后 throw first —— 确有重抛路径
  // NFA.close()（L206-214）：
  for (State<T> state : getStates()) {
      for (StateTransition<T> transition : state.getStateTransitions()) {
          IterativeCondition<T> condition = transition.getCondition();
          FunctionUtils.closeFunction(condition);   // 首个 condition.close() 抛出即中断，其余条件永不关闭
      }
  }
  ```
- **严重程度**: P3（释放路径中间异常洞：触发需 keyedStateBackend.close() 抛出（RocksDB native 关闭失败，罕见）；后果 = 用户 Rich 条件函数的 close 被跳过（资源属用户代码，通常无状态——泄漏链不可具体化为框架自有资源）；周期统计定时器「不死任务」假说经引擎停机顺序核实**被驳倒**（见触发时序 ③），不构成升级依据）
- **并发场景**: 任务线程 teardown（operatorChain.close → CepOperator.close）；无跨线程释放。
- **触发时序/泄漏链**: ① 任务停机 → `closeChainAndGate` → `CepOperator.close()` → `super.close()` → `RocksDBKeyedStateBackend.close()` 在关闭全部 native 句柄后重抛首个收集错误（L1060-1066）→ `releaseCacheStatisticsTimer()` 与 `nfa.close()` 均被跳过 → NFA 全部 StateTransition 上的 `RichIterativeCondition`（用户代码，open() 中可能已持有资源）未经 close。② `AbstractUdfStreamOperator.close()`（core，L137-139）同形状：super.close() 抛出时 `FunctionUtils.closeFunction(userFunction)` 同样被跳过（模块外，登记为同族相邻观察）。③ **定时器不死假说的核查记录**：若 `releaseCacheStatisticsTimer` 被跳过，`cacheStatsTimerFuture` 残留——但 `ProcessingTimeServiceDriver` 为 **per-task** 线程（`StreamTaskInvokable.invoke()` 外层 finally `stopProcessingTimeDriver()` 在链 close 之后的时点停止 driver；driver.shutdown 后新注册的 timer 永不触发，残留注册随 driver 对象一并 GC）——故任何存续注册的生命周期被 driver 终止所封闭，**不构成**跨任务泄漏或不死自续期任务。cancel/re-arm 竞态窗口（`cacheStatsTimerFuture` 非 volatile、`onCacheStatisticsTimer` 无 closed 守卫）同样被该顺序封闭，后果仅为死 driver map 中的一条注册。
- **现状**: 与仓内已确立的 robust-close 纪律（`RocksDBKeyedStateBackend.close` 收集-suppressed、`BatchConsumerSinkFunction.close` flush 失败优先 + suppressed、`StreamTaskInvokable.closeChainAndGate` 的 `CloseSupport.accumulate`）相比，cep 侧 close 链是同仓双标准中的朴素形态：无 per-step 收集、顺序上把「最可能抛出的 native 关闭」放在最前。
- **风险**: 用户条件函数持有资源（如打开的外部连接）时不释放；框架自有资源无实际泄漏（native 已被 backend.close 内部全部关闭、定时器随 driver 消亡）。低概率 + 低后果 = P3。
- **建议**: ① `CepOperator.close()` 调整顺序：`releaseCacheStatisticsTimer()` 与 `nfa.close()` 提前到 `super.close()` 之前（定时器取消最廉价且最关键，先做），`super.close()` 的异常最后收集/重抛；② `NFA.close()` 引入 per-condition try/catch + suppressed 收集（与 `RocksDBKeyedStateBackend.close` 同款形态）。二者均为健壮性整改，非紧急。
- **失效面枚举**（F3——cep 模块全部 close/release 链逐一核对）: ① `CepOperator.close()`（本条）；② `NFA.close()`（本条第二点）；③ `NFA.EventWrapper.close()`（round-1 配对表 #23，TWR 承载且仅单资源 releaseEvent，✓）；④ `SharedBufferAccessor`（round-1 #23 lockNode/releaseNode 配对，✓）；⑤ `releaseCacheStatisticsTimer` 本体（null 幂等，✓）；⑥ 8 模块其余 close 链（本轮 §2 逐项核查，✓）。
- **信心水平**: 确定（顺序与首抛中断均为静态事实）；后果定级依赖「用户条件函数持有资源」的假设——框架自有资源经核查无实际泄漏，故自降 P3 而非 P2。
- **误报排除**: 不是「close 抛错已响亮」的可接受设计辩护对象——本条不主张泄漏后果严重，而是记录释放链健壮性与仓内既有纪律的不一致；「不死定时器」是本轮主动检验并排除的假说，非夸大申报。
- **复核状态**: 保留 P3（二轮独立复核：CepOperator.close 先 super.close()（keyedStateBackend.close 收集-suppressed 后重抛首错，RocksDBKeyedStateBackend:1060-1066）后 releaseCacheStatisticsTimer/nfa.close 的顺序、NFA.close 首抛中断后续条件关闭（NFA:206-214 无 per-condition try/catch）均经 live code 复证；「定时器不死」假说的驳论亦复核成立——ProcessingTimeServiceDriver 为 per-task 线程，StreamTaskInvokable:714 创建/:723,819 停止，存续注册生命周期被 driver 终止封闭。现状归属：新发现——R4 曾登记共享条件实例并发 close 的另一缺陷面（01-cep-correctness:215），本锚点首次立案，live code 未修）

### [nop-stream-state-connectors-C6-014] FileTwoPhaseCommitSink.deleteIfExistsQuiet 空 catch 静默吞 IOException——abort 路径的 epoch temp 删除失败零观测，C6-007 孤儿残留面在「abort 已执行但删除失败」分支上完全不可见

- **文件**: `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileTwoPhaseCommitSink.java:683-689`（deleteIfExistsQuiet）、`:474-481`（abort 调用点）
- **证据片段**:
  ```java
  public void abort(long epochId) throws Exception {
      Object raw = getPendingCommits().remove(epochId);
      if (raw instanceof FilePendingCommit) {
          Path tempPath = Paths.get(((FilePendingCommit) raw).getTempPath());
          deleteIfExistsQuiet(tempPath);        // L479：删除失败 → 静默
      }
  }
  ...
  private void deleteIfExistsQuiet(Path path) {
      try {
          Files.deleteIfExists(path);
      } catch (IOException e) {
          // best-effort cleanup                 // L688：空 catch，无任何日志
      }
  }
  ```
- **严重程度**: P3（F9 静默吞异常：泄漏本体与 C6-007 同池（孤儿 `.epoch-N.tmp`），本条独立缺陷是**该分支的零可观测性**；触发需删除时 IO 错误（权限/磁盘故障），罕见）
- **并发场景**: 任务线程 abort（checkpoint 协调器驱动）与文件系统状态；无跨线程释放问题。
- **触发时序/泄漏链**: checkpoint 未 durable → 协调器调用 `abort(epochId)` → pendingCommits 条目已 remove（L476）→ `deleteIfExistsQuiet` 因 IO 错误（目录权限被收紧/只读挂载/文件被外部占用）删除失败 → 异常被空 catch 吞掉，**无 WARN** → 孤儿 temp 留存且运维侧无任何信号。对比同仓清理路径的既有纪律：`RocksDBIncrementalRestore.deleteRecursively`（WARN）、`RocksDBIncrementalSnapshotStrategy.pruneOldCheckpoints`（WARN）、`BatchLoaderSourceReader.close`（WARN）——本点是同仓双标准中唯一零日志的清理吞异常。
- **现状**: 与 C6-007（结构性孤儿窗口：登记后即死、无清理路径）互补：007 覆盖「abort 根本不发生」的窗口，本条覆盖「abort 发生但删除失败」的分支——后者在 007 的恢复入口清扫建议（方案①：恢复时扫描非白名单 temp）落地后会被顺带治愈，但在此之前该分支完全静默。
- **风险**: 与 C6-007 同池的磁盘残留 + 排障时无日志可循（孤儿来源无法区分「从未 abort」与「abort 删除失败」）。独立风险低。
- **建议**: 最小修复 = 空 catch 改 `LOG.warn("Failed to delete aborted epoch temp file {}", path, e)`（对齐仓内清理路径纪律）；根治随 C6-007 建议①的恢复入口清扫一并覆盖。
- **失效面枚举**（F3——file sink 全部静默清理点逐一核对）: ① `deleteIfExistsQuiet`（本条，未修）；② `forceDirectoryQuietly`（L615-621：catch Exception + LOG.debug——有日志但 debug 级，目录 fsync 为平台无关的 best-effort 增强而非资源清理，✓ 可接受）；③ `withManifestLock` 各级 finally（本轮核实全部有守卫，✓）；④ JDBC sink 的 rollback/setAutoCommit/close 失败分支（全部 LOG.error/LOG.warn，✓）；⑤ 其余 7 模块 grep 空 catch 清理点——`DebeziumCdcSourceFunction` finally 两处 catch 均 LOG.warn（✓）、`BatchLoaderSourceReader.close` LOG.warn（✓），无第二个零日志清理点。
- **信心水平**: 确定（空 catch 为静态事实；泄漏量与频率取决于 IO 错误出现率，本体后果已被 C6-007 承载）
- **误报排除**: 不是 C6-007 的重复登记——007 立案的是「无清理路径」的结构窗口，本条立案的是「有清理路径但失败静默」的 F9 可观测性缺陷，修复点不同（007=新增清扫，本条=补日志）；不属「测试专用简化」；空 catch 无注释化辩解（仅有 "best-effort cleanup" 一词，未论证零观测的取舍）。
- **复核状态**: 保留 P3（二轮独立复核：deleteIfExistsQuiet（FileTwoPhaseCommitSink:683-689）注释-only 空 catch、abort:479 唯一调用点经复证；与同仓 RocksDBIncrementalRestore.deleteRecursively/pruneOldCheckpoints/BatchLoaderSourceReader.close 的 WARN 纪律双标准成立。现状归属：新发现——R6 CON-04 仅提及该清理点存在、未立案其静默性；R6 卫生扫描「空 catch=0」结论与本点相左（注释-only catch 未被计入扫描口径），live code 未修）

---

## 2. 排除项/定向核查（本轮全部核查记录——含第 1 轮"配对健全面"抽查复核结论）

**A. 第 1 轮配对健全面抽查复核（抽 9 面，逐面重读 live code，全部成立）**:

| # | 抽查面 | 复核点与结论 |
|---|---|---|
| 1 | JDBC Connection finally 链（`JdbcTwoPhaseCommitSink.commit:717-735`） | rollback→setAutoCommit→close 三步**各自独立 try/catch**（LOG.error/WARN），任一步抛出不阻断后续——释放路径中间异常核查通过；guard 命中分支（L688-696）同样走 finally 关闭 ✓ |
| 2 | JDBC PreparedStatement 双关（`writeKeyedOps:997-1012`） | upsertPs/deletePs 各自独立 catch，首关失败不阻断次关 ✓ |
| 3 | Debezium run finally 三连释放（`DebeziumCdcSourceFunction:297-318`） | `unregisterFailureListener(null)` 经 wrapper L67-71 核实 null 安全（`if (connectorName != null)`）——`resolveConnectorName()` 失败（name 缺失）时 finally 以 null 名 no-op，原始 typed `ERR_STREAM_CONFIG_ERROR` 不被 finally 异常遮蔽；subscription.cancel 与 source.stop 各自 catch WARN ✓；run 前置 subscribe 失败补 stop（L278-281）✓ |
| 4 | RocksDBKeyedStateBackend.close suppressed 收集（`:979-1067`） | 8 类 native 句柄逐项独立 try/catch + errors 收集 + addSuppressed 重抛；关闭顺序 metrics→CF handles→defaultCF→db→WBM→cache→filter→options ✓。唯一未包裹点 `metricsRecorder.close()`（L988-990）本体仅 `dbRef.set(null)`（AtomicFieldUpdater，无可抛路径），观察项不立案 |
| 5 | FileTwoPhaseCommitSink.withManifestLock（`:389-404`） | jvmLock.lock 后所有分支经 finally unlock；channel TWR；fileLock.release 在 action 的 finally——action 抛出→release→TWR 关 channel→unlock，四级均闭合；`channel.lock()` 抛出时 fileLock 未获、TWR 关 channel + unlock ✓ |
| 6 | FileSourceReader.openSplit 三错误分支（`AR-15-①`） | channel.size / cursor>size / channel.position 三分支均显式 `fis.close()` 后重抛 ✓ |
| 7 | Kafka 探测与运行面 | `KafkaSourceFunction.run:246` / `checkConnection:377` TWR ✓；`KafkaTwoPhaseCommitSink.checkConnection:307` probe producer TWR（C6-001 失效面扩展：**probe 面确认干净，非第二个泄漏点**）✓ |
| 8 | BatchConsumerSinkFunction.close（`:141-173`） | flush 失败捕获并保持主错误、consumer close 在 finally、close 失败 addSuppressed 到 flush 错误（数据丢失优先上报）——释放路径中间异常核查通过 ✓ |
| 9 | RocksIterator 全部点 | 8 处状态/序列化点（AbstractRocksDBState:73、RocksDBListState:62、RocksDBMapState:61/220/269、RocksDBSnapshotSerDe:209/311/448）+ backend 2 处（387/748）= 10 处全部 TWR，与第 1 轮「×10」一致 ✓ |

**B. C6-001/002/003 失效面扩展核查**:

- **AdminClient / 其他 Kafka 客户端**: 8 模块 grep `AdminClient|KafkaAdminClient` 零命中——kafka 客户端面仅 KafkaProducer（长驻=C6-001 已立案；probe=TWR ✓）与 KafkaConsumer（TWR ✓）两类，C6-001 失效面无第三实例。
- **C6-002/003 同族 runner 线程**: 全 8 模块 `new Thread|Executors.|new ScheduledThreadPool` grep 复核——自建线程仅 `MessageSourceReader.launchEngine` 与 `DebeziumCdcSourceReader.launchEngine` 两处（C6-002/003 已尽）；`CepOperator` 的 cache-stats 定时器与 `internalTimerService` 寄宿于 **per-task** `ProcessingTimeServiceDriver` 线程（引擎自建但随任务生命周期终止，见 C6-013 触发时序③），非同族长驻 runner。
- **cep TimerServiceImpl**（`CepOperator:1382-1388`）: 仅 `currentProcessingTime()` 委托，无注册/资源，✓。

**C. native 句柄关闭顺序其余路径**:

- **快照执行中途失败**（`RocksDBIncrementalSnapshotStrategy.doSnapshot`）: Checkpoint TWR ✓；hash/copy 循环中途抛出时部分写入的 `cp-N` 目录残留——但 `pruneOldCheckpoints` 在下一次**成功**快照时按 id 排序滚动清理（F-02 retention=2），失败循环内每次仅新增一个残留、首个成功快照后 2 代内收敛自愈；与第 1 轮配对表 #7「删除失败 WARN + 下轮重试」同池，不另立案。
- **restore 中途失败句柄归属**（`RocksDBIncrementalRestore.restoreRangeInto`）: `copyColumnFamilyRange` 对第 N 个 CF 抛出时，TWR 关 src + finally 关 handles 全表（含已用过的前 N-1 个）——中途失败不漏句柄 ✓；唯一破口仍是 `openReadOnlyTyped` open 失败时 handles 半成品无人关（第 1 轮 C6-006 失效面③已登记为 C6-005 同族兄弟，维持）。
- **copyColumnFamilyRange 的 dstCf**（`RocksDBKeyedStateBackend:381-416`）: `getOrCreateColumnFamily` 产物登记进 backend 的 cfHandles 注册表，由 `backend.close()` 统一关闭，restore 失败路径不产生孤儿句柄 ✓；iterator TWR ✓。

**D. 临时文件/目录其余形态**:

- checkpoint metadata 面（manifest/body/SerDe）宿主于 `nop-stream-runtime` 的 CheckpointStorage 族（模块外，不立案）；本审计单元内 temp 形态全清单：file sink `.epoch-N.tmp`（C6-007）、manifest `.tmp`（C6-007 失效面②，本轮核实 `updateManifestAtomically` move 失败残留路径真实存在）、rocksdb `cp-N`（retention ✓ + 本轮 §C 自愈结论）、restore reconstructed 目录（finally deleteRecursively ✓）。无第五形态。
- **判定为非缺陷**（防第 3 轮重复）: ① `MessageSourceFunction.run` finally（`:196-205`）中 `localSubscription.cancel()` 为最后资源、未包裹——若 cancel 抛出会替换 pendingError 重抛，但无资源后续可漏且两态均响亮，不立案；② `BatchLoaderSourceFunction.run` finally（`:144-148`）loader close 为最后资源，同上 ✓；③ `BatchLoaderSourceReader.adoptSplit` 不会双 adopt 孤儿化旧 loader（pendingSplit 置 null + restore 前置守卫 postStartRestoreError，单次采纳不变式 ✓）；④ `MessageSinkFunction`（纯 per-record send）/ `MessageTopicSplitEnumerator.close`（"no external resources"）/ `FileSplitEnumerator.close`（无持有资源）✓；⑤ `BatchLoaderSource.checkConnection`（L171-172 closeable 即关）与 `BatchLoaderSourceFunction.checkConnection`（L163-174 setup→null 检查→close）✓；⑥ avro 模块无 OS 流（in-memory byte[] + BinaryEncoder/Decoder），HttpClient 两面已由 C6-008/P3 附注承载 ✓；⑦ 8 模块无自建线程池/调度器（前篇第 2 轮结论复核成立）。

**E. 未点名 listener/handler 注册对称性**:

- cep/connector/batch/jdbc/debezium/kafka/avro/rocksdb 全模块 grep `addListener|removeListener|register|unregister` 复核：第 1/2 轮已点名面（Debezium failure-listener ✓、MANIFEST_JVM_LOCKS=C6-009、StreamMetricsRegistries=C6-010/本条 C6-012）之外，**无第 4 处注册点**。`NFAStateNameHandler`（nfa/compiler）为纯字符串处理工具，非注册表。

---

## 3. 终止声明

**累计发现清单（第 1 轮 + 第 2 轮前篇 + 本续篇）**:

| ID | 严重度 | 标题 | 轮次 |
|---|---|---|---|
| C6-001 | **P0** | kafka-2pc sink KafkaProducer 全终止路径永不关闭 | 第 1 轮 |
| C6-002 | P1 | MessageSourceReader.close 无法终止阻塞 put 的 runner | 第 1 轮 |
| C6-003 | P1 | DebeziumCdcSourceReader 同族 | 第 1 轮 |
| C6-004 | P1 | RocksDBMetricsRecorder gauge 抓取与 close 竞态（SIGSEGV 窗口） | 第 1 轮 |
| C6-005 | P2 | openDB 失败路径 native 泄漏 | 第 1 轮 |
| C6-006 | P2 | restore 先关 src DB 后关 CFHandle 顺序颠倒 | 第 1 轮 |
| C6-007 | P2 | file sink 孤儿 epoch temp | 第 1 轮 |
| C6-008 | P2 | Avro registry HTTP 无响应超时 | 第 1 轮 |
| C6-009 | P3 | MANIFEST_JVM_LOCKS 只增不删 | 第 1 轮 |
| C6-010 | P2 | RocksDB gauge 注册无摘除 + first-wins 静默失效 | 第 2 轮前篇（C6-resource-lifecycle.md 追加节） |
| C6-011 | P2 | Files.walk Stream 未关闭（异常路径 fd 泄漏，2 处） | 第 2 轮前篇（C6-resource-lifecycle.md 追加节） |
| C6-012 | P3 | CepOperator counter 注册无摘除（标签基数增长 + 跨代合并） | 本续篇 |
| C6-013 | P3 | CepOperator.close/NFA.close 释放链中间异常无 suppressed 收集 | 本续篇 |
| C6-014 | P3 | deleteIfExistsQuiet 空 catch 静默吞 IOException | 本续篇 |

**累计**: P0×1 / P1×3 / P2×6 / P3×4（14 条）。

**终止判定**: **未发现新的高价值问题。C6 维度深挖终止。**

依据：① 本轮按追加指令五个方向全部走完——释放路径中间异常专项（A 表 9 面 + C6-013/014 两处新洞，均 P3）、第 1 轮配对健全面抽查复核（9 面全部成立，无翻案）、C6-001/002/003 失效面扩展（AdminClient 零命中、probe 面干净、runner 线程族无第 3 处）、native 句柄中途失败归属（快照自愈 + restore 中途不漏，仅存 C6-005/006 已立案面）、临时文件其余形态（无第五形态）与未点名注册点（无第 4 处）；② 本续篇新增 3 条均为 P3 优化候选级，无一改变前轮 P0/P1/P2 结论或引入新泄漏链；③ 8 模块资源获取点盘点已闭合（连接/流/池/文件锁/信号量/Buffer/临时文件/listener/native 句柄九类均有明确归属），继续第 3 轮的边际收益为负。前篇第 2 轮终止判定在本续篇的补充核查（释放路径专项 + 配对面抽查）之后**维持并加固**。

---

## 4. 覆盖声明

- **本轮深读文件**（逐行/逐方法核对）: `DebeziumCdcSourceFunction.java`（全文）、`JdbcTwoPhaseCommitSink.java`（commit/initializeLedgerTable/writeInserts/writeKeyedOps/normalizeBatch 全释放链）、`KafkaTwoPhaseCommitSink.java`（全文）、`KafkaSourceFunction.java`（全文）、`RocksDBKeyedStateBackend.java`（构造器/openDB/close/copyColumnFamilyRange）、`RocksDBIncrementalRestore.java`（全文）、`RocksDBIncrementalSnapshotStrategy.java`（全文）、`RocksDBMetricsRecorder.java`（全文）、`FileTwoPhaseCommitSink.java`（全文）、`MessageSourceFunction.java`（全文）、`BatchConsumerSinkFunction.java`（全文）、`BatchLoaderSourceFunction.java`（run/checkConnection）、`BatchLoaderSourceReader.java`（adoptSplit/pollNext/restoreState/close）、`CepOperator.java`（open/close/定时器注册与释放/counter 注册/TimerServiceImpl）、`AbstractStreamOperator.java`/`AbstractUdfStreamOperator.java`/`FunctionUtils.java`/`StreamTaskInvokable.java`/`ProcessingTimeServiceDriver.java`/`TaskProcessingTimeService.java`（模块外但作为 C6-013/定时器假说核查的框架事实基础）、`DebeziumEngineWrapper.java`/`NopStreamOffsetBackingStore` 定位（模块外，仅核实 unregister null 语义）。
- **grep 补扫面**: AdminClient/Kafka 客户端 close 点；meter 注册调用点（8 模块）；`registry.remove`/removeByTag；空 catch 清理点；`new Thread|Executors.`（runner 族复核）；`AutoCloseable|close()`（cep/batch/avro 全量）；`Files.(list|walk|find|newDirectoryStream)`（复核前篇 C6-011 面）；`newIterator`（RocksIterator 10 点）；`addListener|register|unregister`（注册对称性第 4 处排查）。
- **未覆盖声明**: `nop-stream-runtime`/`nop-stream-core`（非本审计单元，仅按需读取框架钩子作为判定基础，未对其立案）；`src/test`（测试专用模型豁免）；`_` 前缀生成文件（纪律排除）；Debezium 引擎与 rocksdbjni 内部实现（平台/三方设施，只审使用方式）。
- **前序轮次输入**: 仅 `C6-resource-lifecycle.md`（第 1 轮 + 第 2 轮前篇）；未读取该目录下 C1-C5/C7/C8 及 dispatch-log 以外任何文件（dispatch-log 未读取）；未读取 `ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`。
