# 95 nop-code 审计遗留发现修复（第 8 轮收口）

> Plan Status: completed
> Last Reviewed: 2026-06-02
> Executed: 2026-06-02
> Source: `ai-dev/audits/2026-06-02-adversarial-review-nop-code/summary.md`（AR-88~AR-93 已由 Plan 93 修复）+ `ai-dev/audits/2026-06-01-adversarial-review-nop-code/01-open-findings.md`（AR-59~AR-67）+ `ai-dev/audits/2026-06-01-adversarial-review-nop-code-r5/01-open-findings.md`（AR-82~AR-86）+ `ai-dev/audits/2026-05-31-adversarial-review-nop-code/01-open-findings.md`（AR-75~AR-76）+ live code verification
> Related: Plans 88–94（all completed）

## Purpose

修复经 live code 验证确认仍 outstanding 的 10 条审计发现（1 P0 + 1 P1 + 8 P2），使 nop-code 模块的审计发现收口。Plans 88–94 已修复全部 P0（3 项）、P1（22 项）和大部分 P2。本轮收口剩余 outstanding 项。

## Current Baseline

### 已修复（Plans 88–94 覆盖）

- **Plan 88**：CallGraph 不可变性 + 去重、deleteIndex 分页删除、TTL 驱逐、per-indexId Lock、CodeIndexService 拆分
- **Plan 89**：安全（权限/路径遍历/Git 注入）、OOM（LIMIT/缓存守卫/flush-evict）、数据完整性（cascade/唯一键）、功能正确性（dict/forType）、测试有效性
- **Plan 90**：安全（detectFlows @Auth）、性能（triggerIncrementalIndex 长事务）、代码质量（GraphExporter 异常/BFS 类型安全）、import 排序
- **Plan 91**：P0 NPE null 保护、deleteFileRecords 补全、extData JSON 注入修复、测试 assumeTrue 修复、唯一约束、类型不一致、静默异常、异常上下文、死代码清理
- **Plan 92**：2026-06-01 深度审计 17 维度 75 条 findings 收口
- **Plan 93**：P0 BFS 深度/VFS 数据完整性/DeadCode 排除逻辑、P1 安全 @Auth 系统性补全/并发 evictOverflow/OOM resolveQualifiedNamesToIds/内存泄漏 TSTree/数据完整性 Python 嵌套、P2 多语言/缓存策略/删除安全、P3 并发统一
- **Plan 94**：ORM 审计字段命名/logical delete/数据库索引、residual 并发模式 evictStatusMap、IoC 统一注册、P3 配置一致性

### 仍 Outstanding（经 live code 验证，2026-06-02）

**P0（1 项）**：

- AR-59：`ExtDataHelper` 无 `setFilePath` 方法；`enrichSymbolsWithAnnotations` 只写 annotations；4 个下游组件（DeadCodeDetector/FlowDetector/ChangeAnalyzer/ImpactAnalyzer）通过 `extractFilePath` 读取 `extData.filePath`，永远返回 null。数据管线断裂

**P1（1 项）**：

- AR-83：`CodeQueryService.getModuleDigest`（line 219-269）per-file 单独查询符号，N+1 模式。5000 文件 → 5001 次 DB 查询

**P2（8 项）**：

- AR-84：`DeadCodeDetector.isPotentiallyDynamic`（line 331-359）使用 `annotation.contains("Bean")` 等子串匹配，Spring 项目假阴性严重
- AR-85：`CodeIndexService` 索引器（line 1103-1104）测试文件检测仅覆盖 `"Test.java"` + `"/test/"`，不覆盖 Python/TypeScript/Kotlin 测试文件。DeadCodeDetector 的 `testPathPatterns` 已修复（多语言），但索引器的 `TESTED_BY` 记录生成未修
- AR-86：`CodeQueryService.findImplementations`（line 665-736）加载 ALL inheritances + ALL symbols 构建 `idToQn` 映射，一次查询加载整个索引
- AR-49：`FlowDetector.computeCriticality`（line 351-358）`testGap` 硬编码 `1.0`，永远最大权重（0.15），未从实际测试数据计算
- AR-80（residual）：`CodeIndexService.updateIndexStats`（line 1316/1321）仍用 `findAllByQuery().size()` 而非 `countByQuery`。主 `getIndexStats` 已修，但增量索引路径的统计更新未修
- AR-81：`CodeGraphService.getTypeHierarchy`（line 179-195）加载 ALL inheritances，一次查询加载整个索引的继承关系
- AR-62：`CodeIndexService.ensureSubServices`（line 110-116）check-then-act 无同步，并发首次访问可能创建多套 Service 实例
- AR-77（recategorized）：`indexLocks` ConcurrentHashMap 永不清理 entry（lock 解锁后不移除），长期运行内存泄漏。非原始 AR-77 描述的 race condition（lock 未被提前移除），而是反方向的资源泄漏

### 已验证修复，不再 outstanding

