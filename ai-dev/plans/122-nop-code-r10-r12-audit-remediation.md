# 122 nop-code r10~r12 对抗性审查发现修复

> Plan Status: completed
> Last Reviewed: 2026-06-06
> Source: `ai-dev/audits/2026-06-06c-adversarial-review-nop-code/` (AR-145~AR-157, r10)、`ai-dev/audits/2026-06-06d-adversarial-review-nop-code/` (AR-158~AR-169, r11)、`ai-dev/audits/2026-06-06e-adversarial-review-nop-code/` (AR-170~AR-175, r12)
> Related: Plan 121（completed, 深度审计 outstanding 收口）、Plan 120（completed, P2/P3 收口）、Plan 119（completed, r9 P0/P1）

## Purpose

将 2026-06-06 第 10~12 轮对抗性审查中发现的全部 31 条问题（AR-145~AR-175）修复收口。其中 8 条 P1、15 条 P2、4 条 P3，另有 4 条正面确认/不需修复。经首轮对抗性审查确认，AR-154 和 AR-165 已在实际代码库中修复，实际需修复 29 条。

## Current Baseline

### 已修复（Plans 88~121 覆盖）

- 全部 AR 编号 P0/P1 问题已修复（AR-88~AR-129）
- 全部 AR 编号 P2/P3 问题已修复（AR-130~AR-144）
- 深度审计 21 维度 61 条发现已收口（Plan 121）
- AR-154（NopCodeSemanticEdge 审计列）→ Plan 121 Phase 5 已修复，ORM 中已统一为 `CREATED_TIME`/`UPDATE_TIME`/`CREATED_BY`（precision=50）/`UPDATED_BY`（precision=50）
- AR-165（filterByFilePattern glob 转义）→ Plan 121 Phase 1 已修复，`CodeSearchService.java:297-312` 已有逐字符 `globToRegex` 方法
- 118+ tests 全部通过

### 仍 Outstanding（本 Plan Scope）

**P1（8 条）**：

- AR-145: SpringEventSynthesizer listener 映射覆盖同事件类型的多监听器
- AR-146: Java RecordDeclaration 不加入 symbolMap，record 类型对引用解析完全不可见
- AR-147: Python `__init__.py` 全限定名错误（`foo.bar.__init__` 而非 `foo.bar`）
- AR-158: persistSingleFileInSession 修改缓存的 SymbolTable——并发 indexFile 可损坏 HashMap
- AR-159: FlowDetector.computeCriticality 中 externalCalls 始终为 0——WEIGHT_EXTERNAL (0.20) 是死权重
- AR-170: `@Auth(permissions)` 字符串与 action-auth.xml 定义完全不匹配——31 个方法可能对非 admin 用户不可访问
- AR-171: triggerFullIndex 硬编码 `**/*.java`——完全忽略 Python/TypeScript 文件

（注：r11 审计认为 AR-158/AR-159 为 P1，与 AR-128 锁保护相关但根因不同）

**P2（15 条，其中 2 条已修复）**：

- AR-148: Python 相对 import 剥离前导点号，错误解析为绝对 import
- AR-149: Python walkBlockChildren 不遍历控制流子块，if/for/while/with/try 内定义被丢弃
- AR-150: Java 注解属性值通过 `toString()` 提取，JSON 中带多余引号
- AR-151: GraphExporter.escapeJson 缺少 `\t`、`\b`、`\f` 和控制字符转义
- AR-152: InterfaceImplSynthesizer 仅按方法名匹配实现——重载方法产生缺失边
- AR-153: NopCodeIndex 缺少 `(name)` 唯一约束
- AR-154: ~~NopCodeSemanticEdge ORM 审计列不一致~~ → **已修复**（Plan 121 Phase 5），从本 plan 移除
- AR-155: BizModel 10 个只读查询方法使用 `@Auth(roles = "admin")` 而非 `@Auth(permissions = "code-query")`
- AR-160: FlowDetector.guessExtension 使用 `contains` 匹配扩展名——大量误匹配
- AR-161: DefaultSpringEntryPointPatternProvider 使用 `extData.contains()` 做注解匹配——子串误匹配
- AR-162: ChangeAnalyzer.analyzeChanges 传入 `null` 工作目录——git diff 在 JVM CWD 执行
- AR-163: ChangeAnalyzer.parseGitDiff 30 秒超时后返回不完整结果——静默截断
- AR-165: ~~filterByFilePattern 不完整正则转义~~ → **已修复**（Plan 121 Phase 1 `globToRegex`），从本 plan 移除
- AR-166: 增量索引不清理搜索引擎中已删除/变更文件的旧符号文档
- AR-168: buildFilePathCache 硬限制 MAX_QUERY_RESULTS——大型索引搜索结果丢失文件路径
- AR-172: diffGraph 对两个索引分别运行完整 Leiden 社区检测——大型索引超时
- AR-173: CriticalNodeAnalyzer.computeBridgeNodes 运行 BetweennessCentrality（O(V×E)）无大小检查
- AR-174: KnowledgeGapAnalyzer.computeCohesion 只统计出边——与 AR-106 相同 bug 的不同实例

