# duckdb 引擎生命周期竞态、CTAS 守卫与重放契约、xlsx 临时文件配对、图算法包络与超时、Edge 可见性契约（CRA 8.36 R3-06 设计裁定）

> Status: active（final-state 写法——本文只记录当前生效的设计决策与使用契约；设计评审 R1 PASS @ 2026-11-03）
> Last Reviewed: 2026-11-03
> Source: plan 2529（CRA Phase 8.36 R3-06 5.6 graph-duckdb 批）；审计目录 `ai-dev/audits/2026-10/2026-10-22-1300-deep-audit-nop-graph-duckdb-concurrency/`；批次定义 `ai-dev/audits/2026-10/2026-10-23-1400-cra-phase7-cross-module-adjudication/batch-proposal.md` §二.3 R3-06；族级 SSOT [ledger-retention-policy.md](ledger-retention-policy.md)（清理形态 D close 桥接承接面）
> Owners: 裁定①（nop-duckdb 引擎生命周期域）、裁定②⑦（nop-duckdb task step 重放契约域）、裁定③（nop-duckdb 文件桥接域）、裁定④（nop-graph-api Edge 契约域——plan-first 专节）、裁定⑤⑥（nop-graph-core 算法包络域）、裁定⑧（测试缝总表）

本文是 plan 2529 全部 7 项成员修复的唯一设计裁决权威。每条裁定给出：决策、理由、被否决的备选、零触碰边界。行号以 live 基线 `1dbb2d1767`（分支 `concurrency-audit-remediation`）亲读值为锚（与 plan 2529 基线 `af49a87ca1` 零源码漂移——本批批前亲读核对），后续漂移以 live 为准。

## 裁定① W-02：DuckDbEngine open/close 竞态世代守卫（track 锁内复查，P1）

**决策**：

1. 守卫收口在 `DuckDbEngine.track`（:247-251）的 `synchronized(this)` 块内**入账前**：复查 `closed`（:86 volatile）标志，命中即按「先关闭、后拒绝」次序处置——`IoHelper.safeCloseObject(conn)` 关闭原连接（`safeCloseObject` 对关闭失败 INFO 记录显式可见，`IoHelper` :137-145，不抛出）→ `filePath != null` 时 `releaseOpen(filePath)` 归还 `OPEN_FILES` 注册表 refs → 记录一条 WARN 日志（守卫命中 = 停机竞态信号，failure-path-only 成本；plan Non-Blocking Follow-up「W-02 守卫可观测性」以最小形态纳入，不再单列）→ 抛 `ERR_DUCKDB_ENGINE_CLOSED` + `bizFatal(true)`（:298-303 `checkClosed` 既有同码同语义）。守卫命中路径不构造 `TrackedConnection`、不动 `connections` 账本、不构造 `BridgeConnection` 包装——无包装泄漏、无重复记账。**已知代价声明（评审 R1 Minor-①）**：守卫的 `safeCloseObject` 在 engine monitor 内执行 JDBC 物理关闭——慢关闭（native 关闭耗时）期间其他 open/close 调用被阻塞直至返回；接受理由 = 失败路径、单连接、非热点路径，且 close() 本体「锁内快照、锁外关闭」的既有形态不变。
2. **一处封死论证（openMemory/openFile 双入口 × close 全时序矩阵）**：`track` 是两入口唯一的连接入队点（openMemory :98 / openFile :125 无其他连接出口），`track`、`close`（:131）、`untrack`（:257）共用同一 `this` monitor——track 的守卫复查与 close 的 `closed=true`（:135）在同一 monitor 上线性化，交错被完全枚举： close 先于 `checkClosed`（:90/:103）→ 入口既有拒绝； checkClosed 通过后、`track` 前（含 connect 慢区与 `applySettings`、`registerOpen` :124 窗口）任意时刻 close 完成 → `closed` 恒为 true（close 幂等早退 ：132-134 不复位），守卫在 track 锁内必命中 → 新连接被关闭 + refs 归还 + 显式拒绝； `track` 先完成 → 连接已入快照（:136-137），由 close() 统一关闭释放。三类全闭合，无第四形态。
3. **BridgeConnection 形态兼容**：守卫位于 `track` 内部最前，先于 W-01 的 `TrackedConnection` 构造（:248）与包装返回（:250）；W-01 落地形态（包装 close 锁内 untrack + releaseOpen、`engine.close()` 幂等兜底、观察钩子 ：264-274）零触碰——守卫拒绝的连接从未进入账本，`engine.close()` 不会重复处理；`registerOpen`（:124）先于 `track` 的窗口由守卫路径的 `releaseOpen` 归还覆盖。
4. **接口文本零改动**：`IDuckDbEngine` :16「close() releases every connection」契约在实现侧兑现（plan 2502 Non-Goals 零改动边界延续）；接口 javadoc 运行时语义注记**不采**——守卫语义的 owner doc 落点为 Phase 3 `nop-duckdb.md`「并发与资源契约」节（世代守卫 + ENGINE_CLOSED 拒绝语义），接口面保持零 diff。
5. **红① latch 缝时序稳定性设计**：测试子类覆写 `newJdbcConnection` 缝（:190-192 protected，`BrokenNativeEngine` 先例 `TestDuckDbEngine` :197-199），以两个 `CountDownLatch` 实现确定性交错：T1 进入覆写方法即 countDown 启动闩并在门闩上等待 → 主线程 `engine.close()`（此时 T1 已越过 checkClosed，时序由闩结构保证，零 sleep、零轮询）→ 门闩放行 → T1 完成 connect/registerOpen/track → 守卫拒绝。整链无时序敏感窗口，间歇红容忍度声明 = **不预期**（结构性确定性；测试保留 `@Timeout(60)` 防病态挂起，沿模块测试惯例）。

