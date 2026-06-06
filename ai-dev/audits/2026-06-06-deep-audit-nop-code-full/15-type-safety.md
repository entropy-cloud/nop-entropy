# 维度 15：类型安全与泛型使用

## 第 1 轮（初审）

### [维度15-01] IFlowDetector 接口泄漏具体类型 FlowDetector

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeCacheManager.java:119-123`
- **证据片段**:
  ```java
  if (flowDetector instanceof FlowDetector) {
      ((FlowDetector) flowDetector).invalidateCache(indexId);
  }
  ```
- **严重程度**: P3
- **现状**: IFlowDetector 接口无 invalidateCache 方法，调用方依赖具体类型。
- **建议**: 将 invalidateCache 提升到 IFlowDetector 接口。
- **信心水平**: 确定
- **误报排除**: 已在维度02-04报告同类问题，此处从类型安全角度补充。
- **复核状态**: 未复核

### [维度15-02] ImpactAnalyzer 使用 String[] 模拟二元组

- **文件**: `nop-code/nop-code-graph/src/main/java/io/nop/code/graph/impact/ImpactAnalyzer.java:238-269`
- **证据片段**:
  ```java
  Queue<String[]> queue = new LinkedList<>();
  queue.add(new String[]{startId, "0"});
  String nodeId = current[0];
  int depth = Integer.parseInt(current[1]);
  ```
- **严重程度**: P3
- **现状**: 用 String[] 传递 (nodeId, depth) 对，每次迭代 parseInt。
- **建议**: 使用已有的 BfsNode 类替代。
- **信心水平**: 确定
- **误报排除**: BfsNode 已存在于 io.nop.code.core.util 包中。
- **复核状态**: 未复核

## 检查通过项

- 无 Raw Type 使用
- Map<String, Object> 仅限于 JSON 序列化边界
- 核心接口全部使用具体类型
- @SuppressWarnings("unchecked") 使用合理（5处，均在 JSON 解析边界）
- 强制类型转换均为框架/库 API 限制导致
