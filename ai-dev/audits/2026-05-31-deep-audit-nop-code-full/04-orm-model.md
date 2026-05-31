# 维度04：ORM 模型与实体设计

**模块**: nop-code
**审计日期**: 2026-05-31

---

## 第 1 轮（初审）

### [维度04-01] 文件级增量删除缺少级联清理，产生孤儿记录

- **文件**: `nop-code/model/nop-code.orm.xml:204-217` + `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:1173-1193`
- **证据片段**:
  ```xml
  <!-- nop-code.orm.xml:211-216 — NopCodeFile的symbols关系无cascadeDelete -->
  <to-many displayName="符号列表" name="symbols" refEntityName="io.nop.code.dao.entity.NopCodeSymbol"
           refPropName="file">
      <join>
          <on leftProp="id" rightProp="fileId"/>
      </join>
  </to-many>
  ```
  ```java
  // CodeIndexService.java:1173-1193 — deleteFileRecords 方法
  private void deleteFileRecords(String indexId, List<String> filePaths) {
      for (String filePath : filePaths) {
          String fileId = generateFileId(indexId, filePath);
          List<String> symbolIds = findSymbolIdsByFileId(fileId);
          deleteEntitiesByFilter(NopCodeCall.class, "fileId", fileId);
          deleteEntitiesByFilter(NopCodeSymbol.class, "fileId", fileId);
          // ... 遗漏了 NopCodeUsage, NopCodeSemanticEdge, NopCodeFlowMembership 等
      }
  }
  ```
- **严重程度**: P1
- **现状**: ORM 模型仅在 NopCodeIndex 级别定义了完整的 cascadeDelete（9 个 to-many 全部 cascade），而 NopCodeFile 的 to-many 关系（symbols）无 cascadeDelete。文件级增量删除完全依赖 service 层手动清理，但 `deleteFileRecords` 方法遗漏了以下实体：NopCodeUsage、NopCodeSemanticEdge、NopCodeFlowMembership、NopCodeFlow。
- **风险**: 增量重新索引时删除/变更文件会产生至少 6 张表的孤儿记录，导致查询结果不准确且数据随时间膨胀。
- **建议**: (1) 为 NopCodeFile 的 symbols 关系添加 `cascadeDelete="true"`；(2) 补全 `deleteFileRecords` 方法中对 NopCodeUsage、NopCodeSemanticEdge、NopCodeFlowMembership、NopCodeFlow 的清理逻辑；(3) 添加文件级删除的集成测试。
- **信心水平**: 确定
- **误报排除**: NopCodeIndex 已定义了完整的 cascadeDelete 作为参照，NopCodeFile 的不完整手动清理直接导致可验证的数据完整性违规。
- **复核状态**: 未复核

---

### [维度04-02] NopCodeUsage、NopCodeInheritance、NopCodeAnnotationUsage 缺少唯一约束

- **文件**: `nop-code/model/nop-code.orm.xml:404-469` (NopCodeUsage), `542-591` (NopCodeInheritance), `594-647` (NopCodeAnnotationUsage)
- **证据片段**:
  ```xml
  <!-- NopCodeUsage (404-469) — 无 unique-keys 节 -->
  <!-- 对比 NopCodeCall (536-538) — 有唯一约束 -->
  <unique-keys>
      <unique-key columns="indexId,callerId,calleeId,line,column" name="uk_call_unique"/>
  </unique-keys>
  ```
- **严重程度**: P2
- **现状**: 11 个实体中有 4 个关系实体定义了 unique-key，但 NopCodeUsage、NopCodeInheritance、NopCodeAnnotationUsage 三个关系实体缺少唯一约束。在增量索引场景下可被重复插入。
- **风险**: 重复记录累积导致查询结果膨胀，且难以事后去重。
- **建议**: 为 NopCodeUsage 添加 `(indexId, symbolId, fileId, kind, line)`；NopCodeInheritance 添加 `(indexId, subTypeId, superTypeId, relationType)`；NopCodeAnnotationUsage 添加 `(indexId, annotationTypeId, annotatedSymbolId)`。
- **信心水平**: 很可能
- **误报排除**: 同模块 NopCodeCall、NopCodeFlowMembership、NopCodeSemanticEdge 均已定义 unique-key，说明这是遗漏而非设计选择。
- **复核状态**: 未复核

---

### [维度04-03] 审计字段覆盖不完整且命名不一致

- **文件**: `nop-code/model/nop-code.orm.xml:726-735` (NopCodeFlow), `841-846` (NopCodeSemanticEdge)
- **证据片段**:
  ```xml
  <!-- NopCodeFlow: createdTime/modifiedTime -->
  <column code="CREATED_TIME" name="createdTime" .../>
  <column code="MODIFIED_TIME" name="modifiedTime" .../>
  <!-- NopCodeSemanticEdge: createTime (非 createdTime) -->
  <column code="CREATE_TIME" name="createTime" .../>
  ```
- **严重程度**: P2
- **现状**: 11 个实体中仅 3 个有审计字段，且命名不一致：NopCodeFlow 使用 `createdTime`/`modifiedTime`，NopCodeSemanticEdge 使用 `createTime`。其余 8 个实体完全没有审计字段。
- **风险**: 命名不一致导致编码时混淆 `getCreateTime()` vs `getCreatedTime()`，编译时才能发现。
- **建议**: 统一审计字段命名为 `createTime`/`updateTime`/`createdBy`/`updatedBy`，至少为核心实体补全审计字段。
- **信心水平**: 很可能
- **误报排除**: 同一模块内已存在的审计字段命名互相矛盾。
- **复核状态**: 未复核

---

### [维度04-04] NopCodeDependency.resolved 语义为布尔值但类型声明为 Integer/SMALLINT

- **文件**: `nop-code/model/nop-code.orm.xml:668-669`
- **证据片段**:
  ```xml
  <column code="RESOLVED" displayName="是否已解析" name="resolved"
          propId="6" stdDataType="int" stdSqlType="SMALLINT"/>
  ```
  ```java
  // CodeIndexService.java:1124 — 手动 boolean↔int 转换
  depEntity.setResolved(dep.isResolved() ? 1 : 0);
  ```
- **严重程度**: P2
- **现状**: `resolved` 字段语义为布尔值（"是否已解析"），但声明为 SMALLINT。同模块其他所有布尔字段统一使用 `stdSqlType="BOOLEAN"`。
- **风险**: 每次读写需手动转换，容易遗漏导致语义错误；null 值处理不一致。
- **建议**: 改为 `stdDataType="boolean" stdSqlType="BOOLEAN"`，消除手动转换。
- **信心水平**: 确定
- **误报排除**: 同模块 12 个其他布尔字段全部使用 BOOLEAN，此字段是唯一例外。
- **复核状态**: 未复核

---

### [维度04-05] 英文 i18n 翻译全部缺失

- **文件**: `nop-code/nop-code-meta/src/main/resources/_vfs/i18n/en/_nop-code.i18n.yaml`
- **证据片段**:
  ```yaml
  # en/_nop-code.i18n.yaml — 所有值均为 null
  entity:
    label:
      NopCodeSymbol: null
  ```
- **严重程度**: P3
- **现状**: 中文 i18n 已完整填充，但英文 i18n 文件中所有值均为 null。
- **风险**: 英文 locale 运行时 UI 标签显示为原始名称。如果仅面向中文用户，无实际影响。
- **建议**: 补全英文翻译，或确定仅支持中文后删除空的英文 i18n 文件。
- **信心水平**: 确定
- **误报排除**: 中文 i18n 已完整填充，英文缺失是明确遗漏。
- **复核状态**: 未复核
