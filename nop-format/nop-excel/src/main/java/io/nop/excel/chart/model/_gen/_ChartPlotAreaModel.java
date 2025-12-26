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
     * xml name: axes
     * Chart axes configuration
     * 对应 Excel POI 中的 ChartAxis 集合，包括 CategoryAxis 和 ValueAxis
     */
    private KeyedList<io.nop.excel.chart.model.ChartAxisModel> _axes = KeyedList.emptyList();
    
    /**
     *  
     * xml name: manualLayout
     * 控制plotArea的位置和大小。缺省情况下title和legend自动布局。
     * x/y/w/h → 左/上/宽/高，数值为百分比。
     */
    private io.nop.excel.chart.model.ChartManualLayoutModel _manualLayout ;
    
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

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._axes = io.nop.api.core.util.FreezeHelper.deepFreeze(this._axes);
            
           this._manualLayout = io.nop.api.core.util.FreezeHelper.deepFreeze(this._manualLayout);
            
           this._series = io.nop.api.core.util.FreezeHelper.deepFreeze(this._series);
            
           this._shapeStyle = io.nop.api.core.util.FreezeHelper.deepFreeze(this._shapeStyle);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("axes",this.getAxes());
        out.putNotNull("manualLayout",this.getManualLayout());
        out.putNotNull("series",this.getSeries());
        out.putNotNull("shapeStyle",this.getShapeStyle());
    }

    public ChartPlotAreaModel cloneInstance(){
        ChartPlotAreaModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartPlotAreaModel instance){
        super.copyTo(instance);
        
        instance.setAxes(this.getAxes());
        instance.setManualLayout(this.getManualLayout());
        instance.setSeries(this.getSeries());
        instance.setShapeStyle(this.getShapeStyle());
    }

    protected ChartPlotAreaModel newInstance(){
        return (ChartPlotAreaModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
