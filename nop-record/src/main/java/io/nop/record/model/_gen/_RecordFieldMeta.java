package io.nop.record.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.record.model.RecordFieldMeta;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/record/record-field.xdef <p>
 * 定长记录的定义
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _RecordFieldMeta extends io.nop.record.model.RecordSimpleFieldMeta {
    
    /**
     *  
     * xml name: fields
     * 
     */
    private KeyedList<io.nop.record.model.RecordFieldMeta> _fields = KeyedList.emptyList();
    
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
     * xml name: switch
     * 动态确定字段类型
     */
    private io.nop.record.model.RecordFieldSwitch _switch ;
    
    /**
     * 
     * xml name: fields
     *  
     */
    
    public java.util.List<io.nop.record.model.RecordFieldMeta> getFields(){
      return _fields;
    }

    
    public void setFields(java.util.List<io.nop.record.model.RecordFieldMeta> value){
        checkAllowChange();
        
        this._fields = KeyedList.fromList(value, io.nop.record.model.RecordFieldMeta::getName);
           
    }

    
    public io.nop.record.model.RecordFieldMeta getField(String name){
        return this._fields.getByKey(name);
    }

    public boolean hasField(String name){
        return this._fields.containsKey(name);
    }

    public void addField(io.nop.record.model.RecordFieldMeta item) {
        checkAllowChange();
        java.util.List<io.nop.record.model.RecordFieldMeta> list = this.getFields();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.record.model.RecordFieldMeta::getName);
            setFields(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_fields(){
        return this._fields.keySet();
    }

    public boolean hasFields(){
        return !this._fields.isEmpty();
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

    
    /**
     * 
     * xml name: switch
     *  动态确定字段类型
     */
    
    public io.nop.record.model.RecordFieldSwitch getSwitch(){
      return _switch;
    }

    
    public void setSwitch(io.nop.record.model.RecordFieldSwitch value){
        checkAllowChange();
        
        this._switch = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._fields = io.nop.api.core.util.FreezeHelper.deepFreeze(this._fields);
            
           this._switch = io.nop.api.core.util.FreezeHelper.deepFreeze(this._switch);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("fields",this.getFields());
        out.putNotNull("repeatExpr",this.getRepeatExpr());
        out.putNotNull("repeatUntil",this.getRepeatUntil());
        out.putNotNull("switch",this.getSwitch());
    }

    public RecordFieldMeta cloneInstance(){
        RecordFieldMeta instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(RecordFieldMeta instance){
        super.copyTo(instance);
        
        instance.setFields(this.getFields());
        instance.setRepeatExpr(this.getRepeatExpr());
        instance.setRepeatUntil(this.getRepeatUntil());
        instance.setSwitch(this.getSwitch());
    }

    protected RecordFieldMeta newInstance(){
        return (RecordFieldMeta) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
