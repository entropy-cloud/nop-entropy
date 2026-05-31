# 审核维度 04：ORM 模型与实体设计

## 第 1 轮（初审）

### [维度04-01] 8/11 实体缺失标准审计字段，全部无 entity 级审计属性

- **文件**: `nop-code/model/nop-code.orm.xml`
- **行号**: 全局 — 第 93 行(NopCodeIndex)、第 176 行(NopCodeFile)、第 228 行(NopCodeSymbol)、第 404 行(NopCodeUsage)、第 472 行(NopCodeCall)、第 542 行(NopCodeInheritance)、第 594 行(NopCodeAnnotationUsage)、第 654 行(NopCodeDependency) 均无审计字段
- **证据片段**:
  ```xml
  <!-- nop-code: 无审计属性 -->
  <entity className="io.nop.code.dao.entity.NopCodeIndex" displayName="代码索引"
          name="io.nop.code.dao.entity.NopCodeIndex" registerShortName="true"
          tableName="nop_code_index">
  ```
  对比 nop-auth 标准写法：
  ```xml
  <!-- nop-auth: 标准审计属性 -->
  <entity className="io.nop.auth.dao.entity.NopAuthUser" createTimeProp="createTime"
          createrProp="createdBy" deleteFlagProp="delFlag" ...>
  ```
- **严重程度**: P2
- **现状**: 8/11 实体完全无审计字段（createdBy/createTime/delFlag）。NopCodeFlow 有手动审计字段但未设置 entity 级审计属性。NopCodeSemanticEdge 有部分审计字段但同样无 entity 级属性。
- **风险**: (1) 无法追溯数据变更历史。(2) 框架自动填充机制未启用。(3) NopCodeIndex 的 cascadeDelete=true 硬删除所有关联数据无法恢复。
- **建议**: 为所有实体添加 createTimeProp/createrProp/deleteFlagProp 属性和对应列。
- **信心水平**: 高
- **误报排除**: NopCodeSemanticEdge 已有 delFlag 表明至少部分实体有审计需求。
- **复核状态**: 未复核

### [维度04-02] 英文 i18n 文件全部为 null

- **文件**: `nop-code/nop-code-meta/src/main/resources/_vfs/i18n/en/_nop-code.i18n.yaml`
- **行号**: 第 1-235 行全部
- **证据片段**:
  ```yaml
  entity:
    label:
      NopCodeAnnotationUsage: null
      NopCodeCall: null
      ...
  prop:
    label:
      NopCodeSymbol:
        id: null
        indexId: null
        ...
  ```
- **严重程度**: P3
- **现状**: 英文 locale 下所有 label 为 null，将回退到 ORM 源文件中的中文 displayName。
- **风险**: 国际化场景下英文用户看到中文标签。
- **建议**: 在 en/nop-code.i18n.yaml 中补充英文翻译。
- **信心水平**: 高
- **误报排除**: 如果模块仅面向中文用户可暂缓。
- **复核状态**: 未复核

### [维度04-03] NopCodeSymbol 宽表设计——32 列中约 15 列为类型特有列

- **文件**: `nop-code/model/nop-code.orm.xml`
- **行号**: 第 228-397 行
- **证据片段**:
  ```xml
  <!-- 类特有 -->
  <column code="SUPER_CLASS_NAME" ... name="superClassName" .../>
  <column code="IS_ABSTRACT" ... name="isAbstract" .../>
  <!-- 方法特有 -->
  <column code="SIGNATURE" ... name="signature" .../>
  <column code="RETURN_TYPE" ... name="returnType" .../>
  <!-- 字段特有 -->
  <column code="FIELD_TYPE" ... name="fieldType" .../>
  <column code="IS_VOLATILE" ... name="isVolatile" .../>
  <!-- 同时有 JSON 扩展 -->
  <column code="EXT_DATA" ... domain="jsonContent" name="extData" .../>
  ```
- **严重程度**: P3
- **现状**: 统一表存储所有符号类型，约 15 个类型特有列在多数行中为 NULL。同时存在 EXT_DATA JSON 字段。
- **风险**: (1) 存储浪费（百万级方法行的类/字段特有列为 NULL）。(2) 策略不一致——部分信息在显式列部分在 JSON。(3) 索引效率降低。
- **建议**: 明确策略边界——需要索引/过滤的字段保留显式列，其余 boolean 标记移入 JSON。
- **信心水平**: 中-高
- **误报排除**: 统一表简化查询（不需要 UNION），在代码索引场景下是合理权衡。
- **复核状态**: 未复核

### [维度04-04] NopCodeFile.SOURCE_CODE (CLOB) 存储完整源代码——潜在性能风险

