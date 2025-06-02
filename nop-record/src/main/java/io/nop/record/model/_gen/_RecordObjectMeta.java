package io.nop.record.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.record.model.RecordObjectMeta;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/record/record-object.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _RecordObjectMeta extends io.nop.core.resource.component.AbstractComponentModel {
    
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
     * xml name: baseType
     * 
     */
    private java.lang.String _baseType ;
    
    /**
     *  
     * xml name: beanClass
     * 
     */
    private java.lang.String _beanClass ;
    
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
     * xml name: displayName
     * 
     */
    private java.lang.String _displayName ;
    
    /**
     *  
     * xml name: doc
     * 
     */
    private java.lang.String _doc ;
    
    /**
     *  
     * xml name: fields
     * 
     */
    private KeyedList<io.nop.record.model.RecordFieldMeta> _fields = KeyedList.emptyList();
    
    /**
     *  
     * xml name: length
     * 
     */
    private java.lang.Integer _length ;
    
    /**
     *  
     * xml name: lengthExpr
     * 动态确定字段长度。在表达式中record指向父结构，_root指向根结构。
     */
    private io.nop.core.lang.eval.IEvalFunction _lengthExpr ;
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: params
     * 
     */
    private KeyedList<io.nop.record.model.RecordParamMeta> _params = KeyedList.emptyList();
    
    /**
     *  
     * xml name: readWhen
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _readWhen ;
    
    /**
     *  
     * xml name: tagsCodec
     * 类似ISO8583协议，支持先输出一个bitmap标记哪些字段需要写出，然后根据tagIndex过滤只写出部分字段
     */
    private java.lang.String _tagsCodec ;
    
    /**
     *  
     * xml name: template
     * 
     */
    private java.lang.String _template ;
    
    /**
     *  
     * xml name: typeRef
     * 引用types段中定义的类型
     */
    private java.lang.String _typeRef ;
    
    /**
     *  
     * xml name: writeWhen
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _writeWhen ;
    
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
     * xml name: baseType
     *  
     */
    
    public java.lang.String getBaseType(){
      return _baseType;
    }

    
    public void setBaseType(java.lang.String value){
        checkAllowChange();
        
        this._baseType = value;
           
    }

    
    /**
     * 
     * xml name: beanClass
     *  
     */
    
    public java.lang.String getBeanClass(){
      return _beanClass;
    }

    
    public void setBeanClass(java.lang.String value){
        checkAllowChange();
        
        this._beanClass = value;
           
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
     * xml name: displayName
     *  
     */
    
    public java.lang.String getDisplayName(){
      return _displayName;
    }

    
    public void setDisplayName(java.lang.String value){
        checkAllowChange();
        
        this._displayName = value;
           
    }

    
    /**
     * 
     * xml name: doc
     *  
     */
    
    public java.lang.String getDoc(){
      return _doc;
    }

    
    public void setDoc(java.lang.String value){
        checkAllowChange();
        
        this._doc = value;
           
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
     * xml name: length
     *  
     */
    
    public java.lang.Integer getLength(){
      return _length;
    }

    
    public void setLength(java.lang.Integer value){
        checkAllowChange();
        
        this._length = value;
           
    }

    
    /**
     * 
     * xml name: lengthExpr
     *  动态确定字段长度。在表达式中record指向父结构，_root指向根结构。
     */
    
    public io.nop.core.lang.eval.IEvalFunction getLengthExpr(){
      return _lengthExpr;
    }

    
    public void setLengthExpr(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._lengthExpr = value;
           
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
     * xml name: params
     *  
     */
    
    public java.util.List<io.nop.record.model.RecordParamMeta> getParams(){
      return _params;
    }

    
    public void setParams(java.util.List<io.nop.record.model.RecordParamMeta> value){
        checkAllowChange();
        
        this._params = KeyedList.fromList(value, io.nop.record.model.RecordParamMeta::getName);
           
    }

    
    public io.nop.record.model.RecordParamMeta getParam(String name){
        return this._params.getByKey(name);
    }

    public boolean hasParam(String name){
        return this._params.containsKey(name);
    }

    public void addParam(io.nop.record.model.RecordParamMeta item) {
        checkAllowChange();
        java.util.List<io.nop.record.model.RecordParamMeta> list = this.getParams();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.record.model.RecordParamMeta::getName);
            setParams(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_params(){
        return this._params.keySet();
    }

    public boolean hasParams(){
        return !this._params.isEmpty();
    }
    
    /**
     * 
     * xml name: readWhen
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getReadWhen(){
      return _readWhen;
    }

    
    public void setReadWhen(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._readWhen = value;
           
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
     *  
     */
    
    public java.lang.String getTemplate(){
      return _template;
    }

    
    public void setTemplate(java.lang.String value){
        checkAllowChange();
        
        this._template = value;
           
    }

    
    /**
     * 
     * xml name: typeRef
     *  引用types段中定义的类型
     */
    
    public java.lang.String getTypeRef(){
      return _typeRef;
    }

    
    public void setTypeRef(java.lang.String value){
        checkAllowChange();
        
        this._typeRef = value;
           
    }

    
    /**
     * 
     * xml name: writeWhen
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getWriteWhen(){
      return _writeWhen;
    }

    
    public void setWriteWhen(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._writeWhen = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._fields = io.nop.api.core.util.FreezeHelper.deepFreeze(this._fields);
            
           this._params = io.nop.api.core.util.FreezeHelper.deepFreeze(this._params);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("afterRead",this.getAfterRead());
        out.putNotNull("afterWrite",this.getAfterWrite());
        out.putNotNull("asMap",this.isAsMap());
        out.putNotNull("baseType",this.getBaseType());
        out.putNotNull("beanClass",this.getBeanClass());
        out.putNotNull("beforeRead",this.getBeforeRead());
        out.putNotNull("beforeWrite",this.getBeforeWrite());
        out.putNotNull("displayName",this.getDisplayName());
        out.putNotNull("doc",this.getDoc());
        out.putNotNull("fields",this.getFields());
        out.putNotNull("length",this.getLength());
        out.putNotNull("lengthExpr",this.getLengthExpr());
        out.putNotNull("name",this.getName());
        out.putNotNull("params",this.getParams());
        out.putNotNull("readWhen",this.getReadWhen());
        out.putNotNull("tagsCodec",this.getTagsCodec());
        out.putNotNull("template",this.getTemplate());
        out.putNotNull("typeRef",this.getTypeRef());
        out.putNotNull("writeWhen",this.getWriteWhen());
    }

    public RecordObjectMeta cloneInstance(){
        RecordObjectMeta instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(RecordObjectMeta instance){
        super.copyTo(instance);
        
        instance.setAfterRead(this.getAfterRead());
        instance.setAfterWrite(this.getAfterWrite());
        instance.setAsMap(this.isAsMap());
        instance.setBaseType(this.getBaseType());
        instance.setBeanClass(this.getBeanClass());
        instance.setBeforeRead(this.getBeforeRead());
        instance.setBeforeWrite(this.getBeforeWrite());
        instance.setDisplayName(this.getDisplayName());
        instance.setDoc(this.getDoc());
        instance.setFields(this.getFields());
        instance.setLength(this.getLength());
        instance.setLengthExpr(this.getLengthExpr());
        instance.setName(this.getName());
        instance.setParams(this.getParams());
        instance.setReadWhen(this.getReadWhen());
        instance.setTagsCodec(this.getTagsCodec());
        instance.setTemplate(this.getTemplate());
        instance.setTypeRef(this.getTypeRef());
        instance.setWriteWhen(this.getWriteWhen());
    }

    protected RecordObjectMeta newInstance(){
        return (RecordObjectMeta) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
