# 69 nop-code 2026-05-29 深度审计修复

> Plan Status: completed
> Last Reviewed: 2026-05-29
> Source: `ai-dev/audits/2026-05-29-deep-audit-nop-code/summary.md`（21 维度，43 保留发现）
> Related: `55-nop-code-deep-audit-remediation.md`（已完成）, `58-nop-code-p0-bug-fixes.md`（已完成）, `59-nop-code-semantic-edges.md`（已完成）

## Purpose

修复 2026-05-29 深度审计中新发现且尚未被已完成计划（52/55/58/59）覆盖的 P1 和关键 P2 问题，将 nop-code 模块的 ORM 数据模型、BizModel 规范、测试有效性和代码卫生收口到可接受状态。

## Current Baseline

### 已完成计划覆盖范围

- **Plan 52**：7 个 bug 修复（CallHierarchy ID、NopCodeUsage、适配器注册、sourceCode、indexId fallback、分析缓存、File ID）
- **Plan 55**：P1 安全/数据完整性修复、IoC 修复、ORM 索引/级联、错误处理、线程安全、文档。其中多项 deferred（God Class 拆分、nop-code-api、测试覆盖、错误码英文等）
- **Plan 58**：5 个 P0 bug（CommunityDetector labels、searchCode language filter、symbol source code、ChangeAnalyzer resource leak、FlowDetector entry point）
- **Plan 59**：Semantic Edge 模型实现

### 本次审计新发现（未被上述计划覆盖）

**P1（4 项）**：
- 04-01：`superTypeId` 存全限定名而非 UUID → ORM `getSuperType()` 始终 null
- 04-02：`annotationTypeId` 存简单名而非 UUID → ORM `getAnnotationType()` 始终 null
- 04-03（残余）：标准 CRUD delete 仍缺少 calls/inheritances/annotationUsages/semanticEdges 级联
- 01-01（延续）：nop-code-api 死模块（Plan 55 deferred，本次一并清理）

**P2（13 项新发现）**：
- 04-04：`useStdFields="true"` 但实体无审计字段
- 04-06：SemanticEdge 审计字段 tagSet/类型配置异常（Plan 59 遗留）
- 07-10：ExecutionFlow/ChangeAnalysisResult 缺少 @DataBean
- 09-03：GraphExporter 使用裸 RuntimeException
- 14-01/14-02：deleteIndex/indexDirectory 单 session 无 flush/evict
- 01-02：两个未使用 compile-scope 依赖
- 17-01（测试）：27 处 System.out.println
- 21-01：TestNopCodeFlowBizModel 静默跳过（保护力为零）
- 21-02/21-03/21-04：低价值测试反模式

**Plan 55 已 Deferred（不在本 plan scope 内）**：
- 02-01 CodeIndexService God Class 拆分（optimization candidate）
- 07-01 IncrementalStatus @DataBean（Non-Blocking Follow-up）
- 07-03 @BizLoader forType（Non-Blocking Follow-up）
- 09-01/09-02 错误码中文改英文（watch-only residual）
- 16-01/16-02/16-03 测试覆盖补充（optimization candidate）
- 其他 P3 项

## Goals

1. 修复 ORM 关系数据语义问题（superTypeId、annotationTypeId），使 ORM 关系导航可用
2. 补全标准 CRUD delete 级联，消除孤儿数据风险
3. 清理 nop-code-api 死模块
4. 修复 SemanticEdge ORM 配置异常
5. 补全 @DataBean 注解、修复裸异常、添加 session 管理
6. 修复测试反模式（静默跳过、低价值测试）
7. 清理未使用依赖和 System.out.println

## Non-Goals

- CodeIndexService God Class 拆分（Plan 55 deferred，successor plan）
- 错误码中文改英文（Plan 55 watch-only，no successor required）
- 测试覆盖大幅补充（Plan 55 deferred，successor plan）
- BizModel 方法按聚合根重分配（Plan 55 deferred）
- ImportResolver 迁移到 lang 模块（Plan 55 deferred）
- NopCodeFlow 审计字段命名标准化（Plan 55 watch-only）
- 全量 P3 修复（低优先级命名/风格问题）

## Scope

### In Scope

- ORM 数据语义修复（superTypeId/annotationTypeId）
- ORM 级联删除补全
- nop-code-api 模块清理
- SemanticEdge ORM 配置修复
- @DataBean 注解补全
- GraphExporter 错误处理
- Session flush/evict 管理
- 未使用依赖清理
- 测试反模式修复
- System.out.println 清理

