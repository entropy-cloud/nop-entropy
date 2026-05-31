# Adversarial Review: nop-code (2026-06-01 r5)

> **审查类型**: 开放式对抗性审查
> **目标模块**: nop-code（含 13 个子模块）
> **审查日期**: 2026-06-01
> **审查方法**: 代码驱动的开放探索，从 GraphQL 安全面（BizModel 注解一致性）入手，扩展到查询性能和跨语言一致性。起始视角：IoC 侦探 + 未来破坏者 + 10x 规模运维者。
> **去重基线**: 已读取 `ai-dev/audits/2026-05-31-adversarial-review-nop-code-r4/`（AR-77 至 AR-81）、`ai-dev/audits/2026-06-01-adversarial-review-nop-code/`（AR-77 至 AR-81 重新确认）、`ai-dev/audits/2026-05-29-adversarial-review-nop-code/`（AR-01 至 AR-22）、`ai-dev/audits/2026-05-29-deep-audit-nop-code/`（维度 01-21）。

---

## 总评

nop-code 模块自 2026-06-01 首次审查以来无代码变更。本次审查从 BizModel 安全注解一致性切入，发现 `detectFlows` 是唯一一个缺少 `@Auth` 保护的 `@BizMutation` 方法（AR-82），这是一个跨所有审查轮次都未被触及的安全面问题。随后追踪查询性能，发现 `getModuleDigest` 的 N+1 查询模式（AR-83）和 `findImplementations` 的全量加载（AR-86）。进一步深入 DeadCodeDetector 的精确度，发现 `isPotentiallyDynamic` 的子串匹配过于宽泛（AR-84），以及测试文件检测仅覆盖 Java（AR-85）。

**最值得关注的 3 个方向**：

1. **`detectFlows` 缺少 `@Auth` 保护**（AR-82）——唯一一个未受保护的写操作入口，允许任何已认证用户触发高开销计算
2. **`getModuleDigest` N+1 查询**（AR-83）——每文件单独查询符号，数千文件时性能灾难
3. **`DeadCodeDetector.isPotentiallyDynamic` 过于宽泛**（AR-84）——"Bean"/"Service"等子串匹配导致 Spring 项目死代码检测近乎失效

## 本次审查的盲区自评

- **增量索引的正确性端到端验证**：仅代码审查了 `triggerIncrementalIndex` 路径，未实际执行增量索引并验证数据一致性
- **搜索引擎集成**：未验证 `ISearchEngine` 实现的正确性和回退逻辑在真实环境中的行为
- **GraphExporter 的各格式输出**：未检查 DOT/JSON/GraphML 格式输出的正确性
- **ChangeAnalyzer（Git diff 集成）**：未深入审查 `ChangeAnalyzer` 的实现
- **CodeSearchService 的 filterByLanguage**：该方法同样全量加载文件列表，与 AR-80/AR-83 同模式但未独立报告

---

## 第 1 轮发现

### [AR-82] detectFlows 缺少 @Auth 保护——唯一未受保护的 BizMutation

- **文件**: `nop-code-service/.../entity/NopCodeIndexBizModel.java:190-193`
- **证据片段**:
  ```java
  @BizMutation                              // :190
  public List<ExecutionFlow> detectFlows(@Name("indexId") String indexId) {  // :191
      return codeIndexService.detectFlows(indexId);                          // :192
  }
  ```
  对比其他所有 `@BizMutation` 方法：
  ```java
  @BizMutation                       // :43
  @Auth(roles = "admin")             // :44
  public String triggerFullIndex(...)
  
  @BizMutation                       // :62
  @Auth(roles = "admin")             // :63
  public int triggerIncrementalIndex(...)
  
  @BizMutation                       // :88
  @Auth(roles = "admin")             // :89
  public int indexDirectory(...)
  ```
- **严重程度**: P1
- **现状**: `detectFlows` 是 NopCodeIndexBizModel 中唯一的 `@BizMutation` 方法没有 `@Auth` 注解。该方法触发的操作极其昂贵：
  1. `getOrRebuildSymbolTable`：全量加载所有符号到内存
  2. `getOrRebuildCallGraph`：全量加载所有调用关系到内存
  3. `EntryPointScorer.scoreEntryPoints`：遍历所有方法计算分数
  4. 对每个入口点做 BFS 遍历
  5. 将所有流持久化到数据库（先删后插）
  6. 写入 flowCache
  
  任何已认证用户（非 admin）都可以通过 GraphQL mutation 调用此方法。
