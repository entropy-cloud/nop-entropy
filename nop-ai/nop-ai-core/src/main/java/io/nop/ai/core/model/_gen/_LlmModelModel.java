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
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
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
        
        out.putNotNull("disableThinkingPrompt",this.getDisableThinkingPrompt());
        out.putNotNull("enableThinkingPrompt",this.getEnableThinkingPrompt());
        out.putNotNull("name",this.getName());
    }

    public LlmModelModel cloneInstance(){
        LlmModelModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(LlmModelModel instance){
        super.copyTo(instance);
        
        instance.setDisableThinkingPrompt(this.getDisableThinkingPrompt());
        instance.setEnableThinkingPrompt(this.getEnableThinkingPrompt());
        instance.setName(this.getName());
    }

    protected LlmModelModel newInstance(){
        return (LlmModelModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
