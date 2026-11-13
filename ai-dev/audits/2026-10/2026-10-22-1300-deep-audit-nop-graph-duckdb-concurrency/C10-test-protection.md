# C10 测试保护力评估 — nop-graph + nop-duckdb 并发审计（CRA WI 5.6）

- **审计单元**: 与三维最终保留项同一单元——nop-duckdb 6f + nop-graph/nop-graph-core 13f + nop-graph/nop-graph-api 14f（评估对象为缺陷面，另延伸核读测试侧消费方 nop-code-service 测试装配）；基线 HEAD 6934df7ad3（实测审计工作树对 nop-duckdb / nop-graph / nop-code 零改动）。
- **维度**: C10 测试保护力——逐条回答「该缺陷引入时，现有测试是否会在断言面上变红？」（会红 = 断言会被缺陷的触发条件与可观测后果破坏）。
- **输入**: 同目录三维最终保留项（已过独立复核）：C1-shared-mutable-state.md（4 条）/ C6-resource-lifecycle.md（6 条）/ CS-sweep.md（9 条），共 19 实体；并入条目按 merge 裁定标注其主登记。
- **方法**: ① 逐文件核读断言面——nop-duckdb 全部 22 个测试文件（TestDuckDbEngine / TestDuckDbSingleWriter / TestDuckDbTaskStep / TestDuckDbFiles / TestDuckDbResume / TestDuckDbE2EPipeline / TestDuckDbFaultInjection / TestDuckDbDeepSpill / TestDuckDbBeans / TestFlakyDuckDbStep / TestFileLockedStep / TestSpillEngine / TestSpillTaskEngine / TestCrossEngineTypeMatrix / TestCrossEngineAggJoin / TestDuckDbDialect / TestBrokenNativeEngine / TestSnapshotTaskStateStore / TestDuckDbPerfBaseline / CrossEngineLegs / CrossEngineDataset / LockHolderMain + `_vfs` 任务样例 XML）；nop-graph 全部 3 个测试文件（TestAlgorithms / TestCommunityAlgorithms / TestApiTypes）；nop-code-service 相关测试（TestShardBuildCoordinator / TestGraphMetricMaterialization / TestGraphAnalysisE2E / TestExplorationQuestions / TestCodeRelationGraphLoader / TestConcurrentIndexing）。② 对每个判定为「无相关测试/无保护」的条目附 grep 命令与命中数。③ 只读评估，未修改任何产品代码与三维审计文件。
- **盲审声明**: 本评估未读取 `ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`、`ai-dev/backlog/` 下任何文件，亦未读取 `docs/` 历史遗留目录；除三份指定输入审计文件外未以任何其他审计产物为依据；全部结论以 live code（测试源码 + 任务 XML + 被审源码）为准。
- **判定口径**: 「会红」要求现有测试 (a) 走到缺陷的触发条件（并发交错 / 失败路径 / 超时路径 / 长驻不关闭形态），且 (b) 对缺陷的可观测后果持有断言。两者缺一即为「不会红」；相邻契约锚（钉定同站点已正确语义、可防修复回归部分侧面）在「现状标注」中单列，不计为会红。

---

## 一、19 行保护力矩阵

