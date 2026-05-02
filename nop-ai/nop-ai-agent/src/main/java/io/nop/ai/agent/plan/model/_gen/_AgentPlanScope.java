package io.nop.ai.agent.plan.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ai.agent.plan.model.AgentPlanScope;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/ai/agent-plan.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _AgentPlanScope extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: inScope
     * 
     */
    private KeyedList<io.nop.ai.agent.plan.model.AgentPlanScopeItem> _inScope = KeyedList.emptyList();
    
    /**
     *  
     * xml name: outOfScope
     * 
     */
    private KeyedList<io.nop.ai.agent.plan.model.AgentPlanScopeItem> _outOfScope = KeyedList.emptyList();
    
    /**
     * 
     * xml name: inScope
     *  
     */
    
    public java.util.List<io.nop.ai.agent.plan.model.AgentPlanScopeItem> getInScope(){
      return _inScope;
    }

    
    public void setInScope(java.util.List<io.nop.ai.agent.plan.model.AgentPlanScopeItem> value){
        checkAllowChange();
        
        this._inScope = KeyedList.fromList(value, io.nop.ai.agent.plan.model.AgentPlanScopeItem::getId);
           
    }

    
    public io.nop.ai.agent.plan.model.AgentPlanScopeItem getInItem(String name){
        return this._inScope.getByKey(name);
    }

    public boolean hasInItem(String name){
        return this._inScope.containsKey(name);
    }

    public void addInItem(io.nop.ai.agent.plan.model.AgentPlanScopeItem item) {
        checkAllowChange();
        java.util.List<io.nop.ai.agent.plan.model.AgentPlanScopeItem> list = this.getInScope();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.ai.agent.plan.model.AgentPlanScopeItem::getId);
            setInScope(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_inScope(){
        return this._inScope.keySet();
    }

    public boolean hasInScope(){
        return !this._inScope.isEmpty();
    }
    
    /**
     * 
     * xml name: outOfScope
     *  
     */
    
    public java.util.List<io.nop.ai.agent.plan.model.AgentPlanScopeItem> getOutOfScope(){
      return _outOfScope;
    }

    
    public void setOutOfScope(java.util.List<io.nop.ai.agent.plan.model.AgentPlanScopeItem> value){
        checkAllowChange();
        
        this._outOfScope = KeyedList.fromList(value, io.nop.ai.agent.plan.model.AgentPlanScopeItem::getId);
           
    }

    
    public io.nop.ai.agent.plan.model.AgentPlanScopeItem getOutItem(String name){
        return this._outOfScope.getByKey(name);
    }

    public boolean hasOutItem(String name){
        return this._outOfScope.containsKey(name);
    }

    public void addOutItem(io.nop.ai.agent.plan.model.AgentPlanScopeItem item) {
        checkAllowChange();
        java.util.List<io.nop.ai.agent.plan.model.AgentPlanScopeItem> list = this.getOutOfScope();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.ai.agent.plan.model.AgentPlanScopeItem::getId);
            setOutOfScope(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_outOfScope(){
        return this._outOfScope.keySet();
    }

    public boolean hasOutOfScope(){
        return !this._outOfScope.isEmpty();
    }
    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._inScope = io.nop.api.core.util.FreezeHelper.deepFreeze(this._inScope);
            
           this._outOfScope = io.nop.api.core.util.FreezeHelper.deepFreeze(this._outOfScope);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("inScope",this.getInScope());
        out.putNotNull("outOfScope",this.getOutOfScope());
    }

    public AgentPlanScope cloneInstance(){
        AgentPlanScope instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(AgentPlanScope instance){
        super.copyTo(instance);
        
        instance.setInScope(this.getInScope());
        instance.setOutOfScope(this.getOutOfScope());
    }

    protected AgentPlanScope newInstance(){
        return (AgentPlanScope) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
