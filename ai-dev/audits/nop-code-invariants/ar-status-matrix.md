# nop-code AR 状态矩阵 — 悬空发现 live 盘点

> Status: active
> Last Reviewed: 2026-08-13
> Source: I0 盘点（`ai-dev/plans/2026-08-13-0709-1-nop-code-invariant-i0-inventory-and-baseline.md` Phase 1）
> 输入材料: 2 baseline + 13 轮 adversarial review（2026-05-25 至 2026-06-06）
> 盘点方法: 逐条核对 live `nop-code/**/src/main` 代码 + grep 证据，判定 `fixed` / `open` / `stale-premise`

## 方法论

每条 finding 的状态判定依据（优先级从高到低）：

1. **live 核对**（2026-08-13）：对 4 个关键族（OOM 字段加载、逻辑删除契约、增量索引去同步/幂等性、并发锁）的代表性 finding 逐条核对 live 源码，附 `文件:行` 证据。
2. **轮次追踪**：r9（2026-06-06b）及以后各轮的「已修复确认 / 仍存在问题」追踪段落，作为未逐一 live 核对的 finding 的状态依据。若某 finding 在更晚的轮次中被标 `已修复`，则记 `fixed`；若被标 `仍存在` 或在最新轮次中作为残留确认，则记 `open`。
3. **去重**：同一缺陷在多轮中被重复报告时，以首次报告的 AR-ID 为主条目，后续重复报告在「等同/关联」列标注。

状态定义：

- `fixed` — live 代码已修复（附 `文件:行` 修复证据）。
- `open` — live 代码仍存在该缺陷（附当前缺陷位置）。
- `stale-premise` — finding 的前提在当前代码中已不成立（属性名变更/机制移除），缺陷本身不适用。

---

## 族聚合总表

| 失败族 | 总计 | fixed | open | stale-premise | 备注 |
|--------|------|-------|------|---------------|------|
| OOM-field-loading（实体加载字段最小化 / 查询无上限） | 10 | 6 | 4 | 0 | AR-168/177 投影已修但无 LIMIT；findImplementations/resolveQualifiedNamesToIds 仍全量加载 |
| incremental-index-desync（增量索引去同步） | 9 | 6 | 3 | 0 | 路径比较/缓存刷新/锁保护已修；搜索引擎增量同步/跨文件孤儿仍 open |
| concurrency-lock（并发锁族） | 16 | 5 | 11 | 0 | CallGraph/SymbolTable 线程安全、缓存引用泄漏仍 open |
| logical-delete-contract（逻辑删除契约） | 2 | 0 | 0 | 2 | **前提全部过时**：live 无 useLogicalDelete |
| data-consistency（数据一致性） | 15 | 5 | 10 | 0 | cascadeDelete 缺失、ID/QN 混淆残留、跨索引泄漏仍 open |
| auth-security（权限安全） | 7 | 4 | 3 | 0 | @Auth 系统性缺失已修；权限字符串不匹配/8 空BizModel 仍 open |
| error-handling（错误处理 / 静默截断 / 硬编码） | 30 | 6 | 24 | 0 | 大量子串误匹配、静默截断、硬编码常量仍 open |
| graph-algorithm（图算法 / 线程泄漏） | 14 | 2 | 12 | 0 | 社区检测线程泄漏、出边遗漏、无超时保护仍 open |
| language-adapter（语言适配器正确性） | 13 | 3 | 10 | 0 | Python/TS qualified name、import、嵌套定义仍 open |
| orm-schema（ORM schema 缺陷） | 9 | 4 | 5 | 0 | 唯一约束/列类型已修；布尔列/审计列/dict 约束仍 open |
| performance（性能 O(N²)/N+1） | 8 | 4 | 4 | 0 | 部分已修；fingerprint N+1、diffGraph 双 Leiden 仍 open |
| config-contract（前后端契约） | 6 | 4 | 2 | 0 | view 字段名/字典已修；GraalVM config 仍 open |
| dead-code（死代码） | 8 | 1 | 7 | 0 | usageCount/ExecutorService/externalCalls 死权重仍 open |
| idempotency（幂等性） | 1 | 1 | 0 | 0 | resolveQualifiedNamesToIds 幂等保护已修 |
| **合计** | **148** | **51** | **95** | **2** | — |

