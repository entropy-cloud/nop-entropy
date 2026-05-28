# 维度 04: ORM 模型与实体设计

## 适用性
不适用 - nop-stream 是流计算引擎模块，不包含数据库表定义或 ORM 模型文件（无 `model/*.orm.xml`）。JdbcCheckpointStorage 使用的是自建的 DDL（在代码中动态建表），而非通过 Nop ORM 模型驱动。nop-dao 在 runtime 中是 `<scope>provided</scope>`，仅用于 IJdbcTemplate 访问。
