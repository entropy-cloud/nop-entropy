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
     * xml name: crossAt
     * 
     */
    private java.lang.Double _crossAt ;
    
    /**
     *  
     * xml name: crossAxisId
     * 
     */
    private java.lang.String _crossAxisId ;
    
    /**
     *  
     * xml name: id
     * 
     */
    private java.lang.String _id ;
    
    /**
     *  
     * xml name: lineStyle
     * Axis line styling
     * 对应 Excel POI 中的 AxisLine
     */
    private io.nop.excel.chart.model.ChartLineStyleModel _lineStyle ;
    
    /**
     *  
     * xml name: majorGrid
     * Grid lines configuration
     * 对应 Excel POI 中的 ChartGridLines
     */
    private io.nop.excel.chart.model.ChartGridModel _majorGrid ;
    
    /**
     *  
     * xml name: minorGrid
     * Grid lines configuration
     * 对应 Excel POI 中的 ChartGridLines
     */
    private io.nop.excel.chart.model.ChartGridModel _minorGrid ;
    
    /**
     *  
     * xml name: multiLevel
     * 对应多级分类轴
     */
    private java.lang.Boolean _multiLevel ;
    
    /**
     *  
     * xml name: position
     * 
     */
    private io.nop.excel.chart.constants.ChartAxisPosition _position ;
    
    /**
     *  
     * xml name: primary
     * 是否主坐标轴
     */
    private boolean _primary  = true;
    
    /**
     *  
     * xml name: scale
     * 
     */
    private io.nop.excel.chart.model.ChartAxisScaleModel _scale ;
    
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
     * xml name: ticks
     * Unified tick configuration (recommended)
     * 统一的刻度配置，更符合 OOXML 的平级结构
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
     * xml name: visible
     * 
     */
    private boolean _visible  = true;
    
    /**
     * 
     * xml name: crossAt
     *  
     */
    
    public java.lang.Double getCrossAt(){
      return _crossAt;
    }

    
    public void setCrossAt(java.lang.Double value){
        checkAllowChange();
        
        this._crossAt = value;
           
    }

    
    /**
     * 
     * xml name: crossAxisId
     *  
     */
    
    public java.lang.String getCrossAxisId(){
      return _crossAxisId;
    }

    
    public void setCrossAxisId(java.lang.String value){
        checkAllowChange();
        
        this._crossAxisId = value;
           
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
     * xml name: lineStyle
     *  Axis line styling
     * 对应 Excel POI 中的 AxisLine
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
     * xml name: majorGrid
     *  Grid lines configuration
     * 对应 Excel POI 中的 ChartGridLines
     */
    
    public io.nop.excel.chart.model.ChartGridModel getMajorGrid(){
      return _majorGrid;
    }

    
    public void setMajorGrid(io.nop.excel.chart.model.ChartGridModel value){
        checkAllowChange();
        
        this._majorGrid = value;
           
    }

    
    /**
     * 
     * xml name: minorGrid
     *  Grid lines configuration
     * 对应 Excel POI 中的 ChartGridLines
     */
    
    public io.nop.excel.chart.model.ChartGridModel getMinorGrid(){
      return _minorGrid;
    }

    
    public void setMinorGrid(io.nop.excel.chart.model.ChartGridModel value){
        checkAllowChange();
        
        this._minorGrid = value;
           
    }

    
    /**
     * 
     * xml name: multiLevel
     *  对应多级分类轴
     */
    
    public java.lang.Boolean getMultiLevel(){
      return _multiLevel;
    }

    
    public void setMultiLevel(java.lang.Boolean value){
        checkAllowChange();
        
        this._multiLevel = value;
           
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
     * xml name: primary
     *  是否主坐标轴
     */
    
    public boolean isPrimary(){
      return _primary;
    }

    
    public void setPrimary(boolean value){
        checkAllowChange();
        
        this._primary = value;
           
    }

    
    /**
     * 
     * xml name: scale
     *  
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
     * xml name: ticks
     *  Unified tick configuration (recommended)
     * 统一的刻度配置，更符合 OOXML 的平级结构
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
        
           this._lineStyle = io.nop.api.core.util.FreezeHelper.deepFreeze(this._lineStyle);
            
           this._majorGrid = io.nop.api.core.util.FreezeHelper.deepFreeze(this._majorGrid);
            
           this._minorGrid = io.nop.api.core.util.FreezeHelper.deepFreeze(this._minorGrid);
            
           this._scale = io.nop.api.core.util.FreezeHelper.deepFreeze(this._scale);
            
           this._shapeStyle = io.nop.api.core.util.FreezeHelper.deepFreeze(this._shapeStyle);
            
           this._textStyle = io.nop.api.core.util.FreezeHelper.deepFreeze(this._textStyle);
            
           this._ticks = io.nop.api.core.util.FreezeHelper.deepFreeze(this._ticks);
            
           this._title = io.nop.api.core.util.FreezeHelper.deepFreeze(this._title);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("crossAt",this.getCrossAt());
        out.putNotNull("crossAxisId",this.getCrossAxisId());
        out.putNotNull("id",this.getId());
        out.putNotNull("lineStyle",this.getLineStyle());
        out.putNotNull("majorGrid",this.getMajorGrid());
        out.putNotNull("minorGrid",this.getMinorGrid());
        out.putNotNull("multiLevel",this.getMultiLevel());
        out.putNotNull("position",this.getPosition());
        out.putNotNull("primary",this.isPrimary());
        out.putNotNull("scale",this.getScale());
        out.putNotNull("shapeStyle",this.getShapeStyle());
        out.putNotNull("textStyle",this.getTextStyle());
        out.putNotNull("ticks",this.getTicks());
        out.putNotNull("title",this.getTitle());
        out.putNotNull("type",this.getType());
        out.putNotNull("visible",this.isVisible());
    }

    public ChartAxisModel cloneInstance(){
        ChartAxisModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartAxisModel instance){
        super.copyTo(instance);
        
        instance.setCrossAt(this.getCrossAt());
        instance.setCrossAxisId(this.getCrossAxisId());
        instance.setId(this.getId());
        instance.setLineStyle(this.getLineStyle());
        instance.setMajorGrid(this.getMajorGrid());
        instance.setMinorGrid(this.getMinorGrid());
        instance.setMultiLevel(this.getMultiLevel());
        instance.setPosition(this.getPosition());
        instance.setPrimary(this.isPrimary());
        instance.setScale(this.getScale());
        instance.setShapeStyle(this.getShapeStyle());
        instance.setTextStyle(this.getTextStyle());
        instance.setTicks(this.getTicks());
        instance.setTitle(this.getTitle());
        instance.setType(this.getType());
        instance.setVisible(this.isVisible());
    }

    protected ChartAxisModel newInstance(){
        return (ChartAxisModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
