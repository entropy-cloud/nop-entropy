  # Standard CRUD Operations
  
  The `CrudBizModel` provides standard CRUD operations using `NopAuthUser` as an example:
  
  ## Create
  
  ```rest
  POST /r/NopAuthUser__save?@selection=name,status
  
  {
    "data": {
      "name": field_value,
      "status": field_value
    }
  }
  ```
  
  ```graphql
  mutation {
    NopAuthUser__save(data: $data) {
      name
      status
    }
  }
  ```
  
  ## Update
  
  ```rest
  POST /r/NopAuthUser__update?@selection=name,status
  
  {
    "data": {
      "id": primary_key_value,
      "name": field_value,
      "status": field_value
    }
  }
  ```
  
  ```graphql
  mutation {
    NopAuthUser__update(data: $data) {
      name
      status
    }
  }
  ```
  
  ## Save or Update
  
  If the submitted data includes a primary key, it is considered an update; otherwise, it is a new record.
  
  ```rest
  POST /r/NopAuthUser__save_update?@selection=name,status
  
  {
    "data": {
      "id": primary_key_value,
      "name": field_value,
      "status": field_value
    }
  }
  ```
  
  ```graphql
  mutation {
    NopAuthUser__save_update(data: $data) {
      name
      status
    }
  }
  ```
  
  ## Delete
  
  ```rest
  POST /r/NopAuthUser__delete?@selection=name,status
  
  {
    "id": "primary_key_value"
  }
  ```
  
  ```graphql
  mutation {
    NopAuthUser__delete(id: "primary_key_value") {
      name
      status
    }
  }
  ```
  
  ## Bulk Delete
  
  ```rest
  POST /r/NopAuthUser__batchDelete?@selection=name,status
  
  {
    "ids": ["1", "2", "3"]
  }
  ```
  
  ```graphql
  mutation {
    NopAuthUser__batchDelete(ids: $ids) {
      name
      status
    }
  }
  ```
  
  ## Bulk Update
  
  Bulk update specific fields of specified records.
  
  ```rest
  POST /r/NopAuthUser__batchUpdate
  
  {
    "ids": ["1", "2", "3"],
    "data": {
      "name": field_value,
      "status": field_value
    }
  }
  ```
  
  ```graphql
  mutation {
    NopAuthUser__batchUpdate(ids: $ids, data: $data) {
      name
      status
    }
  }
  ```
  
  ## Bulk CRUD
  
  A single request can include multiple operations such as create, delete, and update.
  
  ```rest
  POST /r/NopAuthUser__batchModify
  
  {
    "data": [
      {
        "id": primary_key_value,
        "chgType": "D",
        // Indicates deletion if `chgType` is set to 'D'
      },
      {
        "id": primary_key_value,
        "chgType": "A", 
        // Indicates creation if `chgType` is not specified or set to 'A'
      }
    ]
  }
  ```
  
  ```graphql
  mutation {
    NopAuthUser__batchModify(data: $data) {
      name
      status
    }
  }
  ```
  
  ## Single Read
  
  ```rest
  GET /r/NopAuthUser__get?id=xxx@selection=name,status
  ```
  
  ```graphql
  query {
    NopAuthUser__get(id: "xxx") {
      name
      status
    }
  }
  ```
  
  ## Paged Reading
  
  ```rest
  POST /r/NopAuthUser__findPage?@selection=total,items{name,status}
  
  {
    "query": {
      "offset": 2,
      "limit": 10,
      "filter": {},
      "orderBy": []
    }
  }
  ```
  
  ```graphql
  query {
    NopAuthUser__findPage(query: $query) {
      total,
      items {
        name,
        status
      }
    }
  }


  The interface assumes that the service calls are suffixed with `findPage`, and all parameters are of type `QueryBean`. The return types are also of type `PageBean`.

## Query List Retrieval

```
# REST
POST /r/NopAuthUser__findList?@selection=name,status

