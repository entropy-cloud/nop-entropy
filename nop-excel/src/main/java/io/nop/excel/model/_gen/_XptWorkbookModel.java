package io.nop.excel.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.model.XptWorkbookModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/workbook.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _XptWorkbookModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: afterExpand
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _afterExpand ;
    
    /**
     *  
     * xml name: beforeExpand
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _beforeExpand ;
    
    /**
     *  
     * xml name: beginLoop
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _beginLoop ;
    
    /**
     *  
     * xml name: dump
     * 如果为true，则每一步展开的中间结果都会输出为html
     */
    private java.lang.Boolean _dump ;
    
    /**
     *  
     * xml name: dumpDir
     * 配置了dumpDir，则dump的时候会输出到此目录下
     */
    private java.lang.String _dumpDir ;
    
    /**
     *  
     * xml name: editors
     * 
     */
    private KeyedList<io.nop.excel.model.XptXplModel> _editors = KeyedList.emptyList();
    
    /**
     *  
     * xml name: endLoop
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _endLoop ;
    
    /**
     *  
     * xml name: loopIndexName
     * 
     */
    private java.lang.String _loopIndexName ;
    
    /**
     *  
     * xml name: loopItemsName
     * 
     */
    private java.lang.String _loopItemsName ;
    
    /**
     *  
     * xml name: loopVarName
     * 
     */
    private java.lang.String _loopVarName ;
    
    /**
     *  
     * xml name: maxSheetNameLength
     * 
     */
    private java.lang.Integer _maxSheetNameLength ;
    
    /**
     *  
     * xml name: removeHiddenCell
     * 
     */
    private boolean _removeHiddenCell  = false;
    
    /**
     *  
     * xml name: viewers
     * 
     */
    private KeyedList<io.nop.excel.model.XptXplModel> _viewers = KeyedList.emptyList();
    
    /**
     * 
     * xml name: afterExpand
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getAfterExpand(){
      return _afterExpand;
    }

    
    public void setAfterExpand(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._afterExpand = value;
           
    }

    
    /**
     * 
     * xml name: beforeExpand
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getBeforeExpand(){
      return _beforeExpand;
    }

    
    public void setBeforeExpand(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._beforeExpand = value;
           
    }

    
    /**
     * 
     * xml name: beginLoop
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getBeginLoop(){
      return _beginLoop;
    }

    
    public void setBeginLoop(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._beginLoop = value;
           
    }

    
    /**
     * 
     * xml name: dump
     *  如果为true，则每一步展开的中间结果都会输出为html
     */
    
    public java.lang.Boolean getDump(){
      return _dump;
    }

    
    public void setDump(java.lang.Boolean value){
        checkAllowChange();
        
        this._dump = value;
           
    }

    
    /**
     * 
     * xml name: dumpDir
     *  配置了dumpDir，则dump的时候会输出到此目录下
     */
    
    public java.lang.String getDumpDir(){
      return _dumpDir;
    }

    
    public void setDumpDir(java.lang.String value){
        checkAllowChange();
        
        this._dumpDir = value;
           
    }

    
    /**
     * 
     * xml name: editors
     *  
     */
    
    public java.util.List<io.nop.excel.model.XptXplModel> getEditors(){
      return _editors;
    }

    
    public void setEditors(java.util.List<io.nop.excel.model.XptXplModel> value){
        checkAllowChange();
        
        this._editors = KeyedList.fromList(value, io.nop.excel.model.XptXplModel::getId);
           
    }

    
    public io.nop.excel.model.XptXplModel getEditr(String name){
        return this._editors.getByKey(name);
    }

    public boolean hasEditr(String name){
        return this._editors.containsKey(name);
    }

    public void addEditr(io.nop.excel.model.XptXplModel item) {
        checkAllowChange();
        java.util.List<io.nop.excel.model.XptXplModel> list = this.getEditors();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.excel.model.XptXplModel::getId);
            setEditors(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_editors(){
        return this._editors.keySet();
    }

    public boolean hasEditors(){
        return !this._editors.isEmpty();
    }
    
    /**
     * 
     * xml name: endLoop
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getEndLoop(){
      return _endLoop;
    }

    
    public void setEndLoop(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._endLoop = value;
           
    }

    
    /**
     * 
     * xml name: loopIndexName
     *  
     */
    
    public java.lang.String getLoopIndexName(){
      return _loopIndexName;
    }

    
    public void setLoopIndexName(java.lang.String value){
        checkAllowChange();
        
        this._loopIndexName = value;
           
    }

    
    /**
     * 
     * xml name: loopItemsName
     *  
     */
    
    public java.lang.String getLoopItemsName(){
      return _loopItemsName;
    }

    
    public void setLoopItemsName(java.lang.String value){
        checkAllowChange();
        
        this._loopItemsName = value;
           
    }

    
    /**
     * 
     * xml name: loopVarName
     *  
     */
    
    public java.lang.String getLoopVarName(){
      return _loopVarName;
    }

    
    public void setLoopVarName(java.lang.String value){
        checkAllowChange();
        
        this._loopVarName = value;
           
    }

    
    /**
     * 
     * xml name: maxSheetNameLength
     *  
     */
    
    public java.lang.Integer getMaxSheetNameLength(){
      return _maxSheetNameLength;
    }

    
    public void setMaxSheetNameLength(java.lang.Integer value){
        checkAllowChange();
        
        this._maxSheetNameLength = value;
           
    }

    
    /**
     * 
     * xml name: removeHiddenCell
     *  
     */
    
    public boolean isRemoveHiddenCell(){
      return _removeHiddenCell;
    }

    
    public void setRemoveHiddenCell(boolean value){
        checkAllowChange();
        
        this._removeHiddenCell = value;
           
    }

    
    /**
     * 
     * xml name: viewers
     *  
     */
    
    public java.util.List<io.nop.excel.model.XptXplModel> getViewers(){
      return _viewers;
    }

    
    public void setViewers(java.util.List<io.nop.excel.model.XptXplModel> value){
        checkAllowChange();
        
        this._viewers = KeyedList.fromList(value, io.nop.excel.model.XptXplModel::getId);
           
    }

    
    public io.nop.excel.model.XptXplModel getViewer(String name){
        return this._viewers.getByKey(name);
    }

    public boolean hasViewer(String name){
        return this._viewers.containsKey(name);
    }

    public void addViewer(io.nop.excel.model.XptXplModel item) {
        checkAllowChange();
        java.util.List<io.nop.excel.model.XptXplModel> list = this.getViewers();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.excel.model.XptXplModel::getId);
            setViewers(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_viewers(){
        return this._viewers.keySet();
    }

    public boolean hasViewers(){
        return !this._viewers.isEmpty();
    }
    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._editors = io.nop.api.core.util.FreezeHelper.deepFreeze(this._editors);
            
           this._viewers = io.nop.api.core.util.FreezeHelper.deepFreeze(this._viewers);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("afterExpand",this.getAfterExpand());
        out.putNotNull("beforeExpand",this.getBeforeExpand());
        out.putNotNull("beginLoop",this.getBeginLoop());
        out.putNotNull("dump",this.getDump());
        out.putNotNull("dumpDir",this.getDumpDir());
        out.putNotNull("editors",this.getEditors());
        out.putNotNull("endLoop",this.getEndLoop());
        out.putNotNull("loopIndexName",this.getLoopIndexName());
        out.putNotNull("loopItemsName",this.getLoopItemsName());
        out.putNotNull("loopVarName",this.getLoopVarName());
        out.putNotNull("maxSheetNameLength",this.getMaxSheetNameLength());
        out.putNotNull("removeHiddenCell",this.isRemoveHiddenCell());
        out.putNotNull("viewers",this.getViewers());
    }

    public XptWorkbookModel cloneInstance(){
        XptWorkbookModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(XptWorkbookModel instance){
        super.copyTo(instance);
        
        instance.setAfterExpand(this.getAfterExpand());
        instance.setBeforeExpand(this.getBeforeExpand());
        instance.setBeginLoop(this.getBeginLoop());
        instance.setDump(this.getDump());
        instance.setDumpDir(this.getDumpDir());
        instance.setEditors(this.getEditors());
        instance.setEndLoop(this.getEndLoop());
        instance.setLoopIndexName(this.getLoopIndexName());
        instance.setLoopItemsName(this.getLoopItemsName());
        instance.setLoopVarName(this.getLoopVarName());
        instance.setMaxSheetNameLength(this.getMaxSheetNameLength());
        instance.setRemoveHiddenCell(this.isRemoveHiddenCell());
        instance.setViewers(this.getViewers());
    }

    protected XptWorkbookModel newInstance(){
        return (XptWorkbookModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
