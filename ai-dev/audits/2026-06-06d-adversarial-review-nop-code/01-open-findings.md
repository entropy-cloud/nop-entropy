# Adversarial Review: nop-code — Open Findings

> **日期**: 2026-06-06（第 11 轮对抗性审查）
> **模块**: nop-code（13 个子模块）
> **审查类型**: 开放式对抗性审查
> **发现来源视角**: 流程检测逻辑审计者 + 搜索引擎集成审计者 + 缓存契约侦探 + 前端-后端契约确认
> **审查范围**: nop-code-flow（FlowDetector/ChangeAnalyzer/DeadCodeDetector 全量）、nop-code-codegen、nop-code-web（view/dict/beans 全量）、ISearchEngine 集成、CodeCacheManager、CodeSearchService

## 去重确认

已审阅以下历史报告：
- r10 (2026-06-06c): AR-145~AR-157（SpringEvent listener 覆盖、Java Record 未入 symbolMap、Python `__init__.py` 全限定名、多语言适配器问题等）
- r9 (2026-06-06b): AR-124~AR-144（增量索引路径失效、view 字段名不匹配、并发锁不完整等）
- r8 (2026-06-06): AR-94~AR-123（零事务→已修复、Leiden directed→已修复、Python TSNode→已修复、glob 匹配→已修复等）
- deep-audit-2026-06-06: 21 维度全覆盖

**已修复确认（自 r10 以来）**：
- AR-125（view 字段名 `isStatic`/`isAbstract` 不匹配）→ **已修复**。view.xml 现在使用 `staticFlag,abstractFlag`，与 SymbolDTO getter 对齐。
- AR-131（view 引用不存在的字典）→ **已修复**。`call_direction.dict.yaml` 和 `hierarchy_direction.dict.yaml` 已创建。

**仍存在的问题（未修复确认）**：
- AR-98（单例节点丢弃）、AR-99（startsWith 错配）、AR-100（cascadeDelete 缺失）
- AR-101~AR-111（persistFlows OOME、线程泄漏、CallGraph 线程安全、脏会话偏移、缓存截断等）
- AR-113~AR-123（注解 O(N²)、imports 丢失、ID 不一致等）
- AR-124（增量索引路径失效）、AR-127/128（并发锁）
- AR-129~AR-144（findReferencedBy 只取第一个、getModuleDigest 全量加载等）
- AR-145~AR-157（listener 覆盖、Record、Python `__init__.py`、Python 相对 import 等）

本轮聚焦于此前审查从未覆盖的三个子系统：**nop-code-flow 全模块**、**ISearchEngine 集成链路**、**nop-code-web 全量验证**。所有发现均为新视角切入的新问题。

---

### [AR-158] persistSingleFileInSession 修改缓存的 SymbolTable——并发 indexFile 可损坏 HashMap

- **文件**: `nop-code/nop-code-service/.../impl/CodeIndexService.java:1096-1106`
- **证据片段**:
  ```java
  SymbolTable fileSymbolTable = buildSymbolTableFromResult(result);
  SymbolTable globalTable = getOrRebuildSymbolTable(indexId);  // 返回缓存实例的引用
  if (globalTable != null) {
      for (CodeSymbol sym : fileSymbolTable.getAll()) {
          if (sym.getQualifiedName() != null) {
              CodeSymbol existing = globalTable.getByQualifiedName(sym.getQualifiedName());
              if (existing == null) {
                  globalTable.add(sym);   // 修改共享缓存对象！
              }
          }
      }
      resolveQualifiedNamesToIds(indexId, globalTable, session);
  }
  ```
- **严重程度**: P1
- **现状**: `getOrRebuildSymbolTable(indexId)` 返回 `CodeCacheManager` 中缓存的 `SymbolTable` 实例的**直接引用**（非副本）。`SymbolTable` 内部使用 `HashMap<String, CodeSymbol>`（非 `ConcurrentHashMap`）。当两个线程并发对同一 `indexId` 调用 `indexFile()` 时，两者都获得同一个 `globalTable` 引用，并发执行 `globalTable.add(sym)`，导致 `HashMap` 的桶链表损坏。

  这与 AR-128（triggerIncrementalIndex/indexFile 无锁）相关但**根因不同**：即使添加了 `indexLocks` 保护 DB 写入，缓存的 `SymbolTable` 对象仍然通过引用被外部修改。缓存契约应返回不可变快照或深拷贝，而不是可变引用。

  同时，`globalTable.add(sym)` 将文件级的符号加入全局缓存后，如果 `indexFile` 后续因异常回滚，全局缓存已被污染（新增的符号指向可能不存在的 DB 记录）。
