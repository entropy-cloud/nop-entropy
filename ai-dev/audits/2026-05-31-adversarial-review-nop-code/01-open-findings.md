# Adversarial Review: nop-code (2026-05-31 — 刷新审查)

> **审查类型**: 开放式对抗性审查
> **目标模块**: nop-code（含 13 个子模块）
> **审查日期**: 2026-05-31
> **审查方法**: 代码驱动的开放探索，从最近 5 个 Phase 修复后的残留问题和新代码入手。起始视角：重构回归猎手 + 10x 规模运维者 + 异常路径侦探。
> **去重基线**: 已读取 `ai-dev/audits/2026-05-31-adversarial-review-nop-code/`（AR-59 至 AR-74，前次审查）、`ai-dev/audits/2026-05-29-adversarial-review-nop-code/`（AR-01 至 AR-22）、`ai-dev/audits/2026-05-29-adversarial-review-nop-code-r2/`（AR-28 至 AR-46）、`ai-dev/audits/2026-05-29-adversarial-review-nop-code-r3/`（AR-47 至 AR-58）。自前次审查以来，新增 5 个 Phase 提交（Phase 1-5：安全修复、数据完整性、OOM 稳定性、LIMIT 修复、测试有效性）。

---

## 总评

nop-code 模块在 2026-05-29 审查后经历了大规模修复和重构，又在 2026-05-31 审查后经历了 5 个 Phase 的系统性修复（安全注解、路径遍历防护、级联删除、唯一约束、分页删除、缓存守卫、测试有效性）。

**本次审查的核心发现是：前几轮反复报告的 3 个 P0 和多个 P1 仍然未修复，而最近的修复引入了少量新的结构性问题。**

本次审查的 2 个新发现是：

1. **AR-75 (P1): `resolveQualifiedNamesToIds` 全量加载所有继承和注解记录到内存**——此前审计关注了此方法的"语义破坏"（AR-01），但在 AR-01 修复后（保留 qualified name），方法仍然使用无分页的 `findAllByQuery`，大型索引下存在 OOME 风险。
2. **AR-76 (P2): `CodeCacheManager.rebuildSymbolTable` 超限时降级为空 `SymbolTable` 而非截断**——当符号数超过 `MAX_CACHE_SYMBOLS=100000` 时，返回全新的空 `SymbolTable`，所有依赖符号表的后续分析全部返回空结果且无错误提示。

其余均为已知未修复问题的确认和补充。

**最值得关注的 3 个方向**：

1. **bfsCollect 反向遍历仍然只走深度 1**（AR-63/AR-29）——这是代码从 `CodeIndexService` 移到 `CodeGraphService` 后仍存在的 P0 Bug。代码组织变了，Bug 没变。
2. **VFS 索引路径仍然跳过语义边和 resolveQualifiedNamesToIds**（AR-64/AR-47）——两条 `indexDirectory` 代码路径的数据完整性差距仍然是 P0。
3. **DeadCodeDetector 排除逻辑仍然检查 signature 中的 `@`**（AR-68/AR-48）——`isFrameworkEntryPoint`/`isDecoratedMethod`/`isOrmEntitySubclass` 三个方法始终返回 false，P0。

## 本次审查的盲区自评

- **GraalVM native image**：未验证 reflect-config.json 是否更新。
- **前端/GraphQL 运行时**：view.xml 和 xmeta 配置未覆盖。
- **大型代码库端到端验证**：未在 50 万+ 符号项目上运行完整管线。
- **图算法数学正确性**：Leiden/Louvain 实现未做数学验证。
- **并发压力测试**：所有并发问题基于代码推理，未实际验证。

---

## 已修复问题确认（自前次 2026-05-31 审查后新增）

以下问题在最近 5 个 Phase 修复中确认**已修复**：

| # | 问题 | 修复方式 |
|---|------|---------|
| AR-06/AR-28 | TypeScript `walkNodeForCalls` 是死代码 | `handleFunctionDeclaration`（:231）和 `handleMethodDefinition`（:259）现在调用 `walkNodeForCalls` ✅ |
| AR-21 | `parseGitDiff` 不设置工作目录 | `parseGitDiff` 现在接受 `workingDirectory` 参数（:121-129）并正确设置 `pb.directory()` ✅ |
| AR-53 | Flow→Membership 级联删除缺失 | ORM `NopCodeFlow→memberships` 添加了 `cascadeDelete="true"` ✅ |
| AR-15 | `sourceCode` 使用 VARCHAR(524288) | ORM 改为 `stdSqlType="CLOB"` ✅ |
| AR-16 | NopCodeCall/SemanticEdge 缺唯一约束 | 添加了 `uk_call_unique` 和 `uk_semantic_edge_unique` ✅ |
| AR-20 | JavaParser 线程不安全 | JavaFileAnalyzer 改为每次调用创建新 `JavaParser(parserConfiguration)` 实例 ✅ |
| CRG-3 | Python/TS 适配器未注册 | 构造函数现在注册 `PythonLanguageAdapter` 和 `TypeScriptLanguageAdapter`（:168-169）✅ |