| # | 编号（严重度） | 缺陷面 | 相关测试（file:line）或「无」 | 会红判定 | 现状标注 |
|---|---|---|---|---|---|
| 1 | C1-001（P3） | OPEN_FILES 冲突守卫 check-then-act 竞态（检查与注册隔一次慢速 JDBC connect） | `TestDuckDbEngine.java:130-155`（testFingerprintConflictBetweenEngineInstances，仅顺序形态） | **不会红**：无并发同文件异配置 open 用例；顺序用例中检查与注册同线程，竞态窗口不被走到；同 fingerprint 并发（TestDuckDbSingleWriter.java:158-192）不触发冲突分支 | 顺序冲突语义有锚（防守卫被删）；竞态窗口零保护 |
| 2 | C1-002（P2，并入 **C6-002** 主登记） | close() 与 openFile/openMemory 生命周期竞态 → 泄漏连接 + refs | `TestDuckDbEngine.java:84-98`（testEngineCloseReleasesAndRejectsFurtherUse，close 均在工作完成后）；`TestDuckDbSingleWriter.java`（2 处 Thread 用例：testParallelIndependentFiles 的 close 在各 worker 线程自身 finally :141（线程封闭）；testSameJvmMultiReadSingleWrite 的 close 在主线程 await 后 finally :190——均与在途 open 无交错） | **不会红**：无任何 close 与在途 open 交错的时序；断言面（closed 后拒绝、连接随 close 关闭）在竞态不发生时全数通过 | ENGINE_CLOSED 契约有锚（缺陷修复将复用该错误码）；交错窗口零保护 |
| 3 | C1-003（P2） | Edge.attrs 非 final 非 volatile 可变字段可见性缺口 × InMemoryGraph blanket 线程安全声明 | `TestApiTypes.java:31-37`（testEdgeAttrs，单线程 set/get）；`TestCodeRelationGraphLoader.java:116-120,135,150,220`（构造期 attrs 内容） | **不会红**：全单元无跨线程 Edge.attrs 用例（grep 证据见缺保护登记 P10-P2-1）；单线程语义断言与可见性无关 | attrs 内容契约有锚（单线程）；可见性面零保护 |
| 4 | C1-R2-001（P1，并入 **C6-001** 缺陷族主登记） | 双台账（OPEN_FILES + connections）释放挂接点锚在 engine.close → 单例长驻泄漏 + 跨 engine 过期指纹误拒 | `TestDuckDbEngine.java:151-154`（close 后异配置复用合法——**仅显式 close 形态**）；`TestDuckDbDeepSpill.java:148-172`（50 次开合循环 + close 后 bare DriverManager 复开，注释明示**绕开指纹注册表**） | **不会红**：生产触发形态 = 单例 engine 不 close、step 逐连接裸关（TWRS），测试全部显式 close engine 后再验证；registry 侧泄漏在测试形态中恰被 close 兜底 | close 侧 refs 释放契约有锚（修复回归时 :151-154 会红）；缺陷本体（step 路径只增不减）零保护 |
| 5 | C6-001（P1，账本泄漏族**主登记**，吸收 C1-R2-001/CS-002/C6-R2-001） | OPEN_FILES 只注册不释放：静态注册表 engine 存活期单调泄漏 + 过期指纹误拒 | 同 #4：`TestDuckDbEngine.java:151-154`；`TestDuckDbE2EPipeline.java:84-90`（以「anchor fingerprint 必须与 bean engine 匹配」**绕开**误拒，反证缺陷已知而未测） | **不会红**：同 #4；E2E 用精确 fingerprint 匹配使守卫永不误触，泄漏本体无断言 | 主登记缺陷面与 #4 共享同一结论：close 形态有锚、生产形态零保护 |
| 6 | C6-002（P1，竞态族**主登记**，吸收 C1-002/CS-CL-201） | open/close 竞态：迟到连接 track 进已关闭引擎 → native 句柄 + 单写者文件锁泄漏 | 同 #2：`TestDuckDbEngine.java:84-98`；`TestDuckDbSingleWriter.java:52-100`（跨进程锁语义，非本竞态） | **不会红**：无交错用例；文件锁断言（FILE_LOCKED bizFatal）针对外部持锁进程，不覆盖 engine 自身停机窗口 | 同 #2 |
| 7 | C6-003（P2，deleteOnExit 族**主登记**，吸收 CS-003） | readXlsx xlsx→CSV 转换失败路径临时文件不删；deleteOnExit 全局 hook 只增不删 | `TestDuckDbFiles.java:319-339,343-352`（testXlsxBridge / testXlsxWithMissingValues / testE2eXlsxToParquet，全部 happy path）；`TestDuckDbE2EPipeline.java:91` | **不会红**：无损坏 xlsx 触发 L74-75 失败的用例，无任何临时文件清理断言（grep `deleteOnExit\|tempCsv` nop-duckdb/src/test = 0 命中） | 失败路径零保护；同模块存在「失败不残留产物」断言先例（TestDuckDbFaultInjection.java:92）可复制 |
| 8 | C6-R2-001（P2，并入 C6-001 作载体 B） | connections 列表死条目单调累积（含已关闭 Connection 引用） | **无**（内部账本零断言：`grep -rn "OPEN_FILES\|registerOpen\|releaseOpen\|TrackedConnection" nop-duckdb/src/test --include="*.java"` = 1 命中且为注释，TestDuckDbE2EPipeline.java:85） | **不会红**：无测试持有 engine 内部账本的任何引用或反射读取 | 零保护 |
| 9 | C6-R2-002（P1） | materialize 无 tableExists 守卫 →「CTAS 已提交 + 步未完成」孤儿表 → 重试/恢复永久必败 | `TestDuckDbFiles.java:113-119`（testTableExistsFailsFast——**同族 ingest 守卫**锚，非 materialize 面）；`TestDuckDbTaskStep.java:85-95`（retry 用例首败在 super.execute 之前抛出，无孤儿表可撞）；`TestDuckDbResume.java:44-86`（resultTable 步单次执行） | **不会红**：全部 resultTable 用例（pipeline/deep-spill/e2e/resume 共 6 处 XML，grep `resultTable` = 6 命中）均单次执行、成功路径；无「步内失败后重放同一持久 dbPath」用例 | 同族守卫不对称的 ingest 侧有锚（正好反衬 materialize 侧裸奔）；缺陷本体零保护 |
| 10 | C6-R3-001（P3） | resume/重试重放对 caller SQL 的幂等性要求未声明（模块自带样例即非幂等） | `TestDuckDbResume.java:44-86`（kill 模拟在 `<suspend>` 边界，步内提交窗口未覆盖；setup 的 `CREATE TABLE res` 在 resume 时已被状态存储跳过） | **不会红**：中步 kill（executeSql 已提交、步完成态未落盘）无用例；样例非幂等 SQL 从未被重放 | resume happy path 有锚；幂等契约与中步窗口零保护 |
| 11 | CS-001（P1，不可中断族**主登记**，吸收 CS-R2-001 调用面 + C6-CL-031） | runWithTimeout 超时后幽灵线程不可终止（CWTS 纯 CPU 循环零中断响应 + 无 future.cancel + 非 daemon） | `TestCommunityAlgorithms.java:46-56`（testLeidenDetectsTwoCommunities，小图 happy path） | **不会红**：无任何测试触发 timeoutMs（grep `timeoutMs\|setTimeoutMs\|nanoTime\|currentTimeMillis\|@Benchmark\|assertTimeout` nop-graph 两模块 src/test = 0 命中）；无线程数量/命名断言（grep `Thread\|Concurrent\|Executor` nop-graph src/test = 0 命中） | 算法正确性有锚；超时路径与线程面零保护 |
| 12 | CS-002（P2，并入 C6-001，关闭为别名） | 连接台账 append-only（C5/C7 角度：无界增长 + 陈旧指纹误拒） | **无**（同 #8：内部账本零断言，grep = 1 注释命中） | **不会红**：同 #8 | 零保护（随主登记 C6-001 收敛） |
| 13 | CS-003（P3，并入 C6-003 作 deleteOnExit 子面） | 每次 readXlsx 向 JVM 级 DeleteOnExitHook 追加条目（只增不删） | **无**（`grep -rn "deleteOnExit\|tempCsv" nop-duckdb/src/test --include="*.java"` = 0 命中） | **不会红**：JDK 全局注册表无任何测试触点 | 零保护（随主登记 C6-003 收敛） |
| 14 | CS-004（P2，邻接分配放大族**主登记**，吸收 CS-R2-003） | PageRank 每条入边触发前驱邻接表整表拷贝（Θ(E·d̄)/迭代分配放大） | `TestAlgorithms.java:189-203`（testPageRankStarGraph，正确性）；`TestGraphMetricMaterialization.java:111-165`（生产路径端到端正确性）；`TestDuckDbPerfBaseline.java:27-28`（明示 "timings are only recorded (no threshold assertions)"，且不覆盖 graph 算法） | **不会红**：分配放大不破坏任何正确性断言；无性能阈值断言 | 正确性有锚（可防修复引入功能回归）；性能面零保护 |
| 15 | CS-005（P3，独立保留） | TopologicalSort 以 ArrayList 为队列 + remove(0)（Θ(V²) 元素搬移） | `TestAlgorithms.java:158-184`（DAG 排序 + 环抛错，正确性） | **不会红**：小图上 V² 不可观测；无性能断言（同 #11 grep = 0） | 正确性有锚；性能面零保护（现状已修正为潜伏 API） |
| 16 | CS-R2-001（P2，并入 CS-001 作调用面失效面） | Leiden 5 个调用点零节点规模上界守卫（重算法无守卫、轻算法有 10⁴ 守卫的不一致） | `TestGraphAnalysisE2E.java:86-88`、`TestGraphMetricMaterialization.java:111-165`、`TestExplorationQuestions.java:84-91`（小图走守卫通过侧） | **不会红**：测试图规模远低于 10⁴，守卫缺失/存在均不改变断言结果；`grep -rn "MAX_NODES_FOR_COMMUNITY\|MAX_NODES" nop-code/nop-code-service/src/test --include="*.java"` = 0 命中 | 调用点功能正确性有锚（防硬崩溃）；守卫面零保护 |
| 17 | CS-R2-002（P2，独立保留，不可中断族 cross-ref） | BetweennessCentrality.compute 无超时/中断参数（Brandes 同步占住请求线程不可取消） | `TestCommunityAlgorithms.java:89-107`（testBetweennessCentralityBridgeNode，小图正确性） | **不会红**：小图秒回；无超时/取消/墙钟断言 | 正确性有锚；超时面零保护 |
| 18 | CS-R2-003（P3，并入 CS-004 作生产路径载体） | CodeCallGraph 适配器每次邻接查询新建全量 Edge 对象（CS-004 生产路径放大加倍） | **无直接测试**（`grep -rln "CodeCallGraph" nop-code/nop-code-core/src/test nop-code/nop-code-service/src/test nop-graph/nop-graph-api/src/test nop-graph/nop-graph-core/src/test --include="*.java"` = 0 命中）；间接经 `TestGraphMetricMaterialization.java:111-165` 生产路径正确性覆盖 | **不会红**：分配层数不破坏正确性断言；无分配量断言 | 功能面有间接锚；放大本体零保护 |
| 19 | CS-R2-004（P3，独立保留，潜伏） | ShardBuildCoordinator 在 ORM 事务内执行全量图指标物化（事务持有时长 = 计算时长；当前无 beans 装配） | `TestShardBuildCoordinator.java:89-109`（testFinalShardPublishesAtomically——断言原子发布 + metric 行存在，直接以 new 装配并触发缺陷形态） | **不会红**：测试断言的原子性契约在「计算入事务」下成立（缺陷形态恰是其通过前提）；无事务时长/连接占用断言 | 原子发布语义有锚；事务时长面零保护（且测试固化了缺陷形态为装配前提） |

