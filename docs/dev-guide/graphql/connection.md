# connection配置

在Excel模型的外键关键对象上标注ref-connection，这样生成的一对多关联属性上会生成connection标签，并自动生成对应的Connection属性。
例如NopAuthResource的site属性上标注ref-connection，则会自动在NopAuthSite对象的resources属性上增加connection标签。

![](ref-connection.png)

通过元编程机制会在编译期为meta文件增加对应的Connection属性，例如resourcesConnection。在`_dump`目录下可以看到最后生成的属性定义

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

生成的resourcesConnection节点上通过graphql:connectionProp属性引用实体上的一个一对多关联属性，会自动使用这个关联属性对应的关联条件进行过滤。

具体测试用例可以参见 TestConnectionProp

resourcesConnection可以接收的参数为GraphQLConnectionInput类型

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

返回的结果类型为GraphQLConnection类型

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

## 对同一个子表应用不同的查询条件返回多个子集合

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

这里的filter1和filter2是两个不同的查询条件，可以在前端传入不同的查询条件，都是查询同一个子表对象`mySubObject`，但是返回的结果是不同的。

## REST请求时通过`_subArgs`简化传参方式

```json
/r/MyObject__get?id=3&@selection=activeRecords:mySubObjectConnection,inactiveRecords:mySubObjectConnection

{
  "_subArgs.activeRecords.limit": 5,
  "_subArgs.activeRecords.filter_status": 1,
  "_subArgs.inactiveRecords.limit": 5,
  "_subArgs.inactiveRecords.filter_status": 0
}
```

后台GraphQLWebService接收到`_subArgs.`为前缀的参数之后会把它们转换为针对子属性的函数参数，并识别`filter_`前缀，将特殊前缀的变量收集在一起，转换为FilterBean对象。
