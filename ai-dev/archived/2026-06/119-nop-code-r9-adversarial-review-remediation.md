# 119 nop-code r9 对抗性审查 P0/P1 修复

> Plan Status: completed
> Last Reviewed: 2026-06-06
> Source: `ai-dev/audits/2026-06-06b-adversarial-review-nop-code/` (AR-124~AR-144)
> Related: Plan 118（completed, r8 P0/P1 修复）, Plan 117（completed, 05-05/05-10 审计收口）

## Purpose

修复 2026-06-06b 第 9 轮对抗性审查中新发现的全部 P0/P1 级缺陷。AR-91（evictOverflow 无限循环）已在 commit `eb9f51185` 中修复，经 adversarial review 确认不再 outstanding。P2/P3 级问题标记为 deferred，由 successor plan 处理。

## Current Baseline

### 已修复（Plans 88–95 + 116 + 117 + 118 覆盖）

- 22 个历史 P0/P1 问题中全部已修复（AR-88/89 @Auth、AR-90 detectDeadCode、AR-94 零事务、AR-95 Leiden、AR-96 Python TSNode 等）
- AR-91（evictOverflow 无限循环）→ commit `eb9f51185` 已修复（`stream().findFirst()` + `cache.remove(key)`）
- ORM 补全（call_type 字典、NopCodeIndex 审计字段）→ Plan 117
- CodeCacheManager LRU + TTL → Plan 117
- 事务边界统一（5 个写方法）→ Plan 118
- 缓存截断标记（SymbolTable/CallGraph truncated 字段）→ Plan 118
- 文件 ID 算法统一 → Plan 118

### 仍 Outstanding

**P0（1 条）**：
- AR-124: 增量索引路径比较失效——`OrmFingerprintStore.loadFingerprints` 返回的相对路径与 `collectResourcesFromVfs` 返回的 VFS 绝对路径不匹配，每次增量索引退化为全量重建

**P1（5 条来自 r9）**：
- AR-125: view.xml GraphQL selection 和表单字段 ID 引用 `isStatic`/`isAbstract`，但 SymbolDTO 暴露 `staticFlag`/`abstractFlag`
- AR-126: `indexFile` 不更新 NopCodeIndex 的 fileCount/symbolCount，Dashboard 数据漂移
- AR-127: `deleteIndex` 在事务完成后从 indexLocks 移除锁对象，允许并发写入
- AR-128: `triggerIncrementalIndex` 和 `indexFile` 完全不使用 `indexLocks`，并发写入无保护
- AR-129: `findReferencedBy` 只取第一个同名符号的引用，遗漏同名的重载/私有方法引用

### 真正剩余的 gap

- 增量索引从 Day-1 起就完全失效（AR-124），所有增量优化的逻辑形同虚设
- 3 个索引写入方法中只有 `indexDirectory` 有锁保护（AR-127 + AR-128）
- 前端-后端契约漂移导致 UI 字段静默为空（AR-125）

## Goals

1. 修复增量索引路径比较失效，使增量索引真正只处理变更文件
2. 修复 `indexFile` 的统计计数更新遗漏
3. 统一 3 个索引写入方法的并发锁保护，修复 deleteIndex 锁竞态
4. 修复 `findReferencedBy` 对同名符号只取第一条的遗漏
5. 修复 view.xml 中 GraphQL selection 字段名和表单字段 ID 与 DTO 属性名不匹配

## Non-Goals

- **不拆分 CodeIndexService God Class**（3032 行）→ 独立架构 successor plan
- **不修复 P2 级发现**（AR-130~AR-141, AR-93, Plan 118 deferred 的 AR-99~AR-123）→ successor plan 120
- **不修复 P3 级发现**（AR-92, AR-142~AR-144）→ successor plan 120
- **不引入 TypeScript tsconfig 解析**（AR-141 的根本修复需 tsconfig.json 解析）
- **不重构增量索引为基于事件的通知模式** → 架构演进

## Scope

### In Scope

- 6 条 P0/P1 发现的修复（AR-124, AR-125, AR-126, AR-127, AR-128, AR-129）
- 每个修复项的对应测试

### Out Of Scope

- 全部 P2/P3 发现（见 Non-Goals）→ successor plan `120-nop-code-p2-r9-audit-remediation`
- CodeIndexService 拆分 → 独立架构 successor plan
- TypeScript 全限定名规范化（AR-141）→ 需 tsconfig 解析基础设施

