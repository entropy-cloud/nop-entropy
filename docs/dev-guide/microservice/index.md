# 微服务框架

## 分布式RPC
集成nacos实现分布式RPC, 参见[rpc.md](rpc.md)

RPC框架的设计原理，参见[rpc-design.md](rpc-design.md)

## 过滤器

Nop平台提供了一套基于过滤器的拦截机制，可以在请求处理前后进行拦截处理。filter的使用参见[web-filter.md](web-filter.md)

## Feign集成
将NopRPC与Spring的Feign框架集成在一起使用，参见[feign.md](feign.md)

## gRPC集成
通过简单配置即可将NopGraphQL中的服务函数暴露为gRPC服务，参见[grpc.md](grpc.md)