**被否决的备选**：checkClosed 在 openConnection 后二次调用不采（窗口仍在 applySettings/registerOpen 与 track 之间未闭合，且散点复查违背单点守卫纪律）；close 端补救（close 后扫描迟到连接）不采（需弱引用/回调登记新设施，复杂度不成比例）；守卫命中静默丢弃连接不采（rule 24——显式 bizFatal 拒绝，非静默）。

**零触碰边界**：`IDuckDbEngine` 接口文本零改动；W-01 三锚（`TestDuckDbLedgerCloseBridge_W_01` :68/:99/:143/:174 四测）与 `TestDuckDbEngine` :84-98 close 拒绝锚、:130-155 指纹冲突锚、:151-154 关闭后重开锚零回退；config fingerprint 注册表语义（:108-115）零触碰；`untrack`/观察钩子（:257-274）零改动。

## 裁定② W-03：materialize CTAS table-exists 守卫 + resultTable 重放语义界定（P1，W-10 声明面随单）

**决策**：

1. **采方案 (a) 前置守卫**（否决方案 (b) DROP IF EXISTS）：`DuckDbSqlTaskStep.materialize`（:124-137）在拼装 CTAS（:126）**之前**做 `tableExists` 检查，命中抛 `ERR_DUCKDB_TABLE_EXISTS`（`NopDuckDbErrors` :56 既有码，param tableName）+ `bizFatal(true)`——与同单元 ingest 守卫 `DuckDbFiles.readExternal` :127-131 完全同口径（要求调用方显式清理，重试不盲目重放；F2 半个成功族 + F3 守卫漏配族的范式对齐）。
2. **tableExists 复用形态 = 包内可见性提升**：`DuckDbFiles.tableExists`（:181-191，`private static`）去 `private` 提升为包内可见（同包 `io.nop.duckdb`，零行为 diff、零逻辑复制）。**否决等效查询复制**：存在性谓词是 W-03 守卫与 ingest 守卫共用的单一判据，复制即引入双源漂移（转义语义 `escapePath` :201-203、`information_schema.tables` 谓词形状两处各自演化）。
3. **resultTable 生命周期语义界定（守卫不误伤论证）**：守卫语义 = 「resultTable 由唯一一次成功执行创建；materialize 启动时同名表已存在即为孤儿或陈旧工作集，显式拒绝并要求清理（DROP 后重放或换新 dbPath）」。三类场景逐一界定： 步内重试/恢复重放撞孤儿表（守卫目标）——CTAS 已提交后 count 失败（:131-135）或进程 kill，步完成态未落盘，重放命中守卫 bizFatal 显式失败，首因可见、零重试风暴； 多步管线合法重复物化——**仓内不存在**：全部 `_vfs` 任务样例（pipeline.task.xml `t_filtered`、deep-spill.task.xml `t_agg`→`t_joined`、e2e-pipeline.task.xml `t_filtered`→`t_agg`、resume.task.xml `verify`）各步 resultTable 互异，"只跨步传名字不传数据"契约（`DuckDbSqlTaskStep` javadoc :33-37）下同表重复物化不是任何合法用例； 跨运行重放（resume.task.xml verify 步为持久前提：suspend 后 resume 以同 dbPath 重执行未完成步）——verify 步二次物化同名 `verify` 表正是守卫目标的声明场景，显式 bizFatal + 清理指引优于静默重建（见第 4 条）。in-memory 路径表随连接消亡，守卫不可达，零影响。
4. **否决方案 (b)（步首 DROP TABLE IF EXISTS）的理由**：「每次执行重建」语义三重破坏——静默丢弃持久工作集中上一次成功运行的产物（工作集的证据属性）；掩盖非确定性 SQL 的重放漂移（同 resultTable 静默给出不同数据）；吞掉下游步正在引用的表（跨任务共享 dbPath 场景另一管线的读路径被静默截断）。bizFatal 口径同时阻断 retry 风暴（守卫抛出即 `RetryPolicy.isRecoverableException` 不可恢复短路——bizFatal 语义既有，分类面零触碰）。
5. **红②b 断言写死（评审 Minor-4）**：重放回归钉的判定链 = 首执行 `executeUpdate`（:129）成功后注入非 bizFatal 可恢复异常 → `<retry>` 第 2 次重放**命中守卫** → `ERR_DUCKDB_TABLE_EXISTS` + bizFatal → 任务终态 **FAILED、零后续重试、孤儿表留存待显式清理**。机制核证：`NopDuckDbException` 为 RuntimeException，穿透 `DuckDbSqlTaskStep` :114 `catch (SQLException)` 不被包装为 IO_FAILED——守卫错误码以原貌到达 retry 装饰器，bizFatal 短路后续重试。
6. **零触碰声明**：`execute` 的 `catch (SQLException)` IO_FAILED 包装面（:114-117）零改动（守卫前置不在分类面）；nop-task `<retry>` 装饰器 / `RetryPolicy` / `RetryTaskStepWrapper` 零触碰；resultTable/exportPath 互斥校验（:71-72）零回退；ingest 守卫锚 `TestDuckDbFiles` :113-119 零回退。**W-10 声明面随同批交付**（载体与文案见裁定⑦；`materialize` javadoc :121-123 区段为文案落点之一，与守卫同 diff）。

## 裁定③ W-05：readXlsx 临时文件配对 + deleteOnExit 移除（P2，吸 CS-003）

**决策**：

