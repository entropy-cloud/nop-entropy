# C9 初审：并发高性能（热路径）— nop-tools 全单元

- **审计单元**: nop-tools/nop-rg + nop-find + nop-bytecode（84 文件 / 6,796 行，与 C1 批次口径一致；nop-rg-benchmark 仅作基准覆盖对照面，不立案）
- **维度**: C9 并发高性能（热路径）——锁争用 / 分配放大 / 串行化瓶颈 / 批量化机会
- **批次**: 全单元单批（盲审）
- **盲审声明**: 本初审为盲审。未读取 ai-dev/audits/、ai-dev/plans/、ai-dev/bugs/、ai-dev/lessons/、ai-dev/backlog/ 下任何文件；仅以 live code、`_tmp/cra-baseline-nop-tools-parallel.txt`（C9 节 + FFM/SIMD 专项）与任务派发单中给出的 CL 线索编号（CL-001/003/201/202）为输入。初审输出只是线索，最终结论须经独立复核。
- **CL 前缀约定**: 本报告立案编号 C9-001 起；维度集外强信号线索以 CL-301 起附记（移交对应维度）。
- **基线**: `_tmp/cra-baseline-nop-tools-parallel.txt`（2026-10-22T19:37:18Z @ 5e84b88a37）：C9 hot-path candidates=40，FFM/Arena/SIMD 专项 17 文件，C2 locks=1。
- **日期**: 2026-10-22

---

## 1. 热路径清单（路径 × 频率 × 单次代价 × 判定）

判定口径：每请求/每记录/每字段执行的代码才算热路径；冷路径不进入本维度。量化链 = 调用频率 × 单次代价估算。

| # | 路径 | 频率 | 单次代价（估算链） | 判定 |
|---|------|------|--------------------|------|
| H1 | `PreparedLiteral.find` / `VectorPreparedLiteral.find`（整文件+分块扫描） | 每文件 × 扫描位置 | 锚点+跳表**每命令编译一次**（SearchCoordinator.java:90 注释；JFR 历史 buildSkipTable 14% 已收敛） | 优（CL-003 已在 live code 收敛） |
| H2 | `ScalarByteSearcher.findPattern` | 生产图可达频率 = 0（仅测试调用） | 每调用重建 2KB `long[256]` 跳表 + 锚点选择（ScalarByteSearcher.java:78-80） | watch：生产不可达，但策略接口保留逐调用编译形态，未来调用方可经 `ByteSearchStrategy` 重引 CL-003（见 H2 备注） |
| H3 | `SearchCoordinator.isBinary` | 每文件 1 次（分块路径每文件首块 1 次） | ≤8192 次 `MemorySegment.get` ≈ 8-16µs/文件；100k 文件 ≈ 1-1.6s 总量（并行摊薄） | 可接受（JIT 常可自动向量化该循环形态；无量化收益不立案） |
| H4 | `MappedFileReader` map/unmap（FFM Arena.ofConfined） | 每文件 1 次 | 1 open + 1 mmap + close（确定性释放） | 正常（C6 面复核资源点） |
| H5 | `ChunkedFileReader.nextChunk` | 每块 1 次 map | 同一 Arena 内顺序映射；命中行提取另开整文件映射（SearchCoordinator.java:220 注释，双映射共存为已裁定契约） | 正常 |
| H6 | `LineCursor.advance`（text 口径） | 每命中 span 1 次 | 行起点推进均摊 O(n)；**行尾前向扫描每 span 重扫**（见 C9-004） | 立案 C9-004（P3） |
| H7 | `LineCursor.advanceLineStart`（count 快速路径） | 每命中 span 1 次 | 仅行起点推进，免行尾扫描（plan 2275 P6） | 优 |
| H8 | `MatchAggregator` count/text 分派 | 每文件（有命中时） | count 口径零解码；text 口径行文本经 LineCursor 单一实现解码、终止符按长度判定 | 优 |
| H9 | `RegexSearcher.findAll` | 每 REGEX 策略文件 1 次 | 整文件解码（契约已声明阈值显式失败）；**Utf8OffsetMap 无条件构建**（见 C9-003） | 立案 C9-003（P3）；整文件解码本身为已声明契约，不立案 |
| H10 | `ParallelFileWalker.ScanAction.compute` | 每目录 1 次 + 每常规文件 1 次锁 | 全局 `synchronized(context)` 每文件一锁 + 每子项 2 次 stat（见 C9-001） | 立案 C9-001（P2，CL-001） |
| H11 | `WalkContext.recordError` | 每错误 1 次 | volatile check-then-act；双写时任一错误被记录即触发 fail-fast，无信号丢失 | 可接受（竞态良性，失效面见 C9-001 枚举） |
| H12 | `GlobMatcher.accept` | 主线程每文件 1 次（串行段） | 空 glob 零开销短路；有 glob 时 `splitPath` 每 glob 每 path 分配 List+数组 ≈ 数百 ns | 可接受（串行段 ~0.1ms/万文件级，相对并行搜索不构成瓶颈） |
| H13 | `SearchCommand.patternBytes` | 主线程每命令 1 次初始化 + worker 每分块文件 1 次读 | 返回 clone（防御性）；惰性字段跨线程读的 JMM 问题见 CL-301 附记 | 附记 CL-301（转 C1） |
| H14 | `SearchCoordinator.search` / `FindCoordinator.find` / `ParallelFileWalker.walk` 每命令新建线程池 | 每命令 1-2 次 | ForkJoinPool/FixedThreadPool 构造 ~1-5ms；CLI 一次性场景不可见；walk 池与搜索池不重叠（顺序执行） | 可接受（无已声明的库态高频复用场景，无量化收益不立案） |
| H15 | `FuzzyFileScorer.scoreNormalized`（nop-find 打分热路径） | 每候选 1 次 × 3 字段 | query 构造期预归一化、候选文本遍历期预归一化、打分零 toLowerCase；子串档走 `String.indexOf` 内在函数 | 优 |
| H16 | `TopKCollector.offer` | 每候选 1 次 | 已满不优于末尾 O(1) 快速路径；插入 O(log K)+搬移；K=limit 有界 | 优（线程局部 + 单线程归并，无共享竞争） |
| H17 | `ClassArtifactCollector.collectJar` | 每 jar 输入 1 次 | `new JarFile` 每 jar 一次（ClassArtifactCollector.java:118）；manifest 命中免读内容 | 优（CL-202 在 collector 侧已收敛） |
| H18 | `NopBytecodeMain.readBytes`（read-back 通道） | **每 jar artifact 1 次** `new JarFile` | O(entries × central-directory)（见 C9-002） | 立案 C9-002（P2，CL-202） |
| H19 | `ClassArtifactCollector.sha256Hex` | 每 artifact 2 次（collect 1 + read-back TOCTOU 验证 1） | `MessageDigest.getInstance` 每次查找 ~1-2µs + SHA-256 本体（JDK 内在函数 ~1GB/s）；采集阶段单线程无争用 | 可接受（TOCTOU 验证为契约要求；getInstance 可换 ThreadLocal/复用，收益 <5%，不立案） |
| H20 | `NullflowAnalyzer.analyze` + `ResourceLeakAnalyzer.analyze` | 每 artifact 各 1 次 | 各自 `ClassReader.accept` 全量解析（每 artifact 2 次解析 + collect 期 `getClassName` 1 次）；解析占分析成本比例有 NullflowKernelBenchmark.parseCorpus 对照 | 可接受（共享解析为批量化机会，登记 BG-5；无独立量化不立案） |
| H21 | `ForwardSolver.solve` | 每方法 worklist 迭代 | 每 edge `Frame.copy()`——fixpoint 求解标准形态；deadline 每步检查（~25ns nanoTime，相对 transfer 可忽略） | 正常（有防挂死 deadline 保护） |
| H22 | `MethodCfgBuilder.build` | 每方法 1 次 | O(n) 指令索引 HashMap + 一次后继扫描 | 正常 |
| H23 | `CompiledGlob.compile` CACHE（ConcurrentHashMap.computeIfAbsent） | 每模式 1 次编译，匹配期只读 | 全局共享缓存只读访问无争用热点；javadoc 声明 CLI 生命周期不淘汰 | 正常 |
| H24 | nop-rg SIMD 主路径（VectorPreparedLiteral 扫描） | 每文件 × 扫描位置（≥16B 模式） | 零拷贝 `fromMemorySegment` 直接扫映射段，无每调用分配；构造期一次性 skip 表 | 优（专项判定见 §3） |

