package io.nop.graphql.gateway.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [10:10:0:0]/nop/schema/gateway.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _GatewayRouteModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: handler
     * 匹配路由后可以直接响应。如果配置了serviceName且不是mock模式，则在执行handler之后会调用分布式RPC服务
     */
    private io.nop.core.lang.eval.IEvalAction _handler ;
    
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
    private io.nop.core.lang.eval.IEvalPredicate _match ;
    
    /**
     *  
     * xml name: mock
     * 如果设置为true，则只会调用handler返回，不会执行分布式RPC
     */
    private boolean _mock  = false;
    
    /**
     *  
     * xml name: on-error
     * 调用过程出现异常时会调用这里的代码，将异常对象包装为异常消息
     */
    private io.nop.core.lang.eval.IEvalAction _onError ;
    
    /**
     *  
     * xml name: on-path
     * 
     */
    private KeyedList<io.nop.graphql.gateway.model.GatewayOnPathModel> _onPaths = KeyedList.emptyList();
    
    /**
     *  
     * xml name: on-response
     * 对应分布式RPC返回的结果进行处理
     */
    private io.nop.core.lang.eval.IEvalAction _onResponse ;
    
    /**
     *  
     * xml name: rawResponse
     * 返回的response对象是否原始响应对象还是标准的ApiResponse对象。集成外部服务时会使用rawResponse
     */
    private boolean _rawResponse  = false;
    
    /**
     *  
     * xml name: serviceName
     * 通过RPC机制调用分布式RPC服务
     */
    private java.lang.String _serviceName ;
    
    /**
     * 
     * xml name: handler
     *  匹配路由后可以直接响应。如果配置了serviceName且不是mock模式，则在执行handler之后会调用分布式RPC服务
     */
    
    public io.nop.core.lang.eval.IEvalAction getHandler(){
      return _handler;
    }

    
    public void setHandler(io.nop.core.lang.eval.IEvalAction value){
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
    
    public io.nop.core.lang.eval.IEvalPredicate getMatch(){
      return _match;
    }

    
    public void setMatch(io.nop.core.lang.eval.IEvalPredicate value){
        checkAllowChange();
        
        this._match = value;
           
    }

    
    /**
     * 
     * xml name: mock
     *  如果设置为true，则只会调用handler返回，不会执行分布式RPC
     */
    
    public boolean isMock(){
      return _mock;
    }

    
    public void setMock(boolean value){
        checkAllowChange();
        
        this._mock = value;
           
    }

    
    /**
     * 
     * xml name: on-error
     *  调用过程出现异常时会调用这里的代码，将异常对象包装为异常消息
     */
    
    public io.nop.core.lang.eval.IEvalAction getOnError(){
      return _onError;
    }

    
    public void setOnError(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._onError = value;
           
    }

    
    /**
     * 
     * xml name: on-path
     *  
     */
    
    public java.util.List<io.nop.graphql.gateway.model.GatewayOnPathModel> getOnPaths(){
      return _onPaths;
    }

    
    public void setOnPaths(java.util.List<io.nop.graphql.gateway.model.GatewayOnPathModel> value){
        checkAllowChange();
        
        this._onPaths = KeyedList.fromList(value, io.nop.graphql.gateway.model.GatewayOnPathModel::getPath);
           
    }

    
    public io.nop.graphql.gateway.model.GatewayOnPathModel getOnPath(String name){
        return this._onPaths.getByKey(name);
    }

    public boolean hasOnPath(String name){
        return this._onPaths.containsKey(name);
    }

    public void addOnPath(io.nop.graphql.gateway.model.GatewayOnPathModel item) {
        checkAllowChange();
        java.util.List<io.nop.graphql.gateway.model.GatewayOnPathModel> list = this.getOnPaths();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.graphql.gateway.model.GatewayOnPathModel::getPath);
            setOnPaths(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_onPaths(){
        return this._onPaths.keySet();
    }

    public boolean hasOnPaths(){
        return !this._onPaths.isEmpty();
    }
    
    /**
     * 
     * xml name: on-response
     *  对应分布式RPC返回的结果进行处理
     */
    
    public io.nop.core.lang.eval.IEvalAction getOnResponse(){
      return _onResponse;
    }

    
    public void setOnResponse(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._onResponse = value;
           
    }

    
    /**
     * 
     * xml name: rawResponse
     *  返回的response对象是否原始响应对象还是标准的ApiResponse对象。集成外部服务时会使用rawResponse
     */
    
    public boolean isRawResponse(){
      return _rawResponse;
    }

    
    public void setRawResponse(boolean value){
        checkAllowChange();
        
        this._rawResponse = value;
           
    }

    
    /**
     * 
     * xml name: serviceName
     *  通过RPC机制调用分布式RPC服务
     */
    
    public java.lang.String getServiceName(){
      return _serviceName;
    }

    
    public void setServiceName(java.lang.String value){
        checkAllowChange();
        
        this._serviceName = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._onPaths = io.nop.api.core.util.FreezeHelper.deepFreeze(this._onPaths);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("handler",this.getHandler());
        out.put("id",this.getId());
        out.put("match",this.getMatch());
        out.put("mock",this.isMock());
        out.put("onError",this.getOnError());
        out.put("onPaths",this.getOnPaths());
        out.put("onResponse",this.getOnResponse());
        out.put("rawResponse",this.isRawResponse());
        out.put("serviceName",this.getServiceName());
    }
}
 // resume CPD analysis - CPD-ON
