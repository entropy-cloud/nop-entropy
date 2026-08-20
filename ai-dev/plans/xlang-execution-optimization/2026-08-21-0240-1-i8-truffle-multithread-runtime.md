# I8 truffle 多线程运行时（Context 池 + 共享 Engine + SHARED 形态）

> Plan Status: completed
> Last Reviewed: 2026-08-21
> Source: `ai-dev/backlog/xlang-execution-optimization-roadmap.md` I8（范围/验收 = 定稿条目）；设计冻结于 `ai-dev/design/xlang-truffle/02-architecture-baseline.md`（§三 SHARED 注册与 initializeMultipleContexts、§四 context-independent 准则、§五多线程架构=路 A 池租借契约/共享 Engine/enter-leave 窗口/SHARED 并发验证载体、§六内联缓存身份纪律、§七翻译缓存键与淘汰归属）；对拍口径 = 设计 `ai-dev/design/xlang-execution/01-architecture-baseline.md` §五
> Mission: xlang-execution-optimization
> Work Item: I8

<!-- Draft review: round-1（fresh session ses_fdf842972ffeEGKw0929lOzriJ，Verdict Ready / 0 Blocker：引用锚点全对 live 核验、scope 与 I8 定稿条目精确匹配、设计保真（池契约逐条镜像/I7→I12 编号映射/两形态载体延续）；2 Major advisory：translator 单实例并发 translate 未显式命名 / 观测事件消费路径依赖方向张力未点名；4 Minor：javadoc 残留同步/池耗尽语义/命名约定（系列一致不阻断）/并发 driver 承载形态）→ 修复（执行侧+翻译侧并发核实 / truffle 侧 SPI 适配器消费路径 / 全模块 grep EXCLUSIVE 清单收口 / 池耗尽语义入契约项 / driver 承载裁定入 Phase 1）→ round-2（fresh session ses_fdf7e21e3ffevE16r1XBxNMnoi，Verdict Ready / 0 Blocker / 0 Major；5/5 round-1 advisory 确认解决；剩 2 Minor+1 nano（javadoc 枚举精化/driver 触发位置/Closure Gate 措辞）当场折入）→ 共识达成 → active。 -->
> Related: I7（前置：B 族翻译 + 两级缓存 + EXCLUSIVE 形态保持声明，I5/I7 移交本 plan 的 SHARED/池/并发对拍/缓存淘汰四项显式在案——I5 Deferred Successor=I8、I7 Execution Notes §11）；I9（后继：单元级翻译失败观测事件的消费方=决策树动态路径第三分支 + 池运行时的生产路由接入方）

## Purpose

把 truffle 后端从 EXCLUSIVE 单 Context 过渡形态推进到设计终态 SHARED 多线程运行时：`@Registration` 形态切换（编译期常量变更 + 重新编译）、共享 Engine 单例 + Context 池（租借协议：租借注入/归还清空/enter..leave 窗口语义/可重入批求值）、翻译缓存淘汰（容量上限/LRU）、SHARED 形态并发正确性对拍（多线程经池并发求值 vs 解释器单线程基线 + 无跨 Context 串值断言）、池租借协议正/负测试、单元级翻译失败观测事件（供 I9 决策树第三分支消费）。

## Current Baseline

- I7 产物存在（前置断言——执行本 plan 前须核验 I7 已 `completed`）：支持集 120（`ExecNodeBaseline.truffleRegisteredTarget()` 全量口径）、`TestTruffleCoverageMatrix` 126 用例全绿、corpus 三列对拍全绿（v1 22 + 覆盖 A 33 + 覆盖 B 20）、`TestTranslatorCoverageB` 17 用例（含 XFunctionDispatchNode 探针断言 directCalls/genericCalls 与 XStaticMethodNode 常量同一性断言）、三模块合计 1254/0/0（nop-xlang 514 + nop-xlang-java 263 + nop-xlang-truffle 477）。
- **EXCLUSIVE 过渡形态在仓（live 事实）**：`nop-kernel/nop-xlang-truffle/src/main/java/io/nop/xlang/truffle/lang/XLangLanguage.java:28` `contextPolicy = ContextPolicy.EXCLUSIVE`；`initializeMultipleContexts` 继承基类默认实现（该类 javadoc 明示"I8 切 SHARED 仅变更该注解取值后重新编译，注册结构不重构；SHARED 切换时按需覆写"——I5 决策 D1 承诺）。
- **求值窗口协议在仓（池协议的扩展基座）**：`XLangContext.bindEvaluation(IEvalScope, IEvalOutput)` / `clearEvaluation()`（根节点入口绑定/出口清空；javadoc 明示"EXCLUSIVE 最小形态，池租借协议归 I8"）+ `swapOutput/restoreOutput` 换缓冲协议（I7 落地，栈式 try/finally 配对；I7 已裁定"归还前清空既有纪律覆盖，无额外移交项"）。
- **测试域 driver 形态（live 事实）**：`XLangTruffleEval`（`io.nop.xlang.truffle.eval`）构造函数 `Context.newBuilder(XLangLanguage.ID).allowHostAccess(HostAccess.ALL).build()` ——**未绑定共享 Engine**，每实例独立 Context；对拍列 `TruffleBackendColumn.open()` = 单 XLangTruffleEval 串行求值（EXCLUSIVE 载体）。生产代码路径的路由接入归 I9（类 javadoc 明示）。
- **求值 handoff（live 事实）**：`EvalHandoff` ThreadLocal<Pending>——同线程一次一求值，嵌套 `begin` fail-fast（"nested eval handoff is not supported"）；多线程各自 ThreadLocal 天然隔离，但与"enter/leave 批求值（可重入）"的关系（批量 = 多次 handoff 循环而非嵌套）未经形态定稿。
- **翻译缓存无淘汰（live 事实）**：`TranslationCache`（language 实例作用域，ConcurrentHashMap）只增不淘汰（javadoc 明示"缓存淘汰（容量上限/LRU）归 I8"）；键 = sourceKey + 树指纹（resourcePath / `dyn:` 源内容哈希双形态，I5 决策 D2）。
- **单元级翻译失败当前形态**：`ExecToTruffleTranslator` 支持集外 fail-fast（报节点类名 + SourceLocation），无观测事件记录——I9 决策树第三分支（动态路径单元级翻译失败降级）缺供给。
- I5 Deferred（Successor=I8，责任链在案）与 I7 Execution Notes §11（移交四项）= 本 plan 范围的既定来源；Q1/Q4 watch-only 重评估归 I12。
- 设计 02 §五冻结要点：池租借模式（不做每线程固定绑定——线程池弹性伸缩会泄漏 Context）；租借状态协议契约（租借注入输出缓冲/全局作用域句柄、归还前清空；context 内状态只在 `enter()..leave()` 窗口内有效；归还后不得残留上一批求值的可变状态，翻译缓存等可共享数据除外）；池大小配置化（缺省随并发工作线程规模；创建/销毁成本实测与调优归 I12——设计文本"I7 基准"按编号映射读 I12）；SHARED 正确性验证载体 = 切换后并发对拍（多线程经池并发求值同一/不同编译单元，断言与单线程解释器一致 + 无跨 Context 串值）。
- 真正剩余的 gap：EXCLUSIVE 注解未切；无共享 Engine 单例；无 Context 池与租借协议；翻译缓存无淘汰；无单元级翻译失败观测事件；无 SHARED 并发对拍与池协议正/负测试。

