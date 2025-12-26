package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartShadowModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * 阴影效果（可选）
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartShadowModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: blur
     * 
     */
    private java.lang.Double _blur  = 3;
    
    /**
     *  
     * xml name: color
     * 
     */
    private java.lang.String _color  = "#000000";
    
    /**
     *  
     * xml name: enabled
     * 
     */
    private java.lang.Boolean _enabled  = false;
    
    /**
     *  
     * xml name: offsetX
     * 
     */
    private java.lang.Double _offsetX  = 2;
    
    /**
     *  
     * xml name: offsetY
     * 
     */
    private java.lang.Double _offsetY  = 2;
    
    /**
     *  
     * xml name: opacity
     * 
     */
    private java.lang.Double _opacity  = 0.5;
    
    /**
     * 
     * xml name: blur
     *  
     */
    
    public java.lang.Double getBlur(){
      return _blur;
    }

    
    public void setBlur(java.lang.Double value){
        checkAllowChange();
        
        this._blur = value;
           
    }

    
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
     * xml name: offsetX
     *  
     */
    
    public java.lang.Double getOffsetX(){
      return _offsetX;
    }

    
    public void setOffsetX(java.lang.Double value){
        checkAllowChange();
        
        this._offsetX = value;
           
    }

    
    /**
     * 
     * xml name: offsetY
     *  
     */
    
    public java.lang.Double getOffsetY(){
      return _offsetY;
    }

    
    public void setOffsetY(java.lang.Double value){
        checkAllowChange();
        
        this._offsetY = value;
           
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
        
        out.putNotNull("blur",this.getBlur());
        out.putNotNull("color",this.getColor());
        out.putNotNull("enabled",this.getEnabled());
        out.putNotNull("offsetX",this.getOffsetX());
        out.putNotNull("offsetY",this.getOffsetY());
        out.putNotNull("opacity",this.getOpacity());
    }

    public ChartShadowModel cloneInstance(){
        ChartShadowModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartShadowModel instance){
        super.copyTo(instance);
        
        instance.setBlur(this.getBlur());
        instance.setColor(this.getColor());
        instance.setEnabled(this.getEnabled());
        instance.setOffsetX(this.getOffsetX());
        instance.setOffsetY(this.getOffsetY());
        instance.setOpacity(this.getOpacity());
    }

    protected ChartShadowModel newInstance(){
        return (ChartShadowModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
