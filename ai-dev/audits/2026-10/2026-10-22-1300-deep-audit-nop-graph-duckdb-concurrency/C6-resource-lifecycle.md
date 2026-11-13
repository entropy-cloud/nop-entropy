# C6 资源生命周期与泄漏 — 初审报告（盲审）

- **审计单元**: nop-duckdb 6f/837（主战场）+ nop-graph-core 13f + nop-graph-api 14f（plan-first 只读），共 33f/2,932 行（src/main，与派发口径一致）
- **维度**: C6（资源生命周期与泄漏）— CRA WI 5.6
- **盲审声明**: 初审期间未阅读 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`、`ai-dev/backlog/` 下任何历史记录；仅阅读方法论文件（`ai-dev/skills/deep-audit-prompts.md`、`ai-dev/skills/concurrency-resource-audit-prompt.md`）、主 agent 提供的基线（`_tmp/cra-baseline-nop-graph-duckdb.txt`）与 live code。为核实使用点的 close 契约，跨模块只读核对了 4 个 callee（`IoHelper.safeCloseObject`、`XlsxReader.read/readMultiple/getResource`、`AbstractXlsxParser.doParseResource`、`AbstractResourceParser.parseFromResource`），未将其纳入发现对象。只读审计，未修改任何文件。
- **CL 前缀**: 本单元跨维度线索自 CL-101 起（CL-001~100 保留给其他维度/单元）。
- **批次**: 全单元单批（33 文件全部读毕：nop-duckdb 6 文件与 nop-graph-core 13 文件逐行核读；nop-graph-api 14 文件经资源 API grep 零命中 + `IGraph`/`Edge` 抽查核实为无资源 POJO）。
- **基线**: `_tmp/cra-baseline-nop-graph-duckdb.txt` @ 6934df7ad3（撰写时 HEAD 一致，审计单元内工作树干净）。

---

## 1. 资源获取点配对表（逐项判定）

判定口径：✓ 配对完整（成功/异常/取消/超时路径均释放）；△ 部分配对（存在未闭合路径，立案或注明）；— 无资源。

### nop-duckdb（主战场）

| # | 资源 | 获取点 | 释放点 | 路径核对 | 判定 |
|---|------|--------|--------|----------|------|
| R1 | JDBC Connection（内存库） | `DuckDbEngine.openMemory` L63-73 → `openConnection` L165 `DriverManager.getConnection` | `close()` L113-114（tracked 快照）；`applySettings` 失败路径 L69 `safeCloseObject` | 成功✓；applySettings 失败✓（先关再抛，不注册 OPEN_FILES，正确）；**close/open 竞态路径 ✗ → C6-002** | △ |
| R2 | JDBC Connection（文件库） | `DuckDbEngine.openFile` L76-100 | `close()` L113-116（关连接 + `releaseOpen`） | 成功✓；applySettings 失败✓（L95 关闭、未注册，平衡）；调用方直接 `conn.close()` 旁路 → 注册表 ref 不放 → **C6-001**；close/open 竞态 → **C6-002** | △ |
| R3 | `OPEN_FILES` 静态注册表项 | `registerOpen` L98（唯一注册点，L218-226） | `releaseOpen`（**唯一调用点 `close()` L116**，L228-238） | engine.close() 生命周期内配对✓；try-with-resources 旁路路径只注册不释放 → **C6-001** | △ |
| R4 | Statement（SET 配置） | `execSet` L184 | try-with-resources L184 | 全路径闭合；SET 失败时 conn 由调用方（R1/R2 的 catch）关闭 | ✓ |
| R5 | Statement（ingest CTAS） | `DuckDbFiles.readExternal` L138 | try-with-resources L138-139 | ✓；CTAS 失败由 DuckDB 事务回滚，无残留句柄 | ✓ |
| R6 | Statement/ResultSet（count/exists） | `countRows` L169-171、`tableExists` L182-184 | try-with-resources | ✓ | ✓ |
| R7 | PreparedStatement（COPY 导出） | `copyTo` L154 | try-with-resources L154-160 | ✓；COPY 中途失败可留残缺目标文件——属 F2"半个成功"面，但类注释 L32 已声明归档覆写语义，导出残缺文件语义未声明（弱信号，不立案，附 CL-103 旁注） | ✓ |
| R8 | 临时 CSV（xlsx 桥接） | `readXlsx` L72 `Files.createTempFile` | finally `delete()` L83-86 + `deleteOnExit()` L73 | 成功✓；**xlsx→CSV 转换失败路径（L76-80）不删除 → C6-003**；`deleteOnExit` 全局 hook 只增不删（含成功路径） | △ |
| R9 | XlsxReader / Tablesaw Table / OOXML package | `readXlsx` L74 | 委托 nop-tablesaw → nop-ooxml `AbstractXlsxParser.doParseResource` finally `IoHelper.safeCloseObject(pkg)`（callee 链逐跳核实） | 核实安全，记表不立案 | ✓ |
| R10 | Task step Connection | `DuckDbSqlTaskStep.execute` L85 `openConnection(dbPath)` | try-with-resources L85 | conn 本体全路径闭合✓；openFile 抛错（locked/closed）时无连接可泄漏✓；但该关闭方式触发 R3 的注册表旁路（C6-001 的成因侧） | ✓（本体） |
| R11 | Step 内 Statement/ResultSet | `materialize` L127/L131-133、`executeSql` L144/L149 | try-with-resources | ✓ | ✓ |

### nop-graph-core

| # | 资源 | 获取点 | 释放点 | 路径核对 | 判定 |
|---|------|--------|--------|----------|------|
| R12 | ExecutorService（单线程，每调用新建） | `LeidenDetector.runWithTimeout` L197 | `finally executor.shutdownNow()` L202 | 创建/关闭在成功、异常、超时（`future.get` 抛 TimeoutException）三路径均闭合，executor 句柄无泄漏；**超时路径 ghost 任务与非 daemon 线程问题见 §3 站点核查记录（三态裁定归 Phase 4）** | △（站点核查） |
| R13 | 其余 12 文件（GraphExporter/InMemoryGraph/DirectedGraphAdapter/Bfs/PageRank/LabelPropagation/TarjanSCC/TopologicalSort/BetweennessCentrality/ImpactPropagator/GraphDiffer/PathQueryExecutor） | 纯内存计算，无 OS 资源获取（资源 API grep 零命中 + 逐行核读确认：StringWriter/StringBuilder/集合，无 Closeable 实现） | — | — | ✓ |

### nop-graph-api（plan-first 只读，未改任何文件）

| # | 资源 | 获取点 | 释放点 | 路径核对 | 判定 |
|---|------|--------|--------|----------|------|
| R14 | 14 文件（Edge/IGraph/各 Result/Config POJO） | 无资源获取（grep `Closeable|close|InputStream|File|register` 零命中；`IGraph` L17-33 仅为两查询方法接口） | — | — | ✓ |

### Closeable 实现盘点（重点面 4）

单元内 `AutoCloseable/Closeable` 实现仅 1 处：`IDuckDbEngine extends AutoCloseable`（L19）→ `DuckDbEngine.close()`：`closed` 标志幂等（L106-108 二次 close 早退，安全）、快照后锁外逐个 `safeCloseObject`（callee 已核实吞异常，单连接 close 失败不阻断其余连接的释放）、`releaseOpen` 逆序无要求（引用计数）。close 语义本身✓；其与 open 的竞态见 C6-002。

### LOCK_MARKER 重试逻辑核查（重点面 1，roadmap 点名）

**live code 中不存在锁标记重试逻辑。** `DuckDbEngine.openConnection` L140-146 将 `LOCK_MARKER`（"Could not set lock on file"）一次性翻译为 `ERR_DUCKDB_FILE_LOCKED` 且 `bizFatal(true)`（注释 L141："permanent while the holder lives: single writer per file (WI0 Q3)"）。无重试上限/退避代码；失败发生在 `DriverManager.getConnection` 内部，**无已获取连接需要清理**（连接从未建成）——失败路径干净。任务层的 retry 装饰器（`filelocked-retry.task.xml` L5 `maxRetryCount="3"`）由 `bizFatal` 裁定不得重试（`TestFileLockedStep` L10-11 注释与断言口径一致）。跨进程锁持有方对照：`LockHolderMain` L16-24 try-with-resources，干净。结论：该重点面现状为 fail-fast 设计，无资源泄漏路径。

---

## 2. 发现条目

### [C6-001] OPEN_FILES 注册表在主消费路径上"只注册不释放"：静态注册表单调泄漏 + 过期指纹误拒同文件复用

- **文件**: `nop-duckdb/src/main/java/io/nop/duckdb/DuckDbEngine.java:98,116,213-238`；成因侧 `nop-duckdb/src/main/java/io/nop/duckdb/DuckDbSqlTaskStep.java:85`
- **证据片段**:
  ```java
  // DuckDbEngine.java — 注册与释放的两端
  98:        registerOpen(normalized, fingerprint);      // openFile 成功后 refs+1
  99:        return track(conn, normalized);
  ...
  113:        for (TrackedConnection tc : toClose) {
  114:            IoHelper.safeCloseObject(tc.connection);
  115:            if (tc.filePath != null) {
  116:                releaseOpen(tc.filePath);            // releaseOpen 的唯一调用点
  ...
  48:    private static final Map<String, FileEntry> OPEN_FILES = new ConcurrentHashMap<>();
  ```
  ```java
  // DuckDbSqlTaskStep.java — 生产消费者在 engine 不知情的情况下关闭连接
  85:        try (Connection conn = openConnection(dbPath)) {   // engine 为共享单例 bean
  ...
  118:        return stepRt.RETURN(outputs);                     // conn 已被 TWRS 关闭，refs 未释放
  ```
- **严重程度**: P1
- **并发场景**: 单例 `nopDuckDbEngine` bean（`app-duckdb.beans.xml` L13-15，`destroy-method="close"`，仅容器停机时调用）+ 多任务线程并发执行 `DuckDbSqlTaskStep`（每步一连接）。非数据竞争，是跨生命周期配对破损：连接的 close 权在调用方（TWRS），ref 的 release 权在 engine.close()，两者无桥接。
- **触发时序**: 单线程即可复现：S1 step `execute` → `engine.openFile(X)` → L98 `registerOpen` refs=1 → S2 step 结束，TWRS 关闭 conn（engine 无感知）→ `OPEN_FILES["X"]` 永久残留 refs≥1。若各任务使用各自临时 `.duckdb`（`DuckDbFiles` javadoc L21"task-level working sets"声明的用法，测试即以 `Files.createTempDirectory` 每次生成唯一 dbPath 实现），map 按"曾打开过的不同路径数"单调增长；同一 X 重复执行则 refs 单调递增。后果二（误拒）：X 的连接全部关闭后，任一**不同配置**的 engine 实例再 `openFile(X)`，在 L83 被残留的过期指纹误判为 config-conflict（`ERR_DUCKDB_CONFIG_CONFLICT`）——而该注册表的自述职责（L44-47）正是拦截"不同配置的第二 engine"，此时文件实际空闲，属守卫假阳性。
- **现状**: `registerOpen`（openFile L98，唯一注册点）与 `releaseOpen`（close L116，唯一释放点）的生命周期不对称；模块自身唯一生产消费者 `DuckDbSqlTaskStep` 恰好走"直接关连接"旁路，每步执行泄漏一个 ref。
- **风险**: 长时运行分析服务中静态 map 无界增长（每任务一个 FileEntry，small 但只增不删，F4 族）；过期指纹使同路径的跨 engine 复用被永久误拒（错误码用户可见，且无自愈路径——除非同指纹 engine 再 close 到 refs 归零，而 step 路径永远凑不齐）。
- **建议**: 让连接自身的关闭触发 `releaseOpen`：(a) `openFile` 返回 close 时回调 `releaseOpen` 的 Connection 代理（包装类，另需保证 `track` 记录原对象供 engine.close 幂等兜底）；或 (b) `TrackedConnection` 以弱引用关联 + `releaseOpen` 挂在 Connection close 上；或 (c) 最小语义修正——`IDuckDbEngine` 契约补充"调用方不得直接 close 连接，必须用 engine.close"，并把 `DuckDbSqlTaskStep` 改为向 engine 借还（复用 `io.nop.commons.pool.IPool` 契约形态）。方向上优先 (a)，改动局限在 openFile 返回值。
- **失效面枚举**: 注册点仅 `openFile` L98 一处（`openMemory` 不注册——内存库无文件，正确豁免）；`releaseOpen` 调用点仅 `close()` L116 一处；直接关连接的同族消费者全仓 grep：in-repo 仅 `DuckDbSqlTaskStep` 一处生产代码（测试 `TestDuckDbTaskStep` L74/L120 用裸 DriverManager 绕过注册表，属测试专用，不立案）；`OPEN_FILES` 读取点仅 L83 指纹检查一处。
- **信心水平**: 确定（代码路径直接可证，无需时序推测）
- **误报排除**: 不是"有界声明存在的缓存"——OPEN_FILES 无容量配置、无淘汰策略，且其语义是打开计数而非缓存；不是 Nop 平台标准模式（无生成文件介入）；后果链有用户可见错误码，非"看起来不优雅"。
- **复核状态**: 已复核（C6 维度独立复核 2026-10-22）——保留 P1；采纳 C6-R2-001 附带修正为边界限定（refs/条目滞留止于 engine 存活期，`releaseOpen` 挂 engine.close()，单例 bean 下=应用存活期、运营上等同永不释放）；P1 定级在修正后维持，判级理由见文末「维度复核结论」。

### [C6-002] open/close 竞态：checkClosed 通过后 close 清空快照，迟到连接 track 进已关闭引擎 → native 句柄与单写者文件锁泄漏

- **文件**: `nop-duckdb/src/main/java/io/nop/duckdb/DuckDbEngine.java:62-77,103-120,213-216`
- **证据片段**:
  ```java
  63:    public Connection openMemory() {
  64:        checkClosed();                       // T1 在此通过（closed=false），此后不再复查
  ...
  103:    public void close() {
  105:        synchronized (this) {
  106:            if (closed) { return; }
  109:            closed = true;
  110:            toClose = new ArrayList<>(connections);   // 快照不含 T1 未 track 的连接
  111:            connections.clear();
  ...
  213:    private synchronized Connection track(Connection conn, String normalizedFilePath) {
  214:        connections.add(new TrackedConnection(conn, normalizedFilePath));  // 不复查 closed
  ```
- **严重程度**: P1
- **并发场景**: 调用方线程执行 `openFile/openMemory` 与停机路径（IoC 容器 destroy-method 或直接使用方 finally）执行 `close()` 并发；engine 为共享单例。
- **触发时序**: T1 `openFile` L77 `checkClosed` 通过（closed=false）→ T2 `close()` L105-111（closed=true，快照不含 T1 的连接，clear）→ T2 关闭快照、`releaseOpen` 后返回 → T1 L91 `openConnection` 成功（**已持有 .duckdb 单写者文件锁**）→ T1 L98 `registerOpen` refs+1 → T1 L99 `track` 把连接加入已关闭引擎的 `connections`。此后：再次 `close()` 在 L106 早退（幂等），该连接永无人关闭；依赖 engine.close() 释放的调用方（接口契约 `IDuckDbEngine` L16/L33-36）即泄漏 native 句柄 + 文件锁（其他进程无法打开该文件，直至 GC 兜底）。in-repo 的 step 消费者因 TWRS 自行关连接，native 不泄漏，但残余 refs 泄漏并入 C6-001。
- **现状**: `checkClosed` 是唯一关闭守卫，位于 open 入口；`track`（synchronized(this)，与 close 同锁）不复查 `closed`，锁内检查-然后-行动不完整。
- **风险**: 停机竞态下的文件锁泄漏直接命中本模块的"单写者"核心约束面；泄漏连接持锁期间该 .duckdb 对所有其他进程不可写，且无错误线索（engine 已 closed，报错无从谈起）。
- **建议**: `track()` 内（已持 engine 锁）复查 `closed`：若已关闭，先 `IoHelper.safeCloseObject(conn)` + `filePath != null` 时 `releaseOpen(filePath)`，再抛 `ERR_DUCKDB_ENGINE_CLOSED`。一处修改同时封死 openMemory/openFile 两个入口的同类窗口。
- **失效面枚举**: `checkClosed` 调用点共 2 处：`openMemory` L64、`openFile` L77——同族同修；`track` 是连接入队唯一点 L214；`close` 幂等早退点 L106（本发现正是利用了该幂等性）；无其他绕过 track 的连接出口。
- **信心水平**: 确定（机制可从代码直接构造）；运营触发频率为"很可能"（依赖停机与在途 open 重叠的窗口）
- **误报排除**: 非"看到共享字段就报线程不安全"——已给出 T1/T2 具体交错与锁内不复查的落点；非低频路径粗锁误报（问题恰是锁内缺一次检查）；按本领域校准"罕见但可具体化的竞态（有明确触发链）"立案 P1，若 Phase 4 认为停机竞态运营权重低可降 P2。
- **复核状态**: 已复核（C6 维度独立复核 2026-10-22）——保留 P1，确认为 open/close 竞态的**主登记**（C1-002 已并入、CS-CL-201 为第三确认）；「可降 P2」注记不采信为判级变更，仅留 Phase 6 合并裁定备选口径（见文末「维度复核结论」）。

### [C6-003] readXlsx 桥接：xlsx→CSV 转换失败路径临时文件不删除；deleteOnExit 全局 hook 每调用只增不删

- **文件**: `nop-duckdb/src/main/java/io/nop/duckdb/DuckDbFiles.java:68-87`
- **证据片段**:
  ```java
  71:        try {
  72:            tempCsv = Files.createTempFile("duckdb-xlsx-", ".csv").toFile();
  73:            tempCsv.deleteOnExit();
  74:            Table table = new XlsxReader().read(XlsxReadOptions.builder(xlsxPath).build());
  75:            table.write().csv(tempCsv.getAbsolutePath());
  76:        } catch (IOException | RuntimeException e) {
  77:            throw new NopDuckDbException(...);        // tempCsv 未删除
  78:        }
  81:        try {
  82:            return readCsv(conn, tempCsv.getAbsolutePath(), tableName);
  83:        } finally {
  85:            tempCsv.delete();                          // 仅覆盖第二段 try
  86:        }
  ```
- **严重程度**: P2
- **并发场景**: 任务线程执行 xlsx ingest（`DuckDbSqlTaskStep` ingestXlsx 无、`readXlsx` 公开 API 直接调用）；单线程即可触发，无需竞争。
- **触发时序**: L72 创建临时 CSV（内容镜像整个 xlsx，可能大）→ L74/L75 任一抛出（损坏 xlsx / 写盘 IO 错）→ L76-80 catch 包装抛出，L81-86 的 finally 不在第一段 try 的作用域 → 临时文件残留至 JVM 退出。叠加放大：任务层 retry 装饰器对非 bizFatal 的 `ERR_DUCKDB_IO_FAILED` 可重试（`filelocked-retry.task.xml` 证明 retry 装饰器在用），每次重试再泄一个文件。次生面：L73 `deleteOnExit` 在**每次调用**（含成功路径）向全局 `java.io.DeleteOnExitHook` 追加路径，该集合无移除 API，进程生命周期内只增不删（F4 族微缩版）。
- **现状**: 删除逻辑与创建逻辑分属两个 try 块，第一段的失败路径无清理；`deleteOnExit` 兜底仅对"JVM 会退出"的场景有效。
- **风险**: 长驻服务上失败的 xlsx 转换按次累积磁盘垃圾（单文件可达 xlsx 解压后量级）；DeleteOnExitHook 集合单调增长。严重度按 P2：泄漏有 JVM 退出兜底、单次有界、失败路径通常有人工介入，未达 P1"主路径/必然"档。
- **建议**: 将 L72-75 与 readCsv 合并进同一 try/finally（或以 try-with-resources 抽象临时文件），成功失败均 `delete()`；`deleteOnExit` 可移除（finally 已覆盖成功路径）——注意平台惯例禁用系统级 `/tmp` 之外还要求项目 `_tmp/`，此处为运行期用户数据桥接文件，保留系统临时目录合理，仅修配对即可。
- **失效面枚举**: 单元内临时文件创建点仅此一处（grep `createTempFile` 全单元仅 L72）；readCsv/readParquet/writeCsv/writeParquet 均为 DuckDB 进程内读写、不落中间文件，无同族兄弟；`delete()` 忽略返回值仅此一处（成功路径下 readCsv 已释放句柄，Windows 语义亦安全）。
- **信心水平**: 确定
- **误报排除**: 非生成代码、非误报校准条目；F8"清理不对称"实锤（创建点与删除点路径覆盖不齐），非"看起来不优雅"。
- **复核状态**: 已复核（C6 维度独立复核 2026-10-22）——保留 P2；证据/判级重核成立（L76-80 catch 重抛无删除、finally 仅覆盖第二段 try），与 CS-003 的 deleteOnExit 重叠面建议归并（Phase 6）。

---

## 3. 站点核查记录：LeidenDetector.runWithTimeout executor（三态裁定归 Phase 4）

按派发要求，以下为站点事实记录，**本初审不做三态裁定**。

- **站点**: `nop-graph/nop-graph-core/src/main/java/io/nop/graph/algorithm/LeidenDetector.java:196-204`
- **生命周期配对**: L197 `Executors.newSingleThreadExecutor()` 每调用新建；L202 `finally executor.shutdownNow()`。成功、算法异常、超时（L200 `future.get(timeoutMs)` 抛 TimeoutException）三路径均执行 finally——**executor 句柄本身无泄漏**。
- **超时路径事实**: `shutdownNow` 仅发出中断；被提交任务 `leiden.findClustering(network)`（L113）为 CWTS `LeidenAlgorithm` 纯 CPU 计算，无中断检查点——超时后 ghost 计算继续占线直至自然结束。线程由默认 ThreadFactory 创建，**非 daemon**——ghost 未结束时阻塞 JVM 退出。ghost 存续期间 `Network`/两根 `LargeIntArray`（L92-94，规模 = 边数）不可回收。
- **关联事实（F9 面）**: L136-140 `catch (Exception e)` 将 TimeoutException 与算法失败统一静默降级为 LabelPropagation（`LOG.warn` 之外对调用方不可分辨）；降级路径 `LabelPropagation.detect` 自身无超时参数——`LeidenConfig.timeoutMs` 的超时保护在降级后失效。
- **C6 立场**: executor 创建/关闭配对 ✓；泄漏争议面 = ghost 线程 × CPU × 内存驻留（超时路径）。泄漏/非泄漏/条件泄漏三态裁定移交 Phase 4 独立复核。

---

## 4. 维度集外线索（CL，自 CL-101 起）

### [CL-101] Leiden 降级路径静默吞异常 + 超时保护失效
- **位置**: `LeidenDetector.java:124-140`
- **摘要**: L136-140 `catch (Exception)` 吞掉一切异常（含 TimeoutException、OutOfMemoryError 之外的所有 RuntimeException）降级 LabelPropagation，仅 `LOG.warn`；且降级算法无超时。涉 F9（吞异常→状态不一致）与 C7（挂死可检测性：降级后无超时上限）。→ 建议复核维度：C3/C7。

### [CL-102] DuckDbSqlTaskStep 每步新建连接的固定开销
- **位置**: `DuckDbSqlTaskStep.java:85,139-141`
- **摘要**: 每步执行都 `engine.openFile`（含 DuckDB 实例 attach + 3 条 SET）并弃置；多步 pipeline 对同一 dbPath 反复开合连接。非泄漏（C6 已核对闭合），属连接管理/性能面。→ 建议复核维度：C9（含与 `IPool` 平台设施的适配性）。

### [CL-103] 注册表路径归一化与 JDBC URL 不一致；COPY 失败残缺文件语义未声明
- **位置**: `DuckDbEngine.java:79,91`；`DuckDbFiles.java:152-165`
- **摘要**: (a) L79 用绝对路径查 OPEN_FILES，L91 却把**原始** `filePath` 拼进 JDBC URL——同一物理文件的别名（相对路径/符号链接）绕过指纹检查、直达 DuckDB 锁错误，错误码语义不一致（C1/C2 弱信号）。(b) `copyTo` 中途失败可留残缺导出文件，类注释仅声明"覆写"语义未声明"残缺"语义（F2 面）。→ 建议复核维度：C1/C7。

---

## 5. 覆盖与口径声明

- 全单元 33 文件（src/main 2,932 行）均纳入核对：nop-duckdb 6f、nop-graph-core 13f 逐行读毕；nop-graph-api 14f 以资源 API grep（零命中）+ IGraph/Edge 抽查核实为无资源 POJO。
- 重点面 4 项（单写者约束实现/CSV-Parquet-Xlsx 读写面/LeidenDetector executor/Xlsx 读取面与 Closeable 盘点）逐项落账于 §1 配对表与 §3 站点记录；LOCK_MARKER"重试逻辑"经核查 live code 为 fail-fast 无重试设计（§1 末专项节）。
- 计数口径：配对盘点 14 项（R1-R14）；发现 3 条（P1×2、P2×1）；站点核查 1 条（Phase 4 裁定）；CL 线索 3 条。测试代码（nop-duckdb src/test）仅作契约与用法证据，不设发现对象。
- 本初审仅为线索输出，全部结论待 Phase 4 独立复核。

---

# C6 深挖第 2 轮

- **盲审声明（第 2 轮）**: 本轮仅阅读本维度文件（第 1 轮产物）、两份方法论文件（`ai-dev/skills/deep-audit-prompts.md`、`ai-dev/skills/concurrency-resource-audit-prompt.md`）与 live code（HEAD 6934df7ad3，与第 1 轮一致）；未读其他维度文件与 `ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`、`ai-dev/backlog/`。为核实重试/恢复契约，跨模块只读核对了 retry 门控链（`RetryPolicy.isRecoverableException`/`TaskStepHelper.retry`/`RetryTaskStepWrapper`）与模块自声明的 resume/e2e 语义载体（`resume.task.xml`、`e2e-pipeline.task.xml`、`TestDuckDbResume`），均不作为发现对象。只读审计，未修改任何产品文件。
- **扩大面执行情况**: 派发的 5 个扩大面全部核查闭合（见 §R2.0），其中 nop-graph 侧 `CodeCallGraph`/`DbCodeRelationGraph` 经全单元 grep 零命中（该类在本审计单元中不存在），该扩大面落空。

## R2.0 扩大面核查结论（不立案面记录）

| # | 扩大面 | 核查结论 |
|---|--------|----------|
| F-1 | `.duckdb` 主文件生命周期 | 模块从不删除 `.duckdb`：`IDuckDbEngine` javadoc L28（"committed data survives close and reopen"）与 `DuckDbFiles` javadoc L20-21（"task-level working sets only"）已声明其为**持久工作集**而非临时文件，dbPath 由调用方显式指定——无自动清理属**已声明边界**，不立案（区别于 C6-003 的系统临时目录桥接文件）。`.wal`/spill 临时文件为 DuckDB 进程内自管（干净关闭时回收，kill 后 `.wal` 重放），非本单元代码职责。`DriverManager.getConnection` 失败后可能残留 0 字节 `.duckdb`——DuckDB 将空文件视为新库打开，无累积语义，不立案 |
| F-2 | `DuckDbSqlTaskStep.execute` 全链 | 输入校验全部在 `openConnection` 之前（L62-82），无"开了连接再因配置错弃置"路径；statement/result set 全部 try-with-resources 且逆序关闭（`materialize` L131、`countRows` L169-171 声明序 rs 在 st 后，TWRS 自动逆序）；`stepRt.RETURN` 在 TWRS 退出之后（L118）；与 `engine.close()` 并发时 TWRS 二次 close 为 JDBC 幂等 no-op，无双闭危害——本体闭合 ✓（registry 侧后果已在 C6-001/本轮 R2-001 立案） |
| F-3 | `getConnection` 失败路径局部资源 | 失败三态均无未 track 泄漏窗口：(a) 连接未建成（lock/config/native 翻译抛出，L131-156，无对象可泄）；(b) applySettings 失败 → L95/L69 `safeCloseObject` 后重抛，此时 `registerOpen` 尚未执行（注册在 L98），registry 干净；(c) `registerOpen`（L98）与 `track`（L99）之间无可抛语句，synchronized 进入不可中断，无中断窗口——除 C6-002 已立案的 close 并发窗口外无新窗口 |
| F-4 | `DuckDbFiles` 全部 public 方法（7 个）流配对 | `readCsv`/`readParquet` → `readExternal`（tableExists 守卫 L127-131 + CTAS TWRS L138 + `countRows` TWRS）；`readXlsx` → C6-003 已立案；`writeCsv`×2/`writeParquet`×2 → `copyTo` TWRS L154-160。全部方法以调用方拥有的 Connection 为入参、从不关闭之（所有权契约正确）；导出残缺文件语义第 1 轮已裁定不立案（CL-103b），不重复 |
| F-5 | nop-graph 侧 DB 资源面 | `CodeCallGraph`/`DbCodeRelationGraph` 全单元 grep 零命中（不存在）；nop-graph-core 13 文件资源面复核：唯一 `java.io` import 为 `GraphExporter` 的 `StringWriter`（纯内存），无 OS 资源——第 1 轮 R12/R13 结论维持 |

## R2.1 发现条目

### [C6-R2-001] DuckDbEngine.connections 累积已关闭连接的 TrackedConnection 条目：单例 bean 生命周期内单调堆增长（F4），并修正 C6-001 风险表述的边界

- **文件**: `nop-duckdb/src/main/java/io/nop/duckdb/DuckDbEngine.java:59,110-111,213-216`；成因侧 `nop-duckdb/src/main/java/io/nop/duckdb/DuckDbSqlTaskStep.java:85`
- **证据片段**:
  ```java
  // DuckDbEngine.java — 唯一插入点与唯一清空点
  59:    private final List<TrackedConnection> connections = new ArrayList<>();
  ...
  110:            toClose = new ArrayList<>(connections);   // 仅 close() 快照
  111:            connections.clear();                      // 唯一移除点：engine.close()
  ...
  213:    private synchronized Connection track(Connection conn, String normalizedFilePath) {
  214:        connections.add(new TrackedConnection(conn, normalizedFilePath));
  ```
  ```java
  // DuckDbSqlTaskStep.java — 每步执行经 engine 打开、自行 TWRS 关闭（engine 不知情）
  85:        try (Connection conn = openConnection(dbPath)) {
  ```
- **严重程度**: P2
- **并发场景**: 无需竞争——单线程即可触发。载体是单例 `nopDuckDbEngine` bean 的实例字段（app-duckdb.beans.xml L13-15，`destroy-method="close"` 仅容器停机调用），跨任务线程共享累积。
- **触发时序**: S1 step `execute` → `engine.openFile/openMemory` → L214 `connections.add(...)`（条目含活连接）→ S2 step 结束，TWRS 关闭连接（engine 无感知）→ `connections` 中的 TrackedConnection 仍持有**已关闭**的 Connection 对象 → 下一步执行再加一条。条目数 = 引擎存活期内 step 执行总次数（含 openMemory 路径，filePath=null 同样累积），仅在容器停机 `engine.close()` 时清空。关闭后的 DuckDBConnection 对象图不持 native 句柄（close 已递减 native 引用），泄漏为纯堆对象滞留。
- **现状**: `track()` 是连接入队唯一点（L214），`close()` 是唯一移除点（L111）；模块自身唯一生产消费者 `DuckDbSqlTaskStep` 每步执行留下一条指向已关闭连接的死条目，长驻分析服务中单调增长。
- **风险**: F4 族——长时运行下堆内死条目按 step 执行次数线性累积（单条目小，数百字节级，但无上界且无自愈）；同时使 `close()` 的快照与释放循环 O(N) 随历史执行数增长（停机拖慢）；死条目还会在 `engine.close()` 时对同一 filePath 重复 `releaseOpen`（与注册次数恰好抵消，见下述修正，不产生错误释放）。
- **建议**: 与 C6-001 同根同修（close 权与生命周期桥接缺失的第二个失效载体）：在 `openFile/openMemory` 返回带 close 回调的 Connection 包装，包装的 `close()` 内调用 `engine.untracked(this)`（锁内移除对应 TrackedConnection + `filePath != null` 时 `releaseOpen`）；`engine.close()` 快照兜底逻辑不变。一处修改同时闭合 C6-001、C6-R2-001 两个载体。
- **失效面枚举**: `connections` 插入点仅 L214 一处；移除点仅 L111 一处；同族累积载体全单元清点：`connections`（实例，本条）与 `OPEN_FILES`（静态，C6-001）共两处，无第三处；`openMemory`（filePath=null）与 `openFile` 两入口同族同修；测试代码直接裸 DriverManager 的路径不经 track，不受影响。
- **信心水平**: 确定（累积机制单线程可证）；保留规模影响为"很可能"（取决于 step 执行频次 × 引擎存活时长）
- **误报排除**: 非"有界声明存在的缓存"——`connections` 无容量/淘汰配置，其语义是停机释放清单而非缓存；非第 1 轮 C6-001 的重复：C6-001 的失效载体是静态 `OPEN_FILES` 注册表（FileEntry + 用户可见误拒后果），本条载体是实例 `connections` 列表（堆滞留后果），同根因不同失效层，按合并裁定口径属"同族相邻"候选，建议 Phase 4 与 C6-001 合并为单一缺陷族立项、修复验证须同时覆盖两个载体。
- **附带修正（供 Phase 4 复核 C6-001 时采信，不改动前轮原文）**: C6-001 风险节"无自愈路径……step 路径永远凑不齐"应加边界限定——step 经 `engine.openFile` 打开的连接**均被 track**，故 `engine.close()` 时快照内每条记录都会 `releaseOpen` 一次（L113-117），N 次 step 注册恰被 N 次释放，refs 归零、`OPEN_FILES` 条目随之移除。即：refs 泄漏与静态条目滞留是**引擎存活期内**的事实（期间跨 engine 过期指纹误拒成立），引擎关闭后静态注册表自清；真正的无界载体是本条的 `connections` 列表。C6-001 的 P1 定级依据（误拒后果 + 生命周期不对称机制）不受此修正影响。
- **复核状态**: 已复核（C6 维度独立复核 2026-10-22）——保留 P2，并入 C6-001 缺陷族作载体 B（实例 `connections`），其附带修正（C6-001 表述边界限定）已采信；主登记与归并结构见文末「维度复核结论」。

### [C6-R2-002] materialize 的 resultTable 失败/kill 路径孤儿表：与 readExternal 的 table-exists 守卫不对称（F2/F3），持久 dbPath 下重试与恢复必败

- **文件**: `nop-duckdb/src/main/java/io/nop/duckdb/DuckDbSqlTaskStep.java:124-137`（守卫缺失侧）；`nop-duckdb/src/main/java/io/nop/duckdb/DuckDbFiles.java:125-146`（对称守卫参照）
- **证据片段**:
  ```java
  // DuckDbSqlTaskStep.materialize — 无 table-exists 守卫，CTAS 直接落持久库
  126:        String ctas = "CREATE TABLE " + DuckDbFiles.quoteIdentifier(resultTable)
  127:                + " AS (" + sql + ")";
  128:        try (PreparedStatement ps = conn.prepareStatement(ctas)) {
  129:            bindParams(ps, params);
  130:            ps.executeUpdate();
  131:        }
  132:        try (Statement st = conn.createStatement();
  133:             ResultSet rs = st.executeQuery("SELECT count(*) FROM "
  ...
  // DuckDbFiles.readExternal — 同族 CTAS 入口带显式守卫（对照）
  127:        if (tableExists(conn, tableName)) {
  128:            throw ... ERR_DUCKDB_TABLE_EXISTS ... .bizFatal(true);
  ```
- **严重程度**: P1
- **并发场景**: 单任务线程即可触发，无需竞争；触发面是**失败/中断路径**而非并发交错。dbPath（持久 `.duckdb`）+ `resultTable` 组合是多步 pipeline 的声明用法（`e2e-pipeline.task.xml` filter 步 L29-35 `resultTable=t_filtered`；`resume.task.xml` verify 步自身即 resultTable 步）。
- **触发时序**: T1 步执行 `materialize`：CTAS 在 L130 `executeUpdate` 时**已提交**到持久文件（DuckDB 自动提交、事务性）→ T2 在"CTAS 已提交"与"步完成状态落状态存储"之间发生失败——(a) L132-135 的 count 查询 SQLException（连接断/native 崩溃，`TestBrokenNativeEngine` 证明 native 失败是实测形态），或 (b) 进程被 kill（resume 功能声明的正常场景，`TestDuckDbResume` javadoc "simulated kill"）→ T3 重试装饰器或 WI4 恢复路径重新执行**同一未完成步**：CTAS 撞已有表 → SQLException → 包装为 `ERR_DUCKDB_IO_FAILED`（非 bizFatal）→ `RetryPolicy.isRecoverableException`（nop-commons RetryPolicy.java L151-155）判定可恢复 → 重试耗尽后任务实例永久失败；孤儿表 `resultTable` 永久滞留工作集文件，无任何 DROP 路径。
- **现状**: 单元内两个 CTAS 入口守卫不对称：`readExternal` 有 tableExists 前置守卫且 `ERR_DUCKDB_TABLE_EXISTS` bizFatal(true)（fail-fast、不进重试），`materialize` 无任何守卫、失败包装为可重试的 IO_FAILED——"半个成功"（表已建、步未完成）无恢复语义（F2），同族守卫漏配（F3）。
- **风险**: 持久工作集上一次窗口内失败即把该 task 实例/该 dbPath 的后续重跑打成**永久必败**（每次重试撞同一孤儿表），且失败表象（"table already exists" 的 IO_FAILED）掩盖真实首因；孤儿表同时使工作集文件只增不缩（F4 邻接面）。in-memory 路径自愈（每次连接私有库），仅 dbPath != null 受影响。
- **建议**: 对齐 `readExternal` 的守卫语义：(a) `materialize` 前置 `tableExists` 检查，命中时抛 `ERR_DUCKDB_TABLE_EXISTS` + `bizFatal(true)`（与 ingest 同口径：要求调用方显式清理，重试不blindly重放）；或 (b) 步开头 `DROP TABLE IF EXISTS`（声明"resultTable 每次执行重建"语义，需写入类 javadoc 契约）；方案 (a) 改动最小且与模块既有 fail-fast 风格一致。
- **失效面枚举**: 单元内 CREATE TABLE 写点共 2 处：`DuckDbFiles.readExternal` L137（有守卫+bizFatal，豁免）、`DuckDbSqlTaskStep.materialize` L126-130（无守卫，本条）；用户在 `sql` 入参中自带的 CREATE TABLE 属调用方 SQL 契约，不属模块守卫面；重试入口唯一（`<retry>` 装饰器 → `RetryTaskStepWrapper`），恢复入口唯一（任务状态存储 → 步级重放），二者对 materialize 的暴露面相同。
- **信心水平**: 确定（守卫不对称与可重试判定均从代码直接可证；"count 在 CTAS 提交后失败"的具体失败形态为很可能）
- **误报排除**: 非 C5/C7 越界——立案点不是表数据量而是**失败路径上已提交产物无清理/无守卫语义**，属 C6 步骤 2"异常路径逐一核对"与本领域 P1 档"'半个成功'无恢复语义"的明文命中；非误报校准条目（无有界声明、无生成文件介入）；resume 场景由模块自声明（TestDuckDbResume + resume.task.xml 的 verify 步即 resultTable 步），非审计方虚构的用例。若 Phase 4 认为窗口概率权重低，降级下限为 P2，但守卫不对称（F3）本身独立成立。
- **复核状态**: 已复核（C6 维度独立复核 2026-10-22）——保留 P1；守卫对照（materialize L126-130 无守卫 vs readExternal L127-131 有守卫+bizFatal）、RetryPolicy.isRecoverableException 分类链（RetryPolicy.java L148-155：NopException 可恢复当且仅当非 bizFatal → RetryTaskStepWrapper L28 → TaskStepHelper.retry L254）与持久 dbPath 前提（resume.task.xml verify 步即 resultTable+dbPath）三要素逐一重核成立；降 P2 下限不采信（理由见文末）。

## R2.2 清扫表终止信号

- 本轮 5 个扩大面全部核查闭合（§R2.0）：DuckDB 文件生命周期全景（.duckdb 已声明持久边界 / .wal、spill 属 DuckDB 内部 / .csv、.parquet 为调用方所有的档案产物 / 唯一中间临时文件已由 C6-003 覆盖）、`DuckDbSqlTaskStep.execute` 全链（本体闭合）、`getConnection` 失败路径（除 C6-002 外无新窗口）、`DuckDbFiles` 全部 7 个 public 方法（配对完整，除 C6-003 外）、nop-graph DB 资源面（对象不存在 + StringWriter 复核）。
- 本单元 33 文件的 C6 资源面历经 2 轮：第 1 轮 14 项配对盘点 + 3 条发现，第 2 轮扩大面 + 2 条新发现（其中 R2-001 为 C6-001 同族相邻载体，建议合并裁定）。**未核查面已清空，建议深挖终止**，交 Phase 4 独立复核（含 §3 LeidenDetector 三态裁定与 R2-001 附带的 C6-001 表述修正）。

---

# C6 深挖第 3 轮

- **盲审声明（第 3 轮）**: 本轮仅阅读本维度文件（前两轮产物）、两份方法论文件（`ai-dev/skills/deep-audit-prompts.md`、`ai-dev/skills/concurrency-resource-audit-prompt.md`）与 live code（HEAD 6934df7ad3，与前两轮一致）；未读其他维度文件与 `ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`、`ai-dev/backlog/`。为核实重试判定链与 resume 语义，跨模块只读核对了 `RetryPolicy.isRecoverableException`（nop-kernel/nop-commons）、`RetryTaskStepWrapper`/`TaskStepHelper.retry`（nop-task-core）、owner doc `docs-for-ai/03-modules/nop-duckdb.md` 与样例 task XML；均不作为发现对象（owner doc 仅用于核对契约声明缺口的存在性）。只读审计，未修改任何产品文件。
- **本轮重点执行情况**: ① 写持久文件 SQL 面全量清点闭合（§R3.1，模块自持写点共 3 处 + 调用方 SQL 一面）；② `isRecoverableException` 消费点对全部写面的残留-重放暴露逐一裁定（§R3.2）；③ 新发现 1 条（C6-R3-001，P3，声明面缺口）。**两族放大核查闭合，本轮终止**（§R3.4 终局清扫结论）。

## R3.1 写持久文件的 SQL 面——逐面核查清单（C6-R2-002 同族放大）

计数口径（可复现）：`grep -rinE "create (table|temp|schema)|drop table|insert into|alter table|export database|attach|copy .* to|checkpoint" nop-duckdb/src/main nop-graph/nop-graph-core/src/main nop-graph/nop-graph-api/src/main` 命中 4 行 = 3 处代码写点（DuckDbFiles.java:137、:153、DuckDbSqlTaskStep.java:126）+ 1 处 javadoc 提及（DuckDbFiles.java:29）。全单元 src/main 无 EXPORT DATABASE、无 ATTACH、无 DROP、无 IF NOT EXISTS、无先删后建；nop-graph 侧零命中（本轮已按正确模块路径 `nop-graph/nop-graph-*` 复查，GraphExporter 的 export 为 StringWriter 纯内存导出，第 1/2 轮 R13/R14 结论维持）。

| # | 写面 | 位置 | SQL 形态 | 幂等守卫 | 残留-重放裁定 |
|---|------|------|----------|----------|----------------|
| W1 | ingest CTAS | `DuckDbFiles.readExternal` L127-137（readCsv/readParquet 直接入口，readXlsx 经 L82 readCsv 汇入；任务面 `DuckDbSqlTaskStep` L88/L91） | `CREATE TABLE t AS SELECT * FROM reader(...)` | **有**：前置 `tableExists`（L127）+ `ERR_DUCKDB_TABLE_EXISTS` `bizFatal(true)`（L128-130） | 重放遇残留收敛到**设计内 fail-fast**（§R3.2 裁定 (b)），不立案 |
| W2 | resultTable CTAS | `DuckDbSqlTaskStep.materialize` L126-130 | `CREATE TABLE t AS (sql)` | **无**（无 tableExists、无 IF NOT EXISTS、无先删后建） | 已立案 **C6-R2-002**（P1）；本轮确认其为唯一无守卫模块自持写点，未发现第三个失效载体 |
| W3 | COPY 导出 | `DuckDbFiles.copyTo` L152-160（writeCsv×2/writeParquet×2 汇入；任务面 L98/L100） | `COPY (sql) TO 'file' (FORMAT ...)` | 文件级幂等：**覆盖写**（javadoc L31-32 明文声明 archival 覆写语义），无表守卫需求 | 重放**自愈**：COPY 重执行整体覆盖目标文件，中途失败残留的残缺文件被下次重放覆盖；残缺文件本身语义第 1 轮已裁定（CL-103b，不立案） |
| W4 | 引擎 SET | `DuckDbEngine.execSet` L183-193（applySettings L168-181） | `SET key=value`（含 `temp_directory`） | bizFatal（INVALID_CONFIG，L188/L197） | 非持久写（仅会话配置；`temp_directory` 只指定 spill 位置，spill 文件生命周期属 DuckDB 进程内自管，第 2 轮 F-1 裁定维持） |
| W5 | 调用方 SQL | `DuckDbSqlTaskStep.executeSql` L143-158（execute L107 直通） | 任意用户 SQL（可含 CREATE TABLE/ATTACH/INSERT 等） | **模块级守卫原理性不可达**（SQL 文本即调用方契约） | 同族残留-重放暴露成立但属声明面缺口 → **C6-R3-001（P3）** |

## R3.2 失败-恢复语义——isRecoverableException 消费点对写面的残留-重放裁定

**判定链（跨模块只读核实）**：`RetryPolicy.isRecoverableException`（`nop-kernel/nop-commons/src/main/java/io/nop/commons/util/retry/RetryPolicy.java` L148-155）：NopException → 可恢复当且仅当非 bizFatal；非 NopException → 非 Error 即可恢复。消费点：`RetryTaskStepWrapper.execute`（L28）→ `TaskStepHelper.retry`（L254，`retryPolicy.getRetryDelay(state.exception(), retryAttempt, stepRt)`，delay<0 即 fail-fast）——`<retry>` 装饰器路径；WI4 恢复路径按持久化的步级状态（含 exception/retryAttempt，resume 时 continuation-skip 仅跳过终态行，未完成步重执行）走同一分类。本单元可恢复异常面 = `ERR_DUCKDB_IO_FAILED` 的全部 6 个包装点（DuckDbFiles L77/L141/L162/L175/L188 + DuckDbSqlTaskStep L115，均非 bizFatal）；其余全 bizFatal（TABLE_EXISTS、FILE_NOT_FOUND、INVALID_STEP_INPUT、INVALID_CONFIG×2、FILE_LOCKED、CONFIG_CONFLICT×2、NATIVE_LOAD_FAILED、ENGINE_CLOSED）→ fail-fast 不重放。

对 §R3.1 各写面的「重试遇残留物必败」逐一裁定：

| 写面 | 重放遇残留行为 | 裁定 |
|------|----------------|------|
| W2 materialize | 每次重放撞孤儿表 → IO_FAILED（可恢复）→ 重试耗尽永久失败，错误表象掩盖首因 | **C6-R2-002 已立案**（P1），唯一模块自持必败站点 |
| W1 ingest | 重放时 `tableExists` 命中 → TABLE_EXISTS bizFatal → 首次重放即设计内 fail-fast（错误文案自带修复指引 "drop it first or use another name"，NopDuckDbErrors L56-60） | **已声明边界**（javadoc L31 "Read side refuses to overwrite an existing table"），非缺陷。附带记录：守卫为 check-then-act，并发同表 ingest 存在 TOCTOU 窗口，但收敛结果与串行一致（后到者重放命中设计内 bizFatal），无独立后果 |
| W3 COPY 导出 | 重放整体覆盖目标文件，自愈 | 非必败站点 |
| W4 SET | 无持久残留 | 非必败站点 |
| W5 调用方 SQL | 非幂等 DDL 重放撞残留 → IO_FAILED（可恢复，无修复指引）→ 盲重放永久失败 | 模块守卫不可达；**契约声明缺口 → C6-R3-001** |

## R3.3 发现条目

### [C6-R3-001] resume/重试重放契约未声明 caller-SQL 幂等性要求：模块自带样例即用非幂等 DDL，kill 落于步内提交窗口时按官方样例组织的任务永久必败且无文档提示

- **文件**: `nop-duckdb/src/main/java/io/nop/duckdb/DuckDbSqlTaskStep.java:36-37,143-158`（契约声明缺口侧）；样例证据 `nop-duckdb/src/test/resources/_vfs/nop/duckdb/task/resume.task.xml:5`、`nop-duckdb/src/test/resources/_vfs/nop/duckdb/task/e2e-pipeline.task.xml:53,60`；owner doc `docs-for-ai/03-modules/nop-duckdb.md:75`（声明 resume 无幂等例外）
- **证据片段**:
  ```xml
  <!-- resume.task.xml:5 — 模块自带 resume 样例的 setup 步使用非幂等 DDL（指向 .duckdb 工作集） -->
  <input name="sql" value="CREATE TABLE res AS SELECT 41 AS v UNION ALL SELECT 1 AS v"/>
  <!-- e2e-pipeline.task.xml:60 — 同样模式（写入 ATTACH 的 sqlite） -->
  <input name="sql" value="CREATE TABLE sq.result AS SELECT n FROM t_agg"/>
  ```
  ```java
  // DuckDbSqlTaskStep.java:36-37 — javadoc 只声明 dbPath 共享，未声明重放幂等要求
  36:  * <li>{@code dbPath} (optional) - use a persistent .duckdb file instead of a private
  37:  * in-memory database. Multi-step pipelines must pass the same dbPath to share tables.</li>
  ```
- **严重程度**: P3
- **并发场景**: 单任务线程即可触发，无需竞争；触发面是「调用方 SQL 已提交」与「步完成状态落状态存储」之间的 kill/失败窗口 + WI4 恢复重放或 `<retry>` 重试（入口链同 §R3.2，与 C6-R2-002 的暴露机制同构）。
- **触发时序**: T1 步执行 `executeSql`：调用方 SQL `CREATE TABLE res ...` 已提交到持久 dbPath → T2 进程被 kill（resume 声明场景）或后续阶段 SQLException → T3 resume 重放该未完成步：同一 DDL 撞已存在表 → SQLException → `ERR_DUCKDB_IO_FAILED`（非 bizFatal，可恢复，错误文案无修复指引）→ 每次重放撞同一残留，重试耗尽任务永久失败。与 C6-R2-002 同构，但残留由调用方 SQL 创建，模块级 `tableExists` 前置守卫在 `executeSql` 直通面上原理性不可达——可修的只剩声明面。
- **现状**: 步 javadoc（L19-47）与 owner doc L75 声明了 resume 语义（"duckdb 步骤参与的任务……续跑；`.duckdb` 工作集文件跨'重启'数据一致"），均未声明「持久 dbPath 上的 caller SQL 必须对重放幂等（IF NOT EXISTS / 先删后写 / 可重入）」；模块自带两个样例（resume.task.xml setup、e2e-pipeline sqlite_write）正是非幂等写法——照样例组织管道的用户在声明场景（resume）下踩中窗口即永久必败。
- **风险**: 按官方样例写管道的用户在步内提交窗口遭遇 kill 后任务实例永久失败，且失败表象（"table already exists" 的 IO_FAILED）不含修复指引（区别于 ingest 的 TABLE_EXISTS bizFatal 文案自带 "drop it first" 指引）；属声明面缺口而非守卫缺陷（守卫原理性不可达），故 P3。
- **建议**: 纯声明面修复，无需运行时变更：(a) `DuckDbSqlTaskStep` javadoc dbPath 条目追加一句契约："caller SQL against a persistent dbPath must be replay-safe (the step may be re-executed after a mid-step failure); use IF NOT EXISTS / drop-then-create for DDL"；(b) owner doc `nop-duckdb.md` L75 resume 条目补同款例外说明；(c) 两个样例 SQL 改幂等形态（`CREATE TABLE IF NOT EXISTS`）或注明演示简化。
- **失效面枚举**: 调用方 SQL 直通点仅 `executeSql` 一处（L143；export/materialize 分支不经此面）；模块样例中非幂等 caller SQL 共 3 处：resume.task.xml:5（CREATE TABLE res → .duckdb 工作集，重放必撞）、e2e-pipeline.task.xml:53（ATTACH——重放语义未实测：进程内重放可能遇重复 attach 错误、跨进程重放 attach 状态消失，附注性记录不单独立案）、e2e-pipeline.task.xml:60（CREATE TABLE sq.result → attach 的 sqlite，重放必撞）；模块自持写面 W1-W4 已在 §R3.1 全部裁定，无第 4 个守卫缺失站点。
- **信心水平**: 确定（机制与 C6-R2-002 同构可证；"声明缺口"经 javadoc + owner doc 双面核实为真）
- **误报排除**: 非"把用户契约问题算到模块头上"——立案点不是 caller SQL 自身会失败（那是调用方责任），而是**模块声明的 resume 特性 + 模块自带样例**引导出必败用法且无任何声明提示，属公开契约面缺口（共享前缀 P1 档"文档/公开面问题"的下限形态）；非 C6-R2-002 的重复——R2-002 是模块自持 materialize 面的守卫缺失（可修守卫），本条是调用方面上的声明缺口（只修文档/样例），失效层不同。若 Phase 4 认为 resume 属主路径声明用法，可升 P2。
- **复核状态**: 已复核（C6 维度独立复核 2026-10-22）——保留 P3；resume.task.xml:5 / e2e-pipeline.task.xml:53,60 非幂等样例与 owner doc L75 声明缺口重核成立；「可升 P2」不采信（声明面缺口 + 窗口条件性，P3 恰当）。

## R3.4 终局清扫结论（两族闭合，本轮终止）

1. **C6-R2-002 同族放大闭合**：模块自持写持久文件 SQL 面全单元共 3 处（W1 ingest CTAS 有设计内 fail-fast 守卫 / W2 materialize 无守卫已立案 C6-R2-002 / W3 COPY 覆盖写自愈）+ W4 SET 非持久写 + W5 调用方 SQL（守卫原理性不可达，声明缺口 C6-R3-001）。无第 4 个守卫缺失站点，无 EXPORT/ATTACH/DROP/IF NOT EXISTS 残留面。
2. **失败-恢复语义闭合**：`isRecoverableException` 全部消费点（`<retry>` 装饰器 + WI4 步级重放，同一分类链）对本单元 6 个 `ERR_DUCKDB_IO_FAILED` 可恢复包装点的残留-重放暴露逐一裁定：materialize（已立案 P1）、ingest（收敛到设计内 bizFatal，已声明边界）、COPY 导出（自愈）、xlsx 桥接临时文件（C6-003 已立案，重放重建新临时文件不撞残留）、SET（无残留）、caller SQL（C6-R3-001 声明面）。无未登记必败站点。
3. **nop-graph 侧终审维持**：以正确模块路径全量复查写面关键字零命中，纯内存计算无持久资源面；前两轮 R12-R14 结论不变。
4. **终止信号**：本单元 33 文件历经 3 轮（第 1 轮 14 项配对盘点 + 3 发现；第 2 轮 5 扩大面 + 2 发现；第 3 轮同族放大 + 恢复语义穷举 + 1 发现）。累计发现 6 条：C6-001（P1）、C6-002（P1）、C6-R2-002（P1）、C6-R2-001（P2）、C6-003（P2）、C6-R3-001（P3），外加 §3 LeidenDetector 站点记录（Phase 4 三态裁定）与 CL-101/102/103 三条跨维度线索。**未核查面已清空，C6 深挖于本轮终止**，移交 Phase 4 独立复核。

---

# C6 维度复核结论（独立复核子代理，2026-10-22）

- **复核基线**: HEAD 6934df7ad3 与初审一致；实测 `git status --porcelain` 对 `nop-duckdb/src/main`、`nop-graph/` 为 0 改动——6 条发现现状均为「新发现，未修复」。
- **复核方法**: 逐条重读源码（DuckDbEngine/DuckDbSqlTaskStep/DuckDbFiles/IDuckDbEngine/LeidenDetector 全文 + app-duckdb.beans.xml + resume/e2e-pipeline/filelocked-retry task XML + owner doc nop-duckdb.md），重放计数 grep（写持久 SQL 面扫描复现 R3.1 计数 3 写点+1 javadoc、nop-graph 资源 API grep 零命中复现、openFile/openMemory 全仓 src/main 消费方枚举=仅 DuckDbSqlTaskStep L85），并对照 C1 复核结论与 CS-sweep 做同站点三方核实；按派单要求对照 WI 2.1 移交原文（merge-adjudication.md L384）给出 C6-CL-031 三态裁定。允许读 cra-index 与历史审计；只读复核 + 回填复核状态行，未修改其他维度文件/cra-index/产品代码。

## 逐条判定

### [C6-001] — 保留 P1（采纳 R2-001 表述修正为边界限定）
- **证据核实**: registerOpen 唯一注册点（openFile :98）、releaseOpen 唯一调用点（close :116，遍历 :113-117）、OPEN_FILES 静态 :48、过期指纹守卫 :82-89（bizFatal :84-88）全部与源码一致；成因侧 DuckDbSqlTaskStep :85 TWRS 自关连接核实；全仓 src/main grep 证实 openFile/openMemory 生产消费方仅此一处（测试 TWRS 裸关为同族用法形态，TestDuckDbTaskStep :102、TestDuckDbFiles :279/:285 等——直接关连接不是异端旁路而是模块自身主流用法，佐证缺陷面在 engine 簿记挂接点而非调用方误用）。
- **R2 修正采信**: 成立且必须采信——step 经 engine 打开的连接均被 track（:99→:214），engine.close() 时 N 次注册恰被 N 次 releaseOpen 抵消，refs 归零、条目移除。原文「永久残留」「无自愈路径……永远凑不齐」应限定为「**engine 存活期内**滞留；释放挂 engine.close()」。
- **修正后 P1 是否维持——维持**。理由：(1) 单例 bean（app-duckdb.beans.xml `destroy-method="close"` 仅容器停机调用）下 engine 存活期=应用存活期，长驻分析服务的运营口径内「存活期不释放」与「永不释放」无差别，释放路径是事实不可达的兜底；(2) 后果 (b) 过期指纹误拒在**整个服务存活期**成立，且命中的恰是注册表自述职责（:44-47「two engine instances with different configs」）所保护的场景——在该被支持场景内 100% 假阳性，用户可见错误码、无自愈；(3) 后果 (a) F4 在「每任务唯一 dbPath」的声明用法（DuckDbFiles javadoc L21 "task-level working sets" + 测试 createTempDirectory 惯例）下于存活期内单调无界；(4) 主路径、单线程、每次执行必触，无需任何竞态——与本单元 P1 校准（主路径/必然档）吻合。IDuckDbEngine :16 契约（"close() releases every connection"）未禁止调用方关连接，故 step 用法合规，责任在 engine。修正仅收窄「JVM 存活期→engine 存活期」边界，判级依据不受影响；与 C1 复核对 C1-R2-001 的 P1 对齐一致。

### [C6-002] — 保留 P1；确认主登记归属；「可降 P2」注记不采信为判级变更
- **证据核实**: checkClosed 仅入口两处（:64/:77）、close 置位+快照+清空（:105-112）、幂等早退（:106-108）、track（:213-215，synchronized(this) 与 close 同锁但**不复查 closed**）全部与源码一致；交错可从代码直接构造，窗口宽度=一次 JDBC connect（首用含 native 加载，非纳秒级）。
- **主登记归属**: 确认归 C6-002。C1 复核已裁定 C1-002「保留（真缺陷），并入 C6-002 主登记（同站点，C1 无独立证据增量）」；CS-CL-201 为第三同站点确认。后果拆分两维口径一致：native+文件锁泄漏落在依赖接口契约（IDuckDbEngine :16/:33-36 明文 "close() releases every connection opened by this engine instance"——被竞态连接正属 "opened by this engine"，**契约明文违约**）的调用方；step 消费者 TWRS 自关故 native 不泄漏、残余 refs 并入 C6-001。
- **「可降 P2」裁定——不采信**：后果 severity（单写者文件锁滞留→该 .duckdb 对所有其他进程不可写，模块核心约束面 + 契约明文违约 + 无自愈）压过运营频率；初审自述的本领域校准「罕见但可具体化的竞态（有明确触发链）」立案 P1 应保持一致适用。注记保留为 Phase 6 备选：若合并裁定显式采用「运营频率权重」口径，则 P2 下限成立，但 C6-002 与 C1-002 必须同步同判，不得一头 P1 一头 P2。

### [C6-003] — 保留 P2
- 证据逐行重核成立：L72 建临时文件、L73 deleteOnExit、L74-75 转换、L76-80 catch 重抛**无删除**（第一段 try 无 finally）、L81-86 finally 仅覆盖 readCsv 段。失败路径残留镜像整个 xlsx 的临时 CSV 至 JVM 退出；deleteOnExit 每调用追加（含成功路径）核实。P2 恰当（JVM 退出兜底、失败路径触发）。与 CS-003 的重叠面（deleteOnExit 注册表增长）建议 Phase 6 并入本条作子面，CS-003 不独立计数。

### [C6-R2-001] — 保留 P2，并入 C6-001 缺陷族（载体 B）
- 证据核实：`connections` 唯一插入点 :214、唯一移除点 :111，step 每执行留一条持已关闭连接的死条目，纯堆滞留（已 close 的 DuckDBConnection 不持 native）。P2 恰当。其附带修正（C6-001 边界限定）已采信（见 C6-001 判定）。同根（close 权与簿记 release 权无桥接）异载体（静态 OPEN_FILES / 实例 connections），单修双验。

### [C6-R2-002] — 保留 P1（降 P2 下限不采信）
- **三要素逐一重核**：
  1. **守卫对照**: materialize :126-130 CTAS 无任何守卫 vs readExternal :127-131 前置 tableExists + `ERR_DUCKDB_TABLE_EXISTS` bizFatal(true)——同单元两 CTAS 入口守卫不对称实锤（R3.1 写面扫描重放：全单元 CREATE TABLE 写点恰 3 处，无 IF NOT EXISTS/DROP，本条为唯一无守卫模块自持写点）。
  2. **分类链**: DuckDbSqlTaskStep :114-116 将 SQLException 包装为 `ERR_DUCKDB_IO_FAILED` **无 bizFatal** → RetryPolicy.isRecoverableException（nop-commons RetryPolicy.java :148-155：NopException 可恢复当且仅当非 bizFatal）→ 可恢复 → RetryTaskStepWrapper :28 → TaskStepHelper.retry :254 `getRetryDelay`（≥0 即重放）→ 重放撞孤儿表 → 同码再败 → 耗尽后任务实例永久失败。链路逐环核实成立。
  3. **持久 dbPath 前提**: resume.task.xml verify 步（:11-17）自身即 resultTable+dbPath 组合、e2e-pipeline sqlite_write（:60）同型；CTAS 经 DuckDB 自动提交于 executeUpdate（:129），「已提交」与「步完成状态落状态存储」之间窗口成立；模块全 src/main 无 DROP 路径，孤儿表无清理出口。
- **判级**: P1 维持——这是模块自声明 resume 特性上的「半个成功」无恢复语义，窗口一旦命中即**确定性永久必败**（非概率性损害，而是必达终态），且错误表象（table already exists 的 IO_FAILED）掩盖首因；in-memory 自愈恰说明受影响面=声明用法面（多步管线共享 dbPath）。初审的「窗口概率低可降 P2」不采信：判定对象是失败路径的终态性质而非窗口宽度。

### [C6-R3-001] — 保留 P3
- 证据核实：resume.task.xml:5、e2e-pipeline.task.xml:53,60 非幂等 caller SQL 样例、DuckDbSqlTaskStep javadoc :36-37 仅声明 dbPath 共享、owner doc nop-duckdb.md L75 声明 resume 语义无幂等例外——声明缺口双面核实为真；executeSql 直通面守卫原理性不可达成立。「可升 P2」不采信：纯声明面、窗口条件性，P3 恰当。

## 同族归并建议（归 Phase 6 执行）

| 族 | 成员 | 主登记 | 结构建议 |
|---|------|--------|----------|
| 账本泄漏族（连接 close 权与簿记 release 权无桥接） | C6-001（P1，静态 OPEN_FILES）+ C6-R2-001（P2，实例 connections）+ CS-002（P2，C5/C7 角度）+ C1-R2-001（P1，已按 C1 复核结论并入本族） | **C6-001**（升格为双载体合并条目，P1） | C6-R2-001 作载体 B 并入、不再独立计数；CS-002 关闭为别名（其 C5/C7 后果表述与「P1 档提请复核」随主登记收敛，其修复建议「track 返回可关代理」与 C6-001 建议(a)同构，采其一）；C1-R2-001 维持 C1 侧「跨维度重述」标注；修复验证必须双载体同测（OPEN_FILES refs 归零 + connections 清空）+ 跨 engine 复用回归（TestDuckDbEngine :151 意图契约） |
| 竞态族（open/close 生命周期竞态） | C6-002（P1）+ C1-002（P2）+ CS-CL-201（第三确认） | **C6-002**（P1） | C1-002 关闭为并入别名（C1 侧无证据增量，其 refs 残留后果本就指向 C6-001）；判级统一 P1；「运营频率口径降 P2」仅可作为族级整体裁定且须两条同步 |
| deleteOnExit 族 | C6-003（P2，主缺陷=失败路径不删）+ CS-003（P3，deleteOnExit 注册表增长） | **C6-003**（P2） | CS-003 并入作 deleteOnExit 子面，不独立计数 |
| CTAS 必败族 | C6-R2-002（P1，模块自持面）+ C6-R3-001（P3，caller SQL 声明面） | 各自独立保留（失效层不同：可修守卫 vs 只修声明） | 同批修复（同一守卫语义对齐 + 同一套样例/javadoc 修订），Phase 6 建立 cross-ref 即可，不合并 |

## C6-CL-031 三态裁定（对照 WI 2.1 移交原文 merge-adjudication.md L384「C6-CL-031｜C6｜确认｜移交 nop-graph 轮次（LeidenDetector 非 daemon + 无 awaitTermination）」）

- **裁定：边界变化（站点事实仍成立，缺陷定性收窄并被 CS-001 吸收；非「已修复」，非独立「仍成立」）。**
- **站点事实对照（LeidenDetector.java:196-204 live code）**: ①非 daemon——:197 `Executors.newSingleThreadExecutor()` 用默认 ThreadFactory，线程非 daemon，与 2.1 移交时一致，未修复；②无 awaitTermination——:201-203 finally 仅 `shutdownNow()`，无 awaitTermination，属实。**两项事实均仍成立**。
- **边界变化**: 2.1 移交将「无 awaitTermination」与「非 daemon」并列为 executor 生命周期缺陷线索；本轮核实 executor 句柄配对在成功/算法异常/超时三路径均闭合（finally :202 必执行，见 §3 站点记录），**无 awaitTermination 不构成独立缺陷**——被提交任务（CWTS Leiden 纯 CPU 循环）无中断检查点，`shutdownNow` 的中断已然无效，补 awaitTermination 只会让调用方（请求线程）阻塞至 ghost 自然结束，属负修复。真实缺陷面是超时 ghost 线程 × 非 daemon 阻塞 JVM 退出 × CPU/内存驻留，已由本单元 **CS-001（P1）** 主登记，调用面放大为 CS-R2-001。
- **处置建议**: C6-CL-031 按「确认-边界变化-别名并入 CS-001」关闭，不独立立案；修复随 CS-001（daemon 命名线程工厂 + 可中断/有界执行路径），C6 侧 §3 站点记录同步关闭。
- **cra-index 2.1 台账回填注记文本（建议原文，供 Phase 6 抄录）**:
  > C6-CL-031（2026-10-22 WI 5.6 Phase 4 三态裁定：**边界变化 → 并入 CS-001**）：LeidenDetector.java:196-204 非 daemon + 无 awaitTermination 站点事实仍成立（代码与移交时一致，未修复）；但 executor 生命周期配对闭合（finally shutdownNow 于成功/异常/超时三路径均执行），无 awaitTermination 非独立缺陷——CWTS Leiden 任务无中断检查点，awaitTermination 仅延长调用方阻塞、不可修复幽灵线程。缺陷主面已由 WI 5.6 单元 CS-001（P1，超时幽灵线程）吸收（调用面放大 CS-R2-001）；修复随 CS-001（daemon 线程工厂 + 可中断执行），C6-CL-031 关闭为 CS-001 别名。

## CL 处置

- **CL-101: 确认**。LeidenDetector :136-140 `catch (Exception)` 吞 TimeoutException/RuntimeException 降级 LabelPropagation（重读核实），降级算法无超时参数——信号成立；涉 F9/C7，归 C3/C7 维度处置（ghost 线程本体已由 CS-001 承载，本条是吞异常+超时保护失效的独立 facet）。
- **CL-102: 确认**。DuckDbSqlTaskStep :85/:139-141 每步开合连接（attach+3 SET 固定开销）核实；C6 配对盘点已证非泄漏（R1 本体 ✓）；归 C9（含与 `io.nop.commons.pool.IPool` 适配性）。
- **CL-103: 确认**。(a) :79 归一化路径查 OPEN_FILES vs :91 原始 filePath 拼 JDBC URL——别名绕过指纹检查核实，与 C1-001（同文件注册 TOCTOU，C1 已独立立案 P3）为不同缺陷面，不冲突；归 C1/C7。(b) copyTo 残缺文件语义未声明，维持不立案附注，归 C7/F2 面。

## 批次覆盖核验（三源码根）

| 源码根 | 实测 | 与文件头口径 | 核查痕迹 |
|---|---|---|---|
| nop-duckdb | 6f/837 | 一致 | DuckDbEngine/DuckDbSqlTaskStep/DuckDbFiles/IDuckDbEngine 全文逐行重读；beans.xml（单例+destroy-method）、resume/e2e-pipeline/filelocked-retry XML、RetryPolicy/RetryTaskStepWrapper/TaskStepHelper 跨模块链逐环核实 |
| nop-graph/nop-graph-core | 13f/1,482 | 一致 | LeidenDetector :196-204 与 §3 站点记录逐字吻合；资源 API grep（Closeable/close/Stream/File/Files.）全根零命中复现（R13 结论维持） |
| nop-graph/nop-graph-api | 14f/613 | 一致 | 资源 API grep 零命中复现（R14 口径）；27f/2,095 与派单口径吻合（13+14 f、1,482+613 行） |

合计 33f/2,932 逐字吻合；HEAD=6934df7ad3 与基线一致；审计路径工作树 0 改动。R3.1 写面计数重放精确复现（3 写点 + 1 javadoc）。**批次覆盖通过。**

## 最终保留项

| 项 | 判级 | 判定 | 处置 | 现状 |
|---|---|---|---|---|
| C6-001 | P1 | 保留（采纳 R2-001 边界限定，判级维持） | 账本泄漏族**主登记**（吸收 C6-R2-001/CS-002/C1-R2-001） | 新发现，未修复 |
| C6-002 | P1 | 保留（「可降 P2」注记不采信为判级） | 竞态族**主登记**（吸收 C1-002/CS-CL-201） | 新发现，未修复 |
| C6-R2-002 | P1 | 保留（降 P2 下限不采信） | 本维度独立登记，与 C6-R3-001 同批修复 + cross-ref | 新发现，未修复 |
| C6-R2-001 | P2 | 保留（并入缺陷族） | 并入 C6-001 作载体 B，不独立计数 | 新发现，未修复 |
| C6-003 | P2 | 保留 | deleteOnExit 族主登记（吸收 CS-003） | 新发现，未修复 |
| C6-R3-001 | P3 | 保留（「可升 P2」不采信） | 本维度独立登记，与 C6-R2-002 同批修复 | 新发现，未修复 |
| §3 LeidenDetector 站点记录 | — | 维持（executor 配对 ✓，泄漏争议面=ghost 线程） | 归 CS-001 主条目；C6-CL-031 三态裁定=边界变化并入 CS-001（见上） | 未修复（随 CS-001） |
| CL-101/102/103 | — | 全部确认 | 101→C3/C7；102→C9；103→C1/C7（与 C1-001 不同缺陷面，不冲突） | 线索移交 |

**净效果**: 本维度 6 条发现全部保留、无降级、无驳回（P1×3 维持、P2×2 维持、P3×1 维持）；两处初审自带的升降档动议（C6-002 可降 P2、C6-R2-002 可降 P2、C6-R3-001 可升 P2）均裁定不采信并给出理由；R2-001 对 C6-001 的表述修正采信为边界限定。归并结构：账本泄漏族主登记=C6-001（1 吸 3）、竞态族主登记=C6-002（1 吸 2）、deleteOnExit 族主登记=C6-003（1 吸 1）、CTAS 必败两列独立保留 + cross-ref。C6-CL-031 三态裁定=边界变化，并入 CS-001，附 cra-index 2.1 回填注记文本。批次覆盖核验通过。
