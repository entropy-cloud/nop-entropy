## 变更 2026-03-04
* **数据库变更**: nop-sys模块VERSION字段从INTEGER升级为BIGINT (commit: af4dbaf54)
  - 迁移指南: 执行ALTER TABLE修改字段类型，代码中version从Integer改为Long
  - 兼容性: 数据完全向后兼容，但JDBC返回类型变为Long

## 特性 2026-03-04
* 新增nop.cluster.name配置项，支持物理机房隔离 (commit: 19b1f98b0)
* 新增配置表达式解析器，支持${...}语法引用其他配置 (commit: 5bb9ffbcb)
* 新增路由**通配符语法糖，自动转换为{*path} (commit: d73f6ead7)
* 增强EQL集合操作符，支持AND/OR条件组合 (commit: 2ddbeee37)


# 更新日志

## 特性 2023-11-2
* 增加启用租户机制的全局开关nop.orm.enable-tenant-by-default
* 增加NopSpringTransactionFactory，继承Spring的事务管理

## 变更 2023-08-19
* 重构项目结构，将meta文件保存到独立的meta模块中。已经生成的代码需要按照NopMigrateTask中的做法进行迁移