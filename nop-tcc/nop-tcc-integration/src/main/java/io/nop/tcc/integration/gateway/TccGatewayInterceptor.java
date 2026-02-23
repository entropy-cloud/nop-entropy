/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.tcc.integration.gateway;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.core.context.TccContext;
import io.nop.gateway.core.context.IGatewayContext;
import io.nop.gateway.core.interceptor.IGatewayInterceptor;
import io.nop.gateway.core.interceptor.IGatewayInvocation;
import io.nop.tcc.api.ITccEngine;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletionStage;

import static io.nop.api.core.context.ContextProvider.thenOnContext;

/**
 * Gateway拦截器，用于自动管理TCC分布式事务。
 * <p>
 * 使用invoke模式包装整个调用过程，通过{@link ITccEngine#runInTransactionAsync}实现事务管理。
 * <p>
 * 工作流程：
 * <ul>
 *   <li>如果ApiRequest中包含txnId，则参与该事务</li>
 *   <li>如果ApiRequest中不包含txnId，则新建事务</li>
 *   <li>事务信息通过ApiHeaders传递给下游服务</li>
 * </ul>
 */
public class TccGatewayInterceptor implements IGatewayInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(TccGatewayInterceptor.class);

    private ITccEngine tccEngine;

    /**
     * 默认事务分组名称
     */
    private String defaultTxnGroup = "default";

    /**
     * 是否在请求没有txnId时自动创建新事务
     */
    private boolean autoCreateTransaction = true;

    @Inject
    public void setTccEngine(ITccEngine tccEngine) {
        this.tccEngine = tccEngine;
    }

    public void setDefaultTxnGroup(String defaultTxnGroup) {
        this.defaultTxnGroup = defaultTxnGroup;
    }

    public void setAutoCreateTransaction(boolean autoCreateTransaction) {
        this.autoCreateTransaction = autoCreateTransaction;
    }

    @Override
    public CompletionStage<ApiResponse<?>> invoke(IGatewayInvocation invocation, ApiRequest<?> request, IGatewayContext svcCtx) {
        TccContext oldContext = TccContext.getCurrent();
        TccContext tccContext = TccContext.buildFromRequest(request);
        String txnGroup = null;
        String txnId = null;
        if (tccContext != null) {
            txnGroup = tccContext.getTxnGroup();
            txnId = tccContext.getTxnId();
            TccContext.setCurrent(tccContext);
        } else if (oldContext != null) {
            txnGroup = oldContext.getTxnGroup();
            txnId = oldContext.getTxnId();
        } else {
            txnGroup = defaultTxnGroup;
        }
        return thenOnContext(tccEngine.runInTransactionAsync(txnGroup, txnId, txn -> {
            return invocation.proceedInvoke(request, svcCtx);
        })).whenComplete((ret, err) -> {
            if (oldContext != null) {
                TccContext.setCurrent(oldContext);
            } else if (tccContext != null) {
                TccContext.removeCurrent(tccContext);
            }
        });
    }

}
