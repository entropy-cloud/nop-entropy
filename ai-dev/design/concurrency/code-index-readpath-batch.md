# WI 6.3 nop-code-index 读路径超时/删除链/失效面/C5 族束（G88，R4-31）设计裁定

> Status: draft
> Last Reviewed: 2026-11-12
> Source: `ai-dev/audits/2026-10/2026-10-23-0900-deep-audit-nop-code-index-concurrency/`（merge-adjudication §二 W-04/W-06~W-09/W-11~W-19 行 + §四#1/#2 家族判定 + §六.3；C10-test-protection TP-004~013/015/016/018/019；live 取证 fresh-session @ 2026-11-12，分支 concurrency-audit-remediation）
> 批次: roadmap 8.71 行（R4-31，G88 十四工单：6.3-W-04、W-06、W-07、W-08、W-09、W-11、W-12、W-13、W-14、W-15、W-16、W-17、W-18、W-19）；批次约束 `ai-dev/audits/cra-index.md:473`（P2-G88 行：W-04=外部进程桥第 6 例 TP-013；W-06 接线前置工单挂接（与 W-20/W-35 并列）；W-11 承接 2026-09 G11-15-02 超集/扩面复活防双计；W-13~W-19=R02 C5 族 7 张同单元同根因自并；C10 缺保护随本体验收前置——TP 编号口径勘正见 §五.1）
> 防双计边界：W-01/W-02/W-28 已随 plan 2532 修复（commit 7ea7184b95/6f8953691b）；W-03 已随 plan 2530 修复（commit f728046e94）；**W-05/W-10 不在 G88**（归 :474 P2-G89 dormant 激活计级，deferred）——本批不取证不触碰。W-17 的 finder 半边（GraphMetricStore 行帽+orderBy 下推）已随 plan 2532 组 A 落地，本批只接装配面剩余半边（§〇.L）。

## 〇、Live 取证（2026-11-12 逐条核对）

行号漂移总源：plan 2530/2532 修复 diff + R1.35（P1-6.7-009/010/011）既有块；**CodeCacheManager 全文件零漂移**（W-14 锚点与审计逐字吻合）。以下行号均为 live @ 本分支。

### W-04 git 读无超时——外部进程桥第 6 例（在位，4 站点逐一核准）

统一缺陷形态：`waitFor(timeout)` 位于**管道读取阶段之后**——读取阻塞时超时永不生效（F5 教科书形态）。全单元 ProcessBuilder 共 3 文件 4 站点：

| # | 站点 | live 锚点 | 现状 |
|---|---|---|---|
| 1 | `CodeIndexService.gitOutput` | `nop-code-service/.../impl/CodeIndexService.java:959-986` | 读取 `reader.lines().collect(joining)`（:964-971）**先于** `waitFor(30s)`（:972）；超时分支 `destroyForcibly`（:973）+ `ERR_CODE_REBUILD_NOT_GIT_REPO`；调用漏斗：requireRepoRoot :930 / requireHeadAt :939-940 / gitDiffNames :948（4 调用点全经 gitOutput 单点） |
| 2 | `ClusterWorkspaceManager.gitWithCredential` | `nop-code-service/.../cluster/ClusterWorkspaceManager.java:131-162` | `readAllBytes`（:146）先于 `waitFor(60s)`（:147）；**持 repoLock**（:61-62 `repoLocks.computeIfAbsent` + `lock.lock()`，finally unlock :93）——挂死级联同 repo 后续 prepare 全部排队 |
| 3 | `ClusterWorkspaceManager.gitOutput` | 同文件 :176-205 | `readAllBytes`（:188）先于 `waitFor`（:189），持锁同上 |
| 4 | `ChangeAnalyzer.parseGitDiff` | `nop-code-flow/.../flow/ChangeAnalyzer.java:146-239` | `readLine` 循环（:162）先于 `waitFor(30s)`（:214）；无锁故级联面小 |

四处均已有 destroyForcibly（:973/:149/:191/:227），缺的是**读阶段超时**与收尸finally。凭据 env 通道（NOP_GIT_*，:169-172 注释）与 `GIT_REF_PATTERN` 校验（:925-927）为冻结面。 absorbed 面核实：C7-003 的持 repoLock 级联挂死 + 无 stall 可见性在 #2/#3 站点结构成立。

### W-06 deleteIndex 清单缺 NopCodeShardLedger + completeShard 击穿链（在位）

`CodeIndexService.deleteIndex:998-1031`：withIndexLock 内 `deleteEntitiesPaged` 11 实体（Usage :1013/FlowMembership :1014/Flow :1015/AnnotationUsage :1016/Inheritance :1017/Call :1018/Symbol :1019/File :1020/Dependency :1021/SemanticEdge :1022/GraphMetric :1023）+ `NopCodeIndex` 删除 :1026——**清单无 `NopCodeShardLedger`**（grep 全文件仅 ShardBuildCoordinator 引用）。击穿链（`ShardBuildCoordinator.java` live 核准）：beginShardBuild（[R1-m3] 重建形态早发布腿夹具不得调用 beginShardBuild——其自带 deleteLedger :57 会清旧行致红证失真，仅首个 once-only 腿可用） :46-72 建 ledger；completeShard :77-93 按 `ledgerRowId(indexId, shardId)` 取行，行在即推进 → finalizeIfAllCompleted :95-116 all-completed 即发布；deleteIndex 删 index 实体后残留 ledger 行 → 对重建的同 indexId：completeShard(旧 shard) 命中旧行 → `:102-106` index null 守卫仅在「未重建」形态抛 index not found，**重建形态旧行全 COMPLETED 时提前发布新索引**。接线前置挂接面核实：completeShard :78 仅 `runInSession` 未显式开事务（W-20 契约缺口在位）；停滞检测缺失（W-35）无租约/watchdog——两项 P3 登记级挂接 N6.4 接线前置工单，本批不实施。

### W-07 findSymbolIdsByFileId setLimit(10000) 无 offset 循环（在位）

`CodeIndexService.java:2580-2586`：`q.setLimit(MAX_QUERY_RESULTS)`（常量 =10000，:114）单页即返，无 offset 穷尽。3 调用点：`:2439`（computeAffectedFiles seeds——受影响面截断）、`:2506`（deleteFileRecords——删除清理残留）、`:2592`（removeStaleSymbolDocsForFile——搜索残留）。对照同类标准：`deleteEntitiesPaged` :1036-1051 与 `deleteRelationalBySymbolIds` :2602-2616 均分页穷尽——「同类标准下的例外」成立。

### W-09 deleteFileRecords Dependency 仅清 source 侧（在位）

