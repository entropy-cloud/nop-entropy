# 维度 04：ORM 模型与实体设计

**审计日期**: 2026-05-29
**审计范围**: `nop-code/model/nop-code.orm.xml`（877行，11个实体）

---

## 第 1 轮（初审）

### [维度04-01] dict valueType=int 与 Java 代码实际存储 string 不一致，导致 UI 字典渲染失效

- **文件**: `nop-code/model/nop-code.orm.xml:64-82`, `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:1931/1941/2017/2029/2586`
- **证据片段**:
  ```xml
  <!-- nop-code.orm.xml:64-82 -->
  <dict label="索引状态" name="code/index_status" valueType="int">
      <option code="CREATED" label="已创建" value="10"/>
      <option code="INDEXING" label="索引中" value="20"/>
      <option code="READY" label="就绪" value="30"/>
      <option code="ERROR" label="错误" value="40"/>
  </dict>
  <dict label="编程语言" name="code/language" valueType="int">
      <option code="JAVA" label="Java" value="10"/>
      <option code="PYTHON" label="Python" value="20"/>
      <option code="TYPESCRIPT" label="TypeScript" value="30"/>
  </dict>
  ```
  ```java
  // CodeIndexService.java:1931,1941,2029
  indexEntity.setStatus("COMPLETED");  // dict 中没有 COMPLETED
  // CodeIndexService.java:1938,2016
  indexEntity.setLanguage("Java");      // dict code=JAVA value=10, 存的是 "Java"
  ```
- **严重程度**: P1
- **现状**: 6 个 dict 声明 valueType="int" 并使用整数 value，但对应列是 VARCHAR，Java 代码实际存储的是字符串值。三方不一致。生成的 _NopCodeDaoConstants.java 中 INDEX_STATUS_CREATED = 10 等常量完全未被使用。
- **风险**: GraphQL/API 读取时 ext:dict 驱动的下拉框和状态显示无法正确渲染。CodeLanguage.valueOf() 在 entity.getLanguage() 返回 "Java" 时抛 IllegalArgumentException。
- **建议**: 将 dict valueType 改为 "string"，将 value 改为枚举名（如 "JAVA"），同时统一 Java 代码中的拼写。
- **信心水平**: 95%
- **误报排除**: 已通过读取 _NopCodeDaoConstants.java 和 CodeIndexService.java 交叉验证确认不一致。排除了"Nop 框架内部自动转换 int↔string"的可能性。
- **复核状态**: 未复核

### [维度04-02] NopCodeIndex.STATUS 存储 "COMPLETED" 在 dict 中不存在

- **文件**: `nop-code/model/nop-code.orm.xml:64-70`, `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:1931/1941/2029`
- **证据片段**:
  ```xml
  <!-- nop-code.orm.xml:64-70 -->
  <dict label="索引状态" name="code/index_status" valueType="int">
      <option code="CREATED" label="已创建" value="10"/>
      <option code="INDEXING" label="索引中" value="20"/>
      <option code="READY" label="就绪" value="30"/>
      <option code="ERROR" label="错误" value="40"/>
  </dict>
  ```
  ```java
  // CodeIndexService.java:1931,1941,2029
  indexEntity.setStatus("COMPLETED");  // "COMPLETED" 不在 dict 中
  ```
- **严重程度**: P1
- **现状**: 索引构建完成后 STATUS 被设为 "COMPLETED"，但 dict 中没有此选项。语义最接近的是 "READY"。
- **风险**: UI/GraphQL 查询时，"COMPLETED" 状态无法显示中文标签。如果 dict 被用于前端筛选，该状态记录将被遗漏。
- **建议**: 将 Java 代码中的 "COMPLETED" 统一改为 dict 中已有的 "READY"。
- **信心水平**: 98%
- **误报排除**: 已确认 COMPLETED 字符串在 dict 的 code 和 value 属性中均不存在。
- **复核状态**: 未复核

### [维度04-03] NopCodeFlow.STATUS 复用 dict code/index_status 但存储 "DETECTED" 不在选项中

- **文件**: `nop-code/model/nop-code.orm.xml:721-722`, `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:2586`
- **证据片段**:
  ```xml
  <!-- nop-code.orm.xml:721-722 -->
  <column code="STATUS" name="status" ext:dict="code/index_status"/>
  ```
  ```java
  // CodeIndexService.java:2586
  flowEntity.setStatus("DETECTED");  // "DETECTED" 不在 code/index_status dict 中
  ```
