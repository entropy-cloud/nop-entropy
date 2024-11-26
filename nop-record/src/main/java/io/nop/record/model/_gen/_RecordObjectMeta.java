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
     * xml name: ref
     * 引用types段中定义的类型
     */
    private java.lang.String _ref ;
    
    /**
     *  
     * xml name: template
     * 
     */
    private java.lang.String _template ;
    
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
     * xml name: ref
     *  引用types段中定义的类型
     */
    
    public java.lang.String getRef(){
      return _ref;
    }

    
    public void setRef(java.lang.String value){
        checkAllowChange();
        
        this._ref = value;
           
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
        out.putNotNull("baseType",this.getBaseType());
        out.putNotNull("beanClass",this.getBeanClass());
        out.putNotNull("doc",this.getDoc());
        out.putNotNull("fields",this.getFields());
        out.putNotNull("params",this.getParams());
        out.putNotNull("readWhen",this.getReadWhen());
        out.putNotNull("ref",this.getRef());
        out.putNotNull("template",this.getTemplate());
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
        instance.setBaseType(this.getBaseType());
        instance.setBeanClass(this.getBeanClass());
        instance.setDoc(this.getDoc());
        instance.setFields(this.getFields());
        instance.setParams(this.getParams());
        instance.setReadWhen(this.getReadWhen());
        instance.setRef(this.getRef());
        instance.setTemplate(this.getTemplate());
        instance.setWriteWhen(this.getWriteWhen());
    }

    protected RecordObjectMeta newInstance(){
        return (RecordObjectMeta) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
