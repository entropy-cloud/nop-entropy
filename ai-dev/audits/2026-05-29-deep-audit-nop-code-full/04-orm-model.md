# 维度04：ORM 模型与实体设计

## 第 1 轮（初审）

### [维度04-01] 实体级审计属性声明缺失 — createTimeProp/createrProp/updateTimeProp/updaterProp 未配置

- **文件**: `nop-code/model/nop-code.orm.xml`
- **行号**: L91, L174, L226, L691, L765, L810（所有 entity 定义）
- **证据片段**:
  ```xml
  <entity className="io.nop.code.dao.entity.NopCodeFlow" displayName="执行流" ...>
  ```
  所有 11 个实体均无 createTimeProp/createrProp/updateTimeProp/updaterProp 属性。其中 NopCodeFlow（L723-730）和 NopCodeSemanticEdge（L836-841）有审计字段列，但实体元数据未关联。
- **严重程度**: P2
- **现状**: 所有 11 个实体的 entity 元素均未声明审计属性。NopCodeFlow 和 NopCodeSemanticEdge 有审计字段列定义，但未关联到实体级属性。
- **风险**: Nop ORM 引擎依赖实体级审计属性来自动填充审计字段。缺少声明意味着审计字段不会在持久化时自动设置，依赖业务代码手动赋值。
- **建议**: 为所有含审计字段的实体添加 createTimeProp="createTime" createrProp="createdBy" updateTimeProp="updateTime" updaterProp="updatedBy"。
- **信心水平**: 确定
- **误报排除**: 对比 nop-auth 模块，所有实体均正确声明了这些属性。
- **复核状态**: 未复核

### [维度04-02] 审计字段命名不一致 — createdTime vs createTime

- **文件**: `nop-code/model/nop-code.orm.xml`
- **行号**: L723-730（NopCodeFlow），L836-841（NopCodeSemanticEdge）
- **证据片段**:
  ```xml
  <!-- NopCodeFlow: -->
  <column name="createdTime" .../> <column name="modifiedTime" .../>
  <!-- NopCodeSemanticEdge: -->
  <column name="createTime" .../> <!-- 无 modifiedTime -->
  ```
- **严重程度**: P2
- **现状**: NopCodeFlow 使用 createdTime/modifiedTime/createdBy/modifiedBy。NopCodeSemanticEdge 使用 createTime/createdBy，无 updateTime/updatedBy。Nop 平台标准使用 createTime/updateTime/createdBy/updatedBy。
- **风险**: 命名不统一导致自动审计字段填充机制可能失效。NopCodeSemanticEdge 缺少更新时间/更新人字段无法追踪修改历史。
- **建议**: 统一所有实体审计字段命名为 createTime/updateTime/createdBy/updatedBy。为 NopCodeSemanticEdge 补充 updateTime/updatedBy 字段。
- **信心水平**: 很可能
- **误报排除**: 如果平台支持自定义映射可排除命名差异部分。但 NopCodeSemanticEdge 缺少更新字段是确定缺陷。
- **复核状态**: 未复核

### [维度04-03] 大部分实体缺少审计字段

- **文件**: `nop-code/model/nop-code.orm.xml`
- **行号**: L91-171（NopCodeIndex），L174-219（NopCodeFile），L226-395（NopCodeSymbol），L402-467（NopCodeUsage），L470-534（NopCodeCall），L537-586（NopCodeInheritance），L589-642（NopCodeAnnotationUsage），L649-685（NopCodeDependency），L765-808（NopCodeFlowMembership）
- **证据片段**:
  ```xml
  <!-- NopCodeIndex 9 列无审计字段 -->
  <!-- NopCodeSymbol 32 列无审计字段 -->
  <!-- NopCodeFlowMembership 仅有一个 createdTime -->
  ```
- **严重程度**: P3
- **现状**: 仅 NopCodeFlow（4个审计字段）和 NopCodeSemanticEdge（2个审计字段+delFlag）有审计字段。其余 8 个实体完全无审计字段。NopCodeFlowMembership 仅有一个 createdTime。
- **风险**: 数据血缘不可追溯。对于代码索引数据（可通过重新全量索引重建），影响相对可控。NopCodeFlowMembership 的半审计状态不规范。
- **建议**: 为 NopCodeIndex、NopCodeSymbol、NopCodeFile 等核心实体添加 createTime/createdBy。纯关联表可按需决定。
- **信心水平**: 很可能
- **误报排除**: 代码索引数据可由批量索引重建生成，审计字段意义相对有限。但 NopCodeFlowMembership 的半审计状态仍不规范。
- **复核状态**: 未复核

### [维度04-04] NopCodeSemanticEdge 关系缺少 refPropName — 无法反向导航