1. **采两段合一形态**（否决 try-with-resources 抽象）：`DuckDbFiles.readXlsx`（:68-87）重构为「创建段 + 生命周期段」——创建段（`Files.createTempFile` :72，自身 `IOException` 显式包装为 `ERR_DUCKDB_IO_FAILED`、reason 标签区分 "temp csv creation failed"）产出临时文件后立即进入**单一 try/finally**，finally 覆盖 xlsx→CSV 转换段（:74-75，转换失败维持既有 IO_FAILED 包装语义 ：76-80 原样）与 `readCsv` 段（:82，其自身 `NopDuckDbException` 原样传播不被二次包装）——成功失败均 `delete()`。
2. **deleteOnExit 移除**（:73，CS-003 子面随单收敛）：finally 已覆盖成功路径后，JVM 退出兜底冗余且 `DeleteOnExitHook` 每调用追加只增不删（进程生命周期无移除 API）——删除注册调用。**兜底语义变化声明（owner doc 义务）**：移除后 JVM 异常退出（kill -9）场景失败路径残留无进程退出兜底——接受度论证 = 失败路径有人工介入链（step 失败即任务失败告警）、残留为有限个临时 CSV（按失败次数）、与仓内其他临时文件使用面（如指纹冲突测试的临时目录）兜底口径一致；`delete()` 返回值忽略维持（:85 既有注释面——成功路径下 readCsv 已释放句柄，Windows 语义安全）。
3. **否决 try-with-resources 抽象（临时文件 AutoCloseable 包装类型）**：`readXlsx` 无生产调用方（仅测试消费——merge-adjudication §二 CS-003 行边界限定在案），单调用点引入新抽象类型不成比例；两段合一本方法内闭合，零新类型。

**零触碰边界**：`readCsv`/`readExternal`/`copyTo` 零改动；xlsx happy path 三测（`TestDuckDbFiles` :317-352 区段）零回退；`TestDuckDbFaultInjection` :92 失败不留残留断言先例（`assertFalse(Files.exists(...))` 形态）为红③ 复制模板零回退；`ERR_DUCKDB_IO_FAILED` 非 bizFatal 分类维持（retry 叠加放大链由配对修复消除单次泄漏，不改编 proc 类面）。

## 裁定④ W-06：Edge.attrs 可见性契约（nop-graph-api plan-first 专节，P2）

**决策**：

1. **采建议 (1)+(2) 组合**：`Edge.attrs`（:23）加 `volatile`；javadoc :13「attrs 是 mutable 的，允许适配层在构造后注入领域属性」扩写为可见性契约——构造后 `setAttrs`（:56-58）的发布对并发读取线程可见（volatile 语义）；**契约范围显式收窄**：volatile 只保证 attrs **引用**的发布可见性，不保护已发布 map **内容**的并发改写——构造完成后调用方不得并发改写已发布 map（或自行使用并发 Map）。防契约过度承诺（plan Risks 面原义）。
2. **InMemoryGraph 联动限定 = 采**：类注释 :18「这是同步线程安全的实现」限定为「结构级——结构操作（增删点边/快照读）线程安全；返回列表中的 Edge 元素为共享实例（防御拷贝 ：44-48 仅拷贝列表，:47），Edge 字段的跨线程可见性由 `Edge` 自身契约（volatile attrs）保证」。impl 侧注释面零行为 diff。
3. **否决 synchronized setAttrs（方案 (3)）**：单字段置入的锁粒度只覆盖写写互斥，读侧 `getAttrs`（:52-54）无锁仍无 happens-before——要么读侧同样加锁（把 api 读路径拖进 monitor，违背值对象轻量语义），要么缺口仍在；volatile 单点闭合读写可见性且读路径零开销（写频率低——构造后注入一次，代价可忽略）。
4. **否决契约收窄方案（构造后禁改、attrs final 化）**：`CodeRelationGraph` ctor :53 构造期写点虽是现网唯一写点，但 :13 javadoc 明文承诺「构造后注入」是 api 契约的一部分，收窄 = 公共契约违约；final 化直接破坏声明能力。volatile+注记是唯一同时兑现声明契约与闭合缺口的形态。
5. **api 变更专节（四件证据链第二件——本节）**：
   - **必要性**：:13 javadoc 明文邀请构造后注入 + `InMemoryGraph` blanket 线程安全声明（:18）组合引导未来消费方在「T1 构建线程 setAttrs × T2 查询线程经 getOutEdges 读 getAttrs」形态下无 happens-before、静默 stale 读（emptyMap/旧引用）；api 未声明 Edge 可见性契约。现网无既发错误（唯一生产写点 `CodeRelationGraph` ctor :53 构造期完成，CodeRelationGraph 自身全 final 字段无 this 逃逸——安全性来自持有者安全发布，attrs 非 final 不适用 JMM final 冻结），风险对象是 api 契约面引导的未来消费方——本裁定是契约缺口修复而非缺陷修复。
   - **二进制兼容性（JLS §13.4 总则——评审 R1 Minor-④ 修订引注）**：字段名与字段描述符解析不变（JLS §13.4 总则——修饰符变更不改变字段解析与链接；§13.4.9 为 final 字段专条，非本变更面）；字段加 `volatile` 不破坏既有二进制的链接与执行，`getAttrs`/`setAttrs` 签名不变；`Edge` 非 `Serializable`（无序列化形态影响）。消费方二进制零重编译、源码零适配，行为仅获得可见性升级。**传递安全依据（既有写点合规性不受本变更影响）**：现网唯一生产写点 `CodeRelationGraph` 的 Edge 引用仅经其全 final 字段可达——JMM final 字段语义对 final 字段可达对象图提供传递性安全发布保证，构造期写点的安全性与 volatile 无关且不受影响。
   - **全仓消费方清单（live grep 实录，`io.nop.graph.api.Edge` 精确匹配）**：main 写点 1 处 = `CodeRelationGraph` ctor :53（nop-code-core，构造期，volatile 下零语义变化）；main 读点 3 处 = `GraphQuestionGenerator` :54（经 ：57 getAttrs）、`GraphWikiExporter` :48/:113（:261 区段 getAttrs）、`SurprisingConnectionAnalyzer` :51/:61（:62 区段 getAttrs）（均 nop-code-service，只读——volatile 零适配）；测试面 = `TestApiTypes` :23/:35-36、`TestCodeRelationGraph`（:53-59/:64-65/:77/:104 读，:144 写）、`TestCodeRelationGraphLoader`（:116/:135/:150/:220-221）、`TestDbCodeRelationGraphEquivalence`（:136/:146-147）——全部零适配。
   - **回归面**：nop-graph-api 11 tests + nop-graph-core 32 tests + nop-code 602 tests（14S 签名维持）三通道（分解通道 `./mvnw -o test -pl nop-graph,nop-code` 面）+ 红④ 结构钉；`nop-graph-api` 其余 13 文件零触碰。