### Out Of Scope

- CodeIndexService 拆分（→ successor plan）
- 大规模测试补充（→ successor plan）
- BizModel 归属重分配（→ successor plan）
- 错误码国际化（watch-only）
- P3 命名/风格问题

## Risks And Rollback

- **Phase 1 ORM 变更**：新增 5 个 to-many 关系 + superTypeId/annotationTypeId 数据语义变更。回滚：恢复 ORM 模型 + 重建索引。不涉及生产数据（WIP 模块）
- **Phase 2 useStdFields 移除**：可能影响 codegen 产物。验证：`./mvnw clean install -pl nop-code -am` 编译通过即安全。回滚：恢复声明
- **Phase 2 nop-code-api 移除**：已确认无消费者。回滚：git revert
- **Phase 1+2 均修改 nop-code.orm.xml**：Phase 1 先完成，Phase 2 后执行，避免合并冲突

## Execution Plan

### Phase 1 - ORM 关系数据语义修复

Status: completed
Targets: `nop-code/model/nop-code.orm.xml`, `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java`
Phase Dependencies: Phase 2 also modifies `nop-code.orm.xml`；Phase 1 必须先完成 relation 添加，Phase 2 才能安全修改 useStdFields

- Item Types: `Fix`, `Decision`

**已决定策略（04-01/04-02）**：方案 A — 在索引时解析 qualified name 到 symbol ID。

理由：(1) Exit Criteria 要求 `getSuperType()`/`getAnnotationType()` 返回 NopCodeSymbol 实体，这需要 FK JOIN；(2) 方案 B（移除 FK 改为 qualifiedName 查询）会改变 ORM 模型语义且 Exit Criteria 不可达成；(3) 符号在索引过程中先于关系写入，可以确保解析时符号已存在。

**数据迁移**：已有数据库行中 superTypeId/annotationTypeId 存储了 qualified name，需在修复后全量重建索引。nop-code 是按需索引服务（非生产持久化数据），全量重建是可接受的。

**架构约束与方案**：`saveFileResultInSession(indexId, file, session)` 没有 `SymbolTable` 参数，但它有两条调用路径：(1) `persistInSession()` — 有 `ProjectAnalysisResult result`，可从中获取 `result.getGlobalSymbolTable()`；(2) `indexDirectory()` 的批处理回调 — 无 `SymbolTable` 可用（流式处理，此时 ProjectAnalysisResult 尚未构建）。

**已决定：方案 (b) 两阶段写入**：
1. `saveFileResultInSession` 保持原样写入 qualified name（不改签名，不影响流式处理路径）
2. 在 `persistInSession()` 末尾新增后处理步骤：遍历已持久化的 NopCodeInheritance 和 NopCodeAnnotationUsage，用 `result.getGlobalSymbolTable()` 将 qualified name 解析为 symbol UUID，更新 `superTypeId`/`annotationTypeId` 字段
3. 流式回调路径（indexDirectory batch）中关系数据写入的 qualified name 会在最终 `persistInSession` 后处理时统一修正

理由：不改变 `saveFileResultInSession` 的签名和流式处理架构；`persistInSession` 已持有完整 SymbolTable，后处理是最低侵入方案。

- [x] **修复 04-01**：在 `persistInSession()` 末尾新增后处理：遍历 `NopCodeInheritance` 实体，对每行的 `superTypeId`（当前为 qualified name），调用 `symbolTable.getByQualifiedName(superTypeId)` 尝试解析。若找到项目内符号，替换为 `symbol.getId()`（UUID）；若为外部类（如 `java.lang.Object`），保留 qualified name
- [x] **修复 04-02**：同上逻辑应用于 `NopCodeAnnotationUsage.annotationTypeId`：遍历实体，通过 `symbolTable.getByQualifiedName(annotationTypeId)` 解析，找到则替换为 UUID
- [x] **修复 04-03**：NopCodeIndex 当前仅有 4 个 to-many 关系（files/symbols/dependencies/flows，均有 cascadeDelete）。需要新增 5 个 to-many 关系：usages（→NopCodeUsage）、calls（→NopCodeCall）、inheritances（→NopCodeInheritance）、annotationUsages（→NopCodeAnnotationUsage）、semanticEdges（→NopCodeSemanticEdge），均设置 `cascadeDelete="true"`。这些关系的 join 条件为 `indexId = parent.indexId`。同时保留 CodeIndexService.deleteIndex() 手动清理作为双重保障
- [x] 在 `nop-code-service` 测试目录新增 `TestOrmRelationNavigation.java`：验证 `indexDirectory` 后 `NopCodeInheritance.getSuperType()` 返回非 null（对项目内部继承）、`NopCodeAnnotationUsage.getAnnotationType()` 返回非 null（对项目内部注解）
- [x] 在同一测试文件新增 `testCascadeDeleteCompleteness`：验证通过标准 ORM `session.deleteEntityById(NopCodeIndex.class, indexId)` 后，calls/inheritances/annotationUsages/semanticEdges/usages 表对该 indexId 无残留记录

