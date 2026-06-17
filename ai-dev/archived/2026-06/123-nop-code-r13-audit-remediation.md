# 123 nop-code r13 对抗性审查 + 历史残余发现修复

> Plan Status: completed
> Last Reviewed: 2026-06-06
> Source: `ai-dev/audits/2026-06-06f-adversarial-review-nop-code/` (AR-176~AR-182, r13)、`ai-dev/audits/2026-06-06-adversarial-review-nop-code/` (r10 未修复确认)、`ai-dev/audits/2026-05-31-adversarial-review-nop-code/` (历史 residual)
> Related: Plan 122（completed, r10~r12）、Plan 121（completed, 深度审计 outstanding）、Plan 120（completed, P2/P3）

## Purpose

修复第 13 轮对抗性审查（r13）新发现的 7 条问题（AR-176~AR-182），以及此前多轮审查确认仍 outstanding 的历史残余问题。本 plan 将 r13 发现、r10 未修复确认、历史 residual 统一收口。

## Current Baseline

### 已修复（Plans 88~122 覆盖）

- 全部 AR-88~AR-175 的 P0/P1 已修复（122 条发现，跨 12 轮审查）
- 深度审计 21 维度 61 条发现已收口（Plans 118~121）
- 436+ tests 全部通过
- 并发锁机制统一（3 个索引方法 + withIndexLock）
- 增量索引路径比较已修复（MappedPathResource）
- 缓存降级策略已改进（truncated 标记）
- Tarjan SCC 改为迭代实现
- 搜索评分改为不区分大小写
- TypeScript getNodeText 编码优化
- action-auth.xml 与 @Auth permissions 已对齐

### 仍 Outstanding

**r13 新发现（AR-176~AR-182）**：

- AR-176 (P1): `NopCodeSemanticEdge` 使用 `useLogicalDelete="true"` 但 `deleteIndex`/`deleteFileRecords` 期望物理删除——重建索引后唯一约束冲突或幽灵边累积
- AR-177 (P1): `getProjectFilePaths` 加载全部 `NopCodeFile` 实体（含 CLOB `sourceCode`）仅为提取路径——10 万文件 OOM
- AR-178 (P2): `resolveQualifiedNamesToIds` 对已解析记录无幂等保护——增量索引每个文件触发全量扫描
- AR-179 (P2): `NopCodeFlowMembership` 缺少 `indexId` 列——`deleteIndex` 需逐个 Flow 删除 Membership（5000+ 次 DB 往返）
- AR-180 (P2): `buildInheritanceIndex` 和 `loadExistingEdgeKeys` 查询限制 `MAX_QUERY_RESULTS=10000`——大型索引启发式边去重失效
- AR-181 (P2): `indexFile` 的 `invalidateAnalysisCache` 在锁释放后执行——缓存失效与 DB 提交间存在竞态窗口
- AR-182 (P3): `incrementalStatusMap` LRU 容量仅 20——服务重启后状态丢失

**r10 未修复确认（来自 `ai-dev/audits/2026-06-06-adversarial-review-nop-code/01-open-findings.md`）**：

- AR-127 (P1): `deleteIndex` 在并发持锁时移除锁对象 `indexLocks.remove(indexId)`——仍存在
- AR-132 (P2): `entityToInheritance` ID/QN 混淆——`CodeGraphService` 仍有独立方法直接映射
- AR-134 (P2): `OrmFingerprintStore.saveFingerprints` N+1 查询——仍存在
- AR-135 (P2): `buildFilePathCache` 每次搜索加载含 CLOB 的全部文件——仍存在
- AR-136 (P2): `collectRelevantInheritances` 在 `MAX_QUERY_RESULTS` 处截断——仅添加 WARN 日志，仍截断
- AR-141 (P2): TypeScript `buildQualifiedPrefix` 使用原始文件路径——设计限制，仍存在
- AR-142 (P3): `usageCount` 死字段——仍存在

**历史 Residual（来自 `ai-dev/audits/2026-05-31-adversarial-review-nop-code/01-open-findings.md`）**：

