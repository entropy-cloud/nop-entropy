# Why Does GraphQL Strictly Outperform REST in the Mathematical Sense?

GraphQL is a query language developed by Facebook for APIs. Many consider it a replacement for REST, while others find it more complex with no obvious benefits. What unique capabilities does GraphQL have that surpass those of REST? Is there an objective and rigorous evaluation standard to help us make this judgment?

In the Nop platform, through rigorous mathematical reasoning, we have reinterpreted the positioning of GraphQL, leading to new design ideas and implementation schemes. Under this reinterpretation, the NopGraphQL engine surpasses REST in a comprehensive manner. We can say that, from a mathematical perspective, GraphQL strictly outperforms REST.

> For more information about NopGraphQL, please refer to my previous article [GraphQL Engine in Low-Code Platforms](https://zhuanlan.zhihu.com/p/589565334).

Simply put, **GraphQL can be seen as an improvement over REST that operates in pull mode**. In a certain sense, it **reverses the flow of information**.

```graphql
query{
    NopAuthDept__findAll{
        name, status, children {name, status}
    }
}
```

This is equivalent to `/r/NopAuthDept__findAll?@selection=name,status,children{name,status}`.

Through **mathematically equivalent transformations (formal transformations)**, we can clearly see that GraphQL, while built on top of REST, adds a standardized `@selection` parameter. This allows for selective fetching of results.

Traditional REST is akin to pushing all information to the frontend from the backend. The frontend cannot finely participate in the production and transmission of information. In the NopGraphQL implementation, if the `@selection` parameter is not passed from the frontend, it automatically reverts to the traditional REST model, returning all non-lazy fields. Lazy fields require explicit specification to be returned to the frontend.

In the architecture of the Nop platform, GraphQL is not on par with REST as a transmission protocol but rather as a **general decomposition and dispatch mechanism**. It allows us to organize the information structure of the system more effectively.

The positioning of NopGraphQL is as a generalized decomposition and dispatch mechanism for backend service functions. This means that a single service function can be simultaneously deployed as a REST service, GraphQL service, gRPC service, Kafka message service, or Batch processing service. In essence, any Request-Response scenario can be directly interfaced with the NopGraphQL engine, making it a versatile implementation mechanism.

What are the advantages of using GraphQL over traditional REST?

## 1. Reduced Data Load Scope, Improved Performance

```plaintext
@BizQuery
public PageBean<NopAuthUser> findPage(@Name("query") QueryBean query, FieldSelectionBean selection, IServiceContext context) {
    PageBean<NopAuthUser> pageBean = new PageBean();
    if (selection != null && !selection.hasField("total")) { 
        long total = dao.countByQuery(query);
        pageBean.setTotal(total);
    }
    // ... rest of the code
}
```

If the frontend does not require the `total` field, the calculation for `total` can be skipped.

## 2. Automatic DTO Adaptation, Enhanced Data Model Composability

In traditional REST services, service functions directly return DTOs, and all properties in the DTO must be loaded within that service function. For example, if a new virtual `roles` field is added to the NopAuthUser entity, it must be included in the DTO and loaded by the corresponding service function. All places where NopAuthUserDTO is returned will need to handle the loading of the `roles` field.

In contrast, the NopGraphQL engine returns objects that are not directly serialized into JSON but are processed further by the GraphQL engine. This allows for a more flexible data model and enhanced composability. The composable nature refers to being able to implement A and B separately and automatically obtaining all A*B results without manual composition.


```java

@BizQuery
public List<NopAuthUser> findList() {
    ...
}

@BizQuery
public NopAuthUser get(@Name("id") String id) {
    ...
}

@BizLoader
public List<String> roles(@ContextSource NopAuthUser user) {
    ...
}
```

In the above example, the `findList` and `get` functions only need to know how to load `NopAuthUser` objects, not how they relate to `NopAuthRole`. Service functions can directly return entity objects without manual translation into DTOs.

When we dynamically add a `roles` property to the `NopAuthUser` type using the `@BizLoader` mechanism, all results involving `NopAuthUser` automatically receive this property. This is achieved through the `DataLoader` provided by the `NopGraphQL` engine, which works alongside our manually written `get/findList` functions.

In `NopGraphQL`, we can independently control field permissions, transformations, and validations using additional metadata files (`xmeta`). This simplifies and standardizes the implementation of service functions.

## 3. GraphQL's selectivity is a beneficial complement to the aggregate root concept in DDD

Domain-Driven Design (DDD) is a fundamental approach where domain logic and data are placed at the center of an application. One of the key concepts in DDD is the `Aggregate Root`, which serves as the central node in an entity graph. Instead of connecting every node directly, we establish a hierarchical structure with core nodes, allowing us to traverse the entire domain through these central nodes.

The Aggregate Root is a structural pattern that encapsulates domain objects within a single unit. It allows for efficient navigation across the domain while maintaining the integrity of individual domain concepts. This approach reduces cognitive and operational costs but can lead to performance issues if not managed correctly.

GraphQL's selectivity is an advantageous complement to the `Aggregate Root` concept in DDD. While the `Aggregate Root` encapsulates all related entities, GraphQL allows for selective data retrieval, enabling precise extraction of required information without unnecessary overhead. This duality—where the `Aggregate Root` ensures conceptual integrity and GraphQL enables flexible querying—aligns perfectly with modern application design goals.

## 4. GraphQL clearly distinguishes between query and mutation in its semantic model, aligning more closely with REST's intended purposes

REST (Representational State Transfer) defines two primary methods: GET and POST. While these methods are useful for basic data exchange, they fall short in accurately distinguishing between different types of operations due to limitations like the inability of GET requests to carry request bodies and issues related to URL length constraints.

GraphQL, on the other hand, explicitly separates `query` and `mutation` operations, which is a more accurate reflection of REST's intended semantics. In REST, GET is typically used for read-only operations, while POST is often used for write operations or when more complex data is involved. However, in practice, GET's inability to send data via the body limits its utility.

GraphQL addresses these limitations by providing a clear separation between query and mutation operations. Unlike REST, where a single method might be used for multiple purposes, GraphQL ensures that each operation type behaves consistently. This makes it easier to enforce domain constraints and implement proper validation rules.

In `NopGraphQL`, we have further refined this approach:

1. **Mutation Operations**: Mutation operations automatically establish transactional contexts when executed through `DataLoader`. This ensures that all database operations are wrapped in a transaction, providing consistent behavior.
2. **Query Operations**: After executing query operations, the results undergo transformation and validation before being returned to the client.

By separating these stages, we can minimize unnecessary resource usage and ensure data consistency. The first stage avoids performing expensive data loading when no mutation has occurred, while the second stage ensures that any modifications are properly validated before being exposed to clients.