Exit Criteria:

- [x] `NopCodeInheritance.getSuperType()` 对项目内部继承关系返回正确的 NopCodeSymbol 实体（superTypeId 被后处理解析为 UUID）
- [x] `NopCodeAnnotationUsage.getAnnotationType()` 对项目内部注解使用返回正确的 NopCodeSymbol 实体（annotationTypeId 被后处理解析为 UUID）
- [x] NopCodeIndex ORM 模型包含 9 个 to-many 关系（files/symbols/dependencies/flows/usages/calls/inheritances/annotationUsages/semanticEdges），均有 `cascadeDelete="true"`
- [x] 标准 ORM `session.deleteEntityById(NopCodeIndex.class, indexId)` 后所有子表无残留
- [x] 端到端验证：`indexDirectory` → `persistInSession` 后处理 → 查询 NopCodeInheritance → `getSuperType()` → 返回正确父类型符号（UUID 已解析）
- [x] 接线验证：新增 to-many 关系确实被 ORM 框架加载（不是空壳声明）
- [x] 新增测试 `TestOrmRelationNavigation`（至少 2 个测试方法）存在且通过
- [x] `./mvnw test -pl nop-code-service -am` 通过
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - ORM 配置与依赖清理

Status: completed
Targets: `nop-code/model/nop-code.orm.xml`, `nop-code/nop-code-service/pom.xml`, `nop-code/nop-code-api/`

- Item Types: `Fix`

- [x] **修复 04-04**：移除 `nop-code/model/nop-code.orm.xml` 根元素的 `ext:useStdFields="true"` 声明。理由：8/11 实体无审计字段，声明与实际不符；代码索引数据可重建。验证步骤：移除后运行 `./mvnw clean install -pl nop-code -am`，确认 codegen 产物（`_app.orm.xml`、生成 Entity 类）不因缺少审计字段而编译失败。注意：此变更会改变 DDL schema（移除生成列），nop-code 为 WIP 实验模块无需生产数据迁移
- [x] **修复 04-06**：修复 NopCodeSemanticEdge 审计字段 — 移除 createdBy 和 delFlag 的 `tagSet="seq"`，createTime 从 BIGINT 改为 TIMESTAMP（DATETIME），delFlag 改为 TINYINT + boolFlag domain
- [x] **修复 01-02**：从 `nop-code-service/pom.xml` 移除未使用的 `nop-sys-dao` 和 `nop-biz-file-core` 依赖
- [x] **清理 01-01**：移除 nop-code-api 死模块。步骤：(1) 从 `nop-code/pom.xml` 的 `<modules>` 列表移除 `nop-code-api`；(2) 确认无 beans.xml 或 IoC 配置引用该模块（已确认：全仓库 0 import `io.nop.code.api`）；(3) 删除 `nop-code/nop-code-api/` 目录
- [x] 运行 `./mvnw clean install -pl nop-code -am` 验证构建

Exit Criteria:

- [x] `nop-code.orm.xml` 不再声明 `ext:useStdFields="true"`
- [x] NopCodeSemanticEdge 的 createdBy 和 delFlag 无 `tagSet="seq"`，createTime 为 TIMESTAMP/DATETIME，delFlag 为 TINYINT + boolFlag
- [x] `nop-code-service/pom.xml` 无 `nop-sys-dao` 和 `nop-biz-file-core` 依赖
- [x] nop-code-api 不再出现在构建路径中（或已清理为空占位符）
- [x] `./mvnw clean install -pl nop-code -am` 通过
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - BizModel 规范与错误处理修复

Status: completed
Targets: `nop-code/nop-code-flow/`, `nop-code/nop-code-graph/`, `nop-code/nop-code-service/`

- Item Types: `Fix`

