# C10 测试保护力审计 — nop-code 索引面并发审计（CRA WI 6.3）

- **审计单元**: nop-code-index（nop-code-core + nop-code-flow + nop-code-service 三模块 `src/test` 断言面，103 个测试文件 / 470 个 `@Test`）× 四维最终保留项 44 实体
- **维度**: C10 测试保护力——「现有测试是否能在该缺陷引入时变红？」
- **输入**: 同目录 C1-shared-mutable-state.md（5）/ C5-memory-bounds.md（16）/ C6-resource-lifecycle.md（11）/ C7-long-run-stability.md（12），全部已过独立复核；别名归并 4 组（主登记 C6-001 / C7-005 / C6-009 / C6-003）
- **盲审声明**: 本审计为盲审。未读取 `ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`、`ai-dev/backlog/` 下任何文件，也未读取四维文件之外的任何审计文档（含本目录 dispatch-log.md）；全部结论仅基于上述四份输入文件 + 三模块 `src/test` 逐文件直读 + 主代码锚点核对。未修改任何产品代码与四维审计文件。
- **方法**: 逐实体定位其失效面代码 → 在三模块测试面 grep/直读最近相关测试 → 判定「引入该缺陷（或移除对应防护）时现有断言是否确定性变红」→ 无相关测试的判定附 grep 命令与命中数 → 按 P1/P2 逐条、P3 聚合登记缺保护项并给建议测试形态。
- **判定口径**: 变红判据为**确定性失败**——竞态类缺陷按「引入竞态根因（如移除同步/移除失效/移除清理）后，单线程功能测试与并发冒烟测试能否稳定失败」评估；偶发/非确定性敏感不计为保护力。别名行在矩阵中占位评估，缺保护登记折入主登记防双计。
- **日期**: 2026-10-22

---

## 一、测试面总览与关键并发测试性质裁定

三模块测试面：nop-code-core 24 / nop-code-flow 5 / nop-code-service 74 个测试文件（合计 103 个文件、470 个 `@Test`；其中 2 个文件自身无 `@Test`，为抽象契约基类/纯继承型）（C10 复核修正：原分模块数 26/5/72 与实测不符，总数不变）。断言面构成：功能正确性断言为绝对主体；**并发类测试仅 2 个（均在审计单元内），内存/规模上界类断言 0 个，交错时序类断言 0 个**。

### 2026-09 G11 提及的 TestConcurrentIndexing 性质裁定

`nop-code/nop-code-service/src/test/java/io/nop/code/service/TestConcurrentIndexing.java`（76 行，1 个 `@Test`）：

- **执行形态为真并发**：3 线程 `Executors.newFixedThreadPool(3)` + `CountDownLatch` 栅栏，并行调用 `indexDirectory` 于 3 个**不同 indexId**。
- **断言面为功能冒烟**：仅断言 (a) 全部任务在 120s 内完成、(b) `errorCount==0`、(c) `successCount==taskCount`、(d) `getIndexStats` 非 null。
- **裁定：并发执行形态 + 功能冒烟断言，不是并发正确性测试。** 它对「不同 indexId 并行不互扰」这一粗粒度性质提供冒烟保护；对串行化程度、缓存一致性、交错时序、内存上界均无断言——CL-001 的全局锁把它完全串行化后该测试照常通过；C7-R2-001 恶化后亦照常通过。**对 44 实体中任何一条均无回归保护力。**

同型的 `nop-code-core/.../analyzer/TestProjectAnalyzerConcurrency.java`（4 线程×3 次共享单例 `ProjectAnalyzer` 并发 `analyzeProject`）断言「无异常 + 文件数一致」，性质相同。唯一附注：它是全单元唯一对「分析期共享可变状态」有**非确定性敏感**的测试——若未来有人在分析运行期向 `semanticExtractors` 等共享集合原地向写入（C1 型缺陷），该测试可能偶发红；按判定口径不计为保护力，登记为 watch-only 资产。

---

## 二、44 实体保护力矩阵

