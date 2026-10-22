# C4 线程池与执行器治理 — 初审（盲审）

- **审计单元**: nop-tools/nop-rg + nop-tools/nop-find + nop-tools/nop-bytecode（全单元，同 C1 批次口径：src/main 84 文件 / 6,796 行；benchmark 742 行不立案）
- **维度**: C4 线程池与执行器治理（含 roadmap 点名 FFM/Arena 并行遍历专项）
- **批次**: 全单元单批（第 1 轮初审）
- **盲审声明**: 本文件为盲审产出。审计过程中未读取 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`、`ai-dev/backlog/` 下任何文件（含本目录兄弟维度文件），未修改任何源码/文档。
- **CL 前缀**: 接收线索 CL-002（每调用新建 ForkJoinPool/ExecutorService 无命名）→ 本文件 C4-001 核实并判级；本维度新产出的维度集外线索以 **CL-101、CL-102** 附记。
- **基线**: `_tmp/cra-baseline-nop-tools-parallel.txt`（C4 节命中 3 文件 + 1 package-info；FFM 专项命中 17 文件）。live 复核 grep 口径：
  - 池创建点：`grep -rn -E "new Thread\(|ExecutorService|Executors\.|newFixedThreadPool|newCachedThreadPool|ForkJoinPool|IThreadPool|CompletableFuture|parallelStream|\.parallel\(\)|commonPool|scheduleAtFixedRate|Timer\(|addShutdownHook" --include="*.java" nop-tools/{nop-rg,nop-find,nop-bytecode} -r`（排除 `*/target/*` 与 `*/test/*`）→ 池创建点恰 3 处，`new Thread` 0 处，commonPool/并行流/CompletableFuture 0 处，定时调度 0 处，shutdown hook 0 处。
  - FFM：`Arena` 仅命中 `MappedFileReader.java`、`ChunkedFileReader.java` 两处（均 `Arena.ofConfined()`）。
  - 计数经两个独立 grep 口径交叉验证（方法论文共享前缀第 10 条）。

---

## 一、执行模型清单表（池 × 参数 × 队列 × 拒绝/失败语义 × 关闭路径 × 判定）

| # | 创建点 | 池类型/参数 | 生命周期 | 任务粒度 | 队列 | 拒绝/失败语义 | 关闭路径 | 判定 |
|---|--------|------------|----------|----------|------|----------------|----------|------|
| 1 | `ParallelFileWalker.walk()` — `nop-rg-core/.../walk/ParallelFileWalker.java:71` | `new ForkJoinPool(threads)`，默认 `availableProcessors`，默认 thread factory（无命名），asyncMode=LIFO | **每次调用新建**，调用内关闭（javadoc L60 明示契约） | 目录级 `RecursiveAction`，`invokeAll` 分治，work-stealing 自适应 | FJ 工作窃取队列（任务数=目录数，天然有界） | 遍历 I/O 错误 → `context.error` volatile 首错胜出 + 快速失败（后续任务 L120 短路）；unchecked 异常经 FJ `invoke` 传播出 `walk()` | `finally pool.shutdownNow()`，与 invoke 严格配对 | **通过**（附 C4-001：无命名/每调用新建，P3） |
| 2 | `SearchCoordinator.search()` — `nop-rg-core/.../coordinator/SearchCoordinator.java:113` | `new ForkJoinPool(Math.max(1, threads))`，默认 factory（无命名） | **每次调用新建**，方法内关闭 | 文件级 `Callable`（每文件一个任务），主线程先做 glob 过滤再提交 | FJ 提交队列 = 任务数（文件数，随输入增长 → CL-102） | `invokeAll` 全量等待；**首错 `future.get()` 抛出即丢弃其余全部已完成结果**（→ C4-002）；`InterruptedException` → 复位中断位 + 抛 `NopRgException` | `finally pool.shutdownNow()`，配对正确 | **通过**（附 C4-001、C4-002） |
| 3 | `FindCoordinator.invokeAll()` — `nop-find-core/.../coordinator/FindCoordinator.java:145` | `Executors.newFixedThreadPool(Math.max(1, threads))`，默认 factory（无命名） | **每次调用新建**，方法内关闭 | 分片级 `Callable`，任务数 = `min(threads, candidates.size())` | LBQ（JDK 固定池默认），但任务数 ≤ 线程数，实质有界 | 分片失败 → `NopFindException` fail-fast；中断 → 复位 + 抛异常 | `finally pool.shutdownNow()`，配对正确 | **通过**（附 C4-001；池大小可取 workerCount 省去冗余空线程，cosmetic） |
| 4 | FFM 并发域 | `MappedFileReader.java:31`、`ChunkedFileReader.java:47` 均 `Arena.ofConfined()` | per-task：构造于池任务线程内，try-with-resources 于同任务同线程关闭 | 单文件单任务独占 segment | — | 无跨线程共享 segment / 无 shared arena 的使用点；confined arena 的 owner-thread fail-fast（`WrongThreadException`）仅为 JDK 兜底，非设计依赖——两条路径（`searchFile` 整文件、`searchFileChunked` 分块 + 二次整文件映射提行）均在单线程内完成分配、访问、释放 | AutoCloseable + try-with-resources，异常路径由构造器内 `finally !ok → closeChannelAndArena()` 兜底（MappedFileReader:37-41、ChunkedFileReader:54-58） | **通过**（主动设计，非兜底依赖） |
| 5 | commonPool 间接使用（并行流 / 无 executor 的 CompletableFuture） | — | — | — | — | 全单元 0 处；walker javadoc L19 明示「专用线程池，非 commonPool」决策 | — | **通过** |
| 6 | 定时任务 | — | — | — | — | 全单元 0 处（无 `scheduleAtFixedRate`/`Timer`/`ScheduledExecutor`） | — | **通过** |
| 7 | CLI 线程模型（NopRgMain:76-79、NopFindMain:65-68） | main 单线程单发：picocli execute → 阻塞式 coordinator 调用 → `System.exit` | 进程级 | — | — | 错误归位 exit 2；SIGINT/SIGTERM 走 JVM 默认处理器直接终止进程，OS 回收映射与线程（无 shutdown hook 需求） | `CoreInitialization.destroy()` finally 对称清理（NopRgMain:113-117） | **通过**；watch-only：walk 阶段 `pool.invoke` 不响应中断（无取消路径），中断状态保留至下一个 j.u.c 阻塞点（invokeAll）才生效——CLI 单发模型下可接受，库化嵌入时需注意 |
| 8 | nop-bytecode 执行模型 | **纯同步单线程**管线（collect → read-back → analyze → render），`Files.walk` 顺序流，0 线程/0 池 | — | — | — | 超时经 `--max-runtime-seconds` 计算 `deadlineNanos` 轮询传入 analyzer（NopBytecodeMain:115-117、125-126、138-139），无定时线程 | — | **通过** |

**任务隔离核对（清单步骤 4）**: 三处池均为「每命令/每调用独立」，无跨命令共享池（一个慢业务饿死其他业务不成立）；walker 任务内 `invokeAll` 为同池 fork/join 递归（FJ 语义允许 worker 帮助执行，无池耗尽互等死锁）；search 池在 walker 池完全关闭后才创建（L88 先 `walk()` 返回，L113 才建池），两池顺序相位、无叠占。无 caller-runs 策略。

**共享任务输入安全发布核对**: 池任务跨线程读取的主线程产物——`RegexSearcher`（final `Pattern`，`findAll` 无状态）、`PreparedLiteral`/`VectorPreparedLiteral`（全 final 域，构造后只读）、`FuzzyFileScorer`（immutable，javadoc L27 明示可并行）、`GlobMatcher`（仅主线程于建池前消费，不进任务）、`TopKCollector`（javadoc L20 明示非线程安全，per-thread 实例 + 主线程 `mergeFrom` 归并）——均经任务提交的 happens-before 边发布，安全。唯一例外见 C4-003。

---

## 二、风险立案

### C4-001 每调用新建 ForkJoinPool/ExecutorService 且线程无命名（CL-002 核实：属实，判级 P3）

- **文件**: `nop-tools/nop-rg/nop-rg-core/src/main/java/io/nop/rg/core/walk/ParallelFileWalker.java:60-76`；`nop-tools/nop-rg/nop-rg-core/src/main/java/io/nop/rg/core/coordinator/SearchCoordinator.java:113-132`；`nop-tools/nop-find/nop-find-core/src/main/java/io/nop/find/core/coordinator/FindCoordinator.java:145-161`
- **证据片段**:
  ```java
  // SearchCoordinator.java:113-117（walker L71 / find L145 同型）
  ExecutorService pool = new ForkJoinPool(Math.max(1, threads));
  try {
      Map<String, FileMatches> results = new TreeMap<>();
      try {
          for (Future<AbstractMap.SimpleEntry<String, FileMatches>> future : pool.invokeAll(tasks)) {
  ```
- **严重程度**: P3
- **并发场景**: CLI 一次性调用场景下 main 线程阻塞于 `search()`/`find()`；池工作线程（FJ 默认名 `ForkJoinPool-N-worker-M`、固定池默认名 `pool-N-thread-M`）执行文件扫描/打分任务。
- **触发时序**: 非交错型缺陷。核实结论：三处池创建点**均为每调用新建**、**均未传 ThreadFactory**；但生命周期配对全部正确（try/finally + `shutdownNow`），walker 侧 javadoc（L60）已把「每次调用独立执行（新建 ForkJoinPool 并在结束后关闭）」钉为契约。CL-002 的「无关闭配对」疑点**不成立**，成立的是「无命名」这一半。
- **现状**: 线程无可观测命名。该单元以 `--jfr`（JfrSupport，NopRgMain:72-74）作为一等诊断特性输出 CPU/alloc/lock 事件，未命名 worker 线程在 JFR 火焰图与线程 dump 中无法归因到 rg/find 的遍历 vs 搜索相位；另 `SearchCoordinator.java:28` 残留未使用的 `import java.util.concurrent.Executors`（实现从固定池切到 ForkJoinPool 的痕迹），佐证该处配置经过轮换、更需要显式工厂钉住语义。
- **风险**: 诊断面劣化（性能问题的 JFR 归因成本升高）；库化嵌入（SearchCoordinator/FindCoordinator 是 public 库 API，本仓库外可被服务进程循环调用）时每次调用建/拆线程组造成线程抖动。当前仓库内仅 CLI 单发调用（embedder grep：非测试/非 benchmark 的构造点仅 NopRgMain:131、NopFindMain:127），风险未兑现，故 P3。
- **建议**: 为三处池传入命名 ThreadFactory（对齐平台 `NamedThreadFactory`，如 `nop-rg-walk-%d` / `nop-rg-search-%d` / `nop-find-shard-%d`）；FindCoordinator 池大小取 `workerCount` 而非 `threads`（任务数 = min(threads, n)，避免多余空线程）；若后续服务化嵌入，再评估 coordinator 持有可复用池（需同步补 stop/destroy 生命周期，当前调用方作用域关闭是对的最简选择）。顺带删除 SearchCoordinator:28 未用 import。
- **失效面枚举**（同族全部 3 处创建点逐一判定）:
  1. `ParallelFileWalker.java:71` — 每调用新建 + 无命名（本条立案）
  2. `SearchCoordinator.java:113` — 每调用新建 + 无命名（本条立案）
  3. `FindCoordinator.java:145` — 每调用新建 + 无命名（本条立案）
  4. 全单元其余文件：`new Thread`/commonPool/定时调度/shutdown-hook grep 均 0 命中，无漏网点。
- **信心水平**: 确定
- **误报排除**: 不属于「约定大于配置」校准——命名缺失不是 Nop 平台标准模式，且与本单元自身主打的可观测特性（--jfr）直接冲突；也不属于「生成产物」校准（全部手写代码）。生命周期配对正确这一点已核实并写入现状，避免把 CL-002 夸大为泄漏缺陷。
- **复核状态**: 已复核（2026-10-22）——保留 P3（见文末维度复核结论）

### C4-002 invokeAll 失败聚合语义：单文件瞬时错误丢弃全部已完成结果（fail-fast 未声明 + 与 rg 行为背离）

- **文件**: `nop-tools/nop-rg/nop-rg-core/src/main/java/io/nop/rg/core/coordinator/SearchCoordinator.java:114-128`（聚合点）；`146-148`、`163`、`189`、`221`（错误源）
- **证据片段**:
  ```java
  // SearchCoordinator.java:117-127
  for (Future<AbstractMap.SimpleEntry<String, FileMatches>> future : pool.invokeAll(tasks)) {
      AbstractMap.SimpleEntry<String, FileMatches> entry = future.get();
      if (entry.getValue() != null) {
          results.put(entry.getKey(), entry.getValue());
      }
  }
  } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new NopRgException("search interrupted", e);
  } catch (ExecutionException e) {
      throw new NopRgException("search task failed", e.getCause());
  }
  ```
- **严重程度**: P2
- **并发场景**: 搜索池 worker 线程执行单文件任务 vs. 外部进程并发变更文件树（构建/checkout/git 操作）。池并行度 = CPU 核数，大树上 walk 与后半文件的处理窗口达秒级。
- **触发时序**: T1（walker）在 t0 列出文件 F 并排序入结果 → T2（外部进程）在 t1 删除/截断 F → T3（搜索 worker）执行 F 任务：`Files.size`（L146，"stat file failed"）或 `new MappedFileReader`（L163，"map file failed"）或 `ChunkedFileReader` 构造（L189，"open file failed"）抛 `NopRgException` → `invokeAll` 已等全部任务跑完，但结果收集循环在首个异常 future 处即抛 `ExecutionException` → L129 `results` 整个丢弃，CLI 以 exit 2 输出**零结果**。交错后果：99% 文件已成功搜索，成果全部作废；报错文案 "search task failed" 不指明是哪个文件。
- **现状**: 任务失败聚合取「首错即全弃」。walker 侧 fail-fast 是**已声明的契约**（ParallelFileWalker javadoc L31-32「不静默返回不完整结果」），search 侧的单文件错误语义**无任何声明**；对齐目标 rg 的实际行为是逐文件打印错误到 stderr、继续搜索、以 exit 2 反映「有错误」（部分结果照常输出）。
- **风险**: 树在搜索窗口内发生合法并发变更（最常见：编辑器保存、构建清理）时工具从「部分结果 + 错误提示」劣化为「零结果 + 模糊报错」；该行为与产品自身的 rg 对齐契约冲突，且因错误文案丢失文件名而难以自诊断。
- **建议**: 二选一并写入 coordinator javadoc 契约：(a) 对齐 rg——任务内捕获单文件 `NopRgException` 转为「该文件错误项」（stderr 汇总 + 有错误时 exit 2），结果照常输出；(b) 保持 fail-fast，但错误信息携带文件路径（`"search task failed: " + cause.getMessage()`），并在 javadoc 声明「任一文件错误 = 全量失败」契约。倾向 (a)。
- **失效面枚举**（同一「单文件 I/O 错误 → 全量丢弃」失效层的全部兄弟入口）:
  1. `SearchCoordinator.searchFile:146` `Files.size` — IOException → 全弃（立案）
  2. `SearchCoordinator.searchFile:163` `MappedFileReader` 构造（整文件路径） — 同族（立案）
  3. `SearchCoordinator.searchFile:159→185` `searchFileChunked` 的 `ChunkedFileReader` 构造与 `nextChunk` map（L85） — 同族（立案）
  4. `SearchCoordinator.searchFileChunked:221` 行提取二次 `MappedFileReader` — 同族（立案）
  5. `FindCoordinator` — 不读文件内容，无此失效面（N/A）
  6. `ParallelFileWalker` walk 阶段 — 同为 fail-fast 但**已声明契约**（javadoc L31-32），且发生在任何搜索工作之前，无「成果丢弃」不对称，不立案
  7. `RegexSearcher.findAll` / `MatchAggregator` / `LineCursor` — 纯内存操作（segment 已打开后不再触盘），无独立 I/O 错误源
- **信心水平**: 确定（机制确定；单次运行是否触发取决于树并发变更概率）
- **误报排除**: 不是「快速失败契约」的同类项——立案点恰是 search 侧**没有**该契约声明却继承了全弃语义，与 walker 的显式契约不对称；也不属于被 CI 覆盖的问题（现有测试无 TOCTOU 类用例，`LargeFileSearchTest`/`SearchCoordinatorTest` 均在静态树上演）。
- **复核状态**: 已复核（2026-10-22）——保留 P2（CLI 报错链实锤补强，见文末维度复核结论）

### C4-003 SearchCommand.patternBytes 惰性缓存为非同步 check-then-act，跨调用并发复用时存在数据竞争

- **文件**: `nop-tools/nop-rg/nop-rg-core/src/main/java/io/nop/rg/core/coordinator/SearchCommand.java:19,63-67`
- **证据片段**:
  ```java
  // SearchCommand.java:63-67
  public byte[] patternBytes() {
      if (patternBytes == null) {
          patternBytes = pattern.getBytes(StandardCharsets.UTF_8);
      }
      return patternBytes.clone();
  }
  // L19: private byte[] patternBytes; // 惰性缓存（plan 2268 F7）
  ```
- **严重程度**: P3
- **并发场景**: `SearchCommand` 为 public 可复用输入模型；`SearchCoordinator.search(command)` 为 public API。当前仓库内每次 CLI 调用新建 command，竞争仅在「外部嵌入方多线程复用同一 SearchCommand 实例并发发起多次 search()」时出现。
- **触发时序**: T1 调 `cmd.patternBytes()` 读到 `patternBytes == null`（L64）→ T2 同时读到 null → T1/T2 各自编码并写 L65（双算，内容相同，良性）→ 理论窗口：按 JMM，非 volatile、非 final 域的写入对无同步关系的读者可出现「引用非 null 但数组内容未发布」（读取到默认零数组）→ 下游 `PreparedLiteral.compile`/`searchFileChunked` 以全零 pattern 搜索，**静默错误结果**。单次 search() 调用内不成立：LITERAL/VECTOR 路径在主线程建池前必经 `PreparedLiteral.compile(command.patternBytes())`（SearchCoordinator:96-98）完成初始化，池任务经 submit happens-before 读到已发布值。
- **现状**: 字段为普通（非 volatile）域，惰性初始化无同步；javadoc 未声明 SearchCommand 的线程安全契约。
- **风险**: 嵌入方按「不可变命令对象」直觉复用实例时引入静默错误搜索；概率极低（JMM 理论窗口）但失效模式为错误结果而非异常，难察觉。
- **建议**: `patternBytes` 改 `volatile`（一行修复，保留惰性缓存收益），或在 javadoc 显式声明「SearchCommand 非线程安全，禁止跨线程复用」。倾向前者。
- **失效面枚举**: SearchCommand 全部可变状态仅此 1 处（其余 8 个字段均 final，globs 经 `List.copyOf` 防御）；`FindCommand`（nop-find）为 builder 构造的不可变模型，无可变缓存，N/A；coordinator/walker 的其余跨线程共享对象已在清单表核对为 immutable，无同族漏网。
- **信心水平**: 确定（竞争存在确定；「零数组」后果为 JMM 理论推演，实际 JIT/硬件上极难观测）
- **误报排除**: 不属于「看起来不优雅」校准——check-then-act 数据竞争是结构性并发缺陷，非风格问题；也不因「当前仓库内无此用法」而驳回：该类是平台对外发布的库 API 面，契约缺口真实存在。
- **复核状态**: 已复核（2026-10-22）——并入 C1-001 主登记（同站点重复登记，见文末维度复核结论）

---

## 三、维度集外线索附记（CL）

### CL-101 【→C1/C2】walker 文件收集走单一全局锁，超大树的锁热点未经评估

- **文件**: `nop-tools/nop-rg/nop-rg-core/src/main/java/io/nop/rg/core/walk/ParallelFileWalker.java:162-164`
- **证据片段**:
  ```java
  } else if (Files.isRegularFile(child)) {
      synchronized (context) {
          context.files.add(child);
      }
  }
  ```
- **线索**: 百万级文件树上所有 worker 对同一 `WalkContext` 锁逐文件争用（每文件一次 synchronized）；临界区极短（ArrayList.add），正确性无虞，但热点收敛度未量化。`_tmp/cra-baseline-nop-tools-parallel.txt` C2 节本单元锁计数 = 1（即此处）。建议 C2 维度评估是否需改 per-task 局部收集 + 终局合并（FindCoordinator 已是此形态）。**强信号度：中**。

### CL-102 【→C5】随文件数线性增长的物化点未经包络声明

- **文件**: `nop-tools/nop-rg/nop-rg-core/src/main/java/io/nop/rg/core/coordinator/SearchCoordinator.java:102-111`（每文件一个 `Callable` + `FutureTask` 双对象）；`ParallelFileWalker.java:94`（全树 `files` ArrayList）
- **线索**: tasks 列表 + invokeAll 的 Future 包装 + walker 全树列表，三处均随文件数 O(n) 物化；CLI 大树（百万文件级）下每文件约数百字节常驻直至搜索结束。单元内无任何内存包络声明（javadoc 仅钉确定性）。建议 C5 维度核对该单元声明规模包络与 O(n) 物化的相容性（含 `ChunkedFileReader` 单 arena 累积全部块视图的虚拟地址占用）。**强信号度：中**。

---

## 四、复核结论表（初审索引，非结论）

| 编号 | 文件 | 严重度 | 一句话 | 复核状态 |
|------|------|--------|--------|----------|
| C4-001 | ParallelFileWalker.java:71 / SearchCoordinator.java:113 / FindCoordinator.java:145 | P3 | 每调用建池（生命周期配对正确）+ 线程无命名，JFR 归因劣化 | 已复核（保留 P3） |
| C4-002 | SearchCoordinator.java:114-128 | P2 | invokeAll 首错全弃已完成结果，单文件 TOCTOU 即零结果退出，且契约未声明 | 已复核（保留 P2） |
| C4-003 | SearchCommand.java:19,63-67 | P3 | patternBytes 惰性缓存非同步，跨线程复用 command 实例时数据竞争 | 已复核（并入 C1-001 主登记） |
| CL-101 | ParallelFileWalker.java:162-164 | — | 文件收集单全局锁热点（转 C1/C2） | 已复核（确认，与 C9-001 去重） |
| CL-102 | SearchCoordinator.java:102-111 等 | — | O(文件数) 物化无包络声明（转 C5） | 已复核（确认） |

本维度盘点结论：池创建点 3 处（FJ×2、固定池×1），`new Thread` 0，commonPool 间接使用 0，定时任务 0；FFM/Arena 并发域 2 处全部 confined + 同线程配对关闭（主动设计，无兜底依赖）；nop-bytecode 纯同步单线程（无 C4 对象）。立案 3 条（P0×0 / P1×0 / P2×1 / P3×2），CL 线索 2 条新增 + CL-002 核实属实但降格为命名/可观测性问题。

---

# C4 深挖第 2 轮

- **审计单元**: 不变（nop-rg + nop-find + nop-bytecode 全单元，src/main 84 文件；benchmark 不立案）
- **本轮扩大面**（主 agent 指定）: (a) ForkJoinPool 公共池污染面（任务内是否用 commonPool）；(b) CLI 信号处理（SIGINT→shutdownNow 的注册）；(c) invokeAll 之外的批量任务 API（anyOf/allOf）；(d) watch-only「walk 中断缺失」的升级判定输入
- **盲审声明**: 本轮允许读取本目录 C1/C4 第 1 轮文件（已读）；仍未读取其他维度文件及 `ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`、`ai-dev/backlog/`。JDK 语义结论以本机 Zulu OpenJDK 26.0.1 的 `src.zip` 源码为准（与项目 Java 25 同族实现）。
- **编号规则**: 本轮新发现 `C4-R2-001` 起；前轮编号（C4-001/002/003、CL-101/102）保持原语义不变。

## 一、扩大面逐项核查结论（先记表，后立案）

| 指定面 | 核查证据 | 判定 |
|---|---|---|
| commonPool 污染面 | 复扫 grep：`commonPool`/`parallelStream`/`.parallel()`/`CompletableFuture` 于全部 main 源 = 0 命中（与第 1 轮口径一致）；进一步逐一核查**三个池的任务体**：ScanAction.compute（Files.list/isDirectory + GitIgnoreFile 正则）、searchFile/searchFileChunked（FFM 扫描 + j.u.regex）、scoreShard（纯字符串运算）——无任何间接落入 commonPool 的调用（无并行流、无无 executor 的 CF、无递归再提交到其他池）；walker javadoc（ParallelFileWalker.java:19「专用线程池，非 commonPool」）在任务体内同样成立 | 通过 |
| invokeAll 之外的批量任务 API | grep `allOf`/`anyOf`/`invokeAny`/`invokeInDomain`/`ExecutorCompletionService` = 0 命中；批量任务面仅 `invokeAll` ×3（ParallelFileWalker:169 RecursiveAction 同池分治、SearchCoordinator:117、FindCoordinator:148） | 通过（无 additional 面） |
| CLI 信号处理注册 | grep `addShutdownHook`/`sun.misc.Signal`/`SignalHandler` 于三模块 main 源 = 0 命中；SIGINT/SIGTERM 走 JVM 默认处理器即刻终止进程。OS 回收映射/线程/句柄，无持久状态损坏面——**但 `--jfr` 录制的 dump 仅存在于正常路径 close()**（见 C4-R2-002，本轮立案） | 通过（附 C4-R2-002） |
| walk 中断缺失（watch-only 升级判定输入） | 机制层证据已取证到 JDK 源码级（见 C4-R2-001）：`ForkJoinPool.invoke` = `poolSubmit` + `task.join()`（ForkJoinPool.java:3146-3154）；`ForkJoinTask.join()` → `awaitDone(false, 0L)`（ForkJoinTask.java:667-669），javadoc 明示「interrupts of the calling thread do not cause the method to abruptly return by throwing InterruptedException」；`awaitDone` 记录中断并在等待完成后复位标志（:437 `interrupts = 1` → :463 `Thread.currentThread().interrupt()`） | 立案 C4-R2-001（升级判定结论见该条） |

## 二、发现条目

### [C4-R2-001] ParallelFileWalker.walk() 完全不可取消：外部中断只悬挂不生效，遍历必然跑完整棵树（watch-only 升级判定输入，结论：维持 P3，库化嵌入前提下属 P2 面）

- **文件**: `nop-tools/nop-rg/nop-rg-core/src/main/java/io/nop/rg/core/walk/ParallelFileWalker.java:73`（`pool.invoke`）、`:119-171`（`ScanAction.compute` 全程无取消/中断检查点）、`:74-76`（finally `shutdownNow`）
- **证据片段**:
  ```java
  // ParallelFileWalker.java:71-76
  ForkJoinPool pool = new ForkJoinPool(threads);
  try {
      pool.invoke(new ScanAction(root, context)); // 外部线程 uninterruptible join
  } finally {
      pool.shutdownNow();
  }
  // ScanAction.compute（:119 起）唯一提前返回条件是 context.error，无任何
  // isCancelled()/Thread.interrupted()/取消标志检查——取消不会抑制子目录任务分发
  ```
- **严重程度**: P3（in-tree 无触发源；公开库 API 嵌入长驻进程前提下为 P2 面——升级判定输入即本条证据链）
- **并发场景**: 库形态嵌入方在 worker 线程调用 `SearchCoordinator.search()`/`FindCoordinator.find()`（二者均为 public API），上层以 thread.interrupt() 实施取消/超时控制；同一时刻 walker 的 FJ worker 正在遍历大树。
- **触发时序**: T1（嵌入方线程）在 t0 进入 `walk()` 并 `pool.invoke(rootAction)`，阻塞于 `ForkJoinTask.join()` 的不可中断等待（JDK 26 ForkJoinPool.java:3146-3149 `poolSubmit` + `task.join()`；ForkJoinTask.java:667-669 `awaitDone(false, 0L)`，javadoc 明示中断不抛 InterruptedException）→ T2（拥有者）在 t1 interrupt T1 → **无任何效果**：`awaitDone` 仅记录中断（:437 `interrupts=1`），T1 继续等待直至遍历自然完成，完成后才复位中断标志（:463）→ T1 在 t2 从 `walk()` 返回，中断标志在下一个 j.u.c 阻塞点（SearchCoordinator:117 / FindCoordinator:148 的 `invokeAll`→`future.get()`）才物化为 InterruptedException → 交叠后果：t0–t2 之间整棵树的 I/O 与 CPU 已被强制跑完，取消延迟无上界（与树规模成正比）；即使 T2 直接对 root task `cancel(true)`，`compute()` 无 `isCancelled()` 检查，已分发的子树任务照常执行，finally 的 `shutdownNow()`（tryTerminate(true,true)）虽中断 worker，但任务体无中断响应点（`Files.list` 目录遍历非中断敏感 I/O）。
- **现状**: 前轮清单表第 7 行 watch-only 注记「walk 阶段 pool.invoke 不响应中断（无取消路径）」。本轮升级判定输入三要素已齐：机制确证（live JDK 源码 + 模块代码逐行，非推演）；触发链具体（嵌入方 interrupt 即触发）；in-tree 触发源为零（CLI 单发进程内无任何代码 interrupt 调用线程；SIGINT 直接杀进程绕过本路径）。按领域判级规则「写不出具体时序才降 P2」不适用——时序完整，降 P3 的唯一依据是当前无 in-tree 受害者。
- **风险**: 库化嵌入长驻 JVM 后，「取消搜索」操作退化为「等待搜索自然完成」：上层超时/关停语义失真，关停时 worker 线程组延迟退出，拖垮 shutdown+awaitTermination 的时限；且因 walk 不可中断，嵌入方无法实现任何搜索取消（含未来 watch 模式）。
- **建议**: 二选一：(a) 补取消通道——`WalkContext` 增加 `volatile boolean cancelled`，`compute()` 头部与目录循环内检查；`ParallelFileWalker` 增加包私有 `cancel()`（前轮 C4-002 建议 (a) 落地时 coordinator 可在任务失败后联动调用，顺带让「首错弃全」语义下的残留 worker 提前退出）；(b) 兑现契约——javadoc 显式声明「walk() 不可取消、不响应中断，调用方取消延迟与树规模成正比」。倾向 (a)+(b) 同时做，成本一个字段 + 两处检查。
- **失效面枚举**（F3 同族清扫：全部「不可取消的并行执行面」）:
  1. `ParallelFileWalker.walk():73` — 本案（RecursiveAction 分治，无取消检查）
  2. `SearchCoordinator.search():117` `invokeAll` — **不适用**：`ExecutorService.invokeAll` 的主线程等待可中断（`future.get()` 抛 InterruptedException，:123-125 已复位并转 NopRgException），取消语义成立
  3. `FindCoordinator.invokeAll():148` — 同上可中断（:156-158），不适用
  4. nop-bytecode — 纯同步单线程无池（第 1 轮清单表 #8），无取消面；`--max-runtime-seconds` deadline 轮询是其自有的有界机制
  5. `ScanAction.invokeAll(subdirs):169` — RecursiveAction 内的分发点，随 #1 的取消通道一并受益，无独立面
- **信心水平**: 确定（JDK 源码 + javadoc + 模块代码三重证据）
- **误报排除**: 不是「线程池默认行为可接受」类误报——问题不在 FJP 原语，而在模块**未向调用方暴露任何取消通道**且 javadoc 未声明该限制（契约缺口与前轮 C1-001/C4-003 同构：public 库 API 面 + 无声明）；也不与 walker「快速失败」契约混淆——快速失败针对遍历错误，不针对调用方取消。
- **复核状态**: 已复核（2026-10-22）——保留 P3（见文末维度复核结论）

### [C4-R2-002] --jfr 录制在 SIGINT/SIGTERM 下无 dump 且无 shutdown hook 兜底：画像特性在最需要的场景（长搜索 + Ctrl-C）静默产出空缺

- **文件**: `nop-tools/nop-rg/nop-rg-cli/src/main/java/io/nop/rg/cli/JfrSupport.java:49-59`（dump 仅在 close lambda 内）；`nop-tools/nop-rg/nop-rg-cli/src/main/java/io/nop/rg/cli/NopRgMain.java:95-112`（recording 只在正常路径 finally close）
- **证据片段**:
  ```java
  // JfrSupport.java:49-59：stop + dump 全部绑定在 close() 调用上
  return () -> {
      try {
          recording.stop();
          Files.createDirectories(output.toAbsolutePath().getParent());
          recording.dump(output);
      } catch (IOException e) { ... }
      finally { recording.close(); }
  };
  // NopRgMain.java:104-111：唯一的 close 点是 call() 正常返回路径的 finally
  ```
- **严重程度**: P3
- **并发场景**: 用户以 `--jfr out.jfr` 启动搜索，搜索期间（池 worker 执行扫描时）向进程发 SIGINT（Ctrl-C）/SIGTERM；JVM 默认信号处理器即刻终止进程。
- **触发时序**: 非交错型缺陷。T1（用户）在搜索进行中（大树上恰是画像目标场景）发送 SIGINT → JVM 默认 handler 终止进程：全单元 `addShutdownHook`/`Signal` 注册为 0（本轮 grep 复核），故 NopRgMain 的 finally 链（`recording.close()` → `recording.dump(output)`、`pool.shutdownNow()`、`CoreInitialization.destroy()`）**全部不执行** → `out.jfr` 从未写出（`setToDisk(true)` 只写 JFR 内部 chunk 临时文件，不落到用户指定路径）→ 用户拿到空缺的画像文件，且无任何提示。
- **现状**: 信号处理无任何自定义注册（JVM 默认即杀）；`--jfr` 是该单元的一等诊断特性（第 1 轮 C4-001 已证其与线程命名缺失的耦合伤害），但 dump 契约只覆盖正常结束路径。
- **风险**: 诊断面缺口：性能问题（慢、高分配、锁争用）的排查通常正是「跑到一半 Ctrl-C」的场景，特性在最需要时静默失效。无持久状态损坏面（进程死亡即 OS 回收，第 1 轮清单表 #7 判定仍成立）。
- **建议**: 在 `JfrSupport.startRecording` 内注册 `Runtime.getRuntime().addShutdownHook(new Thread(...))` 执行 stop+dump（与正常 close 共用同一 lambda 并加 AtomicBoolean 幂等守卫，防 SIGINT 与正常结束双 dump）；hook 线程用平台 `NamedThreadFactory` 风格命名（顺带对齐 C4-001 的命名建议）。注意 Windows/JVM -Xrs 环境下 shutdown hook 语义差异，javadoc 声明「SIGKILL/异常崩溃仍无 dump」的残余边界。
- **失效面枚举**（F3 同族清扫：全部「仅正常路径释放的进程级资源」）:
  1. `JfrSupport` recording dump — 本案
  2. `NopRgMain`/`NopFindMain` 的 `CoreInitialization.destroy()`（NopRgMain:115 / NopFindMain:107）— SIGINT 下不执行；平台 VFS 初始化资源随进程死亡回收，CLI 单发模型可接受，不立案
  3. 三处池的 `shutdownNow`（ParallelFileWalker:75 / SearchCoordinator:131 / FindCoordinator:160）— SIGINT 下不执行；线程随进程回收，无泄漏面（进程级），不立案
  4. nop-find / nop-bytecode — 无录制类特性，无此失效面（N/A）
- **信心水平**: 确定
- **误报排除**: 不属于「CLI 无需优雅停机」的误报校准——立案点不是停机语义本身（进程死亡即回收，第 1 轮已判定通过），而是**用户显式请求的输出产物（--jfr 文件）在可预期的操作序列下静默缺失**；也不是生成产物或测试专用代码。
- **复核状态**: 已复核（2026-10-22）——保留 P3（见文末维度复核结论）

## 三、前轮发现失效面补充（追加记表，不新立案）

| 前轮编号 | 补充失效面 | 证据 | 处置 |
|---|---|---|---|
| C4-002（首错弃全聚合） | 补充失效面 8：文件在分块扫描与行提取之间被截断（`SearchCoordinator.java:189→221` 窗口）——spans 按截断前偏移生成，对新整文件映射（已按新 size 建段）越界读取，FFM 边界检查确定性抛 `IndexOutOfBoundsException`，同样无文件上下文、同样落入 :117-127 首错弃全层 | 机制：`MappedFileReader` 构造（:34-35）以 map 时 `channel.size()` 建段，陈旧 `MatchSpan` 偏移 > 新段大小时 `seg.get` 抛 IOOBE（确定性，非 fault） | 并入 C4-002 失效面清单作第 8 项（接续原 7 项编号），修复时随聚合策略一并覆盖；与 C1-R2-001（映射存活期截断）同根因（外部树变更）不同交错点——合并裁定输入 |
| C4-002 | 补充失效面 9：`FindCoordinator.invokeAll():150-153` 的 shard 失败聚合与 search 同型（首错即弃其余已完成 collector），但评分层（`FuzzyFileScorer.scoreNormalized`，纯字符串运算）无可具体化异常源 | FindCoordinator.java:144-161 + FuzzyFileScorer.java:102-164 逐方法核过无 throw 点（构造器校验在提交前） | 不另立案；C4-002 修复若泛化为「聚合策略统一」，将本点纳入同一工单 |

## 四、记表不立案项（本轮扩大面的剩余面）

1. **`--threads` 无上界校验**（NopRgMain:131-132 / FindCoordinator 构造）：用户显式传 `--threads 100000` 时 FJP 并行度直通（JDK 内部有 0x7fff 硬顶），线程按任务需求惰性创建、实质上限 = 任务数——自伤型误用面，非缺陷；可选跟进：CLI 侧 cap + 提示。
2. **FJP 公共池污染面**：任务体逐面核查无 commonPool 间接使用（见核查表第一行），walker「非 commonPool」声明在全链路成立。
3. **nop-bytecode 信号面**：无录制/无常驻注册资源，SIGINT 进程即死即回收，报告型 CLI 可接受；`--manifest` 写出（NopBytecodeMain:152-154）被 SIGINT 截断的最坏后果是 manifest 缺本轮条目（下次 collect 重扫），无损坏语义。
4. **walk 中断在 FJP shutdownNow 后的行为**：`tryTerminate(true,true)` 中断 worker，但 `Files.list`/正则/FFM 读取均非中断敏感点——已在 C4-R2-001 触发时序内覆盖，不重复立案。

## 五、本轮清扫表与终止信号

| 检查面 | 结论 |
|---|---|
| 池创建点全量复扫（`new Thread|ExecutorService|Executors\.|newFixedThreadPool|newCachedThreadPool|ForkJoinPool|IThreadPool`） | 仍为 3 处，无新增；`new Thread` 0（若采纳 C4-R2-002 的 shutdown hook 建议将出现首例 hook 线程，需随即补命名） |
| 定时调度（`scheduleAtFixedRate|scheduleWithFixedDelay|Timer\(`） | 0，无新增 |
| commonPool/并行流/CompletableFuture/allOf/anyOf/invokeAny | 0，无新增 |
| shutdown hook / Signal 注册 | 0（C4-R2-002 立案的正是该缺失本身） |
| 第 1 轮 watch-only 项处置 | 已升级为正式立案 C4-R2-001 并给出升级判定结论（维持 P3，嵌入前提 P2 面） |

**本轮终止**：四个指定扩大面已全部核查完毕，新增立案 2 条（C4-R2-001 P3×1、C4-R2-002 P3×1）+ 前轮失效面补充 2 项（不新立案）+ 记表不立案 4 项；未发现更多高价值新问题，C4 维度深挖结束。

---

# C4 维度复核结论（独立复核）

- **复核人**: C1+C4 维度独立复核子代理（独立 task，与初审非同一 agent）
- **复核日期**: 2026-10-22
- **方法**: 逐条重读 live 源码 file:line；重点核 C4-002 的 walker/search 契约分界与 CLI 报错链、C4-001 的生命周期配对；复核环境 Zulu OpenJDK 26.0.1（macOS arm64）。
- **盲审边界说明**: 复核阶段读取了本目录 `dispatch-log.md` 与 `C9-hot-path.md` 的 CL 节，仅用于 CL 回填与同站点去重比对；未修改其他维度文件与产品代码。

## 逐条判定

| 编号 | 判定 | 复核依据（file:line） | 理由 |
|---|---|---|---|
| C4-001 | **保留 P3** | ParallelFileWalker.java:59-60（每调用新建契约 javadoc）/:71/:74-76；SearchCoordinator.java:28（未用 `import java.util.concurrent.Executors` 属实，全文件无 `Executors.` 使用点）/:113/:130-132；FindCoordinator.java:145/:159-161；NopRgMain.java:72-74（--jfr）/:131；NopFindMain.java:127 | 三处每调用新建、零 ThreadFactory、try/finally `shutdownNow` 配对全正确；对 CL-002「无配对」疑点的驳回裁定核实；P3 恰当（当前 in-tree 无服务化嵌入调用方，grep 构造点仅两 CLI main） |
| C4-002 | **保留 P2** | SearchCoordinator.java:113-132（`invokeAll` 全量等待后收集循环首错 `future.get()` 即抛、:115 `results` 整体弃）；错误源 :146-148（"stat file failed: {file}"）/:163（"map file failed: {file}"）/ChunkedFileReader.java:60/:85/:189/:221；契约分界：ParallelFileWalker.java:31-32（walker 快速失败**已声明**）vs SearchCoordinator.java:82-84（search javadoc 无错误语义）——分界准确；CLI 报错链：NopRgMain.java:118-123 `System.err.println("nop-rg: " + e)` 仅打印顶层 toString，`NopException` 无 toString 覆写（nop-kernel/nop-api-core/src/main/java/io/nop/api/core/exceptions/NopException.java，grep 无覆写点） | 初审两个关键声明均核实成立：(1) walker 已声明 / search 未声明的分界准确（且两者失效面枚举第 6 项对 walker 不立案的理由成立——发生在任何搜索工作之前）；(2)「报错不指明哪个文件」在实践中**实锤**：cause 链中的文件名消息（"stat/map/open file failed: {file}"）因 CLI 只打印顶层消息而从未到达用户可见输出。rg 对齐契约（NopRgMain.java:26 自述「退出码对齐 rg」）与 rg 逐文件报错继续的行为背离成立。P2 维持 |
| C4-003 | **并入 C1-001（主登记）** | 同 SearchCommand.java:19,63-68 | 与 C1-001、CL-301（C9 移交）构成同站点三重登记；P3 判级低于初审 rubric 对数据竞争的下限口径（P3 = 性能优化/防御冗余/命名结构），以 C1-001 的 P2 + 更完整证据链（调用点 :96-98/:186/:245 分析 + FindCommand 对照）为准，见 C1 文件维度复核结论 |
| C4-R2-001 | **保留 P3** | ParallelFileWalker.java:71-76（invoke + finally shutdownNow）/:119-171（compute 唯一提前返回是 :120-122 error 检查，无 isCancelled/interrupted 检查点）/:162-164；SearchCoordinator.java:117/:123-125、FindCoordinator.java:148/:156-158（invokeAll 面 `future.get` 可中断——失效面枚举第 2/3 项「不适用」判定正确） | JDK `ForkJoinTask.join` 不可中断语义准确；无 in-tree 中断源（全单元 grep 无 interrupt 调用方）→「P3 + 嵌入前提 P2 面」的判级逻辑成立且与 C1-001 主登记的判级口径自洽 |
| C4-R2-002 | **保留 P3** | JfrSupport.java:49-59（stop+dump 全绑定 close lambda）/:40（setToDisk(true)）；NopRgMain.java:95/:104-111（唯一 close 点为正常路径 finally）/:115（destroy）；复核复跑 grep `addShutdownHook|sun.misc.Signal` 三模块 main 源 = 0 命中 | 信号路径无 dump 属实；建议（shutdown hook + AtomicBoolean 幂等守卫 + 命名）可落地；P3 恰当（诊断特性静默缺口，无持久状态损坏） |

## R2 前轮失效面补充表复核

- 补充面 8（SearchCoordinator.java:216→:221 截断窗口 → 陈旧 spans 对新映射 IOOBE）：机制核实成立（MappedFileReader.java:34-35 按新 `channel.size()` 建段，FFM 边界检查确定性抛 `IndexOutOfBoundsException`），维持并入 C4-002 失效面清单、不另立案。
- 补充面 9（FindCoordinator.java:148-153 shard 聚合同型首错即弃）：核实成立（results 为局部列表，:151-153 首 ExecutionException 即抛出弃列表），维持「随 C4-002 聚合策略统一修复、不另立案」。

## CL 复核结论（C4 接收/产出线索）

- **CL-101 确认**: ParallelFileWalker.java:162-164 单全局锁逐文件 `synchronized(context)` 核实；按 dispatch-log 与 C9-001 构成跨维重复对，归 Phase 4/6 去重，去向 C2（正确性面 C1 已核：写侧互斥正确、读侧经 invoke HB）。
- **CL-102 确认**: SearchCoordinator.java:102-111（每文件 Callable lambda + FutureTask 双对象）、ParallelFileWalker.java:94/:81（全树 files ArrayList + 结果复制）核实；去向 C5 维持。
- **CL-002（C1 接收线索，本维度承接）**: 确认 C4-001 的裁定——生命周期配对疑点驳回、命名缺口成立。

## 最终保留项

| 编号 | 状态 | 说明 |
|---|---|---|
| C4-001（P3） | 保留 | 建议不变：三处命名 ThreadFactory + FindCoordinator 池大小取 workerCount + 删 SearchCoordinator.java:28 未用 import |
| C4-002（P2） | 保留 | 建议 (a) 对齐 rg 不变；复核补充：若取 (b)，文件路径应取 cause 消息（"stat/map/open file failed: {file}"）拼接，因顶层消息本身无路径 |
| C4-003 | 并入 C1-001 | 主登记建议与判级说明见 C1 文件维度复核结论 |
| C4-R2-001（P3） | 保留 | 建议 (a)+(b)（volatile cancelled 通道 + javadoc 声明）不变 |
| C4-R2-002（P3） | 保留 | shutdown hook + 幂等守卫 + hook 线程命名建议不变 |

**C4 维度最终计数**：独立保留 4 条（P2×1、P3×3）+ 1 条并入 C1-001；驳回 0；R2 失效面补充 2 项维持不另立案；CL-101/102 确认转出、CL-002 承接裁定确认。