## Goals

- **SHARED 形态切换**：`XLangLanguage` `@Registration` 注解 `contextPolicy` 取值 EXCLUSIVE → SHARED（编译期常量变更 + 重新编译；注册结构不重构——I5 D1 承诺保持）；`initializeMultipleContexts` 按 SHARED 要求覆写（如 Phase 1 定稿需要）；既有全部测试保持全绿（SHARED 为超集形态：单 Context 用法在 SHARED 下合法）。
- **共享 Engine 单例 + Context 池（路 A）**：Engine 单例（进程内一份，创建失败显式异常 fail-fast——注册不可用条目语义归 I9）；池租借协议按设计 §五契约落地：租借时注入本次求值输出缓冲与全局作用域句柄、归还前清空、context 内状态只在 enter()..leave() 窗口有效、异常路径归还同样清空；`enter()/leave()` 包批求值（可重入）；池大小配置项（缺省随并发工作线程规模，数值不发明——调优归 I12）。
- **翻译缓存淘汰**：容量上限/LRU（配置项 + 缺省值；LRU 语义与键 = sourceKey + 树指纹纪律保持，淘汰不影响正确性——被淘汰单元下次 getOrBuild 重翻译）；淘汰可观测（淘汰计数/日志）。
- **SHARED 形态并发正确性对拍（roadmap 验收第一项）**：多线程经池并发求值同一/不同编译单元，结果与单线程解释器求值一致（三层断言 + truffle 身份断言 = 翻译 AST 经 CallTarget 执行）+ 无跨 Context 串值断言（输出缓冲/作用域隔离逐线程比对）全绿；语料 = 既有 corpus 单元（v1/覆盖 A/覆盖 B 的静态+动态单元按适用性，不扩 corpus）。
- **池租借协议正/负测试（roadmap 验收第二项）**：正测试（租借→求值→归还→再租借无残留、异常路径归还后无残留）；负测试（注入故意残留 → 残留检测机制红灯，红/绿对照——I1 差异注入自检先例）。
- **单元级翻译失败观测事件**：事件载荷（sourceKey/节点类名/SourceLocation/原因）+ 消费接口（I9 决策树第三分支回调）落地；默认实现记录事件；无消费者时不静默（显式可查询）。
- `org.graalvm.*` 不泄漏口径保持（收口复跑不泄漏断言）。

## Non-Goals

- 生产代码路径的后端注册 SPI/统一决策树/路由接入（I9——本 plan 只供给观测事件接口与池运行时接入点，不做路由）。
- java 侧转译/加载/构建集成（I9/I10/I11）。
- 性能基准与调优：Context 池创建/销毁成本实测、池大小调优、Q1（Bytecode DSL）/Q4（与 nop-js 共享 Engine）重评估触发口径量化（I12——设计 §五/§八/§九既定归属）。
- corpus 扩充（并发对拍消费既有 corpus 单元，不新增对账单元）。
- docs-for-ai 同步（I11）。

## Scope

### In Scope

- SHARED 形态切换（注解取值 + initializeMultipleContexts 覆写面 + 既有测试全绿保持）。
- 共享 Engine 单例 + Context 池 + 租借协议（含异常路径归还、可重入批求值、池大小配置项）。
- 翻译缓存淘汰（容量上限/LRU + 配置项 + 淘汰可观测）。
- 单元级翻译失败观测事件（载荷 + 消费接口 + 默认记录实现）。
- SHARED 并发对拍测试（corpus 既有单元 × 多线程经池）+ 池协议正/负测试 + 淘汰/观测单测。
- 共享 AST 下 DSL 缓存（XFunctionDispatchNode 特化/缓存状态、探针计数器）并发语义核实与必要处置。

### Out Of Scope

- 同 Non-Goals。

## Execution Notes

### Phase 1 裁定记录（2026-08-21，全部 repo-observable）

**0. 前置核验**：I7 `completed`（roadmap live 核验，2026-08-21）。消费移交清单逐项对齐：
- I7 Execution Notes §11 四项 → 本 plan 执行项映射：SHARED 形态切换（Phase 2 item 1）、Context 池租借协议（Phase 2 item 2）、并发正确性对拍（Phase 3 item 1）、翻译缓存淘汰（Phase 2 item 3）；`XLangContext.swapOutput` 池归还兼容性 = 归还前清空既有纪律覆盖（Phase 2 池实现内含）。
- I5 Deferred（Successor=I8）责任链 = 上述同一集合（I5 D1 承诺"SHARED 仅变更注解取值"在 D1' 保持；I5 D2 键口径在淘汰实现中不变）。
- 观测事件 = I9 决策树第三分支供给（消费路径见 §7）。

