package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartWaterfallSubtotalModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartWaterfallSubtotalModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: labels
     * 
     */
    private java.util.List<java.lang.String> _labels ;
    
    /**
     *  
     * xml name: positions
     * 
     */
    private java.util.List<java.lang.String> _positions ;
    
    /**
     * 
     * xml name: labels
     *  
     */
    
    public java.util.List<java.lang.String> getLabels(){
      return _labels;
    }

    
    public void setLabels(java.util.List<java.lang.String> value){
        checkAllowChange();
        
        this._labels = value;
           
    }

    
    /**
     * 
     * xml name: positions
     *  
     */
    
    public java.util.List<java.lang.String> getPositions(){
      return _positions;
    }

    
    public void setPositions(java.util.List<java.lang.String> value){
        checkAllowChange();
        
        this._positions = value;
           
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
        
        out.putNotNull("labels",this.getLabels());
        out.putNotNull("positions",this.getPositions());
    }

    public ChartWaterfallSubtotalModel cloneInstance(){
        ChartWaterfallSubtotalModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartWaterfallSubtotalModel instance){
        super.copyTo(instance);
        
        instance.setLabels(this.getLabels());
        instance.setPositions(this.getPositions());
    }

    protected ChartWaterfallSubtotalModel newInstance(){
        return (ChartWaterfallSubtotalModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
