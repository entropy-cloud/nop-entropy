
## Tenant Configuration

* You need to set `nop.core.tenant-resource.enabled=true` to use tenant-related model caching.

* When the useTenant attribute of an entity in app.orm.xml is set to true, tenant configuration is enabled for that entity; in this case, the field nop\_tenant\_id will be automatically added for that entity.

* When `nop.orm.enable-tenant-by-default` is set to true, tenants are enabled by default globally. In this case, all entities that do not explicitly set useTenant will automatically enable tenant configuration.

* In the Excel model, entities tagged with `no-tenant` in the tag bar will not automatically add the tenant field.

* If `nop.orm.user-use-tenant=true` is configured, the user table will also be filtered by tenant, and tenant filter criteria will be included when querying by primary key. By default, this is not needed.


## How to Set the Tenant Context

* To set the context’s tenantId, set it directly via `ContextProvider.setTenantId`.
* If you are accessing our service through a gateway, you can set it directly via the nop-tenant in the HTTP header.

## Tenant Management for Users

* NopAuthUser has data permission configurations; if the user does not have the nop-admin role, tenant filtering is always applied.

## Migration

If the system did not enable tenants at the beginning and later enabled them, configure `nop.orm.auto-add-tenant-col=true` in the configuration file. At startup, all relevant tables will automatically have the tenant field added, with a default tenant value of 0.

Additionally, when generating code based on the Excel model, a file `_add_tenant_{appName}.sql` will be automatically created under the deploy directory, which contains the SQL statements needed to add the tenant field.

## Implementation Code

In orm-gen.xlib, via the `x:post-extends` metaprogramming mechanism, useTenant is automatically added to all entities during the parsing of the ORM model.

## Tenant Processing Flow
1. Add a tenant in NopAuthTenant.
2. When adding a NopAuthUser, set the user’s tenantId property.
3. The client sends a request, and the Nop platform automatically sets the tenantId of the currently logged-in user in the corresponding IContext.
4. The ORM automatically performs tenant filtering based on the tenantId in IContext.

The tenant filtering logic is executed by the ORM core. Once an entity has tenant support enabled, it cannot be temporarily disabled at runtime. All DAO access and EQL queries will automatically apply tenant filter conditions.
The reason it cannot be temporarily disabled is that an entity’s id may not be unique without tenant filtering, whereas the ORM session needs to manage the cache using the entity’s id as a unique key.

If a table has tenant enabled but certain business scenarios should not use tenant filtering, you should add another entity mapping in `app.orm.xml` that represents a non-tenant-filtered `orm` entity for this table.
```
<entity name="XXXAll" useTenant="false"
  className="XXXAll" x:prototype="XXX"> // x:prototype is the entity to inherit; note: className must also be configured, otherwise a type-casting error will be thrown
</entity>
```

<!-- SOURCE_MD5:823fa8413999c82acb2584ee9d717e6f-->
