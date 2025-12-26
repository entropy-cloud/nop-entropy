package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartTextStyleModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * 文本样式（对应 CTTextBody）
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartTextStyleModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: font
     * 字体
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
     * xml name: textDirection
     * 
     */
    private io.nop.excel.chart.constants.ChartTextDirection _textDirection ;
    
    /**
     *  
     * xml name: textPadding
     * 文本框内边距（文本与形状边框的距离）
     */
    private io.nop.excel.chart.model.ChartSpacingModel _textPadding ;
    
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
     * xml name: font
     *  字体
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
     * xml name: textDirection
     *  
     */
    
    public io.nop.excel.chart.constants.ChartTextDirection getTextDirection(){
      return _textDirection;
    }

    
    public void setTextDirection(io.nop.excel.chart.constants.ChartTextDirection value){
        checkAllowChange();
        
        this._textDirection = value;
           
    }

    
    /**
     * 
     * xml name: textPadding
     *  文本框内边距（文本与形状边框的距离）
     */
    
    public io.nop.excel.chart.model.ChartSpacingModel getTextPadding(){
      return _textPadding;
    }

    
    public void setTextPadding(io.nop.excel.chart.model.ChartSpacingModel value){
        checkAllowChange();
        
        this._textPadding = value;
           
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

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._font = io.nop.api.core.util.FreezeHelper.deepFreeze(this._font);
            
           this._textPadding = io.nop.api.core.util.FreezeHelper.deepFreeze(this._textPadding);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("font",this.getFont());
        out.putNotNull("horizontalAlign",this.getHorizontalAlign());
        out.putNotNull("textDirection",this.getTextDirection());
        out.putNotNull("textPadding",this.getTextPadding());
        out.putNotNull("verticalAlign",this.getVerticalAlign());
        out.putNotNull("wrapText",this.isWrapText());
    }

    public ChartTextStyleModel cloneInstance(){
        ChartTextStyleModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartTextStyleModel instance){
        super.copyTo(instance);
        
        instance.setFont(this.getFont());
        instance.setHorizontalAlign(this.getHorizontalAlign());
        instance.setTextDirection(this.getTextDirection());
        instance.setTextPadding(this.getTextPadding());
        instance.setVerticalAlign(this.getVerticalAlign());
        instance.setWrapText(this.isWrapText());
    }

    protected ChartTextStyleModel newInstance(){
        return (ChartTextStyleModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
