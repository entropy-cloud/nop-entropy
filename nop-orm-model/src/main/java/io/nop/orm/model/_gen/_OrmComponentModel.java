package io.nop.orm.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [165:10:0:0]/nop/schema/orm/entity.xdef <p>
 * 多个字段可能构成一个component对象。component对象如果实现了IOrmComponent接口，则其结果可以被缓存。它的内部实现会自动实现与实体属性的同步
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _OrmComponentModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: className
     * 
     */
    private java.lang.String _className ;
    
    /**
     *  
     * xml name: displayName
     * 
     */
    private java.lang.String _displayName ;
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: needFlush
     * 如果needFlush为true，则保存到数据库之前需要调用IOrmComponent.flushToEntity将组件内部的变换更新到实体字段上
     */
    private boolean _needFlush  = true;
    
    /**
     *  
     * xml name: notGenCode
     * 
     */
    private boolean _notGenCode  = false;
    
    /**
     *  
     * xml name: 
     * 
     */
    private KeyedList<io.nop.orm.model.OrmComponentPropModel> _props = KeyedList.emptyList();
    
    /**
     *  
     * xml name: tagSet
     * 
     */
    private java.util.Set<java.lang.String> _tagSet ;
    
    /**
     * 
     * xml name: className
     *  
     */
    
    public java.lang.String getClassName(){
      return _className;
    }

    
    public void setClassName(java.lang.String value){
        checkAllowChange();
        
        this._className = value;
           
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
     * xml name: needFlush
     *  如果needFlush为true，则保存到数据库之前需要调用IOrmComponent.flushToEntity将组件内部的变换更新到实体字段上
     */
    
    public boolean isNeedFlush(){
      return _needFlush;
    }

    
    public void setNeedFlush(boolean value){
        checkAllowChange();
        
        this._needFlush = value;
           
    }

    
    /**
     * 
     * xml name: notGenCode
     *  
     */
    
    public boolean isNotGenCode(){
      return _notGenCode;
    }

    
    public void setNotGenCode(boolean value){
        checkAllowChange();
        
        this._notGenCode = value;
           
    }

    
    /**
     * 
     * xml name: 
     *  
     */
    
    public java.util.List<io.nop.orm.model.OrmComponentPropModel> getProps(){
      return _props;
    }

    
    public void setProps(java.util.List<io.nop.orm.model.OrmComponentPropModel> value){
        checkAllowChange();
        
        this._props = KeyedList.fromList(value, io.nop.orm.model.OrmComponentPropModel::getName);
           
    }

    
    public io.nop.orm.model.OrmComponentPropModel getProp(String name){
        return this._props.getByKey(name);
    }

    public boolean hasProp(String name){
        return this._props.containsKey(name);
    }

    public void addProp(io.nop.orm.model.OrmComponentPropModel item) {
        checkAllowChange();
        java.util.List<io.nop.orm.model.OrmComponentPropModel> list = this.getProps();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.orm.model.OrmComponentPropModel::getName);
            setProps(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_props(){
        return this._props.keySet();
    }

    public boolean hasProps(){
        return !this._props.isEmpty();
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

    

    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._props = io.nop.api.core.util.FreezeHelper.deepFreeze(this._props);
            
        }
    }

    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("className",this.getClassName());
        out.put("displayName",this.getDisplayName());
        out.put("name",this.getName());
        out.put("needFlush",this.isNeedFlush());
        out.put("notGenCode",this.isNotGenCode());
        out.put("props",this.getProps());
        out.put("tagSet",this.getTagSet());
    }
}
 // resume CPD analysis - CPD-ON
