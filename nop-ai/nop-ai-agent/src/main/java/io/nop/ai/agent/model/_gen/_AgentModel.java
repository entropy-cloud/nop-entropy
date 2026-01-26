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
     * xml name: capabilities
     * 
     */
    private java.util.Set<java.lang.String> _capabilities ;
    
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
     * xml name: name
     * agent的唯一名称
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: permissions
     * 
     */
    private java.util.List<io.nop.ai.agent.model.AgentPermissionModel> _permissions = java.util.Collections.emptyList();
    
    /**
     *  
     * xml name: prompt
     * 系统提示词，指导agent的行为模式
     */
    private io.nop.ai.core.prompt.node.IPromptSyntaxNode _prompt ;
    
    /**
     *  
     * xml name: tagSet
     * 
     */
    private java.util.Set<java.lang.String> _tagSet ;
    
    /**
     *  
     * xml name: tools
     * 
     */
    private java.util.Set<java.lang.String> _tools ;
    
    /**
     * 
     * xml name: capabilities
     *  
     */
    
    public java.util.Set<java.lang.String> getCapabilities(){
      return _capabilities;
    }

    
    public void setCapabilities(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._capabilities = value;
           
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
     * xml name: permissions
     *  
     */
    
    public java.util.List<io.nop.ai.agent.model.AgentPermissionModel> getPermissions(){
      return _permissions;
    }

    
    public void setPermissions(java.util.List<io.nop.ai.agent.model.AgentPermissionModel> value){
        checkAllowChange();
        
        this._permissions = value;
           
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

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._chatOptions = io.nop.api.core.util.FreezeHelper.deepFreeze(this._chatOptions);
            
           this._constraints = io.nop.api.core.util.FreezeHelper.deepFreeze(this._constraints);
            
           this._hooks = io.nop.api.core.util.FreezeHelper.deepFreeze(this._hooks);
            
           this._permissions = io.nop.api.core.util.FreezeHelper.deepFreeze(this._permissions);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("capabilities",this.getCapabilities());
        out.putNotNull("chatOptions",this.getChatOptions());
        out.putNotNull("constraints",this.getConstraints());
        out.putNotNull("description",this.getDescription());
        out.putNotNull("hooks",this.getHooks());
        out.putNotNull("meta",this.getMeta());
        out.putNotNull("name",this.getName());
        out.putNotNull("permissions",this.getPermissions());
        out.putNotNull("prompt",this.getPrompt());
        out.putNotNull("tagSet",this.getTagSet());
        out.putNotNull("tools",this.getTools());
    }

    public AgentModel cloneInstance(){
        AgentModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(AgentModel instance){
        super.copyTo(instance);
        
        instance.setCapabilities(this.getCapabilities());
        instance.setChatOptions(this.getChatOptions());
        instance.setConstraints(this.getConstraints());
        instance.setDescription(this.getDescription());
        instance.setHooks(this.getHooks());
        instance.setMeta(this.getMeta());
        instance.setName(this.getName());
        instance.setPermissions(this.getPermissions());
        instance.setPrompt(this.getPrompt());
        instance.setTagSet(this.getTagSet());
        instance.setTools(this.getTools());
    }

    protected AgentModel newInstance(){
        return (AgentModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
