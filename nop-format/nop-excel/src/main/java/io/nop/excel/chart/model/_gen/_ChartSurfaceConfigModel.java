package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartSurfaceConfigModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * 成交量使用次要坐标轴
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartSurfaceConfigModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: colorScale
     * 
     */
    private io.nop.excel.chart.model.ChartColorScaleModel _colorScale ;
    
    /**
     *  
     * xml name: contour
     * 
     */
    private io.nop.excel.chart.model.ChartContourModel _contour ;
    
    /**
     *  
     * xml name: wireframe
     * 
     */
    private io.nop.excel.chart.model.ChartWireframeModel _wireframe ;
    
    /**
     * 
     * xml name: colorScale
     *  
     */
    
    public io.nop.excel.chart.model.ChartColorScaleModel getColorScale(){
      return _colorScale;
    }

    
    public void setColorScale(io.nop.excel.chart.model.ChartColorScaleModel value){
        checkAllowChange();
        
        this._colorScale = value;
           
    }

    
    /**
     * 
     * xml name: contour
     *  
     */
    
    public io.nop.excel.chart.model.ChartContourModel getContour(){
      return _contour;
    }

    
    public void setContour(io.nop.excel.chart.model.ChartContourModel value){
        checkAllowChange();
        
        this._contour = value;
           
    }

    
    /**
     * 
     * xml name: wireframe
     *  
     */
    
    public io.nop.excel.chart.model.ChartWireframeModel getWireframe(){
      return _wireframe;
    }

    
    public void setWireframe(io.nop.excel.chart.model.ChartWireframeModel value){
        checkAllowChange();
        
        this._wireframe = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._colorScale = io.nop.api.core.util.FreezeHelper.deepFreeze(this._colorScale);
            
           this._contour = io.nop.api.core.util.FreezeHelper.deepFreeze(this._contour);
            
           this._wireframe = io.nop.api.core.util.FreezeHelper.deepFreeze(this._wireframe);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("colorScale",this.getColorScale());
        out.putNotNull("contour",this.getContour());
        out.putNotNull("wireframe",this.getWireframe());
    }

    public ChartSurfaceConfigModel cloneInstance(){
        ChartSurfaceConfigModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartSurfaceConfigModel instance){
        super.copyTo(instance);
        
        instance.setColorScale(this.getColorScale());
        instance.setContour(this.getContour());
        instance.setWireframe(this.getWireframe());
    }

    protected ChartSurfaceConfigModel newInstance(){
        return (ChartSurfaceConfigModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
