# Permissions

## Enable Permissions

* Configure `nop.auth.enable-action-auth=true` to enable operation permissions. Field-level permissions also utilize this switch.
* Configure `nop.auth.enable-data-auth=true` to enable data permissions.
* Configure `nop.auth.use-data-auth-table=true` to use the data permissions configuration table `NopAuthRoleDataAuth`. This allows merging of data permissions rules from both the database and configuration files.
* By default, `/nop/main/auth/app.action-auth.xml` is loaded as a static permission configuration file. You can customize the path using `nop.auth.site-map.static-config-path`.
* In `main/action-auth.xml`, use `x:extends` to import existing permission configuration files.
* If `nop.auth.skip-check-for-admin=true`, users with the `admin` role or `nop-admin` will bypass operation permission checks. The default is `true`.
* Configure `nop.auth.service-public=true` to expose backend services without requiring login, allowing access to `/debug` and `/graphiql`.
* Configure `nop.auth.quarkus-dev-public=true` to expose the Quarkus debug pages at `/debug` and `/graphiql`.

> When running in debug mode, the platform will print all known configuration variables and their locations.

## Core Interfaces

1. `IUserContext`: Saves `userId`, `roles`, etc., user identity and permission-related information. Use `IUserContext.set()/get()` methods for access.
2. `IActionAuthChecker`: Checks operation permissions and field permissions. In `GraphQLExecutor`, call this for each GraphQL field.
3. `IDataAuthChecker`: Checks data permissions. In `CrudBizModel`, add filter conditions to the Query object before fetching each entity, then execute `check` on the entity.
4. Authentication is implemented in `AuthHttpServerFilter` by calling `ILoginService`.
5. `ILoginService` handles login, logout, and token validation. Built-in implementations include `LoginServiceImpl` and `OAuthLoginServiceImpl`.

## User Roles

* All users automatically have a `user` role and do not require explicit assignment.
* Each user has a single `primary` role, similar to job roles.
*Roles are hierarchical, with child roles defined in the parent role. This resembles a role grouping mechanism, simplifying configuration.
* By default, admin role permissions are not checked. Use `nop.auth.skip-check-for-admin` to enable checks.

Using this concept of associated child roles allows for dynamic permission assignment. For example:
- Assign the `user` role to all users.
- In `accessDeptData`, specify data permissions and operation permissions.
- The system will automatically apply these permissions to all users with the `user` role.

## Operation Permissions

### Operation Permissions Configuration

Use `action-auth.xml` and `NopAuthResource` to configure operation permissions. The `resource` type is divided into `TOPM`, `SUBM`, and `FNPT`, corresponding to top-level, sub-level, and function-point permissions. Mark the relevant `permissions` at each function point.

```xml
<resource id="NopAuthDept-main" displayName="Department" orderNo="10001" i18n-en:displayName="Department"
          icon="ant-design:appstore-twotone" component="AMIS" resourceType="SUBM"
          url="/nop/auth/pages/NopAuthDept/main.page.yaml">
    <children>
        <resource id="FNPT:NopAuthDept:query" displayName="Query Department" orderNo="10002" resourceType="FNPT">
            <permissions>NopAuthDept:query</permissions>
        </resource>
        <resource id="FNPT:NopAuthDept:update" displayName="Modify Department" orderNo="10003" resourceType="FNPT">
            <permissions>NopAuthDept:update</permissions>
        </resource>
        <resource id="FNPT:NopAuthDept:delete" displayName="Delete Department" orderNo="10004" resourceType="FNPT">
            <permissions>NopAuthDept:delete</permissions>
        </resource>
    </children>
</resource>
```

Then, the system backend can configure user roles and `NopAuthResource` mappings to control which menus users have access to, thereby determining which permissions users possess.

1. Menu items corresponding to `resourceType=SUBM`, such as the edit button in the department details page, correspond to `resourceType=FNPT`.
2. If using amis pages, you need to configure `component=AMIS` and set the URL to the page's virtual file path.

### Button Permissions
- In the frontend, menu configurations can be obtained via `SiteMap__getSiteMap?siteId=xx&includeFunctionPoints=true`, which retrieves functionality-defined menu configurations.
- Functionality points can correspond to multiple buttons (e.g., an edit button and a delete button for a department).

