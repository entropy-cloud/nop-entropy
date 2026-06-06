# 维度 03：API 表面积与契约一致性

## 第 1 轮（初审）

### [维度03-01] NopCodeFileBizModel.getByPath 返回 CodeFileAnalysisResult 包含 sourceCode，仅需 code-query 权限

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeFileBizModel.java:34-40`
- **证据片段**:
  ```java
  @BizQuery
  @Auth(permissions = "code-query")
  public CodeFileAnalysisResult getByPath(
          @Name("filePath") String filePath,
          @Name("indexId") String indexId) {
      return codeIndexService.getFile(indexId, filePath);
  }
  
  // 对比：同类型 sourceCode 字段的 loader 需更严格权限
  @BizLoader(forType = CodeFileAnalysisResult.class)
  @Auth(permissions = "code-source-read")
  public String sourceCode(@ContextSource CodeFileAnalysisResult file) {
      return file.getSourceCode();
  }
  ```
- **严重程度**: P3
- **现状**: getByPath 返回的对象内部填充了 sourceCode，但仅需 code-query 权限。而 sourceCode BizLoader 要求 code-source-read 更严格权限。
- **风险**: 如果 GraphQL 框架在直接返回对象时不经过 BizLoader 的字段级权限过滤，则 code-query 用户可读取源代码。
- **建议**: 确认 Nop 的 GraphQL 序列化管线在直接返回实体对象时是否尊重 @BizLoader 的字段级 @Auth。
- **信心水平**: 中 (60%)
- **误报排除**: BizLoader 字段级权限可能在 GraphQL 序列化时覆盖返回对象的字段可见性。
- **复核状态**: 未复核

## 合规性检查

| 检查项 | 结果 |
|--------|------|
| I*Biz 接口覆盖 | 合规（按 Nop 设计，I*Biz 是 CRUD 标记接口，自定义方法通过注解发现）|
| xmeta 覆盖 | 合规（自定义方法参数通过 @Name 声明）|
| 无 Map<String, Object> | 合规（仅在内部聚合中使用）|
| 无死 API | 合规（所有方法均为功能性端点）|
| xmeta 字段权限 | 合规（sourceCode 设置 published="false"）|
