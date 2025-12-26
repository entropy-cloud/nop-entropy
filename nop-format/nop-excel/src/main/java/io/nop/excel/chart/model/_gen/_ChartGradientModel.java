package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartGradientModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * Gradient fill support
 * 对应 Excel 中的渐变填充
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartGradientModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: angle
     * 
     */
    private java.lang.Double _angle ;
    
    /**
     *  
     * xml name: direction
     * 
     */
    private java.lang.String _direction ;
    
    /**
     *  
     * xml name: enabled
     * 
     */
    private java.lang.Boolean _enabled ;
    
    /**
     *  
     * xml name: endColor
     * 
     */
    private java.lang.String _endColor ;
    
    /**
     *  
     * xml name: startColor
     * 
     */
    private java.lang.String _startColor ;
    
    /**
     * 
     * xml name: angle
     *  
     */
    
    public java.lang.Double getAngle(){
      return _angle;
    }

    
    public void setAngle(java.lang.Double value){
        checkAllowChange();
        
        this._angle = value;
           
    }

    
    /**
     * 
     * xml name: direction
     *  
     */
    
    public java.lang.String getDirection(){
      return _direction;
    }

    
    public void setDirection(java.lang.String value){
        checkAllowChange();
        
        this._direction = value;
           
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
     * xml name: endColor
     *  
     */
    
    public java.lang.String getEndColor(){
      return _endColor;
    }

    
    public void setEndColor(java.lang.String value){
        checkAllowChange();
        
        this._endColor = value;
           
    }

    
    /**
     * 
     * xml name: startColor
     *  
     */
    
    public java.lang.String getStartColor(){
      return _startColor;
    }

    
    public void setStartColor(java.lang.String value){
        checkAllowChange();
        
        this._startColor = value;
           
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
        
        out.putNotNull("angle",this.getAngle());
        out.putNotNull("direction",this.getDirection());
        out.putNotNull("enabled",this.getEnabled());
        out.putNotNull("endColor",this.getEndColor());
        out.putNotNull("startColor",this.getStartColor());
    }

    public ChartGradientModel cloneInstance(){
        ChartGradientModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartGradientModel instance){
        super.copyTo(instance);
        
        instance.setAngle(this.getAngle());
        instance.setDirection(this.getDirection());
        instance.setEnabled(this.getEnabled());
        instance.setEndColor(this.getEndColor());
        instance.setStartColor(this.getStartColor());
    }

    protected ChartGradientModel newInstance(){
        return (ChartGradientModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
