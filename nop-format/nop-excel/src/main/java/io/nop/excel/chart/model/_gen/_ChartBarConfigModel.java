package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartBarConfigModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartBarConfigModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: dir
     * 
     */
    private io.nop.excel.chart.constants.ChartBarDirection _dir ;
    
    /**
     *  
     * xml name: grouping
     * 
     */
    private io.nop.excel.chart.constants.ChartBarGrouping _grouping ;
    
    /**
     *  
     * xml name: is3D
     * 
     */
    private java.lang.Boolean _is3D ;
    
    /**
     *  
     * xml name: percentGapWidth
     * 柱间空隙占柱宽百分比：65=65%。
     */
    private java.lang.Double _percentGapWidth ;
    
    /**
     *  
     * xml name: percentOverlap
     * 同一类别中系列重叠比例：100=100% 重叠（堆积效果）。
     */
    private java.lang.Double _percentOverlap ;
    
    /**
     *  
     * xml name: varyColors
     * 
     */
    private java.lang.Boolean _varyColors ;
    
    /**
     * 
     * xml name: dir
     *  
     */
    
    public io.nop.excel.chart.constants.ChartBarDirection getDir(){
      return _dir;
    }

    
    public void setDir(io.nop.excel.chart.constants.ChartBarDirection value){
        checkAllowChange();
        
        this._dir = value;
           
    }

    
    /**
     * 
     * xml name: grouping
     *  
     */
    
    public io.nop.excel.chart.constants.ChartBarGrouping getGrouping(){
      return _grouping;
    }

    
    public void setGrouping(io.nop.excel.chart.constants.ChartBarGrouping value){
        checkAllowChange();
        
        this._grouping = value;
           
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
     * xml name: percentGapWidth
     *  柱间空隙占柱宽百分比：65=65%。
     */
    
    public java.lang.Double getPercentGapWidth(){
      return _percentGapWidth;
    }

    
    public void setPercentGapWidth(java.lang.Double value){
        checkAllowChange();
        
        this._percentGapWidth = value;
           
    }

    
    /**
     * 
     * xml name: percentOverlap
     *  同一类别中系列重叠比例：100=100% 重叠（堆积效果）。
     */
    
    public java.lang.Double getPercentOverlap(){
      return _percentOverlap;
    }

    
    public void setPercentOverlap(java.lang.Double value){
        checkAllowChange();
        
        this._percentOverlap = value;
           
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
        
        out.putNotNull("dir",this.getDir());
        out.putNotNull("grouping",this.getGrouping());
        out.putNotNull("is3D",this.getIs3D());
        out.putNotNull("percentGapWidth",this.getPercentGapWidth());
        out.putNotNull("percentOverlap",this.getPercentOverlap());
        out.putNotNull("varyColors",this.getVaryColors());
    }

    public ChartBarConfigModel cloneInstance(){
        ChartBarConfigModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartBarConfigModel instance){
        super.copyTo(instance);
        
        instance.setDir(this.getDir());
        instance.setGrouping(this.getGrouping());
        instance.setIs3D(this.getIs3D());
        instance.setPercentGapWidth(this.getPercentGapWidth());
        instance.setPercentOverlap(this.getPercentOverlap());
        instance.setVaryColors(this.getVaryColors());
    }

    protected ChartBarConfigModel newInstance(){
        return (ChartBarConfigModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
