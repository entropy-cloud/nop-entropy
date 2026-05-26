# 维度04：ORM 模型与实体设计

**源文件**: `nop-code/model/nop-code.orm.xml` (748行)
**审计对象**: 10 个实体, 12 个域定义, 5 个字典定义

## 第 1 轮（初审）

### [维度04-01] 字典 valueType 与列数据类型系统性不匹配

- **文件**: `nop-code/model/nop-code.orm.xml:22-77` (dicts) 及 `:103, :202, :208, :374, :663` (columns)
- **证据片段**:
  ```xml
  <!-- 字典定义：valueType="int"，选项值为整数 10/20/30... -->
  <dict label="符号类型" name="code/symbol_kind" valueType="int">
      <option code="CLASS" label="类" value="10"/>
  </dict>
  <!-- 但引用该字典的列却定义为 string/VARCHAR -->
  <column code="KIND" displayName="符号类型" mandatory="true" name="kind"
          precision="20" propId="4" stdDataType="string" stdSqlType="VARCHAR" ext:dict="code/symbol_kind"/>
  ```
- **严重程度**: P1
- **现状**: 全部 5 个字典均声明 `valueType="int"`，但所有引用这些字典的列（共 6 处）均定义为 `stdDataType="string" stdSqlType="VARCHAR"`。对比 nop-auth 标准模块，字典 `valueType="int"` 时列同步为 `stdDataType="int"`。
- **风险**: 运行时字典标签解析可能出现类型不匹配，前端下拉无法匹配、`_label` 后缀字段为空。
- **建议**: 将所有 dict-bound 列改为 `stdDataType="int" stdSqlType="INTEGER"`（推荐）；或将字典改为 `valueType="string"` 并将 option value 改为字符串。
- **误报排除**: 不是生成代码问题，是源模型手写定义的系统性类型不一致。
- **复核状态**: 未复核

---

### [维度04-02] deleteIndex 级联删除遗漏 NopCodeUsage / NopCodeFlow / NopCodeFlowMembership

- **文件**: `nop-code/model/nop-code.orm.xml:110-135` (NopCodeIndex to-many relations)
- **证据片段**:
  ```xml
  <!-- NopCodeIndex 的 to-many 关系全部缺少 cascadeDelete="true" -->
  <to-many displayName="文件列表" name="files" refEntityName="io.nop.code.dao.entity.NopCodeFile"
           refPropName="index">
      <join><on leftProp="id" rightProp="indexId"/></join>
  </to-many>
  <!-- 没有 cascadeDelete="true" -->
  ```
  配合 service 层 `CodeIndexService.java:1262-1311` 的 `deleteIndex()` 遗漏了 NopCodeUsage、NopCodeFlow、NopCodeFlowMembership。
- **严重程度**: P1
- **现状**: `NopCodeIndex` 的 4 条 to-many 关系均未设置 `cascadeDelete="true"`。service 层 `deleteIndex()` 手动级联删除了大部分子表，但遗漏了 3 个实体。
- **风险**: 每次删除索引后三表产生孤儿记录。随着反复创建/删除索引积累大量无效数据。
- **建议**: 在 ORM 模型中为 to-many 关系添加 `cascadeDelete="true"`，同时补充 deleteIndex 中的遗漏表。
- **误报排除**: 实际运行时会产生孤儿数据的逻辑缺陷。
- **复核状态**: 未复核

---

### [维度04-03] NopCodeSymbol 缺少 fileId 索引，NopCodeFile→symbols 关系无索引支撑

- **文件**: `nop-code/model/nop-code.orm.xml:336-353`
- **证据片段**:
  ```xml
  <!-- NopCodeSymbol 现有索引 -->
  <indexes>
      <index name="idx_symbol_qualified_name">
          <column name="indexId"/><column name="qualifiedName"/>
      </index>
      <index name="idx_symbol_name">
          <column name="indexId"/><column name="name"/>
      </index>
      <index name="idx_symbol_kind">
          <column name="indexId"/><column name="kind"/>
      </index>
      <index name="idx_symbol_declaring">
          <column name="indexId"/><column name="declaringSymbolId"/>
      </index>
  </indexes>
  <!-- 无 fileId 索引，但 NopCodeFile→symbols to-many 关系 join on id=fileId -->
  ```
