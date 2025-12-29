package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartAxisLabelsModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * Axis labels styling
 * 对应 Excel POI 中的 AxisTickLabels
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartAxisLabelsModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: font
     * 
     */
    private io.nop.excel.model.ExcelFont _font ;
    
    /**
     *  
     * xml name: format
     * 
     */
    private java.lang.String _format ;
    
    /**
     *  
     * xml name: interval
     * 
     */
    private java.lang.Integer _interval ;
    
    /**
     *  
     * xml name: margin
     * 
     */
    private java.lang.Double _margin ;
    
    /**
     *  
     * xml name: rotation
     * 
     */
    private java.lang.Double _rotation ;
    
    /**
     *  
     * xml name: visible
     * 
     */
    private java.lang.Boolean _visible  = true;
    
    /**
     * 
     * xml name: font
     *  
     */
    
    public io.nop.excel.model.ExcelFont getFont(){
      return _font;
    }

    
    public void setFont(io.nop.excel.model.ExcelFont value){
        checkAllowChange();
        
        this._font = value;
           
    }

    
    /**
     * 
     * xml name: format
     *  
     */
    
    public java.lang.String getFormat(){
      return _format;
    }

    
    public void setFormat(java.lang.String value){
        checkAllowChange();
        
        this._format = value;
           
    }

    
    /**
     * 
     * xml name: interval
     *  
     */
    
    public java.lang.Integer getInterval(){
      return _interval;
    }

    
    public void setInterval(java.lang.Integer value){
        checkAllowChange();
        
        this._interval = value;
           
    }

    
    /**
     * 
     * xml name: margin
     *  
     */
    
    public java.lang.Double getMargin(){
      return _margin;
    }

    
    public void setMargin(java.lang.Double value){
        checkAllowChange();
        
        this._margin = value;
           
    }

    
    /**
     * 
     * xml name: rotation
     *  
     */
    
    public java.lang.Double getRotation(){
      return _rotation;
    }

    
    public void setRotation(java.lang.Double value){
        checkAllowChange();
        
        this._rotation = value;
           
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
        
           this._font = io.nop.api.core.util.FreezeHelper.deepFreeze(this._font);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("font",this.getFont());
        out.putNotNull("format",this.getFormat());
        out.putNotNull("interval",this.getInterval());
        out.putNotNull("margin",this.getMargin());
        out.putNotNull("rotation",this.getRotation());
        out.putNotNull("visible",this.getVisible());
    }

    public ChartAxisLabelsModel cloneInstance(){
        ChartAxisLabelsModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartAxisLabelsModel instance){
        super.copyTo(instance);
        
        instance.setFont(this.getFont());
        instance.setFormat(this.getFormat());
        instance.setInterval(this.getInterval());
        instance.setMargin(this.getMargin());
        instance.setRotation(this.getRotation());
        instance.setVisible(this.getVisible());
    }

    protected ChartAxisLabelsModel newInstance(){
        return (ChartAxisLabelsModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