`CodeIndexService.deleteFileRecords:2499-2556`：NopCodeDependency 仅 `deleteEntitiesByFilters(sourceFilePath+indexId)`（:2524-2527）；对照三表双侧清理齐备——Call callerId+calleeId（:2533-2534）、Inheritance subTypeId+superTypeId（:2530-2531+2535）、SemanticEdge source+target（:2532+2536）——**Dependency 无 targetFilePath 对称腿**。删除入口 4（:480/:515/:583/:1292-1293）全受影响；P1-6.7-011 的 source 侧 indexId scoping（:2521-2523 注释）为已修保护面（冻结）。修复仅需补 target 侧（含 `computeAffectedFiles` 双向 2-hop 放大面随之闭合）。

### W-08 搜索双写时序（C7-005 族主，在位）

三站点事务体内直写引擎 + 双向吞异常：
- `addDoc`：persistSingleFileInSession 内 `:1930-1935`（try/catch + LOG.warn）——位于 indexDirectory/triggerIncrementalIndex/indexFileSet 的 `transactionTemplate.runInTransaction` 体内；
- `removeDocs`：deleteFileRecords `:2508-2513`（先于同方法 DB 删除 :2516-2547）+ removeStaleSymbolDocsForFile `:2594-2599`。
回滚时引擎已被变更（DB 未提交/已回滚）→ 发散持续至下次重索引；removeStaleSymbolDocsForFile 提供部分自愈，无对账路径。deleteIndex 的 removeTopic（:1005-1010）在锁内事务外，非本条站点（登记不扩面）。

### W-11 SymbolTable 混合同步（G11-15-02 超集复活，在位；防双计核对通过）

`nop-code-core/.../graph/SymbolTable.java`（零漂移）：`add` synchronized :26-33；点读裸读 `getByQualifiedName` :35-37 / `getById` :39-41 / `size` :49-51；`getAll` synchronized 防御快照 :43-47（WP-5 AR-155/158 注释在位）；`truncated` 非 volatile :16；`findAllByQualifiedNamePrefix` synchronized :53-61。发布链：`CodeCacheManager.getOrRebuildSymbolTable:113-127` 锁内发布共享实例；`addToSymbolTableCache:157-171` 持 cacheManager 锁对已发布实例原地写 + **:163 check-then-act 判重**（getByQualifiedName==null → add，G11 复核附注同源在位）。36 点读站点 × 5 读类清单（C1 维度 :288-301 权威表：CodeGraphService 27 / FlowDetector 6[BFS 循环体 :271/:287 高密度] / ChangeAnalyzer 1 / GraphMetricMaterializer 1 / DeadCodeDetector 1）抽查吻合。防双计：G11-15-02 已由 merge-adjudication §二.2 关闭为 C1-001/W-11 别名，本批为单一修复工单， cra-index 回填注记已落（2026-10-23），无第二修复面。

### W-12 六失效点时序（在位，行号漂移已核准）

`invalidateAnalysisCache`（:448-450 委托 cacheManager :145-155）六站点 live 对照：

| 站点 | 审计行号 | live 行号 | 形态 |
|---|---|---|---|
| indexDirectory | L350 | :458（先于 withIndexLock :459） | 锁外先行 ● |
| indexFileSet | L438 | :551（先于 withIndexLock :557） | 锁外先行 ● |
| deleteIndex | L888 | :1001（withIndexLock 内、事务 :1011 前） | 提交前 ● |
| batchDeleteFileRecords | L2850 | :2969（withIndexLock 内、事务 :2970 前） | 提交前 ● |
| indexFile | L426 | :539（锁内事务后） | 提交后正确对照 ○ |
| triggerIncrementalIndex | L1218 | :1331（锁内分支末，:1326-1330 注释自证） | 提交后正确对照 ○ |

风险时序（deleteIndex 例）：删除线程 invalidate → 查询线程从 DB 读到未删快照回填 → 事务提交 → 此后无失效点 → 陈旧投影存活至 CACHE_TTL_MS=1h。CL-102 撕裂快照（incrementalAffectedFilesMap put :1216 非原子替换）同窗口机制随案注记，不单列。

### W-13 两提取器输出无上限 + 保存循环无分批（在位）

- `NameSimilarityExtractor.java`：MAX_SYMBOLS=5000 输入侧（:26/:36-37），输出 edges 无任何上限（O(N²) 对全收）；
- `DocKeywordExtractor.java`：同（:31/:52-55）；
- 对照惯例：`AnnotationPatternExtractor.java:29` `MAX_EDGES=50000` + `:92` `if (edges.size() >= MAX_EDGES) return edges`——同族守卫不对称成立；
- 保存循环漂移：语义边 `CodeIndexService.java:1397-1422`（`session.save(edgeEntity)` :1416 逐条，无 flush/evict）；启发式边 `:1673-1706`（save :1704）；对照正确范式：同方法文件/符号侧 BatchQueue(500)+flush+evictAll（:1352-1395 queue.flush()）与 resolveQualifiedNamesToIds 逐批 flush+evictAll。

### W-14 rebuildDependencies 无上限（在位，零漂移）

`CodeCacheManager.java`：常量组 :44-46 仅 `BATCH_SIZE=5000`/`MAX_CACHE_SYMBOLS=100000`/`MAX_CACHE_EDGES=500000`，**无 deps 上限**；`rebuildDependencies :254-270` 循环翻页全量累积无 MAX 检查无截断旗标；同文件 rebuildSymbolTable :192-221 / rebuildCallGraph :223-252 均有上界+截断降级——不对称直接事实。缓存条目 LRU 20 × TTL 1h（:42-43），deps 一项击穿「20×单条上界」峰值推导。

### W-15 replaceRows 插入侧无 flush/evict（在位，漂移 270-295→347-370）

`GraphMetricMaterializer.replaceRows:347-370`：删除侧分批 + `session.flush(); session.clear()`（:362-363）；插入侧 `for (row : rows) dao.saveEntity(row)`（:366-368）无 flush/evict——同方法两侧不对称直接事实。触发面：写路径失效后 + 读路径自愈（ensureMaterializedForGraphMetrics / detectCommunities / getCriticalNodes）与查询请求同栈。

### W-16 增量持久化每文件全表复制（在位，漂移 1584-1592→1701-1709）

`CodeIndexService.persistSingleFileInSession`：`mergedTable` 每变更文件重建——`globalTable.getAll()`（防御快照，SymbolTable :45-47）逐条 add 进新表 + fileSymbolTable 合并（:1701-1709），消费点 resolveQualifiedNamesToIds；调用点 triggerIncrementalIndex BatchQueue(:1183-1193 区域 live :1300-1308) 批内逐文件调用——O(F×S) 复制，快照成本无法跨文件复用。

### W-17 物化读装配路径 chunk 物化（finder 半边已修，装配半边在位）

