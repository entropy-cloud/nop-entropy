# Nop Getting Started: Extending Existing Services

When developing a productized business system, you often need to extend existing services in the base product. For example, extend the system’s built-in Login service by adding more request parameters to LoginRequest, or extend the returned LoginResult object with additional response information. As a productized system, we certainly do not want to modify existing code. However, popular open-source frameworks such as Spring and Quarkus do not provide a built-in extension mechanism that allows changing existing service interfaces without altering the original service function code. As a result, secondary development for complex business products becomes challenging.

Based on Reversible Computation theory, the Nop platform implements a so-called Delta customization mechanism that enables deep customization at multiple levels, including data models, business logic, and service interfaces, with customization code managed and stored independently. This article briefly explains the specific extension approach at the Nop platform’s service layer using the Login service as an example. For a more detailed introduction, see [How to Implement Custom Development Without Modifying the Base Product Source Code](https://zhuanlan.zhihu.com/p/628770810). Sample code is available at [nop-delta-demo](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-demo/nop-delta-demo)

Walkthrough video: https://www.bilibili.com/video/BV1Gz421a7eU/

## I. Extending the Return Result Object

In the Nop platform, the LoginApi service object provides login/logout and other login-related functions.

```java
@BizModel("LoginApi")
public class LoginApiBizModel implements ILoginSpi {

    @Inject
    ILoginService loginService;

    @BizMutation("login")
    @Auth(publicAccess = true)
    public CompletionStage<LoginResult> loginAsync(
           @RequestBean LoginRequest request, IServiceContext context) {
        return loginService.loginAsync(request, context.getRequestHeaders())
             .thenApply(this::buildLoginResult);
    }
    // ...
}    
```

A common extension requirement is to return some business-related extra information after a successful login. In traditional web frameworks, modifying the return type of a service function requires changing the function’s code itself. However, the Nop platform’s web layer uses the NopGraphQL engine, which can leverage NopGraphQL’s built-in property loader mechanism to extend results without modifying the login function. The approach is as follows.

### Introduce extended fields via `@BizLoader`

```java
@BizModel("LoginApi")
public class LoginApiBizModelDelta {
    @BizLoader(autoCreateField = true, forType = LoginResult.class)
    @LazyLoad
    public String location(@ContextSource LoginResult result,
                           IServiceContext context) {
        return "loc:" + result.getUserInfo().getUserId();
    }
}    
```

* We can add a new LoginApiBizModelDelta class, which does not need to inherit from the existing LoginApiBizModel class.
* Use the `@BizLoader` annotation to indicate this is an extended field added at the GraphQL service layer. The field itself does not need to exist on the LoginResult class. `autoCreateField=true` means the location field will be automatically added to the externally exposed LoginResult type based on the Loader definition.
* The `@LazyLoad` annotation indicates this field is lazily loaded. Unless the frontend explicitly requests it, the field will be ignored in REST requests. This ensures that, by default, interface calls remain compatible with the platform’s built-in version.

Note that the special aspect here is that we did not modify the LoginApiBizModel class at all, nor did we override the login function itself. Simply by adding a Loader function, we can extend the structure of the return result at the service layer.

### Register the service extension

The Nop platform does not use class scanning. Therefore, all objects must be registered in the `beans.xml` file to take effect. If LoginApiBizModelDelta does not inherit from the existing LoginApiBizModel, in principle you can register the bean in any `app-*.beans.xml` file. If we need to replace an existing bean definition in the system, we need to add the corresponding `beans.xml` registration file under the delta directory, inherit the existing configuration via `x:extends="super"`, then keep the bean’s id and override the bean’s class attribute.

```xml
<!-- /_delta/default/nop/auth/service/beans/auth-service.beans.xml -->
<beans x:schema="/nop/schema/beans.xdef" xmlns:x="/nop/schema/xdsl.xdef"
       xmlns:ioc="ioc" x:extends="super">

   <bean id="io.nop.auth.service.biz.LoginApiBizModel" 
         class="io.nop.auth.service.biz.LoginApiBizModelEx" />

   <bean id="io.nop.demo.biz.LoginApiBizModelDelta" ioc:type="@bean:id" />
</beans>
```

## II. Extending the Input Request Object

If we want to change a service function’s input parameters, we must implement a new service function and disable the old one. In the Nop platform, externally exposed service function names must be unique. If multiple functions have the same name, the implementation with higher priority is selected according to the `@Priority` annotation (smaller value means higher priority). If multiple same-named functions have the same priority, an exception is thrown.

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
        request.setAttr("a","123");
        return loginApiBizModel.loginAsync(request, context);
    }
}
```

* You can use `@Inject` to bring in the original service implementation and enhance it. The Nop platform’s built-in base classes such as CrudBizModel provide many helper functions. In general, you can reuse via composition without inheriting from the original class. See [Extensible Design of Backend Service Functions from the Perspective of Reversible Computation](https://zhuanlan.zhihu.com/p/696846283)

* In the Nop platform, multiple BizModel objects with the same bizObjName are stacked together, and all of their functions are merged according to priority and exposed as a single business object. This process can also be viewed as a way of constructing an aggregate root in DDD.

## III. Extending via the XBiz Model

The Nop platform supports a smooth transition from ProCode to LowCode to NoCode development modes. Therefore, in addition to implementing BizModel objects in Java, the Nop platform also provides the ability to implement service functions via configuration. In the XBiz model file, we can add action definitions, which have the highest priority and will override same-named service functions in Java. The XBiz model file is in XML format and conforms to the `xbiz.xdef` meta-model specification. With a visual designer for the XBiz model file, NoCode development can be achieved.

Every business object in the Nop platform has a corresponding xbiz model file.

```xml
<!-- /_delta/default/nop/auth/model/LoginApi/LoginApi.xbiz -->
<biz x:schema="/nop/schema/biz/xbiz.xdef" xmlns:x="/nop/schema/xdsl.xdef"
     x:extends="super" xmlns:bo="bo" xmlns:c="c">

    <actions>
        <query name="myMethod" >
            <arg name="msg" type="String" optional="true" />
            <return type="String" />

            <source>
                return "hello:" + msg;
            </source>
        </query>
    </actions>
