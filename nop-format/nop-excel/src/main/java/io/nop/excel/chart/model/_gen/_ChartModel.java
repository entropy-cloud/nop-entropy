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
     * xml name: dynamicBindings
     * 
     */
    private io.nop.excel.chart.model.ChartDynamicBindingsModel _dynamicBindings ;
    
    /**
     *  
     * xml name: height
     * 
     */
    private java.lang.Double _height ;
    
    /**
     *  
     * xml name: interactions
     * Interactive features configuration
     * 主要用于 Web 图表（ECharts），Excel 图表部分支持
     */
    private io.nop.excel.chart.model.ChartInteractionsModel _interactions ;
    
    /**
     *  
     * xml name: lang
     * 
     */
    private java.lang.String _lang ;
    
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
     * xml name: plotArea
     * 
     */
    private io.nop.excel.chart.model.ChartPlotAreaModel _plotArea ;
    
    /**
     *  
     * xml name: plotVisOnly
     * 
     */
    private java.lang.Boolean _plotVisOnly ;
    
    /**
     *  
     * xml name: roundedCorners
     * 
     */
    private java.lang.Boolean _roundedCorners ;
    
    /**
     *  
     * xml name: showLabelsOverMax
     * 
     */
    private java.lang.Boolean _showLabelsOverMax ;
    
    /**
     *  
     * xml name: style
     * Global chart styling and theming
     * 对应 Excel 图表的整体样式主题
     */
    private io.nop.excel.chart.model.ChartStyleModel _style ;
    
    /**
     *  
     * xml name: styleId
     * 
     */
    private java.lang.String _styleId ;
    
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
     * xml name: dynamicBindings
     *  
     */
    
    public io.nop.excel.chart.model.ChartDynamicBindingsModel getDynamicBindings(){
      return _dynamicBindings;
    }

    
    public void setDynamicBindings(io.nop.excel.chart.model.ChartDynamicBindingsModel value){
        checkAllowChange();
        
        this._dynamicBindings = value;
           
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
     * xml name: lang
     *  
     */
    
    public java.lang.String getLang(){
      return _lang;
    }

    
    public void setLang(java.lang.String value){
        checkAllowChange();
        
        this._lang = value;
           
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
     * xml name: plotVisOnly
     *  
     */
    
    public java.lang.Boolean getPlotVisOnly(){
      return _plotVisOnly;
    }

    
    public void setPlotVisOnly(java.lang.Boolean value){
        checkAllowChange();
        
        this._plotVisOnly = value;
           
    }

    
    /**
     * 
     * xml name: roundedCorners
     *  
     */
    
    public java.lang.Boolean getRoundedCorners(){
      return _roundedCorners;
    }

    
    public void setRoundedCorners(java.lang.Boolean value){
        checkAllowChange();
        
        this._roundedCorners = value;
           
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
     * xml name: styleId
     *  
     */
    
    public java.lang.String getStyleId(){
      return _styleId;
    }

    
    public void setStyleId(java.lang.String value){
        checkAllowChange();
        
        this._styleId = value;
           
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
        
           this._dataLabels = io.nop.api.core.util.FreezeHelper.deepFreeze(this._dataLabels);
            
           this._dynamicBindings = io.nop.api.core.util.FreezeHelper.deepFreeze(this._dynamicBindings);
            
           this._interactions = io.nop.api.core.util.FreezeHelper.deepFreeze(this._interactions);
            
           this._legend = io.nop.api.core.util.FreezeHelper.deepFreeze(this._legend);
            
           this._manualLayout = io.nop.api.core.util.FreezeHelper.deepFreeze(this._manualLayout);
            
           this._plotArea = io.nop.api.core.util.FreezeHelper.deepFreeze(this._plotArea);
            
           this._style = io.nop.api.core.util.FreezeHelper.deepFreeze(this._style);
            
           this._title = io.nop.api.core.util.FreezeHelper.deepFreeze(this._title);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("autoFit",this.getAutoFit());
        out.putNotNull("dataLabels",this.getDataLabels());
        out.putNotNull("description",this.getDescription());
        out.putNotNull("dispBlanksAs",this.getDispBlanksAs());
        out.putNotNull("dynamicBindings",this.getDynamicBindings());
        out.putNotNull("height",this.getHeight());
        out.putNotNull("interactions",this.getInteractions());
        out.putNotNull("lang",this.getLang());
        out.putNotNull("legend",this.getLegend());
        out.putNotNull("manualLayout",this.getManualLayout());
        out.putNotNull("name",this.getName());
        out.putNotNull("plotArea",this.getPlotArea());
        out.putNotNull("plotVisOnly",this.getPlotVisOnly());
        out.putNotNull("roundedCorners",this.getRoundedCorners());
        out.putNotNull("showLabelsOverMax",this.getShowLabelsOverMax());
        out.putNotNull("style",this.getStyle());
        out.putNotNull("styleId",this.getStyleId());
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
        instance.setDataLabels(this.getDataLabels());
        instance.setDescription(this.getDescription());
        instance.setDispBlanksAs(this.getDispBlanksAs());
        instance.setDynamicBindings(this.getDynamicBindings());
        instance.setHeight(this.getHeight());
        instance.setInteractions(this.getInteractions());
        instance.setLang(this.getLang());
        instance.setLegend(this.getLegend());
        instance.setManualLayout(this.getManualLayout());
        instance.setName(this.getName());
        instance.setPlotArea(this.getPlotArea());
        instance.setPlotVisOnly(this.getPlotVisOnly());
        instance.setRoundedCorners(this.getRoundedCorners());
        instance.setShowLabelsOverMax(this.getShowLabelsOverMax());
        instance.setStyle(this.getStyle());
        instance.setStyleId(this.getStyleId());
        instance.setTitle(this.getTitle());
        instance.setType(this.getType());
        instance.setWidth(this.getWidth());
    }

    protected ChartModel newInstance(){
        return (ChartModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