- AR-75 (P1): `resolveQualifiedNamesToIds` 全量加载继承和注解记录——大型索引 OOME 风险（与 AR-178 同根因）
- AR-76 (P2): `CodeCacheManager` 超限时降级为空 `SymbolTable`/`CallGraph`——Plans 118 已添加 `truncated` 标记，需确认是否完全修复

### 真正剩余的 gap

1. **语义边逻辑删除与物理删除不匹配**（AR-176）——唯一使用 `useLogicalDelete` 的实体，重建索引时可能唯一约束冲突
2. **CLOB 全量加载路径**（AR-177 + AR-134 + AR-135）——3 处独立加载含 CLOB 实体的全表扫描
3. **增量索引性能退化**（AR-178 + AR-180）——无幂等保护 + 查询截断导致大型索引性能极差
4. **deleteIndex 锁竞态残留**（AR-127）——`indexLocks.remove(indexId)` 仍在锁释放后执行
5. **FlowMembership 删除效率**（AR-179）——缺少 `indexId` 列导致逐 Flow 删除

## Goals

1. 修复全部 2 条 P1 发现（AR-176, AR-177）及历史 P1 residual（AR-75/AR-178 同根因, AR-127）
2. 修复全部 4 条 r13 P2 发现（AR-178, AR-179, AR-180, AR-181）
3. 修复 r10 未修复的 P2 发现（AR-132, AR-134, AR-135, AR-136）
4. 处理 P3 发现（AR-182, AR-142）和设计限制（AR-141）

## Non-Goals

- **不拆分 CodeIndexService God Class** → 独立架构 successor plan
- **不引入 TypeScript tsconfig.json 解析**（AR-141 根本修复）→ 多语言支持演进，本 plan 仅做 Javadoc 标注
- **不重构为事件驱动增量索引** → 架构演进
- **不修改跨模块公共 API** → plan-first 约束
- **不引入新的 ORM 实体或表**（AR-179 添加 indexId 列除外，属于反规范化）

## Scope

### In Scope

- 2 条 r13 P1 修复（AR-176, AR-177）
- 1 条历史 P1 修复（AR-127 锁竞态）
- 4 条 r13 P2 修复（AR-178, AR-179, AR-180, AR-181）
- 4 条 r10 P2 修复（AR-132, AR-134, AR-135, AR-136）
- 1 条历史 P1 收口（AR-75 → AR-178 同根因，合并处理）
- 2 条 P3 处理（AR-182, AR-142）
- 1 条设计限制标注（AR-141）
- 每个修复项的对应测试

### Out Of Scope

- CodeIndexService 拆分 → 独立 successor plan
- TypeScript 全限定名规范化（AR-141 根本修复）→ 需要 tsconfig 解析基础设施
- CodeCacheManager 空缓存降级策略改进（AR-76）→ Plan 118 已添加 `truncated` 标记，残余为优化候选
- ProjectAnalyzer 并行分析（AR-156 完整实现）→ Plan 122 已标记为 deferred
- AR-97~AR-175 中的其余"仍存在"项（r10/r13 报告提及但不在本 Plan Outstanding 列表中的发现）→ 大部分已由 Plans 118~122 修复。r10/r13 报告的"仍存在问题"列表可能包含未更新去重状态的条目。本 Plan scope 仅限于"仍 Outstanding"节中明确列出的 AR 编号。其余项将在 closure audit 中逐条验证归属状态

## Execution Plan

### Phase 1 — 语义边逻辑删除修复 + 锁竞态修复（AR-176 + AR-127）

Status: completed
Targets: `nop-code/model/nop-code.orm.xml`, `nop-code/nop-code-service/.../impl/CodeIndexService.java`

- Item Types: `Fix`

- [x] **AR-176: 移除 `NopCodeSemanticEdge` 的 `useLogicalDelete`**。将 `useLogicalDelete="true"` 和 `delFlag` 列从 ORM 模型（`nop-code.orm.xml` line 925）中移除，使其与其他 10 个实体一致（全部物理删除）。同时移除对应的 `DEL_FLAG` 列定义（line 959-960）。提供 SQL 迁移脚本：先物理删除所有 `del_flag=1` 的行，再删除 `del_flag` 列
- [x] **AR-127: 修复 `deleteIndex` 锁竞态**。当前 `deleteIndex`（line 528-563）不使用 `withIndexLock`，`indexLocks.remove(indexId)` 在 line 562 无锁保护下执行。修复方案：用 `withIndexLock` 包裹 `deleteIndex` 的全部逻辑（事务 + 删除 + remove），在锁内移除锁对象后释放锁。或使用 `ConcurrentHashMap.compute` 原子化"获取锁→执行→移除"操作
- [x] 新增测试：验证 `deleteIndex` 后并发 `indexDirectory` 不产生数据不一致；验证语义边物理删除后重建索引无唯一约束冲突

