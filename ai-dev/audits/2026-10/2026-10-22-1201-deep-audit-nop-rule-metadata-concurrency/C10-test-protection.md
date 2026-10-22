# C10 测试保护力评估 — nop-rule + nop-metadata 并发审计保留发现

- **审计单元**: CRA WI 5.5 深度并发审计（nop-rule + nop-metadata），C10 测试保护力子任务
- **评估对象**: 同目录 C1/C4/C5/C7 四维「最终保留项」全部发现，实体口径 **38 条**（C1×5 + C4×4 + C5×15→14（C5-106 已并入 C5-001，按 14 计）+ C7×15）
- **核心问题**: 对每条保留发现评估——「现有测试套是否有测试能在该缺陷被引入时变红？」
- **方法**: ① 枚举双单元全部 src/test（nop-rule 7 模块 + nop-metadata 2 模块，共 160+ 测试类）；② 定位与本单元存在真并发用例的测试（`grep CountDownLatch|Atomic*|ExecutorService|new Thread` → 5 个测试类 + 2 个 mock 类 + 1 个 ThreadLocal wiring 测试，其中真·多线程并发测试 3 个：TestCheckpointActionDispatcherConcurrency / TestNopMetaEntityConcurrentNullSchemaUpsert / TestNopMetaQualityCheckpointBizModel 并发用例组）；③ 对 38 条发现逐条把「缺陷面」拆解为可检测断言面，逐一 grep/读码核实测试是否覆盖该面。**会红 = 存在断言会被缺陷引入所破坏的测试；不会红 = 无测试或断言不覆盖缺陷面。**不预设「必然 0」，逐条如实判定。
- **盲审声明**: 本文件为 C10 独立盲审产出。撰写过程中未读取 `ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`、`ai-dev/backlog/` 下任何文件；仅读取同目录四维审计文件（C1/C4/C5/C7）与 live 源码、src/test。只读评估，未修改任何产品代码与四维审计文件。
- **判定基线**: live code，commit 81a7c228e7（与四维审计基线一致）。

---

## 一、保护力矩阵表（38 行）

「现状标注」取值：
- **无保护** — 无任何测试触达该缺陷面；
- **邻接保护** — 存在同机制/同协作者测试，但断言的是另一分支或另一面，缺陷面本身引入仍不会红；
- **反向钉定** — 现有测试断言的恰是缺陷行为本身（缺陷被按建议修复移除时会红，保持/引入时不会红）。

