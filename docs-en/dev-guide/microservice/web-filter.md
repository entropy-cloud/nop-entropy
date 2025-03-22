# Configuration Items

| Name                  | Default Value | Description                                                                 |
|-----------------------|--------------|-----------------------------------------------------------------------------|
| nop.web.http-server-filter.enabled | true          | Whether to wrap NopIoC-defined IHttpServerFilter for Spring and Quarkus Filters. |
| nop.quarkus.http-server-filter.sys-order  | 5             | Default priority level of Quarkus framework's IHttpServerFilter, lower value higher priority. |
| nop.quarkus.http-server-filter.app-order  | 10            | Default application-level priority of Quarkus framework's IHttpServerFilter, lower value higher priority. |
| nop.spring.http-server-filter.sys-order   | 0             | Default system-level priority of Spring framework's IHttpServerFilter, lower value higher priority. |
| nop.spring.http-server-filter.app-order   | 1000          | Default application-level priority of Spring framework's IHttpServerFilter, lower value higher priority. |

## Built-in Filters

- **ContextHttpServerFilter**: Initializes the global IContext object. A new IContext is created for each Web request.
- **AuthHttpServerFilter**: Handles login checks. Can be customized by inheriting this class or replacing the ILoginService implementation.
  - AuthHttpServerFilter contains knowledge related to Web environment, such as cookies. However, ILoginService does not have Web environment knowledge; it processes requests based on request messages.

## SpringSecurity Integration

SpringSecurity uses a unique HttpServletFilter but creates its own FilterChain internally. The order of this Filter is Integer.MIN_VALUE + 50, so if you want it to execute before Nop-platform-registered filters, its order must be set very low. By default, Nop-platform-registered filters will execute after SpringSecurity filters (since their order defaults to 0, which is lower than SpringSecurity's order).

Another approach is to insert custom Filters into the SecurityFilterChain, such as:

```java
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

In SpringHttpServerFilterConfiguration, the Nop platform's filter inherits from OncePerRequestFilter, meaning it will only be executed once in the entire FilterChain. If `registerSysFilter()` is added to the SecurityFilterChain, this filter will execute earlier. By default, it should execute after SpringSecurity filters because its order is 0, which is lower than SpringSecurity's order.
