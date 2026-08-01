package io.nop.ai.core.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ai.core.model.LlmErrorResponseModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/ai/llm.xdef <p>
 * 告诉底层从错误响应体抽取哪些字段用于错误分类（对应 <response> 抽成功体字段）。
 * 沿用 prop-path（支持点号嵌套，如 OpenAI "error.type"）。
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _LlmErrorResponseModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: errorCodePath
     * 
     */
    private java.lang.String _errorCodePath ;
    
    /**
     *  
     * xml name: errorMessagePath
     * 
     */
    private java.lang.String _errorMessagePath ;
    
    /**
     *  
     * xml name: errorTypePath
     * 
     */
    private java.lang.String _errorTypePath ;
    
    /**
     *  
     * xml name: retryAfterPath
     * 
     */
    private java.lang.String _retryAfterPath ;
    
    /**
     * 
     * xml name: errorCodePath
     *  
     */
    
    public java.lang.String getErrorCodePath(){
      return _errorCodePath;
    }

    
    public void setErrorCodePath(java.lang.String value){
        checkAllowChange();
        
        this._errorCodePath = value;
           
    }

    
    /**
     * 
     * xml name: errorMessagePath
     *  
     */
    
    public java.lang.String getErrorMessagePath(){
      return _errorMessagePath;
    }

    
    public void setErrorMessagePath(java.lang.String value){
        checkAllowChange();
        
        this._errorMessagePath = value;
           
    }

    
    /**
     * 
     * xml name: errorTypePath
     *  
     */
    
    public java.lang.String getErrorTypePath(){
      return _errorTypePath;
    }

    
    public void setErrorTypePath(java.lang.String value){
        checkAllowChange();
        
        this._errorTypePath = value;
           
    }

    
    /**
     * 
     * xml name: retryAfterPath
     *  
     */
    
    public java.lang.String getRetryAfterPath(){
      return _retryAfterPath;
    }

    
    public void setRetryAfterPath(java.lang.String value){
        checkAllowChange();
        
        this._retryAfterPath = value;
           
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
        
        out.putNotNull("errorCodePath",this.getErrorCodePath());
        out.putNotNull("errorMessagePath",this.getErrorMessagePath());
        out.putNotNull("errorTypePath",this.getErrorTypePath());
        out.putNotNull("retryAfterPath",this.getRetryAfterPath());
    }

    public LlmErrorResponseModel cloneInstance(){
        LlmErrorResponseModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(LlmErrorResponseModel instance){
        super.copyTo(instance);
        
        instance.setErrorCodePath(this.getErrorCodePath());
        instance.setErrorMessagePath(this.getErrorMessagePath());
        instance.setErrorTypePath(this.getErrorTypePath());
        instance.setRetryAfterPath(this.getRetryAfterPath());
    }

    protected LlmErrorResponseModel newInstance(){
        return (LlmErrorResponseModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