| # | 发现编号 | 严重度 | 缺陷面（可检测断言面） | 相关测试（file:line 或「无」） | 会红判定 | 现状标注 |
|---|---------|--------|----------------------|------------------------------|---------|---------|
| 1 | C1-001 | P2 | BizModel `ensureActionDispatcher` check-then-act + publish-then-configure（`NopMetaQualityCheckpointBizModel.java:531-538,:160`） | `TestCheckpointActionDispatcherConcurrency.java:38,85`（直构 `new CheckpointActionDispatcher(null,null)`，测 dispatch per-action 并发隔离，不经 BizModel ensure* 路径）；`TestNopMetaQualityCheckpointBizModel.java:641`（同 checkpointId 并发被 R4.3 运行标记挡在 ensure 之前） | 不会红 | 邻接保护（同协作者、异缺陷面） |
| 2 | C1-002 | P2 | 构造器写 JVM 全局 `DriverManager.loginTimeout`（`MetaDataSourceConnectionProcessor.java:140-152`） | `TestMetaDataSourceConnectionSecurity.java:776-792` `testLoginTimeoutSetGlobally`（断言 buildDataSource 后全局值==5） | 不会红 | **反向钉定**（按 C1-002 建议 (b) 移除全局写将使该测试变红——现有测试是修复阻力而非保护） |
| 3 | C1-003 | P2 | RuleRuntime 输出三容器与 scope 并发声明不对称（`RuleRuntime.java:41-45,68`） | 无 | 不会红 | 无保护 |
| 4 | C1-101 | P3 | `CrossDbConfigHolder.maxCrossDbRows` public static 非 final + 快照/活读双口径 | `TestMetaJoinCrossDbMergeNullSemantics.java:173-174`（仅注释引用，`:179` 直构 `new CrossDbJoinMerger(3)`）；`TestMetaEntityProfilerResourceLimits.java:29`（注释） | 不会红 | 无保护（测试对 holder 零写点、无 final/配置通道断言） |
| 5 | C1-102 | P2 | 单例 BizModel 懒初始化字段族（11 字段 × 8 载体，无同步 check-then-act） | 无 | 不会红 | 无保护 |
| 6 | C4-001 | P2 | ensure* 惰性初始化缝（质量链子集，同 C1-001/C1-102 族） | 同 #1 | 不会红 | 邻接保护（弱） |
| 7 | C4-002 | P3 | checkpoint 全编排同步占共享 4 线程 job 调度池（`MetaQualityCheckpointScheduler.java:215`） | 无（`TestMetaQualityCheckpointScheduler.java:112-137` 仅 job 注册/fireNow/结果行功能面，无池隔离断言） | 不会红 | 无保护 |
| 8 | C4-R2-001 | P2 | 外部 JDBC socket 读无超时 + 监视器内同步占池（`MetaDataSourceConnectionProcessor.java:199-212`） | `TestMetaEntityProfilerSecurity.java:395-396`（stub `setQueryTimeout` 纯委托转发，无「生产已设超时」断言） | 不会红 | 无保护 |
| 9 | C4-R2-002 | P3 | 外部连接零复用（`buildDataSource` 每次 new SimpleDataSource） | 无 | 不会红 | 无保护 |
| 10 | C5-001 | P2 | syncExternalTables 外部 catalog 全量物化，无 limit/流式（`ExternalTableStructureReader.java:58-91`） | `TestExternalTableStructureReader.java:37-57`（方言识别/拒绝面）；`TestNopMetaEntityConcurrentNullSchemaUpsert.java:89-122`（每轮单表同步，无规模/物化断言） | 不会红 | 无保护 |
| 11 | C5-002 | P3 | migrateDataSourcesCredential 全表 findAllByQuery 无 limit（`NopMetaDataSourceBizModel.java:428-429`） | `TestNopMetaDataSourceBizModelCredential.java:564-622`（迁移正确性/rerun 幂等收敛面） | 不会红 | 无保护 |
| 12 | C5-003 | P3 | AutoClassificationProcessor 全表载入仅 isEmpty 消费（死查询，`:229-236`） | `TestMetadataPropagationUnit.java:285`（discoverClassification 行为面；空/非空两分支等价 return null，移除死查询不红） | 不会红 | 无保护 |
| 13 | C5-004 | P3 | executeQualityRulesForDataSource 规则全载 + 结果 DTO 全量累积（`NopMetaQualityRuleBizModel.java:233-250`） | `TestNopMetaQualityRuleBizModel.java:210,231`（批量正确性/失败隔离面） | 不会红 | 无保护 |
| 14 | C5-005 | P3 | 规则树递归无深度护栏（矩阵面有 `Guard.checkArgument(<500)`，六处树面无） | `TestRuleModelCompiler.java:33-54`（Guard.notEmpty/编译执行面；无矩阵护栏亦无树深断言） | 不会红 | 无保护 |
| 15 | C5-006 | P3 | deleteMeasureParseEdges 仍用 load+delete 循环（孪生已改 deleteByQuery，`NopMetaLineageEdgeQueryAction.java:497-500`） | `TestSqlParseLineageStaleEdgeCleanup.java:15-16`（measure 通道仅注释对照）、`:28-51`（sql_parse 通道钉死）；`TestLineageSourceTablesContract.java:109,125`（extractMeasureLineage 功能面） | 不会红 | 邻接保护（已修孪生通道被钉定；load+delete vs deleteByQuery 在事务性测试下功能等价，不红） |
| 16 | C5-101 | P2 | 同库 JOIN 正值 limit 不封顶 + L458 注释契约漂移（`NopMetaEntityBizModel.java:458-471`） | `TestLimitNegativeValueInvariant.java:67-71`（负 limit 分支钉死）；`TestMetaJoinTruncateOverflow.java:24-44` + `TestMetaJoinCrossDbMergeNullSemantics.java:178-196`（跨库 truncate 闸钉死） | 不会红 | 邻接保护（负值分支 + 跨库闸在位；同库正值无闸面零覆盖） |
| 17 | C5-102 | P3 | maxCrossDbRows 快照/活读双口径（C1-101 次登记，同站点） | 同 #4 | 不会红 | 无保护 |
| 18 | C5-103 | P3 | 对账 details 行数×候选数组合上界缺失（`ReconciliationExecutor.java:117` 全量 stringify） | `TestReconciliationFetchLimitMarker.java:50,63+`（行侧 fetch-limit=5 + truncated 标记钉死，mutate-fail 声明） | 不会红 | 邻接保护（行侧闸在位；乘积组合闸零覆盖） |
| 19 | C5-104 | P3 | 对账候选池逐行全量 findAllByQuery 无 LIMIT（`LocalReconciliationProcessor.java:93-103`） | 无 | 不会红 | 无保护 |
| 20 | C5-105 | P3 | search 全量索引构建 5 处 `dao.findAll()` 无界物化（`NopMetaIndexBuilder.java:207-307`） | `TestNopMetaIndexBuilder.java:50-125`（正确性/purge-once/失败上报面，无规模断言） | 不会红 | 无保护 |
| 21 | C5-107 | P3 | checkpoint mounted 规则查询无 LIMIT + summary 线性累积（`MetaQualityCheckpointExecutor.java:270-276`） | `TestMetaQualityCheckpointExecutorSchemaResolution.java:96-127`（schema 解析正确性面） | 不会红 | 无保护 |
| 22 | C5-R2-001 | P3 | lineage 四端点每请求全图重建（无复用/无投影，`NopMetaLineageEdgeQueryAction.java:417-438`） | `TestNopMetaLineageEdgeSizeLimit.java:57-83`（maxEdges fail-fast 钉死） | 不会红 | 邻接保护（上限存在性钉定；重建成本/投影面零覆盖） |
| 23 | C5-R2-002 | P3 | lineage 抽取面全列物化（sourceSql mediumtext 无谓载入，`:442-456`） | 无 | 不会红 | 无保护 |
| 24 | C7-001 | **P1** | 质量/剖析/目录外部查询无 statement 超时（`MetaQualityRuleExecutor.java:706-782` 等 14 处） | 同 #8（stub 委托无断言） | 不会红 | 无保护 |
| 25 | C7-002 | P2 | `executeScheduledCheckpoint` 仅 catch Exception，Error 逃逸→job 永久 FAILED（`MetaQualityCheckpointScheduler.java:216-233`） | `TestMetaQualityCheckpointScheduler.java:212-239`（**Exception 面** job 存活钉死）；`TestNopMetaQualityCheckpointBizModel.java:710-770`（并发拒绝 WARN 面） | 不会红 | 邻接保护（Exception 半边钉死；**Error 半边零覆盖**——测试套无任何向调度链注入 Error 的用例） |
| 26 | C7-003 | P2 | 检查点逐规则 N 次建连（`MetaQualityCheckpointExecutor.java:129-186,319`） | 无（`TestNopMetaQualityCheckpointBizModel` 的 fetchCallCount 断言为 webhook HTTP 面，非 JDBC 建连计数） | 不会红 | 无保护 |
| 27 | C7-004 | **P1** | 六张 append-only 时序表零 retention（`QualityResultWriter.java:57-68` 等） | 无 | 不会红 | 无保护 |
| 28 | C7-005 | P3 | webhook/notify 投递失败无重投/无死信（`CheckpointActionDispatcher.java:197-202`） | `TestCheckpointActionDispatcher.java:53-84`（失败记入 errors 的隔离语义钉死） | 不会红 | 邻接保护（隔离面钉死；重投/死信面零覆盖） |
| 29 | C7-006 | P2 | 对账逐行全池重载 N+1（`ReconciliationExecutor.java:89-91` × `LocalReconciliationProcessor.java:93-103`） | `TestNopMetaReconciliationBizModel.java:66-154`（评分/执行正确性面，无查询次数断言） | 不会红 | 无保护 |
| 30 | C7-007 | P3 | 告警工作流无去重（`QualityAlertWorkflowProcessor.java:67-98`） | `TestQualityAlertWorkflowProcessor.java:39-45`（仅错误码 + wfManager null 面） | 不会红 | 无保护 |
| 31 | C7-101 | P2 | catalog/manifest/profiling 时序表零 retention（`NopMetaDataSourceBizModel.java:880-892` 等三写点） | 无 | 不会红 | 无保护 |
| 32 | C7-102 | **P1** | 外部语句执行全链无 queryTimeout（超时族主登记，`MetaDataSourceConnectionProcessor.java:269-297` 收口） | 同 #8/#24 | 不会红 | 无保护 |
| 33 | C7-103 | P3 | EXTERNAL_TABLE_UPSERT_LOCKS 无驱逐、陈旧键残余（watch，`NopMetaDataSourceBizModel.java:1053,1070`） | `TestNopMetaEntityConcurrentNullSchemaUpsert.java:89-122`（per-key 锁**收敛正确性**钉死，AR-07 回归） | 不会红 | 邻接保护（锁正确性钉死；无驱逐/陈旧键面零覆盖） |
| 34 | C7-R2-001 | **P1** | 规则节点级 CRUD 不触发 RuleModel 缓存失效，陈旧规则无限期服务（`DaoRuleModelLoader.java:84`） | 无（`TestNopRuleDefinitionBizModel.java:112` testUpdateByFile 为**定义级**保存路径 = 正常失效路径，反而可能掩盖缺口）【C10 复核修正：原引 `TestRuleService.java:112` 系笔误，该文件仅 26 行无此测试】 | 不会红 | 无保护 |
| 35 | C7-R2-002 | P3 | checkpoint run 无持久化状态机（run 实体 0 命中，AR-14 补写失败仅日志） | 无 | 不会红 | 无保护 |
| 36 | C7-R2-003 | P3 | reJudge 原地改写时序结果行，破坏 insert-only（`QualityAlertWorkflowProcessor.java:130-135`） | `TestNopMetaQualityResultApprove.java:46-67`（approve **动作**零副作用钉死；不经 verify 步 reJudge 链） | 不会红 | 邻接保护（同实体、异写点） |
| 37 | C7-R2-004 | P3 | syncExternalTables upsert-only 无删除对账，幽灵表单调累积（`NopMetaDataSourceBizModel.java:646-660`） | 无（现有 sync 测试均为 upsert 正确性面） | 不会红 | 无保护 |
| 38 | C7-R3-001 | P2 | wf.start 半程失败残留 CREATED 孤儿实例（`NopMetaQualityRuleBizModel.java:175-178` 吞异常 + `WorkflowEngineImpl.java:213-226`） | `TestQualityAlertWorkflowProcessor.java:39-45`（仅错误码/wfManager null 面；无 start 半程失败→孤儿断言） | 不会红 | 无保护 |

