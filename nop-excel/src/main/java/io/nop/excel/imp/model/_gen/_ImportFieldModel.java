package io.nop.excel.imp.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [61:18:0:0]/nop/schema/excel/imp.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116"})
public abstract class _ImportFieldModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: alias
     * 
     */
    private java.util.Set<java.lang.String> _alias ;
    
    /**
     *  是否计算字段
     * xml name: computed
     * 计算字段不需要从文件读取，而是通过valueExpr计算
     */
    private boolean _computed  = false;
    
    /**
     *  
     * xml name: displayMode
     * 
     */
    private java.lang.String _displayMode ;
    
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
     * xml name: fieldDecider
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _fieldDecider ;
    
    /**
     *  
     * xml name: fields
     * 
     */
    private KeyedList<io.nop.excel.imp.model.ImportFieldModel> _fields = KeyedList.emptyList();
    
    /**
     *  
     * xml name: groupField
     * 列表表头中多个字段可能归属于一个分组字段，采用多级表头形式显示
     */
    private java.lang.String _groupField ;
    
    /**
     *  
     * xml name: ignoreWhenEmpty
     * 当数据为空的时候自动忽略该字段，不设置到record对象上
     */
    private boolean _ignoreWhenEmpty  = false;
    
    /**
     *  
     * xml name: keyProp
     * 当list=true时，keyProp表示集合中每个对象采用这个属性作为唯一键。解析的得到的列表会使用KeyedList对象
     */
    private java.lang.String _keyProp ;
    
    /**
     *  是否列表定义
     * xml name: list
     * 解析得到List类型，内部fields定义的是列表条目对象的属性。
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
     * 执行时上下文中存在record对象。
     * 如果是对象字段，record对应于当前对象。对应简单字段，record对应于父对此昂
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
     * xml name: prop
     * 实际设置到record上的属性名，如果为空，则prop与name相同
     */
    private java.lang.String _prop ;
    
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
     * 是否计算字段
     * xml name: computed
     *  计算字段不需要从文件读取，而是通过valueExpr计算
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
     * xml name: displayMode
     *  
     */
    
    public java.lang.String getDisplayMode(){
      return _displayMode;
    }

    
    public void setDisplayMode(java.lang.String value){
        checkAllowChange();
        
        this._displayMode = value;
           
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
     * xml name: fieldDecider
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getFieldDecider(){
      return _fieldDecider;
    }

    
    public void setFieldDecider(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._fieldDecider = value;
           
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
        
        this._fields = KeyedList.fromList(value, io.nop.excel.imp.model.ImportFieldModel::getName);
           
    }

    
    public io.nop.excel.imp.model.ImportFieldModel getField(String name){
        return this._fields.getByKey(name);
    }

    public boolean hasField(String name){
        return this._fields.containsKey(name);
    }

    public void addField(io.nop.excel.imp.model.ImportFieldModel item) {
        checkAllowChange();
        java.util.List<io.nop.excel.imp.model.ImportFieldModel> list = this.getFields();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.excel.imp.model.ImportFieldModel::getName);
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
     * xml name: groupField
     *  列表表头中多个字段可能归属于一个分组字段，采用多级表头形式显示
     */
    
    public java.lang.String getGroupField(){
      return _groupField;
    }

    
    public void setGroupField(java.lang.String value){
        checkAllowChange();
        
        this._groupField = value;
           
    }

    
    /**
     * 
     * xml name: ignoreWhenEmpty
     *  当数据为空的时候自动忽略该字段，不设置到record对象上
     */
    
    public boolean isIgnoreWhenEmpty(){
      return _ignoreWhenEmpty;
    }

    
    public void setIgnoreWhenEmpty(boolean value){
        checkAllowChange();
        
        this._ignoreWhenEmpty = value;
           
    }

    
    /**
     * 
     * xml name: keyProp
     *  当list=true时，keyProp表示集合中每个对象采用这个属性作为唯一键。解析的得到的列表会使用KeyedList对象
     */
    
    public java.lang.String getKeyProp(){
      return _keyProp;
    }

    
    public void setKeyProp(java.lang.String value){
        checkAllowChange();
        
        this._keyProp = value;
           
    }

    
    /**
     * 是否列表定义
     * xml name: list
     *  解析得到List类型，内部fields定义的是列表条目对象的属性。
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
     *  执行时上下文中存在record对象。
     * 如果是对象字段，record对应于当前对象。对应简单字段，record对应于父对此昂
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
     * xml name: prop
     *  实际设置到record上的属性名，如果为空，则prop与name相同
     */
    
    public java.lang.String getProp(){
      return _prop;
    }

    
    public void setProp(java.lang.String value){
        checkAllowChange();
        
        this._prop = value;
           
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

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._fields = io.nop.api.core.util.FreezeHelper.deepFreeze(this._fields);
            
           this._schema = io.nop.api.core.util.FreezeHelper.deepFreeze(this._schema);
            
           this._unknownField = io.nop.api.core.util.FreezeHelper.deepFreeze(this._unknownField);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("alias",this.getAlias());
        out.put("computed",this.isComputed());
        out.put("displayMode",this.getDisplayMode());
        out.put("displayName",this.getDisplayName());
        out.put("exportExpr",this.getExportExpr());
        out.put("fieldDecider",this.getFieldDecider());
        out.put("fields",this.getFields());
        out.put("groupField",this.getGroupField());
        out.put("ignoreWhenEmpty",this.isIgnoreWhenEmpty());
        out.put("keyProp",this.getKeyProp());
        out.put("list",this.isList());
        out.put("mandatory",this.isMandatory());
        out.put("name",this.getName());
        out.put("normalizeFieldsExpr",this.getNormalizeFieldsExpr());
        out.put("parentProp",this.getParentProp());
        out.put("prop",this.getProp());
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
