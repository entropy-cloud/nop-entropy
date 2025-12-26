package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartMarkersModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * Data point markers
 * 对应 Excel POI 中线图和散点图的 Marker 设置
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartMarkersModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: borderColor
     * 
     */
    private java.lang.String _borderColor ;
    
    /**
     *  
     * xml name: borderWidth
     * 
     */
    private java.lang.Double _borderWidth ;
    
    /**
     *  
     * xml name: color
     * 
     */
    private java.lang.String _color ;
    
    /**
     *  
     * xml name: enabled
     * 
     */
    private java.lang.Boolean _enabled  = false;
    
    /**
     *  
     * xml name: size
     * 
     */
    private java.lang.Double _size ;
    
    /**
     *  
     * xml name: type
     * 
     */
    private io.nop.excel.chart.constants.ChartMarkerType _type ;
    
    /**
     * 
     * xml name: borderColor
     *  
     */
    
    public java.lang.String getBorderColor(){
      return _borderColor;
    }

    
    public void setBorderColor(java.lang.String value){
        checkAllowChange();
        
        this._borderColor = value;
           
    }

    
    /**
     * 
     * xml name: borderWidth
     *  
     */
    
    public java.lang.Double getBorderWidth(){
      return _borderWidth;
    }

    
    public void setBorderWidth(java.lang.Double value){
        checkAllowChange();
        
        this._borderWidth = value;
           
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
     * xml name: size
     *  
     */
    
    public java.lang.Double getSize(){
      return _size;
    }

    
    public void setSize(java.lang.Double value){
        checkAllowChange();
        
        this._size = value;
           
    }

    
    /**
     * 
     * xml name: type
     *  
     */
    
    public io.nop.excel.chart.constants.ChartMarkerType getType(){
      return _type;
    }

    
    public void setType(io.nop.excel.chart.constants.ChartMarkerType value){
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
        
        out.putNotNull("borderColor",this.getBorderColor());
        out.putNotNull("borderWidth",this.getBorderWidth());
        out.putNotNull("color",this.getColor());
        out.putNotNull("enabled",this.getEnabled());
        out.putNotNull("size",this.getSize());
        out.putNotNull("type",this.getType());
    }

    public ChartMarkersModel cloneInstance(){
        ChartMarkersModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartMarkersModel instance){
        super.copyTo(instance);
        
        instance.setBorderColor(this.getBorderColor());
        instance.setBorderWidth(this.getBorderWidth());
        instance.setColor(this.getColor());
        instance.setEnabled(this.getEnabled());
        instance.setSize(this.getSize());
        instance.setType(this.getType());
    }

    protected ChartMarkersModel newInstance(){
        return (ChartMarkersModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
