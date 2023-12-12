package io.nop.excel.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [210:14:0:0]/nop/schema/excel/workbook.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101"})
public abstract class _ExcelPrint extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: fitHeight
     * 
     */
    private boolean _fitHeight  = false;
    
    /**
     *  
     * xml name: fitWidth
     * 
     */
    private boolean _fitWidth  = false;
    
    /**
     *  
     * xml name: gridlines
     * 
     */
    private java.lang.Boolean _gridlines ;
    
    /**
     *  
     * xml name: horizontalResolution
     * 
     */
    private java.lang.Integer _horizontalResolution ;
    
    /**
     *  
     * xml name: pageSizeIndex
     * 
     */
    private java.lang.Integer _pageSizeIndex ;
    
    /**
     *  
     * xml name: rowColHeadings
     * 
     */
    private java.lang.Boolean _rowColHeadings ;
    
    /**
     *  
     * xml name: scale
     * 
     */
    private java.lang.Double _scale ;
    
    /**
     *  
     * xml name: verticalResolution
     * 
     */
    private java.lang.Integer _verticalResolution ;
    
    /**
     * 
     * xml name: fitHeight
     *  
     */
    
    public boolean isFitHeight(){
      return _fitHeight;
    }

    
    public void setFitHeight(boolean value){
        checkAllowChange();
        
        this._fitHeight = value;
           
    }

    
    /**
     * 
     * xml name: fitWidth
     *  
     */
    
    public boolean isFitWidth(){
      return _fitWidth;
    }

    
    public void setFitWidth(boolean value){
        checkAllowChange();
        
        this._fitWidth = value;
           
    }

    
    /**
     * 
     * xml name: gridlines
     *  
     */
    
    public java.lang.Boolean getGridlines(){
      return _gridlines;
    }

    
    public void setGridlines(java.lang.Boolean value){
        checkAllowChange();
        
        this._gridlines = value;
           
    }

    
    /**
     * 
     * xml name: horizontalResolution
     *  
     */
    
    public java.lang.Integer getHorizontalResolution(){
      return _horizontalResolution;
    }

    
    public void setHorizontalResolution(java.lang.Integer value){
        checkAllowChange();
        
        this._horizontalResolution = value;
           
    }

    
    /**
     * 
     * xml name: pageSizeIndex
     *  
     */
    
    public java.lang.Integer getPageSizeIndex(){
      return _pageSizeIndex;
    }

    
    public void setPageSizeIndex(java.lang.Integer value){
        checkAllowChange();
        
        this._pageSizeIndex = value;
           
    }

    
    /**
     * 
     * xml name: rowColHeadings
     *  
     */
    
    public java.lang.Boolean getRowColHeadings(){
      return _rowColHeadings;
    }

    
    public void setRowColHeadings(java.lang.Boolean value){
        checkAllowChange();
        
        this._rowColHeadings = value;
           
    }

    
    /**
     * 
     * xml name: scale
     *  
     */
    
    public java.lang.Double getScale(){
      return _scale;
    }

    
    public void setScale(java.lang.Double value){
        checkAllowChange();
        
        this._scale = value;
           
    }

    
    /**
     * 
     * xml name: verticalResolution
     *  
     */
    
    public java.lang.Integer getVerticalResolution(){
      return _verticalResolution;
    }

    
    public void setVerticalResolution(java.lang.Integer value){
        checkAllowChange();
        
        this._verticalResolution = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("fitHeight",this.isFitHeight());
        out.put("fitWidth",this.isFitWidth());
        out.put("gridlines",this.getGridlines());
        out.put("horizontalResolution",this.getHorizontalResolution());
        out.put("pageSizeIndex",this.getPageSizeIndex());
        out.put("rowColHeadings",this.getRowColHeadings());
        out.put("scale",this.getScale());
        out.put("verticalResolution",this.getVerticalResolution());
    }
}
 // resume CPD analysis - CPD-ON
