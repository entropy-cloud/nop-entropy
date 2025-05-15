package io.nop.ai.core.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ai.core.model.LlmRequestModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/ai/llm.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _LlmRequestModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: contextLengthPath
     * 
     */
    private java.lang.String _contextLengthPath ;
    
    /**
     *  
     * xml name: maxTokensPath
     * 
     */
    private java.lang.String _maxTokensPath ;
    
    /**
     *  
     * xml name: seedPath
     * 
     */
    private java.lang.String _seedPath ;
    
    /**
     *  
     * xml name: stopPath
     * 
     */
    private java.lang.String _stopPath ;
    
    /**
     *  
     * xml name: temperaturePath
     * 
     */
    private java.lang.String _temperaturePath ;
    
    /**
     *  
     * xml name: thinkingPath
     * 
     */
    private java.lang.String _thinkingPath ;
    
    /**
     *  
     * xml name: topKPath
     * 
     */
    private java.lang.String _topKPath ;
    
    /**
     *  
     * xml name: topPPath
     * 
     */
    private java.lang.String _topPPath ;
    
    /**
     * 
     * xml name: contextLengthPath
     *  
     */
    
    public java.lang.String getContextLengthPath(){
      return _contextLengthPath;
    }

    
    public void setContextLengthPath(java.lang.String value){
        checkAllowChange();
        
        this._contextLengthPath = value;
           
    }

    
    /**
     * 
     * xml name: maxTokensPath
     *  
     */
    
    public java.lang.String getMaxTokensPath(){
      return _maxTokensPath;
    }

    
    public void setMaxTokensPath(java.lang.String value){
        checkAllowChange();
        
        this._maxTokensPath = value;
           
    }

    
    /**
     * 
     * xml name: seedPath
     *  
     */
    
    public java.lang.String getSeedPath(){
      return _seedPath;
    }

    
    public void setSeedPath(java.lang.String value){
        checkAllowChange();
        
        this._seedPath = value;
           
    }

    
    /**
     * 
     * xml name: stopPath
     *  
     */
    
    public java.lang.String getStopPath(){
      return _stopPath;
    }

    
    public void setStopPath(java.lang.String value){
        checkAllowChange();
        
        this._stopPath = value;
           
    }

    
    /**
     * 
     * xml name: temperaturePath
     *  
     */
    
    public java.lang.String getTemperaturePath(){
      return _temperaturePath;
    }

    
    public void setTemperaturePath(java.lang.String value){
        checkAllowChange();
        
        this._temperaturePath = value;
           
    }

    
    /**
     * 
     * xml name: thinkingPath
     *  
     */
    
    public java.lang.String getThinkingPath(){
      return _thinkingPath;
    }

    
    public void setThinkingPath(java.lang.String value){
        checkAllowChange();
        
        this._thinkingPath = value;
           
    }

    
    /**
     * 
     * xml name: topKPath
     *  
     */
    
    public java.lang.String getTopKPath(){
      return _topKPath;
    }

    
    public void setTopKPath(java.lang.String value){
        checkAllowChange();
        
        this._topKPath = value;
           
    }

    
    /**
     * 
     * xml name: topPPath
     *  
     */
    
    public java.lang.String getTopPPath(){
      return _topPPath;
    }

    
    public void setTopPPath(java.lang.String value){
        checkAllowChange();
        
        this._topPPath = value;
           
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
        
        out.putNotNull("contextLengthPath",this.getContextLengthPath());
        out.putNotNull("maxTokensPath",this.getMaxTokensPath());
        out.putNotNull("seedPath",this.getSeedPath());
        out.putNotNull("stopPath",this.getStopPath());
        out.putNotNull("temperaturePath",this.getTemperaturePath());
        out.putNotNull("thinkingPath",this.getThinkingPath());
        out.putNotNull("topKPath",this.getTopKPath());
        out.putNotNull("topPPath",this.getTopPPath());
    }

    public LlmRequestModel cloneInstance(){
        LlmRequestModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(LlmRequestModel instance){
        super.copyTo(instance);
        
        instance.setContextLengthPath(this.getContextLengthPath());
        instance.setMaxTokensPath(this.getMaxTokensPath());
        instance.setSeedPath(this.getSeedPath());
        instance.setStopPath(this.getStopPath());
        instance.setTemperaturePath(this.getTemperaturePath());
        instance.setThinkingPath(this.getThinkingPath());
        instance.setTopKPath(this.getTopKPath());
        instance.setTopPPath(this.getTopPPath());
    }

    protected LlmRequestModel newInstance(){
        return (LlmRequestModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
