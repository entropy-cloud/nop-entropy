# 维度 04：ORM 模型与实体设计 — nop-code 模块审计报告

## 审计范围

- **审计文件**: `nop-code/model/nop-code.orm.xml`（970 行，1 个源模型文件）
- **实体数量**: 11 个实体

---

## 第 1 轮（初审）

### [维度04-01] NopCodeSemanticEdge 缺少 `i18n-en:displayName` 声明

- **文件**: `nop-code/model/nop-code.orm.xml:898-900`
- **证据片段**:
  ```xml
  <entity className="io.nop.code.dao.entity.NopCodeSemanticEdge" displayName="语义边"
          name="io.nop.code.dao.entity.NopCodeSemanticEdge" registerShortName="true"
          tableName="nop_code_semantic_edge" useLogicalDelete="true" deleteFlagProp="delFlag">
  ```
- **严重程度**: P2
- **现状**: 该模块的 11 个实体中，10 个都声明了 `i18n-en:displayName` 属性，唯独 `NopCodeSemanticEdge` 缺失。生成的英文 i18n 文件 `_nop-code.i18n.yaml` 中对应值为 `null`，证实英文标签确实未生成。
- **风险**: 英文环境下 UI 展示该实体名称时将显示 `null` 或回退到技术名称。这不是仅"看起来不优雅"的问题——生成的 i18n 产物中已有明确缺失值。
- **建议**: 补充 `i18n-en:displayName="Semantic Edge"`。
- **信心水平**: 高
- **误报排除**: 不是"未显式声明平台核心包依赖"类误报。这是业务实体级别的本地化完整性问题，且对比同文件其他 10 个实体的模式，缺失是确定的。
- **复核状态**: 未复核

### [维度04-02] NopCodeSemanticEdge 缺少 `ext:icon` 声明

- **文件**: `nop-code/model/nop-code.orm.xml:898-900`
- **证据片段**:
  ```xml
  <entity className="io.nop.code.dao.entity.NopCodeSemanticEdge" displayName="语义边"
          name="io.nop.code.dao.entity.NopCodeSemanticEdge" registerShortName="true"
          tableName="nop_code_semantic_edge" useLogicalDelete="true" deleteFlagProp="delFlag">
  ```
- **严重程度**: P3
- **现状**: 该模块 11 个实体中，10 个都声明了 `ext:icon`，唯独 `NopCodeSemanticEdge` 缺失。
- **风险**: 若 `NopCodeSemanticEdge` 参与后台菜单生成，其图标将回退到默认值。若为纯内部实体，则此问题可忽略。
- **建议**: 若该实体参与 UI 菜单展示，补充 `ext:icon="git-merge"` 或类似的 Lucide 图标名。
- **信心水平**: 中 — 取决于该实体是否参与 UI 菜单生成。
- **误报排除**: 基于 `model-first-development.md` 明确约定的检查项。
- **复核状态**: 未复核

### [维度04-03] NopCodeSemanticEdge provenance 字段 propId=15 跳跃，源文件声明顺序与 propId 值不一致

- **文件**: `nop-code/model/nop-code.orm.xml:922-931`
- **证据片段**:
  ```xml
  <column code="EXT_DATA" displayName="扩展数据" domain="jsonContent" name="extData"
          propId="11" stdDataType="string" stdSqlType="VARCHAR" ui:control="textarea"/>
  <column code="PROVENANCE" displayName="来源" name="provenance"
          precision="20" propId="15" stdDataType="string" stdSqlType="VARCHAR"/>
  <column code="CREATED_BY" displayName="创建人" name="createdBy"
          propId="12" stdDataType="string" stdSqlType="VARCHAR"/>
  <column code="CREATE_TIME" displayName="创建时间" name="createTime"
          propId="13" stdDataType="timestamp" stdSqlType="DATETIME"/>
  <column code="DEL_FLAG" displayName="逻辑删除" name="delFlag" precision="1"
          propId="14" stdDataType="boolean" stdSqlType="TINYINT" stdDomain="boolFlag"/>
  ```
