# 维度15：类型安全与泛型使用

## 第 1 轮（初审）

### [维度15-01] CodeIndexService.findImplementations 使用 String[] 传递混合类型数据

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java`
- **行号**: L2825-2842
- **证据片段**:
  ```java
  Queue<String[]> queue = new LinkedList<>();
  queue.add(new String[]{qualifiedName, "0"});
  // ...
  String[] current = queue.poll();
  String superQn = current[0];
  int d = Integer.parseInt(current[1]);
  ```
- **严重程度**: P2
- **现状**: BFS 使用 String[] 同时传递节点名和深度值，String-int 反复转换。
- **风险**: 类型不安全（数组长度无编译期保障），性能浪费，可读性差。
- **建议**: 使用 record Entry(String qualifiedName, int depth) 替代 String[]。
- **信心水平**: 确定
- **误报排除**: 使用 String[] 传递混合类型是明确的反模式。
- **复核状态**: 未复核

### [维度15-02] CodeIndexService 使用双括号初始化 QueryBean

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java`
- **行号**: L2800-2802
- **证据片段**:
  ```java
  for (NopCodeSymbol sym : symDaoForInh.findAllByQuery(new QueryBean() {{
      addFilter(FilterBeans.eq("indexId", indexId));
  }})) {
  ```
- **严重程度**: P2
- **现状**: 双括号初始化创建匿名内部类，持有外部类引用，可能导致序列化问题和内存泄漏。
- **建议**: 替换为标准写法。
- **信心水平**: 很可能
- **误报排除**: 功能无 bug，但有编码规范和潜在内存问题。
- **复核状态**: 未复核

## 通过项

1. @SuppressWarnings("unchecked") 使用合理（JsonTool.parseNonStrict 后 instanceof 检查）
2. 全部 33 个 DTO 正确使用泛型（List、Map、Set 均带泛型参数）
3. ICodeIndexService 接口泛型精度良好
4. DTO 均标注 @DataBean 并实现 Serializable
