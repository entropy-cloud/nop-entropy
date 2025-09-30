
# Feature Comparison: Nop Platform vs. APIJSON

[APIJSON](http://apijson.cn/) is a widely adopted zero-code interface and documentation ORM library developed by Tencent engineers, with as many as 16.8K stars on GitHub. This library implements a lightweight data exchange format based on JSON, providing universal generic interfaces that enable CRUD, cross-database joins, nested subqueries, and more without coding. APIJSON’s ecosystem is quite complete: it supports various backend databases, offers clients in multiple languages, and provides a series of peripheral features such as automatic documentation generation and automated API testing.

The Nop Platform comes with next-generation engines NopGraphQL and NopORM, designed from scratch based on the principles of reversible computing. Together, they can easily implement features similar to APIJSON while offering better extensibility. In this article, using APIJSON’s documentation outline as a basis, I will compare item by item the differences in functionality when the Nop Platform is used as a low-code data service engine.

> As a general next-generation low-code platform, the Nop Platform has a much more ambitious goal than APIJSON. Nop promotes the so-called language-oriented programming paradigm: first, enable users to quickly develop their own DSL (domain-specific language), and then use that DSL to build specific business logic. The functionality provided by APIJSON can be seen as a data-access-oriented DSL, while the Nop Platform provides the underlying tools for developing similar DSLs and already includes a series of mature DSLs such as NopGraphQL and NopORM, making it easy for users to combine them.

## I. Examples

In the Nop Platform, REST requests are executed by the NopGraphQL engine. NopGraphQL supports multiple access protocols including GraphQL, gRPC, and REST, allowing multiple ways to invoke the same backend service function.
For REST, NopGraphQL supports two URL access patterns: `/r/{bizObjName}__{bizAction}` and `/p/{bizObjName}__{bizAction}`.

A `/r/` request returns an `ApiResponse<T>` structure, which includes properties such as headers, data, stats, code, and msg. If status equals 0, the request is successful. On failure, code returns the error code, and msg returns the exception message.

```java
class ApiResponse<T>{
  Map<String,Object> headers;
  T data;
  int status;
  String code;
  String message;
}
```

A `/p/` request returns `T` directly without wrapping it in `ApiResponse<T>`. In addition, `/p/` requests set contentType, so downloading binary files or returning XML formats should also use `/p/`.
For example, the platform’s built-in `/p/DevDoc__beans` returns the configuration of all enabled beans in NopIoC in XML format.

When using the REST request mode, HTTP GET can call GraphQL query methods, and HTTP POST can call either GraphQL query or mutation methods.

### Get User

APIJSON request:

```json
{
  "User":{
    "id":38710
  }
}
```

Nop request:

```
query{
  User__get(id: "38710"){
     id,sex, name, tag, head, data, pictureList
  }
}
```

Response:

```json
{
  "data":{
    "User__get": {
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
    }
  }
}
```

Or using the `/r/` request mode:

```
/r/User__get?id=38710
```

Response:

```json
{
  "data":{
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
  "status": 0
}
```

A third approach is to use `/p/`:

```
/p/User__get?id=38710
```

Response:

```json
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
}
```

### Get User List

APIJSON request:

```json
{
  "[]":{
    "count":3,               // only 3 items
    "User":{
      "@column":"id,name"    // only return fields id and name
    }
  }
}
```

Nop request:

```
/r/User__findList?limit=3&@selection=id,name
```

Response:

```json
{
  "data": [
    {
      "User":{
        "id":38710,
        "name":"TommyLemon"
      }
    },
    {
      "User":{
        "id":70793,
        "name":"Strong"
      }
    },
    {
      "User":{
        "id":82001,
        "name":"Android"
      }
    }
  ],
  "status": 0
}
```

- CrudBizModel provides a findList function that can execute complex paginated queries and return a list of data.
- The findPage function can execute complex paginated queries and returns a `PageBean<T>` object. PageBean includes total pages, current page, current page data, and more.
- NopGraphQL provides field selection capabilities compliant with the GraphQL specification, allowing complex nested selections with support for field renaming and additional parameters.

```
/r/User__findList?@selection=id,name,status:userStatus,
     roles:rolesList(limit:5)%7BroleId, roleName%7D
```

- `roles:rolesList(limit:5)` calls the backend User object’s rolesList loading method, limits the returned items to a maximum of 5, and renames the corresponding property to roles.
- `{` and `}` are special characters in URLs and must be URL-encoded: `{` corresponds to `%7B`, and `}` corresponds to `%7D`.

### Get a Moment and Its Publisher User

APIJSON request:

```json
{
  "Moment":{
  },
  "User":{
    "id@":"Moment/userId"  // User.id = Moment.userId
  }
}
```

Nop request:

```
/r/Moment__findFirst?@selection=...F_defaults,user
```

Nop response:

```json
{
  "status": 0,
  "data": {
    "id":12,
    "userId":70793,
    "date":"2017-02-08 16:06:11.0",
    "content":"1111534034",
    "user": {
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

- For security reasons, NopGraphQL does not support passing table association conditions directly from the frontend, as it is hard to control data access scope and the size of intermediate datasets.
- On the backend ORM model, you can configure the association between Moment and User by adding a user association property on the Moment object. By default, associated objects are marked lazy-loaded, so if the frontend does not explicitly request the property, it will not be returned.
- In the frontend selection definition, `...F_defaults` uses GraphQL’s fragment syntax to reference the set of all non-lazy fields, on top of which we can request the associated user object.
- findFirst returns the first record that matches complex query conditions. If no sort condition is specified, it sorts by primary key.
- If no association is established at the ORM level, you can still define association conditions in an XMeta metadata file:

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

For more details, see [Nop Getting Started: How to Implement Complex Queries]()

- Here `graphql:queryMethod` indicates that when the frontend requests the user property, the findFirst method will be used with the `graphql:filter` condition to fetch the data.
- In principle, additional query and sorting conditions can be passed in. These conditions combine with the `graphql:filter` configuration to form the final condition.

### Fetch a WeChat Moments-like Timeline

APIJSON request:

```json5
{
  "[]":{                             // request an array
    "page":0,                        // array-level condition
    "count":2,
    "Moment":{                       // request an object named Moment
      "content$":"%a%"               // object-level condition: search moments where content contains 'a'
    },
    "User":{
      "id@":"/Moment/userId",        // User.id = Moment.userId; default reference assignment path starts from the parent container of the current context
      "@column":"id,name,head"       // specify return fields
    },
    "Comment[]":{                    // request an array named Comment, and strip the Comment wrapper
      "count":2,
      "Comment":{
        "momentId@":"[]/Moment/id"   // Comment.momentId = Moment.id; full reference assignment path
      }
    }
  }
}
```

APIJSON response:

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
```

Nop request:

```
/r/Moment__findList?offset=0&limit=2&filter_content__contains=a
&@selection=...F_defaults,user%7Bid,name,head%7D,comments(limit:2)
```

Nop response:

```json
{
  "status": 0,
  "data":[
    {
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
        ],
        "user": {
            "id":70793,
            "name":"Strong",
            "head":"http://static.oschina.net/uploads/user/585/1170143_50.jpg?t=1390226446000"
        },
        "comments": [
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
        ],
        "user":{
            "id":90814,
            "name":7,
            "head":"http://static.oschina.net/uploads/user/51/102723_50.jpg?t=1449212504000"
        },
        "comments":[
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
  ]
}
```

- Compared with APIJSON, NopGraphQL returns standard JSON object structures with naturally nested property names, while APIJSON uses a flattened object structure and special conventions like `Comment[]`, which may require extra structural conversion on the frontend before passing to components.
- NopGraphQL supports many multi-table association queries: one-to-one, one-to-many, many-to-one, with various conditions. However, for security reasons, association conditions must be configured in backend XMeta or ORM models.
- NopORM supports various JOINs: LEFT JOIN, INNER JOIN, FULL JOIN, etc. Via Dialect, it supports various SQL functions and can migrate across databases.
- `filter_content__contains=a` requires the backend XMeta file to specify `allowFilterOps="in,eq,contains"` for the prop; you must enable the contains operator to use it.

## II. Comparison with Traditional RESTful Style

### 2.1 Development Workflow

| Development Workflow | Traditional Approach                                                                 | APIJSON                                                                                                                                                                    |
| ---- | ---------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interface exchange | Wait for the backend to edit the interface, then update the docs; the frontend edits requests and parsing code according to the docs | The frontend edits requests and parsing code according to its own needs.<br />There is no interface, nor any need for docs! The frontend no longer has to coordinate about interfaces or docs! |
| Backward compatibility | The backend adds a new interface and marks it as v2 for the second version, then updates the docs                    | Do nothing!                                                                                                                                                                |

**Nop Development Workflow:**

- The advantages claimed by APIJSON are all provided natively when NopGraphQL and NopORM are combined.
- Using the GraphQL protocol, you can reorganize returned data and maintain backward compatibility automatically through field selection, without adding API version numbers. By customizing xmeta and orm models, you can extend returned fields without writing server-side code.
- The Nop Platform’s unique Delta customization capability enables the same service to provide different default returned fields and different associations with data entities under different deployment environments, without modifying already packaged server-side code.

### 2.2 Frontend Requests

| Frontend Requests | Traditional Approach                                                                                                                    | APIJSON                                                                                                                                                                                                                                                                                                                                 |
| ---- | ---------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Requirements | The frontend assembles key-value pairs onto the corresponding URL according to the docs                                                                             | The frontend assembles JSON onto a fixed URL according to its own needs                                                                                                                                                                                                                                                                 |
| URL | Different requests correspond to different URLs; basically, the number of distinct requests equals the number of interface URLs                                               | The same operation methods (CRUD) all use the same URL. <br />Most requests use one of 7 generic interface URLs                                                                                                                                                                                                                         |
| Key-value pairs | key=value                                                                                                                                      | key:value                                                                                                                                                                                                                                                                                                                                |
| Structure | Within the same URL, table_name can only be one. <br /><br /> base_url/get/table_name?<br />key0=value0&key1=value1...                           | Any number of TableName objects can be passed after the same URL. <br /><br /> base_url/get/<br />{<br /> &nbsp;&nbsp; TableName0:{<br /> &nbsp;&nbsp;&nbsp;&nbsp; key0:value0,<br /> &nbsp;&nbsp;&nbsp;&nbsp; key1:value1,<br /> &nbsp;&nbsp;&nbsp;&nbsp; ...<br /> &nbsp;&nbsp; },<br /> &nbsp;&nbsp; TableName1:{<br /> &nbsp;&nbsp;&nbsp;&nbsp; ...<br /> &nbsp;&nbsp; }<br /> &nbsp;&nbsp; ...<br /> } |

**Nop Frontend Requests:**

- Leveraging the selection mechanism in the GraphQL protocol, the Nop Platform can achieve effects equivalent to APIJSON, while returning a more natural and intuitive nested data structure. You can also use standard third-party GraphQL tools for debugging, such as the graphql-ui tool integrated in the Quarkus framework.

- The built-in CrudBizModel provides a series of CRUD-related service functions to implement complex master-detail structures for create, read, update, and delete. Common data maintenance tasks either require no code or only small amounts of logic deviating from CRUD.

- Beyond CRUD operations, NopGraphQL also supports business entity objects and their methods that have no backing database tables.

- The GraphQL protocol natively supports invoking multiple backend service functions in one shot. For example:

  ```graphql
  query{
      Entity1__findPage{
         items: { fld1, fld2}
      },
      Entity2__get(id:"333") { name, status}
  }
  ```

### 2.3 Backend Operations

| Backend Operations | Traditional Approach                                                                                    | APIJSON                                  |
| ------------- | -------------------------------------------------------------------------------------------------- | ---------------------------------------- |
| Parsing and returning | Extract key-value pairs, use them as conditions to query the database in preset ways, and finally wrap JSON and return to the frontend | Just return the result of Parser#parse   |
| How the JSON response structure is defined | Defined by the backend; the frontend cannot modify                                                           | Defined by the frontend; the backend cannot modify |

**Backend Operations in Nop Platform:**

- The Nop Platform’s backend uses NopORM, which includes a complete EQL object query language and built-in support for multi-tenancy, extended fields, logical deletion, and other common business needs. The overall design is more complete, powerful, and extensible than JPA+MyBatis.

For details, see [What Kind of ORM Engine Does a Low-Code Platform Need? (1)](https://mp.weixin.qq.com/s/biBdNaQV98uaxdpVKndwwg), [What Kind of ORM Engine Does a Low-Code Platform Need? (2)](https://mp.weixin.qq.com/s/Nv9Z23rv0ijwJ34PPH-uyw)

### 2.4 Frontend Parsing

| Frontend Parsing | Traditional Approach                                            | APIJSON                                                                       |
| ---- | ----------------------------------------------------------------- | ----------------------------------------------------------------------------- |
| How to view | Check docs or ask the backend, or wait for the request to succeed and check the logs | Just look at the request; what you ask is what you get—no checking, asking, or waiting. You can also check logs after success |
| Parsing method | Use a JSON parser to parse JSONObject                        | Use JSONResponse to parse JSONObject, or use the traditional method           |

**Frontend Parsing in Nop Platform:**

- The Quarkus framework provides robust development and debugging tools, such as graphql-ui and online log viewing.
- The Nop Platform logs at all key points, e.g., input/output JSON data, the actual SQL statements executed, parameters and execution times for each SQL, etc.
- In debug mode, `/p/DevDoc__graphql` lets you view GraphQL definitions for all backend service functions and data objects.
- The Nop Platform considers field masking when outputting logs; sensitive data such as passwords and card numbers are automatically masked before being logged.
- Results are generally JSON structures and can be parsed with JSON parsers. `/p/` requests can return other formats to support binary file downloads, etc.

### 2.5 Frontend Requests for Different Needs

The Nop Platform can freely use the standard GraphQL protocol, gRPC protocol, or REST protocol to access backend service objects. The overall usage is more intuitive and simpler than APIJSON and includes all capabilities of APIJSON.
In addition, the Nop Platform offers unique Delta customization and metaprogramming capabilities.

#### 1. Fetch a single user

```
/r/User__get?id=38710
```

#### 2. Moment and its corresponding User

```
/r/Moment__findFirst?filter_userId=38710&@selection=...F_defaults,user
```

#### 3. User list

```
/r/User__findList?offset=0&limit=3&filter_sex=0
```

#### 4. Moment list, each Moment includes 1. Publisher User 2. Top 3 Comments

```
/r/Moment__findList?@selection=...F_defaults,user,comments(limit:3)
```

#### 5. List of Moments published by a User, each Moment includes 1. Publisher User 2. Top 3 Comments

```
/r/Moment__findList?filter_userId=38710&@selection=...F_defaults,user,comments(limit:3)
```

### 2.6 Backend Return Formats for Different Requests

The Nop Platform supports multiple ways to invoke the same backend service function, returning different result formats. Whether using the standard GraphQL protocol or REST, you call the same service function, making integration with various frontends easy.

1. Requests prefixed with `/r/` return an `ApiResponse<T>` structure.
2. Requests prefixed with `/p/` return only the data part, without wrapping it in ApiResponse.
3. Via the `@selection` parameter, you can specify the returned data fields. Unselected fields are skipped by the backend, reducing data loaded and computation performed.
4. For queries, the built-in CrudBizModel provides `findList/findPage/findFirst`, which return list data, paginated list data with item counts, and the first matching record, respectively.

For more on NopGraphQL, see [Why, Mathematically, GraphQL Is Strictly Superior to REST](https://mp.weixin.qq.com/s/7Ou1h7NwyI4eAX4m_Zbftw)

## III. Design Guidelines

### 3.1 Operation Methods

1. In line with the GraphQL specification, the Nop Platform’s server uses only GET and POST methods, not PUT, DELETE, PATCH, etc., simplifying frontend and backend handling.
2. GET can only call idempotent GraphQL query operations, while POST can call GraphQL query or mutation operations.
3. Mutation operations automatically open a database transaction (with internal optimizations—if the database is not actually accessed, no connection is held).
4. CrudBizModel provides `findList/findPage/findFirst/get/delete/update/save/batchDelete/batchModify` and more for CRUD operations, supports batch processing, and supports submitting master-detail data in one shot.
5. All findXX methods accept filter query conditions and orderBy sorting conditions, supporting complex combinations with `and/or`.
6. During queries, you can directly use composite properties like `moment.user.dept`; at the ORM level, they are automatically recognized and expanded into multi-table association queries. This leverages NopORM’s underlying EQL, an object query language with association capabilities.
7. You can pass global parameters via HTTP headers, such as tenantId, authToken, traceId, etc.
8. Each database entity has a corresponding service object by default. You can call service methods via `/r/{bizObjName}__{bizAction}`. For subtable structures that should not be exposed directly, you can mark the data model as no-web so that no separate service endpoint is generated.

### 3.2 Operators

The Nop Platform expresses complex query conditions using the QueryBean model. This is a general-purpose Predicate definition model that is automatically converted into SQL queries or in-memory Predicate interfaces and can freely convert among formats like XML and JSON. For example, to express `status=1 and type in(1,2)`, the QueryBean XML looks like:

```xml
<and>
  <eq name="status" value="1" />
  <in name="type" value="@:[1,2]" />
</and>
```

- `@:` is an XML extension in the Nop Platform indicating the subsequent value is JSON-encoded.

- You can also use `<in name="type" value="1,2" />`; comma-separated strings are automatically split into string lists.

- `value="1"` is parsed as the string "1". The Nop Platform automatically converts types during queries based on the ORM field type definitions, so status is converted to an integer. If you must indicate the condition value as an integer, use the `@:` prefix, e.g., `value="@:1"`.

- and/or can be nested and combined with not.

- The Nop Platform provides many built-in operators, such as gt for greater than and ge for greater than or equal. See FilterOp.java for the specific built-in op definitions.

If using JSON, the equivalent is:

```json
{
    "$type": "and",
    "$body":[
       {
          "$type": "eq",
          "name": 1,
       },
       {
           "$type": "in",
           "value": [1,2]
       }
    ]
}
```

For `/r/User__get?id=123` fetching a single entity, the Nop Platform applies data authorization rules. In this case, filter is automatically translated into an in-memory Predicate interface, equivalent to:

```java
class MyDataAuthFilter implements Predicate<IEvalScope>{
   public boolean accept(IEvalScope scope){
      User user = (User)scope.getLocalValue("entity");
      if(user.getStatus() != 1)
         return false;

      if(!Arrays.asList(1,2).contains(user.getType()))
         return false;
      return true;
   }
}
```

Compared with APIJSON, NopGraphQL’s operator capabilities are stronger, more intuitive, and simpler to use.

#### 1. Set-membership query

```
POST /r/User__findList

{
  "filter": {
    "$type": "in",
    "name": "id",
    "value": [38710,82001,70793]
  }
}
```

This queries a User array whose id is any of 38710, 82001, or 70793.

For batch fetching by id, the Nop Platform also provides a simplified method, batchGet:

```
/r/User__batchGet?ids=38710,82001,70793
```

#### 2. Range-matching conditions

```
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

This queries a User array where `id<=80000 || id>90000`.

#### 3. Containment on options

In APIJSON, `["contactIdList<>":38710]` is equivalent to the SQL filter `json_contains(contactIdList,38710)`, i.e., query for a User array whose contactIdList contains 38710.

However, for security reasons, the Nop Platform does not support executing SQL fragments or SQL function calls passed from the frontend; all function calls must be wrapped as operators or data fields on the backend and not exposed directly to the frontend.

For this requirement, we introduce a transformation in the backend XMeta metadata model:

```xml
<meta>
   <props>
     <prop name="contactIdList" queryable="true"
          allowFilterOp="contains,eq">
       <graphql:transFilter>
          <filter:sql>
             json_contains( o.contactIdList, ${value} )
          </filter:sql>
      </graphql:transFilter>
     </prop>
   </props>
</meta>
```

- For security, by default only in or eq operators are allowed. To use other operators, you must configure them via allowFilterOp.
- You can configure `<graphql:transFilter>` to perform transformation logic. In the example, `<filter:sql>` from the `filter.xlib` tag library is used to dynamically generate an SQL fragment.
- If the above configuration feels verbose, leverage the Nop Platform’s built-in metaprogramming to generate transFilter configurations uniformly at compile time for JSON-type fields. This makes it feel like json_contains is natively supported.

```xml
<prop name="contactIdList" allowFilterOp="json_contains"/>
```

- After recognizing the json_contains operator at compile time, the system can automatically generate the `<graphql:transFilter>` configuration.

For complex query condition configurations, see [Nop Getting Started: How to Implement Complex Queries](https://mp.weixin.qq.com/s/5PVIrgqjlPQ549V9RZbDdg)

#### 4. Existence checks

In APIJSON, the following call:

```json
["id}{@":{
  "from":"Comment",
  "Comment":{
   "momentId":15
 }
}]
```

represents a subquery filter:

```sql
 WHERE EXISTS(SELECT * FROM Comment WHERE momentId=15)
```

In the Nop Platform, we can also use transFilter configurations:

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

#### 5. Remote function invocation

In APIJSON, you can use function expressions to call remote functions. For example:

```json
{
  "isPraised()": "isContain(praiseUserIdList,userId)"
}
```

This calls the remote function `boolean isContain(JSONObject request, String array, String value)`.

In the Nop Platform, property function calls are supported by the GraphQL protocol itself. Again, for security, which function a property calls must be declared beforehand in XMeta or XBiz models and cannot be passed directly from the frontend.

```xml
<!-- Add an isPraised computed property in User.xmeta -->
<prop name="isPraised">
  <arg name="praiseUserIdList" type="List<String>" />

  <getter>
    const api = inject("service_isContains");
    return api.invoke({praiseUserIdList,userId:entity.id});
  </getter>
</prop>
```

- In the getter configuration, you can access the current entity via entity.
- Use inject to obtain beans from the IoC container, or use import syntax to import Java classes.
- You can leverage the XPL templating language’s tag abstraction to simplify calls. For example:

```xml
<prop name="isPraised">
   <getter>
      <api:invoke name="isContains" args="${{praiseUserIdList, userId:entityId}}" />
   </getter>
</prop>
```

#### 6. Stored procedures

Following the previous approach, you can call stored procedures in the getter via the Xpl templating language. The IJdbcTemplate interface in the NopDao module has a callProc function to invoke stored procedures.

#### 7. Reference assignment

Leveraging NopORM’s association query capabilities, we can use composite property expressions like `user.dept.manager` to access associated properties in an object tree. In Java code and the EQL object query syntax, you can use the same composite property expression.

For example, in a getter configuration, you can use a composite property expression to get an associated property from the current entity:

```xml
<prop name="managerName">
  <getter>
    return entity.dept.manager.name
  </getter>
</prop>
```

#### 8. Subqueries

You can use the `graphql:transFilter` configuration to encapsulate subquery conditions:

```xml
<prop name="minUser">
   <graphql:transFilter>
     <filter:sql>
       o.id in (select min(o2.userId)) from Comment o2
     </filter:sql>
   </graphql:transFilter>
</prop>
```

Frontend request:

```
/r/User__findFirst?filter_minUser=1
```

#### 9. Fuzzy search

The Nop Platform’s QueryBean model supports contains, startsWith, endsWith, like, and other partial string match operators, which can be used directly in URLs:

```
/r/User__findList?filter_userName__startsWith=a
```

#### 10. Regex matching

Use the regex operator to express regular expression matches.

#### 11. Continuous ranges

Use between and dateBetween operators to implement SQL between semantics.

```json
{
  "$type": "between",
  "name": "date",
  "min": "2017-10-01",
  "max": "2018-10-01"
}
```

Using the simplified filter parameter:

```
/r/User__findList?filter_date__between=2017-10-01,2018-10-01
```

#### 12. Aliasing

GraphQL provides a built-in alias mechanism to specify an alias for each property in returned data. At the ORM level, NopORM can select the mapped entity property name for a database field, and also provides alias configuration so that fields on associated entity tables can be mapped onto the current entity. When modifying and querying, fields corresponding to alias behave exactly like native entity fields.

With ORM-level alias, we can map extended fields stored in vertical tables (EAV) onto the current entity as properties identical to ordinary fields, simplifying extended field usage.

For extended field configuration, see [Bilibili Video: How to Add Extended Properties to an Entity Without Changing the Database](https://www.bilibili.com/video/BV1wL411D7g7/)

#### 13. Increment or extend

In APIJSON, the `"key+"` syntax indicates increment on the original basis, e.g., `"praiseUserIdList+":[82001]`, which corresponds to `json_insert(praiseUserIdList,82001)`, adding a like user id to denote that the user liked the post.

The Nop Platform uses standard ORM design. Generally, you first load data into Java memory, modify the corresponding fields, then generate an update statement upon detecting differences. Therefore, the Nop Platform does not use `json_insert`; instead, the JSON field is mapped to a Java list, and you simply call add in Java.

For subtable structures, you can submit delta updates to the backend using `_chgType=A`, `_chgType=D`, `_chgType=U` to distinguish add, update, delete, etc.

Currently, the Nop Platform does not provide a list delta update mechanism similar to APIJSON. Typically, similar requirements can be implemented via xbiz models. If such a feature is necessary, the Nop Platform would not introduce special conventions at the key level but would use prefix-guided syntax and operate on the value structure.
For instance, we could define `@delta: +82001, -32001` to indicate adding element 82001 and removing element 32001 from the collection. Compared with APIJSON’s `key+` syntax, this value-level prefix is more flexible and does not affect the overall object structure (the key remains the same). It is purely a local enhancement.
Moreover, APIJSON’s syntax cannot express hybrid changes such as adding several elements while removing others at the same time.

> Adding prefixes at the value level affects only the value for a single key. If conventions are introduced at the key level, they affect not the current key but the parent object one level up. In handling, you cannot easily leverage mapping mechanisms to quickly determine whether a key exists or is unique. The root cause is that changing keys breaks the original structure.

For details on prefix-guided syntax, see [Layered DSL Syntax Design and Prefix-Guided Syntax](https://zhuanlan.zhihu.com/p/548314138)

#### 14. Decrement or removal

In APIJSON, `key-:` indicates decrement on the original basis, e.g., `"balance-":100.00` corresponds to `balance = balance - 100.00`, i.e., the balance decreases by 100.00.

Similarly, the Nop Platform uses the standard ORM view: you typically load data into memory first, then call `setBalance(entity.getBalance()-100)` on the entity. Entities generally have optimistic locking to ensure consistency.

For cases where you must directly execute increment/decrement in the database, you can perform SQL in an XBiz model:

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

As in the previous section, if you must introduce a delta update mechanism similar to APIJSON, use the `@delta:` prefix-guided syntax.

#### 15. Comparison operators

The Nop Platform provides gt, ge, eq, lt, le, and ne (not equal). It also provides isEmpty, notEmpty, isNull, notNull, isBlank, notBlank for convenient null/empty checks.

#### 16. Logical operators

The Nop Platform provides and, or, not operators that can be freely nested and combined.

```json
{
  "$type": "or",
  "$body": [
    {
      "$type": "notIn",
      "name": "id",
      "value": [1,2]
    },
    {
      "$type": "gt",
      "name": "status",
      "value": 1
    }
  ]
}
```

The above condition means `(id not in [1,2] or status > 1)`.

#### 17. Array-related keywords, customizable

The Nop Platform’s CrudBizModel provides findPage and findList for querying collections. findPage returns a PageBean object, which offers total, page, cursor, and other fields:

```java
class PageBean<T>{

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

It supports offset/limit (index-based pagination) and value-based cursor pagination (e.g., `id > :cursor limit 100`).

For subtable collections, it supports a Connection-style pagination mechanism similar to React Relay and also supports QueryBean-style pagination. Unlike APIJSON, for security reasons, the Nop Platform does not allow the frontend to specify table associations; they must be specified in backend xmeta or orm models.
Specifying in the backend has the benefit of automatic reuse, eliminating the need to repeatedly express data associations across multiple frontend requests. For example, after defining associations from table A to B and from B to C at the ORM level, the composite property expression `a.b.c` can automatically infer the association path from A to B to C, greatly reducing redundant repetition.

The Nop Platform also supports a Dimensional Query Language (DQL) proposed by Runqian Software, which can significantly reduce the complexity of statistical queries over master-detail data.

By leveraging GraphQL’s DataLoader mechanism, we can add extra returned properties to a backend findPage service via DataLoader without modifying the PageBean class definition. For details, see [Nop Getting Started: How to Extend Existing Services](https://mp.weixin.qq.com/s/H3SxiFAsqVJz0PR15tWkww)

#### 18. Object-related keywords, customizable

The Nop Platform does not need to introduce a large number of keyword conventions at the syntax level like APIJSON; traditional method parameters suffice.

```
POST /r/User__findList?@selection=id,sex,name

{
  "filter": {
    "$type": "and",
    "$body":[
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
      "desc": true,
    },
    {
      "field": "id",
      "desc": false
    }
  ]
}
```

The above is equivalent to:

```sql
select id,sex,name
from User
where name like '%a%' and tag like '%a%'
order by name desc, id asc
```

More complex queries can be implemented directly in backend xbiz models using tags from the `dao.xlib` tag library. You can use the EQL object query syntax, which is very similar to SQL, supports various SQL functions and complex nested subqueries, and is much simpler than using JSON syntax:

```sql
<dao:FindPage offset="0" limit="100">
   with SectionCount as
    (
    select t.section, count(t.studentId) as studentCount
    from Taking t
    where t.section.year = 2017 and t.section.semester = 'Fall'
    group by t.section
    )
    select o.section, o.studentCount
    from SectionCount o
    where o.studentCount = (
    select max(sc.studentCount) from SectionCount sc
    )
</dao:FindPage>
```

In xbiz models, we can further abstract the SQL construction process using xpl template tags to simplify writing complex SQL. EQL can support the most complex SQL statements, while in APIJSON many complex SQL statements are difficult to express directly, especially lacking a simple abstraction mechanism to encapsulate dynamic SQL construction into a localized concept.

For more on complex SQL, see [Nop Getting Started: Dynamic SQL Management](https://mp.weixin.qq.com/s/_5-CSfY5SXquknvdINemNA)

### 19. Global Keywords

The Nop Platform is not designed specifically for database access; CrudBizModel is just an ordinary helper. NopGraphQL is a general-purpose GraphQL engine, and NopORM is a general-purpose ORM engine comparable in scope to Hibernate. With these foundational engines, combined with BizModel and other orchestration models, we can deliver a more complex and general-purpose low-code processing platform.

See [A Next-Generation Logic Orchestration Engine Built from Scratch: NopTaskFlow](https://mp.weixin.qq.com/s/2mFC0nQon_l2M82tOlJVhg), [Implement Backend Service Functions via Logic Orchestration Using NopTaskFlow](https://mp.weixin.qq.com/s/CMBcV9Riehlf4_Ds_BmyEw)

## Summary

1. The Nop Platform can implement all features provided by APIJSON through configuration and uses the standard GraphQL protocol with minimal special conventions.
2. The Nop Platform enforces stricter security with robust operation permissions, field permissions, and data permissions, supporting both small rapid prototypes and rigorous large projects that evolve persistently.
3. The Nop Platform offers unique extensibility in the industry. See [Why the Nop Platform Is a Unique Open-Source Software Development Platform](https://mp.weixin.qq.com/s/vCPpnE-VMF7GW7yCOGWKxw)

<!-- SOURCE_MD5:187b988ffc99e85fa47d708a120d4b8f-->
