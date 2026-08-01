package io.nop.ai.core.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ai.core.model.LlmErrorMappingModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/ai/llm.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _LlmErrorMappingModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: classification
     * 
     */
    private io.nop.ai.core.model.ErrorClassification _classification ;
    
    /**
     *  
     * xml name: errorCodes
     * 
     */
    private java.util.Set<java.lang.String> _errorCodes ;
    
    /**
     *  
     * xml name: errorTypes
     * 
     */
    private java.util.Set<java.lang.String> _errorTypes ;
    
    /**
     *  
     * xml name: httpStatus
     * 
     */
    private java.util.Set<java.lang.String> _httpStatus ;
    
    /**
     *  
     * xml name: id
     * 
     */
    private java.lang.String _id ;
    
    /**
     *  
     * xml name: messagePattern
     * 
     */
    private java.lang.String _messagePattern ;
    
    /**
     *  
     * xml name: retryAfterPath
     * 
     */
    private java.lang.String _retryAfterPath ;
    
    /**
     * 
     * xml name: classification
     *  
     */
    
    public io.nop.ai.core.model.ErrorClassification getClassification(){
      return _classification;
    }

    
    public void setClassification(io.nop.ai.core.model.ErrorClassification value){
        checkAllowChange();
        
        this._classification = value;
           
    }

    
    /**
     * 
     * xml name: errorCodes
     *  
     */
    
    public java.util.Set<java.lang.String> getErrorCodes(){
      return _errorCodes;
    }

    
    public void setErrorCodes(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._errorCodes = value;
           
    }

    
    /**
     * 
     * xml name: errorTypes
     *  
     */
    
    public java.util.Set<java.lang.String> getErrorTypes(){
      return _errorTypes;
    }

    
    public void setErrorTypes(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._errorTypes = value;
           
    }

    
    /**
     * 
     * xml name: httpStatus
     *  
     */
    
    public java.util.Set<java.lang.String> getHttpStatus(){
      return _httpStatus;
    }

    
    public void setHttpStatus(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._httpStatus = value;
           
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
     * xml name: messagePattern
     *  
     */
    
    public java.lang.String getMessagePattern(){
      return _messagePattern;
    }

    
    public void setMessagePattern(java.lang.String value){
        checkAllowChange();
        
        this._messagePattern = value;
           
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
        
        out.putNotNull("classification",this.getClassification());
        out.putNotNull("errorCodes",this.getErrorCodes());
        out.putNotNull("errorTypes",this.getErrorTypes());
        out.putNotNull("httpStatus",this.getHttpStatus());
        out.putNotNull("id",this.getId());
        out.putNotNull("messagePattern",this.getMessagePattern());
        out.putNotNull("retryAfterPath",this.getRetryAfterPath());
    }

    public LlmErrorMappingModel cloneInstance(){
        LlmErrorMappingModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(LlmErrorMappingModel instance){
        super.copyTo(instance);
        
        instance.setClassification(this.getClassification());
        instance.setErrorCodes(this.getErrorCodes());
        instance.setErrorTypes(this.getErrorTypes());
        instance.setHttpStatus(this.getHttpStatus());
        instance.setId(this.getId());
        instance.setMessagePattern(this.getMessagePattern());
        instance.setRetryAfterPath(this.getRetryAfterPath());
    }

    protected LlmErrorMappingModel newInstance(){
        return (LlmErrorMappingModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
