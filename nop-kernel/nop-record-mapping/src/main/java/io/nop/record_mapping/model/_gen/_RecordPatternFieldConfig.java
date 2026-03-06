package io.nop.record_mapping.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.record_mapping.model.RecordPatternFieldConfig;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/record/record-mapping.xdef <p>
 * patternField和field配置的区别仅在于来源和目标字段名都可以是动态的，其他属性配置完全一样
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _RecordPatternFieldConfig extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: afterFieldMapping
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _afterFieldMapping ;
    
    /**
     *  
     * xml name: beforeFieldMapping
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _beforeFieldMapping ;
    
    /**
     *  
     * xml name: computeExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _computeExpr ;
    
    /**
     *  
     * xml name: disableFromPropPath
     * 
     */
    private boolean _disableFromPropPath  = false;
    
    /**
     *  
     * xml name: disableToPropPath
     * 
     */
    private boolean _disableToPropPath  = false;
    
    /**
     *  
     * xml name: flattenFrom
     * 
     */
    private boolean _flattenFrom  = false;
    
    /**
     *  
     * xml name: flattenTo
     * 
     */
    private boolean _flattenTo  = false;
    
    /**
     *  
     * xml name: fromPattern
     * 源字段匹配模式，支持{namedGroup}命名捕获组和*通配符
     */
    private java.lang.String _fromPattern ;
    
    /**
     *  
     * xml name: id
     * 
     */
    private java.lang.String _id ;
    
    /**
     *  
     * xml name: ignore
     * 如果设置为true，则匹配的字段将被忽略，不进行映射
     */
    private boolean _ignore  = false;
    
    /**
     *  
     * xml name: ignoreWhenEmpty
     * 
     */
    private boolean _ignoreWhenEmpty  = false;
    
    /**
     *  
     * xml name: itemFilterExpr
     * 当Map或者Collection结构进行映射时，可以判断每个条目是否需要映射
     */
    private io.nop.core.lang.eval.IEvalFunction _itemFilterExpr ;
    
    /**
     *  
     * xml name: itemMapping
     * 
     */
    private java.lang.String _itemMapping ;
    
    /**
     *  
     * xml name: keyProp
     * 
     */
    private java.lang.String _keyProp ;
    
    /**
     *  
     * xml name: mandatory
     * 
     */
    private boolean _mandatory  = false;
    
    /**
     *  
     * xml name: mapping
     * 
     */
    private java.lang.String _mapping ;
    
    /**
     *  
     * xml name: newInstanceExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _newInstanceExpr ;
    
    /**
     *  
     * xml name: newItemExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _newItemExpr ;
    
    /**
     *  
     * xml name: optional
     * 
     */
    private boolean _optional  = false;
    
    /**
     *  
     * xml name: schema
     * schema包含如下几种情况：1. 简单数据类型 2. Map（命名属性集合） 3. List（顺序结构，重复结构） 4. Union（switch选择结构）
     * Map对应props配置,  List对应item配置, Union对应oneOf配置
     */
    private io.nop.xlang.xmeta.ISchema _schema ;
    
    /**
     *  
     * xml name: sourceType
     * 
     */
    private io.nop.core.type.IGenericType _sourceType ;
    
    /**
     *  
     * xml name: to
     * 目标字段名表达式，支持${var}变量插值和表达式
     */
    private io.nop.core.lang.eval.IEvalAction _to ;
    
    /**
     *  
     * xml name: type
     * 
     */
    private io.nop.core.type.IGenericType _type ;
    
    /**
     *  
     * xml name: valueExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _valueExpr ;
    
    /**
     *  
     * xml name: valueMapper
     * 
     */
    private io.nop.core.model.mapper.IValueMapper<java.lang.String,java.lang.Object> _valueMapper ;
    
    /**
     *  
     * xml name: varName
     * 
     */
    private java.lang.String _varName ;
    
    /**
     *  
     * xml name: virtual
     * 虚拟字段，不直接映射值，而是通过computeExpr计算
     */
    private boolean _virtual  = false;
    
    /**
     *  
     * xml name: when
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _when ;
    
    /**
     * 
     * xml name: afterFieldMapping
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getAfterFieldMapping(){
      return _afterFieldMapping;
    }

    
    public void setAfterFieldMapping(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._afterFieldMapping = value;
           
    }

    
    /**
     * 
     * xml name: beforeFieldMapping
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getBeforeFieldMapping(){
      return _beforeFieldMapping;
    }

    
    public void setBeforeFieldMapping(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._beforeFieldMapping = value;
           
    }

    
    /**
     * 
     * xml name: computeExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getComputeExpr(){
      return _computeExpr;
    }

    
    public void setComputeExpr(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._computeExpr = value;
           
    }

    
    /**
     * 
     * xml name: disableFromPropPath
     *  
     */
    
    public boolean isDisableFromPropPath(){
      return _disableFromPropPath;
    }

    
    public void setDisableFromPropPath(boolean value){
        checkAllowChange();
        
        this._disableFromPropPath = value;
           
    }

    
    /**
     * 
     * xml name: disableToPropPath
     *  
     */
    
    public boolean isDisableToPropPath(){
      return _disableToPropPath;
    }

    
    public void setDisableToPropPath(boolean value){
        checkAllowChange();
        
        this._disableToPropPath = value;
           
    }

    
    /**
     * 
     * xml name: flattenFrom
     *  
     */
    
    public boolean isFlattenFrom(){
      return _flattenFrom;
    }

    
    public void setFlattenFrom(boolean value){
        checkAllowChange();
        
        this._flattenFrom = value;
           
    }

    
    /**
     * 
     * xml name: flattenTo
     *  
     */
    
    public boolean isFlattenTo(){
      return _flattenTo;
    }

    
    public void setFlattenTo(boolean value){
        checkAllowChange();
        
        this._flattenTo = value;
           
    }

    
    /**
     * 
     * xml name: fromPattern
     *  源字段匹配模式，支持{namedGroup}命名捕获组和*通配符
     */
    
    public java.lang.String getFromPattern(){
      return _fromPattern;
    }

    
    public void setFromPattern(java.lang.String value){
        checkAllowChange();
        
        this._fromPattern = value;
           
    }

    
    /**
     * 
     * xml name: id
     *  
     */
    
    public java.lang.String getId(){
      return _id;
    }

    
    public void setId(java.lang.String value){
        checkAllowChange();
        
        this._id = value;
           
    }

    
    /**
     * 
     * xml name: ignore
     *  如果设置为true，则匹配的字段将被忽略，不进行映射
     */
    
    public boolean isIgnore(){
      return _ignore;
    }

    
    public void setIgnore(boolean value){
        checkAllowChange();
        
        this._ignore = value;
           
    }

    
    /**
     * 
     * xml name: ignoreWhenEmpty
     *  
     */
    
    public boolean isIgnoreWhenEmpty(){
      return _ignoreWhenEmpty;
    }

    
    public void setIgnoreWhenEmpty(boolean value){
        checkAllowChange();
        
        this._ignoreWhenEmpty = value;
           
    }

    
    /**
     * 
     * xml name: itemFilterExpr
     *  当Map或者Collection结构进行映射时，可以判断每个条目是否需要映射
     */
    
    public io.nop.core.lang.eval.IEvalFunction getItemFilterExpr(){
      return _itemFilterExpr;
    }

    
    public void setItemFilterExpr(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._itemFilterExpr = value;
           
    }

    
    /**
     * 
     * xml name: itemMapping
     *  
     */
    
    public java.lang.String getItemMapping(){
      return _itemMapping;
    }

    
    public void setItemMapping(java.lang.String value){
        checkAllowChange();
        
        this._itemMapping = value;
           
    }

    
    /**
     * 
     * xml name: keyProp
     *  
     */
    
    public java.lang.String getKeyProp(){
      return _keyProp;
    }

    
    public void setKeyProp(java.lang.String value){
        checkAllowChange();
        
        this._keyProp = value;
           
    }

    
    /**
     * 
     * xml name: mandatory
     *  
     */
    
    public boolean isMandatory(){
      return _mandatory;
    }

    
    public void setMandatory(boolean value){
        checkAllowChange();
        
        this._mandatory = value;
           
    }

    
    /**
     * 
     * xml name: mapping
     *  
     */
    
    public java.lang.String getMapping(){
      return _mapping;
    }

    
    public void setMapping(java.lang.String value){
        checkAllowChange();
        
        this._mapping = value;
           
    }

    
    /**
     * 
     * xml name: newInstanceExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getNewInstanceExpr(){
      return _newInstanceExpr;
    }

    
    public void setNewInstanceExpr(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._newInstanceExpr = value;
           
    }

    
    /**
     * 
     * xml name: newItemExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getNewItemExpr(){
      return _newItemExpr;
    }

    
    public void setNewItemExpr(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._newItemExpr = value;
           
    }

    
    /**
     * 
     * xml name: optional
     *  
     */
    
    public boolean isOptional(){
      return _optional;
    }

    
    public void setOptional(boolean value){
        checkAllowChange();
        
        this._optional = value;
           
    }

    
    /**
     * 
     * xml name: schema
     *  schema包含如下几种情况：1. 简单数据类型 2. Map（命名属性集合） 3. List（顺序结构，重复结构） 4. Union（switch选择结构）
     * Map对应props配置,  List对应item配置, Union对应oneOf配置
     */
    
    public io.nop.xlang.xmeta.ISchema getSchema(){
      return _schema;
    }

    
    public void setSchema(io.nop.xlang.xmeta.ISchema value){
        checkAllowChange();
        
        this._schema = value;
           
    }

    
    /**
     * 
     * xml name: sourceType
     *  
     */
    
    public io.nop.core.type.IGenericType getSourceType(){
      return _sourceType;
    }

    
    public void setSourceType(io.nop.core.type.IGenericType value){
        checkAllowChange();
        
        this._sourceType = value;
           
    }

    
    /**
     * 
     * xml name: to
     *  目标字段名表达式，支持${var}变量插值和表达式
     */
    
    public io.nop.core.lang.eval.IEvalAction getTo(){
      return _to;
    }

    
    public void setTo(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._to = value;
           
    }

    
    /**
     * 
     * xml name: type
     *  
     */
    
    public io.nop.core.type.IGenericType getType(){
      return _type;
    }

    
    public void setType(io.nop.core.type.IGenericType value){
        checkAllowChange();
        
        this._type = value;
           
    }

    
    /**
     * 
     * xml name: valueExpr
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getValueExpr(){
      return _valueExpr;
    }

    
    public void setValueExpr(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._valueExpr = value;
           
    }

    
    /**
     * 
     * xml name: valueMapper
     *  
     */
    
    public io.nop.core.model.mapper.IValueMapper<java.lang.String,java.lang.Object> getValueMapper(){
      return _valueMapper;
    }

    
    public void setValueMapper(io.nop.core.model.mapper.IValueMapper<java.lang.String,java.lang.Object> value){
        checkAllowChange();
        
        this._valueMapper = value;
           
    }

    
    /**
     * 
     * xml name: varName
     *  
     */
    
    public java.lang.String getVarName(){
      return _varName;
    }

    
    public void setVarName(java.lang.String value){
        checkAllowChange();
        
        this._varName = value;
           
    }

    
    /**
     * 
     * xml name: virtual
     *  虚拟字段，不直接映射值，而是通过computeExpr计算
     */
    
    public boolean isVirtual(){
      return _virtual;
    }

    
    public void setVirtual(boolean value){
        checkAllowChange();
        
        this._virtual = value;
           
    }

    
    /**
     * 
     * xml name: when
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getWhen(){
      return _when;
    }

    
    public void setWhen(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._when = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._schema = io.nop.api.core.util.FreezeHelper.deepFreeze(this._schema);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("afterFieldMapping",this.getAfterFieldMapping());
        out.putNotNull("beforeFieldMapping",this.getBeforeFieldMapping());
        out.putNotNull("computeExpr",this.getComputeExpr());
        out.putNotNull("disableFromPropPath",this.isDisableFromPropPath());
        out.putNotNull("disableToPropPath",this.isDisableToPropPath());
        out.putNotNull("flattenFrom",this.isFlattenFrom());
        out.putNotNull("flattenTo",this.isFlattenTo());
        out.putNotNull("fromPattern",this.getFromPattern());
        out.putNotNull("id",this.getId());
        out.putNotNull("ignore",this.isIgnore());
        out.putNotNull("ignoreWhenEmpty",this.isIgnoreWhenEmpty());
        out.putNotNull("itemFilterExpr",this.getItemFilterExpr());
        out.putNotNull("itemMapping",this.getItemMapping());
        out.putNotNull("keyProp",this.getKeyProp());
        out.putNotNull("mandatory",this.isMandatory());
        out.putNotNull("mapping",this.getMapping());
        out.putNotNull("newInstanceExpr",this.getNewInstanceExpr());
        out.putNotNull("newItemExpr",this.getNewItemExpr());
        out.putNotNull("optional",this.isOptional());
        out.putNotNull("schema",this.getSchema());
        out.putNotNull("sourceType",this.getSourceType());
        out.putNotNull("to",this.getTo());
        out.putNotNull("type",this.getType());
        out.putNotNull("valueExpr",this.getValueExpr());
        out.putNotNull("valueMapper",this.getValueMapper());
        out.putNotNull("varName",this.getVarName());
        out.putNotNull("virtual",this.isVirtual());
        out.putNotNull("when",this.getWhen());
    }

    public RecordPatternFieldConfig cloneInstance(){
        RecordPatternFieldConfig instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(RecordPatternFieldConfig instance){
        super.copyTo(instance);
        
        instance.setAfterFieldMapping(this.getAfterFieldMapping());
        instance.setBeforeFieldMapping(this.getBeforeFieldMapping());
        instance.setComputeExpr(this.getComputeExpr());
        instance.setDisableFromPropPath(this.isDisableFromPropPath());
        instance.setDisableToPropPath(this.isDisableToPropPath());
        instance.setFlattenFrom(this.isFlattenFrom());
        instance.setFlattenTo(this.isFlattenTo());
        instance.setFromPattern(this.getFromPattern());
        instance.setId(this.getId());
        instance.setIgnore(this.isIgnore());
        instance.setIgnoreWhenEmpty(this.isIgnoreWhenEmpty());
        instance.setItemFilterExpr(this.getItemFilterExpr());
        instance.setItemMapping(this.getItemMapping());
        instance.setKeyProp(this.getKeyProp());
        instance.setMandatory(this.isMandatory());
        instance.setMapping(this.getMapping());
        instance.setNewInstanceExpr(this.getNewInstanceExpr());
        instance.setNewItemExpr(this.getNewItemExpr());
        instance.setOptional(this.isOptional());
        instance.setSchema(this.getSchema());
        instance.setSourceType(this.getSourceType());
        instance.setTo(this.getTo());
        instance.setType(this.getType());
        instance.setValueExpr(this.getValueExpr());
        instance.setValueMapper(this.getValueMapper());
        instance.setVarName(this.getVarName());
        instance.setVirtual(this.isVirtual());
        instance.setWhen(this.getWhen());
    }

    protected RecordPatternFieldConfig newInstance(){
        return (RecordPatternFieldConfig) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
