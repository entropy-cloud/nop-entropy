# Nop Introduction: Extending Existing Services

In the development of a productized business system, it is common to encounter the need to extend existing services within the foundation of the system. For example, you might want to extend the built-in `Login` service by adding more request parameters to the `LoginRequest`, or by extending the returned `LoginResult` object to include additional information. As a productized system, it is our goal not to modify existing code. However, popular open-source frameworks like Spring and Quarkus do not inherently provide mechanisms for extending service interfaces without altering the original service code. This makes secondary development in complex business systems particularly challenging.

Nop platform leverages reversible computation theory to implement so-called Delta customization mechanism, allowing for deep customization across multiple dimensions: data models, business logic, service interfaces, etc. Furthermore, custom code can be independently managed and stored. For instance, this document provides a brief overview of how the `Login` service is extended on the service layer using Nop platform, with detailed explanations available in [How to Implement Customization Without Modify Foundation Code](https://zhuanlan.zhihu.com/p/628770810). Example codes are referenced from [nop-delta-demo](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-demo/nop-delta-demo).

For further details, please refer to the following video: [BV1Gz421a7eU](https://www.bilibili.com/video/BV1Gz421a7eU/).

---


## Section 1: Extending Return Result Object

In the Nop platform, the `LoginApi` service object provides functions related to login/logout.

```java
@BizModel("LoginApi")
public class LoginApiBizModel implements ILoginSpi {

    @Inject
    private ILoginService loginService;

    @BizMutation("login")
    @Authority(true)
    public CompletionStage<LoginResult> loginAsync(
            @RequestBean LoginRequest request,
            IServiceContext context) {
        return loginService.loginAsync(request, context.getRequestHeaders())
                .thenApply(this::buildLoginResult);
    }
    // ...
}
```

A common extension requirement is to return additional business-related information after a login operation. Traditional Web frameworks require modifying the service function's return type, which in turn necessitates changes to the original service code. However, Nop platform's Web layer utilizes the built-in NopGraphQL engine, enabling result expansion through its inherent property loading mechanism without altering the `login` function.

---


### Section 2: Adding Extended Fields with @BizLoader

To add custom fields to the return result without modifying the base service class, you can utilize the `@BizLoader` annotation.

```java
@BizModel("LoginApi")
public class LoginApiBizModelDelta {
    @BizLoader(autoCreateField = true, forType = LoginResult.class)
    @LazyLoad
    public String location(
            @ContextSource LoginResult result,
            IServiceContext context) {
        return "loc:" + result.getUserInfo().getUserId();
    }
}
```

* You can create a new `LoginApiBizModelDelta` class instead of extending the original `LoginApiBizModel` class.
* The `@BizLoader` annotation is used to indicate that an additional field (`location`) is being added at the GraphQL level. This field does not need to exist in the original `LoginResult` class.
* Setting `autoCreateField = true` ensures that the `location` field is automatically exposed in the `LoginResult` type.
* The `@LazyLoad` annotation indicates that this field will be loaded only when explicitly requested via a REST endpoint, ensuring backward compatibility with the default implementation.

---


### Section 3: Registering Service Extensions

This approach allows for service layer extension without modifying the original code or service interface. By introducing a custom loader function, you can dynamically extend the return structure while maintaining compatibility with the platform's built-in version.

**Note:** The key advantage here lies in the fact that no changes are made to the `LoginApiBizModel` class or its `login` method. Instead, a separate loader is added to extend the result structure purely through configuration.

---




If we want to modify the input parameters of a service function, we need to create a new service function and disable the old one. In the Nop platform, exposed service function names are not allowed to be duplicated. If multiple functions have the same name, the function with the highest priority (smallest value) will be chosen. If multiple functions have the same priority, an exception will be thrown.

```java
@BizModel("LoginApi")
public class LoginApiBizModelDelta {
    @Inject
    LoginApiBizModel loginApiBizModel;

    @BizMutation("login")
    @Auth(publicAccess = true)
    @Priority(NORMAL_PRIORITY - 100)
    public CompletionStage<LoginResult> loginAsync(
        @RequestBean LoginRequestEx request, IServiceContext context) {
        request.setAttr("a", "123");
        return loginApiBizModel.loginAsync(request, context);
    }
}
```

* The `@Inject` annotation can be used to import existing implementations and enhance them. The Nop platform provides base classes like `CrudBizModel` that offer various helper functions. These can generally be combined through composition rather than inheritance. For example, see [Reverse Engineering the Backend Service's Scalable Design](https://zhuanlan.zhihu.com/p/696846283).

* In the Nop platform, multiple `BizModel` objects with the same `bizObjName` will be stacked together and merged based on their priority. The result is exposed as a complete business object. This process can also be viewed as a way to construct the root object in a Domain-Driven Design (DDD) context.


## Through XBiz Modeling for Extension

The Nop platform supports a smooth transition from high-code (ProCode) to low-code (LowCode) and even no-code (NoCode) development. In addition to implementing `BizModel` objects in Java, the Nop platform also provides configuration-based functionality for service functions. In the XBiz model file, you can add action definitions with the highest priority, which will override the same-named service functions in Java. The XBiz model file is entirely in XML format and must comply with `xbiz.xdef` specifications. If you want to enable visual design for the XBiz model, you can do so by providing a visualization designer.

Every business object corresponds to an XBiz model file in the Nop platform.

```xml
<!-- /_delta/default/nop/auth/model/LoginApi/LoginApi.xbiz -->
<biz x:schema="/nop/schema/biz/xbiz.xdef" xmlns:x="/nop/schema/xdsl.xdef"
     x:extends="super" xmlns:bo="bo" xmlns:c="c">

    <actions>
        <query name="myMethod">
            <arg name="msg" type="String" optional="true" />
            <return type="String" />

            <source>
                return "hello:" + msg;
            </source>
        </query>
    </actions>
</biz>
```

* The path of the xbiz file is `{moduleId}/model/{bizObjName}/{bizObjName}.xbiz`
* The method `myMethod` in `LoginApi` can be called using `/r/LoginApi__myMethod?msg=aaa`

## Four. Header as an Extension Channel

Nop uses a `data + ext_data` pairing design throughout, ensuring that extended information can be added at any point. At the service level, both request and response messages include additional headers. In service functions, header data can be accessed via `IServiceContext`.

```java
@BizModel("LoginApi")
public class LoginApiBizModel implements ILoginSpi {

    @BizMutation("login")
    @Auth(publicAccess = true)
    public CompletionStage<LoginResult> loginAsync(
            @RequestBean LoginRequest request, IServiceContext context) {
        String header = (String) context.getRequestHeader("nop-tenant");
        context.setResponseHeader("x-xxx", value);
        // ... 
    }
}
```

* Headers can be considered as a cross-system and cross-service function extension channel. Some generic extended data that spans multiple service functions can be transmitted via headers.

* NopGraphQL employs a minimal information expression design. Its headers can map to different underlying implementation mechanisms depending on the runtime environment. For example:
  - When running on a gRPC server, headers map to gRPC message headers.
  - When running on a REST server, headers map to HTTP protocol headers.
  - When running on Kafka, headers map to Kafka message headers.

For detailed information, please refer to [Business Development Freedom: How to Break Free from Framework Constraints and Achieve True Framework Neutrality](https://zhuanlan.zhihu.com/p/682910525)

