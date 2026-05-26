# 维度03+12+13+14：API/GraphQL/安全/事务

## 维度03：API 表面积

### [维度03-01] NopCodeFileBizModel.getByPath 返回内部领域模型

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeFileBizModel.java:35-39`
- **证据片段**:
```java
@BizQuery
public CodeFileAnalysisResult getByPath(...) {
    return codeIndexService.getFileByPath(indexId, filePath);
}
```
- **严重程度**: P2
- **现状**: 直接返回 core 包的内部模型类 `CodeFileAnalysisResult`，而非 DTO。
- **风险**: 泄露内部实现细节。
- **建议**: 改为返回 DTO。
- **复核状态**: 未复核

---

### [维度03-02] NopCodeFile.sourceCode 在 xmeta 中无可见性限制

- **文件**: `nop-code/nop-code-meta/src/main/resources/_vfs/nop/code/model/NopCodeFile/_NopCodeFile.xmeta:53-56`
- **严重程度**: P2
- **现状**: `sourceCode`（524288字符）设置了 `queryable="true" sortable="true" insertable="true" updatable="true"`，无任何可见性限制。
- **风险**: 通过 GraphQL CRUD API 任何人可读写任意索引文件的全量源代码。
- **建议**: 在 xmeta 中设置 `published="false"` 或 `insertable="false" updatable="false"`。
- **复核状态**: 未复核

---

## 维度12：GraphQL

### [维度12-01] filterByFilePattern 的 glob→regex 转换未转义元字符（ReDoS 风险）

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:926-932`
- **证据片段**:
```java
String regex = filePattern.replace("*", ".*").replace("?", ".");
// 未转义其他正则元字符 (, ), +, {, [, \
```
- **严重程度**: P2
- **现状**: 用户传入正则元字符会导致运行时异常或 ReDoS。
- **建议**: 改用 glob 匹配工具或先转义非 `*`/`?` 的元字符。
- **复核状态**: 未复核

---

## 维度13：安全

### [维度13-01] 全模块所有 BizModel 方法无权限注解

- **文件**: 全部 BizModel 类
- **严重程度**: P1
- **现状**: 48 个 @BizQuery/@BizMutation/@BizLoader 方法，无一使用权限注解。@BizMutation 方法（triggerFullIndex/deleteIndex）可被匿名调用。
- **风险**: 可触发服务器 CPU/IO 过载，删除任意索引数据。
- **建议**: 对所有 @BizMutation 方法添加 `@BizPermission` 注解。
- **复核状态**: 未复核

---

### [维度13-02] triggerFullIndex 接受未验证的文件系统路径（路径遍历）

- **文件**: `NopCodeIndexBizModel.java:41-55`, `CodeIndexService.java:310-334`
- **证据片段**:
```java
@BizMutation
public String triggerFullIndex(@Name("indexId") String indexId, @Name("projectPath") String projectPath) {
    int fileCount = codeIndexService.indexDirectory(indexId, projectPath, "**/*.java");
}
```
- **严重程度**: P1
- **现状**: `projectPath` 直接传给 `new File(vfsPath)`，攻击者可遍历 `/etc`、`/` 等路径。
- **建议**: 添加路径白名单校验。
- **复核状态**: 未复核

---

### [维度13-03] incrementalStatusMap 无界增长 + 非线程安全

- **文件**: `NopCodeIndexBizModel.java:34`
- **严重程度**: P2
- **现状**: `LinkedHashMap` 无容量限制、非线程安全，并发读写可能导致死循环。
- **建议**: 改用 `ConcurrentHashMap`，添加容量上限。
- **复核状态**: 未复核

---

## 维度14：事务

### [维度14-01] deleteIndex 异常被静默吞没，可能破坏数据一致性

- **文件**: `CodeIndexService.java:1273-1311`
- **证据片段**:
```java
ormTemplate.runInSession(session -> {
    try {
        // 删除多个子表
    } catch (Exception e) {
        LOG.warn("...");
        return null; // 不抛异常，调用方认为删除成功
    }
});
```
- **严重程度**: P1
- **现状**: 部分删除失败时，调用方收到成功响应，但数据库中残留孤立记录。
- **建议**: 移除 try/catch 让异常传播触发回滚。
- **复核状态**: 未复核

---

### [维度14-02] analysisCacheMap 的 synchronized 粒度粗

- **文件**: `CodeIndexService.java:98-306`
- **严重程度**: P2
- **现状**: 三个 synchronized 方法锁 `this`，重建缓存时持有锁时间过长。
- **建议**: 改用 `ConcurrentHashMap` + per-key lock。
- **复核状态**: 未复核
