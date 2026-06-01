# 维度13：安全与权限模型 -- nop-code 模块审计报告

## 第 1 轮（初审）

### [维度13-01] @BizQuery 方法使用 `@Auth(roles = "admin")` 的不一致性

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeIndexBizModel.java:124,142,151,183,192,221,229,238,247,263`
- **证据片段**:
  ```java
  @BizQuery @Auth(roles = "admin")
  public CommunityDetectionResultDTO detectCommunities(...) { ... }
  // 对比同 BizModel 中：
  @BizQuery @Auth(permissions = "code-query")
  public List<DepDTO> getDeps(...) { ... }
  ```
- **严重程度**: P3
- **现状**: 10 个纯查询方法使用 `@Auth(roles = "admin")`，而同 BizModel 中其他查询方法使用 `@Auth(permissions = "code-query")`。不一致。
- **风险**: 管理员角色要求比查询权限更严格，可能导致普通用户无法使用图分析功能。
- **建议**: 统一为 `code-query` 权限或定义 `code-analysis` 级别。
- **信心水平**: 85%
- **误报排除**: 这不是缺少权限注解（全部方法都有 @Auth），而是权限级别不一致。
- **复核状态**: 未复核

### [维度13-02] `indexFile` 方法缺少对 sourceCode 大小限制

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeIndexBizModel.java:100-108`
- **证据片段**:
  ```java
  @BizMutation @Auth(roles = "admin")
  public String indexFile(@Name("indexId") String indexId,
                          @Name("filePath") String filePath,
                          @Name("sourceCode") String sourceCode) { ... }
  ```
- **严重程度**: P3
- **现状**: `indexFile` 接受用户提供的 `sourceCode` 字符串，无大小限制。已限制为 admin 角色。
- **风险**: 恶意或超大输入可能导致 TreeSitter 解析器内存消耗过大。
- **建议**: 增加 `sourceCode` 长度上限校验（如 1MB）。
- **信心水平**: 80%
- **误报排除**: 已限制为 admin 角色，风险降低。
- **复核状态**: 未复核

### [维度13-03] `allowedLocalRoot` 默认为 null，路径校验不生效

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:1600`
- **证据片段**:
  ```java
  @InjectValue(configKey = "nop-code.allowed-local-root", defaultValue = "")
  protected String allowedLocalRoot;
  ```
- **严重程度**: P2
- **现状**: `allowedLocalRoot` 默认为空字符串，`validateLocalPath` 在此情况下不做规范路径校验。生产部署中若未显式配置，路径校验不生效。
- **风险**: 管理员可能通过 `indexDirectory` 访问服务器上任意目录。
- **建议**: `allowedLocalRoot` 设为必填或增加启动时校验。
- **信心水平**: 85%
- **误报排除**: 已限制为 admin 角色，且 `validatePath` 仍检查 `..` 路径遍历。
- **复核状态**: 未复核

## 无问题确认

- **所有公开方法均有 @Auth 注解**: 48 个 API 方法全覆盖。
- **sourceCode 字段在 xmeta 中正确限制**: `published="false" queryable="false"`。
- **无 SQL 注入风险**: 全部使用参数化 QueryBean。
