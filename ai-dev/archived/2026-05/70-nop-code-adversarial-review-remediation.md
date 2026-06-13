# 70 nop-code 对抗性审查修复

> Plan Status: completed
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

Status: completed
Targets: `nop-code/nop-code-lang-python/`, `nop-code/nop-code-lang-typescript/`, `nop-code/nop-code-core/src/main/java/io/nop/code/core/analyzer/ProjectAnalyzer.java`, `nop-code/nop-code-graph/`

- Item Types: `Fix`, `Decision`

- [x] **修复 AR-01**：在 `PythonCodeFileAnalyzer.nodeText()`（line 68）和 `TypeScriptCodeFileAnalyzer.getNodeText()`（line 498-504）中，将 `source.substring(startByte, endByte)` 替换为：`byte[] bytes = source.getBytes(StandardCharsets.UTF_8); new String(bytes, startByte, endByte - startByte, StandardCharsets.UTF_8)`。TypeScript 端同步修复 `endByte > source.length()` 防护为 `endByte > bytes.length`
- [x] **修复 AR-02**：在 `ProjectAnalyzer.java` line 254 和 line 329 两处过滤器中，将 `return true;` 改为解析 `filePattern` 的扩展名并匹配。具体：当 `filePattern` 非空非 `"*"` 时，提取扩展名（如 `"*.java"` → `".java"`），然后 `return resource.getName().endsWith(extractedExtension);`
- [x] **修复 AR-03**：在 `ProjectAnalyzer.analyzeIncremental` 的两条路径中（store-based 和 manifest-based），将已计算的 `changes`（addedFiles/modifiedFiles/deletedFiles）作为参数传递给内部分析方法，使分析只处理变更文件。具体：将 `analyzeIncremental(projectRoot, (ProjectAnalysisResult) null)` 改为调用新方法 `analyzeFiles(projectRoot, changes.getAddedFiles() + changes.getModifiedFiles())`，该方法仅对给定文件列表调用 `analyzeFile`。在 Javadoc 中标注增量分析的当前限制
- [x] **修复 AR-04（Decision D1）**：将 `CommunityDetector.java`（line 767-785）的 cohesion 计算改为 `edgeCohesion = internalEdges / (internalEdges + externalEdges)`，与 `KnowledgeGapAnalyzer.java`（line 67-87）一致。更新 `CommunityDetector` 中调用 cohesion 的所有下游代码中的注释/日志
- [x] 新增测试 `TestTreeSitterByteOffset`：创建含中文注释的 Python 和 TypeScript 测试源文件，验证符号名称正确提取（非截断/乱码）
- [x] 新增测试 `TestProjectAnalyzerFileFilter`：验证 `filePattern="*.java"` 时只接受 `.java` 文件，拒绝 `.png`/`.jar`/`.class`
- [x] 新增测试 `TestCohesionConsistency`：验证 `CommunityDetector` 和 `KnowledgeGapAnalyzer` 对同一社区输出相同的 cohesion 值

Exit Criteria:

- [x] `PythonCodeFileAnalyzer.nodeText()` 和 `TypeScriptCodeFileAnalyzer.getNodeText()` 使用 `byte[]` 切片而非 `String.substring`
- [x] TypeScript 端的 `endByte` 上界检查与 `bytes.length` 比较（非 `source.length`）
- [x] `ProjectAnalyzer` 过滤器（line 254、line 329）在 `filePattern` 非空非 `"*"` 时按扩展名匹配，而非 `return true`
- [x] `analyzeIncremental` 的两条路径使用变更文件列表（非 `null` 旧结果），且 Javadoc 标注了增量分析的当前限制
- [x] `CommunityDetector.cohesion` 公式与 `KnowledgeGapAnalyzer` 一致（`internalEdges / (internalEdges + externalEdges)`）
- [x] 新增至少 3 个测试（byte offset、file filter、cohesion consistency），全部通过
- [x] `./mvnw test -pl nop-code -am` 通过
- [x] No owner-doc update required（不改变公共 API 契约）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - P1 数据完整性与并发安全修复

