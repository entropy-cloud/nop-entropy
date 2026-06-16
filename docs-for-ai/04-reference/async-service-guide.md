# Nop 异步服务调用指南

> 高级特性，一般业务开发不需要使用。优先使用同步调用。

## 框架层异步（始终生效）

Nop 平台的所有 HTTP 入口（`/graphql`、`/r/`、`/p/`、`/px/`、`/jsonrpc`）在 HTTP 传输层**始终是异步的**，所有 `GraphQLWebService` 方法返回 `CompletionStage<T>`，不阻塞工作线程。这是框架自身的异步机制，与业务代码是否返回 `CompletionStage` 无关。

因此即使业务方法定义为同步（返回 `void` 或普通 POJO），HTTP 传输层已经在 Servlet 3.1 async / Quarkus reactive 模式下运行。

框架层异步与业务层异步的区别：

| 层次 | 异步方式 | 业务代码是否需要关心 |
|------|---------|-------------------|
| HTTP 传输 | `CompletionStage<T>` + 非阻塞 I/O | 不需要，始终生效 |
| 服务接口 | 业务方法返回 `CompletionStage<T>` | 按需使用 |

详细架构见 `../02-core-guides/rpc-and-distributed-rpc.md`。

## 同步调用（默认）

Service 接口直接使用业务 DTO 类型，不包装 `ApiRequest`/`ApiResponse`：

```java
@BizModel("PayService")
public interface PayService {
    @BizMutation("refund")
    PayRefundResponseBean refund(PayRefundRequestBean req);
}
```

## 异步调用（按需定义）

需要异步调用时，单独定义一个异步接口，从同步接口继承。异步接口使用底层 `ApiRequest`/`ApiResponse` 包装，并通过 `ICancelToken` 支持异步取消。通过相同注解匹配同一个 BizModel：

```java
// 异步接口（按需定义，从同步接口继承）
@BizModel("PayService")
public interface PayServiceAsync extends PayService {

    @BizMutation("refund")
    CompletionStage<ApiResponse<PayRefundResponseBean>> api_refund(
        ApiRequest<PayRefundRequestBean> req, ICancelToken cancelToken);
}
```

两个接口通过 `@BizMutation("refund")` 匹配同一个 服务方法 实现，只是调用形式不同。

## 注意事项

- 异步调用时必须检查 `ApiResponse` 返回值，不能忽略。 response的结构为 { status, code, msg, data}。code为错误码，response.isOk()是一个帮助函数，判断status是否等于0，msg一般是出错时的国际化文本消息。data是成功时的实际数据。
- response.get()是一个帮助函数，如果成功则返回值，如果失败，则将code和msg包装为 `NopRebuildException`
- 一般业务场景用同步调用，避免忘记检查返回值
- `ICancelToken` 仅在需要动态取消时使用，不是必需参数
