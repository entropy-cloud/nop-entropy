package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartHoverModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * Hover effects
 * 主要用于 Web 图表
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartHoverModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: enabled
     * 
     */
    private java.lang.Boolean _enabled  = true;
    
    /**
     *  
     * xml name: highlightPolicy
     * 
     */
    private java.lang.String _highlightPolicy ;
    
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
     * xml name: highlightPolicy
     *  
     */
    
    public java.lang.String getHighlightPolicy(){
      return _highlightPolicy;
    }

    
    public void setHighlightPolicy(java.lang.String value){
        checkAllowChange();
        
        this._highlightPolicy = value;
           
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
        
        out.putNotNull("enabled",this.getEnabled());
        out.putNotNull("highlightPolicy",this.getHighlightPolicy());
    }

    public ChartHoverModel cloneInstance(){
        ChartHoverModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartHoverModel instance){
        super.copyTo(instance);
        
        instance.setEnabled(this.getEnabled());
        instance.setHighlightPolicy(this.getHighlightPolicy());
    }

    protected ChartHoverModel newInstance(){
        return (ChartHoverModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
