# ORM数据库初始化

## 启动时自动对数据库进行增量更新

* 仅自动执行表新增、字段重定义、字段新增、唯一约束新增、唯一约束删除、唯一约束重定义等变更，不做表和字段的删除：字段删除无法区分是否为重命名造成的，表删除可能会造成比对范围以外的表被误删除
* 启用自动升级，需在工程内引入依赖 io.github.entropy-cloud:nop-dbtool-core，并设置 nop.orm.db-differ.auto-upgrade-database 为 true 