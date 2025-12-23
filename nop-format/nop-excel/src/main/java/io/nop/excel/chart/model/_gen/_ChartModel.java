package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * Enhanced Chart Metamodel Definition
 * Supports comprehensive chart configuration for Apache POI, PDF, and ECharts output formats
 * 参考 Apache POI XSSFChart 和 Excel Chart API 设计
 * 对应 Excel 中的 Chart 对象和相关属性
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: autoFit
     * 
     */
    private java.lang.Boolean _autoFit  = true;
    
    /**
     *  
     * xml name: axes
     * Chart axes configuration
     * 对应 Excel POI 中的 ChartAxis 集合，包括 CategoryAxis 和 ValueAxis
     */
    private KeyedList<io.nop.excel.chart.model.ChartAxisModel> _axes = KeyedList.emptyList();
    
    /**
     *  
     * xml name: description
     * Chart description for documentation
     * 对应 Excel 中图表的描述信息
     */
    private java.lang.String _description ;
    
    /**
     *  
     * xml name: heatmapConfig
     * Heatmap chart specific settings
     */
    private io.nop.excel.chart.model.ChartHeatmapConfigModel _heatmapConfig ;
    
    /**
     *  
     * xml name: height
     * 
     */
    private java.lang.Double _height ;
    
    /**
     *  
     * xml name: id
     * 
     */
    private java.lang.String _id ;
    
    /**
     *  
     * xml name: interactions
     * Interactive features configuration
     * 主要用于 Web 图表（ECharts），Excel 图表部分支持
     */
    private io.nop.excel.chart.model.ChartInteractionsModel _interactions ;
    
    /**
     *  
     * xml name: legend
     * Chart legend configuration
     * 对应 Excel POI 中的 ChartLegend
     */
    private io.nop.excel.chart.model.ChartLegendModel _legend ;
    
    /**
     *  
     * xml name: margin
     * 
     */
    private io.nop.excel.chart.model.ChartMarginModel _margin ;
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: padding
     * Chart layout and spacing
     * 对应 Excel POI 中的 ChartSpace 和 PlotArea 的边距设置
     */
    private io.nop.excel.chart.model.ChartPaddingModel _padding ;
    
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
     * xml name: series
     * Data series collection
     * 对应 Excel POI 中的 XSSFChart.getChartSeries() 集合
     * 每个 series 对应一个 ChartSeries 对象
     */
    private KeyedList<io.nop.excel.chart.model.ChartSeriesModel> _series = KeyedList.emptyList();
    
    /**
     *  
     * xml name: style
     * Global chart styling and theming
     * 对应 Excel 图表的整体样式主题
     */
    private io.nop.excel.chart.model.ChartStyleModel _style ;
    
    /**
     *  
     * xml name: title
     * Chart title configuration
     * 对应 Excel POI 中的 XSSFChart.getTitle() 和 ChartTitle
     */
    private io.nop.excel.chart.model.ChartTitleModel _title ;
    
    /**
     *  
     * xml name: type
     * 
     */
    private io.nop.excel.chart.constants.ChartType _type ;
    
    /**
     *  
     * xml name: width
     * 
     */
    private java.lang.Double _width ;
    
    /**
     * 
     * xml name: autoFit
     *  
     */
    
    public java.lang.Boolean getAutoFit(){
      return _autoFit;
    }

    
    public void setAutoFit(java.lang.Boolean value){
        checkAllowChange();
        
        this._autoFit = value;
           
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
     * xml name: description
     *  Chart description for documentation
     * 对应 Excel 中图表的描述信息
     */
    
    public java.lang.String getDescription(){
      return _description;
    }

    
    public void setDescription(java.lang.String value){
        checkAllowChange();
        
        this._description = value;
           
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
     * xml name: height
     *  
     */
    
    public java.lang.Double getHeight(){
      return _height;
    }

    
    public void setHeight(java.lang.Double value){
        checkAllowChange();
        
        this._height = value;
           
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
     * xml name: interactions
     *  Interactive features configuration
     * 主要用于 Web 图表（ECharts），Excel 图表部分支持
     */
    
    public io.nop.excel.chart.model.ChartInteractionsModel getInteractions(){
      return _interactions;
    }

    
    public void setInteractions(io.nop.excel.chart.model.ChartInteractionsModel value){
        checkAllowChange();
        
        this._interactions = value;
           
    }

    
    /**
     * 
     * xml name: legend
     *  Chart legend configuration
     * 对应 Excel POI 中的 ChartLegend
     */
    
    public io.nop.excel.chart.model.ChartLegendModel getLegend(){
      return _legend;
    }

    
    public void setLegend(io.nop.excel.chart.model.ChartLegendModel value){
        checkAllowChange();
        
        this._legend = value;
           
    }

    
    /**
     * 
     * xml name: margin
     *  
     */
    
    public io.nop.excel.chart.model.ChartMarginModel getMargin(){
      return _margin;
    }

    
    public void setMargin(io.nop.excel.chart.model.ChartMarginModel value){
        checkAllowChange();
        
        this._margin = value;
           
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
     * xml name: padding
     *  Chart layout and spacing
     * 对应 Excel POI 中的 ChartSpace 和 PlotArea 的边距设置
     */
    
    public io.nop.excel.chart.model.ChartPaddingModel getPadding(){
      return _padding;
    }

    
    public void setPadding(io.nop.excel.chart.model.ChartPaddingModel value){
        checkAllowChange();
        
        this._padding = value;
           
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
     * xml name: style
     *  Global chart styling and theming
     * 对应 Excel 图表的整体样式主题
     */
    
    public io.nop.excel.chart.model.ChartStyleModel getStyle(){
      return _style;
    }

    
    public void setStyle(io.nop.excel.chart.model.ChartStyleModel value){
        checkAllowChange();
        
        this._style = value;
           
    }

    
    /**
     * 
     * xml name: title
     *  Chart title configuration
     * 对应 Excel POI 中的 XSSFChart.getTitle() 和 ChartTitle
     */
    
    public io.nop.excel.chart.model.ChartTitleModel getTitle(){
      return _title;
    }

    
    public void setTitle(io.nop.excel.chart.model.ChartTitleModel value){
        checkAllowChange();
        
        this._title = value;
           
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
     * xml name: width
     *  
     */
    
    public java.lang.Double getWidth(){
      return _width;
    }

    
    public void setWidth(java.lang.Double value){
        checkAllowChange();
        
        this._width = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._axes = io.nop.api.core.util.FreezeHelper.deepFreeze(this._axes);
            
           this._heatmapConfig = io.nop.api.core.util.FreezeHelper.deepFreeze(this._heatmapConfig);
            
           this._interactions = io.nop.api.core.util.FreezeHelper.deepFreeze(this._interactions);
            
           this._legend = io.nop.api.core.util.FreezeHelper.deepFreeze(this._legend);
            
           this._margin = io.nop.api.core.util.FreezeHelper.deepFreeze(this._margin);
            
           this._padding = io.nop.api.core.util.FreezeHelper.deepFreeze(this._padding);
            
           this._pieConfig = io.nop.api.core.util.FreezeHelper.deepFreeze(this._pieConfig);
            
           this._radarConfig = io.nop.api.core.util.FreezeHelper.deepFreeze(this._radarConfig);
            
           this._series = io.nop.api.core.util.FreezeHelper.deepFreeze(this._series);
            
           this._style = io.nop.api.core.util.FreezeHelper.deepFreeze(this._style);
            
           this._title = io.nop.api.core.util.FreezeHelper.deepFreeze(this._title);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("autoFit",this.getAutoFit());
        out.putNotNull("axes",this.getAxes());
        out.putNotNull("description",this.getDescription());
        out.putNotNull("heatmapConfig",this.getHeatmapConfig());
        out.putNotNull("height",this.getHeight());
        out.putNotNull("id",this.getId());
        out.putNotNull("interactions",this.getInteractions());
        out.putNotNull("legend",this.getLegend());
        out.putNotNull("margin",this.getMargin());
        out.putNotNull("name",this.getName());
        out.putNotNull("padding",this.getPadding());
        out.putNotNull("pieConfig",this.getPieConfig());
        out.putNotNull("radarConfig",this.getRadarConfig());
        out.putNotNull("series",this.getSeries());
        out.putNotNull("style",this.getStyle());
        out.putNotNull("title",this.getTitle());
        out.putNotNull("type",this.getType());
        out.putNotNull("width",this.getWidth());
    }

    public ChartModel cloneInstance(){
        ChartModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartModel instance){
        super.copyTo(instance);
        
        instance.setAutoFit(this.getAutoFit());
        instance.setAxes(this.getAxes());
        instance.setDescription(this.getDescription());
        instance.setHeatmapConfig(this.getHeatmapConfig());
        instance.setHeight(this.getHeight());
        instance.setId(this.getId());
        instance.setInteractions(this.getInteractions());
        instance.setLegend(this.getLegend());
        instance.setMargin(this.getMargin());
        instance.setName(this.getName());
        instance.setPadding(this.getPadding());
        instance.setPieConfig(this.getPieConfig());
        instance.setRadarConfig(this.getRadarConfig());
        instance.setSeries(this.getSeries());
        instance.setStyle(this.getStyle());
        instance.setTitle(this.getTitle());
        instance.setType(this.getType());
        instance.setWidth(this.getWidth());
    }

    protected ChartModel newInstance(){
        return (ChartModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
