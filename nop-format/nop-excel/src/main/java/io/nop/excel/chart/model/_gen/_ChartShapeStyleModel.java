package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartShapeStyleModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * Common shape style model based on POI CTShapeProperties
 * 通用形状样式模型，基于 POI 的 CTShapeProperties
 * 对应 Excel POI 中的 CTShapeProperties，用于 Legend、DataLabel、Tooltip 等元素的样式
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartShapeStyleModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: border
     * Chart border styling
     * 对应 Excel 图表边框设置
     */
    private io.nop.excel.chart.model.ChartBorderModel _border ;
    
    /**
     *  
     * xml name: fill
     * Fill pattern configuration
     * 对应 Excel POI 中的 FillProperties
     */
    private io.nop.excel.chart.model.ChartFillModel _fill ;
    
    /**
     *  
     * xml name: opacity
     * 
     */
    private java.lang.Double _opacity ;
    
    /**
     *  
     * xml name: shadow
     * 阴影效果（可选）
     */
    private io.nop.excel.chart.model.ChartShadowModel _shadow ;
    
    /**
     * 
     * xml name: border
     *  Chart border styling
     * 对应 Excel 图表边框设置
     */
    
    public io.nop.excel.chart.model.ChartBorderModel getBorder(){
      return _border;
    }

    
    public void setBorder(io.nop.excel.chart.model.ChartBorderModel value){
        checkAllowChange();
        
        this._border = value;
           
    }

    
    /**
     * 
     * xml name: fill
     *  Fill pattern configuration
     * 对应 Excel POI 中的 FillProperties
     */
    
    public io.nop.excel.chart.model.ChartFillModel getFill(){
      return _fill;
    }

    
    public void setFill(io.nop.excel.chart.model.ChartFillModel value){
        checkAllowChange();
        
        this._fill = value;
           
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
     * xml name: shadow
     *  阴影效果（可选）
     */
    
    public io.nop.excel.chart.model.ChartShadowModel getShadow(){
      return _shadow;
    }

    
    public void setShadow(io.nop.excel.chart.model.ChartShadowModel value){
        checkAllowChange();
        
        this._shadow = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._border = io.nop.api.core.util.FreezeHelper.deepFreeze(this._border);
            
           this._fill = io.nop.api.core.util.FreezeHelper.deepFreeze(this._fill);
            
           this._shadow = io.nop.api.core.util.FreezeHelper.deepFreeze(this._shadow);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("border",this.getBorder());
        out.putNotNull("fill",this.getFill());
        out.putNotNull("opacity",this.getOpacity());
        out.putNotNull("shadow",this.getShadow());
    }

    public ChartShapeStyleModel cloneInstance(){
        ChartShapeStyleModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartShapeStyleModel instance){
        super.copyTo(instance);
        
        instance.setBorder(this.getBorder());
        instance.setFill(this.getFill());
        instance.setOpacity(this.getOpacity());
        instance.setShadow(this.getShadow());
    }

    protected ChartShapeStyleModel newInstance(){
        return (ChartShapeStyleModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