- **严重程度**: P2
- **现状**: NopCodeFlow.STATUS 引用了 code/index_status dict（设计用于索引状态），但 Flow 的生命周期状态不同，属于语义误用。
- **风险**: "DETECTED" 无法在 dict 驱动的 UI 中正确显示，两个实体共享同一 dict 但语义不同。
- **建议**: 为 NopCodeFlow.STATUS 创建独立 dict（如 code/flow_status），或在 code/index_status 中补充 DETECTED 选项。
- **信心水平**: 95%
- **误报排除**: 已确认 NopCodeFlow 和 NopCodeIndex 共用同一 ext:dict。
- **复核状态**: 未复核

### [维度04-04] NopCodeIndex.LANGUAGE 存储 "Java"（混合大小写）与 NopCodeFile.LANGUAGE 存储 "JAVA" 不一致

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:1938/2016/2049`
- **证据片段**:
  ```java
  // CodeIndexService.java:1938,2016 — Index 实体
  indexEntity.setLanguage("Java");    // 混合大小写
  // CodeIndexService.java:2049 — File 实体
  fileEntity.setLanguage(file.getLanguage().name()); // CodeLanguage.JAVA.name() = "JAVA" 全大写
  ```
- **严重程度**: P1
- **现状**: 同一业务概念使用两种大小写。NopCodeIndex 存 "Java"，NopCodeFile 存 "JAVA"。
- **风险**: 按 language 查询时不一致导致匹配失败；CodeLanguage.valueOf("Java") 抛 IllegalArgumentException。
- **建议**: 统一使用 CodeLanguage.JAVA.name()（即 "JAVA" 全大写）存储。
- **信心水平**: 98%
- **误报排除**: 已通过读取 CodeLanguage 枚举确认 name() 返回全大写。已确认 valueOf("Java") 会抛异常。
- **复核状态**: 未复核

### [维度04-05] dict code/relation_type 缺少 MIXIN 选项，与 Java 枚举 CodeRelationType 不一致

- **文件**: `nop-code/model/nop-code.orm.xml:78-82`, `nop-code/nop-code-core/src/main/java/io/nop/code/core/model/CodeRelationType.java:6-10`
- **证据片段**:
  ```xml
  <dict label="继承关系类型" name="code/relation_type" valueType="int">
      <option code="EXTENDS" label="继承" value="10"/>
      <option code="IMPLEMENTS" label="实现" value="20"/>
  </dict>
  ```
  ```java
  public enum CodeRelationType {
      EXTENDS,
      IMPLEMENTS,
      MIXIN       // dict 中缺失
  }
  ```
- **严重程度**: P2
- **现状**: Java 枚举包含 MIXIN，但 ORM dict 未定义此选项。
- **风险**: TypeScript/Python 等语言的 mixin 关系在索引时可能产生此值，dict 驱动的 UI 无法正确显示。
- **建议**: 在 dict 中补充 `<option code="MIXIN" label="Mixin" value="30"/>`。
- **信心水平**: 90%
- **误报排除**: 已确认枚举定义在生产代码中。
- **复核状态**: 未复核

### [维度04-06] 所有实体缺少 entity-level 审计属性（createTimeProp/createrProp 等）

- **文件**: `nop-code/model/nop-code.orm.xml` 全文（所有 11 个实体）
- **证据片段**:
  ```xml
  <!-- nop-code 实体定义（无审计属性）-->
  <entity className="io.nop.code.dao.entity.NopCodeIndex" displayName="代码索引"
          name="io.nop.code.dao.entity.NopCodeIndex" registerShortName="true"
          tableName="nop_code_index">
  ```
  ```xml
  <!-- nop-auth 基线（带审计属性）-->
  <entity createTimeProp="createTime" createrProp="createdBy"
          updateTimeProp="updateTime" updaterProp="updatedBy" 
          useLogicalDelete="true" versionProp="version" ...>
  ```
- **严重程度**: P2
- **现状**: 全部 11 个实体均未配置 entity-level 审计属性。3 个实体手动定义了部分审计列，但无属性绑定，框架不会自动填充。
- **风险**: 审计字段不会被框架自动赋值；无乐观锁；除 NopCodeSemanticEdge 外无逻辑删除。
- **建议**: 对 NopCodeFlow/NopCodeSemanticEdge 添加审计属性；对 NopCodeIndex 添加 versionProp 防并发。
- **信心水平**: 85%
- **误报排除**: 已确认 nop-auth 全部实体均有审计属性。降低了严重程度，因为索引数据场景对审计需求不同于业务数据。
- **复核状态**: 未复核

### [维度04-07] NopCodeFlow 审计字段命名偏离平台约定

- **文件**: `nop-code/model/nop-code.orm.xml:723-730`
- **证据片段**:
  ```xml
  <!-- nop-code 使用 createdTime/modifiedTime/modifiedBy -->
  <column code="CREATED_TIME" name="createdTime" stdSqlType="DATETIME"/>
  <column code="MODIFIED_TIME" name="modifiedTime" stdSqlType="DATETIME"/>
  <column code="MODIFIED_BY" name="modifiedBy" stdSqlType="VARCHAR"/>
  ```
  ```xml
  <!-- 平台标准是 createTime/updateTime/updatedBy -->
  <column code="CREATE_TIME" domain="createTime" name="createTime"/>
  <column code="UPDATE_TIME" domain="updateTime" name="updateTime"/>
  <column code="UPDATED_BY" domain="updatedBy" name="updatedBy"/>
  ```
- **严重程度**: P2
- **现状**: NopCodeFlow 使用 createdTime/modifiedTime/modifiedBy，平台标准是 createTime/updateTime/updatedBy。且未使用 domain。
- **风险**: 偏离命名惯例增加认知负担；即使添加审计属性也需额外配置映射。
- **建议**: 改为平台标准命名并使用 domain 定义。
- **信心水平**: 90%
- **误报排除**: 已通过 nop-auth 确认平台标准命名。
- **复核状态**: 未复核

### [维度04-08] NopCodeSemanticEdge 审计字段不完整且有 delFlag 但无 entity-level 逻辑删除配置

- **文件**: `nop-code/model/nop-code.orm.xml:836-841`
- **证据片段**:
  ```xml
  <column code="DEL_FLAG" name="delFlag" precision="1"
          stdDataType="boolean" stdSqlType="TINYINT" stdDomain="boolFlag"/>
  <!-- 缺少 UPDATED_BY, UPDATE_TIME -->
  <!-- entity 缺少 useLogicalDelete="true" deleteFlagProp="delFlag" -->
  ```
- **严重程度**: P2
- **现状**: delFlag 列存在但实体无 useLogicalDelete 配置，框架不会自动处理逻辑删除。只有 createdBy/createTime，缺少 updatedBy/updateTime。
- **风险**: delFlag 列存在但不起作用——框架执行 delete 时仍会物理删除。
- **建议**: 添加 useLogicalDelete="true" deleteFlagProp="delFlag"，或移除 delFlag 列。
- **信心水平**: 85%
- **误报排除**: 已确认 nop-auth 中 useLogicalDelete 的标准用法。NopCodeSemanticEdge 是全模块唯一有 delFlag 的实体。
- **复核状态**: 未复核

### [维度04-09] NopCodeSemanticEdge.RELATION_TYPE 缺少 ext:dict 定义

- **文件**: `nop-code/model/nop-code.orm.xml:824-825`
- **证据片段**:
  ```xml
  <column code="RELATION_TYPE" name="relationType" precision="40"
          stdDataType="string" stdSqlType="VARCHAR"/>
  <!-- 无 ext:dict -->
  ```
- **严重程度**: P3
- **现状**: NopCodeSemanticEdge.RELATION_TYPE 缺少 ext:dict 定义，UI 无法提供下拉选择或值标签显示。
- **风险**: 前端需要自行维护可选值列表。
- **建议**: 创建专用 dict（如 code/semantic_relation_type）。
- **信心水平**: 80%
- **误报排除**: 该字段的值域与 NopCodeInheritance 不同，不宜复用 code/relation_type。
- **复核状态**: 未复核

### [维度04-10] NopCodeDependency.RESOLVED 使用 SMALLINT 表示布尔语义

- **文件**: `nop-code/model/nop-code.orm.xml:663-664`
- **证据片段**:
  ```xml
  <column code="RESOLVED" name="resolved"
          stdDataType="int" stdSqlType="SMALLINT"/>
  ```
- **严重程度**: P3
- **现状**: RESOLVED 字段语义是布尔值但使用 SMALLINT，未使用 boolFlag 域。
- **风险**: 偏离平台布尔字段惯例，浪费存储空间，无法享受标准 UI 渲染。
- **建议**: 改为 stdSqlType="TINYINT" stdDomain="boolFlag"。
- **信心水平**: 85%
- **误报排除**: 已确认平台布尔标记字段的标准做法。
- **复核状态**: 未复核

## 无问题的检查项

1. **主键设计**: 所有实体使用 domain="codeId"（VARCHAR(36)）配合 tagSet="seq"，符合规范。
2. **关系反向侧完整性**: 反向关系由框架自动生成，模式正确。
3. **cascadeDelete 设计**: NopCodeIndex 对子实体的级联删除语义正确。
4. **索引覆盖**: 所有外键列均有索引覆盖。
5. **字段命名**: 数据库列名均为 snake_case，符合规范。
6. **未使用实体**: 所有 11 个实体均被引用，无孤立实体。
