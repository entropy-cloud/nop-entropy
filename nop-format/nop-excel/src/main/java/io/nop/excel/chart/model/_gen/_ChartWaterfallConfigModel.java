package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartWaterfallConfigModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartWaterfallConfigModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: connectorLines
     * Line/border styling
     * 对应 Excel POI 中的 LineProperties
     */
    private io.nop.excel.chart.model.ChartLineStyleModel _connectorLines ;
    
    /**
     *  
     * xml name: subtotal
     * 
     */
    private io.nop.excel.chart.model.ChartWaterfallSubtotalModel _subtotal ;
    
    /**
     *  
     * xml name: total
     * 
     */
    private io.nop.excel.chart.model.ChartWaterfallTotalModel _total ;
    
    /**
     * 
     * xml name: connectorLines
     *  Line/border styling
     * 对应 Excel POI 中的 LineProperties
     */
    
    public io.nop.excel.chart.model.ChartLineStyleModel getConnectorLines(){
      return _connectorLines;
    }

    
    public void setConnectorLines(io.nop.excel.chart.model.ChartLineStyleModel value){
        checkAllowChange();
        
        this._connectorLines = value;
           
    }

    
    /**
     * 
     * xml name: subtotal
     *  
     */
    
    public io.nop.excel.chart.model.ChartWaterfallSubtotalModel getSubtotal(){
      return _subtotal;
    }

    
    public void setSubtotal(io.nop.excel.chart.model.ChartWaterfallSubtotalModel value){
        checkAllowChange();
        
        this._subtotal = value;
           
    }

    
    /**
     * 
     * xml name: total
     *  
     */
    
    public io.nop.excel.chart.model.ChartWaterfallTotalModel getTotal(){
      return _total;
    }

    
    public void setTotal(io.nop.excel.chart.model.ChartWaterfallTotalModel value){
        checkAllowChange();
        
        this._total = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._connectorLines = io.nop.api.core.util.FreezeHelper.deepFreeze(this._connectorLines);
            
           this._subtotal = io.nop.api.core.util.FreezeHelper.deepFreeze(this._subtotal);
            
           this._total = io.nop.api.core.util.FreezeHelper.deepFreeze(this._total);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("connectorLines",this.getConnectorLines());
        out.putNotNull("subtotal",this.getSubtotal());
        out.putNotNull("total",this.getTotal());
    }

    public ChartWaterfallConfigModel cloneInstance(){
        ChartWaterfallConfigModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartWaterfallConfigModel instance){
        super.copyTo(instance);
        
        instance.setConnectorLines(this.getConnectorLines());
        instance.setSubtotal(this.getSubtotal());
        instance.setTotal(this.getTotal());
    }

    protected ChartWaterfallConfigModel newInstance(){
        return (ChartWaterfallConfigModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