- **风险**: (1) 非 admin 用户可触发高开销计算，可能导致 DoS。(2) 该方法先删除再插入所有 flows，并发调用可导致数据不一致。(3) 与 `indexDirectory`（有 `@Auth`）使用相同的 `indexLocks` 机制不同，`detectFlows` 不受锁保护，可以与正在进行的索引操作并发执行。
- **建议**: 添加 `@Auth(roles = "admin")` 注解，与其他所有 `@BizMutation` 保持一致。
- **信心水平**: 确定
- **发现来源视角**: IoC 侦探（检查 BizModel 注解一致性时发现）

### [AR-83] getModuleDigest N+1 查询——每文件单独查询符号

- **文件**: `nop-code-service/.../impl/CodeQueryService.java:268-295`
- **证据片段**:
  ```java
  // :259 — 一次性加载最多 MAX_QUERY_RESULTS(10000) 个文件
  List<NopCodeFile> files = fileDao.findAllByQuery(fileQuery);
  
  IEntityDao<NopCodeSymbol> symbolDao = daoProvider.daoFor(NopCodeSymbol.class);
  
  List<ModuleDigestDTO> result = new ArrayList<>();
  for (NopCodeFile file : files) {               // :268 — 对每个文件
      String fileId = file.getId();
      
      QueryBean symQuery = new QueryBean();      // :271 — 发起一个新查询
      symQuery.addFilter(FilterBeans.eq("indexId", indexId));
      symQuery.addFilter(FilterBeans.eq("fileId", fileId));
      if (!includePrivate) {
          symQuery.addFilter(FilterBeans.ne("accessModifier", "PRIVATE"));
      }
      
      // :279 — 每文件单独的 findAllByQuery 调用
      for (NopCodeSymbol sym : symbolDao.findAllByQuery(symQuery)) {
          ...
      }
  }
  ```
- **严重程度**: P1
- **现状**: `getModuleDigest` 对每个文件单独查询符号，形成经典的 N+1 查询模式。对于有 5000 个文件的项目，该方法会执行 1（文件查询）+ 5000（符号查询）= 5001 次 DB 查询。即使每次查询只需几毫秒，总耗时也在秒级。这是一个 GraphQL 可直接调用的 `@BizQuery` 方法（通过 `NopCodeIndexBizModel` 未直接暴露，但通过 `ICodeIndexService` 可达），UI 展示模块摘要时会频繁调用。
- **风险**: 大型项目的模块摘要查询极慢，严重影响 UI 响应时间。比 AR-80 的 `getIndexStats` 更危险，因为：(1) N+1 模式比单次全量加载有更多的网络往返；(2) 每次查询都有 ORM 实体化开销。
- **建议**: 批量加载所有匹配条件的符号，然后在内存中按 fileId 分组。改为：
  ```java
  // 一次性加载所有符号
  QueryBean allSymQuery = new QueryBean();
  allSymQuery.addFilter(FilterBeans.eq("indexId", indexId));
  if (!includePrivate) {
      allSymQuery.addFilter(FilterBeans.ne("accessModifier", "PRIVATE"));
  }
  Map<String, List<NopCodeSymbol>> symbolsByFile = symbolDao.findAllByQuery(allSymQuery)
      .stream().collect(Collectors.groupingBy(NopCodeSymbol::getFileId));
  // 然后按文件分组构建 DTO
  ```
- **信心水平**: 确定
- **发现来源视角**: 10x 规模运维者（追踪频繁调用路径时发现）

### [AR-84] DeadCodeDetector.isPotentiallyDynamic 子串匹配过于宽泛

