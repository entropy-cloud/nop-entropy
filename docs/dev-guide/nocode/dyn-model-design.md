# 动态模型设计

## 整体设计

整个设计思想如下：

```javascript
IBizObject bizObj = bizObjectManager.getBizObject(bizObjName);
bizObj.invoke(actionName, request, selection, svcCtxt);
```

1. 业务层面总是按照业务对象名来访问后台
2. BizObjectManager根据bizObjName加载IBizObject。如果在内存中没有找到，则会通过IDynamicBizModelProvider去动态加载对象模型GraphQLBizModel, 根据它创建BizObjectImpl
3. GraphQLBizModel内部会用到bizPath和metaPath，它们对应于XBiz模型文件和XMeta文件的虚拟文件路径。
4. ResourceComponentManager.loadComponentModel(modelPath) 加载XBiz和XMeta模型文件的时候会调用 IDynamicResourceProvider去获取动态IResource对象。
5. action内部需要访问数据库时会通过OrmSessionFactory获取OrmSession，此时会通过IOrmModelProvider获取OrmModel。
6. 返回的OrmModel内部使用IDynamicEntityModelProvider来根据实体名来动态加载单个实体的EntityModel。

最小的动态加载粒度是单个IResource资源文件。IDynamicResourceProvider加载的时候会根据虚拟文件路径动态决定如何生成一个InMemoryTextResource。

对于动态生成的BizObject，它实际上对应于两个资源文件，XMeta和XBiz。IDynamicBizModelProvider本质上只是根据对象名来动态确定两个路径。

每个对象属于一个模块Module。访问该模块的任何资源文件会导致自动执行该模块的动态初始化代码。这可能导致预先生成一批InMemoryTextResource。原先加载一批代码等。
比如加载模块的orm-interceptor监听器。

有些资源的实际加载粒度是模块。比如OrmModel。按照实体名去动态加载实体模型时，会动态确定它是哪个模块的。然后一次性加载该模块的OrmModel，然后把它合并到当前的OrmModel中。

所有加载都是Lazy的，也就是说如果访问了一个模块的BizObject，但是如果没有用到该模块的ORM，则实际不会去加载这个模块的实体模型。

动态加载器总是最后使用。也就是说如果在缓存中或者当前虚拟文件系统中已经存在，则返回已经存在的资源。

核心逻辑如下：
1. virtualPath => DynamicGeneratedResource
2. bizObjName => virtualPath
3. entityName => moduleName => DynamicGeneratedOrmModel
4. virtualPath => moduleName => Init Module Once

IDynamicBizModelProvider.getBizObjNames() 提供所有BizObjName名称，包含所有未加载的业务对象的名称

IDynamicModuleDiscovery.getEnabledModules() 返回所有可用的模块，但是实际访问之前并没有加载。

也就是说存在三种粒度：
1. 单个资源文件（但是解析或者生成资源文件的过程中可能会引入更多的资源文件依赖）
2. 业务对象汇总一组根据对象名确定的模型（每个模型对应一个资源文件）。
3. 一组业务对象构成一个模块，特别是它们的ORM模型是一个整体。

访问一个模块中的任何资源时都会导致先初始化模块。这个初始化过程就是自动生成一组基本的资源文件。

所有的DynamicXXXProvider都可以选择提供租户支持，根据上下文中的租户id来动态确定如何加载。并且内部会持有租户特定的缓存。

整体设计模式是Loader as Generator

 Model = Loader(virtualPath X tenantId)

在加载器中动态确定如何加载模型。