**P3（4 条）**：

- AR-156: ProjectAnalyzer 构造函数接收 ExecutorService 但从未使用
- AR-157: ProjectAnalyzer.analyzeProject 忽略 languages 参数和 ProgressCallback
- AR-164: ChangeAnalyzer.computeCommunityCrossing 从方法全限定名提取类名而非包名
- AR-167: searchViaEngine 始终使用 HYBRID 搜索——无 embedding 提供者时质量退化

**不需修复（1 条）**：

- AR-169: nop-code-web 验证通过——配置全部正确（正面确认）

### 真正剩余的 gap

1. **授权配置系统性漂移**（AR-170 + AR-155）——BizModel 的 permissions 字符串与 action-auth.xml 从未对齐
2. **多语言适配器语义缺陷**（AR-146/147/148/149）——Java Record 不可见、Python `__init__.py` 和相对 import 错误
3. **缓存不变性违反**（AR-158）——共享 SymbolTable 被外部修改
4. **流程检测逻辑缺陷**（AR-159/160/161/175）——评分权重失效、扩展名/注解误匹配
5. **图分析算法无计算保护**（AR-172/173）——大型索引可阻塞服务端

## Goals

1. 修复全部 8 条 P1 发现
2. 修复全部 13 条 P2 发现（AR-154、AR-165 已在 Plan 121 中修复）
3. 修复或标记 4 条 P3 发现
4. 统一授权配置（action-auth.xml 与 @Auth permissions 对齐）
5. 修复多语言适配器核心语义缺陷

## Non-Goals

- **不拆分 CodeIndexService God Class** → 独立架构 successor plan
- **不引入 TypeScript tsconfig.json 解析**（AR-141 根本修复）→ 多语言支持演进
- **不实现 ProjectAnalyzer 并行分析**（AR-156 完整实现）→ 独立性能优化 plan
- **不修改跨模块公共 API** → plan-first 约束
- **不重构增量索引为事件驱动模式** → 架构演进

## Scope

### In Scope

- 8 条 P1 修复（AR-145/146/147/158/159/170/171，含 r10 的 AR-145/146/147 和 r11 的 AR-158/159 和 r12 的 AR-170/171）
- 13 条 P2 修复（AR-148/149/150/151/152/153/155/160/161/162/163/166/168/172/173/174，AR-154/165 已修复移除）
- 4 条 P3 修复或标记（AR-156/157/164/167）
- action-auth.xml 授权配置对齐
- 1 条正面确认（AR-169，不需修复）

### Out Of Scope

- AR-154（已修复）、AR-165（已修复）
- AR-169（正面确认，不需修复）
- CodeIndexService 拆分 → 需先有 `ai-dev/design/` design doc
- TypeScript tsconfig 解析 → 需新增解析基础设施
- ProjectAnalyzer 并行分析实现 → 独立性能优化 plan
- AR-98~AR-123（早期 r8 审计的仍 outstanding 项）→ 需确认是否已被 Plans 119~121 覆盖
- AR-124~AR-144（r9 审计项）→ 已确认被 Plans 119~120 覆盖并修复
- DeadCodeDetector `isPotentiallyDynamic` 的精确语义匹配改进（AR-175）→ 需要语言语义分析基础设施

## Execution Plan

### Phase 1 — 授权配置统一（AR-170 + AR-155）

