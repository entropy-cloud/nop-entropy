package io.nop.task.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [172:18:0:0]/nop/schema/task/task.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _TaskChooseOtherwiseModel extends io.nop.task.model.TaskStepsModel {
    
    /**
     *  
     * xml name: to
     * 
     */
    private java.lang.String _to ;
    
    /**
     * 
     * xml name: to
     *  
     */
    
    public java.lang.String getTo(){
      return _to;
    }

    
    public void setTo(java.lang.String value){
        checkAllowChange();
        
        this._to = value;
           
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
        
        out.put("to",this.getTo());
    }
}
 // resume CPD analysis - CPD-ON
