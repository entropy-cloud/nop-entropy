## Tenant Configuration

* Need to configure `nop.core.tenant-resource.enabled=true` to use tenant-related model caching.

* When the `useTenant` property of an entity is set to `true` in `app.orm.xml`, tenant configuration will be applied for that entity, automatically adding a `nop_tenant_id` field.

* If `nop.orm.enable-tenant-by-default` is set to `true`, tenant configuration will be globally enabled by default. In this case, all entities without an explicit `useTenant` setting will automatically have tenant configuration applied.

* In the Excel model, entities with the label `no-tenant` in the tag will not automatically have a `nop_tenant_id` field added.

* If `nop.orm.user-use-tenant=true`, then the user table will also perform tenant filtering. When querying by primary key, tenant filter conditions will be included. This is optional and defaults to `false`.

## Setting Tenant Context

* Set the `tenantId` in the context using `ContextProvider.setTenantId`.
* If accessed via a gateway, tenant can be set via the HTTP header's `nop-tenant`.

## User Tenant Management

* NopAuthUser has data permission configuration. If the user does not have the nop-admin role, tenant filtering will always be performed.

## Migration

* If the system initially did not enable tenant support but later enabled it, configure `nop.orm.auto-add-tenant-col=true` in the configuration file. This will automatically add a `tenant_id` column to all relevant tables with a default value of 0.

* When generating code based on an Excel model, a SQL script `_add_tenant_{appName}.sql` will be created in the deploy directory containing the SQL statements needed to add the tenant fields.

## Implementation Code

* In `orm-gen.xlib`, the `x:post-extends` meta programming mechanism is used during ORM model parsing. This automatically sets the `useTenant` property for all entities.

## Tenant Processing Flow
1. Add a tenant in `NopAuthTenant`.
2. When adding a `NopAuthUser`, set the `tenantId` property.
3. The client sends a request, and Nop will automatically set the `tenantId` in the corresponding `IContext` for the logged-in user.
4. The ORM will perform tenant filtering based on `IContext.tenantId`.

Tenant filtering logic is handled by the ORM core. Once an entity enables tenant support, it cannot be temporarily disabled at runtime. All DAO and EQL queries will automatically apply tenant filter conditions.

The reason tenant filtering cannot be temporarily disabled is: Without tenant filtering, entity IDs may not be unique, which could lead to issues with ORM's session management, which relies on entity IDs as primary keys for caching.

If a table has been configured with tenant support but certain business processes do not want tenant filtering applied, you should create a separate mapping in `app.orm.xml` for that table, setting `useTenant="false"`.

Here is an example of such a mapping:
```xml
<entity name="XXXAll" useTenant="false"
  className="XXXAll" x:prototype="XXX">
  // x:prototype indicates the prototype class to inherit from, note that className must also be configured.
</entity>
```
