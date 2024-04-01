# 服务鉴权

服务间鉴权的方式与普通用户相同，都是用accessToken。在LoginService的实现中可以判断accessToken是否对应于具体用户，如果不是，则可以创建一个系统用户上下文。

## 开放所有服务

`nop.auth.service-public: true`会自动允许所有服务对象被匿名访问。如果Authorization这个http header中传递的accessToken为空或者解析失败，会自动创建一个系统上下文，
而不是返回【尚未登录】的错误信息。

在`auth-service.beans.xml`配置文件中通过nopAuthFilterConfig配置了服务路径，

```xml
<bean id="nopAuthFilterConfig" class="io.nop.auth.core.filter.AuthFilterConfig">
        <property name="servicePaths">
            <list>
                <value>/graphql*</value>
                <!-- REST请求 -->
                <value>/r/*</value>
                <!-- 返回具有指定contentType的内容 -->
                <value>/p/*</value>
                <!-- 文件上传下载 -->
                <value>/f/*</value>
            </list>
        </property>
    
        <property name="servicePublic" value="@cfg:nop.auth.service-public|false"/>
    </bean>
```

* 可以通过nop-tenant, nop-timezone, nop-locale等http header来设置系统用户上下文的tenantId和locale等信息
* 系统用户的id固定为sys
