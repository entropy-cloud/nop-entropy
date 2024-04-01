# 配置项

|名称|缺省值|说明|
|---|---|---|
|nop.web.http-server-filter.enabled|true|是否将NopIoC中定义的IHttpServerFilter包装为Spring和Quarkus所使用的Filter|
|nop.quarkus.http-server-filter.sys-order|5|Quarkus框架所使用的系统级IHttpServerFilter的缺省优先级，值越小优先级越高|
|nop.quarkus.http-server-filter.app-order|10|Quarkus框架所使用的应用级IHttpServerFilter的缺省优先级，值越小优先级越高|
|nop.spring.http-server-filter.sys-order|0|Spring框架所使用的系统级IHttpServerFilter的缺省优先级，值越小优先级越高|
|nop.spring.http-server-filter.app-order|1000|Spring框架所使用的应用级IHttpServerFilter的缺省优先级，值越小优先级越高|

## 内置Filter

* ContextHttpServerFilter: 初始化全局的IContext对象。每次Web请求都产生一个新的IContext
* AuthHttpServerFilter: 负责进行登录检查。继承这个类或者替换ILoginService的实现类都可以定制登录逻辑。
  AuthHttpServerFilter具有Web环境相关的知识，例如cookie等。但是ILoginService就没有Web环境的知识了，它只能通过请求消息来进行处理。

## SpringSecurity集成

SpringSecurity的架构是使用唯一的一个HttpServletFilter，但是内部建立自己的FilterChain。SpringSecurity的这个Filter的order的值是Integer.MIN\_VALUE+50,
所以如果想在SpringSecurity的filter之前执行代码，需要把order设置为很小的值。缺省配置下Nop平台注册的filter会在SpringSecurity的filter之后执行。

另外一种做法是在SpringSecurity的SecurityFilterChain中插入自定义的Filter，比如如下方式:

```
    @Inject
    SpringHttpServerFilterConfiguration config;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(Customizer.withDefaults())
                .addFilter(config.registerSysFilter().getFilter());
        return http.build();
    }
```

SpringHttpServerFilterConfiguration中创建的Nop平台的filter选择了从OncePerRequestFilter继承，因此在整个filterChain中只会执行一次。
如果在SpringSecurity的SecurityFilterChain中注册了`registerSysFilter()`，则这个filter会提前执行。缺省情况下它应该是在SpringSecurity的
filter执行完毕之后才执行（因为它的order缺省为0，比SpringSecurity的order大）
