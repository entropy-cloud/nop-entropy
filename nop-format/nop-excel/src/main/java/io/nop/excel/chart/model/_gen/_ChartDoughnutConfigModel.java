package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartDoughnutConfigModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartDoughnutConfigModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: endAngle
     * 
     */
    private java.lang.Double _endAngle ;
    
    /**
     *  
     * xml name: holeSize
     * 
     */
    private java.lang.Double _holeSize ;
    
    /**
     *  
     * xml name: innerRadius
     * 
     */
    private java.lang.Double _innerRadius ;
    
    /**
     *  
     * xml name: is3D
     * 
     */
    private java.lang.Boolean _is3D ;
    
    /**
     *  
     * xml name: outerRadius
     * 
     */
    private java.lang.Double _outerRadius ;
    
    /**
     *  
     * xml name: padAngle
     * 
     */
    private java.lang.Double _padAngle ;
    
    /**
     *  
     * xml name: startAngle
     * 
     */
    private java.lang.Double _startAngle ;
    
    /**
     *  
     * xml name: varyColors
     * 
     */
    private java.lang.Boolean _varyColors ;
    
    /**
     * 
     * xml name: endAngle
     *  
     */
    
    public java.lang.Double getEndAngle(){
      return _endAngle;
    }

    
    public void setEndAngle(java.lang.Double value){
        checkAllowChange();
        
        this._endAngle = value;
           
    }

    
    /**
     * 
     * xml name: holeSize
     *  
     */
    
    public java.lang.Double getHoleSize(){
      return _holeSize;
    }

    
    public void setHoleSize(java.lang.Double value){
        checkAllowChange();
        
        this._holeSize = value;
           
    }

    
    /**
     * 
     * xml name: innerRadius
     *  
     */
    
    public java.lang.Double getInnerRadius(){
      return _innerRadius;
    }

    
    public void setInnerRadius(java.lang.Double value){
        checkAllowChange();
        
        this._innerRadius = value;
           
    }

    
    /**
     * 
     * xml name: is3D
     *  
     */
    
    public java.lang.Boolean getIs3D(){
      return _is3D;
    }

    
    public void setIs3D(java.lang.Boolean value){
        checkAllowChange();
        
        this._is3D = value;
           
    }

    
    /**
     * 
     * xml name: outerRadius
     *  
     */
    
    public java.lang.Double getOuterRadius(){
      return _outerRadius;
    }

    
    public void setOuterRadius(java.lang.Double value){
        checkAllowChange();
        
        this._outerRadius = value;
           
    }

    
    /**
     * 
     * xml name: padAngle
     *  
     */
    
    public java.lang.Double getPadAngle(){
      return _padAngle;
    }

    
    public void setPadAngle(java.lang.Double value){
        checkAllowChange();
        
        this._padAngle = value;
           
    }

    
    /**
     * 
     * xml name: startAngle
     *  
     */
    
    public java.lang.Double getStartAngle(){
      return _startAngle;
    }

    
    public void setStartAngle(java.lang.Double value){
        checkAllowChange();
        
        this._startAngle = value;
           
    }

    
    /**
     * 
     * xml name: varyColors
     *  
     */
    
    public java.lang.Boolean getVaryColors(){
      return _varyColors;
    }

    
    public void setVaryColors(java.lang.Boolean value){
        checkAllowChange();
        
        this._varyColors = value;
           
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
        
        out.putNotNull("endAngle",this.getEndAngle());
        out.putNotNull("holeSize",this.getHoleSize());
        out.putNotNull("innerRadius",this.getInnerRadius());
        out.putNotNull("is3D",this.getIs3D());
        out.putNotNull("outerRadius",this.getOuterRadius());
        out.putNotNull("padAngle",this.getPadAngle());
        out.putNotNull("startAngle",this.getStartAngle());
        out.putNotNull("varyColors",this.getVaryColors());
    }

    public ChartDoughnutConfigModel cloneInstance(){
        ChartDoughnutConfigModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartDoughnutConfigModel instance){
        super.copyTo(instance);
        
        instance.setEndAngle(this.getEndAngle());
        instance.setHoleSize(this.getHoleSize());
        instance.setInnerRadius(this.getInnerRadius());
        instance.setIs3D(this.getIs3D());
        instance.setOuterRadius(this.getOuterRadius());
        instance.setPadAngle(this.getPadAngle());
        instance.setStartAngle(this.getStartAngle());
        instance.setVaryColors(this.getVaryColors());
    }

    protected ChartDoughnutConfigModel newInstance(){
        return (ChartDoughnutConfigModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
