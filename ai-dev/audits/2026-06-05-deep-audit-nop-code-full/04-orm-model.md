# 维度 04：ORM 模型与实体设计 — nop-code 模块

## 第 1 轮（初审）

### [维度04-01] 字典 `code/call_type` 被引用但从未定义

- **文件**: `nop-code/model/nop-code.orm.xml:546` 及 `22-87`
- **证据片段**:
  ```xml
  <!-- Line 546: NopCodeCall 实体引用 code/call_type -->
  <column code="CALL_TYPE" displayName="调用类型" name="callType"
          precision="20" propId="8" stdDataType="string" stdSqlType="VARCHAR" ext:dict="code/call_type"/>

  <!-- Lines 22-87: <dicts> 节定义了: code/symbol_kind, code/access_modifier, code/reference_kind,
       code/index_status, code/language, code/relation_type — 但没有 code/call_type -->
  ```
- **严重程度**: P1
- **现状**: NopCodeCall 实体的 CALL_TYPE 列声明 `ext:dict="code/call_type"`，但该字典在 ORM 模型的 `<dicts>` 节中从未定义。生成的 xmeta 也传播了 `dict="code/call_type"`。
- **风险**: UI 下拉渲染无选项；GraphQL schema 可能缺少枚举约束；运行时字典查找返回 null，可能导致 NPE 或静默空渲染。字段实际存储自由格式字符串（"void"、"String"、"CONSTRUCTOR"），dict 概念可能与实际数据模式根本不匹配。
- **建议**: 要么 (a) 添加 `code/call_type` 字典及匹配实际值的选项代码（CONSTRUCTOR、VIRTUAL_DISPATCH、STATIC_CALL 等），要么 (b) 移除 `ext:dict` 引用，因为该字段存储自由格式字符串。
- **信心水平**: 确定
- **误报排除**: 字典引用存在于源 ORM 模型并传播到生成的 xmeta，但字典定义缺失。grep 确认 `<dicts>` 节中 0 匹配 `code/call_type`。
- **复核状态**: 未复核

### [维度04-02] NopCodeAnnotationUsage 唯一键包含可空列 annotatedSymbolId

- **文件**: `nop-code/model/nop-code.orm.xml:668-714`
- **证据片段**:
  ```xml
  <!-- Line 668: annotatedSymbolId 无 mandatory="true" -->
  <column code="ANNOTATED_SYMBOL_ID" displayName="被注解符号ID" domain="codeId" name="annotatedSymbolId"
          propId="4" stdDataType="string" stdSqlType="VARCHAR"/>

  <!-- Line 714: 唯一键包含可空列 -->
  <unique-keys>
      <unique-key columns="indexId,annotationTypeId,annotatedSymbolId" name="uk_annotation_usage_unique"/>
  </unique-keys>
  ```
- **严重程度**: P1
- **现状**: 唯一约束 `uk_annotation_usage_unique` 定义在 `(indexId, annotationTypeId, annotatedSymbolId)` 上，但 `annotatedSymbolId` 是可空的。在大多数 SQL 数据库中，NULL 值不被视为相等，因此多行相同 indexId + annotationTypeId + NULL annotatedSymbolId 可以共存，静默违反预期的唯一性约束。
- **风险**: 当 `annotatedSymbolId` 为 NULL 时可以插入重复的注解使用记录，破坏数据完整性。这在包级或文件级注解不针对特定符号时会发生。
- **建议**: 要么 (a) 使 `annotatedSymbolId` 强制 (`mandatory="true"`)，如果注解总是针对符号，要么 (b) 重新设计唯一键以处理 NULL 语义。
- **信心水平**: 很可能
- **误报排除**: 第 668 行的列定义明确缺少 `mandatory="true"`，而唯一键中的其他两列（indexId, annotationTypeId）都有。SQL NULL 语义问题在主流数据库中已确立。
- **复核状态**: 未复核

