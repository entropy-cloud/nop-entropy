package io.nop.task.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.task.model.GraphTaskStepModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [120:14:0:0]/nop/schema/task/task.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _GraphTaskStepModel extends io.nop.task.model.TaskStepsModel {
    
    /**
     *  
     * xml name: enterSteps
     * 
     */
    private java.util.Set<java.lang.String> _enterSteps ;
    
    /**
     *  
     * xml name: exitSteps
     * 
     */
    private java.util.Set<java.lang.String> _exitSteps ;
    
    /**
     * 
     * xml name: enterSteps
     *  
     */
    
    public java.util.Set<java.lang.String> getEnterSteps(){
      return _enterSteps;
    }

    
    public void setEnterSteps(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._enterSteps = value;
           
    }

    
    /**
     * 
     * xml name: exitSteps
     *  
     */
    
    public java.util.Set<java.lang.String> getExitSteps(){
      return _exitSteps;
    }

    
    public void setExitSteps(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._exitSteps = value;
           
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
        
        out.putNotNull("enterSteps",this.getEnterSteps());
        out.putNotNull("exitSteps",this.getExitSteps());
    }

    public GraphTaskStepModel cloneInstance(){
        GraphTaskStepModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(GraphTaskStepModel instance){
        super.copyTo(instance);
        
        instance.setEnterSteps(this.getEnterSteps());
        instance.setExitSteps(this.getExitSteps());
    }

    protected GraphTaskStepModel newInstance(){
        return (GraphTaskStepModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
