# 维度 03+12+13+14：API/GraphQL/安全/事务

**审计日期**: 2026-05-29

## 第 1 轮（初审）

### [维度12-01] detectFlows 标注为 @BizQuery 但内部执行写操作（P1）

- **文件**: `NopCodeIndexBizModel.java:179-182`, `CodeIndexService.java:2466-2481`
- **证据片段**:
  ```java
  @BizQuery  // 标记为只读查询
  public List<ExecutionFlow> detectFlows(@Name("indexId") String indexId) {
      return codeIndexService.detectFlows(indexId);
  }
  ```
  ```java
  // CodeIndexService.java — detectFlows 内部调用 persistFlows 写入数据库
  List<ExecutionFlow> flows = detector.detectFlows(indexId, symbolTable, callGraph);
  persistFlows(indexId, flows);  // 写入数据库！
  ```
- **严重程度**: P1
- **现状**: @BizQuery 方法内部调用 persistFlows，先删除旧 Flow 记录再插入新记录。违反 GraphQL query 不应有副作用的语义约定。
- **风险**: GraphQL 引擎可能对 query 做缓存/去重；权限审计中 query 通常不要求写权限。
- **建议**: 将 @BizQuery 改为 @BizMutation，或将检测和持久化拆分为两步。
- **信心水平**: 95%
- **误报排除**: 无合理误报理由。
- **复核状态**: 未复核

### [维度12-02] 多处全量查询未使用分页，存在 OOM 风险

- **文件**: `CodeIndexService.java:271-298,1277-1307,530-577`
- **证据片段**:
  ```java
  // L1277-1307 — 两次全量查询只为计数
  List<NopCodeSymbol> allSymbols = symbolDao.findAllByQuery(symbolQuery);
  stats.setSymbolCount(allSymbols.size());  // 应改用 countByQuery
  stats.setFileCount(fileDao.findAllByQuery(fileQuery).size());
  ```
- **严重程度**: P2
- **现状**: rebuildSymbolTable/rebuildCallGraph 加载全量数据到内存；getIndexStats 全量加载只为 size()；getModuleDigest 是 N+1 查询。
- **风险**: 大项目 OOM；数据库全表扫描；synchronized 块中执行阻塞其他线程。
- **建议**: getIndexStats 改用 countByQuery；rebuildSymbolTable 考虑流式加载。
- **信心水平**: 90%
- **误报排除**: 从支持三种语言的设计意图看目标规模不会很小。
- **复核状态**: 未复核

### [维度13-01] 路径遍历校验 validatePath 不完整

- **文件**: `CodeIndexService.java:2965-2970,333-334`
- **证据片段**:
  ```java
  private void validatePath(String path) {
      if (path == null || path.isEmpty())
          return;  // null/空路径直接放行
      if (path.contains(".."))
          throw new NopException(ERR_CODE_INVALID_PATH).param(ARG_PATH, path);
  }
  // L339 — new java.io.File(vfsPath) 直接使用用户输入
  ```
- **严重程度**: P1
- **现状**: 仅检查 ".."，null/空路径放行，未检查绝对路径白名单。indexDirectory 中 new File(vfsPath) 直接使用用户输入。
- **风险**: 路径遍历攻击可读取服务器任意文件。
- **建议**: null/空路径应抛异常；添加白名单机制限制 vfsPath。
- **信心水平**: 90%
- **误报排除**: 如果调用方已做输入校验风险降低，但 Service 层应自校验。
- **复核状态**: 未复核

### [维度13-02] 全模块零权限注解

- **文件**: 全部 BizModel 文件
- **严重程度**: P2
- **现状**: 42 个 @BizQuery/@BizMutation 方法均无权限注解。deleteIndex、triggerFullIndex 等破坏性操作无权限控制。
- **风险**: 任何已认证用户可删除索引、触发全量重索引。
- **建议**: 至少为破坏性操作添加权限注解。
- **信心水平**: 85%
- **误报排除**: 如果仅作为内部工具库被调用，影响较小。
- **复核状态**: 未复核

### [维度13-03] indexFile 接受用户提交的 sourceCode 无大小限制

- **文件**: `NopCodeIndexBizModel.java:89-96`
- **严重程度**: P2
- **现状**: sourceCode 参数无大小限制，ORM 层精度 512KB。
- **风险**: 恶意用户可提交超大 sourceCode 导致 OOM。
- **建议**: 添加长度校验。
- **信心水平**: 85%
- **误报排除**: GraphQL 层可能有全局请求体大小限制。
- **复核状态**: 未复核

### [维度14-01] deleteIndex 长事务 — 串行加载并删除 10 种实体

- **文件**: `CodeIndexService.java:1319-1404`
- **证据片段**:
  ```java
  ormTemplate.runInSession(session -> {
      // 10 次 findAllByQuery + batchDeleteEntities
  });
  ```
- **严重程度**: P2
- **现状**: 单个 session 中依次全量加载+删除 10 种实体，可能耗时数分钟。
- **风险**: 长事务持有连接；中途失败导致不一致；内存峰值高。
- **建议**: 使用 runInTransaction 或分步删除。
- **信心水平**: 88%
- **复核状态**: 未复核

### [维度14-02] indexDirectory 长事务 — 分析+持久化在一个 session 中

- **文件**: `CodeIndexService.java:333-361`
- **严重程度**: P2
- **现状**: analyzeProject（CPU密集）和 persistInSession 在同一 session 中，可能耗时数分钟。
- **风险**: 长时间持有数据库连接。
- **建议**: 将分析和持久化分离。
- **信心水平**: 92%
- **复核状态**: 未复核

### [维度14-03] synchronized 方法与数据库 I/O 交织

- **文件**: `CodeIndexService.java:300-328`
- **严重程度**: P2
- **现状**: getOrRebuildSymbolTable/getOrRebuildCallGraph 是 synchronized 方法，在持有锁时执行 DB 查询。
- **风险**: 不同 indexId 的查询被不必要串行化；DB 延迟放大锁持有时间。
- **建议**: 改为 per-indexId 锁或 ReadWriteLock。
- **信心水平**: 88%
- **复核状态**: 未复核

### [维度03-01] NopCodeIndexBizModel API 表面积过大（24个公开方法）

- **文件**: `NopCodeIndexBizModel.java:39-245`
- **严重程度**: P2
- **现状**: 承载了 5 个以上不同职责域的 24 个公开 API。
- **建议**: 按职责域拆分为多个 BizModel。
- **信心水平**: 85%
- **复核状态**: 未复核
