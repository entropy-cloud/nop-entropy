package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartTooltipModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * Tooltip configuration
 * 对应 Excel 图表的数据标签显示
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartTooltipModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: enabled
     * 
     */
    private java.lang.Boolean _enabled  = true;
    
    /**
     *  
     * xml name: format
     * 
     */
    private java.lang.String _format ;
    
    /**
     *  
     * xml name: hideDelay
     * 
     */
    private java.lang.Long _hideDelay ;
    
    /**
     *  
     * xml name: showDelay
     * 
     */
    private java.lang.Long _showDelay ;
    
    /**
     *  
     * xml name: style
     * Tooltip styling with extended properties
     * 对应 Excel POI 中 Tooltip 的样式，基于 CTShapeProperties 并扩展 textColor
     */
    private io.nop.excel.chart.model.ChartExtendedShapeStyleModel _style ;
    
    /**
     *  
     * xml name: trigger
     * 
     */
    private java.lang.String _trigger ;
    
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
     * xml name: hideDelay
     *  
     */
    
    public java.lang.Long getHideDelay(){
      return _hideDelay;
    }

    
    public void setHideDelay(java.lang.Long value){
        checkAllowChange();
        
        this._hideDelay = value;
           
    }

    
    /**
     * 
     * xml name: showDelay
     *  
     */
    
    public java.lang.Long getShowDelay(){
      return _showDelay;
    }

    
    public void setShowDelay(java.lang.Long value){
        checkAllowChange();
        
        this._showDelay = value;
           
    }

    
    /**
     * 
     * xml name: style
     *  Tooltip styling with extended properties
     * 对应 Excel POI 中 Tooltip 的样式，基于 CTShapeProperties 并扩展 textColor
     */
    
    public io.nop.excel.chart.model.ChartExtendedShapeStyleModel getStyle(){
      return _style;
    }

    
    public void setStyle(io.nop.excel.chart.model.ChartExtendedShapeStyleModel value){
        checkAllowChange();
        
        this._style = value;
           
    }

    
    /**
     * 
     * xml name: trigger
     *  
     */
    
    public java.lang.String getTrigger(){
      return _trigger;
    }

    
    public void setTrigger(java.lang.String value){
        checkAllowChange();
        
        this._trigger = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._style = io.nop.api.core.util.FreezeHelper.deepFreeze(this._style);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("enabled",this.getEnabled());
        out.putNotNull("format",this.getFormat());
        out.putNotNull("hideDelay",this.getHideDelay());
        out.putNotNull("showDelay",this.getShowDelay());
        out.putNotNull("style",this.getStyle());
        out.putNotNull("trigger",this.getTrigger());
    }

    public ChartTooltipModel cloneInstance(){
        ChartTooltipModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartTooltipModel instance){
        super.copyTo(instance);
        
        instance.setEnabled(this.getEnabled());
        instance.setFormat(this.getFormat());
        instance.setHideDelay(this.getHideDelay());
        instance.setShowDelay(this.getShowDelay());
        instance.setStyle(this.getStyle());
        instance.setTrigger(this.getTrigger());
    }

    protected ChartTooltipModel newInstance(){
        return (ChartTooltipModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
