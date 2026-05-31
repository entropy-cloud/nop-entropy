# 维度 15：类型安全与泛型使用

## 第 1 轮（初审）

### [维度15-01] instanceof ChangeAnalyzer 将接口下溯到具体实现类

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:130-132, 140-142`
- **证据片段**:
  ```java
  @Inject
  public void setFlowDetector(@Nullable IFlowDetector flowDetector) {
      this.flowDetector = flowDetector;
      if (flowDetector != null && this.changeAnalyzer instanceof ChangeAnalyzer) {
          ((ChangeAnalyzer) this.changeAnalyzer).setFlowDetector(flowDetector);
      }
  }
  ```
- **严重程度**: P2
- **现状**: `IFlowDetector`/`IChangeAnalyzer` 是接口但此处硬绑定到 `ChangeAnalyzer` 具体类。
- **风险**: 若注入其他实现，`setFlowDetector` 不会被调用，行为静默退化。
- **建议**: 在 `IChangeAnalyzer` 接口上声明 `setFlowDetector` 方法。
- **信心水平**: 确定
- **误报排除**: 绕过接口的 instanceof 下溯是真实的设计耦合问题。
- **复核状态**: 未复核

### [维度15-02] CodeMethodCall.confidence 使用 String 字面量而非已有的 EdgeConfidence 枚举

- **文件**: `nop-code/nop-code-core/src/main/java/io/nop/code/core/model/CodeMethodCall.java:19,101-107`
- **证据片段**:
  ```java
  private String confidence;  // EXTRACTED or INFERRED
  // ...
  // 赋值处（ProjectAnalyzer.java:444,450,453）
  call.setConfidence("EXTRACTED");
  call.setConfidence("INFERRED");
  ```
  而同模块已存在 `EdgeConfidence` 枚举：`EXTRACTED(10), INFERRED(20), AMBIGUOUS(30)`
- **严重程度**: P3
- **现状**: confidence 字段使用 String 而非已存在的 EdgeConfidence 枚举，字符串字面量无编译时约束。
- **风险**: 拼写错误不会在编译时被发现。
- **建议**: 将 confidence 类型改为 EdgeConfidence 枚举。
- **信心水平**: 确定
- **误报排除**: 同模块已有枚举定义但未复用。
- **复核状态**: 未复核

### [维度15-03] FileTreeNode.type 使用无约束 String 而非枚举

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/api/dto/FileTreeNode.java:14`
- **证据片段**:
  ```java
  private String type; // "package" or "file"
  ```
- **严重程度**: P3
- **现状**: type 的合法值只有 "package" 和 "file"，但无编译时约束。
- **建议**: 引入枚举 FileTreeNodeType { PACKAGE, FILE }。
- **信心水平**: 确定
- **误报排除**: 合法值有限且已知，适用枚举。
- **复核状态**: 未复核

### [维度15-04] extractFilePathFromSymbol 模式在四个文件中重复

- **文件**: FlowDetector.java:512-530, ImpactAnalyzer.java:333-351, DeadCodeDetector.java:368-387, ChangeAnalyzer.java:230-248
- **证据片段**:
  ```java
  private static String extractFilePathFromSymbol(CodeSymbol symbol) {
      String extData = symbol.getExtData();
      if (extData != null && extData.contains("filePath")) {
          try {
              Object parsed = JsonTool.parseNonStrict(extData);
              if (parsed instanceof Map) { /* ... */ }
          } catch (Exception e) { /* ... */ }
      }
      return null;
  }
  ```
- **严重程度**: P3
- **现状**: 完全相同的 extData 解析逻辑在四个文件中重复。
- **建议**: 抽取到 CodeSymbol 的工具方法或 CodeSymbolHelper。
- **信心水平**: 确定
- **误报排除**: 四处完全相同的逻辑是可量化的重复。
- **复核状态**: 未复核

### [维度15-05] Map<String, Boolean> 应替换为 Set<String>

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeGraphService.java:676,694,700,706,716`
- **证据片段**:
  ```java
  Map<String, Boolean> onStack = new HashMap<>();
  onStack.put(v, true);
  Boolean.TRUE.equals(onStack.get(w))
  ```
- **严重程度**: P3
- **现状**: Tarjan SCC 算法使用 `Map<String, Boolean>` 代替更清晰的 `Set<String>`。
- **建议**: 改用 `Set<String> onStack = new HashSet<>()`。
- **信心水平**: 确定
- **误报排除**: 经典 boolean map 反模式。
- **复核状态**: 未复核

## 零发现区域

- Raw Type 使用：零发现（所有集合均有泛型参数）
- DTO/DataBean 类型定义：零问题
- 不必要的 Object 参数：零发现

## 最终保留项

| 编号 | 严重程度 | 文件 | 一句话摘要 |
|------|---------|------|-----------|
| 15-01 | P2 | CodeIndexService.java:130-132 | instanceof 下溯绕过接口 |
| 15-02 | P3 | CodeMethodCall.java:19 | confidence 使用 String 而非已有 EdgeConfidence 枚举 |
| 15-03 | P3 | FileTreeNode.java:14 | type 使用 String 而非枚举 |
| 15-04 | P3 | 4个文件 | extractFilePathFromSymbol 重复 |
| 15-05 | P3 | CodeGraphService.java:676 | Map<String, Boolean> 应为 Set<String> |
