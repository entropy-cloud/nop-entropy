# Nop入门：极简服务层开发

Nop平台的后端服务采用NopGraphQL引擎来实现，它的设计相比于SpringMVC这种传统的Web框架要更加精炼、通用，仅包含**数学层面上最小化的假定**，通过类似数学的自动推理机制可以实现SpringMVC所无法达到的高度可组合性和可复用性。

NopGraphQL引擎的实现原理可以参考文章： [为什么在数学的意义上GraphQL严格的优于REST？](https://zhuanlan.zhihu.com/p/678597287)和[低代码平台中的GraphQL引擎](https://zhuanlan.zhihu.com/p/589565334)

讲解视频：https://www.bilibili.com/video/BV1EC4y1k7s2/

以下介绍如何集成spring框架，并实现一个最简单的后台服务函数。

## 一. 引入nop-spring-web-starter依赖

一般情况下可以选择将pom文件的parent设置为nop-entropy模块，从而自动引入缺省的maven配置

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

如果需要支持orm，则需要引入nop-spring-web-orm-starter。

## 二. 实现BizModel

NopGraphQL中的BizModel类似于SpringMVC中的Controller，只是它的特殊假定更少，是一种数学意义上的最小化定义。

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

1. 在类上增加`@BizModel`注解，指定后台的服务对象名
2. 在服务函数上增加`@BizQuery`或者`@BizMutation`注解，分别表示无副作用的查询操作和有副作用的修改操作。`@BizMutation`会指示NopGraphQL引擎自动打开数据库事务，确保后台服务函数在事务环境中执行。
3. 通过`@Name`注解指定服务函数的参数名。如果参数类型是JavaBean，则会自动执行JSON解析将前台参数转换为对应类型

与SpringMVC的Controller对比，NopGraphQL会自动推定很多事情，从而极大降低的系统的不确定性：

1. 前端的REST链接根据对象名和方法名自动推定，而无需手工指定，固定格式为 `/r/{bizObjName}__{bizMethod}`。**注意是两个下划线**。
2. `@BizQuery`允许通过GET和POST两种HTTP方法调用，而`@BizMutation`只允许通过POST方法调用
3. 通过GET方式调用时，可以通过URL链接来传递参数，例如`/r/Demo__hello?message=abc`。通过POST方式调用时可以通过URL来传参，也可以通过Http的body使用JSON格式传递。
4. 可以通过`@Name`来一个个的指定前台参数，也可以通过`@RequestBean`将前台传递过来的所有参数包装为指定的JavaBean类型
5. 服务函数总是返回POJO对象，并指定按照JSON格式进行编码

> 如果参数是可选的，可以使用@io.nop.api.core.annotations.core.Optional注解来标记，否则框架会自动校验参数不能为空。

与springboot不同，Nop平台并不会通过类扫描来发现bean，所以需要在模块的beans目录下增加ioc配置文件。

```xml
<!-- _vfs/nop/demo/beans/app-simple-demo.beans.xml -->
<beans x:schema="/nop/schema/beans.xdef" xmlns:x="/nop/schema/xdsl.xdef">

    <bean id="DemoBizModel" class="io.nop.demo.biz.DemoBizModel"/>
</beans>
```

`_vfs/nop/demo`目录下存在一个空文件`_module`，它标识`nop/demo`是一个Nop模块。Nop平台启动时会自动加载所有模块的beans目录下以`app-`为前缀的beans.xml
配置文件。注意，并不是beans目录下的配置文件都会被加载，只有哪些以`app-`为前缀的beans.xml文件才会被自动加载。

## 三. 通用错误处理

NopGraphQL返回到前台的结果对象永远是`ApiResponse<T>`类型, 但是后台服务函数实现时并不需要像SpringMVC那样手工进行包装。

```java
class ApiResponse<T>{
    int status;
    String code;
    String msg;
    T data;
}
```

* `status=0`的时候表示执行成功，不为0表示执行失败
* 失败时通过code来传递错误码，通过msg来传递国际化后的错误消息。
* 执行成功的时候通过data来返回结果数据，也就是后台服务函数中的返回值。
* Nop平台的这种标准返回格式与前端AMIS框架所需要的服务返回格式一致。

后端需要把错误信息返回到前台时，直接抛出异常即可

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

错误码的定义和使用参见[error-code.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/dev-guide/error-code.md)

## 四. 框架中立

SpringMVC这种框架本质上是一个Web框架，很多人在使用时都会不自觉的引入对特定Web运行时的依赖，比如使用HttpServletRequest和HttpServletResponse对象等。

NopGraphQL强调框架的技术中立性，它仅表达业务逻辑，不依赖任何特定的运行时。即使是下载文件功能，也是通过WebContentBean这个POJO对象来返回结果，而不是使用HttpServletResponse中获取到的输出流。

NopGraphQL仅使用了`/graphql`和`/r/{operationName}`等少数几个Http端点，可以很容易的运行在任何Web运行时环境中，甚至可以自己使用Netty编写一个简单的HttpServer即可，
不需要任何复杂的Web标准支持。目前NopGraphQL与Spring框架集成时底层是使用SpringMVC实现URL路由，而与Quarkus框架集成时是使用JAXRS标准注解。

有些人可能对GraphQL感到陌生，对于将整个前端迁移到GraphQL的调用模式上存在疑虑。NopGraphQL所强调的信息中立表达的概念，为这种问题提供了一个完美的解决方案：
我们通过代码表达的应该是某种技术中立的业务信息，框架可以根据这些技术中立的信息自动推导得到各种技术相关的接口形式。目前，基于NopGraphQL框架实现的业务函数，
可以自动发布为REST服务、GraphQL服务、Grpc服务、消息队列服务、批处理服务等。比如说：

1. 我们可以通过`/r/Demo__hello?message=abc`这种REST接口形式调用DemoBizModel类中的hello方法
2. 也可以通过`query{ Demo__hello(message:'abc') }` 这种GraphQL请求来访问同一个服务函数
3. 如果引入了`nop-rpc-grpc`模块依赖，NopGraphQL引擎启动的时候还会自动生成如下proto服务定义，使得我们可以通过grpc来访问这个服务函数
4. 使用`/r/{bizObjName}_{bizAction}`来调用服务函数时，服务端的返回类型是固定的ApiResponse。如果希望直接返回原始类型，而不用ApiResponse包裹，可以使用`/p/{bizObjName}_{bizAction}`调用。

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

## 五. 查看服务定义

设置了nop.debug=true的时候，Nop平台会以调试模式启动。此时可以访问如下链接来获取所有服务定义：

1. `/p/DevDoc__graphql`
2. `/p/DevDoc__beans`
