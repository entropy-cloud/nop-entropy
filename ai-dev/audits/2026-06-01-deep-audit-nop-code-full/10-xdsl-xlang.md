# 维度 10：XDSL 与 XLang 正确性 + 维度 15：类型安全与泛型使用 — nop-code 模块

## 维度 10 结论：零发现

所有 XDSL 文件（22 个 xbiz、6 个 beans.xml）检查通过：
- x:schema 引用正确
- x:extends 使用场景正确
- entityName 全部指向正确 Java 实体
- beans.xml bean 定义与 Java 类路径一致
- xbiz 方法声明与 BizModel 兼容

## 维度 15：类型安全与泛型使用

### 第 1 轮（初审）

### [维度15-01] entityToCodeSymbol 三重复制

- **文件**: `CodeIndexService.java:209-238`, `CodeQueryService.java:35-64`, `CodeGraphService.java:304-333`
- **证据片段**: 约 30 行字段逐一转换代码（25 个字段）在 3 处完全相同。
- **严重程度**: P2
- **现状**: NopCodeSymbol 实体字段的增减需同步修改三处。
- **建议**: 提取为共享 CodeSymbolConverter 工具类。
- **信心水平**: 确定
- **误报排除**: 三份代码完全相同。
- **复核状态**: 未复核

### [维度15-02] extractFilePathFromSymbol 四重复制

- **文件**: `FlowDetector.java:512-530`, `ImpactAnalyzer.java:333-351`, `ChangeAnalyzer.java:230-248`, `DeadCodeDetector.java:368-387`
- **证据片段**: 从 CodeSymbol.extData 解析 filePath 的代码在 4 处完全相同，每处都有 @SuppressWarnings("unchecked")。
- **严重程度**: P2
- **现状**: 分散的 JSON 解析 + 类型转换逻辑。
- **建议**: 在 CodeSymbol 模型类中提供 getFilePathFromExtData() 方法。
- **信心水平**: 确定
- **误报排除**: 4 处代码完全相同。
- **复核状态**: 未复核

### [维度15-03] 类型符号过滤逻辑四重复制

- **文件**: `CodeQueryService.java:161-164,174-178`, `NopCodeFileBizModel.java:63-66,84-87`
- **证据片段**:
  ```java
  s.getKind() == CodeSymbolKind.CLASS || s.getKind() == CodeSymbolKind.INTERFACE
      || s.getKind() == CodeSymbolKind.ENUM || s.getKind() == CodeSymbolKind.ANNOTATION_TYPE
  ```
- **严重程度**: P3
- **现状**: 4 个条件的组合重复出现 4 次。
- **建议**: 在 CodeSymbolKind 枚举中添加 static boolean isTypeKind(CodeSymbolKind kind)。
- **信心水平**: 确定
- **误报排除**: TypeScriptCodeFileAnalyzer 已有 isTypeSymbol() 但仅在内部使用。
- **复核状态**: 未复核

### [维度15-04] CommunityDetectionResultDTO.algorithmUsed 的 "none" 值不一致

- **文件**: `NopCodeIndexBizModel.java:131`, `CommunityDetectionResultDTO.java`
- **证据片段**: 当结果为 null 时设置 `result.setAlgorithmUsed("none")`，字符串 "none" 不对应 AlgorithmType 枚举的任何值。
- **严重程度**: P3
- **现状**: API 返回值命名空间不一致。
- **建议**: 使用 null 或空字符串。
- **信心水平**: 确定
- **误报排除**: 无。
- **复核状态**: 未复核
