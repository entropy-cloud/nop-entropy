# 维度 14：异步与事务模式

## 第 1 轮（初审）

### 检查范围

- txn() 使用：9 处，全部集中在 JdbcCheckpointStorage（5 处）和 JdbcClusterRegistry（4 处）
- CheckpointCoordinator 并发安全
- TaskManager/JobCoordinator 竞态条件
- 资源泄漏

### 结论：无发现

| 检查项 | 结果 |
|--------|------|
| txn() 使用 | 仅限 JDBC 持久化层，事务传播 REQUIRED，语义正确 |
| PendingCheckpoint 并发 | AtomicReference CAS + ConcurrentHashMap + synchronized，设计良好 |
| CheckpointCoordinator 线程安全 | ConcurrentHashMap + AtomicInteger + volatile + CAS 原子状态转换 |
| TaskManager 竞态 | Semaphore 控制并发 + putIfAbsent + AtomicBoolean 防重复 release |
| JobCoordinator 竞态 | AtomicBoolean 防双重初始化 + AtomicReference fencing token + ConcurrentHashMap |
| 线程池关闭 | 均有 shutdownNow + awaitTermination |
| 文件资源 | LocalFileCheckpointStorage 全部使用 try-with-resources |
| 线程池大小 | 均有上界，无 newCachedThreadPool |

整体评价：异步/事务模式设计成熟，并发安全防护系统性。