- **文件**: `nop-code-flow/.../DeadCodeDetector.java:337-366`
- **证据片段**:
  ```java
  private boolean isPotentiallyDynamic(CodeSymbol symbol) {
      String signature = symbol.getSignature();
      if (signature != null) {
          if (signature.contains("Bean")            // 匹配 "getBeanFactory", "newBean..."
              || signature.contains("Component")     // 匹配 "addComponent", "ComponentA"
              || signature.contains("Service")       // 匹配 "service()", "getServiceName"
              || signature.contains("Repository")    // 匹配 "createRepository", "repository"
              || signature.contains("Controller")    // 匹配 "getController", "controller"
              || signature.contains("Inject")        // 匹配 "getInjectionPoint", "inject"
              || signature.contains("Autowired")     // 匹配 "autowired"
              || signature.contains("Resource"))     // 匹配 "getResource", "resources"
              return true;
      }
      
      String qName = symbol.getQualifiedName();
      if (qName != null) {
          String lower = qName.toLowerCase();
          if (lower.contains("listener")            // 匹配任何含 "listener" 的类
              || lower.contains("handler")           // 匹配任何含 "handler" 的类
              || lower.contains("callback")          // 匹配 "CallbackHelper"
              || lower.contains("hook")              // 匹配 "WebhookService"
              || lower.contains("observer")          // 匹配 "ObserverPattern"
              || lower.contains("subscriber"))       // 匹配 "EventSubscriber"
              return true;
      }
      return false;
  }
  ```
- **严重程度**: P2
- **现状**: `isPotentiallyDynamic` 使用纯子串匹配来检测"可能被动态调用"的方法。在 Spring 项目中：
  - `getBeanFactory()` 匹配 "Bean" → 标记为 potentially dynamic
  - `addServiceEndpoint()` 匹配 "Service" → 标记为 potentially dynamic
  - `getResourceAsStream()` 匹配 "Resource" → 标记为 potentially dynamic
  - `getController()` 匹配 "Controller" → 标记为 potentially dynamic
  
  这些方法被标记后，`computeConfidence` 返回 0.6，被归类为 "suspicious" 而非 "dead"。对于一个典型的 Spring 项目，大部分方法名中都可能包含这些关键词，导致死代码检测器产生大量假阴性。
  
  更严重的是，`signature` 字段存储的是方法签名（如 `public void handleRequest(Request req)`），而非注解信息。检查 `signature.contains("Bean")` 实际上是在方法签名的文本中搜索，而非检查方法是否被 `@Bean` 注解。
- **风险**: Spring 项目中死代码检测器的实用性大幅降低。大量真正死代码的方法因为签名或名称中包含常见词汇而被标记为 "suspicious"（confidence=0.6），而非 "dead"（confidence>=0.9）。
- **建议**: (1) 使用注解信息而非签名文本匹配。利用 `NopCodeAnnotationUsage` 表中已存储的注解数据来判断方法是否被框架注解标记。(2) 对名称匹配使用更精确的模式（如 `@Bean` 注解检测，而非 `signature.contains("Bean")`）。(3) 至少使用 word boundary 匹配。
- **信心水平**: 很可能
- **发现来源视角**: 未来破坏者（评估死代码检测器对 Spring 项目的实用性时发现）

### [AR-85] saveFileResultInSession 测试文件检测仅覆盖 Java

- **文件**: `nop-code-service/.../impl/CodeIndexService.java:1072-1094`
- **证据片段**:
  ```java
  if (file.getSymbols() != null && file.getFilePath() != null) {
      boolean isTestFile = file.getFilePath().contains("Test.java")    // :1073
              || file.getFilePath().contains("/test/");                // :1074
      if (isTestFile) {
          for (CodeSymbol sym : file.getSymbols()) {
              ...
              String testUsageKind = "TESTED_BY";
              ...
          }
      }
  }
  ```
- **严重程度**: P2
- **现状**: 测试文件检测仅检查 `Test.java` 和 `/test/` 路径。缺少以下常见测试模式：
  - Python: `test_*.py`, `*_test.py`, `/tests/`, `/test/`
  - TypeScript: `*.test.ts`, `*.spec.ts`, `__tests__/`
  - Kotlin: `*Test.kt`, `*Tests.kt`
  
  虽然 `/test/` 子串会匹配部分 Python 路径（如 `tests/test_foo.py`），但不会匹配 `src/tests/` 或 `__tests__/`。而 `Test.java` 子串完全排除了所有非 Java 测试文件。
  
  结果：`TESTED_BY` usage 记录不会为 Python/TypeScript 测试文件生成，影响 DeadCodeDetector 和知识图谱分析的准确性（测试覆盖分析对这些语言完全失效）。
