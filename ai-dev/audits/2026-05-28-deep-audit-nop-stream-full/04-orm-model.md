# 维度 04：ORM 模型与实体设计

## 第 1 轮（初审）

nop-stream 模块不含 ORM 模型定义文件（*.orm.xml），无 ORM 实体类。使用原始 JDBC（IJdbcTemplate + SQL）进行数据访问是合理的设计选择。

对 JDBC 数据访问代码进行了审计，发现以下问题：

### [维度04-01] Missing UNIQUE constraint on checkpoint natural key allows duplicate records

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/JdbcCheckpointStorage.java:288-300,59-84`
- **证据片段**:
  ```java
  String ddl = "CREATE TABLE " + TABLE_NAME + " (" +
          "sid BIGINT NOT NULL, " +
          "job_id VARCHAR(255) NOT NULL, " +
          "pipeline_id VARCHAR(255) NOT NULL, " +
          "checkpoint_id BIGINT NOT NULL, " +
          // ...
          "PRIMARY KEY (sid)" +
          ")";
  ```
- **严重程度**: P2
- **现状**: stream_checkpoint 表仅以 sid 自增 ID 为主键，无 (job_id, pipeline_id, checkpoint_id) 的唯一约束。storeCheckPoint() 是纯 INSERT 操作，不做重复检查。stream_epoch_manifest 表同理。
- **风险**: 重试、网络超时、协调器故障转移等场景下会产生重复记录。getAllCheckpoints() 返回重复数据，getCheckpointCount() 计数偏高，存储空间无上限增长。
- **建议**: 在 DDL 中添加 UNIQUE (job_id, pipeline_id, checkpoint_id)，并将 storeCheckPoint() 改为 upsert 模式。
- **误报排除**: 不是设计决策。checkpoint 的 natural key 是明确的业务唯一标识，缺少唯一约束是数据模型缺陷。
- **复核状态**: 未复核

### [维度04-02] Inefficient exists() default implementation not overridden

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/JdbcCheckpointStorage.java`
- **证据片段**:
  ```java
  // ICheckpointStorage.java:32-41 (interface default)
  default boolean exists(String jobId, String pipelineId, long checkpointId) throws Exception {
      List<CompletedCheckpoint> checkpoints = getAllCheckpoints(jobId);
      for (CompletedCheckpoint checkpoint : checkpoints) {
          if (pipelineId.equals(checkpoint.getPipelineId())
                  && checkpoint.getCheckpointId() == checkpointId) {
              return true;
          }
      }
      return false;
  }
  ```
- **严重程度**: P3
- **现状**: JdbcCheckpointStorage 未重写 exists() 方法。默认实现加载并反序列化该 job 的所有 checkpoint，仅在内存中逐条检查存在性。
- **风险**: 随存储的 checkpoint 数量增长，性能线性下降。对简单存在性检查而言开销过大。
- **建议**: 在 JdbcCheckpointStorage 中重写 exists()，使用 SELECT COUNT(*) ... WHERE job_id=? AND pipeline_id=? AND checkpoint_id=? 直接查询。
- **误报排除**: 不是性能微调建议。默认实现执行全量反序列化以做存在性判断，是结构性性能问题。
- **复核状态**: 未复核

### [维度04-03] registerNode() has TOCTOU race condition

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/cluster/JdbcClusterRegistry.java:90-121`
- **证据片段**:
  ```java
  SQL existsSql = SQL.begin().name("nodeExists").querySpace(querySpace)
          .sql("SELECT 1 FROM " + NODE_TABLE + " WHERE node_id = ?", nodeId)
          .end();
  boolean exists = jdbcTemplate.exists(existsSql);
  if (exists) {
      jdbcTemplate.executeUpdate(updateSql);
  } else {
      jdbcTemplate.executeUpdate(insertSql);
  }
  ```
- **严重程度**: P2
- **现状**: registerNode() 先 SELECT 检查存在性，再条件 INSERT/UPDATE，但不在事务中执行。并发注册同一 node_id 时，两个线程可能同时观察到 exists=false 并都执行 INSERT，导致主键冲突。
- **风险**: 并发场景下产生未处理的 SQL 异常，可能导致节点注册失败。而同类中 registerCoordinator() 和 assignTask() 正确使用了事务模式。
- **建议**: 使用 INSERT ... ON DUPLICATE KEY UPDATE（MySQL），或将 SELECT+INSERT/UPDATE 包裹在事务中。
- **误报排除**: 不是过度设计要求。同类方法 registerCoordinator() 和 assignTask() 已正确使用事务模式，此处是遗漏。
- **复核状态**: 未复核

### [维度04-04] sidSequence is not collision-safe across JVM instances

- **文件**: `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/JdbcCheckpointStorage.java:37`
- **证据片段**:
  ```java
  private static long sidSequence = System.currentTimeMillis();
  private static synchronized long nextSid() {
      return ++sidSequence;
  }
  ```
- **严重程度**: P3
- **现状**: 主键值基于 System.currentTimeMillis() 起始自增。多 JVM 实例（水平扩展）在同一毫秒启动时将生成相同的初始 sid 值，导致后续值冲突。
- **风险**: 水平扩展 checkpoint coordinator 时，INSERT 会因主键冲突失败。
- **建议**: 使用数据库自增 ID 或在 sid 中包含 JVM 唯一前缀/UUID。
- **误报排除**: 水平扩展是合理的部署场景。synchronized 仅防止单 JVM 内冲突。
- **复核状态**: 未复核
