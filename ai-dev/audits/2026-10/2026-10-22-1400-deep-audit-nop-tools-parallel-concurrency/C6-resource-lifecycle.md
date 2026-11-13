# C6 资源生命周期与泄漏 — 初审报告

- **审计单元**：nop-tools/nop-rg（nop-rg-core / nop-rg-cli / nop-rg-vector / nop-rg-benchmark）+ nop-tools/nop-find（nop-find-core / nop-find-cli）+ nop-tools/nop-bytecode
- **批次**：全单元单批（84 文件 / 6,796 行，与 C1 批次口径一致；`target/` 生成物不在审计面）
- **维度**：C6 资源生命周期与泄漏（方法论：`ai-dev/skills/concurrency-resource-audit-prompt.md` C6 正文 + `ai-dev/skills/deep-audit-prompts.md` 共享提示词前缀）
- **基线**：`_tmp/cra-baseline-nop-tools-parallel.txt`（2026-10-22T19:37:18Z @ 5e84b88a37，FFM/Arena/SIMD 专项 + C6 资源点 24 处）
- **盲审声明**：本报告为第 1 轮盲审初审。审计过程未读取 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`、`ai-dev/backlog/` 任何历史记录；所有现状均为本轮独立判定，未经与既往发现对表。初审输出只是线索，最终结论以独立复核为准。
- **CL 前缀**：本文件发现编号 C6-001 起；维度集外强信号线索以 CL-2xx 编号附记；主线索 CL-001（ChunkedFileReader 映射累积+双映射）在本文件 §3 裁定。

---

## 1. 资源获取点配对表（逐项判定）

盘点口径：全单元 main 代码 grep（`FileChannel.open|new *Stream|Files.new*|Files.walk|Files.list|ProcessBuilder|new Recording|RecordingFile|getResourceAsStream|ExecutorService|ForkJoinPool|CoreInitialization`）逐处人工核对。基线 "C6 resource points: 24" 为 grep 命中数参考，本表为人工逐点判定（口径不同，不以基线计数为结论）。

| # | 获取点（file:line） | 资源 | 释放机制 | 成功路径 | 异常路径 | 判定 |
|---|---|---|---|---|---|---|
| 1 | MappedFileReader.java:30-31 | FileChannel + confined Arena | try-with-resources（close 幂等，`closed` 标志） | ✅ | ✅ 构造器失败 `finally` 内 `closeChannelAndArena()`（L37-41）自清理 | 配对 ✅ |
| 2 | ChunkedFileReader.java:46-47 | FileChannel + confined Arena | 同上（L94-105 幂等 close） | ✅ | ✅ 构造器失败自清理（L54-58）；close 后 `nextChunk` 返回 null（L72）不产生关闭后新映射 | 配对 ✅（累积问题见 C6-001） |
| 3 | ChunkedFileReader.java:81 `channel.map(...)` | 每块映射视图 → 共享 arena | 仅随 `arena.close()`（L99）批量释放，无逐块释放 | ✅ | ✅（随 #2 的 TWR） | 见 C6-001 |
| 4 | SearchCoordinator.java:163, L221 | MappedFileReader（两处消费点） | try-with-resources | ✅ | ✅（binary 嗅探提前 return 也在 TWR 内，L198-200） | 配对 ✅ |
| 5 | SearchCoordinator.java:189 | ChunkedFileReader | try-with-resources | ✅ | ✅ | 配对 ✅ |
| 6 | SearchCoordinator.java:113 | ForkJoinPool（每次 search 新建） | `finally pool.shutdownNow()`（L131） | ✅ | ✅（InterruptedException/ExecutionException 均经 finally） | 配对 ✅（取消路径释放延迟见 C6-002） |
| 7 | ParallelFileWalker.java:125 | `Files.list` 目录流 | try-with-resources | ✅ | ✅ | 配对 ✅ |
| 8 | ParallelFileWalker.java:71 | ForkJoinPool（每次 walk 新建） | `finally pool.shutdownNow()`（L75） | ✅ | ✅ | 配对 ✅ |
| 9 | FindCoordinator.java:145 | FixedThreadPool（每次 find 新建） | `finally pool.shutdownNow()`（L160） | ✅ | ✅（InterruptedException 外层捕获 + finally） | 配对 ✅ |
| 10 | JfrSupport.java:33 | JFR Recording | start 失败 `finally recording.close()`（L44-48）；返回的 AutoCloseable 内 stop→dump→`finally close`（L49-59） | ✅ | ✅（dump IOException 包装后仍 close）；调用方 NopRgMain L104-112 / HotspotProfiler L68-71 均 finally 关闭 | 配对 ✅ |
| 11 | JfrSupport.java:63-66 | classpath InputStream | try-with-resources Reader 包装 | ✅ | ✅ | 配对 ✅ |
| 12 | HotspotProfiler.java:75 | RecordingFile | try-with-resources | ✅ | ✅ | 配对 ✅ |
| 13 | HotspotProfiler.java:50-52 | FileOutputStream("/dev/null") | 仅 flush，**无 close** | 部分 | 无 | 见 C6-004（基准工具，进程退出兜底） |
| 14 | CoordinatorEndToEndBenchmark.java:100-102 | FileOutputStream(NUL) | `@TearDown textSink.close()`（L109-111） | ✅ | ✅ | 配对 ✅ |
| 15 | RgCompareBenchmark.java:56, L69 | 子进程 Process | 仅 `waitFor()`，**无 destroy、无超时、stderr 不消费** | 部分（进程自然退出） | readAllBytes IOException 时进程不 destroy | 见 C6-003（基准代码） |
| 16 | CorpusUtil.java:61 | Files.newOutputStream | try-with-resources | ✅ | ✅ | 配对 ✅ |
| 17 | ClassArtifactCollector.java:99 | Files.walk 流 | try-with-resources | ✅ | ✅ | 配对 ✅ |
| 18 | ClassArtifactCollector.java:118 + L140 | JarFile + jar entry InputStream | 双层 try-with-resources | ✅ | ✅ | 配对 ✅ |
| 19 | CollectManifest.java:77 / L108 | newInputStream / newOutputStream | try-with-resources | ✅ | ✅ | 配对 ✅ |
| 20 | NopBytecodeMain.java:200 | Files.walk 流 | try-with-resources | ✅ | ✅ | 配对 ✅ |
| 21 | NopBytecodeMain.java:244 + L249 | JarFile + entry InputStream（每 artifact 重开，见 CL-202） | 双层 try-with-resources | ✅ | ✅ | 配对 ✅ |
| 22 | NopRgMain.java:91/L115、NopFindMain.java:92/L107、HotspotProfiler.java:42/L108、CoordinatorEndToEndBenchmark.java:104/L113 | CoreInitialization（VFS 全局初始化） | 内层 `finally destroy()` | ✅ | ✅（JFR 启动失败也经外层 finally 销毁，NopRgMain L94 注释明示对称清理） | 配对 ✅ |
| 23 | NopRgMain.java:99-102 / NopFindMain.java:96-103 | 缓冲 stdout（64KB BufferedOutputStream） | 成功路径 `out.flush()` | ✅ | ⚠️ print 抛 RuntimeException 时 flush 跳过，缓冲丢失 | watch-only（CLI 进程即将退出，后果限于输出截断） |
| 24 | 测试：GlobMatcherTest.java:144, L185 | createTempDirectory | **无删除** | ❌ | ❌ | 见 C6-005 |
| 25 | 测试：LargeFileSearchTest.java:133/150/165 | 1.2GB 临时大文件 | `finally Files.deleteIfExists`（含"无句柄/映射残留：close 后删除成功"断言意图）；外层 `@TempDir` 兜底 | ✅ | ✅ | 配对 ✅ |

**注册表对称性（C6 步骤 5）**：全单元无 add/removeListener、register/unregister、全局缓存 put/evict 模式（grep 证实：无 `GlobalCacheRegistry`、无 listener 列表、无静态可变注册表；baseline C1 static mutable = 0 处于 main 代码）。`LiteralFinderProvider` 经 ServiceLoader 每次调用重新发现，无驻留。**无未配对注册点。**

**进程退出兜底（C6 步骤 6）**：三个 CLI main 均为短进程、全部资源 try-with-resources 确定性释放、无 shutdown hook 需求。FFM Arena 为 `Arena.ofConfined()`，无 Cleaner 兜底——但两类 reader 的 javadoc 均钉死 try-with-resources 契约，且库内全部消费点合规；"调用方忘 close = 映射驻留至进程退出"属已声明契约的误用面，不立案（watch-only）。

**池生命周期模式备注**：SearchCoordinator 每次 `search()` 新建 2 个池（walker 1 + coordinator 1），FindCoordinator 每次 `find()` 新建 1 个池，均"用完即关"。无泄漏；频繁建池的分配开销属 C9/C10 性能维度，不在本维度立案。

---

## 2. 发现条目

### C6-001（P2）ChunkedFileReader 全部分块视图累积于单一 Arena，块数随文件大小线性增长，与"分块限内存"的类级意图相悖（CL-001 核实：累积成立，双映射共存不成立）

- **文件**：`nop-tools/nop-rg/nop-rg-core/src/main/java/io/nop/rg/core/io/ChunkedFileReader.java`
- **行号**：L27（arena 字段）、L71-87（nextChunk）、L94-105（close）
- **证据片段**：
  ```java
  private final Arena arena;          // L27：全生命周期唯一的 arena
  ...
  public Chunk nextChunk() {
      ...
      MemorySegment segment = channel.map(FileChannel.MapMode.READ_ONLY, viewStart, viewLen, arena);  // L81
      nextStart = chunkStart + primaryLen;
      return new Chunk(chunkStart, primaryLen, viewStart, viewLen, segment);
  }
  ...
  private void closeChannelAndArena() {
      ...
      arena.close();                  // L99：所有块映射仅在 close 一次性释放
  ```
- **严重程度**：P2
- **现状**：每次 `nextChunk()` 在同一 confined arena 内新映射一个视图，FFM 无单段 unmap API，已映射视图在 `arena.close()` 前全部共存。消费方 SearchCoordinator（L190）以 `chunkSize = min(chunkedThreshold, Integer.MAX_VALUE)` = 默认 256MiB 分块、`overlap = patternLen-1`。
- **风险**（量化）：
  1. **映射数量** = ⌈S/chunkSize⌉（S=文件大小）。Linux 默认 `vm.max_map_count=65530`，默认 chunkSize=256MiB 时需 S ≳ 16TiB 才会在 `nextChunk` 抛 `IOException("Map failed")`——触发链具体但远超常规文件包络；若调用方以小 chunkSize 注入（构造器允许任意正整数，测试已用 8-64B），GB 级文件即可击穿。
  2. **峰值映射地址空间** ≈ S × (1 + overlap/chunkSize)，非"chunkSize+overlap"。只读文件映射页为 page-cache 支撑、可被内核回收，不构成堆 OOM；64 位地址空间实际不可耗尽。本质是"分块"语义只落在"逻辑分块"上，未落在"映射驻留"上。
  3. **无泄漏**：close 确定性释放全部映射，成功/异常/提前 return 路径均经 try-with-resources 关闭（配对表 #2/#5）——本条是边界/包络缺陷，不是泄漏。
- **CL-001 裁定**：①"映射累积"——**成立**（如上，P2 立案）；②"双映射共存"——**不成立**：`SearchCoordinator.searchFileChunked` 中 ChunkedFileReader 的 try 块于 L216 结束（映射全部释放）**之后**，整文件 MappedFileReader 才于 L221 打开，两者时间上不共存；代码注释（L220"与已关闭的分块映射不冲突，审查实测双映射共存"）与当前 live code 一致。仅相邻块视图在 overlap 区间（patternLen-1 字节）内有微量瞬时共存，可忽略。
- **建议**：两选一：(a) 逐块释放——`Chunk` 实现自主关闭（每块独立 confined arena，Chunk.close() 即 arena.close()），消费方扫描完一块关一块，映射驻留回到 O(chunkSize)；(b) 保持现状但在类 javadoc/owner doc 钉死包络声明："映射驻留≈全文件（page-cache 支撑），块数上限 ⌈S/chunkSize⌉，小 chunkSize 注入时注意 max_map_count"。
- **信心水平**：高（代码路径全量核对，触发算术已验算；LargeFileSearchTest 以 1.2GB 实测通过佐证功能正确性，但无映射数/驻留断言）。
- **误报排除**：为什么不是"看起来不优雅"：有可量化风险（max_map_count 触发算术 + 分块语义与映射驻留的契约偏差）；为什么不是 C5 重复立案：本条只裁定生命周期/释放形状（累积→确定性批量释放），堆/物理内存有界性归 C5。
- **复核状态**: 已复核（2026-10-22 C6/C9 维度独立复核——保留，降级 P2→P3，见文末《维度复核结论》）

### C6-002（P2）取消/中断路径：worker 扫描循环无中断检查点，池 shutdownNow 不等待终止，search() 返回/抛出后映射与线程仍驻留至扫描自然结束

- **文件**：`nop-tools/nop-rg/nop-rg-core/src/main/java/io/nop/rg/core/coordinator/SearchCoordinator.java`（L113-132）；关联 `nop-rg-core/.../search/PreparedLiteral.java`、`.../search/ScalarByteSearcher.java`、`nop-rg-vector/.../VectorPreparedLiteral.java`（扫描主循环均无 `Thread.interrupted()` 检查，全单元 grep 证实仅 SearchCoordinator L125 一处出现 "interrupted"）
- **行号**：SearchCoordinator L116-132
- **证据片段**：
  ```java
  for (Future<...> future : pool.invokeAll(tasks)) {
      ...
  }
  } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new NopRgException("search interrupted", e);
  } ...
  } finally {
      pool.shutdownNow();   // 不 awaitTermination
  }
  ```
- **严重程度**：P2
- **现状**：`invokeAll` 被中断时对未完成任务 `cancel(true)` 后立即抛出（不等待在跑任务）；`shutdownNow()` 亦不等待。BMH/SIMD 扫描主循环（`PreparedLiteral.find` / `VectorPreparedLiteral.find` / `ScalarByteSearcher.find`）与 `ChunkedFileReader.nextChunk` 循环均不检查中断标志。
- **风险**（触发时序具体）：T1 协调线程在 `invokeAll` 等待期被中断 → invokeAll cancel 全部在跑任务并抛 InterruptedException；T2 某 worker 恰在扫描 512MB 文件的第 1 块（MappedFileReader/ChunkedFileReader 的 arena 开着）→ 扫描循环不响应中断，继续跑满整文件扫描时长；T3 `search()` 已向调用方抛出"search interrupted"，但该文件的映射与 worker 线程仍存活直至扫描自然结束才执行 TWR close。库形态下"search() 返回 ≠ 资源已释放"，取消语义形同虚设（不省 CPU）；CLI 形态由进程退出兜底（Ctrl-C 杀进程，不走向该路径）——故不到 P1（无永久泄漏，最终必释放）。
- **建议**：在 `PreparedLiteral.find`/`VectorPreparedLiteral.find` 的大步长循环（如 skip 跳跃点或每 64KB 窗口边界）插入 `Thread.interrupted()` 检查抛 NopRgException；或在 owner doc 声明"search 不可取消，中断仅加速等待方、不停止扫描方"的边界契约。
- **信心水平**：中高（invokeAll/shutdownNow 语义为 JDK 钉死行为；扫描循环无检查点经全单元 grep 证实；未实际构造中断演示）。
- **误报排除**：为什么不是 P1 泄漏：映射最终由任务自然结束时确定性关闭，仅延迟不缺席；为什么不是"原语使用不是问题"类误报：本条是复合行为（cancel 语义 × 无检查点循环 × 不等待的池关闭）构成的取消路径失效，非原语本身。
- **复核状态**: 已复核（2026-10-22 C6/C9 维度独立复核——保留，降级 P2→P3，与同族 C4-R2-001 判级对齐，见文末《维度复核结论》）

### C6-003（P3）RgCompareBenchmark 子进程未 destroy、waitFor 无超时、stderr 不消费（基准代码，按共享前缀降级）

- **文件**：`nop-tools/nop-rg/nop-rg-benchmark/src/main/java/io/nop/rg/benchmark/RgCompareBenchmark.java`
- **行号**：L56-57, L69-79
- **证据片段**：
  ```java
  Process p = new ProcessBuilder("rg", "-c", "needle", ".")
          .directory(corpusDir.toFile()).start();
  byte[] out = p.getInputStream().readAllBytes();
  p.waitFor();
  ```
- **严重程度**：P3
- **现状**：`readAllBytes` 抛 IOException 时进程不 destroy（僵尸/驻留进程风险）；`waitFor()` 无超时（rg 挂起则基准永久挂起）；stderr 管道从不消费（本场景 rg stderr 输出量极小，无管道死锁现实风险）。
- **风险**：仅影响基准运行环境（JMH fork 进程内）；生产代码不受影响。
- **建议**：`waitFor(long, TimeUnit)` + 失败分支 `p.destroyForcibly()`；按共享前缀"基准代码简化并发模型不按生产标准报 P1"校准为 P3 排期项。
- **信心水平**：高。
- **误报排除**：未按 P1/P2 报——共享提示词前缀明示基准测试专用代码的简化模型降级处理；且进程在正常路径自然退出，无主路径泄漏。
- **复核状态**: 已复核（2026-10-22 C6/C9 维度独立复核——保留 P3，见文末《维度复核结论》）

### C6-004（P3）HotspotProfiler 文本口径的 /dev/null FileOutputStream 只 flush 不 close

- **文件**：`nop-tools/nop-rg/nop-rg-benchmark/src/main/java/io/nop/rg/benchmark/HotspotProfiler.java`
- **行号**：L50-52, L60-63
- **证据片段**：
  ```java
  PrintWriter sink = textMode
          ? new PrintWriter(new BufferedOutputStream(
                  new FileOutputStream("/dev/null"), OUTPUT_BUFFER_BYTES), false)
          : null;
  ```
- **严重程度**：P3
- **现状**：`sink` 无 close 调用（仅每轮 `sink.flush()`）；fd 驻留至进程退出。对比同模块 CoordinatorEndToEndBenchmark 的同类 sink 有 `@TearDown close()`（L109-111），两处形态不对称。
- **风险**：剖析工具短进程，fd 数量 1 个，进程退出兜底；无累积。
- **建议**：补 `finally { if (sink != null) sink.close(); }`，与 CoordinatorEndToEndBenchmark 对齐。
- **信心水平**：高。
- **误报排除**：不对称性真实存在（同模块姊妹实现一处关一处不关，F8 缺陷族画像），但因仅 1 fd × 短进程，降 P3。
- **复核状态**: 已复核（2026-10-22 C6/C9 维度独立复核——保留 P3，见文末《维度复核结论》）

### C6-005（P3）GlobMatcherTest 两处 createTempDirectory 无清理，测试残留随运行次数累积

- **文件**：`nop-tools/nop-rg/nop-rg-core/src/test/java/io/nop/rg/core/glob/GlobMatcherTest.java`
- **行号**：L144（`rg-glob-compare`）、L185（`glob-stream`）
- **证据片段**：
  ```java
  @Test
  @EnabledIfSystemProperty(named = "test.rg.compare", matches = "true")
  public void testCompareAgainstSystemRg() throws ... {
      Path dir = Files.createTempDirectory("rg-glob-compare");
      // 全方法无 delete/removeRecursively
  ```
- **严重程度**：P3
- **现状**：两个测试方法创建的临时目录（含写入的文件树）无删除；同文件内其他测试及 LargeFileSearchTest 均用 `@TempDir`（JUnit 自动清理）或 finally deleteIfExists，本类这两处偏离项目自身惯例。
- **风险**：CI/本机反复运行在 `java.io.tmpdir` 留小体积残留；仅卫生问题。
- **建议**：改用 `@TempDir` 字段，与项目测试惯例归一。
- **信心水平**：高。
- **误报排除**：真实残留非猜测（代码无任何清理语句）；因体积小、频率低，P3。
- **复核状态**: 已复核（2026-10-22 C6/C9 维度独立复核——保留 P3，见文末《维度复核结论》）

### Watch-only（不立案，登记备复核）

| # | 观察点 | 为什么不立案 |
|---|---|---|
| W1 | MappedFileReader/ChunkedFileReader `closeChannelAndArena` 先置 `closed=true` 再 `arena.close()`（MappedFileReader L64-75）：`arena.close()` 抛 WrongThreadException 时 channel 不关且无法重试 | 库内消费点全部同线程创建/关闭（confined arena 契约内），无跨线程 close 调用点；防御性缺口非可达缺陷 |
| W2 | Arena 无 Cleaner 兜底，调用方忘 close = 映射驻留至进程退出 | javadoc 钉死 try-with-resources 契约（MappedFileReader L15、ChunkedFileReader 契约链），库内 100% 合规（配对表 #1-#5）；属声明内误用面 |
| W3 | ResourceLeakAnalyzer.analyze(InputStream) / NullflowAnalyzer.analyze(InputStream) 不关闭入参流（调用方契约），main 代码无调用点（仅测试可及） | 所有权归调用方是流 API 惯例；byte[] 变体为唯一生产路径 |
| W4 | NopRgMain/NopFindMain 缓冲 stdout 在 print 抛非预期 RuntimeException 时丢缓冲 | CLI 退出即兜底；仅输出截断，无资源驻留 |
| W5 | 基准 corpus 常驻 `java.io.tmpdir/nop-rg-bench-corpus`（512MB 档）不清理 | CorpusUtil.ensureFile 的幂等复用是钉死设计（"构建一次复用"注释），属已声明边界，非泄漏 |

---

## 3. CL-001 线索核实结论（C5/C6 交界）

- **线索**：ChunkedFileReader 映射累积 + 双映射共存。
- **核实结果**：拆为两半裁定——
  1. **映射累积**：成立。全部块视图在同一 confined arena 累积至 close 批量释放（见 C6-001），生命周期判定为"无泄漏、但驻留形状与分块意图偏差 + 块数上界未声明"，P2 立案于 C6-001。
  2. **双映射共存**：不成立（时间维度）。`SearchCoordinator.searchFileChunked` 的分块扫描（L189-216）与整文件行提取映射（L221-223）严格先后，TWR 关闭先于第二个映射创建；代码注释与 live code 一致。
- **移交**：内存有界性视角（峰值堆/page-cache 验算、spans 无界、regex 解码放大）归 C5 维度；本文件不重复计数。

## 4. 维度集外强信号线索（CL-2xx 附记，不展开）

- **CL-201（C5 方向）**：`RegexSearcher.findAll`（RegexSearcher.java:34-53）对 ≤chunkedThreshold（默认 256MB）文件整文件 UTF-8 解码 + 全长 `int[] charToByte`（L64-94，≈4 字节/char），单 worker 峰值堆可达 ~1.5GB；SearchCoordinator 以 `threads` 个 worker 并发执行 regex 任务，峰值叠加 = 单文件放大 × 并发数，未见 owner 级验算或声明（现有 guard 只挡"单文件 > 阈值"，L150-154）。建议 C5 维度核查并发乘数下的堆包络。
- **CL-202（C9/C10 方向）**：`NopBytecodeMain.readBytes`（NopBytecodeMain.java:244）在逐 artifact 分析循环内每次重新 `new JarFile(...)`，同一 jar 对 N 个 class 重复 open/close N 次（ClassArtifactCollector.collect 阶段是单次 JarFile 枚举）。资源安全（每次 TWR 配对）但系统调用放大 O(N)；建议提升 jar 读取为 per-input 单实例。

---

## 5. 结论摘要

- 配对盘点：26 个资源获取点（含 4 处 CoreInitialization、3 处线程池、2 处 FFM Arena、子进程/JFR/jar/walk/manifest/temp），24 处配对 ✅，2 处部分配对（基准/工具 P3），1 处测试残留（P3）。
- 发现：P0=0，P1=0，P2=2（C6-001 Arena 映射累积包络、C6-002 取消路径释放延迟），P3=3。
- 注册表对称性：无未配对注册点。进程退出兜底：三个 CLI 均确定性释放，无 shutdown hook 缺口。
- CL-001 裁定：映射累积成立（C6-001）；双映射共存不成立（顺序释放）。
- 集外线索：CL-201（regex 堆放大 × 并发，交 C5）、CL-202（JarFile 重复打开，交 C9/C10）。

# C6 深挖第 2 轮

- **轮次定位**：第 2 轮合并批深挖，追加语义（不修改前轮内容）。新发现编号 C6-R2-001 起。
- **本轮扩大面**（对照派发单）：① find/bytecode 侧资源面补扫（初审主战场在 rg 侧）；② JFR 记录文件生命周期；③ 子进程流（RgCompareBenchmark 已立案——其余 Process 用法？）。
- **盲审声明**：本轮为深挖轮，仅可读本文件（C6）与 C9-hot-path.md 两份前轮产物；未读取其他维度文件及 ai-dev/plans/、ai-dev/bugs/、ai-dev/lessons/、ai-dev/backlog/。所有判定以 live code 为准。

## 1. 扩大面清扫表（逐项判定）

| # | 检查点 | 证据（file:line） | 判定 |
|---|---|---|---|
| S1 | nop-find 是否有 JarFile/walk 类资源点 | 全单元 grep：nop-find main 代码无 JarFile、无 Files.walk/list、无流打开；内容读取为 0（FindCoordinator javadoc L36-37 明示"不读文件内容"）；唯一资源点 = FixedThreadPool（初审配对表 #9 已判配对 ✅，本轮重核 FindCoordinator.java:145-162 无变化） | 无新资源点 |
| S2 | nop-find 池取消姿势是否为 C6-002 同族 | FindCoordinator.invokeAll（L144-162）：`invokeAll` + `finally shutdownNow()` 不 awaitTermination；scoreShard 打分循环（L112-128）无中断检查点——与 C6-002 同构。但单候选打分为 µs 级纯 CPU（FuzzyFileScorer 三字段 O(len) 扫描），取消延迟上界 = 单分片扫描时长，远小于 rg 内容扫描场景 | watch（登记为 C6-002 失效面同族兄弟，不另立案） |
| S3 | bytecode 侧全部资源点重核 | NopBytecodeMain.readBytes:244 JarFile 每 artifact TWR（配对 ✅，初审 #21 复核一致；性能面归 C9-002）；NopBytecodeMain.java:200 Files.walk TWR ✅；ClassArtifactCollector.java:99 Files.walk、:118 collectJar JarFile、:140 entry InputStream 三处 TWR ✅（初审 #17/#18 复核一致）；CollectManifest.java:77 load / :108 save 双向 TWR ✅（初审 #19 复核一致） | 配对全部 ✅；发现 S4（原子性，见 C6-R2-001） |
| S4 | bytecode 持久化落盘原子性 | CollectManifest.save（L98-111）：`Files.newOutputStream(file)` 默认 CREATE+TRUNCATE_EXISTING 原地截断写，无临时文件 + ATOMIC_MOVE、无 fsync | 立案 C6-R2-001（P3，F6 族） |
| S5 | JFR 记录文件生命周期 | JfrSupport.java:30-60：`new Recording` 失败路径 started 标志 finally close（L44-48）；返回 lambda `stop → createDirectories → dump → finally close`（L49-59，IOException 包装不吞、非 IO RuntimeException 同样命中 finally）；调用方 NopRgMain.java:104-112、HotspotProfiler.java:68-71 均 finally close（初审 #10 复核一致）。JDK `Recording.close()` 语义：setToDisk(true) 的内部磁盘缓冲临时文件随 close 确定性删除；`setMaxSize(64MB)`（L41）钉死录制体积上界；dump 目标文件为调用方点名的产物文件，非泄漏 | 配对 ✅，无立案 |
| S6 | JFR dump 失败路径的资源去向 | createDirectories/dump 抛 IOException → NopRgException 上抛 → finally `recording.close()` 仍执行（内部临时文件删除，数据丢弃但显式报错：NopRgMain L108-110 转 stderr warning 不中断搜索结果；HotspotProfiler 直接传播经 finally destroy 初始化） | "失败即显式"，无静默驻留，无立案 |
| S7 | 子进程用法全单元清扫 | grep `ProcessBuilder|Runtime.getRuntime().exec`：main 代码仅 RgCompareBenchmark.java:56（`rg --version` 探测）与 :69（基准本体）两处，**均在初审 C6-003 已声明的行号范围（L56-57, L69-79）内**，无第三个 Process 用法 | 无新立案（C6-003 覆盖面确认完整） |
| S8 | 测试侧临时文件 F3 同族清扫（C6-005 兄弟） | grep 全部 test：仅 GlobMatcherTest.java:144/:185 两处 createTempDirectory 无清理（即 C6-005 本体）；其余 20+ 处全部 `@TempDir`（含 nop-find 2 文件、nop-bytecode 8 文件），无新同族兄弟 | C6-005 覆盖面确认完整，无新立案 |

**清扫表终止信号**：S1-S8 已覆盖本轮派发单全部扩大面（find/bytecode 资源补扫、JFR 生命周期、子进程流），且 S2/S7/S8 对初审发现做了 F3 同族兄弟逐一回扫；既定检查点无遗留线索，C6 深挖第 2 轮清扫终止。

## 2. 发现条目

### [C6-R2-001] CollectManifest.save 原地截断写非原子：崩溃/kill 自伤 manifest，后续运行锁定在 exit 2 直到人工删除（F6 族：rename 原子性）

- **文件**: `nop-tools/nop-bytecode/src/main/java/io/nop/bytecode/collect/CollectManifest.java:98-111`；调用点 `nop-tools/nop-bytecode/src/main/java/io/nop/bytecode/cli/NopBytecodeMain.java:152-154`
- **证据片段**:
  ```java
  public void save(Path file) throws IOException {
      Properties props = new Properties();
      ... // 全量条目序列化
      try (OutputStream out = Files.newOutputStream(file)) {   // L108：默认 CREATE+TRUNCATE_EXISTING，先截断旧 manifest
          props.store(out, "nop-bytecode collection manifest (...)");
      }
  }
  // NopBytecodeMain.java:152-154（分析循环之后才落盘）
  if (manifestFile != null) {
      manifest.save(manifestFile);
  }
  ```
- **严重程度**: P3
- **并发场景**: 单线程 CLI 写盘；失效场景非线程交错，而是写盘中断（进程被 kill / JVM 崩溃 / 磁盘满 IOException 中途抛出）与下次运行的 load 之间的跨进程时序。
- **触发时序**: T1 进程 A 执行 save：L108 打开输出流瞬间旧 manifest 被截断为 0 字节；T2 进程 A 在 `props.store` 写完之前崩溃/被 kill -9（或磁盘满抛 IOException）；T3 进程 B 以同一 `--manifest` 启动：`CollectManifest.load` 读到截断文件——末行残缺 → 列数 ≠5 → loud `NopBytecodeException("Malformed manifest row")` exit 2；且此后**每次**运行都失败，直到用户手工删除 manifest 文件。若恰在行边界截断，则 load 成功但基线静默缩水（退化为全量重分析，正确性无损）。
- **现状**: 持久化 manifest 的写路径为"先截断、后写全量"的非原子替换；corrupt→loud 是 NopBytecodeMainTest.corruptManifestFailsLoudly（:236-243）钉死的有意契约（本轮不诉该契约），缺陷在**自伤来源**与**恢复路径缺位**：javadoc（L19-21）只为 mtime 粒度声明了"清 manifest 重收集"的恢复动作，对损坏态未声明任何恢复路径，报错文案也未提示"删除该文件即可重建"。
- **风险**: 增量缓存功能被一次崩溃锁定为持续 exit 2，需人工介入恢复；对把 nop-bytecode 挂进 CI 定时任务/增量管线的使用形态，这是单点自愈缺失（F6 缺陷族画像：flush ≠ fsync、rename 原子性）。降 P3 依据：manifest 是可再生缓存（删文件即全量重建），无数据资产损失。
- **建议**: save 改为兄弟临时文件写出后 `Files.move(tmp, file, REPLACE_EXISTING, ATOMIC_MOVE)`（同目录保证同文件系统，rename 原子替换）；同时在 "Malformed manifest row" 报错文案中追加一句恢复指引（"delete the manifest file to rebuild it from scratch"）。平台设施对照：仓库惯例可查 `Files.move` + `ATOMIC_MOVE` 既有用法（如 docs 惯例中的 checkpoint 落盘形态），无需新基础设施。
- **失效面枚举**（F3：全单元持久化写出点清扫）: `CollectManifest.save:108`（本条，非原子）；`CorpusUtil.ensureFile:61`（基准 corpus，幂等重写、W5 已声明"构建一次复用"边界，且损坏无下游——不适用）；`JfrSupport` dump（L53，用户点名产物文件，失败显式抛错，重跑即重生成——不适用）；三个 CLI 的 stdout 缓冲（初审 #23/W4，非文件——不适用）；grep 证实全单元 main 代码无其余 `Files.write|newOutputStream|FileOutputStream` 文件写出点（仅 HotspotProfiler/CoordinatorEndToEndBenchmark 的 /dev/null 与 NUL sink，初审 C6-004 已立案）。同族面收口。
- **信心水平**: 高（写路径结构即证据；截断时机与 load 失败链为 JDK Properties/store 语义 + corruptManifestFailsLoudly 测试反向佐证"残缺行必 loud"）。
- **误报排除**: 为什么不是"已声明的边界"：javadoc 只对"读取到 malformed manifest → loud"做了声明（防静默丢数据），未对"写入中断产生 malformed"做任何声明或防护，两者是契约的两半而只钉了读半；为什么不是复核初审 #19（TWR 配对）——本条是落盘原子性（F6），配对完整性与其正交，初审配对表未覆盖该面；为什么不是 C7 慢性病——一次性状态损坏，非单调增长。
- **复核状态**: 已复核（2026-10-22 C6/C9 维度独立复核——保留 P3，见文末《维度复核结论》）

## 3. 本轮结论

- 新立案：P0=0，P1=0，P2=0，P3=1（C6-R2-001 manifest 非原子落盘）。
- 扩大面回扫结论：find 侧无新资源点（无内容读取，池配对复核 ✅）；bytecode 侧配对全部复核 ✅，仅原子性缺口 1 处；JFR 生命周期配对 ✅（含 JDK 内部临时文件随 close 删除语义 + 64MB 上界）；子进程面确认 C6-003 覆盖完整；测试临时文件面确认 C6-005 覆盖完整。
- 初审发现的 F3 同族回扫：C6-002 在 find 侧的同族（FindCoordinator.invokeAll）登记为 watch（S2，µs 级取消延迟，不另立案）；C6-005 无新兄弟（S8）。
- 清扫终止信号已给出（§1 末），无遗留线索。

---

# C6 维度复核结论（独立复核）

- **复核人**: C6+C9 维度独立复核子代理（独立 task，与初审/R2 深挖轮非同一 agent）
- **复核日期**: 2026-10-22
- **方法**: 逐条重读 live 源码 file:line（ChunkedFileReader / MappedFileReader / SearchCoordinator / PreparedLiteral·VectorPreparedLiteral·ScalarByteSearcher 中断面 grep / RgCompareBenchmark / HotspotProfiler / GlobMatcherTest / CollectManifest / NopBytecodeMain / FindCoordinator）；grep 全单元 `chunkedThreshold` 引用面与 `interrupted` 检查点面验证触发前提；对照 C4-thread-pools.md C4-R2-001 判定同族关系。基线 commit 5e84b88a37 后审计单元源码无相关变更。
- **盲审边界说明**: 复核阶段读取了本目录 `C4-thread-pools.md`（C4-R2-001 同族比对与判级对齐参照）与 `ai-dev/audits/cra-index.md`（只读；尚无 WI 6.1 条目）；未修改其他维度文件与产品代码。

## 逐条判定

| 条目 | 判定 | 复核依据（file:line） |
|---|---|---|
| C6-001 | **保留，降级 P2→P3** | 累积事实与 CL-001 裁定核实成立（ChunkedFileReader.java:27 单一 arena、:81 每块 map 入共享 arena、:99 close 批量释放；双映射不共存——SearchCoordinator.java:216 chunked TWR 结束严格先于 :221 整文件映射）。**触发前提现实性**：chunkSize 注入面仅两处——ChunkedFileReader 公共构造器（任意正 int，:35 javadoc 明示小值为「测试可注入」）与 SearchCoordinator 的 chunkedThreshold 构造参数；grep 证实 `chunkedThreshold` 在全单元无其他引用点，**CLI 不暴露该参数**，NopRgMain 恒用默认 256MiB（SearchCoordinator.java:56）。默认包络击穿 max_map_count=65530 需 ⌈S/256MiB⌉>65530 → S≳16TiB，非现实；小 chunkSize 属 javadoc 已声明「测试专用」参数的生产误用（同 W2「已声明误用面」教义），且失效形态为 nextChunk loud IOException（:84-85），非静默、非泄漏。发现本体（映射驻留≈全文件与分块意图的契约偏差）真实，采纳原建议 (b) 作包络声明缺陷保留 |
| C6-002 | **保留，降级 P2→P3** | 证据核实（SearchCoordinator.java:113 每次新建池、:117 invokeAll、:123-125 中断转 NopRgException、:130-132 finally shutdownNow 不等待；grep 证实 nop-rg 主代码除 :125 外零中断检查点）。**机制细化**（不改变结论）：FJP 下 `ForkJoinTask.cancel(mayInterruptIfRunning)` 忽略中断标志，worker 实际收到的中断来自 finally `shutdownNow()` 的 interruptAll——扫描循环无检查点故不响应，资源驻留延迟结论成立。同族与判级见下节 |
| C6-003 | **保留 P3** | RgCompareBenchmark.java:56-57（`rg --version` 探测）与 :69-79（rgCount：readAllBytes 后 `waitFor()` 无超时、无 destroy、stderr 不消费）逐行核实；InterruptedException 分支同样不 destroy。基准代码按共享前缀降级校准正确 |
| C6-004 | **保留 P3** | HotspotProfiler.java:50-52 sink 创建、:62 仅 flush；grep 全文件证实无 `sink.close`（唯一 close 为 :70 recording.close）；与 CoordinatorEndToEndBenchmark `@TearDown close` 不对称确认。P3 校准正确 |
| C6-005 | **保留 P3** | GlobMatcherTest.java:144（`rg-glob-compare`）、:185（`glob-stream`）createTempDirectory 无清理确认；:144 测试受 `@EnabledIfSystemProperty(test.rg.compare)` 门控（残留仅条件性累积），P3 不变 |
| C6-R2-001 | **保留 P3** | CollectManifest.java:108 `Files.newOutputStream(file)` 默认 CREATE+TRUNCATE_EXISTING 原地截断写、调用点 NopBytecodeMain.java:152-154 确认；javadoc（CollectManifest.java:18-23）确只声明「mtime 粒度→清 manifest 重收集」与「读侧 malformed→loud」两半，写中断产生的损坏无声明恢复路径；corruptManifestFailsLoudly（NopBytecodeMainTest.java:236-242）存在，反向佐证截断文件必 loud。ATOMIC_MOVE 改造建议成立。P3 恰当（可再生缓存，删文件即重建） |

### C6-002 与 C4-R2-001 同族裁定（派发单复核重点）

- **同族关系确认**：同一根因（nop-rg 未向调用方暴露任何取消通道）的两个站点——C4-R2-001 = **walk 站点**（`pool.invoke` caller uninterruptible join + ScanAction.compute 无检查点，取消对调用方完全失效）；C6-002 = **scan 站点**（caller 经 invokeAll 可中断返回，但 worker 扫描循环无检查点 + 池关闭不等待，映射与线程驻留至扫描自然结束；重试取消可堆积池/映射）。
- **主次**：C4-R2-001 为主案（失效形态更强：调用方取消完全失效、取消延迟无上界；其修复建议 `volatile boolean cancelled` + 检查点即根因修复的 walk 半边），C6-002 为从案（scan 半边）。
- **合并建议**：并卷单一「nop-rg 取消通道」修复工单——① WalkContext cancelled 标志 + ScanAction 检查点（C4-R2-001）；② PreparedLiteral/VectorPreparedLiteral 扫描大步长（skip 跳跃点或 64KB 窗口边界）中断检查点（C6-002）；③ 两者 javadoc 钉死取消契约。FindCoordinator 同族兄弟（R2 清扫表 S2 watch，单分片 µs 级延迟）不进工单。
- **判级对齐**：两站点可达性前提相同（in-tree 无中断源、CLI SIGINT 直接杀进程不走向该路径、仅库嵌入长驻进程可达）；C4 复核已按「in-tree 零触发源→P3（嵌入前提 P2 面）」维持 C4-R2-001 P3，C6-002 同口径降级，跨维度一致。

### CL-001 裁定复查与 CL-201/202 确认

- **CL-001 裁定维持**：双映射共存不成立（时序确认如上）；映射累积成立（C6-001 承接）。
- **CL-201 确认**（C5 方向）：decode 全文件解码（RegexSearcher.java:47-53）+ `int[len+1]`≈4B/char（:67-82）算术核实；guard 仅挡单文件 >阈值（SearchCoordinator.java:150-154）确认。其「分配浪费」半边已由 C9-003 承接立案；「峰值堆 ~1.5GB/worker × 并发乘数」包络验算维持移交 C5，仍待 C5 维度核查。
- **CL-202 确认且已闭环**（C9/C10 方向）：NopBytecodeMain.java:244 每 artifact 重开 JarFile 核实，已由 C9-002 承接立案（P2）；「per-input 单实例」建议与 C9-002 一致，CL-202 不再独立跟踪。
- watch W1-W5 维持（其中 W1/W2 与 C6-001/002 降级后的包络声明建议可合并落地）；R2 清扫表 S1-S8 结论复核无异议。

## 最终保留项

| 编号 | 严重程度（复核后） | 修复去向 |
|---|---|---|
| C6-001 | P3（降） | 包络声明缺陷：javadoc/owner doc 钉死「映射驻留≈全文件（page-cache 支撑）+ 块数上界 ⌈S/chunkSize⌉ + 小 chunkSize 的 max_map_count 注意事项」（原建议 (b)） |
| C6-002 | P3（降） | 并入「nop-rg 取消通道」单一修复工单（主案 C4-R2-001，scan 半边为本条） |
| C6-003 | P3 | waitFor(timeout) + destroyForcibly，排期项 |
| C6-004 | P3 | 补 sink.close() 与 CoordinatorEndToEndBenchmark 对齐 |
| C6-005 | P3 | 改 @TempDir 归一测试惯例 |
| C6-R2-001 | P3 | save 改临时文件 + ATOMIC_MOVE；报错文案补恢复指引 |

- **汇总**：6 项全部保留，0 驳回；降级 2 项（C6-001/002 P2→P3）；P0/P1 仍为 0。全单元资源配对结论（初审 §1/§5、R2 §3）复核维持。
