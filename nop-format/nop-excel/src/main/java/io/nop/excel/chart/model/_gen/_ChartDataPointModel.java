package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartDataPointModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartDataPointModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: bubble3D
     * 
     */
    private java.lang.Boolean _bubble3D ;
    
    /**
     *  
     * xml name: explosion
     * 
     */
    private java.lang.Double _explosion ;
    
    /**
     *  
     * xml name: index
     * 
     */
    private int _index ;
    
    /**
     *  
     * xml name: shapeStyle
     * Shape style specific to this data point, overriding the series-level style.
     */
    private io.nop.excel.chart.model.ChartShapeStyleModel _shapeStyle ;
    
    /**
     * 
     * xml name: bubble3D
     *  
     */
    
    public java.lang.Boolean getBubble3D(){
      return _bubble3D;
    }

    
    public void setBubble3D(java.lang.Boolean value){
        checkAllowChange();
        
        this._bubble3D = value;
           
    }

    
    /**
     * 
     * xml name: explosion
     *  
     */
    
    public java.lang.Double getExplosion(){
      return _explosion;
    }

    
    public void setExplosion(java.lang.Double value){
        checkAllowChange();
        
        this._explosion = value;
           
    }

    
    /**
     * 
     * xml name: index
     *  
     */
    
    public int getIndex(){
      return _index;
    }

    
    public void setIndex(int value){
        checkAllowChange();
        
        this._index = value;
           
    }

    
    /**
     * 
     * xml name: shapeStyle
     *  Shape style specific to this data point, overriding the series-level style.
     */
    
    public io.nop.excel.chart.model.ChartShapeStyleModel getShapeStyle(){
      return _shapeStyle;
    }

    
    public void setShapeStyle(io.nop.excel.chart.model.ChartShapeStyleModel value){
        checkAllowChange();
        
        this._shapeStyle = value;
           
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
        
        out.putNotNull("bubble3D",this.getBubble3D());
        out.putNotNull("explosion",this.getExplosion());
        out.putNotNull("index",this.getIndex());
        out.putNotNull("shapeStyle",this.getShapeStyle());
    }

    public ChartDataPointModel cloneInstance(){
        ChartDataPointModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartDataPointModel instance){
        super.copyTo(instance);
        
        instance.setBubble3D(this.getBubble3D());
        instance.setExplosion(this.getExplosion());
        instance.setIndex(this.getIndex());
        instance.setShapeStyle(this.getShapeStyle());
    }

    protected ChartDataPointModel newInstance(){
        return (ChartDataPointModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
