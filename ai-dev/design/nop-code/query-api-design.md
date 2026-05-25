# nop-code 查询 API 设计

**日期**：2026-05-09（更新于 2026-05-25）
**范围**：`nop-code-service` BizModel 暴露的 GraphQL 查询 API
**状态**：**已实现**
**灵感来源**：ast-grep、ast-outline、code-review-graph

---

## 一、API 归属策略

按聚合根职责分配，不新建 BizModel。BizModel 方法默认返回 Entity，由 xmeta 控制字段可见性。仅无对应实体的计算结果才返回 DTO。

| API 类别 | 归属聚合根 | 有数据库表 | 理由 |
|---------|-----------|-----------|------|
| 索引生命周期 + 索引级分析 + 依赖图查询 + 图分析 + 流分析 | `NopCodeIndex` | ✅ `nop_code_index` | 输入是 indexId，属于索引级操作 |
| 符号查询（搜索/层级/注解/引用/死代码/实现） | `NopCodeSymbol` | ✅ `nop_code_symbol` | 符号实体 |
| 文件查询（大纲/树/源码） | `NopCodeFile` | ✅ `nop_code_file` | 文件实体 |
| 继承关系查询 | `NopCodeInheritance` | ✅ `nop_code_inheritance` | 继承实体 |

> **注意**：依赖图查询（`getDeps` / `getReverseDeps` / `findCycles` / `getDepGraph`）虽然操作 `NopCodeFileDependency` 实体，但输入是 `indexId`，属于索引级操作，归属 `NopCodeIndexBizModel`。`NopCodeFileDependencyBizModel` 只保留标准 CRUD。

**拒绝了什么**：
- 所有方法放 NopCodeIndex（之前的做法）→ 违反单一职责 → 已修正：索引级操作归 NopCodeIndex，实体级查询归对应 BizModel
- 为调用关系单独建 `NopCodeCallBizModel` → 调用关系存储在 `nop_code_usage` 表，调用链查询已归入 `NopCodeSymbolBizModel.getCallHierarchy()`，无需额外聚合根

---

## 二、CodeIndexApi（nop-code-api）

`nop-code-api` 模块提供 `CodeIndexApi` 接口，定义 5 个通用 API 方法，供外部系统集成使用：

```
fullIndex(request)     → ApiResponse<String>
searchCode(request)    → ApiResponse<List<Map>>
getOutline(request)    → ApiResponse<Map>
getTypeHierarchy(request) → ApiResponse<Map>
getCallHierarchy(request)  → ApiResponse<Map>
```

该接口使用通用 `ApiRequest<Map>` / `ApiResponse` 签名，适合 RPC 或 HTTP 网关集成。

---

## 三、核心查询 API

### 3.1 文件大纲（File Outline）

```
NopCodeSymbol__fileOutline(indexId, filePath) → FileOutlineDTO
```

### 3.2 模块摘要（Module Digest）

一页纸展示目录内所有文件的公共 API。

```
NopCodeSymbol__moduleDigest(indexId, dirPath, includePrivate) → [ModuleDigestDTO]
```

### 3.3 符号源码定位（Symbol Show）

按全限定名查找并返回源码片段。

```
NopCodeSymbol__showSymbol(indexId, qualifiedName, includeBody) → SymbolSourceDTO
```

依赖 `ISourceCodeProvider` 接口从文件系统按行号读取。

### 3.4 implements 查询

递归查找所有实现/继承指定类型的类。

```
NopCodeSymbol__findImplementations(indexId, qualifiedName, directOnly, maxDepth) → [SymbolDTO]
```

### 3.5 反向引用（Reverse References）

```
NopCodeSymbol__findReferencedBy(indexId, qualifiedName, kind, limit) → [ReferenceDTO]
```

### 3.6 类型层级（Type Hierarchy）

```
NopCodeSymbol__getTypeHierarchy(qualifiedName, indexId, direction, maxDepth) → TypeHierarchyDTO
```

### 3.7 调用层级（Call Hierarchy）

```
NopCodeSymbol__getCallHierarchy(qualifiedName, indexId, direction, maxDepth) → CallHierarchyDTO
```

### 3.8 依赖图查询

归属 `NopCodeIndexBizModel`：

```
NopCodeIndex__getDeps(indexId, filePath, depth) → DepGraphDTO
NopCodeIndex__getReverseDeps(indexId, filePath, depth, limit) → DepGraphDTO
NopCodeIndex__findCycles(indexId, minSize) → [[String]]
NopCodeIndex__getDepGraph(indexId, includeExternal) → DepGraphDTO
NopCodeIndex__findDependentFiles(indexId, filePath) → [String]
```

### 3.9 按注解搜索

```
NopCodeSymbol__findByAnnotation(indexId, annotationName) → [SymbolDTO]
```

利用已有 `nop_code_annotation_usage` 表。

### 3.10 代码搜索

```
NopCodeSymbol__searchCode(indexId, query, searchType, language, filePattern, limit) → [CodeSearchResultDTO]
```

