# 维度 03：连接器与安全

审计对象：nop-stream 连接器族（connector / connector-batch / connector-jdbc / connector-debezium）+ nop-stream-core 的 credentials 包与连接器注册表 + 直接承接连接器契约的上游类（`NopStreamOffsetBackingStore` / `DebeziumEngineConfig` / `UpsertStatements` / `DialectImpl.escapeSQLName`）。审计基线为当前 HEAD（已含 R5 修复批次），全部结论来自本次对 live code 的独立阅读。按盲审纪律，未读取 ai-dev/audits/、ai-dev/plans/、ai-dev/bugs/、ai-dev/lessons/、ai-dev/logs/ 下任何文件。

## 审计范围与方法

- **exactly-once / 2PC**：通读 `JdbcTwoPhaseCommitSink`（1118 行全文）、`FileTwoPhaseCommitSink`（660 行全文）、基类 `TwoPhaseCommitSinkFunction`，并核对调用面（`StreamSinkOperator.processBarrier/restoreState`、`OperatorChain.deepCopy(int|TaskLocation)`、runtime 侧 `deepCopy` 全部 4 个调用点均为 TaskLocation 版本）。逐一推演故障场景：checkpoint 未完成即崩溃、commit 阶段崩溃、恢复后重复提交、并行 subtask 同 epoch、guard 命中路径。
- **SQL 注入面**：核对所有外部标识符进入 SQL 的入口（`tableName`/`columnNames`/`keyColumns`/CDC 行 keySet 派生列名/ledger 表名/checkpoint 存储常量表名），下钻 `DialectImpl.escapeSQLName`（`isValidSimpleVarName` 放行 + `quoteDupEscapeString` 双写引号转义）与 `UpsertStatements` 的转义覆盖，确认放行分支只可能是 `[A-Za-z0-9_]` 字符集。
- **文件 sink 原子性**：tmp + fsync + `ATOMIC_MOVE` + manifest 原子替换 + 目录 fsync 的顺序；并行 subtask 后缀命名；恢复路径（`setPendingCommits` JSON 退化回水合、durable/非 durable 分支、abort 删 tmp）；fresh run 的 epoch 计数起源（`CheckpointIDCounter` 默认 0，4 处执行器均 `new CheckpointIDCounter()`）。
- **凭证 / 安全横切面**：`StreamCredentialSupport`（fail-closed 语义、引用串持久化）、`DebeziumCdcSourceFunction` 的 D4 解密路径、全 nop-stream main 代码的日志/异常/快照泄漏面扫描（无凭证明文落日志点）；`DebeziumEngineConfig` 的 connectorType 白名单与 `offset.storage`/`record.processing.threads` 覆盖守卫。
- **工厂/注册表**：`StreamConnectorRegistry`（重复注册、方向错配、descriptor/factory 一致性 fail-fast）、`StreamConnectorCatalog`（探测不变式：2PC ⟹ PARALLEL、consistency 单一事实源对齐）、5 个 beans.xml 与 9 个工厂的注册一一核对，docs-for-ai 能力矩阵行与工厂代码逐项比对。
- **深挖第 2 轮**：基于初审命中模式做同类扫描（守卫命名空间缺失、批内列集校验粒度、PARALLEL 声明 vs 共享可变状态、tmp 残留、静态 registry 键碰撞、SQL 标识符转义纪律一致性）。

明确核实为**无问题**的高风险面（供后续审计去重）：
- JDBC 2PC 的数据+台账同事务原子性、恢复后重提交幂等（ledger PK 使 split-brain 双提交的败者整体回滚）、`finishCommit` 亚组合提交、`restoreFromEpoch` durable/非 durable 分支、guard 命中后 pendingCommits 清理（R5 已修）。
- 文件 sink 单运行代际内的 exactly-once（saveState 先 fsync 再入 checkpoint、rename 后 manifest 修复、`withManifestLock` 双层锁、per-subtask tmp 名）。
- 凭证链路 fail-closed（provider 缺失/凭证不存在均类型化报错），引用串（非明文）进入序列化/checkpoint 路径。
- Debezium offset：快照在 task 线程内与 barrier 同步、`setOffsets` REPLACE 语义（R5-CON-03 已修）、engine 终态死亡经 failure listener 上抛（R5-CON-03 已修）、collect 异常捕获重抛（R5-CON-02 已修）。
- 注册表构建期/探测期双重校验（重复类型名、别名冲突、方向错配、2PC⟹PARALLEL）。

---

## 第 1 轮（初审）

