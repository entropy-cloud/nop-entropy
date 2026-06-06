# 维度 13+14：安全与权限模型 + 异步与事务模式

## 维度 13：安全与权限模型

### [维度13-01] filterByFilePattern glob转正则未转义元字符，ReDoS 风险

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeSearchService.java`
- **行号**: 282-287
- **证据片段**:
  ```java
  String pattern = filePattern.replace(".", "\\.").replace("*", ".*").replace("?", ".");
  return results.stream()
          .filter(r -> r.getFilePath() != null && r.getFilePath().matches(pattern))
          .collect(Collectors.toList());
  ```
- **严重程度**: P2
- **现状**: 只处理 .、*、? 三个字符，正则元字符（+、{、}、(、)、[、]、^、$、|）未转义。
- **风险**: 输入 (.+)+ 导致 ReDoS；a|b 非预期匹配。
- **建议**: 使用 Pattern.quote(filePattern).replace("\\*", ".*").replace("\\?", ".")
- **信心水平**: 确定
- **误报排除**: 真实输入处理缺陷。
- **复核状态**: 未复核

### [维度13-02] indexFile sourceCode 无长度限制

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeIndexBizModel.java`
- **行号**: 105-113
- **证据片段**:
  ```java
  @BizMutation
  @Auth(roles = "admin")
  public FileAnalysisDTO indexFile(
          @Name("indexId") String indexId,
          @Name("filePath") String filePath,
          @Name("sourceCode") String sourceCode) {
  ```
- **严重程度**: P2
- **现状**: sourceCode 无 maxLength 或 @Size 校验，可提交任意长度字符串。
- **风险**: 恶意 admin 提交数百 MB sourceCode 导致 OOM 或 DB 膨胀。
- **建议**: 添加长度限制（如 1MB）。
- **复核状态**: 未复核

## 维度 14：异步与事务模式

### [维度14-01] indexDirectory 在事务内执行 AST 分析，长事务风险

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java`
- **行号**: 332-349
- **证据片段**:
  ```java
  return withIndexLock(indexId, () -> {
      ProjectAnalysisResult result = analyzer.analyzeProject(...); // CPU密集型
      return transactionTemplate.runInTransaction(null, ..., txn ->
          ormTemplate.runInSession(session -> {
              persistInSession(...); // 全部持久化
          }));
  });
  ```
- **严重程度**: P2
- **现状**: AST 解析（CPU密集型）在事务锁内执行，大型项目事务可能持续分钟级。
- **风险**: 数据库连接长时间占用；ReentrantLock 阻塞所有同 indexId 操作。
- **建议**: 将 analyzeProject 移到事务外，持久化分批提交。
- **复核状态**: 未复核

### [维度14-02] indexLocks 永不清除，内存泄漏

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java`
- **行号**: 105, 587-619
- **严重程度**: P3
- **现状**: ConcurrentHashMap 只通过 computeIfAbsent 创建，无 remove/clear。deleteIndex 不清理。
- **建议**: deleteIndex 中增加 indexLocks.remove(indexId)。
- **复核状态**: 未复核

### [维度14-03] CodeCacheManager 内部方法无锁保护

- **严重程度**: P3
- **现状**: getValidEntry/getOrCreateEntry 不加锁，依赖调用者持锁。package-private 设计存在隐患。
- **建议**: 改为 private 或在方法内部加锁。
- **复核状态**: 未复核
