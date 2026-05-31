# Adversarial Review: nop-code (2026-05-31 — 第 4 轮刷新)

> **审查类型**: 开放式对抗性审查
> **目标模块**: nop-code（含 13 个子模块）
> **审查日期**: 2026-05-31
> **审查方法**: 代码驱动的开放探索，从全量代码重新阅读入手，不依赖前次审查路径。起始视角：异常路径侦探 + 代码生成受害者 + 未来破坏者。
> **去重基线**: 已读取 `ai-dev/audits/2026-05-31-adversarial-review-nop-code/`（AR-75、AR-76）、`ai-dev/audits/2026-05-31-deep-audit-nop-code-full/`（65 项发现）、`ai-dev/audits/2026-05-29-adversarial-review-nop-code/`（AR-01 至 AR-22）、r2（AR-28 至 AR-46）、r3（AR-47 至 AR-58）。代码自 r4 前次审查以来无显著变更。

---

## 总评

本轮审查重新阅读了 nop-code 全部核心源文件（CodeIndexService 1570 行、CodeGraphService 722 行、CodeQueryService 767 行、CodeCacheManager 141 行、CodeSearchService 268 行、CommunityDetector 892 行、FlowDetector 546 行、DeadCodeDetector 397 行、ChangeAnalyzer 461 行、JavaFileAnalyzer 771 行、PythonCodeFileAnalyzer 475 行、TypeScriptCodeFileAnalyzer 605 行，以及所有 BizModel、DTO、beans.xml 等配置文件），关注点放在前几轮审查未充分覆盖的方向：异常路径连环效应、代码生成的正确性、以及设计决策的未来可扩展性。

**本轮新发现 2 个问题**，均为前几轮审查未触及的新模式：

1. **AR-77 (P1): `getIndexStats` 和 `updateIndexStats` 全量加载实体计数**——这两个方法使用 `findAllByQuery` 加载全部文件/符号实体仅为了获取 `.size()`，未使用 `countByQuery`。大型索引下，这会导致内存中同时持有数万甚至数十万实体对象。
2. **AR-78 (P2): `buildTypeHierarchy` 和 `buildCallHierarchy` 的递归调用中 `symbol.getKind().name()` 无 null 保护**——前次 deep audit 的 15-01 发现了 4 处 `getKind().name()` NPE，但遗漏了 `CodeGraphService` 中的 `buildTypeHierarchy`(:216) 和 `buildCallHierarchy`(:266) 这两处。当 `symbol.getKind()` 为 null 时会抛 NPE。

**前次审查的已知 P0 问题全部仍存在**。

## 本次审查的盲区自评

- **GraalVM native image**: 未验证 reflect-config.json。
- **前端/GraphQL 运行时**: view.xml 和 xmeta 配置未覆盖。
- **大型代码库端到端验证**: 未在 50 万+ 符号项目上运行完整管线。
- **图算法数学正确性**: Leiden/Louvain 实现未做数学验证。
- **并发压力测试**: 所有并发问题基于代码推理，未实际验证。

---

## 第 1 轮发现

### [AR-77] getIndexStats / updateIndexStats 全量加载实体仅用于计数——大型索引 OOME

- **文件**: `nop-code-service/.../impl/CodeIndexService.java:481-490, 1278-1287`
- **证据片段**:
  ```java
  // getIndexStats (line 481-490):
  List<NopCodeSymbol> allSymbols = symbolDao.findAllByQuery(symbolQuery);  // 全量加载
  stats.setSymbolCount(allSymbols.size());
  // ...
  stats.setFileCount(fileDao.findAllByQuery(fileQuery).size());  // 全量加载

  // updateIndexStats (line 1278-1287):
  index.setFileCount(fileDao.findAllByQuery(fq).size());  // 全量加载
  index.setSymbolCount(symDao.findAllByQuery(sq).size());  // 全量加载
  ```