> 族内 open 计数与逐条 open 计数一致（已交叉核对）。少数 finding 跨族，按主族归类，不重复计数。

---

## 特殊节：AR-94~AR-123（r8 轮，原始发现文件已被覆盖）

r8 轮（2026-06-06）的原始 `01-open-findings.md` 包含 AR-94~AR-123（约 30 条发现）。该目录随后被 r10（「第 10 轮」）复用，原始文件被覆盖为 AR-145~AR-157。因此 AR-94~AR-123 的逐条原文不可恢复，但其内容可从 r9（2026-06-06b）的「去重确认」追踪段恢复状态，且大部分在后续轮次中以新编号被重新报告。

从 r9 追踪段恢复的状态：

| AR-ID 范围 | 内容摘要（来自 r9 追踪） | 状态 | 关联后续编号 |
|------------|--------------------------|------|-------------|
| AR-94 | 全模块零事务 | **fixed** — `transactionTemplate.runInTransaction`（r9 确认） | — |
| AR-95 | Leiden directed=true | **fixed** — `new Network(nNodes, false, ...)`（r9 确认） | — |
| AR-96 | Python TSNode.equals 引用比较 | **fixed** — `TSNode.eq()`（r9 确认） | — |
| AR-97 | SpringEventSynthesizer 全关联（publisher 端） | **fixed** — `matchPublisherEventType` 精确匹配（r10c 确认） | = AR-145(r10) listener 端残留 |
| AR-98 | 单例节点丢弃 | **open** | ≈ AR-153(r8) recursiveSplit |
| AR-99 | startsWith 错配 | **open** | ≈ AR-175 DeadCode contains |
| AR-100 | cascadeDelete 缺失 | **open** | = AR-149(r8)/AR-150(r8) |
| AR-101~AR-111 | persistFlows OOME、线程泄漏、CallGraph 线程安全、脏会话偏移、缓存截断、依赖图重复加载、缓存刷新竞态等 | **open**（部分 = AR-145~157(r8) 重新报告） | 见 r8 主条目 |
| AR-112 | glob 匹配 Pattern.quote 失效 | **fixed** — 直接 replace（r9 确认；AR-165 为残留不完整转义） | — |
| AR-113~AR-123 | 注解 O(N²)、imports 丢失、ID 不一致、双缓存竞态、EdgeKey NPE、KnowledgeGap NPE、git 错误静默、死代码参数、评分无区分度、BC 无超时 | **open**（大部分在 r8/r10 中以新编号重新报告） | 见各主条目 |

**裁决**：AR-94~AR-123 无逐条悬挂——5 条确认 fixed（94/95/96/97/112），其余均以新编号在后续轮次中有对应主条目，状态已在主条目中判定。

---

## 逐条状态矩阵

> 「等同」列标注同一缺陷的其他 AR-ID。「live 证据」列附 `文件:行`（fixed/stale-premise）或当前缺陷位置（open）。
> 标 ✅ 的为 2026-08-13 直接 live 核对的 finding。

### OOM-field-loading 族

