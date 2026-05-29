# 维度 03：API 表面积与契约一致性

## 第 1 轮（初审）

### [维度03-01] CodeIndexApi 接口使用 Map<String, Object> 作为请求/响应类型（死代码）

- **文件**: `nop-code/nop-code-api/src/main/java/io/nop/code/api/CodeIndexApi.java:14-28`
- **证据片段**:
  ```java
  @BizMutation
  ApiResponse<String> fullIndex(ApiRequest<Map<String, Object>> request);
  @BizQuery
  ApiResponse<List<Map<String, Object>>> searchCode(ApiRequest<Map<String, Object>> request);
  ```
- **严重程度**: P2
- **现状**: 5 个方法全部使用 Map<String, Object>。无实现类，是死代码。与 NopCodeIndexBizModel 中的强类型 DTO 方法形成两套并行 API 契约。
- **风险**: API 消费者无法获知请求/响应结构；编译器无法校验。
- **建议**: 删除或标注 @Deprecated。如保留应替换为已有 DTO 类型。
- **信心水平**: 95%
- **误报排除**: 与维度01-01同源问题（孤立模块），但此处关注类型安全。
- **复核状态**: 未复核

### [维度03-02] I*Biz 接口未声明 BizModel 中自定义的公开方法

- **文件**: `nop-code/nop-code-dao/src/main/java/io/nop/code/biz/INopCodeIndexBiz.java:全文`
- **证据片段**:
  ```java
  public interface INopCodeIndexBiz extends ICrudBiz<NopCodeIndex>{}
  // 但 NopCodeIndexBizModel 有 24 个自定义方法
  ```
- **严重程度**: P3
- **现状**: 3 个主接口均为空壳。48 个自定义方法游离于接口之外。Nop 平台中 I*Biz 是 CrudBizModel 泛型约束，GraphQL 通过反射发现方法，运行不受影响。
- **建议**: 平台惯例，可忽略。如有跨模块强类型需求再补充。
- **信心水平**: 90%
- **误报排除**: Nop 平台 I*Biz 接口主要作为 CrudBizModel 泛型约束，不是 bug。
- **复核状态**: 未复核

# 维度 12：GraphQL 与 API 层

## 第 1 轮（初审）

**零实质性发现**。GraphQL API 层合规：
- @BizQuery/@BizMutation 映射正确
- 分页查询未使用 doFindPage 有合理理由（查询目标是内存索引而非 ORM 实体）
- 无硬编码 SQL（零匹配）
- 无手动序列化绕过 GraphQL selection

# 维度 13：安全与权限模型

## 第 1 轮（初审）

### [维度13-01] 所有 BizModel 方法无权限注解，依赖粗粒度 action-auth 控制

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeIndexBizModel.java:全文`
- **证据片段**:
  ```java
  @BizMutation  // 无 @Auth 注解
  public String triggerFullIndex(...) { ... }
  @BizMutation  // 无 @Auth 注解
  public boolean deleteIndex(...) { ... }
  ```
- **严重程度**: P2
- **现状**: 所有方法均无方法级权限注解。action-auth.xml 仅提供 NopCodeIndex:query/mutation 两级。triggerFullIndex（计算密集）与 getStats（轻量）使用相同权限。
- **风险**: 低权限用户可能执行高成本/破坏性操作（全量重索引、删除索引）。
- **建议**: 对高影响操作添加方法级权限控制（至少区分读/管理/导出）。
- **信心水平**: 90%
- **误报排除**: 代码分析工具类模块，粗粒度权限在实际部署中可能可接受。
- **复核状态**: 未复核

### [维度13-02] 路径遍历防护仅覆盖 ".." 模式

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:2870-2875`
- **证据片段**:
  ```java
  private void validatePath(String path) {
      if (path.contains(".."))
          throw new NopException(ERR_CODE_INVALID_PATH).param(ARG_PATH, path);
  }
  ```
- **严重程度**: P3
- **现状**: 仅检查 ".." 子串，未检查 URL 编码绕过（如 %2e%2e）、符号链接等。但需要 mutation 权限且通常是管理员操作。
- **建议**: 使用 Path.of(path).normalize() 后再验证。
- **信心水平**: 85%
- **误报排除**: 代码索引服务通常不在公网暴露，实际被利用可能性低。
- **复核状态**: 未复核

**零发现确认**：无 SQL 注入风险（全部通过 QueryBean 参数化）。sourceCode 字段已正确设 published=false。

# 维度 14：异步与事务模式

## 第 1 轮（初审）

### [维度14-01] deleteIndex 在单 session 中加载并删除 11 类实体的全量数据

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:1300-1365`
- **证据片段**:
  ```java
  ormTemplate.runInSession(session -> {
      usageDao.batchDeleteEntities(usageDao.findAllByQuery(usageQuery));
      fmDao.batchDeleteEntities(fmDao.findAllByQuery(fmQuery));
      // ... 11 类实体全部加载+删除 ...
      daoProvider.daoFor(NopCodeIndex.class).deleteEntityById(indexId);
  });
  ```
- **严重程度**: P2
- **现状**: 单 runInSession 中 11 次 findAllByQuery + batchDeleteEntities。大型索引可能导致长事务/内存压力。
- **风险**: 数据库连接超时、锁超时或 OOM。
- **建议**: 分批删除，或使用 session.flush() + evictAll() 释放内存。
- **信心水平**: 90%
- **误报排除**: runInSession 不一定是事务，但全量加载删除确实构成性能风险。
- **复核状态**: 未复核

### [维度14-02] indexDirectory 全量索引在单 session 中持久化所有文件结果

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:332-358`
- **证据片段**:
  ```java
  return ormTemplate.runInSession(session -> {
      ProjectAnalysisResult result = analyzer.analyzeProject(localFile.toPath());
      persistInSession(indexId, vfsPath, result, session);
      return result.getFileResults().size();
  });
  ```
- **严重程度**: P2
- **现状**: 数千文件的分析和持久化在单个 session 中。每个文件创建 7 类 ORM 实体。
- **风险**: 大型项目可能内存压力。
- **建议**: 每隔一定数量文件执行 session.flush() + evictAll()。
- **信心水平**: 85%
- **误报排除**: saveReplacingExisting 中有部分 flush，但不够系统化。
- **复核状态**: 未复核
