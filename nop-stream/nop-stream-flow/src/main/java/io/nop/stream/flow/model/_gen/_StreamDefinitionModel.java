package io.nop.stream.flow.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.stream.flow.model.StreamDefinitionModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/stream/stream.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _StreamDefinitionModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: description
     * 
     */
    private java.lang.String _description ;
    
    /**
     *  
     * xml name: id
     * 
     */
    private java.lang.String _id ;
    
    /**
     *  
     * xml name: valueType
     * 
     */
    private io.nop.core.type.IGenericType _valueType ;
    
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
     * xml name: id
     *  
     */
    
    public java.lang.String getId(){
      return _id;
    }

    
    public void setId(java.lang.String value){
        checkAllowChange();
        
        this._id = value;
           
    }

    
    /**
     * 
     * xml name: valueType
     *  
     */
    
    public io.nop.core.type.IGenericType getValueType(){
      return _valueType;
    }

    
    public void setValueType(io.nop.core.type.IGenericType value){
        checkAllowChange();
        
        this._valueType = value;
           
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
        out.putNotNull("id",this.getId());
        out.putNotNull("valueType",this.getValueType());
    }

    public StreamDefinitionModel cloneInstance(){
        StreamDefinitionModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(StreamDefinitionModel instance){
        super.copyTo(instance);
        
        instance.setDescription(this.getDescription());
        instance.setId(this.getId());
        instance.setValueType(this.getValueType());
    }

    protected StreamDefinitionModel newInstance(){
        return (StreamDefinitionModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