### 「无相关测试」grep 复现口径（命中数均为实测）

| 缺陷面 | grep 命令（工作区根执行） | 命中数 |
|---|---|---|
| ensure* 惰性初始化族（#1/#5/#6 缺陷面） | `grep -rn "ensureActionDispatcher\|ensureCheckpointExecutor\|ensureTableRefExecutor\|ensureScorer" nop-rule nop-metadata --include="*.java" \| grep "/src/test/"` | **0** |
| RuleRuntime 并发输出（#3） | `grep -rn "addOutput\|Thread\|concurren" nop-rule/nop-rule-core/src/test nop-rule/nop-rule-service/src/test --include="*.java" \| grep -v import` | 1（TestRuleModelCompiler 模型构建 addOutput，非并发断言） |
| 调度池隔离（#7） | `grep -rn "LocalJobScheduler\|pool-size\|poolSize" nop-metadata/nop-metadata-service/src/test --include="*.java"` | 4（全部注释/javadoc，0 断言） |
| 建连计数/复用（#9/#26） | `grep -rn "withConnection\|SimpleDataSource\|getConnection" …/src/test \| grep -in "count\|times\|verify\|reuse\|pool"` | 仅 TestAggregationHelperTableVisibility.java:185（聚合路径单次调用 verify，非复用断言） |
| 候选池 limit（#19） | `grep -rn "loadCandidates" nop-metadata/nop-metadata-service/src/test --include="*.java"` | **0** |
| 时序表 retention（#27/#31） | `grep -rn "NopMetaCatalog\|NopMetaManifest\|ProfilingResult" …/src/test \| grep -in "delete\|purge\|clean\|retention\|ttl"` | **0** |
| run 状态机（#35） | `grep -rin "QualityRun\|runState" …/src/test` | 2（migrate rerunSummary 假阳性） |
| 热更新失效（#34） | `grep -rn "traceDepends\|DaoEntityResource" nop-rule/*/src/test --include="*.java"` | **0** |
| 幽灵表对账（#37） | `grep -rn "removedTables" …/src/test` | **0** |
| webhook 重投/死信（#28 缺陷面） | `grep -rin "redeliver\|dead.letter\|retry\|重投\|死信" …/src/test` | **0** |
| 告警去重（#30 缺陷面） | `grep -in "dedup\|幂等\|idempot" TestQualityAlertWorkflowProcessor.java` | **0** |
| 全局 loginTimeout 移除面（#2 反向） | `TestMetaDataSourceConnectionSecurity.java:788-791` 显式断言 | 1（断言全局写**存在**） |

