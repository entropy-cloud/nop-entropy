# Configuration Items

|Name|Default Value|Description|
|---|---|---|
|nop.web.http-server-filter.enabled|true|Whether to wrap the IHttpServerFilter defined in NopIoC as the Filter used by Spring and Quarkus|
|nop.quarkus.http-server-filter.sys-order|5|Default priority of the system-level IHttpServerFilter used by the Quarkus framework; the smaller the value, the higher the priority|
|nop.quarkus.http-server-filter.app-order|10|Default priority of the application-level IHttpServerFilter used by the Quarkus framework; the smaller the value, the higher the priority|
|nop.spring.http-server-filter.sys-order|0|Default priority of the system-level IHttpServerFilter used by the Spring framework; the smaller the value, the higher the priority|
|nop.spring.http-server-filter.app-order|1000|Default priority of the application-level IHttpServerFilter used by the Spring framework; the smaller the value, the higher the priority|

## Built-in Filters

* ContextHttpServerFilter: Initializes the global IContext object. Each web request creates a new IContext.
* AuthHttpServerFilter: Responsible for login checks. You can customize the login logic by extending this class or by replacing the implementation of ILoginService. AuthHttpServerFilter has knowledge of the web environment, such as cookies, etc. However, ILoginService has no knowledge of the web environment; it can only process based on the request message.

## Spring Security Integration

The architecture of Spring Security uses a single HttpServletFilter, but builds its own internal FilterChain. The order value of this Spring Security filter is Integer.MIN\_VALUE+50, so if you want code to run before Spring Security's filter, you need to set a very small order. Under the default configuration, filters registered by the Nop platform will run after Spring Security's filter.

Another approach is to insert a custom Filter into Spring Security's SecurityFilterChain, for example as follows:

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

The filter of the Nop platform created in SpringHttpServerFilterConfiguration extends OncePerRequestFilter, so it will execute only once in the entire filterChain.
If registerSysFilter() is registered in Spring Security's SecurityFilterChain, this filter will execute earlier. By default, it should execute only after Spring Security's
filter has finished (because its default order is 0, which is greater than Spring Security's order).
<!-- SOURCE_MD5:db58288b2331a7d4f2525b60c6916bc6-->
