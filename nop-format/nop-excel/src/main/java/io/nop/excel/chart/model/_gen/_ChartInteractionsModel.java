package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartInteractionsModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * Interactive features configuration
 * 主要用于 Web 图表（ECharts），Excel 图表部分支持
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartInteractionsModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: animation
     * Animation configuration
     * 主要用于 Web 图表
     */
    private io.nop.excel.chart.model.ChartGlobalAnimationModel _animation ;
    
    /**
     *  
     * xml name: hover
     * Hover effects
     * 主要用于 Web 图表
     */
    private io.nop.excel.chart.model.ChartHoverModel _hover ;
    
    /**
     *  
     * xml name: selection
     * Selection capabilities
     * 主要用于 Web 图表
     */
    private io.nop.excel.chart.model.ChartSelectionModel _selection ;
    
    /**
     *  
     * xml name: tooltip
     * Tooltip configuration
     * 对应 Excel 图表的数据标签显示
     */
    private io.nop.excel.chart.model.ChartTooltipModel _tooltip ;
    
    /**
     *  
     * xml name: zoom
     * Zoom and pan capabilities
     * 主要用于 Web 图表
     */
    private io.nop.excel.chart.model.ChartZoomModel _zoom ;
    
    /**
     * 
     * xml name: animation
     *  Animation configuration
     * 主要用于 Web 图表
     */
    
    public io.nop.excel.chart.model.ChartGlobalAnimationModel getAnimation(){
      return _animation;
    }

    
    public void setAnimation(io.nop.excel.chart.model.ChartGlobalAnimationModel value){
        checkAllowChange();
        
        this._animation = value;
           
    }

    
    /**
     * 
     * xml name: hover
     *  Hover effects
     * 主要用于 Web 图表
     */
    
    public io.nop.excel.chart.model.ChartHoverModel getHover(){
      return _hover;
    }

    
    public void setHover(io.nop.excel.chart.model.ChartHoverModel value){
        checkAllowChange();
        
        this._hover = value;
           
    }

    
    /**
     * 
     * xml name: selection
     *  Selection capabilities
     * 主要用于 Web 图表
     */
    
    public io.nop.excel.chart.model.ChartSelectionModel getSelection(){
      return _selection;
    }

    
    public void setSelection(io.nop.excel.chart.model.ChartSelectionModel value){
        checkAllowChange();
        
        this._selection = value;
           
    }

    
    /**
     * 
     * xml name: tooltip
     *  Tooltip configuration
     * 对应 Excel 图表的数据标签显示
     */
    
    public io.nop.excel.chart.model.ChartTooltipModel getTooltip(){
      return _tooltip;
    }

    
    public void setTooltip(io.nop.excel.chart.model.ChartTooltipModel value){
        checkAllowChange();
        
        this._tooltip = value;
           
    }

    
    /**
     * 
     * xml name: zoom
     *  Zoom and pan capabilities
     * 主要用于 Web 图表
     */
    
    public io.nop.excel.chart.model.ChartZoomModel getZoom(){
      return _zoom;
    }

    
    public void setZoom(io.nop.excel.chart.model.ChartZoomModel value){
        checkAllowChange();
        
        this._zoom = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._animation = io.nop.api.core.util.FreezeHelper.deepFreeze(this._animation);
            
           this._hover = io.nop.api.core.util.FreezeHelper.deepFreeze(this._hover);
            
           this._selection = io.nop.api.core.util.FreezeHelper.deepFreeze(this._selection);
            
           this._tooltip = io.nop.api.core.util.FreezeHelper.deepFreeze(this._tooltip);
            
           this._zoom = io.nop.api.core.util.FreezeHelper.deepFreeze(this._zoom);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("animation",this.getAnimation());
        out.putNotNull("hover",this.getHover());
        out.putNotNull("selection",this.getSelection());
        out.putNotNull("tooltip",this.getTooltip());
        out.putNotNull("zoom",this.getZoom());
    }

    public ChartInteractionsModel cloneInstance(){
        ChartInteractionsModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartInteractionsModel instance){
        super.copyTo(instance);
        
        instance.setAnimation(this.getAnimation());
        instance.setHover(this.getHover());
        instance.setSelection(this.getSelection());
        instance.setTooltip(this.getTooltip());
        instance.setZoom(this.getZoom());
    }

    protected ChartInteractionsModel newInstance(){
        return (ChartInteractionsModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
