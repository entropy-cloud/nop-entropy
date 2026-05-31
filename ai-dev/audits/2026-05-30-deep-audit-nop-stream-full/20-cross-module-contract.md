# 维度 20：跨模块契约一致性

## 第 1 轮（初审）

### 检查范围

- nop-stream-core 公共接口稳定性
- connector optional 依赖接口兼容性
- runtime provided 依赖接口兼容性

### 结论：无发现

| 检查项 | 结果 |
|--------|------|
| core 公共接口 | SourceFunction/SinkFunction/MapFunction 等接口签名稳定 |
| connector optional 依赖 | 5 个连接器类正确桥接外部模块接口到 core 接口，构造器校验非 null |
| runtime provided 依赖 | JdbcCheckpointStorage/JdbcClusterRegistry 正确使用 nop-dao API |
| @Internal 标注 | runtime 中的 JDBC 相关类已标注 @Internal |

nop-stream-api 为空壳模块，所有公共接口在 nop-stream-core 中。如果未来抽取 API 层，消费者需修改依赖坐标。

## 维度复核结论

（待复核）
