package io.nop.biz.service;

import io.nop.api.core.annotations.txn.TransactionPropagation;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.api.core.util.FutureHelper;
import io.nop.biz.api.IBizObject;
import io.nop.biz.api.IBizObjectManager;
import io.nop.core.context.IServiceContext;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.orm.IOrmTemplate;

import java.util.concurrent.CompletionStage;

/**
 * 提供了直接调用BizObject上的action动作的方法。比如在Spring的工作流引擎中可以绕过NopGraphQL引擎调用BizModel上的方法
 */
public class BizActionInvoker {
    /**
     * 同步调用BizModel上的方法
     *
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

    public static CompletionStage<Object> invokeActionAsync(String bizObjName, String bizAction, Object request,
                                                            FieldSelectionBean selection, IServiceContext context) {
        IBizObjectManager bizObjectManager = BeanContainer.getBeanByType(IBizObjectManager.class);
        IBizObject bizObject = bizObjectManager.getBizObject(bizObjName);
        GraphQLOperationType opType = bizObject.getOperationType(bizAction);
        IOrmTemplate ormTemplate = BeanContainer.getBeanByType(IOrmTemplate.class);
        ITransactionTemplate txnTemplate = BeanContainer.getBeanByType(ITransactionTemplate.class);

        return ormTemplate.runInSessionAsync(session -> {
            if (opType == GraphQLOperationType.query) {
                Object ret = bizObject.invoke(bizAction, request, selection, context);
                return FutureHelper.toCompletionStage(ret);
            } else {
                // 其他情况下假设都需要事务处理
                return txnTemplate.runInTransactionAsync(null, TransactionPropagation.REQUIRED, txn -> {
                    Object ret = bizObject.invoke(bizAction, request, selection, context);
                    return FutureHelper.toCompletionStage(ret);
                });
            }
        });
    }

    /**
     * 通过GraphQLEngine调用BizModel上的方法。它会捕获所有异常，返回ApiResponse对象。内部会自动打开事务环境和OrmSession环境，并自动实现事务回滚
     *
     * @param bizObjName 业务对象名
     * @param bizAction  业务方法名
     * @param request    包含headers, selection和实际的请求对象数据
     * @return 结果对象
     */
    public static CompletionStage<ApiResponse<?>> invokeGraphQLAsync(String bizObjName, String bizAction,
                                                                     ApiRequest<?> request) {
        IGraphQLEngine graphQLEngine = BeanContainer.getBeanByType(IGraphQLEngine.class);
        String realBizName = bizObjName + "__" + bizAction;
        IGraphQLExecutionContext gqlCtx = graphQLEngine.newRpcContext(null, realBizName, request);
        return graphQLEngine.executeRpcAsync(gqlCtx);
    }

    public static ApiResponse<?> invokeGraphQLSync(String bizObjName, String bizAction,
                                                   ApiRequest<?> request) {
        IGraphQLEngine graphQLEngine = BeanContainer.getBeanByType(IGraphQLEngine.class);
        String realBizName = bizObjName + "__" + bizAction;
        IGraphQLExecutionContext gqlCtx = graphQLEngine.newRpcContext(null, realBizName, request);
        return graphQLEngine.executeRpc(gqlCtx);
    }
}
