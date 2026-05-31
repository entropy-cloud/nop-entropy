# 审核维度 15：类型安全与泛型使用

## 第 1 轮（初审）

### [维度15-01] deleteFileRecords 使用 List<?> + Object，类型不安全

- **文件**: `nop-code/nop-code-service/.../CodeIndexService.java:1154-1158`
- **证据片段**:
  ```java
  private void deleteFileRecords(String indexId, List<?> filePaths) {
      for (Object pathObj : filePaths) {
          String filePath = pathObj instanceof Path ? ((Path) pathObj).toString() : pathObj.toString();
  ```
- **严重程度**: P2
- **建议**: 使用泛型方法或拆分为两个重载方法。
- **复核状态**: 未复核

### [维度15-02] BFS 使用 String[] 编码 (node, depth) 对，类型不安全

- **文件**: `nop-code/nop-code-service/.../CodeGraphService.java:628-647`
- **证据片段**:
  ```java
  queue.add(new String[]{start, "0"});
  String[] current = queue.poll();
  int d = Integer.parseInt(current[1]);  // String -> int 转换
  ```
- **严重程度**: P2
- **建议**: 使用 record NodeDepth(String node, int depth) 替代。
- **复核状态**: 未复核

### [维度15-03] 同上模式出现在 CodeQueryService.java:711-728

- **严重程度**: P2
- **建议**: 提取为通用 BFS 工具方法，使用类型安全结构。
- **复核状态**: 未复核