---

## 2. 风险立案

### [C9-001] walker 文件发现热路径：每文件全局锁 + 每子项双重 stat 串行化

- **文件**: `nop-tools/nop-rg/nop-rg-core/src/main/java/io/nop/rg/core/walk/ParallelFileWalker.java:133-165`（锁：162-164）
- **证据片段**:
  ```java
  for (Path child : children) {
      ...
      // 每子项 2 次独立 stat syscall（Files.list 不携带属性）
      isDir = Files.isDirectory(child);
      ...
      } else if (Files.isRegularFile(child)) {
          synchronized (context) {          // ← 每个常规文件一次全局锁获取
              context.files.add(child);
          }
      }
  }
  ```
- **严重程度**: P2
- **并发场景**: ForkJoinPool(threads=CPU 核数) 的全部工作线程在目录级 RecursiveAction 中并行枚举；所有线程的每个常规文件发现都汇入 `WalkContext.files` 上的单一监视器锁。CL-001 线索所指热点。
- **触发时序**: 非交错正确性问题，为结构性串行化：T1..Tn 各自 `Files.list` 拿到目录 → 逐子项过滤 → 每发现一个常规文件，全部线程在同一 `context` 监视器上串行排队执行 `ArrayList.add`（临界区 ~50-100ns，争用下膨胀为 100-500ns/次 + 缓存行弹跳）。量化链：100k 文件树 × 16 线程 → 串行化下限 ~5-10ms、争用放大 ~10-50ms；同路径每子项 `isDirectory`+`isRegularFile` 各 1 次 stat（~1-2µs×2）× 100k ≈ 0.2-0.4s，为 walk 真实主导成本。二者可由同一次批量化改造一并消除。
- **现状**: 文件发现按单文件粒度加全局锁；目录流属性未复用导致每子项 2 次独立 stat。
- **风险**: 大树（10 万级文件）+ 高核数下，文件发现阶段出现锁队列与 syscall 双重串行段，削弱 work-stealing 并行收益；属性能退化而非正确性问题。声明规模（rg 级工具）下可量化（见上链）。
- **建议**: 每目录将常规文件先收集到局部 List，循环结束后一次 `synchronized(context){ context.files.addAll(local); }`——锁获取次数从 O(文件数) 降为 O(目录数)（典型降 10-20×）；同时改用 `Files.newDirectoryStream(dir)` 携带 `BasicFileAttributes` 一次读取 isDirectory/isRegularFile，消除每子项第二笔 stat。复用面：无需新基础设施，纯 walker 内改造。
- **失效面枚举**: WalkContext 三个共享成员逐一核查——`files`（本条，缺陷）；`error`（volatile + recordError check-then-act，双写竞态良性：任一写入方记录的都是真实错误且 walk() 必抛，无信号丢失，OK）；`ignore`（构造后只读，OK）。同族兄弟入口：`ParallelFileWalker.walk()` 唯一公开入口（OK，串行收尾排序）；`SearchCoordinator.search`（worker 写各自 Future 结果，主线程单线程汇入 TreeMap，无锁，OK）；`FindCoordinator`（线程局部 TopKCollector + 单线程归并，OK）。
- **信心水平**: 确定（锁存在性与串行化结构）；量级为估算链， magnitude 为二阶（相对 syscall 成本），复核可酌情降 P3。
- **误报排除**: 不是"锁内 I/O"类 C2 线索（临界区本身只有 ArrayList.add，I/O 在锁外）；不是把"synchronized 看着不优雅"当问题——有上面的调用频率×代价量化链，且 walker 是 nop-rg/nop-find 共享的每记录热路径。
- **复核状态**: 已复核（2026-10-22 C6/C9 维度独立复核——保留，降级 P2→P3，见文末《维度复核结论》）

### [C9-002] nop-bytecode read-back 通道：逐 artifact 重开 JarFile，O(entries × central-directory)

- **文件**: `nop-tools/nop-bytecode/src/main/java/io/nop/bytecode/cli/NopBytecodeMain.java:121-151, 235-261`（重开点：244）
- **证据片段**:
  ```java
  for (Map.Entry<String, ClassArtifact> e : byRelative.entrySet()) {
      byte[] bytes = readBytes(input, Path.of(e.getKey()), e.getValue());   // ← 每 artifact 调一次
      ...
  }
  private static byte[] readBytes(CollectInput input, Path relative, ClassArtifact artifact) {
      ...
      try (JarFile jar = new JarFile(input.path().toFile())) {              // ← 每个 entry 重开同一个 jar
          JarEntry entry = jar.getJarEntry(relative.toString());
  ```
