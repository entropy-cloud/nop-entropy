package io.nop.rpc.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.rpc.model.ApiMethodModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [38:14:0:0]/nop/schema/api.xdef <p>
 * 服务方法。接收ApiRequest<T>类型的请求，返回ApiResponse<R>类型的响应
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ApiMethodModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: description
     * 
     */
    private java.lang.String _description ;
    
    /**
     *  
     * xml name: displayName
     * 
     */
    private java.lang.String _displayName ;
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: requestMessage
     * 
     */
    private java.lang.String _requestMessage ;
    
    /**
     *  
     * xml name: responseMessage
     * 
     */
    private io.nop.core.type.IGenericType _responseMessage ;
    
    /**
     *  
     * xml name: streamRequest
     * 
     */
    private boolean _streamRequest  = false;
    
    /**
     *  
     * xml name: streamResponse
     * 
     */
    private boolean _streamResponse  = false;
    
    /**
     *  
     * xml name: tagSet
     * 
     */
    private java.util.Set<java.lang.String> _tagSet ;
    
    /**
     * 
     * xml name: description
     *  
     */
    
    public java.lang.String getDescription(){
      return _description;
    }

    
    public void setDescription(java.lang.String value){
        checkAllowChange();
        
        this._description = value;
           
    }

    
    /**
     * 
     * xml name: displayName
     *  
     */
    
    public java.lang.String getDisplayName(){
      return _displayName;
    }

    
    public void setDisplayName(java.lang.String value){
        checkAllowChange();
        
        this._displayName = value;
           
    }

    
    /**
     * 
     * xml name: name
     *  
     */
    
    public java.lang.String getName(){
      return _name;
    }

    
    public void setName(java.lang.String value){
        checkAllowChange();
        
        this._name = value;
           
    }

    
    /**
     * 
     * xml name: requestMessage
     *  
     */
    
    public java.lang.String getRequestMessage(){
      return _requestMessage;
    }

    
    public void setRequestMessage(java.lang.String value){
        checkAllowChange();
        
        this._requestMessage = value;
           
    }

    
    /**
     * 
     * xml name: responseMessage
     *  
     */
    
    public io.nop.core.type.IGenericType getResponseMessage(){
      return _responseMessage;
    }

    
    public void setResponseMessage(io.nop.core.type.IGenericType value){
        checkAllowChange();
        
        this._responseMessage = value;
           
    }

    
    /**
     * 
     * xml name: streamRequest
     *  
     */
    
    public boolean isStreamRequest(){
      return _streamRequest;
    }

    
    public void setStreamRequest(boolean value){
        checkAllowChange();
        
        this._streamRequest = value;
           
    }

    
    /**
     * 
     * xml name: streamResponse
     *  
     */
    
    public boolean isStreamResponse(){
      return _streamResponse;
    }

    
    public void setStreamResponse(boolean value){
        checkAllowChange();
        
        this._streamResponse = value;
           
    }

    
    /**
     * 
     * xml name: tagSet
     *  
     */
    
    public java.util.Set<java.lang.String> getTagSet(){
      return _tagSet;
    }

    
    public void setTagSet(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._tagSet = value;
           
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
        
        out.put("description",this.getDescription());
        out.put("displayName",this.getDisplayName());
        out.put("name",this.getName());
        out.put("requestMessage",this.getRequestMessage());
        out.put("responseMessage",this.getResponseMessage());
        out.put("streamRequest",this.isStreamRequest());
        out.put("streamResponse",this.isStreamResponse());
        out.put("tagSet",this.getTagSet());
    }

    public ApiMethodModel cloneInstance(){
        ApiMethodModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ApiMethodModel instance){
        super.copyTo(instance);
        
        instance.setDescription(this.getDescription());
        instance.setDisplayName(this.getDisplayName());
        instance.setName(this.getName());
        instance.setRequestMessage(this.getRequestMessage());
        instance.setResponseMessage(this.getResponseMessage());
        instance.setStreamRequest(this.isStreamRequest());
        instance.setStreamResponse(this.isStreamResponse());
        instance.setTagSet(this.getTagSet());
    }

    protected ApiMethodModel newInstance(){
        return (ApiMethodModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