- **文件**: `nop-code/model/nop-code.orm.xml`
- **行号**: 第 194-195 行
- **证据片段**:
  ```xml
  <column code="SOURCE_CODE" displayName="源代码" name="sourceCode"
          propId="8" stdDataType="string" stdSqlType="CLOB" ui:control="textarea"/>
  ```
- **严重程度**: P2
- **现状**: SOURCE_CODE 使用 CLOB 存储，单行可达数 MB。NopCodeFile 没有标记延迟加载。
- **风险**: 列表查询默认 SELECT *，会加载所有 CLOB 内容。1000 个文件可能加载 100MB+ 数据到内存。
- **建议**: (1) 垂直拆分到独立 NopCodeFileContent 表。(2) 或标记延迟加载。(3) 确保 BizModel 列表查询使用 selectFields 排除 SOURCE_CODE。
- **信心水平**: 中
- **误报排除**: 如果 Nop ORM 的 CLOB 自动延迟加载则不成立。
- **复核状态**: 未复核

### [维度04-05] dict valueType="int" 与列 stdSqlType="VARCHAR" 类型不一致

- **文件**: `nop-code/model/nop-code.orm.xml`
- **行号**: 字典定义第 23/43/52/63/73/80 行，列引用第 239/245/417/110/555/727 行
- **证据片段**:
  ```xml
  <!-- 字典 valueType="int"，option value 为整数 -->
  <dict name="code/symbol_kind" valueType="int">
      <option code="CLASS" label="类" value="10"/>
  </dict>
  <!-- 列为 VARCHAR -->
  <column code="KIND" ... stdSqlType="VARCHAR" precision="20" ext:dict="code/symbol_kind"/>
  ```
- **严重程度**: P3
- **现状**: 6 个字典均声明 valueType="int"，但引用列均为 VARCHAR(20)。与 nop-auth 使用 INTEGER 列的模式不一致。
- **风险**: 类型语义冲突。如果存储 code 字符串则 valueType 误导；如果存储数字则 VARCHAR 浪费空间。
- **建议**: 确认实际存储策略后统一类型声明。
- **信心水平**: 中
- **误报排除**: Nop ORM 可能有自己的 dict 存储策略，需确认。
- **复核状态**: 未复核

### [维度04-06] 源模型缺少反向关系声明（生成器已补充）

- **文件**: `nop-code/model/nop-code.orm.xml`
- **行号**: 第 204-217 行(NopCodeFile)、第 311-372 行(NopCodeSymbol)
- **证据片段**:
  ```xml
  <!-- NopCodeFile 源模型只有 index 和 symbols，缺少 usages/calls -->
  <relations>
      <to-one name="index" .../>
      <to-many name="symbols" .../>
  </relations>
  <!-- _app.orm.xml 已自动补充 -->
  ```
- **严重程度**: P3
- **现状**: 源模型中多个实体仅声明正向关系，反向关系依赖代码生成器自动推导。
- **风险**: 源模型不完整，开发者直接阅读源模型无法看到完整关系图。
- **建议**: 在源模型中显式补充反向关系声明。
- **信心水平**: 高
- **误报排除**: 生成器已正确补充，运行时功能正常。
- **复核状态**: 未复核

### [维度04-07] NopCodeDependency.resolved 字段类型不匹配布尔语义

- **文件**: `nop-code/model/nop-code.orm.xml`
- **行号**: 第 668-669 行
- **证据片段**:
  ```xml
  <column code="RESOLVED" displayName="是否已解析" name="resolved"
          propId="6" stdDataType="int" stdSqlType="SMALLINT"/>
  ```
- **严重程度**: P3
- **现状**: RESOLVED 语义为布尔值但使用 SMALLINT 而非 BOOLEAN/TINYINT + boolFlag 域。
- **风险**: 与同模块其他布尔字段不一致。前端可能渲染为数字输入框。
- **建议**: 改为 `stdDataType="boolean" stdSqlType="TINYINT" stdDomain="boolFlag"`。
- **信心水平**: 高
- **误报排除**: 如果 RESOLVED 可能有多种状态值则 SMALLINT 合理。但 displayName "是否已解析"暗示二值。
- **复核状态**: 未复核

### [维度04-08] NopCodeCall 唯一键包含可空列

- **文件**: `nop-code/model/nop-code.orm.xml`
- **行号**: 第 488-489 行(COLUMN 列), 第 537 行(唯一键)
- **证据片段**:
  ```xml
  <column code="COLUMN" displayName="列号" domain="columnNumber" name="column"
          propId="7" stdDataType="int" stdSqlType="INTEGER"/>
  <unique-key columns="indexId,callerId,calleeId,line,column" name="uk_call_unique"/>
  ```
