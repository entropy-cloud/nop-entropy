# C10 测试保护力审计 — CRA WI 6.2 nop-tools 解析面并发审计（三维最终保留项 21 实体）

> **审计单元**: 三维（C1/C6/CS）最终保留项 21 实体——C1×6（C1-001/101/102/103/104/105）、C6×11（C6-001~006/101~105）、CS×4（CS-001/002/003/R2-001）；其中 **C1-102、CS-001 为归属改判条目，缺陷本体挂 C6-101 主登记**（NodeTscBridge 三方归并），本矩阵按 21 行一一对应登记。
> **基线 / live code**: 0d131797dd（git HEAD 实测一致）；测试面 = 四模块 `src/test`（nop-treesitter / nop-jq / nop-lint 六子模块 / nop-refactor 三子模块）+ lint/refactor `_vfs` 测试 fixture。
> **方法**: 对每实体定位其 main 缺陷站点的全部既有测试断言面（逐文件读取测试源码核断言语义，非仅 grep 文件名），回答唯一问题——**「该缺陷被引入的瞬间，现有测试是否会变红？」**。缺陷均已在 live code 存在且测试当前全绿，故判定实质为：现有断言是否覆盖缺陷违反的不变量（并发时序 / 失败路径 / 生命周期 / 契约文本），还是只覆盖缺陷存在时仍成立的终态功能语义。
> **关键背景事实（套件级）**: ① 四模块测试无 `junit-platform.properties`（find 命中 0）→ JUnit 默认顺序执行，**纯并发型缺陷在套件内结构性不可自发显现**；② 全部测试中含并发构造（ExecutorService/new Thread/CountDownLatch/CompletableFuture/parallelStream）的仅 2 文件——`TestSha256HexEncoding`（SHA256 原型 clone 压力）、`TestTreeSitterNodeCache.concurrentFirstWalksCooperate`（TreeCache 并发首遍历），两者对应的载体在三维审计中均已裁定**安全**（C1 批 B 载体表 #18/#8），**不在 21 实体内**——即 21 实体中并发型缺陷的并发场景测试覆盖为 0，且套件已有并发测试先例（缺的是对这 21 处的使用）。
> **jq 面说明**: 21 实体中无 nop-jq 立案（jq 仅 CL-001 线索移交 C5/C7，非立案），jq `src/test` 断言面（TestJqResourceGuards 等）不进入矩阵；矩阵 21 行全部落在 nop-treesitter / nop-lint / nop-refactor。
> **盲审声明**: 本报告为 C10 独立子代理产出。执行期间仅读取三维最终审计文件（C1-shared-mutable-state.md / C6-resource-lifecycle.md / CS-sweep.md）与四模块 live code + `src/test` 源码；**未读取** `ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`、`ai-dev/backlog/`、`ai-dev/audits/` 下其他任何文件（含本目录 dispatch-log.md）。除本报告外未修改任何文件；未修改产品代码与三维审计文件。
> **判定口径**: 「会红」= 存在确定性与缺陷违反的不变量绑定的断言；「不会红」= 现有断言在缺陷引入前后均绿；「不会可靠红（假保护）」= 存在表面上相关的测试，但其通过依赖于缺陷自身的时序不触发，或仅钉定缺陷旁路行为。
> **Status**: reviewed（初审 open → C10 独立复核 2026-10-22 判定 **PASS with corrections**：抽样 15 行独立验证 + 反向保护 4/4 重演 + hang-once-then-ok 假保护重演通过；修正 2 处——缺保护登记分桶补正（17→18 项）、#10 fNode 证据式——详见文末复核结论）

---

## 一、21 行测试保护力矩阵

