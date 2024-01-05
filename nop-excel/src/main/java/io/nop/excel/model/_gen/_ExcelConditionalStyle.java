package io.nop.excel.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.model.ExcelConditionalStyle;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [171:18:0:0]/nop/schema/excel/workbook.xdef <p>
 * 当条件满足时，将对指定区间单元格的样式进行增量修改
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ExcelConditionalStyle extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: range
     * 
     */
    private java.lang.String _range ;
    
    /**
     *  
     * xml name: style
     * 
     */
    private io.nop.excel.model.ExcelStyle _style ;
    
    /**
     *  
     * xml name: when
     * 
     */
    private io.nop.api.core.beans.TreeBean _when ;
    
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
     * xml name: style
     *  
     */
    
    public io.nop.excel.model.ExcelStyle getStyle(){
      return _style;
    }

    
    public void setStyle(io.nop.excel.model.ExcelStyle value){
        checkAllowChange();
        
        this._style = value;
           
    }

    
    /**
     * 
     * xml name: when
     *  
     */
    
    public io.nop.api.core.beans.TreeBean getWhen(){
      return _when;
    }

    
    public void setWhen(io.nop.api.core.beans.TreeBean value){
        checkAllowChange();
        
        this._when = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._style = io.nop.api.core.util.FreezeHelper.deepFreeze(this._style);
            
           this._when = io.nop.api.core.util.FreezeHelper.deepFreeze(this._when);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("range",this.getRange());
        out.put("style",this.getStyle());
        out.put("when",this.getWhen());
    }

    public ExcelConditionalStyle cloneInstance(){
        ExcelConditionalStyle instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ExcelConditionalStyle instance){
        super.copyTo(instance);
        
        instance.setRange(this.getRange());
        instance.setStyle(this.getStyle());
        instance.setWhen(this.getWhen());
    }

    protected ExcelConditionalStyle newInstance(){
        return (ExcelConditionalStyle) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
