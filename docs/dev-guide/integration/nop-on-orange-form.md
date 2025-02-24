# Nop平台与橙单OrangeForm集成

Nop平台是基于可逆计算理论从零开始构建的下一代低代码开发平台，它的核心组件不依赖任何第三方库，可以和大部分第三方软件协同运行。目前，Nop平台可以运行在Quarkus和Spring框架之上，因此也兼容各类Spring衍生框架。
国产的Solon平台非常短小精悍，接口隔离也做得比较好，所以Nop平台与Solon框架也可以很容易的集成在一起。

* solon集成参见[nop-solon](https://gitee.com/canonical-entropy/nop-extensions/tree/master/nop-solon)项目。
* 若依集成参见[nop-for-ruoyi](https://gitee.com/canonical-entropy/nop-for-ruoyi)项目。

也就是说，使用Nop平台开发的代码无需任何修改，就可以在多种基础框架（Quarkus/Spring/Solon）框架上运行，使得业务代码可以摆脱基础运行环境的依赖。

[nop-for-orange-form](https://gitee.com/canonical-entropy/nop-for-orange-form)项目是Nop平台与OrangeForms开源版集成的项目，它演示了如何集成sa-tokens实现登录验证和操作权限检查。

## 一. 配置调整

### 1.1 `application.yml`文件调整

Nop平台使用classpath下的`application.yml`和`bootstrap.yml`文件来作为自己的缺省配置文件，因此可以`application.yml`
文件中直接增加Nop平台相关的配置

```yaml
nop:
  debug: true
  orm:
    init-database-schema: true
  auth:
    enable-action-auth: true
```

* nop.debug 开启调试模式。调试模式下Nop平台启动时会暴露`/r/DevDoc__graphql`等调试接口，会自动将所有用到的模型文件在执行完Delta差量合并算法后输出到
  `_dump`目录下
* nop.orm.init-database-schema 开启数据库初始化。Nop平台启动时会自动初始化数据库表结构
* nop.auth.enable-action-auth 开启操作权限检查。Nop平台会调用IActionAuthChecker接口来检查用户是否有权限访问某个服务函数。

### 1.2 POM文件调整

首先修改`OrangeFormsOpen-MybatisPlush`项目的根pom文件，将spring-boot的版本升级到3.3.3

```xml
<properties>
  <spring-boot.version>3.3.3</spring-boot.version>
  <spring-boot-admin.version>3.3.3</spring-boot-admin.version>
</properties>
```

> Nop平台目前缺省使用的SpringBoot版本为`3.3.3`，并支持JDK17以上直到JDK最新版本。目前橙单无法在JDK21以上版本运行，只能使用JDK17。

然后修改`application-webadmin`模块的`pom.xml`，在其中引入Nop平台的基础包

```xml
<pom>
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>io.github.entropy-cloud</groupId>
                <artifactId>nop-bom</artifactId>
                <version>2.0.0-SNAPSHOT</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
            <version>3.5.9</version>
        </dependency>

        <dependency>
            <groupId>io.github.entropy-cloud</groupId>
            <artifactId>nop-spring-web-orm-starter</artifactId>
        </dependency>

        <!--        <dependency>-->
        <!--            <groupId>io.github.entropy-cloud</groupId>-->
        <!--            <artifactId>nop-spring-delta</artifactId>-->
        <!--        </dependency>-->

        <dependency>
            <groupId>io.github.entropy-cloud</groupId>
            <artifactId>nop-sys-web</artifactId>
        </dependency>

        <!-- 业务组件依赖 -->
    </dependencies>
</pom>
```

* `nop-bom`的作用与spring的`spring-boot-dependencies`类似，它定义了Nop平台中所有组件的版本号，这样在引入Nop平台组件的时候就不需要指定版本号了。
* `nop-spring-web-orm-starter`的作用与`spring-boot-starter-xxx`类似，它自动引入了使用NopORM和NopGraphQL所需要的所有组件。NopORM的作用类似于JPA+MyBatis+SpringData，实现数据访问层抽象。NopGraphQL的作用类似SpringMVC+SpringGraphQL，一份代码可以同时提供REST和GraphQL两种调用方式。
* `nop-sys-web`是Nop平台中提供了序列号、编码规则等通用支持，是可选组件。
* `mybatis-plus-spring-boot3-starter`的版本需要更新到`3.5.9`，橙单内置的MyBatisPlus版本较低，无法与`3.3.3`版本以上的SpringBoot集成。

## 二. 定制数据库连接

Nop平台缺省使用自己内置的dataSource定义，与橙单集成时可以禁用Nop平台内部的数据源定义，转而使用Spring框架自动创建的DataSource。

Nop平台底层是基于可逆计算理论构建，支持完全的差量化定制。具体来说，就是在完全不修改Nop平台源码的情况下，可以通过在Delta目录下增加同名的文件来定制Nop平台中所有的逻辑。

Spring框架中如果要实现Bean的定制，需要事先在Bean的定义的时候增加`@ConditionalOnProperty`等注解，而且只能整体定制整个Bean的定义，不能说指定定制某个bean的属性的配置。
在Nop平台中，无需事前做任何特定的标注，可以使用统一的Delta定制来定制任何Bean的任何属性的配置。具体来说，就是首先确定bean在哪个模型文件中定义，然后在`_delta`目录下增加一个同名的文件，在其中就可以定制bean的定义。

以数据源定义而言，在application-webadmin项目下增加`resources/_vfs/_delta/default/nop/dao/beans/dao-defaults.beans.xml`文件，在其中增加如下内容

```xml
<beans x:schema="/nop/schema/beans.xdef" xmlns:x="/nop/schema/xdsl.xdef"
       x:extends="super" x:dump="true">
    <bean id="nopDataSource" x:override="remove"/>

    <bean id="nopHikariConfig" x:override="remove"/>

    <alias name="dataSource" alias="nopDataSource"/>

</beans>
```

* `x:extends=super`表示继承平台里原有的`dao-defaults.beans.xml`文件中的内容，本文件中的内容会和继承的内容合并在一起。合并后的结果会输出在
  `_dump`目录下。
* `x:override="remove"`表示删除继承的内容中同名的bean定义。
* `<alias name="dataSource" alias="nopDataSource"/>`
  表示将Spring框架自动创建的dataSource命名为nopDataSource，这样就可以在Nop平台中引用这个dataSource了。

## 三. 与SaToken集成

橙单使用SaToken作为登录验证和操作权限检查的框架，因此需要定制Nop平台中的操作权限检查接口。

### 3.1 使用SaToken实现IActionAuthChecker接口

首先增加SpringActionAuthChecker类，实现IActionAuthChecker接口。Nop平台内部检查操作权限使用的是这个接口。

```java
public class SpringActionAuthChecker implements IActionAuthChecker {

    @Inject
    StpInterface stpInterface;

    @Override
    public boolean isPermitted(String permission, ISecurityContext context) {
        IUserContext userContext = context.getUserContext();
        if (userContext == null)
            return false;

        String userId = userContext.getUserId();
        String loginType = "password";

        List<String> perms = stpInterface.getPermissionList(userId, loginType);

        boolean b = perms.contains(permission);
        // 假定写权限总是隐含读权限
        if (!b && permission.endsWith(":query")) {
            String prefix = StringHelper.removeTail(permission, ":query");
            b = perms.contains(prefix + ":mutation");
        }
        return b;
    }
}
```

Nop平台中缺省不使用类扫描机制，所以并不识别`@Component`等注解，而是要求所有的Bean的定义写在`beans.xml`文件中。
因此增加SpringActionAuthChecker类后，还需要在某个`beans.xml`文件中增加如下定义

```xml
<bean id="nopActionAuthChecker" class="com.orangeforms.webadmin.nop.SpringActionAuthChecker"/>
```

示例代码是加在了`resources/_delta/default/nop/dao/beans/dao-defaults.beans.xml`文件中。

### 3.2 登录成功后绑定IUserContext

Nop平台使用IUserContext接口来表示登录用户信息，因此在橙单登录成功后需要根据内部登录信息构造出一个IUserContext对象，然后将它和Nop平台的上下文绑定。

具体做法是修改AuthenticationInterceptor类，加入bindUserContext调用。

```java
public class AuthenticationInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String appCode = this.getAppCodeFromRequest(request);
        if (StrUtil.isNotBlank(appCode)) {
            return this.handleThirdPartyRequest(appCode, request);
        }
        ResponseResult<Void> result = saTokenUtil.handleAuthIntercept(request, handler);
        if (!result.isSuccess()) {
            ResponseResult.output(result.getHttpStatus(), result);
            return false;
        }

        TokenData tokenData = TokenData.takeFromRequest();
        if (tokenData == null) {
            return true;
        }
        AuthHelper.bindUserContext(tokenData);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex)
            throws Exception {
        // 这里需要空注解，否则sonar会不happy。
        AuthHelper.unbindUserContext();
    }
}    
```

同时需要修改InterceptorConfig中AuthenticationInterceptor所识别的URL模式。

```java
public class InterceptorConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        // @FIX 修改拦截器配置，增加拦截路径
        //registry.addInterceptor(new AuthenticationInterceptor()).addPathPatterns("/admin/**");
        AuthenticationInterceptor interceptor = new AuthenticationInterceptor();
        registry.addInterceptor(interceptor)
                .addPathPatterns("/admin/**", "/r/**", "/p/**", "/f/**", "/graphql");
    }
}
```

Nop平台只使用了少数URL端点

* `/r/{bizObjName}__{bizMethod}` 通过REST方式调用BizModel对象上的指定方法，返回结果会被包装为`ApiResponse<T>`
* `/p/{bizObjName}__{bizMethod}` 通过REST方式调用BizModel对象上的指定方法，与`/r/`不同的是，服务方法的返回结果会被直接返回，而不会被包装为`ApiResponse`
* `/f/download`和`/f/upload`用于文件上传下载
  * `/graphql`用于通过GraphQL协议调用BizModel对象上的指定方法。同一个方法可以用`/graphql`, `/r/`和`/p/`这三种方式来调用。

## 四. 增加DemoBizModel示例

NopGraphQL底层集成了NopORM，可以直接通过REST请求实现大部分增删改查操作，而无需编写任何代码。在整体设计层面，可以实现类似APIJSON这种无代码后端服务框架的功能，同时还提供更严格的安全性校验，以及更好的可扩展性。
参见[Nop平台与APIJSON的功能对比](https://mp.weixin.qq.com/s/vrQVGs-c0dVWcOJEsOz_nA)。

NopGraphQL使用起来比SpringMVC要简单得多。以DemoBizModel为例

```java
@BizModel("Demo")
public class DemoBizModel {
    @BizQuery
    public String helloWithAuth(@Name("message") String message) {
        return "hello:" + message;
    }

    @BizQuery
    @Auth(publicAccess = true)
    public String hello(@Name("message") @Optional String message) {
        return "hello:" + message;
    }
}
```

* 服务对象类不需要从任何基类继承，只需要增加`@BizModel`注解。如果是根据数据模型自动生成BizModel，会从CrudBizModel基类继承，它自动实现了一系列针对实体的增删改查操作。
* 如果是查询方法，增加`@BizQuery`注解，方法参数可以增加`@Name`注解，用于指定参数名。
* 可以增加`@Auth`注解，用于指定访问权限，`publicAccess`表示是否允许匿名访问。如果不增加`@Auth`注解缺省也会检查权限，相当于自动设置permission为`{bizObjName}:{methodName}`。
* 如果参数是可选的，则需要增加`@Optional`注解。
* 返回结果不需要额外包装为Response或者ResultBean，NopGraphQL引擎根据不同调用情况来决定如何进行包装。比如`/r/`调用时对于String类型的返回值，会包装为`ApiResponse<String>`。如果执行过程报错，则会自动根据异常码翻译为国际化后的错误消息，存放在ApiResponse的message字段中返回。

DemoBizModel与SpringMVC的Controller机制相比，它的约定要少得多，入口参数和返回值都是普通的Java对象，并没有任何特定的框架依赖，也不需要指定REST链接模式，不需要指定通过GET还是POST方法调用。
NopGraphQL引入了一些全局的约定：

1. 调用链接固定使用`/r/{bizObjName}__{methodName}`模式，其中`{bizObjName}`是BizModel的名称，`{methodName}`是BizModel的方法名。
2. 只读操作允许GET和POST两种HTTP方法，而写操作只允许POST方法。
3. 通过`@BizMutation`标记写操作，框架自动为写操作打开数据库事务环境，无需标注`@Transactional`注解。
4. 所有的参数通过URL param或者JSON body传递，自动按照名称映射到具体参数。

需要注意的是，Nop平台并不使用类扫描机制（Quarkus这种支持native编译的框架不建议在运行时执行类扫描），而且还需要支持Delta定制机制，允许第三方在不修改源码的情况下调整Bean的配置，因此只是标记了`@BizModel`注解并不会自动识别这个服务对象，而是需要在`beans.xml`中注册。

示例代码是在`resource/_vfs/app/demo/beans/app-demo.beans.xml`文件中增加如下定义

```xml
<beans x:schema="/nop/schema/beans.xdef" xmlns:x="/nop/schema/xdsl.xdef">
  <bean id="DemoBizModel" class="com.orangeforms.webadmin.nop.DemoBizModel"/>
</beans>
```

未来如果其他项目希望在已有的产品基础上进行定制化开发，就可以在`_delta`目录下增加一个同路径的`app-demo.beans.xml`文件，在其中引入将bean的实现类替换为某个派生类。比如

```xml
<beans x:schema="/nop/schema/beans.xdef" xmlns:x="/nop/schema/xdsl.xdef" x:extends="super">
  <bean id="DemoBizModel" class="app.ext.DemoBizModelEx"/>
</beans>
```

* `x:extends=super`合并的时候会按照id找到对应的bean，并自动合并所有的属性和子节点定义。

这里还需要注意的一个技术点是，`/app/demo`这种两级目录是Nop平台中的模块目录，在这个目录下需要增加一个空的`_module`文件。Nop平台启动的时候会扫描所有的`_module`文件，并自动加载模块的beans目录下所有以`app-`为前缀的`beans.xml`文件，通过这种方式实现类似SpringAutoConfiguration的效果。

## 五. 修正集成问题

橙单对于系统中所有的REST请求统一配置了结果序列化机制，会自动对所有JSON返回对象执行JSON序列化转换为文本。但是Nop平台底层已经实现了所有的JSON序列化转换，这里的转换就多余了，需要禁用掉。
具体做法是修改CommonWebMvcConfig类的实现。

```java
@Configuration
public class CommonWebMvcConfig implements WebMvcConfigurer {

    public static class MyFastJsonHttpMessageConverter extends FastJsonHttpMessageConverter {

        @Override
        public boolean canWrite(Type type, Class<?> clazz, MediaType mediaType) {
            HttpServletRequest request = ContextUtil.getHttpRequest();
            if (request == null) {
                return super.canWrite(type, clazz, mediaType);
            }
            if (request.getRequestURI().contains("/v3/api-docs")) {
                return false;
            }
            String uri = request.getRequestURI();

            //@FIX Nop平台的链接不要进行JSON转换
            if (uri.startsWith("/r/") || uri.startsWith("/p/") || uri.startsWith("/f/") || uri.startsWith("/graphql"))
                return false;
            return super.canWrite(type, clazz, mediaType);
        }
    }
}
```

基于可逆计算理论设计的低代码平台NopPlatform已开源：

- gitee: [https://gitee.com/canonical-entropy/nop-entropy](https://gitee.com/canonical-entropy/nop-entropy)
- github: [https://github.com/entropy-cloud/nop-entropy](https://github.com/entropy-cloud/nop-entropy)
- gitcode:[https://gitcode.com/canonical-entropy/nop-entropy](https://gitcode.com/canonical-entropy/nop-entropy)
- 开发示例：[https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/tutorial/tutorial.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/tutorial/tutorial.md)
- 可逆计算原理和Nop平台介绍及答疑：[https://www.bilibili.com/video/BV14u411T715/](https://www.bilibili.com/video/BV14u411T715/)
- 官网国际站: [https://nop-platform.github.io/](https://nop-platform.github.io/)
- 网友Crazydan Studio建立的Nop开发实践分享网站: [https://nop.crazydan.io/](https://nop.crazydan.io/)
