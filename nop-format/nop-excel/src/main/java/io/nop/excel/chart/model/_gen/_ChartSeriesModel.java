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
     * xml name: dataSource
     * Data source configuration
     * 对应 Excel POI 中的 ChartDataSource，支持多种数据来源
     */
    private io.nop.excel.chart.model.ChartDataSourceModel _dataSource ;
    
    /**
     *  
     * xml name: labels
     * Data labels configuration
     * 对应 Excel POI 中的 DataLabels
     */
    private io.nop.excel.chart.model.ChartLabelsModel _labels ;
    
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
     * xml name: style
     * Series visual styling
     * 对应 Excel POI 中的 ChartSeries 的样式属性
     */
    private io.nop.excel.chart.model.ChartSeriesStyleModel _style ;
    
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
    private java.lang.Boolean _visible  = true;
    
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
     * xml name: dataSource
     *  Data source configuration
     * 对应 Excel POI 中的 ChartDataSource，支持多种数据来源
     */
    
    public io.nop.excel.chart.model.ChartDataSourceModel getDataSource(){
      return _dataSource;
    }

    
    public void setDataSource(io.nop.excel.chart.model.ChartDataSourceModel value){
        checkAllowChange();
        
        this._dataSource = value;
           
    }

    
    /**
     * 
     * xml name: labels
     *  Data labels configuration
     * 对应 Excel POI 中的 DataLabels
     */
    
    public io.nop.excel.chart.model.ChartLabelsModel getLabels(){
      return _labels;
    }

    
    public void setLabels(io.nop.excel.chart.model.ChartLabelsModel value){
        checkAllowChange();
        
        this._labels = value;
           
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
     * xml name: style
     *  Series visual styling
     * 对应 Excel POI 中的 ChartSeries 的样式属性
     */
    
    public io.nop.excel.chart.model.ChartSeriesStyleModel getStyle(){
      return _style;
    }

    
    public void setStyle(io.nop.excel.chart.model.ChartSeriesStyleModel value){
        checkAllowChange();
        
        this._style = value;
           
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
        
           this._animation = io.nop.api.core.util.FreezeHelper.deepFreeze(this._animation);
            
           this._dataSource = io.nop.api.core.util.FreezeHelper.deepFreeze(this._dataSource);
            
           this._labels = io.nop.api.core.util.FreezeHelper.deepFreeze(this._labels);
            
           this._markers = io.nop.api.core.util.FreezeHelper.deepFreeze(this._markers);
            
           this._style = io.nop.api.core.util.FreezeHelper.deepFreeze(this._style);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("animation",this.getAnimation());
        out.putNotNull("dataSource",this.getDataSource());
        out.putNotNull("labels",this.getLabels());
        out.putNotNull("markers",this.getMarkers());
        out.putNotNull("name",this.getName());
        out.putNotNull("style",this.getStyle());
        out.putNotNull("type",this.getType());
        out.putNotNull("visible",this.getVisible());
    }

    public ChartSeriesModel cloneInstance(){
        ChartSeriesModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartSeriesModel instance){
        super.copyTo(instance);
        
        instance.setAnimation(this.getAnimation());
        instance.setDataSource(this.getDataSource());
        instance.setLabels(this.getLabels());
        instance.setMarkers(this.getMarkers());
        instance.setName(this.getName());
        instance.setStyle(this.getStyle());
        instance.setType(this.getType());
        instance.setVisible(this.getVisible());
    }

    protected ChartSeriesModel newInstance(){
        return (ChartSeriesModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
