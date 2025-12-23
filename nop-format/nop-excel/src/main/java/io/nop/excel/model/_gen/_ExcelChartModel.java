package io.nop.excel.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.model.ExcelChartModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/workbook.xdef <p>
 * Enhanced Chart Metamodel Definition
 * Supports comprehensive chart configuration for Apache POI, PDF, and ECharts output formats
 * 参考 Apache POI XSSFChart 和 Excel Chart API 设计
 * 对应 Excel 中的 Chart 对象和相关属性
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ExcelChartModel extends io.nop.excel.chart.model.ChartModel {
    
    /**
     *  
     * xml name: anchor
     * 
     */
    private io.nop.excel.model.ExcelClientAnchor _anchor ;
    
    /**
     *  
     * xml name: testExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalPredicate _testExpr ;
    
    /**
     * 
     * xml name: anchor
     *  
     */
    
    public io.nop.excel.model.ExcelClientAnchor getAnchor(){
      return _anchor;
    }

    
    public void setAnchor(io.nop.excel.model.ExcelClientAnchor value){
        checkAllowChange();
        
        this._anchor = value;
           
    }

    
    /**
     * 
     * xml name: testExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalPredicate getTestExpr(){
      return _testExpr;
    }

    
    public void setTestExpr(io.nop.core.lang.eval.IEvalPredicate value){
        checkAllowChange();
        
        this._testExpr = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._anchor = io.nop.api.core.util.FreezeHelper.deepFreeze(this._anchor);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("anchor",this.getAnchor());
        out.putNotNull("testExpr",this.getTestExpr());
    }

    public ExcelChartModel cloneInstance(){
        ExcelChartModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ExcelChartModel instance){
        super.copyTo(instance);
        
        instance.setAnchor(this.getAnchor());
        instance.setTestExpr(this.getTestExpr());
    }

    protected ExcelChartModel newInstance(){
        return (ExcelChartModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
