package io.nop.excel.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.model.ExcelColumnConfig;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [61:22:0:0]/nop/schema/excel/workbook.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ExcelColumnConfig extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: hidden
     * 
     */
    private boolean _hidden  = false;
    
    /**
     *  
     * xml name: styleId
     * 
     */
    private java.lang.String _styleId ;
    
    /**
     *  
     * xml name: styleIdExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _styleIdExpr ;
    
    /**
     *  列宽
     * xml name: width
     * 列的宽度，单位为pt
     */
    private java.lang.Double _width ;
    
    /**
     * 
     * xml name: hidden
     *  
     */
    
    public boolean isHidden(){
      return _hidden;
    }

    
    public void setHidden(boolean value){
        checkAllowChange();
        
        this._hidden = value;
           
    }

    
    /**
     * 
     * xml name: styleId
     *  
     */
    
    public java.lang.String getStyleId(){
      return _styleId;
    }

    
    public void setStyleId(java.lang.String value){
        checkAllowChange();
        
        this._styleId = value;
           
    }

    
    /**
     * 
     * xml name: styleIdExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getStyleIdExpr(){
      return _styleIdExpr;
    }

    
    public void setStyleIdExpr(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._styleIdExpr = value;
           
    }

    
    /**
     * 列宽
     * xml name: width
     *  列的宽度，单位为pt
     */
    
    public java.lang.Double getWidth(){
      return _width;
    }

    
    public void setWidth(java.lang.Double value){
        checkAllowChange();
        
        this._width = value;
           
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
        
        out.put("hidden",this.isHidden());
        out.put("styleId",this.getStyleId());
        out.put("styleIdExpr",this.getStyleIdExpr());
        out.put("width",this.getWidth());
    }

    public ExcelColumnConfig cloneInstance(){
        ExcelColumnConfig instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ExcelColumnConfig instance){
        super.copyTo(instance);
        
        instance.setHidden(this.isHidden());
        instance.setStyleId(this.getStyleId());
        instance.setStyleIdExpr(this.getStyleIdExpr());
        instance.setWidth(this.getWidth());
    }

    protected ExcelColumnConfig newInstance(){
        return (ExcelColumnConfig) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
