# Adversarial Review: nop-code (2026-06-01 r6)

> **审查类型**: 开放式对抗性审查
> **目标模块**: nop-code（含 13 个子模块）
> **审查日期**: 2026-06-01
> **审查方法**: 代码驱动的开放探索，从索引管线性能和并发安全入手。起始视角：10x 规模运维者 + 异常路径侦探 + 事务边界追踪者。
> **去重基线**: 已读取 `ai-dev/audits/2026-06-01-adversarial-review-nop-code/`（AR-77 至 AR-81）、`ai-dev/audits/2026-06-01-adversarial-review-nop-code-r5/`（AR-82 至 AR-86）、`ai-dev/audits/2026-05-31-adversarial-review-nop-code-r4/`（AR-77-r4 至 AR-78-r4）、`ai-dev/audits/2026-05-31-adversarial-review-nop-code/`（AR-75 至 AR-76）、`ai-dev/audits/2026-05-29-adversarial-review-nop-code/`（AR-01 至 AR-22）。

---

## 总评

nop-code 模块自 r5 审查以来无代码变更。本轮审查重新阅读了索引管线的完整路径（`persistInSession` → `saveFileResultInSession` → `getProjectFilePaths`），发现了一个此前所有审查轮次均未触及的性能缺陷。

**本次审查发现 1 个新问题（AR-87），是索引管线中一个 O(N²) 级别的性能灾难**：

`saveFileResultInSession` 对每个含 import 的文件调用 `getProjectFilePaths`，后者每次全量加载该索引的所有文件实体（含 `sourceCode`）到内存。由于 `cachedProjectFilePaths` 是方法级局部变量，无法跨文件复用，导致 5000 个文件的索引操作产生约 1250 万次实体加载。

此外确认了 `updateIndexStats` 的 `findAllByQuery().size()` 问题仍然存在——`getIndexStats` 已修复为 `countByQuery()`，但同一文件中的 `updateIndexStats` 未修复。

**最值得关注的 1 个方向**：

1. **`getProjectFilePaths` O(N²) 全量加载**（AR-87）——此前所有审查聚焦于查询路径的全量加载（AR-75/AR-80/AR-83/AR-86），但忽略了索引管线中更严重的同类问题

## 本次审查的盲区自评

- **FlowDetector / DeadCodeDetector / ChangeAnalyzer 内部实现**：本轮聚焦索引管线性能，未重新审查图算法和流检测
- **前端 view.xml 和 xmeta 配置**：未覆盖 GraphQL schema 与 BizModel 方法的字段级对齐
- **大型代码库端到端验证**：所有性能问题基于代码推理，未实际压测
- **GraalVM native image**：未验证 reflect-config.json 完整性
- **图算法数学正确性**：Leiden/Louvain 实现未做数学层面验证

---

## 已修复问题确认（自 r5 审查后）

本次审查确认以下此前报告的问题已修复：

| # | 问题 | 修复方式 |
|---|------|---------|
| AR-80 | getIndexStats 全量加载 OOME | 已改为 `countByQuery()` + `selectFieldsByQuery` 按 kind 分组（:432-451）✅ |
| AR-82 | detectFlows 缺少 @Auth 保护 | 已添加 `@Auth(roles = "admin")`（:201）✅ |
| AR-77 | indexLocks 竞态条件 | finally 块不再移除锁，仅执行 `lock.unlock()`（:251）✅ |
| AR-77-r4 | getIndexStats / updateIndexStats 全量加载计数 | **部分修复**：`getIndexStats` 已修复 ✅，但 `updateIndexStats` 仍使用 `findAllByQuery().size()` ❌ |

---

## 第 1 轮发现

### [AR-87] getProjectFilePaths O(N²) 全量加载——索引管线性能灾难

- **文件**: `nop-code-service/.../impl/CodeIndexService.java:822-1080, 1084-1097`
- **证据片段**:
  ```java
  // :822-824 — saveFileResultInSession 每次调用重置缓存
  private void saveFileResultInSession(String indexId, CodeFileAnalysisResult file,
                                       IOrmSession session) {
      Set<String> cachedProjectFilePaths = null;  // ← 局部变量，不跨文件复用
      // ... 保存 file、symbols、calls、inheritances、annotations、usages ...

  // :1047-1053 — 每个含 import 的文件触发全量加载
      if (file.getImports() != null && !file.getImports().isEmpty() && file.getLanguage() != null) {
          IImportResolver resolver = importResolvers.get(file.getLanguage().name());
          if (resolver != null) {
              Set<String> projectFiles = cachedProjectFilePaths != null
                  ? cachedProjectFilePaths : getProjectFilePaths(indexId);  // ← 每次都调用
  ```
  ```java
  // :1084-1097 — getProjectFilePaths 加载所有文件（含 sourceCode！）
  private Set<String> getProjectFilePaths(String indexId) {
      IEntityDao<NopCodeFile> fileDao = daoProvider.daoFor(NopCodeFile.class);
      QueryBean q = new QueryBean();
      q.addFilter(FilterBeans.eq("indexId", indexId));
      List<NopCodeFile> files = fileDao.findAllByQuery(q);  // ← 无 LIMIT，加载全部列（含 sourceCode）
      Set<String> paths = new HashSet<>();
      for (NopCodeFile f : files) {
          if (f.getFilePath() != null) {
              paths.add(f.getFilePath());
          }
      }
      return paths;
  }
  ```
  ```java
  // :733-736 — persistInSession 对每个文件调用 saveFileResultInSession
  for (CodeFileAnalysisResult file : result.getFileResults()) {
      saveFileResultInSession(indexId, file, session);  // ← 每次都重新创建 cachedProjectFilePaths = null
      queue.add(file);
  }
  ```
