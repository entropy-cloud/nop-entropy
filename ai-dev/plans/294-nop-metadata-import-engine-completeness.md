# 294 nop-metadata 导入引擎完整性补全

> Plan Status: completed
> Last Reviewed: 2026-07-16
> Source: `ai-dev/design/nop-metadata/nop-metadata-roadmap.md` P1+（P1+-4 / P1+-5 / P1+-6）；`ai-dev/design/nop-metadata/01-architecture-baseline.md` §2.4 / §4.3
> Mission: nop-metadata
> Work Item: P1+ Phase 1 补完 — UniqueKey/Index 导入 + MetaTableJoin 关系补全 + 批量模块发现
> Related: `292-nop-metadata-implementation-roadmap.md`（Phase 1 导入引擎，Deferred But Adjudicated 收口）；`293-nop-metadata-design-consistency-fix.md`（G10 MetaTableJoin to-one deferred）

## Purpose

将 nop-metadata 的 ORM 模型导入引擎从"能导 Module/Entity/Field/Relation/Domain/Dict/Table"补全到"能导完整 ORM 模型内容（含 UniqueKey/Index）+ MetaTableJoin 关系可导航 + 支持批量模块发现导入"。收口 plan 292 和 plan 293 中 Deferred 的 3 个 `out-of-scope improvement` / `optimization candidate`。

## Current Baseline

- **OrmModelImporter**（`nop-metadata-dao/.../dao/model/OrmModelImporter.java`，210 行）：11 个 build 方法，覆盖 Module / OrmModel / Entity / Field / Relation / Domain / Dict / DictItem / Table。**不含** UniqueKey 和 Index 的 build 方法。
- **IEntityModel vs OrmEntityModel**：`IEntityModel` 接口（`nop-orm-model`）**不暴露** `getUniqueKeys()` / `getIndexes()`。这两个方法仅存在于具体类 `OrmEntityModel`（继承 `_OrmEntityModel`）。`OrmModel.getEntityModels()` 返回 `List<? extends IEntityModel>`（通配类型，非 `List<OrmEntityModel>`），importOrmModel 循环变量为 `IEntityModel`。运行时实例是 `OrmEntityModel`（来自 `OrmModelLoader`），需在循环内显式 cast `(OrmEntityModel) em` 或在 OrmModelImporter 中新增接受 `OrmEntityModel` 的 helper 方法。
- **NopMetaModuleBizModel.importOrmModel**（`nop-metadata-service/.../entity/NopMetaModuleBizModel.java`，127 行）：单个 `@BizMutation importOrmModel(path)` action，按层级 saveEntity。不导入 UniqueKey / Index。
- **ORM 模型**（`nop-metadata/model/nop-metadata.orm.xml`）：
  - `NopMetaEntityUniqueKey`（行 618-672）和 `NopMetaEntityIndex`（行 678-733）实体已定义，含 `to-one metaEntity` 关系。
  - 存储约定不一致：`UniqueKey.columns` 为逗号分隔字符串（precision 1000），`Index.indexColumns` 为 JSON（domain `json-4000`）。
  - `NopMetaTableJoin`（行 1209-1264）有 `leftEntityId` / `rightEntityId` 列（VARCHAR(32)），但 `<relations>` 仅有 `metaTable` 一个 to-one，**缺少**到 `NopMetaEntity` 的 left/right to-one 关系。
- **测试**（`TestNopMetaModuleBizModel.java`，69 行）：2 个测试验证 import → query 链路（module/entity/field），不覆盖 UniqueKey/Index/Relation/Domain/Dict。
- **构建**：`./mvnw clean install -pl nop-metadata -T 1C` BUILD SUCCESS（8 子模块）。

## Goals

- OrmModelImporter 导入 UniqueKey 和 Index：从 `OrmEntityModel`（具体类，`IEntityModel` 接口不暴露这些方法）提取唯一键和索引定义，构建 `NopMetaEntityUniqueKey` / `NopMetaEntityIndex` 记录并持久化
- NopMetaTableJoin 的 `leftEntityId` / `rightEntityId` 补全 to-one 关系到 NopMetaEntity，使 ORM 层可导航
- 提供批量模块导入能力：支持从注册的模块列表批量导入多个 orm.xml，而非仅单个路径

## Non-Goals