**1. SHARED 切换面定稿**：变更点 = `XLangLanguage.java:28` 注解取值 `ContextPolicy.EXCLUSIVE` → `ContextPolicy.SHARED`（编译期常量 + 重新编译；注册结构不重构——I5 D1 承诺保持）。API 锚点（`~/sources/graal`）：
- `TruffleLanguage.java:4333-4355`（ContextPolicy.SHARED javadoc）：SHARED 下"All methods of the language instance and parsed ASTs may be called from multiple threads at the same time"；"Language instance fields must only be used for data that can be shared across multiple contexts and mutable data held by the language instance must be synchronized"。
- `TruffleLanguage.java:928-936`（initializeMultipleContexts javadoc）：sharing 仅在 Context 以显式 Engine（或 option）创建时发生——"Sharing can be enabled by specifying an Builder#engine(Engine) explicit engine"；`initializeMultipleContexts` 保证在任何共享使用前被调用。
- SL 先例：`SLLanguage.java:226` `contextPolicy = ContextPolicy.SHARED` + `SLLanguage.java:556` 覆写 `initializeMultipleContexts()` 使 singleContext Assumption 失效。
- **`initializeMultipleContexts` 覆写裁定**：覆写（非继承默认空实现）——XLangLanguage 无 single-context 假设需失效（语言实例唯一可变数据 = 翻译缓存（已并发安全化）+ 观测记录器（同步集合）），覆写体 = 置 `volatile boolean multipleContextsInitialized = true`，经 `isMultipleContextsInitialized()` 暴露。角色 = SHARED 激活的**接线证据**（Phase 3 断言共享真实发生：经 `TranslatedUnit.getRootNode().getLanguage()` 取语言实例断言 flag 置位），非空转摆设。SL 覆写先例对应（SL 失效假设 / XLang 置观测旗标，同为"多 Context 生效通知"消费）。
- **切换后 context-independent 复验方式**：既有全量测试保持全绿（非共享 Engine 的单 Context 用法在 SHARED 下合法——每 Context 独立 Engine 时语言实例不共享，行为不变）承载翻译正确性；SHARED 特有风险（共享 AST 并发执行、跨 Context 串值）由 Phase 3 并发对拍承载——两形态各自是验证载体的设计裁定延续。

**2. 共享 AST 并发语义核实（执行侧 + 翻译侧，规范/源码锚点 + 处置裁定）**：
- **执行侧（DSL 缓存/特化改写）**：SHARED 下共享 AST 多线程执行的线程安全依据 = DSL 规范层契约（`TruffleLanguage.java:4342-4344`"parsed ASTs may be called from multiple threads"）——DSL 特化状态转换/节点改写由框架同步（`Node.replace`/adopt 内部锁，SL 以 SHARED 注册并依赖 DSL 节点不改即并发安全为先例）；`XFunctionDispatchNode` 的 `@Cached` 条目 = CallTarget 身份 + DirectCallNode（**无运行时值身份**，I7 纪律），CallTarget 身份 = language 实例作用域翻译缓存产物，跨 Context 稳定 → L1 guard 在共享形态下成立。共享 helper（`XLangSemantics`，static 纯函数）与无状态节点字段无共享可变状态。
- **探针计数器处置裁定：原子化**。live `XFunctionDispatchNode` 探针 `private long directCalls/genericCalls` 为非原子 long——共享 AST 并发执行下 `++` 为数据竞争（丢失更新 + long 撕裂读），不允许静默保留。处置 = 改 `AtomicLong`（getter 签名保持 `long`，DSL 生成类不受影响）；锚点 = SHARED policy"mutable data held by the language instance must be synchronized"（共享 AST 状态同适用）。验证 = Phase 3 并发对拍（探针在并发路径上读写不崩、计数单调）。
- **翻译侧（translator 单实例并发 translate）**：`ExecToTruffleTranslator` **无实例字段**（live 代码核实：全部翻译状态在方法局部 `FrameCtx`（每次 translate() 新建）；`SUPPORTED_NODE_CLASSES` static final 不可变）；`TreeFingerprints`/`SyntheticSources`/`FrameLayoutMapper` 均 static 纯工具无共享可变状态。裁定 = **无状态声明维持单实例**（不改为每线程实例化——无收益且弱化"单翻译器"事实源）；共享 Engine 下不同 sourceKey 并行 translate 安全；断言载体 = Phase 3 并发 getOrBuild + 淘汰并发测试（行为级验证无串译）。

**3. 池租借协议契约定稿**：
- 落点/命名：新包 `io.nop.xlang.truffle.runtime`；类 = `XLangTruffleEngine`（设计 §五架构图命名）+ `XLangContextPool`（含嵌套 `Lease`）。
- **租借注入与既有窗口协议的关系：复用**。注入链路保持 I5/I7 形态：求值现场（scope + 输出缓冲）经 `EvalHandoff.Pending`（ThreadLocal，逐求值）→ 根节点 `bindEvaluation` → `finally clearEvaluation`；池不另设注入通道。池侧补齐设计契约"归还前清空"= **归还时残留检测 + 防御性二次清空**（`Lease.close()` 内，窗口内执行）：`XLangContext` 新增 `hasEvaluationResidue()`（evalScope/output 任一非 null），残留 → fail-fast（IllegalStateException）且该 Context 从池中退役（可疑状态不得复用）；无残留 → 幂等 `clearEvaluation()` 防御清空。
- **enter/leave 与可重入批求值**：`lease()` = 出借 + `context.enter()`；`Lease.close()` = 残留检测/清空（仍在 enter 窗口）→ `leave()` → 归还；批求值 = 一个 Lease 内多次 `eval()`（每次一个 handoff begin/end 循环，**不嵌套**——`EvalHandoff` 嵌套 fail-fast 语义保持）。锚点：`Context.java:798-807`（"Contexts can be entered multiple times on the same thread" + enter/leave 消除逐操作进出开销即批形态）。
- **池耗尽语义裁定：有界阻塞等待**（idle 队列 `take()`）。拒绝有界外增长（Context 泄漏——池化要消除的正是它）与 fail-fast（池缺省随工作线程规模，饱和等待是池语义常态）；fail-fast 保留给协议违约（双重归还/残留/use-after-close）与 Engine 创建失败。调优归 I12。
- **残留检测机制形态（负测试载体）**：归还时断言 + 注入红/绿对照——负测试经公共 API 注入（`context.enter()` → `XLangLanguage.currentContext().bindEvaluation(...)` 绕过归还清空路径 → `leave()`）→ `Lease.close()` 红灯；正常求值归还与异常抛出路径归还 → 绿灯。检测机制可达性 = 负测试自身证明（同一 `currentContext()` 通道）。
- **异常路径归还保证**：`Lease.close()` try/finally——leave 与归还不因残留异常丢失（残留路径 = leave + 退役 + 抛错）。
- **池打开预热**：每 Context 创建即跑一次平凡求值（`NullExecutable` 单元）——fail-fast 接线自检（池出口到 CallTarget 执行全链）+ 保证语言 context 已初始化（归还时 `currentContext()` 可达）。