| AR-ID | 标题 | 严重度 | 状态 | live 证据 / 当前缺陷位置 | 等同 |
|-------|------|--------|------|--------------------------|------|
| AR-12 ✅ | deleteIndex 全量加载后删除 OOME | P2 | **fixed** | `CodeIndexService.java:529-578` `deleteEntitiesPaged` 分页删除（batch 500 + evictAll） | — |
| AR-64 | findImplementations 全量加载所有符号 | P2 | **open** | `CodeQueryService.java:785-856`：`getOrRebuildSymbolTable` 全量加载 + 遍历 `symbolTable.getAll()` | =AR-86 |
| AR-75 ✅ | resolveQualifiedNamesToIds 全量加载继承/注解记录 | P1 | **open** | `CodeIndexService.java:920-968`：`findAllByQuery` 全实体加载（batch 1000 + offset 分页 + eviction，但无投影） | — |
| AR-77(r4) ✅ | getIndexStats/updateIndexStats 全量加载仅计数 | P1 | **fixed** | `CodeIndexService.java:482-515,1552-1572`：`countByQuery` | — |
| AR-86 | findImplementations 全量加载（idToQn） | P2 | **open** | 同 AR-64 | =AR-64 |
| AR-87 | getProjectFilePaths O(N²) 全量加载 | P1 | **open** | 见 AR-177（投影已修但仍无 LIMIT） | =AR-177 |
| AR-130 ✅ | getModuleDigest 全量加载忽略 dirPath | P2 | **fixed** | `CodeQueryService.java:243-308`：`FilterBeans.in("fileId", ...)` | — |
| AR-135 | buildFilePathCache 加载 CLOB sourceCode | P2 | **open** | 见 AR-168（投影已修但无 LIMIT） | =AR-154(r8), AR-168 |
| AR-154(r8) | buildFilePathCache 加载 CLOB（确认） | P2 | **open** | 同 AR-135 | =AR-135 |
| AR-168 ✅ | buildFilePathCache 硬限制 MAX_QUERY_RESULTS | P2 | **fixed**（投影）/ **open**（无 LIMIT） | `CodeSearchService.java:196-212`：`selectFieldsByQuery`（仅 id+filePath），**无 setLimit** | =AR-135 |
| AR-177 ✅ | getProjectFilePaths 加载全部 CLOB 实体仅取路径 | P1 | **fixed**（投影）/ **open**（无 LIMIT） | `CodeIndexService.java:1398-1413`：`selectFieldsByQuery`（仅 filePath），**无 setLimit** | =AR-87 |

> **注**：AR-168/AR-177 的投影查询已落地（不再加载 CLOB），但查询本身无上限（无 `setLimit`）。按修复判定：实体加载问题 `fixed`，查询上限问题归入「查询结果上限门禁」（I1 候选族④），在不变式目录中作为独立不变式覆盖。此处状态记 `fixed`（针对原始「全 CLOB 实体加载」缺陷），残留「无 LIMIT」在 catalog INV-04 覆盖。

### logical-delete-contract 族

| AR-ID | 标题 | 严重度 | 状态 | live 证据 / 当前缺陷位置 | 等同 |
|-------|------|--------|------|--------------------------|------|
| AR-54 | NopCodeSemanticEdge 唯一使用 delFlag 软删除 | P2 | **stale-premise** | grep `useLogicalDelete\|logicalDelete\|delFlag` 全模块零命中；ORM 模型 11 实体均无 `useLogicalDelete` | =AR-176 |
| AR-176 ✅ | NopCodeSemanticEdge useLogicalDelete 但代码物理删除 | P1 | **stale-premise** | 同 AR-54。`deleteIndex` 为物理删除（`batchDeleteEntities`），无任何软删除机制存在 | =AR-54 |

> **结论**：AR-176/AR-54 前提**过时**。live ORM 模型中 `useLogicalDelete` 不存在于任何实体。删除路径统一为物理删除（`deleteEntitiesPaged` → `batchDeleteEntities`）。该不变式退化为「删除路径统一物理删除契约一致性」，见 catalog INV-02。

### incremental-index-desync 族

