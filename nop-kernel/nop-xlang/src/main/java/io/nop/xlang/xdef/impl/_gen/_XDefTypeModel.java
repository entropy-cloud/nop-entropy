package io.nop.xlang.xdef.impl._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.xlang.xdef.impl.XDefTypeModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/xdef.xdef <p>
 * 引入自定义的def-type类型约束，可以在本文件中使用
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _XDefTypeModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: description
     * 
     */
    private java.lang.String _description ;
    
    /**
     *  
     * xml name: dict
     * 
     */
    private java.lang.String _dict ;
    
    /**
     *  
     * xml name: max
     * 
     */
    private java.lang.Number _max ;
    
    /**
     *  
     * xml name: maxItems
     * 
     */
    private java.lang.Integer _maxItems ;
    
    /**
     *  
     * xml name: maxLength
     * 
     */
    private java.lang.Integer _maxLength ;
    
    /**
     *  
     * xml name: min
     * 
     */
    private java.lang.Number _min ;
    
    /**
     *  
     * xml name: minItems
     * 
     */
    private java.lang.Integer _minItems ;
    
    /**
     *  
     * xml name: minLength
     * 
     */
    private java.lang.Integer _minLength ;
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: pattern
     * 
     */
    private java.lang.String _pattern ;
    
    /**
     *  
     * xml name: ref
     * 可以引用系统内置的def-type定义，对其进行特化
     */
    private java.lang.String _ref ;
    
    /**
     *  
     * xml name: type
     * 
     */
    private io.nop.core.type.IGenericType _type ;
    
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
     * xml name: dict
     *  
     */
    
    public java.lang.String getDict(){
      return _dict;
    }

    
    public void setDict(java.lang.String value){
        checkAllowChange();
        
        this._dict = value;
           
    }

    
    /**
     * 
     * xml name: max
     *  
     */
    
    public java.lang.Number getMax(){
      return _max;
    }

    
    public void setMax(java.lang.Number value){
        checkAllowChange();
        
        this._max = value;
           
    }

    
    /**
     * 
     * xml name: maxItems
     *  
     */
    
    public java.lang.Integer getMaxItems(){
      return _maxItems;
    }

    
    public void setMaxItems(java.lang.Integer value){
        checkAllowChange();
        
        this._maxItems = value;
           
    }

    
    /**
     * 
     * xml name: maxLength
     *  
     */
    
    public java.lang.Integer getMaxLength(){
      return _maxLength;
    }

    
    public void setMaxLength(java.lang.Integer value){
        checkAllowChange();
        
        this._maxLength = value;
           
    }

    
    /**
     * 
     * xml name: min
     *  
     */
    
    public java.lang.Number getMin(){
      return _min;
    }

    
    public void setMin(java.lang.Number value){
        checkAllowChange();
        
        this._min = value;
           
    }

    
    /**
     * 
     * xml name: minItems
     *  
     */
    
    public java.lang.Integer getMinItems(){
      return _minItems;
    }

    
    public void setMinItems(java.lang.Integer value){
        checkAllowChange();
        
        this._minItems = value;
           
    }

    
    /**
     * 
     * xml name: minLength
     *  
     */
    
    public java.lang.Integer getMinLength(){
      return _minLength;
    }

    
    public void setMinLength(java.lang.Integer value){
        checkAllowChange();
        
        this._minLength = value;
           
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
     * xml name: pattern
     *  
     */
    
    public java.lang.String getPattern(){
      return _pattern;
    }

    
    public void setPattern(java.lang.String value){
        checkAllowChange();
        
        this._pattern = value;
           
    }

    
    /**
     * 
     * xml name: ref
     *  可以引用系统内置的def-type定义，对其进行特化
     */
    
    public java.lang.String getRef(){
      return _ref;
    }

    
    public void setRef(java.lang.String value){
        checkAllowChange();
        
        this._ref = value;
           
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
        
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("description",this.getDescription());
        out.putNotNull("dict",this.getDict());
        out.putNotNull("max",this.getMax());
        out.putNotNull("maxItems",this.getMaxItems());
        out.putNotNull("maxLength",this.getMaxLength());
        out.putNotNull("min",this.getMin());
        out.putNotNull("minItems",this.getMinItems());
        out.putNotNull("minLength",this.getMinLength());
        out.putNotNull("name",this.getName());
        out.putNotNull("pattern",this.getPattern());
        out.putNotNull("ref",this.getRef());
        out.putNotNull("type",this.getType());
    }

    public XDefTypeModel cloneInstance(){
        XDefTypeModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(XDefTypeModel instance){
        super.copyTo(instance);
        
        instance.setDescription(this.getDescription());
        instance.setDict(this.getDict());
        instance.setMax(this.getMax());
        instance.setMaxItems(this.getMaxItems());
        instance.setMaxLength(this.getMaxLength());
        instance.setMin(this.getMin());
        instance.setMinItems(this.getMinItems());
        instance.setMinLength(this.getMinLength());
        instance.setName(this.getName());
        instance.setPattern(this.getPattern());
        instance.setRef(this.getRef());
        instance.setType(this.getType());
    }

    protected XDefTypeModel newInstance(){
        return (XDefTypeModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
