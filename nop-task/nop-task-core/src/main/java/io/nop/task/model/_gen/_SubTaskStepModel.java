package io.nop.task.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.task.model.SubTaskStepModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [175:14:0:0]/nop/schema/task/task.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _SubTaskStepModel extends io.nop.task.model.TaskStepModel {
    
    /**
     *  
     * xml name: taskName
     * 
     */
    private java.lang.String _taskName ;
    
    /**
     * 
     * xml name: taskName
     *  
     */
    
    public java.lang.String getTaskName(){
      return _taskName;
    }

    
    public void setTaskName(java.lang.String value){
        checkAllowChange();
        
        this._taskName = value;
           
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
        
        out.put("taskName",this.getTaskName());
    }

    public SubTaskStepModel cloneInstance(){
        SubTaskStepModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(SubTaskStepModel instance){
        super.copyTo(instance);
        
        instance.setTaskName(this.getTaskName());
    }

    protected SubTaskStepModel newInstance(){
        return (SubTaskStepModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