| # | 实体（严重度） | 现有测试断言面（file:line） | 会红? | 判定依据 |
|---|---|---|---|---|
| 1 | C1-001（P2）provider 语言缓存 get→load→put 竞态 | `TestTreeSitterProvider`（biz/TestTreeSitterProvider.java:36-108：顺序 getLanguage 功能面）；`TestCustomGrammarProvider.java`（顺序） | **不会红** | 竞态需多线程并发首触同名语法；测试全单线程（treesitter tests `assertSame` 命中 0——无跨调用实例身份断言）；无并发首触用例。顺序加载被破坏时功能面会红，但那不是本缺陷 |
| 2 | C1-101（P1）LintDeadlineExecutor.install 全局槽丢更新 | `TestLintDeadlineExecutor`（xscript/TestLintDeadlineExecutor.java:74-270：install 幂等 :74-96、透传 :100-120、超时中止 :124-155、深度护栏 :225-270，全部单线程驱动真实全局槽） | **不会红** | 缺陷 = install 与**第二全局写者**（调试器 attach/detach）跨 monitor 丢更新；无任何测试模拟并发第二写者（文件内 0 个 Thread/ExecutorService）。**反向锁定注记**：:84-92 钉定「install 必须拥有全局槽」，按 C1-101 建议（scope 传播/显式下传）修复会使本测试红——修复需同步改测试 |
| 3 | C1-102（P1，**归属改判 → C6-101 主登记引用**） | 同 #13（NodeTscBridgeTest） | **不会可靠红** | 挂 C6-101 主登记；诊断误导侧写（误记死因）无任何断言面 |
| 4 | C1-103（P2）TscTypeResolver.bridge 非 volatile 无锁读 | `TscTypeResolverTest.java:31-101`（探针无副作用 :31-38、close 后不可用 :94-101，全单线程） | **不会红** | stale 可见性偏离需并发读-写交叠；无并发用例。且「在编未接线」（main 0 构造点），消费面在测试内 |
| 5 | C1-104（P2）LanguageRegistry HashMap 守卫非原子 | `TestLanguageRegistry.java:74-95`（**顺序**冲突 fail-closed :74-84、同实例幂等 :87-95） | **不会红** | 顺序冲突检测有保护（删守卫会红）；缺陷是**并发**双注册窗口 + registeredIds 迭代交叠 CME——并发用例 0。缺陷活在嵌入契约面，测试全部走库内安全形态 |
| 6 | C1-105（P2）RuleResultCache 固定 .tmp 跨进程踩踏 | `TestNopLintCliCache.java`（单 JVM 顺序冷/热/失效矩阵 :77-202）；`TestRuleResultCacheCorruptEntry.java`（入口级 fail-closed :41-81） | **不会红** | 踩踏需两个进程共享 `--cache`：`grep -rln "ProcessBuilder" nop-lint/*/src/test nop-refactor/*/src/test --include="*.java"` → **0 文件**（唯一命中为 `_vfs` fixture 资源）；无双进程/并发 save 用例；无 `.tmp` 残留断言（`grep -rn '"\.tmp"'` → 0） |
| 7 | C6-001（P2）EditPlanApplier 守卫后置（写盘→守卫窗口） | `TestEditPlanApplier.java:106-120`（rollback 后**终态**断言 restore）；`TestFixApplier`/`TestOperationFrameworkWiring.java:115-125`（同终态面） | **不会红** | 测试在 `apply()` 返回后读文件——写盘（:89）→守卫（:92-94）→回滚写（:96）的**中间窗口**对终态断言不可见；kill -9 变体无模拟（`grep -rn "kill\|interrupt\|Thread.sleep" fix/refactor-core tests` → 0）。缺陷引入前后终态断言均绿 |
| 8 | C6-002（P2）RefactorOperationRunner「lands atomically」契约矛盾 | `TestNopRefactorBizModel.java:178-200`（钉定部分落地行为：异常含 "already landed" + 已落地文件枚举 + 已落地保新内容） | **不会红** | 契约文本矛盾（javadoc :45 vs :30）不可被行为测试观测；且 :200 **反钉**非原子语义为预期——测试站在实现一侧，javadoc 侧无保护。崩溃恢复语义未声明面亦不可测 |
| 9 | C6-003（P3）ScannerVM POOL set-无-remove 常驻 | `ThreadLocalRetentionTest.java:69-98`（断言 source/program/validSymbols 引用置空） | **不会红（反向保护）** | 引用面有保护；**常驻面被钉定为预期**——:79 `assertNotNull(vm, "scanner VM should be pooled for reuse")` 与 :47（SCRATCH cursor 同）把「跨请求池化常驻」断言为设计目标，按 C6-003 建议（NamedThreadLocal 可清理 / 每 parse 实例化）修复会使本测试红——修复需同步改测试 |
| 10 | C6-004（P3，降级后）SCRATCH 游标 release 不缩容 | `ThreadLocalRetentionTest.java:40-50`（仅 tree/nav 引用 null 断言）；`ScratchStatsTest.java`（arena 统计打印，无断言） | **不会红** | 五列 frame（fNode..fAlias）容量无任何断言（`grep -rn "fNode.length\|fAlias" nop-treesitter/src/test` → 0；字面 `grep -rn "fNode"` 命中 1 处为 CursorNavigationTest.java:281 方法名 `leafNodesHaveNoChildren` 子串、非容量断言——C10 复核修正证据式）；峰值容量驻留对引用断言不可见 |
| 11 | C6-005（P3）SubtreeArena free() 死代码 + adopt 保留放大 | `SubtreeArenaTest.java:30-58`（**测试侧**调用 free/freeList 钉定机制语义）；`SubtreeArenaIntegrationTest`（结构面） | **不会红（反向保护）** | 无「parse 主路径释放/保留率」断言（main 面 `.free(` 零调用方为 C6 复核实证）；free() 机制单测反而使「删除死代码」修复选项 (a) 会红——修复取 (b)/(c) 或同步删测试 |
| 12 | C6-006（P3）atomicWrite 无 fsync | `TestEditPlanApplier.java`（原子性/失败 fail-closed 面，无持久性断言） | **不会红** | 掉电级持久性不可测（`grep -rn "fsync\|StandardOpenOption.SYNC\|force(" lint/refactor tests` → 0）；javadoc durable 声明缺失为文档面，无断言 |
| 13 | C6-101（P1，**NodeTscBridge 三方唯一主登记**，吸 C1-102/CS-001） | `NodeTscBridgeTest.queryDeadlineIsAStructuredTimeoutAndRecyclesThePeer`（NodeTscBridgeTest.java:109-129，即 C6 批 B 点名的 hang-once-then-ok）；辅面 :134-148（crash-once）、:151-169（预算耗尽） | **不会可靠红（假保护）** | hang-once-then-ok 性质核定为**时序敏感的功能恢复测试、非并发缺陷检测**：单线程、单次重启、单查询；其通过恰因挂起 peer **零字节写出**且本地 SIGKILL→EOF 先于下一次 spawn 的 `clear()`——即**依赖缺陷的竞态窗口不命中才绿**。分支 A（stale 数据帧杀健康新进程）零覆盖（无「先 flush 响应再挂起」的 peer 模式）；分支 B（stale EOF→EXHAUSTED）若竞态命中会使 :125 的后续查询断言红，但纯概率性、不可依赖；测试亦未断言「无 stale 帧」「restart 预算消耗数」「无孤儿进程」（`grep -rn "destroyProcess\|destroyForcibly\|isAlive\|stale" nop-lint-js tests` → 0） |
| 14 | C6-102（P3，降级后）save 失败 temp 无清理 + BaselineFile 非原子 | `TestRuleResultCacheCorruptEntry.java:31-38`（**成功路径** save/load）；`TestCliBaseline.java`（顺序 write/apply/check 生命周期）；`TestBaselineEngine` | **不会红** | move 失败路径无模拟（`\.save()` 测试命中 3 处均为成功路径）；`.tmp` 残留断言 0；BaselineFile 撕裂写无用例（`grep -rn "truncat\|torn\|partial\|half"` → 0）；load 侧 fail-closed 有覆盖（corrupt cache），但那是缺陷存在时仍成立的行为 |
| 15 | C6-103（P3）握手 EOF 分支不销毁进程 | `NodeTscBridgeTest.peerDyingBeforeReadyIsAnExplicitUnavailableState`（:68-75：断言异常 + isAvailable false） | **不会红** | 只断言不可用状态可见，**不断言进程句柄/fd 被回收**（grep destroyProcess/isAlive → 0）；句柄滞留对行为断言不可见 |
| 16 | C6-104（P3）decode BAD_FRAME 不回收进程 | `NodeTscBridgeTest.unparseablePeerFramesAreProtocolViolations`（:95-106：只钉 BAD_FRAME 抛出，配 maxRestarts=1 且无后续查询） | **不会红** | 回收语义（预算消耗/进程回收时点）未钉定——C6 复核同判定（「只钉抛错不钉回收语义」）；:102 的 code 断言在缺陷存在时成立 |
| 17 | C6-105（P3）TscTypeResolver 所有权无执行者 | `TscTypeResolverTest`（resolver 自身 close 语义 :94-101）；`TestRuleTestRunner`（close/TypeResolver 实义命中 0；`grep -rn "AutoCloseable" lint/refactor tests` → 0） | **不会红** | 「持有 resolver 的 runner 必须关闭它」契约无保护力测试（C6 复核同判定）；今日 main 亦无接线（在编未接线），缺陷不可触发故测试不可能红 |
| 18 | CS-001（P1，**归属改判 → C6-101 主登记引用**） | 同 #13 | **不会可靠红** | 挂 C6-101 主登记；CS-001 变体 2（尾行 correlation id 失配烧预算）无专属用例（无「先写响应帧再挂起」peer 模式） |
| 19 | CS-002（P3，降级后）JavaTypeResolver 查询缓存无界 | `TestJavaTypeResolver.java:126-154`（缓存幂等 :126-137、跨文件隔离 :141-154） | **不会红** | 断言的是缓存**正确性**非**有界性**：`grep -c "LRU\|removeEldest\|CACHE_SIZE\|evict\|bound"` → 0；增长缺陷引入前后断言均绿。且「在编未接线」，L2 生产面未接 |
| 20 | CS-003（P3）XmlTagKinds.idFor 并发同 id | `XNodePatternMatcherTest.java:324-328`（-1 形态 + entity round-trip，顺序） | **不会红** | 竞态需不同键并发首见；套件顺序执行 + 无并发用例；:327-328 的 round-trip 断言在缺陷存在时（intern 完成后）恒绿 |
| 21 | CS-R2-001（P2）error 帧无 id 校验 / BAD_FRAME 不回收 / 协议无版本 | `NodeTscBridgeTest.errorFramesAreStructuredFailuresWithTheirCode`（:80-92：单飞行**顺序** error 帧）；`TscProtocolTest.java`（协议面 8 用例，无 id-mismatch-on-error / 版本字段用例，`grep -rn "protocolVersion\|version" tsc tests` → 0） | **不会红** | 全部 error 帧用例走「队首帧=当前应答」成立的顺序路径；去同步偏移（陈旧 error 帧、半帧、错配 id）无用例；FAIL-visible 误归因（errorOf 用在途 id 拼消息）无断言 |