**矩阵规模自查**: 19 行 = C1×4（#1-4）+ C6×6（#5-10）+ CS×9（#11-19），与三维最终保留项一一对应；并入条目已标注主登记（#2→C6-002、#4/#5 族、#8→C6-001 载体 B、#12→C6-001 别名、#13→C6-003 子面、#16→CS-001 调用面、#18→CS-004 生产载体）。

---

## 二、缺保护登记

### P1（逐条，4 个缺陷面）

**[P10-P1-1] 账本泄漏族（C6-001 主登记；覆盖 C1-R2-001 / CS-002 / C6-R2-001）**
- 缺口：无「单例 engine 长驻 + N 次任务执行不 close」形态的测试；现有锚（TestDuckDbEngine.java:151-154）只保护 close 侧释放原语，TestDuckDbDeepSpill.java:148-172 的句柄泄漏代理以 bare DriverManager 有意绕开注册表，对 registry 泄漏不敏感。
- 建议测试形态：单例 `DuckDbEngine` 上连续 K 次 `openFile+裸关`（模拟 DuckDbSqlTaskStep TWRS 形态）→ 断言 (a) `OPEN_FILES` 对应条目 refs 归零并移除（包内可见性或包级反射钩子），(b) `connections` 不留存已关闭连接条目，(c) 之后异配置 engine 复用同路径不抛 CONFIG_CONFLICT（= TestDuckDbEngine:151 场景的单例变体，即审计建议的「回退必红」改造）。修复验证须双载体同测（openFile 与 openMemory 两入口）。

