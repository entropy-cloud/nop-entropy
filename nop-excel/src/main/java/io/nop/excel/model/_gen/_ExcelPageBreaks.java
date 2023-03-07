package io.nop.excel.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [177:14:0:0]/nop/schema/excel/workbook.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _ExcelPageBreaks extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: cols
     * 
     */
    private java.util.List<java.lang.Integer> _cols ;
    
    /**
     *  
     * xml name: rows
     * 
     */
    private java.util.List<java.lang.Integer> _rows ;
    
    /**
     * 
     * xml name: cols
     *  
     */
    
    public java.util.List<java.lang.Integer> getCols(){
      return _cols;
    }

    
    public void setCols(java.util.List<java.lang.Integer> value){
        checkAllowChange();
        
        this._cols = value;
           
    }

    
    /**
     * 
     * xml name: rows
     *  
     */
    
    public java.util.List<java.lang.Integer> getRows(){
      return _rows;
    }

    
    public void setRows(java.util.List<java.lang.Integer> value){
        checkAllowChange();
        
        this._rows = value;
           
    }

    

    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
        }
    }

    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("cols",this.getCols());
        out.put("rows",this.getRows());
    }
}
 // resume CPD analysis - CPD-ON
