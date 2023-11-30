package io.nop.excel.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [184:14:0:0]/nop/schema/excel/workbook.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
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
        
        out.put("fitToPage",this.isFitToPage());
        out.put("splitHorizontal",this.getSplitHorizontal());
        out.put("splitVertical",this.getSplitVertical());
    }
}
 // resume CPD analysis - CPD-ON