**[P10-P1-2] open/close 竞态族（C6-002 主登记；覆盖 C1-002）**
- 缺口：无 close 与在途 open 交错的用例；窗口宽度为一次 JDBC connect，现有测试全部顺序先开后关。
- 建议测试形态：利用现有 `newJdbcConnection` override 缝（BrokenNativeEngine 先例，TestDuckDbEngine.java:197-202）注入 latch——T1 在 connect 慢区阻塞 → T2 `close()` → 放行 T1 → 断言 (a) openFile/openMemory 抛 `ERR_DUCKDB_ENGINE_CLOSED`，(b) 该连接已被关闭（bare DriverManager 可立即重开文件，锁无残留），(c) 二次 close 幂等。两个入口（openFile/openMemory）同测。

**[P10-P1-3] materialize 孤儿表 / 重试永久必败（C6-R2-002）**
- 缺口：全部 resultTable 用例单次成功执行；「CTAS 已提交后失败 → 重放同一持久 dbPath」窗口零覆盖；同族 ingest 守卫有锚（TestDuckDbFiles.java:113-119）反衬 materialize 侧裸奔。
- 建议测试形态：(a) 守卫修复后——持久 dbPath 上预置同名 resultTable → 执行步 → 断言 `ERR_DUCKDB_TABLE_EXISTS` + bizFatal（与 ingest 口径对齐）；(b) 重放回归——仿 TestFlakyDuckDbStep 但在首执行 `executeUpdate` 成功后抛可恢复异常 → `<retry>` 装饰器重放 → 断言任务终态与孤儿表处置符合声明的恢复语义。

