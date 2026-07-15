package io.nop.stream.flow.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.stream.flow.model.StreamSchemaFieldModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/stream/stream.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _StreamSchemaFieldModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: defaultValue
     * 
     */
    private java.lang.Object _defaultValue ;
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: nullable
     * 
     */
    private java.lang.Boolean _nullable ;
    
    /**
     *  
     * xml name: type
     * 
     */
    private java.lang.String _type ;
    
    /**
     * 
     * xml name: defaultValue
     *  
     */
    
    public java.lang.Object getDefaultValue(){
      return _defaultValue;
    }

    
    public void setDefaultValue(java.lang.Object value){
        checkAllowChange();
        
        this._defaultValue = value;
           
    }

    
    /**
     * 
     * xml name: name
     *  
     */
    
    public java.lang.String getName(){
      return _name;
    }

    
    public void setName(java.lang.String value){
        checkAllowChange();
        
        this._name = value;
           
    }

    
    /**
     * 
     * xml name: nullable
     *  
     */
    
    public java.lang.Boolean getNullable(){
      return _nullable;
    }

    
    public void setNullable(java.lang.Boolean value){
        checkAllowChange();
        
        this._nullable = value;
           
    }

    
    /**
     * 
     * xml name: type
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
        
        out.putNotNull("defaultValue",this.getDefaultValue());
        out.putNotNull("name",this.getName());
        out.putNotNull("nullable",this.getNullable());
        out.putNotNull("type",this.getType());
    }

    public StreamSchemaFieldModel cloneInstance(){
        StreamSchemaFieldModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(StreamSchemaFieldModel instance){
        super.copyTo(instance);
        
        instance.setDefaultValue(this.getDefaultValue());
        instance.setName(this.getName());
        instance.setNullable(this.getNullable());
        instance.setType(this.getType());
    }

    protected StreamSchemaFieldModel newInstance(){
        return (StreamSchemaFieldModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
