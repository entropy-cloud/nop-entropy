# 71 nop-code P2 逻辑缺陷修复与代码质量改进

> Plan Status: completed
> Last Reviewed: 2026-05-29
> Source: `ai-dev/audits/2026-05-29-adversarial-review-nop-code/01-open-findings.md`（AR-12 至 AR-27）、`ai-dev/audits/nop-code-audit-2026-05-10.md`（P1-2 sealed class、P1-4 memory limits）
> Related: `70-nop-code-adversarial-review-remediation.md`（completed，P0+P1），`69-nop-code-2026-05-29-audit-remediation.md`（completed，ORM/BizModel/测试反模式）

## Purpose

修复对抗性审查中 deferred 的 P2 逻辑缺陷（AR-12 至 AR-21）和 P3 代码质量问题（AR-22 至 AR-27），以及 05-10 审计中遗留的 sealed class 支持缺口和内存边界检查缺失。将 nop-code 模块的逻辑正确性和代码质量收口到可接受状态。

## Current Baseline

### 已完成计划覆盖范围

- **Plan 70**：11 项 P0+P1 修复——Tree-sitter 字节偏移、VFS 过滤器、增量分析退化、cohesion 统一、O(N²) 查询、evictAll、hashCode 碰撞、visited-set、HashMap→ConcurrentHashMap、TSLanguage 缓存、TSX 验证
- **Plan 69**：4 Phase——ORM 关系语义、级联删除、nop-code-api 清理、@DataBean、session flush/evict、测试反模式
- **Plan 58**：5 个 P0 bug——CommunityDetector labels、searchCode filter、symbol sourceCode、ChangeAnalyzer leak、FlowDetector entry point
- **Plan 55**：安全/数据完整性/IoC/ORM 索引等修复
- **Plan 59**：Semantic Edge 模型实现

### 本次计划范围——未修复发现

**P2 逻辑缺陷（10 项，来自 Plan 70 deferred）**：
- AR-12：CodeIndexService 缓存 TOCTOU 竞态——invalidate 与 rebuild 之间无原子保护
- AR-13：ChangeAnalyzer.parseGitDiff 异常时子进程永不销毁，无超时
- AR-14：DocKeywordExtractor O(N²) 无符号上限——万级符号时 2 亿次迭代
- AR-15：SymbolTable.getAll() 不返回无 qualifiedName 的符号——下游分析遗漏
- AR-16：FlowDetector.generateFlowId 使用 identityHashCode——跨 JVM 不稳定
- AR-17：ImpactAnalyzer.extractFilePath 永远返回 null——影响分析无文件路径
- AR-18：DeadCodeDetector.resolveFilePath 返回整个 JSON extData 作为路径
- AR-19：ImpactAnalyzer.findSymbolByQualifiedName 模糊匹配返回歧义结果
- AR-20：searchBySymbolName/searchCombined 忽略 language 过滤器
- AR-21：glob exclude 匹配器产生误匹配（子字符串匹配）

**P3 代码质量（6 项，来自 Plan 70 deferred）**：
- AR-22：Python dunder 方法（`__init__`）被分类为 PRIVATE
- AR-23：JavaFileAnalyzer 手写 JSON 转义不完整
- AR-24：ImpactAnalyzer.RiskLevel 枚举是死代码
- AR-25：MethodCallFilter.isJavaLangType 每次调用创建新 HashSet
- AR-26：Python walkExpressionStatement 空壳
- AR-27：Java 分析器语言级别硬编码 JAVA_17

**05-10 审计遗留**：
- P1-2（残余）：Java sealed class 的 `permits` 子句未提取
- P1-4：ProjectAnalyzer 无内存边界检查

### 代码库事实基线

