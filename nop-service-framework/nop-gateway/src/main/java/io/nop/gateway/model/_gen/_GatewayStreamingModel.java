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
     * xml name: bufferEnabled
     * 首段缓冲开关（W7 机制 A 落地，plan 2026-08-15-1116-3）：开启后
     * StreamingProcessor 缓冲层在前 N 个元素（bufferSize）或 T 毫秒（bufferTimeMs，
     * 先到者越窗）内不向下游转发；窗口内上游失败 → 经重执行回调（拦截器经
     * IGatewayContext attribute 注入）重订阅；缺省 false = 既有零缓冲直通（零回归）
     */
    private java.lang.Boolean _bufferEnabled ;
    
    /**
     *  
     * xml name: bufferSize
     * 首段缓冲窗口元素数 N（>=1；缺省 10）
     */
    private java.lang.Integer _bufferSize ;
    
    /**
     *  
     * xml name: bufferTimeMs
     * 首段缓冲窗口时长 T 毫秒（>=0；缺省 1000）
     */
    private java.lang.Long _bufferTimeMs ;
    
    /**
     *  
     * xml name: contentType
     * SSE场景为 text/event-stream，JSON Lines 则为 application/x-ndjson
     */
    private java.lang.String _contentType ;
    
    /**
     *  
     * xml name: elementMapping
     * 
     */
    private java.lang.String _elementMapping ;
    
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
     * xml name: bufferEnabled
     *  首段缓冲开关（W7 机制 A 落地，plan 2026-08-15-1116-3）：开启后
     * StreamingProcessor 缓冲层在前 N 个元素（bufferSize）或 T 毫秒（bufferTimeMs，
     * 先到者越窗）内不向下游转发；窗口内上游失败 → 经重执行回调（拦截器经
     * IGatewayContext attribute 注入）重订阅；缺省 false = 既有零缓冲直通（零回归）
     */
    
    public java.lang.Boolean getBufferEnabled(){
      return _bufferEnabled;
    }

    
    public void setBufferEnabled(java.lang.Boolean value){
        checkAllowChange();
        
        this._bufferEnabled = value;
           
    }

    
    /**
     * 
     * xml name: bufferSize
     *  首段缓冲窗口元素数 N（>=1；缺省 10）
     */
    
    public java.lang.Integer getBufferSize(){
      return _bufferSize;
    }

    
    public void setBufferSize(java.lang.Integer value){
        checkAllowChange();
        
        this._bufferSize = value;
           
    }

    
    /**
     * 
     * xml name: bufferTimeMs
     *  首段缓冲窗口时长 T 毫秒（>=0；缺省 1000）
     */
    
    public java.lang.Long getBufferTimeMs(){
      return _bufferTimeMs;
    }

    
    public void setBufferTimeMs(java.lang.Long value){
        checkAllowChange();
        
        this._bufferTimeMs = value;
           
    }

    
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
     *  
     */
    
    public java.lang.String getElementMapping(){
      return _elementMapping;
    }

    
    public void setElementMapping(java.lang.String value){
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
        
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("bufferEnabled",this.getBufferEnabled());
        out.putNotNull("bufferSize",this.getBufferSize());
        out.putNotNull("bufferTimeMs",this.getBufferTimeMs());
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
        
        instance.setBufferEnabled(this.getBufferEnabled());
        instance.setBufferSize(this.getBufferSize());
        instance.setBufferTimeMs(this.getBufferTimeMs());
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