**[P10-P1-4] Leiden 幽灵线程 + 调用面守卫（CS-001 主登记；覆盖 CS-R2-001）**
- 缺口：graph 侧 0 并发/0 超时断言（grep 证据见矩阵 #11/#16）；生产调用点守卫永不触发。
- 建议测试形态：(a) 超时路径——`LeidenConfig.create().setTimeoutMs(1)` + 中等规模图（保证 findClustering 超时）→ 断言 detect 返回且 `algorithmUsed=LABEL_PROPAGATION`（降级语义既有），(b) 幽灵可观测——修复采用命名 ThreadFactory 后，断言超时后按名存活线程 ≤ 预期上限（无命名则先以线程总数上界弱断言），(c) 守卫面——对 `runCommunityDetection`/`GraphMetricMaterializer.detectCommunities` 单测：nodes 超过 `MAX_NODES_FOR_COMMUNITY_DETECTION` 时不进入 Leiden（复用既有常量，diffGraph 双侧同测）。

### P2（逐条，4 个缺陷面）

**[P10-P2-1] Edge.attrs 可见性契约（C1-003）**
- 缺口：全单元零跨线程 Edge 用例；既有锚全为单线程语义/内容。
- 建议测试形态：可见性缺陷本身难以用常规 JUnit 确定性钉死，主保护来自修复后的契约锚——若采纳 volatile 方案，以反射断言 `Edge.class.getDeclaredField("attrs").getModifiers()` 含 `volatile` 作为回归必红锚；辅以多线程 setAttrs/getAttrs 压力用例（弱断言：读值 ∈ {写入值, emptyMap}，不作严格 happens-before 判定）。修复走 plan-first（nop-graph-api 公共 API）。

