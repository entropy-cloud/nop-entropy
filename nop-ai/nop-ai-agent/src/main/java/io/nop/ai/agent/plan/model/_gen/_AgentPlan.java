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
     * xml name: closure
     * 
     */
    private io.nop.ai.agent.plan.model.AgentPlanClosure _closure ;
    
    /**
     *  
     * xml name: createdAt
     * 
     */
    private java.time.LocalDateTime _createdAt ;
    
    /**
     *  
     * xml name: currentBaseline
     * 
     */
    private java.lang.String _currentBaseline ;
    
    /**
     *  
     * xml name: currentPhase
     * 
     */
    private java.lang.String _currentPhase ;
    
    /**
     *  
     * xml name: currentTaskNo
     * 
     */
    private java.lang.String _currentTaskNo ;
    
    /**
     *  
     * xml name: errors
     * 任务执行过程中遇到的阻断性或值得追踪的错误记录
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
     * xml name: nonGoals
     * 
     */
    private KeyedList<io.nop.ai.agent.plan.model.AgentPlanNonGoalItem> _nonGoals = KeyedList.emptyList();
    
    /**
     *  
     * xml name: phases
     * 任务阶段列表：用于高层任务分类
     */
    private KeyedList<io.nop.ai.agent.plan.model.AgentPlanPhase> _phases = KeyedList.emptyList();
    
    /**
     *  
     * xml name: purpose
     * 
     */
    private java.lang.String _purpose ;
    
    /**
     *  
     * xml name: readFiles
     * 读取过的有用文件
     */
    private java.util.List<io.nop.ai.agent.plan.model.AgentPlanReadFileRecord> _readFiles = java.util.Collections.emptyList();
    
    /**
     *  
     * xml name: relatedPlans
     * 
     */
    private KeyedList<io.nop.ai.agent.plan.model.AgentPlanRelatedRef> _relatedPlans = KeyedList.emptyList();
    
    /**
     *  
     * xml name: reviewedAt
     * 
     */
    private java.time.LocalDate _reviewedAt ;
    
    /**
     *  
     * xml name: scope
     * 
     */
    private io.nop.ai.agent.plan.model.AgentPlanScope _scope ;
    
    /**
     *  
     * xml name: sources
     * 
     */
    private KeyedList<io.nop.ai.agent.plan.model.AgentPlanSourceRef> _sources = KeyedList.emptyList();
    
    /**
     *  
     * xml name: status
     * 
     */
    private io.nop.ai.agent.model.AgentExecStatus _status ;
    
    /**
     *  
     * xml name: successCriteria
     * 
     */
    private KeyedList<io.nop.ai.agent.plan.model.AgentPlanSuccessCriterion> _successCriteria = KeyedList.emptyList();
    
    /**
     *  
     * xml name: title
     * 
     */
    private java.lang.String _title ;
    
    /**
     *  
     * xml name: updatedAt
     * 
     */
    private java.time.LocalDateTime _updatedAt ;
    
    /**
     *  
     * xml name: validationChecklist
     * 
     */
    private KeyedList<io.nop.ai.agent.plan.model.AgentPlanCriterion> _validationChecklist = KeyedList.emptyList();
    
    /**
     *  
     * xml name: writtenFiles
     * 编写的文件
     */
    private java.util.List<io.nop.ai.agent.plan.model.AgentPlanWriteFileRecord> _writtenFiles = java.util.Collections.emptyList();
    
    /**
     * 
     * xml name: closure
     *  
     */
    
    public io.nop.ai.agent.plan.model.AgentPlanClosure getClosure(){
      return _closure;
    }

    
    public void setClosure(io.nop.ai.agent.plan.model.AgentPlanClosure value){
        checkAllowChange();
        
        this._closure = value;
           
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
     * xml name: currentBaseline
     *  
     */
    
    public java.lang.String getCurrentBaseline(){
      return _currentBaseline;
    }

    
    public void setCurrentBaseline(java.lang.String value){
        checkAllowChange();
        
        this._currentBaseline = value;
           
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
     * xml name: currentTaskNo
     *  
     */
    
    public java.lang.String getCurrentTaskNo(){
      return _currentTaskNo;
    }

    
    public void setCurrentTaskNo(java.lang.String value){
        checkAllowChange();
        
        this._currentTaskNo = value;
           
    }

    
    /**
     * 
     * xml name: errors
     *  任务执行过程中遇到的阻断性或值得追踪的错误记录
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
     * xml name: nonGoals
     *  
     */
    
    public java.util.List<io.nop.ai.agent.plan.model.AgentPlanNonGoalItem> getNonGoals(){
      return _nonGoals;
    }

    
    public void setNonGoals(java.util.List<io.nop.ai.agent.plan.model.AgentPlanNonGoalItem> value){
        checkAllowChange();
        
        this._nonGoals = KeyedList.fromList(value, io.nop.ai.agent.plan.model.AgentPlanNonGoalItem::getId);
           
    }

    
    public io.nop.ai.agent.plan.model.AgentPlanNonGoalItem getNonGoal(String name){
        return this._nonGoals.getByKey(name);
    }

    public boolean hasNonGoal(String name){
        return this._nonGoals.containsKey(name);
    }

    public void addNonGoal(io.nop.ai.agent.plan.model.AgentPlanNonGoalItem item) {
        checkAllowChange();
        java.util.List<io.nop.ai.agent.plan.model.AgentPlanNonGoalItem> list = this.getNonGoals();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.ai.agent.plan.model.AgentPlanNonGoalItem::getId);
            setNonGoals(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_nonGoals(){
        return this._nonGoals.keySet();
    }

    public boolean hasNonGoals(){
        return !this._nonGoals.isEmpty();
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
     * xml name: purpose
     *  
     */
    
    public java.lang.String getPurpose(){
      return _purpose;
    }

    
    public void setPurpose(java.lang.String value){
        checkAllowChange();
        
        this._purpose = value;
           
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
     * xml name: relatedPlans
     *  
     */
    
    public java.util.List<io.nop.ai.agent.plan.model.AgentPlanRelatedRef> getRelatedPlans(){
      return _relatedPlans;
    }

    
    public void setRelatedPlans(java.util.List<io.nop.ai.agent.plan.model.AgentPlanRelatedRef> value){
        checkAllowChange();
        
        this._relatedPlans = KeyedList.fromList(value, io.nop.ai.agent.plan.model.AgentPlanRelatedRef::getId);
           
    }

    
    public io.nop.ai.agent.plan.model.AgentPlanRelatedRef getRelatedPlan(String name){
        return this._relatedPlans.getByKey(name);
    }

    public boolean hasRelatedPlan(String name){
        return this._relatedPlans.containsKey(name);
    }

    public void addRelatedPlan(io.nop.ai.agent.plan.model.AgentPlanRelatedRef item) {
        checkAllowChange();
        java.util.List<io.nop.ai.agent.plan.model.AgentPlanRelatedRef> list = this.getRelatedPlans();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.ai.agent.plan.model.AgentPlanRelatedRef::getId);
            setRelatedPlans(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_relatedPlans(){
        return this._relatedPlans.keySet();
    }

    public boolean hasRelatedPlans(){
        return !this._relatedPlans.isEmpty();
    }
    
    /**
     * 
     * xml name: reviewedAt
     *  
     */
    
    public java.time.LocalDate getReviewedAt(){
      return _reviewedAt;
    }

    
    public void setReviewedAt(java.time.LocalDate value){
        checkAllowChange();
        
        this._reviewedAt = value;
           
    }

    
    /**
     * 
     * xml name: scope
     *  
     */
    
    public io.nop.ai.agent.plan.model.AgentPlanScope getScope(){
      return _scope;
    }

    
    public void setScope(io.nop.ai.agent.plan.model.AgentPlanScope value){
        checkAllowChange();
        
        this._scope = value;
           
    }

    
    /**
     * 
     * xml name: sources
     *  
     */
    
    public java.util.List<io.nop.ai.agent.plan.model.AgentPlanSourceRef> getSources(){
      return _sources;
    }

    
    public void setSources(java.util.List<io.nop.ai.agent.plan.model.AgentPlanSourceRef> value){
        checkAllowChange();
        
        this._sources = KeyedList.fromList(value, io.nop.ai.agent.plan.model.AgentPlanSourceRef::getId);
           
    }

    
    public io.nop.ai.agent.plan.model.AgentPlanSourceRef getSource(String name){
        return this._sources.getByKey(name);
    }

    public boolean hasSource(String name){
        return this._sources.containsKey(name);
    }

    public void addSource(io.nop.ai.agent.plan.model.AgentPlanSourceRef item) {
        checkAllowChange();
        java.util.List<io.nop.ai.agent.plan.model.AgentPlanSourceRef> list = this.getSources();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.ai.agent.plan.model.AgentPlanSourceRef::getId);
            setSources(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_sources(){
        return this._sources.keySet();
    }

    public boolean hasSources(){
        return !this._sources.isEmpty();
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
     * xml name: successCriteria
     *  
     */
    
    public java.util.List<io.nop.ai.agent.plan.model.AgentPlanSuccessCriterion> getSuccessCriteria(){
      return _successCriteria;
    }

    
    public void setSuccessCriteria(java.util.List<io.nop.ai.agent.plan.model.AgentPlanSuccessCriterion> value){
        checkAllowChange();
        
        this._successCriteria = KeyedList.fromList(value, io.nop.ai.agent.plan.model.AgentPlanSuccessCriterion::getId);
           
    }

    
    public io.nop.ai.agent.plan.model.AgentPlanSuccessCriterion getCriterion(String name){
        return this._successCriteria.getByKey(name);
    }

    public boolean hasCriterion(String name){
        return this._successCriteria.containsKey(name);
    }

    public void addCriterion(io.nop.ai.agent.plan.model.AgentPlanSuccessCriterion item) {
        checkAllowChange();
        java.util.List<io.nop.ai.agent.plan.model.AgentPlanSuccessCriterion> list = this.getSuccessCriteria();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.ai.agent.plan.model.AgentPlanSuccessCriterion::getId);
            setSuccessCriteria(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_successCriteria(){
        return this._successCriteria.keySet();
    }

    public boolean hasSuccessCriteria(){
        return !this._successCriteria.isEmpty();
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
     * xml name: validationChecklist
     *  
     */
    
    public java.util.List<io.nop.ai.agent.plan.model.AgentPlanCriterion> getValidationChecklist(){
      return _validationChecklist;
    }

    
    public void setValidationChecklist(java.util.List<io.nop.ai.agent.plan.model.AgentPlanCriterion> value){
        checkAllowChange();
        
        this._validationChecklist = KeyedList.fromList(value, io.nop.ai.agent.plan.model.AgentPlanCriterion::getId);
           
    }

    
    public io.nop.ai.agent.plan.model.AgentPlanCriterion getCheck(String name){
        return this._validationChecklist.getByKey(name);
    }

    public boolean hasCheck(String name){
        return this._validationChecklist.containsKey(name);
    }

    public void addCheck(io.nop.ai.agent.plan.model.AgentPlanCriterion item) {
        checkAllowChange();
        java.util.List<io.nop.ai.agent.plan.model.AgentPlanCriterion> list = this.getValidationChecklist();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.ai.agent.plan.model.AgentPlanCriterion::getId);
            setValidationChecklist(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_validationChecklist(){
        return this._validationChecklist.keySet();
    }

    public boolean hasValidationChecklist(){
        return !this._validationChecklist.isEmpty();
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
        
           this._closure = io.nop.api.core.util.FreezeHelper.deepFreeze(this._closure);
            
           this._errors = io.nop.api.core.util.FreezeHelper.deepFreeze(this._errors);
            
           this._nonGoals = io.nop.api.core.util.FreezeHelper.deepFreeze(this._nonGoals);
            
           this._phases = io.nop.api.core.util.FreezeHelper.deepFreeze(this._phases);
            
           this._readFiles = io.nop.api.core.util.FreezeHelper.deepFreeze(this._readFiles);
            
           this._relatedPlans = io.nop.api.core.util.FreezeHelper.deepFreeze(this._relatedPlans);
            
           this._scope = io.nop.api.core.util.FreezeHelper.deepFreeze(this._scope);
            
           this._sources = io.nop.api.core.util.FreezeHelper.deepFreeze(this._sources);
            
           this._successCriteria = io.nop.api.core.util.FreezeHelper.deepFreeze(this._successCriteria);
            
           this._validationChecklist = io.nop.api.core.util.FreezeHelper.deepFreeze(this._validationChecklist);
            
           this._writtenFiles = io.nop.api.core.util.FreezeHelper.deepFreeze(this._writtenFiles);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("closure",this.getClosure());
        out.putNotNull("createdAt",this.getCreatedAt());
        out.putNotNull("currentBaseline",this.getCurrentBaseline());
        out.putNotNull("currentPhase",this.getCurrentPhase());
        out.putNotNull("currentTaskNo",this.getCurrentTaskNo());
        out.putNotNull("errors",this.getErrors());
        out.putNotNull("goal",this.getGoal());
        out.putNotNull("nonGoals",this.getNonGoals());
        out.putNotNull("phases",this.getPhases());
        out.putNotNull("purpose",this.getPurpose());
        out.putNotNull("readFiles",this.getReadFiles());
        out.putNotNull("relatedPlans",this.getRelatedPlans());
        out.putNotNull("reviewedAt",this.getReviewedAt());
        out.putNotNull("scope",this.getScope());
        out.putNotNull("sources",this.getSources());
        out.putNotNull("status",this.getStatus());
        out.putNotNull("successCriteria",this.getSuccessCriteria());
        out.putNotNull("title",this.getTitle());
        out.putNotNull("updatedAt",this.getUpdatedAt());
        out.putNotNull("validationChecklist",this.getValidationChecklist());
        out.putNotNull("writtenFiles",this.getWrittenFiles());
    }

    public AgentPlan cloneInstance(){
        AgentPlan instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(AgentPlan instance){
        super.copyTo(instance);
        
        instance.setClosure(this.getClosure());
        instance.setCreatedAt(this.getCreatedAt());
        instance.setCurrentBaseline(this.getCurrentBaseline());
        instance.setCurrentPhase(this.getCurrentPhase());
        instance.setCurrentTaskNo(this.getCurrentTaskNo());
        instance.setErrors(this.getErrors());
        instance.setGoal(this.getGoal());
        instance.setNonGoals(this.getNonGoals());
        instance.setPhases(this.getPhases());
        instance.setPurpose(this.getPurpose());
        instance.setReadFiles(this.getReadFiles());
        instance.setRelatedPlans(this.getRelatedPlans());
        instance.setReviewedAt(this.getReviewedAt());
        instance.setScope(this.getScope());
        instance.setSources(this.getSources());
        instance.setStatus(this.getStatus());
        instance.setSuccessCriteria(this.getSuccessCriteria());
        instance.setTitle(this.getTitle());
        instance.setUpdatedAt(this.getUpdatedAt());
        instance.setValidationChecklist(this.getValidationChecklist());
        instance.setWrittenFiles(this.getWrittenFiles());
    }

    protected AgentPlan newInstance(){
        return (AgentPlan) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