---

## 二、缺保护登记（修复优先序：P1 → P2 逐条，P3 聚合）

### P1（4 条，全部「无保护」——最高优先补测）

| # | 发现 | 建议测试形态（一句话） |
|---|---|---|
| 1 | C7-102 外部语句执行全链无 queryTimeout | 对 `MetaDataSourceConnectionProcessor.withConnection` 注入记录型 Connection/Statement stub，断言 callback 内创建的每条 Statement 均被 `setQueryTimeout(n)/setNetworkTimeout` 调用（缺省值钉死）。 |
| 2 | C7-001 质量链外部查询无超时（同缺陷次登记） | 随 C7-102 单点收口测试一并覆盖；如分修，对 `MetaQualityRuleExecutor.queryLong/queryTimestamp/querySingleValue` 用记录型 Statement 断言超时已设。 |
| 3 | C7-004 六张 append-only 表零 retention | 对 retention 机制（清理 job 或 purgeBefore action）写行为回归：插入 N 行→执行清理→断言保留窗口外行被删、窗口内行与被引用行留存（mutate-fail：无清理机制时 purge 断言必红）。 |
| 4 | C7-R2-001 节点级 CRUD 不触发规则缓存失效 | 集成测试：加载并执行规则→经 `NopRuleNodeBizModel` 标准 mutation 改节点谓词→（快进失效检查间隔后）再执行同一规则，断言行为随新节点变化（现状必红=判别性回归）。 |