**4. 共享 Engine 单例形态定稿**：`XLangTruffleEngine.sharedEngine()` 惰性单例（holder 惰性初始化；首次池打开/显式调用时创建）。创建失败 = 显式 IllegalStateException fail-fast（"注册不可用条目 + 降级解释器"归 I9，本 plan 不做路由）。Context 构建显式绑定：`Context.newBuilder(XLangLanguage.ID).engine(sharedEngine()).allowHostAccess(HostAccess.ALL).build()`；锚点：`Context.java:1185`（Builder.engine——"multiple contexts are created from one engine...may share/cache certain system resources like ASTs or optimized code"）。可重入 = §3 enter/leave 锚点。池大小配置：`XLangTruffleConfigs.CFG_TRUFFLE_CONTEXT_POOL_MAX_SIZE`（`nop.xlang.truffle.context-pool.max-size`，Integer），**缺省值 live 来源 = `Runtime.getRuntime().availableProcessors()`**（JVM 并行度即"并发工作线程规模"的 live 取值来源，类初始化时求值一次；数值调优归 I12）。

**5. 翻译缓存淘汰策略定稿**：
- **算法选型**：repo 核实无现成 LRU 先例（nop-kernel main grep：无 accessOrder LinkedHashMap / LRUCache 类）→ 自实现：`LinkedHashMap(accessOrder=true)` + monitor 守护；**翻译在锁外执行**（miss → 并行 translate → 重入锁 put + 淘汰超限 LRU 头），保持 I5/I7 CHM `computeIfAbsent` 并行翻译语义等价（并发重复翻译为良性竞争，胜者经 present-check 收敛）。
- 配置项：`XLangTruffleConfigs.CFG_TRUFFLE_TRANSLATION_CACHE_MAX_ENTRIES`（`nop.xlang.truffle.translation-cache.max-entries`，Integer，缺省 1024；显式构造器供测试）。调优归 I12。
- 并发安全：monitor 守护全部 map 读写；翻译锁外并行；淘汰在 put 路径内完成（size > max 时逐条淘汰 eldest）。
- 可观测：`getEvictedCount()`（AtomicLong）+ 淘汰 DEBUG 日志（slf4j，`XDefinition.java:23` 平台先例）。
- 正确性口径：键 = sourceKey + 树指纹纪律不变；淘汰不影响正确性——被淘汰单元下次 getOrBuild 重翻译，行为等价（测试断言：淘汰后重求值结果 equals 含类型 + 同键重翻译可用）。

**6. 单元级翻译失败观测事件形态定稿**：
- 载荷 `TranslationFailureEvent`：sourceKey / nodeClassName / SourceLocation / reason(String) / cause(Throwable|null)——nodeClassName 与 SourceLocation 从 fail-fast `NopEvalException` 参数提取（translator `unsupported()` 已 `param(ARG_CLASS_NAME/ARG_LOCATION)`；`NopException.getParam` line 382 可取回），reason = 异常消息。
- 消费接口 `TranslationFailureListener.onTranslationFailure(event)`：落 truffle 模块 main（`io.nop.xlang.truffle.translate`）；默认实现 = `XLangLanguage` 内置环形记录器（bounded，`getTranslationFailures()` 可查询）+ 已注册 listener 追加通知（CopyOnWriteArrayList）。**无消费者时不静默**：内置记录器恒开（显式可查询），非"无 listener 则丢弃"。
- 记录点：`TranslationCache.getOrBuild` catch RuntimeException → 经 language 上报事件 → **原样重抛**（fail-fast 语义不变，事件是观测增量不是降级）；language 为 null（测试直构缓存路径）时维持 fail-fast 无记录。
- **消费路径裁定（依赖方向张力显式化）**：I9 决策树裁决入口在 nop-xlang（不可反向 import truffle 模块）→ 本接口的消费方 = **I9 的 truffle 侧 SPI 适配器**（落 truffle 模块内，经池/Engine 运行时句柄取得语言实例、注册 `TranslationFailureListener`，向上经注册表契约暴露降级决策）——接口形态按本裁定落仓，I9 plan 消费。

**7. 并发对拍 driver 承载形态裁定：独立并发测试类**（Phase 3 消费）：`TestCorpusConcurrentTruffleColumn`（truffle 测试 compare 包）。理由：harness 列契约 = 单列串行执行（`ExecCompareHarness.executeColumn` 顺序调用、column 实例非线程安全——`TruffleBackendColumn` 持单 Context），并发对拍需要 N 线程 × 池 × 每线程独立现场，超出列契约维度，强行扩展列机制破坏 I1 语义；**对拍口径照常消费**：三层断言（预期声明 + 解释器列基线，经 `CompareValues.typedEquals`/`RecordingEvalOutput` 共享断言工具）+ 身份断言（translated root sourceTree 同一性 + CallTarget）+ 共享缓存实例同一性断言（跨线程同键 → 同一 `TranslatedUnit` = SHARED 共享真实发生的接线证据）+ 无跨 Context 串值断言（每线程输出缓冲调用序列 = 各自预期、scope 变量集 = 各自预期）；语料 = 既有 corpus（v1 + 覆盖 A + 覆盖 B，静态/动态按适用性），不扩 corpus。

### Phase 2 执行记录（2026-08-21）

**8. 落地清单**（`nop-xlang-truffle` 主代码 + 测试）：

