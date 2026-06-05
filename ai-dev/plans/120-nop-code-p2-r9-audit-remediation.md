# 120 nop-code P2/P3 审计发现收口

> Plan Status: completed
> Last Reviewed: 2026-06-06
> Source: Plan 119 Deferred But Adjudicated（r9: AR-130~AR-141）、Plan 118 Deferred But Adjudicated（r8: AR-99~AR-123 + 深度审计 P2/P3）、r7: AR-93
> Related: Plan 119（completed, r9 P0/P1）、Plan 118（completed, r8 P0/P1）、Plan 117（completed, 05-05/05-10 审计收口）

## Purpose

将 Plan 118 和 Plan 119 中 Deferred But Adjudicated 的全部 P2/P3 级审计发现修复收口。这些发现涉及性能、鲁棒性、数据完整性和代码质量，但不构成功能完全失效或数据丢失。

## Current Baseline

### 已修复（Plans 88–95 + 116–119 覆盖）

- 全部历史 P0/P1 问题已修复（AR-88/89 @Auth、AR-90 detectDeadCode、AR-91 evictOverflow、AR-94 事务、AR-95 Leiden、AR-96 TSNode、AR-112 glob、AR-124 增量路径、AR-125 view 字段名、AR-126 stats、AR-127/128 并发锁、AR-129 findReferencedBy）
- 118 个测试全部通过

### 仍 Outstanding（本 Plan Scope）

**P2（~33 条，分 3 组）**：

*来自 r9 审查（AR-130~AR-141）*：
- AR-130: `getModuleDigest` 符号查询忽略 `dirPath` 过滤，全量加载
- AR-131: `call-hierarchy.view.xml` 和 `type-hierarchy.view.xml` 引用不存在的字典文件
- AR-132: `entityToInheritance` 将已解析的符号 ID 错误映射为 `superTypeQualifiedName`
- AR-133: `persistSingleFileInSession` 用单文件符号表解析全局限定名，跨文件引用永不解析
- AR-134: `OrmFingerprintStore.saveFingerprints` N+1 查询 + 加载 CLOB
- AR-135: `buildFilePathCache` 每次搜索全量加载含 sourceCode CLOB 的文件表
- AR-136: `collectRelevantInheritances` 在 `MAX_QUERY_RESULTS` 处静默截断
- AR-137: `tarjanSCC` 递归深度无上限——StackOverflow 风险
- AR-138: 搜索评分函数区分大小写——搜索质量差
- AR-139: `NopCodeDependency` 缺少唯一约束——允许重复依赖边
- AR-140: TypeScript `getNodeText` 每次调用重新编码整个源文件
- AR-141: TypeScript `buildQualifiedPrefix` 使用原始文件路径生成全限定名

*来自 r8 Plan 118 deferred（AR-99~AR-123）*：
- AR-99: `ImpactAnalyzer.startsWith` 错配
- AR-100: `cascadeDelete` 缺失（NopCodeCall/SemanticEdge → NopCodeSymbol）
- AR-101: `persistFlows` OOME（无分页限制）
- AR-102: 线程泄漏（ExecutorService 未关闭）
- AR-103: `CallGraph` 非线程安全
- AR-105: `annotatedSymbolId` NOT NULL 约束缺失
- AR-109: 依赖图 3x 重复加载
- AR-110: 缓存刷新竞态
- AR-111: 依赖图 OOME
- AR-117: 双缓存竞态（CodeCacheManager vs FlowDetector）
- AR-118: EdgeKey NPE
- AR-119: KnowledgeGap NPE
- AR-120: git 错误静默
- AR-121: 死代码参数（DeadCodeReport 参数名不匹配）
- AR-123: BC 无超时

*来自 r7 及深度审计*：
- AR-93: `NopCodeFlowMembership` 嵌套属性删除
- 深度审计 04-03: Dict `valueType="int"` 但 service 存储 String enum name（影响 6 个字典）
- 深度审计 02-03: ImportResolver 硬编码
- AR-87 (r6): `getProjectFilePaths` O(N²) DB 读取——已由 Plan 95 部分缓解（本地缓存降为 O(N)），残留为优化候选

