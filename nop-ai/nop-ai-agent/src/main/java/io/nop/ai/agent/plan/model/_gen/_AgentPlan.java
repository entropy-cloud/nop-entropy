package io.nop.ai.agent.plan.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ai.agent.plan.model.AgentPlan;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/ai/agent-plan.xdef <p>
 * Agent Plan Metamodel Definition
 * 定义AI Agent执行计划的元模型，包含以下特性：
 * - 阶段驱动（Phase-based）：高层任务分类
 * - 任务驱动（Task-based）：支持任务递归和并行执行
 * - 状态跟踪：记录决策、错误、关键问题
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _AgentPlan extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: additionalNotes
     * 任务笔记列表, 用于记录任意扩展信息
     */
    private KeyedList<io.nop.ai.agent.plan.model.AgentPlanNote> _additionalNotes = KeyedList.emptyList();
    
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
    private KeyedList<io.nop.ai.agent.plan.model.AgentPlanDecision> _decisions = KeyedList.emptyList();
    
    /**
     *  
     * xml name: errors
     * 任务执行过程中遇到的值得记录的错误记录
     */
    private KeyedList<io.nop.ai.agent.plan.model.AgentPlanError> _errors = KeyedList.emptyList();
    
    /**
     *  
     * xml name: goal
     * 
     */
    private java.lang.String _goal ;
    
    /**
     *  
     * xml name: phases
     * 任务阶段列表：用于高层任务分类
     */
    private KeyedList<io.nop.ai.agent.plan.model.AgentPlanPhase> _phases = KeyedList.emptyList();
    
    /**
     *  
     * xml name: questions
     * 需要回答的关键问题列表
     */
    private KeyedList<io.nop.ai.agent.plan.model.AgentPlanQuestion> _questions = KeyedList.emptyList();
    
    /**
     *  
     * xml name: readFiles
     * 读取过的有用文件
     */
    private java.util.List<io.nop.ai.agent.plan.model.AgentPlanReadFileRecord> _readFiles = java.util.Collections.emptyList();
    
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
     * xml name: writtenFiles
     * 编写的文件
     */
    private java.util.List<io.nop.ai.agent.plan.model.AgentPlanWriteFileRecord> _writtenFiles = java.util.Collections.emptyList();
    
    /**
     * 
     * xml name: additionalNotes
     *  任务笔记列表, 用于记录任意扩展信息
     */
    
    public java.util.List<io.nop.ai.agent.plan.model.AgentPlanNote> getAdditionalNotes(){
      return _additionalNotes;
    }

    
    public void setAdditionalNotes(java.util.List<io.nop.ai.agent.plan.model.AgentPlanNote> value){
        checkAllowChange();
        
        this._additionalNotes = KeyedList.fromList(value, io.nop.ai.agent.plan.model.AgentPlanNote::getId);
           
    }

    
    public io.nop.ai.agent.plan.model.AgentPlanNote getAdditionalNote(String name){
        return this._additionalNotes.getByKey(name);
    }

    public boolean hasAdditionalNote(String name){
        return this._additionalNotes.containsKey(name);
    }

    public void addAdditionalNote(io.nop.ai.agent.plan.model.AgentPlanNote item) {
        checkAllowChange();
        java.util.List<io.nop.ai.agent.plan.model.AgentPlanNote> list = this.getAdditionalNotes();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.ai.agent.plan.model.AgentPlanNote::getId);
            setAdditionalNotes(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_additionalNotes(){
        return this._additionalNotes.keySet();
    }

    public boolean hasAdditionalNotes(){
        return !this._additionalNotes.isEmpty();
    }
    
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
    
    public java.util.List<io.nop.ai.agent.plan.model.AgentPlanDecision> getDecisions(){
      return _decisions;
    }

    
    public void setDecisions(java.util.List<io.nop.ai.agent.plan.model.AgentPlanDecision> value){
        checkAllowChange();
        
        this._decisions = KeyedList.fromList(value, io.nop.ai.agent.plan.model.AgentPlanDecision::getId);
           
    }

    
    public io.nop.ai.agent.plan.model.AgentPlanDecision getDecision(String name){
        return this._decisions.getByKey(name);
    }

    public boolean hasDecision(String name){
        return this._decisions.containsKey(name);
    }

    public void addDecision(io.nop.ai.agent.plan.model.AgentPlanDecision item) {
        checkAllowChange();
        java.util.List<io.nop.ai.agent.plan.model.AgentPlanDecision> list = this.getDecisions();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.ai.agent.plan.model.AgentPlanDecision::getId);
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
     *  任务执行过程中遇到的值得记录的错误记录
     */
    
    public java.util.List<io.nop.ai.agent.plan.model.AgentPlanError> getErrors(){
      return _errors;
    }

    
    public void setErrors(java.util.List<io.nop.ai.agent.plan.model.AgentPlanError> value){
        checkAllowChange();
        
        this._errors = KeyedList.fromList(value, io.nop.ai.agent.plan.model.AgentPlanError::getId);
           
    }

    
    public io.nop.ai.agent.plan.model.AgentPlanError getError(String name){
        return this._errors.getByKey(name);
    }

    public boolean hasError(String name){
        return this._errors.containsKey(name);
    }

    public void addError(io.nop.ai.agent.plan.model.AgentPlanError item) {
        checkAllowChange();
        java.util.List<io.nop.ai.agent.plan.model.AgentPlanError> list = this.getErrors();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.ai.agent.plan.model.AgentPlanError::getId);
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
     * xml name: phases
     *  任务阶段列表：用于高层任务分类
     */
    
    public java.util.List<io.nop.ai.agent.plan.model.AgentPlanPhase> getPhases(){
      return _phases;
    }

    
    public void setPhases(java.util.List<io.nop.ai.agent.plan.model.AgentPlanPhase> value){
        checkAllowChange();
        
        this._phases = KeyedList.fromList(value, io.nop.ai.agent.plan.model.AgentPlanPhase::getName);
           
    }

    
    public io.nop.ai.agent.plan.model.AgentPlanPhase getPhase(String name){
        return this._phases.getByKey(name);
    }

    public boolean hasPhase(String name){
        return this._phases.containsKey(name);
    }

    public void addPhase(io.nop.ai.agent.plan.model.AgentPlanPhase item) {
        checkAllowChange();
        java.util.List<io.nop.ai.agent.plan.model.AgentPlanPhase> list = this.getPhases();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.ai.agent.plan.model.AgentPlanPhase::getName);
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
     * xml name: questions
     *  需要回答的关键问题列表
     */
    
    public java.util.List<io.nop.ai.agent.plan.model.AgentPlanQuestion> getQuestions(){
      return _questions;
    }

    
    public void setQuestions(java.util.List<io.nop.ai.agent.plan.model.AgentPlanQuestion> value){
        checkAllowChange();
        
        this._questions = KeyedList.fromList(value, io.nop.ai.agent.plan.model.AgentPlanQuestion::getId);
           
    }

    
    public io.nop.ai.agent.plan.model.AgentPlanQuestion getQuestion(String name){
        return this._questions.getByKey(name);
    }

    public boolean hasQuestion(String name){
        return this._questions.containsKey(name);
    }

    public void addQuestion(io.nop.ai.agent.plan.model.AgentPlanQuestion item) {
        checkAllowChange();
        java.util.List<io.nop.ai.agent.plan.model.AgentPlanQuestion> list = this.getQuestions();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.ai.agent.plan.model.AgentPlanQuestion::getId);
            setQuestions(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_questions(){
        return this._questions.keySet();
    }

    public boolean hasQuestions(){
        return !this._questions.isEmpty();
    }
    
    /**
     * 
     * xml name: readFiles
     *  读取过的有用文件
     */
    
    public java.util.List<io.nop.ai.agent.plan.model.AgentPlanReadFileRecord> getReadFiles(){
      return _readFiles;
    }

    
    public void setReadFiles(java.util.List<io.nop.ai.agent.plan.model.AgentPlanReadFileRecord> value){
        checkAllowChange();
        
        this._readFiles = value;
           
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

    
    /**
     * 
     * xml name: writtenFiles
     *  编写的文件
     */
    
    public java.util.List<io.nop.ai.agent.plan.model.AgentPlanWriteFileRecord> getWrittenFiles(){
      return _writtenFiles;
    }

    
    public void setWrittenFiles(java.util.List<io.nop.ai.agent.plan.model.AgentPlanWriteFileRecord> value){
        checkAllowChange();
        
        this._writtenFiles = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._additionalNotes = io.nop.api.core.util.FreezeHelper.deepFreeze(this._additionalNotes);
            
           this._decisions = io.nop.api.core.util.FreezeHelper.deepFreeze(this._decisions);
            
           this._errors = io.nop.api.core.util.FreezeHelper.deepFreeze(this._errors);
            
           this._phases = io.nop.api.core.util.FreezeHelper.deepFreeze(this._phases);
            
           this._questions = io.nop.api.core.util.FreezeHelper.deepFreeze(this._questions);
            
           this._readFiles = io.nop.api.core.util.FreezeHelper.deepFreeze(this._readFiles);
            
           this._writtenFiles = io.nop.api.core.util.FreezeHelper.deepFreeze(this._writtenFiles);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("additionalNotes",this.getAdditionalNotes());
        out.putNotNull("createdAt",this.getCreatedAt());
        out.putNotNull("currentPhase",this.getCurrentPhase());
        out.putNotNull("decisions",this.getDecisions());
        out.putNotNull("errors",this.getErrors());
        out.putNotNull("goal",this.getGoal());
        out.putNotNull("phases",this.getPhases());
        out.putNotNull("questions",this.getQuestions());
        out.putNotNull("readFiles",this.getReadFiles());
        out.putNotNull("status",this.getStatus());
        out.putNotNull("updatedAt",this.getUpdatedAt());
        out.putNotNull("writtenFiles",this.getWrittenFiles());
    }

    public AgentPlan cloneInstance(){
        AgentPlan instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(AgentPlan instance){
        super.copyTo(instance);
        
        instance.setAdditionalNotes(this.getAdditionalNotes());
        instance.setCreatedAt(this.getCreatedAt());
        instance.setCurrentPhase(this.getCurrentPhase());
        instance.setDecisions(this.getDecisions());
        instance.setErrors(this.getErrors());
        instance.setGoal(this.getGoal());
        instance.setPhases(this.getPhases());
        instance.setQuestions(this.getQuestions());
        instance.setReadFiles(this.getReadFiles());
        instance.setStatus(this.getStatus());
        instance.setUpdatedAt(this.getUpdatedAt());
        instance.setWrittenFiles(this.getWrittenFiles());
    }

    protected AgentPlan newInstance(){
        return (AgentPlan) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
