package io.nop.xlang.xpl.xlib._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [62:14:0:0]/nop/schema/xlib.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _XplTagAttribute extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: defaultValue
     * 
     */
    private java.lang.String _defaultValue ;
    
    /**
     *  是否已废弃
     * xml name: deprecated
     * 已废弃的属性不推荐在程序中继续被使用
     */
    private boolean _deprecated  = false;
    
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
     * xml name: implicit
     * 
     */
    private boolean _implicit  = false;
    
    /**
     *  是否内部
     * xml name: internal
     * 内部属性不出现在IDE的提示信息中
     */
    private boolean _internal  = false;
    
    /**
     *  是否非空
     * xml name: mandatory
     * 是否必须设置该属性，且属性值不能是空值或者空字符串
     */
    private boolean _mandatory  = false;
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  是否可选
     * xml name: optional
     * 如果不是可选属性，则调用时必须传入该属性，但是属性值允许为空
     */
    private boolean _optional  = false;
    
    /**
     *  
     * xml name: stdDomain
     * 
     */
    private java.lang.String _stdDomain ;
    
    /**
     *  
     * xml name: type
     * 
     */
    private io.nop.core.type.IGenericType _type ;
    
    /**
     *  
     * xml name: varName
     * 对应于表达式中可以使用的变量名。一般情况下变量名与属性名相同，但是如果属性名中存在特殊字符，例如on:click或者v-model，
     * 则将会转换为大小写混排的变量名，例如onClick和vModel。
     */
    private java.lang.String _varName ;
    
    /**
     * 
     * xml name: defaultValue
     *  
     */
    
    public java.lang.String getDefaultValue(){
      return _defaultValue;
    }

    
    public void setDefaultValue(java.lang.String value){
        checkAllowChange();
        
        this._defaultValue = value;
           
    }

    
    /**
     * 是否已废弃
     * xml name: deprecated
     *  已废弃的属性不推荐在程序中继续被使用
     */
    
    public boolean isDeprecated(){
      return _deprecated;
    }

    
    public void setDeprecated(boolean value){
        checkAllowChange();
        
        this._deprecated = value;
           
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
     * xml name: implicit
     *  
     */
    
    public boolean isImplicit(){
      return _implicit;
    }

    
    public void setImplicit(boolean value){
        checkAllowChange();
        
        this._implicit = value;
           
    }

    
    /**
     * 是否内部
     * xml name: internal
     *  内部属性不出现在IDE的提示信息中
     */
    
    public boolean isInternal(){
      return _internal;
    }

    
    public void setInternal(boolean value){
        checkAllowChange();
        
        this._internal = value;
           
    }

    
    /**
     * 是否非空
     * xml name: mandatory
     *  是否必须设置该属性，且属性值不能是空值或者空字符串
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
     * 是否可选
     * xml name: optional
     *  如果不是可选属性，则调用时必须传入该属性，但是属性值允许为空
     */
    
    public boolean isOptional(){
      return _optional;
    }

    
    public void setOptional(boolean value){
        checkAllowChange();
        
        this._optional = value;
           
    }

    
    /**
     * 
     * xml name: stdDomain
     *  
     */
    
    public java.lang.String getStdDomain(){
      return _stdDomain;
    }

    
    public void setStdDomain(java.lang.String value){
        checkAllowChange();
        
        this._stdDomain = value;
           
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

    
    /**
     * 
     * xml name: varName
     *  对应于表达式中可以使用的变量名。一般情况下变量名与属性名相同，但是如果属性名中存在特殊字符，例如on:click或者v-model，
     * 则将会转换为大小写混排的变量名，例如onClick和vModel。
     */
    
    public java.lang.String getVarName(){
      return _varName;
    }

    
    public void setVarName(java.lang.String value){
        checkAllowChange();
        
        this._varName = value;
           
    }

    

    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
        }
    }

    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("defaultValue",this.getDefaultValue());
        out.put("deprecated",this.isDeprecated());
        out.put("description",this.getDescription());
        out.put("displayName",this.getDisplayName());
        out.put("implicit",this.isImplicit());
        out.put("internal",this.isInternal());
        out.put("mandatory",this.isMandatory());
        out.put("name",this.getName());
        out.put("optional",this.isOptional());
        out.put("stdDomain",this.getStdDomain());
        out.put("type",this.getType());
        out.put("varName",this.getVarName());
    }
}
 // resume CPD analysis - CPD-ON
