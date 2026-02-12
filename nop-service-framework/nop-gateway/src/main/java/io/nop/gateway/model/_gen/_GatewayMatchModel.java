package io.nop.gateway.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.gateway.model.GatewayMatchModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/gateway.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _GatewayMatchModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: httpMethod
     * 
     */
    private java.util.Set<java.lang.String> _httpMethod ;
    
    /**
     *  
     * xml name: path
     * 对应REST请求链接，例如 /r/NopAuthUser__findPage
     */
    private java.lang.String _path ;
    
    /**
     *  
     * xml name: when
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _when ;
    
    /**
     * 
     * xml name: httpMethod
     *  
     */
    
    public java.util.Set<java.lang.String> getHttpMethod(){
      return _httpMethod;
    }

    
    public void setHttpMethod(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._httpMethod = value;
           
    }

    
    /**
     * 
     * xml name: path
     *  对应REST请求链接，例如 /r/NopAuthUser__findPage
     */
    
    public java.lang.String getPath(){
      return _path;
    }

    
    public void setPath(java.lang.String value){
        checkAllowChange();
        
        this._path = value;
           
    }

    
    /**
     * 
     * xml name: when
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getWhen(){
      return _when;
    }

    
    public void setWhen(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._when = value;
           
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
        
        out.putNotNull("httpMethod",this.getHttpMethod());
        out.putNotNull("path",this.getPath());
        out.putNotNull("when",this.getWhen());
    }

    public GatewayMatchModel cloneInstance(){
        GatewayMatchModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(GatewayMatchModel instance){
        super.copyTo(instance);
        
        instance.setHttpMethod(this.getHttpMethod());
        instance.setPath(this.getPath());
        instance.setWhen(this.getWhen());
    }

    protected GatewayMatchModel newInstance(){
        return (GatewayMatchModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
