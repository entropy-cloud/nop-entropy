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
 * 所有的单位统一使用pt(points)，而OOXML中一般是使用EMU
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
     * xml name: barConfig
     * 
     */
    private io.nop.excel.chart.model.ChartBarConfigModel _barConfig ;
    
    /**
     *  
     * xml name: dataLabels
     * Data labels configuration
     * 对应 Excel POI 中的 DataLabels
     */
    private io.nop.excel.chart.model.ChartDataLabelsModel _dataLabels ;
    
    /**
     *  
     * xml name: description
     * Chart description for documentation
     * 对应 Excel 中图表的描述信息
     */
    private java.lang.String _description ;
    
    /**
     *  
     * xml name: dispBlanksAs
     * 
     */
    private io.nop.excel.chart.constants.ChartDispBlankAs _dispBlanksAs ;
    
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
     * xml name: height
     * 
     */
    private java.lang.Double _height ;
    
    /**
     *  
     * xml name: hierarchicalConfig
     * 
     */
    private io.nop.excel.chart.model.ChartHierarchicalConfigModel _hierarchicalConfig ;
    
    /**
     *  
     * xml name: interactions
     * Interactive features configuration
     * 主要用于 Web 图表（ECharts），Excel 图表部分支持
     */
    private io.nop.excel.chart.model.ChartInteractionsModel _interactions ;
    
    /**
     *  
     * xml name: is3D
     * 
     */
    private java.lang.Boolean _is3D  = false;
    
    /**
     *  
     * xml name: is3D
     * 
     */
    private boolean _is3D  = false;
    
    /**
     *  
     * xml name: legend
     * Chart legend configuration
     * 对应 Excel POI 中的 ChartLegend
     */
    private io.nop.excel.chart.model.ChartLegendModel _legend ;
    
    /**
     *  
     * xml name: manualLayout
     * 控制plotArea的位置和大小。缺省情况下title和legend自动布局。
     * x/y/w/h → 左/上/宽/高，数值为百分比。
     */
    private io.nop.excel.chart.model.ChartManualLayoutModel _manualLayout ;
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: pieConfig
     * Pie chart specific settings
     * 对应 Excel POI 中 PieChart 的特殊属性
     */
    private io.nop.excel.chart.model.ChartPieConfigModel _pieConfig ;
    
    /**
     *  
     * xml name: plotArea
     * 
     */
    private io.nop.excel.chart.model.ChartPlotAreaModel _plotArea ;
    
    /**
     *  
     * xml name: radarConfig
     * Radar chart specific settings
     */
    private io.nop.excel.chart.model.ChartRadarConfigModel _radarConfig ;
    
    /**
     *  
     * xml name: showLabelsOverMax
     * 
     */
    private java.lang.Boolean _showLabelsOverMax ;
    
    /**
     *  
     * xml name: stockConfig
     * 需要扩展的配置
     */
    private io.nop.excel.chart.model.ChartStockConfigModel _stockConfig ;
    
    /**
     *  
     * xml name: style
     * Global chart styling and theming
     * 对应 Excel 图表的整体样式主题
     */
    private io.nop.excel.chart.model.ChartStyleModel _style ;
    
    /**
     *  
     * xml name: surfaceConfig
     * 成交量使用次要坐标轴
     */
    private io.nop.excel.chart.model.ChartSurfaceConfigModel _surfaceConfig ;
    
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
     * xml name: varyColors
     * 
     */
    private boolean _varyColors  = true;
    
    /**
     *  
     * xml name: waterfallConfig
     * 
     */
    private io.nop.excel.chart.model.ChartWaterfallConfigModel _waterfallConfig ;
    
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
     * xml name: dispBlanksAs
     *  
     */
    
    public io.nop.excel.chart.constants.ChartDispBlankAs getDispBlanksAs(){
      return _dispBlanksAs;
    }

    
    public void setDispBlanksAs(io.nop.excel.chart.constants.ChartDispBlankAs value){
        checkAllowChange();
        
        this._dispBlanksAs = value;
           
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
     * xml name: is3D
     *  
     */
    
    public java.lang.Boolean getIs3D(){
      return _is3D;
    }

    
    public void setIs3D(java.lang.Boolean value){
        checkAllowChange();
        
        this._is3D = value;
           
    }

    
    /**
     * 
     * xml name: is3D
     *  
     */
    
    public boolean isIs3D(){
      return _is3D;
    }

    
    public void setIs3D(boolean value){
        checkAllowChange();
        
        this._is3D = value;
           
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
     * xml name: plotArea
     *  
     */
    
    public io.nop.excel.chart.model.ChartPlotAreaModel getPlotArea(){
      return _plotArea;
    }

    
    public void setPlotArea(io.nop.excel.chart.model.ChartPlotAreaModel value){
        checkAllowChange();
        
        this._plotArea = value;
           
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
     * xml name: showLabelsOverMax
     *  
     */
    
    public java.lang.Boolean getShowLabelsOverMax(){
      return _showLabelsOverMax;
    }

    
    public void setShowLabelsOverMax(java.lang.Boolean value){
        checkAllowChange();
        
        this._showLabelsOverMax = value;
           
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
     * xml name: varyColors
     *  
     */
    
    public boolean isVaryColors(){
      return _varyColors;
    }

    
    public void setVaryColors(boolean value){
        checkAllowChange();
        
        this._varyColors = value;
           
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
        
           this._barConfig = io.nop.api.core.util.FreezeHelper.deepFreeze(this._barConfig);
            
           this._dataLabels = io.nop.api.core.util.FreezeHelper.deepFreeze(this._dataLabels);
            
           this._filters = io.nop.api.core.util.FreezeHelper.deepFreeze(this._filters);
            
           this._funnelConfig = io.nop.api.core.util.FreezeHelper.deepFreeze(this._funnelConfig);
            
           this._heatmapConfig = io.nop.api.core.util.FreezeHelper.deepFreeze(this._heatmapConfig);
            
           this._hierarchicalConfig = io.nop.api.core.util.FreezeHelper.deepFreeze(this._hierarchicalConfig);
            
           this._interactions = io.nop.api.core.util.FreezeHelper.deepFreeze(this._interactions);
            
           this._legend = io.nop.api.core.util.FreezeHelper.deepFreeze(this._legend);
            
           this._manualLayout = io.nop.api.core.util.FreezeHelper.deepFreeze(this._manualLayout);
            
           this._pieConfig = io.nop.api.core.util.FreezeHelper.deepFreeze(this._pieConfig);
            
           this._plotArea = io.nop.api.core.util.FreezeHelper.deepFreeze(this._plotArea);
            
           this._radarConfig = io.nop.api.core.util.FreezeHelper.deepFreeze(this._radarConfig);
            
           this._stockConfig = io.nop.api.core.util.FreezeHelper.deepFreeze(this._stockConfig);
            
           this._style = io.nop.api.core.util.FreezeHelper.deepFreeze(this._style);
            
           this._surfaceConfig = io.nop.api.core.util.FreezeHelper.deepFreeze(this._surfaceConfig);
            
           this._title = io.nop.api.core.util.FreezeHelper.deepFreeze(this._title);
            
           this._waterfallConfig = io.nop.api.core.util.FreezeHelper.deepFreeze(this._waterfallConfig);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("autoFit",this.getAutoFit());
        out.putNotNull("barConfig",this.getBarConfig());
        out.putNotNull("dataLabels",this.getDataLabels());
        out.putNotNull("description",this.getDescription());
        out.putNotNull("dispBlanksAs",this.getDispBlanksAs());
        out.putNotNull("filters",this.getFilters());
        out.putNotNull("funnelConfig",this.getFunnelConfig());
        out.putNotNull("heatmapConfig",this.getHeatmapConfig());
        out.putNotNull("height",this.getHeight());
        out.putNotNull("hierarchicalConfig",this.getHierarchicalConfig());
        out.putNotNull("interactions",this.getInteractions());
        out.putNotNull("is3D",this.getIs3D());
        out.putNotNull("legend",this.getLegend());
        out.putNotNull("manualLayout",this.getManualLayout());
        out.putNotNull("name",this.getName());
        out.putNotNull("pieConfig",this.getPieConfig());
        out.putNotNull("plotArea",this.getPlotArea());
        out.putNotNull("radarConfig",this.getRadarConfig());
        out.putNotNull("showLabelsOverMax",this.getShowLabelsOverMax());
        out.putNotNull("stockConfig",this.getStockConfig());
        out.putNotNull("style",this.getStyle());
        out.putNotNull("surfaceConfig",this.getSurfaceConfig());
        out.putNotNull("title",this.getTitle());
        out.putNotNull("type",this.getType());
        out.putNotNull("varyColors",this.isVaryColors());
        out.putNotNull("waterfallConfig",this.getWaterfallConfig());
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
        instance.setBarConfig(this.getBarConfig());
        instance.setDataLabels(this.getDataLabels());
        instance.setDescription(this.getDescription());
        instance.setDispBlanksAs(this.getDispBlanksAs());
        instance.setFilters(this.getFilters());
        instance.setFunnelConfig(this.getFunnelConfig());
        instance.setHeatmapConfig(this.getHeatmapConfig());
        instance.setHeight(this.getHeight());
        instance.setHierarchicalConfig(this.getHierarchicalConfig());
        instance.setInteractions(this.getInteractions());
        instance.setIs3D(this.getIs3D());
        instance.setLegend(this.getLegend());
        instance.setManualLayout(this.getManualLayout());
        instance.setName(this.getName());
        instance.setPieConfig(this.getPieConfig());
        instance.setPlotArea(this.getPlotArea());
        instance.setRadarConfig(this.getRadarConfig());
        instance.setShowLabelsOverMax(this.getShowLabelsOverMax());
        instance.setStockConfig(this.getStockConfig());
        instance.setStyle(this.getStyle());
        instance.setSurfaceConfig(this.getSurfaceConfig());
        instance.setTitle(this.getTitle());
        instance.setType(this.getType());
        instance.setVaryColors(this.isVaryColors());
        instance.setWaterfallConfig(this.getWaterfallConfig());
        instance.setWidth(this.getWidth());
    }

    protected ChartModel newInstance(){
        return (ChartModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
