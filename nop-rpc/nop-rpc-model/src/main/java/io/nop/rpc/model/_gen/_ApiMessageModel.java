package io.nop.rpc.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.rpc.model.ApiMessageModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [61:10:0:0]/nop/schema/api.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ApiMessageModel extends io.nop.core.resource.component.AbstractComponentModel {
    
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
     * xml name: field
     * 
     */
    private KeyedList<io.nop.rpc.model.ApiMessageFieldModel> _fields = KeyedList.emptyList();
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: option
     * 
     */
    private KeyedList<io.nop.rpc.model.ApiOptionModel> _options = KeyedList.emptyList();
    
    /**
     *  
     * xml name: tagSet
     * 
     */
    private java.util.Set<java.lang.String> _tagSet ;
    
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
     * xml name: field
     *  
     */
    
    public java.util.List<io.nop.rpc.model.ApiMessageFieldModel> getFields(){
      return _fields;
    }

    
    public void setFields(java.util.List<io.nop.rpc.model.ApiMessageFieldModel> value){
        checkAllowChange();
        
        this._fields = KeyedList.fromList(value, io.nop.rpc.model.ApiMessageFieldModel::getName);
           
    }

    
    public io.nop.rpc.model.ApiMessageFieldModel getField(String name){
        return this._fields.getByKey(name);
    }

    public boolean hasField(String name){
        return this._fields.containsKey(name);
    }

    public void addField(io.nop.rpc.model.ApiMessageFieldModel item) {
        checkAllowChange();
        java.util.List<io.nop.rpc.model.ApiMessageFieldModel> list = this.getFields();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.rpc.model.ApiMessageFieldModel::getName);
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
     * xml name: option
     *  
     */
    
    public java.util.List<io.nop.rpc.model.ApiOptionModel> getOptions(){
      return _options;
    }

    
    public void setOptions(java.util.List<io.nop.rpc.model.ApiOptionModel> value){
        checkAllowChange();
        
        this._options = KeyedList.fromList(value, io.nop.rpc.model.ApiOptionModel::getName);
           
    }

    
    public io.nop.rpc.model.ApiOptionModel getOption(String name){
        return this._options.getByKey(name);
    }

    public boolean hasOption(String name){
        return this._options.containsKey(name);
    }

    public void addOption(io.nop.rpc.model.ApiOptionModel item) {
        checkAllowChange();
        java.util.List<io.nop.rpc.model.ApiOptionModel> list = this.getOptions();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.rpc.model.ApiOptionModel::getName);
            setOptions(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_options(){
        return this._options.keySet();
    }

    public boolean hasOptions(){
        return !this._options.isEmpty();
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

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._fields = io.nop.api.core.util.FreezeHelper.deepFreeze(this._fields);
            
           this._options = io.nop.api.core.util.FreezeHelper.deepFreeze(this._options);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("description",this.getDescription());
        out.putNotNull("displayName",this.getDisplayName());
        out.putNotNull("fields",this.getFields());
        out.putNotNull("name",this.getName());
        out.putNotNull("options",this.getOptions());
        out.putNotNull("tagSet",this.getTagSet());
    }

    public ApiMessageModel cloneInstance(){
        ApiMessageModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ApiMessageModel instance){
        super.copyTo(instance);
        
        instance.setDescription(this.getDescription());
        instance.setDisplayName(this.getDisplayName());
        instance.setFields(this.getFields());
        instance.setName(this.getName());
        instance.setOptions(this.getOptions());
        instance.setTagSet(this.getTagSet());
    }

    protected ApiMessageModel newInstance(){
        return (ApiMessageModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
