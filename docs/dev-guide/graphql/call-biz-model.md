# 跳过GraphQL引擎直接调用BizModel中的方法

BizModel中的服务方法可以通过NopGraphQL引擎对外暴露为服务函数，可以通过GraphQL、REST和gRPC等多种方式调用BizModel上的方法。

有的时候在后台集成使用的时候，可能会希望跳过NopGraphQL引擎，直接触发BizModel上的方法，比如说在Spring的工作流步骤中触发。

Nop平台提供了BizActionInvoker这个帮助类，通过它上面的方法来直接调用BizModel。

```java
public class BizActionInvoker {
  /**
   * 同步调用BizModel上的方法
   * @param bizObjName 业务对象名
   * @param bizAction  业务对象的服务方法名
   * @param request    请求对象，一般情况下为Map结构，包含所有发送给服务方法的参数。也可以是RequestBean对象
   * @param selection  GraphQL执行时可选的结果字段选择机制。如果不需要选择，这里可以设置为null
   * @param context    上下文对象。可以直接new ServiceContextImpl()
   * @return 直接返回BizAction方法的返回结果，这里的结果没有经过GraphQL的dataLoader处理。
   */
  public static Object invokeActionSync(String bizObjName, String bizAction, Object request,
                                        FieldSelectionBean selection, IServiceContext context) {
    IBizObjectManager bizObjectManager = BeanContainer.getBeanByType(IBizObjectManager.class);
    IBizObject bizObject = bizObjectManager.getBizObject(bizObjName);
    GraphQLOperationType opType = bizObject.getOperationType(bizAction);
    IOrmTemplate ormTemplate = BeanContainer.getBeanByType(IOrmTemplate.class);
    ITransactionTemplate txnTemplate = BeanContainer.getBeanByType(ITransactionTemplate.class);

    return ormTemplate.runInSession(session -> {
      if (opType == GraphQLOperationType.query) {
        Object ret = bizObject.invoke(bizAction, request, selection, context);
        return FutureHelper.getResult(ret);
      } else {
        // 其他情况下假设都需要事务处理
        return txnTemplate.runInTransaction(null, TransactionPropagation.REQUIRED, txn -> {
          Object ret = bizObject.invoke(bizAction, request, selection, context);
          return FutureHelper.getResult(ret);
        });
      }
    });
  }

  /**
   * 通过GraphQLEngine调用BizModel上的方法。它会捕获所有异常，返回ApiResponse对象。内部会自动打开事务环境和OrmSession环境，并自动实现事务回滚
   */
  public static ApiResponse<?> invokeGraphQLSync(String bizObjName, String bizAction,
                                                 ApiRequest<?> request) {
    IGraphQLEngine graphQLEngine = BeanContainer.getBeanByType(IGraphQLEngine.class);
    IGraphQLExecutionContext gqlCtx = graphQLEngine.newRpcContext(null, bizObjName, request);
    return graphQLEngine.executeRpc(gqlCtx);
  }
}
```

* invokeActionSync调用会直接触发BizObject上的action，它会转化为对BizModel上方法的调用。这种调用方式与GraphQL调用的区别在于不会对返回结果执行GraphQL Selection处理。

