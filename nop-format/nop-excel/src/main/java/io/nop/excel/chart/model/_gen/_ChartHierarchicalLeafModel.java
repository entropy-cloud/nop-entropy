package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartHierarchicalLeafModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartHierarchicalLeafModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: labelPosition
     * 
     */
    private io.nop.excel.chart.constants.ChartLabelPosition _labelPosition ;
    
    /**
     *  
     * xml name: visible
     * 
     */
    private boolean _visible  = true;
    
    /**
     * 
     * xml name: labelPosition
     *  
     */
    
    public io.nop.excel.chart.constants.ChartLabelPosition getLabelPosition(){
      return _labelPosition;
    }

    
    public void setLabelPosition(io.nop.excel.chart.constants.ChartLabelPosition value){
        checkAllowChange();
        
        this._labelPosition = value;
           
    }

    
    /**
     * 
     * xml name: visible
     *  
     */
    
    public boolean isVisible(){
      return _visible;
    }

    
    public void setVisible(boolean value){
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
        
        out.putNotNull("labelPosition",this.getLabelPosition());
        out.putNotNull("visible",this.isVisible());
    }

    public ChartHierarchicalLeafModel cloneInstance(){
        ChartHierarchicalLeafModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartHierarchicalLeafModel instance){
        super.copyTo(instance);
        
        instance.setLabelPosition(this.getLabelPosition());
        instance.setVisible(this.isVisible());
    }

    protected ChartHierarchicalLeafModel newInstance(){
        return (ChartHierarchicalLeafModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