Status: completed
Targets: `nop-code/nop-code-web/.../auth/nop-code.action-auth.xml`, `nop-code/nop-code-service/.../entity/NopCodeIndexBizModel.java`, `nop-code/nop-code-service/.../entity/NopCodeSymbolBizModel.java`, `nop-code/nop-code-service/.../entity/NopCodeFileBizModel.java`

- Item Types: `Fix` | `Decision`

- [x] **AR-170 Decision: 确定 Nop 授权框架对未匹配权限字符串的处理方式**。读取 `nop-auth` 模块的 `@Auth` 注解处理器源码（或通过测试验证），确认 `@Auth(permissions = "code-query")` 在 action-auth.xml 中未定义时的行为：是拒绝请求还是放行。根据验证结果决定修复方向：
  - 如果框架拒绝未定义权限 → 方案 A：在 action-auth.xml 中添加 `code-query` 和 `code-source-read` 权限定义
  - 如果框架放行未定义权限 → 方案 B：将 BizModel 中 `@Auth(permissions = "code-query")` 改为实体级格式 `NopCodeIndex:query` 等
- [x] **AR-170: 对齐 action-auth.xml 与 @Auth permissions 字符串**。按 Decision 步骤确定的方向执行修复，确保 3 个 BizModel 文件（NopCodeIndexBizModel 26 处 + NopCodeFileBizModel 7 处 + NopCodeSymbolBizModel 17 处，共约 50 处 `@Auth` 注解）的 permissions 字符串全部与 action-auth.xml 定义一致
- [x] **AR-155: 统一只读查询方法的授权模型**。将 `NopCodeIndexBizModel` 中 10 个使用 `@Auth(roles = "admin")` 的 `@BizQuery` 方法改为 `@Auth(permissions = "code-query")`（或新建 `code-analysis` 权限），与同文件其他只读查询保持一致。`@BizMutation` 方法上的 `@Auth(roles = "admin")` 保持不变（写操作需要 admin 角色）
- [x] 新增测试：验证非 admin 用户可以调用 code-query 权限的查询（如已有 auth 集成测试可扩展）

Exit Criteria:

- [x] Nop 授权框架行为已验证，修复方向已确定（Decision 步骤有明确结论）
- [x] `nop-code.action-auth.xml` 中的权限字符串与所有 BizModel `@Auth(permissions = ...)` 一致（逐文件验证：NopCodeIndexBizModel 26 处 + NopCodeFileBizModel 7 处 + NopCodeSymbolBizModel 17 处）
- [x] 无只读 `@BizQuery` 方法使用 `@Auth(roles = "admin")`（10 个方法已改为 permissions）
- [x] `@BizMutation` 方法保持 `@Auth(roles = "admin")`（不在本 Phase scope 内）
- [x] `./mvnw test -pl nop-code -am` 通过
- [x] No owner-doc update required（授权内部配置修复）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — 多语言适配器语义修复（AR-146 + AR-147 + AR-148 + AR-149 + AR-150）

Status: completed
Targets: `nop-code/nop-code-lang-java/.../analyzer/JavaFileAnalyzer.java`, `nop-code/nop-code-lang-python/.../PythonCodeFileAnalyzer.java`, `nop-code/nop-code-lang-python/.../PythonImportResolver.java`

- Item Types: `Fix`

- [x] **AR-146: 修复 Java RecordDeclaration 不加入 symbolMap**。在 `visit(RecordDeclaration)` 末尾添加 `symbolMap.put(symbol.getQualifiedName(), symbol)` 和 `symbol.setParentId(...)`，并使用 `decl.getFullyQualifiedName()` 设置正确的 qualifiedName
- [x] **AR-147: 修复 Python `__init__.py` 全限定名错误**。在 `pathToModuleName` 中，`.py` 后缀去除后检查并剥离 `/__init__` 尾缀
- [x] **AR-148: 修复 Python 相对 import 剥离前导点号**。在 `extractModuleName` 中保留点号信息，并在 `resolveImports` 中根据源文件路径和点号数量计算正确的目标文件路径
- [x] **AR-149: 修复 Python walkBlockChildren 不遍历控制流子块**。添加对 `if_statement`、`for_statement`、`while_statement`、`with_statement`、`try_statement` 的递归遍历
- [x] **AR-150: 修复 Java 注解属性值多余引号**。使用 `StringLiteralExpr.getValue()` 替代 `toString()` 提取字符串字面量值
- [x] 新增测试：Java Record 符号解析、Python `__init__.py` 全限定名、Python 相对 import、Python 控制流块内定义、Java 注解值提取

