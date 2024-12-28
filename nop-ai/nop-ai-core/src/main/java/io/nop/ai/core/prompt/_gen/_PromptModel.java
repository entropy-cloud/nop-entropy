package io.nop.ai.core.prompt._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ai.core.prompt.PromptModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/ai/prompt.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _PromptModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: template
     * 
     */
    private io.nop.core.resource.tpl.ITextTemplateOutput _template ;
    
    /**
     *  
     * xml name: vars
     * 
     */
    private KeyedList<io.nop.ai.core.prompt.PromptVarModel> _vars = KeyedList.emptyList();
    
    /**
     * 
     * xml name: template
     *  
     */
    
    public io.nop.core.resource.tpl.ITextTemplateOutput getTemplate(){
      return _template;
    }

    
    public void setTemplate(io.nop.core.resource.tpl.ITextTemplateOutput value){
        checkAllowChange();
        
        this._template = value;
           
    }

    
    /**
     * 
     * xml name: vars
     *  
     */
    
    public java.util.List<io.nop.ai.core.prompt.PromptVarModel> getVars(){
      return _vars;
    }

    
    public void setVars(java.util.List<io.nop.ai.core.prompt.PromptVarModel> value){
        checkAllowChange();
        
        this._vars = KeyedList.fromList(value, io.nop.ai.core.prompt.PromptVarModel::getName);
           
    }

    
    public io.nop.ai.core.prompt.PromptVarModel getVar(String name){
        return this._vars.getByKey(name);
    }

    public boolean hasVar(String name){
        return this._vars.containsKey(name);
    }

    public void addVar(io.nop.ai.core.prompt.PromptVarModel item) {
        checkAllowChange();
        java.util.List<io.nop.ai.core.prompt.PromptVarModel> list = this.getVars();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.ai.core.prompt.PromptVarModel::getName);
            setVars(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_vars(){
        return this._vars.keySet();
    }

    public boolean hasVars(){
        return !this._vars.isEmpty();
    }
    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._vars = io.nop.api.core.util.FreezeHelper.deepFreeze(this._vars);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("template",this.getTemplate());
        out.putNotNull("vars",this.getVars());
    }

    public PromptModel cloneInstance(){
        PromptModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(PromptModel instance){
        super.copyTo(instance);
        
        instance.setTemplate(this.getTemplate());
        instance.setVars(this.getVars());
    }

    protected PromptModel newInstance(){
        return (PromptModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
