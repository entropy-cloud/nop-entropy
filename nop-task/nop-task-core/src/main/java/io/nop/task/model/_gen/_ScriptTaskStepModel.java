package io.nop.task.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [106:14:0:0]/nop/schema/task/task.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
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

    

    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
        }
    }

    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("lang",this.getLang());
        out.put("source",this.getSource());
    }
}
 // resume CPD analysis - CPD-ON
