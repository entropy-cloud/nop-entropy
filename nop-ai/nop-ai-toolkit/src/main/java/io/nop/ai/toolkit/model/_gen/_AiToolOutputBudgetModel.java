package io.nop.ai.toolkit.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ai.toolkit.model.AiToolOutputBudgetModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/ai/tool/tool.xdef <p>
 * Plan 2297 (WI16): model-tunable output budget declaration. Optional —
 * absent = runtime defaults (8000 chars, spill allowed). max-chars is
 * the per-tool default budget; the model may override per call with the
 * reserved `maxOutputChars` argument (clamped to this declared value,
 * then to the global hard cap). spill=false disables the full-text
 * spill path for this tool (truncation-only degradation). Opt-in
 * metadata: NOT auto-injected into any tool's LLM-facing schema
 * (prefix-cache stability, plan 2297 前缀缓存影响评估).
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _AiToolOutputBudgetModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: max-chars
     * 
     */
    private java.lang.Integer _maxChars ;
    
    /**
     *  
     * xml name: spill
     * 
     */
    private boolean _spill  = true;
    
    /**
     * 
     * xml name: max-chars
     *  
     */
    
    public java.lang.Integer getMaxChars(){
      return _maxChars;
    }

    
    public void setMaxChars(java.lang.Integer value){
        checkAllowChange();
        
        this._maxChars = value;
           
    }

    
    /**
     * 
     * xml name: spill
     *  
     */
    
    public boolean isSpill(){
      return _spill;
    }

    
    public void setSpill(boolean value){
        checkAllowChange();
        
        this._spill = value;
           
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
        
        out.putNotNull("maxChars",this.getMaxChars());
        out.putNotNull("spill",this.isSpill());
    }

    public AiToolOutputBudgetModel cloneInstance(){
        AiToolOutputBudgetModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(AiToolOutputBudgetModel instance){
        super.copyTo(instance);
        
        instance.setMaxChars(this.getMaxChars());
        instance.setSpill(this.isSpill());
    }

    protected AiToolOutputBudgetModel newInstance(){
        return (AiToolOutputBudgetModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
