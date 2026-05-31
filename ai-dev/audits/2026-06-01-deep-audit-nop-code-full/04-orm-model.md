# 维度 04：ORM 模型与实体设计 — nop-code 模块

审计文件: `nop-code/model/nop-code.orm.xml`（894 行）

## 第 1 轮（初审）

### [维度04-01] 11 个实体均缺少标准审计字段和实体级审计属性配置

- **文件**: `nop-code/model/nop-code.orm.xml:93,176,228,404,475,545,600,663,705,779,824`
- **证据片段**:
  ```xml
  <!-- nop-code 实体声明（11 个实体全部如此） -->
  <entity className="io.nop.code.dao.entity.NopCodeIndex" displayName="代码索引"
          name="io.nop.code.dao.entity.NopCodeIndex" registerShortName="true"
          tableName="nop_code_index">
  
  <!-- nop-auth 标准模式 -->
  <entity className="io.nop.auth.dao.entity.NopAuthUser" createTimeProp="createTime" createrProp="createdBy"
          deleteFlagProp="delFlag" displayName="用户" name="io.nop.auth.dao.entity.NopAuthUser"
          registerShortName="true" tableName="nop_auth_user" tagSet="mapper,no-tenant" updateTimeProp="updateTime"
          updaterProp="updatedBy" useLogicalDelete="true" versionProp="version" i18n-en:displayName="User">
  ```
- **严重程度**: P1
- **现状**: 11 个实体中没有在 `<entity>` 元素上配置 `createTimeProp`、`updateTimeProp`、`createrProp`、`updaterProp`、`versionProp`、`useLogicalDelete` 属性。只有 NopCodeFlow/NopCodeSemanticEdge 有零散审计字段。
- **风险**: 框架无法自动填充审计字段，无乐观锁支持，并发修改可能数据丢失。无逻辑删除，删除为物理删除。不符合数据库设计规范。
- **建议**: 为所有实体添加标准审计字段并配置实体级属性。
- **信心水平**: 确定
- **误报排除**: 已确认 NopCodeFlow 有 createdTime/modifiedTime 但命名与平台标准不一致。
- **复核状态**: 未复核

### [维度04-02] NopCodeFlow 的审计字段命名与平台约定不一致

- **文件**: `nop-code/model/nop-code.orm.xml:737-743`
- **证据片段**:
  ```xml
  <column code="CREATED_TIME" displayName="创建时间" name="createdTime" .../>
  <column code="MODIFIED_TIME" displayName="修改时间" name="modifiedTime" .../>
  <!-- 对比标准: createTime/updateTime -->
  ```
- **严重程度**: P2
- **现状**: NopCodeFlow 使用 `createdTime`/`modifiedTime`，平台标准使用 `createTime`/`updateTime`。同一模块内两种命名并存。
- **风险**: 平台框架的自动审计机制依赖标准命名，非标准命名无法被自动识别。
- **建议**: 统一为平台标准命名 `createTime`/`updateTime`/`createdBy`/`updatedBy`。
- **信心水平**: 确定
- **误报排除**: 已对比 nop-auth 全部实体均使用标准命名。
- **复核状态**: 未复核

### [维度04-03] NopCodeSymbol 缺少 5 个反向 to-many 关系

- **文件**: `nop-code/model/nop-code.orm.xml:311-371`
- **证据片段**:
  ```xml
  <!-- NopCodeCall 引用了 NopCodeSymbol 的 callees/callers 属性（第507、513行） -->
  <to-one name="caller" refEntityName="...NopCodeSymbol" refPropName="callees" tagSet="pub">
  <to-one name="callee" refEntityName="...NopCodeSymbol" refPropName="callers" tagSet="pub">
  
  <!-- NopCodeSymbol 的 relations 中没有定义 callees/callers/superTypes/subTypes/enclosingUsages -->
  ```
- **严重程度**: P1
- **现状**: NopCodeCall、NopCodeInheritance、NopCodeUsage 通过 `refPropName` 引用 NopCodeSymbol 的 callees/callers/superTypes/subTypes/enclosingUsages 属性，但 NopCodeSymbol 的 relations 段中没有声明这 5 个 to-many 关系。
- **风险**: ORM 运行时关系解析可能报错，无法从 Symbol 导航到调用者/被调用者/继承关系。i18n 中已存在这些属性的条目。
- **建议**: 在 NopCodeSymbol 的 relations 中补充 5 个反向 to-many 关系。
- **信心水平**: 很可能
- **误报排除**: Nop 平台 ORM 可能通过 refPropName 自动推断反向关系，但显式声明仍是最佳实践。
- **复核状态**: 未复核

### [维度04-04] NopCodeFile 缺少 usages 和 calls 反向 to-many 关系

- **文件**: `nop-code/model/nop-code.orm.xml:204-217`
- **证据片段**:
  ```xml
  <!-- NopCodeFile 只定义了 index 和 symbols 关系 -->
  <relations>
      <to-one name="index" ... refPropName="files" .../>
      <to-many displayName="符号列表" name="symbols" .../>
  </relations>
  <!-- NopCodeUsage/NopCodeCall 引用了 NopCodeFile 的 usages/calls 属性 -->
  ```