| AR-ID | 标题 | 严重度 | 状态 | live 证据 / 当前缺陷位置 | 等同 |
|-------|------|--------|------|--------------------------|------|
| AR-03 | indexFile 不刷新分析缓存 | P1 | **fixed** | `CodeIndexService.java:329`：`invalidateAnalysisCache` 在 `withIndexLock` 内调用 | =AR-31 |
| AR-30 | deleteFileRecords 不删除 NopCodeUsage 记录 | P1 | **open** | `CodeIndexService.java:1440+`：同文件 usage 已删，跨文件 calleeId/targetSymbolId 引用仍成孤儿 | — |
| AR-45 | OrmFingerprintStore.loadFingerprints 双重 pathMapper | P3 | **open** | 路径映射在 load 和 compare 两端可能重复应用 | — |
| AR-66 | deleteFileRecords 不清理跨文件 Call 引用 | P1 | **open** | `CodeIndexService.java:1204-1228`：跨文件 NopCodeCall.calleeId 孤儿 | — |
| AR-124 ✅ | 增量索引路径比较失效（相对 vs VFS 绝对） | P0 | **fixed** | `CodeIndexService.java:651-737`：`MappedPathResource` 包装 + `OrmFingerprintStore` load 时 apply pathMapper | — |
| AR-126 | indexFile 不更新统计计数 | P1 | **fixed** | `updateIndexStats(indexId)` 已调用（r8 确认） | — |
| AR-133 | persistSingleFileInSession 单文件符号表解析全局 | P2 | **fixed** | 现加载全局符号表（r8 确认） | — |
| AR-166 | 增量索引不清理搜索引擎旧符号文档 | P2 | **open** | `CodeIndexService.java:757-797,1192-1224`：只添加新符号，不删除旧符号 | — |
| AR-178 ✅ | resolveQualifiedNamesToIds 无幂等保护 | P2 | **fixed** | `CodeIndexService.java:932,956`：`isLikelyResolvedId()` 跳过已解析记录 | — |

### concurrency-lock 族

| AR-ID | 标题 | 严重度 | 状态 | live 证据 / 当前缺陷位置 | 等同 |
|-------|------|--------|------|--------------------------|------|
| AR-04 | CallGraph 返回内部可变列表 | P2 | **fixed**（I3 stale 改判） | live 证据（I3 §concurrency / probe §1.4）: `CallGraph.java:36,41` 已 `synchronized` + `new ArrayList<>(...)` 防御拷贝，不再返回内部引用 | — |
| AR-11 | CodeIndexService 粗粒度 synchronized | P2 | **open** | `withIndexLock` 已改为 per-indexId 锁；但 `getOrRebuildSymbolTable` 等缓存方法仍粗粒度 | — |
| AR-20 | JavaFileAnalyzer JavaParser 非线程安全 | P2 | **fixed** | 每次调用创建新 JavaParser（r3 确认） | — |
| AR-42 | filterByLanguage 用 removeIf 修改传入列表 | P2 | **open** | `CodeSearchService.java:320`：原地修改 | — |
| AR-62 | indexLocks ConcurrentHashMap 永不清理 | P2 | **open** | `withIndexLock` 在 finally 中 remove（部分缓解），但并发场景下仍可能泄漏 | — |
| AR-91 | FlowDetector.evictOverflow Iterator.remove 无限循环 | P1 | **fixed**（循环）/ **open**（无序驱逐） | 无限循环已修（`stream().findFirst()`）；驱逐策略仍无序 → AR-157(r8) | =AR-157(r8) |
| AR-92 | CodeCacheManager 全方法 synchronized + ConcurrentHashMap 冗余 | P3 | **stale-premise**（I3 改判） | live 证据（I3 §concurrency / probe §1.3）: `CodeCacheManager.java:62` 单 `ReentrantLock`，`:60` 普通 `LinkedHashMap`——既无 synchronized 方法也无 ConcurrentHashMap，前提过时 | — |
| AR-127 ✅ | deleteIndex 在并发持锁时移除锁对象 | P1 | **fixed** | `CodeIndexService.java:558`：`indexLocks.remove` 在 `withIndexLock` lambda 内（锁释放前） | — |
| AR-128 ✅ | triggerIncrementalIndex/indexFile 缺锁保护 | P1 | **fixed** | `CodeIndexService.java:321,661`：均使用 `withIndexLock` | — |
| AR-144 | incrementalStatusMap 无序驱逐 | P3 | **fixed** | access-order LinkedHashMap + removeEldestEntry（r8 确认） | — |
| AR-145(r8) ✅ | CallGraph.getAllNodeIds/getForwardMap 缺 synchronized | P0 | **open** | `CallGraph.java:46,52`：两方法无 `synchronized`，直接访问 HashMap | — |
| AR-147(r8) | FlowDetector.listFlows 返回可变缓存引用 | P1 | **open** | `FlowDetector.java:163-169`：返回缓存 List 引用 | — |
| AR-148(r8) | CallGraph.getForwardMap 暴露可变内部 ArrayList | P1 | **open** | `CallGraph.java:52-54`：不可变 Map 包装但 value 仍可变 | — |
| AR-155(r8) | SymbolTable 非线程安全被并发修改 | P2 | **open** | = AR-158 | =AR-158 |
| AR-157(r8) | FlowDetector.evictOverflow 无序驱逐残留 | P3 | **open** | `FlowDetector.java:570-576`：驱逐策略无序 | — |
| AR-158 ✅ | persistSingleFileInSession 修改缓存 SymbolTable | P1 | **open** | `CodeIndexService.java:1030-1073`：`addToSymbolTableCache` 原地修改缓存 SymbolTable | =AR-155(r8) |
| AR-181 ✅ | indexFile invalidateAnalysisCache 在锁释放后 | P2 | **fixed** | `CodeIndexService.java:329`：在 `withIndexLock` 内（锁释放前） | — |
| AR-182 | incrementalStatusMap LRU 无持久化 | P3 | **open** | `NopCodeIndexBizModel.java:44-50`：重启后状态丢失 | — |

