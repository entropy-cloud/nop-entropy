# 低代码平台中的分布式RPC框架(约3000行代码)

RPC是分布式系统设计中不可或缺的一个部分。国内开源的RPC框架很多，它们的设计大都受到了dubbo框架的影响，核心的抽象概念与dubbo类似。从今天的角度上看，dubbo的设计已经过于繁琐冗长，如果基于现在的技术环境，重新审视RPC框架的定位和设计，我们可以得到更简单、扩展性更好的实现方案。本文将介绍Nop平台中的NopRPC框架的设计思想和具体实现，它充分利用了成熟的技术设施，如IoC容器、JSON序列化、GraphQL引擎、Nacos注册中心、Sentinel熔断限流器等，通过大概3000行代码即可实现一个具备实用价值的分布式RPC框架。NopRPC具有如下特点：

1. **可以将NopGraphQL服务封装为普通的强类型的RPC接口，同时保留GraphQL的响应字段选择能力**

2. **可以将任意支持单向信息发送和接收的消息接口封装为等待响应消息的RPC接口**

3. **Http、Socket、WebSocket、消息队列、批处理文件等都只是一种接口形式，通过配置可以将同一个服务实现适配到多种不同的接口形式**

4. **支持取消正在执行的RPC调用，取消时可以调用远程服务上的cancelMethod**

5. **支持将配对的startTask和checkTaskStatus两个调用封装为一个异步RPC接口**

6. 支持灰度发布。可以在网关处设置路由选择header，直接控制后续调用链路中的服务路由。

7. 支持广播式调用、选主调用（只调用选举得到的主服务器），以及指定服务器调用（直接指定被调用服务的地址和端口）

8. 利用NopTcc引擎实现分布式事务

9. 利用NopTask引擎实现服务端的低代码模型驱动开发

10. 支持端到端的RPC超时控制

11. 支持国际化多语言消息

12. 支持错误码映射（例如将多个内部错误码统一映射为同一个外部错误码或者将同一个错误码根据错误参数不同映射为不同的外部错误码和错误消息）

13. 支持云原生的服务网格

14. 支持GraalVM原生应用编译

NopRPC的具体使用文档参见[rpc.md](rpc.md)

# 一. 请求和响应消息设计

RPC的核心功能是发送请求消息和接收响应消息，所以请求消息和响应消息的结构是RPC中的一项关键性设计。NopRPC框架中的消息结构定义如下：

```java
class ApiRequest<T>{
    Map<String,Object> headers;
    T data;
    FieldSelectionBean selection;
    Map<String,Object> properties;
}

class ApiResponse<T>{
    Map<String,Object> headers;
    int status;
    String code;
    String msg;
    T data;
}
```

1. 根据可逆计算原理，Nop平台中所有的核心数据结构都要采取（data, metadata)这种配对的结构设计，在metadata中可以存放data之外的扩展数据，因此我们为消息对象增加了headers字段，在传输时可以映射到底层信道支持的headers字段，例如通过http传输时headers对应于http的headers，而通过Kafka消息队列传输时，对应于Kafka消息的headers。

2. GraphQL的一个特别之处是支持调用端选择返回哪些数据，这样可以减少返回的数据量并可以优化服务端数据处理过程。**ApiRequest增加的selection字段将这种能力扩展到了所有RPC调用过程**。

3. headers是与data一起被发送到远端的扩展数据，除了这些扩展数据之外，我们经常还需要使用一些仅存在于当前处理过程中的扩展对象，例如responseNormalizer等，因此ApiRequest中定义了一个properties扩展数据集合，它不支持json序列化，可以用于存放那些不需要被发送到远端的临时数据。

4. NopRPC对错误码和错误消息的处理进行了统一规范化，前端页面的Ajax请求直接使用ApiResponse返回消息格式，统一了RPC和Web请求的输入输出规范。

