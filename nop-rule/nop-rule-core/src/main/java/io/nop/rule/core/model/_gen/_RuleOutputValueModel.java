package io.nop.rule.core.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [60:22:0:0]/nop/schema/rule.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116"})
public abstract class _RuleOutputValueModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: valueExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _valueExpr ;
    
    /**
     * 
     * xml name: name
     *  
     */
    
    public java.lang.String getName(){
      return _name;
    }

    
    public void setName(java.lang.String value){
        checkAllowChange();
        
        this._name = value;
           
    }

    
    /**
     * 
     * xml name: valueExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getValueExpr(){
      return _valueExpr;
    }

    
    public void setValueExpr(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._valueExpr = value;
           
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
        
        out.put("name",this.getName());
        out.put("valueExpr",this.getValueExpr());
    }
}
 // resume CPD analysis - CPD-ON
