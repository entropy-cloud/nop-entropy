package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartHeatmapConfigModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * Heatmap chart specific settings
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartHeatmapConfigModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: cellSize
     * 
     */
    private java.lang.Double _cellSize ;
    
    /**
     *  
     * xml name: colorRange
     * 
     */
    private java.util.List<java.lang.String> _colorRange ;
    
    /**
     *  
     * xml name: missingDataColor
     * 
     */
    private java.lang.String _missingDataColor ;
    
    /**
     * 
     * xml name: cellSize
     *  
     */
    
    public java.lang.Double getCellSize(){
      return _cellSize;
    }

    
    public void setCellSize(java.lang.Double value){
        checkAllowChange();
        
        this._cellSize = value;
           
    }

    
    /**
     * 
     * xml name: colorRange
     *  
     */
    
    public java.util.List<java.lang.String> getColorRange(){
      return _colorRange;
    }

    
    public void setColorRange(java.util.List<java.lang.String> value){
        checkAllowChange();
        
        this._colorRange = value;
           
    }

    
    /**
     * 
     * xml name: missingDataColor
     *  
     */
    
    public java.lang.String getMissingDataColor(){
      return _missingDataColor;
    }

    
    public void setMissingDataColor(java.lang.String value){
        checkAllowChange();
        
        this._missingDataColor = value;
           
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
        
        out.putNotNull("cellSize",this.getCellSize());
        out.putNotNull("colorRange",this.getColorRange());
        out.putNotNull("missingDataColor",this.getMissingDataColor());
    }

    public ChartHeatmapConfigModel cloneInstance(){
        ChartHeatmapConfigModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartHeatmapConfigModel instance){
        super.copyTo(instance);
        
        instance.setCellSize(this.getCellSize());
        instance.setColorRange(this.getColorRange());
        instance.setMissingDataColor(this.getMissingDataColor());
    }

    protected ChartHeatmapConfigModel newInstance(){
        return (ChartHeatmapConfigModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