- SHARED 切换：`XLangLanguage` 注解取值 SHARED + `initializeMultipleContexts` 覆写（volatile 旗标 + `isMultipleContextsInitialized()`）；javadoc 口径收口（全模块 grep `EXCLUSIVE` 清单 = XLangLanguage/XLangContext/EvalHandoff/XLangRootNode/TruffleBackendColumn + corpus 三列测试 javadoc，逐一更新；仅保留 XLangLanguage javadoc 中"自 EXCLUSIVE 过渡形态切换"的历史表述）。
- 新增 `io.nop.xlang.truffle.runtime` 包：`XLangTruffleEngine`（共享 Engine 惰性单例，双检锁 + 创建失败显式 IllegalStateException，不缓存失败态）+ `XLangContextPool`（池 + 嵌套 `Lease`；`activate()` 统一状态机入口（AVAILABLE→LEASED + 计数 + enter）；归还 = 残留检测 + 防御清空 + leave + CAS 回 AVAILABLE 入队；残留/双重归还/归还后求值/关闭后租借均 fail-fast；池耗尽 = 有界阻塞（idle 队列 take）；池打开预热 = 每 Context 平凡求值（fail-fast 接线自检 + 语言 context 初始化保证 + 语言实例捕获 `getLanguage()`——I9 消费通道）；池关闭毒丸唤醒阻塞租借者）。
- `XLangContextPool.getLanguage()`：预热捕获共享 Engine 的语言实例（SHARED 下单份）——翻译失败观测注册与 SHARED 激活断言的运行时取用通道（Phase 1 §6 消费路径的落地形态）。
- `XLangTruffleEval` 重构：抽取 `evalOnContext(Context, sourceKey, tree, scope, output)` 静态通用求值协议（facade 与 Lease 共用）；`TranslatedEval`/`dynamicSourceKey` 位置不动（既有测试零改）。
- 翻译缓存淘汰：`TranslationCache` = accessOrder LinkedHashMap + monitor + **翻译锁外并行**（miss → 并行 translate → present-check 收敛）；`getEvictedCount()` AtomicLong + slf4j DEBUG 淘汰日志；`new TranslationCache(maxEntries)` 显式构造（非正数 fail-fast）+ 无参构造走配置。
- 观测事件：`TranslationFailureEvent`（sourceKey/nodeClassName/SourceLocation/reason/cause）+ `TranslationFailureListener`（@FunctionalInterface）+ `TranslationFailureRecorder`（环形 256 条 + 累计计数，public record）；`XLangLanguage` 内置 recorder + COW listener 列表 + `reportTranslationFailure`；`TranslationCache.getOrBuild` catch RuntimeException → 提取 NopEvalException 参数（ARG_CLASS_NAME + `getErrorLocation()`）上报 → 原样重抛。
- 探针原子化：`XFunctionDispatchNode` directCalls/genericCalls → AtomicLong（getter 签名不变，既有探针断言零改）。
- `XLangContext.hasEvaluationResidue()` 新增（归还残留检测）；`XLangRootNode` 增持语言实例引用 + `getXLangLanguage()`（运行时/测试侧取语言实例入口——RootNode 基类无公开 getLanguage）。

**9. 执行中发现并修复的缺陷（scope 内）**：

- **（a）存量编译缺陷（先于本 plan 存在）**：`TestTranslatorCoverageB.java:93` 使用不存在的 `NopEvalException(String, Throwable)` 构造器（I7 提交物在 live `NopEvalException` API 下不编译——Phase 1 基线核验时发现）。修复 = 改 `IllegalStateException(String, Throwable)`（同文件 176 行既有惯例）。
- **（b）Source 内容唯一性必须全局（SHARED 特有）**：原驱动按"逐 Context 计数"生成合成 Source 内容（"eval-1"...），共享 Engine 下 parse 缓存按语言实例共享 → 第二个 Context 的首个 eval 命中同内容缓存**跳过 parse**（绕过翻译缓存查找链路且 handoff 无解析产物）。修复 = 全局 `AtomicLong GLOBAL_EVAL_SEQ`（`XLangTruffleEval.evalOnContext` 内部持有）。
- **（c）池预热绕过状态机**：预热直接构造 Lease 不置 LEASED 状态 → 归还 CAS 失败误判违约 → 全部 Context 被退役（池空转 → 租借永久阻塞）；且计数不配对（leasedCount 负数）。修复 = `activate()` 统一激活入口（状态机 + 计数 + enter 一处维护，`lease()` 与预热共用）。
- **（d）parse 期异常透传**：parse 抛出的 fail-fast `NopEvalException` 经引擎包装为 PolyglotException 且无 host 异常分类、无 cause 链 → 宿主侧拿不到原始异常。修复 = `XLangLanguage.parse` catch RuntimeException → `pending.captureThrown(e)`（复用执行期异常的 handoff 交接通道）→ 驱动 unwrap 首分支命中原始异常。

**10. Phase 2 验证**：`./mvnw test -pl :nop-xlang-truffle -am -T 1C` **495/0/0**（477 基线全绿保持 + 新增 18：`TestXLangContextPool` 9（租借→求值→归还→再租借无残留/异常路径归还/可重入批求值/池耗尽阻塞/双重归还/归还后求值/关闭后租借/Engine 同一性/共享缓存 + SHARED 旗标）+ `TestTranslationCacheEviction` 5（容量淘汰 eldest/访问刷新新近度/淘汰后重翻译等价/配置缺省/非正数 fail-fast）+ `TestTranslationFailureEvents` 4（fail-fast 同时记事件/消费者通知/池句柄注册通道/直构无 language 纯 fail-fast））。

### Phase 3 执行记录（2026-08-21）

**11. 并发对拍落地**（`TestCorpusConcurrentTruffleColumn`，Phase 1 §7 裁定的独立并发测试类承载）：

- 同一编译单元并发：74 corpus 单元（v1 22 + 覆盖 A 33 + 覆盖 B 19，静态/动态按适用性）× 4 线程经池并发（barrier 同时起步，每线程独立 scope/输出缓冲/租约）；逐线程三层对拍 vs **解释器单线程基线**（同一棵树经 harness 解释器列执行的结果快照：返回值 typedEquals / 异常错误码 + SourceLocation 回映射 / scope 变量逐项 / 输出缓冲调用序列逐项 = **无跨 Context 串值断言载体**——任何串值表现为逐线程比对分歧）。
- 不同编译单元并发：4 线程 × corpus 单元分片（19 单元/线程）同时起步各自顺序求值——不同期望值的并发线程间串值直接暴露为该线程比对分歧。
- truffle 身份断言：执行产物源树 = 本单元编译树实例（全新翻译）或结构等价（**JVM 级共享翻译缓存命中**——键 = sourceKey + 树指纹，跨测试复用是 SHARED 缓存正确语义；执行中发现严格 assertSame 在共享缓存下伪红——同键旧条目结构等价但实例不同，修正为"实例同一或指纹同一"双通道断言，Phase 3 执行记录）。
- 共享激活行为证据：跨线程同键 → 同一 `TranslatedUnit` 实例（共享语言实例 + 翻译缓存）+ `isMultipleContextsInitialized()` 旗标置位断言；池空闲回收（leasedCount=0 / availableCount=THREADS）。

**12. 负测试与并发观测落地**：

- `TestPoolProtocolNegative` 5 用例（roadmap 验收第二项红/绿对照）：红灯 = 注入通道自证（currentContext + bindEvaluation 公共 API，与检测同通道）+ 注入残留 → 归还红灯 + Context 退役 + 剩余条目照常服务 + 仅输出缓冲残留同样红灯；绿灯 = 正常/异常路径归还无残留 + 连续三轮复用无残留。
- `TestConcurrentEvictionAndFailures` 2 用例：并发淘汰压力（4 线程 × 60 键，逐次结果必须与树字面值一致——错译/串用即数值错位）+ 并发翻译失败注入（4 线程 × 5 失败：fail-fast 各自抛出、事件 20/20 全记录无丢失、消费者计数一致、环形保留有界、载荷字段完整、fail-fast 主语义并发下保持）。
- 不泄漏断言复跑：`TestTruffleDependencyIsolation` 在全量 577 用例内全绿。