### [CON-01] 文件 2PC sink 的 manifest 守卫无运行代际命名空间：fresh run 复用输出目录时静默丢弃全部新数据
- **文件**: `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileTwoPhaseCommitSink.java:284-315`（`doCommitLocked` 守卫命中路径）、`588-599`（`manifestKey`/`subtaskSuffix` 无 job 维度）
- **证据片段**:
  ```java
  static String manifestKey(int subtaskIndex, long epochId) {
      return epochId + subtaskSuffix(subtaskIndex);   // 无 job/run 命名空间
  }
  ...
  if (manifest.containsKey(manifestEntryKey)) {
      LOG.warn("File 2PC commit skipped: manifest already records epoch {} at key {} (recover-safe re-commit or run collision)",
              checkpointId, manifestEntryKey);
      getPendingCommits().remove(checkpointId);
      return;                                          // 本 epoch 数据（temp 文件）被丢弃且 temp 不清理
  }
  ```
- **严重程度**: P1
- **现状**: manifest 键仅为 `(epochId, subtaskIndex)`。fresh run（非恢复启动）的 checkpoint id 从 `new CheckpointIDCounter()`（初始 0）重新计数（`GraphModelCheckpointExecutor.java:228/357`、`EmbeddedDistributedExecutor.java:254`、`RpcDistributedExecutor.java:213`、`OpsJobManager.java:138`），任何复用旧输出目录的新运行，其全部 epoch 键与旧 manifest 碰撞，commit 走守卫命中分支：只打 WARN，然后丢弃本次已写入 temp 的新数据。
- **风险**: "重置作业状态重跑/补数但保留 sink 目录"这一常见运维动作会导致整个新运行数据静默丢失（仅日志可见）；`final-exists → repair manifest` 分支（304-315 行）同理会把外来文件认领为本运行已提交数据。JDBC sink 的同构缺陷（共享台账上的静默跳写）已在 R5 以 `sink_namespace` 结构性修复，且其 fallback WARN 措辞（`JdbcTwoPhaseCommitSink.java:416-422`）明确将"两个作业共享键空间导致静默跳写"裁定为必须显式消解的缺陷；文件 sink 连 namespace 配置入口都没有，属核心契约（exactly-once = 不丢不重）在新运行场景下的单侧破坏。
- **建议**: 为 manifest 引入运行/作业命名空间——最低成本方案是 `copyForSubtask(TaskLocation)` 把 `jobId|vertexId` 传入 sink 拷贝（镜像 JDBC sink 的做法），manifest 键变为 `jobId|vertexId + "#" + epochId + suffix`，并在 job 首次 commit 时对同 namespace 旧 epoch 提供清理或显式冲突报错；至少应在守卫命中分支增加"本运行从未写过该键"的可判别标记（如 manifest 记录 runId）并把命中分级为可配置的 fail-fast。
- **信心水平**: 确定（行为与触发条件均经代码核实；严重度判级基于与 R5-CON-01 修复裁定的同构性）
- **误报排除**: 同类误报是把"恢复重提交时守卫命中"当成缺陷——本条不涉及恢复场景（恢复时数据已持久化，命中跳过是正确的幂等行为）。本条命中的是 fresh run：数据只存在于 temp 文件、从未提交过，守卫却把它当作"已提交"丢弃。WARN 文案中的 "or run collision" 说明作者知晓该场景存在但未消解，且现有测试只覆盖单运行代际（`TestFileTwoPhaseCommitSink` 无跨运行用例），不是已裁定的可接受行为。
- **复核状态**: 未复核

### [CON-02] JDBC 2PC 键控模式批内列集漂移无守卫：同批后续行缺失列静默写 NULL、多余列静默丢弃
- **文件**: `nop-stream/nop-stream-connector-jdbc/src/main/java/io/nop/stream/connector/jdbc/JdbcTwoPhaseCommitSink.java:920-1020`（`writeKeyedOps`/`resolveKeyedColumns`）、`1030-1042`（`bindRow`）
- **证据片段**:
  ```java
  private List<String> resolveKeyedColumns(LinkedHashMap<String, DmlOp> folded) {
      List<String> first = null;
      for (DmlOp op : folded.values()) {
          if (op.kind == DmlOp.KIND_UPSERT) { first = new ArrayList<>(op.row.keySet()); break; }
      }
      ...
      synchronized (this) {
          if (keyedColumns == null) { keyedColumns = first; ... }
          else if (!keyedColumns.equals(first)) { throw ... /* 仅比对每批第一行 */ }
      }
      return keyedColumns;
  }
  ...
  private void bindRow(PreparedStatement ps, List<String> columns, Map<String, Object> row) {
      for (String col : columns) {
          Object value = row.get(col);
          if (value == null) { ps.setNull(index, Types.NULL); } else { ps.setObject(index, value); }
      }
  }
  ```
