package io.nop.record_mapping.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.record_mapping.model.RecordMappingDefinitions;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/record/record-mappings.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _RecordMappingDefinitions extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: mapping
     * 数据对象属性映射规则
     */
    private KeyedList<io.nop.record_mapping.model.RecordMappingConfig> _mappings = KeyedList.emptyList();
    
    /**
     * 
     * xml name: mapping
     *  数据对象属性映射规则
     */
    
    public java.util.List<io.nop.record_mapping.model.RecordMappingConfig> getMappings(){
      return _mappings;
    }

    
    public void setMappings(java.util.List<io.nop.record_mapping.model.RecordMappingConfig> value){
        checkAllowChange();
        
        this._mappings = KeyedList.fromList(value, io.nop.record_mapping.model.RecordMappingConfig::getName);
           
    }

    
    public io.nop.record_mapping.model.RecordMappingConfig getMapping(String name){
        return this._mappings.getByKey(name);
    }

    public boolean hasMapping(String name){
        return this._mappings.containsKey(name);
    }

    public void addMapping(io.nop.record_mapping.model.RecordMappingConfig item) {
        checkAllowChange();
        java.util.List<io.nop.record_mapping.model.RecordMappingConfig> list = this.getMappings();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.record_mapping.model.RecordMappingConfig::getName);
            setMappings(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_mappings(){
        return this._mappings.keySet();
    }

    public boolean hasMappings(){
        return !this._mappings.isEmpty();
    }
    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._mappings = io.nop.api.core.util.FreezeHelper.deepFreeze(this._mappings);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("mappings",this.getMappings());
    }

    public RecordMappingDefinitions cloneInstance(){
        RecordMappingDefinitions instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(RecordMappingDefinitions instance){
        super.copyTo(instance);
        
        instance.setMappings(this.getMappings());
    }

    protected RecordMappingDefinitions newInstance(){
        return (RecordMappingDefinitions) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
