# 维度03：API 表面积 / 维度12：GraphQL / 维度13：安全 / 维度14：异步事务

**模块**: nop-code
**审计日期**: 2026-05-31

---

## 维度03：API 表面积与契约一致性

### [维度03-01] ICodeIndexService 接口过度膨胀

- **文件**: `nop-code-service/src/main/java/io/nop/code/service/api/ICodeIndexService.java:31-156`
- **证据片段**:
  ```java
  void batchSaveFileRecords(String indexId, List<FileFingerprint> fingerprints);
  List<FileFingerprint> batchLoadFileRecords(String indexId);
  void batchDeleteFileRecords(String indexId, List<String> filePaths);
  ```
- **严重程度**: P3
- **现状**: 公开服务接口包含 8+ 个仅在测试/内部调用的方法。
- **建议**: 拆分内部方法到包级可见性。
- **信心水平**: 高
- **误报排除**: 接口契约膨胀是可量化问题。
- **复核状态**: 未复核

---

## 维度12：GraphQL 与 API 层

### [维度12-01] getStats 全量加载符号表用于计数

- **文件**: `CodeIndexService.java:478-490`
- **证据片段**:
  ```java
  List<NopCodeSymbol> allSymbols = symbolDao.findAllByQuery(symbolQuery);
  stats.setSymbolCount(allSymbols.size());
  ```
- **严重程度**: P2
- **现状**: 加载该 index 下全部 NopCodeSymbol 到内存仅为了计数。大项目可达数万条。
- **风险**: 大索引下 OOM 或响应极慢。
- **建议**: 使用 COUNT 聚合查询。
- **信心水平**: 高
- **误报排除**: 通过 GraphQL API 触发，可在页面刷新时调用。
- **复核状态**: 未复核

---

### [维度12-02] getFiles/getFileTree 全量加载含 sourceCode

- **文件**: `CodeQueryService.java:115-124,195-196`
- **证据片段**:
  ```java
  return fileDao.findAllByQuery(query).stream()
          .map(this::entityToFileResult)
          .collect(Collectors.toList());
  ```
- **严重程度**: P2
- **现状**: 每条记录加载 sourceCode 字段（可能很大），有 10000 硬上限。
- **风险**: 大项目返回 10000 条含 sourceCode 的对象，内存消耗严重。
- **建议**: entityToFileResult 不应加载 sourceCode。
- **信心水平**: 高
- **误报排除**: getFileTree 通过 BizModel 暴露给 UI。
- **复核状态**: 未复核

---

## 维度13：安全与权限模型

### [维度13-01] detectFlows 缺少 @Auth（维度07已报）

- **文件**: `NopCodeIndexBizModel.java:190-193`
- **严重程度**: P1
- **现状**: 所有其他 @BizMutation 有 @Auth(roles="admin")，唯独此方法缺失。
- **建议**: 添加 @Auth(roles="admin")。
- **复核状态**: 未复核（与维度07-01重复）

---

### [维度13-02] NopCodeFileBizModel.getByPath 无 @Auth 但返回含 sourceCode 的对象

- **文件**: `NopCodeFileBizModel.java:35-39`
- **证据片段**:
  ```java
  @BizQuery
  public CodeFileAnalysisResult getByPath(...) {
      return codeIndexService.getFile(indexId, filePath);
  }
  ```
- **严重程度**: P2
- **现状**: 无 @Auth 注解。GraphQL 层 BizLoader 有权限控制，但 Java 直接调用绕过。
- **建议**: 添加 @Auth(permissions = "code-source-read")。
- **信心水平**: 中
- **误报排除**: xmeta 中 sourceCode 已设 published=false。
- **复核状态**: 未复核

---

### [维度13-03] validatePath 仅检查 ".." 子串

- **文件**: `CodeIndexService.java:1545-1550`
- **证据片段**:
  ```java
  if (path.contains(".."))
      throw new NopException(ERR_CODE_INVALID_PATH).param(ARG_PATH, path);
  ```
- **严重程度**: P3
- **现状**: 不防御 URL 编码等。VFS 路径穿越风险低（VFS 有路径规范化）。本地路径由 validateLocalPath 额外保护。
- **建议**: 考虑使用 VFS 层的路径规范化 API。
- **信心水平**: 高
- **误报排除**: validateLocalPath 已用 canonical path。
- **复核状态**: 未复核

---

## 维度14：异步与事务模式

### [维度14-01] indexDirectory 长事务 + 长锁

- **文件**: `CodeIndexService.java:270-305`
- **证据片段**:
  ```java
  lock.lock();
  try {
      return ormTemplate.runInSession(session -> {
          ProjectAnalysisResult result = analyzer.analyzeProject(localFile.toPath());
          persistInSession(indexId, vfsPath, result, session);
      });
  }
  ```
- **严重程度**: P2
- **现状**: analyzeProject（纯计算）在锁+session 内执行，大项目可能持续数十秒。
- **风险**: 长事务可能超时回滚。
- **建议**: 将 analyzeProject 移到锁/session 外。
- **信心水平**: 高
- **误报排除**: BatchQueue 做了周期性 flush+evict 缓解内存膨胀。
- **复核状态**: 未复核

---

### [维度14-02] CodeCacheManager 粗粒度 synchronized 阻塞跨索引

- **文件**: `CodeCacheManager.java:40-66`
- **证据片段**:
  ```java
  synchronized SymbolTable getOrRebuildSymbolTable(String indexId, ...) { ... }
  ```
- **严重程度**: P2
- **现状**: 实例级 synchronized，线程 A 为 index-1 重建时阻塞 index-2 的查询。
- **建议**: 改为 per-indexId 锁（ConcurrentHashMap<String, ReentrantLock>）。
- **信心水平**: 高
- **误报排除**: 单索引场景无影响。
- **复核状态**: 未复核
