package io.nop.ai.agent.plan.model._gen;

import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ai.agent.plan.model.AgentPlanModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/ai/plan.xdef <p>
 * Agent Plan Metamodel Definition
 * 定义AI Agent执行计划的元模型，支持任务分解、状态跟踪和备注管理
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _AgentPlanModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: notes
     * 
     */
    private KeyedList<io.nop.ai.agent.plan.model.AgentPlanNoteModel> _notes = KeyedList.emptyList();
    
    /**
     *  
     * xml name: overview
     * 
     */
    private java.lang.String _overview ;
    
    /**
     *  
     * xml name: path
     * 
     */
    private java.lang.String _path ;
    
    /**
     *  
     * xml name: planStatus
     * 
     */
    private AgentExecStatus _planStatus ;
    
    /**
     *  
     * xml name: tasks
     * 
     */
    private KeyedList<io.nop.ai.agent.plan.model.AgentPlanTaskModel> _tasks = KeyedList.emptyList();
    
    /**
     *  
     * xml name: title
     * 
     */
    private java.lang.String _title ;
    
    /**
     * 
     * xml name: notes
     *  
     */
    
    public java.util.List<io.nop.ai.agent.plan.model.AgentPlanNoteModel> getNotes(){
      return _notes;
    }

    
    public void setNotes(java.util.List<io.nop.ai.agent.plan.model.AgentPlanNoteModel> value){
        checkAllowChange();
        
        this._notes = KeyedList.fromList(value, io.nop.ai.agent.plan.model.AgentPlanNoteModel::getName);
           
    }

    
    public io.nop.ai.agent.plan.model.AgentPlanNoteModel getNote(String name){
        return this._notes.getByKey(name);
    }

    public boolean hasNote(String name){
        return this._notes.containsKey(name);
    }

    public void addNote(io.nop.ai.agent.plan.model.AgentPlanNoteModel item) {
        checkAllowChange();
        java.util.List<io.nop.ai.agent.plan.model.AgentPlanNoteModel> list = this.getNotes();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.ai.agent.plan.model.AgentPlanNoteModel::getName);
            setNotes(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_notes(){
        return this._notes.keySet();
    }

    public boolean hasNotes(){
        return !this._notes.isEmpty();
    }
    
    /**
     * 
     * xml name: overview
     *  
     */
    
    public java.lang.String getOverview(){
      return _overview;
    }

    
    public void setOverview(java.lang.String value){
        checkAllowChange();
        
        this._overview = value;
           
    }

    
    /**
     * 
     * xml name: path
     *  
     */
    
    public java.lang.String getPath(){
      return _path;
    }

    
    public void setPath(java.lang.String value){
        checkAllowChange();
        
        this._path = value;
           
    }

    
    /**
     * 
     * xml name: planStatus
     *  
     */
    
    public AgentExecStatus getPlanStatus(){
      return _planStatus;
    }

    
    public void setPlanStatus(AgentExecStatus value){
        checkAllowChange();
        
        this._planStatus = value;
           
    }

    
    /**
     * 
     * xml name: tasks
     *  
     */
    
    public java.util.List<io.nop.ai.agent.plan.model.AgentPlanTaskModel> getTasks(){
      return _tasks;
    }

    
    public void setTasks(java.util.List<io.nop.ai.agent.plan.model.AgentPlanTaskModel> value){
        checkAllowChange();
        
        this._tasks = KeyedList.fromList(value, io.nop.ai.agent.plan.model.AgentPlanTaskModel::getTaskNo);
           
    }

    
    public io.nop.ai.agent.plan.model.AgentPlanTaskModel getTask(String name){
        return this._tasks.getByKey(name);
    }

    public boolean hasTask(String name){
        return this._tasks.containsKey(name);
    }

    public void addTask(io.nop.ai.agent.plan.model.AgentPlanTaskModel item) {
        checkAllowChange();
        java.util.List<io.nop.ai.agent.plan.model.AgentPlanTaskModel> list = this.getTasks();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.ai.agent.plan.model.AgentPlanTaskModel::getTaskNo);
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
    
    /**
     * 
     * xml name: title
     *  
     */
    
    public java.lang.String getTitle(){
      return _title;
    }

    
    public void setTitle(java.lang.String value){
        checkAllowChange();
        
        this._title = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._notes = io.nop.api.core.util.FreezeHelper.deepFreeze(this._notes);
            
           this._tasks = io.nop.api.core.util.FreezeHelper.deepFreeze(this._tasks);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("notes",this.getNotes());
        out.putNotNull("overview",this.getOverview());
        out.putNotNull("path",this.getPath());
        out.putNotNull("planStatus",this.getPlanStatus());
        out.putNotNull("tasks",this.getTasks());
        out.putNotNull("title",this.getTitle());
    }

    public AgentPlanModel cloneInstance(){
        AgentPlanModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(AgentPlanModel instance){
        super.copyTo(instance);
        
        instance.setNotes(this.getNotes());
        instance.setOverview(this.getOverview());
        instance.setPath(this.getPath());
        instance.setPlanStatus(this.getPlanStatus());
        instance.setTasks(this.getTasks());
        instance.setTitle(this.getTitle());
    }

    protected AgentPlanModel newInstance(){
        return (AgentPlanModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
