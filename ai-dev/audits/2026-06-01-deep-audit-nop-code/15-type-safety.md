# 维度 15：类型安全与泛型使用

## 第 1 轮（初审）

### [维度15-01] ImpactAnalyzer.traceImpact 使用 String[] 承载 BFS 节点+深度信息

- **文件**: `nop-code/nop-code-graph/src/main/java/io/nop/code/graph/impact/ImpactAnalyzer.java:238-269`
- **证据片段**:
  ```java
  Queue<String[]> queue = new LinkedList<>();
  queue.add(new String[]{startId, "0"});
  while (!queue.isEmpty()) {
      String[] current = queue.poll();
      String nodeId = current[0];
      int depth = Integer.parseInt(current[1]);  // String → int
      ...
      queue.add(new String[]{neighborId, String.valueOf(depth + 1)});
  }
  ```
- **严重程度**: P2
- **现状**: BFS 队列使用 String[] 传递 (nodeId, depth)。深度在 String 和 int 之间反复转换。
- **风险**: Integer.parseInt 可能 NumberFormatException；String[] 长度无编译期约束；无语义信息。
- **建议**: 引入 record BfsEntry(String nodeId, int depth)。项目内已有 BfsNode record 先例。
- **信心水平**: 确定
- **误报排除**: 纯算法类，不涉及 Nop 动态边界。
- **复核状态**: 未复核

### [维度15-02] GraphExporter.exportJson 使用 String[] 承载边信息

- **文件**: `nop-code/nop-code-graph/src/main/java/io/nop/code/graph/export/GraphExporter.java:168-176`
- **证据片段**:
  ```java
  List<String[]> allEdges = new ArrayList<>();
  for (String caller : callGraph.getAllNodeIds()) {
      for (String callee : callGraph.getCallees(caller)) {
          allEdges.add(new String[]{caller, callee});
      }
  }
  ```
- **严重程度**: P3
- **现状**: String[] 承载 (source, target) 边信息，通过下标 [0]/[1] 访问。
- **风险**: 维护成本稍高，实际出错概率低。
- **建议**: 使用 Map.Entry 或内联序列化。
- **信心水平**: 很可能
- **误报排除**: 不涉及 Nop 动态边界。
- **复核状态**: 未复核

### [维度15-03] DTO 层 kind/accessModifier 使用 String 而非枚举

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/api/dto/SymbolDTO.java:14-15`
- **证据片段**:
  ```java
  private String kind;
  private String accessModifier;
  ```
  对比 core 模型：
  ```java
  private CodeSymbolKind kind;
  private CodeAccessModifier accessModifier;
  ```
- **严重程度**: P3
- **现状**: 所有 DTO 的 kind、accessModifier 等字段使用 String 而非枚举。
- **风险**: 调用方可传入任意字符串，编译期无约束。但这是 API DTO 层常见实践。
- **建议**: 可通过 XMeta schema 约束。若 DTO 仅供内部 Java 消费，可考虑枚举。
- **信心水平**: 很可能
- **误报排除**: DTO 使用 String 有序列化兼容性理由，是设计取舍。
- **复核状态**: 未复核

## 可接受/无需变更

- ExtDataHelper @SuppressWarnings: JSON 解析标准模式，有 instanceof 守卫
- CodeIndexService ORM casts: Nop ORM 框架 API 设计决定
- CodeIndexService Map<String,Object>: 框架 selectFieldsByQuery/orm_initedValues 返回类型
- ICrudBiz<NopCodeIndex> 泛型精度: 正确
- 手写代码无原始类型（Raw Type）问题
- 测试代码 @SuppressWarnings: ApiResponse 动态边界，标准实践
