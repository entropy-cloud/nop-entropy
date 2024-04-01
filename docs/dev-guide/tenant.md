# 租户配置

* app.orm.xml中实体的useTenant属性设置为true时会针对此实体启用租户配置，此时会自动为该实体增加nop\_tenant\_id字段。

* nop.orm.enable-tenant-by-default设置为true时会全局缺省启用租户。此时，所有没有明确设置useTenant的实体都会自动启用租户配置。

* nop\_auth\_user, nop\_auth\_session, nop\_auth\_op\_log, nop\_tenant这几个表不会自动启用租户配置

* 如果配置nop.orm.user-use-tenant=true，则用户表也自动进行tenant过滤，按主键查询的时候也会传入租户过滤条件。缺省是不需要的。

## 用户的租户管理

* NopAuthUser具有数据权限配置，如果没有nop-admin角色，则始终进行tenant过滤

## 迁移

如果系统一开始没有启用租户，后来又启用租户。则在配置文件中配置nop.orm.auto-add-tenant-col=true，在启动的时候会自动为所有相关的表增加租户字段，
租户的缺省值为0。

另外根据Excel模型生成代码时，会自动在deploy目录下建立 `_add_tenant_{appName}.sql`，其中包含有增加租户字段需要执行的SQL语句。

## 实现代码

在orm-gen.xlib中，通过`x:post-extends`元编程机制，在ORM模型的解析过程中自动为所有实体增加useTenant配置。
