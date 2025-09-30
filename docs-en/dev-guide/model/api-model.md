
# API Model

Define externally exposed service interfaces via an Excel model. See the example [nop-wf.api.xlsx](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-wf/model/nop-wf.api.xlsx)

In the xxx-codegen module, we can add code generation scripts to automatically generate code from the Excel model. For example, in gen-orm.xgen:

```xml
<c:script>
// Generate service messages and interface classes based on the API model
codeGenerator.withTargetDir("../").renderModel('../../model/nop-wf.api.xlsx','/nop/templates/api', '/',$scope);
</c:script>
```

## Configuration

On the 【Configuration】 page you can configure the following variables:

1. apiName: Prefix for all submodules, e.g., nop-wf
2. apiModuleName: The module into which to generate interface definitions and interface message classes, typically xxx-api
3. serviceModuleName: The module into which to generate default implementation classes, typically xxx-service
4. metaModuleName: The module into which to generate the meta files corresponding to interface messages, typically xxx-meta
5. servicePackageName: Package name where service implementation classes reside
6. apiPackageName: Package name of the API module

## Service Definitions

On the 【Service Definitions】 page you can define all services exposed by the current module.

1. 【Change】 is used to configure whether a service method corresponds to a GraphQL mutation or query.
2. In the 【Tags】 column, you can add extended annotations for service methods. The sync tag indicates that, when generating code automatically, the backend service function uses a synchronous invocation form. If the sync tag is not added, an asynchronous function call will be generated with a return type of CompletionStage.

<!-- SOURCE_MD5:82b23cd1d14439af109cf765f2b78cf8-->
