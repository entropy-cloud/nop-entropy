# Getting Started with Nop: Minimalist Service Layer Development

The backend services of the Nop platform are implemented using the NopGraphQL engine. Compared to traditional web frameworks like SpringMVC, its design is more concise and general, containing only mathematically minimal assumptions. Through an automatic reasoning mechanism akin to mathematics, it achieves a level of composability and reusability that SpringMVC cannot reach.

For the implementation principles of the NopGraphQL engine, see: [Why is GraphQL strictly superior to REST in the mathematical sense?](https://zhuanlan.zhihu.com/p/678597287) and [GraphQL Engine in Low-Code Platforms](https://zhuanlan.zhihu.com/p/589565334)

Tutorial video: https://www.bilibili.com/video/BV1EC4y1k7s2/

The following describes how to integrate the Spring framework and implement the simplest backend service function.

## I. Add the nop-spring-web-starter dependency

Typically, you can set the parent of the pom file to the nop-entropy module to automatically inherit the default Maven configuration:

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

If you need ORM support, include nop-spring-web-orm-starter.

## II. Implement BizModel

In NopGraphQL, a BizModel is similar to a Controller in SpringMVC, but it makes fewer special assumptions—it's a mathematically minimal definition.

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

1. Add the `@BizModel` annotation to the class to specify the backend service object name.
2. Use `@BizQuery` or `@BizMutation` on service functions to denote side-effect-free queries and side-effecting mutations, respectively. `@BizMutation` instructs the NopGraphQL engine to automatically open a database transaction, ensuring the service function executes within a transactional context.
3. Use the `@Name` annotation to specify parameter names for service functions. If a parameter is a JavaBean type, the framework will automatically parse JSON to convert the frontend parameters into the corresponding type.

Compared with SpringMVC Controllers, NopGraphQL automatically infers many things, greatly reducing system uncertainty:

1. The frontend REST endpoint is inferred from the object name and method name without manual configuration, with the fixed format `/r/{bizObjName}__{bizMethod}`. Note there are two underscores.
2. `@BizQuery` can be invoked via both GET and POST HTTP methods, whereas `@BizMutation` only allows POST.
3. When using GET, parameters can be passed via the URL, e.g., `/r/Demo__hello?message=abc`. When using POST, parameters can be passed via the URL or via JSON in the HTTP body.
4. You can specify frontend parameters one by one using `@Name`, or you can use `@RequestBean` to wrap all incoming parameters into a specified JavaBean type.
5. Service functions always return POJO objects, encoded in JSON.

> If a parameter is optional, use the @io.nop.api.core.annotations.core.Optional annotation; otherwise, the framework will automatically validate that parameters are not null.

Unlike Spring Boot, the Nop platform does not discover beans via classpath scanning, so you must add IOC configuration files under the module’s beans directory.

```xml
<!-- _vfs/nop/demo/beans/app-simple-demo.beans.xml -->
<beans x:schema="/nop/schema/beans.xdef" xmlns:x="/nop/schema/xdsl.xdef">

    <bean id="DemoBizModel" class="io.nop.demo.biz.DemoBizModel"/>
</beans>
```

There is an empty file `_module` under the `_vfs/nop/demo` directory, indicating that `nop/demo` is a Nop module. When the Nop platform starts, it automatically loads beans XML configuration files under the beans directory of all modules that are prefixed with `app-`. Note that not every configuration file under the beans directory is loaded—only those beans.xml files whose names start with `app-` are automatically loaded.

## III. Common Error Handling

NopGraphQL always returns an `ApiResponse<T>` object to the frontend, but backend service functions do not need to manually wrap results like in SpringMVC.

```java
class ApiResponse<T>{
    int status;
    String code;
    String msg;
    T data;
}
```

* `status=0` indicates success; non-zero indicates failure.
* On failure, `code` carries the error code and `msg` carries the localized error message.
* On success, `data` contains the result data—i.e., the return value of the backend service function.
* This standard response format of the Nop platform matches the service response format expected by the AMIS frontend framework.

When the backend needs to return error information to the frontend, simply throw an exception:

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

For the definition and usage of error codes, see [error-code.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/dev-guide/error-code.md)

## IV. Framework Neutrality

SpringMVC is essentially a web framework, and many users inadvertently introduce dependencies on a specific web runtime—for example, using HttpServletRequest and HttpServletResponse objects.

NopGraphQL emphasizes technical neutrality. It expresses only business logic and does not depend on any specific runtime. Even for file download functionality, results are returned via the WebContentBean POJO rather than using the output stream obtained from HttpServletResponse.

NopGraphQL uses only a few HTTP endpoints such as `/graphql` and `/r/{operationName}`, making it easy to run in any web runtime environment—you can even implement a simple HttpServer with Netty without needing support for complex web standards. Currently, when integrated with the Spring framework, NopGraphQL uses SpringMVC for URL routing, while integration with Quarkus uses JAXRS standard annotations.

Some may be unfamiliar with GraphQL and hesitant to migrate the entire frontend to the GraphQL invocation model. The concept of information-neutral expression emphasized by NopGraphQL provides a perfect solution: what we express in code should be technology-neutral business information, and based on this information the framework can automatically derive various technology-specific interface forms. At present, business functions implemented on NopGraphQL can be automatically published as REST services, GraphQL services, gRPC services, message queue services, batch services, etc. For example:

1. You can call the hello method in DemoBizModel via the REST interface `/r/Demo__hello?message=abc`.
2. You can also access the same service function via a GraphQL request: `query{ Demo__hello(message:'abc') }`.
3. If you include the `nop-rpc-grpc` module dependency, the NopGraphQL engine will automatically generate the following proto service definition at startup, allowing you to access this service function via gRPC.
4. When invoking service functions via `/r/{bizObjName}_{bizAction}`, the server's return type is fixed to ApiResponse. If you want to return the raw type directly without ApiResponse wrapping, call via `/p/{bizObjName}_{bizAction}`.

```protobuf
syntax = "proto3";

package graphql.api;

message Demo__hello_request{
   optional string value = 1;
}

message Demo__hello_response{
  optional string value = 1;
}

service Demo{
  rpc hello(Demo__hello_request) returns (Demo__hello_response);
}
```

## V. Inspect Service Definitions

When `nop.debug=true` is set, the Nop platform starts in debug mode. You can then visit the following links to retrieve all service definitions:

1. `/p/DevDoc__graphql`
2. `/p/DevDoc__beans`

<!-- SOURCE_MD5:a2c52daaa801003d281d5735395b5ce9-->
