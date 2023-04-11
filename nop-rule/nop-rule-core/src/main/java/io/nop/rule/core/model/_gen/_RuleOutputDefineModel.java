package io.nop.rule.core.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [39:10:0:0]/nop/schema/rule.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _RuleOutputDefineModel extends io.nop.xlang.xmeta.ObjVarDefineModel {
    
    /**
     *  
     * xml name: aggreate
     * 
     */
    private java.lang.String _aggreate ;
    
    /**
     * 
     * xml name: aggreate
     *  
     */
    
    public java.lang.String getAggreate(){
      return _aggreate;
    }

    
    public void setAggreate(java.lang.String value){
        checkAllowChange();
        
        this._aggreate = value;
           
    }

    

    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
        }
    }

    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("aggreate",this.getAggreate());
    }
}
 // resume CPD analysis - CPD-ON
