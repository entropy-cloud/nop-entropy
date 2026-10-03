package io.nop.stream.flow.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.stream.flow.model.StreamAggregatorModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/stream/stream.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _StreamAggregatorModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: aggregatorId
     * 
     */
    private java.lang.String _aggregatorId ;
    
    /**
     *  
     * xml name: description
     * 
     */
    private java.lang.String _description ;
    
    /**
     *  
     * xml name: expr
     * 
     */
    private java.lang.String _expr ;
    
    /**
     *  
     * xml name: fnId
     * 
     */
    private java.lang.String _fnId ;
    
    /**
     *  
     * xml name: schemaId
     * 
     */
    private java.lang.String _schemaId ;
    
    /**
     * 
     * xml name: aggregatorId
     *  
     */
    
    public java.lang.String getAggregatorId(){
      return _aggregatorId;
    }

    
    public void setAggregatorId(java.lang.String value){
        checkAllowChange();
        
        this._aggregatorId = value;
           
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
     * xml name: expr
     *  
     */
    
    public java.lang.String getExpr(){
      return _expr;
    }

    
    public void setExpr(java.lang.String value){
        checkAllowChange();
        
        this._expr = value;
           
    }

    
    /**
     * 
     * xml name: fnId
     *  
     */
    
    public java.lang.String getFnId(){
      return _fnId;
    }

    
    public void setFnId(java.lang.String value){
        checkAllowChange();
        
        this._fnId = value;
           
    }

    
    /**
     * 
     * xml name: schemaId
     *  
     */
    
    public java.lang.String getSchemaId(){
      return _schemaId;
    }

    
    public void setSchemaId(java.lang.String value){
        checkAllowChange();
        
        this._schemaId = value;
           
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
        
        out.putNotNull("aggregatorId",this.getAggregatorId());
        out.putNotNull("description",this.getDescription());
        out.putNotNull("expr",this.getExpr());
        out.putNotNull("fnId",this.getFnId());
        out.putNotNull("schemaId",this.getSchemaId());
    }

    public StreamAggregatorModel cloneInstance(){
        StreamAggregatorModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(StreamAggregatorModel instance){
        super.copyTo(instance);
        
        instance.setAggregatorId(this.getAggregatorId());
        instance.setDescription(this.getDescription());
        instance.setExpr(this.getExpr());
        instance.setFnId(this.getFnId());
        instance.setSchemaId(this.getSchemaId());
    }

    protected StreamAggregatorModel newInstance(){
        return (StreamAggregatorModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
