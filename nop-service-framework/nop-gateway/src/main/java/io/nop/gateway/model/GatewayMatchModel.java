package io.nop.gateway.model;

import io.nop.gateway.model._gen._GatewayMatchModel;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.IEvalScope;

/**
 * GatewayMatchModel extends the generated _GatewayMatchModel.
 * 
 * NOTE: The passConditions() wrapper method is a temporary fix to make the
 * existing GatewayHandler.java compile. This will be properly refactored
 * in Task 5 when GatewayHandler is updated to use the new gateway.xdef structure.
 */
public class GatewayMatchModel extends _GatewayMatchModel{
    
    /**
     * Temporary wrapper for passConditions() to fix compilation.
     * In the new gateway.xdef structure, the "when" attribute is an IEvalFunction
     * that should be evaluated to determine if the route matches.
     * 
     * TODO: Remove this wrapper when GatewayHandler.java is refactored in Task 5.
     */
    public boolean passConditions(IEvalScope scope) {
        IEvalFunction when = getWhen();
        if (when == null) {
            return true;  // If no condition, pass by default
        }
        Object result = when.call0(null, scope);
        if (result instanceof Boolean) {
            return (Boolean) result;
        }
        // Handle other cases - convert to boolean
        return result != null && Boolean.TRUE.equals(result);
    }
    
    public GatewayMatchModel(){
 
    }
}