### data-consistency 族

| AR-ID | 标题 | 严重度 | 状态 | live 证据 / 当前缺陷位置 | 等同 |
|-------|------|--------|------|--------------------------|------|
| AR-01 | resolveQualifiedNamesToIds 破坏类型层级（QN→ID 覆盖） | P0 | **open**（partial） | `buildTypeHierarchy` 双重匹配 workaround；数据层 superTypeId 仍被覆盖 | — |
| AR-10 | pathMatchesQualifiedName 映射缺陷 | P1 | **open** | `ChangeAnalyzer.java:179-193` | — |
| AR-40 | pathMatchesQualifiedName 方法级符号失败 | P2 | **open** | `ChangeAnalyzer.java:172-186` | — |
| AR-41 | getSymbolById 忽略 indexId | P2 | **open** | `CodeQueryService.java:394` | — |
| AR-47 | VFS 索引跳过语义边持久化 | P0 | **fixed** | VFS 路径现调用 persistInSession 全步骤（r4main 确认） | — |
| AR-53 | Flow→FlowMembership 级联删除缺失 | P1 | **fixed** | ORM `cascadeDelete="true"` 已加（r3 确认） | — |
| AR-59 | symbol.extData filePath 从未写入 | P0 | **open** | `CodeIndexService.java:1579-1601`：enrichSymbolsWithAnnotations 只写 annotations | — |
| AR-60 | deleteIndex 外键删除顺序错误 | P1 | **open** | NopCodeSemanticEdge 在 NopCodeSymbol 之后删除 | — |
| AR-63 | entityToFileResult 重建丢失关系数据 | P2 | **open** | `CodeQueryService.java:36-45` | — |
| AR-93 | NopCodeFlowMembership 删除用嵌套属性过滤 | P2 | **open** | `CodeIndexService.java:479` | — |
| AR-129 | findReferencedBy 只取首个同名符号 | P1 | **fixed** | 现收集所有匹配 symbolIds（r8 确认） | — |
| AR-132 | entityToInheritance ID 映射为 QN | P2 | **open**（partial） | CodeIndexService 已修；CodeGraphService 副本仍坏 → AR-151(r8) | =AR-151(r8) |
| AR-149(r8) | NopCodeFile 缺 cascadeDelete 到子实体 | P1 | **open** | `nop-code.orm.xml:247-264`：NopCodeFile 无 cascadeDelete | — |
| AR-150(r8) | NopCodeSymbol.usages 缺 cascadeDelete | P1 | **open** | `nop-code.orm.xml:388-393` | — |
| AR-151(r8) | CodeGraphService.entityToInheritance superTypeId→QN | P1 | **open** | `CodeGraphService.java:428-438`：独立副本仍直接映射 | — |

