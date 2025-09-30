# Why Is GraphQL Strictly Superior to REST in the Mathematical Sense?

GraphQL is a query language for APIs proposed by Facebook. Many see it as a replacement for REST, yet many others consider GraphQL much more complex than REST with no obvious benefits.
What unique capabilities does GraphQL possess that go beyond the scope of REST? Is there an objective, rigorous criterion that can help us make a judgment?

In the Nop platform, through rigorous mathematical reasoning, we reinterpret the positioning of GraphQL and derive new design ideas and technical solutions. Under this interpretation, the NopGraphQL engine achieves a comprehensive surpassing of REST—one can say that, in the mathematical sense, GraphQL is strictly superior to REST.

> For an introduction to NopGraphQL, see my earlier article [GraphQL Engine in a Low-Code Platform](https://zhuanlan.zhihu.com/p/589565334)

Simply put, GraphQL can be regarded as a refinement of REST oriented toward the pull mode; in a sense, it inverts the direction of information flow.

```graphql
query{
    NopAuthDept__findAll{
        name, status,children {name, status}
    }
}
```

is equivalent to `/r/NopAuthDept__findAll?@selection=name,status,children{name,status}`

After a mathematically meaningful equivalence (formal) transformation, we can clearly see that GraphQL merely augments REST with a standardized `@selection` parameter, through which the client can selectively pull parts of the response.

Traditional REST effectively has the backend push all information to the frontend; the frontend cannot finely participate in the production and transfer of that information. In the NopGraphQL implementation, if the frontend does not provide the `@selection` parameter, it automatically degrades to the traditional REST model and returns all non-lazy fields. Lazy fields must be explicitly specified to be returned to the frontend.

In the Nop platform architecture, GraphQL is not a transport protocol on the same level as REST, but rather a general mechanism for decomposing and composing information that helps organize the system’s information structure more effectively.

NopGraphQL is positioned as a general decomposition and dispatch mechanism for backend service functions. Therefore, the same service function can be exposed simultaneously as a REST service, a GraphQL service, a GRpc service, a Kafka messaging service, a Batch processing service, etc. Put simply, any scenario that receives a Request Message and returns a Response Message can directly integrate with the NopGraphQL engine and use it as its implementation mechanism.

Compared with traditional REST, using GraphQL has the following advantages:

## 1. Automatically trim the data loading scope based on request data to optimize performance

```
  @BizQuery
  public PageBean<NopAuthUser> findPage(@Name("query") QueryBean query, FieldSelectionBean selection, IServiceContext context){
          PageBean<NopAuthUser> pageBean = new PageBean();
          if(selection != null && !selection.hasField("total")){
               long total = dao.countByQuery(query);
              pageBean.setTotal(total);
          }
          ....
  }
```

If the frontend does not request the total, the computation of the total property can be skipped.

## 2. Automatic DTO adaptation by the engine greatly improves data model composability

In traditional REST services, service functions directly return DTO objects, and all properties in the DTO must be loaded within that service function. For example, if a virtual roles field is added to the NopAuthUser entity, the DTO must be extended with this field, and every place that returns NopAuthUserDTO must add logic to load the roles field.
In the NopGraphQL engine, the object returned by a service function is not directly serialized to JSON and returned to the frontend; instead, it is handed off to the GraphQL engine for further DataLoader processing to produce the final response data. As a result, it can greatly improve the composability of the backend data model. Here, composability means that once we implement A and separately implement B, we automatically obtain all A*B results without manual composition.

```java

 @BizQuery
 public List<NopAuthUser> findList(){
    ...
 }

 @BizQuery
 public NopAuthUser get(@Name("id") String id){
     ...
 }

 @BizLoader
 public List<String> roles(@ContextSource NopAuthUser user){
    ...
 }
```

In the example above, the findList and get functions only need to know how to load NopAuthUser objects; they do not need to know how NopAuthUser associates with NopAuthRole. Service functions can return entity objects directly without manual translation to DTOs.

After we add a dynamic roles property for the NopAuthUser type through the `@BizLoader` mechanism, all places in the returned results that involve NopAuthUser automatically include the roles property. The knowledge provided by DataLoader is automatically composed with our hand-written get/findList functions by the NopGraphQL engine.

In NopGraphQL, we can also use an additional xmeta meta-model file to independently control each field’s permissions, transformation, and validation logic, thereby simplifying and standardizing service function implementations.

Entity-level caching is supported on entities returned by NopORM.

```javascript
@BizLoader
public Strin getFieldA(@ContextSOurce NopAuthUser entity){
  return ((ExtFields)entity.computeIfAbsent("extFields", k-> loadExtFields(entity))).getFieldA();
}
```

For example, if we need to compute the values of several extension fields at once, we can load them in a single shot and then have each BizLoader return a cached single property. GraphQL itself has no mechanism to return multiple fields in a single loader invocation. However, through lazy loading, we can avoid calling the loader multiple times.

## 3. GraphQL’s selection capability is a useful complement to the Aggregate Root concept in DDD

In DDD (Domain-Driven Design), a crucial design for planning the information space is the concept of the Aggregate Root. An aggregate root serves as a core node in the information space. When traversing this space, instead of establishing point-to-point connections among all information nodes, we establish direct paths at a small number of core nodes and then access subordinate child information nodes via the aggregate root.

An aggregate root is a formal-level way to aggregate information. This formal aggregation reduces our cognitive and usage costs, but it can also have a negative performance impact. The aggregate root expresses all the information that can be accessed through the root object; if all of it is automatically loaded every time we obtain the root object, unnecessary resource waste is inevitable. GraphQL’s selection capability is precisely the dual of aggregation (aggregation and selection, as dual operations, should always be designed together). It allows us to selectively extract just the slices of information we need from a formally enormous information structure, perfectly meeting our business requirements.

## 4. GraphQL semantically distinguishes query and mutation, aligning better with REST’s original intent of Representational State Transfer.

Although REST defines GET and POST, due to implementation constraints (GET does not support sending data via the HTTP body, URLs have length limits and security concerns), we often cannot precisely distinguish the semantics of service functions with GET/POST.

In NopGraphQL, we stipulate that queries are side-effect-free and do not modify the database, whereas mutations have side effects and require consideration of transaction management, etc. Therefore, in the NopGraphQL framework, we do not need to manually add an `@Transactional` annotation to each service function; instead, a transactional context is uniformly established for all mutation operations. Through implementation-level optimization, if a mutation actually does not access the database, the NopGraphQL framework will not acquire a database connection.

The NopGraphQL engine decomposes the execution of backend service functions into two stages:

1. Execute the service function. If it is a mutation, a transaction is automatically established.
2. Further process the result object returned by the service function, performing data transformation and trimming via DataLoader.

After the first stage completes, the transaction is automatically closed; at this point, some of the more resource-intensive data loading has not yet started. During the second stage, execution proceeds with the transaction closed, and the OrmSession is set to read-only; if an attempt is mistakenly made to modify entity data, an exception is automatically thrown. In this way, we reduce the transaction open time and the time a database connection is held.
<!-- SOURCE_MD5:651d2de914a504a7fb29d091146b19d6-->
