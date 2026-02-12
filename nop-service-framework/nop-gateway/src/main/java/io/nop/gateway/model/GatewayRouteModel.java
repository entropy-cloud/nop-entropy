/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.gateway.model;

import io.nop.gateway.model._gen._GatewayRouteModel;

/**
 * GatewayRouteModel extends the generated _GatewayRouteModel.
 * 
 * NOTE: The following wrapper methods are temporary fixes to make the
 * existing GatewayHandler.java compile. These will be properly refactored
 * in Task 5 when GatewayHandler is updated to use the new gateway.xdef structure.
 */
public class GatewayRouteModel extends _GatewayRouteModel{
    
    /**
     * Temporary wrapper for isRawResponse() to fix compilation.
     * The new gateway.xdef uses rawResponse as Boolean, and Boolean auto-unboxing
     * should work, but the compiler error suggests isRawResponse() method is expected.
     * 
     * TODO: Remove this wrapper when GatewayHandler.java is refactored in Task 5.
     */
    public boolean isRawResponse() {
        Boolean value = getRawResponse();
        return value != null && value;
    }
    
    /**
     * Temporary wrapper for isMock() to fix compilation.
     * In the new gateway.xdef structure, "mock" means having a source script
     * configured (invoke.source != null).
     * 
     * TODO: Remove this wrapper when GatewayHandler.java is refactored in Task 5.
     */
    public boolean isMock() {
        return getInvoke() != null && getInvoke().getSource() != null;
    }
    
    /**
     * Temporary wrapper for getServiceName() to fix compilation.
     * In the new gateway.xdef structure, service name is in invoke.serviceName.
     * 
     * TODO: Remove this wrapper when GatewayHandler.java is refactored in Task 5.
     */
    public String getServiceName() {
        GatewayInvokeModel invoke = getInvoke();
        return invoke != null ? invoke.getServiceName() : null;
    }
    
    /**
     * Temporary wrapper for getHandler() to fix compilation.
     * In the new gateway.xdef structure, handler is replaced by invoke.source.
     * Returns IEvalFunction to match the expected type.
     * 
     * TODO: Remove this wrapper when GatewayHandler.java is refactored in Task 5.
     */
    public io.nop.core.lang.eval.IEvalFunction getHandler() {
        GatewayInvokeModel invoke = getInvoke();
        return invoke != null ? invoke.getSource() : null;
    }
    
    public GatewayRouteModel(){
 
    }
}
