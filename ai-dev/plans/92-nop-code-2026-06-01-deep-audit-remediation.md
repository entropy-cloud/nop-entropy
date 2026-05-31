# 92 nop-code 2026-06-01 Deep Audit Remediation

> Plan Status: in progress
> Last Reviewed: 2026-06-01
> Source: `ai-dev/audits/2026-06-01-deep-audit-nop-code-full/summary.md` + `ai-dev/audits/2026-06-01-deep-audit-nop-code/summary.md`
> Related: Plans 55, 58, 59, 69–72, 88–91 (prior nop-code audit remediations, all completed)

## Purpose

将 2026-06-01 两轮深度审计（`-full/` 17 维度 + 主审计 21 维度）发现的 75 条去重 findings 收口到已修复、已裁定为 residual、或已移出 scope 的状态。

## Current Baseline

- nop-code 模块含 13 个子模块、241 个 Java 文件、约 33K 行代码
- 19 份历史修复计划（55, 58, 59, 69–72, 88–91 等）均已 completed
- 生成管线完整闭合、依赖图无循环、IoC 注入合规（历史修复成果仍成立）
- 本次审计新发现 75 条去重 findings：6 P1、35 P2、34 P3
- 核心风险集中在：(1) ORM 模型关系声明不完整 (2) 安全控制遗漏 (3) CodeIndexService God Object (4) 代码重复 (5) 测试薄弱

## Goals

- 修复所有 P1 findings（6 条）
- 修复所有确认的 P2 live defects 和 functional defects
- 裁定所有 P2 Decision 类 findings（明确采纳方案或移出 scope）
- 裁定所有 P3 findings（修复或 adjudicate 为 residual）
- 无新增空壳实现、无静默跳过

## Non-Goals

- 不重构 nop-code-api 空壳模块结构（F-29, F-30）：跨模块结构变更需单独 plan
- 不拆分 CodeIndexService（F-08, F-34）：属于大型重构，收益/风险比需独立评估，移入 successor plan
- 不重做 i18n 英文翻译（F-13）：不影响功能正确性，属于 out-of-scope improvement
- 不修改 tree-sitter JNI 集成或性能调优（审计盲区，不属于 findings）
- 不处理 P3 测试反模式（F-62, F-63, F-72, F-73, F-74）：不影响正确性，属于优化项

## Scope

### In Scope

- P1 全部 6 条 findings
- P2 Fix 类型 findings（15 条）
- P2 Decision 类型 findings 裁定（12 条）
- P2 Follow-up 类型 findings 裁定（5 条）
- P3 中影响功能正确性的 Fix 类型 findings（15 条）
- P3 Follow-up 类型 findings 裁定（19 条）

### Out Of Scope

- CodeIndexService 大规模拆分（God Object 重构）→ successor plan
- nop-code-api 模块结构重组 → successor plan
- i18n 英文翻译（34 个 null 条目）→ optimization candidate
- 性能测试、并发安全深挖 → audit blind spot, not a finding
- P3 测试反模式纯 getter/setter 测试等 → optimization candidate

## Execution Plan

### Phase 1 — P1 Security & Correctness Fixes

Status: completed
Targets: `NopCodeIndexBizModel.java`, `nop-code/model/nop-code.orm.xml`, `TestCacheEviction.java`

- Item Types: `Fix`

- [x] F-02: 为 `NopCodeIndexBizModel.detectFlows()` @BizMutation 添加 `@Auth(roles="admin")`（当前唯一无权限的 mutation）
- [x] F-03: 为 `NopCodeIndexBizModel` 中 17 个无 @Auth 的 @BizQuery 方法添加权限注解。策略：compute-intensive 分析操作添加 `@Auth(roles="admin")`（执行时按实际方法逐一确认，参考标准：涉及图算法、全量扫描、密集计算的查询）；普通查询操作添加 `@Auth(permissions="code-query")`（已有先例：sourceCode 使用 `code-source-read`）
- [x] F-01: 在 `nop-code.orm.xml` NopCodeSymbol 的 relations 中补充 4 个缺失的反向 to-many 关系：callees、callers（from NopCodeCall via callerId/calleeId）、superTypes、subTypes（from NopCodeInheritance via parentTypeId/childTypeId）。注意：enclosingUsages 已由 codegen 从 NopCodeUsage.enclosingSymbol 的 refPropName 自动生成（见 `_app.orm.xml:395`），无需手动添加
- [x] F-06: 重写 `TestCacheEviction`：先调用 detectFlows() 填充缓存，再调用 invalidateCache()，断言缓存被正确驱逐