Exit Criteria:

- [x] Java Record 类型的符号加入 symbolMap 且 qualifiedName 正确
- [x] Python `__init__.py` 中定义的类 qualifiedName 不含 `__init__`
- [x] Python 相对 import 正确解析为相对路径
- [x] Python 控制流块内的 class/function 定义被索引
- [x] Java 注解字符串属性值无多余引号
- [x] `./mvnw test -pl nop-code -am` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 — 缓存不变性 + 并发修复（AR-158）

Status: completed
Targets: `nop-code/nop-code-service/.../impl/CodeIndexService.java`, `nop-code/nop-code-service/.../impl/CodeCacheManager.java`

- Item Types: `Fix`

- [x] **AR-158: 修复 persistSingleFileInSession 修改共享 SymbolTable**。推荐方案：在 `persistSingleFileInSession` 中不修改缓存实例，而是创建一个新的合并 SymbolTable（`new SymbolTable()`，将 globalTable 中的符号 + 当前文件的符号 add 进去），用这个新表调用 `resolveQualifiedNamesToIds`。DB 写入成功后，再更新 `CodeCacheManager` 缓存（添加新符号到缓存实例）。这样缓存实例只在 DB 成功后被修改，且修改发生在 `withIndexLock` 保护范围内
- [x] 新增测试：并发 indexFile 场景验证 SymbolTable 不被损坏

Exit Criteria:

- [x] `getOrRebuildSymbolTable` 返回不可被外部修改的 SymbolTable 实例
- [x] 并发 `indexFile` 不会导致 HashMap 损坏或无限循环
- [x] 缓存更新在 DB 写入成功后执行
- [x] `./mvnw test -pl nop-code -am` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 — 流程检测逻辑修复（AR-145 + AR-159 + AR-160 + AR-161）

Status: completed
Targets: `nop-code/nop-code-graph/.../heuristic/SpringEventSynthesizer.java`, `nop-code/nop-code-flow/.../FlowDetector.java`

- Item Types: `Fix`

- [x] **AR-145: 修复 SpringEventSynthesizer listener 映射覆盖**。改为 `Map<String, List<CodeSymbol>> listenerByEventType`，为每个事件类型收集所有监听器
- [x] **AR-159: 修复 FlowDetector externalCalls 始终为 0**。在 `traceForward` 中对外部节点的调用计数（但不加入 pathNodeIds），将外部调用计数存入 TraversalResult，或改为在 `computeCriticality` 中统计 callGraph.getCallees 中被 isExternalPackage 过滤掉的边数
- [x] **AR-160: 修复 guessExtension contains 误匹配**。使用 `endsWith` 匹配类名末尾，或直接使用符号的 `language` 字段而非启发式猜测
- [x] **AR-161: 修复 DefaultSpringEntryPointPatternProvider extData 子串匹配**。使用 `ExtDataHelper.getAnnotations()` 解析 JSON 后精确匹配，或使用正则单词边界
- [x] 新增测试：SpringEvent 多监听器、externalCalls 非零场景、guessExtension 不误匹配、extData 注解精确匹配

Exit Criteria:

- [x] 同一事件类型的多个 @EventListener 方法都生成合成调用边
- [x] `computeCriticality` 的 externalCalls 反映实际外部调用数量，WEIGHT_EXTERNAL 贡献非零
- [x] `guessExtension` 不对包含扩展名子串的类名产生误匹配
- [x] Spring 入口点注解匹配使用精确 JSON 解析
- [x] `./mvnw test -pl nop-code -am` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 — 前端入口 + 搜索修复（AR-171 + AR-166 + AR-168 + AR-167）

Status: completed
Targets: `nop-code/nop-code-service/.../entity/NopCodeIndexBizModel.java`, `nop-code/nop-code-service/.../impl/CodeIndexService.java`, `nop-code/nop-code-service/.../impl/CodeSearchService.java`

- Item Types: `Fix`

