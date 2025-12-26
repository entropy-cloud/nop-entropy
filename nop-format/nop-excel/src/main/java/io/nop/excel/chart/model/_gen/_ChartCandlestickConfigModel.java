package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartCandlestickConfigModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartCandlestickConfigModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: downColor
     * 
     */
    private java.lang.String _downColor ;
    
    /**
     *  
     * xml name: upColor
     * 
     */
    private java.lang.String _upColor ;
    
    /**
     * 
     * xml name: downColor
     *  
     */
    
    public java.lang.String getDownColor(){
      return _downColor;
    }

    
    public void setDownColor(java.lang.String value){
        checkAllowChange();
        
        this._downColor = value;
           
    }

    
    /**
     * 
     * xml name: upColor
     *  
     */
    
    public java.lang.String getUpColor(){
      return _upColor;
    }

    
    public void setUpColor(java.lang.String value){
        checkAllowChange();
        
        this._upColor = value;
           
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
        
        out.putNotNull("downColor",this.getDownColor());
        out.putNotNull("upColor",this.getUpColor());
    }

    public ChartCandlestickConfigModel cloneInstance(){
        ChartCandlestickConfigModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartCandlestickConfigModel instance){
        super.copyTo(instance);
        
        instance.setDownColor(this.getDownColor());
        instance.setUpColor(this.getUpColor());
    }

    protected ChartCandlestickConfigModel newInstance(){
        return (ChartCandlestickConfigModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
