# Microservice Framework

## Distributed RPC
Integrate Nacos to implement distributed RPC, see [rpc.md](rpc.md)

For the design principles of the RPC framework, see [rpc-design.md](rpc-design.md)

## Filters

The Nop platform provides a filter-based interception mechanism that can intercept processing before and after request handling. For the usage of filters, see [web-filter.md](web-filter.md)

## Authentication

When invoking RPC services via HttpClient, you need to set the accessToken, see [rpc-auth.md](rpc-auth.md)

## Feign Integration
Integrate NopRPC with Spring's Feign framework for combined use, see [feign.md](feign.md)

## gRPC Integration
With simple configuration, you can expose service functions in NopGraphQL as gRPC services, see [grpc.md](grpc.md)
<!-- SOURCE_MD5:7c2c48a11424d7e51561566218733269-->
