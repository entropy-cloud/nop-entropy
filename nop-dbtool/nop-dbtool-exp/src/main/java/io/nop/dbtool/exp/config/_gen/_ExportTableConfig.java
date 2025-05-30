package io.nop.dbtool.exp.config._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.dbtool.exp.config.ExportTableConfig;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/db/export-db.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ExportTableConfig extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: afterExport
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _afterExport ;
    
    /**
     *  
     * xml name: beforeExport
     * 
     */
    private io.nop.core.lang.eval.IEvalFunction _beforeExport ;
    
    /**
     *  
     * xml name: concurrency
     * 
     */
    private java.lang.Integer _concurrency ;
    
    /**
     *  
     * xml name: exportAllFields
     * 如果设置为true，则导出表中所有字段，即使在fields段中没有定义。否则只导出指定的字段
     */
    private boolean _exportAllFields  = true;
    
    /**
     *  
     * xml name: fields
     * 
     */
    private KeyedList<io.nop.dbtool.exp.config.TableFieldConfig> _fields = KeyedList.emptyList();
    
    /**
     *  
     * xml name: filter
     * 可以根据过滤条件只导出部分数据
     */
    private io.nop.core.lang.xml.IXNodeGenerator _filter ;
    
    /**
     *  
     * xml name: from
     * 来源表名，如果不指定，与name相同。
     */
    private java.lang.String _from ;
    
    /**
     *  
     * xml name: name
     * 导出到文件中的目标表名。所有的表名应该只使用小写字符。
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: sql
     * 可以导出sql语句产生的结果，而不是导出已有的表
     */
    private io.nop.core.lang.sql.ISqlGenerator _sql ;
    
    /**
     *  
     * xml name: transformExpr
     * 导出数据时可以对行进行变换，input对应于来源行，output对应于转换后的行
     */
    private io.nop.core.lang.eval.IEvalAction _transformExpr ;
    
    /**
     * 
     * xml name: afterExport
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getAfterExport(){
      return _afterExport;
    }

    
    public void setAfterExport(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._afterExport = value;
           
    }

    
    /**
     * 
     * xml name: beforeExport
     *  
     */
    
    public io.nop.core.lang.eval.IEvalFunction getBeforeExport(){
      return _beforeExport;
    }

    
    public void setBeforeExport(io.nop.core.lang.eval.IEvalFunction value){
        checkAllowChange();
        
        this._beforeExport = value;
           
    }

    
    /**
     * 
     * xml name: concurrency
     *  
     */
    
    public java.lang.Integer getConcurrency(){
      return _concurrency;
    }

    
    public void setConcurrency(java.lang.Integer value){
        checkAllowChange();
        
        this._concurrency = value;
           
    }

    
    /**
     * 
     * xml name: exportAllFields
     *  如果设置为true，则导出表中所有字段，即使在fields段中没有定义。否则只导出指定的字段
     */
    
    public boolean isExportAllFields(){
      return _exportAllFields;
    }

    
    public void setExportAllFields(boolean value){
        checkAllowChange();
        
        this._exportAllFields = value;
           
    }

    
    /**
     * 
     * xml name: fields
     *  
     */
    
    public java.util.List<io.nop.dbtool.exp.config.TableFieldConfig> getFields(){
      return _fields;
    }

    
    public void setFields(java.util.List<io.nop.dbtool.exp.config.TableFieldConfig> value){
        checkAllowChange();
        
        this._fields = KeyedList.fromList(value, io.nop.dbtool.exp.config.TableFieldConfig::getName);
           
    }

    
    public io.nop.dbtool.exp.config.TableFieldConfig getField(String name){
        return this._fields.getByKey(name);
    }

    public boolean hasField(String name){
        return this._fields.containsKey(name);
    }

    public void addField(io.nop.dbtool.exp.config.TableFieldConfig item) {
        checkAllowChange();
        java.util.List<io.nop.dbtool.exp.config.TableFieldConfig> list = this.getFields();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.dbtool.exp.config.TableFieldConfig::getName);
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
     * xml name: filter
     *  可以根据过滤条件只导出部分数据
     */
    
    public io.nop.core.lang.xml.IXNodeGenerator getFilter(){
      return _filter;
    }

    
    public void setFilter(io.nop.core.lang.xml.IXNodeGenerator value){
        checkAllowChange();
        
        this._filter = value;
           
    }

    
    /**
     * 
     * xml name: from
     *  来源表名，如果不指定，与name相同。
     */
    
    public java.lang.String getFrom(){
      return _from;
    }

    
    public void setFrom(java.lang.String value){
        checkAllowChange();
        
        this._from = value;
           
    }

    
    /**
     * 
     * xml name: name
     *  导出到文件中的目标表名。所有的表名应该只使用小写字符。
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
     * xml name: sql
     *  可以导出sql语句产生的结果，而不是导出已有的表
     */
    
    public io.nop.core.lang.sql.ISqlGenerator getSql(){
      return _sql;
    }

    
    public void setSql(io.nop.core.lang.sql.ISqlGenerator value){
        checkAllowChange();
        
        this._sql = value;
           
    }

    
    /**
     * 
     * xml name: transformExpr
     *  导出数据时可以对行进行变换，input对应于来源行，output对应于转换后的行
     */
    
    public io.nop.core.lang.eval.IEvalAction getTransformExpr(){
      return _transformExpr;
    }

    
    public void setTransformExpr(io.nop.core.lang.eval.IEvalAction value){
        checkAllowChange();
        
        this._transformExpr = value;
           
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
        
        out.putNotNull("afterExport",this.getAfterExport());
        out.putNotNull("beforeExport",this.getBeforeExport());
        out.putNotNull("concurrency",this.getConcurrency());
        out.putNotNull("exportAllFields",this.isExportAllFields());
        out.putNotNull("fields",this.getFields());
        out.putNotNull("filter",this.getFilter());
        out.putNotNull("from",this.getFrom());
        out.putNotNull("name",this.getName());
        out.putNotNull("sql",this.getSql());
        out.putNotNull("transformExpr",this.getTransformExpr());
    }

    public ExportTableConfig cloneInstance(){
        ExportTableConfig instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ExportTableConfig instance){
        super.copyTo(instance);
        
        instance.setAfterExport(this.getAfterExport());
        instance.setBeforeExport(this.getBeforeExport());
        instance.setConcurrency(this.getConcurrency());
        instance.setExportAllFields(this.isExportAllFields());
        instance.setFields(this.getFields());
        instance.setFilter(this.getFilter());
        instance.setFrom(this.getFrom());
        instance.setName(this.getName());
        instance.setSql(this.getSql());
        instance.setTransformExpr(this.getTransformExpr());
    }

    protected ExportTableConfig newInstance(){
        return (ExportTableConfig) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
