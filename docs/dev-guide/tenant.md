## 租户配置

* 需要配置`nop.core.tenant-resource.enabled=true`来使用租户相关的模型缓存

* app.orm.xml中实体的useTenant属性设置为true时会针对此实体启用租户配置，此时会自动为该实体增加nop\_tenant\_id字段。

* `nop.orm.enable-tenant-by-default`设置为true时会全局缺省启用租户。此时，所有没有明确设置useTenant的实体都会自动启用租户配置。

* 在 excel 模型中，标签栏中增加标签: `no-tenant` 的实体，不会自动增加租户字段。

* 如果配置`nop.orm.user-use-tenant=true`，则用户表也自动进行tenant过滤，按主键查询的时候也会传入租户过滤条件。缺省是不需要的。


## 租户上下文的设置方式

* 设置上下文的 tenantId ，直接通过 `ContextProvider.setTenantId` 来设置。
* 如果本身是通过某种网关来访问我们的服务，可以通过http header中的nop-tenant来直接设置。

## 用户的租户管理

* NopAuthUser具有数据权限配置，如果没有nop-admin角色，则始终进行tenant过滤

## 迁移

如果系统一开始没有启用租户，后来又启用租户。则在配置文件中配置nop.orm.auto-add-tenant-col=true，在启动的时候会自动为所有相关的表增加租户字段，
租户的缺省值为0。

另外根据Excel模型生成代码时，会自动在deploy目录下建立 `_add_tenant_{appName}.sql`，其中包含有增加租户字段需要执行的SQL语句。

## 实现代码

在orm-gen.xlib中，通过`x:post-extends`元编程机制，在ORM模型的解析过程中自动为所有实体增加useTenant配置。

## 租户处理流程
1. 在 NopAuthTenant 中新增一个租户
2. 新增 NopAuthUser 用户的时候，设置用户的 tenantId 属性
3. 客户端发送请求，Nop 平台自动在相应的 IContext 中设置当前登录用户的 tenantId
4. ORM 自动根据 IContext 的 tenantId 进行租户过滤

租户过滤逻辑由ORM核心负责执行，一旦实体开启了租户支持，无法在运行时临时禁用。所有的dao访问和EQL查询都会自动应用租户过滤条件。
不能临时禁用的原因是：实体的id没有租户过滤的情况下有可能不唯一，而ORM的session需要以实体的id为唯一键来管理缓存。

如果某个表已经启用了租户，但是某些业务不想使用租户过滤，应该另外在 `app.orm.xml` 里另外增加一个实体映射，代表此表的无租户过滤的 `orm` 实体。
```
<entity name="XXXAll" useTenant="false"
  className="XXXAll" x:prototype="XXX"> // x:prototype 要继承的实体，注意：className 也需要配置，否则会报类型转换错误
</entity>
```
