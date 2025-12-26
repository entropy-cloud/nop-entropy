package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartTicksModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * Axis tick marks
 * 对应 Excel POI 中的 AxisTickMark
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartTicksModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: color
     * 
     */
    private java.lang.String _color ;
    
    /**
     *  
     * xml name: majorTick
     * 主刻度线位置：none、inside、outside、cross
     */
    private io.nop.excel.chart.constants.ChartTickMark _majorTick ;
    
    /**
     *  
     * xml name: minorTick
     * 次刻度线位置：none、inside、outside、cross
     */
    private io.nop.excel.chart.constants.ChartTickMark _minorTick ;
    
    /**
     *  
     * xml name: visible
     * 
     */
    private java.lang.Boolean _visible  = true;
    
    /**
     *  
     * xml name: width
     * 
     */
    private java.lang.Double _width ;
    
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
     * xml name: majorTick
     *  主刻度线位置：none、inside、outside、cross
     */
    
    public io.nop.excel.chart.constants.ChartTickMark getMajorTick(){
      return _majorTick;
    }

    
    public void setMajorTick(io.nop.excel.chart.constants.ChartTickMark value){
        checkAllowChange();
        
        this._majorTick = value;
           
    }

    
    /**
     * 
     * xml name: minorTick
     *  次刻度线位置：none、inside、outside、cross
     */
    
    public io.nop.excel.chart.constants.ChartTickMark getMinorTick(){
      return _minorTick;
    }

    
    public void setMinorTick(io.nop.excel.chart.constants.ChartTickMark value){
        checkAllowChange();
        
        this._minorTick = value;
           
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
        
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("color",this.getColor());
        out.putNotNull("majorTick",this.getMajorTick());
        out.putNotNull("minorTick",this.getMinorTick());
        out.putNotNull("visible",this.getVisible());
        out.putNotNull("width",this.getWidth());
    }

    public ChartTicksModel cloneInstance(){
        ChartTicksModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartTicksModel instance){
        super.copyTo(instance);
        
        instance.setColor(this.getColor());
        instance.setMajorTick(this.getMajorTick());
        instance.setMinorTick(this.getMinorTick());
        instance.setVisible(this.getVisible());
        instance.setWidth(this.getWidth());
    }

    protected ChartTicksModel newInstance(){
        return (ChartTicksModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
