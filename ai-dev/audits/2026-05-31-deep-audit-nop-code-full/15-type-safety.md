# 维度15：类型安全与泛型使用

**模块**: nop-code
**审计日期**: 2026-05-31

---

## 第 1 轮（初审）

### [维度15-01] getKind().name() 无 null 保护导致 NPE

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeGraphService.java:216`
- **证据片段**:
  ```java
  if (symbol != null) {
      symbolInfo.setName(symbol.getName());
      symbolInfo.setQualifiedName(symbol.getQualifiedName());
      symbolInfo.setKind(symbol.getKind().name());  // getKind() 可能为 null → NPE
  }
  ```
  同一问题还出现在：
  - `CodeGraphService.java:266`
  - `CodeQueryService.java:540`
- **严重程度**: P0
- **现状**: `symbol != null` 只检查了外层引用，未检查 `kind`。当数据库中 kind 列为空时抛 NullPointerException。
- **风险**: `buildTypeHierarchy`/`buildCallHierarchy` API 调用直接失败。同文件行 398 已有正确的 null 保护写法。
- **建议**: 改为 `symbol.getKind() != null ? symbol.getKind().name() : null`。
- **信心水平**: 95%
- **误报排除**: 同文件行 398 和 375 已有正确的 null 保护，说明是遗漏。
- **复核状态**: 未复核

---

### [维度15-02] extData 手工拼接 JSON 存在数据损坏风险

- **文件**: `nop-code-lang-java/src/main/java/io/nop/code/lang/java/analyzer/JavaFileAnalyzer.java:203-209`
- **证据片段**:
  ```java
  String permitsExtData = permitsList.isEmpty()
          ? "{\"sealed\":true}"
          : "{\"sealed\":true,\"permits\":\"" + permitsList + "\"}";
  symbol.setExtData(existingExtData != null
          ? existingExtData.substring(0, existingExtData.length() - 1) + ",\"sealed\":true,\"permits\":\"" + permitsList + "\"}"
          : permitsExtData);
  ```
- **严重程度**: P1
- **现状**: 通过字符串截断和拼接构造 JSON。`permitsList` 若含双引号/反斜杠会产生无效 JSON。
- **风险**: 下游 4 处 `JsonTool.parseNonStrict(extData)` 返回 null 或异常。
- **建议**: 使用 `Map<String,Object>` + `JsonTool.stringify(map)` 构造 JSON。
- **信心水平**: 90%
- **误报排除**: permitsList 当前来自 JavaParser 的 getNameAsString()，仅产生合法标识符。但 extData 是通用 JSON 字段。
- **复核状态**: 未复核

---

### [维度15-03] confidence 字段使用魔法字符串而非枚举

- **文件**: `nop-code-core/src/main/java/io/nop/code/core/model/CodeMethodCall.java:19`
- **证据片段**:
  ```java
  private String confidence; // EXTRACTED or INFERRED
  ```
- **严重程度**: P2
- **现状**: 两个固定值用 String 表示，无编译期保障。
- **建议**: 定义枚举 `CallConfidence { EXTRACTED, INFERRED }`。
- **信心水平**: 85%
- **误报排除**: 同模块 CodeSymbolKind、EdgeConfidence 已枚举化。
- **复核状态**: 未复核

---

### [维度15-04] extData 提取 filePath 逻辑四处重复 + unchecked cast

- **文件**: `ImpactAnalyzer.java:334`, `FlowDetector.java:512`, `DeadCodeDetector.java:368`, `ChangeAnalyzer.java:230`
- **证据片段**:
  ```java
  @SuppressWarnings("unchecked")
  Map<String, Object> map = (Map<String, Object>) parsed;
  ```
- **严重程度**: P2
- **现状**: 同一"从 extData JSON 中提取 filePath"逻辑重复 4 次。
- **建议**: 提取为 `CodeSymbol.getFilePathFromExtData()` 工具方法。
- **信心水平**: 90%
- **误报排除**: @SuppressWarnings 的出现说明类型安全可改进。
- **复核状态**: 未复核

---

### [维度15-05] FileTreeNode.type 使用魔法字符串

- **文件**: `nop-code-service/src/main/java/io/nop/code/service/api/dto/FileTreeNode.java:14`
- **证据片段**:
  ```java
  private String type; // "package" or "file"
  ```
- **严重程度**: P3
- **现状**: 仅在注释中约束合法值。
- **建议**: 定义枚举或添加 codeEnum 注解。
- **信心水平**: 80%
- **误报排除**: DTO 字段为 String 可能为了 GraphQL schema 灵活性。
- **复核状态**: 未复核
