# 维度 04：ORM 模型与实体设计

## 第 1 轮（初审）

### [维度04-01] NopCodeSemanticEdge.relationType 字典与实际枚举不匹配

- **文件**: `nop-code/model/nop-code.orm.xml`
- **行号**: 87-91（字典定义），915-916（字段引用）
- **证据片段**:
  ```xml
  <dict label="继承关系类型" name="code/relation_type" valueType="string">
      <option code="EXTENDS" label="继承" value="10"/>
      <option code="IMPLEMENTS" label="实现" value="20"/>
  </dict>
  <!-- NopCodeSemanticEdge 引用同一字典 -->
  <column code="RELATION_TYPE" ... ext:dict="code/relation_type"/>
  ```
- **严重程度**: P2
- **现状**: 字典仅有 EXTENDS/IMPLEMENTS，但实际持久化 SemanticRelationType 枚举的 8 个值（SEMANTICALLY_SIMILAR_TO 等）。
- **风险**: 前端下拉只有2选项；若框架校验字典值，插入会失败。
- **建议**: 为 NopCodeSemanticEdge 新建专用字典 code/semantic_relation_type。
- **信心水平**: 确定
- **误报排除**: 已确认 SemanticRelationType 枚举有8个值且确实用于持久化。
- **复核状态**: 未复核

### [维度04-02] NopCodeDependency 唯一键索引长度可能超出 MySQL 限制

- **文件**: `nop-code/model/nop-code.orm.xml`
- **行号**: 768
- **证据片段**:
  ```xml
  <unique-key columns="indexId,sourceFilePath,targetFilePath,importStatement" name="uk_dependency_unique"/>
  <!-- 三列各 VARCHAR(500) + indexId VARCHAR(36) = 1536×4(utf8mb4) = 6144 字节 > 3072 限制 -->
  ```
- **严重程度**: P2
- **现状**: 唯一键总长度 6144 字节，超过 MySQL InnoDB 3072 字节限制。
- **风险**: DDL 建表失败或唯一性约束在 500 字符后失效。
- **建议**: 引入 hash 字段或缩减 precision。
- **信心水平**: 确定
- **误报排除**: 数学计算明确 1536×4=6144>3072。
- **复核状态**: 未复核

### [维度04-03] annotationTypeId 列长度不足以存储全限定名

- **文件**: `nop-code/model/nop-code.orm.xml`
- **行号**: 674-675
- **证据片段**:
  ```xml
  <column code="ANNOTATION_TYPE_ID" displayName="注解类型ID" domain="codeId" .../>
  <!-- codeId = VARCHAR(36)，但实际存入注解全限定名如 "org.springframework...RequestMapping" = 51字符 -->
  ```
- **严重程度**: P2
- **现状**: 列 VARCHAR(36) 但初始写入注解全限定名可超36字符。
- **风险**: 严格模式下 INSERT 失败；非严格模式截断导致后续解析失败。
- **建议**: 改 domain 为 qualifiedName(500) 或在持久化前先执行符号解析。
- **信心水平**: 确定
- **误报排除**: 已确认代码先保存全限定名再异步解析为UUID，存在截断窗口。
- **复核状态**: 未复核

### [维度04-04] 审计字段配置不一致

- **严重程度**: P3
- **现状**: 11实体中仅2个有完整4审计字段；NopCodeSemanticEdge有逻辑删除但无updateTime。
- **复核状态**: 未复核

### [维度04-05] NopCodeSemanticEdge 缺少 i18n-en:displayName

- **严重程度**: P3
- **现状**: 11实体中唯一缺失英文显示名，导致生成产物中英文标签为 null。
- **复核状态**: 未复核

### [维度04-06] NopCodeUsage 唯一键含可空列

- **严重程度**: P3
- **现状**: column 列可为 NULL，NULL!=NULL 语义下去重保障失效。
- **复核状态**: 未复核