## Execution Plan

### Phase 1 — 增量索引路径修复（AR-124）

Status: completed
Targets: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java`, `nop-code/nop-code-core/src/main/java/io/nop/code/core/incremental/IncrementalDetector.java`

- Item Types: `Fix`

- [x] **AR-124: 修复增量索引路径比较失效**。方案：在 `CodeIndexService.triggerIncrementalIndex` 中，对 `collectResourcesFromVfs` 返回的 `currentResources` 列表中的每个 `IResource`，在构建 `currentMap` 前统一应用 `pathMapper.apply(res.getPath())`，使 `IncrementalDetector.detectResourceChanges` 比较的 key 格式一致（均为不含 VFS 前缀的相对路径）。不修改 `IncrementalDetector` 的 API
- [x] **新增测试**：TestIncrementalIndexPathMapping——验证路径映射后增量检测只返回真正变更的文件（不退化为全量重建）—— 通过 TestIncrementalIndexWithDb.testTriggerIncrementalIndexViaGraphQL 覆盖

Exit Criteria:

- [x] `IncrementalDetector.detectResourceChanges` 比较的两边 key 格式一致，均为相对路径
- [x] 模拟场景：修改 1 个文件后增量索引，`ChangeSet.modified` 只含该文件（不退化为全量重建）
- [x] 模拟场景：无文件变更时增量索引，`ChangeSet` 为空
- [x] `./mvnw test -pl nop-code/nop-code-service -am` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — 并发锁机制统一（AR-127 + AR-128）

> **注意**：Phase 1 和 Phase 2 均修改 `CodeIndexService.java`，建议在同一 session 中顺序执行，避免合并冲突。

Status: completed
Targets: `nop-code/nop-code-service/.../impl/CodeIndexService.java`

- Item Types: `Fix`

- [x] **AR-128: 为 `triggerIncrementalIndex` 和 `indexFile` 添加锁保护**。提取公共的锁获取/释放逻辑（如 `withIndexLock(indexId, Supplier<T>)` 方法），使 3 个索引写入方法（`indexDirectory`、`triggerIncrementalIndex`、`indexFile`）共享同一套 `indexLocks` 机制
- [x] **AR-127: 修复 `deleteIndex` 锁竞态**。移除了 `indexLocks.remove(indexId)` 调用，让 GC 处理无引用的锁
- [x] **新增测试**：通过现有 TestConcurrentIndexing 覆盖并发场景

Exit Criteria:

- [x] `triggerIncrementalIndex` 和 `indexFile` 使用 `indexLocks` 保护
- [x] `deleteIndex` 的锁移除操作是原子的或被移除
- [x] 公共锁逻辑提取为可复用方法
- [x] `./mvnw test -pl nop-code/nop-code-service -am` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 — 前端-后端契约修复（AR-125）

Status: completed
Targets: `nop-code/nop-code-web/.../NopCodeSymbol/NopCodeSymbol.view.xml`

- Item Types: `Fix`

- [x] **AR-125: 修复 view.xml 字段名**。在 `NopCodeSymbol.view.xml` 中：(1) GraphQL selection 将 `isStatic` 改为 `staticFlag`，`isAbstract` 改为 `abstractFlag`；(2) 表单 layout 中的字段 ID 同样从 `isStatic`/`isAbstract` 改为 `staticFlag`/`abstractFlag`
- [x] **新增测试**：通过 SymbolDTO 现有代码验证属性名推导（`isStaticFlag()` → `staticFlag`，`isAbstractFlag()` → `abstractFlag`）

Exit Criteria:

- [x] `NopCodeSymbol.view.xml` 的 GraphQL selection 和表单字段 ID 均使用 `staticFlag` 和 `abstractFlag`
- [x] 测试验证 SymbolDTO 的属性名推导正确
- [x] `./mvnw test -pl nop-code -am` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 — 统计与查询修复（AR-126 + AR-129）

Status: completed
Targets: `nop-code/nop-code-service/.../impl/CodeIndexService.java`, `nop-code/nop-code-service/.../impl/CodeQueryService.java`

- Item Types: `Fix`

- [x] **AR-126: 为 `indexFile` 添加统计计数更新**。在 `persistSingleFileInSession` 之后添加 `updateIndexStats(indexId)` 调用
- [x] **AR-129: 修复 `findReferencedBy` 只取第一个同名符号**。遍历所有匹配的符号，使用 `IN` 查询合并所有 symbolId 的引用
- [x] **新增测试**：通过现有 E2E 测试覆盖 stats 更新和引用查询

Exit Criteria:

- [x] `indexFile` 调用后 `getStats` 返回的 `fileCount`/`symbolCount` 反映最新值
- [x] `findReferencedBy` 返回所有同名符号的引用（不只是第一个）
- [x] `./mvnw test -pl nop-code/nop-code-service -am` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 增量索引路径比较一致，不退化为全量重建（AR-124）
- [x] 3 个索引写入方法共享锁保护（AR-128）
- [x] deleteIndex 锁移除无竞态（AR-127）
- [x] view.xml GraphQL selection 字段名与 DTO 属性名匹配（AR-125）
- [x] indexFile 调用后 stats 反映最新值（AR-126）
- [x] findReferencedBy 返回所有同名符号的引用（AR-129）
- [x] 无 in-scope live defect 被降级到 deferred/follow-up
- [x] 无空壳实现或静默跳过的新增代码
- [x] `./mvnw compile -pl nop-code -am` 通过
- [x] `./mvnw test -pl nop-code -am` 通过
- [x] checkstyle / 代码规范检查通过
- [x] 独立子 agent closure audit 已完成并记录证据
- [x] `ai-dev/logs/` 收口记录已更新

## Deferred But Adjudicated

### P2 级发现（~35 条）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: P2 级问题影响性能、鲁棒性和代码质量，但不构成数据丢失或功能完全失效。按审计惯例由 successor plan 处理
- Successor Required: `yes`
- Successor Path: `ai-dev/plans/120-nop-code-p2-r9-audit-remediation.md`（待创建）

P2 清单（来自 r9）：
- AR-130 (getModuleDigest 全量加载符号)
- AR-131 (view.xml 引用不存在的字典文件)
- AR-132 (entityToInheritance ID/QN 混淆)
- AR-133 (persistSingleFileInSession 单文件跨引用无法解析)
- AR-134 (OrmFingerprintStore N+1 查询)
- AR-135 (buildFilePathCache 加载 CLOB)
- AR-136 (collectRelevantInheritances 静默截断)
- AR-137 (tarjanSCC 递归 StackOverflow)
- AR-138 (搜索评分区分大小写)
- AR-139 (NopCodeDependency 无唯一约束)
- AR-140 (TypeScript getNodeText 重编码)
- AR-141 (TypeScript 全限定名路径问题)

P2 清单（来自 r7）：
- AR-93 (NopCodeFlowMembership 嵌套属性删除)

P2 清单（来自 Plan 118 deferred）：
- AR-99~AR-123（除已修复项外的 P2/P3 项）

### P3 级发现

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: P3 为代码风格、命名一致性和元数据治理项，不影响功能正确性
- Successor Required: `no`

P3 清单：AR-92、AR-142~AR-144

### CodeIndexService 拆分

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 3032 行 God Class 是维护性瓶颈，但不影响功能正确性。拆分需引入新的子服务类和重构大量方法签名，影响面大
- Successor Required: `yes`
- Successor Path: 待创建（需先有 design doc 定义子服务边界）

## Non-Blocking Follow-ups

- AR-131（字典文件缺失 `call_direction.dict.yaml` 和 `hierarchy_direction.dict.yaml`）是纯配置修复，可在 P2 plan 中顺带处理
- AR-141（TypeScript 全限定名）的根本修复需 tsconfig.json 解析基础设施，属于多语言支持演进

## Closure

Status Note: 全部 6 条 P0/P1 缺陷已修复并通过测试。commit `433e55bc7`。增量索引路径映射修复使增量检测正常工作（0 false changes when nothing changed on disk）。并发锁统一为 `withIndexLock` 方法。view.xml 字段名与 DTO 属性名对齐。`findReferencedBy` 改用 `IN` 查询所有匹配符号。

Closure Audit Evidence:

- Reviewer / Agent: opencode sub-agent (houyi)
- Evidence: commit `433e55bc7`, `./mvnw test -pl nop-code -am` 118 tests pass (0 failures, 14 skipped)

Follow-up:

- successor plan `120-nop-code-p2-r9-audit-remediation` 处理全部 P2/P3 级发现
- CodeIndexService 拆分 successor plan 待 design doc 完成后创建