- **严重程度**: P2
- **并发场景**: 单线程主循环（CLI 分析管线无并行），每个 jar 输入内的每个 `.class` entry 触发一次完整的 JarFile 打开（文件 open + central directory 读取与解析 + JarFile 内部结构初始化）。CL-202 线索所指；注意 collector 侧（`ClassArtifactCollector.collectJar`，ClassArtifactCollector.java:118）已是每 jar 一次，缺陷仅在 CLI read-back 侧。
- **触发时序**: 每次 `nop-bytecode <fat.jar>` 运行：collect 阶段打开 jar 1 次枚举全部 entry → read-back 阶段对同一 jar 再打开 N 次（N=.class entry 数）。量化链：JarFile 构造 ~0.2-2ms（随 central directory 大小线性，fat jar 数百 KB-数 MB 目录需反复重读解析）；200MB/30k-entry 级 fat jar → 30k × ~1ms ≈ 30s 纯重复开销，单线程串行，且伴随 30k 次文件句柄开闭。同一 jar 的正确形态已在 collectJar 示范（1 次打开遍历全部 entry）。
- **现状**: read-back 逐 entry 重开同一 jar；TOCTOU 的 sha256 校验逻辑本身正确（collect 与 read-back 之间内容变更会被捕获）。
- **风险**: 大 jar 输入下分析管线出现分钟级非必要耗时与句柄churn；manifest 增量只省 collect 侧读取，read-back 侧的重复打开不受增量保护，每次全量付费。
- **建议**: 在 per-input 循环层为 JAR 输入打开一次 `JarFile` 并下沉为 `readBytes` 参数（或传入按 entry 取字节的闭包），目录输入保持逐文件 `readAllBytes`（每文件 1 open 已是下限）。对照实现：`ClassArtifactCollector.collectJar` 同一模式。
- **失效面枚举**: 生产代码 JarFile 打开点全清单——`ClassArtifactCollector.collectJar:118`（每 jar 1 次，OK）；`NopBytecodeMain.readBytes:244`（每 artifact 1 次，本条缺陷）；`NopBytecodeMainTest`/测试面不计数。同族兄弟：目录输入 read-back 逐文件 open（最小形态，OK）；`jar.getInputStream(entry)` 在两处均在已打开的 JarFile 内调用（OK）。resource 分析器 `ResourceLeakAnalyzer` 的 registry 为 per-analyzer 实例字段、main 每 artifact 新建（OK，无跨 artifact 状态）。
- **信心水平**: 确定（代码结构即证据；绝对量级依赖 jar 规模的估算链）。
- **误报排除**: 不是把"单线程工具慢"泛化为问题——重复打开同一 jar 是结构性 O(N×C) 放大而非固有成本，且同仓库已有正确形态（collectJar）可直接对齐；不是 C6 资源泄漏（try-with-resources 完整关闭）。
- **复核状态**: 已复核（2026-10-22 C6/C9 维度独立复核——保留 P2，见文末《维度复核结论》）

### [C9-003] RegexSearcher：零命中文件也无条件构建 O(len) UTF-8 偏移映射（4B/char 分配放大）

- **文件**: `nop-tools/nop-rg/nop-rg-core/src/main/java/io/nop/rg/core/coordinator/RegexSearcher.java:34-45, 64-94`
- **证据片段**:
  ```java
  public List<ByteSpan> findAll(MemorySegment seg, long size) {
      String content = decode(seg, size);              // 整文件解码（已声明契约）
      Utf8OffsetMap offsets = new Utf8OffsetMap(content); // ← 无条件：int[len+1] = 4B/char
      Matcher matcher = pattern.matcher(content);
      List<ByteSpan> spans = new ArrayList<>();
      while (matcher.find()) {
          long byteStart = offsets.byteOffsetOf(matcher.start()); // 仅命中时消费
  ```
- **严重程度**: P3
- **并发场景**: REGEX 策略下每个 ≤chunkedThreshold 文件在 ForkJoinPool worker 线程内独立执行 findAll；分配放大按 worker 数并行发生，瞬时分配率 = 并发度 × 单文件放大。
- **触发时序**: 每 regex 文件：`decode` 产生 byte[](~S)+String(~S，compact strings)；`new Utf8OffsetMap` 再分配 `int[len+1]` ≈ 4×chars 并做 O(len) codePointAt 扫描——**在第一次 `matcher.find()` 之前**。零命中文件（搜索语料中的多数）全程不消费该映射。量化链：50KB ASCII 文件 → int[] ~200KB 无效分配；10k 文件 × 50KB 语料一轮 ≈ 2GB 纯垃圾（parallel GC 下直接推高 GC 频率）；百万级 char 的 minified 文件 → 单文件 4MB+ 映射。
- **现状**: 整文件解码是 plan 声明的契约（大文件显式失败不静默回退），但偏移映射的前置无条件构建不是契约要求——`byteOffsetOf` 只在命中循环内消费。
- **风险**: 大语料 REGEX 搜索的分配率被放大 ~4×（相对 decode 本体），worker 并发乘数下制造 GC 压力；零命中为主的大规模语料（典型 CI 场景）全额付费。不掩盖正确性问题，C9 默认 P3。
- **建议**: 将 `Utf8OffsetMap` 改为惰性：先跑 `matcher.find()`，首次命中时才构建映射（局部 null 检查）；或改用按需换算（对命中位置做一次 O(len) 线性扫描并缓存游标）。同族语义已可复用：`MatchAggregator` count/text 分派（plan 2273 R5）就是同款"消费方不读不解码"先例。
- **失效面枚举**: 偏移/解码同族点全清单——`RegexSearcher.decode`（映射路径必需，保留）；`Utf8OffsetMap` 构造（本条，无条件构建为缺陷点）；`Utf8OffsetOf.byteOffsetOf`（只读消费，OK）；`MatchAggregator.decode`（仅 includeSubmatchText 时解码，OK）；`LineCursor.text`（仅 text 口径消费，OK）；分块路径（LITERAL/VECTOR 专用，REGEX 显式抛异常，不触及，OK）。
- **信心水平**: 确定（分配结构与消费点代码即证据；语料级总量为估算链）。
- **误报排除**: 不是把"整文件解码"已声明契约重复立案——立案对象仅是无条件 offset map；这与共享前缀"不重复报告已收敛问题"一致：plan 2273 R5 已对行文本做过同款收敛，本条是同族尚未收敛的最后一个点。
- **复核状态**: 已复核（2026-10-22 C6/C9 维度独立复核——保留 P3，见文末《维度复核结论》）

