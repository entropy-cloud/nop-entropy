package io.nop.excel.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.model.ExcelRichText;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [82:34:0:0]/nop/schema/excel/workbook.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ExcelRichText extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: 
     * 
     */
    private java.util.List<io.nop.excel.model.ExcelRichTextPart> _parts = java.util.Collections.emptyList();
    
    /**
     * 
     * xml name: 
     *  
     */
    
    public java.util.List<io.nop.excel.model.ExcelRichTextPart> getParts(){
      return _parts;
    }

    
    public void setParts(java.util.List<io.nop.excel.model.ExcelRichTextPart> value){
        checkAllowChange();
        
        this._parts = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._parts = io.nop.api.core.util.FreezeHelper.deepFreeze(this._parts);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("parts",this.getParts());
    }

    public ExcelRichText cloneInstance(){
        ExcelRichText instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ExcelRichText instance){
        super.copyTo(instance);
        
        instance.setParts(this.getParts());
    }

    protected ExcelRichText newInstance(){
        return (ExcelRichText) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
