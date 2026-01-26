package io.nop.ai.agent.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ai.agent.model.AgentPlanPhaseModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/ai/plan.xdef <p>
 * 每个阶段包含多个任务，任务支持递归子任务
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _AgentPlanPhaseModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: completedAt
     * 
     */
    private java.time.LocalDateTime _completedAt ;
    
    /**
     *  
     * xml name: description
     * 阶段描述（大文本）
     */
    private java.lang.String _description ;
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: startedAt
     * 
     */
    private java.time.LocalDateTime _startedAt ;
    
    /**
     *  
     * xml name: status
     * 
     */
    private io.nop.ai.agent.model.AgentExecStatus _status ;
    
    /**
     *  
     * xml name: tasks
     * 阶段任务列表：支持递归子任务
     */
    private KeyedList<io.nop.ai.agent.model.AgentPlanTaskModel> _tasks = KeyedList.emptyList();
    
    /**
     * 
     * xml name: completedAt
     *  
     */
    
    public java.time.LocalDateTime getCompletedAt(){
      return _completedAt;
    }

    
    public void setCompletedAt(java.time.LocalDateTime value){
        checkAllowChange();
        
        this._completedAt = value;
           
    }

    
    /**
     * 
     * xml name: description
     *  阶段描述（大文本）
     */
    
    public java.lang.String getDescription(){
      return _description;
    }

    
    public void setDescription(java.lang.String value){
        checkAllowChange();
        
        this._description = value;
           
    }

    
    /**
     * 
     * xml name: name
     *  
     */
    
    public java.lang.String getName(){
      return _name;
    }

    
    public void setName(java.lang.String value){
        checkAllowChange();
        
        this._name = value;
           
    }

    
    /**
     * 
     * xml name: startedAt
     *  
     */
    
    public java.time.LocalDateTime getStartedAt(){
      return _startedAt;
    }

    
    public void setStartedAt(java.time.LocalDateTime value){
        checkAllowChange();
        
        this._startedAt = value;
           
    }

    
    /**
     * 
     * xml name: status
     *  
     */
    
    public io.nop.ai.agent.model.AgentExecStatus getStatus(){
      return _status;
    }

    
    public void setStatus(io.nop.ai.agent.model.AgentExecStatus value){
        checkAllowChange();
        
        this._status = value;
           
    }

    
    /**
     * 
     * xml name: tasks
     *  阶段任务列表：支持递归子任务
     */
    
    public java.util.List<io.nop.ai.agent.model.AgentPlanTaskModel> getTasks(){
      return _tasks;
    }

    
    public void setTasks(java.util.List<io.nop.ai.agent.model.AgentPlanTaskModel> value){
        checkAllowChange();
        
        this._tasks = KeyedList.fromList(value, io.nop.ai.agent.model.AgentPlanTaskModel::getTaskNo);
           
    }

    
    public io.nop.ai.agent.model.AgentPlanTaskModel getTask(String name){
        return this._tasks.getByKey(name);
    }

    public boolean hasTask(String name){
        return this._tasks.containsKey(name);
    }

    public void addTask(io.nop.ai.agent.model.AgentPlanTaskModel item) {
        checkAllowChange();
        java.util.List<io.nop.ai.agent.model.AgentPlanTaskModel> list = this.getTasks();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.ai.agent.model.AgentPlanTaskModel::getTaskNo);
            setTasks(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_tasks(){
        return this._tasks.keySet();
    }

    public boolean hasTasks(){
        return !this._tasks.isEmpty();
    }
    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._tasks = io.nop.api.core.util.FreezeHelper.deepFreeze(this._tasks);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("completedAt",this.getCompletedAt());
        out.putNotNull("description",this.getDescription());
        out.putNotNull("name",this.getName());
        out.putNotNull("startedAt",this.getStartedAt());
        out.putNotNull("status",this.getStatus());
        out.putNotNull("tasks",this.getTasks());
    }

    public AgentPlanPhaseModel cloneInstance(){
        AgentPlanPhaseModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(AgentPlanPhaseModel instance){
        super.copyTo(instance);
        
        instance.setCompletedAt(this.getCompletedAt());
        instance.setDescription(this.getDescription());
        instance.setName(this.getName());
        instance.setStartedAt(this.getStartedAt());
        instance.setStatus(this.getStatus());
        instance.setTasks(this.getTasks());
    }

    protected AgentPlanPhaseModel newInstance(){
        return (AgentPlanPhaseModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
