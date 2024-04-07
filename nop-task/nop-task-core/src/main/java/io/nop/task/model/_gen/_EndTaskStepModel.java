package io.nop.task.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.task.model.EndTaskStepModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [176:14:0:0]/nop/schema/task/task.xdef <p>
 * 退出整个工作流
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _EndTaskStepModel extends io.nop.task.model.TaskStepModel {
    
    /**
     *  
     * xml name: source
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _source ;
    
    /**
     * 
     * xml name: source
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getSource(){
      return _source;
    }

    
    public void setSource(io.nop.core.lang.eval.IEvalAction value){
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
        
        out.putNotNull("source",this.getSource());
    }

    public EndTaskStepModel cloneInstance(){
        EndTaskStepModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(EndTaskStepModel instance){
        super.copyTo(instance);
        
        instance.setSource(this.getSource());
    }

    protected EndTaskStepModel newInstance(){
        return (EndTaskStepModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
