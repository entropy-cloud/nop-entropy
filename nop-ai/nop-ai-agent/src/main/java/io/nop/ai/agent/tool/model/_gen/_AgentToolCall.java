package io.nop.ai.agent.tool.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ai.agent.tool.model.AgentToolCall;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/ai/tool/tool-call.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _AgentToolCall extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: explanation
     * 
     */
    private java.lang.String _explanation ;
    
    /**
     *  
     * xml name: id
     * 
     */
    private int _id ;
    
    /**
     *  
     * xml name: input
     * 
     */
    private java.lang.String _input ;
    
    /**
     *  
     * xml name: input-files
     * 
     */
    private KeyedList<io.nop.ai.agent.tool.model.ToolCallInputFile> _inputFiles = KeyedList.emptyList();
    
    /**
     *  
     * xml name: timeoutMs
     * 
     */
    private java.lang.Integer _timeoutMs ;
    
    /**
     * 
     * xml name: explanation
     *  
     */
    
    public java.lang.String getExplanation(){
      return _explanation;
    }

    
    public void setExplanation(java.lang.String value){
        checkAllowChange();
        
        this._explanation = value;
           
    }

    
    /**
     * 
     * xml name: id
     *  
     */
    
    public int getId(){
      return _id;
    }

    
    public void setId(int value){
        checkAllowChange();
        
        this._id = value;
           
    }

    
    /**
     * 
     * xml name: input
     *  
     */
    
    public java.lang.String getInput(){
      return _input;
    }

    
    public void setInput(java.lang.String value){
        checkAllowChange();
        
        this._input = value;
           
    }

    
    /**
     * 
     * xml name: input-files
     *  
     */
    
    public java.util.List<io.nop.ai.agent.tool.model.ToolCallInputFile> getInputFiles(){
      return _inputFiles;
    }

    
    public void setInputFiles(java.util.List<io.nop.ai.agent.tool.model.ToolCallInputFile> value){
        checkAllowChange();
        
        this._inputFiles = KeyedList.fromList(value, io.nop.ai.agent.tool.model.ToolCallInputFile::getPath);
           
    }

    
    public io.nop.ai.agent.tool.model.ToolCallInputFile getInputFile(String name){
        return this._inputFiles.getByKey(name);
    }

    public boolean hasInputFile(String name){
        return this._inputFiles.containsKey(name);
    }

    public void addInputFile(io.nop.ai.agent.tool.model.ToolCallInputFile item) {
        checkAllowChange();
        java.util.List<io.nop.ai.agent.tool.model.ToolCallInputFile> list = this.getInputFiles();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.ai.agent.tool.model.ToolCallInputFile::getPath);
            setInputFiles(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_inputFiles(){
        return this._inputFiles.keySet();
    }

    public boolean hasInputFiles(){
        return !this._inputFiles.isEmpty();
    }
    
    /**
     * 
     * xml name: timeoutMs
     *  
     */
    
    public java.lang.Integer getTimeoutMs(){
      return _timeoutMs;
    }

    
    public void setTimeoutMs(java.lang.Integer value){
        checkAllowChange();
        
        this._timeoutMs = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._inputFiles = io.nop.api.core.util.FreezeHelper.deepFreeze(this._inputFiles);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("explanation",this.getExplanation());
        out.putNotNull("id",this.getId());
        out.putNotNull("input",this.getInput());
        out.putNotNull("inputFiles",this.getInputFiles());
        out.putNotNull("timeoutMs",this.getTimeoutMs());
    }

    public AgentToolCall cloneInstance(){
        AgentToolCall instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(AgentToolCall instance){
        super.copyTo(instance);
        
        instance.setExplanation(this.getExplanation());
        instance.setId(this.getId());
        instance.setInput(this.getInput());
        instance.setInputFiles(this.getInputFiles());
        instance.setTimeoutMs(this.getTimeoutMs());
    }

    protected AgentToolCall newInstance(){
        return (AgentToolCall) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
