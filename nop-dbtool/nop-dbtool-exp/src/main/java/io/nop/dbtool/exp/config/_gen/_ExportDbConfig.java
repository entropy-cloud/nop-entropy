package io.nop.dbtool.exp.config._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.dbtool.exp.config.ExportDbConfig;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/db/export-db.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ExportDbConfig extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: batchSize
     * 
     */
    private int _batchSize  = 0;
    
    /**
     *  
     * xml name: checkExportable
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _checkExportable ;
    
    /**
     *  
     * xml name: concurrencyPerTable
     * 
     */
    private java.lang.Integer _concurrencyPerTable ;
    
    /**
     *  
     * xml name: excludeTableNames
     * 排除某些表不导出
     */
    private java.util.Set<java.lang.String> _excludeTableNames ;
    
    /**
     *  
     * xml name: exportAllTables
     * 如果设置为true，则导出所有满足tableNamePattern的表。否则以tables配置的表为准，只导出指定的表
     */
    private boolean _exportAllTables  = false;
    
    /**
     *  
     * xml name: exportFormats
     * 导出文件格式，可以是csv, csv.gz和sql
     */
    private java.util.Set<java.lang.String> _exportFormats ;
    
    /**
     *  
     * xml name: fetchSize
     * 设置jdbc底层连接使用的fetchSize。MySQL数据库要求fetchSize设置为一个特殊值来启用流数据读取模式
     */
    private java.lang.Integer _fetchSize ;
    
    /**
     *  
     * xml name: jdbc-connection
     * 
     */
    private io.nop.dbtool.exp.config.JdbcConnectionConfig _jdbcConnection ;
    
    /**
     *  
     * xml name: outputDir
     * 
     */
    private java.lang.String _outputDir ;
    
    /**
     *  
     * xml name: schemaPattern
     * 
     */
    private java.lang.String _schemaPattern ;
    
    /**
     *  
     * xml name: streaming
     * 
     */
    private boolean _streaming  = true;
    
    /**
     *  
     * xml name: tableNamePattern
     * 查找数据库中所有表名满足模式要求的表，例如nop_%会匹配nop_auth_user等表。
     */
    private java.lang.String _tableNamePattern ;
    
    /**
     *  
     * xml name: tables
     * 
     */
    private KeyedList<io.nop.dbtool.exp.config.ExportTableConfig> _tables = KeyedList.emptyList();
    
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
     * xml name: checkExportable
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getCheckExportable(){
      return _checkExportable;
    }

    
    public void setCheckExportable(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._checkExportable = value;
           
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
     *  排除某些表不导出
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
     * xml name: exportAllTables
     *  如果设置为true，则导出所有满足tableNamePattern的表。否则以tables配置的表为准，只导出指定的表
     */
    
    public boolean isExportAllTables(){
      return _exportAllTables;
    }

    
    public void setExportAllTables(boolean value){
        checkAllowChange();
        
        this._exportAllTables = value;
           
    }

    
    /**
     * 
     * xml name: exportFormats
     *  导出文件格式，可以是csv, csv.gz和sql
     */
    
    public java.util.Set<java.lang.String> getExportFormats(){
      return _exportFormats;
    }

    
    public void setExportFormats(java.util.Set<java.lang.String> value){
        checkAllowChange();
        
        this._exportFormats = value;
           
    }

    
    /**
     * 
     * xml name: fetchSize
     *  设置jdbc底层连接使用的fetchSize。MySQL数据库要求fetchSize设置为一个特殊值来启用流数据读取模式
     */
    
    public java.lang.Integer getFetchSize(){
      return _fetchSize;
    }

    
    public void setFetchSize(java.lang.Integer value){
        checkAllowChange();
        
        this._fetchSize = value;
           
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
     * xml name: outputDir
     *  
     */
    
    public java.lang.String getOutputDir(){
      return _outputDir;
    }

    
    public void setOutputDir(java.lang.String value){
        checkAllowChange();
        
        this._outputDir = value;
           
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
     * xml name: streaming
     *  
     */
    
    public boolean isStreaming(){
      return _streaming;
    }

    
    public void setStreaming(boolean value){
        checkAllowChange();
        
        this._streaming = value;
           
    }

    
    /**
     * 
     * xml name: tableNamePattern
     *  查找数据库中所有表名满足模式要求的表，例如nop_%会匹配nop_auth_user等表。
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
    
    public java.util.List<io.nop.dbtool.exp.config.ExportTableConfig> getTables(){
      return _tables;
    }

    
    public void setTables(java.util.List<io.nop.dbtool.exp.config.ExportTableConfig> value){
        checkAllowChange();
        
        this._tables = KeyedList.fromList(value, io.nop.dbtool.exp.config.ExportTableConfig::getName);
           
    }

    
    public io.nop.dbtool.exp.config.ExportTableConfig getTable(String name){
        return this._tables.getByKey(name);
    }

    public boolean hasTable(String name){
        return this._tables.containsKey(name);
    }

    public void addTable(io.nop.dbtool.exp.config.ExportTableConfig item) {
        checkAllowChange();
        java.util.List<io.nop.dbtool.exp.config.ExportTableConfig> list = this.getTables();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.dbtool.exp.config.ExportTableConfig::getName);
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
        out.putNotNull("checkExportable",this.getCheckExportable());
        out.putNotNull("concurrencyPerTable",this.getConcurrencyPerTable());
        out.putNotNull("excludeTableNames",this.getExcludeTableNames());
        out.putNotNull("exportAllTables",this.isExportAllTables());
        out.putNotNull("exportFormats",this.getExportFormats());
        out.putNotNull("fetchSize",this.getFetchSize());
        out.putNotNull("jdbcConnection",this.getJdbcConnection());
        out.putNotNull("outputDir",this.getOutputDir());
        out.putNotNull("schemaPattern",this.getSchemaPattern());
        out.putNotNull("streaming",this.isStreaming());
        out.putNotNull("tableNamePattern",this.getTableNamePattern());
        out.putNotNull("tables",this.getTables());
        out.putNotNull("threadCount",this.getThreadCount());
    }

    public ExportDbConfig cloneInstance(){
        ExportDbConfig instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ExportDbConfig instance){
        super.copyTo(instance);
        
        instance.setBatchSize(this.getBatchSize());
        instance.setCheckExportable(this.getCheckExportable());
        instance.setConcurrencyPerTable(this.getConcurrencyPerTable());
        instance.setExcludeTableNames(this.getExcludeTableNames());
        instance.setExportAllTables(this.isExportAllTables());
        instance.setExportFormats(this.getExportFormats());
        instance.setFetchSize(this.getFetchSize());
        instance.setJdbcConnection(this.getJdbcConnection());
        instance.setOutputDir(this.getOutputDir());
        instance.setSchemaPattern(this.getSchemaPattern());
        instance.setStreaming(this.isStreaming());
        instance.setTableNamePattern(this.getTableNamePattern());
        instance.setTables(this.getTables());
        instance.setThreadCount(this.getThreadCount());
    }

    protected ExportDbConfig newInstance(){
        return (ExportDbConfig) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
