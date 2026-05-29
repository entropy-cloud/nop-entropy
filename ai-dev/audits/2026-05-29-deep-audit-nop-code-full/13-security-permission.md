# 维度13：安全与权限模型

## 第 1 轮（初审）

### [维度13-01] BizModel 方法无细粒度权限注解——粗粒度 query/mutation 二分

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeIndexBizModel.java`
- **行号**: L39-96
- **证据片段**:
  ```java
  @BizMutation
  public String triggerFullIndex(...) { ... }
  
  @BizMutation
  public boolean deleteIndex(@Name("indexId") String indexId) { ... }
  ```
  ```xml
  <!-- _nop-code.action-auth.xml -->
  <resource id="FNPT:NopCodeIndex:mutation">
      <permissions>NopCodeIndex:mutation</permissions>
  </resource>
  ```
- **严重程度**: P2
- **现状**: 所有方法无 @BizPermission 注解。权限仅按实体名 + query/mutation 粗粒度划分。deleteIndex 和 indexDirectory 共享 NopCodeIndex:mutation 权限。
- **风险**: 高风险操作（deleteIndex）与低风险操作共享同一权限。无法做到"可查询但不可删除"。triggerFullIndex 接受用户提供的 projectPath，恶意调用可能耗尽资源。
- **建议**: 对 deleteIndex、triggerFullIndex 等高危操作添加 @BizPermission，定义独立权限。
- **信心水平**: 确定
- **误报排除**: 作为 WIP 实验模块，粗粒度权限可能暂时可接受。
- **复核状态**: 未复核

### [维度13-02] validatePath 路径校验过于简单

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java`
- **行号**: L2967-2972
- **证据片段**:
  ```java
  private void validatePath(String path) {
      if (path == null || path.isEmpty()) return;
      if (path.contains(".."))
          throw new NopException(ERR_CODE_INVALID_PATH).param(ARG_PATH, path);
  }
  ```
- **严重程度**: P2
- **现状**: 仅检查 .. 子串。不检查绝对路径、URL 编码绕过。indexFile 的 filePath 参数未调用 validatePath。
- **风险**: 路径遍历防护不充分。indexFile 的 filePath 未经校验。
- **建议**: 使用路径规范化工具替换简单子串检查。对 indexFile 也添加路径校验。
- **信心水平**: 很可能
- **误报排除**: 当前路径主要用于文件系统扫描，实际风险取决于部署环境。
- **复核状态**: 未复核

### [维度13-03] indexFile 接受无限制大小的 sourceCode 参数

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeIndexBizModel.java`
- **行号**: L89-96
- **证据片段**:
  ```java
  @BizMutation
  public FileAnalysisDTO indexFile(
          @Name("indexId") String indexId,
          @Name("filePath") String filePath,
          @Name("sourceCode") String sourceCode) {
  ```
- **严重程度**: P2
- **现状**: sourceCode 参数无约束。xmeta 中 precision="524288" 仅对 ORM 实体生效，不对 GraphQL 入参生效。
- **风险**: DoS 风险。恶意用户可发送极大 payload 导致 OOM。
- **建议**: 在方法入口添加 sourceCode 长度校验（如最大 1MB）。
- **信心水平**: 很可能
- **误报排除**: WIP 模块当前可能在内网使用，但仍是安全隐患。
- **复核状态**: 未复核
