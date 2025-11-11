# What kind of ORM engine does a low-code platform need? (2)

[Continuation of the previous article](https://zhuanlan.zhihu.com/p/543252423). In the previous article, I conducted an initial theoretical analysis of ORM design, proposed the minimal extension for SQL: the EQL Object Query Language, and then implemented various user-customizable dynamic storage structures based on EQL. In this article, I will first introduce some feature trade-offs made in the NopOrm engine and how, under these trade-offs, to address common ORM performance issues. I will then describe how to implement a customizable Dialect, how to implement MyBatis-like SQL management in about 200 lines of code, as well as GraphQL integration and visual integration.

## 4. Less is More

There has long been a rumor that Hibernate is easy to get started with but hard to master. But isn’t that true of any technology? Hibernate’s problem is that it seems to provide too many choices and keeps forcing us to choose. For example, when mapping association tables to collection objects, which option is best among Set/Bag/List/Collection/Map? Should delete/update/insert operations cascade to associated entities? Does removing from a collection also mean removing from the database? Should associated objects be loaded eagerly or lazily? This freedom of choice can be agonizing for those with OCD or choice anxiety. What if we choose wrong? What if changing our choice affects others’ code? What if we regret it?

If we constantly make choices, but each choice leads to irreversible consequences, then abundant choices are more likely to bring deep regret rather than a happy life.

The NopOrm engine drastically reduces the programmer’s decision points by excluding encapsulations that the application layer can accomplish by itself from the engine kernel. For example, is there any necessity to force mapping an association table to a List, map an index field to list element indices, and additionally supplement a bunch of List-related HQL special query syntax?

### 4.1 ORM Automatic Mapping

The first design decision of the NopOrm engine is: the physical database model can be automatically mapped to the Java entity model without any additional design decisions. We do not base this on the logical database model, because the path from logical to physical is indeterminate and requires additional information. Starting from the physical model, however, the mapping can be done automatically without further choices.

> The physical model itself is the cumulative result of various design decisions and will remain stable in the future. If the ORM mapping is based on the logical model, it essentially repeats the decision-making process.

Specifically, NopOrm maps every database column to a Java property on the entity (same as MyBatis), while each foreign-key association is mapped to a lazily loaded entity object. In other words, the same field may be mapped to multiple properties: one atomic field property and one (or more) association object property. They automatically stay in sync. If the atomic field property is updated, the associated object is set to null, and the next time the association is accessed, it will be looked up from the session.

If a foreign-key association explicitly marks that a one-to-many collection property should be generated, a Set-type property is automatically created (no option to choose among different collection types). According to basic ORM principles, object references within the same Session maintain uniqueness, so they naturally form a Set. Mapping to other collection types would require additional assumptions. Because equality is based on pointer equality, we do not need to override equals() on entity objects.

Only when we explicitly need Component/Computed/Alias do we add the corresponding configuration, and these configurations are incremental: their presence or absence does not affect prior field and association mappings, nor the database schema definition itself. Because NopOrm’s implementation adheres to the principles of Reversible Computation, these incremental configurations can be expressed in Delta files without modifying the original model design files.

### 4.2 Goodbye, POJO

The second major design decision of NopOrm is to abandon the POJO assumption. POJO (Plain Old Java Object) was vital for Hibernate back in the day because it helped Hibernate break free from the EJB (Enterprise Java Bean) container environment and ultimately dismantle the EJB ecosystem. But POJO alone is insufficient. Hibernate must enhance Java entity objects via AOP (Aspect-Oriented Programming), add auxiliary functions, and maintain an EntityEntryMap in memory to manage auxiliary state data.

In a low-code context, entity classes themselves are code-generated, and AOP is essentially another form of code generation (often using runtime bytecode generation). In that case, why not just generate the final code in one pass? Is it necessary to split it into two different generation stages?

As technology evolves, the hidden costs of POJO keep increasing, further eroding the rationale for using it.

1. AOP’s bytecode generation is slow and not conducive to debugging.

2. Using POJOs requires reflection, which has significant performance overhead, and native Java technologies like GraalVM should avoid reflection as much as possible.

3. POJO objects cannot maintain complex persistent state, making optimization difficult. For example, Hibernate cannot determine whether an entity has changed by a simple dirty flag; it is forced to keep a copy of object data in memory. Each session flush must traverse objects and compare each property against the copy. This impacts performance and consumes more memory.

4. To implement necessary business features, we often end up having entity classes inherit from a common base class, which breaks the POJO assumption. For example, adding dynamic property mapping or automatically recording pre- and post-modification field data requires the base class to provide members and methods.

5. Collection properties are performance-unfriendly and error-prone. Collections are typically initialized as HashSet. Once the object is associated with a session, the collection is automatically replaced by the ORM engine’s internal PersistSet implementation, essentially creating a new collection to replace the POJO’s original. Also, by ORM principles, to support lazy loading, a collection must be bound to a specific entity, so you cannot directly assign one entity’s collection property to another’s. But POJOs expose get/set methods, making misuses easy. For example, otherEntity.setChildren(myEntity.getChildren()) is wrong because the set returned by myEntity.getChildren() is bound to myEntity and cannot be used as a property of otherEntity.

All entity classes in NopOrm are required to implement the IOrmEntity interface, and a default implementation OrmEntity is provided.

[IOrmEntity](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-persistence/nop-orm/src/main/java/io/nop/orm/IOrmEntity.java)

Every column model has a unique propId attribute, and you can use IOrmEntity.orm\_propValue(int propId) instead of reflection to access property data.

All collection properties are of type OrmEntitySet, which implements the IOrmEntitySet interface. When generating code, only the getter is generated for entity collection properties, not the setter, eliminating misuse.

[IOrmEntitySet](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-persistence/nop-orm/src/main/java/io/nop/orm/IOrmEntitySet.java)

For each entity, two Java classes are generated, e.g., SimsExam and \_SimsExam. The \_SimsExam class is overwritten each time, while SimsExam preserves existing content if already present, so custom code can be written in SimsExam. See:

[\_SimsExam](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-orm/src/test/java/io/nop/app/_gen/_SimsExam.java)

[SimsExam](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-orm/src/test/java/io/nop/app/SimsExam.java)

### 4.3 Lazy and Cascade All

In NopOrm, all associated entities and collections are lazily loaded, and class inheritance is not supported. This design dramatically simplifies the internal implementation of the ORM engine and lays the foundation for unified batch loading optimization.

In the column definition of the entity model, you can add the lazy setting to specify that the column is lazily loaded. By default, when an entity is first loaded, only eager properties are loaded; lazy properties are loaded only when actually used. The engine also provides batch preloading to explicitly load specific columns in one shot to avoid multiple database calls.

EQL does not include eager fetch syntax because using eager fetch can cause SQL statements to deviate from expectations. For example, if child table records are joined when loading the main table, the number of rows returned increases, and there is a lot of redundant data, which is not performance-friendly. In NopOrm, optimization is implemented uniformly via a batch loading queue provided by BatchLoadQueue.

In Hibernate, cascade is triggered by actions, e.g., calling session.save cascades save actions on associations. This was likely motivated initially by performance optimization—for instance, skipping cascade on some properties. But action-based triggering can lead to unexpected results. For example, after save, if the entity is modified again, it might generate two SQL statements: one insert and one update, when originally just one insert would suffice.

Hibernate’s FlushMode setting can also produce confusing results. By default, FlushMode is auto, where Hibernate decides whether to proactively flush. Subtle but equivalent adjustments in Java code can mislead Hibernate into thinking a flush is needed, emitting a large number of SQL calls and causing serious performance issues.

NopOrm’s design philosophy is thoroughly lazy. It removes the FlushMode concept and flushes only when session.flush() is explicitly called. Combined with the OrmTemplate pattern, a template method triggers session.flush() before transaction commit, improving the predictability of the ORM engine’s behavior at the conceptual level.

NopOrm employs state-driven cascading: per operation no cascade is executed; cascade is executed once for all entities during session.flush. It uses dirty flags for pruning: if no instances of a type were modified, that type’s dirty flag is false and all instances of that type are skipped during flush. If no entities in the entire session were modified, the global dirty flag is false and session.flush() is skipped entirely.

Action-triggered cascade in Hibernate has another side effect: it is hard to precisely control the execution order of SQL statements. In NopOrm, actions produced during flush are cached in an actionQueue and then executed after being sorted according to topological dependencies among database tables. This ensures a consistent table sequence during updates and can help avoid database deadlocks.

> Deadlocks generally occur when thread A updates table A then table B, while thread B updates table B then table A. Executing database updates in a deterministic order acts like lock ordering.

### 5. More is Better

NopOrm gives up many features offered by Hibernate, but it provides many features that Hibernate lacks but are very common in business development and often require significant effort. The difference is that all these features are optional, and whether they are enabled has no impact on other implemented functions.

### 5.1 Good parts of Hibernate

NopOrm inherits several excellent designs from Hibernate and Spring:

1. Second-level cache and query cache: cache size is limited by default to avoid OOM.

2. Composite primary keys: Composite PKs are hard to avoid in business systems. NopOrm includes OrmCompositePk and auto-generates builder helpers and conversions between String and OrmCompositePk, simplifying usage.

3. Primary key generator: If a column model is marked with seq, a primary key can be auto-generated in Java code. If the entity already has a primary key set, the user value takes precedence. Unlike Hibernate, NopOrm uses a global SequenceGenerator.generate(entityName) to generate primary keys, facilitating dynamic adjustments at runtime. NopOrm drops support for database auto-increment keys because many databases don’t support it and it has issues in distributed environments.

4. JDBC Batch: automatically merges database update statements to reduce round-trips. In debug mode, prints exact SQL parameters before merging to aid diagnosis.

5. Optimistic locking: updates via update xxx set version=version+1 where version=:curVersion to avoid concurrent modification conflicts.

6. Template method pattern: improves coordination among JdbcTemplate/TransactionManager/OrmTemplate, reduces redundant wrapping, and adds async support so OrmSession can be used in async contexts.

7. Interceptor: OrmInterceptor’s preSave/preUpdate/preDelete can intercept single-entity operations inside the ORM engine, providing trigger-like functionality.

8. Pagination: uses Dialect to unify cross-database pagination and adds MySQL-like offset/limit to EQL.

9. SQL compatibility: leverages Dialect to translate cross-database SQL syntax, including formatting and function translation.

### 5.2 An ORM That Better Understands Requirements

Common business requirements can be easily implemented via an ORM engine, so NopOrm provides out-of-the-box support, no extra plugins needed.

1. Multi-tenancy: adds tenantId filter to tenant-enabled tables and forbids cross-tenant data access.

2. Database/table sharding: dynamically select shards via IShardSelector.

3. Logical delete: converts delete into setting delFlag=1, and auto-adds delFlag=0 filtering to common queries.

4. Timestamps: automatically records modifier and modification time, etc.

5. Change logs: via OrmInterceptor intercepting entity modifications, you can get pre- and post-modification field values and record them in a separate change-log table.

6. History table support: adds revType/beginVer/endVer fields, assigns a start and end version per record; modifications are turned into inserting a new record, and the previous record’s endVer is set to the latest record’s startVer. Common queries auto-add filters to retrieve only the latest version.

7. Field encryption: add an enc tag on the column model to indicate encrypted storage. The system uses a custom IDataParameterBinder to read database fields, storing encrypted values in the database while decrypted in Java properties. The EQL parser, via type analysis, transparently uses the encode binder to encrypt/decrypt SQL parameters.

8. Sensitive data masking: add a mask tag to fields like card numbers or IDs to automatically mask them in system logs.

9. Component logic reuse: a related set of fields may form a reusable component. OrmComponent enables reuse of such logic. For example, Decimal precision must be specified in the database, but customers may require display and computation with input-specified precision. This requires a VALUE\_SCALE column to store precision, while we want BigDecimal values fetched with the desired scale already set. NopOrm provides a FloatingScaleDecimal component for this. Complex fields such as attachment and attachment lists can be similarly encapsulated.

   [FloatingScaleDecimal](https://gitee.com/canonical-entropy/nop-entropy/blob/master)

Combined with outer frameworks, the Nop platform also includes more common solutions, e.g.:

1. Universal query: without writing front/back-end code, as long as the form submits a query in a certain format, the backend validates format and permissions based on meta configuration, and upon passing returns results in GraphQL format.

2. Change confirmation and approval: combined with CRUD and API invocation services, user submissions do not directly modify the database or invoke APIs. Instead, an approval request is produced automatically; approvers see pre- and post-change content on the approval page, and upon approval subsequent actions are executed. Any form page can be converted into a submission page plus approval page via this scheme.

3. Copy to create: create complex business objects by copying existing ones, with fields to copy specified via a GraphQL-like selection.

4. Dictionary translation: front-end display needs to translate fields like statusId via dictionary tables and choose locale-specific text. During meta-programming, the Nop platform automatically discovers fields configured with dict and augments the GraphQL description with an associated display-text field, e.g., add statusId\_text according to statusId. The front-end can query statusId\_text for translated text while statusId returns the raw value.

5. Batch import/export: import via CSV/Excel uploads; import logic is identical to manual submission via UI and automatically validates data permissions. Export to CSV or Excel.

6. Distributed transactions: automatic integration with a TCC distributed transaction engine.

NopOrm follows the principles of Reversible Computation, so its underlying models are customizable. Users can add custom attributes to models at any time and then leverage them via meta-programming, code generators, etc. Many features above are implemented through such mechanisms; many are not part of the engine kernel, but introduced via customization.

### 5.3 Embrace the Asynchronous New World

Traditionally, JDBC interfaces are all synchronous, so JdbcTemplate and HibernateTemplate are also synchronous. But as asynchronous high-concurrency programming spreads, reactive programming has entered mainstream frameworks. Spring now proposes the [R2DBC standard](https://r2dbc.io/), and the [vertx framework](https://vertx.io/) includes [asynchronous connectors](https://vertx.io/docs/vertx-pg-client/java/) for MySQL, PostgreSQL, etc. On the other hand, if an ORM engine acts as a data fusion access engine, its storage might be async-supported NoSQL sources (Redis, ElasticSearch, MongoDB). ORM also needs to cooperate with a GraphQL async execution engine. Given these, NopOrm’s OrmTemplate adds an asynchronous invocation mode:

```java
 public interface IOrmTemplate extends ISqlExecutor {
    <T> CompletionStage<T> runInSessionAsync(
          Function<IOrmSession, CompletionStage<T>> callback);
 }
```

OrmSession is designed to be thread-unsafe: only one thread may access it at a time. To support multi-threaded access to a thread-unsafe structure, a basic design is an Actor-like task queue:

```java
class Context{
    ContextTaskQueue taskQueue;

    public void runOnContext(Runnable task) {
        if (!taskQueue.enqueue(task)) {
            taskQueue.flush();
        }
    }
}
```

Context is a cross-thread object with an associated task queue. At any moment, only one thread executes tasks registered in that queue. runOnContext registers a task; if no other thread is executing the queue, the current thread executes it.

> For recursion, taskQueue acts like a [trampoline function](https://zhuanlan.zhihu.com/p/142241289).

With an async Context, we can also improve timeout handling for remote calls. After a client-side timeout, the client throws an exception or retries, but the server may be unaware and continues. Service functions typically access the database multiple times. Combined with retry traffic, this can cause much higher DB pressure than non-timeout scenarios. An improved strategy is to add a timeout attribute on Context:

```java
class Context{
    long callExpireTime;
}
```

For cross-system RPC, pass a timeout interval in the message header. On the server, set callExpireTime = currentTime + timeout after receiving it. Then in JdbcTemplate, before each DB request, check whether callExpireTime has been reached to detect server-side timeouts promptly. If the server calls a third-party API, recompute timeout = callExpireTime - currentTime for the remaining interval and pass it to the third-party.

### 5.4 Delta Customization of Dialect

NopOrm encapsulates cross-database differences via a Dialect model.

[default dialect](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-persistence/nop-dao/src/main/resources/_vfs/nop/dao/dialect/default.dialect.xml)

[mysql dialect](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-persistence/nop-dao/src/main/resources/_vfs/nop/dao/dialect/mysql.dialect.xml)

[postgresql dialect](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-persistence/nop-dao/src/main/resources/_vfs/nop/dao/dialect/postgresql.dialect.xml)

As in the examples above, mysql.dialect.xml and postgresql.dialect.xml both extend default.dialect.xml. Compared to programmatic Dialect construction in Hibernate, using dialect model files yields higher information density and more intuitive expression. More importantly, in postgresql.dialect.xml you can clearly see what is added/modified/removed relative to default.dialect.xml.

Because the entire Nop platform is built on the principles of Reversible Computation, dialect model file parsing and validation can be done by a generic DslModelParser, with automatic support for Delta customization—that is, without modifying default.dialect.xml or any references to default.dialect.xml (e.g., no need to modify x:extends in postgresql.dialect.xml), we can add a default.dialect.xml under /_delta to customize system built-in model files:

```xml
<!-- /_delta/myapp/nop/dao/dialect/default.dialect.xml -->
<dialect x:extends="raw:/nop/dao/dialect/default.dialect.xml">
  Only describe the Delta changes here
</dialect>
```

Delta customization is like overlay fs in Docker, allowing multiple Delta layers to be stacked. Unlike Docker, Delta customization applies not only at the file layer but also extends to delta structural operations within files. With xdef meta-model definitions, all model files in the Nop platform automatically support Delta customization.

### 5.5 Visual Integration

Hibernate’s hbm files and JPA annotations are designed for mapping to database structures and are not ideal for visual model design. Adding a visual designer to Hibernate is relatively complex.

NopOrm uses orm.xml to define entity models. First, it’s a complete structural definition model that can generate DDL scripts, automatically diff against current DB structure, and perform migrations.

[app.orm.xml](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-orm/src/test/resources/_vfs/nop/test/orm/app.orm.xml)

Adding a visual designer to NopOrm is very simple—so simple that it only requires adding one meta-programming tag invocation:

```xml
<orm ... >
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

[Pdman](http://www.pdman.cn/) is an open-source DB modeling tool that stores models as JSON. `<pdman:GenOrm>` is an XPL template tag executed at compile-time meta-programming; it generates an orm model from pdman’s JSON. Generation is effective immediately: modifying test.pdma.json invalidates the OrmModel parsing cache, and the model is re-parsed upon next access.

According to Reversible Computation, a visual design interface is merely a graphical representation of a domain model, while a model file is a textual representation. Reversible Computation states that a model can have multiple representations and that visual editing is simply a reversible conversion between graphical and textual representations. From this reasoning, we can deduce that a model’s visual display form is not unique; multiple different visual designers can be used to design the same model object.

For orm models, besides pdman, we can use PowerDesigner and convert its pdm model to orm via a similar `<pdm:GenOrm>` tag.

On the Nop platform, we also support defining entity models via Excel.

[test.orm.xlsx](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-orm/src/test/resources/_vfs/nop/test/orm/test.orm.xlsx)

Likewise, simply introduce the `<orm-gen:GenFromExcel>` tag and you can happily design ORM models in Excel.

```xml
<orm ...>
  <x:gen-extends>
     <orm-gen:GenFromExcel path="test.orm.xlsx" />
  </x:gen-extends>
</orm>
```

Notably, Excel model parsing in the Nop platform is also based on Reversible Computation. It treats parsing Excel files as a functor mapping from the Excel category to the DSL AST (Abstract Syntax Tree) category (an equivalent representation transformation), enabling a generic Excel model parser. With only structural info from the orm meta-model file and no special coding, Excel models can be parsed. This mechanism is fully general: for any model file defined in Nop, we can obtain its Excel visual editing model “for free,” and the Excel format is relatively flexible—cell positions, styles, order can be freely adjusted—as long as they can be recognized as a tree structure by deterministic rules.

For more on model transformation, refer to:

[Designing a low-code platform through the lens of tensor product](https://zhuanlan.zhihu.com/p/531474176)

Model info in the Nop platform can also be exported via generic Word templates; technical details:

[How to implement a poi-tl-like Word template in about 800 lines of code](https://zhuanlan.zhihu.com/p/537439335)

All business features in the Nop platform are model-driven, so analyzing model info can export a wealth of useful documents, e.g., database model docs, data dictionaries, API docs, unit test docs, etc.

### 6. The Infamous N+1 Problem

Ever since Hibernate was born, the N+1 problem has been a dark cloud over ORM engines. Suppose we have this model:

```java
class Customer{
    Set<Order> orders;
}

class Order{
   Set<OrderDetail> details;
}
```

If we want to process a customer’s order details, we need to traverse the orders collection:

```
Customer customer = ... // assume customer has been loaded
Set<Order> orders = customer.getOrders();
for(Order order: orders){
    process(order.getDetails());
}
```

Loading orders from customer triggers one SQL. Traversing orders and getting details for each order triggers one SQL per order. The process results in N+1 queries.

The N+1 problem is notorious because during development the dataset is small and performance issues are often ignored. When issues are discovered after going live, we lack any local remediation approaches—changes often require rewriting code or even fundamentally redesigning the program.

This problem plagued Hibernate until many years later, when the JPA (Java Persistence API) standard proposed the EntityGraph concept:

```java
@NamedEntityGraph(
   name = "customer-with-orders-and-details",
   attributeNodes = {
       @NamedAttributeNode(value = "orders", subgraph = "order-details"),
   },
   subgraphs = {@NamedSubgraph(
       name = "order-details",
       attributeNodes = {
           @NamedAttributeNode("details")
       }
   )}
)
@Entity
class Customer{
    ...
}
```

Add NamedEntityGraph on the entity class to declare that orders and their details should be loaded in one shot. Then specify which EntityGraph to use when calling find:

```java
EntityGraph entityGraph = entityManager.getEntityGraph("customer-with-orders-and-details");
Map<String,Object> hints = new HashMap<>();
hints.put("javax.persistence.fetchgraph", entityGraph);
Customer customer = entityManager.find(Customer.class, customerId, hints);
```

Besides annotations, EntityGraph can be constructed via code:

```java
EntityGraph graph = entityManager.createEntityGraph(Customer.class);
Subgraph detailGraph = graph.addSubgraph("order-details");
detailGraph.addAttributeNodes("details");
```

This produces SQL similar to:

```sql
select customer0.*,
       order1.*,
       detail2.*
from
     customer customer0
       left join order order1 on ...
       left join order_detail detail2 on ...
 where customer0.id = ?
```

Hibernate fetches all data via a single SQL, at the cost of multiple table joins and a lot of redundant data.

Is there another solution? From the data model structure, the nested Customer -> orders -> details is intuitive and fine. The problem is we are constrained to traverse the object structure as defined and can only do so incrementally, which triggers many queries. If we could bypass the object structure, directly fetch object data via some mechanism, and then assemble it in memory according to the desired object shape, wouldn’t it solve the problem?

```java
Customer customer = ...
// inject a magical data-fetch-and-assemble instruction
fetchAndAssembleDataInAMagicalWay(customer);
// data now exists in memory safely; traversals no longer trigger loading
Set<Order> orders = customer.getOrders();
for(Order order: orders){
    process(order.getDetails());
}
```

NopOrm provides a batch property-loading API via OrmTemplate:

```java
ormTemplate.batchLoadProps(Arrays.asList(customer), Arrays.asList("orders.details"));
// data now exists in memory safely; traversals no longer trigger loading
Set<Order> orders = customer.getOrders();
```

Internally, OrmTemplate implements this via an IBatchLoadQueue:

```java
IBatchLoadQueue queue = session.getBatchLoadQueue();
queue.enqueue(entity);
queue.enqueueManyProps(collection,propNames);
queue.enqueueSelection(collection,fieldSelection);
queue.flush();
```

BatchLoadQueue works similarly to GraphQL’s DataLoader: collect entities or entity collections to load, then fetch in bulk via select xxx from ref__entity where ownerId in :idList, and split results by ownerId across objects and collections. Because BatchLoadQueue has the full entity model and unified loaders, its implementation is more optimized than DataLoader. At the external API level, less information needs to be expressed. For example, orders.details means load orders first, then load details, and retrieve all eager properties of OrderDetail. If described in GraphQL, you must specify which fields of OrderDetail to fetch explicitly, making it more complex.

> BatchLoadQueue was not inspired by GraphQL. GraphQL was open-sourced in 2015; we were already using BatchLoadQueue before that.

If the number/depth of entities to load is large, id-based bulk loading may still have performance impact. NopOrm retains an experts-only backdoor:

```java
 session.assembleAllCollectionInMemory(collectionName);
 // or
 session.assembleCollectionInMemory(entitySet);
```

assembleAllCollectionInMemory assumes all involved entities have already been loaded into memory. It no longer accesses the database and determines collection elements directly by filtering in-memory data. As for how to load all related entities into memory, there are many options. For example:

```java
orm().findAll(new SQL("select o from Order o"));
orm().findAll(new SQL("select o from OrderDetail o"));
session.assembleAllCollectionInMemory("test.Customer@orders");
session.assembleAllCollectionInMemory("test.Order@details");
```

> This approach is risky: if all associated entities aren’t loaded before assemble, the assembled collections will be incorrect.

If we revisit the SQL generated by EntityGraph, it corresponds to the following EQL:

```sql
select c, o, d
from Customer c left join c.orders o left join o.details
where c.id = ?
```

By ORM principles, although the query returns many duplicate Customer and Order objects, they share the same primary keys, so only a single instance is kept in memory. If a Customer or Order was loaded previously, its data takes precedence; data from this query is ignored.

> That is, ORM yields an effect similar to the [Repeatable Read isolation level](https://zhuanlan.zhihu.com/p/150107974): repeated reads return the same result, and the ORM engine retains the first read. For Load X, Update X, Load X, the second load’s data is discarded, so what we observe is the first load plus our subsequent modifications—effectively [Read-your-writes causal consistency](https://zhuanlan.zhihu.com/p/59119088).

From this, EntityGraph’s process is equivalent to:

```sql
orm().findAll(new SQL("select c,o,d from Customer c left join ..."));
session.assembleSelectionInMemory(c, FieldSelectionBean.fromProp("orders.details"));
// assembleSelection is equivalent to:
session.assembleCollectionInMemory(c.getOrders());
for(Order o: c.getOrders()){
    session.assembleCollectionInMemory(o.getDetails());
}
```

## 7. QueryBuilder Is Important, but Unrelated to ORM

Some believe [the value of ORM largely lies in QueryBuilder](https://www.zhihu.com/question/23244681/answer/2426095608). I think that’s a misconception. QueryBuilder is useful simply because Query objects need to be modeled. In the Nop platform we provide a QueryBean model object that supports:

1. On the frontend, QueryBean corresponds to QueryForm and QueryBuilder controls, which can construct complex query conditions directly.

2. Backend data-permission filters can be injected into QueryBean, offering clear structure and avoiding SQL injection vs. string concatenation. queryBean.appendFilter(filter)

3. QueryBean supports custom query operators and fields and can convert them to built-ins via queryBean.transformFilter(fn). For example, define a virtual field myField, then query in-memory state data and other associated tables, converting to a subquery condition. Thus, under a single-table query framework you can achieve multi-table join effects.

4. DaoQueryHelper.queryToSelectObjectSql(query) transforms query conditions into SQL.

5. QueryBeanHelper.toPredicate(filter) converts filters into Predicate interfaces for direct Java filtering.

6. Via and/eq in FilterBeans and property-name constants generated at code-gen time, you can build compile-time-safe expressions:

   filter = and(eq(PROP\_NAME\_myFld,"a"), gt(PROP\_NAME\_otherFld,3))

QueryBuilder is essentially unrelated to ORM because even completely detached from relational databases and SQL, Query models are still useful. For example, in business rules configuration:

```xml
<decisionTree>
    <children>
      <rule>
        <filter>
          <eq name="message.type" vaule="@:1" />
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

You can directly reuse the frontend QueryBuilder to visually configure backend decision rules.

Constructing SQL in Java via so-called QueryDSL has no real advantage. In a model-driven approach, just use the frontend-provided QueryBean, and for minor additions use static combinators and/or/eq in FilterBeans. For truly complex SQL, adopt a MyBatis-like approach of managing statements in external files; in sql-lib we can achieve a level of intuitiveness, flexibility, and extensibility that QueryDSL cannot match (details below).

[QueryBean](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-kernel/nop-api-core/src/main/java/io/nop/api/core/beans/query/QueryBean.java)

## 8. Can ORM Handle OLAP?

There’s a common claim that ORM suits only OLTP, being powerless for complex OLAP queries. Yet some insist on using ORM—for faster, higher, stronger!

> Honestly, is writing summary/analysis SQL easy? Many joins and subqueries are merely to organize data along some dimension. Splitting into multiple queries and assembling in code can be simpler.

[Raqsoft Reports](http://www.raqsoft.com.cn/about#aboutme) is a unique company. Founder Jiang Buxing is a legendary figure in Chinese history (winner of China’s first IMO gold medal, from Shihezi, Xinjiang; see [Prof. Gu Xianfeng’s recollection](https://blog.sciencenet.cn/blog-2472277-1160241.html)). He invented the theory behind Chinese-style reporting and led a generation of reporting software. Although for various reasons Raqsoft’s later development fell short, it published many unique insights.

Raqsoft open-sourced a [front-end BI system](http://www.raqsoft.com.cn/r/os-bi). Though not pretty, technically it proposes a nifty DQL (Dimensional Query Language). See:

[Say goodbye to wide tables—DQL enables a new generation of BI (Qian Academy)](http://c.raqsoft.com.cn/article/1653901344139?p=1&m=0)

Raqsoft argues that end users struggle with complex SQL JOINs, and for multi-dimensional analysis must use wide tables, complicating data preparation. DQL simplifies the mental model of JOIN for end users and offers performance advantages over SQL.

Take finding American employees managed by Chinese managers:

```sql
-- SQL
SELECT A.*
FROM  Employee A
JOIN Department ON A.dept_id = Department.id
JOIN  Employee C ON Department.manager_id = C.id
WHERE A.nationality = 'US' AND C.nationality = 'CN'

-- DQL
SELECT *
FROM Employee
WHERE nationality='US' AND department.manager.nationality='CN'
```

The key idea is: foreign-key attributization, i.e., fields of the table referenced by a foreign key can be used as subproperties, allowing multiple layers and recursion.

Another example: given Orders and Area, query the shipping city name, province name, and region name:

```sql
-- DQL
SELECT
    send_city.name AS city,
    send_city.pid.name AS province,
    send_city.pid.pid.name AS region
FROM
    orders
```

The second key idea: same-dimension table equivalence. For one-to-one associated tables, no explicit join conditions are needed; fields can be considered shared. For example, querying all employees’ income:

```sql
-- SQL
SELECT e.name, e.salary + m.allowance
FROM Employee e
LEFT JOIN Manager m ON e.id = m.id

-- DQL
SELECT name, salary + allowance
FROM Employee
```

The third key idea: subtable setification. For example, order details can be viewed as a collection field of Orders. To compute each order’s total amount:

```sql
-- SQL
SELECT o.order_id, o.customer, SUM(d.price)
FROM Orders o
JOIN OrderDetails d ON o.order_id = d.order_id
GROUP BY o.order_id, o.customer

-- DQL
SELECT order_id, customer, order_details.SUM(price)
FROM Orders
```

If there are multiple child tables, SQL needs a separate GROUP per child, then join them with the main table, often as subqueries. DQL remains simple—just add fields after SELECT.

The fourth key idea: data naturally aligned by dimension. We don’t need explicit join conditions; data can sit in the same result table not because of prior associations but because they share the leftmost dimensional coordinate. For example, we want to aggregate contract amount, payment amount, and inventory amount by date. We fetch data from three tables and align by date:

```sql
-- SQL
SELECT T1.date, T1.amount, T2.amount, T3.amount
FROM (SELECT date, SUM(amount) AS amount FROM Contracts GROUP BY date) T1
LEFT JOIN (SELECT date, SUM(amount) AS amount FROM Payments GROUP BY date) T2
ON T1.date = T2.date
LEFT JOIN (SELECT date, SUM(amount) AS amount FROM Inventory GROUP BY date) T3
ON T2.date = T3.date

-- DQL
SELECT Contracts.SUM(amount), Payments.SUM(amount), Inventory.SUM(amount) ON date
FROM Contracts BY date
LEFT JOIN Payments BY date
LEFT JOIN Inventory BY date
```

In DQL, dimensional alignment can be combined with foreign-key attributization, e.g.:

```sql
-- DQL
SELECT Salesperson.COUNT(1), Contracts.SUM(amount) ON region
FROM Salesperson BY region
JOIN Contracts BY Customers.region
SELECT Salesperson.COUNT(1), Contracts.SUM(amount) ON region
FROM Salesperson BY region
JOIN Contracts BY Customers.region
```

From a NopOrm perspective, DQL is essentially an ORM design:

1. DQL requires designers to define PK/FK associations and specify display names per field—same as ORM model design.

2. DQL’s FK attributization, same-dimension equivalence, and subtable setification are essentially EQL’s object property association syntax, but using DB-association fields as association names. This is simpler but less convenient with composite PKs.

3. Dimensional alignment is an interesting idea. Implementation likely loads via multiple SQLs and merges in memory via hash joins—fast. Especially with pagination: page only the main table, and child tables fetch via IN only records involved on this page, which can greatly speed up in large tables.

Implementing DQL on EQL is straightforward. After reading Raqsoft’s article, I spent a weekend building an MdxQueryExecutor to execute dimensional alignment queries. Because EQL already supports object property associations, we only need to implement QueryBean splitting, sharded execution, and data coalescing.

## 9. SQL Template Management: You Deserve It

When constructing complex SQL/EQL, managing them via an external model file is valuable. MyBatis provides such SQL modeling, yet many still prefer dynamic SQL assembly via QueryDSL in Java—indicating that MyBatis’s implementation is thin and doesn’t fully exploit modeling advantages.

In NopOrm, we manage all complex SQL/EQL/DQL via sql-lib. Leveraging the Nop platform’s infrastructure, an equivalent to MyBatis’s SQL management takes about 200 lines of code. See:

[SqlLibManager](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-persistence/nop-orm/src/main/java/io/nop/orm/sql_lib/SqlLibManager.java)

[SqlItemModel](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-persistence/nop-orm/src/main/java/io/nop/orm/sql_lib/SqlItemModel.java)

[SqlLibInvoker](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-persistence/nop-orm/src/main/java/io/nop/orm/sql_lib/proxy/SqlLibInvoker.java)

Test sql-lib:

[test.sql-lib.xml](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-orm/src/test/resources/_vfs/nop/test/sql/test.sql-lib.xml)

Features of sql-lib:

### 9.1 Unified Management of SQL/EQL/DQL

sql-lib includes three node types—sql/eql/query—for SQL, EQL, and DQL (from the previous section), all managed uniformly.

```xml
<sql-lib>
  <sqls>
     <sql name="xxx" > ... </sql>
     <eql name="yyy" > ... </eql>
     <query name="zz" > ... </query>
  </sqls>
</sql-lib>
```

The first benefit of modeling is the Nop platform’s built-in Delta customization. Suppose we developed a Base product; upon deployment at a customer site, we need to optimize SQL for their data. We need not modify any Base code—just add a delta sql-lib model to customize any SQL. For example:

```xml
<sql-lib x:extends="raw:/original.sql-lib.xml">
   <sqls>
      <!-- SQL with the same name overrides definitions in the base file -->
      <eql name="yyy"> ...</eql>
   </sqls>
</sql-lib>
```

Another common Delta use is combined with meta-programming. If the system’s domain model is regular, with many similar SQLs, we can auto-generate them at compile time via meta-programming, then improve via Delta customization. For example:

```xml
<sql-lib>
   <x:gen-extends>
       <app:GenDefaultSqls ... />
   </x:gen-extends>

  <sqls>
     <!-- Customize auto-generated SQL here -->
     <eql name="yyy">...</eql>
  </sqls>
</sql-lib>
```

### 9.2 Component Abstraction in XPL Templates

MyBatis only offers a few fixed tags such as foreach/if/include. Writing highly complex dynamic SQL becomes cumbersome. Many find XML SQL concatenation awkward because MyBatis is an incomplete solution—it lacks a mechanism for secondary abstraction. In Java, we can always reuse SQL assembly logic via functions, whereas MyBatis has only three rudimentary built-ins and almost no auxiliary reuse capabilities.

NopOrm uses XLang’s XPL template language as the generation engine and thus inherits tag abstraction capabilities.

> XLang is a language born for Reversible Computation. It includes XDefinition/XScript/Xpl/XTransform, centering on AST generation, transformation, and delta merge—think of it as a language designed for tree grammars.

```xml
<sql name="xxx">
  <source>
   select <my:MyFields />
       <my:WhenAdmin>
         ,<my:AdmninFields />
       </my:WhenAdmin>
   from MyEntity o
   where <my:AuthFilter/>
  </source>
</sql>
```

XPL not only has built-ins like `<c:for>` and `<c:if>` for Turing-complete logic but also lets you introduce new tag abstractions via custom tags (analogous to Vue component encapsulation).

Some template languages require all functions used in templates to be registered in advance. XPL can call Java directly:

```xml
<sql>
  <source>
    <c:script>
       import test.MyService;

       let service = new MyService();
       let bean = inject("MyBean"); // directly get a bean from the IoC container
    </c:script>
  </source>
</sql>
```

### 9.3 Macro Tags for Meta-Programming

MyBatis’s dynamic SQL concatenation is clumsy, so some MyBatis-like frameworks provide special simplified syntax in the SQL template layer. For example, some introduce implicit condition handling:

```sql
select xxx
from my_entity
where id = :id
[and name=:name]
```

By analyzing the variables in brackets, it implicitly adds a condition: output the SQL fragment only if name is non-empty.

In NopOrm, macro tags can implement similar local syntactic transformations:

```xml
<sql>
  <source>
    select o from MyEntity o
    where 1=1
     <sql:filter> and o.classId = :myVar</sql:filter>
  </source>
</sql>
```

`<sql:filter>` is a macro tag executed at compile time. It transforms the source structure and is equivalent to hand-written code:

```xml
<c:if test="${!_.isEmpty(myVar)}">
   and o.classId = ${myVar}
</c:if>
```

See tag implementations:

[sql.xlib](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-persistence/nop-orm/src/main/resources/_vfs/nop/orm/xlib/sql.xlib)

This is conceptually equivalent to Lisp macros—particularly that, like Lisp, any part of the code (any AST node) can be replaced by a macro node. It uses XML as the representation, more human-friendly than Lisp’s mathematical-symbol style.

C# LINQ (Language Integrated Query) achieves its syntax by capturing expression ASTs at compile time and then executing transformations in application code—essentially compile-time macro transformation. In XLang, besides macro tags in XPL templates, macro functions in XScript can transform between SQL and object syntax. For example:

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

XScript template expressions automatically recognize macro functions and execute them at compile time. We can define a linq macro that parses the template string into an SQL AST at compile time, then transforms it into normal JavaScript AST. Thus, we embed an SQL-like DSL into XScript’s object-oriented syntax (similar to TypeScript), achieving LINQ-like functionality in a simpler way and closer to raw SQL form.

> The above is conceptual. Currently, the Nop platform only provides macro functions such as xpath/jpath/xpl, not a built-in linq macro.

### 9.4 SQL Output Mode in the Template Language

Compared to ordinary languages, template languages are biased to make Output a first-class concept of side effects. If no special syntax is used, it implies output; other logic must be explicitly isolated with expressions/tags. As a generic template language, XPL enhances the Output concept and adds multi-mode output.

XPL supports multiple output modes:

- text: plain text output without extra escaping
- xml: XML output with automatic escaping
- node: structured AST output with source location retained
- sql: SQL-object output that prevents SQL injection

The sql mode adds special handling for SQL:

1. If an object is output, replace it with ?, and collect the object into the parameter list. For example, `id = ${id}` generates id=? with parameters stored in a List.

2. If a collection is output, expand into multiple parameters. For example, `id in (${ids})` becomes id in (?,?,?).

If you need raw text output concatenated to SQL, wrap with raw:

```
from MyEntity_${raw(postfix)} o
```

Additionally, NopOrm models parameterized SQL simply as:

```
SQL = Text + Params
```

You can build via sql = SQL.begin().sql("o.id = ? ", name).end(). XPL’s sql output mode automatically recognizes SQL objects and handles text and parameters separately.

### 9.5 Automatic Validation

Managing SQL templates in external files has a downside: you cannot rely on the type system for validation and must rely on runtime tests to catch syntax errors. If the data model changes, impacted SQL statements may not be detected immediately.

There are simple solutions. Since SQL statements are managed as structured models, our options become rich. NopOrm includes a Contract-Based Programming-like mechanism: each EQL model supports validate-input configuration. We can prepare test data there; when sql-lib loads, the ORM engine automatically runs validate-input to get test data, uses it to generate EQL, and then passes it to the EQL parser to analyze validity. This achieves a quasi-static analysis checking consistency between ORM models and EQL.

### 9.6 Debugging Support

Unlike MyBatis’s built-in minimalist template language, NopOrm uses XPL to generate SQL. Therefore, we can naturally use the XLang debugger. The Nop platform offers an IDEA plugin with DSL hints and breakpoints. It reads the sql-lib.xdef meta-model definition, validates sql-lib syntax per the meta-model, offers syntax hints, supports breakpoints in source sections, and step-by-step debugging.

All DSLs in the Nop platform are built on Reversible Computation and use a unified meta-model language, XDefinition, to describe themselves, so we don’t need separate IDE plugins and debuggers for each DSL. To add IDE support for a custom sql-lib model, the only requirement is adding x:schema="/nop/schema/orm/sql-lib.xdef" on the model’s root node to introduce the xdef meta-model.

XLang also includes debugging features to diagnose issues during meta-programming:

1. AST nodes generated under outputMode=node automatically preserve source line numbers, so compiler errors map directly to source positions.

2. Add xpl:dump on XPL nodes to print the dynamically compiled AST.

3. Any expression can append the extension function `$`, which prints the expression’s text, line number, and result, and returns the result. For example:

```
x = a.f().$(prefix) corresponds to
x = DebugHelper.v(location,prefix, "a.f()",a.f())
```

## 10. GraphQL over ORM

Abstractly, front-back interactions boil down to: request business method M on backend business object O, pass argument X, return Y. In URL form, it looks like:

```
view?bizObj=MyObj&bizAction=myMethod&arg=X
```

Specifically, bizObj maps to a backend Controller object, and bizAction to its business method. view represents the result to the caller, and its data originates from the business method. For ordinary AJAX, the returned JSON format is uniquely determined by the method, so a fixed JSON is fine. For generic RESTful services, view selection is more flexible, e.g., use Http contentType to decide JSON vs. XML. If view is uniquely determined by the requested business object and method, the Web request is push-mode; if the client can choose the view, it’s pull-mode. Based on this, we can view GraphQL as a Composable Pull-mode Web Request.

GraphQL’s most notable difference from REST/RPC is that its request is like:

```
selection?bizObj=MyObj&bizField=myField&arg=X
```

GraphQL is pull-mode, specifying the returned result data. But the specification is not a complete new creation; it is selection and partial reorganization (renaming) atop existing structures. Because selection info is highly structured, it can be parsed ahead of time and used as a blueprint for executing business methods. Also, being highly structured, multiple business object requests can be composed in order.

In some sense, the logical structure of web frameworks is unique. To achieve effective logical separation, we must distinguish backend business objects; for flexible organization, we must specify the view. The implication is a URL form of `view?bizObj=MyObj&bizAction=myAction&arg=X`.

> Many years ago, I wrote an article analyzing WebMVC design principles: [The Past and Present of WebMVC](http://www.blogjava.net/canonical/archive/2008/02/18/180551.html). Its analysis still holds today.

Based on this understanding, GraphQL and ORM can be combined very simply. In the Nop platform, GraphQL services can map directly to underlying ORM entities via deterministic rules, yielding a runnable GraphQL service with no coding. On this automatic mapping, we progressively add business rules: permissions, workflows, structural adjustments, etc. Specifically, each DB table is a candidate business object, and code generators automatically produce:

```
/entity/_MyObj.java
       /MyObj.java
/model/_MyObj.xmeta
      /MyObj.xmeta
      /MyObj.xbiz
/biz/MyObjBizModel.java
```

- MyObj.java is generated from the ORM model; we can add internal auxiliary properties and methods in the entity class.

- MyObj.xmeta is the externally visible business entity structure; the system generates the GraphQL object schema from it.

- MyObjBizModel.java defines custom GraphQL resolver functions and data loaders.

- MyObj.xbiz involves more complex business aspects and will not be elaborated here.

GraphQL and ORM provide information structures at different layers. GraphQL is an external view, while ORM is for internal application use, so they inevitably won’t share the exact same schema. Yet in typical business applications, they are obviously similar and have much in common. Reversible Computation provides a standardized solution for handling similar-but-not-identical information structures.

Given this, both `_MyObj.java` and `_MyObj.xmeta` are generated directly from the ORM model, and their info is kept in sync. MyObj.java extends `_MyObj.java` so we can add internal-only properties/methods. MyObj.xmeta customizes `_MyObj.xmeta` via x:extends Delta merge, supporting add/modify/remove of properties/methods, and allowing renaming and auth rules. For example:

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

In this example, propA is removed and cannot be accessed via GraphQL. The internalProp is renamed to propB for GraphQL; propB has auth roles=admin, allowing only administrators to access it. The dict in schema limits values to my.dict.yaml. As introduced in section 5.2, NopOrm’s dictionary translation mechanism: during meta-programming, the engine detects dict and auto-generates a propB\_text field, returning internationalized text from the dictionary. 

For top-level GraphQL objects, the Nop platform automatically generates:

```graphql
extend type Query{
    MyObj__get(id:String): MyObj
    MyObj__findPage(query:String): PageBean_MyObj
    ...
}
```

Beyond default operations like get/findPage, we can define extended properties and methods in MyObjBizModel.

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
    public MyEntity getEntity(@ReflectionName("id") String id, IEvalScope scope,
                              IServiceContext context, FieldSelectionBean selection)     {
       ...
    }

    @BizQuery
    @BizObjName("MyEntity")
    public PageBean<MyEntity> findPage(@ReflectionName("query") QueryBean query) {
        ...
    }
}

@BizModel("MyChild")
public class MyChildBizModel {

    /**
     * Batch load attributes
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

In BizModel, @BizQuery and @BizMutation are used to define GraphQL Query and Mutation operations, respectively. The naming format for GraphQL operations is `{bizObj}__{bizAction}`. At the same time, we can use @BizLoader to add GraphQL fetcher definitions, use @ContextSource to inject the parent object instance in GraphQL, and use @ReflectionName to mark arguments; automatic type conversion is performed during parameter mapping.

> If get/findPage and similar functions are also defined in BizModel, they will override the default implementations such as MyObj\_\_get.

Within the BizModel design space, only the concepts of business objects, business methods, and business parameters exist. It is completely decoupled from GraphQL, so we can easily provide REST service bindings or bindings to other RPC interface standards for BizModel. In our concrete implementation, we even provide a batch-file binding: a background batch job runs periodically, parses the batch file to obtain request objects, then invokes the BizModel to execute business logic, and writes the returned objects as results to the output files. The key design point here is batch-processing optimization: for a batch job that processes 100 records per batch, the entire batch should be processed completely and then update the database once, rather than updating the database immediately after each business request. With the ORM engine’s session mechanism, this batch-processing optimization comes entirely for free.

## Conclusion

Reversible Computation theory is neither a clever design pattern nor a set of best-practice summaries. It is rooted in the physical laws that truly exist in our world and derives, from first principles through rigorous logical reasoning, an innovative technological idea for constructing large-scale software structures.

Guided by Reversible Computation theory, NopOrm’s technical approach exhibits completeness and consistency in its underlying logical structure, enabling it to tackle a series of thorny technical problems in a straightforward manner. (Often, the intrinsic complexity of a system is not very high, but structural barriers and conceptual conflicts arising from the configuration of multiple components lead to a great deal of accidental complexity.)

NopOrm is not an ORM engine dedicated solely to low-code; it supports a smooth transition from LowCode to ProCode. It supports code generation during development as well as dynamic field addition at runtime, and provides a complete solution for user-defined storage.

It is very lightweight: while covering the main functionalities of Hibernate + MyBatis + SpringData JDBC + GraphQL, the amount of hand-written effective code is under 20,000 lines (with a large amount of code being automatically generated, because the Nop platform is striving to use low-code methods to develop all of its own components). It is suitable not only for small monolithic projects, but also for distributed, high-performance, high-complexity large-scale business systems. It additionally offers certain syntax support for BI systems and supports compilation to native applications via GraalVM.

NopOrm adheres to the principles of Reversible Computation and can customize and enhance the underlying model through Delta customization and metaprogramming. Users can continuously accumulate reusable domain models in their business domains, and even develop their own proprietary DSLs.

More importantly, NopOrm is open-source! (It is currently in the code cleanup stage and will be released together with Nop Platform 2.0.)

Lastly, to those who managed to read this far: it truly wasn’t easy—kudos to your studious spirit!

The low-code platform NopPlatform, designed based on Reversible Computation theory, is now open-source:

- gitee: [canonical-entropy/nop-entropy](https://gitee.com/canonical-entropy/nop-entropy)
- github: [entropy-cloud/nop-entropy](https://github.com/entropy-cloud/nop-entropy)
- Development sample: [docs/tutorial/tutorial.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/tutorial/tutorial.md)
- [Principles of Reversible Computation and Nop Platform Introduction & Q&A\_哔哩哔哩\_bilibili](https://www.bilibili.com/video/BV1u84y1w7kX/)
<!-- SOURCE_MD5:ff30bf5600bfa7f0f45ab17f3ea0baf5-->
