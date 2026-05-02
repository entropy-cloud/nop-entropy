package io.nop.ai.agent.plan.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ai.agent.plan.model.AgentPlanCriterion;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/ai/agent-plan.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _AgentPlanCriterion extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: blocking
     * 
     */
    private java.lang.Boolean _blocking ;
    
    /**
     *  
     * xml name: 
     * 
     */
    private java.lang.String _body ;
    
    /**
     *  
     * xml name: completed
     * 
     */
    private java.lang.Boolean _completed ;
    
    /**
     *  
     * xml name: id
     * 
     */
    private java.lang.String _id ;
    
    /**
     *  
     * xml name: required
     * 
     */
    private java.lang.Boolean _required ;
    
    /**
     * 
     * xml name: blocking
     *  
     */
    
    public java.lang.Boolean getBlocking(){
      return _blocking;
    }

    
    public void setBlocking(java.lang.Boolean value){
        checkAllowChange();
        
        this._blocking = value;
           
    }

    
    /**
     * 
     * xml name: 
     *  
     */
    
    public java.lang.String getBody(){
      return _body;
    }

    
    public void setBody(java.lang.String value){
        checkAllowChange();
        
        this._body = value;
           
    }

    
    /**
     * 
     * xml name: completed
     *  
     */
    
    public java.lang.Boolean getCompleted(){
      return _completed;
    }

    
    public void setCompleted(java.lang.Boolean value){
        checkAllowChange();
        
        this._completed = value;
           
    }

    
    /**
     * 
     * xml name: id
     *  
     */
    
    public java.lang.String getId(){
      return _id;
    }

    
    public void setId(java.lang.String value){
        checkAllowChange();
        
        this._id = value;
           
    }

    
    /**
     * 
     * xml name: required
     *  
     */
    
    public java.lang.Boolean getRequired(){
      return _required;
    }

    
    public void setRequired(java.lang.Boolean value){
        checkAllowChange();
        
        this._required = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("blocking",this.getBlocking());
        out.putNotNull("body",this.getBody());
        out.putNotNull("completed",this.getCompleted());
        out.putNotNull("id",this.getId());
        out.putNotNull("required",this.getRequired());
    }

    public AgentPlanCriterion cloneInstance(){
        AgentPlanCriterion instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(AgentPlanCriterion instance){
        super.copyTo(instance);
        
        instance.setBlocking(this.getBlocking());
        instance.setBody(this.getBody());
        instance.setCompleted(this.getCompleted());
        instance.setId(this.getId());
        instance.setRequired(this.getRequired());
    }

    protected AgentPlanCriterion newInstance(){
        return (AgentPlanCriterion) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
