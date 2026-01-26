package io.nop.ai.core.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ai.core.model.LlmModelModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/ai/llm.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _LlmModelModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: contextLenth
     * 
     */
    private java.lang.Integer _contextLenth ;
    
    /**
     *  
     * xml name: defaultMaxTokens
     * 
     */
    private java.lang.Integer _defaultMaxTokens ;
    
    /**
     *  
     * xml name: disableThinkingPrompt
     * 
     */
    private java.lang.String _disableThinkingPrompt ;
    
    /**
     *  
     * xml name: enableThinkingPrompt
     * 
     */
    private java.lang.String _enableThinkingPrompt ;
    
    /**
     *  
     * xml name: maxTokensLimit
     * 
     */
    private java.lang.Integer _maxTokensLimit ;
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: supportToolCalls
     * 
     */
    private java.lang.Boolean _supportToolCalls ;
    
    /**
     *  
     * xml name: thinkEndMarker
     * 
     */
    private java.lang.String _thinkEndMarker ;
    
    /**
     *  
     * xml name: thinkStartMarker
     * 
     */
    private java.lang.String _thinkStartMarker ;
    
    /**
     * 
     * xml name: contextLenth
     *  
     */
    
    public java.lang.Integer getContextLenth(){
      return _contextLenth;
    }

    
    public void setContextLenth(java.lang.Integer value){
        checkAllowChange();
        
        this._contextLenth = value;
           
    }

    
    /**
     * 
     * xml name: defaultMaxTokens
     *  
     */
    
    public java.lang.Integer getDefaultMaxTokens(){
      return _defaultMaxTokens;
    }

    
    public void setDefaultMaxTokens(java.lang.Integer value){
        checkAllowChange();
        
        this._defaultMaxTokens = value;
           
    }

    
    /**
     * 
     * xml name: disableThinkingPrompt
     *  
     */
    
    public java.lang.String getDisableThinkingPrompt(){
      return _disableThinkingPrompt;
    }

    
    public void setDisableThinkingPrompt(java.lang.String value){
        checkAllowChange();
        
        this._disableThinkingPrompt = value;
           
    }

    
    /**
     * 
     * xml name: enableThinkingPrompt
     *  
     */
    
    public java.lang.String getEnableThinkingPrompt(){
      return _enableThinkingPrompt;
    }

    
    public void setEnableThinkingPrompt(java.lang.String value){
        checkAllowChange();
        
        this._enableThinkingPrompt = value;
           
    }

    
    /**
     * 
     * xml name: maxTokensLimit
     *  
     */
    
    public java.lang.Integer getMaxTokensLimit(){
      return _maxTokensLimit;
    }

    
    public void setMaxTokensLimit(java.lang.Integer value){
        checkAllowChange();
        
        this._maxTokensLimit = value;
           
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
     * xml name: supportToolCalls
     *  
     */
    
    public java.lang.Boolean getSupportToolCalls(){
      return _supportToolCalls;
    }

    
    public void setSupportToolCalls(java.lang.Boolean value){
        checkAllowChange();
        
        this._supportToolCalls = value;
           
    }

    
    /**
     * 
     * xml name: thinkEndMarker
     *  
     */
    
    public java.lang.String getThinkEndMarker(){
      return _thinkEndMarker;
    }

    
    public void setThinkEndMarker(java.lang.String value){
        checkAllowChange();
        
        this._thinkEndMarker = value;
           
    }

    
    /**
     * 
     * xml name: thinkStartMarker
     *  
     */
    
    public java.lang.String getThinkStartMarker(){
      return _thinkStartMarker;
    }

    
    public void setThinkStartMarker(java.lang.String value){
        checkAllowChange();
        
        this._thinkStartMarker = value;
           
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
        
        out.putNotNull("contextLenth",this.getContextLenth());
        out.putNotNull("defaultMaxTokens",this.getDefaultMaxTokens());
        out.putNotNull("disableThinkingPrompt",this.getDisableThinkingPrompt());
        out.putNotNull("enableThinkingPrompt",this.getEnableThinkingPrompt());
        out.putNotNull("maxTokensLimit",this.getMaxTokensLimit());
        out.putNotNull("name",this.getName());
        out.putNotNull("supportToolCalls",this.getSupportToolCalls());
        out.putNotNull("thinkEndMarker",this.getThinkEndMarker());
        out.putNotNull("thinkStartMarker",this.getThinkStartMarker());
    }

    public LlmModelModel cloneInstance(){
        LlmModelModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(LlmModelModel instance){
        super.copyTo(instance);
        
        instance.setContextLenth(this.getContextLenth());
        instance.setDefaultMaxTokens(this.getDefaultMaxTokens());
        instance.setDisableThinkingPrompt(this.getDisableThinkingPrompt());
        instance.setEnableThinkingPrompt(this.getEnableThinkingPrompt());
        instance.setMaxTokensLimit(this.getMaxTokensLimit());
        instance.setName(this.getName());
        instance.setSupportToolCalls(this.getSupportToolCalls());
        instance.setThinkEndMarker(this.getThinkEndMarker());
        instance.setThinkStartMarker(this.getThinkStartMarker());
    }

    protected LlmModelModel newInstance(){
        return (LlmModelModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
