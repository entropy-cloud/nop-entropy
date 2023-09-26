package io.nop.excel.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [157:18:0:0]/nop/schema/excel/workbook.xdef <p>
 * 行区间的类型标注。例如标记表头，表旁，表尾等
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _ExcelAnnotation extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: range
     * 
     */
    private java.lang.String _range ;
    
    /**
     *  
     * xml name: type
     * 
     */
    private java.lang.String _type ;
    
    /**
     * 
     * xml name: range
     *  
     */
    
    public java.lang.String getRange(){
      return _range;
    }

    
    public void setRange(java.lang.String value){
        checkAllowChange();
        
        this._range = value;
           
    }

    
    /**
     * 
     * xml name: type
     *  
     */
    
    public java.lang.String getType(){
      return _type;
    }

    
    public void setType(java.lang.String value){
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
        
        out.put("range",this.getRange());
        out.put("type",this.getType());
    }
}
 // resume CPD analysis - CPD-ON