---

## 第 1 轮发现

### [AR-75] resolveQualifiedNamesToIds 全量加载继承和注解记录——大型索引 OOME 风险

- **文件**: `nop-code-service/.../impl/CodeIndexService.java:793-819`
- **证据片段**:
  ```java
  private void resolveQualifiedNamesToIds(String indexId, SymbolTable symbolTable, IOrmSession session) {
      IEntityDao<NopCodeInheritance> inhDao = daoProvider.daoFor(NopCodeInheritance.class);
      QueryBean inhQuery = new QueryBean();
      inhQuery.addFilter(FilterBeans.eq("indexId", indexId));
      for (NopCodeInheritance inh : inhDao.findAllByQuery(inhQuery)) {  // ← 全量加载
          // ...
      }

      IEntityDao<NopCodeAnnotationUsage> annotDao = daoProvider.daoFor(NopCodeAnnotationUsage.class);
      QueryBean annotQuery = new QueryBean();
      annotQuery.addFilter(FilterBeans.eq("indexId", indexId));
      for (NopCodeAnnotationUsage annot : annotDao.findAllByQuery(annotQuery)) {  // ← 全量加载
          // ...
      }
  }
  ```
- **严重程度**: P1
- **现状**: `resolveQualifiedNamesToIds` 对 `NopCodeInheritance` 和 `NopCodeAnnotationUsage` 两个表执行无分页的 `findAllByQuery`。对包含 50 万符号的 Java 项目（每个类可能有多个继承和注解），两张表可能各有数十万行，全部加载到内存。虽然 `deleteIndex` 和 `CodeCacheManager` 已改为分页，但此方法仍然全量加载。
- **风险**: 大型项目索引时，`indexDirectory` 的 filesystem 路径在 `persistInSession` 中调用此方法可能导致 OOME。VFS 路径不受影响（因为跳过了此方法）。
- **建议**: 改为分页处理（与 `deleteEntitiesPaged` 模式一致）。
- **信心水平**: 很可能
- **发现来源视角**: 10x 规模运维者（注意到其他路径已分页但此方法未分页）

### [AR-76] CodeCacheManager 超限时降级为空 SymbolTable/CallGraph——静默丢失全部缓存数据

- **文件**: `nop-code-service/.../impl/CodeCacheManager.java:100-104, 130-134`
- **证据片段**:
  ```java
  // rebuildSymbolTable:
  if (totalLoaded >= MAX_CACHE_SYMBOLS) {
      LOG.warn("Symbol cache for index {} exceeded MAX_CACHE_SYMBOLS({}), degrading to empty cache",
              indexId, MAX_CACHE_SYMBOLS);
      return new SymbolTable();  // ← 返回空表，丢弃已加载的部分数据
  }

  // rebuildCallGraph:
  if (totalLoaded >= MAX_CACHE_EDGES) {
      LOG.warn("Call graph cache for index {} exceeded MAX_CACHE_EDGES({}), degrading to empty cache",
              indexId, MAX_CACHE_EDGES);
      return new CallGraph();  // ← 返回空图
  }
  ```
- **严重程度**: P2
- **现状**: 当符号或边超过硬编码限制（100000/500000）时，缓存返回**空**结构而非已加载的部分数据。后续所有依赖缓存的分析（社区检测、影响分析、关键节点、死代码检测）静默返回空结果。WARN 日志在生产环境中可能不可见。虽然这是 Phase 3 OOM 修复中引入的保护机制，但降级策略过于激进。
- **风险**: 大型项目的图分析功能静默失效。用户看到空结果但无错误提示。
- **建议**: 方案 A：保留已加载的部分数据并截断（不精确但比空好）。方案 B：改为抛出明确异常或返回错误结果。方案 C：将限制设为可配置，并在降级时在返回值中标记不完整。
- **信心水平**: 确定
- **发现来源视角**: 10x 规模运维者（审查 Phase 3 OOM 修复时发现降级策略问题）

---

## 已知未修复问题确认

以下问题在前次审查中已报告，经本次验证**仍然存在**：

