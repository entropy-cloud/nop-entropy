# 70 nop-code 对抗性审查修复

> Plan Status: in progress
> Last Reviewed: 2026-05-29
> Source: `ai-dev/audits/2026-05-29-adversarial-review-nop-code/01-open-findings.md`（27 项新发现）
> Related: `69-nop-code-2026-05-29-audit-remediation.md`（completed）, `58-nop-code-p0-bug-fixes.md`（completed）

## Purpose

修复 2026-05-29 对抗性审查中新发现的 P0 和 P1 问题（共 11 项），消除 nop-code 模块中静默数据损坏、逻辑反转、内存泄漏和数据完整性风险。P2 逻辑缺陷（10 项）和 P3 代码质量（6 项）不在本 plan scope 内，留作 successor plan。

## Current Baseline

### 已完成计划覆盖范围

- **Plan 69**：深度审计 21 维度 43 项发现——ORM 关系语义、级联删除、nop-code-api 清理、@DataBean、session flush/evict、测试反模式
- **Plan 58**：5 个 P0 bug——CommunityDetector labels、searchCode language filter（仅 searchFullText）、symbol sourceCode、ChangeAnalyzer resource leak、FlowDetector entry point
- **Plan 55**：安全/数据完整性/IoC/ORM 索引等修复
- **Plan 59**：Semantic Edge 模型实现

### API 事实基线（对抗性审查后验证）

- `org.treesitter.TSTree` 无 `close()`/`dispose()` 方法，原生内存依赖 `TSTreeCleanAction`（GC Cleaner）非确定性回收
- `io.github.bonede:tree-sitter-typescript:0.23.2` 中 `TreeSitterTypescript` 同时处理 `.ts` 和 `.tsx`；无独立 `TreeSitterTsx` 类
- `IOrmSession` 有 `evict(IOrmEntity)`（line 90）和 `saveOrUpdate(IOrmEntity)`（line 193），无 `merge()` 方法
- 子模块嵌套在 `nop-code/` 下（如 `nop-code/nop-code-lang-python/`）
- `ProjectAnalyzer` 过滤器 bug 位于 line 254 和 line 329（非 252-261/327-336）
- `saveReplacingExisting` 位于 `CodeIndexService.java` 约 line 2341

### 对抗性审查新发现（未覆盖）

**P0（4 项）——静默数据损坏/逻辑反转**：
- AR-01：Tree-sitter 字节偏移被当作字符偏移使用（Python + TypeScript），非 ASCII 字符时静默数据损坏
- AR-02：VFS 文件过滤器逻辑反转，指定 filePattern 时分析所有文件（含二进制）
- AR-03：增量分析退化为全量——fingerprint 检测有效但分析结果被丢弃
- AR-04：Cohesion 在 CommunityDetector 和 KnowledgeGapAnalyzer 中用完全不同的公式计算

**P1（7 项）——性能/数据完整性/内存/并发**：
- AR-05：getProjectFilePaths 每个文件调用 O(N²) 全表扫描
- AR-06：saveReplacingExisting 的 evictAll 驱逐同类型所有待提交实体
- AR-07：hashCode 碰撞实体 ID 导致静默数据覆盖
- AR-08：buildTypeHierarchy/buildCallHierarchy 无 visited-set，循环继承导致无限递归
- AR-09：FlowDetector.flowCache 使用普通 HashMap，并发损坏
- AR-10：Tree-sitter TSTree/TSParser 原生内存泄漏，无显式释放
- AR-11：TSX 文件使用 TypeScript 语法解析，JSX 语法可能产生错误 AST

**P2（10 项）——逻辑缺陷/API 契约**（不在本 plan scope）：
- AR-12 至 AR-21

**P3（6 项）——代码质量**（不在本 plan scope）：
- AR-22 至 AR-27

## Goals

1. 消除 Tree-sitter 字节/字符偏移混用导致的静默数据损坏（AR-01）
2. 修复 VFS 文件过滤器逻辑反转（AR-02）
3. 使增量分析路径行为明确化——至少显式记录限制，而非静默退化为全量（AR-03）
4. 统一 Cohesion 计算公式，消除同名不同义（AR-04）
5. 消除 O(N²) 全表扫描性能问题（AR-05）
6. 修复 saveReplacingExisting 的 evictAll 数据丢失风险（AR-06）
7. 修复 hashCode 碰撞导致的实体 ID 冲突（AR-07）
8. 为递归层级构建添加 visited-set 防护（AR-08）
9. 修复 FlowDetector HashMap 并发问题（AR-09）
10. 缓解 Tree-sitter 原生内存压力——缓存 parser、及时释放引用（AR-10）
11. 明确 TSX 文件解析策略并修复相关测试（AR-11）

