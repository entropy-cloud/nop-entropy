package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartTopNFilterModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * 顶部N筛选器
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartTopNFilterModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: by
     * 
     */
    private io.nop.excel.chart.constants.ChartTopNBy _by ;
    
    /**
     *  
     * xml name: enabled
     * 
     */
    private java.lang.Boolean _enabled ;
    
    /**
     *  
     * xml name: n
     * 
     */
    private java.lang.Integer _n ;
    
    /**
     * 
     * xml name: by
     *  
     */
    
    public io.nop.excel.chart.constants.ChartTopNBy getBy(){
      return _by;
    }

    
    public void setBy(io.nop.excel.chart.constants.ChartTopNBy value){
        checkAllowChange();
        
        this._by = value;
           
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
     * xml name: n
     *  
     */
    
    public java.lang.Integer getN(){
      return _n;
    }

    
    public void setN(java.lang.Integer value){
        checkAllowChange();
        
        this._n = value;
           
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
        
        out.putNotNull("by",this.getBy());
        out.putNotNull("enabled",this.getEnabled());
        out.putNotNull("n",this.getN());
    }

    public ChartTopNFilterModel cloneInstance(){
        ChartTopNFilterModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartTopNFilterModel instance){
        super.copyTo(instance);
        
        instance.setBy(this.getBy());
        instance.setEnabled(this.getEnabled());
        instance.setN(this.getN());
    }

    protected ChartTopNFilterModel newInstance(){
        return (ChartTopNFilterModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