6. **契约记录落点终判（owner-doc 缺口下）**：预设维持——不新建 nop-graph owner doc（缺口声明随 R-G，roadmap Owner Doc 列原文；plan 立项 review 未推翻）；契约记录 SSOT = 本文本裁定④ + `docs-for-ai/01-repo-map/module-groups.md` :24 nop-graph 行最小同步核对 + `docs-for-ai/04-reference/source-anchors.md` 锚点行（GRAPH-CC-001 候选，Phase 3 落账）。条件申报①（超 volatile+javadoc 形态）**不触发**。
7. **钉形态**：红④ 主保护 = 反射结构钉（`getDeclaredField("attrs")` 修饰符含 volatile——确定性当前即红，pre-fix 0 volatile）；辅 = 多线程压力弱断言（读值引用 ∈ {emptyMap, 已写入引用集}，不作 happens-before 判定，如实标注间歇红可能——2528 红③ 先例口径；主保护为结构钉）。

## 裁定⑤ W-07：PageRank outDegree 预计算消除分配放大（双路径，P2，吸 CS-R2-003）

**决策**：

1. **预计算形态**：`PageRank.compute`（:30-77）在入参校验后、迭代开始前，对入参 `nodes` 集一次性构建 outDegree 映射——每节点恰一次 `getOutEdges(node).size()`（Θ(V) 次调用、Θ(V+E) 次元素拷贝，单次全量）。迭代体内 ：49（悬挂节点判定，每节点每迭代一次）与 ：62（每条入边读 source 出度）全部改读映射——迭代期 `getOutEdges` 调用归零。**节点集边界**：预计算域 = 入参 `nodes`（迭代宇宙）；nodes 之外的 source 经 ：61 `ranks.containsKey` 天然跳过，无需出度，与现行为等价（:62 仅在 source ∈ ranks 时调用）。
2. **量化**：pre-fix `getOutEdges` 调用数 ≈ (V + E′)·iterations（E′ = nodes 内入边数），每次调用在 `InMemoryGraph` 为锁内全表防御拷贝（:44-48，:47）——E=10⁵/d̄=10/20 迭代 → 2×10⁷ 元素拷贝 + 等量临时 List；post-fix = 恰 V 次（单次预计算）。生产路径（`CodeCallGraph` 载体，CodeGraphService :753/:971、GraphMetricMaterializer :91 装配）叠加的第二层放大（每边 new Edge，CodeCallGraph :32/:45 + `CallGraph` synchronized 整表拷贝 ：36-44/:41-44）随调用频次从 (V+E′)·iterations 坍缩到 V **消费侧自然缓解**——两层放大中列表拷贝层被预计算消解、Edge 重建层随频次坍缩到无感量级。
3. **:49 轻症同单顺带消解**（同映射覆盖，零额外调用）；**`getInEdges`（:59）每节点每迭代保留**——入边枚举是算法固有需求，预计算/缓存入边列表会引入「跨迭代缓存活性图快照」的并发语义变化与内存驻留，不采。
4. **否决的备选**：IGraph 新增 degree/count 只读 API 分支**禁选**（触 nop-graph-api 叶——条件申报②预设不触发；CL-204 结构性根因的 API 级治理归 Non-Blocking Follow-up/后续批，本批预计算已闭合缺陷本体）；`CodeCallGraph`/`CallGraph` 适配器本体缓存或 Edge 复用**不采**（触碰 nop-code-core + 引入新缓存组件——plan Non-Goal「禁引入新缓存组件」；消费侧频次坍缩后无剩余可观收益）；PageRank 增量计算/迭代间差分不采（算法语义变更，超出包络修复）。
5. **双路径同测**：InMemoryGraph 侧 = 红⑤ 计数代理钉；生产路径 = `TestGraphMetricMaterialization`（nop-code-service，经 GraphMetricMaterializer :201 PageRank 调用）正确性零回退 + `TestAlgorithms` :158-203 正确性锚零回退。CL-204 作结构性根因附注 + CL-206 随预计算顺带覆盖声明（§六.5 注记通道），落 cra-index 收敛注（Phase 4）。

**零触碰边界**：`IGraph` 接口零改动；`InMemoryGraph` 产品码零改动（红⑤ 仅测试侧计数代理包装）；`CodeCallGraph`/`CallGraph`/`CodeRelationGraph` 零产品 diff（预设零触碰登记，Phase 2/4 git diff 核对）；PageRank 数值语义零变更（预计算值 ≡ 逐次调用值——图在 compute 期间不可变属 IGraph 消费既有假设）。

## 裁定⑥ W-08：Betweenness 超时包络（生产接线 + daemon 设施 + 语义边界，P2，W-04 族 cross-ref 不并单）

**决策**：