## Non-Goals

- P2 逻辑缺陷修复（AR-12 至 AR-21）→ successor plan
- P3 代码质量改进（AR-22 至 AR-27）→ successor plan
- CodeIndexService God Class 拆分（Plan 55/69 deferred）
- 测试覆盖大幅补充（Plan 55/69 deferred）
- 错误码中文改英文（Plan 55 watch-only）
- `ProjectAnalysisResult` 完整持久化层实现（超出 bug fix scope）
- Tree-sitter 库升级（超出本模块 scope）

## Scope

### In Scope

- AR-01：`nop-code/nop-code-lang-python/` + `nop-code/nop-code-lang-typescript/` 字节/字符偏移修复
- AR-02：`nop-code/nop-code-core/` ProjectAnalyzer 文件过滤器逻辑修复（2 处）
- AR-03：`nop-code/nop-code-core/` ProjectAnalyzer 增量分析路径行为明确化
- AR-04：`nop-code/nop-code-graph/` CommunityDetector + KnowledgeGapAnalyzer cohesion 公式统一
- AR-05：`nop-code/nop-code-service/` CodeIndexService.getProjectFilePaths 缓存优化
- AR-06：`nop-code/nop-code-service/` CodeIndexService.saveReplacingExisting evict 策略修复
- AR-07：`nop-code/nop-code-service/` CodeIndexService + OrmFingerprintStore 实体 ID 生成修复
- AR-08：`nop-code/nop-code-service/` CodeIndexService.buildTypeHierarchy/buildCallHierarchy visited-set
- AR-09：`nop-code/nop-code-flow/` FlowDetector.flowCache 线程安全
- AR-10：`nop-code/nop-code-lang-python/` + `nop-code/nop-code-lang-typescript/` TSParser 缓存 + 引用释放
- AR-11：`nop-code/nop-code-lang-typescript/` TSX 解析策略明确化 + 测试修复

### Out Of Scope

- AR-12 至 AR-27（全部 P2 + P3）
- CodeIndexService God Class 拆分
- 大规模测试补充
- 错误码国际化
- tree-sitter 库版本升级

## Decisions

### D1 — AR-04 Cohesion 公式统一策略

**选择 Strategy A**：两个模块统一使用 `edgeCohesion = internalEdges / (internalEdges + externalEdges)`。

理由：(1) 更直观——0 = 无内部边（不内聚），1 = 无外部边（完全内聚）；(2) 与文献中 "cohesion" 的常见定义一致；(3) CommunityDetector 的 `graphDensity = internalEdges / (n*(n-1))` 公式对大社区值极小（50 节点 10 条内边 = 0.004），缺乏区分度。

### D2 — AR-03 增量分析路径处理

**选择方案 (c)**：使用已计算的 `changes`（added/modified/deleted）列表，仅对变更文件调用 `analyzeFile`，但**不合并旧结果**——而是将变更文件的重新分析结果作为完整输出返回。在 Javadoc 中明确标注："此方法仅重新分析变更文件，但不与历史结果合并。调用方应将返回的结果作为完整结果集使用"。

理由：(1) `ProjectAnalysisResult` 是内存对象，无持久化机制——实现完整持久化是 feature 级别的工作，超出 bug fix scope；(2) 仅过滤变更文件已经是增量分析的核心收益（避免对未变更文件的解析开销）；(3) 不抛 `UnsupportedOperationException`，因为当前行为（全量）不是错误的，只是不优化。

### D3 — AR-10 TSTree 原生内存缓解

**选择缓存 + 显式释放引用方案**：(1) 将 `TSParser` + `TSLanguage` 提取为 `private static final` 字段，避免每个文件重建；(2) 在 `analyzeFile` 方法中，解析完成后将 `tree` 引用设为局部变量，方法结束后立即可被 GC；(3) 在批处理场景（如 `indexDirectory`）中，每处理 N 个文件后插入一个 `tree = null` 赋值提示 GC 优先回收。

理由：`TSTree` 无 `close()` 方法（已验证），无法确定性释放原生内存。缓存 parser 可减少 parser 本身的原生内存创建开销。显式 null 赋值是最少侵入的 GC 提示方式。

