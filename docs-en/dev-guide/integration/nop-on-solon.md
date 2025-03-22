# How to Integrate Nop Platform with Solon Framework

Solon is a lightweight, domestically produced microservices framework based on Java. For detailed information, please visit its official website at [https://solon.noear.org/](https://solon.noear.org/) . Solon has a fast startup time and occupies minimal memory, making it an alternative to Spring Boot. The Nop platform is developed from scratch based on reversible computation principles, utilizing a language-oriented programming paradigm for domain-specific languages (DSL) design and application.

The Nop platform operates as a higher-level architectural platform capable of running on multiple underlying frameworks. Previously, it has been adapted to Quarkus and Spring Frameworks. In this document, we will use Solon Framework as an example to describe the specific methods for integrating Nop with third-party frameworks.

* The specific implementation code resides in the nop-solon project at [nop-extensions/nop-solon](https://gitee.com/canonical-entropy/nop-extensions/tree/master/nop-solon).

## 1. Framework Initialization

Solon framework is equipped with a lightweight IOC container. You can leverage its lifecycle management to trigger Nop platform initialization.

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

The `SolonBeanContainer` can be adapted to Nop's required `IBeanContainer` interface. This allows direct usage of Solon-managed beans within the Nop platform.

> The Nop platform provides an IOC container compatible with Spring 1.0 configuration syntax, namely `NopIoc`, and includes dynamic condition matching similar to Spring Boot. To ensure compatibility with underlying frameworks' IOC containers, it actually uses the parent container provided by the underlying framework for beans that are not found in `NopIoc`.

For detailed information about `NopIoc`, please refer to [If we rewrite Spring Boot, what changes would we make?](https://zhuanlan.zhihu.com/p/579847124).

### CoreInitialization Initialization

The Nop platform uses `CoreInitialization.initialize()` to perform platform initialization. Its implementation leverages the Java ServiceLoader mechanism to load the `ICoreInitializer` interface.

The built-in initializers follow this order of execution:

1. **ReflectionHelperMethodInitializer**: Registers extension functions in the reflection system.
2. **XLangCoreInitializer**: Registers global functions and objects used within XLang expressions.
3. **XLangDebuggerInitializer**: Initializes the XLang debugger.
4. **ConfigInitializer**: Reads configuration files like `application.yaml` and loads them from remote configuration services.
5. **VirtualFileSystemInitializer**: Initializes the virtual file system.
6. **RegisterModelCoreInitializer**: Loads `register-model.xml` to register DSL models.
7. **DaoDialectInitializer**: Scans and registers dialect models.
8. **IocCoreInitializer**: Initializes the NopIoc container.

The `ICoreInitializer` interface provides stage-wise loading capabilities, allowing each initializer to specify a particular initialization level. For example, in the code generator, you can explicitly set the execution level to `INITIALIZER_PRIORITY_PRECOMPILE`, which will not trigger NopIoc initialization.

## 2. Web Service Adaptation


**Unlike typical GraphQL engines, NopGraphQL not only implements the GraphQL protocol but also provides a universal service decomposition and composition mechanism. With just one function written, you can automatically expose it as REST, GraphQL, or gRPC services.**

NopGraphQL employs a minimal information expression design. Writing code with it is significantly simpler compared to other service frameworks.

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

The service function can be called via the GraphQL protocol:

```graphql
query{
   Demo__testOk(name:"sss"){
      name,
      result
   }
}
```

Or via a REST request:

```
/r/Demo__testOk?name=sss
```

Additionally, it can be invoked via gRPC:

```proto3
service Demo{
   rpc testOk(DemoRequest)returns (DemoResponse)
}
```

For detailed information about NopGraphQL, please refer to [Why does GraphQL strictly surpass REST in mathematical terms?](https://zhuanlan.zhihu.com/p/678597287).

With the underlying Web framework providing only a few routes (`/graphql` and `/r/{operationName}`), NopGraphQL can be set up with minimal configuration. When adapting to the Solon framework, you only need to implement the SolonGraphQLWebService class.

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
        }), this::transformRestResponse);
    }

    protected String transformRestResponse(ApiResponse<?> response, IGraphQLExecutionContext gqlContext) {
        SolonWebHelper.setResponseHeader(Context.current(), response.getHeaders());

        String str = JSON.stringify(response.cloneInstance(false));
        int status = response.getHttpStatus();
        if (status == 0) {
            status = 200;
        }
        Context.current().status(status);
        return str;
    }
}
```

* The Solon framework also unifies variables defined in the REST Path into the `param` collection to avoid confusion with other parameters. Therefore, the REST path is mapped as `/r/{@operationName}`.
* The built-in `GraphQLWebService` provides a basic framework for exposing GraphQL services based on the JAXRS standard. Only minor customizations are needed.
* The NoOP implementation used in the transformation only relies on generic `getRequestHeader()` and `setResponseHeader()` functions, as well as pure POJOs for input/output parameters. This allows for seamless JSON conversion using a standard JSON library.

# Security Authentication Mechanisms in Nop Platform

If you are using the security authentication mechanisms in the Nop platform, you will also need to adapt `HttpServerFilter`. The Nop platform does not directly use `HttpServletRequest` and its related runtime framework classes but instead uses a custom-defined interface called `IHttpServerContext`. Therefore, adapting `SolonServerContext` to this interface is sufficient.

# Customizing Static Resource Loader

The Solon framework allows for customization of static resource loading. When enabling gzip compression support for JavaScript resources, the framework checks if a `js.gz` file exists in the same directory as the `js` file. If it does and the browser supports gzip compression, the `js.gz` file is returned instead. This means that when a request is made for `app.js`, if a `app.js.gz` file exists, the compressed version of the resource will be served.

# Challenges with Compression

In the context of the Nop platform, the Solon framework's logic requires both the original `js` and compressed `js.gz` files to be present on the server side. This significantly increases the size of the delivered package. The Nop platform's frontend uses a low-code framework, which is feature-rich, and the frontend code still amounts to around 10MB (excluding additional assets) even after compression. Since most modern browsers support gzip compression out of the box, there is no need to maintain both formats simultaneously. Therefore, it is common practice to only retain the `js.gz` files.

# Customizing Resource Mapping

The Solon framework allows for customization of resource mapping by defining your own static asset handlers. This can be particularly useful when working with internal or proprietary web frameworks that require specific handling of resources.

For example, you can create a custom resource loader class:

```java
@Component
public class SolonStaticResourceRegistrar implements LifecycleBean {
    @Override
    public void start() throws Throwable {
        NopResourceRepository repository = new NopResourceRepository();
        StaticMappings.add("/", repository);
    }
}
```

This class registers a global mapping that ensures all frontend paths are processed through the `NopResourceRepository`. This allows for consistent handling of static resources across the application.

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

## Four. Packaging as a Starter Module

The Nop platform integrates the Solon framework by encapsulating its code into the `nop-solon-starter` module. To use this module in a Solon project, simply add the following dependency:

```xml
<dependency>
    <groupId>io.github.entropy-cloud.extensions</groupId>
    <artifactId>nop-solon-starter</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

