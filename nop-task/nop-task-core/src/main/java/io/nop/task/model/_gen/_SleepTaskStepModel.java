package io.nop.task.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [130:14:0:0]/nop/schema/task/task.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _SleepTaskStepModel extends io.nop.task.model.TaskStepModel {
    
    /**
     *  
     * xml name: sleepMillisExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _sleepMillisExpr ;
    
    /**
     * 
     * xml name: sleepMillisExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getSleepMillisExpr(){
      return _sleepMillisExpr;
    }

    
    public void setSleepMillisExpr(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._sleepMillisExpr = value;
           
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
        
        out.put("sleepMillisExpr",this.getSleepMillisExpr());
    }
}
 // resume CPD analysis - CPD-ON