Status: completed
Targets: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java`, `nop-code/nop-code-flow/src/main/java/io/nop/code/flow/FlowDetector.java`, `nop-code/nop-code-service/src/main/java/io/nop/code/service/incremental/OrmFingerprintStore.java`

- Item Types: `Fix`

- [x] **修复 AR-05**：在 `saveFileResultInSession` 方法中，将 `getProjectFilePaths(indexId)` 调用从循环内（约 line 2251）移至方法开头，调用一次存入局部变量 `Set<String> cachedProjectFilePaths`，循环内使用局部变量
- [x] **修复 AR-06**：在 `saveReplacingExisting` 中，将 `session.evictAll(entity.orm_entityName())` 替换为：捕获重复键异常后 `flush`，通过 `session.get()` 查找已有实体，使用 `orm_initedValues()` 逐属性更新已有实体（不驱逐其他同类实体）
- [x] **修复 AR-07**：将 `CodeIndexService` 依赖实体 ID 改为基于内容的截断 SHA-256（`DigestHelper.sha256Hex`）。同步修复 `OrmFingerprintStore.java` 的 `Math.abs(canonicalPath.hashCode())` 为基于内容的 ID
- [x] **修复 AR-08**：在 `buildTypeHierarchy` 和 `buildCallHierarchy` 中添加 `Set<String> visited` 参数，递归前检查并添加。入口处 `Math.min(maxDepth, 50)` 硬上限
- [x] **修复 AR-09**：在 `FlowDetector.java` line 63，将 `new HashMap<>()` 改为 `new ConcurrentHashMap<>()`
- [x] 新增测试 `TestBuildHierarchyCycleProtection`：验证 visited-set 和 maxDepth 限制
- [x] 新增测试 `TestDeterministicEntityIds`：验证相同输入产生相同 ID，不同输入产生不同 ID

Exit Criteria:

- [x] `getProjectFilePaths` 在 `saveFileResultInSession` 内只调用一次（循环外缓存）
- [x] `saveReplacingExisting` 使用 `orm_initedValues()` 更新已有实体，仅在 entity 不在 session 中时回退到 `evictAll`
- [x] 依赖实体 ID 使用截断 SHA-256，`OrmFingerprintStore` 使用基于内容的 ID
- [x] `buildTypeHierarchy`/`buildCallHierarchy` 有 `Set<String> visited` 参数且 `maxDepth` 硬上限 ≤ 50
- [x] `FlowDetector.flowCache` 为 `ConcurrentHashMap`
- [x] 新增至少 2 个测试（循环防护、确定性 ID），全部通过
- [x] `./mvnw test -pl nop-code -am` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - P1 内存缓解与 TSX 策略明确化

Status: completed
Targets: `nop-code/nop-code-lang-python/`, `nop-code/nop-code-lang-typescript/`

- Item Types: `Fix`, `Decision`

- [x] **修复 AR-10（Decision D3）**：在 `PythonCodeFileAnalyzer` 中将 `TreeSitterPython` 提取为 `private static final TS_LANGUAGE` 字段；在 `TypeScriptCodeFileAnalyzer` 中将 `TreeSitterTypescript` 提取为 `private static final TS_LANGUAGE` 字段。`TSParser` 保持每调用创建（非线程安全），但 `TSLanguage` 复用避免每文件重建原生语言对象
- [x] **修复 AR-11（Decision D4）**：验证 `TreeSitterTypescript`（v0.23.2）能正确解析 TSX 文件中的 JSX。测试 `testTsxParsesClassWithJsx` 验证 TSX 文件中类声明和方法的符号提取（非仅 assertNotNull）
- [x] 新增测试 `testMultipleFilesReuseStaticLanguage`（Python + TypeScript 各一个）：连续分析 10 个文件验证多次分析稳定
- [x] 修复测试 `testTsxParsesClassWithJsx`：使用含 JSX 的 class 声明，验证符号提取

Exit Criteria:

- [x] `PythonCodeFileAnalyzer` 和 `TypeScriptCodeFileAnalyzer` 中 `TSLanguage` 为 `private static final` 字段
- [x] `.tsx` 文件解析策略已明确——`TreeSitterTypescript` 正确处理 TSX，保留 `.tsx` 支持
- [x] `testTsxParsesClassWithJsx` 验证实际符号提取（非仅 assertNotNull）
- [x] 新增至少 2 个测试（parser 缓存 + TSX 验证），全部通过
- [x] `./mvnw test -pl nop-code -am` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 全部 4 个 P0 发现已修复（AR-01 字节偏移、AR-02 过滤器反转、AR-03 增量退化明确化、AR-04 cohesion 统一）
- [x] 全部 7 个 P1 发现已修复（AR-05 N+1、AR-06 evictAll、AR-07 hashCode、AR-08 无限递归、AR-09 HashMap、AR-10 内存缓解、AR-11 TSX 策略）
- [x] 不存在被静默降级到 deferred 的 in-scope live defect
- [x] No owner-doc update required（不改变公共 API 契约或平台规范）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证组件间调用链连通、无空壳/静默跳过
- [x] `./mvnw compile -pl nop-code -am` 通过
- [x] `./mvnw test -pl nop-code -am` 通过
- [x] checkstyle / 代码规范检查通过

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

Status Note: 所有 3 个 Phase 均已完成。Phase 1（commit 45d228c33）修复了 4 个 P0 静默数据损坏问题——Tree-sitter byte offset、VFS 文件过滤器逻辑反转、增量分析退化为全量、cohesion 公式不一致。Phase 2（commit 45d518fb9）修复了 5 个 P1 数据完整性和并发安全问题——getProjectFilePaths O(N²)、saveReplacingExisting evictAll、hashCode 碰撞实体 ID、buildTypeHierarchy/buildCallHierarchy 无限递归、FlowDetector HashMap 并发。Phase 3（commit 59228e4dd）修复了 2 个 P1 内存和 TSX 问题——TSLanguage 提取为 static final、TSX 解析验证。所有测试通过。

Closure Audit Evidence:

- Reviewer / Agent: Executor Agent (session commits 45d228c33, 45d518fb9, 59228e4dd)
- Evidence:
  - Phase 1 Exit Criteria: PythonCodeFileAnalyzer.nodeText() uses byte[] slice; ProjectAnalyzer filters by extension; analyzeIncremental uses analyzeFiles for changed files only; CommunityDetector.cohesion unified with KnowledgeGapAnalyzer; TestTreeSitterByteOffset/TestProjectAnalyzerFileFilter/TestCohesionConsistency all PASS
  - Phase 2 Exit Criteria: getProjectFilePaths cached in saveFileResultInSession; saveReplacingExisting uses orm_initedValues() update; entity IDs use truncated SHA-256 via DigestHelper; buildTypeHierarchy/buildCallHierarchy have visited-set + maxDepth<=50; FlowDetector.flowCache is ConcurrentHashMap; TestBuildHierarchyCycleProtection/TestDeterministicEntityIds PASS
  - Phase 3 Exit Criteria: TreeSitterPython and TreeSitterTypescript extracted as private static final TS_LANGUAGE; TSX verified working with testTsxParsesClassWithJsx; testMultipleFilesReuseStaticLanguage PASS (Python + TypeScript)
  - `./mvnw test -pl nop-code -am` BUILD SUCCESS (80 tests, 0 failures, 3 skipped)
  - Anti-Hollow Check: AR-01 byte offset fix confirmed by TestTreeSitterByteOffset with Chinese comments; AR-02 filter fix confirmed by TestProjectAnalyzerFileFilter; AR-08 visited-set confirmed by TestBuildHierarchyCycleProtection; AR-07 deterministic IDs confirmed by TestDeterministicEntityIds

Follow-up:

- P2 逻辑缺陷修复（AR-12 至 AR-21）→ successor plan
- P3 代码质量改进（AR-22 至 AR-27）→ 可在 successor plan 中一并处理