### [C9-004] LineCursor.advance 逐 span 重扫行尾：多命中长行 O(spans × 行长) 扫描放大

- **文件**: `nop-tools/nop-rg/nop-rg-core/src/main/java/io/nop/rg/core/coordinator/LineCursor.java:29-55`；调用方 `MatchAggregator.java:62-79`
- **证据片段**:
  ```java
  public LineInfo advance(long offset) {
      ...
      long contentEnd = indexOf(LF, offset, size);   // ← 每次 advance 都从 span 起点前向扫到行尾
      ...
  }
  // MatchAggregator.buildLineMatches：同一行的每个 span 都触发一次
  for (MatchSpan span : spans) { // spans 升序，同行命中的行信息相同
      LineCursor.LineInfo info = cursor.advance(span.start());
  ```
- **严重程度**: P3
- **并发场景**: text 口径下每文件的命中 span 聚合在单个 worker 线程内执行（单文件串行，跨文件并行）；放大因子取决于单文件内命中密度与行长，与并发度正交。
- **触发时序**: 单文件内含一行 L 字节、其上 M 个命中（minified JS/JSON/数据文件典型形态）：第 i 个 span 的 advance 从该 span 起点前向扫描至行尾，平均扫 L/2 字节 → 总扫描 ≈ M × L/2。量化链：5MB 单行文件、10k 命中 → ~25GB 字节扫描/文件（~10GB/s 内存扫描下 ≈ 2.5s 单文件），而行结构信息其实一次扫描即可确定。同行 span 的 `lineStart` 判定（currentInfo 比对）已表明"同行"是显式存在的概念，行尾却未随行缓存。
- **现状**: 行起点推进均摊 O(n)（游标单调），行尾终点每 span 重算；count 快速路径（advanceLineStart）无此问题。
- **风险**: 高密度命中 + 超长单行文件（现代前端产物、日志流）下 text 口径聚合退化为二次方级扫描，单文件即可拖慢整体（该 worker 成为 straggler，`invokeAll` 等最慢任务）。真实语料可触发、可量化，但非常规源码语料的主导成本。
- **建议**: LineCursor 增加行级缓存：当 `advance` 前向推进后 `lineStart` 未变且 `offset` 仍落在上次 `contentEnd` 之前时，直接复用上次 (contentEnd, lineEnd, lineNumber)（spans 升序契约保证 offset 单调，MatchAggregator.aggregate javadoc 已声明 spans 升序为前提）。
- **失效面枚举**: LineCursor 方法面全清单——`advance`（本条，行尾重扫）；`advanceLineStart`（均摊 O(n)，OK）；`text`（每行一次，消费驱动，OK）；`indexOf`（逐字节扫，见 H3 同款判定：无量化收益不单独立案）；调用方两路——`MatchAggregator` count 路径走 advanceLineStart（OK），text 路径走 advance（本条缺陷生效点）；`SearchCoordinator` 不直接消费 LineCursor（OK）。
- **信心水平**: 确定（结构）；实际影响幅度依赖语料形态（估算链已给），复核可结合 corpus 实测酌情维持 P3 或降 watch。
- **误报排除**: 不是"每字段执行"以外的冷路径——命中 span 聚合是 text 口径的每记录热路径；不是把 JIT 可向量化的逐字节扫描当问题（那是 H3/H7 判定为可接受的同款），立案对象是**同一行内重复扫描的算法性放大**，非单次扫描代价。
- **复核状态**: 已复核（2026-10-22 C6/C9 维度独立复核——保留 P3；其失效面对 advanceLineStart 的"OK"判定被 C9-R2-001 纠正成立，两案合并，见文末《维度复核结论》）

---

## 3. SIMD/Vector 专项判定（roadmap 点名面，对照 nop-rg-benchmark）

| 判定项 | 证据 | 结论 |
|--------|------|------|
| 向量化收益 | `NopRgVectorLiteralFinderProvider.SIMD_MIN_PATTERN_LENGTH=16`：6B 实测向量=标量 72-80%（劣化），16B 实测=稀疏 3.5x/密集 2.5x（类 javadoc 注明取证 `_tmp/nop-rg-bench/vec-16b.json`、VectorCompareBenchmark 场景矩阵）；8-15B 段未测、保守归标量 | 收益声明有基准取证，阈值策略成立 |
| 分配/拷贝开销 | `VectorPreparedLiteral.find`：`ByteVector.fromMemorySegment` 零拷贝扫映射段；skip 表/折叠 pattern 为构造期一次性（每命令一次）；扫描循环内无分配 | 无分配放大 |
| fallback 一致性 | 三层：`available()` Class.forName 门控（catch LinkageError）→ compile 内 LinkageError 二次兜底降 `PreparedLiteral`（NopRgVectorLiteralFinderProvider.java:61-72）→ 短模式返回标量等价实现；`VectorLiteralEquivalenceTest` 存在等价性测试；ignoreCase 均为 ASCII 折叠（与 RegexSearcher CASE_INSENSITIVE 无 UNICODE_CASE 对齐） | 一致，无立案 |
| 逐调用编译残留 | `VectorByteSearcher.findPattern` 每调用 `new VectorPreparedLiteral`（VectorByteSearcher.java:27-29）——javadoc 自述"契约承载"，生产 coordinator 走预编译形态，生产图不可达 | 与 H2 同款 watch：策略接口保留逐调用形态，无生产频率，不立案 |

---

## 4. 基准缺口登记（C9 步骤 6，对照 nop-benchmark 惯例；P3 级登记，不占发现编号）

| # | 缺口 | 现状 | 建议基准形态 |
|---|------|------|--------------|
| BG-1 | ParallelFileWalker 无独立基准 | 仅经 CoordinatorEndToEndBenchmark 间接覆盖 | JMH Mode.Throughput：合成树（D 目录 × M 文件参数化），`-p threads=1/4/NCPU` 验证并行伸缩与锁串行段（对照 C9-001） |
| BG-2 | 分块大文件路径（>256MB）零基准覆盖 | EndToEnd corpus 最大单文件 32MB，全部走整文件映射路径；`LargeFileSearchTest` 仅正确性 | JMH：512MB 单文件 × LITERAL count 口径，mapped vs chunked 双路径吞吐 + 分配率（-prof gc） |
| BG-3 | REGEX 策略无基准 | 无任何 regex 路径 JMH | JMH：regex vs literal 同语料（含零命中语料），-prof gc 验证 C9-003 的分配放大口径 |
| BG-4 | nop-find 全模块无基准 | 无 | JMH Mode.Throughput：`FuzzyFileScorer.scoreNormalized` + `TopKCollector.offer` 100k 候选；FindCoordinator 端到端 avgt |
| BG-5 | nop-bytecode 基准面窄 | 仅 NullflowKernelBenchmark（合成 20 类 × 15 方法，test scope）；ResourceLeakAnalyzer、collect/read-back 管线无基准 | JMH：真实 fat jar 的 collect+read-back avgt（对照 C9-002 的重复打开代价）；ResourceLeakAnalyzer runXxx 对齐 runNullflow 形态 |

