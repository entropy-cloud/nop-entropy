# 维度 04：ORM 模型与实体设计 — 审计报告

> 初审子 agent 输出，待复核

## 发现条目

### [维度04-01] NopMetaDictItem 的 isDelta 列 propId 跳序且与审计字段冲突

- **文件**: `nop-metadata/model/nop-metadata.orm.xml:1098-1137`
- **证据**:
  ```xml
  <column code="DICT_ITEM_ID" ... propId="1" .../>
  <column code="META_DICT_ID" ... propId="2" .../>
  ...
  <column code="INTERNAL" ... propId="10" .../>
  <column code="IS_DELTA" ... propId="17" .../>          ← propId 从 10 跳到 17
  <column code="VERSION" ... propId="11" .../>            ← 17 > 11，序列断裂
  <column code="CREATED_BY" ... propId="12" .../>
  ```
- **严重程度**: P2
- **现状**: `isDelta` 的 propId 被设为 17，但其物理位置在 `internal` (propId=10) 和 `version` (propId=11) 之间。后续审计字段使用 propId 11-16，与 isDelta 的 propId=17 产生交叉冲突。其他 31 个实体的 propId 均严格连续，仅此实体存在跳序。
- **风险**: 二进制序列化/反序列化通常依赖 propId 进行紧凑编码，跳序不影响正确性但导致非连续 ID 空间。未来新增列时需要仔细避开冲突，维护成本高。
- **建议**: 将 `isDelta` 的 propId 改为 11，后续审计字段 propId 依次改为 12-17。修正后重新 codegen 更新实体常量。
- **信心水平**: 高
- **误报排除**: 不是格式偏好问题，是唯一一个违反 propId 连续性的实体，说明是孤立引入的缺陷而非约定。

### [维度04-02] SQL 保留字用作数据库列代码（PRIMARY / CONSTRAINT）

- **文件**: `nop-metadata/model/nop-metadata.orm.xml:655-656, 822-823`
- **证据**:
  ```xml
  <!-- NopMetaEntityField -->
  <column code="PRIMARY" displayName="主键" domain="boolFlag" name="primaryField" propId="12"/>
  
  <!-- NopMetaEntityUniqueKey -->
  <column code="CONSTRAINT" displayName="约束名" name="constraintName" precision="100" propId="7"/>
  ```
- **严重程度**: P2
- **现状**: `PRIMARY` 和 `CONSTRAINT` 在 MySQL、PostgreSQL、Oracle 中均为 SQL 保留关键字。当 ORM 自动生成 DDL 时，如果 Dialect 实现未正确引用这些列名，生成的 CREATE TABLE 语句将产生语法错误。
- **风险**: 建表或 DML 操作中若列名未引号包裹可能导致解析失败。问题可能在某些 Dialect 中隐藏（如果 Nop 的 Dialect 已自动对所有列名加引号），但在未测试的数据库中可能暴露。
- **建议**: `code="PRIMARY"` → 改为 `code="IS_PRIMARY"` 或 `code="PRIMARY_FLAG"`；`code="CONSTRAINT"` → 改为 `code="CONSTRAINT_NAME"`。修改后需同步检查 codegen 生成的实体常量。
- **信心水平**: 中（风险取决于 Dialect 实现是否自动引用）

### [维度04-03] 关键子表 to-many 关系缺失 cascade-delete 标签

- **文件**: `nop-metadata/model/nop-metadata.orm.xml:242-290, 561-602, 395-416`
- **证据**:
  ```xml
  <!-- NopMetaModule → NopMetaOrmModel -->
  <to-many displayName="ORM模型集" name="ormModels"
           refEntityName="...NopMetaOrmModel" refPropName="metaModule"
           tagSet="pub,ref-pub"/>  <!-- 缺失 cascade-delete -->

  <!-- NopMetaModule → NopMetaTable -->
  <to-many displayName="逻辑表集" name="tables"
           refEntityName="...NopMetaTable" refPropName="metaModule"
           tagSet="pub,ref-pub"/>  <!-- 缺失 cascade-delete -->

  <!-- NopMetaEntity → NopMetaEntityField -->
  <to-many displayName="实体字段集" name="entityFields"
           refEntityName="...NopMetaEntityField" refPropName="metaEntity"
           tagSet="pub,ref-pub"/>  <!-- 缺失 cascade-delete -->
  ```
- **严重程度**: P3
- **现状**: 多个关键父子关系的 to-many 关联缺少 `cascade-delete` 标签。
- **风险**: 删除父实体时，关联的大量子表记录会成为孤儿数据。如果当前业务逻辑中先手动删除子记录再删父记录，则此问题被掩盖；但 ORM 层面缺少声明性保护，一旦有路径绕过了手动清理，数据完整性就会受损。
- **建议**: 在以下 to-many 关系上补充 `cascade-delete`：NopMetaModule→ormModels/tables/pipelines/qualityCheckpoints/manifests/reconciliationConfigs；NopMetaOrmModel→entities/domains/dicts；NopMetaEntity→entityFields/entityRelations/entityUniqueKeys/entityIndexes。
- **信心水平**: 高

### [维度04-04] NopMetaProfilingRule 外键列命名不一致（tableId vs metaTableId）

- **文件**: `nop-metadata/model/nop-metadata.orm.xml:2184-2185, 2215-2221`
- **证据**:
  ```xml
  <!-- NopMetaProfilingRule 使用 tableId -->
  <column code="TABLE_ID" displayName="剖析表ID" mandatory="true" name="tableId" precision="32" propId="4" .../>
  
  <!-- 对比其他实体统一的 metaTableId -->
  <!-- NopMetaCatalog -->
  <column code="META_TABLE_ID" ... name="metaTableId" .../>
  <!-- NopMetaQualityScore -->
  <column code="META_TABLE_ID" ... name="metaTableId" .../>
  ```
- **严重程度**: P3
- **现状**: NopMetaProfilingRule 引用 NopMetaTable 时外键列命名为 `tableId`，模型中其他 12 个引用 NopMetaTable 的实体全部使用 `metaTableId`。
- **风险**: 代码生成时生成的 Java 实体属性名不同，增加导航和记忆成本。在 SQL 查询和 GraphQL 查询中需要特殊处理。
- **建议**: 将 NopMetaProfilingRule 的 `tableId` 列更名为 `metaTableId`，保持一致。
- **信心水平**: 高

## 合规总结

| 检查项 | 评估 | 备注 |
|--------|------|------|
| 主键设计 | ✅ 全部符合规范 | VARCHAR(32) + tagSet="seq" |
| 字段类型与域使用 | ✅ 一致 | domains 定义清晰一致 |
| 关系定义 | ⚠️ 见 #04-03 | to-many 级联未完整声明 |
| displayName 本地化 | ✅ 全部完成 | 所有实体及列均配置 i18n-en:displayName |
| 审计字段 | ✅ 完整 | createTime/updateTime/createdBy/updatedBy 齐全 |
| 字段命名规范 | ⚠️ 见 #04-02 | PRIMARY/CONSTRAINT 使用了 SQL 保留字 |
| 索引覆盖 | ✅ 完善 | FK 列均建有索引 |
| dict 定义一致性 | ✅ 规范 | dict name 使用 kebab-case |
| 级联行为 | ⚠️ 见 #04-03 | 大量子表关系未声明 cascade-delete |
| propId 连续性 | ❌ 见 #04-01 | NopMetaDictItem 跳序 |