### [维度04-03] NopCodeSemanticEdge 缺少 `i18n-en:displayName` — 唯一没有英文本地化的实体

- **文件**: `nop-code/model/nop-code.orm.xml:890-892`
- **证据片段**:
  ```xml
  <!-- Line 890-892: 缺少 i18n-en:displayName -->
  <entity className="io.nop.code.dao.entity.NopCodeSemanticEdge" displayName="语义边"
          name="io.nop.code.dao.entity.NopCodeSemanticEdge" registerShortName="true"
          tableName="nop_code_semantic_edge" useLogicalDelete="true" deleteFlagProp="delFlag">
  ```
  对比其他 10 个实体均有 `i18n-en:displayName` 属性。
- **严重程度**: P3
- **现状**: NopCodeSemanticEdge 是 11 个实体中唯一缺少 `i18n-en:displayName` 属性的实体。
- **风险**: 英文 locale UI 和 API 消费者将看到中文"语义边"而非本地化的英文字符串。
- **建议**: 添加 `i18n-en:displayName="Semantic Edge"` 到实体元素。
- **信心水平**: 确定
- **误报排除**: grep 确认文件中恰好 10 个 `i18n-en:displayName` 匹配，SemanticEdge 实体不在其中。
- **复核状态**: 未复核

### [维度04-04] Flow/FlowMembership/SemanticEdge 的审计字段缺少实体级自动填充属性

- **文件**: `nop-code/model/nop-code.orm.xml:765-804` (NopCodeFlow), `839-860` (FlowMembership), `890-922` (SemanticEdge)
- **证据片段**:
  ```xml
  <!-- NopCodeFlow entity: 无 createTimeProp/updateTimeProp/createrProp/updaterProp -->
  <entity className="io.nop.code.dao.entity.NopCodeFlow" displayName="执行流" ...>
      <!-- ... columns include createTime, updateTime, createdBy, updatedBy ... -->
  </entity>

  <!-- 对比: nop-auth NopAuthUser: -->
  <entity ... createTimeProp="createTime" createrProp="createdBy"
           updateTimeProp="updateTime" updaterProp="updatedBy" ...>
  ```
- **严重程度**: P2
- **现状**: NopCodeFlow、NopCodeFlowMembership 和 NopCodeSemanticEdge 都定义了审计列，但未声明对应的实体级属性 (`createTimeProp`、`updateTimeProp`、`createrProp`、`updaterProp`)。Nop ORM 框架使用这些实体级属性在 insert/update 时自动填充审计字段。没有它们，这些列被视为普通数据字段，不会被自动填充。
- **风险**: 审计字段将静默为空，除非 service 层手动设置。违背了拥有审计列的目的。
- **建议**: 添加实体级审计属性以匹配列定义。
- **信心水平**: 确定
- **误报排除**: 平台约定在 nop-auth 和 orm-gen.xlib 的 StdSysFieldsSupport 中已确立。NopCodeFlow 定义了 4 个审计列但无实体级属性。
- **复核状态**: 未复核

### [维度04-05] `ext:useStdFields="true"` 声明无效且与实际模型矛盾

- **文件**: `nop-code/model/nop-code.orm.xml:4`
- **证据片段**:
  ```xml
  <!-- Line 2-5: 声明 ext:useStdFields="true" -->
  <orm ext:useStdFields="true" ...>

  <!-- orm-gen.xlib line 25: 实际检查 ext:useStdSysFields（不同属性名） -->
  <thisLib:StdSysFieldsSupport xpl:if="_dsl_root.attrBoolean('ext:useStdSysFields')"/>
  ```