**矩阵统计**: 21 行 = **0 行会红**；18 行不会红；3 行不会可靠红（#3/#13/#18，同属 C6-101 一缺陷的假保护）。其中 4 处**反向保护**（修复会红、需同步改测试）：#2（全局槽设计被钉定）、#8（部分落地语义被反钉）、#9（池化常驻被钉定）、#11（free() 死代码被机制单测钉定）。

---

## 二、缺保护登记

### P1（逐条，2 项缺陷）

**M-P1-1. C6-101（NodeTscBridge 重启无栅栏，三方主登记）——hang-once-then-ok 假保护**
- 缺口性质：现有唯一相关测试（NodeTscBridgeTest:109-129）通过依赖「挂起 peer 零字节输出 + SIGKILL→EOF 快于 clear」这一缺陷自身的时序不触发；分支 A（stale 数据帧）零覆盖，分支 B（stale EOF）仅概率性可红，未断言无 stale 帧 / 预算消耗语义 / 孤儿进程。
- 建议测试形态：FakeTscPeer 增设确定性模式——① `respond-then-hang`（先 flush 完整响应帧再 park 至超时）复现分支 A，断言 re-spawn 后 initProject 成功且 restartsLeft 消耗 ≤1；② `delayed-eof`（kill 后由 fake/代 Perl 延迟关闭 stdout）复现分支 B，断言握手不被 stale EOF 毒化；③ 修复落地后以 50–100 次循环重放钉定（并发缺陷保护力测试需压力次数达成确定性）；④ 断言 respawn/close 后无存活 node 进程（process.isAlive()=false，封 C6-103/CS-001 孤儿面）；⑤ C1-102 并入侧写：断言失败异常消息与真实死因一致（不误记 "peer exited"）。

