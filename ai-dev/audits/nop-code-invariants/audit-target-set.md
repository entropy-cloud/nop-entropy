# nop-code 审计目标集与基线确认

> Status: active
> Last Reviewed: 2026-08-13
> Source: I0 盘点（`ai-dev/plans/2026-08-13-0709-1-nop-code-invariant-i0-inventory-and-baseline.md` Phase 3）
> 依赖: `ar-status-matrix.md`（Phase 1 产出，open 方法交叉标注）
> 后继: I2 跑门禁优先靶点 = 本文件标注 `open` 的方法

## 目的

枚举 nop-code 全部 SearchService / IndexManager / 删除路径的公共与变更型方法（附 `类名.方法名` + `文件:行`），作为 I1 门禁对接点和 I2 审计优先靶点。同时确认当前零 nop-code 代码不变式门禁基线。

---

## 1. 审计目标集 — 方法清单

> 路径前缀：`nop-code/nop-code-service/src/main/java/io/nop/code/service/`
> R/W 列：read（只读查询）/ write（变更/持久化/删除）/ init（构造/setter）

### 1.1 CodeSearchService

文件：`impl/CodeSearchService.java`

| 方法 | 行 | R/W | open? | 备注 |
|------|----|-----|-------|------|
| `searchCode(...)` | 46 | read | — | 入口 |
| `searchViaEngine(...)` | 66 | read | **open** | AR-167：硬编码 HYBRID |
| `searchBySymbolName(...)` | 120 | read | — | |
| `searchFullText(...)` | 143 | read | — | |
| `searchCombined(...)` | 166 | read | — | |
| `buildFilePathCache(String)` | 196 | read | **open** | AR-168：投影已修但**无 setLimit**（INV-01✓ / INV-04✗） |
| `filterByFilePattern(...)` | 295 | read | **open** | AR-165：不完整正则转义 |
| `globToRegex(String)` | 303 | read | — | |
| `filterByLanguage(...)` | 320 | read | **open** | AR-42：removeIf 修改传入列表 |

无删除/写入方法。全部只读。

### 1.2 CodeIndexService（变更型 + 查询型）

文件：`impl/CodeIndexService.java`

**索引/创建（write）：**

| 方法 | 行 | open? | 备注 |
|------|----|-------|------|
| `indexDirectory(String,String,String)` | 294 | — | 全量索引入口 |
| `indexFile(String,String,String)` | 314 | — | AR-03/181 已修（invalidateAnalysisCache 在锁内 ✓） |
| `persistSingleFileInSession(...)` | 1030 | **open** | AR-158：修改缓存 SymbolTable（INV-05） |
| `resolveQualifiedNamesToIds(...)` | 920 | **open** | AR-75：全实体加载（INV-01✗）；AR-178 已修（幂等 ✓） |
| `ensureIndexEntity(...)` | 1938 | **open** | AR-52：硬编码 language="Java" |

**删除（write）：**

| 方法 | 行 | open? | 备注 |
|------|----|-------|------|
| `deleteIndex(String)` | 529 | — | AR-12 已修（分页删除 ✓）；AR-127 已修（锁内移除 ✓） |
| `deleteEntitiesPaged(...)` | 562 | — | 分页删除辅助（batch 500 + evictAll） |
| `deleteFileRecords(String,List<String>)` | 1440 | **open** | AR-30/66：跨文件引用孤儿（INV-03✗） |
| `deleteEntitiesByFilter(...)` | 1527 | — | 全量加载删除（无分页） |
| `deleteRelationalBySymbolIds(...)` | 1516 | — | 全量加载删除 |
| `batchDeleteFileRecords(String,List<String>)` | 1834 | — | 委托 deleteFileRecords |

**增量（write）：**

| 方法 | 行 | open? | 备注 |
|------|----|-------|------|
| `triggerIncrementalIndex(String,String,String)` | 651 | — | AR-124 已修（路径 ✓）；AR-128 已修（锁 ✓） |
| `getProjectFilePaths(...)` | 1398 | **open** | AR-177：投影已修但**无 setLimit**（INV-04✗） |
| `buildInheritanceIndex(...)` | 865 | **open** | AR-180：MAX_QUERY_RESULTS 截断 |
| `loadExistingEdgeKeys(...)` | — | **open** | AR-180：截断 |

