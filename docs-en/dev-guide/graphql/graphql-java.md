# Object-Oriented GraphQL

The Nop platform is built on a data model and automatically generates entity definitions, SQL table definitions, GraphQL types, and front-end pages. For example, if the `Department` table is missing, the platform will generate a GraphQL type `Department`. It will also generate corresponding properties for primary and foreign key associations, such as `parent` and `children`. If a `connection` tag is added, it will generate pagination properties for associated objects, such as users in a specific department, using a mechanism similar to `[Relay Cursor Connection](https://relay.dev/graphql/connections.htm)`.

If business objects are missing, they will automatically inherit from `CrudBizModel`, generating corresponding GraphQL entry operations.

> For details about connections, see [connection.md](connection.md).

```graphql
extend type Query {
  Department_get(id: String!): Department
  Department_batchGet(ids: [String!]): [Department]
  Department_findPage(query: QueryBeanInput): PageBean_Department
  ...
}
extend type Mutation {
  Department_save(data: DepartmentInput): Department
  Department_delete(id: String!): Boolean
  ...
}
```

The Nop platform includes an automated backend management software production line. Its input is user requirements (expressed in Excel format), and its output is a runnable application system, implemented through a systematic incremental code generation approach. In this process, the GraphQL Schema is generated as an intermediate product based on Meta metadata and BizModel business models. We do not manually write GraphQL type definitions, and during business code writing, no knowledge of GraphQL is required. No need to implement specific GraphQL DataFetcher or DataLoader interfaces. Detailed technical details are explained in the "Incremental Pipeline" section. Additionally, refer to the following article:

