# Introduction to Nop: Minimalist Service Layer Development

The backend services of the Nop platform utilize the NopGraphQL engine, which is designed with a more refined and generic approach compared to traditional Web frameworks like SpringMVC. It incorporates only **the minimal assumptions necessary on a mathematical level** and leverages a mechanism akin to automatic reasoning to achieve a level of composability and reusability that SpringMVC cannot attain.

The implementation principles of the NopGraphQL engine can be referenced in the following articles:
- [Why is GraphQL strictly superior to REST from a mathematical perspective?](https://zhuanlan.zhihu.com/p/678597287)
- [GraphQL engines in low-code platforms](https://zhuanlan.zhihu.com/p/589565334)

For further details, you can refer to the following video:
- [Introduction to Nop and Spring Integration](https://www.bilibili.com/video/BV1EC4y1k7s2/)

The following section will guide you through integrating the Spring framework and implementing a simple backend service function.

## I. Integrating nop-spring-web-starter Dependency

Typically, you can set the parent pom of your project's pom file to the nop-entropy module, which will automatically include the default Maven configurations.

```xml
<pom>
    <parent>
        <groupId>io.github.entropy-cloud</groupId>
        <artifactId>nop-entropy</artifactId>
        <version>2.0.0-SNAPSHOT</version>
    </parent>

    <dependencies>
        <dependency>
            <groupId>io.github.entropy-cloud</groupId>
            <artifactId>nop-spring-web-starter</artifactId>
        </dependency>
    </dependencies>
</pom>
```

If you require ORM support, you will need to include the nop-spring-web-orm-starter dependency.

## II. Implementing BizModel

In NopGraphQL, the BizModel class is analogous to the Controller in SpringMVC, with the distinction that it has fewer assumptions and is defined on a mathematical level for minimalization.

```java
@BizModel("Demo")
public class DemoBizModel {
    @BizQuery
    public String hello(@Name("message") String message) {
        return "Hi," + message;
    }

    @BizMutation
    public DemoResponse testOk(@RequestBean DemoRequest request) {
        DemoResponse ret = new DemoResponse();
        ret.setName(request.getName());
        ret.setResult("ok");
        return ret;
    }
}
```

1. **Class-level Annotation**: Add the `@BizModel` annotation to specify the service object name.
2. **Method-level Annotations**:
   - Use `@BizQuery` for non-side-effecting query operations.
   - Use `@BizMutation` for mutating operations, which will automatically initiate database transactions via NopGraphQL.
3. **Parameter Annotation**: Use `@Name` to specify parameter names. If the parameter type is a JavaBean, Nop will automatically deserialize frontend parameters into the corresponding type.

Compared to SpringMVC's Controller:
- **URL Generation**: URLs for services are automatically generated based on object name and method, following the format `/r/{bizObjName}__{bizMethod}`. Note that there are two underscores.
- **HTTP Method Support**: `@BizQuery` supports both GET and POST methods, while `@BizMutation` only allows POST.
- **Parameter Transmission**: GET parameters can be passed via URL, while POST allows both URL and JSON body transmission.
- **Parameter Handling**: Parameters can be individually specified using `@Name`, or all incoming parameters can be wrapped into a `@RequestBean`.
- **Return Value**: Service methods always return POJO objects serialized as JSON.


  
> If the parameter is optional, you can use `@io.nop.api.core.annotations.core.Optional` to mark it, otherwise, the framework will automatically validate that parameters are not empty.

Unlike Spring Boot, the Nop platform does not discover beans via class scanning, so you need to add an IOC configuration file in the module's beans directory.

```xml
<!-- _vfs/nop/demo/beans/app-simple-demo.beans.xml -->
<beans x:schema="/nop/schema/beans.xdef" xmlns:x="/nop/schema/xdsl.xdef">

    <bean id="DemoBizModel" class="io.nop.demo.biz.DemoBizModel"/>
</beans>
```

There is an empty file `_module` in the `_vfs/nop/demo` directory, which indicates that `nop/demo` is a Nop module. The Nop platform will automatically load all modules' beans directories under files named `app-*.beans.xml`. Note that not all configuration files in the beans directory are loaded—only those with filenames prefixed by `app-` are automatically loaded.

## 3. General Error Handling

The results returned to the frontend by NopGraphQL are always of type `ApiResponse<T>`, but backend service functions do not need to manually wrap responses like SpringMVC does.

```java
class ApiResponse<T> {
    int status;
    String code;
    String msg;
    T data;
}
```

* `status=0` indicates successful execution; non-zero values indicate failure.
* On failure, errors are transmitted via the `code` field (error code) and `msg` field (localized error message).
* On success, results are returned via the `data` field, which corresponds to the return value of backend service functions.
* The standard response format defined by Nop matches the required format by the frontend AMIS framework.

To return error information to the frontend from the backend, simply throw an exception:

```java
@BizModel("Demo")
public class DemoBizModel {

    @BizMutation
    public DemoResponse testError(@RequestBean DemoRequest request) {
        throw new NopException(ERR_DEMO_NOT_FOUND).param(ARG_NAME, request.getName());
    }
}

@Locale("zh-CN")
public interface DemoErrors {
    String ARG_NAME = "name";

    ErrorCode ERR_DEMO_NOT_FOUND =
            define("nop.err.demo.not-found", "指定数据不存在: {name}", ARG_NAME);
}
```

For details on error code definitions and usage, refer to [error-code.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/dev-guide/error-code.md).

## 4. Framework Neutrality

SpringMVC, as a Web framework, inherently introduces dependencies on specific runtime environments when used, such as `HttpServletRequest` and `HttpServletResponse`. NopGraphQL emphasizes framework neutrality by encapsulating business logic without relying on any specific runtime environment. Even file downloads are handled via the `WebContentBean` POJO, rather than using `HttpServletResponse`.

NopGraphQL only uses a minimal set of HTTP endpoints like `/graphql` and `/r/{operationName}`, making it easy to run in any Web runtime environment, even with a simple custom HTTP server implemented using Netty. No complex Web standards are required. Currently, NopGraphQL integrates with Spring using SpringMVC for URL routing, while integrating with Quarkus uses JAXRS standard annotations.

Some may find GraphQL unfamiliar, and concerns about migrating the entire frontend to a GraphQL-based calling pattern may arise. The framework's emphasis on neutral information representation provides a perfect solution for this issue:


We should express some technology-agnostic business information through code, and the framework can automatically infer various technical-related interface forms based on these technology-agnostic pieces of information. Currently, using the NopGraphQL framework to implement business functions allows automatic publication as REST services, GraphQL services, Grpc services, message queue services, batch processing services, etc.

For example:

1. We can call the `hello` method in the `DemoBizModel` class via the REST interface using `/r/Demo__hello?message=abc`.
2. We can access the same service function via GraphQL with a query such as `query{ Demo__hello(message:'abc') }`.
3. If the `nop-rpc-grpc` module is imported, the NopGraphQL engine will automatically generate the following proto service definition upon startup, allowing us to access this service function using gRPC:
4. When calling a service function using `/r/{bizObjName}_{bizAction}`, the server's response type is fixed as ApiResponse. If you want to have the raw type returned directly without being wrapped in ApiResponse, you can use `/p/{bizObjName}_{bizAction}`.

```protobuf
syntax = "proto3";

package graphql.api;

message Demo__hello_request {
   optional string value = 1;
}

message Demo__hello_response {
  optional string value = 1;
}

service Demo {
  rpc hello(Demo__hello_request) returns (Demo__hello_response);
}
```

## Viewing Service Definitions

When `nop.debug` is set to true, the Nop platform starts in debug mode. At this point, you can access the following links to obtain all service definitions:

1. `/p/DevDoc__graphql`
2. `/p/DevDoc__beans`