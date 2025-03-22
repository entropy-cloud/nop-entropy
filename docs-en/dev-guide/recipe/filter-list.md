# How to Implement List Data Filtering


## Problem
The problem is to add a filter condition to the table, for example, to display only data where `type=2` or to show only data that the user has permission to see.


## Solutions
Several solutions exist:

---


## 1. Implement Filtering via Data Permissions
In the backend, built-in actions such as `findPage`, `findList`, and `findFirst` will apply data permissions using the `IDataAuthChecker` interface. Data permissions can be enabled by setting `nop.auth.enable-data-auth` to `true` (default is `true`).


## Configuration via Auth Module
Filtering can be configured through the `auth` module's data permission menu, which corresponds to the `NopAuthRoleDataAuth` table.
Configuration can also be done in the `/nop/main/auth/app.data-auth.xml` file.

```xml
<data-auth>
  <objs>
    <obj name="MyEntity">
      <role-auths>
        <role-auth roleId="manager">
          <filter>
            <eq name="type" value="1" />
          </filter>
        </role-auth>
      </role-auths>
    </obj>
  </objs>
</data-auth>
```

For detailed configuration, refer to [auth.md](../auth/auth.md).

---


In the `XView` model, you can define a `grid` with a `filter` condition. When querying data from this grid, the filter condition will automatically be included.

```xml
<grid id="list">
  <cols>
    ...
  </cols>

  <filter>
    <eq name="type" value="1" />
  </filter>
</grid>
```

**Note:** The conditions defined in the `grid` are propagated to the backend as query conditions.

---


In the `xmeta` configuration file, you can define a `filter` condition. If this filter is configured in the metadata (`meta`), then when creating or editing an entity, this filter will automatically be applied based on the configured values.

For example:

```xml
<filter>
  <eq name="type" value="1" />
</filter>
```

When adding a new entity or modifying an existing one, the `type` field will be set to `1`. If you need to create a new metadata (`meta`) for this filter, you can extend it as follows:

```xml
<meta x:extends="MyEntity.xmeta">
  <filter>
    <eq name="type" value="1" />
  </filter>
</meta>
```

In the frontend, when querying using `MyEntity_ext`, the metadata (`meta`) will automatically apply the filter. For example:

```graphql
query {
  MyEntity_ext_findPage {
    ...
  }
}
```

The filter conditions added via `meta` are appended in the backend and not visible in the frontend. These filters will be applied during creation and editing, ensuring that new entities and modified entities meet the filtering requirements.

---





## 1. Business Logic Model Class
```java
class MyEntityBizModel extends CrudBizModel<MyEntity> {
    @BizQuery
    @GraphQLReturn(bizObjName = BIZ_OBJ_NAME_THIS_OBJ)
    public PageBean<T> findPage_ext(@Name("query") @Description("@i18n:biz.query|查询条件") QueryBean query,
                                    FieldSelectionBean selection, IServiceContext context) {
        if (query != null) {
            query.setDisableLogicalDelete(false);
        }

        return doFindPage(query, this::addExtQuery, selection, context);
    }

    protected void defaultPrepareQuery(@Name("query") QueryBean query, IServiceContext context) {
        query.addFilter(FilterBeans.eq(MyEntity.PROP_NAME_status, 1));
    }
}
```


## 2. Front-end Development
In the front-end, you can inherit existing pages and customize their API request links.

```xml
<form id="edit">
  <cells>
    <cell id="productId">
      <gen-control>
        return {
            "x:extends": "xxx.page.yaml",
            initApi: {
                url: "@query:MyEntity__findPage_ext/{@gridSelection}"
            }
        }
      </gen-control>
    </cell>
  </cells>
</form>
```


You can use subqueries to filter data.

```xml
<filter>
    <eq name="type" value="${3}" />
    <filter:sql xpl:lib="/nop/core/xlib/filter.xlib">
        o.id in (
            select t.task.id from MyTask t
            where t.userId = ${ $context.userId || '1' }
        )
    </filter:sql>
</filter>
```