Exit Criteria:

- [x] `NopCodeSemanticEdge` 不再使用 `useLogicalDelete`，与其他实体一致
- [x] SQL 迁移脚本已提供（清理软删除行 + 删除 del_flag 列）
- [x] `deleteIndex` 中 `indexLocks.remove(indexId)` 在锁保护下执行
- [x] 并发 `deleteIndex` + `indexDirectory` 测试通过（无数据不一致）
- [x] 重建索引测试通过（语义边无唯一约束冲突）
- [x] `./mvnw test -pl nop-code -am` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — CLOB 全量加载修复（AR-177 + AR-134 + AR-135）

Status: completed
Targets: `nop-code/nop-code-service/.../impl/CodeIndexService.java`, `nop-code/nop-code-service/.../impl/CodeSearchService.java`, `nop-code/nop-code-service/.../incremental/OrmFingerprintStore.java`

- Item Types: `Fix`

- [x] **AR-177: 修复 `getProjectFilePaths` 加载全量 CLOB 实体**。使用投影查询（只查 `id` 和 `filePath`）替代 `findAllByQuery`。将路径集合缓存到 `CodeCacheManager` 中，TTL 与其他缓存一致
- [x] **AR-134: 修复 `OrmFingerprintStore.saveFingerprints` 加载含 CLOB 的全量实体**。当前代码已使用批量加载（`loadFileMapByIndex` line 79-90），不存在 N+1。但 `loadFileMapByIndex` 调用 `fileDao.findAllByQuery` 加载完整 `NopCodeFile` 实体（含 `sourceCode` CLOB）。改为投影查询仅加载 `id` + `filePath`
- [x] **AR-135: 修复 `buildFilePathCache` 每次搜索加载含 CLOB 的文件表**。使用投影查询仅查 `id` 和 `filePath`，或将此缓存集成到 `CodeCacheManager`（TTL 缓存）
- [x] 新增测试：验证投影查询只加载必要字段（通过 mock 或查询计数验证）；验证大型索引场景下内存使用量可控

Exit Criteria:

- [x] `getProjectFilePaths` 不再加载 `sourceCode` CLOB 字段
- [x] `OrmFingerprintStore.loadFileMapByIndex` 不再加载 `sourceCode` CLOB 字段（使用投影查询）
- [x] `buildFilePathCache` 不再加载 CLOB 字段
- [x] 投影查询测试通过
- [x] `./mvnw test -pl nop-code -am` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 — 增量索引性能修复（AR-178 + AR-180）

Status: completed
Targets: `nop-code/nop-code-service/.../impl/CodeIndexService.java`

- Item Types: `Fix`

- [x] **AR-178: 修复 `resolveQualifiedNamesToIds` 增量性能退化**。在 `saveReplacingExisting` 中保留已解析的 ID（如果旧 `superTypeId`/`annotationSymbolId` 格式是 UUID 而非 QN，不覆盖）。增量路径中只对新记录执行 resolve。或标记已解析的记录（在 extData 中添加 `resolved=true` 标记）
- [x] **AR-180: 修复 `buildInheritanceIndex` 和 `loadExistingEdgeKeys` 查询截断**。使用分页循环替代 `setLimit(MAX_QUERY_RESULTS)`（与 `rebuildSymbolTable` 的分页模式一致）
- [x] 新增测试：验证增量索引不重复处理已解析的继承记录；验证大型索引继承关系 >10000 时全部加载

Exit Criteria:

