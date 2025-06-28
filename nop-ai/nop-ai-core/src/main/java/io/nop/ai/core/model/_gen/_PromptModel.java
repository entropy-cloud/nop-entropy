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
     * xml name: inputs
     * 
     */
    private KeyedList<io.nop.ai.core.model.PromptInputModel> _inputs = KeyedList.emptyList();
    
    /**
     *  
     * xml name: outputs
     * 
     */
    private KeyedList<io.nop.ai.core.model.PromptOutputModel> _outputs = KeyedList.emptyList();
    
    /**
     *  
     * xml name: postProcess
     * 执行完AI模型调用后得到AichatExchange对象，可以通过模板内置的后处理器对返回结果进行再加工。
     * 这样在切换不同的Prompt模板的时候可以自动切换使用不同的后处理器。
     */
    private io.nop.core.lang.eval.IEvalFunction _postProcess ;
    
    /**
     *  
     * xml name: preProcess
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _preProcess ;
    
    /**
     *  
     * xml name: template
     * 通过xpl模板语言生成prompt，可以利用xpl的扩展能力实现Prompt的结构化抽象
     */
    private io.nop.core.lang.eval.IEvalAction _template ;
    
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
     * xml name: inputs
     *  
     */
    
    public java.util.List<io.nop.ai.core.model.PromptInputModel> getInputs(){
      return _inputs;
    }

    
    public void setInputs(java.util.List<io.nop.ai.core.model.PromptInputModel> value){
        checkAllowChange();
        
        this._inputs = KeyedList.fromList(value, io.nop.ai.core.model.PromptInputModel::getName);
           
    }

    
    public io.nop.ai.core.model.PromptInputModel getInput(String name){
        return this._inputs.getByKey(name);
    }

    public boolean hasInput(String name){
        return this._inputs.containsKey(name);
    }

    public void addInput(io.nop.ai.core.model.PromptInputModel item) {
        checkAllowChange();
        java.util.List<io.nop.ai.core.model.PromptInputModel> list = this.getInputs();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.ai.core.model.PromptInputModel::getName);
            setInputs(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_inputs(){
        return this._inputs.keySet();
    }

    public boolean hasInputs(){
        return !this._inputs.isEmpty();
    }
    
    /**
     * 
     * xml name: outputs
     *  
     */
    
    public java.util.List<io.nop.ai.core.model.PromptOutputModel> getOutputs(){
      return _outputs;
    }

    
    public void setOutputs(java.util.List<io.nop.ai.core.model.PromptOutputModel> value){
        checkAllowChange();
        
        this._outputs = KeyedList.fromList(value, io.nop.ai.core.model.PromptOutputModel::getName);
           
    }

    
    public io.nop.ai.core.model.PromptOutputModel getOutput(String name){
        return this._outputs.getByKey(name);
    }

    public boolean hasOutput(String name){
        return this._outputs.containsKey(name);
    }

    public void addOutput(io.nop.ai.core.model.PromptOutputModel item) {
        checkAllowChange();
        java.util.List<io.nop.ai.core.model.PromptOutputModel> list = this.getOutputs();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.ai.core.model.PromptOutputModel::getName);
            setOutputs(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_outputs(){
        return this._outputs.keySet();
    }

    public boolean hasOutputs(){
        return !this._outputs.isEmpty();
    }
    
    /**
     * 
     * xml name: postProcess
     *  执行完AI模型调用后得到AichatExchange对象，可以通过模板内置的后处理器对返回结果进行再加工。
     * 这样在切换不同的Prompt模板的时候可以自动切换使用不同的后处理器。
     */
    
    public io.nop.core.lang.eval.IEvalFunction getPostProcess(){
      return _postProcess;
    }

    
    public void setPostProcess(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._postProcess = value;
           
    }

    
    /**
     * 
     * xml name: preProcess
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getPreProcess(){
      return _preProcess;
    }

    
    public void setPreProcess(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._preProcess = value;
           
    }

    
    /**
     * 
     * xml name: template
     *  通过xpl模板语言生成prompt，可以利用xpl的扩展能力实现Prompt的结构化抽象
     */
    
    public io.nop.core.lang.eval.IEvalAction getTemplate(){
      return _template;
    }

    
    public void setTemplate(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._template = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._defaultChatOptions = io.nop.api.core.util.FreezeHelper.deepFreeze(this._defaultChatOptions);
            
           this._inputs = io.nop.api.core.util.FreezeHelper.deepFreeze(this._inputs);
            
           this._outputs = io.nop.api.core.util.FreezeHelper.deepFreeze(this._outputs);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("defaultChatOptions",this.getDefaultChatOptions());
        out.putNotNull("description",this.getDescription());
        out.putNotNull("displayName",this.getDisplayName());
        out.putNotNull("inputs",this.getInputs());
        out.putNotNull("outputs",this.getOutputs());
        out.putNotNull("postProcess",this.getPostProcess());
        out.putNotNull("preProcess",this.getPreProcess());
        out.putNotNull("template",this.getTemplate());
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
        instance.setInputs(this.getInputs());
        instance.setOutputs(this.getOutputs());
        instance.setPostProcess(this.getPostProcess());
        instance.setPreProcess(this.getPreProcess());
        instance.setTemplate(this.getTemplate());
    }

    protected PromptModel newInstance(){
        return (PromptModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