---

## 5. CL 条目附记（维度集外强信号，移交对应维度；CL-301 起）

### CL-301 【转 C1/C2】SearchCommand.patternBytes 惰性缓存的跨线程可见性依赖隐式预热时序

- **文件**: `nop-tools/nop-rg/nop-rg-core/src/main/java/io/nop/rg/core/coordinator/SearchCommand.java:19, 63-68`
- **信号**: `private byte[] patternBytes;` 为普通惰性字段（非 final、非 volatile、无锁），`patternBytes()` 在 worker 线程内被调用（`searchFileChunked` 经 worker 任务触及，SearchCoordinator.java:186）。JMM 下无同步的惰性发布理论上可读到非 null 引用但数组内容为默认值 → 返回全零 pattern（静默错误结果）。
- **当前实际安全的原因（复核需验证的调用序论证）**: 两条 prepared 路径（LITERAL/VECTOR）在主线程 task 提交前已调用 `command.patternBytes()`（SearchCoordinator.java:96-98），字段必然已初始化，经 `invokeAll` 的任务提交 happens-before 传递安全可见；REGEX 路径不触及该字段。即当前唯一公开入口 `search()` 下竞态不可达——但这是**隐式时序契约**，无任何钉定；任何新增直接消费 `patternBytes()` 的入口都会激活竞态。
- **建议**: 构造器内急切初始化（pattern 非空校验已在构造器，零成本），或字段改 final；同步删除"惰性缓存"注释。
- **移交**: C1（实例级共享可变状态）。非 C9 立案（当前无可达的量化性能/正确性后果）。

### CL-302 【转 C6/C7】CompiledGlob.CACHE 进程级只增不减缓存

- **文件**: `nop-tools/nop-rg/nop-rg-core/src/main/java/io/nop/rg/core/glob/CompiledGlob.java:24, 35-37`
- **信号**: `ConcurrentHashMap` 静态缓存无淘汰；javadoc 自述"CLI 场景模式数量有限且只增不减，不做淘汰"。库态长期驻留 + 动态 pattern 输入时为 C7"缓存项只加不减"慢性增长点。
- **移交**: C7。当前 CLI 生命周期下有声明豁免，不立案。

---

## 6. 汇总

| 类别 | 数量 | 明细 |
|------|------|------|
| 热路径清单行 | 24 | H1-H24（优/正常 17，watch 2，立案 4，附记 1） |
| 立案 | 4 | P2×2（C9-001 walker 全局锁+双重 stat、C9-002 逐 artifact 重开 JarFile）、P3×2（C9-003 无条件偏移映射、C9-004 行尾重扫放大）；P0×0 / P1×0 |
| 基准缺口 | 5 | BG-1..BG-5 |
| CL 附记 | 2 | CL-301（转 C1）、CL-302（转 C7） |
| SIMD/FFM 专项 | 4 项判定 | 收益/分配/fallback/残留全部通过，无立案（§3） |

**线索闭环状态**（对照派发单）：CL-001 → C9-001（立案，live 证据确凿）；CL-003 → 已收敛（SearchCoordinator.java:90 每命令预编译，仅策略接口残留 watch，H2）；CL-201 → 部分收敛（整文件解码为已声明契约不立案，无条件 offset map 为残留缺陷，C9-003）；CL-202 → collector 侧已收敛、CLI read-back 侧仍在（C9-002）。

# C9 深挖第 2 轮

- **轮次定位**：第 2 轮合并批深挖，追加语义（不修改前轮内容）。新发现编号 C9-R2-001 起。
- **本轮扩大面**（对照派发单）：① SIMD vector 4 文件分配/拷贝放大量化核查（初审判通过，扩大到候选面全查）；② LineCursor 之外的扫描循环放大；③ nop-find 搜索路径分配面。
- **盲审声明**：本轮为深挖轮，仅可读本文件（C9）与 C6-resource-lifecycle.md 两份前轮产物；未读取其他维度文件及 ai-dev/plans/、ai-dev/bugs/、ai-dev/lessons/、ai-dev/backlog/。所有判定以 live code 为准。

## 1. 扩大面清扫表（逐项判定）

