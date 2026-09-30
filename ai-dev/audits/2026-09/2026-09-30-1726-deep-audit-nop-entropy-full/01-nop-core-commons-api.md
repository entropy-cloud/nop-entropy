# G1: nop-core/commons/api-core 深度审计（首轮）

- **审计日期**: 2026-09-30
- **审计子代理**: 首轮初审（维度 09 / 14 / 15 / 16）
- **方法论**: `ai-dev/skills/deep-audit-prompts.md`（共享前缀 + 各维度正文 + 附录 A 格式）
- **复核状态**: 全部条目未复核（首轮初审，待独立复核）

## 审计范围

### 模块清单

- `nop-kernel/nop-api-core`（322 个 main Java 文件 / 33 个 test 文件）
- `nop-kernel/nop-commons`（403 个 main Java 文件 / 41 个 test 文件）
- `nop-kernel/nop-core`（757 个 main Java 文件 / 66 个 test 文件）

排除：`target/`、`_` 前缀生成文件、`.m2-repo-2275/`、`_tmp/`、test 代码中的机械基线命中（按共享前缀降级处理）。

### 机械基线核实（区分 main/test 后）

| 基线 | 主 agent 口径 | 本轮核实（main / 三模块合计） | 处理 |
|------|--------------|------------------------------|------|
| 裸异常 throw（RTE/IAE/ISE） | nop-kernel 315 处 | main 190 处（core 64 / commons 96 / api-core 30），test 9 处 | 见下方判级校准 |
| `throw new RuntimeException` | — | main 仅 1 处真实抛出（`ByteHelper.java:360`，另 1 处为注释）；test 0 | [G1-09-03] |
| `nop.err.*` 风格字符串嵌入裸 IAE/ISE | — | main 15 处 / 12 文件 | [G1-09-01] |
| System.out/err/printStackTrace | nop-kernel 222 处 | main 8 处（5 处已注释），真实 3 处 | [G1-09-02] |
| `@Inject private` | 全仓 0 处 | 复核为 0 | 无发现 |

**裸异常判级校准**（依 error-handling.md 两档策略 + 任务书口径）：
- `Guard.java`（api-core，17 处 IAE）是平台专用断言工具类（等价 Guava Preconditions），按设计抛 IAE——校准为非问题。
- `JavaGenericTypeHelper.java`（12 处）为 Retrofit/Gson `Types` 的移植代码；`CollectTextJsonHandler`/`BuildObjectJsonHandler`（15 处 ISE，"Nesting problem"/"Dangling name"）为 json.org/JsonWriter 移植的内部一致性断言——第三方移植风格，不单独报告。
- 其余约 140 处为低层工具类（Bytes/NetHelper/MathHelper/ByteQueue/IntHashMap/AntPathMatcher 等）的参数断言式 IAE/ISE，消息为英文、语义自足，符合"模块内部实现"档位——不报告。
- 中文字符串消息：三模块 main 代码 0 处命中（`new XxxException("` + Han 字符扫描）。
- `new NopException("` 字符串构造器：三模块 main 代码 0 处——内核模块整体遵循 ErrorCode 模式。

### 各维度检查过的关键文件/模式

**维度 09（错误处理与错误码）**：
- 逐一核实 main 代码全部 190 处裸抛的文件分布与上下文（分布统计 + 抽样深读：Guard、QueryBean.addJoin、JavaGenericTypeHelper、RecordBeanModelBuilder、XNode.singleNode、JSON handlers、ExecutionContextImpl、TaskExecutionGraph、ResourceComponentManager、MathHelper、StdDataType、EvalFrame、TemplateGenPath、TreeDataHelper、ResourceVersionHelper、DeltaJsonLoader、I18nTextResolver、GenericFunctionTypeImpl）。
- 吞异常检查：comment-only catch 块扫描（MavenDirHelper、TextScanner、ByteQueue、DefaultTaskExecutionQueue、ClassPathScanner、ClassModel、Seq.java——均为 probe/fallback/带 cause 包装模式，校准为合理）；`LOG.debug` 级吞异常扫描（FileHelper.moveFile、TemplateFileGenerator.normalizeText、LifeCycleSupport.start 等，逐一读上下文判定）。
- 资源关闭规范：手写 `try{x.close()}catch` 模式扫描（main 0 处命中；IoHelper.safeClose 使用于 27 个文件）。
- ErrorCode 定义完整性：核对 `nop.err.execution-already-completed` 等 15 个伪错误码在 CoreErrors/CommonErrors/ApiErrors 与 i18n yaml 中均无定义。

