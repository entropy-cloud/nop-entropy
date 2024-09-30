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
     * xml name: defaultTextEncoding
     * 
     */
    private java.lang.String _defaultTextEncoding  = "UTF-8";
    
    /**
     *  
     * xml name: endian
     * 
     */
    private io.nop.commons.bytes.EndianKind _endian ;
    
    /**
     *  
     * xml name: enums
     * 
     */
    private KeyedList<io.nop.record.model.RecordEnum> _enums = KeyedList.emptyList();
    
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
     * xml name: defaultTextEncoding
     *  
     */
    
    public java.lang.String getDefaultTextEncoding(){
      return _defaultTextEncoding;
    }

    
    public void setDefaultTextEncoding(java.lang.String value){
        checkAllowChange();
        
        this._defaultTextEncoding = value;
           
    }

    
    /**
     * 
     * xml name: endian
     *  
     */
    
    public io.nop.commons.bytes.EndianKind getEndian(){
      return _endian;
    }

    
    public void setEndian(io.nop.commons.bytes.EndianKind value){
        checkAllowChange();
        
        this._endian = value;
           
    }

    
    /**
     * 
     * xml name: enums
     *  
     */
    
    public java.util.List<io.nop.record.model.RecordEnum> getEnums(){
      return _enums;
    }

    
    public void setEnums(java.util.List<io.nop.record.model.RecordEnum> value){
        checkAllowChange();
        
        this._enums = KeyedList.fromList(value, io.nop.record.model.RecordEnum::getName);
           
    }

    
    public io.nop.record.model.RecordEnum getEnum(String name){
        return this._enums.getByKey(name);
    }

    public boolean hasEnum(String name){
        return this._enums.containsKey(name);
    }

    public void addEnum(io.nop.record.model.RecordEnum item) {
        checkAllowChange();
        java.util.List<io.nop.record.model.RecordEnum> list = this.getEnums();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.record.model.RecordEnum::getName);
            setEnums(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_enums(){
        return this._enums.keySet();
    }

    public boolean hasEnums(){
        return !this._enums.isEmpty();
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
        
           this._enums = io.nop.api.core.util.FreezeHelper.deepFreeze(this._enums);
            
           this._types = io.nop.api.core.util.FreezeHelper.deepFreeze(this._types);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("binary",this.isBinary());
        out.putNotNull("bitEndian",this.getBitEndian());
        out.putNotNull("defaultTextEncoding",this.getDefaultTextEncoding());
        out.putNotNull("endian",this.getEndian());
        out.putNotNull("enums",this.getEnums());
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
        instance.setDefaultTextEncoding(this.getDefaultTextEncoding());
        instance.setEndian(this.getEndian());
        instance.setEnums(this.getEnums());
        instance.setTypes(this.getTypes());
    }

    protected RecordDefinitions newInstance(){
        return (RecordDefinitions) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