### 3.11 公共 API Surface

```
NopCodeSymbol__publicSurface(indexId, dirPath) → [PublicAPIDTO]
```

---

## 四、索引操作 API

### 4.1 索引管理

归属 `NopCodeIndexBizModel`：

```
NopCodeIndex__triggerFullIndex(indexId, projectPath) → String
NopCodeIndex__triggerIncrementalIndex(indexId, projectPath, manifestPath) → int
NopCodeIndex__indexDirectory(indexId, directoryPath, filePattern) → int
NopCodeIndex__indexFile(indexId, filePath, sourceCode) → FileAnalysisDTO
NopCodeIndex__deleteIndex(indexId) → boolean
NopCodeIndex__getStats(indexId) → IndexStatsDTO
NopCodeIndex__getIncrementalStatus(indexId) → IncrementalStatus
```

### 4.2 图分析操作

归属 `NopCodeIndexBizModel`：

```
NopCodeIndex__detectCommunities(indexId) → CommunityDetectionResultDTO
NopCodeIndex__getGraphAnalysis(indexId, topN) → GraphAnalysisResultDTO
NopCodeIndex__getImpactAnalysis(indexId, symbolId, depth) → ImpactResultDTO
NopCodeIndex__detectFlows(indexId) → [ExecutionFlow]
NopCodeIndex__listFlows(indexId) → [ExecutionFlow]
NopCodeIndex__getFlow(indexId, flowId) → ExecutionFlow
NopCodeIndex__getAffectedFlows(indexId, changedFilePaths) → [ExecutionFlow]
NopCodeIndex__analyzeChanges(indexId, baselineCommitish, targetCommitish) → ChangeAnalysisResult
NopCodeIndex__getCriticalNodes(indexId, topN) → CriticalNodeResultDTO
NopCodeIndex__getKnowledgeGaps(indexId) → KnowledgeGapResultDTO
NopCodeIndex__exportGraph(indexId, format, communityView) → String
NopCodeIndex__diffGraph(baselineIndexId, targetIndexId) → GraphDiffDTO
```

注意：`diffGraph` 使用两个 `indexId` 参数（`baselineIndexId`, `targetIndexId`），而非 git commitish，用于比较两次不同索引的图状态。

### 4.3 符号级分析操作

归属 `NopCodeSymbolBizModel`：

```
NopCodeSymbol__detectDeadCode(indexId) → DeadCodeReport
```

---

## 五、错误处理约定

| 场景 | 行为 |
|------|------|
| 查询返回空结果 | 返回空列表 `[]` |
| 无效 `indexId` | 抛 `NopException` + `ERR_CODE_INDEX_NOT_FOUND`，`.param("indexId", indexId)` |
| `filePath` 未找到 | 返回 `null` |
| `qualifiedName` 未找到 | 返回 `null` |
| 参数格式错误 | 抛 `NopException` + 参数校验错误 |

---

## 六、设计决策

### 为什么返回 Entity 而不是 DTO

BizModel 方法直接返回 ORM Entity，框架通过 xmeta 控制字段暴露。不需要手写 DTO 来限制字段。

| 场景 | 返回什么 |
|------|---------|
| 背后有 ORM 实体的查询 | 直接返回 Entity |
| 计算派生结果（层级树/图/分析结果） | DTO |
| 多字段组合但无实体 | `@DataBean` |

### 为什么需要补充 xmeta

nop-code 当前缺少 xmeta 文件，导致：GraphQL 类型定义依赖运行时推导、字段可见性无法控制、框架无法做 schema 版本管理。补充 xmeta 是所有 API 正常工作的前提。

---

## 七、已知缺陷

| # | 问题 | 位置 | 影响 | 修复方向 |
|---|------|------|------|---------|
| 1 | `entityToFileResult()` 显式将 `sourceCode` 设为 null | `CodeIndexService` | `showSymbol` 无法返回源码 | 改为按需从文件系统读取，或直接存储 |
| 2 | BizLoader `indexId` fallback 硬编码 `"test"` | `CodeIndexService` BizLoader | 生产环境不可用 | 移除 fallback，要求显式传入 indexId |
| 3 | 每次图查询全量 rebuild SymbolTable + CallGraph | `CodeIndexService` 图分析方法 | 10万+符号时性能问题 | 引入 AnalysisCache 缓存构建结果，变更时失效 |

---

## 八、被否决的功能记录

| 功能 | 决策 | 理由 |
|------|------|------|
| Token 效率分级（`detailLevel` / `minimalContext`） | 不做 | GraphQL Selection Set 已提供字段级裁剪，额外分级收益不足以 justify 复杂度 |
| 代码重构工具（`IRefactorService`） | 不做 | nop-code 定位为只读索引服务，代码修改由上层工具（IDE/CI）负责 |
| 交互式图谱可视化 | 不做 | 通过 GraphML/Mermaid 导出满足静态可视化需求，交互式可视化由前端工具负责 |
| MCP 独立服务层 | 不做 | AI 层通过 GraphQL 访问，无需额外协议转换 |