- **风险**: 非 Java 项目的测试覆盖分析完全失效。DeadCodeDetector 的排除逻辑（`isTestSymbol`）在 DB 层面是正确的（因为它使用 `testPathPatterns` 包含多语言模式），但 `TESTED_BY` 记录的缺失意味着影响分析无法追踪"哪些符号被测试覆盖"。
- **建议**: 使用 `LanguageAdapterRegistry` 获取当前语言的测试文件模式，或提取为共享的 `ITestFileDetector` 接口。最简方案：添加 `"Test.kt"`, `"_test.py"`, `"test_"`, `".test.ts"`, `".spec.ts"`, `"/tests/"`, `"/__tests__/"` 等模式。
- **信心水平**: 确定
- **发现来源视角**: 未来破坏者（评估多语言支持完整性时发现）

### [AR-86] findImplementations 全量加载所有符号构建 idToQn 映射

- **文件**: `nop-code-service/.../impl/CodeQueryService.java:712-721`
- **证据片段**:
  ```java
  IEntityDao<NopCodeSymbol> symDaoForInh = daoProvider.daoFor(NopCodeSymbol.class);
  Map<String, String> idToQn = new HashMap<>();
  QueryBean allSymForInhQuery = new QueryBean();
  allSymForInhQuery.addFilter(FilterBeans.eq("indexId", indexId));
  allSymForInhQuery.setLimit(CodeIndexService.MAX_QUERY_RESULTS);  // 10000
  for (NopCodeSymbol sym : symDaoForInh.findAllByQuery(allSymForInhQuery)) {  // :717
      if (sym.getQualifiedName() != null) {
          idToQn.put(sym.getId(), sym.getQualifiedName());
      }
  }
  ```
- **严重程度**: P2
- **现状**: `findImplementations` 加载最多 10000 个符号到内存仅为了构建 `id→qualifiedName` 映射。与 AR-75 的 `resolveQualifiedNamesToIds` 同一模式，但发生在不同的查询路径中。该方法是 GraphQL `@BizQuery` `findImplementations` 的底层实现，用户每次查询接口实现都会触发。
- **风险**: 大型项目的实现查询可能导致 OOME 或极慢。且 `MAX_QUERY_RESULTS` 限制为 10000，超出此数量的符号会被静默忽略，导致实现查询结果不完整。
- **建议**: (1) 使用 SymbolTable 缓存（已通过 `cacheManager.getOrRebuildSymbolTable` 构建），避免重复加载。(2) 或者仅加载继承关系中涉及的符号 ID，而非全量加载。
- **信心水平**: 确定
- **发现来源视角**: 10x 规模运维者

---

## 已知未修复问题确认

以下问题在前次审查中已报告，经本次验证**仍然存在**：

| # | 问题 | 状态 | 本次补充 |
|---|------|------|---------|
| AR-78 | bfsCollect 反向深度始终为 1 | **仍存在** | `CodeGraphService.java:628-645` 仍使用 `edge.getTarget()` |
| AR-77 | indexLocks 竞态条件 | **仍存在** | `:273-304` finally 块仍无条件移除锁 |
| AR-79 | CommunityDetector.runWithTimeout 线程泄漏 | **仍存在** | `:741-749` shutdownNow() 不等待终止 |
| AR-80 | getIndexStats 全量加载 OOME | **仍存在** | `:472-502` 仍用 findAllByQuery().size() |
| AR-81 | getTypeHierarchy 全量加载 | **仍存在** | `:185-192` |
| AR-75 | resolveQualifiedNamesToIds 全量加载 | **仍存在** | `:812-838` |
| AR-76 | CodeCacheManager 超限降级为空 | **仍存在** | `:100-103` |
| AR-65 | language 硬编码 "Java" | **仍存在** | `:767, :849` |
| AR-69 | Tarjan 递归 StackOverflow | **仍存在** | `:692-718` 仍为递归 DFS |
| AR-62 | ensureSubServices 竞态条件 | **仍存在** | `:110-116` 无同步 |
| AR-59 | entityToCodeSymbol 三重复制 | **仍存在** | CodeIndexService:209-238, CodeGraphService:304-333, CodeQueryService:35-64 |

---

## 严重程度分布表

| 严重程度 | 独立发现数 | 编号 |
|---------|-----------|------|
| P0      | 1         | AR-78（已知未修复，本轮确认） |
| P1      | 3         | AR-82, AR-83 + AR-77（已知未修复） |
| P2      | 6         | AR-84, AR-85, AR-86 + AR-79, AR-80, AR-81（已知未修复） |
| P3      | 0         | — |

> 本轮新增独立发现 5 个（AR-82 至 AR-86），已知未修复确认 11 个。