- **风险**: 并发 `indexFile` 导致 `HashMap` 无限循环（桶链表成环）或数据丢失。缓存与 DB 状态不一致。
- **建议**: (a) `getOrRebuildSymbolTable` 返回 `SymbolTable` 的深拷贝或不可变包装；(b) 或在 `persistSingleFileInSession` 中创建新的合并 `SymbolTable` 而非修改缓存实例；(c) 缓存更新应在 DB 写入成功后再执行。
- **信心水平**: 确定
- **发现来源视角**: 缓存契约侦探

---

### [AR-159] FlowDetector.computeCriticality 中 externalCalls 始终为 0——WEIGHT_EXTERNAL (0.20) 是死权重

- **文件**: `nop-code/nop-code-flow/.../FlowDetector.java:257-259, 336-337, 426-428, 354`
- **证据片段**:
  ```java
  // traceForward (line 257-259): 外部包符号被跳过，不加入 pathNodeIds
  CodeSymbol symbol = symbolTable.getById(current.symbolId);
  if (symbol != null && isExternalPackage(symbol.getQualifiedName())) {
      continue;   // 外部节点不加入结果
  }
  visited.add(current.symbolId);
  result.pathNodeIds.add(current.symbolId);  // 只有非外部节点

  // computeCriticality (line 336-337): 在 pathNodeIds 中查找外部调用
  if (isExternalCall(symbol.getQualifiedName())) {
      externalCalls++;
  }

  // isExternalCall (line 426-428): 与 isExternalPackage 完全相同
  private boolean isExternalCall(String qualifiedName) {
      return isExternalPackage(qualifiedName);
  }
  ```
  `traceForward` 已通过 `isExternalPackage` 过滤掉所有外部节点（不加入 `pathNodeIds`）。`computeCriticality` 在 `pathNodeIds` 中用 `isExternalCall`（与 `isExternalPackage` 相同）检查外部调用——永远找不到。`externalCalls` 始终为 0，`externalScore` 始终为 0.0，`WEIGHT_EXTERNAL = 0.20` 贡献恒为零。
- **严重程度**: P1
- **现状**: 流程关键性评分的五个维度中，`WEIGHT_EXTERNAL`（0.20，占 20%权重）完全失效。所有流程的 externalScore 为 0.0。`fileSpread`（0.30）、`securityScore`（0.25）、`testGap`（0.15）、`depthScore`（0.10） 四个维度实际竞争，但它们的权重和为 0.80 而非 1.0，导致最终评分被系统性压低 20%。
- **风险**: 流程关键性评分不准确——外部调用密集的流程（如远程服务调用）与纯内部流程无法区分。影响影响分析（`getAffectedFlows`）和风险排序。
- **建议**: (a) 在 `traceForward` 中，对外部节点的调用计数但不加入 `pathNodeIds`，将外部调用计数存入 `TraversalResult`；(b) 或在 `computeCriticality` 中改为统计 `callGraph.getCallees(nodeId)` 中被 `isExternalPackage` 过滤掉的边数。
- **信心水平**: 确定
- **发现来源视角**: 流程检测逻辑审计者

---

### [AR-160] FlowDetector.guessExtension 使用 `contains` 匹配扩展名——产生大量误匹配

- **文件**: `nop-code/nop-code-flow/.../FlowDetector.java:232-240`
- **证据片段**:
  ```java
  private String guessExtension(String qualifiedName) {
      String lower = qualifiedName.toLowerCase();
      for (String ext : SOURCE_EXTENSIONS) {
          if (lower.contains(ext.substring(1))) {  // ext=".java" → checks "java"
              return ext;
              // "com.example.JavascriptParser.parse" → contains "java" → returns ".java"
              // "com.example.EventStream.handle" → contains "ts" (in "EventStream") → returns ".ts"
              // "com.example.CopyUtil.copy" → contains "py" (in "Copy") → returns ".py"
          }
      }
      return SOURCE_EXTENSIONS.get(0);
  }
  ```
