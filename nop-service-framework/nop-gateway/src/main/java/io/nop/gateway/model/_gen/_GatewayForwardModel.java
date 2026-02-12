package io.nop.gateway.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.gateway.model.GatewayForwardModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/gateway.xdef <p>
 * 路由到已有的route。执行后返回到本route继续执行onResponse和responseMapping。
 * 若源路由是streaming模式，则目标路由必须也为流式路由。不满足条件时网关会抛出异常。
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _GatewayForwardModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: dynamicRoute
     * 动态计算将要路由到的routeId。它会覆盖routeId属性设置
     */
    private io.nop.core.lang.eval.IEvalFunction _dynamicRoute ;
    
    /**
     *  
     * xml name: routeId
     * 
     */
    private java.lang.String _routeId ;
    
    /**
     * 
     * xml name: dynamicRoute
     *  动态计算将要路由到的routeId。它会覆盖routeId属性设置
     */
    
    public io.nop.core.lang.eval.IEvalFunction getDynamicRoute(){
      return _dynamicRoute;
    }

    
    public void setDynamicRoute(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._dynamicRoute = value;
           
    }

    
    /**
     * 
     * xml name: routeId
     *  
     */
    
    public java.lang.String getRouteId(){
      return _routeId;
    }

    
    public void setRouteId(java.lang.String value){
        checkAllowChange();
        
        this._routeId = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("dynamicRoute",this.getDynamicRoute());
        out.putNotNull("routeId",this.getRouteId());
    }

    public GatewayForwardModel cloneInstance(){
        GatewayForwardModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(GatewayForwardModel instance){
        super.copyTo(instance);
        
        instance.setDynamicRoute(this.getDynamicRoute());
        instance.setRouteId(this.getRouteId());
    }

    protected GatewayForwardModel newInstance(){
        return (GatewayForwardModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