### D4 — AR-11 TSX 解析策略

**选择验证 + 文档化方案**：`TreeSitterTypescript`（v0.23.2）声称同时处理 `.ts` 和 `.tsx`。首先编写测试验证 `.tsx` 文件中 JSX 元素的实际解析结果：(a) 如果 `TreeSitterTypescript` 能正确解析 TSX（即 JSX 节点不出现 ERROR），则保留现有行为并修复测试使其验证 JSX 解析正确性；(b) 如果 JSX 节点出现 ERROR，则在 `TypeScriptCodeFileAnalyzer` 中为 `.tsx` 文件添加降级警告日志，且在 `getFileExtensions()` 返回值中移除 `.tsx` 直到找到正确的语法支持。

理由：无独立 `TreeSitterTsx` 类（已验证），添加新 tree-sitter 依赖超出本模块 scope。

## Execution Plan

### Phase 1 - P0 静默数据损坏修复

Status: planned
Targets: `nop-code/nop-code-lang-python/`, `nop-code/nop-code-lang-typescript/`, `nop-code/nop-code-core/src/main/java/io/nop/code/core/analyzer/ProjectAnalyzer.java`, `nop-code/nop-code-graph/`

- Item Types: `Fix`, `Decision`

- [ ] **修复 AR-01**：在 `PythonCodeFileAnalyzer.nodeText()`（line 68）和 `TypeScriptCodeFileAnalyzer.getNodeText()`（line 498-504）中，将 `source.substring(startByte, endByte)` 替换为：`byte[] bytes = source.getBytes(StandardCharsets.UTF_8); new String(bytes, startByte, endByte - startByte, StandardCharsets.UTF_8)`。TypeScript 端同步修复 `endByte > source.length()` 防护为 `endByte > bytes.length`
- [ ] **修复 AR-02**：在 `ProjectAnalyzer.java` line 254 和 line 329 两处过滤器中，将 `return true;` 改为解析 `filePattern` 的扩展名并匹配。具体：当 `filePattern` 非空非 `"*"` 时，提取扩展名（如 `"*.java"` → `".java"`），然后 `return resource.getName().endsWith(extractedExtension);`
- [ ] **修复 AR-03**：在 `ProjectAnalyzer.analyzeIncremental` 的两条路径中（store-based 和 manifest-based），将已计算的 `changes`（addedFiles/modifiedFiles/deletedFiles）作为参数传递给内部分析方法，使分析只处理变更文件。具体：将 `analyzeIncremental(projectRoot, (ProjectAnalysisResult) null)` 改为调用新方法 `analyzeFiles(projectRoot, changes.getAddedFiles() + changes.getModifiedFiles())`，该方法仅对给定文件列表调用 `analyzeFile`。在 Javadoc 中标注增量分析的当前限制
- [ ] **修复 AR-04（Decision D1）**：将 `CommunityDetector.java`（line 767-785）的 cohesion 计算改为 `edgeCohesion = internalEdges / (internalEdges + externalEdges)`，与 `KnowledgeGapAnalyzer.java`（line 67-87）一致。更新 `CommunityDetector` 中调用 cohesion 的所有下游代码中的注释/日志
- [ ] 新增测试 `TestTreeSitterByteOffset`：创建含中文注释的 Python 和 TypeScript 测试源文件，验证符号名称正确提取（非截断/乱码）
- [ ] 新增测试 `TestProjectAnalyzerFileFilter`：验证 `filePattern="*.java"` 时只接受 `.java` 文件，拒绝 `.png`/`.jar`/`.class`
- [ ] 新增测试 `TestCohesionConsistency`：验证 `CommunityDetector` 和 `KnowledgeGapAnalyzer` 对同一社区输出相同的 cohesion 值

Exit Criteria:

- [ ] `PythonCodeFileAnalyzer.nodeText()` 和 `TypeScriptCodeFileAnalyzer.getNodeText()` 使用 `byte[]` 切片而非 `String.substring`
- [ ] TypeScript 端的 `endByte` 上界检查与 `bytes.length` 比较（非 `source.length`）
- [ ] `ProjectAnalyzer` 过滤器（line 254、line 329）在 `filePattern` 非空非 `"*"` 时按扩展名匹配，而非 `return true`
- [ ] `analyzeIncremental` 的两条路径使用变更文件列表（非 `null` 旧结果），且 Javadoc 标注了增量分析的当前限制
- [ ] `CommunityDetector.cohesion` 公式与 `KnowledgeGapAnalyzer` 一致（`internalEdges / (internalEdges + externalEdges)`）
- [ ] 新增至少 3 个测试（byte offset、file filter、cohesion consistency），全部通过
- [ ] `./mvnw test -pl nop-code -am` 通过
- [ ] No owner-doc update required（不改变公共 API 契约）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - P1 数据完整性与并发安全修复