| # | 检查点 | 证据与量化 | 判定 |
|---|---|---|---|
| S1 | VectorPreparedLiteral 扫描循环全查 | VectorPreparedLiteral.java:60-102：`ByteVector.fromMemorySegment`（L73）零拷贝加载；EQ/or/anyTrue/firstTrue 掩码操作为寄存器级（可标量替换，无逃逸分配）；matchesAt/readByte（L104-116）逐字节 `seg.get` 无分配；skip 表/foldedPattern 均构造期一次（L52-56）。检查两处保守推进（L87 `Math.max(s+1,...)`、L91 整窗口跳过）——最坏情况（锚点高频命中+首字节失配）退化为近标量，与标量 BMH 同为已知最坏形态，无向量特有放大 | 通过（维持初审 §3 结论） |
| S2 | VectorByteSearcher/NopRgVectorLiteralFinderProvider | VectorByteSearcher.findFirstByte（L32-49）向量化 memchr 无分配；findPattern 逐调用 `new VectorPreparedLiteral`（L27-29）= 初审 §3 已 watch（契约承载、生产不可达），无变化；provider.compile 的 `available()` → `Class.forName`（NopRgVectorLiteralFinderProvider.java:46-52）在 `resolveVectorFinder` 中**每 search() 一次**（SearchCoordinator.java:236-254，非每文件）——CLI 一次性场景不可见，H14 同款判定 | 通过（无新立案） |
| S3 | 标量扫描循环 | PreparedLiteral.find（:57-71）/ScalarByteSearcher.findPattern（:69-91）：跳表推进循环零分配，跳表每命令编译（H1 已收敛）；RegexSearcher.findAll 命中循环 byteOffsetOf O(1)（RegexSearcher.java:43-49）；isBinary ≤8192 次 get（H3 已判可接受）；searchFileChunked 块内循环 overlap 重扫 = patternLen-1 字节/块（SearchCoordinator.java:185-216，微量） | 通过 |
| S4 | LineCursor 同族全查（advance 之外的兄弟） | advanceLineStart（LineCursor.java:62-76）count 路径：同行多 span 时每次从 lineStart 重扫至 span 起点——初审 C9-004 失效面"均摊 O(n)，OK"的判定**只对跨行 span 成立**，同行 span 簇打破均摊 | **立案 C9-R2-001**（P3，与 C9-004 同族相邻） |
| S5 | CompiledGlob 匹配算法最坏形态 | matchComponents（CompiledGlob.java:111-137）中间 `**` 段 take=0..n 枚举递归（L120-127），多 `**` 段无 memoization → 病态 pattern 下 O(depth^k)；该匹配在 SearchCoordinator.search 的**并行化之前的串行段**执行（SearchCoordinator.java:103-107），H12 只量化了常规 pattern 的均摊代价（数百 ns），未覆盖最坏形态 | **立案 C9-R2-002**（P3） |
| S6 | nop-find 每候选分配面 | FindCoordinator.scoreShard（:112-128）：每候选 `path.toString()` + `getFileName().toString()` + 3×normalize（normalize 无大写时零分配短路，FuzzyFileScorer.java:61-69）≈ ≤5 个短命字符串/候选；10 万文件 ≈ 50 万短命对象，GC 压力相对 walk/stat 成本二阶。主线程串行段 relativize+toString+replace（FindCoordinator.java:79-85、SearchCoordinator.java:103-107）≈ 0.3µs × 100k ≈ 30ms | watch（H12/H15/H16 同族，低于立案量化门槛） |
| S7 | nop-bytecode collect 每 artifact 附加 stat | ClassArtifactCollector.collectDirectory:99-107：walk 自身 stat + `Files::isRegularFile` 再 stat + resolveArtifact 内 `Files.size` + `readAttributes`（:153/:206）= 每 .class 多 2-3 次 stat；单线程 CLI、10k class ≈ 数十 ms | watch（与 C9-001 stat 半边同族"属性未随遍历携带"，绝对量低于立案门槛） |
| S8 | 输出面 | JsonOutput.escape 控制字符走 `String.format("\\u%04x")`（JsonOutput.java:92-94）≈ 200-500ns/次——仅命中行含控制字符时触发，输出本就字节受限的路径；find ResultPrinter.escape 已做零分配短路（ResultPrinter.java:51-73） | watch（数据依赖低频，不立案） |

**清扫表终止信号**：S1-S8 已覆盖派发单三个扩大面（SIMD 4 文件全查、LineCursor 外扫描循环、nop-find 分配面）及扩大过程中暴露的 glob/bc 侧候选面；既定检查点无遗留线索，C9 深挖第 2 轮清扫终止。

## 2. 发现条目

### [C9-R2-001] count 快速路径同行 span 重复扫描行前缀：advanceLineStart 均摊判定被同行 span 簇打破（与 C9-004 同族相邻）

- **文件**: `nop-tools/nop-rg/nop-rg-core/src/main/java/io/nop/rg/core/coordinator/LineCursor.java:62-76`；生效调用方 `nop-tools/nop-rg/nop-rg-core/src/main/java/io/nop/rg/core/coordinator/MatchAggregator.java:38-49`
- **证据片段**:
  ```java
  long advanceLineStart(long offset) {
      if (offset < lineStart) { lineStart = 0; lineNumber = 1; }
      while (lineStart < offset) {
          long nl = indexOf(LF, lineStart, Math.min(offset, size));  // ← 上界=span 起点：同行下一 span 从行首重扫
          if (nl < 0) { break; }
          lineStart = nl + 1;
          lineNumber++;
      }
      return lineStart;
  }
  // MatchAggregator.aggregate count 路径：每个 span 调用一次
  for (MatchSpan span : spans) {
      long lineStart = cursor.advanceLineStart(span.start());
  ```
- **严重程度**: P3
- **并发场景**: count 口径（`-c` 默认等价口径、HotspotProfiler count 基准口径）下每文件的命中 span 聚合在单个 worker 线程内执行（单文件串行、跨文件并行）；放大因子 = 单文件内同行命中密度 × 行长，与并发度正交。
- **触发时序**: 单行内容起点 L、长度 ℓ、其上 M 个升序 span（minified JS/JSON/数据文件典型形态）：第 i 次调用 `advanceLineStart(span_i)` 时 while 条件 `lineStart(L) < offset` 成立，`indexOf(LF, L, span_i)` 从**行首**扫描到 span_i 起点——前一次调用已扫过的前缀被整体重扫，无任何记忆。总扫描 ≈ Σ|L..span_i| ≈ M×ℓ/2。量化链：5MB 单行文件、10k 命中 → ~25GB 字节扫描/文件（~10GB/s 内存扫描 ≈ 2.5s 单文件），与 C9-004 text 路径的量化完全同形——但发生在 count 快速路径。span 恰在行首（offset==lineStart）时零扫描；跨行 span 序列则逐字节 telescoping 均摊 O(n) 成立。
- **现状**: 行起点游标单调推进，但"本行内已扫描到哪"没有水位记忆；同行第 2..M 个 span 每个都重付行前缀扫描。初审 C9-004 失效面枚举中"`advanceLineStart`（均摊 O(n)，OK）"的判定只覆盖了跨行场景，漏了同行簇——本轮按 F3 同族回扫纠正。
- **风险**: 高密度同行命中 + 超长单行文件在 count 口径（rg -c 等价、也是基准裁定口径）下退化为二次方级扫描，该 worker 成为 straggler（`invokeAll` 等最慢任务），与 C9-004 的 text 口径风险对称；count 是性能敏感口径，基准数字被该放大污染的风险真实。
- **建议**: LineCursor 增加单字段行内扫描水位（如 `long lineScanHighWater`：advanceLineStart 在 `lineStart` 未变且 `offset <= lineScanHighWater` 时直接返回，扫描推进时更新水位），O(1) 消除同行重扫；与 C9-004 的行级缓存属同一缺陷族的单一修复面（LineCursor 本地改造，一处类内两方法），复核/合并裁定可按"同族相邻"并入单一修复工单。
- **失效面枚举**: LineCursor 方法面全清单（对照初审 C9-004 枚举纠正）——`advance`（C9-004 已立案，行尾后向扫描重付，同族）；`advanceLineStart`（**本条**，行前缀重扫，初审误判 OK 的纠正点）；`text`（每行一次消费驱动，OK）；`indexOf`（单次线性扫描，H3 口径 OK）；调用方——`MatchAggregator.aggregate` count 路径 L38-49（本条生效点）；`buildLineMatches` text 路径 L62-79（C9-004 生效点）；`SearchCoordinator` 不直接消费 LineCursor（OK）。
- **信心水平**: 确定（扫描边界 `Math.min(offset, size)` 与 while 条件即证据；同行重付链可用 3-span 手工 trace 复现；未跑 corpus 实测）。
- **误报排除**: 为什么不是对 C9-004 的重复立案——失效层不同（count 快速路径 vs text 聚合路径），且初审对该方法的"OK"判定是**错误否定**而非已报告项；为什么不是"JIT 可向量化逐字节扫描"类误报（H3 口径）——立案对象是同行内重复扫描的算法性放大，非单次扫描代价；为什么不是凑数条目——有与 C9-004 同型的 25GB/文件量化链，且 count 是基准裁定口径（影响基准数字可信度）。
- **复核状态**: 已复核（2026-10-22 C6/C9 维度独立复核——保留 P3，纠正依据核实成立，与 C9-004 合并为单一 LineCursor 修复面，见文末《维度复核结论》）