**M-P1-2. C1-101（LintDeadlineExecutor.install 全局槽丢更新 + 永久驻留）**
- 缺口性质：install 相关 10 处测试命中全部单线程；无第二全局写者并发交错用例；无「lint run 结束按所有权还原」断言（还原仅存在于测试自身 finally）。
- 建议测试形态：① 双线程交错测试——CyclicBarrier 强制 T1 `install()` 与 T2 `registerGlobalExecutor(other)` 交叠，断言两写者均在链上（链扫描语义）或后者不丢（volatile 槽 + 原子 RMW 后）；② 卸载契约测试——install→run→restore 后 `getGlobalExecutor()` 恢复安装前快照且全链无 LDE 残留；③ 与调试器 attach/detach（XLangDebuggerInitializer）的组合用例。注意反向锁定：:74-96 钉定全局槽设计，Route-B（scope 传播）修复须同步重写本测试类。

### P2（逐条，7 项缺陷；C10 复核修正：7 行 P2 每行一个独立缺陷，初审漏登 C6-001、且误将 C6-002 置入 P3 表）

**M-P2-1. CS-R2-001（error 帧无 id 校验 / BAD_FRAME 不回收 / 协议无版本）**
- 建议测试形态：TscProtocolTest 层加纯单元用例——error 帧携带非在途 id 时 `request()` 应回收并抛错（现直接上抛他帧错误）；崩溃半行 JSON（BAD_FRAME）后下一查询应触发回收而非消费迟到帧；ready 帧版本字段用例（协议加版本后 validateReady 拒绝错配）。FakeTscPeer 增设 `error-stale-id` / `half-frame-then-die` 模式。