- **已修半边（plan 2532 组 A，防双计兑现）**：`GraphMetricStore.java` `FAMILY` 行帽常量 + `findByMetric(indexId, metricType, orderBy, asc)` 单点封口（:50-56 javadoc 单源声明：「source of truth for the finder row cap, downstream batches must not introduce a second」）+ loadHubs score desc / loadEntryPoints rankNo asc 下推（:114-118/:172-176）+ warnIfCapped。本批**零触碰**。
- **本批剩余面**：`CodeGraphService.loadSymbolsByIds:922-941` 按 chunk 全量物化符号实体；3 装配路径输入未截断——`:148`（assembleCommunitiesFromMetrics，输入=membership.keySet() 全体社区成员）、`:263`（assembleGraphAnalysisFromMetrics，输入=全体 ENTRY_POINT symbolId）、`:864`（assembleCriticalNodesFromMetrics，输入=hubs.keySet()）。输出侧 godNodes/isolated 本就 topN 截断，实体物化面可随输出收口。

### W-18 getPublicSurface 符号查询完全无 LIMIT（在位，漂移 384-403→386-404）

`CodeQueryService.java`：symQuery 三过滤（indexId + in(fileId) + PUBLIC，:386-390）后 `findAllByQuery` **无任何 setLimit/兜底**（:394）——全文件 17 查询方法唯一无界（grep :140-753 逐一核准，其余均有 MAX_QUERY_RESULTS 或 limit>0 兜底）；文件侧投影对照 :361-366（"avoid loading CLOB" 注释）；兜底范式对照 findReferencedBy :753（`limit>0 ? limit : MAX_QUERY_RESULTS`）。

### W-19 getFiles/fileTree 全实体含 CLOB（在位）

`CodeQueryService.getFiles:135-146`：全实体 `findAllByQuery`（含 sourceCode CLOB，orm 无 lazy 标记）+ entityToFileResult `setSourceCode(entity.getSourceCode())`（:84）；getFileTree（:222-274）仅消费 path/packageName。投影惯例对照：getModuleDigest :289-291、getPublicSurface 文件侧 :361-366 均已 `selectFieldsByQuery` 并注释 CLOB 代价——惯例偏离成立。名义上界 MAX_QUERY_RESULTS=10000 使 CLOB 驻留 100MB-500MB 量级/请求。

### 历史修复面核对（防重复取证）

- **plan 2532**（commit 7ea7184b95 组 A + 6f8953691b 组 B）：W-01 loader 四表上界 50000+截断标志+findByMetric 共享根 cap=10⁴ 一处封口七 finder+orderBy 下推；W-02 skip+truncated 激活+触发面预检（indexDirectory 锁内 fail-fast :464 precheck）+sourceCode 逐文件释放；W-28 随批。**勘误⑤移交义务**：常量单源（GraphMetricStore 行帽）下游禁第二份——W-17 本批新常量须独立语义+交叉引用声明（裁定 L）。
- **plan 2530**（commit f728046e94）：W-03 materialize 单飞门+失败冷却窗（TestGraphMetricsSingleFlight_W_03 在位）；5.6-W-04 runCommunityDetection 守卫+常量单源（TestCommunityDetectionGuard_W_04 在位）。本批零触碰单飞门与 materialize 计数缝（Major-2 不变式延续：禁覆写 materialize 本身）。
- 既有测试锚点 live 确认：TestConcurrentIndexing / TestDeletePathIntegrity / TestDeleteFileRecordsIndexScoping / TestCacheInvalidationAlignment / TestShardBuildCoordinator 均在位（nop-code-service/src/test）。

## 一、设计裁定（十四工单 A–N，含三跨工单设施）

### 设施 F1：GitProcessRunner 统一超时桥（承载裁定 C / W-04）

新建 `GitProcessRunner.java` @ nop-code-core `io.nop.code.core.util`（nop-code-service 与 nop-code-flow 均依赖 core——三文件单点收敛）：

- **核心机制（watchdog 先于读取布防）**：`run(Path dir, long timeoutMillis, List<String> args)` → `pb.start()` 后**立即**向共享 daemon `ScheduledExecutorService` 提交 `schedule(process::destroyForcibly, timeout)`；随后在调用线程 `readAllBytes`（管道在 destroy 时关闭 → 读取最迟于超时点返回）；`waitFor` 收割退出码；取消 watchdog；[R1-M2 定形] run/runLines 返回结果对象（output+exitCode+timedOut 标志）——异常翻译留站点：#1 站点 timedOut→ERR_CODE_REBUILD_NOT_GIT_REPO 保既有映射、#4 站点 timedOut→truncated=true 返回部分结果不抛（携带 dir+args 摘要，**零凭据零环境变量入消息**）。结构上消灭「读阶段裸奔」——超时作用于真实等待点（外部进程桥族 R21/R23 纪律：等待点必须有界）。
- **两个入口**：`run`（全量捕获，供 #1/#2/#3）与 `runLines(dir, timeout, args, lineSink)`（逐行回调，供 #4 parseGitDiff 渐进解析——避免改变其增量解析内存形态；内部同一 watchdog 机制覆盖 readLine 阶段）。
- **收尸 finally**：非超时异常路径（IOException/InterruptedException）亦 `destroyForcibly` 后上抛——补齐 4 站点异常路径进程对象泄漏子面（C6-001 修复建议超集兑现）。
- **测试缝**：包内可见静态 `executable` 字段 + 包内 setter（缺省 "git"，生产零 diff；对齐 2530/2532「实例/包内缝+缺省常量」先例）；三调用类各持包内可见 runner 引用缝（CodeIndexService 实例字段 / ClusterWorkspaceManager 与 ChangeAnalyzer 静态方法转实例委托或同类缝——形态留实现评审，R2-m1），测试注入挂起桩脚本。
- **站点映射（异常语义逐站点保留）**：#1 保持 `ERR_CODE_REBUILD_NOT_GIT_REPO` 映射与 GIT_TIMEOUT_MILLIS=30s；#2/#3 保持 IllegalStateException 文案与 GIT_TIMEOUT_MILLIS、[R1-M1 改写] 桥 API 增 `Map<String,String> extraEnv` 参数（缺省空 map 生产零 diff），gitWithCredential 把 NOP_GIT_USERNAME/PASSWORD 经 extraEnv 传入桥内组装——冻结面 :167-172 注释契约逐字保留；#4 保持 30s 与其解析契约。桥只统一「进程执行+超时+收尸」，异常翻译留在站点。

### 设施 F2：C5 上限/分批统一纪律（承载裁定 H–N / W-13~W-19，自并设施非单类）

