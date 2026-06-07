# 维度 13：安全与权限模型

## 第 1 轮（初审）

### [维度13-01] validateLocalPath 在路径不存在时跳过 allowedLocalRoot 检查

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:1891-1913`
- **证据片段**:
  ```java
  java.io.File localFile = new java.io.File(path);
  if (localFile.isDirectory()) {    // <-- only checked if directory exists!
      if (allowedLocalRoot != null && !allowedLocalRoot.isEmpty()) {
          // allowedLocalRoot check skipped if not a directory
      }
  }
  ```
- **严重程度**: P1
- **现状**: `validateLocalPath()` 仅在 `localFile.isDirectory()` 为 true 时检查 `allowedLocalRoot`。如果路径尚不存在（首次索引场景），`isDirectory()` 返回 false，allowedLocalRoot 检查被完全跳过。此外 `resolveVfsPath` 将路径转为 "file:/abs/dir" 格式，`new File("file:/abs/dir").isDirectory()` 在大多数 JVM 中返回 false。
- **风险**: 安全校验入口可被绕过，路径遍历风险。
- **建议**: 始终检查 allowedLocalRoot，不依赖 isDirectory()。先 resolve 父目录的 canonical path 进行校验。
- **信心水平**: 高
- **误报排除**: validateLocalPath 作为安全校验入口的设计意图被 isDirectory() 条件削弱，实际安全缺陷。
- **复核状态**: 未复核

### [维度13-02] sourceCode @BizLoader 绕过 xmeta published=false 控制

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeFileBizModel.java:75-79`
- **证据片段**:
  ```java
  @BizLoader(forType = CodeFileAnalysisResult.class)
  @Auth(permissions = "NopCodeFile:query")
  public String sourceCode(@ContextSource CodeFileAnalysisResult file) {
      return file.getSourceCode();
  }
  ```
- **严重程度**: P2
- **现状**: NopCodeFile.xmeta 设置 sourceCode 为 published=false，但 @BizLoader 作为独立数据加载通道不受此限制。任何有 query 权限的用户可通过 GraphQL 查询获取完整源代码。
- **风险**: 源代码可能包含敏感信息（密钥、密码）。
- **建议**: 为 sourceCode @BizLoader 添加更严格的权限控制。
- **信心水平**: 中
- **误报排除**: 代码索引系统查看源代码可能是核心功能，但 published=false 暗示设计意图是不公开。两者应保持一致。
- **复核状态**: 未复核

### [维度13-03] indexId 参数无格式验证

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeIndexBizModel.java:58-60 等`
- **严重程度**: P3
- **现状**: indexId 直接从 API 传入到数据库查询，无长度限制、格式校验。也被用作 Map key 和缓存 key。
- **风险**: 超长 indexId 可导致内存问题。特殊字符可能影响 search engine topic name。
- **建议**: 添加 indexId 格式验证（字母数字+连字符+下划线，1-64 字符）。
- **信心水平**: 高
- **误报排除**: SQL 注入风险由参数化查询消除。风险主要在资源消耗。
- **复核状态**: 未复核

## 深挖第 2 轮追加

### [维度13-04] triggerIncrementalIndex validateLocalPath 使用原始 vfsPath 而非解析后路径

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:654-658`
- **证据片段**:
  ```java
  // triggerIncrementalIndex — 使用原始 vfsPath
  validateLocalPath(vfsPath);
  
  // 对比 indexDirectory — 先解析再验证
  String resolvedPath = resolveVfsPath(vfsPath);
  validateLocalPath(resolvedPath);
  ```
- **严重程度**: P1
- **现状**: triggerIncrementalIndex 直接在原始 vfsPath 上调用 validateLocalPath，而 indexDirectory 先 resolveVfsPath 再验证。VFS 路径（如 `file:/etc/passwd`）的 `new File(vfsPath).isDirectory()` 返回 false，且绝对路径检查不匹配 `file:` 前缀，导致验证完全失效。
- **风险**: triggerIncrementalIndex 完全绕过 allowedLocalRoot 限制。
- **建议**: 统一两个方法的验证模式——先 resolveVfsPath 再 validateLocalPath。
- **信心水平**: 高
- **误报排除**: 这是维度13-01 的更严重变体，在 triggerIncrementalIndex 中验证不仅被跳过而且完全无效。
- **复核状态**: 复核保留

### [维度13-05] ChangeAnalyzer.parseGitDiff 使用数据库存储的 workingDirectory 执行系统命令

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:1616-1634`, `nop-code/nop-code-flow/src/main/java/io/nop/code/flow/ChangeAnalyzer.java:131-139`
- **证据片段**:
  ```java
  // CodeIndexService — 使用数据库中的 rootPath
  String workingDirectory = indexEntity.getRootPath();
  return analyzer.analyzeChanges(..., workingDirectory);
  
  // ChangeAnalyzer — 作为 git diff 工作目录
  ProcessBuilder pb = new ProcessBuilder("git", "diff", baseline + ".." + target);
  pb.directory(new java.io.File(workingDirectory));
  ```
- **严重程度**: P2
- **现状**: analyzeChanges 使用数据库中存储的 rootPath 作为 git diff 命令的工作目录。baselineCommitish 和 targetCommitish 参数直接拼入命令字符串。
- **风险**: 有权限用户可通过查询接口触发本地命令执行。ProcessBuilder 虽非 shell 执行，但 git ref 参数可能被解析为选项。
- **建议**: 对 git ref 参数做白名单校验（仅允许合法 git ref 格式）。
- **信心水平**: 中
- **误报排除**: ProcessBuilder 使用参数数组而非 shell 执行，降低了注入风险。但 rootPath 来自数据库且经过有限验证。
- **复核状态**: 复核保留

## 维度复核结论

| 编号 | 判定 | 理由 |
|------|------|------|
| [维度13-01] | **保留** P1 | 安全校验被 isDirectory() 条件削弱 |
| [维度13-02] | **降级** P2→信息性 | @BizLoader 和 xmeta 工作在不同类型上，是有意设计 |
| [维度13-03] | **保留** P3 | indexId 作为 Map key 和缓存 key 无格式限制 |
| [维度13-04] | **新增保留** P1 | triggerIncrementalIndex 验证路径与 indexDirectory 不一致 |
| [维度13-05] | **新增保留** P2 | 数据库路径用于 git diff 命令执行 |

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|------|---------|---------|-----------|
| [维度13-01] | P1 | CodeIndexService.java | validateLocalPath 路径校验可被绕过 |
| [维度13-04] | P1 | CodeIndexService.java | triggerIncrementalIndex 验证使用原始 vfsPath |
| [维度13-05] | P2 | ChangeAnalyzer.java | 数据库路径用于 git diff 命令执行 |
| [维度13-03] | P3 | NopCodeIndexBizModel.java | indexId 参数无格式验证 |
