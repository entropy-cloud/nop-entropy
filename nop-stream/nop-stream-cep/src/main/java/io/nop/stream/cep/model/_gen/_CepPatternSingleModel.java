package io.nop.stream.cep.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [34:6:0:0]/nop/schema/stream/pattern.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public abstract class _CepPatternSingleModel extends io.nop.stream.cep.model.CepPatternPartModel {
    
    /**
     *  
     * xml name: until
     * 仅适用于oneOrMore（）
     */
    private io.nop.core.lang.eval.IEvalFunction _until ;
    
    /**
     *  
     * xml name: where
     * 具有上下文变量event, Context context
     */
    private io.nop.core.lang.eval.IEvalFunction _where ;
    
    /**
     * 
     * xml name: until
     *  仅适用于oneOrMore（）
     */
    
    public io.nop.core.lang.eval.IEvalFunction getUntil(){
      return _until;
    }

    
    public void setUntil(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._until = value;
           
    }

    
    /**
     * 
     * xml name: where
     *  具有上下文变量event, Context context
     */
    
    public io.nop.core.lang.eval.IEvalFunction getWhere(){
      return _where;
    }

    
    public void setWhere(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._where = value;
           
    }

    

    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
        }
    }

    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("until",this.getUntil());
        out.put("where",this.getWhere());
    }
}
 // resume CPD analysis - CPD-ON