- **严重程度**: P2
- **现状**: `guessExtension` 将 `".java"` 的子串 `"java"` 在全限定名中做 `contains` 匹配。任何类名或方法名中包含 `"java"`（如 `Javascript`、`JavascriptParser`）都会被误匹配为 `.java`。类似地，`".ts"` 的子串 `"ts"` 匹配 `EventStream`、`Results` 等；`".py"` 匹配 `Copy`、`Empty` 等。

  `guessExtension` 被 `qualifiedNameToFilePath` 调用（line 215, 227），后者是 `isFlowAffected` 和 `symbolIdToFilePath` 的回退路径。当 `symbolFilePathCache` 未命中时，依赖此方法推断文件路径。误匹配导致错误的文件扩展名，使变更文件与流程的关联失效。
- **风险**: 变更影响分析（`getAffectedFlows`）在缓存未命中时关联错误文件，产生虚假影响或遗漏真实影响。
- **建议**: 使用 `endsWith` 匹配类名末尾（如 `lower.endsWith(ext.substring(1))` 或检查扩展名是否作为独立单词出现），或直接使用语言信息（符号已有 `language` 字段）而非启发式猜测。
- **信心水平**: 确定
- **发现来源视角**: 流程检测逻辑审计者

---

### [AR-161] DefaultSpringEntryPointPatternProvider 使用 `extData.contains()` 做注解匹配——子串误匹配

- **文件**: `nop-code/nop-code-flow/.../FlowDetector.java:516-524`
- **证据片段**:
  ```java
  String extData = symbol.getExtData();
  if (extData != null) {
      for (String annotation : SPRING_ENTRY_ANNOTATIONS) {
          String shortName = annotation.substring(annotation.lastIndexOf('.') + 1);
          if (extData.contains(shortName)) {   // JSON 字符串的子串匹配
              return true;
          }
      }
  }
  ```
  `extData` 是 JSON 字符串（如 `{"annotations":["ScheduledTask","Async"]}`）。`extData.contains("Scheduled")` 会匹配包含 `"Scheduled"` 的任何子串（如 `"ScheduledTask"` → 匹配 `"Scheduled"`），产生假阳性。
- **严重程度**: P2
- **现状**: `DefaultSpringEntryPointPatternProvider.isEntryPoint()` 对 `extData`（JSON 字符串）做子串匹配检查注解。`SPRING_ENTRY_ANNOTATIONS` 包含 `"org.springframework.scheduling.annotation.Scheduled"`，其 `shortName` 为 `"Scheduled"`。任何 `extData` 中包含 `"Scheduled"` 子串的符号（如注解了 `@ScheduledTask` 但不是 `@Scheduled` 的方法）都会被误判为入口点。

  同时，如果 JSON 中有字段值恰好包含注解短名（如 `{"name":"TestController"}` 匹配 `"Controller"` 也会命中。虽然 `SPRING_ENTRY_ANNOTATIONS` 中的大多数短名（`Controller`、`Service` 等）足够具体，但 `"Scheduled"` 等短名可能误匹配。
- **风险**: 入口点误判导致流程检测产生虚假的执行流路径，影响变更影响分析的准确性。
- **建议**: 解析 `extData` JSON 后检查注解列表的精确匹配，或使用 `ExtDataHelper.getAnnotations()` 方法（`DeadCodeDetector` 已正确使用此方法）。
- **信心水平**: 很可能（取决于 `extData` 的 JSON 结构中是否会出现注解短名的子串）
- **发现来源视角**: 流程检测逻辑审计者

---

### [AR-162] ChangeAnalyzer.analyzeChanges 传入 `null` 工作目录——git diff 在 JVM CWD 执行

- **文件**: `nop-code/nop-code-flow/.../ChangeAnalyzer.java:63, 121-128`
- **证据片段**:
  ```java
  // line 63:
  Map<String, List<LineRange>> fileChanges = parseGitDiff(baselineCommitish, targetCommitish, null);

  // line 121-128:
  protected Map<String, List<LineRange>> parseGitDiff(String baseline, String target, String workingDirectory) {
      ProcessBuilder pb = new ProcessBuilder("git", "diff", baseline + ".." + target, "--unified=0");
      if (workingDirectory != null) {
          pb.directory(new java.io.File(workingDirectory));
      }
  ```