### Configuring Permissions Through the Interface
Do not enable operation permissions directly. Instead, use the interface to assign admin roles to users, then grant them access to specific `NopAuthResource` based on their roles.

NopAuthResource is organized by siteId. If no siteId is specified, it defaults to MAIN, which corresponds to the main website's menu. Nop-auth supports managing multiple frontend applications and their corresponding menu links.
For example:
- Using siteId=mobile for mobile端菜单
- Using siteId=MAIN for Web端等

### Adding Other Modules' Menus
Create a file at `/nop/main/auth/app.action-auth.xml` and use `x:extends` to import menus from other modules.

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<auth x:extends="/nop/auth/auth/nop-auth.action-auth.xml,/nop/sys/auth/nop-sys.action-auth.xml"
      x:schema="/nop/schema/action-auth.xdef" xmlns:x="/nop/schema/xdsl.xdef">
</auth>
```

- By default, it will use `main.action.xml` if no specific action is defined.
- You can specify additional modules by adding their paths in the `x:extends` attribute.

### Backend Actions
Use `@Auth` annotation on actions to specify which permissions or roles they require. If not specified, the action will inherit permissions from `@BizQuery`.



### Basic Usage
- If public access is allowed, set `roles="user"` to grant all users the `user` role.


### Field-level Access Control
- To control read access, configure `{BizObjName}:query`.
- To control write access, configure `{BizObjName}:mutation`.


## Example Configuration in XML
```xml
<auth permissions="NopAuthUser:delete"/>
```


- If `@Auth(roles="user")` is used, all authenticated users are assigned the `user` role.


- Methods marked with `publicAccess=true` will bypass authentication checks but still enforce data permissions.


The system uses `IActionAuthChecker` to perform authentication and permission checks.

```java
public interface IActionAuthChecker {
    boolean isPermitted(String permission, ISecurityContext context);

    default boolean isAllPermitted(Set<String> permissions, ISecurityContext context) {
        if (permissions == null || permissions.isEmpty())
            return true;

        for (String permission : permissions) {
            if (!isPermitted(permission, context))
                return false;
        }
        return true;
    }

    default boolean isPermissionSetSatisfied(MultiCsvSet permissionSet, ISecurityContext context) {
        if (permissionSet == null || permissionSet.isEmpty())
            return true;

        for (Set<String> permissions : permissionSet) {
            if (!isAllPermitted(permissions, context))
                return false;
        }
        return true;
    }
}
```

Based on the `auth` configuration, the following implementation performs permission validation:

```java
IUserContext userContext = context.getUserContext();
if (userContext == null)
    throw new IllegalStateException("nop.err.auth.no-user-context");
if (auth.getRoles() != null && !auth.getRoles().isEmpty()) {
    if (userContext.isUserInAnyRole(auth.getRoles()))
        return;
}

if (auth.getPermissions() != null && !auth.getPermissions().isEmpty()) {
    if (!checker.isPermissionSetSatisfied(auth.getPermissions(), context))
        return;
}

throw new NopException(AuthApiErrors.ERR_AUTH_NO_PERMISSION)
    .param(AuthApiErrors.ARG_ACTION_NAME, fieldName)
    .param(ARG_PERMISSION, auth.getPermissions())
    .param(ARG_ROLES, auth.getRoles())
    .param(ARG_OBJ_TYPE_NAME, objTypeName);
```

Rules for validation:

1. If `roles` is set, the user satisfies the role condition and returns `true`. The `roles` configuration is typically a comma-separated string, and any one `role` grants corresponding permissions.
2. If `permissions` are set, the user does not satisfy the permission condition and returns `false`. The `permissions` configuration type is often `multi-csv-set`, formatted as `a,b|c,d`, indicating `(a AND b) OR (c AND d)`.

In essence, the system can be configured to use either `roles` or `permissions`. If only `permissions` are configured, the system internally maps `role` and `resource` relationships to find all permissions that satisfy the condition, then checks against `roles`.

> By checking `permission`, we determine all matching `roles`. This step is independent of the user and can be unified for efficient caching.

## Data Permissions

The built-in actions `findPage`, `findList`, and `findFirst` will apply data authorization checks using the interface `IDataAuthChecker`.

Data authorization can be enabled by setting the property `nop.auth.enable-data-auth` to `true`. The default value is `true`.

---


### Data Privilege Definition File

The data privilege configuration is managed in the file located at `/nop/main/auth/app.data-auth.xml`. The `filter` section uses xpl format and outputs filter definitions. During execution, the context includes variables such as `entity` and `userContext`.

---

```xml
<data-auth>
    <objs>
        <obj name="NopSysUserVariable">
            <role-auths>
                <role-auth id="manager" roleIds="manager">
                </role-auth>

                <role-auth id="default" roleIds="user">
                    <filter>
                        <eq name="userId" value="@biz:userId"/>
                    </filter>
                </role-auth>
            </role-auths>
        </obj>
    </objs>