Exit Criteria:

- [x] `detectFlows()` 带有 `@Auth(roles="admin")` 注解
- [x] NopCodeIndexBizModel 的 compute-intensive @BizQuery 方法（图算法、全量扫描等）带有 `@Auth(roles="admin")`，普通 @BizQuery 带有 `@Auth(permissions="code-query")`
- [x] NopCodeSymbol ORM relations 包含 callees, callers, superTypes, subTypes 四个 to-many 关系（enclosingUsages 已由 codegen 自动生成，无需手动添加）
- [x] TestCacheEviction 测试填充缓存后验证驱逐行为，删除驱逐逻辑会使测试失败
- [x] `./mvnw test -pl nop-code -am` 通过
- [x] No owner-doc update required（修复为局部改动，不涉及 owner doc 变更）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — P2 ORM Model Fixes & BizModel Correctness

Status: planned
Targets: `nop-code/model/nop-code.orm.xml`, `NopCodeFileBizModel.java`, `NopCodeIndexBizModel.java`, `CodeIndexService.java`

- Item Types: `Fix | Decision`

- [ ] F-11: 在 NopCodeFile 的 relations 中补充 usages 和 calls 反向 to-many 关系
- [ ] F-12: 为 callType 和 NopCodeSemanticEdge.relationType 添加 ext:dict 定义（对齐 NopCodeInheritance.relationType 的现有做法）
- [ ] F-27: 修正 NopCodeCall 的 refDisplayName（caller/callee 标注反了）
- [ ] F-28: 统一 NopCodeFlow 审计字段命名：createdTime→createTime, modifiedTime→updateTime, modifiedBy→updatedBy（对齐平台约定）
- [ ] F-36: 为 NopCodeFlowMembership 补充缺失的审计字段（createdBy, updateTime, updatedBy）
- [ ] F-16: 为 IncrementalStatus 内部类添加 `@DataBean` 注解
- [ ] F-37: 修复 NopCodeFileBizModel @BizLoader symbols/types/outline 返回空数据：entityToFileResult() 应填充 symbols 数据
- [ ] F-38: 修复 getIndexStats() 性能问题：`findAllByQuery().size()` → `countByQuery()`
- [ ] F-19: NopCodeFileBizModel.getByPath()/findPage_files() 改用 @DataBean FileAnalysisDTO 返回（对齐 indexFile() 做法）
- [ ] F-18: 为 6 个注册在非 ORM 实体类型的 @BizLoader 补充 xmeta 定义或改用 @DataBean
- [ ] F-49: 为 NopCodeSemanticEdge 的 3 个 to-one relation 补充 refDisplayName

Exit Criteria:

- [ ] NopCodeFile ORM relations 包含 usages 和 calls 反向关系
- [ ] callType 和 SemanticEdge.relationType 具有 ext:dict 定义
- [ ] NopCodeCall refDisplayName 的 caller/callee 标注语义正确
- [ ] NopCodeFlow 审计字段使用 createTime/updateTime/createdBy/updatedBy 命名
- [ ] NopCodeFlowMembership 具有 createdBy, updateTime, updatedBy 字段
- [ ] IncrementalStatus 标注了 @DataBean
- [ ] @BizLoader symbols/types/outline 返回非空 symbols 数据
- [ ] getIndexStats() 使用 countByQuery() 而非全量加载
- [ ] NopCodeFileBizModel 的返回类型统一使用 @DataBean
- [ ] `./mvnw test -pl nop-code -am` 通过
- [ ] 若 ORM 模型变更触发了 codegen，`./mvnw clean install -pl nop-code -am -DskipTests` 后生成产物正确
- [ ] No owner-doc update required（ORM/BizModel 局部修复不涉及 owner doc 变更）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 — P2 Code Duplication & Type Safety

Status: planned
Targets: `CodeIndexService.java`, `CodeQueryService.java`, `CodeGraphService.java`, `FlowDetector.java`, `ImpactAnalyzer.java`, `ChangeAnalyzer.java`, `DeadCodeDetector.java`, `CodeMethodCall.java`, `FileTreeNode.java`