- **严重程度**: P2
- **现状**: 源模型声明 `ext:useStdFields="true"`，但 ORM codegen 模板检查的是 `ext:useStdSysFields` — 一个不同的属性名。这意味着该声明对 codegen 没有效果。之前审计计划 69 声称已修复此项，但当前源文件仍包含它。
- **风险**: (1) 声明制造了标准字段正在被管理的错误期望。(2) 未来开发者可能假设审计字段是自动添加的。(3) 之前审计退出标准被错误标记为完成。
- **建议**: 移除 `ext:useStdFields="true"`，如之前审计计划 69 中所计划的。如果确实需要标准系统字段，使用正确的属性名 `ext:useStdSysFields="true"`。
- **信心水平**: 确定
- **误报排除**: 源文件直接读取确认属性存在于第 4 行。orm-gen.xlib 确认不同的属性名。之前的计划 69 退出标准记录为已检查但与实际文件状态矛盾。
- **复核状态**: 未复核

### [维度04-06] 审计列 code 命名不一致: `CREATED_TIME` vs `CREATE_TIME`

- **文件**: `nop-code/model/nop-code.orm.xml:797-803` (Flow), `853-859` (FlowMembership), `918-920` (SemanticEdge)
- **证据片段**:
  ```xml
  <!-- NopCodeFlow: CREATED_TIME / UPDATE_TIME -->
  <column code="CREATED_TIME" displayName="创建时间" name="createTime" .../>

  <!-- NopCodeSemanticEdge: CREATE_TIME (匹配平台约定) -->
  <column code="CREATE_TIME" displayName="创建时间" name="createTime" .../>
  ```
- **严重程度**: P3
- **现状**: NopCodeFlow 和 NopCodeFlowMembership 使用 `CREATED_TIME`/`UPDATE_TIME`，而 NopCodeSemanticEdge 使用 `CREATE_TIME`（匹配平台约定）。
- **风险**: 不一致的 DDL 命名增加 DBA 和跨实体查询的认知负担。
- **建议**: 将 Flow 和 FlowMembership 对齐为 `CREATE_TIME`/`UPDATE_TIME`（移除 'D'）以匹配平台约定。
- **信心水平**: 很可能
- **误报排除**: 列 code 是源 XML 中明确不同的字符串。平台标准一致使用 `CREATE_TIME`。
- **复核状态**: 未复核

### [维度04-07] NopCodeIndex→SemanticEdge 的 cascadeDelete 与 SemanticEdge 的 useLogicalDelete 冲突

- **文件**: `nop-code/model/nop-code.orm.xml:169-170` 及 `892`
- **证据片段**:
  ```xml
  <!-- Line 169-170: cascade 删除 SemanticEdges -->
  <to-many ... refEntityName="...NopCodeSemanticEdge" cascadeDelete="true">

  <!-- Line 892: 但 SemanticEdge 使用逻辑（软）删除 -->
  <entity ... useLogicalDelete="true" deleteFlagProp="delFlag">
  ```
- **严重程度**: P2
- **现状**: NopCodeIndex 删除时，cascade 将设置 `delFlag=1` 而非物理删除 SemanticEdge 记录。这意味着软删除的 SemanticEdge 记录在父 Index 被物理删除后仍作为孤儿留在数据库中。
- **风险**: 孤儿软删除的 SemanticEdge 记录在数据库中积累，引用不存在的 indexId。
- **建议**: 要么 (a) 从 SemanticEdge 移除 `useLogicalDelete`，因为它是生命周期完全由 NopCodeIndex 控制的从属实体，要么 (b) 从关系中移除 `cascadeDelete`，通过 service 层代码处理。
- **信心水平**: 很可能
- **误报排除**: cascadeDelete 属性在关系上明确设置，useLogicalDelete 在实体上明确设置。这仅针对这一个实体对。
- **复核状态**: 未复核

### [维度04-08] NopCodeSemanticEdge relationType 复用 `code/relation_type` 字典，但该字典仅涵盖 EXTENDS/IMPLEMENTS