**查询（read，选列与门禁相关）：**

| 方法 | 行 | open? | 备注 |
|------|----|-------|------|
| `getIndexStats(String)` | 482 | — | AR-77 已修（countByQuery ✓） |
| `updateIndexStats(...)` | 1552 | — | AR-77 已修（countByQuery ✓） |
| `findImplementations(...)` | 1762 | **open** | AR-64/86：全量加载所有符号（INV-01✗） |

### 1.3 CodeGraphService

文件：`impl/CodeGraphService.java`。全部 read。

| 方法 | 行 | open? | 备注 |
|------|----|-------|------|
| `detectCommunities(String)` | 68 | — | |
| `getGraphAnalysis(String,int)` | 79 | — | |
| `getImpactAnalysis(String,String,int)` | 120 | — | |
| `getCriticalNodes(String,int)` | 154 | **open** | AR-173：BetweennessCentrality 无超时 |
| `getKnowledgeGaps(String)` | 184 | **open** | AR-174：computeCohesion 只统计出边 |
| `exportGraph(String,String,boolean)` | 200 | **open** | AR-36/151(r10)：escapeJson 缺陷 |
| `diffGraph(String,String)` | 214 | **open** | AR-172：双 Leiden；AR-151：entityToInheritance 残留 |
| `getTypeHierarchy(...)` | 244 | — | |
| `getCallHierarchy(...)` | 319 | — | |
| `entityToInheritance(...)` | 428 | **open** | AR-151：superTypeId→QN 残留 |
| `collectRelevantInheritances(...)` | 218 | **open** | AR-136：MAX_QUERY_RESULTS 截断 |

无删除/写入方法。

### 1.4 CodeQueryService

文件：`impl/CodeQueryService.java`。全部 read。

| 方法 | 行 | open? | 备注 |
|------|----|-------|------|
| `getModuleDigest(String,String,boolean)` | 243 | — | AR-130 已修（FilterBeans.in ✓） |
| `findReferencedBy(...)` | 666 | — | AR-129 已修（收集全部 ✓） |
| `findImplementations(...)` | 785 | **open** | AR-64/86：全量加载（INV-01✗） |
| `getSymbolById(String,String)` | 394 | **open** | AR-41：忽略 indexId |

无删除/写入方法。

### 1.5 CodeCacheManager

文件：`impl/CodeCacheManager.java`

| 方法 | 行 | R/W | open? | 备注 |
|------|----|-----|-------|------|
| `getValidEntry(String)` | 64 | read | — | |
| `getOrCreateEntry(String)` | 75 | write | — | |
| `getOrRebuildSymbolTable(...)` | 102 | read/rebuild | — | |
| `getOrRebuildCallGraph(...)` | 118 | read/rebuild | — | |
| `invalidateAnalysisCache(...)` | 134 | write | — | |
| `addToSymbolTableCache(...)` | 146 | **write** | **open** | AR-158：原地修改缓存 SymbolTable（INV-05✗） |
| `getOrRebuildDependencies(...)` | 162 | read/rebuild | — | |

### 1.6 CodeClassLoader

**不存在**。nop-code 模块中无 `CodeClassLoader` 类（grep 零命中）。审计目标集中该类别不适用。

### 1.7 删除路径汇总

| 方法 | 文件:行 | open? |
|------|---------|-------|
| `deleteIndex` | `CodeIndexService.java:529` | — |
| `deleteEntitiesPaged` | `CodeIndexService.java:562` | — |
| `deleteFileRecords` | `CodeIndexService.java:1440` | **open**（跨文件孤儿） |
| `deleteRelationalBySymbolIds` | `CodeIndexService.java:1516` | — |
| `deleteEntitiesByFilter` | `CodeIndexService.java:1527` | — |
| `batchDeleteFileRecords` | `CodeIndexService.java:1834` | — |