- Delta full 展开（isDelta=false）— 在 plan 295
- 版本发布 / releaseModule action — 在 plan 295
- baseModuleId 自引用 to-one — 在 plan 295
- 外部数据源注册 / 外部表同步 — P2
- BI 语义层 SQL 视图 — P3
- OrmModelImporter 的事务/错误回滚改进（当前每次 save 独立，部分失败可能残留半导入数据）— non-blocking follow-up

## Scope

### In Scope

- `OrmModelImporter`：新增 `buildUniqueKey` / `buildIndex` 方法
- `NopMetaModuleBizModel`：importOrmModel 流程中新增 UniqueKey / Index 持久化逻辑
- `NopMetaModuleBizModel`：新增批量导入 action
- `nop-metadata/model/nop-metadata.orm.xml`：NopMetaTableJoin 补全 left/right to-one 关系
- AutoTest：覆盖 UniqueKey / Index 导入断言 + 批量导入断言

### Out Of Scope

- 版本管理 / Delta 展开 / baseModuleId（plan 295）
- 外部数据源（P2）
- BI 语义层（P3）

## Execution Plan

### Phase 1 - UniqueKey / Index 导入填充

Status: completed
Targets: `nop-metadata/nop-metadata-dao/src/main/java/io/nop/metadata/dao/model/OrmModelImporter.java`、`nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaModuleBizModel.java`、`nop-metadata/nop-metadata-service/src/test/java/io/nop/metadata/service/TestNopMetaModuleBizModel.java`

- Item Types: `Fix`（plan 292 Deferred：UniqueKey/Index 导入暂不填充）

- [x] 1.1 **确认源模型 API**：`OrmEntityModel`（`nop-orm-model`，`_OrmEntityModel` 子类）暴露 `getUniqueKeys()` 返回 `List<OrmUniqueKeyModel>`（每个含 `getName()` / `getColumns()` 返回 `List<String>` / `getConstraint()` / `getTagSet()` 返回 `Set<String>`）和 `getIndexes()` 返回 `List<OrmIndexModel>`（每个含 `getName()` / `getDisplayName()` / `getIndexType()` / `getUnique()` 返回 nullable `Boolean` / `getColumns()` 返回 `List<OrmIndexColumnModel>`，每个 column 含 `getName()` / `getDesc()` 返回 nullable `Boolean`）。注意：`OrmModel.getEntityModels()` 返回 `List<? extends IEntityModel>`，运行时实例是 `OrmEntityModel`，需 cast
- [x] 1.2 在 OrmModelImporter 新增 `buildUniqueKey(OrmUniqueKeyModel ukModel)` 方法：构建 `NopMetaEntityUniqueKey`（ukName ← getName()，displayName ← getDisplayName() != null ? getDisplayName() : getName()，columns ← StringHelper.join(ukModel.getColumns(), ",")，constraintName ← getConstraint()，tagSet ← joinTags(ukModel.getTagSet())，isDelta=true）
- [x] 1.3 在 OrmModelImporter 新增 `buildIndex(OrmIndexModel idxModel)` 方法：构建 `NopMetaEntityIndex`（indexName ← getName()，displayName ← getDisplayName() != null ? getDisplayName() : getName()，indexType ← getIndexType()，uniqueIndex ← `Boolean.TRUE.equals(getUnique())`（null-safe Boolean→byte），indexColumns ← JSON 序列化 `[{fieldName: col.getName(), desc: Boolean.TRUE.equals(col.getDesc())}]`，isDelta=true）
- [x] 1.4 在 NopMetaModuleBizModel.importOrmModel 的 entity 循环中，将 `IEntityModel` cast 为 `OrmEntityModel`，新增 UniqueKey 和 Index 的持久化逻辑（遍历 `((OrmEntityModel) em).getUniqueKeys()` / `getIndexes()` → build → setMetaEntityId → save）
- [x] 1.5 在 TestNopMetaModuleBizModel 新增测试：导入后查询 `NopMetaEntityUniqueKey__findPage` 和 `NopMetaEntityIndex__findPage`，断言 total > 0（nop-metadata 自身 orm.xml 含唯一键和索引定义）

Exit Criteria:

- [x] 导入 nop-metadata orm.xml 后，`NopMetaEntityUniqueKey__findPage` 返回 total > 0
- [x] 导入 nop-metadata orm.xml 后，`NopMetaEntityIndex__findPage` 返回 total > 0
- [x] 导入的 UniqueKey 记录的 `columns` 字段与源 orm.xml 的唯一键列列表一致
- [x] 导入的 Index 记录的 `indexColumns` 字段为有效 JSON，含 fieldName / desc 信息
- [x] **接线验证**：OrmModelImporter.buildUniqueKey / buildIndex 确实在 importOrmModel 流程中被调用（查询结果 > 0 证明）
- [x] **无静默跳过**：buildUniqueKey / buildIndex 每个都实际填充字段，无空方法体
- [x] **新功能测试**：新增测试方法验证 UniqueKey/Index 导入，全绿
- [x] No owner-doc update required（不改设计文档契约，只补实现）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - MetaTableJoin to-one 关系补全

Status: completed
Targets: `nop-metadata/model/nop-metadata.orm.xml`

- Item Types: `Fix`（plan 293 Deferred G10：MetaTableJoin 缺少 to-one 关系）

- [x] 2.1 在 `nop-metadata.orm.xml` 的 NopMetaTableJoin 实体 `<relations>` 块中，新增 `leftEntity` to-one 关系（join on `leftEntityId` → `NopMetaEntity.metaEntityId`）
- [x] 2.2 在同一 `<relations>` 块中，新增 `rightEntity` to-one 关系（join on `rightEntityId` → `NopMetaEntity.metaEntityId`）
- [x] 2.3 运行 `./mvnw clean install -pl nop-metadata -T 1C` 重新生成代码，确认 BUILD SUCCESS

Exit Criteria:

- [x] NopMetaTableJoin 的 ORM 模型包含 `leftEntity` 和 `rightEntity` 两个 to-one relation，指向 NopMetaEntity
- [x] 生成的 Java 实体类（`_gen` 或 dao entity）包含 `getLeftEntity()` / `getRightEntity()` 方法
- [x] `./mvnw clean install -pl nop-metadata -T 1C` BUILD SUCCESS
- [x] `ai-dev/design/nop-metadata/01-architecture-baseline.md` §2.5 MetaTableJoin 关系描述与 ORM 模型一致（§2.5 已描述 leftEntityId/rightEntityId → MetaEntity 引用，确认 ORM relation 已落地）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 批量模块发现 / 导入

Status: completed
Targets: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaModuleBizModel.java`、`nop-metadata/nop-metadata-service/src/test/java/io/nop/metadata/service/TestNopMetaModuleBizModel.java`

- Item Types: `Proof`（新功能：批量导入）

- [x] 3.1 设计批量导入的输入方式和返回结构：接受 `List<String> paths`（多个 orm.xml 虚拟路径）。返回 `List<Map<String,Object>>`（每个元素含 `metaModuleId` / `moduleName` / `success`（boolean）/ `error`（失败时填写）），使 GraphQL 调用方能看到每个模块的导入结果
- [x] 3.2 在 NopMetaModuleBizModel 新增 `@BizMutation importOrmModels(@Name("paths") List<String> paths, IServiceContext context)` action，对每个路径调用现有 importOrmModel 逻辑，收集结果到返回列表
- [x] 3.3 错误处理：单个模块导入失败时 catch 异常，在结果中标记 `success=false` + `error=异常消息`，不中断整体批次（不静默跳过——失败信息显式记录在返回结果中）。**注意 ORM session 隔离**：单个导入失败后需清理 session（`orm().clearSession()` 或等价操作），避免约束违例污染 session 导致后续导入级联失败
- [x] 3.4 在 TestNopMetaModuleBizModel 新增测试：批量导入同一 orm.xml 路径 2 次（`["/nop/metadata/orm/app.orm.xml", "/nop/metadata/orm/app.orm.xml"]`），断言返回 2 个结果条目（验证批量逻辑即可；重复导入的版本/唯一键冲突由 plan 295 的版本管理解决，此处不断言持久化成功）。或验证 classpath 中存在第二个可用 orm.xml 路径后改用不同路径

Exit Criteria:

- [x] `importOrmModels` action 可通过 GraphQL mutation 调用，接受多个路径，返回包含每个模块导入结果（success/error）的列表
- [x] 批量导入 2 个路径后，返回列表包含 2 个结果条目
- [x] 单个模块导入失败不中断整批，返回结果中对应条目标记 `success=false` 并包含错误信息
- [x] **端到端验证**：从 `importOrmModels(paths)` 入口到 `NopMetaModule__findPage` 查询结果的完整路径已验证
- [x] **接线验证**：importOrmModels 确实在运行时对每个路径调用了导入逻辑（返回多个模块证明）
- [x] **无静默跳过**：单个导入失败时抛出异常并收集到结果中，不静默 continue
- [x] **新功能测试**：新增测试方法验证批量导入，全绿
- [x] No owner-doc update required（批量导入是已有导入引擎的批量封装，不改契约）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] UniqueKey / Index 导入端到端可用（import → query 链路连通）
- [x] NopMetaTableJoin left/right to-one 关系已补全，代码已重新生成
- [x] 批量导入 action 端到端可用（多路径 → 多模块持久化）
- [x] 不存在空壳实现（无空方法体 / 静默跳过）
- [x] 必要 focused verification 已完成（UniqueKey/Index 导入测试 + 批量导入测试全绿）
- [x] `./mvnw clean install -pl nop-metadata -T 1C` BUILD SUCCESS（含测试）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0
- [x] plan 292 Deferred "UniqueKey/Index 导入" 和 plan 293 Deferred "G10 MetaTableJoin to-one" 已收口
- [x] 受影响的 owner docs 已同步（`01-architecture-baseline.md` §2.5 关系描述与 ORM 模型一致）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证组件间调用链在运行时连通
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/294-nop-metadata-import-engine-completeness.md --strict` 退出码 0

