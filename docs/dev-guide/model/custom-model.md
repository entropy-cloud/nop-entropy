# 自定义模型

Nop平台与所有其他公开的平台技术相比，它的一个本质性区别在于：Nop平台基于可逆计算原理实现模型的分解、合并、推导，它内部的所有组件和机制都是允许使用者
进行扩展或者定制修改的。使用者也可以利用Nop平台内置的元模型和元编程机制来开发特定于自己领域的模型。

以下以api模型的解析和代码生成为例，介绍引入自定义模型的过程。

## 1. 注册模型文件后缀名所对应的加载器

在/nop/core/registry这个虚拟路径下建立文件api.register-model.xml。

```xml
<model x:schema="/nop/schema/register-model.xdef" xmlns:x="/nop/schema/xdsl.xdef"
    name="api">
    <loaders>
        <xlsx-loader fileType="api.xlsx" impPath="/nop/graphql/imp/api.imp.xml" />
        <xdsl-loader fileType="api.xml" schemaPath="/nop/schema/api.xdef" />
    </loaders>
</model>
```

* xlsx-loader： 根据imp模型定义解析Excel文件得到Java对象，一般情况下为DynamicObject类型。在Xpl模板语言中使用时，类似于Json对象，
  但是如果试图读取模型中没有定义的属性，则会抛出异常。
* xdsl-loader: 根据xdef元模型定义解析XML文件得到Java对象，一般情况下为DynamicObject类型。

fileType比文件扩展名(fileExt)要复杂一些，它可能会取文件名按点分隔后的最后两个部分。因为我们大量使用app.orm.xml. app.orm.xlsx这样的复合文件名，
如果只取fileExt，则无法识别出专用于orm模型的XML文件以及XLSX文件。

## 2. 加载模型

Nop平台的RegisterModelCoreInitializer会在框架启动的时候自动收集所有的`/_vfs/nop/core/registry/xxx.register-model.xml`文件，把它们合并为一个完整的registry文件，
然后再解析为配置对象，注册到ResourceComponentManager中。

注册完毕后，可以通过如下方式获取模型对象

```javascript
apiModel = ResourceComponentManager.instance().loadComponentModel("/xxx/yyy.api.xlsx");
```

loadComponentModel会根据文件的fileType查找到对应的FileLoader，然后解析得到模型对象返回。

ResourceComponentManager内部提供了模型缓存，并自动跟踪模型解析过程中发现的资源文件依赖关系。例如解析 yyy.orm.xlsx的时候加载了标签库 orm-gen.xlib，则
当orm-gen.xlib发生变化或者yyy.orm.xlsx文件本身发生变化时，都会导致解析缓存失效。这一机制类似于前端的Webpack或者Vite框架所提供的源码修改监听机制。

如果需要强制刷新模型缓存，可以调用GlobalCacheRegistry对象上的方法

```
GlobalCacheRegistry.instance().clearAllCache();
```

### 3. 代码生成

Nop平台内置的代码生成工具支持渲染自定义模型，参考[gen-orm.xgen](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-wf/nop-wf-codegen/precompile/gen-orm.xgen)

```javascript
// 根据API模型生成服务消息和接口类
codeGenerator.withTargetDir("../").renderModel('../../model/nop-wf.api.xlsx','/nop/templates/api', '/',$scope);
```

renderModel(modelPath, templatePath)负责读取模型对象，然后使用模板目录下的模板文件进行渲染。

> Nop平台将resources/\_vfs下的所有文件统一组织成一个虚拟文件系统，所以无需指定模板文件属于哪个模块即可在代码生成器中使用。
