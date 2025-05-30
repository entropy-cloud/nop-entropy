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
     * xml name: repeatCountExpr
     * 返回body行的循环次数
     */
    private io.nop.core.lang.eval.IEvalFunction _repeatCountExpr ;
    
    /**
     *  
     * xml name: repeatCountField
     * 定长记录的定义
     */
    private io.nop.record.model.RecordSimpleFieldMeta _repeatCountField ;
    
    /**
     *  
     * xml name: repeatKind
     * 
     */
    private io.nop.record.model.FieldRepeatKind _repeatKind ;
    
    /**
     *  
     * xml name: repeatUntil
     * 返回body行循环的终止条件
     */
    private io.nop.core.lang.eval.IEvalFunction _repeatUntil ;
    
    /**
     * 
     * xml name: repeatCountExpr
     *  返回body行的循环次数
     */
    
    public io.nop.core.lang.eval.IEvalFunction getRepeatCountExpr(){
      return _repeatCountExpr;
    }

    
    public void setRepeatCountExpr(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._repeatCountExpr = value;
           
    }

    
    /**
     * 
     * xml name: repeatCountField
     *  定长记录的定义
     */
    
    public io.nop.record.model.RecordSimpleFieldMeta getRepeatCountField(){
      return _repeatCountField;
    }

    
    public void setRepeatCountField(io.nop.record.model.RecordSimpleFieldMeta value){
        checkAllowChange();
        
        this._repeatCountField = value;
           
    }

    
    /**
     * 
     * xml name: repeatKind
     *  
     */
    
    public io.nop.record.model.FieldRepeatKind getRepeatKind(){
      return _repeatKind;
    }

    
    public void setRepeatKind(io.nop.record.model.FieldRepeatKind value){
        checkAllowChange();
        
        this._repeatKind = value;
           
    }

    
    /**
     * 
     * xml name: repeatUntil
     *  返回body行循环的终止条件
     */
    
    public io.nop.core.lang.eval.IEvalFunction getRepeatUntil(){
      return _repeatUntil;
    }

    
    public void setRepeatUntil(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._repeatUntil = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._repeatCountField = io.nop.api.core.util.FreezeHelper.deepFreeze(this._repeatCountField);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("repeatCountExpr",this.getRepeatCountExpr());
        out.putNotNull("repeatCountField",this.getRepeatCountField());
        out.putNotNull("repeatKind",this.getRepeatKind());
        out.putNotNull("repeatUntil",this.getRepeatUntil());
    }

    public RecordFileBodyMeta cloneInstance(){
        RecordFileBodyMeta instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(RecordFileBodyMeta instance){
        super.copyTo(instance);
        
        instance.setRepeatCountExpr(this.getRepeatCountExpr());
        instance.setRepeatCountField(this.getRepeatCountField());
        instance.setRepeatKind(this.getRepeatKind());
        instance.setRepeatUntil(this.getRepeatUntil());
    }

    protected RecordFileBodyMeta newInstance(){
        return (RecordFileBodyMeta) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
