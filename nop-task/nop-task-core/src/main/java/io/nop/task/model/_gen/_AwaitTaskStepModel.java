package io.nop.task.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [177:14:0:0]/nop/schema/task/task.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _AwaitTaskStepModel extends io.nop.task.model.TaskStepModel {
    
    /**
     *  
     * xml name: depends
     * 
     */
    private java.util.Set<java.lang.String> _depends ;
    
    /**
     * 
     * xml name: depends
     *  
     */
    
    public java.util.Set<java.lang.String> getDepends(){
      return _depends;
    }

    
    public void setDepends(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._depends = value;
           
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
        
        out.put("depends",this.getDepends());
    }
}
 // resume CPD analysis - CPD-ON
