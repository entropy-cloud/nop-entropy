package io.nop.stream.flow.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.stream.flow.model.StreamWindowModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/stream/stream.xdef <p>
 * Window 节点：窗口
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _StreamWindowModel extends io.nop.stream.flow.model.StreamTransformModel {
    
    /**
     *  
     * xml name: allowedLateness
     * 
     */
    private java.lang.Long _allowedLateness ;
    
    /**
     *  
     * xml name: strategyRef
     * 
     */
    private java.lang.String _strategyRef ;
    
    /**
     *  
     * xml name: triggerId
     * 
     */
    private java.lang.String _triggerId ;
    
    /**
     *  
     * xml name: 
     * 
     */
    private java.lang.String _type ;
    
    /**
     * 
     * xml name: allowedLateness
     *  
     */
    
    public java.lang.Long getAllowedLateness(){
      return _allowedLateness;
    }

    
    public void setAllowedLateness(java.lang.Long value){
        checkAllowChange();
        
        this._allowedLateness = value;
           
    }

    
    /**
     * 
     * xml name: strategyRef
     *  
     */
    
    public java.lang.String getStrategyRef(){
      return _strategyRef;
    }

    
    public void setStrategyRef(java.lang.String value){
        checkAllowChange();
        
        this._strategyRef = value;
           
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
     * xml name: 
     *  
     */
    
    public java.lang.String getType(){
      return _type;
    }

    
    public void setType(java.lang.String value){
        checkAllowChange();
        
        this._type = value;
           
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
        
        out.putNotNull("allowedLateness",this.getAllowedLateness());
        out.putNotNull("strategyRef",this.getStrategyRef());
        out.putNotNull("triggerId",this.getTriggerId());
        out.putNotNull("type",this.getType());
    }

    public StreamWindowModel cloneInstance(){
        StreamWindowModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(StreamWindowModel instance){
        super.copyTo(instance);
        
        instance.setAllowedLateness(this.getAllowedLateness());
        instance.setStrategyRef(this.getStrategyRef());
        instance.setTriggerId(this.getTriggerId());
        instance.setType(this.getType());
    }

    protected StreamWindowModel newInstance(){
        return (StreamWindowModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
