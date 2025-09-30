# connection configuration

Annotate the foreign-key key object in the Excel model with ref-connection; this will cause a connection tag to be generated on the produced one-to-many association property and automatically generate the corresponding Connection property.
For example, if the site property of NopAuthResource is annotated with ref-connection, a connection tag will automatically be added to the resources property of the NopAuthSite object.

![](ref-connection.png)

Through metaprogramming, the corresponding Connection property (e.g., resourcesConnection) will be added to the meta file at compile time. You can see the final generated property definition under the `_dump` directory

```xml

<meta>
    <props>
        <prop name="resources" displayName="资源列表" i18n-en:displayName="Resources" tagSet="pub,connection"
              ext:kind="to-many" internal="true" ext:joinLeftProp="siteId" ext:joinRightProp="siteId"
              ext:joinRightDisplayProp="displayName" insertable="false" updatable="false" lazy="true">
            <schema type="io.nop.orm.IOrmEntitySet&lt;io.nop.auth.dao.entity.NopAuthResource&gt;"
                  bizObjName="NopAuthResource"/>
        </prop>
        <!--LOC:[90:22:0:0]/nop/core/xlib/biz-gen.xlib#/_delta/default/nop/auth/model/NopAuthSite/NopAuthSite.xmeta-->
        <prop name="resourcesConnection" displayName="资源列表" internal="true"
              graphql:connectionProp="resources" graphql:queryMethod="findConnection">
            <schema type="io.nop.api.core.beans.graphql.GraphQLConnection&lt;io.nop.auth.dao.entity.NopAuthResource&gt;"
                  bizObjName="NopAuthResource"/>
        </prop>
    </props>
</meta>
```

The generated resourcesConnection node references a one-to-many association property on the entity via the graphql:connectionProp attribute, and the association’s join conditions will automatically be used for filtering.

For concrete test cases, see TestConnectionProp

resourcesConnection accepts parameters of type GraphQLConnectionInput

```java
public class GraphQLConnectionInput {
  /**
   * first表示从afterCursor开始向后取n条数据
   */
  int first;
  int last;
  String after;
  String before;

  /**
   * 如果没有设置cursor，则也可以使用offset/limit机制进行分页
   */
  long offset;
  TreeBean filter;
  List<OrderFieldBean> orderBy;
}
```

The returned result type is GraphQLConnection

```java
class GraphQLConnection<T> {

  long total;
  List<GraphQLEdgeBean> edges;

  List<T> items;

  GraphQLPageInfo pageInfo;
}

class GraphQLPageInfo {
  String startCursor;
  String endCursor;
  Boolean hasNextPage;
  Boolean hasPreviousPage;
}
```

## Apply different query conditions to the same child table to return multiple sub-collections

```graphql
query($filter1:Map, $filter2:Map){
   MyObject__get(id:3){
     activeRecords: mySubObjectConnection(filter:$filter1,limit:5){
       total
       items{
         id
       }
     }

     inactiveRecords: mySubObjectConnection(filter:$filter2, limit:5){
       total
       items{
         id
       }
     }
   }
}
```

Here, filter1 and filter2 are two different query conditions. The frontend can pass different query conditions; both query the same child-table object `mySubObject`, but the returned results differ.

## Simplify parameter passing in REST requests via `_subArgs`

```json
/r/MyObject__get?id=3&@selection=activeRecords:mySubObjectConnection,inactiveRecords:mySubObjectConnection

{
  "_subArgs.activeRecords.limit": 5,
  "_subArgs.activeRecords.filter_status": 1,
  "_subArgs.inactiveRecords.limit": 5,
  "_subArgs.inactiveRecords.filter_status": 0
}
```

After the backend GraphQLWebService receives parameters prefixed with `_subArgs.`, it converts them into function arguments targeting sub-properties, recognizes the `filter_` prefix, aggregates such specially prefixed variables, and transforms them into a FilterBean object.


## Set the `ref-query` tag for association properties in the Excel model
If the `ref-query` tag is set on a `to-one` association, then when generating the one-to-many collection property from the parent table to the child table, a `query` tag will be added. In `meta-gen.xlib`, a one-to-many property with a query tag will automatically get the configuration `graphql:findMethod="findList"`, thereby enabling pagination support for that property.

```xml
<prop name="children" graphql:findMethod="findList">

</prop>
```

On the frontend, you can pass the filter criteria and offset/limit pagination parameters

```
MyEntity__get(id:3) {
   children(filter: {...}, limit:10){
     name, status
   }
}
```

On the prop node in the meta, you can configure `graphql:maxFetchSize` to automatically cap the number of fetched records at maxFetchSize. If unspecified, it is constrained by the global maxPageSize.
<!-- SOURCE_MD5:ad0408cadd2087ee6788f82c9801d640-->
