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
     * xml name: asMap
     * 表示解析得到Map结构。要求fields中必须包含且只包含两个字段key和value，repeatKind不允许为空。
     */
    private boolean _asMap  = false;
    
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
     * xml name: div
     * 表示是否仅仅是作为分组使用，并不对应于一个真实的对象
     */
    private boolean _div  = false;
    
    /**
     *  
     * xml name: fields
     * 
     */
    private KeyedList<io.nop.record.model.RecordFieldMeta> _fields = KeyedList.emptyList();
    
    /**
     *  
     * xml name: readRepeatExpr
     * <sizeField xdef:ref="record-simple-field.xdef"/>
     * 返回字段的循环次数
     */
    private io.nop.core.lang.eval.IEvalFunction _readRepeatExpr ;
    
    /**
     *  
     * xml name: readRepeatUntil
     * 返回字段循环的终止条件
     */
    private io.nop.core.lang.eval.IEvalFunction _readRepeatUntil ;
    
    /**
     *  
     * xml name: repeatKind
     * 如果是列表结构或者Map结构，则这里用来确定如何判断所有条目已经解析完毕
     */
    private io.nop.record.model.FieldRepeatKind _repeatKind ;
    
    /**
     *  
     * xml name: switchOnField
     * 动态确定字段类型
     * 如果指定了switchOnField，则输出时根据从record[switchOnField]上获取到case类型，然后再映射到type类型，从根对象的types集合中再获取具体定义
     */
    private java.lang.String _switchOnField ;
    
    /**
     *  
     * xml name: switchTypeMap
     * 根据record[switchOnField]获取到类型，key为*表示缺省映射
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
     * xml name: tagsCodec
     * 类似ISO8583协议，支持先输出一个bitmap标记哪些字段需要写出，然后根据tagIndex过滤只写出部分字段
     */
    private java.lang.String _tagsCodec ;
    
    /**
     *  
     * xml name: template
     * 文本输出时使用template更加直观
     */
    private java.lang.String _template ;
    
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
     * xml name: asMap
     *  表示解析得到Map结构。要求fields中必须包含且只包含两个字段key和value，repeatKind不允许为空。
     */
    
    public boolean isAsMap(){
      return _asMap;
    }

    
    public void setAsMap(boolean value){
        checkAllowChange();
        
        this._asMap = value;
           
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
     * xml name: div
     *  表示是否仅仅是作为分组使用，并不对应于一个真实的对象
     */
    
    public boolean isDiv(){
      return _div;
    }

    
    public void setDiv(boolean value){
        checkAllowChange();
        
        this._div = value;
           
    }

    
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
     * xml name: readRepeatExpr
     *  <sizeField xdef:ref="record-simple-field.xdef"/>
     * 返回字段的循环次数
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
     * xml name: readRepeatUntil
     *  返回字段循环的终止条件
     */
    
    public io.nop.core.lang.eval.IEvalFunction getReadRepeatUntil(){
      return _readRepeatUntil;
    }

    
    public void setReadRepeatUntil(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._readRepeatUntil = value;
           
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
     * xml name: switchTypeMap
     *  根据record[switchOnField]获取到类型，key为*表示缺省映射
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
     * xml name: tagsCodec
     *  类似ISO8583协议，支持先输出一个bitmap标记哪些字段需要写出，然后根据tagIndex过滤只写出部分字段
     */
    
    public java.lang.String getTagsCodec(){
      return _tagsCodec;
    }

    
    public void setTagsCodec(java.lang.String value){
        checkAllowChange();
        
        this._tagsCodec = value;
           
    }

    
    /**
     * 
     * xml name: template
     *  文本输出时使用template更加直观
     */
    
    public java.lang.String getTemplate(){
      return _template;
    }

    
    public void setTemplate(java.lang.String value){
        checkAllowChange();
        
        this._template = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._fields = io.nop.api.core.util.FreezeHelper.deepFreeze(this._fields);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("afterRead",this.getAfterRead());
        out.putNotNull("afterWrite",this.getAfterWrite());
        out.putNotNull("asMap",this.isAsMap());
        out.putNotNull("beforeRead",this.getBeforeRead());
        out.putNotNull("beforeWrite",this.getBeforeWrite());
        out.putNotNull("div",this.isDiv());
        out.putNotNull("fields",this.getFields());
        out.putNotNull("readRepeatExpr",this.getReadRepeatExpr());
        out.putNotNull("readRepeatUntil",this.getReadRepeatUntil());
        out.putNotNull("repeatKind",this.getRepeatKind());
        out.putNotNull("switchOnField",this.getSwitchOnField());
        out.putNotNull("switchTypeMap",this.getSwitchTypeMap());
        out.putNotNull("tagIndex",this.getTagIndex());
        out.putNotNull("tagsCodec",this.getTagsCodec());
        out.putNotNull("template",this.getTemplate());
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
        instance.setAsMap(this.isAsMap());
        instance.setBeforeRead(this.getBeforeRead());
        instance.setBeforeWrite(this.getBeforeWrite());
        instance.setDiv(this.isDiv());
        instance.setFields(this.getFields());
        instance.setReadRepeatExpr(this.getReadRepeatExpr());
        instance.setReadRepeatUntil(this.getReadRepeatUntil());
        instance.setRepeatKind(this.getRepeatKind());
        instance.setSwitchOnField(this.getSwitchOnField());
        instance.setSwitchTypeMap(this.getSwitchTypeMap());
        instance.setTagIndex(this.getTagIndex());
        instance.setTagsCodec(this.getTagsCodec());
        instance.setTemplate(this.getTemplate());
    }

    protected RecordFieldMeta newInstance(){
        return (RecordFieldMeta) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
