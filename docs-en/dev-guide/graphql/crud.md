# Standard CRUD Operations

CrudBizModel provides standard CRUD operations, using NopAuthUser as an example:

## Create

```
# REST
POST /r/NopAuthUser__save?@selection=name,status

{
  "data": {
     fieldName: fieldValue
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

## Update

```
# REST
POST /r/NopAuthUser__update?@selection=name,status

{
  "data": {
     id: primary key value
     fieldName: fieldValue
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

## Save or Update

If the submitted data contains a primary key, it is considered an update; otherwise, it is a create.

```
# REST
POST /r/NopAuthUser__save_update?@selection=name,status

{
  "data": {
     id: primary key value
     fieldName: fieldValue
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

## Delete

```
# REST
POST /r/NopAuthUser__delete?@selection=name,status

{
  "id": "primary key"
}

# GraphQL
mutation{
   NopAuthUser__delete(id: "primary key"){
      name
      status
   }
}
```

## Batch Delete

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

## Batch Update

Batch update specified fields of specified records

```
# REST
POST /r/NopAuthUser__batchUpdate

{
  "ids”: [ "1","2","3"],
  "data: {
    fieldName: fieldValue
  }
}

# GraphQL
mutation{
   NopAuthUser__batchUpdate(ids: $ids, data:$data)
}
```

## Batch Create/Delete/Update

A single request includes multiple operations such as create, delete, and update

```
# REST
POST /r/NopAuthUser__batchModify

{
  "data: [
    {
      If id is included and _chgType=D, it represents deletion
      If id is included and _chgType!=D and A, it represents update
      If id is not included or _chgType=A, it represents creation
    }
  ]
}

# GraphQL
mutation{
   NopAuthUser__batchModify(data:$data)
}
```

## Read Single Record

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

## Paginated Read

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

The frontend assumes service calls suffixed with findPage, with parameters of type QueryBean and a return type of PageBean.

## Query Returning a List

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

## Query Returning the First Record

findFirst returns the first record that meets the criteria.

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

## Query Returning the List Length

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

## QueryBean Query Criteria

The filter in QueryBean supports complex nested conditions such as and/or.

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

filter corresponds to a TreeBean object on the backend. This is a general Tree structure and can be automatically converted to XML format. The conversion rules are a standard mechanism defined by the Nop platform:

1. The $type attribute corresponds to the tag name
2. $body corresponds to child nodes and node content
3. Other attributes not prefixed with $ correspond to XML node attributes
4. Values prefixed with `@:` are parsed in JSON format

```xml

<and>
    <eq name="status" value="@:1"/>
    <gt name="amount" value="@:3"/>
</and>
```

Corresponds to

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

The operators supported in filter conditions, such as eq and gt, are defined in [FilterOp.java](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-kernel/nop-core/src/main/java/io/nop/core/model/query/FilterOp.java).
Reusable operators include:

|Operator|Description|
|---|---|
|eq|equals|
|gt|greater than|
|ge|greater than or equal to|
|lt|less than|
|le|less than or equal to|
|in|in the set|
|between|between min and max|
|dateBetween|date between min and max|
|alwaysTrue|always true|
|alwaysFalse|always false|
|isEmpty|the value corresponding to name is empty|
|startsWith|string prefix is the specified value|
|endsWith|string suffix is the specified value|

## Simplified filter syntax

The backend now also supports a simplified filter concatenation syntax in REST call mode.

/r/NopAuthUser\_\_findPage?filter\_userStatus=3

```
The format of the filter field name is: filter_{propName}__{filterOp}
```

For example, `filter_userName__contains` filters the userName field using the contains operator. For the eq (equals) case, the filterOp part can be omitted. For example, filter\_userId is equivalent to `filter_userId__eq`.

## Add Many-to-Many Relations

```
POST /r/NopAuthUser__addManyToManyRelations

{
   "id": "current entity id",
   "propName": "roleMappings",
   "relValues" : ["1","2"]
}
```

The id parameter specifies the current entity, propName specifies the name of the many-to-many association collection property to operate on, and relValues correspond to the associated property values in the many-to-many association table.

## Update Many-to-Many Relations

The difference between this function and addManyToManyRelations is that it automatically deletes associated objects not included in the relValues collection.

```
POST /r/NopAuthUser__updateManyToManyRelations

{
   "id": "current entity id",
   "propName": "roleMappings",
   "relValues" : ["1","2"]
}
```

The id parameter specifies the current entity, propName specifies the name of the many-to-many association collection property to operate on, and relValues correspond to the associated property values in the many-to-many association table.

## Remove Many-to-Many Relations

```
POST /r/NopAuthUser__removeManyToManyRelations

{
   "id": "current entity id",
   "propName": "roleMappings",
   "relValues" : ["1","2"]
}
```

The id parameter specifies the current entity, propName specifies the name of the many-to-many association collection property to operate on, and relValues correspond to the associated property values in the many-to-many association table.

## Find a batch of entities based on criteria and update their specified properties

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

## Update master-detail tables in one go
Pass a nested JSON structure directly

```
POST /r/NopAuthUser__save

{
   "data": {
     "name": "test",
     "roles": [
       {
         "id": "1",
         "name": "admin"
       }
     ]
   }
}
```

However, to allow child table data to be updated, you must configure updatable and insertable to true for the corresponding association properties in the meta.

In CrudBizModel, OrmEntityCopier updates the entity object with data from the nested JSON structure.
<!-- SOURCE_MD5:9830898d52e177ed611c810ed5145971-->