- **严重程度**: P2
- **现状**: `analyzeChanges` 接口的 `parseGitDiff` 调用始终传 `null` 作为工作目录。`parseGitDiff` 方法支持传入工作目录（line 127-129），但接口层面没有暴露此参数。`git diff` 在 JVM 的当前工作目录执行，而项目根目录可能与 JVM CWD 不同（如在 Web 服务器环境中运行时，CWD 可能是 `/` 或应用服务器目录）。

  BizModel 层（`NopCodeIndexBizModel.analyzeChanges`）也不提供工作目录参数。调用方无法控制 git 命令的执行目录。
- **风险**: 在 JVM CWD 不是 git 仓库根目录的环境中，`git diff` 命令失败或返回错误仓库的 diff，变更分析结果完全无效。无明确的错误消息告知用户根因。
- **建议**: 在 `IChangeAnalyzer.analyzeChanges` 接口或 `NopCodeIndexBizModel` 中增加 `workingDirectory` 参数。如果无法修改接口，至少使用索引的 `rootPath` 作为工作目录。
- **信心水平**: 确定
- **发现来源视角**: 流程检测逻辑审计者

---

### [AR-163] ChangeAnalyzer.parseGitDiff 30 秒超时后返回不完整结果——静默截断

- **文件**: `nop-code/nop-code-flow/.../ChangeAnalyzer.java:193-198`
- **证据片段**:
  ```java
  if (!process.waitFor(30, TimeUnit.SECONDS)) {
      LOG.warn("Git diff process timed out after 30 seconds for {}..{}", baseline, target);
  }
  // 超时后继续处理已读取的部分数据
  ...
  return result;
  ```
- **严重程度**: P2
- **现状**: `parseGitDiff` 使用 `process.waitFor(30, TimeUnit.SECONDS)` 等待 git diff 完成。如果超时，代码仅打印 WARN 日志并继续处理已读取的部分输出。大型 monorepo 的 diff 输出可能超过 30 秒（如第一次分析时 baseline 是初始提交），此时返回的结果只包含部分变更文件列表。

  `destroyForcibly()` 在 `finally` 块中调用（line 197），即使正常完成也强制销毁进程——无害但不必要。
- **风险**: 变更影响分析基于不完整的 diff 数据，遗漏部分变更文件的影响。用户无 API 级别的信号知道结果被截断。
- **建议**: (a) 超时时向 `ChangeAnalysisResult` 添加 `truncated` 标记；(b) 增大超时阈值或使用可配置的超时参数；(c) 只在超时时调用 `destroyForcibly()`。
- **信心水平**: 确定
- **发现来源视角**: 流程检测逻辑审计者

---

### [AR-164] ChangeAnalyzer.computeCommunityCrossing 从方法全限定名提取的是类名而非包名——社区计数偏高

- **文件**: `nop-code/nop-code-flow/.../ChangeAnalyzer.java:334-345`
- **证据片段**:
  ```java
  Set<String> packages = new HashSet<>();
  for (String callerId : callers) {
      CodeSymbol caller = symbolTable.getById(callerId);
      if (caller != null && caller.getQualifiedName() != null) {
          int lastDot = caller.getQualifiedName().lastIndexOf('.');
          if (lastDot > 0) {
              packages.add(caller.getQualifiedName().substring(0, lastDot));
          }
      }
  }
  return packages.size() > 1 ? CAP_COMMUNITY_CROSSING : 0.0;
  ```
  对于 `com.example.service.UserService.process(Request)`，`lastIndexOf('.')` 找到 `process(` 之前的点，`substring(0, lastDot)` 返回 `com.example.service.UserService`——这是**类名**而非包名。
- **严重程度**: P3
- **现状**: `computeCommunityCrossing` 旨在统计调用者跨越的不同社区（包/模块）数量。但 `lastIndexOf('.')` 对方法全限定名提取的是 `package.ClassName` 而非 `package`。每个不同类名的调用者都被视为不同社区，导致社区计数膨胀。当同类中有多个调用者时（如 `ServiceA.process` 和 `ServiceA.handle` 都调用目标），它们被正确合并为一个社区，但 `ServiceA.process` 和 `ServiceB.process` 被视为两个社区（实际可能在同一个包下）。
- **风险**: 风险评分中的社区交叉维度区分度降低——同一个包下不同类的调用者被错误地视为跨社区调用。风险评分偏高。
- **建议**: 对方法符号，提取两次 `lastIndexOf('.')` 以获取真正的包名；或使用符号的 `declaringSymbolId` 查找父类，再从类的全限定名提取包名。
- **信心水平**: 确定
- **发现来源视角**: 流程检测逻辑审计者