- **严重程度**: P2
- **现状**: "列集漂移类型化拒绝（no silent schema drift）" 的实现粒度是**每批第一条 upsert 行**：`resolveKeyedColumns` 只取 `folded` 中第一条 upsert 行的 keySet 与缓存比对；同批内其余行的 keySet 不再校验，`bindRow` 对缺失列 `setNull`、对多余列静默忽略。
- **风险**: 源库 DDL 恰好落在一个 checkpoint epoch 窗口内（同一批混合新旧 schema 事件）时：新增列静默不写（镜像语义丢数据）；删除可空列后，旧 schema 行按新列集继续写、新 schema 行缺失列被 `setNull` **覆盖旧值**——CDC 镜像目标表出现静默数据错误而非报错。文档/描述符声明的失败方向（typed error）未在批内兑现。
- **建议**: 在 `writeKeyedOps` 折叠后对每条 upsert 行执行一次 `keyedColumns.equals(op.row.keySet())` 校验（或至少校验 keySet 无增量且关键列存在），漂移即抛 `ERR_STREAM_STATE_ERROR`，与跨批守卫同一失败方向。
- **信心水平**: 确定（行为可直接从代码推出；`TestJdbcTwoPhaseCommitSinkDml` 无批内混合列集用例）
- **误报排除**: 常见误报是"列集从数据派生本身是缺陷"——本条不质疑动态列集设计（跨批漂移确有类型化守卫，且这是文档化契约）。本条指出的是该不变式的实现粒度与声明粒度错位（批间有守卫、批内没有），且失败方向是静默写错数据，属于契约-实现的精确缺口而非风格问题。
- **复核状态**: 未复核

### [CON-03] batch-consumer 能力声明 PARALLEL 与共享非同步缓冲实现矛盾：parallelism>1 必然数据损坏且无部署期门禁
- **文件**: `nop-stream/nop-stream-connector-batch/src/main/java/io/nop/stream/connector/batch/BatchConsumerSinkConnectorFactory.java:34-44`（声明 `PARALLEL`）、`nop-stream/nop-stream-connector-batch/src/main/java/io/nop/stream/connector/batch/BatchConsumerSinkFunction.java:40-58,80-93`（非同步 buffer + 单写者契约）、`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/StreamSinkOperator.java:69-75`（非 2PC sink 跨 subtask 共享同一实例）
- **证据片段**:
  ```java
  // 工厂描述符
  .sinkConsistency(SinkConsistencyCapability.IDEMPOTENT)
  .parallelism(ConnectorParallelism.PARALLEL)          // 声明可并行
  ...
  // 实现
  * Accordingly {@code consume()} is NOT designed for concurrent invocation
  * and the internal {@code buffer} is unsynchronized.
  private final List<R> buffer;                         // 无 copyForSubtask、无 validateParallelism
  ```
  `StreamSinkOperator.copyForSubtask` 只对 `TwoPhaseCommitSinkFunction` 复制用户函数，`BatchConsumerSinkFunction`（普通 `SinkFunction`）在 parallelism>1 时被所有 subtask 线程共享。
- **严重程度**: P2
- **现状**: 描述符与文档矩阵均宣告 batch-consumer 可并行（"无并行限制（单线程契约）"），但实现是有状态、刻意非同步、单实例共享的缓冲 sink；并行部署时多 subtask 线程并发 `buffer.add`/`flush`/`buffer.clear`，产生丢记录、错批、`ArrayIndexOutOfBounds` 等 未定义行为，且没有任何部署期校验拦截。
- **风险**: SPI 描述符是用户选型的契约面：它主动邀请 parallelism>1 部署，而该部署 100% 触发数据损坏；损坏模式（丢记录、错批）在 IDEMPOTENT 声明下会被下游误判为"幂等可重放"而难以追查。同族 `batch-loader` 刚在 R5 为完全相同的矛盾做了 `SINGLE_INSTANCE` + `ParallelismCheckable` 门禁修复（工厂注释自述"三个事实源的对齐"），consumer 侧是同一模式残留。
- **建议**: 与 batch-loader 对齐：描述符改 `SINGLE_INSTANCE`，`BatchConsumerSinkFunction` 实现 sink 侧并行度校验（部署期 fail-fast），或实现 `copyForSubtask` 为每 subtask 独立 consumer/buffer（语义需先裁定——每 subtask 独立实例才能兑现 PARALLEL）。
- **信心水平**: 确定（共享路径与并发面均可从代码直接推出）
- **误报排除**: "单线程契约已写进 Javadoc"不构成豁免：契约文档同时以 PARALLEL 描述符与矩阵行对外承诺可并行，两个事实源互相矛盾；且本代码库已在 R5 把同型矛盾（batch-loader PARALLEL 声明 vs 单写者实现）裁定为需修复的真实缺陷并落地了门禁，故本条是该已裁决模式的未修残留，不是把文档化取舍当缺陷的同类误报。
- **复核状态**: 未复核

