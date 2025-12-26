package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartTicksModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * Unified tick configuration (recommended)
 * 统一的刻度配置，更符合 OOXML 的平级结构
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartTicksModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: labelAlignment
     * 
     */
    private io.nop.excel.chart.constants.ChartLabelAlignment _labelAlignment ;
    
    /**
     *  
     * xml name: labelFont
     * 刻度标签字体
     */
    private io.nop.excel.model.ExcelFont _labelFont ;
    
    /**
     *  
     * xml name: labelNumFmt
     * 标签数字格式串，如"General""#,##0.00""0%"
     */
    private java.lang.String _labelNumFmt ;
    
    /**
     *  
     * xml name: labelOffset
     * 
     */
    private java.lang.Double _labelOffset ;
    
    /**
     *  
     * xml name: labelPosition
     * 刻度标签相对于轴的位置：none、low、high、nextTo
     */
    private io.nop.excel.chart.constants.ChartAxisTickLabelPosition _labelPosition ;
    
    /**
     *  
     * xml name: labelRotation
     * 标签文字旋转角度
     */
    private java.lang.Double _labelRotation ;
    
    /**
     *  
     * xml name: labelVisible
     * 是否显示刻度标签
     */
    private java.lang.Boolean _labelVisible  = true;
    
    /**
     *  
     * xml name: majorTickMark
     * 主刻度线位置：none、inside、outside、cross
     */
    private io.nop.excel.chart.constants.ChartTickMark _majorTickMark ;
    
    /**
     *  
     * xml name: minorTickMark
     * 次刻度线位置：none、inside、outside、cross
     */
    private io.nop.excel.chart.constants.ChartTickMark _minorTickMark ;
    
    /**
     *  
     * xml name: tickColor
     * 刻度线颜色
     */
    private java.lang.String _tickColor ;
    
    /**
     *  
     * xml name: tickWidth
     * 刻度线宽度
     */
    private java.lang.Double _tickWidth ;
    
    /**
     *  
     * xml name: visible
     * 是否显示刻度（包括标记和标签）
     */
    private java.lang.Boolean _visible  = true;
    
    /**
     * 
     * xml name: labelAlignment
     *  
     */
    
    public io.nop.excel.chart.constants.ChartLabelAlignment getLabelAlignment(){
      return _labelAlignment;
    }

    
    public void setLabelAlignment(io.nop.excel.chart.constants.ChartLabelAlignment value){
        checkAllowChange();
        
        this._labelAlignment = value;
           
    }

    
    /**
     * 
     * xml name: labelFont
     *  刻度标签字体
     */
    
    public io.nop.excel.model.ExcelFont getLabelFont(){
      return _labelFont;
    }

    
    public void setLabelFont(io.nop.excel.model.ExcelFont value){
        checkAllowChange();
        
        this._labelFont = value;
           
    }

    
    /**
     * 
     * xml name: labelNumFmt
     *  标签数字格式串，如"General""#,##0.00""0%"
     */
    
    public java.lang.String getLabelNumFmt(){
      return _labelNumFmt;
    }

    
    public void setLabelNumFmt(java.lang.String value){
        checkAllowChange();
        
        this._labelNumFmt = value;
           
    }

    
    /**
     * 
     * xml name: labelOffset
     *  
     */
    
    public java.lang.Double getLabelOffset(){
      return _labelOffset;
    }

    
    public void setLabelOffset(java.lang.Double value){
        checkAllowChange();
        
        this._labelOffset = value;
           
    }

    
    /**
     * 
     * xml name: labelPosition
     *  刻度标签相对于轴的位置：none、low、high、nextTo
     */
    
    public io.nop.excel.chart.constants.ChartAxisTickLabelPosition getLabelPosition(){
      return _labelPosition;
    }

    
    public void setLabelPosition(io.nop.excel.chart.constants.ChartAxisTickLabelPosition value){
        checkAllowChange();
        
        this._labelPosition = value;
           
    }

    
    /**
     * 
     * xml name: labelRotation
     *  标签文字旋转角度
     */
    
    public java.lang.Double getLabelRotation(){
      return _labelRotation;
    }

    
    public void setLabelRotation(java.lang.Double value){
        checkAllowChange();
        
        this._labelRotation = value;
           
    }

    
    /**
     * 
     * xml name: labelVisible
     *  是否显示刻度标签
     */
    
    public java.lang.Boolean getLabelVisible(){
      return _labelVisible;
    }

    
    public void setLabelVisible(java.lang.Boolean value){
        checkAllowChange();
        
        this._labelVisible = value;
           
    }

    
    /**
     * 
     * xml name: majorTickMark
     *  主刻度线位置：none、inside、outside、cross
     */
    
    public io.nop.excel.chart.constants.ChartTickMark getMajorTickMark(){
      return _majorTickMark;
    }

    
    public void setMajorTickMark(io.nop.excel.chart.constants.ChartTickMark value){
        checkAllowChange();
        
        this._majorTickMark = value;
           
    }

    
    /**
     * 
     * xml name: minorTickMark
     *  次刻度线位置：none、inside、outside、cross
     */
    
    public io.nop.excel.chart.constants.ChartTickMark getMinorTickMark(){
      return _minorTickMark;
    }

    
    public void setMinorTickMark(io.nop.excel.chart.constants.ChartTickMark value){
        checkAllowChange();
        
        this._minorTickMark = value;
           
    }

    
    /**
     * 
     * xml name: tickColor
     *  刻度线颜色
     */
    
    public java.lang.String getTickColor(){
      return _tickColor;
    }

    
    public void setTickColor(java.lang.String value){
        checkAllowChange();
        
        this._tickColor = value;
           
    }

    
    /**
     * 
     * xml name: tickWidth
     *  刻度线宽度
     */
    
    public java.lang.Double getTickWidth(){
      return _tickWidth;
    }

    
    public void setTickWidth(java.lang.Double value){
        checkAllowChange();
        
        this._tickWidth = value;
           
    }

    
    /**
     * 
     * xml name: visible
     *  是否显示刻度（包括标记和标签）
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
        
           this._labelFont = io.nop.api.core.util.FreezeHelper.deepFreeze(this._labelFont);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("labelAlignment",this.getLabelAlignment());
        out.putNotNull("labelFont",this.getLabelFont());
        out.putNotNull("labelNumFmt",this.getLabelNumFmt());
        out.putNotNull("labelOffset",this.getLabelOffset());
        out.putNotNull("labelPosition",this.getLabelPosition());
        out.putNotNull("labelRotation",this.getLabelRotation());
        out.putNotNull("labelVisible",this.getLabelVisible());
        out.putNotNull("majorTickMark",this.getMajorTickMark());
        out.putNotNull("minorTickMark",this.getMinorTickMark());
        out.putNotNull("tickColor",this.getTickColor());
        out.putNotNull("tickWidth",this.getTickWidth());
        out.putNotNull("visible",this.getVisible());
    }

    public ChartTicksModel cloneInstance(){
        ChartTicksModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartTicksModel instance){
        super.copyTo(instance);
        
        instance.setLabelAlignment(this.getLabelAlignment());
        instance.setLabelFont(this.getLabelFont());
        instance.setLabelNumFmt(this.getLabelNumFmt());
        instance.setLabelOffset(this.getLabelOffset());
        instance.setLabelPosition(this.getLabelPosition());
        instance.setLabelRotation(this.getLabelRotation());
        instance.setLabelVisible(this.getLabelVisible());
        instance.setMajorTickMark(this.getMajorTickMark());
        instance.setMinorTickMark(this.getMinorTickMark());
        instance.setTickColor(this.getTickColor());
        instance.setTickWidth(this.getTickWidth());
        instance.setVisible(this.getVisible());
    }

    protected ChartTicksModel newInstance(){
        return (ChartTicksModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