---

### [AR-165] filterByFilePattern 不完整正则转义——AR-112 修复残留问题

- **文件**: `nop-code/nop-code-service/.../impl/CodeSearchService.java:284`
- **证据片段**:
  ```java
  String pattern = filePattern.replace(".", "\\.").replace("*", ".*").replace("?", ".");
  ```
  AR-112 修复了 `Pattern.quote` 阻止通配符的问题，改用手动转义。但当前实现只转义了 `.`、`*`、`?` 三种字符。正则特殊字符 `+`、`{`、`}`、`(`、`)`、`[`、`]`、`^`、`$`、`|` 未被转义。

  例如：
  - `filePattern="test(v2).java"` → 产生 `test\(v2\)\.java`（`(` 和 `)` 未转义）→ `PatternSyntaxException`
  - `filePattern="module^2/test.java"` → `^` 未转义 → 可能匹配错误
  - `filePattern="a+b.java"` → `+` 未转义 → 错误匹配
- **严重程度**: P2
- **现状**: 4 个搜索路径（`searchBySymbolName`、`searchFullText`、`searchCombined`、`searchViaEngine`）都经过 `filterByFilePattern`。用户传入包含正则特殊字符的文件模式（如含括号的文件名 `test(v2).java`）时，`String.matches()` 抛出 `PatternSyntaxException`，搜索请求失败并返回错误。
- **风险**: 特定文件名模式的搜索请求崩溃。由于此方法在搜索引擎回退路径上，可能导致整个搜索功能不可用。
- **建议**: 使用 `java.util.regex.Pattern.quote()` 先 quote 整个字符串，再替换通配符的 quoted 形式：
  ```java
  String pattern = Pattern.quote(filePattern)
          .replace("\\Q", "").replace("\\E", "")
          .replace(".", "\\.").replace("*", ".*").replace("?", ".");
  ```
  或更安全地使用 `org.apache.commons.io.FilenameUtils.wildcardMatch` 或 `PathMatcher`。
- **信心水平**: 确定
- **发现来源视角**: 搜索引擎集成审计者

---

### [AR-166] 增量索引不清理搜索引擎中已删除/变更文件的旧符号文档——搜索引擎返回已不存在的符号

- **文件**: `nop-code/nop-code-service/.../impl/CodeIndexService.java:757-797, 1192-1224`
- **证据片段**:
  ```java
  // triggerIncrementalIndex (line 757-758):
  deleteFileRecords(indexId, deletedFiles);    // 从 DB 删除
  deleteFileRecords(indexId, changedFiles);    // 从 DB 删除

  // ... 重新分析变更文件 ...
  // saveFileResultInSession (line 1192-1224):
  // 只添加新符号到搜索引擎，不删除旧符号
  if (searchEngine != null) {
      for (CodeSymbol sym : file.getSymbols()) {
          searchEngine.addDoc(topic, doc);    // 只 addDoc，不 removeDoc
      }
  }
  ```
  对比 `deleteIndex`（line 592）：
  ```java
  searchEngine.removeTopic("nop-code-" + indexId);   // 清除整个 topic
  ```
- **严重程度**: P2
- **现状**: 增量索引处理变更/删除文件时：
  1. 从 DB 删除旧文件记录和关联的符号/调用/继承记录（`deleteFileRecords`）
  2. 重新分析变更文件并添加新记录到 DB
  3. 将新符号添加到搜索引擎（`searchEngine.addDoc`）

  但**从未删除搜索引擎中旧文件的符号文档**。如果一个类有 10 个方法，重构后只剩 5 个，搜索引擎中仍有 10 条文档。只有 `deleteIndex`（删除整个索引）会清理整个 topic。

  `searchEngine` 接口有 `removeDoc(topic, docId)` 方法可用，但增量路径从未调用。
