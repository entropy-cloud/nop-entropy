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
     * xml name: exportAllFields
     * 
     */
    private boolean _exportAllFields  = true;
    
    /**
     *  
     * xml name: fields
     * 
     */
    private KeyedList<io.nop.dbtool.exp.config.ExportTableFieldConfig> _fields = KeyedList.emptyList();
    
    /**
     *  
     * xml name: filter
     * 
     */
    private io.nop.api.core.beans.TreeBean _filter ;
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: sql
     * 
     */
    private java.lang.String _sql ;
    
    /**
     *  
     * xml name: to
     * 
     */
    private java.lang.String _to ;
    
    /**
     *  
     * xml name: transformExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _transformExpr ;
    
    /**
     * 
     * xml name: exportAllFields
     *  
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
    
    public java.util.List<io.nop.dbtool.exp.config.ExportTableFieldConfig> getFields(){
      return _fields;
    }

    
    public void setFields(java.util.List<io.nop.dbtool.exp.config.ExportTableFieldConfig> value){
        checkAllowChange();
        
        this._fields = KeyedList.fromList(value, io.nop.dbtool.exp.config.ExportTableFieldConfig::getName);
           
    }

    
    public io.nop.dbtool.exp.config.ExportTableFieldConfig getField(String name){
        return this._fields.getByKey(name);
    }

    public boolean hasField(String name){
        return this._fields.containsKey(name);
    }

    public void addField(io.nop.dbtool.exp.config.ExportTableFieldConfig item) {
        checkAllowChange();
        java.util.List<io.nop.dbtool.exp.config.ExportTableFieldConfig> list = this.getFields();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.dbtool.exp.config.ExportTableFieldConfig::getName);
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
     *  
     */
    
    public io.nop.api.core.beans.TreeBean getFilter(){
      return _filter;
    }

    
    public void setFilter(io.nop.api.core.beans.TreeBean value){
        checkAllowChange();
        
        this._filter = value;
           
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
     * xml name: sql
     *  
     */
    
    public java.lang.String getSql(){
      return _sql;
    }

    
    public void setSql(java.lang.String value){
        checkAllowChange();
        
        this._sql = value;
           
    }

    
    /**
     * 
     * xml name: to
     *  
     */
    
    public java.lang.String getTo(){
      return _to;
    }

    
    public void setTo(java.lang.String value){
        checkAllowChange();
        
        this._to = value;
           
    }

    
    /**
     * 
     * xml name: transformExpr
     *  
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
            
           this._filter = io.nop.api.core.util.FreezeHelper.deepFreeze(this._filter);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("exportAllFields",this.isExportAllFields());
        out.putNotNull("fields",this.getFields());
        out.putNotNull("filter",this.getFilter());
        out.putNotNull("name",this.getName());
        out.putNotNull("sql",this.getSql());
        out.putNotNull("to",this.getTo());
        out.putNotNull("transformExpr",this.getTransformExpr());
    }

    public ExportTableConfig cloneInstance(){
        ExportTableConfig instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ExportTableConfig instance){
        super.copyTo(instance);
        
        instance.setExportAllFields(this.isExportAllFields());
        instance.setFields(this.getFields());
        instance.setFilter(this.getFilter());
        instance.setName(this.getName());
        instance.setSql(this.getSql());
        instance.setTo(this.getTo());
        instance.setTransformExpr(this.getTransformExpr());
    }

    protected ExportTableConfig newInstance(){
        return (ExportTableConfig) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
