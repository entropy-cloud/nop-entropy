package io.nop.ai.agent.model.recipe._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ai.agent.model.recipe.RecipeModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/ai/recipe.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _RecipeModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: description
     * 
     */
    private java.lang.String _description ;
    
    /**
     *  
     * xml name: hooks
     * hook 链。每个 <on> 引用既有生成类 AgentHookModel（裁定 A：不在 recipe 包内重新生成，
     * 避免合并时类型不匹配），与 agent.xdef 的 <hooks> 共享同一类型。
     */
    private KeyedList<io.nop.ai.agent.model.AgentHookModel> _hooks = KeyedList.emptyList();
    
    /**
     *  
     * xml name: model-config
     * 模型配置快照，复用 chat-options.xdef 的 ChatOptionsModel（裁定 F 逐字段覆盖）
     */
    private io.nop.ai.core.model.ChatOptionsModel _modelConfig ;
    
    /**
     *  
     * xml name: name
     * recipe的唯一名称（与 agent name 同为 valid identifier，作为 VFS 主键）
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: prompt-template
     * prompt 模板（源字符串层，{{paramName}} 占位符由引用处 <param> 替换）。
     * 类型为 string（非 prompt-syntax）：模板参数替换与 prompt 拼接在源字符串层完成，
     * 合并后统一经 PromptSyntaxParser 解析为 IPromptSyntaxNode（裁定 D/G）。
     */
    private java.lang.String _promptTemplate ;
    
    /**
     *  
     * xml name: tools
     * 
     */
    private java.util.Set<java.lang.String> _tools ;
    
    /**
     *  
     * xml name: version
     * 可选版本标记（首版仅作记录，不参与合并语义）
     */
    private java.lang.String _version ;
    
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
     *  hook 链。每个 <on> 引用既有生成类 AgentHookModel（裁定 A：不在 recipe 包内重新生成，
     * 避免合并时类型不匹配），与 agent.xdef 的 <hooks> 共享同一类型。
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
     * xml name: model-config
     *  模型配置快照，复用 chat-options.xdef 的 ChatOptionsModel（裁定 F 逐字段覆盖）
     */
    
    public io.nop.ai.core.model.ChatOptionsModel getModelConfig(){
      return _modelConfig;
    }

    
    public void setModelConfig(io.nop.ai.core.model.ChatOptionsModel value){
        checkAllowChange();
        
        this._modelConfig = value;
           
    }

    
    /**
     * 
     * xml name: name
     *  recipe的唯一名称（与 agent name 同为 valid identifier，作为 VFS 主键）
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
     * xml name: prompt-template
     *  prompt 模板（源字符串层，{{paramName}} 占位符由引用处 <param> 替换）。
     * 类型为 string（非 prompt-syntax）：模板参数替换与 prompt 拼接在源字符串层完成，
     * 合并后统一经 PromptSyntaxParser 解析为 IPromptSyntaxNode（裁定 D/G）。
     */
    
    public java.lang.String getPromptTemplate(){
      return _promptTemplate;
    }

    
    public void setPromptTemplate(java.lang.String value){
        checkAllowChange();
        
        this._promptTemplate = value;
           
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
     * xml name: version
     *  可选版本标记（首版仅作记录，不参与合并语义）
     */
    
    public java.lang.String getVersion(){
      return _version;
    }

    
    public void setVersion(java.lang.String value){
        checkAllowChange();
        
        this._version = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._hooks = io.nop.api.core.util.FreezeHelper.deepFreeze(this._hooks);
            
           this._modelConfig = io.nop.api.core.util.FreezeHelper.deepFreeze(this._modelConfig);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("description",this.getDescription());
        out.putNotNull("hooks",this.getHooks());
        out.putNotNull("modelConfig",this.getModelConfig());
        out.putNotNull("name",this.getName());
        out.putNotNull("promptTemplate",this.getPromptTemplate());
        out.putNotNull("tools",this.getTools());
        out.putNotNull("version",this.getVersion());
    }

    public RecipeModel cloneInstance(){
        RecipeModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(RecipeModel instance){
        super.copyTo(instance);
        
        instance.setDescription(this.getDescription());
        instance.setHooks(this.getHooks());
        instance.setModelConfig(this.getModelConfig());
        instance.setName(this.getName());
        instance.setPromptTemplate(this.getPromptTemplate());
        instance.setTools(this.getTools());
        instance.setVersion(this.getVersion());
    }

    protected RecipeModel newInstance(){
        return (RecipeModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