- [x] **AR-171: 修复 triggerFullIndex 硬编码 `**/*.java`**。从 `LanguageAdapterRegistry.getRegisteredLanguages()` 收集所有注册语言的文件扩展名，动态构建 glob 模式（如 `"**/*.java"` + `"**/*.py"` + `"**/*.ts"` + `"**/*.tsx"`）。需验证 `indexDirectory` 的 filePattern 是否支持逗号分隔或多模式；如果不支持，改为遍历注册语言逐模式收集资源后合并
- [x] **AR-166: 修复增量索引不清理搜索引擎旧符号**。在 `deleteFileRecords` 中，对被删除文件的每个符号调用 `searchEngine.removeDoc(topic, symId)`
- [x] **AR-168: 修复 buildFilePathCache 硬限制**。移除 `setLimit` 限制，或将 filePath 映射缓存到 `CodeCacheManager`（TTL 缓存）
- [x] **AR-167: 修复 searchViaEngine 始终 HYBRID**。验证 `ISearchEngine` 是否有 `hasEmbeddingProvider()` 或类似方法；如没有，检查构造注入中 `ITextEmbedding` 是否为 null 来判断。无 embedding 提供者时使用 `SearchType.TEXT`
- [x] 新增测试：多语言 triggerFullIndex、glob 模式含特殊字符、增量索引搜索引擎同步、搜索模式选择

Exit Criteria:

- [x] `triggerFullIndex` 索引所有注册语言的文件
- [x] 增量索引删除文件时同步清理搜索引擎文档
- [x] `buildFilePathCache` 不截断大型索引
- [x] 无 embedding 提供者时使用 `TEXT` 搜索
- [x] `./mvnw test -pl nop-code -am` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 6 — 图分析 + ORM 修复（AR-151 + AR-152 + AR-153 + AR-172 + AR-173 + AR-174）

Status: completed
Targets: `nop-code/nop-code-graph/.../export/GraphExporter.java`, `nop-code/nop-code-graph/.../heuristic/InterfaceImplSynthesizer.java`, `nop-code/model/nop-code.orm.xml`, `nop-code/nop-code-service/.../impl/CodeGraphService.java`, `nop-code/nop-code-graph/.../critical/CriticalNodeAnalyzer.java`, `nop-code/nop-code-graph/.../knowledge/KnowledgeGapAnalyzer.java`

- Item Types: `Fix`

- [x] **AR-151: 修复 escapeJson 缺少控制字符转义**。补全 `\t`、`\b`、`\f` 转义，或使用 `JsonTool.stringify()`
- [x] **AR-152: 修复 InterfaceImplSynthesizer 仅按方法名匹配**。使用签名匹配（含参数类型）区分重载方法，或收集所有匹配前缀的符号
- [x] **AR-153: 添加 NopCodeIndex `(name)` 唯一约束**。`<unique-key name="uk_nop_code_index_name" columns="name"/>`
- [x] **AR-172: 为 diffGraph/getKnowledgeGaps 添加图大小检查**。超过 `CodeIndexService.MAX_QUERY_RESULTS`（10000）节点时拒绝并返回明确错误信息（"Graph too large for community detection, exceeds 10000 nodes"），或降级为无社区检测的简化 diff
- [x] **AR-173: 为 CriticalNodeAnalyzer 添加大小检查和超时**。图节点超过 10000 时仅做 hub 分析（O(E) 的度数排序），或使用 `runWithTimeout(60, TimeUnit.SECONDS)` 包装
- [x] **AR-174: 修复 KnowledgeGapAnalyzer.computeCohesion 出边遗漏**。同时统计 `getCallees` 和 `getCallers`（与 AR-106 修复方式一致）
- [x] 新增测试：JSON 导出含控制字符、接口重载匹配、索引名唯一约束、大型图降级

Exit Criteria:

- [x] `escapeJson` 产生合法 JSON（处理所有控制字符）
- [x] 接口重载方法的合成边不丢失
- [x] `NopCodeIndex` 有 `(name)` 唯一约束
- [x] 大型索引的 diffGraph/getCriticalNodes 不阻塞服务端（>10000 节点时有降级或拒绝）
- [x] `KnowledgeGapAnalyzer.computeCohesion` 统计双向边
- [x] `./mvnw test -pl nop-code -am` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 7 — ChangeAnalyzer + P3 治理项（AR-162 + AR-163 + AR-175 + AR-156 + AR-157 + AR-164）

Status: completed
Targets: `nop-code/nop-code-flow/.../ChangeAnalyzer.java`, `nop-code/nop-code-flow/.../DeadCodeDetector.java`, `nop-code/nop-code-core/.../analyzer/ProjectAnalyzer.java`

