package io.nop.excel.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.model.ExcelClientAnchor;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/workbook.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
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
        
        out.putNotNull("type",this.getType());
    }

    public ExcelClientAnchor cloneInstance(){
        ExcelClientAnchor instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ExcelClientAnchor instance){
        super.copyTo(instance);
        
        instance.setType(this.getType());
    }

    protected ExcelClientAnchor newInstance(){
        return (ExcelClientAnchor) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
