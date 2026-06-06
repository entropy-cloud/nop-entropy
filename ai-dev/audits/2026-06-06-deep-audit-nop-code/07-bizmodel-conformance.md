# 维度 07：BizModel 规范遵循

## 第 1 轮（初审）

### [维度07-01] allowedLocalRoot 未配置时路径校验形同虚设

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java`
- **行号**: 1934-1957（校验方法），332-348（调用点）
- **证据片段**:
  ```java
  private void validatePath(String path) {
      if (path.contains(".."))
          throw new NopException(ERR_CODE_INVALID_PATH).param(ARG_PATH, path);
  }
  private void validateLocalPath(String path) {
      // ... 仅在 allowedLocalRoot != null 时做规范路径校验
  }
  ```
- **严重程度**: P2
- **现状**: validatePath 只检查 ".." 子串；validateLocalPath 在 allowedLocalRoot 为 null 时完全跳过。
- **风险**: admin 可通过 GraphQL 触发对服务器任意目录的递归索引。
- **建议**: 启动时检查 allowedLocalRoot 配置，未配置时打 WARN 日志。
- **信心水平**: 确定
- **误报排除**: 不是审美问题——防御机制在默认配置下等于不存在。
- **复核状态**: 未复核

### [维度07-02] IncrementalStatus.errorMessage 从未被设置

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeIndexBizModel.java`
- **行号**: 39-67（状态 Map 和更新），283-339（IncrementalStatus 定义）
- **证据片段**:
  ```java
  IncrementalStatus status = new IncrementalStatus();
  status.setCompleted(true);  // 只有 true，从未设为 false
  // errorMessage 从未被设置
  incrementalStatusMap.put(indexId, status);
  ```
- **严重程度**: P2
- **现状**: errorMessage 字段通过 @DataBean 暴露到 GraphQL schema，但永远是 null。
- **风险**: API 契约漂移；无法区分"未开始"和"失败"。
- **建议**: 添加 try-catch，失败时设置 completed=false 和 errorMessage。
- **信心水平**: 确定
- **误报排除**: errorMessage 字段存在说明设计意图是跟踪错误状态，但实现不一致。
- **复核状态**: 未复核

### [维度07-03] NopCodeException 已定义但从未使用

- **严重程度**: P3
- **现状**: 模块级异常类存在但全模块零实例化，所有异常直接用 NopException。
- **建议**: 删除或改为统一使用。
- **复核状态**: 未复核

### [维度07-04] 查询方法权限配置不一致

- **严重程度**: P3
- **现状**: 9 个只读查询用 @Auth(roles="admin")，7 个用 @Auth(permissions="code-query")，功能类别无明显区分。
- **复核状态**: 未复核

## 审计合规总结

| 检查项 | 结果 |
|--------|------|
| BizModel 有对应 ORM 实体 | 全部 11 个合规 |
| BizModel 有对应 xmeta | 全部合规 |
| 正确继承 CrudBizModel<T> | 全部合规 |
| setEntityName() 调用 | 全部合规 |
| @BizQuery/@BizMutation 使用 | 全部合规 |
| 无 Map<String, Object> 反模式 | 未发现 |
| @BizLoader 使用 | 正确（forType 模式） |