- `CodeIndexService.java` 当前 3003 行（God Class，拆分不在本 plan scope）
- `analysisCacheMap` 使用 ConcurrentHashMap，但 `synchronized(this)` 方法锁粒度过粗
- `SymbolTable` 有两个内部 Map：`byQualifiedName` 和 `byId`，`getAll()` 返回 `byQualifiedName.values()`
- `FlowDetector.generateFlowId` 使用 `System.identityHashCode`（非确定性）
- `ImpactAnalyzer.extractFilePath` 是空壳（`return null`）
- `DeadCodeDetector.resolveFilePath` 返回整个 JSON 字符串
- `searchBySymbolName` 和 `searchCombined` 方法不调用 `filterByLanguage`
- `ProjectAnalyzer.shouldExclude` 使用 `pathStr.contains(dir)` 子字符串匹配
- `DocKeywordExtractor` 双层循环无上限（不同于 `NameSimilarityExtractor` 的 5000 上限）
- `ChangeAnalyzer.parseGitDiff` 无 `process.destroyForcibly()` 和超时
- `JavaFileAnalyzer` 使用 JavaParser JAVA_17，已支持 Record 但无 sealed permits 提取

## Goals

1. 修复缓存并发竞态（AR-12）——消除 TOCTOU 窗口
2. 修复子进程资源泄漏（AR-13）——确保异常路径进程被销毁
3. 添加 DocKeywordExtractor 符号上限（AR-14）——防止 O(N²) 爆炸
4. 修复 SymbolTable.getAll() 数据遗漏（AR-15）——使所有符号对下游分析可见
5. 使 FlowDetector flowId 跨 JVM 稳定（AR-16）
6. 修复 ImpactAnalyzer/DeadCodeDetector 文件路径提取（AR-17/AR-18）——消除空壳和错误返回
7. 修复 ImpactAnalyzer 模糊匹配歧义（AR-19）——优先精确匹配
8. 补全搜索语言过滤（AR-20）——统一 API 行为
9. 修复 glob exclude 误匹配（AR-21）——使用正确路径匹配
10. 修复 P3 代码质量问题（AR-22 至 AR-27）——快速改进
11. 补全 Java sealed class permits 提取（05-10 P1-2 残余）
12. 添加 ProjectAnalyzer 内存边界防护（05-10 P1-4）

## Non-Goals

- CodeIndexService God Class 拆分（Plan 55/69 deferred，大型重构 → successor plan）
- 大规模测试覆盖补充（Plan 55/69 deferred → successor plan）
- BizModel 方法按聚合根重分配（Plan 55/69 deferred → successor plan）
- 错误码中文改英文（Plan 55 watch-only）
- 前端图可视化（05-10 P0-7/P3-3，前端专项）
- 结构化类型系统（05-10 P1-5，长期架构演进）
- 外部符号引用（05-10 P2-2，长期能力建设）
- Python/TypeScript import 解析增强（05-10 P2-3，中期能力建设）

## Scope

### In Scope

- AR-12 至 AR-27 全部 P2 和 P3 修复
- Java sealed class permits 提取
- ProjectAnalyzer 内存边界防护
- 对应的单元测试

### Out Of Scope

- CodeIndexService God Class 拆分
- 大规模测试覆盖补充
- BizModel 归属重分配
- 前端页面/可视化
- tree-sitter 库版本升级
- 错误码国际化

## Decisions

### D1 — AR-12 缓存 TOCTOU 竞态修复策略

**选择 Strategy A**：将 `indexDirectory()` 中的 `invalidateAnalysisCache(indexId)` 调用与整个 `ormTemplate.runInSession(...)` 块包装在同一个 `synchronized(this)` 块内，确保 invalidate + persist + 后续可能的 cache rebuild 在同一锁范围内完成。

理由：(1) 改动最小；(2) `synchronized(this)` 粒度虽粗但对索引操作（低频）影响可忽略；(3) per-indexId ReentrantLock 虽更精细但增加复杂度，索引操作本身是全表重建，并发粒度优化收益有限。注意：`ormTemplate.runInSession` 持有 session/事务，在粗锁内执行对低频索引操作可接受。

### D2 — AR-16 flowId 稳定化策略

**选择确定性 ID**：将 `generateFlowId` 改为 `"flow-" + entrySymbolId`（直接使用入口符号 ID）。