### [CON-04] 文件 2PC sink 无临时文件/锁文件生命周期管理：多处路径残留 `.epoch-N.tmp`、`manifest.properties*.tmp`、`.manifest.lock`
- **文件**: `nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileTwoPhaseCommitSink.java:295-302`（守卫命中不删 temp）、`521-556`（manifest tmp 崩溃窗口）、`359-360`（`.manifest.lock` 常驻）、构造器 `153-168`（仅 createDirectories，无残留清理）
- **证据片段**:
  ```java
  if (manifest.containsKey(manifestEntryKey)) {
      LOG.warn("File 2PC commit skipped: ...");
      getPendingCommits().remove(checkpointId);
      return;                       // tempPath 对应的 .epoch-N.tmp 留在输出目录
  }
  ```
  另：`saveState` 中 `writeLines`（先于 `pendingCommits.put`）崩溃时 temp 已写但无 pending 记录，恢复分支永远不会 abort 它；`withManifestLock` 的锁文件无删除路径。
- **严重程度**: P3
- **现状**: 三类残留物（守卫命中/abort 前崩溃的 epoch temp、manifest 写入与 rename 之间的 tmp、锁文件）均无任何启动期或恢复期清理逻辑。
- **风险**: 恢复频繁的长期运行作业在输出目录积累隐藏垃圾文件；更实质的是它放大 CON-01——残留的 `.epoch-N.tmp` 使"目录是否可安全复用"对运维不可判读。
- **建议**: 构造/恢复时对输出目录做一次受限清理（仅删除匹配 `.epoch-*.tmp` / `manifest.properties*.tmp` 模式且不在任何 pending 条目中的文件）；守卫命中分支顺手删除对应 temp。
- **信心水平**: 确定
- **误报排除**: 不是"临时文件存在"本身——`ATOMIC_MOVE` 正常路径不留残骸；本条针对的是失败路径与守卫路径上确凿无清理的残留，且与 manifest 守卫语义（丢弃数据但保留其载体）直接相关，可定位、可量化。
- **复核状态**: 未复核

### [CON-05] NopStreamOffsetBackingStore 静态 registry 仅以 connector name 为键：同 JVM 并发作业同名连接器 offsets 交叉污染
- **文件**: `nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/engine/NopStreamOffsetBackingStore.java:59-103`（`REGISTRY`/`clearConnector`）、`189-205`（`setOffsets` REPLACE 语义）
- **证据片段**:
  ```java
  private static final ConcurrentHashMap<String, ConcurrentHashMap<ByteBuffer, ByteBuffer>> REGISTRY =
          new ConcurrentHashMap<>();
  public static void clearConnector(String connectorName) {
      REGISTRY.remove(connectorName);      // 作业 B 首启清掉注册项，作业 A 原引擎仍持旧 map
  }
  ...
  public void setOffsets(Map<ByteBuffer, ByteBuffer> offsets) {
      ...
      data.clear();                        // 作业 A 恢复时 forConnector 取回 B 的 map 并整体清空替换
  ```
- **严重程度**: P3
- **现状**: AR-03/R5-CON-03 修复了未命名连接器共享 `_default_` 桶与陈旧注册项两类问题，但同名连接器的**并发**双作业（同一 worker JVM 承载多作业在本框架中是常态形态）仍未隔离：B 首启 `clearConnector` 移除注册项后，A 的引擎继续写旧 map；A 随后 region 重启恢复时 `forConnector(name)` 取到的是 B 的新 map，`setOffsets` REPLACE 会清空 B 的活跃 offsets 并写入 A 的位点。
- **风险**: 两个 CDC 作业的恢复位点互相覆盖，表现为难以归因的重复消费/漏消费；名称唯一性当前仅靠约定（Debezium JMX serverName 冲突会卡死，但那只在 B-layer 并行同前缀场景被显式处理过）。
- **建议**: registry 键升级为 `connectorName@jobId`（需要把部署身份传入 source function，`copyForSubtask(TaskLocation)` 管线已有现成通道），或至少在 `clearConnector` 命中"仍被其他活跃 store 持有"时类型化报错。
- **信心水平**: 很可能（交叉污染路径成立，但需要同名+同 JVM+恢复交错三条件叠加）
- **误报排除**: 不是把"静态 registry 设计"当缺陷——它是 Debezium 2.4 无法直传 OffsetBackingStore 的合理桥接；本条是该桥接在已修复的两类场景之外仍存在的第三类键碰撞，且 `setOffsets` 的 REPLACE 语义（R5 修复本身）恰是使碰撞从"合并噪声"升级为"整体清空"的放大器。
- **复核状态**: 未复核

