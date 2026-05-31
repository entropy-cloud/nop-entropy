# 维度 03：API 表面积 + 维度 12：GraphQL 与 API 层 + 维度 13：安全与权限 + 维度 14：异步与事务

## 第 1 轮（初审）

### [维度13-01] detectFlows @BizMutation 无 @Auth 权限注解

- **文件**: `NopCodeIndexBizModel.java:190-193`
- **证据片段**:
  ```java
  @BizMutation
  public List<ExecutionFlow> detectFlows(@Name("indexId") String indexId) {
      return codeIndexService.detectFlows(indexId);
  }
  ```
- **严重程度**: P1
- **现状**: detectFlows 是 @BizMutation（会持久化数据），但无 @Auth 注解。同类写操作都标注了 @Auth(roles = "admin")。
- **风险**: 任何已登录用户可触发计算密集且写入数据的操作。
- **建议**: 添加 @Auth(roles = "admin") 或至少 @Auth(permissions = "code-flow-write")。
- **信心水平**: 确定
- **误报排除**: 同类的 triggerFullIndex 等都有 @Auth。
- **复核状态**: 未复核

### [维度13-02] 18 个 @BizQuery 无权限控制，含计算密集型操作

- **文件**: `NopCodeIndexBizModel.java`（全文 18 个 @BizQuery 方法）
- **证据片段**: detectCommunities(), findCycles(), detectDeadCode() 等计算密集型操作无 @Auth。
- **严重程度**: P1
- **现状**: 仅 5 个方法有 @Auth 注解，其余 18 个 @BizQuery 均无权限控制。
- **风险**: 计算密集型操作可能被滥用导致拒绝服务。
- **建议**: 至少为计算密集型操作添加权限控制。
- **信心水平**: 确定
- **误报排除**: nop-code 可能是内部工具，但权限防御是平台标准要求。
- **复核状态**: 未复核

### [维度14-01] indexDirectory 长事务/长 session 风险

- **文件**: `CodeIndexService.java:270-305`
- **证据片段**:
  ```java
  return ormTemplate.runInSession(session -> {
      ensureIndexEntity(indexId, vfsPath, session);
      // ... 完整项目分析 + 持久化 ...
  });
  ```
- **严重程度**: P2
- **现状**: 大型项目一个 session 跨越数万条记录插入，无事务超时设置。
- **风险**: Session 持续时间可能以分钟计，可能耗尽数据库连接或超时。
- **建议**: 将大项目分批处理，每批独立 session，或添加事务超时。
- **信心水平**: 很可能
- **误报排除**: 内部已用 BatchQueue 做分批 flush+evict，但 session 生命周期仍与索引持续时间相同。
- **复核状态**: 未复核

### [维度14-02] triggerIncrementalIndex 两段 session 间可能不一致

- **文件**: `CodeIndexService.java:632-739`
- **证据片段**: 第一个 runInSession 加载旧指纹，session 外做文件 I/O，第二个 runInSession 删除旧记录+持久化新结果。
- **严重程度**: P2
- **现状**: 两次 session 间如果崩溃，数据可能不一致。
- **建议**: 考虑在第二个 session 中使用事务保证原子性。
- **信心水平**: 很可能
- **误报排除**: 增量索引场景下崩溃恢复后重新运行即可，影响有限。
- **复核状态**: 未复核

### [维度12-01] 分页查询绕过 doFindPage，手工拼装 PageBean

- **文件**: `NopCodeSymbolBizModel.java:64-95`, `NopCodeFileBizModel.java:41-48`
- **证据片段**: 手工接受 query, kinds, packageName, offset, limit 参数，非标准 QueryBean。
- **严重程度**: P2
- **现状**: 绕过标准 QueryBean + doFindPage 机制。
- **建议**: 见维度07-07，索引范围查询确实不适合标准 CRUD。
- **信心水平**: 很可能
- **误报排除**: 与维度07-07重复发现。
- **复核状态**: 未复核

### [维度03-01] I*Biz 接口为空壳，BizModel 自定义方法未在接口中声明

- **文件**: `INopCodeIndexBiz.java`, `INopCodeSymbolBiz.java`, `INopCodeFileBiz.java`
- **证据片段**: 三个 I*Biz 接口均仅继承 ICrudBiz<Entity>，零自定义方法。但 BizModel 有 10-25 个公开方法。
- **严重程度**: P2
- **现状**: 接口完全无法表达实际 API 表面积。
- **建议**: 将 BizModel 自定义公开方法签名同步到 I*Biz 接口。
- **信心水平**: 确定
- **误报排除**: Nop 平台 GraphQL 引擎通过反射调用，不强制要求接口包含所有方法，但接口作为契约应保持同步。
- **复核状态**: 未复核
