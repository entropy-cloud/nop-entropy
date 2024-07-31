# Nop平台与APIJSON的功能对比

[APIJSON](http://apijson.cn/)是由腾讯的工程师研发的一款流传甚广的零代码接口与文档ORM库，github的star数高达16.8K。这个库实现了一种基于JSON的轻量级数据交换格式，提供万能通用接口，无需编码即可实现增删改查、跨库连表、嵌套子查询等。APIJSON的生态相当完整，支持各种后端数据库，有各种语言的客户端，支持自动生成文档，自动进行API测试等一系列外围功能。

Nop平台内置了基于可逆计算原理从零开始设计的下一代GraphQL引擎NopGraphQL和下一代ORM引擎NopORM，它们结合在一起可以很容易的实现类似APIJSON的功能，并且提供更好的可扩展性。在本文中，我就以APIJSON的文档大纲为基础，逐条比较一下Nop平台在作为低代码数据服务引擎使用时与APIJSON的功能差异。

> Nop平台作为一个通用的下一代低代码平台，它的发展目标远比APIJSON要宏大得多。Nop平台推行的是所谓面向语言编程范式，即先支持用户快速开发属于自己的DSL领域特定语言，然后再用这个DSL去开发具体业务。APIJSON提供的功能可以看作是一种面向数据访问的DSL，而Nop平台提供了开发类似DSL的底层支撑工具，并内置了NopGraphQL和NopORM等一系列成熟的DSL，便于用户在此基础上组合使用。

## 一.示例

在Nop平台中，REST请求由NopGraphQL引擎来负责执行。NopGraphQL同时支持GraphQL协议、gRPC协议、REST协议等多种访问协议，可以使用多种方式来调用同一个后台服务函数。
对于REST请求方式，NopGraphQL支持两种访问连接模式： `/r/{bizObjName}__{bizAction}`和`/p/{bizObjName}__{bizAction}`。

其中`/r/`请求会返回`ApiResponse<T>`结构，它包含headers, data, stats, code, msg等属性。 如果status为0，则表示成功。如果失败，则通过code来返回错误码，通过msg来返回异常消息。

```java
class ApiResponse<T>{
  Map<String,Object> headers;
  T data;
  int status;
  String code;
  String message;
}
```

`/p/`请求会直接返回`T`结构，而不会使用`ApiResponse<T>`来包装。此外`/p/`请求会设置contentType，因此下载二进制文件、返回XML格式等功能也需要使用`/p/`请求。
例如Nop平台内置的`/p/DevDoc__beans`会以XML格式返回NopIoC中所有启用的bean的配置。

在使用REST请求模式的情况下，HTTP GET可以调用GraphQL的query方法，而HTTP POST可以调用GraphQL的query或者mutation方法。

### 获取用户

APIJSON请求：

```json
{
  "User":{
    "id":38710
  }
}
```

Nop请求：

```
query{
  User__get(id: "38710"){
     id,sex, name, tag, head, data, pictureList
  }
}
```

返回

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

或者使用`/r/`请求模式

```
/r/User__get?id=38710
```

返回

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

第三种方式是使用`/p/`调用

```
/p/User__get?id=38710
```

返回:

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

### 获取用户列表

APIJSON请求：

```json
{
  "[]":{
    "count":3,             //只要3个
    "User":{
      "@column":"id,name"  //只要id,name这两个字段
    }
  }
}
```

Nop请求：

```
/r/User__findList?limit=3&@selection=id,name
```

返回

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

* CrudBizModel提供了findList函数，它可以执行复杂分页查询，然后返回列表数据
* findPage函数可以执行复杂分页查询，然后返回`PageBean<T>`对象。PageBean包含总页数，当前页数，当前页数据等多个部分。
* NopGraphQL提供了满足GraphQL规范的字段选择能力，可以实现复杂嵌套结构的选择，支持字段重命名和附加参数等特性。

```
/r/User__findList?@selection=id,name,status:userStatus,
     roles:rolesList(limit:5)%7BroleId, roleName%7D
```

* `roles:rolesList(limit:5)`表示调用后台User对象的rolesList加载方法，限制返回条目数最大为5条，然后将返回的列表数据对应的属性名重命名为roles
* `{`和`}`在URL中是特殊字符，需要进行URL转义，`{`对应于`%7B`，`}`对应于`%7D`

### 获取动态及发布者用户

APIJSON请求：

```json
{
  "Moment":{
  },
  "User":{
    "id@":"Moment/userId"  //User.id = Moment.userId
  }
}
```

Nop请求：

```
/r/Moment__findFirst?@selection=...F_defaults,user
```

Nop返回:

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

* **出于安全性考虑，NopGraphQL并不支持在前台直接传递表关联条件**，因为这样很难控制数据的可访问范围，也很难控制中间数据集的大小。
* 在后端ORM模型中可以配置moment与用户关联，在Moment对象上增加user关联对象属性。缺省情况下关联对象标记了延迟加载，所以如果前端没有明确获取该属性，则不会返回到前端。
* 在前端selection定义中`...F_defaults`是使用GraphQL的fragment语法来引用所有非lazy的字段集合，在此基础上我们可以要求返回user关联对象
* findFirst会根据复杂查询条件返回第一条满足条件的记录。如果没有指定排序条件，则按照主键排序
* **如果没有在ORM层面建立关联，在XMeta元数据文件中仍然可以定义关联条件**

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

更详细的介绍，参见[Nop入门: 如何实现复杂查询]()

* 这里的`graphql:queryMethod`方法表示当前端请求user这个属性的时候，会使用findFirst方法传入`graphql:filter`条件去获取数据。
* 原则上还可以传入一些额外的查询和排序条件。这些条件和`graphql:filter`配置结合在一起，形成最终的条件。

### 获取类似微信朋友圈的动态列表

APIJSON请求：

```json5
{
  "[]":{                             //请求一个数组
    "page":0,                        //数组条件
    "count":2,
    "Moment":{                       //请求一个名为Moment的对象
      "content$":"%a%"               //对象条件，搜索content中包含a的动态
    },
    "User":{
      "id@":"/Moment/userId",        //User.id = Moment.userId  缺省引用赋值路径，从所处容器的父容器路径开始
      "@column":"id,name,head"       //指定返回字段
    },
    "Comment[]":{                    //请求一个名为Comment的数组，并去除Comment包装
      "count":2,
      "Comment":{
        "momentId@":"[]/Moment/id"   //Comment.momentId = Moment.id  完整引用赋值路径
      }
    }
  }
}
```

APIJSON返回数据：

```json
{
  "[]":[
    {
      "Moment":{
        "id":15,
        "userId":70793,
        "date":1486541171000,
        "content":"APIJSON is a JSON Transmission Structure Protocol…",
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

Nop请求:

```
/r/Moment__findList?offset=0&limit=2&filter_content__contains=a
&@selection=...F_defaults,user%7Bid,name,head%7D,comments(limit:2)
```

Nop返回结果：

```json
{
  "status": 0,
  "data":[
    {
        "id":15,
        "userId":70793,
        "date":1486541171000,
        "content":"APIJSON is a JSON Transmission Structure Protocol…",
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

* 与APIJSON相比，NopGraphQL返回的数据是标准的JSON对象结构，属性名也是很自然的嵌套属性名，而APIJSON的对象结构层次是平展模式，还使用了特殊的`Comment[]`这种特殊的格式约定，在前台可能还需要经过额外的结构转换才能传递给组件使用。
* NopGraphQL支持种多表关联查询：一对一、一对多、多对一、各种条件，只不过出于安全性考虑，关联条件需要在后台的XMeta或者ORM模型中配置。
* NopORM支持各种JOIN： LEFT JOIN,  INNER JOIN，FULL JOIN 等. 还通过Dialect支持各类SQL函数，可以跨数据库迁移
* `filter_content__contains=a`要求在后端的XMeta文件中为prop指定`allowFilterOps="in,eq,contains"`这种配置，需要开放contains查询运算才可以。

## 二. 对比传统RESTful方式

### 2.1 开发流程

| 开发流程 | 传统方式                            | APIJSON                                                   |
| ---- | ------------------------------- | --------------------------------------------------------- |
| 接口传输 | 等后端编辑接口，然后更新文档，前端再按照文档编辑请求和解析代码 | 前端按照自己的需求编辑请求和解析代码。<br />没有接口，更不需要文档！前端再也不用和后端沟通接口或文档问题了！ |
| 兼容旧版 | 后端增加新接口，用v2表示第2版接口，然后更新文档       | 什么都不用做！                                                   |

**Nop开发流程:**

* APIJSON所宣称的优点，NopGraphQL + NopORM引擎结合后都可以内置提供。
* 利用GraphQL协议可以对返回数据进行再组织，通过字段选择能力自动兼容旧版调用，无需为接口增加版本号。通过定制xmeta和orm模型可以自行扩展返回字段，而不需要编写服务端代码。
* Nop平台独有的Delta定制能力，可以使得同一个服务在不同的部署环境下提供不同的缺省返回字段，并扩展与不同数据实体的关联关系，而无需修改服务端已经打包好的代码。

### 2.2 前端请求

| 前端请求 | 传统方式                                                                                         | APIJSON                                                                                                                                                                                                                                                                                                                                                 |
| ---- | -------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 要求   | 前端按照文档在对应URL后面拼接键值对                                                                          | 前端按照自己的需求在固定URL后拼接JSON                                                                                                                                                                                                                                                                                                                                  |
| URL  | 不同的请求对应不同的URL，基本上有多少个不同的请求就得有多少个接口URL                                                        | 相同的操作方法(增删改查)都用同一个URL，<br />大部分请求都用7个通用接口URL的其中一个                                                                                                                                                                                                                                                                                                       |
| 键值对  | key=value                                                                                    | key:value                                                                                                                                                                                                                                                                                                                                               |
| 结构   | 同一个URL内table_name只能有一个 <br /><br /> base_url/get/table_name?<br />key0=value0&key1=value1... | 同一个URL后TableName可传任意数量个 <br /><br /> base_url/get/<br />{<br > &nbsp;&nbsp; TableName0:{<br > &nbsp;&nbsp;&nbsp;&nbsp; key0:value0,<br > &nbsp;&nbsp;&nbsp;&nbsp; key1:value1,<br > &nbsp;&nbsp;&nbsp;&nbsp; ...<br > &nbsp;&nbsp; },<br > &nbsp;&nbsp; TableName1:{<br > &nbsp;&nbsp;&nbsp;&nbsp; ...<br > &nbsp;&nbsp; }<br > &nbsp;&nbsp; ...<br > } |

**Nop前端请求:**

* 借助于GraphQL协议中的selection机制，Nop平台可以实现与APIJSON等价的效果，同时它返回的嵌套数据结构更加自然直观。而且可以使用标准的GraphQL第三方工具进行调试，例如quarkus框架内置集成的graphql-ui工具。

* 内置的CrudBizModel提供了一系列增删改查相关的服务函数，可以实现复杂主子表结构的增删改查。一般数据维护任务不需要编写代码或者只需要编写少量偏离增删改查逻辑的差异化逻辑。

* 除了CRUD操作之外，NopGraphQL还支持没有任何数据库表支持的业务实体对象及其方法。

* GraphQL协议内置支持一次性调用多个后台服务函数。比如

  ```graphql
  query{
      Entity1__findPage{
         items: { fld1, fld2}
      },
      Entity2__get(id:"333") { name, status}
  }
  ```

### 2.3 后端操作

| 后端操作          | 传统方式                                       | APIJSON                    |
| ------------- | ------------------------------------------ | -------------------------- |
| 解析和返回         | 取出键值对，把键值对作为条件用预设的的方式去查询数据库，最后封装JSON并返回给前端 | 把Parser#parse方法的返回值返回给前端就行 |
| 返回JSON结构的设定方式 | 由后端设定，前端不能修改                               | 由前端设定，后端不能修改               |

**Nop平台后端操作:**

* Nop平台的后端的底层采用NopORM实现，它包含完整的EQL对象查询语言，内置支持多租户、扩展字段、逻辑删除等常见业务扩展需求，整体设计比JPA+MyBatis更加完整、强大，可扩展性更好。

具体介绍参见[低代码平台需要什么样的ORM引擎?(1) ](https://mp.weixin.qq.com/s/biBdNaQV98uaxdpVKndwwg), [低代码平台需要什么样的ORM引擎?(2) ](https://mp.weixin.qq.com/s/Nv9Z23rv0ijwJ34PPH-uyw)

### 2.4 前端解析

| 前端解析 | 传统方式                  | APIJSON                              |
| ---- | --------------------- | ------------------------------------ |
| 查看方式 | 查文档或问后端，或等请求成功后看日志    | 看请求就行，所求即所得，不用查、不用问、不用等。也可以等请求成功后看日志 |
| 解析方法 | 用JSON解析器来解析JSONObject | 可以用JSONResponse解析JSONObject，或使用传统方式  |

**Nop平台前端解析:**

* Quarkus框架提供了相当完善的开发调试工具，比如graphql-ui，在线日志查看等。
* Nop平台在所有关键处都有日志，比如输入输出的JSON数据，每条SQL语句实际执行的语句、参数以及执行时间等。
* 在调试模式下，通过`/p/DevDoc__graphql`可以查看后台所有服务函数和数据对象的GraphQL定义。
* Nop平台输出日志的时候考虑到了字段的mask设置，对于用户密码、卡号等敏感数据自动执行掩码处理后再打印到日志中
* 一般返回结果都是JSON结构，可以使用JSON解析器解析。`/p/`请求可以返回其他格式的结果，实现二进制文件下载等功能。

### 2.5 前端对应不同需求的请求

Nop平台可以自由选择使用标准的GraphQL协议或者gRPC协议或者REST协议来访问后台服务对象。整体使用方式比APIJSON要更加直观、简单，并且包含APIJSON的全部能力。
除此之外，Nop平台还提供了独一无二的Delta定制能力和元编程能力。

#### 1. 获取单个用户数据

```
/r/User__get?id=38710
```

#### 2. Moment和对应的User

```
/r/Moment__findFirst?filter_userId=38710&@selection=...F_defaults,user
```

#### 3. User列表

```
/r/User__findList?offset=0&limit=3&filter_sex=0
```

#### 4. MomentL列表，每个Moment包括1.发布者User 2.前3条Comment

```
/r/Moment__findList?@selection=...F_defaults,user,comments(limit:3)
```

#### 5. User发布的Moment列表， 每个Moment包括 1.发布者User 2.前3条Comment

```
/r/Moment__findList?filter_userId=38710&@selection=...F_defaults,user,comments(limit:3)
```

### 2.6 后端对应不同请求的返回结果

Nop平台支持多种方式来调用同一个后端服务函数，从而返回不同的结果格式。无论是使用标准的GraphQL协议，还是REST协议，都可以调用到同样的服务函数，从而便于和各类前端对接。

1. 以`/r/`为前缀的请求返回`ApiResponse<T>`结构
2. 以`/p/`为前缀的请求则只返回data部分，不会将它包装为ApiResponse
3. 通过`@selection`参数可以指定返回的数据字段。没有被选择的字段后台会跳过处理，可以减少后台加载的数据量和执行的运算量。
4. 对于查询操作，内置的CrudBizModel提供了`findList/findPage/findFirst`等多种查询方法，分别返回列表数据、包含条目数的分页列表数据和第一条数据等。

关于NopGraphQL的进一步介绍，可以参见[为什么在数学的意义上GraphQL严格的优于REST？](https://mp.weixin.qq.com/s/7Ou1h7NwyI4eAX4m_Zbftw)

## 三. 设计规范

### 3.1 操作方法

1. 与GraphQL规范相同，Nop平台的服务端只使用GET和POST方法，不使用PUT、DELETE、PATCH等Http方法，从而简化前后端处理。
2. GET方法只能调用幂等的graphql query操作，而POST可以调用graphql query或者mutation操作。
3. mutation操作会自动打开数据库事务（内部实现进行了优化，如果没有实际访问数据库，则并不会真的占用数据库连接）。
4. CrudBizModel提供了`findList/findPage/findFirst/get/delete/update/save/batchDelete/batchModify`等多种增删改查操作，而且支持批量处理、支持主子表数据一次性提交。
5. 所有的findXX方法都接收filter查询条件和orderBy排序条件，支持包含`and/or`的复杂组合查询条件
6. 查询时可以直接使用`moment.user.dept`这种复合属性，在ORM层面它会被自动识别，并展开成多表关联查询。这是利用了NopORM底层的对象查询语言EQL的关联查询能力。
7. 可以通过http header来传递一些全局参数信息，比如tenantId, authToken，traceId等。
8. 每个数据库实体缺省都具有对应的服务对象，可以通过`/r/{bizObjName}__{bizAction}`这种方式来调用服务对象上的服务方法。对于一些不需要直接暴露的子表结构，可以在数据模型上标记为no-web，则不会单独为它生成服务端点。

### 3.2 功能符

Nop平台的复杂查询条件使用了QueryBean模型来表达。这是一个通用的Predicate定义模型，被自动转换为SQL查询语句或者在内存中执行的Predicate接口，并可以在XML和JSON等多种格式之间自由转换。例如表达`status=1 and type in(1,2)` 这个条件，QueryBean的XML格式表达如下所示：

```xml
<and>
  <eq name="status" value="1" />
  <in name="type" value="@:[1,2]" />
</and>
```

* `@:`是Nop平台为XML格式增加的一个扩展，它表示后面的值按照JSON格式编码。

* 也可以直接使用`<in name="type" value="1,2" />`，逗号分隔的字符串可以被自动切分得到字符串列表。

* `value="1"`在后台解析得到的值是字符串1。Nop平台在查询时会自动根据ORM中定义的字段类型进行转型，因此status会被自动转换为整数类型。如果一定要表示传入的条件值是整数，则可以使用`@:`前缀，`value="@:1"`。

* and/or可以嵌套使用，并可以用not取反。

* Nop平台内置了大量查询算符，如gt表示大于，ge表示大于等于。具体内置的op定义参见FilterOp.java中的定义。

如果使用JSON格式来表达，则对应如下内容：

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

Nop平台在`/r/User__get?id=123`这种读取单条实体的调用函数中会应用数据权限规则。此时，会自动将filter翻译为在内存中执行的Predicate接口来运行，相当于翻译为如下代码：

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

与APIJSON对比，NopGraphQL的功能符能力要更加强大，使用更加直观、简单。

#### 1. 查询选项范围

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

查询id符合38710,82001,70793中任意一个的一个User数组。

对于批量按照id获取，Nop平台还提供了一个简化的调用方法batchGet

```
/r/User__batchGet?ids=38710,82001,70793
```

#### 2. 匹配条件范围

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
        "value"： 90000
      }
    ]
  }
}
```

查询id符合`id<=80000 || id>90000`的一个User数组。

#### 3. 包含选项范围

APIJSON中 `["contactIdList<>":38710]`等价于执行SQL过滤条件`json_contains(contactIdList,38710)`。查询contactIdList包含38710的一个User数组。

但是出于安全性方面的考虑，Nop平台不支持直接执行前端传过来的SQL片段，也不支持执行前端传过来的SQL函数调用，所有的函数调用必须在后台封装为操作符或者数据字段，不直接暴露给前端。

对于上面的需求，我们引入在后台的XMeta元数据模型中引入一个转换配置。

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

* 出于安全性方面的考虑，缺省只允许通过in或者eq运算符来查询。如果要使用其他查询运算符，必须通过allowFilterOp属性进行配置。
* 可以配置`<graphql:transFilter>`来执行转换逻辑。例子中时使用`filter.xlib`标签库中的`<filter:sql>`标签函数来动态生成SQL片段。
* 如果感觉上面的配置有些繁琐，可以利用Nop平台内置的元编程机制在编译期为JSON类型的字段统一生成相应的transFilter配置。这样看起来就像是原生支持json_contains运算符。

```xml
<prop name="contactIdList" allowFilterOp="json_contains"/>
```

* 在编译期识别到json_contains运算符后可以自动生成`<graphql:transFilter>`配置。

关于复杂查询条件的配置，可以参见[Nop入门：如何实现复杂查询](https://mp.weixin.qq.com/s/5PVIrgqjlPQ549V9RZbDdg)

#### 4. 判断是否存在

APIJSON中，如下调用

```json
["id}{@":{
  "from":"Comment",
  "Comment":{
   "momentId":15
 }
}]
```

可以表示子查询过滤

```sql
 WHERE EXISTS(SELECT * FROM Comment WHERE momentId=15)
```

在Nop平台中我们同样可以采用transFilter配置

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

#### 5. 远程调用函数

在APIJSON中可以通过函数表达式来调用远程函数。例如

```json
{
  "isPraised()": "isContain(praiseUserIdList,userId)"
}
```

可以表示调用远程函数`boolean isContain(JSONObject request, String array, String value)`。

在Nop平台中通过GraphQL协议本身就支持属性函数的调用。同样的，基于安全性的考虑，某个属性具体调用哪个函数必须事先在XMeta元模型或者XBiz业务模型中声明，不能直接通过前端传递。

```xml
<!-- 在User.xmeta中增加isPraised计算属性 -->
<prop name="isPraised">
  <arg name="praiseUserIdList" type="List<String>" />

  <getter>
    const api = inject("service_isContains");
    return api.invoke({praiseUserIdList,userId:entity.id});
  </getter>
</prop>
```

* 在getter配置中可以使用entity来访问当前实体。
* 可以使用inject函数从IoC容器中获取bean，或者使用import语法导入Java类。
* 可以利用XPL模板语言的标签抽象机制类简化调用。例如

```xml
<prop name="isPraised">
   <getter>
      <api:invoke name="isContains" args="${{praiseUserIdList, userId:entityId}}" />
   </getter>
</prop>
```

#### 6. 存储过程

可以仿照上一节的做法，在getter中通过Xpl模板语言来调用存储过程。NopDao模块中的IJdbcTemplate接口具有callProc函数，可以用它来调用存储过程。

#### 7. 引用赋值

利用NopORM引擎的关联查询能力，我们可以使用`user.dept.manager`这种复合属性表达式来访问对象树中的关联属性。在Java代码以及EQL对象查询语法中，我们都可以使用同样的复合属性表达式。

例如在getter配置中，我们可以使用复合属性表达式从当前实体上获取关联属性值

```xml
<prop name="managerName">
  <getter>
    return entity.dept.manager.name
  </getter>
</prop>
```

#### 8. 子查询

使用`graphql:transFilter`配置可以实现对子查询条件的封装.

```xml
<prop name="minUser">
   <graphql:transFilter>
     <filter:sql>
       o.id in (select min(o2.userId)) from Comment o2
     </filter:sql>
   </graphql:transFilter>
</prop>
```

前台提交查询条件

```
/r/User__findFirst?filter_minUser=1
```

#### 9. 模糊搜索

Nop平台内置的QueryBean模型支持contains, startsWith, endsWith, like等字符串部分匹配运算符，可以直接在url中使用

```
/r/User__findList?filter_userName__startsWith=a
```

#### 10. 正则匹配

通过regex运算符可以表达正则匹配

#### 11. 连续范围

通过between, dateBetween等运算符可以实现SQL的between语义。

```json
{
  "$type": "between",
  "name": "date",
  "min": "2017-10-01",
  "max": "2018-10-01"
}
```

如果使用简化的filter查询参数，则对应于

```
/r/User__findList?filter_date__between=2017-10-01,2018-10-01
```

#### 12. 新建别名

GraphQL规范中内置了别名机制，可以在返回数据的时候为每个属性指定一个别名。而在ORM层面，本身底层的NopORM在映射时就可以为数据库字段选择映射到的实体属性名，而且在ORM层面也具有alias配置，利用它我们可以将关联实体表上的字段映射到当前实体上。在修改以及查询的时候，aliasu对应的字段与实体原生字段完全一样使用。

利用ORM层面的alias机制，我们可以将使用纵表保存的扩展字段映射到当前实体上成为与普通字段完全相同的属性形式，从而简化扩展字段的使用。

对于扩展字段配置，可以参见[B站视频：如何在不改库的情况下为实体增加扩展属性](https://www.bilibili.com/video/BV1wL411D7g7/)

#### 13. 增加或扩展

在APIJSON中，可以通过`"key+"`语法表示在原有基础上增加，例如`"praiseUserIdList+":[82001]`，对应SQL是`json_insert(praiseUserIdList,82001)`，添加一个点赞用户id，即这个用户点了赞

Nop平台采用标准的ORM设计，一般要求先获取数据到Java内存中，然后修改相应字段，发现存在差异后再生成update语句更新数据库。因此Nop平台中的做法并不会使用`json_insert`这种调用，而是直接映射到Java中的列表数据，然后调用java的add方法即可。

对于子表结构，可以提交子表的差量更新数据到后台，通过`_chgType=A`，`_chgType=D`，`_chgType=U`等来区分新增、修改、删除等不同的情况。

目前Nop平台没有提供类似于APIJSON的列表差量更新机制，一般类似的需求可以通过配置xbiz模型实现。如果一定要增加类似的做法，Nop平台也不会选择在key的结构上增加特殊约定，而是会采用前缀引导语法，在value的结构上动手脚。
例如，我们可以约定`@delta：+82001，-32001`表示向集合中增加82001元素，删除32001元素。与APIJSON的`key+`语法相比，这种在值的前面增加前缀的方式更加灵活，不影响对象整体结构（对象的key没有变），完全是一种局部增强。
而且，APIJSON的语法也无法表达同时增加几个元素，又减少另外几个元素这种混合型变更。

> 在value的层面增加前缀，影响的只是单个key对应的值。而如果在key的层面约定复杂结构，则影响的不是当前key，而是更上一层的父对象。在具体处理的时候也无法利用映射机制快速判断某个key是否存在，是否唯一等。归根节点是因为key的变化破坏了原有的结构。

关于前缀引导语法的详细介绍，可以参见[DSL分层语法设计及前缀引导语法](https://zhuanlan.zhihu.com/p/548314138)

#### 14. 减少或去除

在APIJSON中，可以使用`key-:`语法表示在原有基础上减少，例如 `"balance-":100.00`，对应SQL是`balance = balance - 100.00`，余额减少100.00，即花费了100元

类似于上一节的情况，Nop平台采用标准的ORM视角，基本都要求先获取数据到内存中，因此直接在实体上调用`setBalance(entity.getBalance()-100)`即可。实体上一般具有乐观锁机制，可以保证更新时不会破坏数据一致性。

对于少数需要直接在数据库中执行递增或者递减语法的情况，可以在XBiz模型中直接执行SQL语句来进行处理。

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

与上一节的处理类似，如果一定要引入类似于APIJSON的差量更新机制，可以使用`@delta:`前缀引导语法。

#### 15. 比较运算

Nop平台内置了gt, ge,eq, lt, le等比较运算符。如果要表示不等于，可以使用ne运算符。此外还提供了isEmpty, notEmpty, isNull, notNull, isBlank, notBlank等方便的空值判断。

#### 16. 逻辑运算

Nop平台内置了and、or、not等逻辑运算符，并可以自由的嵌套组合。

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

上面的查询条件表示 `(id not in [1,2] or status > 1)`

#### 17. 数组关键词, 可自定义

Nop平台内置的CrudBizModel提供了findPage和findList等多种集合查询函数。其中findPage返回PageBean对象，它提供了total, page, cursor等多种返回结果值。

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

它支持`offset/limit`这种按位置下标分页，也支持`id> :cursor limit 100`这种按值排序分页。

对于子表集合，支持类似React Relay框架的Connection分页机制，也支持QueryBean这种分页机制。另外，与APIJSON不同，Nop平台出于安全性的考虑，不允许在前台指定表关联关系，而必须在后台的xmeta或者orm模型中指定。
在后台指定还有一个好处是自动复用，就不需要在多个前端请求中重复表达数据关联关系。例如在ORM层面指定了表A和表B的关联，以及表B与表C的关联之后，`a.b.c`这种复合属性表达式可以自动推导出A到B再到C的关联关系，从而极大的减少信息重复表达。

Nop平台还支持润乾软件公司提出的一种所谓DQL(Dimentional Query Language)查询机制，可以极大的降低主子表数据的统计查询复杂度。

借助于GraphQL的DataLoader机制，我们在完全不修改PageBean类定义的情况下，可以通过增加DataLoader的方式，为后台的findPage服务调用增加额外的返回属性，具体介绍参见[Nop入门：如何扩展已有服务](https://mp.weixin.qq.com/s/H3SxiFAsqVJz0PR15tWkww)

#### 18. 对象关键词，可自定义

Nop平台并不需要像APIJSON这样，在底层语法层面引入大量特殊约定的关键词，而是使用传统的方法参数即可。

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

上面的查询条件等价于

```sql
select id,sex,name
from User
where name like '%a%' and tag like '%a%'
order by name desc, id asc
```

更复杂的查询语句可以直接在后台的xbiz模型中调用`dao.xlib`标签库中的标签实现。可以直接使用EQL对象查询语法，非常类似于SQL语言，支持各种SQL函数和复杂的嵌套子查询，比使用JSON语法要简单得多。

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

在xbiz模型中，我们还可以利用xpl模板标签来对SQL构造的过程进行进一步的抽象，简化复杂SQL的编写。EQL语法可以支持最复杂的SQL语句，而在APIJSON的设计中很多复杂SQL是难以直接表达的，特别是我们没有一种很简单的抽象手段将动态SQL的构造过程抽象到某个局部概念中。

关于复杂SQL的进一步介绍，可以参见[Nop入门：动态SQL管理](https://mp.weixin.qq.com/s/_5-CSfY5SXquknvdINemNA)

### 19. 全局关键词

Nop平台并不是专为数据库访问而设计的，CrudBizModel模型仅仅是它其中提供的一个很普通的帮助类。NopGraphQL是一个通用的GraphQL引擎， 而NopORM是一个与Hibernate定位差不多的一个通用的ORM引擎。借助于这些基础引擎，结合BizModel等业务流模型，我们可以提供更加复杂、更加通用的低代码处理平台。

参见[从零开始编写的下一代逻辑编排引擎 NopTaskFlow](https://mp.weixin.qq.com/s/2mFC0nQon_l2M82tOlJVhg),[通过NopTaskFlow逻辑编排实现后台服务函数](https://mp.weixin.qq.com/s/CMBcV9Riehlf4_Ds_BmyEw)

## 总结

1. Nop平台可以通过配置实现APIJSON所提供的各项功能,且使用标准的GraphQL协议，无需太多特殊约定
2. Nop平台在安全性方面控制更加严格，支持严格的操作权限、字段权限和数据权限控制，可以同时支持小型快速原型的开发和严谨的大型项目的持久演化
3. Nop平台提供了业内独一无二的可扩展性，参见[Nop平台为什么是一个独一无二的开源软件开发平台 ](https://mp.weixin.qq.com/s/vCPpnE-VMF7GW7yCOGWKxw)
