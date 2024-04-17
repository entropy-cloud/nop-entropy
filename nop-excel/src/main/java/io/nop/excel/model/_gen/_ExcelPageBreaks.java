package io.nop.excel.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.model.ExcelPageBreaks;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [219:14:0:0]/nop/schema/excel/workbook.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
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
        
        out.putNotNull("cols",this.getCols());
        out.putNotNull("rows",this.getRows());
    }

    public ExcelPageBreaks cloneInstance(){
        ExcelPageBreaks instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ExcelPageBreaks instance){
        super.copyTo(instance);
        
        instance.setCols(this.getCols());
        instance.setRows(this.getRows());
    }

    protected ExcelPageBreaks newInstance(){
        return (ExcelPageBreaks) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
