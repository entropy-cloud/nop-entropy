package io.nop.excel.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.model.ExcelAnnotation;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/workbook.xdef <p>
 * 行区间的类型标注。例如标记表头，表旁，表尾等
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
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
        
        out.putNotNull("range",this.getRange());
        out.putNotNull("type",this.getType());
    }

    public ExcelAnnotation cloneInstance(){
        ExcelAnnotation instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ExcelAnnotation instance){
        super.copyTo(instance);
        
        instance.setRange(this.getRange());
        instance.setType(this.getType());
    }

    protected ExcelAnnotation newInstance(){
        return (ExcelAnnotation) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
