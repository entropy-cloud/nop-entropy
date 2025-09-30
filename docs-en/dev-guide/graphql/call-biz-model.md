# Bypass the GraphQL Engine to Directly Call Methods in BizModel

Service methods in BizModel can be exposed as service functions via the NopGraphQL engine, and the methods on BizModel can be invoked through GraphQL, REST, gRPC, and other means.

Sometimes, during backend integrations, you may want to bypass the NopGraphQL engine and directly trigger methods on BizModel, for example within a step of a Spring workflow.

The Nop platform provides the helper class BizActionInvoker, which lets you directly invoke BizModel via its methods.

```java
public class BizActionInvoker {
  /**
   * Synchronously invoke a method on BizModel
   * @param bizObjName Business object name
   * @param bizAction  Service method name of the business object
   * @param request    Request object, typically a Map containing all parameters sent to the service method. It can also be a RequestBean object
   * @param selection  Optional GraphQL result field selection. Set to null if no selection is needed
   * @param context    Context object. You can directly new ServiceContextImpl()
   * @return Directly returns the result from the BizAction method; the result is not processed by GraphQL's dataLoader
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
        // For other cases, assume a transaction is required
        return txnTemplate.runInTransaction(null, TransactionPropagation.REQUIRED, txn -> {
          Object ret = bizObject.invoke(bizAction, request, selection, context);
          return FutureHelper.getResult(ret);
        });
      }
    });
  }

  /**
   * Invoke a method on BizModel via GraphQLEngine. It captures all exceptions and returns an ApiResponse. Internally it will automatically open a transaction environment and an OrmSession environment, and perform automatic transaction rollback
   */
  public static ApiResponse<?> invokeGraphQLSync(String bizObjName, String bizAction,
                                                 ApiRequest<?> request) {
    IGraphQLEngine graphQLEngine = BeanContainer.getBeanByType(IGraphQLEngine.class);
    IGraphQLExecutionContext gqlCtx = graphQLEngine.newRpcContext(null, bizObjName, request);
    return graphQLEngine.executeRpc(gqlCtx);
  }
}
```

* The invokeActionSync call directly triggers an action on the BizObject, which translates into invoking a method on BizModel. Unlike a GraphQL call, this approach does not perform GraphQL Selection on the returned result.

<!-- SOURCE_MD5:6fe7b1e5eb19a4a21d06ecf790e5276f-->
