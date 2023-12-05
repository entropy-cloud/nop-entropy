package io.nop.excel.imp.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [31:10:0:0]/nop/schema/excel/imp.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _ImportSheetModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: afterParse
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _afterParse ;
    
    /**
     *  
     * xml name: beforeParse
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _beforeParse ;
    
    /**
     *  
     * xml name: description
     * 
     */
    private java.lang.String _description ;
    
    /**
     *  
     * xml name: displayMode
     * 
     */
    private java.lang.String _displayMode ;
    
    /**
     *  
     * xml name: field
     * 整个sheet的数据解析为一个字段。如果multiple=true，则field对应List类型，而sheet的数据解析为List中的一个条目。
     */
    private java.lang.String _field ;
    
    /**
     *  
     * xml name: fieldDecider
     * 动态确定Excel单元格所对应的field，返回field的name
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
     * xml name: ignore
     * 
     */
    private boolean _ignore  = false;
    
    /**
     *  
     * xml name: keyProp
     * 
     */
    private java.lang.String _keyProp ;
    
    /**
     *  
     * xml name: list
     * sheet中的数据是否为列表数据
     */
    private boolean _list  = false;
    
    /**
     *  
     * xml name: mandatory
     * 当导入数据文件中不存在满足条件的sheet时，是否抛出异常
     */
    private boolean _mandatory  = false;
    
    /**
     *  
     * xml name: multiple
     * 如果设置为true, 则有可能匹配到多个sheet, 否则只应该匹配到一个sheet。multiple和list不能同时为true
     */
    private boolean _multiple  = false;
    
    /**
     *  
     * xml name: name
     * 匹配的sheet的名称
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: namePattern
     * 正则表达式模式。用于匹配需要解析的对应sheet。如果无法通过name匹配，则会尝试按照namePattern来匹配
     */
    private java.lang.String _namePattern ;
    
    /**
     *  
     * xml name: normalizeFieldsExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _normalizeFieldsExpr ;
    
    /**
     *  
     * xml name: resultType
     * 
     */
    private io.nop.core.type.IGenericType _resultType ;
    
    /**
     *  
     * xml name: sheetNameProp
     * 
     */
    private java.lang.String _sheetNameProp ;
    
    /**
     *  
     * xml name: sheetVarName
     * 
     */
    private java.lang.String _sheetVarName ;
    
    /**
     *  
     * xml name: unknownField
     * 
     */
    private io.nop.excel.imp.model.ImportFieldModel _unknownField ;
    
    /**
     *  
     * xml name: when
     * 如果非空，则满足此条件时本sheet对应的导入操作才执行
     */
    private io.nop.core.lang.eval.IEvalPredicate _when ;
    
    /**
     * 
     * xml name: afterParse
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getAfterParse(){
      return _afterParse;
    }

    
    public void setAfterParse(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._afterParse = value;
           
    }

    
    /**
     * 
     * xml name: beforeParse
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getBeforeParse(){
      return _beforeParse;
    }

    
    public void setBeforeParse(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._beforeParse = value;
           
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
     * xml name: field
     *  整个sheet的数据解析为一个字段。如果multiple=true，则field对应List类型，而sheet的数据解析为List中的一个条目。
     */
    
    public java.lang.String getField(){
      return _field;
    }

    
    public void setField(java.lang.String value){
        checkAllowChange();
        
        this._field = value;
           
    }

    
    /**
     * 
     * xml name: fieldDecider
     *  动态确定Excel单元格所对应的field，返回field的name
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
     * xml name: ignore
     *  
     */
    
    public boolean isIgnore(){
      return _ignore;
    }

    
    public void setIgnore(boolean value){
        checkAllowChange();
        
        this._ignore = value;
           
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
     *  sheet中的数据是否为列表数据
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
     *  当导入数据文件中不存在满足条件的sheet时，是否抛出异常
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
     * xml name: multiple
     *  如果设置为true, 则有可能匹配到多个sheet, 否则只应该匹配到一个sheet。multiple和list不能同时为true
     */
    
    public boolean isMultiple(){
      return _multiple;
    }

    
    public void setMultiple(boolean value){
        checkAllowChange();
        
        this._multiple = value;
           
    }

    
    /**
     * 
     * xml name: name
     *  匹配的sheet的名称
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
     * xml name: namePattern
     *  正则表达式模式。用于匹配需要解析的对应sheet。如果无法通过name匹配，则会尝试按照namePattern来匹配
     */
    
    public java.lang.String getNamePattern(){
      return _namePattern;
    }

    
    public void setNamePattern(java.lang.String value){
        checkAllowChange();
        
        this._namePattern = value;
           
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
     * xml name: resultType
     *  
     */
    
    public io.nop.core.type.IGenericType getResultType(){
      return _resultType;
    }

    
    public void setResultType(io.nop.core.type.IGenericType value){
        checkAllowChange();
        
        this._resultType = value;
           
    }

    
    /**
     * 
     * xml name: sheetNameProp
     *  
     */
    
    public java.lang.String getSheetNameProp(){
      return _sheetNameProp;
    }

    
    public void setSheetNameProp(java.lang.String value){
        checkAllowChange();
        
        this._sheetNameProp = value;
           
    }

    
    /**
     * 
     * xml name: sheetVarName
     *  
     */
    
    public java.lang.String getSheetVarName(){
      return _sheetVarName;
    }

    
    public void setSheetVarName(java.lang.String value){
        checkAllowChange();
        
        this._sheetVarName = value;
           
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
     * xml name: when
     *  如果非空，则满足此条件时本sheet对应的导入操作才执行
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
            
           this._unknownField = io.nop.api.core.util.FreezeHelper.deepFreeze(this._unknownField);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("afterParse",this.getAfterParse());
        out.put("beforeParse",this.getBeforeParse());
        out.put("description",this.getDescription());
        out.put("displayMode",this.getDisplayMode());
        out.put("field",this.getField());
        out.put("fieldDecider",this.getFieldDecider());
        out.put("fields",this.getFields());
        out.put("ignore",this.isIgnore());
        out.put("keyProp",this.getKeyProp());
        out.put("list",this.isList());
        out.put("mandatory",this.isMandatory());
        out.put("multiple",this.isMultiple());
        out.put("name",this.getName());
        out.put("namePattern",this.getNamePattern());
        out.put("normalizeFieldsExpr",this.getNormalizeFieldsExpr());
        out.put("resultType",this.getResultType());
        out.put("sheetNameProp",this.getSheetNameProp());
        out.put("sheetVarName",this.getSheetVarName());
        out.put("unknownField",this.getUnknownField());
        out.put("when",this.getWhen());
    }
}
 // resume CPD analysis - CPD-ON
