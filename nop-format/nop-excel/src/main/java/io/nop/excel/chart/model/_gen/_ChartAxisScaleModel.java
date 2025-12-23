package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartAxisScaleModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * Axis scaling configuration
 * 对应 Excel POI 中的 ValueAxis.setMinimum(), setMaximum() 等
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartAxisScaleModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: base
     * 
     */
    private java.lang.Double _base ;
    
    /**
     *  
     * xml name: interval
     * 
     */
    private java.lang.Double _interval ;
    
    /**
     *  
     * xml name: max
     * 
     */
    private java.lang.Double _max ;
    
    /**
     *  
     * xml name: min
     * 
     */
    private java.lang.Double _min ;
    
    /**
     *  
     * xml name: nice
     * 
     */
    private java.lang.Boolean _nice  = true;
    
    /**
     *  
     * xml name: type
     * 
     */
    private java.lang.String _type ;
    
    /**
     * 
     * xml name: base
     *  
     */
    
    public java.lang.Double getBase(){
      return _base;
    }

    
    public void setBase(java.lang.Double value){
        checkAllowChange();
        
        this._base = value;
           
    }

    
    /**
     * 
     * xml name: interval
     *  
     */
    
    public java.lang.Double getInterval(){
      return _interval;
    }

    
    public void setInterval(java.lang.Double value){
        checkAllowChange();
        
        this._interval = value;
           
    }

    
    /**
     * 
     * xml name: max
     *  
     */
    
    public java.lang.Double getMax(){
      return _max;
    }

    
    public void setMax(java.lang.Double value){
        checkAllowChange();
        
        this._max = value;
           
    }

    
    /**
     * 
     * xml name: min
     *  
     */
    
    public java.lang.Double getMin(){
      return _min;
    }

    
    public void setMin(java.lang.Double value){
        checkAllowChange();
        
        this._min = value;
           
    }

    
    /**
     * 
     * xml name: nice
     *  
     */
    
    public java.lang.Boolean getNice(){
      return _nice;
    }

    
    public void setNice(java.lang.Boolean value){
        checkAllowChange();
        
        this._nice = value;
           
    }

    
    /**
     * 
     * xml name: type
     *  
     */
    
    public java.lang.String getType(){
      return _type;
    }

    
    public void setType(java.lang.String value){
        checkAllowChange();
        
        this._type = value;
           
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
        
        out.putNotNull("base",this.getBase());
        out.putNotNull("interval",this.getInterval());
        out.putNotNull("max",this.getMax());
        out.putNotNull("min",this.getMin());
        out.putNotNull("nice",this.getNice());
        out.putNotNull("type",this.getType());
    }

    public ChartAxisScaleModel cloneInstance(){
        ChartAxisScaleModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartAxisScaleModel instance){
        super.copyTo(instance);
        
        instance.setBase(this.getBase());
        instance.setInterval(this.getInterval());
        instance.setMax(this.getMax());
        instance.setMin(this.getMin());
        instance.setNice(this.getNice());
        instance.setType(this.getType());
    }

    protected ChartAxisScaleModel newInstance(){
        return (ChartAxisScaleModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