- Item Types: `Fix`

- [ ] F-09: 提取 entityToCodeSymbol 到共享 Converter/工具方法（消除 3 处 ~90 行重复）
- [ ] F-26: 提取 extData JSON filePath 解析逻辑到共享工具方法。当前 4 个文件使用 3 种不同方法名（FlowDetector.extractFilePathFromSymbol, ChangeAnalyzer.extractFilePathFromSymbol, DeadCodeDetector.resolveFilePath, ImpactAnalyzer.extractFilePath），实现逻辑相似但需先验证语义等价性后再合并为统一方法
- [ ] F-58: 提取 type-symbol filter 逻辑为 CodeSymbolKind.isTypeKind()（消除 4 处 `s.getKind() == CLASS || ...` 重复）
- [ ] F-69: CodeMethodCall.confidence 改用 EdgeConfidence 枚举替代 String 字面量
- [ ] F-70: FileTreeNode.type 改用枚举替代 unbounded String
- [ ] F-71: CodeGraphService Tarjan SCC 算法中 Map<String, Boolean> → Set<String>
- [ ] F-40: 消除 CodeIndexService 中 instanceof 下溯：在 nop-code-flow 模块的 IChangeAnalyzer 接口上添加 setFlowDetector() 方法（注意：需验证 IChangeAnalyzer 所在包，确保不引入 flow→core 循环依赖）
- [ ] F-35: 将 JavaImportResolver/PythonImportResolver/TypeScriptImportResolver 从 core 模块移到对应 lang-* 模块。执行前需验证 core 模块无对这三个类的编译期依赖，否则暂缓移入 successor plan

Exit Criteria:

- [ ] entityToCodeSymbol 只存在于 1 处定义，3 处调用方使用同一方法
- [ ] extData filePath 解析只存在于 1 处定义，4 个调用方（FlowDetector, ChangeAnalyzer, DeadCodeDetector, ImpactAnalyzer）使用同一方法
- [ ] CodeSymbolKind.isTypeKind() 替代所有 4 处内联过滤逻辑
- [ ] CodeMethodCall.confidence 类型为 EdgeConfidence 枚举
- [ ] FileTreeNode.type 使用枚举约束
- [ ] CodeGraphService Tarjan 算法使用 Set<String>
- [ ] IChangeAnalyzer 接口包含 setFlowDetector()，无 instanceof 下溯
- [ ] JavaImportResolver 在 lang-java 模块，PythonImportResolver 在 lang-python 模块，TypeScriptImportResolver 在 lang-typescript 模块
- [ ] `./mvnw test -pl nop-code -am` 通过
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 — P2 Concurrency, Transaction & Error Handling

Status: planned
Targets: `CodeIndexService.java`, `NopCodeErrors.java`, `NopCodeCoreErrors.java`, `GraphExporter.java`, `ChangeAnalyzer.java`, `JavaFileAnalyzer.java`

- Item Types: `Fix | Decision`

- [ ] F-39: 修复 indexDirectory 锁竞态：不在 finally 中移除锁（移除操作应在锁释放后，或使用 computeIfAbsent 模式）
- [ ] F-24: 将 analyzer.analyzeProject() 移出 DB session（参照 triggerIncrementalIndex 的正确模式）
- [ ] F-21: 创建 NopCodeException 模块级异常类（遵循 error-handling.md Pattern 2）
- [ ] F-22: 4 条中文 ErrorCode 消息改为英文
- [ ] F-55: GraphExporter.ERR_GRAPH_EXPORT_FAILED 移入 NopCodeErrors 统一管理
- [ ] F-56: 3 处 ErrorCode throws 补充 .param(indexId) 上下文参数
- [ ] F-57: 2 处静默异常吞没添加 LOG.debug 日志（对齐 FlowDetector/DeadCodeDetector 做法）

Exit Criteria:

- [ ] indexDirectory 的锁生命周期正确：不同线程不会因锁提前移除而同时进入临界区
- [ ] analyzer.analyzeProject() 在 runInSession() 外执行
- [ ] NopCodeException 类存在且被模块内代码使用
- [ ] NopCodeErrors/NopCodeCoreErrors 中无中文消息
- [ ] GraphExporter 不含内联 ErrorCode，引用 NopCodeErrors
- [ ] 所有 ErrorCode throws 包含 .param() 上下文
- [ ] 静默异常处有 LOG.debug 记录
- [ ] `./mvnw test -pl nop-code -am` 通过
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 — P2 Test Quality & P2 Decision Adjudication

