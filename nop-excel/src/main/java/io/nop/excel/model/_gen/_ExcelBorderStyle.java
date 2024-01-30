package io.nop.excel.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.model.ExcelBorderStyle;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [37:14:0:0]/nop/schema/excel/workbook.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
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
        
        out.putNotNull("color",this.getColor());
        out.putNotNull("type",this.getType());
        out.putNotNull("weight",this.getWeight());
    }

    public ExcelBorderStyle cloneInstance(){
        ExcelBorderStyle instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ExcelBorderStyle instance){
        super.copyTo(instance);
        
        instance.setColor(this.getColor());
        instance.setType(this.getType());
        instance.setWeight(this.getWeight());
    }

    protected ExcelBorderStyle newInstance(){
        return (ExcelBorderStyle) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
