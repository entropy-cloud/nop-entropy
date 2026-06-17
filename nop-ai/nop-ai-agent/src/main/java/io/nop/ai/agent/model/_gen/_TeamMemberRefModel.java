package io.nop.ai.agent.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ai.agent.model.TeamMemberRefModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/ai/agent.xdef <p>
 * Plan 231: declarative team-member reference. A member agent declares
 * its team membership here; the engine auto-binds the member session
 * when a functional ITeamManager is wired. Optional; absent => the
 * agent does not join a team.
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _TeamMemberRefModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: memberName
     * 
     */
    private java.lang.String _memberName ;
    
    /**
     *  
     * xml name: teamName
     * 
     */
    private java.lang.String _teamName ;
    
    /**
     * 
     * xml name: memberName
     *  
     */
    
    public java.lang.String getMemberName(){
      return _memberName;
    }

    
    public void setMemberName(java.lang.String value){
        checkAllowChange();
        
        this._memberName = value;
           
    }

    
    /**
     * 
     * xml name: teamName
     *  
     */
    
    public java.lang.String getTeamName(){
      return _teamName;
    }

    
    public void setTeamName(java.lang.String value){
        checkAllowChange();
        
        this._teamName = value;
           
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
        
        out.putNotNull("memberName",this.getMemberName());
        out.putNotNull("teamName",this.getTeamName());
    }

    public TeamMemberRefModel cloneInstance(){
        TeamMemberRefModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(TeamMemberRefModel instance){
        super.copyTo(instance);
        
        instance.setMemberName(this.getMemberName());
        instance.setTeamName(this.getTeamName());
    }

    protected TeamMemberRefModel newInstance(){
        return (TeamMemberRefModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
