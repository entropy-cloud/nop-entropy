package io.nop.ai.agent.plan.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ai.agent.plan.model.AgentPlanClosure;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/ai/agent-plan.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _AgentPlanClosure extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: auditEvidence
     * 
     */
    private java.lang.String _auditEvidence ;
    
    /**
     *  
     * xml name: completedAt
     * 
     */
    private java.time.LocalDateTime _completedAt ;
    
    /**
     *  
     * xml name: followUps
     * 
     */
    private KeyedList<io.nop.ai.agent.plan.model.AgentPlanFollowUp> _followUps = KeyedList.emptyList();
    
    /**
     *  
     * xml name: reviewedAt
     * 
     */
    private java.time.LocalDateTime _reviewedAt ;
    
    /**
     *  
     * xml name: reviewedBy
     * 
     */
    private java.lang.String _reviewedBy ;
    
    /**
     *  
     * xml name: statusNote
     * 
     */
    private java.lang.String _statusNote ;
    
    /**
     * 
     * xml name: auditEvidence
     *  
     */
    
    public java.lang.String getAuditEvidence(){
      return _auditEvidence;
    }

    
    public void setAuditEvidence(java.lang.String value){
        checkAllowChange();
        
        this._auditEvidence = value;
           
    }

    
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
     * xml name: followUps
     *  
     */
    
    public java.util.List<io.nop.ai.agent.plan.model.AgentPlanFollowUp> getFollowUps(){
      return _followUps;
    }

    
    public void setFollowUps(java.util.List<io.nop.ai.agent.plan.model.AgentPlanFollowUp> value){
        checkAllowChange();
        
        this._followUps = KeyedList.fromList(value, io.nop.ai.agent.plan.model.AgentPlanFollowUp::getId);
           
    }

    
    public io.nop.ai.agent.plan.model.AgentPlanFollowUp getFollowUp(String name){
        return this._followUps.getByKey(name);
    }

    public boolean hasFollowUp(String name){
        return this._followUps.containsKey(name);
    }

    public void addFollowUp(io.nop.ai.agent.plan.model.AgentPlanFollowUp item) {
        checkAllowChange();
        java.util.List<io.nop.ai.agent.plan.model.AgentPlanFollowUp> list = this.getFollowUps();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.ai.agent.plan.model.AgentPlanFollowUp::getId);
            setFollowUps(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_followUps(){
        return this._followUps.keySet();
    }

    public boolean hasFollowUps(){
        return !this._followUps.isEmpty();
    }
    
    /**
     * 
     * xml name: reviewedAt
     *  
     */
    
    public java.time.LocalDateTime getReviewedAt(){
      return _reviewedAt;
    }

    
    public void setReviewedAt(java.time.LocalDateTime value){
        checkAllowChange();
        
        this._reviewedAt = value;
           
    }

    
    /**
     * 
     * xml name: reviewedBy
     *  
     */
    
    public java.lang.String getReviewedBy(){
      return _reviewedBy;
    }

    
    public void setReviewedBy(java.lang.String value){
        checkAllowChange();
        
        this._reviewedBy = value;
           
    }

    
    /**
     * 
     * xml name: statusNote
     *  
     */
    
    public java.lang.String getStatusNote(){
      return _statusNote;
    }

    
    public void setStatusNote(java.lang.String value){
        checkAllowChange();
        
        this._statusNote = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._followUps = io.nop.api.core.util.FreezeHelper.deepFreeze(this._followUps);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("auditEvidence",this.getAuditEvidence());
        out.putNotNull("completedAt",this.getCompletedAt());
        out.putNotNull("followUps",this.getFollowUps());
        out.putNotNull("reviewedAt",this.getReviewedAt());
        out.putNotNull("reviewedBy",this.getReviewedBy());
        out.putNotNull("statusNote",this.getStatusNote());
    }

    public AgentPlanClosure cloneInstance(){
        AgentPlanClosure instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(AgentPlanClosure instance){
        super.copyTo(instance);
        
        instance.setAuditEvidence(this.getAuditEvidence());
        instance.setCompletedAt(this.getCompletedAt());
        instance.setFollowUps(this.getFollowUps());
        instance.setReviewedAt(this.getReviewedAt());
        instance.setReviewedBy(this.getReviewedBy());
        instance.setStatusNote(this.getStatusNote());
    }

    protected AgentPlanClosure newInstance(){
        return (AgentPlanClosure) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
