package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartLabelsModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * Data labels configuration
 * 对应 Excel POI 中的 DataLabels
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartLabelsModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: enabled
     * 
     */
    private java.lang.Boolean _enabled  = false;
    
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
     * xml name: position
     * 
     */
    private java.lang.String _position ;
    
    /**
     *  
     * xml name: rotation
     * 
     */
    private java.lang.Double _rotation ;
    
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
     * xml name: position
     *  
     */
    
    public java.lang.String getPosition(){
      return _position;
    }

    
    public void setPosition(java.lang.String value){
        checkAllowChange();
        
        this._position = value;
           
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
        
        out.putNotNull("enabled",this.getEnabled());
        out.putNotNull("font",this.getFont());
        out.putNotNull("format",this.getFormat());
        out.putNotNull("position",this.getPosition());
        out.putNotNull("rotation",this.getRotation());
    }

    public ChartLabelsModel cloneInstance(){
        ChartLabelsModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartLabelsModel instance){
        super.copyTo(instance);
        
        instance.setEnabled(this.getEnabled());
        instance.setFont(this.getFont());
        instance.setFormat(this.getFormat());
        instance.setPosition(this.getPosition());
        instance.setRotation(this.getRotation());
    }

    protected ChartLabelsModel newInstance(){
        return (ChartLabelsModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
