# NopGraphQL对外暴露为Grpc

引入nop-rpc-grpc模块后，NopGraphQL服务将对外暴露grpc服务。

1. 对象和服务都位于`graphql.api`包中
2. 请求消息名一般为 `{bizObjName}__{bizMethod}_request`，响应消息名为GraphQL对象类型名
3. 如果GraphQL服务函数返回标量类型，则响应消息名称为 `{bizObjName}__{bizMethod}_response`，其中通过value属性来返回标量字段
4. 在调试模式下，访问/p/DevDoc__grpc可以返回grpc的proto定义

> grpc的设计目前缺少一个namespace的概念，导致无法将多个proto文件中声明的所有message和service合并到一个统一的输出文件中

## 结果选择
因为采用了NopGraphQL引擎，所以它为grpc也自动引入了结果选择能力，可以通过grpc的metadata传递nop-selection，通过它可以实现结果选择。
为了与结果选择的能力相匹配，所有响应消息中的字段都自动设置为optional。

## Grpc服务器

目前是使用grpc-java来启动单独的grpc服务器，它的服务实现类从GraphQL服务转化而来。目前的实现中grpc服务的端口与rest的服务端口是分离的

* 如果配置了`nop.cluster.registration.enabled`且引入了nop-rpc-cluster依赖，则启动时会注册到服务注册中心，服务名为`{nop.application.name}-grpc`。
* 通过nop.server.grpc-port来配置Grpc服务端口，缺省为9000
* 在配置文件中通过nop.grpc.server.xxx来配置GrpcServerConfig中的各项属性。

| 配置名                                             | 说明                        |
|-------------------------------------------------|---------------------------|
| nop.grpc.server.cert-chain                      | TLS证书链文件                  |
| nop.grpc.server.private-key                     | TLS私钥文件                   |
| nop.grpc.server.handshake-timeout               | 握手超时时间，Duration格式         |
| nop.grpc.server.keep-alive-timeout              | 保持连接超时时间，Duration格式       |
| nop.grpc.server.max-connection-idle             | 最大连接空闲时间，Duration格式       |
| nop.grpc.server.max-connection-age              | 最大连接存活时间，Duration格式       |
| nop.grpc.server.max-connection-age-grace        | 最大连接存活时间宽限，Duration格式     |
| nop.grpc.server.permit-keep-alive-time          | 允许保持连接的时间，Duration格式      |
| nop.grpc.server.permit-keep-alive-without-calls | 没有调用时允许保持空闲连接             |
| nop.grpc.server.max-inbound-message-size        | 最大入站消息大小                  |
| nop.grpc.server.max-inbound-metadata-size       | 最大入站元数据大小                 |
| nop.grpc.server.thread-pool                     | 连接池的配置，参见ThreadPoolConfig |

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

