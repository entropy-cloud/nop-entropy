# 审核维度 14：异步与事务模式

## 第 1 轮（初审）

### [维度14-01] indexDirectory 在 ORM session 内执行全项目文件分析

- **文件**: `nop-code/nop-code-service/.../CodeIndexService.java:276-304`
- **证据片段**:
  ```java
  return ormTemplate.runInSession(session -> {
      ensureIndexEntity(indexId, vfsPath, session);
      ProjectAnalysisResult result = analyzer.analyzeProject(localFile.toPath());
      persistInSession(indexId, vfsPath, result, session);
      return result.getFileResults().size();
  });
  ```
- **严重程度**: P2
- **现状**: 在 ORM session（通常关联事务）内执行 CPU/IO 密集型的项目分析。
- **风险**: 事务持续数秒到数分钟，持有数据库连接，阻塞其他操作。
- **建议**: 将分析阶段移到事务外部，仅在持久化阶段开启事务。
- **信心水平**: 85%
- **误报排除**: 如果 Nop ORM 的 runInSession 不自动开启事务则不成立。
- **复核状态**: 未复核

### [维度14-02] triggerIncrementalIndex 同样在 ORM session 内执行增量分析

- **文件**: `nop-code/nop-code-service/.../CodeIndexService.java:639-719`
- **严重程度**: P2
- **现状**: 同上，增量索引的文件读取和解析在事务内完成。
- **建议**: 同上，将分析阶段移到事务外部。
- **复核状态**: 未复核

### [维度14-03] ConcurrentHashMap 与 synchronized 混用

- **文件**: `nop-code/nop-code-service/.../CodeCacheManager.java:38-68`
- **严重程度**: P3
- **现状**: 使用 ConcurrentHashMap 但所有方法都加了 synchronized，并发特性被完全覆盖。
- **建议**: 统一为 synchronized + HashMap 或移除 synchronized 用 ConcurrentHashMap 原子方法。
- **复核状态**: 未复核