- AR-09：FlowDetector HashMap → ConcurrentHashMap（FlowDetector.java:66-67 已是 CHM）
- AR-28：TypeScript walkNodeForCalls 已被调用（handleFunctionDeclaration:231, handleMethodDefinition:259）
- AR-87：getProjectFilePaths O(N²) 已通过局部缓存缓解（saveFileResultInSession 内 cachedProjectFilePaths）
- entityToCodeSymbol 三重复制已由 `CodeSymbolConverter` 统一（CodeSymbolConverter.java）

## Goals

1. 修复 P0 数据管线断裂：symbol extData.filePath 写入
2. 修复 P1 性能：getModuleDigest N+1 → 批量加载
3. 修复 P2 功能正确性：DeadCodeDetector 排除精确度、测试文件检测多语言化、testGap 动态计算
4. 修复 P2 性能：findImplementations 避免全量加载、updateIndexStats 使用 countByQuery、getTypeHierarchy 避免 ALL 加载
5. 修复 P2 并发：ensureSubServices 同步、indexLocks 清理

## Non-Goals

- 不拆分 CodeIndexService God Object（Plan 93/94 Deferred，独立 successor plan）
- 不做 Tarjan 递归→迭代（AR-69 P3 watch-only residual）
- 不做 i18n 英文翻译（ORM displayName/label，不影响运行时）
- 不重构 nop-code-api 模块结构（Plan 92 Deferred）
- 不做 getProjectFilePaths 进一步优化（AR-87 已通过局部缓存缓解，残余为 optimization candidate）

## Scope

### In Scope

- 1 个 P0 修复（extData filePath）
- 1 个 P1 修复（getModuleDigest N+1）
- 8 个 P2 修复（功能 3 + 性能 3 + 并发 2）

### Out Of Scope

- CodeIndexService 拆分 → successor plan
- Tarjan 递归→迭代 → watch-only residual
- i18n / DRY / 模块结构优化

## Execution Plan

> **Phase 依赖**：Phase 1 是 Phase 3 的前置条件——AR-49（testGap）和 AR-84（isPotentiallyDynamic）的测试依赖 extData 中有正确的 filePath 数据。Phase 2 和 Phase 4 可独立执行。Phase 5 可独立执行。

### Phase 1 — P0 数据管线修复

Status: completed
Targets: `nop-code/nop-code-core/src/main/java/io/nop/code/core/util/ExtDataHelper.java`, `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java`

- Item Types: `Fix`

- [x] **AR-59：添加 ExtDataHelper.setFilePath + 在 saveFileResultInSession 中为所有 symbol 写入 filePath**

Exit Criteria:

> Phase 1 completed: commit 1daf78270

- [x] `ExtDataHelper`（`nop-code-core` 模块）有 `setFilePath(String extData, String filePath)` 方法
- [x] `saveFileResultInSession` 中 `enrichSymbolsWithAnnotations` 调用之后，有独立遍历对所有 symbol（非仅 annotated symbol）调用 `setFilePath` 写入当前文件的 `filePath`
- [x] 新增单元测试验证：索引后所有 symbol 的 extData 中 filePath 非空，与文件路径一致（包括无 annotations 的 symbol）
- [x] `./mvnw test -pl nop-code -am` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — P1 性能修复

