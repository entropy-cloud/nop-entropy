package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartLegendPagingModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * Legend pagination for large datasets
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartLegendPagingModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: enabled
     * 
     */
    private java.lang.Boolean _enabled  = false;
    
    /**
     *  
     * xml name: pageSize
     * 
     */
    private java.lang.Integer _pageSize ;
    
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
     * xml name: pageSize
     *  
     */
    
    public java.lang.Integer getPageSize(){
      return _pageSize;
    }

    
    public void setPageSize(java.lang.Integer value){
        checkAllowChange();
        
        this._pageSize = value;
           
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
        out.putNotNull("pageSize",this.getPageSize());
    }

    public ChartLegendPagingModel cloneInstance(){
        ChartLegendPagingModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartLegendPagingModel instance){
        super.copyTo(instance);
        
        instance.setEnabled(this.getEnabled());
        instance.setPageSize(this.getPageSize());
    }

    protected ChartLegendPagingModel newInstance(){
        return (ChartLegendPagingModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
