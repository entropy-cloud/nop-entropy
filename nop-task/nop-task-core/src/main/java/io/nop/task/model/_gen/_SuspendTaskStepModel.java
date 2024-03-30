package io.nop.task.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.task.model.SuspendTaskStepModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [205:14:0:0]/nop/schema/task/task.xdef <p>
 * 挂起当前任务，等待手工触发继续执行
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
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
        
        out.putNotNull("resumeWhen",this.getResumeWhen());
    }

    public SuspendTaskStepModel cloneInstance(){
        SuspendTaskStepModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(SuspendTaskStepModel instance){
        super.copyTo(instance);
        
        instance.setResumeWhen(this.getResumeWhen());
    }

    protected SuspendTaskStepModel newInstance(){
        return (SuspendTaskStepModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
