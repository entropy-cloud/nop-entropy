package io.nop.excel.imp.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [41:18:0:0]/nop/schema/excel/imp.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _ImportFieldModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: alias
     * 
     */
    private java.util.Set<java.lang.String> _alias ;
    
    /**
     *  
     * xml name: computed
     * 是否计算字段。计算字段不需要从文件读取，而是通过valueExpr计算
     */
    private boolean _computed  = false;
    
    /**
     *  
     * xml name: displayName
     * 
     */
    private java.lang.String _displayName ;
    
    /**
     *  
     * xml name: exportExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _exportExpr ;
    
    /**
     *  
     * xml name: fields
     * 
     */
    private java.util.List<io.nop.excel.imp.model.ImportFieldModel> _fields = java.util.Collections.emptyList();
    
    /**
     *  
     * xml name: keyProp
     * 
     */
    private java.lang.String _keyProp ;
    
    /**
     *  
     * xml name: list
     * 是否列表定义，解析得到List类型，内部fields定义的是列表条目对象的属性。
     */
    private boolean _list  = false;
    
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
     * xml name: normalizeFieldsExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _normalizeFieldsExpr ;
    
    /**
     *  
     * xml name: parentProp
     * 
     */
    private java.lang.String _parentProp ;
    
    /**
     *  
     * xml name: schema
     * schema包含如下几种情况：1. 简单数据类型 2. Map（命名属性集合） 3. List（顺序结构，重复结构） 4. Union（switch选择结构）
     * Map对应props配置,  List对应item配置, Union对应oneOf配置
     */
    private io.nop.xlang.xmeta.ISchema _schema ;
    
    /**
     *  
     * xml name: stripText
     * 
     */
    private java.lang.Boolean _stripText ;
    
    /**
     *  
     * xml name: typeProp
     * 
     */
    private java.lang.String _typeProp ;
    
    /**
     *  
     * xml name: unknownField
     * 
     */
    private io.nop.excel.imp.model.ImportFieldModel _unknownField ;
    
    /**
     *  
     * xml name: valueExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _valueExpr ;
    
    /**
     *  
     * xml name: virtual
     * 虚拟字段不会设置到实体上。只是会运行valueExpr表达式
     */
    private boolean _virtual  = false;
    
    /**
     *  
     * xml name: when
     * 
     */
    private io.nop.core.lang.eval.IEvalPredicate _when ;
    
    /**
     * 
     * xml name: alias
     *  
     */
    
    public java.util.Set<java.lang.String> getAlias(){
      return _alias;
    }

    
    public void setAlias(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._alias = value;
           
    }

    
    /**
     * 
     * xml name: computed
     *  是否计算字段。计算字段不需要从文件读取，而是通过valueExpr计算
     */
    
    public boolean isComputed(){
      return _computed;
    }

    
    public void setComputed(boolean value){
        checkAllowChange();
        
        this._computed = value;
           
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
     * xml name: exportExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getExportExpr(){
      return _exportExpr;
    }

    
    public void setExportExpr(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._exportExpr = value;
           
    }

    
    /**
     * 
     * xml name: fields
     *  
     */
    
    public java.util.List<io.nop.excel.imp.model.ImportFieldModel> getFields(){
      return _fields;
    }

    
    public void setFields(java.util.List<io.nop.excel.imp.model.ImportFieldModel> value){
        checkAllowChange();
        
        this._fields = value;
           
    }

    
    /**
     * 
     * xml name: keyProp
     *  
     */
    
    public java.lang.String getKeyProp(){
      return _keyProp;
    }

    
    public void setKeyProp(java.lang.String value){
        checkAllowChange();
        
        this._keyProp = value;
           
    }

    
    /**
     * 
     * xml name: list
     *  是否列表定义，解析得到List类型，内部fields定义的是列表条目对象的属性。
     */
    
    public boolean isList(){
      return _list;
    }

    
    public void setList(boolean value){
        checkAllowChange();
        
        this._list = value;
           
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
     * xml name: normalizeFieldsExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getNormalizeFieldsExpr(){
      return _normalizeFieldsExpr;
    }

    
    public void setNormalizeFieldsExpr(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._normalizeFieldsExpr = value;
           
    }

    
    /**
     * 
     * xml name: parentProp
     *  
     */
    
    public java.lang.String getParentProp(){
      return _parentProp;
    }

    
    public void setParentProp(java.lang.String value){
        checkAllowChange();
        
        this._parentProp = value;
           
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
     * xml name: stripText
     *  
     */
    
    public java.lang.Boolean getStripText(){
      return _stripText;
    }

    
    public void setStripText(java.lang.Boolean value){
        checkAllowChange();
        
        this._stripText = value;
           
    }

    
    /**
     * 
     * xml name: typeProp
     *  
     */
    
    public java.lang.String getTypeProp(){
      return _typeProp;
    }

    
    public void setTypeProp(java.lang.String value){
        checkAllowChange();
        
        this._typeProp = value;
           
    }

    
    /**
     * 
     * xml name: unknownField
     *  
     */
    
    public io.nop.excel.imp.model.ImportFieldModel getUnknownField(){
      return _unknownField;
    }

    
    public void setUnknownField(io.nop.excel.imp.model.ImportFieldModel value){
        checkAllowChange();
        
        this._unknownField = value;
           
    }

    
    /**
     * 
     * xml name: valueExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getValueExpr(){
      return _valueExpr;
    }

    
    public void setValueExpr(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._valueExpr = value;
           
    }

    
    /**
     * 
     * xml name: virtual
     *  虚拟字段不会设置到实体上。只是会运行valueExpr表达式
     */
    
    public boolean isVirtual(){
      return _virtual;
    }

    
    public void setVirtual(boolean value){
        checkAllowChange();
        
        this._virtual = value;
           
    }

    
    /**
     * 
     * xml name: when
     *  
     */
    
    public io.nop.core.lang.eval.IEvalPredicate getWhen(){
      return _when;
    }

    
    public void setWhen(io.nop.core.lang.eval.IEvalPredicate value){
        checkAllowChange();
        
        this._when = value;
           
    }

    

    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._fields = io.nop.api.core.util.FreezeHelper.deepFreeze(this._fields);
            
           this._schema = io.nop.api.core.util.FreezeHelper.deepFreeze(this._schema);
            
           this._unknownField = io.nop.api.core.util.FreezeHelper.deepFreeze(this._unknownField);
            
        }
    }

    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("alias",this.getAlias());
        out.put("computed",this.isComputed());
        out.put("displayName",this.getDisplayName());
        out.put("exportExpr",this.getExportExpr());
        out.put("fields",this.getFields());
        out.put("keyProp",this.getKeyProp());
        out.put("list",this.isList());
        out.put("mandatory",this.isMandatory());
        out.put("name",this.getName());
        out.put("normalizeFieldsExpr",this.getNormalizeFieldsExpr());
        out.put("parentProp",this.getParentProp());
        out.put("schema",this.getSchema());
        out.put("stripText",this.getStripText());
        out.put("typeProp",this.getTypeProp());
        out.put("unknownField",this.getUnknownField());
        out.put("valueExpr",this.getValueExpr());
        out.put("virtual",this.isVirtual());
        out.put("when",this.getWhen());
    }
}
 // resume CPD analysis - CPD-ON
