package io.nop.ai.agent.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ai.agent.model.TeamMemberModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/ai/agent.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _TeamMemberModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: agentModel
     * 
     */
    private java.lang.String _agentModel ;
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: role
     * 
     */
    private io.nop.ai.agent.team.MemberRole _role ;
    
    /**
     * 
     * xml name: agentModel
     *  
     */
    
    public java.lang.String getAgentModel(){
      return _agentModel;
    }

    
    public void setAgentModel(java.lang.String value){
        checkAllowChange();
        
        this._agentModel = value;
           
    }

    
    /**
     * 
     * xml name: name
     *  
     */
    
    public java.lang.String getName(){
      return _name;
    }

    
    public void setName(java.lang.String value){
        checkAllowChange();
        
        this._name = value;
           
    }

    
    /**
     * 
     * xml name: role
     *  
     */
    
    public io.nop.ai.agent.team.MemberRole getRole(){
      return _role;
    }

    
    public void setRole(io.nop.ai.agent.team.MemberRole value){
        checkAllowChange();
        
        this._role = value;
           
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
        
        out.putNotNull("agentModel",this.getAgentModel());
        out.putNotNull("name",this.getName());
        out.putNotNull("role",this.getRole());
    }

    public TeamMemberModel cloneInstance(){
        TeamMemberModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(TeamMemberModel instance){
        super.copyTo(instance);
        
        instance.setAgentModel(this.getAgentModel());
        instance.setName(this.getName());
        instance.setRole(this.getRole());
    }

    protected TeamMemberModel newInstance(){
        return (TeamMemberModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
