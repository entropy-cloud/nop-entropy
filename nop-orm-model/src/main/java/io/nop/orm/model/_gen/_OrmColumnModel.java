package io.nop.orm.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [75:10:0:0]/nop/schema/orm/entity.xdef <p>
 * column必须是原子数据类型，它对应于数据库中的字段。其他属性都根据column字段的值衍生而来。
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _OrmColumnModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: code
     * 数据库中的列名。code也必须是唯一的
     */
    private java.lang.String _code ;
    
    /**
     *  
     * xml name: comment
     * 
     */
    private java.lang.String _comment ;
    
    /**
     *  
     * xml name: defaultValue
     * 
     */
    private java.lang.String _defaultValue ;
    
    /**
     *  
     * xml name: displayName
     * 
     */
    private java.lang.String _displayName ;
    
    /**
     *  
     * xml name: domain
     * 
     */
    private java.lang.String _domain ;
    
    /**
     *  
     * xml name: fixedValue
     * 字段的值总是取固定值。例如通过一个大宽表来同时支持多个业务实体对象时，可以通过类型列来区分不同的实体类型
     */
    private java.lang.String _fixedValue ;
    
    /**
     *  
     * xml name: insertable
     * 
     */
    private boolean _insertable  = true;
    
    /**
     *  
     * xml name: jsonPath
     * 
     */
    private java.lang.String _jsonPath ;
    
    /**
     *  是否延迟加载
     * xml name: lazy
     * 如果设置为true，则装载实体时缺省情况下该字段会被延迟加载。clob和blob字段生成代码时会缺省设置为lazy=true
     */
    private boolean _lazy  = false;
    
    /**
     *  是否非空
     * xml name: mandatory
     * 如果设置为true，则不允许为null或者空字符串
     */
    private boolean _mandatory  = false;
    
    /**
     *  
     * xml name: name
     * java实体属性名
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: nativeSqlType
     * 
     */
    private java.lang.String _nativeSqlType ;
    
    /**
     *  
     * xml name: notGenCode
     * 
     */
    private boolean _notGenCode  = false;
    
    /**
     *  
     * xml name: precision
     * 
     */
    private java.lang.Integer _precision ;
    
    /**
     *  是否主键
     * xml name: primary
     * 
     */
    private boolean _primary  = false;
    
    /**
     *  
     * xml name: propId
     * 每个列都对应唯一的propId。生成代码的时候propId会编程PROP_ID_{propName}这样的常量定义。
     * 数据库中字段定义顺序与propId顺序一致。propId从1开始，与列的index并不一致，而且从兼容性考虑，propId之间有可能存在被忽略的值
     * （例如某字段被从数据库删除，它对应的propId忽略，但是程序中已经使用的其他propId值不变）。当实体修改内容作为json格式保存到数据库中时，
     * 为减少存储空间，会按照[[propId,oldValue,newValue]]的格式进行存储。
     */
    private int _propId ;
    
    /**
     *  
     * xml name: scale
     * 
     */
    private java.lang.Integer _scale ;
    
    /**
     *  
     * xml name: sqlText
     * 如果不为空，则表示是sql视图字段，字段为只读列，值不允许被修改。查询的时候会使用sqlText作为获取数据的表达式。
     */
    private java.lang.String _sqlText ;
    
    /**
     *  
     * xml name: stdDataType
     * 
     */
    private io.nop.commons.type.StdDataType _stdDataType ;
    
    /**
     *  
     * xml name: stdDomain
     * 
     */
    private java.lang.String _stdDomain ;
    
    /**
     *  
     * xml name: stdSqlType
     * 
     */
    private io.nop.commons.type.StdSqlType _stdSqlType ;
    
    /**
     *  
     * xml name: tagSet
     * 
     */
    private java.util.Set<java.lang.String> _tagSet ;
    
    /**
     *  
     * xml name: uiHint
     * 
     */
    private java.lang.String _uiHint ;
    
    /**
     *  
     * xml name: updatable
     * 
     */
    private boolean _updatable  = true;
    
    /**
     * 
     * xml name: code
     *  数据库中的列名。code也必须是唯一的
     */
    
    public java.lang.String getCode(){
      return _code;
    }

    
    public void setCode(java.lang.String value){
        checkAllowChange();
        
        this._code = value;
           
    }

    
    /**
     * 
     * xml name: comment
     *  
     */
    
    public java.lang.String getComment(){
      return _comment;
    }

    
    public void setComment(java.lang.String value){
        checkAllowChange();
        
        this._comment = value;
           
    }

    
    /**
     * 
     * xml name: defaultValue
     *  
     */
    
    public java.lang.String getDefaultValue(){
      return _defaultValue;
    }

    
    public void setDefaultValue(java.lang.String value){
        checkAllowChange();
        
        this._defaultValue = value;
           
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
     * xml name: domain
     *  
     */
    
    public java.lang.String getDomain(){
      return _domain;
    }

    
    public void setDomain(java.lang.String value){
        checkAllowChange();
        
        this._domain = value;
           
    }

    
    /**
     * 
     * xml name: fixedValue
     *  字段的值总是取固定值。例如通过一个大宽表来同时支持多个业务实体对象时，可以通过类型列来区分不同的实体类型
     */
    
    public java.lang.String getFixedValue(){
      return _fixedValue;
    }

    
    public void setFixedValue(java.lang.String value){
        checkAllowChange();
        
        this._fixedValue = value;
           
    }

    
    /**
     * 
     * xml name: insertable
     *  
     */
    
    public boolean isInsertable(){
      return _insertable;
    }

    
    public void setInsertable(boolean value){
        checkAllowChange();
        
        this._insertable = value;
           
    }

    
    /**
     * 
     * xml name: jsonPath
     *  
     */
    
    public java.lang.String getJsonPath(){
      return _jsonPath;
    }

    
    public void setJsonPath(java.lang.String value){
        checkAllowChange();
        
        this._jsonPath = value;
           
    }

    
    /**
     * 是否延迟加载
     * xml name: lazy
     *  如果设置为true，则装载实体时缺省情况下该字段会被延迟加载。clob和blob字段生成代码时会缺省设置为lazy=true
     */
    
    public boolean isLazy(){
      return _lazy;
    }

    
    public void setLazy(boolean value){
        checkAllowChange();
        
        this._lazy = value;
           
    }

    
    /**
     * 是否非空
     * xml name: mandatory
     *  如果设置为true，则不允许为null或者空字符串
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
     * xml name: name
     *  java实体属性名
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
     * xml name: nativeSqlType
     *  
     */
    
    public java.lang.String getNativeSqlType(){
      return _nativeSqlType;
    }

    
    public void setNativeSqlType(java.lang.String value){
        checkAllowChange();
        
        this._nativeSqlType = value;
           
    }

    
    /**
     * 
     * xml name: notGenCode
     *  
     */
    
    public boolean isNotGenCode(){
      return _notGenCode;
    }

    
    public void setNotGenCode(boolean value){
        checkAllowChange();
        
        this._notGenCode = value;
           
    }

    
    /**
     * 
     * xml name: precision
     *  
     */
    
    public java.lang.Integer getPrecision(){
      return _precision;
    }

    
    public void setPrecision(java.lang.Integer value){
        checkAllowChange();
        
        this._precision = value;
           
    }

    
    /**
     * 是否主键
     * xml name: primary
     *  
     */
    
    public boolean isPrimary(){
      return _primary;
    }

    
    public void setPrimary(boolean value){
        checkAllowChange();
        
        this._primary = value;
           
    }

    
    /**
     * 
     * xml name: propId
     *  每个列都对应唯一的propId。生成代码的时候propId会编程PROP_ID_{propName}这样的常量定义。
     * 数据库中字段定义顺序与propId顺序一致。propId从1开始，与列的index并不一致，而且从兼容性考虑，propId之间有可能存在被忽略的值
     * （例如某字段被从数据库删除，它对应的propId忽略，但是程序中已经使用的其他propId值不变）。当实体修改内容作为json格式保存到数据库中时，
     * 为减少存储空间，会按照[[propId,oldValue,newValue]]的格式进行存储。
     */
    
    public int getPropId(){
      return _propId;
    }

    
    public void setPropId(int value){
        checkAllowChange();
        
        this._propId = value;
           
    }

    
    /**
     * 
     * xml name: scale
     *  
     */
    
    public java.lang.Integer getScale(){
      return _scale;
    }

    
    public void setScale(java.lang.Integer value){
        checkAllowChange();
        
        this._scale = value;
           
    }

    
    /**
     * 
     * xml name: sqlText
     *  如果不为空，则表示是sql视图字段，字段为只读列，值不允许被修改。查询的时候会使用sqlText作为获取数据的表达式。
     */
    
    public java.lang.String getSqlText(){
      return _sqlText;
    }

    
    public void setSqlText(java.lang.String value){
        checkAllowChange();
        
        this._sqlText = value;
           
    }

    
    /**
     * 
     * xml name: stdDataType
     *  
     */
    
    public io.nop.commons.type.StdDataType getStdDataType(){
      return _stdDataType;
    }

    
    public void setStdDataType(io.nop.commons.type.StdDataType value){
        checkAllowChange();
        
        this._stdDataType = value;
           
    }

    
    /**
     * 
     * xml name: stdDomain
     *  
     */
    
    public java.lang.String getStdDomain(){
      return _stdDomain;
    }

    
    public void setStdDomain(java.lang.String value){
        checkAllowChange();
        
        this._stdDomain = value;
           
    }

    
    /**
     * 
     * xml name: stdSqlType
     *  
     */
    
    public io.nop.commons.type.StdSqlType getStdSqlType(){
      return _stdSqlType;
    }

    
    public void setStdSqlType(io.nop.commons.type.StdSqlType value){
        checkAllowChange();
        
        this._stdSqlType = value;
           
    }

    
    /**
     * 
     * xml name: tagSet
     *  
     */
    
    public java.util.Set<java.lang.String> getTagSet(){
      return _tagSet;
    }

    
    public void setTagSet(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._tagSet = value;
           
    }

    
    /**
     * 
     * xml name: uiHint
     *  
     */
    
    public java.lang.String getUiHint(){
      return _uiHint;
    }

    
    public void setUiHint(java.lang.String value){
        checkAllowChange();
        
        this._uiHint = value;
           
    }

    
    /**
     * 
     * xml name: updatable
     *  
     */
    
    public boolean isUpdatable(){
      return _updatable;
    }

    
    public void setUpdatable(boolean value){
        checkAllowChange();
        
        this._updatable = value;
           
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
        
        out.put("code",this.getCode());
        out.put("comment",this.getComment());
        out.put("defaultValue",this.getDefaultValue());
        out.put("displayName",this.getDisplayName());
        out.put("domain",this.getDomain());
        out.put("fixedValue",this.getFixedValue());
        out.put("insertable",this.isInsertable());
        out.put("jsonPath",this.getJsonPath());
        out.put("lazy",this.isLazy());
        out.put("mandatory",this.isMandatory());
        out.put("name",this.getName());
        out.put("nativeSqlType",this.getNativeSqlType());
        out.put("notGenCode",this.isNotGenCode());
        out.put("precision",this.getPrecision());
        out.put("primary",this.isPrimary());
        out.put("propId",this.getPropId());
        out.put("scale",this.getScale());
        out.put("sqlText",this.getSqlText());
        out.put("stdDataType",this.getStdDataType());
        out.put("stdDomain",this.getStdDomain());
        out.put("stdSqlType",this.getStdSqlType());
        out.put("tagSet",this.getTagSet());
        out.put("uiHint",this.getUiHint());
        out.put("updatable",this.isUpdatable());
    }
}
 // resume CPD analysis - CPD-ON
