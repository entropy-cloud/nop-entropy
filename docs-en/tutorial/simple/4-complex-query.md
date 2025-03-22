# Nop Basics: How to Implement Complex Queries

Bilibili Video: [https://www.bilibili.com/video/BV1c1421i72k/](https://www.bilibili.com/video/BV1c1421i72k/)

The process of executing queries in the Nop platform consists of three main parts:

1. Locate the service object
2. Call the service method on the service object
3. Process the returned results

In each of these parts, you can insert filter and sorting conditions.

For example, the query link `/r/NopAuthSite__findList?@selection=id,resources{resoureName}` maps to the `findList` method of the `NopAuthSite` object. This allows you to specify which fields to retrieve based on the selection criteria.

[!](images/complex-query.png)

## Section 1: Filter and Sorting Conditions at the Object Level

You can add filter and ordering segments in the XMeta metadata model file. All operations related to this service object will automatically include these conditions, especially when adding or modifying records.

For example:
```xml
<meta>
  <filter>
    <eq name="siteId" value="main"/>
  </filter>
</meta>
```

This mechanism can be used to store multiple business entities in a single database table, even if they have different attributes. For instance, in inventory management, each product may have unique attributes that require separate XMeta files for customization.

Additionally, different object names can map to different data permission configurations:
```xml
<data-auth x:schema="/nop/schema/data-auth.xdef" xmlns:x="/nop/schema/xdsl.xdef">
  <objs>
    <obj name="NopAuthUser">
      <role-auths>
        <role-auth roleId="nop-admin"/>
        <role-auth roleId="user">
          <filter>
            <eq name="tenantId" value="${$context.tenantId}"/>
          </filter>
        </role-auth>
      </role-auths>
    </obj>
  </objs>
</data-auth>
```

You can choose to inherit from an existing XMeta model by selecting "Inherit" in the `app.beans.xml` file:
```xml
<bean id="NopAuthResourceBizModel_main" class="io.nop.auth.service.entity.NopAuthResourceBizModel">
  <property name="bizObjName" value="NopAuthResource_main"/>
</bean>
```

If you prefer to extend an existing model, the `NopAuthResource_main.xmeta` file can be set to inherit from `NopAuthResource.xmeta`:
```xml
<meta x:extends="NopAuthResource.xmeta">
  ...
</meta>
```

## Section 2: Adding Filters and Sorting Conditions in XBiz Models

In XBiz models, you can add filter and sorting conditions using the `<xpl>...</xpl>` tags. Adding filters to XMeta will affect the `save` and `update` functions. If you only want to apply specific filters to a particular query request, such as retrieving active records, you can configure this in the XBiz model.

For example:
```xml
<bean id="NopAuthResourceBizModel_main" class="io.nop.auth.service.entity.NopAuthResourceBizModel">
  <property name="bizObjName" value="NopAuthResource_main"/>
</bean>
```

```xml
<biz x:schema="/nop/schema/biz/xbiz.xdef" xmlns:x="/nop/schema/xdsl.xdef"
     x:extends="_NopAuthUser.xbiz" xmlns:bo="bo" xmlns:c="c">

  <actions>
    <query name="active_findPage" x:prototype="findPage">
      <source>
        <c:import class="io.nop.auth.api.AuthApiConstants"/>

        <bo:DoFindPage query="${query}" selection="${selection}" xpl:lib="/nop/biz/xlib/bo.xlib">
          <filter>
            <eq name="status" value="${AuthApiConstants.USER_STATUS_ACTIVE}"/>
          </filter>
        </bo:DoFindPage>
      </source>
    </query>
  </actions>
</biz>
```

* `x:prototype` indicates inheritance of input parameters and return type definitions from the existing `findPage` function.
* `bo.xlib` provides encapsulation for helper functions like `doFindPage/doUpdate`, allowing additional logic to be passed when these functions are called.  
* `<bo:DoFindPage>` utilizes the xpl template language's wrapping capability, offering a straightforward filter configuration method.

The `xbiz` model file can be considered as an XML configuration file, which can be effectively designed using a visual designer and leveraged for dynamic loading in the Nop platform.

Further details are available in [filter.md](../../dev-guide/recipe/filter-list.md).

## 3. Adding Related Query Configuration in XMeta

In the Nop platform, service function return values are not directly serialized into JSON to be delivered to the frontend but undergo processing through the NopGraphQL engine's result mapping. Complex data fetching logic can be executed during this process using `DataFetcher`.

When additional filtering and sorting conditions are required for subtable records, they can be configured in the `XMeta`'s `prop` node by setting the `graphql:queryMethod` property, enabling the use of `OrmEntityConnectionFetcher` for subtable filtering and sorting.

```xml
<meta x:schema="/nop/schema/xmeta.xdef" xmlns:x="/nop/schema/xdsl.xdef" x:extends="_NopAuthSite.xmeta"
      xmlns:graphql="graphql">
  <props>
    <prop name="resourcesList" displayName="资源列表"
          graphql:queryMethod="findList" lazy="true">
      <schema bizObjName="NopAuthResource"/>

      <graphql:filter>
        <eq name="siteId" value="@prop-ref:siteId"/>
      </graphql:filter>

      <graphql:orderBy>
        <field name="orderNo" desc="false"/>
      </graphql:orderBy>
    </prop>
  </props>
</meta>
```

* `graphql:queryMethod` supports various methods like `findCount`, `findFirst`, `findList`, `findPage`, and `findConnection`. Each method corresponds to different result types, but all input types are of the form `GraphQLConnectionInput`.
* The `bizObjName` attribute specifies the associated business object.
* No ORM-level relationship is required between the current entity and its related entities. Filtering for related queries can be achieved using `<graphql:filter>`.  
* For logical deletion, if enabled by default, all queries will automatically include a logical deletion filter. This behavior can be modified by setting `graphql:disableLogicalDelete` to `true`.




When performing association queries, certain conditions may be skipped due to logical deletion. To ensure that only active records are retrieved, you can apply filtering conditions.

For example:
1. If you want to retrieve the count of sub-entities, you can use `graphql:queryMethod="findCount"` or `graphql:queryMethod="findPage"`.
2. When using `findConnection`, you can fetch the total number of records by accessing the `total` attribute from the returned `PageBean`.



If your ORM supports association properties, the above configurations can be simplified.



In REST mode, you can pass sub-table filtering conditions in the format:
```http
http://localhost:8080/r/NopAuthSite__get?id=main&%40selection=id,displayName,resourcesList%7Bitems%7Bid,displayName%7D%7D&_subArgs.resourcesList.filter_status=1
```



To map fields similar to GraphQL, you can use `@selection`. Note that special characters like `@` and `{}` require proper encoding to avoid URL parsing issues.

For more complex conditions:
```graphql
{
  query($filter: Map) {
    NopAuthSite_get(id: "main") {
      id
      displayName
      resourcesList(filter: $filter, limit: 10, offset: 0) {
        items {
          id
          displayName
        }
      }
    }
  }
}

variables:
  filter: {
    "$type": "or",
    "$body": [
      { "$type": "eq", "status": 1 },
      { "$type": "eq", "status": 2 }
    ]
  }
```



By leveraging GraphQL aliases, you can return different query results from the same association field. This allows for flexible querying based on your needs.

```graphql
query($filter1: Map, $filter2: Map) {
    NopAuthSite_get(id: "main") {
        id
        displayName
        activeResources: resourcesList(filter: $filter1, limit: 10, offset: 0) {
            items {
                id
                displayName
            }
        }
        inactiveResources: resourcesList(filter: $filter2, limit: 10, offset: 0) {
            items {
                id
                displayName
            }
        }
    }
}
```

## 4. Filtering Main Table Records Based on Subtable Attributes

You can add a custom field to XMeta and use `graphql:transFilter` to translate the custom field condition into the corresponding SQL query for subtable queries.

The implementation principle is to utilize the `transformFilter` function provided by QueryBean to structure-transform the filter conditions submitted from the frontend.

For example, for a custom filter like `/r/NopAuthSite__findPage?filter__myCustomFilter=1`, you can define the transformation logic in XMeta using the `graphql:transFilter` child node under the `prop` node.

### Example of Custom Filter Configuration

```xml
<prop name="myCustomFilter" queryable="true">
    <graphql:transFilter>
        <filter:sql>
            exists(select o2 from NopAuthResource o2 where o2.siteId = o.id and o2.status >= ${filter.getAttr('value')})
        </filter:sql>
    </graphql:transFilter>
</prop>
```

* If `queryable="true"`, the filter condition can be passed through from the frontend.
* `graphql:transFilter` is a function that maps the custom field condition to a subtable query structure.
* `<filter:sql>` defines the SQL statement generated based on the transformed filter.

The translated EQL (Enhanced Query Language) becomes:

```sql
select o
from NopAuthSite o
where
    exists(select o2 from NopAuthResource o2 where o2.siteId = o.id and o2.status >= 1)
```

### Advanced Features

- The type of `graphql:transFilter` is `xpl-fn`, which requires the return type to be `XNode`. To output `XNode`, you need to use an appropriate node wrapper, such as setting `outputMode`.
- You can further configure `allowFilterOp` to support multiple operations (`op` types). The value of `filter.tagName` determines which operation is used.

### Frontend Configuration for Left Join

When passing parameters from the frontend, you can specify which attributes to join using `leftJoinProps`. These properties are defined in the root node of your meta file and are prefixed with `biz:allowLeftJoinProps`.

For safety reasons, only specified attributes are allowed to be joined. These attributes are defined in your meta file's root node using `biz:allowLeftJoinProps`.

以下是英文翻译：

If the value is specified as `*`, it allows all related object properties to be placed in the `leftJoinProps` collection.

When loading sub-table objects in list queries, the platform automatically enables the `BatchLoader` mechanism to optimize loading and avoid the n+1 problem. The approach is to first load the main table, then batch load the sub-tables. Both `to-one` and `to-many` relationships in the ORM have been optimized for loading. However, queries using `graphql:queryMethod="findList"` where the method explicitly specifies association conditions are still not optimized.

### Grouped Aggregation Query

Methods in `IEntityDao` and `CrudBizModel` require returning entity objects, so direct querying from the frontend using `QueryBean` for grouped aggregation is not supported. However, you can create your own service methods on the backend to call `IOrmTemplate.findListByQuery` for grouped aggregation. Use `QueryBean`'s `limit` and `offset` properties for pagination. If `limit` is not set, it will query all records.

### Complex Parent-Child Relationship Query
For more complex parent-child relationships, refer to [mdx-query.md](../../dev-guide/orm/mdx-query.md).

### Specifying Field Order in Query Conditions
You can use syntax similar to SQL ordering to specify the order of fields in query conditions. The `QueryBeanArgsNormalizer` will recognize this parameter and normalize it into the `orderBy` property of the query object, resolving as a list of `OrderByBean` objects.

```markdown
query_orderBy=name asc, status desc
```

