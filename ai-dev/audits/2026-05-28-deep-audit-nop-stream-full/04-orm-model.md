# 维度 04：ORM 模型与实体设计

## 零发现说明

**检查范围**: 搜索 nop-stream/ 下所有 *.orm.xml 文件。

**结论**: nop-stream 是流处理引擎框架模块，不使用 Nop 平台的 ORM/BizModel/xmeta/xbiz 标准业务模块骨架。该模块无 ORM 模型文件、无数据库实体定义、无 XMeta 文件。数据持久化通过 JdbcCheckpointStorage 和 JdbcClusterRegistry 直接使用 IJdbcTemplate 接口实现，不经过 ORM 层。

此维度不适用于 nop-stream 模块。
