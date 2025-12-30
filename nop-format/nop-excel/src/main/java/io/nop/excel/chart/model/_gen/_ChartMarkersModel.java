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
     * xml name: enabled
     * 
     */
    private java.lang.Boolean _enabled  = false;
    
    /**
     *  
     * xml name: shapeStyle
     * Common shape style model based on POI CTShapeProperties
     * 通用形状样式模型，基于 POI 的 CTShapeProperties
     * 对应 Excel POI 中的 CTShapeProperties，用于 Legend、DataLabel、Tooltip 等元素的样式
     */
    private io.nop.excel.chart.model.ChartShapeStyleModel _shapeStyle ;
    
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
     * xml name: shapeStyle
     *  Common shape style model based on POI CTShapeProperties
     * 通用形状样式模型，基于 POI 的 CTShapeProperties
     * 对应 Excel POI 中的 CTShapeProperties，用于 Legend、DataLabel、Tooltip 等元素的样式
     */
    
    public io.nop.excel.chart.model.ChartShapeStyleModel getShapeStyle(){
      return _shapeStyle;
    }

    
    public void setShapeStyle(io.nop.excel.chart.model.ChartShapeStyleModel value){
        checkAllowChange();
        
        this._shapeStyle = value;
           
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
        
           this._shapeStyle = io.nop.api.core.util.FreezeHelper.deepFreeze(this._shapeStyle);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("enabled",this.getEnabled());
        out.putNotNull("shapeStyle",this.getShapeStyle());
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
        
        instance.setEnabled(this.getEnabled());
        instance.setShapeStyle(this.getShapeStyle());
        instance.setSize(this.getSize());
        instance.setType(this.getType());
    }

    protected ChartMarkersModel newInstance(){
        return (ChartMarkersModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