**[P10-P2-2] xlsx 桥接临时文件失败路径 + deleteOnExit（C6-003 主登记；覆盖 CS-003 子面）**
- 缺口：`grep -rn "deleteOnExit\|tempCsv" nop-duckdb/src/test --include="*.java"` = 0 命中；xlsx 用例全 happy path。
- 建议测试形态：损坏 xlsx（非 zip 字节写入 .xlsx 文件）→ `assertThrows(NopDuckDbException.class, () -> DuckDbFiles.readXlsx(...))` → 断言系统临时目录无 `duckdb-xlsx-` 前缀残留（复制 TestDuckDbFaultInjection.java:92「失败步不留残缺产物」的既有断言先例）。

**[P10-P2-3] PageRank 邻接分配放大，双路径（CS-004 主登记；覆盖 CS-R2-003 生产载体）**
- 缺口：无性能断言（graph 测试 grep 计时/超时锚 = 0；TestDuckDbPerfBaseline 明示不设阈值且不覆盖算法）；CS-R2-003 无直接测试（CodeCallGraph 测试引用 = 0 命中）。
- 建议测试形态：性能缺陷不宜用墙钟断言（CI 抖动）；推荐结构性断言——以计数代理 IGraph（包装 getOutEdges/inEdges 计数）执行 PageRank.compute，修复（预计算 outDegree / IGraph 增 degree API）后断言邻接查询次数为 O(V) 量级上界而非 O(E·d̄)；正确性回归须双路径同测（InMemoryGraph + CodeCallGraph，后者经 TestGraphMetricMaterialization 生产路径）。

**[P10-P2-4] Betweenness 无超时/中断（CS-R2-002）**
- 缺口：唯一测试为小图正确性（TestCommunityAlgorithms.java:89-107）。
- 建议测试形态：若按建议补 timeout 包装——小图 + timeout≈0 → 断言显式超时错误（非无限阻塞）；若采边数守卫方案——对 compute 前置守卫单测（V×E 乘积超限 fail-fast）。测试须声明「底层 Brandes 不可中断」的语义边界，避免钉死错误承诺。

### P3（聚合，4 个缺陷面）

- **C1-001（OPEN_FILES 冲突 TOCTOU）**: 建议形态为 latch 对齐的并发同文件异配置 openFile 压力用例，弱断言最终 refs 一致、无永久污染；若合并裁定接受现状（native 层兜底），则以 javadoc 契约测试钉定「JVM 层检查为尽力快速失败」即可，优先级低。
- **C6-R3-001（caller SQL 重放幂等声明缺口）**: 声明面缺陷，常规行为测试不可达；可选样例契约测试（扫描模块自带 task XML 中指向持久 dbPath 的非幂等 caller SQL 形态）作为文档回归锚；登记观察，不强制。
- **CS-005（TopologicalSort Θ(V²)）**: 纯性能、一行修复（ArrayDeque）；TestAlgorithms.java:158-184 正确性锚已足够防功能回归，无需新增测试。
- **CS-R2-004（事务内物化，潜伏）**: 当前无装配（仅测试直接 new）；TestShardBuildCoordinator 已锚定原子发布语义。装配激活升 P2 时补「计算移出事务后发布仍原子」回归（现有测试可直接复用）+ 事务时长可观测断言；当前登记 watch-only。

