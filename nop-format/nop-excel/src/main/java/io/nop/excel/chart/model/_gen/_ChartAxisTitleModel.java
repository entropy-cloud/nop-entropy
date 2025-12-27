package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartAxisTitleModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * Axis title
 * 对应 Excel POI 中的 AxisTitle
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartAxisTitleModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: shapeStyle
     * Common shape style model based on POI CTShapeProperties
     * 通用形状样式模型，基于 POI 的 CTShapeProperties
     * 对应 Excel POI 中的 CTShapeProperties，用于 Legend、DataLabel、Tooltip 等元素的样式
     */
    private io.nop.excel.chart.model.ChartShapeStyleModel _shapeStyle ;
    
    /**
     *  
     * xml name: text
     * 
     */
    private java.lang.String _text ;
    
    /**
     *  
     * xml name: textCellRef
     * 
     */
    private java.lang.String _textCellRef ;
    
    /**
     *  
     * xml name: textStyle
     * 文本样式（对应 CTTextBody）
     */
    private io.nop.excel.chart.model.ChartTextStyleModel _textStyle ;
    
    /**
     *  
     * xml name: visible
     * 
     */
    private boolean _visible  = true;
    
    /**
     * 
     * xml name: shapeStyle
     *  Common shape style model based on POI CTShapeProperties
     * 通用形状样式模型，基于 POI 的 CTShapeProperties
     * 对应 Excel POI 中的 CTShapeProperties，用于 Legend、DataLabel、Tooltip 等元素的样式
     */
    
    public io.nop.excel.chart.model.ChartShapeStyleModel getShapeStyle(){
      return _shapeStyle;
    }

    
    public void setShapeStyle(io.nop.excel.chart.model.ChartShapeStyleModel value){
        checkAllowChange();
        
        this._shapeStyle = value;
           
    }

    
    /**
     * 
     * xml name: text
     *  
     */
    
    public java.lang.String getText(){
      return _text;
    }

    
    public void setText(java.lang.String value){
        checkAllowChange();
        
        this._text = value;
           
    }

    
    /**
     * 
     * xml name: textCellRef
     *  
     */
    
    public java.lang.String getTextCellRef(){
      return _textCellRef;
    }

    
    public void setTextCellRef(java.lang.String value){
        checkAllowChange();
        
        this._textCellRef = value;
           
    }

    
    /**
     * 
     * xml name: textStyle
     *  文本样式（对应 CTTextBody）
     */
    
    public io.nop.excel.chart.model.ChartTextStyleModel getTextStyle(){
      return _textStyle;
    }

    
    public void setTextStyle(io.nop.excel.chart.model.ChartTextStyleModel value){
        checkAllowChange();
        
        this._textStyle = value;
           
    }

    
    /**
     * 
     * xml name: visible
     *  
     */
    
    public boolean isVisible(){
      return _visible;
    }

    
    public void setVisible(boolean value){
        checkAllowChange();
        
        this._visible = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._shapeStyle = io.nop.api.core.util.FreezeHelper.deepFreeze(this._shapeStyle);
            
           this._textStyle = io.nop.api.core.util.FreezeHelper.deepFreeze(this._textStyle);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("shapeStyle",this.getShapeStyle());
        out.putNotNull("text",this.getText());
        out.putNotNull("textCellRef",this.getTextCellRef());
        out.putNotNull("textStyle",this.getTextStyle());
        out.putNotNull("visible",this.isVisible());
    }

    public ChartAxisTitleModel cloneInstance(){
        ChartAxisTitleModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartAxisTitleModel instance){
        super.copyTo(instance);
        
        instance.setShapeStyle(this.getShapeStyle());
        instance.setText(this.getText());
        instance.setTextCellRef(this.getTextCellRef());
        instance.setTextStyle(this.getTextStyle());
        instance.setVisible(this.isVisible());
    }

    protected ChartAxisTitleModel newInstance(){
        return (ChartAxisTitleModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
