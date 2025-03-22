# NopGraphQL Exposed as Grpc

After introducing the `nop-rpc-grpc` module, the NopGraphQL service will expose gRPC services.

1. Both objects and services are located in the `graphql.api` package, which can be configured using `nop.grpc.graphql-api-package`.
2. The request message name typically follows the format `{bizObjName}__{bizMethod}_request`, while the response message name corresponds to the GraphQL object type name.
3. If the GraphQL service function returns a scalar type, the response message name will be `{bizObjName}__{bizMethod}_response`, where scalar fields are returned using the `value` attribute.
4. In debug mode, accessing `/p/DevDoc/__grpc` will return the gRPC definitions.

> The current design of gRPC lacks a namespace concept, making it impossible to merge all messages and services from multiple proto files into a single unified output file.

## Result Selection

Due to the adoption of the NopGraphQL engine, the gRPC service also inherits result field selection capability. This can be achieved by using `nop-selection` in the metadata. To match this capability, all fields in response messages are automatically set to `optional`. For example:

- REST requests can implement result field selection using the `@selection` parameter, such as `/r/NopAuthUser/_findList?@selection=userName,userStatus`.
- In gRPC, use Metadata to send `nop-selection=userName,userStatus`, which will result in the response data containing only `userName` and `userStatus` fields.

## propId Configuration

The protobuf protocol used by gRPC requires each field to have a unique `propId`. The Nop platform generates `propId` for database fields based on the API model. Similarly, each field in the JavaBean also receives a corresponding `propId`. However, in other cases, you need to manually add the corresponding `propId`.

- Manually add attributes in Metadata: `@PropMeta(propId=xx)`
- In JavaBeans, add `@PropMeta(propId=xx)` to the `get` method.

If `nop.grpc.auto-init-prop-id=true` is enabled globally, then fields without a predefined `propId` will be assigned one based on their name in alphabetical order. However, if new fields are added later, this may cause inconsistencies with previously defined orders.

## Grpc Server

The current implementation uses gRPC-java to start a separate gRPC server. Its service implementation is derived from the GraphQL service. The gRPC service's port is distinct from the REST service's port.

- If `nop.cluster.registration.enabled` is configured and the `nop-rpc-cluster` dependency is included, the service will be registered in the service registry with the name `{nop.application.name}-grpc`.
- The gRPC server port can be configured using `nop.server.grpc-port`, defaulting to 9000.
- In the configuration file, use `nop.grpc.server.xxx` to configure various properties of the GrpcServerConfig.


| Configuration Name | Description |
|-------------------|-------------|
| nop.grpc.server.cert-chain | TLS certificate chain file |
| nop.grpc.server.private-key | TLS private key file |
| nop.grpc.server.handshake-timeout | Handshake timeout, Duration format |
| nop.grpc.server.keep-alive-timeout | Keep-alive timeout, Duration format |
| nop.grpc.server.max-connection-idle | Maximum idle connection time, Duration format |
| nop.grpc.server.max-connection-age | Maximum connection age, Duration format |
| nop.grpc.server.max-connection-age-grace | Maximum connection grace period, Duration format |
| nop.grpc.server.permit-keep-alive-time | Allow keep-alive time, Duration format |
| nop.grpc.server.permit-keep-alive-without-calls | Allow keep-alive without calls |
| nop.grpc.server.max-inbound-message-size | Maximum inbound message size |
| nop.grpc.server.max-inbound-metadata-size | Maximum inbound metadata size |
| nop.grpc.server.thread-pool | ThreadPool configuration, see ThreadPoolConfig |


## Service Registration

The registration is done via `nopGrpcAutoConfiguration` in `grpc-defaults.beans.xml`. The service name for gRPC is `${nop.application.name}-rpc`, while the REST service name is `${nop.application.name}` without the HTTP suffix, aligning with Spring Cloud conventions.


## Debugging

In debug mode, logs are generated in the `dump` directory at `/nop/main/graphql/graphql-api.proto`.


## Service Functionality

`MethodDescriptor` provides a method `generateFullMethodName` to generate the full method name:

```plaintext
fullMethodName = packageName + '.' + serviceName + '/' + methodName
```

The GET method is accessed via `/fullMethodName?base64_encoded_payload`.

The content is set to `application/grpc`.


## Quarkus Configuration

- Configuration Class: GrpcServerConfiguration
- Default Port: 9000
- Graceful Shutdown: useSeparateServer=true

The project uses the `nop-quarkus-web-starter` module, with logging enabled by setting `nop.http.netty-server.enable-log=true`, which enables `Http2FrameLogger` for debug logging.

