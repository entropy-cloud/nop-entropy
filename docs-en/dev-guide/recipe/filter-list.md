
# How to implement filtering for list data

Problem: We want to add filter criteria to a table, such as showing only data with type=2, or showing only data the user is authorized to see.
There are multiple solutions:

## 1. Filter via data authorization

The backend built-in actions findPage, findList, and findFirst all apply the data authorization check interface IDataAuthChecker.
Enable data authorization via nop.auth.enable-data-auth, default is true.

* Configure data authorization via the data authorization menu in the auth module; it corresponds to the NopAuthRoleDataAuth table.
* Configure data authorization in the /nop/main/auth/app.data-auth.xml file.

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

For detailed data authorization configuration, see [auth.md](../auth/auth.md)

## 2. Append filter criteria on the frontend

The grid defined in the XView model can configure filter conditions; queries issued by tables generated from this grid will automatically include these filters.

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

Note that the conditions configured in the grid are propagated to the backend as frontend query conditions.

## 3. Configure filter conditions in backend XMeta objects

The xmeta configuration file can specify filter conditions. If configured in meta, create and update operations will automatically set fields according to these filters. For example:

```xml
<filter>
   <eq name="type" value="1" />
</filter>
```

Then both create and update operations will set type=1.

Based on an existing xmeta, you can create a new xmeta. For example, MyEntity_ext.xmeta

```xml
<meta x:extends="MyEntity.xmeta">
  <filter>
     <eq name="type" value="1" />
  </filter>
</meta>
```

Use the object name `MyEntity_ext` on the frontend and this meta will be applied automatically. For example:

```graphql
query{
   MyEntity_ext__findPage{
      ...
   }
}
```

Filters added in meta are appended on the backend and are invisible to the frontend. They also affect create and update operations; create and update will set values per the filters, ensuring the entity meets the filter requirements.

## 4. Add new methods in the backend BizModel

```java
class MyEntityBizModel extends CrudBizModel<MyEntity>{
    @BizQuery
    @GraphQLReturn(bizObjName = BIZ_OBJ_NAME_THIS_OBJ)
    public PageBean<T> findPage_ext(@Name("query") @Description("@i18n:biz.query|Query Conditions") QueryBean query,
                                FieldSelectionBean selection, IServiceContext context) {
        if (query != null)
            query.setDisableLogicalDelete(false);

        return doFindPage(query, this::addExtQuery, selection, context);
    }

    protected void defaultPrepareQuery(@Name("query") QueryBean query, IServiceContext context) {
        query.addFilter(FilterBeans.eq(MyEntity.PROP_NAME_status,1));
    }
}
```

On the frontend you can extend an existing page and customize the API request URL therein.

```xml
<form id="edit">
  <cells>
    <cell id="productId">
       <gen-control>
          return {
                "x:extends":"xxx.page.yaml",
                initApi: {
                   url: "@query:MyEntity__findPage_ext/{@gridSelection}"
                }
            }
       </gen-control>
    </cell>
  </cells>
</form>
```

## 5. Filter using subqueries

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

`<filter:sql>` will generate a filter of the form `<sql value="SQL" />`.

Here, `o` denotes the current entity; the entire filter block will be translated into a WHERE clause. For example, the above filter will be translated as:

```sql
o.type = 3
and o.id in (
  select t.task.id from MyTask t
  where t.userId = ${ $context.userId || '1' }
)
```

> The value attribute of the sql node must be of SQL type, not a simple text string. This convention is intended to prevent SQL injection via subqueries inserted by frontend-submitted filter conditions. Requiring value to be of SQL type means it can only be constructed programmatically on the backend.

In Java you can append subquery conditions as follows:

```java
QueryBean query= ...;
SQL sql = SQL.begin().sql("o.id in (select t.task.id from MyTask t where t.userId = ?",userId).end();
query.addFilter(FilterBeans.assertOp("sql",sql));
```

SQL.begin() returns a SqlBuilder object that provides many helper functions to simplify SQL building.

## 6. Configure filter conditions in XBiz model files

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

* Methods configured in xbiz files take precedence; if a method shares the same name as one in the Java BizModel, it will override the Java implementation.

* bo.xlib provides a series of default implementations; we can add extra filters on top of the defaults.

* Auto-generated xbiz files already include parameter declarations for standard CRUD functions like findPage/findList/save/update. By inheriting existing configurations with `x:prototype="findPage"`, we can simplify writing similar functions.
  For extensions of standard functions, we generally adopt the naming rule `{extName}_{stdName}`, allowing the frontend to automatically infer the parameter and return types for GraphQL calls, thus avoiding type declarations on the frontend.

The above code, when fully expanded, corresponds to the following (you can check under the `_dump` directory):

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

## 7. Add filter conditions to child-table properties

In xmeta you can configure `graphql:queryMethod` for child-table properties; its value corresponds to the GraphqlQLQueryMethod enum, including findCount/findFirst/findList/findPage/findConnection.
Among them, findPage indicates a paginated query returning a PageBean object, while findConnection indicates a paginated query returning a GraphQLConnection object.

> Connection is a concept in the GraphQL Relay framework; for details, see [connection.md](../graphql/connection.md)

A prop specified with `graphql:queryMethod` supports pagination and sorting criteria; the concrete type corresponds to `GraphQLConnectionInput`.

```xml
<prop name="resourcesConnection" graphql:queryMethod="findConnection">
  <schema bizObjName="NopAuthResource" />

  <graphql:filter>
     <eq name="siteId" value="@prop-ref:siteId" />
  </graphql:filter>

  <graphql:orderBy>
     <field name="displayName" desc="false" />
  </graphql:orderBy>
</prop>
```

Use `graphql:filter` and `graphql:orderBy` to specify filtering and sorting for child-table queries. In filter conditions, the form `@prop-ref:{propName}` denotes referencing a property of the current entity.
With this syntax we bypass the ORM engine to implement associated queries between arbitrary entities.

If the ORM engine has already defined an associated object property, we can simplify by using `graphql:connectionProp` to specify the corresponding ORM property and obtain its association condition definition to replace `graphql:filter`.

```xml
<prop name="resourcesConnection" graphql:queryMethod="findConnection" graphql:connectionProp="resources" />
```

## 8. Filter master records based on child-table properties

QueryBean provides the transformFilter function, which can structurally transform frontend-submitted query conditions, for example, converting the condition of the myCustomFilter field into a subquery condition.

In XMeta you can define the transformation logic via the `graphql:transFilter` child node of the prop node.

```xml
  <prop name="myCustomFilter" queryable="true">
      <graphql:transFilter>
          <filter:sql>
              exists( select o2 from NopAuthResource o2 where o2.siteId= o.id
                and o2.status >= ${ filter.getAttr('value') }
              )
          </filter:sql>
      </graphql:transFilter>
  </prop>
```

* A property with `queryable=true` can be used in filter conditions submitted by the frontend. The field does not need to be an actual entity attribute.
* `graphql:transFilter` is a function; the context contains a filter object, which corresponds to a TreeBean whose name is the specified property.
* `<filter:sql>` is a tag defined in `filter.xlib`; it can wrap a dynamically generated SQL statement into a TreeBean with `$type=sql`, used as a database query condition.

<!-- SOURCE_MD5:a90a3590137058325b029cbd4d8b6bd8-->
