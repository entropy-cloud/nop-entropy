package io.nop.stream.flow.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.stream.flow.model.WindowingStrategyModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from file:/Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/stream.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _WindowingStrategyModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: accumulationMode
     * 
     */
    private io.nop.stream.core.windowing.AccumulationMode _accumulationMode ;
    
    /**
     *  
     * xml name: allowedLateness
     * 
     */
    private long _allowedLateness  = 0;
    
    /**
     *  
     * xml name: description
     * 
     */
    private java.lang.String _description ;
    
    /**
     *  
     * xml name: strategyId
     * 
     */
    private java.lang.String _strategyId ;
    
    /**
     *  
     * xml name: triggerId
     * 
     */
    private java.lang.String _triggerId ;
    
    /**
     *  
     * xml name: windowFnId
     * 
     */
    private java.lang.String _windowFnId ;
    
    /**
     * 
     * xml name: accumulationMode
     *  
     */
    
    public io.nop.stream.core.windowing.AccumulationMode getAccumulationMode(){
      return _accumulationMode;
    }

    
    public void setAccumulationMode(io.nop.stream.core.windowing.AccumulationMode value){
        checkAllowChange();
        
        this._accumulationMode = value;
           
    }

    
    /**
     * 
     * xml name: allowedLateness
     *  
     */
    
    public long getAllowedLateness(){
      return _allowedLateness;
    }

    
    public void setAllowedLateness(long value){
        checkAllowChange();
        
        this._allowedLateness = value;
           
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
     * xml name: strategyId
     *  
     */
    
    public java.lang.String getStrategyId(){
      return _strategyId;
    }

    
    public void setStrategyId(java.lang.String value){
        checkAllowChange();
        
        this._strategyId = value;
           
    }

    
    /**
     * 
     * xml name: triggerId
     *  
     */
    
    public java.lang.String getTriggerId(){
      return _triggerId;
    }

    
    public void setTriggerId(java.lang.String value){
        checkAllowChange();
        
        this._triggerId = value;
           
    }

    
    /**
     * 
     * xml name: windowFnId
     *  
     */
    
    public java.lang.String getWindowFnId(){
      return _windowFnId;
    }

    
    public void setWindowFnId(java.lang.String value){
        checkAllowChange();
        
        this._windowFnId = value;
           
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
        
        out.putNotNull("accumulationMode",this.getAccumulationMode());
        out.putNotNull("allowedLateness",this.getAllowedLateness());
        out.putNotNull("description",this.getDescription());
        out.putNotNull("strategyId",this.getStrategyId());
        out.putNotNull("triggerId",this.getTriggerId());
        out.putNotNull("windowFnId",this.getWindowFnId());
    }

    public WindowingStrategyModel cloneInstance(){
        WindowingStrategyModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(WindowingStrategyModel instance){
        super.copyTo(instance);
        
        instance.setAccumulationMode(this.getAccumulationMode());
        instance.setAllowedLateness(this.getAllowedLateness());
        instance.setDescription(this.getDescription());
        instance.setStrategyId(this.getStrategyId());
        instance.setTriggerId(this.getTriggerId());
        instance.setWindowFnId(this.getWindowFnId());
    }

    protected WindowingStrategyModel newInstance(){
        return (WindowingStrategyModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