### P2（13 条，逐条登记）

| # | 发现 | 现状标注 | 建议测试形态（一句话） |
|---|---|---|---|
| 5 | C1-001 ensureActionDispatcher publish-then-configure | 邻接保护 | 并发测试：两线程分别经两个不同 checkpointId 首次触发 executeCheckpoint，收集实际使用的 dispatcher，断言单实例且 `webhookAllowedHostsCsv/webhookTimeoutSeconds` 等于部署配置值。 |
| 6 | C1-002 构造器写 JVM 全局 loginTimeout | **反向钉定** | 先解除 `testLoginTimeoutSetGlobally`（:776-792）对全局写的钉定（改断言连接属性路径），再补「构造器不触碰 DriverManager 全局态」断言（构造后全局值不变）。 |
| 7 | C1-003 RuleRuntime 输出容器契约不对称 | 无保护 | 契约测试（单线程断言面即可落地）：按建议 (a)/(b) 二选一后，钉死「输出写入仅限执行线程」契约或并发容器选型（如契约文档化则断言 fail-fast 守卫存在）。 |
| 8 | C1-102 懒初始化字段族 11 字段×8 载体 | 无保护 | 结构性断言测试（反射扫描 8 载体类，断言懒初始化实例字段均带 `volatile` 修饰或经 synchronized/holder 路径初始化——低成本防回归网）。 |
| 9 | C4-001 ensure* 惰性缝（质量链子集） | 邻接保护 | 随 C1-001/C1-102 合并测试覆盖（同族一次断言）。 |
| 10 | C4-R2-001 外部 JDBC 无界挂起 + 监视器内占池 | 无保护 | 随 C7-102 超时收口测试覆盖；补充对 `conn.setNetworkTimeout` 在 withConnection 内被调用的断言。 |
| 11 | C5-001 syncExternalTables 外部 catalog 全量物化 | 无保护 | 对 syncExternalTables 注入多表外部 catalog + 记录型查询/物化计数，断言单轮同步的物化面受配置上界约束或分批流式执行（修复后形态；现状全量物化不红，判别性回归随修复补）。【C10 复核修正：本条原误入 P3 无保护清单，按矩阵行 10 与 C5 终裁 P2 归位】 |
| 12 | C5-101 同库 JOIN 正值 limit 无硬闸 | 邻接保护 | 在 TestLimitNegativeValueInvariant 同构形态上补正值分支：`queryJoinData` 传超大正值 limit，断言按新闸抛 `ERR_PAGINATION_LIMIT_TOO_LARGE` 或被 configuredMaxQueryLimit 封顶。 |
| 13 | C7-002 Error 逃逸致 job 永久 FAILED | 邻接保护 | 仿 testScheduledCheckpointBusinessErrorDoesNotKillJob（:212）注入 `throw new Error(...)` 的 mock executor，断言 job 仍 WAITING 可再次 fireNow。 |
| 14 | C7-003 逐规则 N 次建连 | 无保护 | 记录型 connectionService 计数：同数据源 N 条规则的 checkpoint 执行后断言 `withConnection` 调用数收敛为数据源分组数（修复后形态）。 |
| 15 | C7-006 对账逐行全池重载 N+1 | 无保护 | 记录型 daoProvider 计数候选池查询次数：R 行对账断言候选池加载次数为 O(1) 而非 O(R)（修复后形态；现状引入即红，判别性）。 |
| 16 | C7-101 catalog/manifest/profiling 时序表零 retention | 无保护 | 随 C7-004 retention 行为回归一并覆盖（三写点同族断言）。 |
| 17 | C7-R3-001 wf.start 半程失败残留 CREATED 孤儿 | 无保护 | 注入 start 半程失败（actor 解析失败或 mock store 抛错）+ FAIL/ERROR 规则执行，断言事务提交后无 CREATED 零 step 的 NopWfInstance 行（清理/回滚语义回归）。 |