### [C9-R2-002] CompiledGlob 多 `**` 段回溯匹配无 memoization：病态 pattern 下串行 glob 过滤段 O(depth^k) 放大（参考实现 globset 为 DFA 线性）

- **文件**: `nop-tools/nop-rg/nop-rg-core/src/main/java/io/nop/rg/core/glob/CompiledGlob.java:111-137`；串行生效段 `nop-tools/nop-rg/nop-rg-core/src/main/java/io/nop/rg/core/coordinator/SearchCoordinator.java:103-107`
- **证据片段**:
  ```java
  if (isDoubleStar) {
      // 中间 **：尝试消耗 0..(n-si) 个路径段（含 0，如 a/**/b 命中 a/b）
      for (int take = 0; take <= paths.size() - si; take++) {
          if (matchComponents(pats, pi + 1, paths, si + take)) {   // ← 每个 ** 段枚举全部 take，无 (pi,si) 失败记忆
              return true;
          }
      }
      return false;
  }
  ```
- **严重程度**: P3
- **并发场景**: glob 过滤在 `search()` 的**任务提交之前的主线程串行段**执行（SearchCoordinator.java:103-107，先于 `pool.invokeAll`）——放大发生在整体并行度的门阀上，串行 stall 直接抵消后续并行扫描收益。FindCoordinator.find 的同名串行段（FindCoordinator.java:77-85）同构生效。
- **触发时序**: pattern 含 k 个中间 `**` 段、且其后继段可吞任意单段（如 `**/*/**/x.java`，k=2）时，对不匹配路径第 pi 个 `**` 的 take 循环 0..n 全枚举、进入后继 `**` 又全枚举 → 单次 matches 调用 O(depth^k) 次递归/segmentMatches。量化链：depth-20 路径 + `**/*/**/x` → 量级 10^2-10^3 次段比较/路径/glob（常规 pattern 如 `**/node_modules/**` 因字面段快速失配实际 O(n)，H12 的数百 ns 量化对应的是这类常态）；1 个此类 glob × 10 万文件 × 10^3 次段比较（每次 ~10-50ns + splitPath 分配）→ 秒级串行 stall，全部发生在并行扫描开始之前。k 无上限（用户可控 pattern），深度 n = 路径段数。
- **现状**: 组件级带回溯匹配（`**` 段枚举 + segmentMatches 星号回溯）无任何 (pi, si) 失败状态记忆；对照参考实现 rg globset 将 glob 编译为 DFA（线性匹配），本实现为纯回溯形态。初审 H12 只量化了常规 pattern 的每路径均摊代价，未覆盖该最坏形态；无输入侧防护（pattern 复杂度不限）。
- **风险**: 用户提供的 glob 形态（多 `**` + 通配后继段）× 深目录树可在主线程串行段制造秒级 stall，且无超时/无降级；作为库（SearchCoordinator 公开 API）被复用时该串行段随调用方放大。不掩盖正确性（回溯枚举语义正确），C9 默认 P3。
- **建议**: `matches()` 调用内增加 `HashSet<Long>`（pi<<32|si 编码）记忆失败状态，将最坏形态压到 O(pats×paths) 多项式（改动局限 matchComponents 单方法，语义不变——枚举语义下失败状态重入结果恒同）；可选补充：deep pattern（`**` 段 ≥3）编译期 stderr 提示。基准缺口沿用 BG 表形态：GlobBenchmark 增加 `**/*/**/x` 病态 pattern × depth-20 树场景。
- **失效面枚举**: glob 匹配面全清单——`CompiledGlob.matchComponents`（本条，多 `**` 枚举无记忆）；`segmentMatches`（单段两指针回溯，O(pat×val) 有界，OK）；`splitPath`/`basename`（每 glob 每 path 分配，H12 已量化接受，OK）；`GlobMatcher.accept/excluded`（线性遍历 include/exclude 列表，规则数小，OK）；串行生效段两处——`SearchCoordinator.search:103-107`（rg 侧）、`FindCoordinator.find:77-85`（find 侧，同构本条生效）；`CompiledGlob.compile` 静态 CACHE（H23 已判正常）。
- **信心水平**: 高（回溯枚举结构即证据，O(depth^k) 为构造性最坏形态；实际触发依赖用户 pattern 形态，常态 pattern 量化与 H12 一致）。
- **误报排除**: 为什么不是把"回溯实现看着不如 DFA 优雅"当问题——有触发形态 × 串行段位置的量化链，且参考实现对同一语义是线性成本；为什么不是 H12 重复——H12 量化的是常规 pattern 均摊（空 glob 短路 + 数百 ns），本条立案对象是多 `**` pattern 的最坏形态与串行门阀位置，初审未覆盖；为什么不是 C5——无内存增长（递归深度受 pattern 段数约束）。
- **复核状态**: 已复核（2026-10-22 C6/C9 维度独立复核——保留 P3，见文末《维度复核结论》）

## 3. 本轮结论