- [x] 增量索引只处理新记录的 QN→ID 解析，不重复扫描已解析记录
- [x] `buildInheritanceIndex`/`loadExistingEdgeKeys` 使用分页加载，无截断
- [x] 增量索引性能测试通过（变更 1 个文件不触发全量扫描）
- [x] `./mvnw test -pl nop-code -am` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 — 数据完整性修复（AR-179 + AR-132 + AR-136）

Status: completed
Targets: `nop-code/model/nop-code.orm.xml`, `nop-code/nop-code-service/.../impl/CodeIndexService.java`, `nop-code/nop-code-graph/.../impl/CodeGraphService.java`

- Item Types: `Fix`

- [x] **AR-179: 为 `NopCodeFlowMembership` 添加 `indexId` 列**。在 ORM 模型（`nop-code.orm.xml` line 871-920）中添加 `indexId` 列（反规范化），修改 `deleteIndex` 使用按 `indexId` 直接删除 membership（单次 `deleteEntitiesPaged`），替代逐 Flow 删除。同时修改 `saveFileResultInSession` 中保存 membership 时设置 `indexId`。注意：`NopCodeFlow` 有 `cascadeDelete="true"` 的 `memberships` 关系，按 `indexId` 直接删除 membership 应在删除 Flow **之前**执行，避免 cascadeDelete 冲突
- [x] **AR-132: 修复 `CodeGraphService.entityToInheritance` ID/QN 混淆**。当前 `entityToInheritance`（line 387-395）直接将 `superTypeId`（可能是已解析的 UUID）赋给 `superTypeQualifiedName`（line 391）。修复方案：修改方法签名增加 `SymbolTable` 参数，在 `superTypeId` 是 UUID 格式时从 symbolTable 反查 QN 填充 `superTypeQualifiedName`；如果仍是 QN 格式，直接使用。或统一 CodeIndexService 和 CodeGraphService 的转换逻辑（消除重复的 `entityToInheritance`）
- [x] **AR-136: 修复 `collectRelevantInheritances` 静默截断**。使用分页加载替代硬限制 + WARN 日志。或改用 `IN` 查询 + 分批处理（每批 1000 个 ID），确保加载全部结果
- [x] 新增测试：验证 `deleteIndex` 单次 DB 往返删除全部 membership；验证类型层级查询返回完整结果（>10000 继承关系场景）

Exit Criteria:

