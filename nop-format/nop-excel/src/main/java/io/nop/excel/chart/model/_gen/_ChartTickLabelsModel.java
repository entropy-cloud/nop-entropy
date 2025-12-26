package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartTickLabelsModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * Axis labels styling
 * 对应 Excel POI 中的 AxisTickLabels
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartTickLabelsModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: font
     * 
     */
    private io.nop.excel.model.ExcelFont _font ;
    
    /**
     *  
     * xml name: numFmt
     * 数字格式串，如"General""#,##0.00""0%"
     */
    private java.lang.String _numFmt ;
    
    /**
     *  
     * xml name: position
     * 刻度标签相对于轴的位置：none、low、high、nextTo
     */
    private io.nop.excel.chart.constants.ChartAxisTickLabelPosition _position ;
    
    /**
     *  
     * xml name: rotation
     * 文字旋转角度
     */
    private java.lang.Integer _rotation ;
    
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
     * xml name: numFmt
     *  数字格式串，如"General""#,##0.00""0%"
     */
    
    public java.lang.String getNumFmt(){
      return _numFmt;
    }

    
    public void setNumFmt(java.lang.String value){
        checkAllowChange();
        
        this._numFmt = value;
           
    }

    
    /**
     * 
     * xml name: position
     *  刻度标签相对于轴的位置：none、low、high、nextTo
     */
    
    public io.nop.excel.chart.constants.ChartAxisTickLabelPosition getPosition(){
      return _position;
    }

    
    public void setPosition(io.nop.excel.chart.constants.ChartAxisTickLabelPosition value){
        checkAllowChange();
        
        this._position = value;
           
    }

    
    /**
     * 
     * xml name: rotation
     *  文字旋转角度
     */
    
    public java.lang.Integer getRotation(){
      return _rotation;
    }

    
    public void setRotation(java.lang.Integer value){
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
        out.putNotNull("numFmt",this.getNumFmt());
        out.putNotNull("position",this.getPosition());
        out.putNotNull("rotation",this.getRotation());
        out.putNotNull("visible",this.getVisible());
    }

    public ChartTickLabelsModel cloneInstance(){
        ChartTickLabelsModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartTickLabelsModel instance){
        super.copyTo(instance);
        
        instance.setFont(this.getFont());
        instance.setNumFmt(this.getNumFmt());
        instance.setPosition(this.getPosition());
        instance.setRotation(this.getRotation());
        instance.setVisible(this.getVisible());
    }

    protected ChartTickLabelsModel newInstance(){
        return (ChartTickLabelsModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
