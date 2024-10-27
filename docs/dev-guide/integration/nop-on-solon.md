# 如何将Nop平台与Solon框架集成

Solon是一个基于Java的国产轻量级微服务框架，详细介绍参见官网[https://solon.noear.org/](https://solon.noear.org/) 。Solon的启动速度很快，占用内存很小，可以作为SpringBoot的一个替代品。Nop平台是基于可逆计算原理从零开始研发的下一代低代码平台，它的核心是采用面向语言编程范式，为领域特定语言(DSL)的设计和应用提供基础架构支撑。Nop平台是一个上层技术架构平台，它可以运行在多种底层技术框架之上，此前已经适配了Quarkus和Spring框架，在本文中我将以Solon框架为例，介绍Nop平台与第三方框架集成的具体方法。

* 具体实现代码在nop-solon工程中 [nop-extensions/nop-solon](https://gitee.com/canonical-entropy/nop-extensions/tree/master/nop-solon)

## 一. 框架初始化

Solon框架内置了一个轻量级的IoC容器，可以利用它的生命周期管理来触发Nop平台的初始化动作。

```java
@Component(index = -1)
public class SolonInitializer implements LifecycleBean {
    @Inject
    AppContext appContext;

    @Override
    public void start() throws Throwable {
        io.nop.api.core.ioc.BeanContainer.registerInstance(new SolonBeanContainer(appContext));
        CoreInitialization.initialize();
    }

    @Override
    public void stop() throws Throwable {
        CoreInitialization.destroy();
    }
}
```

可以通过SolonBeanContainer将Solon的Bean容器适配为Nop平台所需要的IBeanContainer接口。这样在Nop的Bean创建过程中就可以直接使用Solon管理的bean。

> Nop提供了一个兼容Spring 1.0配置语法的IoC容器NopIoc，并增加了类似SpringBoot的动态条件匹配机制。为了兼容底层框架的IoC容器，它实际是将底层框架提供的IoC作为自己的parent使用，即在NopIoC中查找不到的bean会在父容器中查找。

关于NopIoc的详细介绍，可以参见[如果重写SpringBoot，我们会做哪些不同的选择？](https://zhuanlan.zhihu.com/p/579847124)

### CoreInitialization分阶段初始化

Nop平台使用`CoreInitialization.initialize()`调用实现平台初始化，它的实现是使用Java的ServiceLoader机制来加载`ICoreInitializer`接口。
内置的常用初始化器按照如下顺序执行:

1. ReflectionHelperMethodInitializer: 向反射系统注册扩展函数
2. XLangCoreInitializer: 注册XLang表达式中使用的全局函数和全局对象
3. XLangDebuggerInitializer: 启动XLang调试器
4. ConfigInitializer: 读取application.yaml等配置文件，并从远程配置服务加载
5. VirtualFileSystemInitializer: 初始化虚拟文件系统
6. RegisterModelCoreInitializer: 加载`register-model.xml`模型注册文件，注册DSL模型
7. DaoDialectInitializer： 扫描读取dialect模型文件
8. IocCoreInitializer: 初始化NopIoc容器

`ICoreInitializer`提供了分阶段加载的能力，每个Initializer都具有对应的初始化级别设置，可以明确指定执行到某个初始化级别。例如，在代码生成器中，
我们可以明确指定执行级别为`INITIALIZER_PRIORITY_PRECOMPILE`，它不会触发NopIoc的初始化。

## 二. 适配Web服务

Nop平台使用NopGraphQL引擎实现对外暴露的Web服务和RPC服务。**与一般的graphql引擎不同，NopGraphQL不仅仅实现了GraphQL调用协议，它还提供了一种通用的服务分解、组合机制，只需要编写一次函数代码，即可将其自动暴露为REST服务、GraphQL服务和gRPC服务等多种服务形式**。NopGraphQL采用了最小信息表达的设计，使用它来编写代码实际上比其他服务框架都要更加简单。

```java
@BizModel("Demo")
public class DemoBizModel {
    @BizQuery
    public DemoResponse testOk(@RequestBean DemoRequest request) {
        DemoResponse ret = new DemoResponse();
        ret.setName(request.getName());
        ret.setResult("ok");
        return ret;
    }
}
```

上述服务函数可以通过GraphQL协议来调用

```graphql
query{
   Demo__testOk(name:"sss"){
      name,
      result
   }
}
```

也可以通过REST请求方式来调用

```
/r/Demo__testOk?name=sss
```

同时还可以通过gPRC接口来调用

```proto3
service Demo{
   rpc testOk(DemoRequest)returns (DemoResponse)
}
```

关于NopGraphQL的详细介绍，可以参见[为什么在数学的意义上GraphQL严格的优于REST？](https://zhuanlan.zhihu.com/p/678597287)

NopGraphQL只需要底层Web框架提供`/graphql`和`/r/{operationName}`等少数链接的路由映射即可。适配Solon框架时我们只需要实现SolonGraphQLWebService类。

```java
@Controller
public class SolonGraphQLWebService extends GraphQLWebService {
    @Mapping(path = "/graphql", method = MethodType.POST, produces = "application/json")
    public String graphqlSolon(Context context) throws IOException {
        String body = context.body();
        return FutureHelper.syncGet(runGraphQL(body, this::transformGraphQLResponse));
    }

    protected String transformGraphQLResponse(GraphQLResponseBean response, IGraphQLExecutionContext gqlContext) {
        SolonWebHelper.setResponseHeader(Context.current(), gqlContext.getResponseHeaders());
        return JsonTool.serialize(response, false);
    }

    @Mapping(path = "/r/{@operationName}", method = {MethodType.GET, MethodType.POST}, produces = "application/json")
    public String restSolon(Context context, @Path("@operationName") String operationName) throws IOException {
        String selection = getSelectionParam(context);
        String body = "GET".equalsIgnoreCase(context.method()) ? null : context.body();
        return FutureHelper.syncGet(runRest(null, operationName, () -> {
            return buildRequest(body, selection, true);
        }, this::transformRestResponse));
    }

    protected String transformRestResponse(ApiResponse<?> response, IGraphQLExecutionContext gqlContext) {
        SolonWebHelper.setResponseHeader(Context.current(), response.getHeaders());

        String str = JSON.stringify(response.cloneInstance(false));
        int status = response.getHttpStatus();
        if (status == 0)
            status = 200;

        Context.current().status(status);
        return str;
    }
    ...
}
```

* Solon框架将REST Path路径中定义的变量也统一放置到了param集合中，为了避免和其他参数混淆，SolonGraphQLWebService选择使用前缀`@`用于区分，所以REST路径映射为 `/r/{@operationName}`。
* 内置的GraphQLWebService提供了基于JAXRS标准对外暴露GraphQL服务的基本框架，我们只需要对它进行一些定制调整即可。
* NopGraphQL内部实现时只使用通用的`getRequestHeader()/setResponseHeader()`等函数，不依赖于特定的Web框架对象类，其他的输入输出参数也是纯粹的POJO对象，因此只要通过JSON转换自动进行适配即可。

如果使用Nop平台的安全认证等机制，我们还需要适配`HttpServerFilter`。因为Nop平台并没有直接使用HttpServletRequest等运行时框架特有的对象类，而是使用自己定义的`IHttpServerContext`接口，所以我们只需要增加SolonServerContext适配这个接口即可。

```java
@Component
public class SolonHttpServerFilter implements Filter {
    ...
    @Override
    public void doFilter(Context context, FilterChain chain) throws Throwable {
        List<IHttpServerFilter> serverFilters = getFilters(false);

        if (serverFilters.isEmpty()) {
            chain.doFilter(context);
        } else {
            IHttpServerContext ctx = new SolonServerContext(context);
            HttpServerHelper.runWithFilters(serverFilters, ctx, () -> {
                return FutureHelper.futureCall(() -> {
                    try {
                        chain.doFilter(context);
                        return null;
                    } catch (Error e) {
                        throw e;
                    } catch (Throwable e) {
                        throw NopException.adapt(e);
                    }
                });
            });
        }
    }
}
```

* 在Nop平台中，IHttpServerFilter的一个实现类AuthHttpServerFilter负责实现用户访问令牌检查。
* 与SpringSecurity的实现类似，Nop平台将自身所有的Filter都包装到一起，在一个Solon Filter中执行。

## 三. 定制静态资源加载器

Solon框架管理js等静态资源时可以开启gzip压缩支持，它的做法是检查是否存在与js文件同目录的`js.gz`文件，如果存在且浏览器accept支持gzip格式，则返回`js.gz`文件中的内容。也就是说前台请求`app.js`，如果存在`app.js.gz`，则实际返回的是`app.js.gz`这个压缩后的文件中的内容。

对于Nop平台来说，Solon的这个判断逻辑要求服务端同时提供`js`和`js.gz`两个文件，会显著增大服务端的包大小。Nop平台的前端使用AMIS低代码框架，功能庞大，前端所有js代码压缩后仍然有10M左右的大小（设计器有4M多，前端采用按需加载机制，一般分包为1M左右），而作为内网应用实际上所有的浏览器都具备gzip解码能力，没有必要同时保留两种格式，因此我们一般的做法是只保留`js.gz`文件。

Solon允许定制静态资源文件和Web请求路径的映射关系，我们可以利用这一点绕过它原有的判断逻辑。

```java
@Component
public class SolonStaticResourceRegistrar implements LifecycleBean {
    @Override
    public void start() throws Throwable {
        NopResourceRepository repository = new NopResourceRepository();
        //StaticMappings.add("/js/", repository);
        StaticMappings.add("/", repository);
    }
}
```

我们首先注册一个全局映射关系，要求所有前端路径都经过NopResourceRepository处理。

```java
public class NopResourceRepository implements StaticRepository {
    @Override
    public URL find(String relativePath) throws Exception {
        if (relativePath.endsWith(".js") || relativePath.endsWith(".css")) {
            URL url = loadResource(relativePath + ".gz");
            if (url != null) {
                return new URL(StringHelper.removeTail(url.toString(), ".gz"));
            }
        }
        return loadResource(relativePath);
    }

    URL loadResource(String path) throws IOException {
        String fullPath = StringHelper.appendPath("META-INF/resources/", path);
        return this.getClass().getClassLoader().getResource(fullPath);
    }
}
```

在NopResourceRespository中，我们判断如果存在对应的`js.gz`或者`css.gz`文件，则直接返回对应的URL，从而跳过对原文件的检查。

## 四. 封装为Starter模块

Nop平台集成Solon框架的代码被统一封装到`nop-solon-starter`模块中，在Solon项目中只要引入如下代码即可使用:

```xml
        <dependency>
            <groupId>io.github.entropy-cloud.extensions</groupId>
            <artifactId>nop-solon-starter</artifactId>
            <version>1.0-SNAPSHOT</version>
        </dependency>
```

* 目前`nop-solon-starter`并没有上传到maven中心仓库，需要自行下载项目编译

* 编译时依赖的Nop平台的包目前从网友提供的一个私有Maven仓库（https://nop.repo.crazydan.io/）下载，该仓库每天凌晨2点编译发布一次。

## 五. 集成效果

Solon框架的启动速度显著快于SpringBoot，打包后的大小也要小得多（大概减少10几M）。如果不使用AMIS前端，`nop-solon-service-demo`打包后大概21M，其中包含了XLang语言、GraphQL引擎、ORM引擎、报表引擎、工作流引擎、逻辑编排引擎、规则引擎、分布式RPC调用、代码生成器、后台权限管理、动态模型管理等完整的低代码后端服务。

基于可逆计算理论设计的低代码平台NopPlatform已开源：

- gitee: [canonical-entropy/nop-entropy](https://gitee.com/canonical-entropy/nop-entropy)
- github: [entropy-cloud/nop-entropy](https://github.com/entropy-cloud/nop-entropy)
- 开发示例：[docs/tutorial/tutorial.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/tutorial/tutorial.md)
- [可逆计算原理和Nop平台介绍及答疑\_哔哩哔哩\_bilibili](https://www.bilibili.com/video/BV14u411T715/)
