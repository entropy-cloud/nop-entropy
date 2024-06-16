package io.nop.xui.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.xui.model.UiApiModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/xui/api.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _UiApiModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: adaptor
     * 对结果数据进行转换的函数，参数为(payload, response, api)，返回格式为response格式
     */
    private java.lang.String _adaptor ;
    
    /**
     *  
     * xml name: autoRefresh
     * 当所依赖的参数发生变化的时候，是否自动刷新。缺省为true
     */
    private java.lang.Boolean _autoRefresh ;
    
    /**
     *  
     * xml name: cache
     * 缓存时间，单位为毫秒。在一段时间内多次请求只会返回缓存的记录。
     */
    private java.lang.Integer _cache ;
    
    /**
     *  
     * xml name: convertKeyToPath
     * 是否将a.b.c这种路径重新组装为对象。缺省为true
     */
    private java.lang.Boolean _convertKeyToPath ;
    
    /**
     *  
     * xml name: data
     * 
     */
    private java.util.Map<java.lang.String,java.lang.Object> _data ;
    
    /**
     *  
     * xml name: dataType
     * 设置为form时，将按照application/x-www-form-urlencoded格式提交。
     * "dataType": "form-data"，可配置发送体格式为multipart/form-data。表单中包含文件类型时，自动使用这一类型
     */
    private java.lang.String _dataType ;
    
    /**
     *  
     * xml name: headers
     * 
     */
    private java.util.Map<java.lang.String,java.lang.Object> _headers ;
    
    /**
     *  
     * xml name: method
     * 
     */
    private java.lang.String _method ;
    
    /**
     *  
     * xml name: replaceData
     * 返回的数据是否替换掉当前的数据，默认为 false（即追加），设置为true就是完全替换当前数据。
     */
    private java.lang.Boolean _replaceData ;
    
    /**
     *  
     * xml name: requestAdaptor
     * 对请求数据执行转换的函数，参数为api
     */
    private java.lang.String _requestAdaptor ;
    
    /**
     *  
     * xml name: responseData
     * 对返回的结果数据进行转换
     */
    private java.util.Map<java.lang.String,java.lang.Object> _responseData ;
    
    /**
     *  
     * xml name: responseType
     * 配置为blob表示文件下载
     */
    private java.lang.String _responseType ;
    
    /**
     *  
     * xml name: sendOn
     * 仅当满足条件的时候才触发
     */
    private java.lang.String _sendOn ;
    
    /**
     *  
     * xml name: trackExpression
     * 如果开启了自动刷新，这里显式的配置需要跟踪的变量。例如"trackExpression": "${a}"
     */
    private java.lang.String _trackExpression ;
    
    /**
     *  
     * xml name: url
     * 
     */
    private java.lang.String _url ;
    
    /**
     *  
     * xml name: withFormData
     * 明确在data部分中包含form表单中的字段
     */
    private java.lang.Boolean _withFormData ;
    
    /**
     * 
     * xml name: adaptor
     *  对结果数据进行转换的函数，参数为(payload, response, api)，返回格式为response格式
     */
    
    public java.lang.String getAdaptor(){
      return _adaptor;
    }

    
    public void setAdaptor(java.lang.String value){
        checkAllowChange();
        
        this._adaptor = value;
           
    }

    
    /**
     * 
     * xml name: autoRefresh
     *  当所依赖的参数发生变化的时候，是否自动刷新。缺省为true
     */
    
    public java.lang.Boolean getAutoRefresh(){
      return _autoRefresh;
    }

    
    public void setAutoRefresh(java.lang.Boolean value){
        checkAllowChange();
        
        this._autoRefresh = value;
           
    }

    
    /**
     * 
     * xml name: cache
     *  缓存时间，单位为毫秒。在一段时间内多次请求只会返回缓存的记录。
     */
    
    public java.lang.Integer getCache(){
      return _cache;
    }

    
    public void setCache(java.lang.Integer value){
        checkAllowChange();
        
        this._cache = value;
           
    }

    
    /**
     * 
     * xml name: convertKeyToPath
     *  是否将a.b.c这种路径重新组装为对象。缺省为true
     */
    
    public java.lang.Boolean getConvertKeyToPath(){
      return _convertKeyToPath;
    }

    
    public void setConvertKeyToPath(java.lang.Boolean value){
        checkAllowChange();
        
        this._convertKeyToPath = value;
           
    }

    
    /**
     * 
     * xml name: data
     *  
     */
    
    public java.util.Map<java.lang.String,java.lang.Object> getData(){
      return _data;
    }

    
    public void setData(java.util.Map<java.lang.String,java.lang.Object> value){
        checkAllowChange();
        
        this._data = value;
           
    }

    
    public boolean hasData(){
        return this._data != null && !this._data.isEmpty();
    }
    
    /**
     * 
     * xml name: dataType
     *  设置为form时，将按照application/x-www-form-urlencoded格式提交。
     * "dataType": "form-data"，可配置发送体格式为multipart/form-data。表单中包含文件类型时，自动使用这一类型
     */
    
    public java.lang.String getDataType(){
      return _dataType;
    }

    
    public void setDataType(java.lang.String value){
        checkAllowChange();
        
        this._dataType = value;
           
    }

    
    /**
     * 
     * xml name: headers
     *  
     */
    
    public java.util.Map<java.lang.String,java.lang.Object> getHeaders(){
      return _headers;
    }

    
    public void setHeaders(java.util.Map<java.lang.String,java.lang.Object> value){
        checkAllowChange();
        
        this._headers = value;
           
    }

    
    public boolean hasHeaders(){
        return this._headers != null && !this._headers.isEmpty();
    }
    
    /**
     * 
     * xml name: method
     *  
     */
    
    public java.lang.String getMethod(){
      return _method;
    }

    
    public void setMethod(java.lang.String value){
        checkAllowChange();
        
        this._method = value;
           
    }

    
    /**
     * 
     * xml name: replaceData
     *  返回的数据是否替换掉当前的数据，默认为 false（即追加），设置为true就是完全替换当前数据。
     */
    
    public java.lang.Boolean getReplaceData(){
      return _replaceData;
    }

    
    public void setReplaceData(java.lang.Boolean value){
        checkAllowChange();
        
        this._replaceData = value;
           
    }

    
    /**
     * 
     * xml name: requestAdaptor
     *  对请求数据执行转换的函数，参数为api
     */
    
    public java.lang.String getRequestAdaptor(){
      return _requestAdaptor;
    }

    
    public void setRequestAdaptor(java.lang.String value){
        checkAllowChange();
        
        this._requestAdaptor = value;
           
    }

    
    /**
     * 
     * xml name: responseData
     *  对返回的结果数据进行转换
     */
    
    public java.util.Map<java.lang.String,java.lang.Object> getResponseData(){
      return _responseData;
    }

    
    public void setResponseData(java.util.Map<java.lang.String,java.lang.Object> value){
        checkAllowChange();
        
        this._responseData = value;
           
    }

    
    public boolean hasResponseData(){
        return this._responseData != null && !this._responseData.isEmpty();
    }
    
    /**
     * 
     * xml name: responseType
     *  配置为blob表示文件下载
     */
    
    public java.lang.String getResponseType(){
      return _responseType;
    }

    
    public void setResponseType(java.lang.String value){
        checkAllowChange();
        
        this._responseType = value;
           
    }

    
    /**
     * 
     * xml name: sendOn
     *  仅当满足条件的时候才触发
     */
    
    public java.lang.String getSendOn(){
      return _sendOn;
    }

    
    public void setSendOn(java.lang.String value){
        checkAllowChange();
        
        this._sendOn = value;
           
    }

    
    /**
     * 
     * xml name: trackExpression
     *  如果开启了自动刷新，这里显式的配置需要跟踪的变量。例如"trackExpression": "${a}"
     */
    
    public java.lang.String getTrackExpression(){
      return _trackExpression;
    }

    
    public void setTrackExpression(java.lang.String value){
        checkAllowChange();
        
        this._trackExpression = value;
           
    }

    
    /**
     * 
     * xml name: url
     *  
     */
    
    public java.lang.String getUrl(){
      return _url;
    }

    
    public void setUrl(java.lang.String value){
        checkAllowChange();
        
        this._url = value;
           
    }

    
    /**
     * 
     * xml name: withFormData
     *  明确在data部分中包含form表单中的字段
     */
    
    public java.lang.Boolean getWithFormData(){
      return _withFormData;
    }

    
    public void setWithFormData(java.lang.Boolean value){
        checkAllowChange();
        
        this._withFormData = value;
           
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
        
        out.putNotNull("adaptor",this.getAdaptor());
        out.putNotNull("autoRefresh",this.getAutoRefresh());
        out.putNotNull("cache",this.getCache());
        out.putNotNull("convertKeyToPath",this.getConvertKeyToPath());
        out.putNotNull("data",this.getData());
        out.putNotNull("dataType",this.getDataType());
        out.putNotNull("headers",this.getHeaders());
        out.putNotNull("method",this.getMethod());
        out.putNotNull("replaceData",this.getReplaceData());
        out.putNotNull("requestAdaptor",this.getRequestAdaptor());
        out.putNotNull("responseData",this.getResponseData());
        out.putNotNull("responseType",this.getResponseType());
        out.putNotNull("sendOn",this.getSendOn());
        out.putNotNull("trackExpression",this.getTrackExpression());
        out.putNotNull("url",this.getUrl());
        out.putNotNull("withFormData",this.getWithFormData());
    }

    public UiApiModel cloneInstance(){
        UiApiModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(UiApiModel instance){
        super.copyTo(instance);
        
        instance.setAdaptor(this.getAdaptor());
        instance.setAutoRefresh(this.getAutoRefresh());
        instance.setCache(this.getCache());
        instance.setConvertKeyToPath(this.getConvertKeyToPath());
        instance.setData(this.getData());
        instance.setDataType(this.getDataType());
        instance.setHeaders(this.getHeaders());
        instance.setMethod(this.getMethod());
        instance.setReplaceData(this.getReplaceData());
        instance.setRequestAdaptor(this.getRequestAdaptor());
        instance.setResponseData(this.getResponseData());
        instance.setResponseType(this.getResponseType());
        instance.setSendOn(this.getSendOn());
        instance.setTrackExpression(this.getTrackExpression());
        instance.setUrl(this.getUrl());
        instance.setWithFormData(this.getWithFormData());
    }

    protected UiApiModel newInstance(){
        return (UiApiModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