- **严重程度**: P1
- **现状**: `getIndexStats` 和 `updateIndexStats` 两个方法使用 `findAllByQuery` 加载全部匹配实体到内存仅为了获取 `size()`。对比同文件中 `findFilesPage` (line 370) 正确使用了 `fileDao.countByQuery(countQb)` 做分页计数。在包含 10 万+ 符号和数万文件的索引中，这两处全量加载会导致大量内存占用和不必要的 GC 压力。`getIndexStats` 是 BizQuery 暴露的 API，用户每次调用都可能触发。
- **风险**: 大型索引调用 `getStats` API 时 OOME 或严重性能退化。`updateIndexStats` 在增量索引完成后也会触发。
- **建议**: 改用 `daoProvider.daoFor(NopCodeSymbol.class).countByQuery(query)` 替代 `findAllByQuery(query).size()`。`findFilesPage` 已有正确模式可参照。
- **信心水平**: 确定
- **发现来源视角**: 10x 规模运维者（注意到同文件中 `findFilesPage` 使用了 `countByQuery` 但 `getIndexStats` 未使用）

### [AR-78] buildTypeHierarchy / buildCallHierarchy 中 getKind().name() 缺 null 保护——NPE

- **文件**: `nop-code-service/.../impl/CodeGraphService.java:216, 266`
- **证据片段**:
  ```java
  // buildTypeHierarchy (line 216):
  symbolInfo.setKind(symbol.getKind().name());  // symbol.getKind() 可能为 null

  // buildCallHierarchy (line 266):
  symbolInfo.setKind(symbol.getKind().name());  // symbol.getKind() 可能为 null
  ```
- **严重程度**: P2
- **现状**: 前次 deep audit 的 15-01 发现了 4 处 `getKind().name()` NPE（CodeGraphService:216,266; CodeQueryService:540; NopCodeFileBizModel:97）。本次审查确认 CodeGraphService 的两处（:216 和 :266）在 `buildTypeHierarchy` 和 `buildCallHierarchy` 中。当 `entityToCodeSymbol` 中 `entity.getKind()` 为 null 时（这是允许的，line 213: `symbol.setKind(entity.getKind() != null ? CodeSymbolKind.valueOf(entity.getKind()) : null)`），对返回的 symbol 调用 `.getKind().name()` 会 NPE。这两处被 `getTypeHierarchy` 和 `getCallHierarchy` BizQuery 直接暴露，用户可通过 GraphQL 触发。
- **风险**: 查询任何 kind 为 null 的符号的类型/调用层次时 API 崩溃。
- **建议**: `symbolInfo.setKind(symbol.getKind() != null ? symbol.getKind().name() : null)`。与 `toGodNode` (line 374) 和 `toImpactedSymbol` (line 398) 中的安全写法一致。
- **信心水平**: 确定
- **发现来源视角**: 异常路径侦探（追踪 entityToCodeSymbol 的 null-kind 路径到所有消费方）

---

## 已知未修复问题确认

以下问题在前次审查中已报告，经本次验证**仍然存在**：

