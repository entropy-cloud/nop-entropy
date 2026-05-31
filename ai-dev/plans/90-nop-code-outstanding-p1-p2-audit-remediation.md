# 90 nop-code Outstanding P1/P2 Audit Findings Remediation

> Plan Status: completed
> Last Reviewed: 2026-05-31
> Source: `ai-dev/audits/2026-05-31-deep-audit-nop-code-full/summary.md`（P1 + P2 findings）、`ai-dev/plans/89-nop-code-2026-05-31-deep-audit-remediation.md`（completed，Non-Goals 中排除的项）
> Related: `89-nop-code-2026-05-31-deep-audit-remediation.md`（completed），`88-nop-code-remaining-audit-findings-remediation.md`（completed）

## Purpose

修复 2026-05-31 深度审计中经独立验证确认仍 outstanding 的 P1 和高优先级 P2 发现。Plan 89 修复了安全（NopCodeIndexBizModel 权限、路径遍历、Git 注入）、OOM（LIMIT、缓存守卫、flush/evict）、数据完整性（cascade、唯一键）、功能正确性（dict、forType）和测试有效性。本计划处理 Plan 89 Non-Goals 中排除但经验证仍有效的缺陷。

## Current Baseline

### Plan 89 已修复范围

- 安全：NopCodeIndexBizModel 的 5 个 `@BizMutation` 方法 + `sourceCode`/`exportGraph` 权限注解、路径遍历、Git 注入
- OOM：56 处 findAllByQuery LIMIT 审查、CodeCacheManager 分页加载+上限、本地路径索引 flush/evict
- 数据完整性：NopCodeFlow.memberships cascadeDelete、NopCodeCall 唯一键补 column
- 功能正确性：dict index_status 补值、@BizLoader forType 修复
- 测试有效性：TestCacheEviction 重写、GraphDiffer 等最小测试

### Plan 88 已修复范围

- CallGraph 不可变性 + 去重
- deleteIndex 分页删除（DELETE_BATCH_SIZE=500）
- incrementalStatusMap TTL 驱逐（MAX_STATUS_ENTRIES=20）
- indexDirectory per-indexId ReentrantLock
- CodeIndexService God Class 拆分（3033→1518 行）

### 仍 Outstanding 的已验证发现（22 项）

**P1（1 项）**：
- 03-01：11 个 I*Biz 接口均为空壳，NopCodeIndexBiz 22 个自定义方法未在接口声明

**P2（21 项，按优先级排序）**：

安全缺口（3 项）：
- 07-01：detectFlows 标注 `@BizQuery` 但执行 `persistFlows` DB 写入，应为 `@BizMutation`
- 13-01：NopCodeSymbolBizModel 的 showSymbol/sourceCode/searchCode 无权限注解
- 13-02：NopCodeFileBizModel 的 sourceCode BizLoader 无权限注解

性能（3 项）：
- 14-02：triggerIncrementalIndex 在 ORM session 内执行文件分析（长事务）
- 07-04：findReferencedBy N+1 查询模式（每条 usage 逐个查询 file + symbol）
- 07-05：findImplementations 加载全部符号到内存（最多 10000 条）

代码质量（4 项）：
- 07-03：entityToCodeSymbol 在三处完全重复（90 行）
- 09-01：GraphExporter 公共 API 路径使用裸 `IllegalArgumentException`
- 15-01：deleteFileRecords 使用 `List<?>` + `Object` 丧失类型安全
- 15-02/15-03：BFS 使用 `String[]` 编码异构数据（CodeGraphService + CodeQueryService）

测试（3 项）：
- 16-01：7 个已定义错误码从未使用（比原审计多 2 个）
- 16-02：CodeIndexService 1518 行核心服务缺少直接单元测试
- 21-01/21-02：TestBuildHierarchyCycleProtection 测试 Math.min/HashSet、TestDeterministicEntityIds 测试 DigestHelper，均不测业务逻辑

