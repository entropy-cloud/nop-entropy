# 维度 04：ORM 模型与实体设计

## 第 1 轮（初审）

### [维度04-01] code/reference_kind 字典缺少 TESTED_BY 枚举值

- **文件**: `nop-code/model/nop-code.orm.xml:53-66` 与 `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:1306`
- **证据片段**:
```xml
<dict label="引用类型" name="code/reference_kind" valueType="string">
    <option code="READ" label="读取" value="10"/>
    <option code="WRITE" label="写入" value="20"/>
    <option code="CALL" label="调用" value="30"/>
    <option code="TYPE_REFERENCE" label="类型引用" value="40"/>
    <option code="EXTENDS" label="继承" value="50"/>
    <option code="IMPLEMENTS" label="实现" value="60"/>
    <option code="ANNOTATES" label="注解" value="70"/>
    <option code="IMPORTS" label="导入" value="80"/>
    <option code="OVERRIDES" label="重写" value="90"/>
    <option code="TYPE_OF" label="类型引用" value="100"/>
    <option code="INSTANTIATES" label="实例化" value="110"/>
</dict>
```
```java
// CodeIndexService.java:1306
String testUsageKind = "TESTED_BY";
```
- **严重程度**: P2
- **现状**: NopCodeUsage 的 KIND 列声明了 `ext:dict="code/reference_kind"`，但运行时代码在测试文件分析场景中向该列写入 `"TESTED_BY"` 值，该值不在字典枚举中。
- **风险**: 基于 XMeta 自动生成的 GraphQL API 对 kind 字段做字典校验时，TESTED_BY 值会被拒绝或无法在下拉列表中出现；后台页面筛选/显示测试引用时会丢失此类型的数据。
- **建议**: 在 `code/reference_kind` 字典中补充 `<option code="TESTED_BY" label="测试引用" value="120"/>`。
- **信心水平**: 确定
- **误报排除**: 字典 `ext:dict` 绑定是 Nop 平台的标准数据校验机制，实际使用的值必须出现在字典中。已通过 grep 确认 TESTED_BY 在 CodeIndexService.java:1306 处实际使用。
- **复核状态**: 未复核

### [维度04-02] PROVENANCE 列在 5 个实体中使用枚举值但无字典绑定

- **文件**: `nop-code/model/nop-code.orm.xml:493, 568, 634, 695, 946` 与 `nop-code/nop-code-core/src/main/java/io/nop/code/core/model/EdgeProvenance.java:3-8`
- **证据片段**:
```xml
<column code="PROVENANCE" displayName="来源" name="provenance"
        precision="20" propId="10" stdDataType="string" stdSqlType="VARCHAR"/>
```
```java
public enum EdgeProvenance {
    AST_EXTRACTION,
    SYMBOL_SOLVER,
    HEURISTIC,
    FRAMEWORK_INFERENCE,
    MANUAL
}
```
- **严重程度**: P2
- **现状**: `provenance` 列出现在 NopCodeUsage、NopCodeCall、NopCodeInheritance、NopCodeAnnotationUsage、NopCodeSemanticEdge 五个实体中，Java 侧有明确的 `EdgeProvenance` 枚举（5 个值），但 ORM 模型的列定义中均无 `ext:dict` 绑定。
- **风险**: API 元数据无法对 provenance 提供下拉选项或输入校验；后台页面无法正确展示来源标签。
- **建议**: 新增 `code/edge_provenance` 字典，映射 EdgeProvenance 枚举的 5 个值，并在上述 5 个 provenance 列上添加 `ext:dict="code/edge_provenance"`。
- **信心水平**: 很可能
- **误报排除**: provenance 是有明确枚举约束的受控词汇，缺少字典绑定会导致 XMeta/API 层丧失校验能力，属于结构性缺失。
- **复核状态**: 未复核

### [维度04-03] NopCodeDependency 存在冗余组合索引

- **文件**: `nop-code/model/nop-code.orm.xml:770-783`
- **证据片段**:
```xml
<indexes>
    <index name="ix_nop_code_dependency_index_id_source_file_path">
        <column name="indexId"/>
        <column name="sourceFilePath"/>
    </index>
    <index name="ix_nop_code_dependency_index_id_target_file_path">
        <column name="indexId"/>
        <column name="targetFilePath"/>
    </index>
    <index name="ix_nop_code_dependency_index_id_source_target">
        <column name="indexId"/>
        <column name="sourceFilePath"/>
        <column name="targetFilePath"/>
    </index>
</indexes>
```
- **严重程度**: P2
- **现状**: `ix_nop_code_dependency_index_id_source_file_path` 的索引键为 `(indexId, sourceFilePath)`，而 `ix_nop_code_dependency_index_id_source_target` 的索引键为 `(indexId, sourceFilePath, targetFilePath)`。前者是后者的严格前缀，任何使用 `(indexId, sourceFilePath)` 的查询都可以由后者覆盖。
- **风险**: 冗余索引增加写入开销和存储空间。
- **建议**: 删除 `ix_nop_code_dependency_index_id_source_file_path`。
- **信心水平**: 确定
- **误报排除**: 最左前缀覆盖是 B-tree 索引的确定性行为。
- **复核状态**: 未复核

### [维度04-04] 含审计列的实体缺少实体级审计属性声明