**13. Phase 3 验证**：`./mvnw test -pl :nop-xlang,:nop-xlang-java,:nop-xlang-truffle -am -T 1C` **1354/0/0**（nop-xlang 514/0/2-skipped（基线一致）+ nop-xlang-java 263/0/0（零变更）+ nop-xlang-truffle **577/0/0** = 477 基线 + 18 Phase 2 + 75 并发对拍 + 5 负测试 + 2 并发淘汰/观测）。

**14. I9 移交显式记录（责任链 repo-observable）**：

- **观测事件消费接口**：`TranslationFailureListener`（载荷 `TranslationFailureEvent`：sourceKey/节点类名/SourceLocation/原因/原始异常）——消费方 = I9 的 truffle 侧 SPI 适配器（Phase 1 §6 裁定），注册通道 = `XLangContextPool.getLanguage().addTranslationFailureListener(...)`（池预热捕获共享 Engine 的语言实例，`TestTranslationFailureEvents.testPoolLanguageHandleRegistersConsumer` 已验证该通道行为）；无消费者时内置记录器恒记可查询（`XLangLanguage.getTranslationFailures()`）。
- **池运行时生产接入点**：`XLangContextPool.open()/lease()/Lease.eval/close`（租借协议契约见类 javadoc 与 Phase 1 §3 定稿：注入复用 handoff 链路、enter/leave 批求值、归还残留检测、池耗尽有界阻塞）；共享 Engine = `XLangTruffleEngine.sharedEngine()`（创建失败显式 IllegalStateException——I9 注册不可用条目的判定信号）。
- I9 决策树动态路径第三分支的供给即上述两者；本 plan 未做路由/注册表/降级（I9 范围）。

## Execution Plan

### Phase 1 - 口径对齐与设计定稿（SHARED 切换面/池协议/共享 Engine/淘汰/观测事件/共享 AST 并发语义）

Status: completed
Targets: 本 plan Execution Notes 与当日 log（决策记录 repo-observable）

- Item Types: `Decision | Proof`

- [x] 前置核验：I7 已 `completed`（roadmap live 核验）；消费 I7 移交清单（Execution Notes §11 四项）与 I5 Deferred 责任链，逐项对齐为本 plan 执行项（Execution Notes §0）
- [x] **SHARED 切换面盘点与设计定稿**：注解取值变更点（`XLangLanguage.java:28`）与重新编译形态；`initializeMultipleContexts` 覆写面（按 Truffle SHARED 要求——依据 `~/sources/graal` SL 实现与 Truffle API 规范落锚点）；切换后 context-independent 准则的复验方式（既有全量测试保持全绿 = 翻译正确性不变；SHARED 特有风险由 Phase 3 并发对拍承载——两形态各自是验证载体的设计裁定延续）（Execution Notes §1）
- [x] **共享 AST 并发语义核实（真裁定项，执行侧 + 翻译侧）**：SHARED 下同一翻译 AST（含 `XFunctionDispatchNode` DSL 缓存状态转换、特化节点改写、探针计数器）被多 Context 并发执行的线程安全依据——以 Truffle DSL 规范/SL 源码（`~/sources/graal`）为锚点核实并在决策记录落据；探针计数器（I7 directCalls/genericCalls，live 为非原子 long）需裁定处置（原子化或并发写限制），不允许静默数据竞争；**翻译侧同标准**：共享 Engine 下不同 sourceKey 的 `computeIfAbsent` mapping 并行执行——`TranslationCache` 持单个共享 `ExecToTruffleTranslator` 实例，核实/处置其单实例并发 translate 无共享可变状态（无状态声明或实例化策略变更，二选一裁定并落断言）（Execution Notes §2）
- [x] **池租借协议契约定稿**：租借/归还 API 形态（落点包 `io.nop.xlang.truffle` 下新 runtime/eval 载体，类名 Phase 1 定）；租借注入（输出缓冲 + 全局作用域句柄）与 `XLangContext.bindEvaluation/clearEvaluation` 窗口协议的关系（复用/扩展裁定）；归还前清空实现位置（池实现自身清空 vs 借用者义务——设计契约为池侧保证）；残留检测机制形态（负测试载体：归还时断言 + 注入红/绿对照）；异常路径归还保证（try/finally）；**池耗尽语义**（阻塞等待 / 有界增长 / fail-fast——显式裁定，不隐含推导）；与 `EvalHandoff` 每求值注册的关系定稿（批量 = 多次 handoff 循环，不嵌套——现 fail-fast 语义保持）（Execution Notes §3）
- [x] **共享 Engine 单例形态定稿**：创建时机（惰性/饿死裁定）、创建失败语义（显式异常 fail-fast；"注册不可用条目 + 降级"归 I9，本 plan 不做路由）、Context 构建显式绑定共享 Engine、`enter()/leave()` 批求值可重入形态（polyglot API 嵌套 enter 语义核实落锚点）、池大小配置项（配置类落点=truffle 模块自身 config 类，varRef 模式同 `XLangConfigs` 先例；缺省值=随并发工作线程规模的 live 取值来源裁定，不发明数值）（Execution Notes §4）
- [x] **翻译缓存淘汰策略定稿**：容量上限/LRU 算法选型（live 平台既有 LRU 先例优先——`nop-commons` cache 系候选核实）、配置项与缺省值、并发安全（并发 getOrBuild + 淘汰）、淘汰可观测形态（计数器/日志级）、淘汰不影响正确性的验证口径（淘汰后重翻译结果一致）（Execution Notes §5）
- [x] **单元级翻译失败观测事件形态定稿**：事件载荷字段（sourceKey/节点类名/SourceLocation/原因）、消费接口签名（I9 第三分支回调）、默认实现（内存记录 + 可查询）、无消费者时的行为（显式非静默）、落点（truffle 模块 main，I9 经依赖方向消费）；**消费路径裁定（依赖方向张力显式化）**：I9 决策树裁决入口在 nop-xlang（不可反向 import truffle 模块）——本接口的消费方 = I9 的 truffle 侧 SPI 适配器（落 truffle 模块内，向上经注册表契约暴露），消费路径在决策记录中写明，防接口形态对 I9 不可见导致返工（Execution Notes §6）
- [x] 决策记录全部落 plan Execution Notes / 当日 log（repo-observable；**并发对拍 driver 承载形态**——扩展既有 harness 列机制 vs 独立并发测试类——随本 Phase 决策记录一并裁定，Phase 3 消费）（Execution Notes §7 + 当日 log）