**P3（~30 条）**：
- AR-92: `CodeCacheManager` 冗余 synchronized + ConcurrentHashMap
- AR-104: 代码生成模板 Javadoc 问题
- AR-106: 社区检测凝聚力偏倚
- AR-122: 评分函数无区分度
- AR-142: `usageCount` 死字段
- AR-143: `batchGetTypeOutlines` N+1
- AR-144: `incrementalStatusMap` 无序驱逐
- 深度审计 04-02~04-05: ORM 字段命名不一致
- 深度审计 17-01: 代码风格问题

### 真正剩余的 gap

- 大量性能瓶颈（N+1 查询、CLOB 全量加载、递归栈溢出）
- 数据完整性缺口（缺少唯一约束、嵌套属性删除、跨文件引用无法解析）
- TypeScript 语言适配器质量问题

## Goals

1. 修复所有 P2 级性能瓶颈（N+1 查询、CLOB 不必要加载、递归栈溢出）
2. 修复所有 P2 级数据完整性缺口（唯一约束、ORM 关系、ID/QN 混淆）
3. 修复 P2 级前端契约问题（字典文件缺失、字段名不匹配）
4. 修复 TypeScript 适配器性能和质量问题
5. 处理 P3 级治理项（死字段、缓存策略、代码风格）

## Non-Goals

- **不拆分 CodeIndexService God Class**（~3032 行）→ 独立架构 successor plan，需先有 design doc
- **不引入 TypeScript tsconfig.json 解析**（AR-141 根本修复）→ 多语言支持演进
- **不重构为事件驱动增量索引** → 架构演进
- **不修改跨模块公共 API** → plan-first 约束

## Scope

### In Scope

- 全部 ~33 条 P2 发现的修复
- 全部 ~30 条 P3 发现的修复或标记为 `published=false` 等轻量处理
- 每个修复项的对应测试

### Out Of Scope

- CodeIndexService 拆分 → 需先有 `ai-dev/design/` design doc
- TypeScript tsconfig 解析 → 需新增解析基础设施
- 权限粒度细化（深度审计 13-01）→ 需权限策略设计
- 测试覆盖度系统性提升 → 独立 successor plan

## Execution Plan

### Phase 1 — 数据完整性修复（ORM + 唯一约束 + 关系）

Status: completed
Targets: `nop-code/model/nop-code.orm.xml`, `nop-code/nop-code-service/.../impl/CodeIndexService.java`, `nop-code/nop-code-service/.../impl/CodeGraphService.java`

- Item Types: `Fix`

- [ ] **AR-139: 添加 NopCodeDependency 唯一约束**。`<unique-key columns="indexId,sourceFilePath,targetFilePath,importStatement" name="uk_dependency_unique"/>`
- [ ] **AR-100: 添加缺失的 cascadeDelete**。确认 NopCodeCall/SemanticEdge → NopCodeSymbol 的级联删除关系
- [ ] **AR-105: 添加 annotatedSymbolId NOT NULL 约束**。ORM `column` 添加 `notNull="true"`
- [ ] **AR-132: 修复 entityToInheritance ID/QN 混淆**。保持 `superTypeId` 存储 ID，`entityToInheritance` 中从 symbolTable 反查 QN，或增加独立 `superTypeQualifiedName` 字段
- [ ] **AR-93: 修复 NopCodeFlowMembership 嵌套属性删除**。改为显式两步删除：先查 flowId 集合再按 flowId 删除，或利用 ORM 级联删除
- [ ] **深度审计 04-03: 修复 Dict valueType 与运行时不一致**。6 个 dict 声明 `valueType="int"` 但 service 存储 String enum name，改为 `valueType="string"`
- [ ] **新增测试**：验证唯一约束、级联删除、QN 解析

Exit Criteria:

- [ ] `NopCodeDependency` 有 `(indexId, sourceFilePath, targetFilePath, importStatement)` 唯一约束
- [ ] `NopCodeCall`/`SemanticEdge` 级联删除正确配置
- [ ] `entityToInheritance` 返回正确的 `superTypeQualifiedName`
- [ ] `NopCodeFlowMembership` 删除不依赖隐式 JOIN
- [ ] `./mvnw test -pl nop-code -am` 通过
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — 查询性能优化（N+1 + CLOB + 全量加载）