[Data-Driven Incremental Code Generator](https://zhuanlan.zhihu.com/p/540022264)

## GraphQL Object Definitions

The NopGraphQL engine initializes by leveraging the dynamic scanning ability of the IoC container to discover all beans marked with the `@BizModel` annotation. These are then categorized and merged based on BizObjName configuration, such as:

```java
@BizModel("NopAuthUser")
public class NopAuthUserBizModel extends CrudBizModel<NopAuthUser> {
  @BizMutation
  public void changeSelfPassword(@Name("oldPassword") String oldPassword,
                                 @Name("newPassword") String newPassword) {
        ...
  }
}


@BizModel("NopAuthUser")
public class NopAuthUserBizModelEx {
  @BizMutation
  public void otherOperation() {
         ...
  }

  @BizMutation
  @Priority(NORMAL_PRIORITY - 100)
  public void changeSelfPassword
  @Name("oldPassword")
  String oldPassword,
  @Name("newPassword")
  String newPassword)

  {
        ...
  }
}
```

NopAuthUserBizModel and NopAuthUserBizModelEx both have the same BizObjectName "NopAuthUser". Their methods are combined to generate operations for the NopAuthUser business object. If function names conflict, priority is determined by `@Priority` annotations. If priorities are the same and function names match, an exception will be thrown.
NopGraphQL Engine constructs BizObject while checking the extended xbiz models. We can extend BizObject by adding methods to the NopAuthUser.xbiz model file. This model file can be updated online, and changes will take effect immediately without requiring reinitialization of GraphQL type definitions. The methods defined in the xbiz file have the highest priority and will automatically override the business methods defined in JavaBean.

If we consider BizModel with the same object name as a slice of the object, then NopGraphQL Engine is equivalent to dynamically collecting these object slices during system initialization, similar to how Docker images are layered. These layers are then combined to form a complete object definition. At runtime, the top-level xbiz slice can be dynamically modified and override the functionality of lower slices.

> The concept of BizModel slices is somewhat analogous to the Entity Component System (ECS) pattern used in game development, except that it accumulates dynamic behavior rather than local state.

The Scatter capability complements Gather: we often need abstract global rules that automatically push some common knowledge into different business objects. NopGraphQL primarily implements information distribution through AOP mechanisms and metaprogramming:

1. Common mechanisms can be used as AOP interceptors for qualifying business methods  
2. xbiz files can leverage the x:gen-extends metaprogramming mechanism in XLang to dynamically define method definitions, or use external CodeGenerators to generate code.

## CRUD Model

In general business development, CRUD (Create/Read/Update/Delete) operations are often the most similar parts across different business objects, making it worthwhile to abstract them. NopGraphQL uses the Template Method design pattern to provide a generic CRUD implementation: CrudBizModel. Specific usage involves inheriting from CrudBizModel and implementing custom logic through methods like defaultPrepareSave and afterEntityChange. See the code examples below:

[CrudBizModel.java](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-biz/src/main/java/io/nop/biz/crud/CrudBizModel.java)

[ObjMetaBasedValidator.java](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-biz/src/main/java/io/nop/biz/crud/ObjMetaBasedValidator.java)

[NopAuthUserBizModel.java](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/entity/NopAuthUserBizModel.java)

## 3.1 Metadata-Driven

CrudBizModel uses a metadata-driven implementation that reads from xmeta configuration files. It includes built-in support for data validation, auto-initialization, cascading deletion, logical deletion, and data access control, meaning custom logic is typically unnecessary beyond adjusting xmeta and xbiz configurations.

1. Data Validation: Similar to GraphQL's output selection, NopGraphQL can selectively validate and transform input fields, demonstrating **input-output duality**.
   
   ```javascript
   validatedData = new ObjMetaBasedValidator(bizObjManager, bizObjName, objMeta, context, checkWriteAuth)
                       .validateForSave(input, inputSelection);
   ```

2. Auto-Initialization: Field values can be automatically initialized based on meta-configured autoExpr expressions during updates or modifications. These expressions are generated based on the data model's domain configuration.

3. Data Transformation: Input property values are adapted and transformed according to meta-configured transformIn expressions. These expressions are also generated based on the data model's domain configuration.

4. Cascading Deletion: Sub-table records marked with cascade-delete will be deleted along with the main table record, executing the corresponding BizObject's delete logic for the sub-table.

5. ...
## 3.2 Complex Queries

`CrudBizModel` provides three standard interfaces for complex queries:

```javascript
PageBean<OrmEntity> findPage(@Name("query") QueryBean query, FieldSelectionBean selection);
List<OrmEntity> findList(@Name("query") QueryBean query);
OrmEntity findFirst(@Name("query") QueryBean query);
```

1. **findPage** will return paged query results based on the provided conditions. The pagination logic can be implemented using `cursor+next` page or the traditional `offset+limit` approach. The `selection` parameter corresponds to the collection of fields passed in from the frontend during a call.
   - If the total number of pages is not required, `findPage` will skip the total count query.
   - If no items are requested, it will adjust the actual pagination query accordingly.

2. **findList** returns a list of records based on the query conditions. If no page size is specified, it will use the configuration from `meta` to determine the maximum number of records per page (e.g., `maxPageSize`).

3. **findFirst** returns the first record that satisfies the conditions.

The `QueryBean` class is analogous to Hibernate's Criteria object and supports complex query conditions with nested `and/or` operations, as well as sorting conditions. A `QueryBean` instance is constructed from the frontend and undergoes the following processing before execution by the DAO:

1. **Validation of Queryable Fields**: Ensures that only fields marked as `queryable="true"` are included in the query. By default, queries are restricted to equality operations unless specified otherwise. For example:
   ```xml
   <prop name="userName" allowFilterOp="eq,contains" queryable="true"
             xui:defaultFilterOp="contains"/>
   ```
   This configuration allows username fields to be queried using equality or containment operators.

2. **Data Permission Filtering**: Automatically applies filters based on the user's permissions, such as filtering for records where the management unit is the same as the current unit.

3. **Sorting by Primary Key Fields**: To avoid issues related to database concurrency, all pagination queries should include sorting conditions. This ensures consistent ordering of results.

`QueryBean` leverages the capabilities of the underlying `NopOrm` engine to support object associations, such as:
```xml
<eq name="manager.dept.type" value="1"/>
```
This query automatically resolves the association using the `manager_id` link to the corresponding department table.

If the underlying ORM does not support association queries, you can implement a custom `QueryTransformer` interface to modify `QueryBean` instances. For example:
```sql
o.manager_id in (select user.id from User user, Dept dept
       where user.dept_id = dept.id and dept.type = 1)
```

In the AMIS framework, we have established conventions for constructing complex queries from form inputs. The format for filter parameters is:
```
字段名格式为: filter_{propName}__{filterOp}
```
For example:
- `filter_userName__contains` applies a containment filter to the `userName` field.
- For equality filters (`eq`), you can omit the `filterOp` part, as in `filter_userId`.

Note: If the filter value is empty, it will be ignored. To explicitly query for null values, use `__null`, and for empty strings, use `__empty`.

When calling methods like `findPage`, `findList`, or `findFirst` via URLs in AMIS, you can use the following format:
```javascript
{
   url: "@query:NopAuthUser__findPage?filter_userStatus=1"
}
```
### Constructing Complex Query Conditions

When directly calling the backend's GraphQL service or REST service, you can construct a `QueryBean` object.

```http
POST /r/NopAuthUser__findPage

{
  "query": {
    "filter": {
      "$type": "eq",
      "name" : "userStatus",
      "value": 1
    }
  }
}
```

The `filter` corresponds to a `TreeBean` type object in the backend, which is a generic Tree structure and can be automatically converted into XML format. The specific conversion rules are defined by the standard conversion mechanism of the Nop platform:

1. The `$type` attribute corresponds to the tag name
2. The `$body` attribute corresponds to child nodes and node content
3. Other attributes without a `$` prefix correspond to XML node attributes
4. Attributes prefixed with `@:` are parsed in JSON format

```xml
<and>
  <eq name="status" value="@:1"/>
  <gt name="amount" value="@:3"/>
</and>
```

This corresponds to:

```json
{
  "$type": "and",
  "$body": [
    {
      "$type": "eq",
      "name": "status",
      "value": 1
    },
    {
      "$type": "gt",
      "name": "amount",
      "value": 3
    }
  ]
}
```

The supported operators such as `eq`, `gt` in the filter are defined in `[FilterOp.java](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-core/src/main/java/io/nop/core/model/query/FilterOp.java)`.

Reusable operators:

| Operator | Description |
|--------|-------------|
| eq     | Equals       |
| gt     | Greater than  |
| ge     | Greater than or equal to |
| lt     | Less than    |
| xe     | Less than or equal to |
| in     | In the collection |
| between | Between min and max |
| betweenDate | Date between min and max |
| alwaysTrue | Always true |
| alwaysFalse | Always false |
| isEmpty | The value corresponding to `name` is empty |
| startsWith | The string starts with the specified value |
| endsWith | The string ends with the specified value |

### BizArgsNormalizer Parameter Specification Conversion
The functions like `findPage/findList` in `CrudBizModel` accept query parameters in `QueryBean` format, but constructing the `QueryBean` structure is complex in the frontend. Therefore, it also supports directly transmitting filter conditions in the format `filter_{propName}`, such as `filter_status=1`.

The backend implementation uses the `@BizArgsNormalizer` annotation to introduce the `IGraphQLArgsNormalizer` interface object for normalizing the parameters passed from the frontend. This converts the frontend-transmitted `filter_xx` conditions into a `QueryBean` object. This is a generic mechanism used not only for converting `QueryBean` structures but also in other contexts.

```javascript
    @BizQuery
    @BizArgsNormalizer(BizConstants.BEAN_nopQueryBeanArgsNormalizer)
    @GraphQLReturn(bizObjName = BIZ_OBJ_NAME_THIS_OBJ)
    public PageBean<T> deleted_findPage(@Optional @Name("query") @Description("@i18n:biz.query|查询条件") QueryBean query,
                                        FieldSelectionBean selection, IServiceContext context) {
      ...
   }
```markdown

## 3.3 The 'This' Pointer: Relationalizing Knowledge

In GraphQL, operation names are global and unique, such as `query{ getUser(id:3){ id, userName}}`.  
The `getUser` method used in queries must be unique across the entire model, which is detrimental to reusable code.

In NopGraphQL, when implementing CRUD operations, you only need to inherit from the `CrudBizModel` base class. The GraphQL operation names exposed to the frontend are formed by concatenating the object name and method name.

```java
class CrudBizModel<T> {
  @BizQuery
  @GraphQLReturn(bizObjName = "THIS_OBJ")
  public T get(@Name("id") String id) {
       ....
  }
}

@BizModel("NopAuthUser")
class NopAuthUserBizModel extends CrudBizModel<NopAuthUser> {

}
```

In the above example, the NopGraphQL engine will automatically generate a query operation named `NopAuthUser_get`, and its return type will be `THIS_OBJ`. This means it will be replaced with the corresponding BizObjName of the current object, which is `NopAuthUser`.

Note that by using this implementation approach, we can provide different GraphQL types for the same implementation class. For example:

```java
@BizModel("NopAuthUser_admin")
public NopAuthUserAdminBizModel extends CrudBizModel<NopAuthUser> {

}
```

The same class inherits from `CrudBizModel<NopAuthUser>`, but because the `BizModel` annotation provides a BizObjName of `NopAuthUser_admin`, the returned field collection can differ from that of a regular `NopAuthUser`, and the backend's permission requirements for calls may also differ.

In other words, the method name is a local identifier defined relative to the `this` pointer. Without complete knowledge, we can create complex logic based on relative knowledge and inject different `this` pointers to change the entire set of calls' specific meanings. This is essentially the basic design principle of object-oriented programming.

> Object-oriented technology created a special name—the 'this' pointer, which is a conventionally fixed local identifier. Using 'this' allows us to distinguish between domain (domain) and non-domain. In the domain, we directly refer to the current object via 'this'.
>
> Code itself is just a formal expression; its specific meaning requires an interpretation process to determine. The call form based on object pointers leads to multiple interpretations: by injecting different 'this' pointers, you can provide different interpretations.

In the frontend implementation, we used a similar strategy: the frontend script automatically determines the method signature based on the method name's suffix, such as all methods ending with `_findPage` have a default signature of:

```java
XXX_findPage(query: QueryBeanInput): PageBean_XXX
```


### Return Types

Service methods on BizModel do not need to wrap return values in ApiResponse; the framework itself handles the wrapping. If the return type is String, it will be returned as String without automatic JSON parsing. If it's a Map or other bean objects, attributes will be loaded via DataLoader and returned. If it's a CompletionStage, it indicates asynchronous execution.

```java
@BizQuery
public Map<String, Object> myMethod() {
   ...
}

@BizQuery
public CompletionStage<Map<String, Object>> myMethod2Async() {
   return ...;
}

@BizQuery
public MyResultBean myMethod3() {
   return ...;
}
```

## 4. Framework-Agnostic Design

When writing business code using traditional Web frameworks, it's inevitable that you'll use framework-specific environment objects like `HttpServletRequest` or `SpringMVC`'s `ModelAndView`. These objects are tightly coupled with the framework's runtime environment, binding your code to a specific runtime context and making it difficult to apply across various scenarios. The most obvious example is a service function designed for online API calls that cannot be directly used as a message queue consumer. We must abstract an additional layer: the Service Layer, and package it separately into Controllers and Message Consumers to handle Web requests and message queue consumption.

NopGraphQL employs a framework-agnostic, non-invasive design in implementing business methods, expanding the use cases for service methods and simplifying service layer implementation. Specifically, NopGraphQL introduces minimal annotations and uses POJO objects as input and output objects, automatically translating business methods into GraphQL-compatible DataFetcher and DataLoader constructs. For example:

```java
@BizModel("MyEntity")
class MyBizModel {
  @BizQuery
  public MyEntity get(@Name("id") String id) {
    return ...;
  }

  @BizLoader
  public String extProp(@ContextSource MyEntity entity) {
        ...
  }

  @BizLoader(forType = OtherEntity.class)
  public String otherProp(@ContextSource OtherEntity entity) {
       ...
  }

  @BizLoader("someProp")
  public CompletionStage<List<SomeObject>> batchLoadSomePropAsync(
    @ContextSource List<MyEntity> entities) {
       ...
  }
}
```

1. `@BizQuery` indicates that this method will be mapped to a GraphQL query.
2. `@BizMutation` indicates that this method will be mapped to a GraphQL mutation.

3. If the return type is a `CompletionStage`, it signifies asynchronous execution.

4. If a method annotated with `@BizLoader` has a `ContextSource` parameter of List type, it indicates a DataLoader implementation for GraphQL, supporting batch loading.

![BizModel Diagram](../../arch/BizModel.svg)

Service methods written with the NopGraphQL engine can be seen as having the following function signature:

```java
ApiResponse<Object> service(ApiRequest<Map> request);

class ApiRequest<T> {
    Map<String, Object> headers;
    FieldSelectionBean selection;
    T data;
}
```

Service methods receive a POJO request object and return another POJO response object. Because both input and output are simple objects, no coding is required—just simple configuration to achieve:

1. Publish GraphQL service methods as message queue consumers: they receive a request object from one topic and send the response to another topic. If the header indicates "one-way," the response message is ignored.

2. Publish GraphQL service methods as RPC functions.

3. Read requests from batch files, invoke service methods sequentially, batch submit, retry on failure, and write returned responses to output files.

## REST Over GraphQL
GraphQL Engine can be run on top of REST services, providing the so-called Federation functionality to combine multiple REST services into a single unified GraphQL endpoint. Conversely, can we also decompose the underlying GraphQL service methods and expose them as individual REST resources?

NopGraphQL leverages the lazy field concept by defining sets of eager loading attributes in GraphQL type definitions and converting GraphQL model methods into REST services through normalized means. The specific REST URL format is as follows:

```java
/r/{operationName}?@selection=a,b,c {
  d, e
}
```

1. Parameters are transmitted via the request body

2. `/r/{operationName}` refers to the service link, and the optional `@selection` parameter specifies the fields to be selected from the returned results. If not specified, the backend will automatically return all attributes that are not marked as lazy. During code generation, associated table data missing will be marked as lazy, so they won't be included in the REST call's response under normal circumstances.

3. A GET request can only invoke GraphQL query operations, while a POST request can invoke either query or mutation operations.

Parameters can be transmitted via URL parameters, for example:

```java
GET /r/NopAuthUser_get?id=3
```

This is equivalent to executing `NopAuthUser_get(id:3)`.

In POST requests, the parameter can be sent via the HTTP body in JSON format:

Nop platform's frontend framework is built on the Baidu AMIS framework and further simplifies GraphQL calls. On the front end, we can use the following URL format to initiate GraphQL calls:

```js
api: {
    url: '@query:NopAuthUser__get/id,userName?id=$id'
}
```

The above URL uses a prefix syntax. The `ajaxFetch` function at the bottom layer will recognize the `@query:` prefix and convert it into a GraphQL request:

```graphql
query($id:String) {
  NopAuthUser_get(id:$id) {
    id, userName
  }
}
```

The `ajaxFetch` function recognizes the GraphQL URL format as:

```java
(@query|@mutation):{operationName}/{selection}?参数名=参数值
```

When writing loading functions for forms or tables, if there are many fields, manually writing GraphQL requests can easily result in missing fields. Since Nop platform's frontend code is also auto-generated, we can leverage compile-time variables like `formSelection`, `pageSelection`, etc., to auto-generate GraphQL requests so that exactly the data needed for the form or table is selected. For example:

```java
@query:NopAuthUser_get/{@formSelection}?id=$id
```

`{@formSelection}` indicates all fields used in the current form.

## GraphQL Extensions

### Map Type

GraphQL is a strongly typed framework that requires all data to have explicit type definitions, which can be inconvenient in some dynamic scenarios. For example, sometimes we need to return extended collections to the frontend.

NopGraphQL introduces a special Scalar type: Map, allowing us to describe those dynamic data structures. For instance:

```graphql
type QueryBean {
    filter: Map
    orderBy: [OrderFieldBean]
}
```

### Tree Structure

For retrieving tree structures like unit trees or menu trees, NopGraphQL provides an extension syntax using the Directive mechanism to directly express recursive data fetching, such as:

```graphql
query {
    NopAuthDept_findList {
        value: id
        label: displayName
        children @TreeChildren(max:5)
    }
}
```

`@TreeChildren(max:5)` indicates that up to 5 levels of nesting will be performed at this level.

## File Upload and Download

See [upload.md](upload.md)

## REST Links

NopGraphQL engine supports calling backend service functions via REST links. In a sense, it unifies GraphQL and REST, allowing them to implement the same functionality while differing only in request and response formats.

See [rest.md](rest.md)

## Combining GraphQL Results

GraphQL provides result combination capabilities that REST does not have, significantly enhancing system's composability and runtime performance. Below is a brief explanation of its implementation in DevDocBizModel.
Frontend, we can query the global object definitions from the backend. Each global object has a methods property that returns the methods defined on that global object.

```graphql
query {
  DevDoc__globalVars {
    name
    methods
  }
}
```

In the implementation of DevDocBizModel, the globalVars method in the GlobalVarDefinition class only initializes simple properties like name and does not load complex methods.
After returning `List<GlobalVarDefinition>`, the GraphQL engine processes it further. When it detects that the methods property needs to be returned, it calls the corresponding DataLoader for methods,
which actually constructs a list of `FunctionDefBean` to return.

**If the client does not request the methods attribute, the backend can avoid executing method loading functions, thereby improving performance**

```java

@BizModel("DevDoc")
public class DevDocBizModel {
  @BizQuery
  @Description("Global Variables")
  public List<GlobalVariableDefBean> globalVars() {
    return ...
  }

  @BizLoader(forType = GlobalVariableDefBean.class)
  public List<FunctionDefBean> methods(@ContextSource GlobalVariableDefBean varDef) {
    return ...
  }
}
```

For the complete implementation, refer to [DevDocBizModel.java](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-biz/src/main/java/io/nop/biz/dev/DevDocBizModel.java)

## Defining Loader in XBiz Model

In XBiz, defining queries, mutations, and loaders is equivalent to doing so in Java. For example:

```java
@BizModel("NopAuthRole")
public class NopAuthRoleBizModel extends CrudBizModel<NopAuthRole> {
  @BizLoader
  @GraphQLReturn(bizObjName = "NopAuthUser")
  public List<NopAuthUser> roleUsers(@ContextSource NopAuthRole role) {
    return role.getUserMappings().stream()
      .map(NopAuthUserRole::getUser)
      .sorted(comparing(NopAuthUser::getUserName))
      .collect(Collectors.toList());
  }
}
```

In XBiz files, this corresponds to:

```xml
<loaders>
  <loader name="roleUsers">
    <arg name="role" kind="ContextSource" type="io.nop.auth.dao.entity.NopAuthRole"/>
    <return type="List&lt;io.nop.auth.dao.entity.NopAuthUser>"/>

    <source>
      <c:script>
        const users = role.userMappings.map(m => m.user);
        return _.sortBy(users, "userName");
      </c:script>
    </source>
  </loader>
</loaders>
```

Note: All GraphQL attributes that can be used must be configured in meta. Defining a loader alone will not automatically create attribute definitions, which is mainly to ensure the completeness of the meta model's semantic meaning.

## Implementing Custom Attributes via Meta's Getter

Some simple attribute adaptation issues might seem cumbersome if using the XBiz loader mechanism. Instead, you can directly configure attributes through getters in meta.
```xml
<prop name="nameEx">
  <getter>
    <c:script>
      // Here entity represents the current entity
      return entity.name + 'M'
    </c:script>
  </getter>
</prop>
```

## Data Types

GraphQL has a limited set of scalar types by default, and NopGraphQL defines additional scalar types for finer granularity.

```java
public enum GraphQLScalarType {
  ID(StdDataType.STRING), //
  Boolean(StdDataType.BOOLEAN), //
  Int(StdDataType.INT), //
  Long(StdDataType.LONG),
  Float(StdDataType.FLOAT), //
  Double(StdDataType.DOUBLE), //
  String(StdDataType.STRING), //
  Map(StdDataType.MAP), //
  Any(StdDataType.ANY),
  Void(StdDataType.VOID),
  BigDecimal(StdDataType.DECIMAL);
}
```

For the Timestamp type, the default display format is `yyyy-MM-dd HH:mm:ss`. In XMeta configuration, you can set `graphql:datePattern` to another format. If you want the timestamp to return milliseconds, you can configure the pattern as `ms`, which is a special format name recognized and handled in DateHelper.

```xml
<prop name="createTime" graphql:datePattern="ms">
</prop>
```

## Loading Objects Using GraphQL Loader
For example, after retrieving a User object via dao, you might want to automatically get the `status_label` property based on status.

```javascript
IEntityDao<NopAuthUser> user = daoProvider.daoFor(NopAuthUser.class);
List<NopAuthUser> list = user.findAll();
IServiceContext svcCtx = null; // svcCtx is typically available in the backend template runtime context
CompletionStage<Object> future = graphQLEngine.fetchResult(list,
        "NopAuthUser", "...F_defaults,status_label,relatedRoleList", svcCtx);
output("result.json5", FutureHelper.syncGet(future));
```

## Publishing the Same Entity Model as Multiple Different GraphQL Objects

* NopGraphQL engine-recognized BizModels need to be registered in `beans.xml`.
* You can specify `bizObjName` during registration. If not specified, it will attempt to retrieve it from the Java class's `@BizObjName` annotation.

```xml
<bean id="MyUserExtBizModel" class="io.nop.graphql.demo.model.MyUserBizModel">
  <property name="bizObjName" value="MyUser_ext"/>
</bean>
```

* The format of `bizObjName` is `{BaseName}_{extName}`. If the corresponding Meta does not exist, it will automatically look for the BaseName's Meta.
* You can choose to add `MyUser_ext.xmeta` and use `x:extends="MyUser.xmeta"` to inherit from the BaseMeta.

