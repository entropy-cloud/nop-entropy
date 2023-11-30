package io.nop.excel.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [37:14:0:0]/nop/schema/excel/workbook.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _ExcelBorderStyle extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: color
     * 
     */
    private java.lang.String _color ;
    
    /**
     *  
     * xml name: type
     * 
     */
    private io.nop.excel.model.constants.ExcelLineStyle _type ;
    
    /**
     *  
     * xml name: weight
     * 
     */
    private int _weight  = 0;
    
    /**
     * 
     * xml name: color
     *  
     */
    
    public java.lang.String getColor(){
      return _color;
    }

    
    public void setColor(java.lang.String value){
        checkAllowChange();
        
        this._color = value;
           
    }

    
    /**
     * 
     * xml name: type
     *  
     */
    
    public io.nop.excel.model.constants.ExcelLineStyle getType(){
      return _type;
    }

    
    public void setType(io.nop.excel.model.constants.ExcelLineStyle value){
        checkAllowChange();
        
        this._type = value;
           
    }

    
    /**
     * 
     * xml name: weight
     *  
     */
    
    public int getWeight(){
      return _weight;
    }

    
    public void setWeight(int value){
        checkAllowChange();
        
        this._weight = value;
           
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
        
        out.put("color",this.getColor());
        out.put("type",this.getType());
        out.put("weight",this.getWeight());
    }
}
 // resume CPD analysis - CPD-ON