- **文件**: `nop-code/model/nop-code.orm.xml:904-905` 及 `83-87`
- **证据片段**:
  ```xml
  <!-- Line 904-905: SemanticEdge 使用 code/relation_type dict -->
  <column code="RELATION_TYPE" ... ext:dict="code/relation_type"/>

  <!-- Lines 83-87: 该字典仅定义 EXTENDS 和 IMPLEMENTS -->
  <dict name="code/relation_type" valueType="string">
      <option code="EXTENDS" value="10"/>
      <option code="IMPLEMENTS" value="20"/>
  </dict>
  ```
- **严重程度**: P2
- **现状**: SemanticEdge 的 `relationType` 列引用 `code/relation_type`，后者仅定义了继承关系。但 SemanticEdge 显然设计用于更广泛的语义分析（有 confidence scoring、extractors、rationale、directed graph 支持）。
- **风险**: 如果语义分析模块生成其他关系类型（CALLS、DEPENDS_ON、OVERRIDES 等），无法在受控词汇中表示。
- **建议**: 为语义边关系类型创建专用字典（如 `code/semantic_relation_type`），或移除字典引用。
- **信心水平**: 很可能
- **误报排除**: SemanticEdge 实体设计（confidence scores、extractors、directed flag）明确表明范围超出继承。字典定义仅限 2 个选项。
- **复核状态**: 未复核

### [维度04-09] NopCodeSemanticEdge 的 sourceSymbol/targetSymbol 关系缺少 refPropName

- **文件**: `nop-code/model/nop-code.orm.xml:946-955`
- **证据片段**:
  ```xml
  <!-- Lines 946-955: 无 refPropName -->
  <to-one name="sourceSymbol" refEntityName="...NopCodeSymbol" tagSet="pub">
      <join><on leftProp="sourceSymbolId" rightProp="id"/></join>
  </to-one>
  <to-one name="targetSymbol" refEntityName="...NopCodeSymbol" tagSet="pub">
      <join><on leftProp="targetSymbolId" rightProp="id"/></join>
  </to-one>
  ```
  NopCodeSymbol 的关系中无反向 to-many 用于 semantic edges。
- **严重程度**: P2
- **现状**: SemanticEdge 定义了到 Symbol 的 to-one 关系，但缺少 `refPropName`，NopCodeSymbol 上无反向 to-many 关系。无法从 Symbol 通过 ORM 关系模型导航到所有相关 SemanticEdges。
- **风险**: 需要为给定 symbol 查找语义边的代码必须发出单独查询而非使用 ORM 关系遍历。
- **建议**: 在 NopCodeSymbol 上添加两个反向 to-many 关系：`outgoingEdges` 和 `incomingEdges`。
- **信心水平**: 很可能
- **误报排除**: 模型中所有其他 to-one 关系都包含 `refPropName` 以建立双向导航。SemanticEdge 的 sourceSymbol/targetSymbol 是唯一缺少它的。
- **复核状态**: 未复核

## 最终保留项

| 编号 | 严重程度 | 文件 | 一句话摘要 |
|------|---------|------|-----------|
| 04-01 | P1 | nop-code.orm.xml:546 | 字典 code/call_type 被引用但从未定义 |
| 04-02 | P1 | nop-code.orm.xml:668-714 | 唯一键包含可空列 annotatedSymbolId |
| 04-03 | P3 | nop-code.orm.xml:890 | SemanticEdge 缺少英文本地化 |
| 04-04 | P2 | nop-code.orm.xml:765-922 | 3 个实体审计字段缺少自动填充属性 |
| 04-05 | P2 | nop-code.orm.xml:4 | useStdFields 声明属性名错误且与 codegen 不匹配 |
| 04-06 | P3 | nop-code.orm.xml:797-859 | 审计列命名不一致 CREATED_TIME vs CREATE_TIME |
| 04-07 | P2 | nop-code.orm.xml:169,892 | cascadeDelete 与 useLogicalDelete 语义冲突 |
| 04-08 | P2 | nop-code.orm.xml:904,83 | relationType 字典范围过窄 |
| 04-09 | P2 | nop-code.orm.xml:946-955 | SemanticEdge 关系缺少 refPropName |
