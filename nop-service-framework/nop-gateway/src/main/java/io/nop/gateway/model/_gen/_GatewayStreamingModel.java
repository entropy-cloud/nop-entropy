package io.nop.gateway.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.gateway.model.GatewayStreamingModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/gateway.xdef <p>
 * 如果启用了streaming模式，则进入流式传输截断，onResponse/responseMapping/onError等部分不再使用。
 * 如果onRequest/invoke失败，整体没有进入流式传输，则仍然使用上面的配置
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _GatewayStreamingModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: contentType
     * SSE场景为 text/event-stream，JSON Lines 则为 application/x-ndjson
     */
    private java.lang.String _contentType ;
    
    /**
     *  
     * xml name: elementMapping
     * request是ApiRequest类型，response是
     */
    private io.nop.gateway.model.GatewayMessageMappingModel _elementMapping ;
    
    /**
     *  
     * xml name: enabled
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _enabled ;
    
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
     * 流式处理过程中如果出现问题，此时不能再通过通过onError来返回完整响应，则需要调用onStreamError返回特殊的错误信息
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
     * xml name: contentType
     *  SSE场景为 text/event-stream，JSON Lines 则为 application/x-ndjson
     */
    
    public java.lang.String getContentType(){
      return _contentType;
    }

    
    public void setContentType(java.lang.String value){
        checkAllowChange();
        
        this._contentType = value;
           
    }

    
    /**
     * 
     * xml name: elementMapping
     *  request是ApiRequest类型，response是
     */
    
    public io.nop.gateway.model.GatewayMessageMappingModel getElementMapping(){
      return _elementMapping;
    }

    
    public void setElementMapping(io.nop.gateway.model.GatewayMessageMappingModel value){
        checkAllowChange();
        
        this._elementMapping = value;
           
    }

    
    /**
     * 
     * xml name: enabled
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getEnabled(){
      return _enabled;
    }

    
    public void setEnabled(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._enabled = value;
           
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
     *  流式处理过程中如果出现问题，此时不能再通过通过onError来返回完整响应，则需要调用onStreamError返回特殊的错误信息
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
        
           this._elementMapping = io.nop.api.core.util.FreezeHelper.deepFreeze(this._elementMapping);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("contentType",this.getContentType());
        out.putNotNull("elementMapping",this.getElementMapping());
        out.putNotNull("enabled",this.getEnabled());
        out.putNotNull("onStreamComplete",this.getOnStreamComplete());
        out.putNotNull("onStreamElement",this.getOnStreamElement());
        out.putNotNull("onStreamError",this.getOnStreamError());
        out.putNotNull("onStreamStart",this.getOnStreamStart());
    }

    public GatewayStreamingModel cloneInstance(){
        GatewayStreamingModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(GatewayStreamingModel instance){
        super.copyTo(instance);
        
        instance.setContentType(this.getContentType());
        instance.setElementMapping(this.getElementMapping());
        instance.setEnabled(this.getEnabled());
        instance.setOnStreamComplete(this.getOnStreamComplete());
        instance.setOnStreamElement(this.getOnStreamElement());
        instance.setOnStreamError(this.getOnStreamError());
        instance.setOnStreamStart(this.getOnStreamStart());
    }

    protected GatewayStreamingModel newInstance(){
        return (GatewayStreamingModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
