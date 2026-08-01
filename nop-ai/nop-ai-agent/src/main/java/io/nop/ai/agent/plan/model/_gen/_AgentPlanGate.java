package io.nop.ai.agent.plan.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ai.agent.plan.model.AgentPlanGate;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/ai/agent-plan.xdef <p>
 * 阶段验收门控（design §14.1）：不满足时按 on-fail 动作处理
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _AgentPlanGate extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: criteria
     * 
     */
    private KeyedList<io.nop.ai.agent.plan.model.AgentPlanCriterion> _criteria = KeyedList.emptyList();
    
    /**
     *  
     * xml name: max-retries
     * 
     */
    private java.lang.Integer _maxRetries ;
    
    /**
     *  
     * xml name: on-fail
     * 
     */
    private io.nop.ai.agent.plan.model.GateOnFail _onFail ;
    
    /**
     *  
     * xml name: require-explicit-verdict
     * 
     */
    private java.lang.Boolean _requireExplicitVerdict ;
    
    /**
     *  
     * xml name: verdict
     * 
     */
    private java.lang.Boolean _verdict ;
    
    /**
     * 
     * xml name: criteria
     *  
     */
    
    public java.util.List<io.nop.ai.agent.plan.model.AgentPlanCriterion> getCriteria(){
      return _criteria;
    }

    
    public void setCriteria(java.util.List<io.nop.ai.agent.plan.model.AgentPlanCriterion> value){
        checkAllowChange();
        
        this._criteria = KeyedList.fromList(value, io.nop.ai.agent.plan.model.AgentPlanCriterion::getId);
           
    }

    
    public io.nop.ai.agent.plan.model.AgentPlanCriterion getCriterion(String name){
        return this._criteria.getByKey(name);
    }

    public boolean hasCriterion(String name){
        return this._criteria.containsKey(name);
    }

    public void addCriterion(io.nop.ai.agent.plan.model.AgentPlanCriterion item) {
        checkAllowChange();
        java.util.List<io.nop.ai.agent.plan.model.AgentPlanCriterion> list = this.getCriteria();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.ai.agent.plan.model.AgentPlanCriterion::getId);
            setCriteria(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_criteria(){
        return this._criteria.keySet();
    }

    public boolean hasCriteria(){
        return !this._criteria.isEmpty();
    }
    
    /**
     * 
     * xml name: max-retries
     *  
     */
    
    public java.lang.Integer getMaxRetries(){
      return _maxRetries;
    }

    
    public void setMaxRetries(java.lang.Integer value){
        checkAllowChange();
        
        this._maxRetries = value;
           
    }

    
    /**
     * 
     * xml name: on-fail
     *  
     */
    
    public io.nop.ai.agent.plan.model.GateOnFail getOnFail(){
      return _onFail;
    }

    
    public void setOnFail(io.nop.ai.agent.plan.model.GateOnFail value){
        checkAllowChange();
        
        this._onFail = value;
           
    }

    
    /**
     * 
     * xml name: require-explicit-verdict
     *  
     */
    
    public java.lang.Boolean getRequireExplicitVerdict(){
      return _requireExplicitVerdict;
    }

    
    public void setRequireExplicitVerdict(java.lang.Boolean value){
        checkAllowChange();
        
        this._requireExplicitVerdict = value;
           
    }

    
    /**
     * 
     * xml name: verdict
     *  
     */
    
    public java.lang.Boolean getVerdict(){
      return _verdict;
    }

    
    public void setVerdict(java.lang.Boolean value){
        checkAllowChange();
        
        this._verdict = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._criteria = io.nop.api.core.util.FreezeHelper.deepFreeze(this._criteria);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("criteria",this.getCriteria());
        out.putNotNull("maxRetries",this.getMaxRetries());
        out.putNotNull("onFail",this.getOnFail());
        out.putNotNull("requireExplicitVerdict",this.getRequireExplicitVerdict());
        out.putNotNull("verdict",this.getVerdict());
    }

    public AgentPlanGate cloneInstance(){
        AgentPlanGate instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(AgentPlanGate instance){
        super.copyTo(instance);
        
        instance.setCriteria(this.getCriteria());
        instance.setMaxRetries(this.getMaxRetries());
        instance.setOnFail(this.getOnFail());
        instance.setRequireExplicitVerdict(this.getRequireExplicitVerdict());
        instance.setVerdict(this.getVerdict());
    }

    protected AgentPlanGate newInstance(){
        return (AgentPlanGate) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