> 列说明：**变红?** = 现有测试在该缺陷引入时是否确定性失败；**最近相关测试/证据** = 断言面核验依据（零命中者附 grep 命令与命中数）；**缺口** = 缺保护登记归属（P1-缺 / P2-缺 逐条；P3-聚 聚合；别名行「折入」主登记）。

### C1 共享可变状态（5 条）

| # | 实体 | 判级 | 变红? | 最近相关测试 / 证据 | 缺口 |
|---|------|------|-------|---------------------|------|
| 1 | C1-001 SymbolTable 写同步/点读裸读（36 站点） | P2 | 不会红 | `TestSymbolTable` 全部 6 测试为单线程功能断言（grep `Thread\|Executor\|CountDownLatch` 命中 **0**）；`TestTruncationObservability` 仅护 `isTruncated` 标志可读性。移除 `add` 的 synchronized 后全部照过 | P2-缺 |
| 2 | C1-002 缓存失效先于锁/事务提交（4 路径） | P2 | 不会红 | `TestCacheInvalidationAlignment` 5 测试**单线程**且在方法返回后断言缓存/物化行已失效——只护「失效发生」（整体移除失效会红），不护「失效先于提交 + 并发查询回填」时序；indexDirectory/indexFileSet/deleteIndex/batchDelete 的窗口行为零断言 | P2-缺 |
| 3 | C1-003 ShardBuildCoordinator 跨事务 check-then-act | P3 | 不会红 | `TestShardBuildCoordinator` 3 测试全顺序（partial 不发布/末片发布+物化/重 begin 重置/unknown-shard 抛）；无线程交错场景，发布丢失/双重发布竞态不可触达 | P3-聚 |
| 4 | C1-004 getFingerprintStore 懒初始化竞态 | P3 | 不会红·无相关测试 | `grep -rn "getFingerprintStore" nop-code/*/src/test --include="*.java"` 命中 **0** | P3-聚 |
| 5 | C1-005 rebuildDebounce get→put 非原子 | P3 | 不会红 | `TestRebuildFromCommit.testDebounceRejectsSecondCallWithinWindow` 单线程功能防抖（30s 窗口第二调用被拒）；并发双通过不可触达 | P3-聚 |

### C5 内存有界性（16 条）