- **风险**: 搜索引擎返回已不存在的符号（已从 DB 删除但搜索引擎中仍在）。搜索结果点击后无法显示详情（DB 查不到），用户体验差。随增量索引轮次累积，搜索引擎数据与 DB 数据越来越不一致。
- **建议**: 在 `deleteFileRecords` 中，对被删除文件的每个符号调用 `searchEngine.removeDoc(topic, symId)`。或在变更文件处理前，先从搜索引擎删除该文件的所有符号文档（需要加载文件对应的符号 ID 列表）。
- **信心水平**: 确定
- **发现来源视角**: 搜索引擎集成审计者

---

### [AR-167] searchViaEngine 始终使用 HYBRID 搜索——无 embedding 提供者时搜索质量退化

- **文件**: `nop-code/nop-code-service/.../impl/CodeSearchService.java:65`
- **证据片段**:
  ```java
  req.setSearchType(SearchType.HYBRID);
  ```
  当 `ITextEmbedding` 未配置时，`LuceneSearchEngine` 使用 `generateSimpleEmbedding()`（基于 `hashCode()` 的确定性但无语义的 768 维向量）。RRF 融合将这个无意义的向量分数与 BM25 文本分数混合。
- **严重程度**: P3
- **现状**: `searchViaEngine` 硬编码 `SearchType.HYBRID`。在默认部署中（无 embedding 提供者），向量搜索部分使用基于 hashCode 的假向量，RRF 融合会将 BM25 的排序质量被无意义向量分数稀释。纯 `TEXT` 搜索在此场景下质量更高。
- **风险**: 搜索质量低于纯文本搜索。用户感觉"搜索引擎不如直接搜索"。
- **建议**: 检测 embedding 提供者是否可用（如 `searchEngine.hasEmbeddingProvider()` 或通过配置注入），动态选择 `TEXT` 或 `HYBRID`。
- **信心水平**: 很可能
- **发现来源视角**: 搜索引擎集成审计者

---

### [AR-168] buildFilePathCache 硬限制 MAX_QUERY_RESULTS——大型索引搜索结果丢失文件路径

- **文件**: `nop-code/nop-code-service/.../impl/CodeSearchService.java:188-199`
- **证据片段**:
  ```java
  private Map<String, String> buildFilePathCache(String indexId) {
      IEntityDao<NopCodeFile> fileDao = daoProvider.daoFor(NopCodeFile.class);
      QueryBean fq = new QueryBean();
      fq.addFilter(FilterBeans.eq("indexId", indexId));
      fq.setLimit(CodeIndexService.MAX_QUERY_RESULTS);  // 10,000
      List<NopCodeFile> files = fileDao.findAllByQuery(fq);
      Map<String, String> cache = new HashMap<>();
      for (NopCodeFile f : files) {
          cache.put(f.getId(), f.getFilePath());
      }
      return cache;
  }
  ```
  `toSearchResult` 方法使用 `filePathCache.get(sym.getFileId())` 获取文件路径。当 fileId 不在 cache 中时，filePath 为 null。
- **严重程度**: P2
- **现状**: `buildFilePathCache` 设置 `limit=MAX_QUERY_RESULTS(10000)`。对于超过 10,000 个文件的索引，filePath 映射不完整。`toSearchResult` 对不在缓存中的符号设置 `filePath=null`，搜索结果缺少文件路径信息。

  此方法在每次搜索请求时都被调用（`searchBySymbolName`、`searchFullText`、`searchCombined`、`searchViaEngine`），且每次都全量查询。
- **风险**: 超过 10,000 文件的索引中，部分搜索结果缺少文件路径，前端无法正确显示结果。用户看到"文件路径: null"。
- **建议**: (a) 移除 `setLimit` 限制，或使用两倍于实际文件数的限制；(b) 在服务层缓存此映射（`CodeCacheManager` 已有缓存基础设施）。
- **信心水平**: 确定
- **发现来源视角**: 搜索引擎集成审计者

---

### [AR-169] nop-code-web 验证通过——view/dict/beans/xmeta 配置全部正确

