# 维度 04：ORM 模型与实体设计

## 第 1 轮（初审）

### [维度04-01] NopCodeFlow 审计字段命名偏离平台全仓约定

- **文件**: `nop-code/model/nop-code.orm.xml:737-744`
- **证据片段**:
  ```xml
  <column code="CREATED_TIME" displayName="创建时间" name="createdTime"
          propId="15" stdDataType="timestamp" stdSqlType="DATETIME"/>
  <column code="MODIFIED_TIME" displayName="修改时间" name="modifiedTime"
          propId="16" stdDataType="timestamp" stdSqlType="DATETIME"/>
  <column code="CREATED_BY" displayName="创建人" name="createdBy"
          precision="50" propId="17" stdDataType="string" stdSqlType="VARCHAR"/>
  <column code="MODIFIED_BY" displayName="修改人" name="modifiedBy"
          precision="50" propId="18" stdDataType="string" stdSqlType="VARCHAR"/>
  ```
- **严重程度**: P2
- **现状**: NopCodeFlow 实体使用 `createdTime`/`modifiedTime`/`modifiedBy` 作为审计字段名。全仓其他所有模块统一使用 `createTime`/`updateTime`/`updatedBy`（213 处 createTime vs 4 处 createdTime 仅在 nop-code）。同一模块内部也不一致：NopCodeSemanticEdge 使用 `createTime`/`createdBy`（符合平台约定）。
- **风险**: 平台自动审计字段填充机制（OrmEntityListener 等）依赖标准字段名，使用非标准命名可能导致自动填充失效。模块内部命名不一致增加认知负担。DB 列名与平台标准不匹配。
- **建议**: 将 NopCodeFlow 和 NopCodeFlowMembership 的审计字段名统一为 `createTime`/`updateTime`/`createdBy`/`updatedBy`。
- **信心水平**: 确定
- **误报排除**: 平台有自动审计字段填充机制依赖标准字段名，使用非标准命名可能导致自动填充失效，属于功能性行为偏差。
- **复核状态**: 未复核

### [维度04-02] NopCodeFlowMembership 审计字段不完整

- **文件**: `nop-code/model/nop-code.orm.xml:793-795`
- **证据片段**:
  ```xml
  <column code="CREATED_TIME" displayName="创建时间" name="createdTime"
          propId="6" stdDataType="timestamp" stdSqlType="DATETIME"/>
  ```
- **严重程度**: P2
- **现状**: NopCodeFlowMembership 实体仅有 `createdTime` 一个审计字段，缺少 `createdBy`、`updateTime`/`modifiedTime`、`updatedBy`/`modifiedBy`。父实体 NopCodeFlow 有完整的 4 个审计字段。
- **风险**: 无法追踪"谁创建了这个关联关系"，只记录了时间但不知道操作人。如果关系被修改，没有任何更新时间/操作人记录。
- **建议**: 至少补充 `createdBy` 字段。审计字段命名也使用了非标准的 `createdTime`（同发现 04-01）。
- **信心水平**: 确定
- **误报排除**: 关系表只有 `createdTime` 而缺少 `createdBy` 是不对称的——记录了"何时"但没记录"谁"。
- **复核状态**: 未复核

### [维度04-03] code/language 字典已定义但从未被任何列引用，且 valueType 与列类型不匹配

- **文件**: `nop-code/model/nop-code.orm.xml:73-79`（字典定义），`行 103-104`（LANGUAGE 列）
- **证据片段**:
  ```xml
  <dict label="编程语言" name="code/language" valueType="int">
      <description>编程语言类型</description>
      <option code="JAVA" label="Java" value="10"/>
      <option code="PYTHON" label="Python" value="20"/>
      <option code="TYPESCRIPT" label="TypeScript" value="30"/>
      <option code="JAVASCRIPT" label="JavaScript" value="40"/>
  </dict>
  
  <column code="LANGUAGE" displayName="编程语言" domain="language" name="language"
          precision="20" propId="4" stdDataType="string" stdSqlType="VARCHAR"/>
  ```
- **严重程度**: P3
- **现状**: 字典 `code/language` 定义了 4 种编程语言（valueType=int），但 NopCodeIndex.LANGUAGE 和 NopCodeFile.LANGUAGE 均未引用此字典。即便引用，字典的 `valueType="int"` 与列的 `stdDataType="string"` 存在类型不匹配。
- **风险**: LANGUAGE 字段无输入验证，可插入任意字符串值。字典定义成为死代码。
- **建议**: 添加 `ext:dict="code/language"` 引用，或删除字典定义。考虑将 valueType 改为 "string"。
- **信心水平**: 确定
- **误报排除**: 定义了字典但不绑定，意味着数据完整性约束缺失。
- **复核状态**: 未复核

### [维度04-04] 英文 i18n 翻译全部缺失

- **文件**: `nop-code/nop-code-meta/src/main/resources/_vfs/i18n/en/_nop-code.i18n.yaml:1-235`
- **证据片段**:
  ```yaml
  entity:
    label:
      NopCodeAnnotationUsage: null
      NopCodeCall: null
  prop:
    label:
      NopCodeIndex:
        id: null
        name: null
  ```
- **严重程度**: P3
- **现状**: 英文 i18n 文件中所有实体标签和属性标签均为 `null`。中文翻译已自动生成且完整。
- **风险**: 英文界面下所有 nop-code 模块的实体名、字段名将显示为 null/空白。
- **建议**: 在 `en/nop-code.i18n.yaml` 中补充英文翻译，或在 ORM 模型中添加 `i18n-en:displayName` 属性。
- **信心水平**: 确定
- **误报排除**: i18n key 存在但值为 null，属于明确的 i18n 缺陷。降为 P3 因为当前模块可能暂无英文用户。
- **复核状态**: 未复核

## 检查通过项

1. **主键设计**: 全部 11 个实体统一使用 `domain="codeId"`（VARCHAR(36)）+ `tagSet="seq"`。
2. **域定义复用**: 12 个域在多实体间复用良好。
3. **索引覆盖**: 所有 FK 列均有索引覆盖。
4. **双向关系完整性**: refPropName 正确指向生成 XMeta 中自动补全的反向关系属性。
5. **级联删除**: NopCodeIndex 对子实体设置 cascadeDelete="true"。
6. **字典绑定**: STATUS、KIND、ACCESS_MODIFIER 等字段均绑定了字典。
7. **字段命名 snake_case**: 合规。
8. **无孤立实体**: 11 个实体均通过 NopCodeIndex 聚合根可达。

## 深挖第 2 轮追加

第 1 轮已完整覆盖所有实体定义。无新发现。深挖结束。

## 最终保留项

| 编号 | 严重程度 | 文件 | 一句话摘要 |
|------|---------|------|-----------|
| 04-01 | P2 | nop-code.orm.xml:737-744 | NopCodeFlow 审计字段命名偏离平台约定（createdTime vs createTime） |
| 04-02 | P2 | nop-code.orm.xml:793-795 | NopCodeFlowMembership 审计字段不完整（仅有 createdTime，缺 createdBy） |
| 04-03 | P3 | nop-code.orm.xml:73-79 | code/language 字典已定义但从未被引用，valueType 与列类型不匹配 |
| 04-04 | P3 | en/_nop-code.i18n.yaml | 英文 i18n 翻译全部为 null |
