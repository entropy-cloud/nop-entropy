# 维度 04：ORM 模型与实体设计

## 第 1 轮（初审）

### [维度04-11] NopCodeCall.callType 字段值与字典定义完全不匹配

- **文件**: `nop-code/model/nop-code.orm.xml:543-544`
- **证据片段**:
  ```xml
  <column code="CALL_TYPE" displayName="调用类型" name="callType"
          precision="20" propId="8" stdDataType="string" stdSqlType="VARCHAR" ext:dict="code/call_type"/>
  ```
  而实际写入代码（`JavaFileAnalyzer.java:549`）：
  ```java
  call.setCallType(resolved.getReturnType().describe());
  ```
  `call_type.dict.yaml` 定义 DIRECT/VIRTUAL/STATIC/CONSTRUCTOR，但实际存储返回类型字符串如 `"void"`、`"java.lang.String"`。
- **严重程度**: P1
- **现状**: ORM 模型声明 `CALL_TYPE` 使用 `code/call_type` 字典，但唯一写入方将方法返回类型描述写入该字段。存储值与字典完全不匹配。
- **风险**: (1) 字典验证约束形同虚设。(2) 前端按字典渲染与实际数据不对应。(3) 按字典值过滤查询返回零结果。(4) precision=20 对返回类型描述可能不够。
- **建议**: 修正写入方使用正确的调用类型枚举值，或重命名字段并移除字典约束。
- **信心水平**: 确定
- **误报排除**: 已验证 JavaFileAnalyzer:549 是唯一写入点。call_type.dict.yaml 存在但其值从未被写入。
- **复核状态**: 未复核

### [维度04-12] 3 个实体有审计字段列但缺少实体级 createTimeProp/createrProp 声明

- **文件**: `nop-code/model/nop-code.orm.xml:755-794`（NopCodeFlow）、`829-850`（NopCodeFlowMembership）、`880-911`（NopCodeSemanticEdge）
- **证据片段**:
  ```xml
  <entity className="io.nop.code.dao.entity.NopCodeFlow" displayName="执行流"
          name="io.nop.code.dao.entity.NopCodeFlow" registerShortName="true"
          tableName="nop_code_flow">
  ```
  无 createTimeProp/createrProp/updateTimeProp/updaterProp 属性。
- **严重程度**: P2
- **现状**: NopCodeFlow、NopCodeFlowMembership、NopCodeSemanticEdge 有 createTime/createdBy/updateTime/updatedBy 列，但实体级标签缺少审计属性声明。框架的 OrmEntityListener 不会自动填充。
- **风险**: 审计字段依赖手动赋值，框架自动填充机制被绕过。
- **建议**: 添加实体级 `createTimeProp="createTime" createrProp="createdBy" updateTimeProp="updateTime" updaterProp="updatedBy"`。
- **信心水平**: 确定
- **误报排除**: 已对比 nop-auth 全部实体均使用实体级声明。
- **复核状态**: 未复核

### [维度04-13] NopCodeSemanticEdge 仅有 create 侧审计字段，缺少 updateTime/updatedBy

- **文件**: `nop-code/model/nop-code.orm.xml:906-911`
- **证据片段**:
  ```xml
  <column code="CREATED_BY" displayName="创建人" name="createdBy"
          propId="12" stdDataType="string" stdSqlType="VARCHAR"/>
  <column code="CREATE_TIME" displayName="创建时间" name="createTime"
          propId="13" stdDataType="timestamp" stdSqlType="DATETIME"/>
  ```
  无 UPDATE_TIME/UPDATED_BY 列。
- **严重程度**: P2
- **现状**: SemanticEdge 是唯一使用逻辑删除的实体，但缺少 update 审计字段。逻辑删除后无法追踪最后修改时间。
- **风险**: 逻辑删除实体的审计追踪不完整。extData/confidenceScore/rationale 可能在后续被更新。
- **建议**: 添加 UPDATE_TIME/UPDATED_BY 列及实体级声明。
- **信心水平**: 很可能
- **误报排除**: SemanticEdge 的 extData、confidenceScore 等字段可能在后续分析中被更新。
- **复核状态**: 未复核

### [维度04-14] NopCodeAnnotationUsage 唯一键包含可空列，NULL 场景下唯一性约束失效

- **文件**: `nop-code/model/nop-code.orm.xml:703-705`
- **证据片段**:
  ```xml
  <unique-key columns="indexId,annotationTypeId,annotatedSymbolId" name="uk_annotation_usage_unique"/>
  ```
  `annotatedSymbolId` 无 `mandatory="true"`。
- **严重程度**: P2
- **现状**: 唯一键由 (indexId, annotationTypeId, annotatedSymbolId) 组成，annotatedSymbolId 可为 NULL。SQL 标准中 NULL 不等于 NULL，唯一约束对 NULL 值失效。此外，唯一键未包含 line/column，对可重复注解会产生冲突。
- **风险**: (1) NULL 值场景下可重复插入。(2) 可重复注解场景下唯一键阻止合法记录。
- **建议**: (A) 将 annotatedSymbolId 设为 mandatory 并保留唯一键。(B) 如需支持可重复注解，加入 line/column。
- **信心水平**: 很可能
- **误报排除**: 当前 CodeIndexService:1064 过滤了 null 情况，但 ORM 模型本身允许 NULL，属隐性耦合。
- **复核状态**: 未复核

## 未修复的历史发现（04-01 ~ 04-10 摘要）

| 编号 | 摘要 | 等级 |
|------|------|------|
| 04-01 | call_type 字典值与实际存储不匹配 | P1 |
| 04-02 | SemanticEdge.relationType 复用 EXTENDS/IMPLEMENTS 字典，实际存储 9 种枚举值 | P1 |
| 04-03 | 审计时间列名 CREATED_TIME vs CREATE_TIME 不一致 | P1 |
| 04-04 | Java 属性名 createTime vs createdTime 不一致 | P2 |
| 04-05 | 审计字段仅存在于 3/11 个实体 | P2 |
| 04-07 | 审计字段未使用标准 domain + tagSet="clock" + mandatory | P2 |
| 04-08 | 仅 SemanticEdge 有 delFlag，删除策略不一致 | P2 |
| 04-09 | 无 i18n-en:displayName | P3 |
| 04-10 | 9 个 cascadeDelete 大数据量性能风险 | P3 |
| 04-06 | enclosingUsages 反向关系 | 驳回（codegen 自动生成） |
