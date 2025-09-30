# Permissions

## Enabling Permissions

* Set `nop.auth.enable-action-auth=true` to enable action permissions. Field-level permissions also use this switch.
* Set `nop.auth.enable-data-auth=true` to enable data permissions.
* Set `nop.auth.use-data-auth-table=true` to enable the data-permission configuration table `NopAuthRoleDataAuth`; data-permission rules configured in the database can be merged with the rules in configuration files.
* By default, the static permission configuration file /nop/main/auth/app.action-auth.xml is loaded; you can customize it via `nop.auth.site-map.static-config-path`.
* In main.action-auth.xml you can include existing permission configuration files via `x:extends`.
* If `nop.auth.skip-check-for-admin=true` is set, operation permission checks are skipped for users with the admin role or `nop-admin`; the default is true.
* Set `nop.auth.service-public=true` to expose backend services so service functions can be accessed without login.
* Set `nop.auth.quarkus-dev-public=true` to expose Quarkus dev pages so `/q/graphql-ui` and similar pages are accessible without login.

> When the platform starts in debug mode, it prints all known configuration variables and their locations.

## Core Interfaces

1. `IUserContext`: Stores `userId`, `roles`, and other identity and permission-related information. Access via `IUserContext.set()/get()`.
2. `IActionAuthChecker`: Checks action permissions and field permissions. Invoked for each GraphQL field in `GraphQLExecutor`.
3. `IDataAuthChecker`: Checks data permissions. In `CrudBizModel`, it automatically appends permission filters to `Query` objects and executes `check` after each entity is retrieved.
4. Login verification is implemented in the `AuthHttpServerFilter` class via the `ILoginService` interface.
5. The `ILoginService` interface handles all logic for sign-in/sign-out and token validation. The platform includes two implementations: `LoginServiceImpl` and `OAuthLoginServiceImpl`.

## User Roles

* All logged-in users automatically have the user role; you don't need to explicitly assign it.
* Roles have an `isPrimary` property; a user has only one primary role, which serves a similar purpose to a position/post.
* Roles have associated child-role sets: once a role is assigned to a user, all associated child roles are automatically assigned as well. This provides a role grouping mechanism and simplifies configuration.
* By default, operation permissions are not checked for the admin role; you can re-enable checks via the switch (`nop.auth.skip-check-for-admin`).

Using the concept of associated child roles, you can achieve a kind of dynamic permission assignment. For example, assign a role `accessDeptData` to the `user` role, and define data permissions and action permissions for `accessDeptData`; then all users automatically gain the related capabilities.

## Action Permissions

### Action Permission Configuration

You can configure action permissions via `action-auth.xml` and the backend object `NopAuthResource`. `resource` types are `TOPM`, `SUBM`, and `FNPT`, corresponding to top-level menus, submenus, and function points. Function points can be annotated with corresponding `permissions`.

```xml

<resource id="NopAuthDept-main" displayName="部门" orderNo="10001" i18n-en:displayName="Department"
          icon="ant-design:appstore-twotone" component="AMIS" resourceType="SUBM"
          url="/nop/auth/pages/NopAuthDept/main.page.yaml">
    <children>
        <resource id="FNPT:NopAuthDept:query" displayName="查询部门" orderNo="10002" resourceType="FNPT">
            <permissions>NopAuthDept:query</permissions>
        </resource>
        <resource id="FNPT:NopAuthDept:update" displayName="修改部门" orderNo="10003" resourceType="FNPT">
            <permissions>NopAuthDept:update</permissions>
        </resource>
        <resource id="FNPT:NopAuthDept:delete" displayName="删除部门" orderNo="10004" resourceType="FNPT">
            <permissions>NopAuthDept:delete</permissions>
        </resource>
    </children>
</resource>
```

The backend can then configure the mapping between user roles and `NopAuthResource` to control which menus users can access, and infer from that which `permission`s users have.

1. Menu items correspond to `resourceType=SUBM`; specific function points on a page (e.g., an edit button) correspond to `resourceType=FNPT`.
2. If you use AMIS pages, set `component=AMIS`, and `url` to the page’s virtual file path.

### Button Permissions
* On the frontend, you can fetch the menu configuration including function point definitions via `SiteMap__getSiteMap?siteId=xx&includeFunctionPoints=true`.
* A function point can map to frontend buttons (multiple buttons can map to one function point).

### Configure Permissions via the UI

First, do not enable action permissions; use the UI to create the `admin` role and assign it to the designated user; then enable action permissions. Users with the `admin` role can assign roles to others and specify which `NopAuthResource` each role can access.

