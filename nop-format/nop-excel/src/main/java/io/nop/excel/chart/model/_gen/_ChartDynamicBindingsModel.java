package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartDynamicBindingsModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartDynamicBindingsModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: axisDataCellRefExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _axisDataCellRefExpr ;
    
    /**
     *  
     * xml name: axisTitleCellRefExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _axisTitleCellRefExpr ;
    
    /**
     *  
     * xml name: chartTitleCellRefExpr
     * 图表标题引用，返回单个单元格地址
     */
    private io.nop.core.lang.eval.IEvalFunction _chartTitleCellRefExpr ;
    
    /**
     *  
     * xml name: chartTitleExpr
     * 图表标题直接计算：返回标题文本字符串，直接作为图表标题
     */
    private io.nop.core.lang.eval.IEvalFunction _chartTitleExpr ;
    
    /**
     *  
     * xml name: seriesCatCellRefExpr
     * 系列分类范围引用，返回单元格区域地址（X轴标签）
     */
    private io.nop.core.lang.eval.IEvalFunction _seriesCatCellRefExpr ;
    
    /**
     *  
     * xml name: seriesCatExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _seriesCatExpr ;
    
    /**
     *  
     * xml name: seriesDataCellRefExpr
     * 系列数值范围引用，返回单元格区域地址
     */
    private io.nop.core.lang.eval.IEvalFunction _seriesDataCellRefExpr ;
    
    /**
     *  
     * xml name: seriesDataExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _seriesDataExpr ;
    
    /**
     *  
     * xml name: seriesNameCellRefExpr
     * 系列名称引用，返回单个单元格地址
     */
    private io.nop.core.lang.eval.IEvalFunction _seriesNameCellRefExpr ;
    
    /**
     *  
     * xml name: seriesNameExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _seriesNameExpr ;
    
    /**
     * 
     * xml name: axisDataCellRefExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getAxisDataCellRefExpr(){
      return _axisDataCellRefExpr;
    }

    
    public void setAxisDataCellRefExpr(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._axisDataCellRefExpr = value;
           
    }

    
    /**
     * 
     * xml name: axisTitleCellRefExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getAxisTitleCellRefExpr(){
      return _axisTitleCellRefExpr;
    }

    
    public void setAxisTitleCellRefExpr(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._axisTitleCellRefExpr = value;
           
    }

    
    /**
     * 
     * xml name: chartTitleCellRefExpr
     *  图表标题引用，返回单个单元格地址
     */
    
    public io.nop.core.lang.eval.IEvalFunction getChartTitleCellRefExpr(){
      return _chartTitleCellRefExpr;
    }

    
    public void setChartTitleCellRefExpr(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._chartTitleCellRefExpr = value;
           
    }

    
    /**
     * 
     * xml name: chartTitleExpr
     *  图表标题直接计算：返回标题文本字符串，直接作为图表标题
     */
    
    public io.nop.core.lang.eval.IEvalFunction getChartTitleExpr(){
      return _chartTitleExpr;
    }

    
    public void setChartTitleExpr(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._chartTitleExpr = value;
           
    }

    
    /**
     * 
     * xml name: seriesCatCellRefExpr
     *  系列分类范围引用，返回单元格区域地址（X轴标签）
     */
    
    public io.nop.core.lang.eval.IEvalFunction getSeriesCatCellRefExpr(){
      return _seriesCatCellRefExpr;
    }

    
    public void setSeriesCatCellRefExpr(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._seriesCatCellRefExpr = value;
           
    }

    
    /**
     * 
     * xml name: seriesCatExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getSeriesCatExpr(){
      return _seriesCatExpr;
    }

    
    public void setSeriesCatExpr(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._seriesCatExpr = value;
           
    }

    
    /**
     * 
     * xml name: seriesDataCellRefExpr
     *  系列数值范围引用，返回单元格区域地址
     */
    
    public io.nop.core.lang.eval.IEvalFunction getSeriesDataCellRefExpr(){
      return _seriesDataCellRefExpr;
    }

    
    public void setSeriesDataCellRefExpr(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._seriesDataCellRefExpr = value;
           
    }

    
    /**
     * 
     * xml name: seriesDataExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getSeriesDataExpr(){
      return _seriesDataExpr;
    }

    
    public void setSeriesDataExpr(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._seriesDataExpr = value;
           
    }

    
    /**
     * 
     * xml name: seriesNameCellRefExpr
     *  系列名称引用，返回单个单元格地址
     */
    
    public io.nop.core.lang.eval.IEvalFunction getSeriesNameCellRefExpr(){
      return _seriesNameCellRefExpr;
    }

    
    public void setSeriesNameCellRefExpr(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._seriesNameCellRefExpr = value;
           
    }

    
    /**
     * 
     * xml name: seriesNameExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getSeriesNameExpr(){
      return _seriesNameExpr;
    }

    
    public void setSeriesNameExpr(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._seriesNameExpr = value;
           
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
        
        out.putNotNull("axisDataCellRefExpr",this.getAxisDataCellRefExpr());
        out.putNotNull("axisTitleCellRefExpr",this.getAxisTitleCellRefExpr());
        out.putNotNull("chartTitleCellRefExpr",this.getChartTitleCellRefExpr());
        out.putNotNull("chartTitleExpr",this.getChartTitleExpr());
        out.putNotNull("seriesCatCellRefExpr",this.getSeriesCatCellRefExpr());
        out.putNotNull("seriesCatExpr",this.getSeriesCatExpr());
        out.putNotNull("seriesDataCellRefExpr",this.getSeriesDataCellRefExpr());
        out.putNotNull("seriesDataExpr",this.getSeriesDataExpr());
        out.putNotNull("seriesNameCellRefExpr",this.getSeriesNameCellRefExpr());
        out.putNotNull("seriesNameExpr",this.getSeriesNameExpr());
    }

    public ChartDynamicBindingsModel cloneInstance(){
        ChartDynamicBindingsModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartDynamicBindingsModel instance){
        super.copyTo(instance);
        
        instance.setAxisDataCellRefExpr(this.getAxisDataCellRefExpr());
        instance.setAxisTitleCellRefExpr(this.getAxisTitleCellRefExpr());
        instance.setChartTitleCellRefExpr(this.getChartTitleCellRefExpr());
        instance.setChartTitleExpr(this.getChartTitleExpr());
        instance.setSeriesCatCellRefExpr(this.getSeriesCatCellRefExpr());
        instance.setSeriesCatExpr(this.getSeriesCatExpr());
        instance.setSeriesDataCellRefExpr(this.getSeriesDataCellRefExpr());
        instance.setSeriesDataExpr(this.getSeriesDataExpr());
        instance.setSeriesNameCellRefExpr(this.getSeriesNameCellRefExpr());
        instance.setSeriesNameExpr(this.getSeriesNameExpr());
    }

    protected ChartDynamicBindingsModel newInstance(){
        return (ChartDynamicBindingsModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