</data-auth>
```

---

* For different roles, distinct data privacy rules can be defined. A user will only match the highest priority rule (if multiple rules have the same priority, they are checked in order).
* Data privacy is not only applied during queries but also during `get` calls. In such cases, the corresponding `role-auth` configuration's `check` section is invoked. If no `check` is defined, it falls back to the `filter` section and compiles it into an `IEvalPredicate` interface.
* For complex filter conditions, errors may occur if they are not properly configured. In such cases, ensure that a `check` is defined.

In the `filter` section, you can define permission filters by writing expressions starting with `@biz:` (e.g., `@biz:userId`, `@biz:deptId`). All available variables are listed in [biz-var.dict.yaml](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xlang/src/main/resources/_vfs/dict/core/biz-var.dict.yaml).

---


### Dynamic Collection of Modules

The file `/nop/main/auth/app.data-auth.xml` typically contains dynamic configurations for data privacy across all modules.

```xml
<data-auth x:schema="/nop/schema/data-auth.xdef" xmlns:x="/nop/schema/xdsl.xdef"
           xmlns:auth-gen="auth-gen" xmlns:xpl="xpl">
  <x:gen-extends>
    <!-- Dynamically collect definitions from multiple modules -->
    <auth-gen:GenFromModules xpl:lib="/nop/auth/xlib/auth-gen.xlib"/>
  </x:gen-extends>
</data-auth>
```

---


### Data Privilege Definition in NopAuthRoleDataAuth Table

The data privacy configuration is merged with the settings defined in `app.data-auth.xml`.

---


#### Business Scenario Splitting

A common scenario is when the same business object requires different filters depending on the context (e.g., users can only view their own data, while admins can view all data). This effectively splits one business object into two distinct scenarios: one for querying the user's own data and another for viewing all data.

There are three possible solutions for this scenario:


Directly create a new business object, such as `MyObject_self`, which will use default xmeta and xbiz configurations.

```xml
<bean id="MyObject_self" class="xxx.MyObjectBizModel">
    <prop name="bizObjName">MyObject_self</prop>
</bean>
```

---



#### 1. Meta Configuration Override
If `MyObject_self.xmeta` is added, then `MyObject_self` will use this meta configuration; otherwise, it will fall back to the default `MyObject.xmeta`. The same applies to the `xbiz` configuration.


#### 2. Default Object Handling
If the object is not split, you can still add filter conditions in the query method.


You can include additional filter conditions in the query by adding them directly.


```xml
<query name="active_findPage" x:prototype="findPage">
    <source>
        <c:import class="io.nop.auth.api.AuthApiConstants" />

        <bo:DoFindPage query="${query}" selection="${selection}" xpl:lib="/nop/biz/xlib/bo.xlib">
            <filter>
                <eq name="status" value="${AuthApiConstants.USER_STATUS_ACTIVE}" />
            </filter>
        </bo:DoFindPage>
    </source>