### [SEC-01] JdbcLeaderElector 将配置表名未转义拼入 SQL：与同引擎其他 JDBC 面的转义纪律不一致
- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/cluster/JdbcLeaderElector.java:104-108,163,272,426,462`（`leaseTable`/`epochCounterTable` 直接拼接）
- **证据片段**:
  ```java
  this.leaseTable = leaseTable != null ? leaseTable : "nop_stream_leader";
  this.epochCounterTable = this.leaseTable + "_epoch_counter";
  ...
  .sql("UPDATE " + leaseTable + " SET leader_epoch = ?, expire_at = ? "
  ...
  String ddl = "CREATE TABLE IF NOT EXISTS " + leaseTable + " (" +
  ```
- **严重程度**: P3
- **现状**: `leaseTable` 来自构造方配置且含派生表名，未经 `IDialect.escapeSQLName` 处理即拼入 DML/DDL；同引擎内 `JdbcTwoPhaseCommitSink`（表/列/ledger 全转义）与 `JdbcCheckpointStorage`（常量表名）执行了更严的纪律。
- **风险**: 防纵深缺口而非现实注入通道（表名属部署方自有配置，且未在 nop-stream main 代码中发现生产构造点，疑似由外部装配使用）；一旦进入用户可配置面（如未来暴露到 conf），与邻近组件的纪律差会直接变成注入入口。
- **建议**: 对 `leaseTable`/`epochCounterTable` 统一过 `escapeSQLName`（或构造期用 `isValidSimpleVarName` fail-fast），并在运行时维度复检同类配置表名入口。
- **信心水平**: 确定（代码事实）；实际可利用性低
- **误报排除**: 不是"表名拼接 = 注入"的一刀切误报：拼接常量类表名（`JdbcClusterRegistry` 的 `COORDINATOR_TABLE`）不构成注入面，未列入；本条仅针对确由外部配置进入且无任何转义/校验的入口，并以"同引擎纪律不一致"为结构性依据。
- **复核状态**: 未复核

---

## 深挖第 2 轮追加

第 2 轮以初审命中模式为准做了同类扫描，追加核实结论：

1. **守卫命名空间模式**（CON-01 同类）：`JdbcTwoPhaseCommitSink` 的 ledger 已有 namespace + fallback WARN（R5 已修，核实无残留缺口）；`FileTwoPhaseCommitSink` 是该模式唯一未修点（见 CON-01）。`JdbcCheckpointStorage` 的键含 `job_id/pipeline_id`，无此问题。
2. **批内/批间校验粒度模式**（CON-02 同类）：`FilePendingCommit` 回水合（`toFilePendingCommit`）、`normalizeBatch`（DmlOp/Map 双形态）均逐条目校验，异常即类型化报错，无同型粒度缺口。
3. **PARALLEL 声明 vs 实现模式**（CON-03 同类）：逐一核对其余 8 个工厂——`file`/`jdbc-2pc` 均实现 `copyForSubtask`（真并行，实至名归）；`message` source/sink 无状态（`{topic}-{subtaskIndex}` 分区模式/逐条直发），PARALLEL 成立；`debezium-cdc` 有 B 层表路由 + `validateParallelism` 门禁；`batch-loader` 已有 `SINGLE_INSTANCE` + 门禁。仅 batch-consumer 一处矛盾。
4. **凭证泄漏面**：全量扫描 nop-stream + nop-message-debezium main 代码的日志语句与异常参数——无明文密码/token 落点；`StreamCredentialSupport` 异常参数携带的是 `credential:` 引用串而非明文；`DebeziumConfig` 无 `toString` 泄漏面；checkpoint 快照持久化的是 sink 的 `pendingCommits`（业务数据行）与 CDC offset（base64 位点），均不含凭证。
5. **SQL 标识符入口**（SEC-01 同类）：`JdbcCheckpointStorage` 表名为类常量（不构成入口）；`JdbcClusterRegistry` 同为常量；`JdbcLeaderElector` 是唯一配置驱动入口（见 SEC-01）。
6. **目录/路径穿越面**：`FileSourceReader.openSplit` 已有 R5-CON-10 源目录包含性校验；`FileSplit`/枚举器序列化对保留字符 fail-fast；未发现新的穿越入口。

深挖第 2 轮未发现第 1 轮清单之外的新增高价值问题（第 1 轮的 CON-04/CON-05/SEC-01 即由第 2 轮扫描定级收录）。

---

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|---|---|---|---|
| CON-01 | P1 | nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileTwoPhaseCommitSink.java | manifest 守卫无运行代际命名空间，fresh run 复用输出目录时全部新 epoch 数据被静默丢弃 |
| CON-02 | P2 | nop-stream/nop-stream-connector-jdbc/src/main/java/io/nop/stream/connector/jdbc/JdbcTwoPhaseCommitSink.java | 键控模式列集漂移守卫只覆盖批间，批内后续行缺失列静默写 NULL、多余列静默丢弃 |
| CON-03 | P2 | nop-stream/nop-stream-connector-batch/src/main/java/io/nop/stream/connector/batch/BatchConsumerSinkConnectorFactory.java | batch-consumer 声明 PARALLEL 但实现为共享非同步缓冲，并行部署必然损坏且无门禁 |
| CON-04 | P3 | nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileTwoPhaseCommitSink.java | 守卫命中/崩溃窗口遗留 `.epoch-N.tmp`、manifest tmp 与 `.manifest.lock`，无任何清理路径 |
| CON-05 | P3 | nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/engine/NopStreamOffsetBackingStore.java | offset 静态 registry 仅按 connector name 分桶，同 JVM 同名并发作业恢复时位点互相覆盖 |
| SEC-01 | P3 | nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/cluster/JdbcLeaderElector.java | 配置表名未过 escapeSQLName 直接拼入 SQL，与同引擎其他 JDBC 面的转义纪律不一致 |

---

## 维度复核结论

> 复核人独立重读全部被引代码（FileTwoPhaseCommitSink 660 行全文、TwoPhaseCommitSinkFunction 基类全文、CheckpointIDCounter、GraphModelCheckpointExecutor/CheckpointCoordinator 计数与恢复链、OpsJobManager 提交链、FileTwoPhaseCommitSinkConnectorFactory、JdbcTwoPhaseCommitSink 相关段与 buildDeleteSql/buildKeyedUpsertSql、BatchConsumerSinkConnectorFactory/BatchConsumerSinkFunction/BatchLoaderSourceConnectorFactory、StreamSinkOperator 全文、OperatorChain.deepCopy、StreamConnectorCatalog 探测不变式、NopStreamOffsetBackingStore 全文、DebeziumCdcSourceFunction 命名派生、JdbcLeaderElector 全文、DialectImpl.escapeSQLName 实现、JdbcCheckpointStorage/JdbcClusterRegistry 表名面），未复用初审推导。初审全部行号引用与代码片段逐一与 HEAD 比对，**零失实**。

| 发现 | 判定 | 现状归属 | 复核要点 |
|---|---|---|---|
| CON-01 | 保留（P1） | 已知未修复（R5 CON-08 已登记） | 触发链全链独立推导成立（见 P1 重点复核详情）；live code 修复注释（FileTwoPhaseCommitSink.java:296-298、307-309 "R5 CON-08"）锚定 R5 CON-08 即本问题，R5 交付物仅是 WARN 措辞与注释，命名空间缺口原样保留 |
| CON-02 | 保留（P2） | 新发现 | `resolveKeyedColumns`（:988-1020）确只取 folded 首条 upsert 行 keySet 与缓存比对；`bindRow`（:1030-1042）缺失列 `setNull`、多余列忽略；逐记录仅校验 keyColumns（`verifyKeyColumnsPresent`:586-592），值列集无批内校验。批间守卫（:1012-1017 类型化报错）与批内缺口并存，契约-实现粒度错位属实 |
| CON-03 | 保留（P2） | R5 已修复的新残余 | 描述符 PARALLEL（工厂 :37）与实现单写者契约（:40-46 javadoc 自述）矛盾属实；`StreamSinkOperator.copyForSubtask`（:69-75、:86-92）只拷贝 2PC sink，普通 SinkFunction 跨 subtask 共享确认；无门禁确认——`ParallelismCheckable.validateParallelism` 仅 source 侧被调（GraphExecutionPlan.java:526-527、RemoteGraphExecutionPlanBuilder.java:347），catalog 只约束 2PC⟹PARALLEL（StreamConnectorCatalog :213-224 "Non-2PC endpoints are unconstrained"）；batch-loader 同族修复注释（BatchLoaderSourceConnectorFactory :37-41 "R5 CON-06"）证实先例。措辞注记："必然损坏"是工程化表述（无同步竞争面覆盖每条记录 + 共享 `flushed` 标志与单例 consumer），严格说是未定义行为/高概率损坏，不影响定级 |
| CON-04 | 保留（P3） | 新发现 | 三类残留逐条证实：守卫命中/崩溃路径无删除（全文仅 :440 一处 `deleteIfExistsQuiet`，位于 abort）、manifest tmp 崩溃窗口（:521-556）、saveState 先 writeLines 后 `pendingCommits.put`（:243-250）的崩溃窗口无 pending 记录可 abort；构造器仅 `createDirectories`（:153-168）。一处从轻修正：`.manifest.lock` 常驻更接近 AR-14 跨 JVM 文件锁的必要设计（删除可能正被锁住的文件会破坏互斥），残留主论证落在 epoch tmp 与 manifest tmp 两类，不影响 P3 |
| CON-05 | 保留（P3） | R5 已修复的新残余 | REGISTRY 仅以 connector name 为键（:59-60）、`clearConnector` 直接 `remove`（:101-103）、`setOffsets` REPLACE 先 `data.clear()`（:194-204）均证实；同名可达性核实：`instanceName` 仅在 parallelism>1 时追加 subtaskIndex（DebeziumCdcSourceFunction.java:584-586），两个 parallelism=1 作业配置同名 `DebeziumConfig.name` 即共享同一桶；A 恢复路径 `forConnector`+`setOffsets`（:431-432）在 B 首启 `clearConnector` 换桶后清空 B 活跃 offsets 的链路成立。三条件叠加 + 名称唯一仅靠约定，P3/"很可能"恰当 |
| SEC-01 | 保留（P3） | 新发现 | `leaseTable`/派生 `epochCounterTable` 裸拼入 SQL 共 11 处（:163、205、272、317、332、364、380、396、426、462、481），全类无任何转义/校验；纪律对照证实：JdbcTwoPhaseCommitSink 全量 `escapeSQLName`（:768-1116 各拼接点），JdbcCheckpointStorage（`stream_checkpoint`/`stream_epoch_manifest` 类常量）与 JdbcClusterRegistry（`COORDINATOR_TABLE` 常量）不构成配置入口，JdbcLeaderElector 确是 runtime 维度唯一配置驱动表名；装配面核实：全仓构造点仅测试树 4 处且均用单参构造（常量默认 `nop_stream_leader`），3 参构造无生产调用方、无 conf 键入口——初审"防纵深缺口而非现实注入通道"的定级依据准确。`escapeSQLName` 实现（DialectImpl.java:614-638，`isValidSimpleVarName` 放行 + `quoteDupEscapeString` 双写转义）与初审描述一致 |

### P1 重点复核详情

（CON-01 独立推导链与裁定，全部现场验证，未复用初审文本）

1. **manifest 键结构**：`manifestKey(subtaskIndex, epochId) = epochId + subtaskSuffix(subtaskIndex)`（FileTwoPhaseCommitSink.java:588-590；suffix 仅 `.sN`，subtask 0 无后缀 :597-599）。键空间 = `(epochId, subtaskIndex)`，无任何 job/run/运行代际维度；工厂参数仅 `outputDir`/`charset`（FileTwoPhaseCommitSinkConnectorFactory.java:37-43），连 namespace 配置入口都不存在。
2. **fresh run 判定与 epoch 来源**：所有执行入口恒定 `new CheckpointIDCounter()`（初始值 0，CheckpointIDCounter.java:24-26；构造点共 5 处：GraphModelCheckpointExecutor.java:228/357、EmbeddedDistributedExecutor.java:254、RpcDistributedExecutor.java:213、OpsJobManager.java:138）。首 epoch = 0（CheckpointCoordinator.java:481 `getAndIncrement()`）。唯一推进点是恢复路径 `advanceCheckpointIdCounterAfterRestore`（GraphModelCheckpointExecutor.java:967），其依据是 **checkpoint 存储**的 EpochManifest，与 sink **输出目录**的 manifest 完全正交。关键放大器：默认 checkpoint 存储（无显式 `path`）时 auto-restore 被显式禁用、固定 "Starting fresh"（GraphModelCheckpointExecutor.java:247-256）——崩溃后按默认配置重启的作业必然从 epoch 0 重放，无需任何"运维重置"动作。
3. **复用输出目录时新旧 manifest 交互**：新运行首个 committing epoch N 满足：saveState 已把本运行新数据写入 `.epoch-N.tmp` 并登记 pending（:238-255）→ `finishCommit` 驱动 `commit(N)`（TwoPhaseCommitSinkFunction.java:176-205）→ `doCommitLocked`：`manifestKey` 与旧运行遗留键碰撞 → 守卫分支（:294-302）`manifest.containsKey` 为真 → 仅 `LOG.warn`（文案自认 "recover-safe re-commit or run collision"）→ `getPendingCommits().remove(N)` → return。新数据 temp 既不 rename 也不删除。若键侥幸不碰撞但 final 文件存在（旧运行产物 `epoch-N.txt`），:304-315 repair 分支把外来文件 `setProperty` 进本运行 manifest——认领旧数据、其效果同样是本运行新数据不可见。
4. **"静默丢弃"的确切路径**：数据物理上滞留为隐藏 temp 文件（非销毁），但消费面按 manifest 读 final 文件，永远看不到新数据；作业正常完成、无错误上抛，丢失仅 WARN 日志可见。属消费语义上的整运行数据丢失（每个与旧 manifest 碰撞的 epoch 一份）。
5. **遗漏保护逐项排查——均不存在**：(a) 目录预检：构造器仅 `createDirectories`（:153-168），`checkConnection` 仅校验目录存在（:457-464），工厂直构（:55-60）；(b) epoch 单调校验：无任何"manifest 已有键 ≥ 本运行首 epoch"的启动期检查；(c) 启动清理：sink/工厂/执行器均无输出目录清理（CON-04 的残留物反而使"目录是否可复用"对运维更不可判读，两缺陷互为放大）。

**裁定**：保留 P1。依据四点：(a) 触发不依赖罕见运维动作——默认 checkpoint 配置下崩溃重启即满足全部前置；(b) 失败方向是 exactly-once 契约的单侧破坏（丢新数据 + 认领旧数据）且无错误上抛；(c) 同仓库对同型缺陷已两次结构化裁定——JDBC ledger 的 R5 `sink_namespace` 修复（JdbcTwoPhaseCommitSink.java:89-99、:396-425，fallback WARN 明文将"两个作业共享键空间导致静默跳写"定为须消解缺陷）与 R5 REG-1（guard 命中静默跳写在 remote 面判 P1）；(d) R5 CON-08 已登记本问题而修复仅落在 WARN/注释（live 注释 :296-298 自述 "silent swallowing is how cross-run collisions hid"），结构性缺口原样保留。测试面核查：TestFileTwoPhaseCommitSink 16 个用例覆盖单运行代际、kill-recover、并行拷贝，无跨运行用例，不构成"已裁定可接受行为"的证据。

**现状归属修正说明**：初审正文未标注本条与 R5 的关系。复核以 live code 修复注释锚定（不读历史审计正文）：R5 CON-08 即 file sink 守卫跨运行碰撞问题（:296、:307），故本条归属"已知未修复（R5 CON-08 已登记）"而非新发现——这提高而非降低其可信度。同法锚定：R5 CON-04 实为 saveState fsync 时序（:244-248 已落地）与 MessageSourceFunction 生命周期（MessageSourceFunction.java:136、197）两处修复，与本轮 CON-04（tmp/锁残留）**非同一问题**，本轮 CON-04 为新发现；R5 CON-06 即 batch-loader SINGLE_INSTANCE+门禁修复（BatchLoaderSourceConnectorFactory.java:37-41），本轮 CON-03 为其同族新残余。

## 最终保留项（复核后）

| 编号 | 复核后严重程度 | 文件路径 | 一句话摘要 | 现状归属 |
|---|---|---|---|---|
| CON-01 | P1（维持） | nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileTwoPhaseCommitSink.java | manifest 守卫无运行代际命名空间：fresh run 复用输出目录时新 epoch 数据滞留 temp 对消费者永久不可见、repair 分支认领旧文件，作业正常完成仅 WARN | 已知未修复（R5 CON-08 已登记，R5 仅落 WARN 缓解） |
| CON-02 | P2（维持） | nop-stream/nop-stream-connector-jdbc/src/main/java/io/nop/stream/connector/jdbc/JdbcTwoPhaseCommitSink.java | 键控模式列集漂移守卫只比对每批首条 upsert 行，批内后续行缺失列静默写 NULL、多余列静默丢弃 | 新发现 |
| CON-03 | P2（维持） | nop-stream/nop-stream-connector-batch/src/main/java/io/nop/stream/connector/batch/BatchConsumerSinkConnectorFactory.java | batch-consumer 声明 PARALLEL 但实现为共享非同步缓冲，且并行度门禁仅存在于 source 侧、sink 无任何部署期校验 | R5 已修复的新残余（同族 batch-loader 已由 R5 CON-06 修复） |
| CON-04 | P3（维持） | nop-stream/nop-stream-connector/src/main/java/io/nop/stream/connector/file/FileTwoPhaseCommitSink.java | 守卫命中/崩溃窗口遗留 `.epoch-N.tmp` 与 manifest tmp 无任何清理路径（`.manifest.lock` 常驻属锁语义必要设计，此子项从轻） | 新发现 |
| CON-05 | P3（维持） | nop-message/nop-message-debezium/src/main/java/io/nop/message/debezium/engine/NopStreamOffsetBackingStore.java | offset 静态 registry 仅按 connector name 分桶，同名并发作业恢复时 `setOffsets` REPLACE 清空对方活跃位点 | R5 已修复的新残余（R5-CON-03/AR-03 修复两类碰撞后的第三类） |
| SEC-01 | P3（维持） | nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/cluster/JdbcLeaderElector.java | 配置表名 11 处裸拼入 SQL、全类无 escapeSQLName，与同引擎转义纪律不一致；生产装配面缺位，属防纵深缺口 | 新发现 |

复核统计：6 条初审发现，保留 6、降级 0、驳回 0；严重程度全部维持初审判级；证据失实 0（初审引用的全部行号与片段与 HEAD 一致）。
