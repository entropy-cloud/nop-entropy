package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartSelectionModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * Selection capabilities
 * 主要用于 Web 图表
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartSelectionModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: brushStyle
     * 
     */
    private java.lang.String _brushStyle ;
    
    /**
     *  
     * xml name: enabled
     * 
     */
    private java.lang.Boolean _enabled  = false;
    
    /**
     *  
     * xml name: multiple
     * 
     */
    private java.lang.Boolean _multiple  = false;
    
    /**
     *  
     * xml name: type
     * 
     */
    private java.lang.String _type ;
    
    /**
     * 
     * xml name: brushStyle
     *  
     */
    
    public java.lang.String getBrushStyle(){
      return _brushStyle;
    }

    
    public void setBrushStyle(java.lang.String value){
        checkAllowChange();
        
        this._brushStyle = value;
           
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
     * xml name: multiple
     *  
     */
    
    public java.lang.Boolean getMultiple(){
      return _multiple;
    }

    
    public void setMultiple(java.lang.Boolean value){
        checkAllowChange();
        
        this._multiple = value;
           
    }

    
    /**
     * 
     * xml name: type
     *  
     */
    
    public java.lang.String getType(){
      return _type;
    }

    
    public void setType(java.lang.String value){
        checkAllowChange();
        
        this._type = value;
           
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
        
        out.putNotNull("brushStyle",this.getBrushStyle());
        out.putNotNull("enabled",this.getEnabled());
        out.putNotNull("multiple",this.getMultiple());
        out.putNotNull("type",this.getType());
    }

    public ChartSelectionModel cloneInstance(){
        ChartSelectionModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartSelectionModel instance){
        super.copyTo(instance);
        
        instance.setBrushStyle(this.getBrushStyle());
        instance.setEnabled(this.getEnabled());
        instance.setMultiple(this.getMultiple());
        instance.setType(this.getType());
    }

    protected ChartSelectionModel newInstance(){
        return (ChartSelectionModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