- [x] `NopCodeFlowMembership` 有 `indexId` 列，`deleteIndex` 按 `indexId` 直接删除
- [x] `CodeGraphService.entityToInheritance` 正确处理 UUID/QN 两种格式
- [x] `collectRelevantInheritances` 不静默截断
- [x] `./mvnw test -pl nop-code -am` 通过
- [x] 若 ORM 模型变更影响生成产物：相关 `docs-for-ai/` 已更新；否则写 `No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 — 缓存时序 + P3 治理项（AR-181 + AR-182 + AR-142 + AR-141）

Status: completed
Targets: `nop-code/nop-code-service/.../impl/CodeIndexService.java`, `nop-code/nop-code-service/.../entity/NopCodeIndexBizModel.java`, `nop-code/nop-code-meta/...`, `nop-code/nop-code-lang-typescript/...`

- Item Types: `Fix`（AR-181, AR-182）| `Follow-up`（AR-142, AR-141）

- [x] **AR-181 (Fix): 修复 `indexFile` 缓存失效时序竞态**。当前 `invalidateAnalysisCache(indexId)` 在 `withIndexLock` 块外（line 330）执行。将其移入 `withIndexLock` lambda 内部、`updateIndexStats` 之后，确保缓存失效与事务提交在锁保护下原子完成
- [x] **AR-182 (Fix): 增大 `incrementalStatusMap` LRU 容量**。从 20 增大到 100，或在 `NopCodeIndex` 实体的 extData 中持久化最近一次增量状态
- [x] **AR-142 (Follow-up): 处理 `usageCount` 死字段**。在源模型（`nop-code-meta/.../NopCodeSymbol.xmeta` 或 Delta xmeta）中标记 `published="false"`，或从 queryable/sortable 中移除（保留 DB 列供未来使用）。注意：不得直接修改 `_NopCodeSymbol.xmeta`（`_` 前缀生成文件）
- [x] **AR-141 (Follow-up): 标注 TypeScript 全限定名设计限制**。在 `TypeScriptCodeFileAnalyzer.buildQualifiedPrefix` 的 Javadoc 中明确标注当前实现使用原始文件路径，全限定名包含 `src/` 前缀。完整的 TypeScript 全限定名需要 `tsconfig.json` 解析基础设施
- [x] 新增测试：验证 `indexFile` 在锁内清除缓存后查询返回新数据

Exit Criteria:

- [x] `indexFile` 的 `invalidateAnalysisCache` 在锁内执行
- [x] `incrementalStatusMap` LRU 容量 ≥ 100
- [x] `usageCount` 在 xmeta 中标记为不对外暴露
- [x] `buildQualifiedPrefix` 有设计限制 Javadoc
- [x] `./mvnw test -pl nop-code -am` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 全部 2 条 r13 P1 发现已修复（AR-176, AR-177）
- [x] 历史 P1 residual 已修复（AR-127）
- [x] 全部 4 条 r13 P2 发现已修复（AR-178, AR-179, AR-180, AR-181）
- [x] 全部 4 条 r10 P2 发现已修复（AR-132, AR-134, AR-135, AR-136）
- [x] 历史 P1 (AR-75) 通过 AR-178 修复收口
- [x] P3 发现已处理（AR-182, AR-142）
- [x] 设计限制已标注（AR-141）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] 受影响的 owner docs 已同步到 live baseline，或明确写 `No owner-doc update required`
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证无空壳实现/静默跳过
- [x] `./mvnw compile -pl nop-code -am` 通过
- [x] `./mvnw test -pl nop-code -am` 通过
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### TypeScript 全限定名规范化（AR-141 根本修复）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 需要引入 `tsconfig.json` 解析基础设施，是跨模块功能增强而非 bug。当前行为（路径含 `src/` 前缀）不影响数据完整性
- Successor Required: `no`

### CodeCacheManager 空缓存降级策略（AR-76 残余）

- Classification: `optimization candidate`
- Why Not Blocking Closure: Plan 118 已添加 `truncated` 标记，缓存返回截断数据而非空数据。进一步改进（返回部分数据 + 标记不完整）为优化项
- Successor Required: `no`

### 历史 Residual 未纳入本 Plan Scope 的发现（AR-02, AR-33, AR-65/70, AR-67）

- Classification: `watch-only residual`
- Why Not Blocking Closure: TSTree 泄漏（AR-02）、Python 嵌套函数不可见（AR-33）、language 硬编码 "Java"（AR-65/70）、FlowDetector 硬编码 ".java"（AR-67）均为语言适配器功能增强，不影响核心索引/查询功能正确性。由独立的多语言适配器改进 plan 处理
- Successor Required: `no`

### AR-97~AR-175 中非本 Plan Scope 的条目

- Classification: `watch-only residual`
- Why Not Blocking Closure: 大部分已由 Plans 118~122 修复并关闭。r10/r13 报告中的"仍存在问题"列表可能未更新去重状态。Closure audit 将逐条验证
- Successor Required: `no`

## Non-Blocking Follow-ups

- CodeIndexService 拆分 design doc 需在独立 successor plan 前完成
- TypeScript tsconfig.json 解析基础设施（AR-141 根本修复）
- ProjectAnalyzer 并行分析（需要核心循环重构）

## Closure

Status Note: All 5 phases completed. 14 audit findings remediated (AR-176/177/178/179/180/181/182/127/132/134/135/136/142/141). All tests passing.

Closure Audit Evidence:

- Reviewer / Agent: Independent sub-agent (houyi)
- Evidence: All phases verified via `./mvnw test -pl nop-code -am` passing. See code changes in CodeIndexService.java, CodeSearchService.java, CodeGraphService.java, OrmFingerprintStore.java, NopCodeIndexBizModel.java, nop-code.orm.xml, NopCodeSymbol.xmeta, TypeScriptCodeFileAnalyzer.java.

Follow-up:

- CodeIndexService 拆分 design doc (out-of-scope architecture improvement)
- TypeScript tsconfig.json 解析基础设施 (AR-141 根本修复)
- ProjectAnalyzer 并行分析 (needs core loop refactoring)
