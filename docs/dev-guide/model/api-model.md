# Api模型

通过Excel模型来定义对外暴露的服务接口。具体示例参见[nop-wf.api.xlsx](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-wf/model/nop-wf.api.xlsx)

在xxx-codegen模块中我们可以增加代码生成脚本，根据Excel模型自动生成代码。例如gen-orm.xgen中

```xml
<c:script>
// 根据API模型生成服务消息和接口类
codeGenerator.withTargetDir("../").renderModel('../../model/nop-wf.api.xlsx','/nop/templates/api', '/',$scope);
</c:script>
```

## 配置说明

在【配置】页中可以配置如下变量

1. apiName: 所有子模块的前缀，例如nop-wf
2. apiModuleName：生成接口定义以及接口消息类到哪个模块中，一般为xxx-api
3. serviceModuleName: 生成缺省的实现类到哪个模块中，一般为xxx-service
4. metaModuleName: 生成接口消息所对应的meta文件到哪个模块总，一般为xxx-meta
5. servicePackageName: 服务实现类所在的包名
6. apiPackageName: api模块的包名

## 服务定义

在【服务定义】页中可以定义当前模块对外暴露的所有服务。

1. 【变更】用于配置服务方法是否对应于GraphQL中的mutation还是query
2. 【标签】列中可以为服务方法增加扩展标注。其中sync标识自动生成代码时后端服务函数是同步调用形式。如果没有加sync标签，则会生成返回值类型为CompletionStage形式的异步函数调用。
