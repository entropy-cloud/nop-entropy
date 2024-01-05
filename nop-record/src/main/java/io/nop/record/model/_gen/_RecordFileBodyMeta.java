package io.nop.record.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.record.model.RecordFileBodyMeta;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [44:6:0:0]/nop/schema/record/record-file.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _RecordFileBodyMeta extends io.nop.record.model.RecordObjectMeta {
    
    /**
     *  
     * xml name: repeat
     * 
     */
    private io.nop.record.model.FieldRepeatKind _repeat ;
    
    /**
     *  
     * xml name: repeatExpr
     * 返回字段的循环次数
     */
    private io.nop.core.lang.eval.IEvalAction _repeatExpr ;
    
    /**
     *  
     * xml name: repeatUntil
     * 返回字段循环的终止条件
     */
    private io.nop.core.lang.eval.IEvalAction _repeatUntil ;
    
    /**
     * 
     * xml name: repeat
     *  
     */
    
    public io.nop.record.model.FieldRepeatKind getRepeat(){
      return _repeat;
    }

    
    public void setRepeat(io.nop.record.model.FieldRepeatKind value){
        checkAllowChange();
        
        this._repeat = value;
           
    }

    
    /**
     * 
     * xml name: repeatExpr
     *  返回字段的循环次数
     */
    
    public io.nop.core.lang.eval.IEvalAction getRepeatExpr(){
      return _repeatExpr;
    }

    
    public void setRepeatExpr(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._repeatExpr = value;
           
    }

    
    /**
     * 
     * xml name: repeatUntil
     *  返回字段循环的终止条件
     */
    
    public io.nop.core.lang.eval.IEvalAction getRepeatUntil(){
      return _repeatUntil;
    }

    
    public void setRepeatUntil(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._repeatUntil = value;
           
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
        
        out.put("repeat",this.getRepeat());
        out.put("repeatExpr",this.getRepeatExpr());
        out.put("repeatUntil",this.getRepeatUntil());
    }

    public RecordFileBodyMeta cloneInstance(){
        RecordFileBodyMeta instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(RecordFileBodyMeta instance){
        super.copyTo(instance);
        
        instance.setRepeat(this.getRepeat());
        instance.setRepeatExpr(this.getRepeatExpr());
        instance.setRepeatUntil(this.getRepeatUntil());
    }

    protected RecordFileBodyMeta newInstance(){
        return (RecordFileBodyMeta) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
