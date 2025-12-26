package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartColorScaleModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartColorScaleModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: colors
     * 
     */
    private java.util.List<java.lang.String> _colors ;
    
    /**
     *  
     * xml name: max
     * 
     */
    private java.lang.String _max ;
    
    /**
     *  
     * xml name: min
     * 
     */
    private java.lang.String _min ;
    
    /**
     * 
     * xml name: colors
     *  
     */
    
    public java.util.List<java.lang.String> getColors(){
      return _colors;
    }

    
    public void setColors(java.util.List<java.lang.String> value){
        checkAllowChange();
        
        this._colors = value;
           
    }

    
    /**
     * 
     * xml name: max
     *  
     */
    
    public java.lang.String getMax(){
      return _max;
    }

    
    public void setMax(java.lang.String value){
        checkAllowChange();
        
        this._max = value;
           
    }

    
    /**
     * 
     * xml name: min
     *  
     */
    
    public java.lang.String getMin(){
      return _min;
    }

    
    public void setMin(java.lang.String value){
        checkAllowChange();
        
        this._min = value;
           
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
        
        out.putNotNull("colors",this.getColors());
        out.putNotNull("max",this.getMax());
        out.putNotNull("min",this.getMin());
    }

    public ChartColorScaleModel cloneInstance(){
        ChartColorScaleModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartColorScaleModel instance){
        super.copyTo(instance);
        
        instance.setColors(this.getColors());
        instance.setMax(this.getMax());
        instance.setMin(this.getMin());
    }

    protected ChartColorScaleModel newInstance(){
        return (ChartColorScaleModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