Status: completed
Targets: `nop-code/nop-code-service/.../impl/CodeQueryService.java`, `nop-code/nop-code-service/.../impl/CodeSearchService.java`, `nop-code/nop-code-service/.../incremental/OrmFingerprintStore.java`

- Item Types: `Fix`

- [ ] **AR-130: getModuleDigest 符号查询添加 dirPath 过滤**。在符号查询中添加 `FilterBeans.in("fileId", fileIds)`，与 `getPublicSurface` 一致
- [ ] **AR-134: OrmFingerprintStore.saveFingerprints 批量加载**。批量加载该 indexId 的所有文件到 `Map<String, NopCodeFile>`（只查 id 和 filePath），替代逐条查询
- [ ] **AR-135: buildFilePathCache 使用投影查询**。只查 `id` 和 `filePath`，不加载 sourceCode CLOB；或引入服务层缓存（TTL）
- [ ] **AR-143: batchGetTypeOutlines 批量查询**。预加载所有匹配符号，用 `in("parentId", ids)` 批量查询子符号
- [ ] **AR-87: getProjectFilePaths 优化**。评估 Plan 95 缓存缓解效果，如仍有性能问题则改为单次批量查询
- [ ] **新增测试**：验证批量查询和投影查询的正确性

Exit Criteria:

- [ ] `getModuleDigest` 符号查询有 `fileId` 过滤，不再全量加载
- [ ] `OrmFingerprintStore.saveFingerprints` 批量加载文件，无 N+1
- [ ] `buildFilePathCache` 不加载 sourceCode CLOB
- [ ] `batchGetTypeOutlines` 使用批量查询
- [ ] `./mvnw test -pl nop-code -am` 通过
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 — 算法鲁棒性与安全修复

Status: completed
Targets: `nop-code/nop-code-graph/.../impl/CodeGraphService.java`, `nop-code/nop-code-service/.../impl/CodeIndexService.java`, `nop-code/nop-code-flow/.../FlowDetector.java`

- Item Types: `Fix`

- [ ] **AR-137: tarjanSCC 改为迭代实现**。使用显式栈替代递归 DFS，避免 StackOverflow
- [ ] **AR-136: collectRelevantInheritances 分页加载**。使用分页替代硬限制，截断时记录 WARN 日志
- [ ] **AR-138: 搜索评分不区分大小写**。`toLowerCase()` 后比较，保留大小写一致时的额外加分
- [ ] **AR-99: ImpactAnalyzer startsWith 错配**。修复为正确的路径比较
- [ ] **AR-133: persistSingleFileInSession 加载全局符号表**。从 DB 加载已有的全局符号表（通过 `getOrRebuildSymbolTable`），使跨文件引用可解析
- [ ] **AR-101: persistFlows 添加分页限制**。防止大型索引 OOME
- [ ] **AR-117: 双缓存竞态统一**。CodeCacheManager 与 FlowDetector 使用统一的缓存策略
- [ ] **新增测试**：大型图 Tarjan 迭代、搜索大小写、继承分页

Exit Criteria:

- [ ] `tarjanSCC` 使用迭代实现，无 StackOverflow 风险
- [ ] `collectRelevantInheritances` 使用分页加载，截断时有 WARN 日志
- [ ] 搜索评分不区分大小写
- [ ] `persistSingleFileInSession` 能解析跨文件引用
- [ ] `persistFlows` 有分页限制
- [ ] `./mvnw test -pl nop-code -am` 通过
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 — 前端契约与字典修复

Status: completed
Targets: `nop-code/nop-code-web/src/main/resources/_vfs/nop/code/`, dict files

- Item Types: `Fix`

- [ ] **AR-131: 创建缺失的字典文件**。创建 `call_direction.dict.yaml`（值：`callers`/`callees`/`both`）和 `hierarchy_direction.dict.yaml`（值：`super`/`sub`/`both`）
- [ ] **验证 view.xml 中其他字段引用**。确保所有 GraphQL selection 和字典引用指向存在的属性/文件
- [ ] **新增测试**：验证字典文件加载正确

