# Skip GraphQL Engine and Directly Call BizModel Methods

BizModel service methods can be exposed as service functions via the NopGraphQL engine. These methods can be called using various protocols such as GraphQL, REST, or gRPC.

Sometimes, when integrating in the backend, you might want to skip the NopGraphQL engine and directly trigger methods on BizModel. For example, in a Spring workflow step.

The Nop platform provides a helper class called `BizActionInvoker`, which can be used to directly call methods on BizModel.


```java
public class BizActionInvoker {
  /**
   * Synchronously calls methods on BizModel
   * @param bizObjName business object name
   * @param bizAction method name of the service method on the business object
   * @param request request object, usually a Map structure containing all parameters sent to the service method. It can also be a RequestBean object
   * @param selection GraphQL execution time optional result field selection mechanism. If not needed, set to null
   * @param context context object. Can directly instantiate ServiceContextImpl()
   * @return directly returns the result of the BizAction method, without going through GraphQL dataLoader processing
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
        // Other cases assume transaction handling is required
        return txnTemplate.runInTransaction(null, TransactionPropagation.REQUIRED, txn -> {
          Object ret = bizObject.invoke(bizAction, request, selection, context);
          return FutureHelper.getResult(ret);
        });
      }
    });
  }

  /**
   * Calls methods on BizModel using GraphQLEngine. It captures all exceptions and returns an ApiResponse object. Internally, it opens transaction contexts and automatically rolls back in case of errors
   */
  public static ApiResponse<?> invokeGraphQLSync(String bizObjName, String bizAction,
                                                 ApiRequest<?> request) {
    IGraphQLEngine graphQLEngine = BeanContainer.getBeanByType(IGraphQLEngine.class);
    IGraphQLExecutionContext gqlCtx = graphQLEngine.newRpcContext(null, bizObjName, request);
    return graphQLEngine.executeRpc(gqlCtx);
  }
}

* invokeActionSync调用会直接触发BizObject上的action，它会转化为对BizModel上方法的调用。这种调用方式与GraphQL调用的区别在于不会对返回结果执行GraphQL Selection处理。