- **严重程度**: P2
- **现状**: NopCodeUsage 和 NopCodeCall 通过 refPropName 引用 NopCodeFile 的 usages/calls，但 NopCodeFile 的 relations 中没有声明。
- **风险**: 无法从 File 直接导航到 Usage 和 Call 记录。i18n 中已存在这些条目。
- **建议**: 补充 usages 和 calls 反向 to-many 关系。
- **信心水平**: 很可能
- **误报排除**: 同 04-03，平台可能自动推断，但 i18n 中已存在。
- **复核状态**: 未复核

### [维度04-05] NopCodeCall.callType 和 NopCodeSemanticEdge.relationType 缺少 dict 定义

- **文件**: `nop-code/model/nop-code.orm.xml:493-494,838-839`
- **证据片段**:
  ```xml
  <!-- 无 ext:dict -->
  <column code="CALL_TYPE" displayName="调用类型" name="callType" precision="20" stdSqlType="VARCHAR"/>
  <!-- 对比: NopCodeInheritance.relationType 有 ext:dict -->
  <column code="RELATION_TYPE" ... ext:dict="code/relation_type"/>
  ```
- **严重程度**: P2
- **现状**: callType 和 SemanticEdge.relationType 没有关联字典，而同模型的 Inheritance.relationType 已使用字典。
- **风险**: 前端无法渲染下拉控件，数据完整性无法通过字典校验，同类字段处理不一致。
- **建议**: 为 callType 新增字典（如 `code/call_type`），为 SemanticEdge.relationType 新增字典或复用现有字典。
- **信心水平**: 很可能
- **误报排除**: 值可能是开放字符串，但同类字段已使用字典。
- **复核状态**: 未复核

### [维度04-06] 主键使用 VARCHAR(36) 而非平台标准 VARCHAR(32)

- **文件**: `nop-code/model/nop-code.orm.xml:8`
- **证据片段**:
  ```xml
  <domain name="codeId" precision="36" stdSqlType="VARCHAR"/>
  <!-- 标准使用 precision="32" -->
  ```
- **严重程度**: P3
- **现状**: codeId 域使用 precision=36，用于存储含横线 UUID。平台标准推荐 VARCHAR(32)。
- **风险**: 与平台其他模块主键长度不一致，存储效率略低。不影响功能正确性。
- **建议**: 考虑使用 VARCHAR(32) 并在应用层去掉 UUID 横线。
- **信心水平**: 有趣的猜测
- **误报排除**: 如果业务层确实需要含横线 UUID 则 36 合理，但 tagSet="seq" 通常生成 32 字符 ID。
- **复核状态**: 未复核

### [维度04-07] 英文 i18n 全部为 null，displayName 未本地化

- **文件**: `nop-code/nop-code-meta/src/main/resources/_vfs/i18n/en/_nop-code.i18n.yaml:1-235`
- **证据片段**:
  ```yaml
  entity:
    label:
      NopCodeAnnotationUsage: null
      NopCodeFile: null
  prop:
    label:
      NopCodeIndex:
        id: null
        name: null
  ```
- **严重程度**: P2
- **现状**: 生成的英文 i18n 文件中所有值均为 null，ORM 模型中没有使用 i18n-en:displayName 属性。
- **风险**: 国际化场景下英文用户界面将显示中文标签或 null。不符合平台标准实践。
- **建议**: 在 ORM 模型中添加 i18n-en:displayName 属性并重新生成 i18n。
- **信心水平**: 确定
- **误报排除**: nop-code 是 WIP 模块，但作为规范审计仍需指出。
- **复核状态**: 未复核

### [维度04-08] NopCodeIndex 9 路级联删除，无软删除保护

- **文件**: `nop-code/model/nop-code.orm.xml:118-171`
- **证据片段**:
  ```xml
  <to-many displayName="文件列表" name="files" ... cascadeDelete="true">...</to-many>
  <to-many displayName="符号列表" name="symbols" ... cascadeDelete="true">...</to-many>
  <!-- 共 9 个 cascadeDelete=true 的 to-many 关系 -->
  ```
- **严重程度**: P2
- **现状**: 删除一个 NopCodeIndex 会级联删除其下所有 9 类子实体数据，无逻辑删除保护。
- **风险**: 误删索引记录导致大量关联数据永久丢失，大量级联删除可能造成长事务。
- **建议**: 为 NopCodeIndex 添加逻辑删除，考虑将级联删除改为应用层软删除逻辑。
- **信心水平**: 很可能
- **误报排除**: "重建索引"场景级联删除是合理的，但缺少软删除保护仍是风险。
- **复核状态**: 未复核

### [维度04-09] NopCodeFlowMembership.isEntry 使用 TINYINT 而非 BOOLEAN