- **严重程度**: P3
- **现状**: `PROVENANCE` 字段在源文件 XML 中排在 `EXT_DATA`(propId=11) 之后、`CREATED_BY`(propId=12) 之前，但其 `propId="15"`。Nop 平台按 propId 值排序，运行时行为不受影响。
- **风险**: 运行时无功能风险。但维护者阅读源文件时容易误判字段顺序，后续基于源文件位置插入新字段可能产生 propId 冲突。
- **建议**: 将 `PROVENANCE` 的 propId 从 15 改为 12，后续审计字段顺延为 13、14、15。
- **信心水平**: 高
- **误报排除**: 不是"看起来不优雅"类问题。propId 值与源文件位置不一致会导致维护性错误。
- **复核状态**: 未复核

### [维度04-04] NopCodeSemanticEdge.relationType 引用 `code/relation_type` 字典（继承关系），与语义边场景不匹配

- **文件**: `nop-code/model/nop-code.orm.xml:912-913`（字段定义），`:87-91`（字典定义）
- **证据片段**:
  ```xml
  <!-- 字段引用 -->
  <column code="RELATION_TYPE" displayName="关系类型" mandatory="true" name="relationType"
          precision="40" propId="6" stdDataType="string" stdSqlType="VARCHAR" ext:dict="code/relation_type"/>
  
  <!-- 字典定义 -->
  <dict label="继承关系类型" name="code/relation_type" valueType="string">
      <description>类型继承关系</description>
      <option code="EXTENDS" label="继承" value="10"/>
      <option code="IMPLEMENTS" label="实现" value="20"/>
  </dict>
  ```
- **严重程度**: P2
- **现状**: `code/relation_type` 字典名为"继承关系类型"，只有 `EXTENDS` 和 `IMPLEMENTS` 两个选项。但 `NopCodeSemanticEdge.relationType` 字段 `precision="40"` 且该实体用于表达通用语义边，其关系类型远不止继承/实现。
- **风险**: 如果运行时存储了超出字典定义的值，`ext:dict` 声明成为误导——UI 下拉将只展示 EXTENDS/IMPLEMENTS 两个选项，而实际数据包含更多类型。
- **建议**: 为语义边创建独立字典（如 `code/semantic_relation_type`），或移除 `ext:dict` 声明。
- **信心水平**: 中高
- **误报排除**: 字典 label 和 description 明确限定为"类型继承关系"，与语义边通用关系定位矛盾。
- **复核状态**: 未复核

### [维度04-05] NopCodeSymbol 缺少 `enclosingUsages` to-many 反向关系定义

- **文件**: `nop-code/model/nop-code.orm.xml:507-512`（Usage 侧引用），`:340-425`（Symbol 侧关系列表）
- **证据片段**:
  ```xml
  <!-- NopCodeUsage 侧 -->
  <to-one name="enclosingSymbol" refDisplayName="引用" refEntityName="io.nop.code.dao.entity.NopCodeSymbol"
          refPropName="enclosingUsages" tagSet="pub">
      <join>
          <on leftProp="enclosingSymbolId" rightProp="id"/>
      </join>
  </to-one>
  ```
- **严重程度**: P2
- **现状**: `NopCodeUsage.enclosingSymbol` 的 `refPropName="enclosingUsages"` 指向 `NopCodeSymbol` 上的一个名为 `enclosingUsages` 的 to-many 属性，但 `NopCodeSymbol` 的关系列表中没有定义 `name="enclosingUsages"` 的 to-many 关系。
- **风险**: Nop ORM 的双向关系要求两端都显式声明。缺失可能导致运行时加载关系时报错或忽略。
- **建议**: 在 `NopCodeSymbol` 的关系列表中补充 `enclosingUsages` to-many 关系定义。
- **信心水平**: 中高
- **误报排除**: Nop ORM 的关系定义明确要求两端配对（同文件中所有其他双向关系均两端声明），此处的缺失是确定的。
- **复核状态**: 未复核

### [维度04-06] 模块内审计字段策略不一致，多个实体完全无审计字段

