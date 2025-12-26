package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartFillModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * Fill pattern configuration
 * 对应 Excel POI 中的 FillProperties
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartFillModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: backgroundColor
     * 
     */
    private java.lang.String _backgroundColor ;
    
    /**
     *  
     * xml name: foregroundColor
     * 
     */
    private java.lang.String _foregroundColor ;
    
    /**
     *  
     * xml name: gradient
     * Gradient fill support
     * 对应 Excel 中的渐变填充
     */
    private io.nop.excel.chart.model.ChartGradientModel _gradient ;
    
    /**
     *  
     * xml name: opacity
     * 
     */
    private java.lang.Double _opacity ;
    
    /**
     *  
     * xml name: pattern
     * 
     */
    private io.nop.excel.chart.constants.ChartFillPatternType _pattern ;
    
    /**
     *  
     * xml name: picture
     * 
     */
    private io.nop.excel.chart.model.ChartFillPictureModel _picture ;
    
    /**
     *  
     * xml name: type
     * 
     */
    private io.nop.excel.chart.constants.ChartFillType _type ;
    
    /**
     * 
     * xml name: backgroundColor
     *  
     */
    
    public java.lang.String getBackgroundColor(){
      return _backgroundColor;
    }

    
    public void setBackgroundColor(java.lang.String value){
        checkAllowChange();
        
        this._backgroundColor = value;
           
    }

    
    /**
     * 
     * xml name: foregroundColor
     *  
     */
    
    public java.lang.String getForegroundColor(){
      return _foregroundColor;
    }

    
    public void setForegroundColor(java.lang.String value){
        checkAllowChange();
        
        this._foregroundColor = value;
           
    }

    
    /**
     * 
     * xml name: gradient
     *  Gradient fill support
     * 对应 Excel 中的渐变填充
     */
    
    public io.nop.excel.chart.model.ChartGradientModel getGradient(){
      return _gradient;
    }

    
    public void setGradient(io.nop.excel.chart.model.ChartGradientModel value){
        checkAllowChange();
        
        this._gradient = value;
           
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
     * xml name: pattern
     *  
     */
    
    public io.nop.excel.chart.constants.ChartFillPatternType getPattern(){
      return _pattern;
    }

    
    public void setPattern(io.nop.excel.chart.constants.ChartFillPatternType value){
        checkAllowChange();
        
        this._pattern = value;
           
    }

    
    /**
     * 
     * xml name: picture
     *  
     */
    
    public io.nop.excel.chart.model.ChartFillPictureModel getPicture(){
      return _picture;
    }

    
    public void setPicture(io.nop.excel.chart.model.ChartFillPictureModel value){
        checkAllowChange();
        
        this._picture = value;
           
    }

    
    /**
     * 
     * xml name: type
     *  
     */
    
    public io.nop.excel.chart.constants.ChartFillType getType(){
      return _type;
    }

    
    public void setType(io.nop.excel.chart.constants.ChartFillType value){
        checkAllowChange();
        
        this._type = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._gradient = io.nop.api.core.util.FreezeHelper.deepFreeze(this._gradient);
            
           this._picture = io.nop.api.core.util.FreezeHelper.deepFreeze(this._picture);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("backgroundColor",this.getBackgroundColor());
        out.putNotNull("foregroundColor",this.getForegroundColor());
        out.putNotNull("gradient",this.getGradient());
        out.putNotNull("opacity",this.getOpacity());
        out.putNotNull("pattern",this.getPattern());
        out.putNotNull("picture",this.getPicture());
        out.putNotNull("type",this.getType());
    }

    public ChartFillModel cloneInstance(){
        ChartFillModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartFillModel instance){
        super.copyTo(instance);
        
        instance.setBackgroundColor(this.getBackgroundColor());
        instance.setForegroundColor(this.getForegroundColor());
        instance.setGradient(this.getGradient());
        instance.setOpacity(this.getOpacity());
        instance.setPattern(this.getPattern());
        instance.setPicture(this.getPicture());
        instance.setType(this.getType());
    }

    protected ChartFillModel newInstance(){
        return (ChartFillModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
