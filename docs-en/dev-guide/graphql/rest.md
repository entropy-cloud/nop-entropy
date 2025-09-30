
# REST Links

The Nop platform uses only a few REST endpoints, namely:

* /graphql
* /r/{bizObjName}\_\_{bizMethod}
* /p/{bizObjName}\_\_{bizMethod}
* /f/download
* /f/upload

1. A GET request to /r/ corresponds to a GraphQL Query, while a POST request can invoke either a query or a mutation.
2. /p/ automatically detects whether the return value is a WebContentBean object. If so, it sets the HTTP Content-Type according to the contentType configured in the WebContentBean. This is typically used for file preview.
   For example, in debug mode, /p/DevDoc\_\_beans displays all registered beans in the system in XML format.
3. /f/download is used to download files.

The backend implementation classes are:

1. io.nop.quarkus.web.service.QuarkusGraphQLWebService
2. io.nop.file.quarkus.web.QuarkusFileService

{bizObjName}\_\_{bizMethod} will invoke the corresponding method on the backend BizModel object. For example, `NopAuthUser__resetUserPassword`
will call the resetUserPassword method of the NopAuthUserBizModel object.

## Standardization of REST Parameters

The frontend can pass parameters to child-table query functions using the form `_subArgs.{propName}.filter_xxx`. For example:

```
/r/NopAuthUser__findPage?@selection=userRoleMappingsConnection{items}&_subArgs.userRoleMappingsConnection.filter_status=3
```

```graphql
query{
   NopAuthUser__findPage{
      userRoleMappingsConnection(query: $subQuery){
        items
      }
   }
}

{
  subQuery: {
    filter: [
       {
          "$type":"eq",
          "name": "status",
          "value": 3
       }
    ]
  }
}
```

* `_subArgs.` is a specially reserved parameter prefix.
* `filter_{xxx}` is the Nop platform’s internal convention for a flattened construction of a QueryBean.

<!-- SOURCE_MD5:2b987f35ccfc25fed06c4ee67853186b-->
