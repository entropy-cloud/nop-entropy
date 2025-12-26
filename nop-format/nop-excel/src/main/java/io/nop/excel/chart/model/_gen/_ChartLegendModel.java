package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartLegendModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * Chart legend configuration
 * 对应 Excel POI 中的 ChartLegend
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartLegendModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: align
     * 
     */
    private io.nop.excel.model.constants.ExcelHorizontalAlignment _align ;
    
    /**
     *  
     * xml name: items
     * Legend items configuration
     * 对应 Legend 中各项的显示设置
     */
    private io.nop.excel.chart.model.ChartLegendItemsModel _items ;
    
    /**
     *  
     * xml name: manualLayout
     * 控制plotArea的位置和大小。缺省情况下title和legend自动布局。
     * x/y/w/h → 左/上/宽/高，数值为百分比。
     */
    private io.nop.excel.chart.model.ChartManualLayoutModel _manualLayout ;
    
    /**
     *  
     * xml name: orientation
     * 
     */
    private io.nop.excel.chart.constants.ChartOrientation _orientation ;
    
    /**
     *  
     * xml name: overlay
     * 
     */
    private java.lang.Boolean _overlay  = false;
    
    /**
     *  
     * xml name: paging
     * Legend pagination for large datasets
     */
    private io.nop.excel.chart.model.ChartLegendPagingModel _paging ;
    
    /**
     *  
     * xml name: position
     * 
     */
    private io.nop.excel.chart.constants.ChartLegendPosition _position ;
    
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
     * xml name: textStyle
     * 文本样式（对应 CTTextBody）
     */
    private io.nop.excel.chart.model.ChartTextStyleModel _textStyle ;
    
    /**
     *  
     * xml name: verticalAlign
     * 
     */
    private io.nop.excel.model.constants.ExcelVerticalAlignment _verticalAlign ;
    
    /**
     *  
     * xml name: visible
     * 
     */
    private java.lang.Boolean _visible  = true;
    
    /**
     * 
     * xml name: align
     *  
     */
    
    public io.nop.excel.model.constants.ExcelHorizontalAlignment getAlign(){
      return _align;
    }

    
    public void setAlign(io.nop.excel.model.constants.ExcelHorizontalAlignment value){
        checkAllowChange();
        
        this._align = value;
           
    }

    
    /**
     * 
     * xml name: items
     *  Legend items configuration
     * 对应 Legend 中各项的显示设置
     */
    
    public io.nop.excel.chart.model.ChartLegendItemsModel getItems(){
      return _items;
    }

    
    public void setItems(io.nop.excel.chart.model.ChartLegendItemsModel value){
        checkAllowChange();
        
        this._items = value;
           
    }

    
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
     * xml name: orientation
     *  
     */
    
    public io.nop.excel.chart.constants.ChartOrientation getOrientation(){
      return _orientation;
    }

    
    public void setOrientation(io.nop.excel.chart.constants.ChartOrientation value){
        checkAllowChange();
        
        this._orientation = value;
           
    }

    
    /**
     * 
     * xml name: overlay
     *  
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
     * xml name: paging
     *  Legend pagination for large datasets
     */
    
    public io.nop.excel.chart.model.ChartLegendPagingModel getPaging(){
      return _paging;
    }

    
    public void setPaging(io.nop.excel.chart.model.ChartLegendPagingModel value){
        checkAllowChange();
        
        this._paging = value;
           
    }

    
    /**
     * 
     * xml name: position
     *  
     */
    
    public io.nop.excel.chart.constants.ChartLegendPosition getPosition(){
      return _position;
    }

    
    public void setPosition(io.nop.excel.chart.constants.ChartLegendPosition value){
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
     * xml name: visible
     *  
     */
    
    public java.lang.Boolean getVisible(){
      return _visible;
    }

    
    public void setVisible(java.lang.Boolean value){
        checkAllowChange();
        
        this._visible = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._items = io.nop.api.core.util.FreezeHelper.deepFreeze(this._items);
            
           this._manualLayout = io.nop.api.core.util.FreezeHelper.deepFreeze(this._manualLayout);
            
           this._paging = io.nop.api.core.util.FreezeHelper.deepFreeze(this._paging);
            
           this._shapeStyle = io.nop.api.core.util.FreezeHelper.deepFreeze(this._shapeStyle);
            
           this._textStyle = io.nop.api.core.util.FreezeHelper.deepFreeze(this._textStyle);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("align",this.getAlign());
        out.putNotNull("items",this.getItems());
        out.putNotNull("manualLayout",this.getManualLayout());
        out.putNotNull("orientation",this.getOrientation());
        out.putNotNull("overlay",this.getOverlay());
        out.putNotNull("paging",this.getPaging());
        out.putNotNull("position",this.getPosition());
        out.putNotNull("shapeStyle",this.getShapeStyle());
        out.putNotNull("textStyle",this.getTextStyle());
        out.putNotNull("verticalAlign",this.getVerticalAlign());
        out.putNotNull("visible",this.getVisible());
    }

    public ChartLegendModel cloneInstance(){
        ChartLegendModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartLegendModel instance){
        super.copyTo(instance);
        
        instance.setAlign(this.getAlign());
        instance.setItems(this.getItems());
        instance.setManualLayout(this.getManualLayout());
        instance.setOrientation(this.getOrientation());
        instance.setOverlay(this.getOverlay());
        instance.setPaging(this.getPaging());
        instance.setPosition(this.getPosition());
        instance.setShapeStyle(this.getShapeStyle());
        instance.setTextStyle(this.getTextStyle());
        instance.setVerticalAlign(this.getVerticalAlign());
        instance.setVisible(this.getVisible());
    }

    protected ChartLegendModel newInstance(){
        return (ChartLegendModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