**维度 14（异步与事务模式）**：
- 深读：`ContextProvider`/`BaseContextProvider`/`BaseContext`/`ContextTaskQueue`（api-core 上下文与任务队列全链路）、`ExecutionContextImpl`（complete/onBeforeComplete 竞态）、`ResourceTenantManager`、`ResourceLoadingCache`、`ResourceCacheEntry`（模型缓存加载同步）、`TaskExecutionGraph` + `Dag`/`DagAnalyzer`（DAG 调度与环处理）、`HighWatermarkSemaphore`、`LocalCache`/`MapCache`/`JavaxCache`/`ICache`/`GlobalCacheRegistry`、`LifeCycleSupport`。
- ThreadLocal 泄漏面：BaseContextProvider/HashHelper/ResourceTenantManager 三处 ThreadLocal 均有 restore/remove 路径。
- 资源泄漏面：`TestCsvRecordIoLeak` 回归测试存在；IoHelper.safeClose 广泛使用；未发现手写 close-catch。
- 检查过且判定健康（无发现）：ContextTaskQueue（endProcessIfIdle 有明确的二次确认防窗口注释）、ResourceCacheEntry（volatile + double-check + 失败清缓存）、HighWatermarkSemaphore（高低水位滞回设计自洽）、BaseContext（close 清理 thread-local）。

**维度 15（类型安全与泛型使用）**：
- `@SuppressWarnings("unchecked")` main 35 处，逐文件归类（CloneHelper/FutureHelper/FreezeHelper 的 `(T)` 透传、ArrayHelper 的 `Array.newInstance` 惯用法、CollectionHelper 的 `(Set<T>) c` 收窄——均为泛型管道惯用法，非缺陷）。
- raw type 扫描：public API 签名中的裸 `Class` 参数命中 `SourceLocation.fromClass(Class)`、`ConvertHelper.getDefault(Class)`/`getPrimitiveClass(Class)`（[G1-15-01]）；`GlobalCacheRegistry.removeCacheEntry` 中一处多余 raw cast（并入 G1-15-01 述及）。
- `(T[])/(E[])` 数组泛型转换：均为 `Array.newInstance` + componentType 惯用法，运行时类型具化正确——无发现。
- ICache 接口契约精度问题归入维度 14（原子性），见 [G1-14-02]。

**维度 16（测试覆盖与质量）**：
- 全量清点三模块测试文件清单；核对关键并发/核心类有无直接测试（有：TestContextTaskQueue、TestHighWatermarkSemaphore、TestIntHashMap、TestGlobalCacheRegistry、TestMapCache、TestExecutionContextImpl、TestResourceCacheEntry、TestResourceLoadingCache、TestXNode*、TestJsonTool*；无：LongHashMap、ByteQueue、CycleSynchronizer、LocalCache）。
- 抽读 TestExecutionContextImpl（并发回归测试质量良好：latch + executor + 终态断言）、TestTaskExecutionGraph（仅 happy path，[G1-16-01]）。

## 发现

