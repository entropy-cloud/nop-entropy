package io.nop.excel.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.model.ExcelBorderStyle;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/style.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ExcelBorderStyle extends io.nop.office.model.OfficeBorderStyle {
    

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
        
    }

    public ExcelBorderStyle cloneInstance(){
        ExcelBorderStyle instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ExcelBorderStyle instance){
        super.copyTo(instance);
        
    }

    protected ExcelBorderStyle newInstance(){
        return (ExcelBorderStyle) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
