package io.nop.ai.agent.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ai.agent.model.AgentModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/ai/agent.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _AgentModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: activeTags
     * Plan 296 (WS2): tag-based tool visibility. activeTags selects tools by
     * tag intersection (empty = no tag restriction); denyTags removes tools
     * containing any of these tags; denyTools removes tools by name.
     */
    private java.util.Set<java.lang.String> _activeTags ;
    
    /**
     *  
     * xml name: availableSkills
     * 
     */
    private java.util.Set<java.lang.String> _availableSkills ;
    
    /**
     *  
     * xml name: chatOptions
     * 可选的AI大模型访问参数
     */
    private io.nop.ai.core.model.ChatOptionsModel _chatOptions ;
    
    /**
     *  
     * xml name: constraints
     * 
     */
    private io.nop.ai.agent.model.AgentConstraintsModel _constraints ;
    
    /**
     *  
     * xml name: denyTags
     * 
     */
    private java.util.Set<java.lang.String> _denyTags ;
    
    /**
     *  
     * xml name: denyTools
     * 
     */
    private java.util.Set<java.lang.String> _denyTools ;
    
    /**
     *  
     * xml name: description
     * 
     */
    private java.lang.String _description ;
    
    /**
     *  
     * xml name: hooks
     * 
     */
    private KeyedList<io.nop.ai.agent.model.AgentHookModel> _hooks = KeyedList.emptyList();
    
    /**
     *  
     * xml name: meta
     * 元数据
     */
    private java.util.Map<java.lang.String,java.lang.Object> _meta ;
    
    /**
     *  
     * xml name: middlewares
     * Plan 296 (WS1): onion-style middleware declarations. Each middleware
     * wraps the lifecycle point's core (which runs all registered hooks).
     * impl = fully-qualified IAgentMiddleware implementation class name;
     * point = lifecycle point name (e.g. pre_call, pre_reasoning, ...).
     * Uses impl instead of class to avoid the Java reserved-word conflict
     * in generated getter (getClass).
     */
    private java.util.List<io.nop.ai.agent.model.AgentMiddlewareModel> _middlewares = java.util.Collections.emptyList();
    
    /**
     *  
     * xml name: mode
     * 
     */
    private java.lang.String _mode ;
    
    /**
     *  
     * xml name: name
     * agent的唯一名称
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: path-rules
     * per-agent glob path-rules: ordered allow/deny patterns, first-match-wins
     */
    private java.util.List<io.nop.ai.agent.model.PathRuleModel> _pathRules = java.util.Collections.emptyList();
    
    /**
     *  
     * xml name: permissions
     * 
     */
    private KeyedList<io.nop.ai.agent.model.AgentPermissionModel> _permissions = KeyedList.emptyList();
    
    /**
     *  
     * xml name: prompt
     * 系统提示词，指导agent的行为模式
     */
    private io.nop.ai.core.prompt.node.IPromptSyntaxNode _prompt ;
    
    /**
     *  
     * xml name: requiredSkills
     * 
     */
    private java.util.Set<java.lang.String> _requiredSkills ;
    
    /**
     *  
     * xml name: tagSet
     * 
     */
    private java.util.Set<java.lang.String> _tagSet ;
    
    /**
     *  
     * xml name: team
     * Plan 231: declarative team declaration. A lead agent declares its
     * team structure here; the engine auto-binds the lead session at the
     * three execution entry points (doExecute/resumeSession/restoreSession)
     * when a functional ITeamManager is wired. Optional; absent => the
     * agent does not lead a team.
     */
    private io.nop.ai.agent.model.TeamModel _team ;
    
    /**
     *  
     * xml name: team-member
     * Plan 231: declarative team-member reference. A member agent declares
     * its team membership here; the engine auto-binds the member session
     * when a functional ITeamManager is wired. Optional; absent => the
     * agent does not join a team.
     */
    private io.nop.ai.agent.model.TeamMemberRefModel _teamMember ;
    
    /**
     *  
     * xml name: tools
     * 
     */
    private java.util.Set<java.lang.String> _tools ;
    
    /**
     *  
     * xml name: workDir
     * 
     */
    private java.lang.String _workDir ;
    
    /**
     * 
     * xml name: activeTags
     *  Plan 296 (WS2): tag-based tool visibility. activeTags selects tools by
     * tag intersection (empty = no tag restriction); denyTags removes tools
     * containing any of these tags; denyTools removes tools by name.
     */
    
    public java.util.Set<java.lang.String> getActiveTags(){
      return _activeTags;
    }

    
    public void setActiveTags(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._activeTags = value;
           
    }

    
    /**
     * 
     * xml name: availableSkills
     *  
     */
    
    public java.util.Set<java.lang.String> getAvailableSkills(){
      return _availableSkills;
    }

    
    public void setAvailableSkills(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._availableSkills = value;
           
    }

    
    /**
     * 
     * xml name: chatOptions
     *  可选的AI大模型访问参数
     */
    
    public io.nop.ai.core.model.ChatOptionsModel getChatOptions(){
      return _chatOptions;
    }

    
    public void setChatOptions(io.nop.ai.core.model.ChatOptionsModel value){
        checkAllowChange();
        
        this._chatOptions = value;
           
    }

    
    /**
     * 
     * xml name: constraints
     *  
     */
    
    public io.nop.ai.agent.model.AgentConstraintsModel getConstraints(){
      return _constraints;
    }

    
    public void setConstraints(io.nop.ai.agent.model.AgentConstraintsModel value){
        checkAllowChange();
        
        this._constraints = value;
           
    }

    
    /**
     * 
     * xml name: denyTags
     *  
     */
    
    public java.util.Set<java.lang.String> getDenyTags(){
      return _denyTags;
    }

    
    public void setDenyTags(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._denyTags = value;
           
    }

    
    /**
     * 
     * xml name: denyTools
     *  
     */
    
    public java.util.Set<java.lang.String> getDenyTools(){
      return _denyTools;
    }

    
    public void setDenyTools(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._denyTools = value;
           
    }

    
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
     * xml name: hooks
     *  
     */
    
    public java.util.List<io.nop.ai.agent.model.AgentHookModel> getHooks(){
      return _hooks;
    }

    
    public void setHooks(java.util.List<io.nop.ai.agent.model.AgentHookModel> value){
        checkAllowChange();
        
        this._hooks = KeyedList.fromList(value, io.nop.ai.agent.model.AgentHookModel::getId);
           
    }

    
    public io.nop.ai.agent.model.AgentHookModel getOn(String name){
        return this._hooks.getByKey(name);
    }

    public boolean hasOn(String name){
        return this._hooks.containsKey(name);
    }

    public void addOn(io.nop.ai.agent.model.AgentHookModel item) {
        checkAllowChange();
        java.util.List<io.nop.ai.agent.model.AgentHookModel> list = this.getHooks();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.ai.agent.model.AgentHookModel::getId);
            setHooks(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_hooks(){
        return this._hooks.keySet();
    }

    public boolean hasHooks(){
        return !this._hooks.isEmpty();
    }
    
    /**
     * 
     * xml name: meta
     *  元数据
     */
    
    public java.util.Map<java.lang.String,java.lang.Object> getMeta(){
      return _meta;
    }

    
    public void setMeta(java.util.Map<java.lang.String,java.lang.Object> value){
        checkAllowChange();
        
        this._meta = value;
           
    }

    
    public boolean hasMeta(){
        return this._meta != null && !this._meta.isEmpty();
    }
    
    /**
     * 
     * xml name: middlewares
     *  Plan 296 (WS1): onion-style middleware declarations. Each middleware
     * wraps the lifecycle point's core (which runs all registered hooks).
     * impl = fully-qualified IAgentMiddleware implementation class name;
     * point = lifecycle point name (e.g. pre_call, pre_reasoning, ...).
     * Uses impl instead of class to avoid the Java reserved-word conflict
     * in generated getter (getClass).
     */
    
    public java.util.List<io.nop.ai.agent.model.AgentMiddlewareModel> getMiddlewares(){
      return _middlewares;
    }

    
    public void setMiddlewares(java.util.List<io.nop.ai.agent.model.AgentMiddlewareModel> value){
        checkAllowChange();
        
        this._middlewares = value;
           
    }

    
    /**
     * 
     * xml name: mode
     *  
     */
    
    public java.lang.String getMode(){
      return _mode;
    }

    
    public void setMode(java.lang.String value){
        checkAllowChange();
        
        this._mode = value;
           
    }

    
    /**
     * 
     * xml name: name
     *  agent的唯一名称
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
     * xml name: path-rules
     *  per-agent glob path-rules: ordered allow/deny patterns, first-match-wins
     */
    
    public java.util.List<io.nop.ai.agent.model.PathRuleModel> getPathRules(){
      return _pathRules;
    }

    
    public void setPathRules(java.util.List<io.nop.ai.agent.model.PathRuleModel> value){
        checkAllowChange();
        
        this._pathRules = value;
           
    }

    
    /**
     * 
     * xml name: permissions
     *  
     */
    
    public java.util.List<io.nop.ai.agent.model.AgentPermissionModel> getPermissions(){
      return _permissions;
    }

    
    public void setPermissions(java.util.List<io.nop.ai.agent.model.AgentPermissionModel> value){
        checkAllowChange();
        
        this._permissions = KeyedList.fromList(value, io.nop.ai.agent.model.AgentPermissionModel::getId);
           
    }

    
    public io.nop.ai.agent.model.AgentPermissionModel getPermission(String name){
        return this._permissions.getByKey(name);
    }

    public boolean hasPermission(String name){
        return this._permissions.containsKey(name);
    }

    public void addPermission(io.nop.ai.agent.model.AgentPermissionModel item) {
        checkAllowChange();
        java.util.List<io.nop.ai.agent.model.AgentPermissionModel> list = this.getPermissions();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.ai.agent.model.AgentPermissionModel::getId);
            setPermissions(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_permissions(){
        return this._permissions.keySet();
    }

    public boolean hasPermissions(){
        return !this._permissions.isEmpty();
    }
    
    /**
     * 
     * xml name: prompt
     *  系统提示词，指导agent的行为模式
     */
    
    public io.nop.ai.core.prompt.node.IPromptSyntaxNode getPrompt(){
      return _prompt;
    }

    
    public void setPrompt(io.nop.ai.core.prompt.node.IPromptSyntaxNode value){
        checkAllowChange();
        
        this._prompt = value;
           
    }

    
    /**
     * 
     * xml name: requiredSkills
     *  
     */
    
    public java.util.Set<java.lang.String> getRequiredSkills(){
      return _requiredSkills;
    }

    
    public void setRequiredSkills(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._requiredSkills = value;
           
    }

    
    /**
     * 
     * xml name: tagSet
     *  
     */
    
    public java.util.Set<java.lang.String> getTagSet(){
      return _tagSet;
    }

    
    public void setTagSet(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._tagSet = value;
           
    }

    
    /**
     * 
     * xml name: team
     *  Plan 231: declarative team declaration. A lead agent declares its
     * team structure here; the engine auto-binds the lead session at the
     * three execution entry points (doExecute/resumeSession/restoreSession)
     * when a functional ITeamManager is wired. Optional; absent => the
     * agent does not lead a team.
     */
    
    public io.nop.ai.agent.model.TeamModel getTeam(){
      return _team;
    }

    
    public void setTeam(io.nop.ai.agent.model.TeamModel value){
        checkAllowChange();
        
        this._team = value;
           
    }

    
    /**
     * 
     * xml name: team-member
     *  Plan 231: declarative team-member reference. A member agent declares
     * its team membership here; the engine auto-binds the member session
     * when a functional ITeamManager is wired. Optional; absent => the
     * agent does not join a team.
     */
    
    public io.nop.ai.agent.model.TeamMemberRefModel getTeamMember(){
      return _teamMember;
    }

    
    public void setTeamMember(io.nop.ai.agent.model.TeamMemberRefModel value){
        checkAllowChange();
        
        this._teamMember = value;
           
    }

    
    /**
     * 
     * xml name: tools
     *  
     */
    
    public java.util.Set<java.lang.String> getTools(){
      return _tools;
    }

    
    public void setTools(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._tools = value;
           
    }

    
    /**
     * 
     * xml name: workDir
     *  
     */
    
    public java.lang.String getWorkDir(){
      return _workDir;
    }

    
    public void setWorkDir(java.lang.String value){
        checkAllowChange();
        
        this._workDir = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._chatOptions = io.nop.api.core.util.FreezeHelper.deepFreeze(this._chatOptions);
            
           this._constraints = io.nop.api.core.util.FreezeHelper.deepFreeze(this._constraints);
            
           this._hooks = io.nop.api.core.util.FreezeHelper.deepFreeze(this._hooks);
            
           this._middlewares = io.nop.api.core.util.FreezeHelper.deepFreeze(this._middlewares);
            
           this._pathRules = io.nop.api.core.util.FreezeHelper.deepFreeze(this._pathRules);
            
           this._permissions = io.nop.api.core.util.FreezeHelper.deepFreeze(this._permissions);
            
           this._team = io.nop.api.core.util.FreezeHelper.deepFreeze(this._team);
            
           this._teamMember = io.nop.api.core.util.FreezeHelper.deepFreeze(this._teamMember);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("activeTags",this.getActiveTags());
        out.putNotNull("availableSkills",this.getAvailableSkills());
        out.putNotNull("chatOptions",this.getChatOptions());
        out.putNotNull("constraints",this.getConstraints());
        out.putNotNull("denyTags",this.getDenyTags());
        out.putNotNull("denyTools",this.getDenyTools());
        out.putNotNull("description",this.getDescription());
        out.putNotNull("hooks",this.getHooks());
        out.putNotNull("meta",this.getMeta());
        out.putNotNull("middlewares",this.getMiddlewares());
        out.putNotNull("mode",this.getMode());
        out.putNotNull("name",this.getName());
        out.putNotNull("pathRules",this.getPathRules());
        out.putNotNull("permissions",this.getPermissions());
        out.putNotNull("prompt",this.getPrompt());
        out.putNotNull("requiredSkills",this.getRequiredSkills());
        out.putNotNull("tagSet",this.getTagSet());
        out.putNotNull("team",this.getTeam());
        out.putNotNull("teamMember",this.getTeamMember());
        out.putNotNull("tools",this.getTools());
        out.putNotNull("workDir",this.getWorkDir());
    }

    public AgentModel cloneInstance(){
        AgentModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(AgentModel instance){
        super.copyTo(instance);
        
        instance.setActiveTags(this.getActiveTags());
        instance.setAvailableSkills(this.getAvailableSkills());
        instance.setChatOptions(this.getChatOptions());
        instance.setConstraints(this.getConstraints());
        instance.setDenyTags(this.getDenyTags());
        instance.setDenyTools(this.getDenyTools());
        instance.setDescription(this.getDescription());
        instance.setHooks(this.getHooks());
        instance.setMeta(this.getMeta());
        instance.setMiddlewares(this.getMiddlewares());
        instance.setMode(this.getMode());
        instance.setName(this.getName());
        instance.setPathRules(this.getPathRules());
        instance.setPermissions(this.getPermissions());
        instance.setPrompt(this.getPrompt());
        instance.setRequiredSkills(this.getRequiredSkills());
        instance.setTagSet(this.getTagSet());
        instance.setTeam(this.getTeam());
        instance.setTeamMember(this.getTeamMember());
        instance.setTools(this.getTools());
        instance.setWorkDir(this.getWorkDir());
    }

    protected AgentModel newInstance(){
        return (AgentModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
