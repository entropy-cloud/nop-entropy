package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartPlotAreaModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartPlotAreaModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: areaConfig
     * Area chart specific settings
     * 对应 Excel POI 中 AreaChart 的特殊属性
     */
    private io.nop.excel.chart.model.ChartAreaConfigModel _areaConfig ;
    
    /**
     *  
     * xml name: axes
     * Chart axes configuration
     * 对应 Excel POI 中的 ChartAxis 集合，包括 CategoryAxis 和 ValueAxis
     */
    private KeyedList<io.nop.excel.chart.model.ChartAxisModel> _axes = KeyedList.emptyList();
    
    /**
     *  
     * xml name: barConfig
     * 
     */
    private io.nop.excel.chart.model.ChartBarConfigModel _barConfig ;
    
    /**
     *  
     * xml name: bubbleConfig
     * Bubble chart specific settings
     * 对应 Excel POI 中 BubbleChart 的特殊属性
     */
    private io.nop.excel.chart.model.ChartBubbleConfigModel _bubbleConfig ;
    
    /**
     *  
     * xml name: filters
     * 
     */
    private io.nop.excel.chart.model.ChartFiltersModel _filters ;
    
    /**
     *  
     * xml name: funnelConfig
     * 
     */
    private io.nop.excel.chart.model.ChartFunnelConfigModel _funnelConfig ;
    
    /**
     *  
     * xml name: heatmapConfig
     * Heatmap chart specific settings
     */
    private io.nop.excel.chart.model.ChartHeatmapConfigModel _heatmapConfig ;
    
    /**
     *  
     * xml name: hierarchicalConfig
     * 
     */
    private io.nop.excel.chart.model.ChartHierarchicalConfigModel _hierarchicalConfig ;
    
    /**
     *  
     * xml name: lineConfig
     * Line chart specific settings
     * 对应 Excel POI 中 LineChart 的特殊属性
     */
    private io.nop.excel.chart.model.ChartLineConfigModel _lineConfig ;
    
    /**
     *  
     * xml name: manualLayout
     * 控制plotArea的位置和大小。缺省情况下title和legend自动布局。
     * x/y/w/h → 左/上/宽/高，数值为百分比。
     */
    private io.nop.excel.chart.model.ChartManualLayoutModel _manualLayout ;
    
    /**
     *  
     * xml name: pieConfig
     * Pie chart specific settings
     * 对应 Excel POI 中 PieChart 的特殊属性
     */
    private io.nop.excel.chart.model.ChartPieConfigModel _pieConfig ;
    
    /**
     *  
     * xml name: radarConfig
     * Radar chart specific settings
     */
    private io.nop.excel.chart.model.ChartRadarConfigModel _radarConfig ;
    
    /**
     *  
     * xml name: scatterConfig
     * Scatter chart specific settings
     * 对应 Excel POI 中 ScatterChart 的特殊属性
     */
    private io.nop.excel.chart.model.ChartScatterConfigModel _scatterConfig ;
    
    /**
     *  
     * xml name: series
     * Data series collection
     * 对应 Excel POI 中的 XSSFChart.getChartSeries() 集合
     * 每个 series 对应一个 ChartSeries 对象
     */
    private KeyedList<io.nop.excel.chart.model.ChartSeriesModel> _series = KeyedList.emptyList();
    
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
     * xml name: stockConfig
     * 需要扩展的配置
     */
    private io.nop.excel.chart.model.ChartStockConfigModel _stockConfig ;
    
    /**
     *  
     * xml name: surfaceConfig
     * 成交量使用次要坐标轴
     */
    private io.nop.excel.chart.model.ChartSurfaceConfigModel _surfaceConfig ;
    
    /**
     *  
     * xml name: waterfallConfig
     * 
     */
    private io.nop.excel.chart.model.ChartWaterfallConfigModel _waterfallConfig ;
    
    /**
     * 
     * xml name: areaConfig
     *  Area chart specific settings
     * 对应 Excel POI 中 AreaChart 的特殊属性
     */
    
    public io.nop.excel.chart.model.ChartAreaConfigModel getAreaConfig(){
      return _areaConfig;
    }

    
    public void setAreaConfig(io.nop.excel.chart.model.ChartAreaConfigModel value){
        checkAllowChange();
        
        this._areaConfig = value;
           
    }

    
    /**
     * 
     * xml name: axes
     *  Chart axes configuration
     * 对应 Excel POI 中的 ChartAxis 集合，包括 CategoryAxis 和 ValueAxis
     */
    
    public java.util.List<io.nop.excel.chart.model.ChartAxisModel> getAxes(){
      return _axes;
    }

    
    public void setAxes(java.util.List<io.nop.excel.chart.model.ChartAxisModel> value){
        checkAllowChange();
        
        this._axes = KeyedList.fromList(value, io.nop.excel.chart.model.ChartAxisModel::getId);
           
    }

    
    public io.nop.excel.chart.model.ChartAxisModel getAxis(String name){
        return this._axes.getByKey(name);
    }

    public boolean hasAxis(String name){
        return this._axes.containsKey(name);
    }

    public void addAxis(io.nop.excel.chart.model.ChartAxisModel item) {
        checkAllowChange();
        java.util.List<io.nop.excel.chart.model.ChartAxisModel> list = this.getAxes();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.excel.chart.model.ChartAxisModel::getId);
            setAxes(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_axes(){
        return this._axes.keySet();
    }

    public boolean hasAxes(){
        return !this._axes.isEmpty();
    }
    
    /**
     * 
     * xml name: barConfig
     *  
     */
    
    public io.nop.excel.chart.model.ChartBarConfigModel getBarConfig(){
      return _barConfig;
    }

    
    public void setBarConfig(io.nop.excel.chart.model.ChartBarConfigModel value){
        checkAllowChange();
        
        this._barConfig = value;
           
    }

    
    /**
     * 
     * xml name: bubbleConfig
     *  Bubble chart specific settings
     * 对应 Excel POI 中 BubbleChart 的特殊属性
     */
    
    public io.nop.excel.chart.model.ChartBubbleConfigModel getBubbleConfig(){
      return _bubbleConfig;
    }

    
    public void setBubbleConfig(io.nop.excel.chart.model.ChartBubbleConfigModel value){
        checkAllowChange();
        
        this._bubbleConfig = value;
           
    }

    
    /**
     * 
     * xml name: filters
     *  
     */
    
    public io.nop.excel.chart.model.ChartFiltersModel getFilters(){
      return _filters;
    }

    
    public void setFilters(io.nop.excel.chart.model.ChartFiltersModel value){
        checkAllowChange();
        
        this._filters = value;
           
    }

    
    /**
     * 
     * xml name: funnelConfig
     *  
     */
    
    public io.nop.excel.chart.model.ChartFunnelConfigModel getFunnelConfig(){
      return _funnelConfig;
    }

    
    public void setFunnelConfig(io.nop.excel.chart.model.ChartFunnelConfigModel value){
        checkAllowChange();
        
        this._funnelConfig = value;
           
    }

    
    /**
     * 
     * xml name: heatmapConfig
     *  Heatmap chart specific settings
     */
    
    public io.nop.excel.chart.model.ChartHeatmapConfigModel getHeatmapConfig(){
      return _heatmapConfig;
    }

    
    public void setHeatmapConfig(io.nop.excel.chart.model.ChartHeatmapConfigModel value){
        checkAllowChange();
        
        this._heatmapConfig = value;
           
    }

    
    /**
     * 
     * xml name: hierarchicalConfig
     *  
     */
    
    public io.nop.excel.chart.model.ChartHierarchicalConfigModel getHierarchicalConfig(){
      return _hierarchicalConfig;
    }

    
    public void setHierarchicalConfig(io.nop.excel.chart.model.ChartHierarchicalConfigModel value){
        checkAllowChange();
        
        this._hierarchicalConfig = value;
           
    }

    
    /**
     * 
     * xml name: lineConfig
     *  Line chart specific settings
     * 对应 Excel POI 中 LineChart 的特殊属性
     */
    
    public io.nop.excel.chart.model.ChartLineConfigModel getLineConfig(){
      return _lineConfig;
    }

    
    public void setLineConfig(io.nop.excel.chart.model.ChartLineConfigModel value){
        checkAllowChange();
        
        this._lineConfig = value;
           
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
     * xml name: pieConfig
     *  Pie chart specific settings
     * 对应 Excel POI 中 PieChart 的特殊属性
     */
    
    public io.nop.excel.chart.model.ChartPieConfigModel getPieConfig(){
      return _pieConfig;
    }

    
    public void setPieConfig(io.nop.excel.chart.model.ChartPieConfigModel value){
        checkAllowChange();
        
        this._pieConfig = value;
           
    }

    
    /**
     * 
     * xml name: radarConfig
     *  Radar chart specific settings
     */
    
    public io.nop.excel.chart.model.ChartRadarConfigModel getRadarConfig(){
      return _radarConfig;
    }

    
    public void setRadarConfig(io.nop.excel.chart.model.ChartRadarConfigModel value){
        checkAllowChange();
        
        this._radarConfig = value;
           
    }

    
    /**
     * 
     * xml name: scatterConfig
     *  Scatter chart specific settings
     * 对应 Excel POI 中 ScatterChart 的特殊属性
     */
    
    public io.nop.excel.chart.model.ChartScatterConfigModel getScatterConfig(){
      return _scatterConfig;
    }

    
    public void setScatterConfig(io.nop.excel.chart.model.ChartScatterConfigModel value){
        checkAllowChange();
        
        this._scatterConfig = value;
           
    }

    
    /**
     * 
     * xml name: series
     *  Data series collection
     * 对应 Excel POI 中的 XSSFChart.getChartSeries() 集合
     * 每个 series 对应一个 ChartSeries 对象
     */
    
    public java.util.List<io.nop.excel.chart.model.ChartSeriesModel> getSeries(){
      return _series;
    }

    
    public void setSeries(java.util.List<io.nop.excel.chart.model.ChartSeriesModel> value){
        checkAllowChange();
        
        this._series = KeyedList.fromList(value, io.nop.excel.chart.model.ChartSeriesModel::getName);
           
    }

    
    public io.nop.excel.chart.model.ChartSeriesModel getSeries(String name){
        return this._series.getByKey(name);
    }

    public boolean hasSeries(String name){
        return this._series.containsKey(name);
    }

    public void addSeries(io.nop.excel.chart.model.ChartSeriesModel item) {
        checkAllowChange();
        java.util.List<io.nop.excel.chart.model.ChartSeriesModel> list = this.getSeries();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.excel.chart.model.ChartSeriesModel::getName);
            setSeries(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_series(){
        return this._series.keySet();
    }

    public boolean hasSeries(){
        return !this._series.isEmpty();
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
     * xml name: stockConfig
     *  需要扩展的配置
     */
    
    public io.nop.excel.chart.model.ChartStockConfigModel getStockConfig(){
      return _stockConfig;
    }

    
    public void setStockConfig(io.nop.excel.chart.model.ChartStockConfigModel value){
        checkAllowChange();
        
        this._stockConfig = value;
           
    }

    
    /**
     * 
     * xml name: surfaceConfig
     *  成交量使用次要坐标轴
     */
    
    public io.nop.excel.chart.model.ChartSurfaceConfigModel getSurfaceConfig(){
      return _surfaceConfig;
    }

    
    public void setSurfaceConfig(io.nop.excel.chart.model.ChartSurfaceConfigModel value){
        checkAllowChange();
        
        this._surfaceConfig = value;
           
    }

    
    /**
     * 
     * xml name: waterfallConfig
     *  
     */
    
    public io.nop.excel.chart.model.ChartWaterfallConfigModel getWaterfallConfig(){
      return _waterfallConfig;
    }

    
    public void setWaterfallConfig(io.nop.excel.chart.model.ChartWaterfallConfigModel value){
        checkAllowChange();
        
        this._waterfallConfig = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._areaConfig = io.nop.api.core.util.FreezeHelper.deepFreeze(this._areaConfig);
            
           this._axes = io.nop.api.core.util.FreezeHelper.deepFreeze(this._axes);
            
           this._barConfig = io.nop.api.core.util.FreezeHelper.deepFreeze(this._barConfig);
            
           this._bubbleConfig = io.nop.api.core.util.FreezeHelper.deepFreeze(this._bubbleConfig);
            
           this._filters = io.nop.api.core.util.FreezeHelper.deepFreeze(this._filters);
            
           this._funnelConfig = io.nop.api.core.util.FreezeHelper.deepFreeze(this._funnelConfig);
            
           this._heatmapConfig = io.nop.api.core.util.FreezeHelper.deepFreeze(this._heatmapConfig);
            
           this._hierarchicalConfig = io.nop.api.core.util.FreezeHelper.deepFreeze(this._hierarchicalConfig);
            
           this._lineConfig = io.nop.api.core.util.FreezeHelper.deepFreeze(this._lineConfig);
            
           this._manualLayout = io.nop.api.core.util.FreezeHelper.deepFreeze(this._manualLayout);
            
           this._pieConfig = io.nop.api.core.util.FreezeHelper.deepFreeze(this._pieConfig);
            
           this._radarConfig = io.nop.api.core.util.FreezeHelper.deepFreeze(this._radarConfig);
            
           this._scatterConfig = io.nop.api.core.util.FreezeHelper.deepFreeze(this._scatterConfig);
            
           this._series = io.nop.api.core.util.FreezeHelper.deepFreeze(this._series);
            
           this._shapeStyle = io.nop.api.core.util.FreezeHelper.deepFreeze(this._shapeStyle);
            
           this._stockConfig = io.nop.api.core.util.FreezeHelper.deepFreeze(this._stockConfig);
            
           this._surfaceConfig = io.nop.api.core.util.FreezeHelper.deepFreeze(this._surfaceConfig);
            
           this._waterfallConfig = io.nop.api.core.util.FreezeHelper.deepFreeze(this._waterfallConfig);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("areaConfig",this.getAreaConfig());
        out.putNotNull("axes",this.getAxes());
        out.putNotNull("barConfig",this.getBarConfig());
        out.putNotNull("bubbleConfig",this.getBubbleConfig());
        out.putNotNull("filters",this.getFilters());
        out.putNotNull("funnelConfig",this.getFunnelConfig());
        out.putNotNull("heatmapConfig",this.getHeatmapConfig());
        out.putNotNull("hierarchicalConfig",this.getHierarchicalConfig());
        out.putNotNull("lineConfig",this.getLineConfig());
        out.putNotNull("manualLayout",this.getManualLayout());
        out.putNotNull("pieConfig",this.getPieConfig());
        out.putNotNull("radarConfig",this.getRadarConfig());
        out.putNotNull("scatterConfig",this.getScatterConfig());
        out.putNotNull("series",this.getSeries());
        out.putNotNull("shapeStyle",this.getShapeStyle());
        out.putNotNull("stockConfig",this.getStockConfig());
        out.putNotNull("surfaceConfig",this.getSurfaceConfig());
        out.putNotNull("waterfallConfig",this.getWaterfallConfig());
    }

    public ChartPlotAreaModel cloneInstance(){
        ChartPlotAreaModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartPlotAreaModel instance){
        super.copyTo(instance);
        
        instance.setAreaConfig(this.getAreaConfig());
        instance.setAxes(this.getAxes());
        instance.setBarConfig(this.getBarConfig());
        instance.setBubbleConfig(this.getBubbleConfig());
        instance.setFilters(this.getFilters());
        instance.setFunnelConfig(this.getFunnelConfig());
        instance.setHeatmapConfig(this.getHeatmapConfig());
        instance.setHierarchicalConfig(this.getHierarchicalConfig());
        instance.setLineConfig(this.getLineConfig());
        instance.setManualLayout(this.getManualLayout());
        instance.setPieConfig(this.getPieConfig());
        instance.setRadarConfig(this.getRadarConfig());
        instance.setScatterConfig(this.getScatterConfig());
        instance.setSeries(this.getSeries());
        instance.setShapeStyle(this.getShapeStyle());
        instance.setStockConfig(this.getStockConfig());
        instance.setSurfaceConfig(this.getSurfaceConfig());
        instance.setWaterfallConfig(this.getWaterfallConfig());
    }

    protected ChartPlotAreaModel newInstance(){
        return (ChartPlotAreaModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