- **文件**: `nop-code/model/nop-code.orm.xml:111-209`（NopCodeIndex）、`794-865`（NopCodeFlow）、`868-917`（NopCodeFlowMembership）
- **证据片段**:
```xml
<!-- NopCodeIndex 实体声明，无 createTimeProp/createrProp -->
<entity className="io.nop.code.dao.entity.NopCodeIndex" displayName="代码索引"
        name="io.nop.code.dao.entity.NopCodeIndex" registerShortName="true"
        tableName="nop_code_index" ext:icon="search">
```
```xml
<!-- NopCodeIndex 的审计列 -->
<column code="CREATED_TIME" displayName="创建时间" name="createTime"
        propId="10" stdDataType="timestamp" stdSqlType="DATETIME"/>
<column code="UPDATE_TIME" displayName="更新时间" name="updateTime"
        propId="11" stdDataType="timestamp" stdSqlType="DATETIME"/>
```
- **严重程度**: P2
- **现状**: NopCodeIndex、NopCodeFlow、NopCodeFlowMembership 包含审计列但实体元素上未声明 `createTimeProp`/`updateTimeProp` 等属性。框架无法自动填充审计字段。
- **风险**: 框架无法自动填充 createTime/updateTime/createdBy/updatedBy，通过标准 CrudBizModel 进行 CRUD 操作时审计字段将始终为 null。
- **建议**: 在这三个实体的 `<entity>` 元素上添加审计属性声明。
- **信心水平**: 很可能
- **误报排除**: `createTimeProp`/`createrProp` 是 Nop ORM 框架自动审计填充的核心机制，nop-auth 等标准模块全部使用此模式。
- **复核状态**: 未复核

### [维度04-05] 核心实体 NopCodeFile/NopCodeSymbol 完全缺失审计列

- **文件**: `nop-code/model/nop-code.orm.xml:211-269`（NopCodeFile）、`276-464`（NopCodeSymbol）
- **证据片段**:
```xml
<!-- NopCodeFile 实体的全部列，无 createTime/updateTime -->
<columns>
    <column code="ID" .../>
    <column code="INDEX_ID" .../>
    <column code="FILE_PATH" .../>
    <column code="PACKAGE_NAME" .../>
    <column code="LANGUAGE" .../>
    <column code="LINE_COUNT" .../>
    <column code="IMPORTS" .../>
    <column code="SOURCE_CODE" .../>
    <column code="FILE_HASH" .../>
    <column code="LAST_MODIFIED" .../>
    <column code="FILE_SIZE" .../>
</columns>
```
- **严重程度**: P3
- **现状**: NopCodeFile 和 NopCodeSymbol 核心实体不含 createTime/updateTime/createdBy/updatedBy 审计列。
- **风险**: 无法追踪数据创建/修改时间，影响增量索引调试和问题排查。但风险被本模块数据以批量索引方式写入的事实缓解。
- **建议**: 至少为 NopCodeFile 和 NopCodeSymbol 补充 createTime/updateTime 列。
- **信心水平**: 很可能
- **误报排除**: SKILL.md 明确将审计字段标记为"必须"，但代码索引场景下 P3 级别已充分反映实际风险。
- **复核状态**: 未复核

### [维度04-06] NopCodeSemanticEdge 的 propId 编号非顺序

- **文件**: `nop-code/model/nop-code.orm.xml:919-958`
- **证据片段**:
```xml
<column code="PROVENANCE" ... propId="15" .../>
<column code="CREATED_BY" ... propId="12" .../>
<column code="CREATED_TIME" ... propId="13" .../>
<column code="UPDATED_BY" ... propId="16" .../>
<column code="UPDATE_TIME" ... propId="17" .../>
<column code="DEL_FLAG" ... propId="14" .../>
```
- **严重程度**: P3
- **现状**: NopCodeSemanticEdge 的列 propId 编号顺序为 1-11, 15, 12-13, 16-17, 14，多次迭代追加但未重新编号。
- **风险**: 非顺序编号增加未来维护时引入 propId 冲突的概率。
- **建议**: 下次模型变更时将 propId 重排为严格递增。
- **信心水平**: 确定
- **误报排除**: propId 是 ORM 实体的持久化标识符，冲突会导致运行时错误。
- **复核状态**: 未复核

### [维度04-07] NopCodeSymbol 无 (indexId, language) 组合索引

- **文件**: `nop-code/model/nop-code.orm.xml:437-463`
- **证据片段**:
```xml
<indexes>
    <index name="ix_nop_code_symbol_index_id_qualified_name">
        <column name="indexId"/><column name="qualifiedName"/>
    </index>
    <index name="ix_nop_code_symbol_index_id_name">
        <column name="indexId"/><column name="name"/>
    </index>
    <index name="ix_nop_code_symbol_index_id_kind">
        <column name="indexId"/><column name="kind"/>
    </index>
    <!-- 无 (indexId, language) 组合索引 -->
</indexes>
```
- **严重程度**: P3
- **现状**: NopCodeSymbol 将 `language` 作为反规范化列存储，但无 `(indexId, language)` 组合索引。如果需要按语言过滤符号查询，将无法利用索引。
- **风险**: 大索引下按语言过滤符号查询性能不佳。降级为 P3 因为当前主查询路径不经过 symbol.language。
- **建议**: 评估是否有按语言过滤符号的查询场景，如有则添加索引。
- **信心水平**: 有趣的猜测
- **误报排除**: 当前主查询路径不经过 symbol.language，风险较低。
- **复核状态**: 未复核
