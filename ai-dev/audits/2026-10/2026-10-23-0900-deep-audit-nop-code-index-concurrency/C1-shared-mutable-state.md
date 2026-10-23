# C1 共享可变状态与可见性初审 — nop-code-index

- **审计单元**: nop-code-index（nop-code-core 51f/3,865 + nop-code-flow 12f/1,840 + nop-code-service 53f/11,193，共 116f/16,898）
- **维度**: C1 共享可变状态与可见性（五类载体盘点 + 可见性/原子性验证）
- **批次**: 全单元单批（初审 · 盲审）
- **盲审声明**: 本审计为盲审。未读取 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`、`ai-dev/backlog/` 下任何文件；结论仅基于 live 代码 + 基线 `_tmp/cra-baseline-nop-code-index.txt`。文中提及的 WP-5 AR-xxx、P1-6.7-010 等编号均来自**代码内注释**，非历史审计文档。
- **CL 前缀**: `nop-code-index-CL-0NN`（维度集外线索）
- **基线**: `_tmp/cra-baseline-nop-code-index.txt`（2026-10-23T01:37:54Z @ 7b0ccc89d4；C1 长驻 bean 实例字段面 89、G11-15-02 站点 SymbolTable.java:13、static final 集合 12 处）
- **日期**: 2026-10-23

---

## 一、五类载体清单表

> 判定口径：写者（W）/ 读者（R）/ 同步机制（Sync）/ 判定。●=立案或 CL；○=安全（记表）。

### 载体 1：singleton bean 实例字段（基线字段面 89）

| # | 载体（字段） | 类 | 写者 | 读者 | Sync | 判定 |
|---|---|---|---|---|---|---|
| 1-1 | `analysisCacheMap`（LinkedHashMap, accessOrder=true） | CodeCacheManager L71 | getOrCreateEntry/invalidate/evict（全在 `lock` 内） | 同左 + `cacheSize()`（无锁读，无调用方） | ReentrantLock（全局单把） | ○ 结构安全；单锁跨 index + 锁内 DB I/O → CL-001 |
| 1-2 | `CacheEntry.cache.symbolTable/callGraph/dependencies`（非 final 可变成员） | CodeCacheManager L48-52 | rebuild*（锁内）、`addToSymbolTableCache` 原地合并（锁内） | 各 service 取引用后**锁外**读 | 锁只保护 map 结构，不保护值对象锁外读 | ● C1-001（SymbolTable 混合同步）；CallGraph 已全同步化 ○ |
| 1-3 | `sub-services`（searchService/graphService/queryService/graphMetricMaterializer，非 final） | CodeIndexService L118-121 | `ensureSubServices()`（private synchronized，L178） | 全部经 `ensureSubServices()` 门后再读（grep 全部 29 个调用点确认） | monitor 入出建立 HB | ○ 懒初始化正确 |
| 1-4 | `indexLocks`（CHM<String,ReentrantLock>） | CodeIndexService L123 | `computeIfAbsent` | 同左 | CHM | ○ 原子；无淘汰 → CL-004 |
| 1-5 | `rebuildDebounceMap`（CHM<String,Long>） | CodeIndexService L159 | L759 get / L778,789 put | 同左 | 单操作原子，get→put 复合非原子 | ● C1-005；无淘汰 → CL-004 |
| 1-6 | `incrementalAffectedFilesMap`（CHM<String,List<String>>） | CodeIndexService L163 | L911 remove / L1103 put（整表替换引用） | L748 get + 防御性拷贝返回 | CHM；list 发布后不再变更 | ○ 结构安全；无淘汰 → CL-004 |
| 1-7 | `importResolvers`（HashMap）/ `heuristicSynthesizers`（ArrayList） | CodeIndexService L147-148 | 构造器/`setRegistry` 装配期一次性写入 | analyze 路径读 | 装配期写 → IoC 发布后只读 | ○（依赖"运行期不再写"不变式，见 §三 备注） |
| 1-8 | `debounceMillis`（long，非 volatile） | CodeIndexService L165 | @InjectValue setter（装配期） | 触发路径读 | 无（配置期写一次） | ○ 理论 long 撕裂不适用（写仅发生于容器单线程装配期） |
| 1-9 | `fingerprintStore`（非 final） | CodeIndexService L222 | setter / `getFingerprintStore()` 懒初始化 | 同左 | 无同步 | ● C1-004（良性 check-then-act） |
| 1-10 | `@Inject protected` 协作对象（daoProvider/ormTemplate/transactionTemplate/searchEngine/flowDetector/changeAnalyzer/deadCodeDetector/registry/analyzer/accessPolicy 等） | CodeIndexService L145-222 | IoC 装配期 | 运行期 | 容器发布 HB | ○ 平台标准模式（非发现） |
| 1-11 | `incrementalStatusMap`（synchronizedMap(access-ordered LinkedHashMap, removeEldestEntry>100)） | NopCodeIndexBizModel L44 | L75/100/110/202 put（先建满再放）、L155 remove | L118 get | synchronizedMap 包装；无迭代/批量点（grep 全站仅 6 个单操作点） | ○ 符合"迭代须在锁内"门禁（无迭代点）；status 对象发布后不变更 |
| 1-12 | `flowCache`/`symbolFilePathCache`（CHM） | FlowDetector L60-61 | detectFlows put 整表引用 / invalidateCache remove | get/getOrDefault（只读缓存的 unmodifiableList，L147/164 注释 AR-147） | CHM | ○ 发布后不原地变更 |
| 1-13 | `maxDepth`（int）/ `patternProviders` | FlowDetector L58,63 | 装配期 setter | 检测期读 | 配置期写一次 | ○ |
| 1-14 | `adapters`（HashMap） | LanguageAdapterRegistry L13 | `registerAdapter`（唯一主代码调用点 CodeIndexService.setRegistry L263，装配期）+ `setAdapters`（配置） | getAdapter/getAnalyzer（analyze 热路径） | 无 | ○ 装配期写后只读；`getSupportedLanguages()` 返回活 keySet 是泄漏面（P3 观察项，无运行期写者故不立案） |
| 1-15 | `semanticExtractors`（ArrayList）/ `resourceLoader` | ProjectAnalyzer L44,47 | 装配期 registerSemanticExtractor（CodeIndexService.setRegistry） | 分析期迭代 | 同 1-7 | ○ |
| 1-16 | `executor`（final ExecutorService） | ProjectAnalyzer L61 | 构造器（CodeIndexService 用的构造器传 null） | **无使用点**（"Reserved for future parallel analysis"） | — | ○ 预留未用（与基线一致）；若启用须补生命周期管理 |
| 1-17 | `repoLocks`（CHM<String,ReentrantLock>） | ClusterWorkspaceManager L28 | computeIfAbsent L62 | 同左 | CHM | ○ 原子；锁内 git 网络 I/O → CL-003 |
| 1-18 | `metricMaterializer`（非 final，setter 注入） | ShardBuildCoordinator L40 | setMetricMaterializer（**主代码零调用方**） | finalizeIfAllCompleted L108 | 无 | ● C1-003（并含未接线 CL-006） |
| 1-19 | `frameworkAnnotations`（非 final Set） | DeadCodeDetector L104 | 构造器 / setPatternProviders（装配期，整体替换引用） | L254 读 | 替换式发布 | ○ |
| 1-20 | final-only 无状态服务 | GraphMetricStore、GraphMetricMaterializer、OrmFingerprintStore、InMemoryFingerprintStore、LlmEdgeCache、LlmSemanticEdgeExtractor、CodeSearchService、CodeQueryService、CodeGraphService | — | — | final 字段 | ○（LlmEdgeCache 内部 CHM 见 CL-002） |

### 载体 2：static 可变字段

| # | 载体 | 位置 | 判定 |
|---|---|---|---|
| 2-1 | static 非 final 字段 | 全单元 grep（模式：`^\s*(private\|protected\|public)\s+static\s+` 后接类型声明且以 `;` 结尾） | **0 处**（反向证实；基线 12 处 static final 集合逐一核对均为 `List.of`/`Set.of`/`Pattern.compile` 不可变类型） |
| 2-2 | static 非线程安全工具（SimpleDateFormat/Random 等） | 全单元 grep | **0 处**；`MessageDigest.getInstance` 为方法内局部实例（LlmEdgeCache L27，无共享） |

### 载体 3：全局注册表/缓存

| # | 载体 | 位置 | 判定 |
|---|---|---|---|
| 3-1 | 平台容器 | `BeanContainer.instance().getBeansOfType`（CodeIndexService.setRegistry L262，装配期只读一次）、`VirtualFileSystem.instance()`（平台自身，按口径不重审） | ○ |
| 3-2 | 模块内进程级缓存 | analysisCacheMap（≤20+TTL）、flowCache/symbolFilePathCache（≤20）、incrementalStatusMap（≤100 LRU）、LlmEdgeCache.cache（**无界**） | LlmEdgeCache → CL-002；其余有界 ○ |
| 3-3 | 无淘汰的 per-indexId 锁/快照表 | indexLocks、rebuildDebounceMap、incrementalAffectedFilesMap（仅 deleteIndex L911 清理其一） | → CL-004 |

### 载体 4：ThreadLocal

| # | 载体 | 判定 |
|---|---|---|
| 4-1 | 全单元 grep `ThreadLocal` | **0 处**（反向证实；无池化线程清理风险面） |

### 载体 5：逃逸/发布（构造器/getter 泄漏）

| # | 载体 | 位置 | 判定 |
|---|---|---|---|
| 5-1 | 缓存的 `AnalysisCache` 成员（SymbolTable/CallGraph/dependencies List）锁外交出 | CodeCacheManager getOrRebuild* 系列（L113-186） | SymbolTable 混合同步 → C1-001；CallGraph 全方法 synchronized + 快照语义（L46-66，AR-145/148 注释）○；dependencies List 发布后无变更点（grep 无 add/remove）○ |
| 5-2 | `getForwardMap`/`getAll` 快照 | CallGraph L47-66、SymbolTable L45-47 | ○（SymbolTable 仅 getAll 快照，点读未覆盖 → C1-001） |
| 5-3 | FlowDetector 缓存列表 | unmodifiableList 包装（L141-164，AR-147 注释） | ○ |
| 5-4 | 构造器启动线程/注册监听器 | 全单元 | **0 处**（无 this-escape） |
| 5-5 | `getIncrementalStatus` 返回活对象 / `getSupportedLanguages` 返回活 keySet | NopCodeIndexBizModel L118、LanguageAdapterRegistry L31 | ○（status 发布后不变更；keySet 无运行期写者） |

---

## 二、风险立案

### C1-001（P2）SymbolTable 写同步/读不同步的混合同步：缓存表发布后被索引写路径原地合并，与查询点读构成数据竞态

- **文件**: `nop-code/nop-code-core/src/main/java/io/nop/code/core/graph/SymbolTable.java`；写侧 `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeCacheManager.java`；触发点 `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java`
- **行号范围**: SymbolTable.java L26-41（写同步）/ L35-41+L49-51（点读不同步）；CodeCacheManager.java L157-171；CodeIndexService.java L1601-1607；CodeGraphService.java L533-536
- **证据片段**:

```java
// SymbolTable.java L26-41 —— 写方法 synchronized，点读方法无同步
public synchronized void add(CodeSymbol symbol) {
    if (symbol.getQualifiedName() != null) {
        byQualifiedName.put(symbol.getQualifiedName(), symbol);
    }
    if (symbol.getId() != null) {
        byId.put(symbol.getId(), symbol);
    }
}
public CodeSymbol getByQualifiedName(String qualifiedName) {
    return byQualifiedName.get(qualifiedName);      // 无 synchronized
}
public CodeSymbol getById(String id) {
    return byId.get(id);                            // 无 synchronized
}
```

```java
// CodeCacheManager.java L157-167 —— 持 cacheManager 锁对**已发布的缓存表**原地 add
void addToSymbolTableCache(String indexId, SymbolTable newSymbols) {
    lock.lock();
    try {
        CacheEntry entry = getValidEntry(indexId);
        if (entry != null && entry.cache.symbolTable != null) {
            for (CodeSymbol sym : newSymbols.getAll()) {
                if (... && entry.cache.symbolTable.getByQualifiedName(...) == null) {
                    entry.cache.symbolTable.add(sym);   // 变更查询线程正持有的同一实例
```

```java
// CodeGraphService.java L533-536 —— 查询线程锁外点读同一缓存实例
SymbolTable table = cacheManager.getOrRebuildSymbolTable(indexId, daoProvider, ...);
if (table == null) return null;
CodeSymbol symbol = table.getByQualifiedName(qualifiedName);   // 无任何锁
```

- **严重程度**: P2
- **现状**: `CodeCacheManager` 的锁只保护 `analysisCacheMap` 结构与合并入口；缓存的 `SymbolTable` 实例被锁外交给所有查询方（CodeGraphService 15+ 处 `getOrRebuildSymbolTable` 调用、CodeIndexService L1589 等）。索引写路径（indexDirectory → persistInSession → `addToSymbolTableCache`，CodeIndexService L1604/1607）会对**同一个已发布实例**调用 synchronized `add`。`getAll`/`findAllByQualifiedNamePrefix` 已是 synchronized 快照（AR-155/158 注释），CallGraph 同族问题已在 AR-145 修复（"read methods must be synchronized to match the write methods"），但 SymbolTable 的三个点读方法（`getByQualifiedName`/`getById`/`size`）未纳入同族修复。
- **风险**: T1（查询线程）：`getOrRebuildSymbolTable` 取得缓存表引用 → T2（索引线程）：`addToSymbolTableCache` → `add()` 内 `HashMap.put`（可能触发 resize）→ T1：`getById/getByQualifiedName` 无 happens-before 地并发 `get`。JMM 下读者可见性无保证：可稳定写出"已入库符号在查询中瞬时 miss（返回 null）或读到陈旧值"的交错；null 会沿 `findSymbolByQualifiedName`/`buildTypeHierarchy` 传播为空结果而非报错，难察觉。单写者故无结构性腐化、无 CME（get 不迭代），影响自愈。
- **建议**: 比照 CallGraph AR-145 同族处理：为 `getByQualifiedName`/`getById`/`size` 补 synchronized（读多写少，方法级 monitor 开销在此表规模下可接受）；或改两把 map 为 ConcurrentHashMap 并去掉 `add` 的 synchronized（`add` 当前两 put 非原子，CHM 化后需评估双 map 一致性语义）。
- **信心水平**: 高（写读两侧代码与交错时序均可定位）
- **误报排除**: 为什么不是"原语使用不是问题"类误报——此处不是质疑 synchronized/HashMap 本身，而是同一可变对象上写者持 SymbolTable monitor、读者无任何同步的**复合不一致**；也不是无状态单例误报（该表是长驻缓存值对象且存在运行期写者 `addToSymbolTableCache`）。
- **F3 同族清单**: `SymbolTable` 全部读面：`getAll`(已同步快照○)、`findAllByQualifiedNamePrefix`(已同步○)、`getByQualifiedName`(未同步●)、`getById`(未同步●)、`size`(未同步，仅摘要行用，低危)、`isTruncated/setTruncated`(boolean 标志，rebuild 锁内写、锁外读，可见性缺口同族但后果仅 truncation 标志延迟可见，P3 记录)。对照同模块 `CallGraph`：全方法 synchronized，已收敛。
- **复核状态**: 已复核（2026-10-23 独立复核，判定：保留 P2；G11-15-02 三态裁定=仍成立未修复，本案为其超集，见文末「维度复核结论」）

### C1-002（P2）缓存失效相对锁/事务提交的次序不一致：4 条写路径存在"读者以 pre-commit DB 状态回填缓存且此后无失效"的 check-then-act 窗口

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java`
- **行号范围**: L349-351（indexDirectory）、L437-444（indexFileSet）、L884-888（deleteIndex）、L2846-2851（batchDeleteFileRecords）；对照正确实现 L388-426（indexFile）、L1209-1218+L1230-1235（triggerIncrementalIndex）
- **证据片段**:

```java
// indexDirectory L349-351：invalidate 在 withIndexLock **之前**，且整个写事务后无二次 invalidate
checkWriteAccess(indexId);
validatePath(vfsPath);
invalidateAnalysisCache(indexId);            // ← 窗口起点（锁外）
return withIndexLock(indexId, () -> {
    ... // analyze + 事务写库（可能数分钟）   // ← 期间任何查询都会 getOrRebuild 回填旧 DB 状态
});                                          // ← 提交后无 invalidate
```

```java
// triggerIncrementalIndex L1230-1234（正确姿势，代码内自注释）
// Actual changes invalidate at the branch end: earlier invalidation would be
// undone by persistSingleFileInSession re-creating cache entries mid-flight.
invalidateAnalysisCache(indexId);
```

- **严重程度**: P2
- **现状**: 类契约（CodeCacheManager javadoc L30-36）声明缓存"invalidated exclusively by index write paths"。6 条失效点中 2 条（indexFile L426、triggerIncrementalIndex L1218，后者内注释引用 P1-6.7-010 教训）在保存/提交后失效；其余 4 条失效先于锁或先于事务提交：indexDirectory L350（锁外）、indexFileSet L438（锁外，且事务后无失效）、deleteIndex L888（锁内但在删事务提交前）、batchDeleteFileRecords L2850（锁内但在删事务提交前）。
- **风险**: 具体时序（以 deleteIndex 为例）：T1 删除线程 `invalidateAnalysisCache` → T2 查询线程 `getOrRebuildSymbolTable` 从 DB 读到**未删**快照并回填缓存 → T1 事务提交（行已删）→ 此后无任何失效点 → 已删除索引的符号/调用图在缓存中存活至 `CACHE_TTL_MS=3600_000`（1 小时），删除后的索引仍可查询出全量数据。indexDirectory/indexFileSet 窗口内回填的则是 pre-reindex 陈旧投影，全量重建后最长 1 小时内查询结果与 DB 不一致。DB 本身无损（源真），但类契约"写路径独占失效"在该窗口失效。
- **建议**: 统一为"提交后失效"次序：indexDirectory/indexFileSet 将 `invalidateAnalysisCache` 移入 withIndexLock 末尾（事务成功返回后）；deleteIndex/batchDeleteFileRecords 移至事务块之后（仍在锁内）。与 triggerIncrementalIndex 的既有姿势对齐即可，无需新机制。
- **信心水平**: 高
- **误报排除**: 不是"低频路径粗锁合理"类——查询与重建并发是 GraphQL 服务常态；也不是理论竞态——时序完全具体，且同文件内 triggerIncrementalIndex 的注释自证该时序已被认定为有害（"missing invalidation after commit would be the harmful case"）。
- **F3 同族清单**: 全部失效点及判定：L350 indexDirectory（锁外先行●）、L426 indexFile（提交后锁内○）、L438 indexFileSet（锁外先行且无后续●）、L888 deleteIndex（锁内、提交前●）、L1218 triggerIncrementalIndex（提交后锁内○）、L2850 batchDeleteFileRecords（锁内、提交前●）。
- **复核状态**: 已复核（2026-10-23 独立复核，判定：保留 P2，见文末「维度复核结论」）

### C1-003（P3，接线后升 P1）ShardBuildCoordinator.completeShard 跨事务 check-then-act：并发完成最后两个分片可双重发布

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/ShardBuildCoordinator.java`
- **行号范围**: L77-93（completeShard）、L95-116（finalizeIfAllCompleted）
- **证据片段**:

```java
// L95-114：读 ledger → 全完成判定 → 状态翻转 + materialize，跨事务无原子化保障
List<NopCodeShardLedger> rows = loadLedger(indexId, ledgerDao);
boolean allCompleted = !rows.isEmpty()
        && rows.stream().allMatch(r -> SHARD_COMPLETED.equals(r.getStatus()));
boolean published = false;
if (allCompleted) {
    NopCodeIndex index = (NopCodeIndex) session.get(NopCodeIndex.class.getName(), indexId);
    ...
    index.setStatus(STATUS_COMPLETED);
    if (metricMaterializer != null) {
        // publish point: global metrics materialized in the same transaction as the
        // status flip, so a published index is always fully scored
        metricMaterializer.materialize(indexId);
    }
    published = true;
}
```

- **严重程度**: P3（当前不可达：全仓 grep 显示 `ShardBuildCoordinator` 无 beans.xml 装配、`beginShardBuild/completeShard/setMetricMaterializer` 在主代码零调用方；一旦按类注释接线即升 P1）
- **现状**: 完成判定是对 ledger 行的普通读后判定，两个事务各自 `completeShard` 最后两个未完成分片时，在读提交隔离下都可看到"其余分片已完成"的中间态而双双进入 `allCompleted` 分支 → 双重 `materialize`（delete+insert 两事务交错）+ 双重 status 翻转。`(indexId,metricType,symbolId)` 唯一键（GraphMetricMaterializer L142 注释）使其中一个事务在插入时违约回滚，最终收敛为"一成一败"，completeShard 调用方收到意外异常。
- **风险**: 分片构建的常态收尾正是最后两片并发上报；接线后每次大索引发布都可能触发一次失败重试与重复物化开销。单事务原子性（status flip 与 metric 同事务）只保证"发布的索引有分"，不保证"恰好发布一次"。
- **建议**: 接线前修复：以 `UPDATE nop_code_index SET status='COMPLETED' WHERE id=? AND status<>'COMPLETED'` 条件更新的影响行数作为发布权裁决，或对 indexId 取 DB 咨询锁后再判定。
- **信心水平**: 中（竞态本身确凿；"当前不可达"结论基于主代码 grep，若存在反射/脚本调用方则需修正）
- **误报排除**: 不是"低频粗锁合理"——这是发布幂等性缺陷而非性能问题；也不是对 synchronized 原语的误报——缺陷在于复合判定跨事务无原子化。立案为 P3 仅因**当前未接线**（不可达缺陷降级处理），修复应先行于接线。
- **复核状态**: 已复核（2026-10-23 独立复核，判定：保留 P3 + 失败模式修正——主失效模式为发布丢失，双重发布仅限 auto-commit 语义，见文末「维度复核结论」）

### C1-004（P3）getFingerprintStore() 懒初始化为非原子的 check-then-act（良性，模式违例登记）

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java`
- **行号范围**: L227-233
- **证据片段**:

```java
protected IFingerprintStore getFingerprintStore() {
    if (fingerprintStore == null) {
        fingerprintStore = new OrmFingerprintStore(daoProvider, ormTemplate);
    }
    return fingerprintStore;
}
```

- **严重程度**: P3
- **现状**: 与同类的 `ensureSubServices()`（synchronized）不同，此处懒初始化无同步、字段非 volatile。两线程可各建一个 `OrmFingerprintStore`。
- **风险**: 实际后果良性：`OrmFingerprintStore` 无状态（三 final 字段，JMM final 字段语义保证竞态发布下也安全），多实例等价；仅造成冗余对象与模式不一致（对照 F10 守卫原子性口径）。
- **建议**: 与 `ensureSubServices` 统一：方法加 synchronized，或初始化移入构造/装配期一次完成。
- **信心水平**: 高（缺陷在，影响低）
- **误报排除**: 不作为 P1/P2 报告，正是因为后果可证良性；登记是为了防未来有人给 OrmFingerprintStore 加状态后踩中不安全发布。
- **复核状态**: 已复核（2026-10-23 独立复核，判定：保留 P3；行号勘误 L227-233→L248-253，见文末「维度复核结论」）

### C1-005（P3）rebuildDebounceMap 防抖判定 get→put 非原子，并发双触发可同时通过防抖窗口

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java`
- **行号范围**: L757-790
- **证据片段**:

```java
long now = System.currentTimeMillis();
Long last = rebuildDebounceMap.get(indexId);            // L759
if (last != null && now - last < debounceMillis) {
    result.setDebounced(true);
    ... return result;
}
... // git diff / validateGitRef / requireRepoRoot 等耗时检查
rebuildDebounceMap.put(indexId, now);                   // L778 / L789
```

- **严重程度**: P3
- **现状**: 防抖为 get-check 与 put 两步，中间隔着 git 校验等耗时操作；两并发 triggerRebuildFromCommit 可都读到旧时间戳而双双通过。
- **风险**: 双份 git diff 与增量扫描执行；第二次进入 `triggerIncrementalIndex` 后被 `withIndexLock` 串行化且指纹比对多半 no-op，浪费为主、无数据损坏；`debounceMillis` 的防护语义（幂等旁路 resetRebuildDebounce 注释自证其用途）在并发下不严格成立。
- **建议**: 若需严格防抖，改 `rebuildDebounceMap.put(indexId, now)` 为先 `putIfAbsent(now)` + `compute` 比较旧值，或直接对 indexId 借用 `withIndexLock` 包裹判定段。
- **信心水平**: 高
- **误报排除**: 不算"无界并发容器使用"误报（CHM 单操作安全，问题在复合操作）；不升 P2 因后果仅为重复工作且下游已有两层兜底。
- **复核状态**: 已复核（2026-10-23 独立复核，判定：保留 P3，见文末「维度复核结论」）

---

## 三、维度集外强信号（CL 线索）

- **nop-code-index-CL-001**（C2/C9 形态）: `CodeCacheManager` 用**一把全局 ReentrantLock**（L73）服务所有 indexId 的缓存读写，且 `rebuildSymbolTable/rebuildCallGraph/rebuildDependencies`（L192-270）在锁内执行 DB 分页 I/O（上限 10 万符号/50 万边、5000/批）。任一 index 的首次重建会串行化**所有** index 的查询缓存访问。建议 per-indexId 锁。
- **nop-code-index-CL-002**（C2/C5 形态）: `LlmEdgeCache.getOrCompute`（L16-19）以 `ConcurrentHashMap.computeIfAbsent` 承载 LLM RPC——长 I/O 期间持有桶锁，同桶其他 key 全部阻塞；且 cache 进程生命周期**无界无淘汰**（类注释自认"v1 纯内存"）。建议 per-key FutureTask 模式 + 容量/TTL 上限。
- **nop-code-index-CL-003**（C2/C9 形态）: `ClusterWorkspaceManager.prepare`（L62-101）持 per-repo `ReentrantLock` 全程执行 `git clone --all / fetch / checkout`（网络 I/O）；同仓并发 prepare 将长时间互等，无超时（对照 F5 等待点口径）。
- **nop-code-index-CL-004**（C5/C6 形态）: `CodeIndexService.indexLocks`、`rebuildDebounceMap` 按 indexId 只增不减（`deleteIndex` 仅清理 `incrementalAffectedFilesMap` L911，不清理前两者）；万级 indexId 场景锁对象与时间戳条目持续累积。建议在 deleteIndex 补齐清理或引入弱引用/上限。
- **nop-code-index-CL-005**（C7/C8 形态）: `OrmFingerprintStore.saveFingerprints`（L40-79）是 DB read-modify-write（`loadFileIdMapByIndex` 快照 → 逐条 get-or-insert），其正确性完全依赖 `CodeIndexService.withIndexLock` 的 JVM 内串行化；任何旁路调用方（如集群 worker 直接构造）会重复插入。另 `InMemoryFingerprintStore` 的 `saveFingerprints`（整 map 替换）与 `deleteByPaths`（原地删）复合交错可丢失删除——当前主代码零使用方（仅测试），若转生产需先补复合操作原子性。
- **nop-code-index-CL-006**（C8 形态，与 C1-003 关联）: `ShardBuildCoordinator` 及其 `metricMaterializer` 协作在主代码无任何装配点（无 beans.xml、无构造调用、setter 零调用），cluster 分片发布链路（N6.4 注释所指设计）实际未接线；`GraphMetricMaterializer` 的 `setMetricMaterializer` 注入预期落空。属"文档/注释承诺 vs live 实现"缺口信号，移交接线/配置维度复核。

---

## 四、反向证实（safe 记录，不立案）

1. **static 可变字段 0 处**；基线 12 处 static final 集合均为不可变类型（`List.of`/`Set.of`/`Pattern`）——与基线口径一致。
2. **ThreadLocal 0 处**——无池化线程清理风险。
3. **构造器 this-escape 0 处**（无构造器启线程/注册监听）。
4. **CallGraph 全方法 synchronized + 快照返回**（L27-66，含 `getForwardMap` 深拷贝）——同族问题已收敛，可作 C1-001 修复的模板。
5. **ensureSubServices 懒初始化正确**：synchronized 门 + 全部 29 个读点先过门（monitor HB 覆盖）。
6. **incrementalStatusMap**：synchronizedMap 全部 6 个访问点为单操作，无迭代/批量点（满足"synchronizedMap 迭代须在锁内"门禁的零迭代前提）；status 对象先建满再 put，无发布后变更。
7. **FlowDetector 双 CHM 缓存**：整引用替换式发布 + unmodifiableList 出口，无原地变更。
8. **LlmEdgeBudget.tryConsume**：get 预检 + `addAndGet` 复核，超限过量最多 `estimatedTokens`，有界良性。
9. **GraphMetricStore/GraphMetricMaterializer/OrmFingerprintStore/各 extractor/synthesizer 均无状态**（final 注入或纯静态方法），无实例级共享可变面。
10. **ProjectAnalyzer.executor 预留未用**（CodeIndexService 使用 null 构造器），无线程泄漏面；与基线一致。

## 五、复核建议优先级

1. C1-001、C1-002 建议进入独立复核（证据完整、时序具体）。
2. C1-003 复核重点：确认"无装配点"结论（含反射/脚本装配可能性），若确认不可达则维持 P3 并将修复挂到接线前置条件。
3. CL-001/CL-002 建议优先转 C2/C9 维度深挖。

---

# C1 深挖第 2 轮

- **日期**: 2026-10-22（第 2 轮合并批）
- **本轮范围**: C1-001 失效面补全（SymbolTable 全部读点穷举）+ CodeCacheManager 三缓存表全部锁外读点穷举
- **盲审声明**: 仅读取第 1 轮 C1/C5 两文件；未读取其他维度文件及 plans/bugs/lessons/backlog。

## 一、C1-001 失效面补全：SymbolTable 全部读点穷举（证据表，不新增缺陷编号）

判定口径：共享缓存实例 = 经 `CodeCacheManager.getOrRebuildSymbolTable` 发布的同一 `SymbolTable`（存在运行期写者 `addToSymbolTableCache`）；本地实例 = 分析事务内新建（`ProjectAnalyzer.globalSymbolTable`、`persistSingleFileInSession.mergedTable`、`indexFile.ownSymbols`、`reanalyzedSymbols`、`HeuristicContext.symbolTable`），单线程生命周期，无并发面。

读点分类：**点读** = `getByQualifiedName`/`getById`/`size`/`isTruncated`（无 synchronized，与缓存写者 `add` 构成 C1-001 数据竞态面）；**快照** = `getAll`/`findAllByQualifiedNamePrefix`（synchronized + 防御性拷贝，安全 ○）。

### 点读站点全清单（全部折叠进 C1-001 现有根因与建议，无需逐站点修复）

| # | 类:行 | 读方法 | 实例 | 所在查询路径 |
|---|---|---|---|---|
| 1-15 | CodeGraphService L314/318（getSurprisingConnections）、L348（getExplorationQuestions）、L376/380（exportGraphWiki）、L399（getImpactAnalysis）、L603/650/664/700/714/732（type/call hierarchy 构建器）、L810（toImpactedSymbolDTO）、L960/984（hub/bridge 打分）、L1036（findDominantPackage） | getById（15 处） | 共享 | 图查询族 |
| 16-22 | CodeGraphService L536（getTypeHierarchy）、L582/608（collectRelevantInheritances）、L632（buildTypeHierarchy）、L682（buildCallHierarchy）、L1074/1081（findSymbolByQualifiedName） | getByQualifiedName（7 处） | 共享 | 图查询族 |
| 23-26 | CodeGraphService L110（detectCommunities）、L446/450/457（getCriticalNodes） | size（4 处） | 共享 | 图查询族 |
| 27 | CodeGraphService L90（warnIfCacheTruncated） | isTruncated | 共享 | 全部图查询前置 |
| 28-33 | FlowDetector L127（detectFlows 入口解析）、L271/287（traceForward BFS 循环内）、L317/349/386（buildFlow/路径标注） | getById（6 处，**位于 BFS 循环体内，单请求可达数千次点读**） | 共享（CodeIndexService L2565 注入） | detectFlows |
| 34 | ChangeAnalyzer L370（resolveCallers，CodeIndexService L2642 注入） | getById | 共享 | analyzeChanges |
| 35 | GraphMetricMaterializer L174（buildGraphSummary） | size | 共享（L88 注入；读路径自愈 + 写路径两侧触达） | 物化 |
| 36 | DeadCodeDetector L195（detectDeadCode 统计，CodeIndexService L2668 注入） | size | 共享 | detectDeadCode |

快照读（安全 ○，记表）：CodeGraphService L220/366/1086、CodeQueryService L891（findImplementations）、CodeIndexService L1592（mergedTable 复制源）、KnowledgeGapAnalyzer L37、SpringEventSynthesizer L36/136、DocKeywordExtractor L40、NameSimilarityExtractor L35、LlmSemanticEdgeExtractor L60、DeadCodeDetector L163、FlowDetector L98/119/406、ChangeAnalyzer L243、EntryPointScorer L159。

本地实例点读（无并发面 ○，记表）：ProjectAnalysisResult L46、CodeCacheManager L163（写者自检，持 cacheManager 锁且全部写者同锁）、CodeIndexService L1328/1377/1379（synthesizeAndPersistHeuristicEdges → result.getGlobalSymbolTable() 本地）、L1423/1447/1469/1473（resolveQualifiedNamesToIds/继承解析，本地 merged/resolve 表）、L1597（mergedTable）、L2127/2183（reanalyzedSymbols）。

**补全结论**：C1-001 的失效面从第 1 轮记录的「CodeGraphService 15+ 处」扩大为 **36 个点读站点 × 5 个读类**（CodeGraphService 27、FlowDetector 6、ChangeAnalyzer 1、GraphMetricMaterializer 1、DeadCodeDetector 1），并新增两个事实：(a) FlowDetector 的 getById 在 BFS 循环体内，是密度最高的读点（单请求数千次无 happens-before 读）；(b) 读路径自愈（GraphMetricMaterializer 经 detectCommunities/getCriticalNodes 等触发 materialize）使物化写路径与点读在同一请求栈上交叠。根因、触发时序与修复建议均以 C1-001 原文为准（补 synchronized 或 CHM 化在 SymbolTable 单点收敛，36 个站点自动闭合），本表仅作 C1-001 复核与修复验证的完整检查单。

## 二、CodeCacheManager 三缓存表锁外读点穷举（判定表）

| 缓存表 | 发布点（锁内） | 锁外读点 | 发布后写者 | 判定 |
|---|---|---|---|---|
| `CacheEntry.cache.symbolTable` | getOrRebuildSymbolTable L113-127 | 36 点读 + 16 快照（见上表） | `addToSymbolTableCache` L157-171（唯一，持 cacheManager 锁 + SymbolTable monitor） | ● 折叠进 C1-001 |
| `CacheEntry.cache.callGraph` | getOrRebuildCallGraph L129-143 | CodeGraphService 12 处、FlowDetector、ChangeAnalyzer、DeadCodeDetector、GraphMetricMaterializer、EntryPointScorer、KnowledgeGapAnalyzer——全部经 synchronized 方法读（getCallers/getCallees/getAllNodeIds/getForwardMap 均 AR-145/148 收敛）；`isTruncated()`（L19-21）无 synchronized，但 `setTruncated` 仅在 rebuildCallGraph L244 **发布前**调用，发布后无写者 → 锁链（写者持 cacheManager 锁 → 读者取引用时持同一锁）建立 happens-before | 无 | ○ 安全（isTruncated 无同步为与 SymbolTable 同型的 P3 模式瑕疵，发布前写 + 锁链可见性使其无实际竞态） |
| `CacheEntry.cache.dependencies` | getOrRebuildDependencies L173-186 | CodeGraphService 5 处（L1141 getDepGraph、L1184 findDependentFiles、L1221/1237/1253 三个邻接构建器）——锁外 for-each 迭代 | **无**（grep 全单元无 add/remove/sort/clear 调用点；行实体字段无写点） | ○ 安全：列表发布后不可变 + HB 经锁链； ORM 实体行仅读。备注：deps 内容规模无界属 C5-004，不属 C1 |
| `analysisCacheMap` 结构 | getOrCreateEntry/getValidEntry/evictIfNeeded/invalidate 全在 lock 内 | `cacheSize()` L188-190 无锁读 size——**零调用方**（dead method，第 1 轮已记） | — | ○（建议随 C1-001 修复顺手删除或加锁，防未来误用） |

## 三、本轮终止

- 全部新证据（36 点读站点、三表锁外读点、isTruncated 发布前写事实）均为 C1-001 既有根因的失效面扩大或良性确认，**不构成新缺陷**：无新的共享可变状态载体（static/ThreadLocal/this-escape 复查仍为 0）、无新的无同步写者、无新的复合操作竞态。
- **未发现新的高价值问题。深挖结束（本轮终止）。**

---

# C1 维度复核结论（独立复核）

- **日期**: 2026-10-23（CRA WI 6.3 Phase 4，C1+C5 维度独立复核子代理）
- **复核方法**: 逐条重读 live 源码 file:line（基线 @ 7b0ccc89d4，与初审同版无漂移）；C1-001 另对照 2026-09 G11-15-02 原文（`ai-dev/audits/2026-09/2026-09-30-1726-deep-audit-nop-entropy-full/11-code-graph.md` L92-120/L423/L436）；C1-003 逐调度重演事务交错。不修改初审既有内容，仅追加本节。

## 逐条裁定

### C1-001（P2）→ **保留，维持 P2**

- 证据全部重演属实：`SymbolTable.add` synchronized（L26-33）vs 点读 `getByQualifiedName`/`getById`（L35-41）、`size`（L49-51）无同步；`CodeCacheManager.getOrRebuildSymbolTable`（L113-127）锁内发布共享实例、`addToSymbolTableCache`（L157-171）持 cacheManager 锁对已发布实例原地 `add`（该锁不覆盖锁外持引用的读者）；`CodeGraphService.getTypeHierarchy` L533-536 锁外点读属实。第 2 轮 36 站点清单抽查吻合（FlowDetector BFS 循环体 L271/287、ChangeAnalyzer L370、GraphMetricMaterializer 唯一键注释 L140-142 均确认）。
- **G11-15-02 三态裁定：仍成立（未修复）**。2026-09 立案（P2，SymbolTable.java:26-51，同根因同发布链）；当前代码 @7b0ccc89d4 点读仍裸读，未纳入修复。**C1-001 = G11-15-02 的超集/扩面复活**：同根因、同判级 P2；新增事实为 36 点读站点×5 读类全清单、FlowDetector BFS 高密度读点、读路径自愈交叠（ensureMaterializedForGraphMetrics L347-356）。G11 复核附注（该审计 L436）的 `addToSymbolTableCache` 判重 check-then-act 属同源，已含于本案修复范围。
- **处置**：两案收敛为单一修复工单（C1-001 吸并 G11-15-02，避免双计数）；修复验证以 36 站点清单为检查单。行号勘误：证据片段头注 "CodeCacheManager.java L157-167" 应为 L157-171（不影响结论）。

### C1-002（P2）→ **保留，维持 P2**

- 6 个失效点行号逐一核准：L350（indexDirectory，锁外先行、事务后无二次失效）/L426（indexFile，提交后锁内○）/L438（indexFileSet，锁外先行且无后续）/L888（deleteIndex，锁内但先于删事务提交）/L1218（triggerIncrementalIndex，提交后锁内，注释自证危害）/L2850（batchDeleteFileRecords，锁内但先于删事务提交），与原文 F3 清单完全一致。时序重演成立：失效→并发查询以 pre-commit DB 状态回填→提交后无失效→陈旧投影存活至 TTL 1h。判级恰当（公开查询面新鲜度缺陷、DB 源真无损、TTL 自愈）。

### C1-003（P3，接线后升 P1）→ **保留 P3；修正失败模式描述**

- 未接线结论核准：主代码零调用方、无 beans.xml 装配、仅 `TestShardBuildCoordinator` 引用；`setMetricMaterializer` 主代码零调用。
- **修正**：原文"双双进入 allCompleted 分支 → 双重发布"在**单事务/调用**语义下不可达——`completeShard` 内 `loadLedger`（L97）先于本事务提交，T1 看到 T2 的写入要求 T2 已提交，T2 看到 T1 的写入要求 T1 已提交，与"各自 loadLedger 先于各自提交"联立矛盾。真实主失效模式为**发布丢失**：双方都在对方提交前读 ledger → 都判 false → 双双提交后 ledger 全 COMPLETED 但 status 滞留 BUILDING、永不物化。"双重发布"仅在语句级 auto-commit 下可调度——而 `completeShard` 现状恰是 `runInSession` 未显式开事务（与类 javadoc "同一事务内发布"承诺不符，构成附加契约缺口，随本条一并登记）。
- 两种模式均违背原子发布契约，原文修复建议（条件 UPDATE 影响行数作发布权裁决）同时覆盖两者，维持不变。判级不变：未接线 P3、接线前置修复。

### C1-004（P3）→ **保留，维持 P3**

- 行号勘误：引用范围 "L227-233" 实为 **L248-253**（字段声明 L222 正确）；证据片段与 live 代码逐字符一致。良性论证核准：`OrmFingerprintStore` 三 final 字段无状态（L24-26），竞态发布后果等价，仅模式违例登记。

### C1-005（P3）→ **保留，维持 P3**

- L759 get / L760 判定 / L778、L789 put 属实，中间隔 normalizeWorkdir/validateGitRef×2/requireRepoRoot/requireHeadAt/gitDiffNames/triggerIncrementalIndex 长链；`resetRebuildDebounce`（L172-175）注释自证防抖语义用途。下游 withIndexLock 串行化 + 指纹 no-op 两层兜底核准。P3 恰当。

## CL 线索裁定（CL-001~006）

| CL | 裁定 | 复核要点 |
|---|---|---|
| CL-001 | **确认** | 全局单把 ReentrantLock（L73）+ rebuildSymbolTable/CallGraph/Dependencies（L192-270）锁内 DB 分页属实 |
| CL-002 | **确认** | L14-19 computeIfAbsent 承载 LLM RPC + 无界无淘汰；与 C5-007 同站点，裁定一致 |
| CL-003 | **确认（附联动注）** | prepare L61-101 持 repoLocks 执行 clone/fetch/checkout 属实；注：因 CL-101（readAllBytes 先于 waitFor）git 超时分支实际不可达，两线索联动放大互等时长 |
| CL-004 | **确认** | deleteIndex L883-915 仅清 incrementalAffectedFilesMap（L911），indexLocks/rebuildDebounceMap 无 per-indexId 清理；resetRebuildDebounce 为测试用全清，不构成反驳 |
| CL-005 | **确认** | 默认装配 OrmFingerprintStore（L248-253）；InMemoryFingerprintStore 主代码零引用（仅自身声明） |
| CL-006 | **确认** | ShardBuildCoordinator 零装配零主代码调用（与 C1-003 复核一致） |

## 批次覆盖核验（三源码根痕迹）

- **nop-code-core**：SymbolTable/CallGraph（C1-001）、ProjectAnalyzer/CodeFileAnalysisResult（C5-002 关联）、LanguageAdapterRegistry（载体 1-14）、InMemoryFingerprintStore/IFingerprintStore/IncrementalDetector（载体表/CL-005）——痕迹在案。
- **nop-code-flow**：FlowDetector（载体 1-12/1-13 + C1-001 失效面 6 站点）、ChangeAnalyzer（1-34 读点 + CL-005 关联）——痕迹在案。
- **nop-code-service**：impl 7 类（CodeCacheManager/CodeIndexService/CodeGraphService/CodeQueryService/CodeSearchService/GraphMetricMaterializer/ShardBuildCoordinator）+ graph 包 4 提取器/GraphMetricStore/CodeRelationGraphLoader + cluster（ClusterWorkspaceManager/IndexAccessPolicy）+ semantic（LlmEdgeCache/LlmSemanticEdgeExtractor）+ entity 3 biz model——痕迹在案。
- **单元计数核准**：nop-code-service 实测 53f/11,193 行与审计单元声明精确吻合；core 51f、flow 12f 与基线 roots 一致。基线 file（2026-10-23T01:37:54Z @ 7b0ccc89d4）三 roots 声明与实际审计范围一致。**覆盖核验通过。**

## 最终保留项

- **立案保留 5/5**：C1-001（P2，吸并 G11-15-02）、C1-002（P2）、C1-003（P3，失败模式修正为"发布丢失为主、双重发布限 auto-commit 语义"，接线后升 P1 不变）、C1-004（P3）、C1-005（P3）。**无驳回、无降级、无升级。**
- **CL 确认 6/6**：CL-001~006 全部确认（CL-003 附 CL-101 联动注）。
- 判级分布：P2×2 + P3×3（与初审一致）。