---

## 三、汇总

1. **矩阵规模**: 19 行，与三维最终保留项一一对应（并入条目 7 处已标注主登记：C1-002→C6-002、C1-R2-001→C6-001 族、C6-R2-001→C6-001 载体 B、CS-002→C6-001、CS-003→C6-003、CS-R2-001→CS-001、CS-R2-003→CS-004）。
2. **会红判定**: **会红 0 / 不会红 19**。全部 19 个缺陷在现有测试套件下静默通过——无一现有断言位于任一缺陷的触发条件与可观测后果的交集上。
3. **P1 保护力结论**: 4 个 P1 缺陷面（账本泄漏族、open/close 竞态族、materialize 孤儿表、Leiden 幽灵线程）**全部零红保护（0/4）**；其中两族存在「相邻契约锚」（TestDuckDbEngine.java:151-154 close 侧 refs 释放、:84-98 closed 引擎拒绝语义、TestDuckDbFiles.java:113-119 同族 ingest 守卫），只防修复回归的部分侧面，不防缺陷本体。共性根因：nop-duckdb 测试全部为「先开后用后关、close 在工作完成后」的顺序形态，单例长驻/交错/失败重放三类生产形态无用例；nop-graph 侧 3 个测试文件 0 并发、0 超时、0 性能断言（grep 均为 0 命中）。
4. **缺保护登记数**: P1 逐条 4 项 + P2 逐条 4 项 + P3 聚合 4 面 = **12 项登记**（19 实体按缺陷面去重合并别名后）；每项附建议测试形态，其中 P10-P1-1（账本泄漏单例形态）与 P10-P1-3（materialize 重放）因缺陷为确定性触发（非竞态）建议优先补测。
5. **边界声明**: 本评估只读，未修改产品代码与三维审计文件；「不会红」判定基于断言面静态核对 + HEAD 6934df7ad3 live code，未运行测试套件（三维审计已确认各测试在缺陷存在下通过，与静态结论一致）。

---

## 复核结论（C10 独立复核子代理，2026-10-22）

