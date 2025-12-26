package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartWaterfallTotalModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartWaterfallTotalModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: label
     * 
     */
    private java.lang.String _label ;
    
    /**
     *  
     * xml name: position
     * 
     */
    private java.lang.Integer _position ;
    
    /**
     * 
     * xml name: label
     *  
     */
    
    public java.lang.String getLabel(){
      return _label;
    }

    
    public void setLabel(java.lang.String value){
        checkAllowChange();
        
        this._label = value;
           
    }

    
    /**
     * 
     * xml name: position
     *  
     */
    
    public java.lang.Integer getPosition(){
      return _position;
    }

    
    public void setPosition(java.lang.Integer value){
        checkAllowChange();
        
        this._position = value;
           
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
        
        out.putNotNull("label",this.getLabel());
        out.putNotNull("position",this.getPosition());
    }

    public ChartWaterfallTotalModel cloneInstance(){
        ChartWaterfallTotalModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartWaterfallTotalModel instance){
        super.copyTo(instance);
        
        instance.setLabel(this.getLabel());
        instance.setPosition(this.getPosition());
    }

    protected ChartWaterfallTotalModel newInstance(){
        return (ChartWaterfallTotalModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
