package io.nop.record.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [36:10:0:0]/nop/schema/record/record-file.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _RecordComputedFieldMeta extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: compute-expr
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _computeExpr ;
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: type
     * 
     */
    private java.lang.String _type ;
    
    /**
     * 
     * xml name: compute-expr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalAction getComputeExpr(){
      return _computeExpr;
    }

    
    public void setComputeExpr(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._computeExpr = value;
           
    }

    
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
        
        out.put("computeExpr",this.getComputeExpr());
        out.put("name",this.getName());
        out.put("type",this.getType());
    }
}
 // resume CPD analysis - CPD-ON
