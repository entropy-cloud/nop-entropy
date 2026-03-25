package io.nop.ai.toolkit.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/ai/tool/call-tools-response.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _AiToolCallResult extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: error
     * 
     */
    private io.nop.ai.toolkit.model.AiToolError _error ;
    
    /**
     *  
     * xml name: exitCode
     * 
     */
    private java.lang.Integer _exitCode ;
    
    /**
     *  
     * xml name: id
     * 
     */
    private int _id ;
    
    /**
     *  
     * xml name: output
     * 
     */
    private io.nop.ai.toolkit.model.AiToolOutput _output ;
    
    /**
     *  
     * xml name: output-files
     * 
     */
    private KeyedList<io.nop.ai.toolkit.model.AiToolOutputFile> _outputFiles = KeyedList.emptyList();
    
    /**
     *  
     * xml name: status
     * 
     */
    private java.lang.String _status ;
    
    /**
     * 
     * xml name: error
     *  
     */
    
    public io.nop.ai.toolkit.model.AiToolError getError(){
      return _error;
    }

    
    public void setError(io.nop.ai.toolkit.model.AiToolError value){
        checkAllowChange();
        
        this._error = value;
           
    }

    
    /**
     * 
     * xml name: exitCode
     *  
     */
    
    public java.lang.Integer getExitCode(){
      return _exitCode;
    }

    
    public void setExitCode(java.lang.Integer value){
        checkAllowChange();
        
        this._exitCode = value;
           
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
     * xml name: output
     *  
     */
    
    public io.nop.ai.toolkit.model.AiToolOutput getOutput(){
      return _output;
    }

    
    public void setOutput(io.nop.ai.toolkit.model.AiToolOutput value){
        checkAllowChange();
        
        this._output = value;
           
    }

    
    /**
     * 
     * xml name: output-files
     *  
     */
    
    public java.util.List<io.nop.ai.toolkit.model.AiToolOutputFile> getOutputFiles(){
      return _outputFiles;
    }

    
    public void setOutputFiles(java.util.List<io.nop.ai.toolkit.model.AiToolOutputFile> value){
        checkAllowChange();
        
        this._outputFiles = KeyedList.fromList(value, io.nop.ai.toolkit.model.AiToolOutputFile::getPath);
           
    }

    
    public io.nop.ai.toolkit.model.AiToolOutputFile getOutputFile(String name){
        return this._outputFiles.getByKey(name);
    }

    public boolean hasOutputFile(String name){
        return this._outputFiles.containsKey(name);
    }

    public void addOutputFile(io.nop.ai.toolkit.model.AiToolOutputFile item) {
        checkAllowChange();
        java.util.List<io.nop.ai.toolkit.model.AiToolOutputFile> list = this.getOutputFiles();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.ai.toolkit.model.AiToolOutputFile::getPath);
            setOutputFiles(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_outputFiles(){
        return this._outputFiles.keySet();
    }

    public boolean hasOutputFiles(){
        return !this._outputFiles.isEmpty();
    }
    
    /**
     * 
     * xml name: status
     *  
     */
    
    public java.lang.String getStatus(){
      return _status;
    }

    
    public void setStatus(java.lang.String value){
        checkAllowChange();
        
        this._status = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._error = io.nop.api.core.util.FreezeHelper.deepFreeze(this._error);
            
           this._output = io.nop.api.core.util.FreezeHelper.deepFreeze(this._output);
            
           this._outputFiles = io.nop.api.core.util.FreezeHelper.deepFreeze(this._outputFiles);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("error",this.getError());
        out.putNotNull("exitCode",this.getExitCode());
        out.putNotNull("id",this.getId());
        out.putNotNull("output",this.getOutput());
        out.putNotNull("outputFiles",this.getOutputFiles());
        out.putNotNull("status",this.getStatus());
    }

    public AiToolCallResult cloneInstance(){
        AiToolCallResult instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(AiToolCallResult instance){
        super.copyTo(instance);
        
        instance.setError(this.getError());
        instance.setExitCode(this.getExitCode());
        instance.setId(this.getId());
        instance.setOutput(this.getOutput());
        instance.setOutputFiles(this.getOutputFiles());
        instance.setStatus(this.getStatus());
    }

    protected AiToolCallResult newInstance(){
        return (AiToolCallResult) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
