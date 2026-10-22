# C1 共享可变状态与可见性 — nop-graph-duckdb 审计单元

- **审计单元**: nop-graph-duckdb（nop-graph/nop-graph-api 14f/613 + nop-graph/nop-graph-core 13f/1,482 + nop-duckdb 6f/837 = 33f/2,932，仅 src/main）
- **维度**: C1 共享可变状态与可见性（五类载体盘点 + 发布安全 + 检查-然后-行动 + long/double 原子性 + F3 同族清扫）
- **批次**: 全单元单批（初审，盲审）
- **盲审声明**: 本初审为盲审——未读取 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`、`ai-dev/backlog/` 下任何文件；全部结论以 live code 为准；`_gen/` 与 `_` 前缀生成物不作发现对象；只读审计，未修改任何产品代码。
- **CL 前缀**: `nop-graph-duckdb-CL-0NN`（维度集外线索编号）
- **基线**: `_tmp/cra-baseline-nop-graph-duckdb.txt`（C1 静态可变字段直接命中 0——本审计以实例字段/单例/逃逸形态为面）。反向证实：见文末「计数口径」。

---

## 一、五类载体清单表

### 载体 A：singleton bean 实例字段

| # | 载体（字段 × 文件） | 写者 | 读者 | 同步机制 | 判定 |
|---|---|---|---|---|---|
| A1 | `DuckDbEngine.connections`（`final ArrayList<TrackedConnection>`，内容可变）`DuckDbEngine.java:59` | `track()`（每次 open* 调用线程）、`close()`（Lifecycle 线程） | `close()` 快照 | `synchronized(this)`（track L213 / close L105） | **每操作原子成立，但 checkClosed→openConnection→track 复合序列与 close 不原子 → C1-002** |
| A2 | `DuckDbEngine.closed`（`volatile boolean`）`DuckDbEngine.java:60` | `close()` L109 | `checkClosed()` L241（openMemory/openFile 入口） | volatile 读 | 可见性安全；但守卫读与后续慢区（JDBC connect）+ track 不构成原子复合操作 → 归入 C1-002 |
| A3 | `DuckDbEngine.memoryLimit / threads / tempDirectory`（protected 非 final String ×3）`DuckDbEngine.java:50-57` | IoC 容器 `@InjectValue` init-once | `applySettings()` / `configFingerprint()`（后续所有 open* 调用线程） | 无（依赖容器发布 + 约定不改写） | 核实安全（平台标准注入模式；javadoc L34 声明 "resolved once at bean creation"，String 不可变；无 final 属约定性约束，按误报校准不立案） |
| A4 | `DuckDbSqlTaskStep.engine`（private 非 final）`DuckDbSqlTaskStep.java:51` | IoC setter 注入 init-once（beans.xml 单例 `nopDuckDbSqlTaskStep`） | 任务执行线程（共享单例 `execute()` 多线程并发） | 无（容器发布） | 核实安全（javadoc L45-46 明确 stateless 契约；已核实 `execute()` 全路径无实例状态，连接 try-with-resources 局部持有） |

### 载体 B：static 可变字段

| # | 载体 | 写者 | 读者 | 同步机制 | 判定 |
|---|---|---|---|---|---|
| B1 | **无**。单元内全部 11 个 static 字段均 `static final`（PageRank 2 常量、LeidenDetector.LOG、DuckDbEngine.LOG/3 个 marker 串/OPEN_FILES 引用、DuckDbSqlTaskStep.STEP_TYPE、DuckDbFiles.TABLE_EXISTS_TABLE、NopDuckDbException.serialVersionUID） | — | — | — | 反向证实基线「静态可变字段直接命中 0」；无 static SimpleDateFormat/Random（LeidenDetector L108 为每次调用 `new Random()`，无共享）；无 static 非线程安全工具 |

### 载体 C：全局注册表/缓存

| # | 载体 | 写者 | 读者 | 同步机制 | 判定 |
|---|---|---|---|---|---|
| C1 | `DuckDbEngine.OPEN_FILES`（`static final ConcurrentHashMap<String, FileEntry>`，**内容可变**）`DuckDbEngine.java:48`；值对象 `FileEntry{final String fingerprint; AtomicInteger refs}`（L251-258） | `registerOpen()` L218 / `releaseOpen()` L228（任意 open*/close 调用线程） | `openFile()` 检查 L82-89（任意线程） | CHM + `compute()` 原子（注册/释放面）；但检查（get+比较）与注册（compute）为两个独立原子区 → **检查-然后-行动非原子 → C1-001**。FileEntry 经 CHM 安全发布，fingerprint final，可见性无问题 |
| C2 | DuckDB native 层文件锁（进程间单写者，经 `LOCK_MARKER` L41 错误翻译） | JVM 外 | `openConnection()` L140 | 域外机制（native lock） | 核实安全（错误翻译面，非本模块持有的可变状态） |

### 载体 D：ThreadLocal

| # | 载体 | 判定 |
|---|---|---|
| D1 | **无**。grep（模式见计数口径）命中 0 | 反向证实基线 C1c ThreadLocal = 0 |

### 载体 E：集合/可变对象参数逃逸与发布形态

| # | 载体 | 写者 | 读者 | 同步机制 | 判定 |
|---|---|---|---|---|---|
| E1 | `Edge.attrs`（**非 final 非 volatile 可变字段**，`getAttrs()` 返回内部 map 引用无防御拷贝）`nop-graph-api/Edge.java:23,52-58` | 构造期（如 `CodeRelationGraph` ctor L53 `setAttrs`）；API 声明的构造后注入（Edge javadoc L13「attrs 是 mutable 的，允许适配层在构造后注入领域属性」） | 跨线程图遍历消费方（`IGraph.getOutEdges/getInEdges` 的全部下游，含 nop-code-service GraphWikiExporter L261 等） | 无任何同步/可见性机制 | **→ C1-003**（声明的构造后可变用法与 InMemoryGraph「同步线程安全」声明组合后存在可见性缺口） |
| E2 | `InMemoryGraph` 内部 `HashMap<String,List<Edge>>` ×2 / `LinkedHashSet` ×2（final 引用，内容可变）`InMemoryGraph.java:22-25` | `addNode/addEdge`（synchronized） | `getOutEdges/getInEdges/nodeSet/edgeSet`（synchronized + **防御拷贝** L47/53/57/61） | `synchronized` 方法（锁 this） | 结构面核实安全（ Happens-before 经同一监视器；返回副本杜绝集合逃逸）；但返回列表中的 **Edge 元素是共享实例**（元素级别名，见 E1/C1-003）；锁 this 形态 → `nop-graph-duckdb-CL-002`（C2） |
| E3 | 结果持有者：`CommunityResult` / `GraphDiff` / `BfsResult` / `CriticalNodeResult` / `ImpactResult` / `CommunityInfo`——final 字段持有集合并经 getter 原样返回 | 方法内新建集合（Bfs/GraphDiffer/ImpactPropagator/LeidenDetector/LabelPropagation 均先填后建）或调用方集合（别名入参） | 结果消费方 | JMM final 字段冻结（构造完成前无 this 逃逸，已逐一核实） | 核实安全：final 冻结保证构造时内容的可见性；构造后无模块内改写；消费方改写属调用方所有权。别名注入（如 `diffWithCommunities` L90-91 复用 baseDiff 内部集合）为同线程内部传递，无跨线程共享证据 |
| E4 | 工具类静态方法入参（`nodes` Set / `config` 对象 / `graph`） | 调用方 | Bfs/TopologicalSort/PageRank/TarjanSCC/BetweennessCentrality/LeidenDetector/LabelPropagation/GraphDiffer/ImpactPropagator/GraphExporter/PathQueryExecutor（11 个无字段工具类，已逐一核实无实例/static 可变字段，「无状态」声明成立） | 无（线程封闭） | 核实安全：所有工具方法对入参只读（遍历/contains，无 put/add 写回调用方集合——入参别名修改为 0 处）；返回值全部为方法内新建集合，无内部状态逃逸。BetweennessCentrality L58 `new HashMap<>(scores)` 额外防御拷贝了 JGraphT 内部 scores |
| E5 | 可变 fluent 配置：`PathQuery` / `LeidenConfig` / `ImpactConfig` | 调用方构建线程 | `PathQueryExecutor.execute` / `LeidenDetector.detect` / `ImpactPropagator.propagate` 执行线程 | 无 | 核实安全：单线程构建-执行所有权模型（典型 builder 模式），无跨线程共享证据；执行器只读配置字段 |
| E6 | `DuckDbSqlTaskStep.InputResolver`（非 static 内部类，持 `stepRt`）`DuckDbSqlTaskStep.java:175` | 每次 `execute()` 新建 | 同一次执行 | 线程封闭 | 核实安全（per-call 实例，无跨调用共享） |

---

## 二、发现条目

### [C1-001] OPEN_FILES 配置冲突守卫为非原子检查-然后-行动，快速失败保证存在竞态窗口

- **文件**: `nop-duckdb/src/main/java/io/nop/duckdb/DuckDbEngine.java:82-99`（检查 L82-89、注册 L98）
- **证据片段**:
  ```java
  // L82-89（检查）与 L98（注册）之间隔着慢速 JDBC 连接建立（L91-97）
  FileEntry entry = OPEN_FILES.get(normalized);
  if (entry != null && !entry.fingerprint.equals(fingerprint)) {
      throw (NopException) new NopDuckDbException(NopDuckDbErrors.ERR_DUCKDB_CONFIG_CONFLICT)
  ...
  Connection conn = openConnection(DUCKDB_URL_PREFIX + filePath, filePath);  // 慢区：数百 ms 量级
  ...
  registerOpen(normalized, fingerprint);   // 原子 compute，但检查在区外
  ```
- **严重程度**: P3
- **并发场景**: 同一 JVM 内两个线程（或两个 engine 实例各自的工作线程）对**同一 .duckdb 文件**并发调用 `openFile`，携带不同的配置字符串（nop.duckdb.memory-limit / threads / temp-directory 任一不同）。
- **触发时序**: T1 `openFile(f, cfgA)` 执行到 L82 读 `entry == null`（T1 尚未注册）→ T2 `openFile(f, cfgB)` 执行到 L82 同样读到 `entry == null` → 两个守卫检查都放行 → T1/T2 各自进入慢速 `openConnection`。子后果 (a)：T2 在 native 层被 DuckDB 以 "different configuration" 拒绝，经 L133-139 翻译为同一错误码 `nop.err.duckdb.config-conflict`——仅失败点后移、错误路径不同，无行为违约。子后果 (b)：cfgA/cfgB 字符串不同但 DuckDB 归一化后语义相同（如 "256MB" vs "268435456"）时 native 放行，T2 的 `registerOpen` 走 `v.refs.incrementAndGet()` 分支——注册表在 fingerprint=A 条目下记账了一条 cfgB 连接；此后第三个 cfgB 字符串的 open 会被 fingerprint=A 拒绝（保守性拒绝，语义仍安全）。双方 close 后 refs 归 0 条目正常清除，无永久污染。
- **现状**: IDuckDbEngine 契约（`IDuckDbEngine.java:14-15`）承诺 "fails fast with nop.err.duckdb.config-conflict when a conflicting open is detected"；JVM 级注册表作为快速失败层，但其检查（get+equals）与注册（compute）不在同一原子区内，竞态窗口为一次完整 JDBC connect 时长。
- **风险**: 不修复时，冲突检测从「JVM 层即时失败」退化为「native 层延迟失败」（同错误码，检测时机不可控）；子后果 (b) 场景下注册表 fingerprint 记账与实际存活连接配置短暂不一致，产生保守方向的误拒。无数据损坏、无资源泄漏（注册在连接成功之后，失败路径不注册）。
- **建议**: 将检查纳入注册的同一原子区：把「冲突检查 + 注册」合并为一次 `OPEN_FILES.compute(normalized, (k, v) -> {...冲突则抛 NopException...})`，或引入 per-path `ConcurrentHashMap<String, Object>` 锁条目将 check-then-act 串行化；连接建立仍可在锁外，注册/校验在锁内补检一次。若接受现状，建议在 `OPEN_FILES` 字段 javadoc 注明「检查为尽力快速失败，最终裁决在 native 层」，消除契约歧义。
- **失效面枚举**（F3 同族清扫，grep `OPEN_FILES` 全类 3 个触点 + 守卫同族）: ① `registerOpen` L218-226（`compute` 原子）——安全；② `releaseOpen` L228-238（`compute` 原子，decrementAndGet 判零）——安全；③ `openFile` L82-98 检查-注册对——本发现；④ `openMemory` L62-73——不触注册表（filePath null），无此形态；⑤ `close()` L105-111 幂等守卫（synchronized + volatile）——原子成立。同族仅 ③ 一处缺陷。
- **信心水平**: 确定（交错序列具体可复现；后果已按 (a)/(b) 两分支限定）
- **误报排除**: 这不是「synchronized 集合迭代点不在锁内」类门禁问题的重复报告（OPEN_FILES 无迭代点）；也不是对 Nop 平台注入模式的误报校准命中——被审对象是自建注册表的复合操作原子性，属 C1 清单第 4 步的显式检查项。
- **复核状态**: 已复核（C1 维度独立复核 2026-10-22）——保留 P3；证据/语义/判级重核成立，本维度独立主登记。见文末「维度复核结论」。

### [C1-002] close() 与 openFile/openMemory 的生命周期竞态：泄漏 JDBC 连接与 OPEN_FILES 引用

- **文件**: `nop-duckdb/src/main/java/io/nop/duckdb/DuckDbEngine.java:62-73（openMemory）、75-100（openFile）、102-120（close）、213-216（track）、240-245（checkClosed）`
- **证据片段**:
  ```java
  // openFile：checkClosed 与 track 之间跨越慢速 openConnection + registerOpen，两者无共同锁
  public Connection openFile(String filePath) {
      checkClosed();                                    // L77：读 volatile closed，无锁
      ...
      Connection conn = openConnection(DUCKDB_URL_PREFIX + filePath, filePath);  // L91：慢区
      ...
      registerOpen(normalized, fingerprint);            // L98：注册引用
      return track(conn, normalized);                   // L99：synchronized(this) 加尾
  }
  public void close() {
      List<TrackedConnection> toClose;
      synchronized (this) {
          if (closed) { return; }                       // L106-107：幂等守卫
          closed = true;
          toClose = new ArrayList<>(connections);
          connections.clear();                          // L111：清空后 track 仍可再 add
      }
  ```
- **严重程度**: P2
- **并发场景**: 共享单例 `nopDuckDbEngine`（beans.xml `destroy-method="close"`）——容器关闭/Lifecycle 线程执行 `close()` 的同时，任务线程（`DuckDbSqlTaskStep.execute` 或直接调用方）正在 `openFile/openMemory` 中段。
- **触发时序**: T1 `openFile` 通过 `checkClosed()`（closed=false）→ 进入 L91 慢速 JDBC connect → T2 `close()` 完整执行：置 closed=true、快照并清空 connections、关闭既有连接、返回 → T1 恢复：`registerOpen` 注册 refs → `track()` 将新连接 add 进**已清空的列表**并返回 → 该连接此后无人关闭：`close()` 幂等守卫使其再也不会被遍历到。
- **现状**: `checkClosed()` 守卫与 `track()` 入账分处复合序列两端，中间隔着无锁慢区；`track`/`close` 各自的 `synchronized(this)` 只保证单操作原子，不构成 check-then-act 的原子性。`connections` 列表与 `OPEN_FILES` 引用双双泄漏。
- **风险**: ① 泄漏的 DuckDB 连接持续持有 .duckdb 文件锁，后续其它进程/引擎实例对该文件 open 将得到 `nop.err.duckdb.file-locked`（对使用方表现为莫名的持久性失败，直到 GC 或进程退出）；② `registerOpen` 已加的 refs 永不释放（`releaseOpen` 仅在 close 遍历 tracked 连接时调用），`OPEN_FILES` 条目在 JVM 存活期内滞留，且其残留 fingerprint 会让同路径后续**不同配置**的 open 收到虚假的 `nop.err.duckdb.config-conflict`——由瞬时竞态固化为 JVM 级持久错误状态。现有测试（TestDuckDbSingleWriter）覆盖并发 openFile，但 close 均在工作线程完成后执行，本竞态无测试保护。
- **建议**: 在 `track()` 的 `synchronized(this)` 区内复检 `closed`，已关闭则立即关闭传入连接并抛 `ERR_DUCKDB_ENGINE_CLOSED`（同时在抛出前 `releaseOpen` 回滚已注册 refs）；或以「engine 级生命周期锁」包裹 checkClosed→track 全程（openConnection 保持在锁外，入账前二次确认）。优先复用现有 `closed` volatile 字段做二次守卫，改动面最小。
- **失效面枚举**（F3 同族清扫，同形态入口逐一核对）: ① `openMemory` L62-73 —— 同形态（checkClosed→openConnection→track），同样受影响；② `openFile` L75-100 —— 本发现主现场；③ `checkClosed` L240-245 自身（volatile 读 + 抛错）——原子，安全；④ `close` 幂等守卫 L105-111（synchronized + volatile 写）——原子，安全；⑤ `DuckDbSqlTaskStep.execute` L85 try-with-resources —— 步骤侧连接关闭正确，不受本缺陷影响但会因 engine 层泄漏间接受害。同族 2 处缺陷入口（①②），同根因合并为 1 个发现。
- **信心水平**: 确定（交错序列与后果均可静态推演；窗口宽度为一次 JDBC connect）
- **误报排除**: 不是「synchronized 方法锁 this」类 C2 问题的错位报告（锁的正确性本身成立，缺陷在复合操作跨度）；也不属于 Nop 平台 `destroy-method`/单例模式的误报校准命中——被审对象是 engine 自身生命周期协议的原子性缺口，且 beans.xml 的 `destroy-method="close"` 使关闭-与-打开并发成为真实生命周期场景而非纯理论。
- **复核状态**: 已复核（C1 维度独立复核 2026-10-22）——保留（真缺陷），但与 C6-002 为同一缺陷面（C1 侧无独立证据增量），处置建议并入 C6-002 主登记、裁级差异留合并裁定归一。见文末「维度复核结论」。

### [C1-003] Edge.attrs 为非 final 非 volatile 可变字段，声明的「构造后注入」用法与图实现线程安全声明组合后存在可见性缺口

- **文件**: `nop-graph/nop-graph-api/src/main/java/io/nop/graph/api/Edge.java:13,23,52-58`；关联 `nop-graph/nop-graph-core/src/main/java/io/nop/graph/impl/InMemoryGraph.java:18`
- **证据片段**:
  ```java
  // Edge.java
  * attrs 是 mutable 的，允许适配层在构造后注入领域属性。   // L13：声明构造后可变
  private Map<String, Object> attrs;                       // L23：非 final 非 volatile

  public Map<String, Object> getAttrs() {
      return attrs != null ? attrs : Collections.emptyMap();  // L52-54：返回内部 map 引用
  }
  public void setAttrs(Map<String, Object> attrs) {         // L56-58：无任何同步
      this.attrs = attrs;
  }
  // InMemoryGraph.java:18
  * 这是同步线程安全的实现。                                 // 结构级声明的 blanket 线程安全
  ```
- **严重程度**: P2
- **并发场景**: 按声明的用法：T1（构建/enrich 线程）对已存入图的共享 Edge 调用 `setAttrs(m)`；T2（查询/导出线程）经 `InMemoryGraph.getOutEdges()`（synchronized，返回 Edge 元素共享的副本列表）读取同一 Edge 的 `getAttrs()`。`InMemoryGraph` 的监视器只覆盖图结构（map/list），不覆盖 Edge.attrs 字段写。
- **触发时序**: T1 在图构建完成后对某共享 Edge 执行 `setAttrs(map)`（无同步，普通字段写）→ T2 调用 `getOutEdges(nodeId)`（进入/退出 synchronized 块）并读取该 Edge 的 `getAttrs()` → T2 对 T1 的写无 happens-before，可能读到 stale null（拿到 `Collections.emptyMap()`）或旧 map 引用；若 T1 在发布 map 后继续改写其内容，T2 还可能观察到 map 的中间状态。写不出该时序的替代情形不存在——字段机制层面缺口确定存在；仅当所有调用方遵守「仅构造期写 + 持有者安全发布」时才不触发（仓库内当前写点 `CodeRelationGraph` ctor L53 满足此约束）。
- **现状**: Edge 是 api 契约面的统一边类型，javadoc 明文允许构造后注入；唯一自带线程安全声明的实现 `InMemoryGraph` 的同步只达结构级，元素级 attrs 写路径完全在监视器外。API 未声明 Edge 的线程安全/可见性契约（final/不可变约定均无）。
- **风险**: 下游按 javadoc 使用「构造后注入」即落入数据竞争，且失败形态是静默的 stale 读（不抛错、难复现）。仓库内当前无构造后写点（唯一写点 CodeRelationGraph 在构造期），故现网无既发错误行为；风险对象是 api 契约面引导的未来消费方——nop-graph-api 属跨模块公共 API（protected area）。
- **建议**: 三选一：(1) 最小改——将 `attrs` 声明为 `volatile`（写频率低，代价可忽略），并在 javadoc 补一句可见性契约；(2) 契约收窄——把 javadoc L13 的「构造后注入」改为「必须在图发布给其它线程前完成注入」，`InMemoryGraph` 的线程安全声明同步限定为「结构级，Edge 元素按 Edge 自身契约」；(3) 若希望保留可变注入语义，提供 `synchronized` 的 setAttrs 或规定注入必须经图监视器。推荐 (1)+(2) 组合。
- **失效面枚举**（F3 同族清扫，grep `setAttrs|getAttrs` 限定 `io.nop.graph.api.Edge` 生态）: ① `Edge.attrs` 本字段——本发现；② `Edge` 其余 4 字段（sourceId/targetId/weight/type）全部 final——冻结安全，且 equals/hashCode 不读 attrs（L15-16 值语义声明），可安全入 Set；③ api 其余结果持有者（CommunityResult/GraphDiff/BfsResult/CriticalNodeResult/ImpactResult/CommunityInfo/NodeScore/CommunityChange/ImpactedNode）字段全 final——核实安全（见载体 E3）；④ 可变配置 PathQuery/LeidenConfig/ImpactConfig——线程封闭所有权，核实安全（见载体 E5）；⑤ 仓库内 Edge.attrs 生产写点唯一：`nop-code-core/CodeRelationGraph.java:53`（构造期）——当前安全；生产读点 3 处（nop-code-service GraphQuestionGenerator L57 / GraphWikiExporter L261 / SurprisingConnectionAnalyzer L62）——只读消费。同族仅 ① 一处缺陷。
- **信心水平**: 字段级缺口——确定；实际触发影响——很可能（依赖未来消费方按声明用法使用）
- **误报排除**: 不是「BizModel 返回实体对象」「内部接口返回 core 模型」类误报校准命中——问题不在返回可变对象本身（那是 E3 已核实安全的平台惯例），而在**一个显式声明构造后可变的字段没有任何可见性机制**，且与同单元另一个类的 blanket「线程安全」声明组合后构成契约矛盾，属 C1 载体 E（逃逸形态）的显式检查项。
- **复核状态**: 已复核（C1 维度独立复核 2026-10-22）——保留 P2；全部 file:line 证据（含唯一写点 CodeRelationGraph.java:53 构造期、3 个只读读点）逐点重核成立，本维度独立主登记（protected area，修复须走 plan-first）。见文末「维度复核结论」。

---

## 三、核实安全项汇总（记表不立案）

| 项 | 证据 | 结论 |
|---|---|---|
| 11 个算法/工具类「无状态」声明（Bfs/TopologicalSort/BetweennessCentrality/LeidenDetector/PathQueryExecutor/PageRank/LabelPropagation/TarjanSCC/GraphDiffer/ImpactPropagator/GraphExporter） | 逐文件核实：0 实例字段、0 static 可变字段（仅 static final 常量/LOG）、私有构造器 | 声明成立 |
| 工具类入参别名 | 所有静态方法对 `nodes`/`graph`/`config` 只读，无写回调用方集合的操作 | 无别名修改 |
| 工具类返回集合逃逸 | 返回值均为方法内新建集合；BetweennessCentrality L58 对 JGraphT 内部 scores 防御拷贝 | 无内部状态发布 |
| InMemoryGraph 结构级同步 | 4 个读访问器全部 synchronized + 防御拷贝（L47/53/57/61） | 结构面安全；元素级 attrs 见 C1-003 |
| DuckDbEngine.closed volatile | L60 + L109 写 + L241 读 | 可见性安全（复合操作缺口另立案 C1-002） |
| FileEntry/TrackedConnection 不可变值对象 | L251-258 / L260-268 全 final 字段，经 CHM/synchronized 发布 | 安全发布 |
| IoC init-once 注入字段（DuckDbEngine 配置 ×3、DuckDbSqlTaskStep.engine） | @InjectValue/setter 注入 + 容器发布；javadoc 声明 fixed/stateless | 平台标准模式，核实安全（误报校准） |
| 构造器逃逸（C1 步骤 3） | 三个有状态类构造器均无 this 逃逸（无线程启动/监听器注册/字段发布）；DirectedGraphAdapter 匿名类在构造完成后返回 | 无不安全发布 |
| long/double 非原子读写、`++` 复合递增 | 共享对象无双精度可变字段；计数一律 AtomicInteger（FileEntry.refs） | 无此类形态 |

## 四、维度集外强信号（CL 条目）

### [nop-graph-duckdb-CL-001] runWithTimeout 超时后幽灵线程：per-call 非守护单线程池 + CWTS 算法不响应中断（C4/C3 形态）

- **文件**: `nop-graph/nop-graph-core/src/main/java/io/nop/graph/algorithm/LeidenDetector.java:196-204`（调用点 L112-117；`LeidenConfig.timeoutMs` 默认 30000，即默认走超时路径）
- **证据片段**:
  ```java
  private static <T> T runWithTimeout(Callable<T> task, long timeoutMs) throws Exception {
      ExecutorService executor = Executors.newSingleThreadExecutor();   // 每次调用新建非守护线程
      try {
          Future<T> future = executor.submit(task);
          return future.get(timeoutMs, TimeUnit.MILLISECONDS);          // 超时抛 TimeoutException
      } finally {
          executor.shutdownNow();                                       // 中断，但 Leiden 纯计算循环不查中断
      }
  }
  ```
- **信号说明（C4/C3）**: 超时后异常被 `runLeiden` L136-139 捕获并回退 LabelPropagation，而被放弃的 `leiden.findClustering(network)` 仍在池线程上继续跑（CWTS 算法为纯 CPU 循环，不轮询中断状态）——幽灵任务持续消耗 CPU 至自然结束；线程为非守护，会阻止 JVM 退出；且每次 detect 新建/销毁线程。建议 C4 复核：改用共享受控 executor + 任务内协作取消点，或改守护线程并评估超大图下的 CPU 占用预算。

### [nop-graph-duckdb-CL-002] 锁 this 公开监视器（C2 形态）

- **文件**: `nop-graph/nop-graph-core/src/main/java/io/nop/graph/impl/InMemoryGraph.java:27-69`（11 个 public synchronized 方法）；`nop-duckdb/src/main/java/io/nop/duckdb/DuckDbEngine.java:105,213`
- **信号说明（C2）**: 两类的监视器均为公开可达的 `this`，外部代码可 `synchronized(graph/engine)` 干预其锁可用性（或对其 wait/notify），与「private 锁对象」惯例不一致；DuckDbEngine 侧若有人持 engine 监视器做慢操作会直接阻塞 close/track。建议 C2 复核锁对象私有化与锁扩展面。

（弱信号，不立案仅备忘：`DuckDbFiles.readXlsx` L72-73 `deleteOnExit()` 在长驻进程中累积 JVM 级文件登记且临时文件延迟到 JVM 退出才清理；当前无 main 代码调用方，未达立案阈值，留给 C5/C6 复核。）

## 五、计数口径

- 静态字段全量清单：`grep -rnE "^\s*(private|protected|public)?\s*static\s" --include="*.java" nop-graph/nop-graph-api/src/main nop-graph/nop-graph-core/src/main nop-duckdb/src/main | grep -E ";\s*$" | grep -vE "...方法签名"` → 11 条，全部 `static final`；**static 非 final 字段 = 0**（与基线 C1a/C1 一致）。
- ThreadLocal：`grep -rnE "ThreadLocal|SimpleDateFormat|Collections\.synchronized|GlobalCacheRegistry|ContextualizedRegistry" <三模块 src/main>` → 0 命中；`new Random()` 命中 1（LeidenDetector.java:108，每次调用新建，非共享）——与基线 C1c=0 一致。
- 发现计数：3 条（P0:0 / P1:0 / P2:2 / P3:1）；CL 线索：2 条（另附 1 条弱信号备忘）。
- 所有行号以 commit 6934df7ad3（基线头部标注）live code 为准。

---

# C1 深挖第 2 轮

- **审计单元**: 同第 1 轮（nop-graph + nop-duckdb，src/main 全单元）；本轮按扩大面授权另核读 nop-code 的 3 个 IGraph 实现（`CodeRelationGraph`/`DbCodeRelationGraph`/`CodeCallGraph`）、`DirectedGraphAdapter` 与 nop-duckdb 测试装配（仅作行为证据，不作发现对象）。
- **本轮扩大面**: ① InMemoryGraph/CodeRelationGraph 等图结构实现的并发声明与实现一致性（blanket「线程安全」声明逐实现核）；② api 契约面可变对象（IGraph/Edge/结果 DTO）的可变暴露复检；③ static final 可变对象（基线 11 处逐个核内容可变性）；④ DuckDbFiles/SqlTaskStep 静态工具态。
- **盲审声明**: 本轮为深挖追加轮——仅可读本维度第 1 轮文件（前轮 3 条发现 + 2 条 CL 不重复报告）；仍禁读其他维度文件与 `ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`、`ai-dev/backlog/`；全部结论以 live code（commit 6934df7ad3）为准；只读审计，未修改任何产品代码。

## 一、扩大面核查记录（记表不立案）

| 扩大面 | 核查对象与证据 | 判定 |
|---|---|---|
| ① IGraph 实现 ×5 逐实现核 | `InMemoryGraph`（9/9 public 方法 synchronized + 4 个读访问器防御拷贝，blanket「这是同步线程安全的实现」声明在**结构层**成立；元素层缺口已由前轮 C1-003 覆盖，本轮未发现新的结构层缺口：无迭代器逃逸、无 check-then-act 跨方法复合、`addEdge` 两重载可重入自洽）；`CodeRelationGraph`（**无并发声明**；全 final 字段 + 构造期唯一写点 L53 `setAttrs` + 无 this 逃逸 → JMM final 冻结使无同步跨线程读安全；`getOutEdges/getInEdges/nodeIds` 返回 unmodifiable 视图，内部集合构造后不再可变，视图稳定）；`DbCodeRelationGraph`（final init-once ×3、per-call DAO 点查 + per-call 投影，无实例可变状态；`traverseBounded` 有 maxDepth 守卫，visited 集合为方法局部）；`CodeCallGraph`（单一 final 委托字段，per-call 新建 Edge）；`DirectedGraphAdapter`（匿名 IGraph 每调用新建 Edge，无共享状态，亦无线程安全声明——无 blanket 声明即无声明-实现矛盾） | 无新增发现 |
| ② api 契约面可变暴露复检 | 9 个结果 DTO（CommunityResult/GraphDiff/BfsResult/CriticalNodeResult/ImpactResult/CommunityInfo/NodeScore/CommunityChange/ImpactedNode）字段全 final（逐文件核实）；getter 返回活集合引用属 per-call 结果所有权模型（前轮 E3 判定复核后维持）；3 个可变配置 PathQuery/LeidenConfig/ImpactConfig 线程封闭（E5 维持）；全仓 grep `setAttrs|getAttrs()`：`io.nop.graph.api.Edge` 生态生产写点仍唯一（`CodeRelationGraph.java:53` 构造期），`getAttrs().put/remove/clear` 变异点 0，读点 3 处全只读——前轮 C1-003「仓库内无构造后写点」论断复核成立 | 无新增发现 |
| ③ static final 内容可变性（基线 11 处逐个核） | PageRank 2 个 double 常量、LeidenDetector.LOG、DuckDbEngine.LOG + 3 个 String marker、DuckDbSqlTaskStep.STEP_TYPE、DuckDbFiles.TABLE_EXISTS_TABLE（String 常量）、NopDuckDbException.serialVersionUID——内容全部不可变；唯一内容可变 = `DuckDbEngine.OPEN_FILES`（CHM）。对其账本生命周期深查发现 **C1-R2-001**（与其配套的实例账本 `connections` 一并复核） | 1 项立案 |
| ④ DuckDbFiles/SqlTaskStep 静态工具态 | `DuckDbFiles`：唯一字段为 String 常量，全部方法以入参 Connection 线程封闭执行，per-call 局部变量（含 readXlsx 临时文件），无 static 可变态；`readExternal` 的 tableExists→CREATE TABLE 检查-然后-行动在单连接线程封闭契约下无 JVM 内竞态，失败路径 fail-closed（SQL 异常 → IO_FAILED）。`DuckDbSqlTaskStep`：唯一实例字段 `engine`（IoC init-once，前轮 A4 已核）；`InputResolver` per-call；全方法局部状态 | 无新增发现 |

## 二、发现条目

### [C1-R2-001] OPEN_FILES refs 与 connections 账本仅在 engine.close() 释放：消费方逐连接正常关闭路径只增不减，已关闭文件被 pin 至 engine 存活期，触发虚假 config-conflict 与长时运行无界增长

- **文件**: `nop-duckdb/src/main/java/io/nop/duckdb/DuckDbEngine.java:59,62-73,75-100,102-120,213-216,218-238`；关联消费方 `nop-duckdb/src/main/java/io/nop/duckdb/DuckDbSqlTaskStep.java:85,139-141`；意图契约证据 `nop-duckdb/src/test/java/io/nop/duckdb/TestDuckDbEngine.java:151-154`
- **证据片段**:
  ```java
  // DuckDbEngine：track 返回裸 JDBC 连接，releaseOpen 只在 close() 循环中调用
  private synchronized Connection track(Connection conn, String normalizedFilePath) {  // L213
      connections.add(new TrackedConnection(conn, normalizedFilePath));                // 只增不删
      return conn;                                                                     // 返回裸连接，非代理
  }
  public void close() {                                            // L103：唯一释放点
      ...
      for (TrackedConnection tc : toClose) {
          IoHelper.safeCloseObject(tc.connection);
          if (tc.filePath != null) { releaseOpen(tc.filePath); }      // L113-118：仅 shutdown 期执行
      }
  }
  // DuckDbSqlTaskStep.java:85 —— 生产装配下的标准消费路径：逐连接关闭、engine 长驻
  try (Connection conn = openConnection(dbPath)) { ... }              // 关闭的是裸连接，engine 不知道
  ```
- **严重程度**: P1
- **并发场景**: 非竞态，为**确定性时序**（更严格的证据标准已满足）：长驻单例 `nopDuckDbEngine`（`app-duckdb.beans.xml:13-15`，singleton + `destroy-method="close"`）服务任意多次 `DuckDbSqlTaskStep` 执行；以及 `DuckDbEngine` javadoc L44-46 明文覆盖的多 engine 实例场景（测试 VFS `app-test.beans.xml`/`app-parity.beans.xml` 与各测试均以 `new DuckDbEngine()` 共存多实例，共享 static `OPEN_FILES`）。
- **触发时序**: （a，无界增长）T1 执行 `DuckDbSqlTaskStep.execute` → `engine.openFile(f)`/`openMemory()` → `track()` 将 TrackedConnection 追加进 `connections` → 步骤结束经 try-with-resources 关闭**裸连接** → `connections` 无任何移除路径（`close()` 之外无写者删除），`OPEN_FILES` 的 refs 亦无递减调用点（`releaseOpen` 全类唯一调用点在 close 循环 L116）→ 每次任务执行净增 1 条 TrackedConnection（含已关闭 Connection 对象的滞留引用），每个出现过的新 dbPath 净增 1 条 FileEntry，直至进程退出时的 engine.close()。（b，虚假冲突）T1 在单例 engine A（配置 X）上完成任务并关闭文件连接后，T2 创建配置 Y 的 engine B 并 `openFile(f)` → L83 守卫读到被 pin 的 entry（refs≥1，fingerprint=X）→ 抛 `nop.err.duckdb.config-conflict`（bizFatal）——而此刻 f 上并无存活连接，DuckDB native 层本会放行。
- **现状**: 注册/释放原语（`registerOpen`/`releaseOpen` 的 `compute`）本身原子正确（前轮已核）；缺陷在**释放路径的挂接点**：refs 语义是「存活连接计数」（构造 `AtomicInteger refs` 初值 1、判零清除），但唯一递减点挂在 engine 生命周期而非连接生命周期；`TrackedConnection.filePath` 字段的存在说明逐连接释放的数据通路已备好，只是从未在消费方关闭时被调用。`TestDuckDbEngine.java:151` 注释钉定了意图契约——「after the holder closed, reopening with a different config is legitimate (refs released)」——该测试之所以通过，仅因测试显式调用了 `writer.close()`/`first.close()`；在生产 singleton 装配（engine 存活期 = 应用存活期，无逐任务 close）下，该契约对每一条真实任务路径都不成立。
- **风险**: ① 长时运行必然触发的无界增长（F4 无界账本族）：单条约 40B–KB 级（TrackedConnection + 已关闭 DuckDB Connection 对象滞留），增速 = 任务执行频次；分析服务连续运行周/月级后账本单调膨胀且永不在运行期回收。② 虚假 config-conflict：多 engine 共存为受支持场景（javadoc 明文 + 测试装配），顺序复用同一 .duckdb 文件（前任务已完全结束）被 registry 判为持久性 bizFatal 冲突，由可恢复的瞬时状态固化为 engine 存活期内的永久拒绝。③ `close()` shutdown 路径需遍历全部历史 TrackedConnection，停机时长随历史执行次数线性增长。
- **建议**: 将释放挂接点从 engine 生命周期下沉到连接生命周期，二选一：(1) **最小改动**——`track()` 改为返回 `Connection` 代理（覆写 `close()`：先关闭底层连接，再从 `connections` 移除自身并按 `filePath` 调 `releaseOpen`；幂等可复用 `closed` 标志），`TrackedConnection.filePath` 通路原样复用，消费方 try-with-resources 语义不变；(2) **契约显式化**——`IDuckDbEngine` 增加 `void close(Connection)` API 并在 javadoc 声明「直接 `Connection.close()` 不会释放 registry 记账」的现状限制。任一方案下同步修正 `TestDuckDbEngine.java:151` 场景为「不调 engine.close 的单例形态」以获得回退必红保护。
- **失效面枚举**（F3 同族清扫，grep `connections|registerOpen|releaseOpen|track\(` 全类 + 静态账本同族）: ① `openMemory`→`track(conn, null)` L72——`connections` 净增（filePath=null 不触 OPEN_FILES），同根因受影响；② `openFile`→`registerOpen`+`track` L98-99——`connections` 与 `OPEN_FILES` 双账本净增，本发现主现场；③ `close()` L102-120——唯一释放点（shutdown 期语义），原语安全；④ `registerOpen`/`releaseOpen` L218-238——`compute` 原子成立，问题仅在 ④ 的调用域被限定于 ③；⑤ `DuckDbSqlTaskStep`/`DuckDbFiles`——per-call 无残留（try-with-resources / 无状态），不受影响但为泄漏的日常入口；⑥ 测试 VFS 多 engine beans（`app-test`/`app-parity`）——共享 static `OPEN_FILES`，放大 ② 的虚假冲突暴露面。同族缺陷入口 = ①②（同根因，合并 1 个发现）。
- **信心水平**: 确定（确定性时序，无交错依赖；释放路径全类枚举完备；与测试注释钉定的意图契约直接冲突）
- **误报排除**: 不是前轮 C1-002 的重复——C1-002 是 close 与 open 的**并发窗口**竞态，本条是**单线程顺序路径**上的账本生命周期缺陷，根因（释放挂接点错位）与失效面（正常成功路径）均不同。不是「有界声明的缓存」误报校准命中——`connections`/`OPEN_FILES` 无任何上限声明或淘汰策略。不是平台 `destroy-method` 单例惯例的误报——被审对象不是 bean 生命周期本身，而是自建注册表的 refs 语义（计数器判零清除逻辑的存在证明其意图是连接级生命周期）与实现释放点的矛盾，属 C1 载体 C（全局注册表）的显式检查项；且第 2 轮扩大面③明确要求「static final 可变对象逐个核内容可变性」，`OPEN_FILES` 是基线 11 处中唯一内容可变者。
- **复核状态**: 已复核（C1 维度独立复核 2026-10-22）——保留（真缺陷，P1 与 C6-001 定级一致）；三方核实为同一缺陷面（C6-001 + C6-R2-001 + CS-002），处置建议并入 C6 主登记、不独立计数，C1 侧证据增量（TestDuckDbEngine.java:151 意图契约）随合并移交。见文末「维度复核结论」。

## 三、终止清扫表

| 清扫项 | 本轮状态 |
|---|---|
| InMemoryGraph blanket「线程安全」声明 | 结构层成立（9/9 synchronized + 防御拷贝）；元素层归前轮 C1-003；无新增缺口——清扫完毕 |
| CodeRelationGraph / DbCodeRelationGraph / CodeCallGraph / DirectedGraphAdapter | 声明与实现一致，或无声明且实际构造后不可变/线程封闭——清扫完毕 |
| api 契约面可变暴露（DTO ×9 + 可变配置 ×3 + Edge 生态写点） | 前轮 E3/E5 判定维持，无新写点、无 attrs 变异点——清扫完毕 |
| static final 11 处内容可变性 | 10 处不可变内容；`OPEN_FILES` → 立案 C1-R2-001——清扫完毕 |
| DuckDbFiles / DuckDbSqlTaskStep 静态工具态 | 仅 String 常量与 per-call 局部；无 static 可变态——清扫完毕 |

**本轮终止**：四个扩大面全部核毕，除 C1-R2-001 外无新发现；建议不再派发第 3 轮深挖，进入维度复核阶段。本轮发现计数：1 条（P0:0 / P1:1 / P2:0 / P3:0）；累计本维度 4 条（前轮 3 条 + 本条）。

---

# C1 维度复核结论（独立复核子代理，2026-10-22）

- **复核人**: C1 维度独立复核（Phase 4）；复核基线 HEAD 6934df7ad3 与初审一致，实测工作树对 `nop-duckdb/src/main`、`nop-graph/` 无未提交改动——全部发现现状均为「新发现，未修复」。
- **复核方法**: 逐条重读源码 file:line（DuckDbEngine/DuckDbSqlTaskStep/IDuckDbEngine/Edge/InMemoryGraph/LeidenDetector/CodeRelationGraph 全文），重放计数 grep（static 字段、ThreadLocal、C2 锚点、releaseOpen 调用点），并对照 C6/CS 两维度文件与 dispatch-log 做跨维度同站点核实；允许读 cra-index（nop-duckdb 0 提及、nop-graph 仅 2.1 移交提及——本单元全部为首报，无历史重复）。只读复核 + 回填复核状态行，未修改任何既有内容与产品代码。

## 逐条判定

### [C1-001] — 保留 P3
- **证据核实**: DuckDbEngine.java:82-89 检查与 :98 `registerOpen` 确为两个独立原子区，中间隔 :91 慢速 JDBC connect；失败路径不注册（applySettings 失败 :94-96 先关连接再抛，注册在其后）与初审一致。子后果 (a) native 同错误码延迟失败（:133-139 翻译为同一 `ERR_DUCKDB_CONFIG_CONFLICT`）、(b) 保守误拒方向，均如实限定。
- **判级**: P3 恰当——无泄漏、无数据损坏，仅失败点后移/保守误拒。F3 同族枚举 5 触点重核无遗漏（registerOpen/releaseOpen compute 原子、openMemory 不触注册表、close 幂等守卫成立）。
- **独立性**: 其他维度未立案此站点（C6-CL-103a 为路径归一化别名问题，不同缺陷面），本维度独立主登记。

### [C1-002] — 保留（真缺陷），并入 C6-002 主登记
- **证据核实**: 交错序列成立——`openFile` :77 `checkClosed`（volatile 读无锁）→ :91 慢区 → :98 注册 → :99 `track`（:213-215 无 `closed` 复查）；`close` :105-112 置位+快照+清空；二次 close 在 :106-108 幂等早退，泄漏连接永无关闭点。连接泄漏（文件锁滞留→他人 `file-locked`）与 OPEN_FILES refs 残留（→虚假 config-conflict）两类后果均成立。
- **C1 侧证据独立性核实（派单重点）**: **不独立**。与 C6-002 为同一交错、同一机制（track 锁内缺一次 `closed` 复查）、同一行号集（62-77/103-120/213-216）；C1 特有的 refs 残留后果已被 C6-002 显式并入 C6-001（"残余 refs 泄漏并入 C6-001"）。CS-CL-201 为同站点第三确认。C1 侧无证据增量。
- **处置**: 本维度保留登记（缺陷为真），修复跟踪与裁级并入 C6-002 主条目；C6-002 P1 与本条 P2 的差异留合并裁定归一（C6-002 自带"可降 P2"注记，两处口径须统一为一个判级）。

### [C1-003] — 保留 P2
- **证据核实**: Edge.java:13 javadoc 明文「构造后注入」、:23 非 final 非 volatile、:52-58 getter 返回内部 map 引用/setter 无同步、equals/hashCode（:61-82）不读 attrs；InMemoryGraph.java:18 blanket「同步线程安全」声明，同步仅达结构级（实核 9 个 synchronized 方法全部快照拷贝）。仓库内唯一生产写点 `CodeRelationGraph.java:53` 在构造器（:38）内——逐点重核成立；读点 3 处（GraphQuestionGenerator:57 / GraphWikiExporter:261 / SurprisingConnectionAnalyzer:62）全只读。
- **判级**: P2 维持——nop-graph-api 属 protected area 公共 API，javadoc 主动引导不安全用法 + blanket 线程安全声明构成契约矛盾，失败形态为静默 stale 读；「现网无构造后写点」已由初审如实披露并作为降档考量，判级仍取 P2（契约指引性缺陷）合理。
- **独立性**: 全审计无其他维度登记此面，本维度独立主登记；修复须走 plan-first（跨模块公共 API）。

### [C1-R2-001] — 保留（真缺陷，P1），并入 C6 主登记
- **证据核实**: 全部成立——① `track` :213-215 只增不删，`connections` 唯一移除点 close :111；② `releaseOpen` 全类唯一调用点 :116（grep 重核 4 触点）；③ 生产消费路径 DuckDbSqlTaskStep.java:85 TWRS 关裸连接（:139-141 委托 engine），engine 无感知；④ `app-duckdb.beans.xml` 单例 + `destroy-method="close"`，engine 存活期=应用存活期；⑤ 意图契约 TestDuckDbEngine.java:151 注释原文核实（"after the holder closed, reopening with a different config is legitimate (refs released)"），且该测试仅因显式 `writer.close()`/`first.close()` 而通过；⑥ 触发后果 (b) 守卫点 :83-84 bizFatal(true) 核实。refs 语义（判零清除，:233）与唯一递减点（engine 生命周期）的矛盾为缺陷核心，定性与代码一致。
- **三方同一缺陷面核实（派单重点）**: **是**。C6-001（OPEN_FILES 只注册不释放+过期指纹误拒，P1）、C6-R2-001（connections 死条目累积，P2，C6 侧已建议与 C6-001 合并缺陷族）、CS-002（双台账 append-only，P2）覆盖同一根因（释放挂接点锚在 engine 生命周期而非连接生命周期，连接 close 权与簿记 release 权无桥接）、同两载体（静态 OPEN_FILES + 实例 connections）、同后果族（无界增长 + 虚假 config-conflict + O(N) 停机）。C1-R2-001 = 两载体并集，无新站点。
- **C1 侧证据增量**: TestDuckDbEngine.java:151 意图契约钉定 + refs 语义矛盾分析——建议随合并移交 C6 主条目补强证据；多 engine 场景（javadoc :44-46 + 测试 VFS 装配）为后果 (b) 的暴露面放大，一并移交。
- **处置**: 并入 C6 主登记（C6-001 + C6-R2-001 缺陷族，主登记归 C6 维度）；C1-R2-001 标注跨维度重述、不独立计数；P1 与 C6-001 对齐（C6-R2-001 附带修正已界定 refs 泄漏止于 engine 存活期，本条风险表述「pin 至 engine 存活期」边界正确，不受修正影响）；修复验证须双载体同测。CS-002 建议同族归并（其 C5/C7 角度后果并入同一条目）。

## R2 豁免注记复核：豁免正当

- **"P1 为跨维度重述"——成立**: C1-R2 站点（DuckDbEngine 双台账）在 C1 R2 派发前已由 C6-001（C6 R1）、CS-002（CS R1）、C6-R2-001（C6 R2）登记，本轮复核确认非新站点（详见上节三方核实）。
- **"清扫全闭"——逐面核对成立**: 扩大面①（5 个 IGraph 实现，InMemoryGraph 结构层/CodeRelationGraph final 冻结/DbCodeRelationGraph/CodeCallGraph/DirectedGraphAdapter 均有核查痕迹且与 live code 一致）；②（api 契约面 DTO×9 + 配置×3 + Edge 写读点全枚举）；③（static final 11 处——本次复核重新 grep 全量 **11 处全 static final**，唯一内容可变 OPEN_FILES 已立案，计数口径与初审精确一致）；④（DuckDbFiles/SqlTaskStep 静态工具态）。加上第 1 轮已反向证实的载体 B（static 非 final=0）与 D（ThreadLocal=0，本次 grep 重放 exit=1 无命中），以及发布安全/构造器逃逸/long-double 原子性三步检查，C1 五类载体检查单无未核面。**豁免第 3 轮正当**，dispatch-log 留痕充分。

## 批次覆盖核验（三源码根）

| 源码根 | 实测 | 与文件头口径 | 核查痕迹 |
|---|---|---|---|
| nop-graph/nop-graph-api | 14f/613 | 一致 | Edge/IGraph/DTO/Config 证据逐点核实（C1-003、载体 E2-E5） |
| nop-graph/nop-graph-core | 13f/1,482 | 一致 | InMemoryGraph/LeidenDetector（CL-001 站点 :196-204、Random :108 per-call）/算法类无状态声明核实 |
| nop-duckdb | 6f/837 | 一致 | DuckDbEngine/SqlTaskStep/IDuckDbEngine/beans.xml/测试契约全核实 |

合计 33f/2,932 逐字吻合；HEAD=6934df7ad3 与基线一致。计数锚点重放：static 字段 11 全 final ✓、ThreadLocal 0 ✓、`new Random()` 1 处（:108，每次调用新建）✓、C2 锚点 15 命中（InMemoryGraph 11 行 = 9 个 synchronized 方法 + addEdge 内 2 处 computeIfAbsent；DuckDbEngine 4 = synchronized×2 + compute×2）✓。覆盖声明全部可复现，**批次覆盖通过**。

## CL 处置

- **CL-001: 确认**（别名归并）。runWithTimeout 证据（LeidenDetector.java:196-204，本次重读核实）与机制成立，`LeidenConfig.timeoutMs` 默认 30000（LeidenConfig.java:9）核实；该信号已由 CS 批立案为 CS-001（P1）、C6 §3 有同站点记录——处置归 CS-001 主条目，CL-001 按「确认-别名归并」关闭。
- **CL-002: 确认**（带一处计数更正）。lock-this 公开监视器信号成立：InMemoryGraph 全部方法 synchronized(this)、DuckDbEngine :105/:213 亦为 this 监视器，外部可 synchronized(graph/engine) 干预锁可用性。**更正**: CL-002 所写「11 个 public synchronized 方法」实为 **9 个** synchronized 方法（11 为基线 C2 锚点行数，含 addEdge 内 2 处 computeIfAbsent；CS-sweep 的 InMemoryGraph 11 同源）；计数误差不影响信号成立性。本单元 CS 批 C2 角度仅 overlay 抽查未立案，CL-002 作为 C2 专项线索保留（留 C2/合并裁定处置）。

## 最终保留项

| 项 | 判级 | 判定 | 处置 | 现状 |
|---|---|---|---|---|
| C1-001 | P3 | 保留 | 本维度独立主登记 | 新发现，未修复 |
| C1-002 | P2（待与 C6-002 P1 归一） | 保留（真缺陷） | 并入 C6-002 主登记（同站点，C1 无独立证据增量） | 新发现，未修复 |
| C1-003 | P2 | 保留 | 本维度独立主登记（protected area，plan-first 修复） | 新发现，未修复 |
| C1-R2-001 | P1 | 保留（真缺陷） | 并入 C6 主登记（C6-001+C6-R2-001 缺陷族；不独立计数） | 新发现，未修复 |

**净效果**: 本维度独立立案 2 条（C1-001 P3、C1-003 P2），合并移交 2 条（C1-002→C6-002、C1-R2-001→C6-001/C6-R2-001 族），CL 确认 2（CL-001 归并 CS-001、CL-002 留 C2 线索）、驳回 0；R2 豁免注记复核通过；批次覆盖核验通过。无降级、无驳回。
