package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartLineConfigModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * Line chart specific settings
 * 对应 Excel POI 中 LineChart 的特殊属性
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartLineConfigModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: dropLines
     * 是否显示垂直线到X轴：true/false
     */
    private java.lang.Boolean _dropLines ;
    
    /**
     *  
     * xml name: grouping
     * 分组方式：standard、stacked、percentStacked
     */
    private io.nop.excel.chart.constants.ChartBarGrouping _grouping ;
    
    /**
     *  
     * xml name: hiLowLines
     * 是否显示高低线：true/false
     */
    private java.lang.Boolean _hiLowLines ;
    
    /**
     *  
     * xml name: is3D
     * 
     */
    private java.lang.Boolean _is3D ;
    
    /**
     *  
     * xml name: marker
     * 是否显示标记点：true/false
     */
    private java.lang.Boolean _marker ;
    
    /**
     *  
     * xml name: smooth
     * 是否平滑曲线：true/false
     */
    private java.lang.Boolean _smooth ;
    
    /**
     *  
     * xml name: upDownBars
     * 是否显示涨跌柱：true/false
     */
    private java.lang.Boolean _upDownBars ;
    
    /**
     *  
     * xml name: varyColors
     * 
     */
    private java.lang.Boolean _varyColors ;
    
    /**
     * 
     * xml name: dropLines
     *  是否显示垂直线到X轴：true/false
     */
    
    public java.lang.Boolean getDropLines(){
      return _dropLines;
    }

    
    public void setDropLines(java.lang.Boolean value){
        checkAllowChange();
        
        this._dropLines = value;
           
    }

    
    /**
     * 
     * xml name: grouping
     *  分组方式：standard、stacked、percentStacked
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
     * xml name: hiLowLines
     *  是否显示高低线：true/false
     */
    
    public java.lang.Boolean getHiLowLines(){
      return _hiLowLines;
    }

    
    public void setHiLowLines(java.lang.Boolean value){
        checkAllowChange();
        
        this._hiLowLines = value;
           
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
     * xml name: marker
     *  是否显示标记点：true/false
     */
    
    public java.lang.Boolean getMarker(){
      return _marker;
    }

    
    public void setMarker(java.lang.Boolean value){
        checkAllowChange();
        
        this._marker = value;
           
    }

    
    /**
     * 
     * xml name: smooth
     *  是否平滑曲线：true/false
     */
    
    public java.lang.Boolean getSmooth(){
      return _smooth;
    }

    
    public void setSmooth(java.lang.Boolean value){
        checkAllowChange();
        
        this._smooth = value;
           
    }

    
    /**
     * 
     * xml name: upDownBars
     *  是否显示涨跌柱：true/false
     */
    
    public java.lang.Boolean getUpDownBars(){
      return _upDownBars;
    }

    
    public void setUpDownBars(java.lang.Boolean value){
        checkAllowChange();
        
        this._upDownBars = value;
           
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
        
        out.putNotNull("dropLines",this.getDropLines());
        out.putNotNull("grouping",this.getGrouping());
        out.putNotNull("hiLowLines",this.getHiLowLines());
        out.putNotNull("is3D",this.getIs3D());
        out.putNotNull("marker",this.getMarker());
        out.putNotNull("smooth",this.getSmooth());
        out.putNotNull("upDownBars",this.getUpDownBars());
        out.putNotNull("varyColors",this.getVaryColors());
    }

    public ChartLineConfigModel cloneInstance(){
        ChartLineConfigModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartLineConfigModel instance){
        super.copyTo(instance);
        
        instance.setDropLines(this.getDropLines());
        instance.setGrouping(this.getGrouping());
        instance.setHiLowLines(this.getHiLowLines());
        instance.setIs3D(this.getIs3D());
        instance.setMarker(this.getMarker());
        instance.setSmooth(this.getSmooth());
        instance.setUpDownBars(this.getUpDownBars());
        instance.setVaryColors(this.getVaryColors());
    }

    protected ChartLineConfigModel newInstance(){
        return (ChartLineConfigModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
