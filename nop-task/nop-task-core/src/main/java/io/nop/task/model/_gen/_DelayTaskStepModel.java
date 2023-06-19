package io.nop.task.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [127:14:0:0]/nop/schema/task/task.xdef <p>
 * 延迟执行后续step
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _DelayTaskStepModel extends io.nop.task.model.TaskStepModel {
    
    /**
     *  
     * xml name: delay
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _delay ;
    
    /**
     * 
     * xml name: delay
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getDelay(){
      return _delay;
    }

    
    public void setDelay(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._delay = value;
           
    }

    

    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
        }
    }

    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("delay",this.getDelay());
    }
}
 // resume CPD analysis - CPD-ON
