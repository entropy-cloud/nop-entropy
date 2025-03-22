# Functionality Comparison Between Nop Platform and APIJSON

[APIJSON](http://apijson.cn/) is a widely used zero-code interface and document ORM library developed by Tencent engineers with over 16.8K stars on GitHub. This library implements a lightweight JSON-based data exchange format, offering versatile interfaces without requiring manual coding for CRUD operations, database connections, or nested queries. The APIJSON ecosystem is highly comprehensive, supporting various backend databases, multiple client languages, auto-documentation, and automated API testing.

The Nop platform integrates a next-generation GraphQL engine, NopGraphQL, and an advanced ORM engine, NopORM, designed from scratch based on reversible computation principles. Together, these engines provide functionality similar to APIJSON but with greater flexibility and scalability. In this document, we will compare the differences in features between Nop platform and APIJSON based on APIJSON's documentation structure.

> The Nop platform is a versatile next-generation low-code platform with a broader scope than APIJSON. It adheres to a programming paradigm oriented around language-specific DSL development: first enabling users to develop their own DSL tailored to their business needs, then using that DSL to build specific applications. In contrast, APIJSON provides functionality akin to a data access DSL but offers a lower-level support framework with NopGraphQL and NopORM, along with various mature DSLs for easy combination and usage by developers.

## Example

In the Nop platform:
- REST requests are handled by the NopGraphQL engine.
- NopGraphQL supports multiple query protocols, including GraphQL, gRPC, and REST.

For REST request types:
- NopGraphQL supports two connection modes: `/r/{bizObjName}__{bizAction}` and `/p/{bizObjName}__{bizAction}`.

The `/r/` endpoint returns a `ApiResponse<T>` structure containing headers, data, statistics, code, and messages.
If the status is 0, it indicates success. In case of failure, the system returns an error code via the `code` property and an error message through the `message` field.

```java
class ApiResponse<T> {
    Map<String, Object> headers;
    T data;
    int status;
    String code;
    String message;
}
```

The `/p/` endpoint directly returns the `T` structure without wrapping it in `ApiResponse<T>`. Additionally, it sets the `Content-Type` header, enabling functionalities such as downloading binary files or returning XML data.

For example, Nop's `/p/DevDoc__beans` endpoint returns configuration data from NopIoC in XML format.

When using REST request patterns:
- HTTP GET can invoke GraphQL query methods.
- HTTP POST can invoke either GraphQL query or mutation methods.

### User Retrieval Example

APIJSON Request:

```json
{
  "User": {
    "id": 38710
  }
}
```

Nop Query:

```graphql
query {
  User__get(id: "38710") {
    id, sex, name, tag, head, data, pictureList
  }
}
```

Response:

```json
{
  "data": {
    "User__get": {
      "id": 38710,
      "sex": 0,
      "name": "TommyLemon",
      "tag": "Android&Java",
      "head": "http://static.oschina.net/uploads/user/1218/2437072_100.jpg?t=1461076033000",
      "date": 1485948110000,
      "pictureList": [
        "http://static.oschina.net/uploads/user/1218/2437072_100.jpg?t=1461076033000",
        "http://common.cnblogs.com/images/icon_weibo_24.png"
      ]
    }
  }
}
```

Alternatively, using the `/r/` request pattern:

```graphql
/r/User__get?id=38710
```
 
 ### Getting User List
 
 APIJSON request:

 ```json
{
  "data":[
    {
      "id":38710,
      "sex":0,
      "name":"TommyLemon",
      "tag":"Android&Java",
      "head":"http://static.oschina.net/uploads/user/1218/2437072_100.jpg?t=1461076033000",
      "date":1485948110000,
      "pictureList":[
        "http://static.oschina.net/uploads/user/1218/2437072_100.jpg?t=1461076033000",
        "http://common.cnblogs.com/images/icon_weibo_24.png"
      ]
    },
    {
      "id":70793,
      "name":"Strong"
    },
    {
      "id":82001,
      "name":"Android"
    }
  ],
  "status":0
}
```

 Nop request:

 ```bash
/r/User__findList?@selection=id,name,status:userStatus,roles:rolesList(limit:5){roleId, roleName}
```

 Returns:

 ```json
{
  "data":[
    {
      "id":38710,
      "sex":0,
      "name":"TommyLemon",
      "tag":"Android&Java",
      "head":"http://static.oschina.net/uploads/user/1218/2437072_100.jpg?t=1461076033000",
      "date":1485948110000,
      "pictureList":[
        "http://static.oschina.net/uploads/user/1218/2437072_100.jpg?t=1461076033000",
        "http://common.cnblogs.com/images/icon_weibo_24.png"
      ]
    },
    {
      "id":70793,
      "name":"Strong"
    },
    {
      "id":82001,
      "name":"Android"
    }
  ],
  "status":0
}
```

 * CrudBizModel provides the `findList` method, which performs complex pagination queries and returns list data.
 * The `findPage` method can perform complex pagination queries and return a `PageBean<T>` object. PageBean includes total pages, current page, and list data.
 * NopGraphQL offers field selection capabilities that comply with GraphQL specifications, enabling nested structure selection, field renaming, and additional parameters.

 Query example:

 ```bash
/r/User__findList?@selection=id,name,status:userStatus,roles:rolesList(limit:5){roleId, roleName}
```

 This query calls the backend to fetch user data with specified filters, limiting roles list results to 5 items. Special characters in URLs such as `{` and `}` need URL encoding, converting them to `%7B` and `%7D`.

 Dynamic & Publisher Users

 APIJSON request:

 ```json
{
  "Moment":{
  },
  "User":{
    "id@":"Moment/userId" // User.id = Moment.userId
  }
}
```

 Nop request:

 ```bash
/r/Moment__findFirst?@selection=...F_defaults,user
```

 Returns:
  
  ```json
{
  "status": 0,
  "data": {
    "id":12,
    "userId":70793,
    "date":"2017-02-08 16:06:11.0",
    "content":"1111534034",
    "user"： {
      "id":70793,
      "sex":0,
      "name":"Strong",
      "tag":"djdj",
      "head":"http://static.oschina.net/uploads/user/585/1170143_50.jpg?t=1390226446000",
      "contactIdList":[
        38710,
        82002
      ],
      "date":"2017-02-01 19:21:50.0"
    }
  }
}
```

* **Due to security considerations, NopGraphQL does not support directly passing table association conditions in the frontend** because it's difficult to control data accessibility and dataset size.
* **In the backend ORM model, you can configure moment with user associations on the Moment object.** By default, associated objects are marked for lazy loading, so if the frontend doesn't explicitly fetch this property, it won't be returned.
* **In the frontend selection definition, `...F_defaults` uses GraphQL fragment syntax to reference all non-lazy fields.** Based on this, you can request the user association object.
* **The `findFirst` method will return the first record that meets the complex query conditions.** If no sorting conditions are specified, records will be sorted by primary key.
* **If there's no ORM-level association setup in XMeta metadata files, you can still define association conditions there.**

```xml
<meta>
  <props>
    <prop name="user" graphql:queryMethod="findFirst">
      <graphql:filter>
        <eq name="id" value="@prop-ref:userId" />
      </graphql:filter>
    </prop>
  </props>
</meta>
```

For more detailed information, see [Nop Introduction: How to Implement Complex Queries].

* Here, `graphql:queryMethod` indicates that when fetching the user attribute in the frontend, the `findFirst` method is used with `graphql:filter` conditions to retrieve data.
* In principle, additional query and sorting conditions can be passed. These conditions combined with `graphql:filter` define the final conditions.

### Getting Similar to WeChat Moments' Dynamic List

APIJSON Request:

```json5
{
  "[]":{                             // Request an array
    "page":0,                           // Array condition
    "count":2,
    "Moment":{                             // Request a named object
      "content$":"%a%",                   // Object condition, searching for 'a' in content
    },
    "User":{
      "id@":"/Moment/userId",             // User.id = Moment.userId; default reference path
      "@column":"id,name,head"           // Specify returned fields
    },
    "Comment[]":{                          // Request an array of Comment objects
      "count":2,
      "Comment":{
        "momentId@":"[]/Moment/id"       // Comment.momentId = Moment.id; complete reference path
      }
    }
  }
}
```

APIJSON Return Data:

```json5
{
  "status": 0,
  "data": {
    "id":12,
    "userId":70793,
    "date":"2017-02-08 16:06:11.0",
    "content":"1111534034",
    "user"： {
      "id":70793,
      "sex":0,
      "name":"Strong",
      "tag":"djdj",
      "head":"http://static.oschina.net/uploads/user/585/1170143_50.jpg?t=1390226446000",
      "contactIdList":[
        38710,
        82002
      ],
      "date":"2017-02-01 19:21:50.0"
    },
    "Comment[]":{
      "count":2,
      "Comment":{
        "momentId@":"[]/Moment/id"
      }
    }
  }
}
```

  
  ```json
{
  "[]":[
    {
      "Moment":{
        "id":15,
        "userId":70793,
        "date":1486541171000,
        "content":"APIJSON is a JSON Transmission Structure Protocol…",
        "praiseUserIdList":[
          82055,
          82002,
          82001
        ],
        "pictureList":[
          "http://static.oschina.net/uploads/user/1218/2437072_100.jpg?t=1461076033000",
          "http://common.cnblogs.com/images/icon_weibo_24.png"
        ]
      },
      "User":{
        "id":70793,
        "name":"Strong",
        "head":"http://static.oschina.net/uploads/user/585/1170143_50.jpg?t=1390226446000"
      },
      "Comment[]":[
        {
          "id":176,
          "toId":166,
          "userId":38710,
          "momentId":15,
          "date":1490444883000,
          "content":"thank you"
        },
        {
          "id":1490863469638,
          "toId":0,
          "userId":82002,
          "momentId":15,
          "date":1490863469000,
          "content":"Just do it"
        }
      ]
    },
    {
      "Moment":{
        "id":58,
        "userId":90814,
        "date":1485947671000,
        "content":"This is a Content...-435",
        "praiseUserIdList":[
          38710,
          82003,
          82005,
          93793,
          82006,
          82044,
          82001
        ],
        "pictureList":[
          "http://static.oschina.net/uploads/img/201604/22172507_aMmH.jpg"
        ]
      },
      "User":{
        "id":90814,
        "name":7,
        "head":"http://static.oschina.net/uploads/user/51/102723_50.jpg?t=1449212504000"
      },
      "Comment[]":[
        {
          "id":13,
          "toId":0,
          "userId":82005,
          "momentId":58,
          "date":1485948050000,
          "content":"This is a Content...-13"
        },
        {
          "id":77,
          "toId":13,
          "userId":93793,
          "momentId":58,
          "date":1485948050000,
          "content":"This is a Content...-77"
        }
      ]
    }
  ],
  "code":200,
  "msg":"success"
}
 
 Nop request:
 
 `/Moment__findList?offset=0&limit=2&filter_content__contains=a&@selection=...F_defaults,user%7Bid,name,head%7D,comments(limit:2)`
 
 Nop返回结果：
 
```json
{
  "status": 0,
  "data": [
    {
      "id": 15,
      "userId": 70793,
      "date": 1486541171000,
      "content": "APIJSON is a JSON Transmission Structure Protocol…",
      "praiseUserIdList": [
        82055,
        82002,
        82001
      ],
      "pictureList": [
        "http://static.oschina.net/uploads/user/1218/2437072_100.jpg?t=1461076033000",
        "http://common.cnblogs.com/images/icon_weibo_24.png"
      ],
      "user": {
        "id": 70793,
        "name": "Strong",
        "head": "http://static.oschina.net/uploads/user/585/1170143_50.jpg?t=1390226446000"
      },
      "comments": [
        {
          "id": 176,
          "toId": 166,
          "userId": 38710,
          "momentId": 15,
          "date": 1490444883000,
          "content": "thank you"
        },
        {
          "id": 1490863469638,
          "toId": 0,
          "userId": 82002,
          "momentId": 15,
          "date": 1490863469000,
          "content": "Just do it"
        }
      ]
    },
    {
      "id": 58,
      "userId": 90814,
      "date": 1485947671000,
      "content": "This is a Content...-435",
      "praiseUserIdList": [
        38710,
        82003,
        82005,
        93793,
        82006,
        82044,
        82001
      ],
      "pictureList": [
        "http://static.oschina.net/uploads/img/201604/22172507_aMmH.jpg"
      ],
      "user": {
        "id": 90814,
        "name": 7,
        "head": "http://static.oschina.net/uploads/user/51/102723_50.jpg?t=1449212504000"
      },
      "comments": [
        {
          "id": 13,
          "toId": 0,
          "userId": 82005,
          "momentId": 58,
          "date": 1485948050000,
          "content": "This is a Content...-13"
        },
        {
          "id": 77,
          "toId": 13,
          "userId": 93793,
          "momentId": 58,
          "date": 1485948050000,
          "content": "This is a Content...-77"
        }
      ]
    }
  ]
}
```
## Comparison with APIJSON

* Compared to APIJSON, NopGraphQL returns data in standard JSON object structure with natural nested property names. In contrast, APIJSON uses a flat structure and employs the special `Comment[]` format convention for properties.
* NopGraphQL supports various query types: one-to-one, one-to-many, many-to-one, and many-to-many relationships, provided that association conditions are configured in the backend's XMeta or ORM model for safety reasons.
* NopORM supports various JOIN operations: LEFT JOIN, INNER JOIN, and FULL JOIN. Additionally, it supports multiple SQL functions through Dialect, enabling database migration capabilities.

### Required Configuration for Filter Operations:
- The `filter_content__contains=a` parameter requires specifying `allowFilterOps="in,eq,contains"` in the backend's XMeta file for property `prop`.

## Comparison with Traditional RESTful Methods

### Traditional RESTful Development Process vs. NopGraphQL+NopORM

| Development Process | Traditional Method                     | APIJSON
|---------------------|--------------------------------------|---------|
| Interface Transmission | Implement interface on the backend, update documentation, and let the frontend handle request parsing and code execution. | Frontend handles everything without interfaces or documentation.<br/>No need to communicate with the backend about interfaces or documentation.
| Compatibility with Old Versions | Extend the backend with new interfaces using v2 notation, then update documentation.  | No action required for compatibility.<br/>APIJSON automatically handles backwards compatibility.

### Nop Development Process:
- APIJSON's claimed advantages are fully supported by combining NopGraphQL and NopORM engines.
- GraphQL protocol allows organizing returned data, supporting field selection capabilities while maintaining backward compatibility without versioning. This is achieved through custom xmeta and orm models without modifying existing service code.
- Nop platform's unique Delta customization capability enables providing default fields and extending relationships in different deployment environments without altering pre-packaged service code.

### Frontend Request Handling
| Frontend Request | Traditional Method                                                                                     | API JSON                                                                                                                                                                                                                                      |
|------------------|-----------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Requirements     | Frontend according to the document appends key-value pairs after the corresponding URL.                                     | Frontend appends JSON according to its own requirements after a fixed URL.                                                                                                                                                                      |
| URL              | Different requests correspond to different URLs, with generally as many URLs as there are distinct requests.                       | The same method (for CRUD operations) uses the same URL, with<br />most requests using one of seven generic endpoint URLs.                                                                                                                                                                      |
| Key-Value Pair   | key=value                                                                                 | key:value                                                                                                                                          |
| Structure        | A single table_name exists within a single URL: <br /><br />base_url/get/table_name?<br />key0=value0&key1=value1...         | Multiple table names can exist within the same URL:<br /><br />base_url/get/<br />{<br />    TableName0:{<br />        key0:value0,<br />        key1:value1,<br />        ...<br />    },<br />    TableName1:{<br />        key0:value0,<br />        key1:value1,<br />        ...<br />    }<br />...<br />}</br >}</br >...</br ></br >
### Nop Frontend Request

* **Leveraging the selection mechanism in the GraphQL protocol**, the Nop platform can achieve an effect equivalent to APIJSON while returning nested data structures that are more natural and intuitive.  
* **Built-in GraphQL tools** such as those provided by the quarkus framework's graphql-ui can be used for debugging and testing purposes.  
* The CrudBizModel provides a series of service functions for CRUD operations, enabling complex parent-child table relationships to be managed without extensive code changes.  
* Beyond basic CRUD operations, NopGraphQL supports business entities that are not tied to any database tables.  
* **Graphql query examples**:  

```graphql
query {
  Entity1__findPage {
    items: field1, field2
  }
  Entity2__get(id: "333") {
    name, status
  }
}
```

### 2.3 Backend Operations

| Operation Type          | Traditional Method               | APIJSON Equivalent       |
|-----------------------|---------------------------------|--------------------------|
| Parsing and Returning   | Querying the database with key-value pairs, then encapsulating the results in JSON format for frontend delivery | Using a parser method to process data and return it in JSON format directly. |
| Setting the JSON Structure for Return | Defined on the backend; frontend cannot modify this setting | Defined on the frontend; backend cannot alter this structure |

### Nop Platform Backend Operations

* The underlying implementation of the Nop platform's backend uses NopORM, which supports EQL (Extended Query Language), multi-tenant capabilities, extended fields, and logical deletion.  
* Compared to JPA+MyBatis, NopORM offers a more comprehensive and scalable ORM solution with built-in support for complex business requirements.  
* For detailed information on ORM engines suitable for low-code platforms, refer to:  
  [Low-Code Platforms and Their Required ORM Engines (1)](https://mp.weixin.qq.com/s/biBdNaQV98uaxdpVKndwwg)  
  [Low-Code Platforms and Their Required ORM Engines (2)](https://mp.weixin.qq.com/s/Nv9Z23rv0ijwJ34PPH-uyw)

### 2.4 Frontend Parsing

| Parsing Type | Traditional Method               | APIJSON Equivalent       |
|--------------|---------------------------------|--------------------------|
| Viewing Data   | Browsing documentation, querying the backend, or examining logs after a successful request | Simply query and receive data directly through requests; no need to manually parse logs. |
| Parsing Methods | Using JSON parsers for specific data types like JSONObject | Automatically parsing JSON responses using built-in JSONResponse tools or traditional methods |

### Nop Platform Frontend Parsing

* **Quarkus framework** provides robust debugging tools such as graphql-ui for efficient query execution and result analysis.  
* The Nop platform logs detailed information at every critical point, including input/output data, SQL statements executed, and execution times.  
* In debug mode, the `/p/DevDoc__graphql` endpoint allows access to all backend service functions and data definitions.  
* Sensitive data such as passwords and card numbers are automatically masked in logs before being printed.  
* General responses are structured in JSON format, which can be parsed using JSON tools. Binary file downloads are also supported through the `/p/` endpoint.

### 2.5 Handling Different Frontend Requests

The Nop platform allows flexibility in choosing between standard GraphQL, gRPC, or REST protocols to interact with backend services.  
Compared to APIJSON, Nop's approach is more intuitive and lightweight while still incorporating all APIJSON capabilities.  
Additionally, Nop offers unique customization capabilities through its Delta and Meta programming features.

#### 1. Retrieving Single User Data

```graphql
/r/User__get?id=38710
```

#### 2. Moment and Corresponding User

```graphql
/r/Moment__findFirst?filter_userId=38710&@selection=...F_defaults,user
```

#### 3. User List
```markdown

#### Translate Result


#### Design Specifications


### Operation Methods

1. Similar to the GraphQL specification, Nop platform's service side only uses GET and POST methods, does not use PUT, DELETE, PATCH, etc., simplifying front-end and back-end processing.
2. The GET method can only call idempotent GraphQL queries, while POST can call either GraphQL queries or mutations.
3. Mutation operations automatically start database transactions (optimizations have been implemented internally; if no actual database access occurs, no real database connection is used).
4. CrudBizModel provides methods such as findList, findPage, findFirst for list, page, and single data retrieval.
5. All findXX methods accept filter and orderBy parameters and support complex combination queries using and/or.
6. Queries can directly use properties like moment.user.dept (composite properties), which will be automatically recognized at the ORM level and translated into multi-table associations. This leverages NopORM's underlying EQL association query capabilities.
7. Global parameters such as tenantId, authToken, traceId can be transmitted via HTTP headers.
8. Each database entity has a corresponding service object, which can be called using `/r/{bizObjName}__{bizAction}`. Sub-table structures that do not need to be directly exposed can be marked as no-web in the data model, preventing separate endpoints from being generated for them.


## Function Symbols

The Nop platform uses QueryBean models to express complex query conditions. This is a general Predicate model automatically converted into SQL queries or executed as in-memory Predicate interfaces and supports conversion between XML and JSON formats. For example:

```xml
<and>
  <eq name="status" value="1" />
  <in name="type" value="@:[1,2]" />
</and>
```

The `@:` symbol is an extension added by Nop for XML format, indicating that the following values should be encoded in JSON format. Alternatively:

```xml
<and>
  <eq name="status" value="1" />
  <in name="type" value="1,2" />
</and>
```

The `value="1"` translates to the string "1" on the server side. If you specifically need to indicate that the condition value is an integer, use `@:` and `value="@:1"`.


### Crud Operations

CrudBizModel provides methods like findList, findPage, findFirst, get, delete, update, save, batchDelete, and batchModify for CRUD operations, supporting bulk processing and single-table data submission. All findXX methods accept filter and orderBy parameters and support complex combination queries using `and/or`.

Queries can directly use composite properties such as `moment.user.dept` in the ORM layer, which will be automatically recognized and translated into multi-table associations. This leverages NopORM's underlying EQL association query capabilities.

Global parameters like tenantId, authToken, traceId can be transmitted via HTTP headers. Each database entity has a corresponding service object accessible via `/r/{bizObjName}__{bizAction}`. Sub-table structures that do not need direct exposure can be marked as no-web in the data model, preventing separate endpoints from being generated.


### Links

For further details on NopGraphQL versus REST, please refer to [Why does GraphQL strictly outperform REST in mathematical terms?](https://mp.weixin.qq.com/s/7Ou1h7NwyI4eAX4m_Zbftw).

  
  # Query Logic and Filters
  
  * The `and/or` operators can be nested and support negation with `not`.
  
  * The Nop platform provides a range of query operators, such as `gt` for greater than and `ge` for greater than or equal to. For specific details, refer to the definitions in `FilterOp.java`.
  
  * When using JSON format, the following structure is used:
  
  ```json
  {
    "$type": "and",
    "$body": [
      {
        "$type": "eq",
        "name": 1,
      },
      {
        "$type": "in",
        "value": [1, 2]
      }
    ]
  }
  ```
  
  * In the `/r/User__get?id=123` endpoint for single entity retrieval, data access policies are applied. At this stage, any provided `filter` is converted into a memory-executed `Predicate` interface, effectively translating to:
  
  ```java
  class MyDataAuthFilter implements Predicate<IEvalScope> {
    public boolean accept(IEvalScope scope) {
      User user = (User) scope.getLocalValue("entity");
      if (user.getStatus() != 1)
        return false;

      if (!Arrays.asList(1, 2).contains(user.getType()))
        return false;
      
      return true;
    }
  }
  ```
  
  * Compared to APIJSON, NopGraphQL offers more powerful and intuitive functionality.
  
  # Query Scopes
  
  ## 1. Query Range by ID
  
  ```http
  POST /r/User__findList
  {
    "filter": {
      "$type": "in",
      "name": "id",
      "value": [38710, 82001, 70793]
    }
  }
  ```
  
  * This query retrieves a `User` array matching any of the specified IDs.
  
  ## 2. Matching Conditions by ID
  
  ```http
  POST /r/User__findList
  {
    "filter": {
      "$type": "or",
      "$body": [
        {
          "$type": "le",
          "name": "id", 
          "value": 80000
        },
        {
          "$type": "gt",
          "name": "id",
          "value": 90000
        }
      ]
    }
  }
  ```
  
  * This query fetches a `User` array where IDs satisfy either `id <= 80000` or `id > 90000`.
  
  ## 3. Containment by ID
  
  ```http
  POST /r/User__findList
  {
    "filter": {
      "$type": "in",
      "name": "contactIdList",
      "value": [38710]
    }
  }
  ```
  
  * This query retrieves a `User` array where their `contactIdList` contains the value 38710.

  However, due to security considerations:
  
  * The Nop platform does not directly execute SQL snippets or functions passed from the frontend.
  * All function calls must be encapsulated via backend operations, such as using operation symbols or fields, rather than exposing raw SQL.
  
  * For specific configurations requiring safe evaluation, a metadata model like `XMeta` can be introduced in the backend to handle transformations without direct exposure.

  ```xml
  <meta>
    <props>
      <prop name="contactIdList" queryable="true">
        <graphql:transFilter>
          <filter:sql>
            json_contains(o.contactIdList, ${value})
          </filter:sql>
        </graphql:transFilter>
      </prop>
    </props>
  </meta>
  ```
  
  * This configuration ensures that `contactIdList` is queried safely without exposing the underlying SQL structure to the frontend.

  * By default, only `in` and `eq` operators are allowed for queries. For other operators like `not`, `and`, or `or`, they must be explicitly configured using the `allowFilterOp` attribute.
  
# 4. 判断是否存在

In APIJSON, you can use the `transFilter` configuration to execute transformation logic. For example, in the example, the `<filter:sql>` tag is used to dynamically generate SQL fragments.

* If the above configuration feels cumbersome, you can leverage Nop's built-in meta-programming mechanism during compilation to automatically generate `transFilter` configurations for JSON type fields. This resembles native support for the `json_contains` operator.

```xml
<prop name="contactIdList" allowFilterOp="json_contains"/>
```

* After detecting `json_contains` during compilation, you can automatically generate `<graphql:transFilter>` configurations.

For complex query conditions, refer to [Nop Basics: How to Implement Complex Queries](https://mp.weixin.qq.com/s/5PVIrgqjlPQ549V9RZbDdg).

#### 4. Existence Check

In APIJSON, the following call can be used:

```json
["id}{@ [{
  "from":"Comment",
  "Comment":{
    "momentId":15
  }
}]
```

This can be interpreted as a subquery filter.

The SQL fragment for this would look like:

```sql
WHERE EXISTS(SELECT * FROM Comment WHERE momentId=15)
```

In the Nop platform, you can also use `transFilter` to configure this.

```xml
<prop name="existsComment" allowFilterOp="exists">
  <graphql:transFilter>
    <filter:sql>
      EXISTS(
        SELECT * FROM Comment o2 WHERE o2.momentId= ${ filter.getAttr('momentId') }
      )
    </filter:sql>
  </graphql:transFilter>
</prop>
```

#### 5. Remote Function Calls

In APIJSON, you can call remote functions using function expressions. For example:

```json
{
  "isPraised()": "isContain(praiseUserIdList, userId)"
}
```

This corresponds to the boolean function `boolean isContain(JSONObject request, String array, String value)`.

In the Nop platform, the GraphQL protocol inherently supports attribute function calls. Similar considerations apply for security: calling a specific attribute's function requires prior declaration in either XMeta or XBiz models, and cannot be directly passed from the frontend.

```xml
<!-- Adding isPraised property to User.xmeta -->
<prop name="isPraised">
  <arg name="praiseUserIdList" type="List<String>" />

  <getter>
    const api = inject("service_isContains");
    return api.invoke({ praiseUserIdList: praiseUserIdList, userId: entityId });
  </getter>
</prop>
```

* In the getter configuration, you can use `entity` to access the current entity.
* You can use the `inject` function to retrieve beans from the IOC container or use the `import` syntax to import Java classes.
* Nop's XPL template language simplifies attribute calls. For example:

```xml
<prop name="isPraised">
  <getter>
    <api:invoke name="isContains" args="${praiseUserIdList, userId: entityId}" />
  </getter>
</prop>
```

#### 6. Stored Procedures

You can follow the approach from the previous section by using Xpl templates in getters to call stored procedures. The NopDao module includes an IJdbcTemplate interface with a `callProc` method for calling stored procedures.

#### 7. Assignment by Reference

Using NopORM's association query capabilities, you can use composite attribute expressions like `user.dept.manager` to access associated properties within the object tree. In Java code and EQL query syntax, the same composite expression can be used.

For example, in getter configuration:

```json
{
  "isPraised": {
    "from": "Comment",
    "momentId": 15
  }
}
```

This can be interpreted as a subquery filter.

The corresponding SQL fragment would be:

```sql
WHERE EXISTS(SELECT * FROM Comment WHERE momentId=15)
```

In the Nop platform, you can use `transFilter` to configure this:

```xml
<prop name="existsComment" allowFilterOp="exists">
  <graphql:transFilter>
    <filter:sql>
      EXISTS(
        SELECT * FROM Comment o2 WHERE o2.momentId= ${ filter.getAttr('momentId') }
      )
    </filter:sql>
  </graphql:transFilter>
</prop>
```

#### 6. Stored Procedures

You can follow the approach from the previous section by using Xpl templates in getters to call stored procedures. The NopDao module includes an IJdbcTemplate interface with a `callProc` method for calling stored procedures.

#### 7. Assignment by Reference

Using NopORM's association query capabilities, you can use composite attribute expressions like `user.dept.manager` to access associated properties within the object tree. In Java code and EQL query syntax, the same composite expression can be used.

For example, in getter configuration:

```xml
<!-- Adding isPraised property to User.xmeta -->
<prop name="isPraised">
  <arg name="praiseUserIdList" type="List<String>" />

  <getter>
    const api = inject("service_isContains");
    return api.invoke({ praiseUserIdList: praiseUserIdList, userId: entityId });
  </getter>
</prop>
```

* In the getter configuration, you can use `entity` to access the current entity.
* You can use the `inject` function to retrieve beans from the IOC container or use the `import` syntax to import Java classes.
* Nop's XPL template language simplifies attribute calls. For example:

```xml
<prop name="isPraised">
  <getter>
    <api:invoke name="isContains" args="${praiseUserIdList, userId: entityId}" />
  </getter>
</prop>
```


Using the `graphql:transFilter` configuration, we can encapsulate subquery conditions.

```xml
<prop name="minUser">
  <graphql:transFilter>
    <filter:sql>
      o.id in (select min(o2.userId)) from Comment o2
    </filter:sql>
  </graphql:transFilter>
</prop>
```


#### Front-end Query Conditions

The query condition can be directly passed to the URL.

```url
/r/User__findFirst?filter_minUser=1
```


#### Fuzzy Search

Nop's QueryBean model supports contains, startsWith, endsWith, and like string partial match operators, which can be used directly in URLs.

```url
/r/User__findList?filter_userName__startsWith=a
```


#### Regular Matching

Regular expressions can be used with the regex operator to enable regular matching.


#### Date Range

Date range queries can be implemented using between, dateBetween, etc., to reflect SQL-like semantics.

```json
{
  "$type": "between",
  "name": "date",
  "min": "2017-10-01",
  "max": "2018-10-01"
}
```

If simplified filter parameters are used, it corresponds to:

```url
/r/User__findList?filter_date__between=2017-10-01,2018-10-01
```


#### Alias Creation

GraphQL supports an alias mechanism built-in.Aliases can be specified for each attribute when returning data. On the ORM level,NopORM automatically maps database fields to entity attributes during mapping, and it also supports alias configuration at the ORM level. By using aliases, related fields from associated tables can be mapped to the current entity. When modifying or querying, aliases help in referencing original field names.

Using the ORM's alias mechanism allows us to map database fields from associated tables to the current entity's attributes. This simplifies the use of extended fields, making them behave like regular fields.

For extended field configurations, refer to [Bilibili Video: How to Add Extended Attributes Without Modifying the Database](https://www.bilibili.com/video/BV1wL411D7g7/).


#### Adding or Extending

In APIJSON, `"key+"` syntax can be used to add to existing data, such as `"praiseUserIdList+"`: [82001].

This corresponds to SQL's `json_insert(praiseUserIdList, 82001)`.

Nop platforms follow standard ORM design. Typically, data is loaded into Java memory before modification, and any discrepancies are resolved with an update statement. Therefore, Nop does not use `json_insert` calls but instead maps directly to Java lists using standard add methods.

For child table structures, differences can be submitted to the backend via `_chgType` parameters like A, D, or U to represent add, delete, or update operations.

Currently, Nop does not provide a mechanism similar to APIJSON's list differential updates. For similar requirements, we recommend using Nop's built-in models and configurations rather than introducing special key conventions.

If forced to implement such behavior, Nop platforms would opt for prefix-based syntax in URLs rather than modifying key structures.

For example:
- `@delta: +82001` or `-32001` can be used to add or remove elements from a collection.
- This approach is more flexible than APIJSON's `key+` syntax and affects only the value, not the key structure.

APIJSON's syntax cannot handle simultaneous additions and deletions of multiple elements in a single operation. Using prefix-based syntax allows for localized updates without disrupting overall data integrity.

For detailed information on prefix guidance syntax, please refer to [DSL Layered Design and Prefix Guidance](https://zhuanlan.zhihu.com/p/548314138)


#### 14. Reduce or Remove

In APIJSON, the `key-:` syntax can be used to indicate a reduction in existing values, such as `"balance-": 100.00`, which translates to SQL `balance = balance - 100.00`. This reduces the balance by 100.00, equivalent to spending 100.00.

Similar to the previous section, Nop platform uses an ORM perspective and generally requires fetching data into memory before performing operations. Thus, you can directly call `setBalance(entity.getBalance() - 100)` on the entity. The entity typically has optimistic locking to ensure data consistency during updates.

For rare cases requiring direct database increment or decrement operations, you can handle them using XBiz models by executing SQL statements directly.

```xml
<action name="changeAmount">
  <arg name="accountId" type="String" />
  <arg name="delta" type="Double" />

  <source>
    <dao:ExecuteUpdate>
      update Account o set o.balance = o.balance - ${delta} where accountId = ${accountId}
    </dao:ExecuteUpdate>
  </source>
</action>
```

Similarly, if you need to implement a delta update mechanism similar to APIJSON, you can use the `@delta:` prefix guidance syntax.


#### 15. Comparison Operations

The Nop platform includes comparison operators such as gt, ge, eq, lt, le, and provides an ne operator for not equal comparisons. Additionally, it offers isEmpty, notEmpty, isNull, notNull, isBlank, and notBlank for convenient null checks.


#### 16. Logical Operations

Nop platform includes logical operators like and, or, and not, which can be freely nested and combined.

```json
{
  "$type": "or",
  "$body": [
    {
      "$type": "notIn",
      "name": "id",
      "value": [1, 2]
    },
    {
      "$type": "gt",
      "name": "status",
      "value": 1
    }
  ]
}
```

The query condition above translates to `(id not in [1,2]) or (status > 1)`.


#### 17. Array Keywords and Customization

The CrudBizModel in Nop platform provides functions like findPage and findList for collection queries. For example, findPage returns a PageBean object containing total, page, cursor, etc., along with items.

```java
class PageBean<T> {

  List<T> items;

  long total;
  long offset;
  int limit;
  Boolean hasPrev;
  Boolean hasNext;
  String prevCursor;
  String nextCursor;
}
```

It supports `offset/limit` pagination by position and also supports `id > :cursor limit 100` value-based sorting.

For sub-collections, Nop supports a mechanism similar to React Relay's Connection pagination and QueryBean pagination. Unlike APIJSON, Nop platform prioritizes security over flexibility, disallowing direct association specifications in the frontend. Instead, associations are defined in the backend through xmeta or orm models. This reduces repetitive data association expressions in multiple frontend requests.

By defining associations for Account and Business in the ORM layer, you can directly use `a.b.c` composite expressions, which automatically resolve to the full association path (Account -> Business -> Contact), significantly reducing repetitive queries.

Nop platform also supports DQL (Dimensional Query Language) as proposed by Ruin Zhang of Ruinsoft. This mechanism greatly simplifies sub-cube querying and reduces query complexity for multi-dimensional data.


Using the DataLoader mechanism provided by GraphQL, we can extend the backend's `findPage` service without modifying the `PageBean` class definition. Specifically, we can add additional return properties to the DataLoader.


#### 18. Customizable Object Keywords

The Nop platform does not require introducing a large number of special keywords at the syntax level like APIJSON does. Instead, it uses traditional method parameters for filtering and ordering.

Here's an example of a POST request:

```
POST /r/User__findList?@selection=id,sex,name
```

And here's the request body in JSON format:

```json
{
  "filter": {
    "$type": "and",
    "$body": [
      {
        "$type": "contains",
        "name": "name",
        "value": "a"
      },
      {
        "$type": "contains",
        "name": "tag",
        "value": "a"
      }
    ]
  },
  "orderBy": [
    {
      "field": "name",
      "desc": true
    },
    {
      "field": "id",
      "desc": false
    }
  ]
}
```

This query is equivalent to the following SQL statement:

```sql
select id, sex, name
from User
where name like '%a%' and tag like '%a%'
order by name desc, id asc
```

For more complex queries, we can utilize the `dao.xlib` library within the Nop platform's xbiz model. EQL (Extended Query Language) provides a syntax similar to SQL, supporting various functions and nested queries, making it much simpler than using JSON for such tasks.

Here's an example of a complex query using EQL:

```sql
<dao:FindPage offset="0" limit="100">
  with SectionCount as (
    select t.section, count(t.studentId) as studentCount
    from Taking t
    where t.section.year = 2017 and t.section.semester = 'Fall'
    group by t.section
  )
  select o.section, o.studentCount
  from SectionCount o
  where o.studentCount = (
    select max(sc.studentCount) 
    from SectionCount sc
  )
</dao:FindPage>
```

In the xbiz model, we can further abstract the SQL construction process using `xpl` templates. EQL syntax allows for handling even the most complex SQL statements, whereas APIJSON's design often falls short when dealing with dynamic SQL, especially without a straightforward abstraction mechanism for such logic.

For more details on managing complex SQL queries, refer to [Nop Basics: Dynamic SQL Management](https://mp.weixin.qq.com/s/_5-CSfY5SXquknvdINemNA).


### 19. Global Keywords

The Nop platform is not designed specifically for database access but rather as a generic helper class framework. The `CrudBizModel` is just one of the many utility classes it provides. NopGraphQL is a general-purpose GraphQL engine, while NopORM is similar to Hibernate in its functionality as an ORM (Object-Relational Mapping) tool.

By leveraging these foundational engines alongside BizModels (business models), we can build more complex and versatile low-code platforms.

For instance, [NopTaskFlow](https://mp.weixin.qq.com/s/2mFC0nQon_l2M82tOlJVhg), the next-generation logic engine, enables backend service automation without manual coding.


## Summary

1. Nop platform supports configuration-based APIJSON functionality and standard GraphQL protocol without requiring special conventions.
2. The platform enforces strict security measures, including role-based access control for fields and data permissions.
3. It balances rapid prototyping with meticulous project requirements, supporting both small-scale proofs of concept and large-scale, long-term projects.

3. The Nop platform offers industrially unique expandability. For details, please refer to [Why is the Nop platform an industry-leading open-source software development platform?](https://mp.weixin.qq.com/s/vCPpnE-VMF7GW7yCOGWKxw)