`NopAuthResource` is organized by `siteId`; by default, `siteId=MAIN` is used as the primary site’s menu. `nop-auth` supports managing menus for multiple frontend applications simultaneously. For example, `siteId=mobile` for mobile menus, and `siteId=MAIN` for the web, etc.

### Include Menus from Other Modules
Create a file `/nop/main/auth/app.action-auth.xml`; within it you can include menus from other modules via `x:extends`.

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<auth x:extends="/nop/auth/auth/nop-auth.action-auth.xml,/nop/sys/auth/nop-sys.action-auth.xml"
      x:schema="/nop/schema/action-auth.xdef" xmlns:x="/nop/schema/xdsl.xdef">
</auth>
```

* Use `nop.auth.site-map.static-config-path` to specify another `action-auth.xml` file; by default, `main.action.xml` is used.

* `SiteMapProviderImpl` caches the mapping from `siteId` to `SiteMapBean`. The cached `SiteMapBean` contains all menu items for the site. There is also a `SiteMapData` cache that holds the set of role IDs associated with each menu item. When a user logs in and requests menus, `SiteMapProviderImpl.filterAllowedMenu` combines the SiteMap cache, the SiteCacheData cache, and the user’s role set to filter out menu items the user is not authorized to access, returning a `SiteMapBean` tailored to the user.
* In the Nop platform, a user can have multiple roles.

### Backend Actions

Use the `@Auth` annotation on action functions to specify required `permissions` or allowed `roles`. If not specified, based on whether it is `@BizQuery` or `@BizMutation`, permissions are automatically set to `{BizObjName}:{actionName}|{BizObjName}:query` for `@BizQuery`, and `{BizObjName}:{actionName}|{BizObjName}:mutation` for `@BizMutation`.

* If access should be open to everyone, configure `roles="user"`; all logged-in users have the user role.

During permission assignment, if all read operations are allowed, configure `{BizObjName}:query` so you don't need to specify each `{actionName}` individually.

Example

```javascript
@Auth(permissions="delete")
@BizMutation
public boolean delete(@Name("id") @Description("@i18n:biz.id|对象的主键标识") String id, IServiceContext context) {
    return super.delete(id, context);
}
```

The full format of `permission` is `bizObjName:action`. If only the action part is written, the `bizObjName` prefix is automatically added. For example, `permissions="delete"` above is ultimately converted to something like `NopAuthUser:delete`.

If an action is not annotated with `@Auth`, the default corresponding `permission` is `{bizObjName}:mutation` or `{bizObjName}:query`.

In the xbiz file you can set `auth` for the corresponding action; it overrides the permission settings introduced via `@Auth` on the Java method with the same name. For example

```xml

<mutation name="delete">
    <auth permissions="delete"/>
</mutation>
```

### Properties on the Result Object

In the xmeta file, you can specify `auth` for a `prop`

```xml

<prop name="xx">
    <auth permissions="NopAuthUser:query" roles="admin" for="read"/>
    <auth permissions="NopAuthUser:mutation" roles="hr" for="write"/>
</prop>
```

Through this configuration you can implement field-level read/write permission control. `for="read"` controls field read permission, `for="write"` controls field write permission, and `for="all"` allows both read and write.

`auth` can be configured with `skipWhenNoAuth=true`, meaning the field is automatically ignored when access is not permitted, rather than throwing an error.

### Public Access

If the `@Auth` annotation or the `auth` configuration in xbiz specifies `publicAccess=true`, the method is publicly accessible and will automatically skip action permission checks. Data permissions still apply.

All users automatically have the `user` role, so configuring `@Auth(roles="user")` allows all logged-in users to access. The difference from `publicAccess` is that methods marked `publicAccess` do not check whether the current user is logged in.

* Note: By default, the `/r/` and `/graphql` endpoints themselves require login, so you also need to set `nop.auth.service-public=true` so all service paths allow anonymous access. In this case, you must enable permission authentication and rely on `IActionAuthChecker` for security. See the `nopAuthFilterConfig` in `auth-service.beans.xml` for details.

## Action Permission Checking Interface

The system uses the `IActionAuthChecker` interface to check action permissions.

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
            if (isAllPermitted(permissions, context))
                return true;
        }
        return false;
    }
}
```

The implementation of permission checks based on `auth` configuration is as follows:

