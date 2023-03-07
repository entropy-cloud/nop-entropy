package io.nop.excel.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [19:10:0:0]/nop/schema/excel/workbook.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _ExcelStyle extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: bottomBorder
     * 
     */
    private io.nop.excel.model.ExcelBorderStyle _bottomBorder ;
    
    /**
     *  
     * xml name: diagonalLeftBorder
     * 
     */
    private io.nop.excel.model.ExcelBorderStyle _diagonalLeftBorder ;
    
    /**
     *  
     * xml name: diagonalRightBorder
     * 
     */
    private io.nop.excel.model.ExcelBorderStyle _diagonalRightBorder ;
    
    /**
     *  
     * xml name: fillBgColor
     * 
     */
    private java.lang.String _fillBgColor ;
    
    /**
     *  
     * xml name: fillFgColor
     * 
     */
    private java.lang.String _fillFgColor ;
    
    /**
     *  
     * xml name: fillPattern
     * 
     */
    private java.lang.String _fillPattern ;
    
    /**
     *  
     * xml name: font
     * 
     */
    private io.nop.excel.model.ExcelFont _font ;
    
    /**
     *  
     * xml name: horizontalAlign
     * 
     */
    private io.nop.excel.model.constants.ExcelHorizontalAlignment _horizontalAlign ;
    
    /**
     *  
     * xml name: id
     * 
     */
    private java.lang.String _id ;
    
    /**
     *  
     * xml name: indent
     * 
     */
    private java.lang.Integer _indent ;
    
    /**
     *  
     * xml name: leftBorder
     * 
     */
    private io.nop.excel.model.ExcelBorderStyle _leftBorder ;
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: numberFormat
     * 
     */
    private java.lang.String _numberFormat ;
    
    /**
     *  
     * xml name: rightBorder
     * 
     */
    private io.nop.excel.model.ExcelBorderStyle _rightBorder ;
    
    /**
     *  
     * xml name: rotate
     * 旋转角度，从1到360
     */
    private java.lang.Integer _rotate ;
    
    /**
     *  
     * xml name: shrinkToFit
     * 
     */
    private boolean _shrinkToFit  = false;
    
    /**
     *  
     * xml name: topBorder
     * 
     */
    private io.nop.excel.model.ExcelBorderStyle _topBorder ;
    
    /**
     *  
     * xml name: verticalAlign
     * 
     */
    private io.nop.excel.model.constants.ExcelVerticalAlignment _verticalAlign ;
    
    /**
     *  
     * xml name: wrapText
     * 
     */
    private boolean _wrapText  = false;
    
    /**
     * 
     * xml name: bottomBorder
     *  
     */
    
    public io.nop.excel.model.ExcelBorderStyle getBottomBorder(){
      return _bottomBorder;
    }

    
    public void setBottomBorder(io.nop.excel.model.ExcelBorderStyle value){
        checkAllowChange();
        
        this._bottomBorder = value;
           
    }

    
    /**
     * 
     * xml name: diagonalLeftBorder
     *  
     */
    
    public io.nop.excel.model.ExcelBorderStyle getDiagonalLeftBorder(){
      return _diagonalLeftBorder;
    }

    
    public void setDiagonalLeftBorder(io.nop.excel.model.ExcelBorderStyle value){
        checkAllowChange();
        
        this._diagonalLeftBorder = value;
           
    }

    
    /**
     * 
     * xml name: diagonalRightBorder
     *  
     */
    
    public io.nop.excel.model.ExcelBorderStyle getDiagonalRightBorder(){
      return _diagonalRightBorder;
    }

    
    public void setDiagonalRightBorder(io.nop.excel.model.ExcelBorderStyle value){
        checkAllowChange();
        
        this._diagonalRightBorder = value;
           
    }

    
    /**
     * 
     * xml name: fillBgColor
     *  
     */
    
    public java.lang.String getFillBgColor(){
      return _fillBgColor;
    }

    
    public void setFillBgColor(java.lang.String value){
        checkAllowChange();
        
        this._fillBgColor = value;
           
    }

    
    /**
     * 
     * xml name: fillFgColor
     *  
     */
    
    public java.lang.String getFillFgColor(){
      return _fillFgColor;
    }

    
    public void setFillFgColor(java.lang.String value){
        checkAllowChange();
        
        this._fillFgColor = value;
           
    }

    
    /**
     * 
     * xml name: fillPattern
     *  
     */
    
    public java.lang.String getFillPattern(){
      return _fillPattern;
    }

    
    public void setFillPattern(java.lang.String value){
        checkAllowChange();
        
        this._fillPattern = value;
           
    }

    
    /**
     * 
     * xml name: font
     *  
     */
    
    public io.nop.excel.model.ExcelFont getFont(){
      return _font;
    }

    
    public void setFont(io.nop.excel.model.ExcelFont value){
        checkAllowChange();
        
        this._font = value;
           
    }

    
    /**
     * 
     * xml name: horizontalAlign
     *  
     */
    
    public io.nop.excel.model.constants.ExcelHorizontalAlignment getHorizontalAlign(){
      return _horizontalAlign;
    }

    
    public void setHorizontalAlign(io.nop.excel.model.constants.ExcelHorizontalAlignment value){
        checkAllowChange();
        
        this._horizontalAlign = value;
           
    }

    
    /**
     * 
     * xml name: id
     *  
     */
    
    public java.lang.String getId(){
      return _id;
    }

    
    public void setId(java.lang.String value){
        checkAllowChange();
        
        this._id = value;
           
    }

    
    /**
     * 
     * xml name: indent
     *  
     */
    
    public java.lang.Integer getIndent(){
      return _indent;
    }

    
    public void setIndent(java.lang.Integer value){
        checkAllowChange();
        
        this._indent = value;
           
    }

    
    /**
     * 
     * xml name: leftBorder
     *  
     */
    
    public io.nop.excel.model.ExcelBorderStyle getLeftBorder(){
      return _leftBorder;
    }

    
    public void setLeftBorder(io.nop.excel.model.ExcelBorderStyle value){
        checkAllowChange();
        
        this._leftBorder = value;
           
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
     * xml name: numberFormat
     *  
     */
    
    public java.lang.String getNumberFormat(){
      return _numberFormat;
    }

    
    public void setNumberFormat(java.lang.String value){
        checkAllowChange();
        
        this._numberFormat = value;
           
    }

    
    /**
     * 
     * xml name: rightBorder
     *  
     */
    
    public io.nop.excel.model.ExcelBorderStyle getRightBorder(){
      return _rightBorder;
    }

    
    public void setRightBorder(io.nop.excel.model.ExcelBorderStyle value){
        checkAllowChange();
        
        this._rightBorder = value;
           
    }

    
    /**
     * 
     * xml name: rotate
     *  旋转角度，从1到360
     */
    
    public java.lang.Integer getRotate(){
      return _rotate;
    }

    
    public void setRotate(java.lang.Integer value){
        checkAllowChange();
        
        this._rotate = value;
           
    }

    
    /**
     * 
     * xml name: shrinkToFit
     *  
     */
    
    public boolean isShrinkToFit(){
      return _shrinkToFit;
    }

    
    public void setShrinkToFit(boolean value){
        checkAllowChange();
        
        this._shrinkToFit = value;
           
    }

    
    /**
     * 
     * xml name: topBorder
     *  
     */
    
    public io.nop.excel.model.ExcelBorderStyle getTopBorder(){
      return _topBorder;
    }

    
    public void setTopBorder(io.nop.excel.model.ExcelBorderStyle value){
        checkAllowChange();
        
        this._topBorder = value;
           
    }

    
    /**
     * 
     * xml name: verticalAlign
     *  
     */
    
    public io.nop.excel.model.constants.ExcelVerticalAlignment getVerticalAlign(){
      return _verticalAlign;
    }

    
    public void setVerticalAlign(io.nop.excel.model.constants.ExcelVerticalAlignment value){
        checkAllowChange();
        
        this._verticalAlign = value;
           
    }

    
    /**
     * 
     * xml name: wrapText
     *  
     */
    
    public boolean isWrapText(){
      return _wrapText;
    }

    
    public void setWrapText(boolean value){
        checkAllowChange();
        
        this._wrapText = value;
           
    }

    

    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._bottomBorder = io.nop.api.core.util.FreezeHelper.deepFreeze(this._bottomBorder);
            
           this._diagonalLeftBorder = io.nop.api.core.util.FreezeHelper.deepFreeze(this._diagonalLeftBorder);
            
           this._diagonalRightBorder = io.nop.api.core.util.FreezeHelper.deepFreeze(this._diagonalRightBorder);
            
           this._font = io.nop.api.core.util.FreezeHelper.deepFreeze(this._font);
            
           this._leftBorder = io.nop.api.core.util.FreezeHelper.deepFreeze(this._leftBorder);
            
           this._rightBorder = io.nop.api.core.util.FreezeHelper.deepFreeze(this._rightBorder);
            
           this._topBorder = io.nop.api.core.util.FreezeHelper.deepFreeze(this._topBorder);
            
        }
    }

    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("bottomBorder",this.getBottomBorder());
        out.put("diagonalLeftBorder",this.getDiagonalLeftBorder());
        out.put("diagonalRightBorder",this.getDiagonalRightBorder());
        out.put("fillBgColor",this.getFillBgColor());
        out.put("fillFgColor",this.getFillFgColor());
        out.put("fillPattern",this.getFillPattern());
        out.put("font",this.getFont());
        out.put("horizontalAlign",this.getHorizontalAlign());
        out.put("id",this.getId());
        out.put("indent",this.getIndent());
        out.put("leftBorder",this.getLeftBorder());
        out.put("name",this.getName());
        out.put("numberFormat",this.getNumberFormat());
        out.put("rightBorder",this.getRightBorder());
        out.put("rotate",this.getRotate());
        out.put("shrinkToFit",this.isShrinkToFit());
        out.put("topBorder",this.getTopBorder());
        out.put("verticalAlign",this.getVerticalAlign());
        out.put("wrapText",this.isWrapText());
    }
}
 // resume CPD analysis - CPD-ON
