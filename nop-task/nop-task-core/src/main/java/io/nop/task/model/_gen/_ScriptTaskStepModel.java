package io.nop.task.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.task.model.ScriptTaskStepModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [141:14:0:0]/nop/schema/task/task.xdef <p>
 * 使用ScriptCompilerRegistry中注册的IScriptCompiler执行脚本语言
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ScriptTaskStepModel extends io.nop.task.model.TaskStepModel {
    
    /**
     *  
     * xml name: lang
     * 
     */
    private java.lang.String _lang ;
    
    /**
     *  
     * xml name: source
     * 
     */
    private java.lang.String _source ;
    
    /**
     * 
     * xml name: lang
     *  
     */
    
    public java.lang.String getLang(){
      return _lang;
    }

    
    public void setLang(java.lang.String value){
        checkAllowChange();
        
        this._lang = value;
           
    }

    
    /**
     * 
     * xml name: source
     *  
     */
    
    public java.lang.String getSource(){
      return _source;
    }

    
    public void setSource(java.lang.String value){
        checkAllowChange();
        
        this._source = value;
           
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
        
        out.putNotNull("lang",this.getLang());
        out.putNotNull("source",this.getSource());
    }

    public ScriptTaskStepModel cloneInstance(){
        ScriptTaskStepModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ScriptTaskStepModel instance){
        super.copyTo(instance);
        
        instance.setLang(this.getLang());
        instance.setSource(this.getSource());
    }

    protected ScriptTaskStepModel newInstance(){
        return (ScriptTaskStepModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
