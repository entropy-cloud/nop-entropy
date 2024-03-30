package io.nop.task.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.task.model.TaskStepsModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [108:6:0:0]/nop/schema/task/task.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _TaskStepsModel extends io.nop.task.model.TaskStepModel {
    
    /**
     *  
     * xml name: steps
     * 
     */
    private KeyedList<io.nop.task.model.TaskStepModel> _steps = KeyedList.emptyList();
    
    /**
     * 
     * xml name: steps
     *  
     */
    
    public java.util.List<io.nop.task.model.TaskStepModel> getSteps(){
      return _steps;
    }

    
    public void setSteps(java.util.List<io.nop.task.model.TaskStepModel> value){
        checkAllowChange();
        
        this._steps = KeyedList.fromList(value, io.nop.task.model.TaskStepModel::getName);
           
    }

    
    public io.nop.task.model.TaskStepModel getStep(String name){
        return this._steps.getByKey(name);
    }

    public boolean hasStep(String name){
        return this._steps.containsKey(name);
    }

    public void addStep(io.nop.task.model.TaskStepModel item) {
        checkAllowChange();
        java.util.List<io.nop.task.model.TaskStepModel> list = this.getSteps();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.task.model.TaskStepModel::getName);
            setSteps(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_steps(){
        return this._steps.keySet();
    }

    public boolean hasSteps(){
        return !this._steps.isEmpty();
    }
    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._steps = io.nop.api.core.util.FreezeHelper.deepFreeze(this._steps);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("steps",this.getSteps());
    }

    public TaskStepsModel cloneInstance(){
        TaskStepsModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(TaskStepsModel instance){
        super.copyTo(instance);
        
        instance.setSteps(this.getSteps());
    }

    protected TaskStepsModel newInstance(){
        return (TaskStepsModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