{
  "query": {
    "filter": {},
    "orderBy": []
  }
}

# GraphQL
query {
   NopAuthUser__findList(query: $query) {
        name,
        status
   }
}
```

## Retrieve First Record

`findFirst` retrieves the first record that meets the conditions.

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
    "orderBy": []
  }
}

# GraphQL
query {
   NopAuthUser__findFirst(query: $query) {
        name,
        status
   }
}
```

## Retrieve List Length

``` 
# REST
POST /r/NopAuthUser__findCount

{
  "query": {
    "filter": {}
  }
}

# GraphQL
query {
   NopAuthUser__findCount(query: $query)
}
```

## QueryBean Query Conditions

The `QueryBean` filter supports complex nested conditions using `and/or`.

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

The filter corresponds to a `TreeBean` type in the backend, which is a generic Tree structure and can be automatically converted into XML format. The conversion rules are defined by Nop's standard conversion mechanism:

1. The `$type` attribute represents the tag name.
2. The `$body` attribute corresponds to child nodes and node content.
3. Other attributes not prefixed with `$` correspond to XML node attributes.
4. Values prefixed with `@:` are parsed as JSON.

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

The supported operators like `eq`, `gt` etc., are defined in `[FilterOp.java](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-core/src/main/java/io/nop/core/model/query/FilterOp.java)`. 

Reusable operators include:

| Operator | Description |
|---------|-------------|
| eq      | Equals       |
| gt      | Greater than  |
| ge     | Greater than or equal to |
| lt     | Less than    |
| xe     | Less than or equal to |
| in     | In the collection |
| between | Between min and max |
| dateBetween | Date between min and max |
| alwaysTrue | Always true |
| alwaysFalse | Always false |
| isEmpty | Name corresponds to an empty value |
| startsWith | The string starts with the specified value |
| endsWith | The string ends with the specified value |

## Simplified Filter Syntax

Now, the backend also supports a simplified filter syntax in REST call mode.

/r/NopAuthUser\_\_findPage?filter\_userStatus=3

```
The filter field format is: filter_{propName}__{filterOp}
```

For example, `filter_userName__contains` indicates filtering based on the "contains" operator for the userName field. When the filterOp is "eq" (equality condition), you can omit the filterOp part, such as `filter_userId`, which is equivalent to `filter_userId__eq`.

## Adding Multiple Relationships

```
POST /r/NopAuthUser__addManyToManyRelations

{
   "id": "current entity id",
   "propName": "roleMappings",
   "relValues" : ["1","2"]
}
```

The "id" parameter specifies the current entity, "propName" indicates the name of the multiple relationship property to operate on, and "relValues" corresponds to the associated attribute values in the multiple relationship table.

## Updating Multiple Relationships

This function differs from addManyToManyRelations in that it will automatically delete any associations not present in the relValues collection.

```
POST /r/NopAuthUser__updateManyToManyRelations

{
   "id": "current entity id",
   "propName": "roleMappings",
   "relValues" : ["1","2"]
}
```

The "id" parameter specifies the current entity, "propName" indicates the name of the multiple relationship property to operate on, and "relValues" corresponds to the associated attribute values in the multiple relationship table.

## Deleting Multiple Relationships

```
POST /r/NopAuthUser__removeManyToManyRelations

{
   "id": "current entity id",
   "propName": "roleMappings",
   "relValues" : ["1","2"]
}
```

The "id" parameter specifies the current entity, "propName" indicates the name of the multiple relationship property to operate on, and "relValues" corresponds to the associated attribute values in the multiple relationship table.

## Finding and Updating Multiple Entities Based on Conditions

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

## Bulk Updating Parent-Child Relationships

Pass nested JSON structure directly:

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

However, to allow child table data updates, the corresponding association's updatable and insertable properties must be configured as true in the meta configuration.

The CrudBizModel uses OrmEntityCopier to update nested JSON structure data into entity objects.