package io.nop.excel.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.model.ExcelSheetOptions;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [186:14:0:0]/nop/schema/excel/workbook.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ExcelSheetOptions extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: fitToPage
     * 
     */
    private boolean _fitToPage  = false;
    
    /**
     *  
     * xml name: splitHorizontal
     * 
     */
    private java.lang.Integer _splitHorizontal ;
    
    /**
     *  
     * xml name: splitVertical
     * 
     */
    private java.lang.Integer _splitVertical ;
    
    /**
     * 
     * xml name: fitToPage
     *  
     */
    
    public boolean isFitToPage(){
      return _fitToPage;
    }

    
    public void setFitToPage(boolean value){
        checkAllowChange();
        
        this._fitToPage = value;
           
    }

    
    /**
     * 
     * xml name: splitHorizontal
     *  
     */
    
    public java.lang.Integer getSplitHorizontal(){
      return _splitHorizontal;
    }

    
    public void setSplitHorizontal(java.lang.Integer value){
        checkAllowChange();
        
        this._splitHorizontal = value;
           
    }

    
    /**
     * 
     * xml name: splitVertical
     *  
     */
    
    public java.lang.Integer getSplitVertical(){
      return _splitVertical;
    }

    
    public void setSplitVertical(java.lang.Integer value){
        checkAllowChange();
        
        this._splitVertical = value;
           
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
        
        out.putNotNull("fitToPage",this.isFitToPage());
        out.putNotNull("splitHorizontal",this.getSplitHorizontal());
        out.putNotNull("splitVertical",this.getSplitVertical());
    }

    public ExcelSheetOptions cloneInstance(){
        ExcelSheetOptions instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ExcelSheetOptions instance){
        super.copyTo(instance);
        
        instance.setFitToPage(this.isFitToPage());
        instance.setSplitHorizontal(this.getSplitHorizontal());
        instance.setSplitVertical(this.getSplitVertical());
    }

    protected ExcelSheetOptions newInstance(){
        return (ExcelSheetOptions) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
