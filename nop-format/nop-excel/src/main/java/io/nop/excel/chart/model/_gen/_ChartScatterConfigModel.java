package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartScatterConfigModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * Scatter chart specific settings
 * 对应 Excel POI 中 ScatterChart 的特殊属性
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartScatterConfigModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: scatterStyle
     * 散点图样式：none、line、lineMarker、marker、smooth、smoothMarker
     */
    private java.lang.String _scatterStyle ;
    
    /**
     *  
     * xml name: showMarkers
     * 是否显示标记点：true/false
     */
    private java.lang.Boolean _showMarkers ;
    
    /**
     * 
     * xml name: scatterStyle
     *  散点图样式：none、line、lineMarker、marker、smooth、smoothMarker
     */
    
    public java.lang.String getScatterStyle(){
      return _scatterStyle;
    }

    
    public void setScatterStyle(java.lang.String value){
        checkAllowChange();
        
        this._scatterStyle = value;
           
    }

    
    /**
     * 
     * xml name: showMarkers
     *  是否显示标记点：true/false
     */
    
    public java.lang.Boolean getShowMarkers(){
      return _showMarkers;
    }

    
    public void setShowMarkers(java.lang.Boolean value){
        checkAllowChange();
        
        this._showMarkers = value;
           
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
        
        out.putNotNull("scatterStyle",this.getScatterStyle());
        out.putNotNull("showMarkers",this.getShowMarkers());
    }

    public ChartScatterConfigModel cloneInstance(){
        ChartScatterConfigModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartScatterConfigModel instance){
        super.copyTo(instance);
        
        instance.setScatterStyle(this.getScatterStyle());
        instance.setShowMarkers(this.getShowMarkers());
    }

    protected ChartScatterConfigModel newInstance(){
        return (ChartScatterConfigModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
