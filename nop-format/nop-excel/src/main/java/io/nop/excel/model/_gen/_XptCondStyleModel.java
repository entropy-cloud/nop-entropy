package io.nop.excel.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.excel.model.XptCondStyleModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/excel/excel-table.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _XptCondStyleModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: expr
     * 
     */
    private io.nop.core.lang.eval.IEvalPredicate _expr ;
    
    /**
     *  
     * xml name: max
     * 
     */
    private java.lang.Double _max ;
    
    /**
     *  
     * xml name: min
     * 
     */
    private java.lang.Double _min ;
    
    /**
     *  
     * xml name: styleId
     * 
     */
    private java.lang.String _styleId ;
    
    /**
     * 
     * xml name: expr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalPredicate getExpr(){
      return _expr;
    }

    
    public void setExpr(io.nop.core.lang.eval.IEvalPredicate value){
        checkAllowChange();
        
        this._expr = value;
           
    }

    
    /**
     * 
     * xml name: max
     *  
     */
    
    public java.lang.Double getMax(){
      return _max;
    }

    
    public void setMax(java.lang.Double value){
        checkAllowChange();
        
        this._max = value;
           
    }

    
    /**
     * 
     * xml name: min
     *  
     */
    
    public java.lang.Double getMin(){
      return _min;
    }

    
    public void setMin(java.lang.Double value){
        checkAllowChange();
        
        this._min = value;
           
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
        
        out.putNotNull("expr",this.getExpr());
        out.putNotNull("max",this.getMax());
        out.putNotNull("min",this.getMin());
        out.putNotNull("styleId",this.getStyleId());
    }

    public XptCondStyleModel cloneInstance(){
        XptCondStyleModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(XptCondStyleModel instance){
        super.copyTo(instance);
        
        instance.setExpr(this.getExpr());
        instance.setMax(this.getMax());
        instance.setMin(this.getMin());
        instance.setStyleId(this.getStyleId());
    }

    protected XptCondStyleModel newInstance(){
        return (XptCondStyleModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