七张同单元同根因工单统一四条纪律（减少仪式，逐工单仅声明参数与缝）：
1. **上限形态**：常量对齐既有惯例量纲（5×10⁴ 边 / 10⁴~10⁵ 行），达到上限截断 + `warnIfCapped` 式结构化 WARN + 可观测标志；**注入缝=实例字段 + 包内可见 setter，缺省=编译期常量值，生产零 diff**（2530 materializer/2532 红缝先例同型）；常量只在一个类声明（禁第二份漂移，2532 勘误⑤义务延续）。
2. **分批形态**：巨事务内逐条 `session.save` 一律改 BatchQueue(500) 或 chunk+`flush()`+`evictAll/clear()`——以同方法内既有正确范式为 oracle（BatchQueue 文件/符号侧 / deleteEntitiesPaged / replaceRows 删除侧）。
3. **投影形态**：只要标量的查询走 `selectFieldsByQuery`（同文件 :290/:365 惯例）。
4. **红证形态**：上限类=低值注入断言截断（行为红）；分批类=批计数（[R1-m4] persistInSession 为 private——分批可观测缝留实施评审登记：session 代理或包内缝，orm harness 先例在）桩（行为红）；结构类（单源钉）=诚实登记不造行为红。

### 设施 F3：SymbolTable 单点并发收敛（承载裁定 A / W-11）

修复在 SymbolTable 单点完成，36 站点自动闭合（C1 维度 :305 复核结论原文兑现）；36 站点清单 + 快照读安全表 = 修复验证检查单（5 读类各抽 ≥1 站点行为断言，见裁定 A 载体）。

### 裁定 A（W-11 SymbolTable CHM 化 + 判重原子化；TP-004）

- `SymbolTable.byQualifiedName/byId`（:14-15）改 `ConcurrentHashMap`；`add`（:26-33）去 synchronized（CHM.put 语义等价：同键后写覆盖）；`getByQualifiedName/getById/size`（:35-51）裸读即安全（CHM 弱一致读）；`truncated`（:16）改 volatile（site #27 isTruncated 读类闭合）；`getAll`（:45-47）**保留 synchronized + ArrayList 拷贝**——WP-5 AR-155/158 防御快照契约冻结（CHM 弱一致迭代不满足其「免疫后续突变」强语义，保留 monitor 不与 CHM 冲突）；`findAllByQualifiedNamePrefix`（:53-61）去 synchronized（CHM 遍历天然 CME 安全）。
- **判重原子化**：新增 `putIfAbsent(CodeSymbol)`（两 map 各自 putIfAbsent；跨 map 非原子但各自幂等，last-write-wins 语义与现状等价）；`CodeCacheManager.addToSymbolTableCache:163-165` 判重改调 `putIfAbsent`——G11 复核附注 check-then-act 同源闭合。
- **红证=结构钉诚实登记**（并发数据竞争不可确定性观测，沿 R4-30 W-01 结构红先例）：反射断言两字段类型为 `ConcurrentHashMap`、`truncated` 修饰符含 volatile（修前红→修后绿，兑现 TP-004「CHM 化修复后扩并发组」）；行为腿=并发 add+点读等价绿证（writer N 线程 add 已知符号 × reader latch 对齐点读，断言恒等 + 无异常——非红证，为回归绿证）+ 36 站点检查单抽验（CodeGraphService 1 站点 / FlowDetector BFS 1 站点行为等价绿）。

### 裁定 B（W-12 失效点事务体内注册提交点收口；TP-005）

[R1-B1 改写] 四站点 invalidate 调用**移入 `runInTransaction` 事务体首行**（注册发生在已打开事务上，提交点执行——`addTransactionListener` 无事务时抛 ERR_TXN_NOT_IN_TRANSACTION[ITransactionTemplate :62-68]，守护必要性依据）：live 核实四站点（:458/:551 锁外、:1001 锁内事务前、:2969 事务前）全部无打开事务上下文，CodeIndexAfterCommit 无事务直行=行为 no-op 空壳——原「无需语句挪位」声明自相矛盾作废。结构性挪位登记：:458/:551 从锁外挪到锁内事务体内（withIndexLock 持有期延长一 invalidate 调用，锁影响复核随实施留证；防回退结构钉[R2-m-A]：断言四站点 invalidate 调用已落 withIndexLock+事务体内（grep 结构断言随载体登记））；CodeIndexAfterCommit 小工具仍用于回调封装（catch+WARN 形态保留），但调用点必须在事务体内。理由：(a) 「提交后失效」由事务设施结构性保证；(b) 回滚路径免 over-invalidation；(c) 两正确对照站点（:539/:1331）冻结不改。两正确对照站点（:539/:1331）**冻结不改**（其注释为契约文档）。CL-102 撕裂快照随案注记不修（同窗口良性机制，登记级）。
- 载体三腿（TestCacheInvalidationAlignment 扩展）：腿1=回滚不误杀（deleteIndex 事务注入失败[手工代理 dao 删除点抛出]→断言缓存条目存活——修前「先失效」形态此腿红=确定性行为红）；腿2=提交后失效（成功路径断言 invalidate 发生——绿证）；腿3=并发回填窗（writer 事务体内 latch 暂停[代理 dao 阻塞]、reader getOrRebuild 回填陈旧快照、release writer 提交→断言缓存终态被失效[修前终态=陈旧回填存活 1h→红]）。

### 裁定 C（W-04 四站点接入 F1 桥；TP-013）

四站点全部委托 GitProcessRunner（§设施 F1）：#1 CodeIndexService.gitOutput :959-986 改委托（保留 ref 校验/异常映射）；#2/#3 ClusterWorkspaceManager :131-162/:176-205 改委托（repoLock 结构、凭据 env 应用、异常文案零变更）；#4 ChangeAnalyzer.parseGitDiff :146-239 改 runLines 委托（解析回调契约零变更）。三站点 runner 缝注入挂起桩。
- 载体：`TestGitProcessTimeout`（nop-code-core，桥单元腿：executable 缝指向挂起桩[打印部分输出后 `sleep infinity` 不关 stdout]——修后断言 <5s 返回且超时标志置位；修前等价内联实现[读后 waitFor]此腿阻塞→超时窗断言红）+ `TestGitCallSitesTimeout`（nop-code-service：经 CodeIndexService 缝注入桩，triggerRebuildFromCommit 断言 NopException 且窗内返回——站点级接线红证；ClusterWorkspaceManager/ChangeAnalyzer 参数化腿各一条）。TP-013「finally 移除时该测试必须红」由链3 兑现。

### 裁定 D（W-06 deleteIndex 补 ledger 清理；接线前置挂接；TP-015）