### P3（21 条，聚合登记）

- **无保护（15 条）**：C1-101、C4-002、C4-R2-002、C5-002、C5-003、C5-004、C5-005、C5-102（与 C1-101 同站点，并入同工单）、C5-104、C5-105、C5-107、C5-R2-002、C7-007、C7-R2-002、C7-R2-004。共性建议：C5 族缺保护本质是「内存上界/物化面」无断言传统——修复时对齐 `TestReconciliationFetchLimitMarker`（配置收紧 + mutate-fail 声明）与 `TestNopMetaLineageEdgeSizeLimit`（fail-fast 错误码钉死）两个既有先例即可低成本补网；C4-002/C4-R2-002 属执行模型治理，建议以 nop-benchmark 或记录型计数测试钉住池占用/建连次数上界。【C10 复核修正：原列 16 条误含 C5-001（P2），已归位 P2 表】
- **邻接保护（6 条）**：C5-006（孪生已修通道被 TestSqlParseLineageStaleEdgeCleanup 钉死）、C5-103（行侧闸被 TestReconciliationFetchLimitMarker 钉死）、C5-R2-001（上限被 TestNopMetaLineageEdgeSizeLimit 钉死）、C7-005（隔离面被 TestCheckpointActionDispatcher 钉死）、C7-103（锁正确性被 TestNopMetaEntityConcurrentNullSchemaUpsert 钉死）、C7-R2-003（approve 动作零副作用被 TestNopMetaQualityResultApprove 钉死，reJudge 写点异面）。六者缺陷面（measure 通道实现形态 / 乘积组合闸 / 重建成本 / 恢复面 / 驱逐面 / reJudge 原地改写面）在修复时均可仿同文件既有测试形态补断言。【C10 复核修正：C7-R2-003（P3）原误列于 P2 表，按矩阵行 36 归位本清单】

---

## 三、汇总

