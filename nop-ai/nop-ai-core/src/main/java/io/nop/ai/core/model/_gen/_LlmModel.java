package io.nop.ai.core.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ai.core.model.LlmModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/ai/llm.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _LlmModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: apiStyle
     * 
     */
    private io.nop.ai.core.model.ApiStyle _apiStyle ;
    
    /**
     *  
     * xml name: baseUrl
     * 服务的基础url，比如http://localhost:11342
     */
    private java.lang.String _baseUrl ;
    
    /**
     *  
     * xml name: buildHttpRequest
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _buildHttpRequest ;
    
    /**
     *  
     * xml name: chatUrl
     * 聊天功能的服务端点，比如 /api/chat
     */
    private java.lang.String _chatUrl ;
    
    /**
     *  
     * xml name: defaultModel
     * 
     */
    private java.lang.String _defaultModel ;
    
    /**
     *  
     * xml name: defaultRequestTimeout
     * 
     */
    private java.lang.Long _defaultRequestTimeout ;
    
    /**
     *  
     * xml name: embedUrl
     * 
     */
    private java.lang.String _embedUrl ;
    
    /**
     *  
     * xml name: generateUrl
     * 单次生成服务断点，比如 /api/generate
     */
    private java.lang.String _generateUrl ;
    
    /**
     *  
     * xml name: logMessage
     * 如果设置为true，则会打印出所有请求和响应消息
     */
    private boolean _logMessage  = true;
    
    /**
     *  
     * xml name: parseHttpResponse
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _parseHttpResponse ;
    
    /**
     *  
     * xml name: rateLimit
     * 为避免调用服务过于频繁，通过rateLimit指定每秒最多允许多少次请求。如果超过则会排队等待。
     */
    private java.lang.Double _rateLimit ;
    
    /**
     *  
     * xml name: request
     * 
     */
    private io.nop.ai.core.model.LlmRequestModel _request ;
    
    /**
     *  
     * xml name: response
     * 
     */
    private io.nop.ai.core.model.LlmResponseModel _response ;
    
    /**
     *  
     * xml name: supportModels
     * 大模型服务所支持的模型列表。通过defaultModel来指定缺省使用的模型
     */
    private java.util.Set<java.lang.String> _supportModels ;
    
    /**
     * 
     * xml name: apiStyle
     *  
     */
    
    public io.nop.ai.core.model.ApiStyle getApiStyle(){
      return _apiStyle;
    }

    
    public void setApiStyle(io.nop.ai.core.model.ApiStyle value){
        checkAllowChange();
        
        this._apiStyle = value;
           
    }

    
    /**
     * 
     * xml name: baseUrl
     *  服务的基础url，比如http://localhost:11342
     */
    
    public java.lang.String getBaseUrl(){
      return _baseUrl;
    }

    
    public void setBaseUrl(java.lang.String value){
        checkAllowChange();
        
        this._baseUrl = value;
           
    }

    
    /**
     * 
     * xml name: buildHttpRequest
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getBuildHttpRequest(){
      return _buildHttpRequest;
    }

    
    public void setBuildHttpRequest(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._buildHttpRequest = value;
           
    }

    
    /**
     * 
     * xml name: chatUrl
     *  聊天功能的服务端点，比如 /api/chat
     */
    
    public java.lang.String getChatUrl(){
      return _chatUrl;
    }

    
    public void setChatUrl(java.lang.String value){
        checkAllowChange();
        
        this._chatUrl = value;
           
    }

    
    /**
     * 
     * xml name: defaultModel
     *  
     */
    
    public java.lang.String getDefaultModel(){
      return _defaultModel;
    }

    
    public void setDefaultModel(java.lang.String value){
        checkAllowChange();
        
        this._defaultModel = value;
           
    }

    
    /**
     * 
     * xml name: defaultRequestTimeout
     *  
     */
    
    public java.lang.Long getDefaultRequestTimeout(){
      return _defaultRequestTimeout;
    }

    
    public void setDefaultRequestTimeout(java.lang.Long value){
        checkAllowChange();
        
        this._defaultRequestTimeout = value;
           
    }

    
    /**
     * 
     * xml name: embedUrl
     *  
     */
    
    public java.lang.String getEmbedUrl(){
      return _embedUrl;
    }

    
    public void setEmbedUrl(java.lang.String value){
        checkAllowChange();
        
        this._embedUrl = value;
           
    }

    
    /**
     * 
     * xml name: generateUrl
     *  单次生成服务断点，比如 /api/generate
     */
    
    public java.lang.String getGenerateUrl(){
      return _generateUrl;
    }

    
    public void setGenerateUrl(java.lang.String value){
        checkAllowChange();
        
        this._generateUrl = value;
           
    }

    
    /**
     * 
     * xml name: logMessage
     *  如果设置为true，则会打印出所有请求和响应消息
     */
    
    public boolean isLogMessage(){
      return _logMessage;
    }

    
    public void setLogMessage(boolean value){
        checkAllowChange();
        
        this._logMessage = value;
           
    }

    
    /**
     * 
     * xml name: parseHttpResponse
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getParseHttpResponse(){
      return _parseHttpResponse;
    }

    
    public void setParseHttpResponse(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._parseHttpResponse = value;
           
    }

    
    /**
     * 
     * xml name: rateLimit
     *  为避免调用服务过于频繁，通过rateLimit指定每秒最多允许多少次请求。如果超过则会排队等待。
     */
    
    public java.lang.Double getRateLimit(){
      return _rateLimit;
    }

    
    public void setRateLimit(java.lang.Double value){
        checkAllowChange();
        
        this._rateLimit = value;
           
    }

    
    /**
     * 
     * xml name: request
     *  
     */
    
    public io.nop.ai.core.model.LlmRequestModel getRequest(){
      return _request;
    }

    
    public void setRequest(io.nop.ai.core.model.LlmRequestModel value){
        checkAllowChange();
        
        this._request = value;
           
    }

    
    /**
     * 
     * xml name: response
     *  
     */
    
    public io.nop.ai.core.model.LlmResponseModel getResponse(){
      return _response;
    }

    
    public void setResponse(io.nop.ai.core.model.LlmResponseModel value){
        checkAllowChange();
        
        this._response = value;
           
    }

    
    /**
     * 
     * xml name: supportModels
     *  大模型服务所支持的模型列表。通过defaultModel来指定缺省使用的模型
     */
    
    public java.util.Set<java.lang.String> getSupportModels(){
      return _supportModels;
    }

    
    public void setSupportModels(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._supportModels = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._request = io.nop.api.core.util.FreezeHelper.deepFreeze(this._request);
            
           this._response = io.nop.api.core.util.FreezeHelper.deepFreeze(this._response);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("apiStyle",this.getApiStyle());
        out.putNotNull("baseUrl",this.getBaseUrl());
        out.putNotNull("buildHttpRequest",this.getBuildHttpRequest());
        out.putNotNull("chatUrl",this.getChatUrl());
        out.putNotNull("defaultModel",this.getDefaultModel());
        out.putNotNull("defaultRequestTimeout",this.getDefaultRequestTimeout());
        out.putNotNull("embedUrl",this.getEmbedUrl());
        out.putNotNull("generateUrl",this.getGenerateUrl());
        out.putNotNull("logMessage",this.isLogMessage());
        out.putNotNull("parseHttpResponse",this.getParseHttpResponse());
        out.putNotNull("rateLimit",this.getRateLimit());
        out.putNotNull("request",this.getRequest());
        out.putNotNull("response",this.getResponse());
        out.putNotNull("supportModels",this.getSupportModels());
    }

    public LlmModel cloneInstance(){
        LlmModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(LlmModel instance){
        super.copyTo(instance);
        
        instance.setApiStyle(this.getApiStyle());
        instance.setBaseUrl(this.getBaseUrl());
        instance.setBuildHttpRequest(this.getBuildHttpRequest());
        instance.setChatUrl(this.getChatUrl());
        instance.setDefaultModel(this.getDefaultModel());
        instance.setDefaultRequestTimeout(this.getDefaultRequestTimeout());
        instance.setEmbedUrl(this.getEmbedUrl());
        instance.setGenerateUrl(this.getGenerateUrl());
        instance.setLogMessage(this.isLogMessage());
        instance.setParseHttpResponse(this.getParseHttpResponse());
        instance.setRateLimit(this.getRateLimit());
        instance.setRequest(this.getRequest());
        instance.setResponse(this.getResponse());
        instance.setSupportModels(this.getSupportModels());
    }

    protected LlmModel newInstance(){
        return (LlmModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
