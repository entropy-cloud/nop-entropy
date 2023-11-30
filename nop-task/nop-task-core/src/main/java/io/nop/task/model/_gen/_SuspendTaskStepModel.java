package io.nop.task.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [182:14:0:0]/nop/schema/task/task.xdef <p>
 * 挂起当前任务，等待手工触发继续执行
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _SuspendTaskStepModel extends io.nop.task.model.TaskStepModel {
    
    /**
     *  
     * xml name: resume-when
     * 
     */
    private io.nop.core.lang.eval.IEvalPredicate _resumeWhen ;
    
    /**
     * 
     * xml name: resume-when
     *  
     */
    
    public io.nop.core.lang.eval.IEvalPredicate getResumeWhen(){
      return _resumeWhen;
    }

    
    public void setResumeWhen(io.nop.core.lang.eval.IEvalPredicate value){
        checkAllowChange();
        
        this._resumeWhen = value;
           
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
        
        out.put("resumeWhen",this.getResumeWhen());
    }
}
 // resume CPD analysis - CPD-ON
