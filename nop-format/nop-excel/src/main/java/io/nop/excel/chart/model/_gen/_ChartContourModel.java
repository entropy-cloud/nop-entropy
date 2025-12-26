package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartContourModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartContourModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: enabled
     * 
     */
    private java.lang.Boolean _enabled ;
    
    /**
     *  
     * xml name: interval
     * 
     */
    private java.lang.Double _interval ;
    
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
        
        out.putNotNull("enabled",this.getEnabled());
        out.putNotNull("interval",this.getInterval());
    }

    public ChartContourModel cloneInstance(){
        ChartContourModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartContourModel instance){
        super.copyTo(instance);
        
        instance.setEnabled(this.getEnabled());
        instance.setInterval(this.getInterval());
    }

    protected ChartContourModel newInstance(){
        return (ChartContourModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
