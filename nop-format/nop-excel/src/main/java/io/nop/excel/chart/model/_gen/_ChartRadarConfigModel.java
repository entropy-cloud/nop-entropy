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
        out.putNotNull("radius",this.getRadius());
        out.putNotNull("startAngle",this.getStartAngle());
    }

    public ChartRadarConfigModel cloneInstance(){
        ChartRadarConfigModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartRadarConfigModel instance){
        super.copyTo(instance);
        
        instance.setEndAngle(this.getEndAngle());
        instance.setRadius(this.getRadius());
        instance.setStartAngle(this.getStartAngle());
    }

    protected ChartRadarConfigModel newInstance(){
        return (ChartRadarConfigModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
