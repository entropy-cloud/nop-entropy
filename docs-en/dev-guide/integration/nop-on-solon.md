# How to Integrate the Nop Platform with the Solon Framework

Solon is a lightweight Java microservice framework. For detailed information, see the official site [https://solon.noear.org/](https://solon.noear.org/). Solon starts quickly and uses very little memory, making it a potential alternative to SpringBoot. The Nop platform is a next-generation low-code platform developed from scratch based on the principles of Reversible Computation. At its core, it adopts a language-oriented programming paradigm, providing infrastructure to support the design and application of domain-specific languages (DSLs). As an upper-layer architectural platform, the Nop platform can run on various underlying technology frameworks. It has already been adapted to Quarkus and Spring; in this article, I will use Solon as an example to introduce how the Nop platform integrates with third-party frameworks.

* The concrete implementation code is in the nop-solon project [nop-extensions/nop-solon](https://gitee.com/canonical-entropy/nop-extensions/tree/master/nop-solon)

## I. Framework Initialization

The Solon framework has a built-in lightweight IoC container, and we can leverage its lifecycle management to trigger Nop platform initialization.

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

You can adapt Solon’s bean container to the Nop platform’s required IBeanContainer interface via SolonBeanContainer. This allows Nop’s bean creation process to directly use beans managed by Solon.

> Nop provides an IoC container, NopIoc, which is compatible with Spring 1.0 configuration syntax and adds a SpringBoot-like dynamic conditional matching mechanism. To be compatible with the underlying framework’s IoC container, it actually uses the underlying IoC as its parent, meaning that beans not found in NopIoc will be looked up in the parent container.

For more details about NopIoc, see [If we rewrote SpringBoot, what different choices would we make?](https://zhuanlan.zhihu.com/p/579847124)

### CoreInitialization phased initialization

The Nop platform uses the `CoreInitialization.initialize()` call to perform platform initialization. It uses Java’s ServiceLoader mechanism to load the `ICoreInitializer` interface.
Built-in common initializers execute in the following order:

1. ReflectionHelperMethodInitializer: registers extension functions with the reflection system
2. XLangCoreInitializer: registers global functions and global objects used in XLang expressions
3. XLangDebuggerInitializer: starts the XLang debugger
4. ConfigInitializer: reads configuration files such as application.yaml and loads from remote configuration services
5. VirtualFileSystemInitializer: initializes the virtual file system
6. RegisterModelCoreInitializer: loads the `register-model.xml` model registration file and registers DSL models
7. DaoDialectInitializer: scans and reads dialect model files
8. IocCoreInitializer: initializes the NopIoc container

`ICoreInitializer` provides staged loading capabilities. Each initializer has a corresponding initialization level, allowing you to explicitly specify execution up to a certain level. For example, in the code generator, we can clearly specify the execution level as `INITIALIZER_PRIORITY_PRECOMPILE`, which will not trigger NopIoc initialization.

## II. Adapting Web Services

The Nop platform uses the NopGraphQL engine to expose external Web services and RPC services. Unlike typical GraphQL engines, NopGraphQL not only implements the GraphQL invocation protocol; it also provides a general mechanism for service decomposition and composition. With a single implementation of the function code, it can automatically be exposed as REST, GraphQL, gRPC, and other service forms. NopGraphQL adopts a minimum-information expression design, making it simpler to write code with it than with other service frameworks.

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

The above service function can be called via the GraphQL protocol:

```graphql
query{
   Demo__testOk(name:"sss"){
      name,
      result
   }
}
```

It can also be called via a REST request:

```
/r/Demo__testOk?name=sss
```

And it can be called through a gRPC interface:

```proto3
service Demo{
   rpc testOk(DemoRequest)returns (DemoResponse)
}
```

For a detailed introduction to NopGraphQL, see [Why is GraphQL strictly superior to REST in the mathematical sense?](https://zhuanlan.zhihu.com/p/678597287)

NopGraphQL only requires the underlying web framework to provide route mappings for a small set of endpoints such as `/graphql` and `/r/{operationName}`. To adapt the Solon framework, we only need to implement the SolonGraphQLWebService class.

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

* The Solon framework also places variables defined in the REST path into the params collection. To avoid confusion with other parameters, SolonGraphQLWebService chooses to use the `@` prefix for distinction; thus the REST path is mapped as `/r/{@operationName}`.
* The built-in GraphQLWebService provides a basic framework to expose GraphQL services based on the JAX-RS standard. We only need to apply some custom adjustments to it.
* In its internal implementation, NopGraphQL uses generic functions such as `getRequestHeader()` and `setResponseHeader()` and does not rely on specific web framework object classes. Other input/output parameters are pure POJOs, so adaptation can be done automatically through JSON conversion.

If you use Nop platform’s security authentication and other mechanisms, you also need to adapt `HttpServerFilter`. Since the Nop platform does not directly use runtime framework-specific objects such as HttpServletRequest, but defines its own `IHttpServerContext` interface, we only need to add SolonServerContext to adapt to this interface.

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

* In the Nop platform, an implementation of IHttpServerFilter named AuthHttpServerFilter handles user access token validation.
* Similar to Spring Security, the Nop platform wraps all its filters together and executes them within a single Solon Filter.

## III. Customizing the Static Resource Loader

When managing static resources such as JavaScript, the Solon framework can enable gzip compression support. Its approach is to check whether a `js.gz` file exists in the same directory as the JavaScript file. If it exists and the browser’s accept header supports gzip, it returns the content of the `js.gz` file. In other words, when the frontend requests `app.js`, if `app.js.gz` exists, the actual content returned is from the compressed `app.js.gz` file.

For the Nop platform, Solon’s logic requires the server to provide both `js` and `js.gz` files, which significantly increases the package size. The Nop platform’s frontend uses the AMIS low-code framework, which is feature-rich. Even after compression, all frontend JavaScript code is still around 10 MB in size (the designer is over 4 MB; with on-demand loading on the frontend, the split bundle is typically around 1 MB). For intranet applications, all browsers effectively support gzip decoding, so there is no need to keep both formats. We generally keep only the `js.gz` files.

Solon allows customization of the mapping between static resource files and web request paths. We can leverage this to bypass the original logic.

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

First, we register a global mapping so that all frontend paths are processed by NopResourceRepository.

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

In NopResourceRespository, if a corresponding `js.gz` or `css.gz` file exists, we directly return the corresponding URL, thereby skipping checks on the original files.

## IV. Packaging as a Starter Module

The code for integrating the Nop platform with the Solon framework is encapsulated in the `nop-solon-starter` module. In a Solon project, you can use it simply by adding the following:

```xml
        <dependency>
            <groupId>io.github.entropy-cloud.extensions</groupId>
            <artifactId>nop-solon-starter</artifactId>
            <version>1.0-SNAPSHOT</version>
        </dependency>
```

* Currently, `nop-solon-starter` has not been uploaded to Maven Central; you need to download the project and build it yourself.

* The Nop platform packages required at build time are currently downloaded from a community-provided private Maven repository (https://nop.repo.crazydan.io/), which publishes daily builds at 2 AM.

## V. Integration Results

The Solon framework starts significantly faster than SpringBoot, and the packaged size is much smaller (roughly reduces by over 10 MB). If you do not use the AMIS frontend, the `nop-solon-service-demo` package is about 21 MB, which includes a complete low-code backend service: XLang language, GraphQL engine, ORM engine, reporting engine, workflow engine, logic orchestration engine, rules engine, distributed RPC, code generator, admin authorization management, dynamic model management, etc.

The low-code platform NopPlatform, designed based on Reversible Computation theory, is open-sourced:

- gitee: [canonical-entropy/nop-entropy](https://gitee.com/canonical-entropy/nop-entropy)
- github: [entropy-cloud/nop-entropy](https://github.com/entropy-cloud/nop-entropy)
- Development examples: [docs/tutorial/tutorial.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/tutorial/tutorial.md)
- [Principles of Reversible Computation and Nop Platform Introduction and Q&A_bilibili](https://www.bilibili.com/video/BV14u411T715/)
<!-- SOURCE_MD5:fb704f82897eb20b0aee863616814f43-->
