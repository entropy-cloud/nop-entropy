package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartSeriesStyleModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * Series visual styling
 * 对应 Excel POI 中的 ChartSeries 的样式属性
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartSeriesStyleModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: color
     * 
     */
    private java.lang.String _color ;
    
    /**
     *  
     * xml name: fill
     * Fill pattern configuration
     * 对应 Excel POI 中的 FillProperties
     */
    private io.nop.excel.chart.model.ChartFillModel _fill ;
    
    /**
     *  
     * xml name: fillOpacity
     * 
     */
    private java.lang.Double _fillOpacity ;
    
    /**
     *  
     * xml name: line
     * Line/border styling
     * 对应 Excel POI 中的 LineProperties
     */
    private io.nop.excel.chart.model.ChartLineModel _line ;
    
    /**
     *  
     * xml name: lineWidth
     * 
     */
    private java.lang.Double _lineWidth ;
    
    /**
     *  
     * xml name: opacity
     * 
     */
    private java.lang.Double _opacity ;
    
    /**
     * 
     * xml name: color
     *  
     */
    
    public java.lang.String getColor(){
      return _color;
    }

    
    public void setColor(java.lang.String value){
        checkAllowChange();
        
        this._color = value;
           
    }

    
    /**
     * 
     * xml name: fill
     *  Fill pattern configuration
     * 对应 Excel POI 中的 FillProperties
     */
    
    public io.nop.excel.chart.model.ChartFillModel getFill(){
      return _fill;
    }

    
    public void setFill(io.nop.excel.chart.model.ChartFillModel value){
        checkAllowChange();
        
        this._fill = value;
           
    }

    
    /**
     * 
     * xml name: fillOpacity
     *  
     */
    
    public java.lang.Double getFillOpacity(){
      return _fillOpacity;
    }

    
    public void setFillOpacity(java.lang.Double value){
        checkAllowChange();
        
        this._fillOpacity = value;
           
    }

    
    /**
     * 
     * xml name: line
     *  Line/border styling
     * 对应 Excel POI 中的 LineProperties
     */
    
    public io.nop.excel.chart.model.ChartLineModel getLine(){
      return _line;
    }

    
    public void setLine(io.nop.excel.chart.model.ChartLineModel value){
        checkAllowChange();
        
        this._line = value;
           
    }

    
    /**
     * 
     * xml name: lineWidth
     *  
     */
    
    public java.lang.Double getLineWidth(){
      return _lineWidth;
    }

    
    public void setLineWidth(java.lang.Double value){
        checkAllowChange();
        
        this._lineWidth = value;
           
    }

    
    /**
     * 
     * xml name: opacity
     *  
     */
    
    public java.lang.Double getOpacity(){
      return _opacity;
    }

    
    public void setOpacity(java.lang.Double value){
        checkAllowChange();
        
        this._opacity = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._fill = io.nop.api.core.util.FreezeHelper.deepFreeze(this._fill);
            
           this._line = io.nop.api.core.util.FreezeHelper.deepFreeze(this._line);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("color",this.getColor());
        out.putNotNull("fill",this.getFill());
        out.putNotNull("fillOpacity",this.getFillOpacity());
        out.putNotNull("line",this.getLine());
        out.putNotNull("lineWidth",this.getLineWidth());
        out.putNotNull("opacity",this.getOpacity());
    }

    public ChartSeriesStyleModel cloneInstance(){
        ChartSeriesStyleModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartSeriesStyleModel instance){
        super.copyTo(instance);
        
        instance.setColor(this.getColor());
        instance.setFill(this.getFill());
        instance.setFillOpacity(this.getFillOpacity());
        instance.setLine(this.getLine());
        instance.setLineWidth(this.getLineWidth());
        instance.setOpacity(this.getOpacity());
    }

    protected ChartSeriesStyleModel newInstance(){
        return (ChartSeriesStyleModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