In this filter, `o` represents the current entity class. The entire `<filter>` block will be translated into a WHERE clause.

Translated SQL:
```sql
o.type = 3
and o.id in (
    select t.task.id from MyTask t
    where t.userId = ${ $context.userId || '1' }
)
```


The `<sql>` node's `value` attribute must be set to SQL type, not a simple text string. This is done to prevent frontend submitted filters from being injected as raw SQL queries.

In Java programs, you can construct safe SQL queries using the following approach:

```java
QueryBean query = ...;
SQL sql = SQL.begin().sql("o.id in (select t.task.id from MyTask t where t.userId = ?", userId).end();
query.addFilter(FilterBeans.assertOp("sql", sql));
```

`SQL.begin()` returns an `SqlBuilder` object that provides methods to simplify SQL statement construction.



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

* In the `xbiz` file, the configuration has higher priority than in `BizModel`. If the function name matches that of `BizModel`, it will override the Java implementation.

* `bo.xlib` provides a series of default implementations. Additional filter conditions can be added based on these defaults.

* The `xbiz` file already includes standard CRUD functions like `findPage/findList/save/update` in their parameter declarations. By inheriting existing configurations using `x:prototype="findPage"`, similar function writing can be simplified.
  - Generally, when extending standard functions, use naming rules `{extName}_{stdName}` for consistency. This allows the frontend to automatically infer parameter types and return types for GraphQL calls, thus avoiding redundant type declarations in the frontend.

* The code below is fully expanded and corresponds to the following code (can be viewed in the `_dump` directory):

```xml
<!--LOC:[49:26:0:0]/nop/core/xlib/biz-gen.xlib#/nop/auth/model/NopAuthUser/_NopAuthUser.xbiz
 @name=[6:22:0:0]/nop/auth/model/NopAuthUser/NopAuthUser.xbiz
-->
<query name="active_findPage">
    <arg name="query" type="io.nop.api.core.beans.query.QueryBean"/>
    <arg name="selection" type="io.nop.api.core.beans.FieldSelectionBean" kind="FieldSelection"/>
    <arg name="svcCtx" type="io.nop.core.context.IServiceContext" kind="ServiceContext"/>
    <return type="PageBean&lt;io.nop.auth.dao.entity.NopAuthUser&gt;"/>
<!--LOC:[8:14:0:0]/nop/auth/model/NopAuthUser/NopAuthUser.xbiz-->
    <source>
        <c:import class="io.nop.auth.api.AuthApiConstants"/>
        <bo:DoFindPage query="${query}" selection="${selection}" xpl:lib="/nop/biz/xlib/bo.xlib">
            <filter>
                <eq name="status" value="${AuthApiConstants.USER_STATUS_ACTIVE}"/>
            </filter>
        </bo:DoFindPage>
    </source>
</query>
```

## 7. Adding Filters for Sub-Entities

In `xmeta`, you can configure `graphql:queryMethod` for sub-entities, which corresponds to the `GraphQLQueryMethod` enum. The values include `findCount`, `findFirst`, `findList`, `findPage`, and `findConnection`.
- `findPage` represents a paginated query returning a `PageBean` object.
- `findConnection` refers to a paginated query returning a `GraphQLConnection` object.

> Connection is a concept in the **GraphQL Relay** framework. For detailed information, refer to [connection.md](../graphql/connection.md).



## 8. Filter main table records based on child table attributes











```xml
<prop name="myCustomFilter" queryable="true">
  <graphql:transFilter>
    <filter:sql>
      exists(
        select o2 from NopAuthResource o2 where o2.siteId = o.id
          and o2.status >= ${ filter.getAttr('value') }
      )
    </filter:sql>
  </graphql:transFilter>
</prop>
```

* The `queryable="true` attribute can be used to include this condition in the front-end query conditions.
* `graphql:transFilter` is a function. It exists in the context as a filter object corresponding to the name specified for this filter.
* `<filter:sql>` is a tag defined in `filter.xlib`. It wraps dynamically generated SQL statements as a `$type=sql` TreeBean object for database queries.

