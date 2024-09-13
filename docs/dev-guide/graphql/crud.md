# 标准增删改查操作

CrudBizModel提供了标准的增删改查操作，以NopAuthUser为例：

## 新增

```
# REST
POST /r/NopAuthUser__save?@selection=name,status

{
  "data": {
     字段名:字段值
  }
}

# GraphQL
mutation{
   NopAuthUser__save(data: $data){
      name
      status
   }
}
```

## 修改

```
# REST
POST /r/NopAuthUser__update?@selection=name,status

{
  "data": {
     id: 主键值
     字段名:字段值
  }
}

# GraphQL
mutation{
   NopAuthUser__update(data: $data){
      name
      status
   }
}
```

## 保存或者修改

如果提交的data中包含主键，则认为是修改，否则是新建。

```
# REST
POST /r/NopAuthUser__save_update?@selection=name,status

{
  "data": {
     id: 主键值
     字段名:字段值
  }
}

# GraphQL
mutation{
   NopAuthUser__save_update(data: $data){
      name
      status
   }
}
```

## 删除

```
# REST
POST /r/NopAuthUser__delete?@selection=name,status

{
  "id": "主键"
}

# GraphQL
mutation{
   NopAuthUser__delete(id: "主键"){
      name
      status
   }
}
```

## 批量删除

```
# REST
POST /r/NopAuthUser__bathDelete?@selection=name,status

{
  "ids”: [ "1","2","3"]
}

# GraphQL
mutation{
   NopAuthUser__batchDelete(ids: $ids){
      name
      status
   }
}
```

## 批量更新

批量更新指定记录的指定字段

```
# REST
POST /r/NopAuthUser__batchUpdate

{
  "ids”: [ "1","2","3"],
  "data: {
    字段名: 字段值
  }
}

# GraphQL
mutation{
   NopAuthUser__batchUpdate(ids: $ids, data:$data)
}
```

## 批量增删改

一次请求包含增加、删除、修改等多个操作

```
# REST
POST /r/NopAuthUser__batchModify

{
  "data: [
    {
      包含id且_chgType=D，则表示删除
      包含id且_chgType!=D和A，则表示更新
      不包含id或者_chgType=A，表示新建。
    }
  ]
}

# GraphQL
mutation{
   NopAuthUser__batchModify(data:$data)
}
```

## 单条读取

```
# REST
GET /r/NopAuthUser__get?id=xxx@selection=name,status

# GraphQL
query{
   NopAuthUser__get(id:"xxx"){
      name
      status
   }
}
```

## 分页读取

```
# REST
POST /r/NopAuthUser__findPage?@selection=total,items{name,status}

{
  "query": {
     "offset": 2,
     "limit": 10,

     "filter": {
     },
     "orderBy":[
     ]
  }
}

# GraphQL
query{
   NopAuthUser__findPage(query:$query){
      total,
      items {
        name,
        status
      }
   }
}
```

前台假定了以findPage为后缀的服务调用，参数都是QueryBean类型，返回的类型都是PageBean

## 查询返回列表

```
# REST
POST /r/NopAuthUser__findList?@selection=name,status

{
  "query": {

     "filter": {
     },
     "orderBy":[
     ]
  }
}

# GraphQL
query{
   NopAuthUser__findList(query:$query){
        name,
        status
   }
}
```

## 查询返回第一条

findFirst返回满足条件的第一条记录

```
# REST
POST /r/NopAuthUser__findFirst?@selection=name,status

{
  "query": {

     "filter": {
        "$type": "eq",
        "name": "status",
        "value": 1
     },
     "orderBy":[
     ]
  }
}

# GraphQL
query{
   NopAuthUser__findFirst(query:$query){
        name,
        status
   }
}
```

## 查询返回列表的长度

```
# REST
POST /r/NopAuthUser__findCount

{
  "query": {

     "filter": {
     }
  }
}

# GraphQL
query{
   NopAuthUser__findCount(query:$query)
}
```

## QueryBean查询条件

QueryBean中的filter支持and/or等复杂嵌套条件

```
POST /r/NopAuthUser__findPage

{
   "query": {
      "filter": {
          "$type": "and",
          "$body": [
            {
              "$type": "eq",
              "name": "deptName",
              "value": "a"
            }
          ]
      }
   }
}
```

filter对应于后台的TreeBean类型的对象，这是一个通用的Tree结构，并且可以自动转换为XML格式。具体转换规则是Nop平台所定义的一种标准转换机制：

1. $type属性对应于标签名
2. $body对应于子节点和节点内容
3. 不以$为前缀的其他属性对应于XML节点的属性
4. 以`@:`为前缀的值按照json格式解析

```xml

<and>
    <eq name="status" value="@:1"/>
    <gt name="amount" value="@:3"/>
</and>
```

对应于

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

过滤条件中所支持的运算符如eq,gt等，都是[FilterOp.java](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-core/src/main/java/io/nop/core/model/query/FilterOp.java)
中定义的操作符。
重用的算符有：

|操作符|说明|
|---|---|
|eq|等于|
|gt|大于|
|ge|大于等于|
|lt|小于|
|xe|小于等于|
|in|在集合中|
|between|介于min和max之间|
|dateBetween|日期在min和max之间|
|alwaysTrue|总是为真|
|alwaysFalse|总是为假|
|isEmpty|name对应的值为空|
|startsWith|字符串的前缀为指定值|
|endsWith|字符串的后缀为指定值|

## 简化filter语法

现在后台在REST调用模式下也支持直接简化的filter拼接语法

/r/NopAuthUser\_\_findPage?filter\_userStatus=3

```
过滤字段名格式为: filter_{propName}__{filterOp}
```

例如 `filter_userName__contains`表示按照contains运算符对userName字段进行过滤。对于filterOp为eq(等于条件)
的情况，可以省略filterOp的部分，例如 filter\_userId等价于`filter_userId__eq`

## 新增多对多关联

```
POST /r/NopAuthUser__addManyToManyRelations

{
   "id": "当前实体id",
   "propName": "roleMappings",
   "relValues" : ["1","2"]
}
```

id参数指定当前实体，propName参数指定要操作的多对多关联集合属性的名称，relValues对应于多对多关联表中的关联属性值。

## 更新多对多关联

这个函数与addManyToManyRelations的区别在于，它会自动删除没有在relValues集合中的关联对象。

```
POST /r/NopAuthUser__updateManyToManyRelations

{
   "id": "当前实体id",
   "propName": "roleMappings",
   "relValues" : ["1","2"]
}
```

id参数指定当前实体，propName参数指定要操作的多对多关联集合属性的名称，relValues对应于多对多关联表中的关联属性值。

## 删除多对多关联

```
POST /r/NopAuthUser__removeManyToManyRelations

{
   "id": "当前实体id",
   "propName": "roleMappings",
   "relValues" : ["1","2"]
}
```

id参数指定当前实体，propName参数指定要操作的多对多关联集合属性的名称，relValues对应于多对多关联表中的关联属性值。

## 根据条件查找到一批实体，更新它们的指定属性

```
POST /r/NopAuthUser__updateByQuery

{
  “query": {
     "$type": "eq",
     "name": "status",
     "value": 10
  },

  "data": {
     "fldA": "aaa"
  }
}
```
