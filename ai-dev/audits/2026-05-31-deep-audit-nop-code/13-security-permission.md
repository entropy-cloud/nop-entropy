# Audit Dimension 13: Security and Permission Model — nop-code

### [13-01] No Permission Annotations on Any BizModel Method

- **File**: `nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeIndexBizModel.java:39-246`（及所有11个BizModel类）
- **Evidence Snippet**:
```java
@BizMutation
public String triggerFullIndex(
        @Name("indexId") String indexId,
        @Name("projectPath") String projectPath) {
    int fileCount = codeIndexService.indexDirectory(indexId, projectPath, "**/*.java");
}

@BizMutation
public int deleteIndex(@Name("indexId") String indexId) {
    codeIndexService.deleteIndex(indexId);
}
```
- **Severity**: P1
- **Current State**: 11个 BizModel 的 42+ 个 @BizQuery/@BizMutation 方法全部无权限注解。破坏性操作（triggerFullIndex、deleteIndex、indexDirectory、indexFile）和敏感读操作（searchCode、sourceCode、exportGraph）完全无认证。
- **Risk**: 任何认证用户可触发任意目录重新索引、删除索引、注入任意代码分析、读取所有已索引文件源码。
- **Recommendation**: 至少对所有 mutation 方法添加 @BizPermission 注解。
- **Confidence**: High
- **False Positive Exclusion**: grep @BizPermission|@Auth|permission 返回零匹配。
- **Review Status**: Not reviewed

---

### [13-02] Filesystem Path Traversal in triggerFullIndex

- **File**: `nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:352-379`
- **Evidence Snippet**:
```java
public synchronized int indexDirectory(String indexId, String vfsPath, String filePattern) {
    validatePath(vfsPath);
    java.io.File localFile = new java.io.File(vfsPath);
    if (localFile.isDirectory()) {
        ProjectAnalysisResult result = analyzer.analyzeProject(localFile.toPath());
    }
}

private void validatePath(String path) {
    if (path.contains(".."))
        throw new NopException(ERR_CODE_INVALID_PATH).param(ARG_PATH, path);
}
```
- **Severity**: P1
- **Current State**: validatePath 仅阻止 `..`，不验证解析后的规范路径。绝对路径（如 /etc、/home）直接通过。
- **Risk**: 攻击者可读取服务器任意目录文件，内容通过 GraphQL API 可查询。
- **Recommendation**: 使用 Path.toRealPath() 并检查白名单基础目录。
- **Confidence**: High
- **False Positive Exclusion**: 仅做 `..` 子串检查，Unix 绝对路径轻松绕过。
- **Review Status**: Not reviewed

---

### [13-03] Git Command Injection via Unsanitized Commit Refs

- **File**: `nop-code-flow/src/main/java/io/nop/code/flow/ChangeAnalyzer.java:116-126`
- **Evidence Snippet**:
```java
ProcessBuilder pb = new ProcessBuilder(
        "git", "diff", baseline + ".." + target, "--unified=0");
```
- **Severity**: P1
- **Current State**: baseline/target 直接拼接进 git diff 命令。虽然 ProcessBuilder 不是 shell 执行，但 git 支持危险 flag（如 --output）。
- **Risk**: 参数注入可导致任意文件写入或命令执行。
- **Recommendation**: 验证 baseline/target 匹配安全模式（hex SHA 或 [a-zA-Z0-9./_-]+），拒绝以 `-` 开头的值。
- **Confidence**: Medium-High
- **False Positive Exclusion**: 用户输入直接从 GraphQL 到命令构造无过滤。
- **Review Status**: Not reviewed

---

## 最终保留项

| 编号 | 严重程度 | 文件路径 | 摘要 |
|------|---------|---------|------|
| 13-01 | P1 | 所有 BizModel | 42+ 方法无权限注解 |
| 13-02 | P1 | CodeIndexService.java | 文件系统路径遍历（仅检查..） |
| 13-03 | P1 | ChangeAnalyzer.java | Git 参数注入 |