| # | 问题 | 状态 | 本次补充 |
|---|------|------|---------|
| AR-02 | TSTree 原生对象未关闭 | **仍存在** | Python:47/61、TypeScript:59/79 仍只做 `tree = null` |
| AR-29/AR-63 | bfsCollect 反向深度始终为 1 | **仍存在** → AR-63 | `CodeGraphService.java:641` 仍访问 `edge.getTarget()` |
| AR-32/AR-66 | extractFileKey 返回包名 | **仍存在** → AR-66 | `FlowDetector.java:397-414` 逻辑未变 |
| AR-33 | Python 嵌套函数/类不可见 | **仍存在** | `PythonCodeFileAnalyzer.java:225` 仍不调用 `walkBlockChildren` |
| AR-41/AR-72 | getSymbolById 不检查 indexId | **仍存在** → AR-72 | `CodeQueryService.java:388` 仍只做 `getEntityById(symbolId)` |
| AR-47/AR-64 | VFS 路径跳过语义边+resolve | **仍存在** → AR-64 | `CodeIndexService.java:286-299` VFS 路径未调用 `persistInSession` |
| AR-48/AR-68 | DeadCodeDetector 排除逻辑死代码 | **仍存在** → AR-68 | `DeadCodeDetector.java:229-240` 仍检查 `signature.contains("@")` |
| AR-49/AR-67 | FlowDetector 硬编码 ".java" | **仍存在** → AR-67 | `FlowDetector.java:212, 224` 仍硬编码 `.java` |
| AR-50 | testGap 硬编码 1.0 | **仍存在** | `FlowDetector.java:338` |
| AR-51 | ORM 布尔列永远 NULL | **仍存在** | `saveFileResultInSession` 仍未设置 isSynchronized/isNative/isVolatile/isTransient |
| AR-52/AR-65/AR-70 | language 硬编码 "Java" | **仍存在** → AR-65 | `CodeIndexService.java:748, 829` |
| AR-57 | ENTRY_POINT_NAME_PATTERN 过宽 | **仍存在** | `FlowDetector.java:43-45` |
| AR-58 | EXTERNAL_PREFIXES 只有 Java | **仍存在** | `FlowDetector.java:29-37` |
| AR-59 | entityToCodeSymbol 三重复制 | **仍存在** | 3 个文件中仍有独立拷贝 |
| AR-60/AR-73 | deleteFileRecords 不删除 NopCodeUsage | **仍存在** → AR-60 | `CodeIndexService.java:1154-1175` 缺少 NopCodeUsage 和 NopCodeSemanticEdge |
| AR-61 | CodeCacheManager synchronized 阻塞跨索引 | **仍存在** | `CodeCacheManager.java:40,54,68` 仍为 synchronized 方法 |
| AR-62 | ensureSubServices 竞态条件 | **仍存在** | `CodeIndexService.java:110-116` 无同步 |
| AR-69 | Tarjan 递归 StackOverflow | **仍存在** | `CodeGraphService.java:694-718` 仍为递归 DFS |
| AR-71 | searchViaEngine 硬编码 HYBRID | **仍存在** | `CodeSearchService.java:65` |

---

## 严重程度分布表

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 3    | 反向 BFS 深度 1（AR-63）、VFS 数据丢失（AR-64）、排除逻辑死代码（AR-68） |
| P1      | 4    | 增量索引数据残留（AR-60/AR-73）、resolveQualifiedNamesToIds OOME（AR-75）、TSTree 泄漏（AR-02）+ Python 嵌套不可见（AR-33）+ language 硬编码（AR-65）+ fileSpread 失效（AR-66）+ 文件路径硬编码（AR-67） |
| P2      | 3    | 缓存降级为空（AR-76）、三重复制（AR-59）、缓存锁粒度（AR-61）、子服务竞态（AR-62）、数据泄漏（AR-72）、搜索类型忽略（AR-71） |
| P3      | 1    | 递归 DFS（AR-69）+ 入口点过宽（AR-57）+ 外部前缀 Java-only（AR-58）+ testGap 常量（AR-50） |

> 注：P1 行中 AR-60/AR-73、AR-02、AR-33、AR-65、AR-66、AR-67 各为独立 P1 发现，合并计数为 6 个 P1。上表按"主要类别"归纳。实际按独立发现编号计数：P0=3, P1=6, P2=6, P3=4。

## 精确分布

| 严重程度 | 独立发现数 | 编号 |
|---------|-----------|------|
| P0      | 3         | AR-63, AR-64, AR-68 |
| P1      | 6         | AR-02, AR-33, AR-60/AR-73, AR-65/AR-70, AR-66, AR-67, AR-75 |
| P2      | 6         | AR-59, AR-61, AR-62, AR-71, AR-72, AR-76 |
| P3      | 4         | AR-50, AR-57, AR-58, AR-69 |

*审查结束。如需深挖某个方向，可追加第 2 轮。*
