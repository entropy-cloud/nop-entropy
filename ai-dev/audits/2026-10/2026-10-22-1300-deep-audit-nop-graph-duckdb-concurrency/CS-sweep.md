# CS-sweep：C2/C3/C4/C5/C7/C8/C9 七形态 overlay 抽查批

- **审计单元**：nop-graph/nop-graph-api（14f，plan-first 只读）+ nop-graph/nop-graph-core（13f）+ nop-duckdb（6f），共 33 个主代码文件 / 2,932 行（与基线 `_tmp/cra-baseline-nop-graph-duckdb.txt` 口径一致，@ 6934df7ad3）
- **形态集合**：C2 锁与同步 / C3 异步链 / C4 线程池 / C5 内存有界 / C7 长时运行 / C8 背压 / C9 热路径
- **批次**：抽查批（overlay 轻量核查：每形态按 grep 锚点 + 逐文件速读，命中即深核）
- **盲审声明**：本报告为初审盲审产出。执行期间未读取 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`、`ai-dev/backlog/` 下任何历史记录；现状标注（新发现/已知未修复）留待复核阶段。已知站点按派单口径处理：LeidenDetector executor（本批 C3/C4 角度核）、DuckDbEngine open/close 竞态（C6/C1 维度处理中，本批仅 C2 角度确认，见 CL-201，不立案）。
- **方法论文本**：`ai-dev/skills/deep-audit-prompts.md` 共享提示词前缀（L298-374）+ `ai-dev/skills/concurrency-resource-audit-prompt.md` 各维度正文（全程遵守：具体触发时序、失效面枚举、误报排除、发现条目 ≥9 行）。

---

## 一、七形态核查表

| 形态 | 核查命令（grep 锚点，main 33f） | 命中数 | 判定 |
|---|---|---|---|
| C2 | `synchronized\|ReentrantLock\|ReadWriteLock\|Collections.synchronized\|computeIfAbsent\|.compute(` | 15（InMemoryGraph 11 + DuckDbEngine 4） | InMemoryGraph：全部 getter 锁内快照拷贝、迭代不逃逸、computeIfAbsent λ 仅分配无重入——**已核查，无发现**。DuckDbEngine：`OPEN_FILES.compute` 原子；但 `checkClosed`（volatile 读）与 `track`（synchronized）之间的 monitor 覆盖缺口属已知 open/close 竞态站点 → CL-201（不立案，C6/C1 维度处理中） |
| C3 | `CompletableFuture\|thenApply\|thenCompose\|supplyAsync\|runAsync\|orTimeout` | 0；扩大面 `Future\|Callable\|ExecutorService` 命中 1 站点 | 唯一异步提交点 = LeidenDetector.runWithTimeout → **立案 CS-001**（超时幽灵线程，与 C4 共因） |
| C4 | `new Thread(\|ExecutorService\|Executors.\|scheduleAt*\|parallelStream` | 1 站点（LeidenDetector L197） | 单元内唯一线程创建点：per-call 单线程 executor、无 NamedThreadFactory、shutdownNow 对 CPU 密集任务无效 → **CS-001**；其余文件零线程创建，commonPool/并行流零使用 |
| C5 | `new (ArrayList\|HashMap\|...)(<.*>)?\(` 修正泛型锚点后全量清单 + `readAllBytes\|readString\|readLines\|Files.read` | 61 处集合创建（全部方法内局部或按输入图规模有界）；readCsv/readParquet 为 DuckDB 服务端流式（无内存物化） | 局部队列（Bfs/ImpactPropagator/TarjanSCC）受 maxDepth/maxNodes/图规模约束，有界。例外：**CS-002**（DuckDbEngine 连接台账 append-only）、CL-203（readXlsx 全量物化无包络声明） |
| C7 | `currentTimeMillis\|nanoTime\|.wait(\|await(\|sleep(\|retry` | 4 处 currentTimeMillis（均纯计时统计，非超时判断）；0 调度器、0 重试逻辑 | 无重试风暴/挂死等待点。**CS-002**（台账单调增长，C7 面）、**CS-003**（deleteOnExit 注册表只增不删，F4 族）；CS-001 幽灵线程亦具长时运行可见性缺口（F5） |
| C8 | `Queue\|offer(\|Semaphore\|RateLimit\|backpressure` | 4（Bfs×3 + ImpactPropagator×1，均为方法内局部 BFS 队列，非线程间生产-消费边界） | 单元内无跨线程生产-消费边界；ImpactPropagator 有 maxNodes（默认 100）载荷上界、BFS 有 maxDepth——**已核查，无发现** |
| C9 | 70 处循环锚点逐个速读 + 热路径定位（算法类为每请求热路径） | 2 处可量化浪费 | **CS-004**（PageRank 每入边整表拷贝，×d̄ 放大）、**CS-005**（TopologicalSort `remove(0)` Θ(V²)）；InMemoryGraph 防御性拷贝结构性根因 → CL-204 |

逐文件覆盖记录：33/33 主代码文件全部读毕（api 14 = 纯 DTO/接口，无可变共享状态与同步原语，Edge.attrs 可变但不参与 equals/hashCode，HashSet 键安全性成立；core 13；duckdb 6）。

---

## 二、发现立案

### [CS-001] runWithTimeout 超时后幽灵线程不可终止：CWTS Leiden 纯 CPU 循环对中断零响应

- **文件**: `nop-graph/nop-graph-core/src/main/java/io/nop/graph/algorithm/LeidenDetector.java:196-204`（调用点 L112-117、异常兜底 L136-140）
- **证据片段**:
  ```java
  private static <T> T runWithTimeout(Callable<T> task, long timeoutMs) throws Exception {
      ExecutorService executor = Executors.newSingleThreadExecutor();
      try {
          Future<T> future = executor.submit(task);
          return future.get(timeoutMs, TimeUnit.MILLISECONDS);
      } finally {
          executor.shutdownNow();
      }
  }
  ```
  task 为 `() -> leiden.findClustering(network)`（L113）。已验证依赖证据：对 `~/.m2/repository/nl/cwts/networkanalysis/1.3.0/networkanalysis-1.3.0.jar` 全部 class 反汇编（javap -c）grep `interrupt`，**全库 0 处引用**——`findClustering` 为不检查 `Thread.interrupted()` 的纯 CPU 循环，`shutdownNow()` 的中断无法使其停止；且 `future.get` 超时后未调用 `future.cancel(true)`，future 级取消也被跳过。
- **严重程度**: P1
- **并发场景**: 调用方线程（CodeGraphService/GraphMetricMaterializer 的请求线程，见失效面）与 per-call executor 工作线程；每个带 timeout 的 detect() 调用产生一个未命名 `pool-N-thread-1`。
- **触发时序**: T0：detect() 提交 findClustering（大图，实际耗时 > timeoutMs）；T=timeoutMs：`future.get` 抛 TimeoutException → finally `shutdownNow()` 发出无效中断 → L136 catch 兜底回退 LabelPropagation → detect() 正常返回。T>timeoutMs：幽灵线程继续全速运行 findClustering 至自然结束（大图上可达分钟~小时级），持有 `network`（2×LargeIntArray，O(E) 边内存）与 `leiden` 引用，无任何句柄指向它。重复超时调用 → 幽灵线程逐个累积，线程名均为可互换的 `pool-N-thread-1`，thread dump 不可辨识（F5：挂死生产者对健康检测不可见）。
- **现状**: 超时机制只解除了调用方的阻塞，没有（也无法）终止底层计算；线程、CPU、O(E) 内存在超时后继续被占用且不可观测。
- **风险**: LeidenConfig 默认 timeoutMs=30000，超大型调用图的常规场景即触发；批处理服务反复 detect 时幽灵线程累积造成 CPU 争用（扭曲后续计时与回退决策）、内存滞留放大、线程数无上界增长。超时承诺（bounded runtime）对资源维度失效。
- **建议**: （1）超时路径显式 `future.cancel(true)` 并在文档中声明"底层计算不可中断，超时仅为放弃等待"；（2）改用平台线程设施 `io.nop.commons.concurrent.thread.NamedThreadFactory` 命名线程（如 `leiden-timeout-%d`）使幽灵可辨识；（3）中期：评估 CWTS 调用前按节点/边数预判（超过阈值直接走 LabelPropagation 路径），或将 executor 提升为模块级共享池 + 有界队列以获得背压（C8 联动）。
- **失效面枚举**（F3 同族清扫）: 本审计单元内 `Executors.*` 唯一站点即此（grep `newSingleThreadExecutor|newFixedThreadPool|newCachedThreadPool|new Thread(` 全单元仅 LeidenDetector L197）。repo 级同族（nop-stream、nop-ai 等 15 处 executor 站点）均带命名 factory 或有界池，不属本单元，不在本轮清扫范围。生产消费方：`nop-code-service` CodeGraphService.java:753、GraphMetricMaterializer.java:122（两者均透传 LeidenConfig，默认 timeoutMs=30000 生效）——幽灵线程后果直接作用于 nop-code 图分析服务。
- **信心水平**: 确定（中断无响应已用反汇编证据钉死；触发链完整）
- **误报排除**: 非"synchronized/FutureTask 原语使用本身"误报——缺陷在于超时语义未作用于任务本体且无补偿（cancel/预判/可观测命名均缺失）；非测试代码简化并发模型；派单明示本站点由本批 C3/C4 角度核查。
- **复核状态**: 已复核（CS 维度独立复核 2026-10-22）——保留 P1，不可中断族主登记（吸收 CS-R2-001 调用面 + C6-CL-031 别名）；一处证据表述修正：两个生产消费方均显式 setTimeoutMs(60000)（CodeGraphService:753、GraphMetricMaterializer:121），“默认 timeoutMs=30000 生效”仅适用于未透传配置的调用方（当前主代码无），60s 在大图上同样触发幽灵线程，判级不受影响（详见文末维度复核结论）

### [CS-002] DuckDbEngine 连接台账 append-only：connections 列表与 OPEN_FILES 引用计数不随连接关闭回收

- **文件**: `nop-duckdb/src/main/java/io/nop/duckdb/DuckDbEngine.java:59,110-118,213-238`
- **证据片段**:
  ```java
  private final List<TrackedConnection> connections = new ArrayList<>();
  ...
  public void close() {
      ...
      toClose = new ArrayList<>(connections);
      connections.clear();          // 仅 engine.close() 清空
  ...
  private synchronized Connection track(Connection conn, String normalizedFilePath) {
      connections.add(new TrackedConnection(conn, normalizedFilePath));
      return conn;                  // 返回裸连接，engine 对逐连接关闭无感知
  }
  ```
  `releaseOpen`（L228-238，OPEN_FILES refs 递减）仅被 `close()` 调用（L116）。
- **严重程度**: P2（C7 校准字面上触及"长时运行必然触发的无界增长"的 P1 档，因单体驻留量小（闭合 Connection 壳对象 + FileEntry），降 P2，提请复核裁定）
- **并发场景**: 单例 engine bean；DuckDbSqlTaskStep（共享单例 step，javadoc L45-46 自述 stateless）每次 execute() 经 `openConnection()`（L139-141）开连接、try-with-resources 逐次关闭——即本单元唯一生产消费方的标准路径。
- **触发时序**: 非竞态缺陷，单调增长链：任务步执行 N 次 → `connections` 追加 N 个 TrackedConnection（各持已关闭的 Connection 引用，阻止 GC 直至 engine.close）→ OPEN_FILES 对每个 dbPath 路径 refs 只增不减（连接关闭不减）→ 长驻服务天/周级运行后台账线性增长；dbPath 逐次唯一的场景下 OPEN_FILES 键空间同步无界。
- **现状**: 跟踪作用域（engine 生命周期）与实际连接生命周期（每次执行）错位；track 返回裸连接而非代理，engine 无逐连接关闭钩子；OPEN_FILES 的 refs 语义（按连接计数）与其唯一递减点（按 engine 关闭批量递减）自相矛盾。
- **风险**: （1）长时运行内存单调增长（F4 无界账本）；（2）陈旧指纹产生行为可见的误拒：engine A（配置 X）打开并关闭文件 f 后，engine B（配置 Y）打开 f 被 ERR_DUCKDB_CONFIG_CONFLICT 拒绝——尽管 DuckDB 原生锁已随连接关闭释放，f 实际空闲。
- **建议**: track 返回 closable 代理（或提供 `close(Connection)` 契约方法），在逐连接关闭路径同步移除 TrackedConnection 并调用 `releaseOpen`；若维持 engine 生命周期语义，则 OPEN_FILES 去掉 refs 机制改为按 engine 注册表登记，避免假 refcount。
- **失效面枚举**（F3）: `openMemory`（L62-73，仅入 connections 台账，增长面适用）、`openFile`（L76-100，双台账均适用）——两个入口同病；`releaseOpen` 唯一调用点 close()；生产消费方全量清单（repo grep）：`DuckDbSqlTaskStep.openConnection`（L140，唯一主代码调用点，逐执行开/关，命中增长链）；openMemory/openFile 无其他主代码调用方。
- **信心水平**: 确定（代码路径唯一且无需竞态即成立）
- **误报排除**: 非有界声明容器误报——`connections` 无任何容量上界；非低频路径粗锁——增长来自主消费方的每次执行；静态 OPEN_FILES 由 C1 维度盘点归属，本条立的是其"只增不删 + 作用域错位"的 C5/C7 后果。
- **复核状态**: 已复核（CS 维度独立复核 2026-10-22）——证据保留（append-only 链逐行核实：track L213-215 唯一插入、releaseOpen L228-238 唯一调用点 close():L116、DuckDbSqlTaskStep L85/L139-141 逐执行开/关）；归并处置：并入 C6-001 主登记（P1，账本泄漏族双载体），本维度关闭为别名不独立计数（「P1 档提请复核裁定」由 C6 主登记 P1 应答；详见文末维度复核结论）

### [CS-003] DuckDbFiles.readXlsx 每次 ingest 向 JVM 级 deleteOnExit 注册表追加条目（只增不删，F4 族）

- **文件**: `nop-duckdb/src/main/java/io/nop/duckdb/DuckDbFiles.java:70-87`
- **证据片段**:
  ```java
  tempCsv = Files.createTempFile("duckdb-xlsx-", ".csv").toFile();
  tempCsv.deleteOnExit();          // JVM 级注册表只增不删
  Table table = new XlsxReader().read(XlsxReadOptions.builder(xlsxPath).build());
  table.write().csv(tempCsv.getAbsolutePath());
  ...
  } finally {
      //noinspection ResultOfMethodCallIgnored
      tempCsv.delete();            // 文件已显式删除，deleteOnExit 纯冗余
  }
  ```
- **严重程度**: P3（readXlsx 当前无主代码调用方——repo grep 仅测试 TestDuckDbE2EPipeline 使用，风险为潜伏；作为已发布公共桥接 API 仍按 F4 族登记）
- **并发场景**: 每次调用 readXlsx 的任意线程；`DeleteOnExitHook` 的 LinkedHashMap 为 JVM 级共享结构，仅 JVM 退出时清空。
- **触发时序**: 非竞态：每次调用 createTempFile + deleteOnExit → 注册表追加一项 → finally 中文件已删除，但注册表条目（含 File 路径对象）保留至进程退出 → 长驻进程多次调用后单调增长。
- **现状**: deleteOnExit 与显式 delete 双重清理，前者在文件已删场景下成为纯注册表泄漏源。
- **风险**: 长驻服务高频 xlsx 摄取下注册表无界增长（速率低、单体小，故 P3）；同时附带一处集外弱信号见 CL-203（转换失败路径 temp 文件泄漏）。
- **建议**: 移除 `deleteOnExit()`（finally 的显式 delete 已覆盖成功路径），失败路径清理交给 try-with-resources 或将 temp 文件创建移入受保护 try（联动 CL-203 修复）。
- **失效面枚举**（F3）: 单元内 `createTempFile|deleteOnExit` 全量 grep：主代码仅此一处（另有 nop-duckdb 测试 TestSpillEngine:20 createTempFile 无 deleteOnExit，不适用）；无同族兄弟。
- **信心水平**: 确定（JDK DeleteOnExitHook 语义明确；增长链无前置条件）
- **误报排除**: 非"临时文件正常清理模式"误报——问题不在文件本身而在 JVM 级注册表的只增不删；当前无生产调用方故降 P3 而非不报，API 面已发布。
- **复核状态**: 已复核（CS 维度独立复核 2026-10-22）——证据保留（L72-73 deleteOnExit、L76-79 失败重抛在 L81 try 作用域外、readXlsx 无主代码调用方仅 TestDuckDbE2EPipeline:91，全部核实）；归并处置：并入 C6-003 主登记（P2，deleteOnExit 族，主缺陷=失败路径临时文件不删），本条作 deleteOnExit 注册表子面不独立计数（详见文末维度复核结论）

### [CS-004] PageRank 每条入边触发前驱邻接表整表拷贝：每迭代 Θ(E·d̄) 分配放大

- **文件**: `nop-graph/nop-graph-core/src/main/java/io/nop/graph/algorithm/PageRank.java:59-67`
- **证据片段**:
  ```java
  for (Edge edge : graph.getInEdges(node)) {
      String source = edge.getSourceId();
      if (ranks.containsKey(source)) {
          int sourceOutDegree = graph.getOutEdges(source).size();  // 整表拷贝仅为取 size
          if (sourceOutDegree > 0) {
  ```
- **严重程度**: P2（C9 默认 P2/P3 档；量化放大倍数明确，非微优化）
- **并发场景**: 每请求热路径：PageRank.compute 对 nodes 全集迭代 `iterations` 次；`getOutEdges` 在 InMemoryGraph 实现下每次调用在锁内做防御性全表拷贝（InMemoryGraph.java:45-48）。
- **触发时序**: 非竞态：每次外层迭代中，节点 X 的每条入边 (s→X) 都调用 `getOutEdges(s)` → 拷贝 s 的整个出边列表仅为读取 `.size()`。单迭代总拷贝元素数 = Σ_node inDeg(node) × d̄ = E·d̄；iterations 次迭代再乘系数。例：E=10⁵、d̄=10、iterations=20 → 2×10⁷ 次元素拷贝 + 等量临时 ArrayList 分配，而算法本体有效工作仅 O(E·iterations)。
- **现状**: 出度在一次 compute 内不变，却在每条入边处理时重新拷贝求取；同一方法 L49 `getOutEdges(node).size()`（每节点每迭代一次，Θ(V) 拷贝/迭代）为同族轻症。
- **风险**: 大图 + 多迭代下 GC 压力与延迟可量化放大（×d̄ 倍分配）；调用方 CodeGraphService 对代码库级图（万级节点）例行调用，浪费随仓库规模超线性增长。
- **建议**: compute 开头一次性预计算 `Map<String,Integer> outDegree`（单次 Θ(V+E) 拷贝）；结构性根因见 CL-204（IGraph 缺 degree/count 只读 API，迫使实现走 getOutEdges 全表拷贝）。
- **失效面枚举**（F3）: 同族"getOutEdges(...).size()"模式全单元 grep：PageRank L49（每节点/迭代，轻症）、L62（每入边/迭代，本条）；其余算法（Bfs/Leiden/LabelPropagation/Betweenness/GraphExporter/GraphDiffer）均每节点调用一次（Θ(V+E)/遍历，属快照语义合理成本，不立案）；DirectedGraphAdapter 匿名实现每次调用新建 Edge 列表（适配层固有，量级同上）。
- **信心水平**: 确定（调用次数与单次代价均为代码可直接计量）
- **误报排除**: 非"synchronized/防御性拷贝原语使用"误报——快照语义本身合理，缺陷是同一不可变值被按入边粒度重复求取；量化口径：调用频率（每迭代每入边）× 单次代价（O(d̄) 拷贝）已给出。
- **复核状态**: 已复核（CS 维度独立复核 2026-10-22）——保留 P2，邻接分配放大族主登记（吸收 CS-R2-003 作生产路径载体）；L62/L49 调用模式与量化核实；一处路径笔误修正：InMemoryGraph 实际位于 `io/nop/graph/impl/InMemoryGraph.java`（防御性拷贝在 L44-48），非 algorithm/ 包，不影响证据实质（详见文末维度复核结论）

### [CS-005] TopologicalSort 用 ArrayList 作队列并 remove(0)：整体 Θ(V²) 元素搬移

- **文件**: `nop-graph/nop-graph-core/src/main/java/io/nop/graph/algorithm/TopologicalSort.java:52,59-61`
- **证据片段**:
  ```java
  List<String> queue = new ArrayList<>();     // L52
  ...
  while (!queue.isEmpty()) {
      String node = queue.remove(0);          // L61：O(n) 数组左移
  ```
- **严重程度**: P3（C9 性能优化候选；正确性无影响）
- **并发场景**: 每请求热路径：sort() 单线程执行；queue 存活期间长度可达 O(V)（宽依赖图）。
- **触发时序**: 非竞态：每次 remove(0) 将剩余元素整体左移一位；Σ 搬移量 ≈ V²/2。V=10⁴ → 5×10⁷ 次元素搬移；V=10⁵（大型代码库）→ 5×10⁹，秒~分钟级纯浪费。
- **现状**: Kahn 算法队列以 ArrayList 承载、头部删除；同单元其余 BFS 均已正确选用 LinkedList（Bfs.java:37 等）或 ArrayDeque（TarjanSCC.java:40）。
- **风险**: 大图拓扑排序延迟被 V² 项支配，掩盖在"算法慢"的表象下；与单元内同族实现不一致。
- **建议**: `ArrayDeque<String>` 替换（一行改动，与 TarjanSCC 用法对齐）。
- **失效面枚举**（F3）: `remove(0)` 全单元 grep 唯一命中本处；Bfs×3 与 ImpactPropagator 用 LinkedList（poll 头删 O(1)，合格）；TarjanSCC 用 ArrayDeque（合格）。同族无漏修。
- **信心水平**: 确定
- **误报排除**: 非"优雅性"指控——有 V²/2 次元素搬移的量化依据；非冷路径——拓扑排序为每请求算法入口。
- **复核状态**: 已复核（CS 维度独立复核 2026-10-22）——保留 P3（独立保留，一行修复）；现状修正：「每请求热路径」表述不成立——全仓 grep `TopologicalSort.sort` 主代码零调用方（仅测试 TestAlgorithms.java），与 PathQueryExecutor（CL-210）同为已发布无生产调用面的潜伏 API；Θ(V²) remove(0) 缺陷本体与 ArrayDeque 修复建议维持（详见文末维度复核结论）

---

## 三、维度集外弱信号附记（CL，不立案）

### CL-201 DuckDbEngine open/close 竞态——C2 角度确认：monitor 不覆盖 openFile 复合动作
`DuckDbEngine.java:64,98-99,105-119,213`：`checkClosed()` 为 volatile 读（无锁），此后 openConnection/applySettings/registerOpen/`track()`（synchronized this）之间存在窗口；`close()`（synchronized this）可在窗口内完整执行 → close 后仍可向已清空的 `connections` 追加新连接并使 OPEN_FILES refs 永久滞留（误拒后续异配置 engine）。C2 判定：互斥成立但复合动作守卫缺口（F10 形态）。该站点已由 C6/C1 维度处理中，此处仅登记本形态确认结论与证据行号，不立案、不重复计数。

### CL-202 PathQueryExecutor.edgeMatches 恒以 start 为 from，edgeType 过滤疑似只作用于首跳
`PathQueryExecutor.java:39-40,43-53`：filter λ 写死 `edgeMatches(graph, start, node, edgeType)`，对深度 ≥2 的节点检查的是 start→node（而非前驱→node）是否存在匹配类型边——多数情况下为 false，导致设置 edgeType 时遍历实际无法越过首跳。功能语义问题（非并发/资源形态），附记供功能维度跟进。

### CL-203 readXlsx 全量物化无包络声明 + 转换失败路径 temp CSV 泄漏（C6 集外）
`DuckDbFiles.java:68-87`：XLSX→Tablesaw Table 全量驻留堆（输入文件大小的数十倍放大）后串行化 CSV，无输入规模上限声明（C5 文档缺口）；L74-75 的 read/write 若抛错，异常在 L76 被转抛，L83-86 的 finally（tempCsv.delete）不在该 try 范围 → 失败路径 temp 文件滞留磁盘（仅剩已登记的 deleteOnExit 兜底，见 CS-003）。资源清理不对称属 C6 形态，附记不立案。

### CL-204 InMemoryGraph 防御性拷贝的结构性根因：IGraph 无 degree/count 只读 API
`InMemoryGraph.java:45-62` + `IGraph.java`（仅 getOutEdges/getInEdges 两方法）：线程安全快照语义导致每次邻接查询 O(deg) 拷贝，全部算法单次遍历合计 Θ(V+E) 额外分配；CS-004 的按入边放大是该根因的极端化。若 IGraph 增加 `int getOutDegree(String)`/边计数 API 可整体消解。P3 微观测记，不单独立案。

### CL-205 热路径微观测记（P3，不立案）
（1）`LeidenDetector.java:68` 边去重键 `src + "_" + tgt` 字符串拼接 + String HashSet，可改 long 编码（src<<32|tgt）；（2）`Bfs.java:38,53,73,91` 用 `String[]{nodeId, depth字符串}` 队列元素并 `Integer.parseInt` 解码，可用 record/Deque<Integer> 对。均为每边/每节点小常数开销，相对算法主体成本可忽略，仅登记。

---

## 四、终止声明

- 核查覆盖：33/33 主代码文件逐文件读毕；七形态锚点核查全部执行并记录命中数与判定（第一节）。
- 立案汇总：**5 项**——P1×1（CS-001 LeidenDetector 超时幽灵线程）、P2×2（CS-002 连接台账 append-only、CS-004 PageRank 分配放大）、P3×2（CS-003 deleteOnExit 注册表、CS-005 TopologicalSort Θ(V²)）；CL 附记 5 条（CL-201~205，不立案）。
- 基线核对：C3 零 CompletableFuture、C4 仅 LeidenDetector executor、C2 少量 synchronized、C5a 集合创建少量——与本批基线 `_tmp/cra-baseline-nop-graph-duckdb.txt` 一致（C5a 计数修正泛型锚点后为 61 处，全部有界或方法局部）。
- **抽查终止：七形态核查完毕，除上述 5 立案 + 5 附记外零发现。**（盲审产出，现状标注与合并裁定留待复核阶段；已知站点 LeidenDetector executor 已按 C3/C4 角度立案 CS-001，DuckDbEngine open/close 竞态按派单口径仅作 CL-201 交叉确认。）

---

# CS 深挖第 2 轮

- **轮次动因**：第 1 轮新增 P1（CS-001）触发的必派轮。重点：CS-001 同族放大（"外部库计算不响应中断/无超时参数"形态 + 调用方请求线程占用面枚举）、C5/C7/C9 收尾（第 3 处内存放大站点排查、热路径分配放大穷尽性确认）。
- **本轮覆盖**：nop-graph-core 全部 13 文件逐行重读（Bfs/LeidenDetector/BetweennessCentrality/PageRank/LabelPropagation/PathQueryExecutor/ImpactPropagator/TarjanSCC/TopologicalSort/GraphDiffer/GraphExporter/InMemoryGraph/DirectedGraphAdapter）；nop-duckdb 6 文件复核；调用面全枚举（nop-code-service CodeGraphService/GraphMetricMaterializer/CodeIndexService/ShardBuildCoordinator/NopCodeIndexBizModel + nop-code-core CodeCallGraph/CallGraph，按派单口径仅限"算法调用点占住请求线程"问题）；JGraphT 1.5.2 反汇编中断响应验证（证据模式复刻 CS-001 的 CWTS 反汇编）。
- **盲审声明**：本轮仅读取第 1 轮 CS-sweep.md（派单明示允许），未读取其他维度文件（C1/C6）与 ai-dev/plans/、ai-dev/bugs/、ai-dev/lessons/、ai-dev/backlog/。
- **规模包络基线**（调用面引用）：`CodeCacheManager.java:45-46` MAX_CACHE_SYMBOLS=100,000 / MAX_CACHE_EDGES=500,000（缓存截断即 warn + 部分数据）。

## 〇、第 2 轮清扫表（派单重点逐项回答）

| 派单问题 | 核查结果 | 结论 |
|---|---|---|
| CS-001 同族：nop-graph-core 其余算法有无超时/中断参数缺失站点 | BetweennessCentrality.compute **零超时参数**（API 面无 timeout 入参，JGraphT 1.5.2 Brandes 反汇编 0 处 interrupt 引用）；Leiden 主体循环 timeoutMs≤0 路径直接同步跑（主代码调用方均设 60000，未触发）；PageRank 固定 20 迭代有界；PathQueryExecutor maxHops 有界且**无主代码调用方**；LabelPropagation/TarjanSCC/Bfs/ImpactPropagator 线性有界 | 立案 **CS-R2-002**（Betweenness）；其余有界不立案 |
| 调用方还有哪些算法调用点占住请求线程 | 全枚举：7 个 @BizQuery（detectCommunities/getCriticalNodes/getKnowledgeGaps/exportGraph/diffGraph/getExplorationQuestions/exportGraphWiki）+ 1 个 @BizMutation（triggerFullIndex）+ materializeGraphMetrics 全部同步在 GraphQL 请求线程跑算法；自愈物化（ensureMaterializedForGraphMetrics）使**任意首查**触发全量 Leiden+Betweenness+PageRank；Leiden 调用面**零节点规模预判守卫**（Betweenness 侧反而有 10⁴ 守卫——同族守卫不一致） | 立案 **CS-R2-001**（守卫不一致）+ **CS-R2-004**（ShardBuildCoordinator 事务内物化，当前未装配） |
| C5 第 3 处算法内存放大（CS-004 之外） | GraphDiffer 双图 4×Set\<Edge\> 物化（O(E) 有界，常数因子）；GraphExporter.exportJson List\<String\[\]\> 中间物化（导出语义固有）；**CodeCallGraph 适配器每次 getOutEdges/getInEdges 新建全量 Edge 对象**——CS-004 在生产路径的失效面延伸（生产图是 CodeCallGraph 而非 R1 取证的 InMemoryGraph） | 立案 **CS-R2-003**（CS-004 族生产路径补强）；前两者 CL-207/CL-208 附记 |
| C9 热路径分配放大穷尽性 | 13 算法文件全部循环锚点重读：新增 TarjanSCC 每帧重取 getOutEdges（CL-204 根因延伸，2× 常数）；其余站点 R1 已穷尽（CS-004/CS-005/CL-204/CL-205） | CL-206 附记，无新立案 |

---

## 一、发现立案（CS-R2-001 起）

### [CS-R2-001] Leiden 请求线程调用面零节点规模预判守卫：重算法（幽灵线程源）无守卫、轻算法（Betweenness）反而有 10⁴ 守卫——同族守卫不一致

- **文件**: 调用面 `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeGraphService.java:68,446,506,521` + `GraphMetricMaterializer.java:113-123`；单元锚点 `nop-graph/nop-graph-core/src/main/java/io/nop/graph/algorithm/LeidenDetector.java:44-50`（detect API 无规模参数，仅 nodes<2 校验）
- **证据片段**:
  ```java
  // GraphMetricMaterializer.java:113-123 —— Leiden 唯一仅有的规模检查是下界
  private CommunityResult detectCommunities(CodeCallGraph graph, Set<String> nodes) {
      if (nodes.size() < 2) { ... return null; }
      LeidenConfig config = LeidenConfig.create()
              .setResolution(0.1).setMaxIterations(10).setTimeoutMs(60000);
      return LeidenDetector.detect(graph, nodes, config);   // 无上界守卫
  }
  // CodeGraphService.java:521 —— diffGraph 只守 baseline（L506），target 侧不守
  CommunityResult targetCommunities = runCommunityDetection(targetCallGraph);
  ```
- **严重程度**: P2
- **并发场景**: GraphQL 请求线程（NopCodeIndexBizModel 7 个 @BizQuery + triggerFullIndex @BizMutation + materializeGraphMetrics，均同步）× LeidenDetector per-call executor 工作线程（CS-001 幽灵线程源）。
- **触发时序**: T0：客户端首次调用 `NopCodeIndex__getKnowledgeGaps(indexId)`（指标行缺失）→ L476 `runCommunityDetection` **无节点数预判** → LeidenDetector.detect 按缓存包络构图（V=10⁵、E=5×10⁵）→ CWTS local-moving。T=60s：超时，按 CS-001 铸造幽灵线程 #1，L138 兜底 LabelPropagation 继续占请求线程。客户端重复调用 → 每次调用铸造一个幽灵线程，全程无任何守卫拦截；对比同文件 Betweenness 侧 L446/L506/L841 与 materializer L185 三处 10⁴ 上界守卫齐全。
- **现状**: `MAX_NODES_FOR_COMMUNITY_DETECTION=10000`（L68 已定义）只保护了 Betweenness/临界节点路径；Leiden（更重、且是 CS-001 幽灵线程的唯一来源）的全部 5 个调用点（materializer.detectCommunities、detectCommunities 兜底 L121、getKnowledgeGaps L476、exportGraph L495、diffGraph target L521）零上界守卫；diffGraph 基线/目标侧守卫不对称，守卫未能约束其保护的操作（diff 需双图）。
- **风险**: 超大调用图上 Leiden 常规超时 → 幽灵线程按请求频度累积（CS-001 的外部触发面从"批处理服务"放大为任意 GraphQL 客户端可随意触发）；请求线程被 60s+兜底 LabelPropagation 反复占用。
- **建议**: 在 `runCommunityDetection` 与 `GraphMetricMaterializer.detectCommunities` 统一加节点数预判（超过 MAX_NODES_FOR_COMMUNITY_DETECTION 直接走 LabelPropagation 或降级返回，复用既有常量）；diffGraph 守卫覆盖双侧。此即 CS-001 建议(3)的调用面落地项，独立立案以便单独复核。
- **失效面枚举**（F3）: Leiden 调用点全清单——①GraphMetricMaterializer.detectCommunities:122 无守卫；②CodeGraphService.detectCommunities 兜底:121 无守卫；③getKnowledgeGaps:476 无守卫；④exportGraph(communityView=true):495 无守卫；⑤diffGraph baseline:513 有守卫(L506)/target:521 无守卫（不对称）；自愈入口 ensureMaterializedForGraphMetrics:335 使①对 getExplorationQuestions:344/exportGraphWiki:372/getGraphAnalysis:199 间接可达。Betweenness 调用点 2 处均守卫（materializer:185、CodeGraphService:446）。PageRank 1 处（materializer:201，固定 20 迭代有界）。
- **信心水平**: 确定（全调用点 grep 枚举 + 逐点读码，无估算成分）
- **误报排除**: 非把"同步算法"本身当问题——缺陷是 F3 同族守卫不一致：守卫常量已存在且用于同文件的轻算法侧，重算法侧零引用；无任何 owner 文档声明"Leiden 允许任意规模"（非已声明边界）；与本轮 CS-R2-002 分立案（不同站点不同修复：本条补预判守卫，R2-002 补超时参数）。
- **复核状态**: 已复核（CS 维度独立复核 2026-10-22）——保留 P2，并入 CS-001 作调用面失效面（守卫不对称全量核实：Leiden 5 调用点 materializer:122 / CodeGraphService:121/476/495/521 零上界守卫，Betweenness 侧 :446/:841/materializer:185 守卫齐全，diffGraph :506 基线守卫 / :521 target 无守卫；详见文末维度复核结论）

### [CS-R2-002] BetweennessCentrality.compute 无超时/中断参数：JGraphT Brandes O(V·E) 同步占住请求线程且不可取消（CS-001 直接同族）

- **文件**: `nop-graph/nop-graph-core/src/main/java/io/nop/graph/algorithm/BetweennessCentrality.java:53-58`
- **证据片段**:
  ```java
  @SuppressWarnings("unchecked")
  org.jgrapht.alg.scoring.BetweennessCentrality<String, DefaultEdge> bc =
          new org.jgrapht.alg.scoring.BetweennessCentrality<>(jgraph, false);
  Map<String, Double> scores = bc.getScores();   // 此处触发全源 Brandes，调用线程同步执行
  ```
  依赖证据（与 CS-001 同一取证标准）：对 `~/.m2/repository/org/jgrapht/jgrapht-core/1.5.2/jgrapht-core-1.5.2.jar` 的 `org/jgrapht/alg/scoring/BetweennessCentrality.class` 全类反汇编（javap -c）grep `interrupt`，**0 处引用**——`getScores()` 触发的 `compute()`/`compute(V)` Brandes 循环不检查 `Thread.interrupted()`，中断无法使其停止；API 面无任何超时参数（对比 LeidenConfig.timeoutMs 至少有超时包装）。
- **严重程度**: P2
- **并发场景**: GraphQL 请求线程（含首次查询自愈物化路径）同步执行；无线程泄漏（区别于 CS-001 的 per-call executor），危害形态为不可取消的请求线程占用。
- **触发时序**: T0：客户端首次调用 `NopCodeIndex__getExplorationQuestions` → CodeGraphService.ensureMaterializedForGraphMetrics(L332-340) → materialize() → betweennessRows(L185 守卫 V≤10⁴ 通过) → BetweennessCentrality.compute。T0→T0+X：调用线程执行全源 Brandes——V=10⁴（守卫上限）× E≤5×10⁵（MAX_CACHE_EDGES 包络）→ Σ BFS ≈ 5×10⁹ 松弛步，秒~分钟级；期间无超时、无中断响应点，客户端断连/取消均不能停止计算。首次查询即触发，无需任何攻击性输入。
- **现状**: 两处调用方（GraphMetricMaterializer:190、CodeGraphService:971）均有 10⁴ 节点预判守卫，V 有界；但守卫只约束节点数不约束墙钟——守卫上限内 Brandes 仍可达分钟级，且 API 无超时/中断参数，与 LeidenConfig.timeoutMs 的能力面不对称。
- **风险**: 任意首查触发的分钟级 GraphQL worker 占用；并发首查（多 worker 同时自愈）× 分钟级计算 → worker 池排队雪崩；无任何取消/超时逃生口。
- **建议**: 比照 LeidenConfig 增加 timeout 参数（包装 `bc.getScores()` 于 runWithTimeout 类设施 + 显式 cancel 语义声明"底层 Brandes 不可中断，超时仅为放弃等待"）；中期在守卫内增加边数上界（V×E 乘积上限）或预判密集度。复用平台 `io.nop.commons.concurrent.thread.NamedThreadFactory` + 有界池获得背压。
- **失效面枚举**（F3）: 本单元 `org.jgrapht.alg` 委托站点全清单——BetweennessCentrality（本条，2 个调用方均守卫 V）、LabelPropagation→LabelPropagationClustering（迭代数有界，调用点 LeidenDetector:126/138 兜底路径，占用叠加在超时之后但有限）、GraphExporter→GraphMLExporter（输出规模 O(V+E) 有界）。LeidenDetector→CWTS 已立案 CS-001。无第 4 处外部库算法委托。
- **信心水平**: 确定（反汇编 0 命中 + 调用链全部读码确认）
- **误报排除**: 非"原语使用本身"误报——10⁴ 守卫存在说明作者已识别规模风险，但守卫不覆盖墙钟且 API 缺超时参数；非测试代码简化模型；与 CS-001 按"根因 + 失效层"为同族相邻（合并裁定时可归并 CS-001 缺陷族，但失效层不同：同步占用 vs 幽灵线程泄漏），独立立案以便逐项复核。
- **复核状态**: 已复核（CS 维度独立复核 2026-10-22）——保留 P2，独立保留归不可中断族 cross-ref；反汇编取证独立复现（jgrapht-core-1.5.2 BetweennessCentrality 主类 + 4 内部类 javap -c grep interrupt 全 0 命中，且无 Thread/Executor 创建——同步执行证实）；10⁴ 守卫只限 V 不限墙钟核实（详见文末维度复核结论）

### [CS-R2-003] CodeCallGraph 适配器每次邻接查询新建全量 Edge 对象：CS-004 分配放大在生产路径的失效面加倍

- **文件**: `nop-code/nop-code-core/src/main/java/io/nop/code/core/graph/CodeCallGraph.java:25-48`（适配器）+ `nop-code/nop-code-core/src/main/java/io/nop/code/core/graph/CallGraph.java:36-44`（synchronized 拷贝）；消费点 `nop-graph/nop-graph-core/src/main/java/io/nop/graph/algorithm/PageRank.java:59-67`
- **证据片段**:
  ```java
  // CodeCallGraph.getOutEdges —— 每次调用为每条边新建 Edge 对象
  public List<Edge> getOutEdges(String nodeId) {
      List<String> callees = callGraph.getCallees(nodeId);   // synchronized 内整表拷贝 String 列表
      List<Edge> edges = new ArrayList<>(callees.size());
      for (String callee : callees) {
          edges.add(new Edge(nodeId, callee, 1.0, "CALLS"));  // 每边一个新 Edge（4 字段 + 31-hash）
      }
      return edges;
  }
  ```
- **严重程度**: P3（CS-004 族的失效面补充，按 F3 单独登记不重复计数；量化常数明确）
- **并发场景**: 每请求热路径：生产调用链传入的 IGraph 是 `new CodeCallGraph(callGraph)`（CodeGraphService:753/971、GraphMetricMaterializer:91），**不是** R1 CS-004 取证的 InMemoryGraph——每次 getOutEdges/getInEdges 除整表拷贝外还多一层 O(deg) 新对象分配 + CallGraph synchronized String 列表拷贝。
- **触发时序**: 非竞态：PageRank L62 每条入边调用 `getOutEdges(source)` → CallGraph.getCallees 同步拷贝 O(d̄) 个 String 引用 + 适配器新建 O(d̄) 个 Edge 对象（各含 4 字段与 hashCode 计算）。PAGE_RANK_ITERATIONS=20、E=5×10⁵、d̄=10 → 每次物化 ≈ 10⁸ 个临时 Edge 分配量级，为 R1 所测 InMemoryGraph 路径（仅引用拷贝）的分配字节量的数倍。
- **现状**: R1 CS-004 的修复建议（预计算 outDegree Map）若在 InMemoryGraph 语义下验证，将无法消除生产路径的本层分配——两层放大（列表拷贝 + Edge 重建）中只有列表拷贝被该修复消解。
- **风险**: 大图物化时 GC 压力与延迟被 ×d̄ 放大再乘适配器对象常数；修复验证若只测 InMemoryGraph 会漏检本层。
- **建议**: CodeCallGraph 增加邻接快照缓存（构造时一次性物化 Map\<String,List\<Edge\>\>，CallGraph 本就 WP-5 AR-148 快照语义）或 IGraph 增加 degree/count 只读 API（CL-204 结构性方向）；PageRank 修复（CS-004）的回归测试须同时覆盖 CodeCallGraph 路径。
- **失效面枚举**（F3）: 同族"每调用新建 Edge"适配器全单元清单——CodeCallGraph（本条，生产主路径）、DirectedGraphAdapter 匿名实现:33-61（R1 CS-004 失效面已登记，适配层固有）、InMemoryGraph（仅列表引用拷贝，R1 已立案）。消费点：PageRank L49/L62（每迭代/每入边，重灾）、LeidenDetector.detect L62-74（单次 O(E) 构图，一次性成本合理）、calculateCohesion L178-186（单次 O(V+E)，合理）。
- **信心水平**: 确定（调用次数与分配层数均代码可直接计量）
- **误报排除**: 非适配层"固有成本"豁免——本条只立"每入边重复重建"的 PageRank 消费模式（CS-004 同根因），单次遍历消费不立案；与 CS-004 分条登记系因失效层不同（生产适配器 vs 单元内实现），合并裁定时可归并 CS-004 缺陷族。
- **复核状态**: 已复核（CS 维度独立复核 2026-10-22）——保留 P3，并入 CS-004 作生产路径载体（CodeCallGraph L25-48 每邻接查询新建 Edge、CallGraph L36-44 synchronized 整表拷贝、消费点 PageRank L49/L62 全部核实；修复验证双路径同测建议成立；详见文末维度复核结论）

### [CS-R2-004] ShardBuildCoordinator 在 ORM 事务/会话内执行全量图指标物化：DB 事务持有时长 = Leiden+Betweenness+PageRank 计算时长（当前未装配，潜伏）

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/ShardBuildCoordinator.java:52-59,71-91,104-107`
- **证据片段**:
  ```java
  public ShardBuildStatus completeShard(String indexId, String shardId) {
      return ormTemplate.runInSession(session -> {          // 事务/会话开启
          ...
          if (allCompleted) {
              ...
              if (metricMaterializer != null) {
                  // publish point: global metrics materialized in the same transaction as the
                  // status flip, so a published index is always fully scored
                  metricMaterializer.materialize(indexId);   // 全量计算在事务内
              }
  ```
- **严重程度**: P3（当前无 beans.xml 装配、无主代码调用方，仅 TestShardBuildCoordinator 引用——潜伏；装配激活即升 P2，提请复核裁定）
- **并发场景**: completeShard 调用线程在 `ormTemplate.runInSession` 事务内同步执行 materialize() 全量计算（Leiden timeoutMs=60000 → CS-001 幽灵线程、Betweenness V≤10⁴ 无超时（CS-R2-002）、PageRank 20 迭代、replaceRows 嵌套 REQUIRED 事务批量 delete/insert）。
- **触发时序**: 非竞态：completeShard（最后一个分片完成）→ 事务开启并持有连接/行锁 → materialize 计算全程（60s 超时路径 + Betweenness 分钟级路径）→ 才提交 status=COMPLETED + 指标行。事务持有时长 = CPU 计算时长；期间连接池占用、ledger/index 行锁持有、undo 增长；Leiden 超时铸造的幽灵线程亦发生在事务窗口内。
- **现状**: javadoc 与注释声明的原子性契约是"status 翻转与指标行同一事务发布"；实现把**计算**也放进了事务，而契约只要求**行写入**同事务。
- **风险**: 装配激活后，分片发布请求占住 DB 连接分钟级；并发分片完成 + 连接池有限 → 连接耗尽；锁等待扩散到同库其他写者。
- **建议**: 计算前置——在事务外完成 callGraph/指标计算（读不依赖本事务写），事务内只做 status 翻转 + replaceRows 批量写入；原子性契约保持不变。
- **失效面枚举**（F3）: materialize() 全部调用点——CodeGraphService ×4（:114/:199/:335/:439，均在**事务外**自愈路径，仅占请求线程，归 CS-R2-001/002 管）、CodeIndexService:727（triggerFullIndex @BizMutation 事务外）、ShardBuildCoordinator:111（本条，唯一事务内站点）。无第 2 处事务内物化。
- **信心水平**: 确定（事务边界与调用链代码直接可证；潜伏状态经 beans.xml/Java 全仓 grep 双口径确认）
- **误报排除**: 非"事务内做业务"的风格指控——连接/锁持有时长等于 CPU 计算时长（60s+ 量级）可具体化；非已声明边界——声明的原子性契约可用"计算后短事务写"完整保持，把计算移出事务不违反任何声明。
- **复核状态**: 已复核（CS 维度独立复核 2026-10-22）——保留 P3（潜伏，装配激活升 P2 动议成立）；runInSession L78 → finalizeIfAllCompleted L91 → materialize L111 事务内核实；materialize 全部 6 调用点核实（CodeGraphService:114/199/335/439 + CodeIndexService:727 均事务外，ShardBuildCoordinator:111 唯一事务内）；无 beans 装配双口径核实（全仓 *.java/*.beans.xml 非 _gen 零引用，仅测试直接 new）；详见文末维度复核结论

---

## 二、维度集外弱信号附记（CL-206 起，不立案）

### CL-206 TarjanSCC 迭代帧每次弹栈重取 getOutEdges（CL-204 根因延伸）
`TarjanSCC.java:63-98`：每弹出一个帧（含子节点完成后的 returning 帧，L95 重入）都在 L77 重新调用 `graph.getOutEdges(v)`——每节点取邻接表 1+children 次每次整表拷贝，合计 ≈ 2E 元素拷贝（稠密枢纽节点最坏 O(deg²)）。线性量级、CL-204（IGraph 缺 degree API）的结构性延伸，登记不立案；CL-204 修复时顺带覆盖。

### CL-207 GraphDiffer 双图边集 4×Set\<Edge\> 物化（O(E) 有界，常数因子）
`GraphDiffer.java:46-53,94-100`：collectEdges 将双图全量边集物化为 LinkedHashSet，add/remove 差集再各拷贝一次 → 4×|E| 集合驻留（包络内 2×5×10⁵×4 ≈ 4×10⁶ 条目，数百 MB 瞬时）。单次物化有界、算法固有工作集，常数可减半（差集可原地遍历判定）；Edge 值语义含 weight（`Edge.java:69-72`，javadoc 已声明）——weight 变化在 diff 中呈现为 add+remove 对是文档化行为，非缺陷。登记供性能维度参考。

### CL-208 GraphExporter.exportJson 非社区路径全量中间物化
`GraphExporter.java:189-204`：先物化 `List<String[]> allEdges`（全边 × String[2]）再拼接 StringWriter，双倍驻留 + 最终 String 整体在堆（导出 API 返回 String 语义固有）。有界 O(E)，常数因子，登记不立案。

### CL-209 DuckDB JDBC 路径零 queryTimeout：病态长查询占住任务线程无取消点
`DuckDbSqlTaskStep.java:143-158`（executeSql 查询路径 `while (rs.next()) n++` 全结果集迭代计数）+ `DuckDbFiles.java:125-191`（readExternal/copyTo/countRows/tableExists 全部 Statement/PreparedStatement）：单元内**所有** JDBC 语句均未调用 `setQueryTimeout`，病态长查询（大结果集计数、慢 SQL）将无限期占住 nop-task 工作线程，JDBC 层无取消点、任务步无 timeout 输入、owner 文档无超时契约声明。因"SQL 执行时长属任务语义、超时契约未声明"不立案，登记供 owner 裁定（对照 F5：超时应作用于所有等待点）。

### CL-210 PathQueryExecutor 无主代码调用方 + edgeMatches 放大受 CL-202 自限
全仓 grep（--exclude-dir=_gen）确认 PathQueryExecutor.execute 无主代码调用方（仅测试）。其 `edgeMatches(graph, start, node, edgeType)` 每访问节点全表扫描 start 出边（O(V·d̄(start)) 放大候选），但该放大仅在 edgeType≠null 时发生，而 CL-202 已证此时遍历无法越过首跳、访问集被压回 2 跳邻域——放大被功能缺陷自限。附记：修复 CL-202 时须同步复核本放大面（修复后访问集扩大，O(V·d̄) 才真正暴露）。

---

## 三、第 2 轮终止声明

- 派单三项重点全部闭合：①CS-001 同族放大——新立案 3 条（CS-R2-001 守卫不一致、CS-R2-002 Betweenness 无超时、CS-R2-004 事务内物化）+ 生产路径补强 1 条（CS-R2-003）；②C5 第 3 处内存放大——除 CS-004 族（R2-003 为其生产失效面）外无独立新站点（GraphDiffer/GraphExporter 均有界，CL-207/208 附记）；③C9 热路径分配放大——13 算法文件循环锚点重读穷尽，仅新增 CL-206（CL-204 根因延伸）。
- 立案汇总：**4 项**——P2×2（CS-R2-001、CS-R2-002）、P3×2（CS-R2-003、CS-R2-004）；CL 附记 5 条（CL-206~210，不立案）。编号与第 1 轮 CS-001~005 连续，合并裁定阶段建议将 CS-001/CS-R2-001/CS-R2-002 按「根因 + 失效层」归并为同一缺陷族（外部库算法不可中断 × 调用面无守卫/无超时）。
- **本轮终止：CS 维度（C2/C3/C4/C5/C7/C8/C9）在 nop-graph + nop-duckdb 单元及其算法调用面上已无新站点可挖——全部 33 单元文件第 2 次逐行核毕，调用方 8 文件按派单口径核毕，第 3 轮无存在依据。**（盲审产出，现状标注与合并裁定留待复核阶段。）

---

# CS 维度独立复核（Phase 6，2026-10-22）

- **复核方法**: 9 条逐条重读源码（LeidenDetector / DuckDbEngine / DuckDbFiles / PageRank / TopologicalSort / BetweennessCentrality / CodeGraphService / GraphMetricMaterializer / ShardBuildCoordinator / CodeCallGraph / CallGraph / DuckDbSqlTaskStep / InMemoryGraph / IGraph / Bfs / TarjanSCC / GraphDiffer / GraphExporter 全部按 file:line 重读）；两组反汇编取证独立复现：CWTS `networkanalysis-1.3.0.jar` 全 222 class `javap -c` grep `interrupt` = **0 命中**、`jgrapht-core-1.5.2` BetweennessCentrality 主类 + 4 内部类 = **0 命中且无 Thread/Executor 创建**（同步执行证实）；ShardBuildCoordinator 装配双口径 grep（全仓 `*.java`/`*.beans.xml` 非 `_gen` 零引用，仅 TestShardBuildCoordinator 直接 `new`）；对照 C6/C1 维度复核结论（C6-resource-lifecycle.md「维度复核结论」）核实同站点裁定一致性。允许读 cra-index 与历史审计；只读复核 + 回填复核状态行，未修改其他维度文件 / cra-index / 产品代码。

## 维度复核结论

| ID | 判定 | 现状标注 | 关键复核证据（file:line 重读 + 取证复现） |
|---|---|---|---|
| CS-001 | **保留 P1**（不可中断族主登记） | 新发现，未修复 | runWithTimeout L196-204 无 `future.cancel(true)`、L113 提交 `findClustering`、L136-140 超时兜底全部核实；CWTS 反汇编独立复现（222 class，0 interrupt）。**表述修正**：LeidenConfig.java:9 默认 30000 属实，但两个生产消费方均显式 `setTimeoutMs(60000)`，「默认 30000 生效」仅适用于未透传配置的调用方（当前主代码无）——60s 在大图上同样触发幽灵线程，P1 不受影响。**与 C6-CL-031 裁定一致性核实：一致**——C6 侧三态裁定「边界变化并入 CS-001」确认 executor finally 配对闭合（L201-203 三路径必执行）、无 awaitTermination 非独立缺陷、ghost 线程主面归本条主登记，与本条证据互补无冲突 |
| CS-002 | **保留证据，并入 C6-001 主登记**（本维度关闭为别名，不独立计数） | 新发现（同站点四方确认），未修复 | append-only 链逐行核实：`connections` L59 唯一插入 track L214、唯一移除 close() L111、`releaseOpen` L228-238 唯一调用点 close():L116、track 返回裸连接 L215；DuckDbSqlTaskStep L85 try-with-resources / L139-141 逐执行开/关证实增长链。与 C6-001 / C6-R2-001 / C1-R2-001 四方同站点成立；C6 复核已裁定账本泄漏族主登记 = C6-001（P1，双载体 OPEN_FILES + connections），CS-002 的 C5/C7 后果表述与「P1 档提请复核」随主登记收敛，修复建议（track 返回可关代理）与 C6-001 建议(a) 同构采其一 |
| CS-003 | **保留证据，并入 C6-003 主登记**（作 deleteOnExit 子面，不独立计数） | 新发现，未修复 | L72 createTempFile、L73 deleteOnExit、L76-79 失败重抛在 L81 第二段 try 作用域外、L83-86 finally 仅覆盖 readCsv 全部核实；readXlsx 无主代码调用方核实（仅 TestDuckDbE2EPipeline:91），P3 定位恰当。C6 复核已裁定 deleteOnExit 族主登记 = C6-003（P2，主缺陷 = 失败路径临时文件不删），本条注册表增长并入作子面 |
| CS-004 | **保留 P2**（邻接分配放大族主登记） | 新发现，未修复 | L62 每入边 `getOutEdges(source).size()`、L49 每节点每迭代同族轻症核实；**路径笔误修正**：InMemoryGraph 实际位于 `io/nop/graph/impl/InMemoryGraph.java`（防御性拷贝 L44-48），非 algorithm/ 包，不影响证据实质；生产路径经 CodeCallGraph 的放大已由 CS-R2-003 补强归族 |
| CS-005 | **保留 P3**（独立保留） | 新发现，未修复（**现状修正：潜伏**） | L52 ArrayList 队列、L61 `remove(0)` 核实；**现状修正**：「每请求热路径」不成立——全仓 grep `TopologicalSort.sort` 主代码零调用方（仅测试 TestAlgorithms.java），与 PathQueryExecutor（CL-210）同为已发布无生产调用面的潜伏 API；Θ(V²) 缺陷本体与 ArrayDeque 一行修复维持，P3 恰当 |
| CS-R2-001 | **保留 P2**（并入 CS-001 作调用面失效面） | 新发现，未修复 | 守卫不对称全量核实：`MAX_NODES_FOR_COMMUNITY_DETECTION=10000`（CodeGraphService:68）；Leiden 5 调用点（materializer:122、CodeGraphService:121/476/495/521）零上界守卫、runCommunityDetection:744-753 仅 `nodes<2` 下界；Betweenness 侧 :446/:841/materializer:185 三处守卫齐全；diffGraph :506 基线守卫 / :521 target 无守卫（不对称）核实 |
| CS-R2-002 | **保留 P2**（独立保留，归不可中断族 cross-ref） | 新发现，未修复 | 反汇编取证独立复现（BetweennessCentrality 主类 + OverflowStrategy/MyQueue/UnweightedQueue/WeightedQueue 5 class，0 interrupt，无 Thread 创建 = 同步执行）；API 无 timeout 参数核实；两调用方 10⁴ 守卫（materializer:185、CodeGraphService:446→971）只限 V 不限墙钟核实 |
| CS-R2-003 | **保留 P3**（并入 CS-004 作生产路径载体） | 新发现，未修复 | CodeCallGraph L25-48 每邻接查询新建 Edge、CallGraph L36-44 synchronized 整表拷贝、消费点 PageRank L49/L62 每迭代/每入边全部核实；CS-004 修复验证须双路径（InMemoryGraph + CodeCallGraph）同测的建议成立 |
| CS-R2-004 | **保留 P3**（潜伏，装配激活升 P2 动议成立） | 新发现，未修复（潜伏：无装配） | `runInSession` L78 → finalizeIfAllCompleted L91 → materialize L111 事务内核实；materialize 全部 6 调用点核实（CodeGraphService:114/199/335/439 + CodeIndexService:727 均事务外，ShardBuildCoordinator:111 唯一事务内）；无 beans 装配双口径核实（全仓非 `_gen` 零引用，仅测试直接 `new`） |

### 归并建议（缺陷族主登记）

1. **Leiden/Betweenness 不可中断族**（外部库算法不可中断 × 调用面无守卫/无超时）：**主登记 = CS-001（P1）**。CS-R2-001 并入作调用面失效面（其修复即 CS-001 建议(3) 的落地项）；CS-R2-002 同族相邻独立保留（不同站点不同修复：超时参数 + 边数守卫；失效层为同步占用而非线程泄漏），族内 cross-ref。与 C6-CL-031「边界变化并入 CS-001」裁定闭环：本族主登记唯一指向 CS-001，cra-index 2.1 回填注记以 C6 复核结论文本为准。
2. **DuckDB 账本泄漏族**：遵从 C6 复核裁定，主登记 = **C6-001**（P1，双载体 OPEN_FILES + connections）；CS-002 关闭为别名，修复验证双载体同测。
3. **deleteOnExit 族**：遵从 C6 复核裁定，主登记 = **C6-003**（P2）；CS-003 并入作 deleteOnExit 注册表子面，「无生产调用方」边界限定随并入保留。
4. **邻接分配放大族**：主登记 = **CS-004**（P2，单元内实现），CS-R2-003 并入作生产路径载体（CodeCallGraph 适配层），修复验证双路径同测；CL-204（IGraph 缺 degree/count API）作结构性根因附注。

### CL-201~210 裁定

**确认 10/10，驳回 0**（可归组）：

- CL-201 → 确认，并入 C6-002 竞态族（C6 复核已记为第三同站点确认，本维度不独立计数）。
- CL-202 → 确认（PathQueryExecutor L43 `edgeMatches(graph, start, node, edgeType)` 恒以 start 为 from、L49-53 只扫 start 出边核实），留功能维度跟进。
- CL-203 → 确认（DuckDbFiles L76-79 失败重抛在 finally 作用域外核实），后果并入 C6-003。
- CL-204 → 确认（IGraph 仅 getOutEdges/getInEdges 两方法核实；路径笔误修正：InMemoryGraph 在 io/nop/graph/impl/），作 CS-004 族结构性根因附注。
- CL-205 → 确认（LeidenDetector:68 字符串去重键、Bfs String[] 队列 + parseInt 核实）。
- CL-206 → 确认（TarjanSCC L77 帧内重取 getOutEdges、L95 returning 帧重入核实）。
- CL-207 → 确认（GraphDiffer collectEdges×2 + added/removed 差集各一 = 4×|E| 集合核实）。
- CL-208 → 确认（GraphExporter L189-204 allEdges `List<String[]>` 中间物化核实）。
- CL-209 → 确认（DuckDbSqlTaskStep executeSql `while(rs.next())` 计数 + DuckDbFiles 全部 Statement/PreparedStatement 无 setQueryTimeout 核实），留 owner 裁定。
- CL-210 → 确认（PathQueryExecutor.execute 主代码零调用方核实；edgeMatches 放大受 CL-202 自限的联动约束成立）。

## 最终保留项

| ID | 判级 | 现状 | 归并去向 |
|---|---|---|---|
| CS-001 | P1 | 新发现，未修复 | 不可中断族**主登记**（吸收 CS-R2-001 调用面 + C6-CL-031 别名） |
| CS-002 | P2 | 新发现（四方同站点确认），未修复 | 并入 C6-001 主登记，关闭为别名 |
| CS-003 | P3 | 新发现，未修复 | 并入 C6-003 主登记作 deleteOnExit 子面 |
| CS-004 | P2 | 新发现，未修复 | 邻接分配放大族**主登记**（吸收 CS-R2-003 作生产载体） |
| CS-005 | P3 | 新发现，未修复（潜伏：无主代码调用方） | 独立保留（一行修复） |
| CS-R2-001 | P2 | 新发现，未修复 | 并入 CS-001 作调用面失效面 |
| CS-R2-002 | P2 | 新发现，未修复 | 独立保留，不可中断族 cross-ref |
| CS-R2-003 | P3 | 新发现，未修复 | 并入 CS-004 作生产路径载体 |
| CS-R2-004 | P3 | 新发现，未修复（潜伏：无装配） | 独立保留（装配激活升 P2 动议成立） |

**净效果**: 9/9 证据成立、0 驳回、0 降级；2 条跨维度归并（CS-002→C6-001、CS-003→C6-003）、2 条族内并入（CS-R2-001→CS-001、CS-R2-003→CS-004）；1 条现状修正（CS-005 潜伏）+ 1 条证据表述修正（CS-001 timeout 默认值生效面）；CL-201~210 确认 10/10（2 条并入 C6 族、8 条维度内保留）。
