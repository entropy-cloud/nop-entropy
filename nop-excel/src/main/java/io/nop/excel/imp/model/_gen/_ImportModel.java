package io.nop.excel.imp.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [7:2:0:0]/nop/schema/excel/imp.xdef <p>
 * 导入不涉及到展现控制，仅仅需要考虑后台处理逻辑，因此比导出设计要简单的多。导入策略与导出策略可以共享objMeta上的信息。
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _ImportModel extends io.nop.core.resource.component.AbstractComponentModel {
    
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
     * xml name: defaultStripText
     * 
     */
    private boolean _defaultStripText  = true;
    
    /**
     *  
     * xml name: dump
     * 
     */
    private boolean _dump  = false;
    
    /**
     *  
     * xml name: ignoreUnknownSheet
     * 
     */
    private boolean _ignoreUnknownSheet  = false;
    
    /**
     *  
     * xml name: resultType
     * 
     */
    private io.nop.core.type.IGenericType _resultType ;
    
    /**
     *  
     * xml name: sheets
     * 
     */
    private KeyedList<io.nop.excel.imp.model.ImportSheetModel> _sheets = KeyedList.emptyList();
    
    /**
     *  
     * xml name: templatePath
     * 空的导入模板文件。导出数据时也会使用这个模板
     */
    private java.lang.String _templatePath ;
    
    /**
     *  
     * xml name: validator
     * 
     */
    private io.nop.core.model.validator.ValidatorModel _validator ;
    
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
     * xml name: defaultStripText
     *  
     */
    
    public boolean isDefaultStripText(){
      return _defaultStripText;
    }

    
    public void setDefaultStripText(boolean value){
        checkAllowChange();
        
        this._defaultStripText = value;
           
    }

    
    /**
     * 
     * xml name: dump
     *  
     */
    
    public boolean isDump(){
      return _dump;
    }

    
    public void setDump(boolean value){
        checkAllowChange();
        
        this._dump = value;
           
    }

    
    /**
     * 
     * xml name: ignoreUnknownSheet
     *  
     */
    
    public boolean isIgnoreUnknownSheet(){
      return _ignoreUnknownSheet;
    }

    
    public void setIgnoreUnknownSheet(boolean value){
        checkAllowChange();
        
        this._ignoreUnknownSheet = value;
           
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
     * xml name: sheets
     *  
     */
    
    public java.util.List<io.nop.excel.imp.model.ImportSheetModel> getSheets(){
      return _sheets;
    }

    
    public void setSheets(java.util.List<io.nop.excel.imp.model.ImportSheetModel> value){
        checkAllowChange();
        
        this._sheets = KeyedList.fromList(value, io.nop.excel.imp.model.ImportSheetModel::getName);
           
    }

    
    public io.nop.excel.imp.model.ImportSheetModel getSheet(String name){
        return this._sheets.getByKey(name);
    }

    public boolean hasSheet(String name){
        return this._sheets.containsKey(name);
    }

    public void addSheet(io.nop.excel.imp.model.ImportSheetModel item) {
        checkAllowChange();
        java.util.List<io.nop.excel.imp.model.ImportSheetModel> list = this.getSheets();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.excel.imp.model.ImportSheetModel::getName);
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
     * xml name: templatePath
     *  空的导入模板文件。导出数据时也会使用这个模板
     */
    
    public java.lang.String getTemplatePath(){
      return _templatePath;
    }

    
    public void setTemplatePath(java.lang.String value){
        checkAllowChange();
        
        this._templatePath = value;
           
    }

    
    /**
     * 
     * xml name: validator
     *  
     */
    
    public io.nop.core.model.validator.ValidatorModel getValidator(){
      return _validator;
    }

    
    public void setValidator(io.nop.core.model.validator.ValidatorModel value){
        checkAllowChange();
        
        this._validator = value;
           
    }

    

    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._sheets = io.nop.api.core.util.FreezeHelper.deepFreeze(this._sheets);
            
           this._validator = io.nop.api.core.util.FreezeHelper.deepFreeze(this._validator);
            
        }
    }

    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("afterParse",this.getAfterParse());
        out.put("beforeParse",this.getBeforeParse());
        out.put("defaultStripText",this.isDefaultStripText());
        out.put("dump",this.isDump());
        out.put("ignoreUnknownSheet",this.isIgnoreUnknownSheet());
        out.put("resultType",this.getResultType());
        out.put("sheets",this.getSheets());
        out.put("templatePath",this.getTemplatePath());
        out.put("validator",this.getValidator());
    }
}
 // resume CPD analysis - CPD-ON
