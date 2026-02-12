package io.nop.graphql.gateway.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.graphql.gateway.model.GatewayRouteModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/gateway.xdef <p>
 * 整体执行过程如下：matchOnPath  => match => requestMapping => onRequest => handler => onResponse => responseMapping
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _GatewayRouteModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: forward
     * 
     */
    private io.nop.graphql.gateway.model.GatewayForwardModel _forward ;
    
    /**
     *  
     * xml name: handler
     * 匹配路由后可以直接响应。如果配置了serviceName且不是mock模式，则在执行handler之后会调用分布式RPC服务
     */
    private io.nop.core.lang.eval.IEvalFunction _handler ;
    
    /**
     *  
     * xml name: id
     * 
     */
    private java.lang.String _id ;
    
    /**
     *  
     * xml name: match
     * 路由的动态匹配条件
     */
    private io.nop.core.lang.eval.IEvalFunction _match ;
    
    /**
     *  
     * xml name: matchOnPath
     * 
     */
    private KeyedList<io.nop.graphql.gateway.model.GatewayOnPathModel> _matchOnPaths = KeyedList.emptyList();
    
    /**
     *  
     * xml name: onError
     * 整个路由过程中出现任何异常都会调用这里的处理函数。如果内部不处理，则可以继续抛出异常
     */
    private io.nop.core.lang.eval.IEvalFunction _onError ;
    
    /**
     *  
     * xml name: onRequest
     * request是ApiRequest类型，包含headers,selection,body
     */
    private io.nop.core.lang.eval.IEvalFunction _onRequest ;
    
    /**
     *  
     * xml name: onResponse
     * response是ApiResponse类型，包含headers,selection,body
     */
    private io.nop.core.lang.eval.IEvalFunction _onResponse ;
    
    /**
     *  
     * xml name: requestMapping
     * request是ApiRequest类型，response是
     */
    private io.nop.graphql.gateway.model.GatewayMessageMappingModel _requestMapping ;
    
    /**
     *  
     * xml name: responseMapping
     * request是ApiRequest类型，response是
     */
    private io.nop.graphql.gateway.model.GatewayMessageMappingModel _responseMapping ;
    
    /**
     *  
     * xml name: serviceName
     * 
     */
    private java.lang.String _serviceName ;
    
    /**
     *  
     * xml name: tcc
     * 
     */
    private io.nop.graphql.gateway.model.GatewayTccModel _tcc ;
    
    /**
     * 
     * xml name: forward
     *  
     */
    
    public io.nop.graphql.gateway.model.GatewayForwardModel getForward(){
      return _forward;
    }

    
    public void setForward(io.nop.graphql.gateway.model.GatewayForwardModel value){
        checkAllowChange();
        
        this._forward = value;
           
    }

    
    /**
     * 
     * xml name: handler
     *  匹配路由后可以直接响应。如果配置了serviceName且不是mock模式，则在执行handler之后会调用分布式RPC服务
     */
    
    public io.nop.core.lang.eval.IEvalFunction getHandler(){
      return _handler;
    }

    
    public void setHandler(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._handler = value;
           
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
     *  路由的动态匹配条件
     */
    
    public io.nop.core.lang.eval.IEvalFunction getMatch(){
      return _match;
    }

    
    public void setMatch(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._match = value;
           
    }

    
    /**
     * 
     * xml name: matchOnPath
     *  
     */
    
    public java.util.List<io.nop.graphql.gateway.model.GatewayOnPathModel> getMatchOnPaths(){
      return _matchOnPaths;
    }

    
    public void setMatchOnPaths(java.util.List<io.nop.graphql.gateway.model.GatewayOnPathModel> value){
        checkAllowChange();
        
        this._matchOnPaths = KeyedList.fromList(value, io.nop.graphql.gateway.model.GatewayOnPathModel::getPath);
           
    }

    
    public io.nop.graphql.gateway.model.GatewayOnPathModel getMatchOnPath(String name){
        return this._matchOnPaths.getByKey(name);
    }

    public boolean hasMatchOnPath(String name){
        return this._matchOnPaths.containsKey(name);
    }

    public void addMatchOnPath(io.nop.graphql.gateway.model.GatewayOnPathModel item) {
        checkAllowChange();
        java.util.List<io.nop.graphql.gateway.model.GatewayOnPathModel> list = this.getMatchOnPaths();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.graphql.gateway.model.GatewayOnPathModel::getPath);
            setMatchOnPaths(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_matchOnPaths(){
        return this._matchOnPaths.keySet();
    }

    public boolean hasMatchOnPaths(){
        return !this._matchOnPaths.isEmpty();
    }
    
    /**
     * 
     * xml name: onError
     *  整个路由过程中出现任何异常都会调用这里的处理函数。如果内部不处理，则可以继续抛出异常
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
     *  request是ApiRequest类型，包含headers,selection,body
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
     *  response是ApiResponse类型，包含headers,selection,body
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
     * xml name: requestMapping
     *  request是ApiRequest类型，response是
     */
    
    public io.nop.graphql.gateway.model.GatewayMessageMappingModel getRequestMapping(){
      return _requestMapping;
    }

    
    public void setRequestMapping(io.nop.graphql.gateway.model.GatewayMessageMappingModel value){
        checkAllowChange();
        
        this._requestMapping = value;
           
    }

    
    /**
     * 
     * xml name: responseMapping
     *  request是ApiRequest类型，response是
     */
    
    public io.nop.graphql.gateway.model.GatewayMessageMappingModel getResponseMapping(){
      return _responseMapping;
    }

    
    public void setResponseMapping(io.nop.graphql.gateway.model.GatewayMessageMappingModel value){
        checkAllowChange();
        
        this._responseMapping = value;
           
    }

    
    /**
     * 
     * xml name: serviceName
     *  
     */
    
    public java.lang.String getServiceName(){
      return _serviceName;
    }

    
    public void setServiceName(java.lang.String value){
        checkAllowChange();
        
        this._serviceName = value;
           
    }

    
    /**
     * 
     * xml name: tcc
     *  
     */
    
    public io.nop.graphql.gateway.model.GatewayTccModel getTcc(){
      return _tcc;
    }

    
    public void setTcc(io.nop.graphql.gateway.model.GatewayTccModel value){
        checkAllowChange();
        
        this._tcc = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._forward = io.nop.api.core.util.FreezeHelper.deepFreeze(this._forward);
            
           this._matchOnPaths = io.nop.api.core.util.FreezeHelper.deepFreeze(this._matchOnPaths);
            
           this._requestMapping = io.nop.api.core.util.FreezeHelper.deepFreeze(this._requestMapping);
            
           this._responseMapping = io.nop.api.core.util.FreezeHelper.deepFreeze(this._responseMapping);
            
           this._tcc = io.nop.api.core.util.FreezeHelper.deepFreeze(this._tcc);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("forward",this.getForward());
        out.putNotNull("handler",this.getHandler());
        out.putNotNull("id",this.getId());
        out.putNotNull("match",this.getMatch());
        out.putNotNull("matchOnPaths",this.getMatchOnPaths());
        out.putNotNull("onError",this.getOnError());
        out.putNotNull("onRequest",this.getOnRequest());
        out.putNotNull("onResponse",this.getOnResponse());
        out.putNotNull("requestMapping",this.getRequestMapping());
        out.putNotNull("responseMapping",this.getResponseMapping());
        out.putNotNull("serviceName",this.getServiceName());
        out.putNotNull("tcc",this.getTcc());
    }

    public GatewayRouteModel cloneInstance(){
        GatewayRouteModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(GatewayRouteModel instance){
        super.copyTo(instance);
        
        instance.setForward(this.getForward());
        instance.setHandler(this.getHandler());
        instance.setId(this.getId());
        instance.setMatch(this.getMatch());
        instance.setMatchOnPaths(this.getMatchOnPaths());
        instance.setOnError(this.getOnError());
        instance.setOnRequest(this.getOnRequest());
        instance.setOnResponse(this.getOnResponse());
        instance.setRequestMapping(this.getRequestMapping());
        instance.setResponseMapping(this.getResponseMapping());
        instance.setServiceName(this.getServiceName());
        instance.setTcc(this.getTcc());
    }

    protected GatewayRouteModel newInstance(){
        return (GatewayRouteModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
