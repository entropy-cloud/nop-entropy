/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.context;

import io.nop.api.core.util.ApiStringHelper;

import static io.nop.api.core.ApiConfigs.CFG_DEFAULT_TENANT_ID;

public class BaseContextProvider implements IContextProvider {
    private final static ThreadLocal<IContext> t_context = new ThreadLocal<>();

    public static ThreadLocal<IContext> contextHolder() {
        return t_context;
    }

    @Override
    public IContext currentContext() {
        IContext context = t_context.get();
        if (context != null && context.isClosed()) {
            // 平台根因守卫（CRA R1-04 裁定③，设计权威来源
            // ai-dev/design/concurrency/bindcancel-context-unification.md §3.3）：
            // 跨线程close残留的已关闭context不可观测——读点惰性清除ThreadLocal残留，
            // 与getOrCreateContext的isClosed检查构成对称双读点守卫
            t_context.remove();
            return null;
        }
        return context;
    }

    public static void clear() {
        t_context.remove();
    }

    @Override
    public void detachContext() {
        t_context.remove();
    }

    @Override
    public IContext getOrCreateContext() {
        IContext context = t_context.get();
        if (context == null || context.isClosed()) {
            return newContext(true);
        }
        return context;
    }

    @Override
    public IContext newContext(boolean attach) {
        IContext context = new BaseContext();
        String defaultTenantId = CFG_DEFAULT_TENANT_ID.get();
        if (!ApiStringHelper.isEmpty(defaultTenantId)) {
            context.setTenantId(defaultTenantId);
        }
        if (attach)
            t_context.set(context);
        return context;
    }

    @Override
    public void attachContext(IContext context) {
        t_context.set(context);
    }
}
