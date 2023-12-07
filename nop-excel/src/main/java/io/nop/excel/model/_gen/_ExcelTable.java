package io.nop.excel.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [53:14:0:0]/nop/schema/excel/workbook.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116"})
public abstract class _ExcelTable extends io.nop.core.model.table.impl.AbstractTable<io.nop.excel.model.ExcelRow> {
    
    /**
     *  
     * xml name: cols
     * 
     */
    private java.util.List<io.nop.excel.model.ExcelColumnConfig> _cols = java.util.Collections.emptyList();
    
    /**
     *  
     * xml name: rows
     * 
     */
    private java.util.List<io.nop.excel.model.ExcelRow> _rows = java.util.Collections.emptyList();
    
    /**
     * 
     * xml name: cols
     *  
     */
    
    public java.util.List<io.nop.excel.model.ExcelColumnConfig> getCols(){
      return _cols;
    }

    
    public void setCols(java.util.List<io.nop.excel.model.ExcelColumnConfig> value){
        checkAllowChange();
        
        this._cols = value;
           
    }

    
    /**
     * 
     * xml name: rows
     *  
     */
    
    public java.util.List<io.nop.excel.model.ExcelRow> getRows(){
      return _rows;
    }

    
    public void setRows(java.util.List<io.nop.excel.model.ExcelRow> value){
        checkAllowChange();
        
        this._rows = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._cols = io.nop.api.core.util.FreezeHelper.deepFreeze(this._cols);
            
           this._rows = io.nop.api.core.util.FreezeHelper.deepFreeze(this._rows);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("cols",this.getCols());
        out.put("rows",this.getRows());
    }
}
 // resume CPD analysis - CPD-ON
