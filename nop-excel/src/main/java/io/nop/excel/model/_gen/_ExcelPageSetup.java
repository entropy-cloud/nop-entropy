package io.nop.excel.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [166:14:0:0]/nop/schema/excel/workbook.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _ExcelPageSetup extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: blackAndWhite
     * 
     */
    private java.lang.Boolean _blackAndWhite ;
    
    /**
     *  
     * xml name: firstPageNumber
     * 
     */
    private java.lang.Integer _firstPageNumber ;
    
    /**
     *  
     * xml name: fitToHeight
     * 
     */
    private java.lang.Boolean _fitToHeight ;
    
    /**
     *  
     * xml name: fitToWidth
     * 
     */
    private java.lang.Boolean _fitToWidth ;
    
    /**
     *  
     * xml name: footer
     * 
     */
    private io.nop.excel.model.ExcelHeaderFooter _footer ;
    
    /**
     *  
     * xml name: header
     * 
     */
    private io.nop.excel.model.ExcelHeaderFooter _header ;
    
    /**
     *  
     * xml name: horizontalCentered
     * 
     */
    private java.lang.Boolean _horizontalCentered ;
    
    /**
     *  
     * xml name: orientationHorizontal
     * 
     */
    private java.lang.Boolean _orientationHorizontal ;
    
    /**
     *  
     * xml name: paperSize
     * 
     */
    private java.lang.Integer _paperSize ;
    
    /**
     *  
     * xml name: scale
     * 放大百分比
     */
    private java.lang.Integer _scale ;
    
    /**
     *  
     * xml name: verticalCentered
     * 
     */
    private java.lang.Boolean _verticalCentered ;
    
    /**
     * 
     * xml name: blackAndWhite
     *  
     */
    
    public java.lang.Boolean getBlackAndWhite(){
      return _blackAndWhite;
    }

    
    public void setBlackAndWhite(java.lang.Boolean value){
        checkAllowChange();
        
        this._blackAndWhite = value;
           
    }

    
    /**
     * 
     * xml name: firstPageNumber
     *  
     */
    
    public java.lang.Integer getFirstPageNumber(){
      return _firstPageNumber;
    }

    
    public void setFirstPageNumber(java.lang.Integer value){
        checkAllowChange();
        
        this._firstPageNumber = value;
           
    }

    
    /**
     * 
     * xml name: fitToHeight
     *  
     */
    
    public java.lang.Boolean getFitToHeight(){
      return _fitToHeight;
    }

    
    public void setFitToHeight(java.lang.Boolean value){
        checkAllowChange();
        
        this._fitToHeight = value;
           
    }

    
    /**
     * 
     * xml name: fitToWidth
     *  
     */
    
    public java.lang.Boolean getFitToWidth(){
      return _fitToWidth;
    }

    
    public void setFitToWidth(java.lang.Boolean value){
        checkAllowChange();
        
        this._fitToWidth = value;
           
    }

    
    /**
     * 
     * xml name: footer
     *  
     */
    
    public io.nop.excel.model.ExcelHeaderFooter getFooter(){
      return _footer;
    }

    
    public void setFooter(io.nop.excel.model.ExcelHeaderFooter value){
        checkAllowChange();
        
        this._footer = value;
           
    }

    
    /**
     * 
     * xml name: header
     *  
     */
    
    public io.nop.excel.model.ExcelHeaderFooter getHeader(){
      return _header;
    }

    
    public void setHeader(io.nop.excel.model.ExcelHeaderFooter value){
        checkAllowChange();
        
        this._header = value;
           
    }

    
    /**
     * 
     * xml name: horizontalCentered
     *  
     */
    
    public java.lang.Boolean getHorizontalCentered(){
      return _horizontalCentered;
    }

    
    public void setHorizontalCentered(java.lang.Boolean value){
        checkAllowChange();
        
        this._horizontalCentered = value;
           
    }

    
    /**
     * 
     * xml name: orientationHorizontal
     *  
     */
    
    public java.lang.Boolean getOrientationHorizontal(){
      return _orientationHorizontal;
    }

    
    public void setOrientationHorizontal(java.lang.Boolean value){
        checkAllowChange();
        
        this._orientationHorizontal = value;
           
    }

    
    /**
     * 
     * xml name: paperSize
     *  
     */
    
    public java.lang.Integer getPaperSize(){
      return _paperSize;
    }

    
    public void setPaperSize(java.lang.Integer value){
        checkAllowChange();
        
        this._paperSize = value;
           
    }

    
    /**
     * 
     * xml name: scale
     *  放大百分比
     */
    
    public java.lang.Integer getScale(){
      return _scale;
    }

    
    public void setScale(java.lang.Integer value){
        checkAllowChange();
        
        this._scale = value;
           
    }

    
    /**
     * 
     * xml name: verticalCentered
     *  
     */
    
    public java.lang.Boolean getVerticalCentered(){
      return _verticalCentered;
    }

    
    public void setVerticalCentered(java.lang.Boolean value){
        checkAllowChange();
        
        this._verticalCentered = value;
           
    }

    

    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._footer = io.nop.api.core.util.FreezeHelper.deepFreeze(this._footer);
            
           this._header = io.nop.api.core.util.FreezeHelper.deepFreeze(this._header);
            
        }
    }

    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("blackAndWhite",this.getBlackAndWhite());
        out.put("firstPageNumber",this.getFirstPageNumber());
        out.put("fitToHeight",this.getFitToHeight());
        out.put("fitToWidth",this.getFitToWidth());
        out.put("footer",this.getFooter());
        out.put("header",this.getHeader());
        out.put("horizontalCentered",this.getHorizontalCentered());
        out.put("orientationHorizontal",this.getOrientationHorizontal());
        out.put("paperSize",this.getPaperSize());
        out.put("scale",this.getScale());
        out.put("verticalCentered",this.getVerticalCentered());
    }
}
 // resume CPD analysis - CPD-ON