- 新立案：P0=0，P1=0，P2=0，P3=2（C9-R2-001 count 路径同行重扫、C9-R2-002 多 `**` 回溯串行放大）。
- SIMD vector 4 文件扩大全查结论：维持初审 §3 通过判定——扫描循环零分配（S1/S2 逐项复核），无新立案。
- 初审判定的本轮纠正：C9-004 失效面对 `advanceLineStart` 的"均摊 O(n)，OK"被证伪（同行 span 簇场景），已按同族相邻立案 C9-R2-001，复核时应与 C9-004 合并裁定单一修复面（LineCursor 行级水位缓存）。
- watch 登记（低于立案门槛，交复核酌情）：nop-find 每候选 ≤5 短命字符串 + 30ms 级串行 relativize（S6）；nop-bytecode collect 每 artifact 2-3 次附加 stat（S7）；JsonOutput 控制字符 String.format（S8）。
- 清扫终止信号已给出（§1 末），无遗留线索。

---

# C9 维度复核结论（独立复核）

- **复核人**: C6+C9 维度独立复核子代理（独立 task，与初审/R2 深挖轮非同一 agent）
- **复核日期**: 2026-10-22
- **方法**: 逐条重读 live 源码 file:line（ParallelFileWalker / SearchCoordinator / LineCursor / MatchAggregator / RegexSearcher / CompiledGlob / NopBytecodeMain / ClassArtifactCollector / FindCoordinator）；量化证据链逐条验算；重点核实 C9-R2-001 对初审 C9-004 失效面"OK"判定的纠正依据（advanceLineStart 同行 span 场景手工 trace）。基线 commit 5e84b88a37 后审计单元源码无相关变更。
- **盲审边界说明**: 复核阶段读取了本目录 `C4-thread-pools.md`（同族判级对齐参照）与 `ai-dev/audits/cra-index.md`（只读；尚无 WI 6.1 条目）；未修改其他维度文件与产品代码。

## 逐条判定

| 条目 | 判定 | 复核依据（file:line） |
|---|---|---|
| C9-001 | **保留，降级 P2→P3** | 结构证据核实成立（ParallelFileWalker.java:144+161 每子项 isDirectory+isRegularFile 双 stat、:162-164 每常规文件一次 `synchronized(context)` 全局锁；:96 volatile error/:102-106 recordError check-then-act 良性竞态维持 H11 判定）。**判级复核**：发现自身量化链显示锁串行段（100k 文件 ≈5-10ms 下限、争用放大 10-50ms）相对 stat 主导成本（0.2-0.4s）为二阶，且信心水平行自注"复核可酌情降 P3"——按本维度二阶性能面默认 P3 校准降级。批量化改造建议（局部 List+addAll 一次入锁、属性随遍历携带消第二笔 stat）成立，与 BG-1 基准联动验证 |
| C9-002 | **保留 P2** | NopBytecodeMain.java:121-124 每 artifact 调 readBytes、:244 每次重开 `new JarFile`（= 同一 jar N 个 entry 重开 N 次）、对照 ClassArtifactCollector.java:118 每 jar 一次，全部核实；manifest 增量只护 collect 侧、read-back 每次全量付费确认；30k-entry fat jar ≈30s 级重复开销量化链合理。P2 恰当 |
| C9-003 | **保留 P3** | RegexSearcher.java:36 `new Utf8OffsetMap(content)` 在 :39 首次 `matcher.find()` 之前无条件构建；`int[len+1]`≈4B/char（:67-82）与 O(len) codePointAt 扫描确认；零命中文件全额付费确认。P3 恰当 |
| C9-004 | **保留 P3** | LineCursor.java:42 `contentEnd = indexOf(LF, offset, size)` 每 span 前向扫至行尾、:34-41 while 循环行前缀重扫、MatchAggregator.java:62-63 同行每 span 一次 advance，确认。P3 恰当。**注意**：其失效面枚举对 `advanceLineStart` 的"均摊 O(n)，OK"为错误否定，由 C9-R2-001 纠正（纠正成立，见下）——两案合并裁定 |
| C9-R2-001 | **保留 P3，与 C9-004 合并为单一修复面** | **纠正依据核实成立**：advanceLineStart（LineCursor.java:67-74）while 上界 `Math.min(offset, size)` = span 起点；同行 span 序列行内无 LF → `lineStart` 停在行首不再推进 → 第 2..M 个 span 各自从行首重扫前缀 ≈ M×ℓ/2（3-span 手工 trace 复现）；跨行序列 telescoping 均摊成立——初审"OK"判定只覆盖跨行场景，确为错误否定。生效点 MatchAggregator.java:38-49（count 路径）核实。与 C9-004 同为 LineCursor 类内缺陷（行级水位缓存一处两方法），合并为单一修复工单 |
| C9-R2-002 | **保留 P3** | CompiledGlob.java:113-127 中间 `**` 段 take 0..n 全枚举、无 (pi,si) 失败记忆核实；串行生效段 SearchCoordinator.java:103-107（glob 过滤先于 invokeAll 的主线程段）与 FindCoordinator.java:79-85 同构核实；O(depth^k) 构造性最坏形态成立、k 用户可控无输入防护；常态 pattern 字面段快速失配 O(n) 与 H12 量化一致。P3 恰当 |

### CL 确认（本维度相关）

- **CL-201 确认**（C6 文件 §4，C5 方向）：分配浪费半边已由 C9-003 承接；「峰值堆 × 并发乘数」包络验算维持移交 C5。详见 C6 文件《维度复核结论》。
- **CL-202 确认且闭环**：NopBytecodeMain.java:244 核实，已由 C9-002 承接立案（P2），不再独立跟踪。
- **CL-301（转 C1）/CL-302（转 C7）**：移交面维持，不在 C6/C9 复核范围。

## 最终保留项

| 编号 | 严重程度（复核后） | 修复去向 |
|---|---|---|
| C9-001 | P3（降） | 批量化改造（锁 O(目录数) + 属性随遍历携带），BG-1 基准验证 |
| C9-002 | P2 | per-input 单实例 JarFile 下沉（对齐 collectJar 形态），BG-5 基准验证 |
| C9-003 | P3 | Utf8OffsetMap 惰性化（首次命中再构建），BG-3 基准验证 |
| C9-004 | P3 | 并入 LineCursor 行级水位缓存修复面（与 C9-R2-001 同一工单） |
| C9-R2-001 | P3 | 同上（合并单一 LineCursor 修复工单） |
| C9-R2-002 | P3 | matchComponents 增加 (pi,si) 失败记忆压多项式；病态 pattern 场景入 BG 表基准 |

- **汇总**：6 项全部保留，0 驳回；降级 1 项（C9-001 P2→P3）；C9-004 与 C9-R2-001 合并为单一 LineCursor 修复面；P0/P1 仍为 0。SIMD 专项（§3）与 H1-H24 热路径清单判定复核维持。
