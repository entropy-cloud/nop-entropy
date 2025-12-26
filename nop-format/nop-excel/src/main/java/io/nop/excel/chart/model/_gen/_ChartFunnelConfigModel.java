package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartFunnelConfigModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartFunnelConfigModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: gap
     * 
     */
    private java.lang.Double _gap ;
    
    /**
     *  
     * xml name: neckHeight
     * 
     */
    private java.lang.Double _neckHeight ;
    
    /**
     *  
     * xml name: neckWidth
     * 
     */
    private java.lang.Double _neckWidth ;
    
    /**
     *  
     * xml name: sort
     * 
     */
    private io.nop.excel.chart.constants.ChartFunnelSort _sort ;
    
    /**
     * 
     * xml name: gap
     *  
     */
    
    public java.lang.Double getGap(){
      return _gap;
    }

    
    public void setGap(java.lang.Double value){
        checkAllowChange();
        
        this._gap = value;
           
    }

    
    /**
     * 
     * xml name: neckHeight
     *  
     */
    
    public java.lang.Double getNeckHeight(){
      return _neckHeight;
    }

    
    public void setNeckHeight(java.lang.Double value){
        checkAllowChange();
        
        this._neckHeight = value;
           
    }

    
    /**
     * 
     * xml name: neckWidth
     *  
     */
    
    public java.lang.Double getNeckWidth(){
      return _neckWidth;
    }

    
    public void setNeckWidth(java.lang.Double value){
        checkAllowChange();
        
        this._neckWidth = value;
           
    }

    
    /**
     * 
     * xml name: sort
     *  
     */
    
    public io.nop.excel.chart.constants.ChartFunnelSort getSort(){
      return _sort;
    }

    
    public void setSort(io.nop.excel.chart.constants.ChartFunnelSort value){
        checkAllowChange();
        
        this._sort = value;
           
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
        
        out.putNotNull("gap",this.getGap());
        out.putNotNull("neckHeight",this.getNeckHeight());
        out.putNotNull("neckWidth",this.getNeckWidth());
        out.putNotNull("sort",this.getSort());
    }

    public ChartFunnelConfigModel cloneInstance(){
        ChartFunnelConfigModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartFunnelConfigModel instance){
        super.copyTo(instance);
        
        instance.setGap(this.getGap());
        instance.setNeckHeight(this.getNeckHeight());
        instance.setNeckWidth(this.getNeckWidth());
        instance.setSort(this.getSort());
    }

    protected ChartFunnelConfigModel newInstance(){
        return (ChartFunnelConfigModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