### [G1-14-01] TaskExecutionGraph 幻影依赖节点被静默跳过 + 错误路径可致聚合 future 永不完成

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/execution/TaskExecutionGraph.java:69-83, 118-136, 183-196`
- **证据片段**:
  ```java
  public TaskExecutionGraph addDepends(String taskName, Collection<String> depends) {
      checkAllowChange();
      if (depends == null || depends.isEmpty())
          return this;
      for (String depend : depends) {
          dag.addNextNode(Dag.DEFAULT_ROOT_NAME, depend);   // 幻影节点直接挂到根，可达性校验必过
          dag.addNextNode(depend, taskName);
      }
      return this;
  }
  ```
  ```java
  // executeAsync 内：
  CompletableFuture<Void> future = waitPrevTasks(futures, taskName);
  if (future != null) {
      future.whenComplete((ret, err) -> {
          if (err != null) {
              futures.get(taskName).completeExceptionally(err);
          } else {
              runTask(executor, cancelToken, futures, taskName);   // 在 whenComplete 回调内执行
          }
      });
  } else {
      noDepends.add(taskName);   // futures.get(幻影名)==null 与"无依赖"共用同一返回值
  }
  ```
  ```java
  private void runTask(Executor executor, ICancelToken cancelToken, Map<String, CompletableFuture<Void>> futures, String taskName) {
      CompletableFuture<Void> future = futures.get(taskName);
      if (runPermits != null) {
          try {
              runPermits.acquire();
          } catch (InterruptedException e) {
              Thread.interrupted();
              throw NopException.adapt(e);   // 从 whenComplete 内抛出会被 CompletableFuture 丢弃
          }
          ...
      }
      executor.execute(() -> { ... });       // RejectedExecutionException 同样在回调内被丢弃
  ```
- **严重程度**: P2
- **现状**: 两个缺陷叠加。(a) `addDepends`/`addTaskWithDepends` 对未注册的任务名（拼写错误）不校验：`Dag.addNextNode` 会 `computeIfAbsent` 创建幻影节点并先行挂到 DEFAULT_ROOT 下（DagAnalyzer.checkStartReachable 的可达性校验因此必过）；`waitPrevTasks` 中单依赖路径 `futures.get(prevName)` 对幻影名返回 null，与"无依赖"共用同一分支，依赖被静默丢弃。(b) 依赖任务的 `runTask` 在 `whenComplete` 回调内执行：`runPermits.acquire()` 被中断时抛出的 NopException、或 `executor.execute` 被拒绝（线程池关闭）抛出的 RejectedExecutionException，均会被 CompletableFuture 的 whenComplete 语义丢弃（异常只进入被丢弃的依赖 stage），该任务的 future 永不完成，`CompletableFuture.allOf(endFutures)` 静默挂起。
- **风险**: 依赖顺序是 DAG 执行器的核心契约：调用方一个拼写错误的依赖名即导致"被依赖任务"不等待先行任务直接并发执行（本类被 `BeanContainerImpl.java:542` 用于 IoC 容器启动的 bean 初始化排序，错误顺序可产生难排查的初始化竞态）；错误路径上的静默挂起使关闭/中断场景下 `executeAsync` 的返回 future 永不完成，调用方 `syncGet` 永久阻塞而非 fail-fast。
- **建议**: (1) `addDepends`/`analyze` 时校验依赖名必须对应已注册任务（或至少在 `waitPrevTasks` 命中 `futures.get(prevName)==null` 时抛出带参数的 NopException 而非按无依赖处理）；(2) `runTask` 内将 acquire/execute 包在 try-catch，异常路径上直接 `future.completeExceptionally(e)` 再重抛；(3) 多依赖路径 `prevFutures[i] = futures.get(prevName)` 的潜在 null 会先以 NPE 暴露，也应一并校验。
- **信心水平**: 很可能（代码路径已逐行核实；触发条件分别为"调用方依赖名写错"与"线程池拒绝/中断"，均为异常但现实场景）
- **误报排除**: 不是"框架约定的容错设计"：DagAnalyzer 对环有显式记录（loopEdges）而非静默，对不可达节点有显式 NopException（ERR_GRAPH_NODES_NOT_REACHABLE）——说明该模块的设计意图是暴露配置错误，幻影依赖漏网与该意图相悖；挂起路径违反 error-handling.md 的 fail-fast 边界规则。
- **复核状态**: 未复核

### [G1-09-01] 框架核心 15 处裸 IAE/ISE 消息里嵌入 `nop.err.*` 风格伪错误码，绕过 ErrorCode/i18n 通道

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/resource/component/ResourceComponentManager.java:394-408`（代表位点）；同模式共 15 处/12 文件：`context/ExecutionContextImpl.java:86,99`、`lang/eval/EvalFrame.java:49`、`execution/TaskExecutionGraph.java:50`、`resource/tpl/TemplateGenPath.java:49`、`lang/xml/XNode.java:2061`、`lang/json/delta/DeltaJsonLoader.java:64`、`model/tree/TreeDataHelper.java:141`、`resource/component/version/ResourceVersionHelper.java:68`、`type/impl/GenericFunctionTypeImpl.java:37`、`nop-commons/util/MathHelper.java:1479`、`nop-commons/type/StdDataType.java:302`
- **证据片段**:
  ```java
  // ResourceComponentManager.java:394-399 —— 公开入口 loadContentModel
  ComponentModelConfig config = requireModelConfigByModelPath(resource.getPath());
  Pair<String, IResourceObjectLoader<Object>> pair =
          resolveModelLoader(resource.getPath(), config.getModelType());
  if (pair == null)
      throw new IllegalArgumentException("nop.err.unsupported-resource-file:" + resource.getPath());
  return pair.getRight().loadObjectFromResource(resource);
  ```
  ```java
  // ExecutionContextImpl.java:85-87
  if (isDone()) {
      throw new IllegalStateException("nop.err.execution-already-completed");
  }
  ```
- **严重程度**: P2
- **现状**: 消息字符串形如 `nop.err.unsupported-resource-file:/xxx/yyy.vue`，看起来是错误码，实际是裸 IllegalArgumentException/IllegalStateException 的消息文本。已核实这 15 个码在 `CoreErrors`/`CommonErrors`/`ApiErrors` 与全部 i18n yaml 中均无定义、无 `ErrorCode.define()`、无 `ARG_*` 参数常量——是"写了一半的错误码迁移"。
- **风险**: nop-core 是定义平台错误处理规范的框架核心模块，error-handling.md 要求框架核心使用 `NopException + ErrorCode + .param()`。这些异常到达 API 边界后被当作通用内部错误（500）处理：错误码不被 ErrorMessageManager 识别、不做 i18n、无结构化参数，前端/调用方无法按码匹配；同时冒号拼接参数（`":" + path`）无法走 `.param()` 掩码/结构化链路。半迁移形态还会误导后续开发者模仿此写法。
- **建议**: 将这 15 个位点收敛为对应模块 `*Errors` 接口中的 `ErrorCode.define(...)` + `ARG_*` 常量，抛 `new NopException(ERR_XXX).param(...)`（`unsupported-resource-file` 尤其应优先，它是用户可触发的公开路径）。若个别位点确属内部断言，也应去掉 `nop.err.` 前缀的误导性写法。
- **信心水平**: 确定（15 处逐一核实，码无任何定义/翻译条目）
- **误报排除**: 不是"参数断言式 IAE 可接受档"：断言式 IAE 的消息是自然语言（如 `"alias is empty"`），而这些位点刻意采用错误码命名格式（`nop.err.` 前缀、kebab-case），表明作者意图走 ErrorCode 通道但未完成迁移——与 QueryBean.addJoin 等"英文自然语言断言"不同类。
- **复核状态**: 未复核

### [G1-14-02] ICache 默认方法违反自身原子性契约，LocalCache.removeIfMatch 与 MapCache/JavaxCache 的 CAS 语义不一致

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/cache/ICache.java:66-76, 92-99`；`nop-kernel/nop-commons/src/main/java/io/nop/commons/cache/LocalCache.java:349-351`；对照 `MapCache.java:91-93, 199-201`、`JavaxCache.java:100-113`
- **证据片段**:
  ```java
  // ICache.java:66-76 —— javadoc 要求原子性，默认实现却是 check-then-act
  /**
   * 仅当key不存在时才插入。实现方应保证check-then-act的原子性：
   * 并发对同一key调用时，只有一个调用返回true。
   */
  default boolean putIfAbsent(K key, V value) {
      if (containsKey(key))
          return false;
      put(key, value);
      return true;
  }
  ```
  ```java
  // ICache.java:92-99 —— 引用相等 + 非原子
  default boolean remove(K key, V object) {
      V value = getIfPresent(key);
      if (value == object) {      // == 而非 equals
          remove(key);            // getIfPresent 与 remove 之间值可被替换，仍无条件删除
          return true;
      }
      return false;
  }
  ```
  ```java
  // LocalCache.java:349-351 —— 未覆写 remove(K,V)，继承上述默认实现
  @Override
  public boolean removeIfMatch(K key, V object) {
      return remove(key, object);
  }
  // 对照 MapCache.java:91-93（原子 + equals）：
  //     public boolean remove(K key, V object) { return map.remove(key, object); }
  ```
- **严重程度**: P2
- **现状**: ICache 接口对 `putIfAbsent` 的 javadoc 明确要求 check-then-act 原子性，但默认实现自身是非原子的；`remove(K,V)` 默认实现使用引用相等（`==`）且 get 与 remove 之间存在竞态窗口（窗口内被并发替换的新值会被无条件删除）。三个内置实现行为分裂：MapCache/JavaxCache 覆写为原子 + equals 语义（`map.remove(key,object)` / JCache `cache.remove(key,object)`），LocalCache 只覆写了 `putIfAbsent`（`asMap().putIfAbsent`，有注释表明修过竞态）却未覆写 `remove(K,V)`，其 `removeIfMatch` 实际是"引用比较 + 非原子无条件删"。
- **风险**: ICache 是 nop-commons 公开框架接口，被 GlobalCacheRegistry/ResourceLoadingCache 等基础设施消费。任何第三方或新增实现者不覆写默认方法即静默违约（并发下同一 key 两次 putIfAbsent 均返回 true）；同一份 `removeIfMatch` 调用在 MapCache 与 LocalCache 上语义不同（equals vs 引用相等），把 LocalCache 当 CAS 用的调用方会在并发下删除本不该删的条目。当前仓内 main 代码未发现 LocalCache.removeIfMatch 的直接调用方（MFA 的 CAS 走 INosqlService），故未构成现行故障。
- **建议**: (1) LocalCache 覆写 `remove(K,V)` 为 `cache.asMap().remove(key, object)`（Caffeine 原子 + equals）；(2) ICache 默认 `putIfAbsent` 的 javadoc 与实现对齐——要么默认实现基于 `computeIfAbsent`/`putIfAbsent` 语义重写，要么在 javadoc 中明确"默认实现非原子，线程安全实现必须覆写"；(3) 为三个实现补一个契约一致性测试（并发 putIfAbsent 只胜一次 + removeIfMatch equals 语义）。
- **信心水平**: 确定（接口与三个实现的代码均已逐行比对）
- **误报排除**: 不是"内部 API 的克制"：ICache/ICacheManagement 是注册进 GlobalCacheRegistry 的公开扩展点，javadoc 自身写明了原子性要求，属于接口契约与实现的真实漂移，而非审计者强加的风格偏好。
- **复核状态**: 未复核

### [G1-09-02] ExecCommandProcessor 缺少 --command 参数时打印到 stdout 后继续执行并以退出码 0 结束

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/command/ExecCommandProcessor.java:46-77`
- **证据片段**:
  ```java
  public boolean process(CommandLineArgs cmdArgs) {
      List<String> commands = cmdArgs.getOptionValues("command");
      if (commands.isEmpty()) {
          System.out.print("no --command argument");   // 无 return，无异常
      }
      for (String command : commands) { ... }
      if (exitAfterExec) {
          System.exit(0);                              // 以成功码退出
      }
      return true;
  }
  ```
- **严重程度**: P3
- **现状**: 调用方漏传 `--command` 时：错误信息走 `System.out.print`（非 SLF4J、无换行），不 return 也不抛异常，空 for 循环后照样走到 `System.exit(0)` / `return true`——错误调用以"成功"收场。
- **风险**: CLI/脚本无法通过退出码检测错误调用（0 被约定为成功）；错误提示混入 stdout 而非 stderr/日志，管道消费方会把 "no --command argument" 当正常输出。违反 error-handling.md"禁止把异常转成返回值/状态码伪装已处理"与"日志使用 SLF4J"两条。
- **建议**: 该分支改为 `throw new NopException(...)`（或 `return false` 前先 `System.exit(1)`），错误信息走 stderr/LOG.error。同类残留：`CodeFormatter.java:251`、`ZipFileWatcher.java:74` 的 `System.out.println` 位于嵌入 main 源码的 debug `main()` 方法内（`ZipFileWatcher.main` 还硬编码了作者本机路径），建议移入 test 或删除。
- **信心水平**: 确定
- **误报排除**: 不是"CLI 工具用 stdout 的合理用法"：问题不在用 stdout 输出结果，而在错误路径既不返回失败也不设非零退出码，且 main 代码中真实运行路径上的 System.out 仅此一处（另两处是 debug main）。
- **复核状态**: 未复核

### [G1-14-03] ExecutionContextImpl.complete() 与 onBeforeComplete() 之间的注册窗口会静默丢弃回调

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/context/ExecutionContextImpl.java:82-128`
- **证据片段**:
  ```java
  @Override
  public void complete() {
      // 在锁内检查done并占用终态，避免与completeExceptionally并发时
      // 出现回调以错误参数触发(例如已失败后仍以null触发成功回调)
      synchronized (this) {
          if (done)
              return;
      }
      fireBeforeComplete();          // 锁外执行；期间 onBeforeComplete 仍可注册成功（done 尚未置位）
      boolean win = false;
      synchronized (this) {
          if (!done) {
              done = true;
              win = true;
            }
      }
      if (win)
          fireAfterComplete(null);
  }
  ```
- **严重程度**: P3
- **现状**: `complete()` 在第一次锁内只检查不置位，随后锁外 `fireBeforeComplete()`（会取走并清空当前回调列表），再第二次加锁置 `done=true`。若另一线程在此窗口调用 `onBeforeComplete()`（其锁内检查的 done 仍为 false，注册成功），该回调加入的是新一轮列表，而 `complete()` 不再第二次触发 `fireBeforeComplete`——回调被静默丢弃且永不执行。代码注释表明作者已处理 complete/completeExceptionally 之间的终态竞态，但漏掉了注册侧窗口。
- **风险**: 触发条件苛刻（需并发地在 complete 进行中注册 before 回调），但后果是清理类回调（典型用途）被静默跳过——无异常、无日志，属于最难排查的一类缺陷。ExecutionContextImpl 是平台执行上下文的内核实现。
- **建议**: 将"置位 done"提前到 `fireBeforeComplete()` 之前一次完成（先 `synchronized { if(done) return; done=true; }` 再触发前后回调，completeExceptionally 同步改造），并在 `onBeforeComplete` 命中 done 时既抛异常也不再接受注册（当前抛 ISE 的行为保持，只需消除窗口）。补一个与现有 TestExecutionContextImpl 同风格的并发回归测试。
- **信心水平**: 确定（窗口存在性由代码结构直接可得；实际触发概率低）
- **误报排除**: 不是"已知的 complete/completeExceptionally 竞态处理"：注释与测试（testCompleteExceptionallyAfterCompleteIsIgnored）只覆盖终态互斥，未覆盖"注册 vs complete 推进"的交错。
- **复核状态**: 未复核

### [G1-14-04] ResourceTenantManager 租户路径集合的懒初始化为非同步非 volatile 的 check-then-act

- **文件**: `nop-kernel/nop-core/src/main/java/io/nop/core/resource/tenant/ResourceTenantManager.java:56-57, 100-111`
- **证据片段**:
  ```java
  private Set<String> enabledTenantPaths;    // 无 volatile / 无同步
  private Set<String> disabledTenantPaths;

  public boolean isSupportTenant(String resourcePath) {
      if (disabledTenantPaths == null) {           // 多线程同时进入
          disabledTenantPaths = CFG_TENANT_RESOURCE_DISABLED_PATHS.get();
          if (disabledTenantPaths == null)
              disabledTenantPaths = Collections.emptySet();
      }
      if (enabledTenantPaths == null) {
          enabledTenantPaths = CFG_TENANT_RESOURCE_ENABLED_PATHS.get();
          ...
      }
  ```
- **严重程度**: P3
- **现状**: `isSupportTenant` 是资源加载热路径（VFS/ResourceComponentManager 全线调用），两个普通字段的懒初始化无同步、无 volatile：并发线程可重复计算（幂等，无害）但也存在不安全发布——后读线程理论上可见未构造完成的 HashSet（JMM 下 final 字段安全发布保证不适用于先构造后共享的非 final 路径，`Collections.singleton` 返回不可变集合那条路径安全，`get()` 返回的新建集合那条不安全）。
- **风险**: 理论上的可见性异常（遍历到损坏的 Set）；工程上更多是维护陷阱——配置刷新（IConfigRefreshable 语义）后这两个字段持有的是启动时快照，与 `CFG_TENANT_RESOURCE_ENABLED.get()` 每次现读的其他配置项行为不一致，租户路径配置热更新不生效。
- **建议**: 改为 `volatile Set<String>` + 本地变量构造后一次性赋值（或直接每次读 `CFG_*.get()`，该配置读取本身有缓存）。同时 `runInitializeTenantTask`（76-88 行）finally 中 `set(false)` 建议改为恢复原始值/`remove()`，避免池化线程残留 entry（现值恒为 FALSE，实际泄漏量可忽略，属顺手修复）。
- **信心水平**: 很可能（不安全发布成立需 JMM 层面推导；配置快照不刷新部分是确定行为）
- **误报排除**: 不是"双重检查锁优化"：这里没有锁也没有 volatile，且字段值依赖可刷新的 IConfigReference——与静态单例 DCL 的误报场景（已正确加 volatile）不同类。
- **复核状态**: 未复核

### [G1-15-01] nop-api-core 公开工具 API 签名使用裸 raw Class 参数

- **文件**: `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/util/SourceLocation.java:143-145`；`nop-kernel/nop-api-core/src/main/java/io/nop/api/core/convert/ConvertHelper.java:100-108`
- **证据片段**:
  ```java
  // SourceLocation.java:143
  public static SourceLocation fromClass(Class clazz) {
      return fromPath("class:" + clazz.getCanonicalName());
  }

  // ConvertHelper.java:100-108
  public static Object getDefault(Class clazz) {
      if (clazz.isPrimitive())
          return s_primitiveDefaults.get(clazz);
      return null;
  }
  public static Class getPrimitiveClass(Class clazz) {
      return s_primitiveClasses.get(clazz);
  }
  ```
- **严重程度**: P3
- **现状**: api-core 是全仓被依赖最广的公开 API 模块，这三处公开方法以 raw `Class`（无 `<?>`）作参数/返回类型，同类 `ConvertHelper.convertConfigTo(Class<T> targetType, ...)` 等均已正确参数化，同文件内风格自相矛盾。另 `GlobalCacheRegistry.java:44` 有一处对已具泛型变量的多余 raw cast `((ICacheManagement) cache).remove(cacheKey)`。
- **风险**: 纯类型卫生问题：raw type 绕过编译器泛型检查并向调用方传播 raw 化告警；作为平台门面 API 会把 raw 用法"合法地"扩散到下游模块。无已知运行时缺陷。
- **建议**: 统一改为 `Class<?>`（`getDefault`/`getPrimitiveClass` 可顺带收窄返回类型），删除多余 raw cast。
- **信心水平**: 确定
- **误报排除**: 已按维度 15 的克制口径过滤：CloneHelper/FutureHelper/FreezeHelper/ArrayHelper 的 `(T)`/`(T[])` 泛型管道转换属动态边界惯用法未报告；本条是公开签名层面的 raw type（编译器可零成本修复），与内部实现惯用法不同类，故保留为 P3。
- **复核状态**: 未复核

### [G1-09-03] ByteHelper 存在 main 代码中唯一一处裸 RuntimeException 包装

- **文件**: `nop-kernel/nop-commons/src/main/java/io/nop/commons/util/ByteHelper.java:358-361`
- **证据片段**:
  ```java
      } catch (UnsupportedEncodingException e) {
          throw new RuntimeException(e); // never happen
      }
      return result;
  ```
- **严重程度**: P3
- **现状**: ISO-8859-1 是 JVM 规范保证可用的字符集，`UnsupportedEncodingException` 确实不可达，但兜底包装用的是裸 `RuntimeException`，而非平台约定的 `NopException.adapt(e)`（同文件 FileHelper.copyFile 等同场景均用 adapt）。
- **风险**: 单点、低概率，但它是三模块 main 代码中唯一的 `throw new RuntimeException`，与 AGENTS.md"Never use bare RuntimeException"的硬性红线直接冲突，具有被复制模仿的示范效应。
- **建议**: 改为 `throw NopException.adapt(e);` 一行修复。
- **信心水平**: 确定
- **误报排除**: 不是 Guard 式断言工具或移植代码：这是通用字节工具类中一次真实的 catch-then-wrap，仓库同场景已有标准写法可循。
- **复核状态**: 未复核

### [G1-16-01] TaskExecutionGraph 仅 happy path 测试，错误/并发路径零覆盖；并发工具类测试覆盖不均衡

- **文件**: `nop-kernel/nop-core/src/test/java/io/nop/core/model/graph/TestTaskExecutionGraph.java:17-41`
- **证据片段**:
  ```java
  public class TestTaskExecutionGraph {
      @Test
      public void testRun() {
          AtomicInteger count = new AtomicInteger();
          TaskExecutionGraph graph = new TaskExecutionGraph(GlobalExecutors.cachedThreadPool(), "test");
          ...
          graph.addTask("a", task);
          graph.addDepend("b", "a");
          graph.addDepend("c", "b");
          graph.addDepend("a", "c");       // 故意成环，验证 DagAnalyzer 断环
          graph.analyze();
          CompletableFuture<?> future = graph.executeAsync(null);
          FutureHelper.syncGet(future);
          assertEquals(3, count.get());
      }
  }
  ```
- **严重程度**: P3
- **现状**: 该测试类只有一个 `testRun`：无任务失败传播断言、无 runPermits/线程池拒绝/中断路径、无幻影依赖名的负向用例——恰好是 [G1-14-01] 两处缺陷所在的面。另 LongHashMap（有 IntHashMap 测试而无对应测试）、ByteQueue、LocalCache（putIfAbsent 原子性修复无回归测试）三个并发相关类缺直接测试，与 IntHashMap/MapCache/HighWatermarkSemaphore/ContextTaskQueue 的覆盖形成不均衡。
- **风险**: DAG 错误路径的回归无保护网：[G1-14-01] 类缺陷（静默挂起、依赖丢失）任何一次重构都可能引入/复发而不被现有测试捕获；LocalCache 的原子性修复若被改回 check-then-act 无测试报警。
- **建议**: 优先补三类测试：(1) 幻影依赖名 → 断言抛错或显式记录；(2) 任务抛异常 → 依赖任务与聚合 future 均以异常完成（带超时断言防挂起）；(3) LocalCache.putIfAbsent 并发只胜一次 + removeIfMatch equals 语义（与 MapCache 同套断言）。对照组 TestExecutionContextImpl 的 latch+executor+终态断言风格可直接复用。
- **信心水平**: 确定（测试文件已全文核对；缺陷对应关系见 G1-14-01/G1-14-02）
- **误报排除**: 不是"数量论"：本维度评估的是保护力而非数量（三模块 test/main 比约 1:10 对内核模块属正常），报告点是"最需要保护的错误路径恰好无测试"这一结构性缺口，且与本轮 P2 发现一一对应。
- **复核状态**: 未复核

## 最终保留项（待复核后更新）

| 编号 | 严重程度 | 文件 | 一句话摘要 |
|------|---------|------|-----------|
| [G1-14-01] | P2 | nop-core/.../execution/TaskExecutionGraph.java | 幻影依赖静默跳过 + 错误路径致聚合 future 永不完成 |
| [G1-09-01] | P2 | nop-core 多文件（12 文件 15 处） | 裸 IAE/ISE 消息嵌入未定义的 nop.err.* 伪错误码 |
| [G1-14-02] | P2 | nop-commons/.../cache/ICache.java + LocalCache.java | 默认方法违反自身原子性契约，removeIfMatch 三实现语义分裂 |
| [G1-09-02] | P3 | nop-core/.../command/ExecCommandProcessor.java | 缺 --command 时 stdout 提示后以退出码 0 成功返回 |
| [G1-14-03] | P3 | nop-core/.../context/ExecutionContextImpl.java | complete 与 onBeforeComplete 注册窗口静默丢回调 |
| [G1-14-04] | P3 | nop-core/.../resource/tenant/ResourceTenantManager.java | 租户路径集合非同步懒初始化 + 配置快照不刷新 |
| [G1-15-01] | P3 | nop-api-core/.../SourceLocation.java, ConvertHelper.java | 公开 API 签名使用 raw Class 参数 |
| [G1-09-03] | P3 | nop-commons/.../util/ByteHelper.java | main 代码唯一裸 RuntimeException 包装 |
| [G1-16-01] | P3 | nop-core/.../graph/TestTaskExecutionGraph.java | DAG 执行仅 happy path 测试，错误路径零覆盖 |

## 子项复核结论

复核人：独立复核代理 R4（2026-09-30）

| 发现编号 | 判定 | 复核说明 |
|---|---|---|
| [G1-14-01] | 保留（维持 P2）| 逐行重核 `nop-kernel/nop-core/src/main/java/io/nop/core/execution/TaskExecutionGraph.java` 全文：addDepends(:73-83) 确无依赖名注册校验；`Dag.addNextNode`（`model/graph/dag/Dag.java:212-218`）确以 `computeIfAbsent` 创建幻影节点，且 addDepends 先行把 depend 挂到 DEFAULT_ROOT（:79），故 `DagAnalyzer.checkStartReachable`（`DagAnalyzer.java:77-97`，BFS 从根出发）的可达性校验必过，ERR_GRAPH_NODES_NOT_REACHABLE 不会触发；`waitPrevTasks(:149-181)` 中幻影依赖被静默丢弃的机制复核为经 :161-169 的 `retainAll(futures.keySet())` 分支（root+幻影 → retainAll 后为空 → 返回 null → 按 noDepends 直接并发执行），与原报告"单依赖路径 futures.get 返回 null"的表述殊途同归（该 size==1 路径对经 addTask 注册的任务实际不可达，净效果完全一致）；多依赖路径 null 元素进 `CompletableFuture.allOf` 先 NPE 的推论正确。异常丢弃路径核实：runTask 确在 whenComplete 回调内被调用（:122-128），:190 `throw NopException.adapt(e)` 与 :196 `executor.execute` 的 RejectedExecutionException 均落入被丢弃的回调 stage，任务 future 永不完成，聚合 future 挂起——成立。实际使用面核实：`BeanContainerImpl.asyncStartBeans`（nop-ioc `BeanContainerImpl.java:541-558`）确实以 TaskExecutionGraph 做 bean 启动排序，但该路径受 `nop.ioc.app-beans-container.concurrent-start` 配置门控（`IocConfigs.java:28` 默认 false，demo `application.yaml:104` 显式 false），影响为条件性；另核实 `BeanTopologySorter.fillResolvedDepends`（:153-195）不剔除 lazy bean，而 start()（:487-500）只把非 lazy 单例加入 startBeans——即 eager bean dependsOn lazy bean 时 resolvedDepends 中的依赖名必然成为幻影，幻影场景比原报告的"拼写错误"假设更系统性可达。两条缺陷均确认为框架核心 DAG 执行器的真实契约破坏，P2 恰当（非默认路径 + 不损数据，未达 P1）。|
| [G1-09-01] | 保留（维持 P2）| 独立全量 grep `throw new (IllegalArgumentException|IllegalStateException)("nop\.` 于 nop-kernel main：在报告点名的 12 个文件中核实 **14 处** throw（ResourceComponentManager:398,408、ExecutionContextImpl:86,99、EvalFrame:49、TemplateGenPath:49、XNode:2061、DeltaJsonLoader:64、TreeDataHelper:141、ResourceVersionHelper:68、GenericFunctionTypeImpl:37、MathHelper:1479、StdDataType:302、TaskExecutionGraph:50），行号与报告逐一相符；报告计"15 处"多 1（疑把 ExecutionContextImpl:177 的 `LOG.error("nop.err.core.execution-after-complete-callback-fail",...)` 日志位点计入），属计数偏差不影响实质。对 12 个码（unsupported-resource-file / execution-already-completed / invalid-frame-parent-level / template-path-stack-mismatch / invalid-tree-structure-key / invalid-version-string / type-index-conflict / invalid-output / generate-random-long-fail / gen-extends-not-string / arg-names-size-not-match / graph.not-allow-change）做 nop-kernel 全树反向 grep：除 throw 语句自身外零命中——CoreErrors/CommonErrors/ApiErrors 与 i18n yaml 均无定义，"伪错误码"定性成立。细微校正：其中 3 处前缀并非 `nop.err.`（`nop.web.page.gen-extends-not-string`、`nop.core.func-type....`、`nop.task.graph.not-allow-change`），但同为错误码命名格式的裸 IAE/ISE，与标题"nop.err.* 风格伪错误码"的刻画不冲突。框架核心违反两档策略 + 误导性半迁移形态成立，P2 恰当。|
| [G1-14-02] | 保留（维持 P2）| 逐文件核对：`ICache.java:66-76` javadoc 明文要求 check-then-act 原子性而默认实现为 containsKey→put 两步；`ICache.java:92-99` 默认 `remove(K,V)` 确用引用相等 `==` 且 getIfPresent 与 remove 之间存在可被并发替换的窗口；`LocalCache.java:234-236` 覆写 putIfAbsent 为 Caffeine `asMap().putIfAbsent` 原子版（含竞态修复注释）但全类无 `remove(K,V)` 覆写，`removeIfMatch`（:349-351）委托回落到接口默认实现；对照 `MapCache.java:91-93`（`map.remove(key,object)` 原子+equals）与 `JavaxCache.java:100-102`（JCache `cache.remove(key,object)`）语义分裂属实。反向 grep 仓内 main 代码：ICache 层面的 removeIfMatch/putIfAbsent 无业务调用方（命中的均为 ConcurrentHashMap/Map 同名方法），与原报告"未构成现行故障"的自我限定一致。作为注册进 GlobalCacheRegistry 的公开扩展点的接口契约漂移，P2 恰当。|

复核中发现的附带线索（不计入发现）：(1) BeanTopologySorter.fillResolvedDepends 保留 lazy bean 依赖导致 concurrent-start 下幻影依赖系统性可达（见 G1-14-01 复核说明，属该发现风险面的加强证据）；(2) `BeanScopeImpl.close()`（nop-ioc，:113-115）在遍历后 `beans` 非空时复用 `ERR_IOC_BEAN_SCOPE_ALREADY_CLOSED` 表达"销毁遍历未清空"的异常条件，错误码语义与 G4-09-03（OrmSessionImpl 复用 SESSION_CLOSED）同族，建议归入相应 IoC 审计维度跟进；(3) nop-xlang 存在 4 处同款伪错误码裸 IAE/ISE（`JaninoParser.java:146`、`RegisterModelDiscovery.java:287,299`、`DeltaExtendsGenerator.java:43`），在本报告范围（nop-core/commons/api-core）之外，应确认是否已被 xlang 审计覆盖。
