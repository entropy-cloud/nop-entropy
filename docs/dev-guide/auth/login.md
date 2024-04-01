# 登录逻辑

## 外部公开链接

在[auth-service.beans.xml](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-auth/nop-auth-service/src/main/resources/_vfs/nop/auth/beans/auth-service.beans.xml)中定义了
nopAuthHttpServerFilter和nopAuthFilterConfig。其中AuthHttpServerFilter负责执行所有用户登录检查，如果发现没有登录就返回HTTP 401错误或者重定向到登录页。

## 公开链接

authFilter使用AuthFilterConfig中的配置来确定哪些路径是公开路径。

缺省情况下开放了如下路径:

1. /r/LoginApi\_\* 等登录相关接口
2. /q/health\* 等健康检查接口
3. /q/metrics\* 等内部状态度量接口

```xml
 <bean id="nopAuthFilterConfig" class="io.nop.auth.core.filter.AuthFilterConfig">
        <!-- 未指定的情况下都是公开页面，主要是js/css/image等 -->
        <property name="defaultPublic" value="true"/>

        <property name="publicPaths">
            <list>
                <value>/r/LoginApi_*</value>
                <value>/q/health*</value>
                <value>/q/metrics*</value>
            </list>
        </property>

        <property name="authPaths">
            <list>
                <value>/graphql*</value>
                <!-- REST请求 -->
                <value>/r/*</value>
                <!-- quarkus内置管理页面 -->
                <value>/q/*</value>
                <!-- 返回具有指定contentType的内容 -->
                <value>/p/*</value>
                <!-- 文件上传下载 -->
                <value>/f/*</value>
            </list>
        </property>
    </bean>
```

## 定制登录逻辑

存在两种方式定制登录逻辑

### 1. 定制AuthHttpServerFilter

如果需要定制登录逻辑，可以继承AuthHttpServerFilter，然后定义一个id为nopAuthHttpServerFilter的bean，即可覆盖平台中内置的authFilter。

```xml
<bean id="nopAuthServerFilter" class="xxx.MyFilter" />
```

> 因为平台内置的nopAuthServerFilter标记了`ioc:default=true`，所以只要发现有其他同名的bean，就会自动覆盖平台内置的authFilter

### 2. 定制ILoginService

authFilter中实际执行登录验证操作时使用的是ILoginService接口，可以提供一个ILoginService的实现来覆盖系统内置的登录逻辑。
与AuthFilter不同的是，这里无法访问到Web环境，所以一些涉及到Web环境处理的逻辑只能通过继承AuthHttpServerFilter来实现（比如修改cookie绑定逻辑等）。

目前集成keycloak单点登录服务就是用通过增加OAuthLoginServiceImpl类来实现，参见[sso.md](sso.md)

## 配置项

1. nop.auth.login.use-dao-user-context-cache
   设置为true后会启用DaoUserContextCache，将IUserContext中的信息保存到NopAuthSession表中。

2. nop.auth.access-token-expire-seconds
   访问令牌(access token)超时时间，缺省为30\*60，即30分钟

3. nop.auth.refresh-token-expire-seconds
   刷新令牌(refresh token)的超时时间，缺省为300\*60，即5个小时
