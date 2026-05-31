# 维度 13：安全与权限模型

## 第 1 轮（初审）

### [维度13-01] OperatorChain.deepCopy() 和 SimpleStreamOperatorFactory 无反序列化白名单

- **文件**: `nop-stream-core/src/main/java/io/nop/stream/core/jobgraph/OperatorChain.java:237-239` + `SimpleStreamOperatorFactory.java:48-52`
- **证据片段**:
  ```java
  // OperatorChain.deepCopy()
  ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
  try (ObjectInputStream ois = new ObjectInputStream(bais)) {
      return (OperatorChain) ois.readObject();
  }
  ```
- **严重程度**: P1
- **现状**: 使用标准 ObjectInputStream.readObject() 进行反序列化，没有像 SimpleTypeSerializer 那样覆盖 resolveClass() 进行白名单校验。数据来源是内存 round-trip。
- **风险**: 同模块中 SimpleTypeSerializer 已有白名单保护，这两处缺少同等保护，安全姿态不一致。如果 operator 中引入来自外部源的 Serializable 字段，可能成为利用链。
- **建议**: 添加 resolveClass() 覆盖 + ClassNameValidator 白名单，与 SimpleTypeSerializer 保持一致。
- **信心水平**: 很可能
- **误报排除**: 数据来源是受控的内存 round-trip，正常情况安全。但作为防御纵深原则建议加固。
- **复核状态**: 未复核

### [维度13-02] CheckpointSerDe JSON 反序列化 unchecked cast 缺少类型验证

- **文件**: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/CheckpointSerDe.java:73-74, 84-89`
- **证据片段**:
  ```java
  Map<String, Object> taskStatesMap = map.get("taskStates") instanceof Map
          ? (Map<String, Object>) map.get("taskStates") : null;
  ```
- **严重程度**: P2
- **现状**: JSON 反序列化后的 Map<String, Object> 进行 unchecked cast，Map 内 value 类型没有进一步校验。
- **风险**: checkpoint 数据被篡改时可能导致 ClassCastException。实际利用门槛较高（需数据库或文件系统访问权限）。
- **建议**: 增加 value 类型验证或白名单检查。
- **信心水平**: 很可能
- **误报排除**: 非远程代码执行风险，但缺少防御纵深。
- **复核状态**: 未复核

### [维度13-03] JdbcCheckpointStorage DDL 冗余唯一约束

- **文件**: `nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/storage/JdbcCheckpointStorage.java:412-459`
- **严重程度**: P3
- **现状**: CREATE TABLE DDL 中已包含 UNIQUE 约束，后续又通过 ALTER TABLE 添加相同约束。
- **风险**: 逻辑冗余，无安全风险。
- **建议**: 移除冗余的 ALTER TABLE 语句。
- **信心水平**: 确定
- **误报排除**: 确认建表已包含相同约束。
- **复核状态**: 未复核

## 正面发现

- SQL 注入防护良好（全部参数化查询）
- CheckpointSerDe 使用 JSON 而非 Java 原生序列化
- SimpleTypeSerializer 有白名单保护
- LocalFileCheckpointStorage 路径遍历防护完善（双重校验）
- 无硬编码凭证

## 最终保留项

| 编号 | 严重程度 | 文件 | 摘要 |
|------|---------|------|------|
| 13-01 | P1 | OperatorChain.java, SimpleStreamOperatorFactory.java | 反序列化无白名单 |
| 13-02 | P2 | CheckpointSerDe.java | JSON 反序列化 unchecked cast |
| 13-03 | P3 | JdbcCheckpointStorage.java | DDL 冗余唯一约束 |