| # | 实体 | 判级 | 变红? | 最近相关测试 / 证据 | 缺口 |
|---|------|------|-------|---------------------|------|
| 6 | C5-001 图查询每请求全量物化 4 表边集 | P1 | 不会红 | `TestSurprisingConnections`/`TestGraphWikiExport`/`TestCodeRelationGraphLoader` 全功能断言（三文件 grep `MAX\|setLimit\|truncat` 仅 1 命中，为分数量级比较 `Integer.MAX_VALUE`，非上界守卫）；无行数/内存断言 | P1-缺 |
| 7 | C5-002 ProjectAnalyzer 死守卫+伪流式+全文驻留 | P1 | 不会红·无相关测试 | `grep -rn "MAX_FILE_SIZE_BYTES\|MAX_FILE_COUNT" nop-code/*/src/test` 命中 **0**；`TestProjectAnalyzerFileFilter.testLargeFilesSkipped` 名实不符——仅写入小文件并断言其被分析，无任何大文件/规模断言 | P1-缺 |
| 8 | C5-003 提取器输出无上界 + 语义边无 flush | P2 | 不会红·提取器零测试 | `grep -rn "NameSimilarity\|DocKeyword\|AnnotationPattern" nop-code/*/src/test` 命中 **3**，均为 `TestFrameworkPatternDslEquivalence` 的 `getAnnotationPatterns`（框架 DSL 注解模式，同名异义），提取器类**零引用**；e2e 间接走过边保存循环但无规模/flush 断言 | P2-缺 |
| 9 | C5-004 rebuildDependencies 无上限 | P2 | 不会红 | `TestCodeCacheManager` 4 测试护条目级 LRU/TTL/invalidate；`TestTruncationObservability` 护 symbol/edge 截断标志——deps 侧无上限无截断，均无断言 | P2-缺 |
| 10 | C5-005 replaceRows 插入侧无 flush/evict | P2 | 不会红 | `TestGraphMetricMaterialization` 4 测试为功能断言（物化行存在/失效/清空/端到端）；ORM session 驻留无断言 | P2-缺 |
| 11 | C5-006 增量路径每文件全表复制 mergedTable | P2 | 不会红 | `TestIncrementalCalleeResolution`/`TestIncrementalDependencyPropagation`/`TestTypeHierarchyAfterResolveQualifiedNames` 等为功能正确性断言；O(F×S) 放大无断言 | P2-缺 |
| 12 | C5-007 LlmEdgeCache 无界（休眠） | P3 | 不会红 | `TestLlmSemanticEdgeExtractor.cachePreventsDuplicateCalls` 护功能去重；无容量/TTL 断言 | P3-聚 |
| 13 | C5-008 findSymbols/getSymbolUsages limit<=0 无兜底 | P3 | 不会红 | `TestPackageFilterBoundary` L53/L60 两处以 `limit=0` **触达该分支**但仅断言返回内容，无上界断言（分支被覆盖、缺陷面零保护） | P3-聚 |
| 14 | C5-009 getDepGraph 无 limit 全量响应 | P3 | 不会红 | `TestDependencyPersistence` 断言图非空/内容正确；无 limit/截断断言 | P3-聚 |
| 15 | C5-R2-001 GraphMetricStore 7/8 finder 无 LIMIT | P2 | 不会红 | `TestMaterializedQueryMigration`/`TestGraphAnalysisE2E` 为物化-查询等价/功能断言；无 LIMIT 断言 | P2-缺 |
| 16 | C5-R2-002 getPublicSurface 无条件无 LIMIT | P2 | 不会红 | `TestNopCodeSymbolBizModel` L205/223/251 三处功能断言内容正确；无上界断言 | P2-缺 |
| 17 | C5-R2-003 fileTree 全量 CLOB 物化 | P2 | 不会红 | `TestIndexNopEntropyProject` 走 fileTree 功能断言树结构；无投影/内存断言 | P2-缺 |
| 18 | C5-R2-004 依赖图族输入侧邻接全量物化 | P3 | 不会红 | `TestBfsReverseTraversalDepth`（深度语义）/`TestIndexNopXlangModule` L276/286（内容断言，limit=10000 为入参非守卫断言） | P3-聚 |
| 19 | C5-R2-005 每请求两次全表投影扫描 | P3 | 不会红·无相关测试 | `grep -rn "buildFilePathCache\|filterByLanguage" nop-code/*/src/test` 命中 **0**（私有方法不可直达）；`TestCodeSearch*` 系列功能断言间接走过，读放大无断言 | P3-聚 |
| 20 | C5-R2-006 analyzeProject 触发面无规模守卫 | P3 | 不会红 | `TestIndexNopEntropyProject`/`TestIndexNopXlangModule` 索引真实仓库断言功能结果；无规模预检断言 | P3-聚 |
| 21 | C5-R2-007 社区检测守卫不一致 | P3 | 不会红 | `TestGraphAnalysisE2E.testGetCriticalNodes/testGetKnowledgeGaps`、`TestSurprisingConnections` 等功能断言，夹具远小于 10000，守卫分支（含 diffGraph 单侧不对称）从不触发 | P3-聚 |

### C6 资源生命周期（11 条）