deleteIndex 事务体（:1013-1023）增第 12 项：`deleteEntitiesPaged(session, NopCodeShardLedger.class, "indexId", indexId)`——同事务同段，零新设施。**接线前置工单挂接兑现（批次约束）**：W-20（completeShard 仅 runInSession 未显式开事务——ShardBuildCoordinator.java:78 核准在位）与 W-35（停滞检测缺失）P3 登记级**挂接 N6.4 接线前置工单**，本批不实施；本批登记挂接关系于本节（cra-index 行收敛注随批）。载体验证击穿链闭合：TestShardBuildCoordinator 扩跨生命周期腿——begin(A,B)→completeShard(A)→deleteIndex→（不重建）completeShard(B)→断言 `unknown shardId`（修前：B 行残留命中→finalize→index==null 抛 `index not found`——断言消息含 "unknown shardId" 即红=确定性红）；重建形态早发布断言腿：deleteIndex→重建 index 实体→completeShard(旧 shard)→断言 unknown shardId 且 published=false（修前旧行全 COMPLETED 提前发布→红）。

### 裁定 E（W-07 findSymbolIdsByFileId 分页穷尽；TP-016）

:2580-2586 改分页穷尽循环（形态对齐 deleteRelationalBySymbolIds :2602-2616：`while(true){ setOffset; setLimit(pageSize); 空/短批 break; offset+=pageSize }`）；pageSize=实例字段 `symbolIdPageSize`（包内 setter，缺省=MAX_QUERY_RESULTS 生产零 diff——F2 缝形态）。3 调用点（:2439/:2506/:2592）零改动自动受益。载体：TestDeleteFileRecordsIndexScoping 扩截断边界腿——注入 pageSize=2、夹具 5 符号×关联六类行→deleteFileRecords 后断言六类关联行零残留（对齐 TestDeletePathIntegrity 语义放大）+ findSymbolIdsByFileId 返回全集断言。红0 pre-fix：仅返回 2/5→残留断言红。

### 裁定 F（W-09 Dependency target 侧对称补清；TP-018）

deleteFileRecords :2524-2527 之后补对称腿：`deleteEntitiesByFilters(NopCodeDependency, and(eq("targetFilePath", filePath), eq("indexId", indexId)))`。与裁定 E 同文件同方法——**同 diff 分组落地**（W-07→W-09 顺序，diff 分组登记防混，2530 同文件多工单先例）。P1-6.7-011 source 侧 scoping 注释块（:2521-2523）逐字保留。载体：TestDeleteFileRecordsIndexScoping 增 target 侧对称腿——夹具 `(source=C, target=A)` 删 A→断言该行不存在（红0 pre-fix 残留红）。computeAffectedFiles 双向 2-hop 放大面随之闭合（seeds 腿由裁定 E 分页穷尽补齐，联动验证）。

### 裁定 G（W-08 搜索双写 afterCommit 后置；TP-019）

[R1-m2] 回调数据快照义务：addDoc 的 doc 由 session 内实体构建——afterCommit 回调在 session 关闭后执行，回调必须携带提交前物化好的 POJO，禁止触碰实体/session（lazy 加载会炸）。

采纳「afterCommit 移出」（对账方案成本高、且 removeStaleSymbolDocsForFile 已提供读路径自愈，二选一取前者）：三事务内站点（addDoc :1930-1935 / removeDocs :2508-2513 / removeStaleSymbolDocsForFile :2594-2599）经 `CodeIndexAfterCommit.run` 移至提交后（与裁定 B 共用同一小工具——**跨工单合并设施第二处**）；回调内失败 catch + 结构化 WARN（站点标识 nop.code.search-sync-failed-after-commit:topic=…，现有吞异常语义升级为提交后可观测 WARN）。**边界声明**（javadoc + owner doc）：搜索引擎为 DB 派生 best-effort 投影，提交后写失败仅 WARN、发散自愈归下次重索引/removeStaleSymbolDocsForFile；回滚不再污染引擎。deleteIndex removeTopic（:1005-1010）锁内事务外站点**不扩入**（登记）。载体：RecordingSearchEngine（手工代理，零 mockito 惯例）增抛异常模式 + 时序记录——腿1=回滚腿（事务注入失败→回滚→断言引擎零变更；修前引擎已被直写污染→红）；腿2=次序腿（引擎写记录晚于 commit 标记→红证「事务体内直写」）；腿3=成功腿（正常路径照常写入+查询可见）。TP-019 的「事务回滚后引擎/DB 一致」由腿1 兑现。

### 裁定 H（W-13 提取器 MAX_EDGES + 保存分批；TP-006）

NameSimilarityExtractor / DocKeywordExtractor 各补 `MAX_EDGES=50000`（对齐 AnnotationPatternExtractor :29 惯例）+ 达上限截断 + WARN + 包内 setter 缝（F2）；两处保存循环（:1397-1422 语义边 / :1673-1706 启发式边）改 BatchQueue(500)+flush+evictAll（对齐同方法文件/符号侧范式 oracle）。载体：TestExtractorEdgeCap（同名密集符号集注入低 cap→edges≤cap+截断标志+WARN 腿）+ 批计数桩腿（保存分批可观测）。链：删 cap 红 / 去 WARN 红 / 保存回退逐条无 flush 红。

### 裁定 I（W-14 deps 缓存上限；TP-007）

`CodeCacheManager` 补 `MAX_CACHE_DEPENDENCIES`（定值 2×10⁵——量纲介于 symbols 10⁵ 与 edges 5×10⁵ 之间，终值留实现评审）+ `AnalysisCache.dependenciesTruncated` 旗标 + rebuildDependencies :254-270 内检查（超限 break+WARN+置旗标，形态逐字对齐 :210-215 symbol 侧）+ 包内 getter（消费方可观测）+ 注入缝（F2）。载体：TestCodeCacheManager 增 deps 上界腿（注入低值→截断旗标+WARN）。链：删检查红 / 旗标不置红 / WARN 文案丢 indexId 红（结构化可 grep 断言）。

### 裁定 J（W-15 replaceRows 插入侧对称分批；TP-008）

:366-368 改按 DELETE_BATCH_SIZE(1000) 切片 save + 每片 `session.flush(); session.clear()`（与删除侧 :362-363 对称；clear 语义等价沿用删除侧既有选择——同一 session 同方法内，风险等价）。载体：手工 session 代理计数 flush/evict——注入低阈值缝（切片大小实例字段+包内 setter，F2）→ 2500 行夹具断言分批数>1。红0 pre-fix：插入侧 flush 计数=0 红。

### 裁定 K（W-16 mergedTable 批级共享快照；TP-009）

