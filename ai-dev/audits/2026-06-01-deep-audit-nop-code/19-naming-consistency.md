# 维度19：命名与术语一致性 -- nop-code 模块审计报告

## 第 1 轮（初审）

### [维度19-01] ORM 时间戳字段命名不一致（已在维度04报告，交叉引用）

详见 `04-orm-model.md` [维度04-03] 和 [维度04-04]。

### [维度19-02] `superTypeId` 字段的双重语义

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:1018,783-785`
- **证据片段**:
  ```java
  // 初始写入时存储全限定名
  inhEntity.setSuperTypeId(inh.getSuperTypeQualifiedName());
  // 解析后更新为符号 ID
  CodeSymbol resolved = symbolTable.getByQualifiedName(superTypeId);
  if (resolved != null) { inh.setSuperTypeId(resolved.getId()); }
  ```
  `CodeGraphService.java:310`:
  ```java
  inh.setSuperTypeQualifiedName(entity.getSuperTypeId());
  // ↑ 用 getSuperTypeId() 的值去 set "QualifiedName"
  ```
- **严重程度**: P2
- **现状**: `superTypeId` 字段名暗示存储 ID，但解析之前实际存储的是 `qualifiedName`。导致 CodeGraphService 中出现语义反转的代码。
- **风险**: 开发者可能误认为该字段始终是 ID，导致查询错误。
- **建议**: 添加注释说明"先存 QN、后解析为 ID"的双阶段语义，或拆分为两个字段。
- **信心水平**: 90%
- **误报排除**: 已确认代码中实际存在语义反转。
- **复核状态**: 未复核

### [维度19-03] NopCodeSemanticEdge 独有 `delFlag`，命名策略不一致（已在维度04报告，交叉引用）

详见 `04-orm-model.md` [维度04-08]。

## 正面发现

- **错误码前缀一致**: `NopCodeErrors` 和 `NopCodeCoreErrors` 均使用 `nop.err.code.*` 前缀。