* Note: The `nop-solon-starter` has not been uploaded to the Maven central repository and must be downloaded from a private Maven repository (https://nop.repo.crazydan.io/) during compilation.

* The repository compiles and releases new versions of the Nop platform's packages every morning at 2 AM.

## Five. Integration Effects

The Solon framework starts significantly faster than SpringBoot, and the packaged size is also reduced by a significant amount (approximately 10-20 MB). If you don't use AMIS (a front-end tool), the `nop-solon-service-demo` package will be around 21 MB after compilation, including components such as XLang language support, GraphQL engine, ORM, report engine, workflow engine, logic processing engine, rule engine, distributed RPC calls, code generator, backend permissions management, dynamic model management, and more.

## Six. Open Source

The Nop platform's backend service is built on Solon, a low-code framework based on reversible computation theory. The following open-source resources are available:

- Gitee: [canonical-entropy/nop-entropy](https://gitee.com/canonical-entropy/nop-entropy)
- GitHub: [entropy-cloud/nop-entropy](https://github.com/entropy-cloud/nop-entropy)
- Documentation: [docs/tutorial/tutorial.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/tutorial/tutorial.md)
- Video tutorials on Bilibili: [可逆计算原理和Nop平台介绍及答疑\_哔哩哔哩\_bilibili](https://www.bilibili.com/video/BV14u411T715/)

