# Http客户端

IHttpClient提供了Http客户端封装，可以异步执行http调用，并使用http2协议。

* nop-http-api包定义了API接口
* nop-http-client-jdk提供了JDK内置的缺省实现
* nop-http-client-apache提供了Apache HttpClient 5的实现。
* nop-http-client-oauth提供了配置式的access token自动设置支持

需要使用哪个http client的实现就引入哪个包。


