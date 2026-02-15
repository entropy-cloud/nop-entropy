package io.nop.ai.agent.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ai.agent.model.AgentPlanModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/ai/plan.xdef <p>
 * Agent Plan Metamodel Definition
 * 定义AI Agent执行计划的元模型，包含以下特性：
 * - 阶段驱动（Phase-based）：高层任务分类
 * - 任务驱动（Task-based）：支持任务递归和并行执行
 * - 状态跟踪：记录决策、错误、关键问题
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _AgentPlanModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: createdAt
     * 
     */
    private java.time.LocalDateTime _createdAt ;
    
    /**
     *  
     * xml name: currentPhase
     * 
     */
    private java.lang.String _currentPhase ;
    
    /**
     *  
     * xml name: decisions
     * 任务执行过程中做出的决策记录
     */
    private KeyedList<io.nop.ai.agent.model.AgentPlanDecision> _decisions = KeyedList.emptyList();
    
    /**
     *  
     * xml name: errors
     * 任务执行过程中遇到的错误记录
     */
    private KeyedList<io.nop.ai.agent.model.AgentPlanError> _errors = KeyedList.emptyList();
    
    /**
     *  
     * xml name: goal
     * 
     */
    private java.lang.String _goal ;
    
    /**
     *  
     * xml name: keyQuestions
     * 需要回答的关键问题列表
     */
    private KeyedList<io.nop.ai.agent.model.AgentPlanQuestion> _keyQuestions = KeyedList.emptyList();
    
    /**
     *  
     * xml name: notes
     * 计划级别的笔记列表，可被任务引用
     */
    private KeyedList<io.nop.ai.agent.model.AgentPlanNote> _notes = KeyedList.emptyList();
    
    /**
     *  
     * xml name: phases
     * 任务阶段列表：用于高层任务分类
     */
    private KeyedList<io.nop.ai.agent.model.AgentPlanPhaseModel> _phases = KeyedList.emptyList();
    
    /**
     *  
     * xml name: status
     * 
     */
    private io.nop.ai.agent.model.AgentExecStatus _status ;
    
    /**
     *  
     * xml name: updatedAt
     * 
     */
    private java.time.LocalDateTime _updatedAt ;
    
    /**
     * 
     * xml name: createdAt
     *  
     */
    
    public java.time.LocalDateTime getCreatedAt(){
      return _createdAt;
    }

    
    public void setCreatedAt(java.time.LocalDateTime value){
        checkAllowChange();
        
        this._createdAt = value;
           
    }

    
    /**
     * 
     * xml name: currentPhase
     *  
     */
    
    public java.lang.String getCurrentPhase(){
      return _currentPhase;
    }

    
    public void setCurrentPhase(java.lang.String value){
        checkAllowChange();
        
        this._currentPhase = value;
           
    }

    
    /**
     * 
     * xml name: decisions
     *  任务执行过程中做出的决策记录
     */
    
    public java.util.List<io.nop.ai.agent.model.AgentPlanDecision> getDecisions(){
      return _decisions;
    }

    
    public void setDecisions(java.util.List<io.nop.ai.agent.model.AgentPlanDecision> value){
        checkAllowChange();
        
        this._decisions = KeyedList.fromList(value, io.nop.ai.agent.model.AgentPlanDecision::getId);
           
    }

    
    public io.nop.ai.agent.model.AgentPlanDecision getDecision(String name){
        return this._decisions.getByKey(name);
    }

    public boolean hasDecision(String name){
        return this._decisions.containsKey(name);
    }

    public void addDecision(io.nop.ai.agent.model.AgentPlanDecision item) {
        checkAllowChange();
        java.util.List<io.nop.ai.agent.model.AgentPlanDecision> list = this.getDecisions();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.ai.agent.model.AgentPlanDecision::getId);
            setDecisions(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_decisions(){
        return this._decisions.keySet();
    }

    public boolean hasDecisions(){
        return !this._decisions.isEmpty();
    }
    
    /**
     * 
     * xml name: errors
     *  任务执行过程中遇到的错误记录
     */
    
    public java.util.List<io.nop.ai.agent.model.AgentPlanError> getErrors(){
      return _errors;
    }

    
    public void setErrors(java.util.List<io.nop.ai.agent.model.AgentPlanError> value){
        checkAllowChange();
        
        this._errors = KeyedList.fromList(value, io.nop.ai.agent.model.AgentPlanError::getId);
           
    }

    
    public io.nop.ai.agent.model.AgentPlanError getError(String name){
        return this._errors.getByKey(name);
    }

    public boolean hasError(String name){
        return this._errors.containsKey(name);
    }

    public void addError(io.nop.ai.agent.model.AgentPlanError item) {
        checkAllowChange();
        java.util.List<io.nop.ai.agent.model.AgentPlanError> list = this.getErrors();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.ai.agent.model.AgentPlanError::getId);
            setErrors(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_errors(){
        return this._errors.keySet();
    }

    public boolean hasErrors(){
        return !this._errors.isEmpty();
    }
    
    /**
     * 
     * xml name: goal
     *  
     */
    
    public java.lang.String getGoal(){
      return _goal;
    }

    
    public void setGoal(java.lang.String value){
        checkAllowChange();
        
        this._goal = value;
           
    }

    
    /**
     * 
     * xml name: keyQuestions
     *  需要回答的关键问题列表
     */
    
    public java.util.List<io.nop.ai.agent.model.AgentPlanQuestion> getKeyQuestions(){
      return _keyQuestions;
    }

    
    public void setKeyQuestions(java.util.List<io.nop.ai.agent.model.AgentPlanQuestion> value){
        checkAllowChange();
        
        this._keyQuestions = KeyedList.fromList(value, io.nop.ai.agent.model.AgentPlanQuestion::getId);
           
    }

    
    public io.nop.ai.agent.model.AgentPlanQuestion getKeyQuestion(String name){
        return this._keyQuestions.getByKey(name);
    }

    public boolean hasKeyQuestion(String name){
        return this._keyQuestions.containsKey(name);
    }

    public void addKeyQuestion(io.nop.ai.agent.model.AgentPlanQuestion item) {
        checkAllowChange();
        java.util.List<io.nop.ai.agent.model.AgentPlanQuestion> list = this.getKeyQuestions();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.ai.agent.model.AgentPlanQuestion::getId);
            setKeyQuestions(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_keyQuestions(){
        return this._keyQuestions.keySet();
    }

    public boolean hasKeyQuestions(){
        return !this._keyQuestions.isEmpty();
    }
    
    /**
     * 
     * xml name: notes
     *  计划级别的笔记列表，可被任务引用
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
     * xml name: phases
     *  任务阶段列表：用于高层任务分类
     */
    
    public java.util.List<io.nop.ai.agent.model.AgentPlanPhaseModel> getPhases(){
      return _phases;
    }

    
    public void setPhases(java.util.List<io.nop.ai.agent.model.AgentPlanPhaseModel> value){
        checkAllowChange();
        
        this._phases = KeyedList.fromList(value, io.nop.ai.agent.model.AgentPlanPhaseModel::getName);
           
    }

    
    public io.nop.ai.agent.model.AgentPlanPhaseModel getPhase(String name){
        return this._phases.getByKey(name);
    }

    public boolean hasPhase(String name){
        return this._phases.containsKey(name);
    }

    public void addPhase(io.nop.ai.agent.model.AgentPlanPhaseModel item) {
        checkAllowChange();
        java.util.List<io.nop.ai.agent.model.AgentPlanPhaseModel> list = this.getPhases();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.ai.agent.model.AgentPlanPhaseModel::getName);
            setPhases(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_phases(){
        return this._phases.keySet();
    }

    public boolean hasPhases(){
        return !this._phases.isEmpty();
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
     * xml name: updatedAt
     *  
     */
    
    public java.time.LocalDateTime getUpdatedAt(){
      return _updatedAt;
    }

    
    public void setUpdatedAt(java.time.LocalDateTime value){
        checkAllowChange();
        
        this._updatedAt = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._decisions = io.nop.api.core.util.FreezeHelper.deepFreeze(this._decisions);
            
           this._errors = io.nop.api.core.util.FreezeHelper.deepFreeze(this._errors);
            
           this._keyQuestions = io.nop.api.core.util.FreezeHelper.deepFreeze(this._keyQuestions);
            
           this._notes = io.nop.api.core.util.FreezeHelper.deepFreeze(this._notes);
            
           this._phases = io.nop.api.core.util.FreezeHelper.deepFreeze(this._phases);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("createdAt",this.getCreatedAt());
        out.putNotNull("currentPhase",this.getCurrentPhase());
        out.putNotNull("decisions",this.getDecisions());
        out.putNotNull("errors",this.getErrors());
        out.putNotNull("goal",this.getGoal());
        out.putNotNull("keyQuestions",this.getKeyQuestions());
        out.putNotNull("notes",this.getNotes());
        out.putNotNull("phases",this.getPhases());
        out.putNotNull("status",this.getStatus());
        out.putNotNull("updatedAt",this.getUpdatedAt());
    }

    public AgentPlanModel cloneInstance(){
        AgentPlanModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(AgentPlanModel instance){
        super.copyTo(instance);
        
        instance.setCreatedAt(this.getCreatedAt());
        instance.setCurrentPhase(this.getCurrentPhase());
        instance.setDecisions(this.getDecisions());
        instance.setErrors(this.getErrors());
        instance.setGoal(this.getGoal());
        instance.setKeyQuestions(this.getKeyQuestions());
        instance.setNotes(this.getNotes());
        instance.setPhases(this.getPhases());
        instance.setStatus(this.getStatus());
        instance.setUpdatedAt(this.getUpdatedAt());
    }

    protected AgentPlanModel newInstance(){
        return (AgentPlanModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