**M-P2-2. C1-001（provider 缓存复合操作竞态）**
- 建议测试形态：N 线程（latch 齐发）并发首触同一**未加载**语法名，`assertSame` 收敛到单一实例（套件级参数化语法名避免缓存预热干扰；修复为 computeIfAbsent 后确定性成立，修复前以压力次数提高命中率）。

**M-P2-3. C1-103（TscTypeResolver.bridge 非 volatile 探针失真）**
- 建议测试形态：并发可见性难以确定性断言，建议以结构断言替代——反射断言 `bridge` 字段 `Modifier.isVolatile`（防回归元测试）；或 isAvailable 收锁后以「close 与 isAvailable 并发，断言不返回已 close 桥的可用」压力用例。随「在编未接线」现状，优先级低于 M-P2-1/2。

**M-P2-4. C1-104（LanguageRegistry 并发注册守卫非原子）**
- 建议测试形态：契约面双线程压力测试——两线程对同一 id 注册不同实例 ×M 次，断言冲突**必**被检出（fail-closed 零胜者通吃）；`registeredIds()` 迭代与并发 put 交叠无 CME（CHM 化后确定性成立）。保留现顺序冲突测试（:74-84）不弱化。

**M-P2-5. C1-105（RuleResultCache 跨进程 load-modify-save 无互斥）**
- 建议测试形态：① 双 JVM 集成测试（同 `--cache` 路径并发 check run，断言双方退出码 0 或明确降级、无 NoSuchFileException 虚假失败）；② 轻量替代——单测内手工预置 `X.tmp` + 并发 save 线程对固定名修复（createTempFile 唯一名）做回归钉定；③ save 失败注入（目录只读）后断言 run 退出码与错误归因。

**M-P2-6. C6-001（EditPlanApplier 守卫后置：写盘 → 守卫窗口；C10 复核补登，初审漏登）**
- 建议测试形态：① 失败注入钉定守卫先行——目标目录只读/占锁注入后断言「守卫拒绝时零文件落盘」（现实现写盘 :89 先于守卫 :92-94，此用例修复前红、修复后绿，即真正的保护力测试）；② 结构断言——将 apply 路径「写盘 vs 守卫」调用次序以 spy/可注入接缝可观测化后，钉定「守卫先于写盘」；③ rollback 中间窗口（kill -9）不可确定性模拟，登记 watch（与 C6-006 durable 面同批）。

**M-P2-7. C6-002（RefactorOperationRunner「lands atomically」契约矛盾；C10 复核自 P3 表归位）**
- 建议测试形态：以 TestNopRefactorBizModel:176-206 为基，若裁定 (a) 批级原子则加 mid-apply 失败后「零文件变更」断言；若裁定 (b) 则把 landed 列表机器可读字段（RefactorResult payload）纳入断言并改 javadoc（文档面，无行为测试可加）。注意反向保护：:200-202 已反钉非原子语义为预期，裁定 (a) 落地须同步改写本测试。

### P3（聚合，10 项缺陷；修复批次落地时同批补测）

