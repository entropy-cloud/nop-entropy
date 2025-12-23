package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartBorderModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * Chart border styling
 * 对应 Excel 图表边框设置
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartBorderModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: color
     * 
     */
    private java.lang.String _color ;
    
    /**
     *  
     * xml name: radius
     * 
     */
    private java.lang.Double _radius ;
    
    /**
     *  
     * xml name: style
     * 
     */
    private java.lang.String _style ;
    
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
     * xml name: radius
     *  
     */
    
    public java.lang.Double getRadius(){
      return _radius;
    }

    
    public void setRadius(java.lang.Double value){
        checkAllowChange();
        
        this._radius = value;
           
    }

    
    /**
     * 
     * xml name: style
     *  
     */
    
    public java.lang.String getStyle(){
      return _style;
    }

    
    public void setStyle(java.lang.String value){
        checkAllowChange();
        
        this._style = value;
           
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
        out.putNotNull("radius",this.getRadius());
        out.putNotNull("style",this.getStyle());
        out.putNotNull("width",this.getWidth());
    }

    public ChartBorderModel cloneInstance(){
        ChartBorderModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartBorderModel instance){
        super.copyTo(instance);
        
        instance.setColor(this.getColor());
        instance.setRadius(this.getRadius());
        instance.setStyle(this.getStyle());
        instance.setWidth(this.getWidth());
    }

    protected ChartBorderModel newInstance(){
        return (ChartBorderModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
