package io.nop.auth.api.utils;

import io.nop.api.core.ApiConstants;
import io.nop.api.core.auth.IUserContext;
import org.slf4j.MDC;

public class AuthMDCHelper {
    public static void bindMDC(IUserContext userContext) {
        if (userContext.getSessionId() != null)
            MDC.put(ApiConstants.MDC_NOP_SESSION, userContext.getSessionId());
        if (userContext.getTenantId() != null)
            MDC.put(ApiConstants.MDC_NOP_TENANT, userContext.getTenantId());
        MDC.put(ApiConstants.MDC_NOP_USER, userContext.getUserId());
    }

    public static void unbindMDC() {
        MDC.remove(ApiConstants.MDC_NOP_SESSION);
        MDC.remove(ApiConstants.MDC_NOP_USER);
        MDC.remove(ApiConstants.MDC_NOP_TENANT);
    }
}