| 缺口 | 建议测试形态（一行） |
|---|---|
| C6-003 POOL 常驻 | 池生命周期断言（NamedThreadLocal 可观测名 / 每 parse 实例化后无跨请求残留）；**须与 :79 assertNotNull 同步改写**（反向保护） |
| C6-004 frame 不缩容 | 深树（~5000 锚点）访问后反射断言 `fNode.length` 回落初始容量 8（release 重置语义钉定） |
| C6-005 free 死代码 | 裁定后：删除则同步删 SubtreeArenaTest free 用例；保留则加 owner doc 声明断言面；保留放大问题移交 C5 后补保留/活树比 probe（非阻塞） |
| C6-006 无 fsync | javadoc「atomic but not durable」声明落地为文档面；行为级 durable 断言不可测，登记 watch |
| C6-102 temp 清理 / BaselineFile | move 失败注入（占锁/只读）后断言无 `X.tmp` 残留；BaselineFile 撕裂写（截断文件）load fail-closed 用例 |
| C6-103 握手 EOF 不销毁 | EOF 分支后反射断言 `process`/`peerInput` 已清（或 process.isAlive()=false），与 :224 对照面成对 |
| C6-104 BAD_FRAME 不回收 | garbage-response 用例追加：BAD_FRAME 后下一查询应消耗恰 1 个 restart 槽（现 maxRestarts=1 恰好可断言第二次查询即 EXHAUSTED 与否） |
| C6-105 所有权无执行者 | L2 装配落地同批：「runner 持 resolver 时 run 结束/close 后 bridge 进程销毁」保护力测试（C6 复核已建议） |
| CS-002 查询缓存无界 | L2 Java 接线同批：LRU 化后「超出容量后 size 收敛 + 命中率语义不变」断言（比照 filesByPath LRU32） |
| CS-003 XmlTagKinds 并发同 id（C10 复核移交主 agent 补登） | 专用锁修复落地同批：双键并发首见断言 id 互异且 dense-id 无烧号（AtomicInteger 方案则加 compute 重试烧号检查） |

---

## 三、汇总

- **矩阵规模**: 21 行，与三维最终保留项一一对应（C1×6 + C6×11 + CS×4；C1-102/CS-001 标注归属改判、挂 C6-101 主登记）。
- **会红/不会红**: **0 行会红**——18 行不会红，3 行（C1-102/C6-101/CS-001 同一缺陷）不会可靠红（hang-once-then-ok 假保护：单线程功能恢复测试，通过依赖竞态窗口不命中，分支 A 零覆盖）。**两个 P1 缺陷（C6-101 族、C1-101）的并发场景测试覆盖均为 0**。
- **P1 保护力结论**: C6-101 与 C1-101 均**无有效测试保护**；C6-101 现有唯一相关测试为时序敏感假保护（性质核定见矩阵 #13），C1-101 现有测试反而钉定缺陷所在的全局槽设计（反向锁定，修复需同步改测试）。
- **缺保护登记数**: P1×2、P2×7（逐条）、P3×10（聚合）＝**19 项缺口**（C10 复核修正 17→18；merge 裁定移交主 agent 补登 CS-003 → 18→19）（C10 复核修正：初审 17 项系漏登 C6-001、且把 P2 的 C6-002 误置 P3 表所致），全部附建议测试形态。另登记**反向保护 4 处**（#2/#8/#9/#11）：按三维审计建议修复时将使现有测试变红，修复工单必须捆绑测试改写。
- **套件级结构性事实**: 无 JUnit 并行配置 + 并发构造测试仅 2 文件（且均对应已裁定安全站点）——并发缺陷保护在四模块测试体系中是系统性缺口而非个案；`TestTreeSitterNodeCache.concurrentFirstWalksCooperate` 证明套件具备写并发保护力测试的先例与设施。
- **修复优先级建议**: M-P1-1（C6-101 确定性 peer 模式）> M-P1-2（C1-101 交错测试）> M-P2-1（CS-R2-001 协议面）> M-P2-2~7；P3 十项随各修复批次同批落地，不单独立项。

---

## 自我复核（C10）

