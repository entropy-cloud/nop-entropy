# C1 共享可变状态与可见性 — nop-tools/nop-treesitter + nop-tools/nop-jq

> **审计批次**: CRA WI 6.2 批 A（盲审批次 1/2）。范围：`nop-tools/nop-treesitter`（main 92f / 18,213 行）、`nop-tools/nop-jq`（main 72f / 7,982 行）——规模口径已用 `find -path "*/main/*" -name "*.java" | wc -l` + `wc -l` 复核，与基线一致。
> **基线**: `_tmp/cra-baseline-nop-tools-parse.txt @ 0d131797dd`。
> **盲审纪律**: 未读 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`、`ai-dev/backlog/`；只读审计，除本报告外未改任何文件。
> **CL 前缀**: `nop-tools-parse-CL-0NN`。
> **方法论**: `ai-dev/skills/deep-audit-prompts.md` 共享前缀 + `ai-dev/skills/concurrency-resource-audit-prompt.md` C1 正文。
> **Status**: open（初审，未复核）

## 0. 方法与 live-code 勘误

精读文件（全文）：`TSParser`、`TSTree`、`TSNode`、`TSTreeCursor`、`TreeNavigator`、`SubtreeArena`、`Subtree`、`GLRParser`（字段面 + 静态 parse 入口 + 主循环）、`Language`（全文）、`DefaultTreeSitterLanguageProvider`、`compat/{TSParser,TSTree,TSNode,TreeSitter*}`、`ScannerVM`、`ScannerProgram`、`Lexer`、`query/{TSQuery,TSQueryCursor}`、`shell/*` 字段面、`biz/{TreeSitterBizModel,ShellAnalysisBizModel}`、jq 全量（`JqEngine`/`JqLexer`/`JqParser`/`JqDirectQuery`/`IJsonQuery`/`ast/*` 字段面/`JqExecutor` 全文/`JqEnvironment` 全文/`JqValues`/`JqBuiltins` 静态面/`JqTimeFunctions`/`JqRegexSupport`/`JqPathEval`/`JqShortCircuitList`），并核对平台设施使用（`LocalCache.get` 底层为 Caffeine LoadingCache，per-key 原子加载）。

**勘误（对照基线口径）**：live code 中**不存在** MemorySegment / Arena（FFM）载体；基线 "C6 native/arena" 列出的 9–10 个文件的实际载体是 `SubtreeArena` 的并行 `int[]` 列（纯 Java 堆内结构）。本报告按 live code 归档。

**执行模型事实**：两模块为纯同步库——0 个自建线程/线程池、0 条异步链、0 个定时任务（与基线 C4 仅命中 `nop-lint-js`（批 B 范围）一致）。共享面全部收敛在「语言对象缓存」「编译缓存」「ThreadLocal 池」「解析实例逃逸」四类。

## 1. 五类载体清单表

判定口径：写者 × 读者 × happens-before 机制 × 后果。安全者记表不立案。

| # | 载体类 | 载体（file:line） | 写者 | 读者 | 同步机制 | 判定 |
|---|---|---|---|---|---|---|
| 1 | singleton bean 实例字段 | `TreeSitterBizModel.languageProvider`（:24-29，protected + setter 注入） | IoC 初始化 | GraphQL 请求线程 | 平台单例初始化先于首请求；此后只读 | 安全 |
| 2 | singleton bean 实例字段 | `ShellAnalysisBizModel.languageProvider`（:77-82） | 同上 | 同上 | 同上 | 安全 |
| 3 | static 可变字段 | **两模块 0 处**（grep 口径 `static [A-Za-z]` 排除 `static final` 与方法声明后仅剩 static 方法；static final 集合均为 `Map.of/Set.of/List.of` 不可变注册表：`BUILTIN_BLOBS`、`REQUIRED_NODES`、`ShellNodeTypes.*`、`ShellAnalysis.*`） | — | — | — | 安全 |
| 4 | 全局缓存 | `DefaultTreeSitterLanguageProvider.cache`（:38，ConcurrentHashMap；getLanguage :42-53） | 首触线程 `cache.put`（:52） | 全部解析入口 | CHM put 安全发布；**get(:46)→loadLanguage(:50)→put(:52) 复合操作非原子** | **立案 C1-001** |
| 5 | 全局注册表 | `DefaultTreeSitterLanguageProvider.customProviders`（:39，volatile + DCL :101-119） | 首次 `languageNames`/`getLanguage` | 同 | volatile + synchronized DCL，读路径无锁取局部快照，正确 | 安全 |
| 6 | 全局缓存（共享 Language 态） | `Language.validSymbolCache`（:78-79，CHM `computeIfAbsent` :731-733）；值 `boolean[]` 仅 Lexer.java:178 / ScannerVM.java:83 只读消费 | scan 期惰性填充 | 同 | CHM 原子 + 值按约定不可变（已核对全部消费点只读） | 安全 |
| 7 | 共享 Language 态 | `Language.observedNodesPerByte`（:80，volatile float；`noteNodeCount` :762-769 读-改-写 max） | 每次完成的 parse（TSParser:62/158） | 下一 parse 的 `expectedNodes`（TSParser:78） | volatile 无撕裂；丢失更新仅使 arena 预留估计滞后一拍——单调启发式，注释明言设计取此（"monotone and noise-immune"），无正确性后果 | 安全（注记：max 丢失更新为良性，非护栏） |
| 8 | 共享 Language 态 | `Language.scannerProgramValidated`（:77，volatile boolean；:777-784 check-then-set） | 首次 `validatedScannerProgram` | ScannerVM.scan | 竞态最坏后果 = `ScannerProgram.validate` 幂等重复执行一次（纯校验、无副作用），flag 两次置 true | 安全（幂等竞态） |
| 9 | 共享 Language 态 | `Language.externalScannerFactory`（:76，volatile Supplier；:793-804） | 装配期一次性接线（Provider:74-79 / compat static-init） | 每 parse `newExternalScanner()` 新建实例 | volatile；工厂每 parse 产出独立 scanner（跨调用状态随实例封闭） | 安全 |
| 10 | static 缓存对象 | compat `TreeSitter{Bash,Python,Rust,CSharp,Go,Typescript}.LANGUAGE`（各文件 `static final Language` + static 块接线 factory） | 类初始化 | compat 消费方 | JLS 类初始化 happens-before 首次使用；factory 接线在发布前完成 | 安全 |
| 11 | 全局编译缓存 | `JqEngine.cache`（JqEngine:15-17，static `LocalCache.newCache("nop-jq-compile", newConfig(1000), loader)`） | `compile()`（:22-24） | 所有 `compile().apply()` 消费方（跨线程共享） | 底层 Caffeine LoadingCache：per-key 加载原子（同键只编译一次），put 安全发布；上限 1000 有界声明 | 安全 |
| 12 | ThreadLocal | `TSNode.SCRATCH` + `SCRATCH_DEPTH`（TSNode:112-113；borrow/release :115-145） | 各结构访问器 | 同线程 | depth 计数守卫重入（嵌套访问器回退新游标）；归池时 `cursor.release()` 置空 tree/arena/source 引用（:133-145），池化线程不钉死最后一棵树；`SCRATCH_DEPTH` 仅 1-int 残留 | 安全（设计完备） |
| 13 | ThreadLocal | `ScannerVM.POOL`（ScannerVM:93；run :95-119） | 每次 external scan | 同线程 | finally 置空 program/source/validSymbols（:111-118）防源字节钉留；`reset()` 下次全量重锚；无重入路径（execute 不递归 run） | 安全 |
| 14 | 隐式共享/逃逸 | `TSTree.arena()`（:107-109）与 `source()`（:92-94）公开可变内部载体；类声明 "Immutable parse tree" | 模块内 arena 写点仅 GLRParser 解析期与 `TSTree.copy` 快照期（grep 全部 `setSize/free/allocate` 调用点核实），**无发布后写点** | 消费方跨线程只读共享 | 消费方经并发容器/Executor 发布即建立 HB；"read-only by convention" 已在 javadoc 声明（:103-105），但 arena 的 allocate/free/setSize 全为 public、无运行时守卫 | 安全（约定式不可变，守卫缺失但已声明边界） |
| 15 | 隐式共享/逃逸 | jq 编译产物 `JqDirectQuery(expression, ast, executor)`（:14-23）经 #11 全局缓存跨线程共享 | `JqParser` 编译期 | 各线程 `apply` | AST 全部节点 `private final` 字段（ast/* 字段面核对），parser 构造即弃局部 list、无保留写引用；`JqObject` 构造即 `Collections.unmodifiableMap` 包装（JqValues:123-140）；`Pattern` 线程安全、Matcher 每次新建；executor 唯一可变态 `debugSink`（非 volatile，:26-30）**主路径无写者**（grep 全仓库 setter 无 main 调用点），仅测试缝 | 安全（注记：debugSink 理论暴露面，见 §4 注 N1） |
| 16 | 隐式共享/逃逸 | `JqEnvironment` fork 共享 `scopes` 元素 map、`functionDefs`、`pendingInputs`（:21-32, :183-187） | 同一 `apply()` 调用线程 | 同线程 | 线程封闭（apply 全同步）；fork 后所有绑定路径先 `pushScope` 再 `bind`（JqExecutor visitBind :282-292 / visitReduce :335-344 / visitForEach :352-367 / bindValueParamsCartesian :800-806 已逐一核对）；`defineFunction` copy-on-write（:86-92） | 安全 |
| 17 | 隐式共享/逃逸 | `GLRParser` 实例态（GSS/versions/`childrenScratch`/`stackScratch`，:61-87, :385, :1501） | 静态 `parse` 每 parse `new GLRParser(...)`（:113-115, :124-126） | 同 | 每 parse 新实例 + 新 `SubtreeArena`，实例封闭，无跨 parse 复用 | 安全 |
| 18 | 隐式共享/逃逸 | `SubtreeArena` 列数组（非 final、非线程安全） | 解析期 / 快照拷贝期 | `TSTree` 持有只读 | per-parse/per-tree 私有（TSParser:60 一次性 arena + adopt/snapshot），发布链单一 | 安全 |

## 2. 立案发现

### [C1-001] provider 语言缓存 get→load→put 复合操作竞态：同名语法可产生两个 Language 实例

- **文件**: `nop-tools/nop-treesitter/src/main/java/io/nop/treesitter/provider/DefaultTreeSitterLanguageProvider.java:42-53`（复合操作）, `:64-82`（loadLanguage 重复解码）
- **证据片段**:
  ```java
  Language cached = cache.get(name);          // :46
  if (cached != null) {
      return cached;                          // :48
  }
  Language language = loadLanguage(name);     // :50  ← 窗口：整段 blob 解码
  if (language != null) {
      cache.put(name, language);              // :52  ← 后写者覆盖
  }
  return language;
  ```
- **严重程度**: P2
- **并发场景**: IoC 单例 provider bean（`app-treesitter.beans.xml`）被全部 GraphQL/Java 消费方共享；多个 HTTP worker 线程在缓存未热时并发首触同一语法名（冷启动并发流量、并发解析同名语法）。
- **触发时序**: T1 `getLanguage("bash")` :46 miss → :50 进入 `loadLanguage`，正在执行 `Language.fromClasspath`（毫秒级 blob 解码，Provider:73）→ T2 同名 :46 miss（T1 未 put）→ 同样进入 :50 二次解码 → T1 :52 put 返回实例 L1 → T2 :52 put 覆盖，返回实例 L2 → cache 最终持其中之一，而 T1、T2 的调用方分别持有 L1、L2。后果链：T2 侧用 L2 `TSParser.parseIncremental`，传入的 oldTree 由 L1 解析时，`TSParser.java:127-129` 抛 `TreeSitterException("oldTree was parsed by a different language instance")`——语言实例被当身份键使用，两个"相同"的 Language 对象身份断裂；同时两份完整解码表（parse tables + lexer automata）同时驻留。
- **现状**: CHM 的 get 与 put 之间的检查-然后-行动窗口未闭合（F10 家族）。
- **风险**: (1) 跨实例树复用 fail-fast 异常直接冒到调用方，且成因难以定位（两实例内容等价、身份不等）；(2) 冷启动并发下重复 blob 解码 + 双份表驻留（每语法数百 KB 级）；(3) 任何后续把 Language 实例当缓存键/相等语义使用的代码都被此裂缝污染。无数据损坏，故按 P2 而非 P1 校准立案：冷路径（每语法每进程首触一次）+ 后果 fail-fast；"罕见但可具体化的竞态"字面可入 P1，留复核裁定。
- **建议**: 改 `cache.computeIfAbsent(name, this::loadLanguage)`（CHM 原子，同键单次加载）。已核实 loader 链不递归本 cache：`loadLanguage` 对 custom 语法走 `customProviders().get(name)`（volatile DCL，:101-119）→ 第三方 provider 自身 `getLanguage`，不回环本 cache，无 CHM bin-lock 递归死锁风险。若维持 get/put 形态，则须在 owner doc 声明"语言实例不保证跨调用同身份、增量树复用必须以同一实例链持有"。
- **失效面枚举**（F3 同族清扫，两模块全部缓存复合路径）: ① `DefaultTreeSitterLanguageProvider.cache` get/put — **本案，竞态**；② `JqEngine.cache`（Caffeine LoadingCache.get 原子加载）— 安全；③ `Language.validSymbolCache` `computeIfAbsent`（:731-733）— 安全；④ `customProviders` DCL（:101-119）— 安全；⑤ `GLRParser.externalScanner` 惰性获取（:804-805，per-parse 实例单线程）— 安全；⑥ `Language.validatedScannerProgram` check-then-set（:777-784，幂等纯校验）— 安全。同族 6 处，仅本案竞态。
- **信心水平**: 确定
- **误报排除**: 不是"CHM 使用即报"的原语误报——本案是 get 与 put **之间**的复合操作（共享前缀执行步骤 4 的定义形态），触发时序已具体到行级；后果非无害重复编译，因为 Language 实例身份被 `TSParser.parseIncremental`（:127）与 compat 层当契约使用。
- **复核状态**: 已复核（WI 6.2 C1 独立复核）——**保留，P2 维持**。get(:46)/load(:50)/put(:52) 复合窗口、`TSParser.java:127-129` 身份契约、singleton bean（app-treesitter.beans.xml:7）全部复核实证；冷路径 + fail-fast 的 P2 校准成立。

## 3. 维度集外强信号线索（CL）

### [nop-tools-parse-CL-001] jq 输出预算按 fork 血统重置：嵌套用户函数使 MAX_OUTPUT_COUNT 护栏按深度倍增（C5/C7 形态）

- **文件**: `nop-tools/nop-jq/src/main/java/io/nop/jq/runtime/JqEnvironment.java:104-132`（计数与上限）, `:183-187`（fork 快照传递）
- **证据片段**:
  ```java
  public void checkOutputLimit() {
      ...
      if (++outputCount > MAX_OUTPUT_COUNT) {   // :128  每环境独立计数
  ...
  public JqEnvironment fork() {
      JqEnvironment env = new JqEnvironment(scopes, functionDefs, true, callDepth,
              outputCount, pendingInputs, deadlineAtMillis, envVarSupplier); // :184-185 传快照值
  ```
- **形态**: C5 内存有界性 / C7 长时运行稳定性（护栏强度口径）
- **说明**: `MAX_OUTPUT_COUNT = 1_000_000` 是**每环境**（每 fork 线）预算而非全局：`callUserFunction`（JqExecutor:764）对函数体 `fork()` 后，子环境从父计数快照继续、各自最多再产出 1M 输出且增量不回传父环境。调用深度上限 2000（`MAX_CALL_DEPTH`）下，深嵌套程序的有效输出上界 ≈ 深度 × 1M，且各 fork 的输出列表同时存活于调用栈（内存峰值同倍放大）。护栏仍能防单点无界循环，但"安全网"与"全局护栏"语义有差；`repeatString` 的按追加计数（JqExecutor:957-972）同样按 fork 线生效。同型注记：`Language.observedNodesPerByte`（语言级 arena 预留估计）为单调启发式，不是护栏，勿在 C5 复核中按护栏口径引用。
- **信心水平**: 很可能（语义推导确定，最坏倍增需嵌套函数形状输入，未构造具体 jq 程序验证）
- **复核状态**: 已复核——**确认**。JqEnvironment:124-132（:128 `++outputCount`）、fork :183-187 快照传递、JqExecutor:764 `definitionEnv().fork()`、MAX_CALL_DEPTH=2000（:16）/MAX_OUTPUT_COUNT=1M（:18）、repeatString :966-971 全部复核实证；移交 C5/C7。

## 4. 安全注记（记表不立案的补充说明）

- **N1** `JqExecutor.debugSink`（非 volatile 可变实例字段，位于经全局缓存跨线程共享的 executor 上）：setter 为 public、注释声明"tests may replace it"，grep 全 main 源无调用点；且 `IJsonQuery`/`JqDirectQuery` 不暴露 executor，外部无法触及共享实例的 setter。当前无触发者，理论暴露面；若未来暴露 getter 或加 setter 调用点，需提升为发现。
- **N2** `TSTree` "不可变"为约定式（#14 行）：arena 变更 API 全 public 且无守卫。模块内无违例写点；本条为声明边界，非缺陷。后续若有人实现"tree 缓存 + 增量改写"类消费方，此约定即失效面。
- **N3** compat `TSParser` 的 `language` 实例字段非 final、无同步（compat/TSParser:12-16）：沿用 bonede JNI 时代"parser 实例非线程安全、每线程一个"的消费契约（`setLanguage` 后按单线程使用），迁移语义"只换 import、行为不变"成立，不构成回归。
- **N4** `JqEngine.cache` 键空间为用户可控 jq 表达式：上限 1000（`newConfig(DEFAULT_CACHE_SIZE)`）+ Caffeine 淘汰，有界声明成立，不属 C5 无界候选。

## 5. 小结

两模块的共享面设计整体质量高：无 static 可变字段、无手写锁、ThreadLocal 池均有显式引用释放、编译缓存走 Caffeine 原子加载、AST/值对象不可变成立。唯一竞态立案（C1-001）为语言 provider 缓存的检查-然后-行动窗口，修复面一行（computeIfAbsent）。C1 立案数：1（P2）；CL 线索：1。


---

# C1 初审追加（批 B）

盲审批次 2/2，批 B 范围：`nop-tools/nop-lint`（160f，core 126f 为主）+ `nop-tools/nop-refactor`（33f）。基线：`_tmp/cra-baseline-nop-tools-parse.txt`（C1 static mutable=63、ThreadLocal=2、G10 站点=LanguageRegistry.java:28）。口径说明：基线 63 为 `grep -E "static [A-Za-z]" | grep -v "static final" | grep -v "@"` 的宽口径（含 static 方法与 main 壳），按字段级口径（`^\s+(private|public|protected)?\s*static\s+<Type> <name>\s*(=|;)` 排除 final 与方法）复现后 **main 源码 static 非 final 可变字段 = 0**；两个口径的差异全部来自 static 方法声明。ThreadLocal = 2，与基线一致。

## 批 B 载体清单（字段/容器 × 写者 × 读者 × 同步机制 × 判定）

| # | 载体 | 写者 | 读者 | 同步机制 | 判定 |
|---|---|---|---|---|---|
| 1 | `EvalExprProvider._executor`（static，非 volatile，nop-core:19） | `LintDeadlineExecutor.install()`（每次 lint 调用，CLI/Mojo/LSP/GraphQL 请求线程）+ 调试器 attach/detach | 全 JVM 所有 XLang 可执行节点求值（BetweenOpExecutable:76 每节点读） | install 为 `static synchronized`（仅 lint-vs-lint）；平台槽读写无同步 | **C1-101（P1）** |
| 2 | `LintDeadlineExecutor.EVAL_DEPTH`（static ThreadLocal<int[]>） | execute() 内 `depth[0]++` | 同线程 | try/finally 平衡（异常/超时路径均回退，L98-107） | OK（池化线程无泄漏） |
| 3 | `ReferentMatcher.EXPANSION_DEPTH`（static ThreadLocal<int[]>） | matches() 内 `depth[0]++` | 同线程 | try/finally 平衡（L52-64） | OK |
| 4 | `LanguageRegistry.languages`（HashMap，非并发） | `register()`（discoverDefaults 顺序注册 / 嵌入式手动注册） | `resolve()` / `registeredIds()` | 无 | **C1-104（P2）** |
| 5 | `NopLintBizModel.runtime`（volatile）+ Runtime 全 final（Map.copyOf/List.copyOf） | runtime() DCL 锁内构建（含 ServiceLoader+VFS 装载+编译） | 各 GraphQL 请求线程 | volatile + synchronized DCL，正确成对 | OK（首请求持锁做 I/O+编译，一次性路径，按校准不立案） |
| 6 | Runtime.compiledByLanguage 跨请求共享的 CompiledRule/XScriptEngine | 构建期一次性 | 并发请求线程 | 载体全 final；XScriptEngine 每匹配状态（ReportFunction/NodeWrapper/MetaVarEnv/scope）局部；DeclTypeResolver 无状态（源码自证） | OK（共享编译产物并发执行面干净） |
| 7 | `NopLintLanguageServer.documents`（CHM）+ `shutdownRequested`（volatile） | didOpen/didChange/didClose | 同上 | CHM+volatile 防御性；传输层单线程顺序循环（Launcher run()） | OK（check-then-act 序列靠传输层单线程约束成立，CHM 为防御） |
| 8 | `TreeCache`（per-tree CHM×3） | 首次遍历物化 | 包装器读取 | per-tree 实例，无 static map（源码 javadoc 自证 GC 根路径缺失） | OK |
| 9 | Discovery 缓存 ×4（MetricsResolver/ScopeResolver/SemanticResolver/DataflowResolverDiscovery，AtomicReference） | discover() 首调 | 后续调用 | AtomicReference；竞态下双写同值（同 classpath ServiceLoader 确定性） | OK（benign race，值恒等） |
| 10 | `NodeTscBridge` process/peerInput/state/restartsLeft/nextId/closed | synchronized 公有方法 + reader 线程（drainPeerOutput 只读 process、写 inbound） | 同上 | 方法级 synchronized；reader 线程经 Thread.start() HB 看到 process；**inbound 队列跨进程代复用无纪元标记** | **C1-102（P1）** |
| 11 | `TscTypeResolver.bridge`（非 volatile） | bridge() 锁内创建、close() 锁内置空 | `isAvailable()` **无锁读**（L53） | 锁内写/无锁读混合 | **C1-103（P2）** |
| 12 | `RuleResultCache.artifact`（LinkedHashMap）+ 磁盘工件（固定 `.tmp` 名） | put/save | get/save | JVM 内单线程（CheckRunner 串行）；**跨进程无互斥** | **C1-105（P2）** |
| 13 | `LintProfile` 三能力集 / `TargetScanner` 双向扩展表（static final） | 类初始化一次性 | 全部 | unmodifiableSet / Map.copyOf+List.copyOf，构建后不可变 | OK |
| 14 | 语言绑定单例（JavaLanguage/TypeScriptLanguage/TsxLanguage/XmlLanguage INSTANCE，final adapter） | 构造期 | 并发请求 | 全 final 不可变（TreeSitterLanguageAdapter kindIds=Map.copyOf） | OK |
| 15 | `JavaSymbolResolverAdapter.parseTool/scopeAnalyzer`（注入单例的实例字段，跨 rename 请求共享） | 构造期 | 并发 @BizMutation rename | JavaParseTool.instance() 每次 new、getJavaParser() 每次 new JavaParser（每次解析独立）；ScopeAnalyzer 无实例字段 | OK（共享对象有效无状态） |
| 16 | `NopRefactorBizModel.renameResolver`（@Inject protected，单例注入） | IoC 构建期 | 并发 mutation | 注入只读引用；被引对象有效无状态（见 #15） | OK |
| 17 | RefactorOperationRunner / RewriteOperation / RenameOperation（enum 单例） | — | 并发 run | run 内状态全部局部 | OK |
| 18 | `BaselineEngine`/`RuleResultCache` 的 `SHA256_PROTOTYPE`（static MessageDigest） | 类初始化 | 各调用 | clone-per-call（prototypedDigest），原型纪律正确 | OK |
| 19 | `XScriptEngine.declTypeResolver`（实例 final） | 构造期 | 并发匹配 | 单方法无状态（源码自证） | OK |
| 20 | `MetaVarEnv.singles/multis`（非 final） | 编译期匹配填充 | 同一次扫描 | 每节点扫描新建实例（scanTree L754），不跨线程 | OK |

---

### [C1-101] LintDeadlineExecutor.install() 从请求路径写 JVM 全局执行器槽，install 与其他全局写者之间存在丢更新竞态，且包装器永久安装、无卸载路径

- **文件**: `nop-tools/nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/xscript/LintDeadlineExecutor.java:70-76`；全局槽 `nop-kernel/nop-core/src/main/java/io/nop/core/lang/eval/EvalExprProvider.java:19,35-49`；每次 lint 的调用点 `nop-lint-core/.../engine/LintEngine.java:269,311`；同槽其他写者 `nop-dev-tools/nop-xlang-debugger/src/main/java/io/nop/xlang/debugger/initialize/XLangDebuggerInitializer.java:77,156`；平台每节点读面 `nop-kernel/nop-xlang/src/main/java/io/nop/xlang/exec/BetweenOpExecutable.java:76`
- **证据片段**:
  ```java
  // LintDeadlineExecutor.java:70-76
  public static synchronized void install() {
      IExpressionExecutor current = EvalExprProvider.getGlobalExecutor();
      if (current instanceof LintDeadlineExecutor) {
          return;
      }
      EvalExprProvider.registerGlobalExecutor(new LintDeadlineExecutor(current));
  }
  // EvalExprProvider.java:19（无 volatile、无同步）
  private static IExpressionExecutor _executor = DefaultExpressionExecutor.INSTANCE;
  ```
- **严重程度**: P1
- **并发场景**: HTTP worker 线程（GraphQL `Lint__checkSource`/`checkFile` 经 `NopLintBizModel` 共享引擎）、Maven Mojo 线程、CLI/LSP 主线程——任一 lint 线程与另一全局槽写者（如调试器 attach 线程）并发；以及 lint 线程与全 JVM 其他 XLang 求值线程之间的全局槽共享。
- **触发时序**: T1（lint 线程）进入 `LintEngine.lint` → LintEngine.java:311 `install()` → synchronized(LintDeadlineExecutor.class) 内读 `_executor = Default`；此刻 T2（调试器 attach）执行 XLangDebuggerInitializer.java:77 `registerGlobalExecutor(new DebugExpressionExecutor(debugger))` —— registerGlobalExecutor 不在任何 monitor 上，install 的 synchronized 只覆盖 lint-vs-lint，**读-判断-写跨两个不同 monitor 不原子** → T1 写 `LDE(Default)` → T2 写 `Debug(Default)`（后写覆盖，LDE 从链上消失）→ T1 继续执行 xscript：每个可执行节点（BetweenOpExecutable.java:76 每次 `EvalExprProvider.getGlobalExecutor()`）取得 `Debug(Default)`，链上无 LintDeadlineExecutor → **该 lint run 的 per-match deadline 与 MAX_EVAL_DEPTH 深度护栏全部静默失效**，runaway 脚本无界执行（XScriptDeadline 注入仍在 scope 里，但无任何执行点读它）。
- **现状**: 每个 lint 调用（含每个 GraphQL 请求、每个 CLI 文件）都对 JVM 全局静态槽做一次无平台同步的读-改-写；wrapper 一经安装永不卸载（无 uninstall API），此后全 JVM 所有 XLang 求值（与 lint 无关的 XPL/xbiz/ORM 表达式求值）永久经由该 wrapper 分发。
- **风险**: (a) 丢更新竞态使 lint 的资源安全边界（design 07 §3/§4 的核心关切：deadline + 深度上限）静默失效，恶意/病态规则脚本可无界消耗 CPU；(b) 请求路径代码拥有对全平台求值机器的永久副作用，任何后续引入的非包装型全局槽写者都会与 lint 互相静默覆盖；(c) 调试器 detach（XLangDebuggerInitializer:156 无条件重置 Default）会静默剥离 lint wrapper（下一次 lint 自愈，但窗口内若另一线程正在跑 lint，同 (a)）。
- **建议**: 把 deadline 拦截点从全局槽改为随作用域传播：优先复用平台既有机制——`EvalRuntime`/`IEvalScope` 已随调用树传播，deadline 检查可在 `XScriptEngine.executeMatch` 的 scope 注入点配对平台执行约定（如 IExpressionProcessor/scope 级 processor）或在 `action.invoke` 外层以 per-run 包装执行器经 EvalRuntime 显式下传（XLang.java:53 的 execute 入口已接受显式 executor）；若必须保留全局 wrapper，则 `install()` 应记持安装身份并在 lint run 结束时按所有权还原（恢复安装前快照），并把 wrapper 判定改为「链扫描」而非单层 instanceof，同时向 EvalExprProvider 补 volatile（平台侧，plan-first 区域）。
- **失效面枚举**（EvalExprProvider 全局槽全部写者，F3 清扫）: ① `LintDeadlineExecutor.install`（本条，请求路径写者）；② `XLangDebuggerInitializer:77` attach（dev 工具，写 `Debug(current)`——与 ① 互覆）；③ `XLangDebuggerInitializer:156` detach（无条件重置 Default——剥离 ①）；④ `XLangCoreInitializer:58-60` 写 `_exprParser`/`featurePredicateEvaluator`/`deltaExtendsGenerator` 三槽（启动期一次性，非请求路径——同模式不同生命周期，不并列立案，登记为家族背景）。
- **信心水平**: 确定（机制为代码实证；触发依赖第二个全局槽写者与 lint 并发，交错序列如上可完全具体化）
- **误报排除**: 不是「synchronized/FutureTask/ConcurrentHashMap 原语使用非问题」——缺陷本体是跨 monitor 的复合读-改-写与全局生命周期错位，非原语误用；也不是「平台基础设施自身不重审」——nop-core 槽本身只作背景，立案对象是 nop-lint 对它的使用方式（把请求路径写进全局槽且不还原）。低频路径粗锁校准不适用：GraphQL 请求是每请求路径。
- **复核状态**: 已复核——**保留，P1 维持**。`_executor` 非 volatile（EvalExprProvider.java:19）、`registerGlobalExecutor` 无同步（:35-37）、install 为 `static synchronized` 仅覆盖 lint-vs-lint（LintDeadlineExecutor.java:70-76）、第二写者（XLangDebuggerInitializer.java:77 attach / :156 detach 无条件重置）、每节点读面（BetweenOpExecutable.java:76，另 XLang.java:53 / EvalBackendRouter.java:101,117）全部复核实证；main 面无 uninstall（仅测试还原）。触发需调试器 attach/detach 与 lint 并发（dev/长驻宿主场景），护栏静默失效后果安全敏感，P1 成立。与 C6 批 B CL-401 同站点，Phase 6 归并。

### [C1-102] NodeTscBridge 的 inbound 队列跨进程代复用且无纪元标记：上一代 reader 的迟到 EOF 毒化下一代会话，可级联为桥永久不可用（EXHAUSTED）并误记死因

- **文件**: `nop-tools/nop-lint/nop-lint-js/src/main/java/io/nop/lint/js/tsc/NodeTscBridge.java:195-229（spawn）,231-242（drainPeerOutput）,245-265（pollWithDeadline）,267-275（handleProcessFailure）`
- **证据片段**:
  ```java
  // spawn():195-197 —— 复用同一 inbound 队列，无 generation token
  private void spawn() {
      destroyProcess();
      inbound.clear();
      ...
  // drainPeerOutput():231-241 —— reader 线程 finally 无条件 offer(EOF)
      } finally {
          inbound.offer(EOF);
      }
  // spawn() 握手:213-216 —— EOF 一律判「对端退出」
          Object answer = pollWithDeadline(config.handshakeTimeout(), "handshake");
          if (answer == EOF) {
              state = State.EXHAUSTED;
  ```
- **严重程度**: P1
- **并发场景**: 请求线程（synchronized 公有方法内）与后台 reader 线程（`nop-lint-tsc-bridge-reader`，daemon）经 `LinkedBlockingQueue inbound` 交互；跨进程重启代际共享同一队列实例。TscTypeResolver（L2 TypeResolver）按 resolver 生命周期持有单个桥并跨查询复用（TscTypeResolver.java:107-118）。
- **触发时序**: T1 查询超时 → pollWithDeadline:259 → handleProcessFailure:267 → `destroyForcibly(process-1)`、state=NEW；reader-1 仍阻塞在 process-1 流的 readLine()。调用方（RuleSetRunner 按 match 吞掉异常后继续；后续 L2 查询）→ bridge() → spawn() → :197 `inbound.clear()` → 启动 process-2、reader-2 → 握手 poll。process-1 销毁使 reader-1 的 readLine 返回/抛出 → finally :241 `inbound.offer(EOF)` 落入 **process-2 的队列** → 握手 poll 读到 EOF → :214-216 判「peer exited before the ready frame」→ state=EXHAUSTED → **一次普通查询超时级联为桥永久不可用**，且诊断信息误记为对端启动即崩。变体：迟到 EOF 晚于握手落入后续查询 → :139-142「peer died before answering request」→ handleProcessFailure 白烧一个 restartsLeft。窗口实测宽（clear() 后是 fork/exec 级耗时的 ProcessBuilder.start，EOF 几乎必然落在其中），仅当 reader-1 的 EOF 早于 clear() 才无害。
- **现状**: 队列与 state 字段是跨进程代共享的可变状态，重启路径（spawn/handleProcessFailure/close 三处 destroy 调用点）无一代际隔离或陈旧载荷丢弃机制。
- **风险**: 超时→重启→误判 EXHAUSTED 的级联使 tsc 桥在 L2 规则面（TS/TSX STANDARD/DEEP lint）从「可自愈的单次超时」劣化为「永久不可用直到进程重启」，且失败原因被误记（排查误导）；restart 预算被幻影 EOF 消耗，违背「budget exhausted 只反映真实重启耗尽」的设计语义（design 11 §3 fail-visible 承诺被静默扭曲）。
- **建议**: 给每代会话一个纪元标记（如 `long generation` 捕获进 reader 闭包，`offer` 时携带，`pollWithDeadline` 丢弃非当前代载荷；或将 `inbound` 改为每代新建并在 reader 结束后丢弃引用）；`drainPeerOutput` 捕获自己那代的 process 引用（已隐式满足）外，EOF 前校验代际；对称修复 `handleProcessFailure` 后清空队列的时点（移到 reader join/代际失效之后）。
- **失效面枚举**: process 生命周期全部对子逐一状态——① spawn():195（clear 在先、无纪元）缺陷本体；② handleProcessFailure():267-274（destroy+state 回退，EOF 竞争源之一）；③ close():184-190（destroy 后 reader EOF 无害——close 后 state=EXHAUSTED，offer 仅残留队列，无后续消费方）；④ spawn 握手 EOF 分支:213-216（误判点）；⑤ request EOF 分支:139-142（误判点 2，烧预算）。同族兄弟进程桥：本批范围内无其他（基线 C4 仅 NodeTscBridge 一处）。
- **信心水平**: 很可能（代码路径全部实证；EOF 落点窗口依赖时序，但窗口宽度由 fork/exec 耗时保证，多数重放命中）
- **误报排除**: 不是「测试专用简化并发模型」——生产 tsc 桥类；不是「有界声明存在的队列」——缺陷不在队列容量而在跨代载荷污染；也不是 F10「声明单线程」契约类——本类从未声明单线程，公有方法显式 synchronized 表明支持并发使用。
- **复核状态**: 已复核——**缺陷本体保留（P1 机制成立），登记归属改判**：spawn:195-197 clear 无纪元、drainPeerOutput:240-242 finally 无条件 offer(EOF)、握手误判点 :213-216、request 误判点 :159-163 全部复核实证。与 C6-101/CS-001 为**同一根因（重启代际无栅栏）的同一缺陷**，非不同失效层；主登记建议 **C6-101**（后果枚举最全：stale 数据帧杀健康进程 + stale EOF + 孤儿进程 + 预算击穿，修复方案同源）。本条转同站点引用登记，独有侧写「失败原因误记/诊断误导」并入 C6-101 验收标准（详见文末维度复核结论）。

### [C1-103] TscTypeResolver.bridge 非 volatile 且 isAvailable() 无锁读：可用性探针与惰性创建/关闭之间的可见性与 check-then-act 缺口

- **文件**: `nop-tools/nop-lint/nop-lint-js/src/main/java/io/nop/lint/js/tsc/TscTypeResolver.java:51-60（无锁读）,99-105（锁内 close）,107-118（锁内写）`
- **证据片段**:
  ```java
  // isAvailable():52-59 —— bridge 无锁读
  public boolean isAvailable() {
      NodeTscBridge current = bridge;
      if (current != null) {
          return current.isAvailable();
      }
      return config.isEnvironmentUsable();
  }
  // bridge():107-110 —— 锁内写
  private NodeTscBridge bridge() {
      synchronized (lock) {
          if (bridge == null) {
              bridge = new NodeTscBridge(config);
  ```
- **严重程度**: P2
- **并发场景**: 同一 resolver 被多线程共享时的 lint 线程（l2Ready→isAvailable 门控读）与首个 L2 查询线程（bridge() 锁内创建写）/关闭线程（close 锁内置空）。
- **触发时序**: T1 执行 `bridge()` 锁内 `bridge = new NodeTscBridge(config)`；T2 并发执行 `isAvailable()` 的无锁读——`bridge` 非 volatile，T2 可能长期读到 stale null → 走 `config.isEnvironmentUsable()` 分支，在桥已 EXHAUSTED 时仍报告可用（探针语义失真）。下游：`l2Ready`（LintEngine.java:395-397）据此放行 → 规则进入 L2 查询 → `TscBridgeUnavailableException` → `TypeResolutionException` → 引擎按 degrade 计数跳过（fail-closed 兜底，不产生错误答案）。对称时序：T2 读到 stale 非 null 桥而 T1 已 close → bridge.isAvailable() 自身 synchronized 读到 closed=true，返回 false，无害。
- **现状**: 锁内写/无锁读混合；isAvailable() 作为门控探针读到的可用性与真实状态可无限期偏离（无 happens-before）。
- **风险**: 门控失真 → 本应 gate 阶段降级的规则反复进入 L2 查询后逐 match 失败，放大 degrade 计数与日志噪声；无错误答案，故 P2。另注：当前 main 源码无 TscTypeResolver 构造调用方（仅测试/嵌入式接线），属「在编未接线」面。
- **建议**: `bridge` 加 volatile（成本可忽略，读多写少）；或 isAvailable() 收进 `synchronized(lock)`（探针是低频路径，粗锁按校准可接受）。
- **失效面枚举**: TscTypeResolver 全部桥接触点——① isAvailable():52（无锁读，缺陷本体）；② initProject:63-67 / isAssignableTo:75-77 / typeNameAt:85-87（经 bridge() 锁内，OK）；③ close():99-104（锁内，OK）；④ projectInitialized:30（仅锁内读写，OK）。同族：NodeTscBridge 自身字段全部锁内（C1-102 另案）。
- **信心水平**: 确定（可见性缺口机制成立）/ 影响很轻（下游 fail-closed）
- **误报排除**: 不是「无状态单例 bean 被并发调用属设计意图」——`bridge` 是惰性创建的可变共享引用，非注入只读依赖；也不是「原语使用非问题」——缺陷是锁内外访问面不一致的复合问题。
- **复核状态**: 已复核——**保留，P2 维持**。`bridge` 非 volatile（TscTypeResolver.java:29）、isAvailable 无锁读（:52-59）vs 锁内写（:107-118）/锁内 close（:99-105）复核实证；l2Ready 门控（LintEngine.java:395-397）与下游 fail-closed 链核实。现状标注补强：「在编未接线」实证——main 源码无 `new TscTypeResolver` 调用方（grep 全仓仅测试），live 影响进一步受限。

### [C1-104] LanguageRegistry 以裸 HashMap 承载注册表：register 的冲突护栏是 check-then-act，读面与并发 put 交叠未设防，且类契约宣传手动注册但无线程契约

- **文件**: `nop-tools/nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/engine/LanguageRegistry.java:30（HashMap）,61-77（register check-then-act）,84-96（resolve 读）,101-105（registeredIds 迭代）`
- **证据片段**:
  ```java
  private final Map<String, LintLanguage> languages = new HashMap<>();
  // register():70-76
  LintLanguage existing = languages.get(normalized);
  if (existing != null && existing != language) {
      throw new NopLintException("lint language id '" + language.id() + "' is already bound to "
              + existing.getClass().getName() + "; conflicting registration from "
              + language.getClass().getName());
  }
  languages.put(normalized, language);
  ```
- **严重程度**: P2
- **并发场景**: 类 javadoc（L19-20）明确宣传「tests and embedders may also register(LintLanguage) manually」——即嵌入式/测试方在共享 registry 上的并发注册；resolve/registeredIds 与之并发。
- **触发时序**: T1、T2 并发对同一 id 注册**不同**实例：T1 `languages.get(id)` → null（通过护栏）→ T2 `get(id)` → null（同样通过）→ T1 `put` → T2 `put` → **后写覆盖，冲突未被发现**——直接违反 L57-59 的明文裁定「binding the same id to a different instance fails closed — ...not a winner-takes-all race」。次生面：任一线程 `registeredIds()` 迭代 keySet（L102）与另一线程 put 交叠 → ConcurrentModificationException 或脏读；`resolve` 的 fail-closed 报错信息（L92-93）调用 registeredIds 同面。
- **现状**: 注册表容器无并发防护，冲突守卫的判定与写入非原子；仓库内全部现存调用方（NopLintBizModel:77 / NopRefactorBizModel:122,210 / NopLintCli:67 / CheckMojo:169 / LSP create:66 / RuleTestRunner:80）均为「方法内新建 registry → discoverDefaults 单线程顺序注册 → 安全发布（final/volatile/DCL）」——发布后无人再 register，故 JVM 内主路径不可触发；缺陷活在嵌入式契约面。
- **风险**: 嵌入式并发注册时冲突静默被吞（恰好是设计文档点名要防的 winner-takes-all），绑定错语言实例的 lint run 全部规则 resolve 到错误 binding；迭代交叠抛 CME 使无关请求失败。
- **建议**: 容器换 `ConcurrentHashMap` + `register` 改 `languages.putIfAbsent` 语义（同实例重注册幂等：先 get 比较再 putIfAbsent）；或注册方法 synchronized + 容器在「冻结」后替换为不可变视图（registration phase / read phase 两段式，与 Runtime 构建期-发布期的既有形态一致）。
- **失效面枚举**: 注册表写读面全清单——① register（写，缺陷本体）；② resolve（读，单键 get，HashMap 读-读安全、读-写交叠危险）；③ registeredIds（读，迭代，交叠最脆）；④ discoverDefaults（顺序写，单线程 OK）；⑤ 同族注册表对照：TargetScanner 静态表（构建后不可变，OK）、Discovery×4（AtomicReference，OK）、平台 GlobalCacheRegistry（本批未用）。
- **信心水平**: 确定（守卫非原子为代码实证）/ 触发条件在库内不存在（嵌入式契约面）
- **误报排除**: 不是「低频路径粗锁」类误报——恰恰相反，该路径**没有**任何锁；立案点是无同步 + 守卫契约（fails-closed 冲突裁定）本身非原子，属 C1 第 4 步「检查-然后-行动」定向检查项的正命中。
- **复核状态**: 已复核——**保留，P2 维持**。HashMap（:30）、check-then-act（:70-76）、fails-closed 契约文（:55-59）、registeredIds 迭代（:101-105）复核实证；调用方清单逐点核实并**补一站点** `NopRefactorCli.java:70`（原清单遗漏，同为 discoverDefaults 单线程形态，不改变结论）——库内调用方全部安全形态成立，缺陷活在嵌入契约面。2026-09 G10-01-02 同站点三态裁定归 C6 复核，本复核仅核 live 证据。

### [C1-105] RuleResultCache 固定名 .tmp + 跨进程 load-modify-save 无互斥：共享 --cache 文件的并发 run 相互踩踏（虚假失败或整件丢更新）

- **文件**: `nop-tools/nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/cli/RuleResultCache.java:197-208（save 固定 .tmp 名 + ATOMIC_MOVE）,73-111（load）,175-191（put）`
- **证据片段**:
  ```java
  // save():197-208 —— temp 文件名 = 目标文件名 + ".tmp"，无进程/时间唯一化
  public void save() {
      try {
          Path temp = file.resolveSibling(file.getFileName() + ".tmp");
          Files.writeString(temp, JsonTool.serialize(artifact, false),
                  StandardCharsets.UTF_8);
          Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING,
                  StandardCopyOption.ATOMIC_MOVE);
  ```
- **严重程度**: P2
- **并发场景**: 两个独立进程（两个 CI job、Maven `-T 1C` 下两个模块的 CheckMojo、开发者本地 lint 与 CI 并行）指向同一 `--cache` 文件；单进程内 CheckRunner 串行无竞争。
- **触发时序**: 进程 A `writeString(tmp)` 写入 A 的全量工件 → 进程 B `writeString(tmp)` **覆盖同一 tmp** → B `move(tmp→file)` 成功（tmp 消失）→ A `move(tmp→file)` → `NoSuchFileException` → NopLintException → **A 的整个 lint run 以退出码 2 失败**（fail-closed 变成虚假失败/CI 抖动源）。次生时序：A、B 都完成 move → 整件 last-writer-wins，B 丢失 A 写入的全部缓存条目（cache 命中率静默回退，无报错）。`ATOMIC_MOVE` 只保证单次替换的可见原子性，不提供跨进程互斥。
- **现状**: 文件级读-改-写复合操作（load 整件 → 内存 put × N → save 整件）无任何跨进程排除；平台已有 `io.nop.commons.concurrent.lock.LocalFileLock` 未用。
- **风险**: 共享缓存文件是 CI 加速手段，其并发失效模式恰在并行度提升时触发：轻则 run 失败（虚假红灯），重则缓存静默失效；两者都随 CI 并行度线性放大。
- **建议**: temp 名唯一化（`Files.createTempFile(parent, fileName, ".tmp")`，与 EditPlanApplier.atomicWrite:161-165 的既有正确形态对齐——该处已用 createTempFile，本处是同族漏改）+ 以 `LocalFileLock` 圈住 load-modify-save 临界区，或在文档明示 --cache 文件 single-writer 契约并在 save 冲突时降级为「跳过保存」而非 run 失败。
- **失效面枚举**: 批内文件原子写全清单——① RuleResultCache.save（固定名，缺陷本体）；② EditPlanApplier.atomicWrite:161（createTempFile 唯一名，OK，但同样无跨进程文件锁——rewrite 写面对同一目标文件的并发 apply 依赖调用方互斥，登记为同族观察项）；③ BaselineFile.writeTo（同族——本批读面未逐行核对其 temp 形态，复核阶段应补查）。JVM 内写入面：CheckRunner 单线程串行，无 JVM 内竞争。
- **信心水平**: 确定（交错与后果代码实证）
- **误报排除**: 不是「原子写已足够」——ATOMIC_MOVE 解决的是单写者断电半写问题（其 javadoc 声明的目标），不解决双写者互斥；也不是「有界声明存在的缓存」校准——问题在共享写路径而非容量。
- **复核状态**: 已复核——**保留，P2 维持**。固定 `.tmp` 名（RuleResultCache.java:199）、整件 load(:73-111)/put(:175-191)/save(:197-208) RMW 复核实证。**失效面③补查完成（原留复核项）**：`BaselineFile.writeTo`（suppress/BaselineFile.java:187-194）为直接 `Files.writeString` 覆写、**无 temp 文件**——非踩踏站点，撕裂写问题归 C6-102 族（C6 载体表 row 5 已登记）。家族终态 = ① 本体（固定 .tmp）② EditPlanApplier.atomicWrite:159-175（createTempFile 唯一名，OK）③ BaselineFile（无 temp，C6-102 族）。与 C6-102 为同站点双登记，Phase 6 归并。

## 维度集外强信号线索（CL）

### [CL-101] 全局 wrapper 的平台级 passthrough 热路径开销（→C9）
首个 lint run 之后，全 JVM 所有 XLang 可执行节点求值（与 lint 无关的 XPL 模板、xbiz、ORM 表达式）永久多一跳 `LintDeadlineExecutor.execute` → `rt.getScope().getValue("__lintDeadlineNanos")`（LintDeadlineExecutor.java:79-88）+ 一层委托分发；且无卸载路径使该开销不可逆。每节点 scope 查找的代价需 C9 量化（调用频率 = 全平台每可执行节点 × 单次 scope 查找），并评估 FastPath（如 scope 级 marker 或 install 作用域化）。

### [CL-102] lint 全链路单线程串行的吞吐缺口（→C9/C4）
CheckRunner.run 逐文件串行循环（CheckRunner.java:217-243），RuleSetLoader/编译/匹配/抑制全链无并行；LintEngine.java:220-221 注释自证「historical JFR showed lexing/parse at 13%+」。文件级并行（每文件独立 LintResult 聚合 + RuleResultCache 按 #C1-105 并发安全化 + NopLintBizModel 请求面天然并发已存在）是 C9 立项候选；基线 C4 面仅 NodeTscBridge 一处线程创建，无池治理负担。

## 批 B 小结

载体盘点 20 项：16 项判定 OK（共享编译产物/单例注入/静态表/ThreadLocal 平衡/原型 digest 均干净，其中 NopLintBizModel 的 volatile-DCL 与 Runtime 不可变共享是本批并发面做得最规范的两处）；发现 4 项（P1×2：C1-101 全局执行器槽丢更新、C1-102 tsc 桥跨代 EOF 毒化；P2×3：C1-103 可用性探针无锁读、C1-104 注册表守卫非原子、C1-105 缓存工件跨进程踩踏）；CL 线索 2 条（均指向 C9）。盲审纪律：未读取 ai-dev/audits|plans|bugs|lessons|backlog 下任何既有文件内容；本批产出以追加语义写入，编号段 C1-101..C1-105 为批 B 保留段。


---

# C1 深挖第 2 轮

> **触发**: 批 B 新增 P1（C1-101 全局执行器槽 / C1-102 tsc 桥跨代 EOF 毒化）按 F3 同族放大纪律必派第 2 轮。
> **范围**: 四模块全单元（nop-treesitter / nop-jq / nop-lint 三层九源码根 / nop-refactor）+ 指定扩大面 5 项（同族放大 ×2、干净面抽查 ×1、treesitter query 角落、jq 内置函数表）。
> **盲审纪律保持**: 只读本审计文件自身与两份方法论文件；未读其他维度文件、ai-dev/plans/、ai-dev/bugs/、ai-dev/lessons/、ai-dev/backlog/。live code @ 0d131797dd，基线同前轮。
> **复核状态**: 全部未复核。

## R2-0. P1 同族放大扫描（指定扩大面 1、2）

### A. 全局静态槽 / 全局 executor 替换形态（C1-101 同族）

扫描方法（可复现口径）：`grep -rn -E "registerInstance|registerGlobal|registerDefault|registerEvaluator|registerHandler|registerTagCompiler|registerExecutableExprCompiler|setSuspended|setClock|RandomImpl|setConfiguration|StaticJavaParser"` × 审计单元九源码根（nop-lint-{core,java,js,graphql,nop,maven-plugin}、nop-refactor、nop-treesitter、nop-jq 的 src/main），排除 import/注释；平台侧反向盘点 nop-kernel 全部静态可变槽（`private static <T>` 非 final 字段）与其 register/set 写者。

结果：

- 审计单元内对平台全局槽的**请求路径写者唯一**：`LintDeadlineExecutor.install()` :75（C1-101 本体，无新增同族站点）。
- `XScriptCompiler` 的 `registerScopeVarDefinition` ×8（:138-188）为 per-IEvalScope 绑定（每 run 的编译 scope），作用域封闭，非全局槽，不立案。
- 无 StaticJavaParser 类静态配置使用；无 registerInstance/registerDefault/registerEvaluator/registerHandler/registerTagCompiler/registerExecutableExprCompiler/setClock/RandomImpl 调用点。
- 平台侧家族背景补全（不立案，扩全批 B 失效面枚举 ④）：`EvalExprProvider` 其余 3 槽（`_exprParser` :21、`deltaExtendsGenerator` :23、`featurePredicateEvaluator` :25，均无 volatile）写者为 XLangCoreInitializer 启动期一次性；另有约 17 个平台单例槽（ModuleManager:43、BeanTool:37、JsonWhitelistChecker:14、VirtualFileSystem:18、VfsConfigLoader:33、DictProvider:35、XPathProvider:36、JPath:31（volatile）、JsonTool:40、I18nMessageManager:44、ContextProvider:37、XSchemaToJsonSchema:28、RegisterModelDiscovery:60、ResourceComponentManager:77、ResourceTenantManager:52、OutputModelHandlers:28、InternalTagCompilers:53，另有 XLangExprParser s_override/s_default（volatile）、CoreMetrics.s_clock（volatile）、MathHelper.s_randomImpl、FileHelper._currentDir）——全部为启动装配/平台初始化期写者，审计单元内 0 调用点，无请求路径交叠。

**清扫终态（F3）**：C1-101 即本缺陷族在审计单元内的全部修复面，无兄弟站点。

### B. 跨进程桥纪元/代际管理站点（C1-102 同族）

扫描方法：`grep -rln "ProcessBuilder|Runtime.getRuntime().exec"` 四模块 src/main（排除 target/test）→ 唯一命中 `NodeTscBridge.java`；`grep destroyForcibly|waitFor|pid` 复核生命周期调用面。

结果：唯一跨进程桥 = NodeTscBridge（C1-102 本体，重启代际管理的失效面以 C1-102 失效面枚举 ①-⑤ 为全集）。`NopLintLanguageServer` 为 lsp4j 线程内传输（读/写线程 + 单线程顺序 dispatch），非进程桥，无纪元管理面。

**清扫终态（F3）**：C1-102 即本缺陷族全部修复面，无兄弟站点。

## R2-1. lint「干净面备案」抽查重演（指定扩大面 3；3 项，覆盖批 B 载体表 #2 / #19+#6 / #18+#12）

| 抽查项 | 批 B 备案 | 重演证据（live code） | 结论 |
|---|---|---|---|
| `LintDeadlineExecutor.EVAL_DEPTH`（#2） | try/finally 平衡，池化线程无泄漏 | `LintDeadlineExecutor.java:92-107`：`depth[0]++`（:98）后唯一 try 体 `expr.execute(this, rt)`，`finally depth[0]--`（:106）；超时抛出（:89-91）与深度超限（:93-97）均在增量前或 finally 覆盖内；withInitial 每 thread 新 int[1] | **维持 OK** |
| `XScriptEngine.declTypeResolver`（#19，支撑 #6 共享执行面） | 实例 final、单方法无状态 | `XScriptEngine.java:61`（`private final DeclTypeResolver`）；`DeclTypeResolver.java:25-53` 全文：唯一实例方法 `resolveDeclType`，0 实例字段，javadoc 自证 "Stateless and allocation-free per query"；跨匹配/跨线程共享仅方法分派 | **维持 OK** |
| `RuleResultCache.SHA256_PROTOTYPE` + `artifact`（#18/#12） | clone-per-call；LinkedHashMap 单线程 | `RuleResultCache.java:41-49`（原型）、`:246-252`（`prototypedDigest()` 每次 `(MessageDigest) SHA256_PROTOTYPE.clone()`，javadoc 明示 stateful/not thread-safe）；`artifact` 为实例字段（:60），`load()` 每次新建实例（:73-111），put/get 面由 CheckRunner 串行约束；跨进程面已由 C1-105 立案 | **维持 OK** |

补查（批 B #18 未单列的 `BaselineEngine`，suppress 包）：`BaselineEngine.java:41`（`SHA256_PROTOTYPE` static final）+ `:56` prototypedDigest 同构 clone 纪律；状态载体为 final record 字段（:178-182）；无 static 非 final、无共享可变容器。OK。

## R2-2. treesitter 语言对象 / TSQuery 编译缓存面（指定扩大面 4）

- `TSQueryParser.parse`（:38）、`TSQuery.compile`（:40/:47）均为 static 方法、0 静态字段；`TSQueryCompiler.captureIds`（TSQuery.java:149）为 compiler **实例**字段（每编译新建）；`TSQueryCursor` 无静态可变面（grep static/Map/cache 仅命中方法声明）。
- 模块内 **无 query 编译缓存容器**：grep `new Query(|TSQuery(` 排除 query 包后 0 命中——query 按需编译，共享面收敛于规则集加载期 final 结构（归批 B #6 面），无跨线程共享可变编译态。
- 语言对象角落补扫：`LanguageProvider.customProviders` DCL、compat static-init LANGUAGE、Language 四共享态均已在批 A 载体表 #5/#6/#7/#8/#9/#10 核毕；本轮无新增写点。

**无新发现。**

## R2-3. jq 内置函数表注册与可变面（指定扩大面 5）

- `JqBuiltins`（1,900 行）为纯 switch 分发器：grep `static [A-Za-z]` 排除 static final 后仅 static **方法**；全部 `map.put` 命中（:787/:799/:836/:910-940）为函数体局部 LinkedHashMap。**无静态可变注册表、无 register/addBuiltin 运行时注册路径**——内置函数集在类初始化即封闭。
- `JqFormatStrings` / `JqTimeFunctions` / `JqRegexSupport` / `JqOrdering` 同口径复查：0 static 非 final 字段。

**批 A「不可变注册表」结论在专项复查下保持。无新发现。**

## R2-4. 维度集外强信号线索（CL）

### [nop-tools-parse-CL-201] xscript 资源护栏哨兵可被脚本 catch-all 吞并：吞哨兵后诊断 cap 失去截断力、deadline 失去终止力（→C5/C7/C10）

- **文件**: `nop-tools/nop-lint/nop-lint-core/src/main/java/io/nop/lint/core/xscript/XScriptEngine.java:282-287`（accept 先 add 后抛哨兵）、`:178-192`（executeMatch catch Exception 按 isCapped 吞）；`XScriptTimeoutException.java:17`；`nop-kernel/nop-api-core/src/main/java/io/nop/api/core/exceptions/NopException.java:33`
- **证据片段**:
  ```java
  // XScriptEngine.java:282-287 —— 每次 accept 先 add 再抛哨兵
  reported.add(new Diagnostic(ruleId, diagnosticSeverity, message, target.range()));
  if (reported.size() >= maxDiagnosticsPerMatch) {
      capped = true;
      throw new DiagnosticCapReached();      // extends RuntimeException
  }
  // XScriptTimeoutException.java:17 —— 超时哨兵同为 RuntimeException 族
  public class XScriptTimeoutException extends NopLintException {
  // NopException.java:33 —— NopLintException → NopException → RuntimeException
  public class NopException extends RuntimeException implements IException, ... {
  ```
- **形态**: C5 内存有界性 / C7 长时运行稳定性 / C10 测试保护力（维度集外线索，非 C1 本体）
- **说明**: 两个资源护栏（per-match 诊断 cap 100、per-match deadline）的终止哨兵都是 RuntimeException 子类。xscript 是 XLang script 方言，脚本作者写 `try { ... } catch(e) {}`（catch-all）形态时：① 吞 `DiagnosticCapReached` 后继续循环 report——accept 每次仍先 `reported.add` 一条再抛哨兵，`reported` 列表无界增长（cap 只约束"正常终止时的诊断数"，不约束"哨兵被吞后续跑"的累计），`MatchOutcome.diagnostics` 随之无界；② 吞 `XScriptTimeoutException` 后 runaway 循环——deadline 检查在每个 executor entry 抛超时，全吞则每个 entry 检查失效，回到无界执行（与 C1-101 护栏失效同后果，但这里是脚本层可自致）。护栏"不可绕过性"不成立：JVM 类型语义实证（继承链 RuntimeException），唯一待验证项是 xscript 编译层是否放行 try-catch 语句与 catch-all 形态（XLang 语句面含 try-catch，白名单只约束 scope 变量，未见语句级白名单——留 C10/C7 复核裁定）。
- **建议方向**: 哨兵改用非 Exception 通道（Error 子类或专用 Throwable，eval 机器透传不封装；或 scope 级 abort flag 由 executor 在 entry 检查而非依赖异常传播）；cap 改为「达上限后 accept 直接丢弃不再 add」的双保险。
- **信心水平**: 很可能（类型语义与 add-then-throw 顺序实证；xscript catch-all 可达性待编译层验证）
- **复核状态**: 已复核——**确认**。XScriptEngine:282-287（add-then-throw）、:178-192（capped 时 catch Exception 吞）、XScriptTimeoutException.java:17 → NopLintException → NopException.java:33 extends RuntimeException 全链复核实证。**补强佐证**：jq 同类护栏特意采用不可捕获通道——`JqCancellationException`（JqEnvironment.checkOutputLimit javadoc 明言 "never a JqRuntimeException, so try/recurse cannot swallow a cancellation"），xscript 缺同一设计。**编号冲突**：与 C6 批 A CL-201（treesitter GraphQL 输入闸）撞号，Phase 6 归并时重编（建议顺延 CL-206）。

## R2-5. 小结与终止信号

指定扩大面 5 项全部闭合：同族放大 A/B 各为**单站点**（C1-101/C1-102 即全部修复面，无兄弟漏点）；干净面 3 项抽查重演**维持原判**（EVAL_DEPTH 平衡、DeclTypeResolver 无状态、SHA256 clone 纪律 + artifact 单线程面）；treesitter query 面、jq 内置函数表均无共享可变状态、无编译缓存容器、无运行时注册路径。

**本轮终止：无新 C1 立案**（C1-R2-001 起编号段未启用）；新增维度集外 CL 线索 1 条（CL-201，指向 C5/C7/C10 复核）。C1 维度累计：立案 6（P1×2 / P2×4：C1-001、C1-101~105）+ CL 4 条（CL-001、CL-101、CL-102、CL-201）。

---

# C1 维度复核结论（WI 6.2 独立复核，2026-10-22）

> 复核人：C1 维度独立复核子代理（与初审、批 A/B、R2 不同 task）。范围：C1-001/101/102/103/104/105 六案逐条 + 批次覆盖核验 + CL-001/101/102/201 裁定。全部证据按 live code @ 0d131797dd 重读源码 file:line 实证。G10-01-02（LanguageRegistry 三态）不在本复核职责内，归 C6 复核统一裁定；本复核仅核 C1-104 live 证据。

## 逐条判定

| 编号 | 判定 | 理由（复核实证锚点） |
|---|---|---|
| C1-001 | **保留，P2 维持** | get(:46)→load(:50)→put(:52) 复合窗口实证（DefaultTreeSitterLanguageProvider.java:42-55）；身份后果链实证（TSParser.java:127-129 `oldTree.language() != language` fail-fast）；共享 singleton bean 实证（app-treesitter.beans.xml:7）。冷路径（每语法每进程首触一次）+ fail-fast 无数据损坏，P2 校准成立；修复面一行 computeIfAbsent 不变。 |
| C1-101 | **保留，P1 维持** | 四要素全实证：① 槽无 volatile/无同步（EvalExprProvider.java:19、:35-37）；② install 跨 monitor 非原子——`static synchronized` 只锁 LintDeadlineExecutor.class，`registerGlobalExecutor` 不在该 monitor（LintDeadlineExecutor.java:70-76）；③ 第二写者并存（XLangDebuggerInitializer.java:77 attach、:156 detach 无条件重置 Default——剥离已装 wrapper）；④ 护栏失效传导链：每 lint 调用 install（LintEngine.java:269/:311 实测行号吻合），被覆盖后每节点重读全局槽（BetweenOpExecutable.java:76；另 XLang.java:53、EvalBackendRouter.java:101,117,223）取得无 LDE 的链，deadline 与 MAX_EVAL_DEPTH 静默失效，scope 中的 deadline 值无任何其他读点（全仓 grep 实证）。无 uninstall 实证（main 面 wrapper 永久驻留，仅测试还原）。触发需第二写者与 lint 并发（调试器 attach/detach 的 dev/长驻宿主场景），但后果为安全护栏静默失效，P1 成立。merge 注记：与 C6 批 B CL-401 同站点。 |
| C1-102 | **缺陷本体保留（P1 机制成立），登记归属改判 → C6-101 主登记** | 机制实证：spawn:195-197（clear 在先、无纪元）、drainPeerOutput:240-242（finally 无条件 offer(EOF)）、握手误判点 :213-216、request 误判点 :159-163。三方裁定见下节。 |
| C1-103 | **保留，P2 维持** | `bridge` 非 volatile（TscTypeResolver.java:29）、isAvailable 无锁读（:52-59）vs 锁内写（:107-118）/锁内 close（:99-105）实证；下游 fail-closed（TypeResolutionException → degrade 计数）核实。现状标注补强：「在编未接线」实证——main 源码 0 个 `new TscTypeResolver` 调用方（grep 实证），live 影响限于嵌入式接线面。 |
| C1-104 | **保留，P2 维持** | HashMap（:30）、check-then-act（:70-76）、fails-closed 契约文（:55-59）、registeredIds 迭代（:101-105）实证；调用方逐点核实并补一站点 `NopRefactorCli.java:70`（初审清单遗漏，同为 discoverDefaults 单线程形态，不改变结论）——库内调用方全部「新建 registry → 顺序注册 → 安全发布」成立，缺陷活在嵌入契约面。 |
| C1-105 | **保留，P2 维持** | 固定 `.tmp` 名（RuleResultCache.java:199）、整件 load(:73-111)/put(:175-191)/save(:197-208) RMW 实证；ATOMIC_MOVE 不提供跨进程互斥的语义裁定成立。失效面③补查闭环（见下）。merge 注记：与 C6-102 同站点双登记，Phase 6 归并。 |

## C1-102 三方同站点裁定（C1-102 / C6-101 / CS-001）

**裁定：同一缺陷，非不同缺陷层。** 三方共享唯一根因——重启路径对上一代 reader 线程与 pipe 残留流量无栅栏/纪元（`drainPeerOutput` 无代际捕获 + `spawn` 的 `clear()` 与旧 reader 退出无时序约束 + EOF 哨兵无归属）。三处登记的全部变体（stale EOF 过握手、stale EOF 烧预算、stale 数据帧过 validateReady 杀健康进程、尾行 id 失配）都是该根因的后果枚举；修复方案（代际捕获比对 / join 旧 reader）三方同源，任一方落地即全闭合。

**主登记建议：C6-101。** 理由：(1) 后果枚举最全——唯一覆盖 stale 数据帧分支 A（validateReady 失败 → catch:224 杀死健康新进程 + EXHAUSTED）与孤儿进程面（握手 EOF 分支不 destroyProcess，挂 C6-103）；(2) 其修复建议（一行级栅栏）同时封死全部变体；(3) C6-103/C6-104 已挂在 C6-101 失效面清单下，以它为主登记保持失效面锚完整性。C1-102、CS-001 转同站点引用登记：C1-102 独有侧写「失败原因误记/诊断误导（排查误导）」并入 C6-101 验收标准；CS-001 的 variant 2（尾行 correlation id 失配）即 C6-101 分支 A 的子情形，同改引用。**CS-R2-001（消费侧 error 帧无校验/无回收）为不同层守卫缺口，维持独立立案**（消费侧校验与生产侧代际隔离互补，二者均必要）。

## 批次覆盖核验

- **单元规模实测**（`find -path "*/main/*" -name "*.java" | wc -l`）：nop-treesitter 92 / nop-jq 72 / nop-lint 160 / nop-refactor 33——与批 A/批 B 头部口径逐一吻合，四源码根全覆盖；R2 九源码根（nop-lint-{core,java,js,graphql,nop,maven-plugin} + nop-refactor + nop-treesitter + nop-jq）清扫口径成立。
- **批 A 痕迹抽核**：载体表 #4（cache:38）/ #5（customProviders:39 volatile + DCL :101-119）/ #10（compat static final）/ `BUILTIN_BLOBS` 不可变（:29-36）复核通过；「两模块 static 非 final 可变字段 = 0」字段级口径重演通过（treesitter/jq/refactor 0 命中）。
- **批 B 痕迹抽核**：载体表 #1/#4/#10/#11/#12 关键行号全复核；「static 非 final 字段 = 0」重演通过（grep 命中均为 static 方法，与初审宽口径勘误说明一致）；install 调用点 :269/:311、XLangDebuggerInitializer :77/:156、l2Ready :395-397 行号全部吻合。
- **R2 终态认可**：全局槽写者唯一 = install 本体、进程桥唯一 = NodeTscBridge——与本次独立 grep 反向盘点一致。
- **C1-105 失效面③补查（初审留复核项）**：`BaselineFile.writeTo`（nop-lint-core/.../suppress/BaselineFile.java:187-194）为直接 `Files.writeString` 覆写、无 temp 文件——非跨进程踩踏站点，撕裂写归 C6-102 族（C6 载体表 row 5 已登记）。C1-105 家族终态 = ① 本体（固定 .tmp）② EditPlanApplier.atomicWrite:159-175（createTempFile 唯一名，OK）③ BaselineFile（无 temp，C6-102 族）。
- **C1-104 调用方清单勘误**：补 `NopRefactorCli.java:70`（原六站点清单遗漏；同形态，不改变结论）。

## CL 裁定

- **CL-001 确认**（→C5/C7）：JqEnvironment:124-132（:128 `++outputCount > MAX_OUTPUT_COUNT`，每环境独立计数）、fork :183-187 快照传递、JqExecutor:764 `fnDef.definitionEnv().fork()`、上限常量 :16（MAX_CALL_DEPTH=2000）/:18（MAX_OUTPUT_COUNT=1M）、repeatString :966-971 按追加计数全部实证。嵌套用户函数按 fork 线各自扩 1M 预算、增量不回传的语义推导成立。
- **CL-101 确认**（→C9）：LintDeadlineExecutor.execute:79-88 每节点读 scope + 无 uninstall 使开销永久；与 C6 批 B CL-401 同站点，Phase 6 归并。
- **CL-102 确认**（→C9/C4）：CheckRunner.java:217-243 逐文件串行 for 循环实证；LintEngine.java:220「historical JFR showed lexing/parse at 13%+」注释实证。
- **CL-201 确认**（→C5/C7/C10）：XScriptEngine:282-287（accept 先 add 后抛哨兵）、:178-192（capped 时 catch Exception 吞）、XScriptTimeoutException.java:17 extends NopLintException、NopException.java:33 extends RuntimeException——哨兵全在 Exception 族、脚本 catch-all 可及的语义实证。补强佐证：jq 同类护栏特意采用不可捕获的 `JqCancellationException`（JqEnvironment.checkOutputLimit javadoc："never a JqRuntimeException, so try/recurse cannot swallow"）——xscript 缺同一设计，修复方向（非 Exception 通道）与 jq 先例一致。**编号冲突**：与 C6 批 A CL-201（treesitter GraphQL 输入闸）撞号，Phase 6 重编（建议顺延 CL-206）。

## G10 边界声明

2026-09 G10-01-02（LanguageRegistry 裸 HashMap 已发布读）与本轮 C1-104 同站点；三态裁定（并存/归并/改判）按分工归 C6 复核统一处理，本复核仅完成 C1-104 的 live 证据核验（见逐条判定行），未裁三态。

## 最终保留项

- **C1 立案保留 6/6，驳回 0，降级 0**：P2×4（C1-001、C1-103、C1-104、C1-105）；P1×1 本体（C1-101）；P1×1 缺陷本体保留但登记归属改判（C1-102 → 主登记 C6-101，本条转同站点引用登记）。
- **CL 确认 4/4**：CL-001（→C5/C7）、CL-101（→C9，与 CL-401 归并）、CL-102（→C9/C4）、CL-201（→C5/C7/C10，撞号重编）。
- **Phase 6 归并待办**（本复核认定的跨批重复族）：① NodeTscBridge 三方（C1-102/C6-101/CS-001 → 合并至 C6-101 主登记）；② RuleResultCache.save 双登记（C1-105/C6-102）；③ LintDeadlineExecutor 站点（C1-101 与 C6 CL-401）；④ CL-201 撞号重编（C1 R2 的 xscript 哨兵线索 → CL-206）。
- **现状标注汇总**：C1-103「在编未接线」（main 无构造调用方）；C1-104「库内调用方全安全形态，缺陷在嵌入契约面」+ 调用方清单补 NopRefactorCli.java:70；C1-105 失效面③补查闭环（BaselineFile 无 temp，归 C6-102 族）。
