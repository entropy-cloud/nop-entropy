# NopGraphQL Exposed as gRPC

After introducing the nop-rpc-grpc module, the NopGraphQL service exposes gRPC services externally.

1. Both objects and services are located in the `graphql.api` package and can be configured via nop.grpc.graphql-api-package.
2. The request message name is generally `{bizObjName}__{bizMethod}_request`, and the response message name is the GraphQL object type name.
3. If a GraphQL service function returns a scalar type, the response message name is `{bizObjName}__{bizMethod}_response`, where the scalar field is returned via the value property.
4. In debug mode, visiting /p/DevDoc\_\_grpc returns the gRPC proto definition.

> gRPC currently lacks the concept of a namespace in its design, which makes it impossible to merge all messages and services declared across multiple proto files into a single unified output file.

## Result Selection

Because it uses the NopGraphQL engine, it automatically brings result field selection capabilities to gRPC as well. You can pass nop-selection via gRPC metadata to enable result selection.
To support result selection, all fields in response messages are automatically set to optional. For example:

A REST request can implement result field selection via the @selection parameter: /r/NopAuthUser\_findList?@selection=userName,userStatus.
When using gRPC, you can pass the header nop-selection=userName,userStatus via Metadata to achieve the same selection functionality, and the returned data will contain only the userName and userStatus fields.

## propId Configuration

The protobuf encoding used by gRPC requires each field to have a definite unique number, propId. During code generation, the Nop platform generates a propId for each database field in an entity; the JavaBean generated from the API model also generates a corresponding propId for each field. In other cases, you need to add the corresponding propId yourself.

* For attributes you add manually in meta, you need to add a propId configuration yourself.
* In the JavaBean, add `@PropMeta(propId=xx)` to the getter method.

If nop.grpc.auto-init-prop-id=true is enabled globally, fields without propId will automatically be assigned propIds according to the lexicographical order of their name strings. However, in this case, if fields are added later, the resulting order may differ from the previously established order.

## gRPC Server

Currently, grpc-java is used to start a standalone gRPC server, and its service implementation classes are transformed from GraphQL services. In the current implementation, the gRPC server port is separate from the REST server port.

* If `nop.cluster.registration.enabled` is configured and the nop-rpc-cluster dependency is included, the service will be registered with the service registry at startup with the service name `{nop.application.name}-grpc`.
* Configure the gRPC server port via nop.server.grpc-port; the default is 9000.
* In the configuration file, configure properties in GrpcServerConfig via nop.grpc.server.xxx.

|配置名|说明|
|---|---|
|nop.grpc.server.cert-chain|TLS certificate chain file|
|nop.grpc.server.private-key|TLS private key file|
|nop.grpc.server.handshake-timeout|Handshake timeout, Duration format|
|nop.grpc.server.keep-alive-timeout|Keep-alive timeout, Duration format|
|nop.grpc.server.max-connection-idle|Maximum connection idle time, Duration format|
|nop.grpc.server.max-connection-age|Maximum connection lifetime, Duration format|
|nop.grpc.server.max-connection-age-grace|Grace period for maximum connection lifetime, Duration format|
|nop.grpc.server.permit-keep-alive-time|Permitted keep-alive time, Duration format|
|nop.grpc.server.permit-keep-alive-without-calls|Allow keeping idle connections without calls|
|nop.grpc.server.max-inbound-message-size|Maximum inbound message size|
|nop.grpc.server.max-inbound-metadata-size|Maximum inbound metadata size|
|nop.grpc.server.thread-pool|Thread pool configuration; see ThreadPoolConfig|

## Service Registration

In grpc-defaults.beans.xml, the nopGrpcAutoConfiguration registration class registers the gRPC service implementation with Nacos.
Currently, the gRPC server port and the REST server port on the Nop platform are separate, and the registered service names are also different. The gRPC service name is
`${nop.application.name}-rpc`, while the REST service name is `${nop.application.name}`, without the http suffix, aligning with Spring Cloud conventions.

## Debugging

In debug mode, the `/nop/main/graphql/graphql-api.proto` definition file will be generated under the dump directory.

## Service Functions

MethodDescriptor provides the generateFullMethodName method to generate the full method name:

```
fullMethodName = packageName + '.' + serviceName + '/' + methodName
```

GET method: /fullMethodName?Base64-encoded payload

Content-Type is set to application/grpc.

## Quarkus

* Configuration class: GrpcServerConfiguration
* Default server port: 9000
* By default, useSeparateServer=true

After introducing the nop-quarkus-web-starter module and setting nop.http.netty-server.enable-log=true, HttpServerOptions.logActivity=true will be set,
and ultimately Http2FrameLogger will be applied to output debug logs.
<!-- SOURCE_MD5:6261a800f500a1068ddc1dd63cce5629-->