- **文件**: `nop-code/model/nop-code.orm.xml`（整个文件，涉及多个实体）
- **严重程度**: P2
- **证据片段**:
  ```xml
  <!-- NopCodeIndex 只有 createTime/updateTime，缺少 createdBy/updatedBy -->
  <column code="CREATED_TIME" displayName="创建时间" name="createTime"
          propId="10" stdDataType="timestamp" stdSqlType="DATETIME"/>
  <column code="UPDATE_TIME" displayName="更新时间" name="updateTime"
          propId="11" stdDataType="timestamp" stdSqlType="DATETIME"/>
  
  <!-- NopCodeFile 完全没有审计字段 -->
  <!-- NopCodeSymbol 完全没有审计字段 -->
  <!-- NopCodeFlow 有完整四字段审计 -->
  ```
- **现状**: 11 个实体中审计字段分布不一致。NopCodeIndex 只有 createTime/updateTime；NopCodeFile、NopCodeSymbol 等无审计字段；NopCodeFlow/NopCodeFlowMembership 有完整审计。
- **风险**: 缺少审计字段意味着无法追踪谁在何时创建/修改了数据。对于代码索引这种批量重建型数据，审计字段可能确实不必要，但策略不一致会导致维护困惑。
- **建议**: 核心实体（Index、File、Symbol）至少补齐 createTime/updateTime；关系实体如不需要审计追踪可保持现状，但需有明确的设计决策记录。
- **信心水平**: 中 — 审计字段是否必要取决于业务场景。对于批量重建型数据，审计字段可能确实不必要。
- **误报排除**: 问题在于同一模块内审计字段策略不一致，以及逻辑删除实体缺少 updateTime 的结构性缺陷。
- **复核状态**: 未复核

### [维度04-07] cascadeDelete 与 NopCodeSemanticEdge 的 useLogicalDelete 交互行为需确认

- **文件**: `nop-code/model/nop-code.orm.xml:128-183`
- **证据片段**:
  ```xml
  <to-many displayName="语义边" name="semanticEdges" refEntityName="io.nop.code.dao.entity.NopCodeSemanticEdge"
            refPropName="index" cascadeDelete="true">
  ```
- **严重程度**: P2
- **现状**: `NopCodeIndex` 对所有子实体都设置了 `cascadeDelete="true"`，包括启用了逻辑删除的 `NopCodeSemanticEdge`。
- **风险**: `cascadeDelete` 对逻辑删除实体的行为需要确认——是标记删除还是物理删除？
- **建议**: 确认 `cascadeDelete` 对逻辑删除实体的行为。如果框架不支持对逻辑删除实体的级联逻辑删除，需要额外处理。
- **信心水平**: 中
- **误报排除**: 不是泛泛地说"级联删除不好"。问题聚焦于 cascadeDelete 与 useLogicalDelete 的交互行为。
- **复核状态**: 未复核

### [维度04-08] NopCodeSemanticEdge 逻辑删除实体缺少 updateTime/updatedBy

- **文件**: `nop-code/model/nop-code.orm.xml:898-931`
- **证据片段**:
  ```xml
  <entity ... useLogicalDelete="true" deleteFlagProp="delFlag">
      ...
      <column code="CREATED_BY" displayName="创建人" name="createdBy"
              propId="12" stdDataType="string" stdSqlType="VARCHAR"/>
      <column code="CREATE_TIME" displayName="创建时间" name="createTime"
              propId="13" stdDataType="timestamp" stdSqlType="DATETIME"/>
      <column code="DEL_FLAG" displayName="逻辑删除" name="delFlag" precision="1"
              propId="14" stdDataType="boolean" stdSqlType="TINYINT" stdDomain="boolFlag"/>
  ```
- **严重程度**: P2
- **现状**: 该实体启用了逻辑删除但只有 `createdBy` 和 `createTime`，缺少 `updateTime` 和 `updatedBy`。逻辑删除本质是 UPDATE 操作，需要 `updateTime` 来记录何时被标记删除。
- **风险**: 无法追踪逻辑删除发生的时间。
- **建议**: 补充 `updateTime` 和 `updatedBy` 字段。
- **信心水平**: 高
- **误报排除**: 逻辑删除实体的核心语义是"UPDATE 而非 DELETE"，updateTime 是该语义的必要组成。
- **复核状态**: 未复核
