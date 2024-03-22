package io.nop.task.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.task.model.TaskFlowModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [8:2:0:0]/nop/schema/task/task.xdef <p>
 * 支持异步执行的轻量化任务引擎。持久化状态为可选特性，如果在步骤上配置了saveState，则可以从任意步骤中断并恢复执行。
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _TaskFlowModel extends io.nop.task.model.TaskStepsModel {
    
    /**
     *  
     * xml name: defaultSaveState
     * 
     */
    private boolean _defaultSaveState  = false;
    
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
     * xml name: graphMode
     * 
     */
    private boolean _graphMode  = false;
    
    /**
     *  
     * xml name: restartable
     * 
     */
    private boolean _restartable  = true;
    
    /**
     *  
     * xml name: version
     * 
     */
    private long _version  = 0L;
    
    /**
     * 
     * xml name: defaultSaveState
     *  
     */
    
    public boolean isDefaultSaveState(){
      return _defaultSaveState;
    }

    
    public void setDefaultSaveState(boolean value){
        checkAllowChange();
        
        this._defaultSaveState = value;
           
    }

    
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

    
    /**
     * 
     * xml name: graphMode
     *  
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
     * xml name: restartable
     *  
     */
    
    public boolean isRestartable(){
      return _restartable;
    }

    
    public void setRestartable(boolean value){
        checkAllowChange();
        
        this._restartable = value;
           
    }

    
    /**
     * 
     * xml name: version
     *  
     */
    
    public long getVersion(){
      return _version;
    }

    
    public void setVersion(long value){
        checkAllowChange();
        
        this._version = value;
           
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
        
        out.putNotNull("defaultSaveState",this.isDefaultSaveState());
        out.putNotNull("enterSteps",this.getEnterSteps());
        out.putNotNull("exitSteps",this.getExitSteps());
        out.putNotNull("graphMode",this.isGraphMode());
        out.putNotNull("restartable",this.isRestartable());
        out.putNotNull("version",this.getVersion());
    }

    public TaskFlowModel cloneInstance(){
        TaskFlowModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(TaskFlowModel instance){
        super.copyTo(instance);
        
        instance.setDefaultSaveState(this.isDefaultSaveState());
        instance.setEnterSteps(this.getEnterSteps());
        instance.setExitSteps(this.getExitSteps());
        instance.setGraphMode(this.isGraphMode());
        instance.setRestartable(this.isRestartable());
        instance.setVersion(this.getVersion());
    }

    protected TaskFlowModel newInstance(){
        return (TaskFlowModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