mergedTable 构建提升到 BatchQueue 消费端批级：每批构建**一次**全局表快照（globalTable.getAll() 一次），批内逐文件解析改两级形态——文件自有符号表优先 + 批级全局快照兜底（resolveQualifiedNamesToIds 增包内两表重载/或 mergedTable 参数化——形态留实现评审，R2-m2；**语义不变量：[R2-m-C 措辞勘正] 同名冲突时全局符号胜出（global-wins，见下方勘正详述）**，与现[R1-B2 勘正] live :1702-1711 实为 **global 胜出**（globalTable 先全量 add，fileSymbolTable 仅 getByQualifiedName(qn)==null 补入）——不变量勘正为「批级全局快照优先 + 文件级仅补缺（global-wins，等价现行为）」，resolveQualifiedNamesToIds 公开解析语义保持；**批内累积语义（R2-2）**：live 同批文件 1..k-1 的符号持续进入已发布 globalTable（persistSingleFileInSession 逐文件 addToSymbolTableCache :1723/:1726）——批级 merged 表构建一次后随每文件处理增量扩入该文件符号（存在性检查 add，global-wins 时机与 live 等价），防「批首冻结快照丢同批前序文件符号」缺口；TP-009 构建次数=批次数载体不受影响；原「file 后 add 覆盖」描述不存在，红证链2 相应改写为 global-wins 等价断言）。getAll 防御快照契约保留（每批一次，频率 F→批数）。载体：构建计数桩（批级快照构建次数断言 = ⌈F/BATCH_SIZE⌉ 非 F；TP-009 原文「构建次数=批次数」）。红0 pre-fix：计数=F 红。

### 裁定 L（W-17；[R1-B3 逐站点重裁]：:148 symbolsById 消费方=findDominantPackageByNames 遍历全部社区成员算 dominantPackage/label（公开 DTO 输出）——截断输入即改公开输出，该站点保持全量输入（受 2532 finder cap 10⁴+loadSymbolsByIds 本体硬帽双保护，如实引用既有上界、「装配面无界」误判勘正）；:864 bridgeNodes qualifiedNameOf 依赖 hubs 集外 id、hubNodes 全量求名在 .limit 之前——同样保持全量输入；仅 :263 entryPoints（输出只消费 limit 内条目）可收口。MAX_ASSEMBLY_SYMBOLS=loadSymbolsByIds 本体硬帽（2532 勘误⑤单源义务兑现）；W-17 装配路径输入截断 + chunk 上界；TP-010 剩余半边）

- 三装配路径（:148/:263/:864）loadSymbolsByIds 输入改**输出截断后的 id 集**：detectCommunities 用 godNodes 截断集、getGraphAnalysis 用 entryPoints topN 截断集、getCriticalNodes 用 hubs topN 截断集（三者 DTO 输出本就 limit 收口，全量实体物化无消费方）。
- `loadSymbolsByIds:922-941` 本体补 `MAX_ASSEMBLY_SYMBOLS=10000` 硬上界（超限 WARN+截断）+ 注入缝（F2）。**常量单源义务兑现**：新常量独立语义（装配实体面≠metric 行面），javadoc 交叉引用 GraphMetricStore :50-56 单源声明、不复制其常量值——2532 勘误⑤移交义务闭合。
- 载体：注入低上界断言物化实体数≤上界+截断 WARN；输入集腿=计数桩断言 id 集大小=topN 截断集（回退全量 membership→红）。与 2532 单飞门（TestGraphMetricsSingleFlight_W_03）零耦合声明：计数/注入点严格位于既有包内缝，禁覆写 materialize（Major-2 不变式延续）。

### 裁定 M（W-18 publicSurface 上限；TP-011）

getPublicSurface symQuery（:386-390）补 `setLimit(publicSurfaceCap)` + `warnIfCapped`（形态对齐 getFiles :140-142 / findReferencedBy :753 范式）；`publicSurfaceCap`=实例字段（包内 setter，缺省=MAX_QUERY_RESULTS 生产零 diff，F2 缝）。载体：注入低值断言截断+WARN（对齐 findReferences 范式作 oracle）。红0 pre-fix：夹具超低值→无截断红。

### 裁定 N（W-19 fileTree 投影；TP-012）

getFiles 增投影变体 `getFilesProjected(indexId)`（`selectFieldsByQuery` id/filePath/packageName 三字段，对齐 :289-291/:361-366 投影惯例+注释理由），getFileTree（:222-274）改走投影版自行组树；getFiles 本体**保留**（其 biz 面语义含 sourceCode，失效面枚举 ○ 的 getFile/getFileSourceCode/findFilesPage 零触碰）。载体：selectFieldsByQuery 手工桩断言 fileTree 路径走投影（红0 pre-fix 零调用红）+ sourceCode 不入内存断言（投影结果无实体载体，结构性成立）。

## 二、载体表

| 工单 | 载体 | 模块叶 |
|---|---|---|
| W-11 | `TestSymbolTableConcurrency`（新建 nop-code-core：结构钉[反射 CHM×2+volatile]+并发 add/点读等价绿证腿+putIfAbsent 幂等腿+getAll 快照腿）+ 36 站点检查单抽验（CodeGraphService/FlowDetector 各 1 站点行为等价，并入既有测试或本载体） | nop-code-core（+service 抽验腿） |
| W-12 | `TestCacheInvalidationAlignment` 扩展（回滚不误杀腿+提交后失效腿+并发回填窗 latch 腿） | nop-code-service |
| W-04 | `TestGitProcessTimeout`（新建 nop-code-core：桥单元挂起桩腿）+ `TestGitCallSitesTimeout`（新建 nop-code-service：CodeIndexService 站点缝腿 + ClusterWorkspaceManager/ChangeAnalyzer 参数化腿） | nop-code-core + nop-code-service（ChangeAnalyzer 腿挂 nop-code-flow 或经 core 桥单测承载，留实现评审 R2-m3） |
| W-06 | `TestShardBuildCoordinator` 扩展（unknown shardId 消息腿 + 重建形态早发布腿） | nop-code-service |
| W-07+W-09 | `TestDeleteFileRecordsIndexScoping` 扩展（截断边界腿[targetPage=2×5 符号]+target 侧对称腿）+ `TestDeletePathIntegrity` 联动绿证 | nop-code-service |
| W-08 | `RecordingSearchEngine` 增强载体（回滚腿+次序腿+成功腿） | nop-code-service |
| W-13 | `TestExtractorEdgeCap`（新建：cap 截断腿+WARN 腿+保存分批计数桩腿） | nop-code-service |
| W-14 | `TestCodeCacheManager` 扩展（deps 上界腿） | nop-code-service |
| W-15 | `TestGraphMetricMaterialization` 扩展或新建分批腿（flush 计数桩） | nop-code-service |
| W-16 | `TestIncrementalMergedTableBatching`（新建：批级构建计数桩腿+global-wins 等价腿[R2-1]） | nop-code-service |
| W-17 | `TestAssemblySymbolsCapping`（新建：上界腿+输入截断计数桩腿（仅 :263，R2-3）） | nop-code-service |
| W-18 | `TestCodeQueryServiceCap` 扩展或新建 publicSurface 腿（低值截断+WARN） | nop-code-service |
| W-19 | `TestFileTreeProjection`（新建：投影桩腿） | nop-code-service |

