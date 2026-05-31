# 维度 13：安全与权限模型

## 第 1 轮（初审）

### [维度13-01] ClassNameValidator 白名单 javax./jakarta. 前缀过宽

- **文件**: `nop-stream-core/.../util/ClassNameValidator.java:15-38`
- **严重程度**: P2（Medium）
- **现状**: ALLOWED_PREFIXES 包含 "javax." 和 "jakarta." 两个极宽泛前缀。
- **风险**: 攻击者篡改检查点数据后，可利用 javax.naming.InitialContext 等危险类名绕过白名单。
- **建议**: 替换为具体的白名单子包（如 javax.annotation.、jakarta.annotation. 等）。

### [维度13-02] SimpleTypeSerializer 使用无过滤的 Java 原生反序列化

- **文件**: `nop-stream-core/.../typeinfo/SimpleTypeSerializer.java:34-39`
- **证据片段**:
  ```java
  public T deserialize(byte[] data) throws Exception {
      ByteArrayInputStream bis = new ByteArrayInputStream(data);
      ObjectInputStream ois = new ObjectInputStream(bis);
      return (T) ois.readObject(); // 无 ObjectInputFilter
  }
  ```
- **严重程度**: P2（Medium）
- **风险**: 如果 byte[] data 来自被篡改的检查点数据，可实现 RCE。
- **建议**: 注册 ObjectInputFilter 或改为 JSON 序列化。

### [维度13-03] ClassNameValidator 的 java.io. 前缀允许潜在敏感类

- **文件**: `nop-stream-core/.../util/ClassNameValidator.java:24`
- **严重程度**: P3（Low）
- **现状**: java.io. 前缀允许 File/FileOutputStream 等类通过验证。

## 已确认安全项

- JdbcCheckpointStorage/JdbcClusterRegistry：全部参数化 SQL，无注入风险
- LocalFileCheckpointStorage：三层路径遍历防护（正则+规范化+预计算基路径）
- 无硬编码敏感信息
- 无 HTTP 端点暴露
- CheckpointSerDe 使用 JSON 序列化，安全

## 最终保留项

| 编号 | 严重程度 | 文件 | 摘要 |
|------|---------|------|------|
| 13-01 | P2 | ClassNameValidator.java | javax./jakarta. 白名单过宽 |
| 13-02 | P2 | SimpleTypeSerializer.java | Java 原生反序列化无过滤 |
| 13-03 | P3 | ClassNameValidator.java | java.io. 前缀过宽 |