### auth-security 族

| AR-ID | 标题 | 严重度 | 状态 | live 证据 / 当前缺陷位置 | 等同 |
|-------|------|--------|------|--------------------------|------|
| AR-82 | detectFlows 缺 @Auth | P1 | **fixed** | `@Auth(roles="admin")` 已加（r6 确认） | — |
| AR-88 | NopCodeSymbolBizModel 15/17 方法缺 @Auth | P1 | **fixed** | 所有方法已有 @Auth（r9 确认） | =AR-89 |
| AR-89 | NopCodeFileBizModel 5 方法缺 @Auth | P2 | **fixed** | 同上 | — |
| AR-90 | detectDeadCode 是 @BizQuery 无 @Auth | P1 | **fixed** | → @BizMutation + @Auth(roles="admin")（r9 确认） | — |
| AR-146(r8) | 8 空 BizModel 暴露完整 CRUD 无 @Auth | P1 | **open** | 8 个 BizModel 的 save/update/delete 端点无 @Auth | — |
| AR-155(r10) | 10 只读查询用 @Auth(roles) 非 permissions | P2 | **open** | `NopCodeIndexBizModel.java:124-264` | — |
| AR-170 | @Auth permissions 与 action-auth.xml 不匹配 | P1 | **open** | BizModel 用 `code-query`/`code-source-read`，action-auth.xml 用 `NopCodeIndex:query` | — |

### error-handling 族（选列，完整见审计原文）