| # | 实体 | 判级 | 变红? | 最近相关测试 / 证据 | 缺口 |
|---|------|------|-------|---------------------|------|
| 22 | C6-001 git 超时族主登记（4 站点读阶段无超时） | P2 | 不会红 | `TestChangeAnalyzer.testParseGitDiffProcessCleanupOnInvalidRefs`/`testParseGitDiffMissingWorkingDirectoryIsTruncated` 相邻保护 P1-6.7-009 失败可见性（truncated 标志）——但无效 ref 时 git 自行退出，finally destroy 与读阶段超时均未被断言（移除 finally destroyForcibly 亦不红）；挂起进程场景零测试 | P2-缺 |
| 23 | C6-002 半克隆工作区毒化 | P2 | 不会红 | `TestClusterShardingE2E.testWorkspaceLifecycle` 仅覆盖 clone 成功→复用→路径逃逸三 Happy/守卫路径；clone 失败注入零测试 | P2-缺 |
| 24 | C6-003 deleteIndex 漏删 shard ledger（主登记） | P2 | 不会红 | `TestShardBuildCoordinator` 的 unknown-shard 守卫断言在**干净账本**上验证；`TestClusterShardingE2E` 无 deleteIndex→completeShard 跨生命周期场景；孤儿 ledger 击穿守卫不可触达 | P2-缺 |
| 25 | C6-004 findSymbolIdsByFileId 单页截断 10000 | P2 | 不会红 | `TestDeletePathIntegrity` 护交叉文件孤儿清理语义（WP-4 AR 面），夹具符号数 << 10000，截断永不触发 | P2-缺 |
| 26 | C6-005 搜索双写无原子性（**别名→C7-005**） | P2 | 不会红 | 同 #37：`TestIncrementalSearchSync` 护 removeDocs 调用次序，失败/回滚面无注入 | 折入 C7-005 |
| 27 | C6-006 executor 预留字段无关闭契约 | P3 | 不会红·无相关测试 | 带 executor 的构造器零测试引用（core 测试面 grep `executor` 仅命中测试自身的线程池局部变量） | P3-聚 |
| 28 | C6-007 LlmEdgeCache 无界账本（容量口径） | P3 | 不会红 | 同 #12 | P3-聚 |
| 29 | C6-008 ManifestStore 非原子覆盖写 | P3 | 不会红 | `TestManifestStore.testLoadMalformedJson` 护 load 侧自愈（损坏→空→全量重建）；写入原子性无断言 | P3-聚 |
| 30 | C6-009 失败不写增量状态（主登记） | P3 | 不会红·无相关测试 | `grep -rn "getIncrementalStatus" nop-code/*/src/test --include="*.java"` 命中 **0**——状态观测面全单元零断言；`TestNopCodeIndexBizModel` 仅 2 测试（indexDirectory/getStats） | P3-聚 |
| 31 | C6-010 analyzeIncremental(store) 不删已删文件指纹 | P3 | 不会红 | `TestProjectAnalyzerWithStore.testIncrementalWithStoreMixedAddModifyDelete` 删 Bar.java 后仅断言变更集（2 文件+findSymbol("Baz")），不断言 store 内 Bar 指纹行清除（第三次增量会重复报 deleted 的行为无测试）；`deleteByPaths` 有 4 处独立方法级测试但「无调用方」这一缺陷本身不可触达 | P3-聚 |
| 32 | C6-R2-001 LLM RPC 无超时、cancelToken=null | P3 | 不会红·无相关测试 | 桩 `chatService` 为即时返回（grep `timeout\|Timeout\|cancel\|Cancel` 命中 2，均为 IChatService 接口签名形参，非超时测试）；无挂起/超时注入 | P3-聚 |

### C7 长时运行稳定性（12 条）