## 三、变异红证计划（每工单 ≥3 链或结构钉诚实登记）

- **W-11**：红0=结构钉（反射 CHM/volatile 断言对现状红，确定性红兑现先红后绿）；链1 两 map 回退 HashMap→结构钉红；链2 truncated 去 volatile→结构钉红；链3 getAll 回退直返 values()→快照腿红（返回副本类型+修改返回集不影响内部断言）；链4 addToSymbolTableCache 回退 getByQualifiedName 判重→putIfAbsent 幂等腿红（并发双判重重复 add 计数桩，弱红如实登记）。
- **W-12**：红0=回滚不误杀腿（修前先失效形态确定性红）；链1 四站点任一回退事务体内直调（跳过 afterCommit）→回滚腿红；链2 afterCommit 回调吞异常去 WARN→log 断言红；链3 并发回填窗腿恢复 pre-fix 顺序（invalidate 先于 txn）→终态陈旧断言红。
- **W-04**：红0=站点缝挂起桩腿（断言窗内返回，修前内联实现阻塞→红）；链1 桥 watchdog 撤销→桥单元腿红；链2 桥超时吞掉不抛→超时标志/异常断言红；链3 恢复「先 waitFor 后读」顺序→窗内返回断言红（TP-013 finally 义务等效兑现）；链4 finally 收尸移除→异常路径进程泄漏，行为红不可移植**诚实登记结构钉**（code review pin：桥内异常路径 destroyForcibly 存在性）。
- **W-06**：红0=unknown shardId 消息腿（修前抛 index not found→消息断言红）；链1 deleteIndex 12 实体清单回删 ledger 项→两腿红；链2 completeShard 行查找回退「行不在即静默成功」→消息腿红（防御性钉）。
- **W-07**：红0=截断边界腿（2/5 残留红）；链1 分页循环回退单页→腿红；链2 offset 不递增→死循环超时（测试侧防护）红/或返回重复集断言红；链3 pageSize 缝删除→编译期构造红（测试找不到 setter）。
- **W-09**：红0=target 侧对称腿（修前残留红）；链1 删 target 腿→红；链2 target 腿丢 indexId 过滤→TestDeleteFileRecordsIndexScoping 既有 index-scoping 断言红（P1-6.7-011 冻结面反证）；链3 字段名误用（targetFilePath→sourceFilePath 重复）→对称腿红（同夹具双断言 source/target 各自闭合）。
- **W-08**：红0=回滚腿（修前引擎污染红）；链1 三站点任一回退事务体内直写→回滚腿红；链2 引擎写先于 commit 标记→次序腿红；链3 removeDocs 丢 topic/成功路径断言→成功腿红；链4 afterCommit 失败去 WARN→log 断言红。
- **W-13**：红0=cap 截断腿（修前无 cap 红）；链1 删 MAX_EDGES→红；链2 截断去 WARN→log 断言红；链3 保存循环回退逐条无 flush→分批计数桩红；链4 对照 oracle（AnnotationPatternExtractor）常量漂移→结构钉（单声明点 grep 钉）。
- **W-14**：红0=deps 上界腿（修前无检查红）；链1 删检查→红；链2 旗标不置位→红；链3 WARN 丢 indexId→结构化断言红。
- **W-15**：红0=插入侧 flush 计数=0（低阈值夹具红）；链1 删分批→红；链2 clear 去掉（只 flush）→evict 计数断言红；链3 切片大小回退全量→红。
- **W-16**：红0=批级计数桩（修前 F 次红）；链1 回退每文件构建→红；链2 [R2-1 改写] 变异为 file-wins（文件覆盖全局）→global-wins 等价腿红；链3 getAll 快照直返内部视图→快照腿红（WP-5 冻结面反证）。
- **W-17**：红0=上界腿（修前无上界红）；链1 删 MAX_ASSEMBLY_SYMBOLS→红；链2 [R2-3 改写] :263 输入集回退全量 entryPoints→计数桩红（:148/:864 全量输入不在钉面）；链3 第二常量复制 GraphMetricStore 值→结构钉（单声明点 grep+交叉引用 javadoc 存在性）。
- **W-18**：红0=低值截断腿（修前无上限红）；链1 删 setLimit→红；链2 去 warnIfCapped→log 断言红；链3 cap 回退硬编码常量（去缝）→编译期构造红。
- **W-19**：红0=投影桩腿（修前 selectFieldsByQuery 零调用红）；链1 fileTree 回退全实体 getFiles→红；链2 投影字段丢 packageName→组树断言红；链3 误伤 getFiles 本体改投影→既有 getFiles 契约测试红（冻结面反证）。

## 四、接缝与回归面

- **批内顺序**：① 裁定 B 与 G 共用 CodeIndexAfterCommit 小工具——先落工具（随 B）后接 G；② W-07→W-09→W-08 同方法（deleteFileRecords）同 diff 分组顺序落地（E→F→G），diff 分组登记防混（2530 先例）；③ W-13 先于 W-17（边上限降低装配规模量纲，且 W-17 消费 F2 缝惯例）；④ W-11（F3）先行于 W-12（同 C1 缓存簇，CodeCacheManager 两单先后 diff 分组）；⑤ W-14 与 W-11 同文件（CodeCacheManager）——diff 分组：W-11 触 SymbolTable+addToSymbolTableCache，W-14 触 rebuildDependencies+常量组，互不重叠。
- **跨工单设施**：F1 桥（1 类+3 站点委托）、F2 纪律（非代码设施，四条形态约束）、CodeIndexAfterCommit 小工具（B+G 共用，1 类）——新增产品代码合计 ≤3 个新类，其余为既有文件改造。
- **Protected Area**：零触碰声明——全批站点位于 nop-code-core/service/flow 实现叶；零 api 叶、零 orm 模型结构、零 _ 生成物、零平台设施（ITransactionTemplate.afterCommit/isTransactionOpened 为使用面，先例 NopDatavSchedulerAfterCommit plan 2526）。
- **回归基线**：`./mvnw -o test -pl nop-code/nop-code-core,nop-code/nop-code-service,nop-code/nop-code-flow -am`[R2-m-B 勘正：nop-code 为 pom 聚合器不递归] 分解通道（2532 先例：aggregator 含 core+service 全部成员）+ E3 `-am` 阻断面占位按批次惯例实录回填；nop-code-flow 变更（ChangeAnalyzer）纳入编译级回归面（E1 全量覆盖）。
- **既有测试零回退面**：TestConcurrentIndexing / TestDeletePathIntegrity / TestDeleteFileRecordsIndexScoping 既有断言 / TestCacheInvalidationAlignment 既有腿 / TestShardBuildCoordinator 3 腿 / TestGraphMetricsSingleFlight_W_03 / TestCommunityDetectionGuard_W_04 / plan 2532 绿-A/绿-B 全钉——全绿维持，语义强化改写逐条登记（预计无：本批全部为增量腿）。

