# 服务函数
MethodDescriptor提供了generateFullMethodName方法来生成全方法名

````
fullMethodName = packageName + '.' + serviceName + '/' + methodName
````

GET方法 ： /fullMethodName?base64编码的payload

content设置为 application/grpc


## Quarkus

* 配置类： GrpcServerConfiguration
* 缺省服务端口： 9000
* 缺省情况下 useSeparateServer=true

引入nop-quarkus-web-starter模块，配置nop.http.netty-server.enable-log=true后，会设置HttpServerOptions.logActivity=true，
最终会应用Http2FrameLogger输出调试日志。

