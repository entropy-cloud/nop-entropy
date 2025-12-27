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
     * xml name: categoryCellRefExpr
     * 系列分类范围引用，返回单元格区域地址（X轴标签）
     */
    private io.nop.core.lang.eval.IEvalFunction _categoryCellRefExpr ;
    
    /**
     *  
     * xml name: categoryExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _categoryExpr ;
    
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
     * xml name: valuesCellRefExpr
     * 系列数值范围引用，返回单元格区域地址
     */
    private io.nop.core.lang.eval.IEvalFunction _valuesCellRefExpr ;
    
    /**
     *  
     * xml name: valuesExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _valuesExpr ;
    
    /**
     * 
     * xml name: categoryCellRefExpr
     *  系列分类范围引用，返回单元格区域地址（X轴标签）
     */
    
    public io.nop.core.lang.eval.IEvalFunction getCategoryCellRefExpr(){
      return _categoryCellRefExpr;
    }

    
    public void setCategoryCellRefExpr(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._categoryCellRefExpr = value;
           
    }

    
    /**
     * 
     * xml name: categoryExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getCategoryExpr(){
      return _categoryExpr;
    }

    
    public void setCategoryExpr(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._categoryExpr = value;
           
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

    
    /**
     * 
     * xml name: valuesCellRefExpr
     *  系列数值范围引用，返回单元格区域地址
     */
    
    public io.nop.core.lang.eval.IEvalFunction getValuesCellRefExpr(){
      return _valuesCellRefExpr;
    }

    
    public void setValuesCellRefExpr(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._valuesCellRefExpr = value;
           
    }

    
    /**
     * 
     * xml name: valuesExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getValuesExpr(){
      return _valuesExpr;
    }

    
    public void setValuesExpr(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._valuesExpr = value;
           
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
        
        out.putNotNull("categoryCellRefExpr",this.getCategoryCellRefExpr());
        out.putNotNull("categoryExpr",this.getCategoryExpr());
        out.putNotNull("chartTitleCellRefExpr",this.getChartTitleCellRefExpr());
        out.putNotNull("chartTitleExpr",this.getChartTitleExpr());
        out.putNotNull("seriesNameCellRefExpr",this.getSeriesNameCellRefExpr());
        out.putNotNull("seriesNameExpr",this.getSeriesNameExpr());
        out.putNotNull("valuesCellRefExpr",this.getValuesCellRefExpr());
        out.putNotNull("valuesExpr",this.getValuesExpr());
    }

    public ChartDynamicBindingsModel cloneInstance(){
        ChartDynamicBindingsModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartDynamicBindingsModel instance){
        super.copyTo(instance);
        
        instance.setCategoryCellRefExpr(this.getCategoryCellRefExpr());
        instance.setCategoryExpr(this.getCategoryExpr());
        instance.setChartTitleCellRefExpr(this.getChartTitleCellRefExpr());
        instance.setChartTitleExpr(this.getChartTitleExpr());
        instance.setSeriesNameCellRefExpr(this.getSeriesNameCellRefExpr());
        instance.setSeriesNameExpr(this.getSeriesNameExpr());
        instance.setValuesCellRefExpr(this.getValuesCellRefExpr());
        instance.setValuesExpr(this.getValuesExpr());
    }

    protected ChartDynamicBindingsModel newInstance(){
        return (ChartDynamicBindingsModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