5. 为了支持可以通过命令行调用RPC服务，ApiResponse通过整数类型的status字段来表示是否调用成功。如果调用成功，则返回0。作为命令行程序被调用时，status可以直接被映射为命令行的返回值。

一般的RPC框架中，Request和Response消息往往会包含大量实现细节，导致它们仅限于在框架实现层面作为内部类来使用，而NopRPC的设计则是将ApiRequest和ApiResponse通用化，在所有需要传输消息、返回信息的地方都采用统一的消息结构，实现了RPC、Web框架、消息队列、批处理服务、命令行应用等一系列接口的无缝对接。

# 二. RPC的分解构造

NopRPC的核心接口是IRpcService

```java
interface IRpcService{
   CompletionStage<ApiResponse<?>> callAsync(String serviceMethod, 
           ApiRequest<?> request, ICancelToken cancelToken);
}

interface ICancelToken{
   boolean isCancelled();
   String getCancelReason();
   void appendOnCancel(Consumer<String> task);
}
```

通过以上接口定义，我们可以获知如下信息：

1. NopRPC是一个面向异步处理的框架，而且它支持取消机制

2. ApiRequest和ApiResponse都是POJO对象，框架本身没有任何运行时假定，因此可以脱离Web环境以及Socket环境来使用。

> 现在有些RPC框架采用ReactiveStream的设计，每个RPC请求可能产生多个响应消息，并且支持通过RPC来下载大附件。但是在Nop平台中，RPC的定位就是实现应用之间一对一的信息交换：每发送一个请求消息，接收且必然接收到唯一的一个响应消息。因为按照ReactiveStream方式来实现RPC会导致服务端和客户端的管理控制变得更加复杂，而在实际使用过程中大部分情况下却用不到多个返回消息的情况。另外，在消息系统的抽象中，本身就提供了发送和接收消息流的功能，如果通过RPC再次暴露类似的功能就显得有些多余。至于大文件的上传下载现在一般都是封装为单独的文件服务，可以专门定义针对云存储优化的接口，也没有必要在RPC框架中再提供类似的功能。

## 2.1 RPC over GraphQL
一般的RPC服务端都是根据消息类型直接映射到某个服务方法，然后所有业务处理都在这个消息服务函数中执行。但是在NopRPC中，RPC调用在服务端会将消息投递给GraphQL引擎，然后由GraphQLExecutor负责协调组织多个DataLoader协同工作。例如在服务端我们实现了如下BizModel

```java
@BizModel("MyEntity")
public class MyEntityBizModel{
    @BizQuery
    public List<MyEntity> findList(@RequestBean MyRequestBean request,             
             FieldSelectionBean selection){
        ....
    }
    
    @BizLoader("children")
    @GraphQLReturn(bizObjName = "MyEntity")
    public List<MyEntity> loadChildren(@ContextSource MyEntity entity) {
        ...
        return children;
    }
}

class MyEntity{
   private String name;
   private List<MyEntity> children;
   
   public String getName(){
      return name;
   }
   
   @LazyLoad
   public List<MyEntity> getChildren(){
      return children;
   }
}
```
以上代码中，我们在服务端定义了一个业务对象MyEntity，并对外提供了一个查询服务函数`MyEntity__findList`，这个查询函数会返回MyEntity对象列表， MyEntity对象上的children属性为延迟加载属性，除非明确指定，否则它并不会自动返回给前端。而加载children属性时会触发MyEntityBizModel上的loadChildren函数。借助于这种设计，我们可以将一个庞大复杂的领域对象模型暴露为统一的RPC接口服务，而不用担心无关信息过多影响性能。


## 2.2 RPC over Message Queue



基于Nop平台实现的RPC服务都支持识别一个特殊的@selection参数，用于实现结果字段选择，例如`http://localhost/r/NopAuthUser__findList?@selection=userId,userName`表示返回的NopAuthUser列表中仅包含userId和userName字段。

# 二. 负载均衡设计

# 三. 取消执行

# 五. 灰度发布

# 六. 端到端的超时控制
