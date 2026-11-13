# C1 共享可变状态与可见性 — 初审报告

- **审计单元**: `nop-tools-parallel`（nop-tools/nop-rg 43f/3,274 + nop-tools/nop-find 14f/901 + nop-tools/nop-bytecode 27f/2,621；口径 = `*/src/main` Java 文件，不含 `target/` 与测试源）
- **审计维度**: C1 共享可变状态与可见性（CRA WI 6.1）
- **批次**: 全单元单批（无分片）
- **盲审声明**: 本初审为盲审——未读取 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`、`ai-dev/backlog/` 下任何历史记录；现状标注（新发现/已知未修复）留待复核阶段。全部结论以 live code 为准。
- **CL 前缀**: `nop-tools-parallel-CL-0NN`（维度集外线索编号）
- **基线**: `_tmp/cra-baseline-nop-tools-parallel.txt`（FFM/Arena/SIMD 专项节已全读）
- **方法**: `ai-dev/skills/deep-audit-prompts.md` 共享提示词前缀 + `ai-dev/skills/concurrency-resource-audit-prompt.md` C1 正文（含附录 A 发现格式、高频缺陷族 F1–F11 对照）
- **复核状态**: 全部条目为「未复核」（初审，等待独立复核）

---

## 一、五类载体清单表（逐项判定）

### 载体 1：singleton bean 实例字段

三模块均为纯库 + CLI（`grep -rn "@Inject|@BizModel|beans.xml"` 于全部 main 源 = 0 命中），无 IoC bean、无注入字段。跨线程共享的协作对象改经 executor 任务提交面：

| 共享对象 | 写者 | 读者 | 同步机制 | 判定 |
|---|---|---|---|---|
| `SearchCoordinator.search` 内 `prepared`（PreparedFinder）/`regexSearcher`（RegexSearcher） | 调用线程在 L98/L245 构造 | ForkJoinPool worker 任务（L110 lambda 捕获） | `pool.invokeAll`（L117）的 executor 内存一致性效应（提交先行于任务执行）+ 对象自身不可变（`PreparedLiteral`/`VectorPreparedLiteral` 全 final 字段；`RegexSearcher.pattern` final、`Matcher` 每次 `findAll` 新建） | 安全 |
| `FindCoordinator.find` 内 `scorer`（FuzzyFileScorer） | 调用线程 L72 构造（query 预归一化后全 final） | fixed-pool worker（`scoreShard`） | `invokeAll` 提交 HB + 类 javadoc 自述"无可变共享状态"（实测核实：仅 final 字段） | 安全 |
| `ResourceLeakAnalyzer.registry`（实例字段，跨 class/method 复用） | 构造时 `new ResourceRegistry()` | 各 `analyzeMethod` 新建的 `ResourceSemantics` | ResourceRegistry 仅含 `static final Set.of` 不可变集合，查询方法只读 | 安全 |

### 载体 2：static 可变字段

口径复现：`grep -rn --include="*.java" -E "static [A-Za-z]" nop-tools/nop-rg/*/src/main nop-tools/nop-find/*/src/main nop-tools/nop-bytecode/src/main | grep -v "static final" | grep -v "@"` 全部命中为静态**方法**声明；真实 static 非 final **字段** = 0。static final 复合对象字段（基线 "C1 static mutable fields: 8" 的对应面，逐项判定如下）：

| 字段 | 类型 | 跨线程读者 | 判定 |
|---|---|---|---|
| `CompiledGlob.CACHE`（CompiledGlob.java:24） | `ConcurrentHashMap<String,CompiledGlob>` | 任意调用 `compile` 的线程 | 安全：`computeIfAbsent`（L36）原子，无检查-然后-行动；值对象不可变 |
| `GlobMatcher.ACCEPT_ALL`（GlobMatcher.java:16） | 内持两个 `ArrayList` | `acceptAll()` 调用方 | 安全：构造后无任何写路径（`of()` 恒新建列表）；弱点仅为字段类型为可变 ArrayList，无写者则无风险 |
| `ScalarByteSearcher.INSTANCE` / `VectorByteSearcher.INSTANCE` | 无状态单例 | worker 线程 | 安全：无实例字段 |
| `ScalarByteSearcher.BYTE_FREQUENCY`（:22） | `int[256]` | `selectAnchorIndex`（worker 线程经 `PreparedLiteral`/`VectorPreparedLiteral` 构造器间接读） | 安全：静态初始化块内构建后只读，类初始化 HB 覆盖所有读者 |
| `PreparedLiteral.JAVA_BYTE` / `ScalarByteSearcher.JAVA_BYTE` | `ValueLayout.OfByte` 常量 | — | 安全（不可变常量） |
| `VectorByteSearcher.SPECIES` / `VectorPreparedLiteral.SPECIES` | `VectorSpecies` | worker 线程 | 安全（不可变） |
| `ObligationToken.TOP/PLAIN_NONNULL/PLAIN_NULL/PLAIN_MAYBE/NEW_MARKER`（:37-41） | 不可变 token（javadoc :18-21 明示 openSites 构造后不变、每次变换新建 TreeSet；merge/without 均新建） | dataflow solve（单线程） | 安全 |
| `ResourceRegistry.NEW_RESOURCE_TYPES/FACTORY_METHODS/OWNERSHIP_WRAPPERS`、`OwnershipWhitelist.WRAPPER_CONSTRUCTORS` | `Set.of` 不可变 | 分析线程 | 安全 |
| `NullnessSemantics.ASSERTIONS_DISABLED`（:48） | 哨兵 Object（仅作引用相等比较） | solve 单线程 | 安全 |
| `FindingRenderer.ORDER`（:14） | `Comparator`（无状态组合子） | CLI 主线程 | 安全 |
| `ParallelFileWalker.DEFAULT_THREADS`、各 CLI `OUTPUT_BUFFER_BYTES` 等 | 基本类型/不可变常量 | — | 安全 |

无 static SimpleDateFormat/Random/Date/Calendar（CorpusUtil 的 `Random` 为方法内局部，benchmark 面不立案）。

### 载体 3：全局注册表/缓存

| 载体 | 判定 |
|---|---|
| `CompiledGlob.CACHE` | 唯一全局缓存。原子性：`computeIfAbsent` 无竞态；容量：javadoc（:20-21）明示"CLI 场景模式数量有限且只增不减，不做淘汰"——已声明边界，无界增长不立案（C7 视角若复用为长驻库面再议，见 CL-003 备注） |
| `ServiceLoader.load(LiteralFinderProvider.class)`（SearchCoordinator.java:238-244） | 每次 `search()` 新建 ServiceLoader 与 provider 实例，无跨调用共享；`NopRgVectorLiteralFinderProvider.unavailableReason` 可变实例字段的写（`available()`/`compile()`）与读（`unavailableReason()`）均在同一线程的同一次 `resolveVectorFinder` 调用内 | 安全（当前接线） |

### 载体 4：ThreadLocal

`grep -rn "new ThreadLocal|NamedThreadLocal"` 于全部 main 源 = 0 命中（与基线 "C1 ThreadLocal: 0" 一致）。无载体，无发现。

### 载体 5：参数逃逸/隐式共享（含 FFM MemorySegment 跨线程面）

| 载体 | 写者/读者 | 同步机制 | 判定 |
|---|---|---|---|
| `MappedFileReader`（segment/arena/closed）与 `ChunkedFileReader`（nextStart/closed/arena） | 创建、`getSegment()`/`nextChunk()`、`close()` 均在同一 worker 任务的 try-with-resources 内（SearchCoordinator.java:163-178、189-223） | 线程封闭（单任务单线程）；`Arena.ofConfined()` 的 owner-thread 约束为第二道兜底（跨线程访问/关闭抛 WrongThreadException，fail-fast 非 UB） | 安全（封闭性为构造性设计；类 javadoc 未明示线程封闭契约，属文档建议项，不立案） |
| `MappedFileReader.closed` / `ChunkedFileReader.closed` 检查-然后-行动（:65-68 / :95-98） | 同一线程序列（try-with-resources 保证 close 在任务末尾） | 单线程内无交错 | 安全；若未来拆分跨线程，`closed` 检查与 `arena.close()` 存在竞态窗口（表现为 IllegalStateException，非内存不安全）——记表不立案 |
| `SearchCommand.patternBytes` 惰性缓存 | 见发现 C1-001 | 无 | **立案 C1-001（P2）** |
| `ParallelFileWalker.WalkContext.files` | 各 worker `synchronized(context){ add }`（:162-164） | 写全部在锁内；读（walk():81 复制）在 `pool.invoke` 返回后，FJP 任务完成 HB 覆盖 | 安全 |
| `WalkContext.error`（volatile）+ `recordError` 检查-然后-行动 | 各 worker 目录任务 | volatile 可见性，无原子性 | **立案 C1-002（P3）** |
| `FindCoordinator.shard` 的 `subList` 视图 | 调用线程构建、冻结后经 `invokeAll` 分发 | executor 提交 HB；backing list 构建后不再修改 | 安全 |
| `TopKCollector` | 每 worker 局部实例，主线程归并 | javadoc :20 显式声明"非线程安全"+单线程持有契约；类内不变式破坏有显式守卫（:72-75 IllegalStateException） | 安全（契约自洽） |
| `LineCursor`/`MatchAggregator`/`RegexSearcher.Utf8OffsetMap` | 每次 aggregate/findAll 新建局部实例 | 线程封闭 | 安全 |
| nop-bytecode 分析内核（ForwardSolver/NullnessSemantics/ResourceSemantics/MethodCfgBuilder/Frame） | CLI 主线程逐 class 串行；`NullflowAnalyzer`/`ResourceLeakAnalyzer`/solver/semantics 均每 method 新建；可变实例字段（pendingRefine*、suppress*、siteTypes 等）由注释标明"single-threaded per solve"并经调用链核实（NopBytecodeMain.java:121-151 串行循环） | 线程封闭 | 安全 |
| JMH benchmark 状态字段（@State(Scope.Benchmark) 各 @Param/@Setup 字段） | JMH 框架管理（@Fork(1) 默认单线程） | benchmark 面，按误报校准不立案 | 不立案 |

---

## 二、发现条目

### [C1-001] SearchCommand.patternBytes 惰性缓存为非原子的检查-然后-行动，且无安全发布保障

- **文件**: `nop-tools/nop-rg/nop-rg-core/src/main/java/io/nop/rg/core/coordinator/SearchCommand.java:19,63-68`
- **证据片段**:
  ```java
  private byte[] patternBytes; // 惰性缓存（plan 2268 F7：分块路径每文件调用一次，免重复 UTF-8 编码）
  ...
  public byte[] patternBytes() {
      if (patternBytes == null) {
          patternBytes = pattern.getBytes(StandardCharsets.UTF_8);
      }
      return patternBytes.clone();
  }
  ```
- **严重程度**: P2
- **并发场景**: `SearchCommand` 是 `SearchCoordinator.search()` 的公共输入模型（构造器已对 `globs` 做 `List.copyOf` 冻结，语义上承诺不可变）。同一线程内单次 `search()` 调用不受影响——LITERAL/VECTOR 路径在任务提交前（SearchCoordinator.java:98/:245）已在调用线程初始化该缓存，worker 在 `searchFileChunked`（:186）读取时经 `pool.invokeAll`（:117）的 executor 内存一致性效应可见；REGEX 路径 worker 不触达该字段。暴露面是**同一 `SearchCommand` 实例被两个线程并发调用 `search()`**（库形态嵌入长驻进程时共享缓存 command 是自然用法），此时两个调用线程并发执行惰性初始化。
- **触发时序**: T1、T2 各自对同一 command 实例调用 `search(command)`，同时进入 `patternBytes()`：T1 于 :64 读 `patternBytes == null` 成立 → T2 于 :64 读亦成立（T1 尚未写回）→ T1 执行 `pattern.getBytes(UTF_8)` 并于 :65 写入字段，同时 T2 执行同样序列并于 :65 覆盖写入（双写，一方数组被丢弃）→ 交叠后果一（必现）：重复编码 + 无同步的字段双写竞态；交叠后果二（JMM 违规窗口）：T2 在 :66 读到的引用可能是 T1 刚写入而未经任何 happens-before 边发布的数组，`clone()` 逐元素读取无同步保障，形式上允许观察 到未完成初始化的数组内容 → 模式字节错误 → **静默返回错误搜索结果**（非崩溃、非可见异常）。
- **现状**: 惰性缓存无 volatile、无同步块、无 final；字段为包内唯一惰性初始化点（F3 清扫见"失效面枚举"）。
- **风险**: 命令模型名义不可变、实际含隐藏可变状态，跨线程共享时（a）竞态双写浪费编码，（b）理论上的未安全发布窗口可产生静默错误结果。当前 in-tree（CLI 单命令单线程）不触发，故按"可具体化但触发前提为外部共享用法"定 P2 而非 P1。
- **建议**: 二选一：(1) 构造器内急切编码 `this.patternBytes = pattern.getBytes(UTF_8)`（与 `globs` 的 `List.copyOf` 同构，彻底恢复不可变契约，构造一次成本可忽略）；(2) 保留惰性但改 volatile + 本地变量读取惯用法（racy single-check）。优先 (1)，与 nop-find 的 `FindCommand`（全不可变，无惰性字段）对齐。
- **失效面枚举**（F3 同族清扫）: `grep -rn "if (.*== null)" 惰性缓存形态` 于全单元 main 源：`SearchCommand.patternBytes`（本案，唯一命中）；nop-find `FindCommand`（FindCommand.java:26-38）构造器全量冻结（`List.copyOf`），无惰性字段——同类已正确，无漏修兄弟；nop-bytecode 无命令模型惰性缓存。`SearchCommand` 的其他字段（root/pattern/strategy/ignoreCase/globs/maxMatchesPerFile/includeLineText/includeSubmatchText）全部 final——仅 `patternBytes` 一处偏离。
- **信心水平**: 确定（竞态形态确定；后果二的实际显现在 x86 TSO 上概率极低，但 JMM 违规成立）
- **误报排除**: 不是"看到共享字段就报线程不安全"——已逐一核实单次 `search()` 内的三条调用点（:98/:186/:245）在 executor HB 下安全，立案依据是跨 `search()` 调用共享 command 的公共 API 面 + 已写出的具体交错时序 + 未安全发布的形式违规，并对照了同族 `FindCommand` 的正确实现证明这是可修复的局部偏离而非平台惯例。
- **复核状态**: 已复核（2026-10-22）——保留 P2，定为本站点主登记（并入 C4-003/CL-301，见文末维度复核结论）

### [C1-002] WalkContext.recordError 的"首个错误胜出"守卫为非原子检查-然后-行动

- **文件**: `nop-tools/nop-rg/nop-rg-core/src/main/java/io/nop/rg/core/walk/ParallelFileWalker.java:96,102-106`
- **证据片段**:
  ```java
  volatile Throwable error;
  ...
  void recordError(Throwable t) {
      if (error == null) {
          error = t; // 首个错误胜出，快速失败
      }
  }
  ```
- **严重程度**: P3
- **并发场景**: ForkJoinPool 多 worker 目录任务（ScanAction.compute）并发遍历不同子目录；两个以上目录同时不可读/拒绝访问时，各 worker 并发调用 `recordError`（调用点 :129 IOException、:146 AccessControlException）。
- **触发时序**: T1 在 dirA 遍历失败进入 `recordError(e1)`，于 :103 读 `error == null` 成立；在 T1 执行 :104 写入之前，T2 在 dirB 失败进入 `recordError(e2)`，读 `error == null` 同样成立（volatile 保证可见但不保证互斥）→ T1、T2 先后写入，最终保留值取决于写入顺序 → `walk()`（:77-78）抛出的 `NopRgException` 携带的是**任意一个**失败目录的错误，而非时间意义上的首个。
- **现状**: 注释与代码意图声明"首个错误胜出"；volatile 只解决可见性，不解决原子性。
- **风险**: 仅影响快速失败时报告哪个错误（诊断信息保真度）；fail-fast 主语义（`error != null` 停止分发新任务，:120-122）不受损——任意错误胜出同样触发停止。无数据损坏面。
- **建议**: 若需兑现"首个错误胜出"字面契约，改用 `AtomicReference<Throwable>` + `compareAndSet(null, t)`；若接受"任意错误胜出"语义，修正注释以消除契约漂移。二选一，成本极低。
- **失效面枚举**（F3 同族清扫）: `grep -rn "volatile"` 于全单元 main 源仅此一处（ParallelFileWalker.java:96）；SearchCoordinator/FindCoordinator 无 error 汇聚字段（任务异常经 Future.get/ExecutionException 串行传播，天然无此形态）；nop-bytecode 无并行分析。无漏修兄弟。
- **信心水平**: 确定
- **误报排除**: 不是"synchronized/FutureTask/ConcurrentHashMap 原语使用"类误报——问题不在 volatile 原语本身，而在注释契约（首个胜出）与实现语义（任意胜出）不一致的复合操作误用；后果虽轻但属 F10（并发守卫形态）定向检查的真实命中。
- **复核状态**: 已复核（2026-10-22）——保留 P3（见文末维度复核结论）

---

## 三、安全判定汇总（记表不立案项及理由）

1. **FFM confined arena 跨线程面**（重点面）：MappedFileReader/ChunkedFileReader 的映射、读取、关闭在单 worker 任务内闭环（SearchCoordinator.java:163-178、185-223），`Arena.ofConfined()` owner-thread 约束提供 fail-fast 兜底（WrongThreadException），无 in-tree 跨线程访问 segment 的路径。基线 FFM 专项节列出的 17 个文件已逐一核过状态封装：搜索器与查找器（ScalarByteSearcher/PreparedLiteral/VectorByteSearcher/VectorPreparedLiteral）全部 final 字段不可变，无跨线程可变状态。
2. **字节码解析器静态缓存/注册表**（重点面）：ResourceRegistry/OwnershipWhitelist 均为 `Set.of` 不可变名单，无运行时注册/变更入口（javadoc 明示"extend via this set"指源码级扩展），无检查-然后-行动。
3. **SIMD vector 路径状态封装**（重点面）：VectorPreparedLiteral/VectorByteSearcher 无实例可变状态；provider 实例 per-search 新建。
4. **`ChunkedFileReader` 的 `nextStart` 游标**：单线程 `nextChunk()` 序列内使用（同一任务线程），封闭安全。
5. **benchmark 面**：JMH @State 字段、CorpusUtil 局部 Random、HotspotProfiler——按误报校准"为压测/基准写的测试专用代码中的简化并发模型不按生产代码标准报"不立案；未发现其暴露生产代码缺陷。

## 四、维度集外线索（CL 条目）

### nop-tools-parallel-CL-001 ChunkedFileReader 映射累积 + 分块/整文件双映射共存（C5/C6 形态）

- **位置**: `nop-tools/nop-rg/nop-rg-core/src/main/java/io/nop/rg/core/io/ChunkedFileReader.java:71-87`（每块 `channel.map(..., arena)`，无单块 unmap，全部累积至 `close()`）；`SearchCoordinator.java:189-223`（分块扫描 reader 未关闭期间命中文件再开整文件映射，代码注释 :220 自述"审查实测双映射共存"）。
- **形态**: 大文件（> chunkedThreshold，默认 256MB）场景并发映射数随文件体积线性增长（每 256MB 一块 × 双映射），叠加 worker 并发数。虚拟地址空间/映射条目数的峰值上界无人验算；`arena.close()` 批量解除是唯一回收点（成功/异常路径均经 try-with-resources，对称性本身成立）。
- **建议去向**: C5（内存有界性）核对声明规模包络下的映射数/地址空间峰值；C6 核对失败路径（`nextChunk` 中途抛 NopRgException 时已映射块仍由 close 兜底，确认无泄漏）。

### nop-tools-parallel-CL-002 每次调用新建 ForkJoinPool/ExecutorService，无线程命名（C4 形态）

- **位置**: `SearchCoordinator.java:113,130-131`（new ForkJoinPool + finally shutdownNow）；`ParallelFileWalker.java:71-76`（同型）；`FindCoordinator.java:145-161`（Executors.newFixedThreadPool + shutdownNow）。
- **形态**: 短命 CLI 进程内合理；库形态嵌入长驻 JVM 时每次 search/walk/find 建池拆池（无线程命名，未用平台 `NamedThreadFactory`/`IThreadPoolExecutor`；shutdownNow 而非 shutdown+awaitTermination 的在途任务语义）。三个调用点为同族（F3 形态一致）。
- **建议去向**: C4 核对线程池治理（命名可观测性、关闭语义、长驻复用建议）。

### nop-tools-parallel-CL-003 ByteSearchStrategy 契约面每次调用重编译（C9 形态）

- **位置**: `nop-tools/nop-rg/nop-rg-vector/src/main/java/io/nop/rg/vector/VectorByteSearcher.java:27-29`（`findPattern` 每次 `new VectorPreparedLiteral(pattern, false).find(...)`，锚点选择 + 256 项跳表逐调用重建）；生产 coordinator 不经此路径（走 PreparedFinder 预编译），但 `ByteSearchStrategy`/`ScalarByteSearcher.findPattern`（ScalarByteSearcher.java:69-91，每次 `buildSkipTable`）是公共契约接口，第三方逐命中直调会放大分配。
- **建议去向**: C9 热路径维度给出量化口径（调用频率 × 单次 2KB 分配）后定级； CompiledGlob.CACHE 若被长驻库面高频扩模式，其"只增不减"声明（CompiledGlob.java:20-21）在 C7 视角需复核。

## 五、计数口径复现

- 范围：`find nop-tools/nop-rg nop-tools/nop-find nop-tools/nop-bytecode -path "*/src/main/*" -name "*.java"` = 84 文件 / 6,796 行（与审计单元口径一致；全量 find 152 文件含 `target/generated-sources` JMH 生成物与测试源，均已排除）。
- static 可变字段：`grep -rn --include="*.java" -E "static [A-Za-z]" {三模块}/src/main | grep -v "static final" | grep -v "@"` 命中 73 处全部为静态方法声明；补充分隔符锚定的字段模式（声明以 `;` 结尾且非 final）= 0。基线 "C1 static mutable fields: 8" 与本报告载体 2 表的 static final 复合字段面（16 处）存在口径差，已按共享前缀第 10 条双口径列明，不做单值计数结论。
- ThreadLocal：0（与基线一致）。
- volatile：1 处（ParallelFileWalker.java:96）；synchronized：1 处（ParallelFileWalker.java:162，写侧互斥正确，读侧依赖 FJP invoke HB——C2 维度可复核）。
- 立案：2 条（P2×1、P3×1）；CL 线索：3 条（CL-001/002/003）。

---

# C1 深挖第 2 轮

- **审计单元**: 不变（nop-rg + nop-find + nop-bytecode 全单元，src/main 84 文件）
- **本轮扩大面**（主 agent 指定）: (a) FFM Segment 内容的隐式共享（Arena 内数据被多个封装对象引用的别名面）；(b) CLI 参数对象的跨线程传递；(c) CompiledGlob.CACHE 的键对象可变性
- **盲审声明**: 本轮允许读取本目录 C1/C4 第 1 轮文件（已读）；仍未读取其他维度文件及 `ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`、`ai-dev/backlog/`。全部结论以 live code + 本机 JDK 实测为准。
- **编号规则**: 本轮新发现 `C1-R2-001` 起；前轮编号（C1-001/002、CL-001..003）保持原语义不变。

## 一、扩大面逐项核查结论（先记表，后立案）

| 指定面 | 核查证据 | 判定 |
|---|---|---|
| CompiledGlob.CACHE 键对象可变性 | 键类型为 `String`（CompiledGlob.java:24 `ConcurrentHashMap<String, CompiledGlob>`，:36 `computeIfAbsent(pattern, ...)`）——不可变；值对象 `components` 虽为可变 `ArrayList`（:27、:42 经 splitComponents 构造），但字段 final、构造后零写路径、无私有暴露（无 getter），CACHE 经 CHM 安全发布 | 安全（键可变性假设不成立；值对象内部可变容器为潜在脆弱面，无写者则无风险，与前轮 ACCEPT_ALL 同判例） |
| CLI 参数对象跨线程传递 | NopRgMain/NopFindMain 的 picocli `@Option/@Parameters` 字段仅在 main 线程 `call()` 内读取；`globs`（可变 ArrayList）在 `SearchCommand` 构造器 :46 与 `FindCommand` 构造器 :37 均 `List.copyOf` 冻结后才进入 coordinator；任务 lambda 捕获的 `command`/`preparedRef`/`regexSearcher`/`rel`/`file` 字段全 final 或 effectively-immutable；`patternBytes` 惰性字段是唯一例外（已由前轮 C1-001 立案，本轮无新面） | 安全（除已立案的 patternBytes 外） |
| FFM Segment 别名面 | 三模块唯一 Arena 载体仍在 nop-rg-core/io 两文件；下游持有链逐一核过：`PreparedLiteral`/`VectorPreparedLiteral`/`ScalarByteSearcher` 全 final 字段且不持有 segment（扫描时参数传入）；`ChunkedFileReader.Chunk` 为 public record 持有 segment 但消费点仅 `searchFileChunked` 局部循环（SearchCoordinator.java:191-215）；`MatchAggregator`/`LineCursor` 产出全部解码为 `long`/`String`（MatchAggregator.java:99-105、LineCursor.java:86-88），`FileMatches`/`LineMatch`/`Submatch` 无任何 segment 引用；`RegexSearcher.findAll` 也仅 `toArray` 拷贝后解码（RegexSearcher.java:47-53） | 别名不逃逸任务线程（封闭性成立）；但别名面扩大核查发现**映射段存活期与外部文件变更的 TOCTOU 面**（见 C1-R2-001，前轮未覆盖的新失效入口） |
| GitIgnoreFile 并发读（前轮载体 5 未列，本轮补核） | `ParallelFileWalker.java:69` 在建池（:71）前 `loadIgnoreRules()`；`GitIgnoreFile.create()` 急切全量加载（nop-kernel GitIgnoreFile.java:93-97 → loadRules 递归装载）；worker 经 `pool.invoke` 的 HB 边看到完整 rulesMap；`isIgnored`（:307-353）对 `rulesMap`（普通 HashMap）纯只读；实例 per-walk 私有（loadIgnoreRules 局部变量，不逃逸 walker），`reload()/clear()` 无任何并发触达路径 | 安全（线程封闭 + 急切装载 + HB；若未来 walker 复用或暴露 GitIgnoreFile 实例，HashMap 并发读写在 reload 下将变为真实竞态——记脆弱面） |
| ObligationToken.openSites 内部容器暴露 | `openSites()` 包私有直接返回内部 `TreeSet`；`tracked(ns, sites)` 直接采用调用方 set。全部调用点核过（ResourceSemantics.java:283/340/370-371/392/203/227/241/263/269）：`dischargeEverywhere` 经 `without()` 拷贝后替换 token（原 set 不变）、`ownerlessScan` 经 `addAll` 拷贝、`sitesOf` 返回值只读遍历；且 dataflow solve 单线程（前轮已核） | 安全（容器别名未发生写穿透；单线程兜底。包私有契约建议防御性 `Collections.unmodifiableSortedSet` 或拷贝返回——记表不立案） |

## 二、发现条目

### [C1-R2-001] FFM 映射段存活期与外部截断的 TOCTOU：扫描中访问已截断映射区触发 InternalError，错误消息丢失文件上下文并复用「首错弃全」聚合失效层

- **文件**: `nop-tools/nop-rg/nop-rg-core/src/main/java/io/nop/rg/core/io/MappedFileReader.java:30-35`（map 持续到 close）；`nop-tools/nop-rg/nop-rg-core/src/main/java/io/nop/rg/core/io/ChunkedFileReader.java:71-87`（全部 chunk 视图在单 Arena 内累积存活至 close，前轮 CL-001 已证）；消费点 `SearchCoordinator.java:163-178`（整文件路径：映射→isBinary→全文扫描→聚合，同段存活窗口跨多个调用）、`:189-223`（分块路径：全块扫描窗口 + 行提取二次映射）
- **证据片段**:
  ```java
  // MappedFileReader.java:30-35：映射一旦建立即存活至 try-with-resources 末尾
  this.channel = FileChannel.open(file, StandardOpenOption.READ);
  this.arena = Arena.ofConfined();
  ...
  this.size = channel.size();
  this.segment = size == 0 ? null : channel.map(FileChannel.MapMode.READ_ONLY, 0, size, arena);
  // SearchCoordinator.java:166-172：同一映射上的多次读取横跨整个扫描窗口
  if (mappedSize == 0 || isBinary(seg, mappedSize)) { return null; }
  List<MatchSpan> spans = regex ? regexSpans(seg, mappedSize, regexSearcher)
                                : literalSpans(seg, mappedSize, prepared);
  ```
- **严重程度**: P2
- **并发场景**: 搜索池 worker 线程持有某文件的只读映射段期间，外部进程（编辑器保存、`> file` 截断、日志轮转、构建清理、`git checkout` 走 truncate 变体时）对同一文件 ftruncate 截断。与 C4-002 的 stat/map 窗口不同，本面的窗口是**映射建立后到 close 前的整个读取期**——分块路径下即整个大文件扫描期（CL-001 的映射累积进一步拉长该窗口）。
- **触发时序**: T1（worker）在 t0 于 `SearchCoordinator:163` 建立文件 F（大小 S）的映射段 → T2（外部进程）在 t1 将 F 截断至 S' < S（如 `> F`）→ T1 继续在 t2 于 `PreparedLiteral.find`/`isBinary`（:64/:166/:204/:266 任一读取点）访问 [S', S) 内偏移 → 触发 mmap SIGBUS 类缺页，JVM unsafe-fault 处理路径转为 Java 异常。**本机实测**（Zulu OpenJDK 26.0.1，与项目 Java 25 同族的 FFM 实现；单 JVM 内第二 channel truncate 复现）：
  ```text
  mapped size=8388608
  truncated; now scanning beyond new EOF...
  Exception in thread "main" java.lang.InternalError: a fault occurred in an unsafe memory access operation
  	at java.base/jdk.internal.misc.Unsafe.getByte(Native Method)
  	...at java.base/jdk.internal.foreign.AbstractMemorySegmentImpl.get(AbstractMemorySegmentImpl.java:710)
  ```
  即：**可捕获的 `InternalError`**（非进程死亡——修正了 mmap 截断=SIGBUS 崩溃的常见直觉，实测以 JVM unsafe-fault 转换为准）→ 任务抛出 → `future.get()` 包成 `ExecutionException`（SearchCoordinator:126-127）→ 与 C4-002 同一「首错弃全」聚合层收敛为 exit 2 零结果，且 `InternalError` 消息**不含文件路径**（对照同族入口 "stat/map/open file failed: {file}" 均携带路径）。
- **现状**: 映射段存活期未与文件内容一致性做任何校验（size 仅在 map 前采样一次）；`MappedFileReader`/`ChunkedFileReader` 的 javadoc 均未声明「文件被外部截断时的行为」；该入口未列入 C4-002 的失效面清单（前轮 5 项均为 stat/open/map 阶段的 IOException 入口，无一处覆盖「映射存活期截断」）。
- **风险**: 树在搜索窗口内发生截断型变更时，报错文案退化为无上下文的 "a fault occurred in an unsafe memory access operation"（用户无法定位是哪个文件、何种变更），并经首错弃全放大为零结果；库形态嵌入长驻进程时，InternalError 属 Error 族，若嵌入方按「库只抛 NopException」契约写 catch，可能穿透其异常屏障。无数据损坏面（只读映射）。
- **建议**: 二选一并写入 io 层 javadoc 契约：(a) 显式声明边界——「映射存活期内文件被外部截断的行为未定义，可能抛 InternalError」，并在 SearchCoordinator 任务 lambda 内对 `InternalError` 补一个带文件路径的包装（`new NopRgException("search fault while scanning: " + rel, e)`），使该入口回到「错误携带文件名」的族内一致性；(b) 收窄窗口——分块路径逐块用完即重建 Arena（代价是放弃 CL-001 的批量 unmap 语义，需与 C5/C6 权衡），或扫描前对 `size` 复核（不能闭合 TOCTOU，仅降概率，不推荐单用）。倾向 (a)。
- **失效面枚举**（F3 同族清扫：全部「映射段读取期 × 外部截断」交叠点）:
  1. `SearchCoordinator.searchFile:166` `isBinary`（整文件路径首个读取点）— 同族（本案）
  2. `SearchCoordinator.searchFile:170-172` `literalSpans`/`regexSpans` 全文扫描 — 同族（本案）
  3. `SearchCoordinator.searchFile:177`→`MatchAggregator` 聚合读取（`LineCursor`/`decode`）— 同族（本案）
  4. `SearchCoordinator.searchFileChunked:198/204` 分块扫描（`isBinary`/`prepared.find`）— 同族且窗口最长（全块累积映射，CL-001）
  5. `SearchCoordinator.searchFileChunked:221-222` 行提取二次整文件映射读取 — 同族（本案；此窗口另有「陈旧 spans 对新映射越界」变体：truncate 发生在 :216 与 :221 之间时为**确定性** `IndexOutOfBoundsException`（FFM 边界检查，非 fault），同样无文件上下文——已并入 C4 第 2 轮 C4-002 失效面补充，合并裁定与本案同族）
  6. `RegexSearcher.findAll:35` `decode` 的 `asSlice(0,size).toArray` — 同族（整段拷贝同样触发 fault）
  7. `PreparedLiteral.find`/`VectorPreparedLiteral.find`/`ScalarByteSearcher.findPattern` — 纯内存读取器，无独立失效面（第 2-4 项的执行体）
  8. nop-find（`FuzzyFileScorer` 路径）与 nop-bytecode（`Files.readAllBytes` 字节拷贝，NopBytecodeMain:239/250）— 不使用 mmap，**无此失效面**（N/A）
- **信心水平**: 确定（交错时序与 FFM 实现均经本机实测复现；「不同 JVM/OS 组合下 unsafe fault 一律转 InternalError」这一点未做矩阵验证，历史上 MappedByteBuffer 时代存在 SIGBUS 进程死亡的公开报告，故实测结论按当前 JDK 族表述）
- **误报排除**: 不是前轮 C4-002 的重复——C4-002 立案的是 stat/open/map 阶段 IOException 经首错弃全聚合的入口面（其失效面枚举第 1-5 项均已明确列过，不含映射读取期）；本案是前轮未覆盖的新失效入口，证据等级也不同（实测复现 vs 机制推演），且修法不同（C4-002 修聚合策略，本案补错误上下文/声明边界）。也不是「看到共享就报」——共享的是只读映射页，问题不在共享而在生命周期与外部文件状态的解耦。
- **复核状态**: 已复核（2026-10-22）——保留 P2（限缩：删除风险节「穿透嵌入方异常屏障」子句，见文末维度复核结论）

## 三、本轮清扫表与终止信号

| 检查面 | 结论 |
|---|---|
| 全单元 static 非 final 字段复扫（含 CollectManifest.entries 等） | 0 新增（CollectManifest 为 CLI 单线程私有实例，封闭） |
| 全单元惰性初始化 `if (x == null)` 形态复扫 | 除已立案的 SearchCommand.patternBytes（C1-001）与 WalkContext.recordError（C1-002，归 C1-002 的守卫语义）外，其余全部为局部变量判空，无同族漏网 |
| regex/compiled pattern 全局共享面（Pattern.CACHE 无关） | RegexSearcher.pattern per-search final；GitIgnoreFile.Rule.regex per-rule final；无静态 Pattern 复用 |
| FFM 下游持有链（Chunk/FileMatches/LineMatch/Submatch/Utf8OffsetMap） | 无 segment 引用逃逸任务（本轮载体表第 3 行） |
| benchmark 面 | 维持前轮不立案判定（CorpusUtil 局部 Random 等已核） |

**本轮终止**：三个指定扩大面 + 补核面已全部核查完毕，新增立案 1 条（C1-R2-001，P2×1），其余记表安全；未发现更多高价值新问题，C1 维度深挖结束。

---

# C1 维度复核结论（独立复核）

- **复核人**: C1+C4 维度独立复核子代理（独立 task，与初审非同一 agent）
- **复核日期**: 2026-10-22
- **方法**: 逐条重读 live 源码 file:line；C1-R2-001 的 FFM 截断行为在复核环境（同 Zulu OpenJDK 26.0.1 build 26.0.1+8 / macOS arm64，与初审环境同类）独立复现，脚本存 `_tmp/c1r2-verify/TruncFaultTest.java`（8MB 映射 + 第二 channel truncate 后越新 EOF 逐字节读 → `java.lang.InternalError: a fault occurred in an unsafe memory access operation`，栈经 `jdk.internal.misc.Unsafe.getByte(Native Method)`，消息无文件路径——与初审记录逐项一致）。
- **盲审边界说明**: 复核阶段读取了本目录 `dispatch-log.md` 与 `C9-hot-path.md` 的 CL-301/302 节，仅用于同站点重复登记比对与 CL 回填，未据其改动初审证据；未修改其他维度文件与产品代码。

## 逐条判定

| 编号 | 判定 | 复核依据（file:line） | 理由 |
|---|---|---|---|
| C1-001 | **保留 P2，定为主登记** | SearchCommand.java:19、:63-68（check-then-act + clone，非 volatile/final）；:46（globs `List.copyOf`，8 个 final 字段对照）；SearchCoordinator.java:96-98/:245（LITERAL/VECTOR 调用线程建池前初始化）、:186 + :155-161（worker 侧唯一触点在 `searchFileChunked`，REGEX 不触达）；FindCommand.java:20-37（全 final + builder + `List.copyOf`，对照成立） | 竞态形态、调用序论证、F3 清扫全部核实。判级：双写竞态在「嵌入方跨线程复用 command」前提下可具体化；有害后果（零数组→静默错误结果）属 JMM 理论窗口，按 rubric「写不出具体时序的理论竞态=P2」口径 P2 成立。C4-003/CL-301 为同站点重复登记，并入本条（主登记建议见下） |
| C1-002 | **保留 P3** | ParallelFileWalker.java:96（唯一 volatile）、:102-106（守卫 + 「首个错误胜出」注释）、:129/:146（两调用点）、:120-122（fail-fast 停分发不受损）、:77-78（walk 抛出点） | 注释契约（首个胜出）与实现语义（volatile check-then-act 实为末写胜出）背离属实；后果仅诊断保真度，P3 恰当 |
| C1-R2-001 | **保留 P2（限缩一条风险表述）** | MappedFileReader.java:30-35（映射存活至 close）；SearchCoordinator.java:163-178/:189-223（消费窗口）、:126-127（`ExecutionException` → `NopRgException` 包装）；RegexSearcher.java:34-35/:47-53（`asSlice(0,size).toArray` 同族）；ChunkedFileReader.java:71-87（窗口拉长面）；复核实测 InternalError 原文、无文件路径消息、Unsafe 栈均复现 | (1)「本机实测」表述强度**恰当、不降级**：即审计宿主机实测，复核者同机同 JDK build 复现成功；初审已自限「按当前 JDK 族表述、未做矩阵验证」，该 JDK 行为依赖级限定维持原样即可。(2) **限缩**：风险节「InternalError 属 Error 族可能穿透嵌入方异常屏障」不成立——`search()` 在 SearchCoordinator.java:126-127 已将 InternalError 包装为 NopRgException 的 cause，按「库只抛 NopException」写 catch 的嵌入方仍可拦截；删除该子句后余害（错误无文件上下文 + 经首错弃全收敛为零结果 + 截断行为契约未声明）足以支撑 P2 |

## CL 复核结论（C1 产出线索）

- **CL-001 确认**: ChunkedFileReader.java:71-87（每块 map 入共享 arena、无逐块 unmap）+ SearchCoordinator.java:220-222（双映射共存注释）证据核实；按 dispatch-log 与 C6-001 构成跨维重复对，归 Phase 4/6 去重，去向 C5/C6 维持。
- **CL-002 确认（按 C4-001 裁定）**: 三处建池生命周期配对全部正确（ParallelFileWalker.java:74-76、SearchCoordinator.java:130-132、FindCoordinator.java:159-161），「无关闭配对」疑点驳回，命名半由 C4-001 承接。
- **CL-003 确认**: VectorByteSearcher.java:27-29、ScalarByteSearcher.java:69-91（`buildSkipTable` 逐调用重建）核实；去向 C9 维持；CompiledGlob.CACHE 半面由 C9 维度 CL-302 承接，不重复登记。

## 主登记建议（patternBytes 同站点三登记收敛）

- **主登记 = C1-001（P2）**；C4-003（C4 维度 P3）与 CL-301（C9 维度移交线索）并入为重复登记，Phase 4/6 工单按 C1-001 单点跟踪。
- 合并动作：吸收 CL-301「隐式时序契约无钉定、新增直接消费入口即激活」的契约面表述与 C4-003 的 `volatile` 修法备选；首选修法维持 C1-001 建议 (1) 构造器急切编码（与 :46 `List.copyOf` 同构，同时消灭双写与理论发布窗口）。
- 判级说明：C4-003 的 P3 低于初审 rubric 对数据竞争的下限口径（P3 = 性能优化/防御冗余/命名结构，不覆盖数据竞争），合并后以 C1-001 的 P2 为准。

## 最终保留项

| 编号 | 状态 | 说明 |
|---|---|---|
| C1-001（P2） | 保留，主登记 | 并入 C4-003 + CL-301；修法与合并动作见上节 |
| C1-002（P3） | 保留 | AtomicReference CAS 或修正注释，二选一如初审 |
| C1-R2-001（P2） | 保留（限缩） | 删除「穿透嵌入方异常屏障」子句；建议 (a)（javadoc 声明边界 + 任务 lambda 内以 `new NopRgException("search fault while scanning: " + rel, e)` 补文件上下文）不变 |

**C1 维度最终计数**：3 条（P2×2、P3×1），驳回 0、降级 0、限缩 1（C1-R2-001 风险子句）；CL-001/002/003 全部确认（CL-002 由 C4-001 承接，CL-001/CL-003 按 dispatch-log 跨维对去重）。
