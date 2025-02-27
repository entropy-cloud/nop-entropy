package io.nop.dbtool.exp.config._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.dbtool.exp.config.ImportDbConfig;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/db/import-db.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ImportDbConfig extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: batchSize
     * 
     */
    private int _batchSize  = 0;
    
    /**
     *  
     * xml name: checkImportable
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _checkImportable ;
    
    /**
     *  
     * xml name: checkKeyFields
     * 导入时是否根据keyFields设置检查记录是否已存在。如果存在，则可以选择更新或者忽略
     */
    private boolean _checkKeyFields  = true;
    
    /**
     *  
     * xml name: concurrencyPerTable
     * 
     */
    private java.lang.Integer _concurrencyPerTable ;
    
    /**
     *  
     * xml name: excludeTableNames
     * 
     */
    private java.util.Set<java.lang.String> _excludeTableNames ;
    
    /**
     *  
     * xml name: importAllTables
     * 导入inputDir目录下的所有表
     */
    private boolean _importAllTables  = true;
    
    /**
     *  
     * xml name: inputDir
     * 
     */
    private java.lang.String _inputDir ;
    
    /**
     *  
     * xml name: jdbc-connection
     * 
     */
    private io.nop.dbtool.exp.config.JdbcConnectionConfig _jdbcConnection ;
    
    /**
     *  
     * xml name: schemaPattern
     * 
     */
    private java.lang.String _schemaPattern ;
    
    /**
     *  
     * xml name: tableNamePattern
     * 
     */
    private java.lang.String _tableNamePattern ;
    
    /**
     *  
     * xml name: tables
     * 
     */
    private KeyedList<io.nop.dbtool.exp.config.ImportTableConfig> _tables = KeyedList.emptyList();
    
    /**
     *  
     * xml name: threadCount
     * 
     */
    private int _threadCount  = 0;
    
    /**
     * 
     * xml name: batchSize
     *  
     */
    
    public int getBatchSize(){
      return _batchSize;
    }

    
    public void setBatchSize(int value){
        checkAllowChange();
        
        this._batchSize = value;
           
    }

    
    /**
     * 
     * xml name: checkImportable
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getCheckImportable(){
      return _checkImportable;
    }

    
    public void setCheckImportable(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._checkImportable = value;
           
    }

    
    /**
     * 
     * xml name: checkKeyFields
     *  导入时是否根据keyFields设置检查记录是否已存在。如果存在，则可以选择更新或者忽略
     */
    
    public boolean isCheckKeyFields(){
      return _checkKeyFields;
    }

    
    public void setCheckKeyFields(boolean value){
        checkAllowChange();
        
        this._checkKeyFields = value;
           
    }

    
    /**
     * 
     * xml name: concurrencyPerTable
     *  
     */
    
    public java.lang.Integer getConcurrencyPerTable(){
      return _concurrencyPerTable;
    }

    
    public void setConcurrencyPerTable(java.lang.Integer value){
        checkAllowChange();
        
        this._concurrencyPerTable = value;
           
    }

    
    /**
     * 
     * xml name: excludeTableNames
     *  
     */
    
    public java.util.Set<java.lang.String> getExcludeTableNames(){
      return _excludeTableNames;
    }

    
    public void setExcludeTableNames(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._excludeTableNames = value;
           
    }

    
    /**
     * 
     * xml name: importAllTables
     *  导入inputDir目录下的所有表
     */
    
    public boolean isImportAllTables(){
      return _importAllTables;
    }

    
    public void setImportAllTables(boolean value){
        checkAllowChange();
        
        this._importAllTables = value;
           
    }

    
    /**
     * 
     * xml name: inputDir
     *  
     */
    
    public java.lang.String getInputDir(){
      return _inputDir;
    }

    
    public void setInputDir(java.lang.String value){
        checkAllowChange();
        
        this._inputDir = value;
           
    }

    
    /**
     * 
     * xml name: jdbc-connection
     *  
     */
    
    public io.nop.dbtool.exp.config.JdbcConnectionConfig getJdbcConnection(){
      return _jdbcConnection;
    }

    
    public void setJdbcConnection(io.nop.dbtool.exp.config.JdbcConnectionConfig value){
        checkAllowChange();
        
        this._jdbcConnection = value;
           
    }

    
    /**
     * 
     * xml name: schemaPattern
     *  
     */
    
    public java.lang.String getSchemaPattern(){
      return _schemaPattern;
    }

    
    public void setSchemaPattern(java.lang.String value){
        checkAllowChange();
        
        this._schemaPattern = value;
           
    }

    
    /**
     * 
     * xml name: tableNamePattern
     *  
     */
    
    public java.lang.String getTableNamePattern(){
      return _tableNamePattern;
    }

    
    public void setTableNamePattern(java.lang.String value){
        checkAllowChange();
        
        this._tableNamePattern = value;
           
    }

    
    /**
     * 
     * xml name: tables
     *  
     */
    
    public java.util.List<io.nop.dbtool.exp.config.ImportTableConfig> getTables(){
      return _tables;
    }

    
    public void setTables(java.util.List<io.nop.dbtool.exp.config.ImportTableConfig> value){
        checkAllowChange();
        
        this._tables = KeyedList.fromList(value, io.nop.dbtool.exp.config.ImportTableConfig::getName);
           
    }

    
    public io.nop.dbtool.exp.config.ImportTableConfig getTable(String name){
        return this._tables.getByKey(name);
    }

    public boolean hasTable(String name){
        return this._tables.containsKey(name);
    }

    public void addTable(io.nop.dbtool.exp.config.ImportTableConfig item) {
        checkAllowChange();
        java.util.List<io.nop.dbtool.exp.config.ImportTableConfig> list = this.getTables();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.dbtool.exp.config.ImportTableConfig::getName);
            setTables(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_tables(){
        return this._tables.keySet();
    }

    public boolean hasTables(){
        return !this._tables.isEmpty();
    }
    
    /**
     * 
     * xml name: threadCount
     *  
     */
    
    public int getThreadCount(){
      return _threadCount;
    }

    
    public void setThreadCount(int value){
        checkAllowChange();
        
        this._threadCount = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._jdbcConnection = io.nop.api.core.util.FreezeHelper.deepFreeze(this._jdbcConnection);
            
           this._tables = io.nop.api.core.util.FreezeHelper.deepFreeze(this._tables);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("batchSize",this.getBatchSize());
        out.putNotNull("checkImportable",this.getCheckImportable());
        out.putNotNull("checkKeyFields",this.isCheckKeyFields());
        out.putNotNull("concurrencyPerTable",this.getConcurrencyPerTable());
        out.putNotNull("excludeTableNames",this.getExcludeTableNames());
        out.putNotNull("importAllTables",this.isImportAllTables());
        out.putNotNull("inputDir",this.getInputDir());
        out.putNotNull("jdbcConnection",this.getJdbcConnection());
        out.putNotNull("schemaPattern",this.getSchemaPattern());
        out.putNotNull("tableNamePattern",this.getTableNamePattern());
        out.putNotNull("tables",this.getTables());
        out.putNotNull("threadCount",this.getThreadCount());
    }

    public ImportDbConfig cloneInstance(){
        ImportDbConfig instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ImportDbConfig instance){
        super.copyTo(instance);
        
        instance.setBatchSize(this.getBatchSize());
        instance.setCheckImportable(this.getCheckImportable());
        instance.setCheckKeyFields(this.isCheckKeyFields());
        instance.setConcurrencyPerTable(this.getConcurrencyPerTable());
        instance.setExcludeTableNames(this.getExcludeTableNames());
        instance.setImportAllTables(this.isImportAllTables());
        instance.setInputDir(this.getInputDir());
        instance.setJdbcConnection(this.getJdbcConnection());
        instance.setSchemaPattern(this.getSchemaPattern());
        instance.setTableNamePattern(this.getTableNamePattern());
        instance.setTables(this.getTables());
        instance.setThreadCount(this.getThreadCount());
    }

    protected ImportDbConfig newInstance(){
        return (ImportDbConfig) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
