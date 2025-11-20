package io.nop.record_mapping.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.record_mapping.model.RecordMappingConfig;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/record/record-mapping.xdef <p>
 * 数据对象属性映射规则
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _RecordMappingConfig extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: afterMapping
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _afterMapping ;
    
    /**
     *  
     * xml name: beforeMapping
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _beforeMapping ;
    
    /**
     *  
     * xml name: description
     * 
     */
    private java.lang.String _description ;
    
    /**
     *  
     * xml name: displayName
     * 
     */
    private java.lang.String _displayName ;
    
    /**
     *  
     * xml name: fields
     * 
     */
    private KeyedList<io.nop.record_mapping.model.RecordFieldMappingConfig> _fields = KeyedList.emptyList();
    
    /**
     *  
     * xml name: fromClass
     * 
     */
    private java.lang.String _fromClass ;
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: tagSet
     * 
     */
    private java.util.Set<java.lang.String> _tagSet ;
    
    /**
     *  
     * xml name: toClass
     * 
     */
    private java.lang.String _toClass ;
    
    /**
     * 
     * xml name: afterMapping
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getAfterMapping(){
      return _afterMapping;
    }

    
    public void setAfterMapping(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._afterMapping = value;
           
    }

    
    /**
     * 
     * xml name: beforeMapping
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getBeforeMapping(){
      return _beforeMapping;
    }

    
    public void setBeforeMapping(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._beforeMapping = value;
           
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
     * xml name: displayName
     *  
     */
    
    public java.lang.String getDisplayName(){
      return _displayName;
    }

    
    public void setDisplayName(java.lang.String value){
        checkAllowChange();
        
        this._displayName = value;
           
    }

    
    /**
     * 
     * xml name: fields
     *  
     */
    
    public java.util.List<io.nop.record_mapping.model.RecordFieldMappingConfig> getFields(){
      return _fields;
    }

    
    public void setFields(java.util.List<io.nop.record_mapping.model.RecordFieldMappingConfig> value){
        checkAllowChange();
        
        this._fields = KeyedList.fromList(value, io.nop.record_mapping.model.RecordFieldMappingConfig::getName);
           
    }

    
    public io.nop.record_mapping.model.RecordFieldMappingConfig getField(String name){
        return this._fields.getByKey(name);
    }

    public boolean hasField(String name){
        return this._fields.containsKey(name);
    }

    public void addField(io.nop.record_mapping.model.RecordFieldMappingConfig item) {
        checkAllowChange();
        java.util.List<io.nop.record_mapping.model.RecordFieldMappingConfig> list = this.getFields();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.record_mapping.model.RecordFieldMappingConfig::getName);
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
     * xml name: fromClass
     *  
     */
    
    public java.lang.String getFromClass(){
      return _fromClass;
    }

    
    public void setFromClass(java.lang.String value){
        checkAllowChange();
        
        this._fromClass = value;
           
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
     * xml name: tagSet
     *  
     */
    
    public java.util.Set<java.lang.String> getTagSet(){
      return _tagSet;
    }

    
    public void setTagSet(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._tagSet = value;
           
    }

    
    /**
     * 
     * xml name: toClass
     *  
     */
    
    public java.lang.String getToClass(){
      return _toClass;
    }

    
    public void setToClass(java.lang.String value){
        checkAllowChange();
        
        this._toClass = value;
           
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
        
        out.putNotNull("afterMapping",this.getAfterMapping());
        out.putNotNull("beforeMapping",this.getBeforeMapping());
        out.putNotNull("description",this.getDescription());
        out.putNotNull("displayName",this.getDisplayName());
        out.putNotNull("fields",this.getFields());
        out.putNotNull("fromClass",this.getFromClass());
        out.putNotNull("name",this.getName());
        out.putNotNull("tagSet",this.getTagSet());
        out.putNotNull("toClass",this.getToClass());
    }

    public RecordMappingConfig cloneInstance(){
        RecordMappingConfig instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(RecordMappingConfig instance){
        super.copyTo(instance);
        
        instance.setAfterMapping(this.getAfterMapping());
        instance.setBeforeMapping(this.getBeforeMapping());
        instance.setDescription(this.getDescription());
        instance.setDisplayName(this.getDisplayName());
        instance.setFields(this.getFields());
        instance.setFromClass(this.getFromClass());
        instance.setName(this.getName());
        instance.setTagSet(this.getTagSet());
        instance.setToClass(this.getToClass());
    }

    protected RecordMappingConfig newInstance(){
        return (RecordMappingConfig) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
