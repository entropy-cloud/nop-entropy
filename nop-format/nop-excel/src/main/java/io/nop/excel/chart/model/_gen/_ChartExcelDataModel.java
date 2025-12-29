package io.nop.excel.chart.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.chart.model.ChartExcelDataModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/chart.xdef <p>
 * Excel cell/range reference
 * 对应 Excel POI 中的 CellRangeAddress 和 AreaReference
 * 支持 Excel 单元格引用格式如 A1, B2:C10
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ChartExcelDataModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: cellRangeRef
     * 
     */
    private java.lang.String _cellRangeRef ;
    
    /**
     *  
     * xml name: sheetName
     * 
     */
    private java.lang.String _sheetName ;
    
    /**
     * 
     * xml name: cellRangeRef
     *  
     */
    
    public java.lang.String getCellRangeRef(){
      return _cellRangeRef;
    }

    
    public void setCellRangeRef(java.lang.String value){
        checkAllowChange();
        
        this._cellRangeRef = value;
           
    }

    
    /**
     * 
     * xml name: sheetName
     *  
     */
    
    public java.lang.String getSheetName(){
      return _sheetName;
    }

    
    public void setSheetName(java.lang.String value){
        checkAllowChange();
        
        this._sheetName = value;
           
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
        
        out.putNotNull("cellRangeRef",this.getCellRangeRef());
        out.putNotNull("sheetName",this.getSheetName());
    }

    public ChartExcelDataModel cloneInstance(){
        ChartExcelDataModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ChartExcelDataModel instance){
        super.copyTo(instance);
        
        instance.setCellRangeRef(this.getCellRangeRef());
        instance.setSheetName(this.getSheetName());
    }

    protected ChartExcelDataModel newInstance(){
        return (ChartExcelDataModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