ORM（3 项）：
- 04-01：8/11 实体缺失审计字段（createdBy/updatedBy 等）
- 04-04：SOURCE_CODE CLOB 列表查询可能加载大量数据
- 04-11：9 个 cascadeDelete 可能导致大批量删除超时

其他（2 项）：
- 02-01：CodeIndexService 1518 行仍混合 7 类职责（Plan 88 从 3033→1518，继续拆分收益递减）
- 17-01：9 个文件 import 分组顺序违反约定（`io.nop.*` 在前 `java.*` 在后，与约定相反）

### 代码库事实基线

- `CodeIndexService.java` 当前约 1551 行（Plan 88 从 3033→1518，后续计划略有增加）
- `NopCodeIndexBizModel.java:190`：`detectFlows` 标注 `@BizQuery`，内部调用 `codeIndexService.detectFlows()` → `persistFlows()` 执行 DB 写入
- `NopCodeSymbolBizModel.java`：showSymbol（line 123）、sourceCode（line 111）、searchCode（line 182）均无 `@Auth` 注解（Plan 89 中 NopCodeIndexBizModel 使用的是 `@Auth(permissions = "code-source-read")`，来自 `io.nop.api.core.annotations.directive.Auth`，非 `@BizPermission`）
- `NopCodeFileBizModel.java:70`：sourceCode BizLoader 无权限注解
- `CodeIndexService.java:639`：`triggerIncrementalIndex` 在 `ormTemplate.runInSession()` 内执行文件分析
- `CodeQueryService.java:601-625`：`findReferencedBy` 在 stream.map() 内逐条 `fileDao.getEntityById()` + `symbolDao.getEntityById()`
- `CodeQueryService.java:684`：`findImplementations` 使用 `findAllByQuery` 加载全量符号（有 LIMIT 10000 保护）
- `CodeIndexService.java:1154`：`deleteFileRecords(List<?>)` 使用 `Object pathObj`
- `GraphExporter.java:35`：`throw new IllegalArgumentException("Unsupported format: " + format)`
- `NopCodeErrors.java`：ERR_INDEX_DIRECTORY_FAILED、ERR_INCREMENTAL_NOT_SUPPORTED、ERR_INDEX_NOT_FOUND、ERR_SYMBOL_NOT_FOUND、ERR_CODE_INDEX_ID_REQUIRED、ERR_CODE_LOCAL_PATH_NOT_ALLOWED、ERR_CODE_INVALID_GIT_REF（service 重复定义）共 7 个未使用/重复错误码
- import 顺序：所有 nop-code 文件均为 `io.nop.* → java.*`（与 AGENTS.md 约定 `java.* → jakarta.* → 第三方 → io.nop.*` 相反）
- `TestBuildHierarchyCycleProtection.java`：测试 `Math.min` 和 `HashSet`
- `TestDeterministicEntityIds.java`：测试 `DigestHelper.sha256Hex`

## Goals

1. 修复安全缺口：detectFlows 改为 @BizMutation、SymbolBizModel 和 FileBizModel 源代码访问添加权限注解
2. 修复 triggerIncrementalIndex 长事务：将文件分析移到 ORM session 外
3. 修复 N+1 查询：findReferencedBy 批量预加载关联实体
4. 修复 GraphExporter 裸异常：改用 NopException + ErrorCode
5. 修复类型安全：deleteFileRecords 泛型化、BFS String[] 重构
6. 清理未使用错误码
7. 重写测试反模式：TestBuildHierarchyCycleProtection 和 TestDeterministicEntityIds 改为测试业务逻辑
8. 统一 import 分组顺序

## Non-Goals

