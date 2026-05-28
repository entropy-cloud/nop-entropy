# 维度 13：安全与权限模型

## 第 1 轮（初审）

### [维度13-01] LocalFileCheckpointStorage 路径遍历漏洞 -- jobId/pipelineId/savepointPath 未净化

- **文件**: `nop-stream-runtime/.../checkpoint/storage/LocalFileCheckpointStorage.java:233-239,389-391,420-421`
- **证据片段**:
  ```java
  // L233-235
  private Path getJobDir(String jobId, String pipelineId) {
      return Paths.get(baseDir, jobId, pipelineId);
  }
  // L389-391
  public String storeSavepoint(CompletedCheckpoint checkpoint, String targetPath) throws Exception {
      Path savepointDir = Paths.get(targetPath, savepointDirName);
  // L420-421
  public CompletedCheckpoint loadSavepoint(String savepointPath) throws Exception {
      Path dir = Paths.get(savepointPath);
  ```
- **严重程度**: P1
- **现状**: jobId、pipelineId、targetPath、savepointPath 全部直接传入 Paths.get() 构建文件系统路径，无任何净化或规范化处理。也未检查最终路径是否在 baseDir 范围内。
- **风险**: 若 jobId 包含 `../` 或 savepointPath 为 `/etc/passwd`，可读写 baseDir 之外的任意文件。savepointPath 是 ICheckpointStorage 公开接口参数，直接来自上层调用者。
- **建议**: (1) 构造函数中保存 baseDir 的规范化绝对路径 (2) 所有路径构建后做 toAbsolutePath().normalize() + startsWith(baseDir) 校验 (3) 对 jobId/pipelineId 增加正则校验 `[a-zA-Z0-9_-]+`
- **误报排除**: 不是误报。savepointPath 通过 ICheckpointStorage 公开接口传入，任何调用者都可能传入不可信值。
- **复核状态**: 未复核

### [维度13-02] JdbcCheckpointStorage 反序列化枚举值未做异常处理

- **文件**: `nop-stream-runtime/.../checkpoint/storage/JdbcCheckpointStorage.java:386,635-638`
- **证据片段**:
  ```java
  // L386
  CheckpointType checkpointType = checkpointTypeName != null
      ? CheckpointType.valueOf(checkpointTypeName) : CheckpointType.CHECKPOINT;
  // L637-638
  EpochState epochState = stateName != null ? EpochState.valueOf(stateName) : null;
  ```
- **严重程度**: P2
- **现状**: 从数据库 BLOB 反序列化 JSON 后，直接调用 valueOf() 解析枚举。若数据库数据被篡改，valueOf() 抛出未捕获的 IllegalArgumentException。
- **风险**: 攻击者若获得数据库写权限，可构造非法枚举值导致作业永久无法启动。
- **建议**: 包裹 try-catch，捕获 IllegalArgumentException 后回退到默认值并记录 WARN 日志。LocalFileCheckpointStorage 也有相同问题。
- **误报排除**: 不是误报。持久化数据的完整性不应假设为可信。
- **复核状态**: 未复核

### [维度13-03] LocalFileCheckpointStorage storeCheckPoint finally 块中 deleteIfExists 在锁外执行

- **文件**: `nop-stream-runtime/.../checkpoint/storage/LocalFileCheckpointStorage.java:59-73`
- **证据片段**:
  ```java
  lock.writeLock().lock();
  try {
      Files.write(tempPath, data, ...);
      Files.move(tempPath, checkpointPath, StandardCopyOption.ATOMIC_MOVE, ...);
      return checkpointPath.toString();
  } finally {
      lock.writeLock().unlock();
      deleteIfExists(tempPath);  // 在锁释放后执行
  }
  ```
- **严重程度**: P2
- **现状**: deleteIfExists 在 finally 块中锁释放后执行，存在微小的时间窗口竞争。
- **风险**: 如果 ATOMIC_MOVE 不被底层文件系统支持，临时文件可能残留。
- **建议**: 将 deleteIfExists 移到 finally 块内（锁释放前），或捕获 AtomicMoveNotSupportedException 回退到 REPLACE_EXISTING。
- **误报排除**: 低风险但属于资源管理最佳实践违规。
- **复核状态**: 未复核

### [维度13-04] JdbcCheckpointStorage sidSequence 多实例不保证全局唯一

- **文件**: `nop-stream-runtime/.../checkpoint/storage/JdbcCheckpointStorage.java:37,692-694`
- **证据片段**:
  ```java
  private static long sidSequence = System.currentTimeMillis();
  private static synchronized long nextSid() { return ++sidSequence; }
  ```
- **严重程度**: P3
- **现状**: synchronized 只保护单 JVM 进程内的线程安全。多 JVM 实例共享数据库时可能产生相同 SID。
- **风险**: 分布式部署时可能导致主键冲突，checkpoint 保存失败。
- **建议**: 使用数据库自增主键或 UUID。
- **误报排除**: 不是误报。多实例部署是流处理引擎的常见场景。
- **复核状态**: 未复核

## 已验证安全项

- **SQL 注入风险为零**：所有 DML 使用 SQL.sql(text, params...) 参数化查询。DDL 表名为编译时常量。
- **资源管理整体良好**：JDBC 连接由 IJdbcTemplate 管理，文件流全部使用 try-with-resources。
- **并发安全整体合理**：CheckpointCoordinator 使用 ConcurrentHashMap + volatile + AtomicInteger；LocalFileCheckpointStorage 使用 ReentrantReadWriteLock。
- **无敏感信息处理问题**：fencing token 使用 UUID，无密码或密钥存储。
