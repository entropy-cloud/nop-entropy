package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartHierarchicalLevelModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartHierarchicalLevelModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: depth
     * 
     */
    private java.lang.Integer _depth ;
    
    /**
     *  
     * xml name: visible
     * 
     */
    private java.lang.Boolean _visible ;
    
    /**
     * 
     * xml name: depth
     *  
     */
    
    public java.lang.Integer getDepth(){
      return _depth;
    }

    
    public void setDepth(java.lang.Integer value){
        checkAllowChange();
        
        this._depth = value;
           
    }

    
    /**
     * 
     * xml name: visible
     *  
     */
    
    public java.lang.Boolean getVisible(){
      return _visible;
    }

    
    public void setVisible(java.lang.Boolean value){
        checkAllowChange();
        
        this._visible = value;
           
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
        
        out.putNotNull("depth",this.getDepth());
        out.putNotNull("visible",this.getVisible());
    }

    public ChartHierarchicalLevelModel cloneInstance(){
        ChartHierarchicalLevelModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartHierarchicalLevelModel instance){
        super.copyTo(instance);
        
        instance.setDepth(this.getDepth());
        instance.setVisible(this.getVisible());
    }

    protected ChartHierarchicalLevelModel newInstance(){
        return (ChartHierarchicalLevelModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
