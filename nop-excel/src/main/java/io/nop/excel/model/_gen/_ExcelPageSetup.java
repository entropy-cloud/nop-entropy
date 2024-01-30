package io.nop.excel.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.model.ExcelPageSetup;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [190:14:0:0]/nop/schema/excel/workbook.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
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

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._footer = io.nop.api.core.util.FreezeHelper.deepFreeze(this._footer);
            
           this._header = io.nop.api.core.util.FreezeHelper.deepFreeze(this._header);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("blackAndWhite",this.getBlackAndWhite());
        out.putNotNull("firstPageNumber",this.getFirstPageNumber());
        out.putNotNull("fitToHeight",this.getFitToHeight());
        out.putNotNull("fitToWidth",this.getFitToWidth());
        out.putNotNull("footer",this.getFooter());
        out.putNotNull("header",this.getHeader());
        out.putNotNull("horizontalCentered",this.getHorizontalCentered());
        out.putNotNull("orientationHorizontal",this.getOrientationHorizontal());
        out.putNotNull("paperSize",this.getPaperSize());
        out.putNotNull("scale",this.getScale());
        out.putNotNull("verticalCentered",this.getVerticalCentered());
    }

    public ExcelPageSetup cloneInstance(){
        ExcelPageSetup instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ExcelPageSetup instance){
        super.copyTo(instance);
        
        instance.setBlackAndWhite(this.getBlackAndWhite());
        instance.setFirstPageNumber(this.getFirstPageNumber());
        instance.setFitToHeight(this.getFitToHeight());
        instance.setFitToWidth(this.getFitToWidth());
        instance.setFooter(this.getFooter());
        instance.setHeader(this.getHeader());
        instance.setHorizontalCentered(this.getHorizontalCentered());
        instance.setOrientationHorizontal(this.getOrientationHorizontal());
        instance.setPaperSize(this.getPaperSize());
        instance.setScale(this.getScale());
        instance.setVerticalCentered(this.getVerticalCentered());
    }

    protected ExcelPageSetup newInstance(){
        return (ExcelPageSetup) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