### 1.8 ORM 模型实体（11 个）

文件：`nop-code/model/nop-code.orm.xml`

| 实体 | useLogicalDelete | cascadeDelete 子关系 | 唯一约束 | open? |
|------|:---:|---|---|-------|
| NopCodeIndex | 无 | files,symbols,deps,flows,usages,calls,inheritances,annotations,semanticEdges | uk_name | — |
| NopCodeFile | 无 | **无** | filePathKey | **open** AR-149 |
| NopCodeSymbol | 无 | annotations,flowMemberships,callees,callers,superTypes,subTypes | — | **open** AR-150(usages) |
| NopCodeUsage | 无 | 无 | uk_usage_unique | — |
| NopCodeCall | 无 | 无 | uk_call_unique | — |
| NopCodeInheritance | 无 | 无 | uk_inheritance_unique | — |
| NopCodeAnnotationUsage | 无 | 无 | uk_annotation_unique | — |
| NopCodeDependency | 无 | 无 | uk_dependency_unique | — |
| NopCodeFlow | 无 | memberships | — | — |
| NopCodeFlowMembership | 无 | 无 | flowSymbolKey | **open** AR-179（缺 indexId 列） |
| NopCodeSemanticEdge | 无 | 无 | uk_semantic_edge_unique | — |

> **确认**：11 实体均无 `useLogicalDelete`。AR-176/AR-54 前提过时。

---

## 2. 基线确认 — 零 nop-code 代码不变式门禁

### 2.1 无 nop-code invariant 检查工具

可复现证据命令：

```bash
ls ai-dev/tools/*.mjs | grep -i 'nop-code-invariant'
# 输出：（空，零命中）
```

`ai-dev/tools/` 下有 17 个 `.mjs` 工具（含 stream 先例 `check-nop-stream-audit-manifest.mjs`），但**无** `check-nop-code-invariants.mjs` 或任何匹配 `*nop-code-invariant*` 的文件。

### 2.2 无 nop-code invariant JUnit 测试

可复现证据命令：

```bash
rg -l 'invariant' nop-code/*/src/test/ --glob '*.java'
# 输出：（空，零命中）
```

`nop-code*/src/test/` 下无任何包含 "invariant" 的 Java 测试类。现有测试为功能/集成测试（如 `TestCodeIndexService.java`、`TestConcurrentIndexing.java`、`TestIncrementalIndexWithDb.java`），均非不变式门禁结构。

### 2.3 ArchUnit 未引入

```bash
rg 'archunit' --glob 'pom.xml' .
# 输出：（空，零命中）
```

全仓无 ArchUnit 依赖。I1 若需 ArchUnit 须先加依赖。

---

## 3. open 方法交叉标注（I2 优先靶点）

以下方法在 Phase 1 矩阵中判定为 `open`，是 I2 跑门禁的优先靶点：

| 优先级 | 方法 | 不变式 | AR-ID |
|--------|------|--------|-------|
| P0 | `CodeQueryService.findImplementations` | INV-01 | AR-64/86 |
| P0 | `CodeIndexService.resolveQualifiedNamesToIds` | INV-01 | AR-75 |
| P0 | `CodeSearchService.buildFilePathCache` | INV-04（无 LIMIT） | AR-168 |
| P0 | `CodeIndexService.getProjectFilePaths` | INV-04（无 LIMIT） | AR-177 |
| P0 | `CallGraph.getAllNodeIds/getForwardMap` | INV-05 | AR-145(r8) |
| P1 | `CodeIndexService.persistSingleFileInSession` | INV-05 | AR-158 |
| P1 | `CodeCacheManager.addToSymbolTableCache` | INV-05 | AR-158 |
| P1 | `CodeIndexService.deleteFileRecords` | INV-03 | AR-30/66 |
| P1 | `CodeGraphService.entityToInheritance` | INV-02/数据一致性 | AR-151 |
| P1 | ORM: NopCodeFile cascadeDelete | INV-02 | AR-149 |

> open 方法清单与 Phase 1 矩阵一致（已交叉核对）。
