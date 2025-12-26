package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartValueFilterModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * 值筛选器
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartValueFilterModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: axisId
     * 
     */
    private java.lang.String _axisId ;
    
    /**
     *  
     * xml name: enabled
     * 
     */
    private java.lang.Boolean _enabled ;
    
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
     * xml name: axisId
     *  
     */
    
    public java.lang.String getAxisId(){
      return _axisId;
    }

    
    public void setAxisId(java.lang.String value){
        checkAllowChange();
        
        this._axisId = value;
           
    }

    
    /**
     * 
     * xml name: enabled
     *  
     */
    
    public java.lang.Boolean getEnabled(){
      return _enabled;
    }

    
    public void setEnabled(java.lang.Boolean value){
        checkAllowChange();
        
        this._enabled = value;
           
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
        
        out.putNotNull("axisId",this.getAxisId());
        out.putNotNull("enabled",this.getEnabled());
        out.putNotNull("max",this.getMax());
        out.putNotNull("min",this.getMin());
    }

    public ChartValueFilterModel cloneInstance(){
        ChartValueFilterModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartValueFilterModel instance){
        super.copyTo(instance);
        
        instance.setAxisId(this.getAxisId());
        instance.setEnabled(this.getEnabled());
        instance.setMax(this.getMax());
        instance.setMin(this.getMin());
    }

    protected ChartValueFilterModel newInstance(){
        return (ChartValueFilterModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
