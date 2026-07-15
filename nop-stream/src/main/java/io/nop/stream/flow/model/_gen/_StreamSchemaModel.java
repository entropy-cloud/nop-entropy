package io.nop.stream.flow.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.stream.flow.model.StreamSchemaModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from file:/Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/stream/stream.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _StreamSchemaModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: fields
     * 
     */
    private KeyedList<io.nop.stream.flow.model.StreamSchemaFieldModel> _fields = KeyedList.emptyList();
    
    /**
     *  
     * xml name: id
     * 
     */
    private java.lang.String _id ;
    
    /**
     * 
     * xml name: fields
     *  
     */
    
    public java.util.List<io.nop.stream.flow.model.StreamSchemaFieldModel> getFields(){
      return _fields;
    }

    
    public void setFields(java.util.List<io.nop.stream.flow.model.StreamSchemaFieldModel> value){
        checkAllowChange();
        
        this._fields = KeyedList.fromList(value, io.nop.stream.flow.model.StreamSchemaFieldModel::getName);
           
    }

    
    public io.nop.stream.flow.model.StreamSchemaFieldModel getField(String name){
        return this._fields.getByKey(name);
    }

    public boolean hasField(String name){
        return this._fields.containsKey(name);
    }

    public void addField(io.nop.stream.flow.model.StreamSchemaFieldModel item) {
        checkAllowChange();
        java.util.List<io.nop.stream.flow.model.StreamSchemaFieldModel> list = this.getFields();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.stream.flow.model.StreamSchemaFieldModel::getName);
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
        out.putNotNull("id",this.getId());
    }

    public StreamSchemaModel cloneInstance(){
        StreamSchemaModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(StreamSchemaModel instance){
        super.copyTo(instance);
        
        instance.setFields(this.getFields());
        instance.setId(this.getId());
    }

    protected StreamSchemaModel newInstance(){
        return (StreamSchemaModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