| # | 实体 | 判级 | 变红? | 最近相关测试 / 证据 | 缺口 |
|---|------|------|-------|---------------------|------|
| 33 | C7-001 读路径自愈物化无单飞（重算风暴） | P1 | 不会红 | `TestGraphMetricMaterialization` 为顺序功能断言；`TestGraphAnalysisE2E` 顺带触达自愈路径但无并发读、无 materialize 调用计数、无退避断言——风暴行为零保护 | P1-缺 |
| 34 | C7-002 集群工作区零 retention | P2 | 不会红 | `TestClusterShardingE2E` 无任何 retention/TTL/磁盘占用断言（每 SHA 一 clone 的累积不可触达） | P2-缺 |
| 35 | C7-003 git 超时不覆盖读阶段（**别名→C6-001**） | P2 | 不会红 | 同 #22 | 折入 C6-001 |
| 36 | C7-004 已删文件被依赖边（target 侧）永久残留 | P2 | 不会红 | `TestDeleteFileRecordsIndexScoping` **确定性保护 source 侧** indexId scoping（若 source 侧删除被移除会红）；target 侧幽灵边无任何断言（`TestDeletePathIntegrity` 护 calls/usage，不护 Dependency target）；`TestIncrementalDependencyPropagation` 用手工构造 Dependency 行测传播，不测删除清理 | P2-缺 |
| 37 | C7-005 搜索引擎副作用事务内+无补偿（主登记） | P2 | 不会红 | `TestIncrementalSearchSync.testReindexRemovesGhostSymbolDoc` 确定性保护「重索引先 removeDocs」调用次序（WP-7 面）；`RecordingSearchEngine` 永不抛异常——回滚发散/吞异常无失败注入，缺陷面零保护 | P2-缺 |
| 38 | C7-006 分片治理三缺口（(a)停滞/(b)同事务声明/(c)别名→C6-003） | P3 | 不会红 | `TestShardBuildCoordinator.testFinalShardPublishesAtomically` 确定性保护「末片发布触发物化」（移除 materialize 调用会红）；(a) 停滞检测零测试、(b) 「同事务」原子性声明零断言、(c) 折入 C6-003 | P3-聚 |
| 39 | C7-007 LLM 预算永不重置且命中仍扣 | P3 | 不会红 | `TestLlmSemanticEdgeExtractor.budgetExhaustionStopsCalls` 确定性保护「新鲜实例内预算限流」；每测试新建 `LlmEdgeBudget(10000)` 恰好掩蔽无重置语义——跨 run 生命周期零断言 | P3-聚 |
| 40 | C7-008 rebuildDebounce 跨生命周期残留 | P3 | 不会红 | `TestRebuildFromCommit` 以反射 `resetDebounce()` 掩蔽残留（`grep deleteIndex -A2 triggerRebuildFromCommit` 于测试面命中 **0**）——deleteIndex 后同名索引 rebuild 被静默拦截的行为无断言 | P3-聚 |
| 41 | C7-009 IFingerprintStore 两实现删除语义相反 | P3 | 不会红 | `TestIFingerprintStore` 契约测试（`testSaveUpdatesExisting` 断言覆盖语义：部分列表保存后旧路径消失）**唯一子类 = TestInMemoryFingerprintStore**；OrmFingerprintStore 无契约子类——**保护机制已建成但断开**：为 Orm 补一个 `createStore()` 子类即立即红（正是本案缺陷），系 44 实体中唯一「低成本即可激活的现成保护」 | P3-聚 |
| 42 | C7-010 batchSaveFileRecords 缺事务/会话包装 | P3 | 不会红 | `TestNopCodeIndexIdempotencyInvariant` 将 `batchSaveFileRecords` 列入 IDEMPOTENCE_TABLE（L87/L151）并护「重复调用不重复插入」面；「已存在行字段更新是否落库」面无断言（与 C7-010 自身登记的测试缺口一致） | P3-聚 |
| 43 | C7-011 triggerFullIndex 失败不写状态（**别名→C6-009**） | P3 | 不会红 | 同 #30（getIncrementalStatus 零命中） | 折入 C6-009 |
| 44 | C7-R2-001 缓存重建全局锁粒度失控 | P3 | 不会红 | `TestConcurrentIndexing`（性质见 §一）：真并发执行+冒烟断言，全局锁串行化后照常通过；串行化程度/停顿无度量 | P3-聚 |

---

## 三、缺保护登记

### P1（3 条，逐条登记——建议作为对应修复工单的验收前置测试项）

