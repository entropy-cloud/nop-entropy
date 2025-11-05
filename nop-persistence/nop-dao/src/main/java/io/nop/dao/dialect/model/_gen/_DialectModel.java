package io.nop.dao.dialect.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.dao.dialect.model.DialectModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/orm/dialect.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _DialectModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: allSchemaPattern
     * 
     */
    private java.lang.String _allSchemaPattern ;
    
    /**
     *  
     * xml name: class
     * 
     */
    private java.lang.String _className ;
    
    /**
     *  
     * xml name: columnNameCase
     * 
     */
    private io.nop.commons.text.CharacterCase _columnNameCase ;
    
    /**
     *  
     * xml name: dbProductNames
     * 
     */
    private java.util.Set<java.lang.String> _dbProductNames ;
    
    /**
     *  
     * xml name: defaultNullsFirst
     * 缺省按照升序排序时，null被被认为是最小还是最大
     */
    private java.lang.Boolean _defaultNullsFirst ;
    
    /**
     *  
     * xml name: discoverySqls
     * 
     */
    private io.nop.dao.dialect.model.DialectDiscoverySqls _discoverySqls ;
    
    /**
     *  
     * xml name: driverClassName
     * 
     */
    private java.lang.String _driverClassName ;
    
    /**
     *  
     * xml name: errorCodes
     * 
     */
    private KeyedList<io.nop.dao.dialect.model.DialectErrorCodeModel> _errorCodes = KeyedList.emptyList();
    
    /**
     *  
     * xml name: features
     * 
     */
    private io.nop.dao.dialect.model.DialectFeatures _features ;
    
    /**
     *  
     * xml name: functions
     * 
     */
    private KeyedList<io.nop.dao.dialect.model.ISqlFunctionModel> _functions = KeyedList.emptyList();
    
    /**
     *  
     * xml name: geometryTypeHandler
     * JDBC使用这个类存取GEOMETRY类型数据。 IDataTypeHandler类型
     */
    private java.lang.String _geometryTypeHandler ;
    
    /**
     *  
     * xml name: jdbcUrlPattern
     * 
     */
    private java.lang.String _jdbcUrlPattern ;
    
    /**
     *  
     * xml name: jsonTypeHandler
     * 
     */
    private java.lang.String _jsonTypeHandler ;
    
    /**
     *  
     * xml name: keywordQuote
     * 列名如果是数据库的关键字，则需要进行转义。这里指定转义时使用的quote字符
     */
    private java.lang.Character _keywordQuote ;
    
    /**
     *  
     * xml name: keywordUnderscore
     * 关键字是否允许以下划线为第一个字符。oracle不允许。
     */
    private java.lang.Boolean _keywordUnderscore  = true;
    
    /**
     *  
     * xml name: maxBytesSize
     * 
     */
    private java.lang.Integer _maxBytesSize ;
    
    /**
     *  
     * xml name: maxStringSize
     * 字符串参数长度超过此长度值之后需要被当作clob看待，采用clob相关的方法去设置
     */
    private java.lang.Integer _maxStringSize ;
    
    /**
     *  
     * xml name: paginationHandler
     * 
     */
    private java.lang.String _paginationHandler ;
    
    /**
     *  
     * xml name: rename
     * 特殊的关键字可能无法通过quote来回避，只能重新命名，例如Oracle中全大写的ROWID不能作为列名，无论是否quote
     */
    private java.util.Map<java.lang.String,java.lang.String> _rename ;
    
    /**
     *  
     * xml name: reservedKeywords
     * 保留的关键字。如果关键字成为列名，则需要用quote来回避
     */
    private java.util.Set<java.lang.String> _reservedKeywords ;
    
    /**
     *  
     * xml name: sqlDataTypes
     * 
     */
    private KeyedList<io.nop.dao.dialect.model.SqlDataTypeModel> _sqlDataTypes = KeyedList.emptyList();
    
    /**
     *  
     * xml name: sqlExceptionTranslator
     * 
     */
    private java.lang.String _sqlExceptionTranslator ;
    
    /**
     *  
     * xml name: sqls
     * 
     */
    private io.nop.dao.dialect.model.DialectSqls _sqls ;
    
    /**
     *  
     * xml name: streamingFetchSize
     * 查询结果集时，如果数据量很大，则采用streaming方式去获取数据，可能需要设置fetchSize标记。比如MySQL要求设置fetchSize为Integer.MIN_VALUE
     */
    private java.lang.Integer _streamingFetchSize ;
    
    /**
     *  
     * xml name: tableNameCase
     * 
     */
    private io.nop.commons.text.CharacterCase _tableNameCase ;
    
    /**
     *  
     * xml name: upsertHandler
     * 
     */
    private java.lang.String _upsertHandler ;
    
    /**
     * 
     * xml name: allSchemaPattern
     *  
     */
    
    public java.lang.String getAllSchemaPattern(){
      return _allSchemaPattern;
    }

    
    public void setAllSchemaPattern(java.lang.String value){
        checkAllowChange();
        
        this._allSchemaPattern = value;
           
    }

    
    /**
     * 
     * xml name: class
     *  
     */
    
    public java.lang.String getClassName(){
      return _className;
    }

    
    public void setClassName(java.lang.String value){
        checkAllowChange();
        
        this._className = value;
           
    }

    
    /**
     * 
     * xml name: columnNameCase
     *  
     */
    
    public io.nop.commons.text.CharacterCase getColumnNameCase(){
      return _columnNameCase;
    }

    
    public void setColumnNameCase(io.nop.commons.text.CharacterCase value){
        checkAllowChange();
        
        this._columnNameCase = value;
           
    }

    
    /**
     * 
     * xml name: dbProductNames
     *  
     */
    
    public java.util.Set<java.lang.String> getDbProductNames(){
      return _dbProductNames;
    }

    
    public void setDbProductNames(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._dbProductNames = value;
           
    }

    
    /**
     * 
     * xml name: defaultNullsFirst
     *  缺省按照升序排序时，null被被认为是最小还是最大
     */
    
    public java.lang.Boolean getDefaultNullsFirst(){
      return _defaultNullsFirst;
    }

    
    public void setDefaultNullsFirst(java.lang.Boolean value){
        checkAllowChange();
        
        this._defaultNullsFirst = value;
           
    }

    
    /**
     * 
     * xml name: discoverySqls
     *  
     */
    
    public io.nop.dao.dialect.model.DialectDiscoverySqls getDiscoverySqls(){
      return _discoverySqls;
    }

    
    public void setDiscoverySqls(io.nop.dao.dialect.model.DialectDiscoverySqls value){
        checkAllowChange();
        
        this._discoverySqls = value;
           
    }

    
    /**
     * 
     * xml name: driverClassName
     *  
     */
    
    public java.lang.String getDriverClassName(){
      return _driverClassName;
    }

    
    public void setDriverClassName(java.lang.String value){
        checkAllowChange();
        
        this._driverClassName = value;
           
    }

    
    /**
     * 
     * xml name: errorCodes
     *  
     */
    
    public java.util.List<io.nop.dao.dialect.model.DialectErrorCodeModel> getErrorCodes(){
      return _errorCodes;
    }

    
    public void setErrorCodes(java.util.List<io.nop.dao.dialect.model.DialectErrorCodeModel> value){
        checkAllowChange();
        
        this._errorCodes = KeyedList.fromList(value, io.nop.dao.dialect.model.DialectErrorCodeModel::getName);
           
    }

    
    public io.nop.dao.dialect.model.DialectErrorCodeModel getErrorCode(String name){
        return this._errorCodes.getByKey(name);
    }

    public boolean hasErrorCode(String name){
        return this._errorCodes.containsKey(name);
    }

    public void addErrorCode(io.nop.dao.dialect.model.DialectErrorCodeModel item) {
        checkAllowChange();
        java.util.List<io.nop.dao.dialect.model.DialectErrorCodeModel> list = this.getErrorCodes();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.dao.dialect.model.DialectErrorCodeModel::getName);
            setErrorCodes(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_errorCodes(){
        return this._errorCodes.keySet();
    }

    public boolean hasErrorCodes(){
        return !this._errorCodes.isEmpty();
    }
    
    /**
     * 
     * xml name: features
     *  
     */
    
    public io.nop.dao.dialect.model.DialectFeatures getFeatures(){
      return _features;
    }

    
    public void setFeatures(io.nop.dao.dialect.model.DialectFeatures value){
        checkAllowChange();
        
        this._features = value;
           
    }

    
    /**
     * 
     * xml name: functions
     *  
     */
    
    public java.util.List<io.nop.dao.dialect.model.ISqlFunctionModel> getFunctions(){
      return _functions;
    }

    
    public void setFunctions(java.util.List<io.nop.dao.dialect.model.ISqlFunctionModel> value){
        checkAllowChange();
        
        this._functions = KeyedList.fromList(value, io.nop.dao.dialect.model.ISqlFunctionModel::getName);
           
    }

    
    public java.util.Set<String> keySet_functions(){
        return this._functions.keySet();
    }

    public boolean hasFunctions(){
        return !this._functions.isEmpty();
    }
    
    /**
     * 
     * xml name: geometryTypeHandler
     *  JDBC使用这个类存取GEOMETRY类型数据。 IDataTypeHandler类型
     */
    
    public java.lang.String getGeometryTypeHandler(){
      return _geometryTypeHandler;
    }

    
    public void setGeometryTypeHandler(java.lang.String value){
        checkAllowChange();
        
        this._geometryTypeHandler = value;
           
    }

    
    /**
     * 
     * xml name: jdbcUrlPattern
     *  
     */
    
    public java.lang.String getJdbcUrlPattern(){
      return _jdbcUrlPattern;
    }

    
    public void setJdbcUrlPattern(java.lang.String value){
        checkAllowChange();
        
        this._jdbcUrlPattern = value;
           
    }

    
    /**
     * 
     * xml name: jsonTypeHandler
     *  
     */
    
    public java.lang.String getJsonTypeHandler(){
      return _jsonTypeHandler;
    }

    
    public void setJsonTypeHandler(java.lang.String value){
        checkAllowChange();
        
        this._jsonTypeHandler = value;
           
    }

    
    /**
     * 
     * xml name: keywordQuote
     *  列名如果是数据库的关键字，则需要进行转义。这里指定转义时使用的quote字符
     */
    
    public java.lang.Character getKeywordQuote(){
      return _keywordQuote;
    }

    
    public void setKeywordQuote(java.lang.Character value){
        checkAllowChange();
        
        this._keywordQuote = value;
           
    }

    
    /**
     * 
     * xml name: keywordUnderscore
     *  关键字是否允许以下划线为第一个字符。oracle不允许。
     */
    
    public java.lang.Boolean getKeywordUnderscore(){
      return _keywordUnderscore;
    }

    
    public void setKeywordUnderscore(java.lang.Boolean value){
        checkAllowChange();
        
        this._keywordUnderscore = value;
           
    }

    
    /**
     * 
     * xml name: maxBytesSize
     *  
     */
    
    public java.lang.Integer getMaxBytesSize(){
      return _maxBytesSize;
    }

    
    public void setMaxBytesSize(java.lang.Integer value){
        checkAllowChange();
        
        this._maxBytesSize = value;
           
    }

    
    /**
     * 
     * xml name: maxStringSize
     *  字符串参数长度超过此长度值之后需要被当作clob看待，采用clob相关的方法去设置
     */
    
    public java.lang.Integer getMaxStringSize(){
      return _maxStringSize;
    }

    
    public void setMaxStringSize(java.lang.Integer value){
        checkAllowChange();
        
        this._maxStringSize = value;
           
    }

    
    /**
     * 
     * xml name: paginationHandler
     *  
     */
    
    public java.lang.String getPaginationHandler(){
      return _paginationHandler;
    }

    
    public void setPaginationHandler(java.lang.String value){
        checkAllowChange();
        
        this._paginationHandler = value;
           
    }

    
    /**
     * 
     * xml name: rename
     *  特殊的关键字可能无法通过quote来回避，只能重新命名，例如Oracle中全大写的ROWID不能作为列名，无论是否quote
     */
    
    public java.util.Map<java.lang.String,java.lang.String> getRename(){
      return _rename;
    }

    
    public void setRename(java.util.Map<java.lang.String,java.lang.String> value){
        checkAllowChange();
        
        this._rename = value;
           
    }

    
    public boolean hasRename(){
        return this._rename != null && !this._rename.isEmpty();
    }
    
    /**
     * 
     * xml name: reservedKeywords
     *  保留的关键字。如果关键字成为列名，则需要用quote来回避
     */
    
    public java.util.Set<java.lang.String> getReservedKeywords(){
      return _reservedKeywords;
    }

    
    public void setReservedKeywords(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._reservedKeywords = value;
           
    }

    
    /**
     * 
     * xml name: sqlDataTypes
     *  
     */
    
    public java.util.List<io.nop.dao.dialect.model.SqlDataTypeModel> getSqlDataTypes(){
      return _sqlDataTypes;
    }

    
    public void setSqlDataTypes(java.util.List<io.nop.dao.dialect.model.SqlDataTypeModel> value){
        checkAllowChange();
        
        this._sqlDataTypes = KeyedList.fromList(value, io.nop.dao.dialect.model.SqlDataTypeModel::getName);
           
    }

    
    public io.nop.dao.dialect.model.SqlDataTypeModel getSqlDataType(String name){
        return this._sqlDataTypes.getByKey(name);
    }

    public boolean hasSqlDataType(String name){
        return this._sqlDataTypes.containsKey(name);
    }

    public void addSqlDataType(io.nop.dao.dialect.model.SqlDataTypeModel item) {
        checkAllowChange();
        java.util.List<io.nop.dao.dialect.model.SqlDataTypeModel> list = this.getSqlDataTypes();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.dao.dialect.model.SqlDataTypeModel::getName);
            setSqlDataTypes(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_sqlDataTypes(){
        return this._sqlDataTypes.keySet();
    }

    public boolean hasSqlDataTypes(){
        return !this._sqlDataTypes.isEmpty();
    }
    
    /**
     * 
     * xml name: sqlExceptionTranslator
     *  
     */
    
    public java.lang.String getSqlExceptionTranslator(){
      return _sqlExceptionTranslator;
    }

    
    public void setSqlExceptionTranslator(java.lang.String value){
        checkAllowChange();
        
        this._sqlExceptionTranslator = value;
           
    }

    
    /**
     * 
     * xml name: sqls
     *  
     */
    
    public io.nop.dao.dialect.model.DialectSqls getSqls(){
      return _sqls;
    }

    
    public void setSqls(io.nop.dao.dialect.model.DialectSqls value){
        checkAllowChange();
        
        this._sqls = value;
           
    }

    
    /**
     * 
     * xml name: streamingFetchSize
     *  查询结果集时，如果数据量很大，则采用streaming方式去获取数据，可能需要设置fetchSize标记。比如MySQL要求设置fetchSize为Integer.MIN_VALUE
     */
    
    public java.lang.Integer getStreamingFetchSize(){
      return _streamingFetchSize;
    }

    
    public void setStreamingFetchSize(java.lang.Integer value){
        checkAllowChange();
        
        this._streamingFetchSize = value;
           
    }

    
    /**
     * 
     * xml name: tableNameCase
     *  
     */
    
    public io.nop.commons.text.CharacterCase getTableNameCase(){
      return _tableNameCase;
    }

    
    public void setTableNameCase(io.nop.commons.text.CharacterCase value){
        checkAllowChange();
        
        this._tableNameCase = value;
           
    }

    
    /**
     * 
     * xml name: upsertHandler
     *  
     */
    
    public java.lang.String getUpsertHandler(){
      return _upsertHandler;
    }

    
    public void setUpsertHandler(java.lang.String value){
        checkAllowChange();
        
        this._upsertHandler = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._discoverySqls = io.nop.api.core.util.FreezeHelper.deepFreeze(this._discoverySqls);
            
           this._errorCodes = io.nop.api.core.util.FreezeHelper.deepFreeze(this._errorCodes);
            
           this._features = io.nop.api.core.util.FreezeHelper.deepFreeze(this._features);
            
           this._functions = io.nop.api.core.util.FreezeHelper.deepFreeze(this._functions);
            
           this._sqlDataTypes = io.nop.api.core.util.FreezeHelper.deepFreeze(this._sqlDataTypes);
            
           this._sqls = io.nop.api.core.util.FreezeHelper.deepFreeze(this._sqls);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("allSchemaPattern",this.getAllSchemaPattern());
        out.putNotNull("className",this.getClassName());
        out.putNotNull("columnNameCase",this.getColumnNameCase());
        out.putNotNull("dbProductNames",this.getDbProductNames());
        out.putNotNull("defaultNullsFirst",this.getDefaultNullsFirst());
        out.putNotNull("discoverySqls",this.getDiscoverySqls());
        out.putNotNull("driverClassName",this.getDriverClassName());
        out.putNotNull("errorCodes",this.getErrorCodes());
        out.putNotNull("features",this.getFeatures());
        out.putNotNull("functions",this.getFunctions());
        out.putNotNull("geometryTypeHandler",this.getGeometryTypeHandler());
        out.putNotNull("jdbcUrlPattern",this.getJdbcUrlPattern());
        out.putNotNull("jsonTypeHandler",this.getJsonTypeHandler());
        out.putNotNull("keywordQuote",this.getKeywordQuote());
        out.putNotNull("keywordUnderscore",this.getKeywordUnderscore());
        out.putNotNull("maxBytesSize",this.getMaxBytesSize());
        out.putNotNull("maxStringSize",this.getMaxStringSize());
        out.putNotNull("paginationHandler",this.getPaginationHandler());
        out.putNotNull("rename",this.getRename());
        out.putNotNull("reservedKeywords",this.getReservedKeywords());
        out.putNotNull("sqlDataTypes",this.getSqlDataTypes());
        out.putNotNull("sqlExceptionTranslator",this.getSqlExceptionTranslator());
        out.putNotNull("sqls",this.getSqls());
        out.putNotNull("streamingFetchSize",this.getStreamingFetchSize());
        out.putNotNull("tableNameCase",this.getTableNameCase());
        out.putNotNull("upsertHandler",this.getUpsertHandler());
    }

    public DialectModel cloneInstance(){
        DialectModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(DialectModel instance){
        super.copyTo(instance);
        
        instance.setAllSchemaPattern(this.getAllSchemaPattern());
        instance.setClassName(this.getClassName());
        instance.setColumnNameCase(this.getColumnNameCase());
        instance.setDbProductNames(this.getDbProductNames());
        instance.setDefaultNullsFirst(this.getDefaultNullsFirst());
        instance.setDiscoverySqls(this.getDiscoverySqls());
        instance.setDriverClassName(this.getDriverClassName());
        instance.setErrorCodes(this.getErrorCodes());
        instance.setFeatures(this.getFeatures());
        instance.setFunctions(this.getFunctions());
        instance.setGeometryTypeHandler(this.getGeometryTypeHandler());
        instance.setJdbcUrlPattern(this.getJdbcUrlPattern());
        instance.setJsonTypeHandler(this.getJsonTypeHandler());
        instance.setKeywordQuote(this.getKeywordQuote());
        instance.setKeywordUnderscore(this.getKeywordUnderscore());
        instance.setMaxBytesSize(this.getMaxBytesSize());
        instance.setMaxStringSize(this.getMaxStringSize());
        instance.setPaginationHandler(this.getPaginationHandler());
        instance.setRename(this.getRename());
        instance.setReservedKeywords(this.getReservedKeywords());
        instance.setSqlDataTypes(this.getSqlDataTypes());
        instance.setSqlExceptionTranslator(this.getSqlExceptionTranslator());
        instance.setSqls(this.getSqls());
        instance.setStreamingFetchSize(this.getStreamingFetchSize());
        instance.setTableNameCase(this.getTableNameCase());
        instance.setUpsertHandler(this.getUpsertHandler());
    }

    protected DialectModel newInstance(){
        return (DialectModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
