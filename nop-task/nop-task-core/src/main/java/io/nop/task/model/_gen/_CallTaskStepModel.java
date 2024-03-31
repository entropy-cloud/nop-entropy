package io.nop.task.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.task.model.CallTaskStepModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [233:14:0:0]/nop/schema/task/task.xdef <p>
 * 调用子任务
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _CallTaskStepModel extends io.nop.task.model.TaskStepModel {
    
    /**
     *  
     * xml name: taskName
     * 
     */
    private java.lang.String _taskName ;
    
    /**
     *  
     * xml name: taskVersion
     * 
     */
    private java.lang.Long _taskVersion ;
    
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

    
    /**
     * 
     * xml name: taskVersion
     *  
     */
    
    public java.lang.Long getTaskVersion(){
      return _taskVersion;
    }

    
    public void setTaskVersion(java.lang.Long value){
        checkAllowChange();
        
        this._taskVersion = value;
           
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
        
        out.putNotNull("taskName",this.getTaskName());
        out.putNotNull("taskVersion",this.getTaskVersion());
    }

    public CallTaskStepModel cloneInstance(){
        CallTaskStepModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(CallTaskStepModel instance){
        super.copyTo(instance);
        
        instance.setTaskName(this.getTaskName());
        instance.setTaskVersion(this.getTaskVersion());
    }

    protected CallTaskStepModel newInstance(){
        return (CallTaskStepModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