| # | 问题 | 状态 | 本次补充 |
|---|------|------|---------|
| AR-02 | TSTree 原生对象未关闭 | **仍存在** | Python:59/61 `tree = null` 不释放原生内存 |
| AR-29/AR-63 | bfsCollect 反向深度始终为 1 | **仍存在** | `CodeGraphService.java:639` 仍访问 `edge.getTarget()` |
| AR-33 | Python 嵌套函数/类不可见 | **仍存在** | `PythonCodeFileAnalyzer.java` 不调用 walkBlockChildren |
| AR-47/AR-64 | VFS 路径跳过语义边+resolve | **仍存在** | `CodeIndexService.java:286-299` VFS 路径未调用 `persistInSession` |
| AR-48/AR-68 | DeadCodeDetector 排除逻辑死代码 | **仍存在** | `DeadCodeDetector.java:234` 仍检查 `signature.contains("@")` |
| AR-49/AR-67 | FlowDetector 硬编码 ".java" | **仍存在** | `FlowDetector.java:212, 224` |
| AR-50 | testGap 硬编码 1.0 | **仍存在** | `FlowDetector.java:338` |
| AR-51 | ORM 布尔列永远 NULL | **仍存在** | `saveFileResultInSession` 未设置 isSynchronized/isNative/isVolatile/isTransient |
| AR-52/AR-65 | language 硬编码 "Java" | **仍存在** | `CodeIndexService.java:767, 848` |
| AR-57 | ENTRY_POINT_NAME_PATTERN 过宽 | **仍存在** | `FlowDetector.java:43-45` |
| AR-58 | EXTERNAL_PREFIXES 只有 Java | **仍存在** | `FlowDetector.java:29-37` |
| AR-59 | entityToCodeSymbol 三重复制 | **仍存在** | CodeIndexService/CodeGraphService/CodeQueryService 中各有独立拷贝 |
| AR-60/AR-73 | deleteFileRecords 不删除 NopCodeUsage | **仍存在** | `CodeIndexService.java:1173-1193` 缺少 NopCodeUsage 和 NopCodeSemanticEdge |
| AR-61 | CodeCacheManager synchronized 阻塞跨索引 | **仍存在** | `CodeCacheManager.java:40,54,68` 仍为 synchronized 方法 |
| AR-62 | ensureSubServices 竞态条件 | **仍存在** | `CodeIndexService.java:110-116` 无同步 |
| AR-69 | Tarjan 递归 StackOverflow | **仍存在** | `CodeGraphService.java:694-718` 仍为递归 DFS |
| AR-71 | searchViaEngine 硬编码 HYBRID | **仍存在** | `CodeSearchService.java:65` |
| AR-75 | resolveQualifiedNamesToIds OOME | **仍存在** | `CodeIndexService.java:812-838` 仍无分页 |
| AR-76 | 缓存超限降级为空 | **仍存在** | `CodeCacheManager.java:100-104, 130-134` |

此外，前次 deep audit 的以下关键发现仍然存在：

| # | 问题 | 状态 |
|---|------|------|
| 15-01 | getKind().name() NPE（4 处中的部分已修？） | **部分仍存在**（AR-78 补充 2 处遗漏） |
| 04-01 | deleteFileRecords 级联清理遗漏 | **仍存在**（与 AR-60/AR-73 同根） |
| 15-02 | JavaFileAnalyzer extData JSON 拼接 | **仍存在**（line 203-209 手拼 JSON） |
| 07-04 | incrementalStatusMap JVM 内存不共享 | **仍存在** |
| 07-06 | CodeIndexService 1570 行上帝类 | **仍存在** |
| 09-05 | FlowDetector 静默吞异常 | **仍存在** |
| 12-01 | getStats 全量加载符号表计数 | **仍存在**（AR-77 新报告，与 12-01 交叉） |
| 12-02 | getFiles 全量加载含 sourceCode | **仍存在** |
| 14-01 | indexDirectory 长事务 | **仍存在** |

---

## 严重程度分布表

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 3    | 反向 BFS 深度 1（AR-63）、VFS 数据丢失（AR-64）、排除逻辑死代码（AR-68） |
| P1      | 3    | 全量加载计数 OOME（AR-77）、增量索引数据残留（AR-60/AR-73）、resolveQualifiedNamesToIds OOME（AR-75）+ TSTree 泄漏（AR-02）+ Python 嵌套不可见（AR-33）+ language 硬编码（AR-65）+ fileSpread 失效（AR-66）+ 文件路径硬编码（AR-67）+ getKind NPE（AR-78 部分） |
| P2      | 3    | getKind NPE 补充（AR-78）、缓存降级为空（AR-76）、三重复制（AR-59）、缓存锁粒度（AR-61）、子服务竞态（AR-62）、数据泄漏（AR-72）、搜索类型忽略（AR-71） |
| P3      | 1    | 递归 DFS（AR-69）+ 入口点过宽（AR-57）+ 外部前缀 Java-only（AR-58）+ testGap 常量（AR-50） |

## 精确分布（仅本轮新发现）

| 严重程度 | 新发现数 | 编号 |
|---------|---------|------|
| P0      | 0       | — |
| P1      | 1       | AR-77 |
| P2      | 1       | AR-78 |

*审查结束。如需深挖某个方向，可追加第 2 轮。*