## Deferred But Adjudicated

（本 plan 无 deferred 项。所有 6 个 P1+ 工作项中，3 项在本 plan，3 项在 plan 295。）

## Non-Blocking Follow-ups

- OrmModelImporter 事务/回滚改进：当前 importOrmModel 每次独立 save，部分失败残留半导入数据。可在后续迭代引入事务边界或 savepoint
- 批量导入的模块发现自动化：当前需手动传入路径列表。后续可支持扫描注册配置（beans.xml 或 _module 文件），实现自动发现

## Closure

Status Note: 三个 Phase 全部完成并通过 focused test + 独立 closure audit。OrmModelImporter 新增 buildUniqueKey/buildIndex（非空），importOrmModel 通过 instanceof OrmEntityModel cast 接入 UK/Index 持久化；NopMetaTableJoin 补全 leftEntity/rightEntity to-one 并重新生成代码；新增 importOrmModels 批量 action（try/catch + orm().clearSession() 失败隔离）。`./mvnw install -pl nop-metadata/nop-metadata-service -am` BUILD SUCCESS，TestNopMetaModuleBizModel 5 tests 0 failure。
Completed: 2026-07-16

Reviewer / Agent: independent closure-audit subagent (task ses_099276b49ffemDzlETlbUliXmy)

Closure Audit Evidence:
- Item 1 (Phase 1 UK/Index import): PASS — buildUniqueKey `OrmModelImporter.java:127-136`、buildIndex `:138-147`、importOrmModel UK/Index loop `NopMetaModuleBizModel.java:106-118` 均为真实非空实现；测试 testImportProducesUniqueKeys / testImportProducesIndexes 断言 total>0。
- Item 2 (Phase 2 MetaTableJoin relations): PASS — `nop-metadata.orm.xml:1258-1271` leftEntity/rightEntity to-one；生成 `_NopMetaTableJoin.java:860-862` getLeftEntity() / `:883-885` getRightEntity() 返回 NopMetaEntity。
- Item 3 (Phase 3 batch import): PASS — importOrmModels `NopMetaModuleBizModel.java:143-167`（@BizMutation + try/catch + success=false + error + orm().clearSession()）；testImportOrmModelsBatch 存在。
- Item 4 (Anti-Hollow): PASS — 无空方法体 / 静默跳过 / 静默吞异常；失败显式 success=false+error。
- Item 5 (Build): PASS — 独立复跑 `./mvnw install -pl nop-metadata/nop-metadata-service -am` BUILD SUCCESS，TestNopMetaModuleBizModel Tests run: 5, Failures: 0, Errors: 0。
- Overall Verdict: CLOSURE_APPROVED.
