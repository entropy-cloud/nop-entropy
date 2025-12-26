package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartApiDataModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * REST API data source
 * 对应外部 API 数据获取
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartApiDataModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: body
     * 
     */
    private java.util.Map<java.lang.String,java.lang.Object> _body ;
    
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
     * xml name: refresh
     * 
     */
    private java.lang.Long _refresh ;
    
    /**
     *  
     * xml name: url
     * 
     */
    private java.lang.String _url ;
    
    /**
     * 
     * xml name: body
     *  
     */
    
    public java.util.Map<java.lang.String,java.lang.Object> getBody(){
      return _body;
    }

    
    public void setBody(java.util.Map<java.lang.String,java.lang.Object> value){
        checkAllowChange();
        
        this._body = value;
           
    }

    
    public boolean hasBody(){
        return this._body != null && !this._body.isEmpty();
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
     * xml name: refresh
     *  
     */
    
    public java.lang.Long getRefresh(){
      return _refresh;
    }

    
    public void setRefresh(java.lang.Long value){
        checkAllowChange();
        
        this._refresh = value;
           
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
        
        out.putNotNull("body",this.getBody());
        out.putNotNull("headers",this.getHeaders());
        out.putNotNull("method",this.getMethod());
        out.putNotNull("refresh",this.getRefresh());
        out.putNotNull("url",this.getUrl());
    }

    public ChartApiDataModel cloneInstance(){
        ChartApiDataModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartApiDataModel instance){
        super.copyTo(instance);
        
        instance.setBody(this.getBody());
        instance.setHeaders(this.getHeaders());
        instance.setMethod(this.getMethod());
        instance.setRefresh(this.getRefresh());
        instance.setUrl(this.getUrl());
    }

    protected ChartApiDataModel newInstance(){
        return (ChartApiDataModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
