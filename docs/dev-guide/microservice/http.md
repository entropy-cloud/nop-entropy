# Http客户端

IHttpClient提供了Http客户端封装，可以异步执行http调用，并使用http2协议。

* nop-http-api包定义了API接口
* nop-http-client-jdk提供了JDK内置的缺省实现
* nop-http-client-apache提供了Apache HttpClient 5的实现。
* nop-http-client-oauth提供了配置式的access token自动设置支持

需要使用哪个http client的实现就引入哪个包。


## 获取客户端IP

后端提供了统一的IHttpServerContext接口，对服务端上下文环境进行了封装。IClientIpFetcher接口从IHttpServerContext中获取客户端ip。
因为可能会经过中间的proxy服务器，客户端真实IP需要通过X-Forwarded-For或者Forworded这两个http header进行读取。可以定制nopClientIpFetcher来改变缺省获取逻辑。

经过SpringGraphQLWebService等入口服务调用的请求，ApiRequest中会设置nop-client-addr这个header。IServiceContext提供了getRequestClientIp()帮助函数.
