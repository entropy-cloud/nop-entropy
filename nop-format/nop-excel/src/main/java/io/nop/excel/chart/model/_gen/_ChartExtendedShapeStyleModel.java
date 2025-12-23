package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartExtendedShapeStyleModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * Extended shape style model for elements with text color
 * 扩展形状样式模型，用于需要文本颜色的元素如 Tooltip
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartExtendedShapeStyleModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: backgroundColor
     * 
     */
    private java.lang.String _backgroundColor ;
    
    /**
     *  
     * xml name: borderColor
     * 
     */
    private java.lang.String _borderColor ;
    
    /**
     *  
     * xml name: borderWidth
     * 
     */
    private java.lang.Double _borderWidth ;
    
    /**
     *  
     * xml name: font
     * 
     */
    private io.nop.excel.model.ExcelFont _font ;
    
    /**
     *  
     * xml name: padding
     * 
     */
    private java.lang.Double _padding ;
    
    /**
     *  
     * xml name: textColor
     * 
     */
    private java.lang.String _textColor ;
    
    /**
     * 
     * xml name: backgroundColor
     *  
     */
    
    public java.lang.String getBackgroundColor(){
      return _backgroundColor;
    }

    
    public void setBackgroundColor(java.lang.String value){
        checkAllowChange();
        
        this._backgroundColor = value;
           
    }

    
    /**
     * 
     * xml name: borderColor
     *  
     */
    
    public java.lang.String getBorderColor(){
      return _borderColor;
    }

    
    public void setBorderColor(java.lang.String value){
        checkAllowChange();
        
        this._borderColor = value;
           
    }

    
    /**
     * 
     * xml name: borderWidth
     *  
     */
    
    public java.lang.Double getBorderWidth(){
      return _borderWidth;
    }

    
    public void setBorderWidth(java.lang.Double value){
        checkAllowChange();
        
        this._borderWidth = value;
           
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
     * xml name: padding
     *  
     */
    
    public java.lang.Double getPadding(){
      return _padding;
    }

    
    public void setPadding(java.lang.Double value){
        checkAllowChange();
        
        this._padding = value;
           
    }

    
    /**
     * 
     * xml name: textColor
     *  
     */
    
    public java.lang.String getTextColor(){
      return _textColor;
    }

    
    public void setTextColor(java.lang.String value){
        checkAllowChange();
        
        this._textColor = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._font = io.nop.api.core.util.FreezeHelper.deepFreeze(this._font);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("backgroundColor",this.getBackgroundColor());
        out.putNotNull("borderColor",this.getBorderColor());
        out.putNotNull("borderWidth",this.getBorderWidth());
        out.putNotNull("font",this.getFont());
        out.putNotNull("padding",this.getPadding());
        out.putNotNull("textColor",this.getTextColor());
    }

    public ChartExtendedShapeStyleModel cloneInstance(){
        ChartExtendedShapeStyleModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartExtendedShapeStyleModel instance){
        super.copyTo(instance);
        
        instance.setBackgroundColor(this.getBackgroundColor());
        instance.setBorderColor(this.getBorderColor());
        instance.setBorderWidth(this.getBorderWidth());
        instance.setFont(this.getFont());
        instance.setPadding(this.getPadding());
        instance.setTextColor(this.getTextColor());
    }

    protected ChartExtendedShapeStyleModel newInstance(){
        return (ChartExtendedShapeStyleModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
