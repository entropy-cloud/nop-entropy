package io.nop.biz.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.biz.model.BizActionArgModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [56:14:0:0]/nop/schema/biz/xbiz.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _BizActionArgModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: defaultExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _defaultExpr ;
    
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
     * xml name: kind
     * 
     */
    private io.nop.api.core.annotations.biz.BizActionArgKind _kind ;
    
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
     * xml name: schema
     * schema包含如下几种情况：1. 简单数据类型 2. Map（命名属性集合） 3. List（顺序结构，重复结构） 4. Union（switch选择结构）
     * Map对应props配置,  List对应item配置, Union对应oneOf配置
     */
    private io.nop.xlang.xmeta.ISchema _schema ;
    
    /**
     *  
     * xml name: type
     * 
     */
    private io.nop.core.type.IGenericType _type ;
    
    /**
     * 
     * xml name: defaultExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getDefaultExpr(){
      return _defaultExpr;
    }

    
    public void setDefaultExpr(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._defaultExpr = value;
           
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
     * xml name: kind
     *  
     */
    
    public io.nop.api.core.annotations.biz.BizActionArgKind getKind(){
      return _kind;
    }

    
    public void setKind(io.nop.api.core.annotations.biz.BizActionArgKind value){
        checkAllowChange();
        
        this._kind = value;
           
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
        
           this._schema = io.nop.api.core.util.FreezeHelper.deepFreeze(this._schema);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("defaultExpr",this.getDefaultExpr());
        out.put("description",this.getDescription());
        out.put("displayName",this.getDisplayName());
        out.put("kind",this.getKind());
        out.put("mandatory",this.isMandatory());
        out.put("name",this.getName());
        out.put("schema",this.getSchema());
        out.put("type",this.getType());
    }

    public BizActionArgModel cloneInstance(){
        BizActionArgModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(BizActionArgModel instance){
        super.copyTo(instance);
        
        instance.setDefaultExpr(this.getDefaultExpr());
        instance.setDescription(this.getDescription());
        instance.setDisplayName(this.getDisplayName());
        instance.setKind(this.getKind());
        instance.setMandatory(this.isMandatory());
        instance.setName(this.getName());
        instance.setSchema(this.getSchema());
        instance.setType(this.getType());
    }

    protected BizActionArgModel newInstance(){
        return (BizActionArgModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