Status: planned
Targets: `TestCriticalNodeAnalyzer.java`, `TestKnowledgeGapAnalyzer.java`, `TestGraphDiffer.java`, `TestDocKeywordExtractor.java`

- Item Types: `Fix | Proof`

- [ ] F-41: 补充 CriticalNodeAnalyzer 和 KnowledgeGapAnalyzer 核心算法测试（centrality, bridge detection, threshold behavior）
- [ ] F-42: 补充 GraphDiffer 边差异测试（getAddedEdges, getRemovedEdges）
- [ ] F-44: 修复 TestDocKeywordExtractor 测试名与断言矛盾：重命名测试或修正断言逻辑

**P2 Decision 裁定（不编码，只记录决策）：**

- [ ] F-04 裁定："Usage" 语义混乱 → 记录决策：BizLoader `usages()` 重命名为 `annotations()`，`getSymbolUsages` → `getSymbolAnnotations`，并记录到 Phase 1 执行（若 Phase 1 已关闭则记入 Non-Blocking Follow-up）
- [ ] F-05 裁定：7 个 BizModel 返回 core 模型 → 记录决策：为每个创建 @DataBean DTO 或记录为 residual
- [ ] F-10+F-20 裁定：语言适配器注册 → 记录决策：通过 IoC 注入或删除孤立 beans.xml
- [ ] F-14 裁定：9 路级联删除无软删除 → 记录决策：添加 softDelete 或裁定为 acceptable risk
- [ ] F-15 裁定：NopCodeDependency 路径字符串非 FK → 记录决策：改用 FK 或裁定为 acceptable
- [ ] F-23 裁定：BizLoader usages 与 xmeta usages 语义冲突 → 随 F-04 一起裁定
- [ ] F-24 裁定：triggerIncrementalIndex 两段 session 不连续 → 记录决策：合并为单 session 或裁定为 acceptable risk
- [ ] F-32 裁定：自定义分页查询绕过 CrudBizModel 管线 → 记录决策：改用标准 doFindPage 或裁定为 index-scoped justified

Exit Criteria:

- [ ] CriticalNodeAnalyzer 测试覆盖 centrality 和 bridge detection 场景
- [ ] KnowledgeGapAnalyzer 测试覆盖 threshold behavior
- [ ] GraphDiffer 测试验证 getAddedEdges 和 getRemovedEdges
- [ ] TestDocKeywordExtractor 测试名与断言语义一致
- [ ] 所有 P2 Decision findings 有明确裁定记录（adopt/residual/successor plan）
- [ ] `./mvnw test -pl nop-code -am` 通过
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 6 — P3 Fixes

Status: planned
Targets: Multiple files across nop-code sub-modules

- Item Types: `Fix | Follow-up`

- [ ] F-33: nop-code-api pom.xml 添加 parent POM 继承（消除版本漂移风险）
- [ ] F-45: 19 处硬编码 `1.0.0-SNAPSHOT` 版本引用改为 `${project.version}`
- [ ] F-46: 删除未引用的 `_NopCodeDaoConstants.java`（226 行手写代码在 `_` 前缀文件中）
- [ ] F-48: NopCodeFlowMembership.isEntry 类型从 TINYINT 改为 BOOLEAN
- [ ] F-51: ORM index 命名从 `idx_{entity}_{column}` 改为 `ix_{table}_{column}` 对齐平台标准
- [ ] F-64: dict code/language 的 valueType 从 int 改为 string（对齐实际存储类型）
- [ ] F-65: _lang-typescript.beans.xml 补充 xsi namespace 声明
- [ ] F-66: 5 个 dict.yaml 文件 valueType 从 int 改为 string
- [ ] F-59: CommunityDetectionResultDTO.algorithmUsed "none" 值对齐 AlgorithmType 枚举
- [ ] F-68: xmeta kinds prop type 从 String 改为 List\<String\>

**P3 Follow-up 裁定（不编码，只记录决策）：**

