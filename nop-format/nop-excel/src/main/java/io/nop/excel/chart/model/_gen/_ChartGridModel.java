package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartGridModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * Grid lines configuration
 * 对应 Excel POI 中的 ChartGridLines
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartGridModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: color
     * 
     */
    private java.lang.String _color ;
    
    /**
     *  
     * xml name: opacity
     * 
     */
    private java.lang.Double _opacity ;
    
    /**
     *  
     * xml name: style
     * 
     */
    private io.nop.excel.chart.constants.ChartLineStyle _style ;
    
    /**
     *  
     * xml name: visible
     * 
     */
    private boolean _visible  = true;
    
    /**
     *  
     * xml name: width
     * 
     */
    private java.lang.Double _width ;
    
    /**
     * 
     * xml name: color
     *  
     */
    
    public java.lang.String getColor(){
      return _color;
    }

    
    public void setColor(java.lang.String value){
        checkAllowChange();
        
        this._color = value;
           
    }

    
    /**
     * 
     * xml name: opacity
     *  
     */
    
    public java.lang.Double getOpacity(){
      return _opacity;
    }

    
    public void setOpacity(java.lang.Double value){
        checkAllowChange();
        
        this._opacity = value;
           
    }

    
    /**
     * 
     * xml name: style
     *  
     */
    
    public io.nop.excel.chart.constants.ChartLineStyle getStyle(){
      return _style;
    }

    
    public void setStyle(io.nop.excel.chart.constants.ChartLineStyle value){
        checkAllowChange();
        
        this._style = value;
           
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

    
    /**
     * 
     * xml name: width
     *  
     */
    
    public java.lang.Double getWidth(){
      return _width;
    }

    
    public void setWidth(java.lang.Double value){
        checkAllowChange();
        
        this._width = value;
           
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
        
        out.putNotNull("color",this.getColor());
        out.putNotNull("opacity",this.getOpacity());
        out.putNotNull("style",this.getStyle());
        out.putNotNull("visible",this.isVisible());
        out.putNotNull("width",this.getWidth());
    }

    public ChartGridModel cloneInstance(){
        ChartGridModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartGridModel instance){
        super.copyTo(instance);
        
        instance.setColor(this.getColor());
        instance.setOpacity(this.getOpacity());
        instance.setStyle(this.getStyle());
        instance.setVisible(this.isVisible());
        instance.setWidth(this.getWidth());
    }

    protected ChartGridModel newInstance(){
        return (ChartGridModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
