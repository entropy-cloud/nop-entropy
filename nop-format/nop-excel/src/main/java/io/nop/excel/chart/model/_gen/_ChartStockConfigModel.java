package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartStockConfigModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * 需要扩展的配置
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartStockConfigModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: candlestick
     * 
     */
    private io.nop.excel.chart.model.ChartCandlestickConfigModel _candlestick ;
    
    /**
     *  
     * xml name: ohlc
     * 
     */
    private io.nop.excel.chart.model.ChartStockOhlcConfigModel _ohlc ;
    
    /**
     *  
     * xml name: volume
     * 
     */
    private io.nop.excel.chart.model.ChartStockVolumeConfigModel _volume ;
    
    /**
     * 
     * xml name: candlestick
     *  
     */
    
    public io.nop.excel.chart.model.ChartCandlestickConfigModel getCandlestick(){
      return _candlestick;
    }

    
    public void setCandlestick(io.nop.excel.chart.model.ChartCandlestickConfigModel value){
        checkAllowChange();
        
        this._candlestick = value;
           
    }

    
    /**
     * 
     * xml name: ohlc
     *  
     */
    
    public io.nop.excel.chart.model.ChartStockOhlcConfigModel getOhlc(){
      return _ohlc;
    }

    
    public void setOhlc(io.nop.excel.chart.model.ChartStockOhlcConfigModel value){
        checkAllowChange();
        
        this._ohlc = value;
           
    }

    
    /**
     * 
     * xml name: volume
     *  
     */
    
    public io.nop.excel.chart.model.ChartStockVolumeConfigModel getVolume(){
      return _volume;
    }

    
    public void setVolume(io.nop.excel.chart.model.ChartStockVolumeConfigModel value){
        checkAllowChange();
        
        this._volume = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._candlestick = io.nop.api.core.util.FreezeHelper.deepFreeze(this._candlestick);
            
           this._ohlc = io.nop.api.core.util.FreezeHelper.deepFreeze(this._ohlc);
            
           this._volume = io.nop.api.core.util.FreezeHelper.deepFreeze(this._volume);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("candlestick",this.getCandlestick());
        out.putNotNull("ohlc",this.getOhlc());
        out.putNotNull("volume",this.getVolume());
    }

    public ChartStockConfigModel cloneInstance(){
        ChartStockConfigModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartStockConfigModel instance){
        super.copyTo(instance);
        
        instance.setCandlestick(this.getCandlestick());
        instance.setOhlc(this.getOhlc());
        instance.setVolume(this.getVolume());
    }

    protected ChartStockConfigModel newInstance(){
        return (ChartStockConfigModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
