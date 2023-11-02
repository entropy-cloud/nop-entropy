# 租户配置


* app.orm.xml中实体的useTenant属性设置为true时会针对此实体启用租户配置，此时会自动为该实体增加nop_tenant_id字段。

* nop.orm.enable-tenant-by-default设置为true时会全局缺省启用租户。此时，所有没有明确设置useTenant的实体都会自动启用租户配置。

* nop_auth_user, nop_auth_session, nop_auth_op_log, nop_tenant这几个表不会自动启用租户配置

## 实现代码

在orm-gen.xlib中，通过`x:post-extends`元编程机制，在ORM模型的解析过程中自动为所有实体增加useTenant配置。

