package io.nop.gateway.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.gateway.model.GatewayRouteModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/gateway.xdef <p>
 * 整体执行过程如下：match => requestMapping => onRequest => invoke|forward => onResponse => responseMapping
 * 流式返回时：
 * [拦截器onStreamElement] → [路由onStreamElement] → [elementMapping]
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _GatewayRouteModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: errorRouteId
     * 如果配置了errorRouteId，当发生未被捕获的异常时路由到此route进行处理，它的response将作为response返回。
     */
    private java.lang.String _errorRouteId ;
    
    /**
     *  
     * xml name: forward
     * 路由到已有的route。执行后返回到本route继续执行onResponse和responseMapping。
     * 若源路由是streaming模式，则目标路由必须也为流式路由。不满足条件时网关会抛出异常。
     */
    private io.nop.gateway.model.GatewayForwardModel _forward ;
    
    /**
     *  
     * xml name: id
     * 
     */
    private java.lang.String _id ;
    
    /**
     *  
     * xml name: invoke
     * 如果指定了source，则直接执行source代码。否则调用分布式RPC。source/serviceName/url不允许同时为空
     */
    private io.nop.gateway.model.GatewayInvokeModel _invoke ;
    
    /**
     *  
     * xml name: match
     * 
     */
    private io.nop.gateway.model.GatewayMatchModel _match ;
    
    /**
     *  
     * xml name: onError
     * 整个路由过程中出现任何异常都会调用这里的处理函数。如果内部不处理，则可以继续抛出异常。
     */
    private io.nop.core.lang.eval.IEvalFunction _onError ;
    
    /**
     *  
     * xml name: onRequest
     * request是ApiRequest类型，包含headers,selection,data
     */
    private io.nop.core.lang.eval.IEvalFunction _onRequest ;
    
    /**
     *  
     * xml name: onResponse
     * response是ApiResponse类型，包含headers,selection,data
     */
    private io.nop.core.lang.eval.IEvalFunction _onResponse ;
    
    /**
     *  
     * xml name: rawResponse
     * 如果设置为true，则gateway返回给调用者的不是ApiResponse，而是ApiResponse的body
     */
    private java.lang.Boolean _rawResponse ;
    
    /**
     *  
     * xml name: requestMapping
     * request是ApiRequest类型，response是
     */
    private io.nop.gateway.model.GatewayMessageMappingModel _requestMapping ;
    
    /**
     *  
     * xml name: responseMapping
     * 如果进入streaming传送截断，这里的配置将失效
     */
    private io.nop.gateway.model.GatewayMessageMappingModel _responseMapping ;
    
    /**
     *  
     * xml name: streaming
     * 如果启用了streaming模式，则进入流式传输截断，onResponse/responseMapping/onError等部分不再使用。
     * 如果onRequest/invoke失败，整体没有进入流式传输，则仍然使用上面的配置
     */
    private io.nop.gateway.model.GatewayStreamingModel _streaming ;
    
    /**
     * 
     * xml name: errorRouteId
     *  如果配置了errorRouteId，当发生未被捕获的异常时路由到此route进行处理，它的response将作为response返回。
     */
    
    public java.lang.String getErrorRouteId(){
      return _errorRouteId;
    }

    
    public void setErrorRouteId(java.lang.String value){
        checkAllowChange();
        
        this._errorRouteId = value;
           
    }

    
    /**
     * 
     * xml name: forward
     *  路由到已有的route。执行后返回到本route继续执行onResponse和responseMapping。
     * 若源路由是streaming模式，则目标路由必须也为流式路由。不满足条件时网关会抛出异常。
     */
    
    public io.nop.gateway.model.GatewayForwardModel getForward(){
      return _forward;
    }

    
    public void setForward(io.nop.gateway.model.GatewayForwardModel value){
        checkAllowChange();
        
        this._forward = value;
           
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
     * xml name: invoke
     *  如果指定了source，则直接执行source代码。否则调用分布式RPC。source/serviceName/url不允许同时为空
     */
    
    public io.nop.gateway.model.GatewayInvokeModel getInvoke(){
      return _invoke;
    }

    
    public void setInvoke(io.nop.gateway.model.GatewayInvokeModel value){
        checkAllowChange();
        
        this._invoke = value;
           
    }

    
    /**
     * 
     * xml name: match
     *  
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
     *  整个路由过程中出现任何异常都会调用这里的处理函数。如果内部不处理，则可以继续抛出异常。
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
     *  request是ApiRequest类型，包含headers,selection,data
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
     *  response是ApiResponse类型，包含headers,selection,data
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
     * xml name: rawResponse
     *  如果设置为true，则gateway返回给调用者的不是ApiResponse，而是ApiResponse的body
     */
    
    public java.lang.Boolean getRawResponse(){
      return _rawResponse;
    }

    
    public void setRawResponse(java.lang.Boolean value){
        checkAllowChange();
        
        this._rawResponse = value;
           
    }

    
    /**
     * 
     * xml name: requestMapping
     *  request是ApiRequest类型，response是
     */
    
    public io.nop.gateway.model.GatewayMessageMappingModel getRequestMapping(){
      return _requestMapping;
    }

    
    public void setRequestMapping(io.nop.gateway.model.GatewayMessageMappingModel value){
        checkAllowChange();
        
        this._requestMapping = value;
           
    }

    
    /**
     * 
     * xml name: responseMapping
     *  如果进入streaming传送截断，这里的配置将失效
     */
    
    public io.nop.gateway.model.GatewayMessageMappingModel getResponseMapping(){
      return _responseMapping;
    }

    
    public void setResponseMapping(io.nop.gateway.model.GatewayMessageMappingModel value){
        checkAllowChange();
        
        this._responseMapping = value;
           
    }

    
    /**
     * 
     * xml name: streaming
     *  如果启用了streaming模式，则进入流式传输截断，onResponse/responseMapping/onError等部分不再使用。
     * 如果onRequest/invoke失败，整体没有进入流式传输，则仍然使用上面的配置
     */
    
    public io.nop.gateway.model.GatewayStreamingModel getStreaming(){
      return _streaming;
    }

    
    public void setStreaming(io.nop.gateway.model.GatewayStreamingModel value){
        checkAllowChange();
        
        this._streaming = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._forward = io.nop.api.core.util.FreezeHelper.deepFreeze(this._forward);
            
           this._invoke = io.nop.api.core.util.FreezeHelper.deepFreeze(this._invoke);
            
           this._match = io.nop.api.core.util.FreezeHelper.deepFreeze(this._match);
            
           this._requestMapping = io.nop.api.core.util.FreezeHelper.deepFreeze(this._requestMapping);
            
           this._responseMapping = io.nop.api.core.util.FreezeHelper.deepFreeze(this._responseMapping);
            
           this._streaming = io.nop.api.core.util.FreezeHelper.deepFreeze(this._streaming);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("errorRouteId",this.getErrorRouteId());
        out.putNotNull("forward",this.getForward());
        out.putNotNull("id",this.getId());
        out.putNotNull("invoke",this.getInvoke());
        out.putNotNull("match",this.getMatch());
        out.putNotNull("onError",this.getOnError());
        out.putNotNull("onRequest",this.getOnRequest());
        out.putNotNull("onResponse",this.getOnResponse());
        out.putNotNull("rawResponse",this.getRawResponse());
        out.putNotNull("requestMapping",this.getRequestMapping());
        out.putNotNull("responseMapping",this.getResponseMapping());
        out.putNotNull("streaming",this.getStreaming());
    }

    public GatewayRouteModel cloneInstance(){
        GatewayRouteModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(GatewayRouteModel instance){
        super.copyTo(instance);
        
        instance.setErrorRouteId(this.getErrorRouteId());
        instance.setForward(this.getForward());
        instance.setId(this.getId());
        instance.setInvoke(this.getInvoke());
        instance.setMatch(this.getMatch());
        instance.setOnError(this.getOnError());
        instance.setOnRequest(this.getOnRequest());
        instance.setOnResponse(this.getOnResponse());
        instance.setRawResponse(this.getRawResponse());
        instance.setRequestMapping(this.getRequestMapping());
        instance.setResponseMapping(this.getResponseMapping());
        instance.setStreaming(this.getStreaming());
    }

    protected GatewayRouteModel newInstance(){
        return (GatewayRouteModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