1. **timeout 参数形态 = 新增 3 参 overload + 2 参缺省委托可配置**（否决改签名）：`compute(IGraph, Set<String>, long timeoutMs)` 新增，入参校验（null/empty + 显式传入的 `timeoutMs <= 0` 为非法——`IllegalArgumentException`，非静默禁用）保持在调用线程；既有 2 参 `compute`（:29）**缺省委托** 3 参路径，超时来源 = AppConfig 配置变量 `nop.graph.betweenness-timeout-ms`（`AppConfig.varRef` 静态配置变量读取面，`CoreConfigs` :18 同型先例；2 参路径每次调用读取——配置变更即时生效）。语义三分（评审 R1 Major-2 选型 (a)）：未配置 → 回落包内可见常量 `DEFAULT_BETWEENNESS_TIMEOUT_MS = 30000`（红⑥ 读取断言；家族一致性 = `LeidenConfig` :9 缺省 30000 同算法包先例）；配置 > 0 → 该值；**配置 <= 0 = 不设超时档**：跳过包装同步执行（调用线程直跑，语义 = 显式关闭墙钟包络，风险 = O(V·E) 无界占用回归——owner doc 声明风险，禁静默缺省）。零调用方 diff、零 PA。2 参委托使两处生产调用方（`GraphMetricMaterializer` :190 / `CodeGraphService` :971）**零改动自动生效**——评审 Major-1 生产接线强制条款兑现（防 rule 23 空洞）。
2. **缺省值论证（30000）与生产行为变更声明**：同族先例 `LeidenConfig.timeoutMs` 缺省 30000（:9）；2528 批 webhook 投递 30s 先例。**缺省超时 = 生产行为变更声明**：现网 Betweenness 等待无上界（V=10⁴ 守卫上限内 Brandes 可达分钟级）→ 未配置时上界 30s；合法超 30s 的计算以显式错误终止，补救路径 = 配置 `nop.graph.betweenness-timeout-ms` 调大、调用方显式切换 3 参 overload、或缩减节点集（既有 10⁴ V 守卫 `GraphMetricMaterializer` :62/:185 不变）；键语义与 `<=0` 禁用档落 Phase 3 owner doc「Betweenness 超时语义边界」节（`nop-duckdb.md` 增补节内消费面声明落点——nop-graph 无 owner doc 的缺口声明形态，随 R-G；本文本裁定为 SSOT 初载）。
3. **包装设施选型 = 每调用单 daemon 工作线程 + `FutureTask`**（JDK 并发原语；线程命名/daemon 经 `NamedThreadFactory(name, daemon)`——nop-commons `concurrent/thread` 包既有设施，使用面引用零平台修改，条件申报③不触发；**pom 裁定：不补显式 nop-commons 依赖**——经 nop-core 传递可见（`nop-kernel/nop-core/pom.xml` :37），零 build 面 diff，显式登记该选择）：调用线程构造 `FutureTask`（任务体 = **jgraph 构建 + Brandes + 防御拷贝全量计算体**，:34-58）并以 daemon 工作线程执行，调用线程 `get(timeoutMs, MILLISECONDS)` 等待；`TimeoutException` → WARN 日志（记录放弃等待与节点规模）→ 抛 `ERR_GRAPH_COMPUTE_TIMEOUT` + `bizFatal(true)`（超时对同一图确定性复现，盲重试无收益且每轮空烧 timeout 预算——bizFatal 短路）。
4. **异常/中断传播成文（评审 R1 Major-1）**：ExecutionException 一律解包——任务体抛出的 Throwable 经 cause 解包原样重抛：`NopException` 保留错误码/bizFatal 原样透出；`Error` 原样重抛；受检异常包为模块异常（`NopGraphErrors` 码）——**调用方面永不出现 ExecutionException 外壳**。调用线程 `get` 被中断 → 恢复中断标志 + `cancel(true)` 工作任务 + 抛 `ERR_GRAPH_COMPUTE_INTERRUPTED` 显式错误（非静默吞中断，rule 24）。两者纳入 C10 语义声明（见裁定⑧ 红⑥）。
5. **错误登记（评审 R1 Major-3——纠正 1 版「nop-graph-core 现无任何 NopException/错误登记」的错讹事实）**：准确刻画 = `GraphExporter` :80 已以字面量形态使用 `NopException("nop.err.graph.export-failed", ...)`（:18 import 在案）；模块缺的是**错误登记族文件**。选型 = **不触碰 nop-core `CoreErrors`**（:902-906 既有 `nop.err.graph.*` 三码 duplicate-vertex/unknown-node/nodes-not-reachable——平台核心 plan-first，零触碰）；在 nop-graph-core 模块内新建 `NopGraphErrors` 注册 `nop.err.graph.compute-timeout` 与 `nop.err.graph.compute-interrupted`（Major-1 中断用码）。**命名空间分工声明**：`CoreErrors` 承载 nop-core 内置图工具码，`NopGraphErrors` 承载 nop-graph-core 算法码；新码与既有码零重名（grep 实证：`nop.err.graph.compute-timeout`/`compute-interrupted` 全仓 0 命中）；`GraphExporter` 字面量零触碰（登记为可选收敛 follow-up——后续批归一为 ErrorCode 引用，非本批义务）。`NopGraphErrors` 为 plan In-Scope 文件清单的最小增补（新登记文件 1 个，Phase 1 评审已登记），Phase 2 diff 随登记。
6. **超时后不泄漏/不成幽灵线程论证（防 W-04 同型自反——裁定强制输入）**：放弃等待后工作线程**继续运行直至 Brandes 自然完成**（JGraphT 1.5.2 零 interrupt 引用——反汇编取证在案，不可中断是既定事实，不承诺中断）——该线程是有界有限任务的一次性 daemon 线程：daemon 标志保证 JVM 退出永不被阻塞；任务自然终止后线程对象与 jgraph 引用一并不可达；无线程池、无队列、无驻留（每调用至多一个线程，完成即亡）。对照 W-04 缺陷本体 `LeidenDetector.runWithTimeout`（:196-203）：`Executors.newSingleThreadExecutor()` 缺省工厂为**非 daemon** 线程，`shutdownNow()` 对不可中断任务无效 → JVM 退出被永久阻塞——W-08 包装以 daemon + 一次性线程从结构上排除该形态。**语义边界声明（C10 义务文案）**：「超时仅放弃等待——调用方在 timeout 后得到显式错误；底层 Brandes 计算不可中断，将在后台继续至自然结束（daemon 线程，不阻塞 JVM 退出）；放弃后计算自身的失败不再上报」。放弃路径的 CPU 残留 = 并发放弃数 × 单次 Brandes 时长，有界且自终止。
7. **否决的备选**：共享静态 `ScheduledExecutorService`/线程池不采（排队等待计入 timeout 语义失真 + 慢计算队头阻塞后续调用）；`CompletableFuture.supplyAsync(...).orTimeout` 不采（orTimeout 的延迟调度器为 JDK 内部单例——可用但 supplyAsync 仍需外部 Executor，两件原语组合相对 FutureTask 无增益，且完成阶段包装引入 CompletionException 解包噪声）；V×E 乘积守卫/密集度预判**本批不采**（墙钟超时已与图形状无关地界定包络；乘积常数需按机器算力标定， premature——登记 Non-Blocking Follow-up，plan 已列）；非 daemon 线程禁选（即 W-04 自反）。**jgraph 构建体纳入任务体**的理由：构建经 IGraph 回调（:41/:40 出边枚举），置于受超时管辖的工作线程使「计算确实在包装线程上执行」成为红⑥ 接线钉的可观测事实（见裁定⑧），且构建 O(V+E) 代价纳入包络语义无损。
8. **W-04 设施对齐软注记（对齐纪律第六条成文）**：W-08 与 8.37 W-04 的超时设施若均落 `NamedThreadFactory` + JDK 并发原语使用面，则两批设计文档内一次对齐声明成立——**8.37 W-04 修复应采同一 daemon/命名纪律**（LeidenDetector 现形态的修正方向），软依赖非硬依赖、两批可并行；不共享线程设施实例（W-08 每调用一次性线程，W-04 为持续检测面，生命周期不同）。`BETWEENNESS_MAX_NODES`（GraphMetricMaterializer :62/:185）V 守卫既有面零触碰，本裁定只叠加墙钟维度。

