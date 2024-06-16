package io.nop.stream.cep.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.stream.cep.model.CepPatternSingleModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/stream/pattern.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
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
        
        out.putNotNull("until",this.getUntil());
        out.putNotNull("where",this.getWhere());
    }

    public CepPatternSingleModel cloneInstance(){
        CepPatternSingleModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(CepPatternSingleModel instance){
        super.copyTo(instance);
        
        instance.setUntil(this.getUntil());
        instance.setWhere(this.getWhere());
    }

    protected CepPatternSingleModel newInstance(){
        return (CepPatternSingleModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