- **严重程度**: P3
- **现状**: COLUMN 列无 mandatory，可能为 NULL。MySQL/PostgreSQL 对 NULL 在唯一约束中允许重复。
- **风险**: 多条相同 (indexId, callerId, calleeId, line, NULL) 记录违背唯一性意图。
- **建议**: 方案 A: 将 COLUMN 设为 mandatory + defaultValue="0"。方案 B: 从唯一键移除 COLUMN。
- **信心水平**: 中
- **误报排除**: 如果 column 总有值则实际不遇到 NULL 问题。
- **复核状态**: 未复核

### [维度04-09] 审计字段命名不一致（createTime vs createdTime）

- **文件**: `nop-code/model/nop-code.orm.xml`
- **行号**: 第 728 行(NopCodeFlow createdTime)、第 843 行(NopCodeSemanticEdge createTime)
- **证据片段**:
  ```xml
  <!-- NopCodeFlow: createdTime -->
  <column code="CREATED_TIME" ... name="createdTime" .../>
  <!-- NopCodeSemanticEdge: createTime -->
  <column code="CREATE_TIME" ... name="createTime" .../>
  ```
- **严重程度**: P3
- **现状**: 模块内部命名不一致，与 Nop 平台标准约定 createTime 不一致。
- **风险**: 开发者可能用错属性名。启用 createTimeProp 框架属性时需改名。
- **建议**: 统一为 createTime。
- **信心水平**: 高
- **误报排除**: 纯命名风格问题，不影响运行时功能。
- **复核状态**: 未复核

### [维度04-10] NopCodeDependency 无外键关联 NopCodeFile

- **文件**: `nop-code/model/nop-code.orm.xml`
- **行号**: 第 654-690 行
- **证据片段**:
  ```xml
  <column code="SOURCE_FILE_PATH" ... name="sourceFilePath" .../>
  <column code="TARGET_FILE_PATH" ... name="targetFilePath" .../>
  <!-- 没有 sourceFileId/targetFileId 外键列 -->
  ```
- **严重程度**: P3
- **现状**: 存储文件路径而非文件 ID 外键，无法通过 JOIN 获取依赖文件详情。
- **风险**: 数据冗余，查询效率低。
- **建议**: 考虑添加 sourceFileId/targetFileId 外键列。
- **信心水平**: 中
- **误报排除**: 如果设计意图是记录跨索引依赖（目标文件可能不在当前索引中），则不用外键合理。
- **复核状态**: 未复核

### [维度04-11] NopCodeIndex 的 9 个 cascadeDelete 可能导致删除操作超时

- **文件**: `nop-code/model/nop-code.orm.xml`
- **行号**: 第 118-171 行
- **证据片段**:
  ```xml
  <to-many ... name="files" ... cascadeDelete="true">
  <to-many ... name="symbols" ... cascadeDelete="true">
  <!-- 共 9 个 cascadeDelete=true -->
  ```
- **严重程度**: P2
- **现状**: 9 个 cascadeDelete=true 关系。大型项目索引含数十万条 usage/call 记录。
- **风险**: (1) 大批量 DELETE 可能导致数据库锁超时。(2) 无软删除机制，删除不可逆。(3) NopCodeFlow cascadeDelete 进一步级联到 FlowMembership。
- **建议**: (1) BizModel 层实现批量删除。(2) 添加软删除支持。(3) 大关系分批删除。
- **信心水平**: 中
- **误报排除**: 如果仅用于开发环境小型项目则影响可忽略。
- **复核状态**: 未复核

### [维度04-12] NopCodeAnnotationUsage.annotatedSymbolId 非必填

- **文件**: `nop-code/model/nop-code.orm.xml`
- **行号**: 第 604-605 行
- **证据片段**:
  ```xml
  <column code="ANNOTATED_SYMBOL_ID" ... name="annotatedSymbolId"
          propId="4" stdDataType="string" stdSqlType="VARCHAR"/>
  <!-- 没有 mandatory="true" -->
  ```
- **严重程度**: P3
- **现状**: INDEX_ID 和 ANNOTATION_TYPE_ID 是 mandatory，但 ANNOTATED_SYMBOL_ID 不是。
- **风险**: 无法保证数据完整性。
- **建议**: 如果注解使用总是关联符号，添加 mandatory="true"。如果存在 package-info.java 场景则保留，但添加说明。
- **信心水平**: 中
- **误报排除**: Java package-info.java 中的注解不关联具体符号。
- **复核状态**: 未复核
