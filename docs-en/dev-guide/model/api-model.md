# Api Model

Through the Excel model, define the exposed service interfaces. For specific examples, refer to [nop-wf.api.xlsx](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-wf/model/nop-wf.api.xlsx).

In the xxx-codegen module, you can add code generation scripts to generate code based on the Excel model. For example, in gen-orm.xgen:

```xml
<c:script>
// Generate service messages and interface classes based on the API model
codeGenerator.withTargetDir("../").renderModel('../../model/nop-wf.api.xlsx', '/nop/templates/api', '/', $scope);
</c:script>
```


## Configuration Explanation

In the [Configuration] section, you can configure the following variables:

1. **apiName**: Prefix for all submodules, e.g., nop-wf
2. **apiModuleName**: Determine where generated interface definitions and message classes are placed in modules, typically xxx-api
3. **serviceModuleName**: Determine where default implementation classes are placed in modules, typically xxx-service
4. **metaModuleName**: Determine where corresponding meta files are placed for messages, typically xxx-meta
5. **servicePackageName**: Package name where service implementations are located
6. **apiPackageName**: Package name where the API module is located


## Service Definition

In the [Service Definitions] section, define all exposed services of the current module:

1. **Change Type**: Configure whether a service method corresponds to a mutation or query in GraphQL
2. **Tag**: Add tags to service methods for extension. The `sync` tag indicates synchronous execution on the backend. If no `sync` tag is added, the method will be executed as an asynchronous CompletionStage.