## 五、审计与 live 勘正（6 处）

1. **cra-index :473 TP 编号口径错位（载荷勘正）**：批次行「C10 缺保护 TP-004/006~009/011~019」按**权威映射**（C10-test-protection :113-128 + merge-adjudication §六.3「W-11 吸 G11-15-02，TP-004；W-12 TP-005；W-13~W-19 TP-006~012」）下 TP-004=W-11、TP-005=W-12、TP-010=W-17——被排除的 TP-005/TP-010 恰是 G88 成员；真正的 G89 排除项为 **TP-014（W-05）/TP-017（W-10）**。批次行作者误用「工单序=TP 序」推排。本批执行口径：**14 成员 TP 锚全兑现（TP-004~013/015/016/018/019）**；cra-index 行文字随批次收敛注勘正为「C10 缺保护 TP-004~013/015/016/018/019 随本体验收前置（TP-014/017 归 G89 随触发条款）」。
2. **行号漂移登记（机制零漂移，全部站点在位）**：W-04 gitOutput L855-859→:959-986（2532 diff 段后移）；W-12 六点 L350/L438/L888/L2850/L426/L1218→:458/:551/:1001/:2969/:539/:1331（漂移源=2530/2532 修复+R1.35 WP-4 块插入）；W-13 保存循环 L1278-1300/L1306-1357→:1397-1422/:1673-1706；W-15 L270-295→:347-370；W-16 L1584-1592→:1701-1709；W-18 L384-403→:386-404；W-19 对照投影 L363-366→:361-366。**零漂移面**：SymbolTable 全部锚点（W-11）、CodeCacheManager 全文件（W-14 :44-46/:254-270 逐字吻合）、GraphMetricStore finder 半边（2532 修后新形态）、ClusterWorkspaceManager/ChangeAnalyzer 站点（C7 维行号 live 精确吻合）。
3. **W-17 防双计兑现确认**：finder 半边（7/8 finder 行帽+orderBy 下推）已随 plan 2532 组 A 落地（live GraphMetricStore :50-56 单源声明+:84-176 全部 finder 经帽化 findByMetric 核准）——本批仅接装配半边（loadSymbolsByIds 3 装配路径）， cra-index「W-17 承接 2532 常量单源移交义务」在本批裁定 L 兑现。
4. **W-06 挂接面行号补充**：merge-adjudication 未给行号——live 补全 deleteIndex 11 实体清单 :1013-1023（逐实体核准）、ShardBuildCoordinator completeShard :77-93 / finalizeIfAllCompleted :95-116（null 守卫击穿链 :102-106）；W-20（runInSession 未显式开事务）与 W-35（停滞检测缺失）live 在位确认，挂接 N6.4 接线前置工单维持。
5. **W-11 getAll 语义精化**：审计 C1 快照读安全表将 getAll 记为「安全 ○」——live 确认其已 synchronized+防御快照（WP-5 AR-155/158 块）但**修后仍须保留 monitor**（CHM 弱一致迭代不满足 AR-155/158 强隔离语义）——裁定 A 的冻结条款依据，非勘误；site #27 isTruncated 读类的 truncated 非 volatile 属同面（审计已含于 5 读类口径内）。
6. **W-08 deleteIndex removeTopic 站点不扩入**：merge-adjudication #37 失效面为「4 副作用点 3 处事务体内」——live 核准第 4 点（deleteIndex removeTopic :1005-1010）位于锁内**事务外**（不在 runInTransaction 体内），回滚污染形态不适用；本批不扩入、不修，登记澄清防止实施期误扩面。

## 六、冻结面（不许回退的既有钉）

- `SymbolTable.getAll` 防御快照契约（WP-5 AR-155/158 注释 :43-47）——裁定 A 修后保留 synchronized+拷贝。
- `triggerIncrementalIndex` 失效时序注释契约（:1182-1183 NOTE + :1326-1330）——两正确对照站点 :539/:1331 零改动；其 no-op 增量不失效语义（:1182 NOTE）与裁定 B 的 afterCommit 语义兼容（no-op 分支本就无失效调用）。
- P1-6.7-011 Dependency source 侧 indexId scoping（:2521-2523 注释）——裁定 F 只补 target 侧对称腿。
- P1-6.7-010 analyze-before-delete 契约（triggerIncrementalIndex 注释块）与 failedFiles 指纹保留语义——本批零触碰。
- GIT_REF_PATTERN 校验（:925-927）与凭据 env 通道契约（ClusterWorkspaceManager :169-172 注释「凭据零命令行零日志」）——F1 桥改造逐字保留。
- `AnnotationPatternExtractor.MAX_EDGES` :29/:92 —— W-13 的对照 oracle，本身不在修改面。
- `GraphMetricStore` finder 行帽单源声明 javadoc（:50-56）与 rankNo 排序契约（loadEntryPoints :166-170）——2532 产物，W-17 只增独立常量不复制值。
- plan 2530 单飞门+冷却窗（materialize 唯一收口、禁覆写 materialize 本身——Major-2 不变式）与 5.6-W-04 守卫常量单源；plan 2532 有界物化（loader 50000 上界+截断标志、W-02 预检 :464）——零回退。
- BatchQueue(500)+flush+evictAll 文件/符号侧范式与 deleteEntitiesPaged/deleteRelationalBySymbolIds 分页穷尽惯例——W-13/W-15 的 oracle，不改其本体。
- `ITransactionTemplate.afterCommit/isTransactionOpened` 使用语义（NopDatavSchedulerAfterCommit 回调失败契约形态）——CodeIndexAfterCommit 逐字对齐。

## 七、owner doc 缺口声明

nop-code.md 存在（单元级缺口 0，merge-adjudication §五.4 已核）。发现级缺口随批兑现：① LlmEdgeCache「无界进程生命周期」组件边界条目（merge-adjudication §五.4 预登记——W-23 登记级不属本批，边界条目随本批 C5 族落地一并补）；② 本批新增契约落 `docs-for-ai/04-reference/source-anchors.md` 新锚点（CODEINDEX-RT-001 GitProcessRunner 超时桥 / CODEINDEX-RT-002 搜索双写 afterCommit 边界声明 / CODEINDEX-RT-003 SymbolTable 并发契约[CHM+getAll 快照+putIfAbsent]）；③ nop-code.md 按 SSOT 规则补：git 调用统一经 F1 桥、C5 上限缝惯例（实例字段+包内 setter）、deleteIndex 12 实体清理清单。
