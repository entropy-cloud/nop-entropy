# Audit Dimension 14: Async and Transaction Patterns — nop-code

### [14-01] deleteIndex Loads All Entities Into Memory Before Deleting

- **File**: `nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:1348-1433`
- **Evidence Snippet**:
```java
usageDao.batchDeleteEntities(usageDao.findAllByQuery(usageQuery));
// 重复10次，每种实体类型全部加载到内存
```
- **Severity**: P1
- **Current State**: 对10种实体类型分别 findAllByQuery 加载全部数据到内存，再 batchDeleteEntities。大型索引（100K+ 符号）会导致 OOM。
- **Risk**: OutOfMemoryError 或极长 GC 暂停。
- **Recommendation**: 使用批量删除查询（DELETE FROM ... WHERE indexId = ?）或分页删除。
- **Confidence**: High
- **False Positive Exclusion**: findAllByQuery 明确加载全部行到内存。
- **Review Status**: Not reviewed

---

### [14-02] Global synchronized on indexDirectory Creates Contention

- **File**: `nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:352`
- **Evidence Snippet**:
```java
public synchronized int indexDirectory(String indexId, String vfsPath, String filePattern) {
```
- **Severity**: P1
- **Current State**: synchronized 在整个 CodeIndexService 实例上，所有索引操作（跨所有 indexId）被串行化。大型项目索引可能持续数分钟，期间所有代码智能查询被阻塞。
- **Risk**: 索引大型项目期间阻塞整个平台的所有代码查询；ORM session 持有数分钟可能导致连接池耗尽。
- **Recommendation**: 改用 per-indexId 锁（ConcurrentHashMap<String, Lock>）。
- **Confidence**: High
- **False Positive Exclusion**: 单例 bean 上的 synchronized 是全局锁，可观测的延迟。
- **Review Status**: Not reviewed

---

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 摘要 |
|------|---------|---------|------|
| 14-01 | P1 | CodeIndexService.java | deleteIndex 全量加载到内存再删除 |
| 14-02 | P1 | CodeIndexService.java | 全局 synchronized 导致串行阻塞 |