- **严重程度**: P1
- **现状**: `persistInSession` 对每个文件调用 `saveFileResultInSession`。每个文件的 `saveFileResultInSession` 以 `cachedProjectFilePaths = null` 开始（局部变量）。当文件含 import 时（几乎所有 Java/Python/TypeScript 文件都含 import），调用 `getProjectFilePaths` 全量加载该索引的所有 `NopCodeFile` 实体（包含 `sourceCode` 列）到内存，仅为了提取 `filePath` 集合。

  **量化影响**：对一个 5000 文件的项目：
  - 约 4000+ 文件含 import（Java/Python/TypeScript 几乎所有文件都有 import）
  - 每个文件触发 1 次 `getProjectFilePaths` → 加载全部文件实体（含 sourceCode）
  - 总实体加载次数 ≈ 4000 × 5000 = **2000 万次**
  - 总内存开销 ≈ 4000 × (5000 文件 × 平均 5KB sourceCode) ≈ **100 GB** 的对象创建（被 GC 回收但造成严重 GC 压力）

  **与已报告问题的区别**：前几轮审查报告了多个查询路径的全量加载（AR-75 resolveQualifiedNamesToIds、AR-80 getIndexStats、AR-83 getModuleDigest N+1、AR-86 findImplementations），但都是在**查询/API 调用**路径中。AR-87 发生在**索引管线**（`indexDirectory`）中，是写入路径的性能问题，影响更基础——索引操作本身变得极慢。
- **风险**: (1) 大型项目（5000+ 文件）的索引操作耗时从分钟级膨胀到小时级。(2) 频繁的大对象创建和 GC 导致 JVM 长时间 STW。(3) 可能 OOME。索引是所有后续分析的前提，此问题使整个平台对大型项目几乎不可用。
- **建议**: 方案 A（推荐）：将 `getProjectFilePaths` 的结果提升到 `persistInSession` 层面缓存，作为参数传入 `saveFileResultInSession`。方案 B：使用 `selectFields` 仅查询 `filePath` 列而非加载全部实体。方案 C：在 `ProjectAnalysisResult` 中维护文件路径集合（分析阶段已遍历所有文件），无需回查数据库。方案 A + C 组合效果最佳。
  ```java
  // 方案 A：提升缓存
  Set<String> projectFilePaths = getProjectFilePaths(indexId);
  for (CodeFileAnalysisResult file : result.getFileResults()) {
      saveFileResultInSession(indexId, file, session, projectFilePaths);
  }
  ```
- **信心水平**: 确定（代码逻辑清晰，`cachedProjectFilePaths` 是方法局部变量，不存在跨调用复用的可能）
- **发现来源视角**: 10x 规模运维者（追踪索引管线的完整路径时，注意到 `cachedProjectFilePaths` 的生命周期限制）

---

## 已知未修复问题确认

以下问题在前次审查中已报告，经本次验证**仍然存在**：

| # | 问题 | 状态 | 本次补充 |
|---|------|------|---------|
| AR-78 | bfsCollect 反向深度始终为 1 | **仍存在** | `CodeGraphService.java:609` 仍使用 `edge.getTarget()` |
| AR-79 | CommunityDetector.runWithTimeout 线程泄漏 | **仍存在** | `:741-749` |
| AR-81 | getTypeHierarchy 全量加载继承记录 | **仍存在** | `:186-192` |
| AR-75 | resolveQualifiedNamesToIds 全量加载 | **仍存在** | `:767, :780` 仍为 `findAllByQuery` 无分页 |
| AR-76 | CodeCacheManager 超限降级为空 | **仍存在** | `:100-104, 130-134` |
| AR-65 | language 硬编码 "Java" | **仍存在** | `:718` ensureIndexEntity 中 `indexEntity.setLanguage("Java")`，`:800` 同 |
| AR-69 | Tarjan 递归 StackOverflow | **仍存在** | `:662-691` 仍为递归 DFS |
| AR-62 | ensureSubServices 竞态条件 | **仍存在** | `:111-116` 无同步 |
| AR-59 | entityToCodeSymbol 三重复制 | **仍存在** | CodeSymbolConverter 已提取为工具类（2 处使用），但 CodeGraphService 中仍有独立的 `entityToInheritance` |
| AR-84 | isPotentiallyDynamic 子串匹配过宽 | **仍存在** | |
| AR-85 | 测试文件检测仅覆盖 Java | **仍存在** | `:1024-1025` |
| AR-86 | findImplementations 全量加载 | **仍存在** | |
| AR-83 | getModuleDigest N+1 | **仍存在** | `:238-265` |
| AR-77-r4 | updateIndexStats 全量加载计数 | **仍存在** | `:1236, :1241` 仍使用 `findAllByQuery().size()` |

---

## 严重程度分布表

| 严重程度 | 独立发现数 | 编号 |
|---------|-----------|------|
| P0      | 1         | AR-78（已知未修复，本轮确认） |
| P1      | 1         | AR-87 |
| P2      | 0         | — |
| P3      | 0         | — |

> 本轮新增独立发现 1 个（AR-87），确认已修复 3 个（AR-77、AR-80、AR-82），部分修复 1 个（AR-77-r4/getIndexStats 已修但 updateIndexStats 未修），已知未修复确认 13 个。

*审查结束。如需深挖某个方向，可追加第 2 轮。*
