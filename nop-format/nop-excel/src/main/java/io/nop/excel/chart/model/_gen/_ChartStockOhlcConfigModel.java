package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartStockOhlcConfigModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartStockOhlcConfigModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: close
     * 
     */
    private java.lang.String _close ;
    
    /**
     *  
     * xml name: high
     * 
     */
    private java.lang.String _high ;
    
    /**
     *  
     * xml name: low
     * 
     */
    private java.lang.String _low ;
    
    /**
     *  
     * xml name: open
     * 
     */
    private java.lang.String _open ;
    
    /**
     * 
     * xml name: close
     *  
     */
    
    public java.lang.String getClose(){
      return _close;
    }

    
    public void setClose(java.lang.String value){
        checkAllowChange();
        
        this._close = value;
           
    }

    
    /**
     * 
     * xml name: high
     *  
     */
    
    public java.lang.String getHigh(){
      return _high;
    }

    
    public void setHigh(java.lang.String value){
        checkAllowChange();
        
        this._high = value;
           
    }

    
    /**
     * 
     * xml name: low
     *  
     */
    
    public java.lang.String getLow(){
      return _low;
    }

    
    public void setLow(java.lang.String value){
        checkAllowChange();
        
        this._low = value;
           
    }

    
    /**
     * 
     * xml name: open
     *  
     */
    
    public java.lang.String getOpen(){
      return _open;
    }

    
    public void setOpen(java.lang.String value){
        checkAllowChange();
        
        this._open = value;
           
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
        
        out.putNotNull("close",this.getClose());
        out.putNotNull("high",this.getHigh());
        out.putNotNull("low",this.getLow());
        out.putNotNull("open",this.getOpen());
    }

    public ChartStockOhlcConfigModel cloneInstance(){
        ChartStockOhlcConfigModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartStockOhlcConfigModel instance){
        super.copyTo(instance);
        
        instance.setClose(this.getClose());
        instance.setHigh(this.getHigh());
        instance.setLow(this.getLow());
        instance.setOpen(this.getOpen());
    }

    protected ChartStockOhlcConfigModel newInstance(){
        return (ChartStockOhlcConfigModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
