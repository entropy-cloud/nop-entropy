# 维度 13：安全与权限模型

## 第 1 轮（初审）

### [维度13-01] Fencing Token 在 INFO 日志中明文输出（5 处）

- **文件**: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/coordinator/JobCoordinator.java:146-147` 及 `TaskManager.java:349` 及 `EmbeddedDistributedExecutor.java:66` 等
- **证据片段**:
  ```java
  // JobCoordinator.java:146-147
  LOG.info("JobCoordinator {} started for job {} with fencing token {}",
          coordinatorId, jobId, token);
  // TaskManager.java:349
  LOG.info("Fencing token updated from {} to {}. Canceling old tasks.", oldToken, newToken);
  ```
- **严重程度**: P2
- **现状**: Fencing token 是分布式协调的安全凭证，用于防止 stale leader 干扰。在 INFO 级别明文输出。
- **风险**: 任何有日志访问权限的人都可以获取当前 fencing token，在特定场景下可用于伪造控制消息。
- **建议**: 将 fencing token 输出降为 DEBUG 级别，或仅输出 token 的前几位（如 `token.substring(0,8)***`）。
- **信心水平**: 很可能
- **误报排除**: 不是误报——fencing token 是安全敏感信息，INFO 级别日志通常会被收集到集中日志系统。
- **复核状态**: 未复核

### [维度13-02] JdbcCheckpointStorage 的 upsert 使用异常驱动控制流

- **文件**: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/JdbcCheckpointStorage.java:88-108`
- **证据片段**:
  ```java
  try {
      jdbcTemplate.executeUpdate(sql);  // INSERT
  } catch (Exception e) {
      LOG.debug("INSERT failed, attempting UPDATE...", e);
      SQL updateSql = SQL.begin().name("updateCheckpoint")...  // 参数化 SQL
      jdbcTemplate.executeUpdate(updateSql);
  }
  ```
- **严重程度**: P3
- **现状**: 先 INSERT 失败后 catch 异常再 UPDATE。SQL 全部参数化，无注入风险。但异常驱动控制流可能导致主键冲突和真正错误被同等处理。
- **风险**: 部分数据库上唯一约束冲突会导致事务标记为 rollback-only。
- **建议**: 改为先查询后决定 INSERT/UPDATE，或使用数据库原生 MERGE/UPSERT。
- **信心水平**: 确定
- **误报排除**: 不是误报，但无安全风险（SQL 参数化到位）。
- **复核状态**: 未复核

## 安全合规亮点

- SQL 注入防护到位：JdbcCheckpointStorage 和 JdbcClusterRegistry 全部使用参数化 SQL
- LocalFileCheckpointStorage 有路径遍历防护（正则校验 + canonical path 检查）
