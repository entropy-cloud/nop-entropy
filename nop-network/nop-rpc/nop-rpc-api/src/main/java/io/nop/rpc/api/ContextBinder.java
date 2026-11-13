/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rpc.api;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.context.IContext;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.ApiHeaders;

import java.util.Set;

import static io.nop.api.core.ApiConfigs.CFG_RPC_PROPAGATE_HEADERS;

public class ContextBinder {
    private final IContext context;
    private final boolean created;

    public ContextBinder() {
        IContext context = ContextProvider.currentContext();
        // 4.3-C3-007：isClosed 残留强制替换（与 getOrCreateContext 既有 isClosed 检查对称的守卫，
        // 消费裁定③平台根因半边的 nop-rpc-api 止血面）——命中已关闭残留不复用、不复初始化
        // （created=false 不复初始化使请求头无法落盘的缺陷面随之闭合），按无 context 新建处理。
        // open context 复用语义不变（HTTP filter 链入口的既有附着不被二次建 context 分叉）。
        if (context != null && context.isClosed()) {
            context = null;
        }
        if (context == null) {
            this.context = ContextProvider.newContext();
            created = true;
        } else {
            this.context = context;
            created = false;
        }
    }

    public ContextBinder(ApiRequest<?> request) {
        this();
        init(request);
    }

    public ContextBinder init(ApiRequest<?> request) {
        if (created)
            initContext(context, request);
        return this;
    }

    public void close() {
        if (created) {
            context.close();
        }
    }

    void initContext(IContext context, ApiRequest<?> request) {
        String timezone = ApiHeaders.getTimeZone(request);
        String locale = ApiHeaders.getLocale(request);
        String tenantId = ApiHeaders.getTenant(request);
        long timeout = ApiHeaders.getTimeout(request, -1);

        context.setLocale(locale);
        context.setTenantId(tenantId);
        context.setTimezone(timezone);
        Set<String> headers = ConvertHelper.toCsvSet(CFG_RPC_PROPAGATE_HEADERS.get());
        context.setPropagateRpcHeaders(ApiHeaders.getHeaders(request.getHeaders(), headers));

        if (timeout > 0) {
            context.setCallExpireTime(CoreMetrics.currentTimeMillis() + timeout);
        }
    }
}