</query>
```


Or implement it in Java:

```java
public PageBean<MyObject> findPage_self(@Name("query") QueryBean query, FieldSelectonBean selection, IServiceContext context) {
    return doFindPage(query, (q, ctx) -> {
        q.addFilter(FilterBeans.eq("ownerId", ctx.getUserId()));
    }, selection, context);
}
```


The second approach ensures that the `data-auth.xml` configuration is always applied to the current object. If different business scenarios require different permission configurations, you can use the `authObjName` parameter to distinguish them.

For example:
- In `CrudBizModel`, methods like `doFindPage0/doFindList0/doFindFirst0` can be adjusted using the `authObjName` parameter to apply different data permissions compared to the current object name.

By specifying a different `authObjName`, you can enable tailored data permission configurations for various business scenarios.

```
    @BizQuery
    @BizArgsNormalizer(BizConstants.BEAN_nopQueryBeanArgsNormalizer)
    @GraphQLReturn(bizObjName = BIZ_OBJ_NAME_THIS_OBJ)
    public List<T> findList(@Optional @Name("query") QueryBean query, FieldSelectionBean selection, IServiceContext context) {
        if (query != null)
            query.setDisableLogicalDelete(false);
        return doFindList(query, this::defaultPrepareQuery, selection, context);
    }

    @BizAction
    public List<T> doFindList(@Name("query") QueryBean query,
                              @Name("prepareQuery") BiConsumer<QueryBean, IServiceContext> prepareQuery,
                              FieldSelectionBean selection,
                              IServiceContext context) {
        return doFindList0(query, getBizObjName(), prepareQuery, selection, context);
    }

    @BizAction
    public List<T> doFindList0(@Name("query") QueryBean query,
                               @Name("authObjName") String authObjName,
                               @Name("prepareQuery") BiConsumer<QueryBean, IServiceContext> prepareQuery,
                               FieldSelectionBean selection,
                               IServiceContext context) {
        // Implementation omitted for brevity
    }
```


#### 4. Dynamic Role

Sometimes, the system allows dynamic assignment of roles. For example, a user might grant access to specific records in a table to another user. In such cases, the role assignments are typically defined within the system's configuration.

The `data-auth.xml` configuration file supports `role-decider`, which dynamically determines the user's role based on parameters like `authObjName`, `userContext`, and `svcCtx`.

```xml
<data-auth>
  <role-decider>
    <!-- Determines role based on authObjName, userContext, and svcCtx -->
    <!-- Returns a list of roles or comma-separated role IDs -->
  </role-decider>
</data-auth>
```

The `role-decider` component returns a set of roles that directly override the roles defined in the `IUserContext`. You can cache decision results in either `userContext` or `svcCtx`, avoiding repeated database queries.



The system's data permission configuration offers two dynamic aspects:

1. **authObjName**: Dynamically determined based on the current business object.
2. **dynamicRoles**: Different users may have different roles based on their associated business objects, which can be managed using `role-decider`.

By leveraging `role-decider`, you can dynamically compute roles for users, ensuring that permissions are applied consistently across the system.



## Overview
The `authObjectName` corresponds to different business scenarios. In a single business scenario, multiple operations may exist. The simplest case involves both `get` and `findPage/findList` methods receiving `dataAuth` restrictions. The constraints within a business scenario are consistent.

The `filter` in `dataAuth` is compiled into an `Predicate` stored in memory. This `Predicate` is applied during the `get` operation.

---



1. By default, filtering conditions for business methods should be configured in `xbiz`.
2. If filtering applies to multiple business methods (i.e., at the business scenario level), then `dataAuth` comes into play, and `authObjectName` is used to select the relevant business scenario.
3. `roleDecider` can dynamically select roles within a specified business scenario.
4. Specific configurations in `role-auth` should be executed based on `when` conditions. Only when the `when` check passes will the corresponding permission entry be selected for execution.
5. In `filter` and `check` segments, leverage the `xpl` template language for abstraction, particularly useful for handling specific scenarios and roles.

The above configurations should cover all applicable scenarios.

---


1. The `NopAuthRoleDataAuth` entity can be used to configure data access restrictions directly in the database.
2. During online configuration, to prevent security risks:
   - Use only `biz!filter.xlib` in `filter` segments. The namespace is `biz`.
   - For `whenConfig`, use only `biz!when.xlib` tags defined in the tag library.

---




- `NopAuthUser:query` allows all queries for a user.
- `NopAuthUser:findPage_active` applies only to the `findPage_active` method.
- Use `auth` annotations on actions to map method names to specific permissions (`permissionName`).

If no explicit `check` is defined, it is automatically converted into an in-memory `check`. However, if a `filter` segment contains SQL subqueries, translation errors may occur, leading to runtime failures.

---




- Use `NopAuthUser:query` for all user-related queries.
- Use `NopAuthUser:findPage_active` only for the `findPage_active` method.
- Apply `auth` annotations on actions to map method names to specific permissions (`permissionName`). If no `check` is explicitly defined, it will be automatically converted into an in-memory check.

---

