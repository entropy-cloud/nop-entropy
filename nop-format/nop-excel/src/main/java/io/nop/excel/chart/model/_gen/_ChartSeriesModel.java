package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartSeriesModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartSeriesModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: animation
     * Animation settings for series
     * 对应图表动画效果配置
     */
    private io.nop.excel.chart.model.ChartAnimationModel _animation ;
    
    /**
     *  
     * xml name: axisId
     * 关联的坐标轴ID，默认使用主坐标轴。
     */
    private java.lang.String _axisId ;
    
    /**
     *  
     * xml name: catCellRef
     * 
     */
    private java.lang.String _catCellRef ;
    
    /**
     *  
     * xml name: dataCellRef
     * 
     */
    private java.lang.String _dataCellRef ;
    
    /**
     *  
     * xml name: dataLabels
     * Data labels configuration
     * 对应 Excel POI 中的 DataLabels
     */
    private io.nop.excel.chart.model.ChartDataLabelsModel _dataLabels ;
    
    /**
     *  
     * xml name: id
     * 
     */
    private java.lang.String _id ;
    
    /**
     *  
     * xml name: invertIfNegative
     * 当系列中的数据点为负值时，使用反转颜色显示（通常是前景色和背景色交换）。
     */
    private java.lang.Boolean _invertIfNegative  = false;
    
    /**
     *  
     * xml name: lineStyle
     * Line/border styling
     * 对应 Excel POI 中的 LineProperties
     */
    private io.nop.excel.chart.model.ChartLineStyleModel _lineStyle ;
    
    /**
     *  
     * xml name: markers
     * Data point markers
     * 对应 Excel POI 中线图和散点图的 Marker 设置
     */
    private io.nop.excel.chart.model.ChartMarkersModel _markers ;
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: nameCellRef
     * 
     */
    private java.lang.String _nameCellRef ;
    
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
     * xml name: trendLines
     * Trend Line Configuration for Chart Series
     * 图表系列趋势线配置 - 简化版，仅包含与 ECharts 双向转换支持的部分
     * 与 ECharts 的 markLine 和 regression 功能对应
     * 参考 ECharts 配置项：series.markLine, series.regression
     */
    private KeyedList<io.nop.excel.chart.model.ChartTrendLineModel> _trendLines = KeyedList.emptyList();
    
    /**
     *  
     * xml name: type
     * 
     */
    private io.nop.excel.chart.constants.ChartType _type ;
    
    /**
     *  
     * xml name: visible
     * 
     */
    private boolean _visible  = true;
    
    /**
     * 
     * xml name: animation
     *  Animation settings for series
     * 对应图表动画效果配置
     */
    
    public io.nop.excel.chart.model.ChartAnimationModel getAnimation(){
      return _animation;
    }

    
    public void setAnimation(io.nop.excel.chart.model.ChartAnimationModel value){
        checkAllowChange();
        
        this._animation = value;
           
    }

    
    /**
     * 
     * xml name: axisId
     *  关联的坐标轴ID，默认使用主坐标轴。
     */
    
    public java.lang.String getAxisId(){
      return _axisId;
    }

    
    public void setAxisId(java.lang.String value){
        checkAllowChange();
        
        this._axisId = value;
           
    }

    
    /**
     * 
     * xml name: catCellRef
     *  
     */
    
    public java.lang.String getCatCellRef(){
      return _catCellRef;
    }

    
    public void setCatCellRef(java.lang.String value){
        checkAllowChange();
        
        this._catCellRef = value;
           
    }

    
    /**
     * 
     * xml name: dataCellRef
     *  
     */
    
    public java.lang.String getDataCellRef(){
      return _dataCellRef;
    }

    
    public void setDataCellRef(java.lang.String value){
        checkAllowChange();
        
        this._dataCellRef = value;
           
    }

    
    /**
     * 
     * xml name: dataLabels
     *  Data labels configuration
     * 对应 Excel POI 中的 DataLabels
     */
    
    public io.nop.excel.chart.model.ChartDataLabelsModel getDataLabels(){
      return _dataLabels;
    }

    
    public void setDataLabels(io.nop.excel.chart.model.ChartDataLabelsModel value){
        checkAllowChange();
        
        this._dataLabels = value;
           
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
     * xml name: invertIfNegative
     *  当系列中的数据点为负值时，使用反转颜色显示（通常是前景色和背景色交换）。
     */
    
    public java.lang.Boolean getInvertIfNegative(){
      return _invertIfNegative;
    }

    
    public void setInvertIfNegative(java.lang.Boolean value){
        checkAllowChange();
        
        this._invertIfNegative = value;
           
    }

    
    /**
     * 
     * xml name: lineStyle
     *  Line/border styling
     * 对应 Excel POI 中的 LineProperties
     */
    
    public io.nop.excel.chart.model.ChartLineStyleModel getLineStyle(){
      return _lineStyle;
    }

    
    public void setLineStyle(io.nop.excel.chart.model.ChartLineStyleModel value){
        checkAllowChange();
        
        this._lineStyle = value;
           
    }

    
    /**
     * 
     * xml name: markers
     *  Data point markers
     * 对应 Excel POI 中线图和散点图的 Marker 设置
     */
    
    public io.nop.excel.chart.model.ChartMarkersModel getMarkers(){
      return _markers;
    }

    
    public void setMarkers(io.nop.excel.chart.model.ChartMarkersModel value){
        checkAllowChange();
        
        this._markers = value;
           
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
     * xml name: nameCellRef
     *  
     */
    
    public java.lang.String getNameCellRef(){
      return _nameCellRef;
    }

    
    public void setNameCellRef(java.lang.String value){
        checkAllowChange();
        
        this._nameCellRef = value;
           
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
     * xml name: trendLines
     *  Trend Line Configuration for Chart Series
     * 图表系列趋势线配置 - 简化版，仅包含与 ECharts 双向转换支持的部分
     * 与 ECharts 的 markLine 和 regression 功能对应
     * 参考 ECharts 配置项：series.markLine, series.regression
     */
    
    public java.util.List<io.nop.excel.chart.model.ChartTrendLineModel> getTrendLines(){
      return _trendLines;
    }

    
    public void setTrendLines(java.util.List<io.nop.excel.chart.model.ChartTrendLineModel> value){
        checkAllowChange();
        
        this._trendLines = KeyedList.fromList(value, io.nop.excel.chart.model.ChartTrendLineModel::getId);
           
    }

    
    public io.nop.excel.chart.model.ChartTrendLineModel getTrendLine(String name){
        return this._trendLines.getByKey(name);
    }

    public boolean hasTrendLine(String name){
        return this._trendLines.containsKey(name);
    }

    public void addTrendLine(io.nop.excel.chart.model.ChartTrendLineModel item) {
        checkAllowChange();
        java.util.List<io.nop.excel.chart.model.ChartTrendLineModel> list = this.getTrendLines();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.excel.chart.model.ChartTrendLineModel::getId);
            setTrendLines(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_trendLines(){
        return this._trendLines.keySet();
    }

    public boolean hasTrendLines(){
        return !this._trendLines.isEmpty();
    }
    
    /**
     * 
     * xml name: type
     *  
     */
    
    public io.nop.excel.chart.constants.ChartType getType(){
      return _type;
    }

    
    public void setType(io.nop.excel.chart.constants.ChartType value){
        checkAllowChange();
        
        this._type = value;
           
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
        
           this._animation = io.nop.api.core.util.FreezeHelper.deepFreeze(this._animation);
            
           this._dataLabels = io.nop.api.core.util.FreezeHelper.deepFreeze(this._dataLabels);
            
           this._lineStyle = io.nop.api.core.util.FreezeHelper.deepFreeze(this._lineStyle);
            
           this._markers = io.nop.api.core.util.FreezeHelper.deepFreeze(this._markers);
            
           this._shapeStyle = io.nop.api.core.util.FreezeHelper.deepFreeze(this._shapeStyle);
            
           this._trendLines = io.nop.api.core.util.FreezeHelper.deepFreeze(this._trendLines);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("animation",this.getAnimation());
        out.putNotNull("axisId",this.getAxisId());
        out.putNotNull("catCellRef",this.getCatCellRef());
        out.putNotNull("dataCellRef",this.getDataCellRef());
        out.putNotNull("dataLabels",this.getDataLabels());
        out.putNotNull("id",this.getId());
        out.putNotNull("invertIfNegative",this.getInvertIfNegative());
        out.putNotNull("lineStyle",this.getLineStyle());
        out.putNotNull("markers",this.getMarkers());
        out.putNotNull("name",this.getName());
        out.putNotNull("nameCellRef",this.getNameCellRef());
        out.putNotNull("shapeStyle",this.getShapeStyle());
        out.putNotNull("trendLines",this.getTrendLines());
        out.putNotNull("type",this.getType());
        out.putNotNull("visible",this.isVisible());
    }

    public ChartSeriesModel cloneInstance(){
        ChartSeriesModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartSeriesModel instance){
        super.copyTo(instance);
        
        instance.setAnimation(this.getAnimation());
        instance.setAxisId(this.getAxisId());
        instance.setCatCellRef(this.getCatCellRef());
        instance.setDataCellRef(this.getDataCellRef());
        instance.setDataLabels(this.getDataLabels());
        instance.setId(this.getId());
        instance.setInvertIfNegative(this.getInvertIfNegative());
        instance.setLineStyle(this.getLineStyle());
        instance.setMarkers(this.getMarkers());
        instance.setName(this.getName());
        instance.setNameCellRef(this.getNameCellRef());
        instance.setShapeStyle(this.getShapeStyle());
        instance.setTrendLines(this.getTrendLines());
        instance.setType(this.getType());
        instance.setVisible(this.isVisible());
    }

    protected ChartSeriesModel newInstance(){
        return (ChartSeriesModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
