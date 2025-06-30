package io.nop.converter.config._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.converter.config.ConvertConfig;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/convert.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _ConvertConfig extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: builders
     * 
     */
    private KeyedList<io.nop.converter.config.ConvertBuilderConfig> _builders = KeyedList.emptyList();
    
    /**
     *  
     * xml name: converters
     * 
     */
    private KeyedList<io.nop.converter.config.ConvertConverterConfig> _converters = KeyedList.emptyList();
    
    /**
     * 
     * xml name: builders
     *  
     */
    
    public java.util.List<io.nop.converter.config.ConvertBuilderConfig> getBuilders(){
      return _builders;
    }

    
    public void setBuilders(java.util.List<io.nop.converter.config.ConvertBuilderConfig> value){
        checkAllowChange();
        
        this._builders = KeyedList.fromList(value, io.nop.converter.config.ConvertBuilderConfig::getFileType);
           
    }

    
    public io.nop.converter.config.ConvertBuilderConfig getBuilder(String name){
        return this._builders.getByKey(name);
    }

    public boolean hasBuilder(String name){
        return this._builders.containsKey(name);
    }

    public void addBuilder(io.nop.converter.config.ConvertBuilderConfig item) {
        checkAllowChange();
        java.util.List<io.nop.converter.config.ConvertBuilderConfig> list = this.getBuilders();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.converter.config.ConvertBuilderConfig::getFileType);
            setBuilders(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_builders(){
        return this._builders.keySet();
    }

    public boolean hasBuilders(){
        return !this._builders.isEmpty();
    }
    
    /**
     * 
     * xml name: converters
     *  
     */
    
    public java.util.List<io.nop.converter.config.ConvertConverterConfig> getConverters(){
      return _converters;
    }

    
    public void setConverters(java.util.List<io.nop.converter.config.ConvertConverterConfig> value){
        checkAllowChange();
        
        this._converters = KeyedList.fromList(value, io.nop.converter.config.ConvertConverterConfig::getId);
           
    }

    
    public io.nop.converter.config.ConvertConverterConfig getConverter(String name){
        return this._converters.getByKey(name);
    }

    public boolean hasConverter(String name){
        return this._converters.containsKey(name);
    }

    public void addConverter(io.nop.converter.config.ConvertConverterConfig item) {
        checkAllowChange();
        java.util.List<io.nop.converter.config.ConvertConverterConfig> list = this.getConverters();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.converter.config.ConvertConverterConfig::getId);
            setConverters(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_converters(){
        return this._converters.keySet();
    }

    public boolean hasConverters(){
        return !this._converters.isEmpty();
    }
    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._builders = io.nop.api.core.util.FreezeHelper.deepFreeze(this._builders);
            
           this._converters = io.nop.api.core.util.FreezeHelper.deepFreeze(this._converters);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("builders",this.getBuilders());
        out.putNotNull("converters",this.getConverters());
    }

    public ConvertConfig cloneInstance(){
        ConvertConfig instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(ConvertConfig instance){
        super.copyTo(instance);
        
        instance.setBuilders(this.getBuilders());
        instance.setConverters(this.getConverters());
    }

    protected ConvertConfig newInstance(){
        return (ConvertConfig) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
