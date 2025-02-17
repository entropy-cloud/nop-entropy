package io.nop.ai.core.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.ai.core.model.ChatOptionsModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/ai/chat-options.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChatOptionsModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: contextLength
     * 
     */
    private java.lang.Integer _contextLength ;
    
    /**
     *  
     * xml name: maxTokens
     * 
     */
    private java.lang.Integer _maxTokens ;
    
    /**
     *  
     * xml name: model
     * 
     */
    private java.lang.String _model ;
    
    /**
     *  
     * xml name: seed
     * 
     */
    private java.lang.String _seed ;
    
    /**
     *  
     * xml name: stop
     * 
     */
    private java.util.List<java.lang.String> _stop ;
    
    /**
     *  
     * xml name: temperature
     * 
     */
    private java.lang.Float _temperature ;
    
    /**
     *  
     * xml name: topK
     * 
     */
    private java.lang.Integer _topK ;
    
    /**
     *  
     * xml name: topP
     * 
     */
    private java.lang.Float _topP ;
    
    /**
     * 
     * xml name: contextLength
     *  
     */
    
    public java.lang.Integer getContextLength(){
      return _contextLength;
    }

    
    public void setContextLength(java.lang.Integer value){
        checkAllowChange();
        
        this._contextLength = value;
           
    }

    
    /**
     * 
     * xml name: maxTokens
     *  
     */
    
    public java.lang.Integer getMaxTokens(){
      return _maxTokens;
    }

    
    public void setMaxTokens(java.lang.Integer value){
        checkAllowChange();
        
        this._maxTokens = value;
           
    }

    
    /**
     * 
     * xml name: model
     *  
     */
    
    public java.lang.String getModel(){
      return _model;
    }

    
    public void setModel(java.lang.String value){
        checkAllowChange();
        
        this._model = value;
           
    }

    
    /**
     * 
     * xml name: seed
     *  
     */
    
    public java.lang.String getSeed(){
      return _seed;
    }

    
    public void setSeed(java.lang.String value){
        checkAllowChange();
        
        this._seed = value;
           
    }

    
    /**
     * 
     * xml name: stop
     *  
     */
    
    public java.util.List<java.lang.String> getStop(){
      return _stop;
    }

    
    public void setStop(java.util.List<java.lang.String> value){
        checkAllowChange();
        
        this._stop = value;
           
    }

    
    /**
     * 
     * xml name: temperature
     *  
     */
    
    public java.lang.Float getTemperature(){
      return _temperature;
    }

    
    public void setTemperature(java.lang.Float value){
        checkAllowChange();
        
        this._temperature = value;
           
    }

    
    /**
     * 
     * xml name: topK
     *  
     */
    
    public java.lang.Integer getTopK(){
      return _topK;
    }

    
    public void setTopK(java.lang.Integer value){
        checkAllowChange();
        
        this._topK = value;
           
    }

    
    /**
     * 
     * xml name: topP
     *  
     */
    
    public java.lang.Float getTopP(){
      return _topP;
    }

    
    public void setTopP(java.lang.Float value){
        checkAllowChange();
        
        this._topP = value;
           
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
        
        out.putNotNull("contextLength",this.getContextLength());
        out.putNotNull("maxTokens",this.getMaxTokens());
        out.putNotNull("model",this.getModel());
        out.putNotNull("seed",this.getSeed());
        out.putNotNull("stop",this.getStop());
        out.putNotNull("temperature",this.getTemperature());
        out.putNotNull("topK",this.getTopK());
        out.putNotNull("topP",this.getTopP());
    }

    public ChatOptionsModel cloneInstance(){
        ChatOptionsModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChatOptionsModel instance){
        super.copyTo(instance);
        
        instance.setContextLength(this.getContextLength());
        instance.setMaxTokens(this.getMaxTokens());
        instance.setModel(this.getModel());
        instance.setSeed(this.getSeed());
        instance.setStop(this.getStop());
        instance.setTemperature(this.getTemperature());
        instance.setTopK(this.getTopK());
        instance.setTopP(this.getTopP());
    }

    protected ChatOptionsModel newInstance(){
        return (ChatOptionsModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
