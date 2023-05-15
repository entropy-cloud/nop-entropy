package io.nop.rpc.interceptors;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.context.IContext;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.rpc.IRpcServiceInterceptor;
import io.nop.api.core.rpc.IRpcServiceInvocation;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.ApiHeaders;

import java.util.concurrent.CompletionStage;

import static io.nop.api.core.ApiErrors.ERR_API_TIMEOUT_EXPIRED;

public class ClientContextRpcServiceInterceptor implements IRpcServiceInterceptor {
    private boolean transferTimeout = true;
    private boolean transferTenant = true;
    private boolean transferUser = true;
    private boolean transferTrace = true;
    private boolean transferLocale = true;
    private boolean transferTimezone = true;

    public boolean isTransferTimezone() {
        return transferTimezone;
    }

    public void setTransferTimezone(boolean transferTimezone) {
        this.transferTimezone = transferTimezone;
    }

    public boolean isTransferTimeout() {
        return transferTimeout;
    }

    public void setTransferTimeout(boolean transferTimeout) {
        this.transferTimeout = transferTimeout;
    }

    public boolean isTransferTenant() {
        return transferTenant;
    }

    public void setTransferTenant(boolean transferTenant) {
        this.transferTenant = transferTenant;
    }

    public boolean isTransferUser() {
        return transferUser;
    }

    public void setTransferUser(boolean transferUser) {
        this.transferUser = transferUser;
    }

    public boolean isTransferTrace() {
        return transferTrace;
    }

    public void setTransferTrace(boolean transferTrace) {
        this.transferTrace = transferTrace;
    }

    public boolean isTransferLocale() {
        return transferLocale;
    }

    public void setTransferLocale(boolean transferLocale) {
        this.transferLocale = transferLocale;
    }

    @Override
    public CompletionStage<ApiResponse<?>> interceptAsync(IRpcServiceInvocation inv) {
        transfer(inv.getRequest());
        return inv.proceedAsync();
    }

    public ApiResponse<?> intercept(IRpcServiceInvocation inv) {
        transfer(inv.getRequest());
        return inv.proceed();
    }

    private void transfer(ApiRequest<?> request) {
        IContext context = ContextProvider.currentContext();
        if (context == null)
            return;

        if (transferLocale) {
            ApiHeaders.setLocale(request, context.getLocale());
        }

        if (transferTenant) {
            ApiHeaders.setTenant(request, context.getTenantId());
        }

        if (transferTimezone) {
            ApiHeaders.setTimeZone(request, context.getTimezone());
        }

        if (transferUser) {
            ApiHeaders.setUserId(request, context.getUserId());
        }

        if (transferTrace) {
            ApiHeaders.setTrace(request, context.getTraceId());
        }

        if (transferTimeout) {
            long expireTime = context.getCallExpireTime();
            if (expireTime > 0) {
                long timeout = expireTime - CoreMetrics.currentTimeMillis();
                if (timeout <= 0)
                    throw new NopException(ERR_API_TIMEOUT_EXPIRED);
                ApiHeaders.setTimeout(request, timeout);
            }
        }
    }
}