Exit Criteria:

- [x] 前置核验记录 + 六项设计定稿全部 repo-observable（含共享 AST 并发语义（执行侧 DSL 缓存/探针 + 翻译侧 translator 单实例）的规范/源码锚点或处置裁定、负测试载体形态、观测事件接口形态与消费路径、并发 driver 承载形态裁定）
- [x] 池协议负测试载体与观测事件消费接口在决策记录中可审（Phase 2/3 消费）
- [x] No owner-doc update required（docs-for-ai 同步归 I11）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - SHARED 切换、共享 Engine、Context 池与缓存淘汰落地

Status: completed
Targets: `nop-kernel/nop-xlang-truffle/src/main/java/io/nop/xlang/truffle/`（lang/eval/runtime 载体）、`nop-kernel/nop-xlang-truffle/src/test/`

- Item Types: `Proof`

- [x] SHARED 切换：`@Registration` 注解取值变更 + `initializeMultipleContexts` 覆写（按 Phase 1 定稿）；**javadoc 口径同步**（以全模块 grep `EXCLUSIVE` 清单收口——live 命中含 `XLangLanguage`/`XLangContext`/`EvalHandoff`/`XLangRootNode`/`TruffleBackendColumn` 及 corpus 列测试等，逐一更新"过渡形态/归 I8"表述——javadoc 为本系列设计锚点，不允许漂移）；既有全部测试保持全绿（SHARED 超集形态验证——corpus 三列/矩阵/fail-fast/探针断言全量复跑）（Execution Notes §8-§10）
- [x] 共享 Engine 单例 + Context 池实现（Phase 1 契约）：租借/归还、enter/leave 可重入批求值、归还前清空、异常路径归还保证、池大小配置项（Execution Notes §8）
- [x] 翻译缓存淘汰落地（容量上限/LRU + 配置项 + 淘汰可观测；键语义与树指纹纪律不变）（Execution Notes §8）
- [x] 单元级翻译失败观测事件落地（载荷 + 消费接口 + 默认记录实现 + fail-fast 抛错路径同时记事件）（Execution Notes §8）
- [x] 单测：池协议正测试（租借→求值→归还→再租借无残留；异常路径归还后无残留）；enter/leave 可重入批求值；淘汰触发与淘汰后重翻译一致；观测事件记录与查询；共享 Engine 单例同一性（多池租借 Context 同一 Engine）（`TestXLangContextPool` 9 + `TestTranslationCacheEviction` 5 + `TestTranslationFailureEvents` 4）
- [x] 既有测试不削弱（纪律 3）；新代码无静默跳过（未实现路径显式 fail-fast）（Execution Notes §9 缺陷修复 + 协议违约 fail-fast 断言在仓）

Exit Criteria:

- [x] SHARED 形态在仓（注解取值 + 覆写面）且既有 477 基线测试全绿保持（全量复跑证据）——495/0/0（477 + 18 新增，全绿）
- [x] 池/Engine/淘汰/观测事件代码在仓且有对应单测（每新增功能同 Phase 测试——guide 规则 25）
- [x] 无静默跳过：池协议违约、Engine 创建失败、观测事件无消费者等路径显式失败或显式记录（有断言）
- [x] `./mvnw test -pl :nop-xlang-truffle -am` 全绿
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - SHARED 并发对拍、池协议负测试与回归收口

Status: completed
Targets: `nop-kernel/nop-xlang-truffle/src/test/`（并发对拍与协议负测试）

- Item Types: `Proof`

- [x] **SHARED 并发对拍（roadmap 验收第一项）**：多线程经池并发求值——同一编译单元并发（N 线程 × 同单元，每线程独立 scope/输出缓冲）与不同编译单元并发（线程 × corpus 单元分配）两形态；结果与单线程解释器求值逐线程比对（三层断言：返回值 equals 含类型 / scope 变量与输出缓冲逐项 / 异常语义错误码 + SourceLocation 回映射）+ truffle 身份断言（翻译 AST 经 CallTarget）+ **无跨 Context 串值断言**（各线程输出缓冲内容互不含他线程输出、scope 变量集互不污染）全绿；语料 = 既有 corpus 单元（静态 + 动态按适用性；并发 driver 承载形态——扩展既有 harness 列机制 vs 独立并发测试类——Phase 1 决策记录中一并裁定）（`TestCorpusConcurrentTruffleColumn` 75 用例全绿，Execution Notes §11）
- [x] **池租借协议负测试（roadmap 验收第二项）**：注入故意残留（绕过归还清空路径构造残留 context 状态）→ 残留检测机制红灯（红/绿对照——I1 差异注入自检先例）；正常归还 → 检测通过；异常抛出路径归还 → 无残留（`TestPoolProtocolNegative` 5 用例红/绿对照，Execution Notes §12）
- [x] 淘汰并发安全测试（并发 getOrBuild + 淘汰触发下结果一致、无错译/串用）；观测事件在并发翻译失败注入下被记录且可查询（`TestConcurrentEvictionAndFailures` 2 用例，Execution Notes §12）
- [x] 不泄漏断言复跑（`TestTruffleDependencyIsolation`）；三模块回归 `./mvnw test -pl :nop-xlang,:nop-xlang-java,:nop-xlang-truffle -am -T 1C` 全绿（1354/0/0，Execution Notes §13）
- [x] I9 移交显式记录：观测事件消费接口 + 池运行时生产接入点（责任链 repo-observable，落 plan Execution Notes + 当日 log）（Execution Notes §14）

Exit Criteria:

