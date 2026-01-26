package io.nop.ai.agent.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ai.agent.model.AgentPlanTaskModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/ai/plan.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _AgentPlanTaskModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: completedAt
     * 
     */
    private java.time.LocalDateTime _completedAt ;
    
    /**
     *  
     * xml name: instructions
     * 任务指令（大文本）
     */
    private java.lang.String _instructions ;
    
    /**
     *  
     * xml name: notes
     * 任务笔记列表
     */
    private KeyedList<io.nop.ai.agent.model.AgentPlanNote> _notes = KeyedList.emptyList();
    
    /**
     *  
     * xml name: resultMessage
     * 任务结果消息（大文本）
     */
    private java.lang.String _resultMessage ;
    
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
     * xml name: subTasks
     * 递归子任务列表：支持任务分解
     */
    private KeyedList<io.nop.ai.agent.model.AgentPlanTaskModel> _subTasks = KeyedList.emptyList();
    
    /**
     *  
     * xml name: taskNo
     * 
     */
    private java.lang.String _taskNo ;
    
    /**
     *  
     * xml name: title
     * 
     */
    private java.lang.String _title ;
    
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
     * xml name: instructions
     *  任务指令（大文本）
     */
    
    public java.lang.String getInstructions(){
      return _instructions;
    }

    
    public void setInstructions(java.lang.String value){
        checkAllowChange();
        
        this._instructions = value;
           
    }

    
    /**
     * 
     * xml name: notes
     *  任务笔记列表
     */
    
    public java.util.List<io.nop.ai.agent.model.AgentPlanNote> getNotes(){
      return _notes;
    }

    
    public void setNotes(java.util.List<io.nop.ai.agent.model.AgentPlanNote> value){
        checkAllowChange();
        
        this._notes = KeyedList.fromList(value, io.nop.ai.agent.model.AgentPlanNote::getId);
           
    }

    
    public io.nop.ai.agent.model.AgentPlanNote getNote(String name){
        return this._notes.getByKey(name);
    }

    public boolean hasNote(String name){
        return this._notes.containsKey(name);
    }

    public void addNote(io.nop.ai.agent.model.AgentPlanNote item) {
        checkAllowChange();
        java.util.List<io.nop.ai.agent.model.AgentPlanNote> list = this.getNotes();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.ai.agent.model.AgentPlanNote::getId);
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
     * xml name: resultMessage
     *  任务结果消息（大文本）
     */
    
    public java.lang.String getResultMessage(){
      return _resultMessage;
    }

    
    public void setResultMessage(java.lang.String value){
        checkAllowChange();
        
        this._resultMessage = value;
           
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
     * xml name: subTasks
     *  递归子任务列表：支持任务分解
     */
    
    public java.util.List<io.nop.ai.agent.model.AgentPlanTaskModel> getSubTasks(){
      return _subTasks;
    }

    
    public void setSubTasks(java.util.List<io.nop.ai.agent.model.AgentPlanTaskModel> value){
        checkAllowChange();
        
        this._subTasks = KeyedList.fromList(value, io.nop.ai.agent.model.AgentPlanTaskModel::getTaskNo);
           
    }

    
    public io.nop.ai.agent.model.AgentPlanTaskModel getTask(String name){
        return this._subTasks.getByKey(name);
    }

    public boolean hasTask(String name){
        return this._subTasks.containsKey(name);
    }

    public void addTask(io.nop.ai.agent.model.AgentPlanTaskModel item) {
        checkAllowChange();
        java.util.List<io.nop.ai.agent.model.AgentPlanTaskModel> list = this.getSubTasks();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.ai.agent.model.AgentPlanTaskModel::getTaskNo);
            setSubTasks(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_subTasks(){
        return this._subTasks.keySet();
    }

    public boolean hasSubTasks(){
        return !this._subTasks.isEmpty();
    }
    
    /**
     * 
     * xml name: taskNo
     *  
     */
    
    public java.lang.String getTaskNo(){
      return _taskNo;
    }

    
    public void setTaskNo(java.lang.String value){
        checkAllowChange();
        
        this._taskNo = value;
           
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
            
           this._subTasks = io.nop.api.core.util.FreezeHelper.deepFreeze(this._subTasks);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("completedAt",this.getCompletedAt());
        out.putNotNull("instructions",this.getInstructions());
        out.putNotNull("notes",this.getNotes());
        out.putNotNull("resultMessage",this.getResultMessage());
        out.putNotNull("startedAt",this.getStartedAt());
        out.putNotNull("status",this.getStatus());
        out.putNotNull("subTasks",this.getSubTasks());
        out.putNotNull("taskNo",this.getTaskNo());
        out.putNotNull("title",this.getTitle());
    }

    public AgentPlanTaskModel cloneInstance(){
        AgentPlanTaskModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(AgentPlanTaskModel instance){
        super.copyTo(instance);
        
        instance.setCompletedAt(this.getCompletedAt());
        instance.setInstructions(this.getInstructions());
        instance.setNotes(this.getNotes());
        instance.setResultMessage(this.getResultMessage());
        instance.setStartedAt(this.getStartedAt());
        instance.setStatus(this.getStatus());
        instance.setSubTasks(this.getSubTasks());
        instance.setTaskNo(this.getTaskNo());
        instance.setTitle(this.getTitle());
    }

    protected AgentPlanTaskModel newInstance(){
        return (AgentPlanTaskModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
