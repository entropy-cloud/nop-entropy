package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartDataLabelsModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * Data labels configuration
 * 对应 Excel POI 中的 DataLabels
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartDataLabelsModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: numberFormat
     * 数字格式串，如"#,##0.00"、"0%"
     */
    private java.lang.String _numberFormat ;
    
    /**
     *  
     * xml name: offsetX
     * 
     */
    private java.lang.Double _offsetX ;
    
    /**
     *  
     * xml name: offsetY
     * 
     */
    private java.lang.Double _offsetY ;
    
    /**
     *  
     * xml name: position
     * 标签位置：center、insideEnd、outsideEnd、left、right、top、bottom
     */
    private io.nop.excel.chart.constants.ChartDataLabelPosition _position ;
    
    /**
     *  
     * xml name: separator
     * 多项内容之间的分隔符，如"\n"、", "
     */
    private java.lang.String _separator ;
    
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
     * xml name: showBubbleSize
     * 是否显示气泡大小
     */
    private java.lang.Boolean _showBubbleSize ;
    
    /**
     *  
     * xml name: showCatName
     * 是否显示类别名称
     */
    private java.lang.Boolean _showCatName ;
    
    /**
     *  
     * xml name: showLeaderLines
     * 是否自动画引导线
     */
    private java.lang.Boolean _showLeaderLines ;
    
    /**
     *  
     * xml name: showLegendKey
     * 是否显示图例色块
     */
    private java.lang.Boolean _showLegendKey ;
    
    /**
     *  
     * xml name: showPercent
     * 是否显示百分比
     */
    private java.lang.Boolean _showPercent ;
    
    /**
     *  
     * xml name: showSerName
     * 是否显示系列名称
     */
    private java.lang.Boolean _showSerName ;
    
    /**
     *  
     * xml name: showVal
     * 是否显示数值（默认true）
     */
    private java.lang.Boolean _showVal ;
    
    /**
     *  
     * xml name: textStyle
     * 文本样式（对应 CTTextBody）
     */
    private io.nop.excel.chart.model.ChartTextStyleModel _textStyle ;
    
    /**
     * 
     * xml name: numberFormat
     *  数字格式串，如"#,##0.00"、"0%"
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
     * xml name: offsetX
     *  
     */
    
    public java.lang.Double getOffsetX(){
      return _offsetX;
    }

    
    public void setOffsetX(java.lang.Double value){
        checkAllowChange();
        
        this._offsetX = value;
           
    }

    
    /**
     * 
     * xml name: offsetY
     *  
     */
    
    public java.lang.Double getOffsetY(){
      return _offsetY;
    }

    
    public void setOffsetY(java.lang.Double value){
        checkAllowChange();
        
        this._offsetY = value;
           
    }

    
    /**
     * 
     * xml name: position
     *  标签位置：center、insideEnd、outsideEnd、left、right、top、bottom
     */
    
    public io.nop.excel.chart.constants.ChartDataLabelPosition getPosition(){
      return _position;
    }

    
    public void setPosition(io.nop.excel.chart.constants.ChartDataLabelPosition value){
        checkAllowChange();
        
        this._position = value;
           
    }

    
    /**
     * 
     * xml name: separator
     *  多项内容之间的分隔符，如"\n"、", "
     */
    
    public java.lang.String getSeparator(){
      return _separator;
    }

    
    public void setSeparator(java.lang.String value){
        checkAllowChange();
        
        this._separator = value;
           
    }

    
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
     * xml name: showBubbleSize
     *  是否显示气泡大小
     */
    
    public java.lang.Boolean getShowBubbleSize(){
      return _showBubbleSize;
    }

    
    public void setShowBubbleSize(java.lang.Boolean value){
        checkAllowChange();
        
        this._showBubbleSize = value;
           
    }

    
    /**
     * 
     * xml name: showCatName
     *  是否显示类别名称
     */
    
    public java.lang.Boolean getShowCatName(){
      return _showCatName;
    }

    
    public void setShowCatName(java.lang.Boolean value){
        checkAllowChange();
        
        this._showCatName = value;
           
    }

    
    /**
     * 
     * xml name: showLeaderLines
     *  是否自动画引导线
     */
    
    public java.lang.Boolean getShowLeaderLines(){
      return _showLeaderLines;
    }

    
    public void setShowLeaderLines(java.lang.Boolean value){
        checkAllowChange();
        
        this._showLeaderLines = value;
           
    }

    
    /**
     * 
     * xml name: showLegendKey
     *  是否显示图例色块
     */
    
    public java.lang.Boolean getShowLegendKey(){
      return _showLegendKey;
    }

    
    public void setShowLegendKey(java.lang.Boolean value){
        checkAllowChange();
        
        this._showLegendKey = value;
           
    }

    
    /**
     * 
     * xml name: showPercent
     *  是否显示百分比
     */
    
    public java.lang.Boolean getShowPercent(){
      return _showPercent;
    }

    
    public void setShowPercent(java.lang.Boolean value){
        checkAllowChange();
        
        this._showPercent = value;
           
    }

    
    /**
     * 
     * xml name: showSerName
     *  是否显示系列名称
     */
    
    public java.lang.Boolean getShowSerName(){
      return _showSerName;
    }

    
    public void setShowSerName(java.lang.Boolean value){
        checkAllowChange();
        
        this._showSerName = value;
           
    }

    
    /**
     * 
     * xml name: showVal
     *  是否显示数值（默认true）
     */
    
    public java.lang.Boolean getShowVal(){
      return _showVal;
    }

    
    public void setShowVal(java.lang.Boolean value){
        checkAllowChange();
        
        this._showVal = value;
           
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
        
        out.putNotNull("numberFormat",this.getNumberFormat());
        out.putNotNull("offsetX",this.getOffsetX());
        out.putNotNull("offsetY",this.getOffsetY());
        out.putNotNull("position",this.getPosition());
        out.putNotNull("separator",this.getSeparator());
        out.putNotNull("shapeStyle",this.getShapeStyle());
        out.putNotNull("showBubbleSize",this.getShowBubbleSize());
        out.putNotNull("showCatName",this.getShowCatName());
        out.putNotNull("showLeaderLines",this.getShowLeaderLines());
        out.putNotNull("showLegendKey",this.getShowLegendKey());
        out.putNotNull("showPercent",this.getShowPercent());
        out.putNotNull("showSerName",this.getShowSerName());
        out.putNotNull("showVal",this.getShowVal());
        out.putNotNull("textStyle",this.getTextStyle());
    }

    public ChartDataLabelsModel cloneInstance(){
        ChartDataLabelsModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartDataLabelsModel instance){
        super.copyTo(instance);
        
        instance.setNumberFormat(this.getNumberFormat());
        instance.setOffsetX(this.getOffsetX());
        instance.setOffsetY(this.getOffsetY());
        instance.setPosition(this.getPosition());
        instance.setSeparator(this.getSeparator());
        instance.setShapeStyle(this.getShapeStyle());
        instance.setShowBubbleSize(this.getShowBubbleSize());
        instance.setShowCatName(this.getShowCatName());
        instance.setShowLeaderLines(this.getShowLeaderLines());
        instance.setShowLegendKey(this.getShowLegendKey());
        instance.setShowPercent(this.getShowPercent());
        instance.setShowSerName(this.getShowSerName());
        instance.setShowVal(this.getShowVal());
        instance.setTextStyle(this.getTextStyle());
    }

    protected ChartDataLabelsModel newInstance(){
        return (ChartDataLabelsModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