- **矩阵规模**：38 行（C1×5 + C4×4 + C5×14 + C7×15），与四维最终保留项逐条核对无遗漏；会红判定覆盖每行并附 file:line 或 grep 命中数证据。
- **会红 / 不会红计数**：**会红 0 / 不会红 38**。本单元虽存在真并发测试（3 个真·多线程并发测试类 + 2 个并发 mock + 若干并发原语使用），但其保护目标全部是**历史上已修复缺陷的回归面**（R4.3 运行标记、AR-07 NULL-schema upsert per-key 锁、MA7.5-01 Exception 面、P2-04 sql_parse 清理、AR-09 血缘上限、P2-07 对账行限、负 limit 不变式、跨库截断闸、profiler 排序上限、webhook 隔离），**无一覆盖本审计 38 条保留发现的缺陷面**。
- **现状标注分布**：无保护 27 / 邻接保护 10 / 反向钉定 1。
- **P1 保护力逐条结论**：
  - **C7-102（超时族主登记）**：无保护。测试套无任何「生产语句设置超时」断言（`TestMetaEntityProfilerSecurity.java:395` 仅为 stub 委托）；该缺陷面处于零覆盖，**修复后也无回归网**。
  - **C7-001（超时族次登记）**：同上，无保护。
  - **C7-004（retention 族主登记）**：无保护。零 retention 测试；且现有测试隐含「行永久保留」假设（如 TestNopMetaQualityCheckpointBizModel 多 run 行数断言），未来引入清理机制时需同步评估既有测试的兼容性（轻度修复阻力）。
  - **C7-R2-001（热更新失效传播）**：无保护。无任何「节点级编辑→再执行」行为回归测试；唯一的规则更新测试（testUpdateByFile）走定义级保存的正常失效路径，对该缺口无检测力。
- **缺保护登记数**：P1×4 + P2×13 + P3×21 = 38（全部登记；其中邻接保护 10 条、反向钉定 1 条已标注，补测建议随条目给出）。
- **最高风险结论**：四条 P1（质量监控静默停摆、全消费面无界挂起、时序表无界膨胀、规则热更新漂移）全部处于测试盲区——它们恰是「长时运行才显形」的缺陷形态，与现有测试套「单请求功能正确性 + 已修复缺陷回归」的覆盖结构正交。建议修复排期时按「每修一条 P1/P2 同步补一条判别性回归」执行（形态建议见第二节）。

---

## 复核结论（C10 独立复核子代理，2026-10-22）

复核方式：抽样复核 + 一致性核对（非重做）。基线 commit 81a7c228e7，工作区实测 grep/读码。

### 1. 抽样行清单与结果（9 行 = P1×4 全部 + P2×3 + P3×2，另附 grep 表复现）

| 行 | 发现 | 结果 | 证据（实测） |
|---|---|---|---|
| #32 | C7-102（P1） | PASS | `grep setQueryTimeout\|setNetworkTimeout` 于双单元 src/test → 命中 2 行，均为 `TestMetaEntityProfilerSecurity.java:395-396` stub 纯委托（`delegate.setQueryTimeout(seconds)`），无「生产已设超时」断言 → 不会红/无保护成立。 |
| #24 | C7-001（P1） | PASS | 同上证据面（同 #8/#24 引用链），测试套零生产超时断言 → 成立。 |
| #27 | C7-004（P1） | PASS | `grep "NopMetaQualityResult\|QualityCheckpointResult\|QualityRuleResult" \| grep -i delete/purge/clean/retention/ttl` 于 nop-metadata src/test → **0 命中**；「测试隐含行永久保留」否定性结论属实：`TestNopMetaQualityCheckpointBizModel.java:595`（2 次执行断言 rows.size()==2 "appends a new row"）、`:627`（不同 runId 断言 countResults==2 "time-series append"）、`:700`（同回归）均假设行只增不删 → 引入 retention 需评估兼容性，成立。 |
| #34 | C7-R2-001（P1） | PASS（引文修正） | 否定性结论「无节点级编辑→再执行测试」属实：`grep traceDepends\|DaoEntityResource` 于 nop-rule src/test → **0**；`invalidate\|refreshCache\|reloadRule\|evict` → **0**；`DaoRuleModelLoader` 仅 1 命中（`TestRuleDaoStructuralContracts.java:28` 注释）。**修正 1 处引文**：testUpdateByFile 实位于 `TestNopRuleDefinitionBizModel.java:112`（原引 `TestRuleService.java:112` 笔误——该文件仅 26 行、只有 testRule()；已改）。实体内容核实：该测试走 `NopRuleDefinition__update` 定义级 mutation = 正常失效路径，对 `DaoRuleModelLoader.java:84`（traceDepends 站点已核实）节点级 CRUD 缺口无检测力。 |
| #2 | C1-002（P2，反向钉定） | PASS | `TestMetaDataSourceConnectionSecurity.java:776` testLoginTimeoutSetGlobally 实测断言 `DriverManager.getLoginTimeout() == MetaDataSourceConnectionProcessor.DEFAULT_LOGIN_TIMEOUT_SECONDS`（=`5`，`MetaDataSourceConnectionProcessor.java:124`）→ 按建议 (b) 移除全局写必使该测试变红，「修复阻力」判定成立。 |
| #1 | C1-001（P2，邻接保护） | PASS | `TestCheckpointActionDispatcherConcurrency.java:38,85` 实测均直构 `new CheckpointActionDispatcher(null, null)`，不经 BizModel ensure* 路径；`TestNopMetaQualityCheckpointBizModel.java:641`（testConcurrentExecuteCheckpointFailFast）实测第二请求被 `ERR_CHECKPOINT_ALREADY_RUNNING`（R4.3 运行标记）fail-fast，ensure* 缺陷面未被并发触达 → 邻接保护/不会红成立。 |
| #25 | C7-002（P2，邻接保护） | PASS | `MetaQualityCheckpointScheduler.java:216` 实测 `catch (Exception e)`（Error 逃逸面属实）；`TestMetaQualityCheckpointScheduler.java:212` 实测钉死 Exception 半边（业务错误后 job 仍 WAITING、可再 fireNow）；`grep "throw new Error("` 于双单元 src/test → **0 命中**，Error 半边零覆盖成立。 |
| #15 | C5-006（P3，邻接保护） | PASS | `TestSqlParseLineageStaleEdgeCleanup.java:15-16` 实测 measure 通道（deleteMeasureParseEdges）仅 Javadoc 对照引用；`:28-51` 实测钉死 sql_parse 通道清理行为；measure 通道 load+delete 实现形态无断言 → 不会红成立。 |
| #18 | C5-103（P3，邻接保护） | PASS | `TestReconciliationFetchLimitMarker.java:50`（`@NopTestProperty fetch-limit=5`）+ `:63-65`（mutate-fail 声明 + 行侧闸测试）实测在位；乘积组合闸零覆盖 → 邻接保护成立（引文 `:50,63+` 与实际 :50/:63/:65 吻合）。 |

