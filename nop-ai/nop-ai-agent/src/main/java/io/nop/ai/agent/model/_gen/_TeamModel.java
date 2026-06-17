package io.nop.ai.agent.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ai.agent.model.TeamModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/ai/agent.xdef <p>
 * Plan 231: declarative team declaration. A lead agent declares its
 * team structure here; the engine auto-binds the lead session at the
 * three execution entry points (doExecute/resumeSession/restoreSession)
 * when a functional ITeamManager is wired. Optional; absent => the
 * agent does not lead a team.
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _TeamModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: description
     * 
     */
    private java.lang.String _description ;
    
    /**
     *  
     * xml name: leadAgentName
     * 
     */
    private java.lang.String _leadAgentName ;
    
    /**
     *  
     * xml name: maxParallelMembers
     * 
     */
    private java.lang.Integer _maxParallelMembers ;
    
    /**
     *  
     * xml name: members
     * 
     */
    private KeyedList<io.nop.ai.agent.model.TeamMemberModel> _members = KeyedList.emptyList();
    
    /**
     *  
     * xml name: teamName
     * 
     */
    private java.lang.String _teamName ;
    
    /**
     * 
     * xml name: description
     *  
     */
    
    public java.lang.String getDescription(){
      return _description;
    }

    
    public void setDescription(java.lang.String value){
        checkAllowChange();
        
        this._description = value;
           
    }

    
    /**
     * 
     * xml name: leadAgentName
     *  
     */
    
    public java.lang.String getLeadAgentName(){
      return _leadAgentName;
    }

    
    public void setLeadAgentName(java.lang.String value){
        checkAllowChange();
        
        this._leadAgentName = value;
           
    }

    
    /**
     * 
     * xml name: maxParallelMembers
     *  
     */
    
    public java.lang.Integer getMaxParallelMembers(){
      return _maxParallelMembers;
    }

    
    public void setMaxParallelMembers(java.lang.Integer value){
        checkAllowChange();
        
        this._maxParallelMembers = value;
           
    }

    
    /**
     * 
     * xml name: members
     *  
     */
    
    public java.util.List<io.nop.ai.agent.model.TeamMemberModel> getMembers(){
      return _members;
    }

    
    public void setMembers(java.util.List<io.nop.ai.agent.model.TeamMemberModel> value){
        checkAllowChange();
        
        this._members = KeyedList.fromList(value, io.nop.ai.agent.model.TeamMemberModel::getName);
           
    }

    
    public io.nop.ai.agent.model.TeamMemberModel getMember(String name){
        return this._members.getByKey(name);
    }

    public boolean hasMember(String name){
        return this._members.containsKey(name);
    }

    public void addMember(io.nop.ai.agent.model.TeamMemberModel item) {
        checkAllowChange();
        java.util.List<io.nop.ai.agent.model.TeamMemberModel> list = this.getMembers();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.ai.agent.model.TeamMemberModel::getName);
            setMembers(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_members(){
        return this._members.keySet();
    }

    public boolean hasMembers(){
        return !this._members.isEmpty();
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
        
           this._members = io.nop.api.core.util.FreezeHelper.deepFreeze(this._members);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("description",this.getDescription());
        out.putNotNull("leadAgentName",this.getLeadAgentName());
        out.putNotNull("maxParallelMembers",this.getMaxParallelMembers());
        out.putNotNull("members",this.getMembers());
        out.putNotNull("teamName",this.getTeamName());
    }

    public TeamModel cloneInstance(){
        TeamModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(TeamModel instance){
        super.copyTo(instance);
        
        instance.setDescription(this.getDescription());
        instance.setLeadAgentName(this.getLeadAgentName());
        instance.setMaxParallelMembers(this.getMaxParallelMembers());
        instance.setMembers(this.getMembers());
        instance.setTeamName(this.getTeamName());
    }

    protected TeamModel newInstance(){
        return (TeamModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