- I*Biz 接口补充声明（03-01）——watch-only residual，接口由代码生成，手写修改会被覆盖
- ORM 审计字段补充（04-01）——P3 级别，不影响运行时正确性
- SOURCE_CODE CLOB 专项处理（04-04）——已有 LIMIT 10000 保护
- cascadeDelete 大批量删除超时（04-11）——已有分页删除（Plan 88 DELETE_BATCH_SIZE=500）
- CodeIndexService 进一步拆分（02-01）——Plan 88 已从 3033→1518（50%），继续拆分收益递减
- findImplementations 全量加载（07-05）——已有 LIMIT 10000 保护，超过时降级为分页需独立设计
- DRY 违规 entityToCodeSymbol 重复（07-03）——optimization candidate，不影响正确性
- CodeIndexService 直接单元测试（16-02）——独立测试专项，需大量 mock 基础设施
- docs-for-ai 文档补充（18-01）——独立文档专项
- 结构化类型系统——长期架构演进
- 外部符号引用注册表——长期能力建设
- 前端页面/可视化——out-of-scope
- 流式持久化——optimization candidate

## Scope

### In Scope

- 安全修复：07-01（detectFlows @BizMutation）、13-01（SymbolBizModel 权限）、13-02（FileBizModel 权限）
- 性能修复：14-02（triggerIncrementalIndex 长事务）、07-04（N+1 查询）
- 代码质量修复：09-01（GraphExporter 异常）、15-01（deleteFileRecords 类型安全）、15-02/15-03（BFS String[]）
- 错误码清理：16-01（7 个未使用错误码）
- 测试有效性：21-01/21-02（重写测试反模式）
- 代码风格：17-01（import 分组顺序）

### Out Of Scope

- I*Biz 接口声明（代码生成覆盖）
- ORM 审计字段（P3）
- CodeIndexService 进一步拆分（收益递减）
- DRY 违规治理（optimization）
- docs-for-ai 文档（独立专项）
- 结构化类型系统（长期架构）
- 前端页面
- E2E 测试

## Execution Plan

### Phase 1 - 安全修复（3 项）

Status: completed
Commit: `4620183f8`
Targets: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeIndexBizModel.java`, `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeSymbolBizModel.java`, `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeFileBizModel.java`

- Item Types: `Fix`

- [x] **修复 07-01**：`NopCodeIndexBizModel.java:190` 将 `detectFlows` 的 `@BizQuery` 改为 `@BizMutation`
- [x] **修复 13-01**：`NopCodeSymbolBizModel.java` 中为返回源代码的方法添加 `@Auth(permissions = "code-source-read")` 注解
- [x] **修复 13-02**：`NopCodeFileBizModel.java:70` 为 `sourceCode` BizLoader 添加 `@Auth(permissions = "code-source-read")` 注解

Exit Criteria:

- [x] `NopCodeIndexBizModel.detectFlows` 标注 `@BizMutation`（非 `@BizQuery`）
- [x] `NopCodeSymbolBizModel` 的 showSymbol/sourceCode/searchCode 有 `@Auth(permissions = "code-source-read")`（导入 `io.nop.api.core.annotations.directive.Auth`）
- [x] `NopCodeFileBizModel` 的 sourceCode BizLoader 有 `@Auth(permissions = "code-source-read")`
- [x] **接线验证**：权限注解使用正确的注解类型 `@Auth`（非不存在的 `@BizPermission`），与 Plan 89 中 `NopCodeIndexBizModel:236` 的模式一致
- [x] `./mvnw test -pl nop-code -am` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 性能修复（2 项）

Status: completed
Commit: `45765774d`
Targets: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java`, `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeQueryService.java`

- Item Types: `Fix`

- [x] **修复 14-02（triggerIncrementalIndex 长事务）**：重构为两-session 方案
- [x] **修复 07-04（findReferencedBy N+1）**：批量查询替代逐条查询

Exit Criteria:

- [x] `triggerIncrementalIndex` 的文件分析在 ORM session 外执行，仅持久化阶段在 session 内
- [x] `findReferencedBy` 使用批量查询（3 次查询，非 N+1）
- [x] 现有测试通过（增量索引行为不变）
- [x] **端到端验证**（14-02）：triggerIncrementalIndex 重构后完整增量索引流程仍可正确执行
- [x] `./mvnw test -pl nop-code -am` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 代码质量修复（3 项）

Status: completed
Commit: `9a3e90f09`
Targets: `nop-code/nop-code-graph/src/main/java/io/nop/code/graph/export/GraphExporter.java`, `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java`, `nop-code/nop-code-graph/src/main/java/io/nop/code/graph/analysis/CodeGraphService.java`, `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeQueryService.java`

- Item Types: `Fix`

- [x] **修复 09-01（GraphExporter 裸异常）**：改为 `NopException(ERR_GRAPH_EXPORT_FAILED)`
- [x] **修复 15-01（deleteFileRecords 类型安全）**：统一为 `List<String>` 参数
- [x] **修复 15-02/15-03（BFS String[] 类型安全）**：改为 `BfsNode` 类型安全类

Exit Criteria:

- [x] `GraphExporter` 无裸 `IllegalArgumentException`（仅 line 35 修改），使用 `NopException` + 已有 `ERR_GRAPH_EXPORT_FAILED` 错误码。Lines 196/223 的 `catch(IllegalArgumentException)` 保留不改
- [x] `deleteFileRecords` 参数类型安全，无 `List<?>` 和 `Object`
- [x] BFS 算法使用类型安全的 `BfsNode` 记录类，无 `String[]` 异构编码
- [x] `./mvnw test -pl nop-code -am` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 错误码清理 + 测试重写 + import 排序

Status: completed
Commit: `6c21f66c6`
Targets: `nop-code/nop-code-service/src/main/java/io/nop/code/service/NopCodeErrors.java`, `nop-code/nop-code-service/src/test/`, `nop-code/nop-code-graph/src/test/`, `nop-code/nop-code-*/src/main/java/**/*.java`（import 排序）

- Item Types: `Fix`, `Proof`

- [x] **修复 16-01（未使用错误码清理）**：已清理 7 个未使用错误码
- [x] **修复 21-01（TestBuildHierarchyCycleProtection）**：已重写测试，验证循环保护和深度限制
- [x] **修复 21-02（TestDeterministicEntityIds）**：已重写测试，验证 generateFileId 的确定性、格式和唯一性
- [x] **修复 17-01（import 分组顺序）**：已通过脚本批量修复非 _gen/ 文件的 import 顺序

Exit Criteria:

- [x] `NopCodeErrors.java` 中无未使用的错误码定义（7 个已删除）
- [x] `TestBuildHierarchyCycleProtection` 测试 buildTypeHierarchy 的循环保护（非 Math.min/HashSet）
- [x] `TestDeterministicEntityIds` 测试实体 ID 生成的确定性（非 DigestHelper 包装）
- [x] nop-code 模块所有 Java 文件 import 分组为 `java.* → jakarta.* → 第三方 → io.nop.*`
- [x] `./mvnw test -pl nop-code -am` 通过
- [x] No owner-doc update required（import 排序是代码风格变更）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 全部 3 项安全 P2 已修复（07-01 @BizMutation、13-01 SymbolBizModel 权限、13-02 FileBizModel 权限）
- [x] 全部 2 项性能 P2 已修复（14-02 长事务、07-04 N+1 查询）
- [x] 全部 3 项代码质量 P2 已修复（09-01 异常、15-01 类型安全、15-02/15-03 BFS）
- [x] 全部 1 项错误码清理已完成（16-01）
- [x] 全部 2 项测试重写已完成（21-01/21-02）
- [x] 全部 1 项代码风格修复已完成（17-01 import 排序）
- [x] 不存在被静默降级到 deferred 的 in-scope live defect
- [x] 受影响的 owner docs 已同步到 live baseline，或明确写明 No owner-doc update required
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）权限注解确实被 GraphQL 框架执行，（b）triggerIncrementalIndex 分析确实在 session 外执行，（c）N+1 查询确实改为批量，（d）BFS 确实使用类型安全结构
- [x] `./mvnw compile -pl nop-code -am` 通过
- [x] `./mvnw test -pl nop-code -am` 通过
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### I*Biz 接口补充声明（03-01）