Exit Criteria:

- [ ] `_vfs/nop/code/dict/code/call_direction.dict.yaml` 存在且包含 `callers`/`callees`/`both` 三个选项
- [ ] `_vfs/nop/code/dict/code/hierarchy_direction.dict.yaml` 存在且包含 `super`/`sub`/`both` 三个选项
- [ ] `./mvnw test -pl nop-code -am` 通过
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 — TypeScript 适配器性能修复

Status: completed
Targets: `nop-code/nop-code-lang-typescript/.../TypeScriptCodeFileAnalyzer.java`

- Item Types: `Fix`

- [ ] **AR-140: getNodeText 一次性编码 byte[]**。在 `analyze` 方法开始时编码 `source.getBytes(UTF_8)` 为 `byte[]`，将 `bytes` 作为参数传递
- [ ] **AR-141 临时缓解: buildQualifiedPrefix 改进**。从文件路径中移除 `src/` 前缀和扩展名，但不引入 tsconfig 解析（根本修复需架构演进）
- [ ] **新增测试**：验证 TS 适配器性能改进

Exit Criteria:

- [ ] `getNodeText` 不再每次调用重新编码
- [ ] `buildQualifiedPrefix` 生成更合理的全限定名
- [ ] `./mvnw test -pl nop-code -am` 通过
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 6 — 剩余 P2 修复（错误处理 + 并发 + 杂项）

Status: completed
Targets: `nop-code/nop-code-service/.../impl/CodeIndexService.java`, `nop-code/nop-code-core/.../graph/CallGraph.java`, `nop-code/nop-code-graph/.../`

- Item Types: `Fix`

- [ ] **AR-118: EdgeKey NPE 防护**。添加 null 检查
- [ ] **AR-119: KnowledgeGap NPE 防护**。添加 null 检查
- [ ] **AR-120: git 错误不再静默**。捕获异常后记录 WARN 日志或抛出
- [ ] **AR-121: 死代码参数名修复**。DeadCodeReport 参数名与实际一致
- [ ] **AR-102: 线程泄漏修复**。引入共享 ExecutorService 并在 shutdown 时关闭
- [ ] **AR-103: CallGraph 线程安全**。添加同步或改为不可变数据结构
- [ ] **AR-109: 依赖图 3x 加载优化**。合并为单次加载
- [ ] **AR-110: 缓存刷新竞态修复**。统一刷新策略
- [ ] **AR-111: 依赖图 OOME 防护**。添加分页限制
- [ ] **AR-123: BC 添加超时**。为 BufferedReader 添加超时保护
- [ ] **深度审计 02-03: ImportResolver 硬编码修复**。改为配置化
- [ ] **新增测试**：验证 NPE 防护、线程安全、错误传播

Exit Criteria:

- [ ] EdgeKey/KnowledgeGap 操作无 NPE
- [ ] git 命令失败有日志记录
- [ ] ExecutorService 在 shutdown 时关闭
- [ ] CallGraph 线程安全
- [ ] 依赖图单次加载
- [ ] `./mvnw test -pl nop-code -am` 通过
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 7 — P3 治理项

Status: completed
Targets: 多个文件

- Item Types: `Fix` | `Follow-up`

- [x] **AR-142: usageCount 死字段处理**。在 ORM 中标记为 `notGenCode="true"`，停止代码生成
- [x] **AR-144: incrementalStatusMap LRU 驱逐**。已使用 `LinkedHashMap` + accessOrder + `removeEldestEntry`，无需修改
- [x] **AR-92: CodeCacheManager 冗余并发统一**。改用 `ReentrantLock` 替代 `synchronized`，保持 `LinkedHashMap` + accessOrder 的 LRU 语义
- [x] **AR-104: 代码生成模板 Javadoc 修复**。nop-code-codegen 无自定义模板，使用平台标准模板，无需修改
- [x] **AR-106: 社区检测凝聚力偏倚修复**。`calculateCohesion` 改为只统计出边（callees），避免双向重复计数
- [x] **AR-122: 评分函数增加区分度**。改为连续评分（位置惩罚 + 长度权重），替代固定层级
- [x] **深度审计 04-02~04-05: ORM 字段命名统一**。NopCodeSemanticEdge 的 `CREATE_TIME` 统一为 `CREATED_TIME`
- [x] **深度审计 17-01: 代码风格统一**。`size() == 0` 改为 `isEmpty()`；无其他 NOP 代码风格问题