- [x] 矩阵行数 = 21：C1 行 6（#1-6）+ C6 行 11（#7-17）+ CS 行 4（#18-21），与三维文件最终保留项逐条对上（C1-001/101/102/103/104/105 ↔ #1/#2/#3/#4/#5/#6；C6-001~006/101~105 ↔ #7-#12/#13-#17；CS-001/002/003/R2-001 ↔ #18/#19/#20/#21）。
- [x] 归属改判条目（C1-102 → #3、CS-001 → #18）已标注「归属改判 → C6-101 主登记引用」且判定随主登记；C6-101（#13）标注三方唯一主登记。
- [x] 严重度取三维复核终态（C6-004/CS-002/C6-102 为降级后 P3；CS-R2-001 P2）。
- [x] 盲审纪律：执行期间未读取 plans/bugs/lessons/backlog 及 audits 下其他文件；未修改产品代码与三维审计文件；本报告为唯一新增文件。
- [x] 所有「无相关测试」判定均附 grep 命令与命中数（正文各行列示；套件级反证：ProcessBuilder in tests=0 文件、并发构造=2 文件、junit-platform.properties=0、fsync/SYNC=0、`.tmp` 断言=0、AutoCloseable=0、protocolVersion=0、fNode.length/fAlias=0（初审字面 `fNode=0` 经复核修正为 1 命中——方法名子串，见 #10）、LRU/bound=0、assertSame in treesitter=0、kill/interrupt=0）。

---

## 复核结论（C10 独立复核，2026-10-22）

**整体判定：PASS with corrections。** 21 行矩阵的「会红/不会红/不会可靠红」定性、反向保护 4 处定性、套件级结构性事实全部独立重演成立；发现并修正 2 处登记性错误（不影响任何行的红/绿判定）。

### 一、抽样结果（15 行独立 grep/读源验证，覆盖要求全部满足）

| 抽样行 | 验证方式 | 结果 |
|---|---|---|
| #2 C1-101（P1，反向保护） | 全读 TestLintDeadlineExecutor.java（302 行） | ✅ install :74-96 / 透传 :101-120 / 超时 :125-155 / 深度 :226-270 全单线程，文件内 0 Thread/Executor；反向锁定断言实证于 :84-85（`"install must own the global executor slot"`） |
| #13 C6-101（P1，假保护） | 全读 NodeTscBridgeTest.java + FakeTscPeer.java + 桥 main | ✅ hang-once-then-ok :109-129 单线程/单重启（maxRestarts=1 :114）/单查询；FakeTscPeer :70-73 先 touchMarker 后 `parkForever()`（:153-159）——挂起前零字节写出、无 `respond-then-hang` 模式，分支 A（stale 数据帧）无可触发 peer；grep destroyForcibly/isAlive/stale in nop-lint-js tests → 0；桥侧 :197 `inbound.clear()` / :279 `destroyForcibly()` 与「SIGKILL→EOF 先于下次 spawn clear」的竞态分析吻合。**假保护定性成立** |
| #3/#18（C6-101 改判引用行） | 同 #13 测试面 | ✅ 判定随主登记一致 |
| #1 C1-001（P2） | 读 TestTreeSitterProvider :30-108 + grep | ✅ 顺序功能面，treesitter tests `assertSame` → 0，无并发首触用例 |
| #5 C1-104（P2） | 读 TestLanguageRegistry :64-109 | ✅ :74-84 顺序冲突 fail-closed、:87-95 同实例幂等，全单线程；「删守卫会红」的顺序面保护与「并发窗口无保护」并存成立 |
| #6 C1-105（P2） | 读 TestNopLintCliCache :74-202 骨架 + TestRuleResultCacheCorruptEntry :25-45 + grep | ✅ 单 JVM 顺序冷/热/失效矩阵；`ProcessBuilder` in test .java → 0 文件（唯一命中 `_vfs` fixture 资源）；`.tmp` 字符串断言 → 0 |
| #8 C6-002（P2，反向保护） | 读 TestNopRefactorBizModel :160-215 | ✅ :176-206 钉定 "already landed" + 落地文件枚举，:200-202 反钉「落地文件保留改写」= 非原子语义为预期——反向保护成立 |
| #21 CS-R2-001（P2） | 读 NodeTscBridgeTest :80-92 + TscProtocolTest 全文 + grep | ✅ error 帧用例走顺序路径（:84-88 单飞行）；TscProtocolTest 8 用例无 stale-id-on-error、无版本字段用例（`protocolVersion\|version` → 0）；errorOf 以在途 id 调用（TscProtocolTest:72）恰为误归因不可辨面 |
| #9 C6-003（P3，反向保护） | 全读 ThreadLocalRetentionTest.java | ✅ :79 `assertNotNull(vm, "scanner VM should be pooled for reuse")` + :47 SCRATCH 同款——池化常驻被钉定为设计目标，引用置空断言（:48-49/:80-82/:95-97）保护的是另一面。反向保护成立 |
| #10 C6-004（P3） | 读 ThreadLocalRetentionTest :39-50 + ScratchStatsTest 全文 + grep | ✅ 仅 tree/nav 引用断言；ScratchStatsTest 纯 println 无断言；frame 容量断言 0（证据式修正见下） |
| #11 C6-005（P3，反向保护） | 全读 SubtreeArenaTest.java（175 行） | ✅ :30-59 测试侧调用 free/freeList 钉定机制语义（删 free() 死代码修复会使 :30-46/:49-59 红）；全文无 parse 主路径释放/保留率断言。反向保护成立 |
| #19 CS-002（P3） | 读 TestJavaTypeResolver :120-160 + grep | ✅ 断言缓存正确性（幂等 + 跨文件隔离），`LRU\|removeEldest\|CACHE_SIZE\|evict\|bound` → 0 |
| #20 CS-003（P3） | 读 XNodePatternMatcherTest :315-339 | ✅ :324-329 -1 形态 + entity round-trip，顺序执行下缺陷存在时恒绿 |
| #15/#16（C6-103/104） | 读 NodeTscBridgeTest :67-106 | ✅ :68-75 只断言不可用状态（无进程句柄断言）；:95-106 只钉 BAD_FRAME 抛出（maxRestarts=1 且无后续查询，回收语义未钉） |