Status: planned
Targets: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java`, `nop-code/nop-code-flow/src/main/java/io/nop/code/flow/FlowDetector.java`, `nop-code/nop-code-service/src/main/java/io/nop/code/service/incremental/OrmFingerprintStore.java`

- Item Types: `Fix`

- [ ] **修复 AR-05**：在 `saveFileResultInSession` 方法中，将 `getProjectFilePaths(indexId)` 调用从循环内（约 line 2251）移至方法开头，调用一次存入局部变量 `Set<String> projectFilePaths`，循环内使用局部变量
- [ ] **修复 AR-06**：在 `saveReplacingExisting`（约 line 2341-2353）中，将 `session.evictAll(entity.orm_entityName())` 替换为：捕获重复键异常后，通过 `session.flush()` 刷出已保存实体，然后用 `session.evict(conflictingEntity)` 只驱逐冲突实体（通过查询 `session.find(entity.orm_entityName(), existingId)` 定位），再执行 `session.saveOrUpdate(entity)` 保存新实体
- [ ] **修复 AR-07**：将 `CodeIndexService` 依赖实体 ID（约 line 2203 `indexId + "_" + Integer.toHexString(key.hashCode())`）改为基于内容的确定性 ID：`indexId + "_" + DigestUtils.sha256Hex(source + "|" + target + "|" + importStatement).substring(0, 16)`。同步修复 `OrmFingerprintStore.java:42` 的 `Math.abs(canonicalPath.hashCode())` 为基于内容的 ID，并移除 `Math.abs()`（改用 `Math.abs((long) hash)` 避免 Integer.MIN_VALUE 问题）
- [ ] **修复 AR-08**：在 `buildTypeHierarchy`（约 line 1149-1192）和 `buildCallHierarchy`（约 line 1205-1253）中添加 `Set<String> visited` 参数（初始为 `new HashSet<>()`），每次递归前检查 `visited.contains(qualifiedName)` 并提前返回，递归调用时 `visited.add(qualifiedName)`。同时在方法入口添加 `maxDepth = Math.min(maxDepth, 50)` 硬上限
- [ ] **修复 AR-09**：在 `FlowDetector.java` line 63，将 `new HashMap<>()` 改为 `new ConcurrentHashMap<>()`
- [ ] 新增测试 `TestGetProjectFilePathsCaching`：验证 `saveFileResultInSession` 批处理中 `getProjectFilePaths` 只被调用一次（可通过 mock 或计数器验证）
- [ ] 新增测试 `TestBuildHierarchyCycleProtection`：创建含循环继承的 mock 数据，验证 `buildTypeHierarchy` 不抛 StackOverflowError 且返回有限深度结果
- [ ] 新增测试 `TestDeterministicEntityIds`：验证相同输入产生相同实体 ID，不同输入产生不同 ID

Exit Criteria:

- [ ] `getProjectFilePaths` 在 `saveFileResultInSession` 内只调用一次（循环外缓存）
- [ ] `saveReplacingExisting` 使用 `session.evict(conflictingEntity)` 而非 `session.evictAll(entity.orm_entityName())`
- [ ] `saveReplacingExisting` 使用 `session.saveOrUpdate(entity)` 处理冲突
- [ ] 依赖实体 ID 使用截断 SHA-256，`OrmFingerprintStore` 使用基于内容的 ID
- [ ] `buildTypeHierarchy`/`buildCallHierarchy` 有 `Set<String> visited` 参数且 `maxDepth` 硬上限 ≤ 50
- [ ] `FlowDetector.flowCache` 为 `ConcurrentHashMap`
- [ ] 新增至少 3 个测试（缓存、循环防护、确定性 ID），全部通过
- [ ] `./mvnw test -pl nop-code -am` 通过
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - P1 内存缓解与 TSX 策略明确化

Status: planned
Targets: `nop-code/nop-code-lang-python/`, `nop-code/nop-code-lang-typescript/`

- Item Types: `Fix`, `Decision`

- [ ] **修复 AR-10（Decision D3）**：在 `PythonCodeFileAnalyzer` 和 `TypeScriptCodeFileAnalyzer` 中：(1) 将 `TSParser` 和 `TSLanguage` 提取为 `private static final` 字段；(2) 在 `analyzeFile`/`analyze` 方法中将 `tree` 保持为局部变量，解析完成后立即提取所需信息，然后 `tree = null` 提示 GC 回收
- [ ] **修复 AR-11（Decision D4）**：首先编写验证测试——在 `TestTypeScriptCodeFileAnalyzer` 中新增测试方法验证 `.tsx` 文件中 JSX 元素（如 `<div>Hello</div>`）的解析结果。根据结果：(a) 如果解析正确，保留现有行为并修复 `testTsxUsesTsxGrammar` 使其断言至少一个 JSX 元素节点被正确解析；(b) 如果出现 ERROR 节点，在 `TypeScriptCodeFileAnalyzer` 中为 `.tsx` 文件添加降级警告，并从 `getFileExtensions()` 中移除 `.tsx`
- [ ] 新增测试：验证 `TSParser` + `TSLanguage` 为 static final（通过反射或行为验证：连续分析多文件不会创建多个 parser）
- [ ] 新增/修复测试：`testTsxUsesTsxGrammar` 验证 JSX 解析正确性（非仅 assertNotNull）

Exit Criteria:

- [ ] `PythonCodeFileAnalyzer` 和 `TypeScriptCodeFileAnalyzer` 中 `TSParser` + `TSLanguage` 为 `private static final` 字段
- [ ] 解析完成后 `tree` 引用被显式设为 null
- [ ] `.tsx` 文件解析策略已明确——要么验证通过并修复测试，要么降级并移除 `.tsx` 支持
- [ ] `testTsxUsesTsxGrammar` 不再是仅 `assertNotNull` 的虚假测试
- [ ] 新增至少 2 个测试（parser 缓存 + TSX 验证），全部通过
- [ ] `./mvnw test -pl nop-code -am` 通过
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] 全部 4 个 P0 发现已修复（AR-01 字节偏移、AR-02 过滤器反转、AR-03 增量退化明确化、AR-04 cohesion 统一）
- [ ] 全部 7 个 P1 发现已修复（AR-05 N+1、AR-06 evictAll、AR-07 hashCode、AR-08 无限递归、AR-09 HashMap、AR-10 内存缓解、AR-11 TSX 策略）
- [ ] 不存在被静默降级到 deferred 的 in-scope live defect
- [ ] No owner-doc update required（不改变公共 API 契约或平台规范）
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 已验证组件间调用链连通、无空壳/静默跳过
- [ ] `./mvnw compile -pl nop-code -am` 通过
- [ ] `./mvnw test -pl nop-code -am` 通过
- [ ] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### P2 逻辑缺陷修复（AR-12 至 AR-21）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 不影响数据完整性和运行时崩溃，属于 API 契约和逻辑准确性改进
- Successor Required: yes
- Successor Path: 待创建 successor plan

### P3 代码质量改进（AR-22 至 AR-27）

- Classification: `watch-only residual`
- Why Not Blocking Closure: 不影响编译和运行时正确性
- Successor Required: no
- Successor Path: N/A

## Non-Blocking Follow-ups

- AR-12：TOCTOU 竞态（synchronized 范围优化）
- AR-13：ChangeAnalyzer 子进程泄漏（process.destroyForcibly）
- AR-14：DocKeywordExtractor O(N²) 上限
- AR-15：SymbolTable.getAll() 遗漏无 qualifiedName 符号
- AR-16：FlowDetector flowId 非确定性
- AR-17：ImpactAnalyzer.extractFilePath 空壳
- AR-18：DeadCodeDetector.resolveFilePath 返回 JSON
- AR-19：ImpactAnalyzer 模糊匹配歧义
- AR-20：searchBySymbolName/searchCombined language filter 遗漏
- AR-21：glob exclude 误匹配

## Closure

Status Note: <<完成或关闭时填写>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent>>
- Evidence: <<task id / daily log link / findings 摘要>>

Follow-up:

- P2 逻辑缺陷修复（AR-12 至 AR-21）→ successor plan
- P3 代码质量改进（AR-22 至 AR-27）→ 可在 successor plan 中一并处理
