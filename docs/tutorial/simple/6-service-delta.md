# Nop入门：扩展已有服务

在开发一个产品化的业务系统时，经常会出现需要扩展基础产品中的已有服务的情况。比如说，扩展系统内置的Login服务，为LoginRequest增加更多的请求参数，或者扩展返回的LoginResult对象，增加更多的返回信息。作为一个产品化的系统，我们肯定不希望修改已有的代码，但是目前流行的开源框架如Spring、Quarkus等并没有内置的扩展机制可以在不修改原有服务函数代码的情况下改变已有服务的接口，因此在支持复杂业务产品的二次开发时困难重重。

Nop平台基于可逆计算理论实现了所谓的Delta定制机制，在数据模型、业务逻辑、服务接口等多种层面都可以进行深度定制，并且定制代码可以独立管理和存放。本文以扩展Login服务为例简要说明Nop平台服务层的具体扩展方案，更详细的介绍参见[如何在不修改基础产品源码的情况下实现定制化开发](https://zhuanlan.zhihu.com/p/628770810)。

## 一. 扩展返回结果对象

在Nop平台中，LoginApi服务对象提供了login/logout等登录相关函数。

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

一个常见的扩展需求是login成功后返回业务相关的一些扩展信息。传统的Web框架如果要修改服务函数的返回结果类型一定要修改服务函数本身的代码。但是Nop平台的Web层使用NopGraphQL引擎，它可以利用NopGraphQL内置的属性加载器机制来实现结果扩展，而无需修改login函数。具体做法如下

### 通过`@BizLoader`引入扩展字段

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

* 我们可以新增一个LoginApiBizModelDelta类，它无需从原有的LoginApiBizModel类继承。
* 通过`@BizLoader`注解来表示这是在GraphQL服务层面增加的一个扩展字段，这个字段本身并不需要在LoginResult类上存在。`autoCreateField=true`表示根据Loader定义自动为对外暴露的LoginResult类型增加location这个字段。
* 通过`@LazyLoad`注解来表示本字段为延迟加载字段，除非前台明确请求返回该字段，否则REST请求时会忽略此字段。这样可以确保接口调用在缺省情况下兼容平台内置版本。

**注意，这里的特殊之处在于我们完全没有修改LoginApiBizModel类，也没有重载login函数本身，仅仅通过增加一个Loader函数，即可在服务层扩展返回结果的结构。**

### 注册服务扩展
Nop平台并不使用类扫描机制，因此所有的对象都需要在`beans.xml`文件中注册了之后才会起作用。如果LoginApiBizModelDelta没有从已有的LoginApiBizModel继承，则原则上任意找一个`app-*.beans.xml`文件，在其中注册bean即可。如果我们需要替换系统中已有的bean定义，则需要在delta目录下增加对应的`beans.xml`注册文件，通过`x:extends="super"`继承已有配置，然后保持bean的id，覆盖bean的class属性。

```xml
<!-- /_delta/default/nop/auth/service/beans/auth-service.beans.xml -->
<beans x:schema="/nop/schema/beans.xdef" xmlns:x="/nop/schema/xdsl.xdef"
       xmlns:ioc="ioc" x:extends="super">

   <bean id="io.nop.auth.service.biz.LoginApiBizModel" 
         class="io.nop.auth.service.biz.LoginApiBizModelEx" />

   <bean id="io.nop.demo.biz.LoginApiBizModelDelta" ioc:type="@bean:id" />
</beans>
```


## 二. 扩展输入请求对象
如果我们要改变服务函数的输入参数，则必须要重新编写一个新的服务函数并且禁用旧的服务函数。在Nop平台中，对外暴露的服务函数名不允许重名。如果多个函数具有同样的名称，则会根据 `@Priority`注解选择优先级更高的实现（值越小优先级越高）。如果多个同名函数优先级一样，则会抛出异常。

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

* 可以通过`@Inject`来引入原有的服务实现，在其基础上进行增强。Nop平台内置的CrudBizModel等基类提供了很多辅助函数，一般可以通过组合的方式进行复用，不需要从原有类继承。参见[从可逆计算看后端服务函数的可扩展设计](https://zhuanlan.zhihu.com/p/696846283)

* 在Nop平台中，多个具有同样bizObjName的BizModel对象会被堆叠在一起，按照优先级合并它们的所有函数，对外暴露为一个完整的业务对象。这个过程也可以看作是DDD中聚合根对象的一种构造过程。

## 三. 通过XBiz模型实现扩展
Nop平台支持从高代码（ProCode）到低代码(LowCode)再到无代码(NoCode)开发模式的平滑过渡，因此除了在Java中实现BizModel对象之外，Nop平台还提供了通过配置实现服务函数的能力。在XBiz模型文件中，我们可以增加action定义，它的优先级最高，会覆盖Java中的同名的服务函数。XBiz模型文件整体是XML格式，并且满足`xbiz.xdef`这个元模型的规范要求，如果给XBiz模型文件配备一个可视化设计器，就可以实现无代码开发。

Nop平台中每一个业务对象都对应有一个xbiz模型文件。
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

* xbiz文件的路径为`/{moduleId}/model/{bizObjName}/{bizObjName}.xbiz`
* 通过`/r/LoginApi__myMethod?msg=aaa`来调用LoginApi的myMethod函数

## 四. Header作为扩展信道
Nop在所有细节处都采用`data + ext_data`配对设计，确保在任何局部都可以加入扩展信息。在服务层面，所有的请求消息和响应消息都包含额外的headers集合。在服务函数中，可以通过IServiceContext读写header数据。

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

* headers可以看作是一种跨系统、跨服务函数的一个扩展信道。一些横跨多个服务函数的通用扩展数据可以通过header传递。

* NopGraphQL采用最小化信息表达设计，它的headers设计可以在不同的运行时环境中映射到不同的具体底层实现机制。比如当NopGraphQL运行在gRPC服务器之上时，headers映射为gRPC消息的headers。当NopGraphQL运行在REST服务器之上时，headers映射为HTTP协议的headers。当NopGraphQL运行在Kafka这种消息系统之上时，headers映射为Kafka消息的headers。详细介绍参见[业务开发自由之路：如何打破框架束缚，实现真正的框架中立性](https://zhuanlan.zhihu.com/p/682910525)