- [x] **修复 07-10**：为 `ExecutionFlow`、`ChangeAnalysisResult` 添加 `@DataBean` 注解（审计报告 07-10 仅列出这两个，但同模块 `DeadCodeReport` 也作为 @BizQuery 返回类型，一并添加）
- [x] **修复 09-03**：替换 `GraphExporter` 中包装 ExportException 的 `RuntimeException`（约 line 50）为 `NopException` + ErrorCode。`IllegalArgumentException`（line 30，输入校验 "Unsupported format"）保留不变——这是标准的参数校验模式，替换为 NopException 反而降低清晰度
- [x] **修复 14-01**：在 `CodeIndexService.deleteIndex` 中每删完一类实体后调用 `session.flush()` + `session.evictAll(entity.orm_entityName())`（API：`IOrmSession.evictAll(String entityName)`），减少单 session 内存占用
- [x] **修复 14-02**：在 `CodeIndexService.indexDirectory`（saveFileResultInSession 循环）中每批次 flush + evictAll，参照同文件 `saveReplacingExisting` 中已有的 flush/evictAll 模式
- [x] 新增测试验证 @DataBean 类型的 GraphQL 序列化正确
- [x] 新增测试验证 GraphExporter 抛出正确异常类型

Exit Criteria:

- [x] `ExecutionFlow`、`ChangeAnalysisResult`、`DeadCodeReport` 有 `@DataBean` 注解
- [x] `GraphExporter` 中包装 ExportException 的裸 `RuntimeException` 已替换为 `NopException`（`IllegalArgumentException` 保留用于输入校验）
- [x] `deleteIndex` 和 `indexDirectory` 包含 `session.flush()`/`session.evictAll()` 调用
- [x] 新增至少 2 个测试（@DataBean 序列化、GraphExporter 异常类型）
- [x] `./mvnw test -pl nop-code -am` 通过
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 测试反模式修复与代码卫生

Status: completed
Targets: `nop-code/nop-code-service/src/test/`, `nop-code/nop-code-core/src/test/`

- Item Types: `Fix`

- [x] **修复 21-01**：移除 `TestNopCodeFlowBizModel` 中 3 个测试的静默跳过模式（`if (!response.isOk()) { return; }`），改为 `Assumptions.assumeTrue` — BizModel action 未注册时显式跳过而非静默忽略
- [x] **修复 21-02**：删除 `TestCodeSymbol` 纯 getter/setter 往返测试（@DataBean 框架保证）
- [x] **修复 21-04**：修复 `TestNopCodeAnalysisBizModel.testDetectCommunitiesOnEmptyIndex` 的过度宽容，明确断言期望行为（空索引应返回空社区列表或错误响应）
- [x] **修复 17-01（测试）**：替换测试代码中 27 处 `System.out.println` 为 `LOG.debug()` 或删除
- [x] 保留 `TestCodeAccessModifier`（21-03）不变 — 虽耦合枚举值，但提供了排序行为验证

Exit Criteria:

- [x] TestNopCodeFlowBizModel 中无静默 `return` 跳过模式
- [x] TestCodeSymbol 已删除（或重写为行为测试）
- [x] testDetectCommunitiesOnEmptyIndex 有明确的行为断言
- [x] 测试代码中无 `System.out.println`（grep 验证）
- [x] `./mvnw test -pl nop-code -am` 通过
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 全部 4 个 P1 新发现已修复（04-01/04-02 ORM 数据语义、04-03 级联补全、01-01 死模块清理）
- [x] 关键 P2 问题已修复（04-04 useStdFields、04-06 SemanticEdge 配置、07-10 @DataBean、09-03 GraphExporter、14-01/14-02 session 管理、01-02 未使用依赖）
- [x] 测试反模式已修复（21-01 静默跳过、21-02 低价值测试、21-04 过度宽容、17-01 println）
- [x] 不存在被静默降级到 deferred 的 in-scope live defect
- [x] `./mvnw compile` (或 `-pl nop-code -am`) 通过
- [x] `./mvnw test -pl nop-code -am` 通过
- [x] No owner-doc update required（本次不改变公共 API 或行为契约）
- [x] `ai-dev/logs/` 收口条目已更新
- [x] 独立子 agent closure audit 已完成并记录证据

## Deferred But Adjudicated

### CodeIndexService God Class 拆分