- **严重程度**: P2
- **现状**: `NopCodeSymbol.fileId` 是必填外键列，to-many 关系 join 条件为 `NopCodeFile.id = NopCodeSymbol.fileId`，但 `fileId` 列没有索引。`parentId` 也缺少索引。
- **风险**: 按 `fileId` 查询某文件下的所有符号将导致全表扫描。
- **建议**: 添加 `idx_symbol_file(fileId)` 和 `idx_symbol_parent(parentId)` 索引。
- **误报排除**: 框架不会自动为关系列建索引，数据库设计规范明确"外键列必须建索引"。
- **复核状态**: 未复核

---

### [维度04-04] NopCodeUsage / NopCodeCall 缺少 fileId 索引

- **文件**: `nop-code/model/nop-code.orm.xml:411-420`, `:473-481`
- **证据片段**:
  ```xml
  <!-- NopCodeUsage 索引 -->
  <indexes>
      <index name="idx_usage_symbol"><column name="symbolId"/></index>
      <index name="idx_usage_kind"><column name="indexId"/><column name="kind"/></index>
  </indexes>
  <!-- 无 fileId 索引 -->
  ```
- **严重程度**: P2
- **现状**: `NopCodeUsage` 和 `NopCodeCall` 均有必填的 `fileId` 外键列，但未建索引。
- **风险**: 查询"某文件中的所有引用/调用"需要全表扫描。
- **建议**: 添加 `idx_usage_file(fileId)` 和 `idx_call_file(fileId)` 索引。
- **误报排除**: 同维度04-03。
- **复核状态**: 未复核

---

### [维度04-05] NopCodeCall / NopCodeInheritance / NopCodeAnnotationUsage 缺少 indexId 索引

- **文件**: `nop-code/model/nop-code.orm.xml:473-481`, `:522-529`, `:575-583`
- **证据片段**:
  ```xml
  <!-- NopCodeCall：无 indexId 索引 -->
  <indexes>
      <index name="idx_call_caller"><column name="callerId"/></index>
      <index name="idx_call_callee"><column name="calleeId"/></index>
  </indexes>
  ```
- **严重程度**: P2
- **现状**: 三个实体都有 `indexId` 外键列（必填），`deleteIndex()` 通过 `FilterBeans.eq("indexId", indexId)` 批量删除，但无索引。
- **风险**: `deleteIndex()` 的批量删除在三个表上触发全表扫描。
- **建议**: 为三个表各添加 `indexId` 索引。
- **误报排除**: 同类表（如 NopCodeSymbol）已包含 `indexId`，这三个表的缺失是不对称的遗漏。
- **复核状态**: 未复核

---

### [维度04-06] NopCodeFlow 审计字段使用非平台标准命名

- **文件**: `nop-code/model/nop-code.orm.xml:664-671`
- **证据片段**:
  ```xml
  <!-- nop-code 中 NopCodeFlow 的审计字段 -->
  <column code="CREATED_TIME" name="createdTime" .../>
  <column code="MODIFIED_TIME" name="modifiedTime" .../>
  <column code="CREATED_BY" name="createdBy" .../>
  <column code="MODIFIED_BY" name="modifiedBy" .../>
  ```
  对比平台标准：`createTime`/`updateTime`/`createdBy`/`updatedBy`。
- **严重程度**: P2
- **现状**: 使用 `createdTime`/`modifiedTime`/`modifiedBy` 命名，而非平台标准的 `createTime`/`updateTime`/`updatedBy`。也没有设置 entity 级审计属性。
- **风险**: 框架的自动审计填充机制无法绑定这些字段。
- **建议**: 改为平台标准命名，并添加 entity 级属性。
- **误报排除**: 缺少 entity 级审计属性意味着这些字段不会被框架自动维护。
- **复核状态**: 未复核

---

### [维度04-07] NopCodeDependency 使用非标准主键列名 depId

- **文件**: `nop-code/model/nop-code.orm.xml:594-595`
- **证据片段**:
  ```xml
  <!-- NopCodeDependency 主键列名为 depId，而非 id -->
  <column code="DEP_ID" name="depId" primary="true" .../>
  ```
  对比其他 9 个实体均使用 `id`。