**零触碰边界**：`LeidenDetector` 全文件（8.37 边界，见批域边界节）；`GraphMetricMaterializer`/`CodeGraphService` 生产调用方两处 :190/:971 零改动（2 参委托自动生效——接线由红⑥-2 钉守护，防「生产改调用方」的隐性扩面）；JGraphT 依赖版本零变更；`LeidenConfig` 零触碰。

## 裁定⑦ W-10：caller-SQL 重放幂等声明面（P3 条款随批，随 W-03 同 diff 交付）

**决策**：

1. **声明面载体（唯一可修面——executeSql 直通面模块级前置守卫原理性不可达，audit 原文）**：`DuckDbSqlTaskStep` javadoc 增补重放幂等契约——持久 dbPath 下步骤可能被 `<retry>` 装饰器或任务恢复重放执行，调用方自带 SQL 必须重放幂等（如 `CREATE TABLE ... IF NOT EXISTS`、调用方自有的 DROP+CREATE 对）；非幂等 DDL 在 kill/重试窗口内导致永久失败且首因被 IO_FAILED 包装掩盖；`resultTable` 路径的守卫语义（同名即 TABLE_EXISTS bizFatal，见裁定②）作为模块侧唯一守卫面声明。落点 = 类 javadoc 输入契约段（:21-47 区）与 `materialize` javadoc（:121-123 区）。
2. **owner doc resume 幂等例外声明（发现级 drift 收敛，merge-adjudication §五.4）**：Phase 3 `nop-duckdb.md` :75-83 task step/resume 节增补——「resume 需重新打开同 dbPath」语义的例外：caller-SQL 不具重放幂等性时，步内提交窗口的 kill/重放将永久必败；模块自带样例即含非幂等 caller SQL（resume.task.xml setup 步 `CREATE TABLE res AS SELECT ...`，:5——亲读核实），照抄样例组织持久任务须自查幂等性。`_vfs` 任务样例 XML 零触碰（预设维持——声明面不改性；可选样例幂等治理归 Non-Blocking Follow-up）。
3. **不设行为红钉**（C10 P3 聚合口径——常规行为测试不可达，登记观察不强制）；executeSql 直通面零代码改动；与 W-03 守卫面同批交付（守卫面 + 声明面对齐 ingest 范式），cross-ref 不合并维持（失效层不同）。

**零触碰边界**：executeSql（:143-158）零代码改动；`_vfs` 样例零触碰；retry/resume 分类面零触碰。

## 批域边界：与 8.37（R3-07 Leiden 交叉批）零触碰

本批零触碰面（closure audit git 过滤核对）：`LeidenDetector.java` 全文件（含 `runWithTimeout` :196-203 非 daemon executor 形态——W-04 幽灵线程缺陷本体归 8.37；本批裁定⑥仅引用其形态作为反例论证）；`GraphMetricMaterializer.detectCommunities` 与 `CodeGraphService.runCommunityDetection` 守卫面（:122/:121/:476/:495/:521/:506/:521）；`MAX_NODES_FOR_COMMUNITY_DETECTION` 守卫常量（CodeGraphService :68）；nop-code-service 读路径自愈单飞面（6.3-W-03）；`BetweennessCentrality` 的 V 守卫既有面（:62/:185/:446/:506/:841——8.37 边界外的 Betweenness 侧既有面，零改动）。W-08 与 W-04 同族相邻（不可中断计算）但失效层不同（同步占用请求线程 vs 幽灵线程泄漏）、修复不同（超时包装 vs daemon/有界执行）——cross-ref 不并单维持；两批超时设施对齐为软注记（裁定⑥第 6 条）。

