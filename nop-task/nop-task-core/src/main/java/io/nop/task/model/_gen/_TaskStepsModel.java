package io.nop.task.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [76:6:0:0]/nop/schema/task/task.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _TaskStepsModel extends io.nop.task.model.TaskStepModel {
    
    /**
     *  
     * xml name: firstStep
     * 
     */
    private java.lang.String _firstStep ;
    
    /**
     *  
     * xml name: graphMode
     * 图模式要求每个步骤都要设置next步骤，如果next为null, 则表示exit而不是继续执行后续步骤
     */
    private boolean _graphMode  = false;
    
    /**
     *  
     * xml name: steps
     * 
     */
    private KeyedList<io.nop.task.model.TaskStepModel> _steps = KeyedList.emptyList();
    
    /**
     * 
     * xml name: firstStep
     *  
     */
    
    public java.lang.String getFirstStep(){
      return _firstStep;
    }

    
    public void setFirstStep(java.lang.String value){
        checkAllowChange();
        
        this._firstStep = value;
           
    }

    
    /**
     * 
     * xml name: graphMode
     *  图模式要求每个步骤都要设置next步骤，如果next为null, 则表示exit而不是继续执行后续步骤
     */
    
    public boolean isGraphMode(){
      return _graphMode;
    }

    
    public void setGraphMode(boolean value){
        checkAllowChange();
        
        this._graphMode = value;
           
    }

    
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
        
        this._steps = KeyedList.fromList(value, io.nop.task.model.TaskStepModel::getId);
           
    }

    
    public java.util.Set<String> keySet_steps(){
        return this._steps.keySet();
    }

    public boolean hasSteps(){
        return !this._steps.isEmpty();
    }
    

    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._steps = io.nop.api.core.util.FreezeHelper.deepFreeze(this._steps);
            
        }
    }

    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("firstStep",this.getFirstStep());
        out.put("graphMode",this.isGraphMode());
        out.put("steps",this.getSteps());
    }
}
 // resume CPD analysis - CPD-ON
