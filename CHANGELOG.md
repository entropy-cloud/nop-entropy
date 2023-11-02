# 更新日志

## 特性 2023-11-2
* 增加启用租户机制的全局开关nop.orm.enable-tenant-by-default
* 增加NopSpringTransactionFactory，继承Spring的事务管理

## 变更 2023-08-19
* 重构项目结构，将meta文件保存到独立的meta模块中。已经生成的代码需要按照NopMigrateTask中的做法进行迁移