| 登记 | 实体 | 建议测试形态 |
|------|------|--------------|
| TP-001 | C5-001（含同族 C5-R2-001/002/003 的上界面） | 大结果集夹具（行数超注入上界）+ 断言：中间产物行数 ≤ 上界、截断标志置位、`warnIfCapped` 触发；上界常量可注入以便测试不必造十万行数据。回归锚点：`TestCodeRelationGraphLoader` 增补 bound 断言组 |
| TP-002 | C5-002（含 C5-R2-006 触发面） | 单文件超 `MAX_FILE_SIZE_BYTES`（注入小阈值）+ 文件数超 `MAX_FILE_COUNT` 夹具，断言 fail-fast（NopException）或截断+truncated 标志；激活死常量的同时让 `testLargeFilesSkipped` 名实相符 |
| TP-003 | C7-001 | 双线程（或 n 线程）并发读同一未物化索引 + 计数型 materializer 桩，断言 materialize 调用次数 ≤ 1（单飞）；失败注入 + 断言第二次读在冷却窗内不重试（退避）。形态参照 `TestConcurrentIndexing` 的 latch 栅栏，但断言从冒烟升级为调用计数 |

### P2（16 条主登记，逐条登记；别名行 C6-005、C7-003 折入不另计）

| 登记 | 实体 | 建议测试形态（一句话） |
|------|------|------------------------|
| TP-004 | C1-001 | 双线程交错测试：写者循环 `add` + 读者 latch 对齐后点读已知符号，断言不返回 null；或以 CHM 化修复后用 TestSymbolTable 扩并发组 |
| TP-005 | C1-002 | 双线程屏障测试：查询线程在写事务提交前回填缓存，断言提交后缓存被失效（扩 `TestCacheInvalidationAlignment` 为并发形态） |
| TP-006 | C5-003 | 提取器直测：构造同名密集符号集，断言 edges.size() ≤ 注入 MAX_EDGES 且截断有 warn；保存循环批内 flush 断言（对齐 AnnotationPatternExtractor 既有守卫作 oracle） |
| TP-007 | C5-004 | `TestCodeCacheManager` 增补 deps 上界断言：注入低 `MAX_CACHE_DEPENDENCIES`，断言截断标志置位（对齐 symbol/edge 既有模式） |
| TP-008 | C5-005 | 物化行数大于批阈值时断言插入侧分批 flush/evict（行为可观测面：session 内实体计数或分批保存计数桩） |
| TP-009 | C5-006 | 大全局表 × 多变更文件夹具下断言 mergedTable 构建次数 = 批次数（非文件数），以计数桩观测批次级复用 |
| TP-010 | C5-R2-001 | `findByMetric` 注入低上限，断言 4 个无 LIMIT finder 截断 + 装配路径 loadSymbolsByIds 仅查 topN 截断后的 id 集 |
| TP-011 | C5-R2-002 | publicSurface 注入低上限断言截断 + warnIfCapped（对齐 findReferences 范式作 oracle） |
| TP-012 | C5-R2-003 | fileTree 断言查询走投影（selectFieldsByQuery 桩 / sourceCode 不出现在的实体上） |
| TP-013 | C6-001 | 挂起进程模拟（git 外部 diff 驱动指向阻塞脚本）+ 断言调用在超时窗口内返回、进程被 destroyForcibly；finally 移除时该测试必须红 |
| TP-014 | C6-002 | clone 失败注入（不可达 URL / 伪 git 超时）后断言无半克隆残留目录、下次 prepare 自愈重建 |
| TP-015 | C6-003 | 跨生命周期测试：begin→partial→deleteIndex→重建→completeShard，断言抛 unknown shardId 而非提前发布 |
| TP-016 | C6-004 | 注入低 symbolIds 上限（或 >N 符号夹具），删除后断言六类关联行零残留（对齐 `TestDeletePathIntegrity` 语义放大到截断边界） |
| TP-017 | C7-002 | prepare 多 SHA 后断言 retention 清理（注入 LRU 容量 / 显式 deleteWorkspace API 被发布后调用） |
| TP-018 | C7-004 | 删除被依赖文件后断言 `(source=C,target=A)` 行不存在（对 `TestDeleteFileRecordsIndexScoping` 增补 target 侧对称断言） |
| TP-019 | C7-005 | `RecordingSearchEngine` 增加抛异常模式 + 事务回滚后断言引擎/DB 一致（或 afterCommit 后才写引擎的次序断言） |

### P3（23 行 = 22 主登记 + 别名行 C7-011；聚合登记如下，修复各 P3 工单时随单补测）