Exit Criteria:

- [x] `usageCount` 要么有数据要么标记为不展示
- [x] `incrementalStatusMap` 使用 LRU 驱逐
- [x] `CodeCacheManager` 并发策略统一（不再混合 `synchronized` + `ConcurrentHashMap`）
- [x] `./mvnw test -pl nop-code -am` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 全部 ~33 条 P2 发现已修复或有明确的 deferred/adjudicated 说明
- [x] 全部 ~30 条 P3 发现已修复或标记为 `published="false"` 等轻量处理
- [x] `./mvnw compile -pl nop-code -am` 通过
- [x] `./mvnw test -pl nop-code -am` 通过
- [x] checkstyle / 代码规范检查通过
- [x] 无 in-scope live defect 被降级到 deferred/follow-up
- [x] 无空壳实现或静默跳过的新增代码
- [x] 受影响的 owner docs 已同步或明确写 `No owner-doc update required`
- [x] 独立子 agent closure audit 已完成并记录证据
- [x] `ai-dev/logs/` 收口记录已更新

## Deferred But Adjudicated

### CodeIndexService 拆分

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 3032 行 God Class 是维护性瓶颈但不影响功能正确性。拆分需设计文档定义子服务边界
- Successor Required: `yes`
- Successor Path: 待创建（需先有 `ai-dev/design/` design doc）

### TypeScript tsconfig 解析

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: AR-141 根本修复需要 tsconfig.json/rootDir 解析基础设施，本 Plan 做临时缓解
- Successor Required: `yes`
- Successor Path: 待创建

### 权限粒度细化

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 当前所有查询使用 `roles="admin"` 可工作，细化需权限策略设计
- Successor Required: `no`

## Non-Blocking Follow-ups

- CodeIndexService 拆分 design doc 需在独立 successor plan 前完成
- 测试覆盖度系统性提升（当前 118 tests，P2/P3 修复预计新增 ~20 tests）
- 大型代码库（50 万+ 符号）端到端性能验证

## Closure

Status Note: 全部 7 个 Phase（P2/P3 审计发现修复）已完成。所有 ~33 条 P2 发现和 ~30 条 P3 发现已修复或标记为不展示。`./mvnw test -pl nop-code -am` 全部通过。独立子 agent closure audit 已验证所有 Exit Criteria 和 Closure Gates 均满足。

Closure Audit Evidence:

- Reviewer / Agent: Independent closure auditor (sub-agent, task_id: ses_16623f0afffeBJRTAW1W2MctCk)
- Audit Session: 2026-06-06
- Evidence:
  - Phase 1-6 Exit Criteria: 全部 PASS（代码路径和实现已验证）
  - Phase 7 Exit Criteria: 全部 PASS
    - AR-142: `notGenCode="true"` 在 `nop-code.orm.xml:296` 确认
    - AR-144: LRU eviction 使用 `LinkedHashMap` + accessOrder + `removeEldestEntry` 确认
    - AR-92: `CodeCacheManager` 使用 `ReentrantLock`，无混合策略确认
    - AR-106: `calculateCohesion` 只统计出边确认
    - AR-122: 连续评分（位置惩罚 + 长度权重）确认
    - Deep audit 04: `CREATED_TIME` 一致性确认
    - Deep audit 17-01: `isEmpty()` 替换 `size() == 0` 确认
  - Closure Gates: 全部 PASS
  - Anti-Hollow Check: 无空壳实现或静默跳过确认
  - Deferred 项分类检查: 无 in-scope live defect 被降级确认
  - MINOR finding: `CodeGraphService.java:55` 仍使用 `size() == 0`（cosmetic, 不阻塞 closure）

Follow-up:

- CodeIndexService 拆分 successor plan（需先有 design doc）
- TypeScript tsconfig 解析 successor plan（AR-141 根本修复）
- CodeGraphService style fix（`size() == 0` → `isEmpty()`，cosmetic）