</biz>
```

* The xbiz file path is `/{moduleId}/model/{bizObjName}/{bizObjName}.xbiz`
* Call the myMethod function of LoginApi via `/r/LoginApi__myMethod?msg=aaa`

## IV. Header as an Extension Channel

Nop uses a `data + ext_data` paired design across all details, ensuring that extension information can be added anywhere locally. At the service layer, all request and response messages contain an additional headers collection. Within a service function, you can read and write header data via IServiceContext.

```java
@BizModel("LoginApi")
public class LoginApiBizModel implements ILoginSpi {

    @BizMutation("login")
    @Auth(publicAccess = true)
    public CompletionStage<LoginResult> loginAsync(
           @RequestBean LoginRequest request, IServiceContext context) {
        String header = (String)context.getRequestHeader("nop-tenant");
        context.setResponseHeader("x-xxx",value);
        ...
    }
    // ...
}    
```

* Headers can be regarded as a cross-system, cross-service-function extension channel. Common extension data spanning multiple service functions can be passed via headers.

* NopGraphQL adopts a minimal-information-expression design. Its headers design can be mapped to different underlying implementation mechanisms in different runtime environments. For example, when NopGraphQL runs on a gRPC server, headers are mapped to gRPC message headers. When NopGraphQL runs on a REST server, headers are mapped to HTTP protocol headers. When NopGraphQL runs on a messaging system such as Kafka, headers are mapped to Kafka message headers. For a detailed introduction, see [The Road to Development Freedom: How to Break Free from Framework Constraints and Achieve True Framework Neutrality](https://zhuanlan.zhihu.com/p/682910525)
<!-- SOURCE_MD5:c515a922f4be94bae066aec0b1f069e8-->
