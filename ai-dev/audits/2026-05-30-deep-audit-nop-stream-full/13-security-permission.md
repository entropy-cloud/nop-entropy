# 维度 13：安全与权限模型

## 第 1 轮（初审）

### [维度13-01] SimpleTypeSerializer.deserialize() 未调用 ClassNameValidator

- **文件**: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/typeinfo/SimpleTypeSerializer.java:37`
- **证据片段**:
```java
public T deserialize(byte[] bytes) {
    try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
        return (T) ois.readObject();  // 无 ClassNameValidator 或 ObjectInputFilter
    } catch (Exception e) {
        throw NopException.adapt(e);
    }
}
```
- **严重程度**: P3
- **现状**: 该类直接使用 `ObjectInputStream.readObject()` 反序列化，未调用 `ClassNameValidator`。当前所有调用场景中输入数据来自可信源（同 JVM 内 checkpoint 状态），但核心路径（MemoryStateSerDe、StreamElementCodec）均有白名单保护，此处缺少纵深防御。
- **风险**: 如果未来 SimpleTypeSerializer 的输入源变为不可信数据，存在反序列化漏洞风险。
- **建议**: 在反序列化前加入 `ClassNameValidator` 或 `ObjectInputFilter`。
- **信心水平**: 很可能
- **误报排除**: 核心路径已有白名单保护，此处是唯一缺口。当前输入源可信但应保持一致的安全基线。
- **复核状态**: 未复核

---

### 无问题的安全检查项

| 检查项 | 结论 |
|--------|------|
| SQL 注入 | JdbcCheckpointStorage/JdbcClusterRegistry 全部使用参数化查询 |
| ClassNameValidator 白名单 | 核心路径（MemoryStateSerDe、StreamElementCodec、WindowAggregationOperator）均有保护 |
| 敏感数据 | 无硬编码密码/密钥，fencing token 是 UUID epoch 标识符 |
| 路径遍历 | LocalFileCheckpointStorage 使用 ID 正则验证 + 路径规范化检查双重保护 |

## 维度复核结论

（待复核）

## 最终保留项

（待复核后填写）
