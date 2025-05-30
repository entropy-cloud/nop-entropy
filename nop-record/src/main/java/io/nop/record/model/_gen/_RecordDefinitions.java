package io.nop.record.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.record.model.RecordDefinitions;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/record/record-definitions.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _RecordDefinitions extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: binary
     * 
     */
    private boolean _binary  = false;
    
    /**
     *  
     * xml name: bitEndian
     * 
     */
    private io.nop.commons.bytes.EndianKind _bitEndian ;
    
    /**
     *  
     * xml name: defaultCharset
     * 
     */
    private java.lang.String _defaultCharset  = "UTF-8";
    
    /**
     *  
     * xml name: defaultEndian
     * 
     */
    private io.nop.commons.bytes.EndianKind _defaultEndian ;
    
    /**
     *  
     * xml name: dicts
     * 
     */
    private KeyedList<io.nop.api.core.beans.DictBean> _dicts = KeyedList.emptyList();
    
    /**
     *  
     * xml name: imports
     * 
     */
    private KeyedList<io.nop.record.model.RecordImportModel> _imports = KeyedList.emptyList();
    
    /**
     *  
     * xml name: packageName
     * 
     */
    private java.lang.String _packageName ;
    
    /**
     *  
     * xml name: types
     * 
     */
    private KeyedList<io.nop.record.model.RecordTypeMeta> _types = KeyedList.emptyList();
    
    /**
     * 
     * xml name: binary
     *  
     */
    
    public boolean isBinary(){
      return _binary;
    }

    
    public void setBinary(boolean value){
        checkAllowChange();
        
        this._binary = value;
           
    }

    
    /**
     * 
     * xml name: bitEndian
     *  
     */
    
    public io.nop.commons.bytes.EndianKind getBitEndian(){
      return _bitEndian;
    }

    
    public void setBitEndian(io.nop.commons.bytes.EndianKind value){
        checkAllowChange();
        
        this._bitEndian = value;
           
    }

    
    /**
     * 
     * xml name: defaultCharset
     *  
     */
    
    public java.lang.String getDefaultCharset(){
      return _defaultCharset;
    }

    
    public void setDefaultCharset(java.lang.String value){
        checkAllowChange();
        
        this._defaultCharset = value;
           
    }

    
    /**
     * 
     * xml name: defaultEndian
     *  
     */
    
    public io.nop.commons.bytes.EndianKind getDefaultEndian(){
      return _defaultEndian;
    }

    
    public void setDefaultEndian(io.nop.commons.bytes.EndianKind value){
        checkAllowChange();
        
        this._defaultEndian = value;
           
    }

    
    /**
     * 
     * xml name: dicts
     *  
     */
    
    public java.util.List<io.nop.api.core.beans.DictBean> getDicts(){
      return _dicts;
    }

    
    public void setDicts(java.util.List<io.nop.api.core.beans.DictBean> value){
        checkAllowChange();
        
        this._dicts = KeyedList.fromList(value, io.nop.api.core.beans.DictBean::getName);
           
    }

    
    public io.nop.api.core.beans.DictBean getDict(String name){
        return this._dicts.getByKey(name);
    }

    public boolean hasDict(String name){
        return this._dicts.containsKey(name);
    }

    public void addDict(io.nop.api.core.beans.DictBean item) {
        checkAllowChange();
        java.util.List<io.nop.api.core.beans.DictBean> list = this.getDicts();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.api.core.beans.DictBean::getName);
            setDicts(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_dicts(){
        return this._dicts.keySet();
    }

    public boolean hasDicts(){
        return !this._dicts.isEmpty();
    }
    
    /**
     * 
     * xml name: imports
     *  
     */
    
    public java.util.List<io.nop.record.model.RecordImportModel> getImports(){
      return _imports;
    }

    
    public void setImports(java.util.List<io.nop.record.model.RecordImportModel> value){
        checkAllowChange();
        
        this._imports = KeyedList.fromList(value, io.nop.record.model.RecordImportModel::getAs);
           
    }

    
    public io.nop.record.model.RecordImportModel getImport(String name){
        return this._imports.getByKey(name);
    }

    public boolean hasImport(String name){
        return this._imports.containsKey(name);
    }

    public void addImport(io.nop.record.model.RecordImportModel item) {
        checkAllowChange();
        java.util.List<io.nop.record.model.RecordImportModel> list = this.getImports();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.record.model.RecordImportModel::getAs);
            setImports(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_imports(){
        return this._imports.keySet();
    }

    public boolean hasImports(){
        return !this._imports.isEmpty();
    }
    
    /**
     * 
     * xml name: packageName
     *  
     */
    
    public java.lang.String getPackageName(){
      return _packageName;
    }

    
    public void setPackageName(java.lang.String value){
        checkAllowChange();
        
        this._packageName = value;
           
    }

    
    /**
     * 
     * xml name: types
     *  
     */
    
    public java.util.List<io.nop.record.model.RecordTypeMeta> getTypes(){
      return _types;
    }

    
    public void setTypes(java.util.List<io.nop.record.model.RecordTypeMeta> value){
        checkAllowChange();
        
        this._types = KeyedList.fromList(value, io.nop.record.model.RecordTypeMeta::getName);
           
    }

    
    public io.nop.record.model.RecordTypeMeta getType(String name){
        return this._types.getByKey(name);
    }

    public boolean hasType(String name){
        return this._types.containsKey(name);
    }

    public void addType(io.nop.record.model.RecordTypeMeta item) {
        checkAllowChange();
        java.util.List<io.nop.record.model.RecordTypeMeta> list = this.getTypes();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.record.model.RecordTypeMeta::getName);
            setTypes(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_types(){
        return this._types.keySet();
    }

    public boolean hasTypes(){
        return !this._types.isEmpty();
    }
    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._dicts = io.nop.api.core.util.FreezeHelper.deepFreeze(this._dicts);
            
           this._imports = io.nop.api.core.util.FreezeHelper.deepFreeze(this._imports);
            
           this._types = io.nop.api.core.util.FreezeHelper.deepFreeze(this._types);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("binary",this.isBinary());
        out.putNotNull("bitEndian",this.getBitEndian());
        out.putNotNull("defaultCharset",this.getDefaultCharset());
        out.putNotNull("defaultEndian",this.getDefaultEndian());
        out.putNotNull("dicts",this.getDicts());
        out.putNotNull("imports",this.getImports());
        out.putNotNull("packageName",this.getPackageName());
        out.putNotNull("types",this.getTypes());
    }

    public RecordDefinitions cloneInstance(){
        RecordDefinitions instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(RecordDefinitions instance){
        super.copyTo(instance);
        
        instance.setBinary(this.isBinary());
        instance.setBitEndian(this.getBitEndian());
        instance.setDefaultCharset(this.getDefaultCharset());
        instance.setDefaultEndian(this.getDefaultEndian());
        instance.setDicts(this.getDicts());
        instance.setImports(this.getImports());
        instance.setPackageName(this.getPackageName());
        instance.setTypes(this.getTypes());
    }

    protected RecordDefinitions newInstance(){
        return (RecordDefinitions) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
