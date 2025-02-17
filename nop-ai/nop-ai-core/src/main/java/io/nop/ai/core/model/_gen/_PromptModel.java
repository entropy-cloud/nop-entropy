package io.nop.ai.core.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ai.core.model.PromptModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/ai/prompt.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _PromptModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: defaultChatOptions
     * 
     */
    private io.nop.ai.core.model.ChatOptionsModel _defaultChatOptions ;
    
    /**
     *  
     * xml name: description
     * 
     */
    private java.lang.String _description ;
    
    /**
     *  
     * xml name: displayName
     * 
     */
    private java.lang.String _displayName ;
    
    /**
     *  
     * xml name: processResultMessage
     * 执行完AI模型调用后得到AiResultMessage对象，可以通过模板内置的后处理器对返回结果进行再加工。
     * 这样在切换不同的Prompt模板的时候可以自动切换使用不同的后处理器。
     * 比如Prompt中可以额外增加一些特殊的标记提示，用于简化结果解析，在processResultMessage中自动识别这些标记并做处理。
     */
    private io.nop.core.lang.eval.IEvalFunction _processResultMessage ;
    
    /**
     *  
     * xml name: template
     * 通过xpl模板语言生成prompt，可以利用xpl的扩展能力实现Prompt的结构化抽象
     */
    private io.nop.core.resource.tpl.ITextTemplateOutput _template ;
    
    /**
     *  
     * xml name: vars
     * 
     */
    private KeyedList<io.nop.ai.core.model.PromptVarModel> _vars = KeyedList.emptyList();
    
    /**
     * 
     * xml name: defaultChatOptions
     *  
     */
    
    public io.nop.ai.core.model.ChatOptionsModel getDefaultChatOptions(){
      return _defaultChatOptions;
    }

    
    public void setDefaultChatOptions(io.nop.ai.core.model.ChatOptionsModel value){
        checkAllowChange();
        
        this._defaultChatOptions = value;
           
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
     * xml name: displayName
     *  
     */
    
    public java.lang.String getDisplayName(){
      return _displayName;
    }

    
    public void setDisplayName(java.lang.String value){
        checkAllowChange();
        
        this._displayName = value;
           
    }

    
    /**
     * 
     * xml name: processResultMessage
     *  执行完AI模型调用后得到AiResultMessage对象，可以通过模板内置的后处理器对返回结果进行再加工。
     * 这样在切换不同的Prompt模板的时候可以自动切换使用不同的后处理器。
     * 比如Prompt中可以额外增加一些特殊的标记提示，用于简化结果解析，在processResultMessage中自动识别这些标记并做处理。
     */
    
    public io.nop.core.lang.eval.IEvalFunction getProcessResultMessage(){
      return _processResultMessage;
    }

    
    public void setProcessResultMessage(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._processResultMessage = value;
           
    }

    
    /**
     * 
     * xml name: template
     *  通过xpl模板语言生成prompt，可以利用xpl的扩展能力实现Prompt的结构化抽象
     */
    
    public io.nop.core.resource.tpl.ITextTemplateOutput getTemplate(){
      return _template;
    }

    
    public void setTemplate(io.nop.core.resource.tpl.ITextTemplateOutput value){
        checkAllowChange();
        
        this._template = value;
           
    }

    
    /**
     * 
     * xml name: vars
     *  
     */
    
    public java.util.List<io.nop.ai.core.model.PromptVarModel> getVars(){
      return _vars;
    }

    
    public void setVars(java.util.List<io.nop.ai.core.model.PromptVarModel> value){
        checkAllowChange();
        
        this._vars = KeyedList.fromList(value, io.nop.ai.core.model.PromptVarModel::getName);
           
    }

    
    public io.nop.ai.core.model.PromptVarModel getVar(String name){
        return this._vars.getByKey(name);
    }

    public boolean hasVar(String name){
        return this._vars.containsKey(name);
    }

    public void addVar(io.nop.ai.core.model.PromptVarModel item) {
        checkAllowChange();
        java.util.List<io.nop.ai.core.model.PromptVarModel> list = this.getVars();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.ai.core.model.PromptVarModel::getName);
            setVars(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_vars(){
        return this._vars.keySet();
    }

    public boolean hasVars(){
        return !this._vars.isEmpty();
    }
    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._defaultChatOptions = io.nop.api.core.util.FreezeHelper.deepFreeze(this._defaultChatOptions);
            
           this._vars = io.nop.api.core.util.FreezeHelper.deepFreeze(this._vars);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("defaultChatOptions",this.getDefaultChatOptions());
        out.putNotNull("description",this.getDescription());
        out.putNotNull("displayName",this.getDisplayName());
        out.putNotNull("processResultMessage",this.getProcessResultMessage());
        out.putNotNull("template",this.getTemplate());
        out.putNotNull("vars",this.getVars());
    }

    public PromptModel cloneInstance(){
        PromptModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(PromptModel instance){
        super.copyTo(instance);
        
        instance.setDefaultChatOptions(this.getDefaultChatOptions());
        instance.setDescription(this.getDescription());
        instance.setDisplayName(this.getDisplayName());
        instance.setProcessResultMessage(this.getProcessResultMessage());
        instance.setTemplate(this.getTemplate());
        instance.setVars(this.getVars());
    }

    protected PromptModel newInstance(){
        return (PromptModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