```java
IUserContext userContext = context.getUserContext();
if (userContext == null)
   throw new IllegalStateException("nop.err.auth.no-user-context");
if (auth.getRoles() != null && !auth.getRoles().isEmpty()) {
   if (userContext.isUserInAnyRole(auth.getRoles()))
       return;
}

if (auth.getPermissions() != null && !auth.getPermissions().isEmpty()) {
   if (checker.isPermissionSetSatisfied(auth.getPermissions(), context))
       return;
}

 throw new NopException(AuthApiErrors.ERR_AUTH_NO_PERMISSION)
           .param(AuthApiErrors.ARG_ACTION_NAME, fieldName)
           .param(ARG_PERMISSION, auth.getPermissions())
           .param(ARG_ROLES, auth.getRoles())
           .param(ARG_OBJ_TYPE_NAME, objTypeName);

```

Rules:

1. If `roles` is set, returning `true` if the role condition is satisfied. `roles` is typically a comma-separated string; having any one `role` grants the corresponding permission.
2. If `permissions` is set, returning `false` if the permission condition is not satisfied. `permissions` is typically of type `multi-csv-set`, formatted as `a,b|c,d`, meaning `(a AND b) OR (c AND d)`.

That is, you can configure only `roles`, or only `permissions`. If only `permissions` is configured, the internal implementation maps between `role` and `resource` to find all `role`s that satisfy the `permission` condition, and then performs permission checks according to the `roles` condition.

> Finding all `role`s from a `permission` does not depend on a specific user; it can be computed centrally and the mapping cached.

## Data Permissions

The built-in `findPage`, `findList`, and `findFirst` actions all apply the data-permission checking interface `IDataAuthChecker`.
Enable data permissions via `nop.auth.enable-data-auth`; the default is `true`.

### Data Permission Definition File

Configure data permissions in the `/nop/main/auth/app.data-auth.xml` file. The `filter` section uses xpl format and outputs a `filter` definition node. During xpl execution the context has variables such as `entity` and `userContext`.

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

* Different roles can have different data-permission rules. A user matches only one rule—the highest-priority rule (if multiple rules share the same priority, they are checked in sequence based on whether the user has the specified role).
* Data permissions apply not only to queries; on `get` calls the corresponding data permissions are checked. In that case, the `check` section in the `role-auth` configuration is invoked. If no `check` is configured, the `filter` is automatically compiled into an `IEvalPredicate`. For complex filter conditions, an error may be raised indicating the operation is not supported; in such cases you must define `check`.

In the `filter` section, you can write permission filter conditions; the `value` part can use expression variables with the `@biz:` prefix, such as `@biz:userId` or `@biz:deptId`.
All available variables are defined in [biz-var.dict.yaml](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xlang/src/main/resources/_vfs/dict/core/biz-var.dict.yaml).

Generally, the `/nop/main/auth/app.data-auth.xml` file can be configured to dynamically collect data-auth configurations across all modules

```xml
<data-auth x:schema="/nop/schema/data-auth.xdef" xmlns:x="/nop/schema/xdsl.xdef"
           xmlns:auth-gen="auth-gen" xmlns:xpl="xpl">
  <x:gen-extends>
    <!-- Automatically collect data-permission definitions from each module -->
    <auth-gen:GenFromModules xpl:lib="/nop/auth/xlib/auth-gen.xlib"/>
  </x:gen-extends>
</data-auth>
```

### Define Data Permissions via the NopAuthRoleDataAuth Table

Data permissions defined in the database are merged with the permissions defined in the `data-auth.xml` configuration file.

### Splitting Business Scenarios

It is common for the same business object to require different filter conditions in different business scenarios; for example, everyone can query their own data, while `admin` can query everyone’s data, but `admin` still needs a page to query their own data. Essentially, the same business object splits into two business scenarios: one to query the owner’s data and one to query all data. There are three possible solutions:

#### 1. Split the Object
   Create a new business object, e.g., `MyObject_self`; it will automatically use the default xmeta model and xbiz configuration.

```
<bean id="MyObject_self" class="xxx.MyObjectBizModel" >
  <prop name="bizObjName" value="MyObject_self" />
</bean>
```

If you add `MyObject_self.xmeta`, then `MyObject_self` will use this meta configuration; otherwise it falls back to `MyObject.xmeta`. The same applies to the xbiz configuration.

> This default-model detection logic is implemented in the `BizObjectBuilder.java` class.

After splitting the object, you can configure different data-permission filters. You can also restrict filters directly via meta-level filters.

#### 2. If you don't split the object, add filter conditions in the query method

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

Or implement it in Java

```
public PageBean<MyObject> findPage_self(@Name("query")QueryBean query, FieldSelectonBean selection, IServiceContext context){
  return doFindPage(query, (q,ctx)->{
     q.addFilter(FilterBeans.eq("ownerId", ctx.getUserId());
  }, selection, context);
}
```

