# 维度07：BizModel 规范遵循

## 第 1 轮（初审）

### [维度07-01] NopCodeIndexBizModel.incrementalStatusMap 使用内存 ConcurrentHashMap，进程重启后状态丢失

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeIndexBizModel.java`
- **行号**: L33
- **证据片段**:
  ```java
  private final Map<String, IncrementalStatus> incrementalStatusMap = new java.util.concurrent.ConcurrentHashMap<>();
  ```
- **严重程度**: P3
- **现状**: triggerFullIndex() 和 triggerIncrementalIndex() 将状态写入内存 Map。getIncrementalStatus() 从内存 Map 读取。状态随 JVM 重启消失，多实例部署时各实例状态不一致。
- **风险**: getIncrementalStatus() 永远返回 null（重启后），调用方可能误判索引尚未完成。不影响核心业务逻辑。
- **建议**: 将 IncrementalStatus 持久化到 NopCodeIndex 实体的扩展字段中。优先级较低。
- **信心水平**: 确定
- **误报排除**: 这是一个辅助诊断功能，核心索引操作（触发、执行、结果存储）已正确持久化。
- **复核状态**: 未复核

### [维度07-02] CodeIndexService.saveFileResultInSession 方法约 260 行，职责过多

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java`
- **行号**: L2041-2299
- **证据片段**:
  ```java
  private void saveFileResultInSession(CodeFileAnalysisResult result, String indexId,
      IEntityDao<NopCodeIndex> indexDao, Set<String> existingFileHashes, ...) {
      // 保存 File 实体 (~30 行)
      // 保存 Symbol 实体 + 搜索引擎同步 (~90 行)
      // 保存 Call (~30 行)
      // 保存 Inheritance (~20 行)
      // 保存 AnnotationUsage (~20 行)
      // 保存 Usage 三种来源 (~50 行)
      // 依赖解析 (~20 行)
  }
  ```
- **严重程度**: P2
- **现状**: saveFileResultInSession() 包含 7-8 个独立职责段。搜索引擎同步逻辑嵌入在 ORM 持久化逻辑中。
- **风险**: 可读性差，难以 review 和测试。搜索引擎同步逻辑嵌入在 ORM 持久化中违反单一职责。
- **建议**: 拆分为多个独立方法：persistFileEntity()、persistSymbols()、persistCalls() 等。
- **信心水平**: 确定
- **误报排除**: 不是风格偏好。260 行方法包含 7-8 个独立职责段是客观的结构问题。
- **复核状态**: 未复核

### [维度07-03] CodeIndexService.deleteIndex() 手动逐表删除，未利用 ORM cascadeDelete

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java`
- **行号**: L1321-1406
- **证据片段**:
  ```java
  // 手动逐表删除约 85 行：
  List<NopCodeUsage> usages = usageDao.findAllByQuery(query);
  usageDao.batchDeleteEntities(usages);
  usageDao.flush(); usageDao.evictAll();
  // ... 重复 10 个表
  ```
- **严重程度**: P2
- **现状**: ORM 模型中 NopCodeIndex 已定义了所有关联子表的 cascadeDelete="true"。手动逐表删除约 85 行做 ORM 已声明的事情。
- **风险**: (1) 代码冗余；(2) 每次 flush+evictAll 的性能开销；(3) 如果 ORM 模型增加新关联实体，此方法必须同步修改。
- **建议**: 改为利用 ORM 级联删除：daoProvider.daoFor(NopCodeIndex.class).deleteEntityById(indexId)。
- **信心水平**: 很可能
- **误报排除**: 如果 ORM 级联删除在 session 缓存场景下有性能问题（大量逐条删除），手动批量删除可能是刻意的性能优化。但 85 行冗余代码仍需更好的抽象。
- **复核状态**: 未复核

### [维度07-04] CodeIndexService 中多处 findAllByQuery 加载全量数据到内存

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java`
- **行号**: L276, L291, L539, L905, L1289, L2800
- **证据片段**:
  ```java
  List<NopCodeSymbol> symbols = symbolDao.findAllByQuery(query); // 加载全部 Symbol
  List<NopCodeCall> calls = callDao.findAllByQuery(query); // 加载全部 Call
  ```
- **严重程度**: P3
- **现状**: 以下方法加载全量数据到内存：rebuildSymbolTable()、rebuildCallGraph()、getModuleDigest()、buildFilePathCache()、getIndexStats()、findImplementations()。
- **风险**: 大型代码库索引可能包含数十万条 Symbol/Call 记录，全部加载有 OOM 风险。
- **建议**: 统计查询使用 countByQuery() 或数据库聚合；大数据量操作使用分页或流式处理。
- **信心水平**: 很可能
- **误报排除**: 目前仅在分析超大型项目时可能触发。这是性能优化项，非功能性缺陷。
- **复核状态**: 未复核

### [维度07-05] NopCodeSymbolBizModel.findPage_symbols 方法未使用 QueryBean 分页模式

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeSymbolBizModel.java`
- **行号**: L63-94
- **证据片段**:
  ```java
  @BizQuery
  public PageBean<SymbolDTO> findPage_symbols(
      @Name("query") @Optional String query,
      @Name("kinds") @Optional String kinds,
      @Name("packageName") @Optional String packageName,
      @Name("indexId") String indexId,
      @Name("offset") @Optional Integer offset,
      @Name("limit") @Optional Integer limit) {
  ```
- **严重程度**: P3
- **现状**: 使用多个 @Name 参数手动构造查询条件，而非平台标准的 @RequestBean QueryBean 模式。
- **风险**: API 签名较长，增加参数时需修改方法签名。
- **建议**: 考虑此模块的特殊性（跨维度组合查询），不使用 QueryBean 是合理的。此处为风格观察。
- **信心水平**: 很可能
- **误报排除**: 考虑到 CodeIndexService 的特殊查询需求，手动参数传递更灵活。且平台不强制要求所有分页都用 QueryBean。
- **复核状态**: 未复核

## 总结

**整体评价**: nop-code 模块的 BizModel 层实现质量良好。

- 11/11 BizModel 全部通过基础合规检查（继承 CrudBizModel、setEntityName、Biz 接口）
- 无 Map<String, Object> 反模式
- 错误处理使用 NopException + ErrorCode
- CodeIndexService 核心方法偏大（P2），deleteIndex() 未利用 ORM 级联（P2）
- 无跨聚合根问题，无 Processor 滥用