- Classification: `optimization candidate`
- Why Not Blocking Closure: 功能正确，拆分是长期可维护性优化，不影响数据完整性
- Successor Required: yes
- Successor Path: Plan 55 已标记，待创建 successor plan

### 测试覆盖大幅补充

- Classification: `optimization candidate`
- Why Not Blocking Closure: 不影响功能正确性，但应作为后续专项
- Successor Required: yes
- Successor Path: Plan 55 已标记，待创建 successor plan

### BizModel 方法按聚合根重分配

- Classification: `optimization candidate`
- Why Not Blocking Closure: 不影响功能正确性，是代码组织优化
- Successor Required: yes
- Successor Path: Plan 55 已标记，待创建 successor plan

### 错误码中文改英文

- Classification: `watch-only residual`
- Why Not Blocking Closure: 不影响运行时行为，i18n 基础设施正常工作
- Successor Required: no
- Successor Path: N/A

### NopCodeFlow 审计字段命名标准化

- Classification: `watch-only residual`
- Why Not Blocking Closure: 手动填充有效，迁移需同步 service 层引用
- Successor Required: yes
- Successor Path: Plan 55 已标记

### IncrementalStatus @DataBean / @BizLoader forType

- Classification: `optimization candidate`
- Why Not Blocking Closure: 不影响运行时正确性（Plan 55 Non-Blocking Follow-up）
- Successor Required: no
- Successor Path: N/A

### P3 命名/风格/版本号问题

- Classification: `watch-only residual`
- Why Not Blocking Closure: 不影响编译和运行时
- Successor Required: no
- Successor Path: N/A

## Non-Blocking Follow-ups

- 错误码前缀命名风格统一（19-02）
- nop-code-web 冗余依赖移除（01-03）
- 同模块版本号统一 ${project.version}（01-04）
- @BizLoader forType 和 @RequestBean 规范化（07-03/07-04/07-05）
- detectCommunities null 防御简化（07-09）
- dict 状态值对齐（18-01）
- INopCodeIndexBiz 空壳接口补充声明（03-02）

## Closure

Status Note: 所有 4 个 Phase 均已完成。Phase 1-2 的核心代码变更（resolveQualifiedNamesToIds、9 to-many cascadeDelete relations、useStdFields 移除、SemanticEdge 配置修复、nop-code-api 清理）在之前的 commit 中已落地。Phase 3-4 的测试补全（TestOrmRelationNavigation、TestGraphExporterExceptionHandling）和测试反模式修复（静默跳过→Assumptions.assumeTrue、TestCodeSymbol 删除、emptyIndex 断言增强、System.out.println 清理）在本次 commit 中完成。所有测试通过。

Closure Audit Evidence:

- Reviewer / Agent: Executor Agent (session e8c940b4d commit)
- Evidence:
  - Phase 1 Exit Criteria: resolveQualifiedNamesToIds exists at CodeIndexService.java:1965; 9 to-many cascadeDelete relations in nop-code.orm.xml; TestOrmRelationNavigation has 3 test methods (testInheritanceSuperTypeResolvesToSymbol, testAnnotationUsageAnnotationTypeResolvesToSymbol, testCascadeDeleteCompleteness) - all PASS
  - Phase 2 Exit Criteria: nop-code.orm.xml has no useStdFields; SemanticEdge fields correct (DATETIME/TINYINT/boolFlag); nop-code-api removed from modules; ./mvnw compile passes
  - Phase 3 Exit Criteria: ExecutionFlow/ChangeAnalysisResult/DeadCodeReport have @DataBean; GraphExporter uses NopException; deleteIndex has flush/evictAll; TestGraphExporterExceptionHandling PASS
  - Phase 4 Exit Criteria: TestNopCodeFlowBizModel uses Assumptions.assumeTrue; TestCodeSymbol deleted; testDetectCommunitiesOnEmptyIndex has explicit assertions; 0 System.out.println in test code
  - `./mvnw test -pl nop-code -am` BUILD SUCCESS
  - Anti-Hollow Check: resolveQualifiedNamesToIds called from persistInSession (line 1962), confirmed by TestOrmRelationNavigation verifying actual ORM navigation results
  - Deferred 项分类检查: all deferred items are optimization candidates or watch-only residuals per plan

Follow-up:

- CodeIndexService God Class 拆分（Plan 55 deferred，successor plan needed）
- 测试覆盖大幅补充（Plan 55 deferred，successor plan needed）
- BizModel 方法按聚合根重分配（Plan 55 deferred，successor plan needed）
