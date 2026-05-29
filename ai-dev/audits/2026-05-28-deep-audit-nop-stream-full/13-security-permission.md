# 维度 13：安全与权限模型

## 第 1 轮（初审）

### [维度13-01] Arbitrary class loading from checkpoint data and network messages

- **文件**: `nop-stream-core/.../transport/StreamElementCodec.java:102`, `MemoryKeyedStateBackend.java:384+`, `WindowAggregationOperator.java:445`
- **证据片段**:
  ```java
  // StreamElementCodec.java:102
  Class<?> clazz = Class.forName(envelope.getValueType());
  value = JsonTool.parseBeanFromText((String) payload, clazz);
  
  // WindowAggregationOperator.java:445
  SimpleAccumulator<Object> acc = (SimpleAccumulator<Object>) Class.forName(accType)
      .getDeclaredConstructor().newInstance();
  ```
- **严重程度**: P2
- **现状**: 类名来自反序列化的 checkpoint 数据和网络消息。Class.forName() 可被用于加载任意类，WindowAggregationOperator 还通过 newInstance() 实例化。
- **风险**: 在分布式部署中，如果消息总线或 checkpoint 存储被攻击者控制，可触发类加载和对象实例化。这是基础设施信任模型问题，与 Apache Flink 的信任模型一致。
- **建议**: 考虑添加类名白名单机制作为深度防御。低优先级。
- **误报排除**: 不是直接的应用漏洞，而是基础设施信任模型的文档化风险。所有类名在序列化时由框架自身产生（Class.getName()），不是用户输入。Checkpoint 存储和消息总线应受保护。
- **复核状态**: 未复核

### 已验证安全

| 方面 | 状态 |
|------|------|
| SQL 注入（参数化查询） | PASS |
| 反序列化（仅进程内深拷贝） | PASS |
| 路径遍历（双重验证 + 测试） | PASS |
| 敏感数据暴露（仅 fencing token） | PASS |
| 命令注入 | PASS |
| Fencing token 安全模型 | PASS |
