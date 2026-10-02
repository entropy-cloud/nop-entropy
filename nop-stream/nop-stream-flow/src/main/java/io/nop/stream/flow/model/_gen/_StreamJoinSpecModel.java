package io.nop.stream.flow.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.stream.flow.model.StreamJoinSpecModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/stream/stream.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _StreamJoinSpecModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: description
     * 
     */
    private java.lang.String _description ;
    
    /**
     *  
     * xml name: joinId
     * 
     */
    private java.lang.String _joinId ;
    
    /**
     *  
     * xml name: joinType
     * 
     */
    private io.nop.stream.core.model.JoinType _joinType ;
    
    /**
     *  
     * xml name: leftKeyExprs
     * 
     */
    private java.lang.String _leftKeyExprs ;
    
    /**
     *  
     * xml name: rightKeyExprs
     * 
     */
    private java.lang.String _rightKeyExprs ;
    
    /**
     *  
     * xml name: timeout
     * 
     */
    private java.lang.String _timeout ;
    
    /**
     *  
     * xml name: windowStrategyRef
     * 
     */
    private java.lang.String _windowStrategyRef ;
    
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
     * xml name: joinId
     *  
     */
    
    public java.lang.String getJoinId(){
      return _joinId;
    }

    
    public void setJoinId(java.lang.String value){
        checkAllowChange();
        
        this._joinId = value;
           
    }

    
    /**
     * 
     * xml name: joinType
     *  
     */
    
    public io.nop.stream.core.model.JoinType getJoinType(){
      return _joinType;
    }

    
    public void setJoinType(io.nop.stream.core.model.JoinType value){
        checkAllowChange();
        
        this._joinType = value;
           
    }

    
    /**
     * 
     * xml name: leftKeyExprs
     *  
     */
    
    public java.lang.String getLeftKeyExprs(){
      return _leftKeyExprs;
    }

    
    public void setLeftKeyExprs(java.lang.String value){
        checkAllowChange();
        
        this._leftKeyExprs = value;
           
    }

    
    /**
     * 
     * xml name: rightKeyExprs
     *  
     */
    
    public java.lang.String getRightKeyExprs(){
      return _rightKeyExprs;
    }

    
    public void setRightKeyExprs(java.lang.String value){
        checkAllowChange();
        
        this._rightKeyExprs = value;
           
    }

    
    /**
     * 
     * xml name: timeout
     *  
     */
    
    public java.lang.String getTimeout(){
      return _timeout;
    }

    
    public void setTimeout(java.lang.String value){
        checkAllowChange();
        
        this._timeout = value;
           
    }

    
    /**
     * 
     * xml name: windowStrategyRef
     *  
     */
    
    public java.lang.String getWindowStrategyRef(){
      return _windowStrategyRef;
    }

    
    public void setWindowStrategyRef(java.lang.String value){
        checkAllowChange();
        
        this._windowStrategyRef = value;
           
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
        
        out.putNotNull("description",this.getDescription());
        out.putNotNull("joinId",this.getJoinId());
        out.putNotNull("joinType",this.getJoinType());
        out.putNotNull("leftKeyExprs",this.getLeftKeyExprs());
        out.putNotNull("rightKeyExprs",this.getRightKeyExprs());
        out.putNotNull("timeout",this.getTimeout());
        out.putNotNull("windowStrategyRef",this.getWindowStrategyRef());
    }

    public StreamJoinSpecModel cloneInstance(){
        StreamJoinSpecModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(StreamJoinSpecModel instance){
        super.copyTo(instance);
        
        instance.setDescription(this.getDescription());
        instance.setJoinId(this.getJoinId());
        instance.setJoinType(this.getJoinType());
        instance.setLeftKeyExprs(this.getLeftKeyExprs());
        instance.setRightKeyExprs(this.getRightKeyExprs());
        instance.setTimeout(this.getTimeout());
        instance.setWindowStrategyRef(this.getWindowStrategyRef());
    }

    protected StreamJoinSpecModel newInstance(){
        return (StreamJoinSpecModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
