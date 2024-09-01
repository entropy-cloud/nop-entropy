package io.nop.dbtool.exp.config._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.dbtool.exp.config.ImportTableConfig;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/db/import-db.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ImportTableConfig extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: allowUpdate
     * 
     */
    private java.lang.Boolean _allowUpdate ;
    
    /**
     *  
     * xml name: fields
     * 
     */
    private KeyedList<io.nop.dbtool.exp.config.TableFieldConfig> _fields = KeyedList.emptyList();
    
    /**
     *  
     * xml name: filter
     * 
     */
    private io.nop.api.core.beans.TreeBean _filter ;
    
    /**
     *  
     * xml name: format
     * 导入文件的后缀名
     */
    private java.lang.String _format ;
    
    /**
     *  
     * xml name: from
     * 
     */
    private java.lang.String _from ;
    
    /**
     *  
     * xml name: importAllFields
     * 
     */
    private boolean _importAllFields  = true;
    
    /**
     *  
     * xml name: keyFields
     * 
     */
    private java.util.List<java.lang.String> _keyFields ;
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: transformExpr
     * 
     */
    private io.nop.core.lang.eval.IEvalAction _transformExpr ;
    
    /**
     * 
     * xml name: allowUpdate
     *  
     */
    
    public java.lang.Boolean getAllowUpdate(){
      return _allowUpdate;
    }

    
    public void setAllowUpdate(java.lang.Boolean value){
        checkAllowChange();
        
        this._allowUpdate = value;
           
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
     * xml name: format
     *  导入文件的后缀名
     */
    
    public java.lang.String getFormat(){
      return _format;
    }

    
    public void setFormat(java.lang.String value){
        checkAllowChange();
        
        this._format = value;
           
    }

    
    /**
     * 
     * xml name: from
     *  
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
     * xml name: importAllFields
     *  
     */
    
    public boolean isImportAllFields(){
      return _importAllFields;
    }

    
    public void setImportAllFields(boolean value){
        checkAllowChange();
        
        this._importAllFields = value;
           
    }

    
    /**
     * 
     * xml name: keyFields
     *  
     */
    
    public java.util.List<java.lang.String> getKeyFields(){
      return _keyFields;
    }

    
    public void setKeyFields(java.util.List<java.lang.String> value){
        checkAllowChange();
        
        this._keyFields = value;
           
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
        
        out.putNotNull("allowUpdate",this.getAllowUpdate());
        out.putNotNull("fields",this.getFields());
        out.putNotNull("filter",this.getFilter());
        out.putNotNull("format",this.getFormat());
        out.putNotNull("from",this.getFrom());
        out.putNotNull("importAllFields",this.isImportAllFields());
        out.putNotNull("keyFields",this.getKeyFields());
        out.putNotNull("name",this.getName());
        out.putNotNull("transformExpr",this.getTransformExpr());
    }

    public ImportTableConfig cloneInstance(){
        ImportTableConfig instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ImportTableConfig instance){
        super.copyTo(instance);
        
        instance.setAllowUpdate(this.getAllowUpdate());
        instance.setFields(this.getFields());
        instance.setFilter(this.getFilter());
        instance.setFormat(this.getFormat());
        instance.setFrom(this.getFrom());
        instance.setImportAllFields(this.isImportAllFields());
        instance.setKeyFields(this.getKeyFields());
        instance.setName(this.getName());
        instance.setTransformExpr(this.getTransformExpr());
    }

    protected ImportTableConfig newInstance(){
        return (ImportTableConfig) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
