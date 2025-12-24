package io.nop.ai.agent.plan.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ai.agent.plan.model.AgentPlanTaskModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/ai/agent-plan.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _AgentPlanTaskModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: instructions
     * 
     */
    private java.lang.String _instructions ;
    
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
     * xml name: resultMessage
     * 
     */
    private java.lang.String _resultMessage ;
    
    /**
     *  
     * xml name: subTasks
     * 
     */
    private KeyedList<io.nop.ai.agent.plan.model.AgentPlanTaskModel> _subTasks = KeyedList.emptyList();
    
    /**
     *  
     * xml name: taskNo
     * 
     */
    private java.lang.String _taskNo ;
    
    /**
     *  
     * xml name: taskStatus
     * 
     */
    private io.nop.ai.agent.plan.AgentExecStatus _taskStatus ;
    
    /**
     *  
     * xml name: title
     * 
     */
    private java.lang.String _title ;
    
    /**
     * 
     * xml name: instructions
     *  
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
     * xml name: resultMessage
     *  
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
     * xml name: subTasks
     *  
     */
    
    public java.util.List<io.nop.ai.agent.plan.model.AgentPlanTaskModel> getSubTasks(){
      return _subTasks;
    }

    
    public void setSubTasks(java.util.List<io.nop.ai.agent.plan.model.AgentPlanTaskModel> value){
        checkAllowChange();
        
        this._subTasks = KeyedList.fromList(value, io.nop.ai.agent.plan.model.AgentPlanTaskModel::getTaskNo);
           
    }

    
    public io.nop.ai.agent.plan.model.AgentPlanTaskModel getTask(String name){
        return this._subTasks.getByKey(name);
    }

    public boolean hasTask(String name){
        return this._subTasks.containsKey(name);
    }

    public void addTask(io.nop.ai.agent.plan.model.AgentPlanTaskModel item) {
        checkAllowChange();
        java.util.List<io.nop.ai.agent.plan.model.AgentPlanTaskModel> list = this.getSubTasks();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.ai.agent.plan.model.AgentPlanTaskModel::getTaskNo);
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
     * xml name: taskStatus
     *  
     */
    
    public io.nop.ai.agent.plan.AgentExecStatus getTaskStatus(){
      return _taskStatus;
    }

    
    public void setTaskStatus(io.nop.ai.agent.plan.AgentExecStatus value){
        checkAllowChange();
        
        this._taskStatus = value;
           
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
        
        out.putNotNull("instructions",this.getInstructions());
        out.putNotNull("notes",this.getNotes());
        out.putNotNull("overview",this.getOverview());
        out.putNotNull("resultMessage",this.getResultMessage());
        out.putNotNull("subTasks",this.getSubTasks());
        out.putNotNull("taskNo",this.getTaskNo());
        out.putNotNull("taskStatus",this.getTaskStatus());
        out.putNotNull("title",this.getTitle());
    }

    public AgentPlanTaskModel cloneInstance(){
        AgentPlanTaskModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(AgentPlanTaskModel instance){
        super.copyTo(instance);
        
        instance.setInstructions(this.getInstructions());
        instance.setNotes(this.getNotes());
        instance.setOverview(this.getOverview());
        instance.setResultMessage(this.getResultMessage());
        instance.setSubTasks(this.getSubTasks());
        instance.setTaskNo(this.getTaskNo());
        instance.setTaskStatus(this.getTaskStatus());
        instance.setTitle(this.getTitle());
    }

    protected AgentPlanTaskModel newInstance(){
        return (AgentPlanTaskModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
