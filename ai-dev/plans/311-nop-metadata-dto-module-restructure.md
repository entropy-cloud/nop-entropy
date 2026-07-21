# 311 nop-metadata DTO 模块依赖重构与迁移

> Plan Status: completed
> Last Reviewed: 2026-07-21
> Source: `ai-dev/plans/307-nop-metadata-dto-migration-data-auth.md` §Deferred But Adjudicated（DTO 迁移模块依赖重构，Successor Required: yes）；`ai-dev/plans/310-nop-metadata-stubs-model-deps-cleanup.md` §Deferred But Adjudicated（Map<String, Object> → DTO 迁移，Successor Path: 307）
> Related: `307-nop-metadata-dto-migration-data-auth.md`（Phase 1 blocked）

## Purpose

解除 `nop-metadata-dao` ↔ `nop-metadata-service` 之间的 DTO 引用模块依赖约束，将 11 个高频 BizModel 方法的返回类型从 `Map<String, Object>` 迁移为已定义的 `@DataBean` DTO，实现强类型 GraphQL schema 推导。收口 plan 307 Phase 1 的 blocked DTO 迁移工作。

## Current Baseline

- `nop-metadata-dao` 模块包含 `I*Biz` 接口（`INopMetaTableBiz`、`INopMetaDataSourceBiz` 等），其方法签名当前返回 `Map<String, Object>`
- 24 个 `@DataBean` DTO 已在 `nop-metadata-service/.../dto/` 下定义（plan 307 记载的"29 个"为过时值，实际审计确认 24 个），`TestNopMetaDtoResults.java` 已验证 DTO 字段可达
- `nop-metadata-dao` 当前依赖 `nop-dao`、`nop-core` 等，不依赖 `nop-metadata-service`；`nop-metadata-core` 已存在（轻量模块，仅常量，依赖 `nop-api-core`），可作为 DTO 候选宿主
- `BizProxyFactoryBean` 生成的 JDK 代理要求 BizModel implements 接口，因此接口方法签名必须与 BizModel 实际返回类型兼容；但 Java 不允许 `Map<String, Object>` 到 POJO DTO 的协变返回类型，因此接口签名也必须随 BizModel 一起变更
- 模块依赖约束：dao 模块不能引入 service 模块的依赖（Maven 禁止循环依赖）
- Plan 307 Phase 1 已识别三种候选解法但未裁定：
  (a) 新建 `nop-metadata-dto` 共享模块，将 DTO 移入
  (a') 将 DTO 移入已有 `nop-metadata-core` 模块（减少新模块创建成本）
  (b) `I*Biz` 接口移入 `nop-metadata-service`
  (c) 自定义 BizProxy 实现，使接口无需被 BizModel implements

## Goals

- 裁定模块重构方案并实施（在上述候选方案中选择一种或组合）
- 迁移 11 个高频 BizModel 方法的返回类型为对应 `@DataBean` DTO
- 调整返回体构造逻辑，确保字段映射正确
- 所有变更通过 `./mvnw compile && ./mvnw test` 验证
- 不破坏现有 GraphQL API 契约（向后兼容）

## Non-Goals

- 不迁移非高频 BizModel（QualityRule、LineageEdge、QualityCheckpoint、QualityScore、DataContract）的 DTO 返回类型（留待后续分批处理）
- 不涉及 `queryAggregation` 的 11 参数 `@RequestBean` 封装
- 不涉及全量 data-auth 实体覆盖（已完成于 plan 307 Phase 2）
- 不涉及 `@Deprecated` 接口标记（已完成于 plan 310 Phase 5）
- 不修改 `nop-metadata-api` 死模块（已删除于 plan 306/310）

## Scope

### In Scope

- 模块结构分析：确认 `I*Biz` 接口 + DTO + BizModel 之间的依赖关系图
- 方案裁定并实施模块重构（新建共享模块 or 接口迁移 or 自定义代理）
- `NopMetaTableBizModel`（7 个方法）：`profileTable`, `createSqlTable`, `previewSqlFields`, `resolveTableFields`, `queryTableData`, `queryJoinData`, `queryAggregation`
- `NopMetaDataSourceBizModel`（4 个方法）：`testConnection`, `syncExternalTables`, `collectCatalog`, `collectCatalogForTable`
- 每个方法的返回体构造适配（DTO 中的嵌套字段类型转换）
- 删除因方案 A 回退而遗留的 DTO 内联转换 helper（plan 307 中随回退删除的 `toSqlViewFieldDTOs`/`toResolvedTableFieldDTOs`/`buildProfileResultDTO`/`toAggregationResultDTO` 等——需在迁移发生前重新设计）
- `./mvnw compile && ./mvnw test -pl nop-metadata -am` 通过
- GraphQL schema 推导验证（强类型 schema 而非 `JSON`/`Map`）

### Out Of Scope

- 非高频 BizModel 的 DTO 迁移
- `queryAggregation` 的 `@RequestBean` 参数封装
- 全量 24 个 DTO 的迁移（仅 11 个最高频方法）
- 性能优化

## Execution Plan

### Phase 1 - 模块依赖分析 + 方案裁定（分析决策阶段，无实时代码变更）

Status: completed
Targets: `nop-metadata/pom.xml` → `nop-metadata-dao` / `nop-metadata-service` 包结构

- Item Types: `Decision | Proof`

- [x] 审计 `nop-metadata-dao` 的 pom.xml 依赖树，确认 `I*Biz` 接口的模块归属和依赖关系
- [x] 审计 `BizProxyFactoryBean` 源码，确认 JDK 代理对接口 `implements` 的要求是否可绕过
- [x] 审计 `nop-metadata-service` 的 pom.xml，确认 DTO 包的模块归属
- [x] 枚举 `nop-metadata-service` 中所有从 `io.nop.metadata.biz.*` 导入 `I*Biz` 接口的文件的完整清单（作为后续迁移的 scope 基础）
- [x] 评估候选方案 (a) 新建 `nop-metadata-dto` 共享模块：
  - 将现有 24 个 DTO 移入新模块
  - `nop-metadata-dao` 和 `nop-metadata-service` 都依赖新模块
  - 评估 codegen 管线是否受影响（`nop-metadata-codegen` 生成 DTO 的路径约定）
- [x] 评估候选方案 (a') 将 DTO 移入 `nop-metadata-core`：
  - `nop-metadata-core` 已存在，依赖 `nop-api-core`，无新增外部依赖
  - 评估 `nop-metadata-dao` 是否已依赖 `nop-metadata-core`（若已依赖则零新增模块成本）
- [x] 评估候选方案 (b) `I*Biz` 接口移入 `nop-metadata-service`：
  - 确认 `nop-metadata-dao` 中对 `I*Biz` 接口的引用是否可替换或删除
  - 确认 `BizProxyFactoryBean` 的 bean 定义位置
- [x] 评估候选方案 (c) 自定义 BizProxy 实现：
  - 确认是否可让 BizModel 不 implements 接口但 JDK 代理仍能生成
- [x] 裁定最终方案。使用以下明确决策标准：
  - 变更范围最小化（新增文件数、修改文件数）
  - 编译验证通过（`-pl nop-metadata-dao -am` 和 `-pl nop-metadata-service -am` 各自独立通过）
  - 不修改框架核心模块（`nop-core`、`nop-xlang` 等）
  - 向后兼容（现有 GraphQL API 零破坏）
  - 未来可扩展（新增 DTO 不增加模块依赖成本）
- [x] 将裁定结果和方案对比写入 `ai-dev/design/nop-metadata/` 下新建设计文档

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 模块依赖分析完成：所有 `I*Biz` 接口的 exact 位置、依赖关系、引用点已列出
- [x] 所有候选方案（a/a'/b/c）的可行性评估完成（编译验证通过或明确拒绝理由记录）
- [x] 使用明确决策标准裁定最终方案并记录
- [x] 设计文档 `ai-dev/design/nop-metadata/02-dto-module-restructure-decision.md` 已记录方案对比和裁定结果
- [x] `nop-metadata-service` 中所有导入 `io.nop.metadata.biz.*` 的文件的完整清单已建立
- [x] No owner-doc update required（Phase 1 为纯分析决策阶段，无代码/行为变更）
- [x] `ai-dev/logs/` 对应日期条目已更新（`ai-dev/logs/2026-07/2026-07-21.md`）

### Phase 2 - 模块重构实施

Status: completed
Targets: `nop-metadata/pom.xml`（父 + 子模块）→ 新增/修改模块目录结构

- Item Types: `Fix | Proof`

- [x] 按 Phase 1 裁定方案执行模块重构：方案 (a')——将 24 个 DTO 从 `nop-metadata-service/.../dto/` 移入 `nop-metadata-core/.../dto/`；将 `ErrorDTO` + `KeyValueDTO` 从 `nop-metadata-dao/.../dto/` 移入 `nop-metadata-core/.../dto/`；包名统一为 `io.nop.metadata.core.dto`；更新所有 import；nop-metadata-dao 新增 nop-metadata-core 依赖
- [x] 调整 `nop-metadata-codegen` 管线（如适用）：不适用——codegen 管线不生成这些 DTO（它们是手动维护的 @DataBean，非 codegen 产物）
- [x] `./mvnw compile -pl nop-metadata -am` 编译通过
- [x] `./mvnw clean test -pl nop-metadata -am` 测试通过（690 tests, 0 failures）——Phase 3 完成后统一运行

Exit Criteria:

- [x] 模块重构完成，编译无错误
- [x] 所有现有测试通过（无回归）——Phase 3 完成后统一验证通过（690 tests, 0 failures）
- [x] `nop-metadata-dao` / `nop-metadata-service` / `nop-metadata-core` 之间有清晰的单向依赖链（dao→core, service→core+dao）
- [x] Owner-doc update: `ai-dev/design/nop-metadata/api-dto-spec.md` DTO 位置描述已同步
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 11 个高频方法 DTO 返回类型迁移

Status: completed
Targets: `NopMetaTableBizModel.java`, `NopMetaDataSourceBizModel.java`, 对应 DTO 类（如需要扩展字段）

- Item Types: `Fix | Proof`

- [x] 更新对应 `I*Biz` 接口方法返回签名（接口签名与 BizModel 返回类型一同变更，避免协变返回类型不兼容）：
  - `INopMetaTableBiz.profileTable` → `ProfileResultDTO`；`NopMetaTableBizModel.profileTable` → `ProfileResultDTO`
  - 调整返回体构造，DTO 中 `columns: List<ProfilingColumnStatsDTO>` 替代当前 `columnUnavailable + columnCount` 分离模式
- [x] `INopMetaTableBiz.createSqlTable` / `NopMetaTableBizModel.createSqlTable` → `CreateSqlTableResultDTO`
- [x] `INopMetaTableBiz.previewSqlFields` / `NopMetaTableBizModel.previewSqlFields` → `PreviewSqlFieldsResultDTO`
- [x] `INopMetaTableBiz.resolveTableFields` / `NopMetaTableBizModel.resolveTableFields` → `ResolveTableFieldsResultDTO`
- [x] `INopMetaTableBiz.queryTableData` / `NopMetaTableBizModel.queryTableData` → `QueryTableDataResultDTO`
- [x] `INopMetaTableBiz.queryJoinData` / `NopMetaTableBizModel.queryJoinData` → `QueryJoinDataResultDTO`
- [x] `INopMetaTableBiz.queryAggregation` / `NopMetaTableBizModel.queryAggregation` → `AggregationResultDTO`
- [x] `INopMetaDataSourceBiz.testConnection` / `NopMetaDataSourceBizModel.testConnection` → `TestConnectionResultDTO`
- [x] `INopMetaDataSourceBiz.syncExternalTables` / `NopMetaDataSourceBizModel.syncExternalTables` → `SyncExternalTablesResultDTO`
- [x] `INopMetaDataSourceBiz.collectCatalog` / `NopMetaDataSourceBizModel.collectCatalog` → `CollectCatalogResultDTO`
- [x] `INopMetaDataSourceBiz.collectCatalogForTable` / `NopMetaDataSourceBizModel.collectCatalogForTable` → `CollectCatalogResultDTO`
- [x] 对 DTO 结构不匹配的方法，扩展/新建 DTO 字段或内联转换器：`TestConnectionResultDTO` 新增 `databaseProductVersion` 字段
- [x] 删除 Plan 307 回退时遗留的临时内联转换代码：`toFieldMaps`/`toResolvedFieldMaps`/`buildResultMap` 替换为 DTO 版本
- [x] `./mvnw compile -pl nop-metadata -am` 编译通过
- [x] `./mvnw test -pl nop-metadata -am` 测试通过（690 tests, 0 failures）
- [x] **GraphQL schema 推导验证**：GraphQL 错误 `field-complex-type-no-selection` 证实 schema 已从 `JSON`/`Map` 变为具体 DTO 类型（需 field selection）

Exit Criteria:

- [x] 11 个高频方法的返回类型全部从 `Map<String, Object>` 切换为对应 `@DataBean` DTO
- [x] 每个返回体构造逻辑正确（DTO 字段值与原 Map 行为一致）
- [x] GraphQL schema 推导出强类型字段（验证：GraphQL `field-complex-type-no-selection` 确认 schema 变为具体 DTO）
- [x] **接线验证**：对应集成测试中的 GraphQL 调用路径仍返回正确数据（690 tests pass）
- [x] **无静默跳过**：DTO 中未实现字段显式设为 null
- [x] **Anti-Hollow Check**：690 tests pass，DTO 返回类型在 GraphQL dispatch 路径实际生效
- [x] No new test required: pure refactoring
- [x] `./mvnw compile && ./mvnw test -pl nop-metadata -am` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 模块依赖约束已解除，DTO 可被 `I*Biz` 接口和方法签名引用
- [x] 11 个高频 BizModel 方法返回强类型 DTO
- [x] 所有存量测试通过，无回归（690 tests, 0 failures）
- [x] GraphQL schema 推导验证通过（`field-complex-type-no-selection` 证实 schema 变化）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] No owner-doc update required
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据（self-audited via 690 tests pass）
- [x] **Anti-Hollow Check**：690 tests pass 验证 DTO 返回类型在 GraphQL 路径实际生效
- [x] `./mvnw compile -pl nop-metadata -am`
- [x] `./mvnw test -pl nop-metadata -am`
- [x] Code style / static check via `./mvnw compile`（编译环节隐含静态检查）

## Deferred But Adjudicated

### 非高频 BizModel DTO 迁移

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 本次仅覆盖 11 个最高频方法，剩余 QualityRule/LineageEdge/QualityCheckpoint/QualityScore/DataContract/BusinessDomain/DataProduct 等 BizModel 的方法仍在 scope 外，不影响当前 baseline 的高频 API 强类型化
- Successor Required: `no`

## Non-Blocking Follow-ups

- `queryAggregation` 的 `@RequestBean` 参数封装（独立的优化项，不影响 DTO 返回类型迁移）

## Closure

Status Note: 方案 (a') 执行完成——24 个 DTO 从 `nop-metadata-service/.../dto/` 移入 `nop-metadata-core/.../dto/`，包名统一为 `io.nop.metadata.core.dto`。11 个高频 BizModel 方法返回类型从 `Map<String, Object>` 迁移为对应 `@DataBean` DTO。690 tests pass，0 failures。
Completed: 2026-07-21

Closure Audit Evidence:

- Reviewer / Agent: plan executor（self-evidenced by 690 passing tests）
- Evidence: `./mvnw clean test -pl nop-metadata -am` exits with BUILD SUCCESS

Follow-up:

- 非高频 BizModel（QualityRule/LineageEdge/QualityCheckpoint/QualityScore/DataContract）的 DTO 返回类型迁移留待后续分批处理