Status: completed
Targets: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeQueryService.java`

- Item Types: `Fix`

- [x] **AR-83：getModuleDigest N+1 → 批量加载**

Exit Criteria:

> Phase 2 completed: commit 0646d854f

- [x] `getModuleDigest` 方法内只有 1 次 `fileDao` 查询 + 1 次 `symbolDao` 查询（而非 N+1）
- [x] 现有测试通过（回归验证）
- [x] `./mvnw test -pl nop-code -am` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 — P2 功能正确性修复

Status: completed
Targets: `DeadCodeDetector.java`, `CodeIndexService.java`, `FlowDetector.java`

- Item Types: `Fix`

- [x] **AR-84：isPotentiallyDynamic 改用精确匹配**
- [x] **AR-85：索引器测试文件检测多语言化**
- [x] **AR-49：testGap 动态计算**

Exit Criteria:

> Phase 3 completed: commit 792700d92

- [x] `isPotentiallyDynamic` 使用精确匹配（`equals`），无宽泛 `contains`
- [x] 新增测试验证：`@SomeBean` 注解不被标记；`@Bean` 被标记
- [x] 索引器为 Python/TypeScript/Kotlin 测试文件生成 `TESTED_BY` usage 记录
- [x] `computeCriticality` 的 `testGap` 参数从调用方传入（`computeTestGap` 方法）
- [x] `./mvnw test -pl nop-code -am` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 — P2 性能修复

Status: completed
Targets: `CodeQueryService.java`, `CodeIndexService.java`, `CodeGraphService.java`

- Item Types: `Fix`

- [x] **AR-86：findImplementations 使用 SymbolTable 缓存**
- [x] **AR-80：updateIndexStats 使用 countByQuery**
- [x] **AR-81：getTypeHierarchy 使用 BFS 定向查询**

Exit Criteria:

> Phase 4 completed: commit b56e04576

- [x] `findImplementations` 使用 `cacheManager.getOrRebuildSymbolTable` 获取 idToQn 映射
- [x] `updateIndexStats` 使用 `countByQuery`（无 `findAllByQuery().size()` 模式）
- [x] `getTypeHierarchy` 使用 `collectRelevantInheritances` BFS 定向查询
- [x] `./mvnw test -pl nop-code -am` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 — P2 并发修复

Status: completed
Targets: `CodeIndexService.java`

- Item Types: `Fix`

- [x] **AR-62：ensureSubServices 添加 synchronized 保护**
- [x] **AR-77：deleteIndex 添加 indexLocks.remove(indexId)**

Exit Criteria:

> Phase 5 completed: commit b0897f869

- [x] `ensureSubServices` 有 `synchronized` 保护
- [x] `deleteIndex` 方法中有 `indexLocks.remove(indexId)` 调用
- [x] `./mvnw test -pl nop-code -am` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 1 个 P0 数据管线断裂已修复（extData filePath 写入）
- [x] 1 个 P1 性能问题已修复（getModuleDigest N+1）
- [x] 8 个 P2 全部修复（功能 3 + 性能 3 + 并发 2）
- [x] 无 in-scope live defect 被降级到 deferred/follow-up
- [x] 无空壳实现或静默跳过的新增代码
- [x] `./mvnw install -pl nop-code -am -DskipTests` 通过
- [x] `./mvnw test -pl nop-code -am` 通过
- [x] checkstyle / 代码规范检查通过（ast-grep lint pass on all commits）
- [x] 独立子 agent closure audit 已完成并记录证据
- [x] `ai-dev/logs/` 收口记录已更新

## Deferred But Adjudicated

### AR-69 Tarjan 递归 DFS

- Classification: `watch-only residual`
- Why Not Blocking Closure: P3 风险，仅影响极端深依赖链（>1000 层），实际项目中极少出现
- Successor Required: no

### AR-87 getProjectFilePaths 残余优化

- Classification: `optimization candidate`
- Why Not Blocking Closure: 已通过局部缓存缓解 O(N²)→O(N)。残余问题为全量加载文件路径列表到内存，可用 targeted query 优化，但当前不构成 correctness 或 OOM 风险
- Successor Required: no

### CodeIndexService God Object 拆分

- Classification: `optimization candidate`
- Why Not Blocking Closure: Plan 88 已拆分至 ~1500 行（50%），功能正确。进一步拆分需独立评估
- Successor Required: yes
- Successor Path: 独立 successor plan（编号待定）

## Non-Blocking Follow-ups

- entityToCodeSymbol DRY 治理（Plan 91 Deferred 07-03）→ 已由 CodeSymbolConverter 修复
- nop-code-api 模块结构重组（Plan 92 Deferred F-29/F-30）
- Tarjan 递归→迭代（AR-69 P3 watch-only residual）
- i18n ORM displayName 英文翻译（P3，不影响运行时）

## Closure

Status Note: All 10 audit findings (1 P0 + 1 P1 + 8 P2) have been fixed and verified. 5 commits across 5 phases. All tests pass. No in-scope live defects remain.

Closure Audit Evidence:

- Reviewer / Agent: Independent sub-agent (ses_17f5fe191ffe3Ea0TCO08JYoHF)
- Audit Session: 2026-06-02
- Verdict: Can Close
- Evidence:
  - Phase 1 (AR-59 P0): ExtDataHelper.setFilePath added, saveFileResultInSession writes filePath for ALL symbols after enrichSymbolsWithAnnotations. 6 new unit tests in TestExtDataHelper.
  - Phase 2 (AR-83 P1): getModuleDigest reduced from N+1 to 2 queries. Batch loads all symbols grouped by fileId.
  - Phase 3 (AR-84/AR-85/AR-49 P2): isPotentiallyDynamic uses equals() not contains(). Test file detection covers Python/TypeScript/Kotlin. testGap dynamically computed from symbol filePath in extData.
  - Phase 4 (AR-86/AR-80/AR-81 P2): findImplementations uses SymbolTable cache. updateIndexStats uses countByQuery. getTypeHierarchy uses BFS targeted inheritance queries.
  - Phase 5 (AR-62/AR-77 P2): ensureSubServices synchronized. deleteIndex removes indexLocks entry.
  - `./mvnw test -pl nop-code -am` BUILD SUCCESS (all phases)
  - `./mvnw install -pl nop-code -am -DskipTests` BUILD SUCCESS
  - ast-grep Java lint check passed on all 5 commits

Follow-up:

- CodeIndexService God Object further split → successor plan
- Tarjan 递归→迭代（AR-69 P3 watch-only residual）
- i18n ORM displayName 英文翻译（P3，不影响运行时）
- nop-code-api 模块结构重组（Plan 92 Deferred F-29/F-30）
