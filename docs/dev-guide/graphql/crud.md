# 标准增删改查操作

CrudBizModel提供了标准的增删改查操作，以NopAuthUser为例：

## 新增

````
# REST 
POST /r/NopAuthUser__save?@selection=name,status

{
  "data": {
     字段名:字段值
  }
}

# GraphQL
mutation{
   NopAuthUser__save(data: Map){
      name
      status
   }
}
````

## 修改

````
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
   NopAuthUser__update(data: Map){
      name
      status
   }
}
````

## 保存或者修改

如果提交的data中包含主键，则认为是修改，否则是新建。

````
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
   NopAuthUser__save_update(data: Map){
      name
      status
   }
}
````

## 删除

````
# REST 
POST /r/NopAuthUser__delete?@selection=name,status

{
  "id”: 主键
}

# GraphQL
mutation{
   NopAuthUser__delete(id: 主键){
      name
      status
   }
}
````

## 批量删除

````
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
````

## 批量更新
批量更新指定记录的指定字段

````
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
````

## 批量增删改
一次请求包含增加、删除、修改等多个操作

````
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
````


## 单条读取

````
# REST 
GET /r/NopAuthUser__get?id=xxx@selection=name,status

# GraphQL
query{
   NopAuthUser__get(id:"xxx"){
      name
      status
   }
}
````

## 分页读取

````
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
````

前台假定了以findPage为后缀的服务调用，参数都是QueryBean类型，返回的类型都是PageBean

## 查询返回列表

````
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
````

## 查询返回第一条

findFirst返回满足条件的第一条记录

````
# REST 
POST /r/NopAuthUser__findFirst?@selection=name,status

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
   NopAuthUser__findFirst(query:$query){
        name,
        status
   }
}
````