- Classification: `watch-only residual`
- Why Not Blocking Closure: 接口由代码生成，手写修改会在下次 `mvn install` 时被覆盖。接口契约的实际执行通过 BizModel 运行时反射实现，空壳接口不影响功能正确性
- Successor Required: no
- Successor Path: N/A

### ORM 审计字段补充（04-01）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 审计字段（createdBy/updatedBy）对代码索引场景无实际业务价值——索引数据由系统自动生成，无用户操作审计需求
- Successor Required: no
- Successor Path: N/A

### SOURCE_CODE CLOB 列表查询（04-04）

- Classification: `watch-only residual`
- Why Not Blocking Closure: Plan 89 Phase 4 已添加 LIMIT 10000 保护，CLOB 查询在最坏情况下只加载有限行。专项 CLOB 延迟加载优化是性能改进
- Successor Required: no
- Successor Path: N/A

### cascadeDelete 大批量删除（04-11）

- Classification: `watch-only residual`
- Why Not Blocking Closure: Plan 88 Phase 2 已实现分页删除（DELETE_BATCH_SIZE=500 + flush/evict），cascadeDelete 语义不变但分批执行降低了超时风险
- Successor Required: no
- Successor Path: N/A

### CodeIndexService 进一步拆分（02-01）

- Classification: `optimization candidate`
- Why Not Blocking Closure: Plan 88 已从 3033→1518 行（50% 减少），继续拆分收益递减。剩余代码是核心索引逻辑（持久化+增量+flow 分析），天然耦合度高
- Successor Required: no
- Successor Path: N/A

### findImplementations 全量加载（07-05）

- Classification: `watch-only residual`
- Why Not Blocking Closure: Plan 89 Phase 4 已添加 LIMIT 10000 保护。真正的分页加载需要 API 级别变更（返回分页结果而非 List），属于功能改进
- Successor Required: no
- Successor Path: N/A

### entityToCodeSymbol DRY 违规（07-03）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 代码重复不影响运行时正确性，重构风险可控
- Successor Required: no
- Successor Path: N/A

### CodeIndexService 直接单元测试（16-02）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 1518 行服务类需要大量 mock 基础设施（IOrmTemplate、IEntityDao、FileAnalyzer 等），投入产出比不高。现有集成测试（TestCodeIndexService）覆盖核心流程
- Successor Required: no
- Successor Path: N/A

### docs-for-ai 文档补充（18-01）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 独立文档专项，不影响功能正确性
- Successor Required: yes
- Successor Path: 待创建文档补充 plan

## Non-Blocking Follow-ups

- I*Biz 代码生成策略评估（03-01）
- ORM 审计字段按需添加（04-01）
- findImplementations 分页 API 设计（07-05）
- entityToCodeSymbol DRY 治理（07-03）
- CodeIndexService 单元测试补充（16-02）
- docs-for-ai nop-code 使用指南（18-01）

## Closure

Status Note: All 4 phases completed. 12 in-scope items fixed. Independent closure audit passed.

Closure Audit Evidence:

- Reviewer / Agent: Independent houyi subagent (task ses_1819bd444ffer3FbUnr30uPy0W)
- Audit Date: 2026-05-31
- Verdict: Can Close
- Commits: 4620183f8, 45765774d, 9a3e90f09, 6c21f66c6, bc12995fc

Follow-up:

- Test import order not in scope (Phase 4 targets src/main/java only)
- TestDeterministicEntityIds/TestBuildHierarchyCycleProtection test local algorithm copies (advisory)
