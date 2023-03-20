package io.nop.excel.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [209:6:0:0]/nop/schema/excel/workbook.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
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
    

    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._editors = io.nop.api.core.util.FreezeHelper.deepFreeze(this._editors);
            
           this._viewers = io.nop.api.core.util.FreezeHelper.deepFreeze(this._viewers);
            
        }
    }

    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("afterExpand",this.getAfterExpand());
        out.put("beforeExpand",this.getBeforeExpand());
        out.put("beginLoop",this.getBeginLoop());
        out.put("editors",this.getEditors());
        out.put("endLoop",this.getEndLoop());
        out.put("loopIndexName",this.getLoopIndexName());
        out.put("loopItemsName",this.getLoopItemsName());
        out.put("loopVarName",this.getLoopVarName());
        out.put("viewers",this.getViewers());
    }
}
 // resume CPD analysis - CPD-ON
