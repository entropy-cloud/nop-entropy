package io.nop.record.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.record.model.RecordObjectMeta;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [30:6:0:0]/nop/schema/record/record-file.xdef <p>
 * 每一行解析得到一个强类型的JavaBean。如果不设置，则解析为Map
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _RecordObjectMeta extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: computed-field
     * 
     */
    private KeyedList<io.nop.record.model.RecordComputedFieldMeta> _computedFields = KeyedList.emptyList();
    
    /**
     *  
     * xml name: field
     * 定长记录的定义
     */
    private KeyedList<io.nop.record.model.RecordFieldMeta> _fields = KeyedList.emptyList();
    
    /**
     *  
     * xml name: param
     * 
     */
    private KeyedList<io.nop.record.model.RecordParamMeta> _params = KeyedList.emptyList();
    
    /**
     *  
     * xml name: type
     * 
     */
    private io.nop.core.type.IGenericType _type ;
    
    /**
     * 
     * xml name: computed-field
     *  
     */
    
    public java.util.List<io.nop.record.model.RecordComputedFieldMeta> getComputedFields(){
      return _computedFields;
    }

    
    public void setComputedFields(java.util.List<io.nop.record.model.RecordComputedFieldMeta> value){
        checkAllowChange();
        
        this._computedFields = KeyedList.fromList(value, io.nop.record.model.RecordComputedFieldMeta::getName);
           
    }

    
    public io.nop.record.model.RecordComputedFieldMeta getComputedField(String name){
        return this._computedFields.getByKey(name);
    }

    public boolean hasComputedField(String name){
        return this._computedFields.containsKey(name);
    }

    public void addComputedField(io.nop.record.model.RecordComputedFieldMeta item) {
        checkAllowChange();
        java.util.List<io.nop.record.model.RecordComputedFieldMeta> list = this.getComputedFields();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.record.model.RecordComputedFieldMeta::getName);
            setComputedFields(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_computedFields(){
        return this._computedFields.keySet();
    }

    public boolean hasComputedFields(){
        return !this._computedFields.isEmpty();
    }
    
    /**
     * 
     * xml name: field
     *  定长记录的定义
     */
    
    public java.util.List<io.nop.record.model.RecordFieldMeta> getFields(){
      return _fields;
    }

    
    public void setFields(java.util.List<io.nop.record.model.RecordFieldMeta> value){
        checkAllowChange();
        
        this._fields = KeyedList.fromList(value, io.nop.record.model.RecordFieldMeta::getName);
           
    }

    
    public io.nop.record.model.RecordFieldMeta getField(String name){
        return this._fields.getByKey(name);
    }

    public boolean hasField(String name){
        return this._fields.containsKey(name);
    }

    public void addField(io.nop.record.model.RecordFieldMeta item) {
        checkAllowChange();
        java.util.List<io.nop.record.model.RecordFieldMeta> list = this.getFields();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.record.model.RecordFieldMeta::getName);
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
     * xml name: param
     *  
     */
    
    public java.util.List<io.nop.record.model.RecordParamMeta> getParams(){
      return _params;
    }

    
    public void setParams(java.util.List<io.nop.record.model.RecordParamMeta> value){
        checkAllowChange();
        
        this._params = KeyedList.fromList(value, io.nop.record.model.RecordParamMeta::getName);
           
    }

    
    public io.nop.record.model.RecordParamMeta getParam(String name){
        return this._params.getByKey(name);
    }

    public boolean hasParam(String name){
        return this._params.containsKey(name);
    }

    public void addParam(io.nop.record.model.RecordParamMeta item) {
        checkAllowChange();
        java.util.List<io.nop.record.model.RecordParamMeta> list = this.getParams();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.record.model.RecordParamMeta::getName);
            setParams(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_params(){
        return this._params.keySet();
    }

    public boolean hasParams(){
        return !this._params.isEmpty();
    }
    
    /**
     * 
     * xml name: type
     *  
     */
    
    public io.nop.core.type.IGenericType getType(){
      return _type;
    }

    
    public void setType(io.nop.core.type.IGenericType value){
        checkAllowChange();
        
        this._type = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._computedFields = io.nop.api.core.util.FreezeHelper.deepFreeze(this._computedFields);
            
           this._fields = io.nop.api.core.util.FreezeHelper.deepFreeze(this._fields);
            
           this._params = io.nop.api.core.util.FreezeHelper.deepFreeze(this._params);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("computedFields",this.getComputedFields());
        out.putNotNull("fields",this.getFields());
        out.putNotNull("params",this.getParams());
        out.putNotNull("type",this.getType());
    }

    public RecordObjectMeta cloneInstance(){
        RecordObjectMeta instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(RecordObjectMeta instance){
        super.copyTo(instance);
        
        instance.setComputedFields(this.getComputedFields());
        instance.setFields(this.getFields());
        instance.setParams(this.getParams());
        instance.setType(this.getType());
    }

    protected RecordObjectMeta newInstance(){
        return (RecordObjectMeta) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