- [ ] F-47: codeId domain VARCHAR(36) vs VARCHAR(32) → 裁定
- [ ] F-50: NopCodeSymbol 单表 32 字段 → 裁定
- [ ] F-52: 4 个 *Service 命名 → 裁定
- [ ] F-53: 多参数方法用 @Name vs @RequestBean → 裁定
- [ ] F-54: nop-code-lang-* 缺少 _module 文件 → 裁定
- [ ] F-60: Boolean 字段命名不一致 → 裁定
- [ ] F-61: 测试文件 import 排序 → 裁定
- [ ] F-67: gql:selection 超长行 → 裁定

Exit Criteria:

- [ ] nop-code-api pom.xml 使用 parent POM 继承
- [ ] 无硬编码 `1.0.0-SNAPSHOT` 版本引用
- [ ] `_NopCodeDaoConstants.java` 已删除
- [ ] NopCodeFlowMembership.isExit/isEntry 使用 BOOLEAN
- [ ] dict.yaml valueType 与实际存储类型一致
- [ ] 所有 P3 Follow-up findings 有明确裁定记录
- [ ] `./mvnw test -pl nop-code -am` 通过
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] 所有 P1 findings（6 条）已修复且有测试验证
- [ ] 所有 P2 Fix 类型 findings 已修复
- [ ] 所有 P2/P3 Decision 类型 findings 有明确裁定（adopt/residual/successor）
- [ ] 无 in-scope live defect 被降级到 deferred/follow-up
- [ ] 无空壳实现或静默跳过的新增代码
- [ ] `./mvnw clean install -pl nop-code -am -DskipTests` 通过
- [ ] `./mvnw test -pl nop-code -am` 通过
- [ ] checkstyle / 代码规范检查通过
- [ ] 独立子 agent closure audit 已完成并记录证据
- [ ] `ai-dev/logs/` 收口记录已更新

## Deferred But Adjudicated

### F-08 CodeIndexService God Object 拆分

- Classification: `optimization candidate`
- Why Not Blocking Closure: 1573 行 God Object 是历史架构债务，功能正确但维护性差。拆分属于大型重构，需独立评估收益/风险比和回归测试覆盖，不宜在审计修复计划中同时进行
- Successor Required: yes
- Successor Path: `ai-dev/plans/` （待创建，建议编号 93+）

### F-34 ICodeIndexService God Interface 拆分

- Classification: `optimization candidate`
- Why Not Blocking Closure: 29 方法接口是 God Object 的镜像，拆分应与 F-08 同步进行
- Successor Required: yes
- Successor Path: 同 F-08 successor plan

### F-29 nop-code-api 空壳模块

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 空壳模块不影响运行时功能，重组涉及跨模块依赖调整，风险和范围超出审计修复计划
- Successor Required: yes
- Successor Path: `ai-dev/plans/` （待创建）

### F-30 ICodeIndexService 返回类型混合

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 接口契约不一致是 F-29 的下游问题，依赖模块结构重组
- Successor Required: yes
- Successor Path: 同 F-29 successor plan

### F-13 i18n 英文翻译

- Classification: `optimization candidate`
- Why Not Blocking Closure: 235 条 null 英文翻译不影响功能正确性，属于本地化优化
- Successor Required: no

### F-31 空 IBiz 接口

- Classification: `optimization candidate`
- Why Not Blocking Closure: 3 个空 IBiz 接口不影响功能，属于代码整洁度改进
- Successor Required: no

### F-07 全部 11 实体缺少审计字段

- Classification: `optimization candidate`
- Why Not Blocking Closure: 索引数据可从源码重建，无审计字段不影响功能正确性；Phase 2 已修复 NopCodeFlow 审计字段命名（F-28）和 NopCodeFlowMembership 缺失字段（F-36），其余实体的审计字段添加为低优先级
- Successor Required: no

## Non-Blocking Follow-ups

- P3 测试反模式（F-62 assertNotNull 空断言, F-63 反射测试私有方法, F-72 assumeTrue 静默跳过, F-73/F-74 纯 getter/setter 测试）→ 统一纳入测试质量提升专项
- F-17 NopCodeIndexBizModel 方法过多 → 随 F-08 CodeIndexService 拆分一起治理
- F-10+F-20 语言适配器 IoC 注册 → Phase 5 Decision 裁定后可能纳入 successor plan

## Closure

Status Note: <<完成或关闭时填写>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent>>
- Evidence: <<task id / daily log link / findings 摘要>>

Follow-up:

- <<或明确写 no remaining plan-owned work>>
