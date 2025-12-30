package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartScatterConfigModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * Scatter chart specific settings
 * 对应 Excel POI 中 ScatterChart 的特殊属性
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartScatterConfigModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: markerSize
     * 
     */
    private java.lang.Double _markerSize ;
    
    /**
     *  
     * xml name: markerSymbol
     * 
     */
    private java.lang.String _markerSymbol ;
    
    /**
     *  
     * xml name: scatterStyle
     * 散点图样式：none、line、lineMarker、marker、smooth、smoothMarker
     */
    private java.lang.String _scatterStyle ;
    
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
     * xml name: showMarkers
     * 是否显示标记点：true/false
     */
    private java.lang.Boolean _showMarkers ;
    
    /**
     *  
     * xml name: varyColors
     * 
     */
    private java.lang.Boolean _varyColors ;
    
    /**
     * 
     * xml name: markerSize
     *  
     */
    
    public java.lang.Double getMarkerSize(){
      return _markerSize;
    }

    
    public void setMarkerSize(java.lang.Double value){
        checkAllowChange();
        
        this._markerSize = value;
           
    }

    
    /**
     * 
     * xml name: markerSymbol
     *  
     */
    
    public java.lang.String getMarkerSymbol(){
      return _markerSymbol;
    }

    
    public void setMarkerSymbol(java.lang.String value){
        checkAllowChange();
        
        this._markerSymbol = value;
           
    }

    
    /**
     * 
     * xml name: scatterStyle
     *  散点图样式：none、line、lineMarker、marker、smooth、smoothMarker
     */
    
    public java.lang.String getScatterStyle(){
      return _scatterStyle;
    }

    
    public void setScatterStyle(java.lang.String value){
        checkAllowChange();
        
        this._scatterStyle = value;
           
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
     * xml name: showMarkers
     *  是否显示标记点：true/false
     */
    
    public java.lang.Boolean getShowMarkers(){
      return _showMarkers;
    }

    
    public void setShowMarkers(java.lang.Boolean value){
        checkAllowChange();
        
        this._showMarkers = value;
           
    }

    
    /**
     * 
     * xml name: varyColors
     *  
     */
    
    public java.lang.Boolean getVaryColors(){
      return _varyColors;
    }

    
    public void setVaryColors(java.lang.Boolean value){
        checkAllowChange();
        
        this._varyColors = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._shapeStyle = io.nop.api.core.util.FreezeHelper.deepFreeze(this._shapeStyle);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("markerSize",this.getMarkerSize());
        out.putNotNull("markerSymbol",this.getMarkerSymbol());
        out.putNotNull("scatterStyle",this.getScatterStyle());
        out.putNotNull("shapeStyle",this.getShapeStyle());
        out.putNotNull("showMarkers",this.getShowMarkers());
        out.putNotNull("varyColors",this.getVaryColors());
    }

    public ChartScatterConfigModel cloneInstance(){
        ChartScatterConfigModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartScatterConfigModel instance){
        super.copyTo(instance);
        
        instance.setMarkerSize(this.getMarkerSize());
        instance.setMarkerSymbol(this.getMarkerSymbol());
        instance.setScatterStyle(this.getScatterStyle());
        instance.setShapeStyle(this.getShapeStyle());
        instance.setShowMarkers(this.getShowMarkers());
        instance.setVaryColors(this.getVaryColors());
    }

    protected ChartScatterConfigModel newInstance(){
        return (ChartScatterConfigModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