- **文件**: `nop-code/model/nop-code.orm.xml`
- **行号**: L864-873
- **证据片段**:
  ```xml
  <to-one name="sourceSymbol" refEntityName="io.nop.code.dao.entity.NopCodeSymbol" tagSet="pub">
  <!-- 无 refPropName -->
  <to-one name="targetSymbol" refEntityName="io.nop.code.dao.entity.NopCodeSymbol" tagSet="pub">
  <!-- 无 refPropName -->
  ```
- **严重程度**: P2
- **现状**: NopCodeSemanticEdge 的 sourceSymbol 和 targetSymbol 关系没有 refPropName，导致 NopCodeSymbol 无法通过 ORM 导航访问其关联的语义边集合。
- **风险**: 无法通过 symbol.getSourceSemanticEdges() ORM 关系导航。业务代码需要手动通过 DAO 查询。对比 NopCodeCall 的 caller 声明了 refPropName="callees"，此处不一致。
- **建议**: 为 sourceSymbol 添加 refPropName="sourceSemanticEdges"，为 targetSymbol 添加 refPropName="targetSemanticEdges"。
- **信心水平**: 确定
- **误报排除**: 图数据库模式的实体，双向导航是常见需求。
- **复核状态**: 未复核

### [维度04-05] NopCodeSemanticEdge 使用 delFlag 逻辑删除但实体未声明 useLogicalDelete

- **文件**: `nop-code/model/nop-code.orm.xml`
- **行号**: L810-841
- **证据片段**:
  ```xml
  <entity className="io.nop.code.dao.entity.NopCodeSemanticEdge" displayName="语义边" ...>
  <!-- 无 useLogicalDelete/deleteFlagProp -->
  ...
  <column code="DEL_FLAG" displayName="逻辑删除" name="delFlag" stdDomain="boolFlag"/>
  ```
- **严重程度**: P1
- **现状**: NopCodeSemanticEdge 有 delFlag 列，但实体级没有声明 useLogicalDelete="true" 和 deleteFlagProp="delFlag"。对比 nop-auth 的 NopAuthUser 正确声明了这些属性。
- **风险**: ORM 引擎不会自动将 delete 操作转化为软删除。实际删除将直接物理删除记录，delFlag 列形同虚设。查询时也不会自动过滤 delFlag=0 的记录。
- **建议**: 在实体声明中添加 deleteFlagProp="delFlag" useLogicalDelete="true"。
- **信心水平**: 确定
- **误报排除**: 几乎不可能误报。delFlag 列和 stdDomain="boolFlag" 明确表明意图是逻辑删除。
- **复核状态**: 未复核

### [维度04-06] NopCodeSemanticEdge.relationType 缺少字典定义

- **文件**: `nop-code/model/nop-code.orm.xml`
- **行号**: L824-825
- **证据片段**:
  ```xml
  <column code="RELATION_TYPE" displayName="关系类型" mandatory="true" name="relationType" precision="40" stdDataType="string" stdSqlType="VARCHAR"/>
  <!-- 无 ext:dict -->
  ```
- **严重程度**: P3
- **现状**: NopCodeSemanticEdge 的 relationType 字段无 ext:dict 引用。对比 NopCodeInheritance 的 relationType 正确使用了 ext:dict="code/relation_type"。
- **风险**: 前端无法自动渲染为下拉列表。
- **建议**: 为 relationType 添加合适的 ext:dict 引用。
- **信心水平**: 很可能
- **误报排除**: 如果语义边关系类型完全开放式（AI 提取器动态生成），可能不适合用字典。
- **复核状态**: 未复核

### [维度04-07] NopCodeFlow.memberships 关系缺少 cascadeDelete

- **文件**: `nop-code/model/nop-code.orm.xml`
- **行号**: L746-751
- **证据片段**:
  ```xml
  <to-many displayName="成员" name="memberships" refEntityName="io.nop.code.dao.entity.NopCodeFlowMembership" refPropName="flow">
  <!-- 无 cascadeDelete -->
  ```
- **严重程度**: P2
- **现状**: NopCodeIndex 的所有 9 个 to-many 关系都声明了 cascadeDelete="true"，但 NopCodeFlow 到 NopCodeFlowMembership 的 memberships 关系没有。
- **风险**: 直接删除单个 Flow 时，其关联的 Membership 不会被自动删除，造成孤立数据。
- **建议**: 为 memberships 关系添加 cascadeDelete="true"。
- **信心水平**: 确定
- **误报排除**: 如果业务层在删除 Flow 时手动清理 Membership 可排除，但 ORM 级联声明是更规范的做法。
- **复核状态**: 未复核

