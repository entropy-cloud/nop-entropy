package io.nop.datav.service;

import io.nop.core.context.IServiceContext;

/**
 * 从 IServiceContext 解析操作者 userName 的共享 helper。
 * Dashboard / FilterState 等 BizModel 复用此单一事实，避免重复私有拷贝。
 */
public final class NopDatavOperatorResolver {

    public static final String SYSTEM_OPERATOR = "system";

    private NopDatavOperatorResolver() {
    }

    /**
     * 优先从 userContext.getUserName() 获取，其次从 context.getContext().getUserName()，
     * 为空时返回 {@link #SYSTEM_OPERATOR}。
     */
    public static String resolveOperator(IServiceContext context) {
        String userName = null;
        if (context != null) {
            if (context.getUserContext() != null) {
                userName = context.getUserContext().getUserName();
            }
            if ((userName == null || userName.isEmpty()) && context.getContext() != null) {
                userName = context.getContext().getUserName();
            }
        }
        return userName == null || userName.isEmpty() ? SYSTEM_OPERATOR : userName;
    }
}
