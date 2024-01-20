package io.nop.rpc.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.rpc.model.ApiMessageFieldModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [67:14:0:0]/nop/schema/api.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ApiMessageFieldModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: binaryScalarType
     * 
     */
    private io.nop.commons.type.BinaryScalarType _binaryScalarType ;
    
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
     * xml name: mandatory
     * 
     */
    private boolean _mandatory  = false;
    
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
     * xml name: propId
     * 
     */
    private int _propId ;
    
    /**
     *  
     * xml name: schema
     * schema包含如下几种情况：1. 简单数据类型 2. Map（命名属性集合） 3. List（顺序结构，重复结构） 4. Union（switch选择结构）
     * Map对应props配置,  List对应item配置, Union对应oneOf配置
     */
    private io.nop.xlang.xmeta.ISchema _schema ;
    
    /**
     *  
     * xml name: tagSet
     * 
     */
    private java.util.Set<java.lang.String> _tagSet ;
    
    /**
     * 
     * xml name: binaryScalarType
     *  
     */
    
    public io.nop.commons.type.BinaryScalarType getBinaryScalarType(){
      return _binaryScalarType;
    }

    
    public void setBinaryScalarType(io.nop.commons.type.BinaryScalarType value){
        checkAllowChange();
        
        this._binaryScalarType = value;
           
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
     * xml name: mandatory
     *  
     */
    
    public boolean isMandatory(){
      return _mandatory;
    }

    
    public void setMandatory(boolean value){
        checkAllowChange();
        
        this._mandatory = value;
           
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
     * xml name: propId
     *  
     */
    
    public int getPropId(){
      return _propId;
    }

    
    public void setPropId(int value){
        checkAllowChange();
        
        this._propId = value;
           
    }

    
    /**
     * 
     * xml name: schema
     *  schema包含如下几种情况：1. 简单数据类型 2. Map（命名属性集合） 3. List（顺序结构，重复结构） 4. Union（switch选择结构）
     * Map对应props配置,  List对应item配置, Union对应oneOf配置
     */
    
    public io.nop.xlang.xmeta.ISchema getSchema(){
      return _schema;
    }

    
    public void setSchema(io.nop.xlang.xmeta.ISchema value){
        checkAllowChange();
        
        this._schema = value;
           
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
        
           this._options = io.nop.api.core.util.FreezeHelper.deepFreeze(this._options);
            
           this._schema = io.nop.api.core.util.FreezeHelper.deepFreeze(this._schema);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("binaryScalarType",this.getBinaryScalarType());
        out.put("description",this.getDescription());
        out.put("displayName",this.getDisplayName());
        out.put("mandatory",this.isMandatory());
        out.put("name",this.getName());
        out.put("options",this.getOptions());
        out.put("propId",this.getPropId());
        out.put("schema",this.getSchema());
        out.put("tagSet",this.getTagSet());
    }

    public ApiMessageFieldModel cloneInstance(){
        ApiMessageFieldModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ApiMessageFieldModel instance){
        super.copyTo(instance);
        
        instance.setBinaryScalarType(this.getBinaryScalarType());
        instance.setDescription(this.getDescription());
        instance.setDisplayName(this.getDisplayName());
        instance.setMandatory(this.isMandatory());
        instance.setName(this.getName());
        instance.setOptions(this.getOptions());
        instance.setPropId(this.getPropId());
        instance.setSchema(this.getSchema());
        instance.setTagSet(this.getTagSet());
    }

    protected ApiMessageFieldModel newInstance(){
        return (ApiMessageFieldModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