#### 3. Switch data-permission configuration via authObjName
   The second approach above causes `data-auth.xml` to always apply to the current object. If different business scenarios need different permission configurations, you can use the `authObjName` parameter to distinguish them.

`CrudBizModel`’s `doFindPage0`/`doFindList0`/`doFindFirst0` methods can specify a permission object name via the `authObjName` parameter that differs from the current object name, thereby enabling different data-permission configurations.

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
                               IServiceContext context){
        ...
    }
```

The built-in `findList` uses `doFindList`, and `doFindList` actually uses `doFindList0`, passing the current business object name as `authObjName`. Passing a different `authObjName` enables different data-permission filters.

#### 4. Dynamic Roles

Sometimes the system involves dynamic grant scenarios. For example, a person might open certain records in a table to specified users. Typically, such special permissions are clearly tied to specific business scenarios and can be implemented in xbiz by dynamically generating filters. If these cases are numerous or the rules are consistent and you don’t want to author them in each object, you can handle them uniformly at the data-permission layer.

The `data-auth.xml` configuration supports `role-decider`, which can dynamically determine the set of roles for the current user, thereby selecting different filters.

```xml
<data-auth>
  <role-decider>
     // Dynamically determine roles based on authObjName, userContext, svcCtx, etc. Return a set of role IDs or a comma-separated list of role IDs.
  </role-decider>
</data-auth>
```

* The role IDs returned by `role-decider` directly override the role settings on `IUserContext`.
* You can cache some dynamic decision results in `userContext` or `svcCtx` to avoid repeated database queries, etc. `userContext` cache is at the user session level, while `svcCtx` cache is at the request level.

Data-permission configuration provides two types of dynamism,
1. `authObjName`, determined dynamically by your business
2. `dynamicRoles`: different users can have different role sets for different business objects, computed dynamically via `role-decider`.

`authObjName` corresponds to different business scenarios; within a business scenario there are multiple operations. Simplest: `get` and `findPage`/`findList` are subject to data-auth constraints; a single business scenario shares the same constraints. The data-auth `filter` is compiled into an in-memory `Predicate` and is also applied on `get`.

1. Permission filter conditions at the business-method level should be configured in xbiz.
2. If they are cross-cutting across multiple business methods, i.e., at the business-scene level, then use data-auth and choose the business scenario via `authObjName`.
3. Use `role-decider` to dynamically select roles within a specified business scenario.
4. In the specific `role-auth` configuration, execute the `when` condition; only if `when` passes, the permission entry is selected.
5. In `filter` and `check` sections you can leverage the abstraction capabilities of the xpl template language to handle more dynamic permission filtering needs for specific scenarios and roles.

These cases should cover all application scenarios.

* You can configure data permissions online via the database entity `NopAuthRoleDataAuth`.
* To avoid security issues when configuring online, the `filter` section can only use `biz!filter.xlib` (namespace is `biz`). `whenConfig` can only use tags defined in the `biz!when.xlib` tag library.
* `whenConfig` can directly configure tag names, e.g., `biz:WhenAdmin` or `<biz:WhenXX type='1' />`

**Note: Data permissions apply to business scenarios, so they affect both `get` and `findXX` functions; on `get` calls the `check` configuration is executed. If no explicit `check` is provided, the `filter` is automatically translated into an in-memory check. Therefore, if the `filter` uses SQL subqueries, translation may fail and throw an error.**

## Frequently Asked Questions
1. How to distinguish two different queries
`NopAuthUser:query` means all queries against the user object are allowed, while `NopAuthUser:findPage_active` corresponds only to the method `findPage_active`. You can use the `Auth` annotation on actions to map the method name to a specific permission. Otherwise, each method name defaults to a permission name.

2. How to obtain `userId`, `userName`, `role`, etc.
`IUserContext.get()` retrieves the current user context. Backend service functions generally receive an `IServiceContext` object; you can also get the user context via `svcCtx.getUserContext()`.

```java
class MyBizModel{
  @BizQuery
  public MyObject get(@Name("id") String id, IServiceContext svcCtx){
     return ...;
  }
}
```

In the reporting engine, you can pass parameters via `IEvalScope`. The `scope` on `svcCtx` is obtained via `svcCtx.getEvalScope()`; it already has the variable `svcCtx`, pointing to its associated `IServiceContext` object.

<!-- SOURCE_MD5:d81d3e933891e2cfd870b7a2f794827e-->