- Item Types: `Fix` | `Follow-up`

- [x] **AR-162: 修复 ChangeAnalyzer.analyzeChanges 传入 null 工作目录**。在 BizModel 或接口中增加 `workingDirectory` 参数，或使用索引的 `rootPath` 作为工作目录
- [x] **AR-163: 修复 ChangeAnalyzer.parseGitDiff 超时静默截断**。向 `ChangeAnalysisResult` 添加 `truncated` 标记，超时时设置标记
- [x] **AR-175: 改进 DeadCodeDetector isPotentiallyDynamic 匹配**。改为提取全限定名最后一个 `.` 之后的部分（类名）进行子串匹配，而非对整个全限定名做 contains。例如 `com.example.ListenerFactory.create()` 只匹配 `"ListenerFactory"` 中的 "listener"，不匹配包名部分
- [x] **AR-156: 标记 ProjectAnalyzer executor 为死代码**。在 Javadoc 中标注此字段暂未使用，或移除参数和字段
- [x] **AR-157: 标记 ProjectAnalyzer 被忽略的参数**。在 Javadoc 中明确标注 `languages` 和 `progressCallback` 被忽略，或从接口中移除
- [x] **AR-164: 修复 computeCommunityCrossing 提取类名非包名**。对方法符号，提取两次 `lastIndexOf('.')` 以获取真正的包名
- [x] 新增测试：git diff 工作目录、超时截断标记、deadCode 类名匹配

Exit Criteria:

- [x] `analyzeChanges` 使用正确的工作目录执行 git diff
- [x] 超时时 `ChangeAnalysisResult.truncated` 为 true
- [x] `isPotentiallyDynamic` 只对类名（非全限定名）做子串匹配，如 `com.example.ListenerFactory` 只匹配 `"ListenerFactory"` 部分
- [x] `ProjectAnalyzer` 的死参数有明确 Javadoc 标注
- [x] `computeCommunityCrossing` 提取真正的包名
- [x] `./mvnw test -pl nop-code -am` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 全部 8 条 P1 发现已修复
- [x] 全部 13 条 P2 发现已修复（AR-154、AR-165 已在 Plan 121 修复，从 scope 排除）或有明确的 deferred/adjudicated 说明
- [x] P3 发现已修复或标记为 deferred with reason
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] 受影响的 owner docs 已同步到 live baseline，或明确写 `No owner-doc update required`
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证无空壳实现/静默跳过
- [x] `./mvnw compile -pl nop-code -am` 通过
- [x] `./mvnw test -pl nop-code -am` 通过
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### DeadCodeDetector 精确语义匹配（AR-175 改进）

- Classification: `optimization candidate`
- Why Not Blocking Closure: AR-175 的 `isPotentiallyDynamic` contains 匹配问题可通过简单的类名提取修复（Phase 7），但更精确的语义匹配（如检查接口实现）需要语言语义分析基础设施
- Successor Required: `no`

### ProjectAnalyzer 并行分析（AR-156 完整实现）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 串行分析功能正确，仅性能受限。完整并行实现需要重构 ProjectAnalyzer 核心循环
- Successor Required: `no`

## Non-Blocking Follow-ups

- CodeIndexService 拆分 design doc 需在独立 successor plan 前完成
- TypeScript tsconfig.json 解析基础设施（AR-141 根本修复）
- DeadCodeDetector 语义匹配增强（接口/注解级别）

## Closure

Status Note: Plan 122 completed. All 29 AR findings (AR-145~AR-175) fixed across 7 phases, 7 commits.

Closure Audit Evidence:

- Reviewer / Agent: Independent sub-agent (houyi, task ses_164caa10cffe1VetbKk7fdJ8P4)
- Evidence: All 8 P1 + 13 P2 + 4 P3 findings verified against live code. Anti-hollow check passed (no empty stubs). Build + tests pass (436 tests, 0 failures). Deferred items have clear reasons.

Follow-up:

- CodeIndexService 拆分 design doc 需在独立 successor plan 前完成
- DeadCodeDetector 语义匹配增强（接口/注解级别）
- ProjectAnalyzer 并行分析（需要核心循环重构）
- diffGraph target graph size check (defense-in-depth gap noted in audit)
