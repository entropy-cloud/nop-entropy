package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartGlobalAnimationModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * Animation configuration
 * 主要用于 Web 图表
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartGlobalAnimationModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: appear
     * 
     */
    private java.lang.String _appear ;
    
    /**
     *  
     * xml name: enabled
     * 
     */
    private java.lang.Boolean _enabled  = true;
    
    /**
     *  
     * xml name: enter
     * 
     */
    private java.lang.String _enter ;
    
    /**
     *  
     * xml name: leave
     * 
     */
    private java.lang.String _leave ;
    
    /**
     *  
     * xml name: update
     * 
     */
    private java.lang.String _update ;
    
    /**
     * 
     * xml name: appear
     *  
     */
    
    public java.lang.String getAppear(){
      return _appear;
    }

    
    public void setAppear(java.lang.String value){
        checkAllowChange();
        
        this._appear = value;
           
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
     * xml name: enter
     *  
     */
    
    public java.lang.String getEnter(){
      return _enter;
    }

    
    public void setEnter(java.lang.String value){
        checkAllowChange();
        
        this._enter = value;
           
    }

    
    /**
     * 
     * xml name: leave
     *  
     */
    
    public java.lang.String getLeave(){
      return _leave;
    }

    
    public void setLeave(java.lang.String value){
        checkAllowChange();
        
        this._leave = value;
           
    }

    
    /**
     * 
     * xml name: update
     *  
     */
    
    public java.lang.String getUpdate(){
      return _update;
    }

    
    public void setUpdate(java.lang.String value){
        checkAllowChange();
        
        this._update = value;
           
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
        
        out.putNotNull("appear",this.getAppear());
        out.putNotNull("enabled",this.getEnabled());
        out.putNotNull("enter",this.getEnter());
        out.putNotNull("leave",this.getLeave());
        out.putNotNull("update",this.getUpdate());
    }

    public ChartGlobalAnimationModel cloneInstance(){
        ChartGlobalAnimationModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartGlobalAnimationModel instance){
        super.copyTo(instance);
        
        instance.setAppear(this.getAppear());
        instance.setEnabled(this.getEnabled());
        instance.setEnter(this.getEnter());
        instance.setLeave(this.getLeave());
        instance.setUpdate(this.getUpdate());
    }

    protected ChartGlobalAnimationModel newInstance(){
        return (ChartGlobalAnimationModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
