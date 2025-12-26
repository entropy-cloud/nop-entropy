package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartAxisScaleModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartAxisScaleModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: logBase
     * 对数底数；空=线性轴  → <c:logBase val="double"/>
     */
    private java.lang.Double _logBase ;
    
    /**
     *  
     * xml name: max
     * 手动最大值；空=自动  → <c:max val="double"/>
     */
    private java.lang.Double _max ;
    
    /**
     *  
     * xml name: min
     * 手动最小值；空=自动  → <c:min val="double"/>
     */
    private java.lang.Double _min ;
    
    /**
     *  
     * xml name: reverse
     * 是否翻转坐标轴方向：false=minMax（默认），true=maxMin  → <c:orientation val="minMax|maxMin"/>
     */
    private java.lang.Boolean _reverse ;
    
    /**
     * 
     * xml name: logBase
     *  对数底数；空=线性轴  → <c:logBase val="double"/>
     */
    
    public java.lang.Double getLogBase(){
      return _logBase;
    }

    
    public void setLogBase(java.lang.Double value){
        checkAllowChange();
        
        this._logBase = value;
           
    }

    
    /**
     * 
     * xml name: max
     *  手动最大值；空=自动  → <c:max val="double"/>
     */
    
    public java.lang.Double getMax(){
      return _max;
    }

    
    public void setMax(java.lang.Double value){
        checkAllowChange();
        
        this._max = value;
           
    }

    
    /**
     * 
     * xml name: min
     *  手动最小值；空=自动  → <c:min val="double"/>
     */
    
    public java.lang.Double getMin(){
      return _min;
    }

    
    public void setMin(java.lang.Double value){
        checkAllowChange();
        
        this._min = value;
           
    }

    
    /**
     * 
     * xml name: reverse
     *  是否翻转坐标轴方向：false=minMax（默认），true=maxMin  → <c:orientation val="minMax|maxMin"/>
     */
    
    public java.lang.Boolean getReverse(){
      return _reverse;
    }

    
    public void setReverse(java.lang.Boolean value){
        checkAllowChange();
        
        this._reverse = value;
           
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
        
        out.putNotNull("logBase",this.getLogBase());
        out.putNotNull("max",this.getMax());
        out.putNotNull("min",this.getMin());
        out.putNotNull("reverse",this.getReverse());
    }

    public ChartAxisScaleModel cloneInstance(){
        ChartAxisScaleModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartAxisScaleModel instance){
        super.copyTo(instance);
        
        instance.setLogBase(this.getLogBase());
        instance.setMax(this.getMax());
        instance.setMin(this.getMin());
        instance.setReverse(this.getReverse());
    }

    protected ChartAxisScaleModel newInstance(){
        return (ChartAxisScaleModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
