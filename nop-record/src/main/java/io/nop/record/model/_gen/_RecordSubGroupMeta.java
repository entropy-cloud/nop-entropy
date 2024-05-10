package io.nop.record.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.record.model.RecordSubGroupMeta;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [63:6:0:0]/nop/schema/record/record-file.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _RecordSubGroupMeta extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: fields
     * 
     */
    private KeyedList<io.nop.record.model.RecordAggregateFieldMeta> _fields = KeyedList.emptyList();
    
    /**
     *  
     * xml name: keyFields
     * 
     */
    private java.util.Set<java.lang.String> _keyFields ;
    
    /**
     * 
     * xml name: fields
     *  
     */
    
    public java.util.List<io.nop.record.model.RecordAggregateFieldMeta> getFields(){
      return _fields;
    }

    
    public void setFields(java.util.List<io.nop.record.model.RecordAggregateFieldMeta> value){
        checkAllowChange();
        
        this._fields = KeyedList.fromList(value, io.nop.record.model.RecordAggregateFieldMeta::getName);
           
    }

    
    public io.nop.record.model.RecordAggregateFieldMeta getField(String name){
        return this._fields.getByKey(name);
    }

    public boolean hasField(String name){
        return this._fields.containsKey(name);
    }

    public void addField(io.nop.record.model.RecordAggregateFieldMeta item) {
        checkAllowChange();
        java.util.List<io.nop.record.model.RecordAggregateFieldMeta> list = this.getFields();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.record.model.RecordAggregateFieldMeta::getName);
            setFields(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_fields(){
        return this._fields.keySet();
    }

    public boolean hasFields(){
        return !this._fields.isEmpty();
    }
    
    /**
     * 
     * xml name: keyFields
     *  
     */
    
    public java.util.Set<java.lang.String> getKeyFields(){
      return _keyFields;
    }

    
    public void setKeyFields(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._keyFields = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._fields = io.nop.api.core.util.FreezeHelper.deepFreeze(this._fields);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("fields",this.getFields());
        out.putNotNull("keyFields",this.getKeyFields());
    }

    public RecordSubGroupMeta cloneInstance(){
        RecordSubGroupMeta instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(RecordSubGroupMeta instance){
        super.copyTo(instance);
        
        instance.setFields(this.getFields());
        instance.setKeyFields(this.getKeyFields());
    }

    protected RecordSubGroupMeta newInstance(){
        return (RecordSubGroupMeta) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
