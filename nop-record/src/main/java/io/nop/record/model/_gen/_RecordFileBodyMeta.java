package io.nop.record.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.record.model.RecordFileBodyMeta;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/record/record-file.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _RecordFileBodyMeta extends io.nop.record.model.RecordObjectMeta {
    
    /**
     *  
     * xml name: readRepeatExpr
     * 返回body行的循环次数
     */
    private io.nop.core.lang.eval.IEvalFunction _readRepeatExpr ;
    
    /**
     *  
     * xml name: readRepeatKind
     * 
     */
    private io.nop.record.model.FieldRepeatKind _readRepeatKind ;
    
    /**
     *  
     * xml name: readRepeatUntil
     * 返回body行循环的终止条件
     */
    private io.nop.core.lang.eval.IEvalFunction _readRepeatUntil ;
    
    /**
     * 
     * xml name: readRepeatExpr
     *  返回body行的循环次数
     */
    
    public io.nop.core.lang.eval.IEvalFunction getReadRepeatExpr(){
      return _readRepeatExpr;
    }

    
    public void setReadRepeatExpr(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._readRepeatExpr = value;
           
    }

    
    /**
     * 
     * xml name: readRepeatKind
     *  
     */
    
    public io.nop.record.model.FieldRepeatKind getReadRepeatKind(){
      return _readRepeatKind;
    }

    
    public void setReadRepeatKind(io.nop.record.model.FieldRepeatKind value){
        checkAllowChange();
        
        this._readRepeatKind = value;
           
    }

    
    /**
     * 
     * xml name: readRepeatUntil
     *  返回body行循环的终止条件
     */
    
    public io.nop.core.lang.eval.IEvalFunction getReadRepeatUntil(){
      return _readRepeatUntil;
    }

    
    public void setReadRepeatUntil(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._readRepeatUntil = value;
           
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
        
        out.putNotNull("readRepeatExpr",this.getReadRepeatExpr());
        out.putNotNull("readRepeatKind",this.getReadRepeatKind());
        out.putNotNull("readRepeatUntil",this.getReadRepeatUntil());
    }

    public RecordFileBodyMeta cloneInstance(){
        RecordFileBodyMeta instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(RecordFileBodyMeta instance){
        super.copyTo(instance);
        
        instance.setReadRepeatExpr(this.getReadRepeatExpr());
        instance.setReadRepeatKind(this.getReadRepeatKind());
        instance.setReadRepeatUntil(this.getReadRepeatUntil());
    }

    protected RecordFileBodyMeta newInstance(){
        return (RecordFileBodyMeta) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
