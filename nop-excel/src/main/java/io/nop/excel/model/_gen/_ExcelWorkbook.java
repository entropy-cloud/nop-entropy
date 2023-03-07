package io.nop.excel.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [7:2:0:0]/nop/schema/excel/workbook.xdef <p>
 * ooxml的文档参考 http://officeopenxml.com/SSstyles.php
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _ExcelWorkbook extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: defaultFont
     * 
     */
    private io.nop.excel.model.ExcelFont _defaultFont ;
    
    /**
     *  
     * xml name: model
     * 
     */
    private io.nop.excel.model.XptWorkbookModel _model ;
    
    /**
     *  
     * xml name: props
     * 
     */
    private KeyedList<io.nop.excel.model.ExcelProperty> _props = KeyedList.emptyList();
    
    /**
     *  
     * xml name: sheets
     * 
     */
    private KeyedList<io.nop.excel.model.ExcelSheet> _sheets = KeyedList.emptyList();
    
    /**
     *  
     * xml name: styles
     * 
     */
    private KeyedList<io.nop.excel.model.ExcelStyle> _styles = KeyedList.emptyList();
    
    /**
     * 
     * xml name: defaultFont
     *  
     */
    
    public io.nop.excel.model.ExcelFont getDefaultFont(){
      return _defaultFont;
    }

    
    public void setDefaultFont(io.nop.excel.model.ExcelFont value){
        checkAllowChange();
        
        this._defaultFont = value;
           
    }

    
    /**
     * 
     * xml name: model
     *  
     */
    
    public io.nop.excel.model.XptWorkbookModel getModel(){
      return _model;
    }

    
    public void setModel(io.nop.excel.model.XptWorkbookModel value){
        checkAllowChange();
        
        this._model = value;
           
    }

    
    /**
     * 
     * xml name: props
     *  
     */
    
    public java.util.List<io.nop.excel.model.ExcelProperty> getProps(){
      return _props;
    }

    
    public void setProps(java.util.List<io.nop.excel.model.ExcelProperty> value){
        checkAllowChange();
        
        this._props = KeyedList.fromList(value, io.nop.excel.model.ExcelProperty::getName);
           
    }

    
    public io.nop.excel.model.ExcelProperty getProp(String name){
        return this._props.getByKey(name);
    }

    public boolean hasProp(String name){
        return this._props.containsKey(name);
    }

    public void addProp(io.nop.excel.model.ExcelProperty item) {
        checkAllowChange();
        java.util.List<io.nop.excel.model.ExcelProperty> list = this.getProps();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.excel.model.ExcelProperty::getName);
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
     * xml name: sheets
     *  
     */
    
    public java.util.List<io.nop.excel.model.ExcelSheet> getSheets(){
      return _sheets;
    }

    
    public void setSheets(java.util.List<io.nop.excel.model.ExcelSheet> value){
        checkAllowChange();
        
        this._sheets = KeyedList.fromList(value, io.nop.excel.model.ExcelSheet::getName);
           
    }

    
    public io.nop.excel.model.ExcelSheet getSheet(String name){
        return this._sheets.getByKey(name);
    }

    public boolean hasSheet(String name){
        return this._sheets.containsKey(name);
    }

    public void addSheet(io.nop.excel.model.ExcelSheet item) {
        checkAllowChange();
        java.util.List<io.nop.excel.model.ExcelSheet> list = this.getSheets();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.excel.model.ExcelSheet::getName);
            setSheets(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_sheets(){
        return this._sheets.keySet();
    }

    public boolean hasSheets(){
        return !this._sheets.isEmpty();
    }
    
    /**
     * 
     * xml name: styles
     *  
     */
    
    public java.util.List<io.nop.excel.model.ExcelStyle> getStyles(){
      return _styles;
    }

    
    public void setStyles(java.util.List<io.nop.excel.model.ExcelStyle> value){
        checkAllowChange();
        
        this._styles = KeyedList.fromList(value, io.nop.excel.model.ExcelStyle::getId);
           
    }

    
    public io.nop.excel.model.ExcelStyle getStyle(String name){
        return this._styles.getByKey(name);
    }

    public boolean hasStyle(String name){
        return this._styles.containsKey(name);
    }

    public void addStyle(io.nop.excel.model.ExcelStyle item) {
        checkAllowChange();
        java.util.List<io.nop.excel.model.ExcelStyle> list = this.getStyles();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.excel.model.ExcelStyle::getId);
            setStyles(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_styles(){
        return this._styles.keySet();
    }

    public boolean hasStyles(){
        return !this._styles.isEmpty();
    }
    

    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._defaultFont = io.nop.api.core.util.FreezeHelper.deepFreeze(this._defaultFont);
            
           this._model = io.nop.api.core.util.FreezeHelper.deepFreeze(this._model);
            
           this._props = io.nop.api.core.util.FreezeHelper.deepFreeze(this._props);
            
           this._sheets = io.nop.api.core.util.FreezeHelper.deepFreeze(this._sheets);
            
           this._styles = io.nop.api.core.util.FreezeHelper.deepFreeze(this._styles);
            
        }
    }

    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("defaultFont",this.getDefaultFont());
        out.put("model",this.getModel());
        out.put("props",this.getProps());
        out.put("sheets",this.getSheets());
        out.put("styles",this.getStyles());
    }
}
 // resume CPD analysis - CPD-ON