**hang-once-then-ok 假保护定性重演结论**：该测试为单线程时序敏感的功能恢复测试——其通过依赖「挂起 peer 零字节写出 + destroyForcibly→EOF 先于 re-spawn 的 `inbound.clear()`」这一缺陷自身竞态窗口不命中；分支 A 零 peer 覆盖、分支 B 概率性、无 stale 帧/预算消耗/孤儿进程断言。定性「不会可靠红」**维持**。

### 二、数字核对

- **会红 0 / 不会红 18 / 不会可靠红 3**：逐行复核 21 行判定列，3 行不会可靠红 = #3/#13/#18（同一 C6-101 缺陷）✅ 与统计一致。
- **缺保护登记构成：初审「P1×2 + P2×5 + P3×10 = 17 项」有误，经复核修正 + merge 裁定补登 CS-003，终态 P1×2 + P2×7 + P3×10 = 19 项**。错误两处：① C6-001（P2，矩阵 #7，不会红）完全未进入缺保护登记（与「全部附建议测试形态」声明矛盾）→ 已补登 M-P2-6；② C6-002（P2，矩阵 #8）的建议测试形态被误置入 P3 聚合表 → 已归位 M-P2-7 并从 P3 表移除。修正后 21 行 ↔ 18 独立缺陷（P2 的 7 行每行一个独立缺陷，初审「7 行中 5 行独立缺陷」表述不成立）全部有登记。
- **反向保护 4 处**（#2/#8/#9/#11）：4/4 重演成立 ✅（#2 :84-85、#8 :200-202、#9 :79/:47、#11 :30-59）。
- **套件级事实**：junit-platform.properties=0、并发构造恰 2 文件（TestSha256HexEncoding / TestTreeSitterNodeCache，均属已裁定安全载体）✅ 重演一致。

### 三、修正记录

1. **#10 行证据式修正**：初审 `grep -rn "fNode" nop-treesitter/src/test → 0` 失实（字面命中 1 处：CursorNavigationTest.java:281 方法名 `leafNodesHaveNoChildren` 子串）；实质结论（五列 frame 容量无任何断言）不变，已改用 `fNode.length\|fAlias` → 0 并注明子串命中。同步修正自我复核清单中的 `fNode=0` 记载。
2. **缺保护登记分桶修正**：P2 节改为 7 项逐条（补 M-P2-6 C6-001、增 M-P2-7 C6-002 自 P3 表归位），P3 聚合改 9 项，汇总缺口数 17 → 18。

无其他错误：抽样 15 行的测试文件/行号引用、grep 反证、缺陷站点描述均与 live code（0d131797dd）一致；三维最终保留项 ↔ 21 行映射与归属改判标注（C1-102→#3、CS-001→#18，挂 C6-101 主登记）核对无误。本次复核仅修改本文件；未修改产品代码与三维审计文件。
