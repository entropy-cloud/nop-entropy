# connection配置

在Excel模型的外键关键对象上标注ref-connection，这样生成的一对多关联属性上会生成connection标签，并自动生成对应的Connection属性。
例如NopAuthResource的site属性上标注ref-connection，则会自动在NopAuthSite对象的resources属性上增加connection标签。

![](ref-connection.png)

通过元编程机制会在编译期为meta文件增加对应的Connection属性，例如resourcesConnection。在\_dump目录下可以看到最后生成的属性定义

```xml

<meta>
    <prop name="resources" displayName="资源列表" i18n-en:displayName="Resources" tagSet="pub,connection"
          ext:kind="to-many" internal="true" ext:joinLeftProp="siteId" ext:joinRightProp="siteId"
          ext:joinRightDisplayProp="displayName" insertable="false" updatable="false" lazy="true">
        <schema type="io.nop.orm.IOrmEntitySet&lt;io.nop.auth.dao.entity.NopAuthResource&gt;"
                bizObjName="NopAuthResource"/>
    </prop>
    <!--LOC:[90:22:0:0]/nop/core/xlib/biz-gen.xlib#/_delta/default/nop/auth/model/NopAuthSite/NopAuthSite.xmeta-->
    <prop name="resourcesConnection" displayName="资源列表" internal="true" graphql:connectionProp="resources">
        <graphql:inputType>io.nop.api.core.beans.graphql.GraphQLConnectionInput</graphql:inputType>
        <schema type="io.nop.api.core.beans.graphql.GraphQLConnection&lt;io.nop.auth.dao.entity.NopAuthResource&gt;"
                bizObjName="NopAuthResource"/>
    </prop>
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