- **观测/生命周期面全零断言组**：C6-009（含 C7-011，`getIncrementalStatus` 测试面 0 命中）、C1-004（`getFingerprintStore` 0 命中）、C5-R2-005（内部方法 0 命中）、C6-006（executor 构造器 0 引用）、C6-R2-001（无超时注入面）。建议形态：状态观测补「失败→getIncrementalStatus.completed==false」一条；LLM 面补挂起桩+超时断言（与 TP-013 同构）。
- **功能已覆盖、缺陷面零断言组**：C1-003、C1-005、C5-007、C5-008、C5-009、C5-R2-004、C5-R2-006、C5-R2-007、C6-007、C6-008、C6-010、C7-006、C7-007、C7-008、C7-010、C7-R2-001。（C10 复核修正：C5-R2-005 原在本组与上一组重复列出，按其 grep 零命中证据仅保留于「全零断言组」，避免双重登记。）共性问题 = 测试停在最短功能路径，竞态/累积/放大量纲从不进入夹具。建议随各修复工单补「边界值 + 计数桩」断言，不要求独立并发框架。
- **特别登记（现成保护断开）**：C7-009 —— `TestIFingerprintStore` 契约测试套件已建成且断言覆盖语义，仅缺 `OrmFingerprintStore` 子类；为 Orm 补 `createStore()` 一行接入即红。**这是 44 实体中唯一可零成本激活的现成保护，建议最优先执行。**
- **watch-only 资产**：`TestProjectAnalyzerConcurrency` 是全单元唯一对分析期共享可变状态具非确定性敏感的测试（C1 型缺陷引入时可能偶发红），按确定性口径不计保护力，建议保留并在 C1-001 修复时作为辅助观测点。

---

## 四、汇总

- **矩阵规模**: 44 行，与四维最终保留项一一对应（C1×5 + C5×16 + C6×11 + C7×12；4 组别名行在矩阵内占位评估、登记折入主登记）。
- **会红 / 不会红**: **0 / 44** —— 无任何实体在其缺陷面上具备确定性回归保护。其中 7 行为完全无相关测试（grep 零命中或有名无实），37 行为「功能相邻覆盖存在、缺陷面零断言」；另有 3 个**子面**受确定性保护（C7-004 source 侧 scoping、C7-005 removeDocs 次序、C7-006 发布触发物化）与 1 个断开的现成契约测试（C7-009）。
- **P1 保护力结论**: **3/3 P1（C5-001、C5-002、C7-001）全部无保护**——两个内存无界 P1 无任何上界断言（C5-002 的守卫常量在测试面零命中，`testLargeFilesSkipped` 名实不符），物化风暴 P1 无并发/计数断言。P1 修复工单必须携带 TP-001~003 作为验收项。
- **缺保护登记数**: P1×3 + P2×16（逐条，另 2 条 P2 别名折入）+ P3×22 主登记（聚合，另 1 条别名行折入）= **41 项唯一登记，覆盖 44 行**。
- **结构性结论**: 单元测试面 103 文件/470 测试全部为功能断言；仅有的两处真并发测试（`TestConcurrentIndexing`、`TestProjectAnalyzerConcurrency`）均经裁定为「并发执行形态 + 功能冒烟断言」，对并发缺陷面保护力为零。（C10 复核修正：原文「唯一的真并发测试」与 §一「并发类测试仅 2 个」不一致。）并发/内存/生命周期三类缺陷在本单元处于**测试保护真空**，建议 CRA WI 6.3 收尾阶段将 TP-001~003（P1）与 C7-009 的 Orm 契约子类接入列为最高优先补测项。

## 自我复核声明

- 行数核验：§二矩阵 44 行（C1 表 5 + C5 表 16 + C6 表 11 + C7 表 12），逐行编号 1–44 连续，与四文件「最终保留项」逐条对应（C1-001~005；C5-001~009+R2-001~007；C6-001~010+R2-001；C7-001~011+R2-001）。
- 判级核验：矩阵判级与各文件复核结论一致（P1×3 = C5-001/C5-002/C7-001；P2×18 行 = 16 主 + 2 别名；P3×23 行 = 22 主 + 1 别名）。
- 约束核验：未修改产品代码与四维审计文件；未读取 plans/bugs/lessons/backlog。