- **文件**: `nop-code/model/nop-code.orm.xml:791-792`
- **证据片段**:
  ```xml
  <column code="IS_ENTRY" displayName="是否入口" name="isEntry"
          stdDataType="boolean" stdSqlType="TINYINT"/>
  <!-- 同模型其他 Boolean 字段统一使用 stdSqlType="BOOLEAN" -->
  ```
- **严重程度**: P3
- **现状**: 唯一使用 TINYINT 表示布尔值的字段，其余 10+ 布尔字段全部使用 BOOLEAN。
- **风险**: 不一致的类型映射可能在跨数据库时产生兼容性问题。
- **建议**: 统一为 stdSqlType="BOOLEAN"。
- **信心水平**: 确定
- **误报排除**: 已逐一确认所有其他 Boolean 字段均使用 BOOLEAN。
- **复核状态**: 未复核

### [维度04-10] NopCodeSemanticEdge 的关系缺少 refDisplayName

- **文件**: `nop-code/model/nop-code.orm.xml:872-887`
- **证据片段**:
  ```xml
  <to-one name="index" refEntityName="...NopCodeIndex" refPropName="semanticEdges" tagSet="pub">
  <to-one name="sourceSymbol" refEntityName="...NopCodeSymbol" tagSet="pub">
  <!-- 其他实体都有 refDisplayName -->
  ```
- **严重程度**: P3
- **现状**: NopCodeSemanticEdge 的 3 个 to-one 关系都没有 refDisplayName，是模型中唯一缺少此属性的实体。
- **风险**: 页面渲染可能显示属性名而非友好名称。
- **建议**: 为 3 个关系添加 refDisplayName。
- **信心水平**: 确定
- **误报排除**: 已确认其他 10 个实体的关系声明都有 refDisplayName。
- **复核状态**: 未复核

### [维度04-11] NopCodeDependency 用路径字符串而非外键引用文件

- **文件**: `nop-code/model/nop-code.orm.xml:663-699`
- **证据片段**:
  ```xml
  <column code="SOURCE_FILE_PATH" displayName="源文件路径" domain="filePath" name="sourceFilePath"/>
  <column code="TARGET_FILE_PATH" displayName="目标文件路径" domain="filePath" name="targetFilePath"/>
  <!-- 无 fileId 外键和 to-one 关系 -->
  ```
- **严重程度**: P2
- **现状**: NopCodeDependency 使用文件路径字符串而非外键表示文件依赖，无 ORM 关系连接到 NopCodeFile。
- **风险**: 无法通过 ORM 导航直接访问源/目标文件实体，路径字符串无法保证引用完整性。与同模块其他实体建模风格不一致。
- **建议**: 如果仅针对已索引文件，改为外键引用。如果设计意图是记录可能不存在的文件依赖，路径字符串是合理的但应补充说明。
- **信心水平**: 有趣的猜测
- **误报排除**: 文件依赖可能需要在文件索引前就记录，此时路径字符串是合理设计。
- **复核状态**: 未复核

### [维度04-12] NopCodeSymbol 单表 32 字段，子类型字段与 extData 功能重叠

- **文件**: `nop-code/model/nop-code.orm.xml:231-301`
- **证据片段**:
  ```xml
  <!-- 类特有字段 -->
  <column code="SUPER_CLASS_NAME" ... name="superClassName"/>
  <!-- 方法特有字段 -->
  <column code="SIGNATURE" ... name="signature"/>
  <!-- 字段特有字段 -->
  <column code="FIELD_TYPE" ... name="fieldType"/>
  ```
- **严重程度**: P3
- **现状**: 单表继承模式存储所有符号类型，32 字段中约 15 个是类型专属字段（对应不匹配类型时为 NULL），且 extData JSON 字段已用于存储额外信息。
- **风险**: 表宽度较大，NULL 占比高。部分低频布尔字段与 extData 功能重叠。
- **建议**: 明确划分哪些属性放入固定列、哪些放入 extData，避免重叠。
- **信心水平**: 有趣的猜测
- **误报排除**: 单表继承是多态实体的经典模式，32 字段仍在可控范围。
- **复核状态**: 未复核

### [维度04-13] 索引命名不符合 SKILL.md ix_{表名}_{列名} 规范

- **文件**: `nop-code/model/nop-code.orm.xml`（全文件 indexes 段）
- **证据片段**:
  ```xml
  <index name="idx_symbol_qualified_name">
  <!-- SKILL.md 规范: ix_nop_code_symbol_qualified_name -->
  ```
- **严重程度**: P3
- **现状**: 索引命名使用 `idx_{实体简称}_{列简称}` 而非平台规范 `ix_{表名}_{列名}`。
- **风险**: 多模块共享数据库时可能冲突，不符合命名规范。
- **建议**: 使用 `ix_nop_code_symbol_qualified_name` 格式。
- **信心水平**: 很可能
- **误报排除**: nop-code 使用独立数据库，冲突风险低。模块内命名自洽。
- **复核状态**: 未复核
