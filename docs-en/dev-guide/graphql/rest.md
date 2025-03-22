# REST Links

The Nop platform uses a limited set of REST links, as outlined below:

* `/graphql`
* `/r/{bizObjName}\_\_{bizMethod}`
* `/p/{bizObjName}\_\_{bizMethod}`
* `/f/download`
* `/f/upload`

1. The `/r/` endpoint corresponds to a GET request and can be used for both `Query` and `Mutation` operations.
2. The `/p/` endpoint automatically checks if the returned result is a `WebContentBean`. If so, it sets the `contentType` based on the one defined in the bean. This is typically used for file preview functionality, especially in debug mode where `/p/DevDoc\_\_beans` displays all registered beans in XML format.
3. The `/f/download` endpoint is used to download files.

## Backend Implementation Code

The backend implementation corresponds to:

1. `io.nop.quarkus.web.service.QuarkusGraphQLWebService`
2. `io.nop.file.quarkus.web.QuarkusFileService`

The URL pattern `{bizObjName}\_\_{bizMethod}` maps to a method on the corresponding business object class, such as `NopAuthUser__resetUserPassword`, which calls the `resetUserPassword` method of the `NopAuthUserBizModel`.

## REST Parameter Normalization

Frontend parameters are normalized using `_subArgs.{propName}.filter_xxx` format for child query functions. For example:

```
/r/NopAuthUser__findPage?@selection=userRoleMappingsConnection{items}&_subArgs.userRoleMappingsConnection.filter_status=3
```

```graphql
query {
  NopAuthUser__findPage {
    userRoleMappingsConnection(query: $subQuery) {
      items
    }
  }
}

{
  subQuery: {
    filter: [
      {
        "$type": "eq",
        "name": "status",
        "value": 3
      }
    ]
  }
}
```

* `_subArgs.` is a special prefix for parameter normalization.
* `filter_{xxx}` represents Nop's internal flattening structure for QueryBean objects.