## 裁定⑧ 测试缝总表（红①–红⑥ 驱动器 + 零回退钉清单 + C10 映射）

### 红绿判据（先红后绿，E2）

本批 **0 条反向钉改写、0 条变异锚定面**（单元裁定在案——红①–红⑥ 全部构造性当前即红，缺陷态下由新断言直接构造红）。

| 钉 | 成员 | 驱动器形态 | 缺陷态红判据 |
|---|---|---|---|
| 红① | W-02 | 测试子类覆写 `newJdbcConnection` 缝（:190-192，`BrokenNativeEngine` :197-199 先例）注入双闩（启动闩 + 门闩）：T1 openMemory/openFile 在 connect 慢区阻塞 → 主线程 `engine.close()` → 放行 → T1 经 track 守卫。断言： (a) 入口抛 `ERR_DUCKDB_ENGINE_CLOSED` + `isBizFatal()`； (b) 底层连接 `isClosed()`、`trackedConnectionCount()==0`、`openFileRefCount(norm)==null`（W-01 钩子 ：264-274 复用）、openFile 形态 bare `DriverManager` 立即重开同文件成功（单写者锁无残留）； (c) 二次 close 幂等。**双入口同测** | **构造性当前即红**：pre-fix track 无守卫—— (a) openFile/openMemory 正常返回包装连接（无异常断言红）； (b) 账本计数 ==1、refcount==1、连接未关 |
| 红② | W-03 | (a) 持久 dbPath 预置同名 resultTable → 执行 materialize 步 → 断言 `ERR_DUCKDB_TABLE_EXISTS` + `isBizFatal()`； (b) 重放回归：测试包装步首执行 `super.execute`（CTAS 已提交）后抛非 bizFatal 可恢复异常 → `<retry>` 第 2 次重放命中守卫 → 断言错误码 + bizFatal、任务终态 FAILED、执行次数 ==2（零后续重试）、重开 dbPath 断言孤儿表留存待显式清理（NopDuckDbException 为 RuntimeException 穿透 :114 catch(SQLException)） | **构造性当前即红**：pre-fix 无守卫—— (a) CTAS 撞表 SQLException 被包装 `ERR_DUCKDB_IO_FAILED` 非 bizFatal（错误码 + bizFatal 双断言红）； (b) 第 2 次重放再次撞表 IO_FAILED → 重试耗尽（次数 > 2）、错误表象掩盖首因 |
| 红③ | W-05 | 损坏 xlsx（非 zip 字节写入 .xlsx 文件）→ `assertThrows(NopDuckDbException, readXlsx)` → 断言 `java.io.tmpdir` 无 `duckdb-xlsx-` 前缀残留（`TestDuckDbFaultInjection` :92 失败不留残留断言先例复制；模块内既有测试顺序执行，前缀扫描零干扰） | **构造性当前即红**：pre-fix finally 不在转换段作用域（:76-80 catch 重抛，:83-86 finally 仅覆盖第二段）——临时文件残留 |
| 红④ | W-06 | 主保护 = 反射结构钉：`Edge.class.getDeclaredField("attrs")` 修饰符断言含 volatile；辅 = 压力弱断言（写线程 setAttrs 固定引用集，读线程断言 `getAttrs()` 引用 ∈ {emptyMap, 已写入引用}，间歇红可能如实标注——主保护为结构钉，弱断言不作 happens-before 判定） | **构造性当前即红**：pre-fix `attrs` 非 volatile（0 volatile，确定性红） |
| 红⑤ | W-07 | 计数代理 IGraph（包装 getOutEdges/getInEdges 分别计数）+ InMemoryGraph 小图（V=20/E=60/iterations=3）执行 `PageRank.compute`：断言 `getOutEdges` 调用数 == V（预计算恰每节点一次）；正确性回归双路径（`TestAlgorithms` :158-203 + 生产路径 `TestGraphMetricMaterialization`）零回退 | **构造性当前即红**：pre-fix :49 每节点每迭代 + :62 每入边每迭代——调用数 ≥ (V+E′)·iterations = 240 ≫ 20（确定性红，无墙钟断言） |
| 红⑥ | W-08 | (i) 3 参小图 + 探针 IGraph（getOutEdges 闩阻塞）+ timeout=100ms → 断言 `ERR_GRAPH_COMPUTE_TIMEOUT` + bizFatal → 放行闩后工作线程 join 终结（不泄漏实证）； (ii) 接线钉：2 参 compute（测试上下文未配置 `nop.graph.betweenness-timeout-ms` → 回落 30000）+ 探针 IGraph 捕获 getOutEdges 回调线程 → 断言全部回调在 `nop-graph-betweenness-` 前缀 daemon 工作线程上（`isDaemon()` 现场捕获）+ `DEFAULT_BETWEENNESS_TIMEOUT_MS == 30000` + 2 参结果 == 3 参(30000) 结果； (iii) 异常透传钉（评审 R1 Major-1）：探针 IGraph 在闩内抛既定 `NopException`（既定错误码 + bizFatal）→ 断言 3 参 compute 原码 + bizFatal 原样透出、无 ExecutionException 外壳 | **构造性当前即红**：pre-fix 无 3 参签名/无常量/无包装——(i) 编译期构造形态红（API 无 timeout 参数）；(ii) 回调全部在调用线程 + 常量不存在；(iii) 随 (i) 编译期红（同上形态） |

