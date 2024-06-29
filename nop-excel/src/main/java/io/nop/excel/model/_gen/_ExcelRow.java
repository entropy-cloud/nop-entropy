package io.nop.excel.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.model.ExcelRow;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/workbook.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ExcelRow extends io.nop.core.model.table.impl.AbstractRow {
    
    /**
     *  
     * xml name: autoFitHeight
     * 
     */
    private boolean _autoFitHeight  = false;
    
    /**
     *  
     * xml name: cells
     * 
     */
    private java.util.List<io.nop.excel.model.ExcelCell> _cells = java.util.Collections.emptyList();
    
    /**
     *  
     * xml name: height
     * 
     */
    private java.lang.Double _height ;
    
    /**
     *  
     * xml name: hidden
     * 
     */
    private boolean _hidden  = false;
    
    /**
     *  
     * xml name: model
     * 
     */
    private io.nop.excel.model.XptRowModel _model ;
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: styleId
     * 
     */
    private java.lang.String _styleId ;
    
    /**
     * 
     * xml name: autoFitHeight
     *  
     */
    
    public boolean isAutoFitHeight(){
      return _autoFitHeight;
    }

    
    public void setAutoFitHeight(boolean value){
        checkAllowChange();
        
        this._autoFitHeight = value;
           
    }

    
    /**
     * 
     * xml name: cells
     *  
     */
    
    public java.util.List<io.nop.excel.model.ExcelCell> getCells(){
      return _cells;
    }

    
    public void setCells(java.util.List<io.nop.excel.model.ExcelCell> value){
        checkAllowChange();
        
        this._cells = value;
           
    }

    
    /**
     * 
     * xml name: height
     *  
     */
    
    public java.lang.Double getHeight(){
      return _height;
    }

    
    public void setHeight(java.lang.Double value){
        checkAllowChange();
        
        this._height = value;
           
    }

    
    /**
     * 
     * xml name: hidden
     *  
     */
    
    public boolean isHidden(){
      return _hidden;
    }

    
    public void setHidden(boolean value){
        checkAllowChange();
        
        this._hidden = value;
           
    }

    
    /**
     * 
     * xml name: model
     *  
     */
    
    public io.nop.excel.model.XptRowModel getModel(){
      return _model;
    }

    
    public void setModel(io.nop.excel.model.XptRowModel value){
        checkAllowChange();
        
        this._model = value;
           
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
     * xml name: styleId
     *  
     */
    
    public java.lang.String getStyleId(){
      return _styleId;
    }

    
    public void setStyleId(java.lang.String value){
        checkAllowChange();
        
        this._styleId = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._cells = io.nop.api.core.util.FreezeHelper.deepFreeze(this._cells);
            
           this._model = io.nop.api.core.util.FreezeHelper.deepFreeze(this._model);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("autoFitHeight",this.isAutoFitHeight());
        out.putNotNull("cells",this.getCells());
        out.putNotNull("height",this.getHeight());
        out.putNotNull("hidden",this.isHidden());
        out.putNotNull("model",this.getModel());
        out.putNotNull("name",this.getName());
        out.putNotNull("styleId",this.getStyleId());
    }

    public ExcelRow cloneInstance(){
        ExcelRow instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ExcelRow instance){
        super.copyTo(instance);
        
        instance.setAutoFitHeight(this.isAutoFitHeight());
        instance.setCells(this.getCells());
        instance.setHeight(this.getHeight());
        instance.setHidden(this.isHidden());
        instance.setModel(this.getModel());
        instance.setName(this.getName());
        instance.setStyleId(this.getStyleId());
    }

    protected ExcelRow newInstance(){
        return (ExcelRow) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
