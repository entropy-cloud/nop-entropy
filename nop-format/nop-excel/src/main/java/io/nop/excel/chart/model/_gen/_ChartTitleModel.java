package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartTitleModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * Chart title configuration
 * 对应 Excel POI 中的 XSSFChart.getTitle() 和 ChartTitle
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartTitleModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: manualLayout
     * 控制plotArea的位置和大小。缺省情况下title和legend自动布局。
     * x/y/w/h → 左/上/宽/高，数值为百分比。
     */
    private io.nop.excel.chart.model.ChartManualLayoutModel _manualLayout ;
    
    /**
     *  
     * xml name: overlay
     * 缺省为false，表示元素（标题、图例等）会独占一块区域，绘图区会自动向内收缩，不与它重叠。
     * true则表示元素将悬浮在绘图区上方，二者可以重叠，绘图区大小保持不变。
     */
    private java.lang.Boolean _overlay  = false;
    
    /**
     *  
     * xml name: position
     * 
     */
    private io.nop.excel.chart.constants.ChartTitlePosition _position ;
    
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
     * xml name: manualLayout
     *  控制plotArea的位置和大小。缺省情况下title和legend自动布局。
     * x/y/w/h → 左/上/宽/高，数值为百分比。
     */
    
    public io.nop.excel.chart.model.ChartManualLayoutModel getManualLayout(){
      return _manualLayout;
    }

    
    public void setManualLayout(io.nop.excel.chart.model.ChartManualLayoutModel value){
        checkAllowChange();
        
        this._manualLayout = value;
           
    }

    
    /**
     * 
     * xml name: overlay
     *  缺省为false，表示元素（标题、图例等）会独占一块区域，绘图区会自动向内收缩，不与它重叠。
     * true则表示元素将悬浮在绘图区上方，二者可以重叠，绘图区大小保持不变。
     */
    
    public java.lang.Boolean getOverlay(){
      return _overlay;
    }

    
    public void setOverlay(java.lang.Boolean value){
        checkAllowChange();
        
        this._overlay = value;
           
    }

    
    /**
     * 
     * xml name: position
     *  
     */
    
    public io.nop.excel.chart.constants.ChartTitlePosition getPosition(){
      return _position;
    }

    
    public void setPosition(io.nop.excel.chart.constants.ChartTitlePosition value){
        checkAllowChange();
        
        this._position = value;
           
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
        
           this._manualLayout = io.nop.api.core.util.FreezeHelper.deepFreeze(this._manualLayout);
            
           this._shapeStyle = io.nop.api.core.util.FreezeHelper.deepFreeze(this._shapeStyle);
            
           this._textStyle = io.nop.api.core.util.FreezeHelper.deepFreeze(this._textStyle);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("manualLayout",this.getManualLayout());
        out.putNotNull("overlay",this.getOverlay());
        out.putNotNull("position",this.getPosition());
        out.putNotNull("shapeStyle",this.getShapeStyle());
        out.putNotNull("text",this.getText());
        out.putNotNull("textCellRef",this.getTextCellRef());
        out.putNotNull("textStyle",this.getTextStyle());
        out.putNotNull("visible",this.isVisible());
    }

    public ChartTitleModel cloneInstance(){
        ChartTitleModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartTitleModel instance){
        super.copyTo(instance);
        
        instance.setManualLayout(this.getManualLayout());
        instance.setOverlay(this.getOverlay());
        instance.setPosition(this.getPosition());
        instance.setShapeStyle(this.getShapeStyle());
        instance.setText(this.getText());
        instance.setTextCellRef(this.getTextCellRef());
        instance.setTextStyle(this.getTextStyle());
        instance.setVisible(this.isVisible());
    }

    protected ChartTitleModel newInstance(){
        return (ChartTitleModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
