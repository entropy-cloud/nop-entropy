package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartSeriesFilterModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * 系列筛选器
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartSeriesFilterModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: enabled
     * 
     */
    private java.lang.Boolean _enabled ;
    
    /**
     *  
     * xml name: exclude
     * 
     */
    private java.lang.Boolean _exclude ;
    
    /**
     *  
     * xml name: seriesNames
     * 
     */
    private java.util.List<java.lang.String> _seriesNames ;
    
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
     * xml name: exclude
     *  
     */
    
    public java.lang.Boolean getExclude(){
      return _exclude;
    }

    
    public void setExclude(java.lang.Boolean value){
        checkAllowChange();
        
        this._exclude = value;
           
    }

    
    /**
     * 
     * xml name: seriesNames
     *  
     */
    
    public java.util.List<java.lang.String> getSeriesNames(){
      return _seriesNames;
    }

    
    public void setSeriesNames(java.util.List<java.lang.String> value){
        checkAllowChange();
        
        this._seriesNames = value;
           
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
        out.putNotNull("exclude",this.getExclude());
        out.putNotNull("seriesNames",this.getSeriesNames());
    }

    public ChartSeriesFilterModel cloneInstance(){
        ChartSeriesFilterModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartSeriesFilterModel instance){
        super.copyTo(instance);
        
        instance.setEnabled(this.getEnabled());
        instance.setExclude(this.getExclude());
        instance.setSeriesNames(this.getSeriesNames());
    }

    protected ChartSeriesFilterModel newInstance(){
        return (ChartSeriesFilterModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
