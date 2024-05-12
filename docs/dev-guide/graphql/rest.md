# REST链接

Nop平台中使用的REST链接很少，只有以下几种

* /graphql
* /r/{bizObjName}\_\_{bizMethod}
* /p/{bizObjName}\_\_{bizMethod}
* /f/download
* /f/upload

1. /r/的GET请求对应于graphql的Query，而POST请求可以调用query，也可以调用mutation
2. /p/会自动识别返回的结果是不是WebContentBean对象，如果是，则按照WebContentBean中设置的contentType来设置http的contentType，一般用于预览文件。
   例如调试模式下/p/DevDoc\_\_beans会以XML格式显示系统中所有注册的bean
3. /f/download用于下载文件

后台的实现代码对应于

1. io.nop.quarkus.web.service.QuarkusGraphQLWebService
2. io.nop.file.quarkus.web.QuarkusFileService

{bizObjName}\_\_{bizMethod}会调用到后台BizModel对象的方法上。例如`NopAuthUser__resetUserPassword`
会调用到 NopAuthUserBizModel对象的resetUserPassword方法。

## REST参数的规范化

前端可以通过`_subArgs.{propName}.filter_xxx`这种形式给子表查询函数传递参数。例如

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

* `_subArgs.`是一个特殊约定的参数前缀
* `filter_{xxx}`是Nop平台内部约定的QueryBean扁平化的构造方案
