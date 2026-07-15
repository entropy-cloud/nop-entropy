package io.nop.stream.flow.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.stream.flow.model.StorageConfigEntryModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/stream/stream.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _StorageConfigEntryModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: key
     * 
     */
    private java.lang.String _key ;
    
    /**
     *  
     * xml name: value
     * 
     */
    private java.lang.String _value ;
    
    /**
     * 
     * xml name: key
     *  
     */
    
    public java.lang.String getKey(){
      return _key;
    }

    
    public void setKey(java.lang.String value){
        checkAllowChange();
        
        this._key = value;
           
    }

    
    /**
     * 
     * xml name: value
     *  
     */
    
    public java.lang.String getValue(){
      return _value;
    }

    
    public void setValue(java.lang.String value){
        checkAllowChange();
        
        this._value = value;
           
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
        
        out.putNotNull("key",this.getKey());
        out.putNotNull("value",this.getValue());
    }

    public StorageConfigEntryModel cloneInstance(){
        StorageConfigEntryModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(StorageConfigEntryModel instance){
        super.copyTo(instance);
        
        instance.setKey(this.getKey());
        instance.setValue(this.getValue());
    }

    protected StorageConfigEntryModel newInstance(){
        return (StorageConfigEntryModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