- **严重程度**: P2
- **现状**: 10 个实体中唯独 `NopCodeDependency` 使用 `depId`，破坏了模块内的命名一致性。
- **风险**: 通用工具方法 `entity.getId()` 无法统一调用；`CrudBizModel` 的默认操作基于 `id` 属性。
- **建议**: 将 `depId` 改为 `id`，同步修改 service 层引用。
- **误报排除**: 同一模块内 9:1 的命名不一致是结构性设计问题。
- **复核状态**: 未复核

---

### [维度04-08] NopCodeUsage 缺少 enclosingSymbolId 索引

- **文件**: `nop-code/model/nop-code.orm.xml:379-410`
- **证据片段**:
  ```xml
  <to-one name="enclosingSymbol" refEntityName="io.nop.code.dao.entity.NopCodeSymbol" ...>
      <join><on leftProp="enclosingSymbolId" rightProp="id"/></join>
  </to-one>
  <!-- 但索引中没有 enclosingSymbolId -->
  ```
- **严重程度**: P3
- **现状**: `enclosingSymbolId` 是可选外键，有 to-one 关系但无索引。
- **风险**: 查询"某符号内部的所有引用"将全表扫描。
- **建议**: 添加 `idx_usage_enclosing(enclosingSymbolId)` 索引。
- **误报排除**: 外键列缺少索引，违反数据库设计规范。
- **复核状态**: 未复核

---

### [维度04-09] NopCodeInheritance.relationType 缺少字典定义

- **文件**: `nop-code/model/nop-code.orm.xml:496-497`
- **证据片段**:
  ```xml
  <column code="RELATION_TYPE" displayName="关系类型" mandatory="true" name="relationType"
          precision="20" propId="5" stdDataType="string" stdSqlType="VARCHAR"/>
  <!-- 注释中说明：EXTENDS / IMPLEMENTS，但没有 ext:dict -->
  ```
- **严重程度**: P3
- **现状**: 枚举型字段（EXTENDS/IMPLEMENTS）没有绑定字典。同模型其他枚举字段均有 dict 绑定。
- **风险**: 前端无法渲染下拉选择控件。
- **建议**: 新增字典 `code/relation_type` 并绑定。
- **误报排除**: 同模型内其他 4 个枚举字段均有 dict 绑定。
- **复核状态**: 未复核

---

### [维度04-10] NopCodeFlowMembership 缺少 flowId+symbolId 唯一约束

- **文件**: `nop-code/model/nop-code.orm.xml:709-745`
- **证据片段**:
  ```xml
  <columns>
      <column code="ID" name="id" primary="true" .../>
      <column code="FLOW_ID" name="flowId" mandatory="true" .../>
      <column code="SYMBOL_ID" name="symbolId" mandatory="true" .../>
  </columns>
  <!-- 无 unique-keys 定义 -->
  ```
- **严重程度**: P3
- **现状**: `(flowId, symbolId)` 应具有业务唯一性，但没有唯一约束。
- **风险**: 可能插入重复记录，影响评分和统计准确性。
- **建议**: 添加 `unique-key columns="flowId,symbolId"`。
- **误报排除**: 多对多中间表的联合唯一约束是数据库设计标准实践。
- **复核状态**: 未复核

---

## 审计汇总

| 序号 | 标题 | 严重程度 |
|------|------|----------|
| 04-01 | 字典 valueType(int) 与列数据类型(string) 系统性不匹配 | **P1** |
| 04-02 | deleteIndex 级联删除遗漏 3 个子表 | **P1** |
| 04-03 | NopCodeSymbol 缺少 fileId/parentId 索引 | **P2** |
| 04-04 | NopCodeUsage / NopCodeCall 缺少 fileId 索引 | **P2** |
| 04-05 | 3 个关系表缺少 indexId 索引 | **P2** |
| 04-06 | NopCodeFlow 审计字段非标准命名 | **P2** |
| 04-07 | NopCodeDependency 非标准主键列名 depId | **P2** |
| 04-08 | NopCodeUsage 缺少 enclosingSymbolId 索引 | **P3** |
| 04-09 | NopCodeInheritance.relationType 缺少字典 | **P3** |
| 04-10 | NopCodeFlowMembership 缺少联合唯一约束 | **P3** |