- **文件**: 多文件交叉验证
- **证据片段**: 无（正面确认）
- **严重程度**: N/A
- **现状**: 对 nop-code-web 模块的全量验证结果：
  1. **26 个 view.xml 文件**：所有 dict 引用、GraphQL API 端点、page 链接均有效
  2. **6 个 beans.xml 文件**：所有 bean class 引用存在，注入类型正确
  3. **11 个 BizModel + biz 接口对**：Java 文件全部存在
  4. **22 个 xmeta 文件**：所有 entityName 引用和 prop-to-column 映射正确
  5. **AR-125 已修复**：view.xml 现在使用 `staticFlag,abstractFlag`
  6. **AR-131 已修复**：`call_direction.dict.yaml` 和 `hierarchy_direction.dict.yaml` 已创建

  唯一残留问题：`staticFlag` 和 `abstractFlag` 在 `NopCodeSymbol.xmeta` 中缺少 `<prop>` 定义（仅有 `modifiers` propId=18），导致表单渲染可能缺少类型元数据，但数据在 GraphQL 层面正确返回。
- **风险**: 无
- **建议**: 可在 xmeta 中为 `staticFlag` 和 `abstractFlag` 添加 computed prop 定义以改善表单渲染。
- **信心水平**: 确定
- **发现来源视角**: 前端-后端契约确认

---

## 总评

本轮审查深入了此前 10 轮从未系统覆盖的三个子系统：**nop-code-flow 全模块**（FlowDetector 567 行、ChangeAnalyzer 446 行、DeadCodeDetector 382 行）、**ISearchEngine 集成链路**、**nop-code-web 全量配置验证**。

**最值得关注的 3 个方向**：

1. **缓存引用泄漏**（AR-158）——`persistSingleFileInSession` 修改 `CodeCacheManager` 返回的共享 `SymbolTable` 实例，破坏缓存的不变性约定。并发场景下可导致 `HashMap` 无限循环。这是一个跨缓存边界的设计缺陷，之前的 AR-128（DB 层锁）和 AR-110（缓存刷新时序）都与此相关，但未触及"缓存对象可变性"这个根因。

2. **流程关键性评分 20% 权重失效**（AR-159）——`traceForward` 与 `computeCriticality` 对"外部调用"的定义一致（`isExternalPackage ≡ isExternalCall`），导致 `externalCalls` 恒为零。这是 nop-code-flow 模块中一个典型的"两段代码使用了相同判定逻辑但预期不同行为"的矛盾。

3. **搜索引擎增量同步缺失**（AR-166）——增量索引只添加新符号到搜索引擎，从不删除旧符号。随增量索引轮次累积，搜索引擎与 DB 的数据越来越不一致。这是一个容易被忽视的"增量管线不完整"问题。

**正面发现**：nop-code-web 模块的配置（view、dict、beans、xmeta）经过 AR-125 和 AR-131 的修复后，现已全部正确。nop-code-codegen 模块也无可报告的问题。

## 本次审查的盲区自评

1. **DeadCodeDetector 深度验证**：虽然阅读了代码，但未构建测试用例验证 `isPotentiallyDynamic` 的名称启发式（`"handler"`、`"listener"` 等子串匹配）在实际项目中的假阴性率。
2. **LuceneSearchEngine 内部并发**：未深入审查 `LuceneSearchEngine` 的 `IndexWriter`/`SearcherManager` 的线程安全模型和 NRT（近实时）刷新行为。
3. **FlowDetector 的 BFS 深度阈值**：`maxDepth` 默认值对大型项目的影响未量化分析。
4. **端到端搜索质量测试**：未构建实际索引验证 HYBRID 搜索 vs TEXT 搜索的质量差异。
5. **Delta 定制层**：未检查 Delta 文件是否覆盖了 FlowDetector 或 ChangeAnalyzer 的关键路径。

## 按严重程度分布表

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0      | 0    | — |
| P1      | 2    | 缓存引用泄漏（AR-158）、流程关键性 externalCalls 死权重（AR-159） |
| P2      | 6    | guessExtension 误匹配（AR-160）、extData 注解子串匹配（AR-161）、git diff 无工作目录（AR-162）、git diff 超时截断（AR-163）、filterByFilePattern 不完整转义（AR-165）、搜索引擎增量不同步（AR-166）、buildFilePathCache 硬限制（AR-168） |
| P3      | 2    | 社区交叉提取类名非包名（AR-164）、HYBRID 搜索无 embedding（AR-167） |
