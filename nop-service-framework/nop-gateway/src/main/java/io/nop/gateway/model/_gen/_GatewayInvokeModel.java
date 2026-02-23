package io.nop.gateway.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.gateway.model.GatewayInvokeModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/gateway.xdef <p>
 * 如果指定了source，则直接执行source代码。否则调用分布式RPC。source/serviceName/url不允许同时为空
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _GatewayInvokeModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: cancelMethod
     * 
     */
    private java.lang.String _cancelMethod ;
    
    /**
     *  
     * xml name: confirmMethod
     * 
     */
    private java.lang.String _confirmMethod ;
    
    /**
     *  
     * xml name: serviceMethod
     * 
     */
    private java.lang.String _serviceMethod ;
    
    /**
     *  
     * xml name: serviceName
     * 分布式RPC的服务名
     */
    private java.lang.String _serviceName ;
    
    /**
     *  
     * xml name: source
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _source ;
    
    /**
     *  
     * xml name: url
     * 如果没有指定serviceName，则需要指定远程路径，会直接调用这里的url，通过body传参
     */
    private io.nop.core.lang.eval.IEvalAction _url ;
    
    /**
     *  
     * xml name: wrapResponse
     * 
     */
    private java.lang.Boolean _wrapResponse ;
    
    /**
     * 
     * xml name: cancelMethod
     *  
     */
    
    public java.lang.String getCancelMethod(){
      return _cancelMethod;
    }

    
    public void setCancelMethod(java.lang.String value){
        checkAllowChange();
        
        this._cancelMethod = value;
           
    }

    
    /**
     * 
     * xml name: confirmMethod
     *  
     */
    
    public java.lang.String getConfirmMethod(){
      return _confirmMethod;
    }

    
    public void setConfirmMethod(java.lang.String value){
        checkAllowChange();
        
        this._confirmMethod = value;
           
    }

    
    /**
     * 
     * xml name: serviceMethod
     *  
     */
    
    public java.lang.String getServiceMethod(){
      return _serviceMethod;
    }

    
    public void setServiceMethod(java.lang.String value){
        checkAllowChange();
        
        this._serviceMethod = value;
           
    }

    
    /**
     * 
     * xml name: serviceName
     *  分布式RPC的服务名
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
     * xml name: source
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getSource(){
      return _source;
    }

    
    public void setSource(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._source = value;
           
    }

    
    /**
     * 
     * xml name: url
     *  如果没有指定serviceName，则需要指定远程路径，会直接调用这里的url，通过body传参
     */
    
    public io.nop.core.lang.eval.IEvalAction getUrl(){
      return _url;
    }

    
    public void setUrl(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._url = value;
           
    }

    
    /**
     * 
     * xml name: wrapResponse
     *  
     */
    
    public java.lang.Boolean getWrapResponse(){
      return _wrapResponse;
    }

    
    public void setWrapResponse(java.lang.Boolean value){
        checkAllowChange();
        
        this._wrapResponse = value;
           
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
        
        out.putNotNull("cancelMethod",this.getCancelMethod());
        out.putNotNull("confirmMethod",this.getConfirmMethod());
        out.putNotNull("serviceMethod",this.getServiceMethod());
        out.putNotNull("serviceName",this.getServiceName());
        out.putNotNull("source",this.getSource());
        out.putNotNull("url",this.getUrl());
        out.putNotNull("wrapResponse",this.getWrapResponse());
    }

    public GatewayInvokeModel cloneInstance(){
        GatewayInvokeModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(GatewayInvokeModel instance){
        super.copyTo(instance);
        
        instance.setCancelMethod(this.getCancelMethod());
        instance.setConfirmMethod(this.getConfirmMethod());
        instance.setServiceMethod(this.getServiceMethod());
        instance.setServiceName(this.getServiceName());
        instance.setSource(this.getSource());
        instance.setUrl(this.getUrl());
        instance.setWrapResponse(this.getWrapResponse());
    }

    protected GatewayInvokeModel newInstance(){
        return (GatewayInvokeModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
