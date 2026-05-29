# 维度 15：类型安全与泛型使用

**审计日期**: 2026-05-29

## 第 1 轮（初审）

### [维度15-01] CodeMethodCall.confidence 用 String 代替枚举

- **文件**: `nop-code/nop-code-core/src/main/java/io/nop/code/core/model/CodeMethodCall.java:20`
- **证据片段**:
  ```java
  private String confidence;  // EXTRACTED or INFERRED
  ```
- **严重程度**: P3
- **现状**: 只有 EXTRACTED/INFERRED 两个固定值，但类型为 String。
- **风险**: 拼写错误无法在编译期发现。
- **建议**: 定义 CallConfidence 枚举。
- **信心水平**: 85%
- **误报排除**: 注释明确标注了两个固定值，属于封闭域。
- **复核状态**: 未复核

### [维度15-02] CodeMethodCall.callType 用 String 代替枚举

- **文件**: `nop-code/nop-code-core/src/main/java/io/nop/code/core/model/CodeMethodCall.java:16`
- **证据片段**:
  ```java
  private String callType;
  ```
- **严重程度**: P3
- **现状**: 合法值依赖运行时约定，无编译期类型约束。
- **建议**: 引入 CallType 枚举。
- **信心水平**: 80%
- **误报排除**: 从 Java 分析器代码看值域是封闭的。
- **复核状态**: 未复核

### [维度15-03] RiskLevel 枚举已定义但未使用，evaluateRisk 返回小写字符串

- **文件**: `nop-code/nop-code-graph/src/main/java/io/nop/code/graph/impact/ImpactAnalyzer.java:144-149,279-282`
- **证据片段**:
  ```java
  public enum RiskLevel { LOW, MEDIUM, HIGH, CRITICAL }
  
  // 但 evaluateRisk() 返回：
  if (totalImpacted > 50) return "critical";  // 小写字符串
  ```
- **严重程度**: P2
- **现状**: RiskLevel 枚举是死代码。DTO 层 riskLevel 是 String 类型。
- **风险**: 枚举误导读者以为系统使用枚举；字符串值（小写）与枚举名（大写）不一致。
- **建议**: 让 evaluateRisk() 返回 RiskLevel 枚举，或删除未使用的枚举。
- **信心水平**: 95%
- **误报排除**: RiskLevel 枚举确实未被任何代码引用。
- **复核状态**: 未复核

### [维度15-04] deleteFileRecords 使用 List<?> 通配符加运行时 instanceof

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:2341-2345`
- **证据片段**:
  ```java
  private void deleteFileRecords(String indexId, List<?> filePaths) {
      for (Object pathObj : filePaths) {
          String filePath = pathObj instanceof Path ? ((Path) pathObj).toString() : pathObj.toString();
  ```
- **严重程度**: P3
- **现状**: 接受 List<?>，内部用 instanceof 做运行时类型判断。
- **建议**: 提供两个重载方法或在调用点统一类型。
- **信心水平**: 90%
- **误报排除**: 当前能正常工作，是类型精度问题。
- **复核状态**: 未复核

### [维度15-05] BFS 遍历用 String[] 编码 {node, depth}

- **文件**: `CodeIndexService.java:1697-1716,2823-2840`; `ImpactAnalyzer.java:233-241`
- **证据片段**:
  ```java
  Queue<String[]> queue = new LinkedList<>();
  queue.add(new String[]{start, "0"});
  int d = Integer.parseInt(current[1]);
  ```
- **严重程度**: P3
- **现状**: 使用 String[] 编码深度，可读性差。
- **建议**: 定义 BfsEntry record。
- **信心水平**: 90%
- **误报排除**: 功能上无问题。
- **复核状态**: 未复核

### [维度15-06] tarjanSCC 用 Map<String, Boolean> 代替 Set<String>

- **文件**: `CodeIndexService.java:1747,1771,1777,1787`
- **证据片段**:
  ```java
  Map<String, Boolean> onStack = new HashMap<>();
  onStack.put(v, true);
  Boolean.TRUE.equals(onStack.get(w));
  ```
- **严重程度**: P3
- **现状**: 应使用 Set<String> 更自然。
- **建议**: 替换为 Set<String>。
- **信心水平**: 95%
- **误报排除**: 标准 Set 替代 Map<K,Boolean> 惯用法。
- **复核状态**: 未复核

### [维度15-07] 双括号初始化 QueryBean 风格不一致

- **文件**: `CodeIndexService.java:2798-2800`
- **证据片段**:
  ```java
  new QueryBean() {{
      addFilter(FilterBeans.eq("indexId", indexId));
  }}
  ```
- **严重程度**: P3
- **现状**: 创建匿名内部类，持有外部类 this 引用（3004行大对象）。
- **建议**: 改为常规初始化。
- **信心水平**: 90%
- **误报排除**: 不会导致 bug，但风格不一致且有隐式 this 引用。
- **复核状态**: 未复核

## 无问题区域

- Raw Type 使用为 0
- @SuppressWarnings("unchecked") 在生产代码中为 0
- DTO 层 String 编码枚举在 API 场景下是合理的序列化选择
- Nop ORM 框架的 IOrmEntity 强制转换是平台限制
