# NopGraphQL对外暴露为Grpc

引入nop-rpc-grpc模块后，NopGraphQL服务将对外暴露grpc服务。

1. 对象和服务都位于`graphql.api`包中，可以通过nop.grpc.graphql-api-package来配置。
2. 请求消息名一般为 `{bizObjName}__{bizMethod}_request`，响应消息名为GraphQL对象类型名
3. 如果GraphQL服务函数返回标量类型，则响应消息名称为 `{bizObjName}__{bizMethod}_response`，其中通过value属性来返回标量字段
4. 在调试模式下，访问/p/DevDoc\_\_grpc可以返回grpc的proto定义

> grpc的设计目前缺少一个namespace的概念，导致无法将多个proto文件中声明的所有message和service合并到一个统一的输出文件中

## 结果选择

因为采用了NopGraphQL引擎，所以它为grpc也自动引入了结果字段选择能力，可以通过grpc的metadata传递nop-selection，通过它可以实现结果选择。
为了与结果选择的能力相匹配，所有响应消息中的字段都自动设置为optional。例如：

REST请求可以通过@selection参数来实现结果字段选择 /r/NopAuthUser\_findList?@selection=userName,userStatus。
在使用grpc时，可以使用Metadata传递nop-selection=userName,userStatus这个header，实现同样的选择功能，返回的数据中就只包含userName和userStatus两个字段。

## propId配置

grpc所使用的protobuf编码协议要求每个字段都具有确定的唯一编号propId。Nop平台在代码生成时为每个实体中的数据库字段生成了propId，根据api模型生成的
Javabean中每个字段也生成了对应的propId。但是其他情况下需要自己手工增加对应的propId

* 自己在meta中自己增加的属性需要自己手工增加propId配置
* 在javabean中为get方法增加`@PropMeta(propId=xx)`配置

如果全局开启了nop.grpc.auto-init-prop-id=true，则会自动为没有propId的字段按照name字符串顺序增加propId，但是这种情况下如果后期增加字段，
则可能导致与此前规定的顺序不同。

## Grpc服务器

目前是使用grpc-java来启动单独的grpc服务器，它的服务实现类从GraphQL服务转化而来。目前的实现中grpc服务的端口与rest的服务端口是分离的

* 如果配置了`nop.cluster.registration.enabled`且引入了nop-rpc-cluster依赖，则启动时会注册到服务注册中心，服务名为`{nop.application.name}-grpc`。
* 通过nop.server.grpc-port来配置Grpc服务端口，缺省为9000
* 在配置文件中通过nop.grpc.server.xxx来配置GrpcServerConfig中的各项属性。

|配置名|说明|
|---|---|
|nop.grpc.server.cert-chain|TLS证书链文件|
|nop.grpc.server.private-key|TLS私钥文件|
|nop.grpc.server.handshake-timeout|握手超时时间，Duration格式|
|nop.grpc.server.keep-alive-timeout|保持连接超时时间，Duration格式|
|nop.grpc.server.max-connection-idle|最大连接空闲时间，Duration格式|
|nop.grpc.server.max-connection-age|最大连接存活时间，Duration格式|
|nop.grpc.server.max-connection-age-grace|最大连接存活时间宽限，Duration格式|
|nop.grpc.server.permit-keep-alive-time|允许保持连接的时间，Duration格式|
|nop.grpc.server.permit-keep-alive-without-calls|没有调用时允许保持空闲连接|
|nop.grpc.server.max-inbound-message-size|最大入站消息大小|
|nop.grpc.server.max-inbound-metadata-size|最大入站元数据大小|
|nop.grpc.server.thread-pool|连接池的配置，参见ThreadPoolConfig|

## 服务注册

grpc-defaults.beans.xml中通过nopGrpcAutoConfiguration注册类会向nacos注册grpc服务实现。
目前Nop平台的grpc服务端口和rest服务端口是分离的，注册的服务名也不同。grpc服务名是
`${nop.application.name}-rpc`，而REST服务名是是`${nop.application.name}`，不带http后缀，这样和springcloud的习惯符合

## 调试

在调试模式下，在dump目录下会生成`/nop/main/graphql/graphql-api.proto`定义文件

## 服务函数

MethodDescriptor提供了generateFullMethodName方法来生成全方法名

```
fullMethodName = packageName + '.' + serviceName + '/' + methodName
```

GET方法 ： /fullMethodName?base64编码的payload

content设置为 application/grpc

## Quarkus

* 配置类： GrpcServerConfiguration
* 缺省服务端口： 9000
* 缺省情况下 useSeparateServer=true

引入nop-quarkus-web-starter模块，配置nop.http.netty-server.enable-log=true后，会设置HttpServerOptions.logActivity=true，
最终会应用Http2FrameLogger输出调试日志。