变异探针纪律：本批无变异锚定面（全构造性红）；如执行期对某钉改用回退论证，恢复路径以 grep/断言确认目标可执行行真实存在（plan guide When Executing #8）。红绿快照落 `_tmp/red-green-2529-summary.md` + 逐 trial 日志（沿 2525–2528 形态）。

### 零回退钉清单

- 分解通道基线 **707 tests 0F0E 14S**（`_tmp/baseline-2529-decomposed.log`，勘误 4：核心双模块 nop-graph 43 + nop-duckdb 62 = 105 tests 0F0E 0S；消费方 nop-code 602 tests）——零回退实证 = 分解通道 BUILD SUCCESS（707+新钉 0F0E）+ **14S 签名比对**（TestIndexNopEntropyProject 13 + NopEntropySelfIndexTest 1，数量与归属不变）。
- 点名既有钉族零回退：`TestDuckDbLedgerCloseBridge_W_01`（:68/:99/:143/:174 四测——W-01 面，红① 的账本断言与其零重叠：守卫拒绝路径连接从未入账）、`TestDuckDbEngine`（:84-98 close 拒绝锚 / :130-155 指纹冲突锚 / :151-154 关闭后重开锚 / :197-199 缝先例）、`TestDuckDbFiles`（:113-119 ingest 守卫锚——裁定② 同口径零回退；:317-352 xlsx happy 组）、`TestDuckDbSingleWriter`（跨进程单写者锁语义面）、`TestDuckDbFaultInjection`（:92）、`TestFlakyDuckDbStep`/`TestDuckDbResume`（retry/resume 形态参照与零回退）、`TestDuckDbE2EPipeline`（:91 readXlsx 消费方——裁定③ 成功路径与 finally 配对零回退）、`TestDuckDbTaskStep`、`TestApiTypes`（:23/:31-37 Edge 单线程锚——红④ 结构钉不改变其行为语义）、`TestAlgorithms`（:158-203 PageRank 正确性）、`TestCommunityAlgorithms`（:46-56 Leiden 小图零触碰 / :89-107 Betweenness 小图正确性——红⑥ 双形态不改变 2 参小图正常返回语义）、`TestGraphMetricMaterialization`（nop-code-service 生产路径——W-07 双路径消费钉）、`TestShardBuildCoordinator`（:89-109，矩阵 #19 假保护注记面——W-12 潜伏非本批，零触碰）。
- plan 2502 W-01 产物面零回退（BridgeConnection/untrack/观察钩子——裁定① 形态兼容为强制输入）。
- 执行期 Skipped/Failures/Errors 出现即登记归因禁静默（勘误 4 口径）。

### C10 缺保护 6 缺陷面同 diff 映射

红①↔P10-P1-2（W-02）、红②↔P10-P1-3（W-03）、红③↔P10-P2-2（W-05）、红④↔P10-P2-1（W-06）、红⑤↔P10-P2-3（W-07）、红⑥↔P10-P2-4（W-08）——每条与本体同 diff 禁拆分交付（E2）。测试命名 `Test*_<工单>` 惯例（W_02/W_03/W_05/W_06/W_07/W_08）；W-10 声明面无可执行红钉（C10 P3 口径），以 javadoc + owner doc 兑现为验收。

## 评审记录

- 设计评审 R1：独立子 agent agent_2fb5f225（fresh session，2026-11-03，@ `1dbb2d1767`）——**Blocker=0，3 Major + 7 Minor；全部 Major 与 Minor 给定清单（①④⑦⑧）随本轮修订闭环；W-06 api 变更第二级 review PASS（四件证据链闭合）**。Major-1（裁定⑥.4 异常/中断传播成文）：ExecutionException 一律解包 cause 原样重抛（NopException 错误码/bizFatal 保留、Error 原样、受检异常包为模块异常），调用方面永不出现 ExecutionException 外壳；`get` 被中断恢复中断标志 + cancel(true) + `ERR_GRAPH_COMPUTE_INTERRUPTED` 显式错误；纳入 C10 语义声明，红⑥ 增补 (iii) NopException 原码透出断言。Major-2（缺省超时可配置，采 (a)）：2 参委托路径读配置变量 `nop.graph.betweenness-timeout-ms`（缺省回落常量 30000；<=0 = 不设超时同步执行显式禁用档），零调用方 diff、零 PA，owner doc 落账该键；保留 30s 的 `LeidenConfig` :9 家族一致性论证。Major-3（错误登记）：纠正「nop-graph-core 零 NopException」错讹事实（`GraphExporter` :18/:80 字面量 `nop.err.graph.export-failed` 在案；`nop-core CoreErrors` :902-906 既有 `nop.err.graph.*` 族）；选型不触碰 CoreErrors（平台核心 plan-first），nop-graph-core 内新建 `NopGraphErrors` 注册 compute-timeout/compute-interrupted 两码，命名空间分工声明成文、零重名 grep 证明；`GraphExporter` 字面量零触碰（可选收敛 follow-up）；pom 不补显式 nop-commons 依赖（传递可见 `nop-kernel/nop-core/pom.xml` :37，显式登记）。Minor-①：裁定①补 track 守卫 engine monitor 内 JDBC 慢关闭阻塞 open/close 的已知代价声明。Minor-④：JLS 引注改 §13.4 总则（§13.4.9 为 final 专条）+ 补 `CodeRelationGraph` final 字段可达的传递安全依据。Minor-⑦：resume.task.xml 行号 :6-8 → :5。Minor-⑧：零回退钉清单补 `TestDuckDbE2EPipeline`（:91 readXlsx 消费方）与 `TestDuckDbTaskStep`。**结论：设计可进入 Phase 2 实现。**