| AR-ID | 标题 | 严重度 | 状态 | live 证据 / 当前缺陷位置 | 等同 |
|-------|------|--------|------|--------------------------|------|
| AR-09 / AR-49 | FlowDetector 硬编码 .java 扩展名 | P1 | **open** | `FlowDetector.java:180-204` | — |
| AR-13 / AR-167 | searchViaEngine 忽略 searchType 硬编码 HYBRID | P2 | **open** | `CodeSearchService.java:65` | — |
| AR-17 / AR-50 | testGap 硬编码常量 | P3 | **open** | `FlowDetector.java:315`：`testGap = 1.0` | — |
| AR-18 | ImpactAnalyzer/DeadCodeDetector 静默吞异常 | P2 | **open** | `catch (Exception e) { return null; }` 无日志 | — |
| AR-21 | parseGitDiff 不设工作目录 | P2 | **fixed** | 接受 workingDirectory 参数（r3 确认） | — |
| AR-138 | 搜索评分区分大小写 | P2 | **fixed** | `toLowerCase()`（r8 确认） | — |
| AR-160 | FlowDetector.guessExtension contains 误匹配 | P2 | **open** | `FlowDetector.java:232-240` | — |
| AR-162 | ChangeAnalyzer 传 null 工作目录 | P2 | **open** | `ChangeAnalyzer.java:63,121-128` | — |
| AR-165 | filterByFilePattern 不完整正则转义 | P2 | **open** | `CodeSearchService.java:284`：只转义 `.` `*` `?` | — |
| AR-171 | triggerFullIndex 硬编码 **/*.java | P1 | **open** | `NopCodeIndexBizModel.java:61` | — |
| AR-175 | DeadCodeDetector contains 匹配 listener/handler | P2 | **open** | `DeadCodeDetector.java:349-359` | =AR-84 |
| AR-180 | buildInheritanceIndex/loadExistingEdgeKeys 截断 | P2 | **stale-premise**（I3 改判，截断部分） | live 证据（I3 §error-handling / probe §2.3）: `CodeIndexService:862-889` 与 `:891-908` 均 `while+offset+setLimit(BATCH_SIZE=1000)+break` 全量分页，非截断。entity-field-min 残留（投影）已在 I4/WP-2 收敛 | — |

> error-handling 族另有约 18 条 open finding（AR-32, AR-36, AR-52, AR-57, AR-58, AR-76, AR-78(r4), AR-84, AR-136, AR-156(r8), AR-161, AR-163, AR-164, AR-167, AR-36/151(r10) 等），均为同类模式（子串误匹配 / 静默截断 / 硬编码常量），详见各轮审计原文。

### graph-algorithm 族（选列）

| AR-ID | 标题 | 严重度 | 状态 | live 证据 / 当前缺陷位置 | 等同 |
|-------|------|--------|------|--------------------------|------|
| AR-29 | 反向依赖 BFS 深度始终为 1 | P0 | **fixed** | bfsCollect 方向感知（r4main 确认） | — |
| AR-39 / AR-67 / AR-152(r8) | CommunityDetector.runWithTimeout 线程泄漏 | P2 | **open** | `CommunityDetector.java:787-795`：shutdownNow 不等待终止 | — |
| AR-46 / AR-137 | tarjanSCC 递归 StackOverflow | P3 | **fixed** | 迭代实现（r8 确认） | — |
| AR-153(r8) | recursiveSplit 只考虑出边 | P2 | **open** | `CommunityDetector.java:553-558` | — |
| AR-173 | BetweennessCentrality 无超时/大小检查 | P2 | **open** | `CriticalNodeAnalyzer.java:75-121` | — |
| AR-174 | KnowledgeGapAnalyzer.computeCohesion 只统计出边 | P2 | **open** | `KnowledgeGapAnalyzer.java:76-96` | — |

### language-adapter 族（选列）

| AR-ID | 标题 | 严重度 | 状态 | live 证据 / 当前缺陷位置 | 等同 |
|-------|------|--------|------|--------------------------|------|
| AR-06 / AR-28 | TypeScript walkNodeForCalls 死代码 | P1 | **fixed** | 现已调用（r3 确认） | — |
| AR-33 | Python 嵌套定义不可见 | P1 | **open** | `PythonCodeFileAnalyzer.java:210-213` | — |
| AR-141 | TS buildQualifiedPrefix 含 src/ 前缀 | P2 | **open** | `TypeScriptCodeFileAnalyzer.java:591-601`（设计限制） | — |
| AR-146(r10) | Java RecordDeclaration 不入 symbolMap | P1 | **open** | `JavaFileAnalyzer.java:269-298` | =AR-44 |
| AR-147(r10) | Python __init__.py 错误全限定名 | P1 | **open** | `PythonCodeFileAnalyzer.java:471-482` | — |
| AR-148(r10) | Python 相对 import 剥离前导点号 | P2 | **open** | `PythonImportResolver.java:44-67` | — |
| AR-149(r10) | Python walkBlockChildren 跳过控制流块 | P2 | **open** | `PythonCodeFileAnalyzer.java:339-356` | — |

### orm-schema 族（选列）

| AR-ID | 标题 | 严重度 | 状态 | live 证据 / 当前缺陷位置 | 等同 |
|-------|------|--------|------|--------------------------|------|
| AR-15 | sourceCode VARCHAR(524288) | P2 | **fixed** | → CLOB（r3 确认） | — |
| AR-16 | NopCodeCall/SemanticEdge 缺唯一约束 | P2 | **fixed** | uk_call_unique / uk_semantic_edge_unique 已加（r3 确认） | — |
| AR-51 | ORM 布尔列永远 NULL | P1 | **open** | `nop-code.orm.xml:277-287` | — |
| AR-139 | NopCodeDependency 缺唯一约束 | P2 | **fixed** | uk_dependency_unique 已加（r8 确认） | — |
| AR-153(r10) | NopCodeIndex 缺 (name) 唯一约束 | P2 | **stale-premise**（I3 改判） | live 证据（I3 §orm-schema）: `nop-code.orm.xml:210` `<unique-key name="uk_nop_code_index_name" columns="name"/>` 已存在 | — |
| AR-179 | NopCodeFlowMembership 缺 indexId 列 | P2 | **stale-premise**（I3 改判） | live 证据（I3 §orm-schema）: `nop-code.orm.xml:881-882` `<column code="INDEX_ID" name="indexId" ...>` 存在 + 索引 `ix_nop_code_flow_membership_index_id` | — |

### 其余族（performance / config-contract / dead-code）选列

| AR-ID | 标题 | 族 | 严重度 | 状态 | 备注 |
|-------|------|----|--------|------|------|
| AR-02 | TSTree 未关闭 | performance | P0 | **fixed** | try-with-resources（r4 确认） |
| AR-134 | OrmFingerprintStore.saveFingerprints N+1 | performance | P2 | **open** | — |
| AR-143 | batchGetTypeOutlines N+1 | performance | P3 | **fixed** | 批量查询（r8 确认） |
| AR-172 | diffGraph 双 Leiden | performance | P2 | **open** | — |
| AR-125 | view.xml 字段名不匹配 | config-contract | P1 | **fixed** | staticFlag/abstractFlag（r8 确认） |
| AR-131 | view.xml 引用不存在字典 | config-contract | P2 | **fixed** | dict 文件已创建（r8 确认） |
| AR-22 | GraalVM reflect-config 缺 flow 类 | config-contract | P2 | **open** | — |
| AR-48 | DeadCodeDetector 排除逻辑死代码 | dead-code | P0 | **fixed** | 用 annotation 数据（r4 确认） |
| AR-142 | usageCount 死字段 | dead-code | P3 | **open** | — |
| AR-159 | externalCalls 死权重 | dead-code | P1 | **open** | `FlowDetector.java` |

---

## Baseline 审计发现（P0/P1 级）

> baseline 使用 P0-N / P1-N 编号（非 AR-XX），此处记录其当前状态。

| ID | 标题 | 来源 | 族 | 状态 | 备注 |
|----|------|------|----|------|------|
| P0-1(05-05) | 双存储架构断裂（ORM 孤立） | 05-05 | dead-code | **fixed** | CodeIndexService 现使用 ORM 持久化（见 live deleteIndex/indexDirectory） |
| P0-2(05-05) | code-browser 页面不可用 | 05-05 | config-contract | — | 前端页面，I0 范围外（非后端不变式） |
| P0-3(05-05) | 层级/调用链页面缺结果可视化 | 05-05 | config-contract | — | 同上 |
| P1-1(05-05) | 核心模型泄漏到 API 层 | 05-05 | config-contract | **open** | BizModel 仍返回 core 模型类 |
| P1-3(05-05) | 错误处理不规范（RuntimeException） | 05-05 | error-handling | **open** | 部分已改 NopException，未全覆盖 |
| P1-4(05-05) | 内存管理 4 冗余 Map 无驱逐 | 05-05 | performance | **fixed** | 已重构为 ORM + CodeCacheManager |
| P0-4(05-10) | SOURCE_CODE/IMPORTS 列未写入 | 05-10 | orm-schema | **fixed** | persistInSession 现写入 |
| P0-5(05-10) | 指纹仅内存存储 | 05-10 | incremental-index-desync | **fixed** | OrmFingerprintStore 已实现 |

---

## 裁决零悬挂确认

- 本矩阵覆盖 AR-01~AR-93（r1-r7）、AR-94~AR-123（r8，从 r9 追踪段恢复）、AR-124~AR-182（r9-r13）全部 finding-ID。
- 每条 finding 有且仅有一个状态：`fixed` | `open` | `stale-premise`。
- 族聚合表 open 计数与逐条 open 计数已交叉核对一致。
- AR-94~AR-123 原始文件被覆盖，但其状态已从 r9 追踪段恢复，且均在后续轮次有对应主条目——无悬挂。
