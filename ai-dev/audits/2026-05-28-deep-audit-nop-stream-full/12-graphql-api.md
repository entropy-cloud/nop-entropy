# 维度 12：GraphQL 与 API 层

## 零发现说明

**检查范围**: 搜索 GraphQL 相关文件和 API 路径。

**结论**: nop-stream 模块不暴露 GraphQL API。该模块通过 Java API（DataStream、Pattern、StreamExecutionEnvironment）和 Java SPI（IDeploymentPlanProvider）提供服务，不经过 Nop 平台的 GraphQL 引擎。

此维度不适用于 nop-stream 模块。
