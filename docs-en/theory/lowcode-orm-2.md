# What ORM Engine Does a Low-Code Platform Require?

[Read the previous article](https://zhuanlan.zhihu.com/p/543252423). In the previous article, I conducted an initial theoretical analysis of ORM design and proposed the minimal extension of SQL language: EQL (Entity Query Language). Based on EQL, I implemented a customizable dynamic storage structure. In this article, I will first introduce some functional trade-offs in the NopOrm engine and how to address common performance issues in ORM under such trade-offs. Then, I will explain how to implement a custom Dialect using 200 lines of code, replicating SQL management functionality similar to MyBatis, as well as implementing GraphQL integration and visualization.

## Less is More

Rumors have it that Hibernate is easy to learn but hard to master. However, what about other technologies? The problem with Hibernate seems to be that it provides too many options, constantly forcing us to make decisions. For example, mapping associations to collection objects like Set/Bag/List/Collection/Map – which is better? Should delete operations cascade to related entities? Does deleting from a collection mean deleting from the database as well? Should we load associated objects eagerly or lazily? The abundance of choices can lead to anxiety and indecision for those prone to OCD. What if you make the wrong choice? What if changing your mappings breaks someone else's code? What if you regret your decisions later?

If we are constantly making choices, each with potentially irreversible consequences, then having so many options might not be a happy path but rather a path filled with regret.

NopOrm significantly reduces developers' decision-making points by **excluding unnecessary encapsulation to the engine core**. For example, why do we need to map associations to List and index fields to list elements' indexes? Simultaneously, we have to learn numerous HQL-specific query syntaxes?

### 4.1 Automatic Mapping

The first design decision of NopOrm is: **No need for additional design decisions – the physical model can be automatically mapped to a Java entity model**. Here, there's no logical model foundation because the path from logic to physics is uncertain; thus, extra information must be added. However, starting from the physical model, it can be completed without further choices.

> The physical model itself is the result of various design decisions and their combined effects. It will remain stable in the future. If ORM mapping is based on a logical model, it essentially repeats the selection process.

Specifically, NopOrm maps every database field to a Java property (similar to MyBatis) and every foreign key to a lazy-loaded entity object. This means a single field could be mapped to multiple properties – an atomic field property plus one or more association properties that are automatically synchronized. Updating an atomic field will set the associated objects' state to null, which can be reloaded when accessed again.

If a foreign key explicitly marks it as a one-to-many relationship, it will automatically generate a Set-type property (not other types). According to ORM principles, object pointers within the same session remain unique, so they form a Set. If we use other collection types for mappings, extra assumptions are made. Since using equal pointers, the equals method doesn't need to be overridden.

Only if explicitly required for components like Component/Computed/Alias will additional configurations be added. These incremental configurations won't affect existing fields or mapping relationships; they're represented in delta files without altering original model design files: **because NopOrm's implementation adheres to reversible principles, these incremental configurations can be expressed in delta files instead of modifying the original model design files**.

### 4.2 Farewell, POJO

NopOrm's second important design decision is: **abandon the assumption of POJO (Plain Old Java Object)**.  
POJO was crucial for Hibernate back then because it helped escape from EJB's container environment and eventually dismantled the EJB ecosystem. However, POJO isn't sufficient on its own; Hibernate requires AOP (Aspect-Oriented Programming) to enhance Java entity objects by adding additional functionality and managing state data through an in-memory map like EntityEntryMap.

In a low-code environment, entity classes themselves are generated code, while AOP is another layer of code generation. Since AOP is a code generation method, why not generate the final code all at once? Is it necessary to split it into two separate generations?

As technology advances, the hidden costs of POJO are increasing, weakening the rationale for using it.

1. **AOP's bytecode generation is slow and debugging is difficult**.

2. Using POJO (Plain Old Java Object) requires reflection mechanism, which has significant performance overhead. Native Java technologies like GraalVM also heavily rely on reflection mechanisms and should avoid their use wherever possible.

3. POJO objects cannot effectively manage complex persistence states, leading to poor optimization opportunities. For example, Hibernate cannot utilize a simple `dirty flag` to determine if an entity has been modified, forcing it to maintain copies of object data in memory. This results in increased memory usage and reduced performance during `session flush` operations.

4. To implement certain mandatory business functionalities, we often inherit from a common base class (Common Base Class), which violates the POJO principle by introducing inherent state management. For instance, adding dynamic property mapping or tracking field changes before and after modifications requires the base class to provide specific member variables and methods.

5. The implementation of collection properties is both performance-intensive and error-prone. During initialization, collection properties are typically set to `HashSet`, but once associated with a session, they are automatically replaced by the ORM's internal `PersistSet` implementation. This means you need to create new collections instead of using POJOs' native collection properties. Additionally, according to ORM principles, collection properties must be lazy-loaded and tied to specific entities. However, since POJOs provide `get/set` methods, it's easy to misuse them. For example, directly assigning a collection from one entity to another (e.g., `otherEntity.setChildren(myEntity.getChildren())`) is incorrect because the returned collection is bound to the original entity and cannot be reassigned.

6. All entities in NopOrm must implement the `IOrmEntity` interface and provide a default implementation (`OrmEntity`). The `OrmEntity` class contains:
   - A unique `propId` property.
   - The `orm_propValue(int propId)` method, which replaces reflection-based property access.

7. Collection properties are represented by `OrmEntitySet`, which implements the `IOrmEntitySet` interface. During code generation:
   - Only `get` methods are generated for collection properties.
   - No `set` methods are created, preventing misuse.

8. **[IOrmEntity](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-orm/src/main/java/io/nop/orm/IOrmEntity.java)**  
   Each column model has a unique `propId`, which can be accessed using `IOrmEntity.orm_propValue(int propId)` instead of reflection.

9. **[IOrmEntitySet](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-orm/src/main/java/io/nop/orm/IOrmEntitySet.java)**  
   All collection properties are generated as `OrmEntitySet` instances, which implement `IOrmEntitySet`. During code generation:
   - Only `get` methods are created for collection properties.
   - No `set` methods are generated, preventing misuse.

10. **[SimsExam](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-orm/src/test/java/io/nop/app/SimsExam.java)** and **[_SimsExam](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-orm/src/test/java/io/nop/app/_gen/_SimsExam.java)**  
    - `SimsExam` is the main test class.
    - `_SimsExam` is an automatically generated class that mirrors the structure of `SimsExam`.


### 4.3 Lazy and Cascade All

In NopOrm:
- **All associated entities and collections are lazy-loaded**, including those defined by `lazy` or `cascade-all` settings. This design significantly simplifies the ORM's internal implementation and enables efficient batch loading.
- **No class inheritance is supported**, ensuring that POJO principles are not violated.

For EQL (Entity Query Language):
- No `eager fetch` syntax is provided because it would generate SQL that deviates from expectations. For example, using a `JOIN` to load associated entities during object initialization increases the result set size and causes unnecessary data retrieval.
- **BatchLoadQueue** is used internally for batch loading, ensuring efficient database access and reduced performance overhead.

### Hibernate Cascade Behavior

In Hibernate, the `cascade` behavior is triggered by an `action` method, such as when `session.save()` is called. This will execute cascade operations on associated entities. The initial purpose of this design was for performance optimization, as certain properties might not require cascade execution and are automatically skipped.

However, using action-based cascade can lead to unintended consequences, like generating two SQL statements (one insert and one update) after a save operation, even though only one insert is needed.

### FlushMode Configuration

Hibernate's `FlushMode` setting can also lead to misleading results. The default mode is `auto`, where Hibernate decides whether to flush the session. This can cause unnecessary database calls and performance issues if misconfigured, as it may incorrectly trigger a flush when it shouldn't.

### NopOrm Design Philosophy

NopOrm adopts a **completely lazy** approach, thus eliminating the `FlushMode` concept. It only flushes the session explicitly when `session.flush()` is called, combined with `OrmTemplate`, which ensures that `session.flush()` is called before transaction commit. This improves ORM engine predictability at the conceptual level.

### State-Driven Cascade in NopOrm

NopOrm uses a state-driven cascade approach: no cascades are performed during operations unless explicitly requested via `session.flush()`. It leverages `dirty flag` optimization, where if all instances of a particular type remain unmodified, their `dirty flag` is set to false, and the corresponding `flush()` operation is skipped. If the entire session has no modified entities, the global `dirty flag` remains false, and the `session.flush()` operation is skipped.

### Side Effect of Action-Based Cascade in Hibernate

Another side effect of action-based cascade in Hibernate is that the order of SQL execution becomes less predictable. In NopOrm, however, all operations are stored in an `actionQueue`, sorted by the database table's dependency hierarchy before execution, ensuring a deterministic order and thus preventing deadlocks.

### Deadlock Prevention
Deadlocks typically occur when two threads modify different tables in a cyclic manner:
- Thread A modifies Table A then Table B.
- Thread B modifies Table B then Table A.

By sorting operations based on the database table's dependency hierarchy, NopOrm ensures that modifications follow a fixed order, effectively preventing deadlocks.

### The Advantage of Deterministic Execution
The deterministic execution of operations in NopOrm acts as a lock replacement mechanism. Instead of relying on locking mechanisms that can lead to contention and reduced performance, NopOrm enforces an order of execution, ensuring that all necessary updates are performed in a predictable sequence.

### NopOrm's Abandoned Features

NopOrm discards many features present in Hibernate but introduces several critical ones missing from it:
- **Composite Key Support**: Built-in with `OrmCompositePk` and automatic property mapping.
- **Key Generator**: Automatic key generation using `SequenceGenerator.generate()` for dynamic strategy support.
- **Batch Processing**: Bulk updates via JDBC batch processing, reducing database interaction.
- **Optimistic Locking**: Implemented through version fields and update checks.
- **Template Method Pattern**: Enhanced with `OrmTemplate` for simplified transaction management.
- **Interceptor Architecture**: Flexible ORM engine interception for custom behavior.

### Good Parts of Hibernate

NopOrm inherits some excellent designs from Hibernate:
1. **Second-Level Caching**: Configurable cache sizes to prevent memory overflow.
2. **Composite Key Support**: Necessary in most business systems, NopOrm provides native support with `OrmCompositePk`.
3. **Key Generator**: Facilitates database-independent key generation through `SequenceGenerator`.
4. **JDBC Batch Processing**: Reduces database interactions by merging updates into a single call.
5. **Optimistic Locking**: Prevents optimistic concurrency issues through version checks.
6. **Template Method Pattern**: Simplifies transaction management with `OrmTemplate`.
7. **Interceptor Architecture**: Allows custom behavior during ORM operations.

### Performance Considerations

NopOrm discards Hibernate's FlushMode in favor of explicit flushing, which eliminates unnecessary database calls and improves performance. The action queue ensures that operations are executed in a predictable order, minimizing deadlocks and contention.

9. **SQL Compatibility**: Achieve cross-database SQL syntax compatibility using Dialect, including syntax formatting and SQL function translations.

### 5.2 Understanding Needs of ORM

Common business requirements can be easily implemented using an ORM engine, and NopOrm provides built-in support without additional plugins.

1. **Multi-tenant**: Add tenantId filter conditions to multi-tenant tables and prevent cross-tenant data access.
2. **Shard Selection**: Dynamically select the appropriate shard using IShardSelector for database sharding.
3. **Logical Deletion**: Convert delete operations into set delFlag=1 updates, with automatic filter conditions in queries (delFlag=0).
4. **Timestamps**: Automatically record user modifications and timestamps for operation history.
5. **Modification Logs**: Intercept entity modification operations using OrmInterceptor to capture field values before and after changes, then log them to a dedicated modification log table.
6. **Historical Table Support**: Add version fields (revType, beginVer, endVer) to history tables. Convert record modifications into new entries with endVer set to the start ver of the latest record. Add filter conditions in queries to only retrieve the latest version.
7. **Field Encryption**: Mark fields requiring encryption with the 'enc' tag for encrypted storage. Use IDataParameterBinder for reading database values, ensuring encryption during SQL statement parameter handling.
8. **Sensitive Data Masking**: Mask sensitive fields like card numbers and IDs with the 'mask' tag to prevent exposure in logs.

9. **Component Reusability**: Group related fields into reusable components using OrmComponent for logic reuse. For example, handle precision requirements for Decimal types by adding a VALUE_SCALE field in history tables, but retrieve the scale-adjusted BigDecimal directly from the database.

   ```java
   [FloatingScaleDecimal](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-orm/src/main/java/io/nop/orm/support/FloatingScaleDecimal.java)
   ```

Combining with surrounding frameworks, NopPlatform includes additional built-in solutions like:

1. **General Queries**: No coding required for front-end or back-end. Submit forms in a standardized format, and the back-end automatically validates meta data and permissions using GraphQL results.

2. **Modification Confirmation and Approval**: Integrate CRUD operations with API calls to automate modification confirmation, approval workflows, and logging without direct database access or external API calls.

3. **Object Creation**: Complex business objects can be created by copying existing entities. Fields requiring specific formats (e.g., GraphQL) can be explicitly defined.

4. **Dictionary Translation**: Translate statusId-like fields using dictionary tables to display appropriate text, with additional language handling via locale settings.

5. **Bulk Imports/Exports**: Import data via CSV/Excel files while ensuring logical consistency and user-defined validation rules. Export data in CSV/Excel formats, preserving permissions during queries.

6. **Distributed Transactions**: Leverage TCC distributed transaction coordination for cross-database operations with automatic synchronization.

### 5.3 Embrace the New Asynchronous World

Traditionally, JDBC interfaces were all synchronous, so JdbcTemplate and HibernateTemplate followed a synchronous encapsulation style. However, with the spread of asynchronous high-concurrency programming ideas, reactive programming styles have gradually become mainstream. Spring currently has proposed the [R2DBC standard](https://r2dbc.io/), and the [Vert.x framework](https://vertx.io/) also includes built-in support for popular databases like MySQL and PostgreSQL through its [asynchronous connectors](https://vertx.io/docs/vertx-pg-client/java/). On the other hand, as a data fusion engine, an ORM engine might use Redis, Elasticsearch, or MongoDB (all of which are asynchronous-access NoSQL sources) as its underlying storage. Additionally, ORM engines need to be compatible with GraphQL for asynchronous execution.

Considering these factors, NopOrm's OrmTemplate encapsulation has also introduced an asynchronous invocation pattern.

```java
public interface IOrmTemplate extends ISqlExecutor {
    <T> CompletionStage<T> runInSessionAsync(Function<IOrmSession, CompletionStage<T>> callback);
}
```

### 5.4 Asynchronous Design in OrmSession

OrmSession is designed to be thread-safe, allowing only one thread to access it at any given time. To enable multi-threaded access to this thread-unsafe data structure, a basic design approach is to adopt an Actor-like task queue pattern.

```java
class Context {
    ContextTaskQueue taskQueue;

    public void runOnContext(Runnable task) {
        if (!taskQueue.enqueue(task)) {
            taskQueue.flush();
        }
    }
}
```

### 5.3 Asynchronous Context Propagation

The Context is a thread-safe context object that carries a corresponding task queue. At any given time, only one thread will execute tasks registered in this task queue. The `runOnContext` method enqueues the task into the task queue. If the enqueue operation fails (which is unlikely), it forces a flush of the task queue to ensure the task is processed.

> For recursive calls, the taskQueue effectively acts like a [trampoline function](https://zhuanlan.zhihu.com/p/142241289).

If we introduce an asynchronous Context, we can further enhance timeout support for remote service calls. When a remote service call exceeds its timeout, the client will throw an exception or retry the request. However, the server remains unaware of the timeout and continues processing. This can lead to excessive database queries if retries are enabled due to the added traffic from repeated attempts. A better approach is to introduce a timeout property in the Context.

```java
class Context {
    long callExpireTime;
}
```

### 5.4 Timeout Handling in Remote Calls

When making cross-system calls via RPC, the client can传递一个timeout值通过RPC消息头传输到服务端。Once the服务端 receives this timeout, it calculates the `callExpireTime` as `currentTime + timeout`. The JdbcTemplate then checks against this `callExpireTime` before executing each database query. If the service call exceeds its timeout, the server will throw an exception or initiate a retry. However, if retries are enabled, this can lead to unnecessary database queries and increased traffic. To optimize this, we can introduce a timeout property in the Context.

```java
class Context {
    long callExpireTime;
}
```

### 5.4 Dialect Customization

NopOrm uses the Dialect model to encapsulate differences between various databases. The [default dialect](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-dao/src/main/resources/_vfs/nop/dao/dialect/default.dialect.xml) serves as a base configuration for database interactions, with specific dialects (like MySQL, PostgreSQL, etc.) extending this default behavior.

[MySQL dialect](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-dao/src/main/resources/_vfs/nop/dao/dialect/mysql.dialect.xml)

[PostgreSQL dialect](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-dao/src/main/resources/_vfs/nop/dao/dialect/postgresql.dialect.xml)

Referencing the above examples, both `mysql.dialect.xml` and `postgresql.dialect.xml` inherit from `default.dialect.xml`. Compared to constructing a `Dialect` object programmatically in Hibernate, using the `dialect` model file provides higher information density and more intuitive expressions. More importantly, the `postgresql.dialect.xml` file can clearly identify configurations that are **added**, **modified**, or **removed** compared to `default.dialect.xml`.

Since the entire Nop platform is built on reversible computation principles, the parsing and validation of `dialect` model files can be handled by a general-purpose `DslModelParser`. This allows for Delta customization without modifying the `default.dialect.xml` file, nor its references. For example, you can add a `default.dialect.xml` file in the `/_delta` directory to customize built-in models without altering `postgresql.dialect.xml`.

```xml
<!-- /_delta/myapp/nop/dao/dialect/default.dialect.xml -->
<dialect x:extends="raw:/nop/dao/dialect/default.dialect.xml">
  Here, describe only the Delta changes
</dialect>
```

Delta customization is similar to Docker's overlay filesystem, allowing **multiple layers of Delta** to be stacked. Unlike Docker, however, Nop's Delta customization extends beyond just file-level changes to include internal Delta structural operations. This is facilitated by `xdef` meta-model definitions, enabling all Nop platform models to automatically support Delta customization.


### 5.5 Visualization Integration

Hibernate's `hbm.xml` mapping files and JPA annotations are designed for database schema mapping, making them unsuitable for visual model design. Enhancing Hibernate with a visualization designer is a complex task.

NopOrm uses `orm.xml` model definition files to define entity models. First, it provides a complete structural definition model that can generate build scripts based on the model's information, as well as perform database schema comparisons and automatic data migrations.

[app.orm.xml](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-orm/src/test/resources/_vfs/nop/test/orm/app.orm.xml)

Adding a visualization designer to NopOrm is surprisingly simple, requiring only an additional meta-programming tag.

```xml
<orm ...>
  <x:gen-extends>
    <pdman:GenOrm src="test.pdma.json" xpl:lib="/nop/orm/xlib/pdman.xlib"
                  versionCol="REVISION"
                  createrCol="CREATED_BY" createTimeCol="CREATED_TIME"
                  updaterCol="UPDATED_BY" updateTimeCol="UPDATED_TIME"
                  tenantCol="TENANT_ID"
    />
    ...
  </x:gen-extends>
</orm>
```

[Pdman](http://www.pdman.cn/) is an open-source database modeling tool that saves model information in JSON file format. `<pdman:GenOrm>` is an XPL template language tag that runs during the compile phase and generates ORM model files based on Pdman's JSON models. This generation is real-time, meaning that if you modify `test.pdma.json`, the OrmModel's cache will become invalid, and subsequent access will re-parse the model to generate a new model object.

According to reversible computation theory, the so-called visualized interface is merely a graphical representation of the domain model (Representation). The model file can be considered as a textual representation of the domain model. Reversible computation theory states that a model can have multiple representations. The visualized display is not unique; it can take various forms depending on the visualization tool used. By exploring this direction, we can draw a conclusion: a model's visual presentation is not unique and can exist in multiple forms.

For ORM models, besides Pdman, we can also choose to use PowerDesigner for modeling, similarly.

Using `<pdm:GenOrm>` tag, you can convert Pdman model files into the required format by ORM.

In the Nop platform, we also support defining entity data models through Excel files.

[test.orm.xlsx](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-orm/src/test/resources/_vfs/nop/test/orm/test.orm.xlsx)

Similarly, you only need to introduce a tag call `<orm-gen:GenFromExcel>`, and then you can happily design ORM models in Excel.

```xml
<orm ...>
  <x:gen-extends>
     <orm-gen:GenFromExcel path="test.orm.xlsx" />
  </x:gen-extends>
</orm>
```

Notably, the Excel model file parsing in the Nop platform is also based on reversible computation theory. Reversible computation theory views Excel model files as mappings from the Excel domain to a DSL AST (Abstract Syntax Tree), essentially treating them as functions within the Excel domain. This allows us to implement a generic Excel model parser that only requires the structure information defined in the OrmMeta model file, without needing any special encoding.

It is worth mentioning that Nop supports defining entity models through Excel files, which are then used for visualization and further processing.

[test.orm.xlsx](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-orm/src/test/resources/_vfs/nop/test/orm/test.orm.xlsx)

The same applies to the `<orm-gen:GenFromExcel>` tag. By simply calling it with `path="test.orm.xlsx"`, you can generate an Excel-based ORM model.

```xml
<orm ...>
  <x:gen-extends>
     <orm-gen:GenFromExcel path="test.orm.xlsx" />
  </x:gen-extends>
</orm>
```

Notably, the Nop platform's Excel model parsing is also designed based on reversible computation theory. The Excel domain is considered a separate domain, and its files are parsed into a DSL AST structure. This allows for the implementation of a generic Excel parser that only requires structural information from the OrmMeta model file, without any need for special encoding.

For further reading on model conversion, you can refer to:

[From Zhihu: Understanding Low-Code Platform Design](https://zhuanlan.zhihu.com/p/531474176)

Additionally, if you are interested in implementing a similar Word template with around 800 lines of code, you can refer to:

[Implementing a Poi-TL-like Word Template with 800 Lines of Code](https://zhuanlan.zhihu.com/p/537439335)

In summary, all business functionalities supported by the Nop platform are implemented through model-driven approaches. By analyzing model information, we can derive valuable insights and export extensive documentation, such as database schema documents, data dictionaries, API documentation, and unit test documents.

### The N+1 Problem

Since Hibernate was first introduced, the N+1 problem has loomed over ORM engines like a dark cloud. Suppose you have a model structure like this:

```java
class Customer {
    Set<Order> orders;
}

class Order {
    Set<OrderDetail> details;
}
```

If you want to process an individual customer's order details, you would typically traverse the `orders` collection:

```java
Customer customer = ...; // Assume customer is already retrieved
Set<Order> orders = customer.getOrders();
for (Order order : orders) {
    process(order.getDetails());
}
```

This results in multiple database queries: one for the customer, one for each order, and potentially another for each order's details. Ultimately, this leads to N+1 queries.

The same applies if you want to retrieve all orders for a specific customer: you would need to load the customer first, then iterate over their orders, and for each order, load its details. This results in multiple database hits, ultimately leading to the N+1 problem.

# The N+1 Problem and Its Evolution in Hibernate

The N+1 problem has earned its notorious reputation for a reason. During the development phase, the data volume is small, so performance issues are often overlooked. When the application goes live, however, we find ourselves with no effective remedies. Any modification to the code typically requires a complete rewrite of the system, even changing the entire architecture.

This issue has troubled Hibernate for many years until the JPA (Java Persistence API) standard introduced the concept of **EntityGraph**.

## Solution via Annotation

The solution involves using the **@NamedEntityGraph** annotation on the entity class. This annotation allows you to define which entities and their attributes should be loaded in a single batch when fetching an object.

```java
@NamedEntityGraph(
    name = "customer-with-orders-and-details",
    attributeNodes = {
        @NamedAttributeNode(value = "orders", subgraph = "order-details"),
    },
    subgraphs = {
        @NamedSubgraph(
            name = "order-details",
            attributeNodes = {
                @NamedAttributeNode("deails")
            }
        )
    }
)
@Entity
class Customer {
    // Entity fields and methods...
}
```

When fetching an object, you can specify the **EntityGraph** configuration:

```java
EntityGraph entityGraph = entityManager.getEntityGraph("customer-with-orders-and-detail");
Map<String, Object> hints = new HashMap<>();
hints.put("javax.persistence.fetchgraph", entityGraph);

Customer customer = entityManager.find(Customer.class, customerId, hints);
```

## Constructing EntityGraph via Code

In addition to using annotations, **EntityGraph** can also be constructed programmatically:

```java
EntityGraph graph = entityManager.createEntityGraph(Customer.class);
Subgraph detailGraph = graph.addSubgraph("order-details");
detailGraph.addAttributeNodes("details");

Customer customer = new Customer();
// EntityManager would generate a SQL statement like:
/*
SELECT customer0.*,
       order1.*,
       detail2.*
FROM customer customer0
LEFT JOIN order order1 ON ...
LEFT JOIN order_detail detail2 ON ...
WHERE customer0.id = ?
*/
```

This approach results in multiple database queries, fetching all related data in one go but at the cost of a more complex SQL statement.

## The Problem with N+1

The N+1 problem persists because the data model's structure (Customer → Orders → Details) is inherently hierarchical. To retrieve all related data, you must issue multiple SELECT statements, leading to inefficient performance when dealing with large datasets.

However, if we can bypass the object structure and directly access the required data while organizing it in memory according to the defined structure, this problem could be resolved.

```java
Customer customer = ...;
// Magic fetch function
fetchAndAssembleDataInAMagicalWay(customer);

// Data is already in memory and structured as needed
Set<Order> orders = customer.getOrders();
for (Order order : orders) {
    Set<Detail> details = order.getDetails();
    // Process each detail...
}
```

## Alternative Solutions

NopOrm provides an **OrmTemplate** that offers batch loading of attributes through its interface. This bypasses the traditional ORM limitations and allows for more efficient data handling.

```java
ormTemplate.batchLoadProps(Arrays.asList(customer), Arrays.asList("orders.details"));
// Data already exists in memory, can be safely traversed and used without generating loading actions
Set<Order> orders = customer.getOrders();
```

OrmTemplate uses IBatchLoadQueue to load functionality

```java
IBatchLoadQueue queue = session.getBatchLoadQueue();
queue.enqueue(entity);
queue.enqueueManyProps(collection, propNameList);
queue.enqueueSelection(collection, fieldSelList);
queue.flush();
```

BatchLoadQueue's internal implementation is similar to GraphQL's DataLoader mechanism. It collects the entities or entity collections to be loaded first and then uses a single `select xxx from ref__entity where ownerId in :idList` query to batch load data. The data is then split into separate objects based on the ownerId. Compared to DataLoader, BatchLoadQueue has access to the full entity model and a unified loader, making its implementation more optimized. Additionally, the external interface requires less information to be expressed. For example, `orders.details` means loading orders first and then their details, including all eager attributes. If using GraphQL, you would need to explicitly define which attributes of OrderDetail to load.

> BatchLoadQueue was not inspired by GraphQL. GraphQL was open-sourced in 2015, but we had been using BatchLoadQueue prior to that.

If the entities to be loaded are numerous or have a deep hierarchy, batch loading can impact performance. Therefore, NopOrm has left a backdoor for experts only,

```java
session.assembleAllCollectionInMemory(collectionName);
or
session.assembleCollectionInMemory(entitySet);
```

**assembleAllCollectionInMemory assumes that all related entities have already been loaded into memory**, so it no longer accesses the database. Instead, it directly uses in-memory data to filter and determine collection elements. For loading all relevant entities into memory beforehand, there are numerous methods, for example,

```java
orm().findAll(new SQL("select o from Order o"));
orm().findAll(new SQL("select o from OrderDetail o"));
session.assembleAllCollectionInMemory("test.Customer@orders");
session.assembleCollectionInMemory("test.Order@details");
```

**This approach is risky because if you call assemble without first loading all related entities, the resulting collection will be incomplete.**

Let's revisit the SQL statement generated by EntityGraph: it corresponds to this EQL query

```sql
select c, o, d
from Customer c
left join c.orders o
left join o.details d
where c.id = ?
```

According to ORM principles, even though the query returns multiple Customer, Order, and OrderDetail objects, their primary keys (c.id) are the same. Therefore, only one instance of each will be constructed in memory. Even if a Customer or Order was previously loaded, its details will still be fetched and added as new entities.

Even if you have already loaded a Customer or Order entity elsewhere, this query will return fresh data for their details, potentially overriding the cached values. This could lead to performance issues if not properly managed.
> In other words, ORM provides an effect similar to the [Repeatable Read](https://zhuanlan.zhihu.com/p/150107974) transaction isolation level in databases. When reading repeatedly, only the initial result is retrieved by the ORM engine, discarding subsequent reads. The same applies to Load X, Update X, and other operations: the second read will automatically discard the result, ensuring that we always observe the first load result and any subsequent modifications.

Based on this understanding, the execution process of EntityGraph is equivalent to the following call:

```sql
orm().findAll(new SQL("select c,o,d from Customer c left join ..."));
session.assembleSelectionInMemory(c, FieldSelectionBean.fromProp("orders.details"));
// The execution process of assembleSelection is equivalent to the following call:
session.assembleCollectionInMemory(c.getOrders());
for (Order o: c.getOrders()) {
    session.assembleCollectionInMemory(o.getDetails());
}
```

## 7. QueryBuilder is important, but unrelated to ORM

Some people believe that [ORM's primary function is QueryBuilder](https://www.zhihu.com/question/23244681/answer/2426095608), but I think this is a misunderstanding. **QueryBuilder is useful only because the Query object needs to be modeled.** In the Nop platform, we provide the QueryBean model object, which supports the following functions:

1. QueryBean corresponds to QueryForm and QueryBuilder controls in the frontend and can directly construct complex query conditions.
2. Backend data filtering corresponding to filter conditions can be directly inserted into QueryBean, which is cleaner and safer than SQL concatenation. Use `queryBean.appendFilter(filter)` for this purpose.
3. QueryBean supports custom query operators and fields. For example, we can define a virtual field myField and query the in-memory data along with other associated tables, converting it into a subquery condition. In a single-table framework, this effectively achieves the effect of joining multiple tables.

4. DaoQueryHelper.queryToSelectObjectSql(query) converts query conditions into SQL statements.
5. QueryBeanHelper.toPredicate(filter) converts filter conditions into the Predicate interface, enabling direct filtering in Java.

6. Using FilterBeans-defined operators (such as and, eq, etc.) along with code-generated property names allows for a safe and structured approach to constructing queries. For example:
   ```java
   filter = and(eq(PROP_NAME_myFld, "a"), gt(PROP_NAME_otherFld, 3))
   ```

QueryBuilder is fundamentally unrelated to ORM because even when completely detached from relational databases and SQL statements, we can still use the Query model for business rule configurations. For example:

```xml
<decisionTree>
    <children>
        <rule>
            <filter>
                <eq name="message.type" value="@:1" />
                <match name="message.desc" value="a.*" />
            </filter>
            <output name="channel" value="A" />
        </rule>
        <rule>
            ...
        </rule>
    </children>
</decisionTree>
```

We can directly use the QueryBuilder in the frontend for business rule configurations, while maintaining the decision tree structure.

# 8. Use of ORM with OLAP Analysis?

There is a common belief that ORM (Object-Relational Mapping) is only suitable for OLTP (Online Transaction Processing) applications, as it struggles to handle the complex queries required by OLAP (Online Analytical Processing). However, some insist on using ORM even in this context, expecting it to perform faster and more effectively.

## 8. Can OLAP Analysis be done with ORM?

The idea persists that ORM falls short when dealing with the complex SQL queries needed for OLAP analysis. Yet, some still opt for ORM, believing it can manage the demands of OLAP while maintaining speed and efficiency.

### The Truth About SQL Construction
The so-called **QueryDsl** in Java doesn't offer significant advantages over direct use of QueryBean. For a few additional query conditions, FilterBeans provide static methods like `and/or/eq`. For highly complex SQL construction, using an external file like MyBatis is more efficient.

### Simplifying Complex Queries
Using tools like MyBatis outside the ORM framework allows for greater control and simplification of complex queries. This approach can significantly reduce the complexity involved in crafting SQL statements.

## The Power of QueryBean

The **QueryBean** class, found in `io/nop/api/core/beans/query/QueryBean.java`, serves as a powerful tool for constructing SQL queries. It allows for dynamic field access and method chaining, making it highly flexible and intuitive.

### Example Queries
Here are some examples using **QueryBean**:

```sql
-- Example 1: Basic Query Construction
SELECT A.*
FROM EmployeeTable A
WHERE Department = 'Marketing'
```

```sql
-- Example 2: Complex Query with Multiple Conditions
SELECT A.EmployeeID, A.FirstName, A.LastName
FROM EmployeeTable A
JOIN DepartmentTable D ON A.DepartmentID = D.ID
WHERE A.Position = 'Manager' AND D.Name = 'Sales';
```

### The Limitations of ORM
While ORM offers convenience, it often struggles with complex queries required for OLAP analysis. This limitation makes tools like MyBatis or direct SQL use more appealing for such tasks.

## The Case for DQL

DQL (Data Query Language), implemented through tools like **Frontend BI System** by RQ Soft, provides a straightforward solution for handling complex OLAP queries. Unlike SQL, which can be cumbersome for users without technical expertise, DQL simplifies the process with its intuitive syntax and powerful capabilities.

### Benefits of DQL
- **Simplicity**: Users can quickly construct complex queries without deep SQL knowledge.
- **Performance**: DQL often outperforms traditional SQL in executing complex OLAP tasks.
- **Flexibility**: It supports a wide range of operations, including joins, aggregations, and hierarchical data organization.

## The Challenges of Wide Tables

Using wide tables for OLAP can be challenging. While they simplify certain aspects, they also present issues with performance and complexity. Tools like DQL help mitigate these challenges by offering a more efficient alternative to SQL.

### Example: Finding American Employees Managed by Chinese Managers

```sql
-- SQL Example
SELECT A.*
FROM EmployeeTable A
JOIN DepartmentTable D ON A.DepartmentID = D.ID
JOIN EmployeeTable C ON D.ManagerID = C.EmployeeID
WHERE A.Country = 'USA' AND C.Country = 'China';
```

```sql
-- DQL Example
SELECT *
FROM EmployeeTable
WHERE Country = 'USA'
  AND (Department Table linked to Manager).Country = 'China';
```

## The Power of Relationships

Understanding relationships is crucial in OLAP analysis. For instance, linking employee and department tables allows for powerful insights into how different departments perform under various managers.

### Example: Summing Employee Salaries by Department and Manager

```sql
-- SQL Example
SELECT T1.DepartmentID, T1.EmployeeID, SUM(T2.Salary) AS TotalSalary
FROM EmployeeTable T1
JOIN DepartmentTable D ON T1.DepartmentID = D.ID
JOIN EmployeeTable C ON D.ManagerID = C.EmployeeID
WHERE T1.Country = 'USA'
GROUP BY T1.DepartmentID, T1.EmployeeID;
```

```sql
-- DQL Example
SELECT DepartmentID, EmployeeID, SUM(Salary) AS TotalSalary
FROM EmployeeTable
GROUP BY DepartmentID, EmployeeID
  WHERE Country = 'USA';
```

### The Importance of Foreign Keys

Foreign keys play a vital role in linking tables and organizing data effectively. Proper use of foreign keys can enhance the accuracy and efficiency of OLAP queries.

## Recursive Relationships

Recursive relationships, such as an employee reporting to another employee, add depth to your analysis. Tools like DQL handle these relationships seamlessly, allowing for complex hierarchical analyses.

### Example: Finding Managers with High-Salary Subordinates

```sql
-- SQL Example
SELECT C.EmployeeID, C.Name, C.Salary
FROM EmployeeTable C
JOIN EmployeeTable E ON C.ManagerID = E.EmployeeID
WHERE C.Country = 'USA'
GROUP BY C.EmployeeID
HAVING COUNT(E.Salary) > 5;
```

```sql
-- DQL Example
SELECT EmployeeID, Name, Salary
FROM EmployeeTable
WHERE Country = 'USA'
  AND (Employee linked to Subordinate).Count > 5;
```

## The Future of Data Analysis

As technology advances, tools like DQL are expected to play a pivotal role in the future of data analysis. They provide a bridge between technical expertise and business insights, making complex analytics more accessible.

### Conclusion
While ORM has its place in OLTP systems, it often falls short for OLAP needs. Tools like MyBatis and DQL offer more flexibility and efficiency, empowering users to tackle even the most complex queries with ease.

# DQL: A Technical Document Fragment

## 1. Understanding DQL
DQL stands for Data Query Language, a concept similar to SQL but tailored for data handling and manipulation.

### Key Concepts of DQL:
1. **Dimensional Alignment**: Data naturally aligns along dimensions without explicit joins.
2. **Summing Values**: Aggregate functions like SUM are used to consolidate data.
3. **Join Operations**: Combining multiple tables is straightforward using joins.
4. **Query Templates**: Simplifying complex SQL or EQL queries by managing them in templates.

## 2. DQL vs SQL and EQL
- **SQL (Structured Query Language)**: Used for relational database operations, often requiring explicit joins and grouping.
- **EQL (Entity Query Language)**: Focuses on object-oriented data modeling, typically used with ORM tools like Hibernate or SQLAlchemy.
- **DQL**: Aims to simplify data querying by aligning data based on dimensions.

## 3. DQL in Practice
### Example Queries:
```sql
-- SQL
SELECT T1.date, T1.amount, T2.amount, T3.amount
FROM (SELECT date, SUM(amount) AS amount FROM contract_table GROUP BY date) T1
LEFT JOIN (SELECT date, SUM(amount) AS amount FROM refund_table GROUP BY date) T2
ON T1.date = T2.date
LEFT JOIN (SELECT date, SUM(amount) AS amount FROM inventory_table GROUP BY date) T3
ON T2.date = T3.date
```

```dql
-- DQL
SELECT contract_table.SUM(amount), refund_table.SUM(amount), inventory_table.SUM(amount)
FROM contract_table
LEFT JOIN refund_table ON date
LEFT JOIN inventory_table ON date
```

### DQL and ORM Comparison:
- **NopOrm Perspective**: DQL is essentially an ORM-like approach, handling data alignment through dimensions.
- **Key Features**:
  1. **Dimensional Key Alignment**: Similar to how ORM tools map foreign keys.
  2. **Query Abstraction**: Simplifies querying by focusing on dimensions rather than individual tables.
  3. **Efficiency**: Reduces the need for complex joins, leveraging dimension-based alignment.

## 4. DQL and Query Templates
- **SqlLibManager**: Manages SQL/EQL/DQL templates for complex queries.
- **SqlItemModel**: Defines fields and relationships for model mapping.
- **SqlLibInvoker**: Executes and returns results based on defined templates.

### Example:
```java
// SqlLibManager.java
public class SqlLibManager {
    private final Map<String, Template> templates;
    
    public SqlLibManager(Map<String, Template> templates) {
        this.templates = templates;
    }
    
    public <T> T executeQuery(Template template, Object... parameters) {
        return (T) template.execute(parameters);
    }
}
```

## 5. Implementation Considerations
- **Performance**: Dimensional alignment can improve query efficiency.
- **Edge Cases**: Handling multi-dimensional data and complex joins requires careful implementation.

### 9.1 Unified Management of SQL/EQL/DQL

The `sql-lib` file contains three types of nodes corresponding to SQL statements, EQL queries, and the DQL query model introduced in the previous section. These can be managed uniformly.

```xml
<sql-lib>
  <sqls>
    <sql name="sql"> ... </sql>
    <eql name="eql"> ... </eql>
    <query name="query"> ... </query>
  </sqls>
</sql-lib>
```

### Benefits of Modeling

The first advantage of modeling is the built-in **Delta Customization Mechanism** within the Nop platform. If you have already developed a Base product and need to optimize SQL queries based on specific customer data during deployment, you can achieve this by simply adding a `sql-lib` Delta model file without modifying any Base code.

For example:

```xml
<sql-lib x:extends="raw:/original.sql-lib.xml">
  <sqls>
    <!-- Same-named SQL statements will override base definitions -->
    <eql name="yyy"> ... </eql>
  </sqls>
</sql-lib>
```

### Combining Delta Customization with Meta Programming

Another common usage of the Delta customization mechanism is in conjunction with meta programming. If your system is well-structured and contains numerous similar SQL statements, you can first generate these statements during compilation using meta programming techniques, and then refine them using the Delta customization mechanism.

For example:

```xml
<sql-lib>
  <x:gen-extends>
    <app:GenDefaultSqls ... />
  </x:gen-extends>

  <sqls>
    <!-- SQL can be customized automatically -->
    <eql name="yyy"> ... </eql>
  </sqls>
</sql-lib>
```

### 9.2 XPL Template's Component Abstraction能力

In contrast, MyBatis provides only a limited set of fixed tags (such as `foreach`, `if`, `include`), making it inadequate for building complex dynamic SQL statements. The process of manually concatenating SQL in XML is cumbersome, and the root cause lies in MyBatis' incomplete solution—**it lacks a second-level abstraction mechanism**.

### 9.3 Macro Tag's Meta Programming能力

In contrast, NopOrm utilizes **XPL templates** as its underlying engine for generating SQL statements. This approach inherits the tagging abstraction capabilities of XPL.

### Macro Tag的元编程能力
> XLang is designed specifically for reversible computing and includes components such as XDefinition, XScript, XPL, and XTransform. Its core concept revolves around the generation, transformation, and incremental merging of abstract syntax trees (ASTs). It can be likened to a domain-specific language tailored for Tree methodology.

### Example Code for SQL Generation

```xml
<sql name="xxx">
  <source>
    select 
      <my:MyFields /> 
      , <my:WhenAdmin> 
        , <my:AdmninFields />
      </my:WhenAdmin>
    from MyEntity o
    where <my:AuthFilter/>
  </source>
</sql>
```

### XPL Template的优势

The XPL template language not only includes essential tags like `<c:for>` and `<c:if>`, but also allows for custom tag extensions via the abstraction mechanism, similar to how Vue components work on the frontend.

### Example Code for Function Execution

```xml
<sql>
  <source>
    <c:script>
      import test.MyService;

      let service = new MyService();
      let bean = inject("MyBean"); // Directly retrieves bean from IOC container
    </c:script>
  </source>
</sql>
```

# Dynamic SQL Construction in MyBatis and Similar Frameworks

The conventional method of concatenating dynamic SQL is quite cumbersome, which is why some frameworks like MyBatis provide special design simplifications at the SQL template level. For example, certain frameworks have introduced implicit condition judgment mechanisms.

## Example with MyBatis-like Frameworks

Here’s an illustrative example using a MyBatis-like framework:

```sql
select xxx
from my_entity
where id = :id
and name = :name
```

The logic automatically analyzes the variables within parentheses and appends an implicit condition only when the `name` attribute is not empty.

In NopOrm, similar **local syntax transformation** can be achieved using macro labels:

```xml
<sql>
  <source>
    select o from MyEntity o
    where 1=1
     <sql:filter> and o.classId = :myVar</sql:filter>
  </source>
</sql>
```

`<sql:filter>` is a macro label that executes at compile time, essentially transforming the code structure. This is equivalent to manually writing:

```xml
<c:if test="${!_.isEmpty(myVar)}">
   and o.classId = ${myVar}
</c:if>
```

For detailed implementation of these macros, refer to the `sql.xlib` library:

[sql.xlib](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-entropy/tree/master/nop-orm/src/main/resources/_vfs/nop/orm/xlib/sql.xlib)

In essence, this concept is akin to macros in Lisp, particularly similar to how Lisp macros operate by replacing any part of the Abstract Syntax Tree (AST). However, instead of using Lisp's mathematical notation style, it employs XML-based syntax, making it more user-friendly.

For C# and LINQ:

```xml
<c:script>
function f(x,y){
    return x + y;
}
let obj = ...
let {a,b} = linq `
  select sum(x + y) as a , sum(x * y) as b
  from obj
  where f(x,y) > 2 and sin(x) > cos(y)
`
</c:script>
```

XScript templates automatically identify macro functions and execute them at compile time. Therefore, we can define a macro function `linq` that parses the template string into an SQL statement tree, which is then executed as JavaScript AST within the object-oriented XScript syntax.

The result resembles embedding a DSL (Data-centric Super Language) for SQL within TypeScript, achieving a simpler yet closer-to-SQL structure compared to LINQ. However, note that NopOrm currently provides `xpath/jpath/xpl` macro functions but does not include built-in LINQ macros.

## Summary of Output Modes

Template language differs from regular programming languages by treating output as its first-class concept:

- **Without special formatting**: Directly outputs the result.
- **With macro labels**: Explicitly isolates logic for other operations, similar to how `<c:if>` works in Thymeleaf.

Xpl templates, being a generic template language, enhance this concept by adding multi-mode output design.

Xpl supports multiple output modes:

- **text**: Plain text output without additional escaping.
- **xml**: XML-formatted text with proper XML escaping.
- **node**: Structural AST output retaining original code positions.
- **sql**: Support for SQL objects to prevent SQL injection attacks.

The sql mode applies special handling to SQL outputs, adding rules such as:

1. For output objects, replace with `?` and collect the object into parameters. E.g., `id = ${id}` results in `id=?`.
2. For collection objects, expand into multiple parameters. E.g., `id in (${ids})` becomes `id=(?,?)`.

To directly output SQL text without parameterization, use the `raw()` function:

```xml
from MyEntity_${raw(postfix)} o
```

Additionally, NopOrm simplifies parameterized SQL by wrapping objects into a packaging model:

```sql
SQL = Text + Params
```

### 9.5 Automatic Validation

The external file management of SQL templates has a drawback: it cannot rely on the type system for validation and can only expect runtime testing to check syntax correctness. If the data model changes, it may not immediately discover which SQL statements are affected.

For this issue, there are some simple solutions. After all, if SQL statements are managed as structured models, we have much more powerful means at our disposal to manipulate them.
NopOrm has a mechanism similar to Contract Based Programming: Each EQL model supports a validate-input configuration. We can prepare test data in advance, and the ORM engine will automatically execute the validate-input method when loading sql-lib. It uses this test data to validate the SQL template and then generates the corresponding EQL statement for analysis using the EQL parser to ensure its validity through static analysis.

### 9.6 Debugging Support

Unlike MyBatis's built-in simple template language, NopOrm uses Xpl Template Language to generate SQL statements, making it natural to use the XLang debugger for debugging. The Nop platform provides an IDEA plugin that supports syntax suggestions and breakpoint setting in the source section. It automatically reads sql-lib.xdef meta-model definitions to validate the syntax of the sql-lib file and provides syntax suggestion functionality while automatically adding breakpoints in the source section for step-by-step debugging.

All DSLs in the Nop platform are built based on reversible computation principles. They use a unified meta-model defined by the XDefinition language, so there's no need to develop separate IDE plugins or debuggers for each DSL. To add IDE support for a custom sql-lib model, you only need to add an x:schema attribute to the root node, referencing nop/schema/orm/sql-lib.xdef. This will automatically import the corresponding meta-model definition.

XLang also includes some built-in debugging features that make it easier to diagnose issues during the meta-programming phase.

1. outputMode=node: The generated AST nodes will retain the original file's line numbers. If the generated code has a compile-time error, we can directly map it back to the original source file's location.
2. Xpl Template Language nodes can have an xpl:dump attribute that prints out the current node's AST after dynamic compilation.
3. Any expression can be extended by calling the `$` function. This function will automatically print the corresponding text, line number, and execution result of the expression, along with its return value. For example:

```
x = a.f().$(prefix) corresponds to
x = DebugHelper.v(location, prefix, "a.f()", a.f())
```

## 十. GraphQL over ORM

From an abstract perspective, the interaction between frontend and backend is essentially about:
**requesting backend business objects O, passing parameters X, and returning results Y.** If we rephrase this in URL format, it looks similar to:

```
view?bizObj=MyObj&bizAction=myMethod&arg=X
```

Specifically:
- bizObj corresponds to the backend Controller object.
- bizAction corresponds to the method defined in the Controller.
- view indicates the result information being presented, which depends on the data retrieved by the business method from the backend.

For standard AJAX requests, the JSON data format is determined solely by the business method. For generic RESTful services, the choice of view can be more flexible, such as deciding between returning JSON or XML based on the Http ContentType header. If the view is determined by both the business object and method (bizObj and bizAction), we refer to this as a **push mode** request because the client receives pre-determined results. Conversely, if the client can choose which view to retrieve, it's called a pull mode.

The most significant difference between GraphQL and traditional REST or RPC requests lies in its request pattern:

```
selection?bizObj=MyObj&bizField=myField&arg=X
```

GraphQL is a pull-mode request because it specifies which data to return. However, unlike regular REST or RPC, it doesn't require creating new objects but instead works with existing structured data and performs selective fetching (renaming) based on the query's selection set.

This structured selection allows for early validation since the query language can analyze the requested fields upfront. Because of its structured nature, GraphQL is excellent for defining blueprints that guide how business methods execute. Its flexibility also allows for combining requests for multiple business objects in a serialized manner.

From a certain perspective, the logical structure of a Web framework is unique. To achieve effective logic separation, we must distinguish between different business objects in the backend. To enable flexible organization, we must specify the return view. The conclusion is that the URL format should be `view?bizObj=MyObj&bizAction=myAction&arg=X`.

> Many years ago, I wrote a paper analyzing the design principles of the WebMVC framework: [The Evolution and Design of WebMVC](http://www.blogjava.net/canonical/archive/2008/02/18/180551.html). This analysis remains relevant today.

Based on this understanding, the combination of GraphQL and ORM can be extremely simple. In the Nop platform, the GraphQL service can directly map to the bottom-level ORM entities using deterministic mapping rules without requiring programming. On this foundation, we can progressively add other business rules, such as permission filtering, business processes, and data structure adjustments. Specifically, each database table is treated as an alternative business object, and a code generator automatically generates the following code:

```
/entity/_MyObj.java
       /MyObj.java
/model/_MyObj.xmeta
      /MyObj.xmeta
      /MyObj.xbiz
/biz/MyObjBizModel.java
```

* MyObj.java is an automatically generated entity class based on the ORM model. We can directly add auxiliary properties and methods to this entity class.

* MyObj.xmeta defines externalizable business entity data structures. The system generates GraphQL objects' Schema definitions based on it.

* MyObjBizModel.java defines custom GraphQL service response functions and loaders.

* MyObj.xbiz involves more complex business slice concepts, which are not discussed in detail in this text.

In essence, GraphQL and ORM provide different levels of structure. While GraphQL is designed for external perspectives, ORM focuses on internal usage. Therefore, they typically do not share the same Schema definitions. However, in general business applications, they often exhibit significant similarities. **A reversible calculation provides a standardized solution for handling similar yet distinct structures.**

For the aforementioned scenario, the Nop platform's design is that both `_MyObj.java` and `MyObj.xmeta` are directly generated based on the ORM model. These two components maintain synchronized information. MyObj.java inherits from `_MyObj.java` and can add additional visible properties and methods within the application. MyObj.xmeta uses an `x:extends` differential merging mechanism to customize `MyObj.xmeta`. This allows for adding, modifying, or deleting properties and methods in `MyObj.xmeta`, while also enabling renaming via `x:rename`.

For example:

```xml
<meta>
  <props>
    <prop name="propA" x:override="remove" />
    <prop name="propB" mapToProp="internalProp">
      <auth roles="admin" />
      <schema dict="/app/my.dict.yaml" />
    </prop>
  </props>
</meta>
```

In the above example, the `propA` property will be removed, so a GraphQL query cannot access it. Meanwhile, the `internalProp` property is renamed to `propB`, so a GraphQL query fetching `propB` will actually retrieve `internalProp`. The `propB` property includes `<auth roles="admin">`, meaning only admins have access to this property. The `<schema dict="/app/my.dict.yaml">` specifies that the value of this property is limited to the dictionary defined in `my.dict.yaml`.

In Section 5.2, we discussed the dictionary table translation mechanism in NopOrm: during runtime, the bottom-layer engine detects dictionary settings and automatically generates a `propB_text` field, which returns the localized text after dictionary translation.

At the top level of the GraphQL object, the Nop platform automatically generates the following structure:

```graphql
extend type Query {
    MyObj__get(id: String): MyObj
    MyObj__findPage(query: String): PageBean_MyObj
    ...
}
```

In addition to the default `get` and `findPage` operations, we can define additional operations in `MyObjBizModel`.

* MyObjBizModel.java allows us to add extended properties and methods.

* MyObj.xbiz involves more complex business aspects, which are not detailed in this text.

GraphQL and ORM essentially provide different levels of structure. While GraphQL is designed for external perspectives, ORM focuses on internal usage. Therefore, they typically do not share the same Schema definitions. However, in general business applications, they often exhibit significant similarities. **A reversible calculation provides a standardized solution for handling similar yet distinct structures.**

For the aforementioned scenario, the Nop platform's design is that both `_MyObj.java` and `MyObj.xmeta` are directly generated based on the ORM model. These two components maintain synchronized information. MyObj.java inherits from `_MyObj.java` and can add additional visible properties and methods within the application. MyObj.xmeta uses an `x:extends` differential merging mechanism to customize `MyObj.xmeta`. This allows for adding, modifying, or deleting properties and methods in `MyObj.xmeta`, while also enabling renaming via `x:rename`.

```java
@BizModel("MyEntity")
public class MyEntityBizModel {

    @BizLoader("children")
    @BizObjName("MyChild")
    public List<MyChild> getChildren(@ContextSource MyEntity entity) {
        ...
    }

    @BizQuery("get")
    @BizObjName("MyEntity")
    public MyEntity getEntity(
            @ReflectionName("id") String id,
            IEvalScope scope,
            IServiceContext context,
            FieldSelectionBean selection
    ) {
        ...
    }

    @BizQuery
    @BizObjName("MyEntity")
    public PageBean<MyEntity> findPage(
            @ReflectionName("query") QueryBean query
    ) {
        ...
    }
}

@BizModel("MyChild")
public class MyChildBizModel {

    /**
     * 批量加载属性
     */
    @BizLoader("name")
    public List<String> getNames(@ContextSource List<MyChild> list) {
        List<String> ret = new ArrayList<>(list.size());
        for (MyChild child : list) {
            ret.add(child.getName() + "_batch");
        }
        return ret;
    }
}
```

BizModel uses annotations like `@BizQuery` and `@BizMutation` to define GraphQL Query and Mutation operations, with operation names formatted as `{bizObj}__{bizAction}`. It also supports adding fetchers using `@BizLoader`, fetching parent objects via `@ContextSource`, and marking arguments with `@ReflectionName` for automatic type conversion.

If BizModel defines methods like `get/findPage`, it will override the default implementation of `MyObj__get`.

The design of BizModel is decoupled from GraphQL, allowing easy binding to REST or other RPC services. In our implementation, we even support batch processing by binding a batch file, which runs periodically to process batches of 100 records at a time, rather than updating the database after each individual request.

The key optimization here is implementing batch processing efficiently, where a batch of 100 records is processed completely before updating the database, instead of updating it after each individual request. This is achieved using the session mechanism provided by the ORM, making this batch processing entirely free.

## Conclusion

Reverse computation theory is not a smart pattern nor a summary of best practices. It is rooted in the actual physical laws of our world, derived from the first principles through rigorous logical deduction, and represents an innovative technical idea regarding the large-scale structure of software.

Under the guidance of reverse computation theory, NopOrm's solution demonstrates the completeness and consistency of the underlying logic structure, enabling it to tackle a wide range of challenging technical issues with a single swipe. While the intrinsic complexity of systems is not particularly high, configuration-related obstacles and concept conflicts during component interactions can introduce significant accidental complexity.

NopOrm is not designed as a low-code-specific ORM but rather as a tool that smoothly transitions from LowCode to ProCode. It supports code generation during development and dynamic field additions at runtime, while also offering comprehensive storage customization.
# Technical Document Snippet

It is lightweight in terms of functionality, with the primary capabilities including Hibernate + MyBatis + SpringData JDBC + GraphQL. The amount of manually written code is less than 20,000 lines (with a large portion generated automatically due to the platform's low-code development approach for its components).

It is not only suitable for small monolithic projects but also for large-scale distributed systems with high performance and complexity, while providing syntax support for BI systems. Additionally, it supports compilation to native applications using GraalVM.

# NopOrm Overview

NopOrm follows the principle of reversible computation. It allows customization of the underlying model through Delta customization and metaprogramming. Users can accumulate reusable domain models within their specific business domain. It even supports the development of a custom DSL (Domain-Specific Language).

Most importantly, NopOrm is open-source! (Currently in the code organization phase and will be released alongside Nop Platform 2.0.)

# Open Source Announcement

Based on the reversible computation theory, the low-code platform NopPlatform has been open-sourced:

- **Gitee**: [canonical-entropy/nop-entropy](https://gitee.com/canonical-entropy/nop-entropy)
- **GitHub**: [entropy-cloud/nop-entropy](https://github.com/entropy-cloud/nop-entropy)
- **Development Example**: [docs/tutorial/tutorial.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/tutorial/tutorial.md)
- **Reversible Computation Theory and Nop Platform Introduction**: [reversible computation principle and Nop platform introduction (Bilibili video)](https://www.bilibili.com/video/BV1u84y1w7kX/)