grep 表复现（4 项抽测，命中数全部实测吻合）：ensure* 族 = **0**；RuleRuntime 输出面 = **1**（`TestRuleModelCompiler.java:45` 模型构建 addOutput）；LocalJobScheduler/pool = **4**（205/1064/1120/29，全为注释）；调度链 Error 注入 = **0**。

### 2. 数字核对

- **完整性**：矩阵 38 行 = C1×5 + C4×4 + C5×14（C5-106 已并入 C5-001、正确缺席）+ C7×15，与四维 `## 最终保留项` 逐条对齐，编号一一对应，严重度与各维终裁一致（含 C1-101 降 P3、C7-R3-001 降 P2、C5-103/C5-105 降级）。**通过**。
- **会红计数**：38 行会红判定逐行核对，**会红 0 / 不会红 38**。**通过**。
- **现状标注分布**：无保护 27 / 邻接保护 10 / 反向钉定 1，与矩阵逐行核对一致。**通过**。
- **缺保护登记**：P1×4 + P2×13 + P3×21 = 38 与第三节汇总一致；**但第二节聚合表原稿有 2 处归组错误**（详见下），修正后 P2 表 13 条（补 C5-001）、P3 = 无保护 15 + 邻接 6 = 21，与矩阵完全对齐。**修正后通过**。

### 3. 修正记录（3 处，均已在正文原位修正并标注）

1. **行 #34 引文**：`TestRuleService.java:112` → `TestNopRuleDefinitionBizModel.java:112`（原引文件仅 26 行，无 testUpdateByFile；实体结论不变）。
2. **第二节 P2 表**：原表误列 P3 条目 C7-R2-003（#16）且漏列 P2 条目 C5-001；已补 C5-001（无保护）、移除 C7-R2-003，重排编号，13 条与矩阵 P2 集合一致。
3. **第二节 P3 聚合**：无保护 16→15（移除误入的 C5-001）；邻接保护 5→6（补入 C7-R2-003）。

### 4. 整体判定

**PASS with corrections**——矩阵本体（38 行）与四维最终保留项完整性、编号对应、严重度一致性全部成立；抽样 9 行（含 P1×4 逐条重演与两个否定性结论）的「相关测试」引用与「会红判定」全部经独立 grep/读码证实；错误集中于第二节 P2/P3 聚合表的条目归组（C5-001/C7-R2-003 互换错位）与 1 处文件名引文，均已修正且不改变任何会红判定与优先序结论。本复核未修改任何产品代码与四维审计文件。
