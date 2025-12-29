package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartRadarConfigModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * Radar chart specific settings
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartRadarConfigModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: endAngle
     * 
     */
    private java.lang.Double _endAngle ;
    
    /**
     *  
     * xml name: radarStyle
     * 
     */
    private io.nop.excel.chart.constants.ChartRadarStyle _radarStyle ;
    
    /**
     *  
     * xml name: radius
     * 
     */
    private java.lang.Double _radius ;
    
    /**
     *  
     * xml name: startAngle
     * 
     */
    private java.lang.Double _startAngle ;
    
    /**
     *  
     * xml name: varyColors
     * 
     */
    private java.lang.Boolean _varyColors ;
    
    /**
     * 
     * xml name: endAngle
     *  
     */
    
    public java.lang.Double getEndAngle(){
      return _endAngle;
    }

    
    public void setEndAngle(java.lang.Double value){
        checkAllowChange();
        
        this._endAngle = value;
           
    }

    
    /**
     * 
     * xml name: radarStyle
     *  
     */
    
    public io.nop.excel.chart.constants.ChartRadarStyle getRadarStyle(){
      return _radarStyle;
    }

    
    public void setRadarStyle(io.nop.excel.chart.constants.ChartRadarStyle value){
        checkAllowChange();
        
        this._radarStyle = value;
           
    }

    
    /**
     * 
     * xml name: radius
     *  
     */
    
    public java.lang.Double getRadius(){
      return _radius;
    }

    
    public void setRadius(java.lang.Double value){
        checkAllowChange();
        
        this._radius = value;
           
    }

    
    /**
     * 
     * xml name: startAngle
     *  
     */
    
    public java.lang.Double getStartAngle(){
      return _startAngle;
    }

    
    public void setStartAngle(java.lang.Double value){
        checkAllowChange();
        
        this._startAngle = value;
           
    }

    
    /**
     * 
     * xml name: varyColors
     *  
     */
    
    public java.lang.Boolean getVaryColors(){
      return _varyColors;
    }

    
    public void setVaryColors(java.lang.Boolean value){
        checkAllowChange();
        
        this._varyColors = value;
           
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
        
        out.putNotNull("endAngle",this.getEndAngle());
        out.putNotNull("radarStyle",this.getRadarStyle());
        out.putNotNull("radius",this.getRadius());
        out.putNotNull("startAngle",this.getStartAngle());
        out.putNotNull("varyColors",this.getVaryColors());
    }

    public ChartRadarConfigModel cloneInstance(){
        ChartRadarConfigModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartRadarConfigModel instance){
        super.copyTo(instance);
        
        instance.setEndAngle(this.getEndAngle());
        instance.setRadarStyle(this.getRadarStyle());
        instance.setRadius(this.getRadius());
        instance.setStartAngle(this.getStartAngle());
        instance.setVaryColors(this.getVaryColors());
    }

    protected ChartRadarConfigModel newInstance(){
        return (ChartRadarConfigModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
