package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartAnimationModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * Animation settings for series
 * 对应图表动画效果配置
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartAnimationModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: delay
     * 
     */
    private java.lang.Long _delay ;
    
    /**
     *  
     * xml name: duration
     * 
     */
    private java.lang.Long _duration ;
    
    /**
     *  
     * xml name: easing
     * 
     */
    private java.lang.String _easing ;
    
    /**
     *  
     * xml name: enabled
     * 
     */
    private java.lang.Boolean _enabled  = true;
    
    /**
     * 
     * xml name: delay
     *  
     */
    
    public java.lang.Long getDelay(){
      return _delay;
    }

    
    public void setDelay(java.lang.Long value){
        checkAllowChange();
        
        this._delay = value;
           
    }

    
    /**
     * 
     * xml name: duration
     *  
     */
    
    public java.lang.Long getDuration(){
      return _duration;
    }

    
    public void setDuration(java.lang.Long value){
        checkAllowChange();
        
        this._duration = value;
           
    }

    
    /**
     * 
     * xml name: easing
     *  
     */
    
    public java.lang.String getEasing(){
      return _easing;
    }

    
    public void setEasing(java.lang.String value){
        checkAllowChange();
        
        this._easing = value;
           
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
        
        out.putNotNull("delay",this.getDelay());
        out.putNotNull("duration",this.getDuration());
        out.putNotNull("easing",this.getEasing());
        out.putNotNull("enabled",this.getEnabled());
    }

    public ChartAnimationModel cloneInstance(){
        ChartAnimationModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartAnimationModel instance){
        super.copyTo(instance);
        
        instance.setDelay(this.getDelay());
        instance.setDuration(this.getDuration());
        instance.setEasing(this.getEasing());
        instance.setEnabled(this.getEnabled());
    }

    protected ChartAnimationModel newInstance(){
        return (ChartAnimationModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
