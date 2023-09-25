package io.nop.excel.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [130:22:0:0]/nop/schema/excel/workbook.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _ExcelClientAnchor extends io.nop.core.model.table.CellAnchor {
    
    /**
     *  
     * xml name: type
     * 
     */
    private io.nop.excel.model.constants.ExcelAnchorType _type ;
    
    /**
     * 
     * xml name: type
     *  
     */
    
    public io.nop.excel.model.constants.ExcelAnchorType getType(){
      return _type;
    }

    
    public void setType(io.nop.excel.model.constants.ExcelAnchorType value){
        checkAllowChange();
        
        this._type = value;
           
    }

    

    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
        }
    }

    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("type",this.getType());
    }
}
 // resume CPD analysis - CPD-ON
