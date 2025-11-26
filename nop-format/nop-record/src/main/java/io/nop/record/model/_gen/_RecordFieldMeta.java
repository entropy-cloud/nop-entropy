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
     * xml name: afterRead
     * 在所有子字段都读取到之后执行
     */
    private io.nop.core.lang.eval.IEvalFunction _afterRead ;
    
    /**
     *  
     * xml name: afterWrite
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _afterWrite ;
    
    /**
     *  
     * xml name: beforeRead
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _beforeRead ;
    
    /**
     *  
     * xml name: beforeWrite
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _beforeWrite ;
    
    /**
     *  
     * xml name: repeatCountExpr
     * 返回字段的循环次数
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
     * xml name: repeatCountFieldName
     * 
     */
    private java.lang.String _repeatCountFieldName ;
    
    /**
     *  
     * xml name: repeatKind
     * 如果是列表结构或者Map结构，则这里用来确定如何判断所有条目已经解析完毕
     */
    private io.nop.record.model.FieldRepeatKind _repeatKind ;
    
    /**
     *  
     * xml name: repeatUntil
     * 返回字段循环的终止条件
     */
    private io.nop.core.lang.eval.IEvalFunction _repeatUntil ;
    
    /**
     *  
     * xml name: supportStreaming
     * 当字段类型为集合类型时，如果设置了supportStreaming，则流式解析的时候可以每次只返回一个StreamingItem。
     */
    private boolean _supportStreaming  = false;
    
    /**
     *  
     * xml name: switchOnExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _switchOnExpr ;
    
    /**
     *  
     * xml name: switchOnField
     * 动态确定字段类型
     * 如果指定了switchOnField，则输出时根据从record[switchOnField]上获取到case类型，然后再映射到type类型，从根对象的types集合中再获取具体定义
     */
    private java.lang.String _switchOnField ;
    
    /**
     *  
     * xml name: switchOnRule
     * 
     */
    private io.nop.record.match.IPeekMatchRule _switchOnRule ;
    
    /**
     *  
     * xml name: switchTypeMap
     * 根据record[switchOnField]或者switchOnRule获取到类型，key为*表示缺省映射
     */
    private java.util.Map<java.lang.String,java.lang.String> _switchTypeMap ;
    
    /**
     *  
     * xml name: tagIndex
     * 
     */
    private int _tagIndex  = 0;
    
    /**
     *  
     * xml name: typeRef
     * 
     */
    private java.lang.String _typeRef ;
    
    /**
     * 
     * xml name: afterRead
     *  在所有子字段都读取到之后执行
     */
    
    public io.nop.core.lang.eval.IEvalFunction getAfterRead(){
      return _afterRead;
    }

    
    public void setAfterRead(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._afterRead = value;
           
    }

    
    /**
     * 
     * xml name: afterWrite
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getAfterWrite(){
      return _afterWrite;
    }

    
    public void setAfterWrite(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._afterWrite = value;
           
    }

    
    /**
     * 
     * xml name: beforeRead
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getBeforeRead(){
      return _beforeRead;
    }

    
    public void setBeforeRead(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._beforeRead = value;
           
    }

    
    /**
     * 
     * xml name: beforeWrite
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getBeforeWrite(){
      return _beforeWrite;
    }

    
    public void setBeforeWrite(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._beforeWrite = value;
           
    }

    
    /**
     * 
     * xml name: repeatCountExpr
     *  返回字段的循环次数
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
     * xml name: repeatCountFieldName
     *  
     */
    
    public java.lang.String getRepeatCountFieldName(){
      return _repeatCountFieldName;
    }

    
    public void setRepeatCountFieldName(java.lang.String value){
        checkAllowChange();
        
        this._repeatCountFieldName = value;
           
    }

    
    /**
     * 
     * xml name: repeatKind
     *  如果是列表结构或者Map结构，则这里用来确定如何判断所有条目已经解析完毕
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
     *  返回字段循环的终止条件
     */
    
    public io.nop.core.lang.eval.IEvalFunction getRepeatUntil(){
      return _repeatUntil;
    }

    
    public void setRepeatUntil(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._repeatUntil = value;
           
    }

    
    /**
     * 
     * xml name: supportStreaming
     *  当字段类型为集合类型时，如果设置了supportStreaming，则流式解析的时候可以每次只返回一个StreamingItem。
     */
    
    public boolean isSupportStreaming(){
      return _supportStreaming;
    }

    
    public void setSupportStreaming(boolean value){
        checkAllowChange();
        
        this._supportStreaming = value;
           
    }

    
    /**
     * 
     * xml name: switchOnExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getSwitchOnExpr(){
      return _switchOnExpr;
    }

    
    public void setSwitchOnExpr(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._switchOnExpr = value;
           
    }

    
    /**
     * 
     * xml name: switchOnField
     *  动态确定字段类型
     * 如果指定了switchOnField，则输出时根据从record[switchOnField]上获取到case类型，然后再映射到type类型，从根对象的types集合中再获取具体定义
     */
    
    public java.lang.String getSwitchOnField(){
      return _switchOnField;
    }

    
    public void setSwitchOnField(java.lang.String value){
        checkAllowChange();
        
        this._switchOnField = value;
           
    }

    
    /**
     * 
     * xml name: switchOnRule
     *  
     */
    
    public io.nop.record.match.IPeekMatchRule getSwitchOnRule(){
      return _switchOnRule;
    }

    
    public void setSwitchOnRule(io.nop.record.match.IPeekMatchRule value){
        checkAllowChange();
        
        this._switchOnRule = value;
           
    }

    
    /**
     * 
     * xml name: switchTypeMap
     *  根据record[switchOnField]或者switchOnRule获取到类型，key为*表示缺省映射
     */
    
    public java.util.Map<java.lang.String,java.lang.String> getSwitchTypeMap(){
      return _switchTypeMap;
    }

    
    public void setSwitchTypeMap(java.util.Map<java.lang.String,java.lang.String> value){
        checkAllowChange();
        
        this._switchTypeMap = value;
           
    }

    
    public boolean hasSwitchTypeMap(){
        return this._switchTypeMap != null && !this._switchTypeMap.isEmpty();
    }
    
    /**
     * 
     * xml name: tagIndex
     *  
     */
    
    public int getTagIndex(){
      return _tagIndex;
    }

    
    public void setTagIndex(int value){
        checkAllowChange();
        
        this._tagIndex = value;
           
    }

    
    /**
     * 
     * xml name: typeRef
     *  
     */
    
    public java.lang.String getTypeRef(){
      return _typeRef;
    }

    
    public void setTypeRef(java.lang.String value){
        checkAllowChange();
        
        this._typeRef = value;
           
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
        
        out.putNotNull("afterRead",this.getAfterRead());
        out.putNotNull("afterWrite",this.getAfterWrite());
        out.putNotNull("beforeRead",this.getBeforeRead());
        out.putNotNull("beforeWrite",this.getBeforeWrite());
        out.putNotNull("repeatCountExpr",this.getRepeatCountExpr());
        out.putNotNull("repeatCountField",this.getRepeatCountField());
        out.putNotNull("repeatCountFieldName",this.getRepeatCountFieldName());
        out.putNotNull("repeatKind",this.getRepeatKind());
        out.putNotNull("repeatUntil",this.getRepeatUntil());
        out.putNotNull("supportStreaming",this.isSupportStreaming());
        out.putNotNull("switchOnExpr",this.getSwitchOnExpr());
        out.putNotNull("switchOnField",this.getSwitchOnField());
        out.putNotNull("switchOnRule",this.getSwitchOnRule());
        out.putNotNull("switchTypeMap",this.getSwitchTypeMap());
        out.putNotNull("tagIndex",this.getTagIndex());
        out.putNotNull("typeRef",this.getTypeRef());
    }

    public RecordFieldMeta cloneInstance(){
        RecordFieldMeta instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(RecordFieldMeta instance){
        super.copyTo(instance);
        
        instance.setAfterRead(this.getAfterRead());
        instance.setAfterWrite(this.getAfterWrite());
        instance.setBeforeRead(this.getBeforeRead());
        instance.setBeforeWrite(this.getBeforeWrite());
        instance.setRepeatCountExpr(this.getRepeatCountExpr());
        instance.setRepeatCountField(this.getRepeatCountField());
        instance.setRepeatCountFieldName(this.getRepeatCountFieldName());
        instance.setRepeatKind(this.getRepeatKind());
        instance.setRepeatUntil(this.getRepeatUntil());
        instance.setSupportStreaming(this.isSupportStreaming());
        instance.setSwitchOnExpr(this.getSwitchOnExpr());
        instance.setSwitchOnField(this.getSwitchOnField());
        instance.setSwitchOnRule(this.getSwitchOnRule());
        instance.setSwitchTypeMap(this.getSwitchTypeMap());
        instance.setTagIndex(this.getTagIndex());
        instance.setTypeRef(this.getTypeRef());
    }

    protected RecordFieldMeta newInstance(){
        return (RecordFieldMeta) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
