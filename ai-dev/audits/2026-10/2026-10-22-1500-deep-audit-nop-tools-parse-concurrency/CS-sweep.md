# CS-sweep — CRA WI 6.2 抽查批（C2/C3/C4/C5/C7/C8/C9 七形态 overlay）

- **审计单元**: nop-tools 解析器生命周期全单元 357f / 52,738 行 —— nop-treesitter 92f/18,213 + nop-lint 160f/22,543 + nop-refactor 33f/4,010 + nop-jq 72f/7,982（`find -name "*.java" -path "*src/main*"` 口径，实测 52,748 与任务书 52,738 差 10 行为行数统计时点差，不影响结论）
- **形态集**: C2 锁与同步原语 / C3 异步链路 / C4 线程池与执行器 / C5 内存有界 / C7 长时运行 / C8 背压流控 / C9 热路径
- **方法**: grep 锚点先行 + 命中深核（不逐文件速读）；基线 `_tmp/cra-baseline-nop-tools-parse.txt`（C2 55 宽口径 / C4 池命中 NodeTscBridge / C6 native 面清单）
- **盲审声明**: 本报告为初审盲审产出。执行期间未读取 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`、`ai-dev/backlog/` 下任何已落盘文件；"现状"字段一律标"未复核"。已知站点 LanguageRegistry（C1 批 B）、NodeTscBridge（C6 批 B）、LintDeadlineExecutor install（C1/C6 交界）仅从本批形态角度交叉核对，不做现状归属裁定。除本文件外未修改任何文件。
- **方法论依据**: `ai-dev/skills/deep-audit-prompts.md` 共享提示词前缀（L298-374）+ `ai-dev/skills/concurrency-resource-audit-prompt.md` C2/C3/C4/C5/C7/C8/C9 维度正文（高频缺陷族 F1–F11 对照）
- **计数口径纪律**: 所有命中数标注 grep 模式与排除项（排 `/target/`、排 `/src/test/` 与 `_vfs` 测试 fixture），与基线宽口径数字差异处显式说明，不做两口径混算。

---

## 一、七形态核查表

| 形态 | 锚点（grep 模式，排 /target/、/src/test/、_vfs fixture） | 主代码命中 | 深核对象 | 判定 |
|---|---|---|---|---|
| C2 | `synchronized\|ReentrantLock\|ReadWriteLock\|Collections.synchronized` → 20 锚点；`computeIfAbsent\|.compute(` → 18 处 | 20 synchronized + 18 computeIfAbsent | 3 处 DCL（均 volatile ✓）、5 个 Java semantic resolver、TscTypeResolver、XmlTagKinds、Language.validSymbolCache、LintDeadlineExecutor.install、XScriptEngine/XScriptTimeoutException fillInStackTrace | **基本健康**。锁序图全单锁无嵌套无环；3 个 DCL 全部正确配 volatile；发现 CS-002（关联 C5）、CS-003 |
| C3 | `CompletableFuture\|thenApply\|thenCompose\|supplyAsync\|runAsync\|orTimeout\|CompletionStage` | **0**（主代码零命中） | 全部线程创建点复核（见 C4）；LSP onMessage 回调面 | **清洁**。纯同步工具栈，无异步链、无上下文传播边界、无时序假设面。弱信号 CL-505 |
| C4 | `new Thread(\|ExecutorService\|Executors.\|newFixedThreadPool\|newCachedThreadPool\|IThreadPool\|ForkJoinPool\|parallelStream\|scheduleAtFixedRate\|new Timer\(` | 主代码**唯一**线程创建点 `NodeTscBridge.java:208`；池/定时器 0 | NodeTscBridge 生命周期（daemon✓ 具名✓ restart budget=2 有界✓ deadline 15s/20s✓）；测试代码 `Executors.newFixedThreadPool` 2 处按"测试专用并发模型"豁免不计 | **发现 CS-001**（reader 线程与 respawn 的生命周期竞态，兼 C7/C8 面） |
| C5 | `readAllBytes\|Files.readString\|readLines` → 12 处；`new ThreadLocal\|NamedThreadLocal` → 3 字段/2 文件；arena/jq/xscript 限额逐点 | 12 文件读取 + 3 ThreadLocal + GLR/arena/jq 限额 | SubtreeArena（几何增长+free list+MAX_CHILDREN✓）、expectedNodes ≤2^21✓、GLR MAX_VERSION_COUNT=6(+4)/MAX_COST_DIFFERENCE✓、ScannerVM step budget✓、jq MAX_CALL_DEPTH=2000+checkOutputLimit+SOE catch✓、xscript maxDiagnosticsPerMatch=100+MAX_EVAL_DEPTH=32✓、TSNode.SCRATCH/ScannerVM.POOL 池化去引用✓、服务面 CFG_MAX_SOURCE_SIZE 1MB✓、LintTree 每树 cache 无静态根✓、TSTree 纯 Java（无 native，didChange 换树 GC 可达释放✓） | **发现 CS-002**（JavaTypeResolver 查询缓存无界）；CLI readAllBytes 无上限属本地工具文件自界，非缺陷 |
| C7 | `while (true)\|for (;;)\|scheduleAtFixedRate\|addShutdownHook\|.take\(\)` → 13 处主代码循环；单调增长源盘点 | 13 循环 + 0 定时器 | PatternMatcher:112（每分支前进或返回✓）、TSQueryCursor.advance（root 回溯终止✓）、TSTreeCursor×4（游标前进有界✓）、BashCharClass:104（i++ 有界✓）、JqParser:219（token 推进✓）、JqBuiltins:162（checkOutputLimit 兜底✓）、GLR for(;;)×2（L1676 error-cost 终止门✓）、NodeTscBridge pollWithDeadline（deadline 兜底✓）；无账本类只增不删（RuleResultCache fail-closed+原子写，CLI 单次运行语义） | **无独立发现**。挂死可检测性由 bridge deadline 与 xscript deadline 覆盖；CS-001 的 EXHAUSTED 分支是 C7 降级语义实现缺陷，并入 CS-001 不重复计数 |
| C8 | `Queue\|BlockingQueue\|Semaphore\|RateLimit\|offer\(` → 主代码唯一生产-消费边界 | LinkedBlockingQueue×1（NodeTscBridge.inbound） | 协议为单飞行 request/response（synchronized 串行化），稳态在途 ≤2 帧；无其他队列/信号量/限流点 | **无独立发现**。队列无界本身记 CL-503（背景面）；stale 帧污染后果并入 CS-001 |
| C9 | 热路径 = LSP didChange 每键 lintCompiled、每规则×每节点匹配、jq 每输出 | — | LintEngine byte[] 直通 parse（避免 String round-trip，JFR 13%+ 已优化）、TreeCache 每树一次物化、fillInStackTrace 覆盖避免栈回填、Pattern 全部静态/编译期（ParserCExtractor 系 codegen 冷路径）、单锁路径在单线程调用方下无争用 | **无立案**（无满足量化证据条的缺陷）；bench 目录已存在（nop-lint/bench、nop-treesitter/bench），无基准缺口登记 |

**与基线口径差异说明**: 基线文件记 "C2 locks: 55"（宽口径，含 test 源与更宽模式），本表主代码口径为 20 synchronized 锚点 + 18 computeIfAbsent；基线 "C1 ThreadLocal: 2"（按文件计），本批实测 3 个 ThreadLocal 字段 / 2 文件（TSNode.SCRATCH、TSNode.SCRATCH_DEPTH、ScannerVM.POOL）。差异全部来自口径而非覆盖缺口，锚点清单见上文。

---

## 二、发现即立案

### [CS-001] NodeTscBridge 旧 reader 线程的 EOF/尾行与 respawn 竞态，可永久击穿 bridge 或虚耗重启预算

- **文件**: `nop-tools/nop-lint/nop-lint-js/src/main/java/io/nop/lint/js/tsc/NodeTscBridge.java:157-178, 195-243, 245-265`
- **形态归类**: C8（生产-消费边界污染）为主，兼 C4（线程与进程生命周期不对称）、C7（降级语义缺陷）
- **证据片段**:
  ```java
  // L231-243 旧 reader 线程：读尽旧进程输出，finally 里无条件投递 EOF
  private void drainPeerOutput() {
      try (BufferedReader reader = new BufferedReader(
              new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
          String line;
          while ((line = reader.readLine()) != null) {
              inbound.put(line);
          }
      } catch (IOException | InterruptedException e) {
          Thread.currentThread().interrupt();
      } finally {
          inbound.offer(EOF);                      // (A) 与新 spawn 的 clear() 竞争
      }
  }
  // L195-197 respawn：clear 只能清掉"已经入队"的帧
  private void spawn() {
      destroyProcess();                            // destroyForcibly 异步生效
      inbound.clear();
      ...
      Object answer = pollWithDeadline(config.handshakeTimeout(), "handshake");  // (B)
          // L213-217: answer == EOF → state = State.EXHAUSTED; throw —— 永久不可用
  ```
- **严重程度**: P1
- **并发场景**: 单一请求线程（持有 bridge 监视器执行 request/spawn）× 旧 reader 线程 `nop-lint-tsc-bridge-reader`（不持有监视器，仅在旧进程流上阻塞读）
- **触发时序**: 前置：某查询超时或 peer 崩溃，`handleProcessFailure` → `destroyProcess()`（destroyForcibly 异步终止旧进程），state=NEW（restartsLeft 2→1）。T1（请求线程）进入 `spawn()`：L197 `inbound.clear()` → 启动新进程与新 reader → 阻塞在 (B) 等握手帧。T2（旧 reader 线程）此时才从已关闭的旧进程流上读到 EOF，readLine 返回 null 退出循环，执行 (A) `inbound.offer(EOF)` —— 该 EOF 落入**新协议会话**的队列。T1 的 `pollWithDeadline` 取到这个陈旧 EOF → L213 判定"新 peer 在 ready 帧前退出"→ `state=EXHAUSTED` 永久不可用（类契约"once the budget is exhausted ... never retries"），尽管新 peer 完全健康。变体 1：陈旧 EOF 落在后续 query 轮次 → L159-163 `handleProcessFailure("peer exited while query in flight")` 虚耗 1 次重启预算并抛 BRIDGE_CRASHED（误归因）。变体 2：旧 reader 最后一行响应帧（非 EOF）晚于 clear 入队 → 下一查询命中 L168-172 correlation id mismatch → 同样虚耗重启预算。窗口真实性：destroyForcibly 到旧 reader 实际退出之间是毫秒级异步区间，而 spawn 同步推进，握手窗口长达 20s，(A) 晚于 clear 到达完全可达。
- **现状**: 未复核（盲审）
- **风险**: 一次超时/崩溃恢复后 bridge 可能被永久判死（只能整体重建 bridge，LSP/长驻 lint 进程内 tsc 语义能力静默降级为 unavailable）；或重启预算（maxRestarts=2）被虚假耗尽，把可恢复故障放大为终端不可用。两者都违反本类自declared的 fail-visible 恢复语义（错误归因错位：健康 peer 被 记为 exited/crashed）。
- **建议**: 给 reader 会话引入代际标记：`AtomicInteger generation`，spawn 时 +1，drainPeerOutput 捕获启动时代际，(A) 处仅在"自己仍是当前代"时 offer EOF（陈旧代际的尾行直接丢弃）；或 spawn 在 clear 前对旧 reader 做 `join(短超时)`。两方案均不需要平台设施，改动局部于本文件。
- **失效面枚举（F3）**: 同类"进程桥 + 后台排水线程"在本单元唯一（`grep "new Thread"` 主代码仅 NodeTscBridge:208 一处）。类内交错点全清单：inbound.put（L236，变体 2 源）、inbound.offer(EOF)（L241，主触发点）、inbound.clear（L197，唯一清队点）、pollWithDeadline 调用点×2（L157 query、L212 handshake，两个受害消费点）、handleProcessFailure（L267，预算虚耗点）。`close()` 路径（L138-142）不受影响（closed 后无人再 poll）。其余两个已知站点（TscTypeResolver.lock、LintDeadlineExecutor.install）无同构模式，已逐一核对不复发。
- **信心水平**: 很可能（交错序列完整具体；窗口依赖旧 reader 存活时长，异步 kill 使其真实存在）
- **误报排除**: 不属"原语使用不是问题"——缺陷不在 LinkedBlockingQueue/Thread 原语，而在恢复路径的代际不隔离；不属"有界队列误报校准"——本案不主张队列无界（那是 CL-503），主张陈旧帧跨会话污染；不属"测试专用简化并发"——这是主代码生产路径。
- **复核状态**: 已复核（CS 抽查批独立复核）——**缺陷本体保留（P1 机制成立），登记归属改判 → C6-101 主登记，本条转同站点引用登记**；「永久击穿/虚耗重启预算」两后果归入 C6-101 失效面（与 C1 复核、C6 复核三方裁定一致闭合）。见维度复核结论第一节。

### [CS-002] JavaTypeResolver 查询缓存 assignableCache/typeNameCache 无界，违反同类自declared的 "bounded LRU" 约定

- **文件**: `nop-tools/nop-lint/nop-lint-java/src/main/java/io/nop/lint/java/semantic/JavaTypeResolver.java:57-70, 89-130`
- **形态归类**: C5（内存有界）为主，兼 C7（长时运行单调增长）
- **证据片段**:
  ```java
  // L57-59 类内约定：bounded LRU 是为长驻进程定的
  // the sibling resolver convention (plan 12): bounded LRU + synchronized —
  // a long-running LSP process must not accumulate one entry per file forever
  private static final int CACHE_SIZE = 32;
  private final Map<String, ParsedFile> filesByPath = new LinkedHashMap<>() { ... removeEldestEntry ... };  // ✓ 有界
  // L69-70 两个查询缓存：键为 每个查询位置 一条，永不清除
  private final Map<String, Boolean> assignableCache = new HashMap<>();
  private final Map<String, String> typeNameCache = new HashMap<>();
  // L91 / L121: 键 = filePath + "|" + line + "|" + col (+ "|" + expectedType)
  String cacheKey = filePath + "|" + line + "|" + col + "|" + expectedType;
  ```
- **严重程度**: P2
- **并发场景**: 单线程语义查询（synchronized 已保证互斥）；问题不在并发而在**长驻进程驻留**——LSP daemon（`NopLintLanguageServer` 长期存活，resolver 经 ServiceLoader 单例注册）或常驻 lint 服务生命周期内持续累积
- **触发时序**: 非竞态，增长链：daemon 每服务一个文件的一批 L2 查询（isAssignableTo/typeNameAt 各按 (file,line,col[,type]) 生成键）→ 每个新位置 put 一条 → `filesByPath` LRU 在 32 处截断，但两个查询缓存**只增不删**，唯一清空点是 `initProject`（L80-86，重新绑定时）。键空间随被查询过的位置数单调上升，且对已删除/改名文件的历史键同样永不清除。
- **现状**: 未复核（盲审）
- **风险**: LSP 长会话（天级）对大型代码库逐位置查询后，缓存条目数 ≥ 被查询位置总数（远超文件数，恰是 LRU 要防的"one entry per file forever"的超集）；单条 ~100B 级（键串+值），百万级位置 → 百 MB 级驻留，且全是陈旧文件的死键。与同文件 LRU 的存在理由直接矛盾，属于约定落实不完整而非风格问题。
- **建议**: 比照同族惯例把两个查询缓存改为与 `filesByPath` 联动的有界结构（如各 1024 条 LinkedHashMap LRU，或随 `filesByPath` 逐出时按 filePath 前缀联动清理）；不建议引平台 ICache——类内已有既定 LRU 惯例，就地补齐最小改动。
- **失效面枚举（F3）**: 五个 Java semantic resolver 的文件级缓存逐一核对：JavaTypeResolver.filesByPath LRU32 ✓、JavaSemanticResolver.unitsByPath LRU32 ✓（:38）、JavaDataflowResolver.unitsByPath LRU32 ✓（:35）、JavaScopeResolver.unitsByPath LRU32 ✓（:37）、JavaMetricsResolver.unitsByPath LRU32 ✓（:42）；**查询级缓存仅 JavaTypeResolver.assignableCache/typeNameCache 两处无界（本条）**，其余 resolver 无查询级缓存，无漏网兄弟。lint-core 侧 TreeSitterLintNode 的 TreeCache 为每树实例、随树回收 ✓；NopLintLanguageServer.documents 为 didClose 配对移除 ✓；compiledByLanguage ctor 冻结 ✓。
- **信心水平**: 确定（无界性与约定冲突均可在代码上直接验证；增长率取决于实际查询分布，故降 P2 而非 P1）
- **误报排除**: 不属"有界声明存在的缓存"误报校准——恰相反，**有界声明存在于同类注释中而这两个字段未落实**；不属冷路径——LSP 每键 didChange 触发 L2 查询，是声明过的长驻主路径。
- **复核状态**: 已复核（CS 抽查批独立复核）——**保留，降级 P2→P3（现状修正：在编未接线）**。机制与约定违约实证成立，但初审「resolver 经 ServiceLoader 单例注册」表述不实、main 0 构造点，判级随 C6-105「接线即显形预埋缺陷」校准。见维度复核结论第一节。

### [CS-003] XmlTagKinds.idFor 跨键并发 compute 时以 IDS.size() 分配 id，可为两个标签名分配同一 id

- **文件**: `nop-tools/nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/xml/XmlTagKinds.java:33-49`
- **形态归类**: C2（computeIfAbsent 复合操作原子性）
- **证据片段**:
  ```java
  private static final Map<String, Integer> IDS = new ConcurrentHashMap<>();
  private static final Map<Integer, String> NAMES = new ConcurrentHashMap<>();

  public static int idFor(String tagName) {
      if (tagName == null || tagName.isEmpty() || tagName.charAt(0) == '$') {
          return -1;
      }
      return IDS.computeIfAbsent(tagName, name -> {
          int id = IDS.size();          // (a) 跨键不互斥：两个并发 compute 同读到同一 size
          NAMES.put(id, name);          // (b) 副作用落在另一张 map，后写覆盖先写
          return id;
      });
  }
  ```
- **严重程度**: P3
- **并发场景**: 两个线程对**互不相同的键**同时首见（CHM computeIfAbsent 只保证单键原子，不同键的 compute 可并行）；本单元调用面：规则编译期 `XNodePatternCompiler.java:133`、`XmlLanguage.java:88`，lint 运行期 `XNodeLintNode.java:144`、`XmlValueNode.java:40`（kindId 路径，随 lint 每节点触达）
- **触发时序**: T1（请求 A 的 XML lint）对标签名 "foo" 首见进入 compute；T2（请求 B）对 "bar" 首见同时进入 compute（不同键、不同 bin，并行执行）→ T1 读 `IDS.size()`=N 得 id=N，T2 同样读得 N → 两键都映射 id=N，(b) 处 NAMES[N] 被 T2 覆盖为 "bar"。此后 kindId==N 的模式匹配在 foo/bar 间串扰（XNodeLintNode.kindId 与编译期 pattern id 比对），产生静默误报/漏报，直至进程重启。
- **现状**: 未复核（盲审）
- **风险**: 当前所有主入口（LSP ctor 编译、CLI 单线程、GraphQL `NopLintBizModel` 并发请求但 XML 语言面尚未在 LSP 默认绑定中注册）均无并发首见窗口，实际暴露为理论路径；一旦未来 XML 绑定进入并发服务面（GraphQL checkSource 对 XML 并发已是合法调用形态），将出现静默 lint 结果串扰，且 intern 后不复发、难以复现排查。
- **建议**: id 分配改用 `IDS` 与 `NAMES` 之外的 `AtomicInteger nextId`，或在类内加一把专用锁把 (a)(b) 与 map 写入串行化（intern 是低频首见操作，锁代价可忽略）；不建议 computeIfAbsent 内再嵌套同步其他结构（CHM 文档不鼓励 compute 内重副作用）。
- **失效面枚举（F3）**: 本单元全部静态/实例 intern 型 computeIfAbsent 逐一核对：XmlTagKinds.IDS（**✗ 本条**，值分配依赖共享 size + 跨 map 副作用）、Language.validSymbolCache（`Language.java:732` ✓ 值为纯函数派生数组、无跨键共享状态）、TreeSitterLintNode.cache.wrappers（`TreeSitterLintNode.java:123` ✓ 实例级、值独立）、DynamicConstructMarker.found（`DynamicConstructMarker.java:51` ✓ 测试辅助、实例级）、RuleSetLoader:143/TargetScanner:92/BaselineEngine:95/SuppressionFilter:106,109/MetaVarEnv:61/CommentSuppressionScanner:152（全部单线程构建期或纯本地列表分组 ✓）。
- **信心水平**: 确定（竞态机制确定；现实暴露度低是定 P3 的唯一原因）
- **误报排除**: 不属"ConcurrentHashMap 原语使用不是问题"——缺陷是**跨键 id 分配复合操作**依赖 size() 快照 + 第二张 map 的写副作用，恰是 C2 执行步骤 4 点名的 check-then-act/compute 复合形态；不属 C1 静态可变形态重复报告—— intern 表本身合法，问题在分配原子性。
- **复核状态**: 已复核（CS 抽查批独立复核）——**保留，P3 维持**。证据逐行重演通过；修复建议精化：优先「类内专用锁」方案（AtomicInteger 在 CHM compute 重试时会烧号，破坏 javadoc dense-id 声明）。见维度复核结论第一节。

---

## 三、维度集外弱信号（CL 附记）

- **CL-501** [C1 交叉面，已知站点] `LanguageRegistry.java:58-75`：`register` 对 plain HashMap 做 check-then-act（`existing != language` 后 `put`），无任何同步。当前调用点（discoverDefaults/LSP ctor/CLI main）均为启动期单线程，不构成现实竞态；但 javadoc 声明 "embedders may also register manually"，并发 embedder 下冲突检测本身有竞态窗口，静默违反 "fails closed" 契约。归属 C1 批 B（已知站点），此处仅登记 C2 交叉观察，不重复立案。
- **CL-502** [C1 弱信号] `TscTypeResolver.java:52-60, 29`：`isAvailable()` 无锁读非 volatile 字段 `bridge`（写侧在 `synchronized(lock)` 内）。引用读本身原子，最坏读到 null 走环境探针分支，行为良性；不建议改（加 volatile 收益趋零），登记备复查。
- **CL-503** [C8 背景面] `NodeTscBridge.java:59`：`inbound` 为无界 LinkedBlockingQueue。协议单飞行 + 超时即 destroy + spawn 即 clear 使稳态在途 ≤2 帧，现实无增长路径；若未来协议引入流水线/事件推送则升级为 C8 发现。与 CS-001 同点不同面，不重复计数。
- **CL-504** [C9 watch] `LintDeadlineExecutor.java:70-77, 85-110`：`install()` 全局换装 IExpressionExecutor 后，**非 lint** 的 XLang 每节点求值多付一次 scope 取值比较（passthrough 分支）；设计文档（design 07 §4 Route A）已裁定该换装方式，本批不翻案。若 install 后长期驻留于混合负载 JVM，建议在 bench 中补一组开关对比（watch-only）。
- **CL-505** [C3/C1 弱信号] `NopLintLanguageServer.java:96-160`：`onMessage` 无同步，`documents` 用 ConcurrentHashMap、`shutdownRequested` 用 volatile，暗示宿主传输层可能多线程投递。两个 didChange 竞争同一 URI 时 last-put-wins，且两轮 lintAndPublish 的 diagnostics 发布顺序可能与编辑顺序倒挂。当前无内置多线程传输实现，行为取决于宿主；建议在 owner doc 声明"单线程传输假设"或对 onMessage 加轻量串行化（二选一，非缺陷立案）。

---

## 四、终止声明

本抽查批对 nop-tools 解析器生命周期全单元（nop-treesitter 92f + nop-lint 160f + nop-refactor 33f + nop-jq 72f，357f/52.7k 行）完成 C2/C3/C4/C5/C7/C8/C9 七形态 overlay 初审：锚点扫描全部执行并留存可复现口径，命中点逐一深核或明确豁免理由（测试 fixture 4 处、测试专用池 2 处、codegen 冷路径 1 文件）。立案 3 项（CS-001 P1 ×1、CS-002 P2 ×1、CS-003 P3 ×1），弱信号 CL 附记 5 项（CL-501…CL-505），无维度集内未处置的锚点残留。**本批七形态初审到此终止**；后续深挖轮、独立复核与合并裁定按调度架构另行派发，本报告所有条目均处于"未复核"状态，最终事实以复核裁定为准。

（盲审约束遵守声明：撰写本文件前未读取任何既有审计/计划/bug/教训/backlog 落盘文件。）

---

# CS 深挖第 2 轮

- **轮次性质**: 因第 1 轮新增 P1（CS-001）触发的必派深挖轮
- **重点**: (1) CS-001 同族放大——读写线程交接面（reader→inbound 队列→消费方）超时/预算/EXHAUSTED 降级之外的其余守卫面（协议版本不匹配？半帧残留？）；(2) C5 收尾——CS-002 之外有无第二无界缓存站点
- **方法**: NodeTscBridge / TscProtocol / TscBridgeConfig 全文互证 + peer 对端 `ai-dev/tools/tsc-bridge/tsc-bridge-server.mjs` 全文（协议证据，非审计产物目录、不属禁读清单）+ `grep -in "cache"` 全单元 main 命中 31 文件逐一判定 + nop-refactor/nop-jq 线程·队列·异步复扫
- **盲审声明**: 除第 1 轮 CS-sweep.md（任务书输入）外未读取任何历史审计/plan/bug/lesson/backlog 落盘文件。本节所有新条目均为"未复核"状态。

## 一、本轮清扫表

| 面 | 锚点/对象 | 结果 |
|---|---|---|
| CS-001 同族放大 | NodeTscBridge 消费点×帧类全矩阵：L157 pollWithDeadline 超时 / L159 EOF / L164 decode / L165 isOk / L168 ok 帧 id 校验 / L177 errorOf / L219 validateReady × 帧 ready·ok·error·半帧·非 JSON 行；drainPeerOutput 对 `process` 字段解引用时序；nextId 跨代不复位的缓解面；helper writeFrame/半帧面 | 陈旧帧三变体（EOF/ok 帧/error 帧过握手）同根因（生产侧代际不隔离），CS-001 修复统一覆盖，不另立；**消费侧校验缺口为独立新发现 CS-R2-001**；reader NPE 变体并入 CS-001 修复面（见第五节放大说明） |
| C5 收尾 | `grep -in "cache"` 全单元 main → 31 文件命中，逐一判定（见第二节） | **无第二无界缓存站点，C5 收口**；单元外 peer 侧同族记 CL-506、磁盘工件记 CL-507（均 watch） |
| C3/C4 复核 | nop-refactor + nop-jq main 复扫 `new Thread\|BlockingQueue\|ExecutorService\|CompletableFuture` | **0 命中**，维持轮 1"纯同步工具栈、线程/队列唯一（NodeTscBridge:208/:59）"判定 |
| 协议版本面 | `TscProtocol.validateReady`（TscProtocol.java:71-82）× helper ready 帧（tsc-bridge-server.mjs:369-373） | 双侧均无协议版本字段——并入 CS-R2-001 建议面，不单独立案 |

## 二、C5 收尾逐站点判定表

| 站点 | 文件:行 | 判定 |
|---|---|---|
| JqEngine.compile cache | `nop-jq/src/main/java/io/nop/jq/JqEngine.java:15-17` | ✓ 有界：`LocalCache.newCache("nop-jq-compile", newConfig(1000), ...)`，平台设施 + 显式容量（命中"有界声明存在的缓存"校准，非发现） |
| DefaultTreeSitterLanguageProvider.cache | `nop-treesitter/.../provider/DefaultTreeSitterLanguageProvider.java:38-55` | ✓ 键空间有限：仅 7 内置名 + ServiceLoader 提供方名单可入缓存（L50-53 仅 `language != null` 才 put，不可解析名不驻留）；`customProviders` 为 volatile DCL ✓ |
| GLRParser cachedToken 系 | `nop-treesitter/.../parser/glr/GLRParser.java:77-80,799-837` | ✓ 单槽 memo（parseState+position 匹配才命中，容量恒为 1） |
| JavaDeclarationIndex files/byName/byFile/byFqn | `nop-refactor/nop-refactor-java/.../JavaSymbolResolverAdapter.java:1267-1272` | ✓ per-run 实例（L72 `new JavaDeclarationIndex()`），以调用方交付的模块文件集为界；javadoc 明示 "Nothing persists beyond the run" |
| RuleResultCache artifact（内存面） | `nop-lint-core/.../cli/RuleResultCache.java:58-60,126,175` | ✓ 条目数 = 本轮 scan 文件集；磁盘面记 CL-507 watch |
| CheckRunner --cache 面 | `nop-lint-core/.../cli/CheckRunner.java:237-278` | ✓ `cacheFile == null` 即零缓存；读写均 per-run |
| RunSummary.cacheHits / CliOptions --cache | `cli/RunSummary.java:60` / `cli/CliOptions.java:133-146` | ✓ 原语计数器 / 参数解析 |
| Language.validSymbolCache、LintTree/TreeCache、TreeSitterLintNode.cache、NodeTscBridge.inbound、五 resolver 文件缓存 | （轮 1 已核） | 维持轮 1 判定（含 CS-002 本体） |

**结论**：单元内查询级无界缓存仅 CS-002 一处（其 F3 兄弟清单轮 1 已穷尽并经本轮复 grep 证实——JavaScopeResolver/JavaDataflowResolver/JavaMetricsResolver/JavaSemanticResolver 均只有 `unitsByPath` LRU32，无查询级缓存字段）；本轮未发现第二无界缓存站点，**C5 收口**。

## 三、新发现

### [CS-R2-001] NodeTscBridge 消费方对 error 帧不校验 correlation id、BAD_FRAME 不回收会话——流去同步不可检测且误归因；协议无版本字段放大漂移失效面

- **文件**: `nop-tools/nop-lint/nop-lint-js/src/main/java/io/nop/lint/js/tsc/NodeTscBridge.java:164-177`（主证）；`同目录/TscProtocol.java:41-47,71-82,119-126`；对端证据 `ai-dev/tools/tsc-bridge/tsc-bridge-server.mjs:327-331,359-361,369-373`
- **形态归类**: C8（生产-消费边界去同步守卫缺失）为主，兼 C7（恢复路径误归因/终端误判）、协议健壮性
- **证据片段**:
  ```java
  // NodeTscBridge.java L164-177：correlation id 校验只存在于 isOk==true 分支
  Map<String, Object> frame = TscProtocol.decode((String) answer);
  if (TscProtocol.isOk(frame)) {
      Map<String, Object> result = TscProtocol.resultOf(id, frame);
      Object answerId = frame.get("id");
      if (!Integer.valueOf(id).equals(answerId)) {
          handleProcessFailure("correlation id mismatch");   // ok 帧：有校验、有回收
          throw new TscQueryException("BAD_FRAME", ...);
      }
      return result;
  }
  // A structured error frame is a healthy peer's answer: ...
  throw TscProtocol.errorOf(id, frame);                      // (A) error 帧：无 id 校验、无回收
  ```
- **严重程度**: P2
- **并发场景**: 单一请求线程（持 bridge 监视器）× reader 线程 `nop-lint-tsc-bridge-reader`；单飞行协议下"队首帧即当前应答"是该路径唯一依赖的假设，而 error 帧路径对这一假设零校验
- **触发时序**:
  **时序 A（error 帧误配，静默接受去同步）**：前置——队列出现有序偏移（来源：CS-001 陈旧帧、peer 崩溃半帧残留、或 helper 传递性依赖 require 期向 stdout 打印一行非帧文本——协议对 stdout 无任何排他守卫）。T1 发出 query K（其响应为 ok:false，如 QUERY_FAILED）→ 偏移使 T1 的 query K+1 在 L157 拿到 K 的 error 帧 → L165 isOk=false → (A) 处**不比对该帧携带的 id**（可能是旧会话 id，甚至是 helper 内部异常兜底路径 mjs L359-361 / 请求解析失败路径 L327-331 的合成 id=-1）→ 直接把 K 的错误码/消息抛给 K+1 的调用方——去同步被静默接受；且 `errorOf`（TscProtocol.java:119-126）用**当前在途 id** 拼消息（"tsc bridge request K+1 failed: ..."），误归因在日志层面不可辨。K+1 真应答滞留队首，后续每个查询继续消费前一查询的应答，直到某个 ok:true 帧命中 L168 校验才回收（再虚耗 1 次重启预算）。
  **变体（陈旧 error 帧过握手 → 终端误判）**：偏移 error 帧落在 spawn 握手窗口 → L219 `validateReady` 判 "first peer frame was not a ready frame" → L224-226 终端 EXHAUSTED——健康新 peer 被永久判死（比 CS-001 变体 2 的预算虚耗更重，且注释"retrying changes nothing"对陈旧帧场景不成立）。
  **时序 B（BAD_FRAME 不回收，检测延迟一轮 + 归因错位）**：peer 被 kill -9 于帧写一半 → `readLine` 在 EOF 前返回半行 JSON → L164 decode 抛 BAD_FRAME → **无 handleProcessFailure**，state 仍 READY 而进程已死 → 首个查询错误归因为"协议违例"而非 peer 死亡 → 检测延迟到下一查询 L151 write/flush 抛 IOException（L153-155）才回收。半行 JSON 同时制造时序 A 的偏移态。
- **现状**: 未复核（盲审）
- **风险**: 单飞行协议唯一防线"队首帧=当前应答"在 error 帧上无第二重校验兜底，流一旦偏移，失真应答被当作健康错误上抛——lint 规则拿到错误码/错误目标位的诊断，fail-visible 语义（错误归因准确）被破坏。叠加 ready 帧无协议版本字段（Java 侧 validateReady 仅查 type + typescript 串非空；helper 侧 L369-373 同样不带版本），协议漂移的失效面正落在该缺口上：helper 由 `resolveHelperPath` 按 cwd 上溯解析（TscBridgeConfig.java:93-100），多 checkout 嵌套时存在 Java 与 helper 跨版本错配的真实路径，错配表现为 BAD_FRAME 噪声/预算虚耗而非"协议版本不匹配"的明确定位。现有部分缓解仅覆盖 ok 帧（`nextId` 跨 spawn 不复位、严格递增，陈旧 ok 帧 id 必然失配——L168），error 帧是该缓解的残余漏洞。
- **建议**: 三点，均局部于本文件 + helper 一行：(1) error 帧同样校验 id，不匹配按 `handleProcessFailure("correlation id mismatch on error frame")` 回收后再抛；(2) L164 decode 失败（BAD_FRAME）视为会话去同步信号——半帧/污染行意味着帧对齐已不可信，回收进程后再抛；(3) ready 帧增加 `protocolVersion` 常量字段并在 validateReady 校验，错配抛专门错误（顺带消除跨 checkout 错配的排查成本）。
- **失效面枚举（F3）**: 消费方全部校验点：L157 pollWithDeadline 超时（✓ 回收）、L159 EOF（✓ 回收）、L164 decode BAD_FRAME（✗ 本条）、L165 isOk 缺 ok 字段抛 BAD_FRAME（✗ 同机制，本条）、L168 ok 帧 id 校验（✓ 回收）、L219 validateReady（✓ 终端态，但误判面见时序 A 变体，由本条建议(3)收敛）。生产方帧完整性：helper writeFrame（mjs:53-55）单行 JSON.stringify ✓、Java encode（TscProtocol.java:33-35）单行 ✓——正常路径无半帧，半帧仅源于崩溃时序。单元内"进程桥 + 排水线程"实现唯一（轮 1 F3 已证 `new Thread` 仅 NodeTscBridge:208），无第二个同构点。
- **信心水平**: 很可能（代码路径确定、交错序列完整具体；触发需前置故障源——陈旧帧/污染行/半帧——故 P2 而非 P1；主触发源 CS-001 已按 P1 单独立案，本条是同族第二层守卫缺口，修复互补且均必要）
- **误报排除**: 不属"原语使用不是问题"——缺陷在帧校验逻辑缺失而非 LinkedBlockingQueue/Thread 原语；不属 CS-001 重复立案——CS-001 主张**生产侧**代际不隔离（陈旧帧得以入队），本条主张**消费侧**对已入队帧的校验与回收缺口（error 帧无 id 校验、BAD_FRAME 不回收、协议无版本），二者根因层不同、任一单独修复都不闭合；不属"测试专用简化并发"——`request()` 是生产查询主路径。
- **复核状态**: 已复核（CS 抽查批独立复核）——**保留，P2 维持，独立立案确认**（消费侧守卫缺口与生产侧代际隔离互补）。去重注记：时序 B（BAD_FRAME 不回收）与 C6-104 同站点，该子面并入 C6-104「统一失败出口」修复。见维度复核结论第一节。

## 四、CL 附记（第 2 轮续）

- **CL-506** [C5 集外 watch] `ai-dev/tools/tsc-bridge/tsc-bridge-server.mjs:51,131,189-197,235,262-265`：peer 侧 `projects` Map（每 tsconfig 路径一个完整 TS Program，按路径 set 覆盖、跨路径只增不删、无淘汰）与每项目 `queryCache`（file:line:col → rendered type，无界）是 CS-002 同族在 Node 进程侧的复现；同文件 `queryPrograms` 却有 16 条 FIFO 界（L262-265），佐证有界意识存在但未覆盖前两者。helper 在审计单元外（工具仓），Java 协议面未暴露任何缓存边界控制。归属工具仓，watch-only。
- **CL-507** [C5/C7 弱信号] `nop-lint-core/.../cli/RuleResultCache.java:175-195,197-208`：磁盘缓存工件对"本轮扫描不含的路径"从不清理（`put` 只覆盖同路径、无全量重建分支），陈旧路径条目驻留至 `runFingerprint` 变化才整体重置；有 fail-closed 校验 + 内容哈希匹配 + 原子写守卫，增长上界≈指纹稳定期内见过的路径并集，磁盘非内存。F4 弱形态，watch-only，不立案。

## 五、CS-001 同族放大说明（不改前轮结论，补充修复覆盖面）

1. **reader 线程 NPE 变体**：`drainPeerOutput`（L232-233）在 reader 线程上解引用共享字段 `this.process`；若解引用晚于 `destroyProcess` 置 null（L281），NPE 不被捕获（L238 仅 `IOException | InterruptedException`）→ 未捕获异常杀死 reader 线程、finally 仍 `inbound.offer(EOF)`——后果与 CS-001 主触发点相同。代际标记修复（CS-001 建议）顺带覆盖本变体；修复时建议 reader 捕获启动时代际的 Process 局部引用而非读共享字段。
2. **陈旧 error 帧过握手变体**：见 CS-R2-001 时序 A 变体——该路径的终局比 CS-001 已列两变体更重（终端 EXHAUSTED 而非预算虚耗），由 CS-001 修复（陈旧代际帧丢弃）+ CS-R2-001 修复（error 帧 id 校验）双覆盖。
3. **同名线程排查面**：每代 reader 复用同一线程名 `nop-lint-tsc-bridge-reader`，多代并存期（respawn 后旧 reader 未退出）线程转储不可辨代际；建议随代际标记命名。

## 六、终止声明

本轮按任务书两点重点完成深挖：(1) CS-001 同族放大——交接面全部帧类 × 消费点矩阵核查完毕，陈旧帧三变体（EOF/ok 帧/error 帧过握手）同根因同修复不另立，消费侧校验缺口立案 **CS-R2-001（P2 × 1）**；(2) C5 收尾——全单元 cache 站点逐一判定，**无第二无界缓存站点，C5 收口**，集外 watch CL-506/CL-507 附记。C3/C4 复扫维持轮 1 清洁判定。**本轮终止**——无进一步深挖点；累计立案 CS-001(P1)/CS-002(P2)/CS-003(P3)/CS-R2-001(P2)，全部处于"未复核"状态，最终事实以独立复核裁定为准。

（盲审约束遵守声明：本节撰写前未读取任何既有审计/计划/bug/教训/backlog 落盘文件，仅按任务书许可读取第 1 轮 CS-sweep.md 与单元内 live code、单元外 peer 协议脚本。）

---

# CS 维度复核结论（WI 6.2 独立复核，2026-10-22）

> 复核人：CS 抽查批独立复核子代理（与初审 R1/R2 不同 task）。范围：CS-001~003 + CS-R2-001 共 4 案逐条重读源码 file:line 实证 + NodeTscBridge 三方归并确认 + CL-501~507 裁定。live code @ 0d131797dd（git HEAD 实测一致）。Phase 4 复核豁免盲审：已读本文件前轮产物、C1/C6 两维复核结论（三方裁定与 C1-103 现状标注为复核必读输入）；除本文件追加与 4 条复核状态行回填外未改任何文件（未动 cra-index、其他维度文件、产品代码）。

## 一、逐条判定

### CS-001 — 缺陷本体保留（P1 机制成立），登记归属改判 → C6-101 主登记，本条转同站点引用登记

- **证据重演（全部吻合）**：`destroyProcess()` + `inbound.clear()` 无栅栏（NodeTscBridge.java:196-197，clear 与旧 reader 退出无时序约束）；reader 线程创建无代际标识（:208-210）；`drainPeerOutput` finally 无条件 `inbound.offer(EOF)`（:240-242，且 :232-233 读共享字段 `process`）；握手 EOF → EXHAUSTED（:213-217）；query EOF → handleProcessFailure 烧预算（:159-163）；ok 帧 id 校验对照面（:168-172）；`nextId` 单调递增不重置（:148）；`handleProcessFailure` 预算递减（:267-275）。初审所引行号与语义全部复核实证，竞态机制（destroyForcibly 异步生效 + spawn 同步推进 + 握手窗口 20s）成立。
- **三方归并确认**：与 C1-102、C6-101 同一唯一根因（重启路径对上一代 reader 线程与 pipe 残留流量无栅栏/纪元），C1 复核与 C6 复核均已裁定 **C6-101 为唯一主登记**，本复核照准。主登记选择正确性的补强证据：C6-101 分支 A 枚举了「stale 数据帧过握手 → catch :224 `destroyProcess` **杀死健康新进程** + EXHAUSTED」这一 CS-001 初审未枚举的更重后果——C6-101 后果枚举确实最全。
- **后果归并确认**：CS-001 标题所述「永久击穿 bridge」（EXHAUSTED 终态 :214/:273）与「虚耗重启预算」（:269-271）两后果均落入 C6-101 失效面；其 R2 第五节放大说明（reader NPE 变体、陈旧 error 帧过握手变体、同名线程排查面）一并由 C6-101 修复方案（代际捕获 + join 栅栏，兼 :281 置 null 时序）覆盖。CS-001 无独有后果留存，转纯引用登记。
- **现状**：未修复（live @ 0d131797dd）。

### CS-002 — 保留，降级 P2→P3（现状修正：在编未接线）

- **机制实证成立**：`assignableCache`/`typeNameCache` 为无界 HashMap（JavaTypeResolver.java:69-70），键 = `filePath|line|col[|expectedType]`（:91、:121），唯一清空点 `initProject`（:80-86）；类内自declared bounded-LRU 约定（:57-59 注释）仅落实于 `filesByPath`（:61-66，LinkedHashMap LRU32）；兄弟 resolver 对照成立（JavaSemanticResolver.java:35-41 `unitsByPath` LRU32、无查询级缓存字段）——「约定存在而两字段未落实」的违约本体与初审一致。
- **现状修正（降级依据）**：**在编未接线**——main 源码 `new JavaTypeResolver` 构造点为 **0**（grep 实证，全部命中在 src/test）；`META-INF/services` 无 `TypeResolver` 注册（注册的是 JavaSemanticResolver/DataflowResolver/MetricsResolver/ScopeResolver），初审并发场景「resolver 经 ServiceLoader 单例注册」**表述不实**；其他 main 文件对 JavaTypeResolver 的引用全部是 javadoc 注释。生产装配点全部不接 L2 resolver（CheckRunner DeepResolvers-only，C6-105 复核已实证），唯一 main 调用链 Constraints.java:253 → TypeQuerySupport.java:73 消费装配注入值（今日恒 null）。
- **判级校准**：增长后果需「长驻进程 + L2 Java 生产接线 + 逐位置持续查询」三前提叠加，live code 今日均不成立——按 C6-105 复核确立的「接线即显形的预埋缺陷 → P3」校准降级。与 C1-103 保留 P2 不构成不一致：C1-103 是并发使用契约下的可见性/正确性缺口，本条是 C5 内存有界缺陷，其损害面今日不可达。初审风险量化中「LSP daemon 长会话逐位置查询」前提随接线状态一并修正。
- **修复建议维持**：两个查询缓存比照 `filesByPath` 改有界 LRU；落地时机与 L2 Java 生产接线（roadmap 37/40）同批，接线时按 C6-105 同款纪律补所有权/边界保护力测试。
- **现状**：未修复（在编未接线）。

### CS-003 — 保留，P3 维持

- **证据逐行重演通过**：XmlTagKinds.java:46-50 `IDS.computeIfAbsent(tagName, ...)` 映射函数内以 `IDS.size()` 分配 id（:47）+ `NAMES.put(id, name)` 跨 map 写副作用（:48），与初审引用一致；CHM computeIfAbsent 仅保证单键原子，不同键（不同 bin）并发 compute 可并行执行映射函数——双线程同读同一 `size()` → 两键同 id + NAMES 后写覆盖，竞态机制确定。
- **声明不变量对照成立**：类 javadoc（:16-21）声明 ids 单调分配、整 run 稳定，「per-JVM intern 表」语义以唯一性为前提；竞态直接破坏该不变量（XNodeLintNode.kindId :144 与编译期 pattern id :133 串扰），非原语误报。
- **调用面核实**：编译期 XNodePatternCompiler.java:133、XmlLanguage.java:88；运行期 XNodeLintNode.java:144、XmlValueNode.java:40——与初审一致。
- **P3 校准确认**：当前主入口无并发首见窗口（CLI 单线程、XML 面未入 LSP 默认绑定；GraphQL 对 XML 并发是合法但未注册形态），暴露为理论路径；intern 后不复发、难复现——P3 恰当。
- **修复建议精化**：初审两方案中优先「类内专用锁串行化 (a)(b) 与两 map 写入」——`AtomicInteger nextId` 在 CHM 争用导致映射函数重试时会烧号（返回前插入失败即弃号），破坏 javadoc :10-11 "dense non-negative ids" 声明；专用锁无此副作用且 intern 低频，代价可忽略。
- **现状**：未修复。

### CS-R2-001 — 保留，P2 维持，独立立案确认

- **证据重演（全部吻合）**：ok 帧 correlation id 校验只存在于 `isOk==true` 分支（NodeTscBridge.java:165-173）；error 帧路径 :177 `throw TscProtocol.errorOf(id, frame)` 无 id 比对、无 `handleProcessFailure`；`errorOf`（TscProtocol.java:119-126）用**在途 id** 拼错误消息（误归因在日志层不可辨）；`decode` 抛 BAD_FRAME 不回收（TscProtocol.java:41-47 + NodeTscBridge.java:164 在任何回收 try 之外）；`validateReady`（TscProtocol.java:71-82）只查 type + typescript 串非空，**无协议版本字段**；对端证据复演通过——ready 帧无版本、dispatch 解析失败与内部异常均合成 `id=-1` 帧、`writeFrame` 单行 JSON 完整性（tsc-bridge-server.mjs:53-55 及文件尾部）；`resolveHelperPath` 按 cwd 上溯（TscBridgeConfig.java:93-107）跨 checkout 错配路径成立。
- **独立性确认（C1 复核裁定照准）**：CS-001/C6-101 是**生产侧**代际隔离缺口（陈旧帧得以入队），本条是**消费侧**对已入队帧的校验与回收缺口——不同层守卫，互补且均必要，维持独立立案。
- **去重注记**：**时序 B（BAD_FRAME 不回收）与 C6-104（P3 保留）同站点同机制**，该子面并入 C6-104 的「统一失败出口」修复，不在本条重复跟踪；本条独有核心 = error 帧 id 校验缺失 + 协议版本缺失。时序 A 变体（陈旧 error 帧过握手 → 终端 EXHAUSTED）的触发源与 C6-101 重叠，但 C6-101 修复封死陈旧帧源后，本条建议(1)仍封住剩余源（崩溃半帧、helper 传递性 stdout 污染——mjs 自身 stdout 纪律已核，唯 require 传递面无排他守卫）下的同型终局，不构成重复立案。
- **P2 校准确认**：触发需前置去同步源（故非 P1），但后果含「健康新 peer 永久判死 + 失真应答静默上抛」，且现有唯一缓解（`nextId` 单调不重置）恰对 error 帧失效——P2 成立。
- **现状**：未修复。

## 二、CL-501~507 裁定

- **CL-501 确认，归并 C1-104（同站点）**：LanguageRegistry register check-then-act 即 C1-104 本体（C1 复核保留 P2，「缺陷活在嵌入契约面」裁定覆盖 CL-501 的 embedder 场景描述）；转引用，不独立跟踪。
- **CL-502 确认为同站点重复，归并 C1-103**：TscTypeResolver.isAvailable 无锁读非 volatile `bridge` 与 C1-103 完全同点（C1 复核保留 P2）；CL-502 的「行为良性；不建议改」评估被 C1 复核裁定取代（门控探针失真 → L2 反复进入 → degrade 噪声；加 volatile 为一行修复）。主登记以 C1-103 为准。
- **CL-503 确认（watch 维持，不立案）**：`inbound` 无界 LinkedBlockingQueue（NodeTscBridge.java:59）实证；单飞行协议 + 超时 destroy + spawn clear 使稳态在途有界成立；陈旧帧面由 C6-101 主登记覆盖——与初审「同点不同面」定位一致。
- **CL-504 确认（watch →C9，与 CL-101/CL-401 同站点归并）**：install 全局换装与竞态/无卸载面已由 C1-101（P1 保留）主登记；CL-504 仅保留 passthrough 分支每节点 scope 取值开销的性能面（LintDeadlineExecutor.java:79-88），归 C9 bench 对比，design 07 §4 Route A 裁定不翻案。
- **CL-505 确认（watch/doc，不立案）**：`onMessage` 全方法无同步（NopLintLanguageServer.java:103-146）、`documents` CHM（:159/:181）、`shutdownRequested` volatile（:117）实证；与 C1 批 B 载体表 #7「check-then-act 序列靠传输层单线程约束成立」判定一致；「owner doc 声明单线程传输假设」建议落 owner doc 维护项，行为不变更，不立案。
- **CL-506 确认（watch，工具仓）**：mjs `projects` Map（:51，set 覆盖、无删除/淘汰路径）、per-project `queryCache`（newProject 内 `new Map()`，无界）与 `queryPrograms` 16 条 FIFO（:239、:262-265）对照全部复演；helper 在审计单元外（工具仓），watch-only 维持。
- **CL-507 确认（watch，不立案）**：`put`（RuleResultCache.java:175-191）只覆盖同路径、无全量重建分支，陈旧路径条目驻留至 `runFingerprint` 变化；fail-closed load + 内容哈希 + 原子写守卫已在案（C1-105/C6-102）。F4 弱形态、磁盘面，watch 维持。

## 三、最终保留项

- **CS 立案复核 4/4 保留，驳回 0**；判级调整 1（CS-002 P2→P3，现状修正）；登记归属改判 1（CS-001 → C6-101 主登记引用，与 C1 复核、C6 复核三方闭合）；独立维持 1（CS-R2-001，附 C6-104 子面去重注记）。
- **判级终态**：CS-001 P1（本体保留，主登记 C6-101）；CS-R2-001 P2（维持）；CS-002 P3（降级）；CS-003 P3（维持）。
- **NodeTscBridge 归并终态**：C6-101 唯一主登记（C1-102/CS-001 转引用，C1-102「诊断误导」侧写入其验收标准）；C6-103/104 挂其失效面清单；CS-R2-001 消费侧独立。Phase 6 台账按此归并，CS 批不再有独立 P1。
- **CL-501~507 全部确认**：CL-501→归并 C1-104；CL-502→归并 C1-103；CL-503 watch 维持；CL-504→C9（并 CL-101/CL-401 族）；CL-505→owner doc 声明（watch）；CL-506→工具仓 watch；CL-507→watch。无驳回、无升级。
- **现状标注汇总**：4 案全部「未修复」（live @ 0d131797dd）；CS-002 另标「在编未接线」（main 0 构造点 + 无 ServiceLoader 注册）。
- **复核覆盖核验**：抽核的全部行号锚点（NodeTscBridge :59/:148/:151-155/:157-177/:195-243/:245-265/:267-283、TscProtocol :33-47/:71-82/:108-138、XmlTagKinds :16-59、JavaTypeResolver :57-130、TscBridgeConfig :85-107、mjs :51/:53-55/:239/:262-265/尾部 ready 帧、RuleResultCache :175-208、NopLintLanguageServer :103-159、JavaSemanticResolver :35-41、XNodePatternCompiler :133/XmlLanguage :88/XNodeLintNode :144/XmlValueNode :40）与初审引用吻合，无证据失实。初审勘误 2 处（均随本结论修正）：CS-002「ServiceLoader 单例注册」表述不实；CS-002「LSP 每键 didChange 长驻主路径」前提今日不成立（在编未接线）。
