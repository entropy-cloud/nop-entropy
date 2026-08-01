package io.nop.ai.core.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ai.core.model.LlmAccountModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/ai/llm.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _LlmAccountModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: apiKey
     * 
     */
    private java.lang.String _apiKey ;
    
    /**
     *  
     * xml name: baseUrl
     * 
     */
    private java.lang.String _baseUrl ;
    
    /**
     *  
     * xml name: id
     * 
     */
    private java.lang.String _id ;
    
    /**
     *  
     * xml name: quotaLimit
     * 
     */
    private java.lang.Long _quotaLimit ;
    
    /**
     *  
     * xml name: renewAt
     * 
     */
    private java.lang.String _renewAt ;
    
    /**
     * 
     * xml name: apiKey
     *  
     */
    
    public java.lang.String getApiKey(){
      return _apiKey;
    }

    
    public void setApiKey(java.lang.String value){
        checkAllowChange();
        
        this._apiKey = value;
           
    }

    
    /**
     * 
     * xml name: baseUrl
     *  
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
     * xml name: quotaLimit
     *  
     */
    
    public java.lang.Long getQuotaLimit(){
      return _quotaLimit;
    }

    
    public void setQuotaLimit(java.lang.Long value){
        checkAllowChange();
        
        this._quotaLimit = value;
           
    }

    
    /**
     * 
     * xml name: renewAt
     *  
     */
    
    public java.lang.String getRenewAt(){
      return _renewAt;
    }

    
    public void setRenewAt(java.lang.String value){
        checkAllowChange();
        
        this._renewAt = value;
           
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
        
        out.putNotNull("apiKey",this.getApiKey());
        out.putNotNull("baseUrl",this.getBaseUrl());
        out.putNotNull("id",this.getId());
        out.putNotNull("quotaLimit",this.getQuotaLimit());
        out.putNotNull("renewAt",this.getRenewAt());
    }

    public LlmAccountModel cloneInstance(){
        LlmAccountModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(LlmAccountModel instance){
        super.copyTo(instance);
        
        instance.setApiKey(this.getApiKey());
        instance.setBaseUrl(this.getBaseUrl());
        instance.setId(this.getId());
        instance.setQuotaLimit(this.getQuotaLimit());
        instance.setRenewAt(this.getRenewAt());
    }

    protected LlmAccountModel newInstance(){
        return (LlmAccountModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