- **复核方式**: 抽样复核（非重做），审计工作树 HEAD 6934df7ad3 实测一致；只读三维文件，未修改产品代码。
- **抽样清单与结果（19 行全覆盖，其中 P1×4 缺陷面逐一重演）**:
  - P1-1 账本泄漏族（#4/#5，并含 #8/#12）: 重演通过。TestDuckDbEngine.java:151-154 确为「显式 close 后异配置复用合法」的 close 侧锚；TestDuckDbDeepSpill.java:148-172 确以 bare DriverManager 复开且注释（:143-146）明示绕开指纹注册表；TestDuckDbE2EPipeline.java:84-90 注释反证误拒已知。源码侧证实 `releaseOpen` 仅挂接在 `DuckDbEngine.close()`（DuckDbEngine.java:116），`registerOpen` 每次 openFile 均执行（:98）——「相邻契约锚只防回归侧（close 释放原语）、不防缺陷本体（step 路径只增不减的单例形态）」判定成立。
  - P1-2 open/close 竞态族（#6，并含 #2）: 重演通过。TestDuckDbEngine.java:84-98 close 均在工作完成后；TestDuckDbSingleWriter 全文件无 close 与在途 open 交错（:141 worker 线程封闭、:190 await 后）；FILE_LOCKED 断言（:83-87）确针对外部持锁进程。会红判定不受影响（发现 1 处描述修正，见下）。
  - P1-3 materialize 孤儿表（#9）: 重演通过。ingest 守卫锚 TestDuckDbFiles.java:113-119（readCsv TABLE_EXISTS）与 materialize 裸 CTAS（DuckDbSqlTaskStep.java:124-126，无 tableExists）不对称属实；retry 用例首败在 `super.execute` 之前（TestFlakyDuckDbStep.java:17-20），无孤儿表可撞；`grep -rn resultTable nop-duckdb/src/test` = 6 命中（4 个任务 XML），全部单次成功路径。
  - P1-4 Leiden 幽灵线程（#11，并含 #16/#17）: 重演通过。LeidenDetector.runWithTimeout（LeidenDetector.java:196-204）`newSingleThreadExecutor`（非 daemon）+ 无 `future.cancel` + `shutdownNow` 对纯 CPU 循环无效；TestCommunityAlgorithms.java:46-56 小图 happy path（仅设 maxIterations）；`grep timeoutMs|nanoTime|@Benchmark|assertTimeout|Thread|Concurrent|Executor`（nop-graph 两模块 src/test）= 0 命中；`grep MAX_NODES`（nop-code 两模块 src/test）= 0 命中，而守卫常量在 main 侧存在（CodeGraphService.java:68、GraphMetricMaterializer.java:62）——守卫面零保护属实。
  - P2/P3 抽样: #3（Edge.java:23 `attrs` 非 final 非 volatile；TestApiTypes.java:31-37 单线程）、#7/#13（readXlsx 源码：临时文件在转换前创建并 deleteOnExit、catch 路径不删；`grep deleteOnExit|tempCsv` src/test = 0 命中）、#14/#15（TestAlgorithms.java:158-203 正确性锚、TestDuckDbPerfBaseline.java:27 "no threshold assertions"）、#1（TestDuckDbEngine.java:130-155 顺序形态）、#10（TestDuckDbResume.java:42-89 kill 在 `<suspend>` 边界、resultTable 步单次执行）、#18（`grep -rln CodeCallGraph` 四测试根 = 0 命中）、#19（TestShardBuildCoordinator.java:89-109 直接 new 装配触发缺陷形态；main resources 无 beans 装配引用）——全部与矩阵一致。
- **数字核对**:
  1. 完整性: 19 行 = C1×4（#1-4）+ C6×6（#5-10）+ CS×9（#11-19），与三维 `## 最终保留项` 一一对应；并入标注与三维处置列一致。
  2. 会红判定: **会红 0 / 不会红 19**，逐行复核无一推翻。
  3. 缺保护登记: P1 逐条 4 + P2 逐条 4 + P3 聚合 4 = **12 项**；19 行按缺陷面归并映射闭合（#4/#5/#8/#12→P10-P1-1、#2/#6→P10-P1-2、#9→P10-P1-3、#11/#16→P10-P1-4、#3→P10-P2-1、#7/#13→P10-P2-2、#14/#18→P10-P2-3、#17→P10-P2-4、#1/#10/#15/#19→P3 四面），无遗漏、无重复计数。
- **发现并已修正的错误（均不改变任何会红判定）**:
  1. 汇总第 1 条「并入条目 6 处」漏计 C6-R2-001→C6-001（载体 B），与矩阵自查行（列 7 处）不一致——已改为 7 处并补入该条。
  2. 矩阵 #2「3 处 Thread 用例的 close 全部在 worker join 后 finally 执行」计数与描述不准（实际 2 处 Thread 用例；testParallelIndependentFiles 的 close 在 worker 线程自身 finally，线程封闭而非 join 后）——已改为精确描述，判定结论不变。
  3. 方法节测试清单漏列 CrossEngineDataset.java——已补入（nop-duckdb 测试目录实际 22 个 java 文件，与「22 个测试文件」表述现已一致）。
- **整体判定**: **PASS（with corrections）**。矩阵 19 行与三维最终保留项闭合一致，抽样重演（P1×4 全部 + 15 行其余条目）未发现任何会红判定错误；三处修正均为计数/描述层面，核心结论（19 个缺陷在现有测试套件下静默通过、P1 保护力 0/4、缺保护 12 项）成立。