理由：(1) `entrySymbolId` 在同一索引内唯一；(2) 跨 JVM 确定性；(3) 无需哈希计算。

### D3 — AR-21 glob exclude 修复策略

**选择 Strategy B**：使用 Nop 平台的 `StringHelper.matchSimplePattern` 或 Java `PathMatcher` 替代子字符串匹配。优先使用 Nop 平台已有的 glob 工具方法。

理由：(1) 复用平台能力；(2) `PathMatcher` 是 Java 标准 API；(3) 不引入新依赖。

### D4 — 05-10 P1-4 内存边界策略

**选择阈值防护**：在 `ProjectAnalyzer.analyzeProject()` 中添加文件大小上限检查（默认 1MB per file）和总文件数上限检查（默认 50000）。超大文件跳过并记录警告日志。

理由：(1) 最少侵入；(2) 防止 OOM 而非优化内存管理；(3) 流式持久化是中长期方案（05-10 P1-3），本 plan 只做硬性上限防护。

## Execution Plan

### Phase 1 - P2 并发安全与资源管理修复

Status: completed
Targets: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java`, `nop-code/nop-code-flow/src/main/java/io/nop/code/flow/ChangeAnalyzer.java`, `nop-code/nop-code-graph/src/main/java/io/nop/code/graph/semantic/DocKeywordExtractor.java`

- Item Types: `Fix`

- [x] **修复 AR-12**：`indexDirectory()` 已是 `synchronized` 方法，`invalidateAnalysisCache(indexId)` 和 `ormTemplate.runInSession(...)` 在同一个 `synchronized(this)` 块内（line 333-361）
- [x] **修复 AR-13**：`ChangeAnalyzer.parseGitDiff` 已有 `finally { process.destroyForcibly(); }` 和 `process.waitFor(30, TimeUnit.SECONDS)` 超时保护（line 143-149）
- [x] **修复 AR-14**：`DocKeywordExtractor` 已有 `MAX_SYMBOLS` 常量和截断逻辑，超出 5000 时截断并记录警告日志（line 56-60）
- [x] 新增测试 `TestChangeAnalyzer.testParseGitDiffProcessCleanupOnInvalidRefs`：验证 `parseGitDiff` 异常路径下进程被销毁
- [x] 新增测试 `TestDocKeywordExtractor.testTruncationAboveMaxSymbols`：验证超过 5000 符号时正确截断

Exit Criteria:

- [x] `indexDirectory()` 中 `invalidateAnalysisCache` 调用和 `ormTemplate.runInSession` 块在同一个 `synchronized(this)` 块内
- [x] `ChangeAnalyzer.parseGitDiff` 有 `finally { process.destroyForcibly(); }` 和 30 秒超时
- [x] `DocKeywordExtractor` 有 5000 符号上限，超出时截断并记录警告
- [x] 新增至少 2 个测试（进程清理、符号上限），全部通过
- [x] `./mvnw test -pl nop-code -am` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - P2 数据正确性修复

Status: completed
Targets: `nop-code/nop-code-core/src/main/java/io/nop/code/core/graph/SymbolTable.java`, `nop-code/nop-code-flow/src/main/java/io/nop/code/flow/FlowDetector.java`, `nop-code/nop-code-graph/src/main/java/io/nop/code/graph/impact/ImpactAnalyzer.java`, `nop-code/nop-code-flow/src/main/java/io/nop/code/flow/DeadCodeDetector.java`, `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java`

- Item Types: `Fix`

- [x] **修复 AR-15**：`SymbolTable.getAll()` 已返回 `byId.values()`，`size()` 已返回 `byId.size()`，包含所有符号（line 31-37）
- [x] **修复 AR-16（Decision D2）**：`FlowDetector.generateFlowId` 已使用 `"flow-" + entrySymbolId`（line 401-403）
- [x] **修复 AR-17**：`ImpactAnalyzer.extractFilePath` 已使用 `JsonTool.parseNonStrict(extData)` 提取 `filePath` 字段（line 330-347）
- [x] **修复 AR-18**：`DeadCodeDetector.resolveFilePath` 已使用 `JsonTool.parseNonStrict(extData)` 提取 `filePath` 字段（line 365-383）
- [x] **修复 AR-19**：`ImpactAnalyzer.findSymbolByQualifiedName` 已优先精确匹配——先 `getByQualifiedName` 精确查找，再 `equals` 完全匹配，最后 `startsWith` 前缀匹配（line 294-328）
- [x] **修复 AR-20**：`searchBySymbolName` 和 `searchCombined` 已调用 `filterByLanguage(results, indexId, language, filePathCache)`（line 834, line 893）
- [x] **修复 AR-21（Decision D3）**：`ProjectAnalyzer.shouldExclude` 已使用 `path.getName(i).toString().equals(dir)` 精确路径段匹配（line 807-820）
- [x] 新增测试 `TestSymbolTable.testGetAllIncludesSymbolsWithoutQualifiedName`：验证 `getAll()` 包含无 qualifiedName 的符号
- [x] 新增测试 `TestImpactAnalyzer.testExtractFilePathFromExtData`：验证 `extractFilePath` 返回实际路径（非 null）
- [x] 新增测试 `TestNopSearchIntegration.testSearchBySymbolName_filtersByLanguage`：验证 `searchBySymbolName` 和 `searchCombined` 正确过滤语言
- [x] 新增测试 `TestProjectAnalyzerFileFilter.testExcludePatternDoesNotSubstringMatch`：验证 `**/build/**` 不误匹配 `/rebuild/scripts/`

Exit Criteria:

- [x] `SymbolTable.getAll()` 返回 `byId.values()`，包含所有符号；`size()` 返回 `byId.size()`
- [x] `FlowDetector.generateFlowId` 使用确定性 `"flow-" + entrySymbolId`
- [x] `ImpactAnalyzer.extractFilePath` 返回实际文件路径（非 null 空壳）
- [x] `DeadCodeDetector.resolveFilePath` 使用 JSON 解析提取 filePath（非返回整个 JSON）
- [x] `ImpactAnalyzer.findSymbolByQualifiedName` 优先返回完全匹配结果
- [x] `searchBySymbolName` 和 `searchCombined` 调用 `filterByLanguage`
- [x] `ProjectAnalyzer.shouldExclude` 使用正确的路径匹配（非子字符串）
- [x] 新增至少 4 个测试，全部通过
- [x] `./mvnw test -pl nop-code -am` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - P3 代码质量与功能补全

Status: completed
Targets: `nop-code/nop-code-lang-python/`, `nop-code/nop-code-lang-java/`, `nop-code/nop-code-graph/`, `nop-code/nop-code-core/`

- Item Types: `Fix`

- [x] **修复 AR-22**：`PythonCodeFileAnalyzer.inferAccessModifier` 已对 `__name__` 模式返回 `PUBLIC`（line 450-459，先检查 dunder 再检查单下划线）
- [x] **修复 AR-23**：`JavaFileAnalyzer.toJson()` 已使用 `JsonTool.stringify(map)` 替代手写 JSON 转义（line 763-765）
- [x] **修复 AR-24**：`ImpactAnalyzer.evaluateRisk` 已使用 `RiskLevel` 枚举（LOW/MEDIUM/HIGH/CRITICAL），返回 `level.name().toLowerCase()`（line 274-292）
- [x] **修复 AR-25**：`MethodCallFilter.JAVA_LANG_TYPES` 已为 `private static final Set<String> JAVA_LANG_TYPES = Set.of(...)`（line 149-154）
- [x] **修复 AR-26**：`PythonCodeFileAnalyzer.walkExpressionStatement` 已实现赋值表达式处理，提取为 FIELD 符号（line 353-378）
- [x] **修复 AR-27**：`JavaFileAnalyzer` 已使用 `JAVA_21`（line 78）
- [x] **补全 sealed class 支持（05-10 P1-2 残余）**：`JavaFileAnalyzer` 已在 `visit(ClassOrInterfaceDeclaration)` 中检测 `isSealed()` 并提取 `getPermittedTypes()` 列表存入 extData（line 198-212）
- [x] **添加内存边界防护（05-10 P1-4，Decision D4）**：`ProjectAnalyzer.analyzeProject()` 已有 `MAX_FILE_SIZE_BYTES = 1MB` 和 `MAX_FILE_COUNT = 50000` 上限检查（line 59-60, 166-169, 178-181）
- [x] 新增测试 `TestPythonCodeFileAnalyzer.testAnalyzeClassWithMethods`：验证 `__init__` 被分类为 PUBLIC
- [x] 新增测试 `TestJavaCodeFileAnalyzer.testSealedClassPermitsExtracted`：验证 sealed class 的 permits 信息被提取
- [x] 新增测试 `TestProjectAnalyzerFileFilter.testLargeFilesSkipped`：验证大文件被跳过

Exit Criteria:

- [x] `__name__` dunder 方法被分类为 PUBLIC
- [x] `JavaFileAnalyzer.toJson()` 使用平台 JSON 工具（非手写转义）
- [x] `evaluateRisk` 使用 `RiskLevel` 枚举
- [x] `JAVA_LANG_TYPES` 为 `private static final` 字段
- [x] `walkExpressionStatement` 提取赋值表达式（至少 FIELD 符号）
- [x] JavaParser 语言级别为 JAVA_21
- [x] sealed class 的 permits 信息被提取并存入 extData
- [x] `ProjectAnalyzer` 跳过超过 1MB 的文件，50000 文件总数上限
- [x] 新增至少 3 个测试（dunder access、sealed class、memory guard），全部通过
- [x] `./mvnw test -pl nop-code -am` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 全部 10 项 P2 逻辑缺陷已修复（AR-12 至 AR-21）
- [x] 全部 6 项 P3 代码质量问题已修复（AR-22 至 AR-27）
- [x] Java sealed class permits 提取已实现
- [x] ProjectAnalyzer 内存边界防护已添加
- [x] 不存在被静默降级到 deferred 的 in-scope live defect
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证空壳方法（extractFilePath、walkExpressionStatement）已实现实际逻辑
- [x] `./mvnw compile -pl nop-code -am` 通过
- [x] `./mvnw test -pl nop-code -am` 通过
- [x] checkstyle / 代码规范检查通过
- [x] No owner-doc update required（不改变公共 API 契约或平台规范）

## Deferred But Adjudicated

### CodeIndexService God Class 拆分

- Classification: `optimization candidate`
- Why Not Blocking Closure: 功能正确，3003 行是可维护性问题非正确性问题。拆分需要大量方法搬迁和接口设计
- Successor Required: yes
- Successor Path: 待创建 successor plan

### 大规模测试覆盖补充

- Classification: `optimization candidate`
- Why Not Blocking Closure: 不影响功能正确性
- Successor Required: yes
- Successor Path: 待创建 successor plan

### BizModel 方法按聚合根重分配

- Classification: `optimization candidate`
- Why Not Blocking Closure: 不影响功能正确性
- Successor Required: yes
- Successor Path: 待创建 successor plan

### 流式持久化（05-10 P1-3）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 本 plan 的内存边界防护（Decision D4）提供硬性 OOM 保护。流式持久化是性能优化
- Successor Required: no
- Successor Path: N/A

### 错误码中文改英文

- Classification: `watch-only residual`
- Why Not Blocking Closure: 不影响运行时行为
- Successor Required: no
- Successor Path: N/A

### 前端图可视化

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 前端专项，非后端逻辑问题
- Successor Required: no
- Successor Path: N/A

## Non-Blocking Follow-ups

- 结构化类型系统（05-10 P1-5）——长期架构演进
- 外部符号引用注册表（05-10 P2-2）——长期能力建设
- Python/TypeScript import 解析增强（05-10 P2-3）——中期能力建设
- E2E 测试覆盖补充（05-10 P1-6）——测试专项
- per-indexId ReentrantLock 替代 synchronized(this)——并发粒度优化

## Closure

Status Note: 全部 3 个 Phase 均已完成。所有 10 项 P2 逻辑缺陷（AR-12 至 AR-21）和 6 项 P3 代码质量问题（AR-22 至 AR-27）均已在先前计划（Plan 70 Phase 1-3）中修复并落地。Java sealed class permits 提取和 ProjectAnalyzer 内存边界防护也已实现。所有测试通过。

Closure Audit Evidence:

- Reviewer / Agent: Independent Closure Auditor (ses_18db6ca9fffeuCJcrKz4d7R4yW)
- Evidence:
  - Phase 1 Exit Criteria: indexDirectory is synchronized method (CodeIndexService.java:333); parseGitDiff has finally { process.destroyForcibly(); } and waitFor(30, SECONDS) (ChangeAnalyzer.java:150-154); DocKeywordExtractor has MAX_SYMBOLS=5000 with truncation (DocKeywordExtractor.java:27,48-51); TestChangeAnalyzer.testParseGitDiffProcessCleanupOnInvalidRefs PASS; TestDocKeywordExtractor.testTruncationAboveMaxSymbols PASS
  - Phase 2 Exit Criteria: SymbolTable.getAll() returns byId.values() (SymbolTable.java:31-32); generateFlowId uses "flow-" + entrySymbolId (FlowDetector.java:401-402); extractFilePath parses JSON extData (ImpactAnalyzer.java:330-347); resolveFilePath parses JSON extData (DeadCodeDetector.java:365-383); findSymbolByQualifiedName does exact match first (ImpactAnalyzer.java:298-301); searchBySymbolName/searchCombined call filterByLanguage (CodeIndexService.java:833,896); shouldExclude uses path.getName(i).equals(dir) segment matching (ProjectAnalyzer.java:813-816); TestSymbolTable.testGetAllIncludesSymbolsWithoutQualifiedName PASS; TestImpactAnalyzer.testExtractFilePathFromExtData PASS; TestNopSearchIntegration language filter tests PASS; TestProjectAnalyzerFileFilter.testExcludePatternDoesNotSubstringMatch PASS
  - Phase 3 Exit Criteria: Dunder methods return PUBLIC (PythonCodeFileAnalyzer.java:452-453); toJson uses JsonTool.stringify (JavaFileAnalyzer.java:764); evaluateRisk uses RiskLevel enum (ImpactAnalyzer.java:146-151,281-289); JAVA_LANG_TYPES is private static final Set (MethodCallFilter.java:149); walkExpressionStatement extracts assignments as FIELD symbols (PythonCodeFileAnalyzer.java:353-378); JavaParser uses JAVA_21 (JavaFileAnalyzer.java:78); sealed class permits extracted (JavaFileAnalyzer.java:198-212); memory guard has MAX_FILE_SIZE_BYTES=1MB and MAX_FILE_COUNT=50000 (ProjectAnalyzer.java:59-60,166-169,178-181); TestPythonCodeFileAnalyzer.testAnalyzeClassWithMethods PASS; TestJavaCodeFileAnalyzer.testSealedClassPermitsExtracted PASS; TestProjectAnalyzerFileFilter.testLargeFilesSkipped PASS
  - `./mvnw test -pl nop-code -am` BUILD SUCCESS (all modules pass, 0 failures)
  - Anti-Hollow Check: extractFilePath, walkExpressionStatement, resolveFilePath all have real non-hollow logic verified by focused tests
  - Deferred 项分类检查: all 6 deferred items are legitimately optimization candidates, watch-only residuals, or out-of-scope improvements; no in-scope live defect was silently downgraded

Follow-up:

- CodeIndexService God Class 拆分 → successor plan needed
- 大规模测试覆盖补充 → successor plan needed
- BizModel 方法按聚合根重分配 → successor plan needed
