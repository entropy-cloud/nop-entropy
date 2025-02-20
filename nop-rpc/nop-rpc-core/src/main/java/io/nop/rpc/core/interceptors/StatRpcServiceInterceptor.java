package io.nop.rpc.core.interceptors;

import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.time.CoreMetrics;
import io.nop.core.stat.GlobalStatManager;
import io.nop.core.stat.IRpcClientStatManager;
import io.nop.core.stat.RpcClientStat;
import io.nop.rpc.api.IRpcServiceInterceptor;
import io.nop.rpc.api.IRpcServiceInvocation;

import java.util.concurrent.CompletionStage;

public class StatRpcServiceInterceptor implements IRpcServiceInterceptor {
    private final IRpcClientStatManager statManager;

    public StatRpcServiceInterceptor(IRpcClientStatManager statManager) {
        this.statManager = statManager;
    }

    public StatRpcServiceInterceptor() {
        this(GlobalStatManager.instance());
    }

    @Override
    public int order() {
        return HIGH_PRIORITY;
    }

    @Override
    public CompletionStage<ApiResponse<?>> interceptAsync(IRpcServiceInvocation inv) {
        long beginTime = CoreMetrics.nanoTime();
        RpcClientStat stat = statManager.getRpcClientStat(inv.getServiceName(), inv.getServiceMethod());
        return inv.proceedAsync().whenComplete((ret, err) -> {
            long duration = CoreMetrics.nanoTime() - beginTime;
            stat.addExecuteTime(duration);

            if (err != null) {
                stat.error(err);
            } else {
                stat.incrementExecuteSuccessCount();
            }
        });
    }
}