### [维度04-08] NopCodeSemanticEdge 软删除与 NopCodeIndex 级联物理删除语义矛盾

- **文件**: `nop-code/model/nop-code.orm.xml`
- **行号**: L164-169, L810-841
- **证据片段**:
  ```xml
  <!-- NopCodeIndex: cascadeDelete="true" -->
  <to-many name="semanticEdges" refEntityName="io.nop.code.dao.entity.NopCodeSemanticEdge" refPropName="index" cascadeDelete="true">
  <!-- NopCodeSemanticEdge 有 delFlag -->
  ```
- **严重程度**: P3
- **现状**: NopCodeIndex 对 NopCodeSemanticEdge 使用 cascadeDelete（物理删除），但 NopCodeSemanticEdge 有 delFlag（软删除意图）。策略矛盾。
- **风险**: 如果 useLogicalDelete 被修复（F04-05），级联删除将执行软删除（设 delFlag=1）而非物理删除，与其他子实体的物理删除行为不一致。
- **建议**: 统一策略：对于代码索引数据，推荐全部物理删除，移除 NopCodeSemanticEdge 的 delFlag。
- **信心水平**: 很可能
- **误报排除**: 如果平台框架 cascadeDelete + useLogicalDelete 交互已明确为软删除，则可能是有意设计。但与其他子实体不一致。
- **复核状态**: 未复核

### [维度04-09] displayName 硬编码中文而非使用 i18n key

- **文件**: `nop-code/model/nop-code.orm.xml`
- **行号**: 全文所有实体和列
- **证据片段**:
  ```xml
  displayName="代码索引"  displayName="索引ID"  displayName="编程语言"
  ```
- **严重程度**: P3
- **现状**: 所有 displayName 均硬编码为中文字符串，未使用 i18n key。en 翻译文件中全部是 null。
- **风险**: 国际化支持不完整。英文用户看到的所有 label 将是 null/空值。
- **建议**: 在实体声明中使用 i18n-en:displayName 属性提供英文翻译。补全 en i18n 文件。
- **信心水平**: 确定
- **误报排除**: 如果仅面向中文用户可接受。但 displayName 的 i18n 规范性仍需改进。
- **复核状态**: 未复核

### [维度04-10] NopCodeIndex.status 和 NopCodeFlow.status 无 tagSet 标记

- **文件**: `nop-code/model/nop-code.orm.xml`
- **行号**: L107-108, L721-722
- **证据片段**:
  ```xml
  <column code="STATUS" displayName="状态" name="status" ext:dict="code/index_status"/>
  <!-- 无 tagSet -->
  ```
- **严重程度**: P3
- **现状**: status 字段正确引用了字典，但没有 tagSet="status" 标记。
- **风险**: 如果平台依赖 tagSet="status" 进行自动状态管理，这些字段可能不会获得该功能。但代码索引模块可能不需要状态机。
- **建议**: 确认是否需要添加 tagSet 标记。如果只是显示用途，当前配置已足够。
- **信心水平**: 有趣的猜测
- **误报排除**: 该模块可能不需要自动状态管理。
- **复核状态**: 未复核

### [维度04-11] NopCodeDependency.resolved 字段类型使用 SMALLINT 而非 TINYINT

- **文件**: `nop-code/model/nop-code.orm.xml`
- **行号**: L663-664
- **证据片段**:
  ```xml
  <column code="RESOLVED" displayName="是否已解析" name="resolved" stdDataType="int" stdSqlType="SMALLINT"/>
  <!-- 无 stdDomain="boolFlag" -->
  ```
- **严重程度**: P3
- **现状**: resolved 字段语义为布尔值，但使用 SMALLINT 而非 TINYINT，且无 stdDomain="boolFlag"。
- **风险**: 浪费存储空间。前端无法自动渲染为开关控件。
- **建议**: 改为 stdSqlType="TINYINT" stdDataType="boolean" stdDomain="boolFlag"。
- **信心水平**: 确定
- **误报排除**: displayName="是否已解析" 暗示是布尔值。
- **复核状态**: 未复核

### [维度04-12] NopCodeSymbol 表宽度过大（32 列）稀疏列设计

- **文件**: `nop-code/model/nop-code.orm.xml`
- **行号**: L226-395
- **证据片段**: NopCodeSymbol 有 32 列。不同 kind 的符号使用不同子集的列，其余列值为 NULL。
- **严重程度**: P3
- **现状**: 采用单表继承模式统一存储所有符号类型。
- **风险**: 表宽度大导致行存储效率降低，无法在列级别添加 NOT NULL 约束。但这是代码索引场景的常见设计模式（如 Eclipse JDT、LSIF），性能影响在索引数据量级可接受。
- **建议**: 当前设计可接受。未来可考虑将特有字段移入 extData JSON 字段。
- **信心水平**: 很可能
- **误报排除**: 代码索引数据单表模式查询效率最高，是合理的架构选择。
- **复核状态**: 未复核

