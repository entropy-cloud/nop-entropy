package io.nop.ai.core.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ai.core.model.LlmResponseModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/ai/llm.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _LlmResponseModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: completionTokensPath
     * 
     */
    private java.lang.String _completionTokensPath ;
    
    /**
     *  
     * xml name: contentPath
     * 
     */
    private java.lang.String _contentPath ;
    
    /**
     *  
     * xml name: errorPath
     * 
     */
    private java.lang.String _errorPath ;
    
    /**
     *  
     * xml name: promptCacheHitTokensPath
     * 
     */
    private java.lang.String _promptCacheHitTokensPath ;
    
    /**
     *  
     * xml name: promptCacheMissTokensPath
     * 
     */
    private java.lang.String _promptCacheMissTokensPath ;
    
    /**
     *  
     * xml name: promptTokensPath
     * 
     */
    private java.lang.String _promptTokensPath ;
    
    /**
     *  
     * xml name: reasoningContentPath
     * 
     */
    private java.lang.String _reasoningContentPath ;
    
    /**
     *  
     * xml name: rolePath
     * 
     */
    private java.lang.String _rolePath ;
    
    /**
     *  
     * xml name: statusPath
     * 
     */
    private java.lang.String _statusPath ;
    
    /**
     *  
     * xml name: toolCallsPath
     * 
     */
    private java.lang.String _toolCallsPath ;
    
    /**
     *  
     * xml name: totalTokensPath
     * 
     */
    private java.lang.String _totalTokensPath ;
    
    /**
     * 
     * xml name: completionTokensPath
     *  
     */
    
    public java.lang.String getCompletionTokensPath(){
      return _completionTokensPath;
    }

    
    public void setCompletionTokensPath(java.lang.String value){
        checkAllowChange();
        
        this._completionTokensPath = value;
           
    }

    
    /**
     * 
     * xml name: contentPath
     *  
     */
    
    public java.lang.String getContentPath(){
      return _contentPath;
    }

    
    public void setContentPath(java.lang.String value){
        checkAllowChange();
        
        this._contentPath = value;
           
    }

    
    /**
     * 
     * xml name: errorPath
     *  
     */
    
    public java.lang.String getErrorPath(){
      return _errorPath;
    }

    
    public void setErrorPath(java.lang.String value){
        checkAllowChange();
        
        this._errorPath = value;
           
    }

    
    /**
     * 
     * xml name: promptCacheHitTokensPath
     *  
     */
    
    public java.lang.String getPromptCacheHitTokensPath(){
      return _promptCacheHitTokensPath;
    }

    
    public void setPromptCacheHitTokensPath(java.lang.String value){
        checkAllowChange();
        
        this._promptCacheHitTokensPath = value;
           
    }

    
    /**
     * 
     * xml name: promptCacheMissTokensPath
     *  
     */
    
    public java.lang.String getPromptCacheMissTokensPath(){
      return _promptCacheMissTokensPath;
    }

    
    public void setPromptCacheMissTokensPath(java.lang.String value){
        checkAllowChange();
        
        this._promptCacheMissTokensPath = value;
           
    }

    
    /**
     * 
     * xml name: promptTokensPath
     *  
     */
    
    public java.lang.String getPromptTokensPath(){
      return _promptTokensPath;
    }

    
    public void setPromptTokensPath(java.lang.String value){
        checkAllowChange();
        
        this._promptTokensPath = value;
           
    }

    
    /**
     * 
     * xml name: reasoningContentPath
     *  
     */
    
    public java.lang.String getReasoningContentPath(){
      return _reasoningContentPath;
    }

    
    public void setReasoningContentPath(java.lang.String value){
        checkAllowChange();
        
        this._reasoningContentPath = value;
           
    }

    
    /**
     * 
     * xml name: rolePath
     *  
     */
    
    public java.lang.String getRolePath(){
      return _rolePath;
    }

    
    public void setRolePath(java.lang.String value){
        checkAllowChange();
        
        this._rolePath = value;
           
    }

    
    /**
     * 
     * xml name: statusPath
     *  
     */
    
    public java.lang.String getStatusPath(){
      return _statusPath;
    }

    
    public void setStatusPath(java.lang.String value){
        checkAllowChange();
        
        this._statusPath = value;
           
    }

    
    /**
     * 
     * xml name: toolCallsPath
     *  
     */
    
    public java.lang.String getToolCallsPath(){
      return _toolCallsPath;
    }

    
    public void setToolCallsPath(java.lang.String value){
        checkAllowChange();
        
        this._toolCallsPath = value;
           
    }

    
    /**
     * 
     * xml name: totalTokensPath
     *  
     */
    
    public java.lang.String getTotalTokensPath(){
      return _totalTokensPath;
    }

    
    public void setTotalTokensPath(java.lang.String value){
        checkAllowChange();
        
        this._totalTokensPath = value;
           
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
        
        out.putNotNull("completionTokensPath",this.getCompletionTokensPath());
        out.putNotNull("contentPath",this.getContentPath());
        out.putNotNull("errorPath",this.getErrorPath());
        out.putNotNull("promptCacheHitTokensPath",this.getPromptCacheHitTokensPath());
        out.putNotNull("promptCacheMissTokensPath",this.getPromptCacheMissTokensPath());
        out.putNotNull("promptTokensPath",this.getPromptTokensPath());
        out.putNotNull("reasoningContentPath",this.getReasoningContentPath());
        out.putNotNull("rolePath",this.getRolePath());
        out.putNotNull("statusPath",this.getStatusPath());
        out.putNotNull("toolCallsPath",this.getToolCallsPath());
        out.putNotNull("totalTokensPath",this.getTotalTokensPath());
    }

    public LlmResponseModel cloneInstance(){
        LlmResponseModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(LlmResponseModel instance){
        super.copyTo(instance);
        
        instance.setCompletionTokensPath(this.getCompletionTokensPath());
        instance.setContentPath(this.getContentPath());
        instance.setErrorPath(this.getErrorPath());
        instance.setPromptCacheHitTokensPath(this.getPromptCacheHitTokensPath());
        instance.setPromptCacheMissTokensPath(this.getPromptCacheMissTokensPath());
        instance.setPromptTokensPath(this.getPromptTokensPath());
        instance.setReasoningContentPath(this.getReasoningContentPath());
        instance.setRolePath(this.getRolePath());
        instance.setStatusPath(this.getStatusPath());
        instance.setToolCallsPath(this.getToolCallsPath());
        instance.setTotalTokensPath(this.getTotalTokensPath());
    }

    protected LlmResponseModel newInstance(){
        return (LlmResponseModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
