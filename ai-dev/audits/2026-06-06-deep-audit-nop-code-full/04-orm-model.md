# 维度 04：ORM 模型与实体设计

## 第 1 轮（初审）

### [维度04-01] CodeRelationType 枚举有 MIXIN 但 code/relation_type 字典未包含

- **文件**: `nop-code/nop-code-core/src/main/java/io/nop/code/core/model/CodeRelationType.java:行号`
- **证据片段**:
  ```java
  // CodeRelationType 枚举包含 MIXIN 值
  EXTENDS(10), IMPLEMENTS(20), MIXIN(30);
  ```
  对比 `nop-code/model/nop-code.orm.xml` 中 code/relation_type 字典：
  ```xml
  <dict name="code/relation_type">
    <option value="EXTENDS" label="EXTENDS"/>
    <option value="IMPLEMENTS" label="IMPLEMENTS"/>
    <!-- 无 MIXIN 选项 -->
  </dict>
  ```
- **严重程度**: P2
- **现状**: Java 枚举 `CodeRelationType` 定义了 `MIXIN(30)` 值，但 ORM 模型中 `code/relation_type` 字典只包含 `EXTENDS` 和 `IMPLEMENTS`。如果代码中任何地方使用 `MIXIN` 值并存储到 `NopCodeInheritance.relationType` 列，通过 dict 验证时会找不到对应字典项。
- **风险**: 字典与枚举不同步，可能导致运行时字典验证失败或前端显示缺失。
- **建议**: 在 `code/relation_type` 字典中添加 `MIXIN` 选项，或者在确认 MIXIN 不被持久化使用后从枚举中移除。
- **信心水平**: 很可能
- **误报排除**: 这不是命名不一致的风格问题，而是枚举值与字典选项的数据完整性问题。
- **复核状态**: 未复核

### [维度04-02] code/language 字典已定义但 language 列未引用

- **文件**: `nop-code/model/nop-code.orm.xml`
- **证据片段**:
  ```xml
  <!-- 字典定义存在 -->
  <dict name="code/language">
    <option value="JAVA" .../>
    <option value="PYTHON" .../>
    <option value="TYPESCRIPT" .../>
    <option value="JAVASCRIPT" .../>
  </dict>
  
  <!-- 但 NopCodeIndex 的 LANGUAGE 列没有 ext:dict="code/language" -->
  <column name="language" code="LANGUAGE" stdDataType="String" ... />
  <!-- NopCodeFile 的 LANGUAGE 列同样没有 -->
  <!-- NopCodeSymbol 的 LANGUAGE 列同样没有 -->
  ```
- **严重程度**: P2
- **现状**: `code/language` 字典已定义了 4 种语言选项，但 `NopCodeIndex`、`NopCodeFile`、`NopCodeSymbol` 三个实体上的 `language` 列均未使用 `ext:dict="code/language"` 引用该字典。这意味着 GraphQL schema 和前端页面无法获得 language 字段的选项列表和显示名称映射。
- **风险**: 前端无法显示语言的下拉选择或格式化显示名称；GraphQL 客户端不知道 language 字段的有效取值范围。
- **建议**: 在三个实体的 `language` 列上添加 `ext:dict="code/language"`。
- **信心水平**: 确定
- **误报排除**: 字典已经定义好了但未被使用，这不是"看起来不优雅"的问题，而是功能未闭环。
- **复核状态**: 未复核

### [维度04-03] NopCodeIndex 索引前缀使用 idx_ 而其他实体使用 ix_

- **文件**: `nop-code/model/nop-code.orm.xml`
- **证据片段**:
  ```xml
  <!-- NopCodeIndex 使用 idx_ 前缀 -->
  <index name="idx_nop_code_index_status" .../>
  
  <!-- 其他实体使用 ix_ 前缀 -->
  <index name="ix_nop_code_symbol_index_id" .../>
  <index name="ix_nop_code_file_index_id" .../>
  ```
- **严重程度**: P3
- **现状**: NopCodeIndex 实体的索引命名使用 `idx_` 前缀，而模块内其他所有实体的索引命名使用 `ix_` 前缀。命名不一致。
- **风险**: 低维护风险。索引命名是数据库层面的约定，不影响功能，但增加了代码阅读时的认知负担。
- **建议**: 统一为 `ix_` 前缀（与模块内其他实体保持一致）。
- **信心水平**: 确定
- **误报排除**: 这不是功能性缺陷，而是命名一致性维护问题。在大量索引定义中，统一前缀有助于 grep 和管理。
- **复核状态**: 未复核

### [维度04-04] NopCodeSemanticEdge propId 值无序分配

- **文件**: `nop-code/model/nop-code.orm.xml`（NopCodeSemanticEdge 实体定义）
- **证据片段**:
  ```xml
  <column name="propId" code="PROP_ID" propId="15" .../>
  <!-- 后续列的 propId: 12, 13, 16, 17, 14 — 非递增顺序 -->
  ```
- **严重程度**: P3
- **现状**: NopCodeSemanticEdge 实体的列 propId 分配值（15, 12, 13, 16, 17, 14）不连续且无序，与其他实体严格递增的 propId 分配模式不一致。
- **风险**: 不影响功能，但可能是手工编辑的痕迹，增加了后续新增列时选择 propId 值的困惑。
- **建议**: 重新排序 propId 为连续递增值，或添加注释说明原因。
- **信心水平**: 很可能
- **误报排除**: 这不是功能性错误，propId 的无序性不影响运行时行为。但作为模型质量信号，值得关注。
- **复核状态**: 未复核

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|------|---------|---------|-----------|
| [维度04-01] | P2 | nop-code/model/nop-code.orm.xml | CodeRelationType.MIXIN 枚举值未在字典中定义 |
| [维度04-02] | P2 | nop-code/model/nop-code.orm.xml | code/language 字典已定义但 language 列未引用 |
| [维度04-03] | P3 | nop-code/model/nop-code.orm.xml | NopCodeIndex 索引前缀 idx_ 与其他实体 ix_ 不一致 |
| [维度04-04] | P3 | nop-code/model/nop-code.orm.xml | NopCodeSemanticEdge propId 值无序分配 |