- [x] **并发对拍全绿（含身份断言 + 无跨 Context 串值断言）——roadmap I8 验收第一项**
- [x] **池租借协议正/负测试在仓（红/绿可控）——roadmap I8 验收第二项**
- [x] **端到端验证**：多线程入口 → 池租借 → enter → 翻译 AST CallTarget 执行 → leave → 归还 → 逐线程三层对拍断言全链可运行（stock JDK 21、SHARED 形态）
- [x] **接线验证**：并发测试真实经池驱动（非直接 new Context）；观测事件真实被记录且消费接口可取（非摆设接口）（`TestCorpusConcurrentTruffleColumn` 全部经池驱动 + `testPoolLanguageHandleRegistersConsumer` 通道行为验证）
- [x] 回归不削弱既有测试；三模块全绿；不泄漏断言通过
- [x] `./mvnw test -pl :nop-xlang,:nop-xlang-java,:nop-xlang-truffle -am -T 1C` 全绿
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] SHARED 形态并发对拍全绿（多线程经池 × 同一/不同编译单元 vs 解释器单线程基线 + 无跨 Context 串值断言）——roadmap I8 验收第一项
- [x] 池租借协议正/负测试在仓（归还后残留检测红/绿对照）——roadmap I8 验收第二项
- [x] SHARED 切换不重构注册结构（I5 D1 承诺）；既有测试全量保持全绿（翻译正确性载体不削弱）
- [x] 共享 Engine 单例 + 可重入批求值 + 归还清空协议落地（异常路径不丢清空）
- [x] 翻译缓存淘汰落地（键语义不变；淘汰后重翻译结果一致有测试）
- [x] 单元级翻译失败观测事件落地且 I9 消费接口显式（无静默路径）
- [x] 共享 AST 并发语义有规范/源码锚点依据或显式处置裁定（Phase 1 决策记录，覆盖执行侧 DSL 缓存/探针与翻译侧 translator 单实例）+ 并发对拍行为级验证
- [x] I5/I7 移交四项全部闭合（SHARED/池/并发对拍/缓存淘汰——Deferred 责任链收口）
- [x] 回归不允许削弱现解释器测试（纪律 3）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] owner-docs：No owner-doc update required（docs-for-ai 同步归 I11）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] Anti-Hollow Check：closure audit 已验证（a）并发测试真实经池与共享 Engine 驱动（b）观测事件/残留检测非摆设（c）无空方法体/静默跳过/no-op
- [x] `./mvnw compile -pl :nop-xlang-truffle -am`
- [x] `./mvnw test -pl :nop-xlang,:nop-xlang-java,:nop-xlang-truffle -am -T 1C`
- [x] checkstyle / 代码规范检查通过（`-Pqa checkstyle:check` 或 mission lint 口径）

## Deferred But Adjudicated

（无——起草时无新 deferred 项；Q1/Q4 watch-only 为设计 §九既定归属归 I12，见 Non-Blocking Follow-ups。执行中产生时按 guide 补录并写明 Why Not Blocking Closure。）

## Non-Blocking Follow-ups

- Q1（Bytecode DSL 重评估）/ Q4（与 nop-js 共享 Engine 重评估）维持 watch-only，触发口径量化归 I12（设计 §九既定归属，延续 I5/I6/I7 Follow-up）。
- Context 池创建/销毁成本与池大小调优实测归 I12（设计 §五"本层不发明数值"裁定保持）。

## Closure

Status Note: SHARED 终态切换在仓（`XLangLanguage` 注解取值 + `initializeMultipleContexts` 覆写，注册结构未重构——I5 D1 保持；477 基线全量在 577 内全绿）；共享 Engine 单例 + Context 池租借协议落地（租借注入复用 handoff 链路、enter/leave 可重入批求值、归还残留检测 + 防御清空 + 退役、池耗尽有界阻塞、打开预热接线自检、getLanguage I9 消费通道）；翻译缓存淘汰（LRU + 翻译锁外并行 + 淘汰可观测，键语义不变）；单元级翻译失败观测事件（fail-fast 重抛不变 + 内置记录器恒记 + 消费者接口，I9 移交在案）；并发对拍全绿（74 corpus 单元 × 4 线程经池 × 同一/不同单元两形态，逐线程三层对拍 vs 解释器单线程基线 + 身份断言 + 无跨 Context 串值 + 共享缓存同一性 + SHARED 旗标）；池协议红/绿对照在仓。三 Phase Exit Criteria 与 16 条 Closure Gates 逐条 PASS（独立 fresh closure audit CAN CLOSE，0 Blocker/0 Major/1 Minor=陈旧 target 调试报告已清理）。执行中四缺陷全部 scope 内修复（含一个先于本 plan 的存量编译缺陷）。docs-for-ai 同步归 I11（owner-doc 裁定在案）。
Completed: 2026-08-21

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh closure audit 子 agent（task `ses_fdf4c2c90ffeTAJ5NrAwayOC7M`，未参与实现，live repo 全量核验）
- Evidence:
  - Phase 1/2/3 全部 Exit Criteria：PASS（live 锚点——`XLangLanguage.java:42` SHARED 注解 / `:86-88` 覆写；`XLangContextPool.java:96-98` activate 状态机、`:283-311` Lease.close try/finally；`TranslationCache.java:78-81` 失败上报 + 原样重抛；`TestCorpusConcurrentTruffleColumn` 75/75 live 绿（三层 `:244-248/:283-333` + 身份 `:252-257` + 共享同一性 `:258-263` + SHARED 旗标 `:166-168`）；`TestPoolProtocolNegative` 5/5（红灯 `:84-88` + 绿灯 `:95-123`）；`TestConcurrentEvictionAndFailures` 2/2（事件 20/20 无丢失））
  - 16 条 Closure Gates：全 PASS（含 I5/I7 移交四项闭合对照 I7 plan §11 逐一核验）
  - Anti-Hollow：(a) 并发测试真实经池 + 共享 Engine（`pool.lease()` + `Context.newBuilder(...).engine(sharedEngine())` 绑定链 + `assertSame(context.getEngine())`）(b) 残留检测在 `Lease.close()` 真实执行 + listener 在 getOrBuild catch 真实触发（行为断言在案）(c) 四个新主类逐读无空方法体/no-op（唯一"无动作"路径 = language==null 跳过记录，显式裁定 + 专测覆盖）——全 PASS
  - 回归 live 复跑：nop-xlang 514/0/0(2skip，基线一致) + nop-xlang-java 263/0/0（零变更）+ nop-xlang-truffle **577/0/0**（477 基线 + 100 新增）；`-Pqa checkstyle:check` EXIT=0
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-xlang-truffle --severity high` 0 critical/0 high
  - Deferred 项分类检查：`Deferred But Adjudicated` 为空；Follow-up 仅 Q1/Q4 watch-only 与池/容量调优（设计既定归属 I12）——无 in-scope live defect 降级

Follow-up:

- Q1（Bytecode DSL 重评估）/ Q4（与 nop-js 共享 Engine 重评估）watch-only，触发口径量化归 I12（Non-Blocking Follow-ups 既定）
- Context 池创建/销毁成本与池大小/缓存容量调优实测归 I12（设计 §五"本层不发明数值"保持）
- I9 消费义务：观测事件消费接口（`TranslationFailureListener`，注册通道 `XLangContextPool.getLanguage()`）+ 池运行时生产接入点（Execution Notes §14 移交记录）
