# 维度 13：安全与权限模型

## 第 1 轮（初审）

### [维度13-01] indexDirectory 中 validateLocalPath 接收 file: 前缀路径，allowedLocalRoot 限制被绕过

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:294-310, 1869-1900`
- **证据片段**:
  ```java
  // indexDirectory 方法
  public int indexDirectory(String indexId, String vfsPath, String filePattern) {
      validatePath(vfsPath);                     // 只检查 ".."
      String resolvedPath = resolveVfsPath(vfsPath);  // "/etc" → "file:/etc"
      validateLocalPath(resolvedPath);           // "file:/etc" 绕过 startsWith("/") 检查
      ...
  }
  
  // validateLocalPath — 绝对路径检查不匹配 "file:" 前缀
  private void validateLocalPath(String path) {
      if (path.startsWith("/") || (path.length() >= 2 && path.charAt(1) == ':')) {
          throw new NopException(ERR_CODE_INVALID_PATH).param(ARG_PATH, path);
      }
      java.io.File localFile = new java.io.File(path);
      if (localFile.isDirectory()) {
          // new File("file:/etc").isDirectory() = false → 此检查被跳过!
      }
  }
  
  // resolveVfsPath — 添加 file: 前缀
  private String resolveVfsPath(String path) {
      if (path.startsWith("file:") || path.startsWith("/"))
          return "file:" + (path.startsWith("file:") ? path.substring(5) : path);
  }
  ```
- **严重程度**: P1
- **现状**: indexDirectory 先 validatePath 检查 ".."，再 resolveVfsPath 将路径转为 "file:/..." 格式，然后传给 validateLocalPath。但 validateLocalPath 的绝对路径检查不匹配 "file:" 前缀格式，导致路径验证失效。admin 用户可调用 triggerFullIndex(indexId, "/etc") 索引服务器任意目录。
- **风险**: admin 用户可读取服务器上任意目录的源文件内容。虽需 admin 角色，但 admin 权限不应等同于可读任意文件。
- **建议**: 对 vfsPath（而非 resolvedPath）调用 validateLocalPath，或在 validateLocalPath 中增加对 file: 前缀的处理。
- **信心水平**: 高 (85%)
- **误报排除**: 路径追踪清晰，可通过单元测试验证。triggerIncrementalIndex 不存在此问题（直接传 vfsPath）。
- **复核状态**: 未复核

### [维度13-02] validatePath 仅检查 ".."，不防御 URL 编码等路径穿越变体

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:1862-1867`
- **证据片段**:
  ```java
  private void validatePath(String path) {
      if (path == null || path.isEmpty()) return;
      if (path.contains(".."))
          throw new NopException(ERR_CODE_INVALID_PATH).param(ARG_PATH, path);
  }
  ```
- **严重程度**: P2
- **现状**: 仅通过 path.contains("..") 检查路径穿越，不检测 URL 编码变体（如 %2e%2e）。
- **风险**: 如果 VFS 层对输入路径进行 URL 解码，可能导致穿越。
- **建议**: 先对路径进行规范化（URL 解码后再检查），或检查规范化后的 canonical path。
- **信心水平**: 中 (65%)
- **误报排除**: 取决于 VFS 是否自动解码 URL 编码的路径。
- **复核状态**: 未复核

### [维度13-03] NopCodeFileBizModel 的 CRUD 继承方法缺乏权限覆盖

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeFileBizModel.java:25`
- **证据片段**:
  ```java
  @BizModel("NopCodeFile")
  public class NopCodeFileBizModel extends CrudBizModel<NopCodeFile> implements INopCodeFileBiz {
      // 自定义方法有 @Auth 注解，但继承的 CrudBizModel CRUD 方法未覆盖
  }
  ```
- **严重程度**: P3
- **现状**: NopCodeFile 包含 sourceCode 字段，继承的默认 CRUD 方法可能允许直接读取。但 xmeta 设置 sourceCode 为 published="false"。
- **风险**: 需验证 CrudBizModel 默认方法是否尊重 xmeta 的 published 属性。
- **建议**: 验证默认 CRUD 方法对 published="false" 字段的处理。
- **信心水平**: 中 (80%)
- **误报排除**: 已考虑 xmeta 的 published 属性可能提供保护。
- **复核状态**: 未复核
