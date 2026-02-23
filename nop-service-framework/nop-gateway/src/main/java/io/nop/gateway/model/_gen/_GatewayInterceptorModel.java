package io.nop.gateway.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.gateway.model.GatewayInterceptorModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/gateway.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _GatewayInterceptorModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: bean
     * 可以在NopIoC容器中配置interceptor对应的bean
     */
    private java.lang.String _bean ;
    
    /**
     *  
     * xml name: id
     * 
     */
    private java.lang.String _id ;
    
    /**
     *  
     * xml name: match
     * 匹配条件才执行
     */
    private io.nop.gateway.model.GatewayMatchModel _match ;
    
    /**
     *  
     * xml name: onError
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _onError ;
    
    /**
     *  
     * xml name: onRequest
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _onRequest ;
    
    /**
     *  
     * xml name: onResponse
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _onResponse ;
    
    /**
     *  
     * xml name: onStreamComplete
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _onStreamComplete ;
    
    /**
     *  
     * xml name: onStreamElement
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _onStreamElement ;
    
    /**
     *  
     * xml name: onStreamError
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _onStreamError ;
    
    /**
     *  
     * xml name: onStreamStart
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _onStreamStart ;
    
    /**
     * 
     * xml name: bean
     *  可以在NopIoC容器中配置interceptor对应的bean
     */
    
    public java.lang.String getBean(){
      return _bean;
    }

    
    public void setBean(java.lang.String value){
        checkAllowChange();
        
        this._bean = value;
           
    }

    
    /**
     * 
     * xml name: id
     *  
     */
    
    public java.lang.String getId(){
      return _id;
    }

    
    public void setId(java.lang.String value){
        checkAllowChange();
        
        this._id = value;
           
    }

    
    /**
     * 
     * xml name: match
     *  匹配条件才执行
     */
    
    public io.nop.gateway.model.GatewayMatchModel getMatch(){
      return _match;
    }

    
    public void setMatch(io.nop.gateway.model.GatewayMatchModel value){
        checkAllowChange();
        
        this._match = value;
           
    }

    
    /**
     * 
     * xml name: onError
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getOnError(){
      return _onError;
    }

    
    public void setOnError(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._onError = value;
           
    }

    
    /**
     * 
     * xml name: onRequest
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getOnRequest(){
      return _onRequest;
    }

    
    public void setOnRequest(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._onRequest = value;
           
    }

    
    /**
     * 
     * xml name: onResponse
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getOnResponse(){
      return _onResponse;
    }

    
    public void setOnResponse(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._onResponse = value;
           
    }

    
    /**
     * 
     * xml name: onStreamComplete
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getOnStreamComplete(){
      return _onStreamComplete;
    }

    
    public void setOnStreamComplete(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._onStreamComplete = value;
           
    }

    
    /**
     * 
     * xml name: onStreamElement
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getOnStreamElement(){
      return _onStreamElement;
    }

    
    public void setOnStreamElement(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._onStreamElement = value;
           
    }

    
    /**
     * 
     * xml name: onStreamError
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getOnStreamError(){
      return _onStreamError;
    }

    
    public void setOnStreamError(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._onStreamError = value;
           
    }

    
    /**
     * 
     * xml name: onStreamStart
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getOnStreamStart(){
      return _onStreamStart;
    }

    
    public void setOnStreamStart(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._onStreamStart = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._match = io.nop.api.core.util.FreezeHelper.deepFreeze(this._match);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("bean",this.getBean());
        out.putNotNull("id",this.getId());
        out.putNotNull("match",this.getMatch());
        out.putNotNull("onError",this.getOnError());
        out.putNotNull("onRequest",this.getOnRequest());
        out.putNotNull("onResponse",this.getOnResponse());
        out.putNotNull("onStreamComplete",this.getOnStreamComplete());
        out.putNotNull("onStreamElement",this.getOnStreamElement());
        out.putNotNull("onStreamError",this.getOnStreamError());
        out.putNotNull("onStreamStart",this.getOnStreamStart());
    }

    public GatewayInterceptorModel cloneInstance(){
        GatewayInterceptorModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(GatewayInterceptorModel instance){
        super.copyTo(instance);
        
        instance.setBean(this.getBean());
        instance.setId(this.getId());
        instance.setMatch(this.getMatch());
        instance.setOnError(this.getOnError());
        instance.setOnRequest(this.getOnRequest());
        instance.setOnResponse(this.getOnResponse());
        instance.setOnStreamComplete(this.getOnStreamComplete());
        instance.setOnStreamElement(this.getOnStreamElement());
        instance.setOnStreamError(this.getOnStreamError());
        instance.setOnStreamStart(this.getOnStreamStart());
    }

    protected GatewayInterceptorModel newInstance(){
        return (GatewayInterceptorModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
