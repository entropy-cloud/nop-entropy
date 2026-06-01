# 维度12：GraphQL 与 API 层 -- nop-code 模块审计报告

## 第 1 轮（初审）

### [维度12-01] CodeCacheManager 分页加载未使用标准 doFindPage 模式

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeCacheManager.java:79-137`
- **证据片段**:
  ```java
  public SymbolTable rebuildSymbolTable(String indexId) {
      int offset = 0;
      while (true) {
          QueryBean query = new QueryBean();
          query.setOffset(offset);
          query.setLimit(BATCH_SIZE);
          List<NopCodeSymbol> batch = symbolDao.findAllByQuery(query);
          if (batch.isEmpty()) break;
          offset += BATCH_SIZE;
      }
  }
  ```
- **严重程度**: P3
- **现状**: 使用手动 offset 分页加载全部数据到内存缓存。受 `MAX_CACHE_SYMBOLS=100000` 限制，超过则返回不完整数据并仅打印 WARN 日志。
- **风险**: 大规模项目缓存可能不完整，且仅 WARN 不报错。
- **建议**: 超过限制时应抛出明确错误或返回截断标记。
- **信心水平**: 75%
- **误报排除**: 这是缓存层内部实现，不是 GraphQL API 层的问题。
- **复核状态**: 未复核

## 无问题确认

- **@BizQuery/@BizMutation 映射正确**: 全部注解使用正确。
- **无硬编码 SQL**: 搜索 `nativeQuery`、`executeSql` 等零命中。
- **分页查询使用 QueryBean**: `findPage_symbols` 和 `findPage_files` 正确使用标准模式。
