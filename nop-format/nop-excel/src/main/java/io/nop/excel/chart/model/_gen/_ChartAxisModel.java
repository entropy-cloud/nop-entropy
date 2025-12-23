package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartAxisModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartAxisModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: grid
     * Grid lines configuration
     * 对应 Excel POI 中的 ChartGridLines
     */
    private io.nop.excel.chart.model.ChartGridModel _grid ;
    
    /**
     *  
     * xml name: id
     * 
     */
    private java.lang.String _id ;
    
    /**
     *  
     * xml name: labels
     * Axis labels styling
     * 对应 Excel POI 中的 AxisTickLabels
     */
    private io.nop.excel.chart.model.ChartAxisLabelsModel _labels ;
    
    /**
     *  
     * xml name: line
     * Axis line styling
     * 对应 Excel POI 中的 AxisLine
     */
    private io.nop.excel.chart.model.ChartAxisLineModel _line ;
    
    /**
     *  
     * xml name: position
     * 
     */
    private io.nop.excel.chart.constants.ChartAxisPosition _position ;
    
    /**
     *  
     * xml name: scale
     * Axis scaling configuration
     * 对应 Excel POI 中的 ValueAxis.setMinimum(), setMaximum() 等
     */
    private io.nop.excel.chart.model.ChartAxisScaleModel _scale ;
    
    /**
     *  
     * xml name: ticks
     * Axis tick marks
     * 对应 Excel POI 中的 AxisTickMark
     */
    private io.nop.excel.chart.model.ChartTicksModel _ticks ;
    
    /**
     *  
     * xml name: title
     * Axis title
     * 对应 Excel POI 中的 AxisTitle
     */
    private io.nop.excel.chart.model.ChartAxisTitleModel _title ;
    
    /**
     *  
     * xml name: type
     * 
     */
    private io.nop.excel.chart.constants.ChartAxisType _type ;
    
    /**
     * 
     * xml name: grid
     *  Grid lines configuration
     * 对应 Excel POI 中的 ChartGridLines
     */
    
    public io.nop.excel.chart.model.ChartGridModel getGrid(){
      return _grid;
    }

    
    public void setGrid(io.nop.excel.chart.model.ChartGridModel value){
        checkAllowChange();
        
        this._grid = value;
           
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
     * xml name: labels
     *  Axis labels styling
     * 对应 Excel POI 中的 AxisTickLabels
     */
    
    public io.nop.excel.chart.model.ChartAxisLabelsModel getLabels(){
      return _labels;
    }

    
    public void setLabels(io.nop.excel.chart.model.ChartAxisLabelsModel value){
        checkAllowChange();
        
        this._labels = value;
           
    }

    
    /**
     * 
     * xml name: line
     *  Axis line styling
     * 对应 Excel POI 中的 AxisLine
     */
    
    public io.nop.excel.chart.model.ChartAxisLineModel getLine(){
      return _line;
    }

    
    public void setLine(io.nop.excel.chart.model.ChartAxisLineModel value){
        checkAllowChange();
        
        this._line = value;
           
    }

    
    /**
     * 
     * xml name: position
     *  
     */
    
    public io.nop.excel.chart.constants.ChartAxisPosition getPosition(){
      return _position;
    }

    
    public void setPosition(io.nop.excel.chart.constants.ChartAxisPosition value){
        checkAllowChange();
        
        this._position = value;
           
    }

    
    /**
     * 
     * xml name: scale
     *  Axis scaling configuration
     * 对应 Excel POI 中的 ValueAxis.setMinimum(), setMaximum() 等
     */
    
    public io.nop.excel.chart.model.ChartAxisScaleModel getScale(){
      return _scale;
    }

    
    public void setScale(io.nop.excel.chart.model.ChartAxisScaleModel value){
        checkAllowChange();
        
        this._scale = value;
           
    }

    
    /**
     * 
     * xml name: ticks
     *  Axis tick marks
     * 对应 Excel POI 中的 AxisTickMark
     */
    
    public io.nop.excel.chart.model.ChartTicksModel getTicks(){
      return _ticks;
    }

    
    public void setTicks(io.nop.excel.chart.model.ChartTicksModel value){
        checkAllowChange();
        
        this._ticks = value;
           
    }

    
    /**
     * 
     * xml name: title
     *  Axis title
     * 对应 Excel POI 中的 AxisTitle
     */
    
    public io.nop.excel.chart.model.ChartAxisTitleModel getTitle(){
      return _title;
    }

    
    public void setTitle(io.nop.excel.chart.model.ChartAxisTitleModel value){
        checkAllowChange();
        
        this._title = value;
           
    }

    
    /**
     * 
     * xml name: type
     *  
     */
    
    public io.nop.excel.chart.constants.ChartAxisType getType(){
      return _type;
    }

    
    public void setType(io.nop.excel.chart.constants.ChartAxisType value){
        checkAllowChange();
        
        this._type = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._grid = io.nop.api.core.util.FreezeHelper.deepFreeze(this._grid);
            
           this._labels = io.nop.api.core.util.FreezeHelper.deepFreeze(this._labels);
            
           this._line = io.nop.api.core.util.FreezeHelper.deepFreeze(this._line);
            
           this._scale = io.nop.api.core.util.FreezeHelper.deepFreeze(this._scale);
            
           this._ticks = io.nop.api.core.util.FreezeHelper.deepFreeze(this._ticks);
            
           this._title = io.nop.api.core.util.FreezeHelper.deepFreeze(this._title);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("grid",this.getGrid());
        out.putNotNull("id",this.getId());
        out.putNotNull("labels",this.getLabels());
        out.putNotNull("line",this.getLine());
        out.putNotNull("position",this.getPosition());
        out.putNotNull("scale",this.getScale());
        out.putNotNull("ticks",this.getTicks());
        out.putNotNull("title",this.getTitle());
        out.putNotNull("type",this.getType());
    }

    public ChartAxisModel cloneInstance(){
        ChartAxisModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartAxisModel instance){
        super.copyTo(instance);
        
        instance.setGrid(this.getGrid());
        instance.setId(this.getId());
        instance.setLabels(this.getLabels());
        instance.setLine(this.getLine());
        instance.setPosition(this.getPosition());
        instance.setScale(this.getScale());
        instance.setTicks(this.getTicks());
        instance.setTitle(this.getTitle());
        instance.setType(this.getType());
    }

    protected ChartAxisModel newInstance(){
        return (ChartAxisModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