### [维度04-13] NopCodeIndex 到所有子实体的 cascadeDelete 可能导致大规模级联删除性能问题

- **文件**: `nop-code/model/nop-code.orm.xml`
- **行号**: L116-169
- **证据片段**:
  ```xml
  <!-- 9 个 to-many 关系全部 cascadeDelete="true" -->
  <to-many name="files" ... cascadeDelete="true">
  <to-many name="symbols" ... cascadeDelete="true">
  <!-- ... 共 9 个 -->
  ```
- **严重程度**: P2
- **现状**: 删除一个 NopCodeIndex 将级联删除 9 个关联表的所有数据。大型代码索引可能包含数十万条记录。
- **风险**: 单事务内大量 DELETE 可能导致数据库锁超时、事务过长。NopCodeSymbol 自引用关系可能在级联删除时产生递归问题。
- **建议**: 考虑使用 SQL 批量 DELETE 替代 ORM 级联删除。修复 F04-07 确保完整级联链。考虑在业务层实现分批删除。
- **信心水平**: 很可能
- **误报排除**: 如果 ORM 引擎能将级联删除优化为批量 SQL，性能风险可降低。
- **复核状态**: 未复核

### [维度04-14] 字典 label 使用中文硬编码而非 i18n key

- **文件**: `nop-code/model/nop-code.orm.xml`
- **行号**: L22-83
- **证据片段**:
  ```xml
  <dict label="符号类型" name="code/symbol_kind">
  <option code="CLASS" label="类" value="10"/>
  <option code="INTERFACE" label="接口" value="20"/>
  ```
- **严重程度**: P3
- **现状**: 所有 6 个字典的 label 和 option.label 均为中文硬编码。
- **风险**: 国际化场景下无法切换为英文显示。
- **建议**: 使用 i18n key 替代硬编码中文 label。
- **信心水平**: 确定
- **误报排除**: 仅面向中文用户可接受。
- **复核状态**: 未复核

### [维度04-15] NopCodeCall.caller 和 callee 的 refDisplayName 语义反转

- **文件**: `nop-code/model/nop-code.orm.xml`
- **行号**: L501-511
- **证据片段**:
  ```xml
  <to-one name="caller" refDisplayName="被调用者" ...>
  <to-one name="callee" refDisplayName="调用者" ...>
  ```
- **严重程度**: P2
- **现状**: caller 的 refDisplayName 是"被调用者"，callee 的 refDisplayName 是"调用者"。语义反了。
- **风险**: 前端展示关系时标签语义混乱。
- **建议**: 交换 refDisplayName：caller -> "调用者"，callee -> "被调用者"。
- **信心水平**: 确定
- **误报排除**: 几乎不可能误报。caller（发起调用者）不可能是"被调用者"。
- **复核状态**: 未复核

### [维度04-16] NopCodeSemanticEdge.confidence 字段缺少字典定义

- **文件**: `nop-code/model/nop-code.orm.xml`
- **行号**: L826-827
- **证据片段**:
  ```xml
  <column code="CONFIDENCE" displayName="置信度级别" mandatory="true" name="confidence" stdDataType="int" stdSqlType="INTEGER"/>
  <!-- 无 ext:dict -->
  ```
- **严重程度**: P3
- **现状**: confidence 是整型必填字段，表示置信度级别，但没有关联字典。
- **风险**: 前端无法展示为可读标签（如"高"/"中"/"低"）。
- **建议**: 定义 code/confidence_level 字典并为 confidence 字段添加 ext:dict。
- **信心水平**: 很可能
- **误报排除**: displayName="置信度级别" 和 confidenceScore 的并存暗示 confidence 是分级枚举。
- **复核状态**: 未复核

### [维度04-17] NopCodeFile 的反向关系 usages/calls 仅在生成代码中存在

- **文件**: `nop-code/model/nop-code.orm.xml`
- **行号**: L202-215
- **证据片段**: NopCodeFile 的 relations 中只有 index 和 symbols，没有 usages 和 calls。但 NopCodeUsage 的 file 声明了 refPropName="usages"。
- **严重程度**: P3（信息）
- **现状**: Nop ORM 反向关系自动推导机制正常工作，但模型文件阅读时不直观。
- **风险**: 低。增加模型文件维护难度。
- **建议**: 可考虑在模型中添加注释说明反向关系。低优先级。
- **信心水平**: 确定
- **误报排除**: 这是 Nop ORM 正常的反向引用机制，不是缺陷。
- **复核状态**: 未复核