## 复核结论

- **复核人/日期**: C10 独立复核子代理 / 2026-10-22。方法：四维文件 `## 最终保留项` 逐条比对矩阵 + 13 行抽样独立 grep/读 `src/test` 重演（P1×3 全部 + P2×3 + P3×5 含 4 条 grep 零命中行 + TestConcurrentIndexing 裁定行）。
- **完整性**: 44 行与四维最终保留项一一对应（C1×5 + C5×16 + C6×11 + C7×12），判级逐条一致；4 组别名占位与各文件「跨维归并总表」完全吻合（#26 C6-005→折入 C7-005、#35 C7-003→折入 C6-001、#43 C7-011→折入 C6-009、#38(c)→折入 C6-003）。
- **抽样重演全部成立**: #6 C5-001（三测试文件 grep 上界词仅 `TestSurprisingConnections.java:142` 的 `Integer.MAX_VALUE` 降序比较 1 命中，非守卫）；#7 C5-002（`MAX_FILE_SIZE_BYTES|MAX_FILE_COUNT` 全测试面 0 命中；`testLargeFilesSkipped` 仅写小文件并断言被分析，名实不符属实）；#33 C7-001（`TestGraphMetricMaterialization` 4 测试全顺序功能断言、`TestGraphAnalysisE2E` 2 测试零并发原语，无 materialize 计数/退避断言）；#1 C1-001（`TestSymbolTable` 6 `@Test`，`Thread|Executor|CountDownLatch|synchronized` 0 命中）；#22 C6-001（两具名测试存在于 `TestChangeAnalyzer.java:124/:135`，全文件无 timeout/destroyForcibly 断言）；#37 C7-005（`testReindexRemovesGhostSymbolDoc` 于 L44/L65 确定性护 removeDocs 次序；`RecordingSearchEngine` 无 throw）；#13 C5-008（两处 `limit=0` 调用仅内容断言）；#41 C7-009（`TestIFingerprintStore` 抽象契约含覆盖语义断言 `testSaveUpdatesExisting`，唯一子类 `TestInMemoryFingerprintStore`，`OrmFingerprintStore` 实现存在于 nop-code-service 主代码但无契约子类——「低成本可激活」成立）；#44 裁定重演（3 线程池 + 双 Latch 栅栏 + 3 个不同 indexId，断言仅 120s 完成/errorCount==0/successCount==taskCount/getIndexStats 非 null——真并发执行 + 功能冒烟定性成立，全局锁串行化后照常通过）；并发测试总数复核：单元内确为 2 个（第 3 处 `TestJavaFileAnalyzerConcurrent` 在 nop-code-lang-java，单元外）。
- **数字核对一致**: 会红 0/44 ✓；缺保护 41 = P1×3 + P2×16（TP-004~019）+ P3×22 ✓；44 = 41 + 3 别名行 ✓；7 行完全无相关测试（#4/#7/#8/#19/#27/#30/#32）+ 37 行相邻覆盖 = 44 ✓；3 个受保护子面 + 1 个断开契约测试 ✓；470 `@Test` 精确命中 ✓。
- **修正 3 处（均已就地修正并标注）**: (1) §一 分模块测试文件数 26/5/72 与实测不符，改为 24/5/74（总数 103 不变）；(2) §三 P3 聚合中 C5-R2-005 在两组重复列出，按其 grep 零命中证据仅保留「全零断言组」（主登记数 22 不变）；(3) §四「唯一的真并发测试」改为「仅有的两处真并发测试」以与 §一「并发类测试仅 2 个」一致。
- **整体判定**: **PASS with corrections** —— 矩阵 44 行的「不会红」判定、缺保护登记构成与测试面证据在独立重演下全部成立，仅 3 处非载荷性数字/表述偏差，已修正